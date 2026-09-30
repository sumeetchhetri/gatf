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
    void executeViewQuery() {
        // Execute view: one grep, one grep-invert, tags comma/space separated; all blank = no filter
        assertThat(TestFilter.fromQuery(null, " ", "")).isNull();
        TestFilter f = TestFilter.fromQuery("login", "slow", "smoke, @checkout");
        assertThat(f.describe()).isEqualTo("--grep login --tag @smoke --tag @checkout --grep-invert slow");
        assertThat(f.matches("ui/login.sel")).isTrue();
        assertThat(f.matches("ui/cart.sel @checkout")).isTrue();
        assertThat(f.matches("ui/login-slow.sel")).isFalse();
        assertThat(f.matches("ui/cart.sel")).isFalse();
        assertThatThrownBy(() -> TestFilter.fromQuery("(", null, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void executeViewFilterIsPerThread() throws Exception {
        // E12: the Execute view's filter applies to its executor thread only; the CLI's static one stays the fallback
        TestFilter view = TestFilter.fromQuery("login", null, null), cli = TestFilter.fromQuery("cart", null, null);
        try {
            TestFilter.activateForThread(view);
            assertThat(TestFilter.active()).isSameAs(view);
            assertThat(TestFilter.isActive()).isTrue();
            TestFilter[] other = new TestFilter[1];
            boolean[] otherActive = new boolean[1];
            Thread t = new Thread(() -> { other[0] = TestFilter.active(); otherActive[0] = TestFilter.isActive(); });
            t.start();
            t.join();
            assertThat(other[0]).isNull();                 // a concurrent Play from Reports is not filtered
            assertThat(otherActive[0]).isFalse();
            TestFilter.activate(cli);
            assertThat(TestFilter.active()).isSameAs(view);   // this thread's filter wins
            TestFilter.activateForThread(null);
            assertThat(TestFilter.active()).isSameAs(cli);
        } finally {
            TestFilter.activateForThread(null);
            TestFilter.activate(null);
        }
        assertThat(TestFilter.isActive()).isFalse();
    }

    @Test
    void invalidRegexIsAUsageError() {
        assertThatThrownBy(() -> new TestFilter(List.of("("), null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("invalid --grep regex '('");
    }
}
