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
package com.gatf.export;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Δ160 — one exported file plus its report: every statement that was not carried over (UNSUPPORTED, also emitted
 * as a {@code TODO(gatf-export)} comment) and every place where the target framework behaves differently (NOTE).
 * The report holds statement text and names only: environment variables are listed by name, never by value.
 */
public final class ExportResult {

    public enum Level { UNSUPPORTED, NOTE }

    public static final class Item {
        public final Level level;
        public final int line;
        /** The statement as written (trimmed), or a short construct name. */
        public final String construct;
        public final String reason;

        Item(Level level, int line, String construct, String reason) {
            this.level = level;
            this.line = line;
            this.construct = construct;
            this.reason = reason;
        }

        @Override
        public String toString() {
            return (line > 0 ? "#" + line + " " : "") + construct + " — " + reason;
        }
    }

    public String scriptName;
    public String fileName;
    public String content;
    public final List<Item> items = new ArrayList<Item>();
    /** {@code ${name}} reference → environment-variable name, sorted by name. */
    public final Map<String, String> env = new TreeMap<String, String>();
    public final Map<String, Integer> stats = new LinkedHashMap<String, Integer>();

    public void unsupported(int line, String construct, String reason) {
        items.add(new Item(Level.UNSUPPORTED, line, construct, reason));
    }

    public void note(int line, String construct, String reason) {
        items.add(new Item(Level.NOTE, line, construct, reason));
    }

    /** The items of one level, by line. */
    public List<Item> items(Level level) {
        List<Item> out = new ArrayList<Item>();
        for (Item i : items) if (i.level == level) out.add(i);
        out.sort((a, b) -> Integer.compare(a.line, b.line));
        return out;
    }

    /** The plain-text report: counts, the environment mapping (names only), unsupported lines, then notes. */
    public String report() {
        StringBuilder b = new StringBuilder();
        b.append(scriptName).append(" → ").append(fileName).append("  ").append(stats).append('\n');
        if (!env.isEmpty()) {
            b.append("  environment (names only):\n");
            for (Map.Entry<String, String> e : env.entrySet()) b.append("    ${").append(e.getKey()).append("} → ").append(e.getValue()).append('\n');
        }
        List<Item> u = items(Level.UNSUPPORTED);
        if (!u.isEmpty()) {
            b.append("  ").append(u.size()).append(" unsupported (exported as TODO(gatf-export) comments):\n");
            for (Item i : u) b.append("   ! ").append(i).append('\n');
        }
        List<Item> n = items(Level.NOTE);
        if (!n.isEmpty()) {
            b.append("  ").append(n.size()).append(" note(s) on behaviour that differs:\n");
            for (Item i : n) b.append("   ~ ").append(i).append('\n');
        }
        return b.toString();
    }
}
