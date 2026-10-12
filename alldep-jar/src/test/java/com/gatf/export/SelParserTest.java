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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.gatf.export.SelScript.Kind;
import com.gatf.export.SelScript.Step;

/** Δ160 — SelParser: the real parser's quoting, comment, opt-out, wait and block rules. */
class SelParserTest {

    static List<Step> parse(String src) {
        return new SelParser().parse("t", src).steps;
    }

    static Step one(String line) {
        List<Step> s = parse(line);
        assertEquals(1, s.size(), "steps of " + line);
        return s.get(0);
    }

    @Test
    void linesSplitOnCrLfAndCrLf() {
        assertEquals(Arrays.asList("a", "b", "c", "", "d"), SelParser.splitLines("a\r\nb\rc\n\nd\n"));
        assertEquals(Arrays.asList("x\u2028y"), SelParser.splitLines("x\u2028y"));   // U+2028 is not a seleasy line break
    }

    @Test
    void quotingFollowsSanitize() {
        SelParser.Masked m = SelParser.mask("type \"it's 'here'\" id@'a b'");
        assertEquals(Arrays.asList("type", "\"it's 'here'\"", "id@'a b'"), m.tokens(m.text));
        SelParser.Masked n = SelParser.mask("jsvar v 'return \"1 2\"'");                     // double inside single
        assertEquals(Arrays.asList("jsvar", "v", "'return \"1 2\"'"), n.tokens(n.text));
        Step s = one("type 'a \\\"b\\\" c' id@q");
        assertEquals("a \\\"b\\\" c", s.text);                                                // outer quotes stripped, nothing unescaped
    }

    @Test
    void trailingCommentNeedsWhitespaceAndStaysOutOfQuotes() {
        Step s = one("goto http://x.test/a //go home");
        assertEquals(Kind.GOTO, s.kind);
        assertEquals("http://x.test/a", s.text);
        assertEquals("go home", s.trailingComment);
        Step q = one("type 'a //not a comment' id@q");
        assertEquals("a //not a comment", q.text);
        assertNull(q.trailingComment);
    }

    @Test
    void noWaitForms() {
        Step a = one("click! css@#fast");
        assertTrue(a.noWait);
        assertEquals("click", a.verb);
        Step b = one("nowait ??10 id@x click");
        assertTrue(b.noWait);
        assertEquals(Kind.ACTION, b.kind);
        assertEquals(Integer.valueOf(10), b.timeoutSec);
        Step c = one("nowait hover id@x");
        assertTrue(c.noWait);
        assertEquals("hover", c.verb);
    }

    @Test
    void waitFormsFollowTheTranspiler() {
        assertEquals("gone", one("??+ id@x").verb);
        assertEquals("gone", one("wait till visible id@x").verb);
        assertEquals("present", one("??- id@x").verb);
        assertEquals("present", one("?? id@x").verb);
        assertEquals("present", one("wait for id@x").verb);
        Step soft = one("??~5 id@'count' text !='0'");
        assertTrue(soft.soft);
        assertEquals(Integer.valueOf(5), soft.timeoutSec);
        assertEquals(Kind.ASSERT, soft.kind);
        assertEquals("!=", soft.assertion.op);
        assertEquals("0", soft.assertion.value);
        Step typed = one("??10 id@username type \"user\"");
        assertEquals(Kind.ACTION, typed.kind);
        assertEquals("type", typed.verb);
        assertEquals("user", typed.text);
        assertEquals("username", typed.target.value);
    }

    @Test
    void assertionOperators() {
        assertEquals("contains", one("?? id@x text %abc%").assertion.op);
        assertEquals("startsWith", one("?? id@x text %abc").assertion.op);
        assertEquals("endsWith", one("?? id@x text abc%").assertion.op);
        assertEquals("<=", one("?? id@x text <='3'").assertion.op);
        Step l = one("?? id@x text =['a', 'b']");
        assertEquals(Arrays.asList("a", "b"), l.assertion.values);
        Step page = one("?? title ='Home'");
        assertTrue(page.assertion.pageLevel);
        assertEquals("title", page.assertion.property);
        Step attr = one("?? id@x attr@data-id ='7'");
        assertEquals("attr", attr.assertion.property);
        assertEquals("data-id", attr.assertion.name);
    }

