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
import javax.net.ssl.TrustManagerFactory;

import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;

/**
 * delta-101: acceptAllCerts trust-all must (1) accept a wrong-hostname cert on ITS client, even when another
 * HttpClient ran first, and (2) NOT disable hostname verification for any other HttpClient in the JVM.
 */
class TlsTrustTest {

    @Test
    void trustAllIsScopedToItsClientAndWorksRegardlessOfOrder() throws Exception {
        File dir = Files.createTempDirectory("gatf-tls").toFile();
        File ks = new File(dir, "ks.p12");
        String keytool = System.getProperty("java.home") + File.separator + "bin" + File.separator + "keytool";
        Process p = new ProcessBuilder(keytool, "-genkeypair", "-alias", "s", "-keyalg", "RSA", "-keysize", "2048",
                "-dname", "CN=not-localhost.example", "-validity", "2", "-storetype", "PKCS12", "-keystore", ks.getPath(),
                "-storepass", "changeit", "-keypass", "changeit").redirectErrorStream(true).start();
        assertThat(p.waitFor()).isZero();

        KeyStore store = KeyStore.getInstance("PKCS12");
        try (FileInputStream in = new FileInputStream(ks)) { store.load(in, "changeit".toCharArray()); }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(store, "changeit".toCharArray());
        SSLContext serverCtx = SSLContext.getInstance("TLS");
        serverCtx.init(kmf.getKeyManagers(), null, null);
        HttpsServer s = HttpsServer.create(new InetSocketAddress("localhost", 0), 0);
        s.setHttpsConfigurator(new HttpsConfigurator(serverCtx));
        s.createContext("/", ex -> { ex.sendResponseHeaders(200, -1); ex.close(); });
        s.start();
        String url = "https://localhost:" + s.getAddress().getPort() + "/";

        // a STRICT client that trusts the certificate but must still reject the wrong hostname
        KeyStore ts = KeyStore.getInstance("PKCS12");
        ts.load(null, null);
        ts.setCertificateEntry("s", store.getCertificate("s"));
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("PKIX");
        tmf.init(ts);
        SSLContext strictCtx = SSLContext.getInstance("TLS");
        strictCtx.init(null, tmf.getTrustManagers(), null);
        try {
            assertThat(call(HttpClient.newBuilder().sslContext(strictCtx).build(), url)).isEqualTo("FAILED");   // strict first
            assertThat(call(TestCaseExecutorUtil.getClient(), url)).isEqualTo("200");                         // trust-all still works
            assertThat(call(HttpClient.newBuilder().sslContext(strictCtx).build(), url)).isEqualTo("FAILED");   // and did not leak
            assertThat(System.getProperty("jdk.internal.httpclient.disableHostnameVerification")).isNull();
        } finally {
            s.stop(0);
        }
    }

    static String call(HttpClient c, String url) {
        try {
            return String.valueOf(c.send(HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.discarding()).statusCode());
        } catch (Exception e) {
            return "FAILED";
        }
    }
}
