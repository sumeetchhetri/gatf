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
package com.gatf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** M16: GatfAssert keeps JUnit 4's behaviour and messages (it replaces org.junit.Assert at runtime). */
class GatfAssertTest {

    private static String msg(Runnable r) {
        try {
            r.run();
        } catch (AssertionError e) {
            return String.valueOf(e.getMessage());
        }
        return "ok";
    }

    @Test
    void conditionsAndNulls() {
        assertEquals("ok", msg(() -> GatfAssert.assertTrue("m", true)));
        assertEquals("m", msg(() -> GatfAssert.assertTrue("m", false)));
        assertEquals("null", msg(() -> GatfAssert.assertTrue(false)));          // no message, like JUnit
        assertEquals("m", msg(() -> GatfAssert.assertFalse("m", true)));
        assertEquals("m", msg(() -> GatfAssert.assertNotNull("m", null)));
        assertEquals("ok", msg(() -> GatfAssert.assertNotNull("m", Boolean.FALSE)));   // a boxed false is not null
        assertEquals("m expected null, but was:<x>", msg(() -> GatfAssert.assertNull("m", "x")));
        assertEquals(" expected null, but was:<x>", msg(() -> GatfAssert.assertNull("", "x")));
        assertEquals("", msg(() -> GatfAssert.fail("")));
    }

    @Test
    void equalityMessagesCarryExpectedAndActual() {
        assertEquals("m expected:<3> but was:<4>", msg(() -> GatfAssert.assertEquals("m", 3, 4)));
        assertEquals("expected:<true> but was:<false>", msg(() -> GatfAssert.assertEquals(true, false)));
        assertEquals("expected: java.lang.Integer<1> but was: java.lang.Long<1>", msg(() -> GatfAssert.assertEquals(Integer.valueOf(1), Long.valueOf(1))));
        assertEquals("ok", msg(() -> GatfAssert.assertEquals(null, null)));
        assertEquals("expected:<null> but was:<a>", msg(() -> GatfAssert.assertEquals(null, "a")));
        GatfAssert.GatfAssertionError e = assertThrows(GatfAssert.GatfAssertionError.class, () -> GatfAssert.assertEquals("status", "200", "500"));
        assertEquals("status expected:<[2]00> but was:<[5]00>", e.getMessage());
        assertEquals("200", e.getExpected());
        assertEquals("500", e.getActual());
        assertNull(new GatfAssert.GatfAssertionError().getMessage());
    }

    @Test
    void longStringsShowOnlyTheDifferenceWithContext() {
        String p = "0123456789abcdefghijKLMNOP";
        assertEquals("expected:<...6789abcdefghijKLMNOP[x]> but was:<...6789abcdefghijKLMNOP[y]>", msg(() -> GatfAssert.assertEquals(p + "x", p + "y")));
        assertEquals("expected:<[]> but was:<[a]>", msg(() -> GatfAssert.assertEquals("", "a")));
        assertEquals("expected:<a[b]c> but was:<a[]c>", msg(() -> GatfAssert.assertEquals("abc", "ac")));
    }

    @Test
    void failuresAreAssertionErrorsSoGeneratedCatchBlocksStillWork() {
        boolean caught = false;
        try {
            GatfAssert.assertTrue("Element not found by selector id@'x' at line number 3 ", false);
        } catch (AssertionError e) {   // what IfCommand's generated catch(AssertionError) relies on
            caught = true;
        }
        assertTrue(caught);
    }

    @Test
    void arraysAreComparedElementByElementLikeJunit() {
        assertEquals("m: arrays first differed at element [0][1]; expected:<2> but was:<3>",
                msg(() -> GatfAssert.assertArrayEquals("m", new Object[] { new int[] { 1, 2 } }, new Object[] { new int[] { 1, 3 } })));
        assertEquals("array lengths differed, expected.length=1 actual.length=2; arrays first differed at element [1]; expected:<end of array> but was:<2>",
                msg(() -> GatfAssert.assertArrayEquals(new int[] { 1 }, new int[] { 1, 2 })));
        assertEquals("ok", msg(() -> GatfAssert.assertArrayEquals(new double[] { 1.0 }, new double[] { 1.05 }, 0.1)));
        assertEquals("ne. Actual: a", msg(() -> GatfAssert.assertNotEquals("ne", "a", "a")));
        IllegalStateException e = GatfAssert.assertThrows(IllegalStateException.class, () -> { throw new IllegalStateException("x"); });
        assertEquals("x", e.getMessage());
    }

    @Test
    void assertAliasServesInlineScriptCode() {
        assertEquals("x", msg(() -> GatfAssert.Assert.assertTrue("x", false)));
        assertEquals("ok", msg(() -> GatfAssert.Assert.assertEquals(1L, 1L)));
        assertEquals("arrays first differed at element [0]; expected:<[a]> but was:<[b]>",
                msg(() -> GatfAssert.Assert.assertArrayEquals(new Object[] { "a" }, new Object[] { "b" })));
    }
}
