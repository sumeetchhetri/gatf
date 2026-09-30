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

    private static String entry(String method, String url, String postData, String text) {
        return "{\"request\":{\"method\":\"" + method + "\",\"url\":\"" + url + "\"" + (postData == null ? "" : ",\"postData\":{\"text\":\"" + postData.replace("\"", "\\\"") + "\"}")
                + "},\"response\":{\"status\":200,\"headers\":[],\"content\":{\"text\":\"" + text + "\"}}}";
    }

    private static RouteTable inline(String... entries) {
        String har = "{\"log\":{\"version\":\"1.2\",\"entries\":[" + String.join(",", entries) + "]}}";
        RouteTable t = new RouteTable();
        RouteSpec.apply(RouteSpec.parse("route from har x.har notfound abort"), t, f -> har.getBytes(StandardCharsets.UTF_8));
        return t;
    }

    private static String body(Route r) {
        return new String(r.body, StandardCharsets.UTF_8);
    }

    @Test
    void queryOverlapPicksTheFallback() {
        RouteTable t = inline(entry("GET", "https://s.test/s", null, "C"), entry("GET", "https://s.test/s?q=a&page=1", null, "A"),
                entry("GET", "https://s.test/s?q=b&page=2", null, "B"));
        assertThat(body(t.match("GET", "https://s.test/s?q=b&page=2"))).isEqualTo("B");        // exact
        assertThat(body(t.match("GET", "https://s.test/s?q=b&page=2&z=1"))).isEqualTo("B");    // 2 matched, 0 missing
        assertThat(body(t.match("GET", "https://s.test/s?page=1&q=a"))).isEqualTo("A");        // order does not matter
        assertThat(body(t.match("GET", "https://s.test/s?q=zz"))).isEqualTo("C");              // nothing shared: no recorded params wins
        assertThat(body(t.match("GET", "https://s.test/s?page=1"))).isEqualTo("C");            // A: 1 matched - 1 missing = 0, tie -> earliest
        assertThat(t.match("GET", "https://s.test/other")).isSameAs(t.match("GET", "https://s.test/other2"));   // one prebuilt abort
    }

    @Test
    void postBodyPicksBetweenSameUrlEntries() {
        RouteTable t = inline(entry("POST", "https://s.test/login", "{\"u\":\"a\"}", "A"), entry("POST", "https://s.test/login", "{\"u\":\"b\"}", "B"),
                entry("POST", "https://s.test/one", "{\"u\":\"a\"}", "ONE"));
        assertThat(body(t.match("POST", "https://s.test/login", "{\"u\":\"b\"}"))).isEqualTo("B");
        assertThat(body(t.match("POST", "https://s.test/login", "{\"u\":\"zz\"}"))).isEqualTo("A");   // no equal body: first
        assertThat(body(t.match("POST", "https://s.test/login"))).isEqualTo("A");                     // body unknown: first
        assertThat(body(t.match("POST", "https://s.test/one", "other"))).isEqualTo("ONE");             // one candidate: as before
    }

    @Test
    void repeatedSetCookieAndBadBase64() {
        String har = "{\"log\":{\"entries\":[{\"request\":{\"method\":\"GET\",\"url\":\"https://s.test/\"},\"response\":{\"status\":200,"
                + "\"headers\":[{\"name\":\"Set-Cookie\",\"value\":\"a=1\"},{\"name\":\"set-cookie\",\"value\":\"b=2\"}],\"content\":{\"text\":\"x\"}}}]}}";
        RouteTable t = new RouteTable();
        RouteSpec.apply(RouteSpec.parse("route from har c.har"), t, f -> har.getBytes(StandardCharsets.UTF_8));
        Route r = t.match("GET", "https://s.test/");
        assertThat(r.header("Set-Cookie")).isEqualTo("a=1");
        assertThat(r.extraHeaders).extracting(h -> h[0] + ": " + h[1]).containsExactly("set-cookie: b=2");
        String bad = "{\"log\":{\"entries\":[" + entry("GET", "https://s.test/a", null, "x") + ",{\"request\":{\"method\":\"GET\",\"url\":\"https://s.test/b\"},"
                + "\"response\":{\"status\":200,\"headers\":[],\"content\":{\"text\":\"@@\",\"encoding\":\"base64\"}}}]}}";
        assertThatThrownBy(() -> RouteSpec.apply(RouteSpec.parse("route from har bad.har"), new RouteTable(), f -> bad.getBytes(StandardCharsets.UTF_8)))
                .hasMessageContaining("route from har bad.har: entry 1: Illegal base64");
    }

    @Test
    @SuppressWarnings("unchecked")
    void bidiBase64RequestHeadersPassThrough() {
        Map<String, String> req = new LinkedHashMap<String, String>();
        req.put("X-Bin", "gA==");
        req.put("Accept", "*/*");
        Route c = RouteSpec.parse("route **/x continue header 'Accept: text/html'").route;
        List<Map<String, Object>> hs = (List<Map<String, Object>>) ((Map<String, Object>) BidiRoutes.command(c, "b1", req, java.util.Set.of("X-Bin"))[1]).get("headers");
        assertThat(hs).extracting(h -> h.get("name") + "=" + h.get("value")).containsExactly("X-Bin={type=base64, value=gA==}", "Accept={type=string, value=text/html}");
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
