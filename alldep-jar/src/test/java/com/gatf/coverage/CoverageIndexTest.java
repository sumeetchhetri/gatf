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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** H6 (Δ155): building the index from a run folder of trace bundles: bounds, bad bundles, cache. */
class CoverageIndexTest {

    static void zip(Path p, String... nameContent) throws Exception {
        try (OutputStream o = Files.newOutputStream(p); ZipOutputStream z = new ZipOutputStream(o)) {
            for (int i = 0; i < nameContent.length; i += 2) {
                z.putNextEntry(new ZipEntry(nameContent[i]));
                z.write(nameContent[i + 1].getBytes(StandardCharsets.UTF_8));
                z.closeEntry();
            }
        }
    }

    static String meta(String sel) { return "{\"runId\":\"x\",\"selFile\":\"" + sel + "\",\"format\":2,\"recCoverage\":true}"; }

    static String har(String... urlMime) {
        StringBuilder b = new StringBuilder("{\"log\":{\"entries\":[");
        for (int i = 0; i < urlMime.length; i += 2) {
            if (i > 0) b.append(',');
            b.append("{\"request\":{\"method\":\"GET\",\"url\":\"").append(urlMime[i]).append("\",\"headers\":[{\"name\":\"Cookie\",\"value\":\"SECRET_COOKIE\"}]},")
                    .append("\"response\":{\"status\":200,\"content\":{\"size\":1,\"mimeType\":\"").append(urlMime[i + 1])
                    .append("\",\"_file\":\"resources/0123456789abcdef0123456789abcdef01234567\"}}}");
        }
        return b.append("]}}").toString();
    }

    static final String COV = "{\"js\":[{\"scriptId\":\"1\",\"url\":\"https://app.test/main.js?token=SECRET_TOKEN\",\"functions\":["
            + "{\"functionName\":\"\",\"isBlockCoverage\":false,\"ranges\":[{\"startOffset\":0,\"endOffset\":100,\"count\":1}]},"
            + "{\"functionName\":\"f\",\"isBlockCoverage\":true,\"ranges\":[{\"startOffset\":10,\"endOffset\":20,\"count\":1}]}]}],"
            + "\"css\":[{\"styleSheetId\":\"3.4\",\"startOffset\":0,\"endOffset\":40,\"used\":false}]}";

    @SuppressWarnings("unchecked")
    static <T> T at(Object o, String... path) { return CoverageMergeTest.at(o, path); }

    Path fixtureRun(Path dir) throws Exception {
        Path run = Files.createDirectories(dir.resolve("out/run-1"));
        Files.writeString(run.resolve("index.html"), "<html/>");
        zip(run.resolve("r-1-1-1-chrome-trace.zip"),
                "metadata.json", meta("tests\\\\orders.sel"),
                "coverage.json", COV,
                "network.har", har("https://app.test/?token=SECRET_TOKEN", "text/html", "https://app.test/main.js?token=SECRET_TOKEN", "application/javascript"),
                "snapshots/snapshot-1.json", "{\"v\":2,\"url\":\"https://app.test/#/orders?id=9\",\"title\":\"SECRET_TITLE\"}",
                "snapshots/snapshot-1.html", "<p>SECRET_DOM</p>");
        zip(run.resolve("r-1-1-2-chrome-trace.zip"),          // trace coverage was off for this one
                "metadata.json", meta("login.sel"),
                "snapshots/snapshot-1.json", "{\"v\":2,\"url\":\"https://app.test/login\"}");
        zip(run.resolve("attachments.zip"), "a.txt", "not a trace");
        Files.write(run.resolve("broken-trace.zip"), new byte[] {1, 2, 3, 4});
        return run;
    }

    Path crawl(Path dir) throws Exception {
        Path tests = Files.createDirectories(dir.resolve("tests"));
        Path c = tests.resolve("app-coverage.json");
        Files.writeString(c, "[{\"url\":\"https://app.test/\",\"priority\":\"low\"},{\"url\":\"https://app.test/#/orders\",\"priority\":\"high\"},"
                + "{\"url\":\"https://app.test/login\",\"priority\":\"medium\"},{\"url\":\"https://app.test/#/admin\",\"priority\":\"high\"}]");
        return c;
    }

