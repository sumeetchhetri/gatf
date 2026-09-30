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
package com.gatf.contract;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * OA2 — a JSON Schema subset validator for response bodies (OpenAPI 3.0 schema objects, 3.1 / draft 2020-12 core
 * keywords, Swagger 2.0 schemas). Covers type (incl. 3.1 type arrays), nullable / x-nullable, enum, const,
 * required / properties / additionalProperties / patternProperties / min-maxProperties, items / prefixItems /
 * min-maxItems / uniqueItems, allOf / anyOf / oneOf / not, min-maxLength, pattern, minimum / maximum /
 * exclusive* (3.0 boolean and 3.1 numeric) / multipleOf, and common formats. {@code writeOnly} properties are not
 * required in responses. Unknown keywords are ignored. No dependency beyond Jackson.
 */
public final class SchemaValidator {

    /** One contract breach: JSON pointer into the body, what was expected, what was found. */
    public static final class Violation {
        public final String pointer;
        public final String message;
        public final String expected;
        public final String actual;

        Violation(String pointer, String message, String expected, String actual) {
            this.pointer = pointer.isEmpty() ? "/" : pointer;
            this.message = message;
            this.expected = expected;
            this.actual = actual;
        }

        @Override
        public String toString() {
            return pointer + ": " + message + " (expected " + expected + ", actual " + actual + ")";
        }
    }

    private static final int MAX_DEPTH = 64;
    private static final int MAX_VIOLATIONS = 50;
    private final SpecDoc spec;
    private final boolean response;
    /** Schema defects met while validating (an unresolvable $ref): reported, never failing the instance. */
    private final LinkedHashSet<String> notes = new LinkedHashSet<String>();

    /** @param response true: writeOnly properties are not required and readOnly ones are allowed (response bodies) */
    public SchemaValidator(SpecDoc spec, boolean response) {
        this.spec = spec;
        this.response = response;
    }

    public List<Violation> validate(SpecDoc.Node schema, JsonNode instance) {
        List<Violation> out = new ArrayList<Violation>();
        check(schema, instance, "", out, 0);
        return out;
    }

    /** Notes from the last {@link #validate} calls, e.g. "schema $ref X unresolvable — not validated". */
    public List<String> notes() {
        return new ArrayList<String>(notes);
    }

    private void check(SpecDoc.Node sn, JsonNode v, String ptr, List<Violation> out, int depth) {
        if (sn == null || sn.json == null || out.size() >= MAX_VIOLATIONS || depth > MAX_DEPTH) return;
        JsonNode raw = sn.json;
        if (raw.isBoolean()) {
            if (!raw.asBoolean()) out.add(new Violation(ptr, "not allowed here", "nothing", describe(v)));
            return;
        }
        if (!raw.isObject()) return;
        SpecDoc.Node s = sn;
        if (raw.has("$ref")) {
            if (v.isNull() && nullable(raw)) return;   // OAS 3.0 {$ref, nullable: true}
            s = spec.deref(sn);
            if (s == null) {   // unresolvable ref: a spec defect, noted (not a response violation)
                notes.add("schema $ref " + raw.get("$ref").asText("") + " unresolvable — not validated");
                return;
            }
            // 3.1 allows keywords beside $ref; validate them too (3.0 tools ignore them, harmlessly)
            if (raw.size() > 1) checkKeywords(new SpecDoc.Node(withoutRef(raw), sn.doc, null), v, ptr, out, depth);
            if (s.json.isBoolean() || !s.json.isObject()) { check(s, v, ptr, out, depth + 1); return; }
        }
        checkKeywords(s, v, ptr, out, depth);
    }

    private static JsonNode withoutRef(JsonNode raw) {
        com.fasterxml.jackson.databind.node.ObjectNode o = raw.deepCopy();
        o.remove("$ref");
        return o;
    }

