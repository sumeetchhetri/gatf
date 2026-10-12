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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * H6 (Δ156): the git side of {@code gatf test --changed <range>}: resolves the range, its base commit and the files
 * it changed. Runs the {@code git} on the PATH; nothing is written.
 *
 * <p>Ranges: {@code A...B} (base = merge-base, as for a pull request), {@code A..B} (base = A), or a single
 * revision {@code A} (base = A, compared with the working tree, untracked files included). A missing side is HEAD.
 *
 * @author Sumeet Chhetri
 */
public final class GitRepo implements ImpactSelector.Repo {

    static final long MAX_FILE = 32L << 20;
    static final long MAX_DIFF = 256L << 20;

    public final Path root;
    public final String range;
    private final String base;
    private final List<String> diffArgs;
    private final boolean worktree;
    private List<String> tracked;

    private GitRepo(Path root, String range, String base, List<String> diffArgs, boolean worktree) {
        this.root = root;
        this.range = range;
        this.base = base;
        this.diffArgs = diffArgs;
        this.worktree = worktree;
    }

    /** @throws IllegalArgumentException for a bad range, no git, or not a git work tree */
    public static GitRepo open(File cwd, String range) {
        String r = range == null ? "" : range.trim();
        if (r.isEmpty() || r.startsWith("-") || r.indexOf('\0') >= 0 || r.contains(" ")) {
            throw new IllegalArgumentException("--changed needs a git range such as main...HEAD, HEAD~1..HEAD or HEAD (got '" + range + "')");
        }
        Path root;
        try {
            root = new File(text(run(cwd.toPath(), "rev-parse", "--show-toplevel")).trim()).toPath();
        } catch (IOException e) {
            throw new IllegalArgumentException("--changed: not inside a git work tree (" + e.getMessage() + ")");
        }
        try {
            int tri = r.indexOf("..."), two = r.indexOf("..");
            if (tri >= 0) {
                String a = side(r.substring(0, tri)), b = side(r.substring(tri + 3));
                String ca = commit(root, a), cb = commit(root, b);
                String mb = text(run(root, "merge-base", ca, cb)).trim();
                return new GitRepo(root, r, mb, Arrays.asList(mb, cb), false);
            }
            if (two >= 0) {
                String a = side(r.substring(0, two)), b = side(r.substring(two + 2));
                String ca = commit(root, a), cb = commit(root, b);
                return new GitRepo(root, r, ca, Arrays.asList(ca, cb), false);
            }
            String ca = commit(root, r);
            return new GitRepo(root, r, ca, Collections.singletonList(ca), true);
        } catch (IOException e) {
            throw new IllegalArgumentException("--changed " + r + ": " + e.getMessage());
        }
    }

    private static String side(String s) { return s.isEmpty() ? "HEAD" : s; }

    private static String commit(Path root, String rev) throws IOException {
        if (rev.startsWith("-")) throw new IOException("bad revision " + rev);
        String c = text(run(root, "rev-parse", "--verify", "--quiet", rev + "^{commit}")).trim();
        if (!c.matches("[0-9a-f]{40,64}")) throw new IOException("unknown revision " + rev);
        return c;
    }

    @Override public String base() { return base; }

    @Override public Long baseTimeMs() {
        try {
            return Long.parseLong(text(run(root, "show", "-s", "--format=%ct", base)).trim()) * 1000L;
        } catch (Exception e) {
            return null;
        }
    }

    @Override public synchronized List<String> tracked() {
        if (tracked == null) {
            try {
                tracked = split0(run(root, "ls-tree", "-r", "--name-only", "-z", base));
            } catch (IOException e) {
                tracked = Collections.emptyList();
            }
        }
        return tracked;
    }

    @Override public byte[] baseContent(String path) {
        try {
            byte[] b = run(root, MAX_FILE, "cat-file", "blob", base + ":" + path);
            return b;
        } catch (IOException e) {
            return null;
        }
    }

