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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** T1 backend-unit: append-only NDJSON line framing (one object per line, null-skip, post-close drop). */
class NdjsonWriterTest {

    @Test
    void writesOneJsonObjectPerLineAndSkipsNull(@TempDir Path tmp) throws Exception {
        Path f = tmp.resolve("events.ndjson");
        try (NdjsonWriter w = new NdjsonWriter(f)) {
            w.writeLine("{\"a\":1}");
            w.writeLine(null);          // ignored, no blank line
            w.writeLine("{\"b\":2}");
        }
        assertThat(Files.readString(f, StandardCharsets.UTF_8)).isEqualTo("{\"a\":1}\n{\"b\":2}\n");
    }

    @Test
    void createsParentDirsAndDropsWritesAfterClose(@TempDir Path tmp) throws Exception {
        Path f = tmp.resolve("sub/dir/events.ndjson");   // parent dirs auto-created by the ctor
        NdjsonWriter w = new NdjsonWriter(f);
        w.writeLine("{\"a\":1}");
        w.close();
        w.writeLine("{\"late\":true}");                  // no-op after close
        assertThat(Files.readString(f, StandardCharsets.UTF_8)).isEqualTo("{\"a\":1}\n");
    }
}
