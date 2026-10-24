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
package com.gatf.selenium;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Δ163n (bug 3): maps a line of the transpiled Java back to the .sel line that produced it, via the
 * {@code GATF_LINE@<file>:<n>} / {@code GATF_ST_LINE@} / {@code GATF_OP_LINE@} markers Command emits before each
 * statement. ECJ errors were only printed (the editor got "Unable to compile seleasy script" with no line); with this
 * they become ordinary parse errors on the .sel line. Pure: no compiler or Selenium types.
 */
public final class SelCompileErrors {

    private SelCompileErrors() {
    }

    static final Pattern MARKER = Pattern.compile("/\\*GATF_(?:ST_|OP_)?LINE@(.*?):(\\d+)\\*/");

    /** {absolute .sel file, .sel line} for 1-based {@code javaLine}, or null when no marker precedes it. */
    public static Object[] selLineFor(List<String> javaLines, long javaLine) {
        if (javaLines == null || javaLine < 1) return null;
        for (int i = (int) Math.min(javaLine, javaLines.size()) - 1; i >= 0; i--) {
            String l = javaLines.get(i);
            Matcher m = MARKER.matcher(l);
            Object[] last = null;
            while (m.find()) {
                // Command writes the path through esc(): a Windows path arrives with doubled backslashes
                last = new Object[] { m.group(1).replace("\\\\", "\\"), Integer.parseInt(m.group(2)) };
            }
            if (last != null) return last;
        }
        return null;
    }

    /** The user-facing message for a compiler diagnostic mapped to a .sel line. */
    public static String message(String javacMessage, long javaLine) {
        String m = javacMessage == null ? "" : javacMessage.trim();
        return "Java compile error: " + m + " (generated line " + javaLine + ")";
    }
}
