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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** T1 backend-unit: pure static helpers on the workflow-context handler
 *  (base-URL derivation + responseMappedCount row expansion). */
class WorkflowContextHandlerTest {

    @Test
    void getBaseUrlStripsPathKeepingSchemeAndHost() {
        assertThat(WorkflowContextHandler.getBaseUrl("https://example.com/a/b")).isEqualTo("https://example.com");
        assertThat(WorkflowContextHandler.getBaseUrl("http://a.com/x")).isEqualTo("http://a.com");
    }

    @Test
    void getBaseUrlReturnsWholeUrlWhenNoPath() {
        assertThat(WorkflowContextHandler.getBaseUrl("https://example.com")).isEqualTo("https://example.com");
    }

    @Test
    void getBaseUrlBlankIsNull() {
        assertThat(WorkflowContextHandler.getBaseUrl(null)).isNull();
        assertThat(WorkflowContextHandler.getBaseUrl("   ")).isNull();
    }

    @Test
    void nodeCountMapListBuildsIndexedRows() {
        List<Map<String, String>> rows = WorkflowContextHandler.getNodeCountMapList("3", "n");
        assertThat(rows).hasSize(3);
        assertThat(rows.get(0)).containsEntry("index", "1");
        assertThat(rows.get(2)).containsEntry("index", "3");
        assertThat(WorkflowContextHandler.getNodeCountMapList("0", "n")).isEmpty();
    }

    @Test
    void nodeCountMapListRejectsNonNumeric() {
        assertThatThrownBy(() -> WorkflowContextHandler.getNodeCountMapList("bad", "n"))
                .isInstanceOf(AssertionError.class);
    }
}
