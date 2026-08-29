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
package com.gatf.selenium;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.gatf.executor.ext.PerfToolProvisioner;

/**
 * G4 — auto-provisions Chrome for Testing's {@code chrome-headless-shell} plus the MATCHING
 * {@code chromedriver} (same version, same CfT build), so local "Mode B" needs no manual setup.
 *
 * Opt-in: {@code GATF_CHROME_BINARY=headless-shell} (or {@code -Dgatf.chrome.binary=headless-shell}).
 * Any other GATF_CHROME_BINARY value is still treated as a binary path (unchanged behaviour).
 *
 * Version: {@code -Dgatf.cft.version} / {@code $GATF_CFT_VERSION}, else the current Stable from
 * last-known-good-versions.json; if that lookup fails (offline), the newest complete cached version.
 * Mirrors / air-gapped: {@code -Dgatf.cft.baseUrl} (zips at {@code <base>/<ver>/<platform>/…zip}) and
 * {@code -Dgatf.cft.versionsUrl}. Cache: {@code <tools cache>/chrome-for-testing/<ver>/}.
 *
 * CfT platforms: linux64, mac-arm64, mac-x64, win64, win32. There is NO linux-arm64 CfT build —
 * on that platform point GATF_CHROME_BINARY at a system chromium instead.
 * Linux note: the shell still needs the usual Chrome shared libs (libnss3, libgbm, …) on minimal images.
 */
public final class ChromeForTesting {

    public static final class Install {
        public final String version;
        public final File browser;
        public final File driver;

        Install(String version, File browser, File driver) {
            this.version = version;
            this.browser = browser;
            this.driver = driver;
        }
    }

    static final String DEFAULT_BASE = "https://storage.googleapis.com/chrome-for-testing-public";
    static final String DEFAULT_VERSIONS =
            "https://googlechromelabs.github.io/chrome-for-testing/last-known-good-versions.json";
    private static final Pattern STABLE =
            Pattern.compile("\"Stable\"\\s*:\\s*\\{[^}]*?\"version\"\\s*:\\s*\"([0-9.]+)\"");

    private ChromeForTesting() {}

    /** True when a GATF_CHROME_BINARY value asks for the auto-provisioned headless shell. */
    public static boolean isHeadlessShellRequest(String v) {
        if (v == null) return false;
        String t = v.trim().toLowerCase(Locale.ROOT);
        return t.equals("headless-shell") || t.equals("chrome-headless-shell");
    }

    /** Resolve (downloading on first use) headless-shell + matching chromedriver. */
    public static synchronized Install headlessShell() throws Exception {
        String plat = platform();
        File root = new File(PerfToolProvisioner.cacheRoot(), "chrome-for-testing");
        String ver = pinnedVersion();
        if (ver == null) {
            try {
                ver = latestStable();
            } catch (Exception e) {
                ver = newestCached(root, plat);
                if (ver == null) throw new IllegalStateException("cannot resolve the Chrome for Testing Stable version ("
                        + e.getMessage() + ") and nothing is cached — set -Dgatf.cft.version or check network/proxy", e);
                System.err.println("[gatf] CfT version lookup failed (" + e.getMessage() + ") — using cached " + ver);
            }
        }
        File dir = new File(root, ver);
        Install in = layout(ver, dir, plat);
        String base = prop("gatf.cft.baseUrl", DEFAULT_BASE);
        if (!in.browser.isFile()) fetchZip(base + "/" + ver + "/" + plat + "/chrome-headless-shell-" + plat + ".zip", dir);
        if (!in.driver.isFile()) fetchZip(base + "/" + ver + "/" + plat + "/chromedriver-" + plat + ".zip", dir);
        if (!in.browser.isFile() || !in.driver.isFile()) {
            throw new IllegalStateException("CfT " + ver + " extracted but binaries missing under " + dir);
        }
        // zips re-packed by a mirror may lack stored unix modes → ensure the two entry points are runnable
        if (File.separatorChar == '/') {
            in.browser.setExecutable(true, false);
            in.driver.setExecutable(true, false);
        }
        return in;
    }

    static Install layout(String ver, File dir, String plat) {
        return new Install(ver,
                new File(new File(dir, "chrome-headless-shell-" + plat), exe("chrome-headless-shell")),
                new File(new File(dir, "chromedriver-" + plat), exe("chromedriver")));
    }

