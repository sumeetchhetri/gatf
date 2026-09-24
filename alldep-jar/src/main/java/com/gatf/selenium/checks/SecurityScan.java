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
package com.gatf.selenium.checks;

import java.io.BufferedReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Q10 — passive security checks over what a UI script's trace recorded (network.har + console.ndjson). Nothing is
 * sent; the findings describe the pages the script visited.
 *
 * <ul>
 * <li>HTML documents: missing Content-Security-Policy, Strict-Transport-Security (https, not localhost),
 * X-Content-Type-Options: nosniff, clickjacking protection (X-Frame-Options or CSP frame-ancestors),
 * Referrer-Policy; Server / X-Powered-By version disclosure.</li>
 * <li>Cookies set by the site (same registrable-looking domain as a visited page): missing Secure on https,
 * missing HttpOnly, SameSite=None without Secure.</li>
 * <li>Mixed content: http:// requests made from an https page, and the browser's own "Mixed Content" messages.</li>
 * <li>CORS: Access-Control-Allow-Origin * together with Allow-Credentials true.</li>
 * <li>CSP violations reported in the console.</li>
 * </ul>
 * Severity: high (mixed content), medium (CSP/HSTS/clickjacking/cookie Secure/CORS/CSP violation), low (the rest).
 */
public final class SecurityScan {

    public static final class Finding {
        public final String severity, rule, page, url, message;

        Finding(String severity, String rule, String page, String url, String message) {
            this.severity = severity;
            this.rule = rule;
            this.page = page;
            this.url = url;
            this.message = message;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("severity", severity);
            m.put("rule", rule);
            m.put("page", page);
            m.put("url", url);
            m.put("message", message);
            return m;
        }

        @Override
        public String toString() {
            return severity + " " + rule + " @ " + page + (url != null && !url.equals(page) ? " (" + url + ")" : "") + ": " + message;
        }
    }

    private final List<Finding> out = new ArrayList<Finding>();
    private final Set<String> seen = new HashSet<String>();

    private SecurityScan() {}

    public static int rank(String severity) {
        switch (severity) {
            case "high": return 3;
            case "medium": return 2;
            case "low": return 1;
            default: return 0;
        }
    }

