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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Q2 — Postman collection (v2.0 / v2.1, and the v1 format GATF's own generator exports) → {@link ApiRequest}s.
 *
 * <p>Carried over: folder structure (as "Folder / Request" names), method, URL incl. path variables, enabled headers
 * and query parameters, raw / urlencoded / form-data / GraphQL bodies, basic / bearer / API-key auth with folder and
 * collection inheritance, collection variables (returned for gatf-config {@code globalVariables}) and status-code
 * assertions from test scripts. {@code {{var}}} becomes GATF's {@code ${var}}. Everything else — other test
 * assertions, pre-request scripts, other auth types, dynamic variables — is listed in the unsupported report.
 */
public class PostmanImporter {

    private static final ObjectMapper OM = new ObjectMapper();

    private ImportResult res;
    private final List<ApiRequest> out = new ArrayList<ApiRequest>();
    private int seq;

    public List<ApiRequest> read(String source, ImportResult res) throws Exception {
        this.res = res;
        JsonNode root = OM.readTree(source);
        if (root == null || !root.isObject()) throw new IllegalArgumentException("not a Postman collection (expected a JSON object)");
        if (root.has("collection") && root.get("collection").isObject()) root = root.get("collection");   // Postman API wrapper

        Map<String, Object> vars = new LinkedHashMap<String, Object>();
        for (JsonNode v : arr(root.get("variable"))) {
            if (v.path("disabled").asBoolean(false) || text(v, "key") == null) continue;
            vars.put(text(v, "key"), v.path("value").isValueNode() ? v.path("value").asText() : v.path("value").toString());
        }
        if (!vars.isEmpty()) res.variables = vars;

        if (root.has("item")) {
            res.stat("format", "postman v2");
            scripts(root, "collection");
            walk(root.get("item"), "", root.get("auth"));
        } else if (root.has("requests")) {
            res.stat("format", "postman v1");
            for (JsonNode r : arr(root.get("requests"))) v1(r);
        } else {
            throw new IllegalArgumentException("not a Postman collection (no 'item' or 'requests')");
        }
        res.stat("requests", out.size());
        return out;
    }

    // ------------------------------------------------------------------ v2

    private void walk(JsonNode items, String prefix, JsonNode inheritedAuth) {
        for (JsonNode it : arr(items)) {
            String name = text(it, "name");
            if (name == null) name = "Request " + (seq + 1);
            if (it.has("item")) {                                  // folder
                JsonNode auth = it.has("auth") && !it.get("auth").isNull() ? it.get("auth") : inheritedAuth;
                scripts(it, "folder '" + name + "'");
                walk(it.get("item"), prefix + name + " / ", auth);
                continue;
            }
            seq++;
            JsonNode req = it.get("request");
            if (req == null || req.isNull()) {
                res.unsupported(seq, prefix + name, "item without a request — skipped");
                continue;
            }
            ApiRequest r = new ApiRequest();
            r.line = seq;
            r.name = prefix + name;
            if (req.isTextual()) {                                 // "request": "https://…" (GET)
                r.setFullUrl(ApiRequest.gatfVars(req.asText()));
                out.add(r);
                continue;
            }
            r.description = description(req.get("description"));
            if (r.description == null) r.description = description(it.get("description"));
            r.method = req.path("method").asText("GET").toUpperCase(Locale.ROOT);

            String url = url(req.get("url"), r);
            headers(req.get("header"), r);
            JsonNode auth = req.has("auth") && !req.get("auth").isNull() ? req.get("auth") : inheritedAuth;
            url = auth(auth, r, url);
            if (ApiRequest.hasDynamicVar(url)) res.unsupported(seq, r.name + " url", "Postman dynamic variable — define it in GATF");
            r.setFullUrl(ApiRequest.gatfVars(url));
            body(req.get("body"), r);
            r.expectedStatus = statusFromTests(it.get("event"), r.name);
            r.normalizeContentType("text/plain");
            out.add(r);
        }
    }

    private String url(JsonNode u, ApiRequest r) {
        if (u == null || u.isNull()) return "";
        if (u.isTextual()) return u.asText();
        String raw = text(u, "raw");
        if (raw == null) {
            StringBuilder b = new StringBuilder();
            String proto = text(u, "protocol");
            if (proto != null) b.append(proto).append("://");
            JsonNode host = u.get("host");
            b.append(host != null && host.isArray() ? join(host, ".") : (host == null ? "" : host.asText()));
            if (text(u, "port") != null) b.append(':').append(text(u, "port"));
            JsonNode path = u.get("path");
            String p = path != null && path.isArray() ? join(path, "/") : (path == null ? "" : path.asText());
            if (!p.isEmpty()) b.append(p.startsWith("/") ? "" : "/").append(p);
            List<String> q = new ArrayList<String>();
            for (JsonNode qp : arr(u.get("query"))) {
                if (qp.path("disabled").asBoolean(false) || text(qp, "key") == null) continue;
                q.add(text(qp, "key") + (qp.path("value").isNull() ? "" : "=" + qp.path("value").asText("")));
            }
            if (!q.isEmpty()) b.append('?').append(String.join("&", q));
            raw = b.toString();
        }   // else: Postman keeps raw in sync with the enabled query params
        for (JsonNode pv : arr(u.get("variable"))) {             // :id path variables
            String key = text(pv, "key");
            if (key == null) continue;
            String val = pv.path("value").asText("");
            String rep = val.isEmpty() ? "{{" + key + "}}" : val;
            raw = raw.replaceAll("(?<=/):" + Pattern.quote(key) + "(?=/|\\?|#|$)", Matcher.quoteReplacement(rep));
        }
        return raw;
    }

    private void headers(JsonNode h, ApiRequest r) {
        if (h == null || h.isNull()) return;
        if (h.isTextual()) {                                      // "K: V\nK2: V2"
            for (String line : h.asText().split("\\r?\\n")) {
                int c = line.indexOf(':');
                if (c > 0) r.putHeader(line.substring(0, c).trim(), ApiRequest.gatfVars(line.substring(c + 1).trim()));
            }
            return;
        }
        for (JsonNode e : arr(h)) {
            if (e.path("disabled").asBoolean(false) || text(e, "key") == null) continue;
            String v = e.path("value").asText("");
            if (ApiRequest.hasDynamicVar(v)) res.unsupported(seq, r.name + " header " + text(e, "key"), "Postman dynamic variable — define it in GATF");
            r.putHeader(text(e, "key"), ApiRequest.gatfVars(v));
        }
    }

    /** Applies auth; returns the (possibly query-extended) URL. */
    private String auth(JsonNode a, ApiRequest r, String url) {
        if (a == null || a.isNull()) return url;
        String type = a.path("type").asText("noauth");
        Map<String, String> p = authParams(a.get(type));
        switch (type) {
            case "noauth":
                return url;
            case "bearer":
                if (r.header("Authorization") == null) r.putHeader("Authorization", "Bearer " + ApiRequest.gatfVars(p.getOrDefault("token", "")));
                return url;
            case "basic": {
                String u = p.getOrDefault("username", ""), pw = p.getOrDefault("password", "");
                if (u.contains("{{") || pw.contains("{{")) {
                    res.unsupported(seq, r.name + " basic auth", "username/password use variables — add an Authorization: Basic header");
                } else if (r.header("Authorization") == null) {
                    r.putHeader("Authorization", "Basic " + Base64.getEncoder().encodeToString((u + ":" + pw).getBytes(StandardCharsets.UTF_8)));
                }
                return url;
            }
            case "apikey": {
                String key = p.getOrDefault("key", ""), val = ApiRequest.gatfVars(p.getOrDefault("value", ""));
                if (key.isEmpty()) return url;
                if ("query".equals(p.get("in"))) return url + (url.indexOf('?') >= 0 ? "&" : "?") + key + "=" + val;
                if (r.header(key) == null) r.putHeader(key, val);
                return url;
            }
            default:
                res.unsupported(seq, r.name + " auth", "'" + type + "' auth is not converted — set the header or GATF auth config");
                return url;
        }
    }

    /** v2.1: [{key,value}]; v2.0: {key: value}. */
    private static Map<String, String> authParams(JsonNode n) {
        Map<String, String> m = new LinkedHashMap<String, String>();
        if (n == null) return m;
        if (n.isArray()) {
            for (JsonNode e : n) if (text(e, "key") != null) m.put(text(e, "key"), e.path("value").asText(""));
        } else if (n.isObject()) {
            n.fields().forEachRemaining(e -> m.put(e.getKey(), e.getValue().asText("")));
        }
        return m;
    }

    private void body(JsonNode b, ApiRequest r) {
        if (b == null || b.isNull() || b.path("disabled").asBoolean(false)) return;
        String mode = b.path("mode").asText("");
        switch (mode) {
            case "raw": {
                String raw = b.path("raw").asText("");
                if (raw.isEmpty()) return;
                r.body = ApiRequest.gatfVars(raw);
                if (ApiRequest.hasDynamicVar(raw)) res.unsupported(seq, r.name + " body", "Postman dynamic variable — define it in GATF");
                if (r.header("Content-Type") == null) {
                    String lang = b.path("options").path("raw").path("language").asText("text");
                    r.putHeader("Content-Type", rawType(lang));
                }
                return;
            }
            case "urlencoded": {
                List<String> kv = new ArrayList<String>();
                for (JsonNode e : arr(b.get("urlencoded"))) {
                    if (e.path("disabled").asBoolean(false) || text(e, "key") == null) continue;
                    kv.add(formEnc(text(e, "key")) + "=" + formEnc(e.path("value").asText("")));
                }
                if (kv.isEmpty()) return;
                r.body = String.join("&", kv);
                if (r.header("Content-Type") == null) r.putHeader("Content-Type", "application/x-www-form-urlencoded");
                return;
            }
            case "formdata":
                for (JsonNode e : arr(b.get("formdata"))) {
                    if (e.path("disabled").asBoolean(false) || text(e, "key") == null) continue;
                    String key = text(e, "key");
                    if ("file".equals(e.path("type").asText())) {
                        JsonNode src = e.get("src");
                        String path = src == null || src.isNull() ? "" : (src.isArray() ? (src.size() > 0 ? src.get(0).asText() : "") : src.asText());
                        if (path.isEmpty() || !ApiRequest.formPartExpressible(key, path)) {
                            res.unsupported(seq, r.name + " form file " + key, "no usable file path — add the part by hand");
                            continue;
                        }
                        r.formParts.add(key + ":file:" + path);
                    } else {
                        String v = ApiRequest.gatfVars(e.path("value").asText(""));
                        String ct = e.path("contentType").asText("text/plain");
                        if (v.isEmpty() || !ApiRequest.formPartExpressible(key, v)) {
                            res.unsupported(seq, r.name + " form field " + key, v.isEmpty() ? "empty text part — GATF skips it"
                                    : "GATF multipart parts cannot contain ':' — add this part by hand");
                            continue;
                        }
                        r.formParts.add(key + ":text:" + v + ":" + ct);
                    }
                }
                return;
            case "graphql": {
                ObjectNode gq = OM.createObjectNode();
                gq.put("query", b.path("graphql").path("query").asText(""));
                String vars = b.path("graphql").path("variables").asText("");
                try {
                    gq.set("variables", vars.isBlank() ? OM.createObjectNode() : OM.readTree(vars));
                } catch (Exception e) {
                    gq.put("variables", vars);
                    res.unsupported(seq, r.name + " graphql variables", "variables are not valid JSON — sent as a string");
                }
                r.body = ApiRequest.gatfVars(gq.toString());
                if (r.header("Content-Type") == null) r.putHeader("Content-Type", "application/json");
                return;
            }
            case "file":
                res.unsupported(seq, r.name + " body", "binary file body — set contentFile on the test case");
                return;
            default:
        }
    }

    /** Encodes a urlencoded key/value, leaving {{var}} placeholders intact (as ${var}). */
    private static String formEnc(String s) {
        StringBuilder b = new StringBuilder();
        Matcher m = Pattern.compile("\\{\\{[^}]+\\}\\}").matcher(s);
        int last = 0;
        while (m.find()) {
            b.append(URLEncoder.encode(s.substring(last, m.start()), StandardCharsets.UTF_8));
            b.append(ApiRequest.gatfVars(m.group()));
            last = m.end();
        }
        b.append(URLEncoder.encode(s.substring(last), StandardCharsets.UTF_8));
        return b.toString();
    }

    private static String rawType(String lang) {
        switch (lang) {
            case "json": return "application/json";
            case "xml": return "application/xml";
            case "html": return "text/html";
            case "javascript": return "application/javascript";
            default: return "text/plain";
        }
    }

    // ------------------------------------------------------------------ scripts

    private static final Pattern[] STATUS = {
        Pattern.compile("pm\\.response\\.to\\.have\\.status\\(\\s*(\\d{3})\\s*\\)"),
        Pattern.compile("pm\\.response\\.to\\.be\\.(ok)\\b"),
        Pattern.compile("pm\\.expect\\(\\s*pm\\.response\\.(?:code|status)\\s*\\)\\.to\\.(?:be\\.)?(?:eql|equal|equals)\\(\\s*(\\d{3})\\s*\\)"),
        Pattern.compile("responseCode\\.code\\s*===?\\s*(\\d{3})"),
    };

    /** Status code asserted by the item's test script (0 when none); reports any other script content. */
    private int statusFromTests(JsonNode events, String name) {
        int status = 0;
        for (JsonNode ev : arr(events)) {
            String src = script(ev);
            if (src.isBlank()) continue;
            String listen = ev.path("listen").asText();
            if (!"test".equals(listen)) {
                res.unsupported(seq, name + " " + listen + " script", "scripts are not translated — use GATF hooks/plugins");
                continue;
            }
            boolean other = false;
            for (String line : src.split("\\r?\\n")) {
                String l = line.trim();
                if (l.isEmpty() || l.startsWith("//")) continue;
                int found = 0;
                for (Pattern p : STATUS) {
                    Matcher m = p.matcher(l);
                    if (m.find()) found = "ok".equals(m.group(1)) ? 200 : Integer.parseInt(m.group(1));
                }
                if (found > 0) status = found;
                else if (!isScaffolding(l)) other = true;
            }
            if (other) res.unsupported(seq, name + " test script", "only status-code assertions are translated — add expectedNodes for the rest");
        }
        return status;
    }

    /** pm.test("…", function () { … }); wrappers and JSON.parse lines carry no assertion of their own. */
    private static boolean isScaffolding(String l) {
        return l.matches("pm\\.test\\(.*(function\\s*\\(\\s*\\)|=>)\\s*\\{\\s*") || l.matches("\\}\\s*\\)\\s*;?")
                || l.matches("(var|let|const)\\s+\\w+\\s*=\\s*(JSON\\.parse\\(responseBody\\)|pm\\.response\\.json\\(\\))\\s*;?")
                || l.matches("tests\\[.*\\]\\s*=\\s*responseCode\\.code\\s*===?\\s*\\d{3}\\s*;?");
    }

    private void scripts(JsonNode container, String where) {
        for (JsonNode ev : arr(container.get("event"))) {
            if (!script(ev).isBlank()) {
                res.unsupported(0, where + " " + ev.path("listen").asText() + " script", "collection/folder scripts are not translated");
            }
        }
    }

    private static String script(JsonNode ev) {
        JsonNode exec = ev.path("script").path("exec");
        if (exec.isArray()) return join(exec, "\n");
        return exec.asText("");
    }

    // ------------------------------------------------------------------ v1 (incl. GATF's own export)

    private void v1(JsonNode q) {
        seq++;
        ApiRequest r = new ApiRequest();
        r.line = seq;
        r.name = text(q, "name") == null ? "Request " + seq : text(q, "name");
        r.description = description(q.get("description"));
        r.method = q.path("method").asText("GET").toUpperCase(Locale.ROOT);
        headers(q.get("headers"), r);
        r.setFullUrl(ApiRequest.gatfVars(q.path("url").asText("")));
        String mode = q.path("dataMode").asText("");
        JsonNode data = q.get("data");
        String raw = text(q, "rawModeData");
        if (raw == null && data != null && data.isTextual()) raw = data.asText();
        if ("raw".equals(mode) || (raw != null && !raw.isEmpty() && !"params".equals(mode) && !"urlencoded".equals(mode))) {
            if (raw != null && !raw.isEmpty()) r.body = ApiRequest.gatfVars(raw);
        } else if (data != null && data.isArray()) {
            List<String> kv = new ArrayList<String>();
            for (JsonNode e : data) {
                if (!e.path("enabled").asBoolean(true) || text(e, "key") == null) continue;
                String v = e.path("value").asText("");
                if ("params".equals(mode) && "file".equals(e.path("type").asText())) {
                    if (ApiRequest.formPartExpressible(text(e, "key"), v) && !v.isEmpty()) r.formParts.add(text(e, "key") + ":file:" + v);
                    else res.unsupported(seq, r.name + " form file " + text(e, "key"), "no usable file path — add the part by hand");
                } else if ("params".equals(mode)) {
                    if (!v.isEmpty() && ApiRequest.formPartExpressible(text(e, "key"), v)) r.formParts.add(text(e, "key") + ":text:" + ApiRequest.gatfVars(v) + ":text/plain");
                    else res.unsupported(seq, r.name + " form field " + text(e, "key"), "empty or contains ':' — add by hand");
                } else {
                    kv.add(formEnc(text(e, "key")) + "=" + formEnc(v));
                }
            }
            if (!kv.isEmpty()) {
                r.body = String.join("&", kv);
                if (r.header("Content-Type") == null) r.putHeader("Content-Type", "application/x-www-form-urlencoded");
            }
        }
        String tests = text(q, "tests");
        if (tests != null) {
            ObjectNode ev = OM.createObjectNode();
            ev.put("listen", "test");
            ev.putObject("script").put("exec", tests);
            r.expectedStatus = statusFromTests(OM.createArrayNode().add(ev), r.name);
        }
        String pre = text(q, "preRequestScript");
        if (pre != null && !pre.isBlank()) res.unsupported(seq, r.name + " prerequest script", "scripts are not translated — use GATF hooks/plugins");
        r.normalizeContentType("text/plain");
        out.add(r);
    }

    // ------------------------------------------------------------------ json helpers

    private static Iterable<JsonNode> arr(JsonNode n) {
        List<JsonNode> l = new ArrayList<JsonNode>();
        if (n != null && n.isArray()) n.forEach(l::add);
        return l;
    }

    private static String text(JsonNode n, String field) {
        if (n == null) return null;
        JsonNode v = n.get(field);
        return v == null || v.isNull() || !v.isValueNode() ? null : v.asText();
    }

    private static String join(JsonNode a, String sep) {
        List<String> parts = new ArrayList<String>();
        a.forEach(e -> parts.add(e.asText()));
        return String.join(sep, parts);
    }

    private static String description(JsonNode d) {
        if (d == null || d.isNull()) return null;
        String s = d.isObject() ? d.path("content").asText("") : d.asText("");
        return s.isBlank() ? null : s;
    }
}
