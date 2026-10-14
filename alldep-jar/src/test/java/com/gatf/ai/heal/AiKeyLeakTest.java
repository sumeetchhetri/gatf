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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatf.ai.AiClient;
import com.gatf.ai.AiConfig;
import com.gatf.ai.AiFakes;
import com.gatf.ai.AiSecret;
import com.gatf.mcp.HealTool;
import com.gatf.mcp.McpServer;

/**
 * H1 (Δ149) secrets: a provider key read from the environment never reaches a heal result, an error, stdout/stderr,
 * the serialized config, or any file in the trace — even when the provider echoes it back in an error or a reply.
 * Δ151: nor a test plan (JSON, Markdown, notes, AI summary) or the MCP {@code plan} reply.
 * Δ152: nor a generated script, the {@code gatf generate} summary or console, the MCP {@code generate} reply, or a
 * repair request body.
 */
public class AiKeyLeakTest {

    static final String KEY = "sk-ant-LEAKCHECK-0123456789abcdefghijKLMNOP";

    /** The key, or any 12-char slice of it, in {@code text}. */
    static List<String> leaks(String where, String text) {
        List<String> out = new ArrayList<String>();
        if (text == null) return out;
        for (int i = 0; i + 12 <= KEY.length(); i++) {
            if (text.contains(KEY.substring(i, i + 12))) { out.add(where + " holds key slice @" + i); break; }
        }
        return out;
    }

