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
package com.gatf.ai;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

/**
 * H1 (Δ149): built-in providers plus any found through {@link ServiceLoader} on {@code loader} (the plugin class
 * loader). As with the perf-engine SPI: names must be {@code [a-z][a-z0-9_-]*}, built-ins cannot be replaced, the
 * first definition wins, and a broken plugin becomes a warning, never a failed run.
 */
public final class AiProviders {

    private AiProviders() {}

    public static Map<String, AiProvider> all(ClassLoader loader, List<String> warnings) {
        Map<String, AiProvider> out = new LinkedHashMap<String, AiProvider>();
        for (AiProvider p : BuiltinProviders.all()) out.put(p.name(), p);
        if (loader == null) return Collections.unmodifiableMap(out);
        try {
            Iterator<AiProvider> it = ServiceLoader.load(AiProvider.class, loader).iterator();
            int errors = 0;
            while (errors < 32) {
                AiProvider p;
                try {
                    if (!it.hasNext()) break;
                    p = it.next();
                } catch (ServiceConfigurationError | RuntimeException | LinkageError e) {
                    if (warnings != null) warnings.add("ai provider skipped: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                    errors++;
                    continue;
                }
                String n;
                try { n = p.name(); } catch (RuntimeException | LinkageError e) { n = null; }
                if (n == null || !n.matches("[a-z][a-z0-9_-]*")) {
                    if (warnings != null) warnings.add("ai provider skipped: invalid name from " + p.getClass().getName());
                    continue;
                }
                if (out.containsKey(n)) {
                    if (warnings != null) warnings.add("ai provider '" + n + "' from " + p.getClass().getName() + " ignored: name already taken");
                    continue;
                }
                out.put(n, p);
            }
        } catch (ServiceConfigurationError | RuntimeException | LinkageError e) {
            if (warnings != null) warnings.add("ai provider discovery failed: " + e.getMessage());
        }
        return Collections.unmodifiableMap(out);
    }
}
