package br.ifpe.sepmerge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class FileUtilsTest {

    @Test
    @DisplayName("stripBom remove U+FEFF do inicio")
    void removesBomFromStart() {
        String withBom = "\uFEFFhello";
        assertEquals("hello", FileUtils.stripBom(withBom));
    }

    @Test
    @DisplayName("stripBom nao altera string sem BOM")
    void leavesStringWithoutBomUntouched() {
        assertEquals("hello", FileUtils.stripBom("hello"));
    }

    @Test
    @DisplayName("stripBom retorna null para entrada null")
    void handlesNull() {
        assertNull(FileUtils.stripBom(null));
    }

    @Test
    @DisplayName("stripBom retorna string vazia para entrada vazia")
    void handlesEmpty() {
        assertEquals("", FileUtils.stripBom(""));
    }

    @Test
    @DisplayName("stripBom remove apenas o primeiro BOM, nao BOMs internos")
    void removesOnlyLeadingBom() {
        String s = "\uFEFFhello\uFEFFworld";
        assertEquals("hello\uFEFFworld", FileUtils.stripBom(s));
    }

    @Test
    @DisplayName("constante UTF_8 esta exposta e correta")
    void utf8CharsetExposed() {
        assertEquals(StandardCharsets.UTF_8, FileUtils.UTF_8);
    }
}
