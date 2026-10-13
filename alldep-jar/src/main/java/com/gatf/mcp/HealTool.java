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
package com.gatf.mcp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatf.ai.AiClient;
import com.gatf.ai.heal.HealRun;
import com.gatf.ai.heal.Healer;

/**
 * H1 (Δ150): the MCP {@code heal} tool. Input {@code {trace, step?, file?, config?}}; output the {@link Healer} JSON
 * plus {@code file} (the script actually used, or null), {@code fileSource}, {@code line}, {@code stepSource} and
 * {@code trace}. It never writes a file: an agent applies a proposal itself and runs {@code lint}.
 * <p>
 * Paths are confined to the project root, like the other MCP readers but stricter than {@code summarise_trace}: a
 * relative or absolute path must stay inside the root after {@code ..} and symlinks are resolved. {@code file} (a .sel)
 * wins over the trace's recorded {@code selFile}; without {@code step} the trace's recorded failure picks the line
 * ({@link HealRun#resolveStep}), and anything it cannot settle is a {@link McpTool.ToolError}.
 */
public final class HealTool {

    private final Path root;
    private final ObjectMapper om;
    /** The AI client for a heal, from the {@code config} argument (null = default config). */
    private final Function<String, AiClient> ai;

    public HealTool(Path root, ObjectMapper om, Function<String, AiClient> ai) {
        this.root = root.toAbsolutePath().normalize();
        this.om = om;
        this.ai = ai;
    }

    public McpTool tool() {
        return McpTool.of("heal", "Propose fixes for a failed UI step from its trace (the failing step's DOM snapshot): ranked "
                        + "proposals, each a unified diff, deterministic first, the configured AI model second. Never writes: "
                        + "apply a proposal's lines yourself, then lint.",
                McpTool.schema("trace*", "string", "the *-trace.zip or its directory, inside the project root",
                        "step", "string", "the failing step: a 1-based line number or the line's text (default: the trace's recorded failure)",
                        "file", "string", "the .sel to heal, inside the project root (default: the script the trace recorded)",
                        "config", "string", "config file for the <ai> block (default gatf-config.json, else gatf-config.xml)"),
                this::call);
    }

    Object call(Map<String, Object> a) throws Exception {
        Path trace = inside(McpTool.req(a, "trace"));
        if (trace == null) throw new McpTool.ToolError("trace must be an existing trace zip or directory inside the project root");
        HealRun.Script script = null;
        String fileSource = null;
        String fileArg = McpTool.str(a, "file");
        if (fileArg != null) {
            Path f = inside(fileArg);
            if (f == null || !isSel(f)) throw new McpTool.ToolError("file must be an existing .sel inside the project root");
            script = HealRun.readScript(f, rel(f));
            fileSource = "argument";
        } else {
            String sel = HealRun.selFile(trace, om);
            Path f = sel == null ? null : inside(sel);
            if (f != null && isSel(f)) {
                script = HealRun.readScript(f, rel(f));
                fileSource = "trace";
            }
        }
        String cfg = McpTool.str(a, "config");
        if (cfg != null && inside(cfg) == null) throw new McpTool.ToolError("config must be a file inside the project root");
        Map<String, Object> r;
        try {
            r = HealRun.heal(trace, script, McpTool.str(a, "step"), ai.apply(cfg), null, om);
        } catch (HealRun.BadInput e) {
            throw new McpTool.ToolError(e.getMessage());
        }
        r.put("fileSource", fileSource);
        r.put("trace", rel(trace));
        r.put("writes", "none: apply a proposal's lines yourself, then lint");
        return r;
    }

    /** An existing path inside the root (relative to it, or absolute), symlinks resolved; null otherwise. */
    Path inside(String p) {
        if (p == null || p.indexOf('\0') >= 0) return null;
        try {
            Path realRoot = root.toRealPath();
            Path x = Paths.get(p);
            Path n = x.isAbsolute() ? x.normalize() : root.resolve(x).normalize();
            if (!n.startsWith(root) && !n.startsWith(realRoot)) return null;
            if (!Files.exists(n)) return null;
            Path real = n.toRealPath();
            return real.startsWith(realRoot) ? real : null;
        } catch (IOException | InvalidPathException e) {
            return null;
        }
    }

    private static boolean isSel(Path f) {
        return Files.isRegularFile(f) && f.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".sel");
    }

    private String rel(Path real) {
        try {
            return root.toRealPath().relativize(real).toString().replace('\\', '/');
        } catch (IOException e) {
            return real.getFileName().toString();
        }
    }

}
