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
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.function.Function;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.NetworkListener;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Δ163o (sweep, out-dir rule): editor lookups and a config save are not runs, so they must not clean out/. On base they
 * called initilaizeContext(cfg, true): on a server with no run yet that deleted the whole out dir (earlier reports,
 * history/, dist-traces/), otherwise every run folder it did not know. The marker is a 36-char (UUID) folder, which
 * both branches deleted, so the test does not depend on which ITs ran first in this JVM.
 */
class OutDirPreservedIT {

    @TempDir static Path ROOT;
    static HttpServer server;
    static String base;
    static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @BeforeAll
    static void startServer() throws Exception {
        GatfConfigToolUtil.authSrc = null;
        Files.createDirectories(ROOT.resolve("tests/ui"));
        Files.writeString(ROOT.resolve("tests/ui/a.sel"), "open chrome\n");
        Files.writeString(ROOT.resolve("tests/ui/b.sel"), "open chrome\n");
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
        server.getServerConfiguration().addHttpHandler(new GatfTestCaseFilesHandler(mojo), "/testcasefiles");
        server.getServerConfiguration().addHttpHandler(new GatfProfileHandler(mojo, f), "/profile");
        server.getServerConfiguration().addHttpHandler(new GatfReportsHandler(mojo, f), "/reports");
        server.start();
        base = "http://127.0.0.1:" + nl.getPort();
    }

    @AfterAll
    static void stopServer() {
        if (server != null) server.shutdownNow();
    }

    private static int send(String method, String path, String body) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(60));
        if (body != null) b.header("Content-Type", "application/json");
        b.method(method, body == null ? BodyPublishers.noBody() : BodyPublishers.ofString(body));
        return CLIENT.send(b.build(), BodyHandlers.ofString()).statusCode();
    }

    private static Path marker() throws Exception {
        Path m = Files.createDirectories(ROOT.resolve("out").resolve(UUID.randomUUID().toString()));
        Files.writeString(m.resolve("index.html"), "earlier report");
        return m;
    }

    @Test
    void anEditorLookupKeepsOut() throws Exception {
        Path m = marker();
        assertThat(send("GET", "/testcasefiles?testcaseFileName=ui/a.sel&referencedFile=b.sel", null)).isEqualTo(200);
        assertThat(m.resolve("index.html")).exists();
    }

    @Test
    void savingTheConfigKeepsOut() throws Exception {
        Path m = marker();
        String cfg = Files.readString(ROOT.resolve("gatf-config.json"));
        assertThat(send("POST", "/configure?configType=executor", cfg)).isEqualTo(200);
        assertThat(m.resolve("index.html")).exists();
    }

    @Test
    void theDataSourceProfileKeepsOut() throws Exception {
        Path m = marker();
        send("GET", "/profile", null);   // no data sources here: the reply may be an error, but the context init ran first
        assertThat(m.resolve("index.html")).exists();
    }

    @Test
    void noCliRunYetIsA204NotA404() throws Exception {
        // Δ163o sweep: the Reports view probes the last CLI run's summary; with none yet it was a 404 (console error)
        Files.deleteIfExists(ROOT.resolve("out/gatf-results.json"));
        assertThat(send("GET", "/reports/gatf-results.json", null)).isEqualTo(204);
        Files.writeString(ROOT.resolve("out/gatf-results.json"), "{\"summary\":{}}");
        assertThat(send("GET", "/reports/gatf-results.json", null)).isEqualTo(200);
        Files.delete(ROOT.resolve("out/gatf-results.json"));
    }
}

