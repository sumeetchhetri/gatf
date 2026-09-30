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

import java.util.Arrays;
import java.util.Objects;

/**
 * M16: GATF's own assertions, replacing JUnit 4's {@code Assert} in the runtime and in generated seleasy
 * code, so {@code junit:junit} is no longer a runtime dependency. The method names, argument order (message first) and
 * failure type ({@link AssertionError}, which generated {@code catch(AssertionError)} blocks and the result
 * classification rely on) are JUnit 4's; messages follow JUnit's {@code expected:<..> but was:<..>} form.
 * <p>
 * {@link Assert} is an alias so inline Java in scripts ({@code #j…}, {@code <<<} blocks) that calls
 * {@code Assert.assertTrue(..)} keeps compiling: generated classes import it in place of JUnit's.
 */
public class GatfAssert {

    GatfAssert() {}

    /** Drop-in name for scripts written against JUnit 4's Assert. */
    public static final class Assert extends GatfAssert {
        private Assert() {}
    }

    /** A failed assertion; {@link #getExpected()} / {@link #getActual()} are set for equality checks. */
    public static class GatfAssertionError extends AssertionError {
        private static final long serialVersionUID = 1L;
        private final String expected, actual;

        /** No message (JUnit's {@code fail()}): {@link #getMessage()} is null. */
        public GatfAssertionError() {
            super();
            this.expected = null;
            this.actual = null;
        }

        public GatfAssertionError(String message) {
            this(message, null, null);
        }

        public GatfAssertionError(String message, String expected, String actual) {
            super(message);
            this.expected = expected;
            this.actual = actual;
        }

        public String getExpected() { return expected; }
        public String getActual() { return actual; }
    }

    public static void fail(String message) {
        throw message == null ? new GatfAssertionError() : new GatfAssertionError(message);
    }

    public static void fail() {
        fail(null);
    }

    public static void assertTrue(String message, boolean condition) {
        if (!condition) fail(message);
    }

    public static void assertTrue(boolean condition) {
        assertTrue(null, condition);
    }

    public static void assertFalse(String message, boolean condition) {
        assertTrue(message, !condition);
    }

    public static void assertFalse(boolean condition) {
        assertFalse(null, condition);
    }

    public static void assertNotNull(String message, Object object) {
        assertTrue(message, object != null);
    }

    public static void assertNotNull(Object object) {
        assertNotNull(null, object);
    }

    public static void assertNull(String message, Object object) {
        if (object == null) return;
        // JUnit prefixes any non-null message here, even an empty one
        throw new GatfAssertionError((message == null ? "" : message + " ") + "expected null, but was:<" + object + ">", "null", String.valueOf(object));
    }

    public static void assertNull(Object object) {
        assertNull(null, object);
    }

    public static void assertEquals(String message, Object expected, Object actual) {
        if (equalsRegardingNull(expected, actual)) return;
        throw notEqual(message, expected, actual);
    }

    public static void assertEquals(Object expected, Object actual) {
        assertEquals(null, expected, actual);
    }

    public static void assertEquals(String message, long expected, long actual) {
        if (expected != actual) throw notEqual(message, Long.valueOf(expected), Long.valueOf(actual));
    }

    public static void assertEquals(long expected, long actual) {
        assertEquals(null, expected, actual);
    }

    public static void assertEquals(String message, double expected, double actual, double delta) {
        if (Double.compare(expected, actual) == 0 || Math.abs(expected - actual) <= delta) return;
        throw notEqual(message, Double.valueOf(expected), Double.valueOf(actual));
    }

    public static void assertEquals(double expected, double actual, double delta) {
        assertEquals(null, expected, actual, delta);
    }

    public static void assertNotEquals(String message, Object unexpected, Object actual) {
        if (!equalsRegardingNull(unexpected, actual)) return;
        // JUnit: "<message>. Actual: x", or "Values should be different. Actual: x" with no message
        String m = message != null ? message + ". Actual: " + actual : "Values should be different. Actual: " + actual;
        throw new GatfAssertionError(m, null, String.valueOf(actual));
    }

    public static void assertNotEquals(Object unexpected, Object actual) {
        assertNotEquals(null, unexpected, actual);
    }

    public static void assertArrayEquals(String message, Object[] expected, Object[] actual) {
        arrays(message, expected, actual, 0);
    }

    public static void assertArrayEquals(Object[] expected, Object[] actual) {
        assertArrayEquals(null, expected, actual);
    }

