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

import java.util.List;

import org.junit.jupiter.api.Test;

/** M2 — soft (??~) assertion collection. */
class SoftFailuresTest {

    @Test
    void threeSoftFailuresAreAllListed() {
        SoftFailures s = new SoftFailures();
        s.add("ui/login.sel:4", "Element not found by selector css@'h1' at line number 4 : text = \"Welcome\" still false after 5000 ms");
        s.add("ui/login.sel:5", "Element not found by selector id@'count' at line number 5 \nstack line");
        s.add("", "no location");
        List<String> f = s.drain();
        assertThat(f).hasSize(3);
        assertThat(f.get(1)).isEqualTo("ui/login.sel:5: Element not found by selector id@'count' at line number 5");
        SoftFailures.SoftAssertionError e = new SoftFailures.SoftAssertionError(f);
        assertThat(e.getMessage()).startsWith("3 soft assertion(s) failed: ui/login.sel:4: ")
                .contains("\n  ui/login.sel:5: ").contains("\n  no location");
        assertThat(e.getFailures()).hasSize(3);
    }

    @Test
    void drainClearsSoEachResultGetsItsOwn() {
        SoftFailures s = new SoftFailures();
        assertThat(s.drain()).isEmpty();
        s.add("a.sel:1", "x");
        assertThat(s.size()).isEqualTo(1);
        assertThat(s.drain()).containsExactly("a.sel:1: x");
        assertThat(s.drain()).isEmpty();
    }

    @Test
    void restorePutsTheCallersFailuresFirst() {
        SoftFailures s = new SoftFailures();
        s.add("main.sel:3", "before call");
        List<String> caller = s.drain();          // set aside while a subtest runs
        s.add("main.sel:9", "after call");
        s.restore(caller);
        s.restore(java.util.Collections.<String>emptyList());
        assertThat(s.drain()).containsExactly("main.sel:3: before call", "main.sel:9: after call");
    }
}
