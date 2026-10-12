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

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Orchestrates the per-driver-session recorders and produces the {@code trace.zip}. Depends
 * only on {@link BrowserControl} — no CDP/BiDi types — so it compiles and unit-tests with a
 * plain javac and no classpath.
 *
 * Lifecycle: {@link #attach} starts capture in initBrowser; {@link #stop} freezes it in
 * quit() (DevTools still alive); {@link #writeZip} zips the streamed working dir at report
 * time (constant memory) and cleans up. See the roadmap §3 for the seam rationale.
 *
 * @author Sumeet Chhetri
 */
public final class TraceRecorder implements AutoCloseable {

    private final BrowserControl browser;
    private final TraceConfig cfg;
    private final Path workDir;
    private final String selFile;
    private final String browserName;
    private final long startTs;
    private final AtomicBoolean stopped = new AtomicBoolean(false);

    private EventTimelineRecorder timeline;
    private NetworkRecorder net;
    private ConsoleRecorder console;
    private SnapshotRecorder snap;
    private MetricsRecorder metrics;
    private CpuRecorder cpu;
    private CoverageRecorder coverage;
    private VideoRecorder video;

    // H2 (Δ146): per-action DOM snapshots. "after k" is taken at the start of action k+1 (the same capture serves
    // "before k+1"), on a failure, and at stop. Guarded by `this`: actions arrive on the test thread, stop may not.
    static final java.util.Set<String> NO_SNAPSHOT = new java.util.HashSet<>(java.util.Arrays.asList(
            "autowait", "softfail", "hookfail", "state", "emulate", "clock", "route", "a11y", "screenshot-compare"));
    private String domMode = "off";
    private String domLimit;          // why capture stopped or degraded: slow | budget | count
    private int actionSeq;
    private int pendingId = -1;
    private String pendingName;
    private Object pendingTarget;
    private long snapNanos;
    private int snapTimed;
    private volatile boolean discarding;   // a discarded trace needs no final after-capture

    private TraceRecorder(BrowserControl browser, TraceConfig cfg, Path workDir, String selFile, String browserName) {
        this.browser = browser;
        this.cfg = cfg;
        this.workDir = workDir;
        this.selFile = selFile;
        this.browserName = browserName;
        this.startTs = System.currentTimeMillis();
    }

    /** Build the recorders and start capture. Never throws upward — tracing must not break a run. */
    public static TraceRecorder attach(BrowserControl browser, TraceConfig cfg, Path workDir,
                                       String selFile, String browserName) {
        TraceRecorder r = new TraceRecorder(browser, cfg, workDir, selFile, browserName);
        try {
            Files.createDirectories(workDir);
            r.timeline = new EventTimelineRecorder(workDir);
            if (cfg.isNetwork() && browser.supports(Capability.NETWORK)) {
                r.net = new NetworkRecorder(browser, workDir, cfg);
                browser.enableNetwork(r.net);
            }
            if (cfg.isConsole() && browser.supports(Capability.CONSOLE)) {
                r.console = new ConsoleRecorder(workDir);
                browser.enableConsole(r.console);
            }
            if (cfg.isSnapshots() && (browser.supports(Capability.DOM_SNAPSHOT) || browser.supports(Capability.SCREENSHOT))) {
                r.snap = new SnapshotRecorder(browser, workDir, cfg);
                r.domMode = cfg.getDomSnapshots();
            }
            if (cfg.isMetrics() && browser.supports(Capability.METRICS)) {
                r.metrics = new MetricsRecorder(browser, workDir, cfg);
                r.metrics.start();
            }
            if (cfg.isCpu() && browser.supports(Capability.CPU_PROFILE)) {
                r.cpu = new CpuRecorder(browser, workDir);
                r.cpu.start();
            }
            if (cfg.isCoverage() && browser.supports(Capability.COVERAGE)) {
                r.coverage = new CoverageRecorder(browser, workDir);
                r.coverage.start();
            }
            if (cfg.isVideo() && browser.supports(Capability.SCREENCAST)) {
                r.video = new VideoRecorder(workDir);
                r.video.setLiveKey(selFile);   // M15: the console's live screencast
                browser.enableVideo(r.video);
            }
            r.timeline.event("lifecycle", "trace-start", selFile);
        } catch (Exception e) {
            // leave partially-initialised recorders; stop()/writeZip() remain safe
        }
        return r;
    }

    /** Q9/Q10: the streamed working directory (valid until {@link #writeZip} / {@link #discard}). */
    public Path workDir() {
        return workDir;
    }

    public void event(String type, String name, String detail) {
        if (timeline != null) timeline.event(type, name, detail);
    }

    public void snapshot(String label) {
        if (snap != null) snap.snapshot(label);
    }

    /** H2: the effective per-action snapshot mode (off | lite | full) — degrades when captures are slow or over budget. */
    public synchronized String domSnapshotMode() { return snap == null ? "off" : domMode; }

    /**
     * H2 (Δ146): a per-action timeline event (with its action id {@code a}) and, when per-action snapshots are on,
     * one capture that is both the previous action's "after" and this action's "before". {@code target} is the
     * action's element (an opaque WebElement) or null. Never throws.
     */
    public synchronized int action(String name, String detail, Object target) {
        int id = ++actionSeq;
        try { if (timeline != null) timeline.action(id, name, detail); } catch (Exception ignored) {}
        if (snap == null || "off".equals(domMode) || stopped.get() || (name != null && NO_SNAPSHOT.contains(name))) return id;
        java.util.List<SnapshotRecorder.Point> pts = new java.util.ArrayList<>();
        java.util.List<Object> targets = new java.util.ArrayList<>();
        if (pendingId > 0) {
            pts.add(new SnapshotRecorder.Point(pendingId, "after", pendingName, targets.size()));
            targets.add(pendingTarget);
        }
        pts.add(new SnapshotRecorder.Point(id, "before", name, targets.size()));
        targets.add(target);
        timedCapture(pts, targets);
        pendingId = id; pendingName = name; pendingTarget = target;
        return id;
    }

    /** H2: the failing step's after-state, captured now (on the failure path, before the failure screenshot). */
    public synchronized void failure(String detail) {
        try { if (timeline != null) timeline.event("lifecycle", "failure", detail); } catch (Exception ignored) {}
        captureAfter("failure");
    }

    private void captureAfter(String why) {
        if (snap == null || "off".equals(domMode) || pendingId <= 0) return;
        java.util.List<Object> targets = new java.util.ArrayList<>();
        targets.add(pendingTarget);
        java.util.List<SnapshotRecorder.Point> pts = new java.util.ArrayList<>();
        pts.add(new SnapshotRecorder.Point(pendingId, "after", pendingName + " (" + why + ")", 0));
        timedCapture(pts, targets);
        pendingId = -1; pendingName = null; pendingTarget = null;
    }

    private void timedCapture(java.util.List<SnapshotRecorder.Point> pts, java.util.List<Object> targets) {
        long t0 = System.nanoTime();
        try { snap.capture(pts, targets, "full".equals(domMode)); } catch (Exception ignored) {}
        snapNanos += System.nanoTime() - t0;
        snapTimed++;
        // never noticeably slow a run, never balloon the bundle: full -> lite -> off
        if (snap.overBudget()) { domMode = "off"; domLimit = "budget"; return; }
        if (snap.captureCount() >= cfg.getSnapshotMaxCount()) { domMode = "off"; domLimit = "count"; return; }
        if (snapTimed >= 3 && snapNanos / snapTimed > cfg.getSnapshotSlowMs() * 1_000_000L) {
            domMode = "full".equals(domMode) ? "lite" : "off";
            domLimit = "slow";
            snapNanos = 0; snapTimed = 0;
        }
    }

    /** Freeze capture: detach listeners (DevTools still alive), flush, finalize HAR + metadata. */
    public void stop() {
        synchronized (this) {   // H2: the last action's after-state, while the driver is still alive
            if (stopped.get()) return;
            if (!discarding) try { captureAfter("end"); } catch (Exception ignored) {}
        }
        if (!stopped.compareAndSet(false, true)) return;
        try { if (timeline != null) timeline.event("lifecycle", "trace-stop", null); } catch (Exception ignored) {}
        try { browser.disableNetwork(); } catch (Exception ignored) {}
        try { browser.disableConsole(); } catch (Exception ignored) {}
        try { browser.disableVideo(); } catch (Exception ignored) {}
        if (metrics != null) metrics.stop();   // stops sampler, reads vitals, writes performance/memory json (driver still alive)
        if (cpu != null) cpu.stop();            // retrieves + writes cpu.cpuprofile
        if (coverage != null) coverage.stop();  // retrieves + writes coverage.json
        if (video != null) video.stop();        // finalizes frames + muxes video.mp4 (best-effort)
        if (cfg.isPdf() && browser.supports(Capability.PRINT_PDF)) {   // G10: opt-in final-page PDF
            try {
                byte[] pdf = browser.printPdf();
                if (pdf != null && pdf.length > 0) Files.write(workDir.resolve("page.pdf"), pdf);
            } catch (Exception ignored) {}
        }
        if (net != null) net.flush();
        if (console != null) console.flush();
        if (snap != null) snap.flush();
        if (timeline != null) timeline.flush();
        try { if (net != null) net.buildHar(workDir.resolve("network.har"), browserName); } catch (Exception ignored) {}
        if (net != null) net.close();
        if (console != null) console.close();
        if (snap != null) snap.close();
        if (timeline != null) timeline.close();
        try { writeMetadata(); } catch (Exception ignored) {}
        try { browser.close(); } catch (Exception ignored) {}
    }

    /** Q3: stop capture and delete the working dir without producing a bundle (retain-on-failure on a passing
     *  script, or a session whose result was never reported). Safe to call more than once. */
    public void discard() {
        discarding = true;
        try { stop(); } finally { deleteTree(workDir); }
    }

    /** Zip the working dir to {@code target} (streamed) and delete the working dir. */
    public Path writeZip(Path target) {
        stop();
        try {
            Files.createDirectories(target.getParent());
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(target))) {
                try (Stream<Path> walk = Files.walk(workDir)) {
                    walk.filter(Files::isRegularFile).forEach(p -> addEntry(zip, p));
                }
            }
        } catch (Exception e) {
            return null;
        } finally {
            deleteTree(workDir);
        }
        return target;
    }

    private void addEntry(ZipOutputStream zip, Path file) {
        String rel = workDir.relativize(file).toString().replace('\\', '/');
        try {
            zip.putNextEntry(new ZipEntry(rel));
            Files.copy(file, (OutputStream) zip);
            zip.closeEntry();
        } catch (IOException e) {
            // skip a single unreadable file rather than fail the whole bundle
        }
    }

    private void writeMetadata() throws IOException {
        String meta = new Json.Obj()
                .put("runId", workDir.getFileName().toString())
                .put("selFile", selFile)
                .put("browser", browserName)
                .put("protocol", cfg.getProtocol())
                .put("format", 2)   // H2 (Δ146): per-action events carry `a`; snapshots.ndjson lines may be v2 (dom manifest)
                .put("domSnapshots", snap == null ? "off" : cfg.getDomSnapshots())
                .put("domSnapshotsLimit", domLimit)
                .put("domSnapshotBytes", snap == null ? 0 : snap.capturedBytes())
                .put("startTs", startTs)
                .put("endTs", System.currentTimeMillis())
                .put("recNetwork", net != null)
                .put("recConsole", console != null)
                .put("recSnapshots", snap != null)
                .put("recMetrics", metrics != null)
                .put("recCpu", cpu != null)
                .put("recCoverage", coverage != null)
                .put("recVideo", video != null)
                .put("transport", browser.getClass().getSimpleName())
                .put("capabilities", capabilityList())
                .put("truncatedBodies", net != null ? net.truncatedBodies() : 0)
                .put("bytesCaptured", net != null ? net.bytesCaptured() : 0)
                .end();
        Files.write(workDir.resolve("metadata.json"), meta.getBytes(StandardCharsets.UTF_8));
    }

    // G10: which capabilities the active transport served for this trace (honest parity record)
    private String capabilityList() {
        StringBuilder b = new StringBuilder();
        for (Capability c : Capability.values()) {
            boolean ok;
            try { ok = browser.supports(c); } catch (Exception e) { ok = false; }
            if (ok) b.append(b.length() == 0 ? "" : ",").append(c.name());
        }
        return b.toString();
    }

    private static void deleteTree(Path root) {
        if (root == null || !Files.exists(root)) return;
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignored) {}
            });
        } catch (IOException ignored) {}
    }

    @Override
    public void close() { stop(); }
}
