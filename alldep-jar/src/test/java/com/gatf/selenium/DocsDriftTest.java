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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.gatf.executor.core.AcceptanceTestContext;
import com.gatf.executor.core.GatfExecutorConfig;
import com.gatf.executor.core.TestCase;
import com.gatf.executor.core.WorkflowContextHandler;

/**
 * Δ191 docs gate. Runs tools/gen-docs/GenDocs.java (compiled in-process: generated reference pages are current, every
 * config field / CLI flag / REST endpoint / seleasy keyword is described, internal links resolve, new pages are portable
 * Markdown with front matter), then checks the docs against the runtime: Jackson's view of the config and case model,
 * the CLI usage constants, and every code snippet (seleasy parsed and transpiled by {@link Command}, JSON/XML parsed, and
 * bound strictly to {@link GatfExecutorConfig} / {@link TestCase} when it is one).
 */
class DocsDriftTest {

    static final Path REPO = Paths.get(System.getProperty("user.dir")).getFileName().toString().equals("alldep-jar")
            ? Paths.get("..").toAbsolutePath().normalize() : Paths.get(".").toAbsolutePath().normalize();
    static Class<?> gen;

    @BeforeAll
    static void compileGenerator() throws Exception {
        JavaCompiler jc = ToolProvider.getSystemJavaCompiler();
        assertTrue(jc != null, "DocsDriftTest needs a JDK (javax.tools)");
        Path out = Paths.get("target", "gen-docs-classes").toAbsolutePath();
        Files.createDirectories(out);
        int rc = jc.run(null, null, null, "-d", out.toString(), "-proc:none", REPO.resolve("tools/gen-docs/GenDocs.java").toString());
        assertEquals(0, rc, "tools/gen-docs/GenDocs.java does not compile");
        @SuppressWarnings("resource")
        URLClassLoader cl = new URLClassLoader(new URL[] { out.toUri().toURL() }, DocsDriftTest.class.getClassLoader());
        gen = cl.loadClass("GenDocs");
    }

    @SuppressWarnings("unchecked")
    static List<String> check(Map<String, String> overrides) throws Exception {
        return (List<String>) gen.getMethod("check", Path.class, Map.class).invoke(null, REPO, overrides);
    }

    // ------------------------------------------------------------------------------------------------ generator gate

    @Test
    void docsGateIsGreen() throws Exception {
        List<String> problems = check(Map.of());
        if (!problems.isEmpty()) fail("docs gate (java tools/gen-docs/GenDocs.java check):\n  " + String.join("\n  ", problems));
    }

    @Test
    void undocumentedNewConfigFieldFails() throws Exception {
        String rel = "alldep-jar/src/main/java/com/gatf/executor/core/GatfExecutorConfig.java";
        String src = new String(Files.readAllBytes(REPO.resolve(rel)), StandardCharsets.UTF_8);
        String anchor = "\tprivate String baseUrl;";
        assertTrue(src.contains(anchor), "anchor field moved; update this test");

        List<String> bare = check(Map.of(rel, src.replace(anchor, "\tprivate String driftProbeField;\r\n\r\n" + anchor)));
        assertTrue(bare.stream().anyMatch(p -> p.contains("no description for GatfExecutorConfig.driftProbeField")), "an undocumented field must fail: " + bare);
        assertTrue(bare.stream().anyMatch(p -> p.startsWith("docs/reference/configuration.md: out of date")), "the page must be reported stale: " + bare);

        // with javadoc it is documented: only the page is stale until `generate` runs
        List<String> doc = check(Map.of(rel, src.replace(anchor, "\t/** A probe. */\r\n\tprivate String driftProbeField;\r\n\r\n" + anchor)));
        assertTrue(doc.stream().noneMatch(p -> p.contains("driftProbeField")), "a javadoc'd field is documented: " + doc);
        assertTrue(doc.stream().anyMatch(p -> p.startsWith("docs/reference/configuration.md: out of date")), doc.toString());
    }

