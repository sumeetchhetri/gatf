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

import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** H1 (Δ149) test doubles: a fake provider and a scripted HTTP sender. No test opens a socket. */
public final class AiFakes {

    private AiFakes() {}

    /** One scripted exchange: a status + body, or an exception to throw. */
    public static final class Step {
        final int status;
        final String body;
        final Exception error;
        Step(int status, String body, Exception error) { this.status = status; this.body = body; this.error = error; }
    }

    public static Step ok(String body) { return new Step(200, body, null); }
    public static Step status(int s, String body) { return new Step(s, body, null); }
    public static Step timeout() { return new Step(0, null, new HttpTimeoutException("request timed out")); }
    public static Step io(String msg) { return new Step(0, null, new IOException(msg)); }

    public static final class Sender implements HttpSender {
        public final Deque<Step> script = new ArrayDeque<Step>();
        public final List<String> urls = new ArrayList<String>();
        public final List<Map<String, String>> headers = new ArrayList<Map<String, String>>();
        public final List<String> bodies = new ArrayList<String>();
        public final List<Duration> timeouts = new ArrayList<Duration>();

        public Sender then(Step s) { script.add(s); return this; }

        @Override
        public Response post(String url, Map<String, String> h, byte[] body, Duration timeout) throws IOException {
            urls.add(url);
            headers.add(new LinkedHashMap<String, String>(h));
            bodies.add(new String(body, StandardCharsets.UTF_8));
            timeouts.add(timeout);
            Step s = script.poll();
            if (s == null) throw new IOException("no scripted reply");
            if (s.error instanceof IOException) throw (IOException) s.error;
            if (s.error != null) throw new IllegalStateException(s.error);
            return new Response(s.status, s.body.getBytes(StandardCharsets.UTF_8), Map.of());
        }
    }

    /**
     * The fake provider: posts {@code {model, max, system, user}} with an {@code x-fake-key} header; reads
     * {@code {"text": …, "in": n, "out": n}}.
     */
    public static class Provider implements AiProvider {
        static final ObjectMapper OM = new ObjectMapper();
        public String name() { return "fake"; }
        public String defaultEndpoint() { return "https://fake.invalid/v1"; }
        public String defaultKeyEnv() { return "FAKE_AI_KEY"; }

        public Request build(String endpoint, String model, AiPrompt p, int maxOut, AiSecret key) {
            Map<String, Object> b = new LinkedHashMap<String, Object>();
            b.put("model", model);
            b.put("max", maxOut);
            b.put("system", p.system);
            b.put("user", p.user);
            Map<String, String> h = new LinkedHashMap<String, String>();
            if (key != null) h.put("x-fake-key", key.reveal());
            try {
                return new Request(endpoint, h, OM.writeValueAsBytes(b));
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        public AiReply parse(byte[] body) throws AiException {
            try {
                JsonNode n = OM.readTree(body);
                if (n == null || !n.path("text").isTextual()) throw new AiException(AiException.Code.BAD_REPLY, "no text");
                return new AiReply(n.path("text").asText(), n.path("in").asLong(0), n.path("out").asLong(0));
            } catch (IOException e) {
                throw new AiException(AiException.Code.BAD_REPLY, "not json");
            }
        }
    }

    /** {"text": text, "in": in, "out": out} as the fake provider's reply body. */
    public static String reply(String text, int in, int out) {
        try {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("text", text);
            m.put("in", in);
            m.put("out", out);
            return Provider.OM.writeValueAsString(m);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static Map<String, AiProvider> providers() {
        Map<String, AiProvider> m = new LinkedHashMap<String, AiProvider>(AiProviders.all(null, null));
        m.put("fake", new Provider());
        return m;
    }

    public static AiConfig config() {
        AiConfig c = new AiConfig();
        c.setProvider("fake");
        c.setModel("fake-model-1");
        return c;
    }

    public static AiClient client(AiConfig cfg, String key, Sender s) {
        return AiClient.open(cfg, n -> "FAKE_AI_KEY".equals(n) ? key : null, providers(), s, ms -> { });
    }
}
