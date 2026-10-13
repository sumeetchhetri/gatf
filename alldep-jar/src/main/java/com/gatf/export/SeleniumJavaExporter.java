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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.gatf.export.SelScript.Assertion;
import com.gatf.export.SelScript.Kind;
import com.gatf.export.SelScript.Locator;
import com.gatf.export.SelScript.Step;
import com.gatf.selenium.SemanticLocators;

/**
 * Δ161 — the Selenium WebDriver (Java, JUnit 5) backend: one test class per script, needing only
 * {@code selenium-java} and {@code junit-jupiter}. seleasy runs on Selenium, so most statements map to the same
 * calls the transpiler makes:
 *
 * <ul>
 * <li>{@code id/name/class/tag/css/xpath/linktext/partiallinktext@} → the same {@code By} strategies; plain
 * {@code text@x} → seleasy's {@code //*[contains(text(), 'x')]}. Semantic locators ({@code role@ label@ placeholder@
 * testid@ alt@ title@}, {@code text@=x}, {@code text@/re/}) run seleasy's own resolver script
 * ({@link SemanticLocators#RESOLVER_JS}, embedded as {@code SEMANTIC_JS}) through {@code executeScript}, so they match
 * exactly what seleasy matches. Every locator acts on its first match; with {@link ExportOptions#strictLocators} a
 * semantic locator matching more than one element fails, as seleasy's {@code strictLocators}.</li>
 * <li>Waits poll the find expression up to the element timeout (seleasy's default 60 s, then each {@code timeout}
 * statement; {@code ??N} for one line). Actions first wait for the element to be displayed / enabled, an
 * approximation of seleasy's actionability probe; {@code nowait} skips that.</li>
 * <li>{@code ${name}} → {@code env("GATF_NAME")} (a helper over {@code System.getenv} that fails when the variable is
 * unset); {@code $v{name}} → {@code v("name")}. Values are never resolved; the report lists names only.</li>
 * <li>Subtests, funcs and hooks become private methods; afterAll runs in a {@code finally}. Soft assertions
 * ({@code ??~}, {@code warn}) are collected and fail the test in {@code @AfterEach}.</li>
 * <li>Every unsupported statement is emitted as {@code // TODO(gatf-export): <line>} and listed in the report.
 * Shared decisions come from {@link ExportCatalog}, with this backend's overrides
 * ({@code ExportCatalog.get("selenium-java", keyword)}).</li>
 * </ul>
 */
public final class SeleniumJavaExporter implements Exporter {

    public static final String ID = "selenium-java";
    public static final String TODO = PlaywrightExporter.TODO;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String fileName(String scriptName) {
        return Jv.className(scriptName) + ".java";
    }

    @Override
    public ExportResult export(SelScript script, ExportOptions options) {
        return new Run(script, options == null ? new ExportOptions() : options).run();
    }

    /** A statement part that cannot be expressed; the whole statement becomes a TODO with this reason. */
    private static final class Unmappable extends RuntimeException {
        private static final long serialVersionUID = 1L;

        Unmappable(String reason) {
            super(reason, null, false, false);
        }
    }

    private static final class Def {
        final Step step;
        final String fn;
        final boolean takesArgs;

        Def(Step step, String fn, boolean takesArgs) {
            this.step = step;
            this.fn = fn;
            this.takesArgs = takesArgs;
        }
    }

    /**
     * The parser's reasons are worded for the first backend (Playwright); these say what Selenium would need. Keyed by
     * catalog keyword; any other reason naming Playwright is reworded generically.
     */
    private static final Map<String, String> REASONS = new HashMap<String, String>();

    static {
        REASONS.put("frame", "frame switching (driver.switchTo().frame(...) in Selenium; not exported yet)");
        REASONS.put("tab", "tab switching (driver.switchTo().window(...) in Selenium; not exported yet)");
        REASONS.put("window", "window switching (driver.switchTo().window(...) in Selenium; not exported yet)");
        REASONS.put("alert", "dialog handling (driver.switchTo().alert() in Selenium; not exported yet)");
        REASONS.put("confirm", "dialog handling (driver.switchTo().alert() in Selenium; not exported yet)");
        REASONS.put("netapix", "network API capture (needs CDP or BiDi network events in Selenium)");
        REASONS.put("a11y", "accessibility check (needs axe-core injected with executeScript)");
        REASONS.put("execjsfile", "JavaScript from a file (read it and pass it to executeScript)");
        REASONS.put("clock", ExportCatalog.get(ID, "clock").note);
        REASONS.put("state", ExportCatalog.get(ID, "state").note);
    }

    private static final class Run {
        final SelScript sc;
        final ExportOptions opt;
        final ExportResult r = new ExportResult();
        final String cls;
        boolean usesVars, usesEnv, usesSoft;
        final Set<String> helpers = new LinkedHashSet<String>();
        final Set<String> imports = new TreeSet<String>();
        final Map<String, String> envOwner = new HashMap<String, String>();   // env name → first ${ref}
        final Map<String, Def> defs = new LinkedHashMap<String, Def>();      // lower-case subtest / func name
        final Map<String, Def> hooks = new LinkedHashMap<String, Def>();
        final Set<String> idents = new HashSet<String>();
        final Set<String> argsTargets = new HashSet<String>();
        final Set<String> notedOnce = new HashSet<String>();
        String browser = "chrome";
        boolean headless, anyOnly;
        int opens, statements, exported;

        Run(SelScript sc, ExportOptions opt) {
            this.sc = sc;
            this.opt = opt;
            this.cls = Jv.className(sc.name);
            r.scriptName = sc.name;
            r.fileName = cls + ".java";
            idents.add("test");
        }

