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
import java.io.PrintStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * M7: {@code gatf merge-reports <dir…> [-o out]}: merges the out dirs of a sharded run ({@code gatf test --shard i/n}
 * on several machines) into one report:
 * <ul>
 *  <li>{@code gatf-results.json}: every test (tagged with its shard), recomputed summary, flaky and failure lists.
 *      A test that ran in several shards (an API prerequisite) is kept once, with its worst status.</li>
 *  <li>{@code gatf-junit.xml}: one {@code <testsuites>}; same-named suites are joined, totals recomputed.</li>
 *  <li>Each input dir is copied to {@code shard-<i>-of-<n>/} (reports, {@code .data.json} sidecars, traces, screenshots), and
 *      attachment paths (JSON and JUnit {@code [[ATTACHMENT|…]]}) are rewritten to those copies.</li>
 *  <li>{@code index.data.json} ({@code type: merged-index}) + an {@code index.html} redirect stub: the merged report
 *      index for the console's report viewer, linking every shard's own report sidecars.</li>
 * </ul>
 * Pure file-in/file-out; Jackson + the JDK XML APIs only.
 */
public final class ReportMerger {

    public static final String USAGE = "gatf merge-reports <dir> <dir>... [-o out-dir]   (default out-dir: merged-report)";

    private static final ObjectMapper OM = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private ReportMerger() {}

