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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.gatf.executor.core.GatfExecutorConfig;

/** M2 — the web-first retry budget, on a fake clock. */
class WebFirstTest {

    /** A clock that only moves when the code under test sleeps. */
    static final class FakeClock implements Actionability.Clock {
        long t = 1_000;
        public long now() { return t; }
        public void sleep(long ms) { t += ms; }
    }

    /** The handleWaitFuncWL retry shape: attempt, on mismatch ask the budget, poll, repeat. */
    private static String assertEventually(String[] frames, long timeoutMs, FakeClock clock) {
        WebFirst.Budget budget = null;
        int i = 0;
        while (true) {
            String text = frames[Math.min(i, frames.length - 1)];
            if (text.equals("Welcome")) return text + "@" + i;
            WebFirst.Mismatch mm = new WebFirst.Mismatch("Element not found by selector css@'h1' at line number 3 ",
                    WebFirst.describe("text", "=", "Welcome", null));
            if (budget == null) budget = new WebFirst.Budget(timeoutMs, clock);
            if (!budget.retry()) throw mm.after(budget.elapsed());
            clock.sleep(WebFirst.POLL_MS);
            i++;
        }
    }

    @Test
    void lateRenderedTextPassesWithinTheBudget() {
        FakeClock c = new FakeClock();
        assertThat(assertEventually(new String[] { "Loading", "Loading", "Loading", "Welcome" }, 5000, c)).isEqualTo("Welcome@3");
        assertThat(c.t - 1_000).isEqualTo(3 * WebFirst.POLL_MS);
    }

    @Test
    void neverTrueFailsAfterTheBudgetNamingTheCondition() {
        FakeClock c = new FakeClock();
        try {
            assertEventually(new String[] { "Loading" }, 1000, c);
            throw new AssertionError("expected a mismatch");
        } catch (WebFirst.Mismatch m) {
            assertThat(m.getMessage()).isEqualTo("Element not found by selector css@'h1' at line number 3: text = \"Welcome\" still false after 1000 ms");
        }
    }

    @Test
    void budgetStartsAtFirstMismatchAndDefaultsTo5s() {
        FakeClock c = new FakeClock();
        WebFirst.Budget b = new WebFirst.Budget(0, c);
        assertThat(b.timeoutMs()).isEqualTo(WebFirst.DEFAULT_ASSERT_TIMEOUT_MS);
        c.t += 60_000;                      // time spent finding the element does not count
        assertThat(b.elapsed()).isEqualTo(0L);
        assertThat(b.retry()).isTrue();
        c.t += 4_999;
        assertThat(b.retry()).isTrue();
        c.t += 1;
        assertThat(b.retry()).isFalse();
    }

    @Test
    void describeListsValues() {
        assertThat(WebFirst.describe("attr@href", "%", "/home%", null)).isEqualTo("attr@href % \"/home%\"");
        assertThat(WebFirst.describe(null, null, null, new String[] { "a", "b" })).isEqualTo("value [a, b]");
    }

    @Test
    void configDefaultsKeepLegacyBehaviour() {
        GatfExecutorConfig cfg = new GatfExecutorConfig();
        assertThat(cfg.webFirstAssertionsEnabled()).isFalse();
        assertThat(cfg.effectiveAssertTimeoutMs()).isEqualTo(WebFirst.DEFAULT_ASSERT_TIMEOUT_MS);
        cfg.setWebFirstAssertions(Boolean.TRUE);
        cfg.setAssertTimeout(750L);
        assertThat(cfg.webFirstAssertionsEnabled()).isTrue();
        assertThat(cfg.effectiveAssertTimeoutMs()).isEqualTo(750L);
    }
}
