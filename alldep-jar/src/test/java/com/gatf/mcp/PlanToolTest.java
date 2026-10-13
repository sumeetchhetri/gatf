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
package com.gatf.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** H1 (Δ151): the MCP {@code plan} tool: read-only, confined to the project root, AI off on request. */
public class PlanToolTest {

    static final ObjectMapper OM = new ObjectMapper();

    static JsonNode call(McpServer s, String args) throws Exception {
        return OM.readTree(s.handleLine("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"plan\",\"arguments\":" + args + "}}"));
    }

    static String text(JsonNode r) {
        return r.at("/result/content/0/text").asText();
    }

    @Test
    void plansReadOnlyAndConfined(@TempDir Path dir) throws Exception {
        Path root = Files.createDirectories(dir.resolve("proj"));
        Path tests = Files.createDirectories(root.resolve("tests"));
        byte[] crawl;
        try (InputStream in = PlanToolTest.class.getResourceAsStream("/plan/shop-coverage.json")) {
            crawl = in.readAllBytes();
        }
        Files.write(tests.resolve("shop-coverage.json"), crawl);
        Files.write(dir.resolve("outside-coverage.json"), crawl);
        Files.write(tests.resolve("notes.txt"), "x".getBytes(StandardCharsets.UTF_8));
        List<String> configs = new ArrayList<String>();
        McpServer s = new McpServer("gatf", "t", "h");
        s.register(new PlanTool(root, OM, c -> { configs.add(String.valueOf(c)); return com.gatf.ai.AiClient.notConfigured("test"); }).tool());

        JsonNode ok = call(s, "{\"crawl\":\"tests/shop-coverage.json\",\"maxRoutes\":3}");
        assertFalse(ok.at("/result/isError").asBoolean(), ok.toString());
        JsonNode r = OM.readTree(text(ok));
        assertEquals("gatf-test-plan", r.at("/plan/format").asText());
        assertEquals(3, r.at("/plan/routes").size());
        assertEquals(3, r.at("/plan/unplanned/count").asInt());
        assertTrue(r.path("markdown").isMissingNode(), "markdown only on request");
        assertEquals(List.of("null"), configs, "the default config");

        JsonNode off = OM.readTree(text(call(s, "{\"crawl\":\"tests/shop-coverage.json\",\"ai\":false,\"markdown\":true}")));
        assertTrue(off.at("/plan/ai/status").asText().startsWith("off"), off.toString());
        assertTrue(off.path("markdown").asText().startsWith("# Test plan: shop-coverage.json"));
        assertEquals(1, configs.size(), "AI off: no client opened");

        for (String bad : new String[] { "{\"crawl\":\"../outside-coverage.json\"}", "{\"crawl\":\"" + dir.resolve("outside-coverage.json").toString().replace("\\", "\\\\") + "\"}",
                "{\"crawl\":\"tests/notes.txt\"}", "{\"crawl\":\"tests/missing.json\"}", "{}",
                "{\"crawl\":\"tests/shop-coverage.json\",\"config\":\"../x.json\"}", "{\"crawl\":\"tests/shop-coverage.json\",\"coverageRun\":\"../\"}" }) {
            JsonNode e = call(s, bad);
            assertTrue(e.at("/result/isError").asBoolean(), bad + " → " + e);
        }
        try (Stream<Path> files = Files.walk(root)) {
            assertEquals(2L, files.filter(Files::isRegularFile).count(), "the tool never writes");
        }
    }
}
