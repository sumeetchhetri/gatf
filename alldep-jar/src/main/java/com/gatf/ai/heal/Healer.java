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
package com.gatf.ai.heal;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatf.ai.AiClient;
import com.gatf.ai.AiException;
import com.gatf.ai.AiReply;
import com.gatf.ai.heal.SnapshotDom.Node;
import com.gatf.record.PickLocator;
import com.gatf.selenium.LocatorRanking;
import com.gatf.selenium.SemanticLocators;

/**
 * H1 (Δ149) healer core: a failed step + the failing step's DOM snapshot (Δ146/147) → ranked proposals to repair the
 * step, each a <b>diff</b>; the healer never writes a {@code .sel} (Δ150 shows the diff in the editor and the MCP
 * {@code heal} tool, where the user accepts it).
 * <ol>
 * <li><b>Deterministic first.</b> If the step's own locator matches exactly one element in the snapshot, the element
 * was there when the step failed: a wait ({@code ??N <locator>}) is proposed. Otherwise the snapshot's elements are
 * scored against the old locator's words (id, test id, name, accessible name, label, …) and the step's verb, and each
 * good match gets the locator {@link PickLocator#rank} picks for it (testid → role → label → id / name / css, the
 * recorder's order).</li>
 * <li><b>The model second</b>, only when the deterministic pass found nothing (or {@link Options#ai} says always) and
 * an {@link AiClient} is configured. Its reply is reduced to a closed set ({@link HealPrompt#parse}).</li>
 * </ol>
 * Every proposal, from either source, passes the same gates before it exists: the locator is rendered by
 * {@link LocatorRanking#render} / {@link PickLocator#selector} (so {@link LocatorRanking#unsafe} applies), re-read as
 * seleasy, and must resolve strictly ({@link SemanticLocators#resolvesStrictly}) to the intended element in the
 * snapshot via {@link SnapshotResolver}; a navigation must pass {@link PickLocator#gotoUrl} and stay on the snapshot's
 * origin. The repaired line is the original with only the locator token swapped, so verbs and values are never
 * model-written. No AI configured means the deterministic result only, with {@code ai: "not-configured"}. Pure.
 */
public final class Healer {

    static final Set<String> SKIP_TAGS = Set.of("html", "head", "body", "script", "style", "template", "meta", "link", "title",
            "noscript", "br", "wbr", "base");
    static final int MAX_DETERMINISTIC = 5;
    /** Elements ranked at most per heal (each costs a few resolver passes). */
    static final int MAX_RANKED = 25;
    static final int MIN_SCORE = 3;

    /** When to ask the model. */
    public enum AiMode { NEVER, FALLBACK, ALWAYS }

    public static final class Options {
        public String testIdAttribute = SemanticLocators.DEFAULT_TEST_ID_ATTRIBUTE;
        public int waitSeconds = 10;
        public AiMode ai = AiMode.FALLBACK;
        /** Wall-clock budget for the deterministic pass (ms); what is found by then is kept. */
        public long maxMillis = 10_000;
    }

    private final ObjectMapper om;

    public Healer(ObjectMapper om) {
        this.om = om == null ? new ObjectMapper() : om;
    }

