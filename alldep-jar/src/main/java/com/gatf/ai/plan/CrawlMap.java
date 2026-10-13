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
package com.gatf.ai.plan;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatf.coverage.CoverageUrls;
import com.gatf.record.PickLocator;
import com.gatf.selenium.LocatorRanking;
import com.gatf.selenium.SemanticLocators;

/**
 * H1 (Δ151): a saved crawl map read for the planner. Two shapes are accepted, as {@code CoverageMerge.crawl} does:
 * the SPA crawl's {@code <name>-coverage.json} (an array of pages, with {@code elements} since Δ151) and the portal
 * crawler's {@code crawl-coverage.json} ({@code {seed, pages:[{url, forms:[{inputs}]}]}}).
 * <p>
 * Pages are keyed by {@link CoverageUrls#route} (query, userinfo and session ids dropped), the same key the coverage
 * index uses, so "every crawled route" means the same thing here and in the untested-routes report. Pages that share
 * a route are merged. Every locator is derived here by GATF's own ranking ({@link LocatorRanking#choose} over the
 * candidates the page reported, else {@link PickLocator#selector} for the legacy selector), never read as a string
 * from the file, so a hand-edited map cannot plant a locator; elements with no safe locator are left out and counted.
 * Page text (titles, labels, button text) is kept only as capped, control-free data. Pure.
 */
public final class CrawlMap {

    /** Largest crawl map read (the caller enforces it on the stream). */
    public static final long MAX_BYTES = 16L * 1024 * 1024;
    public static final int MAX_ELEMENTS = 40;
    public static final int MAX_TEXT = 60;
    public static final int MAX_TITLE = 120;
    public static final int MAX_ALTERNATIVES = 4;
    /** Hard ceiling on routes read from one map, whatever the planner's cap (bounds memory). */
    public static final int MAX_ROUTES_READ = 5000;

    /** One addressable element the crawl saw on a route. */
    public static final class Element {
        public String kind;          // field | submit | button
        public String tag = "";
        public String type = "";
        public String name = "";
        public String label = "";
        public String text = "";
        public boolean required;
        public int options;
        public int form;             // 0 = not in a form
        public String method;        // the form's method (lower case), null outside a form
        public String locator;       // GATF-ranked, safe
        public List<String> alternatives = new ArrayList<String>();
    }

    /** One crawled route (pages with the same {@link CoverageUrls#route} merged). */
    public static final class Route {
        public String route;
        public String title = "";
        public int depth;
        public String priority = "";  // high | medium | low | error ("" when the map has none)
        public int score;
        public String error;
        public boolean queryDropped;
        public int pages;
        public final List<Element> elements = new ArrayList<Element>();
        public int elementsDropped;   // seen, but no safe locator or over the cap
        int loaded;                   // pages of this route that loaded
    }

    public String format;             // spa | portal
    public String origin;             // scheme://host[:port] of the seed (portal) or the first depth-0 page
    public final List<Route> routes = new ArrayList<Route>();
    public final List<String> notes = new ArrayList<String>();
    public int pages;
    /** Pages the map lists whose URL is not an http(s) route (counted, never silently dropped). */
    public int pagesWithoutRoute;
    /** Distinct routes beyond {@link #MAX_ROUTES_READ}: counted, not read. */
    public int routesNotRead;

    private CrawlMap() {}

    public static CrawlMap read(InputStream in, ObjectMapper om) throws IOException {
        JsonNode root = om.readTree(in);
        CrawlMap m = parse(root);
        if (m == null) throw new IOException("not a crawl map (expected [{url…}] or {pages:[{url…}]})");
        return m;
    }

