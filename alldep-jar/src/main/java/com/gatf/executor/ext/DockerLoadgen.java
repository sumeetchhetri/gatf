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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.apache.commons.lang3.SystemUtils;

import com.gatf.executor.core.GatfExecutorConfig;

/**
 * Runs wrk / wrk2 inside a long-running "gatf-loadgen" container via {@code docker exec} — the same
 * thing a user gets with {@code alias wrk='docker exec -i gatf-loadgen wrk'}.
 *
 * Container: {@code perfToolContainer} config / {@code $GATF_PERF_CONTAINER}, default {@code gatf-loadgen}.
 * If it is stopped it is started; if it does not exist and {@code -Dgatf.loadgen.autostart} is not
 * {@code false}, the image ({@code -Dgatf.loadgen.image} / {@code $GATF_LOADGEN_IMAGE}, default
 * {@code gatf/loadgen:1}) is built once from the Dockerfile embedded in the jar (no build context) and
 * the container is started — with {@code --network host} on Linux. {@code -Dgatf.loadgen.docker=false}
 * disables Docker delegation entirely. {@code -Dgatf.docker.bin} overrides the docker executable.
 *
 * Networking: on a host-network container targets are used as-is; otherwise localhost / 127.0.0.1 /
 * [::1] targets are rewritten to host.docker.internal. Note that load generated through Docker Desktop's
 * VM (macOS/Windows) is capped by the VM's networking — fine for functional load checks, not for
 * headline numbers.
 */
public final class DockerLoadgen {

    public static final String DEFAULT_IMAGE = "gatf/loadgen:1";
    public static final String DEFAULT_CONTAINER = "gatf-loadgen";

    private static volatile Boolean dockerOk;

    private DockerLoadgen() {}

    public static String docker() {
        String d = System.getProperty("gatf.docker.bin");
        return d != null && !d.trim().isEmpty() ? d.trim() : "docker";
    }

    public static String container(GatfExecutorConfig cfg) {
        String c = cfg == null ? null : cfg.getPerfToolContainer();
        if (c == null || c.trim().isEmpty()) c = System.getenv("GATF_PERF_CONTAINER");
        return c == null || c.trim().isEmpty() ? DEFAULT_CONTAINER : c.trim();
    }

    static String image() {
        String i = System.getProperty("gatf.loadgen.image");
        if (i == null || i.trim().isEmpty()) i = System.getenv("GATF_LOADGEN_IMAGE");
        return i == null || i.trim().isEmpty() ? DEFAULT_IMAGE : i.trim();
    }

    /** Docker CLI present AND daemon reachable (checked once per JVM). */
    public static boolean dockerAvailable() {
        if (dockerOk == null) {
            Out o = run(Arrays.asList(docker(), "version", "--format", "{{.Server.Version}}"), null);
            dockerOk = o.rc == 0 && !o.out.trim().isEmpty();
        }
        return dockerOk;
    }

    /** [docker, exec, -i, container, tool], or null when Docker can't serve this tool here. */
    public static synchronized List<String> execPrefix(String tool, GatfExecutorConfig cfg) {
        if ("false".equalsIgnoreCase(System.getProperty("gatf.loadgen.docker"))) return null;
        if (!dockerAvailable()) return null;
        String name = container(cfg);
        if (!ensureRunning(name)) return null;
        if (run(Arrays.asList(docker(), "exec", name, "sh", "-c", "command -v " + tool), null).rc != 0) {
            System.err.println("[gatf] container '" + name + "' has no " + tool
                    + ("wrk2".equals(tool) ? " (wrk2 is amd64-only in the loadgen image)" : ""));
            return null;
        }
        return Arrays.asList(docker(), "exec", "-i", name, tool);
    }

    public static boolean isDockerCommand(List<String> cmd) {
        return cmd != null && cmd.size() > 3 && cmd.get(0).equals(docker()) && "exec".equals(cmd.get(1));
    }

    /** Container name from a docker-exec prefix. */
    static String containerOf(List<String> cmd) {
        return cmd.get(3);
    }

