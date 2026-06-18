#!/usr/bin/env bash
# ============================================================================
#  SepMerge++ - Executor de Ferramentas e Coletor de Metricas (v2)
# ----------------------------------------------------------------------------
#  Mudancas em relacao a v1:
#   - CSDiff agora roda via `java -cp sepmerge.jar br.ifpe.sepmerge.CSDiffMain`
#     (nao precisa mais de csdiff.jar separado)
#   - files_equal normaliza BOM (UTF-8 EF BB BF) antes de comparar — elimina
#     falsos aFN causados por diferenca apenas de encoding
#   - files_equal normaliza CRLF -> LF e strip trailing whitespace
#   - classify_result renomeia "aFP" para "reportou_conflito" para clareza
#     (a real classificacao aFP/aFN eh feita pelo 03_aggregate.py em pares)
# ============================================================================

set -euo pipefail

# ---------------------------------------------------------------------------
# CONFIGURACAO
# ---------------------------------------------------------------------------

DATASET_DIR="${DATASET_DIR:-$(pwd)/dataset}"
RESULTS_DIR="${RESULTS_DIR:-$(pwd)/results}"
CSV_FILE="${CSV_FILE:-$(pwd)/results.csv}"

# Caminho do jar do SepMerge++ (gera tambem CSDiff via -cp)
SEPMERGE_JAR="${SEPMERGE_JAR:-$(pwd)/sepmerge.jar}"

DIFF3_CMD="${DIFF3_CMD:-diff3}"

# ---------------------------------------------------------------------------
# UTILIDADES
# ---------------------------------------------------------------------------

log()   { printf '\033[1;34m[INFO]\033[0m %s\n' "$*"; }
warn()  { printf '\033[1;33m[WARN]\033[0m %s\n' "$*" >&2; }
error() { printf '\033[1;31m[ERROR]\033[0m %s\n' "$*" >&2; }

count_conflicts() {
    local file="$1"
    [[ -f "$file" ]] || { echo 0; return; }
    local count
    count=$(grep -c '^<<<<<<<' "$file" 2>/dev/null || true)
    echo "${count:-0}"
}

# Compara dois arquivos com normalizacao tolerante:
#   - Strip de BOM UTF-8 (EF BB BF) na primeira linha
#   - Normaliza CRLF -> LF
#   - Remove trailing whitespace por linha
files_equal() {
    local a="$1"; local b="$2"
    [[ -f "$a" && -f "$b" ]] || return 1

    local na="${a}.norm" nb="${b}.norm"

    # sed remove BOM UTF-8 se estiver na primeira linha
    # tr -d '\r' remove CR (CRLF -> LF)
    sed -e '1s/^\xEF\xBB\xBF//' "$a" | tr -d '\r' | sed -e 's/[[:space:]]*$//' > "$na"
    sed -e '1s/^\xEF\xBB\xBF//' "$b" | tr -d '\r' | sed -e 's/[[:space:]]*$//' > "$nb"

    diff -q "$na" "$nb" >/dev/null 2>&1
    local rc=$?
    rm -f "$na" "$nb"
    return $rc
}

run_diff3() {
    local base="$1"; local left="$2"; local right="$3"; local out="$4"
    "$DIFF3_CMD" -m "$left" "$base" "$right" > "$out" 2>/dev/null || true
}

# Roda o CSDiff (nao-focalizado) usando a classe CSDiffMain do mesmo jar
run_csdiff() {
    local base="$1"; local left="$2"; local right="$3"; local out="$4"

    if [[ ! -f "$SEPMERGE_JAR" ]]; then
        error "SEPMERGE_JAR nao encontrado em $SEPMERGE_JAR"
        exit 1
    fi
    java -cp "$SEPMERGE_JAR" br.ifpe.sepmerge.CSDiffMain \
        "$base" "$left" "$right" > "$out" 2>/dev/null || true
}

run_sepmerge() {
    local base="$1"; local left="$2"; local right="$3"; local out="$4"

    if [[ ! -f "$SEPMERGE_JAR" ]]; then
        error "SEPMERGE_JAR nao encontrado em $SEPMERGE_JAR"
        exit 1
    fi
    java -jar "$SEPMERGE_JAR" "$base" "$left" "$right" > "$out" 2>/dev/null || true
}

# Classifica o resultado preliminar de uma ferramenta:
#   "reportou_conflito"     - ferramenta reportou pelo menos 1 conflito
#   "correct"               - sem conflito, resultado bate com merged_real
#   "merge_diferente"       - sem conflito, mas resultado difere do merged_real
#   "vazio"                 - ferramenta nao produziu saida
# A classificacao final aFP/aFN eh feita pelo 03_aggregate.py comparando
# pares de ferramentas (ex.: sepmerge vs diff3).
classify_result() {
    local tool_out="$1"
    local merged_real="$2"

    if [[ ! -s "$tool_out" ]]; then
        echo "vazio"
        return
    fi

    local tool_conflicts
    tool_conflicts=$(count_conflicts "$tool_out")

    if (( tool_conflicts > 0 )); then
        echo "reportou_conflito"
    else
        if files_equal "$tool_out" "$merged_real"; then
            echo "correct"
        else
            echo "merge_diferente"
        fi
    fi
}

