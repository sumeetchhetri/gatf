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
package com.gatf.selenium;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * M3 — semantic locators: {@code role@button[name=Save]}, {@code label@Email}, {@code placeholder@Search},
 * {@code testid@submit}, {@code alt@Logo}, {@code title@Close}, and exact / regex text ({@code text@=Save},
 * {@code text@/sav(e|ed)/i}; plain {@code text@x} keeps its old "contains" XPath meaning).
 *
 * <p>Value matching (every prefix and a role's {@code name}): plain {@code Save} = whole value, whitespace-collapsed,
 * case-insensitive; {@code ~Save} = substring, case-insensitive; {@code =Save} = exact, case-sensitive;
 * {@code /re/flags} = JavaScript regex. {@code testid} is always exact.
 *
 * <p>Resolution runs in the page ({@link #RESOLVER_JS}): it walks the document and every open shadow root, computes
 * implicit ARIA roles and a subset of the accessible-name algorithm (aria-labelledby, aria-label, label[for] /
 * wrapping label, fieldset legend / table caption / figure figcaption, alt, button value, text content, title,
 * placeholder). Every query skips hidden elements unless {@code [include-hidden]} (a role attribute, or a trailing
 * {@code [include-hidden]} on any other value). With {@code strictLocators} a semantic locator matching more than one element fails and
 * lists the candidates. Pure: the Selenium call lives in {@code SeleniumTest.semanticElements}.
 */
public final class SemanticLocators {

    public static final String DEFAULT_TEST_ID_ATTRIBUTE = "data-testid";
    public static final List<String> PREFIXES = List.of("role", "label", "placeholder", "testid", "alt", "title");

    private SemanticLocators() {}

    /** True for the semantic prefixes, and for {@code text@} with an exact ({@code =x}) or regex ({@code /x/}) value. */
    public static boolean isSemantic(String by, String classifier) {
        if (by == null) return false;
        String b = by.trim().toLowerCase(Locale.ROOT);
        if (PREFIXES.contains(b)) return true;
        if (!b.equals("text") || classifier == null) return false;
        String c = classifier.endsWith(INCLUDE_HIDDEN) ? classifier.substring(0, classifier.length() - INCLUDE_HIDDEN.length()).trim() : classifier;
        return c.startsWith("=") || isRegex(c);
    }

    static boolean isRegex(String v) {
        return v != null && REGEX.matcher(v).matches();
    }

    private static final Pattern REGEX = Pattern.compile("^/(.+)/([dgimsuvy]*)$", Pattern.DOTALL);
    private static final String INCLUDE_HIDDEN = "[include-hidden]";
    private static final Pattern ROLE = Pattern.compile("^([A-Za-z][A-Za-z0-9-]*)((?:\\[[^\\]]*\\])*)$", Pattern.DOTALL);
    private static final Pattern ATTR = Pattern.compile("\\[\\s*([A-Za-z-]+)\\s*(?:=\\s*(.*?))?\\s*\\]", Pattern.DOTALL);

    /**
     * The spec the resolver script takes: {@code kind}, and per kind {@code role}, {@code value} + {@code mode}
     * (full | contains | exact | regex) + {@code flags}, role states. Throws on a malformed locator.
     */
    public static Map<String, Object> parse(String by, String classifier) {
        String kind = by.trim().toLowerCase(Locale.ROOT);
        String c = classifier == null ? "" : classifier.trim();
        Map<String, Object> spec = new LinkedHashMap<String, Object>();
        spec.put("kind", kind);
        if (kind.equals("role")) {
            Matcher m = ROLE.matcher(c);
            if (!m.matches()) throw new IllegalArgumentException("Invalid role locator role@" + c + " (expected role@<role>[name=...][level=N]...)");
            spec.put("role", m.group(1).toLowerCase(Locale.ROOT));
            Matcher a = ATTR.matcher(m.group(2));
            while (a.find()) {
                String k = a.group(1).toLowerCase(Locale.ROOT);
                String v = a.group(2) == null ? null : unquote(a.group(2).trim());
                switch (k) {
                    case "name":
                        if (v == null) throw new IllegalArgumentException("role@" + c + ": [name] needs a value");
                        value(spec, v, false);
                        if (String.valueOf(spec.get("value")).trim().isEmpty()) throw new IllegalArgumentException("role@" + c + ": empty [name] would match everything");
                        break;
                    case "level":
                        try {
                            spec.put("level", Integer.valueOf(v));
                        } catch (Exception e) {
                            throw new IllegalArgumentException("role@" + c + ": [level] must be a number");
                        }
                        break;
                    case "checked": case "disabled": case "expanded": case "pressed": case "selected":
                        spec.put(k, v == null || !v.equalsIgnoreCase("false"));
                        break;
                    case "include-hidden":
                        spec.put("includeHidden", v == null || !v.equalsIgnoreCase("false"));
                        break;
                    default:
                        throw new IllegalArgumentException("role@" + c + ": unknown attribute [" + k + "] (name, level, checked, disabled, expanded, pressed, selected, include-hidden)");
                }
            }
            return spec;
        }
        if (!PREFIXES.contains(kind) && !kind.equals("text")) throw new IllegalArgumentException("not a semantic locator: " + by + "@");
        if (c.endsWith(INCLUDE_HIDDEN)) {
            spec.put("includeHidden", true);
            c = c.substring(0, c.length() - INCLUDE_HIDDEN.length()).trim();
        }
        if (c.isEmpty()) throw new IllegalArgumentException(kind + "@ needs a value");
        String u = unquote(c);
        if (kind.equals("testid") && u.startsWith("=")) u = u.substring(1);   // testid is always exact: testid@=x means x
        value(spec, kind.equals("testid") ? "=" + u : u, kind.equals("text"));
        if (String.valueOf(spec.get("value")).trim().isEmpty()) throw new IllegalArgumentException(kind + "@" + c + ": empty value would match everything");
        return spec;
    }

    private static void value(Map<String, Object> spec, String v, boolean textPrefix) {
        Matcher r = REGEX.matcher(v);
        if (r.matches()) {
            spec.put("mode", "regex");
            spec.put("value", r.group(1));
            spec.put("flags", checkRegex(r.group(1), r.group(2)).replace("g", ""));
        } else if (v.startsWith("=")) {
            spec.put("mode", "exact");
            spec.put("value", unquote(v.substring(1)));
        } else if (v.startsWith("~") && !textPrefix) {
            spec.put("mode", "contains");
            spec.put("value", unquote(v.substring(1)));
        } else {
            spec.put("mode", "full");
            spec.put("value", v);
        }
    }

    /**
     * Compile-time check of a JavaScript regex: flags must be distinct JS flags (u and v exclusive) and the pattern must
     * compile — approximated with java.util.regex after mapping the common JS-only syntax ({@code [^]}, u{hex} escapes,
     * group names with _ or $). Returns the flags; throws a readable error instead of a later in-page "not found".
     */
    static String checkRegex(String pattern, String flags) {
        int f = 0;
        for (int i = 0; i < flags.length(); i++) {
            char ch = flags.charAt(i);
            if (flags.indexOf(ch) != i) throw new IllegalArgumentException("Invalid regex /" + pattern + "/" + flags + ": duplicate flag '" + ch + "'");
            if (ch == 'i') f |= Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
            else if (ch == 'm') f |= Pattern.MULTILINE;
            else if (ch == 's') f |= Pattern.DOTALL;
            else if (ch == 'u' || ch == 'v') f |= Pattern.UNICODE_CASE;
        }
        if (flags.indexOf('u') >= 0 && flags.indexOf('v') >= 0) throw new IllegalArgumentException("Invalid regex /" + pattern + "/" + flags + ": flags u and v cannot be combined");
        // JS-only syntax Java rejects: [^] (any char), u{hex} escapes (u/v), group names with _ or $ (names are dropped)
        String j = pattern.replace("[^]", "[\\s\\S]").replace("\\u{", "\\x{")
                .replaceAll("\\(\\?<[A-Za-z_$][\\w$]*>", "(").replaceAll("\\\\k<[A-Za-z_$][\\w$]*>", "\\\\1");
        try {
            Pattern.compile(j, f);
        } catch (java.util.regex.PatternSyntaxException e) {
            throw new IllegalArgumentException("Invalid regex /" + pattern + "/" + flags + ": " + e.getDescription());
        }
        return flags;
    }

    static String unquote(String s) {
        if (s.length() >= 2 && (s.charAt(0) == '"' || s.charAt(0) == '\'') && s.charAt(s.length() - 1) == s.charAt(0)) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    /** Thrown when a strict semantic locator matches more than one element; never retried or swallowed by waits. */
    public static final class StrictViolation extends RuntimeException {
        private static final long serialVersionUID = 1L;
        public StrictViolation(String message) { super(message); }
    }

    /**
     * Δ148: the strict rule itself. A locator resolves strictly when it matches exactly one element: more is the
     * {@link #strictViolation} case, none is "not found". The recorder / picker ranking ({@link LocatorRanking}) and
     * the pick-locator fallback apply this same rule to their match counts.
     */
    public static boolean resolvesStrictly(int count) {
        return count == 1;
    }

    /**
     * Δ148a: a match count as reported by a page or a request body; -1 unless it is a whole number in 0..MAX_INT, so
     * 1.9 or 4294967297 (which {@code intValue()} turns into 1) never pass {@link #resolvesStrictly}.
     */
    public static int matchCount(Object n) {
        if (n instanceof Integer || n instanceof Short || n instanceof Byte) {
            int i = ((Number) n).intValue();
            return i >= 0 ? i : -1;
        }
        if (n instanceof Long) {
            long l = (Long) n;
            return l >= 0 && l <= Integer.MAX_VALUE ? (int) l : -1;
        }
        if (n instanceof Double || n instanceof Float) {
            double d = ((Number) n).doubleValue();
            return d >= 0 && d <= Integer.MAX_VALUE && d == Math.rint(d) ? (int) d : -1;
        }
        if (n instanceof java.math.BigInteger) {
            java.math.BigInteger b = (java.math.BigInteger) n;
            return b.signum() >= 0 && b.bitLength() < 32 ? b.intValue() : -1;
        }
        if (n instanceof java.math.BigDecimal) {
            try {
                int i = ((java.math.BigDecimal) n).intValueExact();
                return i >= 0 ? i : -1;
            } catch (ArithmeticException e) {
                return -1;
            }
        }
        return -1;
    }

    /** The strict-mode failure: the locator matched {@code candidates.size()} elements (first few described). */
    public static String strictViolation(String locator, List<String> candidates) {
        return strictViolation(locator, candidates, candidates.size());
    }

    /** The strict-mode failure: the locator matched {@code count} elements, of which {@code candidates} (capped) are described. */
    public static String strictViolation(String locator, List<String> candidates, int count) {
        StringBuilder b = new StringBuilder("Strict locator " + locator + " matched " + count + " elements");
        for (int i = 0; i < candidates.size() && i < 10; i++) b.append("\n  ").append(i + 1).append(") ").append(candidates.get(i));
        if (count > 10 || candidates.size() > 10) b.append("\n  ...");
        return b.toString();
    }

    /** Descriptions returned by the resolver, as strings (the driver hands back a List of Objects). */
    public static List<String> strings(Object o) {
        List<String> out = new ArrayList<String>();
        if (o instanceof List) for (Object x : (List<?>) o) out.add(String.valueOf(x));
        return out;
    }

    /**
     * Defines {@code __gatfSemantic(root, spec, testIdAttribute[, target])} → {@code {els, desc, count}}; with
     * {@code spec.kind == 'info'} it describes {@code target} instead ({@code {role, name, label, placeholder, testid,
     * alt, title}}), for the recorder / crawler locator ranking. Plain ES5 (Map caches only where present) so it runs through {@code executeScript} on
     * every driver. Keep backslashes doubled: this is a Java text block.
     */
    public static final String RESOLVER_FN = """
        var __gatfSemantic = function (root, spec, tidAttr, target) {
          var doc = root && root.ownerDocument ? root.ownerDocument : document;
          var win = doc.defaultView || window;
          var start = root || doc;
          function norm(s) { return (s == null ? '' : String(s)).replace(/[\\s\\u00a0]+/g, ' ').trim(); }
          function matches(v, sp) {
            if (v == null) return false;
            var s = norm(v), want = sp.value == null ? '' : String(sp.value);
            switch (sp.mode) {
              case 'regex': return new RegExp(want, sp.flags || '').test(s);
              case 'exact': return s === norm(want);
              case 'contains': return s.toLowerCase().indexOf(norm(want).toLowerCase()) >= 0;
              default: return s.toLowerCase() === norm(want).toLowerCase();
            }
          }
          function all(r) {
            var out = [];
            (function walk(n) {
              var list = n.querySelectorAll('*');
              for (var i = 0; i < list.length; i++) {
                out.push(list[i]);
                if (list[i].shadowRoot) walk(list[i].shadowRoot);
              }
            })(r);
            return out;
          }
          function tag(e) { return (e.localName || e.tagName || '').toLowerCase(); }
          function attr(e, n) { return e.getAttribute ? e.getAttribute(n) : null; }
          // per-call caches (computed style, hidden subtree, text content): keeps text@ / role@ linear on big pages
          var SC = typeof Map === 'function' ? new Map() : null, HC = SC && new Map(), TC = SC && new Map();
          function style(e) {
            if (SC && SC.has(e)) return SC.get(e);
            var v; try { v = win.getComputedStyle(e); } catch (x) { v = null; }
            if (SC) SC.set(e, v);
            return v;
          }
          // hidden attribute / aria-hidden / display:none on n or any ancestor (shadow hosts included)
          function hiddenTree(n) {
            if (!n || n.nodeType !== 1) return false;
            if (HC && HC.has(n)) return HC.get(n);
            var cs = style(n);
            var h = !!(n.hidden || attr(n, 'aria-hidden') === 'true' || (cs && cs.display === 'none'))
              || hiddenTree(n.parentElement || (n.parentNode && n.parentNode.host) || null);
            if (HC) HC.set(n, h);
            return h;
          }
          function hidden(e) {
            if (hiddenTree(e)) return true;
            var cs = style(e);
            return !!(cs && (cs.visibility === 'hidden' || cs.visibility === 'collapse'));
          }
          function inputType(e) { return (attr(e, 'type') || 'text').toLowerCase(); }
          function role(e) {
            var r = attr(e, 'role');
            if (r) return r.trim().split(/\\s+/)[0].toLowerCase();
            var t = tag(e);
            switch (t) {
              case 'button': return 'button';
              case 'a': case 'area': return e.hasAttribute('href') ? 'link' : null;
              case 'h1': case 'h2': case 'h3': case 'h4': case 'h5': case 'h6': return 'heading';
              case 'textarea': return 'textbox';
              case 'select': return (e.multiple || (e.size && e.size > 1)) ? 'listbox' : 'combobox';
              case 'option': return 'option';
              case 'img': return attr(e, 'alt') === '' ? 'presentation' : 'img';
              case 'ul': case 'ol': case 'menu': return 'list';
              case 'li': return 'listitem';
              case 'nav': return 'navigation';
              case 'main': return 'main';
              case 'aside': return 'complementary';
              case 'article': return 'article';
              case 'dialog': return 'dialog';
              case 'form': return 'form';
              case 'table': return 'table';
              case 'tr': return 'row';
              case 'td': return 'cell';
              case 'th': return 'columnheader';
              case 'fieldset': return 'group';
              case 'figure': return 'figure';
              case 'hr': return 'separator';
              case 'p': return 'paragraph';
              case 'progress': return 'progressbar';
              case 'section': return (attr(e, 'aria-label') || attr(e, 'aria-labelledby')) ? 'region' : null;
              case 'header': return e.closest && e.closest('article,aside,main,nav,section') ? null : 'banner';
              case 'footer': return e.closest && e.closest('article,aside,main,nav,section') ? null : 'contentinfo';
              case 'input':
                switch (inputType(e)) {
                  case 'button': case 'submit': case 'reset': case 'image': return 'button';
                  case 'checkbox': return 'checkbox';
                  case 'radio': return 'radio';
                  case 'range': return 'slider';
                  case 'number': return 'spinbutton';
                  case 'search': return attr(e, 'list') ? 'combobox' : 'searchbox';
                  case 'hidden': return null;
                  case 'email': case 'tel': case 'text': case 'url': return attr(e, 'list') ? 'combobox' : 'textbox';
                  default: return 'textbox';
                }
            }
            return null;
          }
          var FROM_CONTENT = { button: 1, cell: 1, checkbox: 1, columnheader: 1, gridcell: 1, heading: 1, link: 1, menuitem: 1,
            menuitemcheckbox: 1, menuitemradio: 1, option: 1, radio: 1, row: 1, rowheader: 1, 'switch': 1, tab: 1, tooltip: 1, treeitem: 1 };
          // allHidden: a hidden node referenced by aria-labelledby contributes all its text (accname 2A)
          function text(n, skipControls, allHidden) {
            var memo = TC && !skipControls && !allHidden;
            if (memo && TC.has(n)) return TC.get(n);
            var s = '';
            for (var c = n.firstChild; c; c = c.nextSibling) {
              if (c.nodeType === 3) s += c.nodeValue;
              else if (c.nodeType === 1) {
                var t = tag(c);
                if (t === 'script' || t === 'style' || t === 'template') continue;
                // a label's own text only: the control it wraps (and a select's options) is not part of the label
                if (skipControls && (t === 'select' || t === 'textarea' || t === 'input')) continue;
                if (!allHidden && hidden(c)) continue;
                var l = attr(c, 'aria-label');
                if (l) { s += ' ' + l + ' '; continue; }
                if (t === 'img' && attr(c, 'alt')) { s += ' ' + attr(c, 'alt') + ' '; continue; }
                if (t === 'br') { s += ' '; continue; }
                s += ' ' + text(c, skipControls, allHidden) + ' ';
              }
            }
            if (n.shadowRoot) s += ' ' + text(n.shadowRoot, skipControls, allHidden);
            if (memo) TC.set(n, s);
            return s;
          }
          var LABELABLE = { button: 1, input: 1, meter: 1, output: 1, progress: 1, select: 1, textarea: 1 };
          function labelable(e) {
            return !!LABELABLE[tag(e)] && !(tag(e) === 'input' && inputType(e) === 'hidden');
          }
          // the control a <label> labels: its for= target, else its first labelable descendant (HTML's rule)
          function labelTarget(l) {
            var f = attr(l, 'for');
            if (f) return byIdIn(l, f);
            var d = l.querySelectorAll ? l.querySelectorAll('button,input,meter,output,progress,select,textarea') : [];
            for (var i = 0; i < d.length; i++) if (labelable(d[i])) return d[i];
            return null;
          }
          function byIdIn(e, id) {
            var r = e.getRootNode ? e.getRootNode() : doc;
            return (r.getElementById ? r.getElementById(id) : null) || doc.getElementById(id);
          }
          function labelsOf(e) {
            var out = [];
            if (!labelable(e)) return out;   // only form controls are labelled by <label>
            if (e.labels && e.labels.length) { for (var i = 0; i < e.labels.length; i++) out.push(e.labels[i]); return out; }
            if (e.id) {
              var r = e.getRootNode ? e.getRootNode() : doc;
              var ls = r.querySelectorAll ? r.querySelectorAll('label[for]') : [];
              for (var j = 0; j < ls.length; j++) if (attr(ls[j], 'for') === e.id) out.push(ls[j]);
            }
            var p = e.closest ? e.closest('label') : null;
            if (p && out.indexOf(p) < 0 && labelTarget(p) === e) out.push(p);
            return out;
          }
          function labelText(e) {
            var lb = attr(e, 'aria-labelledby');
            if (lb) {
              var parts = [], ids = lb.trim().split(/\\s+/);
              for (var i = 0; i < ids.length; i++) { var t = byIdIn(e, ids[i]); if (t) parts.push(text(t, false, hidden(t))); }
              var joined = norm(parts.join(' '));
              if (joined) return joined;
            }
            var al = attr(e, 'aria-label');
            if (al && norm(al)) return norm(al);
            var ls = labelsOf(e);
            if (ls.length) {
              var s = [];
              for (var k = 0; k < ls.length; k++) s.push(text(ls[k], true));
              return norm(s.join(' '));
            }
            return '';
          }
          function accName(e, r) {
            var t = tag(e);
            var n = labelText(e);
            if (n) return n;
            var cap = t === 'fieldset' ? 'legend' : t === 'table' ? 'caption' : t === 'figure' ? 'figcaption' : null;
            if (cap) for (var ch = e.firstElementChild; ch; ch = ch.nextElementSibling) {
              if (tag(ch) === cap) { var cn = norm(text(ch)); if (cn) return cn; break; }
            }
            if ((t === 'img' || t === 'area' || (t === 'input' && inputType(e) === 'image')) && attr(e, 'alt')) return norm(attr(e, 'alt'));
            if (t === 'input') {
              var it = inputType(e);
              if (it === 'button' || it === 'submit' || it === 'reset') return norm(e.value || attr(e, 'value') || (it === 'submit' ? 'Submit' : it === 'reset' ? 'Reset' : ''));
            }
            if (FROM_CONTENT[r]) { var c = norm(text(e)); if (c) return c; }
            if (attr(e, 'title')) return norm(attr(e, 'title'));
            if (attr(e, 'placeholder')) return norm(attr(e, 'placeholder'));
            return '';
          }
          function state(e, name) {
            var a = attr(e, 'aria-' + name);
            if (a != null) return a === 'true' || a === 'mixed';
            if (name === 'expanded' || name === 'pressed') return null;   // not expandable / not a toggle: never matches [x=…]
            if (name === 'checked') return !!e.checked;
            if (name === 'selected') return !!e.selected;
            if (name === 'disabled') return !!e.disabled;
            return false;
          }
          function level(e) {
            var a = attr(e, 'aria-level');
            if (a) return parseInt(a, 10);
            var m = /^h([1-6])$/.exec(tag(e));
            return m ? parseInt(m[1], 10) : null;
          }
          function describe(e) {
            var d = '<' + tag(e) + (e.id ? '#' + e.id : '');
            var cl = attr(e, 'class');
            if (cl) d += '.' + norm(cl).split(' ').slice(0, 2).join('.');
            d += '>';
            var n = norm(accName(e, role(e)) || text(e));
            if (n) d += ' "' + (n.length > 40 ? n.substring(0, 37) + '...' : n) + '"';
            return d;
          }
          if (spec.kind === 'info') {
            var ir = role(target), il = labelText(target);
            return { role: ir, name: ir ? accName(target, ir) : '', label: il, placeholder: norm(attr(target, 'placeholder')),
              testid: attr(target, tidAttr || 'data-testid'), alt: norm(attr(target, 'alt')), title: norm(attr(target, 'title')) };
          }
          var els = [], k = spec.kind, cand = all(start);
          for (var i = 0; i < cand.length; i++) {
            var e = cand[i], ok = false;
            if (k === 'role') {
              var r = role(e);
              if (r !== spec.role) continue;
              if (!spec.includeHidden && hidden(e)) continue;
              if (spec.value != null && !matches(accName(e, r), spec)) continue;
              if (spec.level != null && level(e) !== spec.level) continue;
              var st = ['checked', 'disabled', 'expanded', 'pressed', 'selected'], bad = false;
              for (var s = 0; s < st.length; s++) if (spec[st[s]] != null && state(e, st[s]) !== spec[st[s]]) bad = true;
              ok = !bad;
            } else if (k === 'label') {
              var t = tag(e);
              if (t === 'label') continue;
              ok = (labelsOf(e).length > 0 || attr(e, 'aria-labelledby') || attr(e, 'aria-label')) && matches(labelText(e), spec);
            } else if (k === 'placeholder') {
              ok = matches(attr(e, 'placeholder'), spec);
            } else if (k === 'testid') {
              ok = matches(attr(e, tidAttr || 'data-testid'), spec);
            } else if (k === 'alt') {
              ok = matches(attr(e, 'alt'), spec);
            } else if (k === 'title') {
              ok = matches(attr(e, 'title'), spec);
            } else if (k === 'text') {
              var tg = tag(e);
              if (tg === 'script' || tg === 'style' || tg === 'head' || tg === 'title' || tg === 'html' || tg === 'body') continue;
              var v = (tg === 'input' && /^(button|submit|reset)$/.test(inputType(e))) ? (e.value || attr(e, 'value')) : text(e);
              ok = matches(v, spec);
            }
            if (ok && k !== 'role' && !spec.includeHidden && hidden(e)) ok = false;   // role checked hidden above
            if (ok) els.push(e);
          }
          if (k === 'text') {   // innermost matches only: drop an element whose descendant (shadow included) also matched
            var under = function (anc, n) {
              for (var p = n.parentNode || n.host; p; p = p.parentNode || p.host) if (p === anc) return true;
              return false;
            };
            els = els.filter(function (e) {
              for (var j = 0; j < els.length; j++) if (els[j] !== e && under(e, els[j])) return false;
              return true;
            });
          }
          var desc = [];
          for (var d = 0; d < els.length && d < 11; d++) desc.push(describe(els[d]));
          return { els: els, desc: desc, count: els.length };
        };
        """;

    /** {@code (root, spec, testIdAttribute)} → {@code {els: Element[], desc: string[], count}}. */
    public static final String RESOLVER_JS = RESOLVER_FN + "return __gatfSemantic(arguments[0], arguments[1], arguments[2]);\n";

    /**
     * M3 (recorder / crawler): defines {@code __gatfCandidates(el, testIdAttribute)} → the semantic locators that
     * would find {@code el}, each with its match count {@code n} ({@code {s:'testid'|'role'|'label', v, role, name, n}}),
     * for {@link LocatorRanking}. Needs {@link #RESOLVER_FN} defined first.
     */
    public static final String CANDIDATES_FN = """
        var __gatfCandidates = function (el, tidAttr) {
          var out = [];
          try {
            var info = __gatfSemantic(null, { kind: 'info' }, tidAttr, el);
            var n = function (spec) { return __gatfSemantic(null, spec, tidAttr).count; };
            if (info.testid) out.push({ s: 'testid', v: info.testid, n: n({ kind: 'testid', mode: 'exact', value: info.testid }) });
            if (info.role && info.name && info.role !== 'presentation' && info.role !== 'generic') {
              out.push({ s: 'role', role: info.role, name: info.name, n: n({ kind: 'role', role: info.role, mode: 'full', value: info.name }) });
            }
            if (info.label) out.push({ s: 'label', v: info.label, n: n({ kind: 'label', mode: 'full', value: info.label }) });
          } catch (e) { }
          return out;
        };
        """;

    /**
     * Δ148 (pick locator): defines {@code __gatfFallback(el)} → the legacy selector that finds {@code el} strictly
     * ({@code {s:'id'|'name'|'css', v, n:1}}), or null. id when unique in the document, else name when unique, else the
     * shortest {@code tag.class:nth-of-type(k)} path (built upwards) that matches exactly one element. An element inside
     * a shadow root gets null (id@ / name@ / css@ do not pierce shadow roots). Plain ES5, no other function needed; it
     * runs in the page (live picker) and, from the generated frontend module, against a trace snapshot's document.
     * Keep backslashes doubled: this is a Java text block.
     */
    public static final String FALLBACK_FN = """
        var __gatfFallback = function (el) {
          try {
            if (!el || el.nodeType !== 1) return null;
            var doc = el.ownerDocument || document;
            if (!el.getRootNode || el.getRootNode() !== doc) return null;   // shadow root or detached
            var esc = function (s) {   // CSS.serializeIdentifier, ES5
              var out = '';
              for (var i = 0; i < s.length; i++) {
                var c = s.charAt(i), cc = s.charCodeAt(i);
                if (cc === 0) out += String.fromCharCode(0xfffd);
                else if ((cc >= 1 && cc <= 31) || cc === 127 || (i === 0 && cc >= 48 && cc <= 57)
                    || (i === 1 && cc >= 48 && cc <= 57 && s.charCodeAt(0) === 45)) out += '\\\\' + cc.toString(16) + ' ';
                else if (i === 0 && cc === 45 && s.length === 1) out += '\\\\' + c;
                else if (cc >= 128 || cc === 45 || cc === 95 || (cc >= 48 && cc <= 57) || (cc >= 65 && cc <= 90) || (cc >= 97 && cc <= 122)) out += c;
                else out += '\\\\' + c;
              }
              return out;
            };
            var count = function (q) { try { return doc.querySelectorAll(q).length; } catch (x) { return -1; } };
            var id = el.getAttribute('id');
            if (id && count('#' + esc(id)) === 1) return { s: 'id', v: id, n: 1 };
            var nm = el.getAttribute('name');
            if (nm && doc.getElementsByName && doc.getElementsByName(nm).length === 1) return { s: 'name', v: nm, n: 1 };
            var path = '', e = el;
            for (var depth = 0; e && e.nodeType === 1 && depth < 64; depth++) {
              var t = (e.localName || '').toLowerCase();
              if (!/^[a-z][a-z0-9-]*$/.test(t)) return null;
              var seg = t, cl = (e.getAttribute('class') || '').replace(/^\\s+|\\s+$/g, '');
              if (cl) seg += '.' + esc(cl.split(/\\s+/)[0]);
              var p = e.parentElement;
              if (p) {
                var k = 0, same = 0;
                for (var s = p.firstElementChild; s; s = s.nextElementSibling) {
                  if ((s.localName || '').toLowerCase() === t) { same++; if (s === e) k = same; }
                }
                if (same > 1) seg += ':nth-of-type(' + k + ')';
              }
              path = path ? seg + ' > ' + path : seg;
              if (count(path) === 1) return { s: 'css', v: path, n: 1 };
              e = p;
            }
            return null;
          } catch (x) { return null; }
        };
        """;
}