    @Test
    void undocumentedNewFlagAndRouteFail() throws Exception {
        String cli = "alldep-jar/src/main/java/com/gatf/generator/core/TestCommandArgs.java";
        String s = new String(Files.readAllBytes(REPO.resolve(cli)), StandardCharsets.UTF_8);
        String a = "case \"--watch\":";
        assertTrue(s.contains(a), "anchor moved; update this test");
        List<String> p1 = check(Map.of(cli, s.replace(a, "case \"--drift-probe\": " + a)));
        assertTrue(p1.stream().anyMatch(p -> p.contains("flag.test.--drift-probe")), p1.toString());

        String ui = "alldep-jar/src/main/java/com/gatf/ui/GatfConfigToolUtil.java";
        String u = new String(Files.readAllBytes(REPO.resolve(ui)), StandardCharsets.UTF_8);
        String r = "addHttpHandler(new GatfMiscHandler(mojo), \"/misc\");";
        assertTrue(u.contains(r), "anchor moved; update this test");
        List<String> p2 = check(Map.of(ui, u.replace(r, r + " server.getServerConfiguration().addHttpHandler(new GatfMiscHandler(mojo), \"/driftprobe\");")));
        assertTrue(p2.stream().anyMatch(p -> p.contains("no description for route /driftprobe")), p2.toString());
    }

    // ------------------------------------------------------------------------------------------------ runtime cross-checks

    /** Every property Jackson binds on each documented type is a row on the page (the generator reads source, this reads the class). */
    @Test
    void modelPagesCoverEveryJacksonProperty() throws Exception {
        List<String> problems = new ArrayList<>();
        for (String page : new String[] { "docs/reference/configuration.md", "docs/reference/test-case-model.md" }) {
            String md = read(page);
            Matcher m = Pattern.compile("\\n## (\\w+)\\n\\n.*?Source: \\[`\\w+\\.java`\\]\\(https://github\\.com/[^)]*?/alldep-jar/src/main/java/([\\w/]+)\\.java\\)").matcher(md);
            int types = 0;
            while (m.find()) {
                types++;
                Class<?> c = Class.forName(m.group(2).replace('/', '.'));
                if (c.isEnum()) continue;
                String section = section(md, m.group(1));
                ObjectMapper om = new ObjectMapper();
                JavaType t = om.constructType(c);
                for (BeanPropertyDefinition d : om.getDeserializationConfig().introspect(t).findProperties()) {
                    if (!section.contains("| `" + d.getName() + "` |")) problems.add(page + ": " + c.getSimpleName() + "." + d.getName() + " is bound by Jackson but not documented");
                }
            }
            assertTrue(types >= 2, page + ": found no type sections");
        }
        if (!problems.isEmpty()) fail(String.join("\n", problems));
    }

    static String section(String md, String type) {
        int s = md.indexOf("\n## " + type + "\n");
        int e = md.indexOf("\n## ", s + 4);
        return md.substring(s, e < 0 ? md.length() : e);
    }

    /** The usage text each command prints at runtime only mentions flags the CLI page documents, and every importer/exporter id is listed. */
    @Test
    void cliPageCoversRuntimeUsage() throws Exception {
        String md = read("docs/reference/cli.md");
        List<String> problems = new ArrayList<>();
        String[][] usages = {
                { "test", "com.gatf.generator.core.TestCommandArgs" }, { "docs", "com.gatf.generator.core.DocsCli" },
                { "import", "com.gatf.generator.core.ImportCli" }, { "export", "com.gatf.generator.core.ExportCli" },
                { "plan", "com.gatf.generator.core.PlanCli" }, { "generate", "com.gatf.generator.core.GenerateCli" },
                { "merge-reports", "com.gatf.log.ReportMerger" }, { "mcp", "com.gatf.mcp.McpCli" } };
        for (String[] u : usages) {
            Field f = Class.forName(u[1]).getDeclaredField("USAGE");
            f.setAccessible(true);
            String usage = (String) f.get(null);
            String section = cliSection(md, u[0]);
            if (!section.contains(usage)) problems.add("cli.md `gatf " + u[0] + "` does not show the runtime usage:\n" + usage);
            Matcher m = Pattern.compile("(?:^|(?<=[\\s\\[|(]))(--?[a-z][a-z0-9]*(?:-[a-z0-9]+)*)").matcher(usage);
            while (m.find()) {
                if (!section.contains("| `" + m.group(1) + "` |")) problems.add("cli.md `gatf " + u[0] + "`: flag " + m.group(1) + " has no row");
            }
        }
        @SuppressWarnings("unchecked")
        Collection<String> types = (Collection<String>) Class.forName("com.gatf.imports.Importers").getField("TYPES").get(null);
        for (String t : types) if (!cliSection(md, "import").contains(t)) problems.add("cli.md: importer " + t + " not listed");
        Method ids = Class.forName("com.gatf.export.Exporters").getMethod("ids");
        @SuppressWarnings("unchecked")
        Collection<String> exps = (Collection<String>) ids.invoke(null);
        for (String t : exps) if (!cliSection(md, "export").contains(t)) problems.add("cli.md: exporter " + t + " not listed");
        if (!problems.isEmpty()) fail(String.join("\n", problems));
    }

