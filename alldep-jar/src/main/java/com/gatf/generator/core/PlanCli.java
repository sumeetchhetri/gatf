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
package com.gatf.generator.core;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatf.ai.AiClient;
import com.gatf.ai.AiConfig;
import com.gatf.ai.plan.CrawlMap;
import com.gatf.ai.plan.Planner;

/**
 * H1 (Δ151): {@code gatf plan <crawl-coverage.json> [-o plan.json] [--md plan.md] [--config cfg] [--no-ai]
 * [--max-routes N] [--coverage-run dir] [--force]}: a crawl map (the SPA crawl's {@code <n>-coverage.json} or the
 * portal crawler's {@code crawl-coverage.json}) → a test plan as JSON ({@code gatf-test-plan} v1, the generator's
 * input) and Markdown. Every crawled route gets an entry with or without AI; the {@code <ai>} block of the config
 * (default {@code gatf-config.json}, else {@code gatf-config.xml}, in the working directory) only enriches it.
 * Outputs default to {@code <n>-plan.json} / {@code <n>-plan.md} beside the map and are never overwritten without
 * {@code --force}. Prints counts and the AI status, never page content or a key.
 */
final class PlanCli {

    static final String USAGE = "gatf plan <crawl-coverage.json> [-o plan.json] [--md plan.md] [--config cfg] [--no-ai] [--max-routes N] [--coverage-run dir] [--force]";

    /** The AI client for a config file (null = none found). Tests pass a fake. */
    interface AiOpener {
        AiClient open(File config);
    }

    private PlanCli() {}

    static int run(String[] args, File cwd, PrintStream out, PrintStream err) {
        return run(args, cwd, out, err, PlanCli::openAi);
    }

