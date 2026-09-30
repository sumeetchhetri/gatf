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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * M14: one tool of the {@code gatf mcp} server. {@link #call} returns any Jackson-serialisable value; throwing
 * {@link ToolError} (or any exception) turns into an {@code isError} tool result the agent can read and act on.
 */
public interface McpTool {

    String name();

    String description();

    /** JSON Schema of the arguments (an {@code object} schema). */
    Map<String, Object> inputSchema();

    Object call(Map<String, Object> args) throws Exception;

    /** A failure the agent caused (bad argument, missing file): reported as a tool error, not a protocol error. */
    final class ToolError extends RuntimeException {
        private static final long serialVersionUID = 1L;
        public ToolError(String msg) { super(msg); }
    }

    interface Handler {
        Object call(Map<String, Object> args) throws Exception;
    }

    static McpTool of(final String name, final String description, final Map<String, Object> schema, final Handler h) {
        return new McpTool() {
            public String name() { return name; }
            public String description() { return description; }
            public Map<String, Object> inputSchema() { return schema; }
            public Object call(Map<String, Object> args) throws Exception { return h.call(args); }
        };
    }

    /** Builds an object schema from {@code name, type, description} triples; a name ending in '*' is required. */
    static Map<String, Object> schema(String... triples) {
        if (triples.length % 3 != 0) throw new IllegalArgumentException("schema needs name,type,description triples");
        Map<String, Object> props = new LinkedHashMap<String, Object>();
        List<String> required = new ArrayList<String>();
        for (int i = 0; i < triples.length; i += 3) {
            String n = triples[i];
            if (n.endsWith("*")) { n = n.substring(0, n.length() - 1); required.add(n); }
            Map<String, Object> p = new LinkedHashMap<String, Object>();
            String type = triples[i + 1];
            if (type.startsWith("enum:")) {
                p.put("type", "string");
                p.put("enum", Arrays.asList(type.substring(5).split("\\|")));
            } else if (type.equals("string[]")) {
                p.put("type", "array");
                p.put("items", java.util.Collections.singletonMap("type", "string"));
            } else {
                p.put("type", type);
            }
            p.put("description", triples[i + 2]);
            props.put(n, p);
        }
        Map<String, Object> s = new LinkedHashMap<String, Object>();
        s.put("type", "object");
        s.put("properties", props);
        if (!required.isEmpty()) s.put("required", required);
        return s;
    }

    // ---- argument helpers ----

    static String str(Map<String, Object> a, String k) {
        Object v = a == null ? null : a.get(k);
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    static String req(Map<String, Object> a, String k) {
        String s = str(a, k);
        if (s == null) throw new ToolError("missing required argument '" + k + "'");
        return s;
    }

    static int num(Map<String, Object> a, String k, int def, int min, int max) {
        Object v = a == null ? null : a.get(k);
        if (v == null) return def;
        int n;
        if (v instanceof Number) n = ((Number) v).intValue();
        else {
            try { n = Integer.parseInt(String.valueOf(v).trim()); }
            catch (NumberFormatException e) { throw new ToolError("'" + k + "' must be a number"); }
        }
        return Math.max(min, Math.min(max, n));
    }

    static Boolean bool(Map<String, Object> a, String k) {
        Object v = a == null ? null : a.get(k);
        if (v == null) return null;
        if (v instanceof Boolean) return (Boolean) v;
        return Boolean.valueOf(String.valueOf(v).trim());
    }

    /** A string or an array of strings, as a list (empty when absent). */
    static List<String> list(Map<String, Object> a, String k) {
        List<String> out = new ArrayList<String>();
        Object v = a == null ? null : a.get(k);
        if (v instanceof List) {
            for (Object o : (List<?>) v) if (o != null && !String.valueOf(o).trim().isEmpty()) out.add(String.valueOf(o).trim());
        } else if (v != null && !String.valueOf(v).trim().isEmpty()) {
            out.add(String.valueOf(v).trim());
        }
        return out;
    }
}
