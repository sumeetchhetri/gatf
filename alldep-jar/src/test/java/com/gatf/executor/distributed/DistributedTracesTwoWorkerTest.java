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
package com.gatf.executor.distributed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatf.log.ReportMerger;

/**
 * H8 (Δ162): R2. Two workers and a coordinator in one JVM over piped streams wrapped in the channel's Kryo
 * Input/Output, following the Listener (worker) and Tester (coordinator) order: status JSON with references, trace
 * frames, then the results zip. The merged report must link every trace of both workers. Also the v1/v2 handshake.
 */
class DistributedTracesTwoWorkerTest {

    static final ObjectMapper OM = new ObjectMapper();

    static Path zip(Path dir, String rel, String body) throws Exception {
        Path p = dir.resolve(rel);
        Files.createDirectories(p.getParent());
        try (ZipOutputStream z = new ZipOutputStream(Files.newOutputStream(p))) {
            z.putNextEntry(new ZipEntry("metadata.json"));
            z.write(("{\"format\":2,\"outcome\":\"passed\",\"b\":\"" + body + "\"}").getBytes(StandardCharsets.UTF_8));
            z.closeEntry();
        }
        return p;
    }

    /** The worker side of one SELENIUM_REQ, as DistributedGatfListener does it for a v2 coordinator. */
    static void worker(String node, int index, Path out, Output oos, int protocol, long coordinatorCap) throws Exception {
        String runPrefix = "DRun-" + index;
        DistributedTraces.Collector col = protocol >= DistributedTraces.TRACE_PROTOCOL ? DistributedTraces.open(node, runPrefix) : null;
        // the executor writes traces on its own pool threads and calls the hook (GatfTestCaseExecutorUtil ~1324)
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<?>> f = new ArrayList<Future<?>>();
            for (String script : new String[] {"tests/login.sel", "tests/cart.sel"}) {
                f.add(pool.submit(() -> {
                    String file = runPrefix + "-1-1-" + (script.contains("login") ? 1 : 2) + "-chrome";
                    Path z = zip(out, "1700000" + index + "/" + file + "-trace.zip", node + script);
                    DistributedTraces.recorded(node, runPrefix, script, "chrome", runPrefix + "-1-1", z);
                    return null;
                }));
            }
            for (Future<?> x : f) x.get();
        } finally {
            pool.shutdown();
            if (col != null) col.close();
        }
        DistributedTestStatus report = new DistributedTestStatus();
        report.setNode(node);
        report.setIdentifier(runPrefix);
        report.setZipFileName("results.zip");
        long cap = DistributedTraces.effectiveCap(coordinatorCap);
        if (col != null) report.setTraces(col.references(out.toFile(), node, cap, DistributedTraces.maxCount()));
        oos.writeInt(DistributedAcceptanceContext.Command.TESTS_SHARE_RES.ordinal());
        oos.writeString(OM.writeValueAsString(report));
        oos.flush();
        if (col != null) {
            DistributedTraces.writeFrames(oos, out, report.getTraces(), cap);
            oos.flush();
        }
        byte[] results = ("RESULTS-OF-" + node).getBytes(StandardCharsets.UTF_8);
        oos.write(results, 0, results.length);
        oos.close();
    }

    /** The coordinator side, as DistributedGatfTester's result task does it. Returns the results-zip bytes it read. */
    static String coordinator(Input ois, String node, File report, String run, int protocol) throws Exception {
        assertEquals(DistributedAcceptanceContext.Command.TESTS_SHARE_RES.ordinal(), ois.readInt());
        DistributedTestStatus res = OM.readValue(ois.readString(), DistributedTestStatus.class);
        DistributedTraces.Received r = null;
        if (protocol >= DistributedTraces.TRACE_PROTOCOL) {
            r = DistributedTraces.receive(ois, res.getTraces(), report, run, node, DistributedTraces.maxBytes(), DistributedTraces.maxCount());
            assertTrue(r.inSync, r.summary(node));
        } else {
            assertNull(res.getTraces(), "a v1 exchange carries no references");
        }
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        int n;
        while ((n = ois.read(buf, 0, buf.length)) > 0) b.write(buf, 0, n);
        if (r != null) {   // after the results zip is extracted
            DistributedTraces.reverify(report, r.entries);
            assertTrue(DistributedTraces.publish(report, run, r.entries));
        }
        return new String(b.toByteArray(), StandardCharsets.UTF_8);
    }

    @Test
    @SuppressWarnings("unchecked")
    void twoWorkersOneRunTheMergedReportLinksEveryTrace(@TempDir Path tmp) throws Exception {
        File report = Files.createDirectories(tmp.resolve("coordinator/out")).toFile();
        String run = DistributedTraces.runIdFor(Files.write(tmp.resolve("5f1e2d3c-0000-0000-0000-000000000000.zip"), new byte[] {1}).toFile());
        String[] nodes = {"worker-a@9567", "worker-b@9568", "worker-old@9569"};
        int[] protocol = {2, 2, 1};
        ExecutorService ex = Executors.newFixedThreadPool(6);
        List<Future<String>> coord = new ArrayList<Future<String>>();
        try {
            for (int i = 0; i < nodes.length; i++) {
                PipedOutputStream po = new PipedOutputStream();
                PipedInputStream pi = new PipedInputStream(po, 1024);   // small pipe: frames really stream
                Path wout = Files.createDirectories(tmp.resolve("w" + i + "/out"));
                final int k = i;
                ex.submit(() -> { worker(nodes[k], k + 2, wout, new Output(po), protocol[k], DistributedTraces.maxBytes()); return null; });
                coord.add(ex.submit(() -> coordinator(new Input(pi), nodes[k], report, run, protocol[k])));
            }
            for (int i = 0; i < nodes.length; i++) assertEquals("RESULTS-OF-" + nodes[i], coord.get(i).get(), "results zip stays intact");
        } finally {
            ex.shutdownNow();
        }

        Map<String, Object> manifest = OM.readValue(new File(report, DistributedTraces.MANIFEST), Map.class);
        List<Map<String, Object>> traces = (List<Map<String, Object>>) manifest.get("traces");
        assertEquals(4, traces.size(), "2 traces x 2 v2 workers; the v1 worker has none");
        assertEquals(run, manifest.get("run"));
        String html = new String(Files.readAllBytes(new File(report, DistributedTraces.PAGE).toPath()), StandardCharsets.UTF_8);
        for (Map<String, Object> t : traces) {
            assertEquals("ok", t.get("status"));
            String href = (String) t.get("href");
            assertTrue(new File(report, href).isFile(), href);
            assertTrue(html.contains("href=\"" + href + "\""), href);
        }
        assertTrue(new File(report, "dist-traces/" + run + "/worker-a_9567/login__chrome__DRun-2-1-1-trace.zip").isFile());
        assertTrue(new File(report, "dist-traces/" + run + "/worker-b_9568/cart__chrome__DRun-3-1-1-trace.zip").isFile());
        assertTrue(html.contains("4 linked, 0 unavailable"));

        // merge-reports over the coordinator's out dir: every trace linked into the shard copy
        Files.write(new File(report, "gatf-results.json").toPath(),
                "{\"summary\":{\"total\":0,\"durationMs\":1},\"tests\":[]}".getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream so = new ByteArrayOutputStream();
        int code = ReportMerger.run(new String[] {report.getPath(), "-o", tmp.resolve("merged").toString()}, tmp.toFile(),
                new PrintStream(so, true, "UTF-8"), new PrintStream(new ByteArrayOutputStream(), true, "UTF-8"));
        assertEquals(0, code, "traces never change the exit code");
        assertTrue(so.toString("UTF-8").contains("Traces: 4 linked, 0 unavailable"), so.toString("UTF-8"));
        File merged = tmp.resolve("merged").toFile();
        Map<String, Object> idx = OM.readValue(new File(merged, "index.data.json"), Map.class);
        List<Map<String, Object>> linked = (List<Map<String, Object>>) idx.get("traces");
        assertEquals(4, linked.size());
        for (Map<String, Object> t : linked) {
            String href = (String) t.get("href");
            assertNotNull(href);
            assertTrue(href.startsWith(t.get("shard") + "/dist-traces/"), href);
            assertTrue(new File(merged, href).isFile(), href);
        }
        assertTrue(new File(merged, ReportMerger.TRACES_PAGE).isFile());
    }

    @Test
    void aZeroCapSendsNoZipsListsThemOversizedAndKeepsTheResults(@TempDir Path tmp) throws Exception {
        String prev = System.getProperty("gatf.dist.trace.maxBytes");
        System.setProperty("gatf.dist.trace.maxBytes", "0");
        try {
            PipedOutputStream po = new PipedOutputStream();
            PipedInputStream pi = new PipedInputStream(po, 1024);
            Path wout = Files.createDirectories(tmp.resolve("w/out"));
            File report = Files.createDirectories(tmp.resolve("c")).toFile();
            ExecutorService ex = Executors.newSingleThreadExecutor();
            try {
                ex.submit(() -> { worker("w@1", 2, wout, new Output(po), 2, DistributedTraces.maxBytes()); return null; });
                assertEquals("RESULTS-OF-w@1", coordinator(new Input(pi), "w@1", report, "run", 2));
            } finally {
                ex.shutdownNow();
            }
            String html = new String(Files.readAllBytes(new File(report, DistributedTraces.PAGE).toPath()), StandardCharsets.UTF_8);
            assertTrue(html.contains("0 linked, 2 unavailable"), html);
            assertTrue(html.contains("oversized"));
        } finally {
            if (prev == null) System.clearProperty("gatf.dist.trace.maxBytes"); else System.setProperty("gatf.dist.trace.maxBytes", prev);
        }
    }

    @Test
    void aMissingZipInTheReportShowsAsMissingNotABrokenLink(@TempDir Path tmp) throws Exception {
        File report = Files.createDirectories(tmp.resolve("out")).toFile();
        Path wout = Files.createDirectories(tmp.resolve("w"));
        Path z = zip(wout, "s/a-trace.zip", "x");
        List<TraceRef> refs = Arrays.asList(DistributedTraces.describe(wout, z, "w", "DRun-2-1-1", "a.sel", "chrome", 1 << 20));
        ByteArrayOutputStream wire = new ByteArrayOutputStream();
        DistributedTraces.writeFrames(wire, wout, refs, 1 << 20);
        DistributedTraces.Received r = DistributedTraces.receive(new ByteArrayInputStream(wire.toByteArray()), refs, report, "run", "w", 1 << 20, 10);
        DistributedTraces.publish(report, "run", r.entries);
        Files.delete(new File(report, (String) r.entries.get(0).get("href")).toPath());   // lost after the run
        Files.write(new File(report, "gatf-results.json").toPath(), "{\"summary\":{},\"tests\":[]}".getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream so = new ByteArrayOutputStream();
        int code = ReportMerger.run(new String[] {report.getPath(), "-o", tmp.resolve("m").toString()}, tmp.toFile(),
                new PrintStream(so, true, "UTF-8"), new PrintStream(new ByteArrayOutputStream(), true, "UTF-8"));
        assertEquals(0, code);
        assertTrue(so.toString("UTF-8").contains("0 linked, 1 unavailable"), so.toString("UTF-8"));
        String html = new String(Files.readAllBytes(tmp.resolve("m").resolve(ReportMerger.TRACES_PAGE)), StandardCharsets.UTF_8);
        assertTrue(html.contains("missing"));
        assertFalse(html.contains("download>"), "no link for a missing zip");
    }

    // ---- handshake --------------------------------------------------------------------------------------------

    static int negotiate(int stamp, String clientSecret, String nodeSecret) {
        String prev = System.getProperty("gatf.dist.secret");
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            Output o = new Output(b);
            setSecret(clientSecret);
            DistributedTransport.clientHandshake(o, stamp);
            o.flush();
            setSecret(nodeSecret);
            return DistributedTransport.serverHandshakeVersion(new Input(new ByteArrayInputStream(b.toByteArray())));
        } finally {
            setSecret(prev);
        }
    }

    static void setSecret(String s) {
        if (s == null) System.clearProperty("gatf.dist.secret"); else System.setProperty("gatf.dist.secret", s);
    }

    @Test
    void nodesServeV1AndV2AndRefuseEverythingElse() {
        assertEquals(2, DistributedTransport.PROTOCOL_VERSION);
        assertEquals(2, negotiate(2, null, null));
        assertEquals(1, negotiate(1, null, null), "an older coordinator still works, without traces");
        assertEquals(0, negotiate(3, null, null));
        assertEquals(0, negotiate(0, null, null));
        assertEquals(0, negotiate(-1, null, null));
        assertEquals(2, negotiate(2, "s3cret", "s3cret"));
        assertEquals(0, negotiate(2, "wrong", "s3cret"));
        assertEquals(0, negotiate(2, null, "s3cret"));
        assertEquals(0, DistributedTransport.serverHandshakeVersion(new Input(new ByteArrayInputStream(new byte[0]))));
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        Output o = new Output(b);
        o.writeInt(2);   // Kryo's own int encoding, whatever its byte order
        o.flush();
        assertTrue(DistributedTransport.serverHandshake(new Input(new ByteArrayInputStream(b.toByteArray()))));
    }

    @Test
    void aV1NodeRefusesTheV2StampWhichIsWhyTheCoordinatorRetriesInV1() {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        Output o = new Output(b);
        DistributedTransport.clientHandshake(o);   // the default stamp is now v2
        o.flush();
        int stamp = new Input(new ByteArrayInputStream(b.toByteArray())).readInt();
        assertFalse(stamp == 1, "a v1 node (readInt() == 1) drops this connection");
        b.reset();
        o = new Output(b);
        DistributedTransport.clientHandshake(o, DistributedTransport.MIN_PROTOCOL_VERSION);
        o.flush();
        assertEquals(1, new Input(new ByteArrayInputStream(b.toByteArray())).readInt(), "the fallback stamp a v1 node accepts");
    }

    @Test
    void theCoordinatorCapRidesInTheTestContextAndOldNodesIgnoreIt() throws Exception {
        DistributedTestContext c = new DistributedTestContext();
        c.setIndex(2);
        c.setTraceMaxBytes(1234);
        String json = OM.writeValueAsString(c);
        assertEquals(1234, OM.readValue(json, DistributedTestContext.class).getTraceMaxBytes());
        assertEquals(-1, OM.readValue("{\"index\":2,\"numberOfRuns\":1}", DistributedTestContext.class).getTraceMaxBytes());
        assertTrue(Collections.singletonList(json).get(0).contains("traceMaxBytes"));
    }
}
