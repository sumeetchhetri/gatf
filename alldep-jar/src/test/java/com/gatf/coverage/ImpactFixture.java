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

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Δ156 test fixtures: a two-file app bundled into main.js with a v3 source map, two tests (a.sel runs a(), b.sel
 * runs b()) recorded as trace bundles with V8-shaped coverage, and an in-memory base commit.
 */
public final class ImpactFixture {

    static final String URL = "https://app.test/main.js";
    static final String MAP_URL = "https://app.test/main.js.map";

    public static final String[] A = {
        "function a(x) {",
        "  if (x) {",
        "    x = x + 1;",
        "    x = x * 3;",
        "    x = x - 2;",
        "  }",
        "  return x;",
        "}",
    };
    public static final String[] B = {
        "function b(y) {",
        "  let z = y + 1;",
        "  z = z * 2;",
        "  z = z + 7;",
        "  return z;",
        "}",
    };

    // ------------------------------------------------------------------ bundle

    /** main.js = "// bundle", a.js, b.js, "a(1); b(2);", then the sourceMappingURL comment. */
    static String bundle(String smu) {
        List<String> l = new ArrayList<>();
        l.add("// bundle");
        l.addAll(Arrays.asList(A));
        l.addAll(Arrays.asList(B));
        l.add("a(1); b(2);");
        if (smu != null) l.add("//# sourceMappingURL=" + smu);
        return String.join("\n", l) + "\n";
    }

    static int lineStart(String text, int genLine) {
        int pos = 0;
        for (int i = 0; i < genLine; i++) pos = text.indexOf('\n', pos) + 1;
        return pos;
    }

    static int lineEnd(String text, int genLine) {
        int s = lineStart(text, genLine);
        int e = text.indexOf('\n', s);
        return e < 0 ? text.length() : e;
    }

    // generated lines: 1..8 = a.js 1..8, 9..14 = b.js 1..6
    static int aGen(int line) { return line; }
    static int bGen(int line) { return A.length + line; }

    static String map(boolean withContent) {
        StringBuilder m = new StringBuilder();
        int pSrc = 0, pLine = 0, pCol = 0;
        int lines = 1 + A.length + B.length + 2;
        for (int g = 0; g < lines; g++) {
            if (g > 0) m.append(';');
            int src = -1, orig = -1;
            String text = null;
            if (g >= 1 && g <= A.length) { src = 0; orig = g - 1; text = A[orig]; }
            else if (g > A.length && g <= A.length + B.length) { src = 1; orig = g - 1 - A.length; text = B[orig]; }
            if (src < 0) continue;
            // a segment at column 0, and one at column 2 (the first token after the indent) when the line is that long
            int pGen = 0;
            int[] cols = text.length() > 2 ? new int[] {0, 2} : new int[] {0};
            for (int k = 0; k < cols.length; k++) {
                int col = cols[k];
                if (k > 0) m.append(',');
                m.append(vlq(col - pGen)).append(vlq(src - pSrc)).append(vlq(orig - pLine)).append(vlq(col - pCol));
                pGen = col;
                pSrc = src;
                pLine = orig;
                pCol = col;
            }
        }
        StringBuilder j = new StringBuilder("{\"version\":3,\"file\":\"main.js\",\"sources\":[\"webpack://app/./src/a.js\",\"webpack://app/./src/b.js\"],");
        if (withContent) {
            j.append("\"sourcesContent\":[").append(jsonStr(String.join("\n", A) + "\n")).append(',').append(jsonStr(String.join("\n", B) + "\n")).append("],");
        }
        j.append("\"names\":[],\"mappings\":\"").append(m).append("\"}");
        return j.toString();
    }

    static final String B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

    static String vlq(int v) {
        int x = v < 0 ? ((-v) << 1) | 1 : v << 1;
        StringBuilder b = new StringBuilder();
        do {
            int d = x & 31;
            x >>>= 5;
            if (x > 0) d |= 32;
            b.append(B64.charAt(d));
        } while (x > 0);
        return b.toString();
    }

