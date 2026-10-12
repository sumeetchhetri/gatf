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
package com.gatf.docs;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Q11 — seleasy script → documented test cases (pure; reads the script and the files it includes/imports).
 *
 * <p>One test case for the script's own top-level steps, plus one per subtest defined in the script (subtests from
 * imported files are only callable). {@code include}d files are inlined, {@code @call "x"} is expanded to x's steps,
 * {@code ??} assertions become expected results (numbered against their step), everything else becomes a step in
 * plain words. Leading {@code //} comments give the description and @tags; a comment right above a subtest gives
 * that subtest's.
 */
public final class SelDocs {

    private static final class Block {
        final String name;
        final List<String> body = new ArrayList<String>();
        final List<String> comments = new ArrayList<String>();
        /** E2: only | skip | fixme marker of a subtest, else null; subtest = not a func or hook. */
        String marker;
        boolean subtest;

        Block(String name) {
            this.name = name;
        }
    }

    private static final class Parsed {
        final List<String> header = new ArrayList<String>();
        final List<String> top = new ArrayList<String>();
        final List<Block> subtests = new ArrayList<Block>();
        final List<Block> funcs = new ArrayList<Block>();
        /** E2: beforeAll / beforeEach / afterEach / afterAll blocks by canonical name. */
        final Map<String, Block> hooks = new LinkedHashMap<String, Block>();
    }

    private final Pattern req;
    private final Map<String, Block> callable = new LinkedHashMap<String, Block>();
    private final Set<Path> visited = new HashSet<Path>();

    private SelDocs(Pattern req) {
        this.req = req;
    }

    public static List<TestDoc> fromScript(Path file, String source, String module, Pattern reqPattern) throws IOException {
        return new SelDocs(reqPattern).script(file, source, module);
    }

    private List<TestDoc> script(Path file, String source, String module) throws IOException {
        Parsed p = parse(Files.readAllLines(file, StandardCharsets.UTF_8));
        visited.add(file.toAbsolutePath().normalize());
        collectCallables(file, p);

        List<TestDoc> out = new ArrayList<TestDoc>();
        String headerText = String.join(" ", p.header);
        List<String> pre = new ArrayList<String>();
        List<String[]> topItems = new ArrayList<String[]>();
        onlyInFile = false;
        for (Block b : p.subtests) onlyInFile |= "only".equals(b.marker);
        expand(file, p.top, topItems, pre, 0, null);
        // E2: beforeAll runs before every case of the script, beforeEach before each subtest: both are preconditions.
        // afterEach / afterAll are cleanup, not part of what a case verifies: left out (no postcondition column).
        List<String> beforeAll = hookSteps(file, p.hooks.get("beforeAll"), "Before all"), beforeEach = hookSteps(file, p.hooks.get("beforeEach"), "Before each");
        boolean hasTopSteps = false;
        for (String[] it : topItems) if (it[0].equals("s")) hasTopSteps = true;

        if (hasTopSteps || p.subtests.isEmpty()) {
            TestDoc d = doc(source, module, null, title(file, p.header), headerText);
            d.description = description(p.header);
            d.preconditions.addAll(pre);
            d.preconditions.addAll(beforeAll);
            fill(d, topItems);
            out.add(d);
        }
        for (Block b : p.subtests) {
            TestDoc d = doc(source, module, b.name, b.name, headerText + " " + String.join(" ", b.comments));
            if (!b.comments.isEmpty()) d.description = clean(String.join(" ", b.comments));
            d.preconditions.addAll(pre);
            d.preconditions.addAll(beforeAll);
            d.preconditions.addAll(beforeEach);
            List<String[]> items = new ArrayList<String[]>();
            expand(file, b.body, items, d.preconditions, 0, null);
            fill(d, items);
            if ("skip".equals(b.marker) || "fixme".equals(b.marker)) {
                // E2: reported as skipped by every run (the reason is the marker)
                d.status = "skipped";
                d.description = ("Skipped: marked " + b.marker + " in the script." + (d.description.isEmpty() ? "" : " " + d.description));
            } else if ("only".equals(b.marker)) {
                d.description = ("Marked only: the script's other subtests are skipped." + (d.description.isEmpty() ? "" : " " + d.description));
            }
            out.add(d);
        }
        return out;
    }

    /** E2: a file of this script has a subtest marked only (its other subtests are skipped when @call'ed). */
    private boolean onlyInFile;

    /** E2: a hook block's steps and checks as precondition lines ("Before all: Navigate to …"). */
    private List<String> hookSteps(Path file, Block hook, String label) {
        List<String> out = new ArrayList<String>();
        if (hook == null) return out;
        List<String[]> items = new ArrayList<String[]>();
        expand(file, hook.body, items, out, 0, null);
        for (String[] it : items) if (!it[0].equals("d")) out.add(label + ": " + it[1]);
        return out;
    }

    private TestDoc doc(String source, String module, String name, String title, String tagText) {
        TestDoc d = new TestDoc();
        d.type = "UI";
        d.source = source;
        d.module = module;
        d.name = name;
        d.title = title;
        d.addTags(tagText, req);
        return d;
    }

    /** First header comment line that is not only tags/decoration, else the file name. */
    /** Δ145b: quoted text blanked, as Command.sanitize masks it, so a quoted '*' '/' never closes a block comment. */
    static String maskQuotes(String s) {
        return s.replaceAll("\"(?:\\\\.|[^\"\\\\])*\"", "\"\"").replaceAll("'(?:\\\\.|[^'\\\\])*'", "''");
    }

    private static String title(Path file, List<String> header) {
        for (String h : header) {
            String c = clean(h);
            if (!c.isEmpty()) return c;
        }
        return file.getFileName().toString().replaceAll("\\.sel$", "");
    }

    /** Header comment lines after the one used as the title. */
    private static String description(List<String> header) {
        List<String> rest = new ArrayList<String>();
        boolean titled = false;
        for (String h : header) {
            String c = clean(h);
            if (c.isEmpty()) continue;
            if (!titled) {
                titled = true;
                continue;
            }
            rest.add(c);
        }
        return String.join(" ", rest);
    }

    /** Comment text without tags and box-drawing decoration. */
    static String clean(String comment) {
        return comment.replaceAll("(?<!\\S)@\\S+", "").replaceAll("[─━═—–-]{2,}", "").replaceAll("\\s+", " ").trim();
    }

    private static void fill(TestDoc d, List<String[]> items) {
        List<String> descr = new ArrayList<String>();
        for (String[] it : items) {
            if (it[0].equals("s")) {
                d.steps.add(it[1]);
            } else if (it[0].equals("e")) {
                d.expected.add((d.steps.isEmpty() ? "" : "(step " + d.steps.size() + ") ") + it[1]);
            } else if (it[0].equals("d")) {
                descr.add(it[1]);
            }
        }
        if (d.description.isEmpty()) d.description = clean(String.join(" ", descr));
    }

    // ---------------------------------------------------------------- parsing

    // E2: an optional only / skip / fixme marker before subtest (group 1); hook blocks are their own blocks
    private static final Pattern SUBTEST = Pattern.compile("(?i)^(?:(only|skip|fixme)\\s+(?=subtest\\s))?(subtest|func)\\s+(?:\"([^\"]*)\"|'([^']*)'|(\\S+))\\s*(\\{)?\\s*$");
    private static final Pattern HOOK = Pattern.compile("(?i)^(beforeall|beforeeach|aftereach|afterall)\\s*(\\{)?\\s*$");

    static Parsed parse(List<String> lines) {
        Parsed p = new Parsed();
        boolean inHeader = true;
        List<String> pendingComments = new ArrayList<String>();
        Block cur = null;
        int depth = 0;
        boolean awaitOpen = false;
        boolean inMulti = false;
        for (String raw : lines) {
            String t = raw.trim();
            if (inMulti) {                                 // /* … */ comment
                if (maskQuotes(t).contains("*/")) inMulti = false;   // Δ145b: a quoted */ does not close it (as Command)
                continue;
            }
            if (t.startsWith("/*")) {
                if (!maskQuotes(t.substring(2)).contains("*/")) inMulti = true;
                continue;
            }
            if (t.isEmpty()) {
                if (cur == null) pendingComments.clear();
                continue;
            }
            if (t.startsWith("//")) {
                String c = t.substring(2).trim();
                if (inHeader) p.header.add(c);
                else if (cur == null) pendingComments.add(c);
                continue;
            }
            inHeader = false;
            if (cur == null) {
                Matcher m = SUBTEST.matcher(t);
                if (m.matches()) {
                    String name = m.group(3) != null ? m.group(3) : m.group(4) != null ? m.group(4) : m.group(5);
                    cur = new Block(name);
                    cur.marker = m.group(1) == null ? null : m.group(1).toLowerCase(Locale.ROOT);
                    cur.comments.addAll(pendingComments);
                    pendingComments.clear();
                    cur.subtest = !m.group(2).equalsIgnoreCase("func");
                    (cur.subtest ? p.subtests : p.funcs).add(cur);
                    depth = m.group(6) != null ? 1 : 0;
                    awaitOpen = m.group(6) == null;
                    continue;
                }
                Matcher h = HOOK.matcher(t);
                if (h.matches()) {
                    String hk = h.group(1).toLowerCase(Locale.ROOT);
                    cur = new Block(hk.startsWith("before") ? "before" + (hk.endsWith("all") ? "All" : "Each") : "after" + (hk.endsWith("all") ? "All" : "Each"));
                    pendingComments.clear();
                    p.hooks.putIfAbsent(cur.name, cur);
                    depth = h.group(2) != null ? 1 : 0;
                    awaitOpen = h.group(2) == null;
                    continue;
                }
                pendingComments.clear();
                p.top.add(t);
                continue;
            }
            // inside a subtest/func body
            if (awaitOpen) {
                if (t.equals("{")) {
                    depth = 1;
                    awaitOpen = false;
                    continue;
                }
                awaitOpen = false;   // body without braces is not seleasy; treat the line as top-level again
                cur = null;
                p.top.add(t);
                continue;
            }
            if (t.equals("}")) {
                depth--;
                if (depth == 0) {
                    cur = null;
                    continue;
                }
            } else if (t.equals("{") || t.endsWith("{")) {
                depth++;
            }
            cur.body.add(t);
        }
        return p;
    }

    private void collectCallables(Path file, Parsed p) {
        for (Block b : p.subtests) callable.putIfAbsent(b.name, b);
        for (Block b : p.funcs) callable.putIfAbsent(b.name, b);
        for (String t : p.top) {
            String[] kw = t.split("\\s+", 2);
            String k = kw[0].toLowerCase(Locale.ROOT);
            if ((k.equals("import") || k.equals("include")) && kw.length > 1) {
                Path f = resolve(file, stripComment(kw[1]));
                if (f != null && visited.add(f.toAbsolutePath().normalize())) {
                    try {
                        collectCallables(f, parse(Files.readAllLines(f, StandardCharsets.UTF_8)));
                    } catch (IOException e) {
                        // unreadable import: its subtests stay unexpanded
                    }
                }
            }
        }
    }

    private static Path resolve(Path from, String ref) {
        String r = unquote(ref.trim());
        if (r.isEmpty()) return null;
        Path p = from.toAbsolutePath().getParent().resolve(r).normalize();
        return Files.isRegularFile(p) ? p : null;
    }

    /**
     * Turns script lines into items {kind, text}: "s" step, "e" expected, "d" description. Includes are inlined,
     * {@code @call} expanded (depth-limited), config/props loads recorded as preconditions.
     */
    private void expand(Path file, List<String> lines, List<String[]> out, List<String> pre, int depth, String prefix) {
        for (String raw : lines) {
            String t = stripComment(raw).trim();
            if (t.isEmpty() || t.equals("{") || t.equals("}")) continue;
            String[] kw = t.split("\\s+", 2);
            String k = kw[0].toLowerCase(Locale.ROOT);
            String rest = kw.length > 1 ? kw[1].trim() : "";
            if (k.equals("include")) {
                Path f = resolve(file, rest);
                if (f != null && depth < 5) {
                    try {
                        Parsed inc = parse(Files.readAllLines(f, StandardCharsets.UTF_8));
                        expand(f, inc.top, out, pre, depth + 1, prefix);
                    } catch (IOException e) {
                        out.add(new String[] { "s", "Run the steps in " + unquote(rest) });
                    }
                } else {
                    out.add(new String[] { "s", "Run the steps in " + unquote(rest) });
                }
                continue;
            }
            if (k.equals("import")) continue;
            if (k.equals("config") || k.equals("dynprops") || k.equals("props")) {
                pre.add("Test data loaded from " + unquote(rest));
                continue;
            }
            if (k.startsWith("@call")) {
                String name = unquote(t.substring(5).trim().split("\\s+")[0]);
                Block b = callable.get(name);
                if (b != null && ("skip".equals(b.marker) || "fixme".equals(b.marker) || (onlyInFile && b.marker == null && b.subtest))) {
                    continue;   // E2: not run (reported as skipped): its steps are not part of the caller's
                }
                if (b != null && depth < 5) {
                    expand(file, b.body, out, pre, depth + 1, name);
                } else {
                    out.add(new String[] { "s", "Perform \"" + name + "\"" });
                }
                continue;
            }
            String[] item = humanize(t);
            if (item != null) {
                if (prefix != null && !item[0].equals("d")) item[1] = "[" + prefix + "] " + item[1];
                out.add(item);
            }
        }
    }

    /** Removes a trailing {@code // comment} that is outside quotes and preceded by whitespace. */
    static String stripComment(String line) {
        boolean dq = false, sq = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"' && !sq && (i == 0 || line.charAt(i - 1) != '\\')) dq = !dq;
            else if (c == '\'' && !dq && (i == 0 || line.charAt(i - 1) != '\\')) sq = !sq;
            else if (c == '/' && !dq && !sq && i + 1 < line.length() && line.charAt(i + 1) == '/'
                    && (i == 0 || Character.isWhitespace(line.charAt(i - 1)))) {
                return line.substring(0, i);
            }
        }
        return line;
    }

    static String unquote(String s) {
        String t = s.trim();
        if (t.length() >= 2 && (t.charAt(0) == '"' || t.charAt(0) == '\'') && t.charAt(t.length() - 1) == t.charAt(0)) {
            return t.substring(1, t.length() - 1);
        }
        return t;
    }

    // ---------------------------------------------------------------- plain-words rendering

    private static final Pattern LOC = Pattern.compile("^([A-Za-z]+)@(\"(?:[^\"\\\\]|\\\\.)*\"|'[^']*'|\\S+)\\s*(.*)$");
    private static final Pattern ASSERT = Pattern.compile("^\\?\\?(\\d*)([+-]?)\\s+(.*)$");

    /** {@code css@"input.a"} → {@code "input.a" (css)}; returns {display, remainder} or null when not a locator. */
    static String[] locator(String s) {
        Matcher m = LOC.matcher(s.trim());
        if (!m.matches()) return null;
        String kind = m.group(1).toLowerCase(Locale.ROOT);
        String v = unquote(m.group(2));
        String disp = kind.equals("text") ? "text \"" + v + "\"" : kind.equals("id") ? "#" + v : "\"" + v + "\" (" + kind + ")";
        return new String[] { disp, m.group(3).trim() };
    }

    static String[] humanize(String t) {
        Matcher a = ASSERT.matcher(t);
        if (a.matches()) {
            String[] l = locator(a.group(3));
            String what = l != null ? l[0] : a.group(3);
            String tail = l != null ? l[1] : "";
            if (a.group(2).equals("+")) return new String[] { "s", "Check whether " + what + " is present" };
            if (a.group(2).equals("-")) {
                if (l == null || tail.isEmpty()) return new String[] { "s", "If present: " + what };
                String[] parts = tail.split("\\s+", 2);
                String act = parts[0].toLowerCase(Locale.ROOT), args = parts.length > 1 ? parts[1] : "";
                String locRaw = a.group(3).substring(0, a.group(3).length() - tail.length()).trim();
                String text;
                if (act.equals("randomize")) text = "enter a random value (" + args + ") into " + what;
                else if (act.equals("type")) text = "enter " + args + " into " + what;
                else text = lower(humanize(act + " " + locRaw + (args.isEmpty() ? "" : " " + args))[1]);
                return new String[] { "s", "If " + what + " is present, " + text };
            }
            if (!a.group(1).isEmpty() && tail.isEmpty()) {
                return new String[] { "e", what + " is displayed (within " + a.group(1) + "s)" };
            }
            return new String[] { "e", what + (tail.isEmpty() ? " is present" : " " + expectation(tail)) };
        }
        String[] kw = t.split("\\s+", 2);
        String k = kw[0].toLowerCase(Locale.ROOT);
        String rest = kw.length > 1 ? kw[1].trim() : "";
        String[] l = locator(rest);
        switch (k) {
            case "open": return new String[] { "s", "Open the " + (rest.isEmpty() ? "" : rest + " ") + "browser" };
            case "goto": return new String[] { "s", "Navigate to " + unquote(rest) };
            case "back": return new String[] { "s", "Go back" };
            case "forward": return new String[] { "s", "Go forward" };
            case "refresh": return new String[] { "s", "Refresh the page" };
            case "close": return new String[] { "s", "Close the browser" };
            case "maximize": return new String[] { "s", "Maximize the browser window" };
            case "click": case "dblclick": case "rightclick": case "hover": case "clear": case "submit": case "check": case "uncheck": {
                String verb = k.equals("dblclick") ? "Double-click" : k.equals("rightclick") ? "Right-click"
                        : Character.toUpperCase(k.charAt(0)) + k.substring(1);
                return new String[] { "s", verb + " " + (l != null ? l[0] : rest) };
            }
            case "type": case "typenb": {
                int end = endOfQuoted(rest);
                if (end > 0) {
                    String[] tl = locator(rest.substring(end).trim());
                    return new String[] { "s", "Enter \"" + unquote(rest.substring(0, end)) + "\" into " + (tl != null ? tl[0] : rest.substring(end).trim()) };
                }
                return new String[] { "s", "Type " + rest };
            }
            case "randomize": return new String[] { "s", "Enter a random value (" + rest + ")" };
            case "select": return new String[] { "s", "Select " + rest };
            case "sleep": return new String[] { "s", "Wait " + rest + " ms" };
            case "execjs": return new String[] { "s", "Run page JavaScript" };
            case "screenshot": case "ele-screenshot": return new String[] { "s", "Take a screenshot" };
            case "printpdf": return new String[] { "s", "Save the page as PDF" };
            case "api": return new String[] { "s", "Call API test " + rest };
            case "curl": return new String[] { "s", "Send HTTP request: curl " + abbreviate(rest, 120) };
            case "var": return new String[] { "s", "Set variable " + rest.split("\\s+")[0] };
            case "upload": return new String[] { "s", "Upload " + rest };
            case "frame": case "tab": case "window": return new String[] { "s", "Switch " + k + " " + rest };
            case "pass": case "fail": case "warn": return null;
            default: return new String[] { "s", abbreviate(t, 160) };
        }
    }

    private static String expectation(String tail) {
        String t = tail.trim();
        String lt = t.toLowerCase(Locale.ROOT);
        if (lt.equals("visible") || lt.equals("invisible") || lt.equals("enabled") || lt.equals("disabled")
                || lt.equals("selected") || lt.equals("checked")) {
            return "is " + lt;
        }
        Matcher m = Pattern.compile("^(text|value|title|attr@\\S+)\\s*(=|!=|contains|startswith|endswith|regex)?\\s*(.*)$", Pattern.CASE_INSENSITIVE).matcher(t);
        if (m.matches() && !m.group(3).isEmpty()) {
            String what = m.group(1).toLowerCase(Locale.ROOT).startsWith("attr@") ? "attribute " + m.group(1).substring(5) : m.group(1).toLowerCase(Locale.ROOT);
            String v = (m.group(3).startsWith("=") ? m.group(3).substring(1) : m.group(3)).trim();
            if (m.group(2) == null || m.group(2).equals("=")) return "has " + what + " " + v;
            return what + " " + (m.group(2).equals("!=") ? "is not" : m.group(2).toLowerCase(Locale.ROOT)) + " " + v;
        }
        return t;
    }

    private static int endOfQuoted(String s) {
        if (s.isEmpty() || (s.charAt(0) != '"' && s.charAt(0) != '\'')) return -1;
        char q = s.charAt(0);
        for (int i = 1; i < s.length(); i++) {
            if (s.charAt(i) == q && s.charAt(i - 1) != '\\') return i + 1;
        }
        return -1;
    }

    private static String lower(String s) {
        return s.isEmpty() ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    private static String abbreviate(String s, int n) {
        return s.length() > n ? s.substring(0, n - 3) + "..." : s;
    }
}
