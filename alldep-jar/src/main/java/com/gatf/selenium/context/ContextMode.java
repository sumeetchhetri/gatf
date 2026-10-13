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

import java.util.Locale;

/**
 * H3: the {@code browserContexts} config value. {@code subtest} (Δ153): a context per top-level subtest in the script's
 * own browser. {@code worker} (Δ154): each worker keeps one browser and every script runs in a fresh context of it.
 * User contexts cannot nest, so under {@code worker} the subtests of a script run inside the script's context (no
 * context per subtest). Anything else (unset, blank, unknown) is {@code off}: today's behaviour.
 */
public final class ContextMode {

    public static final String OFF = "off", SUBTEST = "subtest", WORKER = "worker";

    private ContextMode() {}

    /** The effective mode of a {@code browserContexts} value. */
    public static String of(String value) {
        String m = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return SUBTEST.equals(m) || WORKER.equals(m) ? m : OFF;
    }

    /** A fresh context per script on the worker's pooled browser. */
    public static boolean perScript(String mode) { return WORKER.equals(of(mode)); }

    /** A fresh context per top-level subtest: {@code subtest} only (under {@code worker} subtests share the script's). */
    public static boolean perSubtest(String mode) { return SUBTEST.equals(of(mode)); }
}
