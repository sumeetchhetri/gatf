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

/**
 * Plain carrier for trace settings, derived from GatfExecutorConfig at attach time. Kept in
 * this package (not the config package) so the recorder core has no dependency back onto the
 * executor config types.
 *
 * @author Sumeet Chhetri
 */
public final class TraceConfig {
    private boolean enableTrace = false;
    private String protocol = "cdp";            // cdp | bidi | hybrid (BiDi + CDP only for CPU/coverage/screencast)
    private boolean network = true;
    private boolean console = true;
    private boolean snapshots = true;
    private boolean captureBodies = true;
    private long maxBodyBytes = 512 * 1024;      // per-response body cap
    private long maxTotalBytes = 64L * 1024 * 1024; // total captured-body budget per trace
    private boolean metrics = true;                 // Performance.getMetrics sampling (perf + memory)
    private long metricsIntervalMs = 1000;
    private boolean webVitals = true;               // FCP/LCP/TTFB/DCL/Load from the Performance Timeline
    private boolean cpu = false;                     // Profiler CPU sampling profile (opt-in; has overhead)
    private boolean coverage = false;                // JS + CSS coverage (opt-in; has overhead)
    private boolean video = false;                   // screencast -> mp4 (opt-in; heaviest)
    private int videoQuality = 60;                   // jpeg quality 0-100
    private int videoEveryNthFrame = 2;              // screencast frame decimation
    // G10 knobs (system properties, so no config-schema change): page.pdf at stop; BiDi polled-screenshot video fps
    private boolean pdf = Boolean.getBoolean("gatf.trace.pdf");
    private int videoPollFps = Integer.getInteger("gatf.trace.videoPollFps", 0);
    // H2 (Δ146): per-action DOM snapshots: off (default) | lite (outerHTML) | full (styled: DOMSnapshot / serialized DOM + CSS)
    private String domSnapshots = normDom(System.getProperty("gatf.trace.domSnapshots"));
    private int snapshotMaxNodes = 200_000;
    private int snapshotMaxChars = 8 * 1024 * 1024;      // serialized HTML cap per snapshot
    private int snapshotMaxCssChars = 2 * 1024 * 1024;   // CSSOM text cap per sheet
    private long snapshotMaxTotalBytes = 32L * 1024 * 1024; // stylesheet bytes stored per trace (resources/, sha1-deduped)
    private int snapshotMaxCount = 2000;                 // captures per trace
    private long snapshotSlowMs = 800;                   // mean capture time above which full -> lite -> off

    public boolean isEnableTrace() { return enableTrace; }
    public TraceConfig setEnableTrace(boolean v) { this.enableTrace = v; return this; }
    public String getProtocol() { return protocol; }
    public boolean isPdf() { return pdf; }
    public TraceConfig setPdf(boolean pdf) { this.pdf = pdf; return this; }
    public int getVideoPollFps() { return videoPollFps; }
    public TraceConfig setVideoPollFps(int fps) { this.videoPollFps = Math.max(0, Math.min(10, fps)); return this; }
    public TraceConfig setProtocol(String v) { this.protocol = (v == null ? "cdp" : v.trim().toLowerCase()); return this; }
    public String getDomSnapshots() { return domSnapshots; }
    /** off | lite | full; null/blank keeps the current value (the -Dgatf.trace.domSnapshots default); unknown -> off. */
    public TraceConfig setDomSnapshots(String v) { if (v != null && !v.trim().isEmpty()) this.domSnapshots = normDom(v); return this; }
    static String normDom(String v) {
        String s = v == null ? "" : v.trim().toLowerCase(java.util.Locale.ROOT);
        return s.equals("lite") || s.equals("full") ? s : "off";
    }
    public int getSnapshotMaxNodes() { return snapshotMaxNodes; }
    public TraceConfig setSnapshotMaxNodes(int v) { if (v > 0) this.snapshotMaxNodes = v; return this; }
    public int getSnapshotMaxChars() { return snapshotMaxChars; }
    public TraceConfig setSnapshotMaxChars(int v) { if (v > 0) this.snapshotMaxChars = v; return this; }
    public int getSnapshotMaxCssChars() { return snapshotMaxCssChars; }
    public TraceConfig setSnapshotMaxCssChars(int v) { if (v > 0) this.snapshotMaxCssChars = v; return this; }
    public long getSnapshotMaxTotalBytes() { return snapshotMaxTotalBytes; }
    public TraceConfig setSnapshotMaxTotalBytes(long v) { if (v > 0) this.snapshotMaxTotalBytes = v; return this; }
    public int getSnapshotMaxCount() { return snapshotMaxCount; }
    public TraceConfig setSnapshotMaxCount(int v) { if (v > 0) this.snapshotMaxCount = v; return this; }
    public long getSnapshotSlowMs() { return snapshotSlowMs; }
    public TraceConfig setSnapshotSlowMs(long v) { if (v > 0) this.snapshotSlowMs = v; return this; }
    public boolean isNetwork() { return network; }
    public TraceConfig setNetwork(boolean v) { this.network = v; return this; }
    public boolean isConsole() { return console; }
    public TraceConfig setConsole(boolean v) { this.console = v; return this; }
    public boolean isSnapshots() { return snapshots; }
    public TraceConfig setSnapshots(boolean v) { this.snapshots = v; return this; }
    public boolean isCaptureBodies() { return captureBodies; }
    public TraceConfig setCaptureBodies(boolean v) { this.captureBodies = v; return this; }
    public long getMaxBodyBytes() { return maxBodyBytes; }
    public TraceConfig setMaxBodyBytes(long v) { if (v > 0) this.maxBodyBytes = v; return this; }
    public long getMaxTotalBytes() { return maxTotalBytes; }
    public TraceConfig setMaxTotalBytes(long v) { if (v > 0) this.maxTotalBytes = v; return this; }
    public boolean isMetrics() { return metrics; }
    public TraceConfig setMetrics(boolean v) { this.metrics = v; return this; }
    public long getMetricsIntervalMs() { return metricsIntervalMs; }
    public TraceConfig setMetricsIntervalMs(long v) { if (v > 0) this.metricsIntervalMs = v; return this; }
    public boolean isWebVitals() { return webVitals; }
    public TraceConfig setWebVitals(boolean v) { this.webVitals = v; return this; }
    public boolean isCpu() { return cpu; }
    public TraceConfig setCpu(boolean v) { this.cpu = v; return this; }
    public boolean isCoverage() { return coverage; }
    public TraceConfig setCoverage(boolean v) { this.coverage = v; return this; }
    public boolean isVideo() { return video; }
    public TraceConfig setVideo(boolean v) { this.video = v; return this; }
    public int getVideoQuality() { return videoQuality; }
    public TraceConfig setVideoQuality(int v) { if (v > 0 && v <= 100) this.videoQuality = v; return this; }
    public int getVideoEveryNthFrame() { return videoEveryNthFrame; }
    public TraceConfig setVideoEveryNthFrame(int v) { if (v > 0) this.videoEveryNthFrame = v; return this; }
}
