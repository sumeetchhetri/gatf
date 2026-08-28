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
package com.gatf.selenium;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** T1 backend-unit: the Phase-7 trailing end-of-line // handling in the transpiler. The full
 *  seleasy->Java codegen path is exercised by the API/e2e layers, not by blind golden strings. */
class CommandTest {

    @Test
    void stripsWhitespacePrecededTrailingComment() {
        assertThat(Command.stripTrailingLineComment("config app.properties //note"))
                .isEqualTo("config app.properties");
        assertThat(Command.stripTrailingLineComment("x // y")).isEqualTo("x");
    }

    @Test
    void preservesUrlSchemeAndUnspacedSlashes() {
        assertThat(Command.stripTrailingLineComment("goto https://example.com"))
                .isEqualTo("goto https://example.com");   // '//' preceded by ':' is not a comment
        assertThat(Command.stripTrailingLineComment("a//b")).isEqualTo("a//b");
    }

    @Test
    void leavesOrdinaryAndTooShortLinesUntouched() {
        assertThat(Command.stripTrailingLineComment("open chrome")).isEqualTo("open chrome");
        assertThat(Command.stripTrailingLineComment("ab")).isEqualTo("ab");
        assertThat(Command.stripTrailingLineComment(null)).isNull();
    }
}
