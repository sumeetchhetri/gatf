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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * M10: maps an axe-core result (bundled {@code gatf-axe/axe.min.js}, run by {@code a11y check}) to GATF's report shape:
 * <pre>
 * { url, browser, minImpact, failed,
 *   counts: {critical, serious, moderate, minor}, passes, incomplete,
 *   violations: [ {id, impact, help, description, helpUrl, tags,
 *                  nodes: [ {target: "css selector" (iframe / shadow hops joined with " >>> "), html, summary} ]} ] }
 * </pre>
 * Violations below {@code minImpact} are dropped; {@code failed} = any violation left. It goes to
 * {@code <out>/<browser>-<n>-a11y.json} (an attachment), to the report's checks tab (key {@code a11y}) and to the trace.
 * Pure; the axe JSON comes in as parsed maps.
 */
public final class A11yReport {

    public static final List<String> IMPACTS = List.of("minor", "moderate", "serious", "critical");

    /** The bundled axe-core (resources/gatf-axe/axe.min.js); a page's own axe of another version is replaced. */
    public static final String AXE_VERSION = "4.13.0";

    /** Configures axe in the current document so it can reach cross-origin frames that have axe injected too. */
    public static final String CONFIGURE_JS = "if (window.axe && window.axe.configure) { try { window.axe.configure({allowedOrigins: ['<unsafe_all_origins>']}); } catch (e) {} }";

    /**
     * In-page runner (executeAsyncScript). arguments[0] = include selectors, arguments[1] = exclude selectors; calls
     * back with a JSON string (so the driver never converts axe's large object graph), or {@code {"error": …}}.
     */
    public static final String RUN_JS = String.join("\n",
            "var done = arguments[arguments.length - 1], inc = arguments[0] || [], exc = arguments[1] || [];",
            "try {",
            "  if (!window.axe || !window.axe.run) { done(JSON.stringify({error: 'axe-core is not loaded'})); return; }",
            "  var ctx = document;",
            "  if (inc.length || exc.length) {",
            "    ctx = {};",
            "    if (inc.length) ctx.include = inc.map(function (s) { return [s]; });",
            "    if (exc.length) ctx.exclude = exc.map(function (s) { return [s]; });",
            "  }",
            "  window.axe.run(ctx, {resultTypes: ['violations']}).then(function (r) {",
            "    done(JSON.stringify({url: r.url, violations: r.violations,",
            "      passes: (r.passes || []).length, incomplete: (r.incomplete || []).length}));",
            "  }, function (e) { done(JSON.stringify({error: String(e && e.message || e)})); });",
            "} catch (e) { done(JSON.stringify({error: String(e && e.message || e)})); }");

    private A11yReport() {}

    /** 1 (minor) .. 4 (critical); an unknown / missing impact ranks as minor (still counted). */
    public static int rank(Object impact) {
        return impact == null ? 1 : Math.max(1, IMPACTS.indexOf(String.valueOf(impact).toLowerCase(Locale.ROOT)) + 1);
    }

