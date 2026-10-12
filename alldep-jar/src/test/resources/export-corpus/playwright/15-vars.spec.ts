// Exported from 15-vars.sel by GATF (gatf export playwright). Review every TODO(gatf-export) line.
// 16 of 16 statement(s) exported, 0 TODO, 4 note(s): see the export report.
// Environment variables (names only): ${apiToken} -> GATF_APITOKEN; ${base.url} -> GATF_BASE_URL; ${user_name} -> GATF_USER_NAME
import { test, expect } from '@playwright/test';

function env(name: string): string {
  const value = process.env[name];
  if (value === undefined) throw new Error('gatf-export: set the environment variable ' + name);
  return value;
}

/** seleasy's plain semantic value: the whole text, whitespace-collapsed, case-insensitive. */
function fullMatch(text: string): RegExp {
  const words = text.trim().split(/\s+/).map((w) => w.replace(/[\\^$.*+?()[\]{}|/]/g, '\\$&'));
  return new RegExp('^' + words.join('\\s+') + '$', 'i');
}

test('15-vars', async ({ page }) => {
  const vars: Record<string, string> = {};
  // Δ160 export corpus: variables, config references (environment), element and JavaScript values, dynamic locators.
  // gatf-export: 'open chrome' - the browser comes from the Playwright project (playwright.config)
  vars['name'] = 'Ada Lovelace';
  vars['copy'] = vars['name'];
  vars['count'] = '3';
  vars['heading'] = await page.locator('h1').first().innerText();
  vars['href'] = (await page.locator('a.next').first().getAttribute('href')) ?? '';
  vars['html'] = await page.locator('#box').first().innerHTML();
  vars['width'] = String(await page.evaluate('(function () {\n' + 'return document.body.clientWidth' + '\n})()'));
  await page.evaluate('(function () {\n' + 'console.log("hi from " + location.href)' + '\n})()');
  await page.goto(env('GATF_BASE_URL') + '/users/' + vars['count'] + '?token=' + env('GATF_APITOKEN'));
  await page.getByLabel(/^Username$/i).filter({ visible: true }).first().pressSequentially(env('GATF_USER_NAME'));
  await page.locator('[id=' + JSON.stringify(vars['copy']) + ']').first().pressSequentially(vars['name']);
  await page.getByLabel(fullMatch(vars['name'])).filter({ visible: true }).first().click();
  await page.getByRole('button', { name: new RegExp(vars['name'], 'i') }).first().click();
  await page.getByText(vars['name'], { exact: true }).filter({ visible: true }).first().click();
  await page.locator('#price').first().pressSequentially('Price $USD and !region!');
});