    static String platform() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        boolean arm = arch.contains("aarch64") || arch.contains("arm64");
        if (os.contains("mac") || os.contains("darwin")) return arm ? "mac-arm64" : "mac-x64";
        if (os.contains("win")) return arch.contains("64") ? "win64" : "win32";
        if (arm) throw new IllegalStateException("Chrome for Testing has no linux-arm64 build — install chromium "
                + "and set GATF_CHROME_BINARY to its path");
        return "linux64";
    }

    private static String pinnedVersion() {
        String v = prop("gatf.cft.version", System.getenv("GATF_CFT_VERSION"));
        return v == null || v.trim().isEmpty() ? null : v.trim();
    }

    static String latestStable() throws Exception {
        File tmp = File.createTempFile("cft-versions", ".json");
        try {
            PerfToolProvisioner.download(prop("gatf.cft.versionsUrl", DEFAULT_VERSIONS), tmp);
            String json = new String(Files.readAllBytes(tmp.toPath()), "UTF-8");
            Matcher m = STABLE.matcher(json);
            if (!m.find()) throw new IllegalStateException("no Stable version in CfT versions JSON");
            return m.group(1);
        } finally {
            tmp.delete();
        }
    }

    // newest version dir (numeric compare) that already holds both binaries — the offline fallback
    static String newestCached(File root, String plat) {
        String[] vs = root.list();
        if (vs == null) return null;
        String best = null;
        for (String v : vs) {
            if (!v.matches("[0-9.]+")) continue;
            Install in = layout(v, new File(root, v), plat);
            if (in.browser.isFile() && in.driver.isFile() && (best == null || cmp(v, best) > 0)) best = v;
        }
        return best;
    }

    static int cmp(String a, String b) {
        String[] x = a.split("\\."), y = b.split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            long p = i < x.length ? Long.parseLong(x[i]) : 0, q = i < y.length ? Long.parseLong(y[i]) : 0;
            if (p != q) return p < q ? -1 : 1;
        }
        return 0;
    }

    private static void fetchZip(String url, File dir) throws Exception {
        dir.mkdirs();
        File zip = new File(dir, url.substring(url.lastIndexOf('/') + 1));
        PerfToolProvisioner.download(url, zip);
        try {
            unzip(zip, dir);
        } finally {
            zip.delete();
        }
    }

    // `unzip` (keeps exec bits + symlinks) when present on unix; else a zip-slip-safe Java extraction
    // that marks every extracted file executable-by-owner on unix.
    static void unzip(File zip, File dir) throws Exception {
        boolean win = File.separatorChar == '\\';
        if (!win && !"java".equals(System.getProperty("gatf.cft.unzip")) && onPath("unzip")) {
            Process p = new ProcessBuilder("unzip", "-q", "-o", zip.getAbsolutePath(), "-d", dir.getAbsolutePath())
                    .redirectErrorStream(true).start();
            p.getInputStream().transferTo(OutputStream.nullOutputStream());
            if (p.waitFor() == 0) return;
        }
        String canon = dir.getCanonicalPath() + File.separator;
        try (ZipInputStream zin = new ZipInputStream(new java.io.FileInputStream(zip))) {
            ZipEntry e;
            byte[] buf = new byte[65536];
            while ((e = zin.getNextEntry()) != null) {
                File f = new File(dir, e.getName());
                if (!f.getCanonicalPath().startsWith(canon)) throw new IllegalStateException("zip entry escapes target: " + e.getName());
                if (e.isDirectory()) {
                    f.mkdirs();
                    continue;
                }
                f.getParentFile().mkdirs();
                try (OutputStream out = new FileOutputStream(f)) {
                    int n;
                    while ((n = zin.read(buf)) > 0) out.write(buf, 0, n);
                }
                if (!win) f.setExecutable(true, true);
            }
        }
    }

    private static boolean onPath(String exe) {
        String path = System.getenv("PATH");
        if (path == null) return false;
        for (String d : path.split(File.pathSeparator)) {
            if (new File(d, exe).canExecute()) return true;
        }
        return false;
    }

    private static String exe(String name) {
        return File.separatorChar == '\\' ? name + ".exe" : name;
    }

    private static String prop(String key, String def) {
        String v = System.getProperty(key);
        return v != null && !v.trim().isEmpty() ? v.trim() : def;
    }

    /** CLI pre-provision (CI / air-gapped prep): java -cp gatf.jar com.gatf.selenium.ChromeForTesting */
    public static void main(String[] args) throws Exception {
        Install in = headlessShell();
        System.out.println("chrome-headless-shell " + in.version + "\n  browser: " + in.browser + "\n  driver:  " + in.driver);
    }
}
