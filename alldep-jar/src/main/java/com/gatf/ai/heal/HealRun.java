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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatf.ai.AiClient;

/**
 * H1 (Δ150): one heal request, shared by the editor endpoint ({@code /record?action=heal}) and the MCP {@code heal}
 * tool. It reads the run's trace ({@link TraceFailure#read}), settles which line of the script is the failing step,
 * and runs {@link Healer#heal}. It never writes anything.
 * <p>
 * The step: an explicit {@code step} (a 1-based line number, or the line's text) wins. Without one, the trace's
 * recorded failure decides: the step is the one script line whose locator token appears, as a whole token, in the
 * failure message. No recorded failure, no script, no matching line or several matching lines is a {@link BadInput}
 * that asks for {@code step}: the step is never guessed. Pure (Jackson + java.nio).
 */
public final class HealRun {

    /** Largest script read (bytes). */
    static final long MAX_SCRIPT = 2L * 1024 * 1024;
    static final long MAX_META = 1024 * 1024;
    /** Traces examined at most when looking for a script's newest failing run. */
    static final int MAX_TRACES = 200;

    private HealRun() {}

    /** A request the caller got wrong (tool error / HTTP 400); the message is safe to show. */
    public static final class BadInput extends RuntimeException {
        private static final long serialVersionUID = 1L;
        public BadInput(String msg) { super(msg); }
    }

    /** The script being healed: its path, the name shown (relative, forward slashes) and its lines. */
    public static final class Script {
        public final Path path;
        public final String display;
        public final List<String> lines;

        public Script(Path path, String display, List<String> lines) {
            this.path = path;
            this.display = display;
            this.lines = lines;
        }
    }

    /** Reads a .sel (UTF-8, at most {@link #MAX_SCRIPT} bytes) into lines; a trailing newline adds no line. */
    public static Script readScript(Path p, String display) throws IOException {
        if (Files.size(p) > MAX_SCRIPT) throw new BadInput("script too large: " + display);
        return new Script(p, display, lines(new String(Files.readAllBytes(p), StandardCharsets.UTF_8)));
    }

    /** Splits on \n, \r\n or \r; a final line ending adds no empty line. */
    static List<String> lines(String s) {
        List<String> out = new ArrayList<String>(Arrays.asList(s.split("\r\n|\r|\n", -1)));
        if (!out.isEmpty() && out.get(out.size() - 1).isEmpty()) out.remove(out.size() - 1);
        return out;
    }

    /** The resolved step: 1-based line ({@code 0} when there is no script) and its text. */
    public static final class Step {
        public final int line;
        public final String text;
        public final String source;   // "argument" | "recorded-failure"

        Step(int line, String text, String source) {
            this.line = line;
            this.text = text;
            this.source = source;
        }
    }

    /**
     * @param script  may be null (then {@code step} must be the line's text)
     * @param stepArg the {@code step} argument, or null for the recorded failure
     */
    public static Step resolveStep(Script script, String stepArg, TraceFailure f) {
        String a = stepArg == null ? null : stepArg.trim();
        if (a != null && !a.isEmpty()) {
            if (a.indexOf('\n') >= 0 || a.indexOf('\r') >= 0) throw new BadInput("step must be one line");
            if (a.matches("\\d{1,9}")) {
                if (script == null) throw new BadInput("step " + a + " is a line number, but no script resolved: pass file");
                int n = Integer.parseInt(a);
                if (n < 1 || n > script.lines.size()) throw new BadInput("step " + n + " is not a line of " + script.display + " (1-" + script.lines.size() + ")");
                return new Step(n, script.lines.get(n - 1), "argument");
            }
            if (script == null) return new Step(0, stepArg, "argument");
            List<Integer> hits = new ArrayList<Integer>();
            for (int i = 0; i < script.lines.size(); i++) if (script.lines.get(i).trim().equals(a)) hits.add(i + 1);
            if (hits.isEmpty()) throw new BadInput("step not found in " + script.display + ": pass its line number");
            if (hits.size() > 1) throw new BadInput("step occurs on lines " + hits + " of " + script.display + ": pass the line number");
            return new Step(hits.get(0), script.lines.get(hits.get(0) - 1), "argument");
        }
        if (f == null || f.failure == null) throw new BadInput("the trace has no recorded failure: pass step (a line number or the line's text)");
        if (script == null) throw new BadInput("the trace's script did not resolve: pass file, or step as the line's text");
        List<Integer> hits = new ArrayList<Integer>();
        for (int i = 0; i < script.lines.size(); i++) {
            StepLocator sl = StepLocator.find(script.lines.get(i));
            if (sl != null && containsToken(f.failure, sl.token())) hits.add(i + 1);
        }
        if (hits.isEmpty()) throw new BadInput("no line of " + script.display + " matches the recorded failure: pass step");
        if (hits.size() > 1) throw new BadInput("the recorded failure matches lines " + hits + " of " + script.display + ": pass step");
        return new Step(hits.get(0), script.lines.get(hits.get(0) - 1), "recorded-failure");
    }

