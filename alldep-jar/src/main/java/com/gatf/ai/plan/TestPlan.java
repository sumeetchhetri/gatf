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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.gatf.record.PickLocator;
import com.gatf.selenium.LocatorRanking;

/**
 * H1 (Δ151): the test plan, format {@code gatf-test-plan} version 1 (the Δ152 generator's input contract; see
 * {@link #toJson}). Built deterministically from a {@link CrawlMap}: every crawled route gets an entry, in the map's
 * order, whether or not a model is ever asked. A model can only enrich an existing entry (journeys, priority) through
 * {@link #addJourney} / {@link #setPriority}; nothing here adds or drops a route.
 * <p>
 * A journey is a list of steps from a closed set of {@link #INTENTS}. A step names an element by {@code ref} (an
 * element of the route the journey is on, whose locator the crawl saw and GATF ranked) or a route by {@code route}
 * (an id in this plan, whose URL is the crawled route, same origin as the crawl and accepted by
 * {@link PickLocator#gotoUrl}). Data comes from a closed set too ({@link #DATA}). No step carries free text: the
 * only page text a generator writes is an element's {@code text} or a route's {@code title}, and only where
 * {@code assert-text} / {@code assert-title} allow it ({@link #safeText}). Pure.
 */
public final class TestPlan {

    public static final String FORMAT = "gatf-test-plan";
    public static final int VERSION = 1;

    /** The closed set of step intents. */
    public static final List<String> INTENTS = Collections.unmodifiableList(Arrays.asList(
            "navigate", "click", "fill", "select", "check", "assert-visible", "assert-text", "assert-title", "assert-url"));
    /** The closed set of {@code fill} data kinds: the generator picks the value (sample by field type, empty, invalid). */
    public static final List<String> DATA = Collections.unmodifiableList(Arrays.asList("sample", "empty", "invalid"));
    /** The closed set of journey goals. */
    public static final List<String> GOALS = Collections.unmodifiableList(Arrays.asList(
            "smoke", "happy-path", "validation", "negative", "navigation", "boundary"));

    public static final int MAX_STEPS = 25;
    public static final int MAX_AI_JOURNEYS = 3;
    public static final int MAX_FORM_JOURNEYS = 3;
    public static final int DEFAULT_MAX_ROUTES = 500;
    public static final int MAX_UNPLANNED_LISTED = 200;

    /** One route's plan entry. */
    public static final class Entry {
        public final String id;
        public final CrawlMap.Route src;
        /** The URL a {@code navigate} uses (the route itself), or null when it is not navigable. */
        public final String url;
        public final String whyNotNavigable;
        public String priority;
        public String priorityFrom = "crawl";
        public Boolean tested;
        public final Map<String, CrawlMap.Element> refs = new LinkedHashMap<String, CrawlMap.Element>();
        public final List<Journey> journeys = new ArrayList<Journey>();
        public final List<String> notes = new ArrayList<String>();
        int aiJourneys;

        Entry(String id, CrawlMap.Route src, String url, String why) {
            this.id = id;
            this.src = src;
            this.url = url;
            this.whyNotNavigable = why;
            this.priority = src.priority == null || src.priority.isEmpty() ? "low" : src.priority;
        }

        public boolean navigable() { return url != null; }
    }

    public static final class Journey {
        public String id;
        public String name;
        public String goal;
        public String origin;   // deterministic | ai
        public boolean sideEffects;
        public final List<Map<String, Object>> steps = new ArrayList<Map<String, Object>>();
    }

    public final String source;
    public final CrawlMap map;
    public final List<Entry> entries = new ArrayList<Entry>();
    private final Map<String, Entry> byId = new LinkedHashMap<String, Entry>();
    public final List<String> unplanned = new ArrayList<String>();
    public int unplannedCount;
    public final List<String> notes = new ArrayList<String>();
    public Map<String, Object> ai = new LinkedHashMap<String, Object>();

    private TestPlan(String source, CrawlMap map) {
        this.source = source;
        this.map = map;
    }

    // ------------------------------------------------------------------ deterministic plan

