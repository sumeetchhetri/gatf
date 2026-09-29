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
package com.gatf.docs;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Q11 — requirement ↔ test-case traceability and coverage (pure).
 *
 * <p>Requirements come from a requirements file (id, title, module), and any requirement tagged on a test but
 * missing from the file is added (marked "not in requirements file"). With no file, the requirements are exactly
 * the tagged ones. Test status comes from the last run's {@code gatf-results.json}, when given.
 */
public final class Traceability {

    public static final class Req {
        public final String id, title, module;
        public boolean inFile = true;

        public Req(String id, String title, String module) {
            this.id = id;
            this.title = title == null ? "" : title;
            this.module = module == null ? "" : module;
        }
    }

    /** One result record from gatf-results.json. */
    public static final class Result {
        public final String suite, name, status, error;

        public Result(String suite, String name, String status, String error) {
            this.suite = suite == null ? "" : suite;
            this.name = name == null ? "" : name;
            this.status = status == null ? "" : status.toLowerCase(Locale.ROOT);
            this.error = error == null ? "" : error;
        }
    }

    private Traceability() {}

    /** Assigns TC-0001… in order. */
    public static void number(List<TestDoc> docs) {
        for (int i = 0; i < docs.size(); i++) docs.get(i).id = String.format(Locale.ROOT, "TC-%04d", i + 1);
    }

    private static String base(String p) {
        String s = p.replace('\\', '/');
        return s.substring(s.lastIndexOf('/') + 1);
    }

    private static boolean sameFile(String a, String b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return false;
        String x = a.replace('\\', '/'), y = b.replace('\\', '/');
        return x.equals(y) || x.endsWith("/" + y) || y.endsWith("/" + x) || (base(x).equals(base(y)) && !base(x).isEmpty());
    }

    private static int rank(String s) {
        switch (s) {
            case "failed": return 4;
            case "flaky": return 3;
            case "passed": return 2;
            case "skipped": return 1;
            default: return 0;
        }
    }

    /** Joins run results onto the docs: the worst status over matching records (e.g. several browsers) wins. */
    public static void applyResults(List<TestDoc> docs, List<Result> results) {
        for (TestDoc d : docs) {
            String best = null, err = "";
            for (Result r : results) {
                if (!sameFile(d.source, r.suite)) continue;
                boolean match;
                if ("API".equals(d.type)) {
                    match = r.name.equals(d.name);
                } else {
                    boolean sub = r.name.matches("\\[[^\\]]*\\] .+");
                    match = d.name == null ? !sub : sub && r.name.endsWith("] " + d.name);
                }
                if (!match) continue;
                if (best == null || rank(r.status) > rank(best)) {
                    best = r.status;
                    if (r.status.equals("failed")) err = r.error;
                }
            }
            if (best != null) {
                d.status = best;
                d.error = err;
            }
        }
    }

    /** Requirements from the file plus any tagged on tests but absent from it. */
    public static List<Req> requirements(List<Req> fromFile, List<TestDoc> docs) {
        Map<String, Req> m = new LinkedHashMap<String, Req>();
        for (Req r : fromFile) m.putIfAbsent(r.id, r);
        for (TestDoc d : docs) {
            for (String id : d.requirements) {
                if (!m.containsKey(id)) {
                    Req r = new Req(id, "", d.module);
                    r.inFile = fromFile.isEmpty();
                    m.put(id, r);
                }
            }
        }
        return new ArrayList<Req>(m.values());
    }

    public static final String[] MATRIX_COLUMNS = { "Requirement ID", "Requirement", "Module", "Test Cases", "Tests", "Passed",
            "Failed", "Not Run", "Coverage Status", "Note" };

    /** Traceability sheet rows. */
    public static List<String[]> matrix(List<Req> reqs, List<TestDoc> docs) {
        List<String[]> rows = new ArrayList<String[]>();
        for (Req r : reqs) {
            List<String> ids = new ArrayList<String>();
            int passed = 0, failed = 0, notRun = 0;
            for (TestDoc d : docs) {
                if (!d.requirements.contains(r.id)) continue;
                ids.add(d.id);
                if (d.status.equals("failed")) failed++;
                else if (d.status.equals("passed") || d.status.equals("flaky")) passed++;
                else notRun++;
            }
            rows.add(new String[] { r.id, r.title, r.module, String.join(", ", ids), String.valueOf(ids.size()), String.valueOf(passed),
                    String.valueOf(failed), String.valueOf(notRun), status(ids.size(), failed, notRun), r.inFile ? "" : "not in requirements file" });
        }
        return rows;
    }

