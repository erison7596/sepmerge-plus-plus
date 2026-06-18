#!/usr/bin/env bash
# ============================================================================
#  SepMerge++ - Extrator de Cenarios de Integracao (v4)
# ----------------------------------------------------------------------------
#  Mudancas em relacao a v3:
#   - clone agora usa --no-tags --single-branch
#     Razao: contorna bug "initial ref transaction called with existing refs"
#     do Git for Windows 2.54.0 em repos com muitas tags
#   - se o clone falhar, pula o projeto e segue em frente em vez de abortar
# ============================================================================

set -uo pipefail

DATASET_DIR="${DATASET_DIR:-$(pwd)/dataset}"
REPOS_DIR="${REPOS_DIR:-$(pwd)/repos}"

SINCE_DATE="${SINCE_DATE:-2018-01-01}"
UNTIL_DATE="${UNTIL_DATE:-2026-12-31}"

MAX_SCENARIOS_PER_PROJECT="${MAX_SCENARIOS_PER_PROJECT:-200}"

PROJECTS=(
    #"csharp|Dapper|https://github.com/DapperLib/Dapper.git"
   # "csharp|AutoMapper|https://github.com/AutoMapper/AutoMapper.git"
   # "csharp|RestSharp|https://github.com/restsharp/RestSharp.git"
    #"csharp|PowerShell|https://github.com/PowerShell/PowerShell.git"
    #"csharp|ShareX|https://github.com/ShareX/ShareX.git"
    "csharp|MaterialDesignInXamlToolkit|https://github.com/MaterialDesignInXAML/MaterialDesignInXamlToolkit.git"
    "csharp|Avalonia|https://github.com/AvaloniaUI/Avalonia.git"

    "go|prometheus|https://github.com/prometheus/prometheus.git"
    "go|etcd|https://github.com/etcd-io/etcd.git"
    "go|syncthing|https://github.com/syncthing/syncthing.git"
    "go|caddy|https://github.com/caddyserver/caddy.git"
    "go|traefik|https://github.com/traefik/traefik.git"
    "go|moby|https://github.com/moby/moby.git"

    "haskell|shellcheck|https://github.com/koalaman/shellcheck.git"
    "haskell|hlint|https://github.com/ndmitchell/hlint.git"
    "haskell|ghc|https://github.com/ghc/ghc.git"
    "haskell|cabal|https://github.com/haskell/cabal.git"
    "haskell|pandoc|https://github.com/jgm/pandoc.git"
    "haskell|yesod|https://github.com/yesodweb/yesod.git"
)

declare -A EXT_BY_LANG=(
    [csharp]="cs"
    [go]="go"
    [haskell]="hs"
)

log()    { printf '\033[1;34m[INFO]\033[0m %s\n' "$*"; }
warn()   { printf '\033[1;33m[WARN]\033[0m %s\n' "$*" >&2; }
error()  { printf '\033[1;31m[ERROR]\033[0m %s\n' "$*" >&2; }

# Clone com workaround para o bug do Git for Windows 2.54.0:
#   --no-tags          : evita "initial ref transaction" em repos com muitas tags
#   --single-branch    : so a branch padrao, suficiente para merges
clone_if_needed() {
    local name="$1"; local url="$2"; local dest="$REPOS_DIR/$name"
    if [[ -d "$dest/.git" ]]; then
        log "Repositorio $name ja existe - pulando clone."
        return 0
    fi

    log "Clonando $name (sem tags, com progresso) ..."
    if git clone --no-tags --single-branch --progress "$url" "$dest"; then
        return 0
    fi

    warn "Clone de $name falhou. Limpando e pulando este projeto."
    rm -rf "$dest"
    return 1
}

extract_file_at_commit() {
    local repo="$1"; local sha="$2"; local file="$3"; local out_path="$4"
    mkdir -p "$(dirname "$out_path")"
    if git -C "$repo" cat-file -e "$sha:$file" 2>/dev/null; then
        git -C "$repo" cat-file -p "$sha:$file" > "$out_path"
    else
        : > "$out_path"
    fi
}

files_modified_between() {
    git -C "$1" diff --name-only "$2" "$3"
}

