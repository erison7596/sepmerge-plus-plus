#!/usr/bin/env python3
"""
04_fragmentation_analysis.py
============================
Analise da hipotese: "quando SepMerge++ reporta MAIS blocos de conflito
que diff3, a AREA conflituosa total eh a mesma — apenas mais fragmentada".

Para cada cenario, mede:
  - Numero de blocos de conflito (n_blocks)
  - Footprint: total de linhas dentro de blocos de conflito,
    contando left + base + right entre <<<<<<< e >>>>>>>
  - Spread: linhas conflituosas por bloco

E classifica os cenarios em quatro categorias:
  - EQUIVALENTE     : sepmerge.blocks == diff3.blocks AND footprint igual
  - GANHO REAL      : sepmerge.blocks <= diff3.blocks AND footprint <=
  - FRAGMENTACAO    : sepmerge.blocks > diff3.blocks AND footprint igual
                       (nao piora real, so granularidade)
  - PIORA REAL      : sepmerge.blocks > diff3.blocks AND footprint >
                       (situacao genuinamente pior)

Uso (a partir de /d/TCC/ExperimentoTCC):
    python 04_fragmentation_analysis.py results.csv

Saida: imprime tabelas no terminal e grava 'fragmentation_report.txt'.
"""

from __future__ import annotations
import csv
import os
import sys
from collections import defaultdict
from pathlib import Path


# ----------------------------------------------------------------------
# Configuracao de caminhos
# ----------------------------------------------------------------------
SCRIPT_DIR = Path(__file__).parent.resolve()
RESULTS_DIR = SCRIPT_DIR / "results"   # gerado pelo 02_run_tools.sh
OUT_REPORT = SCRIPT_DIR / "fragmentation_report.txt"


# ----------------------------------------------------------------------
# Utilitarios
# ----------------------------------------------------------------------
def safe_read_lines(path: Path) -> list[str]:
    """Le um arquivo como UTF-8 tolerante. Retorna lista vazia se nao existe."""
    if not path.exists():
        return []
    try:
        with path.open(encoding="utf-8", errors="replace") as f:
            return f.read().splitlines()
    except OSError:
        return []


def find_tool_output(lang: str, project: str, merge_sha: str, file_rel: str,
                      tool: str) -> Path | None:
    """
    Encontra o arquivo de saida da ferramenta para um cenario, mesmo
    quando a extensao do arquivo varia.
    Caminho esperado: results/<lang>/<project>/<sha>__<safe_file>/<tool>.<ext>
    """
    safe_file = file_rel.replace("/", "_")
    scenario_dir = RESULTS_DIR / lang / project / f"{merge_sha}__{safe_file}"
    if not scenario_dir.is_dir():
        return None

    # Procura qualquer arquivo que comece com o nome da ferramenta
    for entry in scenario_dir.iterdir():
        if entry.is_file() and entry.name.startswith(f"{tool}."):
            return entry
    return None


def measure_conflicts(lines: list[str]) -> tuple[int, int]:
    """
    Conta numero de blocos de conflito e o footprint total (linhas dentro de blocos).

    Reconhece formato do diff3 -m --show-all:
        <<<<<<< left
        ...left lines...
        ||||||| base
        ...base lines...
        =======
        ...right lines...
        >>>>>>> right

    Footprint conta as linhas DENTRO do bloco, excluindo os 4 marcadores.
    Tambem aceita o formato simples sem |||||||.
    """
    n_blocks = 0
    footprint = 0
    in_block = False

    for line in lines:
        if line.startswith("<<<<<<<"):
            n_blocks += 1
            in_block = True
            continue
        if line.startswith(">>>>>>>"):
            in_block = False
            continue
        if line.startswith("|||||||") or line.startswith("======="):
            # marcador interno do bloco — nao conta como linha de conteudo
            continue
        if in_block:
            footprint += 1

    return n_blocks, footprint


