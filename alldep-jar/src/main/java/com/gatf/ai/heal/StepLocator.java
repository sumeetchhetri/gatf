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
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import com.gatf.selenium.SemanticLocators;

/**
 * H1 (Δ149): the one find expression ({@code <by>@<selector>}) in a seleasy step line, located the way
 * {@code FindCommand} reads it: whitespace-separated tokens, quoted strings opaque, the selector bare or quoted. A line
 * with no locator, or with more than one ({@code relative …}, {@code actions draganddrop a b}), is not healable: the
 * healer only ever swaps one locator for another, so the rest of the step (verb, values, options) is kept byte for
 * byte. Pure.
 */
public final class StepLocator {

    /** The element actions the healer's scoring knows (a bonus only; anything else is ""). */
    static final java.util.Set<String> VERBS = java.util.Set.of("click", "dblclick", "doubleclick", "rightclick", "hover", "check",
            "uncheck", "submit", "tap", "type", "clear", "upload", "select");

    static final Pattern BY = Pattern.compile("(?i)id|name|class|classname|tag|tagname|xpath|text|partiallinktext|linktext|jquery|jq|\\$|css|cssselector|role|label|placeholder|testid|alt|title");

    public final String line;
    public final int start, end;     // [start, end) of the whole token in line
    public final String by;          // as written
    public final String classifier;  // unquoted, as FindCommand keeps it

    private StepLocator(String line, int start, int end, String by, String classifier) {
        this.line = line;
        this.start = start;
        this.end = end;
        this.by = by;
        this.classifier = classifier;
    }

    /** The token as written, e.g. {@code id@"save"}. */
    public String token() { return line.substring(start, end); }

    /** The line with this locator replaced by {@code locator} (indentation, verb and values untouched). */
    public String replace(String locator) {
        return line.substring(0, start) + locator + line.substring(end);
    }

