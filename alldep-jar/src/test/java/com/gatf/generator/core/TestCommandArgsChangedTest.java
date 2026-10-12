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
package com.gatf.generator.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;

import org.junit.jupiter.api.Test;

/** H6 (Δ156): `gatf test --changed <git-range> [--explain] [--coverage-run dir] [--source-maps dir]`. */
class TestCommandArgsChangedTest {

    @Test
    void parsesTheChangedOptions() {
        TestCommandArgs a = TestCommandArgs.parse(new String[] {"cfg.xml", "--changed", "main...HEAD", "--explain", "--coverage-run=out/1", "--source-maps", "dist"}, new File("."));
        assertEquals("main...HEAD", a.changed);
        assertTrue(a.explain);
        assertEquals("out/1", a.coverageRun);
        assertEquals("dist", a.sourceMaps);
        TestCommandArgs b = TestCommandArgs.parse(new String[] {"cfg.xml"}, new File("."));
        assertNull(b.changed);
        assertFalse(b.explain);
        assertTrue(TestCommandArgs.USAGE.contains("[--changed <git-range> [--explain]"));
    }

    @Test
    void rejectsOptionsThatNeedChangedAndChangedWithWatch() {
        assertThrows(IllegalArgumentException.class, () -> TestCommandArgs.parse(new String[] {"cfg.xml", "--explain"}, new File(".")));
        assertThrows(IllegalArgumentException.class, () -> TestCommandArgs.parse(new String[] {"cfg.xml", "--coverage-run", "x"}, new File(".")));
        assertThrows(IllegalArgumentException.class, () -> TestCommandArgs.parse(new String[] {"cfg.xml", "--changed", "HEAD", "--watch"}, new File(".")));
        assertThrows(IllegalArgumentException.class, () -> TestCommandArgs.parse(new String[] {"cfg.xml", "--changed"}, new File(".")));
    }
}
