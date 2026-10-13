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
package com.gatf.ai.heal;

import java.util.ArrayList;
import java.util.List;

import com.gatf.ai.heal.SnapshotDom.Node;

/**
 * H1 (Δ149): the CSS subset the healer evaluates against a snapshot: compound selectors of a type ({@code tag} or
 * {@code *}), {@code #id}, {@code .class}, {@code [attr]}, {@code [attr=value]} and {@code :nth-of-type(k)}, joined by
 * descendant (space) or child ({@code >}) combinators, with CSS escapes. That covers every path
 * {@code SemanticLocators.FALLBACK_FN} writes and the usual hand-written {@code css@}. Anything else parses to null,
 * so its count is "unknown" and it is never treated as strict. Pure.
 */
final class CssLite {

    private static final class Compound {
        String tag;   // null = any
        final List<String> ids = new ArrayList<String>(), classes = new ArrayList<String>();
        final List<String[]> attrs = new ArrayList<String[]>();   // {name, value|null}
        int nth;      // 0 = none
        char comb;    // combinator to the previous compound: ' ' or '>' (0 for the first)
    }

    private final List<Compound> parts;

    /** HTML attributes whose values selectors match ASCII case-insensitively in an HTML document. */
    static final java.util.Set<String> CI_ATTRS = java.util.Set.of("accept", "accept-charset", "align", "alink", "axis", "bgcolor",
            "charset", "checked", "clear", "codetype", "color", "compact", "declare", "defer", "dir", "direction", "disabled", "enctype",
            "face", "frame", "hreflang", "http-equiv", "lang", "language", "link", "media", "method", "multiple", "nohref", "noresize",
            "noshade", "nowrap", "readonly", "rel", "rev", "rules", "scope", "scrolling", "selected", "shape", "target", "text", "type",
            "valign", "valuetype", "vlink");

    private CssLite(List<Compound> parts) {
        this.parts = parts;
        this.selfMemo = newMemo();
        this.ancMemo = newMemo();
    }

