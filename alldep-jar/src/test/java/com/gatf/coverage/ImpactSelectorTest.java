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
package com.gatf.coverage;

import static com.gatf.coverage.ImpactFixture.b;
import static com.gatf.coverage.ImpactFixture.change;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** H6 (Δ156): test-impact selection on fixtures: a change runs only the affected tests; every unsafe case runs all, with a reason. */
class ImpactSelectorTest {

    static final long TRACE_TS = 2_000_000_000_000L;   // traces recorded after the base commit
    static final long BASE_TS = 1_900_000_000_000L;
    static final String TA = "/ci/work/tests/a.sel", TB = "/ci/work/tests/b.sel";
    static final String[] NONE = {};

    static ImpactSelector.Options opts() {
        ImpactSelector.Options o = new ImpactSelector.Options();
        o.isTestScript = p -> p.startsWith("tests/");
        return o;
    }

    static ImpactSelector.Result sel(Path run, DiffParser.FileChange... c) {
        return ImpactFixture.select(run, new ImpactFixture.FakeRepo(BASE_TS), Arrays.asList(c), opts());
    }

    static String why(ImpactSelector.Result r) { return String.join(" | ", r.runAll) + " || " + r.tests.toString() + " notes " + r.notes; }

    static boolean selected(ImpactSelector.Result r, String id) {
        assertFalse(r.runsAll(), "unexpected run-all: " + r.runAll);
        assertTrue(r.tests.containsKey(id), id + " not judged: " + r.tests.keySet() + " " + r.notes);
        return r.tests.get(id).selected;
    }

    static String reasons(ImpactSelector.Result r, String id) { return String.join("; ", r.tests.get(id).reasons); }

    // ------------------------------------------------------------------ accept: a changed file runs only the affected tests

    @Test
    void aChangeInsideBRunsOnlyB(@TempDir Path dir) throws Exception {
        Path run = ImpactFixture.run(dir, "har", true, TRACE_TS);
        ImpactSelector.Result r = sel(run, change("src/b.js", 3, new String[] {"  z = z * 2;"}, "  z = z * 5;"));
        assertFalse(selected(r, TA), reasons(r, TA));
        assertTrue(selected(r, TB), why(r));
        assertTrue(reasons(r, TB).contains("src/b.js line 3 is in code this test ran"), reasons(r, TB));
        assertTrue(reasons(r, TA).contains("outside the code this test ran"), reasons(r, TA));
    }

    @Test
    void aChangeInsideAnUntakenBranchSkipsTheTestThatNeverEnteredIt(@TempDir Path dir) throws Exception {
        Path run = ImpactFixture.run(dir, "har", true, TRACE_TS);
        ImpactSelector.Result r = sel(run, change("src/a.js", 4, new String[] {"    x = x * 3;"}, "    x = x * 4;"));
        assertTrue(selected(r, TA), why(r));       // a() ran with its if taken
        assertFalse(selected(r, TB), reasons(r, TB));
    }

    @Test
    void inlineMapsWorkLikeCapturedOnes(@TempDir Path dir) throws Exception {
        Path run = ImpactFixture.run(dir, "inline", true, TRACE_TS);
        ImpactSelector.Result r = sel(run, change("src/b.js", 3, new String[] {"  z = z * 2;"}, "  z = z * 5;"));
        assertFalse(selected(r, TA), reasons(r, TA));
        assertTrue(selected(r, TB));
    }

