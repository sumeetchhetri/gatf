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
package com.gatf;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Harness smoke for the integration-test phase: proves maven-failsafe discovers *IT classes and
 * that JUnit 5 + AssertJ resolve on the test classpath. This is intentionally trivial — it is
 * replaced by the real in-process Grizzly REST + API-execution integration suite in T2 (delta-066).
 */
class HarnessIT {

    @Test
    void failsafeRunsIntegrationTests() {
        assertThat(1 + 1).isEqualTo(2);
    }

    @Test
    void junitAndAssertjAreOnTheClasspath() {
        assertThat("gatf").startsWith("g").hasSize(4);
    }
}
