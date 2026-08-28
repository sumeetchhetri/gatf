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
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Samples runtime metrics on an interval (CDP Performance.getMetrics via the transport) and
 * streams them to {@code performance.ndjson}; at finalize it emits {@code performance.json}
 * (the full metric series + web vitals) and {@code memory.json} (the JS-heap series). Web
 * vitals (FCP/LCP/TTFB/DCL/Load) are read once from the Performance Timeline at stop.
 *
 * Metric samples are small and one-per-interval, so they are retained in a concurrent list to
 * build the JSON views — the heavy trace data (bodies, snapshots, screencast frames) is what
 * streams to disk elsewhere.
 *
 * @author Sumeet Chhetri
 */
public final class MetricsRecorder {

    private static final String HEAP_USED = "JSHeapUsedSize";
    private static final String HEAP_TOTAL = "JSHeapTotalSize";

    static final class Sample {
        final long ts;
        final Map<String, Number> metrics;
        Sample(long ts, Map<String, Number> metrics) { this.ts = ts; this.metrics = metrics; }
    }

    private final BrowserControl browser;
    private final Path traceDir;
    private final boolean webVitals;
    private final NdjsonWriter raw;
    private final Sampler sampler;
    private final List<Sample> samples = new CopyOnWriteArrayList<>();
    private volatile String vitalsJson;

    public MetricsRecorder(BrowserControl browser, Path traceDir, TraceConfig cfg) throws IOException {
        this.browser = browser;
        this.traceDir = traceDir;
        this.webVitals = cfg.isWebVitals();
        this.raw = new NdjsonWriter(traceDir.resolve("performance.ndjson"));
        this.sampler = new Sampler(cfg.getMetricsIntervalMs(), this::sample);
    }

    public void start() {
        try { browser.enableMetrics(); } catch (Exception ignored) {}
        sample();            // one immediate sample
        sampler.start();
    }

    private void sample() {
        Map<String, Number> m;
        try { m = browser.sampleMetrics(); } catch (Exception e) { return; }
        if (m == null || m.isEmpty()) return;
        long ts = System.currentTimeMillis();
        samples.add(new Sample(ts, m));
        Json.Obj o = new Json.Obj().put("ts", ts);
        for (Map.Entry<String, Number> e : m.entrySet()) {
            o.putRaw(e.getKey(), e.getValue() == null ? "null" : e.getValue().toString());
        }
        raw.writeLine(o.end());
    }

    public void stop() {
        sampler.stop();
        sample();            // final sample
        try { if (webVitals) vitalsJson = browser.readWebVitals(); } catch (Exception ignored) {}
        try { browser.disableMetrics(); } catch (Exception ignored) {}
        raw.flush();
        raw.close();
        try { writePerformanceJson(); } catch (Exception ignored) {}
        try { writeMemoryJson(); } catch (Exception ignored) {}
    }

    private void writePerformanceJson() throws IOException {
        StringBuilder arr = new StringBuilder("[");
        boolean first = true;
        for (Sample s : samples) {
            if (!first) arr.append(',');
            first = false;
            Json.Obj o = new Json.Obj().put("ts", s.ts);
            for (Map.Entry<String, Number> e : s.metrics.entrySet()) {
                o.putRaw(e.getKey(), e.getValue() == null ? "null" : e.getValue().toString());
            }
            arr.append(o.end());
        }
        arr.append(']');
        String doc = "{\"samples\":" + arr
                + (vitalsJson != null && !vitalsJson.isEmpty() ? ",\"webVitals\":" + vitalsJson : "")
                + "}";
        Files.write(traceDir.resolve("performance.json"), doc.getBytes(StandardCharsets.UTF_8));
    }

    private void writeMemoryJson() throws IOException {
        List<String> rows = new ArrayList<>();
        for (Sample s : samples) {
            Number used = s.metrics.get(HEAP_USED);
            Number total = s.metrics.get(HEAP_TOTAL);
            if (used == null && total == null) continue;
            rows.add(new Json.Obj().put("ts", s.ts)
                    .putRaw("jsHeapUsed", used == null ? "null" : used.toString())
                    .putRaw("jsHeapTotal", total == null ? "null" : total.toString())
                    .end());
        }
        String doc = "{\"samples\":[" + String.join(",", rows) + "]}";
        Files.write(traceDir.resolve("memory.json"), doc.getBytes(StandardCharsets.UTF_8));
    }
}
