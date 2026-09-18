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

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.Pdf;
import org.openqa.selenium.PrintsPage;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chromium.HasCdp;

/** printpdf: Transports.printPdf picks raw CDP, then BiDi/W3C, with no DevTools session and no pinned CDP version. */
class PrintPdfTest {

    private static final byte[] PDF = "%PDF-1.7 fake".getBytes(StandardCharsets.US_ASCII);
    private static final String B64 = Base64.getEncoder().encodeToString(PDF);

    /** A driver implementing only the given interfaces; unimplemented calls return null. */
    private static WebDriver driver(Map<String, Object> answers, AtomicReference<String> called, Class<?>... ifaces) {
        Class<?>[] all = new Class<?>[ifaces.length + 1];
        all[0] = WebDriver.class;
        System.arraycopy(ifaces, 0, all, 1, ifaces.length);
        return (WebDriver) Proxy.newProxyInstance(PrintPdfTest.class.getClassLoader(), all, (p, m, a) -> {
            if (m.getDeclaringClass() == Object.class) {
                return m.getName().equals("equals") ? p == a[0] : m.getName().equals("hashCode") ? System.identityHashCode(p) : "proxy";
            }
            if (called != null) called.set(m.getName() + (a != null && a.length > 0 ? ":" + a[0] : ""));
            return answers.get(m.getName());
        });
    }

    @Test
    void chromiumUsesRawCdpPrintToPdf() {
        Map<String, Object> ans = new HashMap<>();
        Map<String, Object> cdp = new HashMap<>();
        cdp.put("data", B64);
        ans.put("executeCdpCommand", cdp);
        AtomicReference<String> called = new AtomicReference<>();
        assertThat(Transports.printPdf(driver(ans, called, HasCdp.class))).isEqualTo(PDF);
        assertThat(called.get()).isEqualTo("executeCdpCommand:Page.printToPDF");
    }

    @Test
    void nonChromiumFallsBackToW3cPrint() {
        Map<String, Object> ans = new HashMap<>();
        ans.put("print", new Pdf(B64));
        assertThat(Transports.printPdf(driver(ans, null, PrintsPage.class))).isEqualTo(PDF);
    }

    @Test
    void cdpFailureFallsBackToW3cPrint() {
        Map<String, Object> ans = new HashMap<>();
        ans.put("executeCdpCommand", new HashMap<String, Object>());   // no "data"
        ans.put("print", new Pdf(B64));
        assertThat(Transports.printPdf(driver(ans, null, HasCdp.class, PrintsPage.class))).isEqualTo(PDF);
    }

    @Test
    void driverThatCannotPrintYieldsNull() {
        assertThat(Transports.printPdf(driver(new HashMap<>(), null))).isNull();
    }
}
