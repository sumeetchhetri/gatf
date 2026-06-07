package com.gatf.selenium.trace;

/**
 * Capabilities a {@link BrowserControl} transport may expose. A per-recorder capability
 * matrix (D-2) uses these so a transport can declare what it can serve today; recorders
 * skip a capability a transport reports as unsupported instead of failing.
 *
 * @author Sumeet Chhetri
 */
public enum Capability {
    NETWORK,        // request/response events (HAR)
    CONSOLE,        // console + log entries
    DOM_SNAPSHOT,   // outerHTML capture
    SCREENSHOT,     // page screenshot bytes
    RESPONSE_BODY,  // fetch a response body by request id
    SCREENCAST,     // Phase 2 (video)
    METRICS,        // Phase 2 (performance/memory)
    CPU_PROFILE,    // Profiler.start/stop -> .cpuprofile
    COVERAGE,       // Phase 2
    PRINT_PDF
}
