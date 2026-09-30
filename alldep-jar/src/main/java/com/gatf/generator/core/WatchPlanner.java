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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * M15: the decisions behind {@code gatf test --watch}, kept pure so they are unit-tested: which files are watched,
 * what changed between two scans, when a burst of saves has settled (debounce), and what to run next — the changed
 * scripts plus the tests that failed last time, or the whole suite with the failed tests first.
 */
public final class WatchPlanner {

    /** Directories never watched (build output, reports, VCS, traces). */
    public static final Set<String> SKIP_DIRS = new HashSet<String>(Arrays.asList(
            ".git", "node_modules", "target", "out", "reports", "logs", "history", "dist", ".trace", "__screenshots__", ".mvn-node"));
    static final Set<String> EXT = new HashSet<String>(Arrays.asList(
            "sel", "xml", "json", "properties", "props", "csv", "yaml", "yml", "js", "har", "jmx"));
    /** Files a run writes itself; a change to them must not trigger another run. */
    static final Set<String> OUTPUTS = new HashSet<String>(Arrays.asList("gatf-results.json", "gatf-junit.xml"));

    private WatchPlanner() {}

    public static boolean watched(String relPath) {
        String[] segs = relPath.replace('\\', '/').split("/");
        for (int i = 0; i < segs.length - 1; i++) {
            if (SKIP_DIRS.contains(segs[i]) || (segs[i].startsWith(".") && !segs[i].equals(".") && !segs[i].equals(".."))) return false;
        }
        String name = segs[segs.length - 1];
        if (OUTPUTS.contains(name) || name.startsWith(".")) return false;
        int dot = name.lastIndexOf('.');
        return dot > 0 && EXT.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    /** Paths added, removed or modified between two scans (path → mtime). */
    public static Set<String> diff(Map<String, Long> before, Map<String, Long> after) {
        Set<String> out = new TreeSet<String>();
        for (Map.Entry<String, Long> e : after.entrySet()) {
            Long b = before.get(e.getKey());
            if (b == null || !b.equals(e.getValue())) out.add(e.getKey());
        }
        for (String k : before.keySet()) if (!after.containsKey(k)) out.add(k);
        return out;
    }

    /** Collects changes and releases them once no change has arrived for {@code quietMs} (editors save in bursts). */
    public static final class Debouncer {
        private final long quietMs;
        private final Set<String> pending = new TreeSet<String>();
        private long last = Long.MIN_VALUE;

        public Debouncer(long quietMs) { this.quietMs = quietMs; }

        public void changed(Set<String> paths, long now) {
            if (paths == null || paths.isEmpty()) return;
            pending.addAll(paths);
            last = now;
        }

        /** The settled batch (and clears it), or an empty set while changes are still arriving / none are pending. */
        public Set<String> poll(long now) {
            if (pending.isEmpty() || now - last < quietMs) return Collections.emptySet();
            Set<String> out = new TreeSet<String>(pending);
            pending.clear();
            return out;
        }

        public boolean hasPending() { return !pending.isEmpty(); }
    }

    /** One run of the cycle: extra {@code --grep} / {@code --grep-invert} patterns (empty = no restriction). */
    public static final class Run {
        public final String label;
        public final List<String> grep;
        public final List<String> grepInvert;
        Run(String label, List<String> grep, List<String> grepInvert) {
            this.label = label; this.grep = grep; this.grepInvert = grepInvert;
        }
        public boolean isFull() { return grep.isEmpty() && grepInvert.isEmpty(); }
        @Override public String toString() { return label + " grep=" + grep + " grepInvert=" + grepInvert; }
    }

    /**
     * After a save: the changed scripts / API case files and the previously failed tests, in one focused run. Only files
     * the last run knew as suites ({@code known}, by file name) are focused; a change to anything else — a library
     * script others import, the config, properties, data — can affect any test, so the whole suite runs, failed first.
     */
    public static List<Run> afterChange(Set<String> changed, List<String> failed, Set<String> known) {
        List<String> grep = new ArrayList<String>();
        boolean onlyTests = !changed.isEmpty() && known != null && !known.isEmpty();
        for (String c : changed) {
            if (!onlyTests) break;
            String n = baseName(c);
            if (known.contains(n)) grep.add(fileGrep(n));
            else onlyTests = false;
        }
        if (!onlyTests) return full(failed);
        Set<String> g = new LinkedHashSet<String>(grep);
        g.addAll(failed);
        return Collections.singletonList(new Run("changed" + (failed.isEmpty() ? "" : " + failed"), new ArrayList<String>(g),
                Collections.<String>emptyList()));
    }

    /** File names of every suite (script / API case file) in a {@code gatf-results.json}. */
    public static Set<String> knownSuites(JsonNode results) {
        Set<String> out = new TreeSet<String>();
        if (results == null) return out;
        for (JsonNode t : results.path("tests")) {
            String s = t.path("suite").asText("");
            if (!s.isEmpty()) out.add(baseName(s));
        }
        return out;
    }

    static String baseName(String p) {
        String n = p.replace('\\', '/');
        return n.substring(n.lastIndexOf('/') + 1);
    }

    /** The whole suite, the last run's failures first (then everything else, without running them twice). */
    public static List<Run> full(List<String> failed) {
        if (failed.isEmpty()) return Collections.singletonList(new Run("all", Collections.<String>emptyList(), Collections.<String>emptyList()));
        List<String> q = new ArrayList<String>(failed);
        return Arrays.asList(new Run("failed", q, Collections.<String>emptyList()),
                new Run("rest", Collections.<String>emptyList(), q));
    }

    /** Only the tests that failed last time (the {@code f} key); nothing when all passed. */
    public static List<Run> failedOnly(List<String> failed) {
        if (failed.isEmpty()) return Collections.emptyList();
        List<String> q = new ArrayList<String>(failed);
        return Collections.singletonList(new Run("failed", q, Collections.<String>emptyList()));
    }

    /** A test title contains its file name ({@code login.sel}, {@code users.xml › …}): match it as a whole path segment (ASCII only: it travels on a command line). */
    static String fileGrep(String fileName) {
        return "(^|[/\\\\ ])" + Pattern.quote(fileName) + "($|\\s)";
    }

    /**
     * {@code --grep} patterns that re-select the failed / flaky / timed-out tests of a {@code gatf-results.json}. A UI
     * script is selected as a whole (the filter matches its path, not a subtest), so it is matched by file name; an API
     * case by its exact title ({@code <file> › <name>}).
     */
    public static List<String> failedGreps(JsonNode results) {
        List<String> out = new ArrayList<String>();
        if (results == null) return out;
        for (JsonNode t : results.path("tests")) {
            String st = t.path("status").asText("");
            if (!(st.equals("failed") || st.equals("flaky") || st.equals("timedOut"))) continue;
            String suite = t.path("suite").asText("").replace('\\', '/');
            String g;
            if (suite.endsWith(".sel")) {
                g = fileGrep(suite.substring(suite.lastIndexOf('/') + 1));
            } else {
                String title = t.path("title").asText(null);
                if (title == null || title.isEmpty()) title = suite + " › " + t.path("name").asText("");
                g = "(^|[/\\\\ ])" + Pattern.quote(title) + "($|\\s@)";   // the whole title: "Get user" is not "Get users"
            }
            if (!out.contains(g)) out.add(g);
        }
        return out;
    }
}
