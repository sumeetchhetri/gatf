package com.gatf.selenium.trace;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Captures "lite" DOM snapshots (document outerHTML) plus a page screenshot on demand,
 * writing each to disk immediately and indexing them in {@code snapshots.ndjson}. True DOM
 * time-travel (resource capture + rebuild) is a Phase-3 trace-viewer concern.
 *
 * @author Sumeet Chhetri
 */
public final class SnapshotRecorder {
    private final BrowserControl browser;
    private final Path traceDir;
    private final Path snapDir;
    private final Path shotDir;
    private final NdjsonWriter index;
    private final AtomicInteger seq = new AtomicInteger(0);

    public SnapshotRecorder(BrowserControl browser, Path traceDir) throws java.io.IOException {
        this.browser = browser;
        this.traceDir = traceDir;
        this.snapDir = traceDir.resolve("snapshots");
        this.shotDir = traceDir.resolve("screenshots");
        Files.createDirectories(snapDir);
        Files.createDirectories(shotDir);
        this.index = new NdjsonWriter(traceDir.resolve("snapshots.ndjson"));
    }

    /** Take a snapshot + screenshot; best-effort, never throws upward. */
    public void snapshot(String label) {
        int n = seq.incrementAndGet();
        String htmlRef = null, shotRef = null;
        try {
            if (browser.supports(Capability.DOM_SNAPSHOT)) {
                String html = browser.captureDomSnapshot();
                if (html != null) {
                    Path f = snapDir.resolve("snapshot-" + n + ".html");
                    Files.write(f, html.getBytes(StandardCharsets.UTF_8));
                    htmlRef = "snapshots/snapshot-" + n + ".html";
                }
            }
        } catch (Exception ignored) {}
        try {
            if (browser.supports(Capability.SCREENSHOT)) {
                byte[] png = browser.captureScreenshot();
                if (png != null && png.length > 0) {
                    Path f = shotDir.resolve("shot-" + n + ".png");
                    Files.write(f, png);
                    shotRef = "screenshots/shot-" + n + ".png";
                }
            }
        } catch (Exception ignored) {}
        index.writeLine(new Json.Obj()
                .put("n", n)
                .put("ts", System.currentTimeMillis())
                .put("label", label)
                .put("html", htmlRef)
                .put("screenshot", shotRef)
                .end());
    }

    public void flush() { index.flush(); }
    public void close() { index.close(); }
}
