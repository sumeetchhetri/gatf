package com.gatf.selenium.trace;

import java.nio.file.Path;

/**
 * Streams the run timeline to {@code events.ndjson}. Phase 1 records lifecycle/navigation
 * events; per-seleasy-action events require Command.java codegen and are a follow-up.
 * @author Sumeet Chhetri
 */
public final class EventTimelineRecorder {
    private final NdjsonWriter out;

    public EventTimelineRecorder(Path traceDir) throws java.io.IOException {
        this.out = new NdjsonWriter(traceDir.resolve("events.ndjson"));
    }

    /** Record one timeline event. type e.g. "lifecycle"|"navigation"|"snapshot"|"action". */
    public void event(String type, String name, String detail) {
        out.writeLine(new Json.Obj()
                .put("ts", System.currentTimeMillis())
                .put("type", type == null ? "event" : type)
                .put("name", name)
                .put("detail", detail)
                .end());
    }

    public void flush() { out.flush(); }
    public void close() { out.close(); }
}
