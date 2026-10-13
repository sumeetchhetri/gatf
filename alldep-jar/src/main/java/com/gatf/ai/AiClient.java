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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * H1 (Δ149): one run's AI access: the configured provider, its key, and the run's limits. {@link #open} never throws:
 * with no {@code <ai>} block, an unknown provider, no model or no key it returns a client whose {@link #configured()}
 * is false and whose {@link #status()} says why ("AI not configured: …"); {@link #ask} then throws
 * {@code NOT_CONFIGURED} without touching the network. Every call goes through here, so per-call timeouts, bounded
 * retries (429 / 5xx / timeouts / I/O, with backoff) and the per-run cap on calls, tokens and cost hold for every
 * provider. Messages are redacted; the key is only ever placed in a request header by the provider. Thread-safe.
 */
public final class AiClient {

    /** Sleeps between retries; tests pass a no-op. */
    public interface Sleeper { void sleep(long ms) throws InterruptedException; }

    private final AiConfig cfg;
    private final AiProvider provider;
    private final AiSecret key;
    private final HttpSender http;
    private final Sleeper sleeper;
    private final String status;
    private final String endpoint;
    /** Longest reply text kept (callers parse at most 64 KB). */
    static final int MAX_TEXT = 256 * 1024;
    private int calls;
    private long tokens;
    private double cost;

    private AiClient(AiConfig cfg, AiProvider provider, AiSecret key, String endpoint, HttpSender http, Sleeper sleeper, String status) {
        this.cfg = cfg;
        this.provider = provider;
        this.key = key;
        this.endpoint = endpoint;
        this.http = http;
        this.sleeper = sleeper;
        this.status = status;
    }

    /** A client that is not configured, with the reason. */
    public static AiClient notConfigured(String why) {
        return new AiClient(null, null, null, null, null, null, "AI not configured: " + why);
    }

    /**
     * @param env        environment lookup ({@code System::getenv} in production; a map in tests)
     * @param providers  from {@link AiProviders#all}
     * @param http       {@link JdkHttpSender} in production; a fake in tests
     */
    public static AiClient open(AiConfig cfg, Function<String, String> env, Map<String, AiProvider> providers, HttpSender http, Sleeper sleeper) {
        if (cfg == null || cfg.getProvider() == null || cfg.getProvider().isBlank()) return notConfigured("no <ai> provider in the config");
        String name = cfg.getProvider().trim().toLowerCase(java.util.Locale.ROOT);
        AiProvider p = providers == null ? null : providers.get(name);
        if (p == null) return notConfigured("unknown provider '" + name.replaceAll("[^a-z0-9_-]", "?") + "'" + (providers == null ? "" : " (known: " + String.join(", ", providers.keySet()) + ")"));
        if (cfg.getModel() == null || cfg.getModel().isBlank()) return notConfigured("no <model> for provider " + name);
        String ep = cfg.getEndpoint() != null && !cfg.getEndpoint().isBlank() ? cfg.getEndpoint().trim() : p.defaultEndpoint();
        if (ep == null) return notConfigured("provider " + name + " needs an <endpoint>");
        if (!ep.matches("(?i)https?://[^\\s]+")) return notConfigured("<endpoint> must be an http(s) URL");
        boolean cleartext = ep.regionMatches(true, 0, "http://", 0, 7) && !loopback(ep);
        AiSecret key = null;
        String keyEnv = cfg.getApiKeyEnv() != null && !cfg.getApiKeyEnv().isBlank() ? cfg.getApiKeyEnv().trim() : p.defaultKeyEnv();
        if (cfg.getApiKeyFile() != null && !cfg.getApiKeyFile().isBlank()) {
            try {
                Path f = Paths.get(cfg.getApiKeyFile().trim());
                if (Files.isRegularFile(f) && Files.size(f) <= 16 * 1024) {
                    String s = new String(Files.readAllBytes(f), StandardCharsets.UTF_8);
                    int nl = s.indexOf('\n');
                    key = AiSecret.of(nl >= 0 ? s.substring(0, nl) : s);
                }
            } catch (IOException | RuntimeException e) {
                key = null;
            }
            if (key == null) return notConfigured("<apiKeyFile> is missing, empty or unreadable");
        } else if (keyEnv != null && env != null) {
            key = AiSecret.of(env.apply(keyEnv));
        }
        if (key != null && cleartext) return notConfigured("a key would be sent in cleartext: use an https <endpoint> (http only to localhost)");
        if (key == null && p.needsKey()) return notConfigured("no key: set the environment variable " + (keyEnv == null ? "named in <apiKeyEnv>" : keyEnv) + " or <apiKeyFile>");
        return new AiClient(cfg, p, key, ep, http == null ? new JdkHttpSender() : http, sleeper == null ? Thread::sleep : sleeper, "ready: " + name + " / " + cfg.getModel().trim());
    }

    static boolean loopback(String url) {
        try {
            String h = java.net.URI.create(url).getHost();
            return h != null && (h.equalsIgnoreCase("localhost") || h.equals("127.0.0.1") || h.equals("[::1]") || h.equals("::1"));
        } catch (RuntimeException e) {
            return false;
        }
    }

    public boolean configured() { return provider != null; }

    /** "ready: provider / model" or "AI not configured: <reason>". Never contains the key. */
    public String status() { return status; }

    /** Calls, tokens and cost spent so far in this run. */
    public synchronized Map<String, Object> usage() {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("calls", calls);
        m.put("tokens", tokens);
        if (cfg != null && (cfg.getInputCostPerMTok() != null || cfg.getOutputCostPerMTok() != null)) m.put("cost", cost);
        return m;
    }

    /**
     * One completion. Refused before any network use when the run's call, token or cost budget is spent; a reply that
     * pushes the run over the token or cost cap is still returned (it is already paid for) and the next call is refused.
     */
    public AiReply ask(AiPrompt prompt) throws AiException {
        if (!configured()) throw new AiException(AiException.Code.NOT_CONFIGURED, status);
        int maxOut;
        synchronized (this) {
            if (calls >= cfg.maxCallsOr()) throw new AiException(AiException.Code.BUDGET, "AI call budget for this run is spent (" + cfg.maxCallsOr() + " calls)");
            long left = cfg.maxTokensOr() - tokens;
            if (left <= 0) throw new AiException(AiException.Code.BUDGET, "AI token budget for this run is spent (" + cfg.maxTokensOr() + " tokens)");
            if (cfg.getMaxCostPerRun() != null && cost >= cfg.getMaxCostPerRun()) throw new AiException(AiException.Code.BUDGET, "AI cost budget for this run is spent");
            maxOut = (int) Math.max(1, Math.min(cfg.maxOutputTokensOr(), left));
            calls++;
        }
        AiProvider.Request req;
        try {
            req = provider.build(endpoint, cfg.getModel().trim(), prompt, maxOut, key);
            if (req == null || req.url == null || req.headers == null || req.body == null) throw new IllegalStateException("incomplete request");
        } catch (RuntimeException e) {   // a plugin provider's bug: reported, redacted, never thrown past the client
            throw new AiException(AiException.Code.IO, redact("AI provider " + provider.name() + " could not build the request: " + e.getClass().getSimpleName()));
        }
        Duration timeout = Duration.ofMillis(cfg.timeoutMsOr());
        int attempts = 1 + cfg.retriesOr();
        AiException last = null;
        for (int a = 0; a < attempts; a++) {
            if (a > 0) {
                try {
                    sleeper.sleep(Math.min(8_000L, 500L << (a - 1)));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AiException(AiException.Code.INTERRUPTED, "AI call interrupted");
                }
            }
            HttpSender.Response r;
            try {
                r = http.post(req.url, req.headers, req.body, timeout);
            } catch (HttpTimeoutException e) {
                last = new AiException(AiException.Code.TIMEOUT, "AI call timed out after " + timeout.toMillis() + " ms");
                continue;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AiException(AiException.Code.INTERRUPTED, "AI call interrupted");
            } catch (IOException | RuntimeException e) {
                last = new AiException(AiException.Code.IO, redact("AI call failed: " + e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage())));
                continue;
            }
            if (r.status == 429 || r.status >= 500) {
                last = new AiException(AiException.Code.HTTP, "AI provider returned HTTP " + r.status);
                continue;
            }
            if (r.status < 200 || r.status >= 300) {   // 4xx other than 429: not retried; body summarised, redacted
                throw new AiException(AiException.Code.HTTP, redact("AI provider returned HTTP " + r.status + errorDetail(r.body)));
            }
            AiReply reply;
            try {
                reply = provider.parse(r.body);
            } catch (AiException e) {
                throw new AiException(e.code, redact(e.getMessage()));
            } catch (RuntimeException e) {
                throw new AiException(AiException.Code.BAD_REPLY, "AI reply could not be read");
            }
            long in = reply.inputTokens, outT = reply.outputTokens;
            if (in == 0 && outT == 0) {   // no usage reported (common on local servers): estimate ~4 chars per token
                in = (req.body.length + 3) / 4;
                outT = (reply.text.length() + 3) / 4;
            }
            final long fin = in, fout = outT;
            synchronized (this) {
                tokens += fin + fout;
                if (cfg.getInputCostPerMTok() != null) cost += fin * cfg.getInputCostPerMTok() / 1_000_000d;
                if (cfg.getOutputCostPerMTok() != null) cost += fout * cfg.getOutputCostPerMTok() / 1_000_000d;
            }
            String text = reply.text.length() > MAX_TEXT ? reply.text.substring(0, MAX_TEXT) : reply.text;
            return new AiReply(redact(text), fin, fout);
        }
        throw last != null ? last : new AiException(AiException.Code.IO, "AI call failed");
    }

    /** A provider's error message ({@code error.message} / {@code message}), capped; never the raw body. */
    static String errorDetail(byte[] body) {
        try {
            com.fasterxml.jackson.databind.JsonNode n = BuiltinProviders.OM.readTree(body);
            String m = n == null ? null : n.path("error").path("message").asText(n.path("message").asText(null));
            if (m == null || m.isEmpty()) return "";
            m = m.replaceAll("[\\x00-\\x1f\\x7f]", " ");
            return ": " + (m.length() > 200 ? m.substring(0, 200) + "…" : m);
        } catch (IOException | RuntimeException e) {
            return "";
        }
    }

    private String redact(String s) {
        return key == null ? s : key.redact(s);
    }

    @Override public String toString() { return "AiClient[" + status + "]"; }
}
