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
package com.gatf.selenium.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** M4 — HAR replay (method + URL) and the BiDi command mapping. */
class HarRoutesTest {

    private static byte[] har() throws Exception {
        try (InputStream in = HarRoutesTest.class.getResourceAsStream("/route/checkout.har")) {
            return in.readAllBytes();
        }
    }

    private static RouteTable table(String statement) throws Exception {
        byte[] h = har();
        RouteTable t = new RouteTable();
        RouteSpec.apply(RouteSpec.parse(statement), t, f -> h);
        return t;
    }

    @Test
    void replaysByMethodAndUrl() throws Exception {
        RouteTable t = table("route from har checkout.har");
        assertThat(t.describe()).containsExactly("har checkout.har (3 responses)");   // 304 and status 0 skipped
        Route v2 = t.match("GET", "https://shop.test/api/cart?v=2");
        assertThat(new String(v2.body, StandardCharsets.UTF_8)).isEqualTo("{\"items\":3}");   // exact URL
        Route other = t.match("GET", "https://shop.test/api/cart?v=9");
        assertThat(new String(other.body, StandardCharsets.UTF_8)).isEqualTo("{\"items\":2}"); // same URL without query: first entry
        assertThat(other.header("Content-Encoding")).isNull();                                   // HAR text is already decoded
        assertThat(other.header("content-length")).isNull();
        Route post = t.match("POST", "https://shop.test/api/checkout");
        assertThat(post.status).isEqualTo(201);
        assertThat(new String(post.body, StandardCharsets.UTF_8)).isEqualTo("{\"order\":7}");    // base64 content
        assertThat(post.header("Content-Type")).isEqualTo("application/json");                    // from content.mimeType
        assertThat(post.source).isEqualTo("har checkout.har");
        assertThat(t.match("GET", "https://shop.test/api/checkout")).isNull();                    // method matters
        assertThat(t.match("GET", "https://shop.test/logo.png")).isNull();                        // not recorded: fallback
    }

    @Test
    void urlFilterAndNotFoundAbort() throws Exception {
        RouteTable t = table("route from har checkout.har url **/api/** notfound abort");
        assertThat(t.match("GET", "https://shop.test/api/unknown").action).isEqualTo(Route.Action.ABORT);
        assertThat(t.match("GET", "https://shop.test/index.html")).isNull();                      // outside the url glob
        assertThat(RouteSpec.apply(RouteSpec.parse("unroute **/api/**"), t, f -> null)).endsWith("1 rule(s) removed");
    }

    @Test
    void badHarIsReported() {
        RouteTable t = new RouteTable();
        assertThatThrownBy(() -> RouteSpec.apply(RouteSpec.parse("route from har x.har"), t, f -> "{\"log\":{}}".getBytes(StandardCharsets.UTF_8)))
                .hasMessageContaining("not a HAR file");
        assertThatThrownBy(() -> RouteSpec.apply(RouteSpec.parse("route from har x.har"), t, f -> "nope".getBytes(StandardCharsets.UTF_8)))
                .hasMessageContaining("not valid JSON");
    }

    @Test
    @SuppressWarnings("unchecked")
    void bidiCommands() {
        Map<String, String> req = new LinkedHashMap<String, String>();
        req.put("accept", "*/*");
        assertThat(BidiRoutes.interceptParams()).containsEntry("phases", List.of("beforeRequestSent"));
        Object[] none = BidiRoutes.command(null, "b1", req);
        assertThat(none[0]).isEqualTo("network.continueRequest");
        assertThat((Map<String, Object>) none[1]).containsOnlyKeys("request");
        Route f = RouteSpec.parse("route **/x fulfill 404 'gone' type text/plain").route;
        f.body = "gone".getBytes(StandardCharsets.UTF_8);
        Map<String, Object> fp = (Map<String, Object>) BidiRoutes.command(f, "b2", req)[1];
        assertThat(fp).containsEntry("request", "b2").containsEntry("statusCode", 404);
        assertThat((Map<String, Object>) fp.get("body")).containsEntry("type", "base64").containsEntry("value", "Z29uZQ==");
        List<Map<String, Object>> hs = (List<Map<String, Object>>) fp.get("headers");
        assertThat(hs.get(0)).containsEntry("name", "Content-Type");
        assertThat((Map<String, Object>) hs.get(0).get("value")).containsEntry("type", "string").containsEntry("value", "text/plain");
        assertThat(BidiRoutes.command(RouteSpec.parse("route **/x abort timedout").route, "b3", req)[0]).isEqualTo("network.failRequest");
        Map<String, Object> cp = (Map<String, Object>) BidiRoutes.command(RouteSpec.parse("route **/x continue header 'Accept: application/json'").route, "b4", req)[1];
        assertThat((List<Map<String, Object>>) cp.get("headers")).hasSize(1);   // overridden, not duplicated
    }
}
