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
package com.gatf.coverage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import com.gatf.crawler.SpaCrawler;

/** H6 (Δ155): URL scrubbing for scripts and route keying through the crawler's own normaliser. */
class CoverageUrlsTest {

    @Test
    void scriptKeepsSchemeHostPathOnly() {
        assertEquals("https://app.test/js/main.js", CoverageUrls.script("https://app.test/js/main.js?token=abc&v=2#x"));
        assertEquals("https://app.test/js/main.js", CoverageUrls.script("https://bob:pw@app.test/js/main.js"));
        assertEquals("http://app.test:8080/a.js", CoverageUrls.script(" http://u@app.test:8080/a.js?sig=1 "));
        assertEquals("file:///work/dist/a.js", CoverageUrls.script("file:///work/dist/a.js?x"));
        assertEquals("https://app.test/p@th/a.js", CoverageUrls.script("https://app.test/p@th/a.js"));   // @ in the path is not userinfo
    }

    @Test
    void scriptWithoutStableIdentityIsDropped() {
        assertNull(CoverageUrls.script(null));
        assertNull(CoverageUrls.script(""));
        assertNull(CoverageUrls.script("data:text/javascript,alert('secret')"));
        assertNull(CoverageUrls.script("blob:https://app.test/1234"));
        assertNull(CoverageUrls.script("chrome-extension://abc/x.js"));
        assertNull(CoverageUrls.script("debugger://VM12"));
    }

    @Test
    void routeUsesTheCrawlersNormaliser() {
        for (String u : new String[] {"http://a.test/x/#section", "http://a.test/#/orders", "http://a.test/#!/orders",
                "http://a.test/x/", "http://a.test/", "https://a.test/deep/page"}) {
            assertEquals(SpaCrawler.norm(u), CoverageUrls.route(u), u);
        }
        assertNull(CoverageUrls.route("mailto:x@y"));
        assertNull(CoverageUrls.route("javascript:void(0)"));
        assertNull(CoverageUrls.route(null));
    }

    @Test
    void routeDropsQueriesAndUserinfoOnBothSidesTheSameWay() {
        assertEquals("http://a.test/x", CoverageUrls.route("http://a.test/x/?token=abc"));
        assertEquals("http://a.test/x", CoverageUrls.route("http://a.test/x?token=abc#top"));
        assertEquals("http://a.test/#/reset", CoverageUrls.route("http://a.test/?s=1#/reset?token=abc"));
        assertEquals("http://a.test/x", CoverageUrls.route("http://user:pw@a.test/x"));
        assertEquals(CoverageUrls.route("http://a.test/orders"), CoverageUrls.route("http://a.test/orders/?page=2"));
    }
}
