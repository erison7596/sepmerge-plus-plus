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
 * Testes de integracao com o utilitario diff3 do sistema.
 * Os testes sao automaticamente desabilitados se o diff3 nao estiver no PATH
 * (no Windows, instale Git for Windows ou Cygwin para tê-lo disponivel).
 */
class Diff3RunnerTest {

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

    @Test
    @DisplayName("diff3 sem conflito retorna conteudo integrado, sem marcadores")
    void noConflictMergesAutomatically(@TempDir Path tmp) throws Exception {
        assumeTrue(diff3Available, "diff3 nao esta no PATH; pulando teste");

        Path base = tmp.resolve("base.txt");
        Path left = tmp.resolve("left.txt");
        Path right = tmp.resolve("right.txt");

        Files.writeString(base, "line1\nline2\nline3\n", StandardCharsets.UTF_8);
        Files.writeString(left, "added_left\nline1\nline2\nline3\n", StandardCharsets.UTF_8);
        Files.writeString(right, "line1\nline2\nline3\nadded_right\n", StandardCharsets.UTF_8);

        List<String> output = Diff3Runner.runDiff3(
                left.toString(), base.toString(), right.toString());

        boolean hasConflictMarkers = output.stream()
                .anyMatch(l -> l.startsWith("<<<<<<<") || l.startsWith(">>>>>>>"));
        assertFalse(hasConflictMarkers, "merge sem conflito nao deveria ter marcadores");

        String joined = String.join("\n", output);
        assertTrue(joined.contains("added_left"));
        assertTrue(joined.contains("added_right"));
    }

    @Test
    @DisplayName("diff3 com conflito real reporta marcadores <<<<<<< e >>>>>>>")
    void conflictReportsMarkers(@TempDir Path tmp) throws Exception {
        assumeTrue(diff3Available, "diff3 nao esta no PATH; pulando teste");

        Path base = tmp.resolve("base.txt");
        Path left = tmp.resolve("left.txt");
        Path right = tmp.resolve("right.txt");

        Files.writeString(base, "x = 1\n", StandardCharsets.UTF_8);
        Files.writeString(left, "x = 2\n", StandardCharsets.UTF_8);
        Files.writeString(right, "x = 3\n", StandardCharsets.UTF_8);

        List<String> output = Diff3Runner.runDiff3(
                left.toString(), base.toString(), right.toString());

        boolean hasConflictMarkers = output.stream()
                .anyMatch(l -> l.startsWith("<<<<<<<") || l.startsWith(">>>>>>>"));
        assertTrue(hasConflictMarkers, "merge com conflito deveria ter marcadores");
    }

    @Test
    @DisplayName("BOM eh removido da primeira linha da saida")
    void stripsBomFromFirstLine(@TempDir Path tmp) throws Exception {
        assumeTrue(diff3Available, "diff3 nao esta no PATH; pulando teste");

        // Cria arquivo com BOM (3 bytes EF BB BF + conteudo)
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] content = "x = 1\n".getBytes(StandardCharsets.UTF_8);
        byte[] full = new byte[bom.length + content.length];
        System.arraycopy(bom, 0, full, 0, bom.length);
        System.arraycopy(content, 0, full, bom.length, content.length);

        Path base = tmp.resolve("base.txt");
        Path left = tmp.resolve("left.txt");
        Path right = tmp.resolve("right.txt");
        Files.write(base, full);
        Files.write(left, full);
        Files.write(right, full);

        List<String> output = Diff3Runner.runDiff3(
                left.toString(), base.toString(), right.toString());

        if (!output.isEmpty()) {
            assertFalse(output.getFirst().startsWith("\uFEFF"),
                    "BOM nao deveria estar na primeira linha. Linha=" + output.getFirst());
        }
    }

    @Test
    @DisplayName("preserva caracteres UTF-8 (acentos, c-cedilha)")
    void preservesUtf8Characters(@TempDir Path tmp) throws Exception {
        assumeTrue(diff3Available, "diff3 nao esta no PATH; pulando teste");

        String content = "funcao: acao, coracao, ç, ã, é\n";
        Path base = tmp.resolve("base.txt");
        Path left = tmp.resolve("left.txt");
        Path right = tmp.resolve("right.txt");
        Files.writeString(base, content, StandardCharsets.UTF_8);
        Files.writeString(left, content, StandardCharsets.UTF_8);
        Files.writeString(right, content, StandardCharsets.UTF_8);

        List<String> output = Diff3Runner.runDiff3(
                left.toString(), base.toString(), right.toString());

        String joined = String.join("\n", output);
        assertTrue(joined.contains("ç"), "deveria preservar caracteres UTF-8");
        assertTrue(joined.contains("ã"), "deveria preservar caracteres UTF-8");
    }
}