    /** CLI entry: returns the exit code (0 ok, 1 error). */
    public static int run(String[] args, File cwd, PrintStream out, PrintStream err) {
        List<File> dirs = new ArrayList<File>();
        File dest = null;
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a.equals("-o") || a.equals("--out")) {
                if (i + 1 >= args.length) { err.println("gatf merge-reports: " + a + " needs a value\n" + USAGE); return 1; }
                dest = resolve(cwd, args[++i]);
            } else if (a.startsWith("--out=")) {
                dest = resolve(cwd, a.substring(6));
            } else if (a.startsWith("-")) {
                err.println("gatf merge-reports: unknown option " + a + "\n" + USAGE);
                return 1;
            } else {
                dirs.add(resolve(cwd, a));
            }
        }
        if (dirs.isEmpty()) { err.println("gatf merge-reports: no report dirs given\n" + USAGE); return 1; }
        if (dest == null) dest = new File(cwd, "merged-report");
        try {
            Summary s = merge(dirs, dest);
            out.println("Merged " + dirs.size() + " report dir(s) into " + dest.getAbsolutePath());
            out.println("  " + s.total + " tests: " + s.passed + " passed, " + s.failed + " failed, " + s.flaky + " flaky, " + s.skipped + " skipped");
            out.println("  JUnit:  " + new File(dest, "gatf-junit.xml").getAbsolutePath());
            out.println("  JSON:   " + new File(dest, "gatf-results.json").getAbsolutePath());
            out.println("  Report: " + new File(dest, "index.html").getAbsolutePath());
            for (String w : s.warnings) out.println("  warning: " + w);
            return s.failed > 0 || s.junitFailures > 0 ? 1 : 0;
        } catch (IllegalArgumentException | IOException e) {
            err.println("gatf merge-reports: " + e.getMessage());
            return 1;
        }
    }

    private static File resolve(File cwd, String p) {
        File f = new File(p);
        return f.isAbsolute() ? f : new File(cwd, p);
    }

    /** Merged totals. */
    public static final class Summary {
        public int total, passed, failed, flaky, skipped, soft;
        public long durationMs, workers;
        /** Failures in the merged JUnit XML (covers shards that wrote JUnit only). */
        public int junitFailures;
        public final List<String> warnings = new ArrayList<String>();
    }

    /** Merges {@code dirs} (each a run's out dir) into {@code dest}. */
    public static Summary merge(List<File> dirs, File dest) throws IOException {
        for (File d : dirs) {
            if (!new File(d, "gatf-results.json").isFile() && !new File(d, "gatf-junit.xml").isFile()) {
                throw new IllegalArgumentException(d + " is not a GATF out dir (no gatf-results.json / gatf-junit.xml)");
            }
        }
        File destCanon = dest.getCanonicalFile();
        for (File d : dirs) {
            if (destCanon.toPath().startsWith(d.getCanonicalFile().toPath())) {
                throw new IllegalArgumentException("the output dir must not be inside an input dir (" + d + ")");
            }
        }
        Files.createDirectories(dest.toPath());
        clearPreviousMerge(dest);

        // shard names: shard-<i> from the recorded "i/n", else the dir name; unique
        List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();
        List<String> names = new ArrayList<String>();
        Set<String> used = new LinkedHashSet<String>();
        for (File d : dirs) {
            Map<String, Object> r = readJson(new File(d, "gatf-results.json"));
            results.add(r);
            Object shard = r == null ? null : sub(r, "summary").get("shard");
            String base = shard != null ? "shard-" + String.valueOf(shard).replaceAll("[^0-9]+", "-of-").replaceAll("-of-$", "")
                    : "shard-" + d.getName().replaceAll("[^A-Za-z0-9._-]", "_");
            String n = base;
            for (int k = 2; !used.add(n); k++) n = base + "-" + k;
            names.add(n);
        }

        // 1) copy each input tree
        for (int i = 0; i < dirs.size(); i++) copyTree(dirs.get(i).toPath(), new File(dest, names.get(i)).toPath());

        // 2) gatf-results.json
        Map<String, Map<String, Object>> tests = new LinkedHashMap<String, Map<String, Object>>();
        Summary s = new Summary();
        for (int i = 0; i < dirs.size(); i++) {
            if (results.get(i) == null) s.warnings.add(dirs.get(i) + " has no gatf-results.json (run with --reporter junit,json): its tests are in the JUnit XML only");
        }
        List<Map<String, Object>> shardRows = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < dirs.size(); i++) {
            Map<String, Object> r = results.get(i);
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("name", names.get(i));
            row.put("source", dirs.get(i).getAbsolutePath());
            row.put("summary", r == null ? null : r.get("summary"));
            row.put("reports", sidecars(new File(dest, names.get(i)), dest));
            shardRows.add(row);
            if (r == null) continue;
            Map<String, Object> sum = sub(r, "summary");
            s.durationMs = Math.max(s.durationMs, num(sum.get("durationMs")));   // shards run side by side: wall time
            s.workers += Math.max(1, num(sum.get("workers")));
            Object tl = r.get("tests");
            if (!(tl instanceof List)) continue;
            for (Object o : (List<?>) tl) {
                if (!(o instanceof Map)) continue;
                @SuppressWarnings("unchecked")
                Map<String, Object> t = new LinkedHashMap<String, Object>((Map<String, Object>) o);
                t.put("shard", names.get(i));
                t.put("attachments", remap(t.get("attachments"), dirs.get(i), names.get(i)));
                String key = t.get("suite") + "\u0000" + t.get("title");
                Map<String, Object> prev = tests.get(key);
                if (prev == null || severity(t.get("status")) > severity(prev.get("status"))) tests.put(key, t);
            }
        }
        List<String> flaky = new ArrayList<String>();
        List<Map<String, Object>> failures = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> t : tests.values()) {
            String st = String.valueOf(t.get("status"));
            s.soft += (int) num(t.get("softFailures"));
            switch (st) {
                case "passed": s.passed++; break;
                case "failed":
                    s.failed++;
                    Map<String, Object> f = new LinkedHashMap<String, Object>();
                    f.put("title", t.get("title"));
                    f.put("error", t.get("error"));
                    f.put("shard", t.get("shard"));
                    failures.add(f);
                    break;
                case "flaky": s.flaky++; flaky.add(String.valueOf(t.get("title"))); break;
                default: s.skipped++;
            }
        }
        s.total = tests.size();
        Map<String, Object> summary = new LinkedHashMap<String, Object>();
        summary.put("total", s.total);
        summary.put("passed", s.passed);
        summary.put("failed", s.failed);
        summary.put("flaky", s.flaky);
        summary.put("skipped", s.skipped);
        summary.put("softFailures", s.soft);
        summary.put("durationMs", s.durationMs);
        summary.put("workers", s.workers);
        summary.put("shard", null);
        summary.put("shards", dirs.size());
        Map<String, Object> merged = new LinkedHashMap<String, Object>();
        merged.put("summary", summary);
        merged.put("tests", new ArrayList<Map<String, Object>>(tests.values()));
        OM.writeValue(new File(dest, "gatf-results.json"), merged);

        // 3) gatf-junit.xml
        List<File> junits = new ArrayList<File>();
        List<String> junitShards = new ArrayList<String>();
        for (int i = 0; i < dirs.size(); i++) {
            File j = new File(dirs.get(i), "gatf-junit.xml");
            if (j.isFile()) { junits.add(j); junitShards.add(names.get(i)); }
        }
        if (!junits.isEmpty()) {
            String xml = mergeJunit(junits, junitShards);
            Files.write(new File(dest, "gatf-junit.xml").toPath(), xml.getBytes(StandardCharsets.UTF_8));
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("<testsuites[^>]*\\sfailures=\"(\\d+)\"").matcher(xml);
            if (m.find()) s.junitFailures = Integer.parseInt(m.group(1));
        }

        // 4) merged index sidecar + redirect stub
        Map<String, Object> idx = new LinkedHashMap<String, Object>();
        idx.put("type", "merged-index");
        idx.put("generated", System.currentTimeMillis());
        idx.put("summary", summary);
        idx.put("results", "gatf-results.json");
        idx.put("shards", shardRows);
        idx.put("flaky", flaky);
        idx.put("failures", failures);
        OM.writeValue(new File(dest, "index.data.json"), idx);
        Files.write(new File(dest, "index.html").toPath(), REDIRECT_STUB.getBytes(StandardCharsets.UTF_8));
        return s;
    }

    /** failed > flaky > passed > skipped: the status a de-duplicated test keeps. */
    static int severity(Object status) {
        switch (String.valueOf(status)) {
            case "failed": return 3;
            case "flaky": return 2;
            case "passed": return 1;
            default: return 0;
        }
    }

    /**
     * Rewrites an attachment path from the shard machine (absolute there) to the copy under {@code shard}: the
     * longest trailing part of the path that exists under the input dir wins; unmatched paths are kept as given.
     */
    static Object remap(Object attachments, File inputDir, String shard) {
        if (!(attachments instanceof List)) return attachments;
        List<Object> out = new ArrayList<Object>();
        for (Object a : (List<?>) attachments) {
            String p = String.valueOf(a).replace('\\', '/');
            String[] seg = p.split("/");
            String hit = null;
            for (int k = 0; k < seg.length && hit == null; k++) {
                String suffix = String.join("/", Arrays.copyOfRange(seg, k, seg.length));
                if (!suffix.isEmpty() && new File(inputDir, suffix).isFile()) hit = suffix;
            }
            out.add(hit != null ? shard + "/" + hit : a);
        }
        return out;
    }

    /**
     * The primary report sidecar of every folder under {@code dir} (as the console's report list picks them: an
     * {@code *index*.data.json} that is not a per-run {@code LRun-*} summary, else the first sidecar), as
     * {label, dataUrl} with dataUrl relative to {@code root}.
     */
    static List<Map<String, Object>> sidecars(File dir, File root) throws IOException {
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        collect(dir, root, out, 0);
        return out;
    }

    private static void collect(File dir, File root, List<Map<String, Object>> out, int depth) throws IOException {
        File[] files = dir.listFiles();
        if (files == null) return;
        Arrays.sort(files);
        File suiteIndex = null, any = null;
        for (File f : files) {
            String ln = f.getName().toLowerCase(Locale.ROOT);
            if (f.isFile() && ln.endsWith(".data.json")) {
                if (any == null) any = f;
                if (ln.contains("index") && !ln.startsWith("lrun") && suiteIndex == null) suiteIndex = f;
            }
        }
        File primary = suiteIndex != null ? suiteIndex : any;
        if (primary != null) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            String rel = root.toPath().relativize(primary.toPath()).toString().replace('\\', '/');
            int slash = rel.lastIndexOf('/');
            m.put("label", slash > 0 ? rel.substring(0, slash) : rel);
            m.put("dataUrl", rel);
            m.put("reportType", sidecarType(primary));
            out.add(m);
        }
        if (depth < 4) {
            for (File f : files) if (f.isDirectory()) collect(f, root, out, depth + 1);
        }
    }

    private static String sidecarType(File f) {
        try {
            Map<String, Object> m = readJson(f);
            return m == null || m.get("type") == null ? null : String.valueOf(m.get("type"));
        } catch (IOException e) {
            return null;
        }
    }

    // ---- JUnit ---------------------------------------------------------------------------------------------------

    /** Merges without attachment rewriting (tests). */
    static String mergeJunit(List<File> files) throws IOException {
        return mergeJunit(files, null);
    }

    /**
     * Merges the JUnit files; with {@code shardNames} (parallel to {@code files}) the {@code [[ATTACHMENT|path]]} lines
     * in {@code system-out} are rewritten to the shard copies, as in the JSON.
     */
    static String mergeJunit(List<File> files, List<String> shardNames) throws IOException {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setExpandEntityReferences(false);
            Document outDoc = f.newDocumentBuilder().newDocument();
            Element root = outDoc.createElement("testsuites");
            root.setAttribute("name", "gatf");
            outDoc.appendChild(root);
            Map<String, Element> suites = new LinkedHashMap<String, Element>();
            Map<String, Element> cases = new LinkedHashMap<String, Element>();
            double wall = 0;
            for (int fi = 0; fi < files.size(); fi++) {
                File file = files.get(fi);
                Document d = f.newDocumentBuilder().parse(file);
                if (shardNames != null) remapJunitAttachments(d, file.getParentFile(), shardNames.get(fi));
                Element top = d.getDocumentElement();
                wall = Math.max(wall, dbl(top.getAttribute("time")));
                NodeList ss = d.getElementsByTagName("testsuite");
                for (int i = 0; i < ss.getLength(); i++) {
                    Element src = (Element) ss.item(i);
                    String name = src.getAttribute("name");
                    Element dst = suites.get(name);
                    if (dst == null) {
                        dst = outDoc.createElement("testsuite");
                        dst.setAttribute("name", name);
                        suites.put(name, dst);
                        root.appendChild(dst);
                    }
                    for (Node c = src.getFirstChild(); c != null; c = c.getNextSibling()) {
                        if (!(c instanceof Element) || !"testcase".equals(c.getNodeName())) continue;
                        Element tc = (Element) outDoc.importNode(c, true);
                        String key = name + "\u0000" + tc.getAttribute("classname") + "\u0000" + tc.getAttribute("name");
                        Element prev = cases.get(key);
                        if (prev == null) {
                            cases.put(key, tc);
                            dst.appendChild(tc);
                        } else if (junitSeverity(tc) > junitSeverity(prev)) {
                            prev.getParentNode().replaceChild(tc, prev);
                            cases.put(key, tc);
                        }
                    }
                }
            }
            int tests = 0, failures = 0, skipped = 0;
            for (Element s : suites.values()) {
                int t = 0, fl = 0, sk = 0;
                double time = 0;
                for (Node c = s.getFirstChild(); c != null; c = c.getNextSibling()) {
                    if (!(c instanceof Element)) continue;
                    Element tc = (Element) c;
                    t++;
                    time += dbl(tc.getAttribute("time"));
                    int sev = junitSeverity(tc);
                    if (sev == 3) fl++;
                    else if (sev == 0) sk++;
                }
                s.setAttribute("tests", String.valueOf(t));
                s.setAttribute("failures", String.valueOf(fl));
                s.setAttribute("skipped", String.valueOf(sk));
                s.setAttribute("time", String.format(Locale.ROOT, "%.3f", time));
                tests += t;
                failures += fl;
                skipped += sk;
            }
            root.setAttribute("tests", String.valueOf(tests));
            root.setAttribute("failures", String.valueOf(failures));
            root.setAttribute("skipped", String.valueOf(skipped));
            root.setAttribute("time", String.format(Locale.ROOT, "%.3f", wall));
            javax.xml.transform.Transformer tr = TransformerFactory.newInstance().newTransformer();
            tr.setOutputProperty(OutputKeys.INDENT, "yes");
            tr.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            StringWriter w = new StringWriter();
            tr.transform(new DOMSource(outDoc), new StreamResult(w));
            return w.toString();
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("JUnit XML merge failed: " + e.getMessage(), e);
        }
    }

    private static void remapJunitAttachments(Document d, File inputDir, String shard) {
        NodeList outs = d.getElementsByTagName("system-out");
        java.util.regex.Pattern att = java.util.regex.Pattern.compile("\\[\\[ATTACHMENT\\|([^\\]]*)\\]\\]");
        for (int i = 0; i < outs.getLength(); i++) {
            Node n = outs.item(i);
            String text = n.getTextContent();
            if (text == null || !text.contains("[[ATTACHMENT|")) continue;
            java.util.regex.Matcher m = att.matcher(text);
            StringBuffer b = new StringBuffer();
            while (m.find()) {
                Object r = remap(java.util.Collections.singletonList(m.group(1)), inputDir, shard);
                String p = String.valueOf(((List<?>) r).get(0));
                m.appendReplacement(b, java.util.regex.Matcher.quoteReplacement("[[ATTACHMENT|" + p + "]]"));
            }
            m.appendTail(b);
            n.setTextContent(b.toString());
        }
    }

    /** Removes what an earlier merge into {@code dest} wrote, so stale shards or a stale JUnit file never linger. */
    private static void clearPreviousMerge(File dest) throws IOException {
        File[] old = dest.listFiles();
        if (old == null) return;
        for (File f : old) {
            String n = f.getName();
            if (f.isDirectory() && n.startsWith("shard-")) deleteTree(f.toPath());
            else if (f.isFile() && (n.equals("gatf-junit.xml") || n.equals("gatf-results.json") || n.equals("index.data.json") || n.equals("index.html"))) Files.delete(f.toPath());
        }
    }

    private static void deleteTree(Path root) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException e) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /** failure/error 3, flakyFailure 2, pass 1, skipped 0. */
    private static int junitSeverity(Element tc) {
        if (tc.getElementsByTagName("failure").getLength() > 0 || tc.getElementsByTagName("error").getLength() > 0) return 3;
        if (tc.getElementsByTagName("skipped").getLength() > 0) return 0;
        if (tc.getElementsByTagName("flakyFailure").getLength() > 0) return 2;
        return 1;
    }

    // ---- helpers -------------------------------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    static Map<String, Object> readJson(File f) throws IOException {
        if (!f.isFile()) return null;
        return OM.readValue(f, Map.class);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> sub(Map<String, Object> m, String k) {
        Object o = m.get(k);
        return o instanceof Map ? (Map<String, Object>) o : new LinkedHashMap<String, Object>();
    }

    private static long num(Object o) { return o instanceof Number ? ((Number) o).longValue() : 0L; }

    private static double dbl(String s) {
        try { return s == null || s.isEmpty() ? 0 : Double.parseDouble(s); } catch (NumberFormatException e) { return 0; }
    }

    private static void copyTree(Path from, Path to) throws IOException {
        Files.walkFileTree(from, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(to.resolve(from.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, to.resolve(from.relativize(file).toString()), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /** Same redirect stub as the executor's reports: opens {@code index.data.json} in the console's report viewer. */
    static final String REDIRECT_STUB = "<!doctype html><html><head><meta charset=\"utf-8\">"
            + "<meta name=\"robots\" content=\"noindex\"><title>GATF merged report</title>"
            + "<script>(function(){var d=location.pathname.replace(/\\.html$/,'.data.json');"
            + "location.replace('/app/?report='+encodeURIComponent(d));})();</script></head>"
            + "<body style=\"font-family:system-ui;background:#1b1b1f;color:#d7d7db;padding:24px\">"
            + "Opening this report in the GATF viewer… "
            + "<a id=\"gv\" href=\"/app/\" style=\"color:#4ea1ff\">open it here</a> if you are not redirected."
            + "<script>document.getElementById('gv').href='/app/?report='"
            + "+encodeURIComponent(location.pathname.replace(/\\.html$/,'.data.json'));</script>"
            + "</body></html>";
}