    public static void assertArrayEquals(String message, int[] expected, int[] actual) { arrays(message, expected, actual, 0); }
    public static void assertArrayEquals(int[] expected, int[] actual) { arrays(null, expected, actual, 0); }
    public static void assertArrayEquals(String message, long[] expected, long[] actual) { arrays(message, expected, actual, 0); }
    public static void assertArrayEquals(long[] expected, long[] actual) { arrays(null, expected, actual, 0); }
    public static void assertArrayEquals(String message, short[] expected, short[] actual) { arrays(message, expected, actual, 0); }
    public static void assertArrayEquals(short[] expected, short[] actual) { arrays(null, expected, actual, 0); }
    public static void assertArrayEquals(String message, byte[] expected, byte[] actual) { arrays(message, expected, actual, 0); }
    public static void assertArrayEquals(byte[] expected, byte[] actual) { arrays(null, expected, actual, 0); }
    public static void assertArrayEquals(String message, char[] expected, char[] actual) { arrays(message, expected, actual, 0); }
    public static void assertArrayEquals(char[] expected, char[] actual) { arrays(null, expected, actual, 0); }
    public static void assertArrayEquals(String message, boolean[] expected, boolean[] actual) { arrays(message, expected, actual, 0); }
    public static void assertArrayEquals(boolean[] expected, boolean[] actual) { arrays(null, expected, actual, 0); }
    public static void assertArrayEquals(String message, double[] expected, double[] actual, double delta) { arrays(message, expected, actual, delta); }
    public static void assertArrayEquals(double[] expected, double[] actual, double delta) { arrays(null, expected, actual, delta); }
    public static void assertArrayEquals(String message, float[] expected, float[] actual, float delta) { arrays(message, expected, actual, delta); }
    public static void assertArrayEquals(float[] expected, float[] actual, float delta) { arrays(null, expected, actual, delta); }

    /** A failure inside nested arrays: the element path is collected on the way out ({@code [0][1]}). */
    private static final class ArrayFailure extends RuntimeException {
        private static final long serialVersionUID = 1L;
        final StringBuilder path = new StringBuilder();
        final String lens, detail, expected, actual;
        ArrayFailure(int index, String lens, String detail, String expected, String actual) {
            super(null, null, false, false);
            path.append('[').append(index).append(']');
            this.lens = lens; this.detail = detail; this.expected = expected; this.actual = actual;
        }
    }

    /** JUnit's array comparison: element by element, recursing into nested arrays; {@code delta} for float/double. */
    private static void arrays(String message, Object expected, Object actual, double delta) {
        if (expected == actual) return;
        String header = message == null ? "" : message + ": ";
        if (expected == null) fail(header + "expected array was null");
        if (actual == null) fail(header + "actual array was null");
        try {
            compareArrays(expected, actual, delta);
        } catch (ArrayFailure f) {
            throw new GatfAssertionError(header + f.lens + "arrays first differed at element " + f.path + "; " + f.detail, f.expected, f.actual);
        }
    }

    private static void compareArrays(Object expected, Object actual, double delta) {
        int el = java.lang.reflect.Array.getLength(expected), al = java.lang.reflect.Array.getLength(actual);
        String lens = el != al ? "array lengths differed, expected.length=" + el + " actual.length=" + al + "; " : "";
        int n = Math.min(el, al);
        for (int i = 0; i < n; i++) {
            Object e = java.lang.reflect.Array.get(expected, i), a = java.lang.reflect.Array.get(actual, i);
            if (e != null && a != null && e.getClass().isArray() && a.getClass().isArray()) {
                try {
                    compareArrays(e, a, delta);
                } catch (ArrayFailure f) {
                    f.path.insert(0, "[" + i + "]");
                    throw f;
                }
                continue;
            }
            boolean same;
            if (e instanceof Double || e instanceof Float) {
                double x = ((Number) e).doubleValue(), y = ((Number) a).doubleValue();
                same = Double.compare(x, y) == 0 || Math.abs(x - y) <= delta;
            } else {
                same = Objects.equals(e, a);
            }
            if (!same) {
                GatfAssertionError m = notEqual(null, e, a);
                throw new ArrayFailure(i, lens, m.getMessage(), String.valueOf(e), String.valueOf(a));
            }
        }
        if (el != al) {
            Object e = n < el ? java.lang.reflect.Array.get(expected, n) : "end of array";
            Object a = n < al ? java.lang.reflect.Array.get(actual, n) : "end of array";
            throw new ArrayFailure(n, lens, "expected:<" + e + "> but was:<" + a + ">", String.valueOf(e), String.valueOf(a));
        }
    }

