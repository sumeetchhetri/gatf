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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.io.InputStream;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/** H1 (Δ149): {@link HttpSender} over {@code java.net.http.HttpClient} (no SDK dependency). Reply bodies are capped. */
public final class JdkHttpSender implements HttpSender {

    static final int MAX_REPLY = 4 * 1024 * 1024;

    private final HttpClient client;

    public JdkHttpSender() {
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    /**
     * The timeout covers the whole exchange, body included: {@code HttpRequest.timeout} alone stops at the response
     * headers, so a server trickling its body could hold a call forever. On expiry the stream is closed and the call
     * fails with {@link HttpTimeoutException}.
     */
    @Override
    public Response post(String url, Map<String, String> headers, byte[] body, Duration timeout) throws IOException, InterruptedException {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(timeout).POST(HttpRequest.BodyPublishers.ofByteArray(body));
        for (Map.Entry<String, String> h : headers.entrySet()) b.header(h.getKey(), h.getValue());
        HttpRequest req = b.build();
        AtomicReference<InputStream> open = new AtomicReference<InputStream>();
        java.util.concurrent.atomic.AtomicBoolean expired = new java.util.concurrent.atomic.AtomicBoolean();
        CompletableFuture<Response> f = CompletableFuture.supplyAsync(() -> {
            try {
                HttpResponse<InputStream> r = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
                byte[] data;
                try (InputStream in = r.body()) {
                    open.set(in);
                    if (expired.get()) throw new IOException("expired");   // the caller already gave up: don't read on
                    data = in.readNBytes(MAX_REPLY);
                }
                Map<String, String> hs = new LinkedHashMap<String, String>();
                for (Map.Entry<String, List<String>> h : r.headers().map().entrySet()) {
                    if (!h.getValue().isEmpty()) hs.put(h.getKey().toLowerCase(Locale.ROOT), h.getValue().get(0));
                }
                return new Response(r.statusCode(), data, hs);
            } catch (IOException e) {
                throw new CompletionException(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CompletionException(e);
            }
        }, EXEC);
        try {
            return f.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            expired.set(true);
            f.cancel(true);
            InputStream in = open.get();
            if (in != null) try { in.close(); } catch (IOException ignored) { }
            throw new HttpTimeoutException("AI call exceeded " + timeout.toMillis() + " ms");
        } catch (ExecutionException e) {
            Throwable c = e.getCause();
            if (c instanceof CompletionException && c.getCause() != null) c = c.getCause();
            if (c instanceof IOException) throw (IOException) c;
            if (c instanceof InterruptedException) throw (InterruptedException) c;
            throw new IOException(c == null ? "AI call failed" : c.getClass().getSimpleName());
        } catch (InterruptedException e) {
            f.cancel(true);
            throw e;
        }
    }

    private static final java.util.concurrent.Executor EXEC = r -> {
        Thread t = new Thread(r, "gatf-ai-http");
        t.setDaemon(true);
        t.start();
    };
}
