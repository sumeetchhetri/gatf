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
package com.gatf.executor.perf;

import java.util.List;
import java.util.Map;

/**
 * M13: the common keys every perf result map gets, whatever tool produced it. They let one report or assertion read
 * any engine's result. The tool's own keys are kept as they are. The common keys are:
 * <pre>
 *   tool, total_requests, rps, lat_avg_ms, lat_p50_ms, lat_p90_ms, lat_p99_ms, lat_max_ms,
 *   transport_errors (connect/read/write/timeout), non_2xx (HTTP status outside 2xx/3xx)
 * </pre>
 * The names avoid every tool's own keys (vegeta's {@code errors} is a list; autocannon's {@code requests} a map). A key
 * the tool does not report is left out, never guessed. Key sources per tool:
 * <ul>
 *   <li>wrk family: parsed stdout</li>
 *   <li>vegeta: report JSON, latencies in ns</li>
 *   <li>autocannon: {@code --json}, latencies in ms</li>
 * </ul>
 * A third-party engine can set the common keys itself. Normalising never overwrites a key already present. Pure.
 */
public final class PerfSummary {

    public static final String[] KEYS = { "tool", "total_requests", "rps", "lat_avg_ms", "lat_p50_ms", "lat_p90_ms", "lat_p99_ms",
            "lat_max_ms", "transport_errors", "non_2xx" };

    private PerfSummary() {}

    public static void normalizeAll(String tool, List<Map<String, Object>> results) {
        if (results == null) return;
        for (Map<String, Object> r : results) {
            if (r == null) continue;
            try {
                normalize(tool, r);
            } catch (UnsupportedOperationException e) {
                // an engine's immutable map: its own keys stand, the run's status is not touched
            }
        }
    }

    /** Adds the missing common keys to {@code r} in place and returns it. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> normalize(String tool, Map<String, Object> r) {
        String t = r.get("tool") instanceof String ? (String) r.get("tool") : tool;
        put(r, "tool", t);
        if (r.containsKey("fallback_from") || "vegeta".equals(t)) {
            Map<String, Object> lat = r.get("latencies") instanceof Map ? (Map<String, Object>) r.get("latencies") : null;
            put(r, "total_requests", lng(r.get("requests")));
            // all responses over attack + wait time, as wrk and autocannon count (vegeta's "throughput" is successes only)
            Double reqs = dbl(r.get("requests")), dur = dbl(r.get("duration")), wait = dbl(r.get("wait"));
            Double rps = reqs != null && dur != null && dur + (wait == null ? 0 : wait) > 0
                    ? reqs / ((dur + (wait == null ? 0 : wait)) / 1_000_000_000d) : dbl(r.get("rate"));
            put(r, "rps", rps);
            if (lat != null) {
                put(r, "lat_avg_ms", nsToMs(lat.get("mean")));
                put(r, "lat_p50_ms", nsToMs(lat.get("50th")));
                put(r, "lat_p90_ms", nsToMs(lat.get("90th")));
                put(r, "lat_p99_ms", nsToMs(lat.get("99th")));
                put(r, "lat_max_ms", nsToMs(lat.get("max")));
            }
            if (r.get("status_codes") instanceof Map) {
                long transport = 0, bad = 0;
                for (Map.Entry<String, Object> e : ((Map<String, Object>) r.get("status_codes")).entrySet()) {
                    Long n = lng(e.getValue());
                    if (n == null) continue;
                    int code;
                    try {
                        code = Integer.parseInt(e.getKey().trim());
                    } catch (NumberFormatException x) {
                        continue;
                    }
                    if (code == 0) transport += n;
                    else if (code < 200 || code >= 400) bad += n;
                }
                put(r, "transport_errors", transport);
                put(r, "non_2xx", bad);
            }
        } else if ("autocannon".equals(t)) {
            Map<String, Object> lat = r.get("latency") instanceof Map ? (Map<String, Object>) r.get("latency") : null;
            Map<String, Object> req = r.get("requests") instanceof Map ? (Map<String, Object>) r.get("requests") : null;
            if (req != null) {
                put(r, "total_requests", lng(req.get("total")));
                put(r, "rps", dbl(req.get("average")));
            }
            if (lat != null) {
                put(r, "lat_avg_ms", dbl(lat.get("average")));
                put(r, "lat_p50_ms", dbl(lat.get("p50")));
                put(r, "lat_p90_ms", dbl(lat.get("p90")));
                put(r, "lat_p99_ms", dbl(lat.get("p99")));
                put(r, "lat_max_ms", dbl(lat.get("max")));
            }
            put(r, "transport_errors", lng(r.get("errors")));
            // autocannon's non2xx includes 3xx; count 1xx/4xx/5xx like wrk ("Non-2xx or 3xx") and vegeta
            Long c1 = lng(r.get("1xx")), c4 = lng(r.get("4xx")), c5 = lng(r.get("5xx"));
            put(r, "non_2xx", c1 != null || c4 != null || c5 != null
                    ? Long.valueOf((c1 == null ? 0 : c1) + (c4 == null ? 0 : c4) + (c5 == null ? 0 : c5)) : lng(r.get("non2xx")));
        } else if (r.containsKey("tot_requests") || r.containsKey("lat_avg") || "wrk".equals(t) || "wrk2".equals(t) || "wperfk".equals(t)) {
            put(r, "total_requests", lng(r.get("tot_requests")));
            put(r, "rps", dbl(r.get("req_sec_tot")));
            put(r, "lat_avg_ms", dbl(r.get("lat_avg")));
            put(r, "lat_p50_ms", dbl(r.get("lat_dis_50")));
            put(r, "lat_p90_ms", dbl(r.get("lat_dis_90")));
            put(r, "lat_p99_ms", dbl(r.get("lat_dis_99")));
            put(r, "lat_max_ms", dbl(r.get("lat_max")));
            long errs = 0;
            boolean any = false;
            for (String k : new String[] { "err_connect", "err_read", "err_write", "err_timeout" }) {
                Long n = lng(r.get(k));
                if (n != null) { errs += n; any = true; }
            }
            if (any) put(r, "transport_errors", errs);
            put(r, "non_2xx", r.containsKey("non_2xx_resp") ? lng(r.get("non_2xx_resp")) : Long.valueOf(0));
        }
        return r;
    }

    private static void put(Map<String, Object> r, String k, Object v) {
        if (v != null && !r.containsKey(k)) r.put(k, v);
    }

    private static Double nsToMs(Object o) {
        Double d = dbl(o);
        return d == null ? null : d / 1_000_000d;
    }

    static Double dbl(Object o) {
        if (o instanceof Number) return ((Number) o).doubleValue();
        if (o instanceof String) {
            try {
                return Double.valueOf(((String) o).trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    static Long lng(Object o) {
        Double d = dbl(o);
        return d == null ? null : Long.valueOf(Math.round(d));
    }
}