        ExportResult run() {
            collectCalls(sc.steps);
            for (Step s : sc.steps) {
                if (s.kind == Kind.SUBTEST && "only".equals(s.args.get("marker"))) anyOnly = true;
            }
            for (Step s : sc.steps) {
                if (s.kind == Kind.HOOK) {
                    if (hooks.containsKey(s.text)) s.unsupported("a second " + s.text + " hook (one of each kind per script)");
                    else hooks.put(s.text, new Def(s, ident("hook_", s.text), false));
                } else if (s.kind == Kind.SUBTEST || s.kind == Kind.FUNC) {
                    String key = s.text.toLowerCase(Locale.ROOT);
                    if (defs.containsKey(key)) s.unsupported("a second subtest/func named '" + s.text + "' (the first one is exported)");
                    else defs.put(key, new Def(s, ident(s.kind == Kind.FUNC ? "fn_" : "st_", s.text), !s.list.isEmpty() || argsTargets.contains(key)));
                }
            }

            List<Step> main = new ArrayList<Step>();
            for (Step s : sc.steps) {
                if (s.kind == Kind.HOOK || ((s.kind == Kind.SUBTEST || s.kind == Kind.FUNC) && isDef(s))) continue;
                main.add(s);
            }
            Def ba = hooks.get("beforeAll"), aa = hooks.get("afterAll");
            StringBuilder mainOut = new StringBuilder();
            int ind = aa != null ? 3 : 2;
            if (ba != null) line(mainOut, ind, ba.fn + "();");
            seq(main, mainOut, ind);

            StringBuilder defsOut = new StringBuilder();
            for (Def h : hooks.values()) renderHook(h, defsOut);
            for (Def d : defs.values()) renderDef(d, defsOut);

            StringBuilder runtime = runtime();

            int todos = r.items(ExportResult.Level.UNSUPPORTED).size();
            int notes = r.items(ExportResult.Level.NOTE).size();
            StringBuilder out = new StringBuilder();
            out.append("// Exported from ").append(Jv.comment(sc.name)).append(".sel by GATF (gatf export selenium-java). Review every TODO(gatf-export) line.\n");
            out.append("// ").append(exported).append(" of ").append(statements).append(" statement(s) exported, ").append(todos)
                    .append(" TODO, ").append(notes).append(" note(s): see the export report.\n");
            if (!r.env.isEmpty()) {
                out.append("// Environment variables (names only):");
                for (Map.Entry<String, String> e : r.env.entrySet()) out.append(" ${").append(Jv.comment(e.getKey())).append("} -> ").append(e.getValue()).append(';');
                out.setLength(out.length() - 1);
                out.append('\n');
            }
            out.append("// Needs selenium-java and junit-jupiter. SELENIUM_REMOTE_URL runs it on a Selenium Grid; -Dgatf.export.browser.binary and\n")
                    .append("// -Dgatf.export.browser.args (comma-separated, e.g. --headless=new) adjust the local browser.\n");
            imports.add("org.junit.jupiter.api.AfterEach");
            imports.add("org.junit.jupiter.api.BeforeEach");
            imports.add("org.junit.jupiter.api.DisplayName");
            imports.add("org.junit.jupiter.api.Test");
            imports.add("org.openqa.selenium.WebDriver");
            imports.add("java.time.Duration");
            String group = null;
            for (String i : imports) {
                String g = i.startsWith("static ") ? "static" : i.substring(0, i.indexOf('.'));
                if (!g.equals(group)) { out.append(group == null ? "" : "\n"); group = g; }
                out.append("import ").append(i).append(";\n");
            }
            out.append('\n');
            out.append("class ").append(cls).append(" {\n\n");
            out.append("    private WebDriver driver;\n");
            out.append("    /** The element timeout: seleasy's default, then each 'timeout' statement. */\n");
            out.append("    private Duration timeout = Duration.ofSeconds(60);\n");
            if (usesVars) out.append("    private final Map<String, String> vars = new HashMap<>();\n");
            if (usesSoft) out.append("    private final List<String> softFailures = new ArrayList<>();\n");
            out.append('\n');
            out.append("    @BeforeEach\n    void openBrowser() throws Exception {\n        driver = newDriver();\n    }\n\n");
            out.append("    @AfterEach\n    void closeBrowser() {\n");
            out.append("        try {\n            if (driver != null) driver.quit();\n        } finally {\n            driver = null;\n        }\n");
            if (usesSoft) out.append("        if (!softFailures.isEmpty()) fail(softFailures.size() + \" soft failure(s):\\n\" + String.join(\"\\n\", softFailures));\n");
            out.append("    }\n\n");
            out.append("    @Test\n    @DisplayName(").append(Jv.str(sc.name)).append(")\n    void test() throws Exception {\n");
            if (aa != null) {
                out.append("        try {\n").append(mainOut).append("        } finally {\n").append("            ").append(aa.fn).append("();\n").append("        }\n");
            } else {
                out.append(mainOut);
            }
            out.append("    }\n");
            out.append(defsOut);
            out.append(runtime);
            out.append("}\n");
            r.content = out.toString();
            r.stats.put("statements", statements);
            r.stats.put("exported", exported);
            r.stats.put("todo", todos);
            r.stats.put("notes", notes);
            return r;
        }

        private boolean isDef(Step s) {
            Def d = defs.get(s.text.toLowerCase(Locale.ROOT));
            return d != null && d.step == s;
        }

        private void collectCalls(List<Step> steps) {
            for (Step s : steps) {
                if (s.kind == Kind.CALL && s.text != null) {
                    for (String k : s.args.keySet()) if (k.startsWith("arg.")) argsTargets.add(s.text.toLowerCase(Locale.ROOT));
                }
                collectCalls(s.children);
            }
        }

        private String ident(String prefix, String text) {
            String base = Jv.ident(prefix, text);
            String id = base;
            for (int i = 2; !idents.add(id); i++) id = base + "_" + i;
            return id;
        }

        // ---- output helpers -----------------------------------------------------------------------------------------

        private static void line(StringBuilder b, int ind, String text) {
            for (int i = 0; i < ind; i++) b.append("    ");
            b.append(text).append('\n');
        }

        private String src(int lineNo) {
            return SelParser.trim(sc.line(lineNo));
        }

        private void note(int lineNo, String construct, String reason) {
            r.note(lineNo, Jv.comment(construct), reason);
        }

        /** A note that applies to the whole script: listed once, at its first line. */
        private void noteOnce(String key, int lineNo, String reason) {
            if (notedOnce.add(key)) note(lineNo, src(lineNo), reason);
        }

        private void use(String... names) {
            for (String n : names) helpers.add(n);
        }

        // ---- unsupported --------------------------------------------------------------------------------------------

        private static String reason(Step s) {
            String why = s.reason == null ? "" : s.reason;
            String k = s.keyword;
            if (k != null && REASONS.containsKey(k)) return REASONS.get(k);
            if (!why.contains("Playwright")) return why;
            if ("emulate".equals(k)) return "emulate: " + ExportCatalog.get(ID, "emulate").note;
            if (why.contains(" has no Playwright equivalent")) return why.replace(" has no Playwright equivalent", " is not exported (port it by hand)");
            return why.replace("Playwright", "Selenium");
        }

        private void todo(Step s, StringBuilder b, int ind, String reason) {
            for (int n = s.line; n <= s.endLine; n++) {
                String t = src(n);
                if (!t.isEmpty()) line(b, ind, "// " + TODO + Jv.comment(t));
            }
            r.unsupported(s.line, Jv.comment(src(s.line)), reason);
            listInside(s.children, s.line);
        }

        private void listInside(List<Step> children, int header) {
            for (Step c : children) {
                if (c.kind != Kind.COMMENT) {
                    statements++;
                    r.unsupported(c.line, Jv.comment(src(c.line)), "inside the unsupported block at line " + header);
                }
                listInside(c.children, header);
            }
        }

        // ---- sequences and control flow -----------------------------------------------------------------------------

        private void seq(List<Step> steps, StringBuilder b, int ind) {
            for (int i = 0; i < steps.size(); i++) {
                Step s = steps.get(i);
                if (s.kind == Kind.IF || (s.kind == Kind.UNSUPPORTED && isIfKeyword(s.keyword))) {
                    List<Step> chain = new ArrayList<Step>();
                    chain.add(s);
                    while (i + 1 < steps.size() && isElseKeyword(steps.get(i + 1).keyword)) chain.add(steps.get(++i));
                    ifChain(chain, b, ind);
                    continue;
                }
                if (isElseKeyword(s.keyword)) {
                    statements++;
                    todo(s, b, ind, s.reason != null ? reason(s) : "else without a preceding if");
                    continue;
                }
                step(s, b, ind);
            }
        }

        private static boolean isIfKeyword(String k) {
            return k != null && (k.equals("?") || k.equals("?!") || k.equals("if") || k.equals("ifnot"));
        }

        private static boolean isElseKeyword(String k) {
            return k != null && (k.equals(":") || k.equals(":?") || k.equals(":?!") || k.equals("else") || k.equals("else if") || k.equals("else ifnot"));
        }

        private void ifChain(List<Step> chain, StringBuilder b, int ind) {
            String bad = null;
            List<String> conds = new ArrayList<String>();
            for (Step s : chain) {
                if (s.kind == Kind.UNSUPPORTED) { bad = reason(s); break; }
                if (s.kind == Kind.ELSE) { conds.add(null); continue; }
                try {
                    String all = "all(" + loc(s.target, s.line) + ")";
                    conds.add(s.negated ? all + ".isEmpty()" : "!" + all + ".isEmpty()");
                } catch (Unmappable u) {
                    bad = u.getMessage();
                    break;
                }
            }
            if (bad != null) {
                Step whole = new Step(Kind.UNSUPPORTED, chain.get(0).line);
                whole.endLine = chain.get(chain.size() - 1).endLine;
                for (Step s : chain) {
                    statements++;
                    whole.children.addAll(s.children);
                }
                todo(whole, b, ind, "if/else chain: " + bad);
                for (int i = 1; i < chain.size(); i++) {
                    r.unsupported(chain.get(i).line, Jv.comment(src(chain.get(i).line)), "part of the unsupported if/else chain at line " + whole.line);
                }
                return;
            }
            use("all");
            noteOnce("if-timing", chain.get(0).line, "an if condition is checked at once with findElements; seleasy may poll for it briefly first");
            for (int i = 0; i < chain.size(); i++) {
                Step s = chain.get(i);
                statements++;
                exported++;
                String head = i == 0 ? "if (" + conds.get(0) + ") {" : conds.get(i) == null ? "} else {" : "} else if (" + conds.get(i) + ") {";
                line(b, ind, head);
                seq(s.children, b, ind + 1);
            }
            line(b, ind, "}");
        }

