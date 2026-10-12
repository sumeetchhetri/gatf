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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** H6 (Δ156): the three index fixes from the Δ155a review, plus what the selector reads (uncovered, traceStart, skip scope). */
class CoverageIndexImpactTest {

    static final String TA = "/ci/work/tests/a.sel", TB = "/ci/work/tests/b.sel";

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> skips(Map<String, Object> idx) { return (List<Map<String, Object>>) idx.get("skipped"); }

    static <T> T at(Object o, String... p) { return CoverageMergeTest.at(o, p); }

    @Test
    void aTraceOverTheRunCapStillNamesItsTestWhichIsMarkedCapped(@TempDir Path dir) throws Exception {
        Path run = ImpactFixture.run(dir, "har", true, 2_000_000_000_000L);
        CoverageMerge.Limits l = new CoverageMerge.Limits();
        l.maxTraces = 1;
        Map<String, Object> idx = CoverageIndex.build(run, null, l);
        assertEquals("ok", at(idx, "tests", TA, "coverage"));
        assertEquals("capped", at(idx, "tests", TB, "coverage"));
        boolean found = false;
        for (Map<String, Object> s : skips(idx)) {
            if (String.valueOf(s.get("reason")).startsWith("cap: more than 1 trace")) {
                assertEquals(TB, s.get("test"));
                assertEquals("test", s.get("scope"));
                found = true;
            }
        }
        assertTrue(found, skips(idx).toString());

        // past the peek allowance (as many again as the cap) the test is unknown: a trace-scope skip with test=null
        ImpactFixture.zip(run.resolve("r-1-1-3-chrome-trace.zip"), Map.of("metadata.json", b(ImpactFixture.meta("/ci/work/tests/c.sel", 1))));
        idx = CoverageIndex.build(run, null, l);
        boolean unknown = false;
        for (Map<String, Object> s : skips(idx)) {
            if (s.get("test") == null && "trace".equals(s.get("scope")) && String.valueOf(s.get("reason")).contains("its test was not read")) unknown = true;
        }
        assertTrue(unknown, skips(idx).toString());
    }

    @Test
    void aTraceOverTheBudgetOrTheEntryCapNamesItsTest(@TempDir Path dir) throws Exception {
        Path run = ImpactFixture.run(dir, "har", true, 2_000_000_000_000L);
        long first;
        try (java.util.zip.ZipFile z = new java.util.zip.ZipFile(run.resolve("r-1-1-1-chrome-trace.zip").toFile())) {
            first = z.getEntry("network.har").getSize() + z.getEntry("coverage.json").getSize();
        }
        CoverageMerge.Limits l = new CoverageMerge.Limits();
        l.maxTotalBytes = first;      // exactly the first trace: the second finds the budget used up
        Map<String, Object> idx = CoverageIndex.build(run, null, l);
        assertEquals("ok", at(idx, "tests", TA, "coverage"), skips(idx).toString());
        assertEquals("capped", at(idx, "tests", TB, "coverage"), skips(idx).toString());
        assertEquals(1, skips(idx).size(), skips(idx).toString());
        assertEquals(TB, skips(idx).get(0).get("test"));
        assertTrue(String.valueOf(skips(idx).get(0).get("reason")).contains("read budget"));

        CoverageMerge.Limits e = new CoverageMerge.Limits();
        e.maxZipEntries = 2;
        idx = CoverageIndex.build(run, null, e);
        assertEquals("capped", at(idx, "tests", TA, "coverage"));
        assertEquals(TA, skips(idx).get(0).get("test"));
    }

    @Test
    void truncatedOrUnknownBodyScriptsAreFileLevel(@TempDir Path dir) throws Exception {
        Path run = ImpactFixture.run(dir, "har", true, 2_000_000_000_000L);
        CoverageMerge.Limits l = new CoverageMerge.Limits();
        l.maxRangesPerScript = 1;
        Map<String, Object> idx = CoverageIndex.build(run, null, l);
        assertEquals(Boolean.TRUE, at(idx, "tests", TA, "scripts", ImpactFixture.URL, "fileLevel"));
        Object reason = at(idx, "tests", TA, "scripts", ImpactFixture.URL, "fileLevelReason");
        assertTrue(String.valueOf(reason).startsWith("truncated"), String.valueOf(reason));

        // a third trace of a.sel whose HAR lacks main.js: its coverage goes under an unknown body, while the run knows one
        String body = ImpactFixture.bundle("main.js.map");
        Map<String, byte[]> e = new LinkedHashMap<>();
        e.put("metadata.json", b(ImpactFixture.meta(TA, 2_000_000_000_000L)));
        e.put("coverage.json", b(ImpactFixture.coverage(body, true, false)));
        ImpactFixture.zip(run.resolve("r-1-1-3-chrome-trace.zip"), e);
        idx = CoverageIndex.build(run);
        assertEquals(Boolean.TRUE, at(idx, "tests", TA, "scripts", ImpactFixture.URL, "unknownBody"));
        assertEquals(Boolean.TRUE, at(idx, "tests", TA, "scripts", ImpactFixture.URL, "fileLevel"));
        assertNull(at(idx, "tests", TB, "scripts", ImpactFixture.URL, "fileLevel"));
        assertNull(at(idx, "scripts", ImpactFixture.URL, "mixedBodies"));
    }