    @Test
    void externalMapsFromTheBuildDirOnlyWhenTheScriptBesideThemIsTheTracedBody(@TempDir Path dir) throws Exception {
        Path run = ImpactFixture.run(dir, "none", true, TRACE_TS);
        DiffParser.FileChange c = change("src/b.js", 3, new String[] {"  z = z * 2;"}, "  z = z * 5;");
        ImpactSelector.Result r = sel(run, c);
        assertTrue(r.runsAll(), why(r));                      // the map wasn't captured: b.js maps to nothing
        assertTrue(String.join(" ", r.runAll).contains("src/b.js (modified): not mapped"), why(r));
        assertTrue(r.notes.toString().contains("pass --source-maps"), r.notes.toString());

        Path build = Files.createDirectories(dir.resolve("dist"));
        Files.write(build.resolve("main.js.map"), b(ImpactFixture.map(true)));
        Files.write(build.resolve("main.js"), b(ImpactFixture.bundle("main.js.map") + "// another build\n"));
        ImpactSelector.Options o = opts();
        o.sourceMapsDir = build;
        r = ImpactFixture.select(run, new ImpactFixture.FakeRepo(BASE_TS), Collections.singletonList(c), o);
        assertTrue(r.runsAll(), "a map beside a different build must not be trusted: " + why(r));

        Files.write(build.resolve("main.js"), b(ImpactFixture.bundle("main.js.map")));
        r = ImpactFixture.select(run, new ImpactFixture.FakeRepo(BASE_TS), Collections.singletonList(c), o);
        assertFalse(selected(r, TA), reasons(r, TA));
        assertTrue(selected(r, TB));
    }

    @Test
    void aServedFileIdenticalToARepoFileMapsLineByLine(@TempDir Path dir) throws Exception {
        Path run = ImpactFixture.run(dir, "plain", true, TRACE_TS);
        ImpactFixture.FakeRepo repo = new ImpactFixture.FakeRepo(BASE_TS);
        repo.files.put("web/main.js", ImpactFixture.bundle(null));
        int line = ImpactFixture.bGen(3) + 1;   // 1-based line of b.js line 3 in main.js
        DiffParser.FileChange c = change("web/main.js", line, new String[] {"  z = z * 2;"}, "  z = z * 5;");
        ImpactSelector.Result r = ImpactFixture.select(run, repo, Collections.singletonList(c), opts());
        assertFalse(selected(r, TA), reasons(r, TA));
        assertTrue(selected(r, TB));
        // the sources of an unmapped build are unknown: a change to them runs everything
        r = ImpactFixture.select(run, repo, Collections.singletonList(change("src/b.js", 3, new String[] {"  z = z * 2;"}, "  z = z * 5;")), opts());
        assertTrue(r.runsAll(), why(r));
    }

    @Test
    void onlyATestScriptChanged(@TempDir Path dir) throws Exception {
        Path run = ImpactFixture.run(dir, "har", true, TRACE_TS);
        ImpactSelector.Result r = sel(run, change("tests/a.sel", 1, new String[] {"open chrome"}, "open firefox"));
        assertFalse(r.runsAll(), why(r));
        assertEquals(Collections.singleton("tests/a.sel"), r.changedScripts);
        List<ImpactSelector.Script> scripts = Arrays.asList(
                new ImpactSelector.Script("a.sel", "/home/me/p/tests/a.sel", "a.sel", "tests/a.sel"),
                new ImpactSelector.Script("b.sel", "/home/me/p/tests/b.sel", "b.sel", "tests/b.sel"));
        List<ImpactSelector.ScriptVerdict> v = ImpactSelector.forScripts(r, scripts);
        assertTrue(v.get(0).selected);
        assertEquals("the script itself changed", v.get(0).reasons.get(0));
        assertFalse(v.get(1).selected, v.get(1).reasons.toString());
    }

    @Test
    void nothingChangedRunsNothing(@TempDir Path dir) throws Exception {
        Path run = ImpactFixture.run(dir, "har", true, TRACE_TS);
        ImpactSelector.Result r = ImpactFixture.select(run, new ImpactFixture.FakeRepo(BASE_TS), Collections.<DiffParser.FileChange>emptyList(), opts());
        assertTrue(r.nothingChanged);
        List<ImpactSelector.ScriptVerdict> v = ImpactSelector.forScripts(r, Collections.singletonList(new ImpactSelector.Script("a.sel", null, "a.sel", null)));
        assertFalse(v.get(0).selected);
    }

    // ------------------------------------------------------------------ edits that aren't provably local: file level

