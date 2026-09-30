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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Q9/Q10 — runs the performance budgets and the passive security scan over a stopped trace, writes
 * {@code checks.json} into the trace (so it travels in trace.zip) and returns what the report needs, including the
 * failure message when a budget is breached or ({@code securityChecks=fail}) a medium/high finding exists.
 */
public final class TraceChecks {

    public static final String[] SECURITY_MODES = { "report", "fail", "off" };

    private static final ObjectMapper OM = new ObjectMapper();

    public static final class Report {
        public final List<PerfBudget.Result> budgets = new ArrayList<PerfBudget.Result>();
        public final List<SecurityScan.Finding> findings = new ArrayList<SecurityScan.Finding>();
        public final List<String> failures = new ArrayList<String>();
        public boolean securityScanned;
        /** M10: {@code a11y check} results the script left in the trace dir ({@code a11y-<n>.json}). */
        public final List<Map<String, Object>> a11y = new ArrayList<Map<String, Object>>();

        /** null when nothing fails the script. */
        public String failure() {
            return failures.isEmpty() ? null : String.join("; ", failures);
        }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            List<Map<String, Object>> b = new ArrayList<Map<String, Object>>();
            for (PerfBudget.Result r : budgets) b.add(r.toMap());
            List<Map<String, Object>> f = new ArrayList<Map<String, Object>>();
            for (SecurityScan.Finding x : findings) f.add(x.toMap());
            m.put("budgets", b);
            m.put("security", securityScanned ? f : null);
            m.put("failure", failure());
            if (!a11y.isEmpty()) m.put("a11y", a11y);
            return m;
        }

        /** One console line, e.g. "budgets 1/3 breached · security 2 medium, 3 low". */
        public String summary() {
            List<String> parts = new ArrayList<String>();
            if (!budgets.isEmpty()) {
                long breached = budgets.stream().filter(r -> r.outcome == PerfBudget.Outcome.BREACH).count();
                long nm = budgets.stream().filter(r -> r.outcome == PerfBudget.Outcome.NOT_MEASURED).count();
                parts.add("budgets " + breached + "/" + budgets.size() + " breached" + (nm > 0 ? " (" + nm + " not measured)" : ""));
            }
            if (securityScanned) {
                Map<String, Integer> by = new LinkedHashMap<String, Integer>();
                for (String s : new String[] { "high", "medium", "low" }) by.put(s, 0);
                for (SecurityScan.Finding x : findings) by.merge(x.severity, 1, Integer::sum);
                List<String> s = new ArrayList<String>();
                by.forEach((k, v) -> { if (v > 0) s.add(v + " " + k); });
                parts.add("security " + (s.isEmpty() ? "no findings" : String.join(", ", s)));
            }
            return String.join(" · ", parts);
        }
    }

    private TraceChecks() {}

    /** M10: the {@code a11y-<n>.json} files in the trace dir, in step order. */
    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> a11yResults(Path traceDir) {
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        java.io.File[] fs = traceDir == null ? null : traceDir.toFile().listFiles((d, n) -> n.matches("a11y-\\d+\\.json"));
        if (fs == null) return out;
        java.util.Arrays.sort(fs, java.util.Comparator.comparingInt(f -> Integer.parseInt(f.getName().substring(5, f.getName().length() - 5))));
        for (java.io.File f : fs) {
            try {
                out.add(OM.readValue(f, Map.class));
            } catch (Exception e) {
                // unreadable: the report sidecar still has the result from the session
            }
        }
        return out;
    }

    /** Validates/normalises a securityChecks value; blank → null (= report). */
    public static String normalizeSecurityMode(String v) {
        if (v == null || v.trim().isEmpty()) return null;
        String t = v.trim().toLowerCase(Locale.ROOT);
        for (String m : SECURITY_MODES) if (m.equals(t)) return t;
        throw new IllegalArgumentException("Invalid securityChecks '" + v + "' - expected one of: " + String.join(", ", SECURITY_MODES));
    }

    /**
     * @param configBudgets gatf-config perfBudgets (already validated at config load)
     * @param scriptLines   the script's lines (its leading comments may hold {@code @budget})
     * @param securityMode  report | fail | off (null = report)
     * @return null when there is nothing to check
     */
    public static Report run(Path traceDir, String configBudgets, List<String> scriptLines, String securityMode) {
        String mode = securityMode == null || securityMode.isBlank() ? "report" : securityMode.trim().toLowerCase(Locale.ROOT);
        Report rep = new Report();
        List<PerfBudget.Rule> rules;
        try {
            rules = PerfBudget.merge(PerfBudget.parse(configBudgets),
                    PerfBudget.parse(PerfBudget.headerSpec(scriptLines == null ? List.of() : scriptLines)));
        } catch (IllegalArgumentException e) {
            rules = List.of();
            rep.failures.add("invalid @budget: " + e.getMessage());
        }
        rep.a11y.addAll(a11yResults(traceDir));
        if (rules.isEmpty() && "off".equals(mode) && rep.failures.isEmpty() && rep.a11y.isEmpty()) return null;
        if (!rules.isEmpty()) {
            rep.budgets.addAll(PerfBudget.evaluate(rules, PerfBudget.measure(traceDir, OM)));
            for (PerfBudget.Result r : rep.budgets) {
                if (r.outcome == PerfBudget.Outcome.BREACH) rep.failures.add("budget: " + r.message);
            }
        }
        if (!"off".equals(mode)) {
            rep.securityScanned = true;
            rep.findings.addAll(SecurityScan.scan(traceDir, OM));
            if ("fail".equals(mode)) {
                List<SecurityScan.Finding> serious = new ArrayList<SecurityScan.Finding>();
                for (SecurityScan.Finding f : rep.findings) if (SecurityScan.rank(f.severity) >= 2) serious.add(f);
                if (!serious.isEmpty()) {
                    rep.failures.add("security: " + serious.size() + " medium/high finding(s), first: " + serious.get(0));
                }
            }
        }
        try {
            Files.write(traceDir.resolve("checks.json"), OM.writerWithDefaultPrettyPrinter().writeValueAsBytes(rep.toMap()));
        } catch (Exception ignored) {
            // the report still carries the result
        }
        return rep;
    }
}
