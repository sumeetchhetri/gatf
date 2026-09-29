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

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.gatf.executor.core.TestCase;

/** Q11 — a GATF API test case → a documented test case (request as steps, assertions as expected results). */
public final class ApiDocs {

    private ApiDocs() {}

    public static TestDoc from(TestCase tc, String module, Pattern reqPattern) {
        TestDoc d = new TestDoc();
        d.type = "API";
        d.source = tc.getSourcefileName();
        d.module = module;
        d.name = tc.getName();
        d.title = tc.getName();
        String desc = tc.getDescription() == null ? "" : tc.getDescription();
        d.description = SelDocs.clean(desc);
        d.addTags((tc.getName() == null ? "" : tc.getName()) + " " + desc, reqPattern);

        if (tc.getRelatedTestName() != null && !tc.getRelatedTestName().isBlank()) d.preconditions.add("\"" + tc.getRelatedTestName() + "\" has run");
        if (tc.isSecure()) d.preconditions.add("Authenticated (the configured auth call supplies the token)");
        if (tc.getExecuteOnCondition() != null && !tc.getExecuteOnCondition().isBlank()) d.preconditions.add("Runs only when: " + tc.getExecuteOnCondition());
        if (tc.getRepeatScenarioProviderName() != null) d.preconditions.add("Repeated for each row of data provider " + tc.getRepeatScenarioProviderName());
        else if (tc.getRepeatScenarios() != null && !tc.getRepeatScenarios().isEmpty()) d.preconditions.add("Repeated for " + tc.getRepeatScenarios().size() + " scenario(s)");
        if (tc.isSkipTest()) d.preconditions.add("Marked skip");

        if (tc.isSoapBase()) {
            d.steps.add("Call SOAP operation " + tc.getOperationName() + " (" + tc.getWsdlKey() + ")");
        } else {
            String url = (tc.getBaseUrl() == null ? "" : tc.getBaseUrl()) + (tc.getUrl() == null ? "" : tc.getUrl());
            if (tc.getExQueryPart() != null && !tc.getExQueryPart().isBlank()) url += (url.contains("?") ? "&" : "?") + tc.getExQueryPart();
            d.steps.add("Send " + (tc.getMethod() == null ? "GET" : tc.getMethod().toUpperCase()) + " " + url);
        }
        if (tc.getHeaders() != null && !tc.getHeaders().isEmpty()) {
            StringBuilder h = new StringBuilder("With headers: ");
            int i = 0;
            for (Map.Entry<String, String> e : tc.getHeaders().entrySet()) {
                if (i++ > 0) h.append(", ");
                h.append(e.getKey()).append(": ").append(abbreviate(e.getValue(), 60));
            }
            d.steps.add(h.toString());
        }
        if (tc.getContent() != null && !tc.getContent().isBlank()) d.steps.add("With body: " + abbreviate(tc.getContent().replaceAll("\\s+", " "), 300));
        if (tc.getContentFile() != null && !tc.getContentFile().isBlank()) d.steps.add("With body from file " + tc.getContentFile());
        List<String> mp = tc.getMultipartContent();
        if (mp != null && !mp.isEmpty()) d.steps.add("With form parts: " + String.join("; ", mp));
        if (tc.getPerfConfig() != null && tc.getPerfConfig().getType() != null && !tc.getPerfConfig().getType().isBlank()
                && !"none".equals(tc.getPerfConfig().getType())) {
            d.steps.add("Load-tested with " + tc.getPerfConfig().getType());
        }

        if (tc.getExpectedResCode() > 0) d.expected.add("Status code " + tc.getExpectedResCode());
        if (tc.getExpectedResContentType() != null && !tc.getExpectedResContentType().isBlank()) d.expected.add("Content type " + tc.getExpectedResContentType());
        if (tc.getExpectedResContent() != null && !tc.getExpectedResContent().isBlank()) d.expected.add("Body matches: " + abbreviate(tc.getExpectedResContent(), 200));
        if (tc.getExpectedNodes() != null) {
            for (String n : tc.getExpectedNodes()) d.expected.add(n.indexOf(',') < 0 ? "Response has " + n : "Response check: " + n);
        }
        if (tc.getLogicalValidations() != null) for (String v : tc.getLogicalValidations()) d.expected.add("Condition holds: " + v);
        if (tc.getWorkflowContextParameterMap() != null) {
            for (Map.Entry<String, String> e : tc.getWorkflowContextParameterMap().entrySet()) {
                d.expected.add("Captures " + e.getKey() + " from " + e.getValue());
            }
        }
        if (d.expected.isEmpty()) d.expected.add("Request succeeds");
        return d;
    }

    private static String abbreviate(String s, int n) {
        if (s == null) return "";
        return s.length() > n ? s.substring(0, n - 3) + "..." : s;
    }
}