    @Test
    void anEditThatChangesBlockStructureOrHoistsIsFileLevel(@TempDir Path dir) throws Exception {
        Path run = ImpactFixture.run(dir, "har", true, TRACE_TS);
        for (DiffParser.FileChange c : new DiffParser.FileChange[] {
                change("src/a.js", 4, new String[] {"    x = x * 3;"}, "  } else {", "    x = x * 3;"),   // moves code out of the if
                change("src/a.js", 3, NONE, "    var q = 1;"),                                        // hoists to a()'s scope
                change("src/a.js", 3, NONE, "    function q() {}"),
                change("src/a.js", 4, new String[] {"    x = x * 3;"}, "    x = 'it;"),              // unterminated string
        }) {
            ImpactSelector.Result r = sel(run, c);
            assertTrue(selected(r, TB), "B never ran a(), but this edit isn't local: " + c.hunks.get(0).added + " " + why(r));
            assertTrue(reasons(r, TB).contains("not provably local"), reasons(r, TB));
        }
    }

    @Test
    void aBuildWhoseSourceIsNotProvenToBeTheBaseIsFileLevel(@TempDir Path dir) throws Exception {
        Path run = ImpactFixture.run(dir, "har", false, TRACE_TS);   // no sourcesContent
        ImpactSelector.Result r = sel(run, change("src/b.js", 3, new String[] {"  z = z * 2;"}, "  z = z * 5;"));
        assertTrue(selected(r, TA), why(r));
        assertTrue(reasons(r, TA).contains("not proven"), reasons(r, TA));
        assertTrue(selected(r, TB));

        // sourcesContent that differs from the base commit's file (the build is of another version) is also file level
        ImpactFixture.FakeRepo repo = new ImpactFixture.FakeRepo(BASE_TS);
        repo.files.put("src/b.js", "// edited before the base\n" + repo.files.get("src/b.js"));
        Path run2 = ImpactFixture.run(dir.resolve("2"), "har", true, TRACE_TS);
        r = ImpactFixture.select(run2, repo, Collections.singletonList(change("src/b.js", 4, new String[] {"  z = z * 2;"}, "  z = z * 5;")), opts());
        assertTrue(selected(r, TA), why(r));
    }

    @Test
    void deletedAndAddedFilesAreFileLevelOrUnmapped(@TempDir Path dir) throws Exception {
        Path run = ImpactFixture.run(dir, "har", true, TRACE_TS);
        DiffParser.FileChange del = DiffParser.parse("diff --git a/src/b.js b/src/b.js\ndeleted file mode 100644\n--- a/src/b.js\n+++ /dev/null\n@@ -1,6 +0,0 @@\n-a\n-b\n-c\n-d\n-e\n-f\n").get(0);
        ImpactSelector.Result r = sel(run, del);
        assertTrue(selected(r, TA) && selected(r, TB), why(r));
        DiffParser.FileChange add = DiffParser.parse("diff --git a/src/c.js b/src/c.js\nnew file mode 100644\n--- /dev/null\n+++ b/src/c.js\n@@ -0,0 +1 @@\n+x\n").get(0);
        r = sel(run, add);
        assertTrue(r.runsAll(), why(r));
    }

    // ------------------------------------------------------------------ run-wide: run everything, and say why

    @Test
    void unmappedAndCssChangesRunEverything(@TempDir Path dir) throws Exception {
        Path run = ImpactFixture.run(dir, "har", true, TRACE_TS);
        ImpactSelector.Result r = sel(run, change("README.md", 1, new String[] {"# app"}, "# App"));
        assertTrue(r.runsAll());
        assertTrue(r.runAll.get(0).startsWith("README.md (modified): not mapped"), why(r));
        r = sel(run, change("styles/app.css", 1, new String[] {"body{}"}, "body{color:red}"),
                change("src/b.js", 3, new String[] {"  z = z * 2;"}, "  z = z * 5;"));
        assertTrue(r.runsAll());
        assertTrue(r.runAll.get(0).contains("stylesheet"), why(r));
        List<ImpactSelector.ScriptVerdict> v = ImpactSelector.forScripts(r, Collections.singletonList(new ImpactSelector.Script("a.sel", TA, "a.sel", null)));
        assertTrue(v.get(0).selected);
        assertTrue(v.get(0).reasons.get(0).contains("stylesheet"));
    }

