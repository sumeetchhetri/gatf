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

import org.junit.jupiter.api.Test;

/** Q3: traceMode resolution, per-round capture and keep decisions. */
class TraceModeTest {

    private static GatfExecutorConfig cfg(String mode, boolean enableTrace, int retryCounter) {
        GatfExecutorConfig c = new GatfExecutorConfig();
        c.setTraceMode(mode);
        c.setEnableTrace(enableTrace);
        c.setRetryCounter(retryCounter);
        return c;
    }

    @Test
    void blankModeFallsBackToEnableTrace() {
        assertThat(cfg(null, false, 0).effectiveTraceMode()).isEqualTo("off");
        assertThat(cfg(null, true, 0).effectiveTraceMode()).isEqualTo("on");
        assertThat(cfg("  ", true, 0).getTraceMode()).isNull();
        assertThat(cfg("Retain-On-Failure", false, 0).effectiveTraceMode()).isEqualTo("retain-on-failure");
    }

    @Test
    void modeWinsOverEnableTrace() {
        assertThat(cfg("off", true, 0).captureTraceThisRun()).isFalse();
        assertThat(cfg("on", false, 0).captureTraceThisRun()).isTrue();
    }

    @Test
    void onFirstRetryCapturesOnlyInRetryRoundOne() {
        // retryCounter: 0 = retries disabled, 1 = normal round (retries enabled), 2 = retry round 1, 3 = round 2
        assertThat(cfg("on-first-retry", false, 0).captureTraceThisRun()).isFalse();
        assertThat(cfg("on-first-retry", false, 1).captureTraceThisRun()).isFalse();
        assertThat(cfg("on-first-retry", false, 2).captureTraceThisRun()).isTrue();
        assertThat(cfg("on-first-retry", false, 3).captureTraceThisRun()).isFalse();
    }

    @Test
    void retainOnFailureKeepsOnlyFailingScripts() {
        assertThat(cfg("retain-on-failure", false, 0).captureTraceThisRun()).isTrue();
        assertThat(cfg("retain-on-failure", false, 0).keepTrace(false)).isFalse();
        assertThat(cfg("retain-on-failure", false, 0).keepTrace(true)).isTrue();
        assertThat(cfg("on", false, 0).keepTrace(false)).isTrue();
    }

    @Test
    void unknownModeIsRejectedWithTheValidList() {
        assertThatThrownBy(() -> cfg("retain-on-fail", false, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("off, on, retain-on-failure, on-first-retry");
    }
}
