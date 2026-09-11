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
package com.gatf.executor.executor;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.FileInputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.security.KeyStore;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;

/** delta-102: shared long-lived HttpClients (no per-run/per-step leak) + OkHttp-parity redirect policy. */
class HttpClientSharingTest {

    @Test
    void clientsAreSharedPerDistinctSettings() {
        System.clearProperty("gatf.http.redirect");
        assertThat(TestCaseExecutorUtil.sharedClient(false, 10_000) == TestCaseExecutorUtil.sharedClient(false, 10_000)).isTrue();
        assertThat(TestCaseExecutorUtil.sharedClient(false, 10_000) == TestCaseExecutorUtil.sharedClient(false, 5_000)).isFalse();
        assertThat(TestCaseExecutorUtil.sharedClient(false, 10_000) == TestCaseExecutorUtil.sharedClient(true, 10_000)).isFalse();
        assertThat(TestCaseExecutorUtil.getClient() == TestCaseExecutorUtil.getClient()).isTrue();   // plugins: no client per step
    }

    @Test
    void redirectPolicyDefaultsToOkHttpParityAndIsConfigurable() {
        try {
            System.clearProperty("gatf.http.redirect");
            assertThat(TestCaseExecutorUtil.redirectPolicy()).isEqualTo(HttpClient.Redirect.ALWAYS);
            assertThat(TestCaseExecutorUtil.sharedClient(false, 1).followRedirects()).isEqualTo(HttpClient.Redirect.ALWAYS);
            System.setProperty("gatf.http.redirect", "normal");
            assertThat(TestCaseExecutorUtil.sharedClient(false, 1).followRedirects()).isEqualTo(HttpClient.Redirect.NORMAL);
            System.setProperty("gatf.http.redirect", "NEVER");
            assertThat(TestCaseExecutorUtil.redirectPolicy()).isEqualTo(HttpClient.Redirect.NEVER);
        } finally {
            System.clearProperty("gatf.http.redirect");
        }
    }

    @Test
    void httpsToHttpRedirectIsFollowedByDefaultButNotUnderNormal() throws Exception {
        HttpServer plain = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        plain.createContext("/", ex -> { ex.sendResponseHeaders(200, -1); ex.close(); });
        plain.start();
        HttpsServer tls = HttpsServer.create(new InetSocketAddress("localhost", 0), 0);
        tls.setHttpsConfigurator(new HttpsConfigurator(serverCtx()));
        tls.createContext("/", ex -> {
            ex.getResponseHeaders().add("Location", "http://localhost:" + plain.getAddress().getPort() + "/landed");
            ex.sendResponseHeaders(302, -1);
            ex.close();
        });
        tls.start();
        String start = "https://localhost:" + tls.getAddress().getPort() + "/start";
        try {
            System.clearProperty("gatf.http.redirect");
            HttpResponse<Void> r = TestCaseExecutorUtil.sharedClient(true, 0)
                    .send(HttpRequest.newBuilder(URI.create(start)).build(), HttpResponse.BodyHandlers.discarding());
            assertThat(r.statusCode()).isEqualTo(200);
            assertThat(r.uri().getScheme()).isEqualTo("http");                              // downgrade followed (OkHttp parity)

            System.setProperty("gatf.http.redirect", "normal");
            HttpResponse<Void> n = TestCaseExecutorUtil.sharedClient(true, 0)
                    .send(HttpRequest.newBuilder(URI.create(start)).build(), HttpResponse.BodyHandlers.discarding());
            assertThat(n.statusCode()).isEqualTo(302);                                      // opt-out: downgrade refused
        } finally {
            System.clearProperty("gatf.http.redirect");
            tls.stop(0);
            plain.stop(0);
        }
    }

    static SSLContext serverCtx() throws Exception {
        File ks = new File(Files.createTempDirectory("gatf-rd").toFile(), "ks.p12");
        String keytool = System.getProperty("java.home") + File.separator + "bin" + File.separator + "keytool";
        Process p = new ProcessBuilder(keytool, "-genkeypair", "-alias", "s", "-keyalg", "RSA", "-keysize", "2048",
                "-dname", "CN=localhost", "-validity", "2", "-storetype", "PKCS12", "-keystore", ks.getPath(),
                "-storepass", "changeit", "-keypass", "changeit").redirectErrorStream(true).start();
        assertThat(p.waitFor()).isEqualTo(0);
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (FileInputStream in = new FileInputStream(ks)) { store.load(in, "changeit".toCharArray()); }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(store, "changeit".toCharArray());
        SSLContext c = SSLContext.getInstance("TLS");
        c.init(kmf.getKeyManagers(), null, null);
        return c;
    }
}