    @Test
    void noIndexStaleIndexOrUnknownAgeRunsEverything(@TempDir Path dir) throws Exception {
        DiffParser.FileChange c = change("src/b.js", 3, new String[] {"  z = z * 2;"}, "  z = z * 5;");
        ImpactSelector.Result r = ImpactSelector.select(null, Collections.singletonList(c), new ImpactFixture.FakeRepo(BASE_TS), null, opts());
        assertTrue(r.runAll.get(0).contains("no coverage index"), why(r));

        Path run = ImpactFixture.run(dir, "har", true, TRACE_TS);
        r = ImpactFixture.select(run, new ImpactFixture.FakeRepo(TRACE_TS + 1), Collections.singletonList(c), opts());
        assertTrue(String.join(" ", r.runAll).contains("older than the base commit"), why(r));
        r = ImpactFixture.select(run, new ImpactFixture.FakeRepo(null), Collections.singletonList(c), opts());
        assertTrue(String.join(" ", r.runAll).contains("base commit time unknown"), why(r));

        Path old = ImpactFixture.run(dir.resolve("o"), "har", true, 0);
        r = sel(old, c);
        assertTrue(String.join(" ", r.runAll).contains("age unknown"), why(r));

        r = sel(dir.resolve("empty"), c);
        assertTrue(r.runsAll(), why(r));
    }

    @Test
    void aSkipThatNamesNoTestRunsEverything(@TempDir Path dir) throws Exception {
        Path run = ImpactFixture.run(dir, "har", true, TRACE_TS);
        Files.write(run.resolve("r-9-trace.zip"), new byte[] {1, 2, 3});
        ImpactSelector.Result r = sel(run, change("src/b.js", 3, new String[] {"  z = z * 2;"}, "  z = z * 5;"));
        assertTrue(String.join(" ", r.runAll).contains("r-9-trace.zip"), why(r));
        // a zip that isn't a trace is not a reason
        Files.delete(run.resolve("r-9-trace.zip"));
        ImpactFixture.zip(run.resolve("attachments.zip"), Collections.singletonMap("a.txt", b("x")));
        r = sel(run, change("src/b.js", 3, new String[] {"  z = z * 2;"}, "  z = z * 5;"));
        assertFalse(r.runsAll(), why(r));
    }

    @Test
    void mixedBodiesOrAnUnknownRuleRunEverything(@TempDir Path dir) throws Exception {
        Path run = ImpactFixture.run(dir, "har", true, TRACE_TS);
        DiffParser.FileChange c = change("src/b.js", 3, new String[] {"  z = z * 2;"}, "  z = z * 5;");
        Map<String, Object> idx = CoverageIndex.build(run);
        @SuppressWarnings("unchecked")
        Map<String, Object> rules = (Map<String, Object>) idx.get("rules");
        rules.put("brandNewRule", "a v2 index from a newer GATF");
        ImpactSelector.Result r = ImpactSelector.select(idx, Collections.singletonList(c), new ImpactFixture.FakeRepo(BASE_TS), new TraceStore(run), opts());
        assertTrue(String.join(" ", r.runAll).contains("brandNewRule"), why(r));
        rules.remove("brandNewRule");
        rules.remove("uncovered");
        r = ImpactSelector.select(idx, Collections.singletonList(c), new ImpactFixture.FakeRepo(BASE_TS), new TraceStore(run), opts());
        assertTrue(String.join(" ", r.runAll).contains("lacks rule 'uncovered'"), why(r));

        // a third trace of a.sel loaded another build of main.js
        String other = ImpactFixture.bundle("main.js.map") + "// v2\n";
        String sha = ImpactSelector.sha1(b(other));
        Map<String, byte[]> e = new LinkedHashMap<>();
        e.put("metadata.json", b(ImpactFixture.meta(TA, TRACE_TS)));
        e.put("network.har", b(ImpactFixture.har(Collections.singletonMap(ImpactFixture.URL, sha))));
        e.put("coverage.json", b(ImpactFixture.coverage(other, true, false)));
        e.put("resources/" + sha, b(other));
        ImpactFixture.zip(run.resolve("r-1-1-3-chrome-trace.zip"), e);
        r = sel(run, c);
        assertTrue(String.join(" ", r.runAll).contains("mixedBodies"), why(r));
    }

