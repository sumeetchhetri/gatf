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

/**
 * Q7: per-run overrides from {@code gatf test} (--trace / --retries / --workers), applied to the loaded config just
 * before the run starts. Unset values leave the config file's settings alone; never used by the UI server.
 */
public final class RunOverrides {

    private static volatile String traceMode;
    private static volatile Integer retries;
    private static volatile Integer workers;

    private RunOverrides() {}

    /** Validates and stores the overrides. Throws IllegalArgumentException for an invalid trace mode. */
    public static void set(String traceMode, Integer retries, Integer workers) {
        RunOverrides.traceMode = GatfExecutorConfig.normalizeTraceMode(traceMode);
        RunOverrides.retries = retries;
        RunOverrides.workers = workers;
    }

    static void apply(GatfExecutorConfig c) {
        if (traceMode != null) c.setTraceMode(traceMode);
        if (retries != null) c.setSeleniumScriptRetryCount(retries);
        if (workers != null) c.setNumConcurrentExecutions(workers);
    }
}
