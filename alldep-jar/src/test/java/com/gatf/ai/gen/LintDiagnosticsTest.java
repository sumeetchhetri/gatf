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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** Δ152 (carried Δ141): MCP {@code lint}'s {@code diagnostics} list, built from validateSel's {status, error}. */
public class LintDiagnosticsTest {

    static Map<String, Object> lint(String status, String error) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("status", status);
        if (error != null) m.put("error", error);
        m.put("file", "tests/a.sel");
        return m;
    }

    @Test
    void successHasNoDiagnosticsAndKeysStayUnchanged() {
        Map<String, Object> m = lint("SUCCESS", null);
        assertEquals(List.of(), LintDiagnostics.json(m));
        assertEquals(Map.of("status", "SUCCESS", "file", "tests/a.sel"), m, "status / error untouched");
    }

    @Test
    void parseErrorsCarryTheirScriptLine() {
        List<Map<String, Object>> d = LintDiagnostics.json(lint("FAILURE",
                "Error parsing command at line 7 in file /proj/tests/a.sel (clikc id@x)Error at line 12 in file /proj/tests/a.sel (})"));
        assertEquals(2, d.size(), d.toString());
        assertEquals("tests/a.sel", d.get(0).get("file"));
        assertEquals(7, d.get(0).get("line"));
        assertEquals("error", d.get(0).get("severity"));
        assertFalse(d.get(0).containsKey("column"));
        assertTrue(String.valueOf(d.get(0).get("message")).contains("clikc id@x"));
        assertFalse(String.valueOf(d.get(0).get("message")).contains("/proj/"), "no absolute path in the message");
        assertEquals(12, d.get(1).get("line"));
    }

    @Test
    void compileErrorsAndEmptyErrorsHaveNoLine() {
        List<Map<String, Object>> d = LintDiagnostics.json(lint("FAILURE",
                "/tmp/gatf-code/com/gatf/selenium/STC_1.java:44: error: cannot find symbol\n  symbol: x\u2028y"));
        assertEquals(1, d.size());
        assertNull(d.get(0).get("line"));
        String msg = String.valueOf(d.get(0).get("message"));
        assertTrue(msg.startsWith("STC_1.java:44: error: cannot find symbol"), msg);
        assertFalse(msg.contains("\n") || msg.contains("\u2028") || msg.contains("/tmp/"), msg);
        assertTrue(String.valueOf(LintDiagnostics.json(lint("FAILURE", "")).get(0).get("message")).contains("no detail"));
        assertTrue(String.valueOf(LintDiagnostics.json(lint("FAILURE", null)).get(0).get("message")).contains("no detail"));
        String longMsg = String.valueOf(LintDiagnostics.json(lint("FAILURE", "x".repeat(5000))).get(0).get("message"));
        assertTrue(longMsg.length() <= LintDiagnostics.MAX_MESSAGE + 1);
        // a URL in the failing statement is kept as written
        assertTrue(String.valueOf(LintDiagnostics.json(lint("FAILURE", "Error at line 3 in file f (goto https://a.test/x/y)")).get(0).get("message")).contains("https://a.test/x/y"));
    }
}
