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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Δ145d (security, pre-existing since the distributed transport): the coordinator used to extract a node's results zip
 * with zip4j {@code extractAll} into its out dir, so an authenticated node could write any file there (or, through a
 * crafted entry name, outside it). This guard extracts only what a node legitimately sends:
 * <ul>
 * <li>API runs ({@code distributeTests}): top-level {@code *.html} / {@code *.csv} report files (the node zips exactly
 *     those, {@code ReportHandler.zipDirectory(.., {".html",".csv"}, .., false, true)});</li>
 * <li>Selenium runs ({@code distributeSeleniumTests}): top-level files named {@code DRun-<index>…} for the index the
 *     coordinator gave that node (the node zips its {@code DRun-<index>} prefix, non-recursively).</li>
 * </ul>
 * Every entry must be a plain top-level file name: no directory part, no {@code ..}, no absolute or drive path, no
 * control or reserved characters, no directory or symlink entry, no duplicate. The received zip and the extracted
 * bytes are size-capped ({@code -Dgatf.distributed.resultsMaxBytes} / {@code GATF_DISTRIBUTED_RESULTS_MAX_BYTES},
 * default 1 GiB) and the entry count is capped. Each file is written to a temp file in the out dir and then renamed
 * over the target, so a pre-existing symlink at the target is replaced, never followed. Rejected entries are skipped
 * with one log line each (name only, clipped); the accepted rest is extracted as before.
 * <p>JDK only (no zip4j, no log4j): the caller passes its logger as a {@code Consumer<String>}.
 */
public final class ResultsZipGuard {

    public static final long DEFAULT_MAX_BYTES = 1L << 30;
    public static final int MAX_ENTRIES = 10000;
    /** Rejection lines logged per zip; the rest are counted in the summary line. */
    static final int MAX_LOGGED = 20;

    private static final Pattern RESERVED = Pattern.compile("[\\p{Cntrl}<>:\"|?*\\\\/]");

    private ResultsZipGuard() {}

    /** What a node may send back: a label for the log and the file-name pattern. */
    public static final class Policy {
        final String label;
        final Pattern names;
        Policy(String label, Pattern names) {
            this.label = label;
            this.names = names;
        }
        @Override
        public String toString() {
            return label;
        }
    }

    /** API run results: the node's top-level .html / .csv report files. */
    public static Policy apiReports() {
        return new Policy("API report", Pattern.compile(".+\\.(html|csv)"));
    }

    /** Selenium run results: the node's top-level {@code DRun-<index>…} files (not DRun-<index><digit>…). */
    public static Policy seleniumRun(int index) {
        return new Policy("DRun-" + index, Pattern.compile("DRun-" + index + "(?![0-9]).*", Pattern.DOTALL));
    }

    /** Why {@code name} is refused under {@code p}, or null when it is an acceptable top-level file name. */
    public static String reject(Policy p, String name) {
        if (name == null || name.isEmpty()) return "empty name";
        if (name.length() > 255) return "name too long";
        if (name.indexOf('/') >= 0 || name.indexOf('\\') >= 0) return "not a top-level file (path or traversal)";
        if (RESERVED.matcher(name).find()) return "unsafe character in name";
        if (name.startsWith(".") || name.endsWith(".") || name.endsWith(" ")) return "dot or trailing-space name";
        if (!p.names.matcher(name).matches()) return "not an expected " + p.label + " file";
        return null;
    }

    /** The extracted-bytes cap: the system property, then the environment, then {@link #DEFAULT_MAX_BYTES}. */
    public static long maxBytes(Map<String, String> env, Properties props) {
        for (String v : new String[] { props == null ? null : props.getProperty("gatf.distributed.resultsMaxBytes"),
                env == null ? null : env.get("GATF_DISTRIBUTED_RESULTS_MAX_BYTES") }) {
            if (v == null || v.trim().isEmpty()) continue;
            try {
                long n = Long.parseLong(v.trim());
                if (n > 0) return n;
            } catch (NumberFormatException ignore) {
                // not a number: try the next source
            }
        }
        return DEFAULT_MAX_BYTES;
    }

