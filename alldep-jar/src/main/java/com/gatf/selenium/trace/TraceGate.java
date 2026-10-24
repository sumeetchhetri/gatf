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

import com.gatf.executor.core.GatfExecutorConfig;

/**
 * Δ163o: where a UI run traces. The implicit default ({@link GatfExecutorConfig#traceImplicit()}: neither traceMode
 * nor enableTrace set) traces quietly, so it never changes how an existing config runs: Chromium over CDP only,
 * outside a worker pool, no BiDi socket requested, no worker-pool protocol switch. Explicit tracing is unchanged. Pure.
 */
public final class TraceGate {

    private TraceGate() {
    }

    private static boolean functional(GatfExecutorConfig cfg) {
        return cfg.captureTraceThisRun() && !cfg.isLoadTestingEnabled()
                && (cfg.getConcurrentUserSimulationNum() == null || cfg.getConcurrentUserSimulationNum() <= 1);
    }

    /** Start a trace for a new browser session? {@code devTools}: the driver has CDP; {@code pooled}: a worker pool runs. */
    public static boolean startTrace(GatfExecutorConfig cfg, boolean devTools, boolean pooled) {
        if (cfg == null || !functional(cfg)) return false;
        return !cfg.traceImplicit() || (devTools && "cdp".equals(cfg.getTraceProtocol()) && !pooled);
    }

    /** Does the worker pool need to plan for tracing (it may switch the run to BiDi)? Never for the implicit default. */
    public static boolean poolTracing(GatfExecutorConfig cfg) {
        return cfg != null && !cfg.traceImplicit() && functional(cfg);
    }

    /** Ask the new session for a BiDi socket ({@code webSocketUrl})? Only for explicit tracing that needs BiDi. */
    public static boolean wantsBidiSocket(GatfExecutorConfig cfg, String browserName) {
        if (cfg == null || cfg.traceImplicit() || !cfg.captureTraceThisRun()) return false;
        boolean chromium = "chrome".equals(browserName) || "edge".equals(browserName);
        String tp = cfg.getTraceProtocol();
        return !chromium || "bidi".equals(tp) || "hybrid".equals(tp);
    }

    /** Run perfBudgets / securityChecks over the trace? Not for the implicit default (they needed tracing set before). */
    public static boolean runChecks(GatfExecutorConfig cfg) {
        return cfg != null && !cfg.traceImplicit();
    }
}
