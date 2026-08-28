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
package com.gatf.executor.ext;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.zeroturnaround.zip.ZipUtil;

import com.gatf.executor.core.GatfExecutorConfig;

/**
 * Cross-platform provisioner for the external load-testing tools GATF shells out to
 * (wrk, wrk2, vegeta, autocannon). Given a tool name it returns an absolute path to a
 * runnable binary/command, downloading + caching it on first use when the user has not
 * configured an explicit path.
 *
 * Resolution order for every tool:
 *   1. the explicit path in {@link GatfExecutorConfig} (getWrkPath/getWrk2Path/getVegetaPath/
 *      getAutocannonPath) when it is set and the file exists — configured paths always win, so
 *      existing setups are unaffected;
 *   2. a previously provisioned copy under the tools cache dir;
 *   3. a copy already on the system PATH (brew/apt/choco installs, `which`/`where`);
 *   4. an auto-download / install into the cache dir.
 *
 * Cache dir: -Dgatf.tools.dir, else $GATF_TOOLS_DIR, else ~/.gatf/tools/&lt;os&gt;-&lt;arch&gt;.
 *
 * Per-tool download strategy (verified asset naming):
 *   vegeta     — GitHub release binary. unix: vegeta_&lt;ver&gt;_&lt;os&gt;_&lt;arch&gt;.tar.gz ;
 *                windows: vegeta_&lt;ver&gt;_windows_&lt;arch&gt;.zip . os=linux|darwin, arch=amd64|arm64.
 *   autocannon — Node CLI. `npm i --prefix &lt;cache&gt;/autocannon autocannon@&lt;ver&gt;` →
 *                &lt;cache&gt;/autocannon/node_modules/.bin/autocannon(.cmd). Requires node+npm on PATH.
 *   wrk / wrk2 — C programs with NO official cross-platform prebuilt binaries. We (a) use PATH,
 *                (b) build from source on macOS/Linux when a C toolchain (make + cc) is present,
 *                (c) return null on Windows (unsupported natively — run under WSL, or use
 *                vegeta/autocannon which ARE auto-provisioned on Windows).
 *
 * Versions are pinned but overridable: -Dgatf.vegeta.version (default 12.12.0),
 * -Dgatf.autocannon.version (default 7).
 */
public class PerfToolProvisioner {

    private static final String DEFAULT_VEGETA_VERSION = "12.12.0";
    private static final String DEFAULT_AUTOCANNON_VERSION = "7";

    // resolved-path cache so we don't re-probe / re-download within a JVM run
    private static final Map<String, String> RESOLVED = new ConcurrentHashMap<String, String>();

    private PerfToolProvisioner() {}

    /**
     * Resolve a runnable path for the given tool, or null when it cannot be provisioned on this OS.
     * @param tool one of wrk, wrk2, vegeta, autocannon (case-insensitive)
     * @param cfg  the executor config (for the explicit configured path); may be null
     */
    public static String resolve(String tool, GatfExecutorConfig cfg) {
        if (tool == null) return null;
        final String key = tool.trim().toLowerCase(Locale.ROOT);

        // 1. explicit configured path always wins
        String configured = configuredPath(key, cfg);
        if (isRunnableFile(configured)) return configured;

        // JVM-level memoization (keyed by tool only; the configured path is handled above)
        String cached = RESOLVED.get(key);
        if (isRunnableFile(cached)) return cached;

        String resolved = null;
        try {
            resolved = provision(key);
        } catch (Throwable t) {
            System.err.println("[gatf] could not provision '" + key + "': " + t.getMessage());
        }
        if (resolved != null) RESOLVED.put(key, resolved);
        // fall back to the bare tool name — lets ProcessBuilder find it on PATH even if our probe missed,
        // preserving the pre-provisioner behaviour rather than hard-failing.
        return resolved != null ? resolved : (configured != null ? configured : key);
    }