    private void checkKeywords(SpecDoc.Node s, JsonNode v, String ptr, List<Violation> out, int depth) {
        JsonNode n = s.json;
        if (n.has("$ref")) { check(s, v, ptr, out, depth + 1); return; }

        if (v.isNull() && nullable(n)) return;   // a 3.1 type array with "null" still applies enum / const

        // combinators
        for (JsonNode sub : n.path("allOf")) check(child(sub, s), v, ptr, out, depth + 1);
        if (n.path("anyOf").size() > 0) {
            boolean ok = false;
            for (JsonNode sub : n.get("anyOf")) if (passes(child(sub, s), v, depth)) { ok = true; break; }
            if (!ok) out.add(new Violation(ptr, "matches none of anyOf", "one of " + n.get("anyOf").size() + " schemas", describe(v)));
        }
        JsonNode disc = n.path("discriminator").path("propertyName");
        if (n.path("oneOf").size() > 0 && disc.isTextual() && v.isObject() && v.path(disc.asText()).isTextual()) {
            // OpenAPI discriminator: the property names the branch, so overlapping subtypes don't double-match
            String val = v.get(disc.asText()).asText();
            SpecDoc.Node pick = discriminated(n, s, val);
            if (pick == null) {
                out.add(new Violation(ptr + "/" + disc.asText(), "discriminator value selects no oneOf schema",
                        "one of the mapped / named schemas", "'" + val + "'"));
            } else {
                check(pick, v, ptr, out, depth + 1);
            }
        } else if (n.path("oneOf").size() > 0) {
            int hits = 0;
            for (JsonNode sub : n.get("oneOf")) if (passes(child(sub, s), v, depth)) hits++;
            if (hits != 1) out.add(new Violation(ptr, hits == 0 ? "matches none of oneOf" : "matches " + hits + " oneOf schemas",
                    "exactly one of " + n.get("oneOf").size(), describe(v)));
        }
        if (n.has("not") && passes(child(n.get("not"), s), v, depth)) {
            out.add(new Violation(ptr, "matches the 'not' schema", "not that schema", describe(v)));
        }

        // type
        List<String> types = types(n);
        if (!types.isEmpty()) {
            boolean ok = false;
            for (String t : types) if (isType(v, t)) { ok = true; break; }
            if (!ok) {
                out.add(new Violation(ptr, "wrong type", String.join("|", types), typeName(v)));
                return;
            }
        }
        if (n.has("enum")) {
            boolean ok = false;
            for (JsonNode e : n.get("enum")) if (same(e, v)) { ok = true; break; }
            if (!ok) out.add(new Violation(ptr, "not in enum", n.get("enum").toString(), describe(v)));
        }
        if (n.has("const") && !same(n.get("const"), v)) {
            out.add(new Violation(ptr, "not the const value", n.get("const").toString(), describe(v)));
        }

        if (v.isTextual()) strings(n, v.asText(), ptr, out);
        if (v.isNumber()) numbers(n, v, ptr, out);
        if (v.isArray()) arrays(s, v, ptr, out, depth);
        if (v.isObject()) objects(s, v, ptr, out, depth);
    }

    /** The oneOf branch a discriminator value names: its mapping entry, else the oneOf $ref whose name is the value. */
    private SpecDoc.Node discriminated(JsonNode n, SpecDoc.Node s, String value) {
        JsonNode mapped = n.path("discriminator").path("mapping").path(value);
        if (mapped.isTextual()) {
            String ref = mapped.asText();
            if (!ref.contains("#") && !ref.contains("/")) ref = "#/components/schemas/" + ref;   // bare schema name
            com.fasterxml.jackson.databind.node.ObjectNode o = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            o.put("$ref", ref);
            return child(o, s);
        }
        for (JsonNode sub : n.get("oneOf")) {
            String ref = sub.path("$ref").asText("");
            if (!ref.isEmpty() && ref.substring(ref.lastIndexOf('/') + 1).equals(value)) return child(sub, s);
        }
        return null;
    }

    private SpecDoc.Node child(JsonNode sub, SpecDoc.Node parent) {
        return new SpecDoc.Node(sub, parent.doc, null);
    }

    private boolean passes(SpecDoc.Node s, JsonNode v, int depth) {
        List<Violation> tmp = new ArrayList<Violation>();
        check(s, v, "", tmp, depth + 1);
        return tmp.isEmpty();
    }

    // ---------------------------------------------------------------- keywords

    private static void strings(JsonNode n, String s, String ptr, List<Violation> out) {
        int len = s.codePointCount(0, s.length());
        if (n.has("minLength") && len < n.get("minLength").asInt()) {
            out.add(new Violation(ptr, "too short", "minLength " + n.get("minLength").asInt(), "length " + len));
        }
        if (n.has("maxLength") && len > n.get("maxLength").asInt()) {
            out.add(new Violation(ptr, "too long", "maxLength " + n.get("maxLength").asInt(), "length " + len));
        }
        if (n.has("pattern")) {
            try {
                if (!Pattern.compile(n.get("pattern").asText()).matcher(s).find()) {
                    out.add(new Violation(ptr, "does not match pattern", n.get("pattern").asText(), quote(s)));
                }
            } catch (PatternSyntaxException e) {
                // an ECMA-only pattern Java cannot compile: not asserted
            }
        }
        String f = n.path("format").asText("");
        if (!f.isEmpty() && !Formats.valid(f, s)) out.add(new Violation(ptr, "invalid " + f, "format " + f, quote(s)));
    }

