package com.gatf.selenium.trace;

/**
 * Protocol-neutral browser control surface (decision D-2). Recorders depend ONLY on this
 * interface — never on CDP or BiDi types directly — which is what lets the active transport
 * be switched by config ({@code protocol=cdp|bidi}) instead of a rewrite. Two implementations
 * exist: {@code CdpTransport} (Selenium devtools.vNNN bindings, active today) and
 * {@code BiDiTransport} (skeleton until parity).
 *
 * Implementations are constructed from a WebDriver by {@code Transports.create(..)} in the
 * selenium package glue; this interface stays free of Selenium types so the recorder core
 * compiles and unit-tests with a plain javac and no classpath.
 *
 * @author Sumeet Chhetri
 */
public interface BrowserControl extends AutoCloseable {

    /** Whether this transport can serve the given capability right now. */
    boolean supports(Capability c);

    /** Begin delivering network events to the listener. No-op if NETWORK unsupported. */
    void enableNetwork(NetworkListener listener);
    void disableNetwork();

    /** Begin delivering console/log entries to the listener. No-op if CONSOLE unsupported. */
    void enableConsole(ConsoleListener listener);
    void disableConsole();

    /** Current document outer HTML, or null if unavailable. */
    String captureDomSnapshot();

    /** PNG screenshot bytes, or null if unavailable. */
    byte[] captureScreenshot();

    /** Decoded response body bytes for a request id, or null if not retrievable/too late. */
    byte[] fetchBody(String requestId);

    // --- Phase 2 metrics (default no-ops so only transports that support them override) ---

    /** Begin the metrics domain (CDP Performance.enable). Default no-op. */
    default void enableMetrics() { }

    /** Stop the metrics domain (CDP Performance.disable). Default no-op. */
    default void disableMetrics() { }

    /** Current runtime metrics as name → value (CDP Performance.getMetrics), or empty. */
    default java.util.Map<String, Number> sampleMetrics() { return java.util.Collections.emptyMap(); }

    /** Web-vitals read from the page's Performance Timeline as a JSON object string, or null. */
    default String readWebVitals() { return null; }

    // --- Phase 2 CPU profile + coverage (retrieved at stop; default no-ops) ---

    /** Begin CPU sampling profile (CDP Profiler.start). Default no-op. */
    default void startCpu() { }

    /** Stop and return the CPU profile as a .cpuprofile JSON string, or null. */
    default String stopCpu() { return null; }

    /** Begin JS + CSS coverage tracking. Default no-op. */
    default void startCoverage() { }

    /** Stop and return coverage as a JSON string ({js,css}), or null. */
    default String stopCoverage() { return null; }

    // --- Phase 2 screencast/video (default no-ops) ---

    /** Begin the screencast, delivering decoded JPEG frames to the listener. Default no-op. */
    default void enableVideo(VideoListener listener) { }

    /** Stop the screencast. Default no-op. */
    default void disableVideo() { }

    @Override
    void close();
}