        // ---- definitions --------------------------------------------------------------------------------------------

        private void renderHook(Def h, StringBuilder b) {
            statements++;
            exported++;
            b.append('\n');
            line(b, 1, "/** " + Jv.blockComment(h.step.text) + " (line " + h.step.line + "). */");
            line(b, 1, "private void " + h.fn + "() throws Exception {");
            seq(h.step.children, b, 2);
            line(b, 1, "}");
        }

        private void renderDef(Def d, StringBuilder b) {
            statements++;
            exported++;
            Step s = d.step;
            b.append('\n');
            String kind = s.kind == Kind.FUNC ? "func" : (s.args.containsKey("marker") ? s.args.get("marker") + " subtest" : "subtest");
            line(b, 1, "/** " + kind + " " + Jv.blockComment(s.text) + " (line " + s.line + "). */");
            line(b, 1, "private void " + d.fn + "(" + (d.takesArgs ? "Map<String, String> args" : "") + ") throws Exception {");
            if (d.takesArgs) {
                usesVars = true;
                imports.add("java.util.Map");
                imports.add("java.util.HashMap");
            }
            if (s.kind == Kind.FUNC) {
                if (d.takesArgs) line(b, 2, "vars.putAll(args);");
                seq(s.children, b, 2);
                line(b, 1, "}");
                return;
            }
            String marker = s.args.get("marker");
            boolean skip = "skip".equals(marker) || "fixme".equals(marker) || (anyOnly && !"only".equals(marker));
            if (skip) {
                String why = marker != null && !"only".equals(marker) ? marker : "another subtest is marked only";
                note(s.line, src(s.line), "skipped (" + why + "): the method returns at once; the body is kept for review");
                use("skipped");
                line(b, 2, "if (skipped(" + Jv.str(s.text) + ", " + Jv.str(why) + ")) return;");
                if (d.takesArgs) line(b, 2, "vars.putAll(args);");
                seq(s.children, b, 2);
                line(b, 1, "}");
                return;
            }
            Def be = hooks.get("beforeEach"), ae = hooks.get("afterEach");
            int bi = 2;
            if (d.takesArgs) line(b, bi, "vars.putAll(args);");
            if (ae != null) { line(b, bi, "try {"); bi++; }
            if (be != null) line(b, bi, be.fn + "();");
            seq(s.children, b, bi);
            if (ae != null) {
                line(b, bi - 1, "} finally {");
                line(b, bi, ae.fn + "();");
                line(b, bi - 1, "}");
            }
            line(b, 1, "}");
        }

        // ---- one statement ------------------------------------------------------------------------------------------

        private void step(Step s, StringBuilder b, int ind) {
            if (s.trailingComment != null) line(b, ind, "//" + Jv.comment(s.trailingComment));
            if (s.kind == Kind.COMMENT) {
                comment(s, b, ind);
                return;
            }
            statements++;
            if (s.kind == Kind.UNSUPPORTED) {
                todo(s, b, ind, reason(s));
                return;
            }
            StringBuilder one = new StringBuilder();
            int itemsBefore = r.items.size();
            Map<String, String> envBefore = new HashMap<String, String>(r.env), ownerBefore = new HashMap<String, String>(envOwner);
            Set<String> helpersBefore = new LinkedHashSet<String>(helpers), importsBefore = new TreeSet<String>(imports), notedBefore = new HashSet<String>(notedOnce);
            boolean varsBefore = usesVars, envUsedBefore = usesEnv, softBefore = usesSoft;
            int opensBefore = opens;
            try {
                statement(s, one, ind);
            } catch (Unmappable u) {
                while (r.items.size() > itemsBefore) r.items.remove(r.items.size() - 1);
                r.env.clear();
                r.env.putAll(envBefore);
                envOwner.clear();
                envOwner.putAll(ownerBefore);
                helpers.retainAll(helpersBefore);
                imports.retainAll(importsBefore);
                notedOnce.retainAll(notedBefore);
                usesVars = varsBefore;
                usesEnv = envUsedBefore;
                usesSoft = softBefore;
                opens = opensBefore;
                todo(s, b, ind, u.getMessage());
                return;
            }
            exported++;
            b.append(one);
        }

        private void comment(Step s, StringBuilder b, int ind) {
            if ("/*".equals(s.keyword) && s.text != null) {
                for (String l : SelParser.splitLines(s.text)) line(b, ind, "// " + Jv.comment(SelParser.trim(l)));
            } else if (s.text != null) {
                line(b, ind, "//" + Jv.comment(s.text));
            }
        }

        private Unmappable catalog(String keyword) {
            return new Unmappable(ExportCatalog.get(ID, keyword).note);
        }

        private void statement(Step s, StringBuilder b, int ind) {
            switch (s.kind) {
                case OPEN:
                    open(s, b, ind);
                    return;
                case MAXIMIZE:
                    line(b, ind, "driver.manage().window().maximize();");
                    return;
                case GOTO:
                    line(b, ind, "driver.get(" + tpl(s.text, s.line) + ");");
                    return;
                case BACK:
                    line(b, ind, "driver.navigate().back();");
                    return;
                case FORWARD:
                    line(b, ind, "driver.navigate().forward();");
                    return;
                case REFRESH:
                    line(b, ind, "driver.navigate().refresh();");
                    return;
                case WAITREADY:
                    use("waitReady");
                    line(b, ind, "waitReady();");
                    return;
                case SLEEP:
                    use("pause");
                    line(b, ind, "pause(" + Long.parseLong(s.text) + "L);");
                    return;
                case TIMEOUT:
                    line(b, ind, "timeout = Duration.ofSeconds(" + Long.parseLong(s.text) + ");");
                    if (s.args.containsKey("extra")) note(s.line, src(s.line), "only the element timeout is exported; '" + s.args.get("extra") + "' (if-timeout / poll granularity) is not");
                    return;
                case CLOSE:
                    line(b, ind, "driver.quit();");
                    line(b, ind, "driver = null;");
                    return;
                case SCROLL:
                    scroll(s, b, ind);
                    return;
                case ACTION:
                    action(s, b, ind);
                    return;
                case WAIT:
                    waitFor(s, b, ind);
                    return;
                case ASSERT:
                    assertion(s, b, ind);
                    return;
                case VAR:
                    line(b, ind, put(s.text, s.args.containsKey("ref") ? var(s.args.get("ref")) : tpl(s.args.get("value"), s.line)));
                    return;
                case ELEMENT_VAR:
                    elementVar(s, b, ind);
                    return;
                case JS_VAR:
                    use("js");
                    line(b, ind, put(s.text, "String.valueOf(js(" + tpl(s.args.get("script"), s.line) + "))"));
                    return;
                case EXEC_JS:
                    use("js");
                    line(b, ind, "js(" + tpl(s.text, s.line) + ");");
                    return;
                case CALL:
                    call(s, b, ind);
                    return;
                case PASS:
                    line(b, ind, "if (true) return; // pass" + (s.text == null || s.text.isEmpty() ? "" : ": " + Jv.comment(s.text)));
                    return;
                case FAIL:
                    imports.add("static org.junit.jupiter.api.Assertions.fail");
                    line(b, ind, "fail(" + tpl(s.text == null || s.text.isEmpty() ? "fail" : s.text, s.line) + ");");
                    return;
                case WARN:
                    soft();
                    line(b, ind, "softFailures.add(" + tpl(s.text == null || s.text.isEmpty() ? "warn" : s.text, s.line) + ");");
                    return;
                case SCREENSHOT:
                    use("screenshot");
                    imports.add("org.openqa.selenium.TakesScreenshot");
                    line(b, ind, "screenshot((TakesScreenshot) driver, " + shotPath(s) + ");");
                    return;
                case ELE_SCREENSHOT:
                    use("screenshot", "find");
                    line(b, ind, "screenshot(find(" + loc(s.target, s.line) + ", " + wait(s) + "), " + shotPath(s) + ");");
                    return;
                case EMULATE:
                    if (!"viewport".equals(s.verb)) throw new Unmappable("emulate " + s.verb + ": " + ExportCatalog.get(ID, "emulate").note);
                    imports.add("org.openqa.selenium.Dimension");
                    line(b, ind, "driver.manage().window().setSize(new Dimension(" + Integer.parseInt(s.args.get("width")) + ", " + Integer.parseInt(s.args.get("height")) + "));");
                    noteOnce("viewport", s.line, "emulate viewport sets the window size; the page viewport is smaller by the browser's own chrome");
                    return;
                case SCREENSHOT_COMPARE:
                    throw catalog("screenshot compare");
                case ROUTE: case ROUTE_HAR:
                    throw catalog("route");
                case UNROUTE:
                    throw catalog("unroute");
                case STATE_SAVE:
                    throw catalog("state");
                case CLOCK:
                    throw catalog("clock");
                case SUBTEST: case FUNC: case HOOK:
                    throw new Unmappable("subtests, funcs and hooks are allowed only at the top level");
                default:
                    throw new Unmappable("no Selenium mapping for " + s.kind);
            }
        }