    @Test
    void theKeyNeverLeavesTheProviderHeader(@TempDir Path dir) throws Exception {
        ObjectMapper om = new ObjectMapper();
        Path zip = RecordedFailure.record(dir);
        AiConfig cfg = AiFakes.config();
        cfg.setApiKeyEnv("FAKE_AI_KEY");
        cfg.setRetries(0);
        List<String> found = new ArrayList<String>();
        PrintStream out = System.out, err = System.err;
        ByteArrayOutputStream cap = new ByteArrayOutputStream();
        List<String> results = new ArrayList<String>();
        AiFakes.Sender sender = new AiFakes.Sender()
                .then(AiFakes.status(401, "{\"error\":{\"message\":\"bad key " + KEY + "\"}}"))
                .then(AiFakes.io("tunnel failed, header x-fake-key: " + KEY))
                .then(AiFakes.ok(AiFakes.reply("{\"candidates\":[{\"kind\":\"label\",\"value\":\"" + KEY.substring(0, 30) + "\"},{\"kind\":\"label\",\"value\":\"City\"}]} "
                        + KEY, 10, 10)));
        try {
            System.setOut(new PrintStream(cap, true, "UTF-8"));
            System.setErr(new PrintStream(cap, true, "UTF-8"));
            Healer.Options o = new Healer.Options();
            o.ai = Healer.AiMode.ALWAYS;
            for (int i = 0; i < 3; i++) {
                AiClient ai = AiFakes.client(cfg, KEY, sender);
                found.addAll(leaks("status", ai.status() + ai + ai.usage()));
                Map<String, Object> r = new Healer(om).heal("click id@place-order", RecordedFailure.SCRIPT, 5, "checkout.sel",
                        TraceFailure.read(zip, om), ai, o);
                String json = om.writeValueAsString(r);
                results.add(json);
                System.out.println(json);
            }
        } finally {
            System.setOut(out);
            System.setErr(err);
        }
        assertEquals(3, sender.urls.size());
        for (int i = 0; i < 3; i++) {
            assertEquals(KEY, sender.headers.get(i).get("x-fake-key"), "the header is the one place it goes");
            found.addAll(leaks("request body " + i, sender.bodies.get(i)));
            found.addAll(leaks("result " + i, results.get(i)));
        }
        assertTrue(results.get(0).contains("\"ai\":\"http\"") && results.get(0).contains("***"), results.get(0));
        assertTrue(results.get(1).contains("\"ai\":\"io\""), results.get(1));
        assertTrue(results.get(2).contains("label@City"), results.get(2));
        found.addAll(leaks("stdout/stderr", cap.toString(StandardCharsets.UTF_8)));

        // what Δ150 will persist next to the trace: the result JSON; plus the config as serialized with the run
        Files.write(dir.resolve("heal-result.json"), String.join("\n", results).getBytes(StandardCharsets.UTF_8));
        Files.write(dir.resolve("ai-config.json"), om.writeValueAsBytes(cfg));
        found.addAll(leaks("config toString", cfg.toString()));
        found.addAll(leaks("secret toString", String.valueOf(AiSecret.of(KEY))));
        try (Stream<Path> files = Files.walk(dir)) {
            for (Path p : (Iterable<Path>) files.filter(Files::isRegularFile)::iterator) {
                found.addAll(leaks(dir.relativize(p).toString(), new String(Files.readAllBytes(p), StandardCharsets.ISO_8859_1)));
                if (p.toString().endsWith(".zip")) {
                    try (ZipFile z = new ZipFile(p.toFile())) {
                        for (ZipEntry e : java.util.Collections.list(z.entries())) {
                            found.addAll(leaks(p.getFileName() + "!" + e.getName(), new String(z.getInputStream(e).readAllBytes(), StandardCharsets.ISO_8859_1)));
                        }
                    }
                }
            }
        }
        assertEquals(List.of(), found);
        assertFalse(om.writeValueAsString(cfg).toLowerCase().contains("apikey\":\"sk"), "the config has no key field to serialize");
    }
    /**
     * Δ150: the editor endpoints ({@link HealEndpoint}: heal + accept) and the MCP {@code heal} reply never carry the key,
     * with the key read from a relative {@code <apiKeyFile>} (resolved against the config's directory, not the working
     * directory) and a plugin provider found through the plugin class loader, even when the provider echoes the key.
     */
    @Test
    void healEndpointsAndTheMcpReplyNeverCarryTheKey(@TempDir Path dir) throws Exception {
        ObjectMapper om = new ObjectMapper();
        Path root = Files.createDirectories(dir.resolve("proj"));
        Path out = Files.createDirectories(root.resolve("out"));
        Path tests = Files.createDirectories(root.resolve("tests"));
        Path cfgDir = Files.createDirectories(root.resolve("configs"));
        Files.write(cfgDir.resolve("ai.key"), (KEY + "\n").getBytes(StandardCharsets.UTF_8));
        RecordedFailure.record(out);
        List<String> script = new ArrayList<String>(RecordedFailure.SCRIPT);
        script.add("type 'x' id@zzqqx-field");
        Files.write(tests.resolve("checkout.sel"), (String.join("\n", script) + "\n").getBytes(StandardCharsets.UTF_8));
        Path plug = Files.createDirectories(dir.resolve("plug/META-INF/services"));
        Files.write(plug.resolve("com.gatf.ai.AiProvider"), "com.gatf.ai.AiPluginProviderForTest\n".getBytes(StandardCharsets.UTF_8));
        java.net.URLClassLoader plugins = new java.net.URLClassLoader(new java.net.URL[] { dir.resolve("plug").toUri().toURL() }, getClass().getClassLoader());
        AiConfig cfg = AiFakes.config();
        cfg.setProvider("plugin-fake");
        cfg.setApiKeyFile("ai.key");   // relative: next to the config, not in the working directory
        cfg.setRetries(0);
        String reply = AiFakes.reply("{\"candidates\":[{\"kind\":\"label\",\"value\":\"City\"}]} " + KEY, 5, 5);
        AiFakes.Sender sender = new AiFakes.Sender()
                .then(AiFakes.status(401, "{\"error\":{\"message\":\"bad key " + KEY + "\"}}"))
                .then(AiFakes.io("proxy said x-fake-key: " + KEY))
                .then(AiFakes.ok(reply))
                .then(AiFakes.ok(reply));
        List<String> warnings = new ArrayList<String>();
        java.util.function.Supplier<AiClient> ai = () -> HealAi.open(cfg, cfgDir, plugins, n -> null, sender, warnings);
        List<String> found = new ArrayList<String>();
        List<String> replies = new ArrayList<String>();
        PrintStream sysOut = System.out, sysErr = System.err;
        ByteArrayOutputStream cap = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(cap, true, "UTF-8"));
            System.setErr(new PrintStream(cap, true, "UTF-8"));
            HealEndpoint ep = new HealEndpoint(out, List.of(tests), ai, c -> "{\"status\":\"SUCCESS\"}", new HealApply.Tickets(), om, null);
            Map<String, Object> body = new java.util.LinkedHashMap<String, Object>();
            body.put("trace", "/reports/checkout-trace.zip");
            body.put("step", "7");
            for (int i = 0; i < 3; i++) {
                HealEndpoint.Reply r = ep.heal(body);
                String json = om.writeValueAsString(r.body);
                replies.add(json);
                System.out.println(json);
            }
            assertTrue(replies.get(0).contains("\"ai\":\"http\"") && replies.get(0).contains("***"), replies.get(0));
            assertTrue(replies.get(1).contains("\"ai\":\"io\""), replies.get(1));
            assertTrue(replies.get(2).contains("ready: plugin-fake / fake-model-1") && replies.get(2).contains("label@City"), replies.get(2));
            McpServer s = new McpServer("gatf", "t", "h");
            s.register(new HealTool(root, om, c -> ai.get()).tool());
            String rpc = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"heal\",\"arguments\":"
                    + "{\"trace\":\"out/checkout-trace.zip\",\"file\":\"tests/checkout.sel\",\"step\":\"7\"}}}";
            String mcp = s.handleLine(rpc);
            replies.add(mcp);
            assertTrue(mcp.contains("label@City") && mcp.contains("\"isError\":false"), mcp);
            @SuppressWarnings("unchecked")
            Map<String, Object> healed = om.readValue(replies.get(2), Map.class);
            Map<String, Object> apply = new java.util.LinkedHashMap<String, Object>();
            apply.put("healId", healed.get("healId"));
            apply.put("rank", 1);
            HealEndpoint.Reply a = ep.apply(apply);
            assertEquals(200, a.status, String.valueOf(a.body));
            replies.add(om.writeValueAsString(a.body));

        } finally {
            System.setOut(sysOut);
            System.setErr(sysErr);
        }
        assertEquals(4, sender.urls.size(), "every heal asked the plugin provider");
        for (int i = 0; i < sender.headers.size(); i++) assertEquals(KEY, sender.headers.get(i).get("x-fake-key"), "the key came from configs/ai.key");
        for (int i = 0; i < replies.size(); i++) found.addAll(leaks("reply " + i, replies.get(i)));
        found.addAll(leaks("stdout/stderr", cap.toString(StandardCharsets.UTF_8)));
        found.addAll(leaks("warnings", String.join("\n", warnings)));
        try (Stream<Path> files = Files.walk(root)) {   // the healed script, the trace: nothing on disk but the key file
            for (Path p : (Iterable<Path>) files.filter(Files::isRegularFile)::iterator) {
                if (p.equals(cfgDir.resolve("ai.key"))) continue;
                found.addAll(leaks(root.relativize(p).toString(), new String(Files.readAllBytes(p), StandardCharsets.ISO_8859_1)));
            }
        }
        assertEquals(List.of(), found);

        // a relative key file that does not exist next to the config: not configured, and the status says why
        cfg.setApiKeyFile("missing.key");
        AiClient none = HealAi.open(cfg, cfgDir, plugins, n -> null, sender, null);
        assertFalse(none.configured());
        assertEquals("AI not configured: <apiKeyFile> is missing, empty or unreadable", none.status());
        assertEquals("AI not configured: no <ai> block in the config", HealAi.open(null, cfgDir, plugins, n -> KEY, sender, null).status());
        plugins.close();
    }

    /** Δ151: the planner's outputs and the MCP {@code plan} reply never carry the key, even when the provider echoes it. */
    @Test
    void thePlanAndTheMcpPlanReplyNeverCarryTheKey(@TempDir Path dir) throws Exception {
        ObjectMapper om = new ObjectMapper();
        Path root = Files.createDirectories(dir.resolve("proj"));
        Path tests = Files.createDirectories(root.resolve("tests"));
        byte[] crawl;
        try (java.io.InputStream in = AiKeyLeakTest.class.getResourceAsStream("/plan/shop-coverage.json")) {
            crawl = in.readAllBytes();
        }
        Files.write(tests.resolve("shop-coverage.json"), crawl);
        AiConfig cfg = AiFakes.config();
        cfg.setRetries(0);
        String echo = "{\"routes\":[{\"id\":\"r2\",\"priority\":\"low\",\"note\":\"" + KEY + "\",\"journeys\":[{\"goal\":\"negative\",\"name\":\"" + KEY
                + "\",\"steps\":[{\"intent\":\"fill\",\"ref\":\"e1\",\"data\":\"invalid\",\"value\":\"" + KEY + "\"},{\"intent\":\"click\",\"ref\":\"e3\"}]}]}]} " + KEY;
        AiFakes.Sender sender = new AiFakes.Sender()
                .then(AiFakes.status(401, "{\"error\":{\"message\":\"bad key " + KEY + "\"}}"))
                .then(AiFakes.io("proxy said x-fake-key: " + KEY))
                .then(AiFakes.ok(AiFakes.reply(echo, 10, 10)))
                .then(AiFakes.ok(AiFakes.reply(echo, 10, 10)));
        List<String> found = new ArrayList<String>();
        List<String> outputs = new ArrayList<String>();
        PrintStream sysOut = System.out, sysErr = System.err;
        ByteArrayOutputStream cap = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(cap, true, "UTF-8"));
            System.setErr(new PrintStream(cap, true, "UTF-8"));
            for (int i = 0; i < 3; i++) {
                com.gatf.ai.plan.Planner.Result r = com.gatf.ai.plan.Planner.plan("shop-coverage.json",
                        com.gatf.ai.plan.Planner.readMap(tests.resolve("shop-coverage.json"), om), AiFakes.client(cfg, KEY, sender),
                        new com.gatf.ai.plan.Planner.Options(), om);
                outputs.add(om.writeValueAsString(r.json));
                outputs.add(r.markdown);
                System.out.println(outputs.get(outputs.size() - 2));
            }
            assertTrue(outputs.get(0).contains("AI chunk 1 of 1: http"), outputs.get(0));
            assertTrue(outputs.get(2).contains("AI chunk 1 of 1: io"), outputs.get(2));
            assertTrue(outputs.get(4).contains("\"journeysAdded\":1"), outputs.get(4));
            McpServer s = new McpServer("gatf", "t", "h");
            s.register(new com.gatf.mcp.PlanTool(root, om, c -> AiFakes.client(cfg, KEY, sender)).tool());
            String mcp = s.handleLine("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"plan\",\"arguments\":"
                    + "{\"crawl\":\"tests/shop-coverage.json\",\"markdown\":true}}}");
            outputs.add(mcp);
            assertTrue(mcp.contains("gatf-test-plan") && mcp.contains("\"isError\":false"), mcp);
        } finally {
            System.setOut(sysOut);
            System.setErr(sysErr);
        }
        assertEquals(4, sender.urls.size());
        for (int i = 0; i < sender.urls.size(); i++) {
            assertEquals(KEY, sender.headers.get(i).get("x-fake-key"), "the header is the one place it goes");
            found.addAll(leaks("plan request body " + i, sender.bodies.get(i)));
        }
        for (int i = 0; i < outputs.size(); i++) found.addAll(leaks("plan output " + i, outputs.get(i)));
        found.addAll(leaks("stdout/stderr", cap.toString(StandardCharsets.UTF_8)));
        try (Stream<Path> files = Files.walk(root)) {   // the MCP tool never writes
            assertEquals(1L, files.filter(Files::isRegularFile).count());
        }
        assertEquals(List.of(), found);
    }

    @Test
    void generatedScriptsTheSummaryAndTheMcpGenerateReplyNeverCarryTheKey(@TempDir Path dir) throws Exception {
        ObjectMapper om = new ObjectMapper();
        Path root = Files.createDirectories(dir.resolve("proj"));
        Files.write(root.resolve("shop-plan.json"), com.gatf.ai.gen.GenFixtures.shopPlanJson().getBytes(StandardCharsets.UTF_8));
        Files.write(root.resolve("gatf-config.json"), "{}".getBytes(StandardCharsets.UTF_8));
        AiConfig cfg = AiFakes.config();
        cfg.setRetries(0);
        // the provider echoes the key: in a 401, an I/O error, and inside replies (as an edit, a field, around the JSON)
        String echo = KEY + " {\"edits\":[\"drop-step 3\",\"" + KEY + "\",\"set-data e1 " + KEY + "\"],\"why\":\"" + KEY + "\"} " + KEY;
        AiFakes.Sender sender = new AiFakes.Sender()
                .then(AiFakes.status(401, "{\"error\":{\"message\":\"bad key " + KEY + "\"}}"))
                .then(AiFakes.io("proxy said x-fake-key: " + KEY));
        for (int i = 0; i < 12; i++) sender.then(AiFakes.ok(AiFakes.reply(echo, 10, 10)));
        // every script whose text has a title assertion fails the gate, so the repair loop asks the model
        java.util.function.Supplier<com.gatf.ai.gen.GenFixtures.FakeGate> gate = () -> {
            com.gatf.ai.gen.GenFixtures.FakeGate g = new com.gatf.ai.gen.GenFixtures.FakeGate();
            g.fail = (f, t) -> t.contains("?? title") ? List.of(new com.gatf.ai.gen.SelGate.Diag(f, 6, null, "error", "Error parsing command at line 6")) : null;
            return g;
        };
        List<String> found = new ArrayList<String>();
        List<String> outputs = new ArrayList<String>();
        PrintStream sysOut = System.out, sysErr = System.err;
        ByteArrayOutputStream cap = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(cap, true, "UTF-8"));
            System.setErr(new PrintStream(cap, true, "UTF-8"));
            for (int i = 0; i < 2; i++) {   // first run: 401 then io (two failures in a row stop AI); second: echoing replies
                int rc = com.gatf.generator.core.GenerateCli.run(new String[] { "shop-plan.json", "-o", "gen" + i }, root.toFile(), System.out, System.err,
                        f -> AiFakes.client(cfg, KEY, sender), (r, c) -> gate.get(), false);
                assertTrue(rc == 0 || rc == 2, "rc " + rc);
            }
            McpServer s = new McpServer("gatf", "t", "h");
            s.register(new com.gatf.mcp.GenerateTool(root, om, c -> AiFakes.client(cfg, KEY, sender), (r, c) -> gate.get()).tool());
            String mcp = s.handleLine("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"generate\",\"arguments\":"
                    + "{\"plan\":\"shop-plan.json\"}}}");
            outputs.add(mcp);
            assertTrue(mcp.contains("\"isError\":false") && mcp.contains("repairRounds"), mcp);
        } finally {
            System.setOut(sysOut);
            System.setErr(sysErr);
        }
        String console = cap.toString(StandardCharsets.UTF_8);
        assertTrue(console.contains("AI stopped") || console.contains("repair stopped"), console);
        assertTrue(sender.urls.size() >= 4, "the model was asked: " + sender.urls.size());
        for (int i = 0; i < sender.urls.size(); i++) {
            assertEquals(KEY, sender.headers.get(i).get("x-fake-key"), "the header is the one place it goes");
            found.addAll(leaks("generate request body " + i, sender.bodies.get(i)));
        }
        for (int i = 0; i < outputs.size(); i++) found.addAll(leaks("generate output " + i, outputs.get(i)));
        found.addAll(leaks("stdout/stderr", console));
        int files = 0;
        try (Stream<Path> w = Files.walk(root)) {
            for (Path p : (Iterable<Path>) w::iterator) {
                if (!Files.isRegularFile(p)) continue;
                files++;
                found.addAll(leaks("file " + root.relativize(p), new String(Files.readAllBytes(p), StandardCharsets.UTF_8)));
            }
        }
        assertTrue(files > 2, "scripts were written");
        assertEquals(List.of(), found);
    }
}