    static String cliSection(String md, String cmd) {
        int s = md.indexOf("\n### gatf " + cmd + "\n");
        assertTrue(s >= 0, "cli.md has no section for gatf " + cmd);
        int e = md.indexOf("\n### ", s + 5);
        return md.substring(s, e < 0 ? md.length() : e);
    }

    // ------------------------------------------------------------------------------------------------ snippets

    static final class Snip {
        String file, lang, meta, code;
        int line;

        @Override
        public String toString() {
            return file + ":" + line;
        }
    }

    static List<Snip> snippets() throws Exception {
        List<Snip> out = new ArrayList<>();
        for (Object o : (List<?>) gen.getMethod("snippets", Path.class).invoke(null, REPO)) {
            Snip s = new Snip();
            for (String f : new String[] { "file", "lang", "meta", "code" }) {
                Field fl = o.getClass().getField(f);
                String v = (String) fl.get(o);
                if (f.equals("file")) s.file = v; else if (f.equals("lang")) s.lang = v; else if (f.equals("meta")) s.meta = v; else s.code = v;
            }
            s.line = o.getClass().getField("line").getInt(o);
            out.add(s);
        }
        return out;
    }

    static boolean noCheck(Snip s) {
        return s.meta != null && s.meta.matches("(^|.*\\s)nocheck(\\s.*|$)");
    }

    /**
     * docs/seleasy/REFERENCE.md's per-statement blocks are syntax templates (`{placeholder}` lines, tab-indented from the first line,
     * with "Examples :-" and "where" sections): they are highlighted as sel but not scripts. Their examples are checked against the parser's own
     * samples by SeleasyDocsDriftTest. Its other sel blocks (Essentials, word forms) are parsed like any snippet.
     */
    static boolean syntaxTemplate(Snip s) {
        if (!s.file.equals("docs/seleasy/REFERENCE.md")) return false;
        for (String l : s.code.split("\n")) if (!l.isEmpty()) return l.startsWith("\t");   // templates start tab-indented
        return false;
    }

    /**
     * Every ```seleasy / ```sel block parses and transpiles through the real Command pipeline. Each page gets its own temp
     * project: blocks with a title="path" (seleasy, and properties/text files such as app.props) are written there first, so
     * multi-file examples (import, include, config) resolve; then every seleasy block is read like a script.
     */
    @Test
    void seleasySnippetsParse(@TempDir Path tmp) throws Exception {
        java.util.Map<String, List<Snip>> byPage = new java.util.LinkedHashMap<>();
        for (Snip s : snippets()) byPage.computeIfAbsent(s.file, k -> new ArrayList<>()).add(s);
        List<String> problems = new ArrayList<>();
        Set<String> skipped = new LinkedHashSet<>();
        int n = 0, pageNo = 0, templates = 0;
        for (java.util.Map.Entry<String, List<Snip>> page : byPage.entrySet()) {
            List<Snip> sel = new ArrayList<>();
            for (Snip s : page.getValue()) if ((s.lang.equals("seleasy") || s.lang.equals("sel")) && syntaxTemplate(s)) templates++;
                else if ((s.lang.equals("seleasy") || s.lang.equals("sel")) && !noCheck(s)) sel.add(s);
                else if ((s.lang.equals("seleasy") || s.lang.equals("sel")) && noCheck(s)) skipped.add(s.toString());
            if (sel.isEmpty()) continue;
            Path proj = tmp.resolve("p" + (++pageNo));
            Path tests = proj.resolve("tests");
            Files.createDirectories(tests);
            Files.copy(REPO.resolve("alldep-jar/src/test/resources/codegen-corpus/gatf-config.xml"), proj.resolve("gatf-config.xml"));
            for (Snip s : page.getValue()) {   // the page's named files: scripts, properties, data
                String t = title(s.meta);
                if (t == null || t.startsWith("gatf-config") || t.contains("..")) continue;
                Path f = tests.resolve(t).normalize();
                if (!f.startsWith(tests)) continue;
                Files.createDirectories(f.getParent());
                Files.write(f, s.code.getBytes(StandardCharsets.UTF_8));
            }
            GatfExecutorConfig config = Command.getConfig(proj.resolve("gatf-config.xml").toString(), proj.toString());
            config.setTestCasesBasePath(proj.toString());
            config.setOutFilesBasePath(proj.toString());
            AcceptanceTestContext ctx = new AcceptanceTestContext();
            ctx.setGatfExecutorConfig(config);
            ctx.validateAndInit(true, false);
            ctx.getWorkflowContextHandler().initializeSuiteContext(1);
            for (Snip s : sel) {
                n++;
                String t = title(s.meta);
                String name;
                if (t != null && !t.contains("..")) {
                    name = t;
                    boolean hasOpen = Pattern.compile("(?m)^\\s*open\\s").matcher(s.code).find();
                    if (!hasOpen) {   // a subtest library: parse a copy beside it with a session, so relative paths still resolve
                        Path lib = tests.resolve(t);
                        name = tests.relativize(lib.resolveSibling("check-" + n + "-" + lib.getFileName())).toString().replace('\\', '/');
                        Files.write(tests.resolve(name), ("open chrome\n" + s.code).getBytes(StandardCharsets.UTF_8));
                    }
                } else {
                    String code = s.code;
                    boolean hasOpen = Pattern.compile("(?m)^\\s*open\\s").matcher(code).find();
                    if (!hasOpen) code = "open chrome\n" + code;   // a fragment: give it a session so it transpiles like a script
                    name = "snippet-" + n + ".sel";
                    Files.write(tests.resolve(name), code.getBytes(StandardCharsets.UTF_8));
                }
                try {
                    Command cmd = Command.read(ctx.getResourceFile(name), new ArrayList<String>(), ctx);
                    String java = cmd.javacode();
                    if (java == null || java.isEmpty()) problems.add(s + ": transpiled to nothing");
                    else {
                        String err = compile(java, tmp.resolve("javac-" + n));
                        if (err != null) problems.add(s + ": generated Java does not compile: " + err);
                    }
                } catch (Throwable e) {
                    problems.add(s + ": " + reason(e));
                }
            }
        }
        System.out.println("[docs-drift] seleasy snippets checked: " + n + ", REFERENCE syntax templates: " + templates + (skipped.isEmpty() ? "" : ", nocheck: " + skipped));
        assertTrue(n >= 10, "expected the guides to carry seleasy snippets, found " + n);
        assertTrue(templates >= 90, "REFERENCE.md syntax blocks should be tagged sel, found " + templates);
        for (String sk : skipped) if (sk.startsWith("docs/guide/") || sk.startsWith("docs/how-to/")) problems.add(sk + ": nocheck is not allowed in guide/how-to pages");
        if (!problems.isEmpty()) fail(String.join("\n", problems));
    }

