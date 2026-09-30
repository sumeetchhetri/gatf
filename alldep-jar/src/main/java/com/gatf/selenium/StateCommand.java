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

/**
 * M5 — {@code state save <file>} / {@code state load <file>}: the browser session's cookies and web storage to / from a
 * Playwright-style storage-state JSON file (see {@link StorageState}), resolved against the test-case directory.
 */
public class StateCommand extends Command {

    final String op, file;

    StateCommand(String val, Object[] cmdDetails, CommandState state) {
        super(cmdDetails, state);
        String[] p = val.trim().split("[\t ]+", 2);
        op = p[0].toLowerCase(java.util.Locale.ROOT);
        String f = p.length > 1 ? unSantizedUnQuoted(p[1].trim(), state) : "";
        file = f;
        if (!op.equals("save") && !op.equals("load")) throwParseError(cmdDetails, new RuntimeException("state save <file> | state load <file>"));
        if (file.isEmpty()) throwParseError(cmdDetails, new RuntimeException("state " + op + " needs a file"));
    }

    String toCmd() {
        return "state " + op + " \"" + file + "\"";
    }

    String javacode() {
        return "__state__(get___d___(), \"" + op + "\", evaluate(\"" + esc(file) + "\"));\n";
    }

    public static String[] toSampleSelCmd() {
        return new String[] {
            "Save / load the browser's authenticated state (cookies + local/session storage)",
            "\tstate save {file}",
            "\tstate load {file}",
            "Examples :-",
            "\tstate save .auth/admin.json",
            "\tstate load .auth/admin.json",
        };
    }
}
