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

import java.net.URI;

import org.junit.jupiter.api.Test;

/** E9: where the browserApiStorageToken may be sent. */
class SessionBridgeTest {

    private static boolean ok(String req, String page, String... extra) {
        return SessionBridge.tokenAllowed(URI.create(req), page == null ? null : URI.create(page), extra);
    }

    @Test
    void tokenGoesToThePagesHostOnly() {
        assertThat(ok("https://app.shop.test/api/x", "https://app.shop.test/home")).isTrue();       // same host
        assertThat(ok("https://app.shop.test:8443/v1", "https://app.shop.test/")).isTrue();         // ports do not matter
        assertThat(ok("https://api.shop.test/v1", "https://app.shop.test/")).isFalse();             // sibling host: list it
        assertThat(ok("https://api.shop.test/v1", "https://app.shop.test/", "*.shop.test")).isTrue();
        assertThat(ok("https://shop.test/v1", "https://app.shop.test/")).isFalse();
        assertThat(ok("https://evil.test/steal", "https://app.shop.test/")).isFalse();
        assertThat(ok("https://shop.test.evil.io/", "https://app.shop.test/")).isFalse();
        assertThat(ok("https://api.shop.test/", null)).isFalse();                                   // no page open
    }

    @Test
    void sharedSuffixesIpsAndLocalhostAreExact() {
        assertThat(ok("https://other.co.uk/", "https://shop.co.uk/")).isFalse();
        assertThat(ok("https://evil.gov.in/", "https://nhm.gov.in/")).isFalse();
        assertThat(ok("https://evil.github.io/", "https://mine.github.io/")).isFalse();
        assertThat(ok("https://b.herokuapp.com/", "https://a.herokuapp.com/")).isFalse();
        assertThat(ok("https://api.nhm.gov.in/", "https://nhm.gov.in/")).isFalse();
        assertThat(ok("http://10.0.0.6:8080/api", "http://10.0.0.5/")).isFalse();
        assertThat(ok("http://10.0.0.5:8080/api", "http://10.0.0.5/")).isTrue();
        assertThat(ok("http://localhost:9000/api", "http://localhost:3000/")).isTrue();
        assertThat(ok("http://127.0.0.1:9000/api", "http://localhost:3000/")).isFalse();
    }

    @Test
    void neverDowngradesAndExtraHostsAreOptIn() {
        assertThat(ok("http://app.shop.test/api", "https://app.shop.test/")).isFalse();
        assertThat(ok("http://api.other.test/", "https://app.shop.test/", "api.other.test")).isFalse();   // not even listed hosts
        assertThat(ok("https://api.other.test/", "https://app.shop.test/", "API.other.test")).isTrue();
        assertThat(ok("https://x.api.other.test/", "https://app.shop.test/", "*.api.other.test")).isTrue();
        assertThat(ok("https://api.other.test/", "https://app.shop.test/", ".api.other.test")).isTrue();
        assertThat(ok("https://evilapi.other.test/", "https://app.shop.test/", "*.api.other.test", "", null)).isFalse();
        assertThat(ok("https://api.other.test/", null, "api.other.test")).isTrue();
        assertThat(ok("https://app.shop.test/api", "http://app.shop.test/")).isTrue();              // upgrade is fine
    }
}
