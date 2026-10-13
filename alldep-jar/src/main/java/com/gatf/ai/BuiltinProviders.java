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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** H1 (Δ149): the built-in providers, as plain {@link AiProvider}s over Jackson. */
public final class BuiltinProviders {

    static final ObjectMapper OM = new ObjectMapper();

    private BuiltinProviders() {}

    public static List<AiProvider> all() {
        return List.of(new Anthropic(), new OpenAi("openai", "https://api.openai.com/v1/chat/completions", "OPENAI_API_KEY", true),
                new OpenAi("local", null, null, false));
    }

    static byte[] json(Object o) {
        try {
            return OM.writeValueAsBytes(o);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static JsonNode read(byte[] body) throws AiException {
        try {
            JsonNode n = OM.readTree(body);
            if (n == null || !n.isObject()) throw new AiException(AiException.Code.BAD_REPLY, "reply is not a JSON object");
            return n;
        } catch (IOException e) {
            throw new AiException(AiException.Code.BAD_REPLY, "reply is not JSON");
        }
    }

    /** Anthropic Messages API. */
    static final class Anthropic implements AiProvider {
        public String name() { return "anthropic"; }
        public String defaultEndpoint() { return "https://api.anthropic.com/v1/messages"; }
        public String defaultKeyEnv() { return "ANTHROPIC_API_KEY"; }

        public Request build(String endpoint, String model, AiPrompt p, int maxOut, AiSecret key) {
            Map<String, Object> b = new LinkedHashMap<String, Object>();
            b.put("model", model);
            b.put("max_tokens", maxOut);
            b.put("system", p.system);
            b.put("messages", List.of(Map.of("role", "user", "content", p.user)));
            Map<String, String> h = new LinkedHashMap<String, String>();
            h.put("content-type", "application/json");
            h.put("anthropic-version", "2023-06-01");
            if (key != null) h.put("x-api-key", key.reveal());
            return new Request(endpoint, h, json(b));
        }

        public AiReply parse(byte[] body) throws AiException {
            JsonNode n = read(body);
            StringBuilder t = new StringBuilder();
            for (JsonNode c : n.path("content")) if ("text".equals(c.path("type").asText())) t.append(c.path("text").asText(""));
            if (t.length() == 0) throw new AiException(AiException.Code.BAD_REPLY, "reply has no text content");
            return new AiReply(t.toString(), n.path("usage").path("input_tokens").asLong(0), n.path("usage").path("output_tokens").asLong(0));
        }
    }

    /** OpenAI Chat Completions, and any compatible server ({@code local}: endpoint required, key optional). */
    static final class OpenAi implements AiProvider {
        final String name, endpoint, keyEnv;
        final boolean needsKey;

        OpenAi(String name, String endpoint, String keyEnv, boolean needsKey) {
            this.name = name;
            this.endpoint = endpoint;
            this.keyEnv = keyEnv;
            this.needsKey = needsKey;
        }

        public String name() { return name; }
        public String defaultEndpoint() { return endpoint; }
        public String defaultKeyEnv() { return keyEnv; }
        public boolean needsKey() { return needsKey; }

        public Request build(String url, String model, AiPrompt p, int maxOut, AiSecret key) {
            Map<String, Object> b = new LinkedHashMap<String, Object>();
            b.put("model", model);
            b.put("max_tokens", maxOut);
            b.put("messages", List.of(Map.of("role", "system", "content", p.system), Map.of("role", "user", "content", p.user)));
            Map<String, String> h = new LinkedHashMap<String, String>();
            h.put("content-type", "application/json");
            if (key != null) h.put("authorization", "Bearer " + key.reveal());
            return new Request(url, h, json(b));
        }

        public AiReply parse(byte[] body) throws AiException {
            JsonNode n = read(body);
            JsonNode m = n.path("choices").path(0).path("message").path("content");
            if (!m.isTextual() || m.asText().isEmpty()) throw new AiException(AiException.Code.BAD_REPLY, "reply has no message content");
            return new AiReply(m.asText(), n.path("usage").path("prompt_tokens").asLong(0), n.path("usage").path("completion_tokens").asLong(0));
        }
    }
}
