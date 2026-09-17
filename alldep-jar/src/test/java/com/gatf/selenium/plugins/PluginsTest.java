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
package com.gatf.selenium.plugins;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

/** Q1: the built-in seleasy plugins (curl / json / xml). Args follow SeleniumTest.pluginize: [first-set..., List<List<Object>>, index, context]. */
class PluginsTest {

    private static Object[] args(Object... firstSet) {
        Object[] a = Arrays.copyOf(firstSet, firstSet.length + 3);
        a[firstSet.length] = new ArrayList<List<Object>>();
        a[firstSet.length + 1] = 0;
        a[firstSet.length + 2] = null;   // no AcceptanceTestContext needed by these paths
        return a;
    }

    @Test
    void curlHeaderSplitAcceptsBothSeparators() {
        assertThat(CurlPlugin.splitHeader("Content-Type: application/json")).containsExactly("Content-Type", "application/json");
        assertThat(CurlPlugin.splitHeader("h1=a")).containsExactly("h1", "a");
        assertThat(CurlPlugin.splitHeader("Authorization: Bearer abc==")).containsExactly("Authorization", "Bearer abc==");
        assertThat(CurlPlugin.splitHeader("X-Url=http://a:8080/x")).containsExactly("X-Url", "http://a:8080/x");
        assertThat(CurlPlugin.splitHeader("novalue")).isNull();
        assertThat(CurlPlugin.splitHeader(":x")).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void curlPostsHeadersAndBodyAndParsesJson() throws Exception {
        AtomicReference<String> gotCt = new AtomicReference<>(), gotAuth = new AtomicReference<>(), gotBody = new AtomicReference<>(),
                gotMethod = new AtomicReference<>();
        HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        s.createContext("/echo", ex -> {
            gotMethod.set(ex.getRequestMethod());
            gotCt.set(ex.getRequestHeaders().getFirst("Content-Type"));
            gotAuth.set(ex.getRequestHeaders().getFirst("Authorization"));
            gotBody.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] out = "{\"ok\":true,\"n\":2}".getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(201, out.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(out); }
        });
        s.start();
        try {
            String url = "http://127.0.0.1:" + s.getAddress().getPort() + "/echo";
            for (String method : new String[] { "post", "patch" }) {
                List<List<Object>> lst = new ArrayList<>();
                lst.add(new ArrayList<Object>(List.of("Content-Type: application/json", "Authorization=Bearer t:1")));
                lst.add(new ArrayList<Object>(List.of("{\"a\":1}")));
                Map<String, Object> resp = (Map<String, Object>) CurlPlugin.execute(new Object[] { method, url, lst, 0, null });

                assertThat(gotMethod.get()).isEqualTo(method.toUpperCase());
                assertThat(gotCt.get()).isEqualTo("application/json");        // ':' form used to throw
                assertThat(gotAuth.get()).isEqualTo("Bearer t:1");
                assertThat(gotBody.get()).isEqualTo("{\"a\":1}");              // PATCH bodies used to be dropped
                assertThat(resp.get("statusCode")).isEqualTo(201);
                assertThat((Map<String, Object>) resp.get("json")).containsEntry("ok", true);
            }
        } finally {
            s.stop(0);
        }
    }

    @Test
    void jsonPathAcceptsEitherArgumentOrderAndParsedDocuments() throws Exception {
        String json = "{\"a\":{\"b\":7}}";
        Object parsed = JsonPlugin.read(new Object[] { json });
        assertThat(parsed).isInstanceOf(Map.class);
        assertThat(JsonPlugin.path(args(json, "$.a.b"))).isEqualTo(7);      // documented order
        assertThat(JsonPlugin.path(args("$.a.b", json))).isEqualTo(7);      // original code's order
        assertThat(JsonPlugin.path(args(parsed, "a.b"))).isEqualTo(7);      // doc from jsonread (used to ClassCastException)
    }

    @Test
    void jsonWriteReturnsTextAndAcceptsEitherFileOrder(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        Map<String, Object> m = new HashMap<>();
        m.put("k", "v");
        assertThat(JsonPlugin.write(new Object[] { m, new ArrayList<>(), 0, null })).isEqualTo("{\"k\":\"v\"}");
        java.nio.file.Path f1 = dir.resolve("a.json"), f2 = dir.resolve("b.json");
        JsonPlugin.write(args(m, f1.toString()));                             // code order {object} {file}
        JsonPlugin.write(args(f2.toString(), m));                             // documented order {file} {object}
        assertThat(java.nio.file.Files.readString(f1)).isEqualTo("{\"k\":\"v\"}");
        assertThat(java.nio.file.Files.readString(f2)).isEqualTo("{\"k\":\"v\"}");
    }

    @Test
    void xmlPathAcceptsEitherArgumentOrder() throws Exception {
        String xml = "<o><a>abc</a><b>1</b></o>";
        assertThat(XmlPlugin.path(args(xml, "/o/a"))).isEqualTo("abc");     // documented order
        assertThat(XmlPlugin.path(args("/o/a", xml))).isEqualTo("abc");     // original code's order
    }
}
