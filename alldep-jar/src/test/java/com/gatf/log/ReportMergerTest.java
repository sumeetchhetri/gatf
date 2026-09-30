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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatf.log.RunConsole.Rec;
import com.gatf.log.RunConsole.Status;

/** M7: gatf merge-reports: 4 shards -> 1 merged report. */
class ReportMergerTest {

    @TempDir
    File tmp;

    private static Rec rec(String suite, String name, Status... attempts) {
        Rec r = new Rec(suite, name, suite + " › " + name);
        for (Status s : attempts) r.attempts.add(s);
        r.millis = 100;
        if (attempts[attempts.length - 1] == Status.FAILED) r.error = "boom in " + name;
        return r;
    }

    /** One shard's out dir, written by the real CiReports writers + a report sidecar and a screenshot. */
    private File shard(int i, List<Rec> recs) throws Exception {
        File d = new File(tmp, "run" + i + "/out");
        File sess = new File(d, "1700000" + i);
        sess.mkdirs();
        File shot = new File(sess, "fail-" + i + ".png");
        Files.write(shot.toPath(), new byte[] { 1, 2, 3 });
        for (Rec r : recs) if (r.last() == Status.FAILED) r.attachments.add("/ci/agent" + i + "/work/out/1700000" + i + "/fail-" + i + ".png");
        RunConsole.setShard(i + "/4");
        try {
            Files.write(new File(d, "gatf-junit.xml").toPath(), CiReports.junitXml(recs, 1000L * i).getBytes(StandardCharsets.UTF_8));
            Files.write(new File(d, "gatf-results.json").toPath(), CiReports.json(recs, 1000L * i).getBytes(StandardCharsets.UTF_8));
        } finally {
            RunConsole.setShard(null);
        }
        Files.write(new File(sess, "selenium-index.data.json").toPath(), "{\"type\":\"selenium-final\",\"indexes\":{}}".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(sess, "LRun-1-index.data.json").toPath(), "{\"type\":\"selenium-summary\"}".getBytes(StandardCharsets.UTF_8));
        return d;
    }

    @Test
    @SuppressWarnings("unchecked")
    void fourShardsMergeIntoOneReport() throws Exception {
        List<File> dirs = new ArrayList<File>();
        dirs.add(shard(1, List.of(rec("a.sel", "[chrome]", Status.PASSED), rec("b.sel", "[chrome]", Status.FAILED))));
        dirs.add(shard(2, List.of(rec("c.sel", "[chrome]", Status.FAILED, Status.PASSED))));        // flaky
        dirs.add(shard(3, List.of(rec("d.sel", "[chrome]", Status.PASSED), rec("e.sel", "[chrome] x", Status.SKIPPED))));
        // shard 4 also ran a.sel (an API-style prerequisite pulled into two shards) and it failed there: worst wins
        dirs.add(shard(4, List.of(rec("f.sel", "[chrome]", Status.PASSED), rec("a.sel", "[chrome]", Status.FAILED))));
        File dest = new File(tmp, "merged");
        new File(dirs.get(0), "history").mkdirs();   // a shard's run history is not copied
        Files.write(new File(dirs.get(0), "history/gatf-results-1.json").toPath(), "{}".getBytes(StandardCharsets.UTF_8));

        ReportMerger.Summary s = ReportMerger.merge(dirs, dest);
        assertThat(new File(dest, "shard-1-of-4/history").exists()).isFalse();
        assertThat(s.total).isEqualTo(6);
        assertThat(s.passed).isEqualTo(2);
        assertThat(s.failed).isEqualTo(2);
        assertThat(s.flaky).isEqualTo(1);
        assertThat(s.skipped).isEqualTo(1);
        assertThat(s.durationMs).isEqualTo(4000);   // wall time = the slowest shard

        ObjectMapper om = new ObjectMapper();
        Map<String, Object> res = om.readValue(new File(dest, "gatf-results.json"), Map.class);
        List<Map<String, Object>> tests = (List<Map<String, Object>>) res.get("tests");
        assertThat(tests).hasSize(6);
        Map<String, Object> a = tests.stream().filter(t -> "a.sel".equals(t.get("suite"))).findFirst().get();
        assertThat(a.get("status")).isEqualTo("failed");
        assertThat(a.get("shard")).isEqualTo("shard-4-of-4");
        Map<String, Object> b = tests.stream().filter(t -> "b.sel".equals(t.get("suite"))).findFirst().get();
        // the CI agent's absolute path now points at the copy inside the merged report
        assertThat((List<Object>) b.get("attachments")).containsExactly("shard-1-of-4/17000001/fail-1.png");
        assertThat(new File(dest, "shard-1-of-4/17000001/fail-1.png")).matches(File::isFile);
        assertThat(((Map<String, Object>) res.get("summary")).get("shards")).isEqualTo(4);

        // JUnit: one testsuites, totals recomputed, a.sel kept once (as failed)
        Document d = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new File(dest, "gatf-junit.xml"));
        Element root = d.getDocumentElement();
        assertThat(root.getAttribute("tests")).isEqualTo("6");
        assertThat(root.getAttribute("failures")).isEqualTo("2");
        assertThat(root.getAttribute("skipped")).isEqualTo("1");
        assertThat(root.getAttribute("time")).isEqualTo("4.000");
        assertThat(d.getElementsByTagName("testsuite").getLength()).isEqualTo(6);
        assertThat(d.getElementsByTagName("flakyFailure").getLength()).isEqualTo(1);

