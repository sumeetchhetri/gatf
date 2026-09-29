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
package com.gatf.generator.core;

import java.io.File;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import com.gatf.docs.TestDoc;
import com.gatf.docs.TestDocsGenerator;
import com.gatf.executor.core.GatfExecutorConfig;
import com.gatf.executor.core.WorkflowContextHandler;

/**
 * Q11 — {@code gatf docs [config…] [-o dir] [--format xlsx|csv|both] [--requirements file] [--results file]
 * [--req-pattern regex]}: test-case documents, traceability matrix and coverage from the configured tests.
 */
final class DocsCli {

    static final String USAGE = "gatf docs [config...] [-o <dir>] [--format xlsx|csv|both] [--requirements <file.csv|xlsx>] "
            + "[--results <gatf-results.json>] [--req-pattern <regex>]";

    private DocsCli() {}

    static int run(String[] args, File cwd, PrintStream out, PrintStream err) {
        List<String> configs = new ArrayList<String>();
        TestDocsGenerator.Options o = new TestDocsGenerator.Options();
        String outDir = null;
        try {
            for (int i = 0; i < args.length; i++) {
                String a = args[i];
                switch (a) {
                    case "-o": case "--out": outDir = value(args, ++i, a); break;
                    case "--format":
                        o.format = value(args, ++i, a).toLowerCase();
                        if (!o.format.matches("xlsx|csv|both")) throw new IllegalArgumentException("--format must be xlsx, csv or both");
                        break;
                    case "--requirements": o.requirementsFile = resolve(cwd, value(args, ++i, a)).toPath(); break;
                    case "--results": o.resultsFile = resolve(cwd, value(args, ++i, a)).toPath(); break;
                    case "--req-pattern":
                        try {
                            o.reqPattern = Pattern.compile(value(args, ++i, a));
                        } catch (java.util.regex.PatternSyntaxException e) {
                            throw new IllegalArgumentException("invalid --req-pattern: " + e.getDescription());
                        }
                        break;
                    default:
                        if (a.startsWith("-")) throw new IllegalArgumentException("unknown option " + a);
                        configs.add(a);
                }
            }
            if (configs.isEmpty()) {
                for (String d : new String[] { "gatf-config.json", "gatf-config.xml" }) {
                    if (new File(cwd, d).isFile()) { configs.add(d); break; }
                }
                if (configs.isEmpty()) throw new IllegalArgumentException("no config given and no gatf-config.json / gatf-config.xml here");
            }
            if (o.requirementsFile != null && !o.requirementsFile.toFile().isFile()) throw new IllegalArgumentException("requirements file not found: " + o.requirementsFile);
            if (o.resultsFile != null && !o.resultsFile.toFile().isFile()) throw new IllegalArgumentException("results file not found: " + o.resultsFile);
            o.outDir = resolve(cwd, outDir != null ? outDir : "gatf-docs").toPath();
            return generate(configs, cwd, o, out);
        } catch (IllegalArgumentException e) {
            err.println("gatf docs: " + e.getMessage());
            err.println("usage: " + USAGE);
            return 1;
        } catch (Exception e) {
            err.println("gatf docs: " + (e.getMessage() == null ? e.toString() : e.getMessage()));
            return 1;
        }
    }

    /** Also used by {@code gatf test --reporter …,docs} after the run. */
    static int generate(List<String> configs, File cwd, TestDocsGenerator.Options o, PrintStream out) throws Exception {
        List<TestDoc> docs = new ArrayList<TestDoc>();
        int[] skipped = { 0 };
        for (String c : configs) {
            File f = resolve(cwd, c);
            if (!f.isFile()) throw new IllegalArgumentException("config not found: " + c);
            GatfExecutorConfig cfg = load(f);
            if (o.resultsFile == null) {
                Path r = defaultResults(cfg, f.getAbsoluteFile().getParentFile());
                if (r != null) o.resultsFile = r;
            }
            docs.addAll(TestDocsGenerator.collect(cfg, f.getAbsoluteFile().getParentFile(), o.reqPattern, o.outDir, skipped));
        }
        if (docs.isEmpty()) throw new IllegalArgumentException("no test cases or scripts found for " + String.join(", ", configs));
        TestDocsGenerator.Outcome r = TestDocsGenerator.write(docs, o);
        out.println("Documented " + r.testCases + " test case(s); " + r.requirements + " requirement(s), " + r.covered + " covered"
                + (r.statusApplied ? "; status from " + o.resultsFile : "; no run results (status 'not run')")
                + (skipped[0] > 0 ? "; " + skipped[0] + " file(s) skipped (missing, unreadable or not test cases)" : ""));
        for (String w : r.written) out.println("  " + w);
        return 0;
    }

    static GatfExecutorConfig load(File f) throws Exception {
        String n = f.getName().toLowerCase();
        if (n.endsWith(".xml")) return WorkflowContextHandler.XOM.readValue(f, GatfExecutorConfig.class);
        if (n.endsWith(".json")) return WorkflowContextHandler.OM.readValue(f, GatfExecutorConfig.class);
        throw new IllegalArgumentException("config must be .xml or .json: " + f);
    }

    /** The run's {@code gatf-results.json}: {@code <outFilesBasePath | config dir | cwd>/<outFilesDir | out>/}, when it exists. */
    static Path defaultResults(GatfExecutorConfig cfg, File cfgDir) {
        String sub = cfg.getOutFilesDir() != null ? cfg.getOutFilesDir() : "out";
        List<File> bases = new ArrayList<File>();
        if (cfg.getOutFilesBasePath() != null) bases.add(new File(cfg.getOutFilesBasePath()));
        bases.add(cfgDir);
        bases.add(new File("."));
        for (File b : bases) {
            File r = new File(new File(b, sub), "gatf-results.json");
            if (r.isFile()) return r.toPath();
        }
        return null;
    }

    private static String value(String[] args, int i, String opt) {
        if (i >= args.length) throw new IllegalArgumentException(opt + " needs a value");
        return args[i];
    }

    private static File resolve(File cwd, String p) {
        File f = new File(p);
        return f.isAbsolute() ? f : new File(cwd, p);
    }
}
