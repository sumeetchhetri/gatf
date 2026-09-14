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
import { fileNode } from './helpers';

// Opens a .sel file and asserts the CodeMirror 6 editor renders its content.
// (Saving a .sel triggers the server-side transpile+compile toolchain, so that path is covered by
// the server ITs / trace-e2e, not driven from the browser here.)
test('opens a .sel file in the CodeMirror editor', async ({ page }) => {
  await page.goto('/app/');
  await (await fileNode(page, '.sel')).click();

  const editor = page.locator('.cm-content');
  await expect(editor).toBeVisible();
  await expect(editor).not.toBeEmpty();
});
