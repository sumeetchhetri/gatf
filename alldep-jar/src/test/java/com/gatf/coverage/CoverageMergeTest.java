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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** H6 (Δ155): the pure merge: V8 range semantics, per-test index, untested routes/scripts, bad input, secrets. */
class CoverageMergeTest {

    static InputStream in(String s) { return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8)); }

    static String json(Object o) throws Exception { return CoverageMerge.OM.writeValueAsString(o); }

    static String r(long s, long e, long c) { return "{\"startOffset\":" + s + ",\"endOffset\":" + e + ",\"count\":" + c + "}"; }

    static String fn(String name, String... ranges) {
        return "{\"functionName\":\"" + name + "\",\"isBlockCoverage\":true,\"ranges\":[" + String.join(",", ranges) + "]}";
    }

    static String script(String url, String... fns) {
        return "{\"scriptId\":\"7\",\"url\":\"" + url + "\",\"functions\":[" + String.join(",", fns) + "]}";
    }

    static String cov(String... scripts) { return "{\"js\":[" + String.join(",", scripts) + "],\"css\":[]}"; }

    static String spans(List<long[]> l) {
        List<String> s = new ArrayList<>();
        for (long[] x : l) s.add(x[0] + "-" + x[1]);
        return String.join(",", s);
    }

    @SuppressWarnings("unchecked")
    static <T> T at(Object o, String... path) {
        Object cur = o;
        for (String p : path) cur = ((Map<String, Object>) cur).get(p);
        return (T) cur;
    }

    @Test
    void innermostRangeDecidesTheCount() {
        // top-level ran; an inner block [10,20) did not
        assertEquals("0-10,20-100", spans(CoverageMerge.covered(Arrays.asList(new long[] {0, 100, 1}, new long[] {10, 20, 0}))));
        // siblings, the second ran inside a function that did not
        assertEquals("0-10,20-30,32-34,40-100", spans(CoverageMerge.covered(Arrays.asList(new long[] {0, 100, 1},
                new long[] {10, 20, 0}, new long[] {30, 40, 0}, new long[] {32, 34, 5}))));
        // nothing ran
        assertEquals("", spans(CoverageMerge.covered(Arrays.asList(new long[] {0, 50, 0}, new long[] {5, 9, 0}))));
        // a malformed child overlapping its parent's end is clamped into the parent
        assertEquals("0-10,60-70", spans(CoverageMerge.covered(Arrays.asList(new long[] {0, 60, 1},
                new long[] {10, 80, 0}, new long[] {60, 70, 1}))));
        assertEquals("0-5,7-12", spans(CoverageMerge.union(Arrays.asList(new long[] {0, 3}, new long[] {7, 9}),
                Arrays.asList(new long[] {2, 5}, new long[] {8, 12}))));
    }

    @Test
    void perTestIndexAndAggregate() throws Exception {
        CoverageMerge m = new CoverageMerge(null);
        String url = "https://app.test/main.js";
        m.trace("a.sel", "a-trace.zip", true);
        assertEquals(CoverageMerge.Read.OK, m.coverage("a.sel", "a-trace.zip",
                in(cov(script(url, fn("", r(0, 100, 1)), fn("f", r(10, 20, 1)), fn("g", r(30, 40, 0)))))));
        m.trace("b.sel", "b-trace.zip", true);
        m.coverage("b.sel", "b-trace.zip", in(cov(script(url, fn("", r(0, 100, 1)), fn("f", r(10, 20, 0)), fn("g", r(30, 40, 2))))));
        m.visit("a.sel", "https://app.test/#/orders");
        m.visit("b.sel", "https://app.test/#/orders?id=3");
        Map<String, Object> idx = m.build("run-1", true);

        assertEquals("gatf-coverage-index", idx.get("format"));
        assertEquals(2, idx.get("version"));
        assertEquals("ok", at(idx, "tests", "a.sel", "coverage"));
        assertEquals("ok", idx.get("status"));
        assertEquals("[[0,30],[40,100]]", json(at(idx, "tests", "a.sel", "scripts", url, "ranges")));
        assertEquals("[[10,20]]", json(at(idx, "tests", "a.sel", "scripts", url, "functions")));
        assertEquals("[[0,10],[20,100]]", json(at(idx, "tests", "b.sel", "scripts", url, "ranges")));
        assertEquals(100L, (long) at(idx, "scripts", url, "usedBytes"));
        assertEquals(2, (int) at(idx, "scripts", url, "functionsHit"));
        assertEquals(2, (int) at(idx, "scripts", url, "functionsTotal"));
        assertEquals("[\"a.sel\",\"b.sel\"]", json(at(idx, "scripts", url, "tests")));
        assertEquals("[\"a.sel\",\"b.sel\"]", json(at(idx, "routes", "https://app.test/#/orders")));

        Map<String, Object> sum = CoverageMerge.summary(idx);
        assertEquals("summary", sum.get("detail"));
        assertNull(at(sum, "tests", "a.sel", "scripts", url, "ranges"));
        assertEquals(90L, (long) at(sum, "tests", "a.sel", "scripts", url, "usedBytes"));
        assertEquals("[[0,30],[40,100]]", json(at(idx, "tests", "a.sel", "scripts", url, "ranges")));   // full untouched
    }

    @Test
    void untestedScriptsAndRoutes() throws Exception {
        CoverageMerge m = new CoverageMerge(null);
        m.trace("t.sel", "t-trace.zip", true);
        m.coverage("t.sel", "t-trace.zip", in(cov(
                script("https://app.test/admin.js", fn("", r(0, 50, 1)), fn("init", r(5, 20, 0)), fn("save", r(25, 40, 0))),
                script("https://app.test/config.js", fn("", r(0, 30, 1))),                         // top-level only: fine
                script("https://app.test/app.js", fn("", r(0, 50, 1)), fn("go", r(5, 20, 3))))));
        m.visit("t.sel", "https://app.test/");
        m.visit("t.sel", "https://app.test/#/orders");
        assertTrue(m.crawl("app-coverage.json", in("[{\"url\":\"https://app.test/\",\"priority\":\"low\",\"title\":\"Home secret\"},"
                + "{\"url\":\"https://app.test/#/orders\",\"priority\":\"high\"},"
                + "{\"url\":\"https://app.test/#/admin?token=abc\",\"priority\":\"high\"},"
                + "{\"url\":\"https://app.test/settings/\",\"priority\":\"bogus\"}]")));
        Map<String, Object> idx = m.build("r", true);
        assertEquals("[{\"route\":\"https://app.test/#/admin\",\"priority\":\"high\"},{\"route\":\"https://app.test/settings\",\"priority\":null}]",
                json(at(idx, "untested", "routes")));
        assertEquals("[\"https://app.test/admin.js\"]", json(at(idx, "untested", "scripts")));
        assertEquals(4, (int) at(idx, "counts", "crawlRoutes"));
        assertFalse(json(idx).contains("Home secret"), "crawl titles are not copied");

        // the portal crawl's {pages:[…]} form
        CoverageMerge p = new CoverageMerge(null);
        p.trace("t.sel", "t.zip", false);
        p.visit("t.sel", "https://app.test/b");
        assertTrue(p.crawl("crawl-coverage.json", in("{\"seed\":\"x\",\"pages\":[{\"url\":\"https://app.test/a\"}]}")));
        assertEquals("[{\"route\":\"https://app.test/a\",\"priority\":null}]", json(at(p.build("r", true), "untested", "routes")));
        // with no trace at all there is nothing to compare against: no-data, no route list
        CoverageMerge q = new CoverageMerge(null);
        q.crawl("crawl-coverage.json", in("[{\"url\":\"https://app.test/a\"}]"));
        assertEquals("no-data", q.build("r", true).get("status"));
        assertNull(at(q.build("r", true), "untested", "routes"));
    }

    @Test
    void tokensAndSourceTextNeverReachTheIndex() throws Exception {
        CoverageMerge m = new CoverageMerge(null);
        m.trace("t.sel", "t-trace.zip", true);
        String cov = "{\"js\":[{\"scriptId\":\"1\",\"url\":\"https://app.test/a.js?token=SECRET_TOKEN\",\"scriptSource\":\"var k='SECRET_SRC'\","
                + "\"functions\":[{\"functionName\":\"SECRET_FN\",\"isBlockCoverage\":false,\"ranges\":[" + r(0, 10, 1) + "]}]},"
                + "{\"scriptId\":\"2\",\"url\":\"data:text/javascript,SECRET_DATA\",\"functions\":[" + fn("", r(0, 5, 1)) + "]}],"
                + "\"css\":[{\"styleSheetId\":\"9.1\",\"startOffset\":0,\"endOffset\":20,\"used\":true,\"text\":\"SECRET_CSS\"}],"
                + "\"source\":\"SECRET_EXTRA\"}";
        assertEquals(CoverageMerge.Read.OK, m.coverage("t.sel", "t-trace.zip", in(cov)));
        m.visit("t.sel", "https://user:SECRET_PW@app.test/reset?token=SECRET_TOKEN#/x?SECRET_HASH=1");
        m.body("t-trace.zip", "https://app.test/a.js?token=SECRET_TOKEN;jsessionid=SECRET_SID", "0123456789abcdef0123456789abcdef01234567");
        m.crawl("c-coverage.json", in("[{\"url\":\"https://app.test/login?next=SECRET_NEXT\"}]"));
        String out = json(m.build("r", true));
        assertFalse(out.contains("SECRET"), out);
        assertTrue(out.contains("https://app.test/a.js"));
        assertEquals(20L, (long) at(m.build("r", true), "css", "usedBytes"));
    }

    @Test
    void oldMissingOrBrokenCoverageIsNoDataNeverAThrow() throws Exception {
        for (String bad : new String[] {"", "[]", "{\"foo\":1}", "{\"js\":{}}", "{\"js\":[{\"url\":", "null", "\u0000\u0001"}) {
            CoverageMerge m = new CoverageMerge(null);
            m.trace("t.sel", "t-trace.zip", true);
            CoverageMerge.Read rd = m.coverage("t.sel", "t-trace.zip", in(bad));
            assertTrue(rd == CoverageMerge.Read.OLD_FORMAT || rd == CoverageMerge.Read.MALFORMED, bad + " -> " + rd);
            Map<String, Object> idx = m.build("r", true);
            assertEquals("no-data", idx.get("status"), bad);
            assertEquals(1, ((List<?>) idx.get("skipped")).size(), bad);
            assertNull(at(idx, "untested", "routes"));
        }
        // a capture that failed on both halves: CoverageRecorder writes {js:null, css:null}
        CoverageMerge nul = new CoverageMerge(null);
        nul.trace("t.sel", "t.zip", true);
        assertEquals(CoverageMerge.Read.EMPTY, nul.coverage("t.sel", "t.zip", in("{\"js\":null,\"css\":null}")));
        assertTrue(json(nul.build("r", true).get("skipped")).contains("returned no coverage"));
        assertEquals("no-data", nul.build("r", true).get("status"));
        CoverageMerge none = new CoverageMerge(null);
        assertEquals("no-data", none.build("r", true).get("status"));
        assertFalse(none.crawl("x-coverage.json", in("{\"pages\":7}")));
        assertFalse(none.crawl("x-coverage.json", in("{nope")));
    }

    @Test
    void partialWhenSomeTracesLackCoverage() throws Exception {
        CoverageMerge m = new CoverageMerge(null);
        m.trace("a.sel", "a-trace.zip", true);
        m.coverage("a.sel", "a-trace.zip", in(cov(script("https://x.test/a.js", fn("", r(0, 9, 1))))));
        m.trace("b.sel", "b-trace.zip", false);
        m.visit("b.sel", "https://x.test/b");
        Map<String, Object> idx = m.build("r", true);
        assertEquals("partial", idx.get("status"));
        assertTrue(json(idx.get("notes")).contains("1 of 2 trace(s) have no coverage.json"));
        assertEquals("[\"b.sel\"]", json(at(idx, "routes", "https://x.test/b")));
    }

    @Test
    void scriptAndRangeCapsAreReported() throws Exception {
        CoverageMerge.Limits l = new CoverageMerge.Limits();
        l.maxScriptsPerTrace = 1;
        l.maxRangesPerScript = 2;
        CoverageMerge m = new CoverageMerge(l);
        m.trace("t.sel", "t.zip", true);
        CoverageMerge.Read rd = m.coverage("t.sel", "t.zip", in(cov(
                script("https://x.test/a.js", fn("", r(0, 100, 1)), fn("a", r(10, 20, 0)), fn("b", r(30, 40, 0)), fn("c", r(50, 60, 0))),
                script("https://x.test/b.js", fn("", r(0, 9, 1))))));
        assertEquals(CoverageMerge.Read.CAPPED, rd);
        Map<String, Object> idx = m.build("r", true);
        assertEquals("partial", idx.get("status"));
        assertEquals(Boolean.TRUE, at(idx, "tests", "t.sel", "scripts", "https://x.test/a.js", "truncated"));
        assertEquals(2, ((List<?>) at(idx, "tests", "t.sel", "scripts", "https://x.test/a.js", "ranges")).size());
        assertNull(at(idx, "scripts", "https://x.test/b.js"));
        assertEquals(2, ((List<?>) idx.get("skipped")).size());
    }

    @Test
    void worstCoverageStatePerTest() throws Exception {
        CoverageMerge m = new CoverageMerge(null);
        m.trace("t.sel", "a.zip", true);
        m.coverage("t.sel", "a.zip", in(cov(script("https://x.test/a.js", fn("", r(0, 9, 1))))));
        m.trace("t.sel", "b.zip", true);
        m.coverage("t.sel", "b.zip", in("{\"js\":["));                   // second trace broken: the test is malformed
        m.trace("u.sel", "c.zip", true);
        m.coverage("u.sel", "c.zip", in("{\"js\":[],\"css\":[]}"));
        m.trace("v.sel", "d.zip", false);
        m.trace("w.sel", "e.zip", true);
        m.mark("w.sel", CoverageMerge.Read.CAPPED);
        m.trace("o.sel", "f.zip", true);
        m.coverage("o.sel", "f.zip", in("[1]"));
        Map<String, Object> idx = m.build("r", true);
        assertEquals("malformed", at(idx, "tests", "t.sel", "coverage"));
        assertEquals("empty", at(idx, "tests", "u.sel", "coverage"));
        assertEquals("missing", at(idx, "tests", "v.sel", "coverage"));
        assertEquals("capped", at(idx, "tests", "w.sel", "coverage"));
        assertEquals("old", at(idx, "tests", "o.sel", "coverage"));
        // the malformed test's good trace still lists its script: the state, not an empty map, says "incomplete"
        assertEquals(1, ((Map<?, ?>) at(idx, "tests", "t.sel", "scripts")).size());
        for (Object s : (List<?>) idx.get("skipped")) assertTrue(((Map<?, ?>) s).containsKey("test"), String.valueOf(s));
        assertTrue(json(idx.get("skipped")).contains("{\"file\":\"d.zip\",\"test\":\"v.sel\",\"reason\":\"no coverage.json"));
        assertTrue(((String) CoverageMerge.rules().get("coverage")).contains("always selected"));
    }

    @Test
    void executedFunctionsAreADistinctSetNotAUnion() throws Exception {
        CoverageMerge m = new CoverageMerge(null);
        m.trace("t.sel", "t.zip", true);
        // f[10,80) contains g[20,30); h[80,90) touches f's end: union would make them one
        m.coverage("t.sel", "t.zip", in(cov(script("https://x.test/a.js", fn("", r(0, 100, 1)), fn("f", r(10, 80, 1)),
                fn("g", r(20, 30, 1)), fn("h", r(80, 90, 1)), fn("k", r(90, 95, 0))))));
        m.trace("u.sel", "u.zip", true);
        m.coverage("u.sel", "u.zip", in(cov(script("https://x.test/a.js", fn("", r(0, 100, 1)), fn("f", r(10, 80, 1))))));
        Map<String, Object> idx = m.build("r", true);
        assertEquals("[[10,80],[20,30],[80,90]]", json(at(idx, "tests", "t.sel", "scripts", "https://x.test/a.js", "functions")));
        assertEquals(3, (int) at(idx, "scripts", "https://x.test/a.js", "functionsHit"));
        assertEquals(4, (int) at(idx, "scripts", "https://x.test/a.js", "functionsTotal"));
        assertEquals("[[0,90],[95,100]]", json(at(idx, "tests", "t.sel", "scripts", "https://x.test/a.js", "ranges")));   // ranges stay unioned
    }

    @Test
    void bodiesArePerTestScriptAndNeverUnionedAcrossBodies() throws Exception {
        String a = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", b = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
        String url = "https://x.test/app.js";
        CoverageMerge m = new CoverageMerge(null);
        m.trace("a.sel", "a.zip", true);
        m.body("a.zip", url + "?v=1", a);
        m.coverage("a.sel", "a.zip", in(cov(script(url, fn("", r(0, 100, 1)), fn("x", r(10, 50, 0))))));
        m.trace("b.sel", "b.zip", true);
        m.body("b.zip", url + "?v=2", b);
        m.coverage("b.sel", "b.zip", in(cov(script(url, fn("", r(0, 100, 1)), fn("y", r(50, 90, 0))))));
        m.trace("c.sel", "c1.zip", true);                                  // one test, both bodies
        m.body("c1.zip", url, a);
        m.coverage("c.sel", "c1.zip", in(cov(script(url, fn("", r(0, 100, 1)), fn("x", r(10, 50, 0))))));
        m.trace("c.sel", "c2.zip", true);
        m.body("c2.zip", url, b);
        m.coverage("c.sel", "c2.zip", in(cov(script(url, fn("", r(0, 100, 1)), fn("y", r(50, 90, 0))))));
        Map<String, Object> idx = m.build("r", true);
        assertEquals("[\"" + a + "\"]", json(at(idx, "tests", "a.sel", "scripts", url, "bodies")));
        assertEquals("[[0,10],[50,100]]", json(at(idx, "tests", "a.sel", "scripts", url, "ranges")));
        assertEquals("[\"" + b + "\"]", json(at(idx, "tests", "b.sel", "scripts", url, "bodies")));
        assertEquals(Boolean.TRUE, at(idx, "tests", "c.sel", "scripts", url, "mixedBodies"));
        assertEquals("[]", json(at(idx, "tests", "c.sel", "scripts", url, "ranges")));
        assertNull(at(idx, "tests", "c.sel", "scripts", url, "usedBytes"));
        assertEquals(Boolean.TRUE, at(idx, "scripts", url, "mixedBodies"));
        assertEquals("[\"" + a + "\",\"" + b + "\"]", json(at(idx, "scripts", url, "bodies")));
        assertEquals(60L, (long) at(idx, "scripts", url, "usedBytes"));     // best single body, not the union (100)
        assertTrue(((String) CoverageMerge.rules().get("bodies")).contains(">1 body"));

        // one body (or none captured) in the run: one group, ranges unioned as before
        CoverageMerge one = new CoverageMerge(null);
        one.trace("a.sel", "a.zip", true);
        one.body("a.zip", url, a);
        one.coverage("a.sel", "a.zip", in(cov(script(url, fn("", r(0, 100, 1)), fn("x", r(10, 50, 0))))));
        one.trace("b.sel", "b.zip", true);                                 // no HAR body: unknown, compatible
        one.coverage("b.sel", "b.zip", in(cov(script(url, fn("", r(0, 100, 1)), fn("y", r(50, 90, 0))))));
        Map<String, Object> i1 = one.build("r", true);
        assertNull(at(i1, "scripts", url, "mixedBodies"));
        assertEquals(100L, (long) at(i1, "scripts", url, "usedBytes"));
        assertEquals("[]", json(at(i1, "tests", "b.sel", "scripts", url, "bodies")));
    }

    @Test
    void outputIsDeterministicAndCarriesTheCssRule() throws Exception {
        String a = script("https://x.test/a.js", fn("", r(0, 9, 1)));
        String b = script("https://x.test/b.js", fn("", r(0, 9, 1)));
        CoverageMerge m1 = new CoverageMerge(null), m2 = new CoverageMerge(null);
        m1.trace("z.sel", "z.zip", true); m1.coverage("z.sel", "z.zip", in(cov(a, b)));
        m1.trace("y.sel", "y.zip", true); m1.coverage("y.sel", "y.zip", in(cov(b)));
        m2.trace("y.sel", "y.zip", true); m2.coverage("y.sel", "y.zip", in(cov(b)));
        m2.trace("z.sel", "z.zip", true); m2.coverage("z.sel", "z.zip", in(cov(b, a)));
        assertEquals(json(m1.build("r", true)), json(m2.build("r", true)));
        String css = (String) CoverageMerge.rules().get("css");
        assertTrue(css.contains(".css") && css.contains(".scss") && css.contains("run every test"));
    }
}
