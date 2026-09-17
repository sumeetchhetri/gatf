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
package com.gatf.executor.executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Q1: an unknown perfConfig.type fails the test case instead of silently running autocannon. */
class PerfToolTypeTest {

    @Test
    void knownTypesPassThrough() {
        for (String t : new String[] { "wrk", "wrk2", "vegeta", "autocannon" }) {
            assertThat(TestCaseExecutorUtil.perfToolType(t)).isEqualTo(t);
        }
    }

    @Test
    void unknownTypesAreRejectedWithTheValidList() {
        for (String t : new String[] { "vegata", "Vegeta", "wrk3", "jmeter" }) {
            assertThatThrownBy(() -> TestCaseExecutorUtil.perfToolType(t))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(t).hasMessageContaining("vegeta, autocannon, wrk, wrk2");
        }
    }
}
