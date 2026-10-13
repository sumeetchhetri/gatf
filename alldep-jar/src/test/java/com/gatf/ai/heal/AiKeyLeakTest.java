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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatf.ai.AiClient;
import com.gatf.ai.AiConfig;
import com.gatf.ai.AiFakes;
import com.gatf.ai.AiSecret;

/**
 * H1 (Δ149) secrets: a provider key read from the environment never reaches a heal result, an error, stdout/stderr,
 * the serialized config, or any file in the trace — even when the provider echoes it back in an error or a reply.
 */
public class AiKeyLeakTest {

    static final String KEY = "sk-ant-LEAKCHECK-0123456789abcdefghijKLMNOP";

    /** The key, or any 12-char slice of it, in {@code text}. */
    static List<String> leaks(String where, String text) {
        List<String> out = new ArrayList<String>();
        if (text == null) return out;
        for (int i = 0; i + 12 <= KEY.length(); i++) {
            if (text.contains(KEY.substring(i, i + 12))) { out.add(where + " holds key slice @" + i); break; }
        }
        return out;
    }

    @Test
    void theKeyNeverLeavesTheProviderHeader(@TempDir Path dir) throws Exception {
        ObjectMapper om = new ObjectMapper();
        Path zip = RecordedFailure.record(dir);
        AiConfig cfg = AiFakes.config();
        cfg.setApiKeyEnv("FAKE_AI_KEY");
        cfg.setRetries(0);
        List<String> found = new ArrayList<String>();
        PrintStream out = System.out, err = System.err;
        ByteArrayOutputStream cap = new ByteArrayOutputStream();
        List<String> results = new ArrayList<String>();
        AiFakes.Sender sender = new AiFakes.Sender()
                .then(AiFakes.status(401, "{\"error\":{\"message\":\"bad key " + KEY + "\"}}"))
                .then(AiFakes.io("tunnel failed, header x-fake-key: " + KEY))
                .then(AiFakes.ok(AiFakes.reply("{\"candidates\":[{\"kind\":\"label\",\"value\":\"" + KEY.substring(0, 30) + "\"},{\"kind\":\"label\",\"value\":\"City\"}]} "
                        + KEY, 10, 10)));
        try {
            System.setOut(new PrintStream(cap, true, "UTF-8"));
            System.setErr(new PrintStream(cap, true, "UTF-8"));
            Healer.Options o = new Healer.Options();
            o.ai = Healer.AiMode.ALWAYS;
            for (int i = 0; i < 3; i++) {
                AiClient ai = AiFakes.client(cfg, KEY, sender);
                found.addAll(leaks("status", ai.status() + ai + ai.usage()));
                Map<String, Object> r = new Healer(om).heal("click id@place-order", RecordedFailure.SCRIPT, 5, "checkout.sel",
                        TraceFailure.read(zip, om), ai, o);
                String json = om.writeValueAsString(r);
                results.add(json);
                System.out.println(json);
            }
        } finally {
            System.setOut(out);
            System.setErr(err);
        }
        assertEquals(3, sender.urls.size());
        for (int i = 0; i < 3; i++) {
            assertEquals(KEY, sender.headers.get(i).get("x-fake-key"), "the header is the one place it goes");
            found.addAll(leaks("request body " + i, sender.bodies.get(i)));
            found.addAll(leaks("result " + i, results.get(i)));
        }
        assertTrue(results.get(0).contains("\"ai\":\"http\"") && results.get(0).contains("***"), results.get(0));
        assertTrue(results.get(1).contains("\"ai\":\"io\""), results.get(1));
        assertTrue(results.get(2).contains("label@City"), results.get(2));
        found.addAll(leaks("stdout/stderr", cap.toString(StandardCharsets.UTF_8)));

        // what Δ150 will persist next to the trace: the result JSON; plus the config as serialized with the run
        Files.write(dir.resolve("heal-result.json"), String.join("\n", results).getBytes(StandardCharsets.UTF_8));
        Files.write(dir.resolve("ai-config.json"), om.writeValueAsBytes(cfg));
        found.addAll(leaks("config toString", cfg.toString()));
        found.addAll(leaks("secret toString", String.valueOf(AiSecret.of(KEY))));
        try (Stream<Path> files = Files.walk(dir)) {
            for (Path p : (Iterable<Path>) files.filter(Files::isRegularFile)::iterator) {
                found.addAll(leaks(dir.relativize(p).toString(), new String(Files.readAllBytes(p), StandardCharsets.ISO_8859_1)));
                if (p.toString().endsWith(".zip")) {
                    try (ZipFile z = new ZipFile(p.toFile())) {
                        for (ZipEntry e : java.util.Collections.list(z.entries())) {
                            found.addAll(leaks(p.getFileName() + "!" + e.getName(), new String(z.getInputStream(e).readAllBytes(), StandardCharsets.ISO_8859_1)));
                        }
                    }
                }
            }
        }
        assertEquals(List.of(), found);
        assertFalse(om.writeValueAsString(cfg).toLowerCase().contains("apikey\":\"sk"), "the config has no key field to serialize");
    }
}
