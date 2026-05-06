package br.ifpe.sepmerge;

public interface LanguageProcessor {
    String preprocessCodeBlock(String codeBlock);
    String postprocessCodeBlock(String mergedCodeBlock);
    String getExtension();
}
