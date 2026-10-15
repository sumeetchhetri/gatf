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
package com.gatf.imports;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatf.executor.core.TestCase;

/** M17: a functional run's network.har → a load test of the same flow. */
class FlowToLoadTest {

    private static String res(String name) throws Exception {
        try (InputStream in = FlowToLoadTest.class.getResourceAsStream("/imports/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void everyFlowStepBecomesALoadCaseWithTheHarFiltering() throws Exception {
        FlowToLoad.Options o = FlowToLoad.Options.of("25", "60s", "", "vegeta");
        ImportResult r = FlowToLoad.convert(res("recording.har"), "checkout", null, o);
        assertEquals("flow", r.type);
        assertEquals("checkout-load.xml", r.fileName);
        // the HAR importer's filtering: no script, no preflight; same names as a plain HAR import
        assertEquals(Arrays.asList("GET /", "POST /login", "GET /items", "POST /form", "POST /upload", "GET /p"), FlowToLoad.stepNames(r));
        for (TestCase tc : r.testCases) {
            assertEquals("vegeta", tc.getPerfConfig().getType());
            assertEquals(25, tc.getPerfConfig().getConnections());
            assertEquals(60, tc.getPerfConfig().getDurationSeconds());
            assertEquals(0, tc.getPerfConfig().getRate());
            assertEquals(FlowToLoad.CLOSED_LOOP, tc.getPerfConfig().getExtras());   // 25 concurrent users, not 50 req/s
        }
        assertTrue(r.testCases.get(1).getDescription().endsWith("load step 2 of 6"), r.testCases.get(1).getDescription());
        assertFalse(r.testCases.get(1).getHeaders().containsKey("Cookie"));
        assertEquals(Boolean.TRUE, r.loadConfig.get("loadTestingEnabled"));
        assertEquals(25, r.loadConfig.get("concurrentUserSimulationNum"));
        assertEquals(60000L, r.loadConfig.get("loadTestingTime"));
    }

    @Test
    void theWholeFlowIsAVegetaJsonTargetsFile() throws Exception {
        ImportResult r = FlowToLoad.convert(res("recording.har"), "checkout", null, null);
        // Δ163m: wperfk is the default engine; its closed loop of N connections needs no vegeta-style extras
        for (TestCase tc : r.testCases) {
            assertEquals("wperfk", tc.getPerfConfig().getType());
            assertEquals(null, tc.getPerfConfig().getExtras());
        }
        String t = r.sideFiles.get(FlowToLoad.TARGETS_SUFFIX);
        List<JsonNode> lines = new ArrayList<JsonNode>();
        ObjectMapper om = new ObjectMapper();
        for (String l : t.split("\n")) if (!l.isEmpty()) lines.add(om.readTree(l));
        // the multipart upload stays a case but is not a vegeta target
        assertEquals(5, lines.size());
        assertEquals(5, r.stats.get("vegetaTargets"));
        assertTrue(r.unsupported.stream().anyMatch(u -> u.construct.equals("POST /upload") && u.reason.contains("multipart")));
        assertEquals("GET", lines.get(0).get("method").asText());
        assertEquals("https://app.test/", lines.get(0).get("url").asText());
        JsonNode login = lines.get(1);
        assertEquals("POST", login.get("method").asText());
        assertEquals("https://api.test/login", login.get("url").asText());
        assertTrue(login.path("header").has("Content-Type"));
        assertFalse(login.path("header").has("Cookie"));
        assertFalse(new String(Base64.getDecoder().decode(login.get("body").asText()), StandardCharsets.UTF_8).isEmpty());
        assertEquals("https://api.test/items?p=1", lines.get(2).get("url").asText());
        assertEquals("a=1+2", new String(Base64.getDecoder().decode(lines.get(3).get("body").asText()), StandardCharsets.UTF_8));
        assertEquals("checkout-load.targets.jsonl", ImportResult.sideFileName(r.fileName, FlowToLoad.TARGETS_SUFFIX));
        assertTrue(String.valueOf(r.stats.get("vegetaCommand")).startsWith("vegeta attack -format=json -targets=checkout-load.targets.jsonl -rate=0 -max-workers=10 -duration=30s"));
    }

    @Test
    void filterRateEngineAndErrors() throws Exception {
        ImportResult r = FlowToLoad.convert(res("recording.har"), "x-load", "api\\.test/", FlowToLoad.Options.of("5", "10", "50", "WRK2"));
        assertEquals("x-load.xml", r.fileName);   // no double suffix
        assertEquals(4, r.testCases.size());
        assertEquals("wrk2", r.testCases.get(0).getPerfConfig().getType());
        assertEquals(50, r.testCases.get(0).getPerfConfig().getRate());
        assertNull(r.testCases.get(0).getPerfConfig().getExtras());               // a rate: open loop, no closed=1
        java.util.Set<String> taken = new java.util.HashSet<String>(Arrays.asList("a-load.xml", "a-load-2.xml"));
        assertEquals("a-load-3.xml", FlowToLoad.freeName("a-load.xml", taken::contains));
        assertEquals("b-load.xml", FlowToLoad.freeName("b-load.xml", taken::contains));
        assertTrue(String.valueOf(r.stats.get("vegetaCommand")).contains("-rate=50"));
        for (String[] bad : new String[][] { { "0", null, null, null }, { null, "x", null, null }, { null, null, "-1", null }, { null, null, null, "jmeter" } }) {
            try {
                FlowToLoad.Options.of(bad[0], bad[1], bad[2], bad[3]);
                throw new AssertionError("expected a failure for " + Arrays.toString(bad));
            } catch (IllegalArgumentException expected) {
                assertFalse(expected.getMessage().isEmpty());
            }
        }
        try {
            FlowToLoad.convert(res("recording.har"), "x", "nothing-matches", null);
            throw new AssertionError("expected nothing to load-test");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("nothing to load-test"));
        }
        assertNull(FlowToLoad.fullUrl(new ApiRequest()));
    }

    @Test
    void registeredAsAnImportType() throws Exception {
        assertTrue(Importers.TYPES.contains("flow"));
        ImportResult r = Importers.importSource("flow", res("recording.har"), "rec", null);
        assertEquals("rec-load.xml", r.fileName);
        assertEquals(10, r.testCases.get(0).getPerfConfig().getConnections());
        assertEquals("a.targets.jsonl", ImportResult.sideFileName("dir/a.xml", ".targets.jsonl"));
        assertEquals("a.b.targets.jsonl", ImportResult.sideFileName("a.b.xml", ".targets.jsonl"));
    }
}
