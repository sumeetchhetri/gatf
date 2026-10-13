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
package com.gatf.executor.distributed;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.gatf.log.ReportMerger;

/**
 * H8 (Δ162): distributed functional traces. A worker writes each trace zip locally (as a local run does) and returns
 * a {@link TraceRef} per zip inside its results; the zip bytes are then <b>streamed back over the same authenticated
 * channel</b> (protocol v2, after the status JSON, before the results zip):
 *
 * <pre>
 *   int n                       number of frames (= number of references)
 *   n x { int ref; long len; len bytes }    len -1 = not sent (missing on the worker, or over the size cap)
 * </pre>
 *
 * The coordinator treats every reference and frame as untrusted: references are validated (no traversal, absolute or
 * drive paths, size cap, sha256 format), the bytes are hashed while they stream to a temp file under its report dir,
 * kept only when the size and sha256 match and the content is a zip (never extracted), and stored under a name it
 * builds itself: {@code dist-traces/<run>/<worker>/<script>__<session>__<workerRun>-trace.zip}, collision-free. Every
 * reference ends up in {@code distributed-traces.json} + {@code distributed-traces.html} in the report dir with a
 * status ({@code ok | missing | oversized | checksum | rejected}); only {@code ok} rows carry a link. Nothing here
 * ever fails the run. Pure JDK + Jackson: no Selenium, Kryo or log4j types.
 */
public final class DistributedTraces {

    /** The wire protocol version that carries trace frames ({@link DistributedTransport#PROTOCOL_VERSION} 2). */
    public static final int TRACE_PROTOCOL = 2;
    /** Report-dir manifest (links relative to the report dir). */
    public static final String MANIFEST = ReportMerger.TRACES_MANIFEST;
    /** Report-dir page linking every trace. */
    public static final String PAGE = ReportMerger.TRACES_PAGE;
    /** Report-dir subfolder holding the received zips. */
    public static final String STORE_DIR = "dist-traces";

    public static final String OK = "ok", MISSING = "missing", OVERSIZED = "oversized", CHECKSUM = "checksum", REJECTED = "rejected";

    /** Default per-trace cap: 256 MiB ({@code -Dgatf.dist.trace.maxBytes}). */
    public static final long DEFAULT_MAX_BYTES = 256L * 1024 * 1024;
    /** Default cap on references per worker run ({@code -Dgatf.dist.trace.maxCount}). */
    public static final int DEFAULT_MAX_COUNT = 2000;

    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern SEGMENT = Pattern.compile("[A-Za-z0-9._@+-]{1,128}");
    private static final int MAX_REL_PATH = 512;
    private static final ObjectMapper OM = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final Object PUBLISH_LOCK = new Object();

    private DistributedTraces() {}

    public static long maxBytes() {
        Long v = Long.getLong("gatf.dist.trace.maxBytes");
        return v == null || v < 0 ? DEFAULT_MAX_BYTES : v;
    }

    public static int maxCount() {
        Integer v = Integer.getInteger("gatf.dist.trace.maxCount");
        return v == null || v < 0 ? DEFAULT_MAX_COUNT : v;
    }

    /**
     * Worker: the cap it honours = the smaller of its own and the coordinator's (sent in the test context; negative =
     * not sent). A cap of 0 on either side sends no zips: every trace is listed as oversized.
     */
    public static long effectiveCap(long coordinatorCap) {
        long own = maxBytes();
        return coordinatorCap >= 0 ? Math.min(own, coordinatorCap) : own;
    }

    // ---- worker: collect the traces a run writes ------------------------------------------------------------

    private static final Map<String, Set<Collector>> OPEN = new ConcurrentHashMap<String, Set<Collector>>();

    /** One written trace, as recorded by the executor. */
    static final class Rec {
        final String script, session, runId;
        final Path zip;
        Rec(String script, String session, String runId, Path zip) { this.script = script; this.session = session; this.runId = runId; this.zip = zip; }
    }

    /** Collects the trace zips one distributed run writes on this worker; {@link #close()} stops collecting. */
    public static final class Collector implements AutoCloseable {
        private final String key;
        private final List<Rec> recs = new CopyOnWriteArrayList<Rec>();
        private int dropped;