    // running → ok; stopped → start; missing → (build image) + run
    static boolean ensureRunning(String name) {
        Out st = run(Arrays.asList(docker(), "inspect", "-f", "{{.State.Running}}", name), null);
        if (st.rc == 0 && st.out.trim().equals("true")) return true;
        if (st.rc == 0) {
            return run(Arrays.asList(docker(), "start", name), null).rc == 0;
        }
        if ("false".equalsIgnoreCase(System.getProperty("gatf.loadgen.autostart"))) {
            System.err.println("[gatf] Docker is available but container '" + name + "' does not exist (autostart off). "
                    + "Start it: docker run -d --name " + name + (SystemUtils.IS_OS_LINUX ? " --network host " : " ") + image());
            return false;
        }
        String img = image();
        if (run(Arrays.asList(docker(), "image", "inspect", img), null).rc != 0 && !buildImage(img)) return false;
        List<String> runCmd = new ArrayList<String>(Arrays.asList(docker(), "run", "-d", "--name", name));
        if (SystemUtils.IS_OS_LINUX) runCmd.addAll(Arrays.asList("--network", "host"));
        else runCmd.add("--add-host=host.docker.internal:host-gateway");
        runCmd.add(img);
        Out r = run(runCmd, null);
        if (r.rc != 0) System.err.println("[gatf] could not start " + name + ": " + r.out.trim());
        return r.rc == 0;
    }

    private static boolean buildImage(String img) {
        System.err.println("[gatf] building " + img + " once (wrk + wrk2 + vegeta; a few minutes)...");
        byte[] df;
        try (InputStream in = DockerLoadgen.class.getResourceAsStream("/gatf-loadgen/Dockerfile")) {
            if (in == null) throw new IllegalStateException("embedded Dockerfile missing");
            df = in.readAllBytes();
        } catch (Exception e) {
            System.err.println("[gatf] " + e.getMessage());
            return false;
        }
        Out b = run(Arrays.asList(docker(), "build", "-t", img, "-"), df);
        if (b.rc != 0) System.err.println("[gatf] image build failed:\n" + tail(b.out, 2000));
        return b.rc == 0;
    }

    /** Copy a host file into the container (world-readable), returning its in-container path. */
    public static String stage(File f, List<String> dockerCmd) throws Exception {
        String name = containerOf(dockerCmd);
        String dest = "/tmp/gatf/" + UUID.randomUUID() + "-" + f.getName().replaceAll("[^A-Za-z0-9._-]", "_");
        f.setReadable(true, false); // the image runs as a non-root user
        Out r = run(Arrays.asList(docker(), "cp", f.getAbsolutePath(), name + ":" + dest), null);
        if (r.rc != 0) throw new IllegalStateException("docker cp failed: " + r.out.trim());
        return dest;
    }

    public static void unstage(String path, List<String> dockerCmd) {
        if (path != null) run(Arrays.asList(docker(), "exec", containerOf(dockerCmd), "rm", "-f", path), null);
    }

    /** localhost targets → host.docker.internal unless the container shares the host network. */
    public static String rewriteUrl(String url, List<String> dockerCmd) {
        if (url == null) return null;
        Out n = run(Arrays.asList(docker(), "inspect", "-f", "{{.HostConfig.NetworkMode}}", containerOf(dockerCmd)), null);
        if (n.rc == 0 && n.out.trim().equals("host")) return url;
        return rewriteLocalhost(url);
    }

    static String rewriteLocalhost(String url) {
        return url.replaceFirst("^(?i)(https?://)(localhost|127\\.0\\.0\\.1|\\[::1\\])(?=[:/?#]|$)", "$1host.docker.internal");
    }

    // ---- process helper (no shell; stdout+stderr merged; optional stdin) ----

    public static final class Out {
        public final int rc;
        public final String out;

        Out(int rc, String out) {
            this.rc = rc;
            this.out = out;
        }
    }

    public static Out run(List<String> cmd, byte[] stdin) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            try (OutputStream o = p.getOutputStream()) {
                if (stdin != null) o.write(stdin);
            }
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            p.getInputStream().transferTo(bo);
            return new Out(p.waitFor(), bo.toString("UTF-8"));
        } catch (Exception e) {
            return new Out(-1, String.valueOf(e.getMessage()));
        }
    }

    private static String tail(String s, int n) {
        return s.length() <= n ? s : s.substring(s.length() - n);
    }
}
