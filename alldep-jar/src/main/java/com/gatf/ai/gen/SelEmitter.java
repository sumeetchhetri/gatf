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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.gatf.ai.plan.CrawlMap;
import com.gatf.ai.plan.TestPlan;
import com.gatf.record.PickLocator;
import com.gatf.selenium.LocatorRanking;

/**
 * H1 (Δ152): one plan journey → one seleasy script, by a fixed mapping of the closed step set. The model never writes
 * seleasy text: every line comes from a template here, and the only page-derived values in it are
 * <ul>
 * <li>URLs, only through {@link PickLocator#gotoUrl}, same origin as the plan and without {@code $};</li>
 * <li>locators, re-checked with {@link LocatorRanking#unsafe} and a strict one-token grammar ({@link #safeLocator});
 * an unsafe primary falls back to the first safe alternative, else the step is skipped;</li>
 * <li>titles and element texts, only when the plan's {@code *Safe} flag is true and {@link TestPlan#safeText} agrees,
 * escaped with {@link PickLocator#stepValue}.</li>
 * </ul>
 * Fill values are GATF's own ({@link #sample}, {@link #invalid}). Side effects are recomputed from the elements, never
 * taken only from the plan's flag. Comments carry only fixed text and sanitised ids. A last check
 * ({@link #allowedLine}) refuses the whole script if any line is not one of the templates. Pure.
 */
public final class SelEmitter {

    /** Repair edits for one journey: the closed set, already parsed. */
    public static final class Edits {
        /** 1-based step numbers of the plan journey to drop. */
        public final Set<Integer> drop = new LinkedHashSet<Integer>();
        /** ref → index into the element's alternatives. */
        public final Map<String, Integer> alternative = new LinkedHashMap<String, Integer>();
        /** ref → data kind (sample | empty | invalid). */
        public final Map<String, String> data = new LinkedHashMap<String, String>();

        public Edits copy() {
            Edits e = new Edits();
            e.drop.addAll(drop);
            e.alternative.putAll(alternative);
            e.data.putAll(data);
            return e;
        }

        public boolean isEmpty() { return drop.isEmpty() && alternative.isEmpty() && data.isEmpty(); }

        /** The edits as their closed-set words, e.g. {@code drop-step 3}. */
        public List<String> words() {
            List<String> w = new ArrayList<String>();
            for (Integer n : drop) w.add("drop-step " + n);
            for (Map.Entry<String, Integer> a : alternative.entrySet()) w.add("use-alternative " + a.getKey() + " " + a.getValue());
            for (Map.Entry<String, String> d : data.entrySet()) w.add("set-data " + d.getKey() + " " + d.getValue());
            return w;
        }
    }

    public static final class Script {
        public String file;
        public String routeId, journeyId, goal;
        public String text;           // null when nothing can be generated (see skipReason)
        public String skipReason;
        public boolean sideEffects;
        public int steps, stepsSkipped;
        public final List<String> notes = new ArrayList<String>();
    }

    private SelEmitter() {}

    static final Pattern BARE = Pattern.compile("[A-Za-z0-9_.:-]+");
    static final String SQ = "'[^']*'";
    static final String DQ = "\"(?:[^\"\\\\]|\\\\.)*\"";
    static final Pattern SEMANTIC = Pattern.compile("(?:testid|label)@(?:[A-Za-z0-9_.:-]+|'[^'~=/][^']*'|\"[^\"~=/][^\"]*\")");
    static final Pattern ROLE = Pattern.compile("role@[a-z][a-z-]*\\[name=(?:[A-Za-z0-9_.:-]+|'[^'\\]~=/][^'\\]]*'|\"[^\"\\]~=/][^\"\\]]*\")\\]");
    static final Pattern LEGACY = Pattern.compile("(?:id|name)@(?:[A-Za-z0-9_\\-:.]+|" + SQ + "|" + DQ + ")|(?:css|xpath)@(?:" + SQ + "|" + DQ + ")");
    static final int MAX_LOCATOR = 1100;

    /**
     * A locator from the plan as one safe seleasy token: no {@code $}, no Velocity directive, no control or
     * line-separator character ({@link LocatorRanking#unsafe}), and exactly one of the forms GATF's own ranking writes
     * ({@code testid@}, {@code label@}, {@code role@r[name=…]}, {@code id@}, {@code name@}, quoted {@code css@} /
     * {@code xpath@}), so nothing after it can read as a second statement.
     */
    public static boolean safeLocator(String loc) {
        if (loc == null || loc.isEmpty() || loc.length() > MAX_LOCATOR || LocatorRanking.unsafe(loc)) return false;
        return SEMANTIC.matcher(loc).matches() || ROLE.matcher(loc).matches() || LEGACY.matcher(loc).matches();
    }

