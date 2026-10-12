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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.junit.jupiter.api.Test;

import com.gatf.record.PickLocator;

/**
 * Δ148b: a crawled page is untrusted, so the crawler's id/name/css/xpath fallback goes through the same
 * {@link com.gatf.selenium.LocatorRanking#unsafe} rule as the recorder: an unsafe fallback is dropped, ranking falls
 * through to a safe semantic candidate, and with none left the step becomes the fixed skip comment.
 */
class SpaCrawlerUnsafeTest {

    private static Map<String, Object> m(Object... kv) {
        Map<String, Object> x = new LinkedHashMap<String, Object>();
        for (int i = 0; i < kv.length; i += 2) x.put((String) kv[i], kv[i + 1]);
        return x;
    }

    @Test
    void unsafeFallbackIsDropped() {
        for (String v : new String[] { "ctl00$main", "a#set(", "x\ry", "a\u2028b" }) {
            for (String s : new String[] { "id", "name", "css", "xpath" }) assertNull(SpaCrawler.sel(m("s", s, "v", v)), s + " " + v);
        }
        assertEquals("id@go", SpaCrawler.sel(m("s", "id", "v", "go")));
        assertEquals("xpath@\"//form/input[1]\"", SpaCrawler.sel(m("s", "xpath", "v", "//form/input[1]")));
    }

    @Test
    void fillLineFallsThroughOrSkips() {
        Map<String, Object> f = m("sel", m("s", "name", "v", "ctl00$Main$email"), "tag", "input", "type", "email", "cand",
                Arrays.asList(m("s", "label", "v", "Work email", "n", 1)));
        assertEquals("type \"user@example.com\" label@'Work email'", SpaCrawler.fillLine(f));
        f.put("cand", Arrays.asList(m("s", "label", "v", "Work email", "n", 2)));
        assertEquals(PickLocator.skipped("crawl", "type"), SpaCrawler.fillLine(f));
        Map<String, Object> sel = m("sel", m("s", "id", "v", "a$b"), "tag", "select", "type", "", "options", 3);
        assertEquals(PickLocator.skipped("crawl", "select"), SpaCrawler.fillLine(sel));
        Map<String, Object> cb = m("sel", m("s", "id", "v", "a$b"), "tag", "input", "type", "checkbox");
        assertEquals(PickLocator.skipped("crawl", "click"), SpaCrawler.fillLine(cb));
        // safe fallbacks are unchanged
        Map<String, Object> ok = m("sel", m("s", "xpath", "v", "//form/input[1]"), "tag", "input", "type", "email");
        assertEquals("type \"user@example.com\" xpath@\"//form/input[1]\"", SpaCrawler.fillLine(ok));
    }

    // ---- Δ148c: crawled goto URLs ------------------------------------------------------------------------------

    private static SpaCrawler.PageInfo page(String url, String title) {
        SpaCrawler.PageInfo p = new SpaCrawler.PageInfo();
        p.url = url;
        p.title = title;
        return p;
    }

    private static String emit(SpaCrawler.PageInfo... ps) {
        CrawlSpec spec = new CrawlSpec();
        spec.setSeed("http://app.test/");
        return new SpaCrawler(spec, null, x -> { }).emitSeleasy(java.util.Arrays.asList(ps));
    }

    @Test
    void crawledGotoUrlsAreWrappedOrSkipped() throws Exception {
        String[][] cases = {
            { "https://evil.test/?p=$password", "goto #[[https://evil.test/?p=$password]]#" },
            { "https://evil.test/${x}", "goto #[[https://evil.test/${x}]]#" },
            { "https://evil.test/$!x", "goto #[[https://evil.test/$!x]]#" },
            { "https://evil.test/#set($a=1)$a", "goto #[[https://evil.test/#set($a=1)$a]]#" },
            { "https://evil.test/a\"+b", null },             // a quote (the raw getAttribute path): GotoCommand's Java literal
            { "javascript:alert(1)", null },
            { "https://evil.test/$v{x}", null },
            { "https://evil.test/$p{prov.0.x}", null },
            { "https://evil.test/a]]#$x", null },
            { "https://evil.test/a\rexecjs x", null },
            { "https://evil.test/a b", null },
            { "http://app.test/orders", "goto http://app.test/orders" },   // ordinary URLs unchanged
        };
        for (String[] c : cases) {
            String out = emit(page(c[0], "T"));
            String want = c[1] != null ? c[1] : PickLocator.skipped("crawl", "goto");
            assertTrue(out.contains("\n" + want + "\nsleep 1000\n"), c[0] + " ->\n" + out);
            if (c[1] == null) assertFalse(out.contains("\ngoto "), c[0]);   // no goto statement at all
            for (String line : out.split("\n", -1)) {
                assertFalse(line.matches("(?s).*[\\r\\u2028\\u2029\\u0085].*"), c[0] + ": " + line);
                if (!line.startsWith("//")) assertFalse(line.replaceAll("#\\[\\[.*?\\]\\]#", "").contains("$")
                        && !line.startsWith("type \"${"), c[0] + ": " + line);
            }
        }
    }

    @Test
    void pageCommentCarriesNoUnsafeUrlOrLineBreak() {
        String out = emit(page("https://evil.test/a\rexecjs evil", "Shop\r\nexecjs x\u2028y\u0085z"));
        assertTrue(out.contains("// page 1 (depth 0): (URL not shown: unsafe) — Shop  execjs x y z\n"), out);
        assertFalse(out.contains("evil.test"), out);
        List<String> statements = new ArrayList<String>();
        for (String l : out.split("\n")) if (!l.isEmpty() && !l.startsWith("//")) statements.add(l);
        assertEquals(java.util.Arrays.asList("open chrome", "maximize", "sleep 1000"), statements);
    }

    @Test
    void aWrappedCrawledUrlComesOutOfVelocityLiterally() throws Exception {
        // GotoCommand: navigateTo(evaluate("<url with backslashes doubled>")); evaluate templates any value with '$'
        VelocityEngine engine = new VelocityEngine();
        engine.init();
        for (String u : new String[] { "https://evil.test/?p=$password", "https://evil.test/#set($a=1)$a", "https://evil.test/${x}" }) {
            String g = PickLocator.gotoUrl(u);
            Map<String, Object> vars = new HashMap<String, Object>();
            vars.put("password", "SECRET");
            vars.put("x", "SECRET");
            VelocityContext ctx = new VelocityContext(vars);
            StringWriter w = new StringWriter();
            engine.evaluate(ctx, w, "ERROR", g);
            assertEquals(u, w.toString(), u);
            assertFalse(w.toString().contains("SECRET"), u);
            assertEquals(2, ctx.getKeys().length, u + ": a directive ran");
        }
    }
}
