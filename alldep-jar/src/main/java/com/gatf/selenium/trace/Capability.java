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

/**
 * Capabilities a {@link BrowserControl} transport may expose. A per-recorder capability
 * matrix (D-2) uses these so a transport can declare what it can serve today; recorders
 * skip a capability a transport reports as unsupported instead of failing.
 *
 * @author Sumeet Chhetri
 */
public enum Capability {
    NETWORK,        // request/response events (HAR)
    CONSOLE,        // console + log entries
    DOM_SNAPSHOT,   // outerHTML capture
    SCREENSHOT,     // page screenshot bytes
    RESPONSE_BODY,  // fetch a response body by request id
    SCREENCAST,     // Phase 2 (video)
    METRICS,        // Phase 2 (performance/memory)
    CPU_PROFILE,    // Profiler.start/stop -> .cpuprofile
    COVERAGE,       // Phase 2
    PRINT_PDF,      // G10: CDP Page.printToPDF / BiDi browsingContext.print / W3C print
    ROUTE           // M4: request interception (CDP Fetch.* / BiDi network.addIntercept)
}
