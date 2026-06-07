package com.gatf.selenium.trace;

import org.openqa.selenium.WebDriver;

/**
 * Skeleton WebDriver BiDi implementation of {@link BrowserControl}. It reports no capabilities
 * yet, so recorders (which guard on {@link BrowserControl#supports(Capability)}) skip it cleanly
 * — this is what makes {@code protocol=bidi} a real, selectable option today without crashing.
 * Network/console/log land here first as BiDi parity is built (Phase 2); when the capability
 * matrix goes all-green the default flips and CdpTransport is retired (D-2).
 *
 * Compiled in CI (references a Selenium type).
 *
 * @author Sumeet Chhetri
 */
public final class BiDiTransport implements BrowserControl {

    @SuppressWarnings("unused")
    private final WebDriver driver;

    BiDiTransport(WebDriver driver) { this.driver = driver; }

    @Override public boolean supports(Capability c) { return false; }

    @Override public void enableNetwork(NetworkListener l) {
        throw new UnsupportedByTransport("BiDi network capture not implemented yet (Phase 2)");
    }
    @Override public void disableNetwork() { }

    @Override public void enableConsole(ConsoleListener l) {
        throw new UnsupportedByTransport("BiDi console capture not implemented yet (Phase 2)");
    }
    @Override public void disableConsole() { }

    @Override public String captureDomSnapshot() { return null; }
    @Override public byte[] captureScreenshot() { return null; }
    @Override public byte[] fetchBody(String requestId) { return null; }
    @Override public void close() { }
}
