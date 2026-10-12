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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Δ160 — adversarial escaping. Every literal {@link Ts#str} writes is decoded back with a JavaScript string-literal
 * decoder and must equal the input; no output may hold a raw line terminator, so no value can end a string or a
 * {@code //} comment early and turn the rest into code.
 */
class TsEscapingTest {

    static final String[] VECTORS = {
        "", "plain", "it's", "\"double\"", "'single' and \"double\"", "back\\slash", "\\", "\\\\", "trailing\\",
        "\\'", "a\nb", "a\r\nb", "\r", "tab\there", "${notTemplate}", "`back`ticks`", "${`nested`}", "</script><!--",
        "*/ /* // ", "line\u2028sep", "para\u2029sep", "nul\u0000", "bell\u0007 esc\u001b del\u007f", "\b\f\u000b",
        "emoji \uD83D\uDE00", "lone high \uD800 end", "lone low \uDC00 end", "reversed \uDC00\uD800", "\u00e9\u4e2d\u0915\u093f",
        "'+alert(1)+'", "\\u0041 stays", "\\x41 stays", "$v{x}", "\u0085 NEL \u00a0 nbsp \ufeff bom"
    };

    /** Decodes a JavaScript single- or double-quoted string literal (the escapes Ts writes, and the standard ones). */
    static String decode(String lit) {
        char q = lit.charAt(0);
        assertTrue((q == '\'' || q == '"') && lit.charAt(lit.length() - 1) == q, "not a string literal: " + lit);
        StringBuilder b = new StringBuilder();
        for (int i = 1; i < lit.length() - 1; i++) {
            char c = lit.charAt(i);
            assertFalse(c == '\n' || c == '\r' || c == '\u2028' || c == '\u2029', "raw line terminator in " + lit);
            assertFalse(c == q, "unescaped quote inside " + lit);
            if (c != '\\') { b.append(c); continue; }
            char e = lit.charAt(++i);
            switch (e) {
                case 'n': b.append('\n'); break;
                case 'r': b.append('\r'); break;
                case 't': b.append('\t'); break;
                case 'b': b.append('\b'); break;
                case 'f': b.append('\f'); break;
                case 'v': b.append('\u000b'); break;
                case '0': b.append('\0'); break;
                case 'x': b.append((char) Integer.parseInt(lit.substring(i + 1, i + 3), 16)); i += 2; break;
                case 'u': b.append((char) Integer.parseInt(lit.substring(i + 1, i + 5), 16)); i += 4; break;
                default: b.append(e);
            }
        }
        return b.toString();
    }

    @Test
    void stringLiteralsRoundTrip() {
        for (String v : VECTORS) {
            String lit = Ts.str(v);
            assertEquals(v, decode(lit), "round trip of " + lit);
            assertTrue(lit.startsWith("'") && lit.endsWith("'"));
            for (int i = 0; i < lit.length(); i++) {
                char c = lit.charAt(i);
                assertFalse(c < 0x20 || c == 0x7f || c == '\u2028' || c == '\u2029', "control character left raw in " + lit);
                if (Character.isHighSurrogate(c)) assertTrue(i + 1 < lit.length() && Character.isLowSurrogate(lit.charAt(++i)), "lone surrogate in " + lit);
                else assertFalse(Character.isLowSurrogate(c), "lone surrogate in " + lit);
            }
        }
    }

    @Test
    void everyCharBelow0x100RoundTrips() {
        StringBuilder all = new StringBuilder();
        for (char c = 0; c < 0x100; c++) all.append(c);
        assertEquals(all.toString(), decode(Ts.str(all.toString())));
    }

    @Test
    void commentsCannotEndEarly() {
        for (String v : VECTORS) {
            String c = Ts.comment(v);
            for (int i = 0; i < c.length(); i++) {
                char ch = c.charAt(i);
                assertFalse(ch == '\n' || ch == '\r' || ch == '\u2028' || ch == '\u2029', "line terminator in comment text for " + v);
            }
        }
        assertEquals("a\\u2028b", Ts.comment("a\u2028b"));
    }

    @Test
    void regexLiteralsOnlyWhenSafe() {
        assertEquals("/^Save$/i", Ts.regex("^Save$", "i"));
        assertEquals("/a\\/b/", Ts.regex("a\\/b", ""));
        assertEquals("new RegExp('a/b', 'i')", Ts.regex("a/b", "i"));        // unescaped slash would end the literal
        assertEquals("new RegExp('*x')", Ts.regex("*x", ""));                  // '/*' opens a comment
        assertEquals("new RegExp('a\\\\')", Ts.regex("a\\", ""));              // trailing backslash escapes the closing '/'
        assertEquals("new RegExp('[/]')", Ts.regex("[/]", ""));
        assertEquals("new RegExp('a\\nb')", Ts.regex("a\nb", ""));
        assertEquals("new RegExp('')", Ts.regex("", ""));
        assertThrows(IllegalArgumentException.class, () -> Ts.regex("a", "i'); x('"));
    }

    @Test
    void fullMatchSourceEscapesEveryMetaCharacter() {
        assertEquals("^Orders\\s+\\(3\\)$", Ts.fullMatchSource("  Orders   (3) "));
        assertEquals("^a\\.b\\*c\\+\\?\\^\\$\\{\\}\\[\\]\\|\\\\\\/$", Ts.fullMatchSource("a.b*c+?^${}[]|\\/"));
    }

    @Test
    void identifiersAreSafe() {
        assertEquals("st_save__smoke_", Ts.ident("st_", "save [smoke]"));
        assertEquals("st____", Ts.ident("st_", "'\";"));
    }
}