        // ---- browser ------------------------------------------------------------------------------------------------

        private void open(Step s, StringBuilder b, int ind) {
            if (opens > 0) throw new Unmappable("a second browser session (open): one driver per exported test");
            String[] t = s.text.trim().split("[\\t ]+");
            String name = SelParser.unquote(t[0]).toLowerCase(Locale.ROOT);
            String base = name.contains("-") ? name.substring(0, name.indexOf('-')) : name;
            if (!base.matches("chrome|firefox|edge|safari")) throw new Unmappable("no Selenium driver for '" + name + "' in the export (chrome, firefox, edge, safari)");
            opens++;
            browser = base;
            headless = name.endsWith("-hdl") && !base.equals("safari");
            line(b, ind, "// gatf-export: '" + Jv.comment(src(s.line)) + "' - the driver is created by newDriver() before the test");
            note(s.line, src(s.line), "the browser is created by newDriver() (" + driverClass(base) + (headless ? ", headless" : "") + "), or on a Grid with SELENIUM_REMOTE_URL"
                    + (t.length > 1 ? "; the session name is not exported" : ""));
        }

        private static String driverClass(String b) {
            switch (b) {
                case "firefox": return "FirefoxDriver";
                case "edge": return "EdgeDriver";
                case "safari": return "SafariDriver";
                default: return "ChromeDriver";
            }
        }

        // ---- values -------------------------------------------------------------------------------------------------

        /** A seleasy value as a Java expression: literals escaped, {@code $v{x}} → v("x"), {@code ${x}} → env("GATF_X"). */
        private String tpl(String raw, int lineNo) {
            Template t = Template.parse(raw == null ? "" : raw);
            if (!t.bareReferences().isEmpty()) {
                noteOnce("bare:" + lineNo, lineNo, "Velocity reference(s) " + t.bareReferences() + " left as literal text; use ${name} or $v{name} to export them");
            }
            if (raw != null && raw.matches("(?s).*![A-Za-z_][A-Za-z0-9_.\\-]*!.*")) {
                noteOnce("dynprop:" + lineNo, lineNo, "!name! is replaced with a dynprop / global value when seleasy parses the script; left as literal text");
            }
            List<String> parts = new ArrayList<String>();
            for (Template.Part p : t.parts()) {
                switch (p.kind) {
                    case LITERAL: parts.add(Jv.str(p.text)); break;
                    case VAR: parts.add(var(p.text)); break;
                    default: parts.add(env(p.text)); break;
                }
            }
            if (parts.isEmpty()) return "\"\"";
            return String.join(" + ", parts);
        }

        /**
         * An expression ready for a member call: a composed value ({@code v("n") + " x"}) is parenthesised, because
         * {@code v("n") + " x".startsWith(a)} would call startsWith on " x" alone and concatenate a boolean.
         */
        private static String member(String expr) {
            return expr.contains(" + ") ? "(" + expr + ")" : expr;
        }

        private boolean isLiteral(String raw) {
            return Template.parse(raw == null ? "" : raw).isLiteral();
        }

        private String var(String name) {
            usesVars = true;
            use("v");
            imports.add("java.util.Map");
            imports.add("java.util.HashMap");
            return "v(" + Jv.str(name) + ")";
        }

        private String put(String name, String expr) {
            usesVars = true;
            imports.add("java.util.Map");
            imports.add("java.util.HashMap");
            return "vars.put(" + Jv.str(name) + ", " + expr + ");";
        }

        private String env(String ref) {
            String name = Template.envName(ref);
            String owner = envOwner.get(name);
            if (owner != null && !owner.equals(ref)) {
                throw new ExportException(sc.name + ".sel: ${" + owner + "} and ${" + ref + "} both map to the environment variable " + name
                        + "; rename one of them");
            }
            envOwner.put(name, ref);
            r.env.put(ref, name);
            usesEnv = true;
            use("env");
            return "env(" + Jv.str(name) + ")";
        }

        private void soft() {
            usesSoft = true;
            imports.add("java.util.ArrayList");
            imports.add("java.util.List");
            imports.add("static org.junit.jupiter.api.Assertions.fail");
        }

        private String shotPath(Step s) {
            if (s.text != null && !s.text.isEmpty()) return tpl(s.text, s.line);
            return Jv.str("target/gatf-export/" + sc.name.replaceAll("[^A-Za-z0-9._-]", "_") + "-line" + s.line + ".png");
        }

        private String wait(Step s) {
            return s.timeoutSec == null ? "timeout" : "Duration.ofSeconds(" + s.timeoutSec + ")";
        }

        // ---- locators -----------------------------------------------------------------------------------------------

        /** A {@code Loc} expression: the find expression's text plus how to find its elements. */
        private String loc(Locator l, int lineNo) {
            if (l == null) throw new Unmappable("no locator");
            if (l.unsupported != null) throw new Unmappable("locator " + l.raw + ": " + l.unsupported);
            use("Loc");
            String what = Jv.str(l.raw);
            if (l.semantic != null) return semantic(l, what, lineNo);
            String v = l.value;
            boolean lit = isLiteral(v);
            imports.add("org.openqa.selenium.By");
            switch (l.kind()) {
                case "id": return "by(" + what + ", By.id(" + tpl(v, lineNo) + "))";
                case "name": return "by(" + what + ", By.name(" + tpl(v, lineNo) + "))";
                case "class": case "classname": return "by(" + what + ", By.className(" + tpl(v, lineNo) + "))";
                case "tag": case "tagname": return "by(" + what + ", By.tagName(" + tpl(v, lineNo) + "))";
                case "xpath":
                    noteOnce("xpath-fallback", lineNo, "xpath@ uses By.xpath only; seleasy also retries an XPath the browser rejects with its own evaluator");
                    return "by(" + what + ", By.xpath(" + tpl(v, lineNo) + "))";
                case "css": case "cssselector": return "by(" + what + ", By.cssSelector(" + tpl(v, lineNo) + "))";
                case "text":
                    if (lit) return "by(" + what + ", By.xpath(" + Jv.str("//*[contains(text(), " + Jv.xpathLiteral(v) + ")]") + "))";
                    use("xpathLiteral");
                    return "by(" + what + ", By.xpath(\"//*[contains(text(), \" + xpathLiteral(" + tpl(v, lineNo) + ") + \")]\"))";
                case "linktext": return "by(" + what + ", By.linkText(" + tpl(v, lineNo) + "))";
                case "partiallinktext": return "by(" + what + ", By.partialLinkText(" + tpl(v, lineNo) + "))";
                case "active":
                    use("active");
                    return "active()";
                default: throw new Unmappable("no Selenium locator for " + l.raw);
            }
        }

        private String semantic(Locator l, String what, int lineNo) {
            use("semantic");
            Map<String, Object> sp = l.semantic;
            List<String> kv = new ArrayList<String>();
            for (Map.Entry<String, Object> e : sp.entrySet()) {
                Object val = e.getValue();
                String expr;
                if (e.getKey().equals("value")) expr = tpl(String.valueOf(val), lineNo);
                else if (val instanceof Boolean || val instanceof Integer) expr = String.valueOf(val);
                else expr = Jv.str(String.valueOf(val));
                kv.add(Jv.str(e.getKey()) + ", " + expr);
            }
            if ("testid".equals(sp.get("kind"))) {
                noteOnce("testid", lineNo, "testid@ reads TEST_ID_ATTRIBUTE (data-testid); change it if the project sets seleasy's testIdAttribute");
            }
            return "semantic(" + what + ", spec(" + String.join(", ", kv) + "))";
        }

