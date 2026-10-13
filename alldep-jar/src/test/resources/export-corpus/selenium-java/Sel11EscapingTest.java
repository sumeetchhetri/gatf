// Exported from 11-escaping.sel by GATF (gatf export selenium-java). Review every TODO(gatf-export) line.
// 15 of 15 statement(s) exported, 0 TODO, 5 note(s): see the export report.
// Environment variables (names only): ${baseUrl} -> GATF_BASEURL
// Needs selenium-java and junit-jupiter. SELENIUM_REMOTE_URL runs it on a Selenium Grid; -Dgatf.export.browser.binary and
// -Dgatf.export.browser.args (comma-separated, e.g. --headless=new) adjust the local browser.
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.ElementNotInteractableException;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;

import static org.junit.jupiter.api.Assertions.fail;

class Sel11EscapingTest {

    private WebDriver driver;
    /** The element timeout: seleasy's default, then each 'timeout' statement. */
    private Duration timeout = Duration.ofSeconds(60);
    private final Map<String, String> vars = new HashMap<>();

    @BeforeEach
    void openBrowser() throws Exception {
        driver = newDriver();
    }

    @AfterEach
    void closeBrowser() {
        try {
            if (driver != null) driver.quit();
        } finally {
            driver = null;
        }
    }

    @Test
    @DisplayName("11-escaping")
    void test() throws Exception {
        // Δ161 export corpus (selenium-java, from Δ160): adversarial strings. Every value must survive as the same characters in Java.
        // gatf-export: 'open chrome' - the driver is created by newDriver() before the test
        driver.get(env("GATF_BASEURL") + "/a'b\\c");
        type(actionable(by("id@q", By.id("q")), timeout, "type"), "it's \\\"quoted\\\" with `back` ticks and ${ not a ref} and " + v("who"), "");
        type(actionable(by("id@q", By.id("q")), timeout, "type"), "back\\slash \\n stays two chars", "");
        type(actionable(by("id@q", By.id("q")), timeout, "type"), "line sep \u2028 para \u2029 end", "");
        type(actionable(by("id@q", By.id("q")), timeout, "type"), "</script> */ /* -->", "");
        actionable(by("css@'a[title=\"x\\\"y\"]'", By.cssSelector("a[title=\"x\\\"y\"]")), timeout, "click").click();
        actionable(by("id@'weird id\"]'", By.id("weird id\"]")), timeout, "click").click();
        actionable(by("name@'n\\\\a'", By.name("n\\\\a")), timeout, "click").click();
        actionable(by("xpath@\"//a[text()='it''s']\"", By.xpath("//a[text()='it''s']")), timeout, "click").click();
        find(semantic("label@'/a\\/b[/]c/i'", spec("kind", "label", "mode", "regex", "value", "a\\/b[/]c", "flags", "i")), timeout);
        find(semantic("role@button[name=*star]", spec("kind", "role", "role", "button", "mode", "full", "value", "*star")), timeout);
        check("?? id@x text =\"x\\y z\"", timeout, () -> now(by("id@x", By.id("x"))).getText(), a -> "x\\y z".equals(a));
        type(actionable(by("id@x", By.id("x")), timeout, "type"), "C:\\temp dir\\a b", "");
        // comment with a line separator U+2028 inside
        fail("emoji \ud83d\ude00 and tab\tend");
    }

    // ---- gatf-export runtime: Selenium + JUnit 5 only ------------------------------------------------------------

    /** The script's browser (chrome): local, or on a Selenium Grid when SELENIUM_REMOTE_URL is set. */
    private static WebDriver newDriver() throws Exception {
        ChromeOptions options = new ChromeOptions();
        String binary = System.getProperty("gatf.export.browser.binary", "");
        if (!binary.isEmpty()) options.setBinary(binary);
        for (String a : System.getProperty("gatf.export.browser.args", "").split(",")) if (!a.trim().isEmpty()) options.addArguments(a.trim());
        String grid = System.getenv("SELENIUM_REMOTE_URL");
        if (grid != null && !grid.isEmpty()) return new RemoteWebDriver(URI.create(grid).toURL(), options);
        return new ChromeDriver(options);
    }

