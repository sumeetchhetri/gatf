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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sun.net.httpserver.HttpServer;

/**
 * Δ161 — "compiles and runs" for the Selenium Java exporter.
 * <ul>
 * <li>Every exported golden in {@code export-corpus/selenium-java} is compiled in-process (javax.tools,
 * {@code --release 11}, {@code -Xlint:all}) against the test classpath (Selenium + JUnit 5): no errors.</li>
 * <li>The {@code e2e-*} goldens then run, each in a child JVM (so {@code GATF_BASEURL} is a real environment
 * variable), against the Δ160 fixture page ({@code e2e/export/fixture}, served here on a free port) in headless
 * Chromium. The child calls the generated {@code openBrowser / test / closeBrowser} methods in JUnit's order.
 * Browser setup comes from the usual places: Selenium Manager, or {@code -Dwebdriver.chrome.driver},
 * {@code -Dgatf.export.browser.binary}; {@code -Dgatf.export.browser.args} replaces the default
 * {@code --headless=new,--no-sandbox,--disable-dev-shm-usage}. When no browser can be started the run is skipped
 * (assumption), unless {@code -Dgatf.export.selenium.require=true}; {@code -Dgatf.export.selenium.run=false} skips
 * it outright.</li>
 * </ul>
 */
class ExportSeleniumCompileTest {

    static final Path DIR = SeleniumJavaGoldenTest.DIR;
    static final Path FIXTURE = Repo.path("e2e/export/fixture");
    static final String NO_BROWSER = "GATF-EXPORT-NO-BROWSER: ";

    @TempDir
    Path tmp;

    /** The test classpath, with a surefire manifest-only booter jar expanded into its Class-Path entries. */
    static List<String> classpath() throws Exception {
        String cp = System.getProperty("surefire.test.class.path");
        if (cp == null || cp.trim().isEmpty()) cp = System.getProperty("java.class.path");
        Set<String> out = new LinkedHashSet<String>();
        for (String e : cp.split(File.pathSeparator)) {
            if (e.isEmpty()) continue;
            out.add(e);
            File f = new File(e);
            if (!f.isFile() || !e.endsWith(".jar")) continue;
            try (JarFile jar = new JarFile(f)) {
                Manifest m = jar.getManifest();
                String more = m == null ? null : m.getMainAttributes().getValue("Class-Path");
                if (more == null) continue;
                for (String u : more.trim().split("\\s+")) {
                    if (u.isEmpty()) continue;
                    URI uri = f.getParentFile().toURI().resolve(u);
                    out.add("file".equals(uri.getScheme()) ? new File(uri).getPath() : u);
                }
            }
        }
        return new ArrayList<String>(out);
    }

    static List<Path> goldens() throws Exception {
        try (Stream<Path> s = Files.list(DIR)) {
            return s.filter(p -> p.toString().endsWith(".java")).sorted().collect(Collectors.toList());
        }
    }

    /** Compiles the goldens (plus {@code extra} sources) into {@code out}; fails with javac's diagnostics. */
    static String compile(List<Path> sources, Path out) throws Exception {
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        assertTrue(javac != null, "no system Java compiler (run the tests on a JDK)");
        StringWriter diag = new StringWriter();
        List<String> options = Arrays.asList("-encoding", "UTF-8", "--release", "11", "-Xlint:all", "-proc:none", "-implicit:none",
                "-d", out.toString(), "-cp", String.join(File.pathSeparator, classpath()));
        try (StandardJavaFileManager fm = javac.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
            List<File> files = new ArrayList<File>();
            for (Path p : sources) files.add(p.toFile());
            Iterable<? extends JavaFileObject> units = fm.getJavaFileObjectsFromFiles(files);
            boolean ok = javac.getTask(diag, fm, null, options, null, units).call();
            if (!ok) fail("exported Selenium tests do not compile:\n" + diag);
        }
        return diag.toString();
    }

    @Test
    void everyExportedGoldenCompiles() throws Exception {
        List<Path> g = goldens();
        assertTrue(g.size() >= 13, "selenium-java goldens: " + g.size() + " (run SeleniumJavaGoldenTest first to write missing ones)");
        String warnings = compile(g, Files.createDirectories(tmp.resolve("classes")));
        if (!warnings.trim().isEmpty()) System.out.println("[export-compile] javac warnings:\n" + warnings);
        for (Path p : g) {
            String cls = p.getFileName().toString().replaceAll("\\.java$", "");
            assertTrue(Files.exists(tmp.resolve("classes").resolve(cls + ".class")), "no class for " + p);
        }
    }

