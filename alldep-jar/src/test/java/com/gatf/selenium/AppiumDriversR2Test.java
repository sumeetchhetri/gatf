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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.UsernameAndPassword;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.remote.http.ClientConfig;

import com.gatf.selenium.CloudDriversTest.Capture;
import com.gatf.selenium.CloudDriversTest.Hub;
import com.gatf.selenium.mobile.MobilePlatform;
import com.gatf.selenium.mobile.TouchGestures;

/**
 * Δ158 R2: Appium session acquisition. The java-client driver is swapped (AppiumDrivers.FACTORY) for a plain Selenium
 * RemoteWebDriver built from the same ClientConfig and Capabilities, against Δ157's fake W3C hub, which demands Basic
 * auth and records every request. Every output (stdout, JUL, errors, requests, ClientConfig#toString, capabilities) is
 * checked for the access key, the user and the encoded pair.
 */
public class AppiumDriversR2Test {

    static final class Recorder implements AppiumDrivers.Factory {
        final List<Object[]> calls = new ArrayList<Object[]>();
        RuntimeException fail;
        boolean offline;
        @Override
        public WebDriver create(MobilePlatform p, ClientConfig cc, Capabilities caps) {
            calls.add(new Object[] {p, cc, caps});
            if (fail != null) throw fail;
            if (offline) {   // a local Appium server: the recording W3C endpoint of AppiumTouchR2Test, no HTTP
                AppiumTouchR2Test.Wire w = new AppiumTouchR2Test.Wire(AppiumTouchR2Test.caps(p.platformName, null));
                return new AppiumTouchR2Test.Device(w.executor(), w.caps);
            }
            try {
                return new RemoteWebDriver(cc.baseUri().toURL(), caps, cc);
            } catch (java.net.MalformedURLException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    static Map<String, String> cfg() {
        Map<String, String> m = new LinkedHashMap<String, String>();
        m.put("app", "/home/me/demo.apk");
        m.put("udid", "emulator-5554");
        m.put("appPackage", "com.example.demo");
        return m;
    }

    static Map<String, String> bsApp() {
        Map<String, String> e = CloudDriversTest.bs();
        e.put("GATF_CLOUD_DEVICE", "Google Pixel 7");
        e.put("GATF_CLOUD_OS_VERSION", "13.0");
        e.put("GATF_CLOUD_APP", "bs://f00d");
        return e;
    }

    @Test
    public void localAppiumServerWithAppium2Capabilities() throws Exception {
        Recorder r = new Recorder();
        r.offline = true;
        try (Capture c = new Capture()) {
            AppiumDrivers.FACTORY = r;
            Map<String, String> env = new HashMap<String, String>();
            env.put("GATF_APPIUM_URL", "http://127.0.0.1:4799");
            WebDriver d = AppiumDrivers.create("appium-android", "http://cfg-host:4723", cfg(), "14", env, new HashMap<String, String>());
            assertTrue(d instanceof RemoteWebDriver);
            ClientConfig cc = (ClientConfig) r.calls.get(0)[1];
            assertEquals("http://127.0.0.1:4799", cc.baseUri().toString());
            assertNull(cc.credentials(), "a local Appium server gets no credentials");
            assertEquals(MobilePlatform.ANDROID, r.calls.get(0)[0]);
            String json = TouchGestures.json(((Capabilities) r.calls.get(0)[2]).asMap());
            // Selenium normalises platformName to its Platform enum (ANDROID); Appium matches platform names case-insensitively
            for (String k : new String[] {"\"platformName\":\"ANDROID\"", "\"appium:automationName\":\"UiAutomator2\"", "\"appium:platformVersion\":\"14\"",
                    "\"appium:app\":\"/home/me/demo.apk\"", "\"appium:udid\":\"emulator-5554\"", "\"appium:appPackage\":\"com.example.demo\""}) {
                assertTrue(json.contains(k), k + " in " + json);
            }
            assertTrue(!json.contains("\"deviceName\"") && !json.contains("\"app\""), json);
            assertTrue(c.text().contains("[gatf] appium Android via http://127.0.0.1:4799 (session sid-158)"), c.text());
            assertTrue(CloudDrivers.SESSIONS.get("sid-158") == null, "a local session is not a cloud session");
            // without GATF_APPIUM_URL the config url is used, else Appium 2's default
            AppiumDrivers.create("appium-ios", "http://cfg-host:4723/wd/hub", null, null, new HashMap<String, String>(), new HashMap<String, String>());
            assertEquals("http://cfg-host:4723/wd/hub", ((ClientConfig) r.calls.get(1)[1]).baseUri().toString());
            AppiumDrivers.create("appium-ios", null, null, null, new HashMap<String, String>(), new HashMap<String, String>());
            assertEquals("http://127.0.0.1:4723", ((ClientConfig) r.calls.get(2)[1]).baseUri().toString());
            assertEquals(MobilePlatform.IOS, r.calls.get(2)[0]);
            d.quit();
        } finally {
            AppiumDrivers.FACTORY = AppiumDrivers.DEFAULT;
        }
    }

    @Test
    public void realDeviceCloudSessionUsesBasicAuthAndReachesTheDashboard() throws Exception {
        Recorder r = new Recorder();
        try (Hub hub = new Hub(); Capture c = new Capture()) {
            AppiumDrivers.FACTORY = r;
            CloudDrivers.HUB_FOR_TESTS = hub.url();
            WebDriver d = AppiumDrivers.create("appium-android", null, cfg(), null, bsApp(), new HashMap<String, String>());
            ClientConfig cc = (ClientConfig) r.calls.get(0)[1];
            assertTrue(cc.credentials() instanceof UsernameAndPassword);
            assertEquals(CloudDriversTest.USER, ((UsernameAndPassword) cc.credentials()).username());
            CloudDriversTest.assertClean("ClientConfig#toString", cc.toString());
            assertTrue(hub.auths.contains("Basic " + CloudDriversTest.B64), "Basic auth after the 401 challenge");
            String newSession = hub.requests.stream().filter(q -> q.startsWith("POST /session ")).findFirst().orElse("");
            assertTrue(newSession.contains("bstack:options") && newSession.contains("Google Pixel 7") && newSession.contains("f00d"), newSession);
            assertTrue(!newSession.contains("demo.apk") && !newSession.contains("emulator-5554"), "local-only capabilities never reach the cloud: " + newSession);
            assertTrue(CloudDrivers.SESSIONS.containsKey("cloud-1"));

            SeleniumTestSession sess = new SeleniumTestSession();
            sess.___d___.add(d);
            sess.___dqs___.add(false);
            CloudDrivers.onResult(sess, "/work/tests/25-mobile-touch.sel");
            assertEquals(2, hub.scripts.size(), String.valueOf(hub.scripts));
            assertTrue(hub.scripts.get(0).contains("setSessionName") && hub.scripts.get(0).contains("25-mobile-touch.sel"), hub.scripts.get(0));
            assertTrue(hub.scripts.get(1).contains("setSessionStatus") && hub.scripts.get(1).contains("passed"), hub.scripts.get(1));
            d.quit();

            String t = c.text();
            assertTrue(t.contains("[gatf] device cloud browserstack: Android 13.0 on Google Pixel 7 (real device) via https://hub-cloud.browserstack.com/wd/hub"), t);
            CloudDriversTest.assertClean("console/log output", t);
            for (String q : hub.requests) CloudDriversTest.assertClean("request", q);
        } finally {
            AppiumDrivers.FACTORY = AppiumDrivers.DEFAULT;
            CloudDrivers.HUB_FOR_TESTS = null;
            CloudDrivers.SESSIONS.remove("cloud-1");
        }
    }

    @Test
    public void explicitAppiumUrlWinsAndFailuresAreRedacted() throws Exception {
        Recorder r = new Recorder();
        try (Hub hub = new Hub(); Capture c = new Capture()) {
            AppiumDrivers.FACTORY = r;
            Map<String, String> e = bsApp();
            e.put("GATF_APPIUM_URL", hub.url());
            r.offline = true;
            AppiumDrivers.create("appium-ios", null, null, null, e, new HashMap<String, String>());
            r.offline = false;
            assertNull(((ClientConfig) r.calls.get(0)[1]).credentials());
            assertTrue(c.text().contains("GATF_APPIUM_URL is set and wins over GATF_CLOUD"), c.text());

            CloudDrivers.HUB_FOR_TESTS = hub.url();
            r.fail = new IllegalStateException("bad session for " + CloudDriversTest.KEY + "\nmore");
            IllegalStateException x = assertThrows(IllegalStateException.class,
                    () -> AppiumDrivers.create("appium-android", null, cfg(), null, bsApp(), new HashMap<String, String>()));
            assertTrue(x.getMessage().startsWith("Could not start an browserstack: Android"), x.getMessage());
            CloudDriversTest.assertClean("session error", x.getMessage());
            Map<String, String> noApp = bsApp();
            noApp.remove("GATF_CLOUD_APP");
            RuntimeException y = assertThrows(RuntimeException.class,
                    () -> AppiumDrivers.create("appium-android", null, cfg(), null, noApp, new HashMap<String, String>()));
            assertTrue(y.getMessage().contains("needs GATF_CLOUD_APP"), y.getMessage());
            assertThrows(IllegalArgumentException.class, () -> AppiumDrivers.create("chrome", null, null, null, new HashMap<String, String>(), new HashMap<String, String>()));
            CloudDriversTest.assertClean("console/log output", c.text());
        } finally {
            AppiumDrivers.FACTORY = AppiumDrivers.DEFAULT;
            CloudDrivers.HUB_FOR_TESTS = null;
            CloudDrivers.SESSIONS.remove("cloud-1");
        }
    }
}
