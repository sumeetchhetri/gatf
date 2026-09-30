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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;

/** M14: the pure readers behind list_tests, get_report, summarise_trace and run. */
class McpReadersTest {

    private final ObjectMapper om = new ObjectMapper();

    private static void write(Path p, String s) throws Exception {
        Files.createDirectories(p.getParent());
        Files.write(p, s.getBytes(StandardCharsets.UTF_8));
    }

    private static final String HAR = "{\"log\":{\"entries\":["
            + "{\"request\":{\"method\":\"GET\",\"url\":\"https://a/app\"},\"response\":{\"status\":200},\"time\":120},"
            + "{\"request\":{\"method\":\"POST\",\"url\":\"https://a/api/save\"},\"response\":{\"status\":500},\"time\":900},"
            + "{\"request\":{\"method\":\"GET\",\"url\":\"https://a/x.js\"},\"response\":{\"status\":0},\"time\":5,\"_failed\":\"net::ERR_ABORTED\"}]}}";

    private static Map<String, String> traceFiles() {
        Map<String, String> f = new LinkedHashMap<String, String>();
        f.put("metadata.json", "{\"runId\":\"r1\",\"selFile\":\"login.sel\",\"browser\":\"chrome\",\"protocol\":\"cdp\",\"startTs\":1000,\"endTs\":4000}");
        f.put("events.ndjson", "{\"ts\":1100,\"type\":\"lifecycle\",\"name\":\"start\",\"detail\":null}\n"
                + "{\"ts\":1300,\"type\":\"action\",\"name\":\"type\",\"detail\":\"id@user\"}\n"
                + "{\"ts\":1200,\"type\":\"action\",\"name\":\"click\",\"detail\":\"css@#login\"}\n"
                + "{\"ts\":1400,\"type\":\"action\",\"name\":\"click\",\"detail\":\"Element not found: css@#save\"}\n{\"ts\":15");
        f.put("console.ndjson", "{\"ts\":1,\"level\":\"log\",\"text\":\"hi\"}\n{\"ts\":2,\"level\":\"error\",\"text\":\"TypeError: x is undefined\",\"url\":\"https://a/app.js\",\"line\":12}\n");
        f.put("network.har", HAR);
        f.put("checks.json", "{\"budgets\":[{\"metric\":\"lcp\"}],\"findings\":[1,2],\"failures\":[\"lcp 3100 > 2500\"]}");
        f.put("performance.json", "{\"webVitals\":{\"lcp\":3100,\"cls\":0.01}}");
        f.put("screenshots/failure.png", "png");
        return f;
    }

    @Test
    void traceSummaryFromZipAndDirectoryAgree(@TempDir Path tmp) throws Exception {
        Path zip = tmp.resolve("t-trace.zip");
        try (ZipOutputStream z = new ZipOutputStream(Files.newOutputStream(zip))) {
            for (Map.Entry<String, String> e : traceFiles().entrySet()) {
                z.putNextEntry(new ZipEntry(e.getKey()));
                z.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                z.closeEntry();
            }
        }
        Path dir = tmp.resolve("unzipped");
        for (Map.Entry<String, String> e : traceFiles().entrySet()) write(dir.resolve(e.getKey()), e.getValue());

        for (Path p : Arrays.asList(zip, dir)) {
            Map<String, Object> s = TraceSummary.summarise(p, 2, om);
            String j = om.writeValueAsString(s);
            assertEquals("login.sel", om.readTree(j).at("/metadata/selFile").asText(), j);
            assertEquals(3000, om.readTree(j).at("/metadata/durationMs").asInt());
            assertEquals(3, om.readTree(j).at("/actions/count").asInt());
            // last two actions, ordered by ts; the torn last line is skipped
            assertEquals("type", om.readTree(j).at("/actions/last/0/name").asText());
            assertEquals("Element not found: css@#save", om.readTree(j).at("/actions/last/1/detail").asText());
            assertEquals(1, om.readTree(j).at("/actions/errors").size());
            assertEquals(1, om.readTree(j).at("/console/levels/error").asInt());
            assertEquals("https://a/app.js:12", om.readTree(j).at("/console/problems/0/at").asText());
            assertEquals(3, om.readTree(j).at("/network/requests").asInt());
            assertEquals(2, om.readTree(j).at("/network/failed").size());
            assertEquals(500, om.readTree(j).at("/network/failed/0/status").asInt());
            assertEquals(900, om.readTree(j).at("/network/slowest/0/timeMs").asInt());
            assertEquals(1, om.readTree(j).at("/checks/budgetsCount").asInt());
            assertEquals("lcp 3100 > 2500", om.readTree(j).at("/checks/failures/0").asText());
            assertEquals(3100, om.readTree(j).at("/webVitals/lcp").asInt());
            assertEquals("screenshots/failure.png", om.readTree(j).at("/files/screenshots/0").asText());
            assertFalse(om.readTree(j).at("/files/video").asBoolean());
        }
    }

