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
}
