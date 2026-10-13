/*
    Copyright 2013-2026, Sumeet Chhetri

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/
package com.gatf.export;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Δ161 — adversarial Java escaping, round-tripped by javac. Every vector is written as a {@link Jv#str} literal and as
 * a {@link Jv#comment} line into one class, which is compiled in-process (javax.tools) and loaded: each literal must
 * hold exactly its vector, and each comment must still be a comment (a field follows every one, and the class must
 * have exactly those fields). javac translates backslash-u sequences before tokenizing, even in comments and
 * literals, so the vectors below build them from {@link #BS} rather than writing them in this source.
 */
class JavaEscapingTest {

    /** One backslash. */
    static final String BS = "\\";

    static final String[] VECTORS = {
        "", "plain", "it's", "\"double\"", "'single' and \"double\"", "back" + BS + "slash", BS, BS + BS, "trailing" + BS,
        BS + "\"", "a\nb", "a\r\nb", "\r", "tab\there", "${notTemplate}", "$v{x}", "*/ /* // ", "</script><!--",
        // backslash-u text in the value: javac must not translate it (quote, backslash, LF, CR, a letter, repeated u)
        BS + "u0022", BS + "u005c", BS + "u000a", BS + "u000d", BS + "u0041", BS + "uuuu0022", BS + BS + "u0022", BS + BS + BS + "u0022",
        BS + "u", BS + "u12", "x" + BS + "u0022); System.exit(1); //",
        "line" + (char) 0x2028 + "sep", "para" + (char) 0x2029 + "sep", "nul" + (char) 0, "bell" + (char) 7 + " esc" + (char) 27 + " del" + (char) 127,
        "\b\f" + (char) 11, (char) 1 + "7" + (char) 0 + "12",   // an octal escape must not swallow a following digit
        "emoji " + (char) 0xD83D + (char) 0xDE00, "lone high " + (char) 0xD800 + " end", "lone low " + (char) 0xDC00 + " end", "reversed " + (char) 0xDC00 + (char) 0xD800,
        "" + (char) 0xE9 + (char) 0x4E2D + (char) 0x915 + (char) 0x93F, "" + (char) 0x85 + " NEL " + (char) 0xA0 + " nbsp " + (char) 0xFEFF + " bom", "\"+System.exit(1)+\"",
    };

    @TempDir
    Path tmp;

    @Test
    void literalsAndCommentsRoundTripThroughJavac() throws Exception {
        StringBuilder src = new StringBuilder("public class EscapeVectors {\n    public static final String[] S = {\n");
        for (String v : VECTORS) src.append("        ").append(Jv.str(v)).append(",\n");
        src.append("    };\n");
        for (int i = 0; i < VECTORS.length; i++) {
            src.append("    // ").append(Jv.comment(VECTORS[i])).append('\n');
            src.append("    public static final int C").append(i).append(" = ").append(i).append(";\n");
        }
        src.append("}\n");
        Path file = tmp.resolve("EscapeVectors.java");
        Files.write(file, src.toString().getBytes(StandardCharsets.UTF_8));
        Path out = Files.createDirectories(tmp.resolve("out"));
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        StringWriter diag = new StringWriter();
        boolean ok = javac.getTask(diag, null, null, Arrays.asList("-encoding", "UTF-8", "-Xlint:all", "-d", out.toString()), null,
                javac.getStandardFileManager(null, null, StandardCharsets.UTF_8).getJavaFileObjects(file.toFile())).call();
        assertTrue(ok, "javac failed:\n" + diag + "\n" + src);
        assertEquals("", diag.toString().trim(), "javac warnings");
        try (URLClassLoader cl = new URLClassLoader(new URL[] { out.toUri().toURL() }, null)) {
            Class<?> c = cl.loadClass("EscapeVectors");
            assertArrayEquals(VECTORS, (String[]) c.getField("S").get(null));
            List<String> fields = new ArrayList<String>();
            for (java.lang.reflect.Field f : c.getDeclaredFields()) fields.add(f.getName());
            assertEquals(VECTORS.length + 1, fields.size(), "a comment turned into code: " + fields);
            for (int i = 0; i < VECTORS.length; i++) assertEquals(i, c.getField("C" + i).getInt(null));
        }
    }

