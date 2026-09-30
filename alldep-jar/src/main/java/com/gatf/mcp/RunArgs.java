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
package com.gatf.mcp;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * M14 {@code run}: tool arguments → the {@code gatf test} argument vector. The JSON reporter is always on, because
 * the tool reads its results back. Pure.
 */
public final class RunArgs {

    private RunArgs() {}

    public static List<String> build(Map<String, Object> a) {
        List<String> v = new ArrayList<String>();
        v.add("test");
        String cfg = McpTool.str(a, "config");
        if (cfg != null) {
            if (cfg.startsWith("-")) throw new McpTool.ToolError("config must be a file name");
            v.add(cfg);
        }
        for (String g : McpTool.list(a, "grep")) { v.add("--grep"); v.add(g); }
        for (String g : McpTool.list(a, "grepInvert")) { v.add("--grep-invert"); v.add(g); }
        for (String t : McpTool.list(a, "tag")) { v.add("--tag"); v.add(t); }
        String trace = McpTool.str(a, "trace");
        if (trace != null) {
            if (!trace.matches("off|on|retain-on-failure|on-first-retry")) throw new McpTool.ToolError("trace must be off|on|retain-on-failure|on-first-retry");
            v.add("--trace"); v.add(trace);
        }
        if (a.get("retries") != null) { v.add("--retries"); v.add(String.valueOf(McpTool.num(a, "retries", 0, 0, 10))); }
        if (a.get("workers") != null) { v.add("--workers"); v.add(String.valueOf(McpTool.num(a, "workers", 1, 1, 64))); }
        String shard = McpTool.str(a, "shard");
        if (shard != null) {
            if (!shard.matches("\\d+/\\d+")) throw new McpTool.ToolError("shard must be i/n");
            v.add("--shard"); v.add(shard);
        }
        if (Boolean.TRUE.equals(McpTool.bool(a, "updateSnapshots"))) v.add("--update-snapshots");
        String rep = McpTool.str(a, "reporter");
        if (rep == null || rep.equalsIgnoreCase("none")) rep = "junit,json";
        if (!rep.toLowerCase(java.util.Locale.ROOT).contains("json")) rep += ",json";
        v.add("--reporter"); v.add(rep);
        return v;
    }

    /** The results file a {@code gatf test} run names on its console ({@code "JSON:   <path>"}), or null for other lines. */
    public static String resultsPath(String line) {
        if (line == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("JSON:\\s+(.*?gatf-results\\.json)").matcher(line);
        return m.find() ? m.group(1).trim() : null;
    }

    /** The server JVM's flags a child run should inherit (GATF_JAVA_OPTS: -D…, heap/stack sizes); never agents or debug ports. */
    public static List<String> jvmFlags(List<String> inputArgs) {
        List<String> out = new ArrayList<String>();
        if (inputArgs == null) return out;
        for (String f : inputArgs) {
            if (f == null) continue;
            if ((f.startsWith("-D") && !f.startsWith("-Djava.class.path")) || f.startsWith("-Xmx") || f.startsWith("-Xms") || f.startsWith("-Xss")
                    || f.startsWith("-XX:MaxMetaspaceSize") || f.startsWith("--add-opens") || f.startsWith("--add-exports")) {
                out.add(f);
            }
        }
        return out;
    }
}
