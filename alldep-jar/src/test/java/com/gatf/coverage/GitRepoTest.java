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
package com.gatf.coverage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** H6 (Δ156): ranges against a real git repo, and the whole pipeline (git + trace bundles + index + selector). */
class GitRepoTest {

    static boolean hasGit() {
        try {
            return new ProcessBuilder("git", "--version").start().waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    static void git(Path dir, String... args) throws Exception {
        List<String> c = new ArrayList<>(Arrays.asList("git", "-c", "user.name=t", "-c", "user.email=t@t", "-c", "commit.gpgsign=false",
                "-c", "init.defaultBranch=main"));
        c.addAll(Arrays.asList(args));
        Process p = new ProcessBuilder(c).directory(dir.toFile()).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, p.waitFor(), String.join(" ", args) + ": " + out);
    }

    static Path repo(Path dir) throws Exception {
        Path r = Files.createDirectories(dir.resolve("repo"));
        git(r, "init", "-q");
        Files.createDirectories(r.resolve("src"));
        Files.createDirectories(r.resolve("tests"));
        Files.writeString(r.resolve("src/a.js"), String.join("\n", ImpactFixture.A) + "\n");
        Files.writeString(r.resolve("src/b.js"), String.join("\n", ImpactFixture.B) + "\n");
        Files.writeString(r.resolve("tests/a.sel"), "open chrome\n");
        Files.writeString(r.resolve("tests/b.sel"), "open chrome\n");
        Files.writeString(r.resolve(".gitignore"), "out/\n");
        git(r, "add", "-A");
        git(r, "commit", "-q", "-m", "base");
        return r;
    }

    @Test
    void rangesAndChanges(@TempDir Path dir) throws Exception {
        if (!hasGit()) return;
        Path r = repo(dir);
        Files.writeString(r.resolve("src/b.js"), Files.readString(r.resolve("src/b.js")).replace("z = z * 2;", "z = z * 5;"));
        git(r, "commit", "-q", "-am", "edit b");

        GitRepo g = GitRepo.open(r.toFile(), "HEAD~1..HEAD");
        List<DiffParser.FileChange> c = g.changes();
        assertEquals(1, c.size(), c.toString());
        assertEquals("src/b.js", c.get(0).path);
        assertEquals(3, c.get(0).hunks.get(0).oldStart);
        assertEquals("  z = z * 2;", c.get(0).hunks.get(0).removed);
        assertNotNull(g.baseTimeMs());
        assertTrue(g.tracked().contains("src/a.js"), g.tracked().toString());
        assertTrue(new String(g.baseContent("src/b.js"), StandardCharsets.UTF_8).contains("z = z * 2;"));
        assertEquals(null, g.baseContent("nope.js"));

        assertEquals(1, GitRepo.open(r.toFile(), "HEAD~1...HEAD").changes().size());
        assertEquals(1, GitRepo.open(r.resolve("src").toFile(), "HEAD~1..").changes().size());   // from a subfolder, "B" defaults to HEAD

        // a single revision compares with the working tree, untracked files included
        Files.writeString(r.resolve("src/a.js"), Files.readString(r.resolve("src/a.js")).replace("x * 3", "x * 4"));
        Files.writeString(r.resolve("src/new.js"), "x();\n");
        List<DiffParser.FileChange> w = GitRepo.open(r.toFile(), "HEAD").changes();
        assertEquals(2, w.size(), w.toString());
        assertEquals("src/a.js", w.get(0).path);
        assertEquals("src/new.js", w.get(1).path);
        assertEquals(DiffParser.Status.ADDED, w.get(1).status);

        for (String bad : new String[] {"-p", "", "nope..HEAD", "HEAD..nope", "a b", "--output=x"}) {
            assertThrows(IllegalArgumentException.class, () -> GitRepo.open(r.toFile(), bad));
        }
        assertThrows(IllegalArgumentException.class, () -> GitRepo.open(dir.toFile(), "HEAD"));   // not a work tree
    }

    @Test
    void endToEndAChangedFileRunsOnlyTheAffectedTest(@TempDir Path dir) throws Exception {
        if (!hasGit()) return;
        Path r = repo(dir);
        long after = System.currentTimeMillis() + 60_000;     // the coverage run happened after the base commit
        Path run = ImpactFixture.run(r, "har", true, after);
        Files.writeString(r.resolve("src/b.js"), Files.readString(r.resolve("src/b.js")).replace("z = z * 2;", "z = z * 5;"));
        git(r, "commit", "-q", "-am", "edit b");

        GitRepo g = GitRepo.open(r.toFile(), "HEAD~1..HEAD");
        ImpactSelector.Options o = new ImpactSelector.Options();
        o.isTestScript = p -> p.startsWith("tests/");
        ImpactSelector.Result res = ImpactSelector.select(CoverageIndex.build(run), g.changes(), g, new TraceStore(run), o);
        assertFalse(res.runsAll(), res.runAll.toString());
        List<ImpactSelector.Script> scripts = Arrays.asList(
                new ImpactSelector.Script("a.sel", r.resolve("tests/a.sel").toString(), "a.sel", "tests/a.sel"),
                new ImpactSelector.Script("b.sel", r.resolve("tests/b.sel").toString(), "b.sel", "tests/b.sel"));
        List<ImpactSelector.ScriptVerdict> v = ImpactSelector.forScripts(res, scripts);
        assertFalse(v.get(0).selected, v.get(0).reasons.toString());
        assertTrue(v.get(1).selected, v.get(1).reasons.toString());

        // the same change, but the coverage run is older than the base commit: everything runs
        Path stale = ImpactFixture.run(dir.resolve("stale"), "har", true, 1_000L);
        res = ImpactSelector.select(CoverageIndex.build(stale), g.changes(), g, new TraceStore(stale), o);
        assertTrue(res.runsAll());
        assertTrue(ImpactSelector.forScripts(res, scripts).get(0).reasons.get(0).contains("older than the base commit"));
    }
}