        // ---- actions ------------------------------------------------------------------------------------------------

        /** The element an action works on: waited for, then (unless nowait) until it is actionable for {@code mode}. */
        private String target(Step s, Locator l, String mode) {
            String loc = loc(l, s.line);
            if (s.noWait) {
                use("find");
                return "find(" + loc + ", " + wait(s) + ")";
            }
            use("actionable");
            noteOnce("actionable", s.line, "before an action the element must be displayed and enabled (Selenium's elementToBeClickable); "
                    + "seleasy's actionability probe also checks that it is stable and receives the pointer");
            return "actionable(" + loc + ", " + wait(s) + ", " + Jv.str(mode) + ")";
        }

        private void action(Step s, StringBuilder b, int ind) {
            String v = s.verb;
            if (s.noWait && !v.equals("hoverclick")) noteOnce("nowait", s.line, "nowait / '!' skips the displayed / enabled wait, as seleasy skips its actionability probe");
            switch (v) {
                case "click":
                    if ("fo".equals(s.qualifier)) {
                        use("focus");
                        line(b, ind, "focus(" + target(s, s.target, "click") + ").click();");
                    } else {
                        line(b, ind, target(s, s.target, "click") + ".click();");
                    }
                    return;
                case "dblclick":
                    imports.add("org.openqa.selenium.interactions.Actions");
                    line(b, ind, "new Actions(driver).moveToElement(" + target(s, s.target, "dblclick") + ").doubleClick().perform();");
                    return;
                case "hover":
                    imports.add("org.openqa.selenium.interactions.Actions");
                    line(b, ind, "new Actions(driver).moveToElement(" + target(s, s.target, "hover") + ").perform();");
                    return;
                case "clear":
                    line(b, ind, target(s, s.target, "clear") + ".clear();");
                    return;
                case "submit":
                    line(b, ind, target(s, s.target, "submit") + ".submit();");
                    return;
                case "type": case "chord": {
                    String text;
                    if (v.equals("chord")) {
                        String decoded;
                        try {
                            decoded = Jv.unescapeJava(s.text);
                        } catch (IllegalArgumentException e) {
                            throw new Unmappable("chord value: " + e.getMessage() + " (seleasy types nothing)");
                        }
                        imports.add("org.openqa.selenium.Keys");
                        text = "Keys.chord(" + Jv.str(decoded) + ")";
                        if (!decoded.equals(s.text)) note(s.line, src(s.line), "chord value decoded as seleasy does (Java escapes); Selenium Keys characters (U+E000..) are sent as keys");
                    } else {
                        text = tpl(s.text, s.line);
                        if (s.text != null && s.text.startsWith("file://")) note(s.line, src(s.line), "seleasy resolves a file:// value to a test file path before typing; typed as written");
                    }
                    use("type");
                    line(b, ind, "type(" + target(s, s.target, "type") + ", " + text + ", " + Jv.str(s.qualifier) + ");");
                    return;
                }
                case "select": {
                    imports.add("org.openqa.selenium.support.ui.Select");
                    String sel = "new Select(" + target(s, s.target, "select") + ")";
                    String mode = s.args.get("mode");
                    switch (mode) {
                        case "text": line(b, ind, sel + ".selectByVisibleText(" + tpl(s.text, s.line) + ");"); break;
                        case "value": line(b, ind, sel + ".selectByValue(" + tpl(s.text, s.line) + ");"); break;
                        case "index": line(b, ind, sel + ".selectByIndex(" + Integer.parseInt(s.text) + ");"); break;
                        case "first": line(b, ind, sel + ".selectByIndex(0);"); break;
                        default:
                            use("selectLast");
                            line(b, ind, "selectLast(" + sel + ");");
                            break;
                    }
                    return;
                }
                case "upload":
                    noteOnce("paths", s.line, "file paths are resolved from the working directory of the test run, not the script's folder");
                    imports.add("java.io.File");
                    line(b, ind, target(s, s.target, "upload") + ".sendKeys(new File(" + tpl(s.text, s.line) + ").getAbsolutePath());");
                    return;
                case "hoverclick": {
                    if (s.noWait) note(s.line, src(s.line), "nowait has no effect on hoverclick: seleasy waits for both elements itself (Δ145c)");
                    imports.add("org.openqa.selenium.interactions.Actions");
                    use("find", "actionable");
                    String hover = "actionable(" + loc(s.target, s.line) + ", " + wait(s) + ", \"hover\")";
                    String click = "find(" + loc(s.target2, s.line) + ", " + wait(s) + ")";
                    line(b, ind, "new Actions(driver).moveToElement(" + hover + ").click(" + click + ").perform();");
                    noteOnce("hoverclick", s.line, "hoverclick finds the click target before hovering (it may be hidden until then), as seleasy does");
                    return;
                }
                case "drag": {
                    imports.add("org.openqa.selenium.interactions.Actions");
                    use("find");
                    String from = target(s, s.target, "drag"), to = "find(" + loc(s.target2, s.line) + ", " + wait(s) + ")";
                    if (s.args.get("gesture").endsWith("1")) {
                        line(b, ind, "new Actions(driver).dragAndDrop(" + from + ", " + to + ").perform();");
                    } else {
                        use("drag");
                        line(b, ind, "drag(" + from + ", " + to + ");");
                    }
                    return;
                }
                default:
                    throw new Unmappable("no Selenium mapping for action " + v);
            }
        }

        // ---- waits and assertions -----------------------------------------------------------------------------------

        private void waitFor(Step s, StringBuilder b, int ind) {
            if (s.args.containsKey("nowaitNote")) note(s.line, src(s.line), "nowait on a wait or assertion has no effect; it keeps its retry");
            String call;
            if ("gone".equals(s.verb)) {
                use("gone");
                call = "gone(" + loc(s.target, s.line) + ", " + wait(s) + ")";
            } else {
                use("find");
                call = "find(" + loc(s.target, s.line) + ", " + wait(s) + ")";
            }
            noteOnce("wait-direction", s.line, "??+ / wait till visible wait until the element is no longer found, ??- / ?? / wait for until it is found; "
                    + "neither checks visibility (as seleasy)");
            if (s.soft) {
                soft();
                use("soft");
                line(b, ind, "soft(() -> " + call + ");");
            } else {
                line(b, ind, call + ";");
            }
        }