    /** Not only the goldens: the export of every .sel in the repo compiles too (each under a unique class name). */
    @Test
    void everyRepoScriptExportCompiles() throws Exception {
        List<Path> all = new ArrayList<Path>();
        all.addAll(ExportGoldenTest.sels(DIR));
        all.addAll(ExportGoldenTest.sels(ExportGoldenTest.DIR));
        all.addAll(ExportGoldenTest.sels(Repo.path("alldep-jar/src/test/resources/codegen-corpus")));
        all.addAll(ExportGoldenTest.sels(Repo.path("sample-project")));
        all.addAll(ExportGoldenTest.sels(Repo.path("dogfood")));
        Path src = Files.createDirectories(tmp.resolve("repo-src"));
        List<Path> sources = new ArrayList<Path>();
        int i = 0;
        for (Path sel : all) {
            ExportResult r;
            try {
                r = Exporters.export(SeleniumJavaExporter.ID, "repo-" + (i++), ExportGoldenTest.read(sel), new ExportOptions());
            } catch (ExportException e) {
                continue;
            }
            Path f = src.resolve(r.fileName);
            Files.write(f, ("// " + Jv.comment(sel.toString()) + "\n" + r.content).getBytes(StandardCharsets.UTF_8));
            sources.add(f);
        }
        assertTrue(sources.size() > 20, "exports: " + sources.size());
        compile(sources, Files.createDirectories(tmp.resolve("repo-classes")));
    }

