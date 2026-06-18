#!/usr/bin/env python3
"""
SepMerge++ — Agregador de resultados

Lê o arquivo results.csv produzido por 02_run_tools.sh e gera as tabelas
agregadas no formato do paper Araujo et al. (2024):
  - Conflitos reportados por ferramenta (total e por linguagem)
  - Arquivos com conflitos por ferramenta
  - Cenários com conflitos por ferramenta
  - Contagem de aFP e aFN na comparação SepMerge++ vs diff3 e CSDiff vs diff3

Uso:
    python3 03_aggregate.py results.csv
"""

from __future__ import annotations
import csv
import sys
from collections import defaultdict
from pathlib import Path


def read_csv(path: Path) -> list[dict]:
    with path.open(encoding="utf-8") as f:
        reader = csv.DictReader(f)
        return list(reader)


def pivot_by_scenario(rows: list[dict]) -> dict[tuple, dict[str, dict]]:
    """
    Reorganiza as linhas do CSV em um mapa:
       (lang, project, merge_sha, file)  ->  { tool -> {conflicts, classification} }
    """
    out: dict[tuple, dict[str, dict]] = defaultdict(dict)
    for r in rows:
        key = (r["language"], r["project"], r["merge_sha"], r["file"])
        out[key][r["tool"]] = {
            "conflicts": int(r["conflicts"]),
            "classification": r["classification"],
        }
    return out


def table_conflicts_by_language(scenarios: dict) -> None:
    """Tabela 2 do paper: conflitos reportados por ferramenta, por linguagem."""
    print("\n=== Tabela 1 — Conflitos reportados (total por linguagem) ===")
    print(f"{'Linguagem':<12} {'diff3':>8} {'CSDiff':>8} {'SepMerge++':>12}")
    print("-" * 44)

    by_lang = defaultdict(lambda: {"diff3": 0, "csdiff": 0, "sepmerge": 0})
    for (lang, _, _, _), tools in scenarios.items():
        for tool in ("diff3", "csdiff", "sepmerge"):
            if tool in tools:
                by_lang[lang][tool] += tools[tool]["conflicts"]

    totals = {"diff3": 0, "csdiff": 0, "sepmerge": 0}
    for lang in sorted(by_lang):
        row = by_lang[lang]
        print(f"{lang:<12} {row['diff3']:>8} {row['csdiff']:>8} {row['sepmerge']:>12}")
        for t in totals:
            totals[t] += row[t]
    print("-" * 44)
    print(f"{'TOTAL':<12} {totals['diff3']:>8} {totals['csdiff']:>8} {totals['sepmerge']:>12}")

    if totals["diff3"]:
        red_vs_diff3 = 100 * (totals["diff3"] - totals["sepmerge"]) / totals["diff3"]
        print(f"\nRedução SepMerge++ vs diff3: {red_vs_diff3:.1f}%")
    if totals["csdiff"]:
        red_vs_csdiff = 100 * (totals["csdiff"] - totals["sepmerge"]) / totals["csdiff"]
        print(f"Redução SepMerge++ vs CSDiff: {red_vs_csdiff:.1f}%")


