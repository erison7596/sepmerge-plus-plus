package br.ifpe.sepmerge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CSharpProcessorTest {

    private final CSharpProcessor processor = new CSharpProcessor();

    @Test
    @DisplayName("extensao do C# eh 'cs'")
    void extension() {
        assertEquals("cs", processor.getExtension());
    }

    @Test
    @DisplayName("todos os separadores principais sao detectados")
    void allSeparatorsDetected() {
        String input = "{};,()[]:";
        String preprocessed = processor.preprocessCodeBlock(input);
        for (String sep : new String[]{"{", "}", ";", ",", "(", ")", "[", "]", ":"}) {
            assertTrue(preprocessed.contains("\n" + sep + "\n"),
                    "deveria detectar separador: " + sep + "\nResultado:\n" + preprocessed);
        }
    }

    @Test
    @DisplayName("token => (lambda arrow) tem precedencia")
    void lambdaArrowHasPrecedence() {
        String input = "Func<int, int> f = x => x + 1;";
        String preprocessed = processor.preprocessCodeBlock(input);
        assertTrue(preprocessed.contains("\n=>\n"),
                "deveria detectar => como separador. Preprocessed:\n" + preprocessed);
    }

    @Test
    @DisplayName("roundtrip em codigo C# real preserva o conteudo")
    void roundtripRealCode() {
        String input = "public int Sum(int a, int b) { return a + b; }";
        String round = processor.postprocessCodeBlock(processor.preprocessCodeBlock(input));
        assertEquals(input, round);
    }

    @Test
    @DisplayName("roundtrip preserva chamadas com parenteses adjacentes (cadeia)")
    void roundtripChainedCalls() {
        String input = "foo(bar(baz()));";
        String round = processor.postprocessCodeBlock(processor.preprocessCodeBlock(input));
        assertEquals(input, round);
    }

    @Test
    @DisplayName("roundtrip preserva blocos aninhados {{ ... }}")
    void roundtripNestedBlocks() {
        String input = "if(true){{int x=1;}}";
        String round = processor.postprocessCodeBlock(processor.preprocessCodeBlock(input));
        assertEquals(input, round);
    }
}
