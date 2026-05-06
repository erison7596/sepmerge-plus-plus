package br.ifpe.sepmerge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testa o comportamento abstrato do BaseProcessor (Template Method)
 * usando uma subclasse fake controlada.
 */
class BaseProcessorTest {

    /** Subclasse minimalista para testar a logica do template. */
    private static class FakeProcessor extends BaseProcessor {
        @Override protected String getRegex() { return "(=>|\\{|\\}|;)"; }
        @Override protected String[] getSeparatorsList() {
            return new String[]{"=>", "{", "}", ";"};
        }
        @Override public String getExtension() { return "fake"; }
    }

    private final FakeProcessor processor = new FakeProcessor();

    @Test
    @DisplayName("preprocess insere \\n antes e depois de cada separador")
    void preprocessInsertsNewlinesAroundSeparators() {
        String result = processor.preprocessCodeBlock("a{b}c;d");
        assertTrue(result.contains("\n{\n"), "deveria conter \\n{\\n");
        assertTrue(result.contains("\n}\n"), "deveria conter \\n}\\n");
        assertTrue(result.contains("\n;\n"), "deveria conter \\n;\\n");
    }

    @Test
    @DisplayName("postprocess eh inverso de preprocess para entrada limpa")
    void postprocessIsInverseOfPreprocess() {
        String input = "a{b}c;d";
        String round = processor.postprocessCodeBlock(processor.preprocessCodeBlock(input));
        assertEquals(input, round);
    }

    @Test
    @DisplayName("ponto fixo: separadores adjacentes }} sao revertidos")
    void fixedPointDoubleAdjacent() {
        String input = "}}";
        String round = processor.postprocessCodeBlock(processor.preprocessCodeBlock(input));
        assertEquals(input, round);
    }

    @Test
    @DisplayName("ponto fixo: tres separadores adjacentes };} sao revertidos")
    void fixedPointTripleAdjacent() {
        String input = "};}";
        String round = processor.postprocessCodeBlock(processor.preprocessCodeBlock(input));
        assertEquals(input, round);
    }

    @Test
    @DisplayName("ponto fixo: quatro separadores adjacentes }}}}")
    void fixedPointQuadrupleAdjacent() {
        String input = "}}}}";
        String round = processor.postprocessCodeBlock(processor.preprocessCodeBlock(input));
        assertEquals(input, round);
    }

    @Test
    @DisplayName("idempotencia: postprocess(postprocess(x)) = postprocess(x)")
    void postprocessIsIdempotent() {
        String preprocessed = processor.preprocessCodeBlock("a{b}c;d");
        String once = processor.postprocessCodeBlock(preprocessed);
        String twice = processor.postprocessCodeBlock(once);
        assertEquals(once, twice);
    }

    @Test
    @DisplayName("token longo => tem precedencia sobre tokens curtos")
    void longTokenHasPrecedence() {
        String result = processor.preprocessCodeBlock("x=>y");
        assertTrue(result.contains("\n=>\n"), "=> deveria ser detectado como token unico");
    }

    @Test
    @DisplayName("CRLF do Windows eh normalizado para LF no postprocess")
    void normalizesCrlfToLf() {
        String withCrlf = "a\r\nb\r\nc";
        String result = processor.postprocessCodeBlock(withCrlf);
        assertFalse(result.contains("\r"), "nao deveria ter \\r apos postprocess");
    }
}
