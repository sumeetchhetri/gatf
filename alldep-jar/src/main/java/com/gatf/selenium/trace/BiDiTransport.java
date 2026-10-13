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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.bidi.module.LogInspector;
import org.openqa.selenium.bidi.module.Network;
import org.openqa.selenium.bidi.network.Header;
import org.openqa.selenium.bidi.network.RequestData;
import org.openqa.selenium.bidi.network.ResponseData;
import org.openqa.selenium.bidi.browsingcontext.BrowsingContext;
import org.openqa.selenium.bidi.browsingcontext.CaptureScreenshotParameters;
import org.openqa.selenium.PrintsPage;
import org.openqa.selenium.print.PrintOptions;

/**
 * WebDriver BiDi implementation of {@link BrowserControl} (decision D-2). Parity is pushed as far as
 * BiDi safely allows:
 *   • NETWORK + CONSOLE — via the Selenium BiDi modules (out-of-band event subscriptions).
 *   • DOM_SNAPSHOT, SCREENSHOT, web-vitals, and METRICS (performance + JS memory) — via the
 *     protocol-agnostic {@link JavascriptExecutor}/{@link TakesScreenshot} paths, identical whether the
 *     active transport is CDP or BiDi (no BiDi API involved). {@code sampleMetrics()} is a read-only
 *     {@code executeScript} that returns a value and changes no driver state; chromedriver serialises
 *     per-session commands, so a low-frequency read from the sampler thread is safe. Note the one real
 *     difference from CDP: CDP reads metrics over its out-of-band DevTools WebSocket, whereas this path
 *     is in-band on the classic command channel, so on very command-heavy suites it adds a little
 *     command-queue pressure. Acceptable for the parity it buys (performance.json + memory.json under BiDi).
 *
 * G10 (delta-080) additions:
 *   • RESPONSE_BODY — raw BiDi {@code network.addDataCollector} + {@code network.getData} (with
 *     {@code disown}, so the browser frees each body once read), sent through {@code BiDi.send} because
 *     Selenium 4.48's Java API does not wrap them. Decided at runtime: if the browser rejects the
 *     collector, RESPONSE_BODY reports unsupported and bodies are simply not captured.
 *   • PRINT_PDF — {@code browsingContext.print}, falling back to the W3C classic print command.
 *   • SCREENCAST — opt-in APPROXIMATION ({@code -Dgatf.trace.videoPollFps=N}, 1–10): polled JPEG
 *     {@code browsingContext.captureScreenshot}s. Unlike CDP's out-of-band frame push this is in-band and
 *     contends with the test, so it is off by default and low-fps; it exists so non-Chromium browsers
 *     (Firefox) can produce a video at all.
 * Still CDP-only (no BiDi command, no page-JS equivalent): CPU_PROFILE, COVERAGE. Use protocol=hybrid
 * to take those from CDP on Chromium while everything else stays on BiDi (see HybridTransport).
 *
 * The recorders are unchanged: they depend only on {@link BrowserControl}, which is exactly what D-2
 * was for — flipping {@code protocol=bidi} routes their calls here without touching them.
 *
 * Verification: since delta-080 this class compiles against the real Selenium 4.48 jars (Network,
 * LogInspector, BrowsingContext, Command/BiDi.send). Browser-side acceptance of network.addDataCollector /
 * getData depends on the browser + driver version, which is why RESPONSE_BODY is decided at runtime.
 *
 * @author Sumeet Chhetri
 */
public final class BiDiTransport implements BrowserControl {

    private final WebDriver driver;
    private Network bidiNetwork;
    private LogInspector logInspector;

    private volatile boolean networkOn = false;
    private volatile boolean consoleOn = false;
    private volatile NetworkListener netListener;
    private volatile ConsoleListener conListener;

