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
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.gatf.coverage.CoverageIndex;
import com.gatf.coverage.DiffParser;
import com.gatf.coverage.GitRepo;
import com.gatf.coverage.ImpactSelector;
import com.gatf.coverage.TraceStore;
import com.gatf.executor.core.GatfExecutorConfig;
import com.gatf.executor.core.SetupScripts;

/**
 * H6 (Δ156): {@code gatf test --changed <git-range> [--explain] [--coverage-run dir] [--source-maps dir]}: which UI
 * scripts the change can reach, from the coverage index of an earlier run ({@link CoverageIndex}) and
 * {@link ImpactSelector}. Prints the selection with a reason per script; the caller excludes the rest from the run.
 * Anything it can't decide safely selects (or runs everything) and says why.
 *
 * @author Sumeet Chhetri
 */
final class ChangedCli {

    private ChangedCli() {}

    static final class Plan {
        /** Configured script paths to exclude from the run. */
        final List<String> exclude = new ArrayList<String>();
        String note;
        /** True: don't run (explain mode, or no script is affected); exit with {@link #exitCode}. */
        boolean done;
        int exitCode;
    }

    /** @throws IllegalArgumentException for a bad range, no git work tree, or an unreadable config */
    static Plan plan(TestCommandArgs t, File cwd, PrintStream out) {
        File cfgFile = resolve(cwd, t.config);
        GatfExecutorConfig cfg;
        try {
            cfg = DocsCli.load(cfgFile);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("--changed: config " + t.config + " unreadable (" + e.getMessage() + ")");
        }
        File cfgDir = cfgFile.getAbsoluteFile().getParentFile();
        GitRepo repo = GitRepo.open(cwd, t.changed);

        List<ImpactSelector.Script> scripts = cfg.isSeleniumExecutor() ? scripts(cfg, cfgDir, cwd, repo.root) : new ArrayList<ImpactSelector.Script>();
        Path run = t.coverageRun != null ? resolve(cwd, t.coverageRun).toPath() : latestRun(outDirs(cfg, cfgDir, cwd));
        ImpactSelector.Result r = new ImpactSelector.Result();
        if (!cfg.isSeleniumExecutor()) {
            r.runAll.add("an API suite: the coverage index covers UI scripts only");
        } else if (scripts.isEmpty()) {
            r.runAll.add("no UI scripts found in the config");
        } else if (run == null || !Files.isDirectory(run)) {
            r.runAll.add(run == null ? "no coverage run found under " + outDirs(cfg, cfgDir, cwd) + " (run the suite with trace coverage on, or pass --coverage-run <dir>)"
                    : "--coverage-run " + run + " is not a folder");
        } else {
            List<DiffParser.FileChange> changes = null;
            try {
                changes = repo.changes();
            } catch (IOException e) {
                r.runAll.add("git diff " + t.changed + " failed: " + e.getMessage());
            }
            if (changes != null) {
                final Set<String> mine = new LinkedHashSet<String>();
                for (ImpactSelector.Script s : scripts) if (s.repoPath != null) mine.add(s.repoPath);
                ImpactSelector.Options o = new ImpactSelector.Options();
                o.sourceMapsDir = t.sourceMaps == null ? null : resolve(cwd, t.sourceMaps).toPath();
                o.isTestScript = mine::contains;
                Map<String, Object> index = CoverageIndex.build(run);
                r = ImpactSelector.select(index, changes, repo, new TraceStore(run), o);
            }
        }
        List<ImpactSelector.ScriptVerdict> vs = ImpactSelector.forScripts(r, scripts);

        out.println("Test impact of " + t.changed + " (base " + abbrev(repo.base()) + ")" + (run == null ? "" : ", coverage run " + run));
        if (r.runsAll()) {
            out.println("  running every test:");
            for (String w : r.runAll) out.println("    - " + w);
        } else if (r.nothingChanged) {
            out.println("  no file changed in the range");
        }
        int selected = 0;
        for (ImpactSelector.ScriptVerdict v : vs) {
            if (v.selected) selected++;
            if (!r.runsAll() || t.explain) {
                out.println("  " + (v.selected ? "run  " : "skip ") + v.script.configured + " - " + String.join("; ", v.reasons));
            }
        }
        if (t.explain && !r.notes.isEmpty()) {
            out.println("  how the change mapped:");
            for (String n : r.notes) out.println("    " + n);
        }
        out.println("  " + selected + " of " + vs.size() + " script(s) selected" + (t.explain ? " (--explain: nothing was run)" : ""));

        Plan p = new Plan();
        if (t.explain) {
            p.done = true;
            return p;
        }
        if (!vs.isEmpty() && selected == 0) {
            out.println("  No script is affected by " + t.changed + ": nothing to run.");
            p.done = true;
            return p;
        }
        for (ImpactSelector.ScriptVerdict v : vs) if (!v.selected) p.exclude.add(v.script.configured);
        if (!p.exclude.isEmpty()) p.note = "--changed " + t.changed + " (" + selected + "/" + vs.size() + " scripts)";
        return p;
    }

