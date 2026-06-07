package com.gatf.selenium.trace;

import java.nio.charset.StandardCharsets;
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
import org.openqa.selenium.devtools.DevTools;
import org.openqa.selenium.devtools.v150.log.Log;
import org.openqa.selenium.devtools.v150.network.Network;
import org.openqa.selenium.devtools.v150.network.model.Headers;
import org.openqa.selenium.devtools.v150.network.model.RequestId;
import org.openqa.selenium.devtools.v150.performance.Performance;
import org.openqa.selenium.devtools.v150.performance.model.Metric;
import org.openqa.selenium.devtools.v150.runtime.Runtime;
import org.openqa.selenium.devtools.v150.runtime.model.RemoteObject;

/**
 * CDP implementation of {@link BrowserControl} over a Selenium DevTools session
 * (devtools.v150). Every CDP call here mirrors a pattern already used in
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
    private final ConcurrentHashMap<String, RequestId> reqIds = new ConcurrentHashMap<>();

    private volatile boolean networkOn = false;
    private volatile boolean consoleOn = false;
    private volatile NetworkListener netListener;
    private volatile ConsoleListener conListener;

    private static final String VITALS_JS =
        "return JSON.stringify((function(){var o={};try{" +
        "var n=performance.getEntriesByType('navigation')[0];" +
        "if(n){o.ttfb=n.responseStart;o.domContentLoaded=n.domContentLoadedEventEnd;o.load=n.loadEventEnd;o.domInteractive=n.domInteractive;}" +
        "var p=performance.getEntriesByType('paint');" +
        "for(var i=0;i<p.length;i++){if(p[i].name==='first-contentful-paint')o.fcp=p[i].startTime;if(p[i].name==='first-paint')o.fp=p[i].startTime;}" +
        "var l=performance.getEntriesByType('largest-contentful-paint');" +
        "if(l&&l.length){o.lcp=l[l.length-1].startTime;}" +
        "}catch(e){o.error=String(e);}return o;})());";

    CdpTransport(WebDriver driver, DevTools devTools) {
        this.driver = driver;
        this.devTools = devTools;
    }

    @Override
    public boolean supports(Capability c) {
        switch (c) {
            case NETWORK:
            case CONSOLE:
            case METRICS:
            case RESPONSE_BODY: return devTools != null;
            case DOM_SNAPSHOT:  return driver instanceof JavascriptExecutor;
            case SCREENSHOT:    return driver instanceof TakesScreenshot;
            default:            return false;
        }
    }

    @Override
    public void enableNetwork(NetworkListener l) {
        if (devTools == null) return;
        this.netListener = l;
        this.networkOn = true;
        // v150 Network.enable takes 5 typed Optionals: maxTotalBufferSize, maxResourceBufferSize,
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
                Object r = ((JavascriptExecutor) driver).executeScript(VITALS_JS);
                return r == null ? null : String.valueOf(r);
            }
        } catch (Exception ignored) {}
        return null;
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
