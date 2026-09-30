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
package com.gatf.executor.core;

/** M5/M6 — helpers for the scripts GATF runs around the UI suite (loginOnce, globalSetup, globalTeardown). Pure but for file lookups. */
public final class SetupScripts {

    private SetupScripts() {}

    /** Same script regardless of separators, a leading "./" or letter case of the extension. */
    public static boolean sameScript(String a, String b) {
        return a != null && b != null && norm(a).equals(norm(b));
    }

    /** The suite's scripts without the setup ones (blank setup entries are ignored); order kept. */
    public static String[] exclude(String[] scripts, String... setup) {
        return excludeResolved(scripts, null, setup);
    }

    /**
     * As {@link #exclude(String[], String...)}, also matching by file: {@code resolver} (the context's resource
     * resolution) turns each name into a file, so an absolute suite path (seleniumScript naming a folder) matches a
     * config-relative setup name. A null resolver compares names only.
     */
    public static String[] excludeResolved(String[] scripts, java.util.function.Function<String, java.io.File> resolver, String... setup) {
        java.util.List<String> out = new java.util.ArrayList<String>();
        for (String s : scripts) {
            boolean isSetup = false;
            for (String x : setup) isSetup |= x != null && !x.trim().isEmpty() && (sameScript(s, x) || sameFile(s, x.trim(), resolver));
            if (!isSetup) out.add(s);
        }
        return out.toArray(new String[out.size()]);
    }

    /** Both names resolve to the same file (normalized, canonical when possible). */
    public static boolean sameFile(String a, String b, java.util.function.Function<String, java.io.File> resolver) {
        if (resolver == null || a == null || b == null) return false;
        try {
            java.io.File fa = resolver.apply(a.trim()), fb = resolver.apply(b.trim());
            return fa != null && fb != null && canon(fa).equals(canon(fb));
        } catch (RuntimeException e) {
            return false;   // unresolvable name (e.g. no test-case base path): names only
        }
    }

    static java.nio.file.Path canon(java.io.File f) {
        try {
            return f.getCanonicalFile().toPath();
        } catch (java.io.IOException e) {
            return f.getAbsoluteFile().toPath().normalize();
        }
    }

    /**
     * The scripts named by the {@code seleniumScript} setting (shared by the executor and {@code gatf docs}); a relative
     * path is tried as given, then under {@code testCaseDir}: a folder → every *.sel under it (absolute paths, sorted);
     * a .sel file → that script; any other file → a list file, one script per line (blank lines and # comments skipped).
     */
    public static java.util.List<String> expandSeleniumScript(String seleniumScript, java.io.File testCaseDir) throws java.io.IOException {
        java.util.List<String> out = new java.util.ArrayList<String>();
        java.io.File f = new java.io.File(seleniumScript.trim());
        if (!f.isAbsolute() && !f.exists() && testCaseDir != null) f = new java.io.File(testCaseDir, seleniumScript.trim());
        if (f.isDirectory()) {
            // a folder: every *.sel under it
            try (java.util.stream.Stream<java.nio.file.Path> w = java.nio.file.Files.walk(f.toPath())) {
                w.filter(p -> java.nio.file.Files.isRegularFile(p) && p.getFileName().toString().endsWith(".sel")).sorted()
                        .forEach(p -> out.add(p.toFile().getAbsolutePath()));
            }
        } else if (f.isFile() && f.getName().endsWith(".sel")) {
            out.add(f.getAbsolutePath());   // one script
        } else if (f.isFile()) {
            // a list file: one script per line; blank lines and # comments skipped
            for (String l : java.nio.file.Files.readAllLines(f.toPath(), java.nio.charset.StandardCharsets.UTF_8)) {
                String t = l.trim();
                if (!t.isEmpty() && !t.startsWith("#")) out.add(t);
            }
        } else {
            throw new java.io.FileNotFoundException("seleniumScript not found: " + seleniumScript);
        }
        return out;
    }

    static String norm(String p) {
        String s = p.trim().replace('\\', '/');
        while (s.startsWith("./")) s = s.substring(2);
        if (s.toLowerCase(java.util.Locale.ROOT).endsWith(".sel")) s = s.substring(0, s.length() - 4) + ".sel";
        return s;
    }

    /** Report run prefix for a setup script, e.g. "loginOnce" → "Setup-loginOnce". */
    public static String runPrefix(String kind) {
        return "Setup-" + kind;
    }
}
