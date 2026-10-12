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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.gatf.executor.core.AcceptanceTestContext;
import com.gatf.executor.core.GatfExecutorConfig;
import com.gatf.export.ExportCatalog;
import com.gatf.export.SelParser;
import com.gatf.export.SelScript;

/**
 * Δ160 fidelity guard — the export parser ({@link SelParser}) and the real parser ({@code Command.read}, set up as
 * CodegenGoldenTest does) must agree on every codegen-corpus, sample-project and dogfood script: the same statement
 * lines, the same block depth per line, a leading keyword whose Command class matches the one the real parser built,
 * the same locator ({@code by} + value), typed / uploaded / selected value, assertion property / operator / value,
 * {@code ??N} timeout, nowait and soft flags, and if vs ifnot. Fails with the first differences per script.
 *
 * <p>Δ160a: the export corpus is compared too (with the codegen corpus' gatf-config), except 10-unsupported.sel,
 * which holds deliberately invalid statements.
 *
 * <p>Comparable statements: comments, braces, value lists, code-block bodies and plugin / var data blocks are not
 * statements on either side; a {@code ^dynprop} line (resolved from config at parse time) is skipped. Δ145b: a line
 * holding an unquoted {@code * /} outside a block comment (e.g. {@code route **}{@code /api/x}) is an ordinary
 * statement on both sides and is compared.
 */
class ExportParserParityTest {

    private static final String[] PROJECTS = { "../sample-project", "../dogfood", "src/test/resources/codegen-corpus",
            "src/test/resources/export-corpus/playwright" };
    private static final String EXPORT_CORPUS = "src/test/resources/export-corpus/playwright";
    private static final Set<String> SKIP = new HashSet<String>(Arrays.asList("10-unsupported.sel"));
    private static final Set<String> NOT_STATEMENTS = new HashSet<String>(Arrays.asList("StartCommand", "EndCommand", "ValueCommand",
            "ValueListCommand", "EndValueListCommand", "NoopCommand", "CommentCommand", "MultiCommentCommand", "EndMultiCommentCommand",
            "EndCodeCommand"));   // `>>>` / `end code` closes a code block like `}` closes a block
    private static final Set<String> RAW_OWNERS = new HashSet<String>(Arrays.asList("PluginCommand", "VarCommand"));
    private static final Set<String> INTERACTIONS = new HashSet<String>(Arrays.asList("click", "type", "sendkeys", "chord", "randomize", "select",
            "doubleclick", "dblclick", "hover", "hoverclick", "clear", "submit", "upload", "actions"));

    /** One statement as either parser sees it. */
    static final class Stmt {
        final int line, depth;
        final String kind;   // real: Command class simple name; export: catalog keyword (or "plugin")
        String by, value;
        /** typed / uploaded text, select mode + value */
        String text, selectBy;
        /** assertion: property (before '@'), attribute name, operator (=, !=, <, ..., contains, startsWith, endsWith), value */
        String prop, propName, op, opValue;
        Integer timeoutSec;
        boolean noWait, soft, negated;

        Stmt(int line, int depth, String kind) {
            this.line = line;
            this.depth = depth;
            this.kind = kind;
        }
    }

