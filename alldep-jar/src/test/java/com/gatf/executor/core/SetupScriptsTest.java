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

class SetupScriptsTest {

    @Test
    void setupScriptsAreNeverSuiteScripts() {
        // M5/M6: loginOnce / globalSetup / globalTeardown run on their own; blank settings exclude nothing
        String[] suite = { "ui/login.sel", "./setup/global.SEL", "ui/cart.sel", "setup\\teardown.sel" };
        assertThat(SetupScripts.exclude(suite, null, "setup/global.sel", "setup/teardown.sel"))
                .containsExactly("ui/login.sel", "ui/cart.sel");
        assertThat(SetupScripts.exclude(suite, "", " ", null)).containsExactly(suite);
    }

    @Test
    void runPrefixNamesTheKind() {
        assertThat(SetupScripts.runPrefix("globalTeardown")).isEqualTo("Setup-globalTeardown");
    }
}
