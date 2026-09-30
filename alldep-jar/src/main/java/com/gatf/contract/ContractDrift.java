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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * OA2 — operation fingerprints and the drift list on re-import: operations added, removed or changed since the
 * existing test cases were generated. A fingerprint hashes the operation with every {@code $ref} inlined (cycles
 * cut) and object keys sorted, so reordering a spec is not drift but changing a referenced schema is.
 */
public final class ContractDrift {

    private ContractDrift() {}

    /** Drift between the cases on disk and a fresh import, keyed by operation pointer → fingerprint. */
    public static final class Report {
        public final List<String> added = new ArrayList<String>();
        public final List<String> removed = new ArrayList<String>();
        public final List<String> changed = new ArrayList<String>();

        public boolean isEmpty() { return added.isEmpty() && removed.isEmpty() && changed.isEmpty(); }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("added", added);
            m.put("removed", removed);
            m.put("changed", changed);
            return m;
        }

        @Override
        public String toString() {
            if (isEmpty()) return "no drift";
            StringBuilder b = new StringBuilder();
            for (String s : added) b.append("\n   + ").append(s);
            for (String s : removed) b.append("\n   - ").append(s);
            for (String s : changed) b.append("\n   ~ ").append(s);
            return b.substring(1);
        }
    }

    /** @param before pointer → fingerprint of the existing cases; @param after the same for the new import */
    public static Report diff(Map<String, String> before, Map<String, String> after) {
        Report r = new Report();
        for (Map.Entry<String, String> e : after.entrySet()) {
            if (!before.containsKey(e.getKey())) r.added.add(label(e.getKey()));
            else if (e.getValue() != null && before.get(e.getKey()) != null && !e.getValue().equals(before.get(e.getKey()))) r.changed.add(label(e.getKey()));
        }
        for (String k : before.keySet()) if (!after.containsKey(k)) r.removed.add(label(k));
        return r;
    }

    /** {@code /paths/~1pet~1{petId}/get} → {@code GET /pet/{petId}}. */
    public static String label(String pointer) {
        return ContractCheck.label(null, pointer);
    }

    /** 12-hex SHA-1 of the operation (plus its path-level parameters) with refs inlined and keys sorted. */
    public static String fingerprint(SpecDoc spec, SpecDoc.Node op, SpecDoc.Node pathParams) {
        StringBuilder b = new StringBuilder();
        canon(spec, op, b, new IdentityHashMap<JsonNode, Boolean>(), 0, false);
        if (pathParams != null && pathParams.json != null && !pathParams.json.isMissingNode()) {
            b.append('|');
            canon(spec, pathParams, b, new IdentityHashMap<JsonNode, Boolean>(), 0, false);
        }
        try {
            byte[] h = MessageDigest.getInstance("SHA-1").digest(b.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder x = new StringBuilder();
            for (int i = 0; i < 6; i++) x.append(String.format("%02x", h[i]));
            return x.toString();
        } catch (Exception e) {
            return Integer.toHexString(b.toString().hashCode());
        }
    }

    /** @param names true when {@code n} is a name → schema map (properties), whose keys are data, not keywords */
    private static void canon(SpecDoc spec, SpecDoc.Node n, StringBuilder b, IdentityHashMap<JsonNode, Boolean> stack, int depth, boolean names) {
        JsonNode j = n.json;
        if (j != null && j.isObject() && j.has("$ref")) {
            SpecDoc.Node t = spec.deref(n);
            if (t == null || stack.containsKey(t.json) || depth > 40) {
                b.append("{\"$ref\":").append(j.get("$ref").toString()).append('}');
                return;
            }
            stack.put(t.json, Boolean.TRUE);
            canon(spec, t, b, stack, depth + 1, names);
            stack.remove(t.json);
            return;
        }
        if (j == null || j.isMissingNode()) { b.append("null"); return; }
        if (j.isObject()) {
            Map<String, JsonNode> sorted = new TreeMap<String, JsonNode>();
            for (Iterator<Map.Entry<String, JsonNode>> it = j.fields(); it.hasNext();) {
                Map.Entry<String, JsonNode> e = it.next();
                String k = e.getKey();
                if (!names && (((k.equals("description") || k.equals("summary")) && e.getValue().isTextual())
                        || k.equals("example") || k.equals("examples") || k.startsWith("x-"))) continue;   // prose/examples are not contract
                sorted.put(e.getKey(), e.getValue());
            }
            b.append('{');
            for (Map.Entry<String, JsonNode> e : sorted.entrySet()) {
                b.append('"').append(e.getKey()).append("\":");
                boolean map = !names && (e.getKey().equals("properties") || e.getKey().equals("patternProperties"));
                canon(spec, new SpecDoc.Node(e.getValue(), n.doc, null), b, stack, depth + 1, map);
                b.append(',');
            }
            b.append('}');
        } else if (j.isArray()) {
            b.append('[');
            for (JsonNode e : j) { canon(spec, new SpecDoc.Node(e, n.doc, null), b, stack, depth + 1, false); b.append(','); }
            b.append(']');
        } else {
            b.append(j.toString());
        }
    }
}
