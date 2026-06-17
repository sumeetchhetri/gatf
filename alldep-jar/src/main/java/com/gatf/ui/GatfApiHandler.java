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
package com.gatf.ui;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.MediaType;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.glassfish.grizzly.http.Method;
import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.http.util.HttpStatus;

import com.gatf.executor.core.AcceptanceTestContext;
import com.gatf.executor.core.GatfExecutorConfig;
import com.gatf.executor.core.WorkflowContextHandler;

/**
 * Phase 3.x — a small, read-only JSON API that the embedded React shell (served at /app) consumes
 * to strangle the classic reports / config / test-file surfaces. It is purely additive: it does not
 * touch any of the existing handlers, and it performs no writes (editing/saving still flow through
 * the legacy config-tool at "/"). All responses are JSON.
 *
 *   GET /api            | /api/health   -> { ok, service, api, version }
 *   GET /api/reports                    -> { dir, reports:[{name,url,size,modified}], traces:[...] }
 *   GET /api/config                     -> { testDir, outDir, config:{...effective GatfExecutorConfig} }
 *   GET /api/testfiles                  -> { dir, files:[{name,path,size,modified,kind}] }
 *   GET /api/testfile?path=<relative>   -> { path, kind, size, truncated, content }
 */
public class GatfApiHandler extends HttpHandler {

    private GatfConfigToolMojoInt mojo;

    // read cap for the test-file viewer (Monaco editor + full content lands in Phase 4)
    private static final long MAX_FILE_BYTES = 512 * 1024;

    public GatfApiHandler(GatfConfigToolMojoInt mojo) {
        super();
        this.mojo = mojo;
    }

    @Override
    public void service(Request request, Response response) throws Exception {
        AcceptanceTestContext.checkAuthAndSetCors(mojo, request, response);
        if (response.getStatus() == 401) return;
        response.setHeader("Cache-Control", "no-cache, no-store");

        if (!request.getMethod().equals(Method.GET)) {
            writeJson(response, HttpStatus.METHOD_NOT_ALLOWED_405, err("only GET is supported"));
            return;
        }

        String sub = resource(request.getRequestURI());
        try {
            if (sub.isEmpty() || sub.equals("health")) {
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("ok", true);
                out.put("service", "gatf");
                out.put("api", "v1");
                writeJson(response, HttpStatus.OK_200, out);
            } else if (sub.equals("reports")) {
                reports(response);
            } else if (sub.equals("config")) {
                config(response);
            } else if (sub.equals("testfiles")) {
                testfiles(response);
            } else if (sub.equals("testfile")) {
                testfile(request, response);
            } else {
                writeJson(response, HttpStatus.NOT_FOUND_404, err("unknown resource: " + sub));
            }
        } catch (Exception e) {
            GatfConfigToolUtil.handleErrorJson(e, response, HttpStatus.INTERNAL_SERVER_ERROR_500);
        }
    }

    // ---- resources -------------------------------------------------------

    private void reports(Response response) throws Exception {
        GatfExecutorConfig cfg = GatfConfigToolUtil.getGatfExecutorConfig(mojo, null);
        String base = cfg.getOutFilesBasePath() == null ? mojo.getRootDir() : cfg.getOutFilesBasePath();
        String dir = cfg.getOutFilesDir() != null ? base + File.separator + cfg.getOutFilesDir() : base;

        List<Map<String, Object>> reports = new ArrayList<>();
        List<Map<String, Object>> traces = new ArrayList<>();
        File d = new File(dir);
        File[] files = d.listFiles();
        if (files != null) {
            for (File f : files) {
                if (!f.isFile()) continue;
                String ln = f.getName().toLowerCase();
                if (ln.endsWith(".html")) {
                    Map<String, Object> a = artifact(f);
                    File sc = new File(f.getParentFile(), f.getName().substring(0, f.getName().length() - 5) + ".data.json");
                    if (sc.isFile()) a.put("dataUrl", "/reports/" + sc.getName());
                    reports.add(a);
                } else if (ln.endsWith("-trace.zip") || ln.endsWith(".zip")) {
                    traces.add(artifact(f));
                }
            }
        }
        reports.sort(byModifiedDesc());
        traces.sort(byModifiedDesc());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("dir", dir);
        out.put("reports", reports);
        out.put("traces", traces);
        writeJson(response, HttpStatus.OK_200, out);
    }

    private void config(Response response) throws Exception {
        GatfExecutorConfig cfg = GatfConfigToolUtil.getGatfExecutorConfig(mojo, null);
        String tbase = cfg.getTestCasesBasePath() == null ? mojo.getRootDir() : cfg.getTestCasesBasePath();
        String tdir = cfg.getTestCaseDir() != null ? tbase + File.separator + cfg.getTestCaseDir() : tbase;
        String obase = cfg.getOutFilesBasePath() == null ? mojo.getRootDir() : cfg.getOutFilesBasePath();
        String odir = cfg.getOutFilesDir() != null ? obase + File.separator + cfg.getOutFilesDir() : obase;

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("testDir", tdir);
        out.put("outDir", odir);
        out.put("config", cfg);
        writeJson(response, HttpStatus.OK_200, out);
    }

