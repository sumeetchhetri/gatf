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
package com.gatf.selenium;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatf.ai.gen.LintDiagnostics;
import com.gatf.ai.gen.SelGate;
import com.gatf.executor.core.AcceptanceTestContext;
import com.gatf.executor.core.GatfExecutorConfig;

/**
 * H1 (Δ152): the production {@link SelGate} for {@code gatf generate} and the MCP {@code generate} tool.
 * <ul>
 * <li>{@link #lint}: {@link Command#validateSel} on the script (transpile + ECJ compile, as MCP {@code lint} does);
 * when it reports no detail, the parse errors are read again for their script lines.</li>
 * <li>{@link #dryRun}: every script transpiled with {@link Command#read} and the generated Java of the whole suite
 * compiled in one ECJ task (no browser), errors mapped back to the script they came from.</li>
 * </ul>
 * Scripts are written only into a private directory from {@code Files.createTempDirectory("gatf-gen-")} (owner-only
 * on POSIX), deleted in a {@code finally} on every path without following symlinks. Nothing is written in the project.
 * {@code validateSel}'s own compile still uses the shared {@code $TMP/gatf-code} dir, which this class never touches
 * (its age-based sweep, Δ161c, cleans it).
 */
public final class SelSuiteGate implements SelGate {

    private final Path root;
    private final Path config;
    private final ObjectMapper om = new ObjectMapper();
    private AcceptanceTestContext ctx;

    /** @param config the config file (inside {@code root}); validateSel needs one. */
    public SelSuiteGate(Path root, Path config) {
        this.root = root.toAbsolutePath().normalize();
        this.config = config.toAbsolutePath().normalize();
    }

    @Override
    public List<SelGate.Diag> lint(String name, String text) throws Exception {
        Path dir = scratch();
        try {
            Path f = write(dir, name, text);
            String res = Command.validateSel(new String[] { "-validate-sel", f.toString(), config.toString(), root.toString() }, null, false);
            @SuppressWarnings("unchecked")
            Map<String, Object> m = om.readValue(res, Map.class);
            String status = String.valueOf(m.get("status"));
            if ("SUCCESS".equals(status)) return new ArrayList<SelGate.Diag>();
            List<SelGate.Diag> ds = parseErrors(name, f);
            return ds.isEmpty() ? LintDiagnostics.from(name, status, m.get("error") == null ? null : String.valueOf(m.get("error"))) : ds;
        } finally {
            delete(dir);
        }
    }

