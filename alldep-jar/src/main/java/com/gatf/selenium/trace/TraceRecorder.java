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
                r.snap = new SnapshotRecorder(browser, workDir);
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
                browser.enableVideo(r.video);
            }
            r.timeline.event("lifecycle", "trace-start", selFile);
        } catch (Exception e) {
            // leave partially-initialised recorders; stop()/writeZip() remain safe
        }
        return r;
    }

    public void event(String type, String name, String detail) {
        if (timeline != null) timeline.event(type, name, detail);
    }

    public void snapshot(String label) {
        if (snap != null) snap.snapshot(label);
    }

    /** Freeze capture: detach listeners (DevTools still alive), flush, finalize HAR + metadata. */
    public void stop() {
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
