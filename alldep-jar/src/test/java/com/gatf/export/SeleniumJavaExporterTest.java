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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.gatf.selenium.SemanticLocators;

/** Δ161 — the Selenium Java backend, statement by statement (goldens: {@link SeleniumJavaGoldenTest}). */
class SeleniumJavaExporterTest {

    static ExportResult export(String src) {
        return export(src, false);
    }

    static ExportResult export(String src, boolean strict) {
        return Exporters.export("selenium-java", "t", "open chrome\n" + src, new ExportOptions().strictLocators(strict));
    }

    /** The first generated statement after the open line. */
    static String line(String sel) {
        String c = export(sel + "\n").content;
        String marker = "before the test\n";
        String body = c.substring(c.indexOf(marker) + marker.length());
        return body.substring(0, body.indexOf('\n')).trim();
    }

    @Test
    void registeredWithAClassNamedFile() {
        assertTrue(Exporters.ids().contains("selenium-java"));
        assertEquals("LoginFlowTest.java", Exporters.get("selenium-java").fileName("login-flow"));
        ExportResult r = Exporters.export("selenium-java", "login-flow", "open chrome\n", null);
        assertEquals("LoginFlowTest.java", r.fileName);
        assertTrue(r.content.contains("\nclass LoginFlowTest {\n"));
        assertTrue(r.content.contains("@DisplayName(\"login-flow\")"));
    }

    @Test
    void locatorsUseSeleasysByStrategies() {
        assertEquals("actionable(by(\"id@a-b\", By.id(\"a-b\")), timeout, \"click\").click();", line("click id@a-b"));
        assertEquals("actionable(by(\"name@q\", By.name(\"q\")), timeout, \"click\").click();", line("click name@q"));
        assertEquals("actionable(by(\"class@btn\", By.className(\"btn\")), timeout, \"click\").click();", line("click class@btn"));
        assertEquals("actionable(by(\"tag@button\", By.tagName(\"button\")), timeout, \"click\").click();", line("click tag@button"));
        assertEquals("actionable(by(\"xpath@//a\", By.xpath(\"//a\")), timeout, \"click\").click();", line("click xpath@//a"));
        assertEquals("actionable(by(\"css@.x\", By.cssSelector(\".x\")), timeout, \"click\").click();", line("click css@.x"));
        assertEquals("actionable(by(\"linktext@Next\", By.linkText(\"Next\")), timeout, \"click\").click();", line("click linktext@Next"));
        assertEquals("actionable(by(\"partiallinktext@Ne\", By.partialLinkText(\"Ne\")), timeout, \"click\").click();", line("click partiallinktext@Ne"));
        assertEquals("actionable(by(\"text@Saved\", By.xpath(\"//*[contains(text(), 'Saved')]\")), timeout, \"click\").click();", line("click text@Saved"));
        assertEquals("actionable(active(), timeout, \"click\").click();", line("click active"));
    }

    @Test
    void semanticLocatorsRunSeleasysResolver() {
        assertEquals("actionable(semantic(\"role@button[name=Save]\", spec(\"kind\", \"role\", \"role\", \"button\", \"mode\", \"full\", \"value\", \"Save\")), timeout, \"click\").click();",
                line("click role@button[name=Save]"));
        assertEquals("find(semantic(\"role@heading[level=2]\", spec(\"kind\", \"role\", \"role\", \"heading\", \"level\", 2)), timeout);", line("?? role@heading[level=2]"));
        assertEquals("actionable(semantic(\"label@~mail\", spec(\"kind\", \"label\", \"mode\", \"contains\", \"value\", \"mail\")), timeout, \"click\").click();", line("click label@~mail"));
        assertEquals("actionable(semantic(\"text@/sav(e|ed)/i\", spec(\"kind\", \"text\", \"mode\", \"regex\", \"value\", \"sav(e|ed)\", \"flags\", \"i\")), timeout, \"click\").click();",
                line("click text@/sav(e|ed)/i"));
        String c = export("click testid@x\n").content;
        assertTrue(c.contains("private static final boolean STRICT_LOCATORS = false;"));
        assertTrue(c.contains("private static final String TEST_ID_ATTRIBUTE = \"data-testid\";"));
        // the resolver is embedded verbatim: concatenating the literals' values gives RESOLVER_JS back
        StringBuilder js = new StringBuilder();
        Matcher m = Pattern.compile("(?m)^            \\+ (\".*\");?$").matcher(c.substring(c.indexOf("SEMANTIC_JS = \"\"")));
        while (m.find() && js.length() < SemanticLocators.RESOLVER_JS.length()) js.append(TsLikeJava.decode(m.group(1)));
        assertEquals(SemanticLocators.RESOLVER_JS, js.toString());
        assertTrue(export("click testid@x\n", true).content.contains("STRICT_LOCATORS = true;"));
        assertFalse(export("click id@x\n").content.contains("SEMANTIC_JS"), "the resolver only when a semantic locator is used");
    }

