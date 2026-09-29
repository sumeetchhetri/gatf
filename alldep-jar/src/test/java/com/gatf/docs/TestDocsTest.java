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
package com.gatf.docs;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.gatf.executor.core.TestCase;

/** Q11 — scripts → test-case documents, traceability and coverage, xlsx/csv round trips. */
class TestDocsTest {

    @TempDir
    Path dir;

    private static Path fixture(String name) throws Exception {
        return Paths.get(TestDocsTest.class.getResource("/docs/" + name).toURI());
    }

    private static List<TestDoc> checkout() throws Exception {
        return SelDocs.fromScript(fixture("checkout.sel"), "ui/checkout.sel", "ui", TestDoc.DEFAULT_REQ);
    }

    @Test
    void seleasyScriptBecomesTestCases() throws Exception {
        List<TestDoc> ds = checkout();
        assertThat(ds).extracting(d -> d.title).containsExactly("Checkout flows", "pay by card", "refund");
        TestDoc main = ds.get(0), pay = ds.get(1), refund = ds.get(2);
        assertThat(main.description).isEqualTo("Covers cart to payment.");
        assertThat(main.tags).containsExactly("@smoke");
        assertThat(main.priority).isEqualTo("P1");
        assertThat(main.steps).containsExactly("Open the chrome browser", "Navigate to https://shop.test/", "Close the browser");
        assertThat(pay.requirements).containsExactly("REQ-101", "REQ-102", "PAY-CARD");
        assertThat(pay.steps).startsWith("Click #cart", "Enter \"4111 1111\" into \"input.card\" (css)");
        assertThat(pay.expected).containsExactly("(step 2) \"div.ok\" (css) has text \"Paid, thanks\"");
        // include'd subtest expanded through @call
        assertThat(refund.steps).containsExactly("[login] Enter \"bob\" into #u", "[login] Click #go", "Click text \"Refund\"");
        assertThat(refund.expected).containsExactly("(step 2) [login] #home is displayed (within 5s)", "(step 3) #status contains Refunded");
    }

    @Test
    void apiCaseBecomesTestCase() {
        TestCase tc = new TestCase();
        tc.setName("List users @REQ-7");
        tc.setDescription("Lists users @smoke");
        tc.setMethod("GET");
        tc.setUrl("/api/users");
        tc.setBaseUrl("https://x.test");
        tc.setExpectedResCode(200);
        tc.setSourcefileName("api/users.xml");
        tc.getExpectedNodes().add("items");
        TestDoc d = ApiDocs.from(tc, "api", TestDoc.DEFAULT_REQ);
        assertThat(d.requirements).containsExactly("REQ-7");
        assertThat(d.tags).containsExactly("@smoke");
        assertThat(d.steps).containsExactly("Send GET https://x.test/api/users");
        assertThat(d.expected).containsExactly("Status code 200", "Response has items");
    }

    @Test
    void traceabilityCoverageAndStatus() throws Exception {
        List<TestDoc> ds = checkout();
        Traceability.number(ds);
        Traceability.applyResults(ds, List.of(new Traceability.Result("ui/checkout.sel", "[chrome]", "passed", null),
                new Traceability.Result("checkout.sel", "[chrome] pay by card", "failed", "boom"),
                new Traceability.Result("ui/checkout.sel", "[firefox] pay by card", "passed", null)));
        assertThat(ds).extracting(d -> d.status).containsExactly("passed", "failed", "not run");
        assertThat(ds.get(1).error).isEqualTo("boom");

        Path reqs = dir.resolve("reqs.csv");
        Files.writeString(reqs, "Requirement ID,Title,Module\nREQ-101,Checkout,ui\nREQ-102,\"Card, payment\",ui\nREQ-103,Wallet,ui\n");
        List<Traceability.Req> all = Traceability.requirements(Sheets.readRequirements(reqs), ds);
        List<String[]> m = Traceability.matrix(all, ds);
        assertThat(m).extracting(r -> r[0] + "=" + r[8]).containsExactly("REQ-101=Failing", "REQ-102=Failing", "REQ-103=Not covered",
                "PAY-CARD=Failing");
        assertThat(m.get(1)[1]).isEqualTo("Card, payment");
        assertThat(m.get(3)[9]).isEqualTo("not in requirements file");
        List<String[]> cov = Traceability.coverage(all, ds, m, true);
        assertThat(cov.get(2)[1]).isEqualTo("3 (75.0%)");
    }

    @Test
    void xlsxAndCsvRoundTrip() throws Exception {
        List<TestDoc> ds = checkout();
        Traceability.number(ds);
        List<String[]> rows = new ArrayList<String[]>();
        for (TestDoc d : ds) rows.add(d.row());
        Path x = dir.resolve("t.xlsx");
        Sheets.writeXlsx(x, List.of(new Sheets.Sheet("Test Cases", TestDoc.COLUMNS, rows, new int[] { 12 })));
        List<String[]> back = Sheets.readXlsx(x);
        assertThat(back).hasSize(4);
        assertThat(back.get(2)[10]).isEqualTo(rows.get(1)[10]);        // multi-line steps survive
        Path c = dir.resolve("t.csv");
        Sheets.writeCsv(c, TestDoc.COLUMNS, rows);
        List<String[]> cb = Sheets.readCsv(c);
        assertThat(cb.get(2)[7]).isEqualTo(rows.get(1)[7]);            // "REQ-101, REQ-102, PAY-CARD" (comma, quoted)
        assertThat(cb.get(2)[10]).isEqualTo(rows.get(1)[10]);
        assertThat(Sheets.col(27)).isEqualTo("AB");
    }

    @Test
    void generatorWritesAllOutputs() throws Exception {
        TestDocsGenerator.Options o = new TestDocsGenerator.Options();
        o.outDir = dir.resolve("out");
        TestDocsGenerator.Outcome r = TestDocsGenerator.write(checkout(), o);
        assertThat(r.written).hasSize(4);
        assertThat(r.testCases).isEqualTo(3);
        assertThat(r.requirements).isEqualTo(3);
        assertThat(o.outDir.resolve("gatf-testcases.xlsx")).exists();
        assertThat(Sheets.readXlsx(o.outDir.resolve("gatf-testcases.xlsx")).get(0)[0]).isEqualTo("Test Case ID");
    }
}
