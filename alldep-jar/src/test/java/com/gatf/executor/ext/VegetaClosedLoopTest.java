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
package com.gatf.executor.ext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.List;

import org.junit.jupiter.api.Test;

/** M17: perfConfig extras closed=1 turns native vegeta's connections into N concurrent users (opt-in). */
class VegetaClosedLoopTest {

    @Test
    void closedLoopOnlyWhenAskedAndNoRate() {
        assertEquals(10, VegetaRunner.closedLoopWorkers("closed=1", 0, 10));
        assertEquals(25, VegetaRunner.closedLoopWorkers("plot=1,closed=1", 0, 25));
        assertEquals(10, VegetaRunner.closedLoopWorkers("closed=1", 0, 0));      // no connections: 10
        assertEquals(0, VegetaRunner.closedLoopWorkers("closed=1", 50, 10));     // a rate wins: open loop at that rate
        assertEquals(0, VegetaRunner.closedLoopWorkers(null, 0, 10));            // existing configs unchanged
        assertEquals(0, VegetaRunner.closedLoopWorkers("plot=1", 0, 10));
        assertEquals(0, VegetaRunner.closedLoopWorkers("notclosed=10", 0, 10));
    }

    @Test
    void closedLoopBecomesRateZeroMaxWorkers() {
        List<String> a = VegetaRunner.attackArgs(new File("t"), new File("o"), 10, 0, 0, VegetaRunner.closedLoopWorkers("closed=1", 0, 10), 30, 0);
        String s = String.join(" ", a);
        assertTrue(s.contains("-rate 0 -max-workers 10"), s);
    }
}
