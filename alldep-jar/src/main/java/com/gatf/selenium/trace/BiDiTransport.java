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
import org.openqa.selenium.bidi.HasBiDi;
import org.openqa.selenium.bidi.module.LogInspector;
import org.openqa.selenium.bidi.module.Network;
import org.openqa.selenium.bidi.network.Header;
import org.openqa.selenium.bidi.network.RequestData;
import org.openqa.selenium.bidi.network.ResponseData;

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
 * Genuinely CDP-only (not a TODO — these have no BiDi- or page-JS-safe equivalent):
 *   • RESPONSE_BODY — needs the Selenium BiDi network data-collector API, which is version-dependent;
 *     page JS cannot faithfully retrieve an already-consumed cross-origin body.
 *   • CPU_PROFILE + COVERAGE — Chrome-DevTools-only protocol features (Profiler, CSS rule-usage); there
 *     is no BiDi command and no page-JS API that samples the JS engine or reports precise coverage.
 *   • SCREENCAST (video) — BiDi has no continuous screencast; the only fallback is polling screenshots,
 *     which both contends heavily with the live test (unlike CDP's out-of-band frame push) and is a
 *     degraded approximation — deliberately not enabled.
 * All four are reported unsupported so recorders skip them cleanly under BiDi.
 *
 * The recorders are unchanged: they depend only on {@link BrowserControl}, which is exactly what D-2
 * was for — flipping {@code protocol=bidi} routes their calls here without touching them.
 *
 * NOTE (verification): the {@code org.openqa.selenium.bidi.*} module usage (network/console) is the
 * highest-CI-risk code in the trace package — newer than CDP, no in-repo pattern to mirror, and no jars
 * in the build container to compile against, so it is a best-effort match to the Selenium 4.48 BiDi API;
 * if a signature differs, the fix is localised to this file. The JS-based paths (metrics/vitals/snapshot)
 * carry none of that risk — they use only JavascriptExecutor. Everything is defensively wrapped so a
 * hiccup degrades to "no capture" rather than a crash.
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

    BiDiTransport(WebDriver driver) { this.driver = driver; }

    private boolean hasBiDi() { return driver instanceof HasBiDi; }

    @Override
    public boolean supports(Capability c) {
        switch (c) {
            case NETWORK:
            case CONSOLE:      return hasBiDi();
            case DOM_SNAPSHOT: return driver instanceof JavascriptExecutor;   // protocol-agnostic
            case SCREENSHOT:   return driver instanceof TakesScreenshot;      // protocol-agnostic
            case METRICS:      return driver instanceof JavascriptExecutor;   // performance/memory via read-only JS (in-band; see class doc)
            default:           return false;   // RESPONSE_BODY / CPU / COVERAGE / SCREENCAST: no BiDi/JS-safe equivalent (see class doc)
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
    }

    @Override
    public void disableNetwork() { this.networkOn = false; }

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
                Object r = ((JavascriptExecutor) driver).executeScript(VITALS_JS);
                return r == null ? null : String.valueOf(r);
            }
        } catch (Exception ignored) {}
        return null;
    }

    @Override
    public byte[] fetchBody(String requestId) { return null; }   // BiDi response-body capture deferred (needs Selenium BiDi network data-collector API)

    @Override
    public void close() {
        this.networkOn = false;
        this.consoleOn = false;
        this.netListener = null;
        this.conListener = null;
    }

    private static Map<String, String> headers(List<Header> hs) {
        Map<String, String> m = new LinkedHashMap<>();
        if (hs != null) {
            for (Header h : hs) {
                try {
                    m.put(h.getName(), h.getValue() != null ? h.getValue().getValue() : "");
                } catch (Exception ignored) {}
            }
        }
        return m;
    }
}
