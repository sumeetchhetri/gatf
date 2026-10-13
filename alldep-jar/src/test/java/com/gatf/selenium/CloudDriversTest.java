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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxOptions;

import com.gatf.selenium.SeleniumTest.PassSubTestException;
import com.gatf.selenium.SeleniumTest.WarnSubTestException;
import com.sun.net.httpserver.HttpServer;

/**
 * Δ157: CloudDrivers against a fake W3C hub (real Selenium client). The hub demands HTTP Basic auth (401 + challenge),
 * records every request, and the test greps every output — stdout, stderr, java.util.logging, request lines and
 * bodies, exception messages — for the access key, the user name and the encoded credentials.
 */
public class CloudDriversTest {

    static final String KEY = "sEcReT-aCcEsS-kEy-9f8e7d";
    static final String USER = "alice_cloud_user";
    static final String B64 = Base64.getEncoder().encodeToString((USER + ":" + KEY).getBytes(StandardCharsets.UTF_8));

    /** A tiny W3C hub. mode: ok | fail-session | fail-execute | deny */
    static final class Hub implements AutoCloseable {
        final HttpServer server;
        final List<String> requests = Collections.synchronizedList(new ArrayList<String>());
        final List<String> auths = Collections.synchronizedList(new ArrayList<String>());
        final List<String> scripts = Collections.synchronizedList(new ArrayList<String>());
        volatile String mode = "ok";

