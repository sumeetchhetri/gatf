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
package com.gatf.selenium;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gatf.executor.core.CookieRules;

/**
 * M5 — a browser's authenticated state: cookies (every domain) plus localStorage / sessionStorage per origin, in
 * Playwright's {@code storageState} JSON shape (with an extra {@code sessionStorage} list per origin, which Playwright
 * readers ignore). Written by {@code state save}, read by {@code state load} and by the project-level
 * {@code storageState} / {@code loginOnce} settings. Also maps cookies to and from CDP ({@code Network.getAllCookies}
 * / {@code setCookies}) and WebDriver BiDi ({@code storage.getCookies} / {@code setCookie}). Pure: Jackson only.
 */
public final class StorageState {

    private static final ObjectMapper OM = new ObjectMapper();

    public final List<CookieRules.C> cookies = new ArrayList<CookieRules.C>();
    /** origin → {"local": {k: v}, "session": {k: v}} */
    public final Map<String, Map<String, Map<String, String>>> origins = new LinkedHashMap<String, Map<String, Map<String, String>>>();

    // ---------------------------------------------------------------- JSON

    public String toJson() {
        ObjectNode root = OM.createObjectNode();
        ArrayNode cs = root.putArray("cookies");
        for (CookieRules.C c : cookies) {
            ObjectNode o = cs.addObject();
            o.put("name", c.name);
            o.put("value", c.value == null ? "" : c.value);
            o.put("domain", (c.hostOnly ? "" : ".") + c.domain);
            o.put("path", c.path == null ? "/" : c.path);
            o.put("expires", c.expiry == null ? -1 : c.expiry);
            o.put("httpOnly", c.httpOnly);
            o.put("secure", c.secure);
            String ss = sameSite(c.sameSite);
            o.put("sameSite", ss.isEmpty() ? "Lax" : ss);   // Playwright requires one of the three; Lax = the browsers' default
        }
        ArrayNode os = root.putArray("origins");
        for (Map.Entry<String, Map<String, Map<String, String>>> e : origins.entrySet()) {
            ObjectNode o = os.addObject();
            o.put("origin", e.getKey());
            pairs(o.putArray("localStorage"), e.getValue().get("local"));
            pairs(o.putArray("sessionStorage"), e.getValue().get("session"));
        }
        try {
            return OM.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static StorageState fromJson(String json) {
        JsonNode root;
        try {
            root = OM.readTree(json);
        } catch (Exception e) {
            throw new IllegalArgumentException("not a storage-state file: " + e.getMessage());
        }
        if (root == null || !root.isObject()) throw new IllegalArgumentException("not a storage-state file (expected {cookies, origins})");
        StorageState s = new StorageState();
        for (JsonNode c : root.path("cookies")) {
            String domain = c.path("domain").asText("");
            CookieRules.C k = new CookieRules.C(c.path("name").asText(""), c.path("value").asText(""),
                    domain.startsWith(".") ? domain.substring(1) : domain, c.path("path").asText("/"));
            k.hostOnly = !domain.startsWith(".");
            k.httpOnly = c.path("httpOnly").asBoolean(false);
            k.secure = c.path("secure").asBoolean(false);
            double exp = c.path("expires").asDouble(-1);
            if (exp > 0) k.expiry = (long) exp;
            String ss = c.path("sameSite").asText("");
            if (!ss.isEmpty()) k.sameSite = ss;
            if (!k.name.isEmpty() && !k.domain.isEmpty()) s.cookies.add(k);
        }
        for (JsonNode o : root.path("origins")) {
            String origin = o.path("origin").asText("");
            if (origin.isEmpty()) continue;
            s.origin(origin, "local").putAll(read(o.path("localStorage")));
            s.origin(origin, "session").putAll(read(o.path("sessionStorage")));
        }
        return s;
    }

    /** The local/session map of an origin (created on demand). */
    public Map<String, String> origin(String origin, String kind) {
        return origins.computeIfAbsent(origin, k -> {
            Map<String, Map<String, String>> m = new LinkedHashMap<String, Map<String, String>>();
            m.put("local", new LinkedHashMap<String, String>());
            m.put("session", new LinkedHashMap<String, String>());
            return m;
        }).get(kind);
    }

    /** {@code other}'s cookies replace same name+domain+path ones; its origins replace whole origins. */
    public StorageState merge(StorageState other) {
        for (CookieRules.C c : other.cookies) {
            cookies.removeIf(x -> x.name.equals(c.name) && x.domain.equalsIgnoreCase(c.domain) && x.path.equals(c.path));
            cookies.add(c);
        }
        origins.putAll(other.origins);
        return this;
    }

    /** Cookies still valid at {@code nowSec} (session cookies always are). */
    public List<CookieRules.C> liveCookies(long nowSec) {
        List<CookieRules.C> out = new ArrayList<CookieRules.C>();
        for (CookieRules.C c : cookies) if (c.expiry == null || c.expiry > nowSec) out.add(c);
        return out;
    }

    // ---------------------------------------------------------------- CDP / BiDi cookie shapes

    /** A CDP Network.Cookie map → cookie. */
    public static CookieRules.C fromCdp(Map<?, ?> m) {
        String domain = str(m.get("domain"));
        CookieRules.C c = new CookieRules.C(str(m.get("name")), str(m.get("value")), domain.startsWith(".") ? domain.substring(1) : domain, str(m.get("path")));
        c.hostOnly = !domain.startsWith(".");
        c.secure = Boolean.TRUE.equals(m.get("secure"));
        c.httpOnly = Boolean.TRUE.equals(m.get("httpOnly"));
        Object exp = m.get("expires");
        if (!Boolean.TRUE.equals(m.get("session")) && exp instanceof Number && ((Number) exp).doubleValue() > 0) c.expiry = ((Number) exp).longValue();
        if (m.get("sameSite") != null) c.sameSite = str(m.get("sameSite"));
        return c;
    }

    /** cookie → a CDP Network.CookieParam map (for Network.setCookies). */
    public static Map<String, Object> toCdp(CookieRules.C c) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("name", c.name);
        m.put("value", c.value == null ? "" : c.value);
        if (c.hostOnly) m.put("url", (c.secure ? "https" : "http") + "://" + c.domain + (c.path == null ? "/" : c.path));
        else m.put("domain", "." + c.domain);
        m.put("path", c.path == null ? "/" : c.path);
        m.put("secure", c.secure);
        m.put("httpOnly", c.httpOnly);
        String ss = sameSite(c.sameSite);
        if (!ss.isEmpty()) m.put("sameSite", ss);
        if (c.expiry != null) m.put("expires", c.expiry);
        return m;
    }

    /** A BiDi network.Cookie map ({@code value: {type, value}}, {@code expiry}) → cookie. */
    public static CookieRules.C fromBidi(Map<?, ?> m) {
        String domain = str(m.get("domain"));
        Object v = m.get("value");
        String value = v instanceof Map ? str(((Map<?, ?>) v).get("value")) : str(v);
        CookieRules.C c = new CookieRules.C(str(m.get("name")), value, domain.startsWith(".") ? domain.substring(1) : domain, str(m.get("path")));
        c.hostOnly = !domain.startsWith(".");
        c.secure = Boolean.TRUE.equals(m.get("secure"));
        c.httpOnly = Boolean.TRUE.equals(m.get("httpOnly"));
        if (m.get("expiry") instanceof Number) c.expiry = ((Number) m.get("expiry")).longValue();
        if (m.get("sameSite") != null) c.sameSite = str(m.get("sameSite"));
        return c;
    }

    /** cookie → {@code storage.setCookie} params ({@code {cookie: PartialCookie}}). */
    public static Map<String, Object> toBidi(CookieRules.C c) {
        Map<String, Object> v = new LinkedHashMap<String, Object>();
        v.put("type", "string");
        v.put("value", c.value == null ? "" : c.value);
        Map<String, Object> k = new LinkedHashMap<String, Object>();
        k.put("name", c.name);
        k.put("value", v);
        k.put("domain", (c.hostOnly ? "" : ".") + c.domain);
        k.put("path", c.path == null ? "/" : c.path);
        k.put("httpOnly", c.httpOnly);
        k.put("secure", c.secure);
        String ss = sameSite(c.sameSite).toLowerCase(Locale.ROOT);
        if (!ss.isEmpty()) k.put("sameSite", ss);
        if (c.expiry != null) k.put("expiry", c.expiry);
        Map<String, Object> p = new LinkedHashMap<String, Object>();
        p.put("cookie", k);
        return p;
    }

    // ---------------------------------------------------------------- page scripts

    /** Reads the current page's origin + localStorage + sessionStorage: {@code {origin, local, session}} or null. */
    public static final String READ_JS = "try{if(!/^https?:$/.test(location.protocol))return null;"
            + "var r={origin:location.origin,local:{},session:{}},i,k;"
            + "for(i=0;i<localStorage.length;i++){k=localStorage.key(i);r.local[k]=localStorage.getItem(k);}"
            + "for(i=0;i<sessionStorage.length;i++){k=sessionStorage.key(i);if(k!=='__gatf_state__')r.session[k]=sessionStorage.getItem(k);}"
            + "return r;}catch(e){return null;}";

    /**
     * Applies stored storage for the page's origin, once per tab (a {@code __gatf_state__} sessionStorage marker), so a
     * later app write is not clobbered on every navigation. Used both immediately and as a new-document preload script.
     */
    public String applyScript() {
        ObjectNode all = OM.createObjectNode();
        for (Map.Entry<String, Map<String, Map<String, String>>> e : origins.entrySet()) {
            ObjectNode o = all.putObject(e.getKey());
            ObjectNode l = o.putObject("l"), s = o.putObject("s");
            for (Map.Entry<String, String> x : e.getValue().get("local").entrySet()) l.put(x.getKey(), x.getValue());
            for (Map.Entry<String, String> x : e.getValue().get("session").entrySet()) s.put(x.getKey(), x.getValue());
        }
        return "(function(){try{var S=" + all.toString() + ",e=S[location.origin];if(!e||sessionStorage.getItem('__gatf_state__'))return;"
                + "var k;for(k in e.l)localStorage.setItem(k,e.l[k]);for(k in e.s)sessionStorage.setItem(k,e.s[k]);"
                + "sessionStorage.setItem('__gatf_state__','1');}catch(x){}})();";
    }

    // ---------------------------------------------------------------- Q8: bearer token kept in web storage

    /**
     * {@code browserApiStorageToken}: {@code [localStorage:|sessionStorage:]key[#jsonField]} → the page script that
     * returns the Authorization value ("Bearer …"), or null when the key is absent. A value that already starts with a
     * scheme ("Bearer ", "Token ") is used as is.
     */
    public static String tokenScript(String spec) {
        String s = spec == null ? "" : spec.trim();
        if (s.isEmpty()) return null;
        String where = "both";
        if (s.regionMatches(true, 0, "localStorage:", 0, 13)) { where = "local"; s = s.substring(13); }
        else if (s.regionMatches(true, 0, "sessionStorage:", 0, 15)) { where = "session"; s = s.substring(15); }
        String key = s, field = null;
        int h = s.indexOf('#');
        if (h >= 0) { key = s.substring(0, h); field = s.substring(h + 1); }
        String k = OM.valueToTree(key).toString();
        String get = where.equals("local") ? "localStorage.getItem(" + k + ")"
                : where.equals("session") ? "sessionStorage.getItem(" + k + ")"
                : "(localStorage.getItem(" + k + ")||sessionStorage.getItem(" + k + "))";
        StringBuilder b = new StringBuilder("try{var v=").append(get).append(";if(v==null)return null;");
        if (field != null) {
            b.append("var o=JSON.parse(v);");
            for (String f : field.split("\\.")) b.append("o=o==null?null:o[").append(OM.valueToTree(f).toString()).append("];");
            b.append("if(o==null)return null;v=String(o);");
        }
        // E8: a JSON-encoded string ("\"eyJ…\"", as JSON.stringify stores it) is unwrapped first
        b.append("v=v.trim();if(/^\".*\"$/.test(v)){try{var q=JSON.parse(v);if(typeof q==='string')v=q.trim();}catch(e){}}");
        b.append("if(!v)return null;return /^[A-Za-z]+ /.test(v)?v:'Bearer '+v;}catch(e){return null;}");
        return b.toString();
    }

    // ---------------------------------------------------------------- helpers

    private static void pairs(ArrayNode a, Map<String, String> m) {
        if (m == null) return;
        for (Map.Entry<String, String> e : m.entrySet()) {
            ObjectNode o = a.addObject();
            o.put("name", e.getKey());
            o.put("value", e.getValue());
        }
    }

    private static Map<String, String> read(JsonNode arr) {
        Map<String, String> m = new LinkedHashMap<String, String>();
        for (JsonNode p : arr) m.put(p.path("name").asText(""), p.path("value").asText(""));
        return m;
    }

    /** Lax / Strict / None (CDP and Playwright spelling); anything else → "". */
    static String sameSite(String s) {
        if (s == null) return "";
        String t = s.trim().toLowerCase(Locale.ROOT);
        return t.equals("lax") ? "Lax" : t.equals("strict") ? "Strict" : t.equals("none") ? "None" : "";
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    /** Parses a {@code {origin, local, session}} map returned by {@link #READ_JS} into this state. */
    public void addOrigin(Map<?, ?> r) {
        if (r == null || r.get("origin") == null) return;
        String origin = str(r.get("origin"));
        Map<String, String> l = origin(origin, "local"), s = origin(origin, "session");
        l.clear();
        s.clear();
        copy(r.get("local"), l);
        copy(r.get("session"), s);
    }

    private static void copy(Object src, Map<String, String> into) {
        if (!(src instanceof Map)) return;
        for (Iterator<? extends Map.Entry<?, ?>> it = ((Map<?, ?>) src).entrySet().iterator(); it.hasNext();) {
            Map.Entry<?, ?> e = it.next();
            into.put(str(e.getKey()), e.getValue() == null ? "" : str(e.getValue()));
        }
    }
}