    private static void numbers(JsonNode n, JsonNode v, String ptr, List<Violation> out) {
        BigDecimal d = v.decimalValue();
        if (n.has("minimum")) {
            BigDecimal m = n.get("minimum").decimalValue();
            boolean excl = n.path("exclusiveMinimum").asBoolean(false) && n.get("exclusiveMinimum").isBoolean();
            if (excl ? d.compareTo(m) <= 0 : d.compareTo(m) < 0) out.add(new Violation(ptr, "below minimum", (excl ? "> " : ">= ") + m, v.asText()));
        }
        if (n.path("exclusiveMinimum").isNumber() && d.compareTo(n.get("exclusiveMinimum").decimalValue()) <= 0) {
            out.add(new Violation(ptr, "below exclusiveMinimum", "> " + n.get("exclusiveMinimum").asText(), v.asText()));
        }
        if (n.has("maximum")) {
            BigDecimal m = n.get("maximum").decimalValue();
            boolean excl = n.path("exclusiveMaximum").asBoolean(false) && n.get("exclusiveMaximum").isBoolean();
            if (excl ? d.compareTo(m) >= 0 : d.compareTo(m) > 0) out.add(new Violation(ptr, "above maximum", (excl ? "< " : "<= ") + m, v.asText()));
        }
        if (n.path("exclusiveMaximum").isNumber() && d.compareTo(n.get("exclusiveMaximum").decimalValue()) >= 0) {
            out.add(new Violation(ptr, "above exclusiveMaximum", "< " + n.get("exclusiveMaximum").asText(), v.asText()));
        }
        if (n.has("multipleOf") && n.get("multipleOf").decimalValue().signum() > 0
                && d.remainder(n.get("multipleOf").decimalValue()).signum() != 0) {
            out.add(new Violation(ptr, "not a multiple", "multipleOf " + n.get("multipleOf").asText(), v.asText()));
        }
        String f = n.path("format").asText("");
        if (("int32".equals(f) || "int64".equals(f)) && isType(v, "integer")) {
            BigDecimal lo = new BigDecimal("int32".equals(f) ? Integer.MIN_VALUE : Long.MIN_VALUE);
            BigDecimal hi = new BigDecimal("int32".equals(f) ? Integer.MAX_VALUE : Long.MAX_VALUE);
            if (d.compareTo(lo) < 0 || d.compareTo(hi) > 0) out.add(new Violation(ptr, "out of " + f + " range", f, v.asText()));
        }
    }

    private void arrays(SpecDoc.Node s, JsonNode v, String ptr, List<Violation> out, int depth) {
        JsonNode n = s.json;
        if (n.has("minItems") && v.size() < n.get("minItems").asInt()) {
            out.add(new Violation(ptr, "too few items", "minItems " + n.get("minItems").asInt(), v.size() + " items"));
        }
        if (n.has("maxItems") && v.size() > n.get("maxItems").asInt()) {
            out.add(new Violation(ptr, "too many items", "maxItems " + n.get("maxItems").asInt(), v.size() + " items"));
        }
        if (n.path("uniqueItems").asBoolean(false)) {
            List<JsonNode> seen = new ArrayList<JsonNode>();
            outer:
            for (JsonNode e : v) {
                for (JsonNode x : seen) {
                    if (same(x, e)) { out.add(new Violation(ptr, "duplicate items", "unique items", describe(e))); break outer; }
                }
                seen.add(e);
            }
        }
        int start = 0;
        JsonNode prefix = n.path("prefixItems");
        for (int i = 0; i < prefix.size() && i < v.size(); i++) {
            check(child(prefix.get(i), s), v.get(i), ptr + "/" + i, out, depth + 1);
            start = i + 1;
        }
        JsonNode items = n.get("items");
        if (items != null && (items.isObject() || items.isBoolean())) {
            for (int i = start; i < v.size(); i++) check(child(items, s), v.get(i), ptr + "/" + i, out, depth + 1);
        } else if (items != null && items.isArray()) {   // draft-4 tuple form (rare in OpenAPI)
            for (int i = 0; i < items.size() && i < v.size(); i++) check(child(items.get(i), s), v.get(i), ptr + "/" + i, out, depth + 1);
        }
    }