    static String jsonStr(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    // ------------------------------------------------------------------ coverage

    static String r(long s, long e, long c) { return "{\"startOffset\":" + s + ",\"endOffset\":" + e + ",\"count\":" + c + "}"; }

    static String fn(String name, String... ranges) { return "{\"functionName\":\"" + name + "\",\"isBlockCoverage\":true,\"ranges\":[" + String.join(",", ranges) + "]}"; }

    /** V8-shaped coverage of main.js: which of a() / b() ran. */
    static String coverage(String body, boolean ranA, boolean ranB) {
        int aS = lineStart(body, aGen(1)), aE = lineEnd(body, aGen(8));
        int ifS = lineStart(body, aGen(2)) + 8, ifE = lineEnd(body, aGen(6));     // the `{ … }` of the if
        int bS = lineStart(body, bGen(1)), bE = lineEnd(body, bGen(6));
        String fa = ranA ? fn("a", r(aS, aE, 1), r(ifS, ifE, 1)) : fn("a", r(aS, aE, 0));
        String fb = ranB ? fn("b", r(bS, bE, 1)) : fn("b", r(bS, bE, 0));
        return "{\"js\":[{\"scriptId\":\"1\",\"url\":\"" + URL + "\",\"functions\":[" + fn("", r(0, body.length(), 1)) + "," + fa + "," + fb + "]}],\"css\":[]}";
    }

    static String har(Map<String, String> urlToSha) {
        StringBuilder b = new StringBuilder("{\"log\":{\"entries\":[");
        boolean first = true;
        for (Map.Entry<String, String> e : urlToSha.entrySet()) {
            if (!first) b.append(',');
            first = false;
            String mime = e.getKey().endsWith(".map") ? "application/json" : "application/javascript";
            b.append("{\"request\":{\"method\":\"GET\",\"url\":\"").append(e.getKey()).append("\"},\"response\":{\"status\":200,\"content\":{\"size\":1,\"mimeType\":\"")
                    .append(mime).append("\",\"_file\":\"resources/").append(e.getValue()).append("\"}}}");
        }
        return b.append("]}}").toString();
    }

    static String meta(String sel, long startTs) {
        return meta(sel, startTs, "passed");
    }

    static String meta(String sel, long startTs, String outcome) {
        return "{\"runId\":\"x\",\"selFile\":\"" + sel + "\",\"format\":2,\"startTs\":" + startTs
                + (outcome == null ? "" : ",\"outcome\":\"" + outcome + "\"") + "}";
    }

    static void zip(Path p, Map<String, byte[]> entries) throws Exception {
        try (OutputStream o = Files.newOutputStream(p); ZipOutputStream z = new ZipOutputStream(o)) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                z.putNextEntry(new ZipEntry(e.getKey()));
                z.write(e.getValue());
                z.closeEntry();
            }
        }
    }

    static byte[] b(String s) { return s.getBytes(StandardCharsets.UTF_8); }

    /**
     * A run folder with a.sel (ran a) and b.sel (ran b), both loading main.js. {@code mapMode}: "har" (map captured),
     * "inline" (data: URI), "none" (sourceMappingURL to an uncaptured map), "plain" (no sourceMappingURL).
     */
    public static Path run(Path dir, String mapMode, boolean withContent, long startTs) throws Exception {
        Path run = Files.createDirectories(dir.resolve("out/123"));
        String mapJson = map(withContent);
        String smu = mapMode.equals("plain") ? null
                : mapMode.equals("inline") ? "data:application/json;base64," + java.util.Base64.getEncoder().encodeToString(b(mapJson))
                : "main.js.map";
        String body = bundle(smu);
        String bodySha = ImpactSelector.sha1(b(body)), mapSha = ImpactSelector.sha1(b(mapJson));
        String[][] tests = {{"/ci/work/tests/a.sel", "true", "false"}, {"/ci/work/tests/b.sel", "false", "true"}};
        int n = 0;
        for (String[] t : tests) {
            Map<String, String> urls = new LinkedHashMap<>();
            urls.put(URL, bodySha);
            if (mapMode.equals("har")) urls.put(MAP_URL, mapSha);
            Map<String, byte[]> e = new LinkedHashMap<>();
            e.put("metadata.json", b(meta(t[0], startTs)));
            e.put("network.har", b(har(urls)));
            e.put("coverage.json", b(coverage(body, Boolean.parseBoolean(t[1]), Boolean.parseBoolean(t[2]))));
            e.put("resources/" + bodySha, b(body));
            if (mapMode.equals("har")) e.put("resources/" + mapSha, b(mapJson));
            zip(run.resolve("r-1-1-" + (++n) + "-chrome-trace.zip"), e);
        }
        return run;
    }

    // ------------------------------------------------------------------ repo

    static final class FakeRepo implements ImpactSelector.Repo {
        final Map<String, String> files = new LinkedHashMap<>();
        Long time;
        FakeRepo(Long time) {
            this.time = time;
            files.put("src/a.js", String.join("\n", A) + "\n");
            files.put("src/b.js", String.join("\n", B) + "\n");
            files.put("README.md", "# app\n");
            files.put("styles/app.css", "body{}\n");
            files.put("tests/a.sel", "open chrome\n");
            files.put("tests/b.sel", "open chrome\n");
        }
        @Override public String base() { return "0123456789abcdef0123456789abcdef01234567"; }
        @Override public Long baseTimeMs() { return time; }
        @Override public List<String> tracked() { return new ArrayList<>(files.keySet()); }
        @Override public byte[] baseContent(String path) { String s = files.get(path); return s == null ? null : b(s); }
    }

    /** A one-hunk modification of {@code path}: old lines [start, start+removed.length) replaced by {@code added}. */
    static DiffParser.FileChange change(String path, int start, String[] removed, String... added) {
        StringBuilder p = new StringBuilder("diff --git a/" + path + " b/" + path + "\nindex 1..2 100644\n--- a/" + path + "\n+++ b/" + path + "\n");
        int ns = removed.length == 0 ? start + 1 : start;
        p.append("@@ -").append(start).append(',').append(removed.length).append(" +").append(ns).append(',').append(added.length).append(" @@\n");
        for (String r : removed) p.append('-').append(r).append('\n');
        for (String a : added) p.append('+').append(a).append('\n');
        return DiffParser.parse(p.toString()).get(0);
    }

    static ImpactSelector.Result select(Path run, ImpactSelector.Repo repo, Collection<DiffParser.FileChange> changes, ImpactSelector.Options o) {
        return ImpactSelector.select(CoverageIndex.build(run), new ArrayList<>(changes), repo, new TraceStore(run), o);
    }

    private ImpactFixture() {}
}
