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
package com.gatf.ui;

import java.io.File;
import java.io.InputStream;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.ws.rs.core.MediaType;

import org.apache.commons.io.FileUtils;
import org.glassfish.grizzly.http.Method;
import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.http.util.HttpStatus;

import com.gatf.crawler.CrawlSession;
import com.gatf.crawler.CrawlSpec;
import com.gatf.crawler.SeleniumCrawlBrowser;
import com.gatf.crawler.SpaCrawler;
import com.gatf.executor.core.AcceptanceTestContext;
import com.gatf.executor.core.GatfExecutorConfig;
import com.gatf.executor.core.WorkflowContextHandler;
import com.gatf.selenium.Command;

/**
 * G3 — browser-driven (SPA-aware) crawl from the React console.
 *
 *   POST /crawl?action=start      body {seed, maxPages, maxDepth, loginUrl, username, password, userSel,
 *                                       passSel, submitSel, submitForms, exclude, headless, suiteName}
 *                                  -> { sessionId }
 *   GET  /crawl?action=status&sessionId=  -> { running, done, visited, queued, current, pages[], log[], ... }
 *   POST /crawl?action=stop&sessionId=
 *   GET  /crawl?action=result&sessionId=  -> { content, coverage[] }   (after done)
 *   POST /crawl?action=save&sessionId=&fileName=  -> { savedPath, coveragePath, validation }
 *
 * The password lives only in the in-memory spec for the crawl; it is never echoed or written (the suite
 * uses ${username}/${password}). Additive: registered by GatfConfigToolUtil at /crawl.
 */
public class GatfCrawlHandler extends HttpHandler {

    private static final Map<String, CrawlSession> SESSIONS = new ConcurrentHashMap<String, CrawlSession>();
    private static final int MAX_RUNNING = 2;
    private static final long KEEP_FINISHED_MS = 60 * 60 * 1000L;

    private final GatfConfigToolMojoInt mojo;

    public GatfCrawlHandler(GatfConfigToolMojoInt mojo) {
        this.mojo = mojo;
    }