    @Test
    void literalsArePrintableAsciiWithEvenBackslashRunsBeforeU() {
        for (String v : VECTORS) {
            String lit = Jv.str(v);
            for (int i = 0; i < lit.length(); i++) {
                char c = lit.charAt(i);
                assertTrue(c >= 0x20 && c <= 0x7E, "non-printable or non-ASCII char in " + lit);
                if (c == 'u' && i > 0 && lit.charAt(i - 1) == '\\') {
                    int run = 0;
                    for (int j = i - 1; j >= 0 && lit.charAt(j) == '\\'; j--) run++;
                    // an odd run means the last backslash starts a Unicode escape: only Jv's own escapes may do that
                    if (run % 2 == 1) assertTrue(lit.substring(i + 1, Math.min(lit.length(), i + 5)).matches("[0-9a-f]{4}") && hexAbove7e(lit, i + 1), lit);
                }
            }
        }
    }

    private static boolean hexAbove7e(String lit, int at) {
        return Integer.parseInt(lit.substring(at, at + 4), 16) > 0x7E;
    }

    @Test
    void commentsHaveNoLineTerminatorAndNoUnicodeEscape() {
        for (String v : VECTORS) {
            String c = Jv.comment(v);
            assertFalse(c.indexOf('\n') >= 0 || c.indexOf('\r') >= 0, "line terminator in comment text for " + v);
            for (int i = 0; i < c.length(); i++) {
                if (c.charAt(i) != 'u' || i == 0 || c.charAt(i - 1) != '\\') continue;
                int run = 0;
                for (int j = i - 1; j >= 0 && c.charAt(j) == '\\'; j--) run++;
                assertEquals(0, run % 2, "comment text would start a Unicode escape: " + c);
            }
        }
        assertEquals("a U+2028 b", Jv.comment("a " + (char) 0x2028 + " b"));
        assertEquals(BS + BS + "u0022 and " + BS + "n", Jv.comment(BS + "u0022 and " + BS + "n"));
    }

    @Test
    void unescapeJavaAsCommonsText() {
        assertEquals("ab" + (char) 0xE003 + "c", Jv.unescapeJava("ab" + BS + "uE003c"));
        assertEquals("A", Jv.unescapeJava(BS + "uuu+0041"));
        assertEquals("\n\t\b\f\r\\\"'", Jv.unescapeJava(BS + "n" + BS + "t" + BS + "b" + BS + "f" + BS + "r" + BS + BS + BS + "\"" + BS + "'"));
        assertEquals("" + (char) 0 + (char) 8 + "9" + (char) 0377 + (char) 040 + "0", Jv.unescapeJava(BS + "0" + BS + "109" + BS + "377" + BS + "400"));
        assertEquals("x", Jv.unescapeJava(BS + "x"));     // a backslash before any other character is dropped
        assertEquals("end", Jv.unescapeJava("end" + BS)); // as is a trailing one
        assertThrows(IllegalArgumentException.class, () -> Jv.unescapeJava(BS + "u12"));
        assertThrows(IllegalArgumentException.class, () -> Jv.unescapeJava(BS + "uZZZZ"));
    }

    @Test
    void namesAndXPathLiterals() {
        assertEquals("E2e01SemanticTest", Jv.className("e2e-01-semantic"));
        assertEquals("Sel10UnsupportedTest", Jv.className("10-unsupported"));
        assertEquals("LoginTest", Jv.className("login"));
        assertEquals("SelTest", Jv.className("--"));
        assertEquals("st_save__smoke_", Jv.ident("st_", "save [smoke]"));
        assertEquals("_9", Jv.ident("", "9"));
        assertEquals("'it'", Jv.xpathLiteral("it"));
        assertEquals("\"it's\"", Jv.xpathLiteral("it's"));
        assertEquals("concat('it', \"'\", 's \"x\"')", Jv.xpathLiteral("it's \"x\""));
        assertTrue(new File(".").exists());
    }
}
