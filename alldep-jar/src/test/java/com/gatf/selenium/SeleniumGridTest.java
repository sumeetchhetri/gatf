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
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

/** G11 — Grid resolution / on-demand official container (Docker faked via -Dgatf.docker.bin). */
class SeleniumGridTest {

    @Test
    void safariUsesOnlyAnExplicitRemoteUrl() {
        // Δ145d: `open safari` now goes through BrowserDrivers.create; GATF_WITH_GRID's on-demand Grid has no Safari image
        assumeTrue(System.getenv("SELENIUM_REMOTE_URL") == null, "SELENIUM_REMOTE_URL is set in this environment");
        String oldUrl = System.getProperty("gatf.selenium.remoteUrl"), oldGrid = System.getProperty("gatf.withGrid");
        try {
            System.clearProperty("gatf.selenium.remoteUrl");
            System.setProperty("gatf.withGrid", "true");
            assertThat(SeleniumGrid.explicitRemoteUrl()).isNull();
            System.setProperty("gatf.selenium.remoteUrl", " http://grid:4444 ");
            assertThat(SeleniumGrid.explicitRemoteUrl()).isEqualTo("http://grid:4444");
            assertThat(SeleniumGrid.remoteUrl("safari")).isEqualTo("http://grid:4444");
        } finally {
            if (oldUrl == null) System.clearProperty("gatf.selenium.remoteUrl"); else System.setProperty("gatf.selenium.remoteUrl", oldUrl);
            if (oldGrid == null) System.clearProperty("gatf.withGrid"); else System.setProperty("gatf.withGrid", oldGrid);
        }
    }

    @Test
    void imageSelectionIsOfficialAndArchAware() {
        assertThat(SeleniumGrid.image("chrome", "amd64")).isEqualTo("selenium/standalone-chrome:latest");
        assertThat(SeleniumGrid.image("chrome", "aarch64")).isEqualTo("selenium/standalone-chromium:latest");   // no Linux arm64 Chrome
        assertThat(SeleniumGrid.image("firefox-dkr", "arm64")).isEqualTo("selenium/standalone-firefox:latest");
        assertThat(SeleniumGrid.image("edge", "amd64")).isEqualTo("selenium/standalone-edge:latest");
        assertThat(SeleniumGrid.container("true")).isEqualTo("gatf-selenium-chrome");
    }

    @Test
    void explicitUrlWinsAndNothingStartsUnlessRequested() {
        System.clearProperty("gatf.withGrid");
        System.setProperty("gatf.selenium.remoteUrl", "http://grid.example:4444");
        try {
            assertThat(SeleniumGrid.remoteUrl("chrome")).isEqualTo("http://grid.example:4444");
        } finally {
            System.clearProperty("gatf.selenium.remoteUrl");
        }
    }

    @Test
    void defaultIsLocalDriverWithNoContainer() {
        // env-dependent: reported as SKIPPED (not a silent pass) when the runner exports a Grid
        assumeTrue(System.getenv("SELENIUM_REMOTE_URL") == null && System.getenv("GATF_WITH_GRID") == null,
                "SELENIUM_REMOTE_URL / GATF_WITH_GRID is set in this environment");
        System.clearProperty("gatf.withGrid");
        System.clearProperty("gatf.selenium.remoteUrl");
        assertThat(SeleniumGrid.remoteUrl("chrome")).isNull();   // D-1 default: local driver, no container
    }

    @Test
    void readyParsesTheGridStatusDocument() throws Exception {
        AtomicBoolean ready = new AtomicBoolean(false);
        HttpServer s = status(ready);
        try {
            String base = "http://localhost:" + s.getAddress().getPort();
            assertThat(SeleniumGrid.ready(base)).isFalse();
            ready.set(true);
            assertThat(SeleniumGrid.ready(base)).isTrue();
            assertThat(SeleniumGrid.ready("http://localhost:1")).isFalse();   // nothing listening
        } finally {
            s.stop(0);
        }
    }

    @Test
    void ensureUpStartsTheOfficialImageAndWaitsForReady() throws Exception {
        AtomicBoolean ready = new AtomicBoolean(false);
        HttpServer s = status(ready);
        File dir = Files.createTempDirectory("gatf-fake-docker").toFile();
        File log = new File(dir, "calls.log"), flag = new File(dir, "started");
        File fake = new File(dir, "docker");
        Files.write(fake.toPath(), ("#!/bin/sh\necho \"$@\" >> " + log + "\n"
                + "case \"$1\" in version) echo 27.0.0 ;; inspect) exit 1 ;; run) touch " + flag + "; echo cid ;; esac\n").getBytes(StandardCharsets.UTF_8));
        fake.setExecutable(true);
        Thread flip = new Thread(() -> { while (!flag.exists()) { try { Thread.sleep(50); } catch (InterruptedException e) { return; } } ready.set(true); });
        flip.setDaemon(true);
        flip.start();
        System.setProperty("gatf.docker.bin", fake.getAbsolutePath());
        System.setProperty("gatf.grid.port", String.valueOf(s.getAddress().getPort()));
        try {
            String url = SeleniumGrid.ensureUp("chrome", false);
            assertThat(url).isEqualTo("http://localhost:" + s.getAddress().getPort());
            String calls = new String(Files.readAllBytes(log.toPath()), StandardCharsets.UTF_8);
            assertThat(calls).contains("run -d --name gatf-selenium-chrome -p " + s.getAddress().getPort() + ":4444 --shm-size=2g selenium/standalone-chrom");
        } finally {
            System.clearProperty("gatf.docker.bin");
            System.clearProperty("gatf.grid.port");
            s.stop(0);
        }
    }

    @Test
    void localFailureCarriesActionableGuidance() {
        RuntimeException e = SeleniumGrid.withGuidance("firefox", new RuntimeException("Unable to obtain: firefox\nmore"));
        assertThat(e.getMessage()).contains("Could not start a local firefox").contains("gatf grid up firefox")
                .contains("GATF_WITH_GRID=firefox").contains("selenium/standalone-firefox");
    }

    static HttpServer status(AtomicBoolean ready) throws Exception {
        HttpServer s = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        s.createContext("/status", ex -> {
            byte[] b = ("{\"value\": {\"ready\": " + ready.get() + ", \"message\": \"x\"}}").getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, b.length);
            try (OutputStream o = ex.getResponseBody()) { o.write(b); }
        });
        s.start();
        return s;
    }
}
