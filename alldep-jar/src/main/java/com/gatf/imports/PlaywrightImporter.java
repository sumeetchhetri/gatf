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
package com.gatf.imports;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.gatf.record.PickLocator;
import com.gatf.selenium.LocatorRanking;

/**
 * Best-effort importer: Playwright test specs (.spec.ts / .spec.js) → a seleasy suite.
 *
 * The Playwright API is JavaScript, so this is a line/regex extractor (not a JS AST parse): it finds
 * each test(...) block and translates the page.* actions and expect(...) assertions inside it into the
 * seleasy grammar. Every test() becomes a file-scope `subtest "name" { ... }` (which auto-runs, matching
 * sample/test.sel). Constructs it cannot translate are emitted as `// UNSUPPORTED: ...` comments AND
 * collected into the ImportResult report — nothing is dropped silently.
 *
 * Selector mapping uses GATF's own selector prefixes (id@/css@/xpath@/text@/name@/class@ — the set the
 * Command.java resolver accepts). Playwright's default CSS selectors map directly to css@.
 *
 * Hooks: test.beforeEach / afterEach bodies are inlined at the start / end of every test's subtest
 * (reproducing per-test setup); test.beforeAll / afterAll become a first / last subtest.
 *
 * Note: seleasy {@code sleep} is in MILLISECONDS (Command.SleepCommand → Thread.sleep(ms)).
 */
public class PlaywrightImporter {

    // test('name', async ... )  /  test("name", ...)  /  test(`name`, ...)  + test.only / test.skip
    private static final Pattern TEST = Pattern.compile(
            "\\btest(?:\\.(?:only|skip|fixme))?\\s*\\(\\s*([\"'`])(.*?)\\1", Pattern.DOTALL);
    // await page.<method>(<args>)   or   await page.locator('..').<method>(<args>)  (also without await)
    private static final Pattern PAGE_CALL = Pattern.compile(
            "(?:await\\s+)?page\\s*\\.\\s*([a-zA-Z]+)\\s*\\(");
    private static final Pattern LOCATOR_CALL = Pattern.compile(
            "(?:await\\s+)?page\\s*\\.\\s*locator\\s*\\(\\s*([\"'`])(.*?)\\1\\s*\\)\\s*\\.\\s*([a-zA-Z]+)\\s*\\(");
    private static final Pattern GETBY = Pattern.compile(
            "(?:await\\s+)?page\\s*\\.\\s*(getBy[A-Za-z]+)\\s*\\(\\s*([\"'`])(.*?)\\2");
    // test.beforeEach / afterEach / beforeAll / afterAll hooks (also on test.describe-scoped `test`)
    private static final Pattern HOOK = Pattern.compile(
            "\\btest\\s*\\.\\s*(beforeEach|afterEach|beforeAll|afterAll)\\s*\\(");
    private static final Pattern EXPECT = Pattern.compile(
            "(?:await\\s+)?expect\\s*\\(\\s*(.*?)\\s*\\)\\s*\\.\\s*(?:not\\.)?([a-zA-Z]+)\\s*\\(");