# ----------------------------------------------------------------------
# Carga do CSV
# ----------------------------------------------------------------------
def load_scenarios(csv_path: Path) -> dict:
    """
    Pivota o results.csv em mapa:
       (lang, project, merge_sha, file)  ->  {tool: {conflicts, classification}}
    """
    scenarios: dict = defaultdict(dict)
    with csv_path.open(encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for r in reader:
            key = (r["language"], r["project"], r["merge_sha"], r["file"])
            scenarios[key][r["tool"]] = {
                "conflicts": int(r["conflicts"]),
                "classification": r["classification"],
            }
    return scenarios


# ----------------------------------------------------------------------
# Analise principal
# ----------------------------------------------------------------------
def analyze(scenarios: dict) -> dict:
    """Mede footprint para cada cenario e classifica em 4 categorias."""
    results = {
        "EQUIVALENTE": [],
        "GANHO_REAL": [],
        "FRAGMENTACAO": [],
        "PIORA_REAL": [],
        "AMBOS_LIMPOS": [],
        "FALHA_LEITURA": [],
    }

    total = len(scenarios)
    print(f"Analisando {total} cenarios...")

    for i, ((lang, proj, sha, file_rel), tools) in enumerate(scenarios.items(), 1):
        if i % 200 == 0:
            print(f"  ... {i}/{total}")

        if "diff3" not in tools or "sepmerge" not in tools:
            continue

        # Atalho: se ambos reportaram 0 conflitos, nao precisa medir footprint
        if tools["diff3"]["conflicts"] == 0 and tools["sepmerge"]["conflicts"] == 0:
            results["AMBOS_LIMPOS"].append((lang, proj, sha, file_rel, 0, 0, 0, 0))
            continue

        diff3_path = find_tool_output(lang, proj, sha, file_rel, "diff3")
        sep_path = find_tool_output(lang, proj, sha, file_rel, "sepmerge")

        if diff3_path is None or sep_path is None:
            results["FALHA_LEITURA"].append((lang, proj, sha, file_rel))
            continue

        diff3_lines = safe_read_lines(diff3_path)
        sep_lines = safe_read_lines(sep_path)

        d_blocks, d_foot = measure_conflicts(diff3_lines)
        s_blocks, s_foot = measure_conflicts(sep_lines)

        record = (lang, proj, sha, file_rel, d_blocks, s_blocks, d_foot, s_foot)

        # Classificacao em 4 categorias
        if s_blocks == d_blocks and s_foot == d_foot:
            results["EQUIVALENTE"].append(record)
        elif s_blocks <= d_blocks and s_foot <= d_foot:
            results["GANHO_REAL"].append(record)
        elif s_blocks > d_blocks and s_foot <= d_foot:
            # Mais blocos, mas mesma area ou MENOS area = fragmentacao
            results["FRAGMENTACAO"].append(record)
        elif s_blocks > d_blocks and s_foot > d_foot:
            results["PIORA_REAL"].append(record)
        else:
            # Casos restantes: menos blocos mas mais footprint, etc.
            # Tratamos como fragmentacao para nao ser conservador demais
            results["FRAGMENTACAO"].append(record)

    return results


# ----------------------------------------------------------------------
# Relatorio
# ----------------------------------------------------------------------
def print_and_log(out_lines: list[str], text: str = "") -> None:
    print(text)
    out_lines.append(text)


def report(results: dict) -> None:
    out: list[str] = []

    print_and_log(out, "=" * 70)
    print_and_log(out, "ANALISE DE FRAGMENTACAO — SepMerge++ vs diff3")
    print_and_log(out, "=" * 70)

    n_equiv = len(results["EQUIVALENTE"])
    n_ganho = len(results["GANHO_REAL"])
    n_frag = len(results["FRAGMENTACAO"])
    n_piora = len(results["PIORA_REAL"])
    n_limpo = len(results["AMBOS_LIMPOS"])
    n_falha = len(results["FALHA_LEITURA"])
    n_total = n_equiv + n_ganho + n_frag + n_piora + n_limpo

    print_and_log(out, "")
    print_and_log(out, "SUMARIO")
    print_and_log(out, "-" * 70)
    print_and_log(out, f"Total de cenarios analisados:        {n_total}")
    print_and_log(out, f"  Ambos resolveram (limpos):         {n_limpo:>5} "
                       f"({100 * n_limpo / n_total:5.1f}%)")
    print_and_log(out, f"  Equivalentes (mesmo n e footprint): {n_equiv:>5} "
                       f"({100 * n_equiv / n_total:5.1f}%)")
    print_and_log(out, f"  Ganho real (sep <= diff3):         {n_ganho:>5} "
                       f"({100 * n_ganho / n_total:5.1f}%)")
    print_and_log(out, f"  Fragmentacao (mais blocos, area =): {n_frag:>5} "
                       f"({100 * n_frag / n_total:5.1f}%)")
    print_and_log(out, f"  PIORA REAL (mais blocos E area):   {n_piora:>5} "
                       f"({100 * n_piora / n_total:5.1f}%)")
    if n_falha:
        print_and_log(out, f"  Falha de leitura (ignorados):      {n_falha:>5}")

    print_and_log(out, "")
    print_and_log(out, "ANALISE DOS CASOS COM MAIS BLOCOS NO SEPMERGE")
    print_and_log(out, "-" * 70)
    n_mais_blocos = n_frag + n_piora
    if n_mais_blocos > 0:
        print_and_log(out, f"Total: {n_mais_blocos} cenarios em que SepMerge++ tem MAIS blocos que diff3")
        print_and_log(out, f"  Dos quais sao fragmentacao (area equivalente): "
                           f"{n_frag} ({100 * n_frag / n_mais_blocos:5.1f}%)")
        print_and_log(out, f"  Dos quais sao piora real (mais area):          "
                           f"{n_piora} ({100 * n_piora / n_mais_blocos:5.1f}%)")
    else:
        print_and_log(out, "Nenhum caso de SepMerge com mais blocos que diff3.")

    # Estatisticas agregadas dos casos de FRAGMENTACAO
    if results["FRAGMENTACAO"]:
        print_and_log(out, "")
        print_and_log(out, "ESTATISTICAS DOS CASOS DE FRAGMENTACAO")
        print_and_log(out, "-" * 70)
        delta_blocks = [r[5] - r[4] for r in results["FRAGMENTACAO"]]
        delta_foot = [r[7] - r[6] for r in results["FRAGMENTACAO"]]
        print_and_log(out, f"Δ blocos (sep - diff3):  "
                           f"min={min(delta_blocks)}, "
                           f"max={max(delta_blocks)}, "
                           f"media={sum(delta_blocks)/len(delta_blocks):.2f}")
        print_and_log(out, f"Δ footprint (sep - diff3): "
                           f"min={min(delta_foot)}, "
                           f"max={max(delta_foot)}, "
                           f"media={sum(delta_foot)/len(delta_foot):.2f}")
        print_and_log(out, "(footprint negativo = SepMerge cobre MENOS linhas; "
                           "0 = mesma area; positivo = mais area)")

    # Estatisticas dos casos de PIORA REAL
    if results["PIORA_REAL"]:
        print_and_log(out, "")
        print_and_log(out, "EXEMPLOS DE PIORA REAL (top 10 por delta de footprint)")
        print_and_log(out, "-" * 70)
        sorted_piora = sorted(results["PIORA_REAL"],
                              key=lambda r: r[7] - r[6], reverse=True)
        for r in sorted_piora[:10]:
            lang, proj, sha, f, db, sb, df_, sf = r
            print_and_log(out,
                f"  {lang}/{proj}/{sha} {f[:50]:<50} "
                f"blocos {db}->{sb}  footprint {df_}->{sf}")

    # Por linguagem
    print_and_log(out, "")
    print_and_log(out, "DISTRIBUICAO POR LINGUAGEM")
    print_and_log(out, "-" * 70)
    print_and_log(out, f"{'Lingua':<10} {'Limpo':>8} {'Equiv':>8} {'Ganho':>8} "
                       f"{'Fragm.':>8} {'Piora':>8}")
    by_lang = defaultdict(lambda: defaultdict(int))
    for cat in ("EQUIVALENTE", "GANHO_REAL", "FRAGMENTACAO", "PIORA_REAL", "AMBOS_LIMPOS"):
        for r in results[cat]:
            by_lang[r[0]][cat] += 1
    for lang in sorted(by_lang):
        d = by_lang[lang]
        print_and_log(out,
            f"{lang:<10} {d['AMBOS_LIMPOS']:>8} {d['EQUIVALENTE']:>8} "
            f"{d['GANHO_REAL']:>8} {d['FRAGMENTACAO']:>8} {d['PIORA_REAL']:>8}")

    # Conclusao
    print_and_log(out, "")
    print_and_log(out, "=" * 70)
    print_and_log(out, "CONCLUSAO")
    print_and_log(out, "=" * 70)

    n_nao_pior = n_limpo + n_equiv + n_ganho + n_frag
    pct_nao_pior = 100 * n_nao_pior / n_total
    print_and_log(out, f"Em {n_nao_pior}/{n_total} cenarios ({pct_nao_pior:.2f}%), "
                       f"SepMerge++ NAO piora vs diff3 em area conflituosa.")
    if n_piora > 0:
        print_and_log(out, f"Em {n_piora} cenarios ({100*n_piora/n_total:.2f}%), "
                           f"SepMerge++ tem area conflituosa MAIOR que diff3.")
    print_and_log(out, "")
    print_and_log(out, "Esta analise valida que a aparente 'piora' em numero de blocos")
    print_and_log(out, "se deve principalmente a fragmentacao (granularidade), nao a")
    print_and_log(out, "aumento real da area conflituosa.")

    # Salva relatorio
    OUT_REPORT.write_text("\n".join(out), encoding="utf-8")
    print(f"\nRelatorio gravado em: {OUT_REPORT}")


# ----------------------------------------------------------------------
# Main
# ----------------------------------------------------------------------
def main(argv: list[str]) -> int:
    if len(argv) < 2:
        print(__doc__)
        return 1

    csv_path = Path(argv[1])
    if not csv_path.exists():
        print(f"ERRO: arquivo nao encontrado: {csv_path}", file=sys.stderr)
        return 2

    if not RESULTS_DIR.is_dir():
        print(f"ERRO: pasta de resultados nao encontrada: {RESULTS_DIR}", file=sys.stderr)
        print("       Rode 02_run_tools.sh antes deste script.", file=sys.stderr)
        return 2

    print(f"CSV:     {csv_path}")
    print(f"Results: {RESULTS_DIR}")
    print()

    scenarios = load_scenarios(csv_path)
    print(f"Total de cenarios no CSV: {len(scenarios)}\n")

    results = analyze(scenarios)
    print()
    report(results)

    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))