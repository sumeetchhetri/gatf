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

import java.util.Map;

/**
 * G10 — {@code protocol=hybrid}: BiDi serves every capability it can (network, console, bodies, DOM,
 * screenshot, metrics, vitals, PDF); CDP is used ONLY for the Chromium-DevTools-only domains that have
 * no BiDi equivalent — CPU profile, coverage — and for screencast (CDP's out-of-band frame push beats
 * BiDi's polled approximation). Built only when the driver has DevTools (Chromium); elsewhere
 * {@link Transports} returns plain BiDi and those capabilities are simply unsupported.
 *
 * This is the "CDP switched off except where BiDi cannot follow" state D-2 aims for: recorders are
 * unchanged, and CDP's surface shrinks to three domains.
 */
final class HybridTransport implements BrowserControl {

    private final BrowserControl bidi;
    private final BrowserControl cdp;

    HybridTransport(BrowserControl bidi, BrowserControl cdp) {
        this.bidi = bidi;
        this.cdp = cdp;
    }

    /** Which side serves a capability. */
    BrowserControl route(Capability c) {
        switch (c) {
            case CPU_PROFILE:
            case COVERAGE:
                return cdp;
            case SCREENCAST:
                return cdp.supports(c) ? cdp : bidi;
            case ROUTE:   // M4: CDP Fetch on Chromium, BiDi interception otherwise
            case DOM_SNAPSHOT_RICH:   // H2: DOMSnapshot (out-of-band, pierces shadow roots) beats the script serializer
                return cdp.supports(c) ? cdp : bidi;
            default:
                return bidi.supports(c) ? bidi : cdp;
        }
    }

    @Override public boolean supports(Capability c) { return route(c).supports(c); }

    @Override public void enableNetwork(NetworkListener l) { bidi.enableNetwork(l); }
    @Override public void disableNetwork() { bidi.disableNetwork(); }
    @Override public void enableConsole(ConsoleListener l) { bidi.enableConsole(l); }
    @Override public void disableConsole() { bidi.disableConsole(); }
    @Override public String captureDomSnapshot() { return route(Capability.DOM_SNAPSHOT).captureDomSnapshot(); }
    @Override public byte[] captureScreenshot() { return route(Capability.SCREENSHOT).captureScreenshot(); }
    @Override public RichSnapshot captureLiteSnapshot(java.util.List<Object> targets, java.util.List<String> marks) {
        return route(Capability.DOM_SNAPSHOT).captureLiteSnapshot(targets, marks);
    }
    @Override public RichSnapshot captureRichSnapshot(java.util.List<Object> targets, java.util.List<String> marks) {
        return route(Capability.DOM_SNAPSHOT_RICH).captureRichSnapshot(targets, marks);
    }
    // bodies must come from the side that saw the request ids (BiDi owns network in hybrid mode)
    @Override public byte[] fetchBody(String requestId) { return bidi.fetchBody(requestId); }

    @Override public void enableMetrics() { route(Capability.METRICS).enableMetrics(); }
    @Override public void disableMetrics() { route(Capability.METRICS).disableMetrics(); }
    @Override public Map<String, Number> sampleMetrics() { return route(Capability.METRICS).sampleMetrics(); }
    @Override public String readWebVitals() { return bidi.readWebVitals(); }
    // Q9: CDP's binding reports pages out-of-band (survives cross-origin navigation); BiDi page buffer otherwise
    private volatile BrowserControl vitalsSide;
    @Override public boolean enablePageVitals(java.util.function.Consumer<String> sink) {
        if (cdp.enablePageVitals(sink)) { vitalsSide = cdp; return true; }
        if (bidi.enablePageVitals(sink)) { vitalsSide = bidi; return true; }
        return false;
    }
    @Override public String drainPageVitals() { BrowserControl v = vitalsSide; return v == null ? null : v.drainPageVitals(); }
    @Override public void disablePageVitals() { BrowserControl v = vitalsSide; if (v != null) v.disablePageVitals(); }

    @Override public boolean enableRoutes(com.gatf.selenium.route.RouteTable t, boolean keepResponseStage) {
        String sc = routeScope;   // H3: re-enabled after `unroute all`: keep the scoped BiDi intercept
        return sc != null ? enableRoutes(t, keepResponseStage, sc) : route(Capability.ROUTE).enableRoutes(t, keepResponseStage);
    }
    @Override public void disableRoutes() {
        if (scopedRoutes) { scopedRoutes = false; bidi.disableRoutes(); return; }
        route(Capability.ROUTE).disableRoutes();
    }
    // H3 (Δ153): only BiDi scopes an intercept to one tab (disableRoutes then goes to BiDi too)
    private volatile boolean scopedRoutes;
    private volatile String routeScope;
    @Override public boolean enableRoutes(com.gatf.selenium.route.RouteTable t, boolean keepResponseStage, String browsingContext) {
        if (browsingContext == null) return enableRoutes(t, keepResponseStage);
        scopedRoutes = bidi.enableRoutes(t, keepResponseStage, browsingContext);
        if (scopedRoutes) routeScope = browsingContext;   // a failed scope must not capture the 2-arg fallback
        return scopedRoutes;
    }
    // contexts: BiDi first (as ContextMatrix), else CDP
    @Override public com.gatf.selenium.context.ContextOps contextOps() {
        com.gatf.selenium.context.ContextOps o = bidi.contextOps();
        return o != null ? o : cdp.contextOps();
    }

    @Override public void startCpu() { cdp.startCpu(); }
    @Override public String stopCpu() { return cdp.stopCpu(); }
    @Override public void startCoverage() { cdp.startCoverage(); }
    @Override public String stopCoverage() { return cdp.stopCoverage(); }

    @Override public void enableVideo(VideoListener l) { route(Capability.SCREENCAST).enableVideo(l); }
    @Override public void disableVideo() { cdp.disableVideo(); bidi.disableVideo(); }

    @Override public byte[] printPdf() { return route(Capability.PRINT_PDF).printPdf(); }

    @Override
    public void close() {
        try { bidi.close(); } catch (Exception ignored) {}
        try { cdp.close(); } catch (Exception ignored) {}
    }
}
