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
package com.gatf.mcp;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatf.crawler.CrawlSession;
import com.gatf.crawler.CrawlSpec;
import com.gatf.crawler.SeleniumCrawlBrowser;
import com.gatf.crawler.SpaCrawler;
import com.gatf.record.RecordSession;
import com.gatf.selenium.Command;
import com.gatf.ui.GatfCrawlHandler;

/**
 * M14: the GATF tools behind {@code gatf mcp}. Everything is relative to the project root; paths that escape it are
 * refused. {@code run} executes {@code gatf test} in a child JVM on the same classpath — a run ends in System.exit and
 * leaves static run state (filters, overrides, console) behind, so an in-process run could not be repeated safely.
 */
public final class GatfMcpTools {

    static final int RUN_TAIL_LINES = 120;

    private final Path root;
    private final ObjectMapper om;
    private final PrintStream log;
    private final Map<String, RecordSession> records = new ConcurrentHashMap<String, RecordSession>();
    private final Map<String, CrawlSession> crawls = new ConcurrentHashMap<String, CrawlSession>();

    public GatfMcpTools(Path root, ObjectMapper om, PrintStream log) {
        this.root = root.toAbsolutePath().normalize();
        this.om = om;
        this.log = log;
    }

    public static final String INSTRUCTIONS =
            "GATF test automation (UI seleasy .sel scripts + API XML test cases). Debug loop: list_tests → run (grep to narrow) → "
          + "get_report (failed tests, errors, trace zips) → summarise_trace on a trace zip (last actions, console errors, failed "
          + "requests, checks) → edit the .sel → lint → run again. record/crawl produce new .sel scripts. heal proposes fixes for a failed step from its trace (never writes).";

    public McpServer register(McpServer s) {
        s.register(McpTool.of("list_tests", "List the project's tests: seleasy scripts (ui, with @tags and subtests) and API test-case XML (api).",
                McpTool.schema("dir", "string", "sub-directory of the project to list (default: the project root)",
                        "kind", "enum:ui|api", "only this kind",
                        "grep", "string", "case-insensitive substring of the path or a tag",
                        "limit", "integer", "max entries (default 200)"),
                a -> TestLister.list(inRoot(McpTool.str(a, "dir") == null ? "." : McpTool.str(a, "dir")), McpTool.str(a, "kind"),
                        McpTool.str(a, "grep"), McpTool.num(a, "limit", 200, 1, 5000))));

        s.register(McpTool.of("lint", "Parse and compile a seleasy script without running it. Returns {status: SUCCESS|FAILURE, error}.",
                McpTool.schema("file*", "string", "the .sel file, relative to the project root",
                        "config", "string", "config file name in the project root (default gatf-config.json, else gatf-config.xml)"),
                this::lint));

        s.register(McpTool.of("run", "Run tests with `gatf test` and return the exit code, the console tail and the failed tests. "
                        + "Narrow with grep/tag; turn on trace to get trace zips for summarise_trace.",
                McpTool.schema("config", "string", "config file (default gatf-config.json, else gatf-config.xml)",
                        "grep", "string[]", "include tests whose title matches any regex",
                        "grepInvert", "string[]", "exclude tests whose title matches any regex",
                        "tag", "string[]", "include tests with any of these @tags",
                        "trace", "enum:off|on|retain-on-failure|on-first-retry", "trace mode for UI runs",
                        "retries", "integer", "retries per failed test",
                        "workers", "integer", "parallel workers",
                        "shard", "string", "i/n",
                        "updateSnapshots", "boolean", "rewrite screenshot baselines",
                        "reporter", "string", "reporters (json is always added)",
                        "timeoutSec", "integer", "kill the run after this many seconds (default 1800)"),
                this::run));

        s.register(McpTool.of("get_report", "Read the newest run results (gatf-results.json): summary + matching tests with errors, tags and attachments.",
                McpTool.schema("dir", "string", "directory holding gatf-results.json, or a sub-directory to search (default: the project root)",
                        "status", "string", "failed (default: failed/flaky/soft failures), all, passed, skipped, ...",
                        "grep", "string", "case-insensitive substring of the suite/name/title",
                        "limit", "integer", "max tests (default 50)"),
                this::report));

        s.register(McpTool.of("summarise_trace", "Condense a trace (*-trace.zip or its directory): last actions, console errors, failed and slowest "
                        + "requests, passive checks, web vitals, screenshots.",
                McpTool.schema("path*", "string", "the trace zip or directory (absolute, or relative to the project root)",
                        "limit", "integer", "entries per section (default 10)"),
                a -> TraceSummary.summarise(anyPath(McpTool.req(a, "path")), McpTool.num(a, "limit", 10, 1, 200), om)));

        s.register(McpTool.of("record", "Record a seleasy script from a visible local browser the user drives. "
                        + "action=start (url, browser) → sessionId; steps → lines so far; stop (saveAs) → the script, optionally saved and linted.",
                McpTool.schema("action*", "enum:start|steps|stop", "what to do",
                        "url", "string", "start URL (start)",
                        "browser", "enum:chrome|firefox|edge", "browser (start; default chrome)",
                        "sessionId", "string", "the session (steps/stop)",
                        "saveAs", "string", ".sel path relative to the project root (stop)"),
                this::record));

        s.register(McpTool.of("crawl", "Crawl a web app in a browser and generate a starter seleasy suite + coverage plan. "
                        + "action=start (seed, …) → sessionId; status; result (saveAs) → script + coverage; stop.",
                McpTool.schema("action*", "enum:start|status|result|stop", "what to do",
                        "seed", "string", "start URL (start)",
                        "maxPages", "integer", "page budget (start; default 25)",
                        "maxDepth", "integer", "link depth (start; default 3)",
                        "headless", "boolean", "headless browser (start; default true)",
                        "browser", "enum:chrome|firefox|edge", "browser (start)",
                        "exclude", "string", "regex of URLs never to visit (start)",
                        "loginUrl", "string", "credentialed crawl: login page (start)",
                        "username", "string", "credentialed crawl (start)",
                        "password", "string", "credentialed crawl (start)",
                        "sessionId", "string", "the session (status/result/stop)",
                        "saveAs", "string", ".sel path relative to the project root (result)"),
                this::crawl));

        // H1 (Δ150): heal a failed step from its trace; read-only
        s.register(new HealTool(root, om, this::healAi).tool());
        return s;
    }

