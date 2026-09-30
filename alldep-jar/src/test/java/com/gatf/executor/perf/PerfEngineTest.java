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
package com.gatf.executor.perf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatf.executor.core.AcceptanceTestContext;
import com.gatf.executor.core.PerformanceConfig;
import com.gatf.executor.core.TestCase;
import com.gatf.executor.report.TestCaseReport;

/** M13: perf-engine registry, one PerformanceConfig on every provider, common result keys. */
class PerfEngineTest {

    @TempDir
    File tmp;

    /** Stand-ins for the built-ins (the real ones need the executors) and a third-party engine found by ServiceLoader. */
    public static class Fake implements PerfEngine {
        final String n;
        public Fake() { this("acme"); }
        Fake(String n) { this.n = n; }
        public String name() { return n; }
        public void execute(AcceptanceTestContext c, TestCase tc, TestCaseReport tcr, boolean s) {}
    }

    public static class Clash extends Fake {
        public Clash() { super("wrk"); }
    }

    public static class BadName extends Fake {
        public BadName() { super("Acme Load"); }
    }

    private static Map<String, PerfEngine> builtins() {
        Map<String, PerfEngine> b = new LinkedHashMap<String, PerfEngine>();
        for (String n : new String[] { "vegeta", "autocannon", "wrk", "wrk2", "wperfk" }) b.put(n, new Fake(n));
        return b;
    }

    @Test
    void thirdPartyEnginesComeFromThePluginsLoaderAndCannotReplaceABuiltin() throws Exception {
        File svc = new File(tmp, "META-INF/services/" + PerfEngine.class.getName());
        svc.getParentFile().mkdirs();
        Files.write(svc.toPath(), (Fake.class.getName() + "\n" + Clash.class.getName() + "\n" + BadName.class.getName()
                + "\nacme.Missing\n").getBytes(StandardCharsets.UTF_8));
        List<String> warnings = new ArrayList<String>();
        try (URLClassLoader l = new URLClassLoader(new URL[] { tmp.toURI().toURL() }, getClass().getClassLoader())) {
            Map<String, PerfEngine> all = PerfEngineRegistry.all(builtins(), l, warnings);
            assertThat(new ArrayList<String>(all.keySet())).containsExactly("vegeta", "autocannon", "wrk", "wrk2", "wperfk", "acme");
            assertThat(all.get("wrk") instanceof Clash).isFalse();
            assertThat(PerfEngineRegistry.resolve("acme", all)).isInstanceOf(Fake.class);
            assertThatThrownBy(() -> PerfEngineRegistry.resolve("vegata", all)).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("'vegata'").hasMessageContaining("vegeta, autocannon, wrk, wrk2, wperfk, acme (or none)");
            assertThatThrownBy(() -> PerfEngineRegistry.resolve("Wrk", all)).isInstanceOf(IllegalArgumentException.class);   // exact, as before
            assertThatThrownBy(() -> PerfEngineRegistry.resolve(null, all)).isInstanceOf(IllegalArgumentException.class);
        }
        String w = String.join("\n", warnings);
        assertThat(w).contains("'wrk'").contains("keeping the built-in").contains("invalid perf engine name 'Acme Load'").contains("not loaded");
        assertThat(PerfEngineRegistry.all(builtins(), null, null).keySet()).containsExactly("vegeta", "autocannon", "wrk", "wrk2", "wperfk");
    }

    @Test
    void theSamePerfConfigMapsOntoEveryProvider() {
        PerformanceConfig pc = new PerformanceConfig();
        pc.setConnections(20);
        pc.setThreads(4);
        pc.setDurationSeconds(30);
        pc.setRate(500);
        pc.setTimeout(5);
        pc.setLatency(true);

        assertThat(PerfArgs.wrk("wrk", pc)).containsExactly("-c", "20", "-d", "30s", "-t", "4", "--timeout", "5s", "--latency");
        assertThat(PerfArgs.wrk("wrk2", pc)).containsExactly("-c", "20", "-R", "500", "-d", "30s", "-t", "4", "--timeout", "5s", "--latency", "--u_latency");
        assertThat(PerfArgs.wrk("wperfk", pc)).containsExactly("-c", "20", "-R", "500", "-d", "30s", "-t", "4", "--timeout", "5s", "--latency");
        assertThat(PerfArgs.autocannon("POST", "http://h/p", null, null, pc.getConnections(), pc.getThreads(), pc.getRate(),
                PerfArgs.duration(pc), pc.getTimeout())).containsExactly("--json", "--no-progress", "-c", "20", "-w", "4", "-R", "500",
                "-d", "30", "-t", "5", "-m", "POST", "http://h/p");
        // vegeta's argv comes from VegetaRunner.attackArgs (LoadToolsTest): the same connections / rate / duration / timeout

        PerformanceConfig none = new PerformanceConfig();
        assertThat(PerfArgs.wrk("wrk", none)).containsExactly("-c", "10", "-d", "10s", "-t", "10");
        assertThat(PerfArgs.wrk("wrk2", none)).containsExactly("-c", "10", "-R", "1000", "-d", "10s", "-t", "10", "--u_latency");
        assertThat(PerfArgs.wrk("wperfk", none)).containsExactly("-c", "10", "-d", "10s", "-t", "10");   // closed loop without a rate
    }

