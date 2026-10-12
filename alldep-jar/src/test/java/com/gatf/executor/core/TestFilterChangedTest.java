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
package com.gatf.executor.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;

/** H6 (Δ156): `--changed` leaves out exactly the scripts it proved unaffected, ANDed with --grep/--tag. */
class TestFilterChangedTest {

    static String t(String path, String... tags) { return TestFilter.selTitle(path, Arrays.asList(tags)); }

    @Test
    void excludesExactScriptsOnly() {
        TestFilter none = new TestFilter(null, null, null);
        assertTrue(none == none.excludingScripts(Collections.<String>emptyList(), "x"));
        TestFilter f = none.excludingScripts(Arrays.asList("ui\\a.sel", "/abs/b.sel"), "--changed HEAD (1/3 scripts)");
        assertFalse(f.isEmpty());
        assertFalse(f.matches(t("ui\\a.sel")));
        assertFalse(f.matches(t("ui/a.sel", "@smoke")));
        assertTrue(f.matches(t("ui/a.sel2")));
        assertTrue(f.matches(t("ui/a.sel.bak")));
        assertTrue(f.matches(t("x/ui/a.sel")));
        assertFalse(f.matches(t("/abs/b.sel")));
        assertTrue(f.matches(t("c.sel")));
        assertEquals("--changed HEAD (1/3 scripts)", f.describe());
    }

    @Test
    void andWithTheUsersFilters() {
        TestFilter g = new TestFilter(Collections.singletonList("checkout"), null, Collections.singletonList("smoke"));
        TestFilter f = g.excludingScripts(Collections.singletonList("checkout.sel"), "--changed main...HEAD");
        assertFalse(f.matches(t("checkout.sel")));                // excluded by --changed
        assertTrue(f.matches(t("checkout2.sel")));                // --grep checkout
        assertTrue(f.matches(t("x.sel", "@smoke")));              // --tag smoke
        assertFalse(f.matches(t("y.sel")));                        // matches no --grep/--tag
        assertEquals("--grep checkout --tag @smoke --changed main...HEAD", f.describe());
    }
}