    @Test
    void exportParserAgreesWithCommandRead() throws Exception {
        List<String> problems = new ArrayList<String>();
        int scripts = 0, statements = 0;
        for (String projectPath : PROJECTS) {
            Path src = Paths.get(projectPath).toAbsolutePath().normalize();
            if (!Files.isDirectory(src)) continue;
            Path work = Paths.get("target", "export-parity-work", src.getFileName().toString()).toAbsolutePath();
            deleteTree(work);
            if (projectPath.equals(EXPORT_CORPUS)) {
                // .sel files only, run with the codegen corpus' config (testCaseDir = tests)
                Files.createDirectories(work.resolve("tests"));
                Files.copy(Paths.get("src/test/resources/codegen-corpus/gatf-config.xml"), work.resolve("gatf-config.xml"));
                try (Stream<Path> w = Files.list(src)) {
                    for (Path p : w.filter(x -> x.toString().endsWith(".sel")).collect(Collectors.toList())) {
                        Files.copy(p, work.resolve("tests").resolve(p.getFileName().toString()));
                    }
                }
            } else {
                copyTree(src, work);
            }
            String cfgName = Files.exists(work.resolve("gatf-config.json")) ? "gatf-config.json" : "gatf-config.xml";
            if (!Files.exists(work.resolve(cfgName))) continue;
            GatfExecutorConfig config = Command.getConfig(work.resolve(cfgName).toString(), work.toString());
            config.setTestCasesBasePath(work.toString());
            config.setOutFilesBasePath(work.toString());
            AcceptanceTestContext ctx = new AcceptanceTestContext();
            ctx.setGatfExecutorConfig(config);
            ctx.validateAndInit(true);
            ctx.getWorkflowContextHandler().initializeSuiteContext(1);
            Path tests = config.getTestCaseDir() != null ? work.resolve(config.getTestCaseDir()) : work;
            List<Path> sels;
            try (Stream<Path> w = Files.walk(tests)) {
                sels = w.filter(p -> p.toString().endsWith(".sel")).sorted().collect(Collectors.toList());
            }
            for (Path sel : sels) {
                String rel = tests.relativize(sel).toString().replace('\\', '/');
                if (projectPath.equals(EXPORT_CORPUS) && SKIP.contains(sel.getFileName().toString())) continue;
                File file = ctx.getResourceFile(rel);
                Command root;
                try {
                    root = Command.read(file, new ArrayList<String>(), ctx);
                } catch (Exception e) {
                    problems.add(rel + ": Command.read failed: " + e.getMessage());
                    continue;
                }
                scripts++;
                SelScript model = new SelParser().parse(rel, new String(Files.readAllBytes(sel), StandardCharsets.UTF_8));
                Map<Integer, Stmt> mine = new TreeMap<Integer, Stmt>();
                List<int[]> raw = new ArrayList<int[]>();
                Set<Integer> skip = new HashSet<Integer>();
                walkModel(model.steps, mine, raw, skip);
                Map<Integer, Stmt> real = new TreeMap<Integer, Stmt>();
                walkReal(root.children, 0, file.getAbsoluteFile(), real, false);
                for (Integer l : new ArrayList<Integer>(real.keySet())) {
                    for (int[] r : raw) if (l > r[0] && l <= r[1]) real.remove(l);
                    if (skip.contains(l)) real.remove(l);
                }
                statements += compare(rel, mine, real, problems);
            }
        }
        System.out.println("[export-parity] scripts " + scripts + ", statements " + statements);
        if (!problems.isEmpty()) fail(problems.size() + " difference(s) between SelParser and Command.read:\n"
                + String.join("\n", problems.subList(0, Math.min(60, problems.size()))));
        assertTrue(scripts > 10, "scripts compared: " + scripts);
    }

    private static int compare(String rel, Map<Integer, Stmt> mine, Map<Integer, Stmt> real, List<String> problems) {
        int before = problems.size();
        for (Integer l : real.keySet()) {
            if (!mine.containsKey(l)) problems.add(rel + ":" + l + " the real parser has " + real.get(l).kind + ", the export parser has no statement");
        }
        for (Map.Entry<Integer, Stmt> e : mine.entrySet()) {
            Stmt m = e.getValue(), r = real.get(e.getKey());
            String at = rel + ":" + e.getKey() + " ";
            if (r == null) { problems.add(at + "the export parser has '" + m.kind + "', the real parser has no statement"); continue; }
            if (m.depth != r.depth) problems.add(at + "block depth " + m.depth + " (export) != " + r.depth + " (real)");
            if (!classMatches(m.kind, r.kind)) problems.add(at + "keyword '" + m.kind + "' but the real parser built " + r.kind);
            if (m.by != null && r.by != null && (!m.by.equalsIgnoreCase(r.by) || !String.valueOf(m.value).equals(String.valueOf(r.value)))) {
                problems.add(at + "locator " + m.by + "@" + m.value + " (export) != " + r.by + "@" + r.value + " (real)");
            }
            if (m.text != null && r.text != null && !m.text.equals(r.text)) problems.add(at + "value [" + m.text + "] (export) != [" + r.text + "] (real)");
            if (m.selectBy != null && r.selectBy != null && !m.selectBy.equalsIgnoreCase(r.selectBy)) problems.add(at + "select " + m.selectBy + " (export) != " + r.selectBy + " (real)");
            if (m.prop != null || r.prop != null) {
                if (!String.valueOf(m.prop).equalsIgnoreCase(String.valueOf(r.prop))) problems.add(at + "assertion property " + m.prop + " (export) != " + r.prop + " (real)");
                if (m.propName != null && !m.propName.equals(r.propName)) problems.add(at + "assertion attribute " + m.propName + " (export) != " + r.propName + " (real)");
                if (!String.valueOf(m.op).equals(String.valueOf(r.op))) problems.add(at + "assertion operator " + m.op + " (export) != " + r.op + " (real)");
                if (m.opValue != null && !m.opValue.equals(r.opValue)) problems.add(at + "assertion value [" + m.opValue + "] (export) != [" + r.opValue + "] (real)");
            }
            if (m.timeoutSec != null && !m.timeoutSec.equals(r.timeoutSec)) problems.add(at + "timeout " + m.timeoutSec + "s (export) != " + r.timeoutSec + "s (real)");
            if (m.noWait != r.noWait) problems.add(at + "nowait " + m.noWait + " (export) != " + r.noWait + " (real)");
            if (m.soft != r.soft) problems.add(at + "soft " + m.soft + " (export) != " + r.soft + " (real)");
            if (m.negated != r.negated) problems.add(at + "negated (ifnot) " + m.negated + " (export) != " + r.negated + " (real)");
            if (problems.size() - before > 15) { problems.add(rel + ": ... more differences"); break; }
        }
        return mine.size();
    }