    public ImportResult importSource(String src, String suiteName) {
        ImportResult res = new ImportResult();
        res.type = "playwright";
        res.kind = "sel";
        res.fileName = safeName(suiteName) + ".sel";
        if (src == null) src = "";
        String[] lines = src.split("\r?\n", -1);

        StringBuilder out = new StringBuilder();
        out.append("// Imported from Playwright by GATF — best-effort; review selectors and assertions.\n");
        out.append("open chrome\n").append("maximize\n\n");

        int tests = 0, actions = 0;
        // Find each test() and translate the statements up to its matching close (brace-tracked over the source).
        Matcher tm = TEST.matcher(src);
        List<int[]> testSpans = new ArrayList<int[]>();
        List<String> testNames = new ArrayList<String>();
        while (tm.find()) {
            int arrow = src.indexOf("=>", tm.end());
            int bodyStart = src.indexOf('{', arrow >= 0 ? arrow : tm.end());
            if (bodyStart < 0) continue;
            int bodyEnd = matchBrace(src, bodyStart);
            testSpans.add(new int[] { bodyStart + 1, bodyEnd });
            testNames.add(tm.group(2));
        }

        // hooks — translated once, inlined per test (Each) or emitted as their own subtest (All)
        StringBuilder beforeEach = new StringBuilder(), afterEach = new StringBuilder();
        StringBuilder beforeAll = new StringBuilder(), afterAll = new StringBuilder();
        int hooks = 0;
        Matcher hm = HOOK.matcher(src);
        while (hm.find()) {
            int arrow = src.indexOf("=>", hm.end());
            int bodyStart = src.indexOf('{', arrow >= 0 ? arrow : hm.end());
            if (bodyStart < 0) continue;
            int bodyEnd = matchBrace(src, bodyStart);
            String kind = hm.group(1);
            StringBuilder dst = kind.equals("beforeEach") ? beforeEach : kind.equals("afterEach") ? afterEach
                    : kind.equals("beforeAll") ? beforeAll : afterAll;
            actions += translateBody(src.substring(bodyStart + 1, Math.max(bodyStart + 1, bodyEnd)),
                    lineNumberAt(src, bodyStart + 1), dst, res);
            hooks++;
        }

        if (testSpans.isEmpty() && hooks == 0) {
            // No test() wrapper — translate the whole file as one subtest.
            testSpans.add(new int[] { 0, src.length() });
            testNames.add(suiteName);
        }

        if (beforeAll.length() > 0) out.append("subtest \"beforeAll\"\n{\n").append(beforeAll).append("}\n\n");
        for (int i = 0; i < testSpans.size(); i++) {
            tests++;
            String name = testNames.get(i).trim();
            if (name.isEmpty()) name = "test " + tests;
            out.append("subtest \"").append(escSel(name)).append("\"\n{\n");
            String body = src.substring(testSpans.get(i)[0], Math.max(testSpans.get(i)[0], testSpans.get(i)[1]));
            int lineBase = lineNumberAt(src, testSpans.get(i)[0]);
            out.append(beforeEach);
            actions += translateBody(body, lineBase, out, res);
            out.append(afterEach);
            out.append("}\n\n");
        }
        if (afterAll.length() > 0) out.append("subtest \"afterAll\"\n{\n").append(afterAll).append("}\n\n");

        res.content = out.toString();
        res.stat("tests", tests);
        res.stat("actions", actions);
        res.stat("hooks", hooks);
        res.stat("unsupported", res.unsupported.size());
        return res;
    }

