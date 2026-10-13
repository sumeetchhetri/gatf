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
package com.gatf.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** H1 (Δ149): the provider SPI and the per-run client: not-configured states, timeouts, retries, budget, discovery. */
public class AiClientTest {

    static final String KEY = "sk-fake-0123456789abcdefXYZ";
    static final AiPrompt P = new AiPrompt("sys", "user");

    @Test
    void noConfigOrAnIncompleteOneIsACleanNotConfiguredStateThatNeverCallsOut() throws Exception {
        AiFakes.Sender s = new AiFakes.Sender();
        List<AiClient> cs = new ArrayList<AiClient>();
        cs.add(AiClient.open(null, n -> KEY, AiFakes.providers(), s, ms -> { }));
        AiConfig c = AiFakes.config();
        c.setProvider("nope");
        cs.add(AiClient.open(c, n -> KEY, AiFakes.providers(), s, ms -> { }));
        c = AiFakes.config();
        c.setModel(" ");
        cs.add(AiClient.open(c, n -> KEY, AiFakes.providers(), s, ms -> { }));
        cs.add(AiFakes.client(AiFakes.config(), null, s));                       // key env unset
        cs.add(AiFakes.client(AiFakes.config(), "  ", s));                       // blank key
        c = AiFakes.config();
        c.setProvider("local");                                                 // local needs an endpoint
        cs.add(AiClient.open(c, n -> null, AiFakes.providers(), s, ms -> { }));
        c = AiFakes.config();
        c.setEndpoint("file:///etc/passwd");
        cs.add(AiFakes.client(c, KEY, s));
        c = AiFakes.config();
        c.setApiKeyFile("/no/such/key/file");
        cs.add(AiFakes.client(c, KEY, s));
        for (AiClient a : cs) {
            assertFalse(a.configured(), a.status());
            assertTrue(a.status().startsWith("AI not configured: "), a.status());
            assertFalse(a.status().contains(KEY));
            AiException e = assertThrows(AiException.class, () -> a.ask(P));
            assertEquals(AiException.Code.NOT_CONFIGURED, e.code);
            assertEquals("not-configured", e.word());
        }
        assertTrue(s.urls.isEmpty(), "no network use while not configured");
    }

    @Test
    void localProviderWorksWithoutAKeyAndKeyFileIsRead(@TempDir Path dir) throws Exception {
        AiConfig c = AiFakes.config();
        c.setProvider("local");
        c.setEndpoint("http://127.0.0.1:11434/v1/chat/completions");
        AiFakes.Sender s = new AiFakes.Sender().then(AiFakes.ok("{\"choices\":[{\"message\":{\"content\":\"hi\"}}],\"usage\":{\"prompt_tokens\":3,\"completion_tokens\":1}}"));
        AiClient a = AiClient.open(c, n -> null, AiFakes.providers(), s, ms -> { });
        assertTrue(a.configured(), a.status());
        assertEquals("hi", a.ask(P).text);
        assertFalse(s.headers.get(0).containsKey("authorization"));

        Path kf = dir.resolve("key.txt");
        Files.write(kf, (KEY + "\nignored\n").getBytes(StandardCharsets.UTF_8));
        c = AiFakes.config();
        c.setApiKeyFile(kf.toString());
        s = new AiFakes.Sender().then(AiFakes.ok(AiFakes.reply("ok", 1, 1)));
        a = AiFakes.client(c, null, s);
        assertTrue(a.configured(), a.status());
        a.ask(P);
        assertEquals(KEY, s.headers.get(0).get("x-fake-key"));
    }

    @Test
    void retriesTimeoutsAnd5xxAnd429ThenSucceedsWithTheConfiguredTimeout() throws Exception {
        AiConfig c = AiFakes.config();
        c.setRetries(3);
        c.setTimeoutMs(2500);
        AiFakes.Sender s = new AiFakes.Sender().then(AiFakes.timeout()).then(AiFakes.status(503, "{}")).then(AiFakes.status(429, "{}"))
                .then(AiFakes.ok(AiFakes.reply("done", 10, 5)));
        List<Long> slept = new ArrayList<Long>();
        AiClient a = AiClient.open(c, n -> KEY, AiFakes.providers(), s, slept::add);
        AiReply r = a.ask(P);
        assertEquals("done", r.text);
        assertEquals(4, s.urls.size());
        assertEquals(List.of(500L, 1000L, 2000L), slept);
        assertEquals(2500, s.timeouts.get(0).toMillis());
        assertEquals(1, a.usage().get("calls"), "retries are one call against the budget");
        assertEquals(15L, a.usage().get("tokens"));
    }