process_merge_commit() {
    local lang="$1"; local proj_name="$2"; local repo="$3"; local merge_sha="$4"

    local ext="${EXT_BY_LANG[$lang]}"
    local short_sha
    short_sha=$(git -C "$repo" rev-parse --short "$merge_sha")

    local parents p1 p2
    parents=$(git -C "$repo" rev-list --parents -n 1 "$merge_sha")
    p1=$(echo "$parents" | awk '{print $2}')
    p2=$(echo "$parents" | awk '{print $3}')

    [[ -z "$p2" ]] && return 0

    local base_sha
    if ! base_sha=$(git -C "$repo" merge-base "$p1" "$p2" 2>/dev/null); then
        return 0
    fi

    local left_files right_files common_files
    left_files=$(files_modified_between "$repo" "$base_sha" "$p1" | grep -E "\.${ext}$" || true)
    right_files=$(files_modified_between "$repo" "$base_sha" "$p2" | grep -E "\.${ext}$" || true)
    common_files=$(comm -12 <(echo "$left_files" | sort -u) <(echo "$right_files" | sort -u))

    [[ -z "$common_files" ]] && return 0

    local scenario_count=0
    while IFS= read -r file; do
        [[ -z "$file" ]] && continue

        local safe_file_name
        safe_file_name=$(echo "$file" | tr '/' '_')
        local scenario_dir="$DATASET_DIR/$lang/$proj_name/${short_sha}__${safe_file_name}"

        [[ -d "$scenario_dir" ]] && continue

        mkdir -p "$scenario_dir"
        extract_file_at_commit "$repo" "$base_sha"  "$file" "$scenario_dir/base/$file"
        extract_file_at_commit "$repo" "$p1"        "$file" "$scenario_dir/left/$file"
        extract_file_at_commit "$repo" "$p2"        "$file" "$scenario_dir/right/$file"
        extract_file_at_commit "$repo" "$merge_sha" "$file" "$scenario_dir/merged/$file"

        cat > "$scenario_dir/metadata.json" <<EOF
{
  "language": "$lang",
  "project": "$proj_name",
  "merge_commit": "$merge_sha",
  "merge_commit_short": "$short_sha",
  "base_commit": "$base_sha",
  "left_commit": "$p1",
  "right_commit": "$p2",
  "file": "$file"
}
EOF
        scenario_count=$((scenario_count + 1))
    done <<< "$common_files"

    if (( scenario_count > 0 )); then
        log "  Merge $short_sha: $scenario_count cenario(s)"
    fi
}

process_project() {
    local lang="$1"; local proj_name="$2"; local proj_url="$3"
    local repo="$REPOS_DIR/$proj_name"

    log "=== Projeto: $proj_name ($lang) ==="

    if ! clone_if_needed "$proj_name" "$proj_url"; then
        warn "Pulando $proj_name por falha de clone."
        return
    fi

    local merge_shas total
    merge_shas=$(git -C "$repo" log \
                     --merges \
                     --since="$SINCE_DATE" \
                     --until="$UNTIL_DATE" \
                     --format="%H" \
                     | head -n "$MAX_SCENARIOS_PER_PROJECT")
    total=$(echo "$merge_shas" | grep -c . || true)
    log "$total merge commits em $proj_name (limite: $MAX_SCENARIOS_PER_PROJECT)."

    local i=0
    while IFS= read -r sha; do
        [[ -z "$sha" ]] && continue
        i=$((i + 1))
        if (( i % 20 == 0 )); then
            log "  ... processando merge $i/$total"
        fi
        process_merge_commit "$lang" "$proj_name" "$repo" "$sha"
    done <<< "$merge_shas"
}

main() {
    mkdir -p "$DATASET_DIR" "$REPOS_DIR"
    log "Dataset: $DATASET_DIR"
    log "Repos:   $REPOS_DIR"
    log "Janela:  $SINCE_DATE ate $UNTIL_DATE"
    log "Limite por projeto: $MAX_SCENARIOS_PER_PROJECT merges"
    log "Total de projetos: ${#PROJECTS[@]}"
    echo

    for entry in "${PROJECTS[@]}"; do
        IFS='|' read -r lang name url <<< "$entry"
        process_project "$lang" "$name" "$url"
        echo
    done

    local total_scenarios
    total_scenarios=$(find "$DATASET_DIR" -name "metadata.json" | wc -l)
    log "=================================================="
    log "EXTRACAO CONCLUIDA"
    log "Total de cenarios extraidos: $total_scenarios"
    log "Dataset disponivel em: $DATASET_DIR"
    log "=================================================="
}

main "$@"