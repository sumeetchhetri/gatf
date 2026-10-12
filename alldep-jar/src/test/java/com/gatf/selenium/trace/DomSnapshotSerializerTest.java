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
package com.gatf.selenium.trace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * H2 (Δ146): the DOMSnapshot serializer and the in-page capture, on a fixture captured from a real Chromium 141
 * (domsnapshot-page.html after typing into it): {@code snapshot} = DOMSnapshot.captureSnapshot, {@code cdpMeta} /
 * {@code scriptMeta} = DomCapture.META_JS without / with serialization, targets #name (mark 1) and #go (mark 2).
 */
class DomSnapshotSerializerTest {

    @SuppressWarnings("unchecked")
    static Map<String, Object> fixture() throws Exception {
        try (InputStream in = DomSnapshotSerializerTest.class.getResourceAsStream("/trace/domsnapshot-chrome141.json")) {
            return new ObjectMapper().readValue(in, Map.class);
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    void cdpSnapshotSerializesStyledStateAndStripsActiveContent() throws Exception {
        Map<String, Object> fx = fixture();
        RichSnapshot r = DomCapture.fromCdp((Map<String, Object>) fx.get("snapshot"), fx.get("cdpMeta"),
                Arrays.asList("1", "2"), 2, DomCapture.Caps.of(new TraceConfig()));
        assertNotNull(r);
        String h = r.html;
        assertEquals("cdp", r.source);
        assertTrue(h.startsWith("<!DOCTYPE html><html"), h.substring(0, 40));
        // form state the user produced, not the authored defaults
        assertTrue(h.contains("value=\"typed &lt;&amp;&gt; &quot;q&quot;\""), "typed value");
        assertFalse(h.contains("value=\"default\""), "authored default dropped");
        assertTrue(h.matches("(?s).*<input id=\"cb\" type=\"checkbox\"[^>]* checked>.*"), "checkbox state");
        assertTrue(h.contains("<option value=\"b\" selected>"), "selected option");
        assertFalse(h.contains("<option value=\"a\" selected>"), "authored selected dropped");
        assertTrue(h.contains(">edited &lt;/textarea&gt;</textarea>"), "textarea value, escaped");
        // a password never reaches the bundle
        assertFalse(h.contains("hunter2"), "password leaked");
        assertTrue(h.contains("type=\"password\" value=\"\u2022\u2022\u2022\u2022\u2022\u2022\u2022\""), "password masked");
        // active content removed
        assertFalse(h.toLowerCase().contains("<script"), "script");
        assertFalse(h.contains("onclick") || h.contains("onload"), "handlers");
        assertFalse(h.contains("<noscript"), "noscript");
        assertFalse(h.contains("javascript:"), "javascript: url");
        assertFalse(h.contains("<base"), "base");
        assertFalse(h.contains("http-equiv=\"refresh\""), "meta refresh");
        // structure
        assertTrue(h.contains("<template shadowrootmode=\"open\"><style>p{color:blue}</style><p class=\"in-shadow\">shadow text</p></template>"), "shadow root");
        assertTrue(h.contains("srcdoc=\"") && h.contains("frame body"), "same-origin iframe inlined");
        assertFalse(h.contains("src=\"/frame.html\""), "iframe src dropped");
        assertTrue(h.contains("<linearGradient") && h.contains("<foreignObject"), "SVG case kept");
        assertTrue(h.contains("src=\"http://127.0.0.1:8765/a.png\"") && !h.contains("srcset"), "currentSrc");
        // targets
        assertTrue(h.contains("id=\"name\"") && h.matches("(?s).*<input id=\"name\"[^>]*data-gatf-target=\"1\".*"), "target 1");
        assertTrue(h.matches("(?s).*<button id=\"go\"[^>]*data-gatf-target=\"2\".*"), "target 2");
        // CSSOM the HTML cannot carry
        assertEquals(3, r.styles.size());
        assertEquals("link", r.styles.get(0).owner);
        assertTrue(r.styles.get(0).text.contains(".imported") && r.styles.get(0).text.contains(".btn"), "@import inlined");
        assertEquals("style", r.styles.get(1).owner);
        assertEquals(0, r.styles.get(1).index);
        assertTrue(r.styles.get(1).text.contains(".injected"), "CSS-in-JS rules");
        assertEquals("adopted", r.styles.get(2).owner);
        assertEquals(800L, r.viewportW);
        assertEquals(2, r.targetRects.size());
        assertEquals(185.0, r.targetRects.get(0)[2], 0.01);
        assertFalse(r.truncated);
    }

    @SuppressWarnings("unchecked")
    @Test
    void scriptSerializationAgreesWithCdpOnTheEssentials() throws Exception {
        Map<String, Object> fx = fixture();
        RichSnapshot r = DomCapture.captureByScript(a -> fx.get("scriptMeta"), Arrays.asList(new Object(), new Object()),
                Arrays.asList("1", "2"), DomCapture.Caps.of(null));
        assertNotNull(r);
        assertEquals("script", r.source);
        String h = r.html;
        for (String must : new String[] { "typed &lt;&amp;&gt; &quot;q&quot;", "<option value=\"b\" selected>", "data-gatf-target=\"1\"",
                "data-gatf-target=\"2\"", "shadowrootmode=\"open\"", "frame body", "edited &lt;/textarea&gt;" }) {
            assertTrue(h.contains(must), must);
        }
        assertFalse(h.contains("hunter2") || h.toLowerCase().contains("<script") || h.contains("onclick") || h.contains("javascript:"));
        assertEquals(3, r.styles.size());
    }

    @Test
    void markingTheSameElementForTwoActionsJoinsTheIds() {
        Map<String, Object> s = tinyDoc();
        List<DomSnapshotSerializer.Mark> m = new ArrayList<>();
        m.add(new DomSnapshotSerializer.Mark(new int[] { 1, 0 }, "4"));
        m.add(new DomSnapshotSerializer.Mark(new int[] { 1, 0 }, "5"));
        m.add(new DomSnapshotSerializer.Mark(new int[] { 1, 0 }, "5"));
        m.add(new DomSnapshotSerializer.Mark(new int[] { 7, 7 }, "6"));   // the DOM changed: unresolved, not fatal
        DomSnapshotSerializer.Result r = DomSnapshotSerializer.serialize(s, m);
        assertTrue(r.html.contains("<p data-gatf-target=\"4 5\">hi</p>"), r.html);
        assertEquals(1, r.unresolvedMarks);
    }

    @Test
    void hostileNamesCannotBreakOutOfTheTag() {
        Map<String, Object> s = tinyDoc();
        @SuppressWarnings("unchecked")
        List<Object> strings = (List<Object>) s.get("strings");
        strings.set(3, "P><img src=x onerror=alert(1)");        // tag name
        strings.add("x\" onmouseover=\"alert(1)");                 // attribute name: 8
        strings.add("v\"><script>");                                 // attribute value: 9
        @SuppressWarnings("unchecked")
        List<Object> attrs = (List<Object>) ((Map<String, Object>) ((Map<String, Object>) ((List<Object>) s.get("documents")).get(0)).get("nodes")).get("attributes");
        attrs.set(4, Arrays.asList(8, 9));
        String h = DomSnapshotSerializer.serialize(s, null).html;
        assertFalse(h.contains("<img") || h.contains("<script") || h.contains("onmouseover=\"alert"), h);
        assertTrue(h.contains("&quot;&gt;&lt;script&gt;"), h);
    }

    @Test
    void capsTruncateCleanlyAndCyclesTerminate() {
        Map<String, Object> s = tinyDoc();
        DomSnapshotSerializer.Result r = DomSnapshotSerializer.serialize(s, null, 2, 1 << 20);
        assertTrue(r.truncated);
        assertTrue(r.html.endsWith("</html>"), r.html);
        // a parent cycle (corrupt data) must not hang: p's parent is the text node and vice versa
        @SuppressWarnings("unchecked")
        List<Object> parents = (List<Object>) ((Map<String, Object>) ((Map<String, Object>) ((List<Object>) s.get("documents")).get(0)).get("nodes")).get("parentIndex");
        parents.set(4, 5);
        parents.set(5, 4);
        assertNotNull(DomSnapshotSerializer.serialize(s, null, 50, 1 << 20));
        assertNull(DomSnapshotSerializer.serialize(new HashMap<>(), null));
        assertNull(DomSnapshotSerializer.serialize(null, null));
    }

    /** 0 document > 1 html > (2 head, 3 body > 4 p > 5 "hi"). strings: 0 #document 1 HTML 2 BODY 3 P 4 hi 7 HEAD */
    static Map<String, Object> tinyDoc() {
        Map<String, Object> nodes = new HashMap<>();
        nodes.put("parentIndex", new ArrayList<Object>(Arrays.asList(-1, 0, 1, 1, 3, 4)));
        nodes.put("nodeType", new ArrayList<Object>(Arrays.asList(9, 1, 1, 1, 1, 3)));
        nodes.put("nodeName", new ArrayList<Object>(Arrays.asList(0, 1, 7, 2, 3, -1)));
        nodes.put("nodeValue", new ArrayList<Object>(Arrays.asList(-1, -1, -1, -1, -1, 4)));
        nodes.put("attributes", new ArrayList<Object>(Arrays.asList(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>())));
        Map<String, Object> doc = new HashMap<>();
        doc.put("nodes", nodes);
        doc.put("documentURL", 0);
        Map<String, Object> s = new HashMap<>();
        s.put("documents", new ArrayList<Object>(Arrays.asList(doc)));
        s.put("strings", new ArrayList<Object>(Arrays.asList("#document", "HTML", "BODY", "P", "hi", "", "", "HEAD")));
        return s;
    }
}
