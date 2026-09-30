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
    // --- M4: network mocking (default unsupported) ---
    /**
     * Intercept every request and let {@code routes} decide (fulfill / abort / continue with headers); a request no
     * rule matches continues untouched. {@code keepResponseStage}: GATF's netapix capture also pauses responses on
     * this session, so the interception must keep that stage. Returns false when this transport cannot intercept.
     */
    default boolean enableRoutes(com.gatf.selenium.route.RouteTable routes, boolean keepResponseStage) { return false; }

    /** Stop intercepting (pending requests are released). Default no-op. */
    default void disableRoutes() { }

    // --- G10: print-to-PDF (default unsupported) ---
    /** Current page as PDF bytes, or null (PRINT_PDF). */
    default byte[] printPdf() { return null; }

    @Override
    void close();
}
