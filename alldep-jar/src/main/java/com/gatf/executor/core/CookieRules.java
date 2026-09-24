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
package com.gatf.executor.core;

import java.net.HttpCookie;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Q8 — RFC 6265 cookie matching and header building, shared by the browser↔API session bridge and the API executor.
 * Pure: no Selenium, no GATF runtime.
 */
public final class CookieRules {

    private CookieRules() {}

    /** A cookie as the browser holds it. {@code expiry} is epoch seconds, null for a session cookie. */
    public static final class C {
        public String name, value, domain, path = "/", sameSite;
        public boolean secure, httpOnly, hostOnly;
        public Long expiry;
        /** true for a Set-Cookie that deletes the cookie (Max-Age=0 or an Expires in the past) */
        public boolean deleted;

        public C() {}

        public C(String name, String value, String domain, String path) {
            this.name = name;
            this.value = value;
            this.domain = domain;
            this.path = path == null || path.isEmpty() ? "/" : path;
        }

        @Override
        public String toString() {
            return name + "=" + value + " (" + (hostOnly ? "" : ".") + domain + path + (secure ? " secure" : "")
                    + (httpOnly ? " httpOnly" : "") + (deleted ? " deleted" : "") + ")";
        }
    }

    /** RFC 6265 5.1.3; a domain cookie ({@code hostOnly=false}) also matches subdomains. */
    public static boolean domainMatch(String host, String cookieDomain, boolean hostOnly) {
        if (host == null || cookieDomain == null) return false;
        String h = host.toLowerCase(Locale.ROOT), d = cookieDomain.toLowerCase(Locale.ROOT);
        if (d.startsWith(".")) d = d.substring(1);
        if (h.equals(d)) return true;
        return !hostOnly && h.endsWith("." + d) && !isIp(h);
    }

    private static boolean isIp(String h) {
        return h.matches("[0-9.]+") || h.indexOf(':') >= 0;
    }

    /** RFC 6265 5.1.4. */
    public static boolean pathMatch(String requestPath, String cookiePath) {
        String r = requestPath == null || requestPath.isEmpty() ? "/" : requestPath;
        String c = cookiePath == null || cookiePath.isEmpty() ? "/" : cookiePath;
        if (r.equals(c)) return true;
        if (!r.startsWith(c)) return false;
        return c.endsWith("/") || r.charAt(c.length()) == '/';
    }

    /** RFC 6265 5.1.4 default-path: the request path up to (not including) its last '/', or "/". */
    public static String defaultPath(String requestPath) {
        if (requestPath == null || !requestPath.startsWith("/")) return "/";
        int last = requestPath.lastIndexOf('/');
        return last <= 0 ? "/" : requestPath.substring(0, last);
    }

    /** Would the browser send {@code c} with a request to {@code uri} at {@code nowSec}? */
    public static boolean applies(C c, URI uri, long nowSec) {
        if (c == null || c.deleted || uri == null || uri.getHost() == null) return false;
        if (c.expiry != null && c.expiry <= nowSec) return false;
        if (c.secure && !"https".equalsIgnoreCase(uri.getScheme()) && !isLocal(uri.getHost())) return false;
        return domainMatch(uri.getHost(), c.domain, c.hostOnly) && pathMatch(uri.getRawPath(), c.path);
    }

    /** Browsers treat http://localhost as a secure context for Secure cookies. */
    private static boolean isLocal(String host) {
        String h = host.toLowerCase(Locale.ROOT);
        return h.equals("localhost") || h.equals("127.0.0.1") || h.equals("[::1]") || h.equals("::1");
    }

    /** {@code name=value; …} for the cookies that apply to {@code uri}, longer paths first (RFC 6265 5.4). */
    public static String header(List<C> cookies, URI uri, long nowSec) {
        List<C> use = new ArrayList<C>();
        for (C c : cookies) if (applies(c, uri, nowSec)) use.add(c);
        use.sort(Comparator.comparingInt((C c) -> c.path == null ? 0 : c.path.length()).reversed());
        StringBuilder b = new StringBuilder();
        for (C c : use) {
            if (b.length() > 0) b.append("; ");
            b.append(c.name).append('=').append(c.value == null ? "" : c.value);
        }
        return b.toString();
    }

    /** Parses a Cookie header into name → value (first occurrence of a name wins, as servers usually read it). */
    public static Map<String, String> parseHeader(String header) {
        Map<String, String> m = new LinkedHashMap<String, String>();
        if (header == null) return m;
        for (String part : header.split(";")) {
            String p = part.trim();
            if (p.isEmpty()) continue;
            int eq = p.indexOf('=');
            String n = eq < 0 ? p : p.substring(0, eq).trim();
            if (!n.isEmpty() && !m.containsKey(n)) m.put(n, eq < 0 ? "" : p.substring(eq + 1).trim());
        }
        return m;
    }

    /** {@code base} cookies overridden/extended by {@code overrides}, as one Cookie header value. */
    public static String merge(String base, Map<String, String> overrides) {
        Map<String, String> m = parseHeader(base);
        if (overrides != null) m.putAll(overrides);
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, String> e : m.entrySet()) {
            if (b.length() > 0) b.append("; ");
            b.append(e.getKey()).append('=').append(e.getValue());
        }
        return b.toString();
    }

    /** {@code base} Cookie header overridden/extended by the cookies in the {@code overrides} Cookie header. */
    public static String merge(String base, String overrides) {
        return merge(base, parseHeader(overrides));
    }

    /**
     * Set-Cookie response headers for a request to {@code uri} → cookies as the browser would store them: no Domain
     * attribute = host-only for the request host, no Path = the default path, Max-Age/Expires → {@code expiry} or
     * {@code deleted}. A Domain the request host does not domain-match is rejected (as browsers do).
     */
    public static List<C> fromSetCookie(List<String> setCookies, URI uri, long nowSec) {
        List<C> out = new ArrayList<C>();
        if (setCookies == null || uri == null || uri.getHost() == null) return out;
        for (String sc : setCookies) {
            List<HttpCookie> parsed;
            try {
                parsed = HttpCookie.parse(sc.regionMatches(true, 0, "set-cookie:", 0, 11) ? sc : "set-cookie: " + sc);
            } catch (IllegalArgumentException e) {
                continue;
            }
            for (HttpCookie h : parsed) {
                C c = new C();
                c.name = h.getName();
                c.value = h.getValue();
                if (h.getDomain() == null || h.getDomain().isEmpty()) {
                    c.domain = uri.getHost();
                    c.hostOnly = true;
                } else {
                    c.domain = h.getDomain().startsWith(".") ? h.getDomain().substring(1) : h.getDomain();
                    if (!domainMatch(uri.getHost(), c.domain, false)) continue;
                }
                c.path = h.getPath() == null || !h.getPath().startsWith("/") ? defaultPath(uri.getRawPath()) : h.getPath();
                c.secure = h.getSecure();
                c.httpOnly = h.isHttpOnly();
                c.sameSite = attr(sc, "samesite");
                long maxAge = h.getMaxAge();
                if (maxAge == 0) c.deleted = true;
                else if (maxAge > 0) c.expiry = nowSec + maxAge;
                out.add(c);
            }
        }
        return out;
    }

    private static String attr(String setCookie, String name) {
        for (String part : setCookie.split(";")) {
            String p = part.trim();
            int eq = p.indexOf('=');
            if (eq > 0 && p.substring(0, eq).trim().equalsIgnoreCase(name)) return p.substring(eq + 1).trim();
        }
        return null;
    }
}
