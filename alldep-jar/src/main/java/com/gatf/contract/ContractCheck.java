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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * OA2 — checks one HTTP response against the operation it was generated from: the status must be declared (exact,
 * {@code 4XX} range or {@code default}), the Content-Type must be one the response declares, and a JSON body must
 * satisfy the response schema. Failures name the operation and the JSON pointer, with expected vs actual.
 */
public final class ContractCheck {

    private static final ObjectMapper OM = new ObjectMapper();

    /** Outcome: the operation label and the failures (empty = the response honours the contract). */
    public static final class Result {
        public final String operation;
        public final List<String> failures = new ArrayList<String>();

        Result(String operation) { this.operation = operation; }

        public boolean ok() { return failures.isEmpty(); }

        /** One line for the report / JUnit message. */
        public String summary() {
            if (ok()) return "contract " + operation + ": ok";
            return "contract " + operation + ": " + failures.get(0) + (failures.size() > 1 ? " (+" + (failures.size() - 1) + " more)" : "");
        }

        /** Every failure, one per line. */
        public String detail() {
            StringBuilder b = new StringBuilder("contract " + operation + ":");
            for (String f : failures) b.append("\n  ").append(f);
            return b.toString();
        }
    }

    private ContractCheck() {}

    /** {@code petstore.openapi.json#/paths/~1pet/get} → {@code petstore.openapi.json} ("" when there is no file part). */
    public static String specFile(String contract) {
        int h = contract.indexOf('#');
        return (h < 0 ? contract : contract.substring(0, h)).trim();
    }

    public static String pointer(String contract) {
        int h = contract.indexOf('#');
        return h < 0 ? "" : contract.substring(h + 1).trim();
    }

    /** "getPetById (GET /pet/{petId})" from an operation pointer. */
    public static String label(SpecDoc spec, String opPointer) {
        String[] t = opPointer.split("/");
        String method = t.length > 0 ? t[t.length - 1].toUpperCase(Locale.ROOT) : "?";
        String path = t.length > 2 ? unesc(t[t.length - 2]) : opPointer;
        String id = spec == null ? "" : operation(spec, opPointer).json.path("operationId").asText("");
        return id.isEmpty() ? method + " " + path : id + " (" + method + " " + path + ")";
    }

    private static final java.util.regex.Pattern OP = java.util.regex.Pattern.compile("^/paths/([^/]+)/([a-z]+)$");

    /** The operation node; a path item that is itself a {@code $ref} (possibly into another file) is followed. */
    public static SpecDoc.Node operation(SpecDoc spec, String opPointer) {
        java.util.regex.Matcher m = OP.matcher(opPointer);
        if (m.matches()) {
            SpecDoc.Node item = spec.deref(spec.node(spec.root.path("paths").path(unesc(m.group(1))), ""));
            if (item == null) return spec.node(com.fasterxml.jackson.databind.node.MissingNode.getInstance(), "");
            return spec.node(item.json.path(m.group(2)), item.doc);
        }
        return spec.at(opPointer);
    }

    static String unesc(String token) {
        return token.replace("~1", "/").replace("~0", "~");
    }