    /** A ${...} value: read from the environment, never from a GATF config. */
    private static String env(String name) {
        String value = System.getenv(name);
        if (value == null) throw new IllegalStateException("gatf-export: set the environment variable " + name);
        return value;
    }

    /** A $v{...} script variable. */
    private String v(String name) {
        String value = vars.get(name);
        if (value == null) throw new IllegalStateException("gatf-export: the script variable " + name + " is not set");
        return value;
    }

    /** Every element a find expression matches now. */
    @FunctionalInterface
    private interface Finder {
        List<WebElement> find();
    }

    /** A find expression: its seleasy text, how to find it, and whether it must match one element only. */
    private static final class Loc {
        final String what;
        final Finder finder;
        final boolean strict;

        Loc(String what, Finder finder, boolean strict) {
            this.what = what;
            this.finder = finder;
            this.strict = strict;
        }
    }

    private Loc by(String what, By by) {
        return new Loc(what, () -> driver.findElements(by), false);
    }

    /** --strict-locators: a semantic locator must match one element (seleasy's strictLocators). */
    private static final boolean STRICT_LOCATORS = false;
    /** seleasy's testIdAttribute, read by testid@. */
    private static final String TEST_ID_ATTRIBUTE = "data-testid";
    /** seleasy's semantic-locator resolver (com.gatf.selenium.SemanticLocators.RESOLVER_JS), verbatim. */
    private static final String SEMANTIC_JS = ""
            + "var __gatfSemantic = function (root, spec, tidAttr, target) {\n"
            + "  var doc = root && root.ownerDocument ? root.ownerDocument : document;\n"
            + "  var win = doc.defaultView || window;\n"
            + "  var start = root || doc;\n"
            + "  function norm(s) { return (s == null ? '' : String(s)).replace(/[\\s\\u00a0]+/g, ' ').trim(); }\n"
            + "  function matches(v, sp) {\n"
            + "    if (v == null) return false;\n"
            + "    var s = norm(v), want = sp.value == null ? '' : String(sp.value);\n"
            + "    switch (sp.mode) {\n"
            + "      case 'regex': return new RegExp(want, sp.flags || '').test(s);\n"
            + "      case 'exact': return s === norm(want);\n"
            + "      case 'contains': return s.toLowerCase().indexOf(norm(want).toLowerCase()) >= 0;\n"
            + "      default: return s.toLowerCase() === norm(want).toLowerCase();\n"
            + "    }\n"
            + "  }\n"
            + "  function all(r) {\n"
            + "    var out = [];\n"
            + "    (function walk(n) {\n"
            + "      var list = n.querySelectorAll('*');\n"
            + "      for (var i = 0; i < list.length; i++) {\n"
            + "        out.push(list[i]);\n"
            + "        if (list[i].shadowRoot) walk(list[i].shadowRoot);\n"
            + "      }\n"
            + "    })(r);\n"
            + "    return out;\n"
            + "  }\n"
            + "  function tag(e) { return (e.localName || e.tagName || '').toLowerCase(); }\n"
            + "  function attr(e, n) { return e.getAttribute ? e.getAttribute(n) : null; }\n"
            + "  // per-call caches (computed style, hidden subtree, text content): keeps text@ / role@ linear on big pages\n"
            + "  var SC = typeof Map === 'function' ? new Map() : null, HC = SC && new Map(), TC = SC && new Map();\n"
            + "  function style(e) {\n"
            + "    if (SC && SC.has(e)) return SC.get(e);\n"
            + "    var v; try { v = win.getComputedStyle(e); } catch (x) { v = null; }\n"
            + "    if (SC) SC.set(e, v);\n"
            + "    return v;\n"
            + "  }\n"
            + "  // hidden attribute / aria-hidden / display:none on n or any ancestor (shadow hosts included)\n"
            + "  function hiddenTree(n) {\n"
            + "    if (!n || n.nodeType !== 1) return false;\n"
            + "    if (HC && HC.has(n)) return HC.get(n);\n"
            + "    var cs = style(n);\n"
            + "    var h = !!(n.hidden || attr(n, 'aria-hidden') === 'true' || (cs && cs.display === 'none'))\n"
            + "      || hiddenTree(n.parentElement || (n.parentNode && n.parentNode.host) || null);\n"
            + "    if (HC) HC.set(n, h);\n"
            + "    return h;\n"
            + "  }\n"
            + "  function hidden(e) {\n"
            + "    if (hiddenTree(e)) return true;\n"
            + "    var cs = style(e);\n"
            + "    return !!(cs && (cs.visibility === 'hidden' || cs.visibility === 'collapse'));\n"
            + "  }\n"
            + "  function inputType(e) { return (attr(e, 'type') || 'text').toLowerCase(); }\n"
            + "  function role(e) {\n"
            + "    var r = attr(e, 'role');\n"
            + "    if (r) return r.trim().split(/\\s+/)[0].toLowerCase();\n"
            + "    var t = tag(e);\n"
            + "    switch (t) {\n"
            + "      case 'button': return 'button';\n"
            + "      case 'a': case 'area': return e.hasAttribute('href') ? 'link' : null;\n"
            + "      case 'h1': case 'h2': case 'h3': case 'h4': case 'h5': case 'h6': return 'heading';\n"
            + "      case 'textarea': return 'textbox';\n"
            + "      case 'select': return (e.multiple || (e.size && e.size > 1)) ? 'listbox' : 'combobox';\n"
            + "      case 'option': return 'option';\n"
            + "      case 'img': return attr(e, 'alt') === '' ? 'presentation' : 'img';\n"
            + "      case 'ul': case 'ol': case 'menu': return 'list';\n"
            + "      case 'li': return 'listitem';\n"
            + "      case 'nav': return 'navigation';\n"
            + "      case 'main': return 'main';\n"
            + "      case 'aside': return 'complementary';\n"
            + "      case 'article': return 'article';\n"
            + "      case 'dialog': return 'dialog';\n"
            + "      case 'form': return 'form';\n"
            + "      case 'table': return 'table';\n"
            + "      case 'tr': return 'row';\n"
            + "      case 'td': return 'cell';\n"
            + "      case 'th': return 'columnheader';\n"
            + "      case 'fieldset': return 'group';\n"
            + "      case 'figure': return 'figure';\n"
            + "      case 'hr': return 'separator';\n"
            + "      case 'p': return 'paragraph';\n"
            + "      case 'progress': return 'progressbar';\n"
            + "      case 'section': return (attr(e, 'aria-label') || attr(e, 'aria-labelledby')) ? 'region' : null;\n"
            + "      case 'header': return e.closest && e.closest('article,aside,main,nav,section') ? null : 'banner';\n"
            + "      case 'footer': return e.closest && e.closest('article,aside,main,nav,section') ? null : 'contentinfo';\n"
            + "      case 'input':\n"
            + "        switch (inputType(e)) {\n"
            + "          case 'button': case 'submit': case 'reset': case 'image': return 'button';\n"
            + "          case 'checkbox': return 'checkbox';\n"
            + "          case 'radio': return 'radio';\n"
            + "          case 'range': return 'slider';\n"
            + "          case 'number': return 'spinbutton';\n"
            + "          case 'search': return attr(e, 'list') ? 'combobox' : 'searchbox';\n"
            + "          case 'hidden': return null;\n"
            + "          case 'email': case 'tel': case 'text': case 'url': return attr(e, 'list') ? 'combobox' : 'textbox';\n"
            + "          default: return 'textbox';\n"
            + "        }\n"
            + "    }\n"
            + "    return null;\n"
            + "  }\n"
            + "  var FROM_CONTENT = { button: 1, cell: 1, checkbox: 1, columnheader: 1, gridcell: 1, heading: 1, link: 1, menuitem: 1,\n"
            + "    menuitemcheckbox: 1, menuitemradio: 1, option: 1, radio: 1, row: 1, rowheader: 1, 'switch': 1, tab: 1, tooltip: 1, treeitem: 1 };\n"
            + "  // allHidden: a hidden node referenced by aria-labelledby contributes all its text (accname 2A)\n"
            + "  function text(n, skipControls, allHidden) {\n"
            + "    var memo = TC && !skipControls && !allHidden;\n"
            + "    if (memo && TC.has(n)) return TC.get(n);\n"
            + "    var s = '';\n"
            + "    for (var c = n.firstChild; c; c = c.nextSibling) {\n"
            + "      if (c.nodeType === 3) s += c.nodeValue;\n"
            + "      else if (c.nodeType === 1) {\n"
            + "        var t = tag(c);\n"
            + "        if (t === 'script' || t === 'style' || t === 'template') continue;\n"
            + "        // a label's own text only: the control it wraps (and a select's options) is not part of the label\n"
            + "        if (skipControls && (t === 'select' || t === 'textarea' || t === 'input')) continue;\n"
            + "        if (!allHidden && hidden(c)) continue;\n"
            + "        var l = attr(c, 'aria-label');\n"
            + "        if (l) { s += ' ' + l + ' '; continue; }\n"
            + "        if (t === 'img' && attr(c, 'alt')) { s += ' ' + attr(c, 'alt') + ' '; continue; }\n"
            + "        if (t === 'br') { s += ' '; continue; }\n"
            + "        s += ' ' + text(c, skipControls, allHidden) + ' ';\n"
            + "      }\n"
            + "    }\n"
            + "    if (n.shadowRoot) s += ' ' + text(n.shadowRoot, skipControls, allHidden);\n"
            + "    if (memo) TC.set(n, s);\n"
            + "    return s;\n"
            + "  }\n"
            + "  var LABELABLE = { button: 1, input: 1, meter: 1, output: 1, progress: 1, select: 1, textarea: 1 };\n"
            + "  function labelable(e) {\n"
            + "    return !!LABELABLE[tag(e)] && !(tag(e) === 'input' && inputType(e) === 'hidden');\n"
            + "  }\n"
            + "  // the control a <label> labels: its for= target, else its first labelable descendant (HTML's rule)\n"
            + "  function labelTarget(l) {\n"
            + "    var f = attr(l, 'for');\n"
            + "    if (f) return byIdIn(l, f);\n"
            + "    var d = l.querySelectorAll ? l.querySelectorAll('button,input,meter,output,progress,select,textarea') : [];\n"
            + "    for (var i = 0; i < d.length; i++) if (labelable(d[i])) return d[i];\n"
            + "    return null;\n"
            + "  }\n"
            + "  function byIdIn(e, id) {\n"
            + "    var r = e.getRootNode ? e.getRootNode() : doc;\n"
            + "    return (r.getElementById ? r.getElementById(id) : null) || doc.getElementById(id);\n"
            + "  }\n"
            + "  function labelsOf(e) {\n"
            + "    var out = [];\n"
            + "    if (!labelable(e)) return out;   // only form controls are labelled by <label>\n"
            + "    if (e.labels && e.labels.length) { for (var i = 0; i < e.labels.length; i++) out.push(e.labels[i]); return out; }\n"
            + "    if (e.id) {\n"
            + "      var r = e.getRootNode ? e.getRootNode() : doc;\n"
            + "      var ls = r.querySelectorAll ? r.querySelectorAll('label[for]') : [];\n"
            + "      for (var j = 0; j < ls.length; j++) if (attr(ls[j], 'for') === e.id) out.push(ls[j]);\n"
            + "    }\n"
            + "    var p = e.closest ? e.closest('label') : null;\n"
            + "    if (p && out.indexOf(p) < 0 && labelTarget(p) === e) out.push(p);\n"
            + "    return out;\n"
            + "  }\n"
            + "  function labelText(e) {\n"
            + "    var lb = attr(e, 'aria-labelledby');\n"
            + "    if (lb) {\n"
            + "      var parts = [], ids = lb.trim().split(/\\s+/);\n"
            + "      for (var i = 0; i < ids.length; i++) { var t = byIdIn(e, ids[i]); if (t) parts.push(text(t, false, hidden(t))); }\n"
            + "      var joined = norm(parts.join(' '));\n"
            + "      if (joined) return joined;\n"
            + "    }\n"
            + "    var al = attr(e, 'aria-label');\n"
            + "    if (al && norm(al)) return norm(al);\n"
            + "    var ls = labelsOf(e);\n"
            + "    if (ls.length) {\n"
            + "      var s = [];\n"
            + "      for (var k = 0; k < ls.length; k++) s.push(text(ls[k], true));\n"
            + "      return norm(s.join(' '));\n"
            + "    }\n"
            + "    return '';\n"
            + "  }\n"
            + "  function accName(e, r) {\n"
            + "    var t = tag(e);\n"
            + "    var n = labelText(e);\n"
            + "    if (n) return n;\n"
            + "    var cap = t === 'fieldset' ? 'legend' : t === 'table' ? 'caption' : t === 'figure' ? 'figcaption' : null;\n"
            + "    if (cap) for (var ch = e.firstElementChild; ch; ch = ch.nextElementSibling) {\n"
            + "      if (tag(ch) === cap) { var cn = norm(text(ch)); if (cn) return cn; break; }\n"
            + "    }\n"
            + "    if ((t === 'img' || t === 'area' || (t === 'input' && inputType(e) === 'image')) && attr(e, 'alt')) return norm(attr(e, 'alt'));\n"
            + "    if (t === 'input') {\n"
            + "      var it = inputType(e);\n"
            + "      if (it === 'button' || it === 'submit' || it === 'reset') return norm(e.value || attr(e, 'value') || (it === 'submit' ? 'Submit' : it === 'reset' ? 'Reset' : ''));\n"
            + "    }\n"
            + "    if (FROM_CONTENT[r]) { var c = norm(text(e)); if (c) return c; }\n"
            + "    if (attr(e, 'title')) return norm(attr(e, 'title'));\n"
            + "    if (attr(e, 'placeholder')) return norm(attr(e, 'placeholder'));\n"
            + "    return '';\n"
            + "  }\n"
            + "  function state(e, name) {\n"
            + "    var a = attr(e, 'aria-' + name);\n"
            + "    if (a != null) return a === 'true' || a === 'mixed';\n"
            + "    if (name === 'expanded' || name === 'pressed') return null;   // not expandable / not a toggle: never matches [x=\u2026]\n"
            + "    if (name === 'checked') return !!e.checked;\n"
            + "    if (name === 'selected') return !!e.selected;\n"
            + "    if (name === 'disabled') return !!e.disabled;\n"
            + "    return false;\n"
            + "  }\n"
            + "  function level(e) {\n"
            + "    var a = attr(e, 'aria-level');\n"
            + "    if (a) return parseInt(a, 10);\n"
            + "    var m = /^h([1-6])$/.exec(tag(e));\n"
            + "    return m ? parseInt(m[1], 10) : null;\n"
            + "  }\n"
            + "  function describe(e) {\n"
            + "    var d = '<' + tag(e) + (e.id ? '#' + e.id : '');\n"
            + "    var cl = attr(e, 'class');\n"
            + "    if (cl) d += '.' + norm(cl).split(' ').slice(0, 2).join('.');\n"
            + "    d += '>';\n"
            + "    var n = norm(accName(e, role(e)) || text(e));\n"
            + "    if (n) d += ' \"' + (n.length > 40 ? n.substring(0, 37) + '...' : n) + '\"';\n"
            + "    return d;\n"
            + "  }\n"
            + "  if (spec.kind === 'info') {\n"
            + "    var ir = role(target), il = labelText(target);\n"
            + "    return { role: ir, name: ir ? accName(target, ir) : '', label: il, placeholder: norm(attr(target, 'placeholder')),\n"
            + "      testid: attr(target, tidAttr || 'data-testid'), alt: norm(attr(target, 'alt')), title: norm(attr(target, 'title')) };\n"
            + "  }\n"
            + "  var els = [], k = spec.kind, cand = all(start);\n"
            + "  for (var i = 0; i < cand.length; i++) {\n"
            + "    var e = cand[i], ok = false;\n"
            + "    if (k === 'role') {\n"
            + "      var r = role(e);\n"
            + "      if (r !== spec.role) continue;\n"
            + "      if (!spec.includeHidden && hidden(e)) continue;\n"
            + "      if (spec.value != null && !matches(accName(e, r), spec)) continue;\n"
            + "      if (spec.level != null && level(e) !== spec.level) continue;\n"
            + "      var st = ['checked', 'disabled', 'expanded', 'pressed', 'selected'], bad = false;\n"
            + "      for (var s = 0; s < st.length; s++) if (spec[st[s]] != null && state(e, st[s]) !== spec[st[s]]) bad = true;\n"
            + "      ok = !bad;\n"
            + "    } else if (k === 'label') {\n"
            + "      var t = tag(e);\n"
            + "      if (t === 'label') continue;\n"
            + "      ok = (labelsOf(e).length > 0 || attr(e, 'aria-labelledby') || attr(e, 'aria-label')) && matches(labelText(e), spec);\n"
            + "    } else if (k === 'placeholder') {\n"
            + "      ok = matches(attr(e, 'placeholder'), spec);\n"
            + "    } else if (k === 'testid') {\n"
            + "      ok = matches(attr(e, tidAttr || 'data-testid'), spec);\n"
            + "    } else if (k === 'alt') {\n"
            + "      ok = matches(attr(e, 'alt'), spec);\n"
            + "    } else if (k === 'title') {\n"
            + "      ok = matches(attr(e, 'title'), spec);\n"
            + "    } else if (k === 'text') {\n"
            + "      var tg = tag(e);\n"
            + "      if (tg === 'script' || tg === 'style' || tg === 'head' || tg === 'title' || tg === 'html' || tg === 'body') continue;\n"
            + "      var v = (tg === 'input' && /^(button|submit|reset)$/.test(inputType(e))) ? (e.value || attr(e, 'value')) : text(e);\n"
            + "      ok = matches(v, spec);\n"
            + "    }\n"
            + "    if (ok && k !== 'role' && !spec.includeHidden && hidden(e)) ok = false;   // role checked hidden above\n"
            + "    if (ok) els.push(e);\n"
            + "  }\n"
            + "  if (k === 'text') {   // innermost matches only: drop an element whose descendant (shadow included) also matched\n"
            + "    var under = function (anc, n) {\n"
            + "      for (var p = n.parentNode || n.host; p; p = p.parentNode || p.host) if (p === anc) return true;\n"
            + "      return false;\n"
            + "    };\n"
            + "    els = els.filter(function (e) {\n"
            + "      for (var j = 0; j < els.length; j++) if (els[j] !== e && under(e, els[j])) return false;\n"
            + "      return true;\n"
            + "    });\n"
            + "  }\n"
            + "  var desc = [];\n"
            + "  for (var d = 0; d < els.length && d < 11; d++) desc.push(describe(els[d]));\n"
            + "  return { els: els, desc: desc, count: els.length };\n"
            + "};\n"
            + "return __gatfSemantic(arguments[0], arguments[1], arguments[2]);\n";

