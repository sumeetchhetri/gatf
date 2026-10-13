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
package com.gatf.ai.heal;

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
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatf.ai.AiClient;
import com.gatf.ai.AiFakes;
import com.gatf.selenium.Command;

/**
 * H1 (Δ149): every line the healer proposes — deterministic and model-sourced, locator, wait and goto — goes through
 * the real seleasy pipeline ({@link Command#validateSel}: transpile + compile, no browser), as Δ148's pasted lines do.
 */
public class HealerValidationTest {

    @Test
    @SuppressWarnings("unchecked")
    void everyProposedLineValidatesInAScript(@TempDir Path dir) throws Exception {
        ObjectMapper om = new ObjectMapper();
        List<String> lines = new ArrayList<String>();
        Path zip = RecordedFailure.record(dir);
        Healer.Options always = new Healer.Options();
        always.ai = Healer.AiMode.ALWAYS;
        String reply = "{\"candidates\":[{\"kind\":\"element\",\"i\":3,\"waitSeconds\":5},{\"kind\":\"role\",\"role\":\"button\",\"name\":\"Back\"},"
                + "{\"kind\":\"label\",\"value\":\"PIN code\"},{\"kind\":\"css\",\"value\":\"div.actions > button.btn-primary\"},"
                + "{\"kind\":\"goto\",\"url\":\"https://shop.example.test/checkout?step=2\"}]}";
        String[] steps = { "click id@place-order", "??10 id@place-order click", "type 'x' name@pincode", "click css@'button.go'", "  click id@city" };
        for (String step : steps) {
            AiClient ai = AiFakes.client(AiFakes.config(), "k-123456789012345678", new AiFakes.Sender().then(AiFakes.ok(AiFakes.reply(reply, 1, 1))));
            Map<String, Object> r = new Healer(om).heal(step, null, 0, null, TraceFailure.read(zip, om), ai, always);
            for (Map<String, Object> p : (List<Map<String, Object>>) r.get("proposals")) lines.addAll((List<String>) p.get("lines"));
        }
        assertTrue(lines.size() >= 12, lines.toString());
        Path work = Paths.get("target", "heal-validate").toAbsolutePath();
        Path tests = work.resolve("tests");
        Files.createDirectories(tests);
        Files.copy(Paths.get("src/test/resources/codegen-corpus/gatf-config.xml"), work.resolve("gatf-config.xml"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        List<String> bad = new ArrayList<String>();
        for (int i = 0; i < lines.size(); i++) {
            String name = "heal-" + i + ".sel";
            Files.write(tests.resolve(name), ("open chrome\ngoto https://shop.example.test/checkout\n" + lines.get(i) + "\n").getBytes(StandardCharsets.UTF_8));
            String res = Command.validateSel(new String[] { "-validate-sel", "tests" + File.separator + name, "gatf-config.xml", work.toString() }, null, false);
            Map<String, Object> m = om.readValue(res, Map.class);
            if (!"SUCCESS".equals(m.get("status"))) bad.add(lines.get(i) + " -> " + m.get("error"));
        }
        if (!bad.isEmpty()) fail("validateSel rejected proposed lines:\n  " + String.join("\n  ", bad));
    }
}
