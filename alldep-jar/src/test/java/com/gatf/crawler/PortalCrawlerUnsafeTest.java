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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.gatf.record.PickLocator;

/** Δ163a: the portal crawler writes crawled text into the generated script only through the Δ148c gates (as SpaCrawler). */
class PortalCrawlerUnsafeTest {

    private static final String SECRET = "s3cr3t\"pa$$";

    private static CrawlSpec spec(boolean login) {
        CrawlSpec s = new CrawlSpec();
        s.setSeed("https://shop.example/");
        if (login) {
            s.setLoginUrl("https://shop.example/login");
            s.setUsername("alice");
            s.setPassword(SECRET);
            s.setUserSel("id@user");
            s.setPassSel("id@pass");
            s.setSubmitSel("id@go");
        }
        return s;
    }

    private static PortalCrawler.Page page(String url, String title) {
        PortalCrawler.Page p = new PortalCrawler.Page();
        p.url = url;
        p.title = title;
        p.status = 200;
        return p;
    }

    private static PortalCrawler.El el(String tag, String type, String id, String name, String cls) {
        PortalCrawler.El e = new PortalCrawler.El();
        e.tag = tag;
        e.type = type;
        e.id = id;
        e.name = name;
        e.cls = cls;
        e.idx = 3;
        return e;
    }

    private static String emit(CrawlSpec spec, List<PortalCrawler.Page> pages) {
        return new PortalCrawler(spec).emitSeleasy(pages, new int[3]);
    }

    @Test
    void theLoginNeverCarriesTheConfiguredPassword() {
        String sel = emit(spec(true), new ArrayList<PortalCrawler.Page>());
        assertFalse(sel.contains(SECRET), sel);
        assertFalse(sel.contains("s3cr3t"), sel);
        assertFalse(sel.contains("alice"), sel);
        assertTrue(sel.contains("type \"${username}\" id@user"), sel);
        assertTrue(sel.contains("type \"${password}\" id@pass"), sel);
        assertTrue(sel.contains("goto https://shop.example/login"), sel);
    }

    @Test
    void unsafeCrawledUrlsSkipTheWholePageBlock() {
        List<PortalCrawler.Page> pages = new ArrayList<PortalCrawler.Page>();
        PortalCrawler.Page bad = page("https://shop.example/a\"b", "A");
        PortalCrawler.Form f = new PortalCrawler.Form();
        f.inputs.add(el("input", "text", "q", "", ""));
        bad.forms.add(f);
        pages.add(bad);
        pages.add(page("javascript:alert(1)", "B"));
        pages.add(page("https://shop.example/$%7Bx%7D/$p{evil}", "C"));
        pages.add(page("https://shop.example/ok", "Fine"));
        String sel = emit(spec(false), pages);
        assertFalse(sel.contains("a\"b"), sel);
        assertFalse(sel.contains("javascript:"), sel);
        assertFalse(sel.contains("$p{"), sel);
        assertFalse(sel.contains("id@q"), sel);   // the skipped page's form is not emitted on the previous page
        assertTrue(sel.contains("goto https://shop.example/ok"), sel);
        int skips = sel.split(java.util.regex.Pattern.quote(PickLocator.skipped("crawl", "goto")), -1).length - 1;
        assertEquals(3, skips, sel);
    }

    @Test
    void aTitleCannotStartAStatementOrEscapeTheComment() {
        String bs = "\\";
        List<PortalCrawler.Page> pages = new ArrayList<PortalCrawler.Page>();
        pages.add(page("https://shop.example/t", "Shop\nclick id@evil\r x */ y " + bs + "u000a"));
        String sel = emit(spec(false), pages);
        for (String line : sel.split("\n")) {
            assertFalse(line.trim().startsWith("click id@evil"), sel);
        }
        assertFalse(sel.contains("*/"), sel);
        assertFalse(sel.contains("\r") || sel.contains(" "), sel);
        // the backslash-u text stays in the comment; Command doubles it when it writes the Java comment (CommandTest)
        assertTrue(sel.contains("// page 1: https://shop.example/t — Shop click id@evil"), sel);
    }

    @Test
    void pageAttributesReachLocatorsOnlyAsPlainTokens() {
        assertEquals("//*[@id='user-1']", PortalCrawler.xpathFor(el("input", "text", "user-1", "", "")));
        assertEquals("//input[@name='q']", PortalCrawler.xpathFor(el("input", "text", "x'y", "q", "")));
        assertEquals("//input[contains(@class,'big')]", PortalCrawler.xpathFor(el("input", "text", "$x", "a\"b", "big red")));
        assertEquals("(//input)[3]", PortalCrawler.xpathFor(el("input", "text", "#set", "]", "c'd")));
        assertEquals("(//select)[3]", PortalCrawler.xpathFor(el("select", "", "a b", "", "")));
        assertEquals("xpath@\"(//input)[3]\"", PortalCrawler.selectorFor(el("input", "text", "${x}", "n\"", "")));
        assertEquals("id@ok", PortalCrawler.selectorFor(el("input", "text", "ok", "", "")));
        List<String> out = new ArrayList<String>();
        PortalCrawler.Form f = new PortalCrawler.Form();
        f.inputs.add(el("input", "text", "a'];x", "", ""));
        PortalCrawler.fillLinesForForm(f, false, out);
        assertEquals("type \"sample\" xpath@\"(//input)[3]\"", out.get(0));
    }
}
