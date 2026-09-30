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

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.reflections.Reflections;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Tier-1 follow-up (M3 session) — the seleasy docs cannot drift from the parser: the three copies of the keyword
 * catalog agree, the VS Code grammar knows every word keyword, and every example printed by a command's
 * {@code toSampleSelCmd()} appears in docs/seleasy/REFERENCE.md. Regenerate with tools/gen-commands +
 * tools/gen-tmgrammar, and copy docs/seleasy/commands.json to frontend/src/config/seleasyCommands.json.
 */
class SeleasyDocsDriftTest {

    // the module dir (alldep-jar) under Maven; the repo root is its parent
    private static final Path REPO = Paths.get("").toAbsolutePath().getFileName().toString().equals("alldep-jar")
            ? Paths.get("..").toAbsolutePath().normalize() : Paths.get("").toAbsolutePath();

    private static String read(String rel) throws Exception {
        return new String(Files.readAllBytes(REPO.resolve(rel)), StandardCharsets.UTF_8);
    }

    @Test
    void catalogCopiesAgree() throws Exception {
        String docs = read("docs/seleasy/commands.json");
        assertThat(read("frontend/src/config/seleasyCommands.json")).isEqualTo(docs);
        ObjectMapper om = new ObjectMapper();
        assertThat(om.readTree(read("vscode-extension/src/commands.json")).get("commands"))
                .isEqualTo(om.readTree(docs).get("commands"));
    }

    @Test
    void grammarKnowsEveryWordKeyword() throws Exception {
        String grammar = read("vscode-extension/syntaxes/seleasy.tmLanguage.json");
        List<String> missing = new ArrayList<String>();
        for (JsonNode c : new ObjectMapper().readTree(read("docs/seleasy/commands.json")).get("commands")) {
            String k = c.get("keyword").asText();
            // whole word only (a JSON-escaped \\b counts as a boundary): "type" must not pass because of "typeText"
            if (k.matches("[a-z][a-z_]*") && !Pattern.compile("(?:(?<![A-Za-z0-9_])|(?<=\\\\b))" + Pattern.quote(k) + "(?![A-Za-z0-9_])").matcher(grammar).find()) missing.add(k);
        }
        assertThat(missing).isEmpty();
    }

    @Test
    void referenceShowsEveryExample() throws Exception {
        Set<String> ref = new HashSet<String>();
        for (String l : read("docs/seleasy/REFERENCE.md").split("\\R")) ref.add(l.trim());
        Set<Class<?>> classes = new LinkedHashSet<Class<?>>(new Reflections("com.gatf.selenium").getSubTypesOf(Command.class));
        classes.add(com.gatf.selenium.plugins.ApiPlugin.class);
        classes.add(com.gatf.selenium.plugins.CurlPlugin.class);
        classes.add(com.gatf.selenium.plugins.JsonPlugin.class);
        classes.add(com.gatf.selenium.plugins.XmlPlugin.class);
        List<String> missing = new ArrayList<String>();
        int checked = 0;
        for (Class<?> c : classes) {
            Method m;
            try {
                m = c.getMethod("toSampleSelCmd");
            } catch (NoSuchMethodException e) {
                continue;
            }
            if (!Modifier.isStatic(m.getModifiers()) || !m.getReturnType().equals(String[].class)) continue;
            String[] lines = (String[]) m.invoke(null);
            if (lines == null) continue;
            boolean examples = false;
            for (String entry : lines) {
                if (entry == null) continue;
                if (entry.trim().equals("Examples :-")) { examples = true; continue; }
                if (!examples) continue;
                for (String l : entry.split("\n")) {
                    String t = l.trim();
                    if (t.isEmpty()) continue;
                    checked++;
                    if (!ref.contains(t) && !ref.contains("### " + t)) missing.add(c.getSimpleName() + ": " + t);
                }
            }
        }
        assertThat(checked).isGreaterThan(100);
        assertThat(missing).isEmpty();
    }
}