        Hub() throws Exception {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", ex -> {
                String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                String path = ex.getRequestURI().toString();
                requests.add(ex.getRequestMethod() + " " + path + " " + body);
                String auth = ex.getRequestHeaders().getFirst("Authorization");
                String out;
                int code = 200;
                if (auth == null || "deny".equals(mode)) {
                    ex.getResponseHeaders().add("WWW-Authenticate", "Basic realm=\"hub\"");
                    code = 401;
                    out = "{\"value\":{\"error\":\"invalid argument\",\"message\":\"auth required\"}}";
                } else {
                    auths.add(auth);
                    if (path.endsWith("/session") && ex.getRequestMethod().equals("POST")) {
                        if ("fail-session".equals(mode)) {
                            code = 500;
                            out = "{\"value\":{\"error\":\"session not created\",\"message\":\"no such platform\"}}";
                        } else {
                            out = "{\"value\":{\"sessionId\":\"cloud-1\",\"capabilities\":{\"browserName\":\"chrome\",\"browserVersion\":\"150\"}}}";
                        }
                    } else if (path.endsWith("/execute/sync")) {
                        scripts.add(body);
                        if ("fail-execute".equals(mode)) {
                            code = 500;
                            out = "{\"value\":{\"error\":\"javascript error\",\"message\":\"nope\"}}";
                        } else {
                            out = "{\"value\":null}";
                        }
                    } else {
                        out = "{\"value\":null}";
                    }
                }
                byte[] b = out.getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
                ex.sendResponseHeaders(code, b.length);
                try (OutputStream os = ex.getResponseBody()) { os.write(b); }
            });
            server.start();
        }

        String url() { return "http://127.0.0.1:" + server.getAddress().getPort(); }

        @Override
        public void close() { server.stop(0); }
    }

    /** Captures stdout, stderr and every java.util.logging record (Selenium logs through JUL). */
    static final class Capture implements AutoCloseable {
        final ByteArrayOutputStream buf = new ByteArrayOutputStream();
        final PrintStream out = System.out, err = System.err;
        final Logger root = Logger.getLogger("");
        final Level level = root.getLevel();
        final Handler h = new Handler() {
            @Override public void publish(LogRecord r) {
                synchronized (buf) {
                    buf.writeBytes((r.getLoggerName() + " " + r.getMessage() + " " + java.util.Arrays.toString(r.getParameters())
                            + (r.getThrown() != null ? " " + r.getThrown() : "") + "\n").getBytes(StandardCharsets.UTF_8));
                }
            }
            @Override public void flush() {}
            @Override public void close() {}
        };

        Capture() {
            PrintStream ps = new PrintStream(buf, true, StandardCharsets.UTF_8);
            System.setOut(ps);
            System.setErr(ps);
            root.setLevel(Level.ALL);
            root.addHandler(h);
        }

        String text() { synchronized (buf) { return buf.toString(StandardCharsets.UTF_8); } }

        @Override
        public void close() {
            System.setOut(out);
            System.setErr(err);
            root.removeHandler(h);
            root.setLevel(level);
        }
    }

    static Map<String, String> env(String... kv) {
        Map<String, String> m = new HashMap<String, String>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }

    static Map<String, String> bs() {
        return env("GATF_CLOUD", "browserstack", "BROWSERSTACK_USERNAME", USER, "BROWSERSTACK_ACCESS_KEY", KEY);
    }

    static void assertClean(String what, String text) {
        assertFalse(text.contains(KEY), what + " leaked the access key");
        assertFalse(text.contains(USER), what + " leaked the user name");
        assertFalse(text.contains(B64), what + " leaked the encoded credentials");
    }

    @Test
    public void nothingSetOrExplicitGridMeansNoCloud() {
        assertNull(CloudDrivers.createIfSelected("chrome", new ChromeOptions(), null, env(), env()));
        try (Capture c = new Capture()) {
            Map<String, String> e = bs();
            e.put("SELENIUM_REMOTE_URL", "http://u:" + KEY + "@grid:4444");
            assertNull(CloudDrivers.createIfSelected("chrome", new ChromeOptions(), null, e, env()));
            String t = c.text();
            assertTrue(t.contains("SELENIUM_REMOTE_URL is set and wins over GATF_CLOUD"), t);
            assertClean("grid-wins notice", t);
        }
        Map<String, String> p = env("gatf.selenium.remoteUrl", "http://grid:4444");
        assertNull(CloudDrivers.createIfSelected("chrome", new ChromeOptions(), null, bs(), p));
    }

    @Test
    public void sessionUsesBasicAuthAndStatusReachesTheDashboard() throws Exception {
        try (Hub hub = new Hub(); Capture c = new Capture()) {
            CloudDrivers.HUB_FOR_TESTS = hub.url();
            Map<String, String> e = bs();
            e.put("GATF_WITH_GRID", "true");
            WebDriver d = CloudDrivers.createIfSelected("chrome", new ChromeOptions(), null, e, env());
            assertTrue(d instanceof org.openqa.selenium.remote.RemoteWebDriver);
            assertTrue(hub.auths.contains("Basic " + B64), "Basic auth after the 401 challenge: " + hub.auths.size());
            String newSession = hub.requests.stream().filter(r -> r.startsWith("POST /session ")).findFirst().orElse("");
            assertTrue(newSession.contains("bstack:options") && newSession.contains("seleniumCdp"), newSession);

            SeleniumTestSession sess = new SeleniumTestSession();
            sess.___d___.add(d);
            sess.___dqs___.add(false);
            CloudDrivers.onResult(sess, "/work/tests/trace-smoke.sel");
            CloudDrivers.onResult(sess, "/work/tests/trace-smoke.sel");   // unchanged: not re-sent
            assertEquals(2, hub.scripts.size(), String.valueOf(hub.scripts));
            assertTrue(hub.scripts.get(0).contains("setSessionName") && hub.scripts.get(0).contains("trace-smoke.sel"), hub.scripts.get(0));
            assertTrue(hub.scripts.get(1).contains("setSessionStatus") && hub.scripts.get(1).contains("passed"), hub.scripts.get(1));
            d.quit();

            String t = c.text();
            assertTrue(t.contains("[gatf] device cloud browserstack: chrome latest on Windows 11"), t);
            assertTrue(t.contains("GATF_CLOUD wins over GATF_WITH_GRID"), t);
            assertClean("console/log output", t);
            for (String r : hub.requests) assertClean("request " + r.split(" ")[1], r);
        } finally {
            CloudDrivers.HUB_FOR_TESTS = null;
        }
    }

    @Test
    public void failuresNeverLeakAndCallbackNeverThrows() throws Exception {
        try (Hub hub = new Hub(); Capture c = new Capture()) {
            CloudDrivers.HUB_FOR_TESTS = hub.url();
            hub.mode = "fail-session";
            IllegalStateException x = assertThrows(IllegalStateException.class,
                    () -> CloudDrivers.createIfSelected("chrome", new ChromeOptions(), null, bs(), env()));
            assertTrue(x.getMessage().contains("browserstack"), x.getMessage());
            assertClean("session error", x.getMessage() + " " + x.getCause());
            java.io.StringWriter sw = new java.io.StringWriter();
            x.printStackTrace(new java.io.PrintWriter(sw));
            assertClean("session error stack", sw.toString());

            hub.mode = "deny";
            IllegalStateException y = assertThrows(IllegalStateException.class,
                    () -> CloudDrivers.createIfSelected("firefox", new FirefoxOptions(), null, bs(), env()));
            java.io.StringWriter sw2 = new java.io.StringWriter();
            y.printStackTrace(new java.io.PrintWriter(sw2));
            assertClean("auth-denied error stack", sw2.toString());

            hub.mode = "ok";
            WebDriver d = CloudDrivers.createIfSelected("chrome", new ChromeOptions(), null, bs(), env());
            hub.mode = "fail-execute";
            SeleniumTestSession sess = new SeleniumTestSession();
            sess.___d___.add(d);
            sess.___dqs___.add(false);
            CloudDrivers.onResult(sess, "x.sel");   // must not throw
            CloudDrivers.onResult(null, "x.sel");
            hub.mode = "ok";
            d.quit();
            assertClean("console/log output", c.text());
            for (String r : hub.requests) assertClean("request", r);
        } finally {
            CloudDrivers.HUB_FOR_TESTS = null;
        }
    }

    @Test
    public void optionsJsonIsParsedAndBadJsonIsNotEchoed() {
        assertEquals(Boolean.TRUE, CloudDrivers.parseOptions(env("GATF_CLOUD_OPTIONS", "{\"local\": true}"), env()).get("local"));
        assertNull(CloudDrivers.parseOptions(env(), env()));
        CloudPresetsLike.err(() -> CloudDrivers.parseOptions(env("GATF_CLOUD_OPTIONS", "{\"accessKey\": \"" + KEY), env()));
        CloudPresetsLike.err(() -> CloudDrivers.parseOptions(env("GATF_CLOUD_OPTIONS", "[\"" + KEY + "\"]"), env()));
    }

    static final class CloudPresetsLike {
        static void err(Runnable r) {
            com.gatf.selenium.cloud.CloudPresets.CloudError x = assertThrows(com.gatf.selenium.cloud.CloudPresets.CloudError.class, r::run);
            assertClean("options error", x.getMessage());
        }
    }

    @Test
    public void failureClassification() {
        assertNull(CloudDrivers.failure(true, null));
        assertNull(CloudDrivers.failure(false, new PassSubTestException("ok")));
        assertNull(CloudDrivers.failure(false, new WarnSubTestException("meh")));
        assertEquals("boom", CloudDrivers.failure(false, new RuntimeException("boom\nat line 3")));
        assertEquals("IllegalStateException", CloudDrivers.failure(false, new IllegalStateException()));
        assertEquals("failed", CloudDrivers.failure(false, null));
    }

    @Test
    public void codegenRoutingFollowsTheEnvironment() {
        // the real process env: GATF_CLOUD is not set in the build, so plain keys keep their local driver
        if (System.getenv("GATF_CLOUD") == null && System.getProperty("gatf.cloud") == null) {
            assertFalse(CloudDrivers.routesPlainKeys());
        }
    }
}
