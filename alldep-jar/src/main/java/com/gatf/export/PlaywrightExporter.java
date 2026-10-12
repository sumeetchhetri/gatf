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
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.gatf.export.SelScript.Assertion;
import com.gatf.export.SelScript.Kind;
import com.gatf.export.SelScript.Locator;
import com.gatf.export.SelScript.Step;

/**
 * Δ160 — the Playwright Test (TypeScript) backend: one {@code test()} per script, using {@code @playwright/test}.
 *
 * <ul>
 * <li>Semantic locators map 1:1 to {@code getByRole / getByLabel / getByPlaceholder / getByTestId / getByAltText /
 * getByTitle / getByText}. Value modes: plain = whole value, case-insensitive ({@code /^…$/i}); {@code ~x} = substring
 * (Playwright's default); {@code =x} = {@code exact: true}; {@code /re/} = a regex. seleasy skips hidden elements for
 * every prefix, so the non-role ones get {@code .filter({ visible: true })} (getByRole already skips them).</li>
 * <li>Strictness: seleasy acts on the first match, so each locator ends in {@code .first()}; with
 * {@link ExportOptions#strictLocators} semantic locators stay strict, as seleasy's {@code strictLocators}.</li>
 * <li>{@code ${name}} → {@code env('GATF_NAME')} (a helper that fails when the variable is unset); {@code $v{name}}
 * → {@code vars['name']}. Values are never resolved.</li>
 * <li>Subtests, funcs and hooks become local async functions in the test; a subtest runs as {@code test.step}, a
 * skipped one as {@code test.step.skip}; afterAll runs in a {@code finally}.</li>
 * <li>Every unsupported statement is emitted as {@code // TODO(gatf-export): <line>} and listed in the report.</li>
 * </ul>
 */
public final class PlaywrightExporter implements Exporter {

    static final Set<String> ARIA_ROLES = new HashSet<String>(Arrays.asList("alert", "alertdialog", "application", "article", "banner",
            "blockquote", "button", "caption", "cell", "checkbox", "code", "columnheader", "combobox", "complementary", "contentinfo",
            "definition", "deletion", "dialog", "directory", "document", "emphasis", "feed", "figure", "form", "generic", "grid",
            "gridcell", "group", "heading", "img", "insertion", "link", "list", "listbox", "listitem", "log", "main", "marquee", "math",
            "meter", "menu", "menubar", "menuitem", "menuitemcheckbox", "menuitemradio", "navigation", "none", "note", "option",
            "paragraph", "presentation", "progressbar", "radio", "radiogroup", "region", "row", "rowgroup", "rowheader", "scrollbar",
            "search", "searchbox", "separator", "slider", "spinbutton", "status", "strong", "subscript", "superscript", "switch", "tab",
            "table", "tablist", "tabpanel", "term", "textbox", "time", "timer", "toolbar", "tooltip", "tree", "treegrid", "treeitem"));

    public static final String TODO = "TODO(gatf-export): ";

    @Override
    public String id() {
        return "playwright";
    }

