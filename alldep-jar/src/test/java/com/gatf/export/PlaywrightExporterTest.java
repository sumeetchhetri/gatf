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

/** Δ160 — the Playwright backend: locator mapping, strictness, environment names, plain specs. */
class PlaywrightExporterTest {

    static ExportResult export(String src) {
        return export(src, false);
    }

    static ExportResult export(String src, boolean strict) {
        return Exporters.export("playwright", "t", "open chrome\n" + src, new ExportOptions().strictLocators(strict));
    }

    static String line(String sel) {
        String c = export(sel + "\n").content;
        String body = c.substring(c.indexOf("(playwright.config)\n") + "(playwright.config)\n".length());
        return body.substring(0, body.indexOf('\n')).trim();
    }

    @Test
    void semanticLocatorsMapOneToOne() {
        assertEquals("await page.getByRole('button', { name: /^Save$/i }).first().click();", line("click role@button[name=Save]"));
        assertEquals("await page.getByRole('button', { name: 'sav' }).first().click();", line("click role@button[name=~sav]"));
        assertEquals("await page.getByRole('button', { name: 'Save', exact: true }).first().click();", line("click role@button[name==Save]"));
        assertEquals("await page.getByRole('link', { name: /^home$/i }).first().click();", line("click role@link[name=/^home$/i]"));
        assertEquals("await page.getByRole('checkbox', { checked: true }).first().click();", line("click role@checkbox[checked]"));
        assertEquals("await page.getByRole('button', { name: /^X$/i, includeHidden: true }).first().click();", line("click role@button[name=X][include-hidden]"));
        assertEquals("await page.getByLabel(/^Email$/i).filter({ visible: true }).first().click();", line("click label@Email"));
        assertEquals("await page.getByPlaceholder('search').filter({ visible: true }).first().click();", line("click placeholder@~search"));
        assertEquals("await page.getByTestId('save-btn').filter({ visible: true }).first().click();", line("click testid@save-btn"));
        assertEquals("await page.getByTestId('save-btn').filter({ visible: true }).first().click();", line("click testid@=save-btn"));
        assertEquals("await page.getByAltText('Logo', { exact: true }).filter({ visible: true }).first().click();", line("click alt@=Logo"));
        assertEquals("await page.getByTitle(/^Close$/i).filter({ visible: true }).first().click();", line("click title@Close"));
        assertEquals("await page.getByText('Saved', { exact: true }).first().click();", line("click text@=Saved[include-hidden]"));
        assertEquals("await page.getByText(/sav(e|ed)/i).filter({ visible: true }).first().click();", line("click text@/sav(e|ed)/i"));
    }

    @Test
    void strictLocatorsKeepSemanticLocatorsStrict() {
        String c = export("click role@button[name=Save]\nclick label@Email\nclick css@.x\n", true).content;
        assertTrue(c.contains("page.getByRole('button', { name: /^Save$/i }).click();"));
        assertTrue(c.contains("page.getByLabel(/^Email$/i).filter({ visible: true }).click();"));
        assertTrue(c.contains("page.locator('.x').first().click();"));
    }

    @Test
    void otherLocators() {
        assertEquals("await page.locator('#a-b').first().click();", line("click id@a-b"));
        assertEquals("await page.locator('[id=\"1st\"]').first().click();", line("click id@1st"));
        assertEquals("await page.locator('[name=\"q\"]').first().click();", line("click name@q"));
        assertEquals("await page.locator('.btn').first().click();", line("click class@btn"));
        assertEquals("await page.locator('xpath=//a').first().click();", line("click xpath@//a"));
        assertEquals("await page.locator('css=//odd').first().click();", line("click css@'//odd'"));
        assertEquals("await page.locator('a', { hasText: /^\\s*Next\\s*$/ }).first().click();", line("click linktext@Next"));
        assertEquals("await page.locator('*:focus').first().click();", line("click active"));
    }

    @Test
    void secretsAndConfigBecomeEnvironmentVariables() {
        ExportResult r = export("goto ${baseUrl}/x\ntype ${user.password} id@p\n");
        assertTrue(r.content.contains("await page.goto(env('GATF_BASEURL') + '/x');"));
        assertTrue(r.content.contains("pressSequentially(env('GATF_USER_PASSWORD'))"));
        assertEquals("GATF_USER_PASSWORD", r.env.get("user.password"));
        assertTrue(r.report().contains("${user.password} → GATF_USER_PASSWORD"));
    }

    @Test
    void environmentNameCollisionIsAnError() {
        ExportException e = assertThrows(ExportException.class, () -> export("goto ${base_url}\ngoto ${base.url}\n"));
        assertTrue(e.getMessage().contains("${base_url}") && e.getMessage().contains("${base.url}") && e.getMessage().contains("GATF_BASE_URL"), e.getMessage());
        export("goto ${baseUrl}\ngoto ${base_url}\n");   // GATF_BASEURL vs GATF_BASE_URL: no collision
    }

    @Test
    void specsStayPlain() {
        ExportResult r = export("goto ${a}\nvar x 'y'\nclick label@$v{x}\n?? id@t text %ok\n");
        Matcher imports = Pattern.compile("(?m)^import .*$").matcher(r.content);
        int n = 0;
        while (imports.find()) {
            n++;
            assertEquals("import { test, expect } from '@playwright/test';", imports.group());
        }
        assertEquals(1, n);
        assertEquals(1, r.content.split("process\\.env", -1).length - 1, "process.env only inside the env() helper");
        assertFalse(r.content.contains("require("));
    }

    @Test
    void unsupportedIsCommentedAndListedNeverDropped() {
        ExportResult r = export("## class@row\n{\n    click id@a\n    exec @print(1)\n}\nclick id@ok\n");
        assertTrue(r.content.contains("// TODO(gatf-export): ## class@row\n"));
        assertTrue(r.content.contains("// TODO(gatf-export): click id@a\n"));
        assertTrue(r.content.contains("// TODO(gatf-export): exec @print(1)\n"));
        assertTrue(r.content.contains("await page.locator('#ok').first().click();"));
        assertEquals(3, r.items(ExportResult.Level.UNSUPPORTED).size());
        assertEquals(Integer.valueOf(5), r.stats.get("statements"));   // open, ##, its two statements, click
        assertEquals(Integer.valueOf(2), r.stats.get("exported"));
    }

    @Test
    void ifChainsAndSubtests() {
        String c = export("subtest \"s\"\n{\n? id@a\n{\nclick id@b\n}\n:\n{\nfail 'no'\n}\n}\n@call \"S\"\n").content;
        assertTrue(c.contains("const st_s = async (): Promise<void> => {"));
        assertTrue(c.contains("if ((await page.locator('#a').count()) > 0) {"));
        assertTrue(c.contains("} else {"));
        assertTrue(c.contains("throw new Error('no');"));
        assertTrue(c.contains("await st_s();"));
    }
}
