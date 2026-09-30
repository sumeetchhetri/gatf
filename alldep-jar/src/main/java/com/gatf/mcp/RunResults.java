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
package com.gatf.mcp;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * M14 {@code get_report}: finds the newest {@code gatf-results.json} under a project and condenses it — the summary,
 * then the tests (failed/flaky by default) with their error, tags and attachments (screenshots, trace zips that
 * {@code summarise_trace} can open). Pure: Jackson + NIO.
 */
public final class RunResults {

    public static final String FILE = "gatf-results.json";
    static final Set<String> SKIP_DIRS = new HashSet<String>(Arrays.asList(
            ".git", "node_modules", "target", "history", "dist", ".mvn-node", "__screenshots__"));
    private static final int MAX_ERROR = 2000;

    private RunResults() {}

    /** The newest results file under {@code root} (depth ≤ 6) modified at or after {@code notBeforeMs}; null if none. */
    public static Path latest(Path root, long notBeforeMs) throws IOException {
        if (root == null || !Files.isDirectory(root)) return null;
        final Path[] best = { null };
        final long[] bestT = { Long.MIN_VALUE };
        Files.walkFileTree(root, java.util.EnumSet.noneOf(java.nio.file.FileVisitOption.class), 6, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes a) {
                Path fn = d.getFileName();
                return fn != null && !d.equals(root) && SKIP_DIRS.contains(fn.toString()) ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult visitFile(Path f, BasicFileAttributes a) {
                if (FILE.equals(String.valueOf(f.getFileName()))) {
                    long t = a.lastModifiedTime().toMillis();
                    if (t >= notBeforeMs && t > bestT[0]) { bestT[0] = t; best[0] = f; }
                }
                return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult visitFileFailed(Path f, IOException e) { return FileVisitResult.CONTINUE; }
        });
        return best[0];
    }

    /** {@link #latest} with no age limit, null on an I/O error. */
    public static Path latestQuietly(Path root) {
        try { return latest(root, Long.MIN_VALUE); } catch (IOException e) { return null; }
    }

    /**
     * @param status  "failed" (failed + flaky + tests with soft failures; the default), "all", or one status
     * @param grep    case-insensitive substring of the test's name/title/suite; null = every test
     */
    public static Map<String, Object> summarise(JsonNode results, Path source, String status, String grep, int limit, ObjectMapper om) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("results", source == null ? null : source.toString());
        if (results.hasNonNull("dir")) out.put("dir", results.get("dir").asText());
        out.put("summary", om.convertValue(results.path("summary"), Object.class));
        String st = status == null ? "failed" : status.toLowerCase(Locale.ROOT);
        String g = grep == null ? null : grep.toLowerCase(Locale.ROOT);
        List<Map<String, Object>> tests = new ArrayList<Map<String, Object>>();
        int matched = 0;
        for (JsonNode t : results.path("tests")) {
            String ts = t.path("status").asText("");
            JsonNode sf = t.path("softFailures");
            boolean soft = sf.isArray() ? sf.size() > 0 : sf.asInt(0) > 0;
            boolean keep = st.equals("all") ? true
                    : st.equals("failed") ? (ts.equals("failed") || ts.equals("flaky") || ts.equals("timedOut") || soft)
                    : ts.equalsIgnoreCase(st);
            if (keep && g != null) {
                String hay = (t.path("suite").asText("") + " " + t.path("name").asText("") + " " + t.path("title").asText("")).toLowerCase(Locale.ROOT);
                keep = hay.contains(g);
            }
            if (!keep) continue;
            matched++;
            if (tests.size() >= limit) continue;
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            for (String k : new String[] { "suite", "name", "title", "status" }) if (t.hasNonNull(k)) m.put(k, t.get(k).asText());
            for (String k : new String[] { "attempts", "durationMs" }) if (t.hasNonNull(k)) m.put(k, t.get(k).asLong());
            if (soft) m.put("softFailures", sf.isArray() ? om.convertValue(sf, List.class) : (Object) sf.asInt());
            if (t.hasNonNull("error")) {
                String e = t.get("error").asText();
                m.put("error", e.length() > MAX_ERROR ? e.substring(0, MAX_ERROR) + "…" : e);
            }
            for (String k : new String[] { "tags", "attachments" }) {
                if (t.path(k).isArray() && t.path(k).size() > 0) m.put(k, om.convertValue(t.get(k), List.class));
            }
            List<String> traces = new ArrayList<String>();
            for (JsonNode a : t.path("attachments")) {
                String p = a.isTextual() ? a.asText() : a.path("path").asText("");
                if (p.endsWith("-trace.zip")) traces.add(p);
            }
            if (!traces.isEmpty()) m.put("traces", traces);
            tests.add(m);
        }
        out.put("matched", matched);
        out.put("tests", tests);
        if (matched > tests.size()) out.put("truncated", matched - tests.size());
        return out;
    }
}
