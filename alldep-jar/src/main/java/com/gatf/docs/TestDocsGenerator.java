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

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatf.executor.core.GatfExecutorConfig;
import com.gatf.executor.core.TestCase;
import com.gatf.executor.finder.CSVFamilyTestCaseFinder.CSVTestCaseFinder;
import com.gatf.executor.finder.CSVFamilyTestCaseFinder.XLSTestCaseFinder;
import com.gatf.executor.finder.CSVFamilyTestCaseFinder.XLSXTestCaseFinder;
import com.gatf.executor.finder.JSONTestCaseFinder;
import com.gatf.executor.finder.TestCaseFinder;
import com.gatf.executor.finder.XMLTestCaseFinder;

/**
 * Q11 — test scripts → test-case documents, traceability matrix and coverage (xlsx and/or csv).
 *
 * <p>API configs: every test case the executor would find (same finders, same {@code ignoreFiles}). UI configs:
 * every configured seleasy script ({@link SelDocs}). Requirements: @tags matching the requirement pattern, optionally
 * joined with a requirements file. Status: the last run's {@code gatf-results.json}, when given.
 */
public final class TestDocsGenerator {

    public static final class Options {
        public Path outDir;
        public String format = "both";              // xlsx | csv | both
        public Path requirementsFile;
        public Path resultsFile;
        public Pattern reqPattern = TestDoc.DEFAULT_REQ;
    }

    public static final class Outcome {
        public final List<String> written = new ArrayList<String>();
        public int testCases, requirements, covered;
        public boolean statusApplied;
    }

    private TestDocsGenerator() {}

    /** Test-case docs for one executor config; {@code baseDir} is used when the config has no testCasesBasePath. */
    public static List<TestDoc> collect(GatfExecutorConfig cfg, File baseDir, Pattern reqPattern, Path skipUnder, int[] skipped) {
        File base = cfg.getTestCasesBasePath() != null ? new File(cfg.getTestCasesBasePath()) : baseDir;
        File testDir = cfg.getTestCaseDir() != null ? new File(base, cfg.getTestCaseDir()) : base;
        List<TestDoc> docs = new ArrayList<TestDoc>();
        if (cfg.isSeleniumExecutor()) {
            if (cfg.getSeleniumScripts() == null) return docs;
            for (String s : cfg.getSeleniumScripts()) {
                File f = firstFile(new File(s), new File(testDir, s), new File(base, s));
                if (f == null) {
                    skipped[0]++;
                    continue;
                }
                try {
                    docs.addAll(SelDocs.fromScript(f.toPath(), s, module(s), reqPattern));
                } catch (Exception e) {
                    skipped[0]++;
                }
            }
            return docs;
        }
        TestCaseFinder[] finders = { new XMLTestCaseFinder(), new JSONTestCaseFinder(), new CSVTestCaseFinder(), new XLSTestCaseFinder(),
                new XLSXTestCaseFinder() };
        String[] exts = { ".xml", ".json", ".csv", ".xls", ".xlsx" };
        for (int i = 0; i < finders.length; i++) {
            final String ext = exts[i];
            List<File> files = new ArrayList<File>();
            TestCaseFinder.getFiles(testDir, (dir, name) -> !new File(dir, name).isDirectory() && name.toLowerCase(Locale.ROOT).endsWith(ext), files);
            files = TestCaseFinder.filterFiles(cfg.getIgnoreFiles(), files, testDir);
            files = TestCaseFinder.orderFiles(cfg.getOrderedFiles(), true, files, testDir);   // stable, human order
            for (File file : files) {
                if (skipUnder != null && file.toPath().toAbsolutePath().normalize().startsWith(skipUnder.toAbsolutePath().normalize())) continue;
                String rel = TestCaseFinder.getRelativePath(file, testDir);
                List<TestCase> tcs;
                try {
                    tcs = finders[i].resolveTestCases(file);
                } catch (Exception e) {
                    skipped[0]++;       // not a test-case file (e.g. a config)
                    continue;
                }
                if (tcs == null) continue;
                for (TestCase tc : tcs) {
                    tc.setSourcefileName(rel);
                    docs.add(ApiDocs.from(tc, module(rel), reqPattern));
                }
            }
        }
        return docs;
    }

