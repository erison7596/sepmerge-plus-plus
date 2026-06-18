# Experimento — SepMerge++

Este diretório contém todo o material para **reproduzir a avaliação empírica**
do TCC *SepMerge++* (Seções 5 e 6 do artigo): a mineração dos cenários de
integração de projetos reais, a execução das três ferramentas (`diff3`, CSDiff
e SepMerge++) e a análise estatística dos resultados, incluindo a análise de
*footprint* (fragmentação vs. piora real).

O pipeline produz os números das Tabelas 2 a 8 do artigo, sobre **2.816
cenários** de integração extraídos de **19 projetos** em C#, Go e Haskell.

---

## Pré-requisitos

- **Bash** (Linux, macOS, ou Git Bash no Windows)
- **Git** 2.40+ (para clonar os projetos e calcular os *merges*)
- **`diff3`** no PATH (GNU diffutils; no Windows vem com o Git for Windows)
- **OpenJDK 21+** (para rodar a ferramenta)
- **Python 3.10+** (para a agregação e a análise de *footprint*)
- A ferramenta compilada: `sepmerge.jar`

O experimento foi executado em **Windows 11 (Git Bash 2.54.0)** e replicado em
**Ubuntu 24.04**, ambos com **OpenJDK 21.0.10** e **GNU diffutils 3.8**.

### Gerando o `sepmerge.jar`

O jar não é versionado (é artefato de build). Esta pasta fica na raiz do
repositório, ao lado do `pom.xml` do projeto Java. Gere o jar a partir do
código-fonte e copie para cá:

```bash
cd ..                 # raiz do repositório (onde está o pom.xml)
mvn clean package
cp target/sepmerge.jar ExperimentoTCC/sepmerge.jar
cd ExperimentoTCC
```

---

## Estrutura

```
ExperimentoTCC/
├── 01_extract_scenarios.sh      # minera os cenários dos repositórios
├── 02_run_tools.sh              # roda diff3, CSDiff e SepMerge++ em cada cenário
├── 03_aggregate.py              # gera as tabelas agregadas (Tabelas 2-4)
├── 04_fragmentation_analysis.py # análise de footprint (Tabelas 6-7)
├── results.csv                  # MÉTRICAS FINAIS dos 2.816 cenários (versionado)
├── fragmentation_report.txt     # saída da análise de footprint (versionado)
├── dataset/                     # cenários extraídos      (gerado — gitignored)
├── repos/                       # repositórios clonados   (gerado — gitignored)
└── results/                     # saídas por cenário      (gerado — gitignored)
```

Apenas os **scripts**, o `results.csv` e o `fragmentation_report.txt` ficam no
Git. As pastas `dataset/`, `repos/` e `results/` são grandes e podem ser
regeneradas pelos scripts (ver `.gitignore`).

> **Atalho:** se você só quer conferir os números do artigo sem reminerar os
> projetos, pule direto para o **Passo 3** usando o `results.csv` já incluído.

---

## Passo a passo

### Passo 1 — Extrair os cenários de integração

```bash
bash 01_extract_scenarios.sh
```

Para cada projeto da lista interna `PROJECTS`, o script clona o repositório
(em `repos/`), percorre seus *merge commits* no intervalo configurado e, para
cada arquivo modificado nos dois lados, salva as quatro versões do cenário
(`base`, `left`, `right`, `merged`) em `dataset/<linguagem>/<projeto>/...`.

Variáveis de ambiente úteis (todas opcionais):

| Variável | Padrão | Função |
|---|---|---|
| `SINCE_DATE` | `2018-01-01` | data inicial dos *merge commits* |
| `UNTIL_DATE` | `2026-12-31` | data final |
| `MAX_SCENARIOS_PER_PROJECT` | `200` | teto de cenários por projeto |
| `DATASET_DIR` | `./dataset` | onde salvar os cenários |
| `REPOS_DIR` | `./repos` | onde clonar os repositórios |

> A clonagem de todos os 19 projetos baixa **vários GB** e pode levar bastante
> tempo. Os projetos a minerar são definidos no array `PROJECTS` no topo do
> script (comente/descomente conforme necessário).

### Passo 2 — Executar as ferramentas e coletar as métricas

```bash
bash 02_run_tools.sh
```

Para cada cenário em `dataset/`, executa as três ferramentas e registra, em
`results.csv`, uma linha por (cenário × ferramenta) com:
`language, project, merge_sha, file, tool, conflicts, classification`.

O `conflicts` é o número de blocos de conflito; o `classification` é um de
`correct`, `merge_diferente`, `reportou_conflito` ou `vazio`, comparando a
saída com a versão `merged` consolidada pelo desenvolvedor (após normalização
de BOM, CRLF e *whitespace* final).

Variáveis úteis: `SEPMERGE_JAR` (padrão `./sepmerge.jar`) e `DIFF3_CMD`
(padrão `diff3`).

### Passo 3 — Agregar os resultados (Tabelas 2 a 4)

```bash
python3 03_aggregate.py results.csv
```

Imprime as tabelas agregadas no formato do artigo: blocos de conflito por
ferramenta (total e por linguagem), cenários com ao menos um conflito, e a
contagem de aFP/aFN nas comparações SepMerge++ vs `diff3` e CSDiff vs `diff3`.

### Passo 4 — Análise de *footprint* (Tabelas 6 e 7)

```bash
python3 04_fragmentation_analysis.py results.csv
```

Classifica cada cenário em *ambos limpos*, *equivalentes*, *ganho real*,
*fragmentação* ou *piora real*, comparando número de blocos **e** *footprint*
(linhas dentro dos blocos). Imprime o resumo no terminal e grava
`fragmentation_report.txt`.

---

## Resultados esperados

Rodando os Passos 3 e 4 sobre o `results.csv` incluído, você deve reproduzir
exatamente os números do artigo, entre eles:

- **5.486** blocos no `diff3`, **9.760** no CSDiff e **5.677** no SepMerge++
  (Tabela 2) — redução de **41,8%** vs CSDiff;
- **zero aFP** do SepMerge++ vs `diff3` e **4 aFP** do CSDiff vs `diff3`
  (Tabela 4);
- **97,3%** dos cenários sem piora de área e **2,7%** de piora real
  (Tabela 6).
