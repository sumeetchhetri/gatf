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
import java.io.File;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.gatf.ai.gen.GenFixtures;
import com.gatf.ai.gen.SelGate;

/** H1 (Δ152): `gatf plan` (AI off) → `gatf generate` end to end on the canned shop crawl, with a fake gate. */
public class GenerateCliTest {

    static final class Io {
        final ByteArrayOutputStream o = new ByteArrayOutputStream(), e = new ByteArrayOutputStream();
        final PrintStream out = new PrintStream(o, true), err = new PrintStream(e, true);
        String out() { return o.toString(StandardCharsets.UTF_8); }
        String err() { return e.toString(StandardCharsets.UTF_8); }
    }

    static Path project(Path dir) throws Exception {
        Path root = Files.createDirectories(dir.resolve("proj"));
        Files.createDirectories(root.resolve("tests"));
        try (InputStream in = GenerateCliTest.class.getResourceAsStream("/plan/shop-coverage.json")) {
            Files.write(root.resolve("tests/shop-coverage.json"), in.readAllBytes());
        }
        Files.write(root.resolve("gatf-config.json"), "{}".getBytes(StandardCharsets.UTF_8));
        return root;
    }

    static Set<String> tree(Path root) throws Exception {
        try (Stream<Path> w = Files.walk(root.getParent())) {
            return w.filter(Files::isRegularFile).map(p -> root.getParent().relativize(p).toString().replace('\\', '/')).collect(Collectors.toCollection(TreeSet::new));
        }
    }

    static int gen(Path root, Io io, GenFixtures.FakeGate gate, List<File> opened, String... args) {
        return GenerateCli.run(args, root.toFile(), io.out, io.err, f -> { opened.add(f); return null; }, (r, c) -> gate, false);
    }

    @Test
    void planThenGenerateWritesOnlyLintCleanScriptsThatDryRun(@TempDir Path dir) throws Exception {
        Path root = project(dir);
        Io p = new Io();
        assertEquals(0, PlanCli.run(new String[] { "tests/shop-coverage.json", "--no-ai" }, root.toFile(), p.out, p.err, f -> null), p.err());
        assertTrue(Files.isRegularFile(root.resolve("tests/shop-plan.json")));
        GenFixtures.FakeGate gate = new GenFixtures.FakeGate();
        List<File> opened = new ArrayList<File>();
        Io io = new Io();
        int rc = gen(root, io, gate, opened, "tests/shop-plan.json");
        assertEquals(0, rc, io.err() + io.out());
        Path out = root.resolve("tests/generated/shop");
        List<Path> written;
        try (Stream<Path> w = Files.list(out)) { written = w.sorted().collect(Collectors.toList()); }
        assertEquals(7, written.size(), written.toString());
        assertEquals(1, gate.dryRuns.size());
        assertEquals(written.size(), gate.dryRuns.get(0).size(), "the suite that dry-ran is the suite written");
        GenFixtures.FakeGate relint = new GenFixtures.FakeGate();
        for (Path f : written) {
            String t = Files.readString(f);
            assertEquals(List.of(), relint.lint(f.getFileName().toString(), t), f.toString());   // every written file lints clean
            assertEquals(gate.dryRuns.get(0).get(f.getFileName().toString()), t);
        }
        assertEquals(List.of(new File(root.toFile(), "gatf-config.json").getAbsoluteFile().getName()), List.of(opened.get(0).getName()), "AI on by default");
        String o = io.out();
        assertTrue(o.contains("generate: 7 script(s), 7 passed, 0 failed"), o);
        assertTrue(o.contains("dry run: ok"), o);
        assertTrue(o.contains("AI: AI not configured"), o);
        assertTrue(o.contains("side-effect journeys skipped: r2-j2, r2-j3"), o);
        assertTrue(o.contains("wrote 7 script(s) to tests/generated/shop"), o);

        // never overwrite without --force; nothing is written then
        Set<String> before = tree(root);
        Io again = new Io();
        assertEquals(1, gen(root, again, new GenFixtures.FakeGate(), new ArrayList<File>(), "tests/shop-plan.json"));
        assertTrue(again.err().contains("exists in the output directory (use --force"), again.err());
        assertEquals(before, tree(root));
        Io forced = new Io();
        assertEquals(0, gen(root, forced, new GenFixtures.FakeGate(), new ArrayList<File>(), "tests/shop-plan.json", "--force", "--no-ai", "--include-side-effects"));
        assertTrue(forced.out().contains("AI: off: deterministic output only"), forced.out());
        assertTrue(forced.out().contains("wrote 9 script(s)"), forced.out());
    }

