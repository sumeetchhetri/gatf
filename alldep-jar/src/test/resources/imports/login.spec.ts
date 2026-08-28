/*
    Copyright 2013-2026, Sumeet Chhetri

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/
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