    @Test
    void traceSummaryRejectsMissingAndNonZip(@TempDir Path tmp) throws Exception {
        try { TraceSummary.summarise(tmp.resolve("none.zip"), 5, om); throw new AssertionError(); }
        catch (McpTool.ToolError e) { assertTrue(e.getMessage().contains("not found")); }
        Path bad = tmp.resolve("bad.zip");
        write(bad, "not a zip");
        try { TraceSummary.summarise(bad, 5, om); throw new AssertionError(); }
        catch (McpTool.ToolError e) { assertTrue(e.getMessage().contains("not a trace zip")); }
    }

    private static final String RESULTS = "{\"dir\":\"out\",\"summary\":{\"total\":4,\"passed\":1,\"failed\":1,\"flaky\":1},\"tests\":["
            + "{\"suite\":\"a.sel\",\"name\":\"a\",\"title\":\"a.sel › a\",\"status\":\"passed\",\"softFailures\":0,\"attachments\":[]},"
            + "{\"suite\":\"b.sel\",\"name\":\"b\",\"title\":\"b.sel › b @smoke\",\"status\":\"failed\",\"error\":\"boom\",\"softFailures\":0,"
            + "\"tags\":[\"@smoke\"],\"attachments\":[\"/o/b.png\",\"/o/b-trace.zip\"]},"
            + "{\"suite\":\"c.sel\",\"name\":\"c\",\"title\":\"c.sel › c\",\"status\":\"flaky\",\"attempts\":2,\"softFailures\":0},"
            + "{\"suite\":\"d.sel\",\"name\":\"d\",\"title\":\"d.sel › d\",\"status\":\"passed\",\"softFailures\":2}]}";

    @Test
    void reportDefaultsToProblemTests() throws Exception {
        Map<String, Object> r = RunResults.summarise(om.readTree(RESULTS), null, null, null, 50, om);
        String j = om.writeValueAsString(r);
        assertEquals(3, om.readTree(j).get("matched").asInt(), j);
        assertEquals("b", om.readTree(j).at("/tests/0/name").asText());
        assertEquals("/o/b-trace.zip", om.readTree(j).at("/tests/0/traces/0").asText());
        assertEquals(2, om.readTree(j).at("/tests/2/softFailures").asInt());
        Map<String, Object> all = RunResults.summarise(om.readTree(RESULTS), null, "all", "SMOKE", 50, om);
        assertEquals(1, all.get("matched"));
        Map<String, Object> one = RunResults.summarise(om.readTree(RESULTS), null, "all", null, 1, om);
        assertEquals(3, one.get("truncated"));
    }

    @Test
    void latestResultsSkipHistoryAndOldFiles(@TempDir Path tmp) throws Exception {
        write(tmp.resolve("out/run1/gatf-results.json"), RESULTS);
        write(tmp.resolve("out/run2/gatf-results.json"), RESULTS);
        write(tmp.resolve("out/history/gatf-results.json"), RESULTS);
        write(tmp.resolve("node_modules/x/gatf-results.json"), RESULTS);
        Files.setLastModifiedTime(tmp.resolve("out/run1/gatf-results.json"), FileTime.fromMillis(10_000));
        Files.setLastModifiedTime(tmp.resolve("out/run2/gatf-results.json"), FileTime.fromMillis(20_000));
        Files.setLastModifiedTime(tmp.resolve("out/history/gatf-results.json"), FileTime.fromMillis(30_000));
        Files.setLastModifiedTime(tmp.resolve("node_modules/x/gatf-results.json"), FileTime.fromMillis(40_000));
        assertEquals(tmp.resolve("out/run2/gatf-results.json"), RunResults.latest(tmp, 0));
        assertNull(RunResults.latest(tmp, 25_000));
        assertNull(RunResults.latest(tmp.resolve("missing"), 0));
    }

