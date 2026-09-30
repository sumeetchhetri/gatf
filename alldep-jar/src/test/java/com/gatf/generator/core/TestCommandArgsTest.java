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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Q7: `gatf test` argument parsing. */
class TestCommandArgsTest {

    @Test
    void parsesEveryOptionInBothForms() {
        TestCommandArgs a = TestCommandArgs.parse(new String[] { "cfg.xml", "--grep", "users", "--grep-invert=Delete", "-t", "@smoke",
                "--reporter", "junit", "--trace", "retain-on-failure", "--retries", "2", "-j", "4" }, new File("."));
        assertThat(a.config).isEqualTo("cfg.xml");
        assertThat(a.grep).containsExactly("users");
        assertThat(a.grepInvert).containsExactly("Delete");
        assertThat(a.tags).containsExactly("@smoke");
        assertThat(a.reporters).isEqualTo("junit");
        assertThat(a.traceMode).isEqualTo("retain-on-failure");
        assertThat(a.retries).isEqualTo(2);
        assertThat(a.workers).isEqualTo(4);
        assertThat(a.forbidOnly).isNull();
    }

    @Test
    void forbidOnlyIsAFlag() {
        // E10: no value argument, so the next token is still read (here the config)
        TestCommandArgs a = TestCommandArgs.parse(new String[] { "--forbid-only", "cfg.xml" }, new File("."));
        assertThat(a.forbidOnly).isTrue();
        assertThat(a.config).isEqualTo("cfg.xml");
        assertThat(TestCommandArgs.parse(new String[] { "cfg.xml", "--forbid-only=false" }, new File(".")).forbidOnly).isFalse();
        assertThat(TestCommandArgs.USAGE).contains("[--forbid-only]");
    }

    @Test
    void defaultsToTheConfigInTheWorkingDirectory(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("gatf-config.xml"), "<gatf-execute-config/>");
        assertThat(TestCommandArgs.parse(new String[0], dir.toFile()).config).isEqualTo("gatf-config.xml");
        Files.writeString(dir.resolve("gatf-config.json"), "{}");
        assertThat(TestCommandArgs.parse(new String[0], dir.toFile()).config).isEqualTo("gatf-config.json");   // json preferred
    }

    @Test
    void rejectsBadInput(@TempDir Path empty) {
        assertThatThrownBy(() -> TestCommandArgs.parse(new String[] { "--grep" }, new File("."))).hasMessageContaining("--grep needs a value");
        assertThatThrownBy(() -> TestCommandArgs.parse(new String[] { "x.xml", "--shard", "1/2" }, new File("."))).hasMessageContaining("Unknown option --shard");
        assertThatThrownBy(() -> TestCommandArgs.parse(new String[] { "a.xml", "b.xml" }, new File("."))).hasMessageContaining("Only one config");
        assertThatThrownBy(() -> TestCommandArgs.parse(new String[] { "x.xml", "--retries", "two" }, new File("."))).hasMessageContaining("expects a number");
        assertThatThrownBy(() -> TestCommandArgs.parse(new String[] { "x.xml", "--workers", "0" }, new File("."))).hasMessageContaining(">= 1");
        assertThatThrownBy(() -> TestCommandArgs.parse(new String[0], empty.toFile())).hasMessageContaining("No config given");
    }
}