        // merged index: every shard with its primary report sidecar (not the LRun summary)
        Map<String, Object> idx = om.readValue(new File(dest, "index.data.json"), Map.class);
        assertThat(idx.get("type")).isEqualTo("merged-index");
        List<Map<String, Object>> shards = (List<Map<String, Object>>) idx.get("shards");
        assertThat(shards).hasSize(4);
        List<Map<String, Object>> reports = (List<Map<String, Object>>) shards.get(2).get("reports");
        assertThat(reports).hasSize(1);
        assertThat(reports.get(0).get("dataUrl")).isEqualTo("shard-3-of-4/17000003/selenium-index.data.json");
        assertThat(reports.get(0).get("reportType")).isEqualTo("selenium-final");
        assertThat((List<Object>) idx.get("flaky")).containsExactly("c.sel › [chrome]");
        assertThat((List<Object>) idx.get("failures")).hasSize(2);
        assertThat(Files.readString(new File(dest, "index.html").toPath())).contains("/app/?report=");
    }

    @Test
    void cliValidatesInputsAndReturnsFailureExitCode() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream(), err = new ByteArrayOutputStream();
        PrintStream o = new PrintStream(out, true, "UTF-8"), e = new PrintStream(err, true, "UTF-8");
        assertThat(ReportMerger.run(new String[0], tmp, o, e)).isEqualTo(1);
        assertThat(err.toString("UTF-8")).contains("no report dirs");
        new File(tmp, "empty").mkdirs();
        assertThat(ReportMerger.run(new String[] { "empty" }, tmp, o, e)).isEqualTo(1);
        assertThat(err.toString("UTF-8")).contains("not a GATF out dir");

        shard(1, List.of(rec("a.sel", "[chrome]", Status.PASSED)));
        shard(2, List.of(rec("b.sel", "[chrome]", Status.PASSED)));
        assertThat(ReportMerger.run(new String[] { "run1/out", "run2/out", "-o", "m" }, tmp, o, e)).isEqualTo(0);
        assertThat(out.toString("UTF-8")).contains("2 tests: 2 passed, 0 failed");
        // output inside an input dir is refused (it would copy itself)
        assertThat(ReportMerger.run(new String[] { "run1/out", "-o", "run1/out/m" }, tmp, o, e)).isEqualTo(1);
        assertThat(err.toString("UTF-8")).contains("must not be inside");
    }

    @Test
    void junitOnlyShardsStillFailTheMergeAndAttachmentsAreRemapped() throws Exception {
        File d = shard(1, List.of(rec("b.sel", "[chrome]", Status.FAILED)));
        new File(d, "gatf-results.json").delete();   // --reporter junit
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream o = new PrintStream(out, true, "UTF-8");
        assertThat(ReportMerger.run(new String[] { "run1/out", "-o", "m" }, tmp, o, o)).isEqualTo(1);
        assertThat(out.toString("UTF-8")).contains("has no gatf-results.json");
        String junit = Files.readString(new File(tmp, "m/gatf-junit.xml").toPath());
        assertThat(junit).contains("[[ATTACHMENT|shard-out/17000001/fail-1.png]]");
    }

    @Test
    void aSecondMergeIntoTheSameDirLeavesNothingStale() throws Exception {
        List<File> first = List.of(shard(1, List.of(rec("a.sel", "[chrome]", Status.FAILED))), shard(2, List.of(rec("b.sel", "[chrome]", Status.PASSED))));
        File dest = new File(tmp, "m");
        ReportMerger.merge(first, dest);
        assertThat(new File(dest, "shard-2-of-4")).matches(File::isDirectory);
        File x = new File(tmp, "x/out");
        x.mkdirs();
        Files.write(new File(x, "gatf-results.json").toPath(), "{\"summary\":{},\"tests\":[]}".getBytes(StandardCharsets.UTF_8));
        ReportMerger.merge(List.of(x), dest);
        assertThat(new File(dest, "shard-2-of-4").exists()).isFalse();
        assertThat(new File(dest, "gatf-junit.xml").exists()).isFalse();   // the new inputs had none
        assertThat(new File(dest, "shard-out")).matches(File::isDirectory);
    }

    @Test
    void sameDirNameTwiceGetsUniqueShardFolders() throws Exception {
        File a = new File(tmp, "x/out"), b = new File(tmp, "y/out");
        a.mkdirs();
        b.mkdirs();
        Files.write(new File(a, "gatf-results.json").toPath(), "{\"summary\":{},\"tests\":[]}".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(b, "gatf-results.json").toPath(), "{\"summary\":{},\"tests\":[]}".getBytes(StandardCharsets.UTF_8));
        ReportMerger.merge(List.of(a, b), new File(tmp, "m"));
        assertThat(new File(tmp, "m/shard-out")).matches(File::isDirectory);
        assertThat(new File(tmp, "m/shard-out-2")).matches(File::isDirectory);
    }
}
