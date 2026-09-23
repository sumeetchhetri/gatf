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
package com.gatf.imports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.type.TypeReference;

import com.gatf.executor.core.TestCase;
import com.gatf.executor.core.WorkflowContextHandler;

/** Q2 — cURL / Postman / HAR importers and the cURL writer used for the report line. */
class ApiImportersTest {

    private static String res(String name) throws Exception {
        try (InputStream in = ApiImportersTest.class.getResourceAsStream("/imports/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Map<String, TestCase> byName(ImportResult r) {
        Map<String, TestCase> m = new LinkedHashMap<String, TestCase>();
        for (TestCase t : r.testCases) m.put(t.getName(), t);
        return m;
    }

    @Test
    void curlFromDevToolsBash() throws Exception {
        String chrome = "curl 'https://api.example.com/v1/users?page=2' \\\n  -H 'accept: application/json' \\\n"
                + "  -H 'content-type: application/json' \\\n  -b 'sid=1; theme=dark' \\\n"
                + "  --data-raw $'{\"name\":\"O\\'Brien\"}' \\\n  --compressed";
        ImportResult r = Importers.importSource("curl", chrome, "users", null);
        TestCase tc = r.testCases.get(0);
        assertThat(tc.getMethod()).isEqualTo("POST");
        assertThat(tc.getBaseUrl()).isEqualTo("https://api.example.com");
        assertThat(tc.getUrl()).isEqualTo("/v1/users?page=2");
        assertThat(tc.getContent()).isEqualTo("{\"name\":\"O'Brien\"}");
        assertThat(tc.getHeaders()).containsEntry("Cookie", "sid=1; theme=dark").containsEntry("content-type", "application/json");
        assertThat(r.unsupported).isEmpty();
        assertThat(r.fileName).isEqualTo("users.xml");
    }

    @Test
    void curlFromDevToolsCmd() throws Exception {
        String cmd = "curl ^\"https://x.test/api/a^\" ^\n  -H ^\"Content-Type: application/json^\" ^\n  --data-raw ^\"^{^\\^\"k^\\^\":1^}^\"";
        TestCase tc = Importers.importSource("curl", cmd, "c", null).testCases.get(0);
        assertThat(tc.getContent()).isEqualTo("{\"k\":1}");
        assertThat(tc.getUrl()).isEqualTo("/api/a");
    }

    @Test
    void curlOptionsAndSeveralCommands() throws Exception {
        String multi = "# login\ncurl -sSL -XPOST https://h.test/login -u admin:s3cret -d user=a -d pass=b\n"
                + "curl -G https://h.test/search --data-urlencode 'q=a b' && curl -I https://h.test/x\necho hi\n"
                + "curl -F 'file=@/tmp/a.png;type=image/png' -F name=bob https://h.test/up -k -x proxy:8080\n"
                + "curl -X PUT http://h:8080/a -H 'Content-Type: application/xml' --data-binary @body.xml\n"
                + "curl --json '{\"a\":1}' localhost:3000/x";
        ImportResult r = Importers.importSource("curl", multi, "m", null);
        List<TestCase> c = r.testCases;
        assertThat(c).hasSize(5);
        assertThat(c.get(0).getHeaders()).containsEntry("Authorization",
                "Basic " + Base64.getEncoder().encodeToString("admin:s3cret".getBytes(StandardCharsets.UTF_8)))
                .containsEntry("Content-Type", "application/x-www-form-urlencoded");
        assertThat(c.get(0).getContent()).isEqualTo("user=a&pass=b");
        assertThat(c.get(1).getMethod()).isEqualTo("GET");
        assertThat(c.get(1).getUrl()).isEqualTo("/search?q=a%20b");
        assertThat(c.get(2).getMultipartContent()).containsExactly("file:file:/tmp/a.png:image/png", "name:text:bob:text/plain");
        assertThat(c.get(2).getHeaders()).doesNotContainKey("Content-Type");
        assertThat(c.get(3).getContentFile()).isEqualTo("body.xml");
        assertThat(c.get(3).getBaseUrl()).isEqualTo("http://h:8080");
        assertThat(c.get(4).getBaseUrl()).isEqualTo("http://localhost:3000");
        assertThat(c.get(4).getHeaders()).containsEntry("Content-Type", "application/json");
        // HEAD, the echo line, the proxy and -k are reported, not guessed at
        assertThat(r.unsupported).extracting(u -> u.construct)
                .anyMatch(s -> s.startsWith("HEAD")).anyMatch(s -> s.startsWith("echo")).anyMatch(s -> s.startsWith("-x"))
                .anyMatch(s -> s.contains("--insecure"));
    }

    @Test
    void nothingImportableIsAnError() {
        assertThatThrownBy(() -> Importers.importSource("curl", "ls -la", "x", null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("nothing to import");
        assertThatThrownBy(() -> Importers.importSource("bogus", "x", "x", null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("curl|postman|har");
    }

    @Test
    void reportCurlLineRoundTrips() {
        String body = "{\"q\":\"it's\\\\ \\\"x\\\" $HOME `id`\"}\n\tend";
        String line = CurlCommand.format("post", "https://h.test/a?b=1&c=$x",
                List.<String[]>of(new String[] { "X-A", "v \"q\" $y" }, new String[] { "Content-Type", "application/json" }),
                body, null, true);
        CurlCommand.Parsed p = CurlCommand.parseOne(line);
        assertThat(p.issues).isEmpty();
        assertThat(p.request.method).isEqualTo("POST");
        assertThat(p.request.url).isEqualTo("/a?b=1&c=$x");
        assertThat(p.request.body).isEqualTo(body);
        assertThat(p.request.header("X-A")).isEqualTo("v \"q\" $y");

        line = CurlCommand.format("POST", "https://h.test/u",
                List.<String[]>of(new String[] { "Content-Type", "multipart/form-data; boundary=zz" }), "ignored",
                List.<String[]>of(new String[] { "file", "f", "/a b.png" }, new String[] { "text", "n", "it's" }), false);
        p = CurlCommand.parseOne(line);
        assertThat(p.request.formParts).containsExactly("f:file:/a b.png", "n:text:it's:text/plain");
        assertThat(p.request.header("Content-Type")).isNull();
    }

    @Test
    void postmanV21() throws Exception {
        ImportResult r = Importers.importSource("postman", res("postman-demo.json"), "demo", null);
        Map<String, TestCase> m = byName(r);
        TestCase login = m.get("Auth / Login");
        assertThat(login.getBaseUrl()).isNull();                       // {{baseUrl}} host → config baseUrl
        assertThat(login.getUrl()).isEqualTo("/auth/login");
        assertThat(login.getContent()).isEqualTo("{\"u\":\"${user}\"}");
        assertThat(login.getHeaders()).containsEntry("Content-Type", "application/json").doesNotContainKey("X-Off")
                .doesNotContainKey("Authorization");                  // folder noauth overrides collection bearer
        assertThat(login.getExpectedResCode()).isEqualTo(200);
        TestCase user = m.get("Get user");
        assertThat(user.getUrl()).isEqualTo("/users/42?expand=1");
        assertThat(user.getHeaders()).containsEntry("Authorization", "Bearer ${token}");
        assertThat(m.get("Upload").getMultipartContent()).containsExactly("f:file:/data/x.csv", "k:text:v:text/plain");
        assertThat(m.get("Upload").getHeaders()).containsEntry("Authorization", "Basic YTpi");
        assertThat(m.get("Form").getUrl()).isEqualTo("/form?api_key=${k}");
        assertThat(m.get("Form").getContent()).isEqualTo("a+b=c%26d&v=${x}");
        assertThat(m.get("GQL").getContent()).isEqualTo("{\"query\":\"query { me { id } }\",\"variables\":{\"a\":1}}");
        assertThat(m).containsKey("Get user (2)").doesNotContainKey("Head");
        assertThat(r.variables).containsEntry("baseUrl", "https://api.demo.test").containsEntry("token", "t0k");
        assertThat(r.unsupported).extracting(u -> u.construct).anyMatch(s -> s.contains("test script")).anyMatch(s -> s.startsWith("HEAD"));
    }

    @Test
    void postmanV1AsExportedByGatf() throws Exception {
        ImportResult r = Importers.importSource("postman", res("postman-v1-gatf.json"), "gen", null);
        TestCase c = r.testCases.get(0);
        assertThat(c.getUrl()).isEqualTo("/api/items");
        assertThat(c.getContent()).isEqualTo("{\"n\":1}");
        assertThat(c.getExpectedResCode()).isEqualTo(201);
        assertThat(r.unsupported).isEmpty();
    }

    @Test
    void harRecording() throws Exception {
        ImportResult r = Importers.importSource("har", res("recording.har"), "rec", null);
        List<TestCase> c = r.testCases;
        assertThat(c).extracting(TestCase::getName).containsExactly("GET /", "POST /login", "GET /items", "POST /form",
                "POST /upload", "GET /p");
        assertThat(c.get(1).getHeaders()).containsOnlyKeys("Content-Type");   // cookie, sec-*, br encoding, length dropped
        assertThat(c.get(2).getExpectedResCode()).isEqualTo(200);             // 304 replayed without validators
        assertThat(c.get(3).getContent()).isEqualTo("a=1+2");
        assertThat(c.get(4).getMultipartContent()).containsExactly("file:file:a.png", "n:text:v:text/plain");
        assertThat(c.get(5).getExpectedResCode()).isZero();                   // status 0 (blocked) not asserted
        assertThat(r.stats).containsEntry("cookieHeadersDropped", 1);
        assertThat(Importers.importSource("har", res("recording.har"), "rec", "api\\.test/").testCases).hasSize(4);
    }

    @Test
    void apiCasesSerializeWithGatfXmlMapper() throws Exception {
        ImportResult r = Importers.importSource("curl", "curl https://h.test/a -H 'X: 1'", "s", null);
        String xml = WorkflowContextHandler.XOM.writeValueAsString(r.testCases);
        assertThat(xml).contains("GET /a").contains("https://h.test");
        // read back exactly as XMLTestCaseFinder reads a test file
        List<TestCase> back = WorkflowContextHandler.XOM.readValue(xml, new TypeReference<List<TestCase>>() {});
        assertThat(back.get(0).getHeaders()).containsEntry("X", "1");
        assertThat(back.get(0).getUrl()).isEqualTo("/a");
    }
}
