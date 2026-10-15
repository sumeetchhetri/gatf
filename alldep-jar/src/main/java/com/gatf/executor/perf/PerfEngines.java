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
package com.gatf.executor.perf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.gatf.executor.core.AcceptanceTestContext;
import com.gatf.executor.core.TestCase;
import com.gatf.executor.ext.AutocannonTestCaseExecutor;
import com.gatf.executor.ext.VegetaTestCaseExecutor;
import com.gatf.executor.ext.WrkTestCaseExecutor;
import com.gatf.executor.report.TestCaseReport;
import com.gatf.selenium.plugins.sdk.PluginRegistry;

/**
 * M13: the built-in {@link PerfEngine}s (in the order the Q1 error message always listed them, wperfk added) and the
 * run-time lookup over the plugins/ class loader (see {@link PerfEngineRegistry}). Replaces the old
 * {@code if wrk / else if vegeta / else autocannon} dispatch in TestCaseExecutorUtil.
 */
public final class PerfEngines {

    private PerfEngines() {}

    private static final class Builtin implements PerfEngine {
        final String name, description;
        final Exec exec;

        Builtin(String name, String description, Exec exec) {
            this.name = name;
            this.description = description;
            this.exec = exec;
        }

        public String name() { return name; }
        public String description() { return description; }

        public void execute(AcceptanceTestContext c, TestCase tc, TestCaseReport tcr, boolean single) throws Exception {
            exec.run(c, tc, tcr, single);
        }
    }

    private interface Exec {
        void run(AcceptanceTestContext c, TestCase tc, TestCaseReport tcr, boolean single) throws Exception;
    }

    private static final Map<String, PerfEngine> BUILTIN_M = new LinkedHashMap<String, PerfEngine>();
    public static final Map<String, PerfEngine> BUILTIN = Collections.unmodifiableMap(BUILTIN_M);
    static {
        add(new Builtin("vegeta", "open-loop HTTP attack (auto-downloaded)", VegetaTestCaseExecutor::execute));
        add(new Builtin("autocannon", "Node.js HTTP benchmarker (private Node.js if needed)", AutocannonTestCaseExecutor::execute));
        add(new Builtin("wrk", "closed-loop max throughput, Lua scripts (host / Docker, else vegeta)",
                (c, tc, tcr, s) -> WrkTestCaseExecutor.execute(c, tc, tcr, s, "wrk")));
        add(new Builtin("wrk2", "constant rate (-R), corrected latency (host / Docker, else vegeta)",
                (c, tc, tcr, s) -> WrkTestCaseExecutor.execute(c, tc, tcr, s, "wrk2")));
        add(new Builtin("wperfk", "the default: wrk/wrk2-compatible, any OS, Lua scripts (auto-downloaded; else vegeta)",
                (c, tc, tcr, s) -> WrkTestCaseExecutor.execute(c, tc, tcr, s, "wperfk")));
    }

    private static void add(PerfEngine e) {
        BUILTIN_M.put(e.name(), e);
    }

    private static ClassLoader cachedLoader;
    private static Map<String, PerfEngine> cached;
    private static List<String> cachedWarnings = new ArrayList<String>();

    /** The engines for the current plugins/ loader, cached until the loader changes (a jar added / replaced). */
    public static synchronized Map<String, PerfEngine> current() {
        ClassLoader l = PluginRegistry.classLoader();
        if (cached == null || l != cachedLoader) {
            List<String> w = new ArrayList<String>();
            cached = PerfEngineRegistry.all(BUILTIN, l, w);
            cachedLoader = l;
            cachedWarnings = w;
            for (String s : w) System.err.println("[gatf] plugins/: " + s);
        }
        return new LinkedHashMap<String, PerfEngine>(cached);
    }

    public static synchronized List<String> warnings() {
        return new ArrayList<String>(cachedWarnings);
    }

    /** Re-scans {@code plugins/} for the run (API-only runs never compile a .sel), then resolves the type. */
    public static PerfEngine forType(AcceptanceTestContext context, String type) {
        if (context != null && !BUILTIN.containsKey(type)) {
            PluginRegistry.load(context.getResourceFile("plugins"), PerfEngines.class.getClassLoader());
        }
        return PerfEngineRegistry.resolve(type, BUILTIN.containsKey(type) ? BUILTIN : current());
    }
}
