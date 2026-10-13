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
package com.gatf.generator.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatf.ai.AiConfig;
import com.gatf.ai.AiFakes;

/** H1 (Δ151): {@code gatf plan}: arguments, default outputs, no overwrite, AI from the config, no key anywhere. */
public class PlanCliTest {

    static final String KEY = "sk-cli-PLANLEAK-0123456789abcdefXYZ";
    static final ObjectMapper OM = new ObjectMapper();

    static Path crawl(Path dir) throws Exception {
        Path f = dir.resolve("shop-coverage.json");
        try (InputStream in = PlanCliTest.class.getResourceAsStream("/plan/shop-coverage.json")) {
            Files.write(f, in.readAllBytes());
        }
        return f;
    }

    static final class Io {
        final ByteArrayOutputStream o = new ByteArrayOutputStream(), e = new ByteArrayOutputStream();
        final PrintStream out = new PrintStream(o, true), err = new PrintStream(e, true);
        String out() { return o.toString(StandardCharsets.UTF_8); }
        String err() { return e.toString(StandardCharsets.UTF_8); }
    }

    @Test
    void writesJsonAndMarkdownBesideTheMapAndNeverOverwrites(@TempDir Path dir) throws Exception {
        crawl(dir);
        List<File> opened = new ArrayList<File>();
        Io io = new Io();
        int rc = PlanCli.run(new String[] { "shop-coverage.json" }, dir.toFile(), io.out, io.err, f -> { opened.add(f); return null; });
        assertEquals(0, rc, io.err());
        assertTrue(opened.isEmpty(), "no config here: the opener is not called");
        assertTrue(Files.isRegularFile(dir.resolve("shop-plan.json")) && Files.isRegularFile(dir.resolve("shop-plan.md")));
        @SuppressWarnings("unchecked") Map<String, Object> plan = OM.readValue(dir.resolve("shop-plan.json").toFile(), Map.class);
        assertEquals("gatf-test-plan", plan.get("format"));
        assertEquals(6, ((List<?>) plan.get("routes")).size());
        assertTrue(io.out().contains("plan: 6 route(s) (5 navigable)") && io.out().contains("AI: AI not configured: no config"), io.out());

        Io again = new Io();
        assertEquals(1, PlanCli.run(new String[] { "shop-coverage.json" }, dir.toFile(), again.out, again.err, f -> null));
        assertTrue(again.err().contains("exists (use --force"), again.err());
        assertEquals(0, PlanCli.run(new String[] { "shop-coverage.json", "--force", "--no-ai", "-o", "out/p.json", "--max-routes", "2" }, dir.toFile(), again.out, again.err, f -> null));
        assertTrue(Files.isRegularFile(dir.resolve("out/p.json")) && Files.isRegularFile(dir.resolve("out/p.md")));
        assertTrue(again.out().contains("4 not planned (cap)"), again.out());
    }

    @Test
    void badArgumentsAndBadMaps(@TempDir Path dir) throws Exception {
        crawl(dir);
        Io io = new Io();
        assertEquals(1, PlanCli.run(new String[] {}, dir.toFile(), io.out, io.err, f -> null));
        assertEquals(1, PlanCli.run(new String[] { "shop-coverage.json", "--max-routes", "0" }, dir.toFile(), io.out, io.err, f -> null));
        assertEquals(1, PlanCli.run(new String[] { "shop-coverage.json", "--bogus" }, dir.toFile(), io.out, io.err, f -> null));
        assertEquals(1, PlanCli.run(new String[] { "shop-coverage.json", "-o", "shop-coverage.json", "--force" }, dir.toFile(), io.out, io.err, f -> null));
        assertTrue(io.err().contains("would overwrite the crawl map"), io.err());
        Files.write(dir.resolve("bad.json"), "{\"nope\":1}".getBytes(StandardCharsets.UTF_8));
        assertEquals(1, PlanCli.run(new String[] { "bad.json" }, dir.toFile(), io.out, io.err, f -> null));
        assertTrue(io.err().contains("not a crawl map"), io.err());
        Files.write(dir.resolve("broken.json"), "[{".getBytes(StandardCharsets.UTF_8));
        assertEquals(1, PlanCli.run(new String[] { "broken.json" }, dir.toFile(), io.out, io.err, f -> null));
        assertTrue(io.err().contains("not valid JSON"), io.err());
        assertEquals(1, PlanCli.run(new String[] { "missing.json" }, dir.toFile(), io.out, io.err, f -> null));
    }

    @Test
    void aiFromTheConfigAndNoKeyAnywhere(@TempDir Path dir) throws Exception {
        crawl(dir);
        Files.write(dir.resolve("gatf-config.json"), "{}".getBytes(StandardCharsets.UTF_8));
        AiConfig cfg = AiFakes.config();
        cfg.setRetries(0);
        String reply = "{\"routes\":[{\"id\":\"r2\",\"journeys\":[{\"goal\":\"negative\",\"steps\":[{\"intent\":\"fill\",\"ref\":\"e1\",\"data\":\"invalid\"},{\"intent\":\"click\",\"ref\":\"e3\"}]}]}]} " + KEY;
        AiFakes.Sender s = new AiFakes.Sender().then(AiFakes.status(403, "{\"message\":\"key " + KEY + " refused\"}")).then(AiFakes.ok(AiFakes.reply(reply, 5, 5)));
        List<File> opened = new ArrayList<File>();
        Io io = new Io();
        assertEquals(0, PlanCli.run(new String[] { "shop-coverage.json" }, dir.toFile(), io.out, io.err, f -> { opened.add(f); return AiFakes.client(cfg, KEY, s); }));
        assertEquals("gatf-config.json", opened.get(0).getName(), "the default config");
        assertTrue(Files.readString(dir.resolve("shop-plan.json")).contains("AI chunk 1 of 1: http"));
        assertEquals(0, PlanCli.run(new String[] { "shop-coverage.json", "--force", "--config", "gatf-config.json" }, dir.toFile(), io.out, io.err,
                f -> AiFakes.client(cfg, KEY, s)));
        String json = Files.readString(dir.resolve("shop-plan.json")), md = Files.readString(dir.resolve("shop-plan.md"));
        assertTrue(json.contains("\"journeysAdded\" : 1") && md.contains("negative-ai1"), json);
        assertTrue(io.out().contains("1 from AI") && io.out().contains("AI: ready: fake / fake-model-1"), io.out());
        for (String t : new String[] { json, md, io.out(), io.err() }) {
            for (int i = 0; i + 12 <= KEY.length(); i++) assertFalse(t.contains(KEY.substring(i, i + 12)), "key slice @" + i);
        }
        Io none = new Io();
        assertEquals(0, PlanCli.run(new String[] { "shop-coverage.json", "--force", "--config", "nope.json" }, dir.toFile(), none.out, none.err, f -> null));
        assertTrue(none.out().contains("AI not configured: config nope.json not found"), none.out());
    }
}
