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
package com.gatf.ai.gen;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * H1 (Δ152): the generator's gate. {@link #lint} is {@code Command.validateSel} on one script; {@link #dryRun}
 * transpiles the whole suite and compiles it together (no browser). Production: {@code com.gatf.selenium.SelSuiteGate};
 * tests pass a fake. A gate writes only into its own private temp dir, never into the project.
 */
public interface SelGate {

    /** One problem: {@code file} (the script's file name), {@code line} (1-based in the script, or null), message. */
    final class Diag {
        public final String file;
        public final Integer line;
        public final Integer column;
        public final String severity;
        public final String message;

        public Diag(String file, Integer line, Integer column, String severity, String message) {
            this.file = file;
            this.line = line;
            this.column = column;
            this.severity = severity == null ? "error" : severity;
            this.message = message == null ? "" : message;
        }

        public Map<String, Object> json() {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("file", file);
            m.put("line", line);
            if (column != null) m.put("column", column);
            m.put("severity", severity);
            m.put("message", message);
            return m;
        }

        @Override public String toString() { return (line == null ? "" : "line " + line + ": ") + message; }
    }

    /** Errors for one script ({@code name} its file name, {@code text} its content); empty when it passes. */
    List<Diag> lint(String name, String text) throws Exception;

    /** Errors for the suite compiled together (name → text); each names the file it belongs to, or null for the whole run. */
    List<Diag> dryRun(Map<String, String> files) throws Exception;
}
