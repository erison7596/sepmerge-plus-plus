package br.ifpe.sepmerge;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main {

    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) {
        // Forca stdout em UTF-8. No Windows, o default eh CP1252 e isso
        // transforma acentos e BOM nao-removido em '?'.
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);

        if (args.length != 3) {
            LOGGER.severe("Uso: java -jar sepmerge.jar <BASE> <LEFT> <RIGHT>");
            System.exit(1);
        }

        String basePath  = args[0];
        String leftPath  = args[1];
        String rightPath = args[2];

        try {
            validateFileExists(basePath);
            validateFileExists(leftPath);
            validateFileExists(rightPath);

            LanguageProcessor processor = determineLanguageProcessor(leftPath);

            List<String> diff3RawOutput =
                    Diff3Runner.runDiff3(leftPath, basePath, rightPath);

            List<String> finalOutput =
                    SepMergeEngine.resolveConflicts(diff3RawOutput, processor);

            for (String line : finalOutput) {
                out.println(line);
            }
            out.flush();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Falha critica ao processar o merge", e);
            System.exit(2);
        }
    }

    private static LanguageProcessor determineLanguageProcessor(String filePath) {
        String lowerPath = filePath.toLowerCase();
        if (lowerPath.endsWith(".hs")) return new HaskellProcessor();
        if (lowerPath.endsWith(".cs")) return new CSharpProcessor();
        if (lowerPath.endsWith(".go")) return new GoProcessor();

        LOGGER.warning("Extensao nao reconhecida. Usando CSharpProcessor como fallback.");
        return new CSharpProcessor();
    }

    private static void validateFileExists(String filePath) throws IOException {
        Path path = Paths.get(filePath);
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new IOException("Arquivo nao encontrado: " + filePath);
        }
    }
}
