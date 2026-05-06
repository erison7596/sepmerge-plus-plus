package br.ifpe.sepmerge;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SepMergeEngine {

    public static List<String> resolveConflicts(
            List<String> diff3Output, LanguageProcessor processor) throws Exception {

        List<String> finalOutput = new ArrayList<>();
        List<String> leftBlock  = new ArrayList<>();
        List<String> baseBlock  = new ArrayList<>();
        List<String> rightBlock = new ArrayList<>();

        int state = 0;
        for (String line : diff3Output) {
            if (line.startsWith("<<<<<<<")) {
                state = 1;
                leftBlock.clear();
                baseBlock.clear();
                rightBlock.clear();
            } else if (line.startsWith("|||||||")) {
                state = 2;
            } else if (line.startsWith("=======")) {
                state = 3;
            } else if (line.startsWith(">>>>>>>")) {
                state = 0;

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
                    case 0: finalOutput.add(line);        break;
                    case 1: leftBlock.add(line);          break;
                    case 2: baseBlock.add(line);          break;
                    case 3: rightBlock.add(line);         break;
                }
            }
        }

        return finalOutput;
    }

    /** Divide uma string multi-linha em linhas individuais, descartando o trailing \n. */
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
            // Sempre UTF-8 sem BOM - previne ? no Windows e
            // desalinhamento de bytes no diff3.
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
