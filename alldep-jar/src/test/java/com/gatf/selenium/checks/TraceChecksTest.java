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

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Q9/Q10 — budgets and passive security checks over a recorded trace (fixture: src/test/resources/checks). */
class TraceChecksTest {

    @TempDir
    Path dir;

    private Path trace() throws Exception {
        for (String f : new String[] { "performance.json", "network.har", "console.ndjson" }) {
            try (InputStream in = TraceChecksTest.class.getResourceAsStream("/checks/" + f)) {
                Files.copy(in, dir.resolve(f));
            }
        }
        return dir;
    }

    @Test
    void budgetSpecParsing() {
        List<PerfBudget.Rule> r = PerfBudget.parse("lcp<2500, requests <= 5 jsheap<50mb;transfer<2mb cls<0.1 load<2s");
        assertThat(r).extracting(x -> x.metric).containsExactly("lcp", "requests", "jsheap", "transfer", "cls", "load");
        assertThat(r.get(2).limit).isEqualTo(50.0 * 1024 * 1024);
        assertThat(r.get(5).limit).isEqualTo(2000.0);
        for (String bad : new String[] { "lcp>2500", "speed<3", "lcp<3mb", "cls<1ms", "requests<5kb" }) {
            assertThatThrownBy(() -> PerfBudget.parse(bad)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(PerfBudget.headerSpec(List.of("// smoke", "// @budget lcp<2000 requests<50", "open chrome", "// @budget fcp<1")))
                .isEqualTo("lcp<2000 requests<50");
        assertThat(PerfBudget.merge(PerfBudget.parse("lcp<2500,requests<80"), PerfBudget.parse("lcp<2000")))
                .extracting(x -> x.text).containsExactly("lcp<2000", "requests<80");
    }

    @Test
    void metricsFromTheTrace() throws Exception {
        Map<String, Double> m = PerfBudget.measure(trace(), new ObjectMapper());
        assertThat(m).containsEntry("lcp", 3120.7).containsEntry("jsheap", 62914560.0).containsEntry("requests", 6.0)
                .containsEntry("transfer", 42133.0).containsEntry("failedrequests", 1.0).containsEntry("consoleerrors", 1.0)
                .containsEntry("dcl", 800.0).containsEntry("cls", 0.02);
    }

    @Test
    void webVitalBudgetsUseTheWorstPageWhenPerNavigationVitalsExist() throws Exception {
        Path t = trace();
        Files.writeString(t.resolve("performance.json"), "{\"samples\":[],\"webVitals\":{\"lcp\":900,\"cls\":0.01,\"fcp\":400},"
                + "\"webVitalsByPage\":[{\"url\":\"https://a/\",\"ts\":1,\"lcp\":2800,\"cls\":0.3,\"fcp\":500},"
                + "{\"url\":\"https://a/b\",\"ts\":2,\"lcp\":1200},{\"url\":\"https://a/c\",\"ts\":3,\"lcp\":900,\"cls\":0.01,\"fcp\":400}]}");
        Map<String, Double> m = PerfBudget.measure(t, new ObjectMapper());
        assertThat(m).containsEntry("lcp", 2800.0).containsEntry("cls", 0.3).containsEntry("fcp", 500.0).doesNotContainKey("ttfb");
        assertThat(TraceChecks.run(t, "lcp<2500", List.of(), "off").failure()).startsWith("budget: lcp 2800ms exceeds budget lcp<2500");
        // a single page reads the same as the final-page webVitals (unchanged behaviour)
        Files.writeString(t.resolve("performance.json"), "{\"samples\":[],\"webVitals\":{\"lcp\":900},"
                + "\"webVitalsByPage\":[{\"url\":\"https://a/\",\"ts\":1,\"lcp\":900}]}");
        assertThat(PerfBudget.measure(t, new ObjectMapper())).containsEntry("lcp", 900.0);
    }

    @Test
    void securityFindingsPerPage() throws Exception {
        List<SecurityScan.Finding> f = SecurityScan.scan(trace(), new ObjectMapper());
        assertThat(f).extracting(x -> x.rule).contains("mixed-content", "missing-csp", "missing-hsts", "missing-clickjacking-protection",
                "missing-referrer-policy", "version-disclosure", "cookie-without-secure", "cookie-without-httponly",
                "cookie-samesite-none-insecure", "cors-wildcard-credentials", "csp-violation").doesNotContain("missing-nosniff");
        assertThat(f.get(0).severity).isEqualTo("high");
        assertThat(f).noneMatch(x -> x.message.contains("'uid'"))                 // third-party cookie
                .noneMatch(x -> x.page.equals("https://app.shop.com/secure"))      // fully hardened page
                .noneMatch(x -> x.message.contains("'prefs'") || x.message.contains("'old'"));
    }

    @Test
    void runFailsOnBreachOrSeriousFindingsAndWritesChecksJson() throws Exception {
        Path t = trace();
        TraceChecks.Report rep = TraceChecks.run(t, "lcp<2500,requests<80", List.of("// @budget requests<5"), null);
        assertThat(rep.failure()).startsWith("budget: lcp 3121ms exceeds budget lcp<2500; budget: requests 6 exceeds budget requests<5");
        assertThat(t.resolve("checks.json")).exists();
        assertThat(TraceChecks.run(t, null, List.of(), null).failure()).isNull();                  // report mode
        assertThat(TraceChecks.run(t, null, List.of(), "fail").failure()).startsWith("security: ");
        assertThat(TraceChecks.run(t, null, List.of(), "off")).isNull();
        assertThat(TraceChecks.run(t, null, List.of("// @budget lcp>1"), "off").failure()).startsWith("invalid @budget");
    }

    @Test
    void unmeasuredMetricWarnsButDoesNotFail() {
        TraceChecks.Report rep = TraceChecks.run(dir, "lcp<1", List.of(), "off");
        assertThat(rep.budgets.get(0).outcome).isEqualTo(PerfBudget.Outcome.NOT_MEASURED);
        assertThat(rep.failure()).isNull();
    }

    @Test
    void configKeysAreValidated() {
        com.gatf.executor.core.GatfExecutorConfig c = new com.gatf.executor.core.GatfExecutorConfig();
        c.setPerfBudgets(" lcp<2500 ");
        c.setSecurityChecks("FAIL");
        assertThat(c.getPerfBudgets()).isEqualTo("lcp<2500");
        assertThat(c.getSecurityChecks()).isEqualTo("fail");
        assertThatThrownBy(() -> c.setPerfBudgets("speed<1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> c.setSecurityChecks("block")).isInstanceOf(IllegalArgumentException.class);
    }
}
