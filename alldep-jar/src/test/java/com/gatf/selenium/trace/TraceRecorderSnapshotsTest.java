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
package com.gatf.selenium.trace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** H2 (Δ146), R2: per-action before/after snapshots through a fake transport — pairing, failure/end captures,
 *  format-2 lines that a v1 reader still understands, sha1-deduped stylesheets, and the slow/budget/count limits. */
class TraceRecorderSnapshotsTest {

    /** A transport serving rich snapshots: the HTML embeds a counter and the marks it was asked for. */
    static class Fake implements BrowserControl {
        final Set<Capability> caps;
        final List<String> calls = new ArrayList<>();
        int n;
        long sleepMs;
        String bigCss;
        Fake(Set<Capability> caps) { this.caps = caps; }
        public boolean supports(Capability c) { return caps.contains(c); }
        public void enableNetwork(NetworkListener l) { }
        public void disableNetwork() { }
        public void enableConsole(ConsoleListener l) { }
        public void disableConsole() { }
        int shotBytes = 3;
        /** raw outerHTML: Δ146a — never used for per-action snapshots (it leaks typed passwords and keeps scripts) */
        public String captureDomSnapshot() { calls.add("raw"); return "<html><body><input type=password value=hunter2><script>x()</script></body></html>"; }
        public RichSnapshot captureLiteSnapshot(List<Object> targets, List<String> marks) {
            calls.add("lite");
            RichSnapshot r = new RichSnapshot();
            r.source = "lite";
            r.html = "<html><body>lite " + (++n) + " " + marks + "</body></html>";
            for (int i = 0; i < targets.size(); i++) r.targetRects.add(targets.get(i) == null ? null : new double[] { 5, 6, 7, 8 });
            return r;
        }
        public byte[] captureScreenshot() { calls.add("shot"); return new byte[shotBytes]; }
        public byte[] fetchBody(String id) { return null; }
        public void close() { }
        public RichSnapshot captureRichSnapshot(List<Object> targets, List<String> marks) {
            calls.add("rich" + marks);
            if (sleepMs > 0) try { Thread.sleep(sleepMs); } catch (InterruptedException e) { }
            RichSnapshot r = new RichSnapshot();
            r.source = "cdp";
            r.html = "<html><body data-n=\"" + (++n) + "\" data-marks=\"" + marks + "\">" + (bigCss != null ? bigCss.substring(0, 15_000) : "") + "</body></html>";
            r.url = "https://app/";
            r.viewportW = 800; r.viewportH = 600;
            RichSnapshot.Style s = new RichSnapshot.Style();
            s.owner = "link"; s.href = "https://app/s.css"; s.media = ""; s.text = bigCss != null ? bigCss : ".a{color:red}";
            r.styles.add(s);
            RichSnapshot.Style x = new RichSnapshot.Style();
            x.owner = "link"; x.href = "https://cdn/x.css"; x.text = null;   // cross-origin
            r.styles.add(x);
            for (int i = 0; i < targets.size(); i++) r.targetRects.add(targets.get(i) == null ? null : new double[] { 1, 2.5, 30, 40 });
            return r;
        }
    }

    static Fake richBrowser() {
        return new Fake(EnumSet.of(Capability.DOM_SNAPSHOT, Capability.DOM_SNAPSHOT_RICH, Capability.SCREENSHOT));
    }

