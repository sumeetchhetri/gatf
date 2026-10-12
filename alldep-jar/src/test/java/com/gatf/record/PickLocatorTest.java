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
package com.gatf.record;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatf.selenium.SemanticLocators;

/**
 * Δ148 pick locator: {@code POST /record?action=rank} as the handler runs it ({@link PickLocator#rankRequest}, with the
 * handler's JSON reader), the ranking order and strictness, the caps, malformed bodies, and the shared escape vectors
 * (src/test/resources/pick-locator/escape-vectors.json, also run through the editor tokenizer by the frontend's
 * pickLocator.test.ts): each one ranks to exactly the pinned locator / line, and every semantic one parses back to the
 * same value through {@link SemanticLocators#parse}.
 */
class PickLocatorTest {

    private static final ObjectMapper OM = new ObjectMapper();
    private static final PickLocator.Json JSON = b -> OM.readValue(b, Object.class);

    private static PickLocator.Reply post(String body) {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        return PickLocator.rankRequest(new ByteArrayInputStream(b), b.length, JSON);
    }

    private static Map<String, Object> cand(String s, String v, int n) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("s", s);
        if (s.equals("role")) { String[] rn = v.split("\\|", 2); m.put("role", rn[0]); m.put("name", rn[1]); } else m.put("v", v);
        m.put("n", n);
        return m;
    }

    private static Map<String, Object> sel(String s, String v, int n) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("s", s);
        m.put("v", v);
        m.put("n", n);
        return m;
    }

    @Test
    void rankingOrderIsTestIdRoleLabelThenStrictFallback() throws Exception {
        List<Object> all = Arrays.asList(cand("label", "Email", 1), cand("role", "textbox|Email", 1), cand("testid", "email", 1));
        Map<String, Object> r = PickLocator.rank(all, sel("id", "email", 1));
        assertEquals("testid@email", r.get("locator"));
        assertEquals("click testid@email", r.get("line"));
        assertEquals(2, r.get("source"));                       // index into the candidates as sent
        // an ambiguous testid falls to role, then label, then the fallback
        r = PickLocator.rank(Arrays.asList(cand("testid", "email", 2), cand("role", "textbox|Email", 1), cand("label", "Email", 1)), null);
        assertEquals("role@textbox[name=Email]", r.get("locator"));
        assertEquals(1, r.get("source"));
        r = PickLocator.rank(Arrays.asList(cand("testid", "email", 2), cand("role", "textbox|Email", 3), cand("label", "Email", 1)), null);
        assertEquals("label@Email", r.get("locator"));
        r = PickLocator.rank(Arrays.asList(cand("testid", "email", 2), cand("label", "Email", 0)), sel("css", "form > input", 1));
        assertEquals("css@\"form > input\"", r.get("locator"));
        assertEquals(-1, r.get("source"));
        assertEquals(Boolean.TRUE, r.get("strict"));
        // nothing strict: no locator, no line; the candidates still carry their counts for display
        r = PickLocator.rank(Arrays.asList(cand("testid", "email", 2), cand("label", "Email", 0)), sel("css", "input", 4));
        assertNull(r.get("locator"));
        assertNull(r.get("line"));
        assertNull(r.get("source"));
        assertEquals(Boolean.FALSE, r.get("strict"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> c = (List<Map<String, Object>>) r.get("candidates");
        assertEquals("testid@email", c.get(0).get("locator"));
        assertEquals(2, c.get(0).get("n"));
        @SuppressWarnings("unchecked")
        Map<String, Object> fb = (Map<String, Object>) r.get("fallback");
        assertEquals("css@\"input\"", fb.get("locator"));
        assertEquals(4, fb.get("n"));
    }

    @Test
    void rankRequestNeedsNoSessionAndReturnsThePasteLine() {
        PickLocator.Reply r = post("{\"cand\":[{\"s\":\"role\",\"role\":\"button\",\"name\":\"Save draft\",\"n\":1}],\"sel\":null}");
        assertEquals(200, r.status);
        assertEquals("role@button[name='Save draft']", r.body.get("locator"));
        assertEquals("click role@button[name='Save draft']", r.body.get("line"));
        assertEquals(200, post("{}").status);                     // nothing to rank is not an error
        assertNull(post("{}").body.get("locator"));
        assertEquals(200, post("{\"cand\":[],\"sel\":{\"s\":\"id\",\"v\":\"go\",\"n\":1}}").status);
    }

    @Test
    void capsAreA413NeverA500() {
        StringBuilder many = new StringBuilder("{\"cand\":[");
        for (int i = 0; i <= PickLocator.MAX_CANDIDATES; i++) many.append(i == 0 ? "" : ",").append("{\"s\":\"testid\",\"v\":\"t").append(i).append("\",\"n\":1}");
        assertEquals(413, post(many.append("]}").toString()).status);
        StringBuilder exact = new StringBuilder("{\"cand\":[");
        for (int i = 0; i < PickLocator.MAX_CANDIDATES; i++) exact.append(i == 0 ? "" : ",").append("{\"s\":\"testid\",\"v\":\"t").append(i).append("\",\"n\":2}");
        assertEquals(200, post(exact.append("]}").toString()).status);
        char[] pad = new char[PickLocator.MAX_BODY];
        Arrays.fill(pad, ' ');
        String big = "{\"cand\":[]" + new String(pad) + "}";
        assertEquals(413, post(big).status);                                                  // as read
        byte[] small = "{}".getBytes(StandardCharsets.UTF_8);
        assertEquals(413, PickLocator.rankRequest(new ByteArrayInputStream(small), PickLocator.MAX_BODY + 1L, JSON).status);   // by Content-Length
    }

    @Test
    void malformedBodiesAreA400() {
        for (String b : new String[] { "", "not json", "{\"cand\":", "[]", "\"x\"", "42", "null", "{\"cand\":{}}", "{\"cand\":[1]}",
                "{\"cand\":[\"x\"]}", "{\"sel\":\"id@x\"}", "{\"sel\":[1]}" }) {
            PickLocator.Reply r = post(b);
            assertEquals(400, r.status, "body " + b);
            assertNotNull(r.body.get("error"), "body " + b);
        }
        StringBuilder deep = new StringBuilder();
        for (int i = 0; i < 30000; i++) deep.append('[');
        assertEquals(400, post(deep.toString()).status);
        assertEquals(400, PickLocator.rankRequest(null, -1, JSON).status);                   // no body at all
        // odd field types inside a candidate are not an error: that candidate is just not expressible
        PickLocator.Reply r = post("{\"cand\":[{\"s\":\"role\",\"role\":7,\"name\":[1],\"n\":\"1\"}]}");
        assertEquals(200, r.status);
        assertNull(r.body.get("locator"));
    }

    @Test
    void sharedEscapeVectorsRankExactlyAsPinnedAndParseBack() throws Exception {
        List<Map<String, Object>> vectors = vectors();
        assertTrue(vectors.size() >= 20);
        for (Map<String, Object> v : vectors) {
            String id = String.valueOf(v.get("id"));
            Map<String, Object> r = PickLocator.rank((List<?>) v.get("cand"), (Map<?, ?>) v.get("sel"));
            assertEquals(v.get("locator"), r.get("locator"), id);
            assertEquals(v.get("line"), r.get("line"), id);
            String loc = (String) r.get("locator");
            if (loc == null) continue;
            assertFalse(loc.contains("\n") || loc.contains("\r"), id);
            String by = loc.substring(0, loc.indexOf('@')), cls = loc.substring(loc.indexOf('@') + 1);
            if (!SemanticLocators.PREFIXES.contains(by)) continue;
            Map<String, Object> spec = SemanticLocators.parse(by, cls);
            Map<?, ?> c = (Map<?, ?>) ((List<?>) v.get("cand")).get((Integer) r.get("source"));
            String want = by.equals("role") ? String.valueOf(c.get("name")) : String.valueOf(c.get("v"));
            assertEquals(want, spec.get("value"), id + ": parses back to the picked value");
            if (by.equals("role")) assertEquals(c.get("role"), spec.get("role"), id);
        }
    }

    // ---- Δ148a ------------------------------------------------------------------------------------------------

    @Test
    void velocityAndControlCharactersAreNeverExpressible() {
        // the orchestrator's repro: a forged strict css fallback with '$' used to come back as `click css@"a$x"`
        PickLocator.Reply r = post("{\"sel\":{\"s\":\"css\",\"v\":\"a$x\",\"n\":1}}");
        assertEquals(200, r.status);
        assertNull(r.body.get("locator"));
        assertNull(r.body.get("line"));
        assertEquals(Boolean.FALSE, r.body.get("strict"));
        for (String v : new String[] { "$x", "${x}", "$!x", "a$", "#set(", "x#if(y)", "#foreach", "a\rb", "a\tb", "a\u0000b", "a\u007fb",
                "a\u0085b", "a\u2028b", "a\u2029b", "a\nb" }) {
            assertTrue(com.gatf.selenium.LocatorRanking.unsafe(v), v);
            for (String k : new String[] { "testid", "label" }) assertNull(com.gatf.selenium.LocatorRanking.render(cand(k, v, 1)), k + " " + v);
            assertNull(com.gatf.selenium.LocatorRanking.render(cand("role", "button|" + v, 1)), "role " + v);
            for (String k : new String[] { "id", "name", "css", "xpath" }) assertNull(PickLocator.selector(sel(k, v, 1)), k + " " + v);
        }
        for (String v : new String[] { "Item #5", "a # b", "50%", "C:\\x", "Größe" }) assertFalse(com.gatf.selenium.LocatorRanking.unsafe(v), v);
        // a semantic candidate that is unsafe falls through to a safe one
        Map<String, Object> rk = PickLocator.rank(Arrays.asList(cand("testid", "$x", 1), cand("role", "button|Save", 1)), sel("id", "a$b", 1));
        assertEquals("role@button[name=Save]", rk.get("locator"));
    }

    @Test
    void theRecorderNeverWritesAnUnsafeStep() {
        RecordSession s = new RecordSession("t", null);
        Map<String, Object> a = new LinkedHashMap<String, Object>();
        a.put("type", "click");
        a.put("sel", sel("name", "ctl00$main$go", 1));
        s.ingest(a);
        Map<String, Object> b = new LinkedHashMap<String, Object>();
        b.put("type", "evil\r#set($x=1)");
        b.put("sel", sel("css", "a$x", 1));
        s.ingest(b);
        Map<String, Object> c = new LinkedHashMap<String, Object>();
        c.put("type", "click");
        c.put("sel", sel("id", "ok", 1));
        c.put("cand", Arrays.asList(cand("label", "#if(x)", 1)));
        s.ingest(c);
        List<String> l = s.currentLines();
        assertEquals(2, l.size(), String.valueOf(l));
        assertTrue(l.get(0).startsWith("// GATF record: a click step was skipped"), l.get(0));
        assertEquals("click id@ok", l.get(1));
        for (String line : l) {
            assertFalse(line.contains("$") || line.matches("(?s).*#[A-Za-z].*") || line.matches("(?s).*[\\x00-\\x1f\\x7f\\u0085\\u2028\\u2029].*"), line);
        }
    }

    @Test
    void onlyAWholeOneIsStrict() {
        for (Object n : new Object[] { 1.9d, 1.0000001d, 4294967297L, -1, 0, 2, "1", null, Double.NaN, new java.math.BigInteger("4294967297"), 1.5f }) {
            Map<String, Object> rk = PickLocator.rank(Arrays.asList(withN(cand("testid", "go", 1), n)), withN(sel("id", "go", 1), n));
            assertNull(rk.get("locator"), "n=" + n);
        }
        for (Object n : new Object[] { 1, 1L, 1.0d, 1.0f, (short) 1, java.math.BigInteger.ONE, new java.math.BigDecimal("1.00") }) {
            assertEquals("testid@go", PickLocator.rank(Arrays.asList(withN(cand("testid", "go", 1), n)), null).get("locator"), "n=" + n);
            assertEquals("id@go", PickLocator.rank(null, withN(sel("id", "go", 1), n)).get("locator"), "n=" + n);
        }
        assertNull(post("{\"cand\":[{\"s\":\"testid\",\"v\":\"go\",\"n\":1.9}]}").body.get("locator"));
        assertNull(post("{\"cand\":[{\"s\":\"testid\",\"v\":\"go\",\"n\":4294967297}]}").body.get("locator"));
        assertEquals("testid@go", post("{\"cand\":[{\"s\":\"testid\",\"v\":\"go\",\"n\":1}]}").body.get("locator"));
    }

    private static Map<String, Object> withN(Map<String, Object> m, Object n) {
        m.put("n", n);
        return m;
    }

    @Test
    void aDrainedPickIsBounded() {
        List<Object> many = new ArrayList<Object>();
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < 5000; i++) big.append('x');
        for (int i = 0; i < 1000; i++) many.add(cand("testid", "t" + i + big, 1));
        Map<String, Object> p = new LinkedHashMap<String, Object>();
        p.put("type", "pick");
        p.put("cand", many);
        p.put("sel", sel("css", big.toString(), 1));
        Map<String, Object> b = PickLocator.boundedPick(p);
        assertEquals(PickLocator.MAX_CANDIDATES, ((List<?>) b.get("cand")).size());
        for (Object o : (List<?>) b.get("cand")) assertEquals(com.gatf.selenium.LocatorRanking.MAX_TEXT + 1, ((String) ((Map<?, ?>) o).get("v")).length());
        assertEquals(PickLocator.MAX_SELECTOR + 1, ((String) ((Map<?, ?>) b.get("sel")).get("v")).length());
        assertEquals(-1, ((Map<?, ?>) b.get("sel")).get("n"));   // a cut selector is another selector: its count is void
        RecordSession s = new RecordSession("t", null);
        s.ingestPick(p);
        assertNull(s.lastPick().get("locator"));                  // nothing cut can become a locator
        assertEquals(PickLocator.MAX_CANDIDATES, ((List<?>) s.lastPick().get("candidates")).size());
        // a long but whole css path (common) is kept and still strict
        Map<String, Object> q = new LinkedHashMap<String, Object>();
        q.put("type", "pick");
        q.put("sel", sel("css", "div.container > div:nth-of-type(2) > ul.list > li:nth-of-type(3) > a.item", 1));
        s.ingestPick(q);
        assertEquals("click css@\"div.container > div:nth-of-type(2) > ul.list > li:nth-of-type(3) > a.item\"", s.lastPick().get("line"));
    }

    /** A fake driver: the poller's scripts, with the page's queued picks as DRAIN_JS results. */
    private static final class FakePage implements java.lang.reflect.InvocationHandler {
        final java.util.ArrayDeque<Object> drains = new java.util.ArrayDeque<Object>();
        final List<String> ran = new ArrayList<String>();
        public Object invoke(Object proxy, java.lang.reflect.Method m, Object[] args) {
            if (m.getName().equals("executeScript")) {
                String js = (String) args[0];
                ran.add(js.equals(PickLocator.DRAIN_JS) ? "drain" : js.equals(PickLocator.OFF_JS) ? "off" : js.startsWith("(function(){if(window.__gatfPicker)return;") ? "arm" : "other");
                return js.equals(PickLocator.DRAIN_JS) ? (drains.isEmpty() ? new ArrayList<Object>() : drains.poll()) : null;
            }
            return null;
        }
    }

    private static Map<String, Object> pickOf(String testid) {
        Map<String, Object> p = new LinkedHashMap<String, Object>();
        p.put("type", "pick");
        p.put("cand", Arrays.asList(cand("testid", testid, 1)));
        p.put("sel", null);
        return p;
    }

    @Test
    void aPickQueuedBeforeArmingIsDiscarded() throws Exception {
        RecordSession s = new RecordSession("t", null);
        FakePage page = new FakePage();
        Object driver = java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { org.openqa.selenium.WebDriver.class, org.openqa.selenium.JavascriptExecutor.class }, page);
        java.lang.reflect.Field d = RecordSession.class.getDeclaredField("driver");
        d.setAccessible(true);
        d.set(s, driver);
        java.lang.reflect.Field run = RecordSession.class.getDeclaredField("running");
        run.setAccessible(true);
        run.setBoolean(s, true);
        assertTrue(s.armPicker(true));
        page.drains.add(Arrays.asList(pickOf("forged")));         // the page queued this before the user armed
        s.pickTick();
        assertNull(s.lastPick());
        assertTrue(s.isPickerArmed());
        page.drains.add(Arrays.asList(pickOf("first"), pickOf("real")));   // two after arming: only the last counts
        s.pickTick();
        assertEquals("click testid@real", s.lastPick().get("line"));
        assertFalse(s.isPickerArmed());
        page.drains.add(Arrays.asList(pickOf("late")));           // after the pick disarmed it: the poller no longer reads picks
        s.pickTick();
        assertEquals("click testid@real", s.lastPick().get("line"));
        assertEquals(Arrays.asList("drain", "arm", "drain", "off"), page.ran);   // no re-arm after the pick, OFF exactly once
    }

    // ---- Δ148b ------------------------------------------------------------------------------------------------

    /** Page-supplied values and the quoted step content the recorder writes for them (null: the fixed skip comment). */
    static final String[][] VALUE_VECTORS = {
        { "$100", "#[[$100]]#" },
        { "p@$$w0rd", "#[[p@$$w0rd]]#" },
        { "#set($x=1)$x", "#[[#set($x=1)$x]]#" },
        { "]]#", "]]#" },                                   // no '$': evaluate leaves it alone, nothing to wrap
        { "a]]#$x", null },                                 // would close the unparsed block early
        { "$v{x}", null },                                  // evaluate rewrites $v to $ before templating
        { "$p{prov.0.x}", null },                           // evaluate's provider pass templates its output again
        { "$P{prov.0.x}", null },
        { "say \"hi\" C:\\x", "say \\\"hi\\\" C:\\\\x" },
        { "price $5 \"x\"", "#[[price $5 \\\"x\\\"]]#" },
        { "a\rb\nc\td\u0000e\u2028f\u0085g", "a b c d e f g" },
        { "plain text", "plain text" },
        { "#if(true)x#end", "#if(true)x#end" },             // no '$': not templated
    };

    @Test
    void pageSuppliedValuesAreCarriedLiterallyOrSkipped() {
        for (String[] v : VALUE_VECTORS) assertEquals(v[1], PickLocator.stepValue(v[0]), v[0]);
        assertEquals("", PickLocator.stepValue(null));
    }

    @Test
    void theRecorderWritesTypedSelectedAndNavigatedTextSafely() {
        RecordSession s = new RecordSession("t", null);
        String[][] events = {
            { "type", "#set($x=1)$y" }, { "select", "$100" }, { "type", "a]]#$x" }, { "select", "$p{prov.0.x}" },
            { "goto", "https://shop.test/p$1" }, { "goto", "https://x.test/a\"+b" }, { "goto", "https://x.test/a\rexecjs x" },
            { "goto", "https://x.test/a b" }, { "goto", "javascript:alert(1)" }, { "goto", "https://x.test/ok" },
        };
        int t = 0;
        for (String[] e : events) {
            Map<String, Object> a = new LinkedHashMap<String, Object>();
            a.put("type", e[0]);
            if (e[0].equals("goto")) a.put("url", e[1]); else { a.put("value", e[1]); a.put("sel", sel("id", "f" + t, 1)); }
            a.put("t", Long.valueOf(++t));   // 1 ms apart: no sleep lines
            s.ingest(a);
        }
        List<String> l = s.currentLines();
        assertEquals("type \"#[[#set($x=1)$y]]#\" id@f0", l.get(0));
        assertEquals("select value@\"#[[$100]]#\" id@f1", l.get(1));
        assertEquals(PickLocator.skipped("record", "type"), l.get(2));
        assertEquals(PickLocator.skipped("record", "select"), l.get(3));
        assertEquals("goto #[[https://shop.test/p$1]]#", l.get(4));
        assertEquals(PickLocator.skipped("record", "goto"), l.get(5));      // quote: would end GotoCommand's Java literal
        // CR, space and javascript: are skipped too; consecutive identical skip lines collapse like any duplicate step
        assertEquals("goto https://x.test/ok", l.get(6));
        assertEquals(7, l.size(), String.valueOf(l));
        assertTrue(s.currentLines().stream().noneMatch(x -> x.matches("(?s).*[\\r\\n\\u2028\\u2029].*")));
        // one line per statement, and every '$' sits inside #[[ … ]]#
        for (String x : l) {
            String outside = x.replaceAll("#\\[\\[.*?\\]\\]#", "");
            assertFalse(outside.contains("$") && !x.startsWith("// "), x);
        }
    }

    @Test
    void armingBetweenTheTwoWritesNeverIngestsAPrequeuedPick() throws Exception {
        // the reviewer's interleaving: a poller tick lands inside armPicker(true), between its two flag writes
        RecordSession s = new RecordSession("t", null);
        FakePage page = new FakePage();
        Object driver = java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { org.openqa.selenium.WebDriver.class, org.openqa.selenium.JavascriptExecutor.class }, page);
        java.lang.reflect.Field d = RecordSession.class.getDeclaredField("driver");
        d.setAccessible(true);
        d.set(s, driver);
        java.lang.reflect.Field run = RecordSession.class.getDeclaredField("running");
        run.setAccessible(true);
        run.setBoolean(s, true);
        java.lang.reflect.Field off = RecordSession.class.getDeclaredField("pickOffPending");
        off.setAccessible(true);
        try {
            for (boolean offPending : new boolean[] { false, true }) {
                off.setBoolean(s, offPending);     // also with an earlier disarm still pending, so the tick does run
                page.drains.add(Arrays.asList(pickOf("forged")));
                page.drains.add(Arrays.asList(pickOf("forged2")));
                RecordSession.armSeam = s::pickTick;
                s.armPicker(true);
                RecordSession.armSeam = null;
                s.pickTick();                      // the first tick after arming: still the page's own queue
                assertNull(s.lastPick(), "offPending=" + offPending);
                assertTrue(s.isPickerArmed());
                s.armPicker(false);
                s.pickTick();
                page.drains.clear();
            }
        } finally {
            RecordSession.armSeam = null;
        }
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> vectors() throws Exception {
        File f = new File("src/test/resources/pick-locator/escape-vectors.json");
        return new ArrayList<Map<String, Object>>((List<Map<String, Object>>) OM.readValue(Files.readAllBytes(f.toPath()), Object.class));
    }

    @Test
    void ingestPickRanksAndDisarmsOnPickOrCancel() {
        RecordSession s = new RecordSession("t", null);
        assertFalse(s.armPicker(true));                 // not recording: refused
        assertNull(s.lastPick());
        Map<String, Object> p = new LinkedHashMap<String, Object>();
        p.put("type", "pick");
        p.put("cand", Arrays.asList(cand("testid", "go", 1)));
        p.put("sel", sel("id", "go", 1));
        s.ingestPick(p);
        assertEquals("click testid@go", s.lastPick().get("line"));
        assertEquals(1, s.lastPick().get("seq"));
        Map<String, Object> c = new LinkedHashMap<String, Object>();
        c.put("type", "cancel");
        s.ingestPick(c);
        assertEquals(Boolean.TRUE, s.lastPick().get("cancelled"));
        assertNull(s.lastPick().get("line"));
        assertFalse(s.isPickerArmed());
        assertTrue(s.recorderJs().contains("__gatfCandidates(el,'data-testid')"));   // the recorder script is unchanged
    }
}
