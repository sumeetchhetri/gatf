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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Δ160 — {@code gatf export playwright|selenium-java <file|dir> [-o] [--strict-locators] [--force]}. */
class ExportCliTest {

    private ByteArrayOutputStream out, err;

    private int run(Path cwd, String... args) throws Exception {
        out = new ByteArrayOutputStream();
        err = new ByteArrayOutputStream();
        return ExportCli.run(args, cwd.toFile(), new PrintStream(out, true, "UTF-8"), new PrintStream(err, true, "UTF-8"));
    }

    private static void write(Path p, String s) throws Exception {
        Files.createDirectories(p.getParent());
        Files.write(p, s.getBytes(StandardCharsets.UTF_8));
    }

    private static String read(Path p) throws Exception {
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    }

    @Test
    void singleFile(@TempDir Path dir) throws Exception {
        write(dir.resolve("login.sel"), "open chrome\nclick role@button[name=Save]\nframe main\n");
        assertEquals(0, run(dir, "playwright", "login.sel"));
        String spec = read(dir.resolve("login.spec.ts"));
        assertTrue(spec.contains("page.getByRole('button', { name: /^Save$/i }).first().click();"));
        assertTrue(spec.contains("// TODO(gatf-export): frame main"));
        String o = out.toString("UTF-8");
        assertTrue(o.contains("#3 frame main"), o);
        assertEquals(1, run(dir, "playwright", "login.sel"), "exists without --force");
        assertTrue(err.toString("UTF-8").contains("--force"));
        assertEquals(0, run(dir, "playwright", "login.sel", "--force", "--strict-locators"));
        assertTrue(read(dir.resolve("login.spec.ts")).contains("page.getByRole('button', { name: /^Save$/i }).click();"));
    }

    @Test
    void directoryMirrorsTreeAndSkipsLibraries(@TempDir Path dir) throws Exception {
        write(dir.resolve("tests/a.sel"), "open chrome\ngoto ${baseUrl}\n");
        write(dir.resolve("tests/sub/b.sel"), "open chrome\nclick id@x\n");
        write(dir.resolve("tests/lib/funcs.sel"), "func \"f\"\n{\nclick id@y\n}\n");
        assertEquals(0, run(dir, "playwright", "tests", "-o", "pw"));
        assertTrue(Files.exists(dir.resolve("pw/a.spec.ts")));
        assertTrue(Files.exists(dir.resolve("pw/sub/b.spec.ts")));
        assertFalse(Files.exists(dir.resolve("pw/lib/funcs.spec.ts")));
        String report = read(dir.resolve("pw/export-report.txt"));
        assertTrue(report.contains("skipped lib/funcs.sel"), report);
        assertTrue(report.contains("${baseUrl} → GATF_BASEURL"), report);
    }

    @Test
    void environmentCollisionsFailAndNameBoth(@TempDir Path dir) throws Exception {
        write(dir.resolve("one.sel"), "open chrome\ngoto ${base_url}\ngoto ${base.url}\n");
        assertEquals(1, run(dir, "playwright", "one.sel"));
        String e = err.toString("UTF-8");
        assertTrue(e.contains("${base_url}") && e.contains("${base.url}") && e.contains("GATF_BASE_URL"), e);
        assertFalse(Files.exists(dir.resolve("one.spec.ts")));

        write(dir.resolve("d/a.sel"), "open chrome\ngoto ${base_url}\n");
        write(dir.resolve("d/b.sel"), "open chrome\ngoto ${base-url}\n");
        assertEquals(1, run(dir, "playwright", "d", "-o", "o"));
        e = err.toString("UTF-8");
        assertTrue(e.contains("a.sel: ${base_url}") && e.contains("b.sel: ${base-url}"), e);
    }