        private Collector(String key) { this.key = key; }

        /**
         * The references for every recorded zip that is a regular file (not a symlink) inside {@code outRoot}, at
         * most {@code maxCount}. A zip over {@code cap} keeps its size but no sha256 (it is not sent).
         */
        public List<TraceRef> references(File outRoot, String workerId, long cap, int maxCount) {
            List<TraceRef> out = new ArrayList<TraceRef>();
            for (Rec r : recs) {
                if (out.size() >= maxCount) { dropped++; continue; }
                TraceRef ref = describe(outRoot.toPath(), r.zip, workerId, r.runId, r.script, r.session, cap);
                if (ref != null) out.add(ref); else dropped++;
            }
            return out;
        }

        /** Recorded zips left out by {@link #references} (outside the out dir, a symlink, gone, or over the count). */
        public int dropped() { return dropped; }

        int size() { return recs.size(); }

        @Override
        public void close() {
            Set<Collector> s = OPEN.get(key);
            if (s != null) {
                s.remove(this);
                if (s.isEmpty()) OPEN.remove(key, s);
            }
        }
    }

    private static String key(String node, String runPrefix) { return node + "\u0000" + runPrefix; }

    /** Worker: start collecting the traces of the run {@code runPrefix} ({@code DRun-<index>}) sent as {@code node}. */
    public static Collector open(String node, String runPrefix) {
        Collector c = new Collector(key(node, runPrefix));
        OPEN.computeIfAbsent(c.key, k -> ConcurrentHashMap.newKeySet()).add(c);
        return c;
    }

    /**
     * Executor hook (GatfTestCaseExecutorUtil, after a trace zip is written): records it for the open distributed run
     * of {@code node}/{@code runPrefix}. A no-op for local runs (nothing open), and never throws.
     */
    public static void recorded(String node, String runPrefix, String script, String session, String runId, Path zip) {
        try {
            if (zip == null || OPEN.isEmpty()) return;
            Set<Collector> s = OPEN.get(key(node, runPrefix));
            if (s == null) return;
            for (Collector c : s) c.recs.add(new Rec(script, session, runId, zip));
        } catch (RuntimeException e) {
            // tracing never fails a run
        }
    }

