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
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

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
}
