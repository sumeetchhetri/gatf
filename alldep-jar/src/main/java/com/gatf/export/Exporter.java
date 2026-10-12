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

/**
 * Δ160 — one export backend: turns a parsed {@link SelScript} into a test file for another framework. Backends never
 * re-parse .sel text and never resolve {@code ${...}} / {@code $v{...}} values. Each unsupported statement becomes a
 * {@code TODO(gatf-export)} comment in the output and an {@link ExportResult.Item} in the report. Δ161 (Selenium Java)
 * adds a second implementation and registers it in {@link Exporters}.
 */
public interface Exporter {

    /** The CLI name: {@code gatf export <id> ...}. */
    String id();

    /** The output file name for a script name, e.g. {@code login} → {@code login.spec.ts}. */
    String fileName(String scriptName);

    /**
     * Exports one script.
     *
     * @throws ExportException when the script cannot be exported at all (e.g. two {@code ${...}} names that map to
     *         the same environment variable)
     */
    ExportResult export(SelScript script, ExportOptions options);
}