    @Test
    void buildsTheIndexFromARunFolder(@TempDir Path dir) throws Exception {
        Path run = fixtureRun(dir);
        Map<String, Object> idx = CoverageIndex.build(run, crawl(dir), null);
        String json = CoverageMerge.OM.writeValueAsString(idx);
        assertFalse(json.contains("SECRET"), json);
        assertEquals("partial", idx.get("status"));
        assertEquals("run-1", idx.get("run"));
        assertEquals(2, (int) at(idx, "counts", "traces"));   // attachments.zip and broken-trace.zip are skipped, not traces
        assertEquals(1, (int) at(idx, "counts", "tracesWithCoverage"));
        Map<String, Object> t = at(idx, "tests", "tests/orders.sel");
        assertNotNull(t, json);
        assertEquals("[[0,100]]", CoverageMergeTest.json(at(t, "scripts", "https://app.test/main.js", "ranges")));
        assertEquals("[\"0123456789abcdef0123456789abcdef01234567\"]", CoverageMergeTest.json(at(idx, "scripts", "https://app.test/main.js", "bodies")));
        assertEquals("[\"https://app.test/\",\"https://app.test/#/orders\"]", CoverageMergeTest.json(t.get("routes")));
        assertEquals("[\"login.sel\"]", CoverageMergeTest.json(at(idx, "routes", "https://app.test/login")));
        assertEquals("[{\"route\":\"https://app.test/#/admin\",\"priority\":\"high\"}]", CoverageMergeTest.json(at(idx, "untested", "routes")));
        assertEquals(40L, (long) at(idx, "css", "totalBytes"));
        assertEquals(Boolean.TRUE, at(idx, "css", "unattributed"));
        String skipped = CoverageMergeTest.json(idx.get("skipped"));
        assertTrue(skipped.contains("attachments.zip") && skipped.contains("no metadata.json"), skipped);
        assertTrue(skipped.contains("broken-trace.zip") && skipped.contains("unreadable"), skipped);
        assertEquals(CoverageMergeTest.json(idx), CoverageMergeTest.json(CoverageIndex.build(run, crawl(dir), null)));
    }

    @Test
    void emptyMissingOrForeignFoldersAreNoData(@TempDir Path dir) throws Exception {
        assertEquals("no-data", CoverageIndex.build(dir).get("status"));
        assertEquals("no-data", CoverageIndex.build(dir.resolve("nope")).get("status"));
        assertEquals("no-data", CoverageIndex.build(null).get("status"));
        Path c = dir.resolve("x-coverage.json");
        Files.writeString(c, "\"old\"");
        Map<String, Object> idx = CoverageIndex.build(dir, c, null);
        assertEquals("no-data", idx.get("status"));
        assertTrue(CoverageMergeTest.json(idx.get("skipped")).contains("not a crawl map"));
        Map<String, Object> miss = CoverageIndex.build(dir, dir.resolve("gone-coverage.json"), null);
        assertTrue(CoverageMergeTest.json(miss.get("skipped")).contains("crawl map not found"));
    }

    @Test
    void boundsAreAppliedAndReported(@TempDir Path dir) throws Exception {
        Path run = fixtureRun(dir);
        CoverageMerge.Limits l = new CoverageMerge.Limits();
        l.maxTraces = 2;
        String s = CoverageMergeTest.json(CoverageIndex.build(run, null, l).get("skipped"));
        assertTrue(s.contains("cap: more than 2 trace bundles"), s);

        CoverageMerge.Limits e = new CoverageMerge.Limits();
        e.maxEntryBytes = 50;
        Map<String, Object> idx = CoverageIndex.build(run, null, e);
        s = CoverageMergeTest.json(idx.get("skipped"));
        assertTrue(s.contains("cap: coverage.json is"), s);
        assertTrue(s.contains("cap: network.har is"), s);
        assertEquals("partial", idx.get("status"));   // snapshot routes still read

        CoverageMerge.Limits t = new CoverageMerge.Limits();
        t.maxTotalBytes = 60;
        s = CoverageMergeTest.json(CoverageIndex.build(run, null, t).get("skipped"));
        assertTrue(s.contains("read budget") || s.contains("cap:"), s);

        CoverageMerge.Limits c = new CoverageMerge.Limits();
        c.maxCrawlBytes = 10;
        s = CoverageMergeTest.json(CoverageIndex.build(run, crawl(dir), c).get("skipped"));
        assertTrue(s.contains("crawl map is larger than 10 bytes"), s);
    }

