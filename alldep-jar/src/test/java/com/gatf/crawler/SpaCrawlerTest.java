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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** G3 — SPA crawl logic against a fake browser that serves pre-rendered scan results per URL. */
class SpaCrawlerTest {

    static Map<String, Object> sel(String s, String v) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("s", s);
        m.put("v", v);
        return m;
    }

    static Map<String, Object> field(Map<String, Object> sel, String tag, String type, boolean req) {
        Map<String, Object> f = new LinkedHashMap<String, Object>();
        f.put("sel", sel);
        f.put("tag", tag);
        f.put("type", type);
        f.put("required", req);
        f.put("options", "select".equals(tag) ? 3 : 0);
        return f;
    }

    static Map<String, Object> page(String url, List<String> links, List<Map<String, Object>> forms, List<Map<String, Object>> loose) {
        Map<String, Object> p = new LinkedHashMap<String, Object>();
        p.put("url", url);
        p.put("title", "T " + url);
        p.put("links", links);
        p.put("forms", forms);
        p.put("loose", loose);
        p.put("buttons", new ArrayList<Object>());
        return p;
    }

    static class Fake implements SpaCrawler.Browser {
        final Map<String, Map<String, Object>> site = new HashMap<String, Map<String, Object>>();
        final List<String> visited = new ArrayList<String>();
        final List<Object[]> sets = new ArrayList<Object[]>();
        String cur = "";

        public void get(String url) { cur = url; visited.add(url); }
        public String currentUrl() { return cur; }
        public void quit() { }
        public Object js(String script, Object... args) {
            if (script == SpaCrawler.SETTLE_JS) return Arrays.asList("complete", 42L);
            if (script == SpaCrawler.SCAN_JS) return site.get(cur);
            if (script == SpaCrawler.LOGIN_FIND_JS) {
                if (!cur.endsWith("/login")) return null;
                Map<String, Object> m = new LinkedHashMap<String, Object>();
                m.put("user", sel("id", "email"));
                m.put("pass", sel("name", "pwd"));
                m.put("submit", sel("css", "button.login"));
                return m;
            }
            if (script == SpaCrawler.SET_JS) { sets.add(args); cur = "http://app.test/#/home"; return true; }
            return null;
        }
    }

    @Test
    void crawlsRenderedSpaSafelyAndEmitsSuite() throws Exception {
        Fake b = new Fake();
        String home = "http://app.test/#/home", orders = "http://app.test/#/orders", edit = "http://app.test/#/orders/edit",
                deep = "http://app.test/#/orders/edit/deep";
        b.site.put(home, page(home, Arrays.asList(orders, "http://app.test/#top", "http://app.test/logout", "https://other.test/x"),
                new ArrayList<Map<String, Object>>(), Arrays.asList(field(sel("id", "q"), "input", "search", false))));
        List<Map<String, Object>> fs = new ArrayList<Map<String, Object>>();
        Map<String, Object> form = new LinkedHashMap<String, Object>();
        form.put("method", "post");
        form.put("fields", Arrays.asList(field(sel("name", "email"), "input", "email", true),
                field(sel("id", "qty"), "select", "", true), field(sel("xpath", "//form/input[3]"), "input", "checkbox", false)));
        form.put("submit", sel("css", "button.save"));
        fs.add(form);
        b.site.put(orders, page(orders, Arrays.asList(edit), fs, new ArrayList<Map<String, Object>>()));
        b.site.put(edit, page(edit, Arrays.asList(deep), new ArrayList<Map<String, Object>>(), new ArrayList<Map<String, Object>>()));
        b.site.put(deep, page(deep, new ArrayList<String>(), new ArrayList<Map<String, Object>>(), new ArrayList<Map<String, Object>>()));

        CrawlSpec spec = new CrawlSpec();
        spec.setSeed(home);
        spec.setMaxPages(10);
        spec.setMaxDepth(2);
        spec.setPollMs(1);
        spec.setLoginUrl("http://app.test/login");
        spec.setUsername("u@x.test");
        spec.setPassword("s3cret");
        List<String> log = new ArrayList<String>();
        SpaCrawler c = new SpaCrawler(spec, b, log::add);
        List<SpaCrawler.PageInfo> pages = c.crawl();

        // login first, then BFS home → orders → edit; depth 2 stops before deep; #top anchor, logout, other origin skipped
        assertThat(b.visited).containsExactly("http://app.test/login", home, orders, edit);
        assertThat(String.join("\n", log)).contains("skip (excluded): http://app.test/logout");
        assertThat(b.sets.get(1)[2]).isEqualTo("s3cret");                     // typed into the browser only

        String sel = c.emitSeleasy(pages);
        assertThat(sel).contains("type \"${username}\" id@email").contains("type \"${password}\" name@pwd")
                .contains("click css@\"button.login\"").contains("goto " + orders)
                .contains("type \"user@example.com\" name@email").contains("select index@1 id@qty")
                .contains("click xpath@\"//form/input[3]\"")
                .contains("// click css@\"button.save\"   (POST form: not submitted")      // POST not submitted by default
                .contains("type \"sample\" id@q");                                         // SPA input outside <form>
        assertThat(sel.contains("s3cret")).isFalse();                                       // never written

        List<Map<String, Object>> plan = SpaCrawler.coveragePlan(pages);
        assertThat(plan.get(0).get("url")).isEqualTo(orders);                               // richest page first
        assertThat(plan.get(0).get("priority")).isEqualTo("high");
    }

    @Test
    void normalizationKeepsHashRoutesDropsAnchors() {
        assertThat(SpaCrawler.norm("http://a.test/x/#section")).isEqualTo("http://a.test/x");
        assertThat(SpaCrawler.norm("http://a.test/#/orders")).isEqualTo("http://a.test/#/orders");
        assertThat(SpaCrawler.norm("http://a.test/#!/orders")).isEqualTo("http://a.test/#!/orders");
        assertThat(SpaCrawler.norm("mailto:x@y")).isNull();
        assertThat(SpaCrawler.selArg("id@user").get("v")).isEqualTo("user");
        assertThat(SpaCrawler.selArg("xpath@\"//a[1]\"").get("v")).isEqualTo("//a[1]");
    }

    @Test
    void emittedFieldsPreferUniqueSemanticLocators() {
        Map<String, Object> f = field(sel("xpath", "//form/input[1]"), "input", "email", true);
        Map<String, Object> lab = new LinkedHashMap<String, Object>();
        lab.put("s", "label"); lab.put("v", "Work email"); lab.put("n", 1);
        f.put("cand", Arrays.asList(lab));
        assertThat(SpaCrawler.fillLine(f)).isEqualTo("type \"user@example.com\" label@'Work email'");
        lab.put("n", 2);
        assertThat(SpaCrawler.fillLine(f)).isEqualTo("type \"user@example.com\" xpath@\"//form/input[1]\"");
    }
}
