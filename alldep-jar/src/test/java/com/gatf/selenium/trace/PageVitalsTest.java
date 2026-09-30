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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** Q9 — per-navigation web vitals: flat-entry reading, de-duplication by document id, webVitalsByPage shape. */
class PageVitalsTest {

    @Test
    void readsFlatObjectsAndArrays() {
        List<Map<String, Object>> l = PageVitals.parse(" [ {\"id\":\"a\",\"url\":\"https://x/\\u0041?q=\\\"1\\\"\",\"lcp\":12.5,\"ok\":true,\"e\":null},{} ] ");
        assertThat(l).hasSize(2);
        assertThat(l.get(0)).containsEntry("url", "https://x/A?q=\"1\"").containsEntry("lcp", 12.5).containsEntry("ok", true).containsKey("e");
        assertThat(PageVitals.parse("{\"cls\":1e-3}").get(0)).containsEntry("cls", 0.001);
        assertThat(PageVitals.parse("[]")).isEmpty();
    }

    @Test
    void dedupesByDocumentIdAndOrdersByTs() {
        PageVitals p = new PageVitals();
        p.add("{\"id\":\"p1\",\"url\":\"https://a/1\",\"ts\":100,\"lcp\":500,\"cls\":0}");
        p.add("{\"id\":\"p1\",\"url\":\"https://a/1\",\"ts\":150,\"lcp\":800,\"cls\":0.05}");   // pagehide after visibilitychange
        p.add("[{\"id\":\"p2\",\"url\":\"https://a/2\",\"ts\":120,\"lcp\":1500,\"ttfb\":30.25}]"); // drained buffer
        p.add("not json");
        p.add(null);
        assertThat(p.size()).isEqualTo(2);
        String json = p.toJson("{\"id\":\"p3\",\"url\":\"https://a/3\",\"lcp\":700,\"cls\":0.1,\"fp\":3,\"error\":\"x\"}", 999);
        assertThat(json).isEqualTo("[{\"url\":\"https://a/2\",\"ts\":120,\"lcp\":1500,\"ttfb\":30.25},"
                + "{\"url\":\"https://a/1\",\"ts\":150,\"lcp\":800,\"cls\":0.05},"
                + "{\"url\":\"https://a/3\",\"ts\":999,\"lcp\":700,\"cls\":0.1}]");
    }

    @Test
    void finalReadReplacesAnEarlierFlushOfTheSamePage() {
        PageVitals p = new PageVitals();
        p.add("{\"id\":\"f\",\"url\":\"https://a/\",\"ts\":10,\"lcp\":100}");
        assertThat(p.toJson("{\"id\":\"f\",\"url\":\"https://a/\",\"lcp\":300}", 50)).isEqualTo("[{\"url\":\"https://a/\",\"ts\":50,\"lcp\":300}]");
        assertThat(new PageVitals().toJson(null, 1)).isEqualTo("[]");
        assertThat(new PageVitals().toJson("{\"lcp\":5}", 7)).isEqualTo("[{\"url\":null,\"ts\":7,\"lcp\":5}]");   // no preload: final only
    }
}
