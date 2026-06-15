package com.gatf.selenium.trace;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.devtools.DevTools;
import org.openqa.selenium.devtools.HasDevTools;

/**
 * Selects the transport for a driver by {@code protocol} (D-2). {@code cdp} (default) builds a
 * {@link CdpTransport} over the driver's existing DevTools session; {@code bidi} builds the
 * {@link BiDiTransport} skeleton. A driver with no DevTools falls back to the inert BiDi
 * transport so tracing degrades quietly instead of failing.
 *
 * Compiled in CI (references Selenium types).
 *
 * @author Sumeet Chhetri
 */
public final class Transports {
    private Transports() {}

    public static BrowserControl create(WebDriver driver, TraceConfig cfg) {
        if ("bidi".equals(cfg.getProtocol())) return new BiDiTransport(driver);
        if (driver instanceof HasDevTools) {
            DevTools dt = ((HasDevTools) driver).getDevTools();
            return new CdpTransport(driver, dt, cfg);
        }
        return new BiDiTransport(driver);
    }
}