# ---------------------------------------------------------------------------
# PROCESSAMENTO DE UM CENARIO
# ---------------------------------------------------------------------------

process_scenario() {
    local scenario_dir="$1"
    local metadata="$scenario_dir/metadata.json"
    [[ -f "$metadata" ]] || { warn "Sem metadata em $scenario_dir"; return; }

    local lang proj merge_sha file_rel
    lang=$(grep -oE '"language": *"[^"]*"' "$metadata" | sed 's/.*: *"//;s/"$//')
    proj=$(grep -oE '"project": *"[^"]*"' "$metadata" | sed 's/.*: *"//;s/"$//')
    merge_sha=$(grep -oE '"merge_commit_short": *"[^"]*"' "$metadata" | sed 's/.*: *"//;s/"$//')
    file_rel=$(grep -oE '"file": *"[^"]*"' "$metadata" | sed 's/.*: *"//;s/"$//')

    local base="$scenario_dir/base/$file_rel"
    local left="$scenario_dir/left/$file_rel"
    local right="$scenario_dir/right/$file_rel"
    local merged_real="$scenario_dir/merged/$file_rel"

    if [[ ! -s "$base" && ! -s "$left" && ! -s "$right" ]]; then
        return
    fi

    local out_base
    out_base="$RESULTS_DIR/$lang/$proj/${merge_sha}__$(echo "$file_rel" | tr '/' '_')"
    mkdir -p "$out_base"

    local ext="${file_rel##*.}"
    [[ "$ext" == "$file_rel" ]] && ext="out"

    local out_diff3="$out_base/diff3.$ext"
    local out_csdiff="$out_base/csdiff.$ext"
    local out_sepmerge="$out_base/sepmerge.$ext"

    run_diff3    "$base" "$left" "$right" "$out_diff3"
    run_csdiff   "$base" "$left" "$right" "$out_csdiff"
    run_sepmerge "$base" "$left" "$right" "$out_sepmerge"

    local c_diff3 c_csdiff c_sepmerge
    c_diff3=$(count_conflicts "$out_diff3")
    c_csdiff=$(count_conflicts "$out_csdiff")
    c_sepmerge=$(count_conflicts "$out_sepmerge")

    local class_diff3 class_csdiff class_sepmerge
    class_diff3=$(classify_result    "$out_diff3"    "$merged_real")
    class_csdiff=$(classify_result   "$out_csdiff"   "$merged_real")
    class_sepmerge=$(classify_result "$out_sepmerge" "$merged_real")

    {
        printf '%s,%s,%s,%s,%s,%d,%s\n' \
            "$lang" "$proj" "$merge_sha" "$file_rel" "diff3"     "$c_diff3"    "$class_diff3"
        printf '%s,%s,%s,%s,%s,%d,%s\n' \
            "$lang" "$proj" "$merge_sha" "$file_rel" "csdiff"    "$c_csdiff"   "$class_csdiff"
        printf '%s,%s,%s,%s,%s,%d,%s\n' \
            "$lang" "$proj" "$merge_sha" "$file_rel" "sepmerge"  "$c_sepmerge" "$class_sepmerge"
    } >> "$CSV_FILE"
}

# ---------------------------------------------------------------------------
# EXECUCAO PRINCIPAL
# ---------------------------------------------------------------------------

main() {
    [[ -d "$DATASET_DIR" ]] || { error "Dataset nao encontrado em $DATASET_DIR"; exit 1; }

    if [[ ! -f "$SEPMERGE_JAR" ]]; then
        error "sepmerge.jar nao encontrado em $SEPMERGE_JAR. Rode 'mvn package' primeiro."
        exit 1
    fi

    mkdir -p "$RESULTS_DIR"
    echo "language,project,merge_sha,file,tool,conflicts,classification" > "$CSV_FILE"

    local scenarios
    mapfile -t scenarios < <(find "$DATASET_DIR" -name "metadata.json" -exec dirname {} \; | sort)
    local total=${#scenarios[@]}

    log "Encontrados $total cenarios para processar."
    log "JAR: $SEPMERGE_JAR"
    log "CSV: $CSV_FILE"
    echo

    local i=0
    for scenario_dir in "${scenarios[@]}"; do
        i=$((i + 1))
        if (( i % 20 == 0 )); then
            log "  ... $i/$total processados"
        fi
        process_scenario "$scenario_dir"
    done

    log "=================================================="
    log "PROCESSAMENTO CONCLUIDO"
    log "CSV: $CSV_FILE"
    log "Resultados por cenario: $RESULTS_DIR"
    log "=================================================="
}

main "$@"
