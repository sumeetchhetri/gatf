// Exported from 14-waits.sel by GATF (gatf export playwright). Review every TODO(gatf-export) line.
// 32 of 33 statement(s) exported, 1 TODO, 11 note(s): see the export report.
import { test, expect } from '@playwright/test';

test('14-waits', async ({ page }) => {
  // Δ160 export corpus: waits, assertion operators, soft assertions, timeouts and the nowait opt-out.
  // gatf-export: 'open chrome' - the browser comes from the Playwright project (playwright.config)
  await page.locator('#ele1').first().click({ timeout: 10000 });
  await page.getByLabel(/^Email$/i).filter({ visible: true }).first().pressSequentially('a@b.c', { timeout: 5000 });
  await page.locator('#size').first().selectOption({ index: 2 }, { timeout: 5000 });
  await page.locator('#ele3').first().click({ force: true, timeout: 10000 });
  await page.locator('#fast-btn').first().click({ force: true });
  await page.locator('#q').first().pressSequentially('abc');
  await page.locator('#q').first().pressSequentially('abc');
  await page.locator('#q').first().blur();
  await page.locator('#q').first().pressSequentially('abc');
  await page.locator('#q').first().dispatchEvent('change');
  await page.locator('#q').first().pressSequentially('abc');
  await page.locator('#q').first().click();
  await page.locator('#q').first().focus();
  await page.locator('#q').first().pressSequentially('abc');
  await page.locator('#q').first().press('Backspace');
  await page.locator('#q').first().click();
  await page.locator('#x').first().focus();
  await page.locator('#x').first().click();
  await page.locator('#menu').first().hover();
  await page.locator('#item').first().click();
  await page.locator('#source').first().dragTo(page.locator('#target').first());
  await expect.soft(page.locator('#count').first()).not.toHaveText('0', { useInnerText: true, timeout: 5000 });
  await expect.poll(async () => {
    const actual = await page.locator('#x').first().innerText();
    return 'abc'.includes(actual);
  }).toBe(true);
  await expect.poll(async () => {
    const actual = await page.locator('#x').first().innerText();
    return 'abc'.endsWith(actual);
  }).toBe(true);
  await expect.poll(async () => {
    const actual = await page.locator('#x').first().innerText();
    return 'abc'.startsWith(actual);
  }).toBe(true);
  await expect.poll(async () => {
    const actual = await page.locator('#x').first().innerText();
    return 'b' > actual;
  }).toBe(true);
  await expect.poll(async () => {
    const actual = await page.locator('#x').first().innerText();
    return ['a', 'b'].includes(actual);
  }).toBe(true);
  await expect(page.locator('#x').first()).toHaveCSS('color', 'rgb(0, 0, 0)');
  await expect(page.locator('#x').first()).toBeVisible();
  await expect(page.locator('#x').first()).toBeDisabled();
  await expect.poll(async () => {
    const actual = String(await page.locator('#x').first().evaluate((el) => Boolean((el as HTMLInputElement).checked || (el as HTMLOptionElement).selected)));
    return 'true' === actual;
  }).toBe(true);
  // TODO(gatf-export): ?? id@x width ='10'
  await expect(page.locator('#x').first()).toBeAttached();
  await expect(page.locator('#y')).toHaveCount(0);
  await page.evaluate('window.scrollBy(0, 200)');
  await page.locator('#footer').first().evaluate((el) => el.scrollIntoView(true));
  await page.waitForTimeout(1000);
  await page.waitForTimeout(250);
  page.setDefaultTimeout(30000);
  // gatf-export: 'maximize' - set the viewport in playwright.config (use.viewport)
  await page.close();
});
