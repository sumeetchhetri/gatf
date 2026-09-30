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

import com.gatf.executor.core.AcceptanceTestContext;
import com.gatf.executor.core.GatfExecutorConfig;
import com.gatf.executor.core.WorkflowContextHandler;
import com.gatf.record.RecordSession;
import com.gatf.selenium.Command;

/**
 * Phase 11 (record mode) — drives a live "infer a test case from user actions" recording from the React
 * console. Launches a visible local Chrome, injects a recorder, streams the growing seleasy step list back
 * (the client polls), and on stop generates the .sel (optionally saving it into the test-case dir).
 *
 *   POST /record?action=start&url=<startUrl>         -> { sessionId, running }
 *   GET  /record?action=steps&sessionId=<id>         -> { running, lines:[...], count, error? }
 *   POST /record?action=stop&sessionId=<id>          -> { content, lines, count }
 *   POST /record?action=save&sessionId=<id>&fileName=<name>  -> { saved, savedPath, validation }
 *
 * Record mode is a local, dev-time authoring activity (the browser opens on the same machine as GATF).
 * Additive: touches no existing handler; registered by GatfConfigToolUtil at /record.
 */
public class GatfRecordHandler extends HttpHandler {

    private GatfConfigToolMojoInt mojo;
    private static final Map<String, RecordSession> SESSIONS = new ConcurrentHashMap<String, RecordSession>();

    public GatfRecordHandler(GatfConfigToolMojoInt mojo) {
        super();
        this.mojo = mojo;
    }

    @Override
    public void service(Request request, Response response) throws Exception {
        AcceptanceTestContext.checkAuthAndSetCors(mojo, request, response);
        if (response.getStatus() == 401) return;
        response.setHeader("Cache-Control", "no-cache, no-store");

        String action = request.getParameter("action");
        String sessionId = request.getParameter("sessionId");
        try {
            if ("start".equals(action)) {
                if (!isWrite(request)) { writeJson(response, HttpStatus.METHOD_NOT_ALLOWED_405, err("use POST to start")); return; }
                String id = UUID.randomUUID().toString();
                RecordSession s = new RecordSession(id, request.getParameter("url"), request.getParameter("browser"), testIdAttribute(request));
                try {
                    s.start();
                } catch (Throwable t) {
                    writeJson(response, HttpStatus.INTERNAL_SERVER_ERROR_500,
                            err("could not launch a local " + s.getBrowser() + " for recording: " + msg(t)));
                    return;
                }
                SESSIONS.put(id, s);
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("sessionId", id);
                out.put("running", true);
                writeJson(response, HttpStatus.OK_200, out);
                return;
            }

            RecordSession s = sessionId == null ? null : SESSIONS.get(sessionId);
            if (s == null) { writeJson(response, HttpStatus.NOT_FOUND_404, err("no such recording session")); return; }

            if ("steps".equals(action)) {
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("running", s.isRunning());
                out.put("lines", s.currentLines());
                out.put("count", s.currentLines().size());
                if (s.getError() != null) out.put("error", s.getError());
                writeJson(response, HttpStatus.OK_200, out);
            } else if ("stop".equals(action)) {
                if (!isWrite(request)) { writeJson(response, HttpStatus.METHOD_NOT_ALLOWED_405, err("use POST to stop")); return; }
                s.stop();
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("content", s.toSel());
                out.put("lines", s.currentLines());
                out.put("count", s.currentLines().size());
                writeJson(response, HttpStatus.OK_200, out);
            } else if ("save".equals(action)) {
                if (!isWrite(request)) { writeJson(response, HttpStatus.METHOD_NOT_ALLOWED_405, err("use POST to save")); return; }
                s.stop();
                String fileName = ensureSel(request.getParameter("fileName"));
                GatfExecutorConfig cfg = GatfConfigToolUtil.getGatfExecutorConfig(mojo, null, request.getParameter("config"));
                String savedPath = writeSel(cfg, fileName, s.toSel());
                SESSIONS.remove(sessionId);
                Map<String, Object> out = new LinkedHashMap<String, Object>();
                out.put("saved", true);
                out.put("savedPath", fileName);
                out.put("validation", validateSel(cfg, savedPath));
                writeJson(response, HttpStatus.OK_200, out);
            } else {
                writeJson(response, HttpStatus.BAD_REQUEST_400, err("unknown action '" + action + "'"));
            }
        } catch (Exception e) {
            writeJson(response, HttpStatus.INTERNAL_SERVER_ERROR_500, err(msg(e)));
        }
    }

    // M3: the project's testIdAttribute for testid@ candidates (null = data-testid); an unreadable config keeps the default
    private String testIdAttribute(Request request) {
        try {
            GatfExecutorConfig cfg = GatfConfigToolUtil.getGatfExecutorConfig(mojo, null, request.getParameter("config"));
            return cfg == null ? null : cfg.getTestIdAttribute();
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isWrite(Request request) {
        return request.getMethod().equals(Method.POST) || request.getMethod().equals(Method.PUT);
    }

    // Write the recorded .sel into the test-case dir (mirrors the create path used elsewhere).
    private String writeSel(GatfExecutorConfig cfg, String fileName, String content) throws Exception {
        String basepath = cfg.getTestCasesBasePath() == null ? mojo.getRootDir() : cfg.getTestCasesBasePath();
        String filePath;
        if (cfg.getTestCaseDir() != null) {
            String dirPath = basepath + File.separator + cfg.getTestCaseDir();
            File d = new File(dirPath);
            if (!d.exists()) d.mkdirs();
            filePath = dirPath + File.separator + fileName;
        } else {
            filePath = basepath + File.separator + fileName;
        }
        FileUtils.writeStringToFile(new File(filePath), content, "UTF-8");
        return filePath;
    }

    private String validateSel(GatfExecutorConfig cfg, String filePath) {
        try {
            String basepath = cfg.getTestCasesBasePath() == null ? mojo.getRootDir() : cfg.getTestCasesBasePath();
            String cfgName = new File(basepath, "gatf-config.json").exists() ? "gatf-config.json" : "gatf-config.xml";
            String rel = new File(filePath).getName();
            if (cfg.getTestCaseDir() != null) rel = cfg.getTestCaseDir() + File.separator + rel;
            return Command.validateSel(new String[] { "-validate-sel", rel, cfgName, basepath }, null, false);
        } catch (Throwable t) {
            return "validation unavailable: " + msg(t);
        }
    }

    private String ensureSel(String name) {
        String base = (name == null || name.trim().isEmpty()) ? "recorded" : name.trim().replaceAll("[^A-Za-z0-9_\\-]", "_");
        return base.replaceAll("\\.sel$", "") + ".sel";
    }

    private void writeJson(Response response, HttpStatus status, Object body) throws Exception {
        String json = WorkflowContextHandler.OM.writeValueAsString(body);
        byte[] b = json.getBytes("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON + "; charset=utf-8");
        response.setContentLength(b.length);
        response.getWriter().write(json);
        response.setStatus(status);
    }

    private static Map<String, Object> err(String message) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("error", message);
        return m;
    }

    private static String msg(Throwable t) {
        return t.getMessage() == null ? t.toString() : t.getMessage();
    }
}
