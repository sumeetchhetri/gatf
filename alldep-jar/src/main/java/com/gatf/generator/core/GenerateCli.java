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
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatf.ai.AiClient;
import com.gatf.ai.gen.GenPlan;
import com.gatf.ai.gen.Generator;
import com.gatf.ai.gen.SelGate;

/**
 * H1 (Δ152): {@code gatf generate <n>-plan.json [-o dir] [--config cfg] [--ai|--no-ai] [--include-side-effects]
 * [--force]}: a test plan ({@code gatf-test-plan} v1, from {@code gatf plan}) → a seleasy suite, every script gated by
 * {@code validateSel} and the suite by a dry-run compile ({@link Generator}). Only scripts that pass are written, into
 * {@code tests/generated/<plan name>/} under the project root (the working directory) by default; the output dir must
 * stay inside the root (symlinks resolved), and an existing script is never overwritten without {@code --force}. The
 * config (--config, else gatf-config.json, else gatf-config.xml in the root) gives the gate its project settings and
 * the {@code <ai>} block for the repair loop; AI is on when configured, {@code --no-ai} turns it off. Prints counts,
 * the AI status and the failing scripts' errors; never the config's AI block or a key.
 */
public final class GenerateCli {

    public static final String USAGE = "gatf generate <n>-plan.json [-o dir] [--config cfg] [--ai|--no-ai] [--include-side-effects] [--force]";

    /** The gate for a project root and config; tests pass a fake. */
    public interface GateFactory {
        SelGate open(Path root, Path config);
    }

    private GenerateCli() {}

    static int run(String[] args, File cwd, PrintStream out, PrintStream err) {
        return run(args, cwd, out, err, PlanCli::openAi, (r, c) -> new com.gatf.selenium.SelSuiteGate(r, c), true);
    }

    /** Tests: {@code opener} gives the AI client for a config file, {@code gates} the gate; {@code quietGate} hides validateSel's prints. */
    public static int run(String[] args, File cwd, PrintStream out, PrintStream err, java.util.function.Function<File, AiClient> opener, GateFactory gates, boolean quietGate) {
        String in = null, outArg = null, cfgArg = null;
        boolean force = false, noAi = false, sideEffects = false;
        try {
            for (int i = 0; i < args.length; i++) {
                String a = args[i];
                switch (a) {
                    case "-o": case "--out": outArg = value(args, ++i, a); break;
                    case "--config": cfgArg = value(args, ++i, a); break;
                    case "--ai": noAi = false; break;
                    case "--no-ai": noAi = true; break;
                    case "--include-side-effects": sideEffects = true; break;
                    case "-f": case "--force": force = true; break;
                    case "-h": case "--help": out.println(USAGE); return 0;
                    default:
                        if (a.startsWith("-")) throw new IllegalArgumentException("unknown option " + a);
                        if (in != null) throw new IllegalArgumentException("unexpected argument " + a);
                        in = a;
                }
            }
            if (in == null) throw new IllegalArgumentException("the test plan is required");
        } catch (IllegalArgumentException e) {
            err.println("gatf generate: " + e.getMessage() + "\n" + USAGE);
            return 1;
        }
        Path root;
        try {
            root = cwd.toPath().toAbsolutePath().normalize().toRealPath();
        } catch (IOException e) {
            err.println("gatf generate: the working directory cannot be read");
            return 1;
        }
        ObjectMapper om = new ObjectMapper();
        Path planFile = resolve(root, in);
        GenPlan plan;
        try {
            plan = GenPlan.read(planFile, om);
        } catch (GenPlan.Bad e) {
            err.println("gatf generate: " + planFile.getFileName() + ": " + e.getMessage());
            return 1;
        }
        String planName = planName(planFile);
        Path outDir = outArg != null ? resolve(root, outArg) : root.resolve("tests").resolve("generated").resolve(planName);
        if (!insideForWrite(root, outDir)) {
            err.println("gatf generate: the output directory must be inside the project root (" + root.getFileName() + ")");
            return 1;
        }
        File cfg = cfgArg != null ? resolve(root, cfgArg).toFile() : PlanCli.defaultConfig(root.toFile());
        if (cfg == null || !cfg.isFile()) {
            err.println("gatf generate: " + (cfgArg != null ? "config " + new File(cfgArg).getName() + " not found" : "no config (gatf-config.json / gatf-config.xml here, or --config)")
                    + ": the gate needs one to lint the scripts");
            return 1;
        }
        AiClient ai = noAi ? null : opener.apply(cfg);
        Generator.Options o = new Generator.Options();
        o.ai = !noAi;
        o.includeSideEffects = sideEffects;
        Generator.Result r;
        PrintStream sysOut = System.out;
        try {
            if (quietGate) System.setOut(new PrintStream(OutputStream.nullOutputStream(), true, "UTF-8"));   // validateSel prints per script
            r = Generator.generate(plan, planName, gates.open(root, cfg.toPath()), ai, o, om);
        } catch (Exception e) {
            err.println("gatf generate: the gate failed: " + e.getClass().getSimpleName());
            return 1;
        } finally {
            System.setOut(sysOut);
        }
        List<Generator.FileResult> pass = r.passing();
        if (!force) {
            for (Generator.FileResult f : pass) {
                if (Files.exists(outDir.resolve(f.script.file), LinkOption.NOFOLLOW_LINKS)) {
                    err.println("gatf generate: " + f.script.file + " exists in the output directory (use --force to overwrite); nothing written");
                    return 1;
                }
            }
        }
        int written = 0;
        try {
            if (!pass.isEmpty()) Files.createDirectories(outDir);
            if (!pass.isEmpty() && !insideForWrite(root, outDir)) throw new IOException("output directory left the root");
            for (Generator.FileResult f : pass) {
                Path t = outDir.resolve(f.script.file).normalize();
                if (!t.getParent().equals(outDir.normalize()) || Files.isSymbolicLink(t)) throw new IOException("unsafe target");
                Files.write(t, f.script.text.getBytes(StandardCharsets.UTF_8));
                written++;
            }
        } catch (IOException e) {
            err.println("gatf generate: could not write the scripts (" + written + " written): " + e.getClass().getSimpleName());
            return 1;
        }
        printSummary(r, root, outDir, written, out);
        return r.files.size() > pass.size() ? 2 : 0;
    }

