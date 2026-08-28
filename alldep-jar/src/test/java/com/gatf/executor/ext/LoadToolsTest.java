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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** delta-077 — shell-free vegeta/autocannon argv, wrk→vegeta fallback notice, docker-exec delegation. */
class LoadToolsTest {

    @Test
    void vegetaTargetsAndArgs() {
        Map<String, String> h = new LinkedHashMap<String, String>();
        h.put("Authorization", "Bearer a b");
        File body = new File("/tmp/sp ace/b.json");
        assertThat(VegetaRunner.targets("post", "http://h/p?q=1&r=2", h, body))
                .isEqualTo("POST http://h/p?q=1&r=2\nAuthorization: Bearer a b\n@" + body.getAbsolutePath() + "\n\n");
        File t = new File("/t"), o = new File("/o");
        List<String> rate = VegetaRunner.attackArgs(t, o, 5, 0, 25, 0, 2, 3);
        assertThat(String.join(" ", rate)).isEqualTo("attack -format http -targets " + t.getAbsolutePath() + " -output "
                + o.getAbsolutePath() + " -duration 2s -rate 25 -connections 5 -timeout 3s");   // rate, not connections
        assertThat(VegetaRunner.attackArgs(t, o, 0, 0, 0, 8, 1, 0).containsAll(Arrays.asList("-rate", "0", "-max-workers", "8"))).isTrue();
    }

    @Test
    void autocannonArgsCarryMethodBodyFileAndRate() {
        Map<String, String> h = new LinkedHashMap<String, String>();
        h.put("X-Q", "x&y|z");
        File body = new File("/tmp/b.json");
        List<String> a = AutocannonTestCaseExecutor.args("post", "http://h/p?a=1&b=2", h, body, 2, 0, 20, 1, 0);
        assertThat(a).containsExactly("--json", "--no-progress", "-c", "2", "-R", "20", "-d", "1", "-m", "POST",
                "-i", body.getAbsolutePath(), "-H", "X-Q=x&y|z", "http://h/p?a=1&b=2");
    }

    @Test
    void fallbackNoticeNamesTheLoss() {
        assertThat(VegetaTestCaseExecutor.fallbackNotice("wrk2", true)).contains("no Lua scripting")
                .contains("perfConfig.filePath was ignored").contains("same model as wrk2 -R");
        assertThat(VegetaTestCaseExecutor.fallbackNotice("wrk", false)).contains("max-rate");
    }

    @Test
    void localhostRewriteOnlyTouchesTheHost() {
        assertThat(DockerLoadgen.rewriteLocalhost("http://localhost:8080/a?u=http://localhost")).isEqualTo("http://host.docker.internal:8080/a?u=http://localhost");
        assertThat(DockerLoadgen.rewriteLocalhost("https://127.0.0.1/x")).isEqualTo("https://host.docker.internal/x");
        assertThat(DockerLoadgen.rewriteLocalhost("http://[::1]:9/")).isEqualTo("http://host.docker.internal:9/");
        assertThat(DockerLoadgen.rewriteLocalhost("http://localhost.example.com/")).isEqualTo("http://localhost.example.com/");
    }

    /** Unix-only: a recording docker shim stands in for the daemon (fresh machine: no image, no container). */
    @Test
    void dockerExecDelegationSequence() throws Exception {
        if (File.separatorChar != '/') return;
        File dir = Files.createTempDirectory("gatf-dshim").toFile();
        File log = new File(dir, "log");
        File shim = new File(dir, "docker");
        Files.write(shim.toPath(), ("#!/bin/sh\necho \"$*\" >> " + log + "\ncase \"$1\" in\n"
                + " version) echo 27.3.1 ;;\n"
                + " inspect) [ \"$3\" = '{{.State.Running}}' ] && { [ -f " + dir + "/up ] && echo true || exit 1; } || echo bridge ;;\n"
                + " image) exit 1 ;;\n build) cat > " + dir + "/Dockerfile ;;\n run) touch " + dir + "/up ;;\n"
                + " exec) case \"$*\" in *'command -v wrk2'*) exit 1 ;; esac ;;\nesac\n").getBytes(StandardCharsets.UTF_8));
        shim.setExecutable(true);
        System.setProperty("gatf.docker.bin", shim.getAbsolutePath());
        try {
            List<String> wrk = DockerLoadgen.execPrefix("wrk", null);
            assertThat(wrk).containsExactly(shim.getAbsolutePath(), "exec", "-i", "gatf-loadgen", "wrk");
            assertThat(DockerLoadgen.execPrefix("wrk2", null)).isNull();                  // → vegeta fallback
            assertThat(new String(Files.readAllBytes(new File(dir, "Dockerfile").toPath()), StandardCharsets.UTF_8))
                    .contains("FROM ubuntu:24.04 AS build");                              // embedded Dockerfile via stdin
            String calls = new String(Files.readAllBytes(log.toPath()), StandardCharsets.UTF_8);
            assertThat(calls).contains("build -t gatf/loadgen:1 -").contains("run -d --name gatf-loadgen");
            assertThat(DockerLoadgen.rewriteUrl("http://localhost:1/", wrk)).isEqualTo("http://host.docker.internal:1/");
        } finally {
            System.clearProperty("gatf.docker.bin");
        }
    }
}
