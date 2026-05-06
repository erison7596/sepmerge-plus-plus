package br.ifpe.sepmerge;

import java.nio.charset.StandardCharsets;

/**
 * Utilitario de tratamento de encoding/BOM.
 * Arquivos .cs criados pelo Visual Studio frequentemente tem BOM UTF-8
 * (EF BB BF), que, se nao removido, aparece como '?' no console ou
 * baguca o alinhamento do diff3.
 */
public final class FileUtils {

    private static final char BOM = '\uFEFF';
    public static final java.nio.charset.Charset UTF_8 = StandardCharsets.UTF_8;

    private FileUtils() { /* utility class */ }

    /** Remove BOM do comeco da string, se houver. */
    public static String stripBom(String s) {
        if (s != null && !s.isEmpty() && s.charAt(0) == BOM) {
            return s.substring(1);
        }
        return s;
    }
}