    /** Every ```json / ```xml block is well formed; configs and test cases bind strictly (unknown fields fail). */
    @Test
    void jsonAndXmlSnippetsAreValid() throws Exception {
        ObjectMapper om = WorkflowContextHandler.OM.copy().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        XmlMapper xom = (XmlMapper) WorkflowContextHandler.XOM.copy().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        List<String> problems = new ArrayList<>();
        int json = 0, xml = 0, typed = 0;
        for (Snip s : snippets()) {
            if (noCheck(s)) continue;
            try {
                if (s.lang.equals("json")) {
                    json++;
                    om.readTree(s.code);
                    String title = title(s.meta);
                    if (title != null && title.matches("(.*/)?gatf-config[^/]*\\.json")) { om.readValue(s.code, GatfExecutorConfig.class); typed++; }
                    else if (title != null && title.matches("tests/.*\\.json")) { om.readValue(s.code, new TypeReference<List<TestCase>>() { }); typed++; }
                    else if (title == null && Pattern.compile("^\\s*\\{[\\s\\S]*\"(testCaseDir|outFilesDir|isSeleniumExecutor|seleniumScripts|gatfTestDataConfig)\"\\s*:").matcher(s.code).find()) {
                        om.readValue(s.code, GatfExecutorConfig.class);   // an untitled block that is a config is still a config
                        typed++;
                    }
                } else if (s.lang.equals("xml")) {
                    xml++;
                    String root = xmlRoot(s.code);
                    if (root == null) continue;   // several roots (a fragment): well-formed check done in xmlRoot
                    if (root.equals("gatf-execute-config")) { xom.readValue(s.code, GatfExecutorConfig.class); typed++; }
                    else if (root.equals("TestCases")) { xom.readValue(s.code, new TypeReference<List<TestCase>>() { }); typed++; }
                    else if (root.equals("TestCase")) { xom.readValue(s.code, TestCase.class); typed++; }
                }
            } catch (Exception e) {
                problems.add(s + " (" + s.lang + "): " + String.valueOf(e.getMessage()).split("\n")[0]);
            }
        }
        System.out.println("[docs-drift] json " + json + ", xml " + xml + ", bound to the model " + typed);
        assertTrue(typed >= 5, "expected config/test-case snippets bound to the model, found " + typed);
        if (!problems.isEmpty()) fail(String.join("\n", problems));
    }

