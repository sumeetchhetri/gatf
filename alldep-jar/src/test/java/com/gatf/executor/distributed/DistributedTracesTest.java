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
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;

/** H8 (Δ162): R1 for the trace reference model, naming, the wire frames and every hostile-reference reject. */
class DistributedTracesTest {

    static final long CAP = 1024 * 1024;

    static byte[] zipBytes(String entry, String content) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(b)) {
            z.putNextEntry(new ZipEntry(entry));
            z.write(content.getBytes(StandardCharsets.UTF_8));
            z.closeEntry();
        }
        return b.toByteArray();
    }

    static Path writeZip(Path dir, String rel, String content) throws IOException {
        Path p = dir.resolve(rel);
        Files.createDirectories(p.getParent());
        Files.write(p, zipBytes("metadata.json", content));
        return p;
    }

    /** One frame by hand: what a (possibly hostile) worker sends. */
    static void frame(DataOutputStream d, int idx, long len, byte[] bytes) throws IOException {
        d.writeInt(idx);
        d.writeLong(len);
        if (bytes != null) d.write(bytes);
    }

    static TraceRef ref(String rel, byte[] bytes) throws Exception {
        return new TraceRef("w1", "DRun-2-1-1", "tests/login.sel", "chrome", rel, bytes.length, DistributedTraces.hex(
                java.security.MessageDigest.getInstance("SHA-256").digest(bytes)));
    }

    static String status(DistributedTraces.Received r, int i) { return String.valueOf(r.entries.get(i).get("status")); }

    // ---- reference model ------------------------------------------------------------------------------------

    @Test
    void safeRelPathAcceptsPlainRelativePathsOnly() {
        for (String ok : new String[] {"a.zip", "17000/DRun-2-1-1-1-chrome-trace.zip", "a/b/c-trace.zip", "x@y+z.zip"}) {
            assertTrue(DistributedTraces.safeRelPath(ok), ok);
        }
        for (String bad : new String[] {null, "", "/etc/passwd", "../a.zip", "a/../../b.zip", "a/./b.zip", ".", "..", "a//b.zip",
                "a/b/", "C:/x.zip", "C:\\x.zip", "a\\..\\b.zip", "\\\\host\\share\\x.zip", "file:/x.zip", "a/b c.zip",
                "a/\u0000.zip", "a/\u00e9.zip", "a\n.zip", "%2e%2e/x.zip", "~/x.zip"}) {
            assertFalse(DistributedTraces.safeRelPath(bad), String.valueOf(bad));
        }
        StringBuilder longPath = new StringBuilder();
        while (longPath.length() < 600) longPath.append("abcdefgh/");
        assertFalse(DistributedTraces.safeRelPath(longPath + "x.zip"));
    }

    @Test
    void rejectChecksSizeCapPathExtensionAndShaFormat() throws Exception {
        byte[] z = zipBytes("a", "b");
        assertNull(DistributedTraces.reject(ref("s/a-trace.zip", z), CAP));
        TraceRef r = ref("s/a-trace.zip", z);
        r.setSize(CAP + 1);
        assertEquals(DistributedTraces.OVERSIZED, DistributedTraces.reject(r, CAP)[0]);
        r = ref("s/a-trace.zip", z);
        r.setSize(-5);
        assertEquals(DistributedTraces.REJECTED, DistributedTraces.reject(r, CAP)[0]);
        assertEquals(DistributedTraces.REJECTED, DistributedTraces.reject(ref("../a.zip", z), CAP)[0]);
        assertEquals(DistributedTraces.REJECTED, DistributedTraces.reject(ref("s/a.txt", z), CAP)[0]);
        r = ref("s/a.zip", z);
        r.setSha256("ABCDEF");
        assertEquals(DistributedTraces.REJECTED, DistributedTraces.reject(r, CAP)[0]);
        r.setSha256(null);
        assertEquals(DistributedTraces.REJECTED, DistributedTraces.reject(r, CAP)[0]);
        assertEquals(DistributedTraces.REJECTED, DistributedTraces.reject(null, CAP)[0]);
    }

    @Test
    void referenceRoundTripsAsJsonInsideTheStatus() throws Exception {
        DistributedTestStatus st = new DistributedTestStatus();
        st.setNode("w1");
        st.setTraces(Arrays.asList(ref("a/b-trace.zip", zipBytes("a", "b"))));
        ObjectMapper om = new ObjectMapper();
        DistributedTestStatus back = om.readValue(om.writeValueAsString(st), DistributedTestStatus.class);
        assertEquals(1, back.getTraces().size());
        assertEquals("a/b-trace.zip", back.getTraces().get(0).getRelPath());
        assertEquals(st.getTraces().get(0).getSha256(), back.getTraces().get(0).getSha256());
        // an older node's status (no traces field) still reads
        DistributedTestStatus old = om.readValue("{\"node\":\"w1\",\"identifier\":\"DRun-2\"}", DistributedTestStatus.class);
        assertNull(old.getTraces());
    }

    // ---- naming ---------------------------------------------------------------------------------------------

    @Test
    void storeNamesAreFlatSafeAndCarryScriptSessionAndRun() {
        assertEquals("login__chrome__DRun-2-1-1-trace.zip", DistributedTraces.storeName("tests/ui/login.sel", "chrome", "DRun-2-1-1"));
        assertEquals("login__DRun-2-1-1-trace.zip", DistributedTraces.storeName("login.sel", null, "DRun-2-1-1"));
        String hostile = DistributedTraces.storeName("../../../etc/passwd", "../../x", "/abs/../run");
        assertFalse(hostile.contains("/"), hostile);
        assertFalse(hostile.contains("\\"), hostile);
        assertFalse(hostile.startsWith("."), hostile);
        assertTrue(hostile.endsWith("-trace.zip"));
        String win = DistributedTraces.storeName("C:\\suite\\a.sel", "s", "r");
        assertEquals("a__s__r-trace.zip", win);
        char[] big = new char[500];
        Arrays.fill(big, 'x');
        assertTrue(DistributedTraces.storeName(new String(big), new String(big), new String(big)).length() < 220);
        assertEquals("_..", DistributedTraces.token("..", 10));
        assertEquals("_", DistributedTraces.token(null, 10));
        assertEquals("host_9567", DistributedTraces.token("host@9567", 96));
    }

    @Test
    void runIdIsSharedByEveryNodeOfOneRun(@TempDir Path tmp) throws Exception {
        File z = tmp.resolve("0b6f2a9c-1111-2222-3333-444455556666.zip").toFile();
        Files.write(z.toPath(), new byte[] {1});
        String a = DistributedTraces.runIdFor(z), b = DistributedTraces.runIdFor(z);
        assertEquals(a, b);
        assertTrue(a.matches("\\d{8}-\\d{6}-0b6f2a9c"), a);
        assertTrue(DistributedTraces.runIdFor(null).matches("\\d{8}-\\d{6}-[0-9a-f]{8}"));
    }

    // ---- worker side ----------------------------------------------------------------------------------------

    @Test
    void collectorRecordsOnlyItsOwnOpenRun(@TempDir Path tmp) throws Exception {
        Path z = writeZip(tmp, "s1/DRun-2-1-1-1-chrome-trace.zip", "x");
        DistributedTraces.recorded("w1", "DRun-2", "a.sel", "chrome", "DRun-2-1-1", z);   // nothing open: no-op
        DistributedTraces.Collector c = DistributedTraces.open("w1", "DRun-2");
        DistributedTraces.Collector other = DistributedTraces.open("w1", "DRun-3");
        try {
            DistributedTraces.recorded("w1", "DRun-2", "a.sel", "chrome", "DRun-2-1-1", z);
            DistributedTraces.recorded("w1", "LRun-1", "a.sel", "chrome", "LRun-1-1-1", z);   // a local run
            DistributedTraces.recorded("w9", "DRun-2", "a.sel", "chrome", "DRun-2-1-1", z);   // another coordinator's name
            DistributedTraces.recorded("w1", "DRun-2", "a.sel", "chrome", "DRun-2-1-1", null);
            assertEquals(1, c.size());
            assertEquals(0, other.size());
        } finally {
            c.close();
            other.close();
        }
        DistributedTraces.recorded("w1", "DRun-2", "a.sel", "chrome", "DRun-2-1-1", z);
        assertEquals(1, c.size());
    }

    @Test
    void describeRefusesSymlinksAndFilesOutsideTheOutDir(@TempDir Path tmp) throws Exception {
        Path out = Files.createDirectories(tmp.resolve("out"));
        Path inside = writeZip(out, "s/a-trace.zip", "x");
        Path outside = writeZip(tmp, "elsewhere/b-trace.zip", "y");
        TraceRef r = DistributedTraces.describe(out, inside, "w1", "run", "a.sel", "chrome", CAP);
        assertNotNull(r);
        assertEquals("s/a-trace.zip", r.getRelPath());
        assertEquals(Files.size(inside), r.getSize());
        assertEquals(DistributedTraces.sha256(inside), r.getSha256());
        assertNull(DistributedTraces.describe(out, outside, "w1", "run", "b.sel", null, CAP));
        Path link = out.resolve("s/link-trace.zip");
        Files.createSymbolicLink(link, outside);
        assertNull(DistributedTraces.describe(out, link, "w1", "run", "b.sel", null, CAP));
        Path dirLink = out.resolve("d");
        Files.createSymbolicLink(dirLink, outside.getParent());
        assertNull(DistributedTraces.describe(out, dirLink.resolve("b-trace.zip"), "w1", "run", "b.sel", null, CAP));
        assertNull(DistributedTraces.describe(out, out.resolve("s/gone-trace.zip"), "w1", "run", "b.sel", null, CAP));
        TraceRef big = DistributedTraces.describe(out, inside, "w1", "run", "a.sel", "chrome", 10);
        assertNotNull(big);
        assertNull(big.getSha256(), "over the cap: not hashed, not sent");
    }

    @Test
    void collectorReferencesHonourTheCountCap(@TempDir Path tmp) throws Exception {
        DistributedTraces.Collector c = DistributedTraces.open("w1", "DRun-7");
        try {
            for (int i = 0; i < 5; i++) {
                DistributedTraces.recorded("w1", "DRun-7", "s" + i + ".sel", "chrome", "DRun-7-1-1", writeZip(tmp, "s/" + i + "-trace.zip", "z" + i));
            }
            List<TraceRef> refs = c.references(tmp.toFile(), "w1", CAP, 3);
            assertEquals(3, refs.size());
            assertEquals(2, c.dropped());
        } finally {
            c.close();
        }
        assertEquals(Math.min(DistributedTraces.maxBytes(), 10L), DistributedTraces.effectiveCap(10L));
        assertEquals(DistributedTraces.maxBytes(), DistributedTraces.effectiveCap(-1L));
        assertEquals(0L, DistributedTraces.effectiveCap(0L), "a coordinator cap of 0 turns transfer off");
    }

    // ---- frames: happy path + merge links -------------------------------------------------------------------

    @Test
    void framesRoundTripStoreVerifyAndLink(@TempDir Path tmp) throws Exception {
        Path wout = Files.createDirectories(tmp.resolve("worker-out"));
        Path z1 = writeZip(wout, "sess/DRun-2-1-1-1-chrome-trace.zip", "one");
        Path z2 = writeZip(wout, "sess/DRun-2-1-1-2-firefox-trace.zip", "two");
        List<TraceRef> refs = new ArrayList<TraceRef>();
        refs.add(DistributedTraces.describe(wout, z1, "w1", "DRun-2-1-1", "tests/a.sel", "chrome", CAP));
        refs.add(DistributedTraces.describe(wout, z2, "w1", "DRun-2-1-1", "tests/b.sel", "firefox", CAP));
        ByteArrayOutputStream wire = new ByteArrayOutputStream();
        DistributedTraces.writeFrames(wire, wout, refs, CAP);
        wire.write("RESULTS-ZIP".getBytes(StandardCharsets.UTF_8));   // what follows the frames on the real channel

        File cout = tmp.resolve("coord-out").toFile();
        ByteArrayInputStream in = new ByteArrayInputStream(wire.toByteArray());
        DistributedTraces.Received r = DistributedTraces.receive(in, refs, cout, "20261013-120000-abcd1234", "w1@9567", CAP, 100);
        assertTrue(r.inSync);
        assertEquals(2, r.count(DistributedTraces.OK));
        assertEquals("RESULTS-ZIP", new String(in.readAllBytes(), StandardCharsets.UTF_8), "stream stays aligned for the results zip");
        String h1 = (String) r.entries.get(0).get("href");
        assertEquals("dist-traces/20261013-120000-abcd1234/w1_9567/a__chrome__DRun-2-1-1-trace.zip", h1);
        assertTrue(Arrays.equals(Files.readAllBytes(z1), Files.readAllBytes(new File(cout, h1).toPath())));
        assertEquals("w1@9567", r.entries.get(0).get("worker"));

        assertTrue(DistributedTraces.publish(cout, "20261013-120000-abcd1234", r.entries));
        String html = new String(Files.readAllBytes(new File(cout, DistributedTraces.PAGE).toPath()), StandardCharsets.UTF_8);
        assertTrue(html.contains("href=\"" + h1 + "\""), html);
        assertTrue(html.contains("/app/?trace="));
        assertTrue(html.contains("2 linked, 0 unavailable"));
        assertFalse(new File(cout, "dist-traces/20261013-120000-abcd1234/w1_9567").list((d, n) -> n.endsWith(".part")).length > 0);
    }

    @Test
    void sameNameTwiceGetsASuffixNeverAnOverwrite(@TempDir Path tmp) throws Exception {
        Path wout = Files.createDirectories(tmp.resolve("w"));
        Path z = writeZip(wout, "s/a-trace.zip", "same");
        File cout = tmp.resolve("c").toFile();
        List<String> hrefs = new ArrayList<String>();
        for (int i = 0; i < 3; i++) {
            List<TraceRef> refs = Arrays.asList(DistributedTraces.describe(wout, z, "w1", "DRun-2-1-1", "a.sel", "chrome", CAP));
            ByteArrayOutputStream wire = new ByteArrayOutputStream();
            DistributedTraces.writeFrames(wire, wout, refs, CAP);
            hrefs.add((String) DistributedTraces.receive(new ByteArrayInputStream(wire.toByteArray()), refs, cout, "run", "w1", CAP, 10).entries.get(0).get("href"));
        }
        assertEquals("dist-traces/run/w1/a__chrome__DRun-2-1-1-trace.zip", hrefs.get(0));
        assertEquals("dist-traces/run/w1/a__chrome__DRun-2-1-1-2-trace.zip", hrefs.get(1));
        assertEquals("dist-traces/run/w1/a__chrome__DRun-2-1-1-3-trace.zip", hrefs.get(2));
    }

    // ---- frames: hostile workers ----------------------------------------------------------------------------

    @Test
    void checksumMismatchIsReportedNotStoredAndTheStreamStaysInSync(@TempDir Path tmp) throws Exception {
        byte[] good = zipBytes("m", "good"), evil = good.clone();
        evil[evil.length - 1] ^= 1;   // same length, one bit off
        TraceRef lying = ref("s/a-trace.zip", good);   // the reference describes "good" ...
        TraceRef honest = ref("s/b-trace.zip", good);
        honest.setScript("b.sel");
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        DataOutputStream d = new DataOutputStream(b);
        d.writeInt(2);
        frame(d, 0, evil.length, evil);                 // ... but other bytes of the same length arrive
        frame(d, 1, good.length, good);
        d.writeBytes("TAIL");
        ByteArrayInputStream in = new ByteArrayInputStream(b.toByteArray());
        File cout = tmp.toFile();
        DistributedTraces.Received r = DistributedTraces.receive(in, Arrays.asList(lying, honest), cout, "run", "w1", CAP, 10);
        assertTrue(r.inSync);
        assertEquals(DistributedTraces.CHECKSUM, status(r, 0));
        assertNull(r.entries.get(0).get("href"));
        assertEquals(DistributedTraces.OK, status(r, 1));
        assertEquals("TAIL", new String(in.readAllBytes(), StandardCharsets.US_ASCII));
        assertEquals(1, new File(cout, "dist-traces/run/w1").list().length, "only the verified zip is kept");
    }

    @Test
    void nonZipContentIsRejectedEvenWithAMatchingChecksum(@TempDir Path tmp) throws Exception {
        byte[] html = "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8);
        TraceRef r0 = ref("s/a-trace.zip", html);
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        DataOutputStream d = new DataOutputStream(b);
        d.writeInt(1);
        frame(d, 0, html.length, html);
        DistributedTraces.Received r = DistributedTraces.receive(new ByteArrayInputStream(b.toByteArray()), Arrays.asList(r0), tmp.toFile(), "run", "w1", CAP, 10);
        assertEquals(DistributedTraces.REJECTED, status(r, 0));
        assertFalse(new File(tmp.toFile(), "dist-traces/run/w1").exists() && new File(tmp.toFile(), "dist-traces/run/w1").list().length > 0);
    }

    @Test
    void traversalReferencesAreDrainedAndRejectedNeverWritten(@TempDir Path tmp) throws Exception {
        byte[] z = zipBytes("m", "x");
        File cout = Files.createDirectories(tmp.resolve("report")).toFile();
        List<TraceRef> refs = new ArrayList<TraceRef>();
        for (String p : new String[] {"../../evil.zip", "/tmp/evil.zip", "C:/evil.zip", "a\\..\\..\\evil.zip"}) refs.add(ref(p, z));
        TraceRef nameAttack = ref("ok/a-trace.zip", z);   // a safe path but a hostile script / run / session
        nameAttack.setScript("../../../../tmp/pwn");
        nameAttack.setRunId("../..");
        nameAttack.setSession("/etc/cron.d/x");
        refs.add(nameAttack);
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        DataOutputStream d = new DataOutputStream(b);
        d.writeInt(refs.size());
        for (int i = 0; i < refs.size(); i++) frame(d, i, z.length, z);
        DistributedTraces.Received r = DistributedTraces.receive(new ByteArrayInputStream(b.toByteArray()), refs, cout, "run", "../../w1", CAP, 10);
        assertTrue(r.inSync);
        for (int i = 0; i < 4; i++) assertEquals(DistributedTraces.REJECTED, status(r, i));
        assertEquals(DistributedTraces.OK, status(r, 4));
        String href = (String) r.entries.get(4).get("href");
        assertTrue(href.startsWith("dist-traces/run/"), href);
        assertTrue(DistributedTraces.safeRelPath(href), href);
        assertTrue(new File(cout, href).getCanonicalPath().startsWith(cout.getCanonicalPath() + File.separator));
        assertFalse(new File(tmp.toFile(), "evil.zip").exists());
        assertFalse(tmp.resolve("pwn").toFile().exists());
    }

    @Test
    void anOversizedFrameAbortsTheTraceStreamWithoutReadingIt(@TempDir Path tmp) throws Exception {
        byte[] z = zipBytes("m", "x");
        TraceRef r0 = ref("s/a-trace.zip", z), r1 = ref("s/b-trace.zip", z);
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        DataOutputStream d = new DataOutputStream(b);
        d.writeInt(2);
        frame(d, 0, Long.MAX_VALUE, null);   // claims an endless body
        DistributedTraces.Received r = DistributedTraces.receive(new ByteArrayInputStream(b.toByteArray()), Arrays.asList(r0, r1), tmp.toFile(), "run", "w1", CAP, 10);
        assertFalse(r.inSync);
        assertEquals(DistributedTraces.OVERSIZED, status(r, 0));
        assertEquals(DistributedTraces.MISSING, status(r, 1));
        assertTrue(r.summary("w1").contains("aborted"));
    }

    @Test
    void anOversizedReferenceSentAsNotSentIsShownAsOversized(@TempDir Path tmp) throws Exception {
        Path wout = Files.createDirectories(tmp.resolve("w"));
        Path z = writeZip(wout, "s/a-trace.zip", "a fairly long body to exceed a tiny cap ............................");
        long tiny = 40;
        List<TraceRef> refs = Arrays.asList(DistributedTraces.describe(wout, z, "w1", "r", "a.sel", null, tiny));
        ByteArrayOutputStream wire = new ByteArrayOutputStream();
        DistributedTraces.writeFrames(wire, wout, refs, tiny);
        DistributedTraces.Received r = DistributedTraces.receive(new ByteArrayInputStream(wire.toByteArray()), refs, tmp.resolve("c").toFile(), "run", "w1", tiny, 10);
        assertTrue(r.inSync);
        assertEquals(DistributedTraces.OVERSIZED, status(r, 0));
    }

    @Test
    void badCountsLengthsDuplicatesAndTruncationNeverThrow(@TempDir Path tmp) throws Exception {
        byte[] z = zipBytes("m", "x");
        List<TraceRef> refs = Arrays.asList(ref("s/a-trace.zip", z), ref("s/b-trace.zip", z));

        // more frames than references
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        new DataOutputStream(b).writeInt(3);
        DistributedTraces.Received r = DistributedTraces.receive(new ByteArrayInputStream(b.toByteArray()), refs, tmp.toFile(), "run", "w1", CAP, 10);
        assertFalse(r.inSync);
        assertEquals(DistributedTraces.MISSING, status(r, 0));

        // negative count, negative length
        b = new ByteArrayOutputStream();
        new DataOutputStream(b).writeInt(-1);
        assertFalse(DistributedTraces.receive(new ByteArrayInputStream(b.toByteArray()), refs, tmp.toFile(), "run", "w1", CAP, 10).inSync);
        b = new ByteArrayOutputStream();
        DataOutputStream d = new DataOutputStream(b);
        d.writeInt(1);
        frame(d, 0, -7, null);
        assertFalse(DistributedTraces.receive(new ByteArrayInputStream(b.toByteArray()), refs, tmp.toFile(), "run", "w1", CAP, 10).inSync);

        // duplicate + unknown index: drained as strays; "not sent" -> missing; length lie -> rejected
        b = new ByteArrayOutputStream();
        d = new DataOutputStream(b);
        d.writeInt(2);
        frame(d, 0, z.length, z);
        frame(d, 0, z.length, z);
        d.writeBytes("TAIL");
        ByteArrayInputStream in = new ByteArrayInputStream(b.toByteArray());
        r = DistributedTraces.receive(in, refs, tmp.toFile(), "run", "w1", CAP, 10);
        assertTrue(r.inSync);
        assertEquals(DistributedTraces.OK, status(r, 0));
        assertEquals(DistributedTraces.MISSING, status(r, 1));
        assertEquals(DistributedTraces.REJECTED, status(r, 2));   // the stray duplicate
        assertEquals("TAIL", new String(in.readAllBytes(), StandardCharsets.US_ASCII));

        b = new ByteArrayOutputStream();
        d = new DataOutputStream(b);
        d.writeInt(2);
        frame(d, 99, z.length, z);
        frame(d, 1, -1, null);
        r = DistributedTraces.receive(new ByteArrayInputStream(b.toByteArray()), refs, tmp.toFile(), "run", "w1", CAP, 10);
        assertTrue(r.inSync);
        assertEquals(DistributedTraces.MISSING, status(r, 0));
        assertEquals(DistributedTraces.MISSING, status(r, 1));
        assertEquals(DistributedTraces.REJECTED, status(r, 2));

        b = new ByteArrayOutputStream();
        d = new DataOutputStream(b);
        d.writeInt(1);
        frame(d, 0, z.length - 1, Arrays.copyOf(z, z.length - 1));
        r = DistributedTraces.receive(new ByteArrayInputStream(b.toByteArray()), refs, tmp.toFile(), "run", "w1", CAP, 10);
        assertEquals(DistributedTraces.REJECTED, status(r, 0));

        // truncated mid-body: the partial file is removed
        b = new ByteArrayOutputStream();
        d = new DataOutputStream(b);
        d.writeInt(1);
        frame(d, 0, z.length, Arrays.copyOf(z, 5));
        File cout = tmp.resolve("trunc").toFile();
        r = DistributedTraces.receive(new ByteArrayInputStream(b.toByteArray()), refs, cout, "run", "w1", CAP, 10);
        assertFalse(r.inSync);
        assertEquals(DistributedTraces.MISSING, status(r, 0));
        assertEquals(0, new File(cout, "dist-traces/run/w1").list().length, "no .part file left behind");

        // empty stream (worker died before the frames), null references
        assertFalse(DistributedTraces.receive(new ByteArrayInputStream(new byte[0]), refs, tmp.toFile(), "run", "w1", CAP, 10).inSync);
        b = new ByteArrayOutputStream();
        new DataOutputStream(b).writeInt(0);
        r = DistributedTraces.receive(new ByteArrayInputStream(b.toByteArray()), null, tmp.toFile(), "run", "w1", CAP, 10);
        assertTrue(r.inSync);
        assertEquals(0, r.entries.size());
    }

    @Test
    void referencesBeyondTheCountCapAreDrainedAndSummarisedInOneRow(@TempDir Path tmp) throws Exception {
        byte[] z = zipBytes("m", "x");
        List<TraceRef> refs = new ArrayList<TraceRef>();
        for (int i = 0; i < 5; i++) {
            TraceRef t = ref("s/" + i + "-trace.zip", z);
            t.setScript("s" + i + ".sel");
            refs.add(t);
        }
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        DataOutputStream d = new DataOutputStream(b);
        d.writeInt(5);
        for (int i = 0; i < 5; i++) frame(d, i, z.length, z);
        d.writeBytes("TAIL");
        ByteArrayInputStream in = new ByteArrayInputStream(b.toByteArray());
        DistributedTraces.Received r = DistributedTraces.receive(in, refs, tmp.toFile(), "run", "w1", CAP, 2);
        assertTrue(r.inSync, "a worker with a larger count cap is drained, not cut off");
        assertEquals(3, r.entries.size());
        assertEquals(DistributedTraces.OK, status(r, 0));
        assertEquals(DistributedTraces.OK, status(r, 1));
        assertEquals(DistributedTraces.REJECTED, status(r, 2));
        assertEquals(6, ((Number) r.entries.get(2).get("count")).intValue(), "3 refs over the cap + 3 frames for them");
        assertEquals("TAIL", new String(in.readAllBytes(), StandardCharsets.US_ASCII));
    }

    @Test
    void aFloodOfReferencesCostsOneSummaryRow(@TempDir Path tmp) throws Exception {
        List<TraceRef> refs = new ArrayList<TraceRef>();
        TraceRef one = new TraceRef("w", "r", "a.sel", null, "s/a.zip", 10, null);
        for (int i = 0; i < 200_000; i++) refs.add(one);
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        new DataOutputStream(b).writeInt(0);
        DistributedTraces.Received r = DistributedTraces.receive(new ByteArrayInputStream(b.toByteArray()), refs, tmp.toFile(), "run", "w1", CAP, 10);
        assertEquals(11, r.entries.size());
        assertTrue(DistributedTraces.publish(tmp.toFile(), "run", r.entries));
        assertTrue(Files.size(tmp.resolve(DistributedTraces.MANIFEST)) < 64 * 1024);
    }

    @Test
    void aSymlinkedStoreDirIsRefusedAndNothingLandsOutsideTheReportDir(@TempDir Path tmp) throws Exception {
        byte[] z = zipBytes("m", "x");
        Path report = Files.createDirectories(tmp.resolve("report"));
        Path outside = Files.createDirectories(tmp.resolve("outside"));
        Files.createSymbolicLink(report.resolve(DistributedTraces.STORE_DIR), outside);
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        DataOutputStream d = new DataOutputStream(b);
        d.writeInt(1);
        frame(d, 0, z.length, z);
        d.writeBytes("TAIL");
        ByteArrayInputStream in = new ByteArrayInputStream(b.toByteArray());
        DistributedTraces.Received r = DistributedTraces.receive(in, Arrays.asList(ref("s/a-trace.zip", z)), report.toFile(), "run", "w1", CAP, 10);
        assertTrue(r.inSync);
        assertEquals(DistributedTraces.MISSING, status(r, 0));
        assertEquals("TAIL", new String(in.readAllBytes(), StandardCharsets.US_ASCII));
        try (java.util.stream.Stream<Path> s = Files.walk(outside)) {
            assertEquals(0L, s.filter(Files::isRegularFile).count());
        }
    }

    @Test
    void aPlantedSymlinkAtTheTargetNameIsNeitherFollowedNorReplaced(@TempDir Path tmp) throws Exception {
        byte[] z = zipBytes("m", "x");
        Path report = Files.createDirectories(tmp.resolve("report"));
        Path dir = Files.createDirectories(report.resolve("dist-traces/run/w1"));
        Path victim = Files.write(tmp.resolve("victim.txt"), "keep".getBytes(StandardCharsets.UTF_8));
        Files.createSymbolicLink(dir.resolve("login__chrome__DRun-2-1-1-trace.zip"), victim);
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        DataOutputStream d = new DataOutputStream(b);
        d.writeInt(1);
        frame(d, 0, z.length, z);
        DistributedTraces.Received r = DistributedTraces.receive(new ByteArrayInputStream(b.toByteArray()), Arrays.asList(ref("s/a-trace.zip", z)), report.toFile(), "run", "w1", CAP, 10);
        assertEquals(DistributedTraces.OK, status(r, 0));
        assertEquals("dist-traces/run/w1/login__chrome__DRun-2-1-1-2-trace.zip", r.entries.get(0).get("href"));
        assertEquals("keep", new String(Files.readAllBytes(victim), StandardCharsets.UTF_8));
    }

    // ---- manifest + page --------------------------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void publishAppendsWithinARunReplacesAcrossRunsAndEscapesEverything(@TempDir Path tmp) throws Exception {
        File out = tmp.toFile();
        TraceRef evil = new TraceRef("w", "<img src=x onerror=alert(1)>", "\"><script>alert(1)</script>.sel", "a'b", "s/a.zip", 1, null);
        DistributedTraces.Received r = new DistributedTraces.Received();
        Map<String, Object> e = new java.util.LinkedHashMap<String, Object>();
        e.put("worker", "w1");
        e.put("script", evil.getScript());
        e.put("session", evil.getSession());
        e.put("workerRun", evil.getRunId());
        e.put("status", "checksum");
        e.put("reason", "x<y");
        e.put("href", null);
        r.entries.add(e);
        assertTrue(DistributedTraces.publish(out, "run-1", r.entries));
        assertTrue(DistributedTraces.publish(out, "run-1", r.entries));
        ObjectMapper om = new ObjectMapper();
        Map<String, Object> m = om.readValue(new File(out, DistributedTraces.MANIFEST), Map.class);
        assertEquals(2, ((List<Object>) m.get("traces")).size());
        assertEquals("distributed-traces", m.get("type"));
        String html = new String(Files.readAllBytes(new File(out, DistributedTraces.PAGE).toPath()), StandardCharsets.UTF_8);
        assertFalse(html.contains("<script>alert"), html);
        assertFalse(html.contains("<img"), html);
        assertTrue(html.contains("&lt;script&gt;"));
        assertTrue(html.contains("0 linked, 2 unavailable"));
        assertTrue(DistributedTraces.publish(out, "run-2", r.entries));
        m = om.readValue(new File(out, DistributedTraces.MANIFEST), Map.class);
        assertEquals(1, ((List<Object>) m.get("traces")).size(), "a new run starts a new manifest");
        assertEquals("run-2", m.get("run"));
    }

    @Test
    void aZipOverwrittenByTheResultsExtractionLosesItsLink(@TempDir Path tmp) throws Exception {
        Path wout = Files.createDirectories(tmp.resolve("w"));
        List<TraceRef> refs = Arrays.asList(
                DistributedTraces.describe(wout, writeZip(wout, "s/a-trace.zip", "a"), "w1", "r", "a.sel", null, CAP),
                DistributedTraces.describe(wout, writeZip(wout, "s/b-trace.zip", "b"), "w1", "r", "b.sel", null, CAP),
                DistributedTraces.describe(wout, writeZip(wout, "s/c-trace.zip", "c"), "w1", "r", "c.sel", null, CAP));
        ByteArrayOutputStream wire = new ByteArrayOutputStream();
        DistributedTraces.writeFrames(wire, wout, refs, CAP);
        File cout = tmp.resolve("c").toFile();
        DistributedTraces.Received r = DistributedTraces.receive(new ByteArrayInputStream(wire.toByteArray()), refs, cout, "run", "w1", CAP, 10);
        // a hostile results zip, extracted into the same dir, swaps one stored zip, deletes one, plants a fake manifest
        Files.write(new File(cout, (String) r.entries.get(0).get("href")).toPath(), zipBytes("metadata.json", "forged"));
        Files.delete(new File(cout, (String) r.entries.get(1).get("href")).toPath());
        Files.write(new File(cout, DistributedTraces.MANIFEST).toPath(), ("{\"run\":\"run\",\"traces\":[{\"status\":\"ok\","
                + "\"href\":\"javascript:alert(1)\"}]}").getBytes(StandardCharsets.UTF_8));
        DistributedTraces.reverify(cout, r.entries);
        assertEquals(DistributedTraces.CHECKSUM, status(r, 0));
        assertNull(r.entries.get(0).get("href"));
        assertEquals(DistributedTraces.MISSING, status(r, 1));
        assertEquals(DistributedTraces.OK, status(r, 2));
        assertTrue(DistributedTraces.publish(cout, "run", r.entries));
        String manifest = new String(Files.readAllBytes(new File(cout, DistributedTraces.MANIFEST).toPath()), StandardCharsets.UTF_8);
        assertFalse(manifest.contains("javascript:"), "a planted manifest is never read back");
        String html = new String(Files.readAllBytes(new File(cout, DistributedTraces.PAGE).toPath()), StandardCharsets.UTF_8);
        assertTrue(html.contains("1 linked, 2 unavailable"), html);
    }

    @Test
    void thePageNeverLinksAnUnsafeHref() {
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        for (String h : new String[] {"javascript:alert(1)", "../x.zip", "/etc/passwd", "https://evil/x.zip", "ok/a-trace.zip"}) {
            Map<String, Object> m = new java.util.LinkedHashMap<String, Object>();
            m.put("status", "ok");
            m.put("href", h);
            rows.add(m);
        }
        String html = com.gatf.log.ReportMerger.tracesHtml(rows, "t");
        assertTrue(html.contains("1 linked, 4 unavailable"), html);
        for (String bad : new String[] {"javascript:", "../x.zip", "/etc/passwd", "evil"}) assertFalse(html.contains(bad), bad);
        assertTrue(html.contains("href=\"ok/a-trace.zip\""));
    }

    @Test
    void noSecretReachesReferencesManifestPageOrLogLine(@TempDir Path tmp) throws Exception {
        String secret = "S3cr3t-shared-" + System.nanoTime();
        String prev = System.getProperty("gatf.dist.secret");
        System.setProperty("gatf.dist.secret", secret);
        try {
            Path wout = Files.createDirectories(tmp.resolve("w"));
            Path z = writeZip(wout, "s/a-trace.zip", "x");
            List<TraceRef> refs = Arrays.asList(DistributedTraces.describe(wout, z, "w1", "DRun-2-1-1", "a.sel", "chrome", CAP));
            ByteArrayOutputStream wire = new ByteArrayOutputStream();
            DistributedTraces.writeFrames(wire, wout, refs, CAP);
            File cout = tmp.resolve("c").toFile();
            DistributedTraces.Received r = DistributedTraces.receive(new ByteArrayInputStream(wire.toByteArray()), refs, cout, "run", "w1", CAP, 10);
            DistributedTraces.publish(cout, "run", r.entries);
            String all = new ObjectMapper().writeValueAsString(refs) + r.summary("w1")
                    + new String(Files.readAllBytes(new File(cout, DistributedTraces.MANIFEST).toPath()), StandardCharsets.UTF_8)
                    + new String(Files.readAllBytes(new File(cout, DistributedTraces.PAGE).toPath()), StandardCharsets.UTF_8);
            assertFalse(all.contains(secret));
            assertFalse(new String(wire.toByteArray(), StandardCharsets.ISO_8859_1).contains(secret), "frames carry no secret");
        } finally {
            if (prev == null) System.clearProperty("gatf.dist.secret"); else System.setProperty("gatf.dist.secret", prev);
        }
    }
}
