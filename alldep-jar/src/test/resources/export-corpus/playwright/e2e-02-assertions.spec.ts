// Exported from e2e-02-assertions.sel by GATF (gatf export playwright). Review every TODO(gatf-export) line.
// 16 of 16 statement(s) exported, 0 TODO, 6 note(s): see the export report.
// Environment variables (names only): ${baseUrl} -> GATF_BASEURL
import { test, expect } from '@playwright/test';

function env(name: string): string {
  const value = process.env[name];
  if (value === undefined) throw new Error('gatf-export: set the environment variable ' + name);
  return value;
}

test('e2e-02-assertions', async ({ page }) => {
  // Δ160 export e2e: assertions, soft assertions, waits and select against the fixture page.
  // gatf-export: 'open chrome' - the browser comes from the Playwright project (playwright.config)
  await page.goto(env('GATF_BASEURL') + '/index.html');
  await expect(page.locator('h1').first()).toHaveText('Welcome', { useInnerText: true });
  await expect(page).toHaveTitle('GATF export fixture');
  await expect(page.locator('#count').first()).not.toHaveText('0', { useInnerText: true });
  await expect.soft(page.locator('#count').first()).toHaveText('3', { useInnerText: true });
  await expect(page.locator('#email').first()).toHaveAttribute('placeholder', 'you@example.com');
  await page.locator('#email').first().pressSequentially('abc');
  await expect(page.locator('#email').first()).toHaveValue('abc');
  await page.locator('#size').first().selectOption({ label: 'Medium' });
  await expect(page.locator('#size').first()).toHaveValue('m');
  // seleasy compares expected.op(actual): this passes once the late banner reads 'Banner shown'
  await expect.poll(async () => {
    const actual = await page.locator('#banner').first().innerText();
    return 'Banner shown later'.startsWith(actual);
  }).toBe(true);
  await expect.poll(async () => {
    const actual = await page.locator('#count').first().innerText();
    return '3' <= actual;
  }).toBe(true);
  await expect(page.locator('#banner').first()).toBeAttached();
  await expect(page.locator('#ghost')).toHaveCount(0);
  await expect.poll(async () => {
    const actual = await page.locator('#size').first().evaluate((el) => el.tagName.toLowerCase());
    return 'select' === actual;
  }).toBe(true);
});