    @Override
    public List<SelGate.Diag> dryRun(Map<String, String> files) throws Exception {
        List<SelGate.Diag> out = new ArrayList<SelGate.Diag>();
        Path dir = scratch();
        try {
            Path sels = Files.createDirectory(dir.resolve("sel"));
            Path src = Files.createDirectories(dir.resolve("src").resolve("com").resolve("gatf").resolve("selenium"));
            Path classes = Files.createDirectory(dir.resolve("classes"));
            Map<String, String> bySource = new LinkedHashMap<String, String>();
            List<File> sources = new ArrayList<File>();
            for (Map.Entry<String, String> e : files.entrySet()) {
                Path f = write(sels, e.getKey(), e.getValue());
                Command cmd;
                try {
                    cmd = Command.read(f.toFile(), new ArrayList<String>(), context());
                } catch (Command.GatfSelCodeParseError pe) {
                    out.addAll(details(e.getKey(), pe));
                    continue;
                } catch (Exception x) {
                    out.add(new SelGate.Diag(e.getKey(), null, null, "error", LintDiagnostics.clean("transpile failed: " + x.getMessage())));
                    continue;
                }
                String java = cmd.javacode();
                Path jf = src.resolve(cmd.getClassName() + ".java");
                if (Files.exists(jf, LinkOption.NOFOLLOW_LINKS)) {
                    out.add(new SelGate.Diag(e.getKey(), null, null, "error", "generated class name is not unique in the suite"));
                    continue;
                }
                Files.write(jf, java.getBytes(StandardCharsets.UTF_8));
                bySource.put(jf.getFileName().toString(), e.getKey());
                sources.add(jf.toFile());
            }
            if (sources.isEmpty()) return out;
            JavaCompiler compiler = null;
            for (JavaCompiler jc : java.util.ServiceLoader.load(JavaCompiler.class)) { compiler = jc; break; }
            if (compiler == null) compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler == null) {
                out.add(new SelGate.Diag(null, null, null, "error", "no Java compiler available (ECJ or a JDK) for the dry run"));
                return out;
            }
            DiagnosticCollector<JavaFileObject> dc = new DiagnosticCollector<JavaFileObject>();
            boolean ok;
            try (StandardJavaFileManager fm = compiler.getStandardFileManager(dc, null, StandardCharsets.UTF_8)) {
                List<String> opts = Arrays.asList("-g", "-proc:none", "-d", classes.toString(), "-classpath", classpath());
                ok = compiler.getTask(null, fm, dc, opts, null, fm.getJavaFileObjectsFromFiles(sources)).call();
            }
            boolean any = false;
            for (Diagnostic<? extends JavaFileObject> d : dc.getDiagnostics()) {
                if (d.getKind() != Diagnostic.Kind.ERROR) continue;
                any = true;
                String srcName = d.getSource() == null ? null : new File(d.getSource().toUri().getPath()).getName();
                String sel = srcName == null ? null : bySource.get(srcName);
                out.add(new SelGate.Diag(sel, null, null, "error",
                        LintDiagnostics.clean("compile error at line " + d.getLineNumber() + " of the generated Java: " + d.getMessage(java.util.Locale.ROOT))));
            }
            if (!ok && !any) out.add(new SelGate.Diag(null, null, null, "error", "the suite did not compile (no detail reported)"));
            return out;
        } finally {
            delete(dir);
        }
    }

    /** The parse errors of a script that validateSel rejected, with their script lines; empty when it parses. */
    private List<SelGate.Diag> parseErrors(String name, Path f) {
        try {
            Command.read(f.toFile(), new ArrayList<String>(), context());
            return new ArrayList<SelGate.Diag>();
        } catch (Command.GatfSelCodeParseError pe) {
            return details(name, pe);
        } catch (Exception e) {
            return new ArrayList<SelGate.Diag>();
        }
    }

    private static List<SelGate.Diag> details(String name, Command.GatfSelCodeParseError pe) {
        List<SelGate.Diag> out = new ArrayList<SelGate.Diag>();
        List<Command.GatfSelCodeParseError> all = new ArrayList<Command.GatfSelCodeParseError>();
        if (pe.getAllErrors() != null && !pe.getAllErrors().isEmpty()) all.addAll(pe.getAllErrors());
        else all.add(pe);
        for (Command.GatfSelCodeParseError e : all) {
            if (out.size() >= LintDiagnostics.MAX_DIAGNOSTICS) break;
            Object[] d = e.getDetails();
            Integer line = d != null && d.length > 1 && d[1] instanceof Integer ? (Integer) d[1] : null;
            String cause = d != null && d.length > 3 && d[3] != null && !String.valueOf(d[3]).isEmpty() ? ": " + d[3] : "";
            String stmt = d != null && d.length > 0 && d[0] != null ? " (" + String.valueOf(d[0]).trim() + ")" : "";
            out.add(new SelGate.Diag(name, line, null, "error", LintDiagnostics.clean("cannot parse" + stmt + cause)));
        }
        return out;
    }

    private AcceptanceTestContext context() throws Exception {
        if (ctx == null) {
            GatfExecutorConfig c = Command.getConfig(config.toString(), root.toString());
            AcceptanceTestContext x = new AcceptanceTestContext();
            x.setGatfExecutorConfig(c);
            x.validateAndInit(true);
            x.getWorkflowContextHandler().initializeSuiteContext(1);
            ctx = x;
        }
        return ctx;
    }

    private static String classpath() {
        LinkedHashSet<String> cp = new LinkedHashSet<String>();
        String jcp = System.getProperty("java.class.path");
        if (jcp != null) for (String p : jcp.split(java.util.regex.Pattern.quote(File.pathSeparator))) if (!p.isEmpty()) cp.add(p);
        try {
            cp.add(new File(Command.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getAbsolutePath());
        } catch (Exception | Error e) {
            // the class path above already has it in every run mode
        }
        return String.join(File.pathSeparator, cp);
    }

    /** A private scratch directory: owner-only on POSIX. */
    static Path scratch() throws IOException {
        if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            return Files.createTempDirectory("gatf-gen-", PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        }
        return Files.createTempDirectory("gatf-gen-");
    }

    private static Path write(Path dir, String name, String text) throws IOException {
        if (name == null || !name.matches("[A-Za-z0-9_-]{1,80}\\.sel")) throw new IOException("bad script name");
        Path f = dir.resolve(name);
        Files.write(f, text.getBytes(StandardCharsets.UTF_8));
        return f;
    }

    /** Deletes {@code dir} recursively; symlinks are removed, never followed. Errors are ignored. */
    static void delete(Path dir) {
        if (dir == null || !Files.exists(dir, LinkOption.NOFOLLOW_LINKS)) return;
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
                @Override public FileVisitResult visitFile(Path f, BasicFileAttributes a) throws IOException {
                    Files.deleteIfExists(f);
                    return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult visitFileFailed(Path f, IOException e) {
                    try { Files.deleteIfExists(f); } catch (IOException ignore) { }
                    return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult postVisitDirectory(Path d, IOException e) throws IOException {
                    Files.deleteIfExists(d);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignore) {
            // best effort: the temp dir is private and owner-only
        }
    }
}
