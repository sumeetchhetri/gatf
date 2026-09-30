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
package com.gatf.selenium.route;

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
import com.gatf.imports.HarImporter;

/**
 * M4 — {@code route from har <file> [url <glob>] [notfound abort|fallback]}: replays recorded responses by method +
 * URL (exact URL first, then the same URL without its query string; the first recorded entry wins). A request with
 * no recording is let through ({@code fallback}, the default) or aborted ({@code notfound abort}). Entries without a
 * response (status 0) or with 304 (no body to replay) are skipped. Reads HAR through {@link HarImporter#entries}.
 */
public final class HarRoutes implements RouteTable.Rule {

    private static final Set<String> DROP = Set.of("content-encoding", "content-length", "transfer-encoding", "connection", "keep-alive");

    private final String file;
    private final String glob;
    private final Pattern regex;
    private final boolean abortMissing;
    private final Map<String, Route> exact = new LinkedHashMap<String, Route>();
    private final Map<String, Route> noQuery = new LinkedHashMap<String, Route>();
    private int size;

    private HarRoutes(String file, String glob, boolean abortMissing) {
        this.file = file;
        this.glob = glob == null ? "**" : glob;
        this.regex = Glob.toRegex(this.glob);
        this.abortMissing = abortMissing;
    }

    public static HarRoutes parse(String source, String file, String glob, boolean abortMissing) {
        HarRoutes h = new HarRoutes(file, glob, abortMissing);
        JsonNode entries;
        try {
            entries = HarImporter.entries(source);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("route from har " + file + ": " + e.getMessage());
        } catch (Exception e) {
            throw new IllegalArgumentException("route from har " + file + ": not valid JSON (" + e.getMessage() + ")");
        }
        for (JsonNode e : entries) {
            JsonNode q = e.path("request"), r = e.path("response");
            String method = q.path("method").asText("GET").toUpperCase(Locale.ROOT);
            String url = q.path("url").asText("");
            int status = r.path("status").asInt(0);
            if (url.isEmpty() || status == 0 || status == 304) continue;
            Map<String, String> headers = new LinkedHashMap<String, String>();
            for (JsonNode hd : r.path("headers")) {
                String n = hd.path("name").asText("");
                if (n.isEmpty() || n.startsWith(":") || DROP.contains(n.toLowerCase(Locale.ROOT))) continue;
                headers.putIfAbsent(n, hd.path("value").asText(""));
            }
            JsonNode c = r.path("content");
            String mime = c.path("mimeType").asText("");
            boolean hasType = false;
            for (String k : headers.keySet()) hasType |= k.equalsIgnoreCase("content-type");
            if (!hasType && !mime.isEmpty()) headers.put("Content-Type", mime);
            String text = c.path("text").asText("");
            byte[] body = "base64".equalsIgnoreCase(c.path("encoding").asText("")) ? Base64.getDecoder().decode(text.replaceAll("\\s", ""))
                    : text.getBytes(StandardCharsets.UTF_8);
            Route d = Route.fulfilled(h.glob, status, headers, body, "har " + file);
            h.exact.putIfAbsent(method + " " + url, d);
            h.noQuery.putIfAbsent(method + " " + stripQuery(url), d);
            h.size++;
        }
        return h;
    }

    static String stripQuery(String url) {
        int i = url.indexOf('#');
        String u = i >= 0 ? url.substring(0, i) : url;
        int q = u.indexOf('?');
        return q >= 0 ? u.substring(0, q) : u;
    }

    public Route decide(String method, String url) {
        if (url == null || !regex.matcher(url).matches()) return null;
        String m = method == null ? "GET" : method.toUpperCase(Locale.ROOT);
        Route d = exact.get(m + " " + url);
        if (d == null) d = noQuery.get(m + " " + stripQuery(url));
        if (d != null) return d;
        if (!abortMissing) return null;
        Route a = new Route(null, glob, Route.Action.ABORT);
        a.reason = "failed";
        a.source = "har " + file + " (not recorded)";
        return a;
    }

    public String glob() {
        return glob;
    }

    public String describe() {
        return "har " + file + " (" + size + " responses" + (glob.equals("**") ? "" : ", url " + glob) + (abortMissing ? ", notfound abort" : "") + ")";
    }

    public int size() {
        return size;
    }

    /** method + URL keys, for tests. */
    List<String> keys() {
        return new ArrayList<String>(exact.keySet());
    }
}
