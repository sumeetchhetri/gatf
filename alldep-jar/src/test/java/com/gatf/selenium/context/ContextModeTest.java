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
package com.gatf.selenium.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** H3 (Δ154): the browserContexts values and how worker and subtest relate. */
class ContextModeTest {

    @Test
    void values() {
        assertEquals("off", ContextMode.of(null));
        assertEquals("off", ContextMode.of(""));
        assertEquals("off", ContextMode.of("contexts"));
        assertEquals("subtest", ContextMode.of(" SubTest "));
        assertEquals("worker", ContextMode.of("WORKER"));
    }

    @Test
    void workerIsAContextPerScriptAndNoContextPerSubtest() {
        assertTrue(ContextMode.perScript("worker"));
        assertFalse(ContextMode.perSubtest("worker"));
        assertTrue(ContextMode.perSubtest("subtest"));
        assertFalse(ContextMode.perScript("subtest"));
        assertFalse(ContextMode.perScript(null) || ContextMode.perSubtest(null), "default: today's behaviour");
    }
}