    @Test
    void listerFindsScriptsAndApiCases(@TempDir Path tmp) throws Exception {
        write(tmp.resolve("tests/ui/login.sel"), "// login flow @smoke @auth\n\nopen chrome\nsubtest \"doLogin\" {\n}\n");
        write(tmp.resolve("tests/ui/lib/nav.sel"), "subtest \"openX\" {\n}\n");
        write(tmp.resolve("tests/api/users.xml"), "<?xml version=\"1.0\"?>\n<!-- users -->\n<TestCases><TestCase/></TestCases>");
        write(tmp.resolve("tests/api/other.xml"), "<project/>");
        write(tmp.resolve("gatf-config.xml"), "<gatf-execute-config/>");
        write(tmp.resolve("out/gen.sel"), "open chrome");
        write(tmp.resolve("node_modules/x.sel"), "open chrome");
        String j = om.writeValueAsString(TestLister.list(tmp, null, null, 100));
        assertEquals(3, om.readTree(j).get("matched").asInt(), j);
        assertEquals("tests/api/users.xml", om.readTree(j).at("/tests/0/path").asText());
        assertEquals("api", om.readTree(j).at("/tests/0/kind").asText());
        assertEquals("tests/ui/login.sel", om.readTree(j).at("/tests/2/path").asText());
        assertEquals("@smoke", om.readTree(j).at("/tests/2/tags/0").asText());
        assertEquals("doLogin", om.readTree(j).at("/tests/2/subtests/0").asText());
        assertEquals(1, TestLister.list(tmp, "ui", "@auth", 100).get("matched"));
        assertEquals(1, TestLister.list(tmp, "api", null, 100).get("matched"));
        assertEquals(2, TestLister.list(tmp, null, null, 1).get("truncated"));
        // the first page is the first paths in order, not whatever the walk met first
        String first = om.writeValueAsString(TestLister.list(tmp, null, null, 1));
        assertEquals("tests/api/users.xml", om.readTree(first).at("/tests/0/path").asText());
        // one script in another encoding does not fail the listing
        Files.write(tmp.resolve("tests/ui/latin.sel"), "// caf\u00e9 @l1\nopen chrome\n".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
        assertEquals(3, TestLister.list(tmp, "ui", null, 100).get("matched"));
        assertEquals(1, TestLister.list(tmp, "ui", "@l1", 100).get("matched"));
    }

    @Test
    void runArgsMapToGatfTest() {
        Map<String, Object> a = new LinkedHashMap<String, Object>();
        a.put("config", "gatf-config.json");
        a.put("grep", Arrays.asList("login", "save"));
        a.put("tag", "@smoke");
        a.put("trace", "retain-on-failure");
        a.put("retries", 1);
        a.put("workers", "2");
        a.put("updateSnapshots", true);
        a.put("reporter", "junit");
        List<String> v = RunArgs.build(a);
        assertEquals(Arrays.asList("test", "gatf-config.json", "--grep", "login", "--grep", "save", "--tag", "@smoke", "--trace", "retain-on-failure",
                "--retries", "1", "--workers", "2", "--update-snapshots", "--reporter", "junit,json"), v);
        assertEquals(Arrays.asList("test", "--reporter", "junit,json"), RunArgs.build(new LinkedHashMap<String, Object>()));
        Map<String, Object> bad = new LinkedHashMap<String, Object>();
        bad.put("trace", "sometimes");
        try { RunArgs.build(bad); throw new AssertionError(); } catch (McpTool.ToolError e) { assertTrue(e.getMessage().contains("trace")); }
        assertEquals(Arrays.asList("-Dgatf.log.level=debug", "-Xmx2g", "--add-opens=java.base/java.lang=ALL-UNNAMED"),
                RunArgs.jvmFlags(Arrays.asList("-Dgatf.log.level=debug", "-agentlib:jdwp=transport=dt_socket,server=y", "-Xmx2g",
                        "-javaagent:x.jar", "--add-opens=java.base/java.lang=ALL-UNNAMED", "-XX:+UseG1GC")));
        assertEquals("/p/out/gatf-results.json", RunArgs.resultsPath("  JSON:   /p/out/gatf-results.json"));
        assertEquals("C:\\p\\out\\gatf-results.json", RunArgs.resultsPath("JSON: C:\\p\\out\\gatf-results.json\u001b[0m"));
        assertEquals(null, RunArgs.resultsPath("JUnit:  /p/out/gatf-junit.xml"));
        bad.clear();
        bad.put("config", "--grep");
        try { RunArgs.build(bad); throw new AssertionError(); } catch (McpTool.ToolError e) { assertTrue(e.getMessage().contains("config")); }
    }
}