    @Override
    public String fileName(String scriptName) {
        return scriptName + ".spec.ts";
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

    private static final class Run {
        final SelScript sc;
        final ExportOptions opt;
        final ExportResult r = new ExportResult();
        boolean usesVars, usesEnv, usesFullMatch;
        final Map<String, String> envOwner = new HashMap<String, String>();   // env name → first ${ref}
        final Map<String, Def> defs = new LinkedHashMap<String, Def>();      // lower-case subtest / func name
        final Map<String, Def> hooks = new LinkedHashMap<String, Def>();
        final Set<String> idents = new HashSet<String>();
        final Set<String> argsTargets = new HashSet<String>();
        final Set<String> notedOnce = new HashSet<String>();
        boolean anyOnly;
        int opens, routes, statements, exported;

        Run(SelScript sc, ExportOptions opt) {
            this.sc = sc;
            this.opt = opt;
            r.scriptName = sc.name;
            r.fileName = sc.name + ".spec.ts";
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

            StringBuilder defsOut = new StringBuilder();
            for (Def h : hooks.values()) renderHook(h, defsOut, 1);
            for (Def d : defs.values()) renderDef(d, defsOut, 1);

            List<Step> main = new ArrayList<Step>();
            for (Step s : sc.steps) {
                if (s.kind == Kind.HOOK || ((s.kind == Kind.SUBTEST || s.kind == Kind.FUNC) && isDef(s))) continue;
                main.add(s);
            }
            Def ba = hooks.get("beforeAll"), aa = hooks.get("afterAll");
            StringBuilder mainOut = new StringBuilder();
            int ind = aa != null ? 2 : 1;
            if (ba != null) line(mainOut, ind, "await " + ba.fn + "();");
            seq(main, mainOut, ind);

            StringBuilder out = new StringBuilder();
            out.append("// Exported from ").append(Ts.comment(sc.name)).append(".sel by GATF (gatf export playwright). Review every TODO(gatf-export) line.\n");
            int todos = r.items(ExportResult.Level.UNSUPPORTED).size();
            int notes = r.items(ExportResult.Level.NOTE).size();
            out.append("// ").append(exported).append(" of ").append(statements).append(" statement(s) exported, ").append(todos)
                    .append(" TODO, ").append(notes).append(" note(s): see the export report.\n");
            if (!r.env.isEmpty()) {
                out.append("// Environment variables (names only):");
                for (Map.Entry<String, String> e : r.env.entrySet()) out.append(" ${").append(Ts.comment(e.getKey())).append("} -> ").append(e.getValue()).append(';');
                out.setLength(out.length() - 1);
                out.append('\n');
            }
            out.append("import { test, expect } from '@playwright/test';\n\n");
            if (usesEnv) {
                out.append("function env(name: string): string {\n")
                        .append("  const value = process.env[name];\n")
                        .append("  if (value === undefined) throw new Error('gatf-export: set the environment variable ' + name);\n")
                        .append("  return value;\n")
                        .append("}\n\n");
            }
            if (usesFullMatch) {
                out.append("/** seleasy's plain semantic value: the whole text, whitespace-collapsed, case-insensitive. */\n")
                        .append("function fullMatch(text: string): RegExp {\n")
                        .append("  const words = text.trim().split(/\\s+/).map((w) => w.replace(/[\\\\^$.*+?()[\\]{}|/]/g, '\\\\$&'));\n")
                        .append("  return new RegExp('^' + words.join('\\\\s+') + '$', 'i');\n")
                        .append("}\n\n");
            }
            out.append("test(").append(Ts.str(sc.name)).append(", async ({ page }) => {\n");
            if (usesVars) out.append("  const vars: Record<string, string> = {};\n");
            out.append(defsOut);
            if (aa != null) {
                out.append("  try {\n").append(mainOut).append("  } finally {\n").append("    await ").append(aa.fn).append("();\n").append("  }\n");
            } else {
                out.append(mainOut);
            }
            out.append("});\n");
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
            String base = Ts.ident(prefix, text);
            String id = base;
            for (int i = 2; !idents.add(id); i++) id = base + "_" + i;
            return id;
        }

        // ---- output helpers -----------------------------------------------------------------------------------------

        private static void line(StringBuilder b, int ind, String text) {
            for (int i = 0; i < ind; i++) b.append("  ");
            b.append(text).append('\n');
        }

        private String src(int lineNo) {
            return SelParser.trim(sc.line(lineNo));
        }

        private void note(int lineNo, String construct, String reason) {
            r.note(lineNo, Ts.comment(construct), reason);
        }

        /** A note that applies to the whole script: listed once, at its first line. */
        private void noteOnce(String key, int lineNo, String reason) {
            if (notedOnce.add(key)) note(lineNo, src(lineNo), reason);
        }

        // ---- unsupported --------------------------------------------------------------------------------------------

        private void todo(Step s, StringBuilder b, int ind, String reason) {
            for (int n = s.line; n <= s.endLine; n++) {
                String t = src(n);
                if (!t.isEmpty()) line(b, ind, "// " + TODO + Ts.comment(t));
            }
            r.unsupported(s.line, Ts.comment(src(s.line)), reason);
            listInside(s.children, s.line);
        }

        private void listInside(List<Step> children, int header) {
            for (Step c : children) {
                if (c.kind != Kind.COMMENT) {
                    statements++;
                    r.unsupported(c.line, Ts.comment(src(c.line)), "inside the unsupported block at line " + header);
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
                    todo(s, b, ind, s.reason != null ? s.reason : "else without a preceding if");
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
                if (s.kind == Kind.UNSUPPORTED) { bad = s.reason; break; }
                if (s.kind == Kind.ELSE) { conds.add(null); continue; }
                try {
                    String count = "(await " + all(s.target, s.line) + ".count())";
                    conds.add(s.negated ? count + " === 0" : count + " > 0");
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
                    r.unsupported(chain.get(i).line, Ts.comment(src(chain.get(i).line)), "part of the unsupported if/else chain at line " + whole.line);
                }
                return;
            }
            noteOnce("if-timing", chain.get(0).line, "an if condition is checked at once with count(); seleasy may poll for it briefly first");
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

        private void renderHook(Def h, StringBuilder b, int ind) {
            statements++;
            exported++;
            line(b, ind, "const " + h.fn + " = async (): Promise<void> => {");
            line(b, ind + 1, "await test.step(" + Ts.str(h.step.text) + ", async () => {");
            seq(h.step.children, b, ind + 2);
            line(b, ind + 1, "});");
            line(b, ind, "};");
        }

        private void renderDef(Def d, StringBuilder b, int ind) {
            statements++;
            exported++;
            Step s = d.step;
            line(b, ind, "const " + d.fn + " = async (" + (d.takesArgs ? "args: Record<string, string> = {}" : "") + "): Promise<void> => {");
            int in = ind + 1;
            if (s.kind == Kind.FUNC) {
                if (d.takesArgs) { usesVars = true; line(b, in, "Object.assign(vars, args);"); }
                seq(s.children, b, in);
                line(b, ind, "};");
                return;
            }
            String marker = s.args.get("marker");
            boolean skip = "skip".equals(marker) || "fixme".equals(marker) || (anyOnly && !"only".equals(marker));
            if (skip) {
                String why = marker != null && !"only".equals(marker) ? marker : "another subtest is marked only";
                note(s.line, src(s.line), "skipped with test.step.skip (" + why + "); needs @playwright/test 1.50 or later");
                line(b, in, "await test.step.skip(" + Ts.str(s.text) + ", async () => {");
                if (d.takesArgs) { usesVars = true; line(b, in + 1, "Object.assign(vars, args);"); }
                seq(s.children, b, in + 1);
                line(b, in, "});");
                line(b, ind, "};");
                return;
            }
            Def be = hooks.get("beforeEach"), ae = hooks.get("afterEach");
            line(b, in, "await test.step(" + Ts.str(s.text) + ", async () => {");
            int bi = in + 1;
            if (d.takesArgs) { usesVars = true; line(b, bi, "Object.assign(vars, args);"); }
            if (ae != null) { line(b, bi, "try {"); bi++; }
            if (be != null) line(b, bi, "await " + be.fn + "();");
            seq(s.children, b, bi);
            if (ae != null) {
                line(b, bi - 1, "} finally {");
                line(b, bi, "await " + ae.fn + "();");
                line(b, bi - 1, "}");
            }
            line(b, in, "});");
            line(b, ind, "};");
        }

        // ---- one statement ------------------------------------------------------------------------------------------

        private void step(Step s, StringBuilder b, int ind) {
            if (s.trailingComment != null) line(b, ind, "//" + Ts.comment(s.trailingComment));
            if (s.kind == Kind.COMMENT) {
                comment(s, b, ind);
                return;
            }
            statements++;
            if (s.kind == Kind.UNSUPPORTED) {
                todo(s, b, ind, s.reason);
                return;
            }
            StringBuilder one = new StringBuilder();
            int itemsBefore = r.items.size();
            Map<String, String> envBefore = new HashMap<String, String>(r.env), ownerBefore = new HashMap<String, String>(envOwner);
            try {
                statement(s, one, ind);
            } catch (Unmappable u) {
                while (r.items.size() > itemsBefore) r.items.remove(r.items.size() - 1);
                r.env.clear();
                r.env.putAll(envBefore);
                envOwner.clear();
                envOwner.putAll(ownerBefore);
                todo(s, b, ind, u.getMessage());
                return;
            }
            exported++;
            b.append(one);
        }

        private void comment(Step s, StringBuilder b, int ind) {
            if ("/*".equals(s.keyword) && s.text != null) {
                for (String l : SelParser.splitLines(s.text)) line(b, ind, "// " + Ts.comment(SelParser.trim(l)));
                if (s.args.containsKey("sameLineEnd")) {
                    note(s.line, src(s.line), "a /* … */ comment on one line: seleasy keeps the comment open up to the next line containing */, so lines "
                            + (s.line + 1) + "-" + s.endLine + " are comments too (exported as comments)");
                }
            } else if (s.text != null) {
                line(b, ind, "//" + Ts.comment(s.text));
            }
        }

        private void statement(Step s, StringBuilder b, int ind) {
            switch (s.kind) {
                case OPEN:
                    if (opens++ > 0) throw new Unmappable("a second browser session (open): one page per exported test");
                    line(b, ind, "// gatf-export: '" + Ts.comment(src(s.line)) + "' - the browser comes from the Playwright project (playwright.config)");
                    note(s.line, src(s.line), "the browser is chosen by the Playwright project, not the script");
                    return;
                case MAXIMIZE:
                    line(b, ind, "// gatf-export: 'maximize' - set the viewport in playwright.config (use.viewport)");
                    note(s.line, src(s.line), "maximize has no page-level equivalent; set use.viewport in playwright.config");
                    return;
                case GOTO:
                    line(b, ind, "await page.goto(" + tpl(s.text, s.line) + ");");
                    return;
                case BACK:
                    line(b, ind, "await page.goBack();");
                    return;
                case FORWARD:
                    line(b, ind, "await page.goForward();");
                    return;
                case REFRESH:
                    line(b, ind, "await page.reload();");
                    return;
                case WAITREADY:
                    line(b, ind, "await page.waitForLoadState('load');");
                    return;
                case SLEEP:
                    line(b, ind, "await page.waitForTimeout(" + Long.parseLong(s.text) + ");");
                    return;
                case TIMEOUT:
                    line(b, ind, "page.setDefaultTimeout(" + (Long.parseLong(s.text) * 1000L) + ");");
                    if (s.args.containsKey("extra")) note(s.line, src(s.line), "only the element timeout is exported; '" + s.args.get("extra") + "' (if-timeout / poll granularity) has no equivalent");
                    return;
                case CLOSE:
                    line(b, ind, "await page.close();");
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
                    usesVars = true;
                    line(b, ind, var(s.text) + " = " + (s.args.containsKey("ref") ? var(s.args.get("ref")) : tpl(s.args.get("value"), s.line)) + ";");
                    return;
                case ELEMENT_VAR:
                    elementVar(s, b, ind);
                    return;
                case JS_VAR:
                    usesVars = true;
                    noteJs(s);
                    line(b, ind, var(s.text) + " = String(await page.evaluate(" + jsFunction(s.args.get("script"), s.line) + "));");
                    return;
                case EXEC_JS:
                    noteJs(s);
                    line(b, ind, "await page.evaluate(" + jsFunction(s.text, s.line) + ");");
                    return;
                case CALL:
                    call(s, b, ind);
                    return;
                case PASS:
                    line(b, ind, "return; // pass" + (s.text == null || s.text.isEmpty() ? "" : ": " + Ts.comment(s.text)));
                    return;
                case FAIL:
                    line(b, ind, "throw new Error(" + tpl(s.text == null || s.text.isEmpty() ? "fail" : s.text, s.line) + ");");
                    return;
                case WARN:
                    line(b, ind, "expect.soft(false, " + tpl(s.text == null || s.text.isEmpty() ? "warn" : s.text, s.line) + ").toBe(true);");
                    return;
                case SCREENSHOT:
                    line(b, ind, "await page.screenshot({ path: " + shotPath(s) + " });");
                    return;
                case ELE_SCREENSHOT:
                    line(b, ind, "await " + pick(s.target, s.line) + ".screenshot({ path: " + shotPath(s) + " });");
                    return;
                case SCREENSHOT_COMPARE:
                    screenshotCompare(s, b, ind);
                    return;
                case ROUTE:
                    route(s, b, ind);
                    return;
                case ROUTE_HAR:
                    routeHar(s, b, ind);
                    return;
                case UNROUTE:
                    line(b, ind, s.args.containsKey("all") ? "await page.unrouteAll();" : "await page.unroute(" + tpl(s.text, s.line) + ");");
                    return;
                case STATE_SAVE:
                    line(b, ind, "await page.context().storageState({ path: " + tpl(s.text, s.line) + " });");
                    note(s.line, src(s.line), "Playwright saves cookies + localStorage, relative to its working directory; seleasy also saves sessionStorage "
                            + "and merges into an existing file next to the test cases");
                    return;
                case EMULATE:
                    emulate(s, b, ind);
                    return;
                case CLOCK:
                    clock(s, b, ind);
                    return;
                case SUBTEST: case FUNC: case HOOK:
                    throw new Unmappable("subtests, funcs and hooks are allowed only at the top level");
                default:
                    throw new Unmappable("no Playwright mapping for " + s.kind);
            }
        }

        // ---- values -------------------------------------------------------------------------------------------------

        /** A seleasy value as a TypeScript expression: literals escaped, {@code $v{x}} → vars, {@code ${x}} → env. */
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
                    case LITERAL: parts.add(Ts.str(p.text)); break;
                    case VAR: usesVars = true; parts.add(var(p.text)); break;
                    default: parts.add("env(" + Ts.str(env(p.text)) + ")"); break;
                }
            }
            if (parts.isEmpty()) return "''";
            return String.join(" + ", parts);
        }

        /**
         * An expression ready for a member access: a composed value ({@code vars['n'] + ' x'}) is parenthesised, because
         * {@code a + 'b'.startsWith(c)} would call startsWith on 'b' alone and concatenate a boolean.
         */
        private static String member(String expr) {
            return expr.contains(" + ") ? "(" + expr + ")" : expr;
        }

        private boolean isLiteral(String raw) {
            return Template.parse(raw == null ? "" : raw).isLiteral();
        }

        private String var(String name) {
            usesVars = true;
            return "vars[" + Ts.str(name) + "]";
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
            return name;
        }

        private String jsFunction(String script, int lineNo) {
            return Ts.str("(function () {\n") + " + " + tpl(script, lineNo) + " + " + Ts.str("\n})()");
        }

        private void noteJs(Step s) {
            noteOnce("js", s.line, "browser JavaScript runs through page.evaluate as a function body; Selenium's 'arguments' is not available");
        }

        private String shotPath(Step s) {
            if (s.text != null && !s.text.isEmpty()) return tpl(s.text, s.line);
            return "test.info().outputPath(" + Ts.str("screenshot-line" + s.line + ".png") + ")";
        }

        // ---- locators -----------------------------------------------------------------------------------------------

        /** The locator seleasy acts on: the first match (or strict, for semantic locators under strictLocators). */
        private String pick(Locator l, int lineNo) {
            String all = all(l, lineNo);
            if (opt.strictLocators && l.semantic != null) return all;
            return all + ".first()";
        }

        /** Every element the find expression matches, hidden ones dropped where seleasy drops them. */
        private String all(Locator l, int lineNo) {
            if (l == null) throw new Unmappable("no locator");
            if (l.unsupported != null) throw new Unmappable("locator " + l.raw + ": " + l.unsupported);
            String base = base(l, lineNo);
            if (l.semantic != null && !"role".equals(l.semantic.get("kind")) && !Boolean.TRUE.equals(l.semantic.get("includeHidden"))) {
                return base + ".filter({ visible: true })";
            }
            return base;
        }

        private String base(Locator l, int lineNo) {
            if (l.semantic != null) return semantic(l, lineNo);
            String by = l.kind();
            String v = l.value;
            boolean lit = isLiteral(v);
            switch (by) {
                case "id":
                    return lit && v.matches("[A-Za-z_][A-Za-z0-9_-]*") ? "page.locator(" + Ts.str("#" + v) + ")" : attrCss("id", v, lineNo);
                case "name":
                    return attrCss("name", v, lineNo);
                case "class": case "classname":
                    return lit && v.matches("[A-Za-z_][A-Za-z0-9_-]*") ? "page.locator(" + Ts.str("." + v) + ")" : attrCss("class~", v, lineNo);
                case "css": case "cssselector":
                    if (!lit) return "page.locator('css=' + " + tpl(v, lineNo) + ")";
                    return "page.locator(" + Ts.str(cssNeedsPrefix(v) ? "css=" + v : v) + ")";
                case "tag": case "tagname":
                    if (!lit || !v.matches("[A-Za-z][A-Za-z0-9-]*")) throw new Unmappable("tag@ needs a plain element name");
                    return "page.locator(" + Ts.str(v) + ")";
                case "xpath":
                    return lit ? "page.locator(" + Ts.str("xpath=" + v) + ")" : "page.locator('xpath=' + " + tpl(v, lineNo) + ")";
                case "text":
                    noteOnce("legacy-text", lineNo, "plain text@x (seleasy's XPath 'contains') is case-sensitive; getByText is case-insensitive");
                    return "page.getByText(" + tpl(v, lineNo) + ")";
                case "linktext":
                    if (!lit) throw new Unmappable("linktext@ with a variable");
                    return "page.locator('a', { hasText: " + Ts.regex("^\\s*" + Ts.escapeRegex(v) + "\\s*$", "") + " })";
                case "partiallinktext":
                    noteOnce("partial-link", lineNo, "partiallinktext@ is case-sensitive in Selenium; hasText is case-insensitive");
                    return "page.locator('a', { hasText: " + tpl(v, lineNo) + " })";
                case "active":
                    return "page.locator('*:focus')";
                default:
                    throw new Unmappable("no Playwright locator for " + l.raw);
            }
        }

        private String attrCss(String attr, String v, int lineNo) {
            if (isLiteral(v)) return "page.locator(" + Ts.str("[" + attr + "=\"" + cssString(v) + "\"]") + ")";
            return "page.locator(" + Ts.str("[" + attr + "=") + " + JSON.stringify(" + tpl(v, lineNo) + ") + ']')";
        }

        private static String cssString(String v) {
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < v.length(); i++) {
                char c = v.charAt(i);
                if (c == '"' || c == '\\') b.append('\\').append(c);
                else if (c == '\n') b.append("\\a ");
                else if (c == '\r') b.append("\\d ");
                else if (c == '\f') b.append("\\c ");
                else b.append(c);
            }
            return b.toString();
        }