    // ------------------------------------------------------------------ per test: select, and say why

    @Test
    void aTestWithIncompleteCoverageIsAlwaysSelected(@TempDir Path dir) throws Exception {
        Path run = ImpactFixture.run(dir, "har", true, TRACE_TS);
        Map<String, byte[]> e = new LinkedHashMap<>();
        e.put("metadata.json", b(ImpactFixture.meta(TB, TRACE_TS)));   // a second trace of b.sel, coverage off
        ImpactFixture.zip(run.resolve("r-1-1-4-chrome-trace.zip"), e);
        ImpactSelector.Result r = sel(run, change("src/a.js", 4, new String[] {"    x = x * 3;"}, "    x = x * 4;"));
        assertTrue(selected(r, TB), why(r));
        assertTrue(reasons(r, TB).contains("'missing'"), reasons(r, TB));
    }

    @Test
    void anOpaqueScriptSelectsEveryTestThatLoadedIt(@TempDir Path dir) throws Exception {
        Path run = ImpactFixture.run(dir, "har", true, TRACE_TS);
        String vendor = "https://cdn.test/vendor.js";
        String vbody = "var v = 1;\n";
        Map<String, byte[]> e = new LinkedHashMap<>();
        e.put("metadata.json", b(ImpactFixture.meta(TA, TRACE_TS)));
        e.put("network.har", b(ImpactFixture.har(Collections.singletonMap(vendor, ImpactSelector.sha1(b(vbody))))));   // body not stored
        e.put("coverage.json", b("{\"js\":[{\"url\":\"" + vendor + "\",\"functions\":[" + ImpactFixture.fn("", ImpactFixture.r(0, vbody.length(), 1)) + "]}],\"css\":[]}"));
        ImpactFixture.zip(run.resolve("r-1-1-5-chrome-trace.zip"), e);
        ImpactSelector.Result r = sel(run, change("src/b.js", 3, new String[] {"  z = z * 2;"}, "  z = z * 5;"));
        assertTrue(selected(r, TA), why(r));
        assertTrue(reasons(r, TA).contains("vendor.js, which is opaque"), reasons(r, TA));
        assertTrue(selected(r, TB));
    }

    @Test
    void truncatedCoverageIsFileLevel(@TempDir Path dir) throws Exception {
        Path run = ImpactFixture.run(dir, "har", true, TRACE_TS);
        CoverageMerge.Limits l = new CoverageMerge.Limits();
        l.maxRangesPerScript = 1;
        Map<String, Object> idx = CoverageIndex.build(run, null, l);
        ImpactSelector.Result r = ImpactSelector.select(idx, Collections.singletonList(change("src/b.js", 3, new String[] {"  z = z * 2;"}, "  z = z * 5;")),
                new ImpactFixture.FakeRepo(BASE_TS), new TraceStore(run), opts());
        assertTrue(selected(r, TA), why(r));
        assertTrue(reasons(r, TA).contains("file level (truncated"), reasons(r, TA));
    }

