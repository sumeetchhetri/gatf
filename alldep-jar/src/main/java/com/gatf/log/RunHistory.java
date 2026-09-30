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
package com.gatf.log;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * M12: run history for trend charts. Every CLI run's {@code gatf-results.json} is also archived as
 * {@code <out>/history/gatf-results-<epoch-ms>.json}; the newest {@code -Dgatf.history.keep} (default 50) are kept.
 * {@link #list} serves {@code GET /api/history?limit=N}: newest first, each with its summary and a compact test list
 * (title, status, duration) so the console can chart trends and rank unstable tests. Pure file I/O + Jackson.
 */
public final class RunHistory {

    public static final String DIR = "history";
    private static final Pattern NAME = Pattern.compile("gatf-results-(\\d{1,18})(?:-(\\d{1,9}))?\\.json");
    private static final ObjectMapper OM = new ObjectMapper();

    private RunHistory() {}

    static int keep() {
        return Math.max(1, Integer.getInteger("gatf.history.keep", 50));
    }

    /** Archives one run's results JSON; prunes the oldest beyond {@code keep}. Returns the archived file. */
    public static File archive(File outDir, String json, long nowMs, int keep) throws IOException {
        File dir = new File(outDir, DIR);
        Files.createDirectories(dir.toPath());
        File f = null;
        for (int k = 1; f == null; k++) {
            // CREATE_NEW: two runs sharing an out dir and finishing in the same millisecond never overwrite each other
            File c = new File(dir, "gatf-results-" + nowMs + (k == 1 ? "" : "-" + k) + ".json");
            try {
                Files.write(c.toPath(), json.getBytes(StandardCharsets.UTF_8), java.nio.file.StandardOpenOption.CREATE_NEW,
                        java.nio.file.StandardOpenOption.WRITE);
                f = c;
            } catch (java.nio.file.FileAlreadyExistsException e) {
                if (k > 1000) throw e;
            }
        }
        List<File> all = files(dir);
        for (int i = keep; i < all.size(); i++) Files.deleteIfExists(all.get(i).toPath());
        return f;
    }

    /** History files, newest first (by the epoch in the name, then name). */
    static List<File> files(File dir) {
        File[] fs = dir.listFiles((d, n) -> NAME.matcher(n).matches());
        if (fs == null) return new ArrayList<File>();
        List<File> l = new ArrayList<File>(Arrays.asList(fs));
        l.sort(Comparator.comparingLong(RunHistory::epoch).thenComparingInt(RunHistory::seq).reversed());
        return l;
    }

    static long epoch(File f) {
        Matcher m = NAME.matcher(f.getName());
        return m.matches() ? Long.parseLong(m.group(1)) : f.lastModified();   // \d{1,18}: always fits a long
    }

    /** Same-millisecond order: no suffix = 1, then -2, -3, … */
    static int seq(File f) {
        Matcher m = NAME.matcher(f.getName());
        return m.matches() && m.group(2) != null ? Integer.parseInt(m.group(2)) : 1;
    }

    /** The newest {@code limit} runs (1..200) under {@code outDir}; unreadable files are skipped. */
    public static List<Map<String, Object>> list(File outDir, int limit) {
        int n = Math.max(1, Math.min(200, limit));
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        for (File f : files(new File(outDir, DIR))) {
            if (out.size() >= n) break;
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> r = OM.readValue(f, Map.class);
                Map<String, Object> run = new LinkedHashMap<String, Object>();
                run.put("id", f.getName());
                run.put("time", epoch(f));
                run.put("summary", r.get("summary"));
                List<Map<String, Object>> tests = new ArrayList<Map<String, Object>>();
                Object tl = r.get("tests");
                if (tl instanceof List) {
                    for (Object o : (List<?>) tl) {
                        if (!(o instanceof Map)) continue;
                        Map<?, ?> t = (Map<?, ?>) o;
                        Map<String, Object> c = new LinkedHashMap<String, Object>();
                        c.put("title", t.get("title"));
                        c.put("status", t.get("status"));
                        c.put("durationMs", t.get("durationMs"));
                        tests.add(c);
                    }
                }
                run.put("tests", tests);
                out.add(run);
            } catch (IOException | RuntimeException e) {
                // a half-written or foreign file: not part of the trend
            }
        }
        return out;
    }
}
