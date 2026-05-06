package br.ifpe.sepmerge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GoProcessorTest {

    private final GoProcessor processor = new GoProcessor();

    @Test
    @DisplayName("extensao do Go eh 'go'")
    void extension() {
        assertEquals("go", processor.getExtension());
    }

    @Test
    @DisplayName("operador := tem precedencia (idiomatico Go)")
    void shortDeclarationHasPrecedence() {
        String input = "x := 42";
        String preprocessed = processor.preprocessCodeBlock(input);
        assertTrue(preprocessed.contains("\n:=\n"),
                "deveria detectar := como separador. Preprocessed:\n" + preprocessed);
    }

    @Test
    @DisplayName("todos os separadores Go sao detectados")
    void allSeparatorsDetected() {
        String input = "{};,()[]";
        String preprocessed = processor.preprocessCodeBlock(input);
        for (String sep : new String[]{"{", "}", ";", ",", "(", ")", "[", "]"}) {
            assertTrue(preprocessed.contains("\n" + sep + "\n"),
                    "deveria detectar separador: " + sep);
        }
    }

    @Test
    @DisplayName("roundtrip em assinatura de funcao Go")
    void roundtripFunctionSignature() {
        String input = "func add(a, b int) int { return a + b }";
        String round = processor.postprocessCodeBlock(processor.preprocessCodeBlock(input));
        assertEquals(input, round);
    }

    @Test
    @DisplayName("roundtrip preserva idiom result, err := fn()")
    void roundtripIdiomaticErrorPattern() {
        String input = "result, err := fn(); if err != nil { return err }";
        String round = processor.postprocessCodeBlock(processor.preprocessCodeBlock(input));
        assertEquals(input, round);
    }
}