    @Test
    void codeInsertedBetweenTwoNeverRunSiblingsAtTopLevelIsSelected(@TempDir Path dir) throws Exception {
        // if (x) {…} and function f() {…} never ran, but the gap between them is top-level code that did
        String[] lines = {"if (x) {", "  a();", "}", "function f() {", "  b();", "  c();", "  d();", "}", "go();"};
        String body = String.join("\n", lines) + "\n";
        int ifS = body.indexOf('{'), ifE = body.indexOf("}\n") + 1, fS = body.indexOf("function"), fE = body.indexOf("}\ngo") + 1;
        String cov = "{\"js\":[{\"url\":\"https://app.test/min.js\",\"functions\":[" + ImpactFixture.fn("", ImpactFixture.r(0, body.length(), 1), ImpactFixture.r(ifS, ifE, 0))
                + "," + ImpactFixture.fn("f", ImpactFixture.r(fS, fE, 0)) + "]}],\"css\":[]}";
        String sha = ImpactSelector.sha1(b(body));
        Path run = Files.createDirectories(dir.resolve("out/1"));
        Map<String, byte[]> e = new LinkedHashMap<>();
        e.put("metadata.json", b(ImpactFixture.meta(TA, TRACE_TS)));
        e.put("network.har", b(ImpactFixture.har(Collections.singletonMap("https://app.test/min.js", sha))));
        e.put("coverage.json", b(cov));
        e.put("resources/" + sha, b(body));
        ImpactFixture.zip(run.resolve("t-trace.zip"), e);
        ImpactFixture.FakeRepo repo = new ImpactFixture.FakeRepo(BASE_TS);
        repo.files.put("web/min.js", body);
        ImpactSelector.Result r = ImpactFixture.select(run, repo, Collections.singletonList(change("web/min.js", 3, NONE, "doIt();")), opts());
        assertTrue(selected(r, TA), "inserted top-level code runs: " + why(r));
        r = ImpactFixture.select(run, repo, Collections.singletonList(change("web/min.js", 6, new String[] {"  c();"}, "  e();")), opts());
        assertFalse(selected(r, TA), "strictly inside f(), which never ran: " + reasons(r, TA));
        r = ImpactFixture.select(run, repo, Collections.singletonList(change("web/min.js", 8, new String[] {"}"}, "} doIt();")), opts());
        assertTrue(selected(r, TA), why(r));
    }

    @Test
    void aTestThatFailedOrHasNoOutcomeIsAlwaysSelected(@TempDir Path dir) throws Exception {
        for (String outcome : new String[] {"failed", null}) {
            Path run = ImpactFixture.run(dir.resolve(String.valueOf(outcome)), "har", true, TRACE_TS);
            // b.sel's second trace: same coverage, but it failed (or predates outcomes): its coverage may stop early
            String body = ImpactFixture.bundle("main.js.map");
            Map<String, byte[]> e = new LinkedHashMap<>();
            e.put("metadata.json", b(ImpactFixture.meta(TB, TRACE_TS, outcome)));
            e.put("coverage.json", b(ImpactFixture.coverage(body, false, true).replace("\"url\":\"" + ImpactFixture.URL, "\"url\":\"https://app.test/other.js")));
            ImpactFixture.zip(run.resolve("r-1-1-6-chrome-trace.zip"), e);
            ImpactSelector.Result r = sel(run, change("src/a.js", 4, new String[] {"    x = x * 3;"}, "    x = x * 4;"));
            assertTrue(selected(r, TB), why(r));
            assertTrue(reasons(r, TB).contains(outcome == null ? "no recorded outcome" : "it failed"), reasons(r, TB));
        }
    }

