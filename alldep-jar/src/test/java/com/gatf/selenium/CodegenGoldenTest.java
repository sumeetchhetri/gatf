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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.gatf.executor.core.AcceptanceTestContext;
import com.gatf.executor.core.GatfExecutorConfig;

/**
 * G8 safety net — golden-output test for the seleasy → Java code generator ({@code Command.javacode()}).
 * Every .sel in the sample projects is generated and compared with a committed golden, so the upcoming
 * decomposition of Command / SeleniumTest / GatfTestCaseExecutorUtil cannot silently change generated code.
 *
 *  - Goldens: src/test/resources/codegen-golden/&lt;project&gt;/&lt;path&gt;.java.txt
 *  - Missing golden → it is written and reported (commit it); refresh all with -Dgatf.golden.update=true
 *  - Non-deterministic parts are normalized (STC_&lt;nanoTime&gt; class names, UUIDs, &lt;nanoTime&gt;.png,
 *    absolute paths). Each script is generated TWICE and the two normalized outputs must match, so any
 *    nondeterminism the normalizer misses fails loudly here with the exact line, not as a flaky drift later.
 *  - Library .sel files (subtests only, "No open command") are skipped; they are covered via includers.
 *  - Projects are copied to target/ first, so context init never writes into the repo.
 */
class CodegenGoldenTest {

    private static final String[] PROJECTS = { "sample-project", "dogfood" };
    private static final Path GOLDEN_ROOT = Paths.get("src/test/resources/codegen-golden");
    private static final boolean UPDATE = Boolean.getBoolean("gatf.golden.update");

    @Test
    void generatedJavaMatchesGoldens() throws Exception {
        List<String> problems = new ArrayList<String>();
        List<String> created = new ArrayList<String>();
        int compared = 0, skipped = 0;
        for (String project : PROJECTS) {
            Path src = Paths.get("..", project).toAbsolutePath().normalize();
            if (!Files.isDirectory(src)) continue;
            Path work = Paths.get("target", "codegen-golden-work", project).toAbsolutePath();
            deleteTree(work);
            copyTree(src, work);
            String cfgName = Files.exists(work.resolve("gatf-config.json")) ? "gatf-config.json" : "gatf-config.xml";
            if (!Files.exists(work.resolve(cfgName))) continue;

            // absolute config path (getConfig tries the CWD first); force base paths to the temp copy because
            // sample configs may pin absolute paths (sample-project/gatf-config.json points at a dev machine)
            GatfExecutorConfig config = Command.getConfig(work.resolve(cfgName).toString(), work.toString());
            config.setTestCasesBasePath(work.toString());
            config.setOutFilesBasePath(work.toString());
            AcceptanceTestContext ctx = new AcceptanceTestContext();
            ctx.setGatfExecutorConfig(config);
            ctx.validateAndInit(true);
            ctx.getWorkflowContextHandler().initializeSuiteContext(1);

            Path tests = config.getTestCaseDir() != null ? work.resolve(config.getTestCaseDir()) : work;
            List<Path> sels;
            try (Stream<Path> w = Files.walk(tests)) {
                sels = w.filter(p -> p.toString().endsWith(".sel")).sorted().collect(Collectors.toList());
            }
            for (Path sel : sels) {
                String rel = tests.relativize(sel).toString().replace('\\', '/');
                String a, b;
                try {
                    a = normalize(generate(ctx, rel), work);
                    b = normalize(generate(ctx, rel), work);
                } catch (Exception e) {
                    String m = String.valueOf(e.getMessage());
                    if (m.contains("No open command")) { skipped++; continue; }   // library of subtests
                    problems.add(project + "/" + rel + ": generation failed: " + m);
                    continue;
                }
                if (!a.equals(b)) {
                    problems.add(project + "/" + rel + ": NON-DETERMINISTIC output not covered by normalize(): " + firstDiff(a, b));
                    continue;
                }
                Path golden = GOLDEN_ROOT.resolve(project).resolve(rel + ".java.txt");
                if (UPDATE || !Files.exists(golden)) {
                    Files.createDirectories(golden.getParent());
                    Files.write(golden, a.getBytes(StandardCharsets.UTF_8));
                    created.add(golden.toString());
                    continue;
                }
                String expected = new String(Files.readAllBytes(golden), StandardCharsets.UTF_8);
                compared++;
                if (!expected.equals(a)) {
                    problems.add(project + "/" + rel + ": generated code drifted from golden " + golden + " — " + firstDiff(expected, a)
                            + "  (intended? re-run with -Dgatf.golden.update=true and commit)");
                }
            }
        }
        if (!created.isEmpty()) {
            System.out.println("[codegen-golden] wrote " + created.size() + " golden(s) — commit them:\n  " + String.join("\n  ", created));
        }
        System.out.println("[codegen-golden] compared " + compared + ", created " + created.size() + ", skipped (subtest libraries) " + skipped);
        if (!problems.isEmpty()) fail(String.join("\n", problems));
        assertTrue(compared + created.size() > 0, "no .sel scripts were generated — are the sample projects present?");
    }

    private static String generate(AcceptanceTestContext ctx, String resourceName) throws Exception {
        Command cmd = Command.read(ctx.getResourceFile(resourceName), new ArrayList<String>(), ctx);
        return cmd.javacode();
    }

    static String normalize(String code, Path root) {
        String s = code.replace("\r\n", "\n");
        for (String r : new String[] { root.toString(), root.toString().replace('\\', '/'), root.toString().replace("\\", "\\\\") }) {
            s = s.replace(r, "<ROOT>");
        }
        s = s.replaceAll("STC_\\d+", "STC_<N>");
        s = s.replaceAll("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}", "<UUID>");
        s = s.replaceAll("\\b\\d{9,}\\.png", "<N>.png");
        return s;
    }

    private static String firstDiff(String a, String b) {
        String[] x = a.split("\n", -1), y = b.split("\n", -1);
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            String p = i < x.length ? x[i] : "<EOF>", q = i < y.length ? y[i] : "<EOF>";
            if (!p.equals(q)) return "first difference at line " + (i + 1) + ":\n    expected: " + clip(p) + "\n    actual:   " + clip(q);
        }
        return "(identical)";
    }

    private static String clip(String s) {
        return s.length() > 200 ? s.substring(0, 200) + "…" : s;
    }

    private static void copyTree(Path from, Path to) throws IOException {
        try (Stream<Path> w = Files.walk(from)) {
            for (Path p : (Iterable<Path>) w::iterator) {
                Path rel = from.relativize(p);
                if (rel.toString().startsWith("out") || rel.toString().contains("node_modules")) continue;
                Path d = to.resolve(rel.toString());
                if (Files.isDirectory(p)) Files.createDirectories(d);
                else { Files.createDirectories(d.getParent()); Files.copy(p, d); }
            }
        }
    }

    private static void deleteTree(Path p) throws IOException {
        if (!Files.exists(p)) return;
        try (Stream<Path> w = Files.walk(p)) {
            w.sorted(java.util.Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
        }
    }
}
