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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatf.ai.heal.SnapshotDom.Node;

/**
 * H1 (Δ149): the Java resolver port counts, describes and ranks exactly as the page script does on the same snapshot
 * HTML. {@code parity-expected.json} is written by {@code tools/smoke/test-heal-parity.mjs --write} from the generated
 * page resolver on jsdom; this test holds {@link SnapshotResolver} to it, fixture by fixture.
 */
public class SnapshotResolverParityTest {

    static Path res(String rel) {
        Path p = Paths.get("src/test/resources/heal", rel);
        return Files.exists(p) ? p : Paths.get("alldep-jar/src/test/resources/heal", rel);
    }

    @Test
    @SuppressWarnings("unchecked")
    void javaResolverMatchesThePageScriptOnEveryFixture() throws Exception {
        ObjectMapper om = new ObjectMapper();
        List<Map<String, Object>> cases = om.readValue(res("parity/cases.json").toFile(), List.class);
        Map<String, Object> expected = om.readValue(res("parity/parity-expected.json").toFile(), Map.class);
        int checks = 0;
        List<String> diffs = new ArrayList<String>();
        for (Map<String, Object> c : cases) {
            String name = (String) c.get("name");
            Map<String, Object> exp = (Map<String, Object>) expected.get(name);
            assertNotNull(exp, name);
            SnapshotResolver r = new SnapshotResolver(SnapshotDom.parse((String) c.get("html")), "data-testid");
            List<Object> specs = (List<Object>) c.get("specs"), counts = (List<Object>) exp.get("specs");
            for (int i = 0; i < specs.size(); i++, checks++) {
                int got = r.count(new LinkedHashMap<String, Object>((Map<String, Object>) specs.get(i)));
                if (got != ((Number) counts.get(i)).intValue()) diffs.add(name + " spec " + specs.get(i) + ": java " + got + " page " + counts.get(i));
            }
            Map<String, Object> picks = (Map<String, Object>) exp.get("picks");
            for (Object p : (List<Object>) c.get("picks")) {
                Node el = null;
                for (Node e : r.dom().elements()) if (p.equals(e.attr("data-pick"))) { el = e; break; }
                assertNotNull(el, name + " pick " + p);
                Map<String, Object> want = (Map<String, Object>) picks.get(p);
                Map<String, Object> got = new LinkedHashMap<String, Object>();
                got.put("info", r.info(el));
                got.put("candidates", r.candidates(el));
                got.put("fallback", r.fallback(el));
                String g = om.writeValueAsString(got), w = om.writeValueAsString(want);
                checks++;
                if (!om.readTree(g).equals(om.readTree(w))) diffs.add(name + " pick " + p + ":\n  java " + g + "\n  page " + w);
            }
            Map<String, Object> css = (Map<String, Object>) exp.get("css");
            for (Map.Entry<String, Object> q : css.entrySet()) {
                checks++;
                int got = r.countCss(q.getKey());
                if (got != ((Number) q.getValue()).intValue()) diffs.add(name + " css " + q.getKey() + ": java " + got + " page " + q.getValue());
            }
        }
        assertEquals("", String.join("\n", diffs));
        assertEquals(true, checks > 80, "checks " + checks);
    }
}