        /** True when Playwright would not read the value as plain CSS (XPath, text or another engine). */
        private static boolean cssNeedsPrefix(String v) {
            String t = v.trim();
            return t.startsWith("/") || t.startsWith("(") || t.startsWith("..") || t.startsWith("\"") || t.startsWith("'")
                    || t.matches("^[a-zA-Z_0-9+:*-]+=.*") || t.startsWith("internal:");
        }

        private String semantic(Locator l, int lineNo) {
            Map<String, Object> sp = l.semantic;
            String kind = String.valueOf(sp.get("kind"));
            if (kind.equals("role")) {
                String role = String.valueOf(sp.get("role"));
                if (!ARIA_ROLES.contains(role)) throw new Unmappable("'" + role + "' is not an ARIA role Playwright's getByRole accepts");
                List<String> o = new ArrayList<String>();
                if (sp.containsKey("value")) {
                    String[] v = value(sp, lineNo);
                    o.add("name: " + v[0]);
                    if (v[1] != null) o.add("exact: true");
                }
                if (sp.containsKey("level")) o.add("level: " + sp.get("level"));
                for (String k : new String[] { "checked", "disabled", "expanded", "pressed", "selected", "includeHidden" }) {
                    if (sp.containsKey(k)) o.add(k + ": " + sp.get(k));
                }
                return "page.getByRole(" + Ts.str(role) + (o.isEmpty() ? "" : ", { " + String.join(", ", o) + " }") + ")";
            }
            String fn;
            switch (kind) {
                case "label": fn = "getByLabel"; break;
                case "placeholder": fn = "getByPlaceholder"; break;
                case "testid": fn = "getByTestId"; break;
                case "alt": fn = "getByAltText"; break;
                case "title": fn = "getByTitle"; break;
                case "text": fn = "getByText"; break;
                default: throw new Unmappable("no Playwright locator for " + l.raw);
            }
            if (kind.equals("testid")) {
                noteOnce("testid", lineNo, "getByTestId uses Playwright's testIdAttribute (default data-testid); set use.testIdAttribute if the project changes seleasy's testIdAttribute");
                return "page.getByTestId(" + tpl(String.valueOf(sp.get("value")), lineNo) + ")";
            }
            String[] v = value(sp, lineNo);
            return "page." + fn + "(" + v[0] + (v[1] != null ? ", { exact: true }" : "") + ")";
        }

