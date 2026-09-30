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
package com.gatf.executor.ext;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * vegeta without a shell: http-format targets file → {@code attack -output} → {@code report} (text),
 * {@code report -type=json}, optional {@code plot} — each a plain argv process. Works identically on
 * Windows (the old {@code echo | attack | tee | report} pipeline needed bash; under cmd.exe it broke).
 */
public final class VegetaRunner {

    public static final class Result {
        public int rc;
        public List<String> attackCmd;
        public String text = "";
        public String json;
        public File plotHtml;
        public String err = "";
    }

    private VegetaRunner() {}

    /** vegeta "http" target format: METHOD URL, header lines, optional @body-file, blank line. */
    static String targets(String method, String url, Map<String, String> headers, File body) {
        StringBuilder b = new StringBuilder();
        b.append(method == null || method.trim().isEmpty() ? "GET" : method.trim().toUpperCase()).append(' ').append(url).append('\n');
        if (headers != null) {
            for (Map.Entry<String, String> h : headers.entrySet()) {
                b.append(h.getKey()).append(": ").append(h.getValue() == null ? "" : h.getValue()).append('\n');
            }
        }
        if (body != null) b.append('@').append(body.getAbsolutePath()).append('\n');
        return b.append('\n').toString();
    }

    /**
     * M17: {@code perfConfig.extras} {@code closed=1} with no rate = a closed loop of {@code connections} workers
     * ({@code -rate=0 -max-workers=N}), i.e. N concurrent users, instead of vegeta's default 50 req/s. Returns the
     * max-workers to use, or 0 to keep the open-loop default. Opt-in, so existing perf configs run unchanged.
     */
    static int closedLoopWorkers(String extras, int rate, int connections) {
        if (rate > 0 || extras == null || !extras.matches("(?s).*(^|[\\s,;&])closed=1([\\s,;&]|$).*")) return 0;
        return connections > 0 ? connections : 10;
    }

    /** attack argv after the vegeta prefix. rate>0 → constant rate; else maxWorkers>0 → -rate=0 (max throughput). */
    static List<String> attackArgs(File targets, File out, int connections, int workers, int rate, int maxWorkers,
            int durationSecs, int timeoutSecs) {
        List<String> a = new ArrayList<String>(Arrays.asList("attack", "-format", "http", "-targets",
                targets.getAbsolutePath(), "-output", out.getAbsolutePath(), "-duration", durationSecs + "s"));
        if (rate > 0) {
            a.addAll(Arrays.asList("-rate", String.valueOf(rate)));
        } else if (maxWorkers > 0) {
            a.addAll(Arrays.asList("-rate", "0", "-max-workers", String.valueOf(maxWorkers)));
        }
        if (connections > 0) a.addAll(Arrays.asList("-connections", String.valueOf(connections)));
        if (workers > 0) a.addAll(Arrays.asList("-workers", String.valueOf(workers)));
        if (timeoutSecs > 0) a.addAll(Arrays.asList("-timeout", timeoutSecs + "s"));
        return a;
    }

    public static Result run(List<String> vegeta, String method, String url, Map<String, String> headers, File body,
            int connections, int workers, int rate, int maxWorkers, int durationSecs, int timeoutSecs, boolean plot)
            throws Exception {
        Result r = new Result();
        File tg = File.createTempFile("gatf-vegeta-", ".targets");
        File bin = File.createTempFile("gatf-vegeta-", ".bin");
        File so = File.createTempFile("gatf-vegeta-", ".out");
        File se = File.createTempFile("gatf-vegeta-", ".err");
        try {
            Files.write(tg.toPath(), targets(method, url, headers, body).getBytes(StandardCharsets.UTF_8));
            r.attackCmd = new ArrayList<String>(vegeta);
            r.attackCmd.addAll(attackArgs(tg, bin, connections, workers, rate, maxWorkers, durationSecs, timeoutSecs));
            r.rc = PerfToolProvisioner.exec(r.attackCmd, so, se);
            r.err = read(se);
            if (bin.length() == 0) return r;

            if (PerfToolProvisioner.exec(with(vegeta, "report", bin.getAbsolutePath()), so, se) == 0) r.text = read(so);
            if (PerfToolProvisioner.exec(with(vegeta, "report", "-type=json", bin.getAbsolutePath()), so, se) == 0) r.json = read(so);
            else r.err += read(se);
            if (plot) {
                File html = File.createTempFile("gatf-vegeta-plot-", ".html");
                if (PerfToolProvisioner.exec(with(vegeta, "plot", bin.getAbsolutePath()), html, se) == 0) r.plotHtml = html;
                else html.delete();
            }
            return r;
        } finally {
            tg.delete();
            bin.delete();
            so.delete();
            se.delete();
        }
    }

    private static List<String> with(List<String> prefix, String... args) {
        List<String> l = new ArrayList<String>(prefix);
        l.addAll(Arrays.asList(args));
        return l;
    }

    private static String read(File f) throws Exception {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }
}
