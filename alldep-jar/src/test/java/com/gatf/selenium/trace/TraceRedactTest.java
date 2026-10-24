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
package com.gatf.selenium.trace;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Δ163o: no credentials in a trace's network.har (CLAUDE.md secrets rule; tracing is on by default since Δ163o). */
class TraceRedactTest {

    @TempDir
    Path tmp;

    @Test
    void credentialHeadersAreRedactedOthersKept() {
        for (String n : new String[] { "Authorization", "proxy-authorization", "Cookie", "Set-Cookie", "X-Api-Key", "x-auth-token",
                "X-CSRF-Token", "X-Session-Id", "x-amz-security-token", "X-Goog-Api-Key", "x-client-secret" }) {
            assertThat(TraceRedact.headerValue(n, "s3cr3t")).as(n).isEqualTo(TraceRedact.REDACTED);
        }
        for (String n : new String[] { "Content-Type", "Accept", "User-Agent", "Cache-Control", "Content-Length" }) {
            assertThat(TraceRedact.headerValue(n, "v")).as(n).isEqualTo("v");
        }
        assertThat(TraceRedact.headerValue("Authorization", null)).isNull();
    }

    @Test
    void theHarNeverContainsTheSecret() throws Exception {
        TraceConfig cfg = new TraceConfig().setEnableTrace(true);
        BrowserControl none = (BrowserControl) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { BrowserControl.class }, (p, m, a) -> m.getReturnType() == boolean.class ? false : null);
        NetworkRecorder r = new NetworkRecorder(none, tmp, cfg);
        Map<String, String> req = new LinkedHashMap<String, String>();
        req.put("Authorization", "Bearer TOPSECRET-1");
        req.put("Cookie", "sid=TOPSECRET-2");
        req.put("Accept", "application/json");
        Map<String, String> res = new LinkedHashMap<String, String>();
        res.put("Set-Cookie", "sid=TOPSECRET-3; HttpOnly");
        res.put("Content-Type", "application/json");
        r.onRequest("1", "https://example.test/api", "GET", req, 1);
        r.onResponse("1", 200, "OK", "application/json", res, 2);
        r.onFinished("1", 10, 3);
        r.close();
        Path har = tmp.resolve("network.har");
        r.buildHar(har, "chrome");
        String all = new String(Files.readAllBytes(har), StandardCharsets.UTF_8)
                + new String(Files.readAllBytes(tmp.resolve("network.ndjson")), StandardCharsets.UTF_8);
        assertThat(all).doesNotContain("TOPSECRET").contains(TraceRedact.REDACTED).contains("application/json");
    }
}
