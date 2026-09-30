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

/**
 * M2 — web-first assertions. A value assertion ({@code ?? css@h1 text ="Welcome"}) whose element is found but whose
 * condition does not hold yet is retried until {@code assertTimeout} (default 5 s) instead of failing at once, so
 * late-rendered text passes without an explicit wait. The budget starts at the first mismatch: the element search
 * itself is still bounded by the statement's {@code ??N} / global timeout. Pure: no Selenium types.
 */
public final class WebFirst {

    public static final long DEFAULT_ASSERT_TIMEOUT_MS = 5000L;
    /** Poll interval between retries (capped by the script's timeout granularity). */
    public static final long POLL_MS = 200L;

    private WebFirst() {}

    /** An element was found but the asserted condition was false. Same message as the legacy failure. */
    public static final class Mismatch extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final String condition;

        public Mismatch(String message, String condition) {
            super(message);
            this.condition = condition;
        }

        /** The condition that did not hold, e.g. {@code text = "Welcome"}; may be null. */
        public String getCondition() { return condition; }

        /** The final failure after retrying: names the condition and how long it was retried. */
        public Mismatch after(long waitedMs) {
            String m = getMessage() == null ? "Assertion failed" : getMessage().trim();
            Mismatch e = new Mismatch(m + (condition == null ? "" : ": " + condition) + " still false after " + waitedMs + " ms", condition);
            e.setStackTrace(getStackTrace());
            return e;
        }
    }

    /** {@code subselector oper "value"} for the failure message, e.g. {@code text = "Welcome"}. */
    public static String describe(String subselector, String oper, String value, String[] values) {
        StringBuilder b = new StringBuilder(subselector == null ? "value" : subselector);
        if (oper != null) b.append(' ').append(oper);
        if (value != null) b.append(" \"").append(value).append('"');
        else if (values != null && values.length > 0) b.append(" ").append(java.util.Arrays.toString(values));
        return b.toString();
    }

    /** One assertion's retry budget; the clock starts at the first {@link #retry()} (the first mismatch). */
    public static final class Budget {
        private final long timeoutMs;
        private final Actionability.Clock clock;
        private long start = -1;

        public Budget(long timeoutMs, Actionability.Clock clock) {
            this.timeoutMs = timeoutMs > 0 ? timeoutMs : DEFAULT_ASSERT_TIMEOUT_MS;
            this.clock = clock;
        }

        /** Called on each mismatch: true while the budget allows another attempt. */
        public boolean retry() {
            long now = clock.now();
            if (start < 0) start = now;
            return now - start < timeoutMs;
        }

        public long elapsed() { return start < 0 ? 0 : clock.now() - start; }

        public long timeoutMs() { return timeoutMs; }
    }
}
