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
package com.gatf.executor.perf;

import com.gatf.executor.core.AcceptanceTestContext;
import com.gatf.executor.core.TestCase;
import com.gatf.executor.report.TestCaseReport;

/**
 * M13: a load-test tool behind {@code perfConfig.type}. The built-ins are wrk, wrk2, wperfk, vegeta and autocannon.
 * A third-party engine ships in a jar in {@code plugins/} with a
 * {@code META-INF/services/com.gatf.executor.perf.PerfEngine} entry.
 * <p>
 * An engine reads the shared {@code PerformanceConfig} ({@code tc.getPerfConfig()}: connections, threads, rate,
 * durationSeconds, timeout, latency, filePath, extras) and the request (method, URL, headers, body). It adds one result
 * map per target URL to {@code tcr.getPerfResult()} and sets the report status and text. GATF then adds the common
 * {@link PerfSummary} keys to every map.
 */
public interface PerfEngine {

    /** The {@code perfConfig.type} value, [a-z][a-z0-9_-]*. */
    String name();

    /** One line for docs and error messages. */
    default String description() {
        return "";
    }

    void execute(AcceptanceTestContext context, TestCase tc, TestCaseReport tcr, boolean singleExecutionContext) throws Exception;
}
