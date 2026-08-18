import { test, expect } from '@playwright/test';

// The Execute view renders its controls (Selenium/API mode toggle + the Run button). Running a suite
// to completion is exercised server-side by the API-execution IT (066c) and by trace-e2e for selenium
// (the sample suite's URLs are placeholders and selenium needs a Grid), so the UI is asserted here.
test('renders the Execute controls', async ({ page }) => {
  await page.goto('/app/');
  await page.getByRole('button', { name: 'Execute' }).click();

  await expect(page.getByRole('heading', { name: 'Execute' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Selenium' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'API' })).toBeVisible();
  await expect(page.getByRole('button', { name: /Run/ })).toBeVisible();
});
