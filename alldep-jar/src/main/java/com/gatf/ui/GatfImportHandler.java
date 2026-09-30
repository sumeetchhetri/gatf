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
import java.nio.file.Files;
import java.util.Iterator;
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
 *   POST /import?type=playwright|jmeter|curl|postman|har|openapi[&fileName=<name>][&save=true][&filter=<url regex, har>]
 *               [&negative=true, openapi: also negative cases][&bundle=true, openapi: multi-file spec]
 *     body = the raw Playwright .spec source, JMeter .jmx XML, curl command(s), Postman collection or HAR JSON;
 *            bundle=true: {"main": spec text, "mainName"?: its relative path, "files": {relative path: text}} for relative-file $refs
 *     ->   { type, fileName, kind, content, unsupported:[{line,construct,reason}], stats:{...},
 *            loadConfig?, variables?, drift?:{added,removed,changed}, spec?, saved:bool, savedPath?, validation? }
 *
 * OpenAPI (OA2): each case points at the spec (saved beside the .xml as {@code <name>.openapi.json|yaml}) so responses
 * are contract-checked at run time; when the .xml already exists the response lists the operations that drifted.
 * A bundle is imported from a temp folder and saved as {@code <name>.openapi/} beside the .xml (relative paths kept), so
 * the run-time contract check resolves the same $refs.
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

        File bundleDir = null;
        try {
            String source = readBody(request);
            String bundleMain = null;
            if ("true".equalsIgnoreCase(request.getParameter("bundle"))) {
                try {
                    com.fasterxml.jackson.databind.JsonNode b = WorkflowContextHandler.OM.readTree(source);
                    source = b.path("main").asText("");
                    String ext = source.trim().startsWith("{") ? "json" : "yaml";
                    bundleMain = b.path("mainName").asText("").trim().isEmpty() ? "openapi." + ext : b.path("mainName").asText().trim();
                    bundleDir = Files.createTempDirectory("gatf-import").toFile();
                    FileUtils.writeStringToFile(bundleFile(bundleDir, bundleMain), source, "UTF-8");
                    for (Iterator<Map.Entry<String, com.fasterxml.jackson.databind.JsonNode>> it = b.path("files").fields(); it.hasNext();) {
                        Map.Entry<String, com.fasterxml.jackson.databind.JsonNode> e = it.next();
                        File f = bundleFile(bundleDir, e.getKey());
                        if (!f.exists()) FileUtils.writeStringToFile(f, e.getValue().asText(""), "UTF-8");   // the main file wins
                    }
                } catch (IllegalArgumentException | com.fasterxml.jackson.core.JsonProcessingException e) {
                    writeJson(response, HttpStatus.BAD_REQUEST_400, err("bad import bundle: " + e.getMessage()));
                    return;
                }
            }
            if (StringUtils.isBlank(source)) {
                writeJson(response, HttpStatus.BAD_REQUEST_400, err("empty import source"));
                return;
            }
            String suite = deriveSuiteName(fileName, source, type);

            boolean openapi = "openapi".equalsIgnoreCase(type) || "swagger".equalsIgnoreCase(type);
            String xmlName = fileName != null && !fileName.trim().isEmpty() ? ensureExt(fileName.trim(), "xml") : Importers.safeName(suite) + ".xml";
            String specName = new File(xmlName.replaceAll("\\.xml$", "") + ".openapi." + (source.trim().startsWith("{") ? "json" : "yaml")).getName();
            // bundle: <name>.openapi/<main> — the folder is saved beside the .xml with the relative paths kept
            String bundleName = new File(xmlName.replaceAll("\\.xml$", "") + ".openapi").getName();
            if (bundleMain != null) specName = bundleName + "/" + bundleMain.replace('\\', '/');

            ImportResult result;
            try {
                result = openapi
                        ? Importers.importOpenApi(source, suite, bundleDir == null ? null : bundleFile(bundleDir, bundleMain).getParentFile(),
                                specName, "true".equalsIgnoreCase(request.getParameter("negative")), bundleDir)
                        : com.gatf.imports.FlowToLoad.TYPE.equalsIgnoreCase(type)
                        ? com.gatf.imports.FlowToLoad.convert(source, suite, request.getParameter("filter"),   // M17: flow → load test
                                com.gatf.imports.FlowToLoad.Options.of(request.getParameter("users"), request.getParameter("duration"),
                                        request.getParameter("rate"), request.getParameter("engine")))
                        : Importers.importSource(type, source, suite, request.getParameter("filter"));
            } catch (IllegalArgumentException | com.fasterxml.jackson.core.JsonProcessingException e) {
                // bad options, nothing importable, or a malformed source: the caller's input, not a server fault
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
            if (save && com.gatf.imports.FlowToLoad.TYPE.equalsIgnoreCase(type) && !"true".equalsIgnoreCase(request.getParameter("overwrite"))) {
                // M17: never overwrite a saved load test (it may have been tuned): x-load.xml, x-load-2.xml, …
                final GatfExecutorConfig fcfg = GatfConfigToolUtil.getGatfExecutorConfig(mojo, null, request.getParameter("config"));
                String old = result.fileName;
                result.fileName = com.gatf.imports.FlowToLoad.freeName(old, n -> targetFile(fcfg, n).exists());
                if (!old.equals(result.fileName) && result.stats.get("vegetaCommand") != null) {
                    result.stats.put("vegetaCommand", String.valueOf(result.stats.get("vegetaCommand")).replace(
                            ImportResult.sideFileName(old, com.gatf.imports.FlowToLoad.TARGETS_SUFFIX),
                            ImportResult.sideFileName(result.fileName, com.gatf.imports.FlowToLoad.TARGETS_SUFFIX)));
                }
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
            if (!result.sideFiles.isEmpty()) {
                java.util.List<String> side = new java.util.ArrayList<String>();
                for (String suffix : result.sideFiles.keySet()) side.add(ImportResult.sideFileName(result.fileName, suffix));
                out.put("sideFiles", side);   // M17: e.g. the flow's vegeta targets, saved beside the test file
            }
            GatfExecutorConfig cfg = null;
            if (openapi) {
                out.put("spec", specName);
                cfg = GatfConfigToolUtil.getGatfExecutorConfig(mojo, null, request.getParameter("config"));
                File existing = targetFile(cfg, result.fileName);
                if (existing.isFile()) {
                    try {
                        java.util.List<com.gatf.executor.core.TestCase> old = WorkflowContextHandler.XOM.readValue(existing,
                                new com.fasterxml.jackson.core.type.TypeReference<java.util.List<com.gatf.executor.core.TestCase>>() {});
                        out.put("drift", com.gatf.contract.ContractDrift.diff(Importers.contractIndex(old),
                                Importers.contractIndex(result.testCases)).toMap());
                    } catch (Exception e) {
                        // an unreadable previous file: no drift list
                    }
                }
            }

            if (save) {
                if (cfg == null) cfg = GatfConfigToolUtil.getGatfExecutorConfig(mojo, null, request.getParameter("config"));
                String savedPath = writeToTestDir(cfg, result);
                if (openapi && bundleDir != null) {
                    File dest = new File(new File(savedPath).getParentFile(), bundleName);
                    FileUtils.deleteDirectory(dest);   // a re-upload replaces the bundle; no stale files from the last one
                    FileUtils.copyDirectory(bundleDir, dest);
                }
                else if (openapi) FileUtils.writeStringToFile(new File(new File(savedPath).getParentFile(), specName), source, "UTF-8");
                for (Map.Entry<String, String> e : result.sideFiles.entrySet()) {
                    FileUtils.writeStringToFile(new File(new File(savedPath).getParentFile(), ImportResult.sideFileName(result.fileName, e.getKey())),
                            e.getValue(), "UTF-8");
                }
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
        } finally {
            if (bundleDir != null) FileUtils.deleteQuietly(bundleDir);
        }
    }

    /** A bundle member inside {@code dir}; absolute paths and '..' segments are rejected. */
    static File bundleFile(File dir, String rel) {
        String r = rel == null ? "" : rel.replace('\\', '/');
        if (r.trim().isEmpty() || r.startsWith("/") || r.matches("^[A-Za-z]:.*")) throw new IllegalArgumentException("not a relative path: " + rel);
        for (String seg : r.split("/")) if ("..".equals(seg)) throw new IllegalArgumentException("'..' not allowed: " + rel);
        File f = new File(dir, r);
        try {
            if (!f.getCanonicalPath().startsWith(dir.getCanonicalPath() + File.separator)) throw new IllegalArgumentException("outside the bundle: " + rel);
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("bad path: " + rel);
        }
        return f;
    }

    // Write the imported artifact into the test-case dir (mirrors GatfTestCaseFilesHandler's create path).
    private String writeToTestDir(GatfExecutorConfig cfg, ImportResult result) throws Exception {
        File f = targetFile(cfg, result.fileName);
        if (f.getParentFile() != null && !f.getParentFile().exists()) f.getParentFile().mkdirs();
        String filePath = f.getPath();
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

    /** Where an imported file lands: the test-case dir (or the base path). */
    private File targetFile(GatfExecutorConfig cfg, String name) {
        String basepath = cfg.getTestCasesBasePath() == null ? mojo.getRootDir() : cfg.getTestCasesBasePath();
        if (cfg.getTestCaseDir() != null) return new File(basepath + File.separator + cfg.getTestCaseDir() + File.separator + name);
        return new File(basepath + File.separator + name);
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
