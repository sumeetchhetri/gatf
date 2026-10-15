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
package com.gatf.imports;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gatf.executor.core.PerformanceConfig;
import com.gatf.executor.core.TestCase;

/**
 * M17 "load-test this flow": a functional UI run's {@code network.har} (from its trace) becomes a load test. The HAR
 * importer's filtering applies (documents/XHR/fetch only; static assets, preflights and cookies dropped), then:
 * <ul>
 *  <li>one API case per flow step, named {@code METHOD /path} like every HAR import, each carrying a perf config
 *      (engine, users as connections, duration, rate) so a normal API run load-tests every step;</li>
 *  <li>{@code <name>-load.targets.jsonl} (a side file) — the whole flow as vegeta JSON targets (bodies inline, base64), for
 *      {@code vegeta attack -format=json -targets=<file> -rate=N -duration=Ns};</li>
 *  <li>the suite-level load settings (users, duration) to put in gatf-config for a concurrent-users run of the flow.</li>
 * </ul>
 * Pure (Jackson + the importer), so it is tested on fixture HARs.
 */
public final class FlowToLoad {

    public static final String TYPE = "flow";
    public static final String TARGETS_SUFFIX = ".targets.jsonl";
    /** perfConfig.extras for native vegeta: a closed loop of {@code connections} workers (see VegetaRunner.closedLoopWorkers). */
    public static final String CLOSED_LOOP = "closed=1";
    public static final List<String> ENGINES = List.of("wperfk", "vegeta", "autocannon", "wrk", "wrk2");
    /** Δ163m: wperfk is the default load engine (decision D-5). */
    public static final String DEFAULT_ENGINE = "wperfk";

    public static final class Options {
        public int users = 10;
        public int durationSeconds = 30;
        /** requests/second for rate-driven engines (vegeta, wrk2); 0 = the engine's default */
        public int rate = 0;
        public String engine = DEFAULT_ENGINE;

        public Options validate() {
            if (users < 1 || users > 100_000) throw new IllegalArgumentException("users must be 1..100000");
            if (durationSeconds < 1 || durationSeconds > 24 * 3600) throw new IllegalArgumentException("duration must be 1..86400 seconds");
            if (rate < 0) throw new IllegalArgumentException("rate must be >= 0");
            engine = engine == null ? DEFAULT_ENGINE : engine.trim().toLowerCase(Locale.ROOT);
            if (!ENGINES.contains(engine)) throw new IllegalArgumentException("engine must be one of " + ENGINES);
            return this;
        }

        /** Builds options from string values (query parameters / CLI flags); null or blank keeps the default. */
        public static Options of(String users, String duration, String rate, String engine) {
            Options o = new Options();
            if (notBlank(users)) o.users = num(users, "users");
            if (notBlank(duration)) o.durationSeconds = num(duration.replaceAll("(?i)s$", ""), "duration");
            if (notBlank(rate)) o.rate = num(rate, "rate");
            if (notBlank(engine)) o.engine = engine;
            return o.validate();
        }

        private static boolean notBlank(String s) { return s != null && !s.trim().isEmpty(); }

        private static int num(String v, String what) {
            try { return Integer.parseInt(v.trim()); }
            catch (NumberFormatException e) { throw new IllegalArgumentException(what + " must be a number, got '" + v + "'"); }
        }
    }

    private static final ObjectMapper OM = new ObjectMapper();

    private FlowToLoad() {}