    /**
     * @param step     the failing step's line, exactly as in the script
     * @param script   the script's lines (for the diff context; may be null)
     * @param lineNo   1-based line of {@code step} in {@code script} (ignored when it does not hold {@code step})
     * @param file     the script's display name for the diff header (may be null)
     * @param failure  the recorded failure ({@link TraceFailure#read}) or {@link TraceFailure#of}
     * @param ai       the run's AI client; null or not configured means deterministic only
     * @return {@code {status, reason?, ai, aiStatus, step, locator:{token, count}, proposals:[…], usage?}}, status
     *         {@code proposed | none | not-healable | no-snapshot}
     */
    public Map<String, Object> heal(String step, List<String> script, int lineNo, String file, TraceFailure failure, AiClient ai, Options opt) {
        Options o = opt == null ? new Options() : opt;
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("status", "none");
        out.put("ai", ai == null || !ai.configured() ? "not-configured" : "not-needed");
        out.put("aiStatus", ai == null ? "AI not configured" : ai.status());
        out.put("step", step);
        List<Map<String, Object>> proposals = new ArrayList<Map<String, Object>>();
        out.put("proposals", proposals);
        StepLocator sl = StepLocator.find(step);
        if (sl == null) return reason(out, "not-healable", "the step has no single locator to repair");
        if (failure == null || failure.html == null) return reason(out, "no-snapshot", "the trace has no DOM snapshot for the failing step (enable traceDomSnapshots)");
        long deadline = System.currentTimeMillis() + Math.max(100, o.maxMillis);
        SnapshotResolver r = new SnapshotResolver(SnapshotDom.parse(failure.html), o.testIdAttribute);
        int c0 = StepLocator.count(r, sl.by, sl.classifier);
        Map<String, Object> loc = new LinkedHashMap<String, Object>();
        loc.put("token", sl.token());
        loc.put("count", c0);
        out.put("locator", loc);
        Set<String> seen = new HashSet<String>();
        seen.add(sl.line);

        // 1. deterministic: present at failure → wait
        if (SemanticLocators.resolvesStrictly(c0) && !LocatorRanking.unsafe(sl.classifier)) {
            int n = Math.max(1, Math.min(120, o.waitSeconds));
            String w = sl.indent() + "??" + n + " " + sl.token();
            StepLocator wf = StepLocator.find(w);
            if (wf != null && wf.token().equals(sl.token())) {
                add(proposals, seen, proposal("deterministic", "wait", sl.token(), List.of(w, sl.line), "present-at-failure", 100));
            }
        }
        // 2. deterministic: the elements the old locator most likely meant
        List<Node> orig = c0 > 1 ? StepLocator.elements(r, sl.by, sl.classifier) : null;
        List<String> hints = hints(sl.classifier);
        String verb = sl.verb();
        List<Object[]> scored = new ArrayList<Object[]>();
        for (Node e : orig != null ? orig : r.dom().elements()) {
            if (System.currentTimeMillis() > deadline) { out.put("truncated", true); break; }
            if (SKIP_TAGS.contains(e.tag) || r.hidden(e)) continue;
            int s = score(r, e, sl.classifier, hints, verb) + (orig != null ? 5 : 0);
            if (s >= MIN_SCORE) scored.add(new Object[] { s, e });
        }
        scored.sort((x, y) -> Integer.compare((Integer) y[0], (Integer) x[0]));   // stable: document order on ties
        int det = 0, ranked = 0;
        for (Object[] se : scored) {
            if (det >= MAX_DETERMINISTIC || ranked++ >= MAX_RANKED) break;
            if (System.currentTimeMillis() > deadline) { out.put("truncated", true); break; }
            Node e = (Node) se[1];
            String best = rankFor(r, e);
            if (best == null || !StepLocator.sameStepWith(sl, sl.replace(best), best)) continue;
            if (add(proposals, seen, proposal("deterministic", "locator", best, List.of(sl.replace(best)), orig != null ? "strict-among-matches" : "similar-element", (Integer) se[0]))) det++;
        }

        // 3. the model, second
        boolean ask = ai != null && ai.configured() && (o.ai == AiMode.ALWAYS || (o.ai == AiMode.FALLBACK && proposals.isEmpty()));
        if (ask) {
            List<Node> outline = HealPrompt.outline(r);
            try {
                AiReply reply = ai.ask(HealPrompt.build(sl, failure.failure, failure.url, outline, r, om));
                List<Map<String, Object>> cands = HealPrompt.parse(reply.text, om);
                if (cands == null) {
                    out.put("ai", "bad-reply");
                } else {
                    int kept = 0, dropped = 0;
                    for (Map<String, Object> c : cands) {
                        Map<String, Object> p = fromAi(c, sl, r, outline, failure.url);
                        if (p != null && add(proposals, seen, p)) kept++;
                        else dropped++;
                    }
                    out.put("ai", "used");
                    out.put("aiKept", kept);
                    out.put("aiDropped", dropped);
                }
            } catch (AiException e) {
                out.put("ai", e.word());
                out.put("aiStatus", e.getMessage());
            } catch (RuntimeException e) {   // a plugin provider or a hostile reply must not cost the deterministic result
                out.put("ai", "error");
                out.put("aiStatus", "AI stage failed: " + e.getClass().getSimpleName());
            }
            out.put("usage", ai.usage());
        }
        for (int i = 0; i < proposals.size(); i++) {
            Map<String, Object> p = proposals.get(i);
            p.put("rank", i + 1);
            @SuppressWarnings("unchecked")
            List<String> lines = (List<String>) p.get("lines");
            p.put("diff", diff(file, script, lineNo, sl.line, lines));
        }
        if (!proposals.isEmpty()) out.put("status", "proposed");
        return out;
    }

