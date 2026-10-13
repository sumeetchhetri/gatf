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

/**
 * Δ161 — Java literal and comment escaping for exported Selenium tests. Every piece of script-supplied text goes
 * through here. javac translates {@code \}{@code uXXXX} sequences <em>before</em> it reads tokens (JLS 3.3), even
 * inside comments and string literals, so a script value holding that text could otherwise end a literal or a
 * {@code //} comment and turn the rest of the line into code:
 * <ul>
 * <li>{@link #str}: a string literal holding exactly the input, written in printable ASCII. Every input backslash is
 * doubled, so an input {@code \}{@code u0022} stays text; control characters are 3-digit octal escapes; everything
 * above U+007E (U+2028/2029 and lone surrogates included) is a {@code \}{@code uXXXX} escape, whose backslash is
 * always preceded by an even run of backslashes and so is translated.</li>
 * <li>{@link #comment}: text safe in a {@code //} comment: CR / LF and other controls are shown escaped, and a run of
 * backslashes followed by {@code u} is doubled so it can never become a Unicode escape.</li>
 * </ul>
 * Pure.
 */
public final class Jv {

    private Jv() {}

    /** A double-quoted Java string literal holding exactly {@code s}, in printable ASCII. */
    public static String str(String s) {
        StringBuilder b = new StringBuilder(s.length() + 2).append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': b.append("\\\\"); break;
                case '"': b.append("\\\""); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                case '\b': b.append("\\b"); break;
                case '\f': b.append("\\f"); break;
                default:
                    if (c < 0x20 || c == 0x7F) b.append(String.format("\\%03o", (int) c));   // 3 digits: a following digit stays a digit
                    else if (c > 0x7E) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
            }
        }
        return b.append('"').toString();
    }

    /** Text safe inside a {@code //} comment: it can never end the comment or form a Unicode escape. */
    public static String comment(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\') {
                int j = i;
                while (j < s.length() && s.charAt(j) == '\\') j++;
                int run = j - i;
                boolean beforeU = j < s.length() && s.charAt(j) == 'u';
                for (int k = 0; k < (beforeU ? run * 2 : run); k++) b.append('\\');
                i = j - 1;
            } else if (c == '\n') b.append("\\n");
            else if (c == '\r') b.append("\\r");
            else if (c == '\t') b.append(c);
            else if (c < 0x20 || c == 0x7F) b.append(String.format("\\x%02x", (int) c));
            else if (c == 0x2028 || c == 0x2029 || c == 0x85) b.append(String.format("U+%04X", (int) c));
            else if (Character.isHighSurrogate(c) && i + 1 < s.length() && Character.isLowSurrogate(s.charAt(i + 1))) b.append(c).append(s.charAt(++i));
            else if (Character.isSurrogate(c)) b.append(String.format("U+%04X", (int) c));   // a lone surrogate cannot be written as UTF-8
            else b.append(c);
        }
        return b.toString();
    }

    /** {@link #comment}, also safe inside a block / Javadoc comment: a star-slash is written star-backslash-slash. */
    public static String blockComment(String s) {
        return comment(s).replace("*/", "*\\/");
    }

    /** A Java identifier from free text: ASCII letters, digits and _, never starting with a digit. */
    public static String ident(String prefix, String text) {
        StringBuilder b = new StringBuilder(prefix);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            b.append((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') ? c : '_');
        }
        if (b.length() == 0 || Character.isDigit(b.charAt(0))) b.insert(0, '_');
        return b.toString();
    }

    /**
     * The test class name for a script: its words capitalised (separators dropped) plus {@code Test}, e.g.
     * {@code e2e-01-semantic} → {@code E2e01SemanticTest}; {@code Sel} is prepended when it would start with a digit.
     */
    public static String className(String scriptName) {
        StringBuilder b = new StringBuilder();
        boolean up = true;
        for (int i = 0; i < scriptName.length(); i++) {
            char c = scriptName.charAt(i);
            boolean alnum = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
            if (!alnum) { up = true; continue; }
            b.append(up ? Character.toUpperCase(c) : c);
            up = false;
        }
        if (b.length() == 0 || Character.isDigit(b.charAt(0))) b.insert(0, "Sel");
        return b.append("Test").toString();
    }

    /**
     * An XPath 1.0 string literal for {@code s}: single- or double-quoted, or {@code concat(...)} when it holds both
     * quote kinds (XPath 1.0 has no escapes).
     */
    public static String xpathLiteral(String s) {
        if (s.indexOf('\'') < 0) return "'" + s + "'";
        if (s.indexOf('"') < 0) return "\"" + s + "\"";
        StringBuilder b = new StringBuilder("concat(");
        String[] parts = s.split("'", -1);
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) b.append(", \"'\", ");
            b.append('\'').append(parts[i]).append('\'');
        }
        return b.append(')').toString();
    }

    /**
     * {@code StringEscapeUtils.unescapeJava} (commons-text), which {@code chord} applies to its value: octal
     * {@code \0}–{@code \377}, {@code \}{@code u+XXXX} (repeated u, optional +), {@code \b \n \t \f \r \\ \" \'}; a
     * backslash before any other character is dropped.
     *
     * @throws IllegalArgumentException for a {@code \}{@code u} escape with fewer than 4 hex digits (seleasy then
     *         types nothing)
     */
    public static String unescapeJava(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '\\') { b.append(c); continue; }
            if (i + 1 >= s.length()) break;   // a trailing backslash is dropped too
            char n = s.charAt(i + 1);
            if (n >= '0' && n <= '7') {
                int end = i + 2, max = n <= '3' ? 3 : 2;
                while (end < s.length() && end - (i + 1) < max && s.charAt(end) >= '0' && s.charAt(end) <= '7') end++;
                b.append((char) Integer.parseInt(s.substring(i + 1, end), 8));
                i = end - 1;
            } else if (n == 'u') {
                int j = i + 1;
                while (j < s.length() && s.charAt(j) == 'u') j++;
                if (j < s.length() && s.charAt(j) == '+') j++;
                if (j + 4 > s.length() || !s.substring(j, j + 4).matches("[0-9A-Fa-f]{4}")) {
                    throw new IllegalArgumentException("Less than 4 hex digits in unicode value: '" + s.substring(i) + "'");
                }
                b.append((char) Integer.parseInt(s.substring(j, j + 4), 16));
                i = j + 3;
            } else {
                switch (n) {
                    case 'b': b.append('\b'); break;
                    case 'n': b.append('\n'); break;
                    case 't': b.append('\t'); break;
                    case 'f': b.append('\f'); break;
                    case 'r': b.append('\r'); break;
                    default: b.append(n); break;   // \\ \" \' and any other: the backslash is dropped
                }
                i++;
            }
        }
        return b.toString();
    }
}
