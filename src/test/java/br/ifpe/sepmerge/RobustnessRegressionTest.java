package br.ifpe.sepmerge;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Testes de regressao para os tres problemas de robustez do paper (Secao 4.5):
 *   4.5.1 - Tratamento de Byte Order Mark (BOM)
 *   4.5.2 - Reversibilidade em Ponto Fixo
 *   4.5.3 - Preservacao de Quebras de Linha na Agregacao
 * Esses testes garantem que as correcoes especificadas no artigo
 * permanecem em vigor e nao regridem em alteracoes futuras.
 */
class RobustnessRegressionTest {

    private static boolean diff3Available;

    @BeforeAll
    static void checkDiff3() {
        try {
            Process p = new ProcessBuilder("diff3", "--version").start();
            p.waitFor();
            diff3Available = (p.exitValue() == 0);
        } catch (Exception e) {
            diff3Available = false;
        }
    }

    // ============================================================
    // SECAO 4.5.1 - Tratamento de BOM
    // ============================================================

    @Test
    @DisplayName("4.5.1: arquivo C# com BOM nao corrompe a saida")
    void bomFileDoesNotCorruptOutput(@TempDir Path tmp) throws Exception {
        assumeTrue(diff3Available);

        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        String content = "public class Foo { }\n";
        byte[] body = content.getBytes(StandardCharsets.UTF_8);
        byte[] full = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, full, 0, bom.length);
        System.arraycopy(body, 0, full, bom.length, body.length);

        Path bp = tmp.resolve("base.cs");
        Path lp = tmp.resolve("left.cs");
        Path rp = tmp.resolve("right.cs");
        Files.write(bp, full);
        Files.write(lp, full);
        Files.write(rp, full);

        List<String> diff3Output = Diff3Runner.runDiff3(
                lp.toString(), bp.toString(), rp.toString());
        List<String> result = SepMergeEngine.resolveConflicts(
                diff3Output, new CSharpProcessor());

        for (String line : result) {
            assertFalse(line.contains("\uFEFF"),
                    "BOM (U+FEFF) nao deveria estar presente. Linha=" + line);
        }
    }

    // ============================================================
    // SECAO 4.5.2 - Reversibilidade em Ponto Fixo
    // ============================================================

    @Test
    @DisplayName("4.5.2: reversibilidade com }} (chamada aninhada)")
    void fixedPointReversalDoubleAdjacent() {
        CSharpProcessor p = new CSharpProcessor();
        String input = "void f() {{ x; }}";
        String round = p.postprocessCodeBlock(p.preprocessCodeBlock(input));
        assertEquals(input, round);
    }

    @Test
    @DisplayName("4.5.2: reversibilidade com tres }}}")
    void fixedPointReversalTripleAdjacent() {
        CSharpProcessor p = new CSharpProcessor();
        String input = "}}}";
        String round = p.postprocessCodeBlock(p.preprocessCodeBlock(input));
        assertEquals(input, round);
    }

    @Test
    @DisplayName("4.5.2: reversibilidade com tipos misturados );")
    void fixedPointReversalMixedAdjacent() {
        CSharpProcessor p = new CSharpProcessor();
        String input = "fn(arg);";
        String round = p.postprocessCodeBlock(p.preprocessCodeBlock(input));
        assertEquals(input, round);
    }

    @Test
    @DisplayName("4.5.2: reversibilidade Go com :=, (), {}")
    void fixedPointReversalGo() {
        GoProcessor p = new GoProcessor();
        String input = "x, err := fn(arg, arg2); { y := 2 }";
        String round = p.postprocessCodeBlock(p.preprocessCodeBlock(input));
        assertEquals(input, round);
    }

    @Test
    @DisplayName("4.5.2: reversibilidade Haskell com ::, ->, []")
    void fixedPointReversalHaskell() {
        HaskellProcessor p = new HaskellProcessor();
        String input = "f :: [Int] -> [Int]";
        String round = p.postprocessCodeBlock(p.preprocessCodeBlock(input));
        assertEquals(input, round);
    }

    // ============================================================
    // SECAO 4.5.3 - Preservacao de Quebras de Linha na Agregacao
    // ============================================================

    @Test
    @DisplayName("4.5.3: sem linhas em branco espurias apos bloco resolvido")
    void noSpuriousBlankLines(@TempDir Path tmp) throws Exception {
        assumeTrue(diff3Available);
        String base  = "class A {\n    int x;\n    int y;\n}\n";
        String left  = "class A {\n    int x;\n    int y;\n    int z;\n}\n";
        String right = "class A {\n    int x;\n    int y;\n    int w;\n}\n";

        Path bp = tmp.resolve("base.cs");
        Path lp = tmp.resolve("left.cs");
        Path rp = tmp.resolve("right.cs");
        Files.writeString(bp, base, StandardCharsets.UTF_8);
        Files.writeString(lp, left, StandardCharsets.UTF_8);
        Files.writeString(rp, right, StandardCharsets.UTF_8);

        List<String> diff3Output = Diff3Runner.runDiff3(
                lp.toString(), bp.toString(), rp.toString());
        List<String> result = SepMergeEngine.resolveConflicts(
                diff3Output, new CSharpProcessor());

        // Heuristica: nao mais que 2 linhas em branco consecutivas
        int blankRun = 0;
        for (String line : result) {
            if (line.trim().isEmpty()) {
                blankRun++;
                assertTrue(blankRun < 3,
                        "muitas linhas em branco consecutivas apos resolucao. Saida:\n"
                                + String.join("\n", result));
            } else {
                blankRun = 0;
            }
        }
    }

    @Test
    @DisplayName("4.5.3: nenhuma linha do resultado contem \\n interno (quebra preservada)")
    void noInternalNewlinesInLines(@TempDir Path tmp) throws Exception {
        assumeTrue(diff3Available);
        String base  = "int x = 1;\n";
        String left  = "int x = 2;\n";
        String right = "int x = 3;\n";

        Path bp = tmp.resolve("base.cs");
        Path lp = tmp.resolve("left.cs");
        Path rp = tmp.resolve("right.cs");
        Files.writeString(bp, base, StandardCharsets.UTF_8);
        Files.writeString(lp, left, StandardCharsets.UTF_8);
        Files.writeString(rp, right, StandardCharsets.UTF_8);

        List<String> diff3Output = Diff3Runner.runDiff3(
                lp.toString(), bp.toString(), rp.toString());
        List<String> result = SepMergeEngine.resolveConflicts(
                diff3Output, new CSharpProcessor());

        // Cada elemento da lista deve representar UMA linha (sem \n internos)
        for (String line : result) {
            assertFalse(line.contains("\n"),
                    "elemento da lista nao deveria conter \\n interno: '" + line + "'");
        }
    }
}
