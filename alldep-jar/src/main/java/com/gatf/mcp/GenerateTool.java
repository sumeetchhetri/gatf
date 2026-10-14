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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatf.ai.AiClient;
import com.gatf.ai.gen.GenPlan;
import com.gatf.ai.gen.Generator;
import com.gatf.ai.gen.SelGate;

/**
 * H1 (Δ152): the MCP {@code generate} tool. Input {@code {plan, config?, ai?, includeSideEffects?}}; output the
 * {@link Generator} summary with each script's text and gate results (lint + dry run). Read-only like {@code heal}
 * and {@code plan}: it writes nothing in the project (the gate's scratch copies live in a private temp dir that is
 * deleted afterwards); an agent saves the scripts that passed itself. {@code plan} and {@code config} are confined to
 * the project root exactly as {@link HealTool}'s paths are.
 */
public final class GenerateTool {

    private final Path root;
    private final ObjectMapper om;
    private final Function<String, AiClient> ai;
    private final BiFunction<Path, Path, SelGate> gates;
    private final HealTool paths;

    public GenerateTool(Path root, ObjectMapper om, Function<String, AiClient> ai, BiFunction<Path, Path, SelGate> gates) {
        this.root = root.toAbsolutePath().normalize();
        this.om = om;
        this.ai = ai;
        this.gates = gates;
        this.paths = new HealTool(this.root, om, c -> null);   // only for its root-confinement rule
    }

    public McpTool tool() {
        return McpTool.of("generate", "Turn a test plan (<n>-plan.json from plan / gatf plan) into seleasy scripts by a fixed mapping of its "
                        + "closed-set steps; each script is linted (validateSel) and the suite dry-run compiled; failing scripts get a bounded "
                        + "AI repair of the plan (closed-set edits) when AI is configured. Writes nothing in the project: returns the scripts' "
                        + "text and gate results; save the passing ones yourself.",
                McpTool.schema("plan*", "string", "the gatf-test-plan v1 JSON, inside the project root",
                        "config", "string", "config file for the gate and the <ai> block (default gatf-config.json, else gatf-config.xml)",
                        "ai", "boolean", "let the configured model repair failing scripts by editing the plan (default true)",
                        "includeSideEffects", "boolean", "also generate journeys that click buttons or submit non-GET forms (default false)"),
                this::call);
    }

    Object call(Map<String, Object> a) throws Exception {
        Path plan = paths.inside(McpTool.req(a, "plan"));
        if (plan == null || !Files.isRegularFile(plan) || !plan.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json")) {
            throw new McpTool.ToolError("plan must be an existing .json test plan inside the project root");
        }
        String cfgArg = McpTool.str(a, "config");
        Path cfg;
        if (cfgArg != null) {
            cfg = paths.inside(cfgArg);
            if (cfg == null || !Files.isRegularFile(cfg)) throw new McpTool.ToolError("config must be a file inside the project root");
        } else {
            cfg = paths.inside("gatf-config.json");
            if (cfg == null || !Files.isRegularFile(cfg)) cfg = paths.inside("gatf-config.xml");
            if (cfg == null || !Files.isRegularFile(cfg)) throw new McpTool.ToolError("no config: gatf-config.json / gatf-config.xml in the project root, or pass config (the gate needs one)");
        }
        GenPlan p;
        try {
            p = GenPlan.read(plan, om);
        } catch (GenPlan.Bad e) {
            throw new McpTool.ToolError(e.getMessage());
        }
        Generator.Options o = new Generator.Options();
        Boolean useAi = McpTool.bool(a, "ai");
        o.ai = useAi == null || useAi;
        o.includeSideEffects = Boolean.TRUE.equals(McpTool.bool(a, "includeSideEffects"));
        String name = plan.getFileName().toString().replaceAll("(?i)\\.json$", "").replaceAll("(?i)-plan$", "");
        Generator.Result r = Generator.generate(p, GenPlan.safeId(name), gates.apply(root.toRealPath(), cfg), o.ai ? ai.apply(cfgArg) : null, o, om);
        Map<String, Object> out = r.json(true);
        out.put("plan", root.toRealPath().relativize(plan).toString().replace('\\', '/'));
        out.put("suggestedDir", "tests/generated/" + GenPlan.safeId(name));
        out.put("writes", "nothing in the project: save the scripts with status pass yourself (or use `gatf generate`)");
        return out;
    }
}
