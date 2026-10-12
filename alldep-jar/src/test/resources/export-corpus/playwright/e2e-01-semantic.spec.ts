// Exported from e2e-01-semantic.sel by GATF (gatf export playwright). Review every TODO(gatf-export) line.
// 14 of 14 statement(s) exported, 0 TODO, 4 note(s): see the export report.
// Environment variables (names only): ${baseUrl} -> GATF_BASEURL
import { test, expect } from '@playwright/test';

function env(name: string): string {
  const value = process.env[name];
  if (value === undefined) throw new Error('gatf-export: set the environment variable ' + name);
  return value;
}

test('e2e-01-semantic', async ({ page }) => {
  // Δ160 export e2e: semantic locators against the fixture page (e2e/export/fixture); exported and run by npm run test:export.
  // gatf-export: 'open chrome' - the browser comes from the Playwright project (playwright.config)
  await page.goto(env('GATF_BASEURL') + '/index.html');
  await page.getByLabel(/^Email\s+address$/i).filter({ visible: true }).first().pressSequentially('me@example.com');
  await page.getByPlaceholder('search').filter({ visible: true }).first().pressSequentially('shoes');
  await page.getByRole('checkbox', { name: /^Subscribe$/i }).first().click();
  await expect(page.getByRole('checkbox', { name: /^Subscribe$/i, checked: true }).first()).toBeAttached();
  await page.getByRole('button', { name: /^Save$/i }).first().click();
  await expect(page.getByTestId('status').filter({ visible: true }).first()).toHaveText('Saved: me@example.com', { useInnerText: true });
  await expect(page.getByAltText(/^Company\s+logo$/i).filter({ visible: true }).first()).toBeAttached();
  await page.getByTitle('Close', { exact: true }).filter({ visible: true }).first().click();
  await expect(page.getByTitle('Close', { exact: true }).filter({ visible: true })).toHaveCount(0);
  await expect(page.getByText('Order placed', { exact: true }).filter({ visible: true }).first()).toBeAttached();
  await expect(page.getByText(/order (placed|shipped)/i).filter({ visible: true }).first()).toBeAttached();
  await expect(page.getByRole('heading', { name: /^Orders\s+\(3\)$/i, level: 2 }).first()).toBeAttached();
});
