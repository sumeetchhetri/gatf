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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.codec.binary.Base64;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chromium.HasCdp;
import org.openqa.selenium.devtools.DevTools;
import org.openqa.selenium.devtools.Command;
import org.openqa.selenium.devtools.latest.log.Log;
import org.openqa.selenium.devtools.latest.network.Network;
import org.openqa.selenium.devtools.latest.network.model.Headers;
import org.openqa.selenium.devtools.latest.network.model.RequestId;
import org.openqa.selenium.devtools.latest.page.Page;
import org.openqa.selenium.devtools.latest.performance.Performance;
import org.openqa.selenium.devtools.latest.performance.model.Metric;
import org.openqa.selenium.devtools.latest.runtime.Runtime;
import org.openqa.selenium.devtools.latest.runtime.model.RemoteObject;

/**
 * CDP implementation of {@link BrowserControl} over a Selenium DevTools session
 * (devtools.latest). Every CDP call here mirrors a pattern already used in
 * {@code SeleniumTest.initBrowser}/{@code networkApiInspectPre} (Network.enable / getResponseBody
 * / Log.entryAdded / Fetch base64 decode), so the API usage matches Selenium 4.48 known-good code.
 *
 * Reuses the session already created in initBrowser and only ADDS listeners; Selenium's DevTools
 * API has no per-listener removal, so {@link #disableNetwork()}/{@link #disableConsole()}/{@link
 * #close()} flip volatile flags that the listener bodies check — capture stops without clearing
 * GATF's own listeners. Never throws upward: a recorder must not break a test run.
 *
 * NOTE: references Selenium types, so it is compiled in CI (the container had no Selenium jars);
 * the transport-neutral recorder core is javac-verified + functionally tested in-container.
 *
 * @author Sumeet Chhetri
 */
public final class CdpTransport implements BrowserControl {

    private final WebDriver driver;
    private final DevTools devTools;
    private final TraceConfig cfg;
    private final ConcurrentHashMap<String, RequestId> reqIds = new ConcurrentHashMap<>();

    private volatile boolean networkOn = false;
    private volatile boolean consoleOn = false;
    private volatile boolean videoOn = false;
    private volatile NetworkListener netListener;
    private volatile com.gatf.selenium.route.RouteTable routes;   // M4
    private volatile boolean routesOn = false;
    private boolean routeListenerAdded = false;
    private boolean routesKeepResponseStage = false;
    private volatile ConsoleListener conListener;
    private volatile VideoListener videoListener;

    private static final String VITALS_JS =
        "return JSON.stringify((function(){var o={};try{" +
        "var n=performance.getEntriesByType('navigation')[0];" +
        "if(n){o.ttfb=n.responseStart;o.domContentLoaded=n.domContentLoadedEventEnd;o.load=n.loadEventEnd;o.domInteractive=n.domInteractive;}" +
        "var p=performance.getEntriesByType('paint');" +
        "for(var i=0;i<p.length;i++){if(p[i].name==='first-contentful-paint')o.fcp=p[i].startTime;if(p[i].name==='first-paint')o.fp=p[i].startTime;}" +
        "var l=performance.getEntriesByType('largest-contentful-paint');" +
        "if(l&&l.length){o.lcp=l[l.length-1].startTime;}" +
        "}catch(e){o.error=String(e);}return o;})());";

    CdpTransport(WebDriver driver, DevTools devTools, TraceConfig cfg) {
        this.driver = driver;
        this.devTools = devTools;
        this.cfg = cfg;
    }

    @Override
    public boolean supports(Capability c) {
        switch (c) {
            case NETWORK:
            case CONSOLE:
            case METRICS:
            case RESPONSE_BODY: return devTools != null;
            case DOM_SNAPSHOT:  return driver instanceof JavascriptExecutor;
            case DOM_SNAPSHOT_RICH: return driver instanceof JavascriptExecutor;   // H2: DOMSnapshot via HasCdp, else script
            case SCREENSHOT:    return driver instanceof TakesScreenshot;
            case CPU_PROFILE:
            case COVERAGE:      return driver instanceof HasCdp;
            case SCREENCAST:    return devTools != null && driver instanceof HasCdp;
            case PRINT_PDF:     return driver instanceof HasCdp || driver instanceof org.openqa.selenium.PrintsPage;
            case ROUTE:         return devTools != null;
            default:            return false;
        }
    }

