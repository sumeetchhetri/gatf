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
    void rejectsNonSpecs() {
        assertThatThrownBy(() -> Importers.importSource("openapi", "{\"info\":{}}", "x", null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("not an OpenAPI/Swagger document");
    }
}
