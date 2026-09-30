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

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * OA1 — Swagger 2.0 / OpenAPI 3.0 / 3.1 (JSON or YAML) → one {@link ApiRequest} per operation, turned into GATF API
 * test cases by {@link Importers#toTestCases}. Parameters and bodies come from {@code example}/{@code examples}/
 * {@code default}, else a value synthesised from the schema; the expected status is the first 2xx. Security schemes
 * become {@code ${authToken}}/API-key placeholders (never secrets); tags become GATF {@code @tags} in the description.
 * Pure: Jackson only (YAML through jackson-dataformat-yaml, loaded lazily).
 */
public final class OpenApiImporter {

    static final ObjectMapper OM = new ObjectMapper();
    private static final JsonNodeFactory NF = JsonNodeFactory.instance;
    private static final List<String> METHODS = List.of("get", "put", "post", "delete", "options", "head", "patch", "trace");
    private static final int MAX_DEPTH = 8;

    /** folder of the spec file, for relative-file {@code $ref}s; null = relative refs are reported, not followed. */
    private final File baseDir;

    private final Map<String, JsonNode> docs = new HashMap<String, JsonNode>();
    private ImportResult res;
    private boolean v2;
    private final Set<String> reported = new LinkedHashSet<String>();

    public OpenApiImporter() { this(null); }

    public OpenApiImporter(File baseDir) {
        this.baseDir = baseDir;
    }

    /** A schema/parameter/body node together with the document it lives in (for nested relative refs). */
    static final class Loc {
        final JsonNode node;
        final String doc;   // "" = root document, else absolute file path
        final String key;   // doc#pointer of the resolved $ref target, null when not reached through a $ref
        Loc(JsonNode node, String doc, String key) { this.node = node; this.doc = doc; this.key = key; }
    }

    // ---------------------------------------------------------------- entry points

    public ImportResult importSource(String source, String suite) throws Exception {
        ImportResult r = new ImportResult();
        r.type = "openapi";
        r.kind = "xml";
        r.fileName = Importers.safeName(suite) + ".xml";
        List<ApiRequest> reqs = read(source, r);
        r.testCases = Importers.toTestCases(reqs, r);
        if (r.testCases.isEmpty()) {
            throw new IllegalArgumentException("nothing to import: the spec has no GET/POST/PUT/DELETE/PATCH operation");
        }
        return r;
    }

    /** Parses JSON or YAML (anything not starting with '{' or '[' is read as YAML). */
    public static JsonNode parse(String source) throws Exception {
        String s = source == null ? "" : source.trim();
        if (s.startsWith("﻿")) s = s.substring(1);
        if (s.startsWith("{") || s.startsWith("[")) return OM.readTree(s);
        return Yaml.read(s);
    }

    /** jackson-dataformat-yaml, isolated so JSON-only use never touches the YAML classes. */
    static final class Yaml {
        static JsonNode read(String s) throws Exception {
            try {
                return new com.fasterxml.jackson.dataformat.yaml.YAMLMapper().readTree(s);
            } catch (NoClassDefFoundError e) {
                throw new IllegalArgumentException("YAML specs need jackson-dataformat-yaml on the classpath — convert the spec to JSON");
            }
        }
    }

    public List<ApiRequest> read(String source, ImportResult r) throws Exception {
        this.res = r;
        JsonNode root = parse(source);
        if (root == null || !root.isObject()) throw new IllegalArgumentException("not an OpenAPI/Swagger document (expected an object)");
        String version;
        if (root.path("swagger").asText("").startsWith("2")) {
            v2 = true;
            version = "swagger " + root.path("swagger").asText();
        } else if (root.path("openapi").asText("").startsWith("3")) {
            version = "openapi " + root.path("openapi").asText();
        } else {
            throw new IllegalArgumentException("not an OpenAPI/Swagger document: no 'swagger: 2.0' or 'openapi: 3.x' field");
        }
        docs.put("", root);
        r.stat("format", version);
        String title = root.path("info").path("title").asText("");
        if (!title.isEmpty()) r.stat("title", title);

        String[] base = v2 ? baseV2(root) : serverBase(root.path("servers"), root);
        if (base[0] != null) r.stat("baseUrl", base[0]);
        else r.stat("baseUrl", "(set baseUrl in gatf-config)");

        List<ApiRequest> out = new ArrayList<ApiRequest>();
        Map<String, String> vars = new LinkedHashMap<String, String>();
        int ops = 0;
        JsonNode paths = root.path("paths");
        for (Iterator<Map.Entry<String, JsonNode>> it = paths.fields(); it.hasNext();) {
            Map.Entry<String, JsonNode> pe = it.next();
            if (pe.getKey().startsWith("x-")) continue;
            Loc item = deref(new Loc(pe.getValue(), "", null));
            if (item == null) continue;
            for (String m : METHODS) {
                JsonNode op = item.node.get(m);
                if (op == null || !op.isObject()) continue;
                ops++;
                String label = m.toUpperCase(Locale.ROOT) + " " + pe.getKey();
                if (!ApiRequest.SUPPORTED_METHODS.contains(m.toUpperCase(Locale.ROOT))) {
                    r.unsupported(0, label, "GATF API tests support GET/POST/PUT/DELETE/PATCH only — skipped");
                    continue;
                }
                String[] opBase = base;
                if (!v2 && op.path("servers").size() > 0) opBase = serverBase(op.path("servers"), root);
                else if (!v2 && item.node.path("servers").size() > 0) opBase = serverBase(item.node.path("servers"), root);
                ApiRequest q = operation(root, pe.getKey(), m, item, op, opBase, vars, label);
                if (q != null) {
                    q.line = ops;
                    out.add(q);
                }
            }
        }
        if (!v2) {
            for (Iterator<String> w = root.path("webhooks").fieldNames(); w.hasNext();) {
                r.unsupported(0, "webhook " + w.next(), "webhooks are called by the API, not by the client — not converted");
            }
        }
        if (!vars.isEmpty()) {
            Map<String, Object> v = new LinkedHashMap<String, Object>();
            for (Map.Entry<String, String> e : vars.entrySet()) v.put(e.getKey(), e.getValue());
            r.variables = v;
        }
        r.stat("operations", ops);
        return out;
    }

    // ---------------------------------------------------------------- base URL

    private String[] baseV2(JsonNode root) {
        String basePath = root.path("basePath").asText("");
        String host = root.path("host").asText("");
        if (host.isEmpty()) return new String[] { null, basePath };
        String scheme = "https";
        JsonNode schemes = root.path("schemes");
        if (schemes.size() > 0) {
            scheme = schemes.get(0).asText("https");
            for (JsonNode s : schemes) if ("https".equals(s.asText())) scheme = "https";
        }
        return new String[] { scheme + "://" + host, basePath };
    }

    private static final Pattern SERVER_VAR = Pattern.compile("\\{([^}]+)\\}");

    /** servers[0].url with its variables' defaults → {origin or null, base path}. */
    static String[] serverBase(JsonNode servers, JsonNode root) {
        if (servers == null || servers.size() == 0) return new String[] { null, "" };
        JsonNode s = servers.get(0);
        String url = s.path("url").asText("/");
        JsonNode vars = s.path("variables");
        Matcher m = SERVER_VAR.matcher(url);
        StringBuilder b = new StringBuilder();
        while (m.find()) {
            JsonNode v = vars.path(m.group(1));
            String val = v.has("default") ? v.get("default").asText() : v.path("enum").path(0).asText("");
            m.appendReplacement(b, Matcher.quoteReplacement(val));
        }
        m.appendTail(b);
        url = b.toString();
        if (url.matches("^[A-Za-z][A-Za-z0-9+.-]*://.*")) {
            String[] p = ApiRequest.splitUrl(url);
            String path = "/".equals(p[1]) ? "" : p[1];
            return new String[] { p[0], path };
        }
        if (url.startsWith("//")) {
            String[] p = ApiRequest.splitUrl("https:" + url);
            return new String[] { p[0], "/".equals(p[1]) ? "" : p[1] };
        }
        return new String[] { null, "/".equals(url) ? "" : url };
    }

    // ---------------------------------------------------------------- operations

    private ApiRequest operation(JsonNode root, String path, String method, Loc item, JsonNode op, String[] base,
            Map<String, String> vars, String label) {
        ApiRequest q = new ApiRequest();
        q.method = method.toUpperCase(Locale.ROOT);
        String opId = op.path("operationId").asText("").trim();
        q.name = opId.isEmpty() ? label : opId;

        // description: summary + @tags (TestFilter picks the tags up for gatf test --tag)
        StringBuilder d = new StringBuilder();
        String summary = op.path("summary").asText("").trim();
        if (summary.isEmpty()) summary = firstLine(op.path("description").asText(""));
        if (!summary.isEmpty()) d.append(summary);
        for (JsonNode t : op.path("tags")) {
            String tag = t.asText("").trim().replaceAll("[^A-Za-z0-9_.:-]+", "-").replaceAll("^-+|-+$", "");
            if (tag.isEmpty()) continue;
            if (d.length() > 0) d.append(' ');
            d.append('@').append(tag);
        }
        if (op.path("deprecated").asBoolean(false)) d.append(d.length() > 0 ? " " : "").append("@deprecated");
        if (d.length() > 0) q.description = d.toString();

        if (op.path("callbacks").size() > 0) {
            for (Iterator<String> c = op.path("callbacks").fieldNames(); c.hasNext();) {
                res.unsupported(0, label + " callback " + c.next(), "callbacks are requests made by the API — not converted");
            }
        }

        // parameters: path-item level, overridden by operation level (same name + in)
        Map<String, Loc> params = new LinkedHashMap<String, Loc>();
        collectParams(item.node.path("parameters"), item.doc, params);
        collectParams(op.path("parameters"), item.doc, params);

        String p = path;
        List<String> query = new ArrayList<String>();
        Map<String, String> formFields = new LinkedHashMap<String, String>();
        boolean formHasFile = false;
        Loc v2Body = null;
        for (Loc pl : params.values()) {
            JsonNode pn = pl.node;
            String in = pn.path("in").asText("");
            String name = pn.path("name").asText("");
            boolean required = pn.path("required").asBoolean(false) || "path".equals(in);
            if ("body".equals(in)) { v2Body = pl; continue; }
            if ("formData".equals(in)) {
                if ("file".equals(pn.path("type").asText())) { formHasFile = true; formFields.put(name, null); continue; }
                JsonNode v = paramValue(pl);
                if (v != null || required) formFields.put(name, scalar(v == null ? NF.textNode("string") : v));
                continue;
            }
            JsonNode v = paramValue(pl);
            if (v == null && !required) continue;   // optional with nothing to go on: left out
            if (v == null) v = NF.textNode("string");
            switch (in) {
                case "path":
                    p = p.replace("{" + name + "}", encPath(scalar(v)));
                    break;
                case "query":
                    if (v.isArray()) {
                        for (JsonNode e : v) query.add(enc(name) + "=" + enc(scalar(e)));
                    } else {
                        query.add(enc(name) + "=" + enc(scalar(v)));
                    }
                    break;
                case "header":
                    if (!name.equalsIgnoreCase("Accept") && !name.equalsIgnoreCase("Content-Type") && !name.equalsIgnoreCase("Authorization")) {
                        q.putHeader(name, scalar(v));
                    }
                    break;
                case "cookie":
                    q.appendHeader("Cookie", name + "=" + scalar(v), "; ");
                    break;
                default:
                    break;
            }
        }

        security(root, op, q, query, vars, label);

        // body
        if (v2) {
            List<String> consumes = strings(op.has("consumes") ? op.path("consumes") : root.path("consumes"));
            if (v2Body != null) {
                String ct = pickMedia(consumes, "application/json");
                Loc schema = new Loc(v2Body.node.path("schema"), v2Body.doc, null);
                JsonNode sample = firstNonNull(v2Body.node.path("x-example"), exampleOf(schema), sample(schema, true));
                setBody(q, ct, sample, label, false);
            } else if (!formFields.isEmpty()) {
                boolean multipart = formHasFile || consumes.contains("multipart/form-data");
                formBody(q, formFields, multipart, label);
            }
        } else {
            JsonNode rb = op.path("requestBody");
            if (!rb.isMissingNode()) {
                Loc body = deref(new Loc(rb, item.doc, null));
                if (body != null) requestBodyV3(q, body, label);
            }
        }

        // expected response
        responses(root, op, item.doc, q, label);

        String url = (base[1] == null ? "" : base[1].replaceAll("/+$", "")) + (p.startsWith("/") ? p : "/" + p);
        if (!query.isEmpty()) url += "?" + String.join("&", query);
        // the spec's origin is reported (stats.baseUrl), never written per case: a case baseUrl overrides gatf-config's
        q.baseUrl = null;
        q.url = url;
        q.fullUrl = (base[0] == null ? "" : base[0]) + url;
        return q;
    }

    private void collectParams(JsonNode list, String doc, Map<String, Loc> into) {
        for (JsonNode pn : list) {
            Loc l = deref(new Loc(pn, doc, null));
            if (l == null) continue;
            into.put(l.node.path("in").asText("") + ":" + l.node.path("name").asText(""), l);
        }
    }

    /** example → examples (first) → schema example/default/enum → synthesised; null when nothing and optional. */
    private JsonNode paramValue(Loc pl) {
        JsonNode pn = pl.node;
        if (pn.has("example")) return pn.get("example");
        JsonNode ex = firstExample(pn.path("examples"), pl.doc);
        if (ex != null) return ex;
        if (v2) {
            if (pn.has("x-example")) return pn.get("x-example");
            if (pn.has("default")) return pn.get("default");
            if (pn.path("enum").size() > 0) return pn.path("enum").get(0);
            if (!pn.path("required").asBoolean(false) && !"path".equals(pn.path("in").asText())) return null;
            return sample(new Loc(pn, pl.doc, null), true);   // v2 non-body params carry type/format inline
        }
        Loc schema = new Loc(pn.path("schema"), pl.doc, null);
        JsonNode sv = exampleOf(schema);
        if (sv != null) return sv;
        if (!pn.path("required").asBoolean(false) && !"path".equals(pn.path("in").asText())) return null;
        if (pn.has("content")) {   // parameter with a media-type schema (e.g. JSON in a query param)
            for (Iterator<JsonNode> it = pn.path("content").elements(); it.hasNext();) {
                JsonNode s = sample(new Loc(it.next().path("schema"), pl.doc, null), true);
                return s == null ? null : NF.textNode(s.toString());
            }
        }
        return sample(schema, true);
    }

    /** schema-level example / examples[0] (3.1) / default / const / enum[0], or null. */
    private JsonNode exampleOf(Loc schema) {
        Loc s = deref(schema);
        if (s == null || !s.node.isObject()) return null;
        JsonNode n = s.node;
        if (n.has("example")) return n.get("example");
        if (n.path("examples").isArray() && n.path("examples").size() > 0) return n.path("examples").get(0);
        if (n.has("default")) return n.get("default");
        if (n.has("const")) return n.get("const");
        if (n.path("enum").size() > 0) return n.path("enum").get(0);
        return null;
    }

    /** OAS3 {@code examples: {name: {value | $ref}}} → the first value. */
    private JsonNode firstExample(JsonNode examples, String doc) {
        if (!examples.isObject()) return null;
        for (Iterator<JsonNode> it = examples.elements(); it.hasNext();) {
            Loc e = deref(new Loc(it.next(), doc, null));
            if (e != null && e.node.has("value")) return e.node.get("value");
            if (e != null && e.node.has("externalValue")) {
                reportOnce("externalValue", "examples.externalValue", "external example files are not fetched — schema sample used");
            }
        }
        return null;
    }

    private void requestBodyV3(ApiRequest q, Loc body, String label) {
        JsonNode content = body.node.path("content");
        List<String> types = new ArrayList<String>();
        content.fieldNames().forEachRemaining(types::add);
        if (types.isEmpty()) return;
        String ct = pickMedia(types, null);
        JsonNode mt = content.path(ct);
        Loc schema = new Loc(mt.path("schema"), body.doc, null);
        String lct = ct.toLowerCase(Locale.ROOT);
        if (lct.startsWith("multipart/") || lct.equals("application/x-www-form-urlencoded")) {
            JsonNode obj = firstNonNull(mt.get("example"), firstExample(mt.path("examples"), body.doc), sample(schema, true));
            Map<String, String> fields = new LinkedHashMap<String, String>();
            Loc rs = deref(schema);
            if (obj != null && obj.isObject()) {
                for (Iterator<Map.Entry<String, JsonNode>> it = obj.fields(); it.hasNext();) {
                    Map.Entry<String, JsonNode> e = it.next();
                    JsonNode ps = rs == null ? null : rs.node.path("properties").path(e.getKey());
                    boolean binary = ps != null && isBinary(deref(new Loc(ps, rs.doc, null)));
                    fields.put(e.getKey(), binary ? null : scalar(e.getValue()));
                }
            }
            formBody(q, fields, lct.startsWith("multipart/"), label);
            return;
        }
        if (isBinary(deref(schema)) || lct.equals("application/octet-stream") || lct.startsWith("image/")
                || lct.startsWith("audio/") || lct.startsWith("video/")) {
            res.unsupported(0, label + " body " + ct, "binary request body — set contentFile on the test case");
            return;
        }
        JsonNode sample = firstNonNull(mt.get("example"), firstExample(mt.path("examples"), body.doc), exampleOf(schema), sample(schema, true));
        setBody(q, ct, sample, label, true);
    }

    private void setBody(ApiRequest q, String ct, JsonNode sample, String label, boolean v3) {
        if (sample == null) return;
        String lct = ct.toLowerCase(Locale.ROOT);
        String type = ct.contains("*") ? "application/json" : ct;
        if (lct.contains("json") || ct.contains("*")) {
            q.body = sample.isTextual() && looksJson(sample.asText()) ? sample.asText() : sample.toString();
        } else if (sample.isTextual()) {
            q.body = sample.asText();
        } else if (lct.contains("xml")) {
            res.unsupported(0, label + " body " + ct, "no XML example in the spec and XML bodies are not synthesised — add content");
            return;
        } else {
            q.body = sample.isValueNode() ? sample.asText() : sample.toString();
        }
        q.putHeader("Content-Type", type);
    }

    private void formBody(ApiRequest q, Map<String, String> fields, boolean multipart, String label) {
        if (multipart) {
            for (Map.Entry<String, String> e : fields.entrySet()) {
                if (e.getValue() == null) {
                    res.unsupported(0, label + " multipart part '" + e.getKey() + "'", "binary multipart part — add name:file:<path> to multipartContent");
                    continue;
                }
                if (!ApiRequest.formPartExpressible(e.getKey(), e.getValue())) {
                    res.unsupported(0, label + " multipart part '" + e.getKey() + "'", "':' in a part name/value cannot be expressed in GATF multipart syntax");
                    continue;
                }
                q.formParts.add(e.getKey() + ":text:" + e.getValue());
            }
            return;
        }
        List<String> kv = new ArrayList<String>();
        for (Map.Entry<String, String> e : fields.entrySet()) {
            if (e.getValue() != null) kv.add(enc(e.getKey()) + "=" + enc(e.getValue()));
        }
        if (kv.isEmpty()) return;
        q.body = String.join("&", kv);
        q.putHeader("Content-Type", "application/x-www-form-urlencoded");
    }

    private void responses(JsonNode root, JsonNode op, String doc, ApiRequest q, String label) {
        JsonNode rs = op.path("responses");
        String code = null;
        for (Iterator<String> it = rs.fieldNames(); it.hasNext();) {
            String c = it.next();
            if (c.matches("2\\d\\d") && (code == null || !code.matches("2\\d\\d"))) code = c;   // first declared 2xx
            else if (code == null && c.equalsIgnoreCase("2XX")) code = c;
        }
        q.expectedStatus = code == null || !code.matches("\\d{3}") ? 200 : Integer.parseInt(code);
        if (code == null) return;
        Loc resp = deref(new Loc(rs.path(code), doc, null));
        if (resp == null) return;
        if (resp.node.path("links").size() > 0) {
            res.unsupported(0, label + " links", "response links are not converted — chain with workflowContextParameterMap");
        }
        if (v2) {
            if (!resp.node.has("schema")) return;
            List<String> produces = strings(op.has("produces") ? op.path("produces") : root.path("produces"));
            String ct = pickMedia(produces, "application/json");
            if (!ct.contains("*")) expect(q, ct);
            return;
        }
        List<String> types = new ArrayList<String>();
        resp.node.path("content").fieldNames().forEachRemaining(types::add);
        if (types.isEmpty()) return;
        String ct = pickMedia(types, null);
        if (!ct.contains("*")) expect(q, ct);
    }

    /** Expect {@code ct} and ask for it (Accept), so a server that can answer in several types answers in this one. */
    private static void expect(ApiRequest q, String ct) {
        q.expectedContentType = ct;
        boolean has = false;
        for (String k : q.headers.keySet()) has |= k.equalsIgnoreCase("Accept");
        if (!has) q.headers.put("Accept", ct);
    }

    // ---------------------------------------------------------------- security

    private void security(JsonNode root, JsonNode op, ApiRequest q, List<String> query, Map<String, String> vars, String label) {
        JsonNode reqs = op.has("security") ? op.get("security") : root.path("security");
        if (!reqs.isArray() || reqs.size() == 0) return;
        JsonNode first = reqs.get(0);   // alternatives: the first requirement set is used
        JsonNode schemes = v2 ? root.path("securityDefinitions") : root.path("components").path("securitySchemes");
        for (Iterator<String> it = first.fieldNames(); it.hasNext();) {
            String name = it.next();
            Loc sl = deref(new Loc(schemes.path(name), "", null));
            if (sl == null || sl.node.isMissingNode()) {
                reportOnce("sec:" + name, "security scheme '" + name + "'", "not defined in the spec — ignored");
                continue;
            }
            JsonNode s = sl.node;
            String type = s.path("type").asText("");
            String scheme = s.path("scheme").asText("").toLowerCase(Locale.ROOT);
            if ("basic".equals(type) || ("http".equals(type) && "basic".equals(scheme))) {
                q.putHeader("Authorization", "Basic ${basicAuth}");
                vars.put("basicAuth", "");
            } else if ("http".equals(type) && !"bearer".equals(scheme)) {
                String v = varName(name);
                q.putHeader("Authorization", s.path("scheme").asText() + " ${" + v + "}");
                vars.put(v, "");
            } else if ("apiKey".equals(type)) {
                String v = varName(name);
                String kn = s.path("name").asText(name);
                String in = s.path("in").asText("header");
                if ("query".equals(in)) query.add(enc(kn) + "=${" + v + "}");
                else if ("cookie".equals(in)) q.appendHeader("Cookie", kn + "=${" + v + "}", "; ");
                else q.putHeader(kn, "${" + v + "}");
                vars.put(v, "");
            } else if ("mutualTLS".equals(type)) {
                reportOnce("mtls", "security scheme '" + name + "' (mutualTLS)", "client certificates are configured on the executor, not per test case");
            } else {   // http bearer, oauth2, openIdConnect
                q.putHeader("Authorization", "Bearer ${authToken}");
                vars.put("authToken", "");
            }
        }
    }

    static String varName(String scheme) {
        String v = scheme.replaceAll("[^A-Za-z0-9_]", "_");
        return v.isEmpty() || Character.isDigit(v.charAt(0)) ? "_" + v : v;
    }

    // ---------------------------------------------------------------- $ref

    /** Follows a {@code $ref} chain (local pointers, relative files) with a cycle guard; null when unresolvable. */
    Loc deref(Loc l) {
        Loc cur = l;
        Set<String> seen = new LinkedHashSet<String>();
        while (cur != null && cur.node != null && cur.node.isObject() && cur.node.has("$ref")) {
            String ref = cur.node.get("$ref").asText("");
            String file = ref.contains("#") ? ref.substring(0, ref.indexOf('#')) : ref;
            String pointer = ref.contains("#") ? ref.substring(ref.indexOf('#') + 1) : "";
            String doc = cur.doc;
            if (!file.isEmpty()) {
                if (file.matches("^[A-Za-z][A-Za-z0-9+.-]*://.*")) {
                    reportOnce("ref:" + ref, "$ref " + ref, "remote references are not fetched");
                    return null;
                }
                File base = doc.isEmpty() ? baseDir : new File(doc).getParentFile();
                if (base == null) {
                    reportOnce("ref:" + file, "$ref " + ref, "relative file reference — import the spec from its folder (gatf import openapi <file>)");
                    return null;
                }
                File f = new File(base, file);
                try {
                    doc = f.getCanonicalPath();
                    if (!docs.containsKey(doc)) {
                        if (!f.isFile()) {
                            reportOnce("ref:" + doc, "$ref " + ref, "file not found: " + f.getPath());
                            return null;
                        }
                        docs.put(doc, parse(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8)));
                    }
                } catch (Exception e) {
                    reportOnce("ref:" + ref, "$ref " + ref, "unreadable: " + e.getMessage());
                    return null;
                }
            }
            String key = doc + "#" + pointer;
            if (!seen.add(key)) {
                reportOnce("loop:" + key, "$ref " + ref, "reference loop — ignored");
                return null;
            }
            JsonNode target = pointer.isEmpty() ? docs.get(doc) : docs.get(doc).at(pointer);
            if (target == null || target.isMissingNode()) {
                reportOnce("ref:" + key, "$ref " + ref, "target not found");
                return null;
            }
            cur = new Loc(target, doc, key);
        }
        return cur;
    }

    // ---------------------------------------------------------------- sample synthesis

    JsonNode sample(Loc schema, boolean request) {
        return sample(schema, request, 0, new LinkedHashSet<String>());
    }

    private JsonNode sample(Loc schema, boolean request, int depth, Set<String> stack) {
        Loc s = deref(schema);
        if (s == null || s.node == null || !s.node.isObject()) {
            return s != null && s.node != null && s.node.isBoolean() && s.node.asBoolean() ? NF.textNode("string") : null;
        }
        if (s.key != null && stack.contains(s.key)) return null;   // recursive schema: truncated here
        if (depth > MAX_DEPTH) return null;
        if (s.key != null) stack.add(s.key);
        try {
            JsonNode ex = exampleOf(s);
            if (ex != null) return ex;
            JsonNode n = s.node;
            if (n.has("allOf")) {
                ObjectNode merged = NF.objectNode();
                JsonNode last = null;
                for (JsonNode part : n.get("allOf")) {
                    JsonNode v = sample(new Loc(part, s.doc, null), request, depth + 1, stack);
                    if (v != null && v.isObject()) merged.setAll((ObjectNode) v);
                    else if (v != null) last = v;
                }
                JsonNode own = objectSample(s, request, depth, stack);
                if (own != null && own.isObject()) merged.setAll((ObjectNode) own);
                return merged.size() > 0 || last == null ? merged : last;
            }
            for (String k : new String[] { "oneOf", "anyOf" }) {
                if (n.path(k).size() > 0) {
                    for (JsonNode alt : n.get(k)) {
                        if ("null".equals(alt.path("type").asText())) continue;
                        JsonNode v = sample(new Loc(alt, s.doc, null), request, depth + 1, stack);
                        if (v != null) {
                            JsonNode own = objectSample(s, request, depth, stack);
                            if (v.isObject() && own != null && own.isObject()) ((ObjectNode) v).setAll((ObjectNode) own);
                            return v;
                        }
                    }
                }
            }
            String type = typeOf(n);
            switch (type) {
                case "object": return objectSample(s, request, depth, stack);
                case "array": {
                    ArrayNode a = NF.arrayNode();
                    JsonNode it = sample(new Loc(n.path("items"), s.doc, null), request, depth + 1, stack);
                    if (it != null) {
                        int min = Math.max(1, n.path("minItems").asInt(1));
                        for (int i = 0; i < Math.min(min, 3); i++) a.add(it);
                    }
                    return a;
                }
                case "integer": {
                    long v = 1;
                    if (n.has("minimum")) v = Math.max(v, n.get("minimum").asLong() + (n.path("exclusiveMinimum").asBoolean(false) ? 1 : 0));
                    if (n.path("exclusiveMinimum").isNumber()) v = Math.max(v, n.get("exclusiveMinimum").asLong() + 1);
                    if (n.has("maximum") && v > n.get("maximum").asLong()) v = n.get("maximum").asLong();
                    return NF.numberNode(v);
                }
                case "number": {
                    double v = 1.5;
                    if (n.has("minimum")) v = Math.max(v, n.get("minimum").asDouble());
                    if (n.has("maximum") && v > n.get("maximum").asDouble()) v = n.get("maximum").asDouble();
                    return NF.numberNode(v);
                }
                case "boolean": return NF.booleanNode(true);
                case "null": return NF.nullNode();
                case "string": return NF.textNode(stringSample(n));
                case "file": return NF.textNode("file");
                default: return NF.textNode("string");
            }
        } finally {
            if (s.key != null) stack.remove(s.key);
        }
    }

    private JsonNode objectSample(Loc s, boolean request, int depth, Set<String> stack) {
        JsonNode n = s.node;
        if (!n.has("properties") && !n.has("additionalProperties") && !"object".equals(typeOf(n))) return null;
        ObjectNode o = NF.objectNode();
        for (Iterator<Map.Entry<String, JsonNode>> it = n.path("properties").fields(); it.hasNext();) {
            Map.Entry<String, JsonNode> e = it.next();
            Loc ps = deref(new Loc(e.getValue(), s.doc, null));
            if (ps == null) continue;
            if (request && ps.node.path("readOnly").asBoolean(false)) continue;
            if (!request && ps.node.path("writeOnly").asBoolean(false)) continue;
            JsonNode v = sample(new Loc(e.getValue(), s.doc, null), request, depth + 1, stack);
            if (v != null) o.set(e.getKey(), v);
        }
        JsonNode ap = n.path("additionalProperties");
        if (o.size() == 0 && ap.isObject() && ap.size() > 0) {
            JsonNode v = sample(new Loc(ap, s.doc, null), request, depth + 1, stack);
            if (v != null) o.set("key", v);
        }
        return o;
    }

    /** type, or the first non-null member of a 3.1 type array, or inferred from the keywords present. */
    static String typeOf(JsonNode n) {
        JsonNode t = n.path("type");
        if (t.isTextual()) return t.asText();
        if (t.isArray()) {
            for (JsonNode e : t) if (!"null".equals(e.asText())) return e.asText();
            return "null";
        }
        if (n.has("properties") || n.has("additionalProperties") || n.has("required")) return "object";
        if (n.has("items")) return "array";
        if (n.has("pattern") || n.has("minLength") || n.has("maxLength") || n.has("format")) return "string";
        if (n.has("minimum") || n.has("maximum")) return "number";
        return "";
    }

    static String stringSample(JsonNode n) {
        String f = n.path("format").asText("");
        String v;
        switch (f) {
            case "date-time": v = "2026-01-01T00:00:00Z"; break;
            case "date": v = "2026-01-01"; break;
            case "time": v = "00:00:00Z"; break;
            case "email": case "idn-email": v = "user@example.com"; break;
            case "uuid": v = "3fa85f64-5717-4562-b3fc-2c963f66afa6"; break;
            case "uri": case "url": case "iri": v = "https://example.com"; break;
            case "hostname": case "idn-hostname": v = "example.com"; break;
            case "ipv4": v = "192.0.2.1"; break;
            case "ipv6": v = "2001:db8::1"; break;
            case "byte": v = "c3RyaW5n"; break;
            case "password": v = "password"; break;
            case "duration": v = "P1D"; break;
            default: v = "string";
        }
        int min = n.path("minLength").asInt(0);
        int max = n.path("maxLength").asInt(Integer.MAX_VALUE);
        StringBuilder b = new StringBuilder(v);
        while (b.length() < min) b.append('x');
        return b.length() > max ? b.substring(0, Math.max(max, 0)) : b.toString();
    }

    private static boolean isBinary(Loc s) {
        if (s == null || !s.node.isObject()) return false;
        String f = s.node.path("format").asText("");
        return "binary".equals(f) || "file".equals(typeOf(s.node)) || s.node.has("contentMediaType") && !s.node.has("contentEncoding");
    }

    // ---------------------------------------------------------------- helpers

    /** Preferred media type: application/json, then *+json, then the fallback, then the first listed. */
    static String pickMedia(List<String> types, String fallback) {
        for (String t : types) if (t.toLowerCase(Locale.ROOT).startsWith("application/json")) return t;
        for (String t : types) if (t.toLowerCase(Locale.ROOT).matches("[a-z]+/[^;]*\\+json.*")) return t;
        if (types.isEmpty()) return fallback == null ? "application/json" : fallback;
        if (fallback != null && types.contains(fallback)) return fallback;
        return types.get(0);
    }

    private static List<String> strings(JsonNode arr) {
        List<String> out = new ArrayList<String>();
        for (JsonNode n : arr) out.add(n.asText());
        return out;
    }

    private static JsonNode firstNonNull(JsonNode... ns) {
        for (JsonNode n : ns) if (n != null && !n.isMissingNode()) return n;
        return null;
    }

    static String scalar(JsonNode v) {
        if (v == null || v.isNull()) return "";
        if (v.isValueNode()) return v.asText();
        if (v.isArray()) {
            List<String> p = new ArrayList<String>();
            for (JsonNode e : v) p.add(scalar(e));
            return String.join(",", p);
        }
        return v.toString();
    }

    private static boolean looksJson(String s) {
        String t = s.trim();
        return (t.startsWith("{") && t.endsWith("}")) || (t.startsWith("[") && t.endsWith("]"));
    }

    private static String firstLine(String s) {
        String t = s == null ? "" : s.trim();
        int nl = t.indexOf('\n');
        t = nl < 0 ? t : t.substring(0, nl).trim();
        return t.length() > 200 ? t.substring(0, 200) : t;
    }

    /** Query component encoding; ${var} placeholders are kept literal. */
    static String enc(String s) {
        if (s.startsWith("${") && s.endsWith("}")) return s;
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    static String encPath(String s) {
        return enc(s).replace("%2C", ",").replace("%3A", ":").replace("%40", "@");
    }

    private void reportOnce(String key, String construct, String reason) {
        if (reported.add(key)) res.unsupported(0, construct, reason);
    }
}