    static void printSummary(Generator.Result r, Path root, Path outDir, int written, PrintStream out) {
        Map<String, Object> j = r.json(false);
        Map<?, ?> c = (Map<?, ?>) j.get("counts");
        out.println("generate: " + c.get("scripts") + " script(s), " + c.get("passed") + " passed, " + c.get("failed") + " failed; "
                + c.get("skippedJourneys") + " journey(s) not generatable, " + c.get("sideEffectJourneysSkipped") + " with side effects skipped");
        out.println("dry run: " + j.get("dryRun"));
        Map<?, ?> ai = (Map<?, ?>) j.get("ai");
        out.println("AI: " + ai.get("status") + (Boolean.TRUE.equals(ai.get("used")) ? " (used: " + ai.get("calls") + " call(s))" : "")
                + (ai.get("stopped") != null ? "; stopped: " + ai.get("stopped") : ""));
        for (Generator.FileResult f : r.files) {
            if ("pass".equals(f.status)) continue;
            out.println("  FAILED " + f.script.file + (f.stoppedBecause != null ? " (repair stopped: " + f.stoppedBecause + ")" : ""));
            int k = 0;
            for (SelGate.Diag d : f.lint) if (k++ < 5) out.println("    lint: " + d);
            for (SelGate.Diag d : f.dryRun) if (k++ < 8) out.println("    dry run: " + d);
        }
        if (!r.sideEffectJourneys.isEmpty()) out.println("side-effect journeys skipped: " + String.join(", ", r.sideEffectJourneys) + " (--include-side-effects)");
        for (String n : r.notes) out.println("note: " + n);
        out.println(written > 0 ? "wrote " + written + " script(s) to " + root.relativize(outDir).toString().replace('\\', '/') : "wrote nothing");
    }

    /** {@code <stem>} of {@code <stem>-plan.json} / {@code <stem>.json}, sanitised. */
    static String planName(Path f) {
        String s = f.getFileName().toString().replaceAll("(?i)\\.json$", "").replaceAll("(?i)-plan$", "");
        return GenPlan.safeId(s);
    }

    private static String value(String[] args, int i, String opt) {
        if (i >= args.length) throw new IllegalArgumentException(opt + " needs a value");
        return args[i];
    }

    private static Path resolve(Path root, String p) {
        Path x = new File(p).toPath();
        return (x.isAbsolute() ? x : root.resolve(x)).toAbsolutePath().normalize();
    }

    /** {@code p} is inside {@code root} (a real path) after normalising, and its nearest existing ancestor resolves inside it too. */
    static boolean insideForWrite(Path root, Path p) {
        Path n = p.toAbsolutePath().normalize();
        if (!n.startsWith(root) || n.equals(root)) return false;
        Path anc = n;
        while (anc != null && !Files.exists(anc)) anc = anc.getParent();
        try {
            return anc != null && anc.toRealPath().startsWith(root);
        } catch (IOException e) {
            return false;
        }
    }
}
