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
package com.gatf.ai.heal;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * H1 (Δ150): Accept for a heal proposal shown in the editor. The browser never sends the lines to write: a heal
 * leaves a {@link Ticket} (the script, the failing line as it was, and the {@link Healer}'s proposal lines) under a
 * random id, and Accept names the ticket and a rank. {@link #apply} then
 * <ol>
 * <li>re-reads the file; if the target line is gone or differs from the line that was healed, it is a
 * {@code conflict} and nothing is written;</li>
 * <li>replaces exactly that line with the proposal's line(s), keeping every other byte (line endings included);</li>
 * <li>validates the result with the caller's {@link Validator} ({@code Command.validateSel}) on a temporary sibling
 * file, removed afterwards; a {@code FAILURE} (or a validator that cannot run) is {@code invalid} and nothing is
 * written;</li>
 * <li>writes only if the file is still byte-for-byte what was read, and returns the new content for the editor.</li>
 * </ol>
 * One apply at a time per JVM. Pure (Jackson + java.nio).
 */
public final class HealApply {

    private HealApply() {}

    /** Validates a candidate script on disk; returns {@code Command.validateSel}'s JSON ({@code {status, error}}). */
    public interface Validator {
        String validate(Path candidate) throws Exception;
    }

    private static final Object LOCK = new Object();
    private static final SecureRandom RND = new SecureRandom();

    /** What a heal proposed for one script line. */
    public static final class Ticket {
        public final Path file;
        public final String display;
        public final int line;
        public final String step;
        public final List<List<String>> proposals;
        final long at;

        Ticket(Path file, String display, int line, String step, List<List<String>> proposals, long at) {
            this.file = file;
            this.display = display;
            this.line = line;
            this.step = step;
            this.proposals = proposals;
            this.at = at;
        }

        /**
         * A ticket for a {@link HealRun#heal} result, or null when nothing can be accepted (no script, no line, no
         * proposals). The proposal lines are copied from the result the server built.
         */
        @SuppressWarnings("unchecked")
        public static Ticket of(HealRun.Script script, Map<String, Object> heal) {
            if (script == null || script.path == null || heal == null || !"proposed".equals(heal.get("status"))) return null;
            Object ln = heal.get("line");
            if (!(ln instanceof Integer) || (Integer) ln < 1 || (Integer) ln > script.lines.size()) return null;
            List<List<String>> ps = new ArrayList<List<String>>();
            for (Map<String, Object> p : (List<Map<String, Object>>) heal.get("proposals")) {
                ps.add(Collections.unmodifiableList(new ArrayList<String>((List<String>) p.get("lines"))));
            }
            if (ps.isEmpty()) return null;
            int line = (Integer) ln;
            return new Ticket(script.path, script.display, line, script.lines.get(line - 1), Collections.unmodifiableList(ps), System.currentTimeMillis());
        }
    }

    /** Recent tickets by id: at most {@link #MAX} kept, each for {@link #TTL_MS}. Thread-safe. */
    public static final class Tickets {
        static final int MAX = 64;
        static final long TTL_MS = 60L * 60 * 1000;
        private final Map<String, Ticket> map = new LinkedHashMap<String, Ticket>();

        public synchronized String put(Ticket t) {
            prune(System.currentTimeMillis());
            byte[] b = new byte[16];
            RND.nextBytes(b);
            StringBuilder id = new StringBuilder();
            for (byte x : b) id.append(String.format("%02x", x & 0xff));
            map.put(id.toString(), t);
            while (map.size() > MAX) {
                Iterator<String> it = map.keySet().iterator();
                it.next();
                it.remove();
            }
            return id.toString();
        }

        public synchronized Ticket get(String id) {
            prune(System.currentTimeMillis());
            return id == null ? null : map.get(id);
        }

        synchronized void prune(long now) {
            map.values().removeIf(t -> now - t.at > TTL_MS);
        }
    }

    /** One line and its terminator ("" for a last line without one). */
    static final class Line {
        final String text, eol;
        Line(String text, String eol) { this.text = text; this.eol = eol; }
    }

    static List<Line> split(String s) {
        List<Line> out = new ArrayList<Line>();
        int i = 0, start = 0, n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (c == '\n' || c == '\r') {
                int e = c == '\r' && i + 1 < n && s.charAt(i + 1) == '\n' ? i + 2 : i + 1;
                out.add(new Line(s.substring(start, i), s.substring(i, e)));
                i = start = e;
            } else {
                i++;
            }
        }
        if (start < n) out.add(new Line(s.substring(start), ""));
        return out;
    }

    /** The file's usual line ending (the first one), for lines added after a last line that had none. */
    static String eolOf(List<Line> ls) {
        for (Line l : ls) if (!l.eol.isEmpty()) return l.eol;
        return "\n";
    }

    /** {@code content} with line {@code lineNo} (1-based) replaced by {@code repl}; every other byte kept. */
    static String replace(String content, int lineNo, List<String> repl) {
        List<Line> ls = split(content);
        String eol = eolOf(ls);
        StringBuilder b = new StringBuilder(content.length() + 64);
        for (int i = 0; i < ls.size(); i++) {
            Line l = ls.get(i);
            if (i == lineNo - 1) {
                for (int k = 0; k < repl.size(); k++) {
                    b.append(repl.get(k));
                    b.append(k < repl.size() - 1 ? (l.eol.isEmpty() ? eol : l.eol) : l.eol);
                }
            } else {
                b.append(l.text).append(l.eol);
            }
        }
        return b.toString();
    }

    static boolean oneLine(String s) {
        if (s == null) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\n' || c == '\r' || c == '\u2028' || c == '\u2029' || c == '\u0085' || c == 0) return false;
        }
        return true;
    }

    /**
     * Applies proposal {@code rank} (1-based) of {@code t}.
     * @return {@code {status: applied|conflict|invalid, file, line, ...}}: {@code applied} carries {@code content}
     *         (the new file text) and {@code lines}; {@code conflict} carries {@code expected}/{@code actual};
     *         {@code invalid} carries {@code validation}
     */
    public static Map<String, Object> apply(Ticket t, int rank, Validator v, ObjectMapper om) throws IOException {
        if (t == null) throw new HealRun.BadInput("unknown or expired heal: heal again");
        if (rank < 1 || rank > t.proposals.size()) throw new HealRun.BadInput("rank must be 1-" + t.proposals.size());
        List<String> repl = t.proposals.get(rank - 1);
        if (repl.isEmpty()) throw new HealRun.BadInput("empty proposal");
        for (String l : repl) if (!oneLine(l)) throw new HealRun.BadInput("proposal is not single lines");
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("file", t.display);
        out.put("line", t.line);
        out.put("rank", rank);
        synchronized (LOCK) {
            if (!Files.isRegularFile(t.file, LinkOption.NOFOLLOW_LINKS)) return conflict(out, t, null, "the file is gone or no longer a regular file");
            byte[] before = Files.readAllBytes(t.file);
            String text = new String(before, StandardCharsets.UTF_8);
            List<Line> ls = split(text);
            String actual = t.line <= ls.size() ? ls.get(t.line - 1).text : null;
            if (!t.step.equals(actual)) return conflict(out, t, actual, "line " + t.line + " changed since the heal");
            String next = replace(text, t.line, repl);
            byte[] bytes = next.getBytes(StandardCharsets.UTF_8);

            String name = t.file.getFileName().toString();
            String stem = name.toLowerCase(java.util.Locale.ROOT).endsWith(".sel") ? name.substring(0, name.length() - 4) : name;
            Path cand = t.file.resolveSibling("." + stem + ".heal-" + Long.toHexString(RND.nextLong() & Long.MAX_VALUE) + ".sel");
            String res;
            try {
                Files.write(cand, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                res = v.validate(cand);
            } catch (Exception e) {
                res = null;
                out.put("status", "invalid");
                out.put("validation", "validation unavailable: " + e.getClass().getSimpleName()
                        + (e.getMessage() == null ? "" : ": " + HealRun.cap(e.getMessage(), 300)).replace(cand.getFileName().toString(), name));
                return out;
            } finally {
                Files.deleteIfExists(cand);
            }
            String shown = res == null ? "" : res.replace(cand.getFileName().toString(), name);
            Object vstatus = null;
            Object validation = shown;
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = om.readValue(shown.trim(), Map.class);
                vstatus = m.get("status");
                validation = m;
            } catch (IOException | RuntimeException e) { /* not JSON: kept as text */ }
            out.put("validation", validation);
            if (!"SUCCESS".equals(vstatus)) {
                out.put("status", "invalid");
                return out;
            }
            if (!Files.isRegularFile(t.file, LinkOption.NOFOLLOW_LINKS) || !Arrays.equals(before, Files.readAllBytes(t.file))) {
                return conflict(out, t, null, "the file changed while the fix was validated");
            }
            Files.write(t.file, bytes, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            out.put("status", "applied");
            out.put("lines", repl);
            out.put("content", next);
            return out;
        }
    }

    private static Map<String, Object> conflict(Map<String, Object> out, Ticket t, String actual, String why) {
        out.put("status", "conflict");
        out.put("reason", why);
        out.put("expected", t.step);
        out.put("actual", actual);
        return out;
    }
}
