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
