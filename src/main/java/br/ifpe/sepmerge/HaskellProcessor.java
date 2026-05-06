package br.ifpe.sepmerge;

public class HaskellProcessor extends BaseProcessor {

    /*
     * Decisao de design: Haskell usa '=' e '|' com altissima frequencia
     * (toda definicao, guard, data constructor e list comprehension).
     * Usa-los como separadores infla muito o arquivo pre-processado e
     * aumenta a chance do diff3 interno errar o pareamento - exatamente
     * o problema que o SepMerge tenta reduzir.
     *
     * Por isso, mantemos apenas separadores sintaticos "fortes" e pouco
     * ambiguos. Se precisar, basta acrescentar '=' e '|' nas duas listas.
     * Ordem: tokens longos (::, ->, =>, <-) antes dos curtos.
     */
    private static final String HASKELL_REGEX =
            "(::|->|=>|<-|,|\\(|\\)|\\[|\\]|\\{|\\}|;)";

    private static final String[] HASKELL_SEPARATORS = {
            "::", "->", "=>", "<-", ",", "(", ")", "[", "]", "{", "}", ";"
    };

    @Override protected String getRegex() { return HASKELL_REGEX; }
    @Override protected String[] getSeparatorsList() { return HASKELL_SEPARATORS; }
    @Override public String getExtension() { return "hs"; }
}
