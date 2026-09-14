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
import { test, expect, type Page } from '@playwright/test';
import { fileNode } from './helpers';

// T4c — full workflows that need the server-side selenium toolchain / a Selenium Grid:
// saving a .sel (server-side transpile+compile parse-validation), running a selenium script, and the
// online debugger (attaches to a paused selenium run via gatfjdb). Skipped unless GATF_SELENIUM_GRID
// is set — start-gatf-server.sh maps it to SELENIUM_REMOTE_URL for the GATF server only (Playwright
// reserves SELENIUM_REMOTE_URL for routing its OWN browser, so we must not set that here). These are
// slow (compile + browser launch), so per-test timeouts are generous.
const NEEDS_GRID = !process.env.GATF_SELENIUM_GRID;

test.describe('GATF /app full workflows (Selenium Grid)', () => {
  test.skip(NEEDS_GRID, 'set GATF_SELENIUM_GRID to a Selenium Grid endpoint to run these');

  async function openFirstSel(page: Page, name = '.sel') {
    await (await fileNode(page, name)).click();
    await expect(page.locator('.cm-content')).toBeVisible();
  }

  test('edits and saves a .sel (server-side parse-validation)', async ({ page }) => {
    test.setTimeout(120_000);
    await page.goto('/app/');
    await openFirstSel(page);

    // append a harmless comment at the end, then save
    const editor = page.locator('.cm-content');
    await editor.click();
    await page.keyboard.press('Control+End');
    await page.keyboard.type('\n// e2e edit');

    const save = page.getByRole('button', { name: 'Save' });
    await expect(save).toBeEnabled();
    await save.click();
    // a successful save clears the dirty flag -> the Debug action re-enables (dirty gate)
    await expect(page.getByRole('button', { name: /Debug/ })).toBeEnabled({ timeout: 90_000 });
  });

  test('runs a selenium script to a terminal state', async ({ page }) => {
    test.setTimeout(180_000);
    await page.goto('/app/');
    await page.getByRole('button', { name: 'Execute' }).click();
    await page.getByRole('button', { name: 'Selenium' }).click();

    // select-all (first checkbox) -> at least one file selected -> Run enables
    await page.locator('input[type="checkbox"]').first().check().catch(() => {});
    const run = page.getByRole('button', { name: /Run/ });
    await expect(run).toBeEnabled();
    await run.click();
    // browser launch + run + report: Run goes busy, then re-enables when a terminal state is reached
    await expect(run).toBeDisabled();
    await expect(run).toBeEnabled({ timeout: 150_000 });
  });

  test('opens the online debugger for a .sel', async ({ page }) => {
    test.setTimeout(180_000);
    await page.goto('/app/');
    // debug needs a RUNNABLE script (library .sel files like ui/lib/auth.sel have no `open`)
    await openFirstSel(page, process.env.GATF_E2E_DEBUG_SEL || 'trace-smoke.sel'); // opened file starts clean -> Debug is enabled

    await page.getByRole('button', { name: /Debug/ }).click();
    // the debugger control bar renders (it starts a paused selenium session via gatfjdb)
    await expect(page.locator('.dbg-bar')).toBeVisible({ timeout: 150_000 });
    await expect(page.getByRole('button', { name: /Continue/ })).toBeVisible();
    await page.getByRole('button', { name: /Stop/ }).click().catch(() => {}); // end the session
  });
});
