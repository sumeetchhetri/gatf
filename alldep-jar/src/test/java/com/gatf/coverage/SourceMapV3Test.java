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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

/** H6 (Δ156): source map v3 decoding. */
class SourceMapV3Test {

    static JsonNode j(String s) throws Exception { return CoverageMerge.OM.readTree(s); }

    @Test
    void decodesTheFixtureMap() throws Exception {
        SourceMapV3 m = SourceMapV3.parse(j(ImpactFixture.map(true)));
        assertEquals(2, m.sources.size());
        assertEquals("webpack://app/./src/b.js", m.sources.get(1));
        assertTrue(m.sourcesContent.get(0).startsWith("function a(x) {"));
        // b.js line 3 is generated line bGen(3), segments at columns 0 and 2
        List<SourceMapV3.Seg> segs = m.lines(1).get(3);
        assertEquals(2, segs.size());
        assertEquals(ImpactFixture.bGen(3), segs.get(0).genLine);
        assertEquals(0, segs.get(0).genCol);
        assertEquals(2, segs.get(0).endCol);
        assertEquals(-1, segs.get(1).endCol);
        assertEquals(1 + ImpactFixture.A.length + ImpactFixture.B.length, m.generatedLines);
        assertEquals(ImpactFixture.A.length, m.lines(0).size());
    }

    @Test
    void vlqRoundTrip() throws Exception {
        for (int v : new int[] {0, 1, 15, 16, 31, 32, 1000, 123456, 1 << 20}) {
            // genCol = v; then a second segment whose original line goes back by v - 1 (a negative delta)
            String map = "{\"version\":3,\"sources\":[\"a\"],\"mappings\":\"" + ImpactFixture.vlq(v) + "A" + ImpactFixture.vlq(v) + "A,"
                    + ImpactFixture.vlq(1) + "A" + ImpactFixture.vlq(-(v - 1)) + "A\"}";
            SourceMapV3 m = SourceMapV3.parse(j(map));
            assertEquals(v, m.lines(0).get(v + 1).get(0).genCol, "v=" + v);
            assertEquals(v + 1, m.lines(0).get(2).get(m.lines(0).get(2).size() - 1).genCol, "v=" + v);
        }
    }

    @Test
    void rejectsWhatItCanNotTrust() {
        String[] bad = {
            "[]",
            "{\"version\":2,\"sources\":[],\"mappings\":\"\"}",
            "{\"version\":3,\"sections\":[]}",
            "{\"version\":3,\"mappings\":\"AAAA\"}",
            "{\"version\":3,\"sources\":[\"a\"],\"mappings\":\"AAAA,!\"}",
            "{\"version\":3,\"sources\":[\"a\"],\"mappings\":\"ACAA\"}",          // source index 1 of 1
            "{\"version\":3,\"sources\":[\"a\"],\"mappings\":\"AA\"}",            // 2 fields
            "{\"version\":3,\"sources\":[\"a\"],\"mappings\":\"g\"}",             // truncated VLQ
            "{\"version\":3,\"sources\":[\"a\"],\"mappings\":\"KAAA,DAAA\"}",     // columns out of order
        };
        for (String b : bad) assertThrows(IllegalArgumentException.class, () -> SourceMapV3.parse(j(b)));
    }
}