    /** {@code token} in {@code text} as a whole token: not glued to a locator character on either side. */
    static boolean containsToken(String text, String token) {
        if (text == null || token == null || token.isEmpty()) return false;
        for (int i = text.indexOf(token); i >= 0; i = text.indexOf(token, i + 1)) {
            boolean before = i == 0 || !glue(text.charAt(i - 1));
            int e = i + token.length();
            boolean after = e == text.length() || !glue(text.charAt(e));
            if (before && after) return true;
        }
        return false;
    }

    private static boolean glue(char c) {
        return Character.isLetterOrDigit(c) || "_-.#@$:[]=/*".indexOf(c) >= 0;
    }

    /**
     * Heals {@code step} (resolved by {@link #resolveStep}) against the trace's failing snapshot.
     * @return the {@link Healer} JSON plus {@code file}, {@code line}, {@code stepSource}
     */
    public static Map<String, Object> heal(Path trace, Script script, String stepArg, AiClient ai, Healer.Options o, ObjectMapper om) throws IOException {
        TraceFailure f;
        try {
            f = TraceFailure.read(trace, om);
        } catch (IOException e) {
            throw new BadInput("cannot read the trace: " + e.getMessage());
        }
        Step st = resolveStep(script, stepArg, f);
        Map<String, Object> r = new Healer(om).heal(st.text, script == null ? null : script.lines, st.line,
                script == null ? null : script.display, f, ai, o);
        Map<String, Object> out = new LinkedHashMap<String, Object>(r);
        out.put("file", script == null ? null : script.display);
        out.put("line", st.line);
        out.put("stepSource", st.source);
        if (f.failure != null) out.put("failure", cap(f.failure, 500));
        return out;
    }

    static String cap(String s, int n) {
        String t = s.replaceAll("[\\x00-\\x08\\x0b\\x0c\\x0e-\\x1f\\x7f]", " ");
        return t.length() > n ? t.substring(0, n) + "…" : t;
    }

    /** The trace's {@code metadata.json} (zip or directory), or null. */
    public static JsonNode metadata(Path trace, ObjectMapper om) {
        try {
            byte[] b = null;
            if (Files.isDirectory(trace)) {
                Path m = trace.resolve("metadata.json");
                if (Files.isRegularFile(m, LinkOption.NOFOLLOW_LINKS) && Files.size(m) <= MAX_META) b = Files.readAllBytes(m);
            } else if (Files.isRegularFile(trace)) {
                try (ZipFile z = new ZipFile(trace.toFile())) {
                    ZipEntry e = z.getEntry("metadata.json");
                    if (e != null && !e.isDirectory()) {
                        try (InputStream in = z.getInputStream(e)) {
                            byte[] x = in.readNBytes((int) MAX_META + 1);
                            if (x.length <= MAX_META) b = x;
                        }
                    }
                }
            }
            if (b == null) return null;
            JsonNode n = om.readTree(b);
            return n != null && n.isObject() ? n : null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** The script name the trace was recorded for ({@code metadata.json} {@code selFile}), or null. */
    public static String selFile(Path trace, ObjectMapper om) {
        JsonNode m = metadata(trace, om);
        if (m == null || !m.path("selFile").isTextual()) return null;
        String s = m.path("selFile").asText().trim();
        return s.isEmpty() || s.indexOf('\0') >= 0 ? null : s;
    }

    /**
     * The newest {@code *-trace.zip} under {@code outDir} (depth 6, symlinks not followed, at most {@link #MAX_TRACES}
     * newest examined) whose {@code selFile} satisfies {@code forScript} and whose outcome is failed (or, for older
     * traces with no outcome, that recorded a failure). Null when there is none.
     */
    public static Path latestFailingTrace(Path outDir, Predicate<String> forScript, ObjectMapper om) throws IOException {
        if (outDir == null || !Files.isDirectory(outDir)) return null;
        List<Path> zips = new ArrayList<Path>();
        try (Stream<Path> s = Files.walk(outDir, 6, new FileVisitOption[0])) {
            s.filter(p -> p.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith("-trace.zip")
                    && Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)).forEach(zips::add);
        }
        zips.sort(Comparator.comparingLong((Path p) -> {
            try { return Files.getLastModifiedTime(p).toMillis(); } catch (IOException e) { return 0L; }
        }).reversed());
        int n = 0;
        for (Path z : zips) {
            if (n++ >= MAX_TRACES) break;
            JsonNode m = metadata(z, om);
            if (m == null || !m.path("selFile").isTextual() || !forScript.test(m.path("selFile").asText())) continue;
            String outcome = m.path("outcome").asText("");
            if (outcome.equals("failed")) return z;
            if (outcome.isEmpty()) {
                try {
                    if (TraceFailure.read(z, om).failure != null) return z;
                } catch (IOException e) { /* not a trace */ }
            }
        }
        return null;
    }
}
