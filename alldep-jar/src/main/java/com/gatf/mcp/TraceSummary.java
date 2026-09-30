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
package com.gatf.mcp;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * M14 {@code summarise_trace}: condenses a trace (the {@code *-trace.zip}, or its unzipped directory) into what an
 * agent needs to debug a failure — the last actions, console errors, failed and slow requests, the passive checks
 * ({@code checks.json}) and the web vitals — without shipping the whole bundle. Pure: Jackson + java.util.zip.
 */
public final class TraceSummary {

    private static final long MAX_FILE = 64L * 1024 * 1024;
    private static final int MAX_TEXT = 500;

    private TraceSummary() {}

    public static Map<String, Object> summarise(Path trace, int limit, ObjectMapper om) throws IOException {
        if (trace == null || !Files.exists(trace)) throw new McpTool.ToolError("trace not found: " + trace);
        int n = Math.max(1, limit);
        Map<String, byte[]> f = new LinkedHashMap<String, byte[]>();
        List<String> names = new ArrayList<String>();
        String[] wanted = { "metadata.json", "events.ndjson", "console.ndjson", "network.har", "checks.json", "performance.json" };
        if (Files.isDirectory(trace)) {
            try (Stream<Path> s = Files.walk(trace)) {
                s.filter(Files::isRegularFile).forEach(p -> names.add(trace.relativize(p).toString().replace('\\', '/')));
            }
            for (String w : wanted) {
                Path p = trace.resolve(w);
                if (Files.isRegularFile(p) && Files.size(p) <= MAX_FILE) f.put(w, Files.readAllBytes(p));
            }
        } else {
            try (ZipFile z = new ZipFile(trace.toFile())) {
                for (Enumeration<? extends ZipEntry> en = z.entries(); en.hasMoreElements();) {
                    ZipEntry e = en.nextElement();
                    if (!e.isDirectory()) names.add(e.getName());
                }
                for (String w : wanted) {
                    ZipEntry e = z.getEntry(w);
                    if (e == null || e.getSize() > MAX_FILE) continue;
                    try (InputStream in = z.getInputStream(e)) {
                        byte[] b = in.readNBytes((int) MAX_FILE + 1);   // the declared size can lie: cap what is inflated
                        if (b.length <= MAX_FILE) f.put(w, b);
                    }
                }
            } catch (java.util.zip.ZipException ze) {
                throw new McpTool.ToolError("not a trace zip: " + trace + " (" + ze.getMessage() + ")");
            }
        }
        Collections.sort(names);

        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("trace", trace.toString());
        JsonNode meta = tree(om, f.get("metadata.json"));
        if (meta != null) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            for (String k : new String[] { "runId", "selFile", "browser", "protocol" }) if (meta.hasNonNull(k)) m.put(k, meta.get(k).asText());
            if (meta.has("startTs") && meta.has("endTs")) m.put("durationMs", meta.get("endTs").asLong() - meta.get("startTs").asLong());
            out.put("metadata", m);
        }

        // actions: the last N steps, and every event that looks like an error
        List<JsonNode> events = ndjson(om, f.get("events.ndjson"));
        List<Map<String, Object>> actions = new ArrayList<Map<String, Object>>();
        List<Map<String, Object>> evErrors = new ArrayList<Map<String, Object>>();
        int actionCount = 0;
        for (JsonNode e : events) {
            String type = e.path("type").asText("");
            String name = e.path("name").asText("");
            String detail = e.path("detail").asText("");
            if ("action".equals(type)) { actionCount++; actions.add(ev(e)); }
            if (isErrorish(name) || isErrorish(detail)) evErrors.add(ev(e));
        }
        Map<String, Object> act = new LinkedHashMap<String, Object>();
        act.put("count", actionCount);
        act.put("last", tail(actions, n));
        if (!evErrors.isEmpty()) act.put("errors", tail(evErrors, n));
        out.put("actions", act);

        // console: counts by level + the first N errors/warnings
        List<JsonNode> console = ndjson(om, f.get("console.ndjson"));
        Map<String, Integer> levels = new TreeMap<String, Integer>();
        List<Map<String, Object>> cerr = new ArrayList<Map<String, Object>>();
        for (JsonNode c : console) {
            String lv = c.path("level").asText("log").toLowerCase(java.util.Locale.ROOT);
            levels.merge(lv, 1, Integer::sum);
            if ((lv.equals("error") || lv.equals("severe") || lv.equals("warning") || lv.equals("warn")) && cerr.size() < n) {
                Map<String, Object> m = new LinkedHashMap<String, Object>();
                m.put("level", lv);
                m.put("text", clip(c.path("text").asText("")));
                if (c.hasNonNull("url")) m.put("at", c.get("url").asText() + (c.hasNonNull("line") ? ":" + c.get("line").asText() : ""));
                cerr.add(m);
            }
        }
        Map<String, Object> con = new LinkedHashMap<String, Object>();
        con.put("levels", levels);
        con.put("problems", cerr);
        out.put("console", con);

