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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Q9 — MetricsRecorder writes webVitalsByPage from sink + drained entries, next to the final-page webVitals. */
class MetricsRecorderPageVitalsTest {

    @TempDir
    Path dir;

    static class Fake implements BrowserControl {
        final boolean pageVitals;
        Consumer<String> sink;
        boolean disabled;
        Fake(boolean pageVitals) { this.pageVitals = pageVitals; }
        @Override public boolean supports(Capability c) { return false; }
        @Override public void enableNetwork(NetworkListener l) { }
        @Override public void disableNetwork() { }
        @Override public void enableConsole(ConsoleListener l) { }
        @Override public void disableConsole() { }
        @Override public String captureDomSnapshot() { return null; }
        @Override public byte[] captureScreenshot() { return null; }
        @Override public byte[] fetchBody(String id) { return null; }
        @Override public String readWebVitals() { return "{\"url\":\"https://a/3\",\"id\":\"p3\",\"lcp\":700,\"cls\":0}"; }
        @Override public boolean enablePageVitals(Consumer<String> s) { sink = s; return pageVitals; }
        @Override public String drainPageVitals() { return "[{\"id\":\"p2\",\"url\":\"https://a/2\",\"ts\":20,\"lcp\":2600}]"; }
        @Override public void disablePageVitals() { disabled = true; }
        @Override public void close() { }
    }

    private String run(Fake b) throws Exception {
        MetricsRecorder r = new MetricsRecorder(b, dir, new TraceConfig());
        r.start();
        if (b.pageVitals) b.sink.accept("{\"id\":\"p1\",\"url\":\"https://a/1\",\"ts\":10,\"lcp\":1200,\"cls\":0.2}");
        r.stop();
        return new String(Files.readAllBytes(dir.resolve("performance.json")), StandardCharsets.UTF_8);
    }

    @Test
    void everyPageIsListedInOrderAndTheFinalPageFieldIsKept() throws Exception {
        Fake b = new Fake(true);
        String doc = run(b);
        assertThat(doc).contains(",\"webVitals\":{\"url\":\"https://a/3\",\"id\":\"p3\",\"lcp\":700,\"cls\":0}")
                .contains(",\"webVitalsByPage\":[{\"url\":\"https://a/1\",\"ts\":10,\"lcp\":1200,\"cls\":0.2},"
                        + "{\"url\":\"https://a/2\",\"ts\":20,\"lcp\":2600},{\"url\":\"https://a/3\",\"ts\":");
        assertThat(b.disabled).isTrue();
    }

    @Test
    void unsupportedTransportStillListsTheFinalPage() throws Exception {
        Fake b = new Fake(false);
        String doc = run(b);
        assertThat(doc).contains("\"webVitalsByPage\":[{\"url\":\"https://a/3\"").doesNotContain("https://a/2");
        assertThat(b.disabled).isFalse();
    }
}
