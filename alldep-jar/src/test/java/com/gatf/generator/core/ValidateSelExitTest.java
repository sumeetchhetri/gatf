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
package com.gatf.generator.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.jupiter.api.Test;

/** Δ163o (#8): -validate-sel exited 0 on FAILURE. */
class ValidateSelExitTest {

    @Test
    void failureAndGarbageExitOne() {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        PrintStream err = new PrintStream(b, true);
        assertThat(GatfCli.validateSelExit("{\"status\": \"SUCCESS\"}", err)).isZero();
        assertThat(GatfCli.validateSelExit("{\"status\":\"FAILURE\",\"error\":\"line 3: bad\"}", err)).isEqualTo(1);
        assertThat(GatfCli.validateSelExit("not json", err)).isEqualTo(1);
        assertThat(b.toString()).contains("line 3: bad");
    }

    @Test
    void aFinishedRunExitsOneWhenATestFailedOrNothingRan() {
        // Δ163o (#1): "No tests were executed." used to exit 0, so CI went green when the browser could not start
        assertThat(GatfCli.suiteExit(0, 3)).isZero();
        assertThat(GatfCli.suiteExit(2, 3)).isEqualTo(1);
        assertThat(GatfCli.suiteExit(0, 0)).isEqualTo(1);
        assertThat(GatfCli.suiteExit(0, -1)).isZero();   // RunConsole never finished (not a CLI run): unchanged
    }

    @Test
    void noConfigIsAMessageNotAnNpe(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp) {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> com.gatf.selenium.Command.validateSel(
                new String[] { "-validate-sel", "x.sel", tmp.resolve("gatf-config.json").toString(), tmp.toString() }, null, false))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("no gatf-config.json").hasMessageContaining(tmp.toString());
    }
}