    @Test
    void retriesAreBoundedAndTheLastErrorIsReported() {
        AiConfig c = AiFakes.config();
        c.setRetries(1);
        AiFakes.Sender s = new AiFakes.Sender().then(AiFakes.timeout()).then(AiFakes.timeout()).then(AiFakes.ok(AiFakes.reply("late", 1, 1)));
        AiClient a = AiFakes.client(c, KEY, s);
        AiException e = assertThrows(AiException.class, () -> a.ask(P));
        assertEquals(AiException.Code.TIMEOUT, e.code);
        assertEquals(2, s.urls.size(), "1 try + 1 retry");
    }

    @Test
    void a4xxIsNotRetriedAndItsMessageIsRedacted() {
        AiFakes.Sender s = new AiFakes.Sender().then(AiFakes.status(401, "{\"error\":{\"message\":\"invalid x-api-key " + KEY + "\"}}"));
        AiClient a = AiFakes.client(AiFakes.config(), KEY, s);
        AiException e = assertThrows(AiException.class, () -> a.ask(P));
        assertEquals(AiException.Code.HTTP, e.code);
        assertEquals(1, s.urls.size());
        assertTrue(e.getMessage().contains("401") && e.getMessage().contains("***"), e.getMessage());
        assertFalse(e.getMessage().contains(KEY), e.getMessage());
        assertFalse(e.getMessage().contains(KEY.substring(3, 20)), "no cut-off echo either");
    }

    @Test
    void ioErrorsAreRedactedToo() {
        AiConfig c = AiFakes.config();
        c.setRetries(0);
        AiFakes.Sender s = new AiFakes.Sender().then(AiFakes.io("proxy said: header x-fake-key=" + KEY));
        AiClient a = AiFakes.client(c, KEY, s);
        AiException e = assertThrows(AiException.class, () -> a.ask(P));
        assertEquals(AiException.Code.IO, e.code);
        assertFalse(e.getMessage().contains(KEY), e.getMessage());
    }

    @Test
    void callTokenAndCostCapsHoldPerRun() throws Exception {
        AiConfig c = AiFakes.config();
        c.setMaxCallsPerRun(2);
        AiFakes.Sender s = new AiFakes.Sender();
        for (int i = 0; i < 3; i++) s.then(AiFakes.ok(AiFakes.reply("r" + i, 1, 1)));
        AiClient a = AiFakes.client(c, KEY, s);
        a.ask(P);
        a.ask(P);
        AiException e = assertThrows(AiException.class, () -> a.ask(P));
        assertEquals(AiException.Code.BUDGET, e.code);
        assertEquals(2, s.urls.size(), "refused before any network use");

        c = AiFakes.config();
        c.setMaxTokensPerRun(100);
        c.setMaxOutputTokens(64);
        s = new AiFakes.Sender().then(AiFakes.ok(AiFakes.reply("big", 70, 20))).then(AiFakes.ok(AiFakes.reply("x", 5, 5))).then(AiFakes.ok(AiFakes.reply("never", 1, 1)));
        AiClient b = AiFakes.client(c, KEY, s);
        b.ask(P);
        JsonNode sent = new ObjectMapper().readTree(s.bodies.get(0));
        assertEquals(64, sent.path("max").asInt(), "max output = min(maxOutputTokens, tokens left)");
        b.ask(P);   // 10 tokens left: allowed, max output capped to what is left
        assertEquals(10, new ObjectMapper().readTree(s.bodies.get(1)).path("max").asInt());
        assertEquals(BUDGET(), assertThrows(AiException.class, () -> b.ask(P)).code);   // 100 of 100 spent
        assertEquals(2, s.urls.size());

        c = AiFakes.config();
        c.setMaxCostPerRun(0.01);
        c.setInputCostPerMTok(1000.0);   // 1000 per million input tokens
        s = new AiFakes.Sender().then(AiFakes.ok(AiFakes.reply("x", 20, 0))).then(AiFakes.ok(AiFakes.reply("y", 1, 0)));
        AiClient d = AiFakes.client(c, KEY, s);
        d.ask(P);   // cost 0.02 >= 0.01
        assertEquals(BUDGET(), assertThrows(AiException.class, () -> d.ask(P)).code);
        assertEquals(0.02, (Double) d.usage().get("cost"), 1e-9);
    }

    private static AiException.Code BUDGET() { return AiException.Code.BUDGET; }

