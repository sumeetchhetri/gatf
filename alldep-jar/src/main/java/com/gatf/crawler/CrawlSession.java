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
package com.gatf.crawler;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One background SPA crawl, polled by the /crawl handler. */
public class CrawlSession {

    public interface BrowserFactory {
        SpaCrawler.Browser open(CrawlSpec spec) throws Exception;
    }

    private static final int LOG_CAP = 300;

    public final String id;
    public final CrawlSpec spec;
    private final Deque<String> log = new ArrayDeque<String>();
    private volatile boolean running;
    private volatile String error;
    private volatile SpaCrawler crawler;
    private volatile String content;
    private volatile List<Map<String, Object>> plan;
    private final long started = System.currentTimeMillis();
    private volatile long finished;

    public CrawlSession(String id, CrawlSpec spec) {
        this.id = id;
        this.spec = spec;
    }

    public synchronized void start(final BrowserFactory factory) {
        running = true;
        Thread t = new Thread(new Runnable() {
            public void run() {
                SpaCrawler.Browser b = null;
                try {
                    log("opening browser" + (spec.isHeadless() ? " (headless)" : ""));
                    b = factory.open(spec);
                    crawler = new SpaCrawler(spec, b, CrawlSession.this::log);
                    List<SpaCrawler.PageInfo> pages = crawler.crawl();
                    content = crawler.emitSeleasy(pages);
                    plan = SpaCrawler.coveragePlan(pages);
                    log("done: " + pages.size() + " page(s)");
                } catch (Throwable e) {
                    error = e.getMessage() == null ? e.toString() : e.getMessage();
                    log("ERROR " + error);
                } finally {
                    if (b != null) b.quit();
                    running = false;
                    finished = System.currentTimeMillis();
                }
            }
        }, "gatf-crawl-" + id);
        t.setDaemon(true);
        t.start();
    }

    public void stop() {
        SpaCrawler c = crawler;
        if (c != null) c.stop();
        log("stop requested");
    }

    public boolean isRunning() { return running; }
    public long finishedAt() { return finished; }
    public String content() { return content; }
    public List<Map<String, Object>> plan() { return plan; }

    synchronized void log(String line) {
        log.addLast(line);
        while (log.size() > LOG_CAP) log.removeFirst();
    }

    public synchronized Map<String, Object> status() {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        SpaCrawler c = crawler;
        List<Map<String, Object>> pages = new ArrayList<Map<String, Object>>();
        if (c != null) {
            for (SpaCrawler.PageInfo p : c.pages()) {
                Map<String, Object> pm = new LinkedHashMap<String, Object>();
                pm.put("url", p.url);
                pm.put("title", p.title);
                pm.put("depth", p.depth);
                pm.put("forms", p.forms.size());
                pm.put("fields", p.fieldCount());
                pm.put("buttons", p.buttons.size());
                if (p.error != null) pm.put("error", p.error);
                pages.add(pm);
            }
        }
        m.put("running", running);
        m.put("done", !running && content != null);
        m.put("visited", pages.size());
        m.put("maxPages", spec.getMaxPages());
        m.put("queued", c == null ? 0 : c.queued());
        m.put("current", c == null ? null : c.current());
        m.put("elapsedMs", (finished > 0 ? finished : System.currentTimeMillis()) - started);
        m.put("pages", pages);
        m.put("log", new ArrayList<String>(log));
        if (c != null && c.loginWarning() != null) m.put("loginWarning", c.loginWarning());
        if (error != null) m.put("error", error);
        return m;
    }
}
