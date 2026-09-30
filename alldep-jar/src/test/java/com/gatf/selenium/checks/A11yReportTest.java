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
package com.gatf.selenium.checks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * M10: axe result mapper on a canned result (resources/a11y/axe-result.json = the bundled axe-core 4.13.0 run through
 * A11yReport.RUN_JS on a small page with an unlabelled button, input and link and two images without alt).
 */
class A11yReportTest {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> canned() throws Exception {
        return new ObjectMapper().readValue(A11yReportTest.class.getResourceAsStream("/a11y/axe-result.json"), Map.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void violationsAreListedWithSelectors() throws Exception {
        Map<String, Object> r = A11yReport.map(canned(), null, "chrome");
        assertThat(r.get("failed")).isEqualTo(true);
        assertThat(r.get("url")).isEqualTo("https://shop.test/");
        List<Map<String, Object>> vs = (List<Map<String, Object>>) r.get("violations");
        assertThat(vs).extracting(v -> v.get("id")).containsExactly("button-name", "image-alt", "label", "link-name");   // critical first
        Map<String, Object> img = vs.get(1);
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) img.get("nodes");
        assertThat(nodes).extracting(n -> n.get("target")).containsExactly("img[src$=\"logo.png\"]", "img[src$=\"ad.png\"]");
        assertThat((String) nodes.get(0).get("html")).contains("logo.png");
        assertThat((String) nodes.get(0).get("summary")).contains("Fix any of the following");
        assertThat((String) img.get("helpUrl")).startsWith("https://dequeuniversity.com/rules/axe/");
        Map<String, Object> counts = (Map<String, Object>) r.get("counts");
        assertThat(counts.get("critical")).isEqualTo(3);
        assertThat(counts.get("serious")).isEqualTo(1);
        assertThat(r.get("passes")).isEqualTo(15);
        assertThat(A11yReport.summary(r)).isEqualTo("4 accessibility violation(s) (3 critical, 1 serious) on https://shop.test/");
        String msg = A11yReport.failureMessage(r, "/out/chrome-1-a11y.json");
        assertThat(msg).contains("[critical] button-name: Buttons must have discernible text at #buy")
                .contains("image-alt").contains("(+1)").contains("details: /out/chrome-1-a11y.json");
    }

    @Test
    @SuppressWarnings("unchecked")
    void impactThresholdFilters() throws Exception {
        Map<String, Object> critical = A11yReport.map(canned(), "critical", "chrome");
        assertThat((List<Object>) critical.get("violations")).hasSize(3);
        Map<String, Object> none = A11yReport.map(Map.of("url", "u", "violations", List.of(Map.of("id", "x", "impact", "minor", "nodes", List.of()))), "serious", "chrome");
        assertThat(none.get("failed")).isEqualTo(false);
        assertThat(A11yReport.summary(none)).isEqualTo("no accessibility violations (impact >= serious) on u");
        assertThatThrownBy(() -> A11yReport.normalizeImpact("severe")).hasMessageContaining("impact must be one of");
    }

    @Test
    void shadowAndFrameTargetsAreJoined() {
        assertThat(A11yReport.selector(List.of("iframe#pay", List.of("my-el", "button.x")))).isEqualTo("iframe#pay >>> my-el >>> button.x");
    }

    @Test
    void axeErrorsSurface() {
        assertThatThrownBy(() -> A11yReport.map(Map.of("error", "axe-core is not loaded"), null, "chrome")).hasMessageContaining("axe-core is not loaded");
    }

    @Test
    void includeExcludeLocatorsBecomeCss() {
        assertThat(A11yReport.cssOf("css@'.ad .banner'")).isEqualTo(".ad .banner");
        assertThat(A11yReport.cssOf("id@main")).isEqualTo("#main");
        assertThat(A11yReport.cssOf("id@'2col'")).isEqualTo("[id='2col']");
        assertThat(A11yReport.cssOf("main > form")).isEqualTo("main > form");
        assertThatThrownBy(() -> A11yReport.cssOf("xpath@//div")).hasMessageContaining("css@… or id@…");
    }

    @Test
    void theRunnerIsSelfContained() {
        // executeAsyncScript: args, then the callback; JSON back
        assertThat(A11yReport.RUN_JS).contains("arguments[arguments.length - 1]").contains("JSON.stringify").contains("axe.run(ctx");
    }
}
