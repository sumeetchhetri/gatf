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