    /** Null when {@code root} is not a crawl map. */
    public static CrawlMap parse(JsonNode root) {
        JsonNode pages = root == null ? null : root.isArray() ? root : root.path("pages");
        if (pages == null || !pages.isArray()) return null;
        CrawlMap m = new CrawlMap();
        boolean portal = root.isObject();
        m.format = portal ? "portal" : "spa";
        if (portal) m.origin = origin(root.path("seed").asText(null));
        Map<String, Route> byRoute = new LinkedHashMap<String, Route>();
        Set<String> notRead = new LinkedHashSet<String>();
        for (JsonNode pg : pages) {
            if (!pg.isObject()) continue;
            m.pages++;
            String url = pg.path("url").isTextual() ? pg.path("url").asText() : null;
            String r = CoverageUrls.route(url);
            if (r == null) { m.pagesWithoutRoute++; continue; }
            int depth = pg.path("depth").canConvertToInt() ? Math.max(0, pg.path("depth").asInt()) : 0;
            if (m.origin == null && depth == 0) m.origin = origin(r);
            Route rt = byRoute.get(r);
            if (rt == null) {
                if (byRoute.size() >= MAX_ROUTES_READ) { notRead.add(r); continue; }
                rt = new Route();
                rt.route = r;
                rt.depth = depth;
                byRoute.put(r, rt);
            }
            rt.pages++;
            rt.depth = Math.min(rt.depth, depth);
            if (url != null && (url.indexOf('?') >= 0)) rt.queryDropped = true;
            if (rt.title.isEmpty()) rt.title = clean(pg.path("title").asText(""), MAX_TITLE);
            List<Element> els = portal ? portalElements(pg) : spaElements(pg);
            int seen = portal ? portalCount(pg) : pg.path("elements").size();
            mergeElements(rt, els, seen);
            String pr, err = null;
            int score;
            if (portal) {
                int st = pg.path("status").asInt(0);
                if (st == 0 || st >= 400) err = st == 0 ? "load failed" : "HTTP " + st;
                int forms = 0, inputs = 0;
                for (JsonNode f : pg.path("forms")) { if (f.path("inputs").size() > 0) forms++; inputs += f.path("inputs").size(); }
                score = forms * 5 + inputs * 2 + (depth == 0 ? 1 : 0);
                pr = err != null ? "error" : score >= 15 ? "high" : score >= 6 ? "medium" : "low";
            } else {
                pr = pg.path("priority").asText("");
                if (!pr.matches("high|medium|low|error")) pr = "";
                score = pg.path("score").canConvertToInt() ? pg.path("score").asInt() : 0;
                if ("error".equals(pr)) err = "load failed during the crawl";
            }
            if (err != null) {
                if (rt.error == null) rt.error = err;
            } else {
                rt.loaded++;
                if (rank(pr) > rank(rt.priority)) rt.priority = pr;
                rt.score = Math.max(rt.score, score);
            }
        }
        m.routes.addAll(byRoute.values());
        for (Route rt : m.routes) {
            if (rt.loaded > 0) rt.error = null;   // a route that loaded on any of its pages is not an error route
            else { rt.priority = "error"; rt.score = -1; }
        }
        m.routesNotRead = notRead.size();
        if (m.origin == null && !m.routes.isEmpty()) m.origin = origin(m.routes.get(0).route);
        if (m.pagesWithoutRoute > 0) m.notes.add(m.pagesWithoutRoute + " crawled page(s) have no http(s) route (not a web page URL) and cannot be planned");
        if (m.routesNotRead > 0) m.notes.add("cap: the map has more than " + MAX_ROUTES_READ + " routes; " + m.routesNotRead + " were not read");
        return m;
    }

    static int rank(String p) {
        switch (p == null ? "" : p) {
            case "high": return 3;
            case "medium": return 2;
            case "low": return 1;
            default: return 0;
        }
    }

    private static void mergeElements(Route rt, List<Element> els, int seen) {
        Set<String> have = new LinkedHashSet<String>();
        for (Element e : rt.elements) have.add(e.locator);
        int kept = 0;
        for (Element e : els) {
            if (have.contains(e.locator)) { kept++; continue; }   // the same element on another page of the route
            if (rt.elements.size() >= MAX_ELEMENTS) continue;
            rt.elements.add(e);
            have.add(e.locator);
            kept++;
        }
        rt.elementsDropped += Math.max(0, seen - kept);
    }

    // ---- SPA map (Δ151 elements written by SpaCrawler.coveragePlan) ----

    private static List<Element> spaElements(JsonNode pg) {
        List<Element> out = new ArrayList<Element>();
        int n = 0;
        for (JsonNode e : pg.path("elements")) {
            if (++n > 4 * MAX_ELEMENTS) break;
            if (!e.isObject()) continue;
            String kind = e.path("kind").asText("");
            if (!kind.matches("field|submit|button")) continue;
            Element el = new Element();
            el.kind = kind;
            el.tag = word(e.path("tag").asText(""));
            el.type = word(e.path("type").asText(""));
            el.name = clean(e.path("name").asText(""), MAX_TEXT);
            el.label = clean(e.path("label").asText(""), MAX_TEXT);
            el.text = clean(e.path("text").asText(""), MAX_TEXT);
            el.required = e.path("required").asBoolean(false);
            el.options = e.path("options").canConvertToInt() ? Math.max(0, Math.min(10_000, e.path("options").asInt())) : 0;
            el.form = e.path("form").canConvertToInt() ? Math.max(0, Math.min(1000, e.path("form").asInt())) : 0;
            if (el.form > 0) el.method = word(e.path("method").asText("get"));
            if ("field".equals(kind) && el.tag.isEmpty()) continue;
            List<Map<String, Object>> cand = new ArrayList<Map<String, Object>>();
            for (JsonNode c : e.path("cand")) {
                if (cand.size() >= 16 || !c.isObject()) break;
                Map<String, Object> cm = new LinkedHashMap<String, Object>();
                for (String k : new String[] { "s", "v", "role", "name" }) if (c.path(k).isTextual()) cm.put(k, c.path(k).asText());
                if (c.path("n").isIntegralNumber() && c.path("n").canConvertToInt()) cm.put("n", c.path("n").asInt());
                cand.add(cm);
            }
            Map<String, Object> sel = null;
            if (e.path("sel").isObject()) {
                sel = new LinkedHashMap<String, Object>();
                sel.put("s", e.path("sel").path("s").asText(""));
                sel.put("v", e.path("sel").path("v").asText(""));
                if (!String.valueOf(sel.get("s")).matches("id|name|css|xpath")) sel = null;
                else if (String.valueOf(sel.get("v")).length() > 1024) sel = null;
            }
            if (!locate(el, cand, sel)) continue;
            out.add(el);
        }
        return out;
    }

