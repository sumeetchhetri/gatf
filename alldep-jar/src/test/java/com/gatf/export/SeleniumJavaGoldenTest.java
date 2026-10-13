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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.gatf.export.SelScript.Step;

/**
 * Δ161 — golden test for the Selenium Java exporter, as {@link ExportGoldenTest} for Playwright: every
 * {@code export-corpus/selenium-java/<name>.sel} is exported and compared with {@code <ClassName>.java} and
 * {@code <name>.report.txt} (same update / CI rules). {@code ExportSeleniumCompileTest} compiles every golden and runs
 * the e2e-* ones. Also: no repo script throws or loses a statement, and no reason in a report points at Playwright.
 */
class SeleniumJavaGoldenTest {

    static final Path DIR = Repo.path("alldep-jar/src/test/resources/export-corpus/selenium-java");

    static ExportResult export(Path sel) throws Exception {
        String src = ExportGoldenTest.read(sel);
        boolean strict = src.startsWith("// export-options:") && src.substring(0, src.indexOf('\n')).contains("--strict-locators");
        String name = sel.getFileName().toString().replaceAll("\\.sel$", "");
        return Exporters.export(SeleniumJavaExporter.ID, name, src, new ExportOptions().strictLocators(strict));
    }

    @Test
    void exportedTestsMatchGoldens() throws Exception {
        List<String> problems = new ArrayList<String>();
        int compared = 0;
        for (Path sel : ExportGoldenTest.sels(DIR)) {
            ExportResult r = export(sel);
            String name = sel.getFileName().toString().replaceAll("\\.sel$", "");
            compared += ExportGoldenTest.check(DIR.resolve(r.fileName), r.content, problems);
            compared += ExportGoldenTest.check(DIR.resolve(name + ".report.txt"), r.report(), problems);
        }
        if (!problems.isEmpty()) fail(String.join("\n", problems));
        assertTrue(compared >= 26, "selenium-java goldens compared or written: " + compared);
    }

    @Test
    void noRepoScriptLosesAStatement() throws Exception {
        List<Path> all = new ArrayList<Path>();
        all.addAll(ExportGoldenTest.sels(DIR));
        all.addAll(ExportGoldenTest.sels(ExportGoldenTest.DIR));
        all.addAll(ExportGoldenTest.sels(Repo.path("alldep-jar/src/test/resources/codegen-corpus")));
        all.addAll(ExportGoldenTest.sels(Repo.path("sample-project")));
        all.addAll(ExportGoldenTest.sels(Repo.path("dogfood")));
        assertTrue(all.size() > 20, "scripts found: " + all.size());
        List<String> problems = new ArrayList<String>();
        for (Path sel : all) {
            SelScript script = new SelParser().parse("x", ExportGoldenTest.read(sel));
            ExportResult r;
            try {
                r = Exporters.get(SeleniumJavaExporter.ID).export(script, new ExportOptions());
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
            for (ExportResult.Item i : r.items) {
                if (i.reason.contains("Playwright") || i.reason.contains("page.")) problems.add(sel + ": a reason points at Playwright: " + i);
            }
            Set<Integer> listed = new HashSet<Integer>();
            for (ExportResult.Item i : r.items(ExportResult.Level.UNSUPPORTED)) listed.add(i.line);
            List<Step> bad = new ArrayList<Step>();
            ExportGoldenTest.collectUnsupported(script.steps, bad);
            for (Step s : bad) {
                if (!listed.contains(s.line)) problems.add(sel + ":" + s.line + " unsupported but not in the report: " + s);
                for (int n = s.line; n <= s.endLine; n++) {
                    String t = SelParser.trim(script.line(n));
                    if (!t.isEmpty() && !r.content.contains("// TODO(gatf-export): " + Jv.comment(t) + "\n")) {
                        problems.add(sel + ":" + n + " not in a TODO(gatf-export) comment: " + t);
                    }
                }
            }
        }
        if (!problems.isEmpty()) fail(problems.size() + " problem(s):\n" + String.join("\n", problems.subList(0, Math.min(40, problems.size()))));
    }
}
