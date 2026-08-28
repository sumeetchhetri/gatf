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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the dependency-free JSON writer the trace recorders emit with.
 * Seed for the T1 backend-unit layer (delta-064 harness); the full trace-core suite
 * (NdjsonWriter, ResourceStore dedup/caps, HAR builder, streamed zip) lands in delta-065.
 *
 * @author Sumeet Chhetri
 */
class JsonTest {

    @Test
    void escapesControlAndSpecialCharacters() {
        assertThat(Json.esc(null)).isEmpty();
        assertThat(Json.esc("plain")).isEqualTo("plain");
        assertThat(Json.esc("a\"b")).isEqualTo("a\\\"b");
        assertThat(Json.esc("a\\b")).isEqualTo("a\\\\b");
        assertThat(Json.esc("line\nbreak\ttab")).isEqualTo("line\\nbreak\\ttab");
        assertThat(Json.esc("\u0001")).isEqualTo("\\u0001");
    }

    @Test
    void strQuotesOrEmitsNull() {
        assertThat(Json.str(null)).isEqualTo("null");
        assertThat(Json.str("x")).isEqualTo("\"x\"");
        assertThat(Json.str("a\"b")).isEqualTo("\"a\\\"b\"");
    }

    @Test
    void writesScalars() {
        assertThat(Json.write(null)).isEqualTo("null");
        assertThat(Json.write(true)).isEqualTo("true");
        assertThat(Json.write(42)).isEqualTo("42");
        assertThat(Json.write("hi")).isEqualTo("\"hi\"");
    }

    @Test
    void nonFiniteNumbersBecomeNull() {
        assertThat(Json.write(Double.NaN)).isEqualTo("null");
        assertThat(Json.write(Double.POSITIVE_INFINITY)).isEqualTo("null");
    }

    @Test
    void writesMapsAndLists() {
        assertThat(Json.write(Map.of("k", "v"))).isEqualTo("{\"k\":\"v\"}");
        assertThat(Json.write(List.of(1, 2, 3))).isEqualTo("[1,2,3]");
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("a", 1);
        nested.put("b", List.of("x", "y"));
        assertThat(Json.write(nested)).isEqualTo("{\"a\":1,\"b\":[\"x\",\"y\"]}");
    }

    @Test
    void objBuilderProducesCompactSingleLineObject() {
        String out = new Json.Obj()
                .put("s", "v")
                .put("n", 5L)
                .put("flag", true)
                .putNum("maybe", null)
                .putRaw("arr", "[1,2]")
                .end();
        assertThat(out).isEqualTo("{\"s\":\"v\",\"n\":5,\"flag\":true,\"maybe\":null,\"arr\":[1,2]}");
    }
}
