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
package com.gatf.coverage;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * H6 (Δ156): test-impact selection. Given the coverage index of a run ({@link CoverageIndex#build}, v2), the files a
 * git range changed and read access to the base commit and the run's traces, decides per test whether the change can
 * reach it. Pure: git and the trace bundles come in through {@link Repo} and {@link Bodies}.
 *
 * <p><b>The safe direction always wins.</b> Everything run-wide that could hide a test (no or unusable index, a rule
 * this class doesn't know, a skip that names no test, an index older than the base commit or of unknown age, a script
 * URL with more than one body, a CSS change, a changed file no script maps to) runs every test, with the reason. A
 * test is deselected only when every changed file maps to scripts and, for each script the test loaded, the change is
 * provably outside the code the test ran:
 * <ul>
 * <li>the changed lines map (through the build's source map, or 1:1 when the served script is byte-identical to the
 * file at the base commit) to generated code, and the build's copy of the file is proven to be the base commit's
 * (sourcesContent or the body equals it), else that file is mapped at file level;</li>
 * <li>the generated span, widened to the neighbouring mapped lines, touches no covered range and lies strictly inside
 * a range V8 reported as never run in that test ({@code uncovered});</li>
 * <li>the edit keeps its block and declares nothing that hoists ({@link EditCheck}).</li>
 * </ul>
 * A script whose map is missing or unparsable, or whose body wasn't captured, is opaque: it may contain any changed
 * source, so every test that loaded it is selected whenever application code changed.
 *
 * @author Sumeet Chhetri
 */
public final class ImpactSelector {

    private ImpactSelector() {}

    /** The base commit of the range, as git sees it. */
    public interface Repo {
        String base();
        /** Commit time of {@link #base()} in ms, or null when unknown. */
        Long baseTimeMs();
        /** Every file at the base commit, repo-relative with '/'. */
        List<String> tracked();
        /** A file's bytes at the base commit, or null (absent, unreadable, too large). */
        byte[] baseContent(String path);
    }

    /** The run's trace bundles. */
    public interface Bodies {
        /** resources/&lt;sha1&gt; from one of these traces, or null. */
        byte[] body(String sha1, Collection<String> traces);
        /** The captured response body for {@code url} (a network.har entry with a _file) in one of these traces, or null. */
        byte[] harFile(String url, Collection<String> traces);
    }

    public static final class Options {
        /** Where to look for external source maps (the build output); null = only inline and captured maps. */
        public Path sourceMapsDir;
        /** True for a repo path that is one of the suite's own test scripts (a change to it selects that test). */
        public Predicate<String> isTestScript = p -> false;
    }

    public static final class Verdict {
        public final boolean selected;
        public final List<String> reasons;
        Verdict(boolean selected, List<String> reasons) { this.selected = selected; this.reasons = reasons; }
    }

    public static final class Result {
        /** Non-empty: run every test; each entry is one reason. */
        public final List<String> runAll = new ArrayList<>();
        public boolean nothingChanged;
        /** Per index test id. Empty when {@link #runAll} is set. */
        public final SortedMap<String, Verdict> tests = new TreeMap<>();
        /** Repo paths of the suite's own test scripts that changed. */
        public final SortedSet<String> changedScripts = new TreeSet<>();
        /** How each changed file mapped, and what was opaque; for --explain. */
        public final List<String> notes = new ArrayList<>();

        public boolean runsAll() { return !runAll.isEmpty(); }
    }

    /** Rule keys this selector implements; an index carrying any other rule is not trusted. */
    static final Set<String> KNOWN_RULES = new HashSet<>(Arrays.asList("offsets", "scripts", "coverage", "bodies", "css", "unmapped", "urls",
            "routes", "fileLevel", "uncovered", "skipped", "traceStart", "outcome"));
    static final Set<String> CSS_EXT = new HashSet<>(Arrays.asList(".css", ".scss", ".sass", ".less", ".styl", ".stylus", ".pcss", ".postcss"));
    private static final int MAX_REASONS = 4;
    private static final Pattern SMU = Pattern.compile("[#@][ \\t]*sourceMappingURL=([^\\s'\"*]+)");

    // ------------------------------------------------------------------ entry point

    public static Result select(Map<String, Object> index, List<DiffParser.FileChange> changes, Repo repo, Bodies bodies, Options opt) {
        Options o = opt == null ? new Options() : opt;
        Result r = new Result();
        List<DiffParser.FileChange> ch = changes == null ? Collections.<DiffParser.FileChange>emptyList() : changes;
        if (ch.isEmpty()) {
            r.nothingChanged = true;
            return r;
        }
        JsonNode idx = index == null ? null : CoverageMerge.OM.valueToTree(index);
        if (!usable(idx, repo, r)) return r;

        for (DiffParser.FileChange c : ch) {
            if (o.isTestScript.test(c.path)) r.changedScripts.add(c.path);
            else if (isCss(c.path)) r.runAll.add(c.path + ": a stylesheet changed, and CSS coverage carries no stylesheet URL (unattributed)");
        }
        if (r.runsAll()) return r;

        // every script the run loaded, resolved to what can map onto it
        List<Unit> units = new ArrayList<>();
        Tracked tracked = new Tracked(repo.tracked());
        Iterator<Map.Entry<String, JsonNode>> it = idx.path("scripts").fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            units.add(resolve(e.getKey(), e.getValue(), idx, repo, bodies, o, tracked));
        }
        for (Unit u : units) if (u.opaque != null) r.notes.add("opaque: " + u.url + " (" + u.opaque + ")");

        List<Attr> attrs = new ArrayList<>();
        for (DiffParser.FileChange c : ch) {
            if (o.isTestScript.test(c.path)) continue;
            List<Attr> a = attribute(c, units, repo);
            boolean mapped = false;
            for (Attr x : a) mapped |= !x.loose;   // a name-only match never proves where a file went
            if (!mapped) {
                r.runAll.add(c.path + " (" + c.status.name().toLowerCase(Locale.ROOT) + "): not mapped to any script the tests loaded"
                        + " (no source map source or served file matches it)");
                continue;
            }
            for (Attr x : a) r.notes.add("mapped: " + c.path + " -> " + x.u.url + (x.fileLevel ? " at file level (" + x.why + ")" : " by line (" + x.iv.size() + " span(s))"));
            attrs.addAll(a);
        }
        if (r.runsAll()) return r;

        Iterator<Map.Entry<String, JsonNode>> tests = idx.path("tests").fields();
        while (tests.hasNext()) {
            Map.Entry<String, JsonNode> e = tests.next();
            r.tests.put(e.getKey(), judge(e.getValue(), attrs, units));
        }
        return r;
    }

    // run-wide reasons not to trust the index for any deselection
    private static boolean usable(JsonNode idx, Repo repo, Result r) {
        if (idx == null || !idx.isObject() || !CoverageMerge.FORMAT.equals(idx.path("format").asText())) {
            r.runAll.add("no coverage index for the run (or not a " + CoverageMerge.FORMAT + ")");
            return false;
        }
        if (idx.path("version").asInt() != CoverageMerge.VERSION) {
            r.runAll.add("coverage index version " + idx.path("version").asText() + " is not v" + CoverageMerge.VERSION);
            return false;
        }
        if (!"full".equals(idx.path("detail").asText())) r.runAll.add("coverage index is a summary (no ranges)");
        JsonNode rules = idx.path("rules");
        if (!rules.isObject()) r.runAll.add("coverage index has no rules");
        for (Iterator<String> k = rules.fieldNames(); k.hasNext();) {
            String key = k.next();
            if (!KNOWN_RULES.contains(key)) r.runAll.add("coverage index rule '" + key + "' is not one this selector implements");
        }
        for (String key : KNOWN_RULES) {
            if (rules.isObject() && !rules.has(key)) r.runAll.add("coverage index lacks rule '" + key + "' (written by an older GATF)");
        }
        if ("no-data".equals(idx.path("status").asText())) r.runAll.add("coverage index has no data: " + String.join("; ", texts(idx.path("notes"))));
        for (JsonNode s : idx.path("skipped")) {
            if (!s.path("test").isNull() && !s.path("test").isMissingNode()) continue;
            String scope = s.path("scope").asText("");
            if (scope.equals("crawl") || scope.equals("other")) continue;
            r.runAll.add(s.path("file").asText("?") + ": " + s.path("reason").asText("") + " (no test named, so no test can be trusted)");
        }
        JsonNode ts = idx.path("traceStart");
        Long base = repo == null ? null : repo.baseTimeMs();
        if (!ts.path("oldest").isNumber()) {
            r.runAll.add("coverage index age unknown (" + ts.path("unknown").asInt(0) + " trace(s) without a startTs)");
        } else if (base == null) {
            r.runAll.add("base commit time unknown");
        } else if (ts.path("oldest").asLong() < base) {
            r.runAll.add("coverage index is older than the base commit " + shortSha(repo.base()) + " (oldest trace started "
                    + java.time.Instant.ofEpochMilli(ts.path("oldest").asLong()) + ", commit at " + java.time.Instant.ofEpochMilli(base) + ")");
        }
        Iterator<Map.Entry<String, JsonNode>> it = idx.path("scripts").fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            if (e.getValue().path("mixedBodies").asBoolean(false)) {
                r.runAll.add(e.getKey() + " has more than one body in the run (mixedBodies): unmapped");
            }
        }
        return !r.runsAll();
    }

    // ------------------------------------------------------------------ scripts

    /** A script URL the run loaded: its body and what maps onto it, or why nothing can (opaque). */
    static final class Unit {
        final String url;
        String opaque;
        String text;
        int[] ls, le;
        SourceMapV3 map;
        final Map<String, List<int[]>> sources = new HashMap<>();   // repo path -> [source index, aligned 0/1]
        final Set<String> identity = new TreeSet<>();              // repo paths byte-identical (modulo EOL) to the body
        // map sources this unit could not attribute to one proven file: any of these repo paths may be in it (file level)
        final Set<String> loose = new TreeSet<>();
        Unit(String url) { this.url = url; }
        int len() { return text.length(); }
    }

    private static Unit resolve(String url, JsonNode sn, JsonNode idx, Repo repo, Bodies bodies, Options o, Tracked tracked) {
        Unit u = new Unit(url);
        List<String> bs = texts(sn.path("bodies"));
        if (bs.size() != 1) { u.opaque = bs.isEmpty() ? "no body of it was captured in the run" : "more than one body"; return u; }
        String sha = bs.get(0);
        Set<String> traces = new LinkedHashSet<>();
        for (String t : texts(sn.path("tests"))) traces.addAll(texts(idx.path("tests").path(t).path("traces")));
        byte[] raw = bodies == null ? null : bodies.body(sha, traces);
        if (raw == null) { u.opaque = "its body resources/" + sha + " is not in the run's traces"; return u; }
        if (!sha.equals(sha1(raw))) { u.opaque = "its captured body does not hash to " + sha; return u; }
        String text = new String(raw, StandardCharsets.UTF_8);
        if (text.startsWith("﻿")) text = text.substring(1);
        if (text.length() != sn.path("len").asLong(-1)) {
            u.opaque = "its body is " + text.length() + " UTF-16 units but coverage saw " + sn.path("len").asLong(-1);
            return u;
        }
        u.text = text;
        String smu = sourceMappingUrl(text);
        if (smu != null) {
            URI mapUri = null;
            byte[] mb;
            if (smu.regionMatches(true, 0, "data:", 0, 5)) {
                mb = dataUri(smu);
                if (mb == null) { u.opaque = "its inline source map is not a readable data: URI"; return u; }
            } else {
                try {
                    mapUri = URI.create(url).resolve(smu);
                } catch (Exception e) {
                    u.opaque = "its sourceMappingURL is not a URL";
                    return u;
                }
                String key = CoverageUrls.script(mapUri.toString());
                mb = key == null || bodies == null ? null : bodies.harFile(key, traces);
                if (mb == null) mb = fromBuildDir(mapUri, o.sourceMapsDir, sha);
                if (mb == null) {
                    u.opaque = "its source map " + (key == null ? "(not an http(s)/file URL)" : key) + " was not captured"
                            + (o.sourceMapsDir == null ? " (pass --source-maps <build dir>)" : " and is not in " + o.sourceMapsDir + " beside an identical build of the script");
                    return u;
                }
            }
            try {
                u.map = SourceMapV3.parse(CoverageMerge.OM.readTree(mb));
            } catch (Exception e) {
                u.opaque = "its source map is unparsable (" + (e instanceof IllegalArgumentException ? e.getMessage() : e.getClass().getSimpleName()) + ")";
                return u;
            }
            lines(u, true);
            String fit = fits(u);
            if (fit != null) { u.opaque = "its source map does not fit the body (" + fit + ")"; u.map = null; return u; }
            String base = mapUri != null ? mapUri.toString() : url;
            for (int i = 0; i < u.map.sources.size(); i++) {
                String s = u.map.sources.get(i);
                if (s == null) continue;
                String content = u.map.sourcesContent.get(i);
                List<String> cands = tracked.match(sourcePath(u.map.sourceRoot, s, base));
                List<String> proven = new ArrayList<>();
                // proof needs git's line numbers to be the map's: no lone CR / U+2028 / U+2029 in the source
                if (content != null && !oddLines(content)) {
                    String want = norm(content);
                    for (String f : cands) {
                        byte[] b = repo.baseContent(f);
                        if (b != null && want.equals(norm(new String(b, StandardCharsets.UTF_8)))) proven.add(f);
                    }
                }
                if (!proven.isEmpty()) {
                    for (String f : proven) u.sources.computeIfAbsent(f, k -> new ArrayList<>()).add(new int[] {i, 1});
                } else if (cands.size() == 1 && !Tracked.basenameOnly(cands.get(0), sourcePath(u.map.sourceRoot, s, base))) {
                    u.sources.computeIfAbsent(cands.get(0), k -> new ArrayList<>()).add(new int[] {i, 0});
                } else {
                    // ambiguous, basename-only or no path match: not attributed by line, but every file it could be
                    // (same tail, else same name) is in this script at file level, even when another script proves it
                    u.loose.addAll(cands.isEmpty() ? tracked.byBasename(basename(sourcePath(u.map.sourceRoot, s, base))) : cands);
                }
            }
            return u;
        }
        // no source map: the served file may be a repo file as-is (lines as git counts them: '\n' only)
        lines(u, false);
        String want = norm(text);
        for (String f : tracked.byBasename(basename(url))) {
            byte[] b = repo.baseContent(f);
            if (b != null && want.equals(norm(new String(b, StandardCharsets.UTF_8)))) u.identity.add(f);
        }
        if (u.identity.isEmpty()) u.opaque = "no source map, and no file at the base commit has its exact content";
        return u;
    }

    // ecma: generated lines as source maps count them (\n, \r\n, \r, U+2028, U+2029); else as git does (\n only)
    private static void lines(Unit u, boolean ecma) {
        List<Integer> s = new ArrayList<>(), e = new ArrayList<>();
        String t = u.text;
        int start = 0;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (ecma ? (c == '\n' || c == '\r' || c == '\u2028' || c == '\u2029') : c == '\n') {
                s.add(start);
                e.add(!ecma && i > start && t.charAt(i - 1) == '\r' ? i - 1 : i);
                if (ecma && c == '\r' && i + 1 < t.length() && t.charAt(i + 1) == '\n') i++;
                start = i + 1;
            }
        }
        s.add(start);
        e.add(t.length());
        u.ls = s.stream().mapToInt(Integer::intValue).toArray();
        u.le = e.stream().mapToInt(Integer::intValue).toArray();
    }

    private static String fits(Unit u) {
        if (u.map.generatedLines > u.ls.length) return "it maps " + u.map.generatedLines + " lines, the body has " + u.ls.length;
        for (int g = 0; g < u.map.generatedLines; g++) {
            int width = u.le[g] - u.ls[g];
            for (SourceMapV3.Seg s : u.map.generatedLine(g)) {
                if (s.genCol > width) return "line " + (g + 1) + " column " + s.genCol + " is past its end";
            }
        }
        return null;
    }

    static String sourceMappingUrl(String text) {
        // only the last one counts, and only when nothing but whitespace (or a comment closer) follows it
        Matcher m = SMU.matcher(text);
        String last = null;
        int lastEnd = -1;
        while (m.find()) { last = m.group(1); lastEnd = m.end(); }
        if (last == null) return null;
        // nothing but whitespace / comment closers may follow it
        String tail = text.substring(lastEnd).trim();
        if (!(tail.isEmpty() || tail.equals("*/"))) return null;
        return last;
    }

    static byte[] dataUri(String uri) {
        int comma = uri.indexOf(',');
        if (comma < 0) return null;
        String meta = uri.substring(5, comma).toLowerCase(Locale.ROOT);
        String data = uri.substring(comma + 1);
        try {
            if (meta.endsWith(";base64")) return Base64.getDecoder().decode(data.trim());
            return URLDecoder.decode(data.replace("+", "%2B"), "UTF-8").getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    // an external map from the build dir, trusted only when the script beside it is byte-identical to the traced body
    private static byte[] fromBuildDir(URI mapUri, Path dir, String bodySha) {
        if (dir == null || mapUri == null) return null;
        String p = mapUri.getPath();
        if (p == null || p.isEmpty()) return null;
        List<Path> cands = new ArrayList<>();
        Path root = dir.toAbsolutePath().normalize();
        Path full = root.resolve(p.replaceFirst("^/+", "")).normalize();
        if (full.startsWith(root)) cands.add(full);
        String bn = basename(p);
        if (!bn.isEmpty()) cands.add(root.resolve(bn));
        for (Path c : cands) {
            try {
                if (!Files.isRegularFile(c) || Files.size(c) > 256L << 20) continue;
                String name = c.getFileName().toString();
                if (!name.endsWith(".map")) continue;
                Path gen = c.resolveSibling(name.substring(0, name.length() - 4));
                if (!Files.isRegularFile(gen) || Files.size(gen) > 256L << 20) continue;
                if (!bodySha.equals(sha1(Files.readAllBytes(gen)))) continue;
                return Files.readAllBytes(c);
            } catch (Exception ignored) {
                // next candidate
            }
        }
        return null;
    }

    /** A map source as a '/' path without scheme, namespace dots or leading slash (e.g. webpack://app/./src/a.ts → app/src/a.ts). */
    static String sourcePath(String sourceRoot, String source, String base) {
        String s = (sourceRoot == null || sourceRoot.isEmpty()) ? source : sourceRoot.endsWith("/") ? sourceRoot + source : sourceRoot + "/" + source;
        s = s.replace('\\', '/');
        Matcher sch = Pattern.compile("^([a-zA-Z][a-zA-Z0-9+.-]*):").matcher(s);
        String path;
        if (sch.find() && sch.group(1).length() > 1) {
            String scheme = sch.group(1).toLowerCase(Locale.ROOT);
            if (scheme.equals("http") || scheme.equals("https") || scheme.equals("file")) {
                try { path = URI.create(s).getPath(); } catch (Exception e) { path = s.substring(s.indexOf(':') + 1); }
            } else {
                path = s.substring(s.indexOf(':') + 1);          // webpack://, vite:, ng:// …: the rest is a path
            }
        } else if (s.startsWith("/")) {
            path = s;
        } else {
            try {
                URI b = URI.create(base);
                String bs = b.getScheme() == null ? "" : b.getScheme().toLowerCase(Locale.ROOT);
                path = (bs.equals("http") || bs.equals("https") || bs.equals("file")) ? b.resolve(new URI(null, null, s, null)).getPath() : s;
            } catch (Exception e) {
                path = s;
            }
        }
        if (path == null) path = "";
        int q = path.indexOf('?');
        if (q >= 0) path = path.substring(0, q);
        List<String> segs = new ArrayList<>();
        for (String seg : path.split("/")) {
            if (seg.isEmpty() || seg.equals(".")) continue;
            if (seg.equals("..")) { if (!segs.isEmpty()) segs.remove(segs.size() - 1); continue; }
            segs.add(seg);
        }
        return String.join("/", segs);
    }

    /** The files at the base commit, indexed by name. */
    static final class Tracked {
        final Map<String, List<String>> byName = new HashMap<>();

        Tracked(List<String> files) {
            if (files == null) return;
            for (String f : files) byName.computeIfAbsent(basename(f), k -> new ArrayList<>()).add(f);
        }

        List<String> byBasename(String n) {
            List<String> l = byName.get(n);
            return l == null ? Collections.<String>emptyList() : l;
        }

        /** Tracked files ending with the longest matching tail of {@code path}, on segment boundaries. */
        List<String> match(String path) {
            if (path == null || path.isEmpty()) return Collections.emptyList();
            String[] segs = path.split("/");
            List<String> pool = byBasename(segs[segs.length - 1]);
            for (int k = 0; k < segs.length; k++) {
                String tail = String.join("/", Arrays.copyOfRange(segs, k, segs.length));
                List<String> out = new ArrayList<>();
                for (String f : pool) if (f.equals(tail) || f.endsWith("/" + tail)) out.add(f);
                if (!out.isEmpty()) return out;
            }
            return Collections.emptyList();
        }

        // matched on the file name alone (the source named a single segment, or only its last segment matched)
        static boolean basenameOnly(String file, String sourcePath) {
            String[] fs = file.split("/"), ss = sourcePath.split("/");
            if (fs.length < 2 || ss.length < 2) return true;
            return !fs[fs.length - 2].equals(ss[ss.length - 2]);
        }
    }

    // ------------------------------------------------------------------ changes

    /** A changed file mapped onto one script: line spans in the script, or file level. */
    static final class Attr {
        final Unit u;
        final String file;
        boolean fileLevel, loose;
        String why;
        final List<long[]> iv = new ArrayList<>();
        final List<String> where = new ArrayList<>();
        Attr(Unit u, String file) { this.u = u; this.file = file; }
    }

    private static List<Attr> attribute(DiffParser.FileChange c, List<Unit> units, Repo repo) {
        List<Attr> out = new ArrayList<>();
        for (Unit u : units) {
            if (u.opaque != null) continue;
            if (u.map != null) {
                List<int[]> refs = u.sources.get(c.path);
                if (refs != null) for (int[] ref : refs) out.add(lines(c, u, ref[0], ref[1] == 1));
                if (u.loose.contains(c.path)) {
                    Attr a = file(new Attr(u, c.path), "its source map names a source that may be " + c.path + " (not provably another file)");
                    a.loose = true;
                    out.add(a);
                }
            } else if (u.identity.contains(c.path)) {
                out.add(lines(c, u, -1, true));
            }
        }
        return out;
    }

    private static Attr lines(DiffParser.FileChange c, Unit u, int src, boolean aligned) {
        Attr a = new Attr(u, c.path);
        if (c.status == DiffParser.Status.DELETED) return file(a, "the file was deleted");
        if (c.status == DiffParser.Status.ADDED) return file(a, "the file is new");
        if (c.wholeFile) return file(a, c.why);
        if (!aligned) return file(a, "the build's copy of " + c.path + " is not proven to be the base commit's (no matching sourcesContent)");
        for (DiffParser.Hunk h : c.hunks) {
            String bad = EditCheck.check(h.removed);
            if (bad == null) bad = EditCheck.check(h.added);
            if (bad != null) return file(a, "the edit at " + h.lines() + " is not provably local: " + bad);
            long[] iv = src < 0 ? identitySpan(u, h) : mapSpan(u, src, h);
            a.iv.add(iv);
            a.where.add(c.path + " " + h.lines());
        }
        return a;
    }

    private static Attr file(Attr a, String why) {
        a.fileLevel = true;
        a.why = why;
        a.iv.clear();
        a.where.clear();
        return a;
    }

    private static int first(DiffParser.Hunk h) { return h.oldCount > 0 ? h.oldStart : h.oldStart + 1; }
    private static int last(DiffParser.Hunk h) { return h.oldCount > 0 ? h.oldStart + h.oldCount - 1 : h.oldStart; }

    // generated span of the changed lines, widened from the last token of the mapped line above to the first token of
    // the mapped line below (so code inserted between them is inside the span)
    static long[] mapSpan(Unit u, int src, DiffParser.Hunk h) {
        NavigableMap<Integer, List<SourceMapV3.Seg>> lines = u.map.lines(src);
        int f = first(h), l = last(h);
        long lo = Long.MAX_VALUE, hi = Long.MIN_VALUE;
        if (f <= l) {
            for (List<SourceMapV3.Seg> segs : lines.subMap(f, true, l, true).values()) {
                for (SourceMapV3.Seg s : segs) {
                    lo = Math.min(lo, start(u, s));
                    hi = Math.max(hi, end(u, s));
                }
            }
        }
        Map.Entry<Integer, List<SourceMapV3.Seg>> above = lines.lowerEntry(f), below = lines.higherEntry(l);
        long a = 0, b = u.len();
        if (above != null) { a = Long.MIN_VALUE; for (SourceMapV3.Seg s : above.getValue()) a = Math.max(a, start(u, s)); }
        if (below != null) { b = Long.MAX_VALUE; for (SourceMapV3.Seg s : below.getValue()) b = Math.min(b, start(u, s) + 1); }
        lo = Math.min(lo, Math.min(a, b - 1));
        hi = Math.max(hi, Math.max(b, a + 1));
        return new long[] {Math.max(0, lo), Math.min(u.len(), hi)};
    }

    // 1:1 file: the changed lines, from the last character of the line above to the first character of the line below
    static long[] identitySpan(Unit u, DiffParser.Hunk h) {
        int f = first(h), l = last(h), n = u.ls.length;
        long a = f - 1 >= 1 && f - 1 <= n ? Math.max(u.ls[f - 2], u.le[f - 2] - 1) : 0;
        long b = l + 1 <= n && l + 1 >= 1 ? u.ls[l] + 1 : u.len();
        long lo = a, hi = b;
        if (h.oldCount > 0 && f >= 1 && l <= n) {
            lo = Math.min(lo, u.ls[f - 1]);
            hi = Math.max(hi, u.le[l - 1]);
        }
        return new long[] {Math.max(0, Math.min(lo, hi - 1)), Math.min(u.len(), Math.max(hi, lo + 1))};
    }

    private static long start(Unit u, SourceMapV3.Seg s) { return u.ls[s.genLine] + s.genCol; }

    private static long end(Unit u, SourceMapV3.Seg s) {
        long e = s.endCol >= 0 ? u.ls[s.genLine] + s.endCol : u.le[s.genLine];
        return Math.max(e, start(u, s) + 1);
    }

    // ------------------------------------------------------------------ tests

    private static Verdict judge(JsonNode t, List<Attr> attrs, List<Unit> units) {
        List<String> why = new ArrayList<>();
        String cov = t.path("coverage").asText("missing");
        if (!cov.equals("ok")) return new Verdict(true, Collections.singletonList("its coverage is '" + cov + "', so its script list is incomplete"));
        String outcome = t.path("outcome").asText("unknown");
        if (!outcome.equals("passed")) {
            return new Verdict(true, Collections.singletonList("it " + (outcome.equals("failed") ? "failed" : "has no recorded outcome")
                    + " in the coverage run, so it may not have reached all of its code"));
        }
        JsonNode scripts = t.path("scripts");
        for (Attr a : attrs) {
            JsonNode ts = scripts.path(a.u.url);
            if (ts.isMissingNode()) continue;
            if (ts.path("mixedBodies").asBoolean(false)) { why.add(a.u.url + " has several bodies in this test"); continue; }
            if (ts.path("fileLevel").asBoolean(false)) {
                why.add(a.file + " maps to " + a.u.url + ", which this test maps at file level (" + ts.path("fileLevelReason").asText("") + ")");
                continue;
            }
            if (ts.path("len").asLong(-1) != a.u.len()) {
                why.add(a.file + " maps to " + a.u.url + ", whose length in this test's coverage differs from the captured body");
                continue;
            }
            if (a.fileLevel) { why.add(a.file + " maps to " + a.u.url + " (loaded by this test) at file level: " + a.why); continue; }
            List<long[]> covered = pairs(ts.path("ranges")), zeros = pairs(ts.path("uncovered"));
            for (int i = 0; i < a.iv.size(); i++) {
                long[] iv = a.iv.get(i);
                if (overlaps(covered, iv)) why.add(a.where.get(i) + " is in code this test ran (" + a.u.url + ")");
                else if (!strictlyInside(zeros, iv)) why.add(a.where.get(i) + " is next to code this test ran, not provably outside it (" + a.u.url + ")");
            }
        }
        if (!attrs.isEmpty()) {
            for (Unit u : units) {
                if (u.opaque != null && !scripts.path(u.url).isMissingNode()) {
                    why.add("loaded " + u.url + ", which is opaque (" + u.opaque + "): a change can't be proven outside it");
                }
            }
        }
        if (!why.isEmpty()) return new Verdict(true, cap(why));
        return new Verdict(false, Collections.singletonList(attrs.isEmpty() ? "only test scripts changed, and not this one"
                : "the change is outside the code this test ran"));
    }

    static boolean overlaps(List<long[]> ranges, long[] iv) {
        for (long[] r : ranges) if (r[0] < iv[1] && iv[0] < r[1]) return true;
        return false;
    }

    static boolean strictlyInside(List<long[]> zeros, long[] iv) {
        for (long[] z : zeros) if (z[0] < iv[0] && iv[1] < z[1]) return true;
        return false;
    }

    private static List<long[]> pairs(JsonNode a) {
        List<long[]> out = new ArrayList<>();
        for (JsonNode p : a) if (p.isArray() && p.size() == 2) out.add(new long[] {p.get(0).asLong(), p.get(1).asLong()});
        return out;
    }

    private static List<String> cap(List<String> why) {
        List<String> d = new ArrayList<>(new LinkedHashSet<>(why));
        if (d.size() <= MAX_REASONS) return d;
        List<String> out = new ArrayList<>(d.subList(0, MAX_REASONS));
        out.add("… and " + (d.size() - MAX_REASONS) + " more");
        return out;
    }

    // ------------------------------------------------------------------ scripts of the suite

    /** One configured UI script: as configured, plus where it is (absolute, relative to the test dir, in the repo). */
    public static final class Script {
        public final String configured, abs, rel, repoPath;
        public Script(String configured, String abs, String rel, String repoPath) {
            this.configured = configured;
            this.abs = slash(abs);
            this.rel = slash(rel);
            this.repoPath = slash(repoPath);
        }
    }

    public static final class ScriptVerdict {
        public final Script script;
        public final boolean selected;
        public final List<String> reasons;
        public final List<String> ids;
        ScriptVerdict(Script s, boolean selected, List<String> reasons, List<String> ids) {
            this.script = s;
            this.selected = selected;
            this.reasons = reasons;
            this.ids = ids;
        }
    }

    /**
     * Per configured script. Index test ids (the selFile of the coverage run, often an absolute path from another
     * machine) match a script exactly, else by the longest relative-path suffix; a script that matches no id is not in
     * the index and is selected; one that matches several is selected when any of them is.
     */
    public static List<ScriptVerdict> forScripts(Result r, List<Script> scripts) {
        List<ScriptVerdict> out = new ArrayList<>();
        if (r.runsAll()) {
            for (Script s : scripts) out.add(new ScriptVerdict(s, true, cap(r.runAll), Collections.<String>emptyList()));
            return out;
        }
        if (r.nothingChanged) {
            for (Script s : scripts) out.add(new ScriptVerdict(s, false, Collections.singletonList("no file changed in the range"), Collections.<String>emptyList()));
            return out;
        }
        Map<Script, List<String>> ids = new LinkedHashMap<>();
        for (Script s : scripts) ids.put(s, new ArrayList<>());
        // suffix matches (ids from another machine): each implies the coverage run's test dir (the id minus "/<rel>");
        // only matches implying the one most common test dir count, so a script never inherits a different file's id
        Map<String, List<Object[]>> byPrefix = new LinkedHashMap<>();
        for (String id : r.tests.keySet()) {
            String nid = slash(id);
            List<Script> exact = new ArrayList<>();
            for (Script s : scripts) if (nid.equals(s.abs) || nid.equals(s.rel)) exact.add(s);
            if (!exact.isEmpty()) {
                for (Script s : exact) ids.get(s).add(id);
                continue;
            }
            int best = 0;
            for (Script s : scripts) {
                if (s.rel == null || s.rel.isEmpty() || !nid.endsWith("/" + s.rel)) continue;
                if (s.rel.length() > best) { best = s.rel.length(); exact.clear(); }
                if (s.rel.length() == best) exact.add(s);
            }
            for (Script s : exact) {
                byPrefix.computeIfAbsent(nid.substring(0, nid.length() - s.rel.length() - 1), k -> new ArrayList<>()).add(new Object[] {s, id});
            }
        }
        String top = null;
        int topN = 0;
        boolean tie = false;
        for (Map.Entry<String, List<Object[]>> e : byPrefix.entrySet()) {
            int n = e.getValue().size();
            if (n > topN) { top = e.getKey(); topN = n; tie = false; }
            else if (n == topN) tie = true;
        }
        // a script that also matched an id outside that test dir is never deselected (that id may be the one that selects it)
        Map<Script, String> outside = new LinkedHashMap<>();
        for (Map.Entry<String, List<Object[]>> e : byPrefix.entrySet()) {
            boolean keep = !tie && e.getKey().equals(top);
            for (Object[] m : e.getValue()) {
                if (keep) ids.get((Script) m[0]).add((String) m[1]);
                else outside.putIfAbsent((Script) m[0], (String) m[1]);
            }
        }
        for (Script s : scripts) {
            List<String> mine = ids.get(s);
            if (s.repoPath != null && r.changedScripts.contains(s.repoPath)) {
                out.add(new ScriptVerdict(s, true, Collections.singletonList("the script itself changed"), mine));
                continue;
            }
            if (outside.containsKey(s)) {
                out.add(new ScriptVerdict(s, true, Collections.singletonList("matched an index id outside the coverage run's test dir ("
                        + outside.get(s) + "), so its verdict can't be trusted"), mine));
                continue;
            }
            if (mine.isEmpty()) {
                out.add(new ScriptVerdict(s, true, Collections.singletonList("not in the coverage index (no trace of it in the coverage run)"), mine));
                continue;
            }
            List<String> sel = new ArrayList<>(), not = new ArrayList<>();
            for (String id : mine) {
                Verdict v = r.tests.get(id);
                if (v.selected) sel.addAll(v.reasons);
                else not.addAll(v.reasons);
            }
            out.add(sel.isEmpty() ? new ScriptVerdict(s, false, cap(not), mine) : new ScriptVerdict(s, true, cap(sel), mine));
        }
        return out;
    }

    // ------------------------------------------------------------------ helpers

    static boolean isCss(String path) {
        String p = path.toLowerCase(Locale.ROOT);
        int d = p.lastIndexOf('.');
        return d >= 0 && CSS_EXT.contains(p.substring(d));
    }

    static String norm(String s) {
        String t = s.startsWith("﻿") ? s.substring(1) : s;
        return t.replace("\r\n", "\n");
    }

    static boolean oddLines(String s) {
        return s.replace("\r\n", "\n").indexOf('\r') >= 0 || s.indexOf('\u2028') >= 0 || s.indexOf('\u2029') >= 0;
    }

    static String basename(String p) {
        String s = p;
        int q = s.indexOf('?');
        if (q >= 0) s = s.substring(0, q);
        int h = s.indexOf('#');
        if (h >= 0) s = s.substring(0, h);
        int i = s.lastIndexOf('/');
        return i >= 0 ? s.substring(i + 1) : s;
    }

    static String sha1(byte[] b) {
        try {
            StringBuilder hex = new StringBuilder();
            for (byte x : MessageDigest.getInstance("SHA-1").digest(b)) hex.append(String.format("%02x", x));
            return hex.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static List<String> texts(JsonNode a) {
        List<String> out = new ArrayList<>();
        for (JsonNode x : a) if (x.isTextual()) out.add(x.asText());
        return out;
    }

    private static String slash(String p) { return p == null ? null : p.replace('\\', '/'); }

    private static String shortSha(String s) { return s == null ? "?" : s.length() > 10 ? s.substring(0, 10) : s; }
}
