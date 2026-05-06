package br.ifpe.sepmerge;

public class GoProcessor extends BaseProcessor {

    // ':=' antes de qualquer separador que use ':' isolado (Go nao tem ':' como
    // separador, mas mantemos a ordem por seguranca)
    private static final String GO_REGEX =
            "(:=|\\{|\\}|,|\\(|\\)|;|\\[|\\])";

    private static final String[] GO_SEPARATORS = {
            ":=", "{", "}", ",", "(", ")", ";", "[", "]"
    };

    @Override protected String getRegex() { return GO_REGEX; }
    @Override protected String[] getSeparatorsList() { return GO_SEPARATORS; }
    @Override public String getExtension() { return "go"; }
}
