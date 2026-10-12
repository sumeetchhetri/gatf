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
 * Δ160 — TypeScript literal and comment escaping for exported specs. Every piece of script-supplied text goes through
 * here: {@link #str} for string literals (single-quoted, so backticks and {@code ${} need no escaping), {@link #comment}
 * for {@code //} comment text (a line terminator inside it would end the comment and turn the rest into code), and
 * {@link #regex} for regular-expression literals. Pure.
 */
public final class Ts {

    private Ts() {}

    /** A single-quoted TypeScript string literal holding exactly {@code s}. */
    public static String str(String s) {
        StringBuilder b = new StringBuilder(s.length() + 2).append('\'');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': b.append("\\\\"); break;
                case '\'': b.append("\\'"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                case '\b': b.append("\\b"); break;
                case '\f': b.append("\\f"); break;
                case '\u000B': b.append("\\v"); break;
                case '\u2028': b.append("\\u2028"); break;
                case '\u2029': b.append("\\u2029"); break;
                default:
                    if (c < 0x20 || c == 0x7F) {
                        b.append(String.format("\\x%02x", (int) c));
                    } else if (Character.isHighSurrogate(c) && i + 1 < s.length() && Character.isLowSurrogate(s.charAt(i + 1))) {
                        b.append(c).append(s.charAt(++i));
                    } else if (Character.isSurrogate(c)) {
                        b.append(String.format("\\u%04x", (int) c));   // a lone surrogate cannot be written as UTF-8
                    } else {
                        b.append(c);
                    }
            }
        }
        return b.append('\'').toString();
    }

    /**
     * Text safe inside a {@code //} comment: JavaScript line terminators (LF, CR, U+2028, U+2029) and other control
     * characters are shown escaped, so the comment can never end early.
     */
    public static String comment(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\n') b.append("\\n");
            else if (c == '\r') b.append("\\r");
            else if (c == '\u2028') b.append("\\u2028");
            else if (c == '\u2029') b.append("\\u2029");
            else if (c == '\t') b.append(c);
            else if (c < 0x20 || c == 0x7F) b.append(String.format("\\x%02x", (int) c));
            else if (Character.isHighSurrogate(c) && i + 1 < s.length() && Character.isLowSurrogate(s.charAt(i + 1))) b.append(c).append(s.charAt(++i));
            else if (Character.isSurrogate(c)) b.append(String.format("\\u%04x", (int) c));
            else b.append(c);
        }
        return b.toString();
    }

    /**
     * A regular expression for {@code pattern} with {@code flags}: a {@code /…/flags} literal when the pattern can be
     * written as one safely, else {@code new RegExp('…', 'flags')}. A literal is refused for an empty pattern, one
     * starting with {@code *} (it would open a comment), an unescaped {@code /}, a line terminator or control
     * character, or a trailing odd backslash.
     */
    public static String regex(String pattern, String flags) {
        String f = flags == null ? "" : flags;
        if (!f.matches("[dgimsuvy]*")) throw new IllegalArgumentException("invalid regex flags " + f);
        return literalSafe(pattern) ? "/" + pattern + "/" + f : "new RegExp(" + str(pattern) + (f.isEmpty() ? "" : ", " + str(f)) + ")";
    }

    static boolean literalSafe(String p) {
        if (p == null || p.isEmpty() || p.startsWith("*")) return false;
        boolean esc = false;
        for (int i = 0; i < p.length(); i++) {
            char c = p.charAt(i);
            if (c < 0x20 || c == 0x7F || c == '\u2028' || c == '\u2029' || Character.isSurrogate(c)) return false;
            if (esc) { esc = false; continue; }
            if (c == '\\') { esc = true; continue; }
            if (c == '/' || c == '[') return false;   // '[': a class may hide '/'; keep it simple and safe
        }
        return !esc;
    }

    /** {@code s} as a JavaScript regex source that matches it literally. */
    public static String escapeRegex(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ("\\^$.*+?()[]{}|/".indexOf(c) >= 0) b.append('\\');
            b.append(c);
        }
        return b.toString();
    }

    /** Whole value, whitespace-collapsed, case-insensitive (seleasy's plain semantic value) as a regex source. */
    public static String fullMatchSource(String value) {
        String[] words = value.trim().split("\\s+");
        StringBuilder b = new StringBuilder("^");
        for (int i = 0; i < words.length; i++) {
            if (i > 0) b.append("\\s+");
            b.append(escapeRegex(words[i]));
        }
        return b.append('$').toString();
    }

    /** A TypeScript identifier from free text: letters, digits and _, never starting with a digit. */
    public static String ident(String prefix, String text) {
        StringBuilder b = new StringBuilder(prefix);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            b.append((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') ? c : '_');
        }
        return b.toString();
    }
}