    /**
     * Every route of {@code map}, up to {@code maxRoutes} (the rest listed in {@code unplanned} with a note), each with
     * its deterministic journeys. {@code visited}: routes some test already visited (from a coverage index), or null.
     */
    public static TestPlan build(String source, CrawlMap map, Set<String> visited, int maxRoutes) {
        TestPlan p = new TestPlan(source, map);
        int cap = Math.max(1, Math.min(CrawlMap.MAX_ROUTES_READ, maxRoutes));
        p.notes.addAll(map.notes);
        int n = 0;
        for (CrawlMap.Route r : map.routes) {
            if (n >= cap) {
                p.unplannedCount++;
                if (p.unplanned.size() < MAX_UNPLANNED_LISTED) p.unplanned.add(r.route);
                continue;
            }
            n++;
            String why = null, url = null;
            String o = CrawlMap.origin(r.route);
            if (o == null || map.origin == null || !o.equals(map.origin)) why = "not on the crawl's origin (" + (map.origin == null ? "unknown" : map.origin) + ")";
            else if (PickLocator.gotoUrl(r.route) == null || !PickLocator.gotoUrl(r.route).equals(r.route)) why = "its URL cannot be written safely in a goto step";
            else url = r.route;
            Entry e = new Entry("r" + n, r, url, why);
            int k = 0;
            for (CrawlMap.Element el : r.elements) e.refs.put("e" + (++k), el);
            if (visited != null) e.tested = visited.contains(r.route);
            if (why != null) e.notes.add("not navigable: " + why + "; listed for coverage, no journeys");
            if (r.error != null) e.notes.add(r.error + ": re-check the route");
            if (r.queryDropped) e.notes.add("crawled with a query string; planned without it (query strings may hold tokens)");
            if (r.elementsDropped > 0) e.notes.add(r.elementsDropped + " element(s) left out: no safe locator, or over the cap of " + CrawlMap.MAX_ELEMENTS);
            p.entries.add(e);
            p.byId.put(e.id, e);
        }
        if (p.unplannedCount > 0) {
            p.notes.add("cap: the crawl has " + map.routes.size() + " routes; " + cap + " planned, " + p.unplannedCount
                    + " not planned (listed under unplanned" + (p.unplannedCount > p.unplanned.size() ? ", first " + p.unplanned.size() : "") + "); raise --max-routes");
        }
        for (Entry e : p.entries) p.deterministic(e);
        return p;
    }

    public Entry entry(String id) { return byId.get(id); }

    private void deterministic(Entry e) {
        if (!e.navigable()) return;
        // smoke: the page loads, has its title, and its first elements are there
        List<Map<String, Object>> smoke = new ArrayList<Map<String, Object>>();
        smoke.add(step("navigate", "route", e.id));
        if (safeText(e.src.title)) smoke.add(step("assert-title"));
        int vis = 0;
        for (String ref : e.refs.keySet()) {
            if (vis >= 3) break;
            smoke.add(step("assert-visible", "ref", ref));
            vis++;
        }
        add(e, "smoke", "smoke", "deterministic", smoke);
        // forms: fill everything and submit; then submit empty when something is required
        Map<Integer, List<String>> forms = new LinkedHashMap<Integer, List<String>>();
        for (Map.Entry<String, CrawlMap.Element> r : e.refs.entrySet()) {
            forms.computeIfAbsent(r.getValue().form, f -> new ArrayList<String>()).add(r.getKey());
        }
        int fj = 0;
        for (Map.Entry<Integer, List<String>> f : forms.entrySet()) {
            if (fj >= MAX_FORM_JOURNEYS) { e.notes.add("only the first " + MAX_FORM_JOURNEYS + " forms get journeys"); break; }
            List<Map<String, Object>> fill = new ArrayList<Map<String, Object>>();
            fill.add(step("navigate", "route", e.id));
            String submit = null;
            boolean required = false;
            for (String ref : f.getValue()) {
                CrawlMap.Element el = e.refs.get(ref);
                if ("submit".equals(el.kind)) { if (submit == null) submit = ref; continue; }
                if (!"field".equals(el.kind)) continue;
                required |= el.required;
                Map<String, Object> s = fillStep(el, ref);
                if (s != null && fill.size() < MAX_STEPS - 1) fill.add(s);
            }
            if (fill.size() < 2) continue;
            String name = f.getKey() == 0 ? "inputs" : "form-" + f.getKey();
            fj++;
            if (submit != null) fill.add(step("click", "ref", submit));
            add(e, name, "happy-path", "deterministic", fill);
            if (required && submit != null) {
                List<Map<String, Object>> empty = new ArrayList<Map<String, Object>>();
                empty.add(step("navigate", "route", e.id));
                empty.add(step("click", "ref", submit));
                empty.add(step("assert-url", "route", e.id));
                add(e, name + "-required", "validation", "deterministic", empty);
            }
        }
    }

    private static Map<String, Object> fillStep(CrawlMap.Element el, String ref) {
        if ("select".equals(el.tag)) {
            Map<String, Object> s = step("select", "ref", ref);
            s.put("option", el.options > 1 ? 1 : 0);
            return s;
        }
        if (checkable(el)) return el.required ? step("check", "ref", ref) : null;
        if (fillable(el)) {
            Map<String, Object> s = step("fill", "ref", ref);
            s.put("data", "sample");
            return s;
        }
        return null;
    }

