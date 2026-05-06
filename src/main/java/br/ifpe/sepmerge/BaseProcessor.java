package br.ifpe.sepmerge;

public abstract class BaseProcessor implements LanguageProcessor {

    protected abstract String getRegex();
    protected abstract String[] getSeparatorsList();

    /**
     * Insere \n antes e depois de cada separador sintatico.
     * Isso separa contextos sintaticos em linhas distintas antes do diff3 interno.
     */
    @Override
    public String preprocessCodeBlock(String codeBlock) {
        return codeBlock.replaceAll(getRegex(), "\n$1\n");
    }

    /**
     * Reverte a insercao de \n e normaliza os marcadores de conflito do diff3.
     * Implementa reversibilidade em ponto fixo (Secao 4.5.2 do paper):
     * separadores adjacentes geram \n...\n\n...\n e podem precisar de mais
     * de uma passada para serem totalmente revertidos.
     */
    @Override
    public String postprocessCodeBlock(String mergedCodeBlock) {
        // Normaliza quebras de linha do Windows (CRLF -> LF)
        String result = mergedCodeBlock.replace("\r", "");

        // --- Reversao literal dos separadores em ponto fixo ---
        String prev;
        do {
            prev = result;
            for (String sep : getSeparatorsList()) {
                result = result.replace("\n" + sep + "\n", sep);
            }
        } while (!result.equals(prev));

        // --- Cosmetica: garante que marcadores de conflito fiquem em linha propria ---
        result = result.replaceAll(
                "([^\\n])(<<<<<<<|\\|\\|\\|\\|\\|\\|\\||=======|>>>>>>>)",
                "$1\n$2");
        result = result.replaceAll("(?m)^(=======)([^\\n])", "$1\n$2");

        String ext = getExtension();
        result = result.replaceAll(
                "(?m)^(<<<<<<<.*?\\." + ext
                        + "|\\|\\|\\|\\|\\|\\|\\|.*?\\." + ext
                        + "|>>>>>>>.*?\\." + ext + ")([^\\n])",
                "$1\n$2");

        return result;
    }
}
