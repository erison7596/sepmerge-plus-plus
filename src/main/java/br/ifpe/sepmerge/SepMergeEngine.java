package br.ifpe.sepmerge;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * VERSAO COM SALVAGUARDA: este SepMergeEngine inclui uma correcao
 * para evitar falsos negativos (aFN) em cenarios de "delete vs modify".
 * Problema observado no experimento da Fase 3:
 *   Quando UM dos lados (base, left ou right) tinha bloco de conflito
 *   VAZIO (arquivo deletado num lado, modificado no outro), o
 *   SepMerge++ pre-processava string vazia, rodava diff3 interno e este
 *   "resolvia" o conflito automaticamente, eliminando o codigo do lado
 *   modificado. Resultado: aFN grave (codigo perdido silenciosamente).
 * Salvaguarda: se qualquer dos tres blocos de conflito esta vazio
 * (descontando whitespace), preservamos o conflito original do diff3
 * em vez de tentar resolver. Garante que SepMerge++ nunca PIORA vs
 * diff3 nesse caso.
 */
public class SepMergeEngine {

    public static List<String> resolveConflicts(
            List<String> diff3Output, LanguageProcessor processor) throws Exception {

        List<String> finalOutput = new ArrayList<>();
        List<String> leftBlock  = new ArrayList<>();
        List<String> baseBlock  = new ArrayList<>();
        List<String> rightBlock = new ArrayList<>();

        // Guarda as linhas originais do conflito para reuso quando precisar
        // preservar (caso de delete vs modify).
        List<String> conflictMarkers = new ArrayList<>();

        int state = 0;
        for (String line : diff3Output) {
            if (line.startsWith("<<<<<<<")) {
                state = 1;
                leftBlock.clear();
                baseBlock.clear();
                rightBlock.clear();
                conflictMarkers.clear();
                conflictMarkers.add(line);
            } else if (line.startsWith("|||||||")) {
                state = 2;
                conflictMarkers.add(line);
            } else if (line.startsWith("=======")) {
                state = 3;
                conflictMarkers.add(line);
            } else if (line.startsWith(">>>>>>>")) {
                state = 0;
                conflictMarkers.add(line);

                // ---- SALVAGUARDA: detecta delete vs modify ----
                if (isEffectivelyEmpty(leftBlock)
                        || isEffectivelyEmpty(rightBlock)) {
                    // Preserva o conflito original do diff3 — nao tente
                    // "resolver" automaticamente, isso geraria aFN.
                    appendConflictAsIs(finalOutput, conflictMarkers,
                            leftBlock, baseBlock, rightBlock);
                    continue;
                }

                // ---- Caso normal: aplicar SepMerge focalizado ----
                String leftStr  = String.join("\n", leftBlock)  + "\n";
                String baseStr  = String.join("\n", baseBlock)  + "\n";
                String rightStr = String.join("\n", rightBlock) + "\n";

                leftStr  = processor.preprocessCodeBlock(leftStr);
                baseStr  = processor.preprocessCodeBlock(baseStr);
                rightStr = processor.preprocessCodeBlock(rightStr);

                String resolvedConflict = runInnerDiff3(
                        leftStr, baseStr, rightStr, processor.getExtension());

                String finalConflict = processor.postprocessCodeBlock(resolvedConflict);

                appendAsLines(finalOutput, finalConflict);

            } else {
                switch (state) {
                    case 0: finalOutput.add(line);  break;
                    case 1: leftBlock.add(line);    break;
                    case 2: baseBlock.add(line);    break;
                    case 3: rightBlock.add(line);   break;
                }
            }
        }

        return finalOutput;
    }

    /** Verifica se o bloco eh efetivamente vazio (so whitespace ou nada). */
    private static boolean isEffectivelyEmpty(List<String> block) {
        if (block.isEmpty()) return true;
        for (String l : block) {
            if (!l.trim().isEmpty()) return false;
        }
        return true;
    }

    /**
     * Reconstitui o conflito original (com marcadores e os tres blocos)
     * para preserva-lo intacto na saida — usado quando a salvaguarda
     * detecta delete vs modify.
     */
    private static void appendConflictAsIs(
            List<String> out,
            List<String> markers,
            List<String> left,
            List<String> base,
            List<String> right) {

        // Os marcadores estao em ordem: <<<<<<<, |||||||, =======, >>>>>>>
        // Os blocos correspondem aos intervalos entre eles.
        if (markers.size() < 4) {
            // Conflito sem marker base (formato simples sem |||||||).
            // Estrutura: <<<<<<< / left / ======= / right / >>>>>>>
            out.add(markers.get(0));
            out.addAll(left);
            out.add(markers.get(1));
            out.addAll(right);
            out.add(markers.get(2));
        } else {
            // Estrutura: <<<<<<< / left / ||||||| / base / ======= / right / >>>>>>>
            out.add(markers.get(0));
            out.addAll(left);
            out.add(markers.get(1));
            out.addAll(base);
            out.add(markers.get(2));
            out.addAll(right);
            out.add(markers.get(3));
        }
    }

    private static void appendAsLines(List<String> out, String content) {
        if (content == null || content.isEmpty()) return;

        String normalized = content.replace("\r", "");
        String[] lines = normalized.split("\n", -1);
        int end = lines.length;
        if (end > 0 && lines[end - 1].isEmpty()) {
            end--;
        }
        out.addAll(Arrays.asList(lines).subList(0, end));
    }

    private static String runInnerDiff3(
            String left, String base, String right, String extension) throws Exception {

        File tmpLeft  = File.createTempFile("left_tmp",  "." + extension);
        File tmpBase  = File.createTempFile("base_tmp",  "." + extension);
        File tmpRight = File.createTempFile("right_tmp", "." + extension);

        try {
            Files.writeString(tmpLeft.toPath(),  left,  StandardCharsets.UTF_8);
            Files.writeString(tmpBase.toPath(),  base,  StandardCharsets.UTF_8);
            Files.writeString(tmpRight.toPath(), right, StandardCharsets.UTF_8);

            List<String> innerOutput = Diff3Runner.runDiff3(
                    tmpLeft.getAbsolutePath(),
                    tmpBase.getAbsolutePath(),
                    tmpRight.getAbsolutePath());

            return String.join("\n", innerOutput);
        } finally {
            //noinspection ResultOfMethodCallIgnored
            tmpLeft.delete();
            //noinspection ResultOfMethodCallIgnored
            tmpBase.delete();
            //noinspection ResultOfMethodCallIgnored
            tmpRight.delete();
        }
    }
}