    @Override
    public void service(Request request, Response response) throws Exception {
        AcceptanceTestContext.checkAuthAndSetCors(mojo, request, response);
        if (response.getStatus() == 401) return;
        response.setHeader("Cache-Control", "no-cache, no-store");
        String action = request.getParameter("action");
        String sid = request.getParameter("sessionId");
        try {
            if ("start".equals(action)) {
                if (!isWrite(request)) { json(response, HttpStatus.METHOD_NOT_ALLOWED_405, err("use POST to start")); return; }
                evictOld();
                int running = 0;
                for (CrawlSession s : SESSIONS.values()) if (s.isRunning()) running++;
                if (running >= MAX_RUNNING) { json(response, HttpStatus.SERVICE_UNAVAILABLE_503, err("too many crawls running (max " + MAX_RUNNING + ")")); return; }
                CrawlSpec spec = specFrom(readBody(request));
                String id = UUID.randomUUID().toString();
                CrawlSession s = new CrawlSession(id, spec);
                SESSIONS.put(id, s);
                s.start(new CrawlSession.BrowserFactory() {
                    public SpaCrawler.Browser open(CrawlSpec sp) throws Exception {
                        return SeleniumCrawlBrowser.create(sp.isHeadless(), sp.getBrowser());
                    }
                });
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("sessionId", id);
                json(response, HttpStatus.OK_200, out);
                return;
            }
            CrawlSession s = sid == null ? null : SESSIONS.get(sid);
            if (s == null) { json(response, HttpStatus.NOT_FOUND_404, err("no such crawl session")); return; }
            if ("status".equals(action)) {
                json(response, HttpStatus.OK_200, s.status());
            } else if ("stop".equals(action)) {
                s.stop();
                json(response, HttpStatus.OK_200, s.status());
            } else if ("result".equals(action)) {
                if (s.content() == null) { json(response, HttpStatus.CONFLICT_409, err(s.isRunning() ? "crawl still running" : "crawl produced no result")); return; }
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("content", s.content());
                out.put("coverage", s.plan());
                json(response, HttpStatus.OK_200, out);
            } else if ("save".equals(action)) {
                if (!isWrite(request)) { json(response, HttpStatus.METHOD_NOT_ALLOWED_405, err("use POST to save")); return; }
                if (s.content() == null) { json(response, HttpStatus.CONFLICT_409, err("nothing to save yet")); return; }
                String name = safeName(request.getParameter("fileName"), s.spec.getSuiteName());
                GatfExecutorConfig cfg = GatfConfigToolUtil.getGatfExecutorConfig(mojo, null, request.getParameter("config"));
                File dir = testDir(cfg);
                File sel = new File(dir, name + ".sel");
                File cov = new File(dir, name + "-coverage.json");
                FileUtils.writeStringToFile(sel, s.content(), "UTF-8");
                FileUtils.writeStringToFile(cov, WorkflowContextHandler.OM.writerWithDefaultPrettyPrinter().writeValueAsString(s.plan()), "UTF-8");
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                String rel = cfg.getTestCaseDir() != null ? cfg.getTestCaseDir() + "/" + sel.getName() : sel.getName();
                out.put("savedPath", sel.getName());
                out.put("coveragePath", cov.getName());
                out.put("validation", validate(cfg, rel));
                json(response, HttpStatus.OK_200, out);
            } else {
                json(response, HttpStatus.BAD_REQUEST_400, err("unknown action '" + action + "'"));
            }
        } catch (IllegalArgumentException e) {
            json(response, HttpStatus.BAD_REQUEST_400, err(e.getMessage()));
        } catch (Exception e) {
            json(response, HttpStatus.INTERNAL_SERVER_ERROR_500, err(e.getMessage() == null ? e.toString() : e.getMessage()));
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readBody(Request request) throws Exception {
        try (InputStream in = request.getInputStream()) {
            byte[] b = in.readAllBytes();
            if (b.length == 0) return new LinkedHashMap<String, Object>();
            return WorkflowContextHandler.OM.readValue(b, Map.class);
        }
    }

    static CrawlSpec specFrom(Map<String, Object> b) {
        CrawlSpec s = new CrawlSpec();
        String seed = str(b.get("seed"));
        if (seed == null || !seed.matches("(?i)https?://.+")) throw new IllegalArgumentException("seed must be an http(s) URL");
        s.setSeed(seed);
        s.setMaxPages(clamp(num(b.get("maxPages"), 25), 1, 500));
        s.setMaxDepth(clamp(num(b.get("maxDepth"), 3), 0, 10));
        s.setSubmitForms(Boolean.TRUE.equals(b.get("submitForms")));
        s.setHeadless(!Boolean.FALSE.equals(b.get("headless")));
        s.setBrowser(str(b.get("browser")));
        s.setExclude(str(b.get("exclude")));
        if (b.get("suiteName") != null) s.setSuiteName(str(b.get("suiteName")));
        s.setLoginUrl(str(b.get("loginUrl")));
        s.setUsername(str(b.get("username")));
        s.setPassword(str(b.get("password")));
        s.setUserSel(str(b.get("userSel")));
        s.setPassSel(str(b.get("passSel")));
        s.setSubmitSel(str(b.get("submitSel")));
        if (s.getExclude() != null) java.util.regex.Pattern.compile(s.getExclude()); // fail fast on a bad regex
        return s;
    }

    private void evictOld() {
        long now = System.currentTimeMillis();
        for (Iterator<CrawlSession> it = SESSIONS.values().iterator(); it.hasNext();) {
            CrawlSession s = it.next();
            if (!s.isRunning() && s.finishedAt() > 0 && now - s.finishedAt() > KEEP_FINISHED_MS) it.remove();
        }
    }

    private File testDir(GatfExecutorConfig cfg) {
        String base = cfg.getTestCasesBasePath() == null ? mojo.getRootDir() : cfg.getTestCasesBasePath();
        File d = cfg.getTestCaseDir() != null ? new File(base, cfg.getTestCaseDir()) : new File(base);
        d.mkdirs();
        return d;
    }

    private String validate(GatfExecutorConfig cfg, String rel) {
        try {
            String base = cfg.getTestCasesBasePath() == null ? mojo.getRootDir() : cfg.getTestCasesBasePath();
            String cfgName = new File(base, "gatf-config.json").exists() ? "gatf-config.json" : "gatf-config.xml";
            return Command.validateSel(new String[] { "-validate-sel", rel, cfgName, base }, null, false);
        } catch (Throwable t) {
            return "validation unavailable: " + (t.getMessage() == null ? t.toString() : t.getMessage());
        }
    }

    private static String safeName(String n, String dflt) {
        String b = (n == null || n.trim().isEmpty()) ? (dflt == null ? "crawl-suite" : dflt) : n.trim();
        return b.replaceAll("\\.sel$", "").replaceAll("[^A-Za-z0-9_\\-]", "_");
    }

    private static boolean isWrite(Request r) {
        return r.getMethod().equals(Method.POST) || r.getMethod().equals(Method.PUT);
    }

    private static String str(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? null : s;
    }

    private static int num(Object o, int d) {
        try {
            return o instanceof Number ? ((Number) o).intValue() : Integer.parseInt(String.valueOf(o).trim());
        } catch (Exception e) {
            return d;
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static Map<String, Object> err(String m) {
        Map<String, Object> e = new LinkedHashMap<String, Object>();
        e.put("error", m);
        return e;
    }

    private static void json(Response response, HttpStatus st, Object body) throws Exception {
        String s = WorkflowContextHandler.OM.writeValueAsString(body);
        byte[] b = s.getBytes("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON + "; charset=utf-8");
        response.setContentLength(b.length);
        response.getWriter().write(s);
        response.setStatus(st);
    }
}
