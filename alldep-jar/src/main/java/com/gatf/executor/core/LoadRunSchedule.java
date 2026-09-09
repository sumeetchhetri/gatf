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

import java.util.function.LongSupplier;

/**
 * G8 — the load-test run schedule shared by {@code doSeleniumTest} and {@code doExecute}, which used to
 * carry identical copies of this logic: report-sample count clamp (3..100), sample interval, and the
 * per-iteration "report this sample? / finished?" decision, for both time-boxed load tests
 * (loadTestingTime) and repeat-N suites (repeatSuiteExecutionNum).
 *
 * Semantics are kept exactly, including where boxed config values are unboxed (only inside the
 * load-testing branch), so a null config value cannot cause an NPE the original code did not.
 */
final class LoadRunSchedule {

    private final long reportSampleTimeMs;
    private final LongSupplier clock;
    private int loadTestRunNum = 1;
    private int loadTstReportsCount = 0;

    /** Outcome of the last {@link #next}: report (sample) this iteration / the run is finished. */
    boolean dorep;
    boolean done;

    LoadRunSchedule(boolean isLoadTestingEnabled, GatfExecutorConfig configuration) {
        this(isLoadTestingEnabled, configuration, System::currentTimeMillis);
    }

    LoadRunSchedule(boolean isLoadTestingEnabled, GatfExecutorConfig configuration, LongSupplier clock) {
        this.clock = clock;
        int loadTestingReportSamplesNum = configuration.getLoadTestingReportSamples();
        if (loadTestingReportSamplesNum < 3)
            loadTestingReportSamplesNum = 3;
        if (loadTestingReportSamplesNum > 100)
            loadTestingReportSamplesNum = 100;
        this.reportSampleTimeMs = isLoadTestingEnabled ? configuration.getLoadTestingTime() / (loadTestingReportSamplesNum - 1) : 0;
    }

    /**
     * Decide this iteration (sets {@link #dorep} / {@link #done}).
     * @return true when the caller must stop the run loop now (was: {@code if (done) break;})
     */
    boolean next(boolean isLoadTestingEnabled, GatfExecutorConfig ctxConfig, GatfExecutorConfig configuration, Long startTime) {
        if (isLoadTestingEnabled) {
            long currentTime = clock.getAsLong();

            if (ctxConfig.getRepeatSuiteExecutionNum() > 1) {
                done = ctxConfig.getRepeatSuiteExecutionNum() == loadTestRunNum - 1;
                dorep = true;
            } else {
                done = (currentTime - startTime) > configuration.getLoadTestingTime();

                long elapsedFraction = (currentTime - startTime);

                dorep = done || loadTstReportsCount == 0;

                if (!dorep && elapsedFraction >= reportSampleTimeMs * loadTstReportsCount) {
                    dorep = true;
                }
            }
            return done;
        }
        dorep = true;
        done = true;
        return false;
    }

    /** A sample was reported (was: {@code loadTestRunNum++; loadTstReportsCount++;}). */
    void advance() {
        loadTestRunNum++;
        loadTstReportsCount++;
    }

    long reportSampleTimeMs() { return reportSampleTimeMs; }
    int loadTestRunNum() { return loadTestRunNum; }
    int loadTstReportsCount() { return loadTstReportsCount; }
}
