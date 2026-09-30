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
package com.gatf.log;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.gatf.log.RunConsole.Rec;
import com.gatf.log.RunConsole.Status;

/** M12: run history archive + listing for the console's trend charts. */
class RunHistoryTest {

    @TempDir
    File out;

    private static String results(int failed) {
        Rec a = new Rec("a.sel", "[chrome]", "a.sel › [chrome]");
        a.attempts.add(failed > 0 ? Status.FAILED : Status.PASSED);
        a.millis = 100 + failed;
        return CiReports.json(List.of(a), 1000, "/w/out");
    }

    @Test
    void keepsTheNewestAndListsThemNewestFirst() throws Exception {
        for (int i = 0; i < 12; i++) RunHistory.archive(out, results(i % 2), 1_700_000_000_000L + i * 1000, 10);
        File[] kept = new File(out, "history").listFiles();
        assertThat(kept.length).isEqualTo(10);
        List<Map<String, Object>> runs = RunHistory.list(out, 10);
        assertThat(runs).hasSize(10);
        assertThat(runs.get(0).get("time")).isEqualTo(1_700_000_011_000L);
        assertThat(runs.get(9).get("time")).isEqualTo(1_700_000_002_000L);
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) runs.get(0).get("summary");
        assertThat(summary.get("failed")).isEqualTo(1);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tests = (List<Map<String, Object>>) runs.get(0).get("tests");
        assertThat(tests.get(0).get("title")).isEqualTo("a.sel › [chrome]");
        assertThat(tests.get(0).get("status")).isEqualTo("failed");
        assertThat(tests.get(0).containsKey("error")).isFalse();   // compact
        assertThat(RunHistory.list(out, 3)).hasSize(3);
    }

    @Test
    void sameMillisecondDoesNotOverwriteAndBadFilesAreSkipped() throws Exception {
        RunHistory.archive(out, results(0), 5, 50);
        RunHistory.archive(out, results(1), 5, 50);
        Files.write(new File(out, "history/gatf-results-9.json").toPath(), "{not json".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(out, "history/notes.txt").toPath(), "x".getBytes(StandardCharsets.UTF_8));
        assertThat(RunHistory.list(out, 10)).hasSize(2);
    }

    @Test
    void sameMillisecondRunsKeepTheirOrderAndOddNamesNeverBreakTheList() throws Exception {
        for (int i = 0; i < 11; i++) RunHistory.archive(out, results(i == 10 ? 1 : 0), 7, 50);   // -1 .. -11 in one ms
        List<Map<String, Object>> runs = RunHistory.list(out, 50);
        assertThat(runs.get(0).get("id")).isEqualTo("gatf-results-7-11.json");   // newest (not "-9" by name)
        assertThat(runs.get(10).get("id")).isEqualTo("gatf-results-7.json");
        RunHistory.archive(out, results(0), 8, 3);   // prune keeps the 3 newest
        assertThat(RunHistory.list(out, 50)).extracting(r -> r.get("id")).containsExactly("gatf-results-8.json", "gatf-results-7-11.json", "gatf-results-7-10.json");
        Files.write(new File(out, "history/gatf-results-99999999999999999999.json").toPath(), "{}".getBytes(StandardCharsets.UTF_8));
        assertThat(RunHistory.list(out, 50)).hasSize(3);   // a name too long for a long is simply not a history file
    }

    @Test
    void noHistoryIsAnEmptyList() {
        assertThat(RunHistory.list(new File(out, "missing"), 10)).isEmpty();
    }

    @Test
    void resultsCarryTheReportDirForAttachmentUrls() {
        assertThat(results(0)).startsWith("{\n  \"dir\": \"/w/out\",\n  \"summary\"");
    }
}
