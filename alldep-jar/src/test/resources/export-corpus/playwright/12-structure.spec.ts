// Exported from 12-structure.sel by GATF (gatf export playwright). Review every TODO(gatf-export) line.
// 24 of 24 statement(s) exported, 0 TODO, 5 note(s): see the export report.
// Environment variables (names only): ${baseUrl} -> GATF_BASEURL
import { test, expect } from '@playwright/test';

function env(name: string): string {
  const value = process.env[name];
  if (value === undefined) throw new Error('gatf-export: set the environment variable ' + name);
  return value;
}

test('12-structure', async ({ page }) => {
  const vars: Record<string, string> = {};
  const hook_beforeEach = async (): Promise<void> => {
    await test.step('beforeEach', async () => {
      await page.goto(env('GATF_BASEURL') + '/index.html');
    });
  };
  const hook_afterEach = async (): Promise<void> => {
    await test.step('afterEach', async () => {
      await page.screenshot({ path: 'each.png' });
    });
  };
  const fn_fill = async (args: Record<string, string> = {}): Promise<void> => {
    Object.assign(vars, args);
    await page.getByLabel(/^Email\s+address$/i).filter({ visible: true }).first().pressSequentially(vars['who']);
  };
  const st_save = async (args: Record<string, string> = {}): Promise<void> => {
    await test.step.skip('save', async () => {
      Object.assign(vars, args);
      await fn_fill({ 'who': vars['who'] });
      await page.getByRole('button', { name: /^Save$/i }).first().click();
      if ((await page.getByTestId('status').filter({ visible: true }).count()) > 0) {
        test.info().annotations.push({ type: 'pass', description: 'saved' });
      } else if ((await page.locator('#error').count()) > 0) {
        throw new Error('error shown');
      } else {
        expect.soft(false, 'nothing happened').toBe(true);
      }
    });
  };
  const st_only_one = async (): Promise<void> => {
    await test.step('only one', async () => {
      try {
        await hook_beforeEach();
        if ((await page.locator('#spinner').count()) === 0) {
          await page.locator('#go').first().click();
        }
      } finally {
        await hook_afterEach();
      }
    });
  };
  const st_later = async (): Promise<void> => {
    await test.step.skip('later', async () => {
      await page.locator('#later').first().click();
    });
  };
  // Δ160 export corpus: subtests, funcs, hooks, markers, arguments and if/else chains.
  // gatf-export: 'open chrome' - the browser comes from the Playwright project (playwright.config)
  await st_save({ 'who': 'Ada' });
  await st_only_one();
  await st_later();
});
