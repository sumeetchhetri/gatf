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

/** Δ160 — {@code gatf export playwright <file|dir> [-o] [--strict-locators] [--force]}. */
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
    void usageErrors(@TempDir Path dir) throws Exception {
        assertEquals(1, run(dir, "cypress", "x.sel"));
        assertTrue(err.toString("UTF-8").contains("unknown export target"));
        assertEquals(1, run(dir, "playwright", "missing.sel"));
        assertTrue(err.toString("UTF-8").contains("input not found"));
        assertEquals(1, run(dir, "playwright"));
        assertTrue(ExportCli.USAGE.startsWith("gatf export <playwright>"));
    }
}
