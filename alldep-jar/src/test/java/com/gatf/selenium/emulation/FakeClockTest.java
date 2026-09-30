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
package com.gatf.selenium.emulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;

/** M11: clock statement parsing + per-document registration calls (the JS itself: tools/smoke/test-clock.mjs). */
class FakeClockTest {

    @Test
    void parsesEveryForm() {
        FakeClock i = FakeClock.parse("install 2026-01-01T09:00:00Z");
        assertThat(i.op).isEqualTo("install");
        assertThat(i.value).isEqualTo(1767258000000L);
        assertThat(FakeClock.parse("install").value).isNull();
        assertThat(FakeClock.parse("set '2026-12-31'").value).isEqualTo(1798675200000L);
        assertThat(FakeClock.parse("set 1767258000000").value).isEqualTo(1767258000000L);
        assertThat(FakeClock.parse("set 2026-01-01T10:00:00+01:00").value).isEqualTo(1767258000000L);
        assertThat(FakeClock.parse("fastforward 10s").value).isEqualTo(10_000L);
        assertThat(FakeClock.parse("ff 250").op).isEqualTo("fastforward");
        assertThat(FakeClock.parse("uninstall").op).isEqualTo("uninstall");
        assertThat(FakeClock.parse("pause").op).isEqualTo("pause");
        assertThat(FakeClock.parse("resume").op).isEqualTo("resume");
        assertThat(FakeClock.parse("set 2026-01-01 09:00:00").value).isEqualTo(1767258000000L);   // space instead of T
    }

    @Test
    void durations() {
        assertThat(FakeClock.duration("1500")).isEqualTo(1500L);
        assertThat(FakeClock.duration("250ms")).isEqualTo(250L);
        assertThat(FakeClock.duration("1h30m")).isEqualTo(5_400_000L);
        assertThat(FakeClock.duration("1.5s")).isEqualTo(1500L);
        assertThat(FakeClock.duration("2d")).isEqualTo(172_800_000L);
        assertThat(FakeClock.duration("01:30")).isEqualTo(90_000L);
        assertThat(FakeClock.duration("1:00:00")).isEqualTo(3_600_000L);
        assertThatThrownBy(() -> FakeClock.duration("soon")).hasMessageContaining("bad duration");
        assertThatThrownBy(() -> FakeClock.duration("5x")).hasMessageContaining("bad duration");
        assertThatThrownBy(() -> FakeClock.duration("99999999999999999999")).hasMessageContaining("bad duration");
    }

    @Test
    void badInput() {
        assertThatThrownBy(() -> FakeClock.parse("rewind 5s")).hasMessageContaining("clock install");
        assertThatThrownBy(() -> FakeClock.parse("set")).hasMessageContaining("clock set <time>");
        assertThatThrownBy(() -> FakeClock.parse("set tomorrow")).hasMessageContaining("bad time");
        assertThatThrownBy(() -> FakeClock.parse("fastforward")).hasMessageContaining("duration");
        assertThatThrownBy(() -> FakeClock.parse("uninstall now")).hasMessageContaining("no value");
        assertThat(FakeClock.preload(1L, "a'b;c")).contains("install(v, 'abc')");   // the generation is sanitised into the script
    }

    @Test
    void perDocumentRegistration() {
        Emulation.Call cdp = FakeClock.registerPreload(true, 5L, "g1", null);
        assertThat(cdp.method).isEqualTo("Page.addScriptToEvaluateOnNewDocument");
        assertThat((String) cdp.params.get("source")).contains("window.__gatfClock").contains("v = 5;").contains("install(v, 'g1')");
        assertThat(FakeClock.preloadId(true, Map.of("identifier", "7"))).isEqualTo("7");
        assertThat(FakeClock.unregisterPreload(true, "7").method).isEqualTo("Page.removeScriptToEvaluateOnNewDocument");

        Emulation.Call bidi = FakeClock.registerPreload(false, 5L, "g1", "ctx");
        assertThat(bidi.method).isEqualTo("script.addPreloadScript");
        assertThat((String) bidi.params.get("functionDeclaration")).startsWith("function () {");
        assertThat(bidi.params.get("contexts").toString()).isEqualTo("[ctx]");
        assertThat(FakeClock.preloadId(false, Map.of("script", "s1"))).isEqualTo("s1");
        assertThat(FakeClock.unregisterPreload(false, "s1").params).containsEntry("script", "s1");
    }
}