    /**
     * @param contentType the response Content-Type header (null/blank = none)
     * @param body        the response body as text (null/blank = none)
     */
    public static Result check(SpecDoc spec, String opPointer, int status, String contentType, String body) {
        Result r = new Result(label(spec, opPointer));
        SpecDoc.Node op = operation(spec, opPointer);
        if (op.json == null || op.json.isMissingNode() || !op.json.isObject()) {
            r.failures.add("operation " + opPointer + " not found in the spec");
            return r;
        }
        JsonNode responses = op.json.path("responses");
        String key = responseKey(responses, status);
        if (key == null) {
            List<String> declared = new ArrayList<String>();
            responses.fieldNames().forEachRemaining(declared::add);
            r.failures.add("status " + status + " is not declared (declared: " + String.join(", ", declared) + ")");
            return r;
        }
        SpecDoc.Node resp = spec.deref(spec.node(responses.get(key), op.doc));
        if (resp == null) return r;
        boolean hasBody = body != null && !body.trim().isEmpty();
        String ct = mediaType(contentType);

        SpecDoc.Node schema = null;
        boolean json;
        if (spec.isV2()) {
            if (!resp.json.has("schema")) {
                if (hasBody && !ct.isEmpty()) r.failures.add("status " + key + " declares no body, got " + ct);
                return r;
            }
            List<String> produces = strings(op.json.has("produces") ? op.json.get("produces") : spec.root.path("produces"));
            if (!produces.isEmpty() && hasBody) {
                if (ct.isEmpty()) r.failures.add("no Content-Type (declared: " + String.join(", ", produces) + ")");
                else if (match(produces, ct) == null) r.failures.add("Content-Type " + ct + " is not declared (declared: " + String.join(", ", produces) + ")");
            }
            schema = new SpecDoc.Node(resp.json.get("schema"), resp.doc, null);
            json = ct.isEmpty() ? looksJson(body) : isJson(ct);
        } else {
            JsonNode content = resp.json.path("content");
            List<String> declared = new ArrayList<String>();
            content.fieldNames().forEachRemaining(declared::add);
            if (declared.isEmpty()) {
                if (hasBody && !ct.isEmpty()) r.failures.add("status " + key + " declares no body, got " + ct);
                return r;
            }
            if (!hasBody && ct.isEmpty()) return r;   // e.g. HEAD-like or empty 200: nothing to compare
            if (ct.isEmpty()) {
                r.failures.add("no Content-Type (declared: " + String.join(", ", declared) + ")");
                return r;
            }
            String mt = match(declared, ct);
            if (mt == null) {
                r.failures.add("Content-Type " + ct + " is not declared (declared: " + String.join(", ", declared) + ")");
                return r;
            }
            JsonNode s = content.path(mt).get("schema");
            if (s != null) schema = new SpecDoc.Node(s, resp.doc, null);
            json = isJson(ct);
        }
        if (schema == null || !json) return r;
        if (!hasBody) {
            r.failures.add("/: empty body (expected JSON matching the " + key + " schema, actual nothing)");
            return r;
        }
        JsonNode inst;
        try {
            inst = OM.readTree(body);
        } catch (Exception e) {
            r.failures.add("/: body is not valid JSON (expected JSON, actual " + firstLine(e.getMessage()) + ")");
            return r;
        }
        for (SchemaValidator.Violation v : new SchemaValidator(spec, true).validate(schema, inst)) r.failures.add(v.toString());
        return r;
    }

    /** exact code, then {@code 4XX}-style range, then {@code default}; null when none is declared. */
    static String responseKey(JsonNode responses, int status) {
        String code = String.valueOf(status);
        if (responses.has(code)) return code;
        for (Iterator<String> it = responses.fieldNames(); it.hasNext();) {
            String k = it.next();
            if (k.length() == 3 && k.substring(1).equalsIgnoreCase("XX") && k.charAt(0) == code.charAt(0)) return k;
        }
        return responses.has("default") ? "default" : null;
    }

    /** The declared media type the actual one satisfies ({@code application/*}, {@code *}{@code /*} wildcards). */
    static String match(List<String> declared, String actual) {
        for (String d : declared) if (mediaType(d).equals(actual)) return d;
        String type = actual.contains("/") ? actual.substring(0, actual.indexOf('/')) : actual;
        for (String d : declared) if (mediaType(d).equals(type + "/*")) return d;
        for (String d : declared) if (mediaType(d).equals("*/*")) return d;
        return null;
    }

    static String mediaType(String ct) {
        if (ct == null) return "";
        int semi = ct.indexOf(';');
        return (semi < 0 ? ct : ct.substring(0, semi)).trim().toLowerCase(Locale.ROOT);
    }

    static boolean isJson(String mt) {
        return mt.equals("application/json") || mt.endsWith("+json") || mt.endsWith("/json");
    }

    private static boolean looksJson(String body) {
        String t = body == null ? "" : body.trim();
        return t.startsWith("{") || t.startsWith("[");
    }

    private static List<String> strings(JsonNode arr) {
        List<String> out = new ArrayList<String>();
        for (JsonNode n : arr) out.add(n.asText());
        return out;
    }

    private static String firstLine(String s) {
        if (s == null) return "unparseable";
        int nl = s.indexOf('\n');
        return nl < 0 ? s : s.substring(0, nl);
    }
}