    /** The suite's UI scripts, as the executor will configure them (seleniumScripts / seleniumScript / module tests, minus setup scripts). */
    static List<ImpactSelector.Script> scripts(GatfExecutorConfig cfg, File cfgDir, File cwd, Path repoRoot) {
        File base = cfg.getTestCasesBasePath() != null ? new File(cfg.getTestCasesBasePath()) : cfgDir;
        File testDir = cfg.getTestCaseDir() != null ? new File(base, cfg.getTestCaseDir()) : base;
        String[] names = cfg.getSeleniumScripts();
        if (cfg.isSeleniumModuleTests()) {
            List<String> mods = new ArrayList<String>();
            try (java.util.stream.Stream<Path> w = Files.walk(testDir.toPath())) {
                w.filter(p -> p.getFileName().toString().equals("main.sel") && Files.isRegularFile(p)).sorted()
                        .forEach(p -> mods.add(testDir.toPath().relativize(p).toString()));
            } catch (IOException | RuntimeException e) {
                // as in the executor: no modules
            }
            names = mods.toArray(new String[0]);
        }
        if (cfg.getSeleniumScript() != null && !cfg.getSeleniumScript().trim().isEmpty()) {
            try {
                List<String> l = SetupScripts.expandSeleniumScript(cfg.getSeleniumScript(), testDir);
                names = l.toArray(new String[0]);
            } catch (IOException e) {
                // as in the executor: an unreadable seleniumScript leaves seleniumScripts
            }
        }
        List<ImpactSelector.Script> out = new ArrayList<ImpactSelector.Script>();
        if (names == null) return out;
        names = SetupScripts.excludeResolved(names, n -> {
            File f = firstFile(new File(testDir, n), new File(base, n), new File(n));
            return f != null ? f : new File(testDir, n);
        }, cfg.getLoginOnce(), cfg.getGlobalSetup(), cfg.getGlobalTeardown());
        Path root = real(repoRoot);
        Path td = real(testDir.toPath());
        for (String n : names) {
            // as the executor resolves it (testCaseDir, then the base path, then as given)
            File f = firstFile(new File(testDir, n), new File(base, n), new File(n), new File(cwd, n));
            Path abs = f == null ? null : real(f.toPath());
            String rel = abs != null && td != null && abs.startsWith(td) ? td.relativize(abs).toString() : n;
            String repoPath = abs != null && root != null && abs.startsWith(root) ? root.relativize(abs).toString() : null;
            out.add(new ImpactSelector.Script(n, abs == null ? null : abs.toString(), rel, repoPath));
        }
        return out;
    }

    /** Where runs are written: {@code <outFilesBasePath | config dir | cwd>/<outFilesDir | out>}. */
    static List<Path> outDirs(GatfExecutorConfig cfg, File cfgDir, File cwd) {
        String sub = cfg.getOutFilesDir() != null ? cfg.getOutFilesDir() : "out";
        Set<Path> out = new LinkedHashSet<Path>();
        if (cfg.getOutFilesBasePath() != null) out.add(new File(new File(cfg.getOutFilesBasePath()), sub).toPath().toAbsolutePath().normalize());
        out.add(new File(cfgDir, sub).toPath().toAbsolutePath().normalize());
        out.add(new File(cwd, sub).toPath().toAbsolutePath().normalize());
        return new ArrayList<Path>(out);
    }

    /**
     * The newest run folder (an out dir or a folder directly in it) holding *-trace.zip bundles; a first-round folder
     * wins over a retry round ("&lt;id&gt;-&lt;n&gt;", which only re-ran failures). Null when there is none.
     */
    static Path latestRun(Collection<Path> outDirs) {
        Path best = null;
        long bestTime = Long.MIN_VALUE;
        boolean bestRetry = true;
        for (Path o : outDirs) {
            List<Path> cands = new ArrayList<Path>();
            cands.add(o);
            if (Files.isDirectory(o)) {
                try (DirectoryStream<Path> ds = Files.newDirectoryStream(o)) {
                    for (Path p : ds) if (Files.isDirectory(p)) cands.add(p);
                } catch (IOException ignored) {
                    // unreadable: only the out dir itself
                }
            }
            for (Path c : cands) {
                long t = newestTrace(c);
                if (t == Long.MIN_VALUE) continue;
                boolean retry = c.getFileName() != null && c.getFileName().toString().matches(".*-\\d+");
                if (best == null || (bestRetry && !retry) || (retry == bestRetry && t > bestTime)) {
                    best = c;
                    bestTime = t;
                    bestRetry = retry;
                }
            }
        }
        return best;
    }

    private static long newestTrace(Path dir) {
        long t = Long.MIN_VALUE;
        if (!Files.isDirectory(dir)) return t;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*-trace.zip")) {
            for (Path p : ds) t = Math.max(t, Files.getLastModifiedTime(p).toMillis());
        } catch (IOException ignored) {
            // none readable
        }
        return t;
    }

    private static File firstFile(File... fs) {
        for (File f : fs) if (f != null && f.isFile()) return f;
        return null;
    }

    private static Path real(Path p) {
        if (p == null) return null;
        try {
            return p.toRealPath();
        } catch (IOException e) {
            return p.toAbsolutePath().normalize();
        }
    }

    private static File resolve(File cwd, String p) {
        File f = new File(p);
        return f.isAbsolute() ? f : new File(cwd, p);
    }

    private static String abbrev(String sha) { return sha == null ? "?" : sha.length() > 10 ? sha.substring(0, 10) : sha; }
}
