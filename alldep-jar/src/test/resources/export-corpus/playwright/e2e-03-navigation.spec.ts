// Exported from e2e-03-navigation.sel by GATF (gatf export playwright). Review every TODO(gatf-export) line.
// 20 of 20 statement(s) exported, 0 TODO, 3 note(s): see the export report.
// Environment variables (names only): ${baseUrl} -> GATF_BASEURL
import { test, expect } from '@playwright/test';

function env(name: string): string {
  const value = process.env[name];
  if (value === undefined) throw new Error('gatf-export: set the environment variable ' + name);
  return value;
}

test('e2e-03-navigation', async ({ page }) => {
  const vars: Record<string, string> = {};
  const st_save = async (): Promise<void> => {
    await test.step('save', async () => {
      await page.getByLabel(/^Email\s+address$/i).filter({ visible: true }).first().pressSequentially('x@y.z');
      await page.getByRole('button', { name: /^Save$/i }).first().click();
      await expect(page.getByTestId('status').filter({ visible: true }).first()).toHaveText('Saved: x@y.z', { useInnerText: true });
    });
  };
  // Δ160 export e2e: navigation, variables and a subtest against the fixture page.
  // gatf-export: 'open chrome' - the browser comes from the Playwright project (playwright.config)
  await page.goto(env('GATF_BASEURL') + '/index.html');
  await page.getByRole('link', { name: /^Next\s+page$/i }).first().click();
  await expect(page).toHaveURL(env('GATF_BASEURL') + '/page2.html');
  await expect(page.locator('h1').first()).toHaveText('Second page', { useInnerText: true });
  await page.goBack();
  await expect(page).toHaveTitle('GATF export fixture');
  await page.goForward();
  await expect(page.locator('h1').first()).toHaveText('Second page', { useInnerText: true });
  await page.reload();
  await page.waitForLoadState('load');
  await expect(page).toHaveTitle('Page two');
  vars['who'] = 'Ada';
  await page.goto(env('GATF_BASEURL') + '/index.html?name=' + vars['who']);
  await expect(page.locator('#greeting').first()).toHaveText('Hello Ada', { useInnerText: true });
  await st_save();
});
