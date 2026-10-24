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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Δ163o (AUDIT F10): summarise_trace takes heal_from_trace's path rule. */
class TraceInRootTest {

    @TempDir
    Path tmp;

    @Test
    void onlyExistingTracesInsideTheRootSymlinksResolved() throws Exception {
        Path root = Files.createDirectories(tmp.resolve("proj"));
        Path t = Files.write(Files.createDirectories(root.resolve("out")).resolve("a-trace.zip"), new byte[] { 1 });
        Path outside = Files.write(tmp.resolve("b-trace.zip"), new byte[] { 1 });
        GatfMcpTools tools = new GatfMcpTools(root, new ObjectMapper(), new java.io.PrintStream(new java.io.ByteArrayOutputStream()));
        assertThat(tools.traceInRoot("out/a-trace.zip")).isEqualTo(t.toRealPath());
        assertThat(tools.traceInRoot(t.toString())).isEqualTo(t.toRealPath());
        assertThatThrownBy(() -> tools.traceInRoot(outside.toString())).hasMessageContaining("inside the project root");
        assertThatThrownBy(() -> tools.traceInRoot("../b-trace.zip")).hasMessageContaining("inside the project root");
        assertThatThrownBy(() -> tools.traceInRoot("out/missing.zip")).hasMessageContaining("inside the project root");
        try {
            Files.createSymbolicLink(root.resolve("link.zip"), outside);
        } catch (UnsupportedOperationException | java.io.IOException noSymlinks) {
            return;
        }
        assertThatThrownBy(() -> tools.traceInRoot("link.zip")).hasMessageContaining("inside the project root");
    }
}
