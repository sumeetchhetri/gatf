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
package com.gatf.selenium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedList;
import java.util.Arrays;
import java.util.Deque;

import org.junit.jupiter.api.Test;

import com.gatf.executor.core.GatfExecutorConfig;

/** M1: the auto-wait polling loop, driven by a scripted probe and a fake clock (no browser). */
class ActionabilityTest {

    /** Fake clock: sleep advances time; every probe call also costs {@code probeCostMs}. */
    static final class FakeClock implements Actionability.Clock {
        long t = 1_000_000L;
        long slept = 0;
        public long now() { return t; }
        public void sleep(long ms) { t += ms; slept += ms; }
    }

    static final class Script implements java.util.function.Function<String, String> {
        final Deque<String> answers;
        final FakeClock clock;
        final long cost;
        int calls = 0;
        String lastMode;
        Script(FakeClock clock, long cost, String... answers) {
            this.clock = clock;
            this.cost = cost;
            this.answers = new LinkedList<String>(Arrays.asList(answers));
        }
        public String apply(String mode) {
            calls++;
            lastMode = mode;
            clock.t += cost;
            return answers.size() > 1 ? answers.poll() : answers.peek();
        }
    }

    @Test
    void readyAtOnceWaitsZero() {
        FakeClock c = new FakeClock();
        Script p = new Script(c, 0, (String) null);
        assertEquals(0, Actionability.await("click", 5000, p, c));
        assertEquals(1, p.calls);
        assertEquals("click", p.lastMode);
        // the probe's own latency (two animation frames) is not reported as waiting
        FakeClock c2 = new FakeClock();
        assertEquals(0, Actionability.await("click", 5000, new Script(c2, 33, (String) null), c2));
    }

    @Test
    void becomesReadyAfterRetries() {
        FakeClock c = new FakeClock();
        Script p = new Script(c, 33, "not visible", "not stable (moving or animating)", "covered by <div.spinner>", null);
        long waited = Actionability.await("click", 5000, p, c);
        assertEquals(4, p.calls);
        assertTrue(waited > 0 && waited < 5000, "waited " + waited);
    }

    @Test
    void coveredElementFailsWithBlockerNamed() {
        FakeClock c = new FakeClock();
        Script p = new Script(c, 33, "covered by <div.modal>");
        Actionability.NotActionableException e = assertThrows(Actionability.NotActionableException.class,
                () -> Actionability.await("click", 2000, p, c));
        assertEquals("covered by <div.modal>", e.getReason());
        assertTrue(e.getWaitedMs() >= 2000, "waited " + e.getWaitedMs());
        assertTrue(e.getWaitedMs() < 2600, "overshoot " + e.getWaitedMs());
        assertFalse(e.isDetached());
        String msg = e.withLocator("css@#save").getMessage();
        assertTrue(msg.contains("css@#save") && msg.contains("covered by <div.modal>") && msg.contains("click"), msg);
    }

    @Test
    void detachedFailsFastForReSearch() {
        FakeClock c = new FakeClock();
        Script p = new Script(c, 5, Actionability.DETACHED);
        Actionability.NotActionableException e = assertThrows(Actionability.NotActionableException.class,
                () -> Actionability.await("type", 5000, p, c));
        assertTrue(e.isDetached());
        assertEquals(1, p.calls);
    }

    @Test
    void backoffIsBoundedAndNeverSleepsPastTimeout() {
        FakeClock c = new FakeClock();
        Script p = new Script(c, 0, "disabled");
        assertThrows(Actionability.NotActionableException.class, () -> Actionability.await("click", 1234, p, c));
        assertEquals(1234, c.slept);   // zero-cost probe: total sleep == the budget exactly
        assertTrue(p.calls >= 7);
    }

    @Test
    void nonPositiveTimeoutUsesDefault() {
        FakeClock c = new FakeClock();
        Script p = new Script(c, 0, "not visible");
        Actionability.NotActionableException e = assertThrows(Actionability.NotActionableException.class,
                () -> Actionability.await("click", 0, p, c));
        assertEquals(Actionability.DEFAULT_TIMEOUT_MS, e.getWaitedMs());
    }

    @Test
    void modes() {
        assertEquals("click", Actionability.modeFor("click"));
        assertEquals("click", Actionability.modeFor("clickfo"));
        assertEquals("type", Actionability.modeFor("type"));
        assertEquals("type", Actionability.modeFor("sendkeysbl"));
        assertEquals("type", Actionability.modeFor("clear"));
        assertEquals("input", Actionability.modeFor("randomize"));
        assertEquals("input", Actionability.modeFor("chord"));
        assertEquals("dblclick", Actionability.modeFor("dblclick"));
        assertEquals("hover", Actionability.modeFor("hover"));
        assertEquals("click", Actionability.modeFor("hoverandclick"));
        assertEquals("select", Actionability.modeFor("select"));
        assertEquals("upload", Actionability.modeFor("upload"));
        assertEquals("submit", Actionability.modeFor("submit"));
        assertEquals("click", Actionability.modeFor(null));
        assertEquals("upload", Actionability.modeFor("type", "file://a/b.txt"));
        assertEquals("type", Actionability.modeFor("type", "hello"));
        assertEquals("input", Actionability.modeFor("randomize", null));
    }

    @Test
    void probeIsOneAsyncScript() {
        String js = Actionability.PROBE_JS;
        assertTrue(js.contains("arguments[arguments.length - 1]"));
        assertTrue(js.contains("elementFromPoint"));
        assertTrue(js.contains("requestAnimationFrame"));
        assertTrue(js.contains("\\s+"), "text block must keep the regex escape");
    }

    @Test
    void configDefaultsKeepLegacyBehaviour() {
        GatfExecutorConfig cfg = new GatfExecutorConfig();
        assertFalse(cfg.autoWaitEnabled());
        assertEquals(Actionability.DEFAULT_TIMEOUT_MS, cfg.effectiveActionTimeoutMs());
        cfg.setAutoWait(Boolean.TRUE);
        cfg.setActionTimeout(750L);
        assertTrue(cfg.autoWaitEnabled());
        assertEquals(750L, cfg.effectiveActionTimeoutMs());
        cfg.setActionTimeout(-1L);
        assertEquals(Actionability.DEFAULT_TIMEOUT_MS, cfg.effectiveActionTimeoutMs());
    }
}