    @Test
    void uncoveredRangesAndTraceStart(@TempDir Path dir) throws Exception {
        Path run = ImpactFixture.run(dir, "har", true, 2_000_000_000_000L);
        Map<String, Object> idx = CoverageIndex.build(run);
        String body = ImpactFixture.bundle("main.js.map");
        int bS = ImpactFixture.lineStart(body, ImpactFixture.bGen(1)), bE = ImpactFixture.lineEnd(body, ImpactFixture.bGen(6));
        assertEquals("[[" + bS + "," + bE + "]]", CoverageMergeTest.json(at(idx, "tests", TA, "scripts", ImpactFixture.URL, "uncovered")));
        assertEquals(2_000_000_000_000L, ((Number) at(idx, "traceStart", "oldest")).longValue());
        assertEquals(0, ((Number) at(idx, "traceStart", "unknown")).intValue());
        Map<String, Object> sum = CoverageMerge.summary(idx);
        Map<String, Object> so = at(sum, "tests", TA, "scripts", ImpactFixture.URL);
        assertFalse(so.containsKey("uncovered"), so.toString());
        // one trace without startTs makes the age unknown
        ImpactFixture.zip(run.resolve("r-1-1-3-chrome-trace.zip"), Map.of("metadata.json", b("{\"selFile\":\"x.sel\"}")));
        assertNull(at(CoverageIndex.build(run), "traceStart", "oldest"));
        for (String k : ImpactSelector.KNOWN_RULES) assertTrue(CoverageMerge.rules().containsKey(k), k);
        assertEquals(ImpactSelector.KNOWN_RULES.size(), CoverageMerge.rules().size());
    }

    @Test
    void skipScopes(@TempDir Path dir) throws Exception {
        Path run = Files.createDirectories(dir.resolve("run"));
        ImpactFixture.zip(run.resolve("attachments.zip"), Map.of("a.txt", b("x")));
        ImpactFixture.zip(run.resolve("lost-trace.zip"), Map.of("a.txt", b("x")));
        Files.write(run.resolve("broken-trace.zip"), new byte[] {1, 2});
        ImpactFixture.zip(run.resolve("noname-trace.zip"), Map.of("metadata.json", b("{\"startTs\":5}")));
        Map<String, Object> idx = CoverageIndex.build(run, dir.resolve("missing-coverage.json"), null);
        Map<String, String> scope = new LinkedHashMap<>();
        for (Map<String, Object> s : skips(idx)) if (s.get("test") == null) scope.put(String.valueOf(s.get("file")), String.valueOf(s.get("scope")));
        assertEquals("other", scope.get("attachments.zip"), scope.toString());
        assertEquals("trace", scope.get("lost-trace.zip"));
        assertEquals("trace", scope.get("broken-trace.zip"));
        assertEquals("trace", scope.get("noname-trace.zip"));
        assertEquals("crawl", scope.get("missing-coverage.json"));
    }

    @Test
    void theWorstOutcomeOfATestsTracesWins(@TempDir Path dir) throws Exception {
        Path run = ImpactFixture.run(dir, "har", true, 2_000_000_000_000L);
        assertEquals("passed", at(CoverageIndex.build(run), "tests", TA, "outcome"));
        ImpactFixture.zip(run.resolve("r-1-1-7-chrome-trace.zip"), Map.of("metadata.json", b(ImpactFixture.meta(TA, 2_000_000_000_000L, null))));
        assertEquals("unknown", at(CoverageIndex.build(run), "tests", TA, "outcome"));
        ImpactFixture.zip(run.resolve("r-1-1-8-chrome-trace.zip"), Map.of("metadata.json", b(ImpactFixture.meta(TA, 2_000_000_000_000L, "failed"))));
        assertEquals("failed", at(CoverageIndex.build(run), "tests", TA, "outcome"));
        assertEquals("passed", at(CoverageIndex.build(run), "tests", TB, "outcome"));
    }
}
