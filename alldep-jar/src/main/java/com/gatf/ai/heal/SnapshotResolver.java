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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.gatf.ai.heal.SnapshotDom.Node;
import com.gatf.selenium.SemanticLocators;

/**
 * H1 (Δ149): {@link SemanticLocators#RESOLVER_FN}, {@link SemanticLocators#CANDIDATES_FN} and
 * {@link SemanticLocators#FALLBACK_FN} ported to Java over a {@link SnapshotDom}, so the healer can count what a locator
 * matches in the failing step's snapshot without a browser. Same rules, same spec maps ({@link SemanticLocators#parse}),
 * same candidate maps ({@code {s, v | role+name, n}}) that {@link com.gatf.record.PickLocator#rank} ranks.
 * <p>
 * Parity with the page script is pinned by {@code tools/smoke/test-heal-parity.mjs} (the generated resolver on jsdom)
 * and {@code SnapshotResolverParityTest} over the same fixtures. One known, one-sided difference: visibility comes
 * from the {@code hidden} attribute, {@code aria-hidden}, inline {@code style} and the UA's display:none elements, not
 * from stylesheets. So an element a stylesheet hides is counted here and not by the page: counts can only be higher,
 * which turns a strict locator into a non-strict one (rejected), never the reverse. Pure.
 */
public final class SnapshotResolver {

    private final SnapshotDom dom;
    private final String tidAttr;
    private final Map<Node, Boolean> hiddenTreeCache = new IdentityHashMap<Node, Boolean>();
    private final Map<Node, String> textCache = new IdentityHashMap<Node, String>();
    // Δ149 verifier round: per-snapshot indexes and memos, so heal() stays near-linear on big pages
    private final Map<Node, Map<String, Node>> idIndex = new IdentityHashMap<Node, Map<String, Node>>();
    private final Map<Node, Map<Node, List<Node>>> labelIndex = new IdentityHashMap<Node, Map<Node, List<Node>>>();
    private final Map<Node, Map<String, List<Node>>> forIndex = new IdentityHashMap<Node, Map<String, List<Node>>>();
    private final Map<Node, String> roleMemo = new IdentityHashMap<Node, String>();
    private final Map<Node, String> labelTextMemo = new IdentityHashMap<Node, String>();
    private final Map<Node, String> accNameMemo = new IdentityHashMap<Node, String>();
    private final Map<Node, String> visibilityMemo = new IdentityHashMap<Node, String>();
    private final Map<String, Integer> countMemo = new HashMap<String, Integer>();
    private List<Node> light;
    private Map<String, List<Node>> tagIndex;
    private Map<String, Integer> idCounts, nameCounts;

    public SnapshotResolver(SnapshotDom dom, String testIdAttribute) {
        this.dom = dom;
        this.tidAttr = testIdAttribute == null || testIdAttribute.isBlank() ? SemanticLocators.DEFAULT_TEST_ID_ATTRIBUTE : testIdAttribute.trim();
    }

    public SnapshotDom dom() { return dom; }

    public String testIdAttribute() { return tidAttr; }

    // ---------------------------------------------------------------------------------------------- helpers (as JS)

    static boolean jsSpace(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == 0x0b || c == '\f' || c == 0xa0 || c == 0x1680
                || (c >= 0x2000 && c <= 0x200a) || c == 0x2028 || c == 0x2029 || c == 0x202f || c == 0x205f || c == 0x3000 || c == 0xfeff;
    }

