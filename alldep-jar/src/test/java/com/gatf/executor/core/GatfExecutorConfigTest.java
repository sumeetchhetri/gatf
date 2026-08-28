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
package com.gatf.executor.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

/** T1 backend-unit: executor-config JSON round-trip stability via the shared ObjectMapper.
 *  Locks the gatf-config.json (de)serialization the SPA + strangler rely on (delta-055 migration).
 *  Uses OM read/write only — no reliance on getters/setters. */
class GatfExecutorConfigTest {

    @Test
    void jsonRoundTripIsStableAndPreservesScalars() throws Exception {
        String src = "{"
                + "\"baseUrl\":\"https://api.example\","
                + "\"testCasesBasePath\":\"tests\","
                + "\"authEnabled\":true,"
                + "\"numConcurrentExecutions\":5,"
                + "\"loadTestingEnabled\":false"
                + "}";

        GatfExecutorConfig cfg = WorkflowContextHandler.OM.readValue(src, GatfExecutorConfig.class);
        String out = WorkflowContextHandler.OM.writeValueAsString(cfg);

        // the fields we set survive the round trip
        assertThat(out)
                .contains("\"baseUrl\":\"https://api.example\"")
                .contains("\"testCasesBasePath\":\"tests\"")
                .contains("\"authEnabled\":true")
                .contains("\"numConcurrentExecutions\":5");

        // POJO -> JSON -> POJO -> JSON is stable (key-order-insensitive tree compare)
        GatfExecutorConfig cfg2 = WorkflowContextHandler.OM.readValue(out, GatfExecutorConfig.class);
        JsonNode t1 = WorkflowContextHandler.OM.readTree(out);
        JsonNode t2 = WorkflowContextHandler.OM.readTree(WorkflowContextHandler.OM.writeValueAsString(cfg2));
        assertThat(t2).isEqualTo(t1);
    }
}