    @Test
    void aKeyIsNeverSentInCleartextAndMissingUsageIsEstimated() throws Exception {
        AiConfig c = AiFakes.config();
        c.setEndpoint("http://ai.example.test/v1");
        AiClient a = AiFakes.client(c, KEY, new AiFakes.Sender());
        assertFalse(a.configured());
        assertTrue(a.status().contains("cleartext"), a.status());
        c.setEndpoint("http://localhost:8080/v1");
        assertTrue(AiFakes.client(c, KEY, new AiFakes.Sender()).configured());

        c = AiFakes.config();
        c.setMaxTokensPerRun(50);
        AiFakes.Sender s = new AiFakes.Sender().then(AiFakes.ok("{\"text\":\"" + "x".repeat(400) + "\"}")).then(AiFakes.ok(AiFakes.reply("y", 1, 1)));
        AiClient b = AiFakes.client(c, KEY, s);
        b.ask(P);
        assertTrue(((Long) b.usage().get("tokens")) >= 100, b.usage().toString());
        assertEquals(AiException.Code.BUDGET, assertThrows(AiException.class, () -> b.ask(P)).code, "a server without usage still hits the token cap");
    }

    @Test
    void builtInProvidersShapeRequestsAndKeepTheKeyInAHeaderOnly() throws Exception {
        ObjectMapper om = new ObjectMapper();
        for (String p : new String[] { "anthropic", "openai" }) {
            AiConfig c = new AiConfig();
            c.setProvider(p);
            c.setModel("m-1");
            String env = p.equals("anthropic") ? "ANTHROPIC_API_KEY" : "OPENAI_API_KEY";
            String body = p.equals("anthropic")
                    ? "{\"content\":[{\"type\":\"text\",\"text\":\"A\"},{\"type\":\"text\",\"text\":\"B\"}],\"usage\":{\"input_tokens\":7,\"output_tokens\":2}}"
                    : "{\"choices\":[{\"message\":{\"content\":\"AB\"}}],\"usage\":{\"prompt_tokens\":7,\"completion_tokens\":2}}";
            AiFakes.Sender s = new AiFakes.Sender().then(AiFakes.ok(body));
            AiClient a = AiClient.open(c, n -> env.equals(n) ? KEY : null, AiFakes.providers(), s, ms -> { });
            assertTrue(a.configured(), a.status());
            AiReply r = a.ask(new AiPrompt("SYS", "USER"));
            assertEquals("AB", r.text);
            assertEquals(9L, a.usage().get("tokens"));
            assertFalse(s.bodies.get(0).contains(KEY), "key never in the body");
            JsonNode b = om.readTree(s.bodies.get(0));
            assertEquals("m-1", b.path("model").asText());
            if (p.equals("anthropic")) {
                assertEquals("https://api.anthropic.com/v1/messages", s.urls.get(0));
                assertEquals(KEY, s.headers.get(0).get("x-api-key"));
                assertEquals("SYS", b.path("system").asText());
                assertEquals("USER", b.path("messages").path(0).path("content").asText());
            } else {
                assertEquals("https://api.openai.com/v1/chat/completions", s.urls.get(0));
                assertEquals("Bearer " + KEY, s.headers.get(0).get("authorization"));
                assertEquals("system", b.path("messages").path(0).path("role").asText());
            }
        }
        AiFakes.Sender s = new AiFakes.Sender().then(AiFakes.ok("{\"content\":[]}"));
        AiConfig c = new AiConfig();
        c.setProvider("anthropic");
        c.setModel("m");
        AiClient a = AiClient.open(c, n -> KEY, AiFakes.providers(), s, ms -> { });
        assertEquals(AiException.Code.BAD_REPLY, assertThrows(AiException.class, () -> a.ask(P)).code);
    }

    @Test
    void pluginProvidersAreDiscoveredBuiltinsCannotBeShadowedAndBadOnesWarn(@TempDir Path dir) throws Exception {
        Path svc = dir.resolve("META-INF/services/com.gatf.ai.AiProvider");
        Files.createDirectories(svc.getParent());
        Files.write(svc, ("com.gatf.ai.AiPluginProviderForTest\ncom.gatf.ai.AiShadowingProviderForTest\ncom.gatf.ai.NoSuchProvider\n").getBytes(StandardCharsets.UTF_8));
        List<String> warnings = new ArrayList<String>();
        try (URLClassLoader cl = new URLClassLoader(new URL[] { dir.toUri().toURL() }, AiClientTest.class.getClassLoader())) {
            Map<String, AiProvider> all = AiProviders.all(cl, warnings);
            assertNotNull(all.get("plugin-fake"));
            assertEquals("com.gatf.ai.BuiltinProviders$Anthropic", all.get("anthropic").getClass().getName(), "built-ins cannot be replaced");
            assertTrue(all.keySet().containsAll(List.of("anthropic", "openai", "local")));
            assertTrue(warnings.stream().anyMatch(w -> w.contains("already taken")), warnings.toString());
            assertTrue(warnings.stream().anyMatch(w -> w.contains("skipped")), warnings.toString());
        }
        assertNull(AiProviders.all(null, null).get("plugin-fake"));
    }
}
