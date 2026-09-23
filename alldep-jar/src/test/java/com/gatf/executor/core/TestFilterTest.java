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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

/** Q5: --grep / --grep-invert / --tag selection, tag extraction, dependency pull-in. */
class TestFilterTest {

    @Test
    void tagMatchesWholeTokenOnly() {
        TestFilter f = new TestFilter(List.of(), List.of(), List.of("smoke"));
        assertThat(f.matches("api/users.xml › List users @smoke")).isTrue();
        assertThat(f.matches("api/users.xml › List users @smoke-extra")).isFalse();
        assertThat(f.matches("ops@smoke.com")).isFalse();
    }

    @Test
    void grepIncludesAndGrepInvertWins() {
        TestFilter f = new TestFilter(List.of("users"), List.of("Delete"), List.of());
        assertThat(f.matches("api/users.xml › Create user")).isTrue();
        assertThat(f.matches("api/users.xml › Delete user")).isFalse();
        assertThat(f.matches("api/auth.xml › Login")).isFalse();
        assertThat(new TestFilter(List.of(), List.of("slow"), List.of()).matches("a › fast")).isTrue();
        assertThat(new TestFilter(null, null, null).isEmpty()).isTrue();
    }

    @Test
    void tagExtraction() {
        assertThat(TestFilter.tagsIn("Create user @smoke @api a@b.com @smoke")).containsExactly("@smoke", "@api");
        assertThat(TestFilter.apiTitle("api/users.xml", "Create user", "creates one @smoke @write"))
                .isEqualTo("api/users.xml › Create user @smoke @write");
        assertThat(TestFilter.selHeaderTags(List.of("// Login flow @smoke", "", "// owner: qa @checkout", "open chrome", "// @late")))
                .containsExactly("@smoke", "@checkout");
        assertThat(TestFilter.selTitle("ui\\login.sel", List.of("@smoke"))).isEqualTo("ui/login.sel @smoke");
    }

    private static final class T {
        final String file, name, desc, dep;
        T(String file, String name, String desc, String dep) { this.file = file; this.name = name; this.desc = desc; this.dep = dep; }
    }

    @Test
    void selectionKeepsOrderAndPullsInDependencies() {
        List<T> items = List.of(new T("api/auth.xml", "Login", "", null), new T("api/users.xml", "List users", "@smoke", "Login"),
                new T("api/users.xml", "Delete user", "", null));
        List<T> sel = new TestFilter(List.of(), List.of(), List.of("smoke"))
                .select(items, t -> TestFilter.apiTitle(t.file, t.name, t.desc), t -> t.name, t -> t.dep);
        assertThat(sel.stream().map(t -> t.name).collect(Collectors.toList())).containsExactly("Login", "List users");
    }

    @Test
    void invalidRegexIsAUsageError() {
        assertThatThrownBy(() -> new TestFilter(List.of("("), null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("invalid --grep regex '('");
    }
}