    @Test
    void reportIsNotOverwrittenWithoutForce(@TempDir Path dir) throws Exception {   // Δ160a
        write(dir.resolve("t/a.sel"), "open chrome\nclick id@x\n");
        write(dir.resolve("pw/export-report.txt"), "mine");
        assertEquals(1, run(dir, "playwright", "t", "-o", "pw"));
        assertTrue(err.toString("UTF-8").contains("export-report.txt exists"), err.toString("UTF-8"));
        assertEquals("mine", read(dir.resolve("pw/export-report.txt")));
        assertFalse(Files.exists(dir.resolve("pw/a.spec.ts")), "nothing written when a check fails");
        assertEquals(0, run(dir, "playwright", "t", "-o", "pw", "--force"));
        assertTrue(read(dir.resolve("pw/export-report.txt")).contains("a.spec.ts"));
    }

    @Test
    void symlinkedTargetsAreRefused(@TempDir Path dir) throws Exception {   // Δ160a
        write(dir.resolve("t/a.sel"), "open chrome\nclick id@x\n");
        Path outside = Files.createDirectories(dir.resolve("outside"));
        Files.createDirectories(dir.resolve("pw"));
        Files.createSymbolicLink(dir.resolve("pw/a.spec.ts"), outside.resolve("evil.ts"));   // dangling
        assertEquals(1, run(dir, "playwright", "t", "-o", "pw", "--force"));
        assertTrue(err.toString("UTF-8").contains("symbolic link"), err.toString("UTF-8"));
        assertFalse(Files.exists(outside.resolve("evil.ts")));

        write(dir.resolve("t2/sub/b.sel"), "open chrome\nclick id@x\n");
        Files.createDirectories(dir.resolve("pw2"));
        Files.createSymbolicLink(dir.resolve("pw2/sub"), outside);                            // a directory pointing out
        assertEquals(1, run(dir, "playwright", "t2", "-o", "pw2", "--force"));
        assertTrue(err.toString("UTF-8").contains("resolves outside"), err.toString("UTF-8"));
        assertFalse(Files.exists(outside.resolve("b.spec.ts")));

        Files.createSymbolicLink(dir.resolve("single.spec.ts"), outside.resolve("single.ts"));
        assertEquals(1, run(dir, "playwright", "t/a.sel", "-o", "single.spec.ts", "--force"));
        assertFalse(Files.exists(outside.resolve("single.ts")));

        Files.createSymbolicLink(dir.resolve("linked-out"), outside);                         // -o itself a link: resolved, allowed
        assertEquals(0, run(dir, "playwright", "t", "-o", "linked-out"));
        assertTrue(Files.exists(outside.resolve("a.spec.ts")));
    }

    @Test
    void seleniumJavaWritesTestClassesAndRefusesClassNameClashes(@TempDir Path dir) throws Exception {   // Δ161
        write(dir.resolve("t/login-flow.sel"), "open chrome\ngoto ${baseUrl}\nclick role@button[name=Save]\n");
        assertEquals(0, run(dir, "selenium-java", "t/login-flow.sel"));
        String java = read(dir.resolve("LoginFlowTest.java"));
        assertTrue(java.contains("class LoginFlowTest {") && java.contains("env(\"GATF_BASEURL\")"), java);
        assertTrue(out.toString("UTF-8").contains("LoginFlowTest.java"));
        write(dir.resolve("t/login_flow.sel"), "open chrome\nclick id@x\n");
        assertEquals(1, run(dir, "selenium-java", "t", "-o", "sj"));
        assertTrue(err.toString("UTF-8").contains("already exports to LoginFlowTest.java"), err.toString("UTF-8"));
        assertTrue(Files.exists(dir.resolve("sj/LoginFlowTest.java")), "the first script is still exported");
    }

    @Test
    void usageErrors(@TempDir Path dir) throws Exception {
        assertEquals(1, run(dir, "cypress", "x.sel"));
        assertTrue(err.toString("UTF-8").contains("unknown export target"));
        assertEquals(1, run(dir, "playwright", "missing.sel"));
        assertTrue(err.toString("UTF-8").contains("input not found"));
        assertEquals(1, run(dir, "playwright"));
        assertTrue(ExportCli.USAGE.startsWith("gatf export <playwright|selenium-java>"), ExportCli.USAGE);   // Δ161
    }
}
