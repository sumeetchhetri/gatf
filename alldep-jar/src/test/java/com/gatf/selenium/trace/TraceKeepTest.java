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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;

/** Δ163o bug 9: when a debug session keeps its trace, and where. */
class TraceKeepTest {

    @Test
    void failedWhenTheScriptOrAnySubtestFailed() {
        assertThat(TraceKeep.failed(true, Arrays.asList(true, true))).isFalse();
        assertThat(TraceKeep.failed(true, Arrays.asList(true, false))).isTrue();   // the reported bug: a failed subtest
        assertThat(TraceKeep.failed(false, Collections.<Boolean>emptyList())).isTrue();
        assertThat(TraceKeep.failed(null, Arrays.asList(true))).isTrue();         // never finished
        assertThat(TraceKeep.failed(true, null)).isFalse();
        assertThat(TraceKeep.failed(true, Arrays.asList((Boolean) null))).isFalse();
    }

    @Test
    void debugTraceGoesUnderOutDebugWithAServedUrl() {
        File out = new File("out");
        File f = TraceKeep.debugTraceFile(out, "com.gatf.selenium.STC_123", 0);
        assertThat(f).isEqualTo(new File(new File(out, "debug"), "STC_123-trace.zip"));
        assertThat(TraceKeep.debugTraceFile(out, "com.gatf.selenium.STC_123", 2).getName()).isEqualTo("STC_123-2-trace.zip");
        assertThat(TraceKeep.debugTraceFile(out, "../x/y", 0).getName()).isEqualTo("_x_y-trace.zip");   // no separators survive
        assertThat(TraceKeep.debugTraceFile(out, "a.b$c d", 0).getName()).isEqualTo("b_c_d-trace.zip");
        assertThat(TraceKeep.debugTraceUrl(f)).isEqualTo("/reports/debug/STC_123-trace.zip");
    }

    @Test
    void oldDebugTracesArePruned(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp) throws Exception {
        File dbg = java.nio.file.Files.createDirectories(tmp.resolve("debug")).toFile();
        for (int i = 0; i < 25; i++) {
            File f = new File(dbg, "STC_" + i + "-trace.zip");
            java.nio.file.Files.write(f.toPath(), new byte[] { 1 });
            f.setLastModified(1_000_000L + i * 1000L);
        }
        java.nio.file.Files.write(new File(dbg, "notes.txt").toPath(), new byte[] { 1 });
        assertThat(TraceKeep.pruneDebug(tmp.toFile(), 20)).isEqualTo(5);
        assertThat(new File(dbg, "STC_0-trace.zip")).doesNotExist();
        assertThat(new File(dbg, "STC_24-trace.zip")).exists();
        assertThat(new File(dbg, "notes.txt")).exists();
        assertThat(TraceKeep.pruneDebug(new File(tmp.toFile(), "none"), 20)).isZero();
    }
}

