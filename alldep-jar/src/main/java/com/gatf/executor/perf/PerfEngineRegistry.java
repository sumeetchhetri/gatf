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

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

/**
 * M13: pure {@link PerfEngine} lookup: the built-ins, then the engines {@link ServiceLoader} finds in a class loader
 * (the plugins/ jars). A built-in name cannot be replaced; the first definition of any name wins. Lookup is exact and
 * case-sensitive, as {@code perfConfig.type} always was.
 */
public final class PerfEngineRegistry {

    private PerfEngineRegistry() {}

    /** {@code builtins} followed by the engines {@code loader} provides (null = none). {@code warnings} may be null. */
    public static Map<String, PerfEngine> all(Map<String, PerfEngine> builtins, ClassLoader loader, List<String> warnings) {
        Map<String, PerfEngine> m = new LinkedHashMap<String, PerfEngine>(builtins);
        if (loader == null) return m;
        Iterator<PerfEngine> it = ServiceLoader.load(PerfEngine.class, loader).iterator();
        while (true) {
            PerfEngine e;
            String n;
            try {
                if (!it.hasNext()) break;
                e = it.next();
                n = e.name();
            } catch (ServiceConfigurationError | RuntimeException | LinkageError x) {
                if (warnings != null) warnings.add("perf engine not loaded: " + x.getMessage());
                continue;
            }
            if (n == null || !n.matches("[a-z][a-z0-9_-]*") || "none".equals(n)) {
                if (warnings != null) warnings.add(e.getClass().getName() + ": invalid perf engine name '" + n + "'");
            } else if (m.containsKey(n)) {
                if (warnings != null) warnings.add("perf engine '" + n + "' (" + e.getClass().getName() + ") is already defined; keeping "
                        + (builtins.containsKey(n) ? "the built-in" : m.get(n).getClass().getName()));
            } else {
                m.put(n, e);
            }
        }
        return m;
    }

    /** The engine for a {@code perfConfig.type}; an unknown type throws with the list of valid names. */
    public static PerfEngine resolve(String type, Map<String, PerfEngine> engines) {
        PerfEngine e = type == null ? null : engines.get(type);
        if (e != null) return e;
        throw new IllegalArgumentException("Unknown perfConfig.type '" + type + "' - expected one of: "
                + String.join(", ", engines.keySet()) + " (or none)");
    }
}