        private void assertion(Step s, StringBuilder b, int ind) {
            Assertion a = s.assertion;
            if (s.args.containsKey("nowaitNote")) note(s.line, src(s.line), "nowait on a wait or assertion has no effect; it keeps its retry");
            noteOnce("web-first", s.line, "assertions re-read the value until the element timeout passes it (web-first); seleasy re-checks only with webFirstAssertions");
            String getter;
            String p = a.property;
            if (a.pageLevel) {
                switch (p) {
                    case "title": getter = "driver.getTitle()"; break;
                    case "currenturl": getter = "driver.getCurrentUrl()"; break;
                    case "pagesource": getter = "driver.getPageSource()"; break;
                    case "width": getter = "String.valueOf(driver.manage().window().getSize().getWidth())"; break;
                    case "height": getter = "String.valueOf(driver.manage().window().getSize().getHeight())"; break;
                    case "xpos": getter = "String.valueOf(driver.manage().window().getPosition().getX())"; break;
                    case "ypos": getter = "String.valueOf(driver.manage().window().getPosition().getY())"; break;
                    case "alerttext": getter = "driver.switchTo().alert().getText()"; break;
                    default: throw new Unmappable("page property '" + p + "' has no Selenium mapping");
                }
            } else {
                use("now");
                String el = "now(" + loc(s.target, s.line) + ")";
                switch (p) {
                    case "text": getter = el + ".getText()"; break;
                    case "tagname": getter = el + ".getTagName()"; break;
                    case "attr": getter = el + ".getAttribute(" + Jv.str(a.name) + ")"; break;
                    case "cssvalue": getter = el + ".getCssValue(" + Jv.str(a.name) + ")"; break;
                    case "visible": getter = "String.valueOf(" + el + ".isDisplayed())"; break;
                    case "enabled": getter = "String.valueOf(" + el + ".isEnabled())"; break;
                    case "selected": getter = "String.valueOf(" + el + ".isSelected())"; break;
                    case "width": getter = "String.valueOf(" + el + ".getSize().getWidth())"; break;
                    case "height": getter = "String.valueOf(" + el + ".getSize().getHeight())"; break;
                    case "xpos": getter = "String.valueOf(" + el + ".getLocation().getX())"; break;
                    case "ypos": getter = "String.valueOf(" + el + ".getLocation().getY())"; break;
                    default: throw new Unmappable("element property '" + p + "' has no Selenium mapping");
                }
            }
            String cmp;
            if (a.values != null) {
                if (!"=".equals(a.op)) throw new Unmappable("a value list with operator " + a.op);
                List<String> vs = new ArrayList<String>();
                for (String v : a.values) vs.add(tpl(v, s.line));
                imports.add("java.util.Arrays");
                cmp = "Arrays.asList(" + String.join(", ", vs) + ").contains(a)";
            } else {
                String e = member(tpl(a.value, s.line));
                switch (a.op) {
                    case "=": cmp = e + ".equals(a)"; break;
                    case "!=": cmp = "!" + e + ".equals(a)"; break;
                    case "<": case ">": case "<=": case ">=": cmp = e + ".compareTo(a) " + a.op + " 0"; break;
                    case "contains": cmp = e + ".contains(a)"; break;
                    case "startsWith": cmp = e + ".startsWith(a)"; break;
                    case "endsWith": cmp = e + ".endsWith(a)"; break;
                    default: throw new Unmappable("assertion operator " + a.op);
                }
                if (!"=".equals(a.op) && !"!=".equals(a.op)) {
                    note(s.line, src(s.line), "seleasy compares expected." + a.op + "(actual), e.g. %'ok' passes when 'ok' starts with the element text; "
                            + "exported literally");
                }
            }
            use("check");
            String call = "check(" + Jv.str(src(s.line)) + ", " + wait(s) + ", () -> " + getter + ", a -> " + cmp + ")";
            if (s.soft) {
                soft();
                use("soft");
                line(b, ind, "soft(() -> " + call + ");");
            } else {
                line(b, ind, call + ";");
            }
        }

        // ---- variables and calls ------------------------------------------------------------------------------------

        private void elementVar(Step s, StringBuilder b, int ind) {
            use("find");
            String el = "find(" + loc(s.target, s.line) + ", timeout)";
            String read = s.args.get("read");
            String rhs = read.equals("text") ? el + ".getText()"
                    : read.equals("html") ? el + ".getAttribute(\"innerHTML\")"
                    : el + ".getAttribute(" + Jv.str(s.args.get("name")) + ")";
            line(b, ind, put(s.text, rhs));
        }

        private void call(Step s, StringBuilder b, int ind) {
            Def d = defs.get(s.text.toLowerCase(Locale.ROOT));
            if (d == null) throw new Unmappable("no exported subtest or func named '" + s.text + "' in this script");
            List<String> args = new ArrayList<String>();
            for (Map.Entry<String, String> e : s.args.entrySet()) {
                if (e.getKey().startsWith("arg.")) {
                    args.add(Jv.str(e.getKey().substring(4)));
                    args.add(tpl(e.getValue(), s.line));
                }
            }
            if (d.takesArgs) {
                use("args");
                line(b, ind, d.fn + "(args(" + String.join(", ", args) + "));");
            } else {
                line(b, ind, d.fn + "();");
            }
        }

        // ---- page-level ---------------------------------------------------------------------------------------------

        private void scroll(Step s, StringBuilder b, int ind) {
            use("js");
            if (s.target != null) {
                use("find", "pause");
                line(b, ind, "js(\"arguments[0].scrollIntoView(true)\", find(" + loc(s.target, s.line) + ", timeout));");
                line(b, ind, "pause(1000L);");
                return;
            }
            String js;
            switch (s.text) {
                case "up": js = "window.scrollBy(0,-20)"; break;
                case "down": js = "window.scrollBy(0,20)"; break;
                case "pageup": js = "window.scrollBy(0,-200)"; break;
                case "pagedown": js = "window.scrollBy(0,200)"; break;
                case "top": js = "window.scrollTo(0,0)"; break;
                default: js = "window.scrollTo(0,document.body.scrollHeight)"; break;
            }
            line(b, ind, "js(" + Jv.str(js) + ");");
        }

        // ---- the runtime helpers --------------------------------------------------------------------------------------

