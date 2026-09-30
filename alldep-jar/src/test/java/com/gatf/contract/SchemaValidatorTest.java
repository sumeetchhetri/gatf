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

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** OA2 — the schema subset validator against the official JSON Schema Test Suite subset plus OpenAPI specifics. */
class SchemaValidatorTest {

    private static final ObjectMapper OM = new ObjectMapper();
    private static final String[] SUITE = { "additionalProperties", "allOf", "anyOf", "boolean_schema", "const", "enum",
            "exclusiveMaximum", "exclusiveMinimum", "items", "maxItems", "maxLength", "maxProperties", "maximum", "minItems",
            "minLength", "minProperties", "minimum", "multipleOf", "not", "oneOf", "pattern", "patternProperties",
            "prefixItems", "properties", "required", "type", "uniqueItems" };

    private static List<SchemaValidator.Violation> run(String schema, String data) throws Exception {
        JsonNode s = OM.readTree(schema);
        SpecDoc d = new SpecDoc(s, null);
        return new SchemaValidator(d, true).validate(d.node(s, ""), OM.readTree(data));
    }

    @Test
    void jsonSchemaTestSuiteSubset() throws Exception {
        List<String> failures = new ArrayList<String>();
        int cases = 0;
        for (String f : SUITE) {
            JsonNode groups;
            try (InputStream in = SchemaValidatorTest.class.getResourceAsStream("/contract/json-schema-test-suite/" + f + ".json")) {
                groups = OM.readTree(in);
            }
            for (JsonNode g : groups) {
                SpecDoc d = new SpecDoc(g.get("schema"), null);
                for (JsonNode t : g.get("tests")) {
                    cases++;
                    boolean valid = new SchemaValidator(d, false).validate(d.node(g.get("schema"), ""), t.get("data")).isEmpty();
                    if (valid != t.get("valid").asBoolean()) failures.add(f + ": " + g.get("description").asText() + " / " + t.get("description").asText());
                }
            }
        }
        assertThat(cases).isGreaterThan(600);
        assertThat(failures).isEmpty();
    }

    @Test
    void missingRequiredFieldNamesThePointer() throws Exception {
        String schema = "{\"type\":\"array\",\"items\":{\"$ref\":\"#/$defs/Pet\"},\"$defs\":{\"Pet\":{\"type\":\"object\",\"required\":[\"name\",\"photoUrls\"],"
                + "\"properties\":{\"name\":{\"type\":\"string\"},\"photoUrls\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}}}}}}";
        List<SchemaValidator.Violation> v = run(schema, "[{\"name\":\"a\",\"photoUrls\":[]},{\"name\":\"b\"}]");
        assertThat(v).hasSize(1);
        assertThat(v.get(0).pointer).isEqualTo("/1/photoUrls");
        assertThat(v.get(0).toString()).isEqualTo("/1/photoUrls: required property missing (expected 'photoUrls' present, actual absent)");
    }

    @Test
    void openApiNullableWriteOnlyAndFormats() throws Exception {
        String schema = "{\"type\":\"object\",\"required\":[\"id\",\"password\"],\"properties\":{"
                + "\"id\":{\"type\":\"string\",\"format\":\"uuid\"},\"password\":{\"type\":\"string\",\"writeOnly\":true},"
                + "\"nick\":{\"type\":\"string\",\"nullable\":true},\"at\":{\"type\":\"string\",\"format\":\"date-time\"},"
                + "\"n\":{\"type\":\"integer\",\"format\":\"int32\",\"minimum\":0,\"exclusiveMinimum\":true}}}";
        assertThat(run(schema, "{\"id\":\"3fa85f64-5717-4562-b3fc-2c963f66afa6\",\"nick\":null,\"at\":\"2026-01-01T10:00:00Z\",\"n\":1}")).isEmpty();
        List<SchemaValidator.Violation> v = run(schema, "{\"id\":\"nope\",\"at\":\"yesterday\",\"n\":0}");
        assertThat(v).extracting(x -> x.pointer).containsExactly("/id", "/at", "/n");
        assertThat(v.get(0).message).isEqualTo("invalid uuid");
        assertThat(run(schema, "{\"id\":\"3fa85f64-5717-4562-b3fc-2c963f66afa6\",\"n\":3000000000}")).extracting(x -> x.message)
                .containsExactly("out of int32 range");
    }