    @Override
    public void enableNetwork(NetworkListener l) {
        if (devTools == null) return;
        this.netListener = l;
        this.networkOn = true;
        // Network.enable (v150..v152 / devtools.latest) takes 5 typed Optionals: maxTotalBufferSize, maxResourceBufferSize,
        // maxPostDataSize, reportDirectSocketTraffic, ...  All defaulted.
        devTools.send(Network.enable(Optional.<Integer>empty(), Optional.<Integer>empty(),
                Optional.<Integer>empty(), Optional.<Boolean>empty(), Optional.<Boolean>empty()));

        devTools.addListener(Network.requestWillBeSent(), ev -> {
            if (!networkOn || netListener == null) return;
            try {
                String id = ev.getRequestId().toString();
                netListener.onRequest(id, ev.getRequest().getUrl(), ev.getRequest().getMethod(),
                        toMap(ev.getRequest().getHeaders()), System.currentTimeMillis());
            } catch (Exception ignored) {}
        });
        devTools.addListener(Network.responseReceived(), ev -> {
            if (!networkOn || netListener == null) return;
            try {
                RequestId rid = ev.getRequestId();
                reqIds.put(rid.toString(), rid);           // keep the id object for body fetch
                int status = ev.getResponse().getStatus() == null ? 0
                        : ((Number) ev.getResponse().getStatus()).intValue();
                netListener.onResponse(rid.toString(), status, ev.getResponse().getStatusText(),
                        ev.getResponse().getMimeType(), toMap(ev.getResponse().getHeaders()),
                        System.currentTimeMillis());
            } catch (Exception ignored) {}
        });
        devTools.addListener(Network.loadingFinished(), ev -> {
            if (!networkOn || netListener == null) return;
            try {
                long len = ev.getEncodedDataLength() == null ? -1
                        : ((Number) ev.getEncodedDataLength()).longValue();
                netListener.onFinished(ev.getRequestId().toString(), len, System.currentTimeMillis());
            } catch (Exception ignored) {}
        });
        devTools.addListener(Network.loadingFailed(), ev -> {
            if (!networkOn || netListener == null) return;
            try {
                netListener.onFailed(ev.getRequestId().toString(), String.valueOf(ev.getErrorText()),
                        System.currentTimeMillis());
            } catch (Exception ignored) {}
        });
    }

    @Override
    public void disableNetwork() { this.networkOn = false; }

    @Override
    public void enableConsole(ConsoleListener l) {
        if (devTools == null) return;
        this.conListener = l;
        this.consoleOn = true;
        try { devTools.send(Log.enable()); } catch (Exception ignored) {}
        devTools.addListener(Log.entryAdded(), e -> {
            if (!consoleOn || conListener == null) return;
            try {
                conListener.onEntry(System.currentTimeMillis(), String.valueOf(e.getLevel()), e.getText(),
                        optStr(e.getUrl()), optInt(e.getLineNumber()), "log");
            } catch (Exception ignored) {}
        });
        try { devTools.send(Runtime.enable()); } catch (Exception ignored) {}
        devTools.addListener(Runtime.consoleAPICalled(), e -> {
            if (!consoleOn || conListener == null) return;
            try {
                conListener.onEntry(System.currentTimeMillis(), String.valueOf(e.getType()),
                        argsToText(e.getArgs()), null, null, "console-api");
            } catch (Exception ignored) {}
        });
    }

    @Override
    public void disableConsole() { this.consoleOn = false; }

