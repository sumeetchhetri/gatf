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
package com.gatf.record;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chromium.HasCdp;


/**
 * Phase 11 (record mode) — a live "infer a test case from user actions" recording session.
 *
 * Launches a visible local Chrome, injects a recorder content-script via CDP
 * {@code Page.addScriptToEvaluateOnNewDocument} (so it re-installs on every navigation), then polls the
 * page for captured actions with {@code executeScript} and translates each into a seleasy line. Draining
 * over executeScript (rather than a CDP event listener) keeps this off the version-pinned devtools domain
 * classes and free of any page→server networking (no CORS / mixed-content concerns).
 *
 * The recorder JS captures click / submit / change(select,input,checkbox) and page navigations, computing
 * a robust selector: a unique semantic one first (testid → role+name → label, see LocatorRanking), else
 * id → name → css → xpath, mapped straight onto seleasy's selector prefixes.
 *
 * Pacing: every captured action carries a wall-clock stamp {@code t}; when the gap since the previous
 * emitted action exceeds {@link #SLEEP_THRESHOLD_MS} a {@code sleep <ms>} line is emitted before it
 * (seleasy sleep is milliseconds), rounded to 100 ms and capped at {@link #SLEEP_CAP_MS} so idle
 * think-time doesn't become multi-minute waits.
 *
 * CDP-only (Chrome) for now — {@code executeCdpCommand} is a Chromium capability.
 */
public class RecordSession {

    // the literal test-id argument inside RECORDER_JS; {@link #recorderJs()} swaps it for the project's testIdAttribute
    static final String CAND_CALL = "__gatfCandidates(el,'data-testid')";
    // Injected into every document; buffers captured actions on window.__gatfRecord__ for the server to drain.
    static final String RECORDER_JS =
        "(function(){"
        + "if(window.__gatfRecInit)return;window.__gatfRecInit=true;window.__gatfRecord__=[];"
        // M3: semantic candidates (testid / role+name / label, each with its match count) for LocatorRanking
        + com.gatf.selenium.SemanticLocators.RESOLVER_FN + com.gatf.selenium.SemanticLocators.CANDIDATES_FN
        + "function cand(el){return el&&el.nodeType===1?__gatfCandidates(el,'data-testid'):[];}"
        + "function uniq(id){try{return document.querySelectorAll('#'+CSS.escape(id)).length===1;}catch(e){return !!id;}}"
        + "function sel(el){"
        + "  if(!el||el.nodeType!==1)return null;"
        + "  if(el.id&&uniq(el.id))return{s:'id',v:el.id};"
        + "  var nm=el.getAttribute&&el.getAttribute('name');if(nm)return{s:'name',v:nm};"
        + "  var cn=(el.className&&typeof el.className==='string')?el.className.trim().split(/\\s+/).filter(Boolean):[];"
        + "  if(cn.length){var c=cn[0];try{if(document.querySelectorAll(el.tagName.toLowerCase()+'.'+CSS.escape(c)).length===1)return{s:'css',v:el.tagName.toLowerCase()+'.'+c};}catch(e){}}"
        + "  return{s:'xpath',v:xp(el)};"
        + "}"
        + "function xp(el){var p=[];while(el&&el.nodeType===1&&el!==document.documentElement){var i=1,s=el.previousElementSibling;while(s){if(s.tagName===el.tagName)i++;s=s.previousElementSibling;}p.unshift(el.tagName.toLowerCase()+'['+i+']');el=el.parentElement;}return'/html/'+p.join('/');}"
        + "function push(a){try{a.t=Date.now();window.__gatfRecord__.push(a);}catch(e){}}"
        + "push({type:'goto',url:location.href});"
        + "document.addEventListener('click',function(e){var t=(e.target.closest&&e.target.closest('a,button,input[type=button],input[type=submit],[role=button],[onclick]'))||e.target;var sub=(t.type==='submit')||(t.tagName==='BUTTON'&&(!t.type||t.type==='submit'));if(sub&&t.form)return;var s=sel(t);if(s)push({type:'click',sel:s,cand:cand(t)});},true);"
        + "document.addEventListener('submit',function(e){var s=sel(e.target);if(s)push({type:'submit',sel:s,cand:cand(e.target)});},true);"
        + "document.addEventListener('change',function(e){var el=e.target,s=sel(el);if(!s)return;var c=cand(el),tag=el.tagName.toLowerCase();if(tag==='select')push({type:'select',sel:s,cand:c,value:el.value});else if(el.type==='checkbox'||el.type==='radio')push({type:'check',sel:s,cand:c});else push({type:'type',sel:s,cand:c,value:(el.value==null?'':el.value)});},true);"
        + "})();";