    private static File firstFile(File... candidates) {
        for (File f : candidates) if (f.isFile()) return f;
        return null;
    }

    static String module(String relPath) {
        String p = relPath == null ? "" : relPath.replace('\\', '/');
        int i = p.lastIndexOf('/');
        return i < 0 ? "" : p.substring(0, i);
    }

    /** Result records from gatf-results.json. */
    public static List<Traceability.Result> readResults(Path json) throws Exception {
        List<Traceability.Result> out = new ArrayList<Traceability.Result>();
        JsonNode root = new ObjectMapper().readTree(json.toFile());
        for (JsonNode t : root.path("tests")) {
            out.add(new Traceability.Result(t.path("suite").asText(""), t.path("name").asText(""), t.path("status").asText(""),
                    t.path("error").isNull() ? "" : t.path("error").asText("")));
        }
        return out;
    }

    /** Numbers the docs, joins status and requirements, writes the files. */
    public static Outcome write(List<TestDoc> docs, Options o) throws Exception {
        Outcome out = new Outcome();
        Traceability.number(docs);
        if (o.resultsFile != null && Files.isRegularFile(o.resultsFile)) {
            Traceability.applyResults(docs, readResults(o.resultsFile));
            out.statusApplied = true;
        }
        List<Traceability.Req> fromFile = o.requirementsFile != null ? Sheets.readRequirements(o.requirementsFile) : new ArrayList<Traceability.Req>();
        List<Traceability.Req> reqs = Traceability.requirements(fromFile, docs);
        List<String[]> matrix = Traceability.matrix(reqs, docs);
        List<String[]> coverage = Traceability.coverage(reqs, docs, matrix, out.statusApplied);

        List<String[]> tcRows = new ArrayList<String[]>();
        for (TestDoc d : docs) tcRows.add(d.row());
        String[] covHeader = coverage.get(0);
        List<String[]> covRows = coverage.subList(1, coverage.size());

        Files.createDirectories(o.outDir);
        String fmt = o.format == null ? "both" : o.format.toLowerCase(Locale.ROOT);
        if (fmt.equals("xlsx") || fmt.equals("both")) {
            List<Sheets.Sheet> sheets = new ArrayList<Sheets.Sheet>();
            sheets.add(new Sheets.Sheet("Test Cases", TestDoc.COLUMNS, tcRows, new int[] { 12, 16, 26, 36, 7, 9, 18, 18, 36, 34, 60, 50, 12, 40 }));
            sheets.add(new Sheets.Sheet("Traceability", Traceability.MATRIX_COLUMNS, matrix, new int[] { 16, 40, 16, 30, 7, 8, 8, 9, 16, 24 }));
            sheets.add(new Sheets.Sheet("Coverage", covHeader, covRows, new int[] { 36, 18, 10, 10, 10, 14 }));
            Path x = o.outDir.resolve("gatf-testcases.xlsx");
            Sheets.writeXlsx(x, sheets);
            out.written.add(x.toString());
        }
        if (fmt.equals("csv") || fmt.equals("both")) {
            Path a = o.outDir.resolve("gatf-testcases.csv"), b = o.outDir.resolve("gatf-traceability.csv"), c = o.outDir.resolve("gatf-coverage.csv");
            Sheets.writeCsv(a, TestDoc.COLUMNS, tcRows);
            Sheets.writeCsv(b, Traceability.MATRIX_COLUMNS, matrix);
            Sheets.writeCsv(c, covHeader, covRows);
            out.written.add(a.toString());
            out.written.add(b.toString());
            out.written.add(c.toString());
        }
        out.testCases = docs.size();
        out.requirements = reqs.size();
        for (String[] m : matrix) if (!m[8].equals("Not covered")) out.covered++;
        return out;
    }
}