    private static Map<String, Object> reason(Map<String, Object> out, String status, String why) {
        out.put("status", status);
        out.put("reason", why);
        return out;
    }

    private static boolean add(List<Map<String, Object>> list, Set<String> seen, Map<String, Object> p) {
        if (p == null) return false;
        String key = String.join("\n", lineList(p));
        if (!seen.add(key)) return false;
        list.add(p);
        return true;
    }

    @SuppressWarnings("unchecked")
    private static List<String> lineList(Map<String, Object> p) {
        return (List<String>) p.get("lines");
    }

    private static Map<String, Object> proposal(String source, String kind, String locator, List<String> lines, String why, int score) {
        Map<String, Object> p = new LinkedHashMap<String, Object>();
        p.put("source", source);
        p.put("kind", kind);
        if (locator != null) {
            p.put("locator", locator);
            p.put("count", 1);
            p.put("strict", true);
        }
        p.put("lines", lines);
        p.put("why", why);
        p.put("score", score);
        return p;
    }

    /** PickLocator's choice for {@code e}, kept only if it re-reads as seleasy and resolves strictly to {@code e}. */
    String rankFor(SnapshotResolver r, Node e) {
        Map<String, Object> ranked = PickLocator.rank(r.candidates(e), r.fallback(e));
        String loc = (String) ranked.get("locator");
        return loc != null && verify(r, loc, e) ? loc : null;
    }

    /** The gate: parses back as one locator, carries no unsafe value, matches exactly one element (that one, if given). */
    static boolean verify(SnapshotResolver r, String locator, Node expected) {
        if (locator == null) return false;
        StepLocator l = StepLocator.parseLocator(locator);
        if (l == null || LocatorRanking.unsafe(l.classifier)) return false;
        List<Node> els = StepLocator.elements(r, l.by, l.classifier);
        if (els == null || !SemanticLocators.resolvesStrictly(els.size())) return false;
        return expected == null || els.get(0) == expected;
    }

    /** One model candidate → a validated proposal, or null. */
    Map<String, Object> fromAi(Map<String, Object> c, StepLocator sl, SnapshotResolver r, List<Node> outline, String snapUrl) {
        String kind = String.valueOf(c.get("kind"));
        String loc = null;
        Node target = null;
        switch (kind) {
            case "element": {
                int i = (Integer) c.get("i");
                if (i < 0 || i >= outline.size()) return null;
                target = outline.get(i);
                loc = rankFor(r, target);
                break;
            }
            case "testid": case "label": {
                Map<String, Object> m = new LinkedHashMap<String, Object>();
                m.put("s", kind);
                m.put("v", c.get("value"));
                loc = LocatorRanking.render(m);
                break;
            }
            case "role": {
                Map<String, Object> m = new LinkedHashMap<String, Object>();
                m.put("s", "role");
                m.put("role", String.valueOf(c.get("role")).toLowerCase(Locale.ROOT));
                m.put("name", c.get("name"));
                loc = LocatorRanking.render(m);
                break;
            }
            case "id": case "name": case "css": {
                String v = String.valueOf(c.get("value"));
                if (v.length() > HealPrompt.MAX_VALUE || v.indexOf('"') >= 0 || v.indexOf('\'') >= 0 || v.indexOf('\\') >= 0) return null;
                Map<String, Object> m = new LinkedHashMap<String, Object>();
                m.put("s", kind);
                m.put("v", v);
                loc = PickLocator.selector(m);
                break;
            }
            case "goto":
                return gotoProposal(String.valueOf(c.get("url")), sl, snapUrl);
            default:
                return null;
        }
        if (loc == null || !verify(r, loc, target)) return null;
        if (loc.equals(sl.token())) return null;   // the locator that just failed (a wait for it is deterministic)
        Integer w = (Integer) c.get("waitSeconds");
        String fixed = sl.replace(loc);
        if (!StepLocator.sameStepWith(sl, fixed, loc)) return null;   // the runtime must read the line back the same way
        if (w != null) {
            String wl = sl.indent() + "??" + w + " " + loc;
            StepLocator wf = StepLocator.find(wl);
            if (wf == null || !wf.token().equals(loc)) return null;
        }
        List<String> lines = w == null ? List.of(fixed) : List.of(sl.indent() + "??" + w + " " + loc, fixed);
        return proposal("ai", w == null ? "locator" : "wait", loc, lines, "model-suggested", 0);
    }

