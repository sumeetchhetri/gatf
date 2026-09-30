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
package com.gatf.crawler;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * G3 — browser-driven crawler for JS-rendered apps (React/Angular/Vue), the SPA counterpart of the
 * regex {@link PortalCrawler}. It BFS-walks the RENDERED DOM of a real browser (optionally after a
 * credentialed login) and emits a starter seleasy suite plus a prioritized coverage plan.
 *
 * The browser is used only through {@link Browser} (navigate + execute script), so the crawl logic is
 * unit-testable with a fake and works over any WebDriver (Classic or BiDi). Everything page-side —
 * DOM settle, element discovery, selector choice (semantic testid → role → label when unique, else id → name → css →
 * xpath), SPA-safe value setting —
 * runs as injected JavaScript.
 *
 * Safety defaults: links matching {@link CrawlSpec#getExclude()} (logout/delete-style) are never
 * followed, so a credentialed crawl does not end its own session; POST forms are not submitted unless
 * {@code submitForms}; credentials are emitted as {@code ${username}} / {@code ${password}}, never in
 * clear text.
 */
public class SpaCrawler {

    /** The minimal browser surface the crawler needs. */
    public interface Browser {
        void get(String url) throws Exception;

        Object js(String script, Object... args) throws Exception;

        String currentUrl() throws Exception;

        void quit();
    }

    /** One crawled page, as scanned from the rendered DOM. */
    public static class PageInfo {
        public String url;
        public String title;
        public int depth;
        public List<Map<String, Object>> forms = new ArrayList<Map<String, Object>>();
        public List<Map<String, Object>> loose = new ArrayList<Map<String, Object>>();   // inputs outside <form>
        public List<Map<String, Object>> buttons = new ArrayList<Map<String, Object>>();
        public int linkCount;
        transient List<String> linksOut = new ArrayList<String>();
        public String error;

        int fieldCount() {
            int n = loose.size();
            for (Map<String, Object> f : forms) n += list(f.get("fields")).size();
            return n;
        }

        int requiredCount() {
            int n = 0;
            for (Map<String, Object> fl : loose) if (Boolean.TRUE.equals(fl.get("required"))) n++;
            for (Map<String, Object> f : forms)
                for (Map<String, Object> fl : list(f.get("fields"))) if (Boolean.TRUE.equals(fl.get("required"))) n++;
            return n;
        }
    }

    // ------------------------------------------------------------------ injected page scripts

    static final String SETTLE_JS = "return [document.readyState, document.getElementsByTagName('*').length];";

    // Shared helpers: unique-selector choice (id → name → css → xpath) + visibility.
    static final String HELPERS_JS =
        "function __u(q){try{return document.querySelectorAll(q).length===1;}catch(e){return false;}}"
        + "function __xp(el){var p=[];while(el&&el.nodeType===1&&el!==document.documentElement){var i=1,s=el.previousElementSibling;"
        + "while(s){if(s.tagName===el.tagName)i++;s=s.previousElementSibling;}p.unshift(el.tagName.toLowerCase()+'['+i+']');el=el.parentElement;}"
        + "return '/html/'+p.join('/');}"
        + "function __sel(el){var t=el.tagName.toLowerCase();"
        + "if(el.id&&__u('#'+CSS.escape(el.id)))return{s:'id',v:el.id};"
        + "var nm=el.getAttribute('name');if(nm&&__u(t+'[name=\"'+CSS.escape(nm)+'\"]'))return{s:'name',v:nm};"
        + "var cn=(typeof el.className==='string')?el.className.trim().split(/\\s+/).filter(Boolean):[];"
        + "for(var i=0;i<cn.length;i++){var q=t+'.'+CSS.escape(cn[i]);if(__u(q))return{s:'css',v:t+'.'+cn[i]};}"
        + "return{s:'xpath',v:__xp(el)};}"
        + "function __vis(el){var r=el.getBoundingClientRect(),st=getComputedStyle(el);"
        + "return r.width>0&&r.height>0&&st.visibility!=='hidden'&&st.display!=='none';}"
        + "function __find(s,v){if(s==='id')return document.getElementById(v);"
        + "if(s==='name')return document.querySelector('[name=\"'+CSS.escape(v)+'\"]');"
        + "if(s==='css')return document.querySelector(v);"
        + "return document.evaluate(v,document,null,XPathResult.FIRST_ORDERED_NODE_TYPE,null).singleNodeValue;}";

    // M3: semantic candidates (testid / role+name / label with match counts) for LocatorRanking
    static final String CAND_JS = com.gatf.selenium.SemanticLocators.RESOLVER_FN + com.gatf.selenium.SemanticLocators.CANDIDATES_FN
        + "function __cand(el){return el?__gatfCandidates(el,'data-testid'):[];}";

    static final String SCAN_JS = "return (function(){" + HELPERS_JS + CAND_JS
        + "var FIELDS='input:not([type=hidden]):not([type=submit]):not([type=button]):not([type=reset]):not([type=image]):not([type=file]),select,textarea';"
        + "function lbl(el){if(el.id){var l=document.querySelector('label[for=\"'+CSS.escape(el.id)+'\"]');if(l)return l.textContent.trim().slice(0,60);}"
        + "return (el.getAttribute('aria-label')||el.getAttribute('placeholder')||'').slice(0,60);}"
        + "function fld(el){return{sel:__sel(el),cand:__cand(el),tag:el.tagName.toLowerCase(),type:(el.getAttribute('type')||'').toLowerCase(),"
        + "name:el.getAttribute('name')||'',required:!!el.required,label:lbl(el),options:el.tagName==='SELECT'?el.options.length:0};}"
        + "var links=[];document.querySelectorAll('a[href],[routerlink],[ng-reflect-router-link],[data-href]').forEach(function(a){"
        + "var h=a.getAttribute('href')||a.getAttribute('routerlink')||a.getAttribute('ng-reflect-router-link')||a.getAttribute('data-href');"
        + "if(!h||/^(javascript:|mailto:|tel:|data:)/i.test(h))return;try{links.push(new URL(h,location.href).href);}catch(e){}});"
        + "var forms=[];document.querySelectorAll('form').forEach(function(f){var fs=[];"
        + "f.querySelectorAll(FIELDS).forEach(function(el){if(__vis(el)&&!el.disabled)fs.push(fld(el));});"
        + "var sb=f.querySelector('button[type=submit],input[type=submit],button:not([type])');"
        + "forms.push({sel:__sel(f),method:(f.getAttribute('method')||'get').toLowerCase(),fields:fs,submit:(sb&&__vis(sb))?__sel(sb):null,submitCand:(sb&&__vis(sb))?__cand(sb):[]});});"
        + "var loose=[];document.querySelectorAll(FIELDS).forEach(function(el){if(!el.closest('form')&&__vis(el)&&!el.disabled)loose.push(fld(el));});"
        + "var buttons=[];document.querySelectorAll('button,[role=button],input[type=button]').forEach(function(b){"
        + "if(__vis(b)&&!b.closest('form'))buttons.push({sel:__sel(b),cand:__cand(b),text:(b.textContent||b.value||'').trim().slice(0,40)});});"
        + "return{url:location.href,title:document.title,links:links,forms:forms,loose:loose,buttons:buttons};})();";

    // Find the login controls: explicit selectors ({s,v} or null) else discovery via the password field.
    static final String LOGIN_FIND_JS = "return (function(us,ps,ss){" + HELPERS_JS + CAND_JS
        + "var p=ps?__find(ps.s,ps.v):Array.prototype.find.call(document.querySelectorAll('input[type=password]'),__vis);"
        + "if(!p)return null;var scope=p.closest('form')||document;"
        + "var u=us?__find(us.s,us.v):null;if(!u){var c=scope.querySelectorAll('input[type=email],input[type=text],input:not([type])');"
        + "for(var i=0;i<c.length;i++){if(__vis(c[i])&&(c[i].compareDocumentPosition(p)&Node.DOCUMENT_POSITION_FOLLOWING))u=c[i];}}"
        + "var s=ss?__find(ss.s,ss.v):null;if(!s){s=scope.querySelector('button[type=submit],input[type=submit]');}"
        + "if(!s){s=Array.prototype.find.call(document.querySelectorAll('button,[role=button],input[type=button]'),function(b){"
        + "return __vis(b)&&/log\\s*in|sign\\s*in|submit|continue/i.test(b.textContent||b.value||'');});}"
        + "return{user:u?__sel(u):null,pass:__sel(p),submit:s?__sel(s):null,userCand:__cand(u),passCand:__cand(p),submitCand:__cand(s)};})(arguments[0],arguments[1],arguments[2]);";

    // Set a value the way frameworks observe it (native setter + input/change events), or click.
    static final String SET_JS = "return (function(s,v,val){" + HELPERS_JS
        + "var el=__find(s,v);if(!el)return false;if(val===null){el.click();return true;}"
        + "var d=Object.getOwnPropertyDescriptor(Object.getPrototypeOf(el),'value');if(d&&d.set)d.set.call(el,val);else el.value=val;"
        + "el.dispatchEvent(new Event('input',{bubbles:true}));el.dispatchEvent(new Event('change',{bubbles:true}));return true;"
        + "})(arguments[0],arguments[1],arguments[2]);";

    // ------------------------------------------------------------------ state

    private final CrawlSpec spec;
    private final Browser browser;
    private final Consumer<String> log;
    private volatile boolean stopped;
    private final List<PageInfo> pages = Collections.synchronizedList(new ArrayList<PageInfo>());
    private volatile int queued;
    private volatile String current;
    private Map<String, Object> loginSel;   // discovered {user, pass, submit} as {s,v} maps
    private String loginWarning;

    public SpaCrawler(CrawlSpec spec, Browser browser, Consumer<String> log) {
        this.spec = spec;
        this.browser = browser;
        this.log = log == null ? s -> { } : log;
    }

    public void stop() { stopped = true; }
    public List<PageInfo> pages() { synchronized (pages) { return new ArrayList<PageInfo>(pages); } }
    public int queued() { return queued; }
    public String current() { return current; }
    public String loginWarning() { return loginWarning; }

    // ------------------------------------------------------------------ crawl

    public List<PageInfo> crawl() throws Exception {
        Pattern exclude = Pattern.compile(spec.getExclude() == null || spec.getExclude().trim().isEmpty()
                ? CrawlSpec.DEFAULT_EXCLUDE : spec.getExclude());
        if (spec.isCredentialed()) login();

        String seed = norm(spec.getSeed());
        Deque<Object[]> q = new ArrayDeque<Object[]>();
        Set<String> seen = new LinkedHashSet<String>();
        q.add(new Object[] { seed, 0 });
        seen.add(seed);
        while (!q.isEmpty() && !stopped && pages.size() < spec.getMaxPages()) {
            Object[] item = q.poll();
            queued = q.size();
            String url = (String) item[0];
            int depth = (Integer) item[1];
            current = url;
            PageInfo pi = visit(url, depth);
            pages.add(pi);
            log.accept("[" + pages.size() + "/" + spec.getMaxPages() + "] " + url
                    + (pi.error != null ? "  ERROR " + pi.error : "  forms=" + pi.forms.size() + " fields=" + pi.fieldCount()));
            if (pi.error != null || depth >= spec.getMaxDepth()) continue;
            String here = stripFragment(url);
            for (String l : pi.linksOut) {
                if (isPlainAnchor(l) && stripFragment(l).equals(here)) continue; // #section on this same page
                String n = norm(l);
                if (n == null || seen.contains(n)) continue;
                if (spec.isSameOrigin() && !PortalCrawler.sameOrigin(n, seed)) continue;
                if (exclude.matcher(n).find()) { log.accept("  skip (excluded): " + n); seen.add(n); continue; }
                seen.add(n);
                q.add(new Object[] { n, depth + 1 });
            }
            queued = q.size();
        }
        current = null;
        return pages();
    }

    private PageInfo visit(String url, int depth) {
        PageInfo pi = new PageInfo();
        pi.url = url;
        pi.depth = depth;
        try {
            browser.get(url);
            settle();
            Map<String, Object> scan = map(browser.js(SCAN_JS));
            pi.title = str(scan.get("title"));
            pi.forms = list(scan.get("forms"));
            pi.loose = list(scan.get("loose"));
            pi.buttons = list(scan.get("buttons"));
            for (Object o : (List<?>) orEmpty(scan.get("links"))) pi.linksOut.add(String.valueOf(o));
            pi.linkCount = pi.linksOut.size();
        } catch (Exception e) {
            pi.error = e.getMessage() == null ? e.toString() : e.getMessage();
        }
        return pi;
    }

    // wait until readyState is complete AND the element count is stable across two polls (SPA render)
    void settle() throws Exception {
        long end = System.currentTimeMillis() + spec.getSettleMs();
        long last = -1;
        int stable = 0;
        while (System.currentTimeMillis() < end && !stopped) {
            List<?> r = (List<?>) browser.js(SETTLE_JS);
            long count = r == null || r.size() < 2 ? -1 : ((Number) r.get(1)).longValue();
            if (r != null && "complete".equals(String.valueOf(r.get(0))) && count == last) {
                if (++stable >= 2) return;
            } else {
                stable = 0;
            }
            last = count;
            Thread.sleep(spec.getPollMs());
        }
    }

    private void login() throws Exception {
        log.accept("login: " + spec.getLoginUrl());
        browser.get(spec.getLoginUrl());
        settle();
        loginSel = map(browser.js(LOGIN_FIND_JS, selArg(spec.getUserSel()), selArg(spec.getPassSel()), selArg(spec.getSubmitSel())));
        if (loginSel.isEmpty() || loginSel.get("pass") == null) {
            loginWarning = "no password field found on the login page — crawling unauthenticated";
            log.accept("  WARN " + loginWarning);
            loginSel = null;
            return;
        }
        Map<String, Object> u = map(loginSel.get("user")), p = map(loginSel.get("pass")), s = map(loginSel.get("submit"));
        if (!u.isEmpty()) browser.js(SET_JS, u.get("s"), u.get("v"), spec.getUsername());
        browser.js(SET_JS, p.get("s"), p.get("v"), spec.getPassword());
        if (!s.isEmpty()) browser.js(SET_JS, s.get("s"), s.get("v"), null);
        settle();
        // still showing a visible password field on the same URL → the login most likely failed
        Map<String, Object> after = map(browser.js(LOGIN_FIND_JS, null, null, null));
        if (!after.isEmpty() && after.get("pass") != null && norm(browser.currentUrl()).equals(norm(spec.getLoginUrl()))) {
            loginWarning = "still on the login page after submit — check credentials/selectors (crawl continues)";
            log.accept("  WARN " + loginWarning);
        } else {
            log.accept("  login submitted → " + browser.currentUrl());
        }
    }

    // ------------------------------------------------------------------ emission

    public String emitSeleasy(List<PageInfo> ps) {
        StringBuilder sb = new StringBuilder();
        sb.append("// Generated by the GATF SPA crawler — a starter walkthrough; review selectors and add assertions.\n");
        if (loginSel != null) {
            sb.append("// Credentials are placeholders: define username/password in a properties file and load it,\n");
            sb.append("// e.g.  config creds.properties   (never commit real credentials).\n");
        }
        sb.append("open chrome\nmaximize\n\n");
        if (loginSel != null) {
            Map<String, Object> u = map(loginSel.get("user")), p = map(loginSel.get("pass")), s = map(loginSel.get("submit"));
            sb.append("// login\ngoto ").append(spec.getLoginUrl()).append("\n");
            if (!u.isEmpty()) sb.append("type \"${username}\" ").append(loc(loginSel.get("userCand"), u)).append("\n");
            sb.append("type \"${password}\" ").append(loc(loginSel.get("passCand"), p)).append("\n");
            if (!s.isEmpty()) sb.append("click ").append(loc(loginSel.get("submitCand"), s)).append("\n");
            sb.append("sleep 2000\n\n");
        }
        int i = 0;
        for (PageInfo pi : ps) {
            i++;
            if (pi.error != null) continue;
            sb.append("// page ").append(i).append(" (depth ").append(pi.depth).append("): ").append(pi.url);
            if (pi.title != null && !pi.title.isEmpty()) sb.append(" — ").append(pi.title.replace('\n', ' '));
            sb.append("\ngoto ").append(pi.url).append("\nsleep 1000\n");
            int fi = 0;
            for (Map<String, Object> f : pi.forms) {
                List<Map<String, Object>> fields = list(f.get("fields"));
                if (fields.isEmpty()) continue;
                fi++;
                String method = str(f.get("method"));
                sb.append("// form ").append(fi).append(" (").append(method).append(", ").append(fields.size()).append(" fields)\n");
                for (Map<String, Object> fl : fields) sb.append(fillLine(fl)).append("\n");
                Map<String, Object> sub = map(f.get("submit"));
                if (!sub.isEmpty()) {
                    if (spec.isSubmitForms() || "get".equals(method)) {
                        sb.append("click ").append(loc(f.get("submitCand"), sub)).append("\nsleep 1000\n");
                    } else {
                        sb.append("// click ").append(loc(f.get("submitCand"), sub)).append("   (POST form: not submitted — enable submitForms)\n");
                    }
                }
            }
            if (!pi.loose.isEmpty()) {
                sb.append("// inputs outside a <form> (SPA)\n");
                for (Map<String, Object> fl : pi.loose) sb.append(fillLine(fl)).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    static String fillLine(Map<String, Object> fl) {
        String tag = str(fl.get("tag")), type = str(fl.get("type"));
        String s = loc(fl.get("cand"), map(fl.get("sel")));
        if ("select".equals(tag)) {
            int n = fl.get("options") instanceof Number ? ((Number) fl.get("options")).intValue() : 0;
            return "select index@" + (n > 1 ? 1 : 0) + " " + s;
        }
        if ("checkbox".equals(type) || "radio".equals(type)) return "click " + s;
        String v = "textarea".equals(tag) ? "sample text" : PortalCrawler.sampleByType(type);
        return "type \"" + v + "\" " + s;
    }

    /** M3: the emitted locator — a unique semantic candidate (testid → role → label) else the legacy selector. */
    static String loc(Object candidates, Map<String, Object> legacy) {
        return com.gatf.selenium.LocatorRanking.choose(candidates instanceof List ? (List<?>) candidates : null, sel(legacy));
    }

    /** {s,v} → seleasy selector (id@x / name@x / css@"…" / xpath@"…"). */
    static String sel(Map<String, Object> m) {
        String s = str(m.get("s")), v = str(m.get("v"));
        if ("id".equals(s)) return "id@" + simpleOrQuoted(v);
        if ("name".equals(s)) return "name@" + simpleOrQuoted(v);
        if ("css".equals(s)) return "css@" + quote(v);
        return "xpath@" + quote(v);
    }

    private static String simpleOrQuoted(String v) {
        return v.matches("[A-Za-z0-9_\\-:.]+") ? v : quote(v);
    }

    private static String quote(String v) {
        if (v.indexOf('"') >= 0 && v.indexOf('\'') < 0) return "'" + v + "'";
        return "\"" + v.replace("\"", "\\\"") + "\"";
    }

    // "id@user" / "css@form .x" / "xpath@//a" → {s,v} for the page scripts; null when blank
    static Map<String, Object> selArg(String seleasy) {
        if (seleasy == null || seleasy.trim().isEmpty()) return null;
        String t = seleasy.trim();
        int at = t.indexOf('@');
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        String s = at > 0 ? t.substring(0, at).toLowerCase(Locale.ROOT) : "css";
        String v = at > 0 ? t.substring(at + 1) : t;
        if (v.length() >= 2 && (v.charAt(0) == '"' || v.charAt(0) == '\'') && v.charAt(v.length() - 1) == v.charAt(0)) v = v.substring(1, v.length() - 1);
        m.put("s", s.equals("class") ? "css" : s);
        m.put("v", s.equals("class") ? "." + v : v);
        return m;
    }

    // ------------------------------------------------------------------ coverage plan

    /** Pages ranked by how much there is to test: forms, fields, required fields, actions. */
    public static List<Map<String, Object>> coveragePlan(List<PageInfo> ps) {
        List<Map<String, Object>> plan = new ArrayList<Map<String, Object>>();
        for (PageInfo pi : ps) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            int forms = 0;
            for (Map<String, Object> f : pi.forms) if (!list(f.get("fields")).isEmpty()) forms++;
            int fields = pi.fieldCount(), req = pi.requiredCount(), btn = pi.buttons.size();
            int score = forms * 5 + fields * 2 + req * 2 + btn + (pi.depth == 0 ? 1 : 0);
            m.put("url", pi.url);
            m.put("title", pi.title);
            m.put("depth", pi.depth);
            m.put("forms", forms);
            m.put("fields", fields);
            m.put("required", req);
            m.put("buttons", btn);
            m.put("links", pi.linkCount);
            m.put("score", pi.error != null ? -1 : score);
            m.put("priority", pi.error != null ? "error" : score >= 15 ? "high" : score >= 6 ? "medium" : "low");
            List<String> why = new ArrayList<String>();
            if (pi.error != null) why.add("load failed: " + pi.error);
            if (forms > 0) why.add(forms + " form(s)");
            if (fields > 0) why.add(fields + " field(s)" + (req > 0 ? " (" + req + " required — validation paths)" : ""));
            if (btn > 0) why.add(btn + " action button(s)");
            if (why.isEmpty()) why.add("navigation / content only");
            m.put("reasons", String.join(", ", why));
            plan.add(m);
        }
        plan.sort((a, b) -> Integer.compare((Integer) b.get("score"), (Integer) a.get("score")));
        return plan;
    }

    // ------------------------------------------------------------------ helpers

    /** Normalize for de-dup: drop plain #anchors (keep #/ and #!/ hash routes), trailing slash. */
    static String norm(String url) {
        if (url == null) return null;
        try {
            String u = url.trim();
            int h = u.indexOf('#');
            if (h >= 0) {
                String frag = u.substring(h + 1);
                if (!(frag.startsWith("/") || frag.startsWith("!/"))) u = u.substring(0, h);
            }
            URI uri = URI.create(u.replace(" ", "%20"));
            if (uri.getScheme() == null || !(uri.getScheme().startsWith("http"))) return null;
            if (u.endsWith("/") && uri.getRawPath() != null && uri.getRawPath().length() > 1 && uri.getRawFragment() == null) {
                u = u.substring(0, u.length() - 1);
            }
            return u;
        } catch (Exception e) {
            return null;
        }
    }

    static String stripFragment(String u) {
        int h = u == null ? -1 : u.indexOf('#');
        String b = h >= 0 ? u.substring(0, h) : u;
        return b != null && b.endsWith("/") ? b.substring(0, b.length() - 1) : b;
    }

    // a fragment that is an in-page anchor, not a hash route (#/x, #!/x)
    static boolean isPlainAnchor(String u) {
        int h = u == null ? -1 : u.indexOf('#');
        if (h < 0) return false;
        String f = u.substring(h + 1);
        return !(f.startsWith("/") || f.startsWith("!/"));
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> map(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : new LinkedHashMap<String, Object>();
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> list(Object o) {
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        if (o instanceof List) for (Object x : (List<Object>) o) if (x instanceof Map) out.add((Map<String, Object>) x);
        return out;
    }

    private static Object orEmpty(Object o) {
        return o instanceof List ? o : Collections.emptyList();
    }

    static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}