    /** A semantic locator spec, as SemanticLocators.parse builds it. */
    private static Map<String, Object> spec(Object... keyValue) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValue.length; i += 2) m.put((String) keyValue[i], keyValue[i + 1]);
        return m;
    }

    /** role@ label@ placeholder@ testid@ alt@ title@ text@=x text@/re/: resolved in the page by SEMANTIC_JS. */
    private Loc semantic(String what, Map<String, Object> spec) {
        return new Loc(what, () -> {
            Object r = ((JavascriptExecutor) driver).executeScript(SEMANTIC_JS, null, spec, TEST_ID_ATTRIBUTE);
            List<WebElement> els = new ArrayList<>();
            if (r instanceof Map && ((Map<?, ?>) r).get("els") instanceof List) {
                for (Object o : (List<?>) ((Map<?, ?>) r).get("els")) if (o instanceof WebElement) els.add((WebElement) o);
            }
            return els;
        }, STRICT_LOCATORS);
    }

    /** The element seleasy acts on: the first match, waited for up to t (a strict locator must match one element). */
    private WebElement find(Loc l, Duration t) {
        long end = System.nanoTime() + t.toNanos();
        while (true) {
            try {
                List<WebElement> els = l.finder.find();
                if (els.size() > 1 && l.strict) throw new AssertionError("Strict locator " + l.what + " matched " + els.size() + " elements");
                if (!els.isEmpty()) return els.get(0);
            } catch (StaleElementReferenceException e) {
                // the page changed while searching: search again
            }
            if (System.nanoTime() > end) throw new NoSuchElementException("Element not found by selector " + l.what + " (waited " + t.getSeconds() + "s)");
            pause(100L);
        }
    }

    /** The first match right now (assertions re-read it on every check). */
    private WebElement now(Loc l) {
        return find(l, Duration.ZERO);
    }

    /**
     * find(), then until the element is displayed (not for upload / submit) and enabled (not for hover / drag):
     * an approximation of seleasy's actionability probe.
     */
    private WebElement actionable(Loc l, Duration t, String mode) {
        long end = System.nanoTime() + t.toNanos();
        boolean visible = !mode.equals("upload") && !mode.equals("submit"), enabled = !mode.equals("hover") && !mode.equals("drag");
        WebElement e = find(l, t);
        while (true) {
            try {
                if ((!visible || e.isDisplayed()) && (!enabled || e.isEnabled())) return e;
            } catch (StaleElementReferenceException x) {
                e = find(l, Duration.ofNanos(Math.max(0L, end - System.nanoTime())));   // replaced while waiting: find it again
                continue;
            }
            if (System.nanoTime() > end) throw new ElementNotInteractableException(l.what + " is not " + (visible ? "displayed and " : "") + "enabled for " + mode + " (waited " + t.getSeconds() + "s)");
            pause(100L);
        }
    }

    /** Reads a value for an assertion. */
    @FunctionalInterface
    private interface Read {
        String get();
    }

    /** seleasy's comparison, expected.op(actual). */
    @FunctionalInterface
    private interface Expect {
        boolean test(String actual);
    }

    /** Re-reads the value until the comparison passes or t runs out (web-first). */
    private void check(String statement, Duration t, Read read, Expect expect) {
        long end = System.nanoTime() + t.toNanos();
        String last = null;
        RuntimeException error = null;
        while (true) {
            try {
                last = read.get();
                error = null;
                if (last != null && expect.test(last)) return;
            } catch (WebDriverException | IllegalStateException e) {
                error = e;
            }
            if (System.nanoTime() > end) break;
            pause(100L);
        }
        throw new AssertionError(statement + " failed after " + t.getSeconds() + "s; " + (error != null ? error.getMessage() : "actual: " + (last == null ? "null" : "'" + last + "'")), error);
    }

    /** sendKeys, then the type qualifier's event as seleasy fires it (bl, ch, fo, bk, cl). */
    private void type(WebElement e, CharSequence text, String qualifier) {
        e.sendKeys(text);
        switch (qualifier) {
            case "bl": js("arguments[0].blur();", e); break;
            case "ch": js("arguments[0].dispatchEvent(new Event('change', {bubbles: true}));", e); break;
            case "fo": e.click(); js("arguments[0].focus();", e); break;
            case "bk": e.sendKeys(Keys.chord(Keys.BACK_SPACE)); e.click(); break;
            case "cl": e.click(); break;
            default: break;
        }
    }

    /** executeScript: the script is a function body and sees 'arguments', as in seleasy. */
    private Object js(String script, Object... args) {
        return ((JavascriptExecutor) driver).executeScript(script, args);
    }

    private static void pause(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted", e);
        }
    }
}
