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
package com.gatf.log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** H8 (Δ162): merge-link logic for distributed traces, including a tampered manifest in an input dir. */
class ReportMergerTracesTest {

    @Test
    void onlyStoredPresentPlainRelativeTracesKeepALink(@TempDir Path tmp) throws Exception {
        File copy = tmp.toFile();
        Files.createDirectories(tmp.resolve("dist-traces/r/w1"));
        Files.write(tmp.resolve("dist-traces/r/w1/a-trace.zip"), new byte[] {'P', 'K', 3, 4});
        Files.write(tmp.resolve("outside.zip"), new byte[] {'P', 'K', 3, 4});
        String manifest = "{\"type\":\"distributed-traces\",\"run\":\"r\",\"traces\":["
                + "{\"worker\":\"w1\",\"status\":\"ok\",\"href\":\"dist-traces/r/w1/a-trace.zip\"},"
                + "{\"worker\":\"w1\",\"status\":\"ok\",\"href\":\"dist-traces/r/w1/gone-trace.zip\"},"
                + "{\"worker\":\"w1\",\"status\":\"ok\",\"href\":\"../outside.zip\"},"
                + "{\"worker\":\"w1\",\"status\":\"ok\",\"href\":\"/etc/passwd\"},"
                + "{\"worker\":\"w1\",\"status\":\"ok\",\"href\":\"javascript:alert(1)\"},"
                + "{\"worker\":\"w1\",\"status\":\"checksum\",\"href\":\"dist-traces/r/w1/a-trace.zip\",\"reason\":\"sha256\"},"
                + "{\"worker\":{\"nested\":1},\"status\":\"ok\",\"href\":[\"x\"]},"
                + "\"not-an-object\"]}";
        Files.write(tmp.resolve(ReportMerger.TRACES_MANIFEST), manifest.getBytes(StandardCharsets.UTF_8));
        List<Map<String, Object>> rows = ReportMerger.traceRows(copy, "shard-a");
        assertEquals(7, rows.size());
        assertEquals("shard-a/dist-traces/r/w1/a-trace.zip", rows.get(0).get("href"));
        for (int i = 1; i < 7; i++) assertNull(rows.get(i).get("href"), "row " + i);
        assertEquals("missing", rows.get(1).get("status"));
        assertEquals("missing", rows.get(2).get("status"));
        assertEquals("checksum", rows.get(5).get("status"));
        assertNull(rows.get(6).get("worker"), "non-scalar values are dropped");
        String html = ReportMerger.tracesHtml(rows, "t");
        assertFalse(html.contains("javascript:"), "a hostile href is never rendered");
        assertFalse(html.contains("/etc/passwd"));
        assertTrue(html.contains("1 linked, 6 unavailable"));
    }

    @Test
    void noManifestOrAGarbledOneGivesNoRows(@TempDir Path tmp) throws Exception {
        assertTrue(ReportMerger.traceRows(tmp.toFile(), "s").isEmpty());
        Files.write(tmp.resolve(ReportMerger.TRACES_MANIFEST), "{not json".getBytes(StandardCharsets.UTF_8));
        assertTrue(ReportMerger.traceRows(tmp.toFile(), "s").isEmpty());
        Files.write(tmp.resolve(ReportMerger.TRACES_MANIFEST), "{\"traces\":42}".getBytes(StandardCharsets.UTF_8));
        assertTrue(ReportMerger.traceRows(tmp.toFile(), "s").isEmpty());
    }

    @Test
    void aMergeWithoutTracesWritesNoTraceFilesAndARemergeClearsOldOnes(@TempDir Path tmp) throws Exception {
        Path in = Files.createDirectories(tmp.resolve("in"));
        Files.write(in.resolve("gatf-results.json"), "{\"summary\":{},\"tests\":[]}".getBytes(StandardCharsets.UTF_8));
        File dest = tmp.resolve("m").toFile();
        Files.createDirectories(dest.toPath());
        Files.write(new File(dest, ReportMerger.TRACES_PAGE).toPath(), "stale".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(dest, ReportMerger.TRACES_MANIFEST).toPath(), "{}".getBytes(StandardCharsets.UTF_8));
        ReportMerger.Summary s = ReportMerger.merge(java.util.Collections.singletonList(in.toFile()), dest);
        assertEquals(0, s.traces);
        assertFalse(new File(dest, ReportMerger.TRACES_PAGE).exists());
        assertFalse(new File(dest, ReportMerger.TRACES_MANIFEST).exists());
        String idx = new String(Files.readAllBytes(new File(dest, "index.data.json").toPath()), StandardCharsets.UTF_8);
        assertFalse(idx.contains("\"traces\""), "the index is unchanged for non-distributed merges");
    }
}