    // ---- portal map ----

    private static int portalCount(JsonNode pg) {
        int n = 0;
        for (JsonNode f : pg.path("forms")) n += f.path("inputs").size();
        return n;
    }

    private static List<Element> portalElements(JsonNode pg) {
        List<Element> out = new ArrayList<Element>();
        int fi = 0;
        for (JsonNode f : pg.path("forms")) {
            if (f.path("inputs").size() == 0) continue;
            fi++;
            String method = word(f.path("method").asText("get"));
            for (JsonNode in : f.path("inputs")) {
                if (out.size() >= 4 * MAX_ELEMENTS) return out;
                Element el = new Element();
                el.kind = "field";
                el.tag = word(in.path("tag").asText(""));
                el.type = word(in.path("type").asText(""));
                if (el.tag.isEmpty()) continue;
                // the portal crawl keeps buttons among its inputs: treat submit-like inputs as the form's submit
                if (el.type.equals("submit") || el.type.equals("image") || (el.tag.equals("button") && !el.type.equals("button") && !el.type.equals("reset"))) el.kind = "submit";
                else if (el.type.equals("hidden") || el.type.equals("reset") || el.type.equals("button") || el.type.equals("file")) continue;
                el.name = clean(in.path("name").asText(""), MAX_TEXT);
                el.form = fi;
                el.method = method;
                Map<String, Object> sel = new LinkedHashMap<String, Object>();
                String id = in.path("id").asText(""), nm = in.path("name").asText("");
                if (!id.isEmpty()) { sel.put("s", "id"); sel.put("v", id); }
                else if (!nm.isEmpty()) { sel.put("s", "name"); sel.put("v", nm); }
                else { sel.put("s", "xpath"); sel.put("v", in.path("xpath").asText("")); }
                if (String.valueOf(sel.get("v")).length() > 1024) continue;
                if (!locate(el, null, sel)) continue;
                out.add(el);
            }
        }
        return out;
    }

    /** Ranks the element's locator (and alternatives) with GATF's own rules; false when none is safe. */
    private static boolean locate(Element el, List<Map<String, Object>> cand, Map<String, Object> sel) {
        String fallback = sel == null || String.valueOf(sel.get("v")).isEmpty() ? null : PickLocator.selector(sel);
        String loc = LocatorRanking.choose(cand, fallback);
        if (loc == null) return false;
        el.locator = loc;
        if (cand != null) {
            for (Map<String, Object> c : cand) {
                if (el.alternatives.size() >= MAX_ALTERNATIVES) break;
                if (!SemanticLocators.resolvesStrictly(SemanticLocators.matchCount(c.get("n")))) continue;
                String a = LocatorRanking.render(c);
                if (a != null && !a.equals(loc) && !el.alternatives.contains(a)) el.alternatives.add(a);
            }
        }
        if (fallback != null && !fallback.equals(loc) && el.alternatives.size() < MAX_ALTERNATIVES && !el.alternatives.contains(fallback)) el.alternatives.add(fallback);
        return true;
    }

    // ---- text ----

    /** Control and line-separator characters to spaces, whitespace collapsed, capped. Never null. */
    public static String clean(String v, int cap) {
        if (v == null) return "";
        String s = v.replaceAll("[\\x00-\\x1f\\x7f\\u0085\\u2028\\u2029]", " ").replaceAll("\\s+", " ").trim();
        return s.length() > cap ? s.substring(0, cap) : s;
    }

    /** A tag / type / method word: lower-case letters, digits and '-' only, capped at 16; anything else is "". */
    static String word(String v) {
        String s = v == null ? "" : v.trim().toLowerCase(Locale.ROOT);
        return s.matches("[a-z0-9-]{1,16}") ? s : "";
    }

    /** scheme://host[:port] in lower case, or null. */
    public static String origin(String url) {
        if (url == null) return null;
        try {
            URI u = URI.create(url.trim());
            String sc = u.getScheme(), h = u.getHost();
            if (sc == null || h == null) return null;
            sc = sc.toLowerCase(Locale.ROOT);
            if (!sc.equals("http") && !sc.equals("https")) return null;
            int port = u.getPort();
            if (port == ("https".equals(sc) ? 443 : 80)) port = -1;
            return sc + "://" + h.toLowerCase(Locale.ROOT) + (port > 0 ? ":" + port : "");
        } catch (RuntimeException e) {
            return null;
        }
    }
}
