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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** H1 (Δ149): finding the one locator in a step line and swapping only it. */
public class StepLocatorTest {

    @Test
    void findsTheSingleLocatorAndKeepsEverythingElse() {
        String[][] cases = {
                { "click id@save", "id@save", "id", "save", "click" },
                { "\t??10 css@\"div.a b\" click", "css@\"div.a b\"", "css", "div.a b", "click" },
                { "type 'me@example.com' label@'Email address'", "label@'Email address'", "label", "Email address", "type" },
                { "nowait click! role@button[name='Save draft']", "role@button[name='Save draft']", "role", "button[name='Save draft']", "click" },
                { "?? xpath@\"//a[@id='x']\" text = 'Hi'", "xpath@\"//a[@id='x']\"", "xpath", "//a[@id='x']", "" },
                { "select text@India id@country", null, null, null, null },   // two locators
                { "type \"a b\" testid@q", "testid@q", "testid", "q", "type" },
        };
        for (String[] c : cases) {
            StepLocator l = StepLocator.find(c[0]);
            if (c[1] == null) { assertNull(l, c[0]); continue; }
            assertNotNull(l, c[0]);
            assertEquals(c[1], l.token(), c[0]);
            assertEquals(c[2], l.by);
            assertEquals(c[3], l.classifier);
            if (!c[4].isEmpty()) assertEquals(c[4], l.verb(), c[0]);
            assertEquals(c[0].replace(c[1], "testid@new"), l.replace("testid@new"));
        }
        assertEquals("\t", StepLocator.find("\t??10 css@\"div.a b\" click").indent());
        // the runtime masks double-quoted strings first, then single-quoted ones: tokenised the same way here
        assertNull(StepLocator.find("type 'x \"y' id@a 'z\" w'"), "id@a sits inside a double-quoted string for the runtime");
        assertEquals("id@x", StepLocator.find("type 'unterminated id@x").token(), "an unterminated quote is no string to the runtime");
        assertEquals("", StepLocator.find("?? id@x title \"Home\"").verb(), "only element actions count as verbs");
    }

    @Test
    void linesWithoutExactlyOneLocatorAreNotHealable() {
        for (String s : new String[] { "", "   ", "// click id@x", "/* click id@x */", "goto https://a.test/x@y", "relative tag@input rightof label@Email",
"click this", "actions draganddrop id@a id@b", "sleep 1000" }) {
            assertNull(StepLocator.find(s), s);
        }
    }

    @Test
    void countsWhatTheSnapshotCanAnswerAndMinusOneOtherwise() {
        SnapshotResolver r = new SnapshotResolver(SnapshotDom.parse("<div id=a class='x y' name=n><span class=x>t</span></div><button>Go</button>"), null);
        assertEquals(1, StepLocator.count(r, "id", "a"));
        assertEquals(2, StepLocator.count(r, "class", "x"));
        assertEquals(1, StepLocator.count(r, "name", "n"));
        assertEquals(1, StepLocator.count(r, "css", "div.y > span"));
        assertEquals(2, StepLocator.count(r, "tagname", "DIV") + StepLocator.count(r, "tag", "span"));
        assertEquals(1, StepLocator.count(r, "role", "button[name=Go]"));
        assertEquals(1, StepLocator.count(r, "text", "=Go"));
        assertEquals(-1, StepLocator.count(r, "xpath", "//div"));
        assertEquals(-1, StepLocator.count(r, "text", "Go"), "plain text@ is the legacy contains-lookup: not answered");
        assertEquals(-1, StepLocator.count(r, "css", "div + span"));
        assertEquals(-1, StepLocator.count(r, "role", "button[bogus]"));
        assertNotNull(StepLocator.parseLocator("role@button[name='a b']"));
        assertNull(StepLocator.parseLocator("id@a id@b"));
    }
}
