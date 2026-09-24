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

import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chromium.HasCdp;

import com.gatf.executor.core.SessionBridge;

/** Q8 — the browser side of the UI↔API session bridge, against fake CDP and WebDriver-only drivers. */
class BrowserSessionTest {

    /** Fake driver: CDP commands are logged (Network.getCookies answers from {@code cdpJar}); WebDriver cookies live in {@code jar}. */
    private static WebDriver driver(String url, boolean cdp, List<String> log, List<Map<String, Object>> cdpJar, Set<Cookie> jar) {
        WebDriver.Options opts = (WebDriver.Options) Proxy.newProxyInstance(BrowserSessionTest.class.getClassLoader(),
                new Class<?>[] { WebDriver.Options.class }, (p, m, a) -> {
                    switch (m.getName()) {
                        case "getCookies": return jar;
                        case "addCookie": jar.removeIf(k -> k.getName().equals(((Cookie) a[0]).getName())); jar.add((Cookie) a[0]); return null;
                        case "deleteCookieNamed": jar.removeIf(k -> k.getName().equals(a[0])); return null;
                        default: throw new UnsupportedOperationException(m.getName());
                    }
                });
        Class<?>[] ifs = cdp ? new Class<?>[] { WebDriver.class, HasCdp.class } : new Class<?>[] { WebDriver.class };
        return (WebDriver) Proxy.newProxyInstance(BrowserSessionTest.class.getClassLoader(), ifs, (p, m, a) -> {
            switch (m.getName()) {
                case "getCurrentUrl": return url;
                case "manage": return opts;
                case "executeCdpCommand":
                    log.add(a[0] + " " + new java.util.TreeMap<Object, Object>((Map<?, ?>) a[1]));
                    return "Network.getCookies".equals(a[0]) ? Map.of("cookies", cdpJar) : Map.of();
                case "hashCode": return System.identityHashCode(p);
                case "equals": return p == a[0];
                default: throw new UnsupportedOperationException(m.getName());
            }
        });
    }

    @Test
    void cdpReadsCookiesForTheApiUrlAndStoresSetCookies() {
        List<String> log = new ArrayList<String>();
        List<Map<String, Object>> cdpJar = new ArrayList<Map<String, Object>>();
        Map<String, Object> session = new HashMap<String, Object>(Map.of("name", "SESSION", "value", "s1", "domain", ".a.com",
                "path", "/", "secure", true, "httpOnly", true, "session", true));
        cdpJar.add(session);
        WebDriver d = driver("https://app.a.com/home", true, log, cdpJar, new LinkedHashSet<Cookie>());
        SessionBridge.Bridge prev = BrowserSession.enter(d, "sync");
        try {
            SessionBridge.Bridge b = SessionBridge.current();
            assertThat(b.cookieHeader(URI.create("https://api.a.com/v1/items"))).isEqualTo("SESSION=s1");
            b.onSetCookies(URI.create("https://api.a.com/v1/login"), List.of("tok=z; Path=/; HttpOnly; SameSite=strict", "d=1; Domain=a.com", "x=; Max-Age=0"));
            assertThat(log.get(1)).startsWith("Network.setCookie").contains("url=https://api.a.com/").contains("sameSite=Strict").doesNotContain("domain=");
            assertThat(log.get(2)).contains("domain=.a.com");
            assertThat(log.get(3)).startsWith("Network.deleteCookies");
        } finally {
            SessionBridge.restore(prev);
        }
        assertThat(SessionBridge.current()).isNull();
    }

    @Test
    void sendModeDoesNotPushAndOffInstallsNothing() {
        List<String> log = new ArrayList<String>();
        WebDriver d = driver("https://app.a.com/", true, log, new ArrayList<Map<String, Object>>(), new LinkedHashSet<Cookie>());
        SessionBridge.Bridge prev = BrowserSession.enter(d, "send");
        SessionBridge.current().onSetCookies(URI.create("https://api.a.com/"), List.of("a=1"));
        assertThat(log).isEmpty();
        SessionBridge.restore(prev);
        prev = BrowserSession.enter(d, "off");
        assertThat(SessionBridge.current()).isNull();
        SessionBridge.restore(prev);
        prev = BrowserSession.enter(null, "sync");
        assertThat(SessionBridge.current()).isNull();
        SessionBridge.restore(prev);
    }

    @Test
    void webDriverFallbackIsLimitedToTheOpenPagesDomain() {
        Set<Cookie> jar = new LinkedHashSet<Cookie>();
        jar.add(new Cookie.Builder("SESSION", "s1").domain(".a.com").path("/").isSecure(true).isHttpOnly(true).build());
        jar.add(new Cookie("local", "l", "app.a.com", "/", null));
        WebDriver d = driver("https://app.a.com/", false, new ArrayList<String>(), null, jar);
        SessionBridge.Bridge prev = BrowserSession.enter(d, "sync");
        try {
            SessionBridge.Bridge b = SessionBridge.current();
            assertThat(b.cookieHeader(URI.create("https://api.a.com/x"))).isEqualTo("SESSION=s1");
            assertThat(b.cookieHeader(URI.create("https://app.a.com/x"))).isEqualTo("SESSION=s1; local=l");
            assertThat(b.cookieHeader(URI.create("https://b.com/x"))).isNull();
            b.onSetCookies(URI.create("https://app.a.com/login"), List.of("tok=1; Path=/", "dom=2; Domain=a.com", "SESSION=; Max-Age=0"));
            b.onSetCookies(URI.create("https://other.com/login"), List.of("far=1"));   // other domain: skipped (logged)
            assertThat(jar).extracting(Cookie::getName).containsExactlyInAnyOrder("local", "tok", "dom");
        } finally {
            SessionBridge.restore(prev);
        }
    }
}
