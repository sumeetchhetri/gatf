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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;

import javax.xml.xpath.XPathFactory;

import org.junit.jupiter.api.Test;

/** Δ163n bug 3 (error X on the .sel line): xpath syntax, throwParseError(null) NPE, ECJ error mapping via GATF_LINE. */
class Delta163nParseTest {

    private static Command parseLine(String line, Command.CommandState st) {
        return Command.parse(new Object[] { line, 7, new java.io.File("t.sel"), "", st, "t.sel" }, st, null);
    }

    @Test
    void throwParseErrorWithNullDetailsReportsTheReasonOnTheLine() {
        // base: "Cannot load from object array because "o" is null" (the 25 throwParseError(null, ...) call sites)
        for (String[] c : new String[][] { { "scroll sideways", "scroll with one of" }, { "confirm maybe", "confirm dialog can be accepted" } }) {
            Command.CommandState st = new Command.CommandState();
            assertThatThrownBy(() -> parseLine(c[0], st)).as(c[0]).isInstanceOf(Command.GatfSelCodeParseError.class)
                    .satisfies(e -> {
                        Object[] d = ((Command.GatfSelCodeParseError) e).getDetails();
                        assertThat(d[1]).isEqualTo(7);
                        assertThat(String.valueOf(d[3])).contains(c[1]).doesNotContain("is null");
                    });
        }
    }

    @Test
    void malformedXpathIsAParseErrorAgain() {
        for (String s : new String[] { "click xpath@\"//div[\"", "?? xpath@//a][ click", "??5 relative tag@input above xpath@\"//div[\"" }) {
            Command.CommandState st = new Command.CommandState();
            assertThatThrownBy(() -> parseLine(s, st)).as(s).isInstanceOf(Command.GatfSelCodeParseError.class)
                    .satisfies(e -> assertThat(String.valueOf(((Command.GatfSelCodeParseError) e).getDetails()[3])).contains("Invalid xpath expression"));
        }
    }

    @Test
    void validTemplatedOrUnknownFunctionXpathsStillParse() {
        for (String s : new String[] { "click xpath@\"//a[text()=\\\"Log in\\\"]\"", "click xpath@\"//*[contains(text(),'a')]\"", "click xpath@\"(//a)[1]\"", "click xpath@\"//div[$v{idx}]\"",
                "click xpath@\"//a[matches(.,'x')]\"", "click xpath@\"ddd-@index\"" }) {
            assertThat(parseLine(s, new Command.CommandState())).as(s).isNotNull();
        }
    }

    @Test
    void xpathCheckIsConservative() {
        XPathFactory f = XPathFactory.newInstance();
        assertThat(XpathCheck.syntaxError(f, "//div[")).contains("Invalid xpath expression //div[");
        assertThat(XpathCheck.syntaxError(f, "//a[text()='it''s']")).isNull();   // export-corpus 11-escaping: left to the browser
        assertThat(XpathCheck.syntaxError(f, "//a[text()='x']]")).isNotNull();
        assertThat(XpathCheck.syntaxError(f, "//div[@id='x']")).isNull();
        assertThat(XpathCheck.syntaxError(f, "//a[matches(.,'x')]")).isNull();
        assertThat(XpathCheck.syntaxError(f, "//div[${i}]")).isNull();
        assertThat(XpathCheck.syntaxError(f, "//div[@{i}]")).isNull();
        assertThat(XpathCheck.syntaxError(f, "ddd-@index")).isNull();
        assertThat(XpathCheck.syntaxError(f, "//a[@x=")).isNotNull();
        assertThat(XpathCheck.syntaxError(null, null)).isNull();
        // verifier round: \" and \' reach the browser unescaped (Command.esc keeps them in the Java literal)
        assertThat(XpathCheck.syntaxError(f, "//a[text()=\\\"Log in\\\"]")).isNull();
        assertThat(XpathCheck.syntaxError(f, "//td[text()=\\\"don't\\\"]")).isNull();
        assertThat(XpathCheck.syntaxError(f, "//a[@onclick=\\\"f('x')\\\"]")).isNull();
        assertThat(XpathCheck.syntaxError(f, "concat('it',\\\"'\\\",'s')")).isNull();
        assertThat(XpathCheck.syntaxError(f, "//a[text()=\\\"x\\\"")).isNotNull();
    }

    @Test
    void compileErrorsMapToTheNearestPrecedingSelMarker() {
        List<String> java = Arrays.asList(
                "class X {",                                                              // 1
                "/*GATF_OP_LINE@/p/tests/a.sel:1*/__set__cln__(\"x\");",                   // 2
                "int a = 1;",                                                             // 3
                "/*GATF_LINE@/p/tests/a.sel:4*/__set__cln__(\"x\");String s = 1;",         // 4
                "/*GATF_ST_LINE@/p/tests/lib/b.sel:9*/__x();/*GATF_ST_LINE@/p/tests/lib/b.sel:10*/y();", // 5
                "/*GATF_ST_LINE_END@/p/tests/lib/b.sel*/return null;",                     // 6
                "/*GATF_LINE@C:\\\\p\\\\tests\\\\w.sel:3*/z();");                          // 7 (esc() doubles backslashes)
        assertThat(SelCompileErrors.selLineFor(java, 1)).isNull();
        assertThat(SelCompileErrors.selLineFor(java, 3)).containsExactly("/p/tests/a.sel", 1);
        assertThat(SelCompileErrors.selLineFor(java, 4)).containsExactly("/p/tests/a.sel", 4);
        assertThat(SelCompileErrors.selLineFor(java, 5)).containsExactly("/p/tests/lib/b.sel", 10);
        assertThat(SelCompileErrors.selLineFor(java, 6)).containsExactly("/p/tests/lib/b.sel", 10);   // END marker has no line
        assertThat(SelCompileErrors.selLineFor(java, 7)).containsExactly("C:\\p\\tests\\w.sel", 3);
        assertThat(SelCompileErrors.selLineFor(java, 99)).containsExactly("C:\\p\\tests\\w.sel", 3);
        assertThat(SelCompileErrors.selLineFor(java, 0)).isNull();
        assertThat(SelCompileErrors.message(" incompatible types ", 4)).isEqualTo("Java compile error: incompatible types (generated line 4)");
    }
}