    // Identical to CdpTransport: web-vitals are read from the page's Performance Timeline via JS, which is
    // protocol-agnostic — no BiDi (or CDP) API is involved, so this is genuine parity, not an approximation.
    private static final String VITALS_JS =
        "return JSON.stringify((function(){var o={};try{" +
        "var n=performance.getEntriesByType('navigation')[0];" +
        "if(n){o.ttfb=n.responseStart;o.domContentLoaded=n.domContentLoadedEventEnd;o.load=n.loadEventEnd;o.domInteractive=n.domInteractive;}" +
        "var p=performance.getEntriesByType('paint');" +
        "for(var i=0;i<p.length;i++){if(p[i].name==='first-contentful-paint')o.fcp=p[i].startTime;if(p[i].name==='first-paint')o.fp=p[i].startTime;}" +
        "var l=performance.getEntriesByType('largest-contentful-paint');" +
        "if(l&&l.length){o.lcp=l[l.length-1].startTime;}" +
        "}catch(e){o.error=String(e);}return o;})());";

    // Read-only performance/memory snapshot, sourced from page JS (protocol-agnostic). performance.memory
    // is Chrome-only but present under BiDi-on-Chrome; keys mirror the CDP Performance.getMetrics names the
    // MetricsRecorder reads (JSHeapUsedSize / JSHeapTotalSize) so memory.json + performance.json populate.
    // Returns a JS object → Selenium marshals it to a Map<String,Number>, so no JSON parsing is needed.
    private static final String METRICS_JS =
        "return (function(){var o={};try{" +
        "var m=performance.memory;" +
        "if(m){o.JSHeapUsedSize=m.usedJSHeapSize;o.JSHeapTotalSize=m.totalJSHeapSize;o.JSHeapLimit=m.jsHeapSizeLimit;}" +
        "o.Nodes=document.getElementsByTagName('*').length;" +
        "o.Frames=(window.frames?window.frames.length:0);" +
        "o.Documents=document.querySelectorAll('iframe,frame').length+1;" +
        "o.Resources=(performance.getEntriesByType?performance.getEntriesByType('resource').length:0);" +
        "o.Timestamp=Date.now()/1000;" +
        "}catch(e){}return o;})();";

    private final TraceConfig cfg;
    private volatile String collectorId;
    // M4: network interception
    private volatile com.gatf.selenium.route.RouteTable routes;
    private volatile boolean routesOn = false;
    private volatile String interceptId;
    private final java.util.Set<String> ownIntercepts = java.util.concurrent.ConcurrentHashMap.newKeySet();   // incl. removed ones
    private volatile boolean addingIntercept;
    private Network routeNetwork;          // the beforeRequestSent subscription that answers blocked requests
    private static final java.util.concurrent.atomic.AtomicBoolean ABORT_REASON_WARNED = new java.util.concurrent.atomic.AtomicBoolean();
    private volatile Thread videoPoller;
    private volatile String routeScope;    // H3: the one top-level browsing context the intercept covers (null = all)

    BiDiTransport(WebDriver driver) { this(driver, new TraceConfig()); }
    BiDiTransport(WebDriver driver, TraceConfig cfg) { this.driver = driver; this.cfg = cfg == null ? new TraceConfig() : cfg; }

    // G9: a LIVE BiDi socket, not just the HasBiDi interface (every 4.48 RemoteWebDriver implements it)
    private volatile Boolean bidiLive;
    private boolean hasBiDi() {
        Boolean b = bidiLive;
        if (b == null) bidiLive = b = BidiRaw.available(driver);
        return b;
    }

    @Override
    public boolean supports(Capability c) {
        switch (c) {
            case NETWORK:
            case CONSOLE:      return hasBiDi();
            case DOM_SNAPSHOT: return driver instanceof JavascriptExecutor;   // protocol-agnostic
            case DOM_SNAPSHOT_RICH: return driver instanceof JavascriptExecutor; // H2: serialized DOM + CSSOM by script
            case SCREENSHOT:   return driver instanceof TakesScreenshot;      // protocol-agnostic
            case METRICS:      return driver instanceof JavascriptExecutor;   // performance/memory via read-only JS (in-band; see class doc)
            case RESPONSE_BODY: return collectorId != null;                   // runtime: browser accepted network.addDataCollector
            case PRINT_PDF:    return hasBiDi() || driver instanceof PrintsPage;
            case ROUTE:        return hasBiDi();                                // M4: network.addIntercept
            case SCREENCAST:   return cfg.getVideoPollFps() > 0 && hasBiDi(); // opt-in polled approximation
            default:           return false;   // CPU_PROFILE / COVERAGE: CDP-only (protocol=hybrid on Chromium)
        }
    }