    /** A same-origin URL accepted unchanged by {@link PickLocator#gotoUrl} (so no '$'); null otherwise. */
    public static String safeUrl(String url, String origin) {
        if (url == null || origin == null) return null;
        String g = PickLocator.gotoUrl(url);
        if (g == null || !g.equals(url)) return null;
        String o = CrawlMap.origin(url);
        return o != null && o.equals(origin) ? url : null;
    }

    // ------------------------------------------------------------------ data (GATF's own values)

    public static String sample(GenPlan.El el) {
        if ("textarea".equals(el.tag)) return "sample text";
        switch (lower(el.type)) {
            case "email": return "user@example.com";
            case "password": return "P@ssw0rd-1";
            case "number": case "range": return "1";
            case "tel": return "9999999999";
            case "url": return "https://example.com";
            case "date": return "2020-01-01";
            case "time": return "10:30";
            case "month": return "2020-01";
            case "week": return "2020-W01";
            case "datetime-local": return "2020-01-01T10:30";
            default: return "sample";
        }
    }

    public static String invalid(GenPlan.El el) {
        switch (lower(el.type)) {
            case "email": return "not-an-email";
            case "number": case "tel": return "abc";
            case "url": return "not a url";
            case "date": case "time": case "month": case "week": case "datetime-local": return "not-a-date";
            case "password": return "x";
            default: return "x".repeat(300);   // over the usual length limits
        }
    }

    private static String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    // ------------------------------------------------------------------ the mapping