    // Translate the statements of one test body. Returns the count of emitted actions.
    private int translateBody(String body, int lineBase, StringBuilder out, ImportResult res) {
        int actions = 0;
        String[] stmts = body.split(";\\s*\r?\n|\r?\n");
        for (String raw : stmts) {
            String s = raw.trim();
            if (s.isEmpty() || s.startsWith("//")) continue;
            int lineNo = lineBase; // approximate — per-statement line within the body is not tracked precisely
            String sel = null;

            // expect(...) first — its subject may itself contain page.getBy*/locator(...)
            if (s.matches("^(?:await\\s+)?expect\\s*\\(.*")) {
                Matcher em0 = EXPECT.matcher(s);
                if (em0.find()) {
                    String line = emitExpect(em0.group(1), em0.group(2).toLowerCase(Locale.ROOT), argsAfter(s, em0.end() - 1), s, lineNo, res);
                    if (line != null) { out.append("  ").append(line).append("\n"); actions++; }
                    continue;
                }
            }
            // page.locator('x').action(args)
            Matcher lm = LOCATOR_CALL.matcher(s);
            if (lm.find()) {
                String selector = pwSelector(lm.group(2));
                String method = lm.group(3).toLowerCase(Locale.ROOT);
                String args = argsAfter(s, lm.end() - 1);
                String line = emitAction(method, selector, args, s, lineNo, res);
                if (line != null) { out.append("  ").append(line).append("\n"); actions++; }
                continue;
            }
            // page.getByRole/getByText/getByLabel/... ('x').action(args)
            Matcher gm = GETBY.matcher(s);
            if (gm.find()) {
                String selector = getBySelector(gm.group(1), gm.group(3), s, lineNo, res);
                int afterClose = s.indexOf(')', gm.end());
                Matcher act = Pattern.compile("\\.\\s*([a-zA-Z]+)\\s*\\(").matcher(s);
                if (afterClose >= 0 && act.find(afterClose)) {
                    String method = act.group(1).toLowerCase(Locale.ROOT);
                    String args = argsAfter(s, act.end() - 1);
                    String line = emitAction(method, selector, args, s, lineNo, res);
                    if (line != null) { out.append("  ").append(line).append("\n"); actions++; }
                } else {
                    res.unsupported(lineNo, trunc(s), "getBy* locator without a recognised action");
                }
                continue;
            }
            // expect(...).matcher(args)
            Matcher em = EXPECT.matcher(s);
            if (em.find()) {
                String line = emitExpect(em.group(1), em.group(2).toLowerCase(Locale.ROOT), argsAfter(s, em.end() - 1), s, lineNo, res);
                if (line != null) { out.append("  ").append(line).append("\n"); actions++; }
                continue;
            }
            // page.method(args)
            Matcher pm = PAGE_CALL.matcher(s);
            if (pm.find()) {
                String method = pm.group(1).toLowerCase(Locale.ROOT);
                String argsRaw = argsAfter(s, pm.end() - 1);
                List<String> a = splitArgs(argsRaw);
                if (method.equals("goto")) {
                    if (!a.isEmpty()) {
                        // Δ163a: an imported URL goes through the recorder's gotoUrl check ('$' literal; quotes, spaces, other schemes refused)
                        String g = PickLocator.gotoUrl(unquote(a.get(0)));
                        if (g != null) out.append("  goto ").append(g).append("\n");
                        else out.append("  ").append(refused("goto", "its URL is not a plain http(s)/file URL", s, lineNo, res)).append("\n");
                        actions++;
                    }
                    continue;
                }
                if (method.equals("waitfortimeout")) {
                    long ms = parseLong(a.isEmpty() ? "0" : a.get(0), 0);
                    out.append("  sleep ").append(Math.max(1, ms)).append("\n"); actions++; // seleasy sleep = ms
                    continue;
                }
                if (method.equals("goback") || method.equals("goforward") || method.equals("reload")) {
                    out.append("  ").append(method.equals("goback") ? "back" : method.equals("goforward") ? "forward" : "refresh")
                            .append("\n"); actions++;
                    continue;
                }
                if (method.equals("waitforselector") || method.equals("waitfor")) {
                    // Δ145c: `??` waits until found, `??+` until no longer found (was inverted)
                    if (!a.isEmpty()) { out.append("  ").append(waitPrefix(a.size() > 1 ? joinFrom(a, 1) : "")).append(" ").append(pwSelector(unquote(a.get(0)))).append("\n"); actions++; }
                    continue;
                }
                if (method.equals("waitforloadstate") || method.equals("waitfornavigation")) {
                    out.append("  sleep 1000\n"); actions++; // ms
                    continue;
                }
                // page.click/fill/type/selectOption/check/hover/... with a selector as arg 0
                if (!a.isEmpty()) {
                    sel = pwSelector(unquote(a.get(0)));
                    String args = a.size() > 1 ? joinFrom(a, 1) : "";
                    String line = emitAction(method, sel, args, s, lineNo, res);
                    if (line != null) { out.append("  ").append(line).append("\n"); actions++; continue; }
                }
                res.unsupported(lineNo, trunc(s), "unrecognised page." + method + "(...)");
                out.append("  // UNSUPPORTED: ").append(trunc(s)).append("\n");
                continue;
            }
            // Anything else inside a test body that isn't a comment/brace — note it.
            if (!s.equals("{") && !s.equals("}") && !s.startsWith("test") && !s.startsWith("await Promise")) {
                res.unsupported(lineNo, trunc(s), "not a page action or expect assertion");
            }
        }
        return actions;
    }

