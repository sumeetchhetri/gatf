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
import java.util.ArrayList;
import java.util.List;

/**
 * Q7: arguments of {@code gatf test} (the one documented entry point for CLI runs):
 * <pre>
 *   gatf test [config] [--grep RE]... [--grep-invert RE]... [--tag T]... [--reporter junit,json,github,docs|none]
 *             [--trace off|on|retain-on-failure|on-first-retry] [--retries N] [--workers N|N%] [--shard i/n] [--forbid-only] [--update-snapshots]
 *             [--watch] [--changed git-range [--explain] [--coverage-run dir] [--source-maps dir]]
 * </pre>
 * The config defaults to gatf-config.json, else gatf-config.xml, in the working directory. Whether the suite is
 * API or UI comes from the config's isSeleniumExecutor, exactly as for -executor / -selenium. Pure parsing; no side
 * effects, so it is unit-testable.
 */
public final class TestCommandArgs {

    public String config;
    public final List<String> grep = new ArrayList<String>();
    public final List<String> grepInvert = new ArrayList<String>();
    public final List<String> tags = new ArrayList<String>();
    public String reporters;
    public String traceMode;
    public Integer retries;
    public Integer workers;
    /** E10: --forbid-only: a subtest marked {@code only} fails the run (null = the config's forbidOnly). */
    public Boolean forbidOnly;
    /** M7: --shard i/n (1-based); null = run every file. */
    public String shard;
    /** M9: --update-snapshots: rewrite `screenshot compare` baselines (null = the config's updateSnapshots). */
    public Boolean updateSnapshots;
    /** M15: --watch: re-run on save (changed scripts + last failures), Enter = everything with failures first. */
    public boolean watch;
    /** Δ156: --changed &lt;git-range&gt;: run only the UI scripts the change can reach (null = every script). */
    public String changed;
    /** Δ156: --explain (with --changed): print the selection with a reason per script and run nothing. */
    public boolean explain;
    /** Δ156: --coverage-run &lt;dir&gt;: the run folder whose traces form the coverage index (default: the newest one). */
    public String coverageRun;
    /** Δ156: --source-maps &lt;dir&gt;: the build output holding the scripts' external .map files. */
    public String sourceMaps;

    public static final String USAGE =
            "gatf test [config] [--grep RE]... [--grep-invert RE]... [--tag T]... [--reporter junit,json,github,docs|none]\n"
          + "          [--trace off|on|retain-on-failure|on-first-retry] [--retries N] [--workers N|N%] [--shard i/n] [--forbid-only]\n"
          + "          [--update-snapshots] [--watch] [--changed <git-range> [--explain] [--coverage-run dir] [--source-maps dir]]";

    /** Parses {@code args} (everything after "test"). Throws IllegalArgumentException with a usage message on error. */
    public static TestCommandArgs parse(String[] args, File cwd) {
        TestCommandArgs a = new TestCommandArgs();
        for (int i = 0; i < args.length; i++) {
            String s = args[i];
            String v = null;
            int eq = s.startsWith("--") ? s.indexOf('=') : -1;
            if (eq > 0) {                       // --grep=foo form
                v = s.substring(eq + 1);
                s = s.substring(0, eq);
            }
            switch (s) {
                case "--grep": case "-g":          a.grep.add(val(args, i, s, v)); break;
                case "--grep-invert":              a.grepInvert.add(val(args, i, s, v)); break;
                case "--tag": case "-t":           a.tags.add(val(args, i, s, v)); break;
                case "--reporter":                 a.reporters = val(args, i, s, v); break;
                case "--trace":                    a.traceMode = val(args, i, s, v); break;
                case "--retries":                  a.retries = num(val(args, i, s, v), s); break;
                case "--workers": case "-j":       a.workers = com.gatf.executor.core.ShardSplitter.resolveWorkers(val(args, i, s, v), Runtime.getRuntime().availableProcessors()); break;   // M7: N or N%
                case "--shard":                    a.shard = val(args, i, s, v); com.gatf.executor.core.ShardSplitter.parse(a.shard); break;   // M7: validated here
                case "--update-snapshots": case "-u": a.updateSnapshots = v == null || Boolean.parseBoolean(v.trim()); continue;   // M9: a flag
                case "--watch":                    a.watch = v == null || Boolean.parseBoolean(v.trim()); continue;   // M15: a flag
                case "--forbid-only":              a.forbidOnly = v == null || Boolean.parseBoolean(v.trim()); continue;   // a flag: no value argument
                case "--changed":                  a.changed = val(args, i, s, v); break;          // Δ156: test-impact selection
                case "--coverage-run":             a.coverageRun = val(args, i, s, v); break;
                case "--source-maps":              a.sourceMaps = val(args, i, s, v); break;
                case "--explain":                  a.explain = v == null || Boolean.parseBoolean(v.trim()); continue;   // a flag
                default:
                    if (s.startsWith("-")) throw new IllegalArgumentException("Unknown option " + s + "\n" + USAGE);
                    if (a.config != null) throw new IllegalArgumentException("Only one config file may be given (got " + a.config + " and " + s + ")\n" + USAGE);
                    a.config = s;
                    continue;
            }
            if (v == null) i++;                 // the value was the next argument
        }
        if (a.config == null) {
            for (String d : new String[] { "gatf-config.json", "gatf-config.xml" }) {
                if (new File(cwd, d).isFile()) { a.config = d; break; }
            }
            if (a.config == null) {
                throw new IllegalArgumentException("No config given and no gatf-config.json / gatf-config.xml in " + cwd.getAbsolutePath() + "\n" + USAGE);
            }
        }
        if (a.retries != null && a.retries < 0) throw new IllegalArgumentException("--retries must be >= 0");
        if (a.workers != null && a.workers < 1) throw new IllegalArgumentException("--workers must be >= 1");
        if (a.changed == null && (a.explain || a.coverageRun != null || a.sourceMaps != null)) {
            throw new IllegalArgumentException("--explain, --coverage-run and --source-maps only apply with --changed <git-range>\n" + USAGE);
        }
        if (a.changed != null && a.watch) throw new IllegalArgumentException("--changed and --watch can't be combined");
        return a;
    }

    private static String val(String[] args, int i, String opt, String inline) {
        if (inline != null) return inline;
        if (i + 1 >= args.length) throw new IllegalArgumentException(opt + " needs a value\n" + USAGE);
        return args[i + 1];
    }

    private static Integer num(String v, String opt) {
        try {
            return Integer.valueOf(v.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(opt + " expects a number, got '" + v + "'");
        }
    }
}