        private StringBuilder runtime() {
            // dependencies between helpers
            if (helpers.contains("actionable") || helpers.contains("check") || helpers.contains("gone") || helpers.contains("waitReady")) helpers.add("pause");
            if (helpers.contains("actionable") || helpers.contains("now")) helpers.add("find");
            if (helpers.contains("find")) helpers.add("pause");
            if (helpers.contains("semantic") || helpers.contains("type") || helpers.contains("focus") || helpers.contains("waitReady")) helpers.add("js");
            if (helpers.contains("all") || helpers.contains("gone") || helpers.contains("find") || helpers.contains("active")) helpers.add("Loc");
            if (helpers.contains("Loc")) {
                imports.add("org.openqa.selenium.WebElement");
                imports.add("java.util.List");
            }

            StringBuilder b = new StringBuilder();
            b.append("\n    // ---- gatf-export runtime: Selenium + JUnit 5 only ------------------------------------------------------------\n");
            driverFactory(b);
            if (helpers.contains("env")) {
                b.append("\n    /** A ${...} value: read from the environment, never from a GATF config. */\n")
                        .append("    private static String env(String name) {\n")
                        .append("        String value = System.getenv(name);\n")
                        .append("        if (value == null) throw new IllegalStateException(\"gatf-export: set the environment variable \" + name);\n")
                        .append("        return value;\n")
                        .append("    }\n");
            }
            if (helpers.contains("v")) {
                b.append("\n    /** A $v{...} script variable. */\n")
                        .append("    private String v(String name) {\n")
                        .append("        String value = vars.get(name);\n")
                        .append("        if (value == null) throw new IllegalStateException(\"gatf-export: the script variable \" + name + \" is not set\");\n")
                        .append("        return value;\n")
                        .append("    }\n");
            }
            if (helpers.contains("args")) {
                b.append("\n    /** Subtest / func arguments as name, value pairs. */\n")
                        .append("    private static Map<String, String> args(String... nameValue) {\n")
                        .append("        Map<String, String> m = new HashMap<>();\n")
                        .append("        for (int i = 0; i + 1 < nameValue.length; i += 2) m.put(nameValue[i], nameValue[i + 1]);\n")
                        .append("        return m;\n")
                        .append("    }\n");
            }
            if (helpers.contains("Loc")) {
                b.append("\n    /** Every element a find expression matches now. */\n")
                        .append("    @FunctionalInterface\n")
                        .append("    private interface Finder {\n")
                        .append("        List<WebElement> find();\n")
                        .append("    }\n")
                        .append("\n    /** A find expression: its seleasy text, how to find it, and whether it must match one element only. */\n")
                        .append("    private static final class Loc {\n")
                        .append("        final String what;\n")
                        .append("        final Finder finder;\n")
                        .append("        final boolean strict;\n")
                        .append("\n        Loc(String what, Finder finder, boolean strict) {\n")
                        .append("            this.what = what;\n")
                        .append("            this.finder = finder;\n")
                        .append("            this.strict = strict;\n")
                        .append("        }\n")
                        .append("    }\n")
                        .append("\n    private Loc by(String what, By by) {\n")
                        .append("        return new Loc(what, () -> driver.findElements(by), false);\n")
                        .append("    }\n");
                imports.add("org.openqa.selenium.By");
            }
            if (helpers.contains("active")) {
                b.append("\n    /** seleasy's 'active': the focused element. */\n")
                        .append("    private Loc active() {\n")
                        .append("        return new Loc(\"active\", () -> java.util.Collections.singletonList(driver.switchTo().activeElement()), false);\n")
                        .append("    }\n");
            }
            if (helpers.contains("semantic")) semanticRuntime(b);
            if (helpers.contains("xpathLiteral")) {
                b.append("\n    /** An XPath 1.0 string literal (concat(...) when it holds both quote kinds). */\n")
                        .append("    private static String xpathLiteral(String s) {\n")
                        .append("        if (s.indexOf('\\'') < 0) return \"'\" + s + \"'\";\n")
                        .append("        if (s.indexOf('\"') < 0) return \"\\\"\" + s + \"\\\"\";\n")
                        .append("        return \"concat('\" + s.replace(\"'\", \"', \\\"'\\\", '\") + \"')\";\n")
                        .append("    }\n");
            }
            if (helpers.contains("all")) {
                b.append("\n    /** Every element the find expression matches now (no wait). */\n")
                        .append("    private List<WebElement> all(Loc l) {\n")
                        .append("        return l.finder.find();\n")
                        .append("    }\n");
            }
            if (helpers.contains("find")) {
                imports.add("org.openqa.selenium.NoSuchElementException");
                imports.add("org.openqa.selenium.StaleElementReferenceException");
                b.append("\n    /** The element seleasy acts on: the first match, waited for up to t (a strict locator must match one element). */\n")
                        .append("    private WebElement find(Loc l, Duration t) {\n")
                        .append("        long end = System.nanoTime() + t.toNanos();\n")
                        .append("        while (true) {\n")
                        .append("            try {\n")
                        .append("                List<WebElement> els = l.finder.find();\n")
                        .append("                if (els.size() > 1 && l.strict) throw new AssertionError(\"Strict locator \" + l.what + \" matched \" + els.size() + \" elements\");\n")
                        .append("                if (!els.isEmpty()) return els.get(0);\n")
                        .append("            } catch (StaleElementReferenceException e) {\n")
                        .append("                // the page changed while searching: search again\n")
                        .append("            }\n")
                        .append("            if (System.nanoTime() > end) throw new NoSuchElementException(\"Element not found by selector \" + l.what + \" (waited \" + t.getSeconds() + \"s)\");\n")
                        .append("            pause(100L);\n")
                        .append("        }\n")
                        .append("    }\n");
            }
            if (helpers.contains("now")) {
                b.append("\n    /** The first match right now (assertions re-read it on every check). */\n")
                        .append("    private WebElement now(Loc l) {\n")
                        .append("        return find(l, Duration.ZERO);\n")
                        .append("    }\n");
            }
            if (helpers.contains("actionable")) {
                imports.add("org.openqa.selenium.ElementNotInteractableException");
                imports.add("org.openqa.selenium.StaleElementReferenceException");
                b.append("\n    /**\n")
                        .append("     * find(), then until the element is displayed (not for upload / submit) and enabled (not for hover / drag):\n")
                        .append("     * an approximation of seleasy's actionability probe.\n")
                        .append("     */\n")
                        .append("    private WebElement actionable(Loc l, Duration t, String mode) {\n")
                        .append("        long end = System.nanoTime() + t.toNanos();\n")
                        .append("        boolean visible = !mode.equals(\"upload\") && !mode.equals(\"submit\"), enabled = !mode.equals(\"hover\") && !mode.equals(\"drag\");\n")
                        .append("        WebElement e = find(l, t);\n")
                        .append("        while (true) {\n")
                        .append("            try {\n")
                        .append("                if ((!visible || e.isDisplayed()) && (!enabled || e.isEnabled())) return e;\n")
                        .append("            } catch (StaleElementReferenceException x) {\n")
                        .append("                e = find(l, Duration.ofNanos(Math.max(0L, end - System.nanoTime())));   // replaced while waiting: find it again\n")
                        .append("                continue;\n")
                        .append("            }\n")
                        .append("            if (System.nanoTime() > end) throw new ElementNotInteractableException(l.what + \" is not \" + (visible ? \"displayed and \" : \"\") + \"enabled for \" + mode + \" (waited \" + t.getSeconds() + \"s)\");\n")
                        .append("            pause(100L);\n")
                        .append("        }\n")
                        .append("    }\n");
            }
            if (helpers.contains("gone")) {
                b.append("\n    /** Waits until the find expression matches nothing (??+, wait till visible). */\n")
                        .append("    private void gone(Loc l, Duration t) {\n")
                        .append("        long end = System.nanoTime() + t.toNanos();\n")
                        .append("        while (!l.finder.find().isEmpty()) {\n")
                        .append("            if (System.nanoTime() > end) throw new AssertionError(l.what + \" is still found after \" + t.getSeconds() + \"s\");\n")
                        .append("            pause(100L);\n")
                        .append("        }\n")
                        .append("    }\n");
            }
            if (helpers.contains("check")) {
                imports.add("org.openqa.selenium.WebDriverException");
                b.append("\n    /** Reads a value for an assertion. */\n")
                        .append("    @FunctionalInterface\n")
                        .append("    private interface Read {\n")
                        .append("        String get();\n")
                        .append("    }\n")
                        .append("\n    /** seleasy's comparison, expected.op(actual). */\n")
                        .append("    @FunctionalInterface\n")
                        .append("    private interface Expect {\n")
                        .append("        boolean test(String actual);\n")
                        .append("    }\n")
                        .append("\n    /** Re-reads the value until the comparison passes or t runs out (web-first). */\n")
                        .append("    private void check(String statement, Duration t, Read read, Expect expect) {\n")
                        .append("        long end = System.nanoTime() + t.toNanos();\n")
                        .append("        String last = null;\n")
                        .append("        RuntimeException error = null;\n")
                        .append("        while (true) {\n")
                        .append("            try {\n")
                        .append("                last = read.get();\n")
                        .append("                error = null;\n")
                        .append("                if (last != null && expect.test(last)) return;\n")
                        .append("            } catch (WebDriverException | IllegalStateException e) {\n")
                        .append("                error = e;\n")
                        .append("            }\n")
                        .append("            if (System.nanoTime() > end) break;\n")
                        .append("            pause(100L);\n")
                        .append("        }\n")
                        .append("        throw new AssertionError(statement + \" failed after \" + t.getSeconds() + \"s; \" + (error != null ? error.getMessage() : \"actual: \" + (last == null ? \"null\" : \"'\" + last + \"'\")), error);\n")
                        .append("    }\n");
            }
            if (helpers.contains("soft")) {
                imports.add("org.openqa.selenium.WebDriverException");
                b.append("\n    /** One soft statement (??~). */\n")
                        .append("    @FunctionalInterface\n")
                        .append("    private interface Soft {\n")
                        .append("        void run();\n")
                        .append("    }\n")
                        .append("\n    /** A soft failure is recorded and the test goes on; closeBrowser() fails the test with all of them. */\n")
                        .append("    private void soft(Soft s) {\n")
                        .append("        try {\n")
                        .append("            s.run();\n")
                        .append("        } catch (AssertionError | WebDriverException e) {\n")
                        .append("            softFailures.add(String.valueOf(e.getMessage()));\n")
                        .append("        }\n")
                        .append("    }\n");
            }
            if (helpers.contains("type")) {
                imports.add("org.openqa.selenium.Keys");
                b.append("\n    /** sendKeys, then the type qualifier's event as seleasy fires it (bl, ch, fo, bk, cl). */\n")
                        .append("    private void type(WebElement e, CharSequence text, String qualifier) {\n")
                        .append("        e.sendKeys(text);\n")
                        .append("        switch (qualifier) {\n")
                        .append("            case \"bl\": js(\"arguments[0].blur();\", e); break;\n")
                        .append("            case \"ch\": js(\"arguments[0].dispatchEvent(new Event('change', {bubbles: true}));\", e); break;\n")
                        .append("            case \"fo\": e.click(); js(\"arguments[0].focus();\", e); break;\n")
                        .append("            case \"bk\": e.sendKeys(Keys.chord(Keys.BACK_SPACE)); e.click(); break;\n")
                        .append("            case \"cl\": e.click(); break;\n")
                        .append("            default: break;\n")
                        .append("        }\n")
                        .append("    }\n");
            }
            if (helpers.contains("focus")) {
                b.append("\n    /** clickfo: focus the element first. */\n")
                        .append("    private WebElement focus(WebElement e) {\n")
                        .append("        js(\"arguments[0].focus();\", e);\n")
                        .append("        return e;\n")
                        .append("    }\n");
            }
            if (helpers.contains("selectLast")) {
                b.append("\n    private static void selectLast(Select s) {\n")
                        .append("        s.selectByIndex(s.getOptions().size() - 1);\n")
                        .append("    }\n");
            }
            if (helpers.contains("drag")) {
                imports.add("org.openqa.selenium.interactions.Actions");
                b.append("\n    /** actions draganddrop: press, move and release, as seleasy (draganddrop1 is Selenium's dragAndDrop). */\n")
                        .append("    private void drag(WebElement from, WebElement to) {\n")
                        .append("        new Actions(driver).clickAndHold(from).moveToElement(to).release(to).perform();\n")
                        .append("    }\n");
            }
            if (helpers.contains("js")) {
                imports.add("org.openqa.selenium.JavascriptExecutor");
                b.append("\n    /** executeScript: the script is a function body and sees 'arguments', as in seleasy. */\n")
                        .append("    private Object js(String script, Object... args) {\n")
                        .append("        return ((JavascriptExecutor) driver).executeScript(script, args);\n")
                        .append("    }\n");
            }
            if (helpers.contains("waitReady")) {
                b.append("\n    /** waitready: until document.readyState is complete (at most 60 s). */\n")
                        .append("    private void waitReady() {\n")
                        .append("        long end = System.nanoTime() + Duration.ofSeconds(60).toNanos();\n")
                        .append("        while (!\"complete\".equals(js(\"return document.readyState\")) && System.nanoTime() < end) pause(100L);\n")
                        .append("    }\n");
            }
            if (helpers.contains("screenshot")) {
                imports.add("org.openqa.selenium.OutputType");
                imports.add("org.openqa.selenium.TakesScreenshot");
                imports.add("java.nio.file.Files");
                imports.add("java.nio.file.Path");
                imports.add("java.nio.file.Paths");
                b.append("\n    /** A page or element screenshot written to path (relative to the working directory). */\n")
                        .append("    private static void screenshot(TakesScreenshot source, String path) throws Exception {\n")
                        .append("        Path p = Paths.get(path).toAbsolutePath();\n")
                        .append("        Files.createDirectories(p.getParent());\n")
                        .append("        Files.write(p, source.getScreenshotAs(OutputType.BYTES));\n")
                        .append("    }\n");
            }
            if (helpers.contains("skipped")) {
                b.append("\n    /** A skipped subtest: reported on stdout, its body not run. */\n")
                        .append("    private static boolean skipped(String subtest, String why) {\n")
                        .append("        System.out.println(\"[gatf-export] skipped subtest \" + subtest + \" (\" + why + \")\");\n")
                        .append("        return true;\n")
                        .append("    }\n");
            }
            if (helpers.contains("pause")) {
                b.append("\n    private static void pause(long ms) {\n")
                        .append("        try {\n")
                        .append("            Thread.sleep(ms);\n")
                        .append("        } catch (InterruptedException e) {\n")
                        .append("            Thread.currentThread().interrupt();\n")
                        .append("            throw new IllegalStateException(\"interrupted\", e);\n")
                        .append("        }\n")
                        .append("    }\n");
            }
            return b;
        }