    @Test
    void discriminatorPicksTheBranchAndRefCanBeNullable() throws Exception {
        // overlapping subtypes (no additionalProperties:false) would match both oneOf branches without the discriminator
        String schema = "{\"type\":\"object\",\"properties\":{\"animal\":{\"oneOf\":[{\"$ref\":\"#/$defs/Cat\"},{\"$ref\":\"#/$defs/Dog\"}],"
                + "\"discriminator\":{\"propertyName\":\"kind\",\"mapping\":{\"dog\":\"#/$defs/Dog\"}}},"
                + "\"owner\":{\"$ref\":\"#/$defs/Cat\",\"nullable\":true}},"
                + "\"$defs\":{\"Cat\":{\"type\":\"object\",\"required\":[\"kind\"],\"properties\":{\"kind\":{\"type\":\"string\"},\"lives\":{\"type\":\"integer\"}}},"
                + "\"Dog\":{\"type\":\"object\",\"required\":[\"kind\",\"bark\"],\"properties\":{\"kind\":{\"type\":\"string\"},\"bark\":{\"type\":\"boolean\"}}}}}";
        assertThat(run(schema, "{\"animal\":{\"kind\":\"Cat\",\"lives\":9},\"owner\":null}")).isEmpty();
        assertThat(run(schema, "{\"animal\":{\"kind\":\"dog\"}}")).extracting(x -> x.pointer)
                .containsExactly("/animal/bark");                                   // mapping → Dog, only Dog's rules apply
        assertThat(run(schema, "{\"animal\":{\"kind\":\"cow\"}}")).extracting(x -> x.pointer)
                .containsExactly("/animal/kind");
    }

    @Test
    void recursiveSchemasTerminate() throws Exception {
        String schema = "{\"$ref\":\"#/$defs/Node\",\"$defs\":{\"Node\":{\"type\":\"object\",\"properties\":{\"kids\":{\"type\":\"array\",\"items\":{\"$ref\":\"#/$defs/Node\"}},"
                + "\"name\":{\"type\":\"string\"}}}}}";
        assertThat(run(schema, "{\"kids\":[{\"kids\":[{\"name\":1}]}]}")).extracting(x -> x.pointer).containsExactly("/kids/0/kids/0/name");
    }

    @Test
    void typeArrayNullStillAppliesEnumAndUnresolvableRefIsNoted() throws Exception {
        assertThat(run("{\"type\":[\"string\",\"null\"],\"enum\":[\"a\"]}", "null")).extracting(x -> x.message).containsExactly("not in enum");
        assertThat(run("{\"type\":[\"string\",\"null\"],\"enum\":[\"a\",null]}", "null")).isEmpty();
        assertThat(run("{\"type\":\"string\",\"nullable\":true,\"enum\":[\"a\"]}", "null")).isEmpty();   // OAS 3.0 nullable
        JsonNode s = OM.readTree("{\"properties\":{\"a\":{\"$ref\":\"#/$defs/Missing\"},\"b\":{\"$ref\":\"#/$defs/My%20Pet\"}},"
                + "\"$defs\":{\"My Pet\":{\"type\":\"integer\"}}}");
        SpecDoc d = new SpecDoc(s, null);
        SchemaValidator sv = new SchemaValidator(d, true);
        assertThat(sv.validate(d.node(s, ""), OM.readTree("{\"a\":1,\"b\":\"x\"}"))).extracting(x -> x.pointer).containsExactly("/b");   // %20 decoded
        assertThat(sv.notes()).containsExactly("schema $ref #/$defs/Missing unresolvable — not validated");
    }
}
