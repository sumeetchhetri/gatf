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

import java.util.List;
import java.util.Set;

/**
 * Δ163n (bug 5): the pure part of "restart the current subtest" in the debugger. Command emits every subtest (and hook /
 * func) as a method {@code __st__N} of the script class, always called through {@code ___invoke_sub_test_dyn___}, which
 * re-invokes it on a {@code RestartsubtestException}. The debugger throws that exception in the paused thread, so it
 * needs the innermost {@code __st__N} frame and a first line to pause on in the replay.
 */
final class RestartPlan {

    private RestartPlan() {
    }

    /**
     * Index (0 = top of stack) of the innermost subtest frame; {@code methodNames} has "" for frames of other classes. Funcs
     * and hooks are {@code __st__N} methods too, so only the real subtests' method names ({@code subtests}) count: pausing
     * inside a func called from a subtest restarts the subtest, and a script without subtests has nothing to restart. -1 if none.
     */
    static int innermostSubtest(List<String> methodNames, Set<String> subtests) {
        if (subtests == null || subtests.isEmpty()) return -1;
        for (int i = 0; i < methodNames.size(); i++) {
            String n = methodNames.get(i);
            if (n != null && subtests.contains(n)) return i;
        }
        return -1;
    }

    /**
     * Index of the method's java line to pause on in the replay: the first one mapped to a .sel line that is not the
     * subtest's header (the first mapped line, {@code subtest "x"}), so the replay stops on the body's first statement;
     * the header when the body has no mapped line; -1 when nothing maps.
     */
    static int firstBodyLine(List<Integer> methodJavaLines, java.util.Map<Integer, Integer> javaToSel) {
        int first = -1;
        for (int i = 0; i < methodJavaLines.size(); i++) {
            Integer sel = javaToSel.get(methodJavaLines.get(i));
            if (sel == null) continue;
            if (first == -1) first = i;
            else if (!sel.equals(javaToSel.get(methodJavaLines.get(first)))) return i;
        }
        return first;
    }
}
