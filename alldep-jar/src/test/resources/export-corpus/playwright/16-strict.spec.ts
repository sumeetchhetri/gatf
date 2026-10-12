// Exported from 16-strict.sel by GATF (gatf export playwright). Review every TODO(gatf-export) line.
// 7 of 7 statement(s) exported, 0 TODO, 3 note(s): see the export report.
import { test, expect } from '@playwright/test';

test('16-strict', async ({ page }) => {
  // export-options: --strict-locators
  // Δ160 export corpus: --strict-locators keeps semantic locators strict (no .first()); CSS/XPath/id still act on the first match.
  // gatf-export: 'open chrome' - the browser comes from the Playwright project (playwright.config)
  await page.getByRole('button', { name: /^Save$/i }).click();
  await page.getByLabel(/^Email$/i).filter({ visible: true }).pressSequentially('x');
  await expect(page.getByTestId('status').filter({ visible: true })).toHaveText('ok', { useInnerText: true });
  await page.locator('.btn').first().click();
  await page.getByRole('button', { name: /^Hidden$/i, includeHidden: true }).click();
  await page.getByAltText(/^Logo$/i).click();
});
