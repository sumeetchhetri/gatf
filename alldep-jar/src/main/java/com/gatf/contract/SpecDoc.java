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

import java.io.File;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.gatf.imports.OpenApiImporter;

/**
 * OA2 — an OpenAPI/Swagger document plus the files its relative {@code $ref}s reach, with a cycle-guarded resolver.
 * Loaded specs are cached by path + modification time, so a run parses each spec once. Pure: Jackson only.
 */
public final class SpecDoc {

    /** A node and the document (absolute path, "" = root) it came from. */
    public static final class Node {
        public final JsonNode json;
        public final String doc;
        public final String key;   // doc#pointer when reached through a $ref, else null
        Node(JsonNode json, String doc, String key) { this.json = json; this.doc = doc; this.key = key; }
    }

    private static final Map<String, Object[]> CACHE = new ConcurrentHashMap<String, Object[]>();

    public final JsonNode root;
    private final File baseDir;
    private final Map<String, JsonNode> docs = new HashMap<String, JsonNode>();

    public SpecDoc(JsonNode root, File baseDir) {
        this.root = root;
        this.baseDir = baseDir;
        docs.put("", root);
    }

    /** Parses (JSON or YAML) and caches by path + mtime. */
    public static SpecDoc load(File f) throws Exception {
        String k = f.getCanonicalPath();
        long m = f.lastModified();
        Object[] c = CACHE.get(k);
        if (c != null && ((Long) c[0]) == m) return (SpecDoc) c[1];
        SpecDoc d = new SpecDoc(OpenApiImporter.parse(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8)),
                f.getCanonicalFile().getParentFile());
        CACHE.put(k, new Object[] { m, d });
        return d;
    }

    public boolean isV2() {
        return root.path("swagger").asText("").startsWith("2");
    }

    public Node at(String pointer) {
        JsonNode n = pointer == null || pointer.isEmpty() ? root
                : pointer.startsWith("/") ? root.at(pointer) : com.fasterxml.jackson.databind.node.MissingNode.getInstance();
        return new Node(n, "", null);
    }

    public Node node(JsonNode n, String doc) {
        return new Node(n, doc, null);
    }

    /** Follows a $ref chain; returns null when unresolvable or looping. Non-ref nodes are returned as they are. */
    public synchronized Node deref(Node n) {
        Node cur = n;
        Set<String> seen = new LinkedHashSet<String>();
        while (cur != null && cur.json != null && cur.json.isObject() && cur.json.has("$ref")) {
            String ref = cur.json.get("$ref").asText("");
            int h = ref.indexOf('#');
            String file = h < 0 ? ref : ref.substring(0, h);
            String pointer = h < 0 ? "" : ref.substring(h + 1);
            String doc = cur.doc;
            if (!file.isEmpty()) {
                if (file.matches("^[A-Za-z][A-Za-z0-9+.-]*://.*")) return null;
                File base = doc.isEmpty() ? baseDir : new File(doc).getParentFile();
                if (base == null) return null;
                try {
                    File f = new File(base, file);
                    doc = f.getCanonicalPath();
                    if (!docs.containsKey(doc)) {
                        if (!f.isFile()) return null;
                        docs.put(doc, OpenApiImporter.parse(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8)));
                    }
                } catch (Exception e) {
                    return null;
                }
            }
            String key = doc + "#" + pointer;
            if (!seen.add(key)) return null;
            JsonNode d = docs.get(doc);
            JsonNode t = resolve(d, pointer);
            if (t == null || t.isMissingNode()) return null;
            cur = new Node(t, doc, key);
        }
        return cur;
    }

    /**
     * The {@code $ref} fragment's target in {@code doc}: "" = the document, else a percent-decoded RFC 6901 pointer
     * ('+' kept literal). Null for a non-pointer fragment (a 3.1 {@code $anchor} such as {@code #pet}) — not supported.
     */
    public static JsonNode resolve(JsonNode doc, String fragment) {
        if (fragment.isEmpty()) return doc;
        if (!fragment.startsWith("/")) return null;
        String p = fragment;
        if (p.indexOf('%') >= 0) {
            try {
                p = URLDecoder.decode(p.replace("+", "%2B"), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                // malformed %-escape: used as written
            }
        }
        return doc.at(p);
    }
}
