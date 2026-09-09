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

import org.junit.jupiter.api.Test;

/** G8 — contract of the run-loop schedule shared by doSeleniumTest / doExecute. */
class LoadRunScheduleTest {

    static GatfExecutorConfig cfg(long loadMs, int samples, int repeat) {
        GatfExecutorConfig c = new GatfExecutorConfig();
        c.setLoadTestingTime(loadMs);
        c.setLoadTestingReportSamples(samples);
        c.setRepeatSuiteExecutionNum(repeat);
        return c;
    }

    @Test
    void sampleCountIsClampedTo3to100() {
        assertThat(new LoadRunSchedule(true, cfg(60_000, 1, 0)).reportSampleTimeMs()).isEqualTo(30_000L);  // clamp 3
        assertThat(new LoadRunSchedule(true, cfg(99_000, 500, 0)).reportSampleTimeMs()).isEqualTo(1_000L);  // clamp 100
        assertThat(new LoadRunSchedule(false, cfg(60_000, 5, 0)).reportSampleTimeMs()).isEqualTo(0L);       // not load
    }

    @Test
    void notLoadTestingReportsOnceAndFinishesWithoutBreaking() {
        GatfExecutorConfig c = cfg(0, 3, 0);
        LoadRunSchedule s = new LoadRunSchedule(false, c);
        assertThat(s.next(false, c, c, 0L)).isFalse();
        assertThat(s.dorep).isTrue();
        assertThat(s.done).isTrue();
    }

    @Test
    void timeBoxedLoadTestSamplesOnScheduleThenStops() {
        GatfExecutorConfig c = cfg(40_000, 5, 0);                 // interval 10s
        long[] now = { 0 };
        LoadRunSchedule s = new LoadRunSchedule(true, c, () -> now[0]);
        assertThat(s.next(true, c, c, 0L)).isFalse();             // t=0: first run always reported
        assertThat(s.dorep).isTrue();
        s.advance();
        now[0] = 5_000;
        assertThat(s.next(true, c, c, 0L)).isFalse();
        assertThat(s.dorep).isFalse();                            // before the next 10s mark
        now[0] = 10_000;
        s.next(true, c, c, 0L);
        assertThat(s.dorep).isTrue();                             // 10s mark reached
        s.advance();
        now[0] = 40_001;
        assertThat(s.next(true, c, c, 0L)).isTrue();              // time budget exhausted -> break
        assertThat(s.done).isTrue();
    }

    @Test
    void repeatSuiteRunsExactlyNTimes() {
        GatfExecutorConfig c = cfg(0, 3, 3);
        LoadRunSchedule s = new LoadRunSchedule(true, c, () -> 0L);
        int runs = 0;
        while (!s.next(true, c, c, 0L)) {
            assertThat(s.dorep).isTrue();
            runs++;
            s.advance();
        }
        assertThat(runs).isEqualTo(3);
        assertThat(s.loadTestRunNum()).isEqualTo(4);
    }
}
