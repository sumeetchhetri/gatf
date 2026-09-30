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

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.gatf.selenium.plugins.sdk.GatfStepPlugin;
import com.gatf.selenium.plugins.sdk.PluginRegistry;

/**
 * M13: every plugin a script can call, with its docs, for {@code GET /api/plugins} (web editor + VS Code completion):
 * the built-ins, then {@code plugins.txt} entries, then SDK v2 plugins from {@code plugins/*.jar}; the first definition
 * of a name wins, as at compile time.
 */
public final class PluginCatalog {

    /** The built-in plugins ({@code name=class@method}); registered before plugins.txt and plugins/. */
    public static final List<String> BUILTIN = Arrays.asList(
            "jsonread=com.gatf.selenium.plugins.JsonPlugin@read",
            "jsonwrite=com.gatf.selenium.plugins.JsonPlugin@write",
            "jsonpath=com.gatf.selenium.plugins.JsonPlugin@path",
            "xmlread=com.gatf.selenium.plugins.XmlPlugin@read",
            "xmlwrite=com.gatf.selenium.plugins.XmlPlugin@write",
            "xmlpath=com.gatf.selenium.plugins.XmlPlugin@path",
            "api=com.gatf.selenium.plugins.ApiPlugin@api",
            "curl=com.gatf.selenium.plugins.CurlPlugin@execute");

    private PluginCatalog() {}

    /**
     * @param bases the folders searched, in order, for {@code plugins.txt} and {@code plugins/} (as
     *              AcceptanceTestContext.getResourceFile does: test-case dir, base path, working dir)
     */
    public static List<Map<String, Object>> list(List<File> bases) {
        Map<String, Map<String, Object>> out = new LinkedHashMap<String, Map<String, Object>>();
        File pluginsDir = first(bases, "plugins", true);
        PluginRegistry.load(pluginsDir, PluginCatalog.class.getClassLoader());
        for (String l : BUILTIN) legacy(out, l, "builtin");
        File txt = first(bases, "plugins.txt", false);
        if (txt != null) {
            try {
                for (String l : Files.readAllLines(txt.toPath(), StandardCharsets.UTF_8)) legacy(out, l, "plugins.txt");
            } catch (IOException e) {
                // unreadable: the compile step reports it
            }
        }
        for (Map.Entry<String, GatfStepPlugin> e : PluginRegistry.all().entrySet()) {
            if (!out.containsKey(e.getKey())) out.put(e.getKey(), PluginRegistry.describe(e.getValue()));
        }
        return new ArrayList<Map<String, Object>>(out.values());
    }

    private static File first(List<File> bases, String name, boolean dir) {
        for (File b : bases) {
            if (b == null) continue;
            File f = new File(b, name);
            if (dir ? f.isDirectory() : f.isFile()) return f;
        }
        return null;
    }

    /** A {@code name=class@method} line with docs from the class's {@code toSampleSelCmd()} (lines about this name). */
    static void legacy(Map<String, Map<String, Object>> out, String line, String kind) {
        int eq = line.indexOf('=');
        if (eq <= 0) return;
        String name = line.substring(0, eq).trim().toLowerCase(Locale.ROOT);
        if (name.isEmpty() || name.startsWith("#") || out.containsKey(name)) return;
        String cls = line.substring(eq + 1).trim();
        if (cls.indexOf('@') > 0) cls = cls.substring(0, cls.indexOf('@')).trim();
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("name", name);
        String summary = null;
        List<String> usage = new ArrayList<String>(), examples = new ArrayList<String>();
        try {
            Method d = Class.forName(cls, false, PluginRegistry.classLoader()).getMethod("toSampleSelCmd");
            String[] lines = (String[]) d.invoke(null);
            boolean ex = false;
            for (String s : lines == null ? new String[0] : lines) {
                if (s == null) continue;
                if (s.trim().equals("Examples :-")) { ex = true; continue; }
                if (!s.startsWith("\t")) { if (summary == null) summary = s.trim(); continue; }
                String t = s.trim();
                boolean mine = t.toLowerCase(Locale.ROOT).startsWith(name + " ") || t.equalsIgnoreCase(name) || t.toLowerCase(Locale.ROOT).contains("plugin " + name);
                if (mine) (ex ? examples : usage).add(t);
            }
        } catch (Throwable e) {
            // no docs (class not loadable here or no toSampleSelCmd)
        }
        m.put("summary", summary);
        m.put("usage", usage);
        m.put("args", new ArrayList<Object>());
        m.put("examples", examples);
        m.put("source", cls);
        m.put("kind", kind);
        out.put(name, m);
    }
}
