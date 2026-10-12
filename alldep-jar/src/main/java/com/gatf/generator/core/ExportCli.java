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
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.gatf.export.ExportException;
import com.gatf.export.ExportOptions;
import com.gatf.export.ExportResult;
import com.gatf.export.Exporter;
import com.gatf.export.Exporters;
import com.gatf.export.SelParser;
import com.gatf.export.SelScript;

/**
 * Δ160 — {@code gatf export <target> <file.sel|dir> [-o out] [--strict-locators] [--force]}: exports seleasy scripts
 * to another framework (Playwright TS first), printing what was not carried over. It reads only the .sel files, never
 * a gatf-config, so no configured value can reach the output: {@code ${name}} references are exported as environment
 * variables. In directory mode, library scripts (subtests / funcs only, no {@code open}) are skipped, as the
 * executor does, and the report is also written to {@code export-report.txt} in the output directory.
 */
final class ExportCli {

    static final String USAGE = "gatf export <" + String.join("|", Exporters.ids()) + "> <file.sel|dir> [-o <out-file|out-dir>] [--strict-locators] [--force]";

    private ExportCli() {}

    static int run(String[] args, File cwd, PrintStream out, PrintStream err) {
        String target = null, in = null, outArg = null;
        boolean force = false;
        ExportOptions options = new ExportOptions();
        try {
            for (int i = 0; i < args.length; i++) {
                String a = args[i];
                switch (a) {
                    case "-o": case "--out":
                        if (++i >= args.length) throw new IllegalArgumentException(a + " needs a value");
                        outArg = args[i];
                        break;
                    case "-f": case "--force": force = true; break;
                    case "--strict-locators": options.strictLocators(true); break;
                    default:
                        if (a.startsWith("-")) throw new IllegalArgumentException("unknown option " + a);
                        if (target == null) target = a;
                        else if (in == null) in = a;
                        else throw new IllegalArgumentException("unexpected argument " + a);
                }
            }
            if (target == null || in == null) throw new IllegalArgumentException("target and input are required");
            Exporter exporter = Exporters.get(target);
            if (exporter == null) throw new IllegalArgumentException("unknown export target '" + target + "'");
            File src = resolve(cwd, in);
            if (src.isFile()) return single(exporter, src, outArg, cwd, force, options, out, err);
            if (src.isDirectory()) return directory(exporter, src, outArg, cwd, force, options, out, err);
            throw new IllegalArgumentException("input not found: " + in);
        } catch (IllegalArgumentException e) {
            err.println("gatf export: " + e.getMessage());
            err.println("usage: " + USAGE);
            return 1;
        } catch (IOException e) {
            err.println("gatf export: " + e.getMessage());
            return 1;
        }
    }

    private static int single(Exporter exporter, File src, String outArg, File cwd, boolean force, ExportOptions options, PrintStream out, PrintStream err) throws IOException {
        String name = scriptName(src);
        ExportResult r;
        try {
            r = exporter.export(new SelParser().parse(name, read(src)), options);
        } catch (ExportException e) {
            err.println("gatf export: " + e.getMessage());
            return 1;
        }
        File target;
        if (outArg == null) target = new File(cwd, r.fileName);
        else {
            File o = resolve(cwd, outArg);
            target = o.isDirectory() || outArg.endsWith("/") || outArg.endsWith(File.separator) ? new File(o, r.fileName) : o;
        }
        write(target, r.content, force);
        out.println("Exported " + src.getName() + " → " + target.getPath());
        out.print(r.report());
        return 0;
    }

    private static int directory(Exporter exporter, File dir, String outArg, File cwd, boolean force, ExportOptions options, PrintStream out, PrintStream err) throws IOException {
        File outDir = resolve(cwd, outArg != null ? outArg : "export-" + exporter.id());
        List<Path> sels;
        try (Stream<Path> w = Files.walk(dir.toPath())) {
            sels = w.filter(p -> p.toString().endsWith(".sel") && Files.isRegularFile(p)).sorted().collect(Collectors.toList());
        }
        if (sels.isEmpty()) throw new IllegalArgumentException("no .sel files under " + dir.getPath());
        Map<String, String> envOwner = new LinkedHashMap<String, String>();   // env name → "script: ${ref}"
        Map<File, ExportResult> results = new LinkedHashMap<File, ExportResult>();
        List<String> skipped = new ArrayList<String>();
        int failed = 0;
        for (Path p : sels) {
            String rel = dir.toPath().relativize(p).toString().replace('\\', '/');
            String name = scriptName(p.toFile());
            SelScript script = new SelParser().parse(name, read(p.toFile()));
            if (!hasOpen(script)) { skipped.add(rel); continue; }
            ExportResult r;
            try {
                r = exporter.export(script, options);
            } catch (ExportException e) {
                err.println("gatf export: " + rel + ": " + e.getMessage());
                failed++;
                continue;
            }
            boolean clash = false;
            for (Map.Entry<String, String> e : r.env.entrySet()) {
                String who = rel + ": ${" + e.getKey() + "}";
                String prev = envOwner.get(e.getValue());
                if (prev != null && !prev.endsWith(": ${" + e.getKey() + "}")) {
                    err.println("gatf export: " + prev + " and " + who + " both map to the environment variable " + e.getValue() + "; rename one of them");
                    clash = true;
                } else if (prev == null) {
                    envOwner.put(e.getValue(), who);
                }
            }
            if (clash) { failed++; continue; }
            String outRel = rel.substring(0, rel.length() - ".sel".length());
            int slash = outRel.lastIndexOf('/');
            File target = new File(outDir, (slash >= 0 ? outRel.substring(0, slash + 1) : "") + r.fileName);
            results.put(target, r);
        }
        for (File t : results.keySet()) {
            if (t.exists() && !force) throw new IllegalArgumentException(t.getPath() + " exists (use --force to overwrite)");
        }
        StringBuilder report = new StringBuilder();
        for (Map.Entry<File, ExportResult> e : results.entrySet()) {
            write(e.getKey(), e.getValue().content, true);
            report.append(e.getValue().report());
        }
        for (String s : skipped) report.append("skipped ").append(s).append(": no 'open' statement (a library of subtests / funcs)\n");
        if (!results.isEmpty() || !skipped.isEmpty()) write(new File(outDir, "export-report.txt"), report.toString(), true);
        out.println("Exported " + results.size() + " script(s) → " + outDir.getPath() + (skipped.isEmpty() ? "" : ", skipped " + skipped.size())
                + (failed == 0 ? "" : ", " + failed + " failed"));
        out.print(report);
        return failed == 0 ? 0 : 1;
    }

    private static boolean hasOpen(SelScript s) {
        for (SelScript.Step st : s.steps) if ("open".equals(st.keyword)) return true;
        return false;
    }

    private static String scriptName(File f) {
        return f.getName().replaceAll("\\.sel$", "");
    }

    private static String read(File f) throws IOException {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    private static void write(File target, String content, boolean force) throws IOException {
        if (target.exists() && !force) throw new IllegalArgumentException(target.getPath() + " exists (use --force to overwrite)");
        if (target.getAbsoluteFile().getParentFile() != null) target.getAbsoluteFile().getParentFile().mkdirs();
        Files.write(target.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    private static File resolve(File cwd, String p) {
        File f = new File(p);
        return f.isAbsolute() ? f : new File(cwd, p);
    }
}
