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
package com.gatf.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.gatf.export.SelScript.Kind;
import com.gatf.export.SelScript.Step;

/**
 * Δ160 — golden test for the Playwright exporter: every {@code export-corpus/playwright/<name>.sel} is exported and
 * compared with {@code <name>.spec.ts} and {@code <name>.report.txt}. A first line {@code // export-options:
 * --strict-locators} sets that option. Missing goldens are written locally and fail under CI (env CI=true); refresh
 * with {@code -Dgatf.export.golden.update=true}. The e2e-*.spec.ts goldens are also run in Playwright
 * ({@code npm run test:export} in e2e/), and every golden is typechecked ({@code npm run typecheck:export}).
 *
 * <p>It also checks, on every .sel in the repo (export corpus, codegen corpus, sample-project, dogfood), that the
 * export never throws and never drops a statement: each one is exported or listed as unsupported, and every line of
 * an unsupported statement or block appears in a {@code TODO(gatf-export)} comment.
 */
class ExportGoldenTest {

    static final Path DIR = Repo.path("alldep-jar/src/test/resources/export-corpus/playwright");
    private static final boolean UPDATE = Boolean.getBoolean("gatf.export.golden.update");
    private static final boolean CI = "true".equalsIgnoreCase(System.getenv("CI"));

    static List<Path> sels(Path root) throws Exception {
        if (!Files.isDirectory(root)) return new ArrayList<Path>();
        try (Stream<Path> w = Files.walk(root)) {
            return w.filter(p -> p.toString().endsWith(".sel") && !p.toString().contains("node_modules")).sorted().collect(Collectors.toList());
        }
    }

    static String read(Path p) throws Exception {
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    }

    static ExportResult export(Path sel) throws Exception {
        String src = read(sel);
        boolean strict = src.startsWith("// export-options:") && src.substring(0, src.indexOf('\n')).contains("--strict-locators");
        String name = sel.getFileName().toString().replaceAll("\\.sel$", "");
        return Exporters.export("playwright", name, src, new ExportOptions().strictLocators(strict));
    }

    @Test
    void exportedSpecsMatchGoldens() throws Exception {
        List<String> problems = new ArrayList<String>();
        int compared = 0;   // compared or written
        for (Path sel : sels(DIR)) {
            ExportResult r = export(sel);
            String name = sel.getFileName().toString().replaceAll("\\.sel$", "");
            compared += check(DIR.resolve(name + ".spec.ts"), r.content, problems);
            compared += check(DIR.resolve(name + ".report.txt"), r.report(), problems);
        }
        if (!problems.isEmpty()) fail(String.join("\n", problems));
        assertTrue(compared > 0, "no export goldens compared or written");
    }

    /** Δ161: shared with {@link SeleniumJavaGoldenTest}. */
    static int check(Path golden, String actual, List<String> problems) throws Exception {
        if (UPDATE || (!CI && !Files.exists(golden))) {
            Files.write(golden, actual.getBytes(StandardCharsets.UTF_8));
            System.out.println("[export-golden] wrote " + golden);
            return 1;
        }
        if (!Files.exists(golden)) {
            problems.add("no golden " + golden + " (run with -Dgatf.export.golden.update=true and commit it)");
            return 0;
        }
        String expected = read(golden);
        if (!expected.equals(actual)) {
            String[] e = expected.split("\n", -1), a = actual.split("\n", -1);
            for (int i = 0; i < Math.max(e.length, a.length); i++) {
                String x = i < e.length ? e[i] : "<EOF>", y = i < a.length ? a[i] : "<EOF>";
                if (!x.equals(y)) {
                    problems.add(golden.getFileName() + " drifted at line " + (i + 1) + ":\n    expected: " + x + "\n    actual:   " + y);
                    break;
                }
            }
        }
        return 1;
    }

    @Test
    void noRepoScriptLosesAStatement() throws Exception {
        List<Path> all = new ArrayList<Path>();
        all.addAll(sels(DIR));
        all.addAll(sels(Repo.path("alldep-jar/src/test/resources/codegen-corpus")));
        all.addAll(sels(Repo.path("sample-project")));
        all.addAll(sels(Repo.path("dogfood")));
        assertTrue(all.size() > 20, "scripts found: " + all.size());
        List<String> problems = new ArrayList<String>();
        for (Path sel : all) {
            String src = read(sel);
            SelScript script = new SelParser().parse("x", src);
            ExportResult r;
            try {
                r = Exporters.get("playwright").export(script, new ExportOptions());
            } catch (ExportException e) {
                continue;   // an environment-name collision: reported, nothing written
            } catch (RuntimeException e) {
                problems.add(sel + ": export threw " + e);
                continue;
            }
            int unsupported = r.items(ExportResult.Level.UNSUPPORTED).size();
            if (r.stats.get("statements") != r.stats.get("exported") + unsupported) {
                problems.add(sel + ": statements " + r.stats + " != exported + " + unsupported + " unsupported");
            }
            Set<Integer> listed = new HashSet<Integer>();
            for (ExportResult.Item i : r.items(ExportResult.Level.UNSUPPORTED)) listed.add(i.line);
            List<Step> bad = new ArrayList<Step>();
            collectUnsupported(script.steps, bad);
            for (Step s : bad) {
                if (!listed.contains(s.line)) problems.add(sel + ":" + s.line + " unsupported but not in the report: " + s);
                for (int n = s.line; n <= s.endLine; n++) {
                    String t = SelParser.trim(script.line(n));
                    if (!t.isEmpty() && !r.content.contains("// TODO(gatf-export): " + Ts.comment(t) + "\n")) {
                        problems.add(sel + ":" + n + " not in a TODO(gatf-export) comment: " + t);
                    }
                }
            }
        }
        if (!problems.isEmpty()) fail(problems.size() + " problem(s):\n" + String.join("\n", problems.subList(0, Math.min(40, problems.size()))));
    }

    static void collectUnsupported(List<Step> steps, List<Step> out) {
        for (Step s : steps) {
            if (s.kind == Kind.UNSUPPORTED) out.add(s);
            else collectUnsupported(s.children, out);
        }
    }

    @Test
    void goldensImportOnlyPlaywright() throws Exception {
        for (Path p : sels(DIR)) {
            Path spec = DIR.resolve(p.getFileName().toString().replaceAll("\\.sel$", ".spec.ts"));
            if (!Files.exists(spec)) continue;
            for (String l : read(spec).split("\n")) {
                if (l.startsWith("import ")) assertEquals("import { test, expect } from '@playwright/test';", l, spec.toString());
            }
        }
    }
}
