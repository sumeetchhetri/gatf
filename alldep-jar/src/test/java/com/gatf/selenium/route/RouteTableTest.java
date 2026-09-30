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

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** M4 — URL globs, the first-match route table, the statement grammar and the CDP command mapping. */
class RouteTableTest {

    private static RouteTable table(String... statements) {
        RouteTable t = new RouteTable();
        for (String s : statements) RouteSpec.apply(RouteSpec.parse(s), t, f -> ("{\"file\":\"" + f + "\"}").getBytes(StandardCharsets.UTF_8));
        return t;
    }

    @Test
    void globsFollowPlaywright() {
        assertThat(Glob.toRegex("**/api/users").matcher("https://shop.test/v1/api/users").matches()).isTrue();
        assertThat(Glob.toRegex("**/api/users").matcher("https://shop.test/api/users?page=2").matches()).isFalse();
        assertThat(Glob.toRegex("**/api/users?page=*").matcher("https://shop.test/api/users?page=2").matches()).isTrue();   // ? is literal
        assertThat(Glob.toRegex("https://*.test/*.js").matcher("https://cdn.test/app.js").matches()).isTrue();
        assertThat(Glob.toRegex("https://*.test/*.js").matcher("https://cdn.test/lib/app.js").matches()).isFalse();   // * stops at /
        assertThat(Glob.toRegex("**/*.{png,jpg}").matcher("http://x/a/b.jpg").matches()).isTrue();
        assertThat(Glob.toRegex("/api/**").matcher("http://localhost:8080/api/x/y").matches()).isTrue();          // any origin
        assertThat(Glob.toRegex("**/a+b(c)").matcher("http://x/a+b(c)").matches()).isTrue();                     // regex chars literal
        assertThatThrownBy(() -> Glob.toRegex("**/{a,b")).hasMessageContaining("unbalanced");
        assertThat(Glob.toRegex("**/api/users*").matcher("https://shop.test/api/users?page=2").matches()).isTrue();
        assertThat(Glob.toRegex("**/a\\*b").matcher("http://x/a*b").matches()).isTrue();                          // \ escapes
        assertThat(Glob.toRegex("**/a\\*b").matcher("http://x/axb").matches()).isFalse();
        assertThat(Glob.toRegex("**/\\{x,y\\}").matcher("http://x/{x,y}").matches()).isTrue();
        assertThat(Glob.toRegex("**/a\\\\b").matcher("http://x/a\\b").matches()).isTrue();
    }

    @Test
    void firstMatchWinsAndMethodFilters() {
        RouteTable t = table("route POST **/api/users fulfill 201 '{\"id\":1}'",
                "route **/api/users fulfill 200 users.json",
                "route **/api/** abort",
                "route **/*.png abort blockedbyclient");
        Route post = t.match("POST", "https://s.test/api/users");
        assertThat(post.status).isEqualTo(201);
        assertThat(new String(post.body, StandardCharsets.UTF_8)).isEqualTo("{\"id\":1}");
        assertThat(post.header("content-type")).isEqualTo("application/json");
        Route get = t.match("GET", "https://s.test/api/users");
        assertThat(new String(get.body, StandardCharsets.UTF_8)).isEqualTo("{\"file\":\"users.json\"}");   // unquoted = file
        assertThat(t.match("GET", "https://s.test/api/orders").action).isEqualTo(Route.Action.ABORT);
        assertThat(t.match("GET", "https://s.test/img/a.png").reason).isEqualTo("blockedbyclient");
        assertThat(t.match("GET", "https://s.test/index.html")).isNull();
        assertThat(t.hits()).hasSize(4);
        assertThat(RouteSpec.apply(RouteSpec.parse("unroute **/api/users"), t, f -> null)).isEqualTo("unroute **/api/users: 2 rule(s) removed");
        assertThat(t.match("POST", "https://s.test/api/users").action).isEqualTo(Route.Action.ABORT);
        RouteSpec.apply(RouteSpec.parse("unroute all"), t, f -> null);
        assertThat(t.isEmpty()).isTrue();
    }

    @Test
    void statementGrammar() {
        Route r = RouteSpec.parse("route **/x fulfill 404 'nope' type text/plain header 'X-A: 1' header X-B: two").route;
        assertThat(r.status).isEqualTo(404);
        assertThat(r.headers).containsEntry("Content-Type", "text/plain").containsEntry("X-A", "1").containsEntry("X-B", "two");
        assertThatThrownBy(() -> RouteSpec.parse("route **/x fulfill 404 'nope' header X-B: two words")).hasMessageContaining("unexpected 'words'");
        Route c = RouteSpec.parse("route **/api/** continue header \"Authorization: Bearer abc\"").route;
        assertThat(c.action).isEqualTo(Route.Action.CONTINUE);
        assertThat(c.headers).containsEntry("Authorization", "Bearer abc");
        assertThat(RouteSpec.parse("route GET **/a abort").route.method).isEqualTo("GET");
        assertThatThrownBy(() -> RouteSpec.parse("route get fulfill 200")).hasMessageContaining("missing URL glob after method");
        assertThatThrownBy(() -> RouteSpec.parse("route GET abort")).hasMessageContaining("missing URL glob after method");
        assertThat(RouteSpec.parse("route 'GET' fulfill 200").route.glob).isEqualTo("GET");   // quoted: really a glob
        assertThatThrownBy(() -> RouteSpec.parse("route **/a fulfill ok")).hasMessageContaining("status must be a number");
        assertThatThrownBy(() -> RouteSpec.parse("route **/a abort nope")).hasMessageContaining("unknown reason 'nope'");
        assertThatThrownBy(() -> RouteSpec.parse("route **/a redirect x")).hasMessageContaining("unknown action 'redirect'");
        assertThatThrownBy(() -> RouteSpec.parse("route **/a fulfill 200 'x")).hasMessageContaining("unterminated quote");
        RouteSpec.Parsed h = RouteSpec.parse("route from har checkout.har url **/api/** notfound abort");
        assertThat(h.kind).isEqualTo("har");
        assertThat(h.harFile).isEqualTo("checkout.har");
        assertThat(h.harGlob).isEqualTo("**/api/**");
        assertThat(h.harAbortMissing).isTrue();
    }

