import { test, expect } from '@playwright/test';

// Opens a .sel file and asserts the CodeMirror 6 editor renders its content.
// (Saving a .sel triggers the server-side transpile+compile toolchain, so that path is covered by
// the server ITs / trace-e2e, not driven from the browser here.)
test('opens a .sel file in the CodeMirror editor', async ({ page }) => {
  await page.goto('/app/');
  await page.getByRole('button', { name: 'Test Files' }).click();

  // Files may sit under folders; if none are visible, expand the top-level folders and retry.
  let sel = page.locator('.tf-node.tf-file', { hasText: '.sel' });
  if ((await sel.count()) === 0) {
    const dirs = page.locator('.tf-node.tf-dir');
    for (let i = 0, n = await dirs.count(); i < n; i++) await dirs.nth(i).click().catch(() => {});
    sel = page.locator('.tf-node.tf-file', { hasText: '.sel' });
  }
  await sel.first().click();

  const editor = page.locator('.cm-content');
  await expect(editor).toBeVisible();
  await expect(editor).not.toBeEmpty();
});
