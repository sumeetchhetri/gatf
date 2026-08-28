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
