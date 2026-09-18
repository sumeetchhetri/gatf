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
        if ("bidi".equals(cfg.getProtocol())) return new BiDiTransport(driver, cfg);
        if ("hybrid".equals(cfg.getProtocol())) {
            // G10: BiDi for everything it can serve; CDP only for the Chromium-only domains (CPU/coverage/screencast)
            BiDiTransport bidi = new BiDiTransport(driver, cfg);
            if (driver instanceof HasDevTools) {
                try {
                    return new HybridTransport(bidi, new CdpTransport(driver, ((HasDevTools) driver).getDevTools(), cfg));
                } catch (Exception cdpUnavailable) {
                    return bidi;
                }
            }
            return bidi;   // e.g. Firefox: CPU/coverage simply unsupported
        }
        if (driver instanceof HasDevTools) {
            DevTools dt = ((HasDevTools) driver).getDevTools();
            return new CdpTransport(driver, dt, cfg);
        }
        return new BiDiTransport(driver, cfg);
    }

    /**
     * Print the current page to PDF without tracing and without pinning a CDP version (D-2). In order:
     * <ol>
     *  <li>raw CDP {@code Page.printToPDF} (Chromium, incl. an augmented Grid driver);</li>
     *  <li>BiDi {@code browsingContext.print} when a live BiDi socket exists (Firefox; Chromium with webSocketUrl);</li>
     *  <li>the W3C classic print command.</li>
     * </ol>
     * Both transports' printPdf() already fall back to W3C print. Neither constructor opens a DevTools session.
     * Returns null when none of the three is available.
     */
    public static byte[] printPdf(WebDriver driver) {
        TraceConfig cfg = new TraceConfig();
        byte[] pdf = null;
        if (driver instanceof org.openqa.selenium.chromium.HasCdp) {
            pdf = new CdpTransport(driver, null, cfg).printPdf();
        }
        if ((pdf == null || pdf.length == 0) && (BidiRaw.available(driver) || driver instanceof org.openqa.selenium.PrintsPage)) {
            pdf = new BiDiTransport(driver, cfg).printPdf();
        }
        return pdf == null || pdf.length == 0 ? null : pdf;
    }
}
