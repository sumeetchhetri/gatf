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
 * URL (exact URL first, then the same URL without its query string, picking the entry whose query parameters overlap
 * best: matched pairs minus recorded pairs the request lacks). Among equally good entries the one whose recorded
 * {@code postData.text} equals the request body wins (when the transport knows the body), else the earliest. A request with
 * no recording is let through ({@code fallback}, the default) or aborted ({@code notfound abort}). Entries without a
 * response (status 0) or with 304 (no body to replay) are skipped. Reads HAR through {@link HarImporter#entries}.
 */
public final class HarRoutes implements RouteTable.Rule {

    private static final Set<String> DROP = Set.of("content-encoding", "content-length", "transfer-encoding", "connection", "keep-alive");

    private final String file;
    private final String glob;
    private final Pattern regex;
    private final boolean abortMissing;
    private final Map<String, List<Entry>> exact = new LinkedHashMap<String, List<Entry>>();
    private final Map<String, List<Entry>> noQuery = new LinkedHashMap<String, List<Entry>>();
    private final Route notRecorded;
    private int size;

    /** one recorded response with what it is matched on */
    static final class Entry {
        final List<String> query;
        final String postData;
        final Route route;

        Entry(List<String> query, String postData, Route route) {
            this.query = query;
            this.postData = postData;
            this.route = route;
        }
    }

    private HarRoutes(String file, String glob, boolean abortMissing) {
        this.file = file;
        this.glob = glob == null ? "**" : glob;
        this.regex = Glob.toRegex(this.glob);
        this.abortMissing = abortMissing;
        this.notRecorded = new Route(null, this.glob, Route.Action.ABORT);
        notRecorded.reason = "failed";
        notRecorded.source = "har " + file + " (not recorded)";
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
        int n = -1;
        for (JsonNode e : entries) {
            n++;
            JsonNode q = e.path("request"), r = e.path("response");
            String method = q.path("method").asText("GET").toUpperCase(Locale.ROOT);
            String url = q.path("url").asText("");
            int status = r.path("status").asInt(0);
            if (url.isEmpty() || status == 0 || status == 304) continue;
            Route hr = new Route(null, h.glob, Route.Action.FULFILL);
            for (JsonNode hd : r.path("headers")) {
                String hn = hd.path("name").asText("");
                if (hn.isEmpty() || hn.startsWith(":") || DROP.contains(hn.toLowerCase(Locale.ROOT))) continue;
                hr.addHeader(hn, hd.path("value").asText(""));   // repeated names (Set-Cookie) are all kept
            }
            Map<String, String> headers = hr.headers;
            JsonNode c = r.path("content");
            String mime = c.path("mimeType").asText("");
            boolean hasType = false;
            for (String k : headers.keySet()) hasType |= k.equalsIgnoreCase("content-type");
            if (!hasType && !mime.isEmpty()) headers.put("Content-Type", mime);
            String text = c.path("text").asText("");
            byte[] body;
            try {
                body = "base64".equalsIgnoreCase(c.path("encoding").asText("")) ? Base64.getDecoder().decode(text.replaceAll("\\s", ""))
                        : text.getBytes(StandardCharsets.UTF_8);
            } catch (IllegalArgumentException bad) {
                throw new IllegalArgumentException("route from har " + file + ": entry " + n + ": " + bad.getMessage());
            }
            Route d = Route.fulfilled(h.glob, status, headers, hr.extraHeaders, body, "har " + file);
            JsonNode pd = q.path("postData").path("text");
            Entry en = new Entry(queryPairs(url), pd.isTextual() ? pd.asText() : null, d);
            h.exact.computeIfAbsent(method + " " + url, k -> new ArrayList<Entry>()).add(en);
            h.noQuery.computeIfAbsent(method + " " + stripQuery(url), k -> new ArrayList<Entry>()).add(en);
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

    /** The query string's {@code name=value} pairs, as written (no decoding). */
    static List<String> queryPairs(String url) {
        int i = url.indexOf('#');
        String u = i >= 0 ? url.substring(0, i) : url;
        int q = u.indexOf('?');
        List<String> out = new ArrayList<String>();
        if (q < 0) return out;
        for (String p : u.substring(q + 1).split("&")) if (!p.isEmpty()) out.add(p.indexOf('=') < 0 ? p + "=" : p);
        return out;
    }

    /**
     * The best of the candidates for this request: highest query score (pairs the request shares minus recorded pairs
     * it lacks; {@code byQuery} only), then a recorded POST body equal to {@code body}, then the earliest.
     */
    static Route pick(List<Entry> cands, List<String> reqQuery, String body, boolean byQuery) {
        if (cands == null || cands.isEmpty()) return null;
        if (cands.size() == 1) return cands.get(0).route;
        Entry best = null;
        int bestScore = Integer.MIN_VALUE;
        for (Entry e : cands) {
            int score = 0;
            if (byQuery) for (String p : e.query) score += reqQuery.contains(p) ? 2 : -2;   // matched - missing, x2
            if (body != null && body.equals(e.postData)) score++;                          // tie-break only
            if (score > bestScore) {
                best = e;
                bestScore = score;
            }
        }
        return best.route;
    }

    public Route decide(String method, String url) {
        return decide(method, url, null);
    }

    @Override
    public Route decide(String method, String url, String body) {
        if (url == null || !regex.matcher(url).matches()) return null;
        String m = method == null ? "GET" : method.toUpperCase(Locale.ROOT);
        Route d = pick(exact.get(m + " " + url), null, body, false);
        if (d == null) d = pick(noQuery.get(m + " " + stripQuery(url)), queryPairs(url), body, true);
        if (d != null) return d;
        return abortMissing ? notRecorded : null;
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