    private static final String DRAIN_JS =
        "return window.__gatfRecord__?window.__gatfRecord__.splice(0):[];";

    private final String id;
    private final String startUrl;
    private volatile WebDriver driver;
    private volatile boolean running = false;
    private volatile String error;
    private Thread poller;

    // emitted seleasy lines, in order (thread-safe: the poller writes, HTTP threads read)
    private final List<String> lines = new CopyOnWriteArrayList<String>();
    private String lastLine = null;
    private long lastTs = -1;

    static final long SLEEP_THRESHOLD_MS = 100;   // original FgRecorder threshold
    static final long SLEEP_CAP_MS = 10000;

    private final String browser;   // G9: chrome | firefox | edge
    private final String recorderJs;   // RECORDER_JS for this project's testIdAttribute

    // Δ148 live pick locator: opt-in, only while recording. HTTP threads flip the flags; only the poller touches the driver.
    private final String pickArmJs;
    private volatile boolean pickArmed = false;
    private volatile boolean pickOffPending = false;
    private volatile Map<String, Object> lastPick = null;
    private int pickSeq = 0;

    public RecordSession(String id, String startUrl) {
        this(id, startUrl, "chrome");
    }

    public RecordSession(String id, String startUrl, String browser) {
        this(id, startUrl, browser, null);
    }

    /** M3: {@code testIdAttribute} (config {@code testIdAttribute}; blank = data-testid) feeds the testid@ candidates. */
    public RecordSession(String id, String startUrl, String browser, String testIdAttribute) {
        this.id = id;
        String tid = testIdAttribute == null || testIdAttribute.trim().isEmpty() ? null : testIdAttribute.trim();
        this.recorderJs = tid == null ? RECORDER_JS : RECORDER_JS.replace(CAND_CALL, "__gatfCandidates(el," + jsString(tid) + ")");
        this.pickArmJs = PickLocator.armJs(jsString(tid == null ? "data-testid" : tid));
        this.startUrl = (startUrl == null || startUrl.trim().isEmpty()) ? "about:blank" : startUrl.trim();
        String b = browser == null ? "" : browser.trim().toLowerCase(java.util.Locale.ROOT);
        this.browser = b.equals("firefox") || b.equals("edge") ? b : "chrome";
    }

    public String getBrowser() { return browser; }

    String recorderJs() { return recorderJs; }

    static String jsString(String v) {
        StringBuilder b = new StringBuilder("\"");
        for (char c : v.toCharArray()) {
            if (c == '"' || c == '\\') b.append('\\').append(c);
            else if (c < 0x20 || c == 0x2028 || c == 0x2029 || c == '<') b.append(String.format("\\u%04x", (int) c));
            else b.append(c);
        }
        return b.append('"').toString();
    }

    // visible local browser (Selenium Manager provisions driver + browser); Firefox gets a BiDi socket
    private WebDriver newDriver() {
        if ("firefox".equals(browser)) {
            return new org.openqa.selenium.firefox.FirefoxDriver(new org.openqa.selenium.firefox.FirefoxOptions().enableBiDi());
        }
        if ("edge".equals(browser)) return new org.openqa.selenium.edge.EdgeDriver();
        return new org.openqa.selenium.chrome.ChromeDriver();
    }

    public String getId() { return id; }
    public boolean isRunning() { return running; }
    public String getError() { return error; }

