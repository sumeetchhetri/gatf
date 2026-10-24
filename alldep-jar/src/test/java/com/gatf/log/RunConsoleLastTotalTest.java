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
package com.gatf.log;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Δ163o (#1): the CLI's "nothing was executed" signal (GatfCli.suiteExit turns lastTotal 0 into exit 1). */
class RunConsoleLastTotalTest {

    @AfterEach
    void reset() {
        RunConsole.sink = null;
        System.clearProperty("gatf.color");
        System.clearProperty("gatf.reporters");
    }

    @Test
    void zeroWhenNothingRanOtherwiseTheCount() throws Exception {
        System.setProperty("gatf.color", "never");
        System.setProperty("gatf.reporters", "none");
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        RunConsole.sink = new PrintStream(buf, true, "UTF-8");
        RunConsole.activate();
        RunConsole.suite("UI (seleasy)", "target/runconsole-test", 1, true);
        assertThat(RunConsole.finish()).isZero();
        assertThat(RunConsole.lastTotal()).isZero();
        assertThat(buf.toString("UTF-8")).contains("No tests were executed.");
        RunConsole.activate();
        RunConsole.suite("UI (seleasy)", "target/runconsole-test", 1, true);
        RunConsole.result("a.sel", "t", "a.sel \u203a t", RunConsole.Status.PASSED, 5, null, null);
        assertThat(RunConsole.finish()).isZero();
        assertThat(RunConsole.lastTotal()).isEqualTo(1);
    }
}