    static Map<String, String> zip(Path zip) throws Exception {
        Map<String, String> m = new HashMap<>();
        try (ZipFile z = new ZipFile(zip.toFile())) {
            for (java.util.zip.ZipEntry e : Collections.list(z.entries())) {
                m.put(e.getName(), new String(z.getInputStream(e).readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return m;
    }

    static List<String> lines(String s) {
        return s == null ? new ArrayList<>() : java.util.Arrays.stream(s.split("\\n")).filter(l -> !l.isBlank()).collect(Collectors.toList());
    }

    @Test
    void afterOfOneActionIsTheBeforeOfTheNextCapturedOnce(@TempDir Path dir) throws Exception {
        Fake b = richBrowser();
        TraceRecorder r = TraceRecorder.attach(b, new TraceConfig().setEnableTrace(true).setDomSnapshots("full"), dir.resolve("wd"), "a.sel", "chrome");
        assertEquals(1, r.action("click", "id@go", "el1"));
        assertEquals(2, r.action("autowait", "click 5ms", null));        // no snapshot for bookkeeping actions
        assertEquals(3, r.action("type", "id@name", "el2"));
        r.failure("boom");                                                // the failing step's after-state, now
        Map<String, String> z = zip(r.writeZip(dir.resolve("t.zip")));

        // 3 physical captures: before(1); after(1)+before(3); after(3) at the failure. Stop adds nothing (no pending).
        assertEquals(3, b.calls.stream().filter(c -> c.startsWith("rich")).count(), b.calls.toString());
        assertEquals("rich[1]", b.calls.get(0));
        assertTrue(b.calls.contains("rich[1, 3]"), "one capture marks both targets: " + b.calls);
        List<String> idx = lines(z.get("snapshots.ndjson"));
        assertEquals(4, idx.size(), idx.toString());
        assertTrue(idx.get(0).contains("\"action\":1,\"phase\":\"before\",\"capture\":1"), idx.get(0));
        assertTrue(idx.get(1).contains("\"action\":1,\"phase\":\"after\",\"capture\":2"), idx.get(1));
        assertTrue(idx.get(2).contains("\"action\":3,\"phase\":\"before\",\"capture\":2"), idx.get(2));
        assertTrue(idx.get(3).contains("\"action\":3,\"phase\":\"after\",\"capture\":3") && idx.get(3).contains("(failure)"), idx.get(3));
        // v1 fields stay, so the current viewer still shows the HTML; v2 adds the manifest + target rect
        for (String l : idx) {
            assertTrue(l.contains("\"html\":\"snapshots/snapshot-") && l.contains("\"n\":"), l);
            assertTrue(l.contains("\"v\":2") && l.contains("\"dom\":\"snapshots/snapshot-"), l);
            assertTrue(l.contains("\"target\":{\"rect\":[1,2.5,30,40]}"), l);
        }
        assertTrue(idx.get(1).contains("\"html\":\"snapshots/snapshot-2.html\"") && idx.get(2).contains("\"html\":\"snapshots/snapshot-2.html\""));
        // Δ146a: no per-action screenshots; the failure capture has one
        for (int i = 0; i < 3; i++) assertTrue(idx.get(i).contains("\"screenshot\":null"), idx.get(i));
        assertTrue(idx.get(3).contains("\"screenshot\":\"screenshots/shot-3.png\""), idx.get(3));
        assertEquals(1, b.calls.stream().filter(c -> c.equals("shot")).count(), b.calls.toString());
        assertFalse(b.calls.contains("raw"), "raw outerHTML never used");
        // manifest: styles go to resources/<sha1>, deduped across captures; cross-origin keeps ref null
        String man = z.get("snapshots/snapshot-1.json");
        assertTrue(man.startsWith("{\"v\":2,\"source\":\"cdp\",\"url\":\"https://app/\""), man);
        assertTrue(man.contains("\"viewport\":{\"w\":800,\"h\":600}"), man);
        assertTrue(man.matches(".*\"href\":\"https://app/s.css\",\"media\":\"\",\"index\":-1,\"ref\":\"resources/[0-9a-f]{40}\".*"), man);
        assertTrue(man.contains("\"href\":\"https://cdn/x.css\",\"media\":null,\"index\":-1,\"ref\":null"), man);
        assertEquals(1, z.keySet().stream().filter(k -> k.startsWith("resources/")).count(), z.keySet().toString());
        assertEquals(".a{color:red}", z.get(z.keySet().stream().filter(k -> k.startsWith("resources/")).findFirst().get()));
        // events: actions carry their id; metadata says format 2
        List<String> ev = lines(z.get("events.ndjson"));
        assertTrue(ev.stream().anyMatch(l -> l.contains("\"type\":\"action\",\"name\":\"click\",\"detail\":\"id@go\",\"a\":1")), ev.toString());
        assertTrue(ev.stream().anyMatch(l -> l.contains("\"type\":\"lifecycle\",\"name\":\"failure\"")), ev.toString());
        String meta = z.get("metadata.json");
        assertTrue(meta.contains("\"format\":2") && meta.contains("\"domSnapshots\":\"full\"") && meta.contains("\"domSnapshotsRequested\":\"full\"")
                && meta.contains("\"domSnapshotsLimit\":null"), meta);
        assertTrue(meta.contains("DOM_SNAPSHOT_RICH"), meta);
    }

    @Test
    void theLastActionGetsItsAfterAtStopWhileTheDriverIsAlive(@TempDir Path dir) throws Exception {
        Fake b = richBrowser();
        TraceRecorder r = TraceRecorder.attach(b, new TraceConfig().setEnableTrace(true).setDomSnapshots("full"), dir.resolve("wd"), "a.sel", "chrome");
        r.action("click", "x", "el");
        r.stop();
        assertEquals(2, b.calls.stream().filter(c -> c.startsWith("rich")).count(), b.calls.toString());
        r.stop();
        r.action("click", "late", "el");                                  // after stop: event only, no capture
        assertEquals(2, b.calls.stream().filter(c -> c.startsWith("rich")).count());
        Map<String, String> z = zip(r.writeZip(dir.resolve("t.zip")));
        List<String> idx = lines(z.get("snapshots.ndjson"));
        assertTrue(idx.get(1).contains("\"phase\":\"after\"") && idx.get(1).contains("(end)"), idx.toString());

        Fake d = richBrowser();
        TraceRecorder r3 = TraceRecorder.attach(d, new TraceConfig().setEnableTrace(true).setDomSnapshots("full"), dir.resolve("wd3"), "a.sel", "chrome");
        r3.action("click", "x", "el");
        r3.discard();                                                     // retain-on-failure, passing script
        assertEquals(1, d.calls.stream().filter(c -> c.startsWith("rich")).count(), "no wasted final capture: " + d.calls);
    }

    @Test
    void offIsTheDefaultAndLiteNeedsNoRichCapability(@TempDir Path dir) throws Exception {
        Fake b = richBrowser();
        TraceRecorder r = TraceRecorder.attach(b, new TraceConfig().setEnableTrace(true), dir.resolve("wd"), "a.sel", "chrome");
        r.action("click", "x", "el");
        r.stop();
        assertTrue(b.calls.isEmpty(), "default off must not capture: " + b.calls);
        assertEquals("off", new TraceConfig().getDomSnapshots());
        assertEquals("off", new TraceConfig().setDomSnapshots("bogus").getDomSnapshots());
        assertEquals("full", new TraceConfig().setDomSnapshots("full").setDomSnapshots(" ").getDomSnapshots());   // blank keeps

        Fake lite = new Fake(EnumSet.of(Capability.DOM_SNAPSHOT));        // e.g. no script-capable rich path
        TraceRecorder r2 = TraceRecorder.attach(lite, new TraceConfig().setEnableTrace(true).setDomSnapshots("full"), dir.resolve("wd2"), "a.sel", "chrome");
        r2.action("click", "x", "el");
        Map<String, String> z = zip(r2.writeZip(dir.resolve("t2.zip")));
        assertEquals(List.of("lite", "lite"), lite.calls);               // Δ146a: the sanitizer, never "raw"; no shot (no SCREENSHOT)
        for (String l : lines(z.get("snapshots.ndjson"))) {
            assertTrue(l.contains("\"v\":1") && l.contains("\"dom\":null") && l.contains("\"target\":{\"rect\":[5,6,7,8]}"), l);
        }
        assertTrue(z.get("snapshots/snapshot-1.html").contains("lite 1 [1]"), "lite marks the target");
    }

    @Test
    void slowCapturesDegradeFullToLiteToOff(@TempDir Path dir) throws Exception {
        Fake b = richBrowser();
        b.sleepMs = 15;
        TraceRecorder r = TraceRecorder.attach(b, new TraceConfig().setEnableTrace(true).setDomSnapshots("full").setSnapshotSlowMs(5),
                dir.resolve("wd"), "a.sel", "chrome");
        for (int i = 0; i < 3; i++) r.action("click", "x" + i, "el");
        assertEquals("lite", r.domSnapshotMode());
        long rich = b.calls.stream().filter(c -> c.startsWith("rich")).count();
        for (int i = 0; i < 3; i++) r.action("click", "y" + i, "el");    // lite: fast here, stays lite
        assertEquals(rich, b.calls.stream().filter(c -> c.startsWith("rich")).count(), "no rich capture once degraded");
        assertEquals("lite", r.domSnapshotMode());
        String meta = zip(r.writeZip(dir.resolve("t.zip"))).get("metadata.json");
        assertTrue(meta.contains("\"domSnapshotsLimit\":\"slow\""), meta);
        assertTrue(meta.contains("\"domSnapshots\":\"lite\"") && meta.contains("\"domSnapshotsRequested\":\"full\""), "effective mode: " + meta);
        assertFalse(b.calls.contains("raw"), "degraded lite is the sanitizer, not outerHTML");
    }

    @Test
    void budgetAndCountCapsStopCapture(@TempDir Path dir) throws Exception {
        Fake b = richBrowser();
        StringBuilder css = new StringBuilder();
        for (int i = 0; i < 3000; i++) css.append(".c").append(i).append("{color:red}");
        b.bigCss = css.toString();                                        // ~45 KB
        TraceRecorder r = TraceRecorder.attach(b, new TraceConfig().setEnableTrace(true).setDomSnapshots("full").setSnapshotMaxTotalBytes(40_000),
                dir.resolve("wd"), "a.sel", "chrome");
        for (int i = 0; i < 5; i++) r.action("click", "x" + i, "el");
        assertEquals("off", r.domSnapshotMode());
        assertEquals(3, b.calls.stream().filter(x -> x.startsWith("rich")).count(), "15 KB pages: the third crosses 40 KB, then none");
        Map<String, String> z = zip(r.writeZip(dir.resolve("t.zip")));
        assertTrue(z.get("metadata.json").contains("\"domSnapshotsLimit\":\"budget\""), z.get("metadata.json"));
        assertFalse(z.keySet().stream().anyMatch(k -> k.startsWith("resources/") && z.get(k).length() > 40_000), "over-cap CSS dropped");

        Fake c = richBrowser();
        TraceRecorder r2 = TraceRecorder.attach(c, new TraceConfig().setEnableTrace(true).setDomSnapshots("lite").setSnapshotMaxCount(2),
                dir.resolve("wd2"), "a.sel", "chrome");
        for (int i = 0; i < 5; i++) r2.action("click", "x" + i, "el");
        assertEquals(2, c.calls.stream().filter(x -> x.equals("lite")).count(), c.calls.toString());
        assertTrue(zip(r2.writeZip(dir.resolve("t2.zip"))).get("metadata.json").contains("\"domSnapshotsLimit\":\"count\""));
    }

    @Test
    void failureAndEndScreenshotsCountAgainstTheBudget(@TempDir Path dir) throws Exception {       // Δ146a item 2
        Fake b = richBrowser();
        b.shotBytes = 30_000;
        TraceRecorder r = TraceRecorder.attach(b, new TraceConfig().setEnableTrace(true).setDomSnapshots("full").setSnapshotMaxTotalBytes(40_000),
                dir.resolve("wd"), "a.sel", "chrome");
        for (int i = 0; i < 4; i++) r.action("click", "x" + i, "el");
        assertEquals(0, b.calls.stream().filter(c -> c.equals("shot")).count(), "per-action captures take no screenshot");
        r.failure("boom");                                                  // after(4) + a 30 KB screenshot
        r.action("click", "y", "el");                                       // 30 KB + ~3 KB < 40 KB: still on
        r.failure("again");
        assertEquals("off", r.domSnapshotMode(), "two 30 KB screenshots cross the 40 KB budget");
        Map<String, String> z = zip(r.writeZip(dir.resolve("t.zip")));
        assertTrue(z.get("metadata.json").contains("\"domSnapshotsLimit\":\"budget\""), z.get("metadata.json"));
        assertTrue(z.get("metadata.json").matches("(?s).*\"domSnapshotBytes\":6\\d{4}.*"), z.get("metadata.json"));
    }

    @Test
    void failureIsRecordedOncePerActionAndNeverAfterStop(@TempDir Path dir) throws Exception {   // Δ146a item 8
        Fake b = richBrowser();
        TraceRecorder r = TraceRecorder.attach(b, new TraceConfig().setEnableTrace(true).setDomSnapshots("full"), dir.resolve("wd"), "a.sel", "chrome");
        r.action("click", "x", "el");
        r.failure("subtest");                                               // nested subtest failure ...
        r.failure("subtest re-reported by the parent");                     // ... reported again for the same action
        long captures = b.calls.stream().filter(c -> c.startsWith("rich")).count();
        r.stop();
        r.failure("after stop");
        assertEquals(captures, b.calls.stream().filter(c -> c.startsWith("rich")).count(), "no capture after stop");
        Map<String, String> z = zip(r.writeZip(dir.resolve("t.zip")));
        assertEquals(1, lines(z.get("events.ndjson")).stream().filter(l -> l.contains("\"name\":\"failure\"")).count(), z.get("events.ndjson"));
    }

    @Test
    void effectiveModeIsLiteWhenNoCaptureCameOutStyled(@TempDir Path dir) throws Exception {      // Δ147b item 5
        Fake b = new Fake(EnumSet.of(Capability.DOM_SNAPSHOT, Capability.DOM_SNAPSHOT_RICH)) {
            @Override public RichSnapshot captureRichSnapshot(List<Object> t, List<String> m) { calls.add("rich-null"); return null; }
        };
        TraceRecorder r = TraceRecorder.attach(b, new TraceConfig().setEnableTrace(true).setDomSnapshots("full"), dir.resolve("wd"), "a.sel", "chrome");
        r.action("click", "x", "el");
        r.action("click", "y", "el");
        assertEquals("full", r.domSnapshotMode(), "still asking for full");
        assertEquals("lite", r.effectiveDomSnapshotMode());
        String meta = zip(r.writeZip(dir.resolve("t.zip"))).get("metadata.json");
        assertTrue(meta.contains("\"domSnapshots\":\"lite\"") && meta.contains("\"domSnapshotsRequested\":\"full\""), meta);
        assertTrue(b.calls.contains("lite") && !b.calls.contains("raw"), b.calls.toString());

        Fake ok = richBrowser();                                            // one styled capture: full
        TraceRecorder r2 = TraceRecorder.attach(ok, new TraceConfig().setEnableTrace(true).setDomSnapshots("full"), dir.resolve("wd2"), "a.sel", "chrome");
        r2.action("click", "x", "el");
        assertEquals("full", r2.effectiveDomSnapshotMode());
        r2.discard();
    }

    @Test
    void aThrowingTransportNeverBreaksTheRun(@TempDir Path dir) throws Exception {
        Fake b = new Fake(EnumSet.of(Capability.DOM_SNAPSHOT, Capability.DOM_SNAPSHOT_RICH, Capability.SCREENSHOT)) {
            @Override public RichSnapshot captureRichSnapshot(List<Object> t, List<String> m) { throw new IllegalStateException("x"); }
            @Override public String captureDomSnapshot() { throw new IllegalStateException("y"); }
            @Override public RichSnapshot captureLiteSnapshot(List<Object> t, List<String> m) { throw new IllegalStateException("w"); }
            @Override public byte[] captureScreenshot() { throw new IllegalStateException("z"); }
        };
        TraceRecorder r = TraceRecorder.attach(b, new TraceConfig().setEnableTrace(true).setDomSnapshots("full"), dir.resolve("wd"), "a.sel", "chrome");
        r.action("click", "x", "el");
        r.failure(null);
        Map<String, String> z = zip(r.writeZip(dir.resolve("t.zip")));
        assertTrue(lines(z.get("snapshots.ndjson")).isEmpty());
        assertTrue(z.containsKey("metadata.json"));
    }
}
