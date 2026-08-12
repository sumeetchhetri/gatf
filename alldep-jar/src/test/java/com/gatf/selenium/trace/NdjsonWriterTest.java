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