    /**
     * Worker: the reference for {@code zip}, or null if it is not a regular file inside {@code outRoot} (symlinks are
     * refused). Over {@code cap} the sha256 is left null: the zip will not be sent.
     */
    public static TraceRef describe(Path outRoot, Path zip, String workerId, String runId, String script, String session, long cap) {
        try {
            if (zip == null || Files.isSymbolicLink(zip) || !Files.isRegularFile(zip, LinkOption.NOFOLLOW_LINKS)) return null;
            Path root = outRoot.toRealPath();
            Path real = zip.toRealPath();
            if (!real.startsWith(root) || real.equals(root)) return null;
            String rel = root.relativize(real).toString().replace(File.separatorChar, '/');
            long size = Files.size(real);
            String sha = size <= cap ? sha256(real) : null;
            return new TraceRef(workerId, runId, script, session, rel, size, sha);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /**
     * Worker: writes the frames for {@code refs} (in order) to {@code out}. Each zip is sent as exactly
     * {@code ref.size} bytes (zero-padded if it shrank, so the stream stays in sync and the checksum fails);
     * a zip over {@code cap}, or one that cannot be opened, is sent as {@code len -1}.
     */
    public static void writeFrames(OutputStream out, Path outRoot, List<TraceRef> refs, long cap) throws IOException {
        DataOutputStream d = new DataOutputStream(out);
        List<TraceRef> list = refs == null ? Collections.<TraceRef>emptyList() : refs;
        d.writeInt(list.size());
        byte[] buf = new byte[64 * 1024];
        for (int i = 0; i < list.size(); i++) {
            TraceRef r = list.get(i);
            d.writeInt(i);
            InputStream in = null;
            try {
                if (r.getSize() <= cap && r.getSha256() != null) {
                    in = Files.newInputStream(outRoot.toRealPath().resolve(r.getRelPath()), LinkOption.NOFOLLOW_LINKS);
                }
            } catch (IOException | RuntimeException e) {
                in = null;
            }
            if (in == null) {
                d.writeLong(-1L);
                continue;
            }
            try {
                d.writeLong(r.getSize());
                long left = r.getSize();
                while (left > 0) {
                    int n = in.read(buf, 0, (int) Math.min(buf.length, left));
                    if (n < 0) break;
                    d.write(buf, 0, n);
                    left -= n;
                }
                java.util.Arrays.fill(buf, (byte) 0);
                while (left > 0) {
                    int n = (int) Math.min(buf.length, left);
                    d.write(buf, 0, n);
                    left -= n;
                }
            } finally {
                in.close();
            }
        }
        d.flush();
    }

    // ---- coordinator: validate, receive, store, publish ------------------------------------------------------

    /** A relative, {@code /}-separated path with plain segments only: no {@code ..}, {@code .}, absolute or drive paths. */
    public static boolean safeRelPath(String p) {
        if (p == null || p.isEmpty() || p.length() > MAX_REL_PATH) return false;
        if (p.startsWith("/") || p.indexOf('\\') >= 0 || p.indexOf(':') >= 0) return false;
        for (String seg : p.split("/", -1)) {
            if (seg.equals(".") || seg.equals("..") || !SEGMENT.matcher(seg).matches()) return false;
        }
        return true;
    }

    /** Why the coordinator refuses {@code ref} ({@link #OVERSIZED} / {@link #REJECTED} + reason), or null if it is acceptable. */
    public static String[] reject(TraceRef ref, long cap) {
        if (ref == null) return new String[] {REJECTED, "empty reference"};
        if (ref.getSize() < 0) return new String[] {REJECTED, "negative size"};
        if (ref.getSize() > cap) return new String[] {OVERSIZED, "size " + ref.getSize() + " > cap " + cap};
        if (!safeRelPath(ref.getRelPath())) return new String[] {REJECTED, "unsafe path"};
        if (!ref.getRelPath().toLowerCase(Locale.ROOT).endsWith(".zip")) return new String[] {REJECTED, "not a .zip"};
        if (ref.getSha256() == null || !SHA256.matcher(ref.getSha256()).matches()) return new String[] {REJECTED, "bad sha256"};
        return null;
    }

    /** A file-name-safe token: {@code [A-Za-z0-9._-]}, at most {@code max} chars, never empty or dot-only. */
    public static String token(String s, int max) {
        String t = s == null ? "" : s.replaceAll("[^A-Za-z0-9._-]", "_");
        if (t.length() > max) t = t.substring(0, max);
        if (t.replace(".", "").isEmpty()) t = "_" + t;
        return t;
    }

    /** Store name for a trace: script + session + worker run (the worker id is the folder). */
    public static String storeName(String script, String session, String workerRun) {
        String sc = script == null ? "script" : script.replace('\\', '/');
        int slash = sc.lastIndexOf('/');
        if (slash >= 0) sc = sc.substring(slash + 1);
        if (sc.toLowerCase(Locale.ROOT).endsWith(".sel")) sc = sc.substring(0, sc.length() - 4);
        StringBuilder b = new StringBuilder(token(sc, 80));
        if (session != null && !session.isEmpty()) b.append("__").append(token(session, 60));
        b.append("__").append(token(workerRun, 60));
        return b.append("-trace.zip").toString();
    }

    /** The coordinator's run id, shared by every worker of one run: derived from that run's class bundle. */
    public static String runIdFor(File testClassesZip) {
        String stem = testClassesZip == null ? UUID.randomUUID().toString() : testClassesZip.getName();
        long ts = testClassesZip != null && testClassesZip.lastModified() > 0 ? testClassesZip.lastModified() : System.currentTimeMillis();
        String id = stem.replaceAll("[^A-Za-z0-9]", "");
        return new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date(ts)) + "-" + (id.length() > 8 ? id.substring(0, 8) : id);
    }

    /** What one worker's trace frames produced. */
    public static final class Received {
        /** One manifest row per reference (and per stray frame), in reference order. */
        public final List<Map<String, Object>> entries = new ArrayList<Map<String, Object>>();
        /** False when the stream could not be followed to the end of the frames (the results zip must not be read). */
        public boolean inSync = true;
        public String error;

        public int count(String status) {
            int n = 0;
            for (Map<String, Object> e : entries) if (status.equals(e.get("status"))) n++;
            return n;
        }

        /** One log line; no paths beyond the report-relative links, no reference content beyond counts. */
        public String summary(String node) {
            return "Distributed traces from node " + node + ": " + count(OK) + " stored, " + count(MISSING) + " missing, "
                    + count(OVERSIZED) + " oversized, " + count(CHECKSUM) + " checksum mismatch, " + count(REJECTED) + " rejected"
                    + (inSync ? "" : " (trace stream aborted: " + error + ")");
        }
    }

    private static Map<String, Object> row(String node, TraceRef ref, String status, String reason) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("worker", node);
        m.put("script", ref == null ? null : clip(ref.getScript()));
        m.put("session", ref == null ? null : clip(ref.getSession()));
        m.put("workerRun", ref == null ? null : clip(ref.getRunId()));
        m.put("status", status);
        m.put("reason", reason);
        m.put("href", null);
        m.put("size", ref == null ? null : ref.getSize());
        m.put("sha256", ref == null || ref.getSha256() == null || !SHA256.matcher(ref.getSha256()).matches() ? null : ref.getSha256());
        return m;
    }

