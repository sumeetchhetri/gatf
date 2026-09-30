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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** M3 — recorder / crawler locator choice: unique testid → role → label, else the legacy selector. */
class LocatorRankingTest {

    static Map<String, Object> c(String s, String v, int n) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("s", s);
        m.put("v", v);
        m.put("n", n);
        return m;
    }

    static Map<String, Object> role(String role, String name, int n) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("s", "role");
        m.put("role", role);
        m.put("name", name);
        m.put("n", n);
        return m;
    }

    @Test
    void orderIsTestIdThenRoleThenLabel() {
        assertThat(LocatorRanking.choose(List.of(c("label", "Email", 1), role("textbox", "Email", 1), c("testid", "email", 1)), "id@email"))
                .isEqualTo("testid@email");
        assertThat(LocatorRanking.choose(List.of(c("label", "Email", 1), role("textbox", "Email", 1)), "id@email"))
                .isEqualTo("role@textbox[name=Email]");
        assertThat(LocatorRanking.choose(List.of(c("label", "Email address", 1)), "id@email")).isEqualTo("label@'Email address'");
    }

    @Test
    void ambiguousOrUnsafeCandidatesFallThrough() {
        assertThat(LocatorRanking.choose(List.of(role("button", "Save", 2), c("testid", "x", 3)), "css@\"button.save\""))
                .isEqualTo("css@\"button.save\"");
        assertThat(LocatorRanking.choose(List.of(role("button", "Say \"hi\" it's", 1)), "id@b")).isEqualTo("id@b");   // both quotes
        assertThat(LocatorRanking.choose(List.of(role("link", "a]b", 1)), "id@b")).isEqualTo("id@b");
        assertThat(LocatorRanking.choose(List.of(role("button", "=Save", 1)), "id@b")).isEqualTo("id@b");         // reads as a mode
        assertThat(LocatorRanking.choose(List.of(role("button", "Pay ${amount}", 1)), "id@b")).isEqualTo("id@b");
        assertThat(LocatorRanking.choose(List.of(role("button", "x".repeat(61), 1)), "id@b")).isEqualTo("id@b");
        assertThat(LocatorRanking.choose(null, "id@b")).isEqualTo("id@b");
    }

    @Test
    void quotingPicksTheFreeQuote() {
        assertThat(LocatorRanking.choose(List.of(role("button", "Don't save", 1)), "id@b")).isEqualTo("role@button[name=\"Don't save\"]");
        assertThat(LocatorRanking.choose(List.of(role("heading", "Orders (3)", 1)), "id@b")).isEqualTo("role@heading[name='Orders (3)']");
    }

    @Test
    void emittedRoleLocatorParsesBack() {
        String loc = LocatorRanking.choose(List.of(role("heading", "Orders (3)", 1)), "id@b");
        String cls = loc.substring(loc.indexOf('@') + 1);
        assertThat(SemanticLocators.parse("role", cls)).containsEntry("role", "heading").containsEntry("value", "Orders (3)").containsEntry("mode", "full");
    }
}
