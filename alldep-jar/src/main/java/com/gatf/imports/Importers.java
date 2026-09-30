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
package com.gatf.imports;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.gatf.executor.core.TestCase;

/**
 * Single entry point for every importer, shared by the /import endpoint and {@code gatf import}.
 * API importers (jmeter, curl, postman, har, openapi) return {@link ImportResult#testCases} with kind "xml"; the caller
 * serializes them with GATF's XmlMapper. Playwright returns seleasy {@link ImportResult#content} (kind "sel").
 */
public final class Importers {

    public static final List<String> TYPES = List.of("playwright", "jmeter", "curl", "postman", "har", "openapi", "flow");

    private Importers() {}

    /**
     * @param filter har / flow only: optional regex on the request URL
     * @throws IllegalArgumentException unknown type, unreadable source, or nothing importable
     */
    public static ImportResult importSource(String type, String source, String suite, String filter) throws Exception {
        return importSource(type, source, suite, filter, null);
    }

    /** @param baseDir openapi only: the spec's folder, so relative-file {@code $ref}s resolve (null = reported) */
    public static ImportResult importSource(String type, String source, String suite, String filter, java.io.File baseDir) throws Exception {
        String t = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
        switch (t) {
            case "playwright":
                return new PlaywrightImporter().importSource(source, suite);
            case "jmeter":
                return new JMeterImporter().importSource(source, suite);
            case "openapi": case "swagger":
                return new OpenApiImporter(baseDir).importSource(source, suite);
            case FlowToLoad.TYPE:   // M17: a run's network.har → a load test of the same flow (default load settings)
                return FlowToLoad.convert(source, suite, filter, null);
            case "curl": case "postman": case "har": {
                ImportResult res = new ImportResult();
                res.type = t;
                res.kind = "xml";
                res.fileName = safeName(suite) + ".xml";
                List<ApiRequest> reqs;
                if (t.equals("curl")) reqs = readCurl(source, res);
                else if (t.equals("postman")) reqs = new PostmanImporter().read(source, res);
                else reqs = new HarImporter(filter).read(source, res);
                res.testCases = toTestCases(reqs, res);
                if (res.testCases.isEmpty()) {
                    throw new IllegalArgumentException("nothing to import: no " + t + " request could be converted"
                            + (res.unsupported.isEmpty() ? "" : " (" + res.unsupported.get(0).construct + ": " + res.unsupported.get(0).reason + ")"));
                }
                return res;
            }
            default:
                throw new IllegalArgumentException("unknown import type '" + type + "' (expected " + String.join("|", TYPES) + ")");
        }
    }

    static List<ApiRequest> readCurl(String source, ImportResult res) {
        List<ApiRequest> out = new ArrayList<ApiRequest>();
        boolean insecure = false;
        for (CurlCommand.Parsed p : CurlCommand.parseAll(source)) {
            res.unsupported.addAll(p.issues);
            insecure |= p.insecure;
            if (p.request != null) {
                p.request.name = p.request.upperMethod() + " " + p.request.url.replaceAll("\\?.*$", "");
                out.add(p.request);
            }
        }
        if (insecure) {
            res.unsupported(0, "-k / --insecure", "per-request TLS relaxation is not carried over — the executor's HTTP client settings apply");
        }
        res.stat("format", "curl");
        res.stat("requests", out.size());
        return out;
    }

    /** ApiRequests → TestCases: unique names, sequence order; unsupported HTTP methods are reported and skipped. */
    public static List<TestCase> toTestCases(List<ApiRequest> reqs, ImportResult res) {
        List<TestCase> cases = new ArrayList<TestCase>();
        Set<String> names = new HashSet<String>();
        for (ApiRequest r : reqs) {
            String m = r.upperMethod();
            if (!ApiRequest.SUPPORTED_METHODS.contains(m)) {
                res.unsupported(r.line, m + " " + r.url, "GATF API tests support GET/POST/PUT/DELETE/PATCH only — skipped");
                continue;
            }
            TestCase tc = new TestCase();
            String base = r.name == null || r.name.isBlank() ? m + " " + r.url : r.name.trim();
            String name = base;
            for (int i = 2; !names.add(name.toLowerCase(Locale.ROOT)); i++) name = base + " (" + i + ")";
            tc.setName(name);
            if (r.description != null) tc.setDescription(r.description);
            tc.setMethod(m);
            if (r.baseUrl != null) tc.setBaseUrl(r.baseUrl);
            tc.setUrl(r.url == null || r.url.isEmpty() ? "/" : r.url);
            if (!r.headers.isEmpty()) tc.setHeaders(new LinkedHashMap<String, String>(r.headers));
            if (r.body != null && !r.body.isEmpty()) tc.setContent(r.body);
            if (r.contentFile != null) tc.setContentFile(r.contentFile);
            if (!r.formParts.isEmpty()) tc.setMultipartContent(new ArrayList<String>(r.formParts));
            if (r.expectedStatus > 0) tc.setExpectedResCode(r.expectedStatus);
            if (r.expectedContentType != null) tc.setExpectedResContentType(r.expectedContentType);
            if (r.contract != null) tc.setContract(r.contract);
            if (r.contractHash != null) tc.setContractHash(r.contractHash);
            tc.setSequence(cases.size() + 1);
            cases.add(tc);
        }
        res.stat("testCases", cases.size());
        return cases;
    }

    /**
     * OA2 — OpenAPI import with contract pointers.
     * @param specRef  the spec file as the generated .xml will reference it (relative to the .xml's folder)
     * @param negative also generate negative cases (missing required parameter / wrong body type → expect 4xx)
     */
    public static ImportResult importOpenApi(String source, String suite, java.io.File baseDir, String specRef, boolean negative) throws Exception {
        return new OpenApiImporter(baseDir, specRef, negative).importSource(source, suite);
    }

    /** {@link #importOpenApi} for an uploaded bundle: relative-file {@code $ref}s may not leave {@code confineTo}. */
    public static ImportResult importOpenApi(String source, String suite, java.io.File baseDir, String specRef, boolean negative,
            java.io.File confineTo) throws Exception {
        return new OpenApiImporter(baseDir, specRef, negative).confineRefsTo(confineTo).importSource(source, suite);
    }

    /** OA2 — operation pointer → fingerprint of already generated cases (for the drift list on re-import). */
    public static java.util.Map<String, String> contractIndex(List<TestCase> cases) {
        java.util.Map<String, String> m = new java.util.LinkedHashMap<String, String>();
        if (cases == null) return m;
        for (TestCase tc : cases) {
            if (tc.getContract() == null || tc.getContract().indexOf('#') < 0) continue;
            m.putIfAbsent(tc.getContract().substring(tc.getContract().indexOf('#') + 1), tc.getContractHash());
        }
        return m;
    }

    public static String safeName(String s) {
        if (s == null || s.trim().isEmpty()) return "imported";
        return s.trim().replaceAll("\\.(sel|xml)$", "").replaceAll("[^A-Za-z0-9_-]", "_");
    }
}
