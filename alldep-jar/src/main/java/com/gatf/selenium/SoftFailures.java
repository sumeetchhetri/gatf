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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * M2 — soft assertions ({@code ??~}). A failing soft assertion is recorded and the script continues; when the
 * script (or subtest) finishes, its result fails with every recorded failure listed. Pure: no Selenium types.
 */
public final class SoftFailures {

    private final List<String> items = new ArrayList<String>();

    /** Records one failure: {@code where} is {@code file:line}, {@code message} the assertion's failure text. */
    public synchronized void add(String where, String message) {
        String m = message == null ? "assertion failed" : message.trim();
        int nl = m.indexOf('\n');
        if (nl >= 0) m = m.substring(0, nl).trim();
        items.add((where == null || where.isEmpty() ? "" : where + ": ") + m);
    }

    public synchronized int size() {
        return items.size();
    }

    /** The recorded failures, cleared, so each script / subtest result gets only its own. */
    public synchronized List<String> drain() {
        if (items.isEmpty()) return Collections.emptyList();
        List<String> out = new ArrayList<String>(items);
        items.clear();
        return out;
    }

    /** "3 soft assertion(s) failed: a.sel:4: …" with every failure on its own line after the first. */
    public static String message(List<String> failures) {
        StringBuilder b = new StringBuilder().append(failures.size()).append(" soft assertion(s) failed");
        for (int i = 0; i < failures.size(); i++) {
            b.append(i == 0 ? ": " : "\n  ").append(failures.get(i));
        }
        return b.toString();
    }

    /** The end-of-script failure carrying every soft failure. */
    public static final class SoftAssertionError extends AssertionError {
        private static final long serialVersionUID = 1L;
        private final ArrayList<String> failures;

        public SoftAssertionError(List<String> failures) {
            super(message(failures));
            this.failures = new ArrayList<String>(failures);
        }

        public List<String> getFailures() { return Collections.unmodifiableList(failures); }
    }
}