    public static long maxBytes() {
        return maxBytes(System.getenv(), System.getProperties());
    }

    /** What one extraction did. */
    public static final class Result {
        public final List<String> extracted = new ArrayList<String>();
        public final List<String> rejected = new ArrayList<String>();
        /** True when a cap (bytes or entry count) stopped the extraction early, or the zip was unreadable. */
        public boolean stopped;
    }

    /**
     * Receive a node's results zip from {@code in} into {@code zip} (capped) and extract it into {@code outDir} under
     * {@code p}. Never throws for a hostile zip: it logs and returns what was done.
     */
    public static Result receive(InputStream in, File zip, File outDir, Policy p, String node, Consumer<String> log) throws IOException {
        long cap = maxBytes();
        Result r;
        // the zip itself may carry headers beyond the extracted bytes: allow ~1 KiB per entry on top of the cap
        if (!copyCapped(in, zip, cap + (long) MAX_ENTRIES * 1024L)) {
            r = new Result();
            r.stopped = true;
            log.accept("Distributed results from node " + clip(node) + " refused: the results zip exceeds the size cap (" + cap + " bytes)");
            return r;
        }
        r = extract(zip, outDir, p, cap, MAX_ENTRIES);
        int shown = 0;
        for (String why : r.rejected) {
            if (shown++ >= MAX_LOGGED) break;
            log.accept("Distributed results from node " + clip(node) + ": skipped " + why);
        }
        if (!r.rejected.isEmpty() || r.stopped) {
            log.accept("Distributed results from node " + clip(node) + ": " + r.extracted.size() + " file(s) extracted, "
                    + r.rejected.size() + " entr(y/ies) rejected" + (r.stopped ? ", extraction stopped at a cap or an unreadable zip" : ""));
        }
        return r;
    }

    /** Copy at most {@code cap} bytes of {@code in} to {@code to}; false (and {@code to} deleted) when there were more. */
    static boolean copyCapped(InputStream in, File to, long cap) throws IOException {
        byte[] buf = new byte[64 * 1024];
        long total = 0;
        try (OutputStream out = Files.newOutputStream(to.toPath())) {
            int n;
            while ((n = in.read(buf)) >= 0) {
                total += n;
                if (total > cap) break;
                out.write(buf, 0, n);
            }
        }
        if (total > cap) {
            Files.deleteIfExists(to.toPath());
            return false;
        }
        return true;
    }

