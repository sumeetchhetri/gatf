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
package com.gatf.imports;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Δ163a: imported Playwright text reaches a seleasy step only through the Δ148 gates. */
class PlaywrightImporterUnsafeTest {

    private static ImportResult imp(String body) {
        return new PlaywrightImporter().importSource("test('t', async ({ page }) => {\n" + body + "});\n", "t");
    }

    private static boolean hasUnsafeStatement(String content) {
        for (String line : content.split("\n")) {
            String t = line.trim();
            if (t.startsWith("//")) continue;
            if (t.contains("$") && !t.contains("#[[")) return true;   // a '$' outside Velocity unparsed content
        }
        return false;
    }

    @Test
    void valuesWithDollarAreLiteral() {
        ImportResult r = imp("  await page.locator('#q').fill('price $total #set($x=1)');\n"
                + "  await page.locator('#k').press('$');\n"
                + "  await page.locator('#s').selectOption({ label: 'A $b' });\n"
                + "  await page.locator('#f').setInputFiles('/tmp/$HOME.txt');\n"
                + "  await expect(page.locator('#o')).toHaveText('Total: $5');\n"
                + "  await expect(page.locator('#v')).toHaveValue('$v');\n");
        assertTrue(r.content.contains("type \"#[[price $total #set($x=1)]]#\" id@q"), r.content);
        assertTrue(r.content.contains("sendkeys \"#[[$]]#\" id@k"), r.content);
        assertTrue(r.content.contains("select text@\"#[[A $b]]#\" id@s"), r.content);
        assertTrue(r.content.contains("upload \"#[[/tmp/$HOME.txt]]#\" id@f"), r.content);
        assertTrue(r.content.contains("?? id@o text =\"#[[Total: $5]]#\""), r.content);
        assertTrue(r.content.contains("?? id@v attr@value =\"#[[$v]]#\""), r.content);
        assertFalse(hasUnsafeStatement(r.content), r.content);
    }

    @Test
    void valuesThatCannotBeCarriedAreRefusedAndReported() {
        ImportResult r = imp("  await page.locator('#q').fill('a ]]# $x');\n"
                + "  await page.locator('#p').fill('$p{evil}');\n");
        assertFalse(r.content.contains("]]# $x"), r.content);
        assertFalse(r.content.contains("$p{evil}"), r.content);
        assertEquals(2, r.content.split("UNSUPPORTED: 'fill' not converted", -1).length - 1, r.content);
        assertEquals(2, r.unsupported.size());
    }

    @Test
    void gotoUrlsGoThroughGotoUrl() {
        ImportResult r = imp("  await page.goto('https://x.test/a$b');\n"
                + "  await page.goto('javascript:alert(1)');\n"
                + "  await page.goto(`${BASE}/login`);\n"
                + "  await page.goto('https://x.test/ok');\n");
        assertTrue(r.content.contains("goto #[[https://x.test/a$b]]#"), r.content);
        assertTrue(r.content.contains("goto https://x.test/ok"), r.content);
        assertFalse(r.content.contains("javascript:"), r.content);
        assertFalse(r.content.contains("goto ${BASE}"), r.content);
        assertEquals(2, r.content.split("UNSUPPORTED: 'goto' not converted", -1).length - 1, r.content);
        assertFalse(hasUnsafeStatement(r.content), r.content);
    }

    @Test
    void unsafeSelectorsAreRefused() {
        ImportResult r = imp("  await page.locator('a[href$=\".pdf\"]').click();\n"
                + "  await page.locator('#ok').click();\n"
                + "  await expect(page.locator('div.$x')).toBeVisible();\n"
                + "  await expect(page.getByText('#set(1)')).toBeVisible();\n");
        assertTrue(r.content.contains("click id@ok"), r.content);
        assertFalse(hasUnsafeStatement(r.content), r.content);
        assertFalse(r.content.contains("text@\"#set"), r.content);
        assertEquals(3, r.unsupported.size(), String.valueOf(r.content));
    }

    @Test
    void ordinaryImportsAreUnchanged() {
        ImportResult r = imp("  await page.goto('https://x.test/login');\n"
                + "  await page.locator('input.name').fill('Asha \"A\" \\\\ B');\n"
                + "  await page.locator('#s').selectOption({ index: 2 });\n"
                + "  await page.locator('#s').selectOption('v1');\n");
        assertTrue(r.content.contains("goto https://x.test/login"), r.content);
        assertTrue(r.content.contains("type \"Asha \\\"A\\\" \\\\\\\\ B\" css@\"input.name\""), r.content);
        assertTrue(r.content.contains("select index@2 id@s"), r.content);
        assertTrue(r.content.contains("select value@\"v1\" id@s"), r.content);
        assertTrue(r.unsupported.isEmpty(), String.valueOf(r.unsupported.size()));
    }
}
