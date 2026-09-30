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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatf.mcp.RunArgs;
import com.gatf.mcp.RunResults;

/**
 * M15: {@code gatf test --watch}. Runs the suite, then watches the project: a save re-runs the changed scripts plus the
 * last failures ({@link WatchPlanner}); Enter re-runs everything with the failures first, {@code f} only the failures,
 * {@code q} quits. Each run is a child JVM on the same classpath ({@code gatf test} ends in System.exit and leaves
 * static run state behind), with the child's console passed straight through.
 */
public final class WatchMode {

    static final long POLL_MS = 300, QUIET_MS = 400;

    private final Path root;
    private final List<String> baseArgs;
    /** The user gave --grep / --grep-invert / --tag: those OR with any extra --grep, so focused runs are off (full runs only). */
    private final boolean userFilter;
    private final PrintStream out;
    private final ObjectMapper om = new ObjectMapper();
    private List<String> failed = new ArrayList<String>();
    private Set<String> known = Collections.emptySet();

    WatchMode(Path root, List<String> baseArgs, PrintStream out) {
        this.root = root;
        this.baseArgs = baseArgs;
        this.out = out;
        this.userFilter = hasUserFilter(baseArgs);
    }

    /** {@code args} = everything after {@code test}, without {@code --watch}. Returns when the user quits. */
    public static int run(String[] args, File cwd, InputStream in, PrintStream out) {
        List<String> base = new ArrayList<String>();
        for (String a : args) if (!a.equals("--watch") && !a.startsWith("--watch=")) base.add(a);
        return new WatchMode(cwd.toPath().toAbsolutePath().normalize(), base, out).loop(in);
    }