    private static String provision(String key) throws Exception {
        // 2. cached provisioned copy
        File cached = cachedBinary(key);
        if (cached != null && cached.exists()) return cached.getAbsolutePath();

        // 3. already on PATH
        String onPath = findOnPath(exeName(key));
        if (onPath != null) return onPath;

        // 4. download / install
        if ("vegeta".equals(key)) return provisionVegeta();
        if ("autocannon".equals(key)) return provisionAutocannon();
        if ("wrk".equals(key) || "wrk2".equals(key)) return provisionWrk(key);
        return null;
    }

    // ---------------------------------------------------------------- vegeta

    private static String provisionVegeta() throws Exception {
        String ver = prop("gatf.vegeta.version", DEFAULT_VEGETA_VERSION);
        String os = vegetaOs();     // linux | darwin | windows
        String arch = vegetaArch(); // amd64 | arm64
        boolean win = SystemUtils.IS_OS_WINDOWS;
        String asset = win
                ? "vegeta_" + ver + "_windows_" + arch + ".zip"
                : "vegeta_" + ver + "_" + os + "_" + arch + ".tar.gz";
        String url = "https://github.com/tsenart/vegeta/releases/download/v" + ver + "/" + asset;

        File dir = toolDir("vegeta");
        dir.mkdirs();
        File archive = new File(dir, asset);
        download(url, archive);

        File bin = new File(dir, exeName("vegeta"));
        if (win) {
            ZipUtil.unpack(archive, dir);
        } else {
            extractTarGz(archive, dir);
        }
        archive.delete();
        if (!bin.exists()) {
            // some archives nest the binary; search for it
            File found = findFile(dir, exeName("vegeta"));
            if (found != null) bin = found;
        }
        if (!bin.exists()) throw new IllegalStateException("vegeta binary not found after extracting " + asset);
        makeExecutable(bin);
        return bin.getAbsolutePath();
    }

    private static String vegetaOs() {
        if (SystemUtils.IS_OS_WINDOWS) return "windows";
        if (SystemUtils.IS_OS_MAC) return "darwin";
        return "linux";
    }

    private static String vegetaArch() {
        String a = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (a.contains("aarch64") || a.contains("arm64")) return "arm64";
        return "amd64";
    }

    // ---------------------------------------------------------------- autocannon

    private static String provisionAutocannon() throws Exception {
        String npm = findOnPath(SystemUtils.IS_OS_WINDOWS ? "npm.cmd" : "npm");
        if (npm == null) npm = findOnPath("npm");
        if (npm == null) {
            System.err.println("[gatf] autocannon needs Node.js/npm on PATH — install Node or set autocannonPath.");
            return null;
        }
        File dir = toolDir("autocannon");
        dir.mkdirs();
        File bin = autocannonBin(dir);
        if (bin.exists()) return bin.getAbsolutePath();

        String ver = prop("gatf.autocannon.version", DEFAULT_AUTOCANNON_VERSION);
        int rc = run(dir, Arrays.asList(npm, "i", "--prefix", dir.getAbsolutePath(), "autocannon@" + ver));
        if (rc != 0 || !bin.exists()) {
            System.err.println("[gatf] `npm i autocannon` failed (rc=" + rc + ") in " + dir);
            return null;
        }
        makeExecutable(bin);
        return bin.getAbsolutePath();
    }

    private static File autocannonBin(File prefix) {
        // npm puts CLIs under <prefix>/node_modules/.bin (bare) or, on Windows, <prefix> too
        File binDir = new File(new File(prefix, "node_modules"), ".bin");
        String name = SystemUtils.IS_OS_WINDOWS ? "autocannon.cmd" : "autocannon";
        return new File(binDir, name);
    }

    // ---------------------------------------------------------------- wrk / wrk2

    private static String provisionWrk(String key) {
        if (SystemUtils.IS_OS_WINDOWS) {
            System.err.println("[gatf] " + key + " has no native Windows build. Run GATF under WSL for "
                    + key + ", or use vegeta/autocannon (auto-provisioned on Windows).");
            return null;
        }
        if (!hasToolchain()) {
            System.err.println("[gatf] " + key + " is not on PATH and no C toolchain (make + cc) is available to "
                    + "build it. Install it (brew install " + key + " / apt-get install " + key + ") or set "
                    + key + "Path.");
            return null;
        }
        try {
            return buildWrkFromSource(key);
        } catch (Throwable t) {
            System.err.println("[gatf] building " + key + " from source failed: " + t.getMessage());
            return null;
        }
    }

