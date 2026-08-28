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
package com.gatf.selenium.trace;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Streams network events to {@code network.ndjson} as they arrive, captures response bodies
 * (deduped + capped) into {@code resources/}, and builds a HAR 1.2 {@code network.har} at
 * finalize. Response bodies are fetched at loadingFinished (when CDP can return them) and
 * bounded by the {@link ResourceStore} budget.
 *
 * Per-request HAR metadata (small — URLs/headers/timings, never bodies) is retained in a
 * concurrent map to emit the HAR; the dominant memory (bodies, snapshots) is on disk.
 *
 * @author Sumeet Chhetri
 */
public final class NetworkRecorder implements NetworkListener {

    static final class Net {
        String requestId, url, method, statusText, mimeType, bodyRef, errorText;
        Map<String, String> reqHeaders, resHeaders;
        int status;
        long reqTs, finTs = -1, encodedDataLength = -1, bodySize = -1;
        boolean failed;
    }

    private final NdjsonWriter raw;
    private final ResourceStore store;
    private final BrowserControl browser;
    private final boolean captureBodies;
    private final ConcurrentHashMap<String, Net> entries = new ConcurrentHashMap<>();

    public NetworkRecorder(BrowserControl browser, Path traceDir, TraceConfig cfg) throws IOException {
        this.browser = browser;
        this.raw = new NdjsonWriter(traceDir.resolve("network.ndjson"));
        this.store = new ResourceStore(traceDir.resolve("resources"), cfg.getMaxBodyBytes(), cfg.getMaxTotalBytes());
        this.captureBodies = cfg.isCaptureBodies();
    }

    @Override
    public void onRequest(String id, String url, String method, Map<String, String> headers, long ts) {
        Net n = new Net();
        n.requestId = id; n.url = url; n.method = method; n.reqHeaders = headers; n.reqTs = ts;
        entries.put(id, n);
        raw.writeLine(new Json.Obj().put("phase", "request").put("requestId", id)
                .put("url", url).put("method", method).put("ts", ts).end());
    }

    @Override
    public void onResponse(String id, int status, String statusText, String mimeType, Map<String, String> headers, long ts) {
        Net n = entries.get(id);
        if (n != null) { n.status = status; n.statusText = statusText; n.mimeType = mimeType; n.resHeaders = headers; }
        raw.writeLine(new Json.Obj().put("phase", "response").put("requestId", id)
                .put("status", status).put("mimeType", mimeType == null ? "" : mimeType).put("ts", ts).end());
    }

    @Override
    public void onFinished(String id, long encodedDataLength, long ts) {
        Net n = entries.get(id);
        String bodyRef = null; long bodySize = -1;
        if (n != null && captureBodies && browser.supports(Capability.RESPONSE_BODY)) {
            try {
                byte[] body = browser.fetchBody(id);
                if (body != null) { bodySize = body.length; bodyRef = store.store(body); }
            } catch (Exception ignored) {}
        }
        if (n != null) { n.finTs = ts; n.encodedDataLength = encodedDataLength; n.bodyRef = bodyRef; n.bodySize = bodySize; }
        raw.writeLine(new Json.Obj().put("phase", "finished").put("requestId", id)
                .put("encodedDataLength", encodedDataLength).put("bodyRef", bodyRef).put("ts", ts).end());
    }

    @Override
    public void onFailed(String id, String errorText, long ts) {
        Net n = entries.get(id);
        if (n != null) { n.failed = true; n.errorText = errorText; n.finTs = ts; }
        raw.writeLine(new Json.Obj().put("phase", "failed").put("requestId", id)
                .put("error", errorText).put("ts", ts).end());
    }

    public long truncatedBodies() { return store.truncatedCount(); }
    public long bytesCaptured() { return store.usedBytes(); }

    private static String headerArray(Map<String, String> h) {
        StringBuilder b = new StringBuilder("[");
        if (h != null) {
            boolean first = true;
            for (Map.Entry<String, String> e : h.entrySet()) {
                if (!first) b.append(',');
                first = false;
                b.append(new Json.Obj().put("name", e.getKey()).put("value", e.getValue()).end());
            }
        }
        return b.append(']').toString();
    }

    /** Build a HAR 1.2 document from the retained entries, sorted by request time. */
    public void buildHar(Path harFile, String browserName) throws IOException {
        List<Net> all = new ArrayList<>(entries.values());
        all.sort((a, b) -> Long.compare(a.reqTs, b.reqTs));

        StringBuilder ent = new StringBuilder("[");
        boolean first = true;
        for (Net n : all) {
            if (!first) ent.append(',');
            first = false;
            long time = (n.finTs >= 0 && n.reqTs >= 0) ? (n.finTs - n.reqTs) : -1;
            String req = "{\"method\":" + Json.str(n.method == null ? "GET" : n.method)
                    + ",\"url\":" + Json.str(n.url)
                    + ",\"httpVersion\":\"HTTP/1.1\",\"cookies\":[],\"queryString\":[]"
                    + ",\"headers\":" + headerArray(n.reqHeaders)
                    + ",\"headersSize\":-1,\"bodySize\":-1}";
            String content = "{\"size\":" + (n.bodySize < 0 ? 0 : n.bodySize)
                    + ",\"mimeType\":" + Json.str(n.mimeType == null ? "" : n.mimeType)
                    + (n.bodyRef != null ? ",\"_file\":" + Json.str(n.bodyRef) : "")
                    + "}";
            String res = "{\"status\":" + n.status
                    + ",\"statusText\":" + Json.str(n.statusText == null ? "" : n.statusText)
                    + ",\"httpVersion\":\"HTTP/1.1\",\"cookies\":[]"
                    + ",\"headers\":" + headerArray(n.resHeaders)
                    + ",\"content\":" + content
                    + ",\"redirectURL\":\"\",\"headersSize\":-1,\"bodySize\":"
                    + (n.encodedDataLength < 0 ? -1 : n.encodedDataLength) + "}";
            Json.Obj o = new Json.Obj()
                    .put("startedDateTime", iso(n.reqTs))
                    .put("time", time)
                    .putRaw("request", req)
                    .putRaw("response", res)
                    .putRaw("cache", "{}")
                    .putRaw("timings", "{\"send\":-1,\"wait\":-1,\"receive\":-1}");
            if (n.failed) o.put("_failed", n.errorText);
            ent.append(o.end());
        }
        ent.append(']');

        String har = "{\"log\":{\"version\":\"1.2\",\"creator\":{\"name\":\"gatf\",\"version\":\"trace\"},"
                + "\"browser\":{\"name\":" + Json.str(browserName == null ? "" : browserName) + ",\"version\":\"\"},"
                + "\"entries\":" + ent + "}}";
        Files.write(harFile, har.getBytes(StandardCharsets.UTF_8));
    }

    private static String iso(long ms) {
        if (ms < 0) return "";
        return java.time.Instant.ofEpochMilli(ms).toString();
    }

    public void flush() { raw.flush(); }
    public void close() { raw.close(); }
}
