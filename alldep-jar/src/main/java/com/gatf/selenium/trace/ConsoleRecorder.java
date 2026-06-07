package com.gatf.selenium.trace;

import java.nio.file.Path;

/**
 * Streams browser console/log entries to {@code console.ndjson} as they arrive.
 * @author Sumeet Chhetri
 */
public final class ConsoleRecorder implements ConsoleListener {
    private final NdjsonWriter out;

    public ConsoleRecorder(Path traceDir) throws java.io.IOException {
        this.out = new NdjsonWriter(traceDir.resolve("console.ndjson"));
    }

    @Override
    public void onEntry(long tsMillis, String level, String text, String url, Integer line, String source) {
        out.writeLine(new Json.Obj()
                .put("ts", tsMillis)
                .put("level", level == null ? "info" : level)
                .put("text", text)
                .put("url", url)
                .putNum("line", line == null ? null : line.longValue())
                .put("source", source)
                .end());
    }

    public void flush() { out.flush(); }
    public void close() { out.close(); }
}