        /** [expression, exact-or-null] for a semantic value in its mode. */
        private String[] value(Map<String, Object> sp, int lineNo) {
            String mode = String.valueOf(sp.get("mode"));
            String v = String.valueOf(sp.get("value"));
            boolean lit = isLiteral(v);
            switch (mode) {
                case "exact":
                    return new String[] { tpl(v, lineNo), "exact" };
                case "contains":
                    return new String[] { tpl(v, lineNo), null };
                case "regex": {
                    String flags = sp.get("flags") == null ? "" : String.valueOf(sp.get("flags"));
                    if (lit) return new String[] { Ts.regex(v, flags), null };
                    return new String[] { "new RegExp(" + tpl(v, lineNo) + (flags.isEmpty() ? "" : ", " + Ts.str(flags)) + ")", null };
                }
                default: // full: whole value, whitespace-collapsed, case-insensitive
                    if (lit) return new String[] { Ts.regex(Ts.fullMatchSource(v), "i"), null };
                    usesFullMatch = true;
                    return new String[] { "fullMatch(" + tpl(v, lineNo) + ")", null };
            }
        }

        // ---- actions ------------------------------------------------------------------------------------------------

        private static String opts(List<String> o) {
            return o.isEmpty() ? "" : "{ " + String.join(", ", o) + " }";
        }