    public static Script emit(GenPlan plan, String planName, GenPlan.Route route, GenPlan.Journey j, Edits edits) {
        Edits ed = edits == null ? new Edits() : edits;
        Script s = new Script();
        s.routeId = route.id;
        s.journeyId = j.id;
        s.goal = TestPlan.GOALS.contains(j.goal) ? j.goal : "unknown";
        s.sideEffects = j.sideEffects;
        String rid = GenPlan.safeId(route.id), jid = GenPlan.safeId(j.id);
        List<String> body = new ArrayList<String>();
        GenPlan.Route cur = null;
        String home = route.navigable ? safeUrl(route.url, plan.origin) : null;
        if (home == null) {
            s.skipReason = "its route is not navigable or its URL is not a safe same-origin URL";
            return s;
        }
        List<Map<String, Object>> steps = new ArrayList<Map<String, Object>>(j.steps);
        boolean prefixed = false;
        if (steps.isEmpty() || !"navigate".equals(steps.get(0).get("intent")) || !route.id.equals(steps.get(0).get("route"))) {
            Map<String, Object> nav = new LinkedHashMap<String, Object>();
            nav.put("intent", "navigate");
            nav.put("route", route.id);
            steps.add(0, nav);
            prefixed = true;
            s.notes.add("the journey did not start on its own route: a navigate to it was added first");
        }
        if (j.stepsDropped > 0) s.notes.add(j.stepsDropped + " step(s) over the cap of " + TestPlan.MAX_STEPS + " were not read");
        for (int i = 0; i < steps.size(); i++) {
            Map<String, Object> st = steps.get(i);
            int n = prefixed ? i : i + 1;   // the plan's own 1-based step number (0 = the added navigate)
            String label = n == 0 ? "the added navigate" : "step " + n;
            String intent = st.get("intent") instanceof String && TestPlan.INTENTS.contains(st.get("intent")) ? (String) st.get("intent") : null;
            String shown = intent == null ? "unknown" : intent;
            boolean moves = "navigate".equals(intent) || "assert-url".equals(intent);
            if (n > 0 && ed.drop.contains(n)) {
                if (moves) {
                    s.notes.add(label + " (" + shown + "): a drop-step edit was ignored: navigation steps cannot be dropped");
                } else {
                    s.stepsSkipped++;
                    s.notes.add(label + " (" + shown + ") dropped by a repair edit");
                    body.add("// GATF generate: " + label + " (" + shown + ") dropped by a repair edit");
                    continue;
                }
            }
            if (intent == null) {
                skip(s, body, label, shown, "not an intent of the closed set");
                continue;
            }
            if (moves) {
                GenPlan.Route to = plan.byId.get(str(st.get("route")));
                String u = to == null || !to.navigable ? null : safeUrl(to.url, plan.origin);
                if (u == null) {
                    skip(s, body, label, shown, "its route is unknown, not navigable, off-origin or not a safe URL");
                    int rest = steps.size() - i - 1;
                    if (rest > 0) {
                        s.stepsSkipped += rest;
                        s.notes.add("the " + rest + " step(s) after " + label + " were skipped: they would run on the wrong page");
                        body.add("// GATF generate: the " + rest + " step(s) after this were skipped: they would run on the wrong page");
                    }
                    break;
                }
                body.add("navigate".equals(intent) ? "goto " + u : "?? currentUrl =\"" + u + "\"");
                cur = to;
                s.steps++;
                continue;
            }
            if (st.get("on") != null && (cur == null || !cur.id.equals(st.get("on")))) {
                skip(s, body, label, shown, "its 'on' route is not the route of the latest navigate or assert-url");
                continue;
            }
            if ("assert-title".equals(intent)) {
                String v = cur.titleSafe && TestPlan.safeText(cur.title) ? PickLocator.stepValue(cur.title) : null;
                if (v == null) { skip(s, body, label, shown, "the route's title is not safe to write"); continue; }
                body.add("?? title =\"" + v + "\"");
                s.steps++;
                continue;
            }
            String ref = str(st.get("ref"));
            GenPlan.El el = cur.elements.get(ref);
            String sref = GenPlan.safeId(ref);
            if (el == null) { skip(s, body, label, shown, "it names an element the plan does not list on that route"); continue; }
            String why = kindCheck(intent, el, st, ed);
            if (why != null) { skip(s, body, label + " (" + sref + ")", shown, why); continue; }
            String loc = locator(el, ed.alternative.get(ref), sref, s);
            if (loc == null) { skip(s, body, label + " (" + sref + ")", shown, "no safe locator: the element's locator and its alternatives are all unsafe"); continue; }
            switch (intent) {
                case "click":
                    if ("button".equals(el.kind) || ("submit".equals(el.kind) && !"get".equals(el.method))) s.sideEffects = true;
                    body.add("click " + loc);
                    break;
                case "check":
                    body.add("click " + loc);
                    break;
                case "fill": {
                    String d = dataOf(st, ed, ref);
                    if ("empty".equals(d)) body.add("clear " + loc);
                    else body.add("type \"" + ("invalid".equals(d) ? invalid(el) : sample(el)) + "\" " + loc);
                    break;
                }
                case "select":
                    body.add("select index@" + optionOf(st, el) + " " + loc);
                    break;
                case "assert-visible":
                    body.add("?? " + loc + " visible =true");
                    break;
                default: {   // assert-text: kindCheck made sure the text is safe
                    String v = PickLocator.stepValue(el.text);
                    if (v == null) { skip(s, body, label + " (" + sref + ")", shown, "the element's text is not safe to write"); continue; }
                    body.add("?? " + loc + " text =\"" + v + "\"");
                    break;
                }
            }
            s.steps++;
        }
        if (s.steps == 0) {
            s.skipReason = "no step could be generated";
            return s;
        }
        StringBuilder b = new StringBuilder();
        b.append("// Generated by `gatf generate` (H1) from test plan ").append(GenPlan.safeId(planName)).append(": route ").append(rid)
                .append(", journey ").append(jid).append(" (").append(s.goal).append(s.sideEffects ? ", side effects" : "").append(").\n");
        b.append("// Deterministic: the plan's closed-set steps mapped by GATF. Review before relying on it.\n");
        b.append("open chrome\nmaximize\n");
        for (String l : body) b.append(l).append('\n');
        String text = b.toString();
        for (String l : text.split("\n", -1)) {
            if (!l.isEmpty() && !allowedLine(l)) {   // defence in depth: never reached by the templates above
                s.skipReason = "internal check: a generated line was not one of the allowed statements";
                return s;
            }
        }
        s.text = text;
        return s;
    }

    private static void skip(Script s, List<String> body, String label, String intent, String why) {
        s.stepsSkipped++;
        s.notes.add(label + " (" + intent + ") skipped: " + why);
        body.add("// GATF generate: " + label + " (" + intent + ") skipped: " + why);
    }