    @Test
    void aSourceThatAnotherBundleNamesOnlyByFileNameIsFileLevelThere(@TempDir Path dir) throws Exception {
        // t1 loads main.js (proves src/b.js); t2 loads only vendor.js, whose map names "b.js" with no content, and ran b()
        String vendorUrl = "https://app.test/vendor.js";
        String vm = ImpactFixture.map(false).replace("webpack://app/./src/b.js", "b.js");
        String ven = ImpactFixture.bundle("data:application/json;base64," + java.util.Base64.getEncoder().encodeToString(b(vm)));
        String main = ImpactFixture.bundle("main.js.map"), mapJson = ImpactFixture.map(true);
        String ms = ImpactSelector.sha1(b(main)), mps = ImpactSelector.sha1(b(mapJson)), vs = ImpactSelector.sha1(b(ven));
        Path run = Files.createDirectories(dir.resolve("out/1"));
        Map<String, String> u1 = new LinkedHashMap<>();
        u1.put(ImpactFixture.URL, ms);
        u1.put(ImpactFixture.MAP_URL, mps);
        Map<String, byte[]> e1 = new LinkedHashMap<>();
        e1.put("metadata.json", b(ImpactFixture.meta("/ci/tests/t1.sel", TRACE_TS)));
        e1.put("network.har", b(ImpactFixture.har(u1)));
        e1.put("coverage.json", b(ImpactFixture.coverage(main, true, false)));
        e1.put("resources/" + ms, b(main));
        e1.put("resources/" + mps, b(mapJson));
        ImpactFixture.zip(run.resolve("r-1-trace.zip"), e1);
        Map<String, byte[]> e2 = new LinkedHashMap<>();
        e2.put("metadata.json", b(ImpactFixture.meta("/ci/tests/t2.sel", TRACE_TS)));
        e2.put("network.har", b(ImpactFixture.har(Collections.singletonMap(vendorUrl, vs))));
        e2.put("coverage.json", b(ImpactFixture.coverage(ven, false, true).replace(ImpactFixture.URL, vendorUrl)));
        e2.put("resources/" + vs, b(ven));
        ImpactFixture.zip(run.resolve("r-2-trace.zip"), e2);
        ImpactSelector.Result r = sel(run, change("src/b.js", 3, new String[] {"  z = z * 2;"}, "  z = z * 5;"));
        assertTrue(selected(r, "/ci/tests/t2.sel"), why(r));
        assertTrue(reasons(r, "/ci/tests/t2.sel").contains("may be src/b.js"), reasons(r, "/ci/tests/t2.sel"));
        assertFalse(selected(r, "/ci/tests/t1.sel"), reasons(r, "/ci/tests/t1.sel"));   // t1 never ran b()
        // a file matched only by name is never a mapping on its own: it still runs everything
        ImpactFixture.FakeRepo repo = new ImpactFixture.FakeRepo(BASE_TS);
        repo.files.put("server/b.js", "x\n");
        r = ImpactFixture.select(run, repo, Collections.singletonList(change("server/b.js", 1, new String[] {"x"}, "y")), opts());
        assertTrue(r.runsAll(), why(r));
    }

    @Test
    void identityLinesAreCountedAsGitCountsThem(@TempDir Path dir) throws Exception {
        // a lone CR is not a line break for git: line numbers must not shift (else a change could land in the wrong code)
        // f() first, with five lone CRs in a comment: splitting on CR would shift every later line by five
        String[] lines = {"function f() {", "  a(); // x\ry\rz\rw\rv\ru", "  b();", "  c();", "  d();", "}", "function g() {", "  e();", "  h();", "  i();", "}", "g();"};
        String body = String.join("\n", lines) + "\n";
        int gS = body.indexOf("function g"), gE = body.indexOf("}\ng();") + 1, fS = 0, fE = body.indexOf("}\nfunction g") + 1;
        String cov = "{\"js\":[{\"url\":\"https://app.test/cr.js\",\"functions\":[" + ImpactFixture.fn("", ImpactFixture.r(0, body.length(), 1))
                + "," + ImpactFixture.fn("f", ImpactFixture.r(fS, fE, 0)) + "," + ImpactFixture.fn("g", ImpactFixture.r(gS, gE, 1)) + "]}],\"css\":[]}";
        String sha = ImpactSelector.sha1(b(body));
        Path run = Files.createDirectories(dir.resolve("out/1"));
        Map<String, byte[]> e = new LinkedHashMap<>();
        e.put("metadata.json", b(ImpactFixture.meta(TA, TRACE_TS)));
        e.put("network.har", b(ImpactFixture.har(Collections.singletonMap("https://app.test/cr.js", sha))));
        e.put("coverage.json", b(cov));
        e.put("resources/" + sha, b(body));
        ImpactFixture.zip(run.resolve("t-trace.zip"), e);
        ImpactFixture.FakeRepo repo = new ImpactFixture.FakeRepo(BASE_TS);
        repo.files.put("web/cr.js", body);
        // git line 9 is "  h();" inside g(), which ran
        ImpactSelector.Result r = ImpactFixture.select(run, repo, Collections.singletonList(change("web/cr.js", 9, new String[] {"  h();"}, "  k();")), opts());
        assertTrue(selected(r, TA), why(r));
        // git line 4 is "  c();" inside f(), which never ran
        r = ImpactFixture.select(run, repo, Collections.singletonList(change("web/cr.js", 4, new String[] {"  c();"}, "  k();")), opts());
        assertFalse(selected(r, TA), why(r));
    }

