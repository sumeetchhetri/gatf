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

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
    void absoluteSuitePathsMatchConfigRelativeSetupNames(@TempDir Path dir) throws Exception {
        // E1: seleniumScript naming a folder gives absolute paths; loginOnce / globalSetup stay config-relative
        Path tests = Files.createDirectories(dir.resolve("tests"));
        Files.createDirectories(tests.resolve("setup"));
        for (String f : new String[] { "setup/login.sel", "setup/global.sel", "cart.sel" }) Files.writeString(tests.resolve(f), "open chrome\n");
        Function<String, File> resolver = n -> new File(n).isAbsolute() ? new File(n) : tests.resolve(n).toFile();
        String login = tests.resolve("setup/login.sel").toString(), global = tests.resolve("./setup/../setup/global.sel").toString(),
                cart = tests.resolve("cart.sel").toString();
        String[] suite = { login, cart, global };
        assertThat(SetupScripts.exclude(suite, "setup/login.sel", "setup/global.sel")).containsExactly(suite);   // names only: no match
        assertThat(SetupScripts.excludeResolved(suite, resolver, "setup/login.sel", "./setup/global.sel", null)).containsExactly(cart);
        assertThat(SetupScripts.excludeResolved(new String[] { "setup/login.sel", cart }, resolver, login)).containsExactly(cart);   // and back
        assertThat(SetupScripts.excludeResolved(suite, n -> { throw new IllegalStateException("no base path"); }, "setup/login.sel")).containsExactly(suite);
    }

    @Test
    void seleniumScriptExpansion(@TempDir Path dir) throws Exception {
        // E4: shared by the executor and `gatf docs`: an existing path lists every *.sel under the test-case dir
        Path tests = Files.createDirectories(dir.resolve("tests"));
        Files.createDirectories(tests.resolve("ui"));
        Files.writeString(tests.resolve("ui/a.sel"), "");
        Files.writeString(tests.resolve("b.sel"), "");
        Files.writeString(tests.resolve("notes.txt"), "");
        assertThat(SetupScripts.expandSeleniumScript(tests.toString(), tests.toFile())).containsExactlyInAnyOrder(
                tests.resolve("ui/a.sel").toFile().getAbsolutePath(), tests.resolve("b.sel").toFile().getAbsolutePath());
    }

    @Test
    void seleniumScriptFolderFileAndListForms() throws Exception {
        java.nio.file.Path tests = Files.createTempDirectory("gatf-ss");
        Files.createDirectories(tests.resolve("ui/smoke"));
        Files.writeString(tests.resolve("ui/smoke/s1.sel"), "");
        Files.writeString(tests.resolve("ui/a.sel"), "");
        Files.writeString(tests.resolve("b.sel"), "");
        Files.writeString(tests.resolve("suite.txt"), "# nightly\nui/a.sel\n\n  b.sel  \n");
        // a sub-folder relative to the test-case dir: only its scripts, not the whole test dir
        assertThat(SetupScripts.expandSeleniumScript("ui/smoke", tests.toFile())).containsExactly(
                tests.resolve("ui/smoke/s1.sel").toFile().getAbsolutePath());
        assertThat(SetupScripts.expandSeleniumScript("b.sel", tests.toFile())).containsExactly(tests.resolve("b.sel").toFile().getAbsolutePath());
        assertThat(SetupScripts.expandSeleniumScript("suite.txt", tests.toFile())).containsExactly("ui/a.sel", "b.sel");
        try {
            SetupScripts.expandSeleniumScript("missing.txt", tests.toFile());
            throw new AssertionError("expected FileNotFoundException");
        } catch (java.io.FileNotFoundException e) {
            assertThat(e.getMessage()).contains("missing.txt");
        }
    }

    @Test
    void runPrefixNamesTheKind() {
        assertThat(SetupScripts.runPrefix("globalTeardown")).isEqualTo("Setup-globalTeardown");
    }
}
