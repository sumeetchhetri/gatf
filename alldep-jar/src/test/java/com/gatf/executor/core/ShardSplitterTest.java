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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** M7: deterministic file sharding for gatf test --shard i/n. */
class ShardSplitterTest {

    private static List<String> files(int n) {
        List<String> l = new ArrayList<String>();
        for (int i = 0; i < n; i++) l.add("ui/flow-" + i + ".sel");
        return l;
    }

    @Test
    void sameFileSetAlwaysLandsOnTheSameShard() {
        List<String> f = files(37);
        List<String> shuffled = new ArrayList<String>(f);
        Collections.shuffle(shuffled, new java.util.Random(7));
        Map<String, Integer> a = ShardSplitter.assign(f, 4);
        // input order, a Windows separator or a ./ prefix do not change the split
        List<String> spelled = new ArrayList<String>();
        for (String s : shuffled) spelled.add(s.hashCode() % 2 == 0 ? "./" + s.replace('/', '\\') : s);
        assertThat(ShardSplitter.assign(shuffled, 4)).isEqualTo(a);
        assertThat(ShardSplitter.assign(spelled, 4)).isEqualTo(a);
        // repeated calls (and fresh instances) agree
        for (int i = 1; i <= 4; i++) {
            assertThat(new ShardSplitter(i, 4).select(f, p -> p)).isEqualTo(new ShardSplitter(i, 4).select(f, p -> p));
        }
    }

    @Test
    void shardsPartitionTheFilesAndAreBalanced() {
        List<String> f = files(37);
        Set<String> seen = new HashSet<String>();
        int min = Integer.MAX_VALUE, max = 0;
        for (int i = 1; i <= 4; i++) {
            List<String> mine = new ShardSplitter(i, 4).select(f, p -> p);
            for (String s : mine) assertThat(seen.add(s)).as("%s in two shards", s).isTrue();
            min = Math.min(min, mine.size());
            max = Math.max(max, mine.size());
        }
        assertThat(seen).hasSize(37);
        assertThat(max - min).isLessThanOrEqualTo(1);
    }

    @Test
    void selectKeepsInputOrderAndEveryTestOfAFile() {
        List<String[]> tests = new ArrayList<String[]>();
        for (String file : files(6)) for (int t = 0; t < 3; t++) tests.add(new String[] { file, file + "#" + t });
        for (int i = 1; i <= 3; i++) {
            List<String[]> mine = new ShardSplitter(i, 3).select(tests, x -> x[0]);
            assertThat(mine.size() % 3).isEqualTo(0);
            for (int k = 1; k < mine.size(); k++) assertThat(tests.indexOf(mine.get(k))).isGreaterThan(tests.indexOf(mine.get(k - 1)));
        }
    }

    @Test
    void dependenciesInAnotherShardComeAlong() {
        // API relatedTestName: "b" in file b.xml depends on "login" in auth.xml
        List<String[]> tests = new ArrayList<String[]>();   // {file, name, dependsOn}
        tests.add(new String[] { "auth.xml", "login", null });
        for (int i = 0; i < 10; i++) tests.add(new String[] { "f" + i + ".xml", "t" + i, "login" });
        Map<String, Integer> shard = ShardSplitter.assign(List.of("auth.xml", "f0.xml", "f1.xml", "f2.xml", "f3.xml", "f4.xml",
                "f5.xml", "f6.xml", "f7.xml", "f8.xml", "f9.xml"), 2);
        int other = shard.get("auth.xml") == 1 ? 2 : 1;
        List<String[]> mine = new ShardSplitter(other, 2).selectWithDependencies(tests, x -> x[0], x -> x[1], x -> x[2]);
        assertThat(mine.get(0)[1]).isEqualTo("login");   // the prerequisite, in input order
        assertThat(mine.size()).isGreaterThan(1);
    }