    public static ImportResult convert(String har, String suite, String filter, Options opts) throws Exception {
        Options o = (opts == null ? new Options() : opts).validate();
        ImportResult res = new ImportResult();
        res.type = TYPE;
        res.kind = "xml";
        String base = Importers.safeName(suite == null || suite.trim().isEmpty() ? "flow" : suite.trim());
        if (!base.endsWith("-load")) base += "-load";
        res.fileName = base + ".xml";
        List<ApiRequest> reqs = new HarImporter(filter).read(har, res);
        res.testCases = Importers.toTestCases(reqs, res);
        if (res.testCases.isEmpty()) {
            throw new IllegalArgumentException("nothing to load-test: the HAR has no API/document request"
                    + (filter == null ? "" : " matching " + filter));
        }
        int step = 0;
        for (TestCase tc : res.testCases) {
            step++;
            PerformanceConfig pc = new PerformanceConfig();
            pc.setType(o.engine);
            pc.setConnections(o.users);
            pc.setDurationSeconds(o.durationSeconds);
            if (o.rate > 0) pc.setRate(o.rate);
            else if (o.engine.equals("vegeta")) pc.setExtras(CLOSED_LOOP);   // users = concurrent workers, not vegeta's 50 req/s
            tc.setPerfConfig(pc);
            String d = "load step " + step + " of " + res.testCases.size();
            tc.setDescription(tc.getDescription() == null || tc.getDescription().isEmpty() ? d : tc.getDescription() + " — " + d);
        }

        StringBuilder targets = new StringBuilder();
        int inTargets = 0;
        for (ApiRequest r : reqs) {
            if (!ApiRequest.SUPPORTED_METHODS.contains(r.upperMethod())) continue;
            if (!r.formParts.isEmpty()) {
                res.unsupported(r.line, r.upperMethod() + " " + r.url, "multipart body: in the API cases, but not in the vegeta targets file");
                continue;
            }
            String full = fullUrl(r);
            if (full == null) {
                res.unsupported(r.line, r.upperMethod() + " " + r.url, "relative URL: not in the vegeta targets file");
                continue;
            }
            targets.append(vegetaTarget(r, full)).append('\n');
            inTargets++;
        }
        res.sideFiles.put(TARGETS_SUFFIX, targets.toString());

        Map<String, Object> lc = new LinkedHashMap<String, Object>();
        lc.put("loadTestingEnabled", true);
        lc.put("concurrentUserSimulationNum", o.users);
        lc.put("loadTestingTime", o.durationSeconds * 1000L);
        lc.put("concurrentUserRampUpTime", 0);
        res.loadConfig = lc;

        res.stat("engine", o.engine);
        res.stat("users", o.users);
        res.stat("durationSeconds", o.durationSeconds);
        if (o.rate > 0) res.stat("rate", o.rate);
        res.stat("vegetaTargets", inTargets);
        res.stat("vegetaCommand", "vegeta attack -format=json -targets=" + ImportResult.sideFileName(res.fileName, TARGETS_SUFFIX)
                + (o.rate > 0 ? " -rate=" + o.rate : " -rate=0 -max-workers=" + o.users) + " -duration=" + o.durationSeconds + "s | vegeta report");
        return res;
    }

    static String fullUrl(ApiRequest r) {
        if (r.fullUrl != null && r.fullUrl.matches("(?i)https?://.+")) return r.fullUrl;
        if (r.baseUrl != null && r.url != null) return r.baseUrl + (r.url.startsWith("/") ? "" : "/") + r.url;
        return r.url != null && r.url.matches("(?i)https?://.+") ? r.url : null;
    }

    /** One vegeta JSON target: {@code {"method","url","header":{k:[v]},"body":base64}}. */
    static String vegetaTarget(ApiRequest r, String url) {
        ObjectNode t = OM.createObjectNode();
        t.put("method", r.upperMethod());
        t.put("url", url);
        if (!r.headers.isEmpty()) {
            ObjectNode h = t.putObject("header");
            for (Map.Entry<String, String> e : r.headers.entrySet()) h.putArray(e.getKey()).add(e.getValue());
        }
        if (r.body != null && !r.body.isEmpty()) {
            t.put("body", Base64.getEncoder().encodeToString(r.body.getBytes(StandardCharsets.UTF_8)));
        }
        try {
            return OM.writeValueAsString(t);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * A name that does not exist yet: {@code x-load.xml}, else {@code x-load-2.xml}, {@code x-load-3.xml}… so a second
     * click on the same trace never overwrites a load test someone tuned.
     */
    public static String freeName(String fileName, java.util.function.Predicate<String> exists) {
        if (!exists.test(fileName)) return fileName;
        String base = fileName.replaceAll("\\.xml$", "");
        for (int i = 2; i < 1000; i++) {
            String n = base + "-" + i + ".xml";
            if (!exists.test(n)) return n;
        }
        throw new IllegalArgumentException("too many load tests named " + fileName);
    }

    /** The load cases' names, in flow order (for callers that pair them with a report). */
    public static List<String> stepNames(ImportResult r) {
        List<String> out = new ArrayList<String>();
        if (r.testCases != null) for (TestCase tc : r.testCases) out.add(tc.getName());
        return out;
    }
}