    /** The changed files of the range (old line numbers), plus untracked files for a working-tree range. */
    public List<DiffParser.FileChange> changes() throws IOException {
        List<String> args = new ArrayList<>(Arrays.asList("diff", "--no-color", "--no-ext-diff", "--no-textconv", "--no-renames", "-U0",
                "--src-prefix=a/", "--dst-prefix=b/"));
        args.addAll(diffArgs);
        args.add("--");
        List<DiffParser.FileChange> out = DiffParser.parse(text(run(root, MAX_DIFF, args.toArray(new String[0]))));
        // cross-check with --name-only: a file the patch parser missed is a whole-file change, never a dropped one
        List<String> names = new ArrayList<>(Arrays.asList("diff", "--no-renames", "--name-only", "-z"));
        names.addAll(diffArgs);
        names.add("--");
        List<String> seen = new ArrayList<>();
        for (DiffParser.FileChange c : out) seen.add(c.path);
        for (String n : split0(run(root, names.toArray(new String[0])))) {
            if (!seen.contains(n)) out.add(new DiffParser.FileChange(n, DiffParser.Status.MODIFIED, true, "the patch for it could not be read", null));
        }
        if (worktree) {
            for (String n : split0(run(root, "ls-files", "--others", "--exclude-standard", "-z"))) {
                out.add(new DiffParser.FileChange(n, DiffParser.Status.ADDED, true, "untracked file", null));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ process

    static byte[] run(Path dir, String... args) throws IOException {
        return run(dir, MAX_DIFF, args);
    }

    static byte[] run(Path dir, long max, String... args) throws IOException {
        List<String> cmd = new ArrayList<>(Arrays.asList("git", "-c", "core.quotepath=off", "-c", "diff.noprefix=false", "-c", "color.ui=never"));
        cmd.addAll(Arrays.asList(args));
        ProcessBuilder pb = new ProcessBuilder(cmd).directory(dir.toFile());
        pb.environment().put("GIT_PAGER", "cat");
        pb.environment().put("LC_ALL", "C");
        Path err = Files.createTempFile("gatf-git-", ".err");
        pb.redirectError(err.toFile());
        Process p;
        try {
            p = pb.start();
        } catch (IOException e) {
            Files.deleteIfExists(err);
            throw new IOException("git is not available (" + e.getMessage() + ")");
        }
        try {
            p.getOutputStream().close();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            boolean over = false;
            try (InputStream in = p.getInputStream()) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) {
                    if (out.size() + (long) n > max) { over = true; break; }
                    out.write(buf, 0, n);
                }
            }
            if (over) {
                p.destroyForcibly();
                throw new IOException("git " + args[0] + " output is over " + max + " bytes");
            }
            if (!p.waitFor(120, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw new IOException("git " + args[0] + " timed out");
            }
            if (p.exitValue() != 0) {
                String e = new String(Files.readAllBytes(err), StandardCharsets.UTF_8).trim();
                int nl = e.indexOf('\n');
                throw new IOException("git " + args[0] + " failed" + (e.isEmpty() ? "" : ": " + (nl > 0 ? e.substring(0, nl) : e)));
            }
            return out.toByteArray();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            p.destroyForcibly();
            throw new IOException("interrupted");
        } finally {
            Files.deleteIfExists(err);
        }
    }

    static String text(byte[] b) { return new String(b, StandardCharsets.UTF_8); }

    static List<String> split0(byte[] b) {
        List<String> out = new ArrayList<>();
        int s = 0;
        for (int i = 0; i < b.length; i++) {
            if (b[i] == 0) {
                if (i > s) out.add(new String(b, s, i - s, StandardCharsets.UTF_8));
                s = i + 1;
            }
        }
        if (s < b.length) out.add(new String(b, s, b.length - s, StandardCharsets.UTF_8).trim());
        return out;
    }
}