    static String status(int tests, int failed, int notRun) {
        if (tests == 0) return "Not covered";
        if (failed > 0) return "Failing";
        if (notRun == tests) return "Not run";
        if (notRun > 0) return "Partially run";
        return "Passing";
    }

    /** Coverage sheet: summary metrics, then a per-module breakdown. */
    public static List<String[]> coverage(List<Req> reqs, List<TestDoc> docs, List<String[]> matrix, boolean haveResults) {
        int covered = 0, passing = 0, failing = 0;
        for (String[] m : matrix) {
            if (!m[8].equals("Not covered")) covered++;
            if (m[8].equals("Passing")) passing++;
            if (m[8].equals("Failing")) failing++;
        }
        int linked = 0, passed = 0, failed = 0, notRun = 0;
        for (TestDoc d : docs) {
            if (!d.requirements.isEmpty()) linked++;
            if (d.status.equals("failed")) failed++;
            else if (d.status.equals("passed") || d.status.equals("flaky")) passed++;
            else notRun++;
        }
        List<String[]> rows = new ArrayList<String[]>();
        rows.add(new String[] { "Metric", "Value", "", "", "", "" });
        rows.add(new String[] { "Requirements", String.valueOf(reqs.size()), "", "", "", "" });
        rows.add(new String[] { "Requirements covered by tests", covered + " (" + pct(covered, reqs.size()) + ")", "", "", "", "" });
        rows.add(new String[] { "Requirements not covered", String.valueOf(reqs.size() - covered), "", "", "", "" });
        if (haveResults) {
            rows.add(new String[] { "Requirements passing", passing + " (" + pct(passing, reqs.size()) + ")", "", "", "", "" });
            rows.add(new String[] { "Requirements failing", String.valueOf(failing), "", "", "", "" });
        }
        rows.add(new String[] { "Test cases", String.valueOf(docs.size()), "", "", "", "" });
        rows.add(new String[] { "Test cases linked to a requirement", linked + " (" + pct(linked, docs.size()) + ")", "", "", "", "" });
        if (haveResults) {
            rows.add(new String[] { "Test cases passed", String.valueOf(passed), "", "", "", "" });
            rows.add(new String[] { "Test cases failed", String.valueOf(failed), "", "", "", "" });
            rows.add(new String[] { "Test cases not run", String.valueOf(notRun), "", "", "", "" });
        }
        rows.add(new String[] { "", "", "", "", "", "" });
        rows.add(new String[] { "Module", "Test Cases", "Passed", "Failed", "Not Run", "Requirements" });
        Map<String, int[]> mods = new LinkedHashMap<String, int[]>();
        Map<String, java.util.Set<String>> modReqs = new LinkedHashMap<String, java.util.Set<String>>();
        for (TestDoc d : docs) {
            String mod = d.module == null || d.module.isEmpty() ? "(root)" : d.module;
            int[] c = mods.computeIfAbsent(mod, k -> new int[4]);
            c[0]++;
            if (d.status.equals("failed")) c[2]++;
            else if (d.status.equals("passed") || d.status.equals("flaky")) c[1]++;
            else c[3]++;
            modReqs.computeIfAbsent(mod, k -> new java.util.LinkedHashSet<String>()).addAll(d.requirements);
        }
        for (Map.Entry<String, int[]> e : mods.entrySet()) {
            int[] c = e.getValue();
            rows.add(new String[] { e.getKey(), String.valueOf(c[0]), String.valueOf(c[1]), String.valueOf(c[2]), String.valueOf(c[3]),
                    String.valueOf(modReqs.get(e.getKey()).size()) });
        }
        return rows;
    }

    static String pct(int n, int of) {
        return of == 0 ? "0%" : String.format(Locale.ROOT, "%.1f%%", 100.0 * n / of);
    }
}
