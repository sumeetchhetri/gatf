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
package com.gatf.ui;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Δ163n: test-file paths as the SPA sends them ('/' separated, relative to the test-case dir), checked and mapped to disk.
 * Pure (no Grizzly): {@link GatfTestCaseFilesHandler} uses it for rename / upload / listing.
 * <ul>
 * <li>{@link #normalize} accepts '/' or '\' and returns a '/' path; it refuses blank, absolute ('/x', 'C:\x', '\\host'),
 * and any '..' segment, so a client path can never leave the base directory.</li>
 * <li>{@link #resolve} also checks the canonical target stays under the base (symlinks included).</li>
 * <li>{@link #toClient} turns an OS relative path ('ui\a.sel' on Windows) into the '/' form the SPA tree splits on.</li>
 * </ul>
 */
public final class TestFilePaths {

    private TestFilePaths() {
    }

    public static String normalize(String rel) {
        if (rel == null || rel.trim().isEmpty()) throw new IllegalArgumentException("A file path is required");
        String p = rel.trim().replace('\\', '/');
        if (p.startsWith("/") || p.matches("^[A-Za-z]:.*")) throw new IllegalArgumentException("Absolute paths are not allowed: " + rel);
        List<String> out = new ArrayList<>();
        for (String seg : p.split("/")) {
            if (seg.isEmpty() || seg.equals(".")) continue;
            if (seg.equals("..")) throw new IllegalArgumentException("'..' is not allowed in a file path: " + rel);
            out.add(seg);
        }
        if (out.isEmpty()) throw new IllegalArgumentException("A file path is required");
        return String.join("/", out);
    }

    /** The directory part of a normalized path ("" at the root). */
    public static String dirOf(String normalized) {
        int i = normalized.lastIndexOf('/');
        return i == -1 ? "" : normalized.substring(0, i);
    }

    /** Maps a client path under {@code base}; refuses anything that would resolve outside it. */
    public static File resolve(File base, String rel) throws IOException {
        String n = normalize(rel);
        File f = new File(base, n.replace('/', File.separatorChar));
        String b = base.getCanonicalPath();
        String c = f.getCanonicalPath();
        if (!c.equals(b) && !c.startsWith(b.endsWith(File.separator) ? b : b + File.separator)) {
            throw new IllegalArgumentException("Path is outside the test-case directory: " + rel);
        }
        return f;
    }

    public static String toClient(String osRelative) {
        return osRelative == null ? null : osRelative.replace(File.separatorChar, '/').replace('\\', '/');
    }

    static String ext(String p) {
        String l = p.toLowerCase(Locale.ROOT);
        int i = l.lastIndexOf('.');
        return i == -1 || i < l.lastIndexOf('/') ? "" : l.substring(i);
    }

    /**
     * A rename keeps the file in its directory and its type (.xml/.sel/.props); returns the normalized {from, to}.
     * The old check compared the names after the last File.separator and required them equal, so every rename inside a
     * folder failed ("Source and Target filenames should be valid").
     */
    public static String[] checkRename(String from, String to) {
        String f = normalize(from), t = normalize(to);
        String ef = ext(f), et = ext(t);
        if (!ef.equals(et) || !(ef.equals(".xml") || ef.equals(".sel") || ef.equals(".props"))) {
            throw new IllegalArgumentException("Testcase File should be an xml or sel file, extension should be (.xml/.sel/.props)");
        }
        if (!dirOf(f).equals(dirOf(t))) {
            throw new IllegalArgumentException("Rename keeps the file in its folder: '" + dirOf(f) + "' vs '" + dirOf(t) + "'");
        }
        return new String[] { f, t };
    }

    /** File types a test-file upload may write (what the Test Files listing shows, plus data files). */
    public static boolean uploadable(String normalized) {
        String e = ext(normalized);
        return e.equals(".sel") || e.equals(".xml") || e.equals(".props") || e.equals(".csv") || e.equals(".json") || e.equals(".txt");
    }

    /** Result of {@link #extractZip}: client paths written and entries skipped (with the reason). */
    public static final class Extracted {
        public final List<String> written = new ArrayList<>();
        public final List<String> skipped = new ArrayList<>();
    }

    /**
     * Unpacks a project / folder zip under {@code base}/{@code intoDir}. Every entry goes through {@link #resolve}
     * (zip-slip safe); entries of other types, directories and existing files (unless {@code overwrite}) are skipped.
     * Bounded: at most {@code maxEntries} files and {@code maxBytes} uncompressed in total ({@link LimitException}); on any
     * failure the files this call created are removed again (files it overwrote keep their new content).
     */
    public static Extracted extractZip(java.io.InputStream in, File base, String intoDir, boolean overwrite, int maxEntries, long maxBytes) throws IOException {
        Extracted r = new Extracted();
        String prefix = intoDir == null || intoDir.trim().isEmpty() ? "" : normalize(intoDir) + "/";
        long total = 0;
        List<File> created = new ArrayList<>();
        try (java.util.zip.ZipInputStream z = new java.util.zip.ZipInputStream(in)) {
            java.util.zip.ZipEntry e;
            while ((e = z.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                String rel;
                File f;
                try {
                    rel = normalize(prefix + normalize(e.getName()));   // the entry itself must be relative and '..'-free
                    f = resolve(base, rel);
                } catch (IllegalArgumentException bad) {
                    r.skipped.add(e.getName() + " (" + bad.getMessage() + ")");
                    continue;
                }
                if (!uploadable(rel)) { r.skipped.add(rel + " (file type not allowed)"); continue; }
                if (f.exists() && !overwrite) { r.skipped.add(rel + " (exists)"); continue; }
                if (r.written.size() >= maxEntries) throw new LimitException("Zip has more than " + maxEntries + " files");
                boolean existed = f.exists();
                total += copyCapped(z, f, maxBytes - total, "Zip expands to more than " + maxBytes + " bytes");
                if (!existed) created.add(f);
                r.written.add(rel);
            }
        } catch (IOException fail) {
            for (File c : created) c.delete();   // a failed upload leaves no new files behind
            throw fail;
        }
        return r;
    }

    /** A size / count cap was hit (the handler answers 413). */
    public static final class LimitException extends IOException {
        private static final long serialVersionUID = 1L;
        public LimitException(String m) { super(m); }
    }

    /**
     * Writes {@code in} to {@code f} through a temp file in the same folder, moved over {@code f} only when complete,
     * so a failed or over-size upload never truncates or deletes an existing file. Returns the bytes written.
     */
    public static long copyCapped(java.io.InputStream in, File f, long maxBytes, String tooBig) throws IOException {
        File parent = f.getAbsoluteFile().getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new IOException("Cannot create " + parent);
        File tmp = File.createTempFile(".upload-", ".part", parent);
        long n = 0;
        try {
            try (java.io.OutputStream o = new java.io.FileOutputStream(tmp)) {
                byte[] buf = new byte[8192];
                int k;
                while ((k = in.read(buf)) > 0) {
                    n += k;
                    if (n > maxBytes) throw new LimitException(tooBig);
                    o.write(buf, 0, k);
                }
            }
            java.nio.file.Files.move(tmp.toPath(), f.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return n;
        } finally {
            java.nio.file.Files.deleteIfExists(tmp.toPath());
        }
    }
}