    @Test
    void semanticLocatorsUseSemanticLocatorsParse() {
        Step s = one("click role@heading[level=2][name='Orders (3)']");
        assertNotNull(s.target.semantic);
        assertEquals("heading", s.target.semantic.get("role"));
        assertEquals(2, s.target.semantic.get("level"));
        assertEquals("full", s.target.semantic.get("mode"));
        Step bad = one("click role@button[name=]");
        assertEquals(Kind.UNSUPPORTED, bad.kind);
        Step tpl = one("click label@'$v{who} name'");
        assertEquals(Kind.ACTION, tpl.kind);
        assertEquals("$v{who} name", tpl.target.semantic.get("value"));
    }

    @Test
    void blocksMustOpenOnTheNextLine() {
        List<Step> s = parse("subtest \"a\"\n{\n  click id@x\n}\nsubtest \"b\"\n\n{\nclick id@y\n}\n");
        assertEquals(Kind.SUBTEST, s.get(0).kind);
        assertEquals(4, s.get(0).endLine);
        assertEquals(1, s.get(0).children.get(0).depth);
        assertEquals(Kind.UNSUPPORTED, s.get(1).kind, "a blank line before '{' is not a block");
        assertEquals(Kind.UNSUPPORTED, s.get(2).kind, "the orphan block");
        assertEquals("{", s.get(2).keyword);
    }

    @Test
    void rawBlocksSkipToTheMatchingBrace() {
        List<Step> s = parse("curl post http://a\n{\n[\nX: y\n]\n{\n}\n<a>}</a>\n}\nclick id@z\n");
        assertEquals(Kind.UNSUPPORTED, s.get(0).kind);
        assertTrue(s.get(0).rawBlock);
        assertEquals(9, s.get(0).endLine);
        assertEquals(Kind.ACTION, s.get(1).kind);
    }

    @Test
    void codeBlocksAndCommentsSwallowTheirLines() {
        List<Step> s = parse("<<<(java)\n}\n{\n>>>\n/* one line */\nclick id@a\nend */\nclick id@b\n");
        assertEquals(Kind.UNSUPPORTED, s.get(0).kind);
        assertEquals(4, s.get(0).endLine);
        assertEquals(Kind.COMMENT, s.get(1).kind);
        assertEquals(7, s.get(1).endLine, "a one-line /* */ stays open up to the next */, as Command.parse does");
        assertEquals(Kind.ACTION, s.get(2).kind);
    }

    @Test
    void transpilerQuirksAreNotExportedSilently() {
        Step glob = one("route **/api/users fulfill 200 users.json");
        assertEquals(Kind.UNSUPPORTED, glob.kind);
        assertTrue(glob.reason.contains("end of a block comment"));
        assertEquals(Kind.ROUTE, one("route '**/api/users' fulfill 200 users.json").kind);
        Step elseIfNot = one("else ifnot id@x");
        assertEquals(Kind.UNSUPPORTED, elseIfNot.kind);
        assertEquals(Kind.UNSUPPORTED, one("click id@'\uE0000\uE001'").kind);
    }

    @Test
    void neverThrowsAndNeverLosesALine() {
        String[] nasty = { "}", "{", "??", "?? ", "click", "type", "select x", "@call", "subtest", "route", "clock set", "emulate",
            "/*", "<<<", "[", "#j", "var", "var x", "'unterminated", "\"", "??10", "^", "nowait", "click!", "?? id@x text",
            "screenshot compare", "ele-screenshot", "#tv a b", "jsvar", "scroll", "sleep x", "timeout", "goto", "open" };
        for (String n : nasty) {
            List<Step> s = parse(n + "\n");
            assertFalse(s.isEmpty(), "no step for '" + n + "'");
        }
    }
}
