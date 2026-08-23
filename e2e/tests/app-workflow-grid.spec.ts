import { test, expect, type Page } from '@playwright/test';

// T4c — full workflows that need the server-side selenium toolchain / a Selenium Grid:
// saving a .sel (server-side transpile+compile parse-validation), running a selenium script, and the
// online debugger (attaches to a paused selenium run via gatfjdb). Skipped unless GATF_SELENIUM_GRID
// is set — start-gatf-server.sh maps it to SELENIUM_REMOTE_URL for the GATF server only (Playwright
// reserves SELENIUM_REMOTE_URL for routing its OWN browser, so we must not set that here). These are
// slow (compile + browser launch), so per-test timeouts are generous.
const NEEDS_GRID = !process.env.GATF_SELENIUM_GRID;

test.describe('GATF /app full workflows (Selenium Grid)', () => {
  test.skip(NEEDS_GRID, 'set GATF_SELENIUM_GRID to a Selenium Grid endpoint to run these');

  async function openFirstSel(page: Page) {
    await page.getByRole('button', { name: 'Test Files' }).click();
    let sel = page.locator('.tf-node.tf-file', { hasText: '.sel' });
    if ((await sel.count()) === 0) {
      const dirs = page.locator('.tf-node.tf-dir');
      for (let i = 0, n = await dirs.count(); i < n; i++) await dirs.nth(i).click().catch(() => {});
      sel = page.locator('.tf-node.tf-file', { hasText: '.sel' });
    }
    await sel.first().click();
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
    await openFirstSel(page); // opened file starts clean -> Debug is enabled

    await page.getByRole('button', { name: /Debug/ }).click();
    // the debugger control bar renders (it starts a paused selenium session via gatfjdb)
    await expect(page.locator('.dbg-bar')).toBeVisible({ timeout: 150_000 });
    await expect(page.getByRole('button', { name: /Continue/ })).toBeVisible();
    await page.getByRole('button', { name: /Stop/ }).click().catch(() => {}); // end the session
  });
});
