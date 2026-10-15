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
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import com.gatf.executor.core.WorkflowContextHandler;
import com.gatf.imports.ImportResult;
import com.gatf.imports.Importers;

/**
 * Q2 — {@code gatf import <type> <file|-> [-o out] [--name suite] [--filter regex] [--force]}: converts a Playwright
 * spec, JMeter plan, cURL command(s), Postman collection or HAR into a GATF test file, printing what could not be
 * carried over. Same conversion as the UI's Import view.
 */
final class ImportCli {

    static final String USAGE = "gatf import <" + String.join("|", Importers.TYPES) + "> <file|-> [-o <out-file>] "
            + "[--name <suite>] [--filter <url-regex>] [--negative] [--force]\n"
            + "          flow (a run's network.har → load test): [--users N] [--duration S] [--rate R] [--engine wperfk|vegeta|autocannon|wrk|wrk2] (default wperfk)";

    private ImportCli() {}

    static int run(String[] args, File cwd, PrintStream out, PrintStream err) {
        String type = null, in = null, outFile = null, name = null, filter = null;
        String users = null, duration = null, rate = null, engine = null;   // M17: flow → load
        File baseDir = null, specFile = null;
        boolean force = false, negative = false;
        try {
            for (int i = 0; i < args.length; i++) {
                String a = args[i];
                switch (a) {
                    case "-o": case "--out": outFile = value(args, ++i, a); break;
                    case "--name": name = value(args, ++i, a); break;
                    case "--filter": filter = value(args, ++i, a); break;
                    case "-f": case "--force": force = true; break;
                    case "--negative": negative = true; break;
                    case "--users": users = value(args, ++i, a); break;
                    case "--duration": duration = value(args, ++i, a); break;
                    case "--rate": rate = value(args, ++i, a); break;
                    case "--engine": engine = value(args, ++i, a); break;
                    default:
                        if (a.startsWith("-") && !a.equals("-")) throw new IllegalArgumentException("unknown option " + a);
                        if (type == null) type = a;
                        else if (in == null) in = a;
                        else throw new IllegalArgumentException("unexpected argument " + a);
                }
            }
            if (type == null || in == null) throw new IllegalArgumentException("type and input file are required");

            String source;
            if (in.equals("-")) {
                source = new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
            } else {
                File f = resolve(cwd, in);
                if (!f.isFile()) throw new IllegalArgumentException("input not found: " + in);
                baseDir = f.getAbsoluteFile().getParentFile();
                specFile = f.getAbsoluteFile();
                source = Files.readString(f.toPath(), StandardCharsets.UTF_8);
                if (name == null) name = f.getName().replaceAll("(\\.spec)?\\.[A-Za-z0-9]+$", "");
            }
            if (source.isBlank()) throw new IllegalArgumentException("input is empty");
            if (name == null) name = "imported-" + type.toLowerCase();

            boolean openapi = type.equalsIgnoreCase("openapi") || type.equalsIgnoreCase("swagger");
            ImportResult r;
            String drift = null;
            if (openapi) {
                // OA2: cases point at the spec (relative to the .xml) so responses are contract-checked at run time
                File xml = resolve(cwd, outFile != null ? outFile : Importers.safeName(name) + ".xml").getAbsoluteFile();
                String specRef = specFile == null ? null : relative(xml.getParentFile(), specFile);
                r = Importers.importOpenApi(source, name, baseDir, specRef, negative);
                if (xml.isFile()) {
                    try {
                        java.util.List<com.gatf.executor.core.TestCase> old = WorkflowContextHandler.XOM.readValue(xml,
                                new com.fasterxml.jackson.core.type.TypeReference<java.util.List<com.gatf.executor.core.TestCase>>() {});
                        drift = com.gatf.contract.ContractDrift.diff(Importers.contractIndex(old), Importers.contractIndex(r.testCases)).toString();
                    } catch (Exception e) {
                        drift = "(existing " + xml.getName() + " unreadable: " + e.getMessage() + ")";
                    }
                }
                if (specRef == null) r.unsupported(0, "contract checks", "spec read from stdin — cases carry no contract pointer");
            } else if (com.gatf.imports.FlowToLoad.TYPE.equalsIgnoreCase(type)) {
                r = com.gatf.imports.FlowToLoad.convert(source, name, filter, com.gatf.imports.FlowToLoad.Options.of(users, duration, rate, engine));
            } else {
                r = Importers.importSource(type, source, name, filter, baseDir);
            }
            if (drift != null) out.println("drift vs the existing file:" + (drift.startsWith("   ") ? "\n" + drift : " " + drift));
            if ("xml".equals(r.kind) && r.testCases != null) {
                r.content = WorkflowContextHandler.XOM.writerWithDefaultPrettyPrinter().writeValueAsString(r.testCases);
            }
            File target = resolve(cwd, outFile != null ? outFile : r.fileName);
            if (target.exists() && !force) throw new IllegalArgumentException(target.getPath() + " exists (use --force to overwrite)");
            if (target.getParentFile() != null) target.getParentFile().mkdirs();
            java.util.Map<File, String> side = new java.util.LinkedHashMap<File, String>();
            for (java.util.Map.Entry<String, String> e : r.sideFiles.entrySet()) {
                File sf = new File(target.getAbsoluteFile().getParentFile(), ImportResult.sideFileName(target.getName(), e.getKey()));
                if (sf.exists() && !force) throw new IllegalArgumentException(sf.getPath() + " exists (use --force to overwrite)");
                side.put(sf, e.getValue());
            }
            Files.writeString(target.toPath(), r.content, StandardCharsets.UTF_8);
            for (java.util.Map.Entry<File, String> e : side.entrySet()) Files.writeString(e.getKey().toPath(), e.getValue(), StandardCharsets.UTF_8);

            int n = r.testCases != null ? r.testCases.size() : -1;
            out.println("Imported " + (n >= 0 ? n + " test case(s)" : "script") + " → " + target.getPath() + "  " + r.stats);
            for (File sf : side.keySet()) out.println("  also wrote " + sf.getPath());
            if (r.loadConfig != null) out.println("  load settings for gatf-config: " + r.loadConfig);
            if (r.variables != null) out.println("  variables for gatf-config globalVariables: " + r.variables);
            if (!r.unsupported.isEmpty()) {
                out.println("  " + r.unsupported.size() + " item(s) need review:");
                for (ImportResult.Unsupported u : r.unsupported) {
                    out.println("   ! " + (u.line > 0 ? "#" + u.line + " " : "") + u.construct + " — " + u.reason);
                }
            }
            return 0;
        } catch (IllegalArgumentException e) {
            err.println("gatf import: " + e.getMessage());
            err.println("usage: " + USAGE);
            return 1;
        } catch (Exception e) {
            err.println("gatf import: " + (e.getMessage() == null ? e.toString() : e.getMessage()));
            return 1;
        }
    }

    private static String value(String[] args, int i, String opt) {
        if (i >= args.length) throw new IllegalArgumentException(opt + " needs a value");
        return args[i];
    }

    /** {@code from} → {@code to} as a '/'-separated relative path (absolute when there is none, e.g. another drive). */
    static String relative(File from, File to) {
        try {
            return from.toPath().relativize(to.toPath()).toString().replace('\\', '/');
        } catch (IllegalArgumentException e) {
            return to.getPath().replace('\\', '/');
        }
    }

    private static File resolve(File cwd, String p) {
        File f = new File(p);
        return f.isAbsolute() ? f : new File(cwd, p);
    }
}
