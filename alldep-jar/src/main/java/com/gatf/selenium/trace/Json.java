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
package com.gatf.selenium.trace;

import java.util.Map;

/**
 * Minimal, dependency-free JSON writing helpers. Deliberately tiny: the trace recorders
 * emit a fixed, well-known shape (NDJSON lines and a couple of small documents), so a full
 * JSON library is not pulled in here — keeping the recorder core compilable and testable
 * with a plain javac and no classpath.
 *
 * @author Sumeet Chhetri
 */
public final class Json {
    private Json() {}

    /** Escape a string for inclusion inside JSON double quotes (no surrounding quotes added). */
    public static String esc(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n");  break;
                case '\r': b.append("\\r");  break;
                case '\t': b.append("\\t");  break;
                case '\b': b.append("\\b");  break;
                case '\f': b.append("\\f");  break;
                default:
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
            }
        }
        return b.toString();
    }

    /** A quoted+escaped JSON string, or the literal null. */
    public static String str(String s) { return s == null ? "null" : "\"" + esc(s) + "\""; }

    /**
     * Serialize a plain object graph (Map / Iterable / arrays / String / Number / Boolean / null)
     * to JSON. Used to write raw CDP command responses (nested Maps and Lists) straight to disk
     * without walking any typed model — keeps the risky, unverifiable model-shape code out of the
     * transports. Anything else is emitted as its toString() in quotes.
     */
    public static String write(Object o) {
        StringBuilder b = new StringBuilder();
        writeTo(b, o);
        return b.toString();
    }

    private static void writeTo(StringBuilder b, Object o) {
        if (o == null) { b.append("null"); return; }
        if (o instanceof String) { b.append('"').append(esc((String) o)).append('"'); return; }
        if (o instanceof Boolean) { b.append(o.toString()); return; }
        if (o instanceof Number) {
            double d = ((Number) o).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) b.append("null"); else b.append(o.toString());
            return;
        }
        if (o instanceof Map) {
            b.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : ((Map<?, ?>) o).entrySet()) {
                if (!first) b.append(',');
                first = false;
                b.append('"').append(esc(String.valueOf(e.getKey()))).append("\":");
                writeTo(b, e.getValue());
            }
            b.append('}');
            return;
        }
        if (o instanceof Iterable) {
            b.append('[');
            boolean first = true;
            for (Object e : (Iterable<?>) o) { if (!first) b.append(','); first = false; writeTo(b, e); }
            b.append(']');
            return;
        }
        if (o instanceof Object[]) {
            b.append('[');
            Object[] a = (Object[]) o;
            for (int i = 0; i < a.length; i++) { if (i > 0) b.append(','); writeTo(b, a[i]); }
            b.append(']');
            return;
        }
        b.append('"').append(esc(String.valueOf(o))).append('"');
    }

    /** Fluent object builder producing a compact single-line JSON object (for NDJSON). */
    public static final class Obj {
        private final StringBuilder b = new StringBuilder("{");
        private boolean first = true;
        private Obj key(String k) {
            if (!first) b.append(',');
            first = false;
            b.append('"').append(esc(k)).append("\":");
            return this;
        }
        public Obj put(String k, String v) { key(k); b.append(str(v)); return this; }
        public Obj put(String k, long v)   { key(k); b.append(v); return this; }
        public Obj put(String k, boolean v){ key(k); b.append(v); return this; }
        public Obj putNum(String k, Long v){ key(k); b.append(v == null ? "null" : v.toString()); return this; }
        /** Insert a pre-rendered JSON value (object/array/number) verbatim. */
        public Obj putRaw(String k, String rawJson) { key(k); b.append(rawJson == null ? "null" : rawJson); return this; }
        public String end() { return b.append('}').toString(); }
    }
}