    public synchronized void start() throws Exception {
        driver = newDriver();
        // install the recorder for every document (survives navigations), then load the start page:
        // CDP on Chromium, a BiDi preload script elsewhere; the poll loop re-injects as a universal fallback
        if (driver instanceof HasCdp) {
            ((HasCdp) driver).executeCdpCommand("Page.addScriptToEvaluateOnNewDocument",
                    Collections.<String, Object>singletonMap("source", recorderJs));
        } else if (com.gatf.selenium.trace.BidiRaw.available(driver)) {
            try {
                new org.openqa.selenium.bidi.module.Script(driver).addPreloadScript("() => {" + recorderJs + "}");
            } catch (Exception ignore) { /* poll-loop re-injection still covers it */ }
        }
        if (!"about:blank".equals(startUrl)) driver.get(startUrl);
        // belt-and-braces: also inject into the already-loaded document (idempotent via the guard)
        try { ((JavascriptExecutor) driver).executeScript(recorderJs); } catch (Exception ignore) {}
        running = true;
        poller = new Thread(new Runnable() {
            public void run() { pollLoop(); }
        }, "gatf-record-" + id);
        poller.setDaemon(true);
        poller.start();
    }

    @SuppressWarnings("unchecked")
    private void pollLoop() {
        while (running) {
            try {
                Thread.sleep(400);
                if (!running) break;
                // universal fallback (any WebDriver): (re)install into a document that lacks the recorder —
                // guarded by __gatfRecInit, so it is a no-op wherever CDP/BiDi preload already did it
                ((JavascriptExecutor) driver).executeScript(recorderJs);
                Object raw = ((JavascriptExecutor) driver).executeScript(DRAIN_JS);
                if (raw instanceof List) {
                    for (Object o : (List<Object>) raw) {
                        if (o instanceof Map) ingest((Map<String, Object>) o);
                    }
                }
                pickTick();
            } catch (org.openqa.selenium.WebDriverException nav) {
                // page navigating or window closed by the user — if the window is gone, stop
                if (isWindowGone()) { running = false; break; }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                // transient; keep polling
            }
        }
    }

    // Δ148: drain picks first, then (re)arm into the current document, or take the picker out of it once disarmed
    @SuppressWarnings("unchecked")
    private void pickTick() {
        if (!pickArmed && !pickOffPending) return;
        JavascriptExecutor js = (JavascriptExecutor) driver;
        Object raw = js.executeScript(PickLocator.DRAIN_JS);
        if (raw instanceof List) {
            for (Object o : (List<Object>) raw) {
                if (o instanceof Map) ingestPick((Map<String, Object>) o);
            }
        }
        if (pickArmed) {
            js.executeScript(pickArmJs);   // idempotent; re-installs after a navigation
        } else if (pickOffPending) {
            js.executeScript(PickLocator.OFF_JS);
            pickOffPending = false;
        }
    }

    /** Δ148: arm (or disarm) the live picker; false when the session is not recording. A pick or Escape disarms it. */
    public synchronized boolean armPicker(boolean on) {
        if (on && !running) return false;
        if (on) {
            pickArmed = true;
            pickOffPending = false;
        } else if (pickArmed || pickOffPending) {
            pickArmed = false;
            pickOffPending = true;
        }
        return true;
    }

    public boolean isPickerArmed() { return pickArmed && running; }

    /** Δ148: the latest pick, {@code {seq, cancelled} + PickLocator.rank(…)}, or null before the first one. */
    public Map<String, Object> lastPick() { return lastPick; }

    // one drained picker event: a pick is ranked (testid → role → label → strict fallback); either kind disarms
    synchronized void ingestPick(Map<String, Object> p) {
        Map<String, Object> out = new java.util.LinkedHashMap<String, Object>();
        out.put("seq", ++pickSeq);
        boolean cancel = "cancel".equals(str(p.get("type")));
        out.put("cancelled", cancel);
        if (!cancel) {
            Object c = p.get("cand");
            Object sel = p.get("sel");
            out.putAll(PickLocator.rank(c instanceof List ? (List<?>) c : null, sel instanceof Map ? (Map<?, ?>) sel : null));
        }
        lastPick = out;
        if (pickArmed) {
            pickArmed = false;
            pickOffPending = true;
        }
    }

