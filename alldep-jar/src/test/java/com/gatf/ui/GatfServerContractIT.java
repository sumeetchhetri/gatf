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

import com.fasterxml.jackson.databind.JsonNode;
import com.gatf.GatfPlugin;
import com.gatf.executor.core.GatfTestCaseExecutorUtil;
import com.gatf.executor.core.WorkflowContextHandler;
import com.gatf.generator.core.GatfTestGeneratorUtil;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;
import java.util.function.Function;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.NetworkListener;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * T2 backend integration — boots the real GATF Grizzly handlers in-process on an ephemeral port
 * against a temp root dir, and exercises the strangler REST contract over HTTP. Browser-free.
 *
 * This first cut deliberately covers ONLY endpoints that read or do plain file ops — none of them
 * triggers AcceptanceTestContext initialisation. The two paths that DO call initilaizeContext(cfg,true)
 * — POST /configure (config write) and POST /testcases (.sel save + parse-validation) — are added in
 * delta-066b, once initilaizeContext is confirmed to run headless on the sample-project config.
 */
class GatfServerContractIT {

    @TempDir static Path ROOT;
    static HttpServer server;
    static String base;
    static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @BeforeAll
    static void startServer() throws Exception {
        // Anonymous mode: the static authSrc defaults to an EMPTY Properties (not null), which would
        // make every request 401; execute() nulls it when auth.props is absent — we do that directly.
        GatfConfigToolUtil.authSrc = null;

        GatfConfigToolUtil mojo = new GatfConfigToolUtil();
        mojo.setRootDir(ROOT.toString());

        Function<String, GatfPlugin> f = type ->
                type.startsWith("executor") ? new GatfTestCaseExecutorUtil() : new GatfTestGeneratorUtil();

        server = new HttpServer();
        NetworkListener nl = new NetworkListener("test", "127.0.0.1", 0);   // 0 -> ephemeral port
        server.addListener(nl);
        server.getServerConfiguration().addHttpHandler(new GatfApiHandler(mojo), "/api");
        server.getServerConfiguration().addHttpHandler(new GatfMiscHandler(mojo), "/misc");
        server.getServerConfiguration().addHttpHandler(new GatfConfigurationHandler(mojo, f), "/configure");
        server.getServerConfiguration().addHttpHandler(new GatfTestCaseFilesHandler(mojo), "/testcasefiles");
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
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(20));
        b.method(method, body == null ? BodyPublishers.noBody() : BodyPublishers.ofString(body));
        HttpResponse<String> r = CLIENT.send(b.build(), BodyHandlers.ofString());
        return new Resp(r.statusCode(), r.body());
    }

    private Resp get(String path) throws Exception { return send("GET", path, null); }

    private JsonNode json(String s) throws Exception { return WorkflowContextHandler.OM.readTree(s); }

    // ---- contract tests ---------------------------------------------------

    @Test
    void apiHealthReturnsOk() throws Exception {
        Resp r = get("/api/health");
        assertThat(r.status()).isEqualTo(200);
        assertThat(json(r.body()).path("ok").asBoolean()).isTrue();
    }

    @Test
    void miscBootstrapReturnsJsonObjectWithDirs() throws Exception {
        Resp r = get("/misc");
        assertThat(r.status()).isEqualTo(200);
        JsonNode m = json(r.body());
        assertThat(m.isObject()).isTrue();
        assertThat(m.has("dirs")).isTrue();
    }

    @Test
    void executorConfigGetReturnsJsonObject() throws Exception {
        Resp r = get("/configure?configType=executor");
        assertThat(r.status()).isEqualTo(200);
        assertThat(json(r.body()).isObject()).isTrue();
    }

    @Test
    void apiConfigAndConfigsAreReadable() throws Exception {
        Resp cfg = get("/api/config");
        assertThat(cfg.status()).isEqualTo(200);
        assertThat(json(cfg.body()).has("config")).isTrue();

        Resp cfgs = get("/api/configs");
        assertThat(cfgs.status()).isEqualTo(200);
        assertThat(json(cfgs.body()).path("configs").isArray()).isTrue();   // shape is {configs:[...]}
    }

    @Test
    void testFileCreateListDelete() throws Exception {
        String q = "/testcasefiles?testcaseFileName=itest_a.sel";
        assertThat(send("POST", q, null).status()).isEqualTo(200);           // create (seeds "open chrome")

        Resp list = get("/testcasefiles");
        assertThat(list.status()).isEqualTo(200);
        assertThat(json(list.body()).isArray()).isTrue();
        assertThat(list.body()).contains("itest_a.sel");

        assertThat(send("DELETE", q, null).status()).isEqualTo(200);         // delete
        assertThat(get("/testcasefiles").body()).doesNotContain("itest_a.sel");
    }

    @Test
    void apiTestfilePathTraversalIsRejected() throws Exception {
        // the resolved file must live under the test dir; "../.." escapes it -> 400
        Resp r = get("/api/testfile?path=../../pom.xml");
        assertThat(r.status()).isEqualTo(400);
    }

    @Test
    void authGateRejectsWhenEnabledAndNoToken() throws Exception {
        assertThat(get("/api/health").status()).isEqualTo(200);   // anonymous baseline

        Properties p = new Properties();
        p.put("admin", "secret");
        GatfConfigToolUtil.authSrc = p;                           // auth on
        try {
            assertThat(get("/api/health").status()).isEqualTo(401);   // no ?token -> rejected
        } finally {
            GatfConfigToolUtil.authSrc = null;
        }
    }
}
