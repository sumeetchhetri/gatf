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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.gatf.coverage.ImpactFixture;

/** H6 (Δ156): `gatf test --changed` end to end on a fixture project: config, git range, coverage run, selection. */
class ChangedCliTest {

    static boolean hasGit() {
        try {
            return new ProcessBuilder("git", "--version").start().waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    static void git(Path dir, String... args) throws Exception {
        List<String> c = new ArrayList<>(Arrays.asList("git", "-c", "user.name=t", "-c", "user.email=t@t", "-c", "commit.gpgsign=false"));
        c.addAll(Arrays.asList(args));
        Process p = new ProcessBuilder(c).directory(dir.toFile()).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, p.waitFor(), out);
    }

    /** A project: gatf-config.json (UI suite, tests/a.sel + tests/b.sel), src/a.js + src/b.js, a coverage run in out/123. */
    static Path project(Path dir, boolean ui) throws Exception {
        Path r = Files.createDirectories(dir.resolve("p"));
        git(r, "init", "-q");
        Files.createDirectories(r.resolve("src"));
        Files.createDirectories(r.resolve("tests"));
        Files.writeString(r.resolve("gatf-config.json"), "{\"isSeleniumExecutor\":" + ui + ",\"testCaseDir\":\"tests\",\"outFilesDir\":\"out\","
                + "\"seleniumScripts\":[\"a.sel\",\"b.sel\",\"c.sel\"],\"loginOnce\":\"c.sel\"}");
        Files.writeString(r.resolve("src/a.js"), String.join("\n", ImpactFixture.A) + "\n");
        Files.writeString(r.resolve("src/b.js"), String.join("\n", ImpactFixture.B) + "\n");
        Files.writeString(r.resolve("src/app.css"), "body{}\n");
        for (String s : new String[] {"a", "b", "c"}) Files.writeString(r.resolve("tests/" + s + ".sel"), "open chrome\n");
        Files.writeString(r.resolve(".gitignore"), "out/\n");
        git(r, "add", "-A");
        git(r, "commit", "-q", "-m", "base");
        ImpactFixture.run(r, "har", true, System.currentTimeMillis() + 60_000);
        return r;
    }

    static String[] lines(ByteArrayOutputStream b) { return new String(b.toByteArray(), StandardCharsets.UTF_8).split("\n"); }

    static ChangedCli.Plan plan(Path r, ByteArrayOutputStream out, String... extra) {
        List<String> a = new ArrayList<>(Arrays.asList("gatf-config.json"));
        a.addAll(Arrays.asList(extra));
        TestCommandArgs t = TestCommandArgs.parse(a.toArray(new String[0]), r.toFile());
        return ChangedCli.plan(t, r.toFile(), new PrintStream(out, true));
    }

    @Test
    void aChangedFileRunsOnlyTheAffectedScript(@TempDir Path dir) throws Exception {
        if (!hasGit()) return;
        Path r = project(dir, true);
        Files.writeString(r.resolve("src/b.js"), Files.readString(r.resolve("src/b.js")).replace("z = z * 2;", "z = z * 5;"));
        git(r, "commit", "-q", "-am", "edit b");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ChangedCli.Plan p = plan(r, out, "--changed", "HEAD~1..HEAD", "--explain");
        String text = String.join("\n", lines(out));
        assertTrue(p.done, text);
        assertEquals(0, p.exitCode);
        assertTrue(text.contains("skip a.sel - the change is outside the code this test ran"), text);
        assertTrue(text.contains("run  b.sel - src/b.js line 3 is in code this test ran"), text);
        assertFalse(text.contains("c.sel"), "loginOnce is not a test: " + text);
        assertTrue(text.contains("1 of 2 script(s) selected (--explain: nothing was run)"), text);
        assertTrue(text.contains("mapped: src/b.js -> https://app.test/main.js by line"), text);

        out.reset();
        p = plan(r, out, "--changed", "HEAD~1..HEAD");
        assertFalse(p.done);
        assertEquals(Collections.singletonList("a.sel"), p.exclude);
        assertEquals("--changed HEAD~1..HEAD (1/2 scripts)", p.note);
    }

    @Test
    void unsafeChangesRunEverythingWithAReason(@TempDir Path dir) throws Exception {
        if (!hasGit()) return;
        Path r = project(dir, true);
        Files.writeString(r.resolve("src/app.css"), "body{color:red}\n");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ChangedCli.Plan p = plan(r, out, "--changed", "HEAD");      // uncommitted work
        String text = String.join("\n", lines(out));
        assertFalse(p.done, text);
        assertTrue(p.exclude.isEmpty());
        assertTrue(text.contains("running every test:") && text.contains("src/app.css: a stylesheet changed"), text);

        // a stale coverage run (recorded before the base commit) is not used
        out.reset();
        Files.writeString(r.resolve("src/app.css"), "body{}\n");
        Path stale = ImpactFixture.run(dir.resolve("s"), "har", true, 1_000L);
        Files.writeString(r.resolve("src/b.js"), Files.readString(r.resolve("src/b.js")).replace("z = z * 2;", "z = z * 5;"));
        p = plan(r, out, "--changed", "HEAD", "--coverage-run", stale.toString(), "--explain");
        text = String.join("\n", lines(out));
        assertTrue(text.contains("older than the base commit"), text);
        assertTrue(text.contains("run  a.sel") && text.contains("run  b.sel"), text);

        // nothing changed: nothing runs
        out.reset();
        git(r, "checkout", "-q", "--", "src/b.js");
        p = plan(r, out, "--changed", "HEAD");
        assertTrue(p.done);
        assertTrue(String.join("\n", lines(out)).contains("nothing to run"), String.join("\n", lines(out)));
    }

    @Test
    void anApiSuiteRunsEverythingAndABadRangeIsAnError(@TempDir Path dir) throws Exception {
        if (!hasGit()) return;
        Path r = project(dir, false);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ChangedCli.Plan p = plan(r, out, "--changed", "HEAD");
        assertFalse(p.done);
        assertTrue(p.exclude.isEmpty());
        assertTrue(String.join("\n", lines(out)).contains("an API suite"));
        assertThrows(IllegalArgumentException.class, () -> plan(r, new ByteArrayOutputStream(), "--changed", "nope..HEAD"));
        assertThrows(IllegalArgumentException.class, () -> plan(dir, new ByteArrayOutputStream(), "--changed", "HEAD"));
    }

    @Test
    void latestRunPrefersTheNewestFirstRound(@TempDir Path dir) throws Exception {
        Path out = Files.createDirectories(dir.resolve("out"));
        for (String n : new String[] {"100", "200", "200-1"}) {
            Path d = Files.createDirectories(out.resolve(n));
            Files.write(d.resolve("x-trace.zip"), new byte[] {0});
        }
        Files.setLastModifiedTime(out.resolve("100/x-trace.zip"), java.nio.file.attribute.FileTime.fromMillis(1_000));
        Files.setLastModifiedTime(out.resolve("200/x-trace.zip"), java.nio.file.attribute.FileTime.fromMillis(2_000));
        Files.setLastModifiedTime(out.resolve("200-1/x-trace.zip"), java.nio.file.attribute.FileTime.fromMillis(3_000));
        assertEquals(out.resolve("200"), ChangedCli.latestRun(Collections.singletonList(out)));
        assertEquals(null, ChangedCli.latestRun(Collections.singletonList(dir.resolve("none"))));
    }
}
