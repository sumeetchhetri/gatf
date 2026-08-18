import { test, expect } from '@playwright/test';

// Integration: drives the SPA against a live backend seeded with sample-project — proves the
// /testcasefiles -> React test-file view path in a real browser. Files may sit under folders, so
// expand the folders and retry if none are directly visible, then assert a file node renders.
test('lists the sample-project test files in the Test Files view', async ({ page }) => {
  await page.goto('/app/');
  await page.getByRole('button', { name: 'Test Files' }).click();

  let files = page.locator('.tf-node.tf-file');
  if ((await files.count()) === 0) {
    const dirs = page.locator('.tf-node.tf-dir');
    for (let i = 0, n = await dirs.count(); i < n; i++) await dirs.nth(i).click().catch(() => {});
    files = page.locator('.tf-node.tf-file');
  }
  await expect(files.first()).toBeVisible();
});
