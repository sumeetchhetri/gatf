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
package com.gatf.selenium;

import java.util.List;
import java.util.Map;

/**
 * M3 — the locator the recorder and the SPA crawler write for an element: the first candidate that matches exactly
 * one element, in the order {@code testid@} → {@code role@…[name=…]} → {@code label@} → the legacy selector
 * (id → name → css → xpath). Candidates come from {@link SemanticLocators#CANDIDATES_FN} run in the page. Values
 * that seleasy cannot carry safely (both quote kinds, ']' in a name, a leading ~ = /, a ${ variable, very long
 * text) are skipped rather than written wrong. Pure.
 */
public final class LocatorRanking {

    public static final int MAX_TEXT = 60;

    private LocatorRanking() {}

    /** @param candidates maps {s, v | role+name, n} as returned by the page; @param fallback legacy seleasy selector */
    public static String choose(List<?> candidates, String fallback) {
        if (candidates != null) {
            for (String kind : new String[] { "testid", "role", "label" }) {
                for (Object o : candidates) {
                    if (!(o instanceof Map)) continue;
                    Map<?, ?> c = (Map<?, ?>) o;
                    if (!kind.equals(str(c.get("s"))) || !SemanticLocators.resolvesStrictly(SemanticLocators.matchCount(c.get("n")))) continue;
                    String loc = locator(kind, c);
                    if (loc != null) return loc;
                }
            }
        }
        return fallback;
    }

    /** Δ148: one candidate as seleasy ({@code testid@…}, {@code role@…[name=…]}, {@code label@…}), or null when its kind is
     *  unknown or its value is not expressible; the same rendering {@link #choose} uses. */
    public static String render(Map<?, ?> c) {
        if (c == null) return null;
        String kind = str(c.get("s"));
        return kind.equals("testid") || kind.equals("role") || kind.equals("label") ? locator(kind, c) : null;
    }

    static String locator(String kind, Map<?, ?> c) {
        if (kind.equals("role")) {
            String role = str(c.get("role")), name = str(c.get("name"));
            if (!role.matches("[a-z][a-z-]*")) return null;
            String q = value(name);
            return q == null || name.indexOf(']') >= 0 ? null : "role@" + role + "[name=" + q + "]";
        }
        String q = value(str(c.get("v")));
        return q == null ? null : kind + "@" + q;
    }

    /** A value as seleasy writes it: bare when simple, else single- or double-quoted; null when not expressible. */
    static String value(String v) {
        if (v == null || v.isEmpty() || v.length() > MAX_TEXT || unsafe(v)) return null;
        char f = v.charAt(0);
        if (f == '~' || f == '=' || f == '/') return null;   // would read as a match mode
        if (v.matches("[A-Za-z0-9_.:-]+")) return v;
        if (v.indexOf('\'') < 0) return "'" + v + "'";
        if (v.indexOf('"') < 0) return "\"" + v + "\"";
        return null;
    }

    /**
     * Δ148a: a value no locator may carry. {@code $} anywhere: {@code SeleniumTest.evaluate} sends any value with a
     * {@code $} through the template engine (context variables, {@code #set(…)}), so a page could plant a step that leaks
     * or changes test state. {@code #} followed by a letter (a Velocity directive; defence in depth, evaluate templates
     * only values that also hold a {@code $}). Any C0 control, DEL, NEL, LS or PS: they split the statement on the
     * recorder's save path (a bare CR is a line break to commons-io {@code readLines}).
     */
    public static boolean unsafe(String v) {
        if (v == null) return true;
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            if (c == '$' || c < 0x20 || c == 0x7f || c == 0x85 || c == 0x2028 || c == 0x2029) return true;
            if (c == '#' && i + 1 < v.length() && Character.isLetter(v.charAt(i + 1))) return true;
        }
        return false;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}