    /** Δ150: the AI client for one heal, from the config's {@code <ai>} block; any gap gives a not-configured client. */
    com.gatf.ai.AiClient healAi(String config) {
        String name;
        try {
            name = configName(config);
        } catch (McpTool.ToolError e) {
            return com.gatf.ai.AiClient.notConfigured("config path escapes the project root");
        }
        Path cf = root.resolve(name).normalize();
        com.gatf.ai.AiConfig ai;
        try (java.io.InputStream in = Files.newInputStream(cf)) {
            com.gatf.executor.core.GatfExecutorConfig c = com.gatf.executor.core.GatfTestCaseExecutorUtil.getConfig(in, name.toLowerCase(java.util.Locale.ROOT).endsWith(".xml"));
            ai = c == null ? null : c.getAi();
        } catch (Exception e) {
            return com.gatf.ai.AiClient.notConfigured("config " + root.relativize(cf) + " could not be read");
        }
        File plugins = new File(root.toFile(), "plugins");
        com.gatf.selenium.plugins.sdk.PluginRegistry.load(plugins.isDirectory() ? plugins : null, GatfMcpTools.class.getClassLoader());
        return com.gatf.ai.heal.HealAi.open(ai, cf.getParent(), com.gatf.selenium.plugins.sdk.PluginRegistry.classLoader(), System::getenv, null, null);
    }

    // ---- tools ----

    Object lint(Map<String, Object> a) throws Exception {
        Path f = inRoot(McpTool.req(a, "file"));
        if (!Files.isRegularFile(f)) throw new McpTool.ToolError("no such file: " + root.relativize(f));
        String rel = "." + File.separator + root.relativize(f).toString();
        String res = Command.validateSel(new String[] { "-validate-sel", rel, configName(McpTool.str(a, "config")), root.toString() }, null, false);
        @SuppressWarnings("unchecked")
        Map<String, Object> m = om.readValue(res, Map.class);
        m.put("file", root.relativize(f).toString().replace('\\', '/'));
        return m;
    }