    // wrk = wg/wrk, wrk2 = giltene/wrk2. Both are `make` from a source tarball producing a single binary.
    private static String buildWrkFromSource(String key) throws Exception {
        String repo = "wrk2".equals(key) ? "giltene/wrk2" : "wg/wrk";
        String ref = "master";
        String url = "https://codeload.github.com/" + repo + "/tar.gz/refs/heads/" + ref;
        File dir = toolDir(key);
        dir.mkdirs();
        File archive = new File(dir, key + "-src.tar.gz");
        download(url, archive);
        extractTarGz(archive, dir);
        archive.delete();
        File srcRoot = findDir(dir, key.substring(0, 3)); // wrk-master / wrk2-master
        if (srcRoot == null) throw new IllegalStateException("source tree not found for " + key);
        int rc = run(srcRoot, Arrays.asList("make"));
        if (rc != 0) throw new IllegalStateException("`make` failed (rc=" + rc + ")");
        File built = new File(srcRoot, "wrk");
        if (!built.exists()) throw new IllegalStateException("built binary not found");
        File out = new File(dir, exeName(key));
        copy(built, out);
        makeExecutable(out);
        return out.getAbsolutePath();
    }

    private static boolean hasToolchain() {
        return findOnPath("make") != null && (findOnPath("cc") != null || findOnPath("gcc") != null || findOnPath("clang") != null);
    }

    // ---------------------------------------------------------------- paths / cache

    private static String configuredPath(String key, GatfExecutorConfig cfg) {
        if (cfg == null) return null;
        if ("wrk".equals(key)) return cfg.getWrkPath();
        if ("wrk2".equals(key)) return cfg.getWrk2Path();
        if ("vegeta".equals(key)) return cfg.getVegetaPath();
        if ("autocannon".equals(key)) return cfg.getAutocannonPath();
        return null;
    }

    private static File cacheRoot() {
        String override = prop("gatf.tools.dir", System.getenv("GATF_TOOLS_DIR"));
        File root = (override != null && !override.trim().isEmpty())
                ? new File(override.trim())
                : new File(new File(System.getProperty("user.home", "."), ".gatf"), "tools");
        return new File(root, osKey() + "-" + vegetaArch());
    }

    private static File toolDir(String tool) {
        return new File(cacheRoot(), tool);
    }

    private static File cachedBinary(String key) {
        if ("autocannon".equals(key)) {
            File b = autocannonBin(toolDir("autocannon"));
            return b.exists() ? b : null;
        }
        File b = new File(toolDir(key), exeName(key));
        return b.exists() ? b : null;
    }

    private static String osKey() {
        if (SystemUtils.IS_OS_WINDOWS) return "win";
        if (SystemUtils.IS_OS_MAC) return "mac";
        return "linux";
    }

    // the on-disk executable name (adds .exe on Windows for native binaries; autocannon handled separately)
    private static String exeName(String tool) {
        return SystemUtils.IS_OS_WINDOWS ? tool + ".exe" : tool;
    }

    private static boolean isRunnableFile(String path) {
        if (StringUtils.isBlank(path)) return false;
        File f = new File(path);
        return f.exists() && f.isFile();
    }

    // ---------------------------------------------------------------- process / io helpers

    // locate an executable on PATH (`where` on Windows, `which` elsewhere)
    private static String findOnPath(String exe) {
        try {
            String finder = SystemUtils.IS_OS_WINDOWS ? "where" : "which";
            ProcessBuilder pb = new ProcessBuilder(finder, exe);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            StringBuilder sb = new StringBuilder();
            try (InputStream in = p.getInputStream()) {
                byte[] buf = new byte[512];
                int n;
                while ((n = in.read(buf)) > 0) sb.append(new String(buf, 0, n, "UTF-8"));
            }
            int rc = p.waitFor();
            if (rc == 0) {
                String first = sb.toString().split("\\r?\\n")[0].trim();
                if (!first.isEmpty() && new File(first).exists()) return first;
            }
        } catch (Exception ignore) {
        }
        return null;
    }