    @Test
    void contentTypeFromFileOrBody() {
        assertThat(RouteSpec.contentType("a.html", new byte[0])).startsWith("text/html");
        assertThat(RouteSpec.contentType(null, "[1,2]".getBytes(StandardCharsets.UTF_8))).isEqualTo("application/json");
        assertThat(RouteSpec.contentType(null, "hello".getBytes(StandardCharsets.UTF_8))).startsWith("text/plain");
        assertThat(RouteSpec.contentType("blob.bin", new byte[] { 1 })).isEqualTo("application/octet-stream");
    }

    @Test
    @SuppressWarnings("unchecked")
    void repeatedHeaders() {
        Route r = RouteSpec.parse("route **/x fulfill 200 'a' header 'Set-Cookie: a=1' header 'Set-Cookie: b=2' type text/plain header 'content-type: text/html'").route;
        assertThat(r.headers).containsEntry("Set-Cookie", "a=1").containsEntry("Content-Type", "text/html").hasSize(2);   // Content-Type: last wins
        assertThat(r.extraHeaders).extracting(h -> h[0] + "=" + h[1]).containsExactly("Set-Cookie=b=2");
        Map<String, Object> fp = (Map<String, Object>) CdpRoutes.command(r, "r1", null)[1];
        assertThat((List<Map<String, Object>>) fp.get("responseHeaders")).extracting(m -> m.get("name") + "=" + m.get("value"))
                .containsExactly("Set-Cookie=a=1", "Content-Type=text/html", "Set-Cookie=b=2", "Content-Length=1");
        Map<String, Object> bp = (Map<String, Object>) BidiRoutes.command(r, "b1", null)[1];
        assertThat((List<Map<String, Object>>) bp.get("headers")).extracting(m -> m.get("name")).containsExactly("Set-Cookie", "Content-Type", "Set-Cookie", "Content-Length");
        Route c = RouteSpec.parse("route **/x continue header 'X-A: 1' header 'X-A: 2'").route;
        Map<String, Object> cp = (Map<String, Object>) CdpRoutes.command(c, "r2", Map.of("x-a", "0"))[1];
        assertThat((List<Map<String, Object>>) cp.get("headers")).extracting(m -> m.get("name") + "=" + m.get("value")).containsExactly("X-A=1", "X-A=2");
    }

    @Test
    @SuppressWarnings("unchecked")
    void cdpCommands() {
        Map<String, String> reqHeaders = new LinkedHashMap<String, String>();
        reqHeaders.put("Accept", "*/*");
        reqHeaders.put("x-env", "prod");
        Object[] none = CdpRoutes.command(null, "r1", reqHeaders);
        assertThat(none[0]).isEqualTo("Fetch.continueRequest");
        assertThat((Map<String, Object>) none[1]).containsOnlyKeys("requestId");

        Route f = RouteSpec.parse("route **/x fulfill 200 'hi'").route;
        f.headers.put("Content-Type", "text/plain");
        f.body = "hi".getBytes(StandardCharsets.UTF_8);
        Object[] ful = CdpRoutes.command(f, "r2", reqHeaders);
        Map<String, Object> fp = (Map<String, Object>) ful[1];
        assertThat(ful[0]).isEqualTo("Fetch.fulfillRequest");
        assertThat(fp).containsEntry("responseCode", 200).containsEntry("body", "aGk=");
        assertThat((List<Map<String, Object>>) fp.get("responseHeaders")).extracting(m -> m.get("name")).containsExactly("Content-Type", "Content-Length");

        Object[] ab = CdpRoutes.command(RouteSpec.parse("route **/x abort timed_out").route, "r3", reqHeaders);
        assertThat(ab[0]).isEqualTo("Fetch.failRequest");
        assertThat((Map<String, Object>) ab[1]).containsEntry("errorReason", "TimedOut");

        Object[] co = CdpRoutes.command(RouteSpec.parse("route **/x continue header 'X-Env: test'").route, "r4", reqHeaders);
        List<Map<String, Object>> hs = (List<Map<String, Object>>) ((Map<String, Object>) co[1]).get("headers");
        assertThat(hs).extracting(m -> m.get("name") + "=" + m.get("value")).containsExactly("Accept=*/*", "X-Env=test");
    }
}
