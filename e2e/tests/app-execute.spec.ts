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
