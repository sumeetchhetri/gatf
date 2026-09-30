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
package com.gatf.selenium.visual;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.imageio.ImageIO;

/**
 * M9: baseline store + check for {@code screenshot compare <name>}.
 * <ul>
 *  <li>Baselines: {@code <testCaseDir>/__screenshots__/<browser>/<script>/<name>.png}, {@code <script>} being the .sel
 *      file's path under the test-case dir without {@code .sel}, so two scripts can use the same name (commit them).</li>
 *  <li>No baseline yet: the actual image becomes the baseline and the step passes, except under {@code CI=true}, where
 *      it fails (as missing codegen goldens do), so a forgotten baseline never passes silently in CI.</li>
 *  <li>{@code gatf test --update-snapshots}: every compared baseline is rewritten; the step passes.</li>
 *  <li>Mismatch: {@code <out>/<browser>-<script>-<name>-expected.png}, {@code -actual.png} and {@code -diff.png} are written
 *      (the diff is attached to the test in the report) and the step fails.</li>
 * </ul>
 * File I/O + {@link ImageDiff} only.
 */
public final class VisualSnapshots {

    public enum Status { MATCH, MISMATCH, CREATED, UPDATED, MISSING }

    /** What happened; {@link #attachments} are the files to attach to the test (absolute paths). */
    public static final class Outcome {
        public final Status status;
        public final boolean passed;
        public final String message;
        public final File baseline;
        public final List<String> attachments = new ArrayList<String>();
        public ImageDiff.Result diff;

        Outcome(Status status, boolean passed, String message, File baseline) {
            this.status = status;
            this.passed = passed;
            this.message = message;
            this.baseline = baseline;
        }
    }

    private VisualSnapshots() {}

    /** A snapshot name safe as a file name: letters, digits, {@code . _ -}; path separators become {@code -}. */
    public static String safeName(String name) {
        String n = name == null ? "" : name.trim();
        if (n.toLowerCase(Locale.ROOT).endsWith(".png")) n = n.substring(0, n.length() - 4);
        n = n.replaceAll("[\\\\/]+", "-").replaceAll("[^A-Za-z0-9._-]+", "_").replaceAll("^[.]+", "");
        if (n.isEmpty()) throw new IllegalArgumentException("screenshot compare needs a name");
        return n;
    }

    /** A script key ({@code ui/login}) as safe relative path segments; null / blank = none. */
    static String safeScript(String script) {
        if (script == null) return "";
        List<String> segs = new ArrayList<String>();
        for (String s : script.replace('\\', '/').split("/")) {
            String t = s.trim().replaceAll("[^A-Za-z0-9._-]+", "_").replaceAll("^[.]+", "");
            if (!t.isEmpty()) segs.add(t);
        }
        return String.join("/", segs);
    }

    /** {@code <testCaseDir>/__screenshots__/<browser>/<script>/<name>.png} ({@code <script>/} omitted when unknown). */
    public static File baselineFile(File testCaseDir, String browser, String script, String name) {
        String b = browser == null || browser.trim().isEmpty() ? "default" : browser.trim().replaceAll("[^A-Za-z0-9._-]+", "_");
        File dir = new File(new File(testCaseDir, "__screenshots__"), b);
        String sc = safeScript(script);
        if (!sc.isEmpty()) dir = new File(dir, sc);
        return new File(dir, safeName(name) + ".png");
    }

    /**
     * Compares {@code actual} with the baseline and applies the update / missing-baseline rules.
     * @param outDir where expected / actual / diff PNGs of a mismatch go
     * @param ci     true under CI ({@code CI=true}): a missing baseline fails
     */
    public static Outcome check(File testCaseDir, String browser, String script, String name, BufferedImage actual, ImageDiff.Options opts,
            boolean update, boolean ci, File outDir) throws IOException {
        File base = baselineFile(testCaseDir, browser, script, name);
        String label = "screenshot '" + safeName(name) + "'";
        if (update) {
            write(actual, base);
            return new Outcome(Status.UPDATED, true, label + ": baseline updated (" + base.getPath() + ")", base);
        }
        if (!base.isFile()) {
            if (ci) {
                File act = new File(outDir, prefix(browser, script, name) + "-actual.png");
                write(actual, act);
                Outcome o = new Outcome(Status.MISSING, false, label + ": no baseline at " + base.getPath()
                        + " (CI=true). Run gatf test --update-snapshots locally and commit it; the actual image is " + act.getPath(), base);
                o.attachments.add(act.getAbsolutePath());
                return o;
            }
            write(actual, base);
            return new Outcome(Status.CREATED, true, label + ": no baseline yet, wrote " + base.getPath() + " (commit it)", base);
        }
        BufferedImage expected = ImageIO.read(base);
        if (expected == null) throw new IOException("not a readable image: " + base);
        ImageDiff.Result r = ImageDiff.compare(expected, actual, opts);
        if (r.passed) {
            Outcome o = new Outcome(Status.MATCH, true, label + ": " + r.message, base);
            o.diff = r;
            return o;
        }
        String p = prefix(browser, script, name);
        File exp = new File(outDir, p + "-expected.png"), act = new File(outDir, p + "-actual.png"), dif = new File(outDir, p + "-diff.png");
        Files.createDirectories(outDir.toPath());
        Files.copy(base.toPath(), exp.toPath(), StandardCopyOption.REPLACE_EXISTING);
        write(actual, act);
        write(r.diff, dif);
        Outcome o = new Outcome(Status.MISMATCH, false, label + " does not match its baseline: " + r.message + "; diff: " + dif.getPath(), base);
        o.diff = r;
        o.attachments.add(dif.getAbsolutePath());
        o.attachments.add(exp.getAbsolutePath());
        o.attachments.add(act.getAbsolutePath());
        return o;
    }

    static String prefix(String browser, String script, String name) {
        String b = browser == null || browser.trim().isEmpty() ? "" : browser.trim().replaceAll("[^A-Za-z0-9._-]+", "_") + "-";
        String sc = safeScript(script).replace('/', '-');
        return b + (sc.isEmpty() ? "" : sc + "-") + safeName(name);
    }

    /** Writes via a temp file + rename, so a parallel worker never reads a half-written PNG. */
    private static void write(BufferedImage img, File f) throws IOException {
        File dir = f.getAbsoluteFile().getParentFile();
        if (dir != null) Files.createDirectories(dir.toPath());
        File tmp = File.createTempFile(".gatf-", ".png.tmp", dir);
        try {
            if (!ImageIO.write(img, "png", tmp)) throw new IOException("no PNG writer for " + f);
            try {
                Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp.toPath());
        }
    }
}