        private List<String> actOpts(Step s, boolean forceable) {
            List<String> o = new ArrayList<String>();
            if (s.noWait && forceable) o.add("force: true");
            if (s.timeoutSec != null) o.add("timeout: " + (s.timeoutSec * 1000L));
            return o;
        }

        private void action(Step s, StringBuilder b, int ind) {
            String v = s.verb;
            if (s.noWait && !(v.equals("type") || v.equals("chord") || v.equals("upload") || v.equals("submit"))) {
                noteOnce("force", s.line, "nowait / '!' maps to { force: true }, which skips Playwright's actionability checks as seleasy's opt-out does");
            }
            switch (v) {
                case "click": {
                    String l = pick(s.target, s.line);
                    if ("fo".equals(s.qualifier)) line(b, ind, "await " + l + ".focus();");
                    line(b, ind, "await " + l + ".click(" + opts(actOpts(s, true)) + ");");
                    return;
                }
                case "dblclick": case "hover": case "clear":
                    line(b, ind, "await " + pick(s.target, s.line) + "." + v + "(" + opts(actOpts(s, true)) + ");");
                    return;
                case "submit": {
                    String l = pick(s.target, s.line);
                    if (s.timeoutSec != null) line(b, ind, "await " + l + ".waitFor({ timeout: " + (s.timeoutSec * 1000L) + " });");
                    line(b, ind, "await " + l + ".evaluate((el) => {");
                    line(b, ind + 1, "const form = el instanceof HTMLFormElement ? el : el.closest('form');");
                    line(b, ind + 1, "if (!form) throw new Error('submit: the element is not in a form');");
                    line(b, ind + 1, "form.requestSubmit();");
                    line(b, ind, "});");
                    noteOnce("submit", s.line, "submit uses form.requestSubmit() (runs validation and the submit event), close to Selenium's submit()");
                    return;
                }
                case "type": case "chord": {
                    String l = pick(s.target, s.line);
                    String text;
                    if (v.equals("chord")) {
                        text = Ts.str(decodeUnicode(s.text));
                        note(s.line, src(s.line), "chord is typed as text: unicode escapes are decoded; Selenium Keys (e.g. Keys.ENTER) are not translated");
                    } else {
                        text = tpl(s.text, s.line);
                        if (s.text != null && s.text.startsWith("file://")) note(s.line, src(s.line), "seleasy resolves a file:// value to a test file path before typing; typed as written");
                    }
                    if (s.noWait) noteOnce("type-nowait", s.line, "nowait has no effect on typing: pressSequentially has no force option");
                    line(b, ind, "await " + l + ".pressSequentially(" + text + (s.timeoutSec != null ? ", { timeout: " + (s.timeoutSec * 1000L) + " }" : "") + ");");
                    switch (s.qualifier) {
                        case "bl": line(b, ind, "await " + l + ".blur();"); break;
                        case "ch": line(b, ind, "await " + l + ".dispatchEvent('change');"); break;
                        case "fo": line(b, ind, "await " + l + ".click();"); line(b, ind, "await " + l + ".focus();"); break;
                        case "bk": line(b, ind, "await " + l + ".press('Backspace');"); line(b, ind, "await " + l + ".click();"); break;
                        case "cl": line(b, ind, "await " + l + ".click();"); break;
                        default: break;
                    }
                    return;
                }
                case "select": {
                    String l = pick(s.target, s.line);
                    String mode = s.args.get("mode");
                    String what;
                    switch (mode) {
                        case "text": what = "{ label: " + tpl(s.text, s.line) + " }"; break;
                        case "value": what = "{ value: " + tpl(s.text, s.line) + " }"; break;
                        case "index": what = "{ index: " + Integer.parseInt(s.text) + " }"; break;
                        case "first": what = "{ index: 0 }"; break;
                        default: what = "{ index: (await " + l + ".locator('option').count()) - 1 }"; break;
                    }
                    List<String> o = actOpts(s, true);
                    line(b, ind, "await " + l + ".selectOption(" + what + (o.isEmpty() ? "" : ", " + opts(o)) + ");");
                    return;
                }
                case "upload": {
                    noteOnce("paths", s.line, "file paths are resolved from the Playwright working directory, not the script's folder");
                    List<String> o = actOpts(s, false);
                    line(b, ind, "await " + pick(s.target, s.line) + ".setInputFiles(" + tpl(s.text, s.line) + (o.isEmpty() ? "" : ", " + opts(o)) + ");");
                    return;
                }
                case "hoverclick": {
                    String o = opts(actOpts(s, true));
                    line(b, ind, "await " + pick(s.target, s.line) + ".hover(" + o + ");");
                    line(b, ind, "await " + pick(s.target2, s.line) + ".click(" + o + ");");
                    return;
                }
                case "drag":
                    line(b, ind, "await " + pick(s.target, s.line) + ".dragTo(" + pick(s.target2, s.line) + (actOpts(s, true).isEmpty() ? "" : ", " + opts(actOpts(s, true))) + ");");
                    if (s.args.get("gesture").endsWith("1")) note(s.line, src(s.line), "draganddrop1 (Selenium dragAndDrop) and draganddrop both map to dragTo");
                    return;
                default:
                    throw new Unmappable("no Playwright mapping for action " + v);
            }
        }

