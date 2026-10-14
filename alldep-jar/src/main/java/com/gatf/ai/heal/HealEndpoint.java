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
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatf.ai.AiClient;

/**
 * H1 (Δ150): the editor's heal endpoints, {@code POST /record?action=heal} and {@code POST /record?action=healApply},
 * as pure logic (the Grizzly handler only builds this from the config and writes the {@link Reply}).
 * <ul>
 * <li>{@code heal} body {@code {trace?, file?, step?}}: {@code trace} is the Reports URL ({@code /reports/<rel>}) or a
 * path under the reports dir; {@code file} a .sel under the test dir. With only {@code file}, the newest failing traced
 * run of that script is used; with only {@code trace}, its recorded script ({@code selFile}). {@code file} wins when
 * both are given. The reply is the {@link Healer} JSON plus {@code file}, {@code line}, {@code trace} and, when a
 * proposal can be accepted, {@code healId}. Nothing is written.</li>
 * <li>{@code healApply} body {@code {healId, rank}}: {@link HealApply#apply}; 200 applied, 409 conflict, 422 invalid.</li>
 * </ul>
 * Every path is confined to its directory after symlinks are resolved.
 */
public final class HealEndpoint {

    static final int MAX_BODY = 64 * 1024;

    /** An HTTP status and a JSON-serialisable body. */
    public static final class Reply {
        public final int status;
        public final Object body;
        Reply(int status, Object body) { this.status = status; this.body = body; }
    }

    private final Path outDir;
    private final List<Path> scriptBases;
    private final Supplier<AiClient> ai;
    private final HealApply.Validator validator;
    private final HealApply.Tickets tickets;
    private final ObjectMapper om;
    private final String testIdAttribute;

    /**
     * @param outDir      the reports dir (traces live under it)
     * @param scriptBases where scripts live, first = the test-case dir (the editor's paths are relative to it); a
     *                    trace's {@code selFile} is tried against each in order
     */
    public HealEndpoint(Path outDir, List<Path> scriptBases, Supplier<AiClient> ai, HealApply.Validator validator,
                        HealApply.Tickets tickets, ObjectMapper om, String testIdAttribute) {
        this.outDir = outDir;
        this.scriptBases = scriptBases;
        this.ai = ai;
        this.validator = validator;
        this.tickets = tickets;
        this.om = om;
        this.testIdAttribute = testIdAttribute;
    }

    /** The JSON object body (at most {@link #MAX_BODY} bytes). */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> readBody(InputStream in, ObjectMapper om) throws IOException {
        byte[] b = in == null ? new byte[0] : in.readNBytes(MAX_BODY + 1);
        if (b.length > MAX_BODY) throw new HealRun.BadInput("request body too large");
        if (b.length == 0) return new LinkedHashMap<String, Object>();
        Object o;
        try {
            o = om.readValue(b, Object.class);
        } catch (IOException e) {
            throw new HealRun.BadInput("request body must be a JSON object");
        }
        if (!(o instanceof Map)) throw new HealRun.BadInput("request body must be a JSON object");
        return (Map<String, Object>) o;
    }

    public Reply heal(Map<String, Object> body) {
        try {
            String traceArg = str(body, "trace"), fileArg = str(body, "file"), step = str(body, "step");
            Path base0 = scriptBases.isEmpty() ? null : real(scriptBases.get(0));
            HealRun.Script script = null;
            String fileSource = null;
            if (fileArg != null) {
                Path f = confined(base0, fileArg, "file");
                if (f == null || !isSel(f)) return bad("file must be an existing .sel under the test directory");
                script = HealRun.readScript(f, display(base0, f));
                fileSource = "argument";
            }
            Path trace;
            if (traceArg != null) {
                String rel = traceArg.replaceFirst("^/?reports/", "");
                trace = confined(real(outDir), rel, "trace");
                if (trace == null) return bad("trace must be a trace zip (or folder) under the reports directory");
            } else if (script != null) {
                final Path want = script.path;
                trace = HealRun.latestFailingTrace(real(outDir), s -> want.equals(resolveSelFile(s)), om);
                if (trace == null) return new Reply(404, err("no failing traced run of " + script.display + " under the reports directory: run it with trace on, then heal"));
            } else {
                return bad("pass trace, file or both");
            }
            if (script == null) {
                String sel = HealRun.selFile(trace, om);
                Path f = sel == null ? null : resolveSelFile(sel);
                if (f != null && (base0 == null || !f.startsWith(base0))) f = null;   // the editor opens test-dir paths only
                if (f == null) return bad("the trace's script " + (sel == null ? "is not recorded" : "'" + HealRun.cap(sel, 200) + "' is not under the test directory") + ": open the script in the editor and heal from there");
                script = HealRun.readScript(f, display(base0, f));
                fileSource = "trace";
            }
            Healer.Options o = new Healer.Options();
            if (testIdAttribute != null && !testIdAttribute.isBlank()) o.testIdAttribute = testIdAttribute.trim();
            Map<String, Object> r = HealRun.heal(trace, script, step, ai.get(), o, om);
            r.put("fileSource", fileSource);
            r.put("trace", display(real(outDir), trace));
            HealApply.Ticket t = HealApply.Ticket.of(script, r);
            if (t != null) r.put("healId", tickets.put(t));
            return new Reply(200, r);
        } catch (HealRun.BadInput e) {
            return bad(e.getMessage());
        } catch (IOException e) {
            return new Reply(500, err("heal failed: " + e.getClass().getSimpleName()));
        }
    }

