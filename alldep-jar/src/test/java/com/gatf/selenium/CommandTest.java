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
    void commentTextCannotSmuggleUnicodeEscapesIntoTheGeneratedJava() {
        // Δ163a: javac decodes backslash-u escapes before comments, so an escaped line feed in a comment (a crawled page
        // title, an imported line) would end the Java comment and compile what follows; the backslashes are doubled
        String bs = "\\";
        Command.CommandState st = new Command.CommandState();
        Command line = parseLine("// Shop " + bs + "u000a static { pwned(); }", st);
        assertThat(line).isInstanceOf(Command.CommentCommand.class);
        assertThat(line.javacode()).contains(bs + bs + "u000a").doesNotContain(" " + bs + "u000a");
        Command.MultiCommentCommand block = (Command.MultiCommentCommand) parseLine("/*", st);
        block.b.append(bs + "u002a/ static { pwned(); }\n");
        assertThat(block.javacode()).contains(bs + bs + "u002a/").doesNotContain("\n" + bs + "u002a");
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

    @Test
    void forcedDoubleClickDoubleClicks() {
        // Δ145c: under global timeouts (the default) `doubleclick` / `dblclick` became a wait with action null (no double-click)
        Command.CommandState st = new Command.CommandState();
        for (String s : new String[] { "doubleclick id@x", "dblclick id@x", "?? id@x doubleclick", "??5 id@x dblclick" }) {
            WaitTillElementVisibleOrInvisibleCommand w = (WaitTillElementVisibleOrInvisibleCommand) parseLine(s, st);
            assertThat(w.children).as(s).hasSize(1);
            assertThat(w.children.get(0)).as(s).isInstanceOf(Command.DoubleClickCommand.class);
            assertThat(w.javacode()).as(s).contains("new String[]{}, \"doubleclick\", null, null, \"Element not found by selector id@'x' ");
        }
    }

    @Test
    void hoverclickIsNotForceWrappedAndClicksTheSecondElement() {
        // Δ145c: a forced `hoverclick A B` produced only "wait for A"; HoverAndClickCommand waits for both itself
        Command.CommandState st = new Command.CommandState();
        Command c = parseLine("hoverclick id@a id@b", st);
        assertThat(c).isInstanceOf(Command.HoverAndClickCommand.class);
        String code = c.javacode();
        assertThat(code).doesNotContain("null[0]").doesNotContain("(List<WebElement>)null");
        int a = code.indexOf("evaluate(\"a\")"), keep = code.indexOf("List<WebElement> ___w___"), b = code.indexOf("evaluate(\"b\")");
        assertThat(a).isGreaterThanOrEqualTo(0);
        assertThat(keep).isGreaterThan(a);   // the hover match is kept before the second find overwrites ___ce___
        assertThat(b).isGreaterThan(keep);
        assertThat(code).containsPattern("___w___\\d+\\.moveToElement\\(___w___\\d+\\.get\\(0\\)\\)\\.click\\(___ce___\\.get\\(0\\)\\)\\.perform\\(\\);");
    }

    @Test
    void hoverclickAfterAWaitIsAParseError() {
        // Δ145c: `?? <loc> hoverclick` built no action at all (a silent no-op)
        Command.CommandState st = new Command.CommandState();
        for (String s : new String[] { "?? id@a hoverclick", "??10 id@a hoverclick id@b", "??- id@a hoverclick" }) {
            assertThatThrownBy(() -> parseLine(s, st)).as(s).isInstanceOf(Command.GatfSelCodeParseError.class)
                    .satisfies(e -> assertThat(e.getCause()).hasMessageContaining("hoverclick cannot follow a ?? wait"));
        }
    }

    @Test
    void frameAndTabKeepTheGranularityWhereTheyAreWritten() {
        // Δ145c (finishes Δ156b): a later `timeout` line changed the retry sleep of every frame/tab block written before it
        Command.CommandState st = new Command.CommandState();
        Command.FrameCommand f = (Command.FrameCommand) parseLine("frame \"f\"", st);
        f.children.add(parseLine("click id@x", st));
        TabCommand t = (TabCommand) parseLine("tab 1", st);
        TabCommand tn = (TabCommand) parseLine("tab \"named\"", st);
        parseLine("timeout 30 1 250", st);
        assertThat(st.timeoutSleepGranularity).isEqualTo(250L);
        assertThat(f.javacode()).contains("sleep(1000);").doesNotContain("sleep(250);");
        assertThat(t.javacode()).contains("sleep(1000);").doesNotContain("sleep(250);");
        assertThat(tn.javacode()).contains("sleep(1000);").doesNotContain("sleep(250);");
        assertThat(((Command.FrameCommand) parseLine("frame \"g\"", st)).sleepGranularity).isEqualTo(250L);
    }

    @Test
    void ifAndElseIfRoundTripKeepTheNegation() {
        // Δ145c: toCmd() wrote "?" / ":?" for ?! / :?! / ifnot / else ifnot
        Command.CommandState st = new Command.CommandState();
        assertThat(parseLine("?! id@x", st).toCmd()).startsWith("?!");
        assertThat(parseLine("ifnot id@x", st).toCmd()).startsWith("?!");
        assertThat(parseLine("? id@x", st).toCmd()).startsWith("?").doesNotStartWith("?!");
        assertThat(parseLine(":?! id@x", st).toCmd()).startsWith(":?!");
        assertThat(parseLine("else ifnot id@x", st).toCmd()).startsWith(":?!");
        assertThat(parseLine(":? id@x", st).toCmd()).startsWith(":?").doesNotStartWith(":?!");
    }

    /** One FindCommand as Command.parse builds it (the text is masked by sanitize() first). */
    private static FindCommand find(String text, Command.CommandState st) {
        return new FindCommand(st.sanitize(text), new Object[]{text, 1, new java.io.File("t.sel"), "", st, ""}, st);
    }

    @Test
    void findKeepsTheTimeoutCountsWhereItIsWritten() {
        // Δ145d: javacodeonly / javacodeifonly / the transient provider read state.timeoutNum / ifTimeoutNum at codegen,
        // so a later `timeout` line changed the retry count of every find written before it
        Command.CommandState st = new Command.CommandState();
        FindCommand f = find("id@x", st);
        assertThat(f.javacodeonly()).contains("___ce___, 60, \"\"");
        parseLine("timeout 30 5", st);
        assertThat(f.javacodeonly()).contains("___ce___, 60, \"\"").doesNotContain("___ce___, 30, \"\"");
        assertThat(f.javacodeifonly(new java.util.ArrayList<Command>(), "__v")).contains("___ce___, 1, \"\"").doesNotContain("___ce___, 5, \"\"");
        assertThat(f.javacodetrprovonly("__p")).contains("___ce___, 60, \"\"");
        FindCommand g = find("id@y", st);   // written after the timeout line: the new counts
        assertThat(g.javacodeonly()).contains("___ce___, 30, \"\"");
        assertThat(g.javacodeifonly(new java.util.ArrayList<Command>(), "__v")).contains("___ce___, 5, \"\"");
        assertThat(g.javacodetrprovonly("__p")).contains("___ce___, 30, \"\"");
    }

    @Test
    void frameTabAndScreenshotMasksKeepTheTimeoutCountsWhereTheyAreWritten() {
        // Δ145d: the retry count of frame / tab blocks and the if-timeout of screenshot-compare masks, as the find above
        Command.CommandState st = new Command.CommandState();
        Command.FrameCommand f = (Command.FrameCommand) parseLine("frame \"f\"", st);
        f.children.add(parseLine("click id@x", st));
        TabCommand t = (TabCommand) parseLine("tab 1", st);
        TabCommand tn = (TabCommand) parseLine("tab \"named\"", st);
        tn.children.add(parseLine("click id@x", st));
        Command sc = parseLine("screenshot compare \"home\" mask id@m", st);
        parseLine("timeout 30 5", st);
        assertThat(st.timeoutNum).isEqualTo(30);
        for (Command c : new Command[] { f, t, tn }) {
            assertThat(c.javacode()).contains("++==60)throw").doesNotContain("++==30)throw");
        }
        assertThat(sc.javacode()).contains("___ce___, 1, \"\"").doesNotContain("___ce___, 5, \"\"");
        assertThat(parseLine("frame \"g\"", st).javacode()).isEqualTo("switchToFrame(___ocw___, \"g\");\n");
        Command.FrameCommand g = (Command.FrameCommand) parseLine("frame \"g\"", st);
        g.children.add(parseLine("click id@x", st));
        assertThat(g.javacode()).contains("++==30)throw");
    }

    @Test
    void findToCmdRoundTrips() {
        // Δ145d: toCmd() wrote `@'value` with no closing quote (and Java-escaped the value), so the text did not parse back
        Command.CommandState st = new Command.CommandState();
        String[] lines = { "id@x", "css@'div > a.b'", "xpath@\"//a[@title='x y']\"", "xpath@'//a[@x=\"1\"]'", "id@x title",
                "role@button[name=Save]", "text@'=Order placed'", "relative tag@div above id@x", "title",
                "eval 1 == 1", "browser-scope chrome", "session-scope 's 1'", "label@'C:\\dir\\x'" };
        for (String line : lines) {
            FindCommand a = find(line, st);
            String text = a.toCmd();
            assertThat(text).as(line).startsWith(" ");
            FindCommand b = find(text.trim(), st);
            assertThat(b.by).as(text).isEqualTo(a.by);
            assertThat(b.classifier).as(text).isEqualTo(a.classifier);
            assertThat(b.subselector).as(text).isEqualTo(a.subselector);
            assertThat(b.relative).as(text).isEqualTo(a.relative);
            assertThat(b.by1).as(text).isEqualTo(a.by1);
            assertThat(b.classifier1).as(text).isEqualTo(a.classifier1);
            assertThat(st.unsanitize(String.valueOf(b.eval))).as(text).isEqualTo(st.unsanitize(String.valueOf(a.eval)));
            assertThat(st.unsanitize(b.browserScope)).as(text).isEqualTo(st.unsanitize(a.browserScope));
            assertThat(st.unsanitize(b.sessionScope)).as(text).isEqualTo(st.unsanitize(a.sessionScope));
        }
        assertThat(find("css@'div > a'", st).toCmd()).isEqualTo(" css@'div > a'");
        assertThat(find("xpath@\"//a[@t='q']\"", st).toCmd()).isEqualTo(" xpath@\"//a[@t='q']\"");
        // and through a statement that embeds it
        Command c = parseLine("hover css@'div > a'", st);
        assertThat(c.toCmd()).endsWith("css@'div > a'");
    }

    /** Δ145d: the `open <key>` driver block, compiled inside a SeleniumTest subclass against the real Selenium API. */
    private static String driverBlock(String key) {
        SeleniumDriverConfig cfg = new SeleniumDriverConfig();
        cfg.setName(key);
        cfg.setCapabilities(new java.util.LinkedHashMap<String, String>());
        StringBuilder b = new StringBuilder();
        DriverInitCodegen.append(b, cfg, "http://127.0.0.1:4723/wd/hub", new Object[]{"open " + key, 1, new java.io.File("t.sel"), "", null, ""});
        return b.toString();
    }

    private static java.util.List<String> compileDriverBlock(String key, String code) throws Exception {
        String cls = "Gen145d" + key.replaceAll("[^A-Za-z]", "");
        String src = "package com.gatf.selenium;\nimport java.util.Map;\nimport org.openqa.selenium.WebDriver;\n"
                + "import org.openqa.selenium.remote.CapabilityType;\nimport org.openqa.selenium.logging.LoggingPreferences;\n"
                + "abstract class " + cls + " extends SeleniumTest {\n" + cls + "() { super(null, null, 0); }\n"
                + "void init(LoggingPreferences ___lp___) throws Exception {\n" + code + "}\n}\n";
        javax.tools.JavaCompiler jc = javax.tools.ToolProvider.getSystemJavaCompiler();
        java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("gen145d");
        java.nio.file.Path f = dir.resolve(cls + ".java");
        java.nio.file.Files.write(f, src.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        javax.tools.DiagnosticCollector<javax.tools.JavaFileObject> dc = new javax.tools.DiagnosticCollector<>();
        try (javax.tools.StandardJavaFileManager fm = jc.getStandardFileManager(dc, null, null)) {
            jc.getTask(null, fm, dc, java.util.Arrays.asList("-proc:none", "-d", dir.toString(), "-cp", testClasspath()),
                    null, fm.getJavaFileObjects(f.toFile())).call();
        }
        java.util.List<String> errors = new java.util.ArrayList<>();
        for (javax.tools.Diagnostic<?> d : dc.getDiagnostics()) {
            if (d.getKind() == javax.tools.Diagnostic.Kind.ERROR) errors.add(d.getLineNumber() + ": " + d.getMessage(null));
        }
        return errors;
    }

    /** The test classpath (surefire's own property first; its manifest-only booter jar expanded, as ExportSeleniumCompileTest). */
    private static String testClasspath() throws Exception {
        String cp = System.getProperty("surefire.test.class.path");
        if (cp == null || cp.trim().isEmpty()) cp = System.getProperty("java.class.path");
        java.util.Set<String> out = new java.util.LinkedHashSet<String>();
        for (String e : cp.split(java.io.File.pathSeparator)) {
            if (e.isEmpty()) continue;
            out.add(e);
            java.io.File f = new java.io.File(e);
            if (!f.isFile() || !e.endsWith(".jar")) continue;
            try (java.util.jar.JarFile jar = new java.util.jar.JarFile(f)) {
                java.util.jar.Manifest m = jar.getManifest();
                String more = m == null ? null : m.getMainAttributes().getValue("Class-Path");
                if (more == null) continue;
                for (String u : more.trim().split("\\s+")) {
                    if (u.isEmpty()) continue;
                    java.net.URI uri = f.getParentFile().toURI().resolve(u);
                    out.add("file".equals(uri.getScheme()) ? new java.io.File(uri).getPath() : u);
                }
            }
        }
        return String.join(java.io.File.pathSeparator, out);
    }

    @Test
    void openEdgeAndEdgeDkrGenerateCompilingCode() throws Exception {
        // Δ145d: plain edge emitted addWdm("edge") (no such overload) and declared __bkey__ twice; edge-dkr had no branch
        String edge = driverBlock("edge"), dkr = driverBlock("edge-dkr");
        for (String code : new String[] { edge, dkr }) {
            assertThat(code.split("String __bkey__ =", -1)).hasSize(2);
            assertThat(code).contains("__workerDriver__(__bkey__)").contains("__workerAdopt__(__bkey__, ");   // Δ154 pool hooks kept
        }
        assertThat(edge).contains("addWdm(\"edge\", ___dc___)").contains("new org.openqa.selenium.edge.EdgeDriver(___dc___)");
        assertThat(dkr).contains("addWdm(\"edge-dkr\", ___dc___)").contains("getDockerDriver(__bkey__)").doesNotContain("new org.openqa.selenium.edge.EdgeDriver");
        assertThat(compileDriverBlock("edge", edge)).isEmpty();
        assertThat(compileDriverBlock("edge-dkr", dkr)).isEmpty();
        assertThat(compileDriverBlock("chrome", driverBlock("chrome"))).isEmpty();   // the harness itself compiles a known-good block
    }

    @Test
    void openSafariGoesThroughGetDockerDriverWithoutInsecureCerts() throws Exception {
        // Δ145d (from Δ159): safaridriver refuses acceptInsecureCerts=true; a direct SafariDriver skipped the Grid and the
        // tracing webSocketUrl that BrowserDrivers.create adds
        String safari = driverBlock("safari");
        assertThat(safari).doesNotContain("ACCEPT_INSECURE_CERTS").doesNotContain("new org.openqa.selenium.safari.SafariDriver")
                .contains("addWdm(\"safari\", ___dc___)").contains("getDockerDriver(__bkey__)").contains("__workerDriver__(__bkey__)");
        assertThat(compileDriverBlock("safari", safari)).isEmpty();
        assertThat(driverBlock("chrome")).contains("ACCEPT_INSECURE_CERTS");   // others unchanged
    }

    @Test
    void aRequestedGridRoutesPlainKeysThroughGetDockerDriver() throws Exception {
        // Δ163g: docs/browser-modes.md Mode C: with SELENIUM_REMOTE_URL (or -Dgatf.selenium.remoteUrl / GATF_WITH_GRID) set,
        // plain `open chrome` must reach the Grid; it used to build a local ChromeDriver regardless (CI e2e debugger failure)
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getenv("SELENIUM_REMOTE_URL") == null && System.getenv("GATF_WITH_GRID") == null,
                "a Grid is set in this environment");
        assertThat(driverBlock("chrome")).contains("new org.openqa.selenium.chrome.ChromeDriver(___dc___)");   // nothing set: unchanged
        for (String prop : new String[] { "gatf.selenium.remoteUrl", "gatf.withGrid" }) {
            System.setProperty(prop, prop.endsWith("Url") ? "http://localhost:4444" : "chrome");
            try {
                for (String key : new String[] { "chrome", "firefox", "edge", "ie" }) {
                    String code = driverBlock(key);
                    assertThat(code).as(prop + " " + key).contains("getDockerDriver(__bkey__)").doesNotContain("Driver(___dc___)))");
                }
                assertThat(compileDriverBlock("chrome", driverBlock("chrome"))).isEmpty();
            } finally {
                System.clearProperty(prop);
            }
        }
    }
}
