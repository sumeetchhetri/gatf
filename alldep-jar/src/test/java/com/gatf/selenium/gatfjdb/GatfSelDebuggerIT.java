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

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.Test;

import com.gatf.selenium.SeleniumTest;

/**
 * G5 — drives the real gatfjdb engine over JDI against {@link DebuggeeToy} (sel line N ↔ the "// LN"
 * statement). Covers the delta-075 fixes: breakpoint toggled before ClassPrepare (was NPE), breakpoint
 * REMOVAL (always failed — sel/java line mix-up), initial pause resolves n, step-over temp breakpoints
 * don't linger, awaitChange push wake-ups, idle-reaper semantics, bounded destroy.
 */
class GatfSelDebuggerIT {

    private GatfSelDebugger d;

    private void await(BooleanSupplier ok, String what) throws Exception {
        long t0 = System.currentTimeMillis(), v = d.getVersion();
        while (!ok.getAsBoolean()) {
            if (System.currentTimeMillis() - t0 > 20000) {
                throw new AssertionError("timeout: " + what + " p=" + d.getPrevLine() + " n=" + d.getNextLine());
            }
            v = d.awaitChange(v, 2000);
        }
    }

    @Test
    @SuppressWarnings({ "unchecked", "rawtypes" })
    void breakpointsStepContinueAndTeardown() throws Exception {
        List<String> src = Files.readAllLines(Paths.get("src/test/java/com/gatf/selenium/gatfjdb/DebuggeeToy.java"));
        List<Integer[]> lines = new ArrayList<Integer[]>();
        for (int i = 0; i < src.size(); i++) {
            int k = src.get(i).indexOf("// L");
            if (k >= 0) lines.add(new Integer[] { Integer.parseInt(src.get(i).substring(k + 4).trim()), i + 1, 2 });
        }
        assertThat(lines).hasSize(6);
        Map<String, List<List<Integer[]>>> map = new LinkedHashMap<String, List<List<Integer[]>>>();
        List<List<Integer[]>> fl = new ArrayList<List<Integer[]>>();
        fl.add(lines);
        map.put("toy.sel", fl);

        d = GatfSelDebugger.debugSession((Class<? extends SeleniumTest>) (Class) DebuggeeToy.class, new String[0], map, null);
        try {
            assertThat(d.setBreakPoint(6)).isTrue();    // before ClassPrepare
            assertThat(d.unsetBreakPoint(6)).isTrue();
            await(() -> d.getPrevLine() == 1 && d.getNextLine() == 2, "initial pause");

            assertThat(d.setBreakPoint(4)).isTrue();
            assertThat(d.unsetBreakPoint(4)).isTrue();  // removal
            assertThat(d.setBreakPoint(5)).isTrue();

            assertThat(d.enableStepOverRequest()).isTrue();
            await(() -> d.getPrevLine() == 2, "step over");
            d.resume();
            await(() -> d.getPrevLine() == 5, "continue to user breakpoint (skips removed bp + spent temp bps)");
            d.resume();
            await(() -> !d.getRunning(), "run to completion");

            assertThat(d.isActivityObserved()).isTrue();
            for (int i = 1; i < GatfSelDebugger.IDLE_CHECKS_BEFORE_REAP; i++) assertThat(d.isActivityObserved()).isTrue();
            assertThat(d.isActivityObserved()).isFalse(); // idle long enough → reapable
        } finally {
            d.destroy(); // bounded, tolerates an already-exited VM
        }
    }
}