    @Test
    void failingScriptsAreReportedNeverWritten(@TempDir Path dir) throws Exception {
        Path root = project(dir);
        Files.write(root.resolve("shop-plan.json"), GenFixtures.shopPlanJson().getBytes(StandardCharsets.UTF_8));
        GenFixtures.FakeGate gate = new GenFixtures.FakeGate();
        gate.fail = (f, t) -> f.equals("r1-j1.sel") ? List.of(new SelGate.Diag(f, 6, null, "error", "Error parsing command at line 6 (?? title)")) : null;
        Io io = new Io();
        assertEquals(2, gen(root, io, gate, new ArrayList<File>(), "shop-plan.json", "-o", "ui/gen", "--no-ai"), io.err());
        assertFalse(Files.exists(root.resolve("ui/gen/r1-j1.sel")));
        assertTrue(Files.exists(root.resolve("ui/gen/r1-j2.sel")));
        assertTrue(io.out().contains("FAILED r1-j1.sel"), io.out());
        assertTrue(io.out().contains("lint: line 6: Error parsing command at line 6"), io.out());
        assertFalse(gate.dryRuns.get(0).containsKey("r1-j1.sel"));

        // the whole suite failing the dry run: nothing is written at all
        GenFixtures.FakeGate g2 = new GenFixtures.FakeGate();
        g2.dryFail = m -> List.of(new SelGate.Diag(null, null, null, "error", "no Java compiler available"));
        Io io2 = new Io();
        assertEquals(2, gen(root, io2, g2, new ArrayList<File>(), "shop-plan.json", "-o", "ui/gen2", "--no-ai"));
        assertFalse(Files.exists(root.resolve("ui/gen2")));
        assertTrue(io2.out().contains("wrote nothing"), io2.out());
    }

    @Test
    void argumentsPathsAndFormats(@TempDir Path dir) throws Exception {
        Path root = project(dir);
        Files.write(root.resolve("shop-plan.json"), GenFixtures.shopPlanJson().getBytes(StandardCharsets.UTF_8));
        Files.write(root.resolve("adv-plan.json"), GenFixtures.adversarialPlanJson().getBytes(StandardCharsets.UTF_8));
        Set<String> before = tree(root);
        String[][] bad = {
                {}, { "a", "b" }, { "shop-plan.json", "--bogus" }, { "shop-plan.json", "-o" },
                { "shop-plan.json", "-o", "../outside" }, { "shop-plan.json", "-o", dir.resolve("elsewhere").toString() },
                { "shop-plan.json", "-o", "." }, { "shop-plan.json", "--config", "nope.json" },
                { "tests/shop-coverage.json" }, { "missing-plan.json" },
        };
        for (String[] b : bad) {
            Io io = new Io();
            assertEquals(1, gen(root, io, new GenFixtures.FakeGate(), new ArrayList<File>(), b), String.join(" ", b));
            assertFalse(io.err().isEmpty(), String.join(" ", b));
        }
        Io io = new Io();
        gen(root, io, new GenFixtures.FakeGate(), new ArrayList<File>(), "tests/shop-coverage.json");
        assertTrue(io.err().contains("not a test plan"), io.err());
        assertEquals(before, tree(root), "a refused run writes nothing");
        // a symlinked output dir that leaves the root is refused
        Path outside = Files.createDirectories(dir.resolve("outside"));
        try {
            Files.createSymbolicLink(root.resolve("link"), outside);
            Io s = new Io();
            assertEquals(1, gen(root, s, new GenFixtures.FakeGate(), new ArrayList<File>(), "shop-plan.json", "-o", "link/gen"));
            try (Stream<Path> w = Files.list(outside)) { assertEquals(0L, w.count()); }
        } catch (UnsupportedOperationException | java.nio.file.FileSystemException e) {
            // no symlinks on this file system
        }
        // no config: the gate cannot run
        Files.delete(root.resolve("gatf-config.json"));
        Io nc = new Io();
        assertEquals(1, gen(root, nc, new GenFixtures.FakeGate(), new ArrayList<File>(), "shop-plan.json"));
        assertTrue(nc.err().contains("no config"), nc.err());
    }

    @Test
    void theAdversarialPlanWritesOnlyInsideTheOutputDir(@TempDir Path dir) throws Exception {
        Path root = project(dir);
        Files.write(root.resolve("adv-plan.json"), GenFixtures.adversarialPlanJson().getBytes(StandardCharsets.UTF_8));
        Set<String> before = tree(root);
        Io io = new Io();
        assertEquals(0, gen(root, io, new GenFixtures.FakeGate(), new ArrayList<File>(), "adv-plan.json", "--no-ai"), io.err() + io.out());
        Set<String> after = tree(root);
        after.removeAll(before);
        assertFalse(after.isEmpty());
        for (String f : after) assertTrue(f.matches("proj/tests/generated/adv/[A-Za-z0-9_-]+\\.sel"), f);
        assertTrue(after.contains("proj/tests/generated/adv/escape-j1.sel"), after.toString());
        assertTrue(io.out().contains("side-effect journeys skipped: r1-j2"), io.out());
        for (String f : after) {
            for (String l : Files.readAllLines(root.getParent().resolve(f))) {
                if (l.startsWith("//")) continue;
                assertFalse(l.contains("$") || l.contains("evil") || l.contains("javascript:"), l);
            }
        }
    }
}
