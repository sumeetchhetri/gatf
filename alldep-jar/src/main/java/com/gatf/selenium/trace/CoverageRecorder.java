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
package com.gatf.selenium.trace;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * JS + CSS coverage recorder. Starts precise JS coverage and CSS rule-usage tracking at attach
 * and, at stop, retrieves both and writes {@code coverage.json} ({@code {js, css}}). The raw CDP
 * coverage payloads are serialized as-is; percentage roll-ups are left to the trace viewer.
 *
 * @author Sumeet Chhetri
 */
public final class CoverageRecorder {
    private final BrowserControl browser;
    private final Path traceDir;

    public CoverageRecorder(BrowserControl browser, Path traceDir) {
        this.browser = browser;
        this.traceDir = traceDir;
    }

    public void start() {
        try { browser.startCoverage(); } catch (Exception ignored) {}
    }

    public void stop() {
        String cov;
        try { cov = browser.stopCoverage(); } catch (Exception e) { return; }
        if (cov == null || cov.isEmpty()) return;
        try {
            Files.write(traceDir.resolve("coverage.json"), cov.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {}
    }
}
