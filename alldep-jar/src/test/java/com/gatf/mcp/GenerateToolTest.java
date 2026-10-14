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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatf.ai.AiClient;
import com.gatf.ai.gen.GenFixtures;

/** H1 (Δ152): the MCP {@code generate} tool: writes nothing in the project, confined to the root, AI off on request. */
public class GenerateToolTest {

    static final ObjectMapper OM = new ObjectMapper();

    static JsonNode call(McpServer s, String args) throws Exception {
        return OM.readTree(s.handleLine("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"generate\",\"arguments\":" + args + "}}"));
    }

    static String text(JsonNode r) {
        return r.at("/result/content/0/text").asText();
    }

    static Set<String> tree(Path dir) throws Exception {
        try (Stream<Path> w = Files.walk(dir)) {
            return w.map(p -> dir.relativize(p).toString()).collect(Collectors.toCollection(TreeSet::new));
        }
    }

    @Test
    void generatesWithoutWritingAndStaysInTheRoot(@TempDir Path dir) throws Exception {
        Path root = Files.createDirectories(dir.resolve("proj"));
        Files.createDirectories(root.resolve("tests"));
        Files.write(root.resolve("tests/shop-plan.json"), GenFixtures.shopPlanJson().getBytes(StandardCharsets.UTF_8));
        Files.write(dir.resolve("outside-plan.json"), GenFixtures.shopPlanJson().getBytes(StandardCharsets.UTF_8));
        Files.write(root.resolve("tests/crawl.json"), "[]".getBytes(StandardCharsets.UTF_8));
        Files.write(root.resolve("gatf-config.json"), "{}".getBytes(StandardCharsets.UTF_8));
        List<String> configs = new ArrayList<String>();
        List<Path> gateConfigs = new ArrayList<Path>();
        McpServer s = new McpServer("gatf", "t", "h");
        s.register(new GenerateTool(root, OM, c -> { configs.add(String.valueOf(c)); return AiClient.notConfigured("test"); },
                (r, c) -> { gateConfigs.add(c); return new GenFixtures.FakeGate(); }).tool());
        Set<String> before = tree(dir);

        JsonNode r = call(s, "{\"plan\":\"tests/shop-plan.json\"}");
        assertFalse(r.at("/result/isError").asBoolean(), r.toString());
        JsonNode out = OM.readTree(text(r));
        assertEquals(7, out.at("/counts/passed").asInt(), out.toString());
        assertEquals("ok", out.at("/dryRun").asText());
        assertTrue(out.at("/files/0/text").asText().contains("open chrome"));
        assertEquals("tests/generated/shop", out.at("/suggestedDir").asText());
        assertTrue(out.at("/writes").asText().startsWith("nothing in the project"));
        assertEquals(2, out.at("/sideEffectJourneys").size());
        assertEquals(List.of("null"), configs, "AI on by default, default config");
        assertEquals("gatf-config.json", gateConfigs.get(0).getFileName().toString());

        JsonNode all = OM.readTree(text(call(s, "{\"plan\":\"tests/shop-plan.json\",\"ai\":false,\"includeSideEffects\":true}")));
        assertEquals(9, all.at("/counts/scripts").asInt());
        assertTrue(all.at("/ai/status").asText().startsWith("off"));
        assertEquals(1, configs.size(), "ai:false opens no client");

        for (String bad : new String[] { "{\"plan\":\"../outside-plan.json\"}", "{\"plan\":\"" + dir.resolve("outside-plan.json").toString().replace("\\", "\\\\") + "\"}",
                "{\"plan\":\"tests/missing-plan.json\"}", "{\"plan\":\"tests\"}", "{}", "{\"plan\":\"tests/crawl.json\"}",
                "{\"plan\":\"tests/shop-plan.json\",\"config\":\"../x.json\"}", "{\"plan\":\"tests/shop-plan.json\",\"config\":\"nope.json\"}" }) {
            JsonNode e = call(s, bad);
            assertTrue(e.at("/result/isError").asBoolean() || e.has("error"), bad + " → " + e);
        }
        assertTrue(text(call(s, "{\"plan\":\"tests/crawl.json\"}")).contains("not a test plan"));
        assertEquals(before, tree(dir), "the tool writes nothing in the project");

        Files.delete(root.resolve("gatf-config.json"));
        assertTrue(text(call(s, "{\"plan\":\"tests/shop-plan.json\"}")).contains("no config"));
    }
}
