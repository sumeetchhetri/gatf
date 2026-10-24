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
package com.gatf.selenium.trace;

import java.io.File;

/**
 * Δ163o (bug 9): the pure parts of keeping a trace for a run that is not a suite run: a debug session runs the script in
 * its own JVM ({@code SeleniumTest.main}), which never zipped or discarded its trace, so a failed subtest in the
 * debugger left no trace.zip. Suite runs (CLI, Execute view, editor Run) keep theirs in GatfTestCaseExecutorUtil.
 */
public final class TraceKeep {

    private TraceKeep() {
    }

    /**
     * "Failed" for retain-on-failure, as the suite run counts it: the script or any subtest failed. A script with no
     * result ({@code null}: it never finished) counts as failed; skipped subtests are not passed in.
     */
    public static boolean failed(Boolean script, Iterable<Boolean> subtests) {
        if (script == null || !script) return true;
        if (subtests != null) {
            for (Boolean s : subtests) {
                if (s != null && !s) return true;
            }
        }
        return false;
    }

    /** Where a debug session's trace goes: {@code <outDir>/debug/<SimpleClassName>[-n]-trace.zip} (n for a 2nd+ session). */
    public static File debugTraceFile(File outDir, String className, int index) {
        String simple = className == null ? "script" : className.substring(className.lastIndexOf('.') + 1).replaceAll("[^A-Za-z0-9_\\-]", "_");
        return new File(new File(outDir, "debug"), simple + (index > 0 ? "-" + index : "") + "-trace.zip");
    }

    /**
     * Δ163o (verifier round): debug traces are named after the compiled class, which is unique per compile, so they
     * would pile up until a real run cleans out/. Keeps the newest {@code keep} {@code *-trace.zip} in
     * {@code <outDir>/debug}; returns how many were deleted. Never throws.
     */
    public static int pruneDebug(File outDir, int keep) {
        File[] fs = new File(outDir, "debug").listFiles((d, n) -> n.endsWith("-trace.zip"));
        if (fs == null || fs.length <= keep) return 0;
        java.util.Arrays.sort(fs, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));   // newest first
        int n = 0;
        for (int i = Math.max(0, keep); i < fs.length; i++) {
            if (fs[i].isFile() && fs[i].delete()) n++;
        }
        return n;
    }

    /** The URL the console serves a debug trace at ({@code /reports} is the out dir). */
    public static String debugTraceUrl(File traceFile) {
        return "/reports/debug/" + traceFile.getName();
    }
}