    public static List<Finding> scan(Path traceDir, ObjectMapper om) {
        SecurityScan s = new SecurityScan();
        try {
            Path har = traceDir.resolve("network.har");
            if (Files.isRegularFile(har)) s.network(om.readTree(har.toFile()).path("log").path("entries"));
        } catch (Exception ignored) {
            // unreadable HAR: no network findings
        }
        try {
            Path con = traceDir.resolve("console.ndjson");
            if (Files.isRegularFile(con)) {
                try (BufferedReader r = Files.newBufferedReader(con, StandardCharsets.UTF_8)) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        if (!line.isBlank()) s.console(om.readTree(line));
                    }
                }
            }
        } catch (Exception ignored) {
        }
        s.out.sort((a, b) -> rank(b.severity) - rank(a.severity));
        return s.out;
    }

    private void add(String sev, String rule, String page, String url, String msg) {
        if (seen.add(rule + "|" + page + "|" + (url == null ? "" : url) + "|" + msg)) out.add(new Finding(sev, rule, page, url, msg));
    }

    private static boolean isHtml(JsonNode e) {
        String mime = e.path("response").path("content").path("mimeType").asText("").toLowerCase(Locale.ROOT);
        String ct = header(e.path("response"), "content-type");
        return mime.startsWith("text/html") || (ct != null && ct.toLowerCase(Locale.ROOT).startsWith("text/html"));
    }

    /** Case-insensitive header lookup; CDP folds repeated headers into one value joined by '\n'. */
    static String header(JsonNode msg, String name) {
        for (JsonNode h : msg.path("headers")) {
            if (h.path("name").asText("").equalsIgnoreCase(name)) return h.path("value").asText("");
        }
        return null;
    }

    private static List<String> headerValues(JsonNode msg, String name) {
        List<String> v = new ArrayList<String>();
        for (JsonNode h : msg.path("headers")) {
            if (h.path("name").asText("").equalsIgnoreCase(name)) {
                for (String p : h.path("value").asText("").split("\n")) if (!p.isBlank()) v.add(p.trim());
            }
        }
        return v;
    }

    private static String host(String url) {
        try {
            String h = URI.create(url).getHost();
            return h == null ? "" : h.toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            return "";
        }
    }

    /** Last two labels (a.example.com → example.com); good enough to tell first- from third-party here. */
    static String site(String host) {
        if (host.matches("[0-9.]+") || host.indexOf(':') >= 0) return host;
        String[] p = host.split("\\.");
        return p.length <= 2 ? host : p[p.length - 2] + "." + p[p.length - 1];
    }

    private static boolean local(String host) {
        return host.equals("localhost") || host.equals("127.0.0.1") || host.equals("::1") || host.endsWith(".localhost");
    }

    private void network(JsonNode entries) {
        Set<String> sites = new HashSet<String>();
        for (JsonNode e : entries) {
            if (isHtml(e) && e.path("response").path("status").asInt(0) >= 200) sites.add(site(host(e.path("request").path("url").asText(""))));
        }
        String page = null;
        for (JsonNode e : entries) {
            String url = e.path("request").path("url").asText("");
            JsonNode res = e.path("response");
            int status = res.path("status").asInt(0);
            boolean doc = isHtml(e) && status >= 200 && status < 400;
            if (doc) {
                page = url;
                document(url, res);
            } else if (page != null && page.startsWith("https://") && url.startsWith("http://")) {
                add("high", "mixed-content", page, url, "https page loads an http:// resource");
            }
            cookies(page == null ? url : page, url, res, sites);
            String acao = header(res, "access-control-allow-origin");
            String acac = header(res, "access-control-allow-credentials");
            if ("*".equals(acao == null ? null : acao.trim()) && acac != null && acac.trim().equalsIgnoreCase("true")) {
                add("medium", "cors-wildcard-credentials", page == null ? url : page, url,
                        "Access-Control-Allow-Origin * with Allow-Credentials true");
            }
        }
    }

    private void document(String url, JsonNode res) {
        boolean https = url.startsWith("https://");
        String h = host(url);
        String csp = header(res, "content-security-policy");
        if (csp == null) add("medium", "missing-csp", url, url, "no Content-Security-Policy header");
        if (https && !local(h) && header(res, "strict-transport-security") == null) {
            add("medium", "missing-hsts", url, url, "no Strict-Transport-Security header");
        }
        String nosniff = header(res, "x-content-type-options");
        if (nosniff == null || !nosniff.trim().equalsIgnoreCase("nosniff")) {
            add("low", "missing-nosniff", url, url, "X-Content-Type-Options: nosniff not set");
        }
        if (header(res, "x-frame-options") == null && (csp == null || !csp.toLowerCase(Locale.ROOT).contains("frame-ancestors"))) {
            add("medium", "missing-clickjacking-protection", url, url, "neither X-Frame-Options nor CSP frame-ancestors");
        }
        if (header(res, "referrer-policy") == null) add("low", "missing-referrer-policy", url, url, "no Referrer-Policy header");
        String server = header(res, "server");
        if (server != null && server.matches(".*\\d+\\.\\d+.*")) add("low", "version-disclosure", url, url, "Server: " + server.trim());
        String powered = header(res, "x-powered-by");
        if (powered != null) add("low", "version-disclosure", url, url, "X-Powered-By: " + powered.trim());
    }

    private void cookies(String page, String url, JsonNode res, Set<String> sites) {
        if (!sites.contains(site(host(url)))) return;   // third-party cookies are out of scope
        boolean https = url.startsWith("https://");
        for (String sc : headerValues(res, "set-cookie")) {
            String name = sc.split("[=;]", 2)[0].trim();
            String attrs = sc.toLowerCase(Locale.ROOT);
            if (attrs.matches("(?s).*;\\s*max-age\\s*=\\s*0\\b.*")) continue;   // a deletion
            boolean secure = attrs.matches("(?s).*;\\s*secure\\s*(;.*)?$");
            boolean httpOnly = attrs.matches("(?s).*;\\s*httponly\\s*(;.*)?$");
            boolean sameSiteNone = attrs.matches("(?s).*;\\s*samesite\\s*=\\s*none\\b.*");
            if (https && !secure) add("medium", "cookie-without-secure", page, url, "cookie '" + name + "' set over https without Secure");
            if (!httpOnly) add("low", "cookie-without-httponly", page, url, "cookie '" + name + "' readable from JavaScript (no HttpOnly)");
            if (sameSiteNone && !secure) add("medium", "cookie-samesite-none-insecure", page, url, "cookie '" + name + "' SameSite=None without Secure");
        }
    }

    private void console(JsonNode c) {
        String text = c.path("text").asText("");
        String src = c.path("url").asText("");
        if (text.contains("Content Security Policy") || text.contains("Content-Security-Policy")) {
            add("medium", "csp-violation", src.isEmpty() ? "(console)" : src, null, first(text));
        } else if (text.startsWith("Mixed Content:") || text.contains("Mixed Content:")) {
            add("high", "mixed-content", src.isEmpty() ? "(console)" : src, null, first(text));
        }
    }

    private static String first(String s) {
        String l = s.split("\n", 2)[0];
        return l.length() > 300 ? l.substring(0, 297) + "..." : l;
    }
}
