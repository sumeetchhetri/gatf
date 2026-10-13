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
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatf.ai.AiClient;
import com.gatf.ai.plan.CrawlMap;
import com.gatf.ai.plan.Planner;

/**
 * H1 (Δ151): the MCP {@code plan} tool. Input {@code {crawl, config?, ai?, maxRoutes?, coverageRun?, markdown?}};
 * output {@code {plan, markdown?, writes}}: the {@code gatf-test-plan} v1 JSON for a saved crawl map, every crawled
 * route planned, the configured model only enriching. Read-only: it never writes a file. Paths ({@code crawl},
 * {@code config}, {@code coverageRun}) are confined to the project root exactly as {@link HealTool}'s are.
 */
public final class PlanTool {

    private final Path root;
    private final ObjectMapper om;
    private final Function<String, AiClient> ai;
    private final HealTool paths;

    public PlanTool(Path root, ObjectMapper om, Function<String, AiClient> ai) {
        this.root = root.toAbsolutePath().normalize();
        this.om = om;
        this.ai = ai;
        this.paths = new HealTool(this.root, om, c -> null);   // only for its root-confinement rule
    }

    public McpTool tool() {
        return McpTool.of("plan", "Turn a saved crawl map (<n>-coverage.json from crawl, or crawl-coverage.json) into a test plan: "
                        + "every crawled route with its elements (crawl-ranked locators) and journeys as closed-set steps "
                        + "(navigate/click/fill/select/check/assert-*), deterministic first, enriched by the configured AI model. Never writes.",
                McpTool.schema("crawl*", "string", "the crawl map JSON, inside the project root",
                        "config", "string", "config file for the <ai> block (default gatf-config.json, else gatf-config.xml)",
                        "ai", "boolean", "ask the configured model to enrich the plan (default true; false = deterministic only)",
                        "maxRoutes", "integer", "plan at most this many routes (default " + com.gatf.ai.plan.TestPlan.DEFAULT_MAX_ROUTES + "); the rest are listed as unplanned",
                        "coverageRun", "string", "a run folder with trace coverage, to mark which routes no test visits",
                        "markdown", "boolean", "also return the Markdown view (default false)"),
                this::call);
    }

    Object call(Map<String, Object> a) throws Exception {
        Path crawl = paths.inside(McpTool.req(a, "crawl"));
        if (crawl == null || !Files.isRegularFile(crawl) || !crawl.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json")) {
            throw new McpTool.ToolError("crawl must be an existing .json crawl map inside the project root");
        }
        String cfg = McpTool.str(a, "config");
        if (cfg != null && paths.inside(cfg) == null) throw new McpTool.ToolError("config must be a file inside the project root");
        Planner.Options o = new Planner.Options();
        o.maxRoutes = McpTool.num(a, "maxRoutes", o.maxRoutes, 1, CrawlMap.MAX_ROUTES_READ);
        Boolean useAi = McpTool.bool(a, "ai");
        o.ai = useAi == null || useAi;
        String run = McpTool.str(a, "coverageRun");
        if (run != null) {
            Path r = paths.inside(run);
            if (r == null || !Files.isDirectory(r)) throw new McpTool.ToolError("coverageRun must be a run folder inside the project root");
            o.visited = Planner.visited(r, o.notes);
        }
        CrawlMap map;
        try {
            map = Planner.readMap(crawl, om);
        } catch (java.io.IOException e) {
            throw new McpTool.ToolError(e.getMessage());
        }
        Planner.Result r = Planner.plan(crawl.getFileName().toString(), map, o.ai ? ai.apply(cfg) : null, o, om);
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("plan", r.json);
        if (Boolean.TRUE.equals(McpTool.bool(a, "markdown"))) out.put("markdown", r.markdown);
        out.put("writes", "none: save the plan yourself if you want it on disk (or use `gatf plan`)");
        return out;
    }
}
