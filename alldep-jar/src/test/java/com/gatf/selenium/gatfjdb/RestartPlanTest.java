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
package com.gatf.selenium.gatfjdb;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import org.junit.jupiter.api.Test;

/** Δ163n bug 5: which frame a debugger "restart subtest" unwinds to, and where the replay pauses. */
class RestartPlanTest {

    @Test
    void innermostSubtestSkipsFuncsAndOtherClasses() {
        // top of stack first: a func (__st__1) called from subtest __st__2, under ___invoke_sub_test_dyn___ (other class: "")
        assertThat(RestartPlan.innermostSubtest(Arrays.asList("__st__1", "", "", "__st__2", "_execute"), new HashSet<>(Arrays.asList("__st__2"))))
                .isEqualTo(3);
        assertThat(RestartPlan.innermostSubtest(Arrays.asList("__st__2", "", "_execute"), new HashSet<>(Arrays.asList("__st__2")))).isEqualTo(0);
        assertThat(RestartPlan.innermostSubtest(Arrays.asList("_execute"), new HashSet<>(Arrays.asList("__st__2")))).isEqualTo(-1);
        assertThat(RestartPlan.innermostSubtest(Arrays.asList("__st__1", "_execute"), new HashSet<>(Arrays.asList("__st__2")))).isEqualTo(-1);
        // a script without subtests: paused in a func there is nothing to restart (verifier round: it restarted the func)
        assertThat(RestartPlan.innermostSubtest(Arrays.asList("", "__st__7"), Collections.emptySet())).isEqualTo(-1);
        assertThat(RestartPlan.innermostSubtest(Arrays.asList("__st__7"), null)).isEqualTo(-1);
    }

    @Test
    void replayPausesOnTheBodysFirstStatement() {
        java.util.Map<Integer, Integer> j2s = new java.util.HashMap<>();
        j2s.put(121, 10);   // subtest "st1" header
        j2s.put(124, 12);   // first statement
        j2s.put(125, 13);
        assertThat(RestartPlan.firstBodyLine(Arrays.asList(119, 121, 122, 124, 125), j2s)).isEqualTo(3);
        assertThat(RestartPlan.firstBodyLine(Arrays.asList(119, 121, 122), j2s)).isEqualTo(1);   // empty body: the header
        assertThat(RestartPlan.firstBodyLine(Arrays.asList(1, 2), j2s)).isEqualTo(-1);
        assertThat(RestartPlan.firstBodyLine(Collections.emptyList(), j2s)).isEqualTo(-1);
    }
}
