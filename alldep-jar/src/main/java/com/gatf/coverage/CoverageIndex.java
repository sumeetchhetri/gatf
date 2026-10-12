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

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * H6 (Δ155): builds the coverage index ({@link CoverageMerge}) for one run folder: every {@code *.zip} trace
 * bundle beside the run's report (the same files {@code /api/reports} lists), plus an optional crawl map.
 *
 * <p>One entry point, {@link #build(Path)}. Reading is bounded by {@link CoverageMerge.Limits}; whatever is over a
 * bound, unreadable, not a trace or in an old format is listed under {@code skipped} and the status becomes
 * {@code partial} (or {@code no-data}): this class never throws for bad input. Nothing is written at run end;
 * {@link #buildCached} keeps an optional {@value #CACHE} in the run folder, written atomically and rebuilt when the
 * version, the limits or any input file change.
 *
 * @author Sumeet Chhetri
 */
public final class CoverageIndex {

    public static final String CACHE = "coverage-index.json";
    private static final Pattern MANIFEST = Pattern.compile("snapshots/snapshot-\\d+\\.json");
    private static final long SMALL = 1L << 20;   // metadata.json and snapshot manifests

    private CoverageIndex() {}

    /** Thrown by the bounded reader when an entry or the run's byte budget is exceeded. */
    static final class CapExceeded extends IOException {
        private static final long serialVersionUID = 1L;
        final long cap;
        CapExceeded(long cap) { super("over " + cap + " bytes"); this.cap = cap; }
    }

    /** The full index for a run folder, with the default limits and no crawl map. */
    public static Map<String, Object> build(Path runDir) {
        return build(runDir, null, new CoverageMerge.Limits());
    }

    /** The full index for a run folder and an optional crawl map. Never throws. */
    public static Map<String, Object> build(Path runDir, Path crawlFile, CoverageMerge.Limits limits) {
        CoverageMerge.Limits lim = limits == null ? new CoverageMerge.Limits() : limits;
        CoverageMerge m = new CoverageMerge(lim);
        long[] budget = {lim.maxTotalBytes};
        String run = runDir == null || runDir.getFileName() == null ? "" : runDir.getFileName().toString();
        try {
            List<Path> zips = traceZips(runDir);
            int n = 0;
            for (Path z : zips) {
                String name = z.getFileName().toString();
                if (++n > lim.maxTraces) { m.skip(name, null, "cap: more than " + lim.maxTraces + " trace bundles in the run"); continue; }
                if (budget[0] <= 0) { m.skip(name, null, "cap: the run's read budget of " + lim.maxTotalBytes + " bytes is used up"); continue; }
                readTrace(m, z, name, lim, budget);
            }
        } catch (Exception e) {
            m.skip(run, null, "run folder unreadable (" + e.getClass().getSimpleName() + ")");
        }
        if (crawlFile != null) {
            String src = crawlFile.getFileName() == null ? crawlFile.toString() : crawlFile.getFileName().toString();
            if (!Files.isRegularFile(crawlFile)) {
                m.skip(src, null, "crawl map not found");
            } else {
                try (InputStream in = new Capped(Files.newInputStream(crawlFile), lim.maxCrawlBytes)) {
                    m.crawl(src, in);
                } catch (Exception e) {
                    m.skip(src, null, "crawl map unreadable (" + e.getClass().getSimpleName() + ")");
                }
            }
        }
        return m.build(run, true);
    }

    /** Like {@link #build(Path, Path, CoverageMerge.Limits)}, reusing {@value #CACHE} when nothing changed. */
    public static Map<String, Object> buildCached(Path runDir, Path crawlFile, CoverageMerge.Limits limits) {
        CoverageMerge.Limits lim = limits == null ? new CoverageMerge.Limits() : limits;
        String key = fingerprint(runDir, crawlFile, lim);
        Path cache = runDir == null ? null : runDir.resolve(CACHE);
        if (key != null && cache != null && Files.isRegularFile(cache)) {
            try {
                if (Files.size(cache) < 512L << 20) {
                    JsonNode root = CoverageMerge.OM.readTree(cache.toFile());
                    if (CoverageMerge.FORMAT.equals(root.path("format").asText())
                            && root.path("version").asInt() == CoverageMerge.VERSION
                            && key.equals(root.path("inputs").asText())) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> m = CoverageMerge.OM.convertValue(root, LinkedHashMap.class);
                        return m;
                    }
                }
            } catch (Exception ignored) {
                // stale or broken cache: rebuild below
            }
        }
        Map<String, Object> idx = build(runDir, crawlFile, lim);
        if (key != null) {
            idx.put("inputs", key);
            writeAtomically(cache, idx);
        }
        return idx;
    }

    /** {@code *-coverage.json} crawl maps in a directory, newest first (the SPA crawl's save and the portal crawl's file). */
    public static List<Path> crawlMaps(Path dir) {
        List<Path> out = new ArrayList<>();
        if (dir == null || !Files.isDirectory(dir)) return out;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            for (Path p : ds) {
                String ln = p.getFileName().toString().toLowerCase(Locale.ROOT);
                if (ln.endsWith("-coverage.json") && Files.isRegularFile(p)) out.add(p);
            }
        } catch (IOException ignored) {
            return out;
        }
        out.sort((a, b) -> {
            int c = Long.compare(mtime(b), mtime(a));
            return c != 0 ? c : a.getFileName().toString().compareTo(b.getFileName().toString());
        });
        return out;
    }

    // ------------------------------------------------------------------ reading

    static List<Path> traceZips(Path runDir) throws IOException {
        List<Path> out = new ArrayList<>();
        if (runDir == null || !Files.isDirectory(runDir)) return out;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(runDir)) {
            for (Path p : ds) {
                if (Files.isRegularFile(p) && p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip")) out.add(p);
            }
        }
        out.sort((a, b) -> a.getFileName().toString().compareTo(b.getFileName().toString()));
        return out;
    }

    private static void readTrace(CoverageMerge m, Path z, String name, CoverageMerge.Limits lim, long[] budget) {
        String testId = null;
        try (ZipFile zf = new ZipFile(z.toFile())) {
            if (zf.size() > lim.maxZipEntries) { m.skip(name, null, "cap: more than " + lim.maxZipEntries + " zip entries"); return; }
            ZipEntry meta = zf.getEntry("metadata.json");
            if (meta == null) { m.skip(name, null, "not a GATF trace bundle (no metadata.json)"); return; }
            testId = testId(readSmall(zf, meta, budget), name);
            ZipEntry cov = zf.getEntry("coverage.json");
            m.trace(testId, name, cov != null);
            // network.har first: its script bodies decide which body this trace's coverage is filed under
            ZipEntry har = zf.getEntry("network.har");
            if (har != null) {
                long cap = Math.min(lim.maxEntryBytes, budget[0]);
                if (cap <= 0) {
                    m.skip(name, testId, "cap: the run's read budget of " + lim.maxTotalBytes + " bytes is used up; network.har not read");
                } else if (har.getSize() > cap) {
                    m.skip(name, testId, "cap: network.har is " + har.getSize() + " bytes (limit " + cap + "); page visits from it not read");
                } else {
                    try (InputStream in = new Capped(zf.getInputStream(har), cap, budget)) {
                        readHar(m, testId, name, CoverageMerge.OM.readTree(in));
                    } catch (CapExceeded e) {
                        m.skip(name, testId, "cap: network.har over " + e.cap + " bytes; page visits from it not read");
                    } catch (Exception e) {
                        m.skip(name, testId, "network.har unreadable (" + e.getClass().getSimpleName() + ")");
                    }
                }
            }
            if (cov != null) {
                long cap = Math.min(lim.maxEntryBytes, budget[0]);
                if (cap <= 0) {
                    m.skip(name, testId, "cap: the run's read budget of " + lim.maxTotalBytes + " bytes is used up; coverage.json not read");
                    m.mark(testId, CoverageMerge.Read.CAPPED);
                } else if (cov.getSize() > cap) {
                    m.skip(name, testId, "cap: coverage.json is " + cov.getSize() + " bytes (limit " + cap + ")");
                    m.mark(testId, CoverageMerge.Read.CAPPED);
                } else {
                    try (InputStream in = new Capped(zf.getInputStream(cov), cap, budget)) {
                        m.coverage(testId, name, in);
                    }
                }
            }
            int manifests = 0;
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (!MANIFEST.matcher(e.getName()).matches()) continue;
                if (++manifests > lim.maxManifestsPerTrace) {
                    m.skip(name, testId, "cap: more than " + lim.maxManifestsPerTrace + " snapshot manifests; the rest not read");
                    break;
                }
                JsonNode man = readSmall(zf, e, budget);
                if (man != null) m.visit(testId, man.path("url").asText(null));
            }
        } catch (Exception e) {
            m.skip(name, testId, "unreadable trace bundle (" + e.getClass().getSimpleName() + ")");
            if (testId != null) m.mark(testId, CoverageMerge.Read.MALFORMED);   // its coverage can't be trusted complete
        }
    }

    // pages the test loaded (HTML documents, 2xx/304) and the sha1 of each captured script body
    private static void readHar(CoverageMerge m, String testId, String traceName, JsonNode har) {
        for (JsonNode e : har.path("log").path("entries")) {
            String url = e.path("request").path("url").asText(null);
            int status = e.path("response").path("status").asInt(0);
            JsonNode content = e.path("response").path("content");
            String mime = content.path("mimeType").asText("").toLowerCase(Locale.ROOT);
            boolean ok = (status >= 200 && status < 300) || status == 304;
            if (ok && (mime.startsWith("text/html") || mime.startsWith("application/xhtml"))) m.visit(testId, url);
            String file = content.path("_file").asText("");
            if (mime.contains("javascript") || mime.contains("ecmascript")) {
                int s = file.lastIndexOf('/');
                if (s >= 0) m.body(traceName, url, file.substring(s + 1));
            }
        }
    }

    private static JsonNode readSmall(ZipFile zf, ZipEntry e, long[] budget) {
        try (InputStream in = new Capped(zf.getInputStream(e), Math.min(SMALL, budget[0]), budget)) {
            return CoverageMerge.OM.readTree(in);
        } catch (Exception ex) {
            return null;
        }
    }

    // the test id is the script the trace ran (metadata.json selFile); a trace without one falls back to its file name
    static String testId(JsonNode meta, String zipName) {
        String sel = meta == null ? null : meta.path("selFile").asText(null);
        if (sel != null && !sel.trim().isEmpty()) {
            String s = sel.trim().replace('\\', '/');
            while (s.startsWith("./")) s = s.substring(2);
            return s;
        }
        String n = zipName;
        if (n.toLowerCase(Locale.ROOT).endsWith(".zip")) n = n.substring(0, n.length() - 4);
        if (n.endsWith("-trace")) n = n.substring(0, n.length() - 6);
        return n;
    }

    /** Counts bytes and fails once {@code cap} (or the shared run budget) is passed. */
    static final class Capped extends FilterInputStream {
        private final long cap;
        private final long[] budget;
        private long read;

        Capped(InputStream in, long cap) { this(in, cap, null); }

        Capped(InputStream in, long cap, long[] budget) {
            super(in);
            this.cap = cap;
            this.budget = budget;
        }

        private void count(long n) throws CapExceeded {
            if (n <= 0) return;
            read += n;
            if (budget != null) budget[0] -= n;
            if (read > cap) throw new CapExceeded(cap);
            if (budget != null && budget[0] < 0) throw new CapExceeded(cap);
        }

        @Override public int read() throws IOException {
            int b = super.read();
            if (b >= 0) count(1);
            return b;
        }

        @Override public int read(byte[] b, int off, int len) throws IOException {
            int n = super.read(b, off, len);
            count(n);
            return n;
        }

        @Override public long skip(long n) throws IOException {
            long s = super.skip(n);
            count(s);
            return s;
        }
    }

    // ------------------------------------------------------------------ cache

    static String fingerprint(Path runDir, Path crawlFile, CoverageMerge.Limits lim) {
        try {
            StringBuilder b = new StringBuilder();
            b.append(CoverageMerge.FORMAT).append('/').append(CoverageMerge.VERSION).append('|').append(lim.fingerprint()).append('\n');
            for (Path z : traceZips(runDir)) {
                b.append(z.getFileName()).append('|').append(Files.size(z)).append('|').append(mtime(z)).append('\n');
            }
            if (crawlFile != null) {
                b.append("crawl:").append(crawlFile.getFileName()).append('|');
                if (Files.isRegularFile(crawlFile)) b.append(Files.size(crawlFile)).append('|').append(mtime(crawlFile));
                b.append('\n');
            }
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            StringBuilder hex = new StringBuilder();
            for (byte x : md.digest(b.toString().getBytes(StandardCharsets.UTF_8))) hex.append(String.format("%02x", x));
            return hex.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static void writeAtomically(Path target, Map<String, Object> idx) {
        if (target == null) return;
        Path tmp = null;
        try {
            tmp = Files.createTempFile(target.getParent(), CACHE + ".", ".tmp");
            Files.write(tmp, CoverageMerge.OM.writeValueAsBytes(idx));
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            tmp = null;
        } catch (Exception ignored) {
            // a read-only out dir just means no cache
        } finally {
            if (tmp != null) try { Files.deleteIfExists(tmp); } catch (IOException ignored) {}
        }
    }

    private static long mtime(Path p) {
        try { return Files.getLastModifiedTime(p).toMillis(); } catch (IOException e) { return 0; }
    }
}
