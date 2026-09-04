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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** G10 — hybrid routing (BiDi first, CDP only for CPU/coverage/screencast), BiDi BytesValue decoding. */
class TransportParityTest {

    static final class Fake implements BrowserControl {
        final String name;
        final Set<Capability> caps;
        final List<String> calls = new ArrayList<String>();

        Fake(String name, Set<Capability> caps) {
            this.name = name;
            this.caps = caps;
        }

        public boolean supports(Capability c) { return caps.contains(c); }
        public void enableNetwork(NetworkListener l) { calls.add("net"); }
        public void disableNetwork() { }
        public void enableConsole(ConsoleListener l) { calls.add("console"); }
        public void disableConsole() { }
        public String captureDomSnapshot() { calls.add("dom"); return name; }
        public byte[] captureScreenshot() { calls.add("shot"); return new byte[0]; }
        public byte[] fetchBody(String id) { calls.add("body"); return name.getBytes(StandardCharsets.UTF_8); }
        public void startCpu() { calls.add("cpu"); }
        public void startCoverage() { calls.add("cov"); }
        public void enableVideo(VideoListener l) { calls.add("video"); }
        public byte[] printPdf() { calls.add("pdf"); return new byte[] { 1 }; }
        public void close() { calls.add("close"); }
    }

    @Test
    void hybridKeepsCdpToTheChromiumOnlyDomains() {
        Fake bidi = new Fake("bidi", EnumSet.of(Capability.NETWORK, Capability.CONSOLE, Capability.DOM_SNAPSHOT,
                Capability.SCREENSHOT, Capability.METRICS, Capability.RESPONSE_BODY, Capability.PRINT_PDF));
        Fake cdp = new Fake("cdp", EnumSet.allOf(Capability.class));
        HybridTransport h = new HybridTransport(bidi, cdp);

        for (Capability c : Capability.values()) assertThat(h.supports(c)).isTrue();          // full parity on Chromium
        h.enableNetwork(null);
        h.enableConsole(null);
        h.captureDomSnapshot();
        h.fetchBody("r1");
        h.printPdf();
        h.startCpu();
        h.startCoverage();
        h.enableVideo(null);
        assertThat(bidi.calls).containsExactly("net", "console", "dom", "body", "pdf");        // BiDi-first
        assertThat(cdp.calls).containsExactly("cpu", "cov", "video");                          // CDP only here
        h.close();
        assertThat(bidi.calls.contains("close") && cdp.calls.contains("close")).isTrue();
    }

    @Test
    void hybridWithoutCdpScreencastFallsBackToBidiPolling() {
        Fake bidi = new Fake("bidi", EnumSet.of(Capability.SCREENCAST));
        Fake cdp = new Fake("cdp", EnumSet.noneOf(Capability.class));
        HybridTransport h = new HybridTransport(bidi, cdp);
        assertThat(h.supports(Capability.SCREENCAST)).isTrue();
        assertThat(h.supports(Capability.CPU_PROFILE)).isFalse();
        h.enableVideo(null);
        assertThat(bidi.calls).containsExactly("video");
    }

    @Test
    void bidiBytesValueDecoding() {
        Map<String, Object> s = new LinkedHashMap<String, Object>();
        s.put("type", "string");
        s.put("value", "{\"ok\":true}");
        assertThat(new String(BiDiTransport.decodeBytes(s), StandardCharsets.UTF_8)).isEqualTo("{\"ok\":true}");
        Map<String, Object> b = new LinkedHashMap<String, Object>();
        b.put("type", "base64");
        b.put("value", "AAEC");
        assertThat(Arrays.equals(BiDiTransport.decodeBytes(b), new byte[] { 0, 1, 2 })).isTrue();
        assertThat(BiDiTransport.decodeBytes(null) == null).isTrue();
        assertThat(new TraceConfig().setVideoPollFps(99).getVideoPollFps()).isEqualTo(10);    // clamped
    }
}