    static int run(String[] args, File cwd, PrintStream out, PrintStream err, AiOpener opener) {
        String in = null, outArg = null, mdArg = null, cfgArg = null, runArg = null;
        boolean force = false, noAi = false;
        Planner.Options o = new Planner.Options();
        try {
            for (int i = 0; i < args.length; i++) {
                String a = args[i];
                switch (a) {
                    case "-o": case "--out": outArg = value(args, ++i, a); break;
                    case "--md": mdArg = value(args, ++i, a); break;
                    case "--config": cfgArg = value(args, ++i, a); break;
                    case "--coverage-run": runArg = value(args, ++i, a); break;
                    case "--max-routes":
                        try {
                            o.maxRoutes = Integer.parseInt(value(args, ++i, a));
                        } catch (NumberFormatException e) {
                            throw new IllegalArgumentException("--max-routes needs a number");
                        }
                        if (o.maxRoutes < 1 || o.maxRoutes > CrawlMap.MAX_ROUTES_READ) throw new IllegalArgumentException("--max-routes must be 1.." + CrawlMap.MAX_ROUTES_READ);
                        break;
                    case "--no-ai": noAi = true; break;
                    case "-f": case "--force": force = true; break;
                    case "-h": case "--help": out.println(USAGE); return 0;
                    default:
                        if (a.startsWith("-")) throw new IllegalArgumentException("unknown option " + a);
                        if (in != null) throw new IllegalArgumentException("unexpected argument " + a);
                        in = a;
                }
            }
            if (in == null) throw new IllegalArgumentException("the crawl map is required");
        } catch (IllegalArgumentException e) {
            err.println("gatf plan: " + e.getMessage() + "\n" + USAGE);
            return 1;
        }
        ObjectMapper om = new ObjectMapper();
        File crawl = resolve(cwd, in);
        CrawlMap map;
        try {
            map = Planner.readMap(crawl.toPath(), om);
        } catch (IOException e) {
            err.println("gatf plan: " + crawl.getName() + ": " + e.getMessage());
            return 1;
        }
        String stem = crawl.getName().replaceAll("(?i)\\.json$", "").replaceAll("(?i)-coverage$", "");
        File jsonOut = outArg != null ? resolve(cwd, outArg) : new File(crawl.getAbsoluteFile().getParentFile(), stem + "-plan.json");
        File mdOut = mdArg != null ? resolve(cwd, mdArg) : new File(jsonOut.getAbsoluteFile().getParentFile(), jsonOut.getName().replaceAll("(?i)\\.json$", "") + ".md");
        if (jsonOut.getAbsoluteFile().equals(crawl.getAbsoluteFile()) || mdOut.getAbsoluteFile().equals(crawl.getAbsoluteFile())) {
            err.println("gatf plan: an output would overwrite the crawl map");
            return 1;
        }
        if (!force && (jsonOut.exists() || mdOut.exists())) {
            err.println("gatf plan: " + (jsonOut.exists() ? jsonOut.getName() : mdOut.getName()) + " exists (use --force to overwrite)");
            return 1;
        }
        if (runArg != null) o.visited = Planner.visited(resolve(cwd, runArg).toPath(), o.notes);
        AiClient ai = null;
        if (noAi) {
            o.ai = false;
        } else {
            File cfg = cfgArg != null ? resolve(cwd, cfgArg) : defaultConfig(cwd);
            ai = cfg == null ? AiClient.notConfigured("no config (gatf-config.json / gatf-config.xml here, or --config)")
                    : cfgArg != null && !cfg.isFile() ? AiClient.notConfigured("config " + cfg.getName() + " not found") : opener.open(cfg);
        }
        Planner.Result r = Planner.plan(crawl.getName(), map, ai, o, om);
        try {
            mkParent(jsonOut);
            mkParent(mdOut);
            Files.write(jsonOut.toPath(), om.writerWithDefaultPrettyPrinter().writeValueAsBytes(r.json));
            Files.write(mdOut.toPath(), r.markdown.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            err.println("gatf plan: could not write the plan: " + e.getClass().getSimpleName());
            return 1;
        }
        java.util.Map<?, ?> c = (java.util.Map<?, ?>) r.json.get("counts");
        out.println("plan: " + c.get("routes") + " route(s) (" + c.get("navigable") + " navigable), " + c.get("journeys") + " journey(s), "
                + c.get("aiJourneys") + " from AI" + (((Number) c.get("unplanned")).intValue() > 0 ? ", " + c.get("unplanned") + " not planned (cap)" : ""));
        out.println("AI: " + ((java.util.Map<?, ?>) r.json.get("ai")).get("status"));
        out.println("wrote " + jsonOut.getPath() + " and " + mdOut.getPath());
        return 0;
    }

    private static String value(String[] args, int i, String opt) {
        if (i >= args.length) throw new IllegalArgumentException(opt + " needs a value");
        return args[i];
    }

    private static File resolve(File cwd, String p) {
        File f = new File(p);
        return f.isAbsolute() ? f : new File(cwd, p);
    }

    private static void mkParent(File f) throws IOException {
        File d = f.getAbsoluteFile().getParentFile();
        if (d != null) Files.createDirectories(d.toPath());
    }

    static File defaultConfig(File cwd) {
        for (String n : new String[] { "gatf-config.json", "gatf-config.xml" }) {
            File f = new File(cwd, n);
            if (f.isFile()) return f;
        }
        return null;
    }

    /** The config's {@code <ai>} client, with plugin providers from {@code <config dir>/plugins}; any gap → not configured. */
    static AiClient openAi(File cfg) {
        AiConfig ai;
        try (InputStream in = Files.newInputStream(cfg.toPath())) {
            com.gatf.executor.core.GatfExecutorConfig c = com.gatf.executor.core.GatfTestCaseExecutorUtil.getConfig(in, cfg.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".xml"));
            ai = c == null ? null : c.getAi();
        } catch (Exception e) {
            return AiClient.notConfigured("config " + cfg.getName() + " could not be read");
        }
        Path dir = cfg.getAbsoluteFile().getParentFile().toPath();
        File plugins = dir.resolve("plugins").toFile();
        com.gatf.selenium.plugins.sdk.PluginRegistry.load(plugins.isDirectory() ? plugins : null, PlanCli.class.getClassLoader());
        return com.gatf.ai.heal.HealAi.open(ai, dir, com.gatf.selenium.plugins.sdk.PluginRegistry.classLoader(), System::getenv, null, null);
    }
}
