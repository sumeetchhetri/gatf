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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Q2 — one HTTP request recovered from a cURL command, a Postman collection item or a HAR entry, before it becomes a
 * GATF {@code TestCase} (see {@link Importers#toTestCases}). Pure: no GATF runtime dependencies.
 */
public class ApiRequest {

    /** HTTP methods the GATF API executor accepts (TestCase.validate). */
    public static final List<String> SUPPORTED_METHODS = List.of("GET", "POST", "PUT", "DELETE", "PATCH");

    public String name;
    public String description;
    public String method = "GET";
    /** scheme://host[:port] when the URL was literal; null when the host is a variable (config baseUrl applies). */
    public String baseUrl;
    /** path + query, relative to baseUrl. */
    public String url;
    /** the URL as given (before the baseUrl/url split). */
    public String fullUrl;
    public Map<String, String> headers = new LinkedHashMap<String, String>();
    public String body;
    /** body read from a file at run time (curl {@code -d @file}); resolved against the test-case base path. */
    public String contentFile;
    /** GATF multipart syntax: {@code name:text:value:mime} or {@code name:file:path[:mime]}. */
    public List<String> formParts = new ArrayList<String>();
    /** 0 = not asserted. */
    public int expectedStatus;
    /** expected response Content-Type (OpenAPI); null = not asserted. */
    public String expectedContentType;
    /** OA2: {@code <spec file>#<operation pointer>} the response is validated against; null = none. */
    public String contract;
    /** OA2: fingerprint of the operation at import time (drift detection on re-import). */
    public String contractHash;
    /** source line (cURL) or entry index (HAR), for the unsupported report. */
    public int line;

    // ---- helpers shared by the importers ----

    private static final Pattern ORIGIN = Pattern.compile("^([A-Za-z][A-Za-z0-9+.-]*://[^/?#]+)(.*)$", Pattern.DOTALL);

    /** Splits an absolute URL into {origin, path+query}; path defaults to "/". A relative URL gives {null, url}. */
    public static String[] splitUrl(String full) {
        String u = full == null ? "" : full.trim();
        int hash = u.indexOf('#');
        if (hash >= 0) u = u.substring(0, hash);
        Matcher m = ORIGIN.matcher(u);
        if (!m.matches()) return new String[] { null, u.isEmpty() ? "/" : u };
        String rest = m.group(2);
        if (rest.isEmpty()) rest = "/";
        else if (rest.charAt(0) == '?') rest = "/" + rest;
        return new String[] { m.group(1), rest };
    }

    /** Sets baseUrl/url from a full URL. A host that is a template variable ({@code ${host}/x}) leaves baseUrl null. */
    public void setFullUrl(String full) {
        String u = full == null ? "" : full.trim();
        fullUrl = u;
        if (u.startsWith("${")) {
            int end = u.indexOf('}');
            String rest = end < 0 ? "" : u.substring(end + 1);
            baseUrl = null;
            url = rest.isEmpty() ? "/" : rest;
            return;
        }
        String[] p = splitUrl(u);
        baseUrl = p[0];
        url = p[1];
    }

    private static final Pattern POSTMAN_VAR = Pattern.compile("\\{\\{\\s*([A-Za-z_][A-Za-z0-9_.\\-]*)\\s*\\}\\}");

    /** Postman {@code {{name}}} → GATF {@code ${name}}; dynamic variables ({@code {{$guid}}}) are left as-is. */
    public static String gatfVars(String s) {
        if (s == null || s.indexOf("{{") < 0) return s;
        return POSTMAN_VAR.matcher(s).replaceAll(r -> Matcher.quoteReplacement("${" + r.group(1) + "}"));
    }

    /** True when {@link #gatfVars} left a Postman dynamic variable such as {@code {{$randomInt}}}. */
    public static boolean hasDynamicVar(String s) {
        return s != null && s.matches("(?s).*\\{\\{\\s*\\$.*\\}\\}.*");
    }

    /** Sets {@link #url} from a path and query parts (joined with '&'). */
    public void setUrl(String path, List<String> query) {
        url = query == null || query.isEmpty() ? path : path + "?" + String.join("&", query);
        fullUrl = (baseUrl == null ? "" : baseUrl) + url;
    }

    /** A field-by-field copy (headers and form parts are copied, not shared). */
    public ApiRequest copy() {
        ApiRequest c = new ApiRequest();
        c.name = name;
        c.description = description;
        c.method = method;
        c.baseUrl = baseUrl;
        c.url = url;
        c.fullUrl = fullUrl;
        c.headers = new LinkedHashMap<String, String>(headers);
        c.body = body;
        c.contentFile = contentFile;
        c.formParts = new ArrayList<String>(formParts);
        c.expectedStatus = expectedStatus;
        c.expectedContentType = expectedContentType;
        c.contract = contract;
        c.contractHash = contractHash;
        c.line = line;
        return c;
    }

    public String header(String name) {
        for (Map.Entry<String, String> e : headers.entrySet()) {
            if (e.getKey().equalsIgnoreCase(name)) return e.getValue();
        }
        return null;
    }

    public void removeHeader(String name) {
        headers.keySet().removeIf(k -> k.equalsIgnoreCase(name));
    }

    /** Adds a header, replacing an existing one of the same name (any case). */
    public void putHeader(String name, String value) {
        removeHeader(name);
        headers.put(name, value);
    }

    /** Adds {@code value} to an existing header with {@code sep}, or sets it (Cookie: a=1; b=2). */
    public void appendHeader(String name, String value, String sep) {
        String cur = header(name);
        putHeader(name, cur == null || cur.isEmpty() ? value : cur + sep + value);
    }

    public String upperMethod() {
        return method == null ? "GET" : method.trim().toUpperCase(Locale.ROOT);
    }

    public boolean hasBody() {
        return (body != null && !body.isEmpty()) || contentFile != null;
    }

    /**
     * GATF's executor requires a Content-Type whenever a body is sent (TestCase.validate). Multipart parts get the
     * executor's own boundary, so a recorded multipart Content-Type is dropped.
     */
    public void normalizeContentType(String defaultType) {
        if (!formParts.isEmpty()) {
            String ct = header("Content-Type");
            if (ct != null && ct.toLowerCase(Locale.ROOT).startsWith("multipart/")) removeHeader("Content-Type");
            return;
        }
        if (hasBody() && header("Content-Type") == null) putHeader("Content-Type", defaultType);
    }

    /** The multipart split in the executor is on ':' — a name or text value containing ':' cannot be expressed. */
    public static boolean formPartExpressible(String name, String value) {
        return name.indexOf(':') < 0 && value.indexOf(':') < 0;
    }

    @Override
    public String toString() {
        return upperMethod() + " " + (baseUrl == null ? "" : baseUrl) + url;
    }
}