    @Override
    public void enableNetwork(NetworkListener l) {
        if (!hasBiDi()) return;
        this.netListener = l;
        this.networkOn = true;
        try {
            bidiNetwork = new Network(driver);
            bidiNetwork.onBeforeRequestSent(ev -> {
                if (!networkOn || netListener == null) return;
                try {
                    RequestData req = ev.getRequest();
                    netListener.onRequest(req.getRequestId(), req.getUrl(), req.getMethod(),
                            headers(req.getHeaders()), System.currentTimeMillis());
                } catch (Exception ignored) {}
            });
            bidiNetwork.onResponseCompleted(ev -> {
                if (!networkOn || netListener == null) return;
                try {
                    RequestData req = ev.getRequest();
                    ResponseData resp = ev.getResponseData();
                    netListener.onResponse(req.getRequestId(), (int) resp.getStatus(), resp.getStatusText(),
                            resp.getMimeType(), headers(resp.getHeaders()), System.currentTimeMillis());
                    netListener.onFinished(req.getRequestId(), resp.getBytesReceived(), System.currentTimeMillis());
                } catch (Exception ignored) {}
            });
            bidiNetwork.onFetchError(ev -> {
                if (!networkOn || netListener == null) return;
                try {
                    netListener.onFailed(ev.getRequest().getRequestId(), String.valueOf(ev.getErrorText()),
                            System.currentTimeMillis());
                } catch (Exception ignored) {}
            });
        } catch (Exception ignored) {}
        if (cfg.isCaptureBodies()) {
            try {
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("dataTypes", java.util.Collections.singletonList("response"));
                p.put("maxEncodedDataSize", cfg.getMaxBodyBytes());
                Object r = BidiRaw.send(driver, "network.addDataCollector", p);
                Object id = r instanceof Map ? ((Map<?, ?>) r).get("collector") : null;
                collectorId = id == null ? null : String.valueOf(id);
            } catch (Exception unsupported) {
                collectorId = null;   // older browser / driver: bodies not captured under BiDi
            }
        }
    }

    @Override
    public void disableNetwork() { this.networkOn = false; }

    /**
     * M4: one {@code network.addIntercept} for every request (beforeRequestSent) and a beforeRequestSent listener that
     * asks the route table; commands go out raw through BidiRaw (see BidiRoutes). Every BLOCKED request gets exactly
     * one answer — also after disableRoutes (a request blocked just before removeIntercept is continued) — and a
     * failing command falls back to continueRequest rather than leaving the page hanging.
     */
    @Override
    public synchronized boolean enableRoutes(com.gatf.selenium.route.RouteTable table, boolean keepResponseStage) {
        if (!hasBiDi()) return false;
        this.routes = table;
        // subscribe first: a request blocked between addIntercept and the subscription would hang
        this.routesOn = true;
        if (routeNetwork == null) {
            routeNetwork = new Network(driver);
            routeNetwork.onBeforeRequestSent(ev -> {
                if (!ev.isBlocked() || !ours(ev)) return;   // not blocked by our intercept: nothing to answer
                String id = null;
                try {
                    RequestData req = ev.getRequest();
                    id = req.getRequestId();
                    com.gatf.selenium.route.RouteTable t = routes;
                    // routes switched off while this request was blocked: plain continue
                    com.gatf.selenium.route.Route d = !routesOn || t == null ? null : t.match(req.getMethod(), req.getUrl());
                    if (d != null && d.action == com.gatf.selenium.route.Route.Action.ABORT && !"failed".equals(d.reason)
                            && ABORT_REASON_WARNED.compareAndSet(false, true)) {
                        System.out.println("route: WebDriver BiDi has no abort reasons - '" + d.reason + "' is sent as a plain network error");
                    }
                    java.util.Set<String> b64 = new java.util.HashSet<>();
                    Object[] c = com.gatf.selenium.route.BidiRoutes.command(d, id, headers(req.getHeaders(), b64), b64);
                    @SuppressWarnings("unchecked") Map<String, Object> params = (Map<String, Object>) c[1];
                    BidiRaw.send(driver, (String) c[0], params);
                } catch (Exception e) {
                    if (id == null) return;
                    try {
                        Map<String, Object> cp = new LinkedHashMap<>();
                        cp.put("request", id);
                        BidiRaw.send(driver, "network.continueRequest", cp);
                    } catch (Exception ignored) {}
                }
            });
        }
        if (interceptId == null) {
            // while addIntercept is in flight its id is unknown: a request it blocks meanwhile counts as ours
            addingIntercept = true;
            try {
                Map<String, Object> r = BidiRaw.send(driver, "network.addIntercept", com.gatf.selenium.route.BidiRoutes.interceptParams(routeScope));
                Object id = r == null ? null : r.get("intercept");
                if (id == null) {
                    routesOn = false;
                    return false;
                }
                interceptId = String.valueOf(id);
                ownIntercepts.add(interceptId);
            } finally {
                addingIntercept = false;
            }
        }
        return true;
    }

