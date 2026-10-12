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
package com.gatf.coverage;

import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * H6 (Δ155): merges per-test JS coverage, CSS rule usage and visited pages with a crawl map into the
 * versioned coverage index ({@value #FORMAT} v{@value #VERSION}). Pure: no files, browsers or servers;
 * {@link CoverageIndex} feeds it from a run folder.
 *
 * <p>Only URLs (via {@link CoverageUrls}) and numeric ranges are kept. Raw CDP fields other than
 * url/functions/ranges/count/offsets are skipped unread, so a {@code scriptSource} or any other text in the
 * input never reaches the output. Bad input is recorded as a skip, never thrown.
 *
 * <p>v2 (Δ155a): every test carries {@code coverage} (ok | empty | capped | old | malformed | missing, the worst
 * over its traces) and every skip names its {@code test}; executed functions are a distinct set, not a union;
 * script bodies are recorded per test script, and ranges are never unioned across different bodies of one URL.
 *
 * @author Sumeet Chhetri
 */
public final class CoverageMerge {

    public static final String FORMAT = "gatf-coverage-index";
    public static final int VERSION = 2;

    /** Bounds for one merge. Anything over a bound is skipped and listed under {@code skipped}. */
    public static final class Limits {
        public int maxTraces = 500;
        public long maxEntryBytes = 32L << 20;       // one coverage.json / network.har
        public long maxTotalBytes = 256L << 20;      // everything read for one run
        public int maxZipEntries = 50_000;
        public int maxManifestsPerTrace = 2_000;     // snapshots/snapshot-*.json read for page URLs
        public int maxScriptsPerTrace = 5_000;
        public int maxRangesPerScript = 20_000;      // merged covered ranges kept per test + script
        public long maxCrawlBytes = 8L << 20;
        public int maxRoutes = 20_000;

        String fingerprint() {
            return maxTraces + "," + maxEntryBytes + "," + maxTotalBytes + "," + maxZipEntries + "," + maxManifestsPerTrace
                    + "," + maxScriptsPerTrace + "," + maxRangesPerScript + "," + maxCrawlBytes + "," + maxRoutes;
        }
    }

    /**
     * Outcome of a trace's coverage.json, in increasing order of badness; a test's {@code coverage} is the worst of
     * its traces' outcomes. Anything but OK means the test's script list can't be trusted to be complete.
     */
    public enum Read {
        OK("ok"), EMPTY("empty"), CAPPED("capped"), OLD_FORMAT("old"), MALFORMED("malformed"), MISSING("missing");
        public final String label;
        Read(String label) { this.label = label; }
    }

    static final ObjectMapper OM = new ObjectMapper();
    static final String UNKNOWN_BODY = "";

    private final Limits limits;
    private final SortedMap<String, TestAcc> tests = new TreeMap<>();
    private final Map<String, Map<String, SortedSet<String>>> traceBodies = new TreeMap<>();   // trace -> url -> sha1s
    private final List<Map<String, Object>> skipped = new ArrayList<>();
    private final SortedSet<String> notes = new TreeSet<>();
    private SortedMap<String, String> crawl;      // route -> priority (or "")
    private String crawlSource;
    private int traces, tracesWithCoverage, tracesCoverageRead;
    private int anonymousScripts;

    public CoverageMerge(Limits limits) {
        this.limits = limits == null ? new Limits() : limits;
    }

    static final class TestAcc {
        final SortedSet<String> traces = new TreeSet<>();
        final SortedSet<String> routes = new TreeSet<>();
        // url -> body sha1 (or UNKNOWN_BODY when the trace captured no single body for it) -> accumulated coverage
        final SortedMap<String, SortedMap<String, ScriptAcc>> scripts = new TreeMap<>();
        Read coverage;                                          // worst over the test's traces; null = none recorded
        long cssRules, cssTotal, cssUsed;
        final SortedSet<String> cssSheets = new TreeSet<>();   // per-trace sheet ids, only counted
    }

    static final class ScriptAcc {
        long len;
        List<long[]> ranges = new ArrayList<>();
        final SortedSet<long[]> functions = new TreeSet<>(CoverageMerge::cmp);   // distinct (start, end) pairs
        int functionsTotal;
        boolean truncated;
    }

    static int cmp(long[] x, long[] y) {
        return x[0] != y[0] ? Long.compare(x[0], y[0]) : Long.compare(x[1], y[1]);
    }

    // ------------------------------------------------------------------ input

    /** A trace was considered for this test. Without coverage.json the test's coverage is {@code missing}. */
    public void trace(String testId, String traceName, boolean hasCoverage) {
        traces++;
        if (hasCoverage) tracesWithCoverage++;
        test(testId).traces.add(traceName);
        if (!hasCoverage) {
            mark(testId, Read.MISSING);
            skip(traceName, testId, "no coverage.json (trace coverage off for this run, or not a Chromium browser)");
        }
    }

    /** Records a coverage outcome decided outside {@link #coverage} (e.g. a file over a size cap, never read). */
    public void mark(String testId, Read r) {
        TestAcc ta = test(testId);
        if (ta.coverage == null || r.ordinal() > ta.coverage.ordinal()) ta.coverage = r;
    }

    public void skip(String file, String testId, String reason) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("file", file);
        m.put("test", testId);
        m.put("reason", reason);
        skipped.add(m);
    }

    public void note(String n) { notes.add(n); }

    /** A page the test visited (raw URL; stored as {@link CoverageUrls#route}). */
    public void visit(String testId, String rawUrl) {
        String r = CoverageUrls.route(rawUrl);
        if (r != null) test(testId).routes.add(r);
    }

    /**
     * The sha1 of a script body captured in a trace (resources/&lt;sha1&gt;). Call it before {@link #coverage} for the
     * same trace: the trace's coverage of that URL is then filed under that body.
     */
    public void body(String traceName, String rawUrl, String sha1) {
        String u = CoverageUrls.script(rawUrl);
        if (u == null || sha1 == null || !sha1.matches("[0-9a-f]{40}")) return;
        traceBodies.computeIfAbsent(traceName, k -> new TreeMap<>()).computeIfAbsent(u, k -> new TreeSet<>()).add(sha1);
    }

    /** Reads one raw coverage.json ({@code {js:[ScriptCoverage], css:[RuleUsage]}}) for a test. Never throws. */
    public Read coverage(String testId, String traceName, InputStream in) {
        Read r = read(testId, traceName, in);
        mark(testId, r);
        return r;
    }

    private Read read(String testId, String traceName, InputStream in) {
        int scripts = 0;
        long cssBefore = test(testId).cssRules;
        boolean any = false, nulls = false;
        try (JsonParser p = OM.createParser(in)) {
            if (p.nextToken() != JsonToken.START_OBJECT) return oldFormat(testId, traceName);
            while (p.nextToken() == JsonToken.FIELD_NAME) {
                String f = p.getCurrentName();
                JsonToken t = p.nextToken();
                if ("js".equals(f) && t == JsonToken.START_ARRAY) {
                    any = true;
                    while (p.nextToken() == JsonToken.START_OBJECT) {
                        JsonNode s = p.readValueAsTree();
                        if (++scripts > limits.maxScriptsPerTrace) {
                            skip(traceName, testId, "cap: more than " + limits.maxScriptsPerTrace + " scripts; the rest of coverage.json was not read");
                            tracesCoverageRead++;
                            return Read.CAPPED;
                        }
                        addScript(testId, traceName, s);
                    }
                } else if ("css".equals(f) && t == JsonToken.START_ARRAY) {
                    any = true;
                    TestAcc ta = test(testId);
                    while (p.nextToken() == JsonToken.START_OBJECT) addCss(ta, traceName, p.readValueAsTree());
                } else if ("js".equals(f) || "css".equals(f)) {
                    if (t != JsonToken.VALUE_NULL) return oldFormat(testId, traceName);
                    nulls = true;                        // CoverageRecorder writes null when that half of the capture failed
                } else {
                    p.skipChildren();
                }
            }
        } catch (CoverageIndex.CapExceeded e) {
            skip(traceName, testId, "cap: coverage.json is larger than " + e.cap + " bytes; partly read");
            if (scripts > 0 || test(testId).cssRules > cssBefore) tracesCoverageRead++;
            return Read.CAPPED;
        } catch (Exception e) {
            skip(traceName, testId, "coverage.json is not valid JSON (" + e.getClass().getSimpleName() + ")");
            return Read.MALFORMED;
        }
        if (!any && nulls) {
            skip(traceName, testId, "coverage.json is empty (the browser returned no coverage)");
            return Read.EMPTY;
        }
        if (!any) return oldFormat(testId, traceName);
        tracesCoverageRead++;
        return scripts == 0 && test(testId).cssRules == cssBefore ? Read.EMPTY : Read.OK;
    }

    private Read oldFormat(String testId, String traceName) {
        skip(traceName, testId, "coverage.json has an unknown or old format (expected {js:[…], css:[…]})");
        return Read.OLD_FORMAT;
    }

    private void addScript(String testId, String traceName, JsonNode s) {
        String url = CoverageUrls.script(s.path("url").asText(null));
        if (url == null) { anonymousScripts++; return; }
        List<long[]> raw = new ArrayList<>();
        List<long[]> fnRanges = new ArrayList<>();
        long len = 0;
        for (JsonNode fn : s.path("functions")) {
            JsonNode rs = fn.path("ranges");
            boolean first = true;
            for (JsonNode r : rs) {
                long a = r.path("startOffset").asLong(-1), b = r.path("endOffset").asLong(-1), c = r.path("count").asLong(0);
                if (a < 0 || b <= a) { first = false; continue; }
                raw.add(new long[] {a, b, c});
                if (b > len) len = b;
                if (first) fnRanges.add(new long[] {a, b, c});
                first = false;
            }
        }
        if (raw.isEmpty()) return;
        // the script's top-level function spans [0, len); every other first range is a function
        List<long[]> executedFns = new ArrayList<>();
        int fnTotal = 0;
        boolean topSeen = false;
        for (long[] f : fnRanges) {
            if (!topSeen && f[0] == 0 && f[1] == len) { topSeen = true; continue; }
            fnTotal++;
            if (f[2] > 0) executedFns.add(new long[] {f[0], f[1]});
        }
        // file the coverage under the body this trace captured for the URL (one body), else under "unknown"
        Map<String, SortedSet<String>> tb = traceBodies.get(traceName);
        SortedSet<String> bs = tb == null ? null : tb.get(url);
        String bodyKey = bs != null && bs.size() == 1 ? bs.first() : UNKNOWN_BODY;
        if (bs != null && bs.size() > 1) {
            note("a trace loaded more than one body for one script URL: that coverage is filed under an unknown body");
        }
        ScriptAcc sa = test(testId).scripts.computeIfAbsent(url, k -> new TreeMap<>()).computeIfAbsent(bodyKey, k -> new ScriptAcc());
        sa.len = Math.max(sa.len, len);
        sa.functionsTotal = Math.max(sa.functionsTotal, fnTotal);
        sa.ranges = union(sa.ranges, covered(raw));
        sa.functions.addAll(executedFns);
        if (sa.ranges.size() > limits.maxRangesPerScript) {
            sa.ranges = new ArrayList<>(sa.ranges.subList(0, limits.maxRangesPerScript));
            if (!sa.truncated) skip(traceName, testId, "cap: " + url + " has more than " + limits.maxRangesPerScript + " covered ranges; truncated");
            sa.truncated = true;
        }
        while (sa.functions.size() > limits.maxRangesPerScript) {
            sa.functions.remove(sa.functions.last());
            sa.truncated = true;
        }
    }

    private void addCss(TestAcc ta, String traceName, JsonNode r) {
        long a = r.path("startOffset").asLong(-1), b = r.path("endOffset").asLong(-1);
        if (a < 0 || b < a) return;
        ta.cssRules++;
        ta.cssTotal += b - a;
        if (r.path("used").asBoolean(false)) ta.cssUsed += b - a;
        String id = r.path("styleSheetId").asText("");
        if (ta.cssSheets.size() < 10_000) ta.cssSheets.add(traceName + "\u0000" + id);
    }

    /** Reads a crawl map: the SPA crawl's {@code <name>-coverage.json} (an array of {url, priority…}) or the portal
     *  crawl's {@code crawl-coverage.json} ({pages:[{url}]}). Returns false (and records why) on anything else. */
    public boolean crawl(String source, InputStream in) {
        crawlSource = source;
        SortedMap<String, String> out = new TreeMap<>();
        try {
            JsonNode root = OM.readTree(in);
            JsonNode pages = root == null ? null : root.isArray() ? root : root.path("pages");
            if (pages == null || !pages.isArray()) {
                skip(source, null, "not a crawl map (expected [{url…}] or {pages:[{url…}]})");
                crawl = null;
                return false;
            }
            for (JsonNode pg : pages) {
                String r = CoverageUrls.route(pg.path("url").asText(null));
                if (r == null) continue;
                if (out.size() >= limits.maxRoutes) {
                    skip(source, null, "cap: more than " + limits.maxRoutes + " crawl routes; the rest were not read");
                    break;
                }
                String pr = pg.path("priority").asText("");
                if (!pr.matches("high|medium|low|error")) pr = "";
                String prev = out.get(r);
                if (prev == null || prev.isEmpty()) out.put(r, pr);
            }
        } catch (CoverageIndex.CapExceeded e) {
            skip(source, null, "cap: crawl map is larger than " + e.cap + " bytes");
            crawl = null;
            return false;
        } catch (Exception e) {
            skip(source, null, "crawl map is not valid JSON (" + e.getClass().getSimpleName() + ")");
            crawl = null;
            return false;
        }
        crawl = out;
        return true;
    }

    private TestAcc test(String id) {
        return tests.computeIfAbsent(testKey(id), k -> new TestAcc());
    }

    private static String testKey(String id) {
        return id == null || id.isEmpty() ? "(unknown)" : id;
    }

    // ------------------------------------------------------------------ ranges

    /**
     * V8 block coverage: ranges nest, and the innermost range containing an offset gives its count. Returns the
     * merged half-open [start, end) spans whose count is &gt; 0.
     */
    static List<long[]> covered(List<long[]> raw) {
        List<long[]> rs = new ArrayList<>(raw);
        rs.sort((x, y) -> x[0] != y[0] ? Long.compare(x[0], y[0]) : Long.compare(y[1], x[1]));
        List<long[]> out = new ArrayList<>();
        Deque<long[]> st = new ArrayDeque<>();
        long pos = 0;
        for (long[] r : rs) {
            long s = r[0], e = r[1];
            while (!st.isEmpty() && st.peek()[1] <= s) {
                long[] t = st.pop();
                emit(out, pos, t[1], t[2]);
                pos = Math.max(pos, t[1]);
            }
            if (!st.isEmpty()) {
                long[] t = st.peek();
                emit(out, pos, s, t[2]);
                if (e > t[1]) e = t[1];          // malformed overlap: clamp into the parent
            }
            pos = Math.max(pos, s);
            if (e > s) st.push(new long[] {s, e, r[2]});
        }
        while (!st.isEmpty()) {
            long[] t = st.pop();
            emit(out, pos, t[1], t[2]);
            pos = Math.max(pos, t[1]);
        }
        return out;
    }

    private static void emit(List<long[]> out, long a, long b, long count) {
        if (b <= a || count <= 0) return;
        if (!out.isEmpty() && out.get(out.size() - 1)[1] >= a) {
            long[] last = out.get(out.size() - 1);
            if (b > last[1]) last[1] = b;
            return;
        }
        out.add(new long[] {a, b});
    }

    /** Sorted union of two interval lists (each [start, end)). Used for covered ranges only, never for functions. */
    static List<long[]> union(List<long[]> a, List<long[]> b) {
        List<long[]> all = new ArrayList<>(a.size() + b.size());
        for (long[] x : a) all.add(new long[] {x[0], x[1]});
        for (long[] x : b) all.add(new long[] {x[0], x[1]});
        all.sort(CoverageMerge::cmp);
        List<long[]> out = new ArrayList<>();
        for (long[] x : all) {
            if (x[1] <= x[0]) continue;
            if (!out.isEmpty() && out.get(out.size() - 1)[1] >= x[0]) {
                long[] last = out.get(out.size() - 1);
                if (x[1] > last[1]) last[1] = x[1];
            } else {
                out.add(x);
            }
        }
        return out;
    }

    static long length(Collection<long[]> ivals) {
        long t = 0;
        for (long[] x : ivals) t += x[1] - x[0];
        return t;
    }

    private static List<List<Long>> json(Collection<long[]> ivals) {
        List<List<Long>> out = new ArrayList<>(ivals.size());
        for (long[] x : ivals) {
            List<Long> p = new ArrayList<>(2);
            p.add(x[0]);
            p.add(x[1]);
            out.add(p);
        }
        return out;
    }

    /** One body group's (or a merge of compatible groups') coverage of a script. */
    private static final class Agg {
        long len;
        List<long[]> ranges = new ArrayList<>();
        final SortedSet<long[]> functions = new TreeSet<>(CoverageMerge::cmp);
        int functionsTotal;
        boolean truncated;

        void add(ScriptAcc s) {
            len = Math.max(len, s.len);
            ranges = union(ranges, s.ranges);
            functions.addAll(s.functions);
            functionsTotal = Math.max(functionsTotal, s.functionsTotal);
            truncated |= s.truncated;
        }

        long used() { return Math.min(length(ranges), len); }
    }

    // ------------------------------------------------------------------ output

    /** Format rules a consumer (Δ156 test-impact selection) must follow. Part of the index, versioned with it. */
    public static Map<String, Object> rules() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("offsets", "ranges and functions are half-open [start, end) offsets into the script as served, as V8 reports them"
                + " (UTF-16 code units); resolve them against the same build (tests.<id>.scripts.<url>.bodies = sha1 of the body in the trace's resources/) and its source map");
        m.put("scripts", "a test lists a script when the script was loaded during the test; ranges = code that ran (unioned),"
                + " functions = the distinct functions that ran (top-level excluded, never merged into each other)");
        m.put("coverage", "a test whose coverage is not \"ok\" (empty, capped, old, malformed, missing) has an incomplete script list:"
                + " it is always selected, whatever changed; so is a test that is not in the index at all (e.g. its trace was over a cap)");
        m.put("bodies", "a script URL with more than one body (>1 body) in the run (scripts.<url>.mixedBodies) is unmapped: run every test"
                + " and say why; its ranges are never unioned across bodies");
        m.put("css", "CSS is unattributed (CDP rule usage carries no stylesheet URL): a changed .css/.scss/.sass/.less file is unmapped,"
                + " so a selector must run every test and say why");
        m.put("unmapped", "a changed file that no script URL or source map maps to is unmapped: run every test and say why");
        m.put("urls", "URLs keep scheme, host and path only: query, fragment, userinfo and ;jsessionid= are dropped (routes keep #/ and #!/ hash routes)");
        m.put("routes", "routes are keyed by com.gatf.coverage.CoverageUrls.route, which applies the crawler's SpaCrawler.norm");
        return m;
    }

    /** The index. {@code full=false} drops per-test ranges/functions (the report page's summary view). Deterministic. */
    public Map<String, Object> build(String run, boolean full) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("format", FORMAT);
        out.put("version", VERSION);
        out.put("detail", full ? "full" : "summary");
        out.put("run", run);

        // run-wide known bodies per URL: more than one => never union ranges across bodies
        SortedMap<String, SortedSet<String>> runBodies = new TreeMap<>();
        for (TestAcc ta : tests.values()) {
            for (Map.Entry<String, SortedMap<String, ScriptAcc>> se : ta.scripts.entrySet()) {
                for (String b : se.getValue().keySet()) {
                    SortedSet<String> s = runBodies.computeIfAbsent(se.getKey(), k -> new TreeSet<>());
                    if (!UNKNOWN_BODY.equals(b)) s.add(b);
                }
            }
        }

        SortedMap<String, SortedSet<String>> routes = new TreeMap<>();
        SortedMap<String, SortedMap<String, Agg>> scriptGroups = new TreeMap<>();   // url -> body group -> agg
        SortedMap<String, SortedSet<String>> scriptTests = new TreeMap<>();
        long cssRules = 0, cssTotal = 0, cssUsed = 0;
        int cssSheets = 0, cssTraces = 0, testsNotOk = 0;

        Map<String, Object> testsOut = new LinkedHashMap<>();
        for (Map.Entry<String, TestAcc> te : tests.entrySet()) {
            String id = te.getKey();
            TestAcc ta = te.getValue();
            for (String r : ta.routes) routes.computeIfAbsent(r, k -> new TreeSet<>()).add(id);
            Map<String, Object> scripts = new LinkedHashMap<>();
            for (Map.Entry<String, SortedMap<String, ScriptAcc>> se : ta.scripts.entrySet()) {
                String u = se.getKey();
                boolean mixed = runBodies.get(u).size() > 1;
                scriptTests.computeIfAbsent(u, k -> new TreeSet<>()).add(id);
                SortedMap<String, Agg> groups = scriptGroups.computeIfAbsent(u, k -> new TreeMap<>());
                Agg testAgg = new Agg();
                List<String> testBodies = new ArrayList<>();
                for (Map.Entry<String, ScriptAcc> be : se.getValue().entrySet()) {
                    String b = be.getKey();
                    if (!UNKNOWN_BODY.equals(b)) testBodies.add(b);
                    testAgg.add(be.getValue());
                    // one known body (or none) in the run: everything is one group; else each body is its own group
                    groups.computeIfAbsent(mixed ? b : "*", k -> new Agg()).add(be.getValue());
                }
                boolean testMixed = mixed && se.getValue().size() > 1;
                Map<String, Object> so = new LinkedHashMap<>();
                so.put("len", testAgg.len);
                so.put("bodies", testBodies);
                if (testMixed) {
                    // this test saw several bodies of the URL: no union across them, so no ranges to offer
                    so.put("usedBytes", null);
                    so.put("mixedBodies", true);
                    if (full) {
                        so.put("ranges", new ArrayList<Object>());
                        so.put("functions", new ArrayList<Object>());
                    }
                } else {
                    so.put("usedBytes", testAgg.used());
                    if (full) {
                        so.put("ranges", json(testAgg.ranges));
                        so.put("functions", json(testAgg.functions));
                    }
                }
                if (testAgg.truncated) so.put("truncated", true);
                scripts.put(u, so);
            }
            Read cov = ta.coverage == null ? Read.MISSING : ta.coverage;
            if (cov != Read.OK) testsNotOk++;
            Map<String, Object> to = new LinkedHashMap<>();
            to.put("coverage", cov.label);
            to.put("traces", new ArrayList<>(ta.traces));
            to.put("routes", new ArrayList<>(ta.routes));
            to.put("scripts", scripts);
            Map<String, Object> css = new LinkedHashMap<>();
            css.put("rules", ta.cssRules);
            css.put("totalBytes", ta.cssTotal);
            css.put("usedBytes", ta.cssUsed);
            to.put("css", css);
            testsOut.put(id, to);
            cssRules += ta.cssRules;
            cssTotal += ta.cssTotal;
            cssUsed += ta.cssUsed;
            cssSheets += ta.cssSheets.size();
            if (ta.cssRules > 0) cssTraces += ta.traces.size();
        }

        Map<String, Object> scriptsOut = new LinkedHashMap<>();
        List<String> untestedScripts = new ArrayList<>();
        for (Map.Entry<String, SortedMap<String, Agg>> ge : scriptGroups.entrySet()) {
            String u = ge.getKey();
            boolean mixed = runBodies.get(u).size() > 1;
            long len = 0, used = 0;
            int fnHit = 0, fnTot = 0;
            for (Agg g : ge.getValue().values()) {       // mixed: the best single body, never a union
                len = Math.max(len, g.len);
                used = Math.max(used, g.used());
                fnHit = Math.max(fnHit, g.functions.size());
                fnTot = Math.max(fnTot, g.functionsTotal);
            }
            Map<String, Object> so = new LinkedHashMap<>();
            so.put("len", len);
            so.put("usedBytes", used);
            so.put("functionsHit", fnHit);
            so.put("functionsTotal", fnTot);
            so.put("tests", new ArrayList<>(scriptTests.get(u)));
            so.put("bodies", new ArrayList<>(runBodies.get(u)));
            if (mixed) so.put("mixedBodies", true);
            scriptsOut.put(u, so);
            // loaded, has functions, and none of them ever ran in any test (under any body)
            if (fnTot > 0 && fnHit == 0) untestedScripts.add(u);
        }

        Map<String, List<String>> routesOut = new LinkedHashMap<>();
        for (Map.Entry<String, SortedSet<String>> e : routes.entrySet()) routesOut.put(e.getKey(), new ArrayList<>(e.getValue()));

        List<Map<String, Object>> untestedRoutes = null;
        if (crawl != null) {
            untestedRoutes = new ArrayList<>();
            for (Map.Entry<String, String> e : crawl.entrySet()) {
                if (routes.containsKey(e.getKey())) continue;
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("route", e.getKey());
                r.put("priority", e.getValue().isEmpty() ? null : e.getValue());
                untestedRoutes.add(r);
            }
        }

        boolean anyData = tracesCoverageRead > 0 || !routes.isEmpty();
        String status = !anyData ? "no-data" : (!skipped.isEmpty() || tracesCoverageRead < traces) ? "partial" : "ok";
        SortedSet<String> outNotes = new TreeSet<>(notes);
        if (traces == 0) outNotes.add("no trace bundles in this run folder");
        else if (tracesWithCoverage == 0) outNotes.add("no trace in this run has coverage.json: turn on trace coverage (traceCoverage) for a Chromium run");
        else if (tracesWithCoverage < traces) outNotes.add((traces - tracesWithCoverage) + " of " + traces + " trace(s) have no coverage.json");
        if (tracesCoverageRead < tracesWithCoverage) outNotes.add((tracesWithCoverage - tracesCoverageRead) + " coverage.json file(s) could not be used (see skipped)");
        if (testsNotOk > 0) outNotes.add(testsNotOk + " test(s) have incomplete coverage (tests.<id>.coverage is not ok)");
        if (crawl == null) outNotes.add("no crawl map: untested routes need a saved crawl (<name>-coverage.json)");
        if (anonymousScripts > 0) outNotes.add(anonymousScripts + " inline/eval/data: script(s) without a stable URL were not indexed");
        if (cssRules > 0) outNotes.add("CSS is one unattributed bucket: rule usage carries no stylesheet URL in this capture");

        out.put("status", status);
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("traces", traces);
        counts.put("tracesWithCoverage", tracesWithCoverage);
        counts.put("tracesCoverageRead", tracesCoverageRead);
        counts.put("tests", tests.size());
        counts.put("testsCoverageNotOk", testsNotOk);
        counts.put("scripts", scriptsOut.size());
        counts.put("routesVisited", routesOut.size());
        counts.put("crawlRoutes", crawl == null ? null : crawl.size());
        counts.put("untestedRoutes", untestedRoutes == null ? null : untestedRoutes.size());
        counts.put("untestedScripts", untestedScripts.size());
        counts.put("skipped", skipped.size());
        out.put("counts", counts);
        out.put("tests", testsOut);
        out.put("routes", routesOut);
        out.put("scripts", scriptsOut);
        Map<String, Object> css = new LinkedHashMap<>();
        css.put("unattributed", true);
        css.put("traces", cssTraces);
        css.put("sheets", cssSheets);
        css.put("rules", cssRules);
        css.put("totalBytes", cssTotal);
        css.put("usedBytes", cssUsed);
        out.put("css", css);
        if (crawl != null) {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("source", crawlSource);
            c.put("routes", crawl.size());
            out.put("crawl", c);
        } else {
            out.put("crawl", null);
        }
        Map<String, Object> untested = new LinkedHashMap<>();
        untested.put("routes", anyData ? untestedRoutes : null);
        untested.put("scripts", untestedScripts);
        out.put("untested", untested);
        out.put("skipped", new ArrayList<>(skipped));
        out.put("notes", new ArrayList<>(outNotes));
        out.put("rules", rules());
        return out;
    }

    /** Drops per-test ranges/functions from a full index (for the report page). */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> summary(Map<String, Object> full) {
        Map<String, Object> out = new LinkedHashMap<>(full);
        out.put("detail", "summary");
        Object t = full.get("tests");
        if (t instanceof Map) {
            Map<String, Object> tests = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : ((Map<String, Object>) t).entrySet()) {
                if (!(e.getValue() instanceof Map)) continue;
                Map<String, Object> to = new LinkedHashMap<>((Map<String, Object>) e.getValue());
                Object s = to.get("scripts");
                if (s instanceof Map) {
                    Map<String, Object> scripts = new LinkedHashMap<>();
                    for (Map.Entry<String, Object> se : ((Map<String, Object>) s).entrySet()) {
                        if (!(se.getValue() instanceof Map)) continue;
                        Map<String, Object> so = new LinkedHashMap<>((Map<String, Object>) se.getValue());
                        so.remove("ranges");
                        so.remove("functions");
                        scripts.put(se.getKey(), so);
                    }
                    to.put("scripts", scripts);
                }
                tests.put(e.getKey(), to);
            }
            out.put("tests", tests);
        }
        return out;
    }
}
