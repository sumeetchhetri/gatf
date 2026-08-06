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
 * WebDriver BiDi implementation of {@link BrowserControl} (decision D-2). This is the first BiDi
 * parity increment: network request/response and console/log capture via the Selenium BiDi
 * modules, plus DOM snapshot and screenshot via the protocol-agnostic
 * {@link JavascriptExecutor}/{@link TakesScreenshot} paths (identical to the CDP transport, so no
 * BiDi API is involved for those). Response-body capture and metrics/cpu/coverage/video remain
 * CDP-only for now — reported unsupported so recorders skip them cleanly.
 *
 * The recorders are unchanged: they depend only on {@link BrowserControl}, which is exactly what
 * D-2 was for — flipping {@code protocol=bidi} routes their calls here without touching them.
 *
 * NOTE (verification): references the {@code org.openqa.selenium.bidi.*} modules, which are newer
 * than CDP and not used anywhere else in this codebase, so — unlike the CDP transport — there was
 * no in-repo pattern to mirror and no jars in the build container to compile against. The
 * network/console module usage is a best-effort match to the Selenium 4.48 BiDi API and is the
 * highest-CI-risk code in the trace package; if a signature differs, the fix is localized to this
 * file (or revert it to the delta-002 no-op skeleton — the default CDP path is unaffected).
 * Everything is defensively wrapped so a BiDi hiccup degrades to "no capture" rather than a crash.
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

    BiDiTransport(WebDriver driver) { this.driver = driver; }

    private boolean hasBiDi() { return driver instanceof HasBiDi; }

    @Override
    public boolean supports(Capability c) {
        switch (c) {
            case NETWORK:
            case CONSOLE:      return hasBiDi();
            case DOM_SNAPSHOT: return driver instanceof JavascriptExecutor;   // protocol-agnostic
            case SCREENSHOT:   return driver instanceof TakesScreenshot;      // protocol-agnostic
            default:           return false;   // RESPONSE_BODY / METRICS / CPU / COVERAGE / SCREENCAST: CDP-only for now
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
