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
import { expect, type Locator, type Page } from '@playwright/test';

/**
 * Open the Test Files view and return the first file node (optionally containing `text`).
 * Waits for the tree to finish loading first. Folders render EXPANDED by default (delta-045), so only
 * collapsed folders (caret ▸) are clicked. Clicking an expanded folder would collapse it, hide its
 * children, and leave a later click waiting on a hidden node until the test timeout.
 */
export async function fileNode(page: Page, text?: string): Promise<Locator> {
  await page.getByRole('button', { name: 'Test Files' }).click();
  await expect(page.locator('.tf-node').first()).toBeVisible();          // tree rendered (not "Loading…")
  const files = text ? page.locator('.tf-node.tf-file', { hasText: text }) : page.locator('.tf-node.tf-file');
  const collapsed = page.locator('.tf-node.tf-dir', { has: page.locator('.tf-caret', { hasText: '\u25b8' }) });
  for (let i = 0; i < 50 && (await files.count()) === 0 && (await collapsed.count()) > 0; i++) {
    await collapsed.first().click();
  }
  return files.first();
}