    /** Validates an {@code impact>=} level; null / blank = minor (every violation counts). */
    public static String normalizeImpact(String v) {
        if (v == null || v.trim().isEmpty()) return "minor";
        String t = v.trim().toLowerCase(Locale.ROOT);
        if (!IMPACTS.contains(t)) throw new IllegalArgumentException("impact must be one of " + IMPACTS + ", got '" + v + "'");
        return t;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> map(Map<String, Object> axe, String minImpact, String browser) {
        String min = normalizeImpact(minImpact);
        if (axe != null && axe.get("error") != null) throw new IllegalStateException("axe-core failed: " + axe.get("error"));
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("url", axe == null ? null : axe.get("url"));
        out.put("browser", browser);
        out.put("minImpact", min);
        Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
        for (int i = IMPACTS.size() - 1; i >= 0; i--) counts.put(IMPACTS.get(i), 0);
        List<Map<String, Object>> vs = new ArrayList<Map<String, Object>>();
        Object raw = axe == null ? null : axe.get("violations");
        if (raw instanceof List) {
            for (Object o : (List<Object>) raw) {
                if (!(o instanceof Map)) continue;
                Map<String, Object> v = (Map<String, Object>) o;
                if (rank(v.get("impact")) < rank(min)) continue;
                Map<String, Object> m = new LinkedHashMap<String, Object>();
                m.put("id", v.get("id"));
                m.put("impact", v.get("impact"));
                m.put("help", v.get("help"));
                m.put("description", v.get("description"));
                m.put("helpUrl", v.get("helpUrl"));
                m.put("tags", v.get("tags") instanceof List ? v.get("tags") : new ArrayList<Object>());
                List<Map<String, Object>> nodes = new ArrayList<Map<String, Object>>();
                if (v.get("nodes") instanceof List) {
                    for (Object n : (List<Object>) v.get("nodes")) {
                        if (!(n instanceof Map)) continue;
                        Map<String, Object> nn = (Map<String, Object>) n;
                        Map<String, Object> x = new LinkedHashMap<String, Object>();
                        x.put("target", selector(nn.get("target")));
                        x.put("html", nn.get("html"));
                        x.put("summary", nn.get("failureSummary"));
                        nodes.add(x);
                    }
                }
                m.put("nodes", nodes);
                vs.add(m);
                String imp = String.valueOf(v.get("impact")).toLowerCase(Locale.ROOT);
                if (counts.containsKey(imp)) counts.merge(imp, 1, Integer::sum);
            }
        }
        vs.sort((a, b) -> rank(b.get("impact")) - rank(a.get("impact")));
        out.put("failed", !vs.isEmpty());
        out.put("counts", counts);
        out.put("passes", axe == null || !(axe.get("passes") instanceof Number) ? 0 : ((Number) axe.get("passes")).intValue());
        out.put("incomplete", axe == null || !(axe.get("incomplete") instanceof Number) ? 0 : ((Number) axe.get("incomplete")).intValue());
        out.put("violations", vs);
        return out;
    }

    /**
     * axe {@code target}: a list of selectors, one per frame hop; a hop inside shadow DOM is itself a list. Joined with
     * {@code " >>> "} into one readable selector.
     */
    static String selector(Object target) {
        if (target instanceof List) {
            List<String> parts = new ArrayList<String>();
            for (Object t : (List<?>) target) parts.add(selector(t));
            return String.join(" >>> ", parts);
        }
        return target == null ? "" : String.valueOf(target);
    }

    /** e.g. "3 accessibility violation(s) (1 critical, 2 serious) on https://x/" or "no accessibility violations …". */
    @SuppressWarnings("unchecked")
    public static String summary(Map<String, Object> rep) {
        List<Object> vs = (List<Object>) rep.get("violations");
        String where = rep.get("url") != null ? " on " + rep.get("url") : "";
        if (vs == null || vs.isEmpty()) return "no accessibility violations (impact >= " + rep.get("minImpact") + ")" + where;
        List<String> by = new ArrayList<String>();
        ((Map<String, Integer>) rep.get("counts")).forEach((k, n) -> { if (n > 0) by.add(n + " " + k); });
        return vs.size() + " accessibility violation(s) (" + String.join(", ", by) + ")" + where;
    }

    /** The step's failure message: the summary plus the first violations with their first selector. */
    @SuppressWarnings("unchecked")
    public static String failureMessage(Map<String, Object> rep, String file) {
        StringBuilder b = new StringBuilder(summary(rep));
        List<Map<String, Object>> vs = (List<Map<String, Object>>) rep.get("violations");
        int shown = 0;
        for (Map<String, Object> v : vs) {
            if (shown++ == 5) { b.append("\n  … ").append(vs.size() - 5).append(" more"); break; }
            List<Map<String, Object>> nodes = (List<Map<String, Object>>) v.get("nodes");
            b.append("\n  [").append(v.get("impact")).append("] ").append(v.get("id")).append(": ").append(v.get("help"));
            if (!nodes.isEmpty()) {
                b.append(" at ").append(nodes.get(0).get("target"));
                if (nodes.size() > 1) b.append(" (+").append(nodes.size() - 1).append(")");
            }
        }
        if (file != null) b.append("\n  details: ").append(file);
        return b.toString();
    }

    /** A seleasy locator for {@code include}/{@code exclude} as a CSS selector: {@code css@…}, {@code id@…}, or plain CSS. */
    public static String cssOf(String locator) {
        String s = locator == null ? "" : locator.trim();
        String l = s.toLowerCase(Locale.ROOT);
        String v;
        if (l.startsWith("css@") || l.startsWith("cssselector@")) {
            v = unquote(s.substring(s.indexOf('@') + 1));
        } else if (l.startsWith("id@")) {
            String id = unquote(s.substring(3));
            if (id.matches("[A-Za-z_][A-Za-z0-9_-]*")) v = "#" + id;
            else if (id.indexOf('\'') < 0 && id.indexOf('\\') < 0) v = "[id='" + id + "']";
            else throw new IllegalArgumentException("a11y include/exclude: use css@… for the id '" + id + "'");
        } else if (s.matches("^[A-Za-z]+@.*")) {
            throw new IllegalArgumentException("a11y include/exclude takes css@… or id@… (axe scopes by CSS selector), got '" + s + "'");
        } else {
            v = unquote(s);
        }
        if (v.isEmpty()) throw new IllegalArgumentException("a11y include/exclude needs a selector");
        return v;
    }

    private static String unquote(String s) {
        String t = s.trim();
        if (t.length() >= 2 && (t.charAt(0) == '\'' || t.charAt(0) == '"') && t.charAt(t.length() - 1) == t.charAt(0)) {
            return t.substring(1, t.length() - 1);
        }
        return t;
    }
}
