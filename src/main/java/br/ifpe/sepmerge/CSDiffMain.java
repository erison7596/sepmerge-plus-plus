package br.ifpe.sepmerge;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementacao do CSDiff original (NAO-focalizado) para comparacao
 * experimental contra o SepMerge++ (focalizado).
 * Diferenca chave em relacao ao SepMerge++:
 *   - CSDiff: aplica pre-processamento ao ARQUIVO INTEIRO antes do diff3
 *     externo, e pos-processamento ao resultado completo.
 *   - SepMerge++: aplica pre-processamento APENAS dentro de blocos de
 *     conflito ja identificados pelo diff3 externo.
 * Uso (esperado pelo 02_run_tools.sh):
 *   java -cp sepmerge.jar br.ifpe.sepmerge.CSDiffMain BASE LEFT RIGHT
 */
public class CSDiffMain {

    private static final Logger LOGGER = Logger.getLogger(CSDiffMain.class.getName());

    public static void main(String[] args) {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);

        if (args.length != 3) {
            LOGGER.severe("Uso: java -cp sepmerge.jar br.ifpe.sepmerge.CSDiffMain <BASE> <LEFT> <RIGHT>");
            System.exit(1);
        }

        String basePath  = args[0];
        String leftPath  = args[1];
        String rightPath = args[2];

        try {
            validateFile(basePath);
            validateFile(leftPath);
            validateFile(rightPath);

            LanguageProcessor processor = determineProcessor(leftPath);

            // 1. Le os arquivos INTEIROS (com strip BOM)
            String baseContent  = readFile(basePath);
            String leftContent  = readFile(leftPath);
            String rightContent = readFile(rightPath);

            // 2. Pre-processa os arquivos INTEIROS (diferenca chave vs SepMerge++)
            String basePre  = processor.preprocessCodeBlock(baseContent);
            String leftPre  = processor.preprocessCodeBlock(leftContent);
            String rightPre = processor.preprocessCodeBlock(rightContent);

            // 3. Grava em arquivos temporarios e roda diff3
            File tmpBase  = File.createTempFile("csdiff_base_",  "." + processor.getExtension());
            File tmpLeft  = File.createTempFile("csdiff_left_",  "." + processor.getExtension());
            File tmpRight = File.createTempFile("csdiff_right_", "." + processor.getExtension());

            try {
                Files.writeString(tmpBase.toPath(),  basePre,  StandardCharsets.UTF_8);
                Files.writeString(tmpLeft.toPath(),  leftPre,  StandardCharsets.UTF_8);
                Files.writeString(tmpRight.toPath(), rightPre, StandardCharsets.UTF_8);

                List<String> diff3Out = Diff3Runner.runDiff3(
                        tmpLeft.getAbsolutePath(),
                        tmpBase.getAbsolutePath(),
                        tmpRight.getAbsolutePath());

                String joined = String.join("\n", diff3Out);

                // 4. Pos-processa o resultado inteiro (reverte separadores)
                String result = processor.postprocessCodeBlock(joined);

                out.print(result);
                if (!result.endsWith("\n")) {
                    out.println();
                }
                out.flush();
            } finally {
                //noinspection ResultOfMethodCallIgnored
                tmpBase.delete();
                //noinspection ResultOfMethodCallIgnored
                tmpLeft.delete();
                //noinspection ResultOfMethodCallIgnored
                tmpRight.delete();
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Falha critica no CSDiff", e);
            System.exit(2);
        }
    }

    private static String readFile(String path) throws IOException {
        return FileUtils.stripBom(
                Files.readString(Paths.get(path), StandardCharsets.UTF_8));
    }

    private static LanguageProcessor determineProcessor(String filePath) {
        String low = filePath.toLowerCase();
        if (low.endsWith(".hs")) return new HaskellProcessor();
        if (low.endsWith(".cs")) return new CSharpProcessor();
        if (low.endsWith(".go")) return new GoProcessor();
        LOGGER.warning("Extensao nao reconhecida em " + filePath + ", usando C# como fallback.");
        return new CSharpProcessor();
    }

    private static void validateFile(String path) throws IOException {
        if (!Files.exists(Paths.get(path)) || !Files.isRegularFile(Paths.get(path))) {
            throw new IOException("Arquivo nao encontrado: " + path);
        }
    }
}
