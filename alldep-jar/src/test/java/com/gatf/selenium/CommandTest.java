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

import org.junit.jupiter.api.Test;

/** T1 backend-unit: the Phase-7 trailing end-of-line // handling in the transpiler. The full
 *  seleasy->Java codegen path is exercised by the API/e2e layers, not by blind golden strings. */
class CommandTest {

    @Test
    void stripsWhitespacePrecededTrailingComment() {
        assertThat(Command.stripTrailingLineComment("config app.properties //note"))
                .isEqualTo("config app.properties");
        assertThat(Command.stripTrailingLineComment("x // y")).isEqualTo("x");
    }

    @Test
    void preservesUrlSchemeAndUnspacedSlashes() {
        assertThat(Command.stripTrailingLineComment("goto https://example.com"))
                .isEqualTo("goto https://example.com");   // '//' preceded by ':' is not a comment
        assertThat(Command.stripTrailingLineComment("a//b")).isEqualTo("a//b");
    }

    @Test
    void leavesOrdinaryAndTooShortLinesUntouched() {
        assertThat(Command.stripTrailingLineComment("open chrome")).isEqualTo("open chrome");
        assertThat(Command.stripTrailingLineComment("ab")).isEqualTo("ab");
        assertThat(Command.stripTrailingLineComment(null)).isNull();
    }

    @Test
    void autoWaitOptOutForms() {
        assertThat(Command.stripNoWait("click! css@#fast-btn")).isEqualTo("click css@#fast-btn");
        assertThat(Command.stripNoWait("clickfo! id@x")).isEqualTo("clickfo id@x");
        assertThat(Command.stripNoWait("type! 'abc' id@x")).isEqualTo("type 'abc' id@x");
        assertThat(Command.stripNoWait("hoverclick! id@a id@b")).isEqualTo("hoverclick id@a id@b");
        assertThat(Command.stripNoWait("dblclick!")).isEqualTo("dblclick");
        assertThat(Command.stripNoWait("nowait select text@'x' id@s")).isEqualTo("select text@'x' id@s");
        assertThat(Command.stripNoWait("NoWait click id@x")).isEqualTo("click id@x");
        assertThat(Command.stripNoWait("nowait ??10 id@x click")).isEqualTo("??10 id@x click");
    }

    @Test
    void autoWaitOptOutLeavesOtherStatementsAlone() {
        assertThat(Command.stripNoWait("click id@x")).isNull();
        assertThat(Command.stripNoWait("clickety! id@x")).isNull();
        assertThat(Command.stripNoWait("nowait goto https://x")).isNull();
        assertThat(Command.stripNoWait("nowaiting click id@x")).isNull();
        assertThat(Command.stripNoWait("??10 id@x click!")).isNull();
        assertThat(Command.stripNoWait("var click! 1")).isNull();
    }

    /** Raw (global timeouts off) codegen of one action statement, for a project with autoWait on or off. */
    private static String rawActionCode(String cmd, boolean autoWait) {
        Command.CommandState st = new Command.CommandState();
        st.autoWait = autoWait;
        Object[] details = new Object[]{cmd, 1, new java.io.File("t.sel"), "", st, ""};
        return Command.handleActions(cmd, null, details, st).javacode();
    }

    @Test
    void autoWaitProbesInRawActionsCodegen() {
        assertThat(rawActionCode("actions draganddrop id@a id@b", true)).contains("\"drag\");");
        assertThat(rawActionCode("actions draganddrop id@a id@b", false)).doesNotContain("awaitActionable");
        assertThat(rawActionCode("actions moveto id@a clickandhold id@b release", true))
                .contains("awaitActionable(___cw___, ___ce___.get(0), \"hover\");");
        assertThat(rawActionCode("actions moveto id@a click", false)).doesNotContain("awaitActionable");
        assertThat(rawActionCode("hover id@a", true)).contains("\"hover\");");
        assertThat(rawActionCode("clear id@a", true)).contains("\"clear\");");
        assertThat(rawActionCode("submit id@a", true)).contains("\"submit\");");
        assertThat(rawActionCode("dblclick id@a", true)).contains("\"dblclick\");");
        assertThat(rawActionCode("select text@'x' id@s", true)).contains("\"select\");");
        assertThat(rawActionCode("hover id@a", false)).doesNotContain("awaitActionable");
        assertThat(rawActionCode("click id@a", false)).contains("clickAction(___ocw___, ___ce___, null, \"id@a\");");
    }

    @Test
    void nowaitActionsOptsOutTheDragProbe() {
        assertThat(Command.stripNoWait("nowait actions draganddrop id@a id@b")).isEqualTo("actions draganddrop id@a id@b");
        assertThat(Command.stripNoWait("NOWAIT actions moveto id@a click")).isEqualTo("actions moveto id@a click");
        assertThat(Command.stripNoWait("actions! draganddrop id@a id@b")).isNull();
        assertThat(Command.stripNoWait("nowait actionsx id@a")).isNull();
    }