    /** H3 (Δ153): an intercept limited to one top-level browsing context (a per-test context's tab). */
    @Override
    public synchronized boolean enableRoutes(com.gatf.selenium.route.RouteTable table, boolean keepResponseStage, String browsingContext) {
        if (interceptId != null && !java.util.Objects.equals(routeScope, browsingContext)) disableRoutes();   // re-scope
        routeScope = browsingContext;
        return enableRoutes(table, keepResponseStage);
    }

    /** H3 (Δ153): browser.createUserContext / browsingContext.create / browser.removeUserContext as raw BiDi. */
    @Override
    public com.gatf.selenium.context.ContextOps contextOps() {
        if (!hasBiDi()) return null;
        return com.gatf.selenium.context.ContextCalls.bidi((m, p) -> BidiRaw.send(driver, m, p));
    }

    // Blocked by one of this transport's intercepts (a later transport on a reused session answers its own). The
    // event's intercept list is read reflectively (BaseParameters.getIntercepts); if it cannot be read, assume ours.
    private boolean ours(Object ev) {
        if (addingIntercept) return true;
        try {
            Object ids = ev.getClass().getMethod("getIntercepts").invoke(ev);
            if (!(ids instanceof java.util.Collection) || ((java.util.Collection<?>) ids).isEmpty()) return true;
            for (Object i : (java.util.Collection<?>) ids) if (ownIntercepts.contains(String.valueOf(i))) return true;
            return false;
        } catch (Exception | LinkageError e) {
            return true;
        }
    }

    @Override
    public synchronized void disableRoutes() {
        routesOn = false;
        String id = interceptId;
        interceptId = null;
        if (id == null) return;
        try {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("intercept", id);
            BidiRaw.send(driver, "network.removeIntercept", p);
        } catch (Exception ignored) {}
    }

    @Override
    public void enableConsole(ConsoleListener l) {
        if (!hasBiDi()) return;
        this.conListener = l;
        this.consoleOn = true;
        try {
            logInspector = new LogInspector(driver);
            logInspector.onConsoleEntry(entry -> {
                if (!consoleOn || conListener == null) return;
                try {
                    conListener.onEntry(System.currentTimeMillis(), String.valueOf(entry.getLevel()),
                            entry.getText(), null, null, "console");
                } catch (Exception ignored) {}
            });
            logInspector.onJavaScriptLog(entry -> {
                if (!consoleOn || conListener == null) return;
                try {
                    conListener.onEntry(System.currentTimeMillis(), String.valueOf(entry.getLevel()),
                            entry.getText(), null, null, "javascript");
                } catch (Exception ignored) {}
            });
        } catch (Exception ignored) {}
    }

    @Override
    public void disableConsole() { this.consoleOn = false; }

    @Override
    public String captureDomSnapshot() {
        try {
            if (driver instanceof JavascriptExecutor) {
                Object html = ((JavascriptExecutor) driver).executeScript("return document.documentElement.outerHTML");
                return html == null ? null : String.valueOf(html);
            }
        } catch (Exception ignored) {}
        return null;
    }

    /** H2 (Δ146): serialized DOM + stylesheets by script (the BiDi fallback for DOMSnapshot). Never throws. */
    @Override
    public RichSnapshot captureRichSnapshot(java.util.List<Object> targets, java.util.List<String> marks) {
        if (!(driver instanceof JavascriptExecutor)) return null;
        try {
            final JavascriptExecutor js = (JavascriptExecutor) driver;
            return DomCapture.captureByScript(args -> js.executeScript(DomCapture.META_JS, args), targets, marks, DomCapture.Caps.of(cfg));
        } catch (Exception e) {
            return null;
        }
    }

