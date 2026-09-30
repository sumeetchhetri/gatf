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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Q9 — performance budgets for UI scripts, evaluated from the trace a script recorded.
 *
 * <p>Spec: {@code metric<limit} or {@code metric<=limit}, comma- or space-separated, from gatf-config
 * {@code perfBudgets} and/or a script's leading comment lines ({@code // @budget lcp<2500 requests<60}); a script
 * budget overrides the config one for the same metric. Limits take units: {@code ms}/{@code s} for times,
 * {@code b}/{@code kb}/{@code mb} for sizes (bare numbers are ms / bytes).
 *
 * <p>Metrics: web vitals ({@code lcp fcp ttfb dcl load} in ms, {@code cls}) of the worst page the script visited when
 * the trace has per-navigation vitals ({@code webVitalsByPage}), else of the page open when the trace stopped,
 * the peak JS heap ({@code jsheap}), and over the whole script: {@code requests} (count), {@code transfer} (bytes on
 * the wire, falling back to decoded size), {@code failedRequests}, {@code consoleErrors}. A metric the browser did
 * not report is "not measured" — a warning, not a failure.
 */
public final class PerfBudget {

    public enum Outcome { PASS, BREACH, NOT_MEASURED }

    public static final class Rule {
        public final String metric, op, text;
        public final double limit;

        Rule(String metric, String op, double limit, String text) {
            this.metric = metric;
            this.op = op;
            this.limit = limit;
            this.text = text;
        }

        boolean ok(double v) {
            return "<".equals(op) ? v < limit : v <= limit;
        }
    }

    public static final class Result {
        public final String metric, budget;
        public final Double actual;
        public final Outcome outcome;
        public final String message;

        Result(Rule r, Double actual, Outcome outcome) {
            this.metric = r.metric;
            this.budget = r.text;
            this.actual = actual;
            this.outcome = outcome;
            this.message = outcome == Outcome.NOT_MEASURED ? r.metric + " not measured (budget " + r.text + ")"
                    : r.metric + " " + fmt(r.metric, actual) + (outcome == Outcome.PASS ? " within " : " exceeds budget ") + r.text;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("metric", metric);
            m.put("budget", budget);
            m.put("actual", actual);
            m.put("display", actual == null ? null : fmt(metric, actual));
            m.put("outcome", outcome.name().toLowerCase(Locale.ROOT));
            m.put("message", message);
            return m;
        }
    }

    /** metric → unit kind: t = time (ms), s = size (bytes), n = plain number */
    private static final Map<String, Character> METRICS = new LinkedHashMap<String, Character>();
    static {
        for (String t : new String[] { "lcp", "fcp", "ttfb", "dcl", "load" }) METRICS.put(t, 't');
        METRICS.put("cls", 'n');
        METRICS.put("jsheap", 's');
        METRICS.put("transfer", 's');
        METRICS.put("requests", 'n');
        METRICS.put("failedrequests", 'n');
        METRICS.put("consoleerrors", 'n');
    }

    private static final Pattern RULE = Pattern.compile("([A-Za-z]+)\\s*(<=|<)\\s*([0-9]*\\.?[0-9]+)\\s*([A-Za-z]*)");

    private PerfBudget() {}

    /** Parses a budget spec; throws IllegalArgumentException naming the bad part and the valid metrics. */
    public static List<Rule> parse(String spec) {
        List<Rule> out = new ArrayList<Rule>();
        if (spec == null || spec.isBlank()) return out;
        for (String part : spec.trim().split("[,;]|\\s+(?=[A-Za-z]+\\s*<)")) {
            String p = part.trim();
            if (p.isEmpty()) continue;
            Matcher m = RULE.matcher(p);
            if (!m.matches()) throw new IllegalArgumentException("Invalid budget '" + p + "' - expected metric<limit, e.g. lcp<2500");
            String metric = m.group(1).toLowerCase(Locale.ROOT);
            Character kind = METRICS.get(metric);
            if (kind == null) {
                throw new IllegalArgumentException("Unknown budget metric '" + m.group(1) + "' - expected one of: lcp, fcp, ttfb, dcl, "
                        + "load, cls, jsheap, transfer, requests, failedRequests, consoleErrors");
            }
            double v = Double.parseDouble(m.group(3));
            String unit = m.group(4).toLowerCase(Locale.ROOT);
            if (kind == 't') {
                if (unit.equals("s")) v *= 1000;
                else if (!unit.isEmpty() && !unit.equals("ms")) throw new IllegalArgumentException("Budget '" + p + "': time unit must be ms or s");
            } else if (kind == 's') {
                if (unit.equals("kb")) v *= 1024;
                else if (unit.equals("mb")) v *= 1024 * 1024;
                else if (!unit.isEmpty() && !unit.equals("b")) throw new IllegalArgumentException("Budget '" + p + "': size unit must be b, kb or mb");
            } else if (!unit.isEmpty()) {
                throw new IllegalArgumentException("Budget '" + p + "': " + metric + " takes no unit");
            }
            out.add(new Rule(metric, m.group(2), v, m.group(1) + m.group(2) + m.group(3) + m.group(4)));
        }
        return out;
    }

