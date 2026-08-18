import { test, expect } from '@playwright/test';

// Smoke: the embedded React console loads from the live server and the primary workflow nav renders.
test.describe('GATF /app shell', () => {
  test('loads the console shell with the primary tabs', async ({ page }) => {
    await page.goto('/app/');
    await expect(page.locator('.nav-brand')).toBeVisible(); // unique shell marker ("GATF console")
    for (const label of ['Config', 'Test Files', 'Execute', 'Reports']) {
      await expect(page.getByRole('button', { name: label })).toBeVisible();
    }
  });

  test('navigates each primary tab without error (shell stays mounted)', async ({ page }) => {
    await page.goto('/app/');
    for (const label of ['Config', 'Test Files', 'Execute', 'Reports']) {
      await page.getByRole('button', { name: label }).click();
      await expect(page.locator('.nav-brand')).toBeVisible();
    }
  });
});