    Object run(Map<String, Object> a) throws Exception {
        List<String> gatfArgs = RunArgs.build(a);
        int timeout = McpTool.num(a, "timeoutSec", 1800, 10, 24 * 3600);
        List<String> cmd = new ArrayList<String>();
        cmd.add(Paths.get(System.getProperty("java.home"), "bin", "java").toString());
        cmd.addAll(RunArgs.jvmFlags(java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments()));
        cmd.add("-cp");
        cmd.add(System.getProperty("java.class.path"));
        cmd.add("com.gatf.generator.core.GatfCli");
        cmd.addAll(gatfArgs);
        long start = System.currentTimeMillis() - 2000;   // coarse file mtimes
        ProcessBuilder pb = new ProcessBuilder(cmd).directory(root.toFile()).redirectErrorStream(true);
        Process p = pb.start();
        final Deque<String> tail = new ArrayDeque<String>();
        final String[] resultsLine = { null };
        Thread pump = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    log.println(line);                    // the user sees the run on the server's stderr
                    String rp = RunArgs.resultsPath(line);
                    if (rp != null) resultsLine[0] = rp;
                    synchronized (tail) {
                        tail.addLast(line);
                        while (tail.size() > RUN_TAIL_LINES) tail.removeFirst();
                    }
                }
            } catch (Exception ignore) { /* process ended */ }
        }, "gatf-mcp-run");
        pump.setDaemon(true);
        pump.start();
        boolean done = p.waitFor(timeout, TimeUnit.SECONDS);
        if (!done) {
            p.descendants().forEach(ProcessHandle::destroyForcibly);   // drivers and browsers the run started
            p.destroyForcibly().waitFor(10, TimeUnit.SECONDS);
        }
        pump.join(5000);
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("command", "gatf " + String.join(" ", gatfArgs));
        out.put("exitCode", done ? p.exitValue() : null);
        out.put("passed", done && p.exitValue() == 0);
        if (!done) out.put("timedOut", timeout);
        // the run prints where it wrote its results ("JSON:   <path>"); walking the project is only the fallback
        Path res = resultsLine[0] != null && Files.isRegularFile(Paths.get(resultsLine[0])) ? Paths.get(resultsLine[0]) : RunResults.latest(root, start);
        if (res != null) {
            out.put("report", RunResults.summarise(om.readTree(res.toFile()), res, "failed", null, 20, om));
        } else {
            out.put("report", null);
        }
        synchronized (tail) { out.put("output", new ArrayList<String>(tail)); }
        return out;
    }

    Object report(Map<String, Object> a) throws Exception {
        Path d = anyPath(McpTool.str(a, "dir") == null ? "." : McpTool.str(a, "dir"));
        Path res = Files.isRegularFile(d) ? d
                : Files.isRegularFile(d.resolve(RunResults.FILE)) ? d.resolve(RunResults.FILE)
                : RunResults.latest(d, 0);
        if (res == null) throw new McpTool.ToolError("no " + RunResults.FILE + " under " + d + " — run the tests first");
        return RunResults.summarise(om.readTree(res.toFile()), res, McpTool.str(a, "status"), McpTool.str(a, "grep"),
                McpTool.num(a, "limit", 50, 1, 5000), om);
    }

    Object record(Map<String, Object> a) throws Exception {
        String action = McpTool.req(a, "action");
        if (action.equals("start")) {
            String url = McpTool.req(a, "url");
            if (!url.matches("(?i)https?://.+")) throw new McpTool.ToolError("url must be an http(s) URL");
            String id = UUID.randomUUID().toString();
            RecordSession s = new RecordSession(id, url, McpTool.str(a, "browser"), null);
            s.start();
            records.put(id, s);
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("sessionId", id);
            m.put("running", true);
            m.put("hint", "the user drives the " + s.getBrowser() + " window; call steps to follow, stop to finish");
            return m;
        }
        RecordSession s = records.get(McpTool.req(a, "sessionId"));
        if (s == null) throw new McpTool.ToolError("no such recording session");
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        if (action.equals("steps")) {
            m.put("running", s.isRunning());
            m.put("lines", s.currentLines());
            if (s.getError() != null) m.put("error", s.getError());
        } else if (action.equals("stop")) {
            s.stop();
            records.remove(s.getId());
            String sel = s.toSel();
            m.put("content", sel);
            save(a, sel, m);
        } else {
            throw new McpTool.ToolError("unknown action " + action);
        }
        return m;
    }

    Object crawl(Map<String, Object> a) throws Exception {
        String action = McpTool.req(a, "action");
        if (action.equals("start")) {
            int running = 0;
            for (CrawlSession c : crawls.values()) if (c.isRunning()) running++;
            if (running >= 2) throw new McpTool.ToolError("two crawls are already running");
            CrawlSpec spec = GatfCrawlHandler.specFrom(a);
            String id = UUID.randomUUID().toString();
            CrawlSession c = new CrawlSession(id, spec);
            crawls.put(id, c);
            c.start(new CrawlSession.BrowserFactory() {
                public SpaCrawler.Browser open(CrawlSpec sp) throws Exception {
                    return SeleniumCrawlBrowser.create(sp.isHeadless(), sp.getBrowser());
                }
            });
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("sessionId", id);
            return m;
        }
        CrawlSession c = crawls.get(McpTool.req(a, "sessionId"));
        if (c == null) throw new McpTool.ToolError("no such crawl session");
        if (action.equals("status")) return c.status();
        if (action.equals("stop")) { c.stop(); return c.status(); }
        if (action.equals("result")) {
            if (c.content() == null) throw new McpTool.ToolError(c.isRunning() ? "crawl still running — poll status" : "crawl produced no result");
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("content", c.content());
            m.put("coverage", c.plan());
            if (save(a, c.content(), m) != null && c.plan() != null) {
                Path sel = inRootForWrite(McpTool.str(a, "saveAs"));
                Path cov = sel.resolveSibling(sel.getFileName().toString().replaceAll("\\.sel$", "") + "-coverage.json");
                Files.write(cov, om.writerWithDefaultPrettyPrinter().writeValueAsBytes(c.plan()));
                m.put("coveragePath", root.relativize(cov).toString().replace('\\', '/'));
            }
            return m;
        }
        throw new McpTool.ToolError("unknown action " + action);
    }

    /** Stops recordings and crawls still open when the client goes away (each holds a browser). */
    public void close() {
        for (RecordSession s : records.values()) { try { s.stop(); } catch (Throwable ignore) { /* best effort */ } }
        for (CrawlSession c : crawls.values()) { try { c.stop(); } catch (Throwable ignore) { /* best effort */ } }
        records.clear();
        crawls.clear();
    }

    /** Writes {@code content} to {@code saveAs} (when given) and lints it; returns the saved path or null. */
    private Path save(Map<String, Object> a, String content, Map<String, Object> m) throws Exception {
        String saveAs = McpTool.str(a, "saveAs");
        if (saveAs == null) return null;
        if (!saveAs.endsWith(".sel")) saveAs += ".sel";
        Path f = inRootForWrite(saveAs);
        Files.createDirectories(f.getParent());
        Files.write(f, content.getBytes(StandardCharsets.UTF_8));
        m.put("savedPath", root.relativize(f).toString().replace('\\', '/'));
        try {
            Map<String, Object> la = new LinkedHashMap<String, Object>();
            la.put("file", saveAs);
            m.put("validation", lint(la));
        } catch (Throwable t) {
            m.put("validation", "unavailable: " + (t.getMessage() == null ? t.toString() : t.getMessage()));
        }
        return f;
    }

    // ---- paths ----

    /** A path inside the project root; anything that escapes it is refused. */
    Path inRoot(String rel) {
        Path p = root.resolve(rel).normalize();
        if (!p.startsWith(root)) throw new McpTool.ToolError("path escapes the project root: " + rel);
        return p;
    }

    /** {@link #inRoot}, and the nearest existing ancestor must resolve (symlinks followed) inside the real root too. */
    Path inRootForWrite(String rel) throws java.io.IOException {
        Path p = inRoot(rel);
        Path realRoot = root.toRealPath();
        Path anc = p;
        while (anc != null && !Files.exists(anc)) anc = anc.getParent();
        if (anc == null || !anc.toRealPath().startsWith(realRoot)) throw new McpTool.ToolError("path leaves the project root (symlink): " + rel);
        return p;
    }

    /** Absolute paths are allowed for read-only tools (reports and traces may live outside the project). */
    Path anyPath(String p) {
        Path x = Paths.get(p);
        return x.isAbsolute() ? x.normalize() : inRoot(p);
    }

    String configName(String given) {
        if (given != null) {
            inRoot(given);
            return given;
        }
        return Files.isRegularFile(root.resolve("gatf-config.json")) ? "gatf-config.json" : "gatf-config.xml";
    }
}