    public static void assertEquals(String message, float expected, float actual, float delta) {
        if (Float.compare(expected, actual) == 0 || Math.abs(expected - actual) <= delta) return;
        throw notEqual(message, Float.valueOf(expected), Float.valueOf(actual));
    }

    public static void assertEquals(float expected, float actual, float delta) {
        assertEquals(null, expected, actual, delta);
    }

    /** Code that is expected to throw (JUnit 4.13's {@code assertThrows}). */
    public interface ThrowingRunnable {
        void run() throws Throwable;
    }

    public static <T extends Throwable> T assertThrows(String message, Class<T> expectedThrowable, ThrowingRunnable runnable) {
        try {
            runnable.run();
        } catch (Throwable actual) {
            if (expectedThrowable.isInstance(actual)) return expectedThrowable.cast(actual);
            String m = prefix(message) + "unexpected exception type thrown; expected:<" + expectedThrowable.getName()
                    + "> but was:<" + actual.getClass().getName() + ">";
            GatfAssertionError err = new GatfAssertionError(m, expectedThrowable.getName(), actual.getClass().getName());
            err.initCause(actual);
            throw err;
        }
        throw new GatfAssertionError(prefix(message) + "expected " + expectedThrowable.getName() + " to be thrown, but nothing was thrown");
    }

    public static <T extends Throwable> T assertThrows(Class<T> expectedThrowable, ThrowingRunnable runnable) {
        return assertThrows(null, expectedThrowable, runnable);
    }

    public static void assertSame(String message, Object expected, Object actual) {
        if (expected == actual) return;
        throw new GatfAssertionError(prefix(message) + "expected same:<" + expected + "> was not:<" + actual + ">",
                String.valueOf(expected), String.valueOf(actual));
    }

    public static void assertSame(Object expected, Object actual) {
        assertSame(null, expected, actual);
    }

    public static void assertNotSame(String message, Object unexpected, Object actual) {
        if (unexpected != actual) return;
        fail(prefix(message) + "expected not same");
    }

    public static void assertNotSame(Object unexpected, Object actual) {
        assertNotSame(null, unexpected, actual);
    }

    private static boolean equalsRegardingNull(Object expected, Object actual) {
        return Objects.equals(expected, actual);
    }

    private static String prefix(String message) {
        return message == null || message.isEmpty() ? "" : message + " ";
    }

    /** JUnit's format: types are shown when the two values print the same; two strings show only where they differ. */
    static GatfAssertionError notEqual(String message, Object expected, Object actual) {
        String e = String.valueOf(expected), a = String.valueOf(actual);
        if (expected instanceof String && actual instanceof String) {
            String[] c = compact((String) expected, (String) actual, CONTEXT);
            return new GatfAssertionError(prefix(message) + "expected:<" + c[0] + "> but was:<" + c[1] + ">", e, a);
        }
        String text;
        if (e.equals(a)) {
            text = prefix(message) + "expected: " + typed(expected) + " but was: " + typed(actual);
        } else {
            text = prefix(message) + "expected:<" + e + "> but was:<" + a + ">";
        }
        return new GatfAssertionError(text, e, a);
    }

    static final int CONTEXT = 20;

    /**
     * The differing middle of two strings in brackets, with at most {@code ctx} characters of the shared prefix and
     * suffix around it ("..." where more was cut): {@code "abcXdef"} vs {@code "abcYdef"} → {@code abc[X]def} /
     * {@code abc[Y]def}.
     */
    static String[] compact(String e, String a, int ctx) {
        int max = Math.min(e.length(), a.length());
        int pre = 0;
        while (pre < max && e.charAt(pre) == a.charAt(pre)) pre++;
        int suf = 0;
        while (suf < max - pre && e.charAt(e.length() - 1 - suf) == a.charAt(a.length() - 1 - suf)) suf++;
        String head = (pre > ctx ? "..." : "") + e.substring(Math.max(0, pre - ctx), pre);
        String tail = e.substring(e.length() - suf, Math.min(e.length(), e.length() - suf + ctx)) + (suf > ctx ? "..." : "");
        return new String[] {
            head + "[" + e.substring(pre, e.length() - suf) + "]" + tail,
            head + "[" + a.substring(pre, a.length() - suf) + "]" + tail };
    }

    private static String typed(Object v) {
        return v == null ? "null" : v.getClass().getName() + "<" + v + ">";
    }
}
