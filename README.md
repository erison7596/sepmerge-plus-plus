# SepMerge++

> Extensão poliglota do CSDiff/SepMerge com abordagem focalizada para detecção
> e resolução de conflitos em **C#**, **Go** e **Haskell**.

O **SepMerge++** é uma ferramenta de integração de código (*merge*)
sintaticamente consciente. Combina a leveza das ferramentas textuais como o
`diff3` com o conhecimento sintático de abordagens estruturadas, atuando de
forma **focalizada**: separadores sintáticos da linguagem são aplicados
**apenas dentro dos blocos de conflito** previamente identificados pelo `diff3`,
preservando a propriedade de nunca introduzir falsos positivos em relação a ele.

Trabalho desenvolvido como TCC do curso de Engenharia de Software no
**Instituto Federal de Pernambuco (IFPE) — Campus Belo Jardim**.

---

## ✨ Principais Funcionalidades

- **Abordagem Focalizada** — atua cirurgicamente apenas nos blocos onde o
  `diff3` reportou conflito, evitando o ruído do CSDiff original que
  pré-processava o arquivo inteiro.
- **Multi-linguagem (Strategy)** — suporta múltiplas linguagens com a mesma
  base de código; a linguagem é detectada automaticamente pela extensão.
- **Robustez em ambiente real** — tratamento explícito de Byte Order Mark
  (BOM, comum em arquivos C# do Visual Studio), encoding UTF-8 ponta a ponta,
  e reversibilidade em ponto fixo do pré-processamento.
- **Integração transparente com Git** — pode ser registrada como *merge driver*
  via `.gitattributes`, sendo invocada automaticamente em `git merge`,
  `git rebase` e `git cherry-pick`.

---

## 🛠 Linguagens Suportadas

A ferramenta vem com separadores sintáticos otimizados para três linguagens
de perfis distintos:

| Linguagem | Extensão | Separadores |
|-----------|:--------:|-------------|
| **C#**      | `.cs`    | `=>` `{` `}` `,` `(` `)` `;` `[` `]` `:` |
| **Go**      | `.go`    | `:=` `{` `}` `,` `(` `)` `;` `[` `]`     |
| **Haskell** | `.hs`    | `::` `->` `=>` `<-` `,` `(` `)` `[` `]` `{` `}` `;` |

> **Decisão de design (Seção 4.4 do paper):** os tokens `=` e `|` em Haskell
> aparecem com altíssima frequência (toda definição de função, *guard*, *data
> constructor*) e foram **excluídos** do conjunto padrão para evitar inflar o
> arquivo pré-processado em proporção maior que o ganho semântico.

Arquivos com extensões não reconhecidas usam o `CSharpProcessor` como
*fallback* (com aviso no `stderr`).

---

## 🏗 Arquitetura

O projeto usa o padrão **Strategy** combinado com **Template Method**:

```
                  ┌──────────────────────────┐
                  │  «interface»             │
                  │  LanguageProcessor       │
                  └──────────────┬───────────┘
                                 │
                  ┌──────────────┴───────────┐
                  │  «abstract»              │
                  │  BaseProcessor           │
                  │  (logica de regex e      │
                  │   reversao em ponto fixo)│
                  └──────────────┬───────────┘
                                 │
              ┌──────────────────┼──────────────────┐
              ▼                  ▼                  ▼
   ┌──────────────────┐ ┌────────────────┐ ┌───────────────────┐
   │ CSharpProcessor  │ │ GoProcessor    │ │ HaskellProcessor  │
   └──────────────────┘ └────────────────┘ └───────────────────┘
```

- **`LanguageProcessor`** define o contrato (`preprocessCodeBlock`,
  `postprocessCodeBlock`, `getExtension`).
- **`BaseProcessor`** centraliza toda a lógica pesada — aplicação de regex,
  reversibilidade em ponto fixo de separadores adjacentes, normalização de
  marcadores de conflito.
- **Processadores concretos** declaram apenas o regex e a lista de
  separadores da sua linguagem. Não duplicam lógica.
- **`SepMergeEngine`** é o motor de resolução, totalmente agnóstico à
  linguagem; recebe o `LanguageProcessor` por injeção.

### Vantagens

- **Aberto/Fechado**: adicionar suporte a uma nova linguagem (ex.: Java, Rust)
  requer apenas uma classe nova de ~10 linhas, sem alterar o motor.
- **Testabilidade**: o motor é testável independentemente dos processadores;
  cada processador é testável independentemente do motor.
- **Manutenção isolada**: ajustes em separadores de uma linguagem não afetam
  as outras.

---

## 🔧 Robustez (Seção 4.5 do paper)

Três classes de problemas práticos de ambientes heterogêneos foram tratadas:

1. **Byte Order Mark (BOM)** — arquivos `.cs` criados pelo Visual Studio
   incluem `EF BB BF` (U+FEFF) no início. A `FileUtils.stripBom` remove o BOM
   na leitura, todos os arquivos temporários são gravados em UTF-8 sem BOM, e
   o `stdout` é forçado a UTF-8 para evitar `?` em consoles Windows.
2. **Reversibilidade em ponto fixo** — separadores adjacentes (`))`, `}}`,
   `};`) geram sequências `\nX\n\nY\n` no pré-processamento. O pós-processamento
   itera as substituições em `do/while` até atingir um ponto fixo, garantindo
   reversibilidade completa independentemente da ordem de adjacência.
3. **Preservação de quebras de linha** — o método `appendAsLines` divide o
   resultado de cada bloco em linhas individuais antes de adicionar à saída
   final, eliminando linhas em branco espúrias após blocos de conflito
   resolvidos.

---

## 🚀 Como Compilar e Usar

### Pré-requisitos

- **JDK 21+** (Adoptium/Temurin, Oracle, Corretto, etc.)
- **Maven 3.9+** (já vem com IntelliJ; opcional fora do IDE)
- **`diff3` no PATH** — em Linux/Mac vem por padrão; no Windows, instale o
  Git for Windows e adicione `C:\Program Files\Git\usr\bin` ao PATH.

### 1. Compilar

```bash
mvn clean package
```

Será gerado um *fat-jar* em `target/sepmerge.jar`.

### 2. Executar manualmente

```bash
java -jar target/sepmerge.jar <BASE> <LEFT> <RIGHT> > saida.cs
```

A ferramenta emite:
- o código-fonte resolvido em **stdout**
- avisos e erros em **stderr** (via `java.util.logging`)

Essa separação permite que ferramentas de mineração capturem a saída sem
contaminação por logs.

### 3. Rodar a suíte de testes

```bash
mvn test
```

A suíte inclui **8 classes de teste JUnit 5** cobrindo:

| Seção do paper | Classe de teste |
|----------------|-----------------|
| 4.2 (Strategy + Template Method) | `BaseProcessorTest` |
| 4.4.1 (CSharpProcessor) | `CSharpProcessorTest` |
| 4.4.2 (GoProcessor) | `GoProcessorTest` |
| 4.4.3 (HaskellProcessor) | `HaskellProcessorTest` |
| 4.5.1 (BOM) | `FileUtilsTest`, `Diff3RunnerTest`, `RobustnessRegressionTest` |
| 4.5.2 (Ponto fixo) | `BaseProcessorTest`, `RobustnessRegressionTest` |
| 4.5.3 (Quebras de linha) | `RobustnessRegressionTest` |
| 5.3 (Cenários controlados C1–C4) | `SepMergeEngineTest` |
| 6.2 (Não introdução de aFP) | `SepMergeEngineTest::neverReportsMoreConflictsThanDiff3` |

Testes que dependem do `diff3` do sistema usam `Assumptions.assumeTrue` e são
**automaticamente pulados** se ele não estiver no PATH.

---

## 🔗 Integração com Git como Merge Driver

Para usar o SepMerge++ automaticamente em todas as operações de merge do Git:

1. Registre o driver em `.git/config` (ou `~/.gitconfig` para uso global):

   ```ini
   [merge "sepmerge"]
       name = SepMerge++ syntactic-aware merge
       driver = java -jar /caminho/absoluto/para/sepmerge.jar %O %A %B > %A.tmp && mv %A.tmp %A
   ```

2. Associe extensões ao driver via `.gitattributes` na raiz do repositório:

   ```
   *.cs merge=sepmerge
   *.go merge=sepmerge
   *.hs merge=sepmerge
   ```

A partir daí, qualquer `git merge`, `git rebase` ou `git cherry-pick`
invocará o SepMerge++ automaticamente. Se o JAR falhar por qualquer razão,
o Git recua para o `diff3` padrão — adoção *zero-risk*.

---

## ➕ Como adicionar uma nova linguagem

Graças ao Strategy, leva poucos minutos:

1. Crie a classe (ex.: `JavaProcessor.java`) estendendo `BaseProcessor`:

   ```java
   public class JavaProcessor extends BaseProcessor {
       private static final String JAVA_REGEX =
           "(\\{|\\}|,|\\(|\\)|;|\\[|\\])";
       private static final String[] JAVA_SEPARATORS = {
           "{", "}", ",", "(", ")", ";", "[", "]"
       };
       @Override protected String getRegex() { return JAVA_REGEX; }
       @Override protected String[] getSeparatorsList() { return JAVA_SEPARATORS; }
       @Override public String getExtension() { return "java"; }
   }
   ```

2. Adicione a verificação em `Main.determineLanguageProcessor`.

3. Recompile com `mvn package`.

Nenhuma outra classe precisa ser tocada.

---

## 🧪 Reproduzir o experimento (avaliação empírica)

Toda a avaliação empírica do TCC — mineração dos 2.816 cenários de 19 projetos
reais, execução das três ferramentas (`diff3`, CSDiff e SepMerge++) e análise
estatística (incluindo a análise de *footprint*) — está documentada e
automatizada por scripts na pasta **`ExperimentoTCC/`**, que tem seu próprio
`README.md` explicando o pipeline passo a passo.

Resumo do fluxo:

```bash
# 1. compile a ferramenta e leve o jar para a pasta do experimento
mvn clean package
cp target/sepmerge.jar ExperimentoTCC/sepmerge.jar

cd ExperimentoTCC
bash    01_extract_scenarios.sh        # minera os cenários dos repositórios
bash    02_run_tools.sh                # roda as 3 ferramentas -> results.csv
python3 03_aggregate.py results.csv    # Tabelas 2-4 do artigo
python3 04_fragmentation_analysis.py results.csv  # Tabelas 6-7 (footprint)
```

Para apenas **conferir os números** sem reminerar os projetos, rode os passos 3
e 4 sobre o `results.csv` já incluído. Detalhes, pré-requisitos e resultados
esperados estão em `ExperimentoTCC/README.md`.

---

## 📁 Estrutura do projeto

```
sepmerge-polyglot/
├── pom.xml
├── README.md
└── src/
    ├── main/java/br/ifpe/sepmerge/
    │   ├── BaseProcessor.java
    │   ├── CSharpProcessor.java
    │   ├── Diff3Runner.java
    │   ├── FileUtils.java
    │   ├── GoProcessor.java
    │   ├── HaskellProcessor.java
    │   ├── LanguageProcessor.java
    │   ├── Main.java
    │   └── SepMergeEngine.java
    └── test/java/br/ifpe/sepmerge/
        ├── BaseProcessorTest.java
        ├── CSharpProcessorTest.java
        ├── Diff3RunnerTest.java
        ├── FileUtilsTest.java
        ├── GoProcessorTest.java
        ├── HaskellProcessorTest.java
        ├── RobustnessRegressionTest.java
        └── SepMergeEngineTest.java
```

---

## 📚 Referências

Este projeto estende e dialoga diretamente com:

- **Clementino, Borba & Cavalcanti (2021).** *Textual merge based on
  language-specific syntactic separators.* SBES 2021.
- **Araujo, Borba & Cavalcanti (2024).** *Refinando a Precisão da Detecção
  de Conflitos: Uma Análise do CSDiff com Abordagem Focalizada.* SBES 2024.

---

## 📝 Citação

Se este trabalho for útil para sua pesquisa, considere citar o TCC associado:

> ALVES, E. C.; SILVA, J. M. S. **SepMerge++: Uma Extensão Poliglota do
> CSDiff com Abordagem Focalizada para Detecção e Resolução de Conflitos em
> C#, Go e Haskell.** Trabalho de Conclusão de Curso (Engenharia de
> Software) — Instituto Federal de Pernambuco, Belo Jardim, 2026.

---

## 👥 Autores

- **Erison Cavalcante Alves** — eca8@discente.ifpe.edu.br
- **Jose Mirosmar dos Santos Silva** — jmss@discente.ifpe.edu.br

**Orientador:** Guilherme José de Carvalho Cavalcanti — guilherme.cavalcanti@belojardim.ifpe.edu.br