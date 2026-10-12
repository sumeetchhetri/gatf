// Exported from 11-escaping.sel by GATF (gatf export playwright). Review every TODO(gatf-export) line.
// 15 of 15 statement(s) exported, 0 TODO, 3 note(s): see the export report.
// Environment variables (names only): ${baseUrl} -> GATF_BASEURL
import { test, expect } from '@playwright/test';

function env(name: string): string {
  const value = process.env[name];
  if (value === undefined) throw new Error('gatf-export: set the environment variable ' + name);
  return value;
}

test('11-escaping', async ({ page }) => {
  const vars: Record<string, string> = {};
  // Δ160 export corpus: adversarial strings. Every value must survive as the same characters in TypeScript.
  // gatf-export: 'open chrome' - the browser comes from the Playwright project (playwright.config)
  await page.goto(env('GATF_BASEURL') + '/a\'b\\c');
  await page.locator('#q').first().pressSequentially('it\'s \\"quoted\\" with `back` ticks and ${ not a ref} and ' + vars['who']);
  await page.locator('#q').first().pressSequentially('back\\slash \\n stays two chars');
  await page.locator('#q').first().pressSequentially('line sep \u2028 para \u2029 end');
  await page.locator('#q').first().pressSequentially('</script> */ /* -->');
  await page.locator('a[title="x\\"y"]').first().click();
  await page.locator('[id="weird id\\"]"]').first().click();
  await page.locator('[name="n\\\\\\\\a"]').first().click();
  await page.locator('xpath=//a[text()=\'it\'\'s\']').first().click();
  await expect(page.getByLabel(new RegExp('a\\/b[/]c', 'i')).filter({ visible: true }).first()).toBeAttached();
  await expect(page.getByRole('button', { name: /^\*star$/i }).first()).toBeAttached();
  await expect(page.locator('#x').first()).toHaveText('x\\y z', { useInnerText: true });
  await page.locator('#x').first().pressSequentially('C:\\temp dir\\a b');
  // comment with a line separator \u2028 inside
  throw new Error('emoji 😀 and tab\tend');
});
