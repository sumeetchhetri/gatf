package com.gatf.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gatf.GatfPlugin;
import com.gatf.executor.core.GatfTestCaseExecutorUtil;
import com.gatf.executor.core.WorkflowContextHandler;
import com.gatf.generator.core.GatfTestGeneratorUtil;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.function.Function;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.NetworkListener;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * T2 (066c) — full API-test execution end-to-end, headless. Enables the built-in EmbHttpServer
 * simulator (serves HTTP 200 on "/"), seeds one API TestCase pointed at it, then drives the
 * /execute start-then-poll model to completion. No browser: the API path is doExecute(), not
 * doSeleniumTest() (which is why this can run headless where a .sel run can't).
 *
 * Highest-risk IT in the suite (static run-state in GatfPluginExecutionHandler + the streaming
 * poll format + the simulator wiring) — structural in-container; validated by mvn.
 */
class GatfApiExecutionIT {

    @TempDir static Path ROOT;
    static HttpServer server;
    static String base;
    static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private static int freePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    @BeforeAll
    static void startServer() throws Exception {
        GatfConfigToolUtil.authSrc = null;
        int simPort = freePort();

        Files.createDirectories(ROOT.resolve("tests"));
        Files.createDirectories(ROOT.resolve("out"));
        final String abs = ROOT.toAbsolutePath().toString();

        // config: enable the embedded HTTP simulator on simPort (>=80); API tests target it.
        ObjectNode sim = WorkflowContextHandler.OM.createObjectNode();
        sim.put("enabled", true);
        sim.put("port", simPort);
        ObjectNode simHeaders = WorkflowContextHandler.OM.createObjectNode();
        simHeaders.put("Content-Type", "application/json"); // else the response media type is null
        sim.set("headers", simHeaders);
        sim.put("body", "{\"ok\":true}");
        ObjectNode cfg = WorkflowContextHandler.OM.createObjectNode();
        cfg.put("testCasesBasePath", abs);
        cfg.put("testCaseDir", "tests");
        cfg.put("outFilesBasePath", abs);
        cfg.put("outFilesDir", "out");
        cfg.put("enabled", true);
        cfg.put("authEnabled", false);
        cfg.put("soapAuthEnabled", false);
        cfg.set("htttpServerSimulator", sim);
        Files.writeString(ROOT.resolve("gatf-config.json"), WorkflowContextHandler.OM.writeValueAsString(cfg));

        // one API TestCase: GET the simulator's "/" -> 200 (format mirrors sample-project/tests/api)
        String tc = "<TestCases>\n"
                + "  <TestCase>\n"
                + "    <baseUrl>http://localhost:" + simPort + "</baseUrl>\n"
                + "    <url>/</url>\n"
                + "    <name>ping</name>\n"
                + "    <method>GET</method>\n"
                + "    <secure>false</secure>\n"
                + "    <expectedResCode>200</expectedResCode>\n"
                + "    <expectedResContentType>application/json</expectedResContentType>\n"
                + "    <sequence>1</sequence>\n"
                + "  </TestCase>\n"
                + "</TestCases>\n";
        Files.writeString(ROOT.resolve("tests").resolve("ping.xml"), tc);

        GatfConfigToolUtil mojo = new GatfConfigToolUtil();
        mojo.setRootDir(abs);
        Function<String, GatfPlugin> f = type ->
                type.startsWith("executor") ? new GatfTestCaseExecutorUtil() : new GatfTestGeneratorUtil();

        server = new HttpServer();
        NetworkListener nl = new NetworkListener("test", "127.0.0.1", 0);
        server.addListener(nl);
        server.getServerConfiguration().addHttpHandler(new GatfPluginExecutionHandler(mojo, f), "/execute");
        server.start();
        base = "http://127.0.0.1:" + nl.getPort();
    }

    @AfterAll
    static void stopServer() {
        if (server != null) server.shutdownNow();
    }

    private record Resp(int status, String body) {}

    private Resp send(String method, String path, String body) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(30));
        b.method(method, body == null ? BodyPublishers.noBody() : BodyPublishers.ofString(body));
        HttpResponse<String> r = CLIENT.send(b.build(), BodyHandlers.ofString());
        return new Resp(r.statusCode(), r.body());
    }

    @Test
    void apiSuiteRunsToCompletion() throws Exception {
        Resp start = send("PUT", "/execute?pluginType=executor-api", "[\"ping.xml\"]");
        assertThat(start.status()).isEqualTo(200);
        assertThat(start.body()).contains("Execution Started");

        // API runs have no browser, so this finishes in seconds; poll the start-then-poll GET,
        // which returns streaming status while running and "Execution completed" when done. Cap 40s.
        String last = "";
        boolean done = false;
        long deadline = System.currentTimeMillis() + 40_000;
        while (System.currentTimeMillis() < deadline) {
            last = send("GET", "/execute?pluginType=executor-api", null).body();
            if (last != null && last.contains("Execution completed")) {
                done = true;
                break;
            }
            Thread.sleep(500);
        }
        assertThat(done).as("run completed within 40s; last poll body = %s", last).isTrue();
    }
}
