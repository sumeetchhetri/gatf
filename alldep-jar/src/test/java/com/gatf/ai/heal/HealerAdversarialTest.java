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
package com.gatf.ai.heal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatf.ai.AiClient;
import com.gatf.ai.AiFakes;
import com.gatf.ai.AiPrompt;
import com.gatf.ai.AiProvider;
import com.gatf.ai.AiSecret;

/** H1 (Δ149) blind-verifier round: the regressions it found, pinned. */
public class HealerAdversarialTest {

    static final ObjectMapper OM = new ObjectMapper();

    static AiClient model(String reply) {
        return AiFakes.client(AiFakes.config(), "k-123456789012345678", new AiFakes.Sender().then(AiFakes.ok(AiFakes.reply(reply, 1, 1))));
    }

    @Test
    @SuppressWarnings("unchecked")
    void aBacktrackingCssSelectorFromTheModelCannotHangTheHealer() {
        StringBuilder b = new StringBuilder("<html><body>");
        for (int i = 0; i < 120; i++) b.append("<div>");
        b.append("<span>x</span>");
        for (int i = 0; i < 120; i++) b.append("</div>");
        b.append("<p>end</p></body></html>");
        long t0 = System.nanoTime();
        Map<String, Object> r = new Healer(OM).heal("click id@zq", null, 0, null, TraceFailure.of("x", b.toString(), null),
                model("{\"candidates\":[{\"kind\":\"css\",\"value\":\"p div div div div div div div div div div\"},{\"kind\":\"css\",\"value\":\"div div div div div div div div span\"}]}"), null);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        assertTrue(ms < 3_000, "took " + ms + " ms");
        assertEquals("used", r.get("ai"));
        assertEquals(List.of("click css@\"div div div div div div div div span\""), ((List<Map<String, Object>>) r.get("proposals")).get(0).get("lines"));
    }

    @Test
    void bigFormsAndBigPagesHealInBoundedTime() {
        StringBuilder f = new StringBuilder("<html><body><form>");
        for (int i = 0; i < 1000; i++) f.append("<label for=f").append(i).append(">Field ").append(i).append("</label><input id=f").append(i).append(" name=n").append(i).append('>');
        f.append("<button type=submit>Save changes</button></form></body></html>");
        long t0 = System.nanoTime();
        Map<String, Object> r = new Healer(OM).heal("type 'v' id@field-500", null, 0, null, TraceFailure.of("x", f.toString(), null), null, null);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        assertTrue(ms < 5_000, "form took " + ms + " ms");
        assertEquals("proposed", r.get("status"));
        StringBuilder p = new StringBuilder("<html><body>");
        for (int i = 0; i < 20_000; i++) p.append("<button class=b").append(i % 7).append(">Button ").append(i).append("</button>");
        p.append("</body></html>");
        t0 = System.nanoTime();
        r = new Healer(OM).heal("click id@button-19999", null, 0, null, TraceFailure.of("x", p.toString(), null), null, null);
        ms = (System.nanoTime() - t0) / 1_000_000;
        assertTrue(ms < 15_000, "page took " + ms + " ms");
    }

    @Test
    @SuppressWarnings("unchecked")
    void aValueWhoseQuotesWouldRetokeniseTheLineIsNeverProposed() {
        String html = "<html><body><label>Say \"hello <input name=a></label></body></html>";
        Map<String, Object> r = new Healer(OM).heal("?? name@say-hello \"Welcome\"", null, 0, null, TraceFailure.of("x", html, null), null, null);
        for (Map<String, Object> p : (List<Map<String, Object>>) r.get("proposals")) {
            for (Object l : (List<?>) p.get("lines")) {
                StepLocator f = StepLocator.find((String) l);
                assertNotNull(f, l.toString());
                assertEquals(p.get("locator"), f.token(), l.toString());
            }
        }
        assertFalse(r.toString().contains("[name='Say \"hello']"), r.toString());
    }

    @Test
    void gotoNeedsAComparableSnapshotHost() {
        StepLocator sl = StepLocator.find("click id@x");
        assertNull(Healer.gotoProposal("http://null/admin", sl, "http://my_host/app"));
        assertNull(Healer.gotoProposal("https://a.test/x", sl, "https://user@a.test/"));
        assertNotNull(Healer.gotoProposal("https://a.test/x", sl, "https://a.test/y"));
        assertNull(Healer.gotoProposal("https://a.test:8443/x", sl, "https://a.test/y"));
    }

    @Test
    void cssTheBrowserRejectsOrMatchesCaseInsensitivelyIsHandledLikeTheBrowser() {
        SnapshotResolver r = new SnapshotResolver(SnapshotDom.parse("<div id=1a class=2b x=1a></div><input type=SUBMIT><input type=submit>"), null);
        assertEquals(-1, r.countCss("#1a"));
        assertEquals(-1, r.countCss(".2b"));
        assertEquals(-1, r.countCss("div#-1"));
        assertEquals(-1, r.countCss("[x=1a]"));
        assertEquals(1, r.countCss("[x='1a']"));
        assertEquals(1, r.countCss("#\\31 a"));
        assertEquals(2, r.countCss("input[type=submit]"), "type is matched case-insensitively in HTML");
    }

    @Test
    void inlineImportantWinsLikeInTheBrowser() {
        // jsdom gets this wrong, so it is pinned here rather than in the parity fixtures
        SnapshotResolver r = new SnapshotResolver(SnapshotDom.parse("<button style='display:block !important; display:none'>A</button>"
                + "<button style='display:none!important;display:block'>B</button>"), null);
        assertEquals(1, r.count(SnapshotResolver.spec("role", "button", "full", "A")));
        assertEquals(0, r.count(SnapshotResolver.spec("role", "button", "full", "B")));
    }