    /** {@code goto <url>} before the step: PickLocator.gotoUrl-clean, no template wrapping needed, same origin as the snapshot. */
    static Map<String, Object> gotoProposal(String url, StepLocator sl, String snapUrl) {
        String g = PickLocator.gotoUrl(url);
        if (g == null || !g.equals(url) || snapUrl == null || StepLocator.find("goto " + g) != null || g.indexOf('\'') >= 0 || g.indexOf('"') >= 0) return null;
        try {
            URI a = new URI(url), b = new URI(snapUrl);
            if (a.getScheme() == null || !a.getScheme().matches("(?i)https?") || a.getHost() == null || a.getRawUserInfo() != null) return null;
            if (b.getScheme() == null || b.getHost() == null || b.getRawUserInfo() != null) return null;   // e.g. a my_host snapshot: no comparable host
            if (!a.getScheme().equalsIgnoreCase(b.getScheme()) || !a.getHost().equalsIgnoreCase(b.getHost()) || port(a) != port(b)) return null;
        } catch (Exception e) {
            return null;
        }
        return proposal("ai", "goto", null, List.of(sl.indent() + "goto " + g, sl.line), "model-suggested", 0);
    }

    private static int port(URI u) {
        if (u.getPort() >= 0) return u.getPort();
        return "https".equalsIgnoreCase(u.getScheme()) ? 443 : 80;
    }

    // ---------------------------------------------------------------------------------------------- scoring

    private static final Set<String> NOISE = Set.of("div", "span", "nth", "of", "type", "child", "data", "testid", "class", "id",
            "name", "role", "xpath", "contains", "text", "normalize", "space", "and", "or", "not", "the", "css", "label", "title",
            "placeholder", "alt", "html", "body", "first", "last");
    private static final Set<String> TAG_HINTS = Set.of("button", "input", "a", "link", "select", "textarea", "img", "option", "li", "h1", "h2", "h3");

    /** The old locator's words: split on non-alphanumerics and camelCase, lower-cased, noise dropped. */
    static List<String> hints(String classifier) {
        List<String> out = new ArrayList<String>();
        String s = classifier.replaceAll("([a-z])([A-Z])", "$1 $2");
        for (String t : s.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (t.length() < 2 && !t.equals("a") || NOISE.contains(t) || out.contains(t)) continue;
            out.add(t);
        }
        return out;
    }

    static List<String> words(String v) {
        List<String> out = new ArrayList<String>();
        if (v == null) return out;
        for (String t : v.replaceAll("([a-z])([A-Z])", "$1 $2").toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) if (!t.isEmpty()) out.add(t);
        return out;
    }