    /**
     * H3 (Δ153): Target.createBrowserContext / createTarget / disposeBrowserContext. Chromium refuses these on a page
     * target ("Not allowed"), so they go over their own browser-level connection (no session id), opened on first use
     * from the session's se:cdp / debuggerAddress and closed by {@code ContextOps.close()}.
     */
    @Override
    public com.gatf.selenium.context.ContextOps contextOps() {
        if (!(driver instanceof org.openqa.selenium.HasCapabilities)) return null;
        final java.util.concurrent.atomic.AtomicReference<org.openqa.selenium.devtools.Connection> conn = new java.util.concurrent.atomic.AtomicReference<>();
        com.gatf.selenium.context.ContextOps.Sender s = (m, p) -> {
            org.openqa.selenium.devtools.Connection c = conn.get();
            if (c == null) {
                c = org.openqa.selenium.devtools.SeleniumCdpConnection.create(driver)
                        .orElseThrow(() -> new IllegalStateException("no browser-level CDP endpoint (se:cdp / debuggerAddress)"));
                conn.set(c);
            }
            @SuppressWarnings({ "unchecked", "rawtypes" })
            Map<String, Object> r = (Map<String, Object>) c.sendAndWait(null, new Command<Map>(m, p, Map.class), java.time.Duration.ofSeconds(30));
            return r == null ? new HashMap<String, Object>() : r;
        };
        return com.gatf.selenium.context.ContextCalls.cdp(s, () -> {
            org.openqa.selenium.devtools.Connection c = conn.getAndSet(null);
            if (c != null) {
                try { c.close(); } catch (Exception ignored) {}
            }
        });
    }

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

    /** H2 (Δ146): CDP DOMSnapshot.captureSnapshot (+ page CSSOM/targets by script); the script serializer when the
     *  driver has no raw CDP channel or the command fails. Never throws. */
    @Override
    public RichSnapshot captureRichSnapshot(List<Object> targets, List<String> marks) {
        if (!(driver instanceof JavascriptExecutor)) return null;
        try {
            final JavascriptExecutor js = (JavascriptExecutor) driver;
            DomCapture.ScriptRunner run = args -> js.executeScript(DomCapture.META_JS, args);
            DomCapture.Caps caps = DomCapture.Caps.of(cfg);
            if (driver instanceof HasCdp) {
                try {
                    Object meta = DomCapture.runMeta(run, targets, marks, false, caps);
                    Map<String, Object> snap = ((HasCdp) driver).executeCdpCommand("DOMSnapshot.captureSnapshot", DomCapture.cdpParams());
                    RichSnapshot r = DomCapture.fromCdp(snap, meta, marks, targets == null ? 0 : targets.size(), caps);
                    if (r != null) return r;
                } catch (Exception cdpFailed) { /* fall through to the script serializer */ }
            }
            return DomCapture.captureByScript(run, targets, marks, caps);
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
            if (driver instanceof TakesScreenshot) {
                return ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
            }
        } catch (Exception ignored) {}
        return null;
    }