    @Test
    void softAssertionPrefix() {
        java.util.regex.Matcher m = Command.WAIT_SOFT.matcher("??~10 id@x text ='a'");
        assertThat(m.find()).isTrue();
        assertThat(m.group(1)).isEqualTo("10");
        m = Command.WAIT_SOFT.matcher("??~ css@h1");
        assertThat(m.find()).isTrue();
        assertThat(m.group(1)).isEmpty();
        assertThat(Command.WAIT_SOFT.matcher("?? css@h1").find()).isFalse();
        assertThat(Command.stripNoWait("nowait ??~ id@x text ='a'")).isEqualTo("??~ id@x text ='a'");
    }

    @Test
    void hookBlocksAndSubtestMarkers() {
        // M6: whole-line hook keywords in any case; only/skip/fixme prefix a subtest definition
        assertThat(Command.HOOK_BLOCK.matcher("beforeEach").matches()).isTrue();
        assertThat(Command.HOOK_BLOCK.matcher("AFTERALL").matches()).isTrue();
        assertThat(Command.HOOK_BLOCK.matcher("beforeEach x").matches()).isFalse();
        assertThat(Command.hookName("aftereach")).isEqualTo("afterEach");
        assertThat(Command.hookName("before")).isNull();
        java.util.regex.Matcher m = Command.SUBTEST_MARKER.matcher("fixme subtest \"checkout\"");
        assertThat(m.find()).isTrue();
        assertThat(m.group(1)).isEqualTo("fixme");
        assertThat(Command.SUBTEST_MARKER.matcher("skip func \"x\"").find()).isFalse();
        assertThat(Command.SUBTEST_MARKER.matcher("subtest \"x\"").find()).isFalse();
    }

    private static FindCommand find(String expr) {
        return new FindCommand(expr, new Object[] { expr, 1, new java.io.File("t.sel"), "", null, "" }, new Command.CommandState());
    }

    private static String findError(String expr) {
        try {
            find(expr);
            return null;
        } catch (RuntimeException e) {
            return e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
        }
    }

    @Test
    void semanticAnchorInRelativeFind() {
        // M3: the anchor (last) may be semantic, validated like a plain find; the target stays tag-only
        FindCommand f = find("relative tag@input rightof label@Email");
        assertThat(f.by()).isEqualTo("label");
        assertThat(f.classifier()).isEqualTo("Email");
        assertThat(f.isSemanticFind()).isTrue();
        assertThat(find("relative tag@a below role@heading[name=Orders]").isSemanticFind()).isTrue();
        assertThat(find("relative tag@a below text@=Orders").isSemanticFind()).isTrue();
        assertThat(find("relative tag@a below id@x").isSemanticFind()).isFalse();
        assertThat(findError("relative tag@input rightof label@~")).contains("empty value");
        assertThat(findError("relative label@x rightof tag@input")).contains("Invalid relative selector");
    }

    @Test
    void semanticLocatorsValidatedAtTranspile() {
        assertThat(findError("placeholder@/(/")).contains("Invalid regex /(/");
        assertThat(findError("text@/a/ii")).contains("duplicate flag");
        assertThat(findError("label@/^(Save$/")).contains("Invalid regex");     // a '$' anchor is not a variable
        assertThat(findError("label@/^Save$/")).isNull();
        assertThat(findError("label@${lbl}")).isNull();                          // runtime variables are checked at run time
        assertThat(findError("label@/^(${x}$/")).isNull();
        assertThat(find("testid@=foo").isSemanticFind()).isTrue();
        assertThat(find("css@.x").isSemanticFind()).isFalse();
    }

    @Test
    void forbidOnlyFindsTheScriptsOwnOnlyMarker() {
        // E10: Command.read fails the transpile at this subtest when forbidOnly is on; an imported file's `only` does not count
        Command.CommandState st = new Command.CommandState();
        Object[] at = { "subtest \"search\"", 3, "f.sel", "", st, "f.sel" };
        new SubTestCommand("\"search\"", at, st, false);
        assertThat(st.firstOnly()).isNull();
        st.isImportedFile = true;
        new SubTestCommand("\"lib\"", at, st, false, null, "only");
        st.isImportedFile = false;
        assertThat(st.firstOnly()).isNull();
        SubTestCommand only = new SubTestCommand("\"cart\"", at, st, false, null, "only");
        assertThat(st.firstOnly()).isSameAs(only);
        assertThat(st.hasOnly()).isTrue();
        assertThat(Command.FORBID_ONLY).isEqualTo("'only' is not allowed (forbidOnly)");
    }

    /** One statement through Command.parse, sharing {@code st} across calls like Command.read does for a file. */
    private static Command parseLine(String line, Command.CommandState st) {
        return Command.parse(new Object[]{line, 1, new java.io.File("t.sel"), "", st, ""}, st, null);
    }

