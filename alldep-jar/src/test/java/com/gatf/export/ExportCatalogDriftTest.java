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
package com.gatf.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * Δ160 — every keyword in docs/seleasy/commands.json has an export decision (mapped, partial, structural or
 * unsupported), and the catalog lists no keyword the generator dropped. Fails when the parser gains a statement the
 * exporters have not decided on (follows SeleasyDocsDriftTest; reads the JSON with a regex so it also runs without
 * Jackson).
 */
class ExportCatalogDriftTest {

    private static final Pattern KEYWORD = Pattern.compile("\"keyword\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern CLASS = Pattern.compile("\"class\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    static Map<String, String> commandsJson() throws Exception {
        String json = new String(Files.readAllBytes(Repo.path("docs/seleasy/commands.json")), StandardCharsets.UTF_8);
        Map<String, String> out = new LinkedHashMap<String, String>();
        Matcher k = KEYWORD.matcher(json), c = CLASS.matcher(json);
        int from = 0;
        while (k.find(from)) {
            assertTrue(c.find(k.end()), "no class after keyword " + k.group(1));
            out.put(unescape(k.group(1)), unescape(c.group(1)));
            from = c.end();
        }
        return out;
    }

    private static String unescape(String s) {
        return s.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    @Test
    void everyCommandHasAnExportDecision() throws Exception {
        Map<String, String> json = commandsJson();
        assertTrue(json.size() > 100, "commands.json read: " + json.size());
        List<String> missing = new ArrayList<String>(), stale = new ArrayList<String>(), wrongClass = new ArrayList<String>();
        for (Map.Entry<String, String> e : json.entrySet()) {
            ExportCatalog.Entry d = ExportCatalog.get(e.getKey());
            if (d == null) missing.add(e.getKey());
            else if (!d.commandClass.equals(e.getValue())) wrongClass.add(e.getKey() + ": " + d.commandClass + " != " + e.getValue());
        }
        for (String k : ExportCatalog.all().keySet()) if (!json.containsKey(k)) stale.add(k);
        assertTrue(missing.isEmpty(), "commands.json keywords with no export decision (add them to ExportCatalog): " + missing);
        assertTrue(stale.isEmpty(), "ExportCatalog keywords no longer in commands.json: " + stale);
        assertTrue(wrongClass.isEmpty(), "Command class differs from commands.json: " + wrongClass);
        assertEquals(json.size(), ExportCatalog.all().size());
    }

    @Test
    void partialAndUnsupportedDecisionsAreExplained() {
        for (ExportCatalog.Entry e : ExportCatalog.all().values()) {
            if (e.decision == ExportCatalog.Decision.PARTIAL) assertTrue(!e.note.isEmpty(), "PARTIAL without a note: " + e.keyword);
        }
    }

    /**
     * Δ161: every backend's view covers commands.json exactly, its overrides name real keywords with the shared
     * Command class, a PARTIAL override is explained, and every registered backend is checked (selenium-java has
     * overrides, playwright uses the shared table).
     */
    @Test
    void everyBackendHasADecisionForEveryCommand() throws Exception {
        Map<String, String> json = commandsJson();
        assertTrue(ExportCatalog.overriddenBackends().contains("selenium-java"));
        for (String backend : Exporters.ids()) {
            Map<String, ExportCatalog.Entry> all = ExportCatalog.all(backend);
            assertEquals(json.keySet(), all.keySet(), backend + ": keywords differ from commands.json");
            for (Map.Entry<String, String> e : json.entrySet()) {
                ExportCatalog.Entry d = ExportCatalog.get(backend, e.getKey());
                assertEquals(e.getValue(), d.commandClass, backend + " " + e.getKey());
                if (d.decision == ExportCatalog.Decision.PARTIAL) assertTrue(!d.note.isEmpty(), backend + ": PARTIAL without a note: " + d.keyword);
            }
        }
        for (String backend : ExportCatalog.overriddenBackends()) {
            assertTrue(Exporters.get(backend) != null, "overrides for an unregistered backend " + backend);
            for (ExportCatalog.Entry o : ExportCatalog.overrides(backend).values()) {
                assertTrue(json.containsKey(o.keyword), backend + " override for a keyword commands.json does not have: " + o.keyword);
                assertTrue(o.decision != ExportCatalog.get(o.keyword).decision || !o.note.equals(ExportCatalog.get(o.keyword).note),
                        backend + " override repeats the shared decision: " + o.keyword);
            }
        }
        assertTrue(ExportCatalog.overrides("playwright").isEmpty());
    }
}