    private static String clip(String s) {
        if (s == null) return null;
        String t = s.replaceAll("[\\p{Cntrl}]", "?");
        return t.length() > 200 ? t.substring(0, 200) : t;
    }

    /**
     * Coordinator: reads one worker's frames from {@code in} and stores the accepted zips under
     * {@code outRoot/dist-traces/<run>/<node>/}. {@code node} is the coordinator's own name for the worker (never the
     * worker-supplied id). Never throws: a broken stream marks the rest {@code missing} and clears
     * {@link Received#inSync}.
     */
    public static Received receive(InputStream in, List<TraceRef> refs, File outRoot, String run, String node, long cap, int maxCount) {
        Received r = new Received();
        List<TraceRef> list = refs == null ? Collections.<TraceRef>emptyList() : refs;
        int keep = Math.min(list.size(), Math.max(maxCount, 0));   // references past the cap get one summary row, not one each
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>(Collections.<Map<String, Object>>nCopies(keep, null));
        int stray = 0;
        DataInputStream d = new DataInputStream(in);
        Path[] dir = null;
        String dirError = null;
        try {
            int n = d.readInt();
            if (n < 0 || n > list.size()) {
                r.inSync = false;
                r.error = "bad frame count " + n;
            }
            for (int k = 0; r.inSync && k < n; k++) {
                int idx = d.readInt();
                long len = d.readLong();
                if (len < -1 || len > cap) {   // a well-behaved worker never sends over the cap it was given
                    r.inSync = false;
                    r.error = "bad frame length";
                    if (idx >= 0 && idx < keep && rows.get(idx) == null) rows.set(idx, row(node, list.get(idx), len > cap ? OVERSIZED : REJECTED, "frame length " + len));
                    break;
                }
                TraceRef ref = idx >= 0 && idx < keep && rows.get(idx) == null ? list.get(idx) : null;
                if (ref == null) {   // unknown, repeated or over the count cap: drained, counted once
                    drain(d, len);
                    stray++;
                    continue;
                }
                String[] why = reject(ref, cap);
                if (why == null && len == -1) why = new String[] {MISSING, "not sent by the worker"};
                if (why == null && len != ref.getSize()) why = new String[] {REJECTED, "length differs from the reference"};
                if (why != null) {
                    drain(d, len);
                    rows.set(idx, row(node, ref, why[0], why[1]));
                    continue;
                }
                if (dir == null && dirError == null) {
                    try {
                        dir = storeDir(outRoot, run, node);
                    } catch (IOException | RuntimeException e) {
                        dirError = "trace store unavailable";
                    }
                }
                if (dir == null) {
                    drain(d, len);
                    rows.set(idx, row(node, ref, MISSING, dirError));
                    continue;
                }
                rows.set(idx, store(d, len, ref, dir, node));
            }
        } catch (EOFException e) {
            r.inSync = false;
            r.error = "stream ended early";
        } catch (IOException | RuntimeException e) {
            r.inSync = false;
            r.error = e.getClass().getSimpleName();
        }
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i) == null) rows.set(i, row(node, list.get(i), MISSING, "no data received"));
        }
        r.entries.addAll(rows);
        int over = list.size() - keep;
        if (over > 0 || stray > 0) {
            Map<String, Object> m = row(node, null, REJECTED, (over > 0 ? over + " reference(s) over the count cap " + maxCount : "")
                    + (over > 0 && stray > 0 ? "; " : "") + (stray > 0 ? stray + " frame(s) for unknown or repeated references" : ""));
            m.put("count", over + stray);
            r.entries.add(m);
        }
        return r;
    }

    private static void drain(DataInputStream d, long len) throws IOException {
        long left = len;
        byte[] buf = new byte[64 * 1024];
        while (left > 0) {
            int n = d.read(buf, 0, (int) Math.min(buf.length, left));
            if (n < 0) throw new EOFException();
            left -= n;
        }
    }

    /** {@code outRoot/dist-traces/<run>/<node>}: created, and refused unless it resolves inside {@code outRoot}. */
    static Path[] storeDir(File outRoot, String run, String node) throws IOException {
        Path root = outRoot.toPath();
        Files.createDirectories(root);
        Path dir = root.resolve(STORE_DIR).resolve(token(run, 64)).resolve(token(node, 96));
        Files.createDirectories(dir);
        Path realRoot = root.toRealPath(), realDir = dir.toRealPath();
        if (!realDir.startsWith(realRoot)) throw new IOException("trace store escapes the report dir");
        return new Path[] {realDir, realRoot};
    }

    /** Streams exactly {@code len} bytes; keeps them only for a matching sha256 + zip magic. Stream errors propagate. */
    private static Map<String, Object> store(DataInputStream d, long len, TraceRef ref, Path[] dirs, String node) throws IOException {
        MessageDigest md = sha();
        Path dir = dirs[0];
        Path tmp = null;
        OutputStream os = null;
        String diskError = null;
        byte[] head = new byte[4];
        int headLen = 0;
        try {
            try {
                tmp = dir.resolve(".recv-" + UUID.randomUUID() + ".part");
                os = Files.newOutputStream(tmp, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            } catch (IOException e) {
                diskError = "cannot write the trace store";
            }
            long left = len;
            byte[] buf = new byte[64 * 1024];
            while (left > 0) {
                int n = d.read(buf, 0, (int) Math.min(buf.length, left));
                if (n < 0) throw new EOFException();
                md.update(buf, 0, n);
                for (int i = 0; i < n && headLen < 4; i++) head[headLen++] = buf[i];
                if (os != null) {
                    try {
                        os.write(buf, 0, n);
                    } catch (IOException e) {
                        diskError = "cannot write the trace store";
                        closeQuietly(os);
                        os = null;
                    }
                }
                left -= n;
            }
            if (os != null) {
                os.close();
                os = null;
            }
            if (diskError != null) return row(node, ref, MISSING, diskError);
            if (!hex(md.digest()).equals(ref.getSha256())) return row(node, ref, CHECKSUM, "sha256 differs from the reference");
            if (!isZipMagic(head, headLen)) return row(node, ref, REJECTED, "content is not a zip");
            String name = storeName(ref.getScript(), ref.getSession(), ref.getRunId());
            Path dest = moveUnique(tmp, dir, name);
            tmp = null;
            Map<String, Object> m = row(node, ref, OK, null);
            m.put("href", dirs[1].relativize(dest).toString().replace(File.separatorChar, '/'));
            return m;
        } finally {
            closeQuietly(os);
            if (tmp != null) {
                try { Files.deleteIfExists(tmp); } catch (IOException e) { /* best effort */ }
            }
        }
    }

    private static Path moveUnique(Path tmp, Path dir, String name) throws IOException {
        String base = name.substring(0, name.length() - "-trace.zip".length());
        for (int k = 1; k < 10000; k++) {
            Path dest = dir.resolve(k == 1 ? name : base + "-" + k + "-trace.zip");
            if (Files.exists(dest, LinkOption.NOFOLLOW_LINKS)) continue;
            try {
                return Files.move(tmp, dest);   // no REPLACE_EXISTING: never overwrites (nor follows) an existing entry
            } catch (FileAlreadyExistsException e) {
                // raced: next suffix
            }
        }
        throw new IOException("no free trace name");
    }

    static boolean isZipMagic(byte[] h, int n) {
        return n >= 4 && h[0] == 'P' && h[1] == 'K' && ((h[2] == 3 && h[3] == 4) || (h[2] == 5 && h[3] == 6));
    }

    /** Rows published so far per report dir + run, kept in memory: the manifest on disk is never read back. */
    private static final Map<String, List<Map<String, Object>>> PUBLISHED = new LinkedHashMap<String, List<Map<String, Object>>>(16, 0.75f, true) {
        private static final long serialVersionUID = 1L;

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, List<Map<String, Object>>> e) {
            return size() > 8;
        }
    };

    /**
     * Coordinator: adds {@code entries} to the report dir's {@link #MANIFEST} for {@code run} and rewrites it and
     * {@link #PAGE}. Rows accumulate in memory per report dir + run (a new run starts a new manifest); the file on disk
     * is only written, never trusted back, since a node's results zip is extracted into the same dir. Serialised
     * across nodes. Returns false on an I/O error.
     */
    public static boolean publish(File outRoot, String run, List<Map<String, Object>> entries) {
        synchronized (PUBLISH_LOCK) {
            try {
                Files.createDirectories(outRoot.toPath());
                String key = outRoot.getAbsoluteFile().toPath().normalize() + "\u0000" + run;
                List<Map<String, Object>> all = PUBLISHED.get(key);
                if (all == null) {
                    all = new ArrayList<Map<String, Object>>();
                    PUBLISHED.put(key, all);
                }
                all.addAll(entries);
                Map<String, Object> m = new LinkedHashMap<String, Object>();
                m.put("type", "distributed-traces");
                m.put("format", 1);
                m.put("run", run);
                m.put("generated", System.currentTimeMillis());
                m.put("traces", all);
                writeAtomic(outRoot.toPath(), MANIFEST, OM.writeValueAsBytes(m));
                writeAtomic(outRoot.toPath(), PAGE, ReportMerger.tracesHtml(all, "GATF distributed traces · run " + run).getBytes(StandardCharsets.UTF_8));
                return true;
            } catch (IOException | RuntimeException e) {
                return false;
            }
        }
    }

    /**
     * Coordinator, after the node's results zip has been extracted into the report dir: every stored zip must still
     * be the regular file it verified, with the same sha256; otherwise its row loses the link
     * ({@link #CHECKSUM}, "changed after it was received").
     */
    public static void reverify(File outRoot, List<Map<String, Object>> entries) {
        for (Map<String, Object> e : entries) {
            Object href = e.get("href");
            if (!OK.equals(e.get("status")) || !(href instanceof String)) continue;
            String why = null;
            try {
                Path p = outRoot.toPath().resolve((String) href);
                if (!safeRelPath((String) href) || !Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)) why = MISSING;
                else if (!sha256(p).equals(e.get("sha256"))) why = CHECKSUM;
            } catch (IOException | RuntimeException x) {
                why = MISSING;
            }
            if (why != null) {
                e.put("status", why);
                e.put("reason", "changed after it was received");
                e.put("href", null);
            }
        }
    }

    private static void writeAtomic(Path dir, String name, byte[] bytes) throws IOException {
        Path tmp = dir.resolve("." + name + "." + UUID.randomUUID() + ".tmp");
        Files.write(tmp, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        try {
            Files.move(tmp, dir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    // ---- helpers --------------------------------------------------------------------------------------------

    static String sha256(Path p) throws IOException {
        MessageDigest md = sha();
        try (InputStream in = Files.newInputStream(p, LinkOption.NOFOLLOW_LINKS)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        }
        return hex(md.digest());
    }

    private static MessageDigest sha() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static String hex(byte[] b) {
        StringBuilder s = new StringBuilder(b.length * 2);
        for (byte x : b) s.append(Character.forDigit((x >> 4) & 0xF, 16)).append(Character.forDigit(x & 0xF, 16));
        return s.toString();
    }

    private static void closeQuietly(OutputStream os) {
        if (os == null) return;
        try { os.close(); } catch (IOException e) { /* ignore */ }
    }
}