    // Map a Playwright action verb (+ selector + remaining args) to a seleasy line, or null if unsupported.
    private String emitAction(String method, String selector, String args, String stmt, int lineNo, ImportResult res) {
        List<String> a = splitArgs(args);
        // Δ163a: imported text reaches a step only through the Δ148 gates (LocatorRanking.unsafe / PickLocator.stepValue)
        if (selector != null && LocatorRanking.unsafe(selector)) return refused(method, UNSAFE_SELECTOR, stmt, lineNo, res);
        if (method.equals("click")) return "click " + selector;
        if (method.equals("dblclick")) return "doubleclick " + selector;
        if (method.equals("hover")) return "hover " + selector;
        if (method.equals("check") || method.equals("uncheck")) return "click " + selector;
        if (method.equals("fill") || method.equals("type")) {
            String v = PickLocator.stepValue(a.isEmpty() ? "" : unquote(a.get(0)));
            return v == null ? refused(method, UNSAFE_VALUE, stmt, lineNo, res) : "type \"" + v + "\" " + selector;
        }
        if (method.equals("press")) {
            String key = PickLocator.stepValue(a.isEmpty() ? "" : unquote(a.get(0)));
            return key == null ? refused(method, UNSAFE_VALUE, stmt, lineNo, res) : "sendkeys \"" + key + "\" " + selector;
        }
        if (method.equals("selectoption")) {
            if (a.isEmpty()) return "select first@0 " + selector;
            String arg = a.get(0).trim();
            // {label:'x'} → text, {index:n} → index, {value:'x'} or 'x' → value
            Matcher lbl = Pattern.compile("label\\s*:\\s*([\"'`])(.*?)\\1").matcher(arg);
            Matcher idx = Pattern.compile("index\\s*:\\s*(\\d+)").matcher(arg);
            Matcher val = Pattern.compile("value\\s*:\\s*([\"'`])(.*?)\\1").matcher(arg);
            boolean hasLbl = lbl.find(), hasIdx = idx.find(), hasVal = val.find();
            if (!hasLbl && hasIdx) return "select index@" + idx.group(1) + " " + selector;
            String kind = hasLbl ? "text" : "value";
            String v = PickLocator.stepValue(hasLbl ? lbl.group(2) : hasVal ? val.group(2) : unquote(arg));   // Δ163a
            return v == null ? refused(method, UNSAFE_VALUE, stmt, lineNo, res) : "select " + kind + "@\"" + v + "\" " + selector;
        }
        if (method.equals("setinputfiles")) {
            String f = PickLocator.stepValue(a.isEmpty() ? "" : unquote(a.get(0)));
            return f == null ? refused(method, UNSAFE_VALUE, stmt, lineNo, res) : "upload \"" + f + "\" " + selector;
        }
        if (method.equals("clear")) return "clear " + selector;
        if (method.equals("scrollintoviewifneeded")) return "scroll " + selector;
        if (method.equals("waitfor")) return waitPrefix(args) + " " + selector;
        res.unsupported(lineNo, trunc(stmt), "unrecognised action '" + method + "'");
        return null;
    }

    // Map a Playwright expect(...).matcher(...) to a seleasy assertion, or a reported best-effort.
    private String emitExpect(String subject, String matcher, String args, String stmt, int lineNo, ImportResult res) {
        String sel = subjectSelector(subject);
        List<String> a = splitArgs(args);
        if (sel != null && LocatorRanking.unsafe(sel)) return refused(matcher, UNSAFE_SELECTOR, stmt, lineNo, res);   // Δ163a
        // Δ145c: `??` = wait until found, `??+` = until no longer found (REFERENCE.md); both were inverted
        if (matcher.equals("tobevisible") || matcher.equals("tobeattached")) {
            return sel != null ? "?? " + sel : reportExpect(subject, matcher, lineNo, res);
        }
        // Δ163o: enabled / disabled are states of a FOUND element: `??+` (wait until not found) never passed for a
        // disabled-but-rendered element; `?? sel enabled` only checked presence before
        if (matcher.equals("tobeenabled") || matcher.equals("tobedisabled")) {
            return sel != null ? "?? " + sel + (matcher.equals("tobedisabled") ? " enabled =\"false\"" : " enabled") : reportExpect(subject, matcher, lineNo, res);
        }
        if (matcher.equals("tohavetext") && sel != null && !a.isEmpty()) {
            String t = PickLocator.stepValue(unquote(a.get(0)));   // Δ163a
            return t == null ? refused(matcher, UNSAFE_VALUE, stmt, lineNo, res) : "?? " + sel + " text =\"" + t + "\"";   // exact text of the subject
        }
        if (matcher.equals("tohavetext") || matcher.equals("tocontaintext")) {
            String t = a.isEmpty() ? "" : unquote(a.get(0));
            if (LocatorRanking.unsafe(t) || t.contains("\"")) return refused(matcher, UNSAFE_VALUE, stmt, lineNo, res);   // Δ163a: a locator
            return "?? text@\"" + escSel(t) + "\"";
        }
        if (matcher.equals("tohavevalue") && sel != null) {
            String v = PickLocator.stepValue(a.isEmpty() ? "" : unquote(a.get(0)));   // Δ163a
            return v == null ? refused(matcher, UNSAFE_VALUE, stmt, lineNo, res) : "?? " + sel + " attr@value =\"" + v + "\"";   // getAttribute("value") = live value
        }
        if (matcher.equals("tobechecked") && sel != null) {
            return "?? " + sel + " attr@checked =\"true\"";
        }
        if (matcher.equals("tobehidden")) {
            return sel != null ? "??+ " + sel : reportExpect(subject, matcher, lineNo, res);
        }
        // toHaveURL / toHaveTitle / toHaveValue / toHavecount etc. have no clean 1-line seleasy equivalent.
        res.unsupported(lineNo, trunc(stmt), "assertion '" + matcher + "' has no direct seleasy equivalent");
        return "// UNSUPPORTED assert: " + trunc(stmt);
    }

