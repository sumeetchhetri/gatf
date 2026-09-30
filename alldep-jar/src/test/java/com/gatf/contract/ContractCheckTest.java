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
package com.gatf.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.gatf.executor.core.TestCase;
import com.gatf.imports.ImportResult;
import com.gatf.imports.Importers;

/** OA2 — responses checked against the operation they were generated from; drift on re-import. */
class ContractCheckTest {

    private static File spec(String name) throws Exception {
        return new File(ContractCheckTest.class.getResource("/imports/openapi/" + name).toURI());
    }

    private static final String PET_OP = "/paths/~1pet~1{petId}/get";

    @Test
    void responseMissingRequiredFieldFailsWithPointer() throws Exception {
        SpecDoc d = SpecDoc.load(spec("petstore-v3.yaml"));
        ContractCheck.Result r = ContractCheck.check(d, PET_OP, 200, "application/json; charset=utf-8",
                "{\"id\":10,\"name\":\"doggie\",\"category\":{\"id\":1,\"name\":\"Dogs\"},\"status\":\"available\"}");
        assertThat(r.ok()).isFalse();
        assertThat(r.operation).isEqualTo("getPetById (GET /pet/{petId})");
        assertThat(r.failures).containsExactly("/photoUrls: required property missing (expected 'photoUrls' present, actual absent)");
        assertThat(r.summary()).startsWith("contract getPetById (GET /pet/{petId}): /photoUrls: required property missing");
    }

    @Test
    void conformingResponsePasses() throws Exception {
        SpecDoc d = SpecDoc.load(spec("petstore-v3.yaml"));
        assertThat(ContractCheck.check(d, PET_OP, 200, "application/json",
                "{\"id\":10,\"name\":\"doggie\",\"photoUrls\":[\"u\"],\"tags\":[{\"id\":1,\"name\":\"t\"}],\"status\":\"sold\"}").failures).isEmpty();
        assertThat(ContractCheck.check(d, PET_OP, 404, null, "").failures).isEmpty();   // declared, no body
    }

    @Test
    void undeclaredStatusContentTypeAndBadValues() throws Exception {
        SpecDoc d = SpecDoc.load(spec("petstore-v3.yaml"));
        assertThat(ContractCheck.check(d, PET_OP, 500, "text/html", "<h1>oops</h1>").failures)
                .containsExactly("status 500 is not declared (declared: 200, 404)");
        assertThat(ContractCheck.check(d, PET_OP, 200, "text/html", "<h1>hi</h1>").failures)
                .containsExactly("Content-Type text/html is not declared (declared: application/json)");
        assertThat(ContractCheck.check(d, PET_OP, 200, "application/json", "{\"name\":1,\"photoUrls\":[],\"status\":\"lost\"}").failures)
                .containsExactly("/name: wrong type (expected string, actual integer 1)",
                        "/status: not in enum (expected [\"available\",\"pending\",\"sold\"], actual \"lost\")");
        assertThat(ContractCheck.check(d, PET_OP, 200, "application/json", "not json").failures.get(0)).startsWith("/: body is not valid JSON");
    }

    @Test
    void swagger2ProducesAndArrays() throws Exception {
        SpecDoc d = SpecDoc.load(spec("petstore-v2.json"));
        ContractCheck.Result r = ContractCheck.check(d, "/paths/~1pet~1findByStatus/get", 200, "application/json",
                "[{\"name\":\"a\",\"photoUrls\":[]},{\"name\":\"b\",\"photoUrls\":[],\"category\":{\"id\":\"x\"}}]");
        assertThat(r.failures).containsExactly("/1/category/id: wrong type (expected integer, actual string \"x\")");
        assertThat(ContractCheck.check(d, "/paths/~1store~1inventory/get", 200, "text/plain", "x").failures)
                .containsExactly("Content-Type text/plain is not declared (declared: application/json)");
    }

    @Test
    void oas31RelativeRefsAndRanges() throws Exception {
        SpecDoc d = SpecDoc.load(spec("tree-v31.json"));
        String node = "{\"id\":\"3fa85f64-5717-4562-b3fc-2c963f66afa6\",\"label\":\"abc\",\"kind\":null,"
                + "\"owner\":{\"email\":\"bad\"},\"children\":[{\"id\":\"3fa85f64-5717-4562-b3fc-2c963f66afa6\",\"label\":\"x\"}]}";
        ContractCheck.Result r = ContractCheck.check(d, "/paths/~1nodes~1{id}/patch", 204, "application/json", node);
        assertThat(r.failures).containsExactly("/owner/email: invalid email (expected format email, actual \"bad\")",
                "/children/0/label: too short (expected minLength 3, actual length 1)");
    }

    @Test
    void driftOnReimport() throws Exception {
        String v3 = new String(java.nio.file.Files.readAllBytes(spec("petstore-v3.yaml").toPath()), java.nio.charset.StandardCharsets.UTF_8);
        ImportResult before = Importers.importOpenApi(v3, "p", null, "petstore-v3.yaml", false);
        String changed = v3.replace("  /user/logout:", "  /user/logout2:")
                .replace("quantity: { type: integer, format: int32, example: 7 }", "quantity: { type: string }")
                .replace("summary: Returns pet inventories by status", "summary: prose only");
        ImportResult after = Importers.importOpenApi(changed, "p", null, "petstore-v3.yaml", false);
        ContractDrift.Report drift = ContractDrift.diff(Importers.contractIndex(before.testCases), Importers.contractIndex(after.testCases));
        assertThat(drift.added).containsExactly("GET /user/logout2");
        assertThat(drift.removed).containsExactly("GET /user/logout");
        assertThat(drift.changed).containsExactly("POST /store/order");   // Order schema changed; summary edits are not drift
        Map<String, String> none = new LinkedHashMap<String, String>();
        assertThat(ContractDrift.diff(none, none).isEmpty()).isTrue();
        for (TestCase tc : before.testCases) assertThat(tc.getContract()).startsWith("petstore-v3.yaml#/paths/");
    }
}
