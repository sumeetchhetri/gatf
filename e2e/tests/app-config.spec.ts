import { test, expect } from '@playwright/test';

// The config edit -> save round-trip in a real browser. Config save runs a full context init
// server-side (proven headless by the server ITs) and persists — so this is the safe "edit + save".
test('edits and saves the executor config', async ({ page }) => {
  await page.goto('/app/');
  await page.getByRole('button', { name: 'Config' }).click();

  const firstField = page.locator('.sf').getByRole('textbox').first();
  await expect(firstField).toBeVisible();
  await firstField.fill('https://e2e.example');

  const save = page.getByRole('button', { name: 'Save' });
  await expect(save).toBeEnabled(); // becomes enabled once the form is dirty
  await save.click();
  await expect(page.getByText('saved ✓')).toBeVisible();
});
