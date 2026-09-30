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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * M13: a third-party jar dropped into plugins/ works without a rebuild. The jar is built here with javac against the
 * SDK classes only, as a plugin author would.
 */
class PluginRegistryTest {

    @TempDir
    File tmp;

    /** Compiles {@code sources} (class name → code) and packs them + a services file into {@code jar}. */
    private File jar(String jarName, Map<String, String> sources, String services) throws Exception {
        File src = new File(tmp, "src-" + jarName), out = new File(tmp, "out-" + jarName);
        src.mkdirs();
        out.mkdirs();
        List<String> args = new ArrayList<String>();
        String sdk = new File(GatfStepPlugin.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getPath();
        args.add("-cp");
        args.add(sdk);
        args.add("-d");
        args.add(out.getPath());
        for (Map.Entry<String, String> e : sources.entrySet()) {
            File f = new File(src, e.getKey().replace('.', '/') + ".java");
            f.getParentFile().mkdirs();
            Files.write(f.toPath(), e.getValue().getBytes(StandardCharsets.UTF_8));
            args.add(f.getPath());
        }
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        assertThat(javac.run(null, null, null, args.toArray(new String[0]))).isEqualTo(0);
        File plugins = new File(tmp, "plugins");
        plugins.mkdirs();
        File jar = new File(plugins, jarName);
        File[] classes;
        try (java.util.stream.Stream<java.nio.file.Path> w = Files.walk(out.toPath())) {
            classes = w.map(java.nio.file.Path::toFile).filter(File::isFile).toArray(File[]::new);
        }
        try (JarOutputStream jo = new JarOutputStream(new FileOutputStream(jar))) {
            for (File c : classes) {
                jo.putNextEntry(new JarEntry(out.toPath().relativize(c.toPath()).toString().replace('\\', '/')));
                jo.write(Files.readAllBytes(c.toPath()));
                jo.closeEntry();
            }
            if (services != null) {
                jo.putNextEntry(new JarEntry("META-INF/services/" + GatfStepPlugin.class.getName()));
                jo.write(services.getBytes(StandardCharsets.UTF_8));
                jo.closeEntry();
            }
        }
        return jar;
    }

    private static String greet(String word) {
        return "package acme;\n"
                + "import com.gatf.selenium.plugins.sdk.GatfStepPlugin;\n"
                + "public class Greet implements GatfStepPlugin {\n"
                + "  public String name() { return \"greet\"; }\n"
                + "  public Object execute(Call c) { return \"" + word + ", \" + c.arg(0) + \" (\" + c.block.size() + \")\"; }\n"
                + "  public Info info() { return new Info(\"greet\", \"Says hello\").arg(\"who\", \"whom to greet\", true).example(\"greet World\"); }\n"
                + "}\n";
    }

    @Test
    void aThirdPartyJarWorksWithoutARebuild() throws Exception {
        File plugins = new File(tmp, "plugins");
        assertThat(PluginRegistry.load(plugins, getClass().getClassLoader())).isTrue();
        assertThat(PluginRegistry.get("greet")).isNull();

        jar("acme-1.jar", Map.of("acme.Greet", greet("Hello")), "acme.Greet\n");
        assertThat(PluginRegistry.load(plugins, getClass().getClassLoader())).isTrue();
        GatfStepPlugin p = PluginRegistry.get("greet");
        assertThat(p).isNotNull();
        Object r = p.execute(new GatfStepPlugin.Call("greet", List.of("World"), List.of(List.of("a")), 0, null));
        assertThat(r).isEqualTo("Hello, World (1)");
        assertThat(PluginRegistry.sourceOf("greet")).isEqualTo("acme-1.jar");
        Map<String, Object> d = PluginRegistry.describe(p);
        assertThat(d.get("summary")).isEqualTo("Says hello");
        assertThat(d.get("examples").toString()).isEqualTo("[greet World]");
        assertThat(d.get("kind")).isEqualTo("sdk");

        // unchanged folder: no reload
        assertThat(PluginRegistry.load(plugins, getClass().getClassLoader())).isFalse();

        // a new version of the jar is picked up by the next load (next script compile / run), no restart
        new File(plugins, "acme-1.jar").delete();
        jar("acme-2.jar", Map.of("acme.Greet", greet("Hi")), "acme.Greet\n");
        assertThat(PluginRegistry.load(plugins, getClass().getClassLoader())).isTrue();
        assertThat(PluginRegistry.get("greet").execute(new GatfStepPlugin.Call("greet", List.of("you"), null, 0, null))).isEqualTo("Hi, you (0)");
    }

    @Test
    void legacyStaticPluginsInAJarResolveThroughTheRegistryLoader() throws Exception {
        File plugins = new File(tmp, "plugins");
        jar("legacy.jar", Map.of("acme.Legacy", "package acme; public class Legacy { public static Object execute(Object[] a) { return \"legacy:\" + a.length; } }"), null);
        PluginRegistry.load(plugins, getClass().getClassLoader());
        Class<?> c = Class.forName("acme.Legacy", true, PluginRegistry.classLoader());
        assertThat(c.getMethod("execute", Object[].class).invoke(null, (Object) new Object[] { 1, 2 })).isEqualTo("legacy:2");
    }

    @Test
    void theCatalogListsBuiltinsThenPluginsTxtThenSdkFirstWins() throws Exception {
        jar("acme.jar", Map.of("acme.Greet", greet("Hello")), "acme.Greet\n");
        Files.write(new File(tmp, "plugins.txt").toPath(), "shout=acme.Shout@execute\ncurl=acme.Other@execute\n".getBytes(StandardCharsets.UTF_8));
        List<Map<String, Object>> all = com.gatf.selenium.PluginCatalog.list(List.of(new File(tmp, "missing"), tmp));
        List<Object> names = new ArrayList<Object>();
        for (Map<String, Object> m : all) names.add(m.get("name"));
        assertThat(names).containsExactly("jsonread", "jsonwrite", "jsonpath", "xmlread", "xmlwrite", "xmlpath", "api", "curl", "shout", "greet");
        Map<String, Object> curl = all.get(7), shout = all.get(8), greet = all.get(9);
        assertThat(curl.get("kind")).isEqualTo("builtin");   // plugins.txt cannot replace a built-in
        assertThat(shout.get("kind")).isEqualTo("plugins.txt");
        assertThat(greet.get("kind")).isEqualTo("sdk");
        assertThat(greet.get("summary")).isEqualTo("Says hello");
        assertThat(greet.get("source")).isEqualTo("acme.jar");
    }

    @Test
    void badPluginsAreReportedNotFatal() throws Exception {
        File plugins = new File(tmp, "plugins");
        String bad = "package acme; import com.gatf.selenium.plugins.sdk.GatfStepPlugin;"
                + " public class Bad implements GatfStepPlugin { public String name() { return \"Bad Name\"; } public Object execute(Call c) { return null; } }";
        String dup = "package acme; import com.gatf.selenium.plugins.sdk.GatfStepPlugin;"
                + " public class Dup implements GatfStepPlugin { public String name() { return \"greet\"; } public Object execute(Call c) { return \"dup\"; } }";
        jar("mixed.jar", Map.of("acme.Greet", greet("Hello"), "acme.Bad", bad, "acme.Dup", dup), "acme.Greet\nacme.Bad\nacme.Dup\nacme.Missing\n");
        PluginRegistry.load(plugins, getClass().getClassLoader());
        assertThat(PluginRegistry.get("greet").execute(new GatfStepPlugin.Call("greet", List.of("x"), null, 0, null))).isEqualTo("Hello, x (0)");
        String w = String.join("\n", PluginRegistry.warnings());
        assertThat(w).contains("invalid plugin name 'Bad Name'").contains("defined twice").contains("plugin not loaded");
    }

    @Test
    void initRunsOncePerLoadedInstanceAndFailuresAreWarnings() throws Exception {
        File plugins = new File(tmp, "plugins");
        String counted = "package acme; import com.gatf.selenium.plugins.sdk.GatfStepPlugin;"
                + " public class Counted implements GatfStepPlugin { public static int inits; public String name() { return \"counted\"; }"
                + " public void init(Object ctx) { inits++; } public Object execute(Call c) { return inits; } }";
        String broken = "package acme; import com.gatf.selenium.plugins.sdk.GatfStepPlugin;"
                + " public class Broken implements GatfStepPlugin { public String name() { return \"broken\"; }"
                + " public void init(Object ctx) { throw new IllegalStateException(\"no config\"); } public Object execute(Call c) { return \"ok\"; } }";
        jar("init.jar", Map.of("acme.Counted", counted, "acme.Broken", broken), "acme.Counted\nacme.Broken\n");
        PluginRegistry.loadAndInit(plugins, getClass().getClassLoader(), "ctx");
        PluginRegistry.loadAndInit(plugins, getClass().getClassLoader(), "ctx");   // unchanged: no second init
        assertThat(PluginRegistry.get("counted").execute(new GatfStepPlugin.Call("counted", List.of(), null, 0, null))).isEqualTo(1);
        assertThat(PluginRegistry.get("broken").execute(new GatfStepPlugin.Call("broken", List.of(), null, 0, null))).isEqualTo("ok");
        assertThat(String.join("\n", PluginRegistry.warnings())).contains("plugin 'broken' init failed").contains("no config");
    }

    @AfterEach
    void closeLoader() {
        PluginRegistry.load(null, getClass().getClassLoader());   // closes the jar loader (Windows @TempDir cleanup)
    }
}