    /** Config rules overridden, per metric, by the script's rules. */
    public static List<Rule> merge(List<Rule> config, List<Rule> script) {
        Map<String, Rule> m = new LinkedHashMap<String, Rule>();
        for (Rule r : config) m.put(r.metric, r);
        for (Rule r : script) m.put(r.metric, r);
        return new ArrayList<Rule>(m.values());
    }

    /** The {@code @budget …} specs in a script's leading comment lines, joined. */
    public static String headerSpec(List<String> lines) {
        StringBuilder b = new StringBuilder();
        for (String l : lines) {
            String t = l.trim();
            if (t.isEmpty()) continue;
            if (!t.startsWith("//")) break;
            int at = t.indexOf("@budget");
            if (at >= 0) b.append(b.length() > 0 ? "," : "").append(t.substring(at + 7).trim());
        }
        return b.toString();
    }

    public static List<Result> evaluate(List<Rule> rules, Map<String, Double> measured) {
        List<Result> out = new ArrayList<Result>();
        for (Rule r : rules) {
            Double v = measured.get(r.metric);
            out.add(new Result(r, v, v == null ? Outcome.NOT_MEASURED : (r.ok(v) ? Outcome.PASS : Outcome.BREACH)));
        }
        return out;
    }

    /** Reads the metrics from a trace working directory (performance.json, network.har, console.ndjson). */
    public static Map<String, Double> measure(Path traceDir, ObjectMapper om) {
        Map<String, Double> m = new LinkedHashMap<String, Double>();
        try {
            Path perf = traceDir.resolve("performance.json");
            if (Files.isRegularFile(perf)) {
                JsonNode p = om.readTree(perf.toFile());
                JsonNode wv = p.path("webVitals");
                if (wv.isTextual()) wv = om.readTree(wv.asText());
                num(m, "lcp", wv.get("lcp"));
                num(m, "fcp", wv.get("fcp"));
                num(m, "ttfb", wv.get("ttfb"));
                num(m, "dcl", wv.get("domContentLoaded"));
                num(m, "load", wv.get("load"));
                num(m, "cls", wv.get("cls"));
                // Q9: per-navigation vitals present -> each web-vital budget applies to the worst page
                JsonNode pages = p.path("webVitalsByPage");
                if (pages.isArray() && pages.size() > 0) {
                    for (String[] k : new String[][] { { "lcp", "lcp" }, { "fcp", "fcp" }, { "ttfb", "ttfb" },
                            { "dcl", "domContentLoaded" }, { "load", "load" }, { "cls", "cls" } }) {
                        Double w = null;
                        for (JsonNode pg : pages) {
                            JsonNode v = pg.get(k[1]);
                            if (v != null && v.isNumber() && v.asDouble() >= 0) w = w == null ? v.asDouble() : Math.max(w, v.asDouble());
                        }
                        if (w != null) m.put(k[0], w);
                    }
                }
                double heap = -1;
                for (JsonNode s : p.path("samples")) {
                    JsonNode h = s.get("JSHeapUsedSize");
                    if (h != null && h.isNumber()) heap = Math.max(heap, h.asDouble());
                }
                if (heap >= 0) m.put("jsheap", heap);
            }
        } catch (Exception ignored) {
            // unreadable performance data: those metrics stay "not measured"
        }
        try {
            Path har = traceDir.resolve("network.har");
            if (Files.isRegularFile(har)) {
                JsonNode entries = om.readTree(har.toFile()).path("log").path("entries");
                double transfer = 0, failed = 0;
                for (JsonNode e : entries) {
                    long wire = e.path("response").path("bodySize").asLong(-1);
                    transfer += wire > 0 ? wire : Math.max(0, e.path("response").path("content").path("size").asLong(0));
                    if (e.has("_failed")) failed++;
                }
                m.put("requests", (double) entries.size());
                m.put("transfer", transfer);
                m.put("failedrequests", failed);
            }
        } catch (Exception ignored) {
        }
        try {
            Path con = traceDir.resolve("console.ndjson");
            if (Files.isRegularFile(con)) {
                double errors = 0;
                try (BufferedReader r = Files.newBufferedReader(con, StandardCharsets.UTF_8)) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        if (line.isBlank()) continue;
                        String lvl = om.readTree(line).path("level").asText("").toLowerCase(Locale.ROOT);
                        if (lvl.equals("error") || lvl.equals("severe")) errors++;
                    }
                }
                m.put("consoleerrors", errors);
            }
        } catch (Exception ignored) {
        }
        return m;
    }

    private static void num(Map<String, Double> m, String k, JsonNode v) {
        if (v != null && v.isNumber() && v.asDouble() >= 0) m.put(k, v.asDouble());
    }

    static String fmt(String metric, Double v) {
        if (v == null) return "-";
        Character kind = METRICS.get(metric);
        if (kind != null && kind == 't') return Math.round(v) + "ms";
        if (kind != null && kind == 's') {
            if (v >= 1024 * 1024) return String.format(Locale.ROOT, "%.1fmb", v / (1024 * 1024));
            if (v >= 1024) return String.format(Locale.ROOT, "%.1fkb", v / 1024);
            return Math.round(v) + "b";
        }
        return v == Math.rint(v) ? String.valueOf(Math.round(v)) : String.format(Locale.ROOT, "%.3f", v);
    }
}
