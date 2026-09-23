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
import java.util.Set;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Q2 — HTTP Archive (browser dev tools "Save all as HAR", proxies, Playwright's {@code recordHar}) → {@link ApiRequest}s,
 * one per API call, in recorded order.
 *
 * <p>Kept: XHR/fetch calls (and page documents) — or, when the HAR has no {@code _resourceType}, everything that is
 * not a static asset by MIME type or extension. The recorded response status becomes the expected status (304 → 200,
 * since the replayed request carries no validators). Browser noise is dropped: HTTP/2 pseudo-headers, Host,
 * Content-Length, Connection, sec-*, priority, conditional headers, and Accept-Encoding asking for encodings GATF does
 * not decode. Cookie headers are dropped too — GATF keeps its own cookie jar from Set-Cookie responses — and counted in
 * the stats so a recording that starts mid-session is noticed. An optional {@code filter} regex keeps only matching
 * URLs (e.g. one API host).
 */
public class HarImporter {

    private static final ObjectMapper OM = new ObjectMapper();

    private static final Set<String> KEEP_TYPES = Set.of("xhr", "fetch", "document");
    private static final Pattern STATIC_EXT = Pattern.compile(
            "(?i).*\\.(png|jpe?g|gif|svg|ico|webp|avif|bmp|css|js|mjs|map|woff2?|ttf|otf|eot|mp4|webm|mp3|wav|ogg|wasm)(\\?.*)?$");
    private static final Pattern STATIC_MIME = Pattern.compile(
            "(?i)^(image/|font/|audio/|video/|text/css|(application|text)/(x-)?javascript|application/wasm|application/font).*");
    private static final Set<String> DROP = Set.of("host", "content-length", "connection", "keep-alive", "priority",
            "upgrade-insecure-requests", "if-none-match", "if-modified-since", "if-match", "if-unmodified-since",
            "if-range", "te", "trailer", "transfer-encoding", "upgrade", "proxy-connection");

    private final Pattern filter;

    /** @param filter optional regex; only request URLs it finds a match in are imported */
    public HarImporter(String filter) {
        this.filter = filter == null || filter.isBlank() ? null : Pattern.compile(filter);
    }

    public List<ApiRequest> read(String source, ImportResult res) throws Exception {
        JsonNode root = OM.readTree(source);
        JsonNode entries = root == null ? null : root.path("log").get("entries");
        if (entries == null || !entries.isArray()) throw new IllegalArgumentException("not a HAR file (no log.entries)");

        List<ApiRequest> out = new ArrayList<ApiRequest>();
        Map<String, Integer> skipped = new LinkedHashMap<String, Integer>();
        Map<String, Integer> origins = new LinkedHashMap<String, Integer>();
        int cookies = 0, idx = 0;
        for (JsonNode e : entries) {
            idx++;
            JsonNode q = e.path("request");
            String url = q.path("url").asText("");
            if (!url.matches("(?i)^https?://.*")) { count(skipped, "non-http"); continue; }
            if (filter != null && !filter.matcher(url).find()) { count(skipped, "filtered"); continue; }
            String type = e.path("_resourceType").asText("").toLowerCase(Locale.ROOT);
            String mime = e.path("response").path("content").path("mimeType").asText("");
            if (!type.isEmpty()) {
                if (!KEEP_TYPES.contains(type)) { count(skipped, type); continue; }
            } else if (STATIC_EXT.matcher(url).matches() || STATIC_MIME.matcher(mime).matches()) {
                count(skipped, "static"); continue;
            }
            String method = q.path("method").asText("GET").toUpperCase(Locale.ROOT);
            if (method.equals("OPTIONS")) { count(skipped, "preflight"); continue; }

            ApiRequest r = new ApiRequest();
            r.line = idx;
            r.method = method;
            r.setFullUrl(url);
            r.name = method + " " + pathOf(r.url);
            origins.merge(r.baseUrl == null ? "?" : r.baseUrl, 1, Integer::sum);

            for (JsonNode h : arr(q.get("headers"))) {
                String name = h.path("name").asText(""), value = h.path("value").asText("");
                String ln = name.toLowerCase(Locale.ROOT);
                if (name.isEmpty() || name.startsWith(":") || DROP.contains(ln) || ln.startsWith("sec-")) continue;
                if (ln.equals("cookie")) { cookies++; continue; }
                if (ln.equals("accept-encoding") && !gatfDecodes(value)) continue;
                r.putHeader(name, value);
            }

            JsonNode pd = q.get("postData");
            if (pd != null && !pd.isNull()) {
                String pmime = pd.path("mimeType").asText("");
                String text = pd.path("text").asText("");
                if ("base64".equals(pd.path("encoding").asText()) && !text.isEmpty()) {
                    text = new String(Base64.getDecoder().decode(text), StandardCharsets.UTF_8);
                }
                if (pmime.toLowerCase(Locale.ROOT).startsWith("multipart/form-data") && pd.has("params") && pd.get("params").size() > 0) {
                    for (JsonNode p : pd.get("params")) {
                        String n = p.path("name").asText(""), v = p.path("value").asText("");
                        String fn = p.path("fileName").asText("");
                        if (!fn.isEmpty()) {
                            if (ApiRequest.formPartExpressible(n, fn)) {
                                r.formParts.add(n + ":file:" + fn);
                                res.unsupported(idx, r.name + " file part " + n, "file content is not in the HAR — place '" + fn + "' under the test-case directory");
                            } else {
                                res.unsupported(idx, r.name + " file part " + n, "GATF multipart parts cannot contain ':' — add by hand");
                            }
                        } else if (!v.isEmpty() && ApiRequest.formPartExpressible(n, v)) {
                            String ct = p.path("contentType").asText("text/plain");
                            r.formParts.add(n + ":text:" + v + ":" + (ct.isEmpty() ? "text/plain" : ct));
                        } else {
                            res.unsupported(idx, r.name + " form field " + n, "empty or contains ':' — add by hand");
                        }
                    }
                } else if (!text.isEmpty()) {
                    r.body = text;                         // incl. multipart without params: body + its boundary header replay as-is
                } else if (pd.has("params") && pd.get("params").size() > 0) {
                    List<String> kv = new ArrayList<String>();
                    for (JsonNode p : pd.get("params")) {
                        kv.add(URLEncoder.encode(p.path("name").asText(""), StandardCharsets.UTF_8) + "="
                                + URLEncoder.encode(p.path("value").asText(""), StandardCharsets.UTF_8));
                    }
                    r.body = String.join("&", kv);
                }
                if (r.hasBody() && r.header("Content-Type") == null && !pmime.isEmpty()) r.putHeader("Content-Type", pmime);
            }
            r.normalizeContentType("application/octet-stream");

            int status = e.path("response").path("status").asInt(0);
            r.expectedStatus = status == 304 ? 200 : (status >= 100 && status < 600 ? status : 0);
            out.add(r);
        }
        res.stat("format", "har");
        res.stat("entries", entries.size());
        res.stat("requests", out.size());
        if (!skipped.isEmpty()) res.stat("skipped", skipped);
        if (!origins.isEmpty()) res.stat("origins", origins);
        if (cookies > 0) res.stat("cookieHeadersDropped", cookies);
        return out;
    }

    /** JdkGatfResponse decodes gzip and deflate only. */
    static boolean gatfDecodes(String acceptEncoding) {
        for (String enc : acceptEncoding.split(",")) {
            String e = enc.trim().replaceAll(";.*$", "").toLowerCase(Locale.ROOT);
            if (!e.isEmpty() && !e.equals("gzip") && !e.equals("deflate") && !e.equals("identity")) return false;
        }
        return true;
    }

    private static String pathOf(String url) {
        int q = url.indexOf('?');
        String p = q < 0 ? url : url.substring(0, q);
        return p.length() > 80 ? p.substring(0, 77) + "..." : p;
    }

    private static void count(Map<String, Integer> m, String k) {
        m.merge(k, 1, Integer::sum);
    }

    private static Iterable<JsonNode> arr(JsonNode n) {
        List<JsonNode> l = new ArrayList<JsonNode>();
        if (n != null && n.isArray()) n.forEach(l::add);
        return l;
    }
}
