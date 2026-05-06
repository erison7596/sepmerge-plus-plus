package br.ifpe.sepmerge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HaskellProcessorTest {

    private final HaskellProcessor processor = new HaskellProcessor();

    @Test
    @DisplayName("extensao do Haskell eh 'hs'")
    void extension() {
        assertEquals("hs", processor.getExtension());
    }

    /**
     * REGRESSAO: o regex original `(...|\\{|\\};)` (sem barra entre } e ;)
     * fazia o regex casar o literal `};` como token unico, fazendo com que
     * o `;` sozinho NAO fosse detectado como separador. Este teste falha
     * com o regex bugado e passa com o regex corrigido.
     */
    @Test
    @DisplayName("REGRESSAO: ; eh detectado como separador isolado")
    void semicolonIsRecognizedAsSeparator() {
        String input = "a;b";
        String preprocessed = processor.preprocessCodeBlock(input);
        assertTrue(preprocessed.contains("\n;\n"),
                "; deveria ser separador isolado. Preprocessed:\n" + preprocessed);
    }

    @Test
    @DisplayName("REGRESSAO: } eh detectado como separador isolado")
    void closingBraceIsRecognizedAsSeparator() {
        String input = "{a; b}";
        String preprocessed = processor.preprocessCodeBlock(input);
        assertTrue(preprocessed.contains("\n}\n"),
                "} deveria ser separador isolado. Preprocessed:\n" + preprocessed);
    }

    @Test
    @DisplayName("tokens multi-caractere :: -> => <- tem precedencia")
    void multiCharTokensPrecedence() {
        String input = "f :: a -> b => c <- d";
        String preprocessed = processor.preprocessCodeBlock(input);
        assertTrue(preprocessed.contains("\n::\n"), "::");
        assertTrue(preprocessed.contains("\n->\n"), "->");
        assertTrue(preprocessed.contains("\n=>\n"), "=>");
        assertTrue(preprocessed.contains("\n<-\n"), "<-");
    }

    @Test
    @DisplayName("roundtrip em assinatura de funcao Haskell")
    void roundtripFunctionSignature() {
        String input = "factorial :: Int -> Int";
        String round = processor.postprocessCodeBlock(processor.preprocessCodeBlock(input));
        assertEquals(input, round);
    }

    @Test
    @DisplayName("roundtrip em do-notation com <-")
    void roundtripDoNotation() {
        String input = "main = do { x <- getLine; print x }";
        String round = processor.postprocessCodeBlock(processor.preprocessCodeBlock(input));
        assertEquals(input, round);
    }

    @Test
    @DisplayName("'=' NAO eh separador (decisao de design da Secao 4.4)")
    void equalsIsNotSeparator() {
        String input = "x = 1";
        String preprocessed = processor.preprocessCodeBlock(input);
        assertFalse(preprocessed.contains("\n=\n"),
                "'=' nao deveria ser separador em Haskell (apenas =>)");
    }

    @Test
    @DisplayName("'|' NAO eh separador (decisao de design da Secao 4.4)")
    void pipeIsNotSeparator() {
        String input = "data T = A | B | C";
        String preprocessed = processor.preprocessCodeBlock(input);
        assertFalse(preprocessed.contains("\n|\n"),
                "'|' nao deveria ser separador no HaskellProcessor padrao");
    }
}
