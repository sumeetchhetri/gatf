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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatf.log.RunConsole.Rec;
import com.gatf.log.RunConsole.Status;

/** Q4/Q6: flaky classification + JUnit XML / JSON / GitHub annotation output. */
class CiReportsTest {

    private static Rec rec(String suite, String name, String error, Status... attempts) {
        Rec r = new Rec(suite, name, suite + " \u203a " + name);
        for (Status s : attempts) r.attempts.add(s);
        r.millis = 1500;
        r.error = error;
        r.detail = error == null ? null : "detail for " + name;
        return r;
    }

    private static final List<Rec> RECS = List.of(
            rec("api/users.xml", "Create <user> & \"x\"", "Expected 201 but got 500\nsecond line", Status.FAILED),
            rec("api/users.xml", "List users", null, Status.PASSED),
            rec("ui/login.sel", "[chrome]", null, Status.FAILED, Status.PASSED),   // flaky
            rec("ui/login.sel", "[chrome] checkout", null, Status.SKIPPED));

    @Test
    void flakyIsFailedThenPassed() {
        assertThat(RECS.get(2).flaky()).isTrue();
        assertThat(RECS.get(0).flaky()).isFalse();
        assertThat(RECS.get(1).flaky()).isFalse();
    }

    @Test
    void junitXmlParsesAndCountsFlakyAsPassed() throws Exception {
        String xml = CiReports.junitXml(RECS, 4000);
        Document d = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        assertThat(d.getDocumentElement().getAttribute("tests")).isEqualTo("4");
        assertThat(d.getDocumentElement().getAttribute("failures")).isEqualTo("1");
        assertThat(d.getDocumentElement().getAttribute("skipped")).isEqualTo("1");
        assertThat(d.getElementsByTagName("testsuite").getLength()).isEqualTo(2);
        assertThat(d.getElementsByTagName("failure").getLength()).isEqualTo(1);
        assertThat(d.getElementsByTagName("flakyFailure").getLength()).isEqualTo(1);
        assertThat(xml).contains("Create &lt;user&gt; &amp; &quot;x&quot;");
    }

    @Test
    @SuppressWarnings("unchecked")
    void jsonSummaryAndStatuses() throws Exception {
        Map<String, Object> j = new ObjectMapper().readValue(CiReports.json(RECS, 4000), Map.class);
        Map<String, Object> s = (Map<String, Object>) j.get("summary");
        assertThat(s).containsEntry("total", 4).containsEntry("passed", 1).containsEntry("failed", 1)
                .containsEntry("flaky", 1).containsEntry("skipped", 1);
        List<Map<String, Object>> tests = (List<Map<String, Object>>) j.get("tests");
        assertThat(tests).extracting(t -> t.get("status")).containsExactly("failed", "passed", "flaky", "skipped");
        assertThat(tests.get(2)).containsEntry("attempts", 2);
    }

    @Test
    void failureAttachmentsGoToJUnitSystemOutAndJson() throws Exception {
        Rec r = rec("ui/login.sel", "[chrome]", "Element not found", Status.FAILED);
        r.attachments.add("/w/out/shot.png");
        r.attachments.add("/w/out/1/login-trace.zip");
        String xml = CiReports.junitXml(List.of(r), 1000);
        assertThat(xml).contains("<system-out>[[ATTACHMENT|/w/out/shot.png]]\n[[ATTACHMENT|/w/out/1/login-trace.zip]]\n</system-out>");
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        assertThat(CiReports.json(List.of(r), 1000)).contains("\"attachments\": [\"/w/out/shot.png\", \"/w/out/1/login-trace.zip\"]");
    }

    @Test
    void githubAnnotationsEscapeAndCoverFailedAndFlaky() {
        List<String> a = CiReports.githubAnnotations(RECS);
        assertThat(a).hasSize(2);
        assertThat(a.get(0)).startsWith("::error title=GATF failed%3A api/users.xml").contains("500%0Asecond line");
        assertThat(a.get(1)).startsWith("::warning title=GATF flaky%3A ui/login.sel");
    }

    @Test
    @SuppressWarnings("unchecked")
    void softFailureCountInJunitAndJson() throws Exception {
        Rec r = rec("ui/soft.sel", "[chrome]", "3 soft assertion(s) failed: ui/soft.sel:4: a\n  ui/soft.sel:5: b\n  ui/soft.sel:6: c", Status.FAILED);
        r.soft = 3;
        List<Rec> recs = List.of(r, rec("api/users.xml", "List users", null, Status.PASSED));
        String xml = CiReports.junitXml(recs, 1000);
        Document d = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        assertThat(d.getElementsByTagName("property").getLength()).isEqualTo(1);
        assertThat(((org.w3c.dom.Element) d.getElementsByTagName("property").item(0)).getAttribute("value")).isEqualTo("3");
        assertThat(((org.w3c.dom.Element) d.getElementsByTagName("failure").item(0)).getAttribute("type")).isEqualTo("SoftAssertionError");
        assertThat(((org.w3c.dom.Element) d.getElementsByTagName("failure").item(0)).getAttribute("message")).isEqualTo("3 soft assertion(s) failed: ui/soft.sel:4: a");
        Map<String, Object> j = new ObjectMapper().readValue(CiReports.json(recs, 1000), Map.class);
        assertThat((Map<String, Object>) j.get("summary")).containsEntry("softFailures", 3);
        assertThat(((List<Map<String, Object>>) j.get("tests")).get(0)).containsEntry("softFailures", 3);
    }
}
