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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
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
import com.gatf.imports.ImportResult;
import com.gatf.imports.Importers;
import com.gatf.selenium.Command;

/**
 * Phase 11 / Q2 — test importer endpoint for the React shell.
 *
 *   POST /import?type=playwright|jmeter|curl|postman|har[&fileName=<name>][&save=true][&filter=<url regex, har>]
 *     body = the raw Playwright .spec source, JMeter .jmx XML, curl command(s), Postman collection or HAR JSON
 *     ->   { type, fileName, kind, content, unsupported:[{line,construct,reason}], stats:{...},
 *            loadConfig?, variables?, saved:bool, savedPath?, validation? }
 *
 * With save omitted/false the endpoint only previews (parse + translate). With save=true it also writes
 * the file into the test-case directory (the same location /testcasefiles create uses), so it appears in
 * the test tree. API cases (JMeter, cURL, Postman, HAR) are written via WorkflowContextHandler.XOM — the exact mapper GATF uses to
 * read/write test XML — so the file round-trips and opens in the structured XML editor; imported .sel is
 * additionally parse-validated (Command.validateSel) and the result returned.
 *
 * Additive: touches no existing handler; registered by GatfConfigToolUtil at /import.
 */
public class GatfImportHandler extends HttpHandler {

    private GatfConfigToolMojoInt mojo;

    public GatfImportHandler(GatfConfigToolMojoInt mojo) {
        super();
        this.mojo = mojo;
    }

    @Override
    public void service(Request request, Response response) throws Exception {
        AcceptanceTestContext.checkAuthAndSetCors(mojo, request, response);
        if (response.getStatus() == 401) return;
        response.setHeader("Cache-Control", "no-cache, no-store");

        if (!request.getMethod().equals(Method.PUT) && !request.getMethod().equals(Method.POST)) {
            writeJson(response, HttpStatus.METHOD_NOT_ALLOWED_405, err("only POST/PUT is supported"));
            return;
        }

        String type = request.getParameter("type");
        String fileName = request.getParameter("fileName");
        boolean save = "true".equalsIgnoreCase(request.getParameter("save"));

        try {
            String source = readBody(request);
            if (StringUtils.isBlank(source)) {
                writeJson(response, HttpStatus.BAD_REQUEST_400, err("empty import source"));
                return;
            }
            String suite = deriveSuiteName(fileName, source, type);

            ImportResult result;
            try {
                result = Importers.importSource(type, source, suite, request.getParameter("filter"));
            } catch (IllegalArgumentException e) {
                writeJson(response, HttpStatus.BAD_REQUEST_400, err(e.getMessage()));
                return;
            }
            if ("xml".equals(result.kind) && result.testCases != null) {
                // serialize the built cases with GATF's own XmlMapper — guarantees round-trip fidelity
                result.content = WorkflowContextHandler.XOM.writeValueAsString(result.testCases);
            }
            if (fileName != null && !fileName.trim().isEmpty()) {
                result.fileName = ensureExt(fileName.trim(), result.kind);
            }

            Map<String, Object> out = new LinkedHashMap<String, Object>();
            out.put("type", result.type);
            out.put("fileName", result.fileName);
            out.put("kind", result.kind);
            out.put("content", result.content);
            out.put("unsupported", result.unsupported);
            out.put("stats", result.stats);
            if (result.loadConfig != null) out.put("loadConfig", result.loadConfig);
            if (result.variables != null) out.put("variables", result.variables);

            if (save) {
                GatfExecutorConfig cfg = GatfConfigToolUtil.getGatfExecutorConfig(mojo, null, request.getParameter("config"));
                String savedPath = writeToTestDir(cfg, result);
                out.put("saved", true);
                out.put("savedPath", result.fileName);
                if ("sel".equals(result.kind)) {
                    out.put("validation", validateSel(cfg, savedPath));
                }
            } else {
                out.put("saved", false);
            }
            writeJson(response, HttpStatus.OK_200, out);
        } catch (Exception e) {
            writeJson(response, HttpStatus.INTERNAL_SERVER_ERROR_500, err(e.getMessage() == null ? e.toString() : e.getMessage()));
        }
    }

    // Write the imported artifact into the test-case dir (mirrors GatfTestCaseFilesHandler's create path).
    private String writeToTestDir(GatfExecutorConfig cfg, ImportResult result) throws Exception {
        String basepath = cfg.getTestCasesBasePath() == null ? mojo.getRootDir() : cfg.getTestCasesBasePath();
        String filePath;
        if (cfg.getTestCaseDir() != null) {
            String dirPath = basepath + File.separator + cfg.getTestCaseDir();
            File d = new File(dirPath);
            if (!d.exists()) d.mkdirs();
            filePath = dirPath + File.separator + result.fileName;
        } else {
            filePath = basepath + File.separator + result.fileName;
        }
        File f = new File(filePath);
        if ("xml".equals(result.kind) && result.testCases != null) {
            FileOutputStream fos = new FileOutputStream(f);
            try {
                WorkflowContextHandler.XOM.writeValue(fos, result.testCases);
            } finally {
                fos.close();
            }
        } else {
            FileUtils.writeStringToFile(f, result.content, "UTF-8");
        }
        return filePath;
    }

    // Parse-validate an imported .sel via the static validator (no plugin context needed).
    private String validateSel(GatfExecutorConfig cfg, String filePath) {
        try {
            String basepath = cfg.getTestCasesBasePath() == null ? mojo.getRootDir() : cfg.getTestCasesBasePath();
            String cfgName = new File(basepath, "gatf-config.json").exists() ? "gatf-config.json" : "gatf-config.xml";
            String rel = new File(filePath).getName();
            if (cfg.getTestCaseDir() != null) rel = cfg.getTestCaseDir() + File.separator + rel;
            return Command.validateSel(new String[] { "-validate-sel", rel, cfgName, basepath }, null, false);
        } catch (Throwable t) {
            return "validation unavailable: " + t.getMessage();
        }
    }

    private String deriveSuiteName(String fileName, String source, String type) {
        if (fileName != null && !fileName.trim().isEmpty()) {
            String n = fileName.trim().replaceAll("\\.(sel|xml)$", "");
            return n.replaceAll("[^A-Za-z0-9_-]", "_");
        }
        return "imported-" + (type == null || type.isBlank() ? "tests" : type.trim().toLowerCase().replaceAll("[^a-z]", ""));
    }

    private String ensureExt(String name, String kind) {
        String want = "." + kind;
        String base = name.replaceAll("\\.(sel|xml)$", "");
        return base + want;
    }

    private String readBody(Request request) throws Exception {
        InputStream in = request.getInputStream();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[65536];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }

    private void writeJson(Response response, HttpStatus status, Object body) throws Exception {
        String json = WorkflowContextHandler.OM.writeValueAsString(body);
        byte[] b = json.getBytes(StandardCharsets.UTF_8);
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
}