    /**
     * Compiles one transpiled script with the JDK compiler against the test classpath (what GATF does on a JDK), so a
     * snippet that transpiles into Java that cannot compile fails too. Null when it compiles, else the first error.
     */
    static String compile(String java, Path dir) throws Exception {
        Matcher pk = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;").matcher(java);
        Matcher cl = Pattern.compile("\\bpublic\\s+(?:final\\s+)?class\\s+(\\w+)").matcher(java);   // after the GATF_ST_CLASS_START_ marker
        if (!cl.find()) return "no public class in the generated code";
        Path src = dir.resolve("src");
        if (pk.find()) src = src.resolve(pk.group(1).replace('.', '/'));
        Files.createDirectories(src);
        Path f = src.resolve(cl.group(1) + ".java");
        Files.write(f, java.getBytes(StandardCharsets.UTF_8));
        Path out = dir.resolve("classes");
        Files.createDirectories(out);
        java.io.ByteArrayOutputStream err = new java.io.ByteArrayOutputStream();
        int rc = ToolProvider.getSystemJavaCompiler().run(null, null, err, "-nowarn", "-proc:none", "-encoding", "UTF-8",
                "-cp", System.getProperty("java.class.path"), "-d", out.toString(), f.toString());
        if (rc == 0) return null;
        for (String l : err.toString(StandardCharsets.UTF_8.name()).split("\n")) if (l.contains("error:")) return l.substring(l.indexOf("error:")).trim();
        return "javac exit " + rc;
    }

    /** The first non-blank message in the cause chain (the parser wraps its line-level error), with the parser's own detail. */
    static String reason(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof Command.GatfSelCodeParseError) {
                Command.GatfSelCodeParseError pe = (Command.GatfSelCodeParseError) t;
                if (pe.getAllErrors() != null && !pe.getAllErrors().isEmpty()) return reason(pe.getAllErrors().get(0));
                if (pe.getDetails() != null) return "parse error at line " + pe.getDetails()[1] + ": " + pe.getDetails()[0]
                        + (pe.getMessage() == null || pe.getMessage().isBlank() ? "" : " (" + pe.getMessage().trim().split("\n")[0] + ")");
            }
        }
        for (Throwable t = e; t != null; t = t.getCause()) {
            String m = t.getMessage();
            if (m != null && !m.isBlank()) return t.getClass().getSimpleName() + ": " + m.trim().split("\n")[0];
        }
        return e.getClass().getName();
    }

    static String title(String meta) {
        Matcher m = Pattern.compile("title=\"([^\"]+)\"").matcher(meta == null ? "" : meta);
        return m.find() ? m.group(1) : null;
    }

    /** The root element's name, or null for a multi-root fragment; throws when the XML is not well formed. */
    static String xmlRoot(String code) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        try {
            Document d = f.newDocumentBuilder().parse(new InputSource(new java.io.StringReader(code)));
            return d.getDocumentElement().getTagName();
        } catch (org.xml.sax.SAXParseException e) {
            if (!String.valueOf(e.getMessage()).contains("markup in the document following the root element")) throw e;
            f.newDocumentBuilder().parse(new InputSource(new java.io.StringReader("<snippet>" + code.replaceFirst("^\\s*<\\?xml[^>]*\\?>", "") + "</snippet>")));
            return null;
        }
    }

    @Test
    void snippetCheckerCatchesBadSnippets() throws Exception {
        // a strict bind must reject an unknown TestCase field, and a broken seleasy line must not parse
        XmlMapper xom = (XmlMapper) WorkflowContextHandler.XOM.copy().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        try {
            xom.readValue("<TestCases><TestCase><url>x</url><expectedStatus>200</expectedStatus></TestCase></TestCases>", new TypeReference<List<TestCase>>() { });
            fail("unknown field expectedStatus was accepted");
        } catch (Exception expected) {
            // ok
        }
        try {
            xmlRoot("<a><b></a>");
            fail("malformed XML was accepted");
        } catch (org.xml.sax.SAXParseException expected) {
            // ok
        }
        assertEquals(null, xmlRoot("<a/><b/>"));
        assertEquals("TestCases", xmlRoot("<TestCases/>"));
    }

    static String read(String rel) throws Exception {
        return new String(Files.readAllBytes(REPO.resolve(rel)), StandardCharsets.UTF_8);
    }
}