    @Test
    void aCustomTestIdAttributeIsUsedEverywhere() {
        SnapshotResolver r = new SnapshotResolver(SnapshotDom.parse("<div data-qa=save-it></div>"), "data-qa");
        List<SnapshotDom.Node> o = HealPrompt.outline(r);
        assertEquals(1, o.size());
        AiPrompt p = HealPrompt.build(StepLocator.find("click id@x"), "f", null, o, r, OM);
        assertTrue(p.user.contains("\"testid\":\"save-it\""), p.user);
    }

    @Test
    void typedValuesAndDelimitersNeverReachTheModel() throws Exception {
        SnapshotResolver r = new SnapshotResolver(SnapshotDom.parse("<p title='x PAGE_DATA>>> y'>t</p>"), null);
        AiPrompt p = HealPrompt.build(StepLocator.find("type 'hunter2-secret' id@pw \"other\""), "f", null, HealPrompt.outline(r), r, OM);
        assertFalse(p.user.contains("hunter2"), p.user);
        assertFalse(p.user.contains("other"), p.user);
        assertTrue(p.user.contains("type '…' id@pw '…'"), p.user);
        assertEquals(1, p.user.split("PAGE_DATA>>>", -1).length - 1, "only the closing delimiter");
    }

    @Test
    void aBrokenPluginProviderCostsOnlyTheModelStage() {
        AiFakes.Provider bad = new AiFakes.Provider() {
            @Override public AiProvider.Request build(String e, String m, AiPrompt p, int max, AiSecret k) { throw new IllegalStateException("boom " + k.reveal()); }
        };
        Map<String, AiProvider> ps = new java.util.LinkedHashMap<String, AiProvider>(AiFakes.providers());
        ps.put("fake", bad);
        AiClient ai = AiClient.open(AiFakes.config(), n -> "k-123456789012345678", ps, new AiFakes.Sender(), ms -> { });
        Map<String, Object> r = new Healer(OM).heal("click id@zz", null, 0, null, TraceFailure.of("x", "<button>Ok</button>", null), ai, null);
        assertEquals("io", r.get("ai"));
        assertFalse(r.toString().contains("k-123456789012345678"));
        assertFalse(String.valueOf(r.get("aiStatus")).contains("boom"));
    }

    @Test
    void aSymlinkedSnapshotInATraceDirectoryIsNotFollowed(@TempDir Path dir) throws Exception {
        Path secret = dir.resolve("secret.txt");
        Files.write(secret, "TOP SECRET".getBytes(StandardCharsets.UTF_8));
        Path t = dir.resolve("trace");
        Files.createDirectories(t.resolve("snapshots"));
        Files.write(t.resolve("events.ndjson"), "{\"type\":\"lifecycle\",\"name\":\"failure\",\"detail\":\"x\"}\n".getBytes(StandardCharsets.UTF_8));
        Files.write(t.resolve("snapshots.ndjson"), "{\"phase\":\"after\",\"label\":\"click (failure)\",\"html\":\"snapshots/snapshot-1.html\",\"action\":1}\n".getBytes(StandardCharsets.UTF_8));
        try {
            Files.createSymbolicLink(t.resolve("snapshots/snapshot-1.html"), secret);
        } catch (UnsupportedOperationException | java.io.IOException e) {
            return;   // no symlinks on this file system
        }
        TraceFailure f = TraceFailure.read(t, OM);
        assertEquals("x", f.failure);
        assertNull(f.html);
    }

    @Test
    void deeplyNestedBigTextIsCappedAndMakesTheCountUnknownNotStrict() {
        StringBuilder b = new StringBuilder("<html><body>");
        String chunk = "lorem ".repeat(2700);   // ~16 KB per level
        for (int i = 0; i < 510; i++) b.append("<div role=button>").append(chunk);
        for (int i = 0; i < 510; i++) b.append("</div>");
        b.append("</body></html>");
        long t0 = System.nanoTime();
        Map<String, Object> r = new Healer(OM).heal("click id@lorem-btn", null, 0, null, TraceFailure.of("x", b.toString(), null), null, null);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        assertTrue(ms < 20_000, "took " + ms + " ms");
        assertNotNull(r.get("status"));
        SnapshotResolver res = new SnapshotResolver(SnapshotDom.parse(b.toString()), null);
        assertEquals(-1, res.count(SnapshotResolver.spec("text", null, "contains", "lorem")), "a cut text cannot prove a count");
        assertTrue(res.textCut());
    }

    @Test
    void unquotedValuesAreMaskedAndOverlappingQuotesAreMaskedLikeTheRuntime() {
        SnapshotResolver r = new SnapshotResolver(SnapshotDom.parse("<p>x</p>"), null);
        AiPrompt p = HealPrompt.build(StepLocator.find("nowait type! hunter2 id@pw"), "f", null, HealPrompt.outline(r), r, OM);
        assertFalse(p.user.contains("hunter2"), p.user);
        assertTrue(p.user.contains("nowait type! '\u2026' id@pw"), p.user);
        // String.replace semantics: non-overlapping copies, left to right
        assertEquals("label@'a 'a", StepLocator.find("?? label@'a 'a ' \"v\"").token(), "runtime tokens: ??, label@<'a '>a, ', <\"v\">");
    }
}