    int loop(InputStream in) {
        final BlockingQueue<String> keys = new LinkedBlockingQueue<String>();
        Thread kt = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String l;
                while ((l = r.readLine()) != null) keys.add(l.trim().toLowerCase(java.util.Locale.ROOT));
            } catch (IOException ignore) { /* stdin closed */ }
            // no stdin (nohup, < /dev/null, IDE runners): keep watching; Ctrl-C ends it
        }, "gatf-watch-keys");
        kt.setDaemon(true);
        kt.start();

        cycle(WatchPlanner.full(failed));
        Map<String, Long> snap = scan();
        WatchPlanner.Debouncer deb = new WatchPlanner.Debouncer(QUIET_MS);
        banner();
        while (true) {
            String k;
            try { k = keys.poll(POLL_MS, TimeUnit.MILLISECONDS); } catch (InterruptedException e) { return 0; }
            if (k != null) {
                if (k.equals("q") || k.equals("quit")) return failed.isEmpty() ? 0 : 1;
                List<WatchPlanner.Run> plan = userFilter ? WatchPlanner.full(Collections.<String>emptyList())
                        : k.equals("f") ? WatchPlanner.failedOnly(failed) : WatchPlanner.full(failed);
                if (plan.isEmpty()) out.println("  no failed tests to re-run");
                else cycle(plan);
                snap = scan();
                banner();
                continue;
            }
            Map<String, Long> now = scan();
            deb.changed(WatchPlanner.diff(snap, now), System.currentTimeMillis());
            snap = now;
            Set<String> batch = deb.poll(System.currentTimeMillis());
            if (!batch.isEmpty()) {
                out.println();
                out.println("  changed: " + String.join(", ", batch));
                cycle(userFilter ? WatchPlanner.full(Collections.<String>emptyList()) : WatchPlanner.afterChange(batch, failed, known));
                snap = scan();   // a run's own writes are not changes
                banner();
            }
        }
    }

    private void banner() {
        out.println();
        out.println("  watching " + root + (failed.isEmpty() ? "" : " - " + failed.size() + " failing")
                + (userFilter ? " (your --grep/--tag filter: every re-run is the filtered suite)" : "")
                + "  |  Enter: run all (failed first)  |  f+Enter: failed only  |  q+Enter: quit");
    }

    /** Runs each step; a focused step that matched nothing (no results written) falls back to the full suite. */
    private void cycle(List<WatchPlanner.Run> plan) {
        for (WatchPlanner.Run r : plan) {
            JsonNode res = runOnce(r);
            if (res == null && !r.isFull() && !r.label.equals("rest")) {
                out.println("  (nothing matched " + r.label + " - running everything)");
                res = runOnce(WatchPlanner.full(Collections.<String>emptyList()).get(0));
            }
            if (res != null) {
                if (r.isFull()) {
                    known = WatchPlanner.knownSuites(res);
                    failed = WatchPlanner.failedGreps(res);
                } else if (r.label.equals("rest")) {
                    known = union(known, WatchPlanner.knownSuites(res));   // plus the suites the "failed" step ran
                    failed = WatchPlanner.failedGreps(res);                // the failed step passed, or rest was skipped
                } else {
                    // a focused run: merge — its tests replace their earlier outcome
                    known = union(known, WatchPlanner.knownSuites(res));
                    List<String> now = WatchPlanner.failedGreps(res);
                    List<String> merged = new ArrayList<String>();
                    for (String g : failed) if (!r.grep.contains(g)) merged.add(g);
                    for (String g : now) if (!merged.contains(g)) merged.add(g);
                    failed = merged;
                }
            }
            if (r.label.equals("failed") && !failed.isEmpty() && plan.size() > 1) {
                out.println("  failures remain - skipping the rest (Enter to run everything)");
                return;
            }
        }
    }

    private static Set<String> union(Set<String> a, Set<String> b) {
        Set<String> s = new java.util.TreeSet<String>(a);
        s.addAll(b);
        return s;
    }

    /** One {@code gatf test} child run; returns its results, or null when it wrote none. */
    JsonNode runOnce(WatchPlanner.Run r) {
        List<String> cmd = new ArrayList<String>();
        cmd.add(Paths.get(System.getProperty("java.home"), "bin", "java").toString());
        cmd.addAll(RunArgs.jvmFlags(java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments()));
        cmd.add("-cp");
        cmd.add(System.getProperty("java.class.path"));
        cmd.add("com.gatf.generator.core.GatfCli");
        cmd.add("test");
        cmd.addAll(baseArgs);
        for (String g : r.grep) { cmd.add("--grep"); cmd.add(g); }
        for (String g : r.grepInvert) { cmd.add("--grep-invert"); cmd.add(g); }
        if (!hasJsonReporter(baseArgs)) { cmd.add("--reporter"); cmd.add("junit,json"); }
        out.println();
        out.println("  -- " + r.label + (r.isFull() ? "" : " (" + (r.grep.size() + r.grepInvert.size()) + " filter(s))") + " --");
        long start = System.currentTimeMillis() - 2000;
        // a run that writes no results must not pick up the previous run's file: require a strictly newer one
        long before = mtime(RunResults.latestQuietly(root));
        try {
            Process p = new ProcessBuilder(cmd).directory(root.toFile()).inheritIO().redirectInput(ProcessBuilder.Redirect.PIPE).start();
            p.getOutputStream().close();
            p.waitFor();
            Path res = RunResults.latest(root, Math.max(start, before + 1));
            return res == null ? null : om.readTree(res.toFile());
        } catch (Exception e) {
            out.println("  run failed to start: " + e.getMessage());
            return null;
        }
    }

    static long mtime(Path p) {
        try { return p == null ? -1 : Files.getLastModifiedTime(p).toMillis(); } catch (IOException e) { return -1; }
    }

    static boolean hasUserFilter(List<String> args) {
        for (String a : args) {
            String k = a.contains("=") ? a.substring(0, a.indexOf('=')) : a;
            if (k.equals("--grep") || k.equals("-g") || k.equals("--grep-invert") || k.equals("--tag") || k.equals("-t")) return true;
        }
        return false;
    }

    static boolean hasJsonReporter(List<String> args) {
        for (int i = 0; i < args.size(); i++) {
            String a = args.get(i);
            String v = a.startsWith("--reporter=") ? a.substring(11) : a.equals("--reporter") && i + 1 < args.size() ? args.get(i + 1) : null;
            if (v != null) return v.toLowerCase(java.util.Locale.ROOT).contains("json");
        }
        return false;
    }

    /** path (relative, '/') → mtime of every watched file. */
    Map<String, Long> scan() {
        final Map<String, Long> m = new HashMap<String, Long>();
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes a) {
                    if (d.equals(root)) return FileVisitResult.CONTINUE;
                    String n = String.valueOf(d.getFileName());
                    return WatchPlanner.SKIP_DIRS.contains(n) || n.startsWith(".") ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
                }
                @Override
                public FileVisitResult visitFile(Path f, BasicFileAttributes a) {
                    String rel = root.relativize(f).toString().replace('\\', '/');
                    if (WatchPlanner.watched(rel)) m.put(rel, a.lastModifiedTime().toMillis());
                    return FileVisitResult.CONTINUE;
                }
                @Override
                public FileVisitResult visitFileFailed(Path f, IOException e) { return FileVisitResult.CONTINUE; }
            });
        } catch (IOException ignore) { /* keep what was read */ }
        return m;
    }
}