    /** The leading whitespace of the line (for an inserted step). */
    public String indent() {
        int i = 0;
        while (i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '\t')) i++;
        return line.substring(0, i);
    }

    /** The verb, lower-case, without a trailing {@code !}: the first word after an optional {@code nowait}, or for {@code ??N <locator> <action>} the word after the locator; "" if none. */
    public String verb() {
        String m = masked(line);
        String[] w = m.trim().split("[\\t ]+");
        int i = 0;
        if (i < w.length && w[i].equalsIgnoreCase("nowait")) i++;
        if (i < w.length && w[i].startsWith("??")) {   // ??N <locator> <action>: the action follows the locator
            String[] after = m.substring(end).trim().split("[\\t ]+");
            return after.length == 0 ? "" : clean(after[0]);
        }
        return i >= w.length ? "" : clean(w[i]);
    }

    private static String clean(String w) {
        String v = w.toLowerCase(Locale.ROOT);
        if (v.endsWith("!")) v = v.substring(0, v.length() - 1);
        return VERBS.contains(v) ? v : "";
    }

    /** {@code CommandState.sanitize}'s two passes (double-quoted strings first, then single-quoted), as same-length masks. */
    private static final java.util.regex.Pattern DQ = java.util.regex.Pattern.compile("\"(?:\\\\\\\"|[^\\\"])*\"");
    private static final java.util.regex.Pattern SQ = java.util.regex.Pattern.compile("'(?:\\\\'|[^'])*'");
    static final char MASK = '\uE000';

    /** The line with every quoted string the runtime masks replaced by {@link #MASK}s of the same length. */
    static String masked(String line) {
        String m = mask(line, DQ);
        return mask(m, SQ);
    }

    private static String mask(String s, java.util.regex.Pattern p) {
        java.util.regex.Matcher m = p.matcher(s);
        StringBuilder b = new StringBuilder(s);
        java.util.Set<String> seen = new java.util.HashSet<String>();
        while (m.find()) seen.add(m.group());
        for (String g : seen) {   // the runtime replaces every occurrence of each matched string
            for (int i = s.indexOf(g); i >= 0; i = s.indexOf(g, i + g.length())) {   // String.replace: non-overlapping, left to right
                for (int k = i; k < i + g.length(); k++) b.setCharAt(k, MASK);
            }
        }
        return b.toString();
    }

    /**
     * The single locator in {@code line}, or null (none, several, a comment). Tokenised exactly as the runtime does it:
     * quoted strings masked (double-quoted first), then split on spaces and tabs; a token is a locator when its
     * unmasked text before the first {@code @} is a known {@code <by>}.
     */
    public static StepLocator find(String line) {
        if (line == null || line.length() > 4096 || line.indexOf(MASK) >= 0) return null;
        String t = line.trim();
        if (t.isEmpty() || t.startsWith("//") || t.startsWith("/*")) return null;
        String m = masked(line);
        List<StepLocator> found = new ArrayList<StepLocator>();
        int i = 0, n = line.length();
        while (i < n) {
            char c = m.charAt(i);
            if (c == ' ' || c == '\t') { i++; continue; }
            int s = i;
            while (i < n && m.charAt(i) != ' ' && m.charAt(i) != '\t') i++;
            int at = m.indexOf('@', s);
            if (at < 0 || at >= i || at == s) continue;
            if (!BY.matcher(m.substring(s, at)).matches()) continue;
            String cl = line.substring(at + 1, i);
            if (cl.length() >= 2 && cl.charAt(0) == cl.charAt(cl.length() - 1) && (cl.charAt(0) == '"' || cl.charAt(0) == '\'')) cl = cl.substring(1, cl.length() - 1);
            if (cl.isEmpty()) continue;
            found.add(new StepLocator(line, s, i, line.substring(s, at), cl));
        }
        return found.size() == 1 ? found.get(0) : null;
    }

    /**
     * Δ149: {@code fixed} is {@code original} with exactly {@code locator} in place of its one locator, and the runtime
     * reads it back that way (same token boundaries, nothing else changed). Guards against a value whose quotes would
     * re-tokenise the line, e.g. {@code role@textbox[name='Say "hello']} before a double-quoted value.
     */
    public static boolean sameStepWith(StepLocator original, String fixed, String locator) {
        StepLocator f = find(fixed);
        if (f == null || !f.token().equals(locator) || f.start != original.start) return false;
        return fixed.substring(0, f.start).equals(original.line.substring(0, original.start))
                && fixed.substring(f.end).equals(original.line.substring(original.end));
    }

    /**
     * How many elements {@code by@classifier} matches in the snapshot, as the runtime would resolve it: semantic
     * locators through the resolver port, id / name / class / tag / the CSS subset directly; -1 when the snapshot
     * cannot answer (xpath, jQuery, link text, plain {@code text@}, CSS outside the subset, a malformed locator).
     */
    public static int count(SnapshotResolver r, String by, String classifier) {
        String b = by.toLowerCase(Locale.ROOT);
        try {
            if (SemanticLocators.isSemantic(b, classifier)) {
                Map<String, Object> spec = SemanticLocators.parse(b, classifier);
                return r.count(spec);
            }
        } catch (IllegalArgumentException e) {
            return -1;
        }
        switch (b) {
            case "id": return r.countId(classifier);
            case "name": return r.countName(classifier);
            case "css": case "cssselector": return r.countCss(classifier);
            case "class": case "classname": return r.countClass(classifier);
            case "tag": case "tagname": return r.countTag(classifier);
            default: return -1;
        }
    }

    /** The elements a locator matches when the snapshot can tell (semantic, id, name, class, tag, CSS subset), else null. */
    public static List<SnapshotDom.Node> elements(SnapshotResolver r, String by, String classifier) {
        String b = by.toLowerCase(Locale.ROOT);
        try {
            if (SemanticLocators.isSemantic(b, classifier)) return r.resolve(SemanticLocators.parse(b, classifier));
        } catch (IllegalArgumentException e) {
            return null;
        }
        List<SnapshotDom.Node> all = new ArrayList<SnapshotDom.Node>(), out = new ArrayList<SnapshotDom.Node>();
        SnapshotResolver.descendants(r.dom().document, all);
        CssLite css = b.equals("css") || b.equals("cssselector") ? CssLite.parse(classifier) : null;
        for (SnapshotDom.Node e : all) {
            boolean ok;
            switch (b) {
                case "id": ok = classifier.equals(e.attr("id")); break;
                case "name": ok = classifier.equals(e.attr("name")); break;
                case "css": case "cssselector": if (css == null) return null; ok = css.matches(e); break;
                case "tag": case "tagname": ok = e.tag.equalsIgnoreCase(classifier); break;
                case "class": case "classname":
                    String c = e.attr("class");
                    ok = c != null && !classifier.matches(".*\\s.*") && List.of(c.trim().split("\\s+")).contains(classifier);
                    break;
                default: return null;
            }
            if (ok) out.add(e);
        }
        return out;
    }

    /** The locator as the healer re-reads its own output: {@code by@classifier} split the same way. */
    public static StepLocator parseLocator(String locator) {
        StepLocator l = find("click " + locator);
        return l != null && l.token().equals(locator) ? l : null;
    }
}
