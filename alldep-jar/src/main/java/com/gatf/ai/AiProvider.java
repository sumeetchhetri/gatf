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

import java.util.Map;

/**
 * H1 (Δ149): the AI provider SPI. A plain interface found through {@link java.util.ServiceLoader} (a plugin jar lists
 * its class in {@code META-INF/services/com.gatf.ai.AiProvider}), like the perf-engine SPI. A provider only shapes
 * the HTTP request and reads the reply; transport, timeouts, retries and the per-run token / cost cap belong to
 * {@link AiClient}, so a provider cannot bypass them. Built-ins: {@code anthropic}, {@code openai}, {@code local}.
 */
public interface AiProvider {

    /** Lower-case name used in {@code <ai><provider>}: {@code [a-z][a-z0-9_-]*}. */
    String name();

    /** Endpoint when the config names none; null means {@code <endpoint>} is required. */
    String defaultEndpoint();

    /** Environment variable read for the key when the config names none; null when no key is needed by default. */
    String defaultKeyEnv();

    /** False for providers that work without a key (a local server). */
    default boolean needsKey() { return true; }

    /** The request: headers (the key goes here and only here) and the JSON body. */
    Request build(String endpoint, String model, AiPrompt prompt, int maxOutputTokens, AiSecret key);

    /** Reads a 2xx reply body. Throws {@link AiException} ({@code BAD_REPLY}) when it is not the expected shape. */
    AiReply parse(byte[] body) throws AiException;

    final class Request {
        public final String url;
        public final Map<String, String> headers;
        public final byte[] body;

        public Request(String url, Map<String, String> headers, byte[] body) {
            this.url = url;
            this.headers = headers;
            this.body = body;
        }

        @Override public String toString() { return "Request[" + url + "]"; }   // never the headers
    }
}