    // ------------------------------------------------------------------ enrichment (validated)

    /** A model's priority for a route; ignored for an error route or an unknown value. */
    public boolean setPriority(Entry e, String priority) {
        if (e == null || "error".equals(e.priority) || priority == null || !priority.matches("high|medium|low")) return false;
        if (!priority.equals(e.priority)) {
            e.priority = priority;
            e.priorityFrom = "ai";
        }
        return true;
    }

    /**
     * Validates a proposed journey on route {@code e} against the closed set and this plan, and adds it. Returns null
     * when added, else why it was rejected (a fixed message, never the proposal's content).
     */
    public String addJourney(Entry e, String goal, List<Map<String, Object>> raw) {
        if (e == null) return "unknown route";
        if (!e.navigable()) return "route is not navigable";
        if (e.aiJourneys >= MAX_AI_JOURNEYS) return "journey cap for the route";
        if (goal == null || !GOALS.contains(goal)) return "unknown goal";
        if (raw == null || raw.isEmpty() || raw.size() > MAX_STEPS) return "no steps or too many steps";
        List<Map<String, Object>> steps = new ArrayList<Map<String, Object>>();
        Entry cur = e;
        // a journey starts on its own route
        if (!("navigate".equals(raw.get(0).get("intent")) && e.id.equals(raw.get(0).get("route")))) steps.add(step("navigate", "route", e.id));
        for (Map<String, Object> s : raw) {
            String intent = s.get("intent") instanceof String ? (String) s.get("intent") : null;
            if (intent == null || !INTENTS.contains(intent)) return "unknown intent";
            if (intent.equals("navigate") || intent.equals("assert-url")) {
                Entry to = byId.get(String.valueOf(s.get("route")));
                if (to == null || !to.navigable()) return "step names an unknown or non-navigable route";
                steps.add(step(intent, "route", to.id));
                cur = to;   // later refs resolve on this route
                continue;
            }
            if (intent.equals("assert-title")) {
                if (!safeText(cur.src.title)) return "assert-title on a route without a safe title";
                Map<String, Object> t = step(intent);
                t.put("on", cur.id);
                steps.add(t);
                continue;
            }
            String ref = s.get("ref") instanceof String ? (String) s.get("ref") : null;
            CrawlMap.Element el = ref == null ? null : cur.refs.get(ref);
            if (el == null) return "step names an element the crawl did not see on that route";
            Map<String, Object> out = step(intent, "ref", ref);
            out.put("on", cur.id);
            switch (intent) {
                case "click":
                    if (!("button".equals(el.kind) || "submit".equals(el.kind) || checkable(el))) return "click on an element that is not clickable";
                    break;
                case "check":
                    if (!checkable(el)) return "check on an element that is not a checkbox or radio";
                    break;
                case "fill": {
                    if (!fillable(el)) return "fill on an element that is not a text field";
                    Object d = s.get("data") == null ? "sample" : s.get("data");
                    if (!DATA.contains(d)) return "unknown data kind";
                    out.put("data", d);
                    break;
                }
                case "select": {
                    if (!"select".equals(el.tag)) return "select on an element that is not a select";
                    Object o = s.get("option") == null ? Integer.valueOf(el.options > 1 ? 1 : 0) : s.get("option");
                    if (!(o instanceof Integer) || (Integer) o < 0 || (Integer) o >= Math.max(1, el.options)) return "select option out of range";
                    out.put("option", o);
                    break;
                }
                case "assert-text":
                    if (!safeText(el.text)) return "assert-text on an element without a safe text";
                    break;
                default:   // assert-visible
                    break;
            }
            steps.add(out);
        }
        if (steps.size() < 2 || steps.size() > MAX_STEPS) return "no steps or too many steps";
        for (Journey j : e.journeys) if (j.steps.equals(steps)) return "same steps as an existing journey";
        e.aiJourneys++;
        add(e, goal + "-ai" + e.aiJourneys, goal, "ai", steps);
        return null;
    }

    private void add(Entry e, String name, String goal, String origin, List<Map<String, Object>> steps) {
        Journey j = new Journey();
        j.id = e.id + "-j" + (e.journeys.size() + 1);
        j.name = name;
        j.goal = goal;
        j.origin = origin;
        for (Map<String, Object> s : steps) {
            if ("click".equals(s.get("intent"))) {
                Entry on = s.get("on") == null ? e : byId.get(String.valueOf(s.get("on")));
                CrawlMap.Element el = on == null ? null : on.refs.get(String.valueOf(s.get("ref")));
                if (el != null && ("button".equals(el.kind) || ("submit".equals(el.kind) && !"get".equals(el.method)))) j.sideEffects = true;
            }
            if ((s.containsKey("ref") || "assert-title".equals(s.get("intent"))) && !s.containsKey("on")) s.put("on", e.id);
            j.steps.add(s);
        }
        e.journeys.add(j);
    }