    private void objects(SpecDoc.Node s, JsonNode v, String ptr, List<Violation> out, int depth) {
        JsonNode n = s.json;
        JsonNode props = n.path("properties");
        for (JsonNode r : n.path("required")) {
            String name = r.asText();
            if (v.has(name)) continue;
            SpecDoc.Node ps = props.has(name) ? spec.deref(child(props.get(name), s)) : null;
            if (response && ps != null && ps.json.path("writeOnly").asBoolean(false)) continue;
            if (!response && ps != null && ps.json.path("readOnly").asBoolean(false)) continue;
            out.add(new Violation(ptr + "/" + esc(name), "required property missing", "'" + name + "' present", "absent"));
        }
        if (n.has("minProperties") && v.size() < n.get("minProperties").asInt()) {
            out.add(new Violation(ptr, "too few properties", "minProperties " + n.get("minProperties").asInt(), v.size() + " properties"));
        }
        if (n.has("maxProperties") && v.size() > n.get("maxProperties").asInt()) {
            out.add(new Violation(ptr, "too many properties", "maxProperties " + n.get("maxProperties").asInt(), v.size() + " properties"));
        }
        JsonNode pattern = n.path("patternProperties");
        JsonNode additional = n.get("additionalProperties");
        for (Iterator<Map.Entry<String, JsonNode>> it = v.fields(); it.hasNext();) {
            Map.Entry<String, JsonNode> e = it.next();
            String p = ptr + "/" + esc(e.getKey());
            boolean matched = false;
            if (props.has(e.getKey())) {
                matched = true;
                check(child(props.get(e.getKey()), s), e.getValue(), p, out, depth + 1);
            }
            for (Iterator<Map.Entry<String, JsonNode>> pi = pattern.fields(); pi.hasNext();) {
                Map.Entry<String, JsonNode> pp = pi.next();
                try {
                    if (Pattern.compile(pp.getKey()).matcher(e.getKey()).find()) {
                        matched = true;
                        check(child(pp.getValue(), s), e.getValue(), p, out, depth + 1);
                    }
                } catch (PatternSyntaxException ex) {
                    matched = true;
                }
            }
            if (matched || additional == null) continue;
            if (additional.isBoolean() && !additional.asBoolean()) {
                out.add(new Violation(p, "additional property not allowed", "only " + names(props), "'" + e.getKey() + "'"));
            } else if (additional.isObject()) {
                check(child(additional, s), e.getValue(), p, out, depth + 1);
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    private static List<String> types(JsonNode n) {
        List<String> out = new ArrayList<String>();
        JsonNode t = n.get("type");
        if (t == null) return out;
        if (t.isTextual()) out.add(t.asText());
        else for (JsonNode e : t) out.add(e.asText());
        if (!out.isEmpty() && nullable(n) && !out.contains("null")) out.add("null");
        return out;
    }

    /** OAS {@code nullable} / {@code x-nullable} (a 3.1 {@code type: [.., "null"]} is handled as a plain type). */
    private static boolean nullable(JsonNode n) {
        return n.path("nullable").asBoolean(false) || n.path("x-nullable").asBoolean(false);
    }

    static boolean isType(JsonNode v, String t) {
        switch (t) {
            case "string": return v.isTextual();
            case "integer": return v.isIntegralNumber() || (v.isNumber() && v.decimalValue().stripTrailingZeros().scale() <= 0);
            case "number": return v.isNumber();
            case "boolean": return v.isBoolean();
            case "array": return v.isArray();
            case "object": return v.isObject();
            case "null": return v.isNull();
            case "file": return true;
            default: return true;
        }
    }

    /** JSON equality with numbers compared by value (1 == 1.0), recursively. */
    static boolean same(JsonNode a, JsonNode b) {
        if (a.isNumber() && b.isNumber()) return a.decimalValue().compareTo(b.decimalValue()) == 0;
        if (a.isArray() && b.isArray()) {
            if (a.size() != b.size()) return false;
            for (int i = 0; i < a.size(); i++) if (!same(a.get(i), b.get(i))) return false;
            return true;
        }
        if (a.isObject() && b.isObject()) {
            if (a.size() != b.size()) return false;
            for (Iterator<String> it = a.fieldNames(); it.hasNext();) {
                String k = it.next();
                if (!b.has(k) || !same(a.get(k), b.get(k))) return false;
            }
            return true;
        }
        return a.equals(b);
    }

    static String typeName(JsonNode v) {
        if (v.isTextual()) return "string " + quote(v.asText());
        if (v.isIntegralNumber()) return "integer " + v.asText();
        if (v.isNumber()) return "number " + v.asText();
        if (v.isBoolean()) return "boolean " + v.asText();
        if (v.isNull()) return "null";
        if (v.isArray()) return "array[" + v.size() + "]";
        return "object";
    }

    static String describe(JsonNode v) {
        if (v.isContainerNode()) return typeName(v);
        return v.isTextual() ? quote(v.asText()) : v.asText();
    }

    private static String quote(String s) {
        return "\"" + (s.length() > 60 ? s.substring(0, 57) + "..." : s) + "\"";
    }

    private static String names(JsonNode props) {
        List<String> l = new ArrayList<String>();
        props.fieldNames().forEachRemaining(l::add);
        return l.isEmpty() ? "declared properties" : l.toString();
    }

    /** JSON pointer escaping (RFC 6901). */
    static String esc(String s) {
        return s.replace("~", "~0").replace("/", "~1");
    }
}
