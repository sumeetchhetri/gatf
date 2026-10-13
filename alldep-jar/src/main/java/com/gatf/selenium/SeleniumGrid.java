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

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import com.gatf.executor.ext.DockerLoadgen;

/**
 * G11 (decision D-7 "guide by default, run on request") — Selenium Grid resolution for GATF sessions.
 *
 * GATF still manages no browser containers by default (D-1). It:
 *  1. uses SELENIUM_REMOTE_URL (env) or -Dgatf.selenium.remoteUrl when set;
 *  2. else, ONLY when asked via GATF_WITH_GRID=&lt;browser|true&gt; (env) or -Dgatf.withGrid, ensures an OFFICIAL
 *     selenium/standalone-&lt;browser&gt; container is running on :4444 (reusing any Grid already answering there)
 *     and stops it on JVM exit if GATF started it;
 *  3. else runs a local driver (Selenium Manager); if that fails, {@link #withGuidance} explains the options.
 *
 * CLI: {@code gatf grid up [chrome|firefox|edge]} / {@code gatf grid down [browser]} / {@code gatf grid status}.
 * Knobs: -Dgatf.grid.port (4444), -Dgatf.grid.tag (latest), -Dgatf.docker.bin (docker).
 */
public final class SeleniumGrid {

    private SeleniumGrid() {}

    static int port() {
        return Integer.getInteger("gatf.grid.port", 4444);
    }

    static String url() {
        return "http://localhost:" + port();
    }

    static String container(String browser) {
        return "gatf-selenium-" + norm(browser);
    }

    static String norm(String browser) {
        String b = browser == null ? "" : browser.trim().toLowerCase(Locale.ROOT);
        if (b.isEmpty() || b.equals("true") || b.startsWith("chrome")) return "chrome";
        if (b.startsWith("firefox")) return "firefox";
        if (b.startsWith("edge")) return "edge";
        return b;
    }

    static boolean armHost(String osArch) {
        String a = osArch == null ? "" : osArch.toLowerCase(Locale.ROOT);
        return a.equals("aarch64") || a.equals("arm64");
    }

    /** Official Selenium image for a browser. Google Chrome/Edge have no Linux arm64 build, so on arm64
     *  hosts "chrome" uses the official multi-arch selenium/standalone-chromium; edge stays amd64-only. */
    static String image(String browser, String osArch) {
        String tag = System.getProperty("gatf.grid.tag", "latest");
        String b = norm(browser);
        if (b.equals("chrome")) return (armHost(osArch) ? "selenium/standalone-chromium:" : "selenium/standalone-chrome:") + tag;
        if (b.equals("firefox")) return "selenium/standalone-firefox:" + tag;
        if (b.equals("edge")) return "selenium/standalone-edge:" + tag;
        throw new IllegalArgumentException("no official Selenium image for browser '" + browser + "' (chrome|firefox|edge)");
    }

    /** GATF_WITH_GRID (env) / -Dgatf.withGrid: null when not requested; "true" means "the session's browser". */
    static String withGridRequest() {
        String v = System.getProperty("gatf.withGrid");
        if (v == null || v.trim().isEmpty()) v = System.getenv("GATF_WITH_GRID");
        if (v == null || v.trim().isEmpty() || v.trim().equalsIgnoreCase("false")) return null;
        return v.trim();
    }

    /** Δ145d: only an explicit SELENIUM_REMOTE_URL / -Dgatf.selenium.remoteUrl (never the on-demand Grid); null when unset. */
    static String explicitRemoteUrl() {
        String u = System.getenv("SELENIUM_REMOTE_URL");
        if (u == null || u.trim().isEmpty()) u = System.getProperty("gatf.selenium.remoteUrl");
        return u == null || u.trim().isEmpty() ? null : u.trim();
    }

    /** The Grid URL a session for {@code browser} should use, or null for a local driver. */
    public static String remoteUrl(String browser) {
        String u = System.getenv("SELENIUM_REMOTE_URL");
        if (u == null || u.trim().isEmpty()) u = System.getProperty("gatf.selenium.remoteUrl");
        if (u != null && !u.trim().isEmpty()) return u.trim();
        String req = withGridRequest();
        if (req == null) return null;
        return ensureUp(req.equalsIgnoreCase("true") ? browser : req, true);
    }

    private static volatile String startedByUs;