    static boolean checkable(CrawlMap.Element el) {
        return "field".equals(el.kind) && "input".equals(el.tag) && ("checkbox".equals(el.type) || "radio".equals(el.type));
    }

    static boolean fillable(CrawlMap.Element el) {
        if (!"field".equals(el.kind)) return false;
        if ("textarea".equals(el.tag)) return true;
        return "input".equals(el.tag) && !checkable(el) && !el.type.matches("range|color|file|submit|button|reset|image|hidden");
    }

    /** Page text a generator may write into an assertion: non-empty, capped, no '$', no '#'+letter, no control characters. */
    public static boolean safeText(String t) {
        return t != null && !t.isEmpty() && t.length() <= CrawlMap.MAX_TITLE && !LocatorRanking.unsafe(t);
    }

    static Map<String, Object> step(String intent, String... kv) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("intent", intent);
        for (int i = 0; i + 1 < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }

    // ------------------------------------------------------------------ JSON (the Δ152 contract)

    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("format", FORMAT);
        m.put("version", VERSION);
        Map<String, Object> src = new LinkedHashMap<String, Object>();
        src.put("crawl", source);
        src.put("crawlFormat", map.format);
        src.put("origin", map.origin);
        src.put("pages", map.pages);
        src.put("routes", map.routes.size() + map.routesNotRead);
        m.put("source", src);
        m.put("ai", ai);
        m.put("intents", INTENTS);
        m.put("data", DATA);
        m.put("goals", GOALS);
        m.put("notes", new ArrayList<String>(new LinkedHashSet<String>(notes)));
        Map<String, Object> counts = new LinkedHashMap<String, Object>();
        int journeys = 0, aiJ = 0, navigable = 0;
        for (Entry e : entries) {
            journeys += e.journeys.size();
            aiJ += e.aiJourneys;
            if (e.navigable()) navigable++;
        }
        counts.put("routes", entries.size());
        counts.put("navigable", navigable);
        counts.put("journeys", journeys);
        counts.put("aiJourneys", aiJ);
        counts.put("unplanned", unplannedCount);
        m.put("counts", counts);
        List<Map<String, Object>> rs = new ArrayList<Map<String, Object>>();
        for (Entry e : entries) rs.add(entryJson(e));
        m.put("routes", rs);
        Map<String, Object> un = new LinkedHashMap<String, Object>();
        un.put("count", unplannedCount);
        un.put("routes", unplanned);
        m.put("unplanned", un);
        return m;
    }

    private static Map<String, Object> entryJson(Entry e) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("id", e.id);
        m.put("route", e.src.route);
        m.put("url", e.url);
        m.put("navigable", e.navigable());
        m.put("title", e.src.title);
        m.put("titleSafe", safeText(e.src.title));
        m.put("depth", e.src.depth);
        m.put("priority", e.priority);
        m.put("priorityFrom", e.priorityFrom);
        m.put("crawlPriority", e.src.priority.isEmpty() ? null : e.src.priority);
        m.put("score", e.src.score);
        m.put("tested", e.tested);
        m.put("pages", e.src.pages);
        List<Map<String, Object>> els = new ArrayList<Map<String, Object>>();
        for (Map.Entry<String, CrawlMap.Element> r : e.refs.entrySet()) {
            CrawlMap.Element el = r.getValue();
            Map<String, Object> x = new LinkedHashMap<String, Object>();
            x.put("ref", r.getKey());
            x.put("kind", el.kind);
            x.put("tag", el.tag);
            x.put("type", el.type);
            if (el.form > 0) { x.put("form", el.form); x.put("method", el.method); }
            x.put("name", el.name);
            x.put("label", el.label);
            x.put("text", el.text);
            x.put("textSafe", safeText(el.text));
            x.put("required", el.required);
            if ("select".equals(el.tag)) x.put("options", el.options);
            x.put("locator", el.locator);
            x.put("alternatives", el.alternatives);
            els.add(x);
        }
        m.put("elements", els);
        List<Map<String, Object>> js = new ArrayList<Map<String, Object>>();
        for (Journey j : e.journeys) {
            Map<String, Object> x = new LinkedHashMap<String, Object>();
            x.put("id", j.id);
            x.put("name", j.name);
            x.put("goal", j.goal);
            x.put("origin", j.origin);
            x.put("sideEffects", j.sideEffects);
            x.put("steps", j.steps);
            js.add(x);
        }
        m.put("journeys", js);
        m.put("notes", e.notes);
        return m;
    }
}