        private static String decodeUnicode(String s) {
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < s.length(); i++) {
                if (s.charAt(i) == '\\' && i + 5 < s.length() && s.charAt(i + 1) == 'u' && s.substring(i + 2, i + 6).matches("[0-9a-fA-F]{4}")) {
                    b.append((char) Integer.parseInt(s.substring(i + 2, i + 6), 16));
                    i += 5;
                } else {
                    b.append(s.charAt(i));
                }
            }
            return b.toString();
        }

        // ---- waits and assertions -----------------------------------------------------------------------------------

        private String expect(Step s) {
            return s.soft ? "expect.soft" : "expect";
        }

        private String timeoutOpt(Step s) {
            return s.timeoutSec == null ? null : "timeout: " + (s.timeoutSec * 1000L);
        }

        private void waitFor(Step s, StringBuilder b, int ind) {
            if (s.args.containsKey("nowaitNote")) note(s.line, src(s.line), "nowait on a wait or assertion has no Playwright equivalent; it keeps its retry");
            noteOnce("wait-direction", s.line, "??+ / wait till visible wait until the element is no longer found, ??- / ?? / wait for until it is found: "
                    + "that is what the transpiler generates (handleWaitFuncWL), although REFERENCE.md describes ??+ as visible and ??- as not visible");
            String t = timeoutOpt(s);
            if ("gone".equals(s.verb)) {
                line(b, ind, "await " + expect(s) + "(" + all(s.target, s.line) + ").toHaveCount(0" + (t == null ? "" : ", { " + t + " }") + ");");
            } else {
                line(b, ind, "await " + expect(s) + "(" + pick(s.target, s.line) + ").toBeAttached(" + (t == null ? "" : "{ " + t + " }") + ");");
            }
        }

        private void assertion(Step s, StringBuilder b, int ind) {
            Assertion a = s.assertion;
            if (s.args.containsKey("nowaitNote")) note(s.line, src(s.line), "nowait on a wait or assertion has no Playwright equivalent; it keeps its retry");
            noteOnce("web-first", s.line, "Playwright assertions always retry until the expect timeout; seleasy retries only with webFirstAssertions");
            String t = timeoutOpt(s);
            boolean single = a.value != null;
            boolean eqne = "=".equals(a.op) || "!=".equals(a.op);
            String not = "!=".equals(a.op) ? ".not" : "";
            if (a.pageLevel) {
                String p = a.property;
                if (single && eqne && (p.equals("title") || p.equals("currenturl"))) {
                    line(b, ind, "await " + expect(s) + "(page)" + not + "." + (p.equals("title") ? "toHaveTitle" : "toHaveURL") + "(" + tpl(a.value, s.line) + (t == null ? "" : ", { " + t + " }") + ");");
                    return;
                }
                String getter;
                if (p.equals("title")) getter = "await page.title()";
                else if (p.equals("currenturl")) getter = "page.url()";
                else throw new Unmappable("page property '" + p + "' has no Playwright mapping");
                poll(s, getter, b, ind);
                return;
            }
            String l = pick(s.target, s.line);
            String p = a.property;
            if (single && eqne) {
                switch (p) {
                    case "text":
                        line(b, ind, "await " + expect(s) + "(" + l + ")" + not + ".toHaveText(" + tpl(a.value, s.line) + ", { useInnerText: true" + (t == null ? "" : ", " + t) + " });");
                        return;
                    case "attr":
                        noteOnce("attr", s.line, "attr@x reads the HTML attribute (toHaveAttribute); Selenium getAttribute prefers the live DOM property (attr@value maps to toHaveValue)");
                        if (a.name.equalsIgnoreCase("value")) {
                            line(b, ind, "await " + expect(s) + "(" + l + ")" + not + ".toHaveValue(" + tpl(a.value, s.line) + (t == null ? "" : ", { " + t + " }") + ");");
                        } else {
                            line(b, ind, "await " + expect(s) + "(" + l + ")" + not + ".toHaveAttribute(" + Ts.str(a.name) + ", " + tpl(a.value, s.line) + (t == null ? "" : ", { " + t + " }") + ");");
                        }
                        return;
                    case "cssvalue":
                        line(b, ind, "await " + expect(s) + "(" + l + ")" + not + ".toHaveCSS(" + Ts.str(a.name) + ", " + tpl(a.value, s.line) + (t == null ? "" : ", { " + t + " }") + ");");
                        return;
                    case "visible": case "enabled":
                        if (a.value.equals("true") || a.value.equals("false")) {
                            boolean want = a.value.equals("true") ^ not.length() > 0;
                            String m = p.equals("visible") ? (want ? "toBeVisible" : "toBeHidden") : (want ? "toBeEnabled" : "toBeDisabled");
                            line(b, ind, "await " + expect(s) + "(" + l + ")." + m + "(" + (t == null ? "" : "{ " + t + " }") + ");");
                            return;
                        }
                        break;
                    default:
                        break;
                }
            }
            String getter;
            switch (p) {
                case "text": getter = "await " + l + ".innerText()"; break;
                case "tagname": getter = "await " + l + ".evaluate((el) => el.tagName.toLowerCase())"; break;
                case "attr":
                    getter = a.name.equalsIgnoreCase("value") ? "await " + l + ".inputValue()" : "(await " + l + ".getAttribute(" + Ts.str(a.name) + ")) ?? ''";
                    break;
                case "cssvalue": getter = "await " + l + ".evaluate((el, name) => getComputedStyle(el).getPropertyValue(name), " + Ts.str(a.name) + ")"; break;
                case "visible": getter = "String(await " + l + ".isVisible())"; break;
                case "enabled": getter = "String(await " + l + ".isEnabled())"; break;
                case "selected":
                    getter = "String(await " + l + ".evaluate((el) => Boolean((el as HTMLInputElement).checked || (el as HTMLOptionElement).selected)))";
                    break;
                default:
                    throw new Unmappable("element property '" + p + "' has no Playwright mapping");
            }
            poll(s, getter, b, ind);
        }

        /** expect.poll reproducing seleasy's comparison, which is expected.op(actual). */
        private void poll(Step s, String getter, StringBuilder b, int ind) {
            Assertion a = s.assertion;
            String cmp;
            if (a.values != null) {
                if (!"=".equals(a.op)) throw new Unmappable("a value list with operator " + a.op);
                List<String> vs = new ArrayList<String>();
                for (String v : a.values) vs.add(tpl(v, s.line));
                cmp = "[" + String.join(", ", vs) + "].includes(actual)";
            } else {
                String e = tpl(a.value, s.line);
                switch (a.op) {
                    case "=": cmp = e + " === actual"; break;
                    case "!=": cmp = e + " !== actual"; break;
                    case "<": case ">": case "<=": case ">=": cmp = e + " " + a.op + " actual"; break;
                    case "contains": cmp = member(e) + ".includes(actual)"; break;
                    case "startsWith": cmp = member(e) + ".startsWith(actual)"; break;
                    case "endsWith": cmp = member(e) + ".endsWith(actual)"; break;
                    default: throw new Unmappable("assertion operator " + a.op);
                }
                if (!"=".equals(a.op) && !"!=".equals(a.op)) {
                    note(s.line, src(s.line), "seleasy compares expected." + a.op + "(actual), e.g. %'ok' passes when 'ok' starts with the element text; "
                            + "exported literally");
                }
            }
            String t = timeoutOpt(s);
            String ex = s.soft ? "expect.configure({ soft: true }).poll" : "expect.poll";
            line(b, ind, "await " + ex + "(async () => {");
            line(b, ind + 1, "const actual = " + getter + ";");
            line(b, ind + 1, "return " + cmp + ";");
            line(b, ind, "}" + (t == null ? "" : ", { " + t + " }") + ").toBe(true);");
        }

        // ---- variables and calls ------------------------------------------------------------------------------------

        private void elementVar(Step s, StringBuilder b, int ind) {
            String l = pick(s.target, s.line);
            String read = s.args.get("read");
            String rhs = read.equals("text") ? "await " + l + ".innerText()"
                    : read.equals("html") ? "await " + l + ".innerHTML()"
                    : "(await " + l + ".getAttribute(" + Ts.str(s.args.get("name")) + ")) ?? ''";
            line(b, ind, var(s.text) + " = " + rhs + ";");
        }

        private void call(Step s, StringBuilder b, int ind) {
            Def d = defs.get(s.text.toLowerCase(Locale.ROOT));
            if (d == null) throw new Unmappable("no exported subtest or func named '" + s.text + "' in this script");
            List<String> args = new ArrayList<String>();
            for (Map.Entry<String, String> e : s.args.entrySet()) {
                if (e.getKey().startsWith("arg.")) args.add(Ts.str(e.getKey().substring(4)) + ": " + tpl(e.getValue(), s.line));
            }
            line(b, ind, "await " + d.fn + "(" + (args.isEmpty() ? "" : "{ " + String.join(", ", args) + " }") + ");");
        }

        // ---- page-level -------------------------------------------------------------------------------------------

        private void scroll(Step s, StringBuilder b, int ind) {
            if (s.target != null) {
                line(b, ind, "await " + pick(s.target, s.line) + ".evaluate((el) => el.scrollIntoView(true));");
                line(b, ind, "await page.waitForTimeout(1000);");
                return;
            }
            String js;
            switch (s.text) {
                case "up": js = "window.scrollBy(0, -20)"; break;
                case "down": js = "window.scrollBy(0, 20)"; break;
                case "pageup": js = "window.scrollBy(0, -200)"; break;
                case "pagedown": js = "window.scrollBy(0, 200)"; break;
                case "top": js = "window.scrollTo(0, 0)"; break;
                default: js = "window.scrollTo(0, document.body.scrollHeight)"; break;
            }
            line(b, ind, "await page.evaluate(" + Ts.str(js) + ");");
        }

        private void screenshotCompare(Step s, StringBuilder b, int ind) {
            List<String> o = new ArrayList<String>();
            if (!s.locators.isEmpty()) {
                List<String> masks = new ArrayList<String>();
                for (Locator l : s.locators) masks.add(all(l, s.line));
                o.add("mask: [" + String.join(", ", masks) + "]");
            }
            String md = s.args.get("maxdiff");
            if (md != null) {
                if (md.endsWith("%")) o.add("maxDiffPixelRatio: " + new java.math.BigDecimal(md.substring(0, md.length() - 1)).movePointLeft(2).stripTrailingZeros().toPlainString());
                else note(s.line, src(s.line), "maxdiff " + md + " (no %) is not mapped; set maxDiffPixels or maxDiffPixelRatio");
            }
            if (s.args.containsKey("threshold")) note(s.line, src(s.line), "threshold " + s.args.get("threshold") + " is not mapped (Playwright's threshold is per-pixel colour distance)");
            noteOnce("baselines", s.line, "toHaveScreenshot keeps its own baselines (the spec's -snapshots folder); seleasy baselines are not reused");
            line(b, ind, "await expect(page).toHaveScreenshot(" + tpl(s.text, s.line) + " + '.png'" + (o.isEmpty() ? "" : ", " + opts(o)) + ");");
        }

        private void route(Step s, StringBuilder b, int ind) {
            if (routes++ == 1) {
                note(s.line, src(s.line), "Playwright runs the most recently added matching route first; seleasy uses the first one added. "
                        + "Overlapping routes may need reordering");
            }
            line(b, ind, "await page.route(" + tpl(s.text, s.line) + ", async (route) => {");
            int in = ind + 1;
            String m = s.args.get("method");
            if (m != null) line(b, in, "if (route.request().method() !== " + Ts.str(m) + ") return route.fallback();");
            Map<String, String> headers = new LinkedHashMap<String, String>();
            for (String h : s.list) {
                int c = h.indexOf(':');
                String name = h.substring(0, c).trim();
                String key = s.verb.equals("continue") ? name.toLowerCase(Locale.ROOT) : name;
                for (String k : headers.keySet()) {
                    if (k.equalsIgnoreCase(key)) note(s.line, src(s.line), "header " + name + " is repeated; Playwright sends one value per name (the last one)");
                }
                headers.remove(key);
                headers.put(key, tpl(h.substring(c + 1).trim(), s.line));
            }
            List<String> hs = new ArrayList<String>();
            for (Map.Entry<String, String> e : headers.entrySet()) hs.add(Ts.str(e.getKey()) + ": " + e.getValue());
            switch (s.verb) {
                case "fulfill": {
                    List<String> o = new ArrayList<String>();
                    o.add("status: " + Integer.parseInt(s.args.get("status")));
                    if (s.args.containsKey("body")) o.add("body: " + tpl(s.args.get("body"), s.line));
                    if (s.args.containsKey("bodyFile")) {
                        o.add("path: " + tpl(s.args.get("bodyFile"), s.line));
                        noteOnce("paths", s.line, "file paths are resolved from the Playwright working directory, not the script's folder");
                    }
                    if (s.args.containsKey("type")) o.add("contentType: " + tpl(s.args.get("type"), s.line));
                    if (!hs.isEmpty()) o.add("headers: { " + String.join(", ", hs) + " }");
                    line(b, in, "await route.fulfill({ " + String.join(", ", o) + " });");
                    break;
                }
                case "abort":
                    line(b, in, "await route.abort(" + (s.args.containsKey("reason") ? Ts.str(s.args.get("reason")) : "") + ");");
                    break;
                default:
                    line(b, in, hs.isEmpty() ? "await route.continue();" : "await route.continue({ headers: { ...route.request().headers(), " + String.join(", ", hs) + " } });");
                    break;
            }
            line(b, ind, "});");
        }

        private void routeHar(Step s, StringBuilder b, int ind) {
            List<String> o = new ArrayList<String>();
            if (s.args.containsKey("url")) o.add("url: " + tpl(s.args.get("url"), s.line));
            if (s.args.containsKey("notFound")) o.add("notFound: " + Ts.str(s.args.get("notFound")));
            noteOnce("har", s.line, "routeFromHAR matches by URL + method (and POST body); seleasy also prefers the entry sharing the most query parameters");
            noteOnce("paths", s.line, "file paths are resolved from the Playwright working directory, not the script's folder");
            line(b, ind, "await page.routeFromHAR(" + tpl(s.text, s.line) + (o.isEmpty() ? "" : ", " + opts(o)) + ");");
        }

        private void emulate(Step s, StringBuilder b, int ind) {
            switch (s.verb) {
                case "viewport":
                    line(b, ind, "await page.setViewportSize({ width: " + Integer.parseInt(s.args.get("width")) + ", height: " + Integer.parseInt(s.args.get("height")) + " });");
                    return;
                case "geolocation":
                    line(b, ind, "await page.context().setGeolocation({ latitude: " + Double.parseDouble(s.args.get("latitude")) + ", longitude: "
                            + Double.parseDouble(s.args.get("longitude")) + " });");
                    note(s.line, src(s.line), "grant the 'geolocation' permission (emulate permissions geolocation) if the page asks for it");
                    return;
                case "permissions": {
                    List<String> ps = new ArrayList<String>();
                    for (String p : s.list) ps.add(Ts.str(p));
                    line(b, ind, "await page.context().grantPermissions([" + String.join(", ", ps) + "]);");
                    return;
                }
                case "colorscheme":
                    line(b, ind, "await page.emulateMedia({ colorScheme: " + Ts.str(s.args.get("scheme")) + " });");
                    return;
                case "offline": case "online":
                    line(b, ind, "await page.context().setOffline(" + s.verb.equals("offline") + ");");
                    return;
                default:
                    throw new Unmappable("emulate " + s.verb);
            }
        }

        private void clock(Step s, StringBuilder b, int ind) {
            String ms = s.args.get("ms");
            switch (s.verb) {
                case "install":
                    line(b, ind, "await page.clock.install(" + (ms == null ? "" : "{ time: " + Long.parseLong(ms) + " }") + "); // " + Ts.comment(s.text));
                    noteOnce("clock", s.line, "Playwright's fake clock covers the whole browser context; install it before the page loads for timers set at load time");
                    return;
                case "set":
                    line(b, ind, "await page.clock.setSystemTime(" + Long.parseLong(ms) + "); // " + Ts.comment(s.text));
                    return;
                case "fastforward":
                    line(b, ind, "await page.clock.fastForward(" + Long.parseLong(ms) + "); // " + Ts.comment(s.text));
                    return;
                default:
                    line(b, ind, "await page.clock.resume();");
                    return;
            }
        }
    }
}
