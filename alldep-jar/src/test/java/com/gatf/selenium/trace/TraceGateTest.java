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

import org.junit.jupiter.api.Test;

import com.gatf.executor.core.GatfExecutorConfig;

/** Δ163o verifier round: the implicit trace default must not change how an existing config runs. */
class TraceGateTest {

    private static GatfExecutorConfig cfg(String mode, boolean enableTrace, String protocol) {
        GatfExecutorConfig c = new GatfExecutorConfig();
        c.setTraceMode(mode);
        c.setEnableTrace(enableTrace);
        if (protocol != null) c.setTraceProtocol(protocol);
        return c;
    }

    @Test
    void implicitDefaultTracesChromiumOverCdpOutsideAPoolOnly() {
        GatfExecutorConfig d = cfg(null, false, null);
        assertThat(TraceGate.startTrace(d, true, false)).isTrue();    // Chrome, CDP, no pool
        assertThat(TraceGate.startTrace(d, false, false)).isFalse();  // Firefox / Safari: no CDP
        assertThat(TraceGate.startTrace(d, true, true)).isFalse();    // browserContexts: worker
        assertThat(TraceGate.startTrace(cfg(null, false, "bidi"), true, false)).isFalse();
        assertThat(TraceGate.poolTracing(d)).isFalse();                // no switch to BiDi
        assertThat(TraceGate.wantsBidiSocket(d, "firefox")).isFalse(); // no webSocketUrl capability added
        assertThat(TraceGate.runChecks(d)).isFalse();                  // no budget / security enforcement
    }

    @Test
    void explicitTracingIsUnchanged() {
        for (GatfExecutorConfig c : new GatfExecutorConfig[] { cfg("retain-on-failure", false, null), cfg(null, true, null), cfg("on", false, null) }) {
            assertThat(TraceGate.startTrace(c, false, true)).isTrue();
            assertThat(TraceGate.poolTracing(c)).isTrue();
            assertThat(TraceGate.wantsBidiSocket(c, "firefox")).isTrue();
            assertThat(TraceGate.wantsBidiSocket(c, "chrome")).isFalse();
            assertThat(TraceGate.runChecks(c)).isTrue();
        }
        assertThat(TraceGate.wantsBidiSocket(cfg("on", false, "bidi"), "chrome")).isTrue();
    }

    @Test
    void offAndLoadRunsNeverTrace() {
        assertThat(TraceGate.startTrace(cfg("off", true, null), true, false)).isFalse();
        assertThat(TraceGate.wantsBidiSocket(cfg("off", false, null), "firefox")).isFalse();
        GatfExecutorConfig load = cfg("on", false, null);
        load.setLoadTestingEnabled(true);
        assertThat(TraceGate.startTrace(load, true, false)).isFalse();
        assertThat(TraceGate.poolTracing(load)).isFalse();
        GatfExecutorConfig users = cfg("on", false, null);
        users.setConcurrentUserSimulationNum(5);
        assertThat(TraceGate.startTrace(users, true, false)).isFalse();
    }
}
