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
import java.util.List;
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
 * delta-077 policy — least resistance per OS, host binaries first:
 *   vegeta, autocannon  DEFAULT engines, auto-set-up on every OS (vegeta: official release binary;
 *                       autocannon: host node, else a portable Node LTS from nodejs.org, SHA-256 checked).
 *   wrk, wrk2           configured path → cache → PATH (brew/apt) → `docker exec` into the gatf-loadgen
 *                       container wherever Docker is running ({@link DockerLoadgen}) → otherwise the test
 *                       FALLS BACK to vegeta with an explicit reduced-capability notice. GATF no longer
 *                       builds wrk/wrk2 from source.
 * Use {@link #command} (full launch prefix) rather than {@link #resolve} (single path, legacy).
 *
 * Per-tool download strategy (verified asset naming):
 *   vegeta     — GitHub release binary. unix: vegeta_&lt;ver&gt;_&lt;os&gt;_&lt;arch&gt;.tar.gz ;
 *                windows: vegeta_&lt;ver&gt;_windows_&lt;arch&gt;.zip . os=linux|darwin, arch=amd64|arm64.
 *   autocannon — Node CLI. `npm i --prefix &lt;cache&gt;/autocannon autocannon@&lt;ver&gt;` →
 *                &lt;cache&gt;/autocannon/node_modules/.bin/autocannon(.cmd). Requires node+npm on PATH.
 *   wrk / wrk2 — no official prebuilt binaries and no native Windows port: host install or Docker only.
 *   node       — portable nodejs.org build (tar.gz / win zip) + SHASUMS256.txt verification.
 *
 * Versions are pinned but overridable: -Dgatf.vegeta.version (default 12.12.0),
 * -Dgatf.autocannon.version (default 7), -Dgatf.node.version (exact; default newest LTS in
 * <base>/index.json), -Dgatf.node.baseUrl (mirror / air-gapped; default https://nodejs.org/dist).
 *
 * Integrity (delta-076): downloads land in a temp file and are renamed into place only when
 * complete; vegeta archives are verified against the release's SHA-256 checksums file (mismatch
 * aborts); first-use provisioning is serialized per tool (concurrent tests no longer race the same
 * download).
 */
public class PerfToolProvisioner {

    private static final String DEFAULT_VEGETA_VERSION = "12.12.0";
    private static final String DEFAULT_AUTOCANNON_VERSION = "7";
    private static final String DEFAULT_NODE_BASE = "https://nodejs.org/dist";

    // one lock per tool: first-use download/build is serialized across concurrent test threads
    private static final Map<String, Object> LOCKS = new ConcurrentHashMap<String, Object>();

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
        synchronized (LOCKS.computeIfAbsent(key, k -> new Object())) {
            cached = RESOLVED.get(key);
            if (isRunnableFile(cached)) return cached; // another thread provisioned it while we waited
            try {
                resolved = provision(key);
            } catch (Throwable t) {
                System.err.println("[gatf] could not provision '" + key + "': " + t.getMessage());
            }
            if (resolved != null) RESOLVED.put(key, resolved);
        }
        if (resolved == null && !"wrk".equals(key) && !"wrk2".equals(key) && !"wperfk".equals(key)) { // wrk/wrk2/wperfk → docker / vegeta (see command())
            System.err.println("[gatf] '" + key + "' is unavailable — falling back to the bare command name (PATH lookup at run time).");
        }
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
        if ("autocannon".equals(key)) return null; // node-hosted: use command("autocannon")
        if ("node".equals(key)) return provisionNode();
        return null; // wrk / wrk2: host install or Docker only; wperfk: host install (see command())
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
        verifyVegeta(ver, asset, archive);

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

    // SHA-256 against the release's vegeta_<ver>_checksums.txt. A mismatch aborts (tampered/corrupt);
    // an unreachable checksums file only warns (e.g. a mirror without it).
    private static void verifyVegeta(String ver, String asset, File archive) throws Exception {
        File sums = new File(archive.getParentFile(), "vegeta_" + ver + "_checksums.txt");
        try {
            download("https://github.com/tsenart/vegeta/releases/download/v" + ver + "/" + sums.getName(), sums);
        } catch (Exception e) {
            System.err.println("[gatf] WARNING: could not fetch vegeta checksums (" + e.getMessage() + ") — archive not verified");
            return;
        }
        try {
            String expected = null;
            for (String line : new String(java.nio.file.Files.readAllBytes(sums.toPath()), "UTF-8").split("\\r?\\n")) {
                String[] f = line.trim().split("\\s+");
                if (f.length == 2 && f[1].equals(asset)) expected = f[0].toLowerCase(Locale.ROOT);
            }
            if (expected == null) throw new IllegalStateException(asset + " not listed in " + sums.getName());
            String actual = sha256(archive);
            if (!expected.equals(actual)) {
                archive.delete();
                throw new IllegalStateException("SHA-256 mismatch for " + asset + " (expected " + expected + ", got " + actual + ")");
            }
        } finally {
            sums.delete();
        }
    }

    static String sha256(File f) throws Exception {
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
        try (InputStream in = new java.io.FileInputStream(f)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) sb.append(String.format("%02x", b));
        return sb.toString();
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

    // ---------------------------------------------------------------- command() — full launch prefix

    /**
     * Launch prefix for a tool, or null when it cannot be run here (wrk/wrk2 → caller falls back to vegeta).
     *   vegeta     → [vegeta]
     *   autocannon → [autocannonPath] | [node, &lt;cache&gt;/autocannon/node_modules/autocannon/autocannon.js]
     *   wrk, wrk2  → [path] (configured / cache / PATH) | [docker, exec, -i, &lt;container&gt;, wrk] | null
     *   wperfk     → [path] (wperfkPath / cache / PATH) | null
     */
    public static List<String> command(String tool, GatfExecutorConfig cfg) {
        String key = tool == null ? "" : tool.trim().toLowerCase(Locale.ROOT);
        if ("vegeta".equals(key)) {
            String p = resolve("vegeta", cfg);
            return isRunnableFile(p) || onPath("vegeta") ? Arrays.asList(p) : null;
        }
        if ("autocannon".equals(key)) {
            String configured = cfg == null ? null : cfg.getAutocannonPath();
            if (isRunnableFile(configured)) return Arrays.asList(configured);
            try {
                return autocannonCommand(cfg);
            } catch (Exception e) {
                System.err.println("[gatf] autocannon unavailable: " + e.getMessage());
                return null;
            }
        }
        if ("wperfk".equals(key)) {
            String p = resolve(key, cfg); // configured → cache → PATH; no download yet, no Docker image
            return isRunnableFile(p) ? Arrays.asList(p) : null;
        }
        if ("wrk".equals(key) || "wrk2".equals(key)) {
            String p = resolve(key, cfg); // configured → cache → PATH (never downloads/builds)
            if (isRunnableFile(p)) return Arrays.asList(p);
            List<String> d = DockerLoadgen.execPrefix(key, cfg);
            if (d != null) return d;
            return null;
        }
        String p = resolve(key, cfg);
        return p == null ? null : Arrays.asList(p);
    }

    // node (configured nodePath → PATH → portable) + autocannon installed into the cache with that node's npm
    private static List<String> autocannonCommand(GatfExecutorConfig cfg) throws Exception {
        synchronized (LOCKS.computeIfAbsent("autocannon", k -> new Object())) {
            String node = cfg != null && isRunnableFile(cfg.getNodePath()) ? cfg.getNodePath() : null;
            if (node == null) node = findOnPath(exeName("node"));
            if (node == null) node = findOnPath("node");
            if (node == null) node = provisionNode();
            File dir = toolDir("autocannon");
            dir.mkdirs();
            File js = new File(new File(new File(dir, "node_modules"), "autocannon"), "autocannon.js");
            if (!js.isFile()) {
                File npmCli = npmCliFor(new File(node));
                if (npmCli == null) throw new IllegalStateException("npm not found next to " + node);
                String ver = prop("gatf.autocannon.version", DEFAULT_AUTOCANNON_VERSION);
                int rc = run(dir, Arrays.asList(node, npmCli.getAbsolutePath(), "i", "--no-audit", "--no-fund",
                        "--prefix", dir.getAbsolutePath(), "autocannon@" + ver));
                if (rc != 0 || !js.isFile()) throw new IllegalStateException("`npm i autocannon@" + ver + "` failed (rc=" + rc + ")");
            }
            // [node, script] — no .cmd/.sh shim, so no shell re-parses URLs or header values on any OS
            return Arrays.asList(node, js.getAbsolutePath());
        }
    }

    // npm-cli.js for a node binary: portable layout (<home>/lib/node_modules or <home>/node_modules), or
    // a system install where npm sits beside node (resolved through symlinks)
    static File npmCliFor(File node) {
        try {
            File real = node.toPath().toRealPath().toFile();
            File binDir = real.getParentFile();
            File[] candidates = {
                    new File(binDir, "node_modules/npm/bin/npm-cli.js"),                          // windows zip
                    new File(binDir.getParentFile(), "lib/node_modules/npm/bin/npm-cli.js"),       // unix tarball, /usr/local
                    new File("/usr/lib/node_modules/npm/bin/npm-cli.js"),                          // distro packages
                    new File("/usr/share/nodejs/npm/bin/npm-cli.js") };                            // debian nodejs
            for (File c : candidates) if (c.isFile()) return c;
        } catch (Exception ignore) {
        }
        return null;
    }

    // ---------------------------------------------------------------- node (portable)

    private static String provisionNode() throws Exception {
        synchronized (LOCKS.computeIfAbsent("node", k -> new Object())) {
            String base = prop("gatf.node.baseUrl", DEFAULT_NODE_BASE);
            String ver = prop("gatf.node.version", null);
            if (ver == null) ver = latestNodeLts(base);
            if (!ver.startsWith("v")) ver = "v" + ver;
            String plat = nodePlatform();
            boolean win = SystemUtils.IS_OS_WINDOWS;
            String asset = "node-" + ver + "-" + plat + (win ? ".zip" : ".tar.gz");
            File dir = toolDir("node");
            File home = new File(dir, "node-" + ver + "-" + plat);
            File bin = win ? new File(home, "node.exe") : new File(new File(home, "bin"), "node");
            if (bin.isFile()) return bin.getAbsolutePath();
            dir.mkdirs();
            File archive = new File(dir, asset);
            download(base + "/" + ver + "/" + asset, archive);
            File sums = new File(dir, "SHASUMS256-" + ver + ".txt");
            try {
                download(base + "/" + ver + "/SHASUMS256.txt", sums);
                String expected = null;
                for (String line : new String(java.nio.file.Files.readAllBytes(sums.toPath()), "UTF-8").split("\\r?\\n")) {
                    String[] f = line.trim().split("\\s+");
                    if (f.length == 2 && f[1].equals(asset)) expected = f[0].toLowerCase(Locale.ROOT);
                }
                if (expected == null) throw new IllegalStateException(asset + " not in SHASUMS256.txt");
                if (!expected.equals(sha256(archive))) {
                    archive.delete();
                    throw new IllegalStateException("SHA-256 mismatch for " + asset);
                }
            } finally {
                sums.delete();
            }
            if (win) ZipUtil.unpack(archive, dir);
            else extractTarGz(archive, dir);   // tar keeps npm's symlinks + exec bits
            archive.delete();
            if (!bin.isFile()) throw new IllegalStateException("node binary missing after extracting " + asset);
            makeExecutable(bin);
            return bin.getAbsolutePath();
        }
    }

    // newest LTS from <base>/index.json (entries newest-first; "lts" is false or a codename)
    static String latestNodeLts(String base) throws Exception {
        File idx = File.createTempFile("node-index", ".json");
        try {
            download(base + "/index.json", idx);
            String json = new String(java.nio.file.Files.readAllBytes(idx.toPath()), "UTF-8");
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\{[^{}]*\\}").matcher(json);
            java.util.regex.Pattern ver = java.util.regex.Pattern.compile("\"version\"\\s*:\\s*\"(v[0-9.]+)\"");
            java.util.regex.Pattern lts = java.util.regex.Pattern.compile("\"lts\"\\s*:\\s*\"[^\"]+\"");
            while (m.find()) {
                java.util.regex.Matcher v = ver.matcher(m.group());
                if (v.find() && lts.matcher(m.group()).find()) return v.group(1);
            }
            throw new IllegalStateException("no LTS release in " + base + "/index.json");
        } finally {
            idx.delete();
        }
    }

    static String nodePlatform() {
        String arch = vegetaArch().equals("arm64") ? "arm64" : "x64";
        if (SystemUtils.IS_OS_WINDOWS) return "win-" + arch;
        if (SystemUtils.IS_OS_MAC) return "darwin-" + arch;
        return "linux-" + arch;
    }

    // ---------------------------------------------------------------- paths / cache

    private static String configuredPath(String key, GatfExecutorConfig cfg) {
        if (cfg == null) return null;
        if ("wrk".equals(key)) return cfg.getWrkPath();
        if ("wrk2".equals(key)) return cfg.getWrk2Path();
        if ("vegeta".equals(key)) return cfg.getVegetaPath();
        if ("autocannon".equals(key)) return cfg.getAutocannonPath();
        if ("wperfk".equals(key)) return cfg.getWperfkPath();
        if ("node".equals(key)) return cfg.getNodePath();
        return null;
    }

    /** Tools cache root for this os/arch (shared with the Chrome-for-Testing provisioner). */
    public static File cacheRoot() {
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
        if ("autocannon".equals(key) || "node".equals(key)) return null; // see command() / provisionNode()
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

    public static boolean onPath(String tool) {
        return findOnPath(exeName(tool)) != null || findOnPath(tool) != null;
    }

    /**
     * Run argv with NO shell (paths/URLs/header values are never re-parsed), stdout/stderr to files
     * (no pipe-buffer deadlock). Returns the exit code.
     */
    public static int exec(java.util.List<String> cmd, File stdout, File stderr) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectOutput(stdout);
        pb.redirectError(stderr);
        return pb.start().waitFor();
    }

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

    // Downloads to <dest>.part and renames into place only on success, so an interrupted/failed transfer
    // never leaves a truncated file that a later run would mistake for a good one.
    public static void download(String url, File dest) throws Exception {
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
        File part = new File(dest.getParentFile(), dest.getName() + ".part");
        try (InputStream in = conn.getInputStream(); OutputStream out = new FileOutputStream(part)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        } catch (Exception e) {
            part.delete();
            throw e;
        } finally {
            conn.disconnect();
        }
        java.nio.file.Files.move(part.toPath(), dest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
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

    private static String prop(String key, String def) {
        String v = System.getProperty(key);
        return (v != null && !v.trim().isEmpty()) ? v.trim() : def;
    }

    /**
     * CLI: pre-provision one or more tools (handy for CI / air-gapped prep / Windows validation).
     * Usage: java -cp gatf.jar com.gatf.executor.ext.PerfToolProvisioner [vegeta] [autocannon] [wrk] [wrk2]
     * Prints the launch command each tool resolves to on this machine (UNAVAILABLE → vegeta fallback).
     */
    public static void main(String[] args) {
        String[] tools = (args != null && args.length > 0)
                ? args
                : new String[] { "vegeta", "autocannon", "wrk", "wrk2", "wperfk" };
        Map<String, String> results = new LinkedHashMap<String, String>();
        for (String t : tools) {
            List<String> c = command(t, null);
            results.put(t, c == null ? null : String.join(" ", c));
        }
        System.out.println("Provisioned load tools (" + osKey() + "-" + vegetaArch() + "):");
        for (Map.Entry<String, String> e : results.entrySet()) {
            System.out.println("  " + e.getKey() + " -> " + (e.getValue() == null ? "UNAVAILABLE (tests fall back to vegeta)" : e.getValue()));
        }
    }
}
