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
package com.gatf.coverage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * H6 (Δ156): a Source Map v3 decoder, just enough to map an original source line to the generated code it became.
 * Pure. Index maps ({@code sections}) and anything malformed are rejected with an {@link IllegalArgumentException};
 * the caller then maps that script at file level.
 *
 * <p>Generated positions are (line, column) with columns in UTF-16 code units, as V8 and the spec use them.
 *
 * @author Sumeet Chhetri
 */
public final class SourceMapV3 {

    /** One mapped segment: generated [genCol, endCol) on genLine (endCol -1 = to the end of the line). */
    public static final class Seg {
        public final int genLine, genCol;
        public int endCol = -1;
        Seg(int genLine, int genCol) { this.genLine = genLine; this.genCol = genCol; }
    }

    public final List<String> sources;          // raw, may hold nulls
    public final List<String> sourcesContent;   // same length as sources, nulls where absent
    public final String sourceRoot;
    public final int generatedLines;            // 1 + the highest generated line that has a segment
    private final List<NavigableMap<Integer, List<Seg>>> bySource;   // source -> original line (1-based) -> segments
    private final List<List<Seg>> byGenLine;                          // every segment (any source), per generated line

    private SourceMapV3(List<String> sources, List<String> sourcesContent, String sourceRoot, List<NavigableMap<Integer, List<Seg>>> bySource,
            List<List<Seg>> byGenLine) {
        this.sources = sources;
        this.sourcesContent = sourcesContent;
        this.sourceRoot = sourceRoot;
        this.bySource = bySource;
        this.byGenLine = byGenLine;
        this.generatedLines = byGenLine.size();
    }

    private static final int[] B64 = new int[128];
    static {
        java.util.Arrays.fill(B64, -1);
        String a = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
        for (int i = 0; i < a.length(); i++) B64[a.charAt(i)] = i;
    }

    /** Parses a v3 map. @throws IllegalArgumentException when it isn't one this decoder trusts */
    public static SourceMapV3 parse(JsonNode root) {
        if (root == null || !root.isObject()) throw new IllegalArgumentException("not a JSON object");
        if (root.has("sections")) throw new IllegalArgumentException("index maps (sections) are not supported");
        if (root.path("version").asInt(-1) != 3) throw new IllegalArgumentException("not a version 3 source map");
        JsonNode src = root.path("sources");
        if (!src.isArray()) throw new IllegalArgumentException("no sources array");
        JsonNode mappings = root.path("mappings");
        if (!mappings.isTextual()) throw new IllegalArgumentException("no mappings string");
        List<String> sources = new ArrayList<>();
        for (JsonNode s : src) sources.add(s.isTextual() ? s.asText() : null);
        List<String> contents = new ArrayList<>();
        JsonNode sc = root.path("sourcesContent");
        for (int i = 0; i < sources.size(); i++) {
            JsonNode c = sc.isArray() && i < sc.size() ? sc.get(i) : null;
            contents.add(c != null && c.isTextual() ? c.asText() : null);
        }
        String sourceRoot = root.path("sourceRoot").isTextual() ? root.path("sourceRoot").asText() : "";

        List<NavigableMap<Integer, List<Seg>>> bySource = new ArrayList<>();
        for (int i = 0; i < sources.size(); i++) bySource.add(new TreeMap<>());
        List<List<Seg>> byGenLine = new ArrayList<>();
        String m = mappings.asText();
        int genLine = 0, genCol = 0, srcIdx = 0, origLine = 0, origCol = 0, nameIdx = 0;
        List<Seg> line = new ArrayList<>();
        int[] f = new int[6];
        int pos = 0;
        while (pos <= m.length()) {
            char c = pos < m.length() ? m.charAt(pos) : ';';
            if (c == ';' || c == ',') {
                pos++;
                if (c == ';') {
                    byGenLine.add(line);
                    line = new ArrayList<>();
                    genLine++;
                    genCol = 0;
                }
                continue;
            }
            int n = 0;
            while (pos < m.length() && m.charAt(pos) != ',' && m.charAt(pos) != ';') {
                if (n >= f.length) throw new IllegalArgumentException("a segment with more than 5 fields");
                long[] r = vlq(m, pos);
                f[n++] = (int) r[0];
                pos = (int) r[1];
            }
            if (n != 1 && n != 4 && n != 5) throw new IllegalArgumentException("a segment with " + n + " fields");
            genCol += f[0];
            if (genCol < 0) throw new IllegalArgumentException("negative generated column");
            Seg seg = new Seg(genLine, genCol);
            if (!line.isEmpty() && line.get(line.size() - 1).genCol > genCol) throw new IllegalArgumentException("segments out of order");
            line.add(seg);
            if (n >= 4) {
                srcIdx += f[1];
                origLine += f[2];
                origCol += f[3];
                if (n == 5) nameIdx += f[4];
                if (srcIdx < 0 || srcIdx >= sources.size() || origLine < 0 || origCol < 0) {
                    throw new IllegalArgumentException("a segment points outside the sources");
                }
                bySource.get(srcIdx).computeIfAbsent(origLine + 1, k -> new ArrayList<>()).add(seg);
            }
        }
        // the trailing ';' handling above added the last line; drop empty trailing lines from the count
        while (!byGenLine.isEmpty() && byGenLine.get(byGenLine.size() - 1).isEmpty()) byGenLine.remove(byGenLine.size() - 1);
        for (List<Seg> l : byGenLine) {
            for (int i = 0; i + 1 < l.size(); i++) l.get(i).endCol = l.get(i + 1).genCol;
        }
        return new SourceMapV3(Collections.unmodifiableList(sources), Collections.unmodifiableList(contents), sourceRoot, bySource, byGenLine);
    }

    // one base64 VLQ value at pos -> {value, next pos}
    private static long[] vlq(String m, int pos) {
        long result = 0;
        int shift = 0;
        while (true) {
            if (pos >= m.length()) throw new IllegalArgumentException("truncated VLQ");
            char c = m.charAt(pos++);
            int d = c < 128 ? B64[c] : -1;
            if (d < 0) throw new IllegalArgumentException("bad base64 in mappings");
            result |= (long) (d & 31) << shift;
            if (shift > 32) throw new IllegalArgumentException("VLQ too large");
            if ((d & 32) == 0) break;
            shift += 5;
        }
        long v = (result & 1) == 1 ? -(result >> 1) : (result >> 1);
        if (v > Integer.MAX_VALUE || v < Integer.MIN_VALUE) throw new IllegalArgumentException("VLQ out of range");
        return new long[] {v, pos};
    }

    /** The original lines (1-based) of a source that have generated code. */
    public NavigableMap<Integer, List<Seg>> lines(int source) {
        return Collections.unmodifiableNavigableMap(bySource.get(source));
    }

    /** Every segment on a generated line (empty past the end). */
    public List<Seg> generatedLine(int genLine) {
        return genLine < byGenLine.size() ? byGenLine.get(genLine) : Collections.<Seg>emptyList();
    }
}
