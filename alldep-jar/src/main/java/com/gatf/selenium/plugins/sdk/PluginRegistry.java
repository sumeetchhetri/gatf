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
package com.gatf.selenium.plugins.sdk;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;

/**
 * M13: loads {@link GatfStepPlugin}s from the project's {@code plugins/*.jar} in a child class loader (parent = GATF's
 * own), through {@link ServiceLoader}. The same loader also resolves {@code plugins.txt} classes, so a legacy
 * static-method plugin can ship in a jar too. The folder is re-scanned on every load: a new or changed jar is picked up
 * by the next script compile / run with no rebuild or restart. No Selenium dependency.
 */
public final class PluginRegistry {

    private static ClassLoader loader = PluginRegistry.class.getClassLoader();
    private static URLClassLoader own;
    private static String loadedSignature = "";
    private static boolean loadedOnce;
    private static final Map<String, GatfStepPlugin> plugins = new LinkedHashMap<String, GatfStepPlugin>();
    private static final Map<String, String> sources = new LinkedHashMap<String, String>();
    private static final List<String> warnings = new ArrayList<String>();
    private static final Set<GatfStepPlugin> inited = Collections.newSetFromMap(new IdentityHashMap<GatfStepPlugin, Boolean>());

    private PluginRegistry() {}

    /** The loader for plugin classes: the plugins/ jars over GATF's own classes. */
    public static synchronized ClassLoader classLoader() {
        return loader;
    }

    /** Warnings of the last load (bad jars, invalid names, duplicates). */
    public static synchronized List<String> warnings() {
        return new ArrayList<String>(warnings);
    }

    static File[] jars(File dir) {
        File[] js = dir == null ? null : dir.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".jar"));
        if (js == null) return new File[0];
        Arrays.sort(js);
        return js;
    }

    /** Names + sizes + modification times of the jars: a change means reload. */
    static String signature(File dir) {
        StringBuilder b = new StringBuilder(dir == null ? "" : dir.getAbsolutePath());
        for (File j : jars(dir)) b.append('|').append(j.getName()).append(':').append(j.length()).append(':').append(j.lastModified());
        return b.toString();
    }

    /**
     * (Re)loads the plugins in {@code dir} (missing = none) when its jars changed since the last load. Returns true when
     * it reloaded. {@code parent} is GATF's class loader.
     */
    public static synchronized boolean load(File dir, ClassLoader parent) {
        String sig = signature(dir);
        if (loadedOnce && sig.equals(loadedSignature)) return false;
        loadedOnce = true;
        loadedSignature = sig;
        warnings.clear();
        plugins.clear();
        sources.clear();
        inited.clear();
        if (own != null) {
            try { own.close(); } catch (IOException e) { /* best effort */ }
            own = null;
        }
        File[] js = jars(dir);
        ClassLoader base = parent != null ? parent : PluginRegistry.class.getClassLoader();
        if (js.length == 0) {
            loader = base;
        } else {
            List<URL> urls = new ArrayList<URL>();
            for (File j : js) {
                try {
                    urls.add(j.toURI().toURL());
                } catch (IOException e) {
                    warnings.add(j.getName() + ": " + e.getMessage());
                }
            }
            own = new URLClassLoader("gatf-plugins", urls.toArray(new URL[0]), base);
            loader = own;
        }
        Iterator<GatfStepPlugin> it = ServiceLoader.load(GatfStepPlugin.class, loader).iterator();
        while (true) {
            GatfStepPlugin p;
            try {
                if (!it.hasNext()) break;
                p = it.next();
            } catch (ServiceConfigurationError | LinkageError e) {
                warnings.add("plugin not loaded: " + e.getMessage());
                continue;
            }
            String name;
            try {
                name = p.name();
            } catch (RuntimeException | LinkageError e) {
                warnings.add(p.getClass().getName() + ": name() failed: " + e);
                continue;
            }
            if (name == null || !name.matches("[a-z][a-z0-9_-]*")) {
                warnings.add(p.getClass().getName() + ": invalid plugin name '" + name + "' (expected [a-z][a-z0-9_-]*)");
                continue;
            }
            if (plugins.containsKey(name)) {
                warnings.add("plugin '" + name + "' is defined twice; keeping " + plugins.get(name).getClass().getName());
                continue;
            }
            plugins.put(name, p);
            sources.put(name, source(p.getClass()));
        }
        return true;
    }

    /**
     * Calls {@link GatfStepPlugin#init(Object)} once per loaded instance (a reload makes new instances, which are
     * initialised again). A failing init is logged as a warning and the plugin stays callable.
     */
    public static synchronized void ensureInit(Object context) {
        for (Map.Entry<String, GatfStepPlugin> e : plugins.entrySet()) {
            if (!inited.add(e.getValue())) continue;
            try {
                e.getValue().init(context);
            } catch (RuntimeException | LinkageError x) {
                warnings.add("plugin '" + e.getKey() + "' init failed: " + x);
            }
        }
    }

    /** {@link #load} then {@link #ensureInit}: what a script compile or a separate run JVM (debugger, remote node) calls. */
    public static synchronized void loadAndInit(File dir, ClassLoader parent, Object context) {
        load(dir, parent);
        ensureInit(context);
    }

    private static String source(Class<?> c) {
        try {
            java.security.CodeSource cs = c.getProtectionDomain().getCodeSource();
            return cs == null || cs.getLocation() == null ? "classpath" : new File(cs.getLocation().toURI()).getName();
        } catch (Exception e) {
            return "classpath";
        }
    }

    public static synchronized GatfStepPlugin get(String name) {
        return name == null ? null : plugins.get(name.toLowerCase(Locale.ROOT));
    }

    /** Loaded v2 plugins, in load order. */
    public static synchronized Map<String, GatfStepPlugin> all() {
        return new LinkedHashMap<String, GatfStepPlugin>(plugins);
    }

    /** The jar (or "classpath") a v2 plugin came from. */
    public static synchronized String sourceOf(String name) {
        return sources.get(name);
    }

    /** Metadata of one v2 plugin as JSON-ready data: {name, summary, args, examples, source, kind}. */
    public static Map<String, Object> describe(GatfStepPlugin p) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        GatfStepPlugin.Info i;
        try {
            i = p.info();
        } catch (RuntimeException | LinkageError e) {
            i = null;
        }
        m.put("name", p.name());
        m.put("summary", i == null ? null : i.summary);
        m.put("args", i == null ? new ArrayList<Object>() : i.args);
        m.put("examples", i == null ? new ArrayList<Object>() : i.examples);
        m.put("source", sourceOf(p.name()));
        m.put("kind", "sdk");
        return m;
    }
}
