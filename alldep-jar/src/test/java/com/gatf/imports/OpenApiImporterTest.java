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

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.gatf.executor.core.TestCase;
import com.gatf.executor.core.TestFilter;
import com.gatf.executor.core.WorkflowContextHandler;

/** OA1 — Swagger 2.0 / OpenAPI 3.0 / 3.1 import. */
class OpenApiImporterTest {

    private static String res(String name) throws Exception {
        try (InputStream in = OpenApiImporterTest.class.getResourceAsStream("/imports/openapi/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static File dir() throws Exception {
        return new File(OpenApiImporterTest.class.getResource("/imports/openapi/common.json").toURI()).getParentFile();
    }

    private static Map<String, TestCase> byName(ImportResult r) {
        Map<String, TestCase> m = new LinkedHashMap<String, TestCase>();
        for (TestCase t : r.testCases) m.put(t.getName(), t);
        return m;
    }

    @Test
    void petstoreSwagger2() throws Exception {
        ImportResult r = Importers.importSource("swagger", res("petstore-v2.json"), "petstore", null);
        Map<String, TestCase> c = byName(r);
        assertThat(r.type).isEqualTo("openapi");
        assertThat(r.fileName).isEqualTo("petstore.xml");
        assertThat(c).hasSize(12).containsKeys("addPet", "getPetById", "findPetsByStatus", "placeOrder", "loginUser");
        TestCase get = c.get("getPetById");
        assertThat(get.getBaseUrl()).isNull();                                   // origin goes in gatf-config baseUrl
        assertThat(r.stats).containsEntry("baseUrl", "https://petstore.swagger.io");
        assertThat(get.getUrl()).isEqualTo("/v2/pet/1");
        assertThat(get.getHeaders()).containsEntry("api_key", "${api_key}");
        assertThat(get.getExpectedResCode()).isEqualTo(200);
        assertThat(get.getExpectedResContentType()).isEqualTo("application/json");
        assertThat(TestFilter.tagsIn(get.getDescription())).containsExactly("@pet");
        TestCase add = c.get("addPet");
        assertThat(add.getHeaders()).containsEntry("Authorization", "Bearer ${authToken}").containsEntry("Content-Type", "application/json");
        assertThat(add.getContent()).contains("\"name\":\"doggie\"").contains("\"photoUrls\":[\"string\"]");
        assertThat(c.get("findPetsByStatus").getUrl()).isEqualTo("/v2/pet/findByStatus?status=available");
        assertThat(c.get("getOrderById").getUrl()).isEqualTo("/v2/store/order/1");
        assertThat(r.variables).containsKeys("authToken", "api_key");
        assertThat(r.unsupported).extracting(u -> u.construct)
                .contains("HEAD /user/{username}", "POST /pet/{petId}/uploadImage multipart part 'file'");
    }

    @Test
    void petstoreOas3Yaml() throws Exception {
        ImportResult r = Importers.importSource("openapi", res("petstore-v3.yaml"), "petstore3", null);
        Map<String, TestCase> c = byName(r);
        assertThat(r.stats).containsEntry("format", "openapi 3.0.3").containsEntry("baseUrl", "https://petstore3.swagger.io");
        assertThat(c).hasSize(10);
        assertThat(c.get("getPetById").getUrl()).isEqualTo("/api/v3/pet/10");                    // path-item $ref param, schema example
        assertThat(c.get("getPetById").getHeaders()).containsEntry("api_key", "${api_key}");     // first security alternative
        assertThat(c.get("addPet").getContent()).doesNotContain("\"id\":10,\"name\"")             // readOnly id left out of requests
                .contains("\"category\":{\"id\":1,\"name\":\"Dogs\"}");
        assertThat(c.get("placeOrder").getContent()).isEqualTo("{\"id\":10,\"petId\":198772,\"quantity\":7,\"status\":\"approved\",\"complete\":true}");
        assertThat(c.get("loginUser").getUrl()).isEqualTo("/api/v3/user/login?username=theUser&password=password");
        assertThat(c.get("logoutUser").getExpectedResContentType()).isNull();
        assertThat(r.unsupported).extracting(u -> u.construct)
                .contains("POST /pet callback onPetAdded", "GET /pet/{petId} links", "POST /pet/{petId}/uploadImage body application/octet-stream");
    }

    @Test
    void oas31CyclesRelativeFilesAndRoundTrip() throws Exception {
        ImportResult r = Importers.importSource("openapi", res("tree-v31.json"), "tree", null, dir());
        Map<String, TestCase> c = byName(r);
        assertThat(c).containsOnlyKeys("getNode", "patchNode", "createNode");
        TestCase get = c.get("getNode");
        assertThat(get.getBaseUrl()).isNull();                                   // relative server → config baseUrl
        assertThat(get.getUrl()).isEqualTo("/api/nodes/3fa85f64-5717-4562-b3fc-2c963f66afa6?depth=2");
        assertThat(get.getHeaders()).containsEntry("Cookie", "trace=on").containsEntry("Authorization", "Bearer ${authToken}");
        assertThat(TestFilter.tagsIn(get.getDescription())).containsExactly("@tree", "@read-only");
        TestCase patch = c.get("patchNode");
        assertThat(patch.getHeaders()).containsEntry("Content-Type", "application/merge-patch+json");
        assertThat(patch.getContent()).contains("\"email\":\"user@example.com\"");   // common.json#/$defs/Person, cyclic manager cut
        assertThat(c.get("createNode").getExpectedResCode()).isEqualTo(201);
        assertThat(c.get("createNode").getUrl()).isEqualTo("/api/nodes?api-key=${key}");
        assertThat(r.unsupported).extracting(u -> u.construct).contains("webhook nodeChanged", "OPTIONS /nodes", "$ref #/paths/~1loop");

        String xml = WorkflowContextHandler.XOM.writeValueAsString(r.testCases);
        List<TestCase> back = WorkflowContextHandler.XOM.readValue(xml, new TypeReference<List<TestCase>>() {});
        assertThat(back).extracting(TestCase::getName).containsExactly("getNode", "patchNode", "createNode");
        assertThat(back.get(1).getContent()).isEqualTo(patch.getContent());
    }

    @Test
    void relativeRefWithoutFolderIsReported() throws Exception {
        ImportResult r = Importers.importSource("openapi", res("tree-v31.json"), "tree", null);
        assertThat(r.unsupported).extracting(u -> u.reason).anyMatch(s -> s.startsWith("relative file reference"));
    }

    @Test
    void contractPointersAndNegativeCases() throws Exception {
        ImportResult r = Importers.importOpenApi(res("petstore-v3.yaml"), "petstore3", null, "petstore3.openapi.yaml", true);
        Map<String, TestCase> c = byName(r);
        assertThat(c.get("getPetById").getContract()).isEqualTo("petstore3.openapi.yaml#/paths/~1pet~1{petId}/get");
        assertThat(c.get("getPetById").getContractHash()).matches("[0-9a-f]{12}");
        TestCase missing = c.get("loginUser [missing username]");
        assertThat(missing.getUrl()).isEqualTo("/api/v3/user/login?password=password");
        assertThat(missing.getExpectedResCode()).isEqualTo(400);
        assertThat(TestFilter.tagsIn(missing.getDescription())).contains("@negative");
        TestCase wrong = c.get("addPet [wrong type name]");
        assertThat(wrong.getContent()).contains("\"name\":12345");
        assertThat(wrong.getExpectedResCode()).isEqualTo(400);                       // only 405 declared: not a malformed-request status
        assertThat(wrong.getContract()).isNull();                                     // 400 undeclared → status-only check
        assertThat(Importers.importSource("openapi", res("petstore-v3.yaml"), "p", null).testCases)
                .allMatch(t -> t.getContract() == null);                               // no spec reference, no pointer
        // an operation without a declared 4xx: the negative case expects 400 and checks the status only
        String spec = "{\"openapi\":\"3.0.0\",\"info\":{\"title\":\"t\",\"version\":\"1\"},\"paths\":{\"/s\":{\"get\":{\"operationId\":\"search\","
                + "\"parameters\":[{\"name\":\"q\",\"in\":\"query\",\"required\":true,\"schema\":{\"type\":\"string\"}}],"
                + "\"responses\":{\"200\":{\"description\":\"ok\",\"content\":{\"application/json\":{\"schema\":{\"type\":\"array\"}}}}}}}}}";
        Map<String, TestCase> s = byName(Importers.importOpenApi(spec, "s", null, "s.openapi.json", true));
        assertThat(s.get("search").getContract()).isNotNull();
        assertThat(s.get("search").getHeaders()).containsEntry("Accept", "application/json");
        assertThat(s.get("search [missing q]").getExpectedResCode()).isEqualTo(400);
        assertThat(s.get("search [missing q]").getContract()).isNull();
    }

    @Test
    void rejectsNonSpecs() {
        assertThatThrownBy(() -> Importers.importSource("openapi", "{\"info\":{}}", "x", null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("not an OpenAPI/Swagger document");
    }

    private static final String HEAD = "{\"openapi\":\"3.1.0\",\"info\":{\"title\":\"t\",\"version\":\"1\"},";
    private static final String Q = "\"parameters\":[{\"name\":\"q\",\"in\":\"query\",\"required\":true,\"schema\":{\"type\":\"string\"}}],";

    private static TestCase missingQ(String responses) throws Exception {
        String spec = HEAD + "\"paths\":{\"/s\":{\"get\":{\"operationId\":\"s\"," + Q + "\"responses\":{\"200\":{\"description\":\"\"}," + responses + "}}}}}";
        return byName(Importers.importOpenApi(spec, "s", null, "s.openapi.json", true)).get("s [missing q]");
    }

    @Test
    void negativeStatusIsAMalformedRequestStatus() throws Exception {
        TestCase n = missingQ("\"404\":{\"description\":\"\"},\"401\":{\"description\":\"\"},\"409\":{\"description\":\"\"}");
        assertThat(n.getExpectedResCode()).isEqualTo(400);
        assertThat(n.getContract()).isNull();
        n = missingQ("\"404\":{\"description\":\"\"},\"422\":{\"description\":\"\"}");
        assertThat(n.getExpectedResCode()).isEqualTo(422);
        assertThat(n.getContract()).isNotNull();
        n = missingQ("\"4XX\":{\"description\":\"\"},\"422\":{\"description\":\"\"},\"400\":{\"description\":\"\"}");
        assertThat(n.getExpectedResCode()).isEqualTo(400);
        n = missingQ("\"4XX\":{\"description\":\"\"}");
        assertThat(n.getExpectedResCode()).isEqualTo(400);
        assertThat(n.getContract()).isNotNull();                                      // 400 falls in the declared 4XX
    }

    @Test
    void anchorsEncodedPointersApiKeysAndTags() throws Exception {
        String spec = HEAD + "\"paths\":{\"/p\":{\"post\":{\"operationId\":\"p\",\"tags\":[\".internal\",\"--x\"],\"security\":[{\"k\":[],\"c\":[]}],"
                + "\"parameters\":[{\"$ref\":\"#pa\"},{\"name\":\"key\",\"in\":\"query\",\"required\":true,\"schema\":{\"type\":\"string\"}},"
                + "{\"name\":\"sid\",\"in\":\"cookie\",\"required\":true,\"schema\":{\"type\":\"string\"}},"
                + "{\"name\":\"theme\",\"in\":\"cookie\",\"required\":true,\"schema\":{\"type\":\"string\",\"example\":\"dark\"}}],"
                + "\"requestBody\":{\"content\":{\"application/json\":{\"schema\":{\"$ref\":\"#/components/schemas/My%20Pet\"}}}},"
                + "\"responses\":{\"200\":{\"description\":\"\"}}}}},"
                + "\"components\":{\"securitySchemes\":{\"k\":{\"type\":\"apiKey\",\"in\":\"query\",\"name\":\"key\"},"
                + "\"c\":{\"type\":\"apiKey\",\"in\":\"cookie\",\"name\":\"sid\"}},"
                + "\"schemas\":{\"My Pet\":{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\",\"example\":\"rex\"}}}}}}";
        ImportResult r = Importers.importSource("openapi", spec, "p", null);
        TestCase p = byName(r).get("p");
        assertThat(r.unsupported).extracting(u -> u.reason).anyMatch(s -> s.startsWith("anchor / non-pointer $ref not supported"));
        assertThat(p.getContent()).isEqualTo("{\"name\":\"rex\"}");                     // %20 decoded
        assertThat(p.getUrl()).isEqualTo("/p?key=${k}");                                  // declared 'key' replaced, not duplicated
        assertThat(p.getHeaders()).containsEntry("Cookie", "theme=dark; sid=${c}");
        assertThat(TestFilter.tagsIn(p.getDescription())).containsExactly("@internal", "@x");
    }

    @Test
    void numericAndPatternSamples() throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
        String[][] ints = { { "{}", "1" }, { "{\"minimum\":5,\"exclusiveMinimum\":true}", "6" }, { "{\"exclusiveMinimum\":5}", "6" },
                { "{\"maximum\":0,\"exclusiveMaximum\":true}", "-1" }, { "{\"exclusiveMaximum\":0}", "-1" }, { "{\"multipleOf\":5}", "5" },
                { "{\"minimum\":7,\"multipleOf\":5}", "10" }, { "{\"exclusiveMinimum\":10,\"multipleOf\":5}", "15" },
                { "{\"maximum\":-3,\"multipleOf\":4}", "-4" }, { "{\"minimum\":2,\"exclusiveMinimum\":1}", "2" } };
        for (String[] t : ints) assertThat(OpenApiImporter.numberSample(om.readTree(t[0]), true).toPlainString()).isEqualTo(t[1]);
        String[][] nums = { { "{}", "1.5" }, { "{\"minimum\":2}", "2" }, { "{\"exclusiveMinimum\":2}", "3" },
                { "{\"minimum\":0,\"maximum\":1,\"exclusiveMaximum\":true}", "0.5" }, { "{\"exclusiveMinimum\":0,\"exclusiveMaximum\":0.1}", "0.05" },
                { "{\"multipleOf\":0.25,\"minimum\":1.1}", "1.25" }, { "{\"exclusiveMaximum\":1}", "0" } };
        for (String[] t : nums) assertThat(OpenApiImporter.numberSample(om.readTree(t[0]), false).compareTo(new java.math.BigDecimal(t[1]))).isZero();
        assertThat(OpenApiImporter.patternSample(om.readTree("{\"pattern\":\"^[A-Z]{3}$\"}"))).isEqualTo("ABC");
        assertThat(OpenApiImporter.patternSample(om.readTree("{\"pattern\":\"^\\\\d+$\",\"minLength\":6}"))).isEqualTo("123333");
        assertThat(OpenApiImporter.patternSample(om.readTree("{\"pattern\":\"^[A-Z]\\\\d-[a-z]\\\\d$\"}"))).isEqualTo("A1-b2");
        assertThat(OpenApiImporter.patternSample(om.readTree("{\"format\":\"email\",\"pattern\":\"@example\\\\.com$\"}"))).isEqualTo("user@example.com");
        assertThat(OpenApiImporter.patternSample(om.readTree("{\"pattern\":\"^\\\\d{9}$\"}"))).isNull();
        String spec = HEAD + "\"paths\":{\"/z/{z}\":{\"get\":{\"operationId\":\"z\",\"parameters\":[{\"name\":\"z\",\"in\":\"path\","
                + "\"required\":true,\"schema\":{\"type\":\"string\",\"pattern\":\"^\\\\d{9}$\"}},{\"name\":\"n\",\"in\":\"query\",\"required\":true,"
                + "\"schema\":{\"type\":\"integer\",\"minimum\":10,\"multipleOf\":10}}],\"responses\":{\"200\":{\"description\":\"\"}}}}}}";
        ImportResult r = Importers.importSource("openapi", spec, "z", null);
        assertThat(byName(r).get("z").getUrl()).isEqualTo("/z/string?n=10");
        assertThat(r.unsupported).extracting(u -> u.reason).contains("no sample matches pattern — add an example");
    }

    @Test
    void uploadedBundleRefsStayInsideTheBundle() throws Exception {
        java.nio.file.Path tmp = java.nio.file.Files.createTempDirectory("gatf-oa-bundle");
        java.nio.file.Path bundle = java.nio.file.Files.createDirectories(tmp.resolve("bundle"));
        java.nio.file.Files.writeString(bundle.resolve("pet.json"), "{\"type\":\"object\",\"example\":{\"in\":1}}");
        java.nio.file.Files.writeString(tmp.resolve("secret.json"), "{\"type\":\"object\",\"example\":{\"out\":1}}");
        String spec = HEAD + "\"paths\":{\"/a\":{\"post\":{\"operationId\":\"a\",\"requestBody\":{\"content\":{\"application/json\":"
                + "{\"schema\":{\"$ref\":\"pet.json\"}}}},\"responses\":{\"200\":{\"description\":\"\"}}}},"
                + "\"/b\":{\"post\":{\"operationId\":\"b\",\"requestBody\":{\"content\":{\"application/json\":"
                + "{\"schema\":{\"$ref\":\"../secret.json\"}}}},\"responses\":{\"200\":{\"description\":\"\"}}}}}}";
        ImportResult r = Importers.importOpenApi(spec, "s", bundle.toFile(), null, false, bundle.toFile());
        assertThat(byName(r).get("a").getContent()).contains("\"in\"");
        assertThat(String.valueOf(byName(r).get("b").getContent())).doesNotContain("\"out\"");
        assertThat(r.unsupported).extracting(u -> u.reason).contains("outside the uploaded files — not followed");
        ImportResult cli = Importers.importOpenApi(spec, "s", bundle.toFile(), null, false);     // CLI: unrestricted, as before
        assertThat(byName(cli).get("b").getContent()).contains("\"out\"");
    }
}