    public Reply apply(Map<String, Object> body) {
        try {
            String id = str(body, "healId");
            if (id == null || !id.matches("[0-9a-f]{32}")) return bad("healId is required");
            Object rk = body.get("rank");
            int rank;
            if (rk instanceof Integer) rank = (Integer) rk;
            else if (rk instanceof String && ((String) rk).matches("\\d{1,3}")) rank = Integer.parseInt((String) rk);
            else return bad("rank must be a number");
            HealApply.Ticket t = tickets.get(id);
            if (t == null) return new Reply(410, err("this heal expired or is unknown: heal again"));
            Map<String, Object> r = HealApply.apply(t, rank, validator, om);
            Object s = r.get("status");
            return new Reply("applied".equals(s) ? 200 : "conflict".equals(s) ? 409 : 422, r);
        } catch (HealRun.BadInput e) {
            return bad(e.getMessage());
        } catch (IOException e) {
            return new Reply(500, err("apply failed: " + e.getClass().getSimpleName()));
        }
    }

    // ---- paths ----

    /** A .sel named by a trace's {@code selFile}, under one of the script bases, or null. */
    Path resolveSelFile(String sel) {
        if (sel == null || sel.indexOf('\0') >= 0) return null;
        try {
            Path p = Paths.get(sel);
            for (Path b : scriptBases) {
                Path rb = real(b);
                if (rb == null) continue;
                Path f = p.isAbsolute() ? existingInside(rb, p) : confined(rb, sel, null);
                if (f != null && isSel(f)) return f;
            }
        } catch (InvalidPathException | HealRun.BadInput e) {
            return null;
        }
        return null;
    }

    static boolean isSel(Path f) {
        return f.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".sel") && Files.isRegularFile(f);
    }

    static Path real(Path p) {
        try {
            return p == null ? null : p.toRealPath();
        } catch (IOException e) {
            return null;
        }
    }

    /** {@code rel} inside {@code base}: no traversal, no absolute path, and the real (symlink-resolved) path inside too. */
    static Path confined(Path base, String rel, String what) {
        if (base == null || rel == null || rel.indexOf('\0') >= 0) return null;
        try {
            Path r = Paths.get(rel);
            if (r.isAbsolute()) return null;
            Path p = base.resolve(r).normalize();
            if (!p.startsWith(base)) return null;
            return existingInside(base, p);
        } catch (InvalidPathException e) {
            return null;
        }
    }

    private static Path existingInside(Path base, Path p) {
        try {
            if (!Files.exists(p)) return null;
            Path real = p.toRealPath();
            return real.startsWith(base) ? real : null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    static String display(Path base, Path f) {
        if (base == null || f == null) return f == null ? null : f.getFileName().toString();
        return base.relativize(f).toString().replace('\\', '/');
    }

    static String str(Map<String, Object> m, String k) {
        Object v = m == null ? null : m.get(k);
        if (v == null) return null;
        if (!(v instanceof String) && !(v instanceof Number)) throw new HealRun.BadInput(k + " must be a string");
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    private static Reply bad(String msg) {
        return new Reply(400, err(msg));
    }

    static Map<String, Object> err(String msg) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("error", msg);
        return m;
    }
}
