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
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatf.ai.AiClient;
import com.gatf.ai.heal.HealFixtures;

/**
 * H1 (Δ150) R1: the MCP {@code heal} tool through the real JSON-RPC server: schema, a heal of a recorded failure,
 * path confinement (traversal, absolute, symlinks), bad input as tool errors, and no write anywhere.
 */
public class HealToolTest {

    final ObjectMapper om = new ObjectMapper();
    final List<String> configs = new ArrayList<String>();

    McpServer server(Path root) {
        McpServer s = new McpServer("gatf", "test", "hint");
        s.register(new HealTool(root, om, cfg -> {
            configs.add(String.valueOf(cfg));
            return AiClient.notConfigured("no <ai> block in the config");
        }).tool());
        return s;
    }

    JsonNode call(McpServer s, Map<String, Object> args) throws Exception {
        Map<String, Object> params = new LinkedHashMap<String, Object>();
        params.put("name", "heal");
        params.put("arguments", args);
        Map<String, Object> req = new LinkedHashMap<String, Object>();
        req.put("jsonrpc", "2.0");
        req.put("id", 7);
        req.put("method", "tools/call");
        req.put("params", params);
        return om.readTree(s.handleLine(om.writeValueAsString(req))).path("result");
    }

    static Map<String, Object> args(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    static Path project(Path dir) throws Exception {
        Path root = Files.createDirectories(dir.resolve("proj"));
        HealFixtures.record(Files.createDirectories(root.resolve("out")));
        Files.write(root.resolve("checkout.sel"), (String.join("\n", HealFixtures.SCRIPT) + "\n").getBytes(StandardCharsets.UTF_8));
        return root;
    }

    /** path → sha-256 of every file under {@code d} (symlinks not followed). */
    static Map<String, String> snapshot(Path d) throws Exception {
        Map<String, String> m = new TreeMap<String, String>();
        try (Stream<Path> s = Files.walk(d)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                if (!Files.isRegularFile(p, java.nio.file.LinkOption.NOFOLLOW_LINKS)) continue;
                byte[] h = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p));
                m.put(d.relativize(p).toString(), java.util.HexFormat.of().formatHex(h));
            }
        }
        return m;
    }

    @Test
    void theToolIsListedWithItsSchema(@TempDir Path dir) throws Exception {
        McpServer s = server(project(dir));
        JsonNode r = om.readTree(s.handleLine("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}"));
        JsonNode heal = null;
        for (JsonNode t : r.at("/result/tools")) if (t.path("name").asText().equals("heal")) heal = t;
        assertTrue(heal != null, r.toString());
        JsonNode sch = heal.path("inputSchema");
        assertEquals("object", sch.path("type").asText());
        assertEquals("[\"trace\"]", sch.path("required").toString());
        for (String k : new String[] { "trace", "step", "file", "config" }) assertEquals("string", sch.at("/properties/" + k + "/type").asText(), k);
        assertTrue(heal.path("description").asText().contains("Never writes"));
    }

    @Test
    void healsARecordedFailureAndWritesNothing(@TempDir Path dir) throws Exception {
        Path root = project(dir);
        McpServer s = server(root);
        Map<String, String> before = snapshot(dir);
        JsonNode r = call(s, args("trace", "out/checkout-trace.zip"));
        assertFalse(r.path("isError").asBoolean(), r.toString());
        JsonNode h = r.path("structuredContent");
        assertEquals("proposed", h.path("status").asText());
        assertEquals("checkout.sel", h.path("file").asText());
        assertEquals("trace", h.path("fileSource").asText());
        assertEquals(5, h.path("line").asInt());
        assertEquals("recorded-failure", h.path("stepSource").asText());
        assertEquals("out/checkout-trace.zip", h.path("trace").asText());
        assertEquals("not-configured", h.path("ai").asText());
        assertEquals("AI not configured: no <ai> block in the config", h.path("aiStatus").asText());
        JsonNode top = h.path("proposals").get(0);
        assertEquals(1, top.path("rank").asInt());
        assertEquals("deterministic", top.path("source").asText());
        assertEquals("[\"click testid@place-order-btn\"]", top.path("lines").toString());
        assertTrue(top.path("strict").asBoolean());
        assertTrue(top.path("diff").asText().contains("\n-click id@place-order\n+click testid@place-order-btn\n"), top.path("diff").asText());
        assertTrue(r.at("/content/0/text").asText().contains("testid@place-order-btn"));

        // absolute inside the root, an unzipped trace directory, step by line and by text: same answer
        assertEquals("proposed", call(s, args("trace", root.resolve("out/checkout-trace.zip").toString(), "step", "5")).at("/structuredContent/status").asText());
        assertEquals(5, call(s, args("trace", "out/checkout-trace.zip", "step", "click id@place-order")).at("/structuredContent/line").asInt());
        assertEquals("null", String.valueOf(configs.get(0)));
        call(s, args("trace", "out/checkout-trace.zip", "config", "checkout.sel"));
        assertEquals("checkout.sel", configs.get(configs.size() - 1), "the config argument reaches the AI factory");
        assertEquals(before, snapshot(dir), "heal never writes, anywhere");
    }

    @Test
    void fileWinsAndTheReplyNamesTheFileUsed(@TempDir Path dir) throws Exception {
        Path root = project(dir);
        Files.createDirectories(root.resolve("ui"));
        Files.write(root.resolve("ui/other.sel"), "open chrome\n\nclick id@place-order\n".getBytes(StandardCharsets.UTF_8));
        JsonNode h = call(server(root), args("trace", "out/checkout-trace.zip", "file", "ui/other.sel")).path("structuredContent");
        assertEquals("ui/other.sel", h.path("file").asText());
        assertEquals("argument", h.path("fileSource").asText());
        assertEquals(3, h.path("line").asInt());

        // the trace's script missing: the step text still heals, without a file
        Files.delete(root.resolve("checkout.sel"));
        JsonNode n = call(server(root), args("trace", "out/checkout-trace.zip", "step", "click id@place-order")).path("structuredContent");
        assertEquals("proposed", n.path("status").asText());
        assertTrue(n.path("file").isNull());
        JsonNode e = call(server(root), args("trace", "out/checkout-trace.zip"));
        assertTrue(e.path("isError").asBoolean());
        assertTrue(e.at("/content/0/text").asText().contains("pass file"), e.toString());
    }

    @Test
    void pathsAreConfinedToTheProjectRoot(@TempDir Path dir) throws Exception {
        Path root = project(dir);
        Path outside = Files.createDirectories(dir.resolve("outside"));
        Path foreign = HealFixtures.record(outside);
        Files.write(outside.resolve("evil.sel"), "click id@place-order\n".getBytes(StandardCharsets.UTF_8));
        Files.createSymbolicLink(root.resolve("link-trace.zip"), foreign);
        Files.createSymbolicLink(root.resolve("linkdir"), outside);
        Files.createSymbolicLink(root.resolve("evil.sel"), outside.resolve("evil.sel"));
        McpServer s = server(root);
        for (String t : new String[] { "../outside/checkout-trace.zip", foreign.toString(), "link-trace.zip", "linkdir/checkout-trace.zip",
                "out/../../outside/checkout-trace.zip", "out/none-trace.zip", "a\0b" }) {
            JsonNode r = call(s, args("trace", t));
            assertTrue(r.path("isError").asBoolean(), t + " → " + r);
            assertTrue(r.at("/content/0/text").asText().contains("inside the project root"), r.toString());
        }
        for (String f : new String[] { "../outside/evil.sel", outside.resolve("evil.sel").toString(), "evil.sel", "linkdir/evil.sel", "out/checkout-trace.zip" }) {
            JsonNode r = call(s, args("trace", "out/checkout-trace.zip", "file", f));
            assertTrue(r.path("isError").asBoolean(), f + " → " + r);
        }
        assertTrue(call(s, args("trace", "out/checkout-trace.zip", "config", "../outside/evil.sel")).path("isError").asBoolean());
    }

    @Test
    void badInputIsAToolError(@TempDir Path dir) throws Exception {
        Path root = project(dir);
        Files.write(root.resolve("junk-trace.zip"), "not a zip".getBytes(StandardCharsets.UTF_8));
        Path pass = Files.createDirectories(root.resolve("out/pass-trace"));
        Files.write(pass.resolve("events.ndjson"), "{\"type\":\"action\",\"name\":\"click\"}\n".getBytes(StandardCharsets.UTF_8));
        McpServer s = server(root);
        String[][] cases = {
                { null, null, "missing required argument 'trace'" },
                { "junk-trace.zip", null, "cannot read the trace" },
                { "out/checkout-trace.zip", "99", "not a line of checkout.sel" },
                { "out/checkout-trace.zip", "click id@nowhere", "step not found" },
                { "out/pass-trace", null, "no recorded failure" },
        };
        for (String[] c : cases) {
            Map<String, Object> a = args();
            if (c[0] != null) a.put("trace", c[0]);
            if (c[1] != null) a.put("step", c[1]);
            JsonNode r = call(s, a);
            assertTrue(r.path("isError").asBoolean(), r.toString());
            assertTrue(r.at("/content/0/text").asText().contains(c[2]), c[2] + " ← " + r);
        }
        JsonNode np = call(s, args("trace", "out/pass-trace", "file", "checkout.sel", "step", "5"));
        assertFalse(np.path("isError").asBoolean());
        assertEquals("no-snapshot", np.at("/structuredContent/status").asText());
    }
}