    /** Extract the allowed entries of {@code zip} into {@code outDir} (see the class comment). */
    public static Result extract(File zip, File outDir, Policy p, long maxBytes, int maxEntries) {
        Result r = new Result();
        Set<String> links;
        try {
            links = symlinkEntries(zip);
        } catch (IOException e) {
            r.stopped = true;
            r.rejected.add("the whole zip (" + clip(e.getMessage()) + ")");
            return r;
        }
        Path root;
        try {
            Files.createDirectories(outDir.toPath());
            root = outDir.toPath().toRealPath();
        } catch (IOException e) {
            r.stopped = true;
            r.rejected.add("the whole zip (out dir unusable: " + clip(e.getMessage()) + ")");
            return r;
        }
        long budget = maxBytes;
        int count = 0;
        Set<String> seen = new HashSet<String>();
        try (ZipFile zf = new ZipFile(zip, StandardCharsets.UTF_8)) {
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                String name = e.getName();
                if (++count > maxEntries) {
                    r.stopped = true;
                    r.rejected.add("entries beyond the first " + maxEntries);
                    break;
                }
                String why = e.isDirectory() ? "a directory entry" : links.contains(name) ? "a symlink entry" : reject(p, name);
                if (why == null && !seen.add(name)) why = "a duplicate entry";
                Path target = why == null ? root.resolve(name).normalize() : null;
                if (why == null && !root.equals(target.getParent())) why = "resolves outside the out dir";
                if (why == null && Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) why = "would replace a directory";
                if (why != null) {
                    r.rejected.add("'" + clip(name) + "': " + why);
                    continue;
                }
                Path tmp = Files.createTempFile(root, ".gatf-results-", ".part");
                long written = 0;
                boolean over = false;
                try {
                    try (InputStream in = zf.getInputStream(e); OutputStream out = Files.newOutputStream(tmp)) {
                        byte[] buf = new byte[64 * 1024];
                        int n;
                        while ((n = in.read(buf)) >= 0) {
                            written += n;
                            if (written > budget) {
                                over = true;
                                break;
                            }
                            out.write(buf, 0, n);
                        }
                    }
                    if (over) {
                        r.stopped = true;
                        r.rejected.add("'" + clip(name) + "': over the size cap (" + maxBytes + " bytes in all)");
                        break;
                    }
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                    budget -= written;
                    r.extracted.add(name);
                } finally {
                    Files.deleteIfExists(tmp);
                }
            }
        } catch (IOException | RuntimeException e) {
            r.stopped = true;
            r.rejected.add("the rest of the zip (" + clip(String.valueOf(e.getMessage())) + ")");
        }
        return r;
    }

    /**
     * Names of the entries the central directory marks as Unix symlinks (made by Unix, mode S_IFLNK). java.util.zip
     * does not expose the external attributes, so the central directory is read here. ZIP64 archives are refused:
     * a node's results zip never needs one.
     */
    static Set<String> symlinkEntries(File zip) throws IOException {
        Set<String> out = new HashSet<String>();
        try (RandomAccessFile f = new RandomAccessFile(zip, "r")) {
            long len = f.length();
            int tail = (int) Math.min(len, 22 + 65535);
            if (tail < 22) throw new IOException("not a zip");
            byte[] t = new byte[tail];
            f.seek(len - tail);
            f.readFully(t);
            int eocd = -1;
            for (int i = tail - 22; i >= 0; i--) {
                if (u32(t, i) == 0x06054b50L) {
                    eocd = i;
                    break;
                }
            }
            if (eocd < 0) throw new IOException("not a zip (no end of central directory)");
            int entries = u16(t, eocd + 10);
            long cdSize = u32(t, eocd + 12), cdOff = u32(t, eocd + 16);
            if (entries == 0xFFFF || cdSize == 0xFFFFFFFFL || cdOff == 0xFFFFFFFFL) throw new IOException("ZIP64 is not accepted");
            if (cdOff + cdSize > len || cdSize > Integer.MAX_VALUE) throw new IOException("bad central directory");
            byte[] cd = new byte[(int) cdSize];
            f.seek(cdOff);
            f.readFully(cd);
            int i = 0;
            while (i + 46 <= cd.length && u32(cd, i) == 0x02014b50L) {
                int host = (cd[i + 5] & 0xFF);
                int nameLen = u16(cd, i + 28), extraLen = u16(cd, i + 30), commentLen = u16(cd, i + 32);
                long ext = u32(cd, i + 38);
                if (i + 46 + nameLen > cd.length) throw new IOException("bad central directory entry");
                String name = new String(cd, i + 46, nameLen, StandardCharsets.UTF_8);
                if (host == 3 && ((ext >>> 16) & 0170000L) == 0120000L) out.add(name);
                i += 46 + nameLen + extraLen + commentLen;
            }
        }
        return out;
    }

    private static int u16(byte[] b, int i) {
        return (b[i] & 0xFF) | (b[i + 1] & 0xFF) << 8;
    }

    private static long u32(byte[] b, int i) {
        return ((long) u16(b, i)) | ((long) u16(b, i + 2)) << 16;
    }

    /** A node-supplied name for a log line: control characters replaced, at most 120 characters. */
    static String clip(String s) {
        if (s == null) return "null";
        String c = s.replaceAll("\\p{Cntrl}", "?");
        return c.length() > 120 ? c.substring(0, 120) + "…" : c;
    }
}