    /** Δ145c: waitFor / waitForSelector with {@code state: 'detached' | 'hidden'} waits until gone ({@code ??+}); otherwise until found. */
    static String waitPrefix(String args) {
        return args != null && Pattern.compile("state\\s*:\\s*[\"'`](?:detached|hidden)[\"'`]").matcher(args).find() ? "??+" : "??";
    }

    private String reportExpect(String subject, String matcher, int lineNo, ImportResult res) {
        res.unsupported(lineNo, matcher + "(" + trunc(subject) + ")", "could not resolve the asserted element");
        return "// UNSUPPORTED assert: " + matcher;
    }

    // expect(page.locator('x')) or expect(page.getByRole(...)) → seleasy selector; expect(page) → null.
    private String subjectSelector(String subject) {
        Matcher lm = Pattern.compile("locator\\s*\\(\\s*([\"'`])(.*?)\\1").matcher(subject);
        if (lm.find()) return pwSelector(lm.group(2));
        Matcher gm = Pattern.compile("(getBy[A-Za-z]+)\\s*\\(\\s*([\"'`])(.*?)\\2").matcher(subject);
        if (gm.find()) return getBySelector(gm.group(1), gm.group(3), subject, 0, null);
        return null;
    }

    // ---- selector translation --------------------------------------------------------------------

    // Playwright selector string → seleasy selector (id@/css@/xpath@/text@).
    String pwSelector(String raw) {
        if (raw == null) return "css@\"\"";
        String s = raw.trim();
        if (s.startsWith("xpath=")) return xp(s.substring(6));
        if (s.startsWith("//") || s.startsWith("(//")) return xp(s);
        if (s.startsWith("text=")) return "text@" + q(stripQuotes(s.substring(5)));
        if (s.startsWith("css=")) s = s.substring(4);
        if (s.startsWith("id=")) return "id@" + q(s.substring(3));
        // pure #id (no combinators/spaces) → id@
        if (s.matches("#[A-Za-z][\\w-]*")) return "id@" + s.substring(1);
        return "css@" + q(s);
    }

    // getByRole/getByText/getByLabel/getByPlaceholder/getByTestId → best-effort seleasy selector.
    private String getBySelector(String fn, String value, String stmt, int lineNo, ImportResult res) {
        String f = fn.toLowerCase(Locale.ROOT);
        if (f.equals("getbytext")) return "text@" + q(value);
        if (f.equals("getbyrole")) return xp("//*[@role='" + value + "' or self::" + value + "]");
        if (f.equals("getbylabel"))
            return xp("//label[contains(.,'" + value + "')]//input | //input[@aria-label='" + value + "']");
        if (f.equals("getbyplaceholder")) return xp("//*[@placeholder='" + value + "']");
        if (f.equals("getbytestid")) return xp("//*[@data-testid='" + value + "']");
        if (f.equals("getbyalttext")) return xp("//*[@alt='" + value + "']");
        if (f.equals("getbytitle")) return xp("//*[@title='" + value + "']");
        if (res != null) res.unsupported(lineNo, fn + "('" + value + "')", "approximated as a best-effort selector");
        return xp("//*[contains(.,'" + value + "')]");
    }

    // ---- small helpers ---------------------------------------------------------------------------

    // quote a selector value, choosing a wrapper that avoids collision with inner quotes
    private String q(String v) {
        if (v.indexOf('"') >= 0 && v.indexOf('\'') < 0) return "'" + v + "'";
        return "\"" + v.replace("\"", "\\\"") + "\"";
    }