    private void testfiles(Response response) throws Exception {
        GatfExecutorConfig cfg = GatfConfigToolUtil.getGatfExecutorConfig(mojo, null);
        String tbase = cfg.getTestCasesBasePath() == null ? mojo.getRootDir() : cfg.getTestCasesBasePath();
        String tdir = cfg.getTestCaseDir() != null ? tbase + File.separator + cfg.getTestCaseDir() : tbase;

        List<Map<String, Object>> out = new ArrayList<>();
        File d = new File(tdir);
        File[] files = d.listFiles();
        if (files != null) {
            for (File f : files) {
                if (!f.isFile()) continue;
                String ln = f.getName().toLowerCase();
                if (!ln.endsWith(".sel") && !ln.endsWith(".xml")) continue;
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("name", f.getName());
                m.put("path", f.getName());
                m.put("size", f.length());
                m.put("modified", f.lastModified());
                m.put("kind", kind(f.getName()));
                out.add(m);
            }
        }
        out.sort(Comparator.comparing(a -> String.valueOf(a.get("name"))));

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("dir", tdir);
        resp.put("files", out);
        writeJson(response, HttpStatus.OK_200, resp);
    }

    private void testfile(Request request, Response response) throws Exception {
        String path = request.getParameter("path");
        if (StringUtils.isBlank(path)) {
            writeJson(response, HttpStatus.BAD_REQUEST_400, err("path is required"));
            return;
        }
        GatfExecutorConfig cfg = GatfConfigToolUtil.getGatfExecutorConfig(mojo, null);
        String tbase = cfg.getTestCasesBasePath() == null ? mojo.getRootDir() : cfg.getTestCasesBasePath();
        String tdir = cfg.getTestCaseDir() != null ? tbase + File.separator + cfg.getTestCaseDir() : tbase;

        File baseDir = new File(tdir).getCanonicalFile();
        File target = new File(baseDir, path).getCanonicalFile();
        // path-traversal guard: the resolved file must live under the test dir
        if (!target.getPath().equals(baseDir.getPath()) && !target.getPath().startsWith(baseDir.getPath() + File.separator)) {
            writeJson(response, HttpStatus.BAD_REQUEST_400, err("path escapes the test directory"));
            return;
        }
        if (!target.exists() || !target.isFile()) {
            writeJson(response, HttpStatus.NOT_FOUND_404, err("no such file"));
            return;
        }
        long size = target.length();
        boolean truncated = size > MAX_FILE_BYTES;
        String content;
        if (truncated) {
            byte[] buf = new byte[(int) MAX_FILE_BYTES];
            try (java.io.InputStream in = new java.io.FileInputStream(target)) {
                int read = 0, n;
                while (read < buf.length && (n = in.read(buf, read, buf.length - read)) != -1) read += n;
                content = new String(buf, 0, read, "UTF-8");
            }
        } else {
            content = FileUtils.readFileToString(target, "UTF-8");
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("path", path);
        out.put("kind", kind(target.getName()));
        out.put("size", size);
        out.put("truncated", truncated);
        out.put("content", content);
        writeJson(response, HttpStatus.OK_200, out);
    }

    // ---- helpers ---------------------------------------------------------

    private static Map<String, Object> artifact(File f) {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("name", f.getName());
        a.put("url", "/reports/" + f.getName());
        a.put("size", f.length());
        a.put("modified", f.lastModified());
        return a;
    }

    private static Comparator<Map<String, Object>> byModifiedDesc() {
        return Comparator.comparingLong((Map<String, Object> a) -> ((Number) a.get("modified")).longValue()).reversed();
    }

    private static String kind(String name) {
        String ln = name.toLowerCase();
        if (ln.endsWith(".sel")) return "sel";
        if (ln.endsWith(".xml")) return "xml";
        return "other";
    }

    private static Map<String, Object> err(String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("error", message);
        return m;
    }

    private static String resource(String uri) {
        // this handler is only ever dispatched for the "/api" mapping, so the request URI
        // always begins with /api; strip exactly that prefix (Grizzly matched it by longest prefix).
        String sub = uri.startsWith("/api") ? uri.substring(4) : uri;
        while (sub.startsWith("/")) sub = sub.substring(1);
        while (sub.endsWith("/")) sub = sub.substring(0, sub.length() - 1);
        int q = sub.indexOf('?');
        if (q >= 0) sub = sub.substring(0, q);
        return sub;
    }

    private static void writeJson(Response response, HttpStatus status, Object obj) throws Exception {
        String text = WorkflowContextHandler.OM.writeValueAsString(obj);
        response.setContentType(MediaType.APPLICATION_JSON + "; charset=utf-8");
        response.setContentLength(text.getBytes("UTF-8").length);
        response.getWriter().write(text);
        response.setStatus(status);
    }
}