    private boolean isWindowGone() {
        try {
            return driver.getWindowHandles().isEmpty();
        } catch (Exception e) {
            return true;
        }
    }

    // map one captured action, emitting a pacing sleep before it when the gap warrants
    synchronized void ingest(Map<String, Object> a) {
        String line = mapAction(a);
        if (line == null) return;
        // collapse consecutive duplicates (e.g. repeated change events, re-emitted goto after re-injection)
        if (line.equals(lastLine)) return;
        long ts = a.get("t") instanceof Number ? ((Number) a.get("t")).longValue() : -1;
        if (ts > 0 && lastTs > 0) {
            long gap = ts - lastTs;
            if (gap > SLEEP_THRESHOLD_MS) {
                long ms = Math.min(SLEEP_CAP_MS, Math.max(100, Math.round(gap / 100.0) * 100));
                lines.add("sleep " + ms);
            }
        }
        if (ts > 0) lastTs = ts;
        lines.add(line);
        lastLine = line;
    }

    // ---- action → seleasy -----------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private String mapAction(Map<String, Object> a) {
        String type = str(a.get("type"));
        if ("goto".equals(type)) {
            String url = str(a.get("url"));
            return url.isEmpty() || "about:blank".equals(url) ? null : "goto " + url;
        }
        Map<String, Object> sel = a.get("sel") instanceof Map ? (Map<String, Object>) a.get("sel") : null;
        // M3: testid → role → label when unique in the page, else the legacy id → name → css → xpath
        String selector = com.gatf.selenium.LocatorRanking.choose(a.get("cand") instanceof List ? (List<Object>) a.get("cand") : null, PickLocator.selector(sel));
        if (selector == null) return null;
        if ("click".equals(type)) return PickLocator.line(selector);   // Δ148: the picker pastes this same step
        if ("submit".equals(type)) return "submit " + selector;
        if ("check".equals(type)) return "click " + selector;
        if ("type".equals(type)) return "type \"" + esc(str(a.get("value"))) + "\" " + selector;
        if ("select".equals(type)) {
            String v = str(a.get("value"));
            return v.isEmpty() ? "select index@0 " + selector : "select value@\"" + esc(v) + "\" " + selector;
        }
        return null;
    }

    // ---- lifecycle / output ---------------------------------------------------------------------

    public List<String> currentLines() {
        return new ArrayList<String>(lines);
    }

    public synchronized void stop() {
        running = false;
        if (poller != null) poller.interrupt();
        boolean pickerOut = pickArmed || pickOffPending;
        pickArmed = false;
        pickOffPending = false;
        try {
            // Δ148: no picker listener or outline stays on the page, even when the browser is left open by a failed quit
            if (pickerOut && driver != null) ((JavascriptExecutor) driver).executeScript(PickLocator.OFF_JS);
        } catch (Exception ignore) {
        }
        try {
            // final drain before closing so the last actions aren't lost
            if (driver != null) {
                Object raw = ((JavascriptExecutor) driver).executeScript(DRAIN_JS);
                if (raw instanceof List) {
                    for (Object o : (List<?>) raw) {
                        if (o instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> m = (Map<String, Object>) o;
                            ingest(m);
                        }
                    }
                }
            }
        } catch (Exception ignore) {
        } finally {
            try { if (driver != null) driver.quit(); } catch (Exception ignore) {}
            driver = null;
        }
    }

    public String toSel() {
        StringBuilder sb = new StringBuilder();
        sb.append("// Recorded with GATF record mode — review selectors and add assertions.\n");
        sb.append("open chrome\n").append("maximize\n\n");
        for (String l : lines) sb.append(l).append("\n");
        return sb.toString();
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static String esc(String v) {
        return v == null ? "" : v.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ");
    }
}
