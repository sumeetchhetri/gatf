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

import java.io.Console;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.concurrent.atomic.LongAdder;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Playwright-style run reporter for CLI runs. It prints one coloured line per finished test, then a summary:
 * <pre>
 *   ✓  1 [chrome] › ui/login-flow.sel (4.2s)
 *   ✘  2 api/users.xml › Create user (312ms)
 *        Expected status 201 but got 500
 *
 *   1 failed
 *     api/users.xml › Create user
 *   1 passed (6.1s)
 * </pre>
 * Only active once {@link #activate()} is called by the CLI, so UI-server and Maven-mojo runs are unaffected.
 * Per-test lines are suppressed for load / multi-user runs, where each test repeats thousands of times; the report
 * covers those. Every result is also written with full detail to the {@code gatf.run} logger, i.e. the log file.
 */
public final class RunConsole {

    public enum Status { PASSED, FAILED, SKIPPED }

    private static final Logger LOG = LogManager.getLogger("gatf.run");

    private static volatile boolean active = false;
    private static volatile String filterNote;   // Q5: e.g. "--tag @smoke", shown in the run header
    private static volatile boolean perTest = true;
    private static String reportDir = null;
    private static long startNanos;
    private static int seq;
    // LongAdder: load / multi-user runs report thousands of results per second from many threads; there we only
    // count (no lock, no per-result log I/O) so the reporter cannot perturb the load being measured
    private static final LongAdder passed = new LongAdder(), failed = new LongAdder(), skipped = new LongAdder();
    // Q4/Q6: per-test records (functional runs only). A selenium retry round re-reports failed scripts under the
    // same key, so the final status is the LAST attempt: failed-then-passed = FLAKY (passes the run, as in Playwright).
    private static final Map<String, Rec> recs = new LinkedHashMap<String, Rec>();
    private static boolean color, unicode;

    /** One test's history across attempts. */
    static final class Rec {
        final String suite, name, title;
        final List<Status> attempts = new ArrayList<Status>();
        long millis;
        String error, detail;
        // Q3: files for this test's LAST attempt (failure screenshot, kept trace.zip); absolute paths
        final List<String> attachments = new ArrayList<String>();
        // M2: soft (??~) assertion failures in the LAST attempt
        int soft;
        Rec(String suite, String name, String title) { this.suite = suite; this.name = name; this.title = title; }
        Status last() { return attempts.get(attempts.size() - 1); }
        boolean flaky() { return last() == Status.PASSED && attempts.contains(Status.FAILED); }
    }

    private RunConsole() {}

    /** Turn the reporter on (CLI runs only). */
    public static synchronized void activate() {
        active = true;
        perTest = true;
        startNanos = System.nanoTime();
        seq = 0;
        passed.reset();
        failed.reset();
        skipped.reset();
        recs.clear();
        color = detectColor();
        unicode = detectUnicode();
    }

    public static boolean isActive() { return active; }

    /** True once any test result has been reported in this run. */
    public static boolean hasResults() { return passed.sum() + failed.sum() + skipped.sum() > 0; }

    /** Q5: the active test filter, shown in the run header (null = none). */
    public static void setFilterNote(String note) { filterNote = note; }

    /** Q11: the run's report dir (where gatf-results.json is written); null before a suite started. */
    public static String reportDir() { return reportDir; }

    /** True when per-test lines/log entries are wanted, so callers can skip building costly detail strings. */
    public static boolean wantsDetail() { return active && perTest; }

    /** Called when a suite starts executing. {@code perTestLines=false} for load / multi-user runs. */
    public static synchronized void suite(String kind, String outDir, int workers, boolean perTestLines) {
        if (!active) return;
        perTest = perTestLines;
        reportDir = outDir;
        PrintStream o = GatfLogging.out();
        o.println();
        o.println(dim("Running GATF " + kind + (workers > 1 ? " tests using " + workers + " workers" : " tests")
                + (perTestLines ? "" : " (load / multi-user run: per-test lines suppressed, see the report)")
                + (filterNote != null ? ", filter: " + filterNote : "")));
        o.println();
        LOG.info("suite start: kind={} outDir={} workers={} perTestLines={}", kind, outDir, workers, perTestLines);
    }

    /**
     * One finished test.
     * @param title  e.g. {@code api/users.xml › Create user} or {@code [chrome] › ui/login.sel › subtest}
     * @param error  short failure reason (first line is shown on the console), may be null
     * @param detail full detail for the log file (url, status code, stack...), may be null
     */
    public static void result(String title, Status status, long millis, String error, String detail) {
        result(null, null, title, status, millis, error, detail);
    }

    /**
     * One finished test, with the grouping used by the CI reporters: {@code suite} = the test file (JUnit
     * testsuite/classname), {@code name} = the test within it (JUnit testcase). Null suite/name derive from the title.
     */
    public static void result(String suite, String name, String title, Status status, long millis, String error, String detail) {
        result(suite, name, title, status, millis, error, detail, null);
    }

    /** As above, plus {@code attachments}: files for this attempt (failure screenshot, trace.zip), absolute paths. */
    public static void result(String suite, String name, String title, Status status, long millis, String error, String detail,
            List<String> attachments) {
        result(suite, name, title, status, millis, error, detail, attachments, 0);
    }

    /** As above, plus {@code softFailures}: M2 soft ({@code ??~}) assertion failures in this attempt. */
    public static void result(String suite, String name, String title, Status status, long millis, String error, String detail,
            List<String> attachments, int softFailures) {
        if (!active) return;
        (status == Status.PASSED ? passed : status == Status.FAILED ? failed : skipped).increment();
        if (!perTest) return;
        line(suite, name, title, status, millis, error, detail, attachments, softFailures);
    }

    private static synchronized void line(String suite, String name, String title, Status status, long millis, String error, String detail,
            List<String> attachments, int softFailures) {
        String key = (suite == null ? "" : suite) + '\u0000' + title;
        Rec r = recs.get(key);
        boolean retry = r != null;
        if (r == null) {
            int cut = title.lastIndexOf(" \u203a ");
            r = new Rec(suite != null ? suite : (cut > 0 ? title.substring(0, cut) : "gatf"),
                    name != null ? name : (cut > 0 ? title.substring(cut + 3) : title), title);
            recs.put(key, r);
        }
        r.attempts.add(status);
        r.millis = millis;
        r.error = error;
        r.detail = detail;
        r.attachments.clear();
        if (attachments != null) r.attachments.addAll(attachments);
        r.soft = Math.max(0, softFailures);
        if (!unicode) title = title.replace('\u203a', '>');
        int n = ++seq;
        if (status == Status.FAILED) {
            LOG.error("FAILED {} ({}ms){}{}", title, millis, error != null ? " - " + error : "", detail != null ? "\n" + detail : "");
        } else {
            LOG.info("{} {} ({}ms){}", status, title, millis, detail != null ? "\n" + detail : "");
        }
        PrintStream o = GatfLogging.out();
        String mark = status == Status.PASSED ? green(unicode ? "\u2713" : "ok")
                : status == Status.FAILED ? red(unicode ? "\u2718" : "x") : yellow("-");
        String line = "  " + mark + "  " + dim(String.valueOf(n)) + " " + title
                + (status == Status.SKIPPED ? "" : " " + dim("(" + dur(millis) + ")"))
                + (retry ? " " + yellow("retry #" + (r.attempts.size() - 1)) : "");
        o.println(line);
        if (status == Status.FAILED && error != null && !error.trim().isEmpty()) {
            o.println("       " + red(firstLine(error)));
        }
        if (r.soft > 0 && error != null && error.indexOf('\n') > 0) {
            for (String s : error.substring(error.indexOf('\n') + 1).split("\n")) o.println("       " + red(s.trim()));
        }
        if (status == Status.FAILED) {
            for (String a : r.attachments) {
                o.println("       " + dim((a.endsWith(".zip") ? "trace:      " : "screenshot: ") + a));
            }
        }
        if (!r.attachments.isEmpty()) LOG.info("  attachments: {}", r.attachments);
    }

    /** A selenium retry round is starting (seleniumScriptRetryCount). */
    public static synchronized void retrying(int scripts, int round) {
        if (!active || !perTest) return;
        GatfLogging.out().println();
        GatfLogging.out().println(yellow("  Retrying " + scripts + " failed script(s), round " + round));
        LOG.info("retry round {}: {} script(s)", round, scripts);
    }

    /** Prints the summary and writes the CI reports; returns the number of failed tests (the CLI's exit code source). */
    public static synchronized int finish() {
        if (!active) return 0;
        active = false;
        PrintStream o = GatfLogging.out();
        long ms = (System.nanoTime() - startNanos) / 1_000_000L;
        long passed, failed, skipped, flaky = 0, soft = 0;
        List<String> failures = new ArrayList<String>(), flakies = new ArrayList<String>();
        if (!recs.isEmpty()) {   // functional run: final status per test (last attempt)
            passed = failed = skipped = 0;
            for (Rec r : recs.values()) {
                soft += r.soft;
                if (r.flaky()) { flaky++; flakies.add(r.title); }
                else if (r.last() == Status.PASSED) passed++;
                else if (r.last() == Status.FAILED) { failed++; failures.add(r.title); }
                else skipped++;
            }
        } else {                 // load / multi-user run: counters only
            passed = RunConsole.passed.sum(); failed = RunConsole.failed.sum(); skipped = RunConsole.skipped.sum();
        }
        long total = passed + failed + skipped + flaky;
        o.println();
        if (total == 0) {
            o.println(yellow("  No tests were executed."));
        }
        if (failed > 0) {
            o.println(red("  " + failed + " failed"));
            if (failures.isEmpty()) o.println(dim("    (per-test detail is in the report)"));
            int shown = 0;
            for (String f : failures) {
                if (shown++ == 20) { o.println(dim("    ... " + (failures.size() - 20) + " more")); break; }
                o.println(red("    " + (unicode ? f : f.replace('\u203a', '>'))));
            }
        }
        if (flaky > 0) {
            o.println(yellow("  " + flaky + " flaky"));
            for (String f : flakies) o.println(yellow("    " + (unicode ? f : f.replace('\u203a', '>'))));
        }
        if (soft > 0) o.println(red("  " + soft + " soft assertion failure(s)"));
        if (skipped > 0) o.println(yellow("  " + skipped + " skipped"));
        if (passed > 0) o.println(green("  " + passed + " passed") + dim(" (" + dur(ms) + ")"));
        else if (total > 0) o.println(dim("  (" + dur(ms) + ")"));
        o.println();
        if (reportDir != null) o.println(dim("  Report: " + reportDir));
        for (String f : CiReports.write(new ArrayList<Rec>(recs.values()), reportDir, ms)) {
            o.println(dim("  " + f));
        }
        o.println(dim("  Log:    " + GatfLogging.logFile()));
        o.println();
        LOG.info("run finished: passed={} failed={} flaky={} skipped={} in {}ms", passed, failed, flaky, skipped, ms);
        return (int) Math.min(Integer.MAX_VALUE, failed);
    }

    /** The run aborted with an exception before completing. */
    public static synchronized void abort(Throwable t) {
        active = false;
        LOG.error("run aborted", t);
        PrintStream o = GatfLogging.out();
        o.println();
        o.println(red("  Run aborted: " + (t.getMessage() != null ? firstLine(t.getMessage()) : t.getClass().getName())));
        o.println(dim("  Details: " + GatfLogging.logFile()));
        o.println();
    }

    // ---- formatting ----------------------------------------------------------------------------------------------

    static String dur(long ms) {
        if (ms < 1000) return ms + "ms";
        if (ms < 60_000) return String.format(Locale.ROOT, "%.1fs", ms / 1000.0);
        return (ms / 60_000) + "m " + ((ms % 60_000) / 1000) + "s";
    }

    static String firstLine(String s) {
        String t = s.trim();
        int i = t.indexOf('\n');
        if (i >= 0) t = t.substring(0, i).trim();
        return t.length() > 200 ? t.substring(0, 200) + "\u2026" : t;
    }

    private static String green(String s) { return ansi("32", s); }
    private static String red(String s) { return ansi("31", s); }
    private static String yellow(String s) { return ansi("33", s); }
    private static String dim(String s) { return ansi("2", s); }
    private static String ansi(String code, String s) { return color ? "\u001b[" + code + "m" + s + "\u001b[0m" : s; }

    static boolean detectColor() {
        String mode = System.getProperty("gatf.color", "auto").toLowerCase(Locale.ROOT);
        if (mode.equals("always")) return true;
        if (mode.equals("never")) return false;
        if (System.getenv("NO_COLOR") != null) return false;
        String force = System.getenv("FORCE_COLOR");
        if (force != null) return !force.equals("0");
        if (System.getenv("GITHUB_ACTIONS") != null) return true;          // Actions logs render ANSI
        if (!isTerminal()) return false;
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows")) {
            // legacy conhost does not interpret ANSI by default; Windows Terminal / VS Code / ConEmu do
            return System.getenv("WT_SESSION") != null || System.getenv("TERM_PROGRAM") != null
                    || System.getenv("ConEmuANSI") != null || System.getenv("TERM") != null;
        }
        return !"dumb".equals(System.getenv("TERM"));
    }

    static boolean isTerminal() {
        Console c = System.console();
        if (c == null) return false;
        try {   // JDK 22+: System.console() is non-null even when redirected; isTerminal() tells the truth
            Method m = Console.class.getMethod("isTerminal");
            return (Boolean) m.invoke(c);
        } catch (NoSuchMethodException e) {
            return true;   // JDK 21: non-null console means an interactive terminal
        } catch (Exception e) {
            return false;
        }
    }

    static boolean detectUnicode() {
        Charset cs = GatfLogging.out().charset();
        return cs.name().toUpperCase(Locale.ROOT).startsWith("UTF");
    }
}
