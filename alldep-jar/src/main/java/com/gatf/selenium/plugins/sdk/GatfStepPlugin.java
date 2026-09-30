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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * M13 Plugin SDK v2: a seleasy step implemented outside GATF. Package it in a jar with a
 * {@code META-INF/services/com.gatf.selenium.plugins.sdk.GatfStepPlugin} file naming the class, drop the jar into the
 * project's {@code plugins/} folder, and scripts can use it at once, with no rebuild:
 * <pre>
 *   greet "World"                  // a statement: {@code <name> args...}
 *   var msg plugin greet "World"   // its return value into a variable
 *   greet {                        // block arguments (one list per line)
 *     "a" "b"
 *   }
 * </pre>
 * Arguments arrive expanded as for built-in plugins ({@code ${var}}, {@code $v{var}}, {@code file://…}, {@code http(s)://…}).
 * {@link #info()} feeds the web editor's and the VS Code extension's completion. A name already taken by a built-in
 * or a {@code plugins.txt} entry keeps its first definition. Implementations need a public no-arg constructor.
 */
public interface GatfStepPlugin {

    /** The statement keyword: lower case, starting with a letter ({@code [a-z][a-z0-9_-]*}). */
    String name();

    /** Runs the step; the returned value is what {@code var x plugin <name> …} stores. */
    Object execute(Call call) throws Exception;

    /** Docs for completion and {@code GET /api/plugins}. */
    default Info info() {
        return new Info(name(), null);
    }

    /** Called once after the plugin is loaded, with the run's {@code AcceptanceTestContext} (null outside a run). */
    default void init(Object testContext) {
    }

    /** One invocation. */
    final class Call {
        public final String name;
        /** Inline arguments, expanded. */
        public final List<Object> args;
        /** Block arguments: one list per block line, expanded. */
        public final List<List<Object>> block;
        /** Index of the current data-provider row (0 when none). */
        public final int index;
        /** The run's {@code com.gatf.executor.core.AcceptanceTestContext}. */
        public final Object context;

        public Call(String name, List<Object> args, List<List<Object>> block, int index, Object context) {
            this.name = name;
            this.args = args == null ? Collections.<Object>emptyList() : Collections.unmodifiableList(new ArrayList<Object>(args));
            this.block = block == null ? Collections.<List<Object>>emptyList() : Collections.unmodifiableList(new ArrayList<List<Object>>(block));
            this.index = index;
            this.context = context;
        }

        /** Argument {@code i} as a string (null when absent). */
        public String arg(int i) {
            return i < args.size() && args.get(i) != null ? String.valueOf(args.get(i)) : null;
        }
    }

    /** Plugin metadata: a summary, the arguments and example lines. */
    final class Info {
        public final String name, summary;
        public final List<Map<String, Object>> args = new ArrayList<Map<String, Object>>();
        public final List<String> examples = new ArrayList<String>();

        public Info(String name, String summary) {
            this.name = name;
            this.summary = summary;
        }

        public Info arg(String argName, String doc, boolean required) {
            Map<String, Object> a = new LinkedHashMap<String, Object>();
            a.put("name", argName);
            a.put("doc", doc);
            a.put("required", required);
            args.add(a);
            return this;
        }

        public Info example(String line) {
            examples.add(line);
            return this;
        }
    }
}
