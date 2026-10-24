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

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.gatf.executor.core.AcceptanceTestContext;
import com.gatf.executor.core.GatfExecutorConfig;

/** Δ163o: parser fixes from Sumeet's 5.0.0 list (#6 #readfile, #7 var + subtest) and the sweep (hoverclick autoWait, negated states). */
class Delta163oParseTest {

    @TempDir
    Path tmp;

    private Command read(String sel) throws Exception {
        Files.createDirectories(tmp.resolve("tests"));
        Files.copy(Paths.get("src/test/resources/codegen-corpus/gatf-config.xml"), tmp.resolve("gatf-config.xml"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Files.write(tmp.resolve("tests/t.sel"), sel.getBytes(StandardCharsets.UTF_8));
        GatfExecutorConfig config = Command.getConfig(tmp.resolve("gatf-config.xml").toString(), tmp.toString());
        config.setTestCasesBasePath(tmp.toString());
        config.setOutFilesBasePath(tmp.toString());
        AcceptanceTestContext ctx = new AcceptanceTestContext();
        ctx.setGatfExecutorConfig(config);
        ctx.validateAndInit(true, false);
        ctx.getWorkflowContextHandler().initializeSuiteContext(1);
        File f = ctx.getResourceFile("t.sel");
        return Command.read(f, new ArrayList<String>(), ctx);
    }

    @Test
    void aSubtestAfterAVarLineIsNotADuplicate() throws Exception {
        // #7: REFERENCE's Essentials (a var line, then a subtest) failed with "Duplicate subtest defined": the parser peeks
        // at the line after a var, rewinds, and parsed (registered) the subtest twice
        Command root = read("open chrome\nvar who \"alice\"   // a comment\nsubtest \"login\"   // reusable block\n{\n    ?? css@\"body\"\n}\n@call \"login\"\n");
        assertThat(Command.subtestMethodNames(root)).hasSize(1);
        assertThat(root.javacode()).contains("set__subtestname__(\"login\"");
        // a func after a plugin-style statement, and a call after a var, behave the same
        root = read("open chrome\nvar a \"1\"\nfunc \"f\"\n{\n    ?? css@\"body\"\n}\nvar b \"2\"\n@call \"f\"\n");
        assertThat(root.javacode()).contains("\"__st__");
    }

    @Test
    void hashReadfileIsTheDocumentedFormOfReadfile() throws Exception {
        // #6: `#readfile` (REFERENCE.md) was "No func/subtest with that name found"; only `readfile` parsed
        Command a = read("open chrome\n#readfile \"/tmp/x.txt\"\n{\n    exec @print(@line)\n}\n");
        assertThat(a.javacode()).contains("/tmp/x.txt");
    }

    @Test
    void hoverclickAwaitsTheClickTargetAfterTheHover() {
        Command.CommandState st = new Command.CommandState();
        st.autoWait = true;
        String js = Command.parse(new Object[] { "hoverclick id@menu id@item", 1, new File("t.sel"), "", st, "t.sel" }, st, null).javacode();
        int hover = js.indexOf("\"hover\")"), moved = js.indexOf(".perform();"), click = js.indexOf("___ce___.get(0), \"click\")");
        assertThat(hover).isPositive();
        assertThat(click).isGreaterThan(moved).isGreaterThan(hover);   // hover, then wait for the target, then click
        // verifier round: the click target is FOUND after the hover too (a hover-revealed item does not exist before it)
        assertThat(js.indexOf("item")).isGreaterThan(moved);
        st.autoWait = false;
        js = Command.parse(new Object[] { "hoverclick id@menu id@item", 1, new File("t.sel"), "", st, "t.sel" }, st, null).javacode();
        assertThat(js).doesNotContain("awaitActionable");   // unchanged without autoWait
    }

    @Test
    void stateSubselectorsTakeFalse() {
        assertThat(SeleniumTest.stateFalse("false")).isTrue();
        assertThat(SeleniumTest.stateFalse(" FALSE ")).isTrue();
        assertThat(SeleniumTest.stateFalse(null)).isFalse();
        assertThat(SeleniumTest.stateFalse("true")).isFalse();
        assertThat(SeleniumTest.stateFalse("")).isFalse();
    }

    @Test
    void chromeBinaryOverrideEnvFirst() {
        assertThat(SeleniumTest.chromeBinaryOverride("/opt/c", "/x")).isEqualTo("/opt/c");
        assertThat(SeleniumTest.chromeBinaryOverride("  ", " /x ")).isEqualTo("/x");
        assertThat(SeleniumTest.chromeBinaryOverride(null, null)).isNull();
    }

    @Test
    void parseErrorTextNamesEveryErrorAndItsCause() {
        // verifier round: -validate-sel printed "Validation failed: " (the multi-error wrapper's message is "")
        Object[] d = { "?? xpath@\"//div[\"", 3, null, null, null, "t.sel" };
        Command.GatfSelCodeParseError one = new Command.GatfSelCodeParseError("Error at line 3 in file t.sel", d,
                new RuntimeException("Invalid xpath expression //div["));
        Command.GatfSelCodeParseError two = new Command.GatfSelCodeParseError("Duplicate subtest defined login", d);
        String txt = Command.parseErrorText(new Command.GatfSelCodeParseError(java.util.Arrays.asList(one, two)));
        assertThat(txt).isEqualTo("Error at line 3 in file t.sel: Invalid xpath expression //div[\nDuplicate subtest defined login");
        assertThat(Command.parseErrorText(two)).isEqualTo("Duplicate subtest defined login");
        assertThat(Command.parseErrorText(new Command.GatfSelCodeParseError(java.util.Collections.emptyList()))).isEqualTo("parse error");
    }

    @Test
    void theChromeBinaryOverrideLandsInTheOptions() {
        // #3: a plain `open chrome` ignored GATF_CHROME_BINARY; the override now sets the ChromeOptions binary
        org.openqa.selenium.chrome.ChromeOptions o = new org.openqa.selenium.chrome.ChromeOptions();
        SeleniumTest.applyChromeBinary(o, SeleniumTest.chromeBinaryOverride("/opt/custom/chrome", "/ignored"));
        assertThat(o.asMap().toString()).contains("binary=/opt/custom/chrome");
        org.openqa.selenium.chrome.ChromeOptions none = new org.openqa.selenium.chrome.ChromeOptions();
        SeleniumTest.applyChromeBinary(none, SeleniumTest.chromeBinaryOverride(null, null));
        assertThat(none.asMap().toString()).doesNotContain("binary=");
    }
}

