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

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.gatf.executor.core.PerformanceConfig;

/**
 * M13: how one {@link PerformanceConfig} maps onto each built-in engine's command line (after the launch prefix).
 * Everything is pure so the mapping can be tested without the tools.
 */
public final class PerfArgs {

    private PerfArgs() {}

    /** Defaults shared by the wrk family: 10 connections, 10 s, 10 threads; wrk2 needs a rate (1000/s). */
    public static int connections(PerformanceConfig pc) { return pc.getConnections() > 0 ? pc.getConnections() : 10; }
    public static int duration(PerformanceConfig pc) { return pc.getDurationSeconds() > 0 ? pc.getDurationSeconds() : 10; }
    public static int threads(PerformanceConfig pc) { return pc.getThreads() > 0 ? pc.getThreads() : 10; }

    /**
     * wrk / wrk2 / wperfk flags: {@code -c -R -d -t [--timeout] [--latency] [--u_latency]}. wrk2 always sends a rate
     * (default 1000). wperfk (wrk/wrk2-compatible) sends {@code -R} only when {@code rate > 0}; without it it runs wrk's
     * closed-loop max-throughput mode. {@code --u_latency} is wrk2-only.
     */
    public static List<String> wrk(String tool, PerformanceConfig pc) {
        List<String> a = new ArrayList<String>();
        a.add("-c");
        a.add(String.valueOf(connections(pc)));
        if ("wrk2".equals(tool)) {
            a.add("-R");
            a.add(String.valueOf(pc.getRate() > 0 ? pc.getRate() : 1000));
        } else if ("wperfk".equals(tool) && pc.getRate() > 0) {
            a.add("-R");
            a.add(String.valueOf(pc.getRate()));
        }
        a.add("-d");
        a.add(duration(pc) + "s");
        a.add("-t");
        a.add(String.valueOf(threads(pc)));
        if (pc.getTimeout() > 0) {
            a.add("--timeout");
            a.add(pc.getTimeout() + "s");
        }
        if (pc.isLatency()) a.add("--latency");
        if ("wrk2".equals(tool)) a.add("--u_latency");
        return a;
    }

    /**
     * The Lua request script the wrk family gets when perfConfig.filePath is not set and the request has headers, a body
     * or a non-GET method. Every value is a quoted, escaped Lua string. The body is not a long bracket, because Lua
     * rewrites CRLF inside one and some endings (e.g. {@code x]=}) close it early. The old builder dropped the closing
     * quote of the header name, which made every generated script a Lua syntax error.
     */
    public static String lua(String method, Map<String, String> headers, String body) {
        StringBuilder b = new StringBuilder();
        b.append("wrk.method = \"").append(luaStr(method == null || method.trim().isEmpty() ? "GET" : method.trim().toUpperCase())).append("\"\n");
        if (headers != null) {
            for (Map.Entry<String, String> h : headers.entrySet()) {
                b.append("wrk.headers[\"").append(luaStr(h.getKey())).append("\"] = \"").append(luaStr(h.getValue() == null ? "" : h.getValue())).append("\"\n");
            }
        }
        if (body != null && !body.isEmpty()) b.append("wrk.body = \"").append(luaStr(body)).append("\"\n");
        return b.toString();
    }

    static String luaStr(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '\\': b.append("\\\\"); break;
                case '"': b.append("\\\""); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\0': b.append("\\000"); break;   // \0 followed by a digit would read as one escape
                default: b.append(c);
            }
        }
        return b.toString();
    }

    /** autocannon argv (no shell: header values and URLs are passed verbatim). */
    public static List<String> autocannon(String method, String url, Map<String, String> headers, File body, int connections,
            int workers, int rate, int durationSecs, int timeoutSecs) {
        List<String> a = new ArrayList<String>();
        a.add("--json");
        a.add("--no-progress");
        if (connections > 0) { a.add("-c"); a.add(String.valueOf(connections)); }
        if (workers > 0) { a.add("-w"); a.add(String.valueOf(workers)); }
        if (rate > 0) { a.add("-R"); a.add(String.valueOf(rate)); }
        a.add("-d"); a.add(String.valueOf(durationSecs));
        if (timeoutSecs > 0) { a.add("-t"); a.add(String.valueOf(timeoutSecs)); }
        if (method != null && !method.trim().isEmpty()) { a.add("-m"); a.add(method.trim().toUpperCase()); }
        if (body != null) { a.add("-i"); a.add(body.getAbsolutePath()); }
        if (headers != null) {
            for (Map.Entry<String, String> h : headers.entrySet()) {
                a.add("-H");
                a.add(h.getKey() + "=" + (h.getValue() == null ? "" : h.getValue()));
            }
        }
        a.add(url);
        return a;
    }
}
