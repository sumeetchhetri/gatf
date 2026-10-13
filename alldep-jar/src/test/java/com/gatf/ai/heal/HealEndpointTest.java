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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatf.ai.AiClient;

/**
 * H1 (Δ150): the editor's heal endpoints ({@link HealEndpoint}) and Accept ({@link HealApply}) on a real recorded
 * failure ({@link RecordedFailure}): heal, accept, conflict, validate-fail, confinement and the step rules.
 */
public class HealEndpointTest {

    static final ObjectMapper OM = new ObjectMapper();
    static final String OK = "{\"status\":\"SUCCESS\",\"error\":null}";
    static final String TRACE = "/reports/run1/checkout-trace.zip";

    /** A project: out/run1/checkout-trace.zip (the recorded failure) and tests/checkout.sel. */
    static final class Proj {
        Path root, out, tests, sel;
        final List<String> validated = new ArrayList<String>();
        final List<Path> candidates = new ArrayList<Path>();
        String verdict = OK;
        RuntimeException throwing;
        Runnable during;
        final HealApply.Tickets tickets = new HealApply.Tickets();

        HealEndpoint ep() {
            return new HealEndpoint(out, List.of(tests, root), () -> AiClient.notConfigured("no <ai> block in the config"), cand -> {
                candidates.add(cand);
                validated.add(new String(Files.readAllBytes(cand), StandardCharsets.UTF_8));
                if (during != null) during.run();
                if (throwing != null) throw throwing;
                return verdict;
            }, tickets, OM, null);
        }
    }

    static Proj proj(Path dir, String script) throws Exception {
        Proj p = new Proj();
        p.root = Files.createDirectories(dir.resolve("proj"));
        p.out = Files.createDirectories(p.root.resolve("out"));
        p.tests = Files.createDirectories(p.root.resolve("tests"));
        RecordedFailure.record(Files.createDirectories(p.out.resolve("run1")));
        p.sel = p.tests.resolve("checkout.sel");
        Files.write(p.sel, script.getBytes(StandardCharsets.UTF_8));
        return p;
    }

    static String script() {
        return String.join("\n", RecordedFailure.SCRIPT) + "\n";
    }

