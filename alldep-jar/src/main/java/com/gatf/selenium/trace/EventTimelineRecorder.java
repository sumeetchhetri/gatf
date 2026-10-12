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

    /** H2 (Δ146): a per-action event carrying its action id {@code a}, which keys the before/after snapshots. */
    public void action(int id, String name, String detail) {
        out.writeLine(new Json.Obj()
                .put("ts", System.currentTimeMillis())
                .put("type", "action")
                .put("name", name)
                .put("detail", detail)
                .put("a", id)
                .end());
    }

    public void flush() { out.flush(); }
    public void close() { out.close(); }
}