        private void driverFactory(StringBuilder b) {
            String opts, drv;
            switch (browser) {
                case "firefox": opts = "FirefoxOptions"; drv = "FirefoxDriver"; imports.add("org.openqa.selenium.firefox.FirefoxDriver"); imports.add("org.openqa.selenium.firefox.FirefoxOptions"); break;
                case "edge": opts = "EdgeOptions"; drv = "EdgeDriver"; imports.add("org.openqa.selenium.edge.EdgeDriver"); imports.add("org.openqa.selenium.edge.EdgeOptions"); break;
                case "safari": opts = "SafariOptions"; drv = "SafariDriver"; imports.add("org.openqa.selenium.safari.SafariDriver"); imports.add("org.openqa.selenium.safari.SafariOptions"); break;
                default: opts = "ChromeOptions"; drv = "ChromeDriver"; imports.add("org.openqa.selenium.chrome.ChromeDriver"); imports.add("org.openqa.selenium.chrome.ChromeOptions"); break;
            }
            imports.add("org.openqa.selenium.remote.RemoteWebDriver");
            imports.add("java.net.URI");
            b.append("\n    /** The script's browser (" + browser + (headless ? ", headless" : "") + "): local, or on a Selenium Grid when SELENIUM_REMOTE_URL is set. */\n");
            b.append("    private static WebDriver newDriver() throws Exception {\n");
            b.append("        " + opts + " options = new " + opts + "();\n");
            if (!browser.equals("safari")) {
                b.append("        String binary = System.getProperty(\"gatf.export.browser.binary\", \"\");\n");
                b.append("        if (!binary.isEmpty()) options.setBinary(binary);\n");
                if (headless) b.append("        options.addArguments(" + Jv.str(browser.equals("firefox") ? "-headless" : "--headless=new") + ");\n");
                b.append("        for (String a : System.getProperty(\"gatf.export.browser.args\", \"\").split(\",\")) if (!a.trim().isEmpty()) options.addArguments(a.trim());\n");
            }
            b.append("        String grid = System.getenv(\"SELENIUM_REMOTE_URL\");\n");
            b.append("        if (grid != null && !grid.isEmpty()) return new RemoteWebDriver(URI.create(grid).toURL(), options);\n");
            b.append("        return new " + drv + "(options);\n");
            b.append("    }\n");
        }

        private void semanticRuntime(StringBuilder b) {
            imports.add("org.openqa.selenium.JavascriptExecutor");
            imports.add("java.util.ArrayList");
            imports.add("java.util.LinkedHashMap");
            imports.add("java.util.Map");
            b.append("\n    /** --strict-locators: a semantic locator must match one element (seleasy's strictLocators). */\n")
                    .append("    private static final boolean STRICT_LOCATORS = ").append(opt.strictLocators).append(";\n")
                    .append("    /** seleasy's testIdAttribute, read by testid@. */\n")
                    .append("    private static final String TEST_ID_ATTRIBUTE = ").append(Jv.str(SemanticLocators.DEFAULT_TEST_ID_ATTRIBUTE)).append(";\n")
                    .append("    /** seleasy's semantic-locator resolver (com.gatf.selenium.SemanticLocators.RESOLVER_JS), verbatim. */\n")
                    .append("    private static final String SEMANTIC_JS = \"\"");
            for (String l : SelParser.splitLines(SemanticLocators.RESOLVER_JS)) b.append("\n            + ").append(Jv.str(l + "\n"));
            b.append(";\n");
            b.append("\n    /** A semantic locator spec, as SemanticLocators.parse builds it. */\n")
                    .append("    private static Map<String, Object> spec(Object... keyValue) {\n")
                    .append("        Map<String, Object> m = new LinkedHashMap<>();\n")
                    .append("        for (int i = 0; i + 1 < keyValue.length; i += 2) m.put((String) keyValue[i], keyValue[i + 1]);\n")
                    .append("        return m;\n")
                    .append("    }\n")
                    .append("\n    /** role@ label@ placeholder@ testid@ alt@ title@ text@=x text@/re/: resolved in the page by SEMANTIC_JS. */\n")
                    .append("    private Loc semantic(String what, Map<String, Object> spec) {\n")
                    .append("        return new Loc(what, () -> {\n")
                    .append("            Object r = ((JavascriptExecutor) driver).executeScript(SEMANTIC_JS, null, spec, TEST_ID_ATTRIBUTE);\n")
                    .append("            List<WebElement> els = new ArrayList<>();\n")
                    .append("            if (r instanceof Map && ((Map<?, ?>) r).get(\"els\") instanceof List) {\n")
                    .append("                for (Object o : (List<?>) ((Map<?, ?>) r).get(\"els\")) if (o instanceof WebElement) els.add((WebElement) o);\n")
                    .append("            }\n")
                    .append("            return els;\n")
                    .append("        }, STRICT_LOCATORS);\n")
                    .append("    }\n");
        }
    }
}