    static Map<String, Object> body(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> proposals(Object r) {
        return (List<Map<String, Object>>) ((Map<String, Object>) r).get("proposals");
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> m(Object o) {
        return (Map<String, Object>) o;
    }

    static List<Path> leftovers(Path d) throws Exception {
        try (Stream<Path> s = Files.walk(d)) {
            List<Path> out = new ArrayList<Path>();
            s.filter(p -> p.getFileName().toString().contains(".heal-")).forEach(out::add);
            return out;
        }
    }

    @Test
    void acceptHealsTheRecordedFailureAndWritesOnlyTheValidatedLine(@TempDir Path dir) throws Exception {
        Proj p = proj(dir, script());
        HealEndpoint ep = p.ep();
        HealEndpoint.Reply h = ep.heal(body("trace", TRACE));
        assertEquals(200, h.status, String.valueOf(h.body));
        Map<String, Object> r = m(h.body);
        assertEquals("proposed", r.get("status"));
        assertEquals("checkout.sel", r.get("file"));
        assertEquals("trace", r.get("fileSource"));
        assertEquals("run1/checkout-trace.zip", r.get("trace"));
        assertEquals(5, r.get("line"));
        assertEquals("recorded-failure", r.get("stepSource"));
        assertEquals("not-configured", r.get("ai"));
        assertTrue(String.valueOf(r.get("aiStatus")).startsWith("AI not configured: no <ai> block"), String.valueOf(r.get("aiStatus")));
        Map<String, Object> top = proposals(r).get(0);
        assertEquals("deterministic", top.get("source"));
        assertEquals(List.of("click testid@place-order-btn"), top.get("lines"));
        assertEquals(true, top.get("strict"));
        assertEquals(script(), new String(Files.readAllBytes(p.sel), StandardCharsets.UTF_8), "heal writes nothing");
        String id = (String) r.get("healId");
        assertNotNull(id);

        HealEndpoint.Reply a = ep.apply(body("healId", id, "rank", 1));
        assertEquals(200, a.status, String.valueOf(a.body));
        Map<String, Object> ar = m(a.body);
        assertEquals("applied", ar.get("status"));
        List<String> expected = new ArrayList<String>(RecordedFailure.SCRIPT);
        expected.set(4, "click testid@place-order-btn");
        String want = String.join("\n", expected) + "\n";
        assertEquals(want, new String(Files.readAllBytes(p.sel), StandardCharsets.UTF_8));
        assertEquals(want, ar.get("content"), "the editor buffer gets exactly what was written");
        assertEquals(List.of(want), p.validated, "the result was validated before the write");
        assertEquals(List.of(), leftovers(p.root), "the validation copy is removed");
        StepLocator healed = StepLocator.find(Files.readAllLines(p.sel).get(4));
        assertEquals("testid@place-order-btn", healed.token());
        assertEquals("click", healed.verb());

        HealEndpoint.Reply again = ep.apply(body("healId", id, "rank", 1));
        assertEquals(409, again.status, "a second accept of the same heal is a conflict: the line already changed");
        assertEquals(want, new String(Files.readAllBytes(p.sel), StandardCharsets.UTF_8));
    }

    @Test
    void theEditorCanHealFromTheFileAloneWithTheNewestFailingRun(@TempDir Path dir) throws Exception {
        Proj p = proj(dir, script());
        Map<String, Object> r = m(p.ep().heal(body("file", "checkout.sel")).body);
        assertEquals("proposed", r.get("status"), r.toString());
        assertEquals("run1/checkout-trace.zip", r.get("trace"));
        assertEquals("argument", r.get("fileSource"));
        HealEndpoint.Reply none = p.ep().heal(body("file", "other.sel"));
        assertEquals(400, none.status);
        Files.write(p.tests.resolve("other.sel"), "click id@x\n".getBytes(StandardCharsets.UTF_8));
        HealEndpoint.Reply nf = p.ep().heal(body("file", "other.sel"));
        assertEquals(404, nf.status, "no failing traced run of other.sel");
    }

    @Test
    void conflictWhenTheTargetLineChangedWritesNothing(@TempDir Path dir) throws Exception {
        Proj p = proj(dir, script());
        HealEndpoint ep = p.ep();
        String id = (String) m(ep.heal(body("trace", TRACE)).body).get("healId");
        String edited = script().replace("click id@place-order", "click id@place-order-now");
        Files.write(p.sel, edited.getBytes(StandardCharsets.UTF_8));
        HealEndpoint.Reply a = ep.apply(body("healId", id, "rank", 1));
        assertEquals(409, a.status);
        assertEquals("conflict", m(a.body).get("status"));
        assertEquals("click id@place-order", m(a.body).get("expected"));
        assertEquals("click id@place-order-now", m(a.body).get("actual"));
        assertEquals(edited, new String(Files.readAllBytes(p.sel), StandardCharsets.UTF_8));
        assertTrue(p.validated.isEmpty(), "nothing validated, nothing written");

        // lines inserted above: the line number moved, so it is a conflict too (never a write to a moved line)
        Files.write(p.sel, ("# note\n" + script()).getBytes(StandardCharsets.UTF_8));
        assertEquals(409, ep.apply(body("healId", id, "rank", 1)).status);
        Files.delete(p.sel);
        assertEquals(409, ep.apply(body("healId", id, "rank", 1)).status, "a deleted file is a conflict");
        assertFalse(Files.exists(p.sel));
    }

    @Test
    void aFixThatDoesNotValidateIsNotWritten(@TempDir Path dir) throws Exception {
        Proj p = proj(dir, script());
        HealEndpoint ep = p.ep();
        String id = (String) m(ep.heal(body("trace", TRACE)).body).get("healId");
        p.verdict = "{\"status\":\"FAILURE\",\"error\":\"Invalid command at line 5 in file " + "PLACEHOLDER\"}";
        p.during = () -> p.verdict = p.verdict.replace("PLACEHOLDER", p.candidates.get(p.candidates.size() - 1).getFileName().toString());
        HealEndpoint.Reply a = ep.apply(body("healId", id, "rank", 1));
        assertEquals(422, a.status);
        assertEquals("invalid", m(a.body).get("status"));
        String v = String.valueOf(m(a.body).get("validation"));
        assertTrue(v.contains("FAILURE") && v.contains("checkout.sel") && !v.contains(".heal-"), v);
        assertEquals(script(), new String(Files.readAllBytes(p.sel), StandardCharsets.UTF_8));
        assertEquals(List.of(), leftovers(p.root));

        p.during = null;
        p.throwing = new IllegalStateException("no compiler");
        HealEndpoint.Reply t = ep.apply(body("healId", id, "rank", 1));
        assertEquals(422, t.status);
        assertTrue(String.valueOf(m(t.body).get("validation")).startsWith("validation unavailable"), String.valueOf(t.body));
        assertEquals(script(), new String(Files.readAllBytes(p.sel), StandardCharsets.UTF_8));

        p.throwing = null;
        p.verdict = "not json at all";
        assertEquals(422, ep.apply(body("healId", id, "rank", 1)).status, "anything but SUCCESS is invalid");
        assertEquals(script(), new String(Files.readAllBytes(p.sel), StandardCharsets.UTF_8));
        assertEquals(List.of(), leftovers(p.root));
    }

    @Test
    void aFileEditedWhileValidatingIsNotOverwritten(@TempDir Path dir) throws Exception {
        Proj p = proj(dir, script());
        HealEndpoint ep = p.ep();
        String id = (String) m(ep.heal(body("trace", TRACE)).body).get("healId");
        String saved = script() + "# saved meanwhile\n";
        p.during = () -> {
            try { Files.write(p.sel, saved.getBytes(StandardCharsets.UTF_8)); } catch (Exception e) { throw new IllegalStateException(e); }
        };
        HealEndpoint.Reply a = ep.apply(body("healId", id, "rank", 1));
        assertEquals(409, a.status);
        assertEquals(saved, new String(Files.readAllBytes(p.sel), StandardCharsets.UTF_8));
    }

    @Test
    void crlfAndEveryOtherByteAreKept(@TempDir Path dir) throws Exception {
        String crlf = String.join("\r\n", RecordedFailure.SCRIPT);   // CRLF, no final newline
        Proj p = proj(dir, crlf);
        HealEndpoint ep = p.ep();
        String id = (String) m(ep.heal(body("trace", TRACE)).body).get("healId");
        assertEquals(200, ep.apply(body("healId", id, "rank", 1)).status);
        assertEquals(crlf.replace("\r\nclick id@place-order\r\n", "\r\nclick testid@place-order-btn\r\n"),
                new String(Files.readAllBytes(p.sel), StandardCharsets.UTF_8));
        assertEquals("a\r\nB\nC\nd", HealApply.replace("a\r\nb\nd", 2, List.of("B", "C")), "added lines take the healed line's own ending");
        assertEquals("a\nX\nY", HealApply.replace("a\nb", 2, List.of("X", "Y")), "a last line without a newline gets the file's ending");
    }

    @Test
    void pathsStayInsideTheirDirectories(@TempDir Path dir) throws Exception {
        Proj p = proj(dir, script());
        Path outside = Files.createDirectories(dir.resolve("outside"));
        Path foreign = RecordedFailure.record(outside);
        Files.write(outside.resolve("evil.sel"), script().getBytes(StandardCharsets.UTF_8));
        Files.createSymbolicLink(p.out.resolve("link-trace.zip"), foreign);
        Files.createSymbolicLink(p.tests.resolve("link.sel"), outside.resolve("evil.sel"));
        Files.createSymbolicLink(p.out.resolve("linkdir"), outside);
        HealEndpoint ep = p.ep();
        for (String t : new String[] { "../outside/checkout-trace.zip", "/reports/../../outside/checkout-trace.zip", foreign.toString(),
                "link-trace.zip", "linkdir/checkout-trace.zip", "run1/../../../outside/checkout-trace.zip", "run1/nope-trace.zip", "a\0b" }) {
            HealEndpoint.Reply r = ep.heal(body("trace", t, "file", "checkout.sel"));
            assertEquals(400, r.status, t + " → " + r.body);
        }
        for (String f : new String[] { "../outside/evil.sel", outside.resolve("evil.sel").toString(), "link.sel", "../tests/../../outside/evil.sel", "checkout.txt" }) {
            HealEndpoint.Reply r = ep.heal(body("trace", TRACE, "file", f));
            assertEquals(400, r.status, f + " → " + r.body);
        }
        assertEquals(script(), new String(Files.readAllBytes(outside.resolve("evil.sel")), StandardCharsets.UTF_8));
        assertEquals(400, ep.heal(body()).status);
        assertEquals(400, ep.heal(body("trace", List.of("x"))).status, "a non-string argument is bad input");
    }

    @Test
    void theStepIsGivenOrRecordedNeverGuessed(@TempDir Path dir) throws Exception {
        Proj p = proj(dir, script());
        HealEndpoint ep = p.ep();
        assertEquals(5, m(ep.heal(body("trace", TRACE, "step", "5")).body).get("line"));
        assertEquals("argument", m(ep.heal(body("trace", TRACE, "step", "5")).body).get("stepSource"));
        assertEquals(5, m(ep.heal(body("trace", TRACE, "step", "  click id@place-order ")).body).get("line"));
        assertEquals(400, ep.heal(body("trace", TRACE, "step", "99")).status);
        assertEquals(400, ep.heal(body("trace", TRACE, "step", "0")).status);
        assertEquals(400, ep.heal(body("trace", TRACE, "step", "click id@nowhere")).status);
        assertEquals(400, ep.heal(body("trace", TRACE, "step", "a\nb")).status);
        // step 4 heals another line: no proposal for a line whose locator still matches strictly except a wait
        assertEquals(4, m(ep.heal(body("trace", TRACE, "step", 4)).body).get("line"));

        // the same failing step on two lines: ambiguous, so it must be named
        List<String> two = new ArrayList<String>(RecordedFailure.SCRIPT);
        two.add("click id@place-order");
        Files.write(p.sel, (String.join("\n", two) + "\n").getBytes(StandardCharsets.UTF_8));
        HealEndpoint.Reply amb = ep.heal(body("trace", TRACE));
        assertEquals(400, amb.status);
        assertTrue(String.valueOf(amb.body).contains("[5, 7]") && String.valueOf(amb.body).contains("pass step"), String.valueOf(amb.body));
        assertEquals(400, ep.heal(body("trace", TRACE, "step", "click id@place-order")).status, "the text is on two lines");
        assertEquals(7, m(ep.heal(body("trace", TRACE, "step", "7")).body).get("line"));

        // a token is matched whole: id@place does not match "id@place-order"
        assertTrue(HealRun.containsToken("Element not found: id@place-order (timeout 10s)", "id@place-order"));
        assertFalse(HealRun.containsToken("Element not found: id@place-order (timeout 10s)", "id@place"));
        assertFalse(HealRun.containsToken("x", ""));

        // a trace that recorded no failure: the step must be given
        Path ok = Files.createDirectories(p.out.resolve("run2/pass-trace"));
        Files.write(ok.resolve("events.ndjson"), "{\"type\":\"action\",\"name\":\"click\"}\n".getBytes(StandardCharsets.UTF_8));
        HealEndpoint.Reply nf = ep.heal(body("trace", "run2/pass-trace", "file", "checkout.sel"));
        assertEquals(400, nf.status);
        assertTrue(String.valueOf(nf.body).contains("no recorded failure") && String.valueOf(nf.body).contains("pass step"), String.valueOf(nf.body));
        Map<String, Object> given = m(ep.heal(body("trace", "run2/pass-trace", "file", "checkout.sel", "step", "5")).body);
        assertEquals("no-snapshot", given.get("status"), "with a step given, the heal runs and says why it cannot propose");
        assertNull(given.get("healId"));
    }

    @Test
    void fileWinsOverTheTracesScriptAndIsNamedInTheReply(@TempDir Path dir) throws Exception {
        Proj p = proj(dir, script());
        Files.createDirectories(p.tests.resolve("sub"));
        Files.write(p.tests.resolve("sub/other.sel"), "open chrome\nclick id@place-order\n".getBytes(StandardCharsets.UTF_8));
        Map<String, Object> r = m(p.ep().heal(body("trace", TRACE, "file", "sub/other.sel")).body);
        assertEquals("sub/other.sel", r.get("file"));
        assertEquals("argument", r.get("fileSource"));
        assertEquals(2, r.get("line"));
        assertTrue(String.valueOf(proposals(r).get(0).get("diff")).startsWith("--- a/sub/other.sel\n"));
        // a real run records selFile as the script's absolute path (Command.read: fn.getAbsolutePath())
        assertEquals(p.sel.toRealPath(), p.ep().resolveSelFile(p.sel.toAbsolutePath().toString()));
        assertNull(p.ep().resolveSelFile(dir.resolve("elsewhere.sel").toString()));
        assertNull(p.ep().resolveSelFile("../../etc/passwd"));
    }

    @Test
    void acceptTakesOnlyAKnownHealAndRank(@TempDir Path dir) throws Exception {
        Proj p = proj(dir, script());
        HealEndpoint ep = p.ep();
        String id = (String) m(ep.heal(body("trace", TRACE)).body).get("healId");
        assertEquals(410, ep.apply(body("healId", "0123456789abcdef0123456789abcdef", "rank", 1)).status);
        assertEquals(400, ep.apply(body("healId", "../../x", "rank", 1)).status);
        assertEquals(400, ep.apply(body("healId", id, "rank", 0)).status);
        assertEquals(400, ep.apply(body("healId", id, "rank", 99)).status);
        assertEquals(400, ep.apply(body("healId", id, "rank", "1; rm")).status);
        assertEquals(script(), new String(Files.readAllBytes(p.sel), StandardCharsets.UTF_8));
        // extra fields (lines, file) are ignored: Accept writes the ticket's proposal, never client text
        HealEndpoint.Reply a = ep.apply(body("healId", id, "rank", "1", "lines", List.of("click css@evil"), "file", "../x.sel"));
        assertEquals(200, a.status);
        assertFalse(new String(Files.readAllBytes(p.sel), StandardCharsets.UTF_8).contains("css@evil"));
        try {
            HealEndpoint.readBody(new java.io.ByteArrayInputStream("[1]".getBytes(StandardCharsets.UTF_8)), OM);
            throw new AssertionError("an array body must be refused");
        } catch (HealRun.BadInput e) { /* expected */ }
        try {
            HealEndpoint.readBody(new java.io.ByteArrayInputStream(new byte[HealEndpoint.MAX_BODY + 1]), OM);
            throw new AssertionError("an oversized body must be refused");
        } catch (HealRun.BadInput e) { /* expected */ }
    }
}