    @Test
    void secretsAndConfigBecomeEnvironmentVariables() {
        ExportResult r = export("goto ${baseUrl}/x\ntype ${user.password} id@p\n");
        assertTrue(r.content.contains("driver.get(env(\"GATF_BASEURL\") + \"/x\");"));
        assertTrue(r.content.contains("type(actionable(by(\"id@p\", By.id(\"p\")), timeout, \"type\"), env(\"GATF_USER_PASSWORD\"), \"\");"));
        assertEquals("GATF_USER_PASSWORD", r.env.get("user.password"));
        assertTrue(r.report().contains("${user.password} → GATF_USER_PASSWORD"));
        assertEquals(1, r.content.split("System\\.getenv\\(name\\)", -1).length - 1, "System.getenv(name) only inside env()");
        assertFalse(r.content.contains("password\""), "no value, only the variable name");
    }

    @Test
    void environmentNameCollisionIsAnError() {
        ExportException e = assertThrows(ExportException.class, () -> export("goto ${base_url}\ngoto ${base.url}\n"));
        assertTrue(e.getMessage().contains("GATF_BASE_URL"), e.getMessage());
        export("goto ${baseUrl}\ngoto ${base_url}\n");
    }

    @Test
    void importsOnlySeleniumJunitAndTheJdk() {
        ExportResult r = export("goto ${a}\nvar x 'y'\nclick label@$v{x}\n??~ id@t text %ok\nscreenshot\nactions draganddrop id@a id@b\n");
        Matcher imports = Pattern.compile("(?m)^import (static )?([\\w.]+);$").matcher(r.content);
        int n = 0;
        while (imports.find()) {
            n++;
            String i = imports.group(2);
            assertTrue(i.startsWith("java.") || i.startsWith("org.openqa.selenium.") || i.startsWith("org.junit.jupiter.api."), imports.group());
        }
        assertTrue(n > 10);
        assertFalse(Pattern.compile("(?m)^(?!\\s*(//|\\*|/\\*)).*com\\.gatf").matcher(r.content).find(), "an exported test never depends on GATF (comments may name it)");
    }

    @Test
    void unsupportedIsCommentedAndListedNeverDropped() {
        ExportResult r = export("## class@row\n{\n    click id@a\n    exec @print(1)\n}\nclick id@ok\nroute **/x abort\n");
        assertTrue(r.content.contains("// TODO(gatf-export): ## class@row\n"));
        assertTrue(r.content.contains("// TODO(gatf-export): click id@a\n"));
        assertTrue(r.content.contains("// TODO(gatf-export): route **/x abort\n"));
        assertEquals(4, r.items(ExportResult.Level.UNSUPPORTED).size());
        assertEquals(Integer.valueOf(6), r.stats.get("statements"));   // open, ##, its two statements, click, route
        assertEquals(Integer.valueOf(2), r.stats.get("exported"));
        assertTrue(r.report().contains("NetworkInterceptor is CDP-only"));
    }

