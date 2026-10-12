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
package com.gatf.coverage;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * H6 (Δ156): is a hunk's text an edit that stays inside the block it is in? Pure, a deliberately rough JS/TS lexer.
 *
 * <p>Line-level selection deselects a test only for an edit strictly inside code the test never ran. That proof
 * needs the edit to keep the block structure ({@code } else {} would move code out of the block) and not to declare
 * anything that hoists or changes module scope ({@code var}, a {@code function} declaration, {@code import},
 * {@code export}, TS {@code enum}/{@code namespace}/{@code declare}). Anything this lexer can't read with confidence
 * (unbalanced brackets, an unterminated string, comment or template, e.g. a multi-line string or JSX text with an
 * apostrophe) is reported, and the caller falls back to file level: the safe direction.
 *
 * @author Sumeet Chhetri
 */
public final class EditCheck {

    private EditCheck() {}

    private static final Set<String> SCOPE_WORDS = new HashSet<>(Arrays.asList("var", "import", "export", "enum", "namespace", "declare"));

    /** Null when {@code text} is balanced and declares nothing that hoists; else why not. */
    public static String check(String text) {
        if (text == null || text.isEmpty()) return null;
        Deque<Character> st = new ArrayDeque<>();   // ( [ { and 'T' for a template literal's ${ … }
        int n = text.length();
        int i = 0;
        String prevWord = null;                      // the previous identifier, when it is the previous token
        char prevSym = 0;                            // the previous non-word token char; 0 = start of the text
        boolean inTemplate = false;
        while (i < n) {
            char c = text.charAt(i);
            if (inTemplate) {
                if (c == '\\') { i += 2; continue; }
                if (c == '`') { inTemplate = false; i++; prevWord = null; prevSym = ')'; continue; }
                if (c == '$' && i + 1 < n && text.charAt(i + 1) == '{') { st.push('T'); inTemplate = false; i += 2; prevWord = null; prevSym = '{'; continue; }
                i++;
                continue;
            }
            if (c == '\n') { i++; prevWord = null; prevSym = ';'; continue; }   // ASI: a new line may start a statement
            if (Character.isWhitespace(c)) { i++; continue; }
            if (c == '/' && i + 1 < n && text.charAt(i + 1) == '/') {
                int e = text.indexOf('\n', i);
                i = e < 0 ? n : e + 1;
                continue;
            }
            if (c == '/' && i + 1 < n && text.charAt(i + 1) == '*') {
                int e = text.indexOf("*/", i + 2);
                if (e < 0) return "an unterminated /* comment";
                i = e + 2;
                continue;
            }
            if (c == '\'' || c == '"') {
                int j = i + 1;
                while (j < n && text.charAt(j) != c) {
                    if (text.charAt(j) == '\n') return "an unterminated string";
                    j += text.charAt(j) == '\\' ? 2 : 1;
                }
                if (j >= n) return "an unterminated string";
                i = j + 1;
                prevWord = null;
                prevSym = ')';
                continue;
            }
            if (c == '`') { inTemplate = true; i++; continue; }
            if (c == '(' || c == '[' || c == '{') { st.push(c); i++; prevWord = null; prevSym = c; continue; }
            if (c == ')' || c == ']' || c == '}') {
                if (st.isEmpty()) return "unbalanced '" + c + "'";
                char o = st.pop();
                if (c == '}' && o == 'T') { inTemplate = true; i++; continue; }
                if ((c == ')' && o != '(') || (c == ']' && o != '[') || (c == '}' && o != '{')) return "unbalanced '" + c + "'";
                i++;
                prevWord = null;
                prevSym = c;
                continue;
            }
            if (Character.isJavaIdentifierStart(c)) {
                int j = i + 1;
                while (j < n && Character.isJavaIdentifierPart(text.charAt(j))) j++;
                String w = text.substring(i, j);
                boolean member = prevSym == '.' && prevWord == null;   // obj.var, x.import: a property name
                if (!member && SCOPE_WORDS.contains(w)) return "it declares or changes scope ('" + w + "')";
                if (!member && w.equals("function") && declarationPosition(prevWord, prevSym) && namedNext(text, j)) {
                    return "it has a function declaration (hoisted)";
                }
                prevWord = w;
                prevSym = 0;
                i = j;
                continue;
            }
            prevWord = null;
            prevSym = c;
            i++;
        }
        if (inTemplate) return "an unterminated template literal";
        if (!st.isEmpty()) return "unbalanced '" + (st.peek() == 'T' ? "${" : String.valueOf(st.peek())) + "'";
        return null;
    }

    // `function` starts a declaration at the start of a statement: start of text, after ; { } or after async/default
    private static boolean declarationPosition(String prevWord, char prevSym) {
        if (prevWord != null) return prevWord.equals("async") || prevWord.equals("default");
        return prevSym == 0 || prevSym == ';' || prevSym == '{' || prevSym == '}';
    }

    // `function name(` or `function* name(`: a declaration; `function(` is an expression
    private static boolean namedNext(String t, int j) {
        int k = j;
        while (k < t.length() && Character.isWhitespace(t.charAt(k))) k++;
        if (k < t.length() && t.charAt(k) == '*') k++;
        while (k < t.length() && Character.isWhitespace(t.charAt(k))) k++;
        return k < t.length() && Character.isJavaIdentifierStart(t.charAt(k));
    }
}
