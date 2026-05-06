package br.ifpe.sepmerge;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class Diff3Runner {

    public static List<String> runDiff3(String leftPath, String basePath, String rightPath)
            throws Exception {

        ProcessBuilder pb = new ProcessBuilder(
                "diff3", "-m", "--show-all", leftPath, basePath, rightPath);

        // Redireciona stderr junto para nao bloquear o processo se o diff3
        // reclamar de algo (evita deadlock em arquivos grandes).
        pb.redirectErrorStream(true);

        Process process = pb.start();
        List<String> diffOutput = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                diffOutput.add(line);
            }
        }

        process.waitFor();

        if (!diffOutput.isEmpty()) {
            // Java 21+: List.getFirst()
            diffOutput.set(0, FileUtils.stripBom(diffOutput.getFirst()));
        }
        return diffOutput;
    }
}