    @Test
    void reasonsNeverPointAtPlaywright() {
        ExportResult r = export("frame \"f\"\nalert 'x'\nnetapix x\na11y check\nexecjsfile a.js\nclock pause\nemulate locale de\nstate load a.json\n<<<\nx\n>>>\n");
        for (ExportResult.Item i : r.items) assertFalse(i.reason.contains("Playwright") || i.reason.contains("page."), i.toString());
        assertTrue(r.report().contains("driver.switchTo().frame"));
    }

    @Test
    void composedExpectedValuesAreParenthesised() {   // as PlaywrightExporter.member: v("n") + " x".startsWith(a) would concatenate a boolean
        String c = export("var n 'Banner'\n?? id@b text %'$v{n} shown later'\n?? id@b text '$v{n} x'%\n?? id@b text %$v{n} x%\n?? id@b text %'plain'\n?? id@b text !='$v{n}'\n").content;
        assertTrue(c.contains("a -> (v(\"n\") + \" shown later\").startsWith(a));"), c);
        assertTrue(c.contains("a -> (v(\"n\") + \" x\").endsWith(a));"), c);
        assertTrue(c.contains("a -> (v(\"n\") + \"x\").contains(a));"), c);
        assertTrue(c.contains("a -> \"plain\".startsWith(a));"), c);
        assertTrue(c.contains("a -> !v(\"n\").equals(a));"), c);
    }

    @Test
    void waitsAssertionsAndSoftOnes() {
        assertEquals("find(by(\"id@x\", By.id(\"x\")), Duration.ofSeconds(5));", line("??5 id@x"));
        assertEquals("gone(by(\"id@x\", By.id(\"x\")), timeout);", line("??+ id@x"));
        assertEquals("soft(() -> check(\"??~ id@x text ='a'\", timeout, () -> now(by(\"id@x\", By.id(\"x\"))).getText(), a -> \"a\".equals(a)));", line("??~ id@x text ='a'"));
        assertEquals("check(\"?? title ='T'\", timeout, () -> driver.getTitle(), a -> \"T\".equals(a));", line("?? title ='T'"));
        assertEquals("timeout = Duration.ofSeconds(30);", line("timeout 30"));
        String c = export("warn 'w'\n").content;
        assertTrue(c.contains("softFailures.add(\"w\");") && c.contains("if (!softFailures.isEmpty()) fail("));
    }

    @Test
    void actionsMirrorSeleasysRuntime() {
        assertEquals("type(actionable(by(\"id@q\", By.id(\"q\")), timeout, \"type\"), \"abc\", \"bl\");", line("typebl 'abc' id@q"));
        assertEquals("type(find(by(\"id@q\", By.id(\"q\")), timeout), \"abc\", \"\");", line("nowait type 'abc' id@q"));
        assertEquals("new Actions(driver).moveToElement(actionable(by(\"id@r\", By.id(\"r\")), timeout, \"dblclick\")).doubleClick().perform();", line("dblclick id@r"));
        assertEquals("drag(actionable(by(\"id@a\", By.id(\"a\")), timeout, \"drag\"), find(by(\"id@b\", By.id(\"b\")), timeout));", line("actions draganddrop id@a id@b"));
        assertEquals("new Actions(driver).dragAndDrop(actionable(by(\"id@a\", By.id(\"a\")), timeout, \"drag\"), find(by(\"id@b\", By.id(\"b\")), timeout)).perform();",
                line("actions draganddrop1 id@a id@b"));
        assertEquals("selectLast(new Select(actionable(by(\"id@s\", By.id(\"s\")), timeout, \"select\")));", line("select last id@s"));
        String bs = "\\";
        assertEquals("type(actionable(by(\"id@q\", By.id(\"q\")), timeout, \"type\"), Keys.chord(\"a" + bs + "ue007\"), \"\");", line("chord 'a" + bs + "uE007' id@q"));
        assertTrue(export("chord 'a" + bs + "u12' id@q\n").content.contains("// TODO(gatf-export): chord 'a" + bs + bs + "u12' id@q\n"));
        assertEquals("driver.manage().window().maximize();", line("maximize"));
        assertEquals("System.out.println(\"[gatf-export] pass: \" + \"ok\");", line("pass 'ok'"));
    }