    @Override
    public byte[] fetchBody(String requestId) {
        try {
            RequestId rid = reqIds.get(requestId);
            if (rid == null || devTools == null) return null;
            Network.GetResponseBodyResponse r = devTools.send(Network.getResponseBody(rid));
            String body = r.getBody();
            if (body == null) return null;
            return r.getBase64Encoded() ? Base64.decodeBase64(body) : body.getBytes(StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return null;   // body no longer available (evicted) — HAR keeps the entry, drops the body
        }
    }

    @Override
    public void close() {
        if (routesOn) disableRoutes();   // no Fetch interception may outlive the transport (the session can be kept for reuse)
        this.networkOn = false;
        this.consoleOn = false;
        this.netListener = null;
        this.conListener = null;
        this.reqIds.clear();
    }

    @Override
    public void enableMetrics() {
        if (devTools == null) return;
        try {
            java.util.Optional<Performance.EnableTimeDomain> td = java.util.Optional.empty();
            devTools.send(Performance.enable(td));
        } catch (Exception ignored) {}
    }

    @Override
    public void disableMetrics() {
        if (devTools == null) return;
        try { devTools.send(Performance.disable()); } catch (Exception ignored) {}
    }

    @Override
    public Map<String, Number> sampleMetrics() {
        Map<String, Number> out = new LinkedHashMap<>();
        if (devTools == null) return out;
        try {
            List<Metric> ms = devTools.send(Performance.getMetrics());
            if (ms != null) for (Metric m : ms) out.put(m.getName(), m.getValue());
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

    // --- Q9: per-navigation vitals. Runtime.addBinding gives the page an out-of-band sink (bindingCalled events on
    // this DevTools session), so a page's entry arrives as it is left, cross-origin navigations included. ---
    private volatile java.util.function.Consumer<String> vitalsSink;
    private volatile Object vitalsScriptId;
    private boolean vitalsListenerAdded = false;

    @Override
    public synchronized boolean enablePageVitals(java.util.function.Consumer<String> sink) {
        if (devTools == null || !(driver instanceof HasCdp) || !(driver instanceof JavascriptExecutor)) return false;
        try {
            try { devTools.send(Runtime.enable()); } catch (Exception ignored) {}
            Map<String, Object> b = new HashMap<>();
            b.put("name", WebVitals.SINK);
            devTools.send(new Command<Void>("Runtime.addBinding", b));
            if (!vitalsListenerAdded) {
                vitalsListenerAdded = true;
                devTools.addListener(Runtime.bindingCalled(), ev -> {
                    java.util.function.Consumer<String> s = vitalsSink;
                    if (s == null || !WebVitals.SINK.equals(ev.getName())) return;
                    try { s.accept(ev.getPayload()); } catch (Exception ignored) {}
                });
            }
            vitalsSink = sink;
            Map<String, Object> a = new HashMap<>();
            a.put("source", WebVitals.PRELOAD_JS);
            vitalsScriptId = ((HasCdp) driver).executeCdpCommand("Page.addScriptToEvaluateOnNewDocument", a).get("identifier");
        } catch (Exception e) {
            vitalsSink = null;
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
    public synchronized void disablePageVitals() {
        vitalsSink = null;
        Object id = vitalsScriptId;
        vitalsScriptId = null;
        try {
            if (id != null) {
                Map<String, Object> a = new HashMap<>();
                a.put("identifier", id);
                ((HasCdp) driver).executeCdpCommand("Page.removeScriptToEvaluateOnNewDocument", a);
            }
        } catch (Exception ignored) {}
        try {
            Map<String, Object> b = new HashMap<>();
            b.put("name", WebVitals.SINK);
            if (devTools != null) devTools.send(new Command<Void>("Runtime.removeBinding", b));
        } catch (Exception ignored) {}
    }

    // --- G10: print-to-PDF via raw CDP Page.printToPDF, falling back to the W3C classic print command ---
    @Override
    public byte[] printPdf() {
        try {
            if (driver instanceof HasCdp) {
                Map<String, Object> args = new HashMap<>();
                args.put("printBackground", true);
                Object data = ((HasCdp) driver).executeCdpCommand("Page.printToPDF", args).get("data");
                if (data != null) return java.util.Base64.getDecoder().decode(String.valueOf(data));
            }
        } catch (Exception ignored) {}
        try {
            if (driver instanceof org.openqa.selenium.PrintsPage) {
                return java.util.Base64.getDecoder().decode(((org.openqa.selenium.PrintsPage) driver)
                        .print(new org.openqa.selenium.print.PrintOptions()).getContent());
            }
        } catch (Exception ignored) {}
        return null;
    }

    // --- CPU profile + coverage via raw CDP (HasCdp.executeCdpCommand -> Map), serialized generically ---

    @Override
    public void startCpu() {
        if (!(driver instanceof HasCdp)) return;
        try {
            HasCdp c = (HasCdp) driver;
            c.executeCdpCommand("Profiler.enable", new HashMap<>());
            c.executeCdpCommand("Profiler.start", new HashMap<>());
        } catch (Exception ignored) {}
    }

    @Override
    public String stopCpu() {
        if (!(driver instanceof HasCdp)) return null;
        try {
            Map<String, Object> r = ((HasCdp) driver).executeCdpCommand("Profiler.stop", new HashMap<>());
            Object profile = r == null ? null : r.get("profile");
            return profile == null ? null : Json.write(profile);
        } catch (Exception ignored) { return null; }
    }

    @Override
    public void startCoverage() {
        if (!(driver instanceof HasCdp)) return;
        HasCdp c = (HasCdp) driver;
        try {
            c.executeCdpCommand("Profiler.enable", new HashMap<>());
            Map<String, Object> pc = new HashMap<>();
            pc.put("callCount", true);
            pc.put("detailed", true);
            c.executeCdpCommand("Profiler.startPreciseCoverage", pc);
        } catch (Exception e) { System.err.println("[gatf-trace] JS coverage start failed: " + e); }
        try {
            c.executeCdpCommand("DOM.enable", new HashMap<>());
            c.executeCdpCommand("CSS.enable", new HashMap<>());
            c.executeCdpCommand("CSS.startRuleUsageTracking", new HashMap<>());
        } catch (Exception e) { System.err.println("[gatf-trace] CSS coverage start failed: " + e); }
    }

    @Override
    public String stopCoverage() {
        if (!(driver instanceof HasCdp)) return null;
        HasCdp c = (HasCdp) driver;
        Map<String, Object> out = new LinkedHashMap<>();
        boolean any = false;
        try {
            Map<String, Object> js = c.executeCdpCommand("Profiler.takePreciseCoverage", new HashMap<>());
            out.put("js", js == null ? null : js.get("result"));
            any = true;
        } catch (Exception e) { System.err.println("[gatf-trace] JS coverage take failed: " + e); }
        try {
            Map<String, Object> css = c.executeCdpCommand("CSS.stopRuleUsageTracking", new HashMap<>());
            out.put("css", css == null ? null : css.get("ruleUsage"));
            any = true;
        } catch (Exception e) { System.err.println("[gatf-trace] CSS coverage stop failed: " + e); }
        return any ? Json.write(out) : null;
    }

    @Override
    public void enableVideo(VideoListener l) {
        if (devTools == null) return;
        this.videoListener = l;
        this.videoOn = true;
        int quality = cfg != null ? cfg.getVideoQuality() : 60;
        int everyNth = cfg != null ? cfg.getVideoEveryNthFrame() : 2;
        // Start the screencast on the SAME DevTools (WebSocket) session the frame listener is on.
        // HasCdp.executeCdpCommand goes through the Grid's HTTP /goog/cdp/execute channel, a
        // different CDP session -- frames started there never reach this WebSocket listener.
        // Raw Command keeps this version-agnostic (no typed vNNN Page.startScreencast needed).
        try {
            devTools.send(new Command<Void>("Page.enable", new HashMap<>()));
            Map<String, Object> args = new HashMap<>();
            args.put("format", "jpeg");
            args.put("quality", quality);
            args.put("everyNthFrame", everyNth);
            devTools.send(new Command<Void>("Page.startScreencast", args));
        } catch (Exception e) { System.err.println("[gatf-trace] startScreencast failed: " + e); }
        devTools.addListener(Page.screencastFrame(), ev -> {
            if (!videoOn || videoListener == null) return;
            try {
                byte[] jpg = Base64.decodeBase64(ev.getData());
                videoListener.onFrame(jpg, System.currentTimeMillis());
                Integer sid = ev.getSessionId();
                if (sid != null) {
                    Map<String, Object> ack = new HashMap<>();
                    ack.put("sessionId", sid);
                    devTools.send(new Command<Void>("Page.screencastFrameAck", ack));
                }
            } catch (Exception ignored) {}
        });
    }

    @Override
    public void disableVideo() {
        this.videoOn = false;
        try {
            if (devTools != null) devTools.send(new Command<Void>("Page.stopScreencast", new HashMap<>()));
        } catch (Exception ignored) {}
    }

    /**
     * M4: Fetch.enable on the request stage (plus the response stage when netapix shares the session) and one
     * requestPaused listener that asks the route table. Commands are raw CDP (see CdpRoutes), so only the paused
     * event uses the typed devtools.latest binding — the same one SeleniumTest's netapix already listens to.
     */
    @Override
    public synchronized boolean enableRoutes(com.gatf.selenium.route.RouteTable table, boolean keepResponseStage) {
        if (devTools == null) return false;
        this.routes = table;
        this.routesOn = true;
        this.routesKeepResponseStage = keepResponseStage;
        devTools.createSessionIfThereIsNotOne();
        java.util.List<Map<String, Object>> patterns = new java.util.ArrayList<>();
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("urlPattern", "*");
        req.put("requestStage", "Request");
        patterns.add(req);
        if (keepResponseStage) {
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("urlPattern", "*");
            res.put("requestStage", "Response");
            patterns.add(res);
        }
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("patterns", patterns);
        // the listener goes first: a request paused between Fetch.enable and addListener would hang
        if (!routeListenerAdded) {
            routeListenerAdded = true;
            devTools.addListener(org.openqa.selenium.devtools.latest.fetch.Fetch.requestPaused(), ev -> {
                // response-stage pauses belong to netapix (it continues them itself)
                if (ev.getResponseStatusCode().isPresent() || ev.getResponseErrorReason().isPresent()) return;
                String id = ev.getRequestId().toString();
                com.gatf.selenium.route.Route d = null;
                try {
                    com.gatf.selenium.route.RouteTable t = routes;
                    if (routesOn && t != null) d = t.match(ev.getRequest().getMethod(), ev.getRequest().getUrl(), postData(ev.getRequest()));
                    Object[] c = com.gatf.selenium.route.CdpRoutes.command(d, id, toMap(ev.getRequest().getHeaders()));
                    @SuppressWarnings("unchecked") Map<String, Object> params = (Map<String, Object>) c[1];
                    devTools.send(new Command<Void>((String) c[0], params));
                } catch (Exception e) {
                    // never leave a request paused: fall back to letting it through (said out loud: the real backend answers)
                    System.out.println("route: " + (d == null ? "continue" : d.toString()) + " failed (" + e.getMessage() + "); request sent to the network");
                    try {
                        Map<String, Object> cp = new LinkedHashMap<>();
                        cp.put("requestId", id);
                        devTools.send(new Command<Void>("Fetch.continueRequest", cp));
                    } catch (Exception ignored) {}
                }
            });
        }
        devTools.send(new Command<Void>("Fetch.enable", p));
        return true;
    }

    @Override
    public synchronized void disableRoutes() {
        routesOn = false;
        try {
            if (devTools == null) return;
            if (routesKeepResponseStage) {   // netapix still needs its response-stage pauses
                Map<String, Object> res = new LinkedHashMap<>();
                res.put("urlPattern", "*");
                res.put("requestStage", "Response");
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("patterns", java.util.Collections.singletonList(res));
                devTools.send(new Command<Void>("Fetch.enable", p));
            } else {
                devTools.send(new Command<Void>("Fetch.disable", new HashMap<>()));
            }
        } catch (Exception ignored) {}
    }

    // Request.postData, read reflectively: CDP marks it deprecated (postDataEntries), so a later devtools.vNNN may drop
    // the getter; the body only breaks ties between HAR entries, so losing it degrades quietly. Absent for large bodies.
    static String postData(Object request) {
        try {
            Object o = request.getClass().getMethod("getPostData").invoke(request);
            if (o instanceof Optional) o = ((Optional<?>) o).orElse(null);
            return o == null ? null : String.valueOf(o);
        } catch (Exception | LinkageError e) {
            return null;
        }
    }

    private static Map<String, String> toMap(Headers headers) {
        Map<String, String> m = new LinkedHashMap<>();
        if (headers != null) {
            for (Map.Entry<String, Object> e : headers.entrySet()) {
                m.put(e.getKey(), e.getValue() == null ? "" : String.valueOf(e.getValue()));
            }
        }
        return m;
    }

    private static String argsToText(List<RemoteObject> args) {
        if (args == null || args.isEmpty()) return "";
        StringBuilder b = new StringBuilder();
        for (RemoteObject a : args) {
            if (b.length() > 0) b.append(' ');
            String s = optStr(a.getDescription());
            if (s == null && a.getValue() != null && a.getValue().isPresent()) s = String.valueOf(a.getValue().get());
            b.append(s == null ? "" : s);
        }
        return b.toString();
    }

    private static String optStr(Optional<String> o) { return (o != null && o.isPresent()) ? o.get() : null; }
    private static Integer optInt(Optional<Integer> o) { return (o != null && o.isPresent()) ? o.get() : null; }
}
