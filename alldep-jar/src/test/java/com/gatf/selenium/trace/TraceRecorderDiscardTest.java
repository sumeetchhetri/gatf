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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Q3: a discarded trace leaves nothing behind; a kept one becomes a bundle and its working dir is removed. */
class TraceRecorderDiscardTest {

    /** A browser that supports nothing (every capability false), so only the event timeline + metadata record. */
    private static BrowserControl inertBrowser() {
        return (BrowserControl) Proxy.newProxyInstance(TraceRecorderDiscardTest.class.getClassLoader(),
                new Class<?>[] { BrowserControl.class }, (p, m, a) -> {
                    if (m.isDefault()) return InvocationHandler.invokeDefault(p, m, a);
                    return m.getReturnType() == boolean.class ? Boolean.FALSE : null;
                });
    }

    @Test
    void discardDeletesTheWorkingDirAndIsIdempotent(@TempDir Path dir) throws Exception {
        Path wd = dir.resolve(".trace/trace-a");
        TraceRecorder r = TraceRecorder.attach(inertBrowser(), new TraceConfig().setEnableTrace(true), wd, "a.sel", "chrome");
        r.event("action", "click", "id@x");
        assertThat(wd).isDirectory();
        r.discard();
        assertThat(wd).doesNotExist();
        r.discard();   // second call must not throw
    }

    @Test
    void writeZipProducesABundleAndRemovesTheWorkingDir(@TempDir Path dir) throws Exception {
        Path wd = dir.resolve(".trace/trace-b");
        TraceRecorder r = TraceRecorder.attach(inertBrowser(), new TraceConfig().setEnableTrace(true), wd, "b.sel", "chrome");
        Path zip = r.writeZip(dir.resolve("out/b-trace.zip"));
        try (ZipFile z = new ZipFile(zip.toFile())) {
            List<String> names = Collections.list(z.entries()).stream().map(e -> e.getName()).sorted().collect(Collectors.toList());
            assertThat(names).contains("events.ndjson", "metadata.json");
        }
        assertThat(wd).doesNotExist();
        assertThat(Files.size(zip)).isPositive();
    }
}