def table_files_and_scenarios_with_conflicts(scenarios: dict) -> None:
    """Tabelas agregadas: quantos arquivos/cenários tiveram conflitos em cada ferramenta."""
    print("\n=== Tabela 2 — Arquivos com conflitos reportados ===")

    files_with_conflicts = {"diff3": 0, "csdiff": 0, "sepmerge": 0}
    for _, tools in scenarios.items():
        for tool in ("diff3", "csdiff", "sepmerge"):
            if tool in tools and tools[tool]["conflicts"] > 0:
                files_with_conflicts[tool] += 1

    total_files = len(scenarios)
    print(f"Total de arquivos analisados: {total_files}")
    print(f"{'Ferramenta':<15} {'Com conflitos':>15} {'% do total':>12}")
    print("-" * 44)
    for tool in ("diff3", "csdiff", "sepmerge"):
        count = files_with_conflicts[tool]
        pct = 100 * count / total_files if total_files else 0
        print(f"{tool:<15} {count:>15} {pct:>11.1f}%")

    # Cenários (agrupados por merge_sha, independente do arquivo)
    print("\n=== Tabela 3 — Cenários de integração com conflitos ===")
    scenarios_by_merge: dict[tuple, dict[str, bool]] = defaultdict(
        lambda: {"diff3": False, "csdiff": False, "sepmerge": False}
    )
    for (lang, proj, merge_sha, _), tools in scenarios.items():
        key = (lang, proj, merge_sha)
        for tool in ("diff3", "csdiff", "sepmerge"):
            if tool in tools and tools[tool]["conflicts"] > 0:
                scenarios_by_merge[key][tool] = True

    total_merges = len(scenarios_by_merge)
    merges_with_conflicts = {"diff3": 0, "csdiff": 0, "sepmerge": 0}
    for flags in scenarios_by_merge.values():
        for tool, has in flags.items():
            if has:
                merges_with_conflicts[tool] += 1

    print(f"Total de cenários (merges): {total_merges}")
    print(f"{'Ferramenta':<15} {'Com conflitos':>15} {'% do total':>12}")
    print("-" * 44)
    for tool in ("diff3", "csdiff", "sepmerge"):
        count = merges_with_conflicts[tool]
        pct = 100 * count / total_merges if total_merges else 0
        print(f"{tool:<15} {count:>15} {pct:>11.1f}%")


def table_aFP_aFN(scenarios: dict) -> None:
    """
    Tabela de erros de identificação (aFP/aFN) comparando pares de ferramentas.
    Reproduz as Tabelas 3 e 4 do paper Araujo et al.
    """
    print("\n=== Tabela 4 — Erros de identificação de conflitos (arquivos) ===")

    def compare_pair(X: str, Y: str) -> tuple[int, int]:
        """
        Retorna (aFP_X, aFN_X): quantos arquivos onde X errou na comparação com Y.
        aFP de X: X reportou conflito, Y não reportou E Y resolveu corretamente.
        aFN de X: X não reportou conflito E X está errado, mas Y reportou conflito.
        """
        aFP = 0
        aFN = 0
        for _, tools in scenarios.items():
            if X not in tools or Y not in tools:
                continue
            tx = tools[X]
            ty = tools[Y]

            # aFP: X tem conflitos, Y não tem, e Y está correto
            if tx["conflicts"] > 0 and ty["conflicts"] == 0 and ty["classification"] == "correct":
                aFP += 1
            # aFN: X sem conflito mas incorreto; Y com conflito
            if tx["conflicts"] == 0 and tx["classification"] == "aFN" and ty["conflicts"] > 0:
                aFN += 1
        return aFP, aFN

    pairs = [
        ("sepmerge", "diff3"),
        ("csdiff", "diff3"),
    ]
    for X, Y in pairs:
        aFP, aFN = compare_pair(X, Y)
        print(f"\n{X} vs {Y}:")
        print(f"   aFP ({X} acrescenta FP sobre {Y}): {aFP}")
        print(f"   aFN ({X} acrescenta FN sobre {Y}): {aFN}")


def main(argv: list[str]) -> int:
    if len(argv) < 2:
        print(__doc__)
        return 1

    csv_path = Path(argv[1])
    if not csv_path.exists():
        print(f"Arquivo não encontrado: {csv_path}", file=sys.stderr)
        return 2

    rows = read_csv(csv_path)
    scenarios = pivot_by_scenario(rows)

    print(f"Total de linhas no CSV: {len(rows)}")
    print(f"Total de cenários únicos: {len(scenarios)}")

    table_conflicts_by_language(scenarios)
    table_files_and_scenarios_with_conflicts(scenarios)
    table_aFP_aFN(scenarios)

    print("\nFeito.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