    @Test
    void cacheIsAtomicVersionedAndRebuiltOnChange(@TempDir Path dir) throws Exception {
        Path run = fixtureRun(dir);
        Path crawl = crawl(dir);
        Map<String, Object> first = CoverageIndex.buildCached(run, crawl, null);
        Path cache = run.resolve(CoverageIndex.CACHE);
        assertTrue(Files.isRegularFile(cache));
        String bytes = Files.readString(cache);
        assertFalse(bytes.contains("SECRET"));
        assertTrue(bytes.contains("\"version\":2"));
        try (Stream<Path> s = Files.list(run)) {
            assertEquals(0L, s.filter(p -> p.getFileName().toString().endsWith(".tmp")).count());
        }
        // served from the cache: prove it by planting a marker the builder would never write
        Files.writeString(cache, bytes.replace("\"run\":\"run-1\"", "\"run\":\"from-cache\""));
        assertEquals("from-cache", CoverageIndex.buildCached(run, crawl, null).get("run"));
        // an input change rebuilds
        Files.setLastModifiedTime(run.resolve("r-1-1-2-chrome-trace.zip"), FileTime.fromMillis(1_000_000L));
        assertEquals("run-1", CoverageIndex.buildCached(run, crawl, null).get("run"));
        // so does an old cache version
        Files.writeString(cache, Files.readString(cache).replace("\"version\":2", "\"version\":1").replace("\"run\":\"run-1\"", "\"run\":\"stale\""));
        assertEquals("run-1", CoverageIndex.buildCached(run, crawl, null).get("run"));
        // and a broken one
        Files.writeString(cache, "{oops");
        assertEquals(first.get("counts"), CoverageIndex.buildCached(run, crawl, null).get("counts"));
        // the cache is never mistaken for a trace or a crawl map
        assertEquals(2, (int) at(CoverageIndex.build(run, crawl, null), "counts", "traces"));
    }

    @Test
    void everyTestCarriesItsCoverageStateAndEverySkipItsTest(@TempDir Path dir) throws Exception {
        Path run = fixtureRun(dir);   // orders.sel has coverage, login.sel's trace has none
        zip(run.resolve("r-1-1-3-chrome-trace.zip"), "metadata.json", meta("big.sel"), "coverage.json", COV);
        CoverageMerge.Limits l = new CoverageMerge.Limits();
        l.maxEntryBytes = 600;        // COV (~560 B) fits; the 900 B one below does not
        zip(run.resolve("r-1-1-4-chrome-trace.zip"), "metadata.json", meta("huge.sel"), "coverage.json", COV + new String(new char[340]).replace('\0', ' '));
        Map<String, Object> idx = CoverageIndex.build(run, null, l);
        assertEquals("ok", at(idx, "tests", "tests/orders.sel", "coverage"));
        assertEquals("missing", at(idx, "tests", "login.sel", "coverage"));
        assertEquals("ok", at(idx, "tests", "big.sel", "coverage"));
        assertEquals("capped", at(idx, "tests", "huge.sel", "coverage"), CoverageMergeTest.json(idx.get("skipped")));
        String sk = CoverageMergeTest.json(idx.get("skipped"));
        assertTrue(sk.contains("{\"file\":\"r-1-1-2-chrome-trace.zip\",\"test\":\"login.sel\",\"reason\":\"no coverage.json"), sk);
        assertTrue(sk.contains("{\"file\":\"attachments.zip\",\"test\":null,"), sk);
        assertEquals(2, (int) at(idx, "counts", "testsCoverageNotOk"));
    }

    @Test
    void crawlMapsNewestFirst(@TempDir Path dir) throws Exception {
        Path a = dir.resolve("a-coverage.json"), b = dir.resolve("crawl-coverage.json");
        Files.writeString(a, "[]");
        Files.writeString(b, "[]");
        Files.writeString(dir.resolve("coverage-index.json"), "{}");
        Files.writeString(dir.resolve("a.sel"), "");
        Files.setLastModifiedTime(a, FileTime.fromMillis(2_000_000L));
        Files.setLastModifiedTime(b, FileTime.fromMillis(1_000_000L));
        List<Path> l = CoverageIndex.crawlMaps(dir);
        assertEquals(2, l.size());
        assertEquals("a-coverage.json", l.get(0).getFileName().toString());
        assertTrue(CoverageIndex.crawlMaps(dir.resolve("nope")).isEmpty());
    }
}
