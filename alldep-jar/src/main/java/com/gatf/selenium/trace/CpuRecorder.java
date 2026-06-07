package com.gatf.selenium.trace;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * CPU sampling profile recorder. Starts the profile at attach and, at stop, retrieves the
 * whole profile once and writes {@code cpu.cpuprofile} (the Chrome DevTools profile format).
 * Not streamed — a sampling profile is delivered as one document at stop; the transport hands
 * back the profile JSON already serialized from the raw CDP response.
 *
 * @author Sumeet Chhetri
 */
public final class CpuRecorder {
    private final BrowserControl browser;
    private final Path traceDir;

    public CpuRecorder(BrowserControl browser, Path traceDir) {
        this.browser = browser;
        this.traceDir = traceDir;
    }

    public void start() {
        try { browser.startCpu(); } catch (Exception ignored) {}
    }

    public void stop() {
        String profile;
        try { profile = browser.stopCpu(); } catch (Exception e) { return; }
        if (profile == null || profile.isEmpty()) return;
        try {
            Files.write(traceDir.resolve("cpu.cpuprofile"), profile.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {}
    }
}
