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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Δ148: every line the pick locator pastes (the shared escape vectors in src/test/resources/pick-locator/, as ranked by
 * the server) goes through the real seleasy pipeline: its locator through {@link FindCommand} (the parser behind the
 * editor's diagnostics), and the line in a script through {@link Command#validateSel}, the transpile + compile check the
 * MCP {@code lint} tool and record-mode save use. Transpile only, no browser.
 */
class PickLocatorValidationTest {

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> vectors() throws Exception {
        File f = new File("src/test/resources/pick-locator/escape-vectors.json");
        return (List<Map<String, Object>>) new ObjectMapper().readValue(Files.readAllBytes(f.toPath()), Object.class);
    }

    private static FindCommand find(String expr) {
        return new FindCommand(expr, new Object[] { expr, 1, new File("t.sel"), "", null, "" }, new Command.CommandState());
    }

    @Test
    void everyPastedLocatorParsesAsAFind() throws Exception {
        List<String> bad = new ArrayList<String>();
        for (Map<String, Object> v : vectors()) {
            Object loc = v.get("locator");
            if (loc == null) continue;
            try {
                find((String) loc);
            } catch (RuntimeException e) {
                bad.add(v.get("id") + ": " + loc + " -> " + e.getMessage());
            }
        }
        if (!bad.isEmpty()) fail("FindCommand rejected pasted locators:\n  " + String.join("\n  ", bad));
    }

    @Test
    void everyPastedLineValidatesInAScript() throws Exception {
        Path work = Paths.get("target", "pick-locator-validate").toAbsolutePath();
        Path tests = work.resolve("tests");
        Files.createDirectories(tests);
        Files.copy(Paths.get("src/test/resources/codegen-corpus/gatf-config.xml"), work.resolve("gatf-config.xml"),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        List<String> bad = new ArrayList<String>();
        int checked = 0;
        for (Map<String, Object> v : vectors()) {
            Object line = v.get("line");
            if (line == null) continue;
            String name = "pick-" + v.get("id") + ".sel";
            Files.write(tests.resolve(name), ("open chrome\ngoto http://abc.com/testpage.html\n" + line + "\n").getBytes(StandardCharsets.UTF_8));
            String res = Command.validateSel(new String[] { "-validate-sel", "tests" + File.separator + name, "gatf-config.xml", work.toString() }, null, false);
            @SuppressWarnings("unchecked")
            Map<String, Object> m = new ObjectMapper().readValue(res, Map.class);
            if (!"SUCCESS".equals(m.get("status"))) bad.add(v.get("id") + ": " + line + " -> " + m.get("error"));
            checked++;
        }
        assertTrue(checked >= 15, "too few vectors with a line: " + checked);
        if (!bad.isEmpty()) fail("validateSel rejected pasted lines:\n  " + String.join("\n  ", bad));
        assertEquals(0, bad.size());
    }
}