    private static boolean classMatches(String keyword, String realClass) {
        if (keyword.equals("plugin")) return realClass.equals("PluginCommand") || realClass.equals("NlpExecSubTestCommand");
        ExportCatalog.Entry e = ExportCatalog.get(keyword);
        if (e != null && e.commandClass.equals(realClass)) return true;
        if (realClass.equals("WaitTillElementVisibleOrInvisibleCommand") && (INTERACTIONS.contains(keyword) || keyword.startsWith("??"))) return true;
        String k = keyword.replaceAll("[^a-z]", "").toLowerCase(Locale.ROOT);
        return !k.isEmpty() && realClass.toLowerCase(Locale.ROOT).startsWith(k);   // e.g. randomize -> RandomizeCommand
    }

    private static void walkModel(List<SelScript.Step> steps, Map<Integer, Stmt> out, List<int[]> raw, Set<Integer> skip) {
        for (SelScript.Step s : steps) {
            String k = s.keyword;
            if (s.kind == SelScript.Kind.COMMENT || "{".equals(k) || "}".equals(k) || "[".equals(k) || "]".equals(k)) continue;
            // ^dynprop lines, and include/import/dynprops (resolved while parsing: the real tree has no statement for them)
            if ("^".equals(k) || "include".equals(k) || "import".equals(k) || "dynprops".equals(k)) { skip.add(s.line); continue; }
            Stmt st = new Stmt(s.line, s.depth, k == null ? "plugin" : k);
            SelScript.Locator l = s.target;
            boolean locatorStmt = s.kind == SelScript.Kind.WAIT || (s.kind == SelScript.Kind.ASSERT && !s.assertion.pageLevel)
                    || s.kind == SelScript.Kind.IF || s.kind == SelScript.Kind.ELSE_IF || s.kind == SelScript.Kind.ELEMENT_VAR
                    || (s.kind == SelScript.Kind.ACTION && s.verb != null && s.verb.matches("click|dblclick|hover|clear|submit|type|upload|select"));
            if (locatorStmt && l != null && l.raw.indexOf('@') > 0) { st.by = l.by; st.value = l.value; }
            if (s.kind == SelScript.Kind.ACTION && s.verb != null && s.verb.matches("type|chord|upload")) st.text = s.text;
            if (s.kind == SelScript.Kind.ACTION && "select".equals(s.verb) && s.args.get("mode") != null && s.text != null) {
                st.selectBy = s.args.get("mode");
                st.text = s.text;
            }
            if (s.kind == SelScript.Kind.ASSERT) {   // a value list (=[a, b]) compares property and operator only
                st.prop = s.assertion.property;
                st.propName = s.assertion.name;
                st.op = s.assertion.op;
                st.opValue = s.assertion.value;
            }
            st.timeoutSec = s.timeoutSec;
            st.noWait = s.noWait;
            st.soft = s.soft;
            st.negated = s.negated;
            out.put(s.line, st);
            if (s.rawBlock) raw.add(new int[] { s.line, s.endLine });
            else walkModel(s.children, out, raw, skip);
        }
    }

