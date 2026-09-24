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
package com.gatf.executor.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** Q8 — cookie matching/merging used by the UI↔API session bridge and the API executor. */
class CookieRulesTest {

    private static final long NOW = 1_800_000_000L;

    private static CookieRules.C c(String n, String v, String d, String p, boolean hostOnly) {
        CookieRules.C c = new CookieRules.C(n, v, d, p);
        c.hostOnly = hostOnly;
        return c;
    }

    @Test
    void domainAndPathMatching() {
        assertThat(CookieRules.domainMatch("api.a.com", ".a.com", false)).isTrue();
        assertThat(CookieRules.domainMatch("api.a.com", "a.com", true)).isFalse();
        assertThat(CookieRules.domainMatch("evila.com", "a.com", false)).isFalse();
        assertThat(CookieRules.domainMatch("1.2.3.4", "3.4", false)).isFalse();
        assertThat(CookieRules.pathMatch("/api/x", "/api")).isTrue();
        assertThat(CookieRules.pathMatch("/apix", "/api")).isFalse();
        assertThat(CookieRules.defaultPath("/a/b/c")).isEqualTo("/a/b");
        assertThat(CookieRules.defaultPath("/a")).isEqualTo("/");
    }

    @Test
    void headerAppliesSecureExpiryAndOrdersByPath() {
        CookieRules.C sid = c("sid", "1", "a.com", "/", false);
        sid.secure = true;
        CookieRules.C pref = c("pref", "x", "a.com", "/api", true);
        CookieRules.C old = c("old", "y", "a.com", "/", true);
        old.expiry = NOW - 1;
        List<CookieRules.C> all = List.of(sid, pref, old);
        assertThat(CookieRules.header(all, URI.create("https://a.com/api/v1"), NOW)).isEqualTo("pref=x; sid=1");
        assertThat(CookieRules.header(all, URI.create("http://a.com/api/v1"), NOW)).isEqualTo("pref=x");
        assertThat(CookieRules.header(all, URI.create("https://api.a.com/api"), NOW)).isEqualTo("sid=1");
        CookieRules.C local = c("t", "1", "localhost", "/", true);
        local.secure = true;   // browsers treat http://localhost as secure
        assertThat(CookieRules.header(List.of(local), URI.create("http://localhost:8080/"), NOW)).isEqualTo("t=1");
    }

    @Test
    void mergeKeepsOrderAndOverridesByName() {
        assertThat(CookieRules.merge("a=1; b=2", "b=3; c=4")).isEqualTo("a=1; b=3; c=4");
        assertThat(CookieRules.merge(null, Map.of("s", "v"))).isEqualTo("s=v");
    }

    @Test
    void setCookieBecomesBrowserCookie() {
        List<CookieRules.C> sc = CookieRules.fromSetCookie(List.of(
                "SESSION=abc; Path=/; HttpOnly; Secure; SameSite=Lax",
                "tk=9; Domain=.a.com; Max-Age=60",
                "gone=; Max-Age=0",
                "evil=1; Domain=b.com",
                "p=1",
                "e=1; Expires=Thu, 01 Jan 1970 00:00:10 GMT"), URI.create("https://api.a.com/v1/login"), NOW);
        assertThat(sc).extracting(x -> x.name).containsExactly("SESSION", "tk", "gone", "p", "e");   // foreign domain rejected
        assertThat(sc.get(0).hostOnly).isTrue();
        assertThat(sc.get(0).domain).isEqualTo("api.a.com");
        assertThat(sc.get(0).httpOnly && sc.get(0).secure).isTrue();
        assertThat(sc.get(0).sameSite).isEqualTo("Lax");
        assertThat(sc.get(1).hostOnly).isFalse();
        assertThat(sc.get(1).expiry).isEqualTo(NOW + 60);
        assertThat(sc.get(2).deleted).isTrue();
        assertThat(sc.get(3).path).isEqualTo("/v1");
        assertThat(sc.get(4).deleted).isTrue();
    }

    @Test
    void sessionBridgeModes() {
        assertThat(SessionBridge.effectiveMode(null)).isEqualTo("sync");
        assertThat(SessionBridge.normalizeMode(" SEND ")).isEqualTo("send");
        assertThat(SessionBridge.effectiveMode("both")).isEqualTo("sync");
        assertThatThrownBy(() -> SessionBridge.normalizeMode("both")).hasMessageContaining("sync, send, off");
        GatfExecutorConfig cfg = new GatfExecutorConfig();
        cfg.setBrowserApiSession("Off");
        assertThat(cfg.getBrowserApiSession()).isEqualTo("off");
        assertThatThrownBy(() -> cfg.setBrowserApiSession("x")).isInstanceOf(IllegalArgumentException.class);
    }
}