    /** {@code CSS.serializeIdentifier}, as FALLBACK_FN's {@code esc}. */
    static String escape(String s) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == 0) out.append('�');
            else if ((c >= 1 && c <= 31) || c == 127 || (i == 0 && c >= '0' && c <= '9')
                    || (i == 1 && c >= '0' && c <= '9' && s.charAt(0) == '-')) out.append('\\').append(Integer.toHexString(c)).append(' ');
            else if (i == 0 && c == '-' && s.length() == 1) out.append('\\').append(c);
            else if (c >= 128 || c == '-' || c == '_' || (c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')) out.append(c);
            else out.append('\\').append(c);
        }
        return out.toString();
    }

    static CssLite parse(String css) {
        if (css == null) return null;
        String s = css.trim();
        if (s.isEmpty() || s.length() > 4096) return null;
        List<Compound> parts = new ArrayList<Compound>();
        int[] i = { 0 };
        char comb = 0;
        while (i[0] < s.length()) {
            Compound c = compound(s, i);
            if (c == null) return null;
            c.comb = parts.isEmpty() ? 0 : comb;
            parts.add(c);
            boolean ws = false;
            while (i[0] < s.length() && Character.isWhitespace(s.charAt(i[0]))) { i[0]++; ws = true; }
            if (i[0] >= s.length()) break;
            char ch = s.charAt(i[0]);
            if (ch == '>') {
                comb = '>';
                i[0]++;
                while (i[0] < s.length() && Character.isWhitespace(s.charAt(i[0]))) i[0]++;
                if (i[0] >= s.length()) return null;
            } else if (ws) {
                comb = ' ';
            } else {
                return null;   // ',', '+', '~', or anything else outside the subset
            }
        }
        return parts.isEmpty() ? null : new CssLite(parts);
    }

    private static Compound compound(String s, int[] i) {
        Compound c = new Compound();
        boolean any = false;
        if (i[0] < s.length() && s.charAt(i[0]) == '*') { i[0]++; any = true; }
        else {
            String t = ident(s, i);
            if (t != null) { c.tag = t.toLowerCase(java.util.Locale.ROOT); any = true; }
        }
        while (i[0] < s.length()) {
            char ch = s.charAt(i[0]);
            if (ch == '#') {
                i[0]++;
                String v = ident(s, i);
                if (v == null) return null;
                c.ids.add(v);
            } else if (ch == '.') {
                i[0]++;
                String v = ident(s, i);
                if (v == null) return null;
                c.classes.add(v);
            } else if (ch == '[') {
                int e = s.indexOf(']', i[0]);
                if (e < 0) return null;
                String body = s.substring(i[0] + 1, e).trim();
                i[0] = e + 1;
                int eq = body.indexOf('=');
                if (eq < 0) {
                    if (!body.matches("[A-Za-z_][A-Za-z0-9_:-]*")) return null;
                    c.attrs.add(new String[] { body.toLowerCase(java.util.Locale.ROOT), null });
                } else {
                    String n = body.substring(0, eq).trim(), v = body.substring(eq + 1).trim();
                    if (!n.matches("[A-Za-z_][A-Za-z0-9_:-]*")) return null;   // ^= *= $= ~= |= are outside the subset
                    if (v.length() >= 2 && (v.charAt(0) == '"' || v.charAt(0) == '\'') && v.charAt(v.length() - 1) == v.charAt(0)) v = v.substring(1, v.length() - 1);
                    else if (!v.matches("(?:--|-?[A-Za-z_])[A-Za-z0-9_-]*")) return null;   // an unquoted value must be an identifier
                    if (v.indexOf('\\') >= 0) return null;
                    c.attrs.add(new String[] { n.toLowerCase(java.util.Locale.ROOT), v });
                }
            } else if (s.startsWith(":nth-of-type(", i[0])) {
                int e = s.indexOf(')', i[0]);
                if (e < 0) return null;
                String k = s.substring(i[0] + 13, e).trim();
                if (!k.matches("[1-9][0-9]{0,5}")) return null;
                c.nth = Integer.parseInt(k);
                i[0] = e + 1;
            } else {
                break;
            }
            any = true;
        }
        return any ? c : null;
    }

    /** A CSS identifier with escapes resolved; null if none at {@code i}. */
    private static String ident(String s, int[] i) {
        StringBuilder b = new StringBuilder();
        int p = i[0];
        // an identifier cannot start with a digit, or '-' + digit (unescaped): #1a, .2b, div#-3 are invalid selectors
        if (p < s.length() && Character.isDigit(s.charAt(p))) return null;
        if (p + 1 < s.length() && s.charAt(p) == '-' && Character.isDigit(s.charAt(p + 1))) return null;
        while (p < s.length()) {
            char ch = s.charAt(p);
            if (ch == '\\') {
                if (p + 1 >= s.length()) return null;
                int h = p + 1, end = h;
                while (end < s.length() && end - h < 6 && Character.digit(s.charAt(end), 16) >= 0) end++;
                if (end > h) {
                    int cp = Integer.parseInt(s.substring(h, end), 16);
                    b.appendCodePoint(cp == 0 || cp > 0x10FFFF || (cp >= 0xD800 && cp <= 0xDFFF) ? 0xFFFD : cp);
                    p = end;
                    if (p < s.length() && s.charAt(p) == ' ') p++;
                } else {
                    b.append(s.charAt(h));
                    p = h + 1;
                }
                continue;
            }
            if (ch >= 128 || ch == '-' || ch == '_' || Character.isLetterOrDigit(ch)) { b.append(ch); p++; continue; }
            break;
        }
        if (b.length() == 0) return null;
        i[0] = p;
        return b.toString();
    }

    // memo per (element, compound): the DOM is fixed for this selector's lifetime, so matching is O(elements x compounds)
    private final List<java.util.Map<Node, Boolean>> selfMemo, ancMemo;

    private List<java.util.Map<Node, Boolean>> newMemo() {
        List<java.util.Map<Node, Boolean>> m = new ArrayList<java.util.Map<Node, Boolean>>();
        for (int i = 0; i < parts.size(); i++) m.add(new java.util.IdentityHashMap<Node, Boolean>());
        return m;
    }

    /** The rightmost compound's type, or null for any: lets a caller look at elements of that tag only. */
    String keyTag() {
        return parts.get(parts.size() - 1).tag;
    }

    boolean matches(Node e) {
        return match(e, parts.size() - 1);
    }

    private boolean match(Node e, int k) {
        if (e == null || !e.isElement()) return false;
        Boolean c = selfMemo.get(k).get(e);
        if (c != null) return c;
        boolean r = one(parts.get(k), e);
        if (r && k > 0) r = parts.get(k).comb == '>' ? match(e.parentElement(), k - 1) : anc(e, k - 1);
        selfMemo.get(k).put(e, r);
        return r;
    }

    /** Some ancestor of e matches compounds 0..k. */
    private boolean anc(Node e, int k) {
        Node p = e.parentElement();
        if (p == null) return false;
        Boolean c = ancMemo.get(k).get(e);
        if (c != null) return c;
        boolean r = match(p, k) || anc(p, k);
        ancMemo.get(k).put(e, r);
        return r;
    }

    private static boolean one(Compound c, Node e) {
        if (c.tag != null && !c.tag.equals(e.tag)) return false;
        for (String id : c.ids) if (!id.equals(e.attr("id"))) return false;
        if (!c.classes.isEmpty()) {
            String cl = e.attr("class");
            if (cl == null) return false;
            List<String> have = List.of(cl.trim().split("\\s+"));
            for (String x : c.classes) if (!have.contains(x)) return false;
        }
        for (String[] a : c.attrs) {
            String v = e.attr(a[0]);
            if (v == null) return false;
            if (a[1] != null && !(CI_ATTRS.contains(a[0]) ? a[1].equalsIgnoreCase(v) : a[1].equals(v))) return false;
        }
        return c.nth <= 0 || e.nthOfType == c.nth;   // precomputed by SnapshotDom
    }
}
