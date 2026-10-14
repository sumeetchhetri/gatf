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
package com.gatf.ai.gen;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatf.ai.plan.CrawlMap;
import com.gatf.ai.plan.TestPlan;

/**
 * H1 (Δ152): a {@code gatf-test-plan} version 1 file (written by the Δ151 Planner, {@link TestPlan#toJson}) read as
 * untrusted input. Only that format and version are accepted; anything else is refused with a {@link Bad} whose
 * message names no file content. Every value is read by type (a field of the wrong type reads as empty), strings and
 * lists are capped, and nothing here is trusted later: the {@link SelEmitter} re-checks every URL, locator, text and
 * element kind it uses. Pure.
 */
public final class GenPlan {

    public static final long MAX_BYTES = 16L * 1024 * 1024;
    public static final int MAX_ROUTES = 5000;
    public static final int MAX_ELEMENTS = 200;
    public static final int MAX_JOURNEYS = 50;
    public static final int MAX_ALTERNATIVES = 8;
    static final int MAX_ID = 64;
    static final int MAX_STRING = 2048;

    /** A plan that cannot be used at all (wrong format or version, not JSON, too big). */
    public static final class Bad extends Exception {
        private static final long serialVersionUID = 1L;
        public Bad(String msg) { super(msg); }
    }

    public static final class El {
        public String ref, kind, tag, type, method, text, locator;
        public int form, options;
        public boolean required, textSafe;
        public final List<String> alternatives = new ArrayList<String>();
    }

    public static final class Journey {
        public String id, goal;
        public boolean sideEffects;
        public final List<Map<String, Object>> steps = new ArrayList<Map<String, Object>>();
        public int stepsDropped;
    }

    public static final class Route {
        public String id, url, title;
        public boolean navigable, titleSafe;
        public final Map<String, El> elements = new LinkedHashMap<String, El>();
        public final List<Journey> journeys = new ArrayList<Journey>();
    }

    public String origin;
    public final List<Route> routes = new ArrayList<Route>();
    public final Map<String, Route> byId = new LinkedHashMap<String, Route>();
    public final List<String> notes = new ArrayList<String>();

    private GenPlan() {}