    @Test
    void e2eGoldensRunAgainstTheFixturePage() throws Exception {
        assumeTrue(!"false".equals(System.getProperty("gatf.export.selenium.run")), "-Dgatf.export.selenium.run=false");
        boolean require = Boolean.getBoolean("gatf.export.selenium.require");
        List<String> e2e = new ArrayList<String>();
        for (Path sel : ExportGoldenTest.sels(DIR)) {
            String name = sel.getFileName().toString();
            if (name.startsWith("e2e-")) e2e.add(Jv.className(name.replaceAll("\\.sel$", "")));
        }
        assertTrue(e2e.size() >= 4, "e2e scripts: " + e2e);
        Path classes = Files.createDirectories(tmp.resolve("classes"));
        Path runner = tmp.resolve("GatfExportRunMain.java");
        Files.write(runner, RUNNER.getBytes(StandardCharsets.UTF_8));
        List<Path> sources = new ArrayList<Path>(goldens());
        sources.add(runner);
        compile(sources, classes);

        HttpServer server = fixtureServer();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            List<String> failures = new ArrayList<String>(), noBrowser = new ArrayList<String>();
            for (String cls : e2e) {
                List<String> cmd = new ArrayList<String>();
                cmd.add(Paths.get(System.getProperty("java.home"), "bin", "java").toString());
                cmd.add("-Dgatf.export.browser.args=" + System.getProperty("gatf.export.browser.args", "--headless=new,--no-sandbox,--disable-dev-shm-usage"));
                for (String p : new String[] { "gatf.export.browser.binary", "webdriver.chrome.driver", "webdriver.chrome.disableBuildCheck" }) {
                    if (System.getProperty(p) != null) cmd.add("-D" + p + "=" + System.getProperty(p));
                }
                cmd.add("-cp");
                cmd.add(classes + File.pathSeparator + String.join(File.pathSeparator, classpath()));
                cmd.add("GatfExportRunMain");
                cmd.add(cls);
                ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
                Map<String, String> env = pb.environment();
                env.put("GATF_BASEURL", base);
                env.remove("SELENIUM_REMOTE_URL");   // the fixture is on 127.0.0.1: a Grid could not reach it
                Process p = pb.start();
                ByteArrayOutputStream log = new ByteArrayOutputStream();
                Thread pump = new Thread(() -> copy(p.getInputStream(), log));
                pump.start();
                boolean done = p.waitFor(180, TimeUnit.SECONDS);
                if (!done) p.destroyForcibly();
                pump.join(5000);
                String text = new String(log.toByteArray(), StandardCharsets.UTF_8);
                System.out.println("[export-e2e] " + cls + ": " + (done ? "exit " + p.exitValue() : "timed out"));
                if (done && p.exitValue() == 0) continue;
                if (done && p.exitValue() == 3) noBrowser.add(cls + ": " + text.substring(text.indexOf(NO_BROWSER) + NO_BROWSER.length()).trim());
                else failures.add(cls + (done ? " (exit " + p.exitValue() + ")" : " (timed out)") + ":\n" + tail(text, 4000));
            }
            if (!failures.isEmpty()) fail("exported Selenium tests failed against the fixture page:\n" + String.join("\n", failures));
            if (!noBrowser.isEmpty()) {
                String why = "no browser could be started for the exported Selenium tests:\n" + String.join("\n", noBrowser);
                if (require) fail(why);
                assumeTrue(noBrowser.size() < e2e.size(), why);
                fail(why);   // some ran, some could not start: not a setup problem
            }
        } finally {
            server.stop(0);
        }
    }

    private static HttpServer fixtureServer() throws Exception {
        HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        Path root = FIXTURE.toAbsolutePath().normalize();
        s.createContext("/", ex -> {
            String path = ex.getRequestURI().getPath().replaceAll("^/+", "");
            Path f = root.resolve(path.isEmpty() ? "index.html" : path).normalize();
            byte[] body;
            int code = 200;
            if (!f.startsWith(root) || !Files.isRegularFile(f)) {
                body = "not found".getBytes(StandardCharsets.UTF_8);
                code = 404;
            } else {
                body = Files.readAllBytes(f);
                ex.getResponseHeaders().add("Content-Type", f.toString().endsWith(".html") ? "text/html; charset=utf-8" : "application/octet-stream");
            }
            ex.sendResponseHeaders(code, body.length);
            try (OutputStream o = ex.getResponseBody()) {
                o.write(body);
            }
        });
        s.start();
        return s;
    }

    private static void copy(InputStream in, ByteArrayOutputStream out) {
        byte[] buf = new byte[8192];
        try {
            for (int n; (n = in.read(buf)) > 0; ) {
                synchronized (out) {
                    out.write(buf, 0, n);
                }
            }
        } catch (Exception e) {
            // the process ended
        }
    }

    private static String tail(String s, int max) {
        return s.length() <= max ? s : "..." + s.substring(s.length() - max);
    }

    /** The child-JVM runner: the generated lifecycle methods, in JUnit's order. Exit 3 = no browser could be started. */
    static final String RUNNER = String.join("\n",
            "import java.lang.reflect.InvocationTargetException;",
            "import java.lang.reflect.Method;",
            "",
            "public class GatfExportRunMain {",
            "    public static void main(String[] args) throws Exception {",
            "        Class<?> c = Class.forName(args[0]);",
            "        java.lang.reflect.Constructor<?> k = c.getDeclaredConstructor();",
            "        k.setAccessible(true);",
            "        Object t = k.newInstance();",
            "        Method open = c.getDeclaredMethod(\"openBrowser\"), test = c.getDeclaredMethod(\"test\"), close = c.getDeclaredMethod(\"closeBrowser\");",
            "        open.setAccessible(true);",
            "        test.setAccessible(true);",
            "        close.setAccessible(true);",
            "        try {",
            "            open.invoke(t);",
            "        } catch (InvocationTargetException e) {",
            "            System.out.println(\"" + NO_BROWSER + "\" + e.getCause());",
            "            System.exit(3);",
            "        }",
            "        Throwable err = null;",
            "        try {",
            "            test.invoke(t);",
            "        } catch (InvocationTargetException e) {",
            "            err = e.getCause();",
            "        }",
            "        try {",
            "            close.invoke(t);",
            "        } catch (InvocationTargetException e) {",
            "            if (err == null) err = e.getCause(); else err.addSuppressed(e.getCause());",
            "        }",
            "        if (err != null) {",
            "            err.printStackTrace(System.out);",
            "            System.exit(1);",
            "        }",
            "        System.out.println(\"GATF-EXPORT-PASS \" + args[0]);",
            "        System.exit(0);",
            "    }",
            "}",
            "");
}