    @Test
    void passRecordsAndContinues() {
        // Δ145d: seleasy's `pass` records the pass and CONTINUES; it exported as `if (true) return;`, skipping the rest
        String c = export("goto x\npass 'half way'\nclick id@after\npass\n").content;
        assertFalse(c.contains("return; // pass"), c);
        assertTrue(c.contains("System.out.println(\"[gatf-export] pass: \" + \"half way\");"), c);
        assertTrue(c.contains("System.out.println(\"[gatf-export] pass: \" + \"pass\");"), c);
        assertTrue(c.indexOf("id@after") > c.indexOf("half way"), c);
    }

    @Test
    void subtestsHooksAndArguments() {
        String c = export("beforeEach\n{\ngoto x\n}\nfunc \"f\" (who)\n{\ntype $v{who} id@e\n}\nsubtest \"s\"\n{\n@call \"f\" (who:Ada)\n? id@a\n{\nclick id@b\n}\n:\n{\nfail 'no'\n}\n}\nskip subtest \"later\"\n{\nclick id@l\n}\n@call \"S\"\n@call \"later\"\n").content;
        assertTrue(c.contains("private void fn_f(Map<String, String> args) throws Exception {\n        vars.putAll(args);"), c);
        assertTrue(c.contains("            hook_beforeEach();\n") || c.contains("        hook_beforeEach();\n"), c);
        assertTrue(c.contains("fn_f(args(\"who\", \"Ada\"));"));
        assertTrue(c.contains("if (!all(by(\"id@a\", By.id(\"a\"))).isEmpty()) {"));
        assertTrue(c.contains("fail(\"no\");"));
        assertTrue(c.contains("if (skipped(\"later\", \"skip\")) return;"));
        assertTrue(c.contains("        st_s();\n        st_later();\n"));
        String evil = export("subtest \"a */ int x; /* b\"\n{\nclick id@e\n}\n").content;
        assertTrue(evil.contains("    /** subtest a *\\/ int x; /* b (line 2). */\n"), evil);
    }

    @Test
    void openPicksTheDriver() {
        assertTrue(export("").content.contains("return new ChromeDriver(options);"));
        String ff = Exporters.export("selenium-java", "t", "open firefox-hdl\n", null).content;
        assertTrue(ff.contains("FirefoxOptions options = new FirefoxOptions();") && ff.contains("options.addArguments(\"-headless\");") && ff.contains("return new FirefoxDriver(options);"));
        assertTrue(ff.contains("return new RemoteWebDriver(URI.create(grid).toURL(), options);"));
        ExportResult two = export("open firefox\n");
        assertTrue(two.content.contains("// TODO(gatf-export): open firefox\n"));
        assertTrue(Exporters.export("selenium-java", "t", "open opera\n", null).content.contains("// TODO(gatf-export): open opera\n"));
    }

    /** Decodes the Java string literals Jv writes (for the embedded resolver check). */
    static final class TsLikeJava {
        static String decode(String lit) {
            StringBuilder b = new StringBuilder();
            for (int i = 1; i < lit.length() - 1; i++) {
                char c = lit.charAt(i);
                if (c != '\\') { b.append(c); continue; }
                char e = lit.charAt(++i);
                switch (e) {
                    case 'n': b.append('\n'); break;
                    case 'r': b.append('\r'); break;
                    case 't': b.append('\t'); break;
                    case 'b': b.append('\b'); break;
                    case 'f': b.append('\f'); break;
                    case 'u': b.append((char) Integer.parseInt(lit.substring(i + 1, i + 5), 16)); i += 4; break;
                    default:
                        if (e >= '0' && e <= '7') { b.append((char) Integer.parseInt(lit.substring(i, i + 3), 8)); i += 2; }
                        else b.append(e);
                }
            }
            return b.toString();
        }
    }
}