    public static GenPlan read(Path file, ObjectMapper om) throws Bad {
        try {
            if (!Files.isRegularFile(file)) throw new Bad("plan file not found");
            if (Files.size(file) > MAX_BYTES) throw new Bad("plan file is larger than " + MAX_BYTES + " bytes");
            try (InputStream in = Files.newInputStream(file)) {
                return read(om.readTree(in));
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new Bad("plan file is not valid JSON");
        } catch (IOException e) {
            throw new Bad("plan file could not be read");
        }
    }

    public static GenPlan read(JsonNode n) throws Bad {
        if (n == null || !n.isObject()) throw new Bad("not a test plan: expected a JSON object");
        if (!TestPlan.FORMAT.equals(n.path("format").asText(null)) || !n.path("format").isTextual()) {
            throw new Bad("not a test plan: format must be \"" + TestPlan.FORMAT + "\" (write one with `gatf plan`)");
        }
        if (!n.path("version").isIntegralNumber() || n.path("version").asLong() != TestPlan.VERSION) {
            throw new Bad("unsupported test plan version: only version " + TestPlan.VERSION + " is supported");
        }
        GenPlan p = new GenPlan();
        String o = str(n.path("source").path("origin"), 300);
        String canon = CrawlMap.origin(o);
        if (o.isEmpty() || canon == null || !canon.equals(o)) throw new Bad("the plan's source.origin is missing or not an http(s) origin");
        p.origin = canon;
        if (!n.path("routes").isArray()) throw new Bad("the plan has no routes array");
        for (JsonNode r : n.path("routes")) {
            if (p.routes.size() >= MAX_ROUTES) { p.notes.add("only the first " + MAX_ROUTES + " routes are read"); break; }
            if (!r.isObject()) continue;
            Route rt = new Route();
            rt.id = str(r.path("id"), MAX_ID);
            if (rt.id.isEmpty() || p.byId.containsKey(rt.id)) { p.notes.add("a route with a missing or duplicate id was ignored"); continue; }
            rt.url = r.path("url").isTextual() ? str(r.path("url"), MAX_STRING) : null;
            rt.navigable = r.path("navigable").asBoolean(false) && r.path("navigable").isBoolean();
            rt.title = str(r.path("title"), MAX_STRING);
            rt.titleSafe = r.path("titleSafe").isBoolean() && r.path("titleSafe").asBoolean();
            for (JsonNode e : r.path("elements")) {
                if (rt.elements.size() >= MAX_ELEMENTS) break;
                if (!e.isObject()) continue;
                El el = new El();
                el.ref = str(e.path("ref"), MAX_ID);
                if (el.ref.isEmpty() || rt.elements.containsKey(el.ref)) continue;
                el.kind = str(e.path("kind"), 16);
                el.tag = str(e.path("tag"), 16);
                el.type = str(e.path("type"), 16);
                el.method = e.path("method").isTextual() ? str(e.path("method"), 16) : null;
                el.form = e.path("form").isIntegralNumber() ? e.path("form").asInt() : 0;
                el.options = e.path("options").isIntegralNumber() ? Math.max(0, Math.min(10_000, e.path("options").asInt())) : 0;
                el.required = e.path("required").isBoolean() && e.path("required").asBoolean();
                el.text = str(e.path("text"), MAX_STRING);
                el.textSafe = e.path("textSafe").isBoolean() && e.path("textSafe").asBoolean();
                el.locator = e.path("locator").isTextual() ? str(e.path("locator"), MAX_STRING) : null;
                for (JsonNode a : e.path("alternatives")) {
                    if (el.alternatives.size() >= MAX_ALTERNATIVES) break;
                    el.alternatives.add(a.isTextual() ? str(a, MAX_STRING) : null);   // keep positions: use-alternative k
                }
                rt.elements.put(el.ref, el);
            }
            for (JsonNode j : r.path("journeys")) {
                if (rt.journeys.size() >= MAX_JOURNEYS) { p.notes.add("route " + safeId(rt.id) + ": only the first " + MAX_JOURNEYS + " journeys are read"); break; }
                if (!j.isObject()) continue;
                Journey jr = new Journey();
                jr.id = str(j.path("id"), MAX_ID);
                jr.goal = str(j.path("goal"), 32);
                jr.sideEffects = j.path("sideEffects").isBoolean() && j.path("sideEffects").asBoolean();
                for (JsonNode s : j.path("steps")) {
                    if (jr.steps.size() >= TestPlan.MAX_STEPS) { jr.stepsDropped++; continue; }
                    jr.steps.add(step(s));
                }
                rt.journeys.add(jr);
            }
            p.routes.add(rt);
            p.byId.put(rt.id, rt);
        }
        return p;
    }

    /** One step reduced to the known fields, by type: {@code intent, route, ref, on} (strings), {@code data}, {@code option}. */
    static Map<String, Object> step(JsonNode s) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        if (!s.isObject()) { m.put("intent", ""); return m; }
        m.put("intent", str(s.path("intent"), 32));
        for (String k : new String[] { "route", "ref", "on", "data" }) if (s.path(k).isTextual()) m.put(k, str(s.path(k), MAX_ID));
        if (s.path("option").isIntegralNumber() && s.path("option").canConvertToInt()) m.put("option", s.path("option").asInt());
        else if (s.has("option")) m.put("option", -1);
        return m;
    }

    static String str(JsonNode n, int cap) {
        if (n == null || !n.isTextual()) return "";
        String s = n.asText();
        return s.length() > cap ? s.substring(0, cap) : s;
    }

    /** An id as GATF may print it (notes, comments, file names): [A-Za-z0-9_-] only, capped; "x" when nothing is left. */
    public static String safeId(String id) {
        String s = id == null ? "" : id.replaceAll("[^A-Za-z0-9_-]", "");
        if (s.length() > 40) s = s.substring(0, 40);
        return s.isEmpty() ? "x" : s;
    }
}