        // network: totals, failed requests, the slowest
        JsonNode har = tree(om, f.get("network.har"));
        if (har != null) {
            List<Map<String, Object>> failed = new ArrayList<Map<String, Object>>();
            List<JsonNode> all = new ArrayList<JsonNode>();
            for (JsonNode e : har.path("log").path("entries")) {
                all.add(e);
                int st = e.path("response").path("status").asInt(0);
                if ((st >= 400 || st <= 0 || e.path("_failed").asBoolean(false)) && failed.size() < n) failed.add(req(e));
            }
            all.sort((a, b) -> Double.compare(b.path("time").asDouble(0), a.path("time").asDouble(0)));
            List<Map<String, Object>> slow = new ArrayList<Map<String, Object>>();
            for (int i = 0; i < Math.min(n, all.size()); i++) slow.add(req(all.get(i)));
            Map<String, Object> net = new LinkedHashMap<String, Object>();
            net.put("requests", all.size());
            net.put("failed", failed);
            net.put("slowest", slow);
            out.put("network", net);
        }

        // passive checks: counts per section + the failures verbatim
        JsonNode checks = tree(om, f.get("checks.json"));
        if (checks != null && checks.isObject()) {
            Map<String, Object> ch = new LinkedHashMap<String, Object>();
            for (Iterator<Map.Entry<String, JsonNode>> it = checks.fields(); it.hasNext();) {
                Map.Entry<String, JsonNode> e = it.next();
                if (e.getValue().isArray()) ch.put(e.getKey() + "Count", e.getValue().size());
            }
            JsonNode fl = checks.get("failures");
            if (fl != null && fl.isArray() && fl.size() > 0) ch.put("failures", om.convertValue(head(fl, n), List.class));
            out.put("checks", ch);
        }

        JsonNode perf = tree(om, f.get("performance.json"));
        if (perf != null && perf.has("webVitals")) out.put("webVitals", om.convertValue(perf.get("webVitals"), Object.class));

        List<String> shots = new ArrayList<String>();
        for (String nm : names) if (nm.startsWith("screenshots/") || nm.endsWith(".png")) shots.add(nm);
        Map<String, Object> files = new LinkedHashMap<String, Object>();
        files.put("count", names.size());
        files.put("screenshots", tail(shots, n));
        files.put("video", names.contains("video.mp4"));
        out.put("files", files);
        return out;
    }

    static boolean isErrorish(String s) {
        if (s == null || s.isEmpty()) return false;
        String l = s.toLowerCase(java.util.Locale.ROOT);
        return l.contains("error") || l.contains("exception") || l.contains("fail") || l.contains("timeout") || l.contains("timed out")
                || l.contains("not found");
    }

    private static Map<String, Object> ev(JsonNode e) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("ts", e.path("ts").asLong());
        m.put("type", e.path("type").asText(""));
        if (e.hasNonNull("name")) m.put("name", clip(e.get("name").asText()));
        if (e.hasNonNull("detail")) m.put("detail", clip(e.get("detail").asText()));
        return m;
    }

    private static Map<String, Object> req(JsonNode e) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("method", e.path("request").path("method").asText(""));
        m.put("url", clip(e.path("request").path("url").asText("")));
        m.put("status", e.path("response").path("status").asInt(0));
        m.put("timeMs", Math.round(e.path("time").asDouble(0)));
        if (e.hasNonNull("_failed")) m.put("failed", e.get("_failed").asText());
        return m;
    }

    private static String clip(String s) { return s.length() > MAX_TEXT ? s.substring(0, MAX_TEXT) + "…" : s; }

    private static <T> List<T> tail(List<T> l, int n) { return l.size() <= n ? l : new ArrayList<T>(l.subList(l.size() - n, l.size())); }

    private static List<JsonNode> head(JsonNode arr, int n) {
        List<JsonNode> l = new ArrayList<JsonNode>();
        for (JsonNode x : arr) { if (l.size() >= n) break; l.add(x); }
        return l;
    }

    private static JsonNode tree(ObjectMapper om, byte[] b) {
        if (b == null) return null;
        try { return om.readTree(b); } catch (Exception e) { return null; }
    }

    private static List<JsonNode> ndjson(ObjectMapper om, byte[] b) {
        List<JsonNode> out = new ArrayList<JsonNode>();
        if (b == null) return out;
        for (String line : new String(b, StandardCharsets.UTF_8).split("\n")) {
            if (line.trim().isEmpty()) continue;
            try { out.add(om.readTree(line)); } catch (Exception ignore) { /* a torn last line */ }
        }
        out.sort((a, c) -> Long.compare(a.path("ts").asLong(), c.path("ts").asLong()));
        return out;
    }
}