    @Test
    void moreShardsThanFilesLeavesSomeEmpty() {
        int empty = 0;
        for (int i = 1; i <= 5; i++) if (new ShardSplitter(i, 5).select(files(3), p -> p).isEmpty()) empty++;
        assertThat(empty).isEqualTo(2);
    }

    @Test
    void parseAndValidation() {
        ShardSplitter s = ShardSplitter.parse(" 2 / 4 ");
        assertThat(s.index).isEqualTo(2);
        assertThat(s.total).isEqualTo(4);
        assertThat(s.describe()).isEqualTo("shard 2/4");
        assertThat(ShardSplitter.parse(null)).isNull();
        assertThat(ShardSplitter.parse("")).isNull();
        assertThatThrownBy(() -> ShardSplitter.parse("0/4")).hasMessageContaining("1..4");
        assertThatThrownBy(() -> ShardSplitter.parse("5/4")).hasMessageContaining("1..4");
        assertThatThrownBy(() -> ShardSplitter.parse("1/0")).hasMessageContaining(">= 1");
        assertThatThrownBy(() -> ShardSplitter.parse("a/b")).hasMessageContaining("i/n");
        assertThatThrownBy(() -> ShardSplitter.parse("1/")).hasMessageContaining("i/n");
    }

    @Test
    void hashIsStableAcrossJvms() {
        // pinned FNV-1a values: a change here would reshuffle every CI split
        assertThat(ShardSplitter.hash("")).isEqualTo(0xcbf29ce484222325L);
        assertThat(ShardSplitter.hash("a")).isEqualTo(0xaf63dc4c8601ec8cL);
        assertThat(ShardSplitter.hash("./x\\y.sel")).isEqualTo(ShardSplitter.hash("x/y.sel"));
    }

    @Test
    void absolutePathsUnderTheTestDirAreKeyedRelative() {
        // a folder seleniumScript expands to absolute paths; two CI checkouts must still split alike
        assertThat(ShardSplitter.relativeTo("/ws/job/tests/ui/a.sel", "/ws/job/tests")).isEqualTo("ui/a.sel");
        assertThat(ShardSplitter.relativeTo("/ws/job@2/tests/ui/a.sel", "/ws/job@2/tests/")).isEqualTo("ui/a.sel");
        assertThat(ShardSplitter.relativeTo("C:\\w\\tests\\ui\\a.sel", "C:\\w\\tests")).isEqualTo("ui/a.sel");
        assertThat(ShardSplitter.relativeTo("ui/a.sel", "/ws/job/tests")).isEqualTo("ui/a.sel");
        assertThat(ShardSplitter.relativeTo("/elsewhere/a.sel", "/ws/job/tests")).isEqualTo("/elsewhere/a.sel");
        assertThat(ShardSplitter.relativeTo("/ws/job/testsX/a.sel", "/ws/job/tests")).isEqualTo("/ws/job/testsX/a.sel");
        assertThat(ShardSplitter.relativeTo("a.sel", null)).isEqualTo("a.sel");
    }

    @Test
    void workersAndPoolSize() {
        assertThat(ShardSplitter.resolveWorkers("12", 4)).isEqualTo(12);
        assertThat(ShardSplitter.resolveWorkers("50%", 8)).isEqualTo(4);
        assertThat(ShardSplitter.resolveWorkers("10%", 4)).isEqualTo(1);
        assertThatThrownBy(() -> ShardSplitter.resolveWorkers("0", 4)).hasMessageContaining(">= 1");
        assertThatThrownBy(() -> ShardSplitter.resolveWorkers("0%", 4)).hasMessageContaining(">= 1%");
        assertThatThrownBy(() -> ShardSplitter.resolveWorkers("x", 4)).hasMessageContaining("N or N%");
        // the old hard cap of 10 is gone; the pool never exceeds the script count
        assertThat(ShardSplitter.poolSize(32, 100)).isEqualTo(32);
        assertThat(ShardSplitter.poolSize(32, 5)).isEqualTo(5);
        assertThat(ShardSplitter.poolSize(0, 5)).isEqualTo(1);
    }
}
