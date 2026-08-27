import { test, expect } from '@playwright/test';

test.beforeEach(async ({ page }) => {
  await page.goto('https://demo.gatf.example/login');
});

test('user can sign in and see the dashboard', async ({ page }) => {
  await page.fill('#username', 'sumeet');
  await page.fill('#password', 'demo-pass');
  await page.getByRole('button', { name: 'Sign In' }).click();
  await expect(page.locator('nav.app-menu')).toBeVisible();
  await expect(page.getByText('Welcome')).toContainText('Welcome');
});

test('walk-in registration', async ({ page }) => {
  await page.getByRole('button', { name: 'Appointments' }).click();
  await page.locator('input.patient-fname').fill('Asha');
  await page.locator('input.patient-lname').fill('Rao');
  await page.getByLabel('Mobile').fill('9876543210');
  await page.getByTestId('save-patient').click();
  await expect(page.locator('div.toast-success')).toHaveText('Patient registered');
  await expect(page).toHaveURL(/dashboard/);

  // a loop the importer cannot translate
  for (let i = 0; i < 3; i++) {
    await page.click('.next');
  }
});
