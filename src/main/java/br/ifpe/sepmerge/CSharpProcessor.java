package br.ifpe.sepmerge;

public class CSharpProcessor extends BaseProcessor {

    // Ordem no regex importa: '=>' precisa vir antes de tokens que poderiam
    // comer seus caracteres. Como nao usamos '=' nem '>' isolados, eh seguro.
    private static final String CSHARP_REGEX =
            "(=>|\\{|\\}|,|\\(|\\)|;|\\[|\\]|:)";

    private static final String[] CSHARP_SEPARATORS = {
            "=>", "{", "}", ",", "(", ")", ";", "[", "]", ":"
    };

    @Override protected String getRegex() { return CSHARP_REGEX; }
    @Override protected String[] getSeparatorsList() { return CSHARP_SEPARATORS; }
    @Override public String getExtension() { return "cs"; }
}