    private String xp(String x) {
        String t = x.trim();
        if (t.indexOf('"') >= 0 && t.indexOf('\'') < 0) return "xpath@'" + t + "'";
        return "xpath@\"" + t.replace("\"", "\\\"") + "\"";
    }

    private String stripQuotes(String s) {
        String t = s.trim();
        if (t.length() >= 2 && (t.charAt(0) == '"' || t.charAt(0) == '\'' || t.charAt(0) == '`')
                && t.charAt(t.length() - 1) == t.charAt(0)) {
            return t.substring(1, t.length() - 1);
        }
        return t;
    }

    private String unquote(String s) {
        return stripQuotes(s);
    }

    // arguments substring starting just after an open-paren index, up to the matching close-paren
    private String argsAfter(String s, int openParenIdx) {
        int close = matchParen(s, openParenIdx);
        if (close < 0) close = s.length();
        return s.substring(openParenIdx + 1, close);
    }

    // split a call's argument list on top-level commas (respecting quotes/braces/brackets)
    private List<String> splitArgs(String args) {
        List<String> out = new ArrayList<String>();
        if (args == null) return out;
        int depth = 0;
        char quote = 0;
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < args.length(); i++) {
            char c = args.charAt(i);
            if (quote != 0) {
                cur.append(c);
                if (c == quote && (i == 0 || args.charAt(i - 1) != '\\')) quote = 0;
                continue;
            }
            if (c == '"' || c == '\'' || c == '`') { quote = c; cur.append(c); continue; }
            if (c == '(' || c == '{' || c == '[') { depth++; cur.append(c); continue; }
            if (c == ')' || c == '}' || c == ']') { depth--; cur.append(c); continue; }
            if (c == ',' && depth == 0) { out.add(cur.toString().trim()); cur.setLength(0); continue; }
            cur.append(c);
        }
        if (cur.length() > 0) out.add(cur.toString().trim());
        return out;
    }

    private String joinFrom(List<String> a, int from) {
        StringBuilder b = new StringBuilder();
        for (int i = from; i < a.size(); i++) {
            if (b.length() > 0) b.append(", ");
            b.append(a.get(i));
        }
        return b.toString();
    }

    private int matchBrace(String s, int open) {
        int depth = 0;
        char quote = 0;
        for (int i = open; i < s.length(); i++) {
            char c = s.charAt(i);
            if (quote != 0) {
                if (c == quote && s.charAt(i - 1) != '\\') quote = 0;
                continue;
            }
            if (c == '"' || c == '\'' || c == '`') { quote = c; continue; }
            if (c == '{') depth++;
            else if (c == '}') { depth--; if (depth == 0) return i; }
        }
        return s.length();
    }

    private int matchParen(String s, int open) {
        int depth = 0;
        char quote = 0;
        for (int i = open; i < s.length(); i++) {
            char c = s.charAt(i);
            if (quote != 0) {
                if (c == quote && s.charAt(i - 1) != '\\') quote = 0;
                continue;
            }
            if (c == '"' || c == '\'' || c == '`') { quote = c; continue; }
            if (c == '(') depth++;
            else if (c == ')') { depth--; if (depth == 0) return i; }
        }
        return -1;
    }

    private int lineNumberAt(String s, int idx) {
        int n = 1;
        for (int i = 0; i < idx && i < s.length(); i++) if (s.charAt(i) == '\n') n++;
        return n;
    }

    private long parseLong(String s, long def) {
        try {
            return Long.parseLong(s.trim().replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return def;
        }
    }

    static final String UNSAFE_SELECTOR = "its selector has '$', '#' before a letter or a control character (seleasy would template it)";
    static final String UNSAFE_VALUE = "its value cannot be carried literally (']]#', '$v{', '$p{' or a quote in a text locator)";

    /** Δ163a: the fixed comment written instead of an imported step whose text cannot be carried safely; reported too. */
    private String refused(String what, String why, String stmt, int lineNo, ImportResult res) {
        res.unsupported(lineNo, trunc(stmt), "'" + what + "' not converted: " + why);
        return "// UNSUPPORTED: '" + what + "' not converted: " + why;
    }

    private String escSel(String v) {
        return v == null ? "" : v.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String trunc(String s) {
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() > 120 ? t.substring(0, 117) + "..." : t;
    }

    private String safeName(String s) {
        if (s == null || s.trim().isEmpty()) return "imported";
        return s.trim().replaceAll("[^A-Za-z0-9_-]", "_");
    }
}