    @Test
    void waitDirectionCodegenIsPinned() {
        // Δ145b: ??+ / wait till visible wait until the element is NOT found (isVisible=true: handleWaitFuncWL returns
        // once the find comes back empty); ??- / ?? / wait for wait until it is found. REFERENCE.md documents this.
        Command.CommandState st = new Command.CommandState();
        for (String s : new String[] { "??+ id@x", "wait till visible id@x" }) {
            WaitTillElementVisibleOrInvisibleCommand w = (WaitTillElementVisibleOrInvisibleCommand) parseLine(s, st);
            assertThat(w.isVisible).as(s).isTrue();
            assertThat(w.javacode()).as(s).contains("handleWaitFuncWL(").contains(", true, \"t.sel\", 1 ,");
        }
        for (String s : new String[] { "??- id@x", "?? id@x", "wait for id@x" }) {
            WaitTillElementVisibleOrInvisibleCommand w = (WaitTillElementVisibleOrInvisibleCommand) parseLine(s, st);
            assertThat(w.isVisible).as(s).isFalse();
            assertThat(w.javacode()).as(s).contains(", false, \"t.sel\", 1 ,");
        }
    }

    @Test
    void commentEndOutsideABlockCommentIsOrdinaryText() {
        // Δ145b: an unquoted '*/' ended a "comment" on any line, so these statements generated nothing
        Command.CommandState st = new Command.CommandState();
        assertThat(parseLine("route **/api/users fulfill 200 users.json", st)).isInstanceOf(RouteCommand.class);
        assertThat(((RouteCommand) parseLine("unroute **/api/users", st)).statement).isEqualTo("unroute **/api/users");
        Command x = parseLine("click xpath@//div/*/a", st);   // global timeouts on (the default): a waited click
        assertThat(x).isInstanceOf(WaitTillElementVisibleOrInvisibleCommand.class);
        assertThat(((WaitTillElementVisibleOrInvisibleCommand) x).cond.by).isEqualTo("xpath");
        assertThat(((WaitTillElementVisibleOrInvisibleCommand) x).cond.classifier).contains("div/*/a");
        assertThat(st.commentStart).isFalse();
        assertThatThrownBy(() -> parseLine("*/", st)).isInstanceOf(Command.GatfSelCodeParseError.class)
                .satisfies(e -> assertThat(e.getCause()).hasMessageContaining("without an open '/*'"));
    }

    @Test
    void blockCommentsOpenAndCloseOnlyWhereTheyShould() {
        Command.CommandState st = new Command.CommandState();
        Command one = parseLine("/* one line */", st);
        assertThat(one).isInstanceOf(Command.CommentCommand.class);
        assertThat(one.javacode()).isEqualTo("// one line ");
        assertThat(st.commentStart).isFalse();
        assertThat(parseLine("/* a // b */", st)).isInstanceOf(Command.CommentCommand.class);   // the trailing-// strip skips '/*' lines
        assertThat(st.commentStart).isFalse();
        Command.MultiCommentCommand open = (Command.MultiCommentCommand) parseLine("/*", st);
        assertThat(st.commentStart).isTrue();
        assertThat(parseLine("click id@x", st)).isInstanceOf(Command.ValueCommand.class);
        assertThat(parseLine("\"a quoted */ does not close it\"", st)).isInstanceOf(Command.ValueCommand.class);
        assertThat(st.commentStart).isTrue();
        assertThat(parseLine("??+ id@x */", st)).isInstanceOf(Command.EndMultiCommentCommand.class);   // was a wait command
        assertThat(st.commentStart).isFalse();
        open.b.append("\"q */ q\"\n");
        assertThat(open.javacode()).isEqualTo("/*\n\"q * / q\"\n*/");   // a quoted */ cannot end the Java comment early
    }

    @Test
    void elseIfnotIsNegatedWithAnySpacing() {
        // Δ145b: was startsWith("else ifnot  ") (two spaces) + substring(8), so `else ifnot x` ran as `else if` on "ot x"
        Command.CommandState st = new Command.CommandState();
        for (String s : new String[] { "else ifnot id@x", "else ifnot   id@x", "else\tifnot\tid@x", "else  ifnot id@x" }) {
            Command.ElseIfCommand e = (Command.ElseIfCommand) parseLine(s, st);
            assertThat(e.negation).as(s).isTrue();
            assertThat(e.conds.get(0).by).as(s).isEqualTo("id");
            assertThat(e.conds.get(0).classifier).as(s).isEqualTo("x");
        }
        for (String s : new String[] { "else if id@x", "else  if id@x" }) {
            Command.ElseIfCommand e = (Command.ElseIfCommand) parseLine(s, st);
            assertThat(e.negation).as(s).isFalse();
            assertThat(e.conds.get(0).classifier).as(s).isEqualTo("x");
        }
        assertThat(((Command.ElseIfCommand) parseLine(":?! id@x", st)).negation).isTrue();
        assertThat(((IfCommand) parseLine("ifnot id@x", st)).negation).isTrue();
        assertThat(((IfCommand) parseLine("?! id@x", st)).negation).isTrue();
        assertThat(parseLine("else", st)).isInstanceOf(Command.ElseCommand.class);
    }
}
