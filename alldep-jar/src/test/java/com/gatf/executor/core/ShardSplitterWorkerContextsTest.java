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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * H3 (Δ154): worker contexts change only how a worker gets its browser; the shard split and the pool size it runs
 * on are untouched. Shim-runnable (Assertions only), alongside the AssertJ ShardSplitterTest.
 */
class ShardSplitterWorkerContextsTest {

    static List<String> suite(int n) {
        List<String> l = new ArrayList<String>();
        for (int i = 0; i < n; i++) l.add("ui/flow-" + i + ".sel");
        return l;
    }

    @Test
    void theSplitIsDeterministicAndOrderFree() {
        List<String> files = suite(24);
        List<String> shuffled = new ArrayList<String>(files);
        Collections.shuffle(shuffled, new java.util.Random(154));
        assertEquals(ShardSplitter.assign(files, 4), ShardSplitter.assign(files, 4));
        assertEquals(ShardSplitter.assign(files, 4), ShardSplitter.assign(shuffled, 4));
        Set<String> seen = new HashSet<String>();
        int total = 0;
        for (int i = 1; i <= 4; i++) {
            List<String> mine = new ShardSplitter(i, 4).select(files, p -> p);
            assertEquals(mine, new ShardSplitter(i, 4).select(files, p -> p), "same shard, same files, every time");
            for (String f : mine) assertTrue(seen.add(f), "a file in two shards: " + f);
            total += mine.size();
        }
        assertEquals(24, total, "every file in exactly one shard");
    }

    @Test
    void thePoolIsSizedAsBefore() {
        assertEquals(4, ShardSplitter.poolSize(4, 24));
        assertEquals(3, ShardSplitter.poolSize(8, 3), "never more workers than scripts");
    }
}
