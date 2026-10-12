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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Captures "lite" DOM snapshots (document outerHTML) plus a page screenshot on demand,
 * writing each to disk immediately and indexing them in {@code snapshots.ndjson}.
 *
 * <p>H2 (Δ146), {@link #capture}: one physical capture (styled {@link RichSnapshot} or lite outerHTML, plus a
 * screenshot) is written once and indexed by several points, e.g. "after action k" and "before action k+1". A
 * format-2 index line keeps every v1 field ({@code n, ts, label, html, screenshot}, so an older viewer still shows
 * the HTML) and adds {@code v, dom, action, phase, target}. {@code dom} is {@code snapshots/snapshot-<c>.json}:
 * <pre>{"v":2,"source":"cdp|script","url","title","viewport":{"w","h"},"scroll":{"x","y"},"truncated",
 *  "styles":[{"owner":"link|style|adopted","href","media","index","ref":"resources/&lt;sha1&gt;"|null}]}</pre>
 * Stylesheet text goes to {@code resources/<sha1>} (deduped); identical HTML is written once and shared.
 * Bytes written by captures are counted against a budget; past it the caller stops capturing.
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
    private final AtomicInteger captures = new AtomicInteger(0);
    private final AtomicLong bytes = new AtomicLong(0);
    private final Map<String, String> htmlBySha = new HashMap<>();
    private ResourceStore css;
    private long maxBytes = Long.MAX_VALUE;

    /** One index line a capture serves: the action id, "before" | "after", the label, and which target it marks. */
    public static final class Point {
        final int action; final String phase; final String label; final int target;
        public Point(int action, String phase, String label, int target) {
            this.action = action; this.phase = phase; this.label = label; this.target = target;
        }
    }

    public SnapshotRecorder(BrowserControl browser, Path traceDir, TraceConfig cfg) throws java.io.IOException {
        this(browser, traceDir);
        long cap = cfg == null ? 32L * 1024 * 1024 : cfg.getSnapshotMaxTotalBytes();
        this.maxBytes = cap;
        this.css = new ResourceStore(traceDir.resolve("resources"), cap, cap);
    }

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

    /** Bytes the per-action captures wrote (HTML + manifests + stylesheet text). */
    public long capturedBytes() { return bytes.get() + (css == null ? 0 : css.usedBytes()); }
    public boolean overBudget() { return capturedBytes() >= maxBytes; }
    public int captureCount() { return captures.get(); }

    /**
     * H2: one capture indexed by every point. {@code rich} asks for a styled snapshot (DOM_SNAPSHOT_RICH); when the
     * transport cannot, or returns nothing, it falls back to lite outerHTML. Returns "full" | "lite" | null (nothing
     * captured). Best-effort: never throws.
     */
    public String capture(List<Point> points, List<Object> targets, boolean rich) {
        if (points == null || points.isEmpty()) return null;
        int c = captures.incrementAndGet();
        long ts = System.currentTimeMillis();
        String htmlRef = null, domRef = null, shotRef = null, kind = null;
        RichSnapshot rs = null;
        try {
            if (rich && browser.supports(Capability.DOM_SNAPSHOT_RICH)) {
                List<String> marks = new ArrayList<>();
                for (int i = 0; i < (targets == null ? 0 : targets.size()); i++) marks.add(null);
                for (Point p : points) if (p.target >= 0 && p.target < marks.size()) marks.set(p.target, String.valueOf(p.action));
                rs = browser.captureRichSnapshot(targets == null ? new ArrayList<>() : targets, marks);
            }
        } catch (Exception ignored) { rs = null; }
        try {
            String html = rs != null ? rs.html : null;
            if (html == null && browser.supports(Capability.DOM_SNAPSHOT)) { html = browser.captureDomSnapshot(); rs = null; }
            if (html != null) {
                htmlRef = writeHtml(c, html);
                kind = rs != null ? "full" : "lite";
            }
            if (rs != null) {
                String man = manifest(rs);
                Path f = snapDir.resolve("snapshot-" + c + ".json");
                byte[] mb = man.getBytes(StandardCharsets.UTF_8);
                Files.write(f, mb);
                bytes.addAndGet(mb.length);
                domRef = "snapshots/snapshot-" + c + ".json";
            }
        } catch (Exception ignored) {}
        try {
            if (browser.supports(Capability.SCREENSHOT)) {
                byte[] png = browser.captureScreenshot();
                if (png != null && png.length > 0) {
                    Files.write(shotDir.resolve("shot-" + c + ".png"), png);
                    shotRef = "screenshots/shot-" + c + ".png";
                }
            }
        } catch (Exception ignored) {}
        if (htmlRef == null && shotRef == null) return null;
        for (Point p : points) {
            Json.Obj o = new Json.Obj()
                    .put("n", seq.incrementAndGet())
                    .put("ts", ts)
                    .put("label", p.label)
                    .put("html", htmlRef)
                    .put("screenshot", shotRef)
                    .put("v", rs != null ? 2 : 1)
                    .put("dom", domRef)
                    .put("action", p.action)
                    .put("phase", p.phase)
                    .put("capture", c);
            double[] r = rs != null ? rs.rect(p.target) : null;
            o.putRaw("target", r == null ? null : "{\"rect\":[" + num(r[0]) + "," + num(r[1]) + "," + num(r[2]) + "," + num(r[3]) + "]}");
            index.writeLine(o.end());
        }
        return kind;
    }

    private String writeHtml(int c, String html) throws java.io.IOException {
        byte[] b = html.getBytes(StandardCharsets.UTF_8);
        String sha = sha1(b);
        synchronized (htmlBySha) {
            String prev = sha == null ? null : htmlBySha.get(sha);
            if (prev != null) return prev;
            Files.write(snapDir.resolve("snapshot-" + c + ".html"), b);
            bytes.addAndGet(b.length);
            String ref = "snapshots/snapshot-" + c + ".html";
            if (sha != null) htmlBySha.put(sha, ref);
            return ref;
        }
    }

    private String manifest(RichSnapshot rs) {
        StringBuilder st = new StringBuilder("[");
        for (RichSnapshot.Style s : rs.styles) {
            String ref = null;
            if (s.text != null && css != null) ref = css.store(s.text.getBytes(StandardCharsets.UTF_8));
            if (st.length() > 1) st.append(',');
            st.append(new Json.Obj().put("owner", s.owner).put("href", s.href).put("media", s.media)
                    .put("index", s.index).put("ref", ref).end());
        }
        st.append(']');
        return new Json.Obj()
                .put("v", 2)
                .put("source", rs.source)
                .put("url", rs.url)
                .put("title", rs.title)
                .putRaw("viewport", "{\"w\":" + rs.viewportW + ",\"h\":" + rs.viewportH + "}")
                .putRaw("scroll", "{\"x\":" + rs.scrollX + ",\"y\":" + rs.scrollY + "}")
                .put("truncated", rs.truncated)
                .putRaw("styles", st.toString())
                .end();
    }

    private static String num(double d) {
        if (Double.isNaN(d) || Double.isInfinite(d)) return "0";
        return d == Math.rint(d) ? Long.toString((long) d) : Double.toString(Math.round(d * 100) / 100.0);
    }

    private static String sha1(byte[] b) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-1").digest(b);
            StringBuilder sb = new StringBuilder(40);
            for (byte x : d) sb.append(Character.forDigit((x >> 4) & 0xf, 16)).append(Character.forDigit(x & 0xf, 16));
            return sb.toString();
        } catch (Exception e) { return null; }
    }

    public void flush() { index.flush(); }
    public void close() { index.close(); }
}
