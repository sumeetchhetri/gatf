package com.gatf.selenium.trace;

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
