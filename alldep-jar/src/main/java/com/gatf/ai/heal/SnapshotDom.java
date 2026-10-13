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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * H1 (Δ149): a small, tolerant HTML tree for a trace snapshot ({@code snapshots/snapshot-*.html}, written by
 * {@code DomSnapshotSerializer} or the lite path). Enough HTML for the snapshot's own output: void and raw-text
 * elements, entities, the common implied end tags, and declarative {@code <template shadowrootmode>} shadow roots
 * (attached to the parent, as a browser does). {@code iframe srcdoc} stays an attribute: the in-page resolver does
 * not pierce frames either. Bounded ({@link #MAX_NODES}); never throws on bad markup. Pure.
 */
public final class SnapshotDom {

    public static final int MAX_NODES = 200_000;
    public static final int MAX_CHARS = 8 * 1024 * 1024;
    /** Chromium's parser depth cap: deeper elements become children of the element at this depth, as in Blink. */
    public static final int MAX_DEPTH = 512;

    /** A node: element ({@code tag != null}), text ({@code text != null}), or a document / shadow-root fragment. */
    public static final class Node {
        public final String tag;                  // lower-case element name; null for text / fragment
        public final String text;                 // decoded text; null for elements / fragments
        public final Map<String, String> attrs;   // lower-case names, document order
        public final List<Node> children = new ArrayList<Node>();
        public Node parent;                       // null for a root
        public Node shadowRoot;                   // open shadow root (a fragment), or null
        public Node host;                         // for a shadow-root fragment: its host element
        int order;                                // document order (pre-order, shadow trees included)
        int nthOfType;                            // 1-based position among same-tag siblings (elements only)

        Node(String tag, String text, Map<String, String> attrs) {
            this.tag = tag;
            this.text = text;
            this.attrs = attrs == null ? Collections.<String, String>emptyMap() : attrs;
        }

        public boolean isElement() { return tag != null; }
        public boolean isText() { return text != null; }
        public String attr(String n) { return attrs.get(n); }
        public boolean has(String n) { return attrs.containsKey(n); }

        /** The fragment (document or shadow root) this node lives in. */
        public Node root() {
            Node n = this;
            while (n.parent != null) n = n.parent;
            return n;
        }

        public Node parentElement() {
            return parent != null && parent.isElement() ? parent : null;
        }

        @Override public String toString() { return tag != null ? "<" + tag + ">" : text != null ? "#text" : "#fragment"; }
    }

    private static final Set<String> VOID = Set.of("area", "base", "br", "col", "embed", "hr", "img", "input", "link",
            "meta", "param", "source", "track", "wbr", "keygen");
    private static final Set<String> RAW = Set.of("script", "style", "textarea", "title", "xmp", "iframe", "noembed", "noframes", "plaintext");
    /** tag → the open tags a new start tag of this kind closes implicitly (HTML's common optional end tags). */
    private static final Map<String, Set<String>> CLOSES = Map.of(
            "li", Set.of("li"),
            "dt", Set.of("dt", "dd"), "dd", Set.of("dt", "dd"),
            "option", Set.of("option"), "optgroup", Set.of("optgroup", "option"),
            "tr", Set.of("tr", "td", "th"), "td", Set.of("td", "th"), "th", Set.of("td", "th"),
            "thead", Set.of("tbody", "tfoot", "tr", "td", "th"), "tbody", Set.of("thead", "tbody", "tfoot", "tr", "td", "th"));
    private static final Set<String> CLOSES_P = Set.of("address", "article", "aside", "blockquote", "details", "div", "dl",
            "fieldset", "figcaption", "figure", "footer", "form", "h1", "h2", "h3", "h4", "h5", "h6", "header", "hr", "main",
            "menu", "nav", "ol", "p", "pre", "section", "table", "ul");

    public final Node document;
    public final boolean truncated;
    private final List<Node> elements = new ArrayList<Node>();

    private SnapshotDom(Node document, boolean truncated) {
        this.document = document;
        this.truncated = truncated;
        number(document, new int[] { 0 });
    }

    /** Every element, document order, shadow trees included (after their host, before its light children). */
    public List<Node> elements() { return elements; }

    private void number(Node n, int[] seq) {
        n.order = seq[0]++;
        if (n.isElement()) elements.add(n);
        if (inert(n)) return;   // a plain <template>'s content is an inert fragment: no selector or resolver sees it
        if (n.shadowRoot != null) number(n.shadowRoot, seq);
        java.util.Map<String, Integer> nth = new java.util.HashMap<String, Integer>();
        for (Node c : n.children) {
            if (c.isElement()) c.nthOfType = nth.merge(c.tag, 1, Integer::sum);
            number(c, seq);
        }
    }

    /** A plain {@code <template>} (its content is inert; declarative shadow templates were already attached). */
    public static boolean inert(Node n) {
        return "template".equals(n.tag);
    }

    public static SnapshotDom parse(String html) {
        return new Parser(html == null ? "" : html).run();
    }

    private static final class Parser {
        final String s;
        final int len;
        int i, nodes;
        boolean truncated;
        final Node doc = new Node(null, null, null);
        final List<Node> stack = new ArrayList<Node>();

        Parser(String html) {
            if (html.length() > MAX_CHARS) { html = html.substring(0, MAX_CHARS); truncated = true; }
            s = html;
            len = html.length();
            stack.add(doc);
        }

        Node cur() { return stack.get(stack.size() - 1); }

        void add(Node n) {
            Node p = cur();
            n.parent = p;
            p.children.add(n);
        }

        SnapshotDom run() {
            StringBuilder text = new StringBuilder();
            while (i < len && !truncated) {
                char c = s.charAt(i);
                if (c == '<' && i + 1 < len) {
                    char d = s.charAt(i + 1);
                    if (d == '!' || d == '?' || d == '/' || Character.isLetter(d)) {
                        flush(text);
                        markup();
                        continue;
                    }
                }
                text.append(c);
                i++;
            }
            flush(text);
            return new SnapshotDom(doc, truncated);
        }

        void flush(StringBuilder t) {
            if (t.length() == 0) return;
            if (++nodes > MAX_NODES) { truncated = true; t.setLength(0); return; }
            add(new Node(null, Entities.decode(t.toString()), null));
            t.setLength(0);
        }

        void markup() {
            if (s.startsWith("<!--", i)) {
                int e = s.indexOf("-->", i + 4);
                i = e < 0 ? len : e + 3;
                return;
            }
            char d = s.charAt(i + 1);
            if (d == '!' || d == '?') {   // doctype, CDATA, processing instruction: skipped
                int e = s.indexOf('>', i);
                i = e < 0 ? len : e + 1;
                return;
            }
            if (d == '/') { endTag(); return; }
            startTag();
        }

        void endTag() {
            int j = i + 2, k = j;
            while (k < len && isNameChar(s.charAt(k))) k++;
            String name = s.substring(j, k).toLowerCase(Locale.ROOT);
            int e = s.indexOf('>', k);
            i = e < 0 ? len : e + 1;
            if (name.isEmpty()) return;
            close(name);
        }

        /** Pops to the innermost open {@code name} in the current fragment; a stray end tag is ignored. */
        void close(String name) {
            for (int x = stack.size() - 1; x > 0; x--) {
                Node n = stack.get(x);
                if (!n.isElement()) return;   // never cross a shadow-root fragment boundary
                if (name.equals(n.tag)) {
                    while (stack.size() > x) {
                        Node top = stack.remove(stack.size() - 1);
                        if ("template".equals(top.tag) && top.attrs.containsKey("shadowrootmode")) attachShadow(top);
                    }
                    return;
                }
            }
        }

        void startTag() {
            int j = i + 1, k = j;
            while (k < len && isNameChar(s.charAt(k))) k++;
            String name = s.substring(j, k).toLowerCase(Locale.ROOT);
            Map<String, String> attrs = new LinkedHashMap<String, String>();
            i = k;
            boolean selfClose = false;
            while (i < len) {
                char c = s.charAt(i);
                if (c == '>') { i++; break; }
                if (c == '/' && i + 1 < len && s.charAt(i + 1) == '>') { selfClose = true; i += 2; break; }
                if (Character.isWhitespace(c) || c == '/') { i++; continue; }
                int a = i;
                while (i < len && !Character.isWhitespace(s.charAt(i)) && s.charAt(i) != '=' && s.charAt(i) != '>'
                        && !(s.charAt(i) == '/' && i + 1 < len && s.charAt(i + 1) == '>')) i++;
                String an = s.substring(a, i).toLowerCase(Locale.ROOT);
                while (i < len && Character.isWhitespace(s.charAt(i))) i++;
                String av = "";
                if (i < len && s.charAt(i) == '=') {
                    i++;
                    while (i < len && Character.isWhitespace(s.charAt(i))) i++;
                    if (i < len && (s.charAt(i) == '"' || s.charAt(i) == '\'')) {
                        char q = s.charAt(i);
                        int e = s.indexOf(q, i + 1);
                        av = s.substring(i + 1, e < 0 ? len : e);
                        i = e < 0 ? len : e + 1;
                    } else {
                        int b = i;
                        while (i < len && !Character.isWhitespace(s.charAt(i)) && s.charAt(i) != '>') i++;
                        av = s.substring(b, i);
                    }
                    av = Entities.decode(av);
                }
                if (!an.isEmpty() && !attrs.containsKey(an)) attrs.put(an, av);   // first wins, as in HTML
            }
            if (++nodes > MAX_NODES) { truncated = true; return; }
            implied(name);
            Node el = new Node(name, null, attrs);
            add(el);
            if (VOID.contains(name) || selfClose && !RAW.contains(name)) return;
            if (RAW.contains(name)) {   // raw text up to the matching end tag
                int e = indexOfCi(s, "</" + name, i);
                String body = s.substring(i, e < 0 ? len : e);
                if (!body.isEmpty()) {
                    Node t = new Node(null, name.equals("textarea") || name.equals("title") ? Entities.decode(body) : body, null);
                    t.parent = el;
                    el.children.add(t);
                }
                if (e < 0) { i = len; return; }
                int g = s.indexOf('>', e);
                i = g < 0 ? len : g + 1;
                return;
            }
            if (stack.size() <= MAX_DEPTH) stack.add(el);
        }

        void implied(String name) {
            if (CLOSES_P.contains(name)) closeIfOpen("p", Set.of("button"));
            Set<String> closes = CLOSES.get(name);
            if (closes == null) return;
            // close the innermost open element of these kinds, unless a list / table / select boundary is crossed first
            for (int x = stack.size() - 1; x > 0; x--) {
                Node n = stack.get(x);
                if (!n.isElement()) return;
                if (closes.contains(n.tag)) { close(n.tag); return; }
                if (Set.of("ul", "ol", "dl", "table", "select", "datalist", "template").contains(n.tag)) return;
            }
        }

        void closeIfOpen(String tag, Set<String> scopeStop) {
            for (int x = stack.size() - 1; x > 0; x--) {
                Node n = stack.get(x);
                if (!n.isElement() || scopeStop.contains(n.tag) || "template".equals(n.tag)) return;
                if (tag.equals(n.tag)) { close(tag); return; }
            }
        }

        /** A closed {@code <template shadowrootmode>} becomes its parent's shadow root (first one wins). */
        void attachShadow(Node tpl) {
            Node host = tpl.parent;
            if (host == null || !host.isElement()) return;
            host.children.remove(tpl);
            if (host.shadowRoot != null) return;
            Node frag = new Node(null, null, null);
            frag.host = host;
            for (Node c : tpl.children) { c.parent = frag; frag.children.add(c); }
            host.shadowRoot = frag;
        }

        static boolean isNameChar(char c) {
            return Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == ':' || c == '.';
        }

        static int indexOfCi(String s, String needle, int from) {
            int n = needle.length();
            for (int x = from; x + n <= s.length(); x++) if (s.regionMatches(true, x, needle, 0, n)) return x;
            return -1;
        }
    }

    /** The named entities a snapshot realistically holds (the serializer writes only &amp; &lt; &gt; &quot; &#…;). */
    static final class Entities {
        private static final Map<String, String> NAMED = Map.ofEntries(
                Map.entry("amp", "&"), Map.entry("lt", "<"), Map.entry("gt", ">"), Map.entry("quot", "\""),
                Map.entry("apos", "'"), Map.entry("nbsp", " "), Map.entry("copy", "©"), Map.entry("reg", "®"),
                Map.entry("hellip", "…"), Map.entry("mdash", "—"), Map.entry("ndash", "–"), Map.entry("times", "×"),
                Map.entry("laquo", "«"), Map.entry("raquo", "»"), Map.entry("lsquo", "‘"), Map.entry("rsquo", "’"),
                Map.entry("ldquo", "“"), Map.entry("rdquo", "”"), Map.entry("bull", "•"), Map.entry("middot", "·"),
                Map.entry("euro", "€"), Map.entry("pound", "£"), Map.entry("rupee", "₹"));

        static String decode(String v) {
            if (v.indexOf('&') < 0) return v;
            StringBuilder b = new StringBuilder(v.length());
            for (int x = 0; x < v.length(); x++) {
                char c = v.charAt(x);
                if (c != '&') { b.append(c); continue; }
                int semi = v.indexOf(';', x);
                if (semi > x + 1 && semi - x <= 12) {
                    String name = v.substring(x + 1, semi);
                    String rep = null;
                    try {
                        if (name.startsWith("#x") || name.startsWith("#X")) rep = cp(Integer.parseInt(name.substring(2), 16));
                        else if (name.startsWith("#")) rep = cp(Integer.parseInt(name.substring(1)));
                        else rep = NAMED.get(name);
                    } catch (NumberFormatException e) { rep = null; }
                    if (rep != null) { b.append(rep); x = semi; continue; }
                }
                b.append(c);
            }
            return b.toString();
        }

        private static String cp(int c) {
            return c <= 0 || c > 0x10FFFF || (c >= 0xD800 && c <= 0xDFFF) ? "�" : new String(Character.toChars(c));
        }
    }
}
