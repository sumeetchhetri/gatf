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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * H2 (Δ146): turns the raw result of CDP {@code DOMSnapshot.captureSnapshot} (as {@code HasCdp.executeCdpCommand}
 * returns it: nested Maps, Lists and Numbers) into one self-contained HTML document. Pure: no Selenium types.
 *
 * <ul>
 *  <li>Form state is materialised: {@code inputValue} → {@code value}, {@code inputChecked} → {@code checked},
 *      {@code optionSelected} → {@code selected}, a textarea's {@code textValue} → its text; the authored defaults
 *      are dropped so the attribute shows what the user saw.</li>
 *  <li>{@code currentSourceURL} (the resolved srcset candidate) becomes {@code src}; {@code srcset}/{@code sizes} go.</li>
 *  <li>Open and closed shadow roots become declarative {@code <template shadowrootmode="open">}; user-agent roots and
 *      pseudo-element nodes are skipped (CSS regenerates them).</li>
 *  <li>An iframe's content document is inlined as {@code srcdoc} (depth-capped); its {@code src} is dropped.</li>
 *  <li>Removed: {@code script}, {@code noscript} (it would render in a script-less viewer), {@code base},
 *      refresh/CSP {@code meta}, every {@code on*} attribute and {@code javascript:} URLs.</li>
 *  <li>Action targets, given as element-child index paths from {@code documentElement} (the shape
 *      {@link DomCapture#META_JS} returns), get {@code data-gatf-target="<mark>"}. Paths are counted on the live
 *      DOM (scripts included) before anything is removed, so they stay valid.</li>
 * </ul>
 * Iterative (no recursion on DOM depth) and capped by node count and output size; a capped document is closed
 * cleanly and flagged {@code truncated}.
 */
public final class DomSnapshotSerializer {

    public static final int DEFAULT_MAX_NODES = 200_000;
    public static final int DEFAULT_MAX_CHARS = 8 * 1024 * 1024;
    static final int MAX_FRAME_DEPTH = 3;
    private static final int OPEN_TEMPLATE = Integer.MIN_VALUE + 1;

    private static final Set<String> VOID = new HashSet<>(Arrays.asList(
            "area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr"));
    private static final Set<String> DROP = new HashSet<>(Arrays.asList("script", "noscript", "base"));
    private static final Set<String> URL_ATTRS = new HashSet<>(Arrays.asList(
            "href", "src", "action", "formaction", "xlink:href", "data", "poster"));

    /** A target mark: element-child index path from documentElement, and the attribute value to put on it. */
    public static final class Mark {
        final int[] path;
        final String value;
        public Mark(int[] path, String value) { this.path = path; this.value = value; }
    }

    public static final class Result {
        public String html;
        public String url;
        public String title;
        public String baseUrl;
        public long scrollX, scrollY;
        public boolean truncated;
        /** Marks whose path did not resolve to an element (the DOM changed between the two calls). */
        public int unresolvedMarks;
    }

    private DomSnapshotSerializer() {}

    public static Result serialize(Map<String, Object> snapshot, List<Mark> marks) {
        return serialize(snapshot, marks, DEFAULT_MAX_NODES, DEFAULT_MAX_CHARS);
    }

    @SuppressWarnings("unchecked")
    public static Result serialize(Map<String, Object> snapshot, List<Mark> marks, int maxNodes, int maxChars) {
        Result r = new Result();
        if (snapshot == null) return null;
        List<Object> docs = (List<Object>) snapshot.get("documents");
        List<Object> strings = (List<Object>) snapshot.get("strings");
        if (docs == null || docs.isEmpty() || strings == null) return null;
        Ctx c = new Ctx(docs, strings, maxNodes, maxChars);
        Doc top = c.doc(0);
        if (top == null) return null;
        r.url = c.str(top.raw.get("documentURL"));
        r.title = c.str(top.raw.get("title"));
        r.baseUrl = c.str(top.raw.get("baseURL"));
        r.scrollX = num(top.raw.get("scrollOffsetX"));
        r.scrollY = num(top.raw.get("scrollOffsetY"));

        Map<Integer, String> marked = new HashMap<>();
        if (marks != null) {
            for (Mark m : marks) {
                if (m == null || m.path == null || m.value == null) continue;
                int node = top.resolvePath(m.path);
                if (node < 0) { r.unresolvedMarks++; continue; }
                String prev = marked.get(node);
                if (prev == null) marked.put(node, m.value);
                else if (!Arrays.asList(prev.split(" ")).contains(m.value)) marked.put(node, prev + " " + m.value);
            }
        }
        StringBuilder out = new StringBuilder(64 * 1024);
        c.write(top, out, marked, 0);
        r.html = out.toString();
        r.truncated = c.truncated;
        return r;
    }

    static long num(Object o) { return o instanceof Number ? Math.round(((Number) o).doubleValue()) : 0L; }

    /** Shared state across one document and its inlined frames. */
    private static final class Ctx {
        final List<Object> docs;
        final List<Object> strings;
        final int maxNodes, maxChars;
        int nodes;
        boolean truncated;
        final Map<Integer, Doc> cache = new HashMap<>();

        Ctx(List<Object> docs, List<Object> strings, int maxNodes, int maxChars) {
            this.docs = docs; this.strings = strings; this.maxNodes = maxNodes; this.maxChars = maxChars;
        }

        String str(Object idx) {
            if (!(idx instanceof Number)) return null;
            int i = ((Number) idx).intValue();
            if (i < 0 || i >= strings.size()) return null;
            Object s = strings.get(i);
            return s == null ? null : String.valueOf(s);
        }

        @SuppressWarnings("unchecked")
        Doc doc(int i) {
            if (i < 0 || i >= docs.size()) return null;
            Doc d = cache.get(i);
            if (d == null) {
                Object raw = docs.get(i);
                if (!(raw instanceof Map)) return null;
                d = new Doc(this, (Map<String, Object>) raw);
                cache.put(i, d);
            }
            return d;
        }

        boolean over(StringBuilder out) {
            if (nodes > maxNodes || out.length() > maxChars) { truncated = true; return true; }
            return false;
        }

        /** Serialize document {@code d} into {@code out}. */
        void write(Doc d, StringBuilder out, Map<Integer, String> marked, int depth) {
            if (d.n == 0) return;
            // explicit stack: node index, or ~node for the closing tag
            ArrayDeque<Integer> stack = new ArrayDeque<>();
            List<Integer> roots = d.children.get(0);
            if (roots == null) roots = Collections.emptyList();
            for (int i = roots.size() - 1; i >= 0; i--) stack.push(roots.get(i));
            while (!stack.isEmpty()) {
                int x = stack.pop();
                if (x == Integer.MIN_VALUE) { out.append("</template>"); continue; }
                if (x == OPEN_TEMPLATE) { nodes++; out.append("<template shadowrootmode=\"open\">"); continue; }
                if (x < 0) { out.append("</").append(d.tag(~x)).append('>'); continue; }
                if (d.pseudo.containsKey(x)) continue;
                int type = d.type(x);
                if (type == 3) {          // text
                    nodes++;
                    if (over(out)) continue;
                    String v = d.value(x);
                    if (v == null) continue;
                    int p = d.parent(x);
                    String pt = p >= 0 && d.type(p) == 1 ? d.tag(p).toLowerCase(Locale.ROOT) : "";
                    boolean htmlCtx = p >= 0 && !d.foreign(p);
                    // raw text only where the re-parse treats it as raw: an HTML-context <style> (not SVG/MathML)
                    if ("style".equals(pt) && htmlCtx) {
                        out.append(v.replace("</", "<\\/"));
                    } else if ("textarea".equals(pt) && htmlCtx) {
                        if (!d.textValue.containsKey(p)) out.append(escText(v));
                    } else out.append(escText(v));
                } else if (type == 8) {   // Δ146a: comments are dropped ("--->" / a leading ">" re-parse into markup)
                    continue;
                } else if (type == 10) {  // doctype
                    String nm = d.name(x);
                    out.append("<!DOCTYPE ").append(nm == null || nm.isEmpty() ? "html" : nm.toLowerCase(Locale.ROOT)).append('>');
                } else if (type == 11) {  // shadow root (reached only through an element's open/closed root)
                    nodes++;
                    if (over(out)) continue;
                    out.append("<template shadowrootmode=\"open\">");
                    stack.push(Integer.MIN_VALUE);   // sentinel: </template>
                    pushChildren(d, x, stack);
                } else if (type == 1) {
                    nodes++;
                    if (over(out)) continue;
                    String tag = d.tag(x);
                    String lt = tag.toLowerCase(Locale.ROOT);   // Δ146a: checks are case-insensitive ("Script", "SCRIPT")
                    boolean htmlCtx = !d.foreign(x);
                    if (DROP.contains(lt) || dropMeta(d, x, lt)) continue;
                    out.append('<').append(tag);
                    writeAttrs(d, x, htmlCtx ? lt : "", out, marked);
                    if (htmlCtx && (lt.equals("iframe") || lt.equals("frame"))) {
                        Integer cdi = d.contentDoc.get(x);
                        if (cdi != null && depth < MAX_FRAME_DEPTH) {
                            Doc child = doc(cdi);
                            if (child != null) {
                                StringBuilder inner = new StringBuilder();
                                write(child, inner, Collections.emptyMap(), depth + 1);
                                out.append(" srcdoc=\"").append(escAttr(inner.toString())).append('"');
                            }
                        }
                    }
                    out.append('>');
                    if (htmlCtx && VOID.contains(lt)) continue;
                    stack.push(~x);
                    if (htmlCtx && lt.equals("textarea") && d.textValue.containsKey(x)) {
                        out.append(escText(str(d.textValue.get(x))));
                    }
                    pushChildren(d, x, stack);
                }
            }
        }

        private void pushChildren(Doc d, int x, ArrayDeque<Integer> stack) {
            List<Integer> kids = d.children.get(x);
            if (kids == null) return;
            // Chromium reports a shadow tree as the host's children, each node carrying shadowRootType (no #document-
            // fragment node); a declarative <template shadowrootmode> must come first, the light children after it.
            Integer shadowNode = null;
            List<Integer> shadowKids = new ArrayList<>(), light = new ArrayList<>();
            for (int k : kids) {
                String km = d.shadowMode(k);
                if ("user-agent".equals(km)) continue;
                if (d.type(k) == 11) {
                    if ("open".equals(km) || "closed".equals(km)) shadowNode = k;
                } else if (d.isShadowChild(x, k)) shadowKids.add(k);
                else light.add(k);
            }
            for (int i = light.size() - 1; i >= 0; i--) stack.push(light.get(i));
            if (!shadowKids.isEmpty()) {
                stack.push(Integer.MIN_VALUE);
                for (int i = shadowKids.size() - 1; i >= 0; i--) stack.push(shadowKids.get(i));
                stack.push(OPEN_TEMPLATE);
            } else if (shadowNode != null) stack.push(shadowNode);
        }

        private boolean dropMeta(Doc d, int x, String tag) {
            if (!"meta".equals(tag)) return false;
            List<String[]> attrs = d.attrs(x);
            for (String[] a : attrs) {
                if ("http-equiv".equalsIgnoreCase(a[0]) && a[1] != null) {
                    String v = a[1].trim().toLowerCase(Locale.ROOT);
                    if (v.equals("refresh") || v.startsWith("content-security-policy")) return true;
                }
            }
            return false;
        }

        private void writeAttrs(Doc d, int x, String tag, StringBuilder out, Map<Integer, String> marked) {
            boolean isInput = tag.equals("input"), isOption = tag.equals("option");
            boolean hasCurrentSrc = d.currentSrc.containsKey(x) && (tag.equals("img") || tag.equals("video") || tag.equals("source"));
            boolean isFrame = tag.equals("iframe") || tag.equals("frame");
            for (String[] a : d.attrs(x)) {
                String n = a[0];
                if (n == null || n.isEmpty()) continue;
                String ln = n.toLowerCase(Locale.ROOT);
                if (ln.startsWith("on")) continue;
                if (ln.equals("data-gatf-target")) continue;
                if (isInput && (ln.equals("value") || ln.equals("checked"))) continue;
                if (isOption && ln.equals("selected")) continue;
                if (hasCurrentSrc && (ln.equals("src") || ln.equals("srcset") || ln.equals("sizes"))) continue;
                if (isFrame && (ln.equals("src") || ln.equals("srcdoc"))) continue;
                String v = a[1] == null ? "" : a[1];
                if (URL_ATTRS.contains(ln) && isScriptUrl(v)) continue;
                out.append(' ').append(n).append("=\"").append(escAttr(v)).append('"');
            }
            if (isInput) {
                Integer iv = d.inputValue.get(x);
                String type = null;
                for (String[] a : d.attrs(x)) if ("type".equalsIgnoreCase(a[0])) type = a[1];
                String val = iv == null ? null : str(iv);
                if (val != null && type != null && type.trim().equalsIgnoreCase("password")) val = mask(val);
                if (val != null && !(type != null && type.trim().equalsIgnoreCase("file"))) out.append(" value=\"").append(escAttr(val)).append('"');
                if (d.inputChecked.contains(x)) out.append(" checked");
            }
            if (isOption && d.optionSelected.contains(x)) out.append(" selected");
            if (hasCurrentSrc) out.append(" src=\"").append(escAttr(str(d.currentSrc.get(x)))).append('"');
            String mk = marked.get(x);
            if (mk != null) out.append(" data-gatf-target=\"").append(escAttr(mk)).append('"');
        }
    }

    /** One {@code documents[i]} with its node arrays indexed. */
    private static final class Doc {
        final Ctx c;
        final Map<String, Object> raw;
        final List<Object> parentIndex, nodeType, nodeName, nodeValue, attributes;
        final int n;
        final Map<Integer, List<Integer>> children = new HashMap<>();
        final Map<Integer, Integer> inputValue, textValue, currentSrc, shadowType, contentDoc, pseudo;
        final Set<Integer> inputChecked, optionSelected;

        @SuppressWarnings("unchecked")
        Doc(Ctx c, Map<String, Object> raw) {
            this.c = c;
            this.raw = raw;
            Map<String, Object> nodes = raw.get("nodes") instanceof Map ? (Map<String, Object>) raw.get("nodes") : Collections.emptyMap();
            parentIndex = list(nodes.get("parentIndex"));
            nodeType = list(nodes.get("nodeType"));
            nodeName = list(nodes.get("nodeName"));
            nodeValue = list(nodes.get("nodeValue"));
            attributes = list(nodes.get("attributes"));
            n = Math.min(parentIndex.size(), nodeType.size());
            for (int i = 0; i < n; i++) {
                int p = parent(i);
                if (p >= 0 && p != i) children.computeIfAbsent(p, k -> new ArrayList<>()).add(i);
            }
            inputValue = rareStr(nodes.get("inputValue"));
            textValue = rareStr(nodes.get("textValue"));
            currentSrc = rareStr(nodes.get("currentSourceURL"));
            shadowType = rareStr(nodes.get("shadowRootType"));
            pseudo = rareStr(nodes.get("pseudoType"));
            contentDoc = rareStr(nodes.get("contentDocumentIndex"));   // same {index,value} shape
            inputChecked = rareBool(nodes.get("inputChecked"));
            optionSelected = rareBool(nodes.get("optionSelected"));
        }

        int parent(int i) { return i < parentIndex.size() && parentIndex.get(i) instanceof Number ? ((Number) parentIndex.get(i)).intValue() : -1; }
        int type(int i) { return i >= 0 && i < nodeType.size() && nodeType.get(i) instanceof Number ? ((Number) nodeType.get(i)).intValue() : 0; }
        String name(int i) { return i < nodeName.size() ? c.str(nodeName.get(i)) : null; }
        String value(int i) { return i < nodeValue.size() ? c.str(nodeValue.get(i)) : null; }

        String tag(int i) {
            String nm = name(i);
            if (nm == null) return "span";
            // HTML element names come upper-cased; SVG/MathML keep their case (linearGradient, foreignObject)
            boolean lower = false;
            for (int k = 0; k < nm.length(); k++) if (Character.isLowerCase(nm.charAt(k))) { lower = true; break; }
            String t = lower ? nm : nm.toLowerCase(Locale.ROOT);
            // a tag name is emitted raw: never let a hostile name break out of the tag
            StringBuilder b = new StringBuilder(t.length());
            for (int k = 0; k < t.length(); k++) {
                char ch = t.charAt(k);
                if (Character.isLetterOrDigit(ch) || ch == '-' || ch == '_' || ch == ':' || ch == '.') b.append(ch);
            }
            return b.length() == 0 ? "span" : b.toString();
        }

        List<String[]> attrs(int i) {
            List<String[]> out = new ArrayList<>();
            if (i >= attributes.size()) return out;
            List<Object> a = list(attributes.get(i));
            for (int k = 0; k + 1 < a.size(); k += 2) {
                String nm = c.str(a.get(k));
                if (nm == null) continue;
                StringBuilder clean = new StringBuilder();
                for (int q = 0; q < nm.length(); q++) {
                    char ch = nm.charAt(q);
                    if (ch > ' ' && ch != '"' && ch != '\'' && ch != '>' && ch != '/' && ch != '=' && ch != '<') clean.append(ch);
                }
                if (clean.length() == 0) continue;
                out.add(new String[] { clean.toString(), c.str(a.get(k + 1)) });
            }
            return out;
        }

        /** Whether element {@code x} sits in SVG/MathML content (below an svg/math element, not under foreignObject),
         *  i.e. where an HTML re-parse treats {@code <style>} as markup rather than raw text. */
        boolean foreign(int x) {
            int guard = 0;
            for (int p = parent(x); p >= 0 && guard++ < 10_000; p = parent(p)) {
                if (type(p) != 1) continue;
                String n = name(p);
                if (n == null) continue;
                n = n.toLowerCase(Locale.ROOT);
                if (n.equals("foreignobject")) return false;
                if (n.equals("svg") || n.equals("math")) return true;
            }
            return false;
        }

        String shadowMode(int k) { return shadowType.containsKey(k) ? c.str(shadowType.get(k)) : null; }

        /** A child in the host's shadow tree: it carries a shadow mode its host does not. A host that is itself inside
         *  a shadow tree is ambiguous in this format; its children are then treated as light DOM (rendered flat). */
        boolean isShadowChild(int host, int k) {
            String km = shadowMode(k);
            return ("open".equals(km) || "closed".equals(km)) && shadowMode(host) == null;
        }

        /** Element children (light DOM, no pseudo nodes), in document order — what {@code el.children} counts. */
        List<Integer> elementChildren(int x) {
            List<Integer> kids = children.get(x);
            List<Integer> out = new ArrayList<>();
            if (kids == null) return out;
            for (int k : kids) {
                if (type(k) != 1 || pseudo.containsKey(k) || isShadowChild(x, k) || "user-agent".equals(shadowMode(k))) continue;
                out.add(k);
            }
            return out;
        }

        int resolvePath(int[] path) {
            List<Integer> top = elementChildren(0);
            if (top.isEmpty()) return -1;
            int cur = top.get(0);   // documentElement
            for (int idx : path) {
                List<Integer> kids = elementChildren(cur);
                if (idx < 0 || idx >= kids.size()) return -1;
                cur = kids.get(idx);
            }
            return cur;
        }

        @SuppressWarnings("unchecked")
        static List<Object> list(Object o) { return o instanceof List ? (List<Object>) o : Collections.emptyList(); }

        @SuppressWarnings("unchecked")
        static Map<Integer, Integer> rareStr(Object o) {
            Map<Integer, Integer> m = new HashMap<>();
            if (!(o instanceof Map)) return m;
            List<Object> idx = list(((Map<String, Object>) o).get("index"));
            List<Object> val = list(((Map<String, Object>) o).get("value"));
            for (int i = 0; i < idx.size() && i < val.size(); i++) {
                if (idx.get(i) instanceof Number && val.get(i) instanceof Number) {
                    m.put(((Number) idx.get(i)).intValue(), ((Number) val.get(i)).intValue());
                }
            }
            return m;
        }

        @SuppressWarnings("unchecked")
        static Set<Integer> rareBool(Object o) {
            Set<Integer> s = new HashSet<>();
            if (!(o instanceof Map)) return s;
            for (Object i : list(((Map<String, Object>) o).get("index"))) if (i instanceof Number) s.add(((Number) i).intValue());
            return s;
        }
    }

    /**
     * Δ146a: whether a URL runs script. Browsers ignore tab/CR/LF anywhere in the scheme and leading C0 controls and
     * spaces, so "java\tscript:" and "\u0001javascript:" count.
     */
    public static boolean isScriptUrl(String v) {
        if (v == null) return false;
        String s = v.replaceAll("[\t\n\r]", "").replaceFirst("^[\u0000-\u0020]+", "").toLowerCase(Locale.ROOT);
        return s.startsWith("javascript:") || s.startsWith("vbscript:");
    }

    /** A password field's value never reaches the bundle: same length (max 32) of bullets. */
    public static String mask(String v) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < Math.min(32, v.length()); i++) b.append('\u2022');
        return b.toString();
    }

    public static String escText(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '&') b.append("&amp;");
            else if (ch == '<') b.append("&lt;");
            else if (ch == '>') b.append("&gt;");
            else b.append(ch);
        }
        return b.toString();
    }

    public static String escAttr(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '&') b.append("&amp;");
            else if (ch == '"') b.append("&quot;");
            else if (ch == '<') b.append("&lt;");
            else if (ch == '>') b.append("&gt;");
            else b.append(ch);
        }
        return b.toString();
    }
}