    @Test
    void generatedLuaIsEscapedAndKeepsTheBodyExactly() {
        Map<String, String> h = new LinkedHashMap<String, String>();
        h.put("Content-Type", "application/json");
        h.put("X-Q", "a \"b\" \\ c");
        String lua = PerfArgs.lua("post", h, "{\"a\":\"]]\"}\r\nx]=");
        assertThat(lua).isEqualTo("wrk.method = \"POST\"\n"
                + "wrk.headers[\"Content-Type\"] = \"application/json\"\n"
                + "wrk.headers[\"X-Q\"] = \"a \\\"b\\\" \\\\ c\"\n"
                + "wrk.body = \"{\\\"a\\\":\\\"]]\\\"}\\r\\nx]=\"\n");   // CRLF kept, no bracket to close early
        assertThat(PerfArgs.luaStr("\0" + "1")).isEqualTo("\\0001");   // NUL then a digit stays two characters
        assertThat(PerfArgs.lua(null, null, null)).isEqualTo("wrk.method = \"GET\"\n");
    }

    @Test
    @SuppressWarnings("unchecked")
    void everyToolsResultGetsTheSameCommonKeys() throws Exception {
        ObjectMapper om = new ObjectMapper();
        // what WrkTestCaseExecutor.parse_wrk_output yields for a --latency run (BigDecimal ms / counts)
        Map<String, Object> wrk = new LinkedHashMap<String, Object>();
        wrk.put("tool", "wperfk");
        wrk.put("lat_avg", new java.math.BigDecimal("2.50"));
        wrk.put("lat_max", new java.math.BigDecimal("40.00"));
        wrk.put("lat_dis_50", new java.math.BigDecimal("2.00"));
        wrk.put("lat_dis_90", new java.math.BigDecimal("4.00"));
        wrk.put("lat_dis_99", new java.math.BigDecimal("9.00"));
        wrk.put("tot_requests", new java.math.BigDecimal("12000.00"));
        wrk.put("req_sec_tot", new java.math.BigDecimal("400.00"));
        wrk.put("err_connect", 0);
        wrk.put("err_read", new java.math.BigDecimal("2.00"));
        wrk.put("err_write", 0);
        wrk.put("err_timeout", new java.math.BigDecimal("1.00"));
        wrk.put("non_2xx_resp", 7L);
        Map<String, Object> vegeta = om.readValue("{\"latencies\":{\"total\":1,\"mean\":2500000,\"50th\":2000000,\"90th\":4000000,"
                + "\"95th\":5000000,\"99th\":9000000,\"max\":40000000,\"min\":1000},\"requests\":12000,\"rate\":400.1,\"throughput\":399.2,\"duration\":29900000000,\"wait\":100000000,"
                + "\"success\":0.99,\"status_codes\":{\"0\":3,\"200\":11990,\"500\":7},\"errors\":[\"dial tcp: refused\"],\"tool\":\"vegeta\"}", Map.class);
        Map<String, Object> ac = om.readValue("{\"latency\":{\"average\":2.5,\"p50\":2,\"p90\":4,\"p99\":9,\"max\":40},"
                + "\"requests\":{\"average\":400,\"total\":12000},\"errors\":3,\"timeouts\":1,\"non2xx\":9,"
                + "\"1xx\":0,\"2xx\":11988,\"3xx\":2,\"4xx\":4,\"5xx\":3,\"tool\":\"autocannon\"}", Map.class);
        List<Map<String, Object>> all = new ArrayList<Map<String, Object>>(List.of(wrk, vegeta, ac));
        PerfSummary.normalizeAll("ignored", all);
        for (Map<String, Object> r : all) {
            assertThat(r.keySet()).contains(PerfSummary.KEYS);
            assertThat(((Number) r.get("total_requests")).longValue()).isEqualTo(12000L);
            assertThat(((Number) r.get("lat_avg_ms")).doubleValue()).isEqualTo(2.5);
            assertThat(((Number) r.get("lat_p50_ms")).doubleValue()).isEqualTo(2.0);
            assertThat(((Number) r.get("lat_p90_ms")).doubleValue()).isEqualTo(4.0);
            assertThat(((Number) r.get("lat_p99_ms")).doubleValue()).isEqualTo(9.0);
            assertThat(((Number) r.get("lat_max_ms")).doubleValue()).isEqualTo(40.0);
            assertThat(((Number) r.get("transport_errors")).longValue()).isEqualTo(3L);
            assertThat(((Number) r.get("non_2xx")).longValue()).isEqualTo(7L);
            assertThat(((Number) r.get("rps")).doubleValue()).isBetween(399.0, 400.5);
        }
        assertThat(wrk.get("tool")).isEqualTo("wperfk");
        assertThat(vegeta.get("errors")).isInstanceOf(List.class);             // the tool's own keys are untouched
        assertThat(ac.get("requests")).isInstanceOf(Map.class);

        // an engine that reports the common keys itself keeps them; the tool name defaults to the engine
        Map<String, Object> own = new LinkedHashMap<String, Object>();
        own.put("rps", 1.5);
        PerfSummary.normalize("acme", own);
        assertThat(own).containsEntry("tool", "acme").containsEntry("rps", 1.5).hasSize(2);
        List<Map<String, Object>> frozen = new ArrayList<Map<String, Object>>();
        frozen.add(Map.of("rps", 2.0));
        PerfSummary.normalizeAll("acme", frozen);   // an immutable map is left alone, not an error
    }
}