    /** {@code norm}: collapse JS whitespace (and nbsp) to one space, trim. */
    public static String norm(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length());
        boolean sp = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (jsSpace(c)) { sp = true; continue; }
            if (sp && b.length() > 0) b.append(' ');
            sp = false;
            b.append(c);
        }
        return b.toString();
    }

    static String lc(String s) { return s == null ? "" : s.toLowerCase(Locale.ROOT); }

    private static String attr(Node e, String n) { return e == null ? null : e.attr(n); }

    private String inputType(Node e) {
        String t = attr(e, "type");
        return lc(t == null ? "text" : t);
    }

    /** Inline {@code style} property (last declaration wins), lower-cased, {@code !important} dropped; null if unset. */
    static String inlineStyle(Node e, String prop) {
        String st = attr(e, "style");
        if (st == null) return null;
        String out = null, important = null;
        for (String decl : st.split(";")) {
            int c = decl.indexOf(':');
            if (c < 0) continue;
            if (!decl.substring(0, c).trim().equalsIgnoreCase(prop)) continue;
            String v = lc(decl.substring(c + 1).trim());
            java.util.regex.Matcher m = IMPORTANT.matcher(v);
            if (m.find()) important = v.substring(0, m.start()).trim();   // a later !important beats any normal one
            else out = v;
        }
        return important != null ? important : out;
    }

    private static final Pattern IMPORTANT = Pattern.compile("!\\s*important\\s*$");

    /** JS {@code String.prototype.trim}: strips every JS whitespace character (nbsp, U+2000…, U+FEFF), not just ASCII. */
    static String jsTrim(String v) {
        int a = 0, b = v.length();
        while (a < b && jsSpace(v.charAt(a))) a++;
        while (b > a && jsSpace(v.charAt(b - 1))) b--;
        return v.substring(a, b);
    }

    /** JS {@code parseInt(v, 10)} (leading JS whitespace, optional sign, leading digits); null for NaN. */
    static Integer jsParseInt(String v) {
        if (v == null) return null;
        String t = jsTrim(v);
        java.util.regex.Matcher m = Pattern.compile("^([+-]?\\d+)").matcher(t);
        if (!m.find()) return null;
        try { return Integer.parseInt(m.group(1).startsWith("+") ? m.group(1).substring(1) : m.group(1)); } catch (NumberFormatException e) { return null; }
    }

    /** HTML "rules for parsing non-negative integers" as the {@code select.size} IDL attribute reflects them (0 if invalid). */
    static int htmlNonNegative(String v) {
        if (v == null) return 0;
        int i = 0;
        while (i < v.length() && (v.charAt(i) == ' ' || v.charAt(i) == '\t' || v.charAt(i) == '\n' || v.charAt(i) == '\f' || v.charAt(i) == '\r')) i++;
        if (i < v.length() && v.charAt(i) == '+') i++;
        int j = i;
        while (j < v.length() && Character.isDigit(v.charAt(j)) && v.charAt(j) < 128) j++;
        if (j == i) return 0;
        try { return Integer.parseInt(v.substring(i, Math.min(j, i + 9))); } catch (NumberFormatException e) { return 0; }
    }

    private static final Set<String> UA_NONE = Set.of("area", "base", "basefont", "datalist", "head", "link", "meta", "noembed",
            "noframes", "param", "rp", "script", "style", "template", "title");

    private boolean displayNone(Node e) {
        String d = inlineStyle(e, "display");
        if (d != null) return d.equals("none");
        if (UA_NONE.contains(e.tag)) return true;
        if (e.tag.equals("input") && inputType(e).equals("hidden")) return true;
        return e.tag.equals("dialog") && !e.has("open");
    }

    /** Computed visibility: inherited, so the nearest inline value on the element or a (flat-tree) ancestor. */
    private String visibility(Node e) {
        if (e == null) return "visible";
        String c = visibilityMemo.get(e);
        if (c != null) return c;
        String v = inlineStyle(e, "visibility");
        String r = v != null && !v.equals("inherit") ? v : visibility(up(e));
        visibilityMemo.put(e, r);
        return r;
    }

    /** {@code n.parentElement || n.parentNode.host}. */
    private static Node up(Node n) {
        Node p = n.parent;
        if (p == null) return null;
        if (p.isElement()) return p;
        return p.host;
    }

    private boolean hiddenTree(Node n) {
        if (n == null || !n.isElement()) return false;
        Boolean c = hiddenTreeCache.get(n);
        if (c != null) return c;
        boolean h = n.has("hidden") || "true".equals(attr(n, "aria-hidden")) || displayNone(n) || hiddenTree(up(n));
        hiddenTreeCache.put(n, h);
        return h;
    }

    public boolean hidden(Node e) {
        if (hiddenTree(e)) return true;
        String v = visibility(e);
        return v.equals("hidden") || v.equals("collapse");
    }

    /** Closest ancestor-or-self in the same tree (no shadow crossing), like {@code Element.closest}. */
    private static Node closest(Node e, Set<String> tags) {
        for (Node n = e; n != null && n.isElement(); n = n.parent) if (tags.contains(n.tag)) return n;
        return null;
    }

    public String role(Node e) {
        if (roleMemo.containsKey(e)) return roleMemo.get(e);
        String r = role0(e);
        roleMemo.put(e, r);
        return r;
    }

    private String role0(Node e) {
        String r = attr(e, "role");
        if (r != null && !r.isEmpty()) {
            String t = jsTrim(r);
            int sp = 0;
            while (sp < t.length() && !jsSpace(t.charAt(sp))) sp++;
            return lc(t.substring(0, sp));
        }
        String t = e.tag;
        switch (t) {
            case "button": return "button";
            case "a": case "area": return e.has("href") ? "link" : null;
            case "h1": case "h2": case "h3": case "h4": case "h5": case "h6": return "heading";
            case "textarea": return "textbox";
            case "select": return e.has("multiple") || htmlNonNegative(attr(e, "size")) > 1 ? "listbox" : "combobox";
            case "option": return "option";
            case "img": return "".equals(attr(e, "alt")) ? "presentation" : "img";
            case "ul": case "ol": case "menu": return "list";
            case "li": return "listitem";
            case "nav": return "navigation";
            case "main": return "main";
            case "aside": return "complementary";
            case "article": return "article";
            case "dialog": return "dialog";
            case "form": return "form";
            case "table": return "table";
            case "tr": return "row";
            case "td": return "cell";
            case "th": return "columnheader";
            case "fieldset": return "group";
            case "figure": return "figure";
            case "hr": return "separator";
            case "p": return "paragraph";
            case "progress": return "progressbar";
            case "section": return attrTruthy(e, "aria-label") || attrTruthy(e, "aria-labelledby") ? "region" : null;
            case "header": return closest(e.parent, SECTIONING) != null ? null : "banner";
            case "footer": return closest(e.parent, SECTIONING) != null ? null : "contentinfo";
            case "input":
                switch (inputType(e)) {
                    case "button": case "submit": case "reset": case "image": return "button";
                    case "checkbox": return "checkbox";
                    case "radio": return "radio";
                    case "range": return "slider";
                    case "number": return "spinbutton";
                    case "search": return attrTruthy(e, "list") ? "combobox" : "searchbox";
                    case "hidden": return null;
                    case "email": case "tel": case "text": case "url": return attrTruthy(e, "list") ? "combobox" : "textbox";
                    default: return "textbox";
                }
            default: return null;
        }
    }

    private static final Set<String> SECTIONING = Set.of("article", "aside", "main", "nav", "section");

    /** JS truthiness of {@code getAttribute(n)}: present and non-empty. */
    private static boolean attrTruthy(Node e, String n) {
        String v = e.attr(n);
        return v != null && !v.isEmpty();
    }

    private static final Set<String> FROM_CONTENT = Set.of("button", "cell", "checkbox", "columnheader", "gridcell", "heading",
            "link", "menuitem", "menuitemcheckbox", "menuitemradio", "option", "radio", "row", "rowheader", "switch", "tab",
            "tooltip", "treeitem");

    /** Longest text kept per node: memory stays depth x cap on deeply nested pages. A cut makes the resolve unknown. */
    static final int MAX_TEXT = 16 * 1024;
    private boolean textCut;
    private final Set<Node> cutDerived = java.util.Collections.newSetFromMap(new IdentityHashMap<Node, Boolean>());
    private final Set<Node> cutAcc = java.util.Collections.newSetFromMap(new IdentityHashMap<Node, Boolean>());
    private final Set<Node> cutNodes = java.util.Collections.newSetFromMap(new IdentityHashMap<Node, Boolean>());

    String text(Node n, boolean skipControls, boolean allHidden) {
        boolean memo = !skipControls && !allHidden;
        if (memo) {
            String c = textCache.get(n);
            if (c != null) {
                if (cutNodes.contains(n)) textCut = true;
                return c;
            }
        }
        StringBuilder s = new StringBuilder();
        if (!SnapshotDom.inert(n)) {
            for (Node c : n.children) {
                if (c.isText()) { s.append(c.text); continue; }
                if (!c.isElement()) continue;
                String t = c.tag;
                if (t.equals("script") || t.equals("style") || t.equals("template")) continue;
                if (skipControls && (t.equals("select") || t.equals("textarea") || t.equals("input"))) continue;
                if (!allHidden && hidden(c)) continue;
                String l = attr(c, "aria-label");
                if (l != null && !l.isEmpty()) { s.append(' ').append(l).append(' '); continue; }
                if (t.equals("img") && attrTruthy(c, "alt")) { s.append(' ').append(attr(c, "alt")).append(' '); continue; }
                if (t.equals("br")) { s.append(' '); continue; }
                s.append(' ').append(text(c, skipControls, allHidden)).append(' ');
                if (s.length() > MAX_TEXT) { s.setLength(MAX_TEXT); textCut = true; break; }
            }
        }
        if (n.shadowRoot != null) s.append(' ').append(text(n.shadowRoot, skipControls, allHidden));
        if (s.length() > MAX_TEXT) { s.setLength(MAX_TEXT); textCut = true; }
        String out = s.toString();
        if (memo) {
            textCache.put(n, out);
            if (out.length() >= MAX_TEXT) cutNodes.add(n);
        }
        return out;
    }

    private static final Set<String> LABELABLE = Set.of("button", "input", "meter", "output", "progress", "select", "textarea");

    private boolean labelable(Node e) {
        return e != null && e.isElement() && LABELABLE.contains(e.tag) && !(e.tag.equals("input") && inputType(e).equals("hidden"));
    }

    /** Light-tree descendants (no shadow piercing, inert template content skipped), document order. */
    static void descendants(Node n, List<Node> out) {
        if (SnapshotDom.inert(n)) return;
        for (Node c : n.children) {
            if (!c.isElement()) continue;
            out.add(c);
            descendants(c, out);
        }
    }

    /** {@code root.getElementById(id)}: the first element in tree order (light tree of that root) with that id. */
    Node byIdInTree(Node root, String id) {
        Map<String, Node> m = idIndex.get(root);
        if (m == null) {
            m = new HashMap<String, Node>();
            List<Node> all = new ArrayList<Node>();
            descendants(root, all);
            for (Node x : all) {
                String v = x.attr("id");
                if (v != null && !v.isEmpty()) m.putIfAbsent(v, x);
            }
            idIndex.put(root, m);
        }
        return m.get(id);
    }

    private Node byIdIn(Node e, String id) {
        Node r = byIdInTree(e.root(), id);
        return r != null ? r : byIdInTree(dom.document, id);
    }

    private Node labelTarget(Node l) {
        String f = attr(l, "for");
        if (f != null && !f.isEmpty()) return byIdIn(l, f);
        List<Node> d = new ArrayList<Node>();
        descendants(l, d);
        for (Node x : d) if (labelable(x)) return x;
        return null;
    }

    /** {@code e.labels} (HTML): the labels in e's tree whose labeled control is e, in tree order. */
    private List<Node> htmlLabels(Node e) {
        Node root = e.root();
        Map<Node, List<Node>> m = labelIndex.get(root);
        if (m == null) {
            m = new IdentityHashMap<Node, List<Node>>();
            Map<String, List<Node>> byFor = new HashMap<String, List<Node>>();
            List<Node> all = new ArrayList<Node>();
            descendants(root, all);
            for (Node l : all) {
                if (!l.tag.equals("label")) continue;
                String f = l.attr("for");
                Node target;
                if (f != null) {
                    byFor.computeIfAbsent(f, k -> new ArrayList<Node>()).add(l);
                    target = f.isEmpty() ? null : byIdInTree(root, f);
                    if (target != null && !labelable(target)) target = null;
                } else {
                    target = null;
                    List<Node> d = new ArrayList<Node>();
                    descendants(l, d);
                    for (Node x : d) if (labelable(x)) { target = x; break; }
                }
                if (target != null) m.computeIfAbsent(target, k -> new ArrayList<Node>()).add(l);
            }
            labelIndex.put(root, m);
            forIndex.put(root, byFor);
        }
        List<Node> l = m.get(e);
        return l == null ? new ArrayList<Node>() : l;
    }

    /** RESOLVER_FN's {@code labelsOf}, step for step: {@code e.labels}, else {@code label[for=e.id]} in e's tree plus the
     *  closest wrapping {@code <label>} whose {@code labelTarget} is e. */
    private List<Node> labelsOf(Node e) {
        List<Node> out = new ArrayList<Node>();
        if (!labelable(e)) return out;
        List<Node> labels = htmlLabels(e);
        if (!labels.isEmpty()) return labels;
        String id = e.attr("id");
        if (id != null && !id.isEmpty()) {
            List<Node> fs = forIndex.get(e.root()).get(id);
            if (fs != null) out.addAll(fs);
        }
        Node p = closest(e, Set.of("label"));
        if (p != null && !out.contains(p) && labelTarget(p) == e) out.add(p);
        return out;
    }

    public String labelText(Node e) {
        String c = labelTextMemo.get(e);
        if (c != null) {
            if (cutDerived.contains(e)) textCut = true;
            return c;
        }
        boolean before = textCut;
        textCut = false;
        String r = labelText0(e);
        if (textCut) cutDerived.add(e);
        textCut |= before;
        labelTextMemo.put(e, r);
        return r;
    }

    private String labelText0(Node e) {
        String lb = attr(e, "aria-labelledby");
        if (lb != null && !lb.isEmpty()) {
            List<String> parts = new ArrayList<String>();
            for (String id : norm(lb).split(" ")) {
                if (id.isEmpty()) continue;
                Node t = byIdIn(e, id);
                if (t != null) parts.add(text(t, false, hidden(t)));
            }
            String joined = norm(String.join(" ", parts));
            if (!joined.isEmpty()) return joined;
        }
        String al = attr(e, "aria-label");
        if (al != null && !norm(al).isEmpty()) return norm(al);
        List<Node> ls = labelsOf(e);
        if (!ls.isEmpty()) {
            List<String> s = new ArrayList<String>();
            for (Node l : ls) s.add(text(l, true, false));
            return norm(String.join(" ", s));
        }
        return "";
    }

    public String accName(Node e, String r) {
        if (r != null && r.equals(role(e))) {   // the resolver always passes role(e): memoise that case
            String c = accNameMemo.get(e);
            if (c != null) {
                if (cutAcc.contains(e)) textCut = true;
                return c;
            }
            boolean before = textCut;
            textCut = false;
            String v = accName0(e, r);
            if (textCut) cutAcc.add(e);
            textCut |= before;
            accNameMemo.put(e, v);
            return v;
        }
        return accName0(e, r);
    }

    private String accName0(Node e, String r) {
        String t = e.tag;
        String n = labelText(e);
        if (!n.isEmpty()) return n;
        String cap = t.equals("fieldset") ? "legend" : t.equals("table") ? "caption" : t.equals("figure") ? "figcaption" : null;
        if (cap != null) {
            for (Node ch : e.children) {
                if (!ch.isElement()) continue;
                if (ch.tag.equals(cap)) {
                    String cn = norm(text(ch, false, false));
                    if (!cn.isEmpty()) return cn;
                    break;
                }
            }
        }
        if ((t.equals("img") || t.equals("area") || (t.equals("input") && inputType(e).equals("image"))) && attrTruthy(e, "alt")) return norm(attr(e, "alt"));
        if (t.equals("input")) {
            String it = inputType(e);
            if (it.equals("button") || it.equals("submit") || it.equals("reset")) {
                String v = attr(e, "value");
                return norm(v != null && !v.isEmpty() ? v : it.equals("submit") ? "Submit" : it.equals("reset") ? "Reset" : "");
            }
        }
        if (r != null && FROM_CONTENT.contains(r)) {
            String c = norm(text(e, false, false));
            if (!c.isEmpty()) return c;
        }
        if (attrTruthy(e, "title")) return norm(attr(e, "title"));
        if (attrTruthy(e, "placeholder")) return norm(attr(e, "placeholder"));
        return "";
    }

    private Boolean state(Node e, String name) {
        String a = attr(e, "aria-" + name);
        if (a != null) return a.equals("true") || a.equals("mixed");
        if (name.equals("expanded") || name.equals("pressed")) return null;
        if (name.equals("checked")) return e.has("checked") && (e.tag.equals("input"));
        if (name.equals("selected")) return e.has("selected") && e.tag.equals("option");
        if (name.equals("disabled")) return e.has("disabled") && Set.of("button", "input", "select", "textarea", "optgroup", "option", "fieldset").contains(e.tag);
        return false;
    }

    private Integer level(Node e) {
        String a = attr(e, "aria-level");
        if (a != null && !a.isEmpty()) return jsParseInt(a);   // parseInt(a, 10); NaN never equals a level
        if (e.tag.matches("h[1-6]")) return e.tag.charAt(1) - '0';
        return null;
    }

    private static boolean matches(String v, Map<String, Object> sp) {
        if (v == null) return false;
        String s = norm(v), want = sp.get("value") == null ? "" : String.valueOf(sp.get("value"));
        String mode = String.valueOf(sp.get("mode"));
        switch (mode) {
            case "regex": return jsRegex(want, sp.get("flags") == null ? "" : String.valueOf(sp.get("flags"))).matcher(s).find();
            case "exact": return s.equals(norm(want));
            case "contains": return lc(s).contains(lc(norm(want)));
            default: return lc(s).equals(lc(norm(want)));
        }
    }

    /** The same JS → Java regex mapping {@code SemanticLocators.checkRegex} uses (approximate; a bad one matches nothing). */
    static Pattern jsRegex(String pattern, String flags) {
        int f = 0;
        if (flags.indexOf('i') >= 0) f |= Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        if (flags.indexOf('m') >= 0) f |= Pattern.MULTILINE;
        if (flags.indexOf('s') >= 0) f |= Pattern.DOTALL;
        String j = pattern.replace("[^]", "[\\s\\S]").replace("\\u{", "\\x{")
                .replaceAll("\\(\\?<[A-Za-z_$][\\w$]*>", "(").replaceAll("\\\\k<[A-Za-z_$][\\w$]*>", "\\\\1");
        try {
            return Pattern.compile(j, f);
        } catch (RuntimeException e) {
            return Pattern.compile("(?!)");
        }
    }

    // ---------------------------------------------------------------------------------------------- RESOLVER_FN

    /** {@code __gatfSemantic(null, spec, tidAttr).count}; -1 for a spec it cannot evaluate. */
    public int count(Map<String, Object> spec) {
        String key = new java.util.TreeMap<String, Object>(spec).toString();
        Integer c = countMemo.get(key);
        if (c != null) return c;
        List<Node> els = resolve(spec);
        int n = els == null ? -1 : els.size();
        countMemo.put(key, n);
        return n;
    }

    /** The elements a semantic spec matches (document order), or null for an unknown kind. */
    public List<Node> resolve(Map<String, Object> spec) {
        String k = String.valueOf(spec.get("kind"));
        if (!Set.of("role", "label", "placeholder", "testid", "alt", "title", "text").contains(k)) return null;
        boolean includeHidden = Boolean.TRUE.equals(spec.get("includeHidden"));
        boolean cutBefore = textCut;
        textCut = false;
        List<Node> els = resolve0(k, spec, includeHidden);
        boolean cut = textCut;
        textCut = cutBefore || cut;
        return cut ? null : els;   // a cut text could hide a match: the count is unknown, never strict
    }

    /** True once any text in this snapshot was cut at {@link #MAX_TEXT}. */
    public boolean textCut() { return textCut; }

    private List<Node> resolve0(String k, Map<String, Object> spec, boolean includeHidden) {
        List<Node> els = new ArrayList<Node>();
        for (Node e : dom.elements()) {
            boolean ok = false;
            if (k.equals("role")) {
                String r = role(e);
                if (r == null || !r.equals(spec.get("role"))) continue;
                if (!includeHidden && hidden(e)) continue;
                if (spec.get("value") != null && !matches(accName(e, r), spec)) continue;
                if (spec.get("level") != null && !spec.get("level").equals(level(e))) continue;
                boolean bad = false;
                for (String st : new String[] { "checked", "disabled", "expanded", "pressed", "selected" }) {
                    if (spec.get(st) != null && !spec.get(st).equals(state(e, st))) bad = true;
                }
                ok = !bad;
            } else if (k.equals("label")) {
                if (e.tag.equals("label")) continue;
                ok = (!labelsOf(e).isEmpty() || attrTruthy(e, "aria-labelledby") || attrTruthy(e, "aria-label")) && matches(labelText(e), spec);
            } else if (k.equals("placeholder")) {
                ok = matches(attr(e, "placeholder"), spec);
            } else if (k.equals("testid")) {
                ok = matches(attr(e, tidAttr), spec);
            } else if (k.equals("alt")) {
                ok = matches(attr(e, "alt"), spec);
            } else if (k.equals("title")) {
                ok = matches(attr(e, "title"), spec);
            } else {
                String tg = e.tag;
                if (Set.of("script", "style", "head", "title", "html", "body").contains(tg)) continue;
                String v = tg.equals("input") && inputType(e).matches("button|submit|reset") ? attr(e, "value") : text(e, false, false);
                ok = matches(v, spec);
            }
            if (ok && !k.equals("role") && !includeHidden && hidden(e)) ok = false;
            if (ok) els.add(e);
        }
        if (k.equals("text")) {   // innermost matches only: drop an element with a matched descendant (shadow included)
            Set<Node> hasMatchedDescendant = java.util.Collections.newSetFromMap(new IdentityHashMap<Node, Boolean>());
            for (Node e : els) {
                for (Node p = e.parent != null ? e.parent : e.host; p != null; p = p.parent != null ? p.parent : p.host) {
                    if (!hasMatchedDescendant.add(p)) break;   // the rest of this chain is already marked
                }
            }
            List<Node> inner = new ArrayList<Node>();
            for (Node e : els) if (!hasMatchedDescendant.contains(e)) inner.add(e);
            els = inner;
        }
        return els;
    }

    /** {@code kind: 'info'} for {@code target}. */
    public Map<String, Object> info(Node target) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        String ir = role(target);
        m.put("role", ir);
        m.put("name", ir != null ? accName(target, ir) : "");
        m.put("label", labelText(target));
        m.put("placeholder", norm(attr(target, "placeholder")));
        m.put("testid", attr(target, tidAttr));
        m.put("alt", norm(attr(target, "alt")));
        m.put("title", norm(attr(target, "title")));
        return m;
    }

    // ---------------------------------------------------------------------------------------------- CANDIDATES_FN

    /** {@code __gatfCandidates(el, tidAttr)}: [{s:'testid'|'role'|'label', v, role, name, n}]. */
    public List<Map<String, Object>> candidates(Node el) {
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        Map<String, Object> info = info(el);
        String tid = (String) info.get("testid"), role = (String) info.get("role"), name = (String) info.get("name"), label = (String) info.get("label");
        if (tid != null && !tid.isEmpty()) {
            out.add(cand("testid", tid, null, null, count(spec("testid", null, "exact", tid))));
        }
        if (role != null && !role.isEmpty() && name != null && !name.isEmpty() && !role.equals("presentation") && !role.equals("generic")) {
            out.add(cand("role", null, role, name, count(spec("role", role, "full", name))));
        }
        if (label != null && !label.isEmpty()) out.add(cand("label", label, null, null, count(spec("label", null, "full", label))));
        return out;
    }

    private static Map<String, Object> cand(String s, String v, String role, String name, int n) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("s", s);
        if (v != null) m.put("v", v);
        if (role != null) { m.put("role", role); m.put("name", name); }
        m.put("n", n);
        return m;
    }

    static Map<String, Object> spec(String kind, String role, String mode, String value) {
        Map<String, Object> m = new HashMap<String, Object>();
        m.put("kind", kind);
        if (role != null) m.put("role", role);
        m.put("mode", mode);
        m.put("value", value);
        return m;
    }

    // ---------------------------------------------------------------------------------------------- FALLBACK_FN

    /** {@code __gatfFallback(el)}: {s:'id'|'name'|'css', v, n:1} or null. */
    public Map<String, Object> fallback(Node el) {
        if (el == null || !el.isElement() || el.root() != dom.document) return null;
        String id = el.attr("id");
        if (id != null && !id.isEmpty() && countId(id) == 1) return fb("id", id);
        String nm = el.attr("name");
        if (nm != null && !nm.isEmpty() && countName(nm) == 1) return fb("name", nm);
        String path = "";
        Node e = el;
        for (int depth = 0; e != null && e.isElement() && depth < 64; depth++) {
            String t = e.tag;
            if (!t.matches("[a-z][a-z0-9-]*")) return null;
            String seg = t;
            String cl = e.attr("class") == null ? "" : e.attr("class").replaceAll("^\\s+|\\s+$", "");
            if (!cl.isEmpty()) seg += "." + CssLite.escape(cl.split("\\s+")[0]);
            Node p = e.parentElement();
            if (p != null) {
                int k = 0, same = 0;
                for (Node s : p.children) {
                    if (s.isElement() && s.tag.equals(t)) { same++; if (s == e) k = same; }
                }
                if (same > 1) seg += ":nth-of-type(" + k + ")";
            }
            path = path.isEmpty() ? seg : seg + " > " + path;
            if (countCss(path) == 1) return fb("css", path);
            e = p;
        }
        return null;
    }

    private static Map<String, Object> fb(String s, String v) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("s", s);
        m.put("v", v);
        m.put("n", 1);
        return m;
    }

    List<Node> light() {
        if (light == null) {
            List<Node> all = new ArrayList<Node>();
            descendants(dom.document, all);
            light = all;
            idCounts = new HashMap<String, Integer>();
            nameCounts = new HashMap<String, Integer>();
            for (Node x : all) {
                if (x.attr("id") != null) idCounts.merge(x.attr("id"), 1, Integer::sum);
                if (x.attr("name") != null) nameCounts.merge(x.attr("name"), 1, Integer::sum);
            }
        }
        return light;
    }

    /** {@code By.id} / {@code querySelectorAll('#id')} on the document (light tree). */
    public int countId(String id) {
        light();
        return idCounts.getOrDefault(id, 0);
    }

    /** {@code By.name} / {@code getElementsByName}. */
    public int countName(String nm) {
        light();
        return nameCounts.getOrDefault(nm, 0);
    }

    /** {@code querySelectorAll(css).length} on the document for the {@link CssLite} subset; -1 outside it. */
    public int countCss(String css) {
        CssLite sel = CssLite.parse(css);
        if (sel == null) return -1;
        int n = 0;
        String tag = sel.keyTag();
        for (Node x : tag == null ? light() : byTag(tag)) if (sel.matches(x)) n++;
        return n;
    }

    /** Light-tree elements with this (lower-case) tag, document order. */
    List<Node> byTag(String tag) {
        if (tagIndex == null) {
            tagIndex = new HashMap<String, List<Node>>();
            for (Node x : light()) tagIndex.computeIfAbsent(x.tag, k -> new ArrayList<Node>()).add(x);
        }
        List<Node> l = tagIndex.get(tag);
        return l == null ? List.of() : l;
    }

    /** {@code By.className} (one class token). */
    public int countClass(String cls) {
        if (cls.isEmpty() || cls.matches(".*\\s.*")) return -1;
        int n = 0;
        for (Node x : light()) {
            String c = x.attr("class");
            if (c != null && List.of(c.trim().split("\\s+")).contains(cls)) n++;
        }
        return n;
    }

    /** {@code By.tagName}. */
    public int countTag(String tag) {
        return byTag(tag.toLowerCase(Locale.ROOT)).size();
    }
}