    @Test
    void aSuffixMatchMustAgreeOnTheCoverageRunsTestDir() {
        ImpactSelector.Result r = new ImpactSelector.Result();
        r.tests.put("/ci/w/tests/a.sel", new ImpactSelector.Verdict(false, Collections.singletonList("outside")));
        r.tests.put("/ci/w/tests/b.sel", new ImpactSelector.Verdict(false, Collections.singletonList("outside")));
        r.tests.put("/ci/w/tests/old/login.sel", new ImpactSelector.Verdict(false, Collections.singletonList("outside")));
        List<ImpactSelector.ScriptVerdict> v = ImpactSelector.forScripts(r, Arrays.asList(
                new ImpactSelector.Script("a.sel", "/me/tests/a.sel", "a.sel", null),
                new ImpactSelector.Script("b.sel", "/me/tests/b.sel", "b.sel", null),
                new ImpactSelector.Script("login.sel", "/me/tests/login.sel", "login.sel", null)));
        assertFalse(v.get(0).selected);
        assertFalse(v.get(1).selected);
        assertTrue(v.get(2).selected, "login.sel is not old/login.sel: " + v.get(2).ids);
        assertTrue(v.get(2).ids.isEmpty());
    }

    @Test
    void aScriptWithAnIdOutsideTheCoverageRunsTestDirIsSelected() {
        // the same script recorded from two workspace roots (e.g. merged shards): the minority id says "selected"
        ImpactSelector.Result r = new ImpactSelector.Result();
        r.tests.put("/m1/tests/a.sel", new ImpactSelector.Verdict(false, Collections.singletonList("outside")));
        r.tests.put("/m1/tests/b.sel", new ImpactSelector.Verdict(false, Collections.singletonList("outside")));
        r.tests.put("/m2/tests/a.sel", new ImpactSelector.Verdict(true, Collections.singletonList("inside")));
        List<ImpactSelector.ScriptVerdict> v = ImpactSelector.forScripts(r, Arrays.asList(
                new ImpactSelector.Script("a.sel", "/me/tests/a.sel", "a.sel", null),
                new ImpactSelector.Script("b.sel", "/me/tests/b.sel", "b.sel", null)));
        assertTrue(v.get(0).selected, v.get(0).reasons.toString());
        assertTrue(v.get(0).reasons.get(0).contains("/m2/tests/a.sel"));
        assertFalse(v.get(1).selected);
    }

    // ------------------------------------------------------------------ scripts of the suite

    @Test
    void indexIdsFromAnotherMachineMatchByTheLongestRelativePath() {
        ImpactSelector.Result r = new ImpactSelector.Result();
        r.tests.put("/ci/w/tests/a.sel", new ImpactSelector.Verdict(false, Collections.singletonList("outside")));
        r.tests.put("/ci/w/tests/sub/a.sel", new ImpactSelector.Verdict(true, Collections.singletonList("inside")));
        List<ImpactSelector.Script> s = new ArrayList<>(Arrays.asList(
                new ImpactSelector.Script("a.sel", "/me/p/tests/a.sel", "a.sel", null),
                new ImpactSelector.Script("sub/a.sel", "/me/p/tests/sub/a.sel", "sub/a.sel", null),
                new ImpactSelector.Script("new.sel", "/me/p/tests/new.sel", "new.sel", null)));
        List<ImpactSelector.ScriptVerdict> v = ImpactSelector.forScripts(r, s);
        assertFalse(v.get(0).selected, v.get(0).ids.toString());
        assertEquals(Collections.singletonList("/ci/w/tests/a.sel"), v.get(0).ids);
        assertTrue(v.get(1).selected);
        assertEquals(Collections.singletonList("/ci/w/tests/sub/a.sel"), v.get(1).ids);
        assertTrue(v.get(2).selected);
        assertTrue(v.get(2).reasons.get(0).contains("not in the coverage index"));
    }
}