    static int score(SnapshotResolver r, Node e, String classifier, List<String> hints, String verb) {
        String role = r.role(e);
        String acc = role != null ? r.accName(e, role) : "", label = r.labelText(e);
        String text = role != null && Set.of("button", "link", "tab", "menuitem", "option", "checkbox", "radio", "heading", "cell").contains(role)
                ? SnapshotResolver.norm(r.text(e, false, false)) : "";
        String[][] fields = {
                { e.attr("id"), "4" }, { e.attr(r.testIdAttribute()), "4" }, { e.attr("name"), "4" },
                { acc, "3" }, { label, "3" }, { e.attr("placeholder"), "3" }, { e.attr("title"), "2" }, { e.attr("alt"), "2" },
                { e.attr("class"), "2" }, { text.length() > 200 ? "" : text, "1" }, { e.attr("value"), "1" } };
        int s = 0;
        for (String h : hints) {
            if (TAG_HINTS.contains(h)) continue;
            int best = 0;
            for (String[] f : fields) {
                if (f[0] == null || f[0].isEmpty()) continue;
                int w = Integer.parseInt(f[1]);
                List<String> ws = words(f[0]);
                if (ws.contains(h)) best = Math.max(best, w * 2);
                else if (h.length() >= 3) for (String x : ws) if (x.length() >= 3 && (x.startsWith(h) || h.startsWith(x))) { best = Math.max(best, w); break; }
            }
            s += best;
        }
        String whole = SnapshotResolver.norm(classifier).toLowerCase(Locale.ROOT);
        for (String v : new String[] { acc, label, text, e.attr("id"), e.attr(r.testIdAttribute()), e.attr("name") }) {
            if (v != null && !v.isEmpty() && SnapshotResolver.norm(v).toLowerCase(Locale.ROOT).equals(whole)) { s += 6; break; }
        }
        if (s == 0) return 0;
        for (String h : hints) if (TAG_HINTS.contains(h) && (h.equals(e.tag) || (h.equals("link") && "link".equals(role)) || (h.equals("button") && "button".equals(role)))) s += 1;
        if (fits(verb, role, e)) s += 2;
        return s;
    }

    /** Whether the element can take the step's verb (a bonus, not a filter). */
    static boolean fits(String verb, String role, Node e) {
        if (verb == null || verb.isEmpty()) return false;
        if (verb.equals("type") || verb.equals("clear") || verb.equals("upload")) {
            return role != null && Set.of("textbox", "searchbox", "combobox", "spinbutton").contains(role) || e.tag.equals("input") || e.tag.equals("textarea");
        }
        if (verb.equals("select")) return role != null && Set.of("combobox", "listbox").contains(role);
        if (verb.matches("click|dblclick|doubleclick|rightclick|hover|check|uncheck|submit|tap")) {
            return role != null && Set.of("button", "link", "checkbox", "radio", "menuitem", "tab", "option", "switch", "treeitem").contains(role);
        }
        return false;
    }

    // ---------------------------------------------------------------------------------------------- diff

    /** A unified diff replacing the failing line with {@code lines} (3 lines of context when the script is known). */
    static String diff(String file, List<String> script, int lineNo, String step, List<String> lines) {
        String f = file == null || file.isBlank() ? "script.sel" : file.replaceAll("[\\x00-\\x1f\\x7f]", "_");
        StringBuilder b = new StringBuilder();
        b.append("--- a/").append(f).append('\n').append("+++ b/").append(f).append('\n');
        boolean known = script != null && lineNo >= 1 && lineNo <= script.size() && step.equals(script.get(lineNo - 1));
        if (!known) {
            b.append("@@ -1,1 +1,").append(lines.size()).append(" @@\n").append('-').append(step).append('\n');
            for (String l : lines) b.append('+').append(l).append('\n');
            return b.toString();
        }
        int from = Math.max(1, lineNo - 3), to = Math.min(script.size(), lineNo + 3);
        int oldLen = to - from + 1, newLen = oldLen - 1 + lines.size();
        b.append("@@ -").append(from).append(',').append(oldLen).append(" +").append(from).append(',').append(newLen).append(" @@\n");
        for (int i = from; i <= to; i++) {
            if (i == lineNo) {
                b.append('-').append(step).append('\n');
                for (String l : lines) b.append('+').append(l).append('\n');
            } else {
                b.append(' ').append(script.get(i - 1)).append('\n');
            }
        }
        return b.toString();
    }
}
