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
 * wrapping label, alt, button value, text content, title, placeholder). Role queries skip hidden elements unless
 * {@code [include-hidden]}. With {@code strictLocators} a semantic locator matching more than one element fails and
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
        return b.equals("text") && classifier != null && (classifier.startsWith("=") || isRegex(classifier));
    }

    static boolean isRegex(String v) {
        return v != null && REGEX.matcher(v).matches();
    }

    private static final Pattern REGEX = Pattern.compile("^/(.+)/([dgimsuy]*)$", Pattern.DOTALL);
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
        if (c.isEmpty()) throw new IllegalArgumentException(kind + "@ needs a value");
        value(spec, kind.equals("testid") ? "=" + unquote(c) : unquote(c), kind.equals("text"));
        return spec;
    }

    private static void value(Map<String, Object> spec, String v, boolean textPrefix) {
        Matcher r = REGEX.matcher(v);
        if (r.matches()) {
            spec.put("mode", "regex");
            spec.put("value", r.group(1));
            spec.put("flags", r.group(2).replace("g", ""));
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

    /** The strict-mode failure: the locator matched {@code count} elements (first few described). */
    public static String strictViolation(String locator, List<String> candidates) {
        StringBuilder b = new StringBuilder("Strict locator " + locator + " matched " + candidates.size() + " elements");
        for (int i = 0; i < candidates.size() && i < 10; i++) b.append("\n  ").append(i + 1).append(") ").append(candidates.get(i));
        if (candidates.size() > 10) b.append("\n  ...");
        return b.toString();
    }

    /** Descriptions returned by the resolver, as strings (the driver hands back a List of Objects). */
    public static List<String> strings(Object o) {
        List<String> out = new ArrayList<String>();
        if (o instanceof List) for (Object x : (List<?>) o) out.add(String.valueOf(x));
        return out;
    }

    /**
     * {@code (root, spec, testIdAttribute)} → {@code {els: Element[], desc: string[]}}. Plain ES5 so it runs through
     * {@code executeScript} on every driver. Keep backslashes doubled: this is a Java text block.
     */
    public static final String RESOLVER_JS = """
        var __gatfSemantic = function (root, spec, tidAttr) {
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
          function style(e) { try { return win.getComputedStyle(e); } catch (x) { return null; } }
          function hidden(e) {
            for (var n = e; n && n.nodeType === 1; n = n.parentElement || (n.parentNode && n.parentNode.host) || null) {
              if (n.hidden || attr(n, 'aria-hidden') === 'true') return true;
              var cs = style(n);
              if (cs && cs.display === 'none') return true;
              if (n === e && cs && (cs.visibility === 'hidden' || cs.visibility === 'collapse')) return true;
            }
            return false;
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
          function text(n, skipControls) {
            var s = '';
            for (var c = n.firstChild; c; c = c.nextSibling) {
              if (c.nodeType === 3) s += c.nodeValue;
              else if (c.nodeType === 1) {
                var t = tag(c);
                if (t === 'script' || t === 'style' || t === 'template') continue;
                // a label's own text only: the control it wraps (and a select's options) is not part of the label
                if (skipControls && (t === 'select' || t === 'textarea' || t === 'input')) continue;
                if (hidden(c)) continue;
                var l = attr(c, 'aria-label');
                if (l) { s += ' ' + l + ' '; continue; }
                if (t === 'img' && attr(c, 'alt')) { s += ' ' + attr(c, 'alt') + ' '; continue; }
                if (t === 'br') { s += ' '; continue; }
                s += ' ' + text(c, skipControls) + ' ';
              }
            }
            if (n.shadowRoot) s += ' ' + text(n.shadowRoot, skipControls);
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
              for (var i = 0; i < ids.length; i++) { var t = byIdIn(e, ids[i]); if (t) parts.push(text(t)); }
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
        return __gatfSemantic(arguments[0], arguments[1], arguments[2]);
        """;
}