    /** Δ146a: lite = the sanitizing script serializer without stylesheets (never raw outerHTML). Never throws. */
    @Override
    public RichSnapshot captureLiteSnapshot(java.util.List<Object> targets, java.util.List<String> marks) {
        if (!(driver instanceof JavascriptExecutor)) return null;
        try {
            final JavascriptExecutor js = (JavascriptExecutor) driver;
            return DomCapture.captureLite(args -> js.executeScript(DomCapture.META_JS, args), targets, marks, DomCapture.Caps.of(cfg));
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public byte[] captureScreenshot() {
        try {
            if (driver instanceof TakesScreenshot) return ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
        } catch (Exception ignored) {}
        return null;
    }

    @Override
    public Map<String, Number> sampleMetrics() {
        Map<String, Number> out = new LinkedHashMap<>();
        try {
            if (driver instanceof JavascriptExecutor) {
                Object r = ((JavascriptExecutor) driver).executeScript(METRICS_JS);
                if (r instanceof Map) {
                    for (Map.Entry<?, ?> e : ((Map<?, ?>) r).entrySet()) {
                        Object v = e.getValue();
                        if (v instanceof Number) out.put(String.valueOf(e.getKey()), (Number) v);
                    }
                }
            }
        } catch (Exception ignored) {}
        return out;
    }

    @Override
    public String readWebVitals() {
        try {
            if (driver instanceof JavascriptExecutor) {
                try {   // Q9: async script so LCP/CLS (observer-only entries) are included
                    Object a = ((JavascriptExecutor) driver).executeAsyncScript(WebVitals.ASYNC_JS);
                    if (a != null) return String.valueOf(a);
                } catch (Exception ignored) {}
                Object r = ((JavascriptExecutor) driver).executeScript(VITALS_JS);
                return r == null ? null : String.valueOf(r);
            }
        } catch (Exception ignored) {}
        return null;
    }

    // --- Q9: per-navigation vitals via raw script.addPreloadScript. GATF does not subscribe to script.message, so there is
    // no out-of-band channel: entries buffer in the page's sessionStorage and survive same-origin navigations only
    // (a page left for another origin is not reported unless the script returns to that origin). ---
    private volatile String vitalsPreloadId;

    @Override
    public boolean enablePageVitals(java.util.function.Consumer<String> sink) {
        if (!hasBiDi() || !(driver instanceof JavascriptExecutor)) return false;
        try {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("functionDeclaration", "function(){" + WebVitals.PRELOAD_JS + "}");
            Object id = BidiRaw.send(driver, "script.addPreloadScript", p).get("script");
            vitalsPreloadId = id == null ? null : String.valueOf(id);
        } catch (Exception e) {
            return false;
        }
        try { ((JavascriptExecutor) driver).executeScript(WebVitals.PRELOAD_JS); } catch (Exception ignored) {}
        return true;
    }

    @Override
    public String drainPageVitals() {
        try {
            if (driver instanceof JavascriptExecutor) {
                Object r = ((JavascriptExecutor) driver).executeScript(WebVitals.DRAIN_JS);
                return r == null ? null : String.valueOf(r);
            }
        } catch (Exception ignored) {}
        return null;
    }

    @Override
    public void disablePageVitals() {
        String id = vitalsPreloadId;
        vitalsPreloadId = null;
        if (id == null) return;
        try {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("script", id);
            BidiRaw.send(driver, "script.removePreloadScript", p);
        } catch (Exception ignored) {}
    }

    @Override
    public byte[] fetchBody(String requestId) {
        String c = collectorId;
        if (c == null || requestId == null) return null;
        try {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("dataType", "response");
            p.put("request", requestId);
            p.put("collector", c);
            p.put("disown", true);   // release it in the browser once read
            Object r = BidiRaw.send(driver, "network.getData", p);
            return decodeBytes(r instanceof Map ? ((Map<?, ?>) r).get("bytes") : null);
        } catch (Exception e) {
            return null;   // not collected (redirect, over maxEncodedDataSize, evicted, ...)
        }
    }

    // BiDi network.BytesValue: {type:"string", value:"..."} | {type:"base64", value:"..."}
    static byte[] decodeBytes(Object bytes) {
        if (!(bytes instanceof Map)) return null;
        Object type = ((Map<?, ?>) bytes).get("type"), value = ((Map<?, ?>) bytes).get("value");
        if (value == null) return null;
        if ("base64".equals(String.valueOf(type))) return java.util.Base64.getDecoder().decode(String.valueOf(value));
        return String.valueOf(value).getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    @Override
    public byte[] printPdf() {
        try {
            String b64 = hasBiDi()
                    ? new BrowsingContext(driver, driver.getWindowHandle()).print(new PrintOptions())
                    : ((PrintsPage) driver).print(new PrintOptions()).getContent();
            return b64 == null ? null : java.util.Base64.getDecoder().decode(b64);
        } catch (Exception e) {
            try {
                if (driver instanceof PrintsPage) return java.util.Base64.getDecoder().decode(((PrintsPage) driver).print(new PrintOptions()).getContent());
            } catch (Exception ignored) {}
            return null;
        }
    }

    // Opt-in polled screencast (see class doc): JPEG straight from browsingContext.captureScreenshot.
    @Override
    public void enableVideo(VideoListener l) {
        if (l == null || cfg.getVideoPollFps() <= 0 || !hasBiDi() || videoPoller != null) return;
        final long periodMs = 1000L / cfg.getVideoPollFps();
        final double q = Math.max(0.1, Math.min(1.0, cfg.getVideoQuality() / 100.0));
        final String ctx;
        try { ctx = driver.getWindowHandle(); } catch (Exception e) { return; }
        Thread t = new Thread(() -> {
            BrowsingContext bc = new BrowsingContext(driver, ctx);
            CaptureScreenshotParameters params = new CaptureScreenshotParameters().imageFormat("image/jpeg", q);
            while (videoPoller == Thread.currentThread()) {
                long t0 = System.currentTimeMillis();
                try {
                    String b64 = bc.captureScreenshot(params);
                    if (b64 != null) l.onFrame(java.util.Base64.getDecoder().decode(b64), t0);
                } catch (Exception ignored) {}   // navigation in progress / context gone: skip a frame
                long sleep = periodMs - (System.currentTimeMillis() - t0);
                try { if (sleep > 0) Thread.sleep(sleep); } catch (InterruptedException ie) { return; }
            }
        }, "gatf-bidi-screencast");
        t.setDaemon(true);
        videoPoller = t;
        t.start();
    }

    @Override
    public void disableVideo() {
        Thread t = videoPoller;
        videoPoller = null;
        if (t != null) t.interrupt();
    }

    @Override
    public void close() {
        disableVideo();
        disableRoutes();   // no intercept may outlive the transport (the session can be kept for reuse)
        String c = collectorId;
        collectorId = null;
        if (c != null) {
            try {
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("collector", c);
                BidiRaw.send(driver, "network.removeDataCollector", p);
            } catch (Exception ignored) {}
        }
        this.networkOn = false;
        this.consoleOn = false;
        this.netListener = null;
        this.conListener = null;
    }

    private static Map<String, String> headers(List<Header> hs) {
        return headers(hs, null);
    }

    /**
     * Header values as text; a base64 BytesValue (non-UTF-8 bytes) is decoded, or — when {@code base64Names} is given
     * (route continue) — kept as its base64 text and its name recorded, so it can be sent back unchanged.
     */
    private static Map<String, String> headers(List<Header> hs, java.util.Set<String> base64Names) {
        Map<String, String> m = new LinkedHashMap<>();
        if (hs != null) {
            for (Header h : hs) {
                try {
                    String v = h.getValue() != null ? h.getValue().getValue() : "";
                    if (h.getValue() != null && "base64".equalsIgnoreCase(String.valueOf(h.getValue().getType())) && v != null) {
                        if (base64Names != null) base64Names.add(h.getName());
                        else v = new String(java.util.Base64.getDecoder().decode(v), java.nio.charset.StandardCharsets.UTF_8);
                    }
                    m.put(h.getName(), v);
                } catch (Exception ignored) {}
            }
        }
        return m;
    }
}
