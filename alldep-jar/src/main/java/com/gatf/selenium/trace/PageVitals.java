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
package com.gatf.selenium.trace;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Q9 — per-navigation web vitals. Collects the flat entries {@link WebVitals#PRELOAD_JS} reports as pages are left
 * (and the final page's {@link WebVitals#ASYNC_JS} read), de-duplicates them by document id (a page flushed on
 * {@code visibilitychange} and again on {@code pagehide} counts once, last report wins) and renders the
 * {@code webVitalsByPage} array of performance.json: {@code [{url,ts,lcp,cls,fcp,ttfb,domContentLoaded,load}]},
 * oldest first. Thread-safe (CDP binding events arrive on the DevTools thread). No external JSON library: the
 * entries are flat objects, parsed by the small reader below.
 */
public final class PageVitals {

    static final String[] KEYS = { "lcp", "cls", "fcp", "ttfb", "domContentLoaded", "load" };

    private final Map<String, Map<String, Object>> byId = new LinkedHashMap<>();
    private int anon;

    /** Adds one entry object, or an array of them, as JSON; malformed input is ignored. */
    public synchronized void add(String json) {
        if (json == null || json.isBlank()) return;
        List<Map<String, Object>> list;
        try { list = parse(json); } catch (RuntimeException e) { return; }
        for (Map<String, Object> m : list) put(m);
    }

    private void put(Map<String, Object> m) {
        Object id = m.get("id");
        String key = id instanceof String && !((String) id).isEmpty() ? (String) id : "#" + (anon++);
        byId.remove(key);
        byId.put(key, m);
    }

    public synchronized int size() { return byId.size(); }

    /**
     * The {@code webVitalsByPage} JSON array, with the final page's vitals ({@code finalJson}, may be null) merged in;
     * an entry without {@code ts} gets {@code stopTs}. Entries are ordered by ts; {@code "[]"} when nothing is known.
     */
    public synchronized String toJson(String finalJson, long stopTs) {
        Map<String, Map<String, Object>> all = new LinkedHashMap<>(byId);
        if (finalJson != null && !finalJson.isBlank()) {
            try {
                for (Map<String, Object> m : parse(finalJson)) {
                    Object id = m.get("id");
                    String key = id instanceof String && !((String) id).isEmpty() ? (String) id : "#final";
                    all.remove(key);
                    all.put(key, m);
                }
            } catch (RuntimeException ignored) {}
        }
        List<Map<String, Object>> rows = new ArrayList<>(all.values());
        for (Map<String, Object> r : rows) if (!(r.get("ts") instanceof Number)) r.put("ts", stopTs);
        rows.sort((a, b) -> Long.compare(((Number) a.get("ts")).longValue(), ((Number) b.get("ts")).longValue()));
        StringBuilder b = new StringBuilder("[");
        for (Map<String, Object> r : rows) {
            if (b.length() > 1) b.append(',');
            Json.Obj o = new Json.Obj().put("url", r.get("url") instanceof String ? (String) r.get("url") : null)
                    .put("ts", ((Number) r.get("ts")).longValue());
            for (String k : KEYS) {
                Object v = r.get(k);
                if (v instanceof Number && Double.isFinite(((Number) v).doubleValue())) o.putRaw(k, num((Number) v));
            }
            b.append(o.end());
        }
        return b.append(']').toString();
    }

    private static String num(Number n) {
        double d = n.doubleValue();
        return d == Math.rint(d) && Math.abs(d) < 1e15 ? Long.toString((long) d) : Double.toString(d);
    }

    // --- minimal reader: an object of scalar values, or an array of such objects ---

    static List<Map<String, Object>> parse(String s) {
        int[] i = { 0 };
        List<Map<String, Object>> out = new ArrayList<>();
        ws(s, i);
        if (peek(s, i) == '[') {
            i[0]++;
            ws(s, i);
            if (peek(s, i) == ']') { i[0]++; return out; }
            while (true) {
                ws(s, i);
                out.add(obj(s, i));
                ws(s, i);
                char c = next(s, i);
                if (c == ']') break;
                if (c != ',') throw new IllegalArgumentException("expected , or ] at " + (i[0] - 1));
            }
        } else {
            out.add(obj(s, i));
        }
        return out;
    }

    private static Map<String, Object> obj(String s, int[] i) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (next(s, i) != '{') throw new IllegalArgumentException("expected { at " + (i[0] - 1));
        ws(s, i);
        if (peek(s, i) == '}') { i[0]++; return m; }
        while (true) {
            ws(s, i);
            if (next(s, i) != '"') throw new IllegalArgumentException("expected key at " + (i[0] - 1));
            String k = str(s, i);
            ws(s, i);
            if (next(s, i) != ':') throw new IllegalArgumentException("expected : at " + (i[0] - 1));
            ws(s, i);
            m.put(k, scalar(s, i));
            ws(s, i);
            char c = next(s, i);
            if (c == '}') return m;
            if (c != ',') throw new IllegalArgumentException("expected , or } at " + (i[0] - 1));
        }
    }

    private static Object scalar(String s, int[] i) {
        char c = peek(s, i);
        if (c == '"') { i[0]++; return str(s, i); }
        if (s.startsWith("true", i[0])) { i[0] += 4; return Boolean.TRUE; }
        if (s.startsWith("false", i[0])) { i[0] += 5; return Boolean.FALSE; }
        if (s.startsWith("null", i[0])) { i[0] += 4; return null; }
        int st = i[0];
        while (i[0] < s.length() && "+-.eE0123456789".indexOf(s.charAt(i[0])) >= 0) i[0]++;
        if (st == i[0]) throw new IllegalArgumentException("unsupported value at " + st);
        return Double.parseDouble(s.substring(st, i[0]));
    }

    private static String str(String s, int[] i) {
        StringBuilder b = new StringBuilder();
        while (true) {
            char c = next(s, i);
            if (c == '"') return b.toString();
            if (c != '\\') { b.append(c); continue; }
            char e = next(s, i);
            switch (e) {
                case 'n': b.append('\n'); break;
                case 't': b.append('\t'); break;
                case 'r': b.append('\r'); break;
                case 'b': b.append('\b'); break;
                case 'f': b.append('\f'); break;
                case 'u':
                    if (i[0] + 4 > s.length()) throw new IllegalArgumentException("bad escape");
                    b.append((char) Integer.parseInt(s.substring(i[0], i[0] + 4), 16));
                    i[0] += 4;
                    break;
                default: b.append(e);
            }
        }
    }

    private static void ws(String s, int[] i) {
        while (i[0] < s.length() && Character.isWhitespace(s.charAt(i[0]))) i[0]++;
    }

    private static char peek(String s, int[] i) {
        if (i[0] >= s.length()) throw new IllegalArgumentException("unexpected end");
        return s.charAt(i[0]);
    }

    private static char next(String s, int[] i) {
        char c = peek(s, i);
        i[0]++;
        return c;
    }
}
