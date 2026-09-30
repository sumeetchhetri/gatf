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
package com.gatf.imports;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.gatf.executor.core.TestCase;

/**
 * Outcome of importing an external test artifact (Playwright spec, JMeter .jmx, cURL, Postman, HAR) into GATF.
 * Carries the generated file content, an unsupported-construct report, and summary stats.
 * For the API importers (JMeter, cURL, Postman, HAR), {@link #testCases} holds the built TestCase objects — the handler serializes them
 * with the same XmlMapper GATF uses everywhere else, so the output round-trips exactly.
 */
public class ImportResult {

    /** A source construct the importer could not translate (best-effort import, reported not silently dropped). */
    public static class Unsupported {
        public int line;
        public String construct;
        public String reason;

        public Unsupported() {}

        public Unsupported(int line, String construct, String reason) {
            this.line = line;
            this.construct = construct;
            this.reason = reason;
        }
    }

    public String type;                 // playwright | jmeter | curl | postman | har | openapi
    public String fileName;             // suggested output test-file name
    public String kind;                 // sel | xml
    public String content;              // generated seleasy or XML (filled by the importer/handler)
    public List<Unsupported> unsupported = new ArrayList<Unsupported>();
    public Map<String, Object> stats = new LinkedHashMap<String, Object>();
    // JMeter only: load settings from the first ThreadGroup, as GatfExecutorConfig keys (null if none)
    public Map<String, Object> loadConfig;
    // Postman collection variables / OpenAPI auth placeholders, as gatf-config globalVariables (null if none)
    public Map<String, Object> variables;

    // API importers: the built cases; the handler serializes via WorkflowContextHandler.XOM for exact fidelity.
    @JsonIgnore
    public List<TestCase> testCases;

    public void unsupported(int line, String construct, String reason) {
        unsupported.add(new Unsupported(line, construct, reason));
    }

    public void stat(String k, Object v) {
        stats.put(k, v);
    }
}