    /** Null when {@code intent} fits {@code el}, else why not (fixed text). */
    static String kindCheck(String intent, GenPlan.El el, Map<String, Object> st, Edits ed) {
        switch (intent) {
            case "click":
                return "button".equals(el.kind) || "submit".equals(el.kind) || checkable(el) ? null : "the element is not clickable";
            case "check":
                return checkable(el) ? null : "the element is not a checkbox or radio";
            case "fill": {
                if (!fillable(el)) return "the element is not a text field";
                return TestPlan.DATA.contains(dataOf(st, ed, el.ref)) ? null : "unknown data kind";
            }
            case "select":
                if (!"select".equals(el.tag)) return "the element is not a select";
                return optionOf(st, el) < 0 ? "select option out of range" : null;
            case "assert-text":
                return el.textSafe && TestPlan.safeText(el.text) ? null : "the element's text is not safe to write";
            default:
                return null;   // assert-visible
        }
    }

    static String dataOf(Map<String, Object> st, Edits ed, String ref) {
        if (ed.data.containsKey(ref)) return ed.data.get(ref);
        Object d = st.get("data");
        return d == null ? "sample" : String.valueOf(d);
    }

    /** The option index, or -1 when out of range (0 ≤ option < options). */
    static int optionOf(Map<String, Object> st, GenPlan.El el) {
        Object o = st.get("option");
        int max = Math.max(1, Math.min(el.options, 1000));
        int v = o == null ? (el.options > 1 ? 1 : 0) : o instanceof Integer ? (Integer) o : -1;
        return v >= 0 && v < max ? v : -1;
    }

    static boolean checkable(GenPlan.El el) {
        return "field".equals(el.kind) && "input".equals(el.tag) && ("checkbox".equals(el.type) || "radio".equals(el.type));
    }

    static boolean fillable(GenPlan.El el) {
        if (!"field".equals(el.kind)) return false;
        if ("textarea".equals(el.tag)) return true;
        return "input".equals(el.tag) && !checkable(el) && !lower(el.type).matches("range|color|file|submit|button|reset|image|hidden|checkbox|radio");
    }

    /**
     * The locator for {@code el}: a repair's alternative when it is safe, else the plan's locator when safe, else the
     * first safe alternative (each re-checked; the plan's order is not trusted to be safe). Null when none is.
     */
    static String locator(GenPlan.El el, Integer pick, String sref, Script s) {
        if (pick != null) {
            String a = pick >= 0 && pick < el.alternatives.size() ? el.alternatives.get(pick) : null;
            if (safeLocator(a)) return a;
            s.notes.add("element " + sref + ": alternative " + pick + " (repair edit) is missing or unsafe; ignored");
        }
        if (safeLocator(el.locator)) return el.locator;
        for (int k = 0; k < el.alternatives.size(); k++) {
            if (safeLocator(el.alternatives.get(k))) {
                String note = "element " + sref + ": its locator is unsafe; alternative " + k + " used instead";
                if (!s.notes.contains(note)) s.notes.add(note);
                return el.alternatives.get(k);
            }
        }
        return null;
    }

    static final Pattern[] ALLOWED = {
            Pattern.compile("// [^\\u0000-\\u001f\\u007f\\u0085\\u2028\\u2029\\\\]*"),
            Pattern.compile("open chrome"),
            Pattern.compile("maximize"),
            Pattern.compile("goto (?i:https?)://\\S+"),
            Pattern.compile("\\?\\? currentUrl =\"(?i:https?)://[^\"\\s]+\""),
            Pattern.compile("\\?\\? title =\"(?:[^\"\\\\]|\\\\.)*\""),
            Pattern.compile("(?:click|clear) \\S.*"),
            Pattern.compile("type \"[A-Za-z0-9@.:/ _-]*\" \\S.*"),
            Pattern.compile("select index@\\d{1,4} \\S.*"),
            Pattern.compile("\\?\\? \\S+(?: \\S+)* visible =true"),
            Pattern.compile("\\?\\? \\S.* text =\"(?:[^\"\\\\]|\\\\.)*\""),
    };

    /** Whether {@code line} is one of the statements this class writes (and nothing that runs code or reaches a network). */
    public static boolean allowedLine(String line) {
        if (line.indexOf('$') >= 0 && !line.startsWith("//")) return false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c < 0x20 || c == 0x7f || c == 0x85 || c == 0x2028 || c == 0x2029) return false;
        }
        if (line.startsWith("//") && (line.contains("\\u") || line.contains("*/") || line.contains("/*"))) return false;
        for (Pattern p : ALLOWED) if (p.matcher(line).matches()) return true;
        return false;
    }

    private static String str(Object o) {
        return o instanceof String ? (String) o : "";
    }
}
