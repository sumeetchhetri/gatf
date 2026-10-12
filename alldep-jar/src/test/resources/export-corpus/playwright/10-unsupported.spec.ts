// Exported from 10-unsupported.sel by GATF (gatf export playwright). Review every TODO(gatf-export) line.
// 6 of 28 statement(s) exported, 22 TODO, 2 note(s): see the export report.
import { test, expect } from '@playwright/test';

test('10-unsupported', async ({ page }) => {
  // Δ160 export corpus: constructs with no Playwright mapping become TODO(gatf-export) lines; whole blocks stay commented.
  // gatf-export: 'open chrome' - the browser comes from the Playwright project (playwright.config)
  await page.goto('https://shop.test/');
  // TODO(gatf-export): #j if(1==1) {} else {}
  // TODO(gatf-export): ## class@"row"
  // TODO(gatf-export): {
  // TODO(gatf-export): click xpath@"//a[@index]"
  // TODO(gatf-export): }
  // TODO(gatf-export): #counter 0 5
  // TODO(gatf-export): {
  // TODO(gatf-export): exec @print(@index)
  // TODO(gatf-export): }
  // TODO(gatf-export): <<<(java) var0
  // TODO(gatf-export): System.out.println("}");
  // TODO(gatf-export): >>>
  // TODO(gatf-export): curl post http://abc.com
  // TODO(gatf-export): {
  // TODO(gatf-export): [
  // TODO(gatf-export): Content-Type: application/xml
  // TODO(gatf-export): ]
  // TODO(gatf-export): <abc>abc</abc>
  // TODO(gatf-export): }
  // TODO(gatf-export): ? eval "a"=="a"
  // TODO(gatf-export): {
  // TODO(gatf-export): click id@a
  // TODO(gatf-export): }
  // TODO(gatf-export): :
  // TODO(gatf-export): {
  // TODO(gatf-export): click id@b
  // TODO(gatf-export): }
  if ((await page.locator('#x').count()) > 0) {
    await page.locator('#c').first().click();
  } else if ((await page.locator('#y').count()) === 0) {
    await page.locator('#d').first().click();
  }
  // TODO(gatf-export): frame "my-frame"
  // TODO(gatf-export): include lib/common.sel
  // TODO(gatf-export): randomize id@'ele1' alphanumeric 12
  // TODO(gatf-export): alert 'Hello'
  // TODO(gatf-export): ^mobileOnly click id@burger
  // TODO(gatf-export): click jq@'.menu:visible'
  // TODO(gatf-export): click relative tag@input rightof label@Email
  // TODO(gatf-export): open firefox
  // TODO(gatf-export): @call "nowhere"
  // TODO(gatf-export): a11y check
  // TODO(gatf-export): ?? text@'/*x/'
});
