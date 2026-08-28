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

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gatf.GatfPlugin;
import com.gatf.executor.core.GatfTestCaseExecutorUtil;
import com.gatf.executor.core.WorkflowContextHandler;
import com.gatf.generator.core.GatfTestGeneratorUtil;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;
import java.util.function.Function;
import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.NetworkListener;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * T2 backend integration (066b) — the config-write + login paths the 066 first cut deferred.
 * Unlike GatfServerContractIT (bare root), this seeds a valid temp project (gatf-config.json +
 * a tests/ dir) so AcceptanceTestContext.validateAndInit(true) passes headless, which POST
 * /configure needs (it runs a full context init before persisting). Also covers the
 * login -> token -> authorized-request flow. Browser-free.
 *
 * NOT here: POST /testcases parse-validation — getSubtestsForFile transpiles+compiles the .sel and
 * pulls in the Selenium/WebDriverManager toolchain (driver resolution), so it hangs headless and
 * isn't a fast IT. The trace-e2e run covers that path end-to-end; Phase-0 tools/smoke/sel-lint.py
 * validates .sel offline. The full executor RUN against the built-in simulator is delta-066c.
 */
class GatfServerWriteIT {

    @TempDir static Path ROOT;
    static HttpServer server;
    static String base;
    static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @BeforeAll
    static void startServer() throws Exception {
        GatfConfigToolUtil.authSrc = null;

        // Seed a valid project: testCaseDir must exist for validateAndInit; bases must exist.
        Files.createDirectories(ROOT.resolve("tests"));
        Files.createDirectories(ROOT.resolve("out"));
        String abs = ROOT.toAbsolutePath().toString();
        ObjectNode cfg = WorkflowContextHandler.OM.createObjectNode();
        cfg.put("baseUrl", "http://localhost");
        cfg.put("testCasesBasePath", abs);
        cfg.put("testCaseDir", "tests");
        cfg.put("outFilesBasePath", abs);
        cfg.put("outFilesDir", "out");
        cfg.put("enabled", true);
        cfg.put("authEnabled", false);
        cfg.put("soapAuthEnabled", false);
        Files.writeString(ROOT.resolve("gatf-config.json"), WorkflowContextHandler.OM.writeValueAsString(cfg));

        GatfConfigToolUtil mojo = new GatfConfigToolUtil();
        mojo.setRootDir(abs);
        Function<String, GatfPlugin> f = type ->
                type.startsWith("executor") ? new GatfTestCaseExecutorUtil() : new GatfTestGeneratorUtil();

        server = new HttpServer();
        NetworkListener nl = new NetworkListener("test", "127.0.0.1", 0);
        server.addListener(nl);
        server.getServerConfiguration().addHttpHandler(new GatfConfigurationHandler(mojo, f), "/configure");
        server.getServerConfiguration().addHttpHandler(new GatfMiscHandler(mojo), "/misc");
        // /login mirrors handleRootContext: delegates to the static authenticate()
        server.getServerConfiguration().addHttpHandler(new HttpHandler() {
            public void service(Request req, Response resp) throws Exception {
                GatfConfigToolUtil.authenticate(req, resp);
            }
        }, "/login");
        server.start();
        base = "http://127.0.0.1:" + nl.getPort();
    }

    @AfterAll
    static void stopServer() {
        if (server != null) server.shutdownNow();
    }

    @BeforeEach
    void anonymousByDefault() {
        GatfConfigToolUtil.authSrc = null;
    }

    // ---- helpers ----------------------------------------------------------

    private record Resp(int status, String body) {}

    private Resp send(String method, String path, String body) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(30));
        b.method(method, body == null ? BodyPublishers.noBody() : BodyPublishers.ofString(body));
        HttpResponse<String> r = CLIENT.send(b.build(), BodyHandlers.ofString());
        return new Resp(r.statusCode(), r.body());
    }

    private Resp get(String path) throws Exception { return send("GET", path, null); }

    // ---- tests ------------------------------------------------------------

    @Test
    void executorConfigPostRoundTrips() throws Exception {
        // read the current (valid) config, change one scalar, write it back, read it again
        Resp g1 = get("/configure?configType=executor");
        assertThat(g1.status()).isEqualTo(200);
        ObjectNode cfg = (ObjectNode) WorkflowContextHandler.OM.readTree(g1.body());
        cfg.put("baseUrl", "http://changed.example");

        Resp post = send("POST", "/configure?configType=executor", WorkflowContextHandler.OM.writeValueAsString(cfg));
        assertThat(post.status()).isEqualTo(200);

        Resp g2 = get("/configure?configType=executor");
        assertThat(g2.status()).isEqualTo(200);
        assertThat(WorkflowContextHandler.OM.readTree(g2.body()).path("baseUrl").asText())
                .isEqualTo("http://changed.example");
    }

    @Test
    void loginMintsTokenAndAuthorizesRequest() throws Exception {
        Properties p = new Properties();
        p.put("admin", "secret");
        GatfConfigToolUtil.authSrc = p;                               // auth on
        try {
            Resp login = send("POST", "/login?username=admin&password=secret", null);
            assertThat(login.status()).isEqualTo(200);
            String token = WorkflowContextHandler.OM.readTree(login.body()).path("token").asText();
            assertThat(token).isNotBlank();

            String enc = URLEncoder.encode(token, StandardCharsets.UTF_8);
            assertThat(get("/misc?token=" + enc).status()).isEqualTo(200);   // valid token
            assertThat(get("/misc").status()).isEqualTo(401);                // no token
        } finally {
            GatfConfigToolUtil.authSrc = null;
        }
    }
}
