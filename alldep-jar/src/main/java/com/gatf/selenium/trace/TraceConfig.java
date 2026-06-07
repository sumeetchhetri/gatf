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
    private String protocol = "cdp";            // cdp | bidi
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

    public boolean isEnableTrace() { return enableTrace; }
    public TraceConfig setEnableTrace(boolean v) { this.enableTrace = v; return this; }
    public String getProtocol() { return protocol; }
    public TraceConfig setProtocol(String v) { this.protocol = (v == null ? "cdp" : v.trim().toLowerCase()); return this; }
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
}
