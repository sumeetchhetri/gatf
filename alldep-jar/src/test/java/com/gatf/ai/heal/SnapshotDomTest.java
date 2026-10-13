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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.gatf.ai.heal.SnapshotDom.Node;

/** H1 (Δ149): the snapshot HTML tree: tolerant parsing, entities, shadow roots, inert templates, bounds. */
public class SnapshotDomTest {

    static List<String> tags(SnapshotDom d) {
        List<String> t = new ArrayList<String>();
        for (Node n : d.elements()) t.add(n.tag);
        return t;
    }

    @Test
    void parsesVoidRawTextEntitiesAndAttributes() {
        SnapshotDom d = SnapshotDom.parse("<!DOCTYPE html><html><head><style>a<b{}</style><script>if (a<b) x='</div>';</script></head>"
                + "<body><img src=a.png alt='A &amp; B'><br/><input disabled value=\"x&quot;y\" DATA-TestId=t1>"
                + "<textarea>a &lt; b</textarea><p>x&nbsp;&#x41;&#66;&bogus;</p><!-- <button>no</button> --></body></html>");
        assertEquals(List.of("html", "head", "style", "script", "body", "img", "br", "input", "textarea", "p"), tags(d));
        Node img = d.elements().get(5), in = d.elements().get(7);
        assertEquals("A & B", img.attr("alt"));
        assertEquals("x\"y", in.attr("value"));
        assertEquals("", in.attr("disabled"));
        assertEquals("t1", in.attr("data-testid"), "attribute names are lower-cased");
        assertEquals("a < b", d.elements().get(8).children.get(0).text);
        assertEquals("x AB&bogus;", d.elements().get(9).children.get(0).text);
        assertTrue(d.elements().get(3).children.get(0).text.contains("'</div>'"), "script text is raw");
    }

    @Test
    void impliedEndTagsAndStrayEndTags() {
        SnapshotDom d = SnapshotDom.parse("<ul><li>a<li>b</ul><p>one<div>two</div></span><select><option>x<option>y</select><table><tr><td>1<td>2<tr><td>3</table>");
        Node ul = d.elements().get(0);
        assertEquals(2, ul.children.size(), "li closes li");
        Node p = d.elements().get(3);
        assertEquals("p", p.tag);
        assertEquals("div", d.elements().get(4).tag);
        assertEquals(d.document, d.elements().get(4).parent, "div closes the open p");
        Node sel = d.elements().get(5);
        assertEquals(2, sel.children.size());
        int tds = 0;
        for (Node n : d.elements()) if (n.tag.equals("td")) { tds++; assertEquals("tr", n.parent.tag); }
        assertEquals(3, tds);
    }

    @Test
    void declarativeShadowRootsAttachAndPlainTemplatesAreInert() {
        SnapshotDom d = SnapshotDom.parse("<x-a><template shadowrootmode=\"open\"><button>In</button></template><span>light</span></x-a>"
                + "<template><button>Inert</button></template>");
        Node host = d.elements().get(0);
        assertNotNull(host.shadowRoot);
        assertEquals(host, host.shadowRoot.host);
        assertEquals("button", host.shadowRoot.children.get(0).tag);
        assertEquals(List.of("x-a", "button", "span", "template"), tags(d), "shadow first, light after, template content unseen");
        assertEquals(1, host.children.size());
    }

    @Test
    void unterminatedMarkupAndHugeInputAreBounded() {
        SnapshotDom d = SnapshotDom.parse("<div class=\"x><p>never closed <!-- open comment");
        assertNotNull(d);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < SnapshotDom.MAX_NODES + 10; i++) b.append("<i>");
        SnapshotDom big = SnapshotDom.parse(b.toString());
        assertTrue(big.truncated);
        assertTrue(big.elements().size() <= SnapshotDom.MAX_NODES);
        assertNull(SnapshotDom.parse(null).document.parent);
    }

    @Test
    void stylesheetHiddenElementsCountHereNotOnThePageSoCountsOnlyGoUp() {
        // the documented one-sided difference: a stylesheet can hide an element; the port does not read stylesheets,
        // so it counts 2 where the page counts 1 and the locator is rejected as non-strict (never the reverse)
        SnapshotResolver r = new SnapshotResolver(SnapshotDom.parse("<style>.gone{display:none}</style><button>Save</button><button class=gone>Save</button>"), null);
        assertEquals(2, r.count(SnapshotResolver.spec("role", "button", "full", "Save")));
    }
}