    private static void walkReal(List<?> children, int depth, File file, Map<Integer, Stmt> out, boolean insideRaw) throws Exception {
        if (children == null) return;
        for (Object o : children) {
            if (!(o instanceof Command)) continue;
            Command c = (Command) o;
            String cls = c.getClass().getSimpleName();
            Object[] fld = c.fileLineDetails;
            boolean mineFile = fld != null && fld.length > 2 && fld[2] instanceof File && ((File) fld[2]).getAbsoluteFile().equals(file);
            if (mineFile && !insideRaw && !NOT_STATEMENTS.contains(cls)) {
                int line = ((Number) fld[1]).intValue();
                if (!out.containsKey(line)) {
                    Stmt st = new Stmt(line, depth, cls);
                    Object cond = field(c, "cond");
                    if (cond == null) {
                        Object conds = field(c, "conds");
                        if (conds instanceof List && !((List<?>) conds).isEmpty()) cond = ((List<?>) conds).get(0);
                    }
                    if (cond != null) {
                        Object by = field(cond, "by"), classifier = field(cond, "classifier");
                        if (by != null && !String.valueOf(by).isEmpty()) { st.by = String.valueOf(by); st.value = String.valueOf(classifier); }
                        Object sub = field(cond, "subselector"), oper = field(cond, "oper"), operval = field(cond, "operval"), pre = field(cond, "precond");
                        if (oper != null) {
                            String sb = String.valueOf(sub);
                            int at = sb.indexOf('@');
                            st.prop = (at < 0 ? sb : sb.substring(0, at)).toLowerCase(Locale.ROOT);
                            st.propName = at < 0 ? null : SelParserAccess.unquote(sb.substring(at + 1));
                            String p = String.valueOf(pre);
                            st.op = p.equals("contains") || p.equals("startsWith") || p.equals("endsWith") ? p : String.valueOf(oper);
                            st.opValue = operval == null ? null : String.valueOf(operval);
                        }
                    }
                    // the action of the statement: the command itself, or (??N <loc> <verb> ...) its first child
                    List<Object> acts = new ArrayList<Object>();
                    acts.add(c);
                    if (c.children != null) acts.addAll(c.children);
                    for (Object a : acts) {
                        String an = a.getClass().getSimpleName();
                        if (an.equals("TypeCommand") || an.equals("UploadCommand")) { st.text = str(field(a, "value")); break; }
                        if (an.equals("SelectCommand")) { st.selectBy = str(field(a, "by")); st.text = str(field(a, "value")); break; }
                    }
                    st.noWait = Boolean.TRUE.equals(field(c, "noWait"));
                    st.soft = Boolean.TRUE.equals(field(c, "soft"));
                    st.negated = Boolean.TRUE.equals(field(c, "negation"));
                    Object counter = field(c, "counter"), state = field(c, "state");
                    // the granularity the find captured where it is written (a later `timeout` line changes the state's)
                    Object gran = cond == null ? null : field(cond, "sleepGranularity");
                    if (gran == null) gran = state == null ? null : field(state, "timeoutSleepGranularity");
                    if (counter instanceof Number && gran instanceof Number) {
                        st.timeoutSec = (int) (((Number) counter).longValue() * ((Number) gran).longValue() / 1000L);
                    }
                    out.put(line, st);
                }
            }
            boolean raw = insideRaw || RAW_OWNERS.contains(cls);
            walkReal(c.children, depth + 1, file, out, raw);
            Object elseifs = field(c, "elseifs");
            if (elseifs instanceof List) walkReal((List<?>) elseifs, depth, file, out, raw);
            Object elsecmd = field(c, "elsecmd");
            if (elsecmd instanceof Command) walkReal(java.util.Collections.singletonList(elsecmd), depth, file, out, raw);
        }
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    /** Outer-quote stripping as the parsers do it. */
    static final class SelParserAccess {
        static String unquote(String s) {
            if (s != null && s.length() >= 2 && (s.charAt(0) == '"' || s.charAt(0) == '\'') && s.charAt(s.length() - 1) == s.charAt(0)) {
                return s.substring(1, s.length() - 1);
            }
            return s;
        }
    }

    /** A field by name anywhere up the class hierarchy, or null. */
    private static Object field(Object o, String name) throws IllegalAccessException {
        for (Class<?> k = o.getClass(); k != null; k = k.getSuperclass()) {
            try {
                Field f = k.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(o);
            } catch (NoSuchFieldException e) {
                // keep looking in the superclass
            }
        }
        return null;
    }

    private static void copyTree(Path from, Path to) throws IOException {
        try (Stream<Path> w = Files.walk(from)) {
            for (Path p : (Iterable<Path>) w::iterator) {
                Path rel = from.relativize(p);
                if (rel.toString().startsWith("out") || rel.toString().contains("node_modules")) continue;
                Path d = to.resolve(rel.toString());
                if (Files.isDirectory(p)) Files.createDirectories(d);
                else { Files.createDirectories(d.getParent()); Files.copy(p, d); }
            }
        }
    }

    private static void deleteTree(Path p) throws IOException {
        if (!Files.exists(p)) return;
        try (Stream<Path> w = Files.walk(p)) {
            w.sorted(java.util.Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
        }
    }
}
