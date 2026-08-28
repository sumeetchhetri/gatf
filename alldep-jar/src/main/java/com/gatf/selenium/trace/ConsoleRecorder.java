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