    private static int run(File cwd, java.util.List<String> cmd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        if (cwd != null) pb.directory(cwd);
        pb.inheritIO();
        Process p = pb.start();
        return p.waitFor();
    }

    private static void download(String url, File dest) throws Exception {
        HttpURLConnection conn = null;
        URL u = new URL(url);
        conn = (HttpURLConnection) u.openConnection();
        conn.setInstanceFollowRedirects(true);
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(120000);
        conn.setRequestProperty("User-Agent", "gatf-provisioner");
        int code = conn.getResponseCode();
        // manual redirect follow (GitHub → objects CDN) in case the platform disables auto-follow across hosts
        int hops = 0;
        while ((code == 301 || code == 302 || code == 303 || code == 307 || code == 308) && hops++ < 5) {
            String loc = conn.getHeaderField("Location");
            conn.disconnect();
            conn = (HttpURLConnection) new URL(loc).openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(120000);
            conn.setRequestProperty("User-Agent", "gatf-provisioner");
            code = conn.getResponseCode();
        }
        if (code != 200) {
            throw new IllegalStateException("download failed HTTP " + code + " for " + url);
        }
        try (InputStream in = conn.getInputStream(); OutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        } finally {
            conn.disconnect();
        }
    }

    // Extract a .tar.gz by shelling to `tar` — present by default on macOS, Linux, and Windows 10+ (bsdtar).
    private static void extractTarGz(File archive, File dir) throws Exception {
        int rc = run(dir, Arrays.asList("tar", "-xzf", archive.getAbsolutePath()));
        if (rc != 0) throw new IllegalStateException("`tar -xzf` failed (rc=" + rc + ") for " + archive.getName());
    }

    private static void makeExecutable(File f) {
        if (!SystemUtils.IS_OS_WINDOWS) {
            try {
                f.setExecutable(true, false);
                f.setReadable(true, false);
            } catch (Exception ignore) {
            }
        }
    }

    private static void copy(File src, File dst) throws Exception {
        try (InputStream in = new java.io.FileInputStream(src); OutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
    }

    private static File findFile(File root, String name) {
        File[] kids = root.listFiles();
        if (kids == null) return null;
        for (File k : kids) {
            if (k.isFile() && k.getName().equals(name)) return k;
            if (k.isDirectory()) {
                File f = findFile(k, name);
                if (f != null) return f;
            }
        }
        return null;
    }

    private static File findDir(File root, String prefix) {
        File[] kids = root.listFiles();
        if (kids == null) return null;
        for (File k : kids) {
            if (k.isDirectory() && k.getName().startsWith(prefix)) return k;
        }
        return null;
    }

    private static String prop(String key, String def) {
        String v = System.getProperty(key);
        return (v != null && !v.trim().isEmpty()) ? v.trim() : def;
    }

    /**
     * CLI: pre-provision one or more tools (handy for CI / air-gapped prep / Windows validation).
     * Usage: java -cp gatf.jar com.gatf.executor.ext.PerfToolProvisioner [wrk] [wrk2] [vegeta] [autocannon]
     * With no args, provisions the two tools that have cross-platform auto-download (vegeta, autocannon).
     */
    public static void main(String[] args) {
        String[] tools = (args != null && args.length > 0)
                ? args
                : new String[] { "vegeta", "autocannon" };
        Map<String, String> results = new LinkedHashMap<String, String>();
        for (String t : tools) {
            String p = resolve(t, null);
            results.put(t, p);
        }
        System.out.println("Provisioned load tools (" + osKey() + "-" + vegetaArch() + "):");
        for (Map.Entry<String, String> e : results.entrySet()) {
            System.out.println("  " + e.getKey() + " -> " + (e.getValue() == null ? "UNAVAILABLE" : e.getValue()));
        }
    }
}
