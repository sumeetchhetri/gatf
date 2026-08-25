/*
    Copyright 2013-2019, Sumeet Chhetri

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
 * a robust selector (id → name → css → xpath) that maps straight onto seleasy's selector prefixes.
 *
 * CDP-only (Chrome) for now — {@code executeCdpCommand} is a Chromium capability. The action→seleasy
 * mapping is mirrored by tools/record/actions_to_seleasy.py, verified lint-clean.
 */
public class RecordSession {

    // Injected into every document; buffers captured actions on window.__gatfRecord__ for the server to drain.
    static final String RECORDER_JS =
        "(function(){"
        + "if(window.__gatfRecInit)return;window.__gatfRecInit=true;window.__gatfRecord__=[];"
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
        + "function push(a){try{window.__gatfRecord__.push(a);}catch(e){}}"
        + "push({type:'goto',url:location.href});"
        + "document.addEventListener('click',function(e){var t=(e.target.closest&&e.target.closest('a,button,input[type=button],input[type=submit],[role=button],[onclick]'))||e.target;var sub=(t.type==='submit')||(t.tagName==='BUTTON'&&(!t.type||t.type==='submit'));if(sub&&t.form)return;var s=sel(t);if(s)push({type:'click',sel:s});},true);"
        + "document.addEventListener('submit',function(e){var s=sel(e.target);if(s)push({type:'submit',sel:s});},true);"
        + "document.addEventListener('change',function(e){var el=e.target,s=sel(el);if(!s)return;var tag=el.tagName.toLowerCase();if(tag==='select')push({type:'select',sel:s,value:el.value});else if(el.type==='checkbox'||el.type==='radio')push({type:'check',sel:s});else push({type:'type',sel:s,value:(el.value==null?'':el.value)});},true);"
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

    public RecordSession(String id, String startUrl) {
        this.id = id;
        this.startUrl = (startUrl == null || startUrl.trim().isEmpty()) ? "about:blank" : startUrl.trim();
    }

    public String getId() { return id; }
    public boolean isRunning() { return running; }
    public String getError() { return error; }

    public synchronized void start() throws Exception {
        driver = new org.openqa.selenium.chrome.ChromeDriver(); // visible local Chrome (Selenium Manager provisions driver + browser)
        // install the recorder for every document (survives navigations), then load the start page
        ((HasCdp) driver).executeCdpCommand("Page.addScriptToEvaluateOnNewDocument",
                Collections.<String, Object>singletonMap("source", RECORDER_JS));
        if (!"about:blank".equals(startUrl)) driver.get(startUrl);
        // belt-and-braces: also inject into the already-loaded document (idempotent via the guard)
        try { ((JavascriptExecutor) driver).executeScript(RECORDER_JS); } catch (Exception ignore) {}
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
                Object raw = ((JavascriptExecutor) driver).executeScript(DRAIN_JS);
                if (raw instanceof List) {
                    for (Object o : (List<Object>) raw) {
                        if (o instanceof Map) addLine(mapAction((Map<String, Object>) o));
                    }
                }
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

    private boolean isWindowGone() {
        try {
            return driver.getWindowHandles().isEmpty();
        } catch (Exception e) {
            return true;
        }
    }

    private void addLine(String line) {
        if (line == null) return;
        // collapse consecutive duplicates (e.g. repeated change events, re-emitted goto after re-injection)
        if (line.equals(lastLine)) return;
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
        String selector = selector(sel);
        if (selector == null) return null;
        if ("click".equals(type)) return "click " + selector;
        if ("submit".equals(type)) return "submit " + selector;
        if ("check".equals(type)) return "click " + selector;
        if ("type".equals(type)) return "type \"" + esc(str(a.get("value"))) + "\" " + selector;
        if ("select".equals(type)) {
            String v = str(a.get("value"));
            return v.isEmpty() ? "select index@0 " + selector : "select value@\"" + esc(v) + "\" " + selector;
        }
        return null;
    }

    // {s:strategy, v:value} → seleasy selector, choosing a quote wrapper that avoids collisions
    private String selector(Map<String, Object> sel) {
        if (sel == null) return null;
        String s = str(sel.get("s"));
        String v = str(sel.get("v"));
        if (v.isEmpty()) return null;
        if ("id".equals(s)) return "id@" + simpleOrQuoted(v);
        if ("name".equals(s)) return "name@" + simpleOrQuoted(v);
        if ("css".equals(s)) return "css@" + q(v);
        return "xpath@" + xp(v);
    }

    private String simpleOrQuoted(String v) {
        return v.matches("[A-Za-z0-9_\\-:.]+") ? v : q(v);
    }

    private String q(String v) {
        if (v.indexOf('"') >= 0 && v.indexOf('\'') < 0) return "'" + v + "'";
        return "\"" + v.replace("\"", "\\\"") + "\"";
    }

    private String xp(String x) {
        String t = x.trim();
        if (t.indexOf('"') >= 0 && t.indexOf('\'') < 0) return "'" + t + "'";
        return "\"" + t.replace("\"", "\\\"") + "\"";
    }

    // ---- lifecycle / output ---------------------------------------------------------------------

    public List<String> currentLines() {
        return new ArrayList<String>(lines);
    }

    public synchronized void stop() {
        running = false;
        if (poller != null) poller.interrupt();
        try {
            // final drain before closing so the last actions aren't lost
            if (driver != null) {
                Object raw = ((JavascriptExecutor) driver).executeScript(DRAIN_JS);
                if (raw instanceof List) {
                    for (Object o : (List<?>) raw) {
                        if (o instanceof Map) {
                            @SuppressWarnings("unchecked")
                            String line = mapAction((Map<String, Object>) o);
                            addLine(line);
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
