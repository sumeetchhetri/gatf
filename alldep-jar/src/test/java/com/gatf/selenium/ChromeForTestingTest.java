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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

/** G4 — CfT headless-shell provisioning against a local server that mirrors the CfT URL layout. */
class ChromeForTestingTest {

    private static byte[] zip(String... nameContent) throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(bo)) {
            for (int i = 0; i < nameContent.length; i += 2) {
                z.putNextEntry(new ZipEntry(nameContent[i]));
                z.write(nameContent[i + 1].getBytes(StandardCharsets.UTF_8));
                z.closeEntry();
            }
        }
        return bo.toByteArray();
    }

    @Test
    void provisionsMatchingShellAndDriverThenWorksOffline() throws Exception {
        String plat = ChromeForTesting.platform();
        String ver = "131.0.6778.85";
        String exe = File.separatorChar == '\\' ? ".exe" : "";
        Map<String, byte[]> files = new ConcurrentHashMap<String, byte[]>();
        files.put("/versions.json", ("{\"timestamp\":\"x\",\"channels\":{\"Beta\":{\"channel\":\"Beta\",\"version\":\"999.0.0.1\"},"
                + "\"Stable\":{\"channel\":\"Stable\",\"version\":\"" + ver + "\",\"revision\":\"1\"}}}").getBytes(StandardCharsets.UTF_8));
        files.put("/" + ver + "/" + plat + "/chrome-headless-shell-" + plat + ".zip",
                zip("chrome-headless-shell-" + plat + "/chrome-headless-shell" + exe, "shell", "chrome-headless-shell-" + plat + "/libx.so", "lib"));
        files.put("/" + ver + "/" + plat + "/chromedriver-" + plat + ".zip", zip("chromedriver-" + plat + "/chromedriver" + exe, "driver"));
        AtomicInteger zipHits = new AtomicInteger();

        HttpServer srv = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        srv.createContext("/", x -> {
            byte[] b = files.get(x.getRequestURI().getPath());
            if (x.getRequestURI().getPath().endsWith(".zip")) zipHits.incrementAndGet();
            x.sendResponseHeaders(b == null ? 404 : 200, b == null ? -1 : b.length);
            try (OutputStream o = x.getResponseBody()) { if (b != null) o.write(b); }
        });
        srv.start();
        String base = "http://127.0.0.1:" + srv.getAddress().getPort();
        File tools = Files.createTempDirectory("gatf-cft").toFile();
        System.setProperty("gatf.tools.dir", tools.getAbsolutePath());
        System.setProperty("gatf.cft.baseUrl", base);
        System.setProperty("gatf.cft.versionsUrl", base + "/versions.json");
        System.setProperty("gatf.cft.unzip", "java");
        try {
            ChromeForTesting.Install in = ChromeForTesting.headlessShell();
            assertThat(in.version).isEqualTo(ver);                         // Stable, not Beta
            assertThat(new String(Files.readAllBytes(in.browser.toPath()), StandardCharsets.UTF_8)).isEqualTo("shell");
            assertThat(new String(Files.readAllBytes(in.driver.toPath()), StandardCharsets.UTF_8)).isEqualTo("driver");
            if (File.separatorChar == '/') assertThat(in.browser.canExecute() && in.driver.canExecute()).isTrue();
            assertThat(zipHits.get()).isEqualTo(2);

            ChromeForTesting.headlessShell();                               // cached → no re-download
            assertThat(zipHits.get()).isEqualTo(2);

            srv.stop(0);                                                    // offline → newest complete cached version
            assertThat(ChromeForTesting.headlessShell().version).isEqualTo(ver);
        } finally {
            srv.stop(0);
            for (String k : new String[] { "gatf.tools.dir", "gatf.cft.baseUrl", "gatf.cft.versionsUrl", "gatf.cft.unzip" }) System.clearProperty(k);
        }
    }

    @Test
    void rejectsZipSlipAndComparesVersionsNumerically() throws Exception {
        File dir = Files.createTempDirectory("gatf-cft-slip").toFile();
        File z = new File(dir, "evil.zip");
        Files.write(z.toPath(), zip("../escape.txt", "x"));
        System.setProperty("gatf.cft.unzip", "java");
        boolean threw = false;
        try {
            ChromeForTesting.unzip(z, new File(dir, "out"));
        } catch (IllegalStateException e) {
            threw = true;
        } finally {
            System.clearProperty("gatf.cft.unzip");
        }
        assertThat(threw).isTrue();
        assertThat(new File(dir, "escape.txt").exists()).isFalse();
        assertThat(ChromeForTesting.cmp("131.0.6778.85", "131.0.6778.9") > 0).isTrue();
        assertThat(ChromeForTesting.isHeadlessShellRequest(" Headless-Shell ")).isTrue();
        assertThat(ChromeForTesting.isHeadlessShellRequest("/opt/chrome/chrome")).isFalse();
    }
}