    /**
     * Ensure a Grid answers on :port. Reuses one that is already ready; otherwise starts the official image.
     * @param stopOnExit stop the container at JVM exit if we started it (the on-demand mode)
     */
    public static synchronized String ensureUp(String browser, boolean stopOnExit) {
        if (ready(url())) return url();
        if (!DockerLoadgen.dockerAvailable()) {
            throw new IllegalStateException("A Selenium Grid was requested but Docker is not available and nothing is listening on "
                    + url() + ". " + howTo(browser));
        }
        String name = container(browser), img = image(browser, System.getProperty("os.arch"));
        DockerLoadgen.Out st = DockerLoadgen.run(Arrays.asList(DockerLoadgen.docker(), "inspect", "-f", "{{.State.Running}}", name), null);
        if (st.rc == 0) {
            if (!st.out.trim().equals("true")) DockerLoadgen.run(Arrays.asList(DockerLoadgen.docker(), "start", name), null);
        } else {
            System.out.println("[gatf] starting " + img + " as '" + name + "' on port " + port() + " (first pull can take a few minutes)...");
            List<String> cmd = Arrays.asList(DockerLoadgen.docker(), "run", "-d", "--name", name, "-p", port() + ":4444", "--shm-size=2g", img);
            DockerLoadgen.Out r = DockerLoadgen.run(cmd, null);
            if (r.rc != 0) throw new IllegalStateException("could not start " + img + ": " + r.out.trim());
        }
        long deadline = System.currentTimeMillis() + Long.getLong("gatf.grid.startTimeoutMs", 180_000L);
        while (!ready(url())) {
            if (System.currentTimeMillis() > deadline) throw new IllegalStateException("Grid container '" + name + "' did not become ready on " + url());
            try { Thread.sleep(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
        }
        if (stopOnExit && startedByUs == null) {
            startedByUs = name;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> down(name), "gatf-grid-stop"));
        }
        System.out.println("[gatf] Selenium Grid ready at " + url() + " (" + img + ")");
        return url();
    }

    static void down(String containerName) {
        DockerLoadgen.run(Arrays.asList(DockerLoadgen.docker(), "rm", "-f", containerName), null);
    }

    /** GET {url}/status and look for "ready": true (Selenium 4 Grid/standalone status document). */
    static boolean ready(String base) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(base + "/status").openConnection();
            c.setConnectTimeout(1500);
            c.setReadTimeout(3000);
            if (c.getResponseCode() != 200) return false;
            try (InputStream in = c.getInputStream()) {
                String body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                return body.replaceAll("\\s+", "").contains("\"ready\":true");
            }
        } catch (Exception e) {
            return false;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    static String howTo(String browser) {
        String b = norm(browser);
        String img;
        try { img = image(b, System.getProperty("os.arch")); } catch (IllegalArgumentException e) { img = "selenium/standalone-chrome:latest"; }
        return "Options: (1) gatf grid up " + b + "   then   export SELENIUM_REMOTE_URL=" + url()
                + "  (2) re-run with GATF_WITH_GRID=" + b + " to start/stop it automatically"
                + "  (3) docker run -d -p " + port() + ":4444 --shm-size=2g " + img + "  and set SELENIUM_REMOTE_URL"
                + (b.equals("edge") && armHost(System.getProperty("os.arch")) ? "  [note: the Edge image is amd64-only]" : "");
    }

    /** Wrap a local-driver start failure with the guidance above (the cause is kept). */
    public static RuntimeException withGuidance(String browser, Throwable cause) {
        return new IllegalStateException("Could not start a local " + norm(browser) + " browser/driver: "
                + String.valueOf(cause.getMessage()).split("\n")[0] + "\n" + howTo(browser), cause);
    }

    public static void main(String[] args) {
        String cmd = args.length > 0 ? args[0] : "status";
        String browser = args.length > 1 ? args[1] : "chrome";
        switch (cmd) {
            case "up":
                System.out.println("export SELENIUM_REMOTE_URL=" + ensureUp(browser, false));
                break;
            case "down":
                down(container(browser));
                System.out.println("[gatf] removed " + container(browser));
                break;
            case "status":
                boolean r = ready(url());
                System.out.println(url() + (r ? " : ready" : " : not reachable/ready"));
                System.exit(r ? 0 : 1);
                break;
            default:
                System.out.println("usage: gatf grid up|down|status [chrome|firefox|edge]");
                System.exit(2);
        }
    }
}
