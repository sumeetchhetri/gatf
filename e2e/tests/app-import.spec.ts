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
import { test, expect, type Page } from '@playwright/test';

// Q2: cURL / Postman / HAR import through the live /import endpoint (preview only — nothing is saved).
async function preview(page: Page, kind: string, source: string) {
  await page.goto('/app/#/import');
  await page.locator('.seg-btn', { hasText: new RegExp('^' + kind + '$') }).click();
  await page.locator('textarea.import-source').fill(source);
  await page.getByRole('button', { name: 'Preview import' }).click();
  await expect(page.locator('.import-err')).toHaveCount(0);
  return page.locator('pre.import-preview');
}

test.describe('Import view — API importers', () => {
  test('cURL (bash, as copied from dev tools) becomes an API test case', async ({ page }) => {
    const out = await preview(
      page,
      'cURL',
      "curl 'https://api.example.test/v1/items?page=2' \\\n  -H 'content-type: application/json' \\\n  --data-raw '{\"name\":\"a\"}'",
    );
    await expect(out).toContainText('POST /v1/items');
    await expect(out).toContainText('https://api.example.test');
    await expect(out).toContainText('/v1/items?page=2');
    await expect(out).toContainText('{"name":"a"}');
  });

  test('Postman collection: folders, variables and status assertion', async ({ page }) => {
    const coll = {
      info: { name: 'e2e', schema: 'https://schema.getpostman.com/json/collection/v2.1.0/collection.json' },
      variable: [{ key: 'baseUrl', value: 'https://api.example.test' }],
      item: [
        {
          name: 'Users',
          item: [
            {
              name: 'List',
              request: { method: 'GET', url: { raw: '{{baseUrl}}/users', host: ['{{baseUrl}}'], path: ['users'] } },
              event: [{ listen: 'test', script: { exec: ['pm.response.to.have.status(200);'] } }],
            },
          ],
        },
      ],
    };
    const out = await preview(page, 'Postman', JSON.stringify(coll));
    await expect(out).toContainText('Users / List');
    await expect(out).toContainText('/users');
    await expect(page.locator('.import-result')).toContainText('globalVariables');
  });

  test('HAR: static assets skipped, recorded status kept', async ({ page }) => {
    const har = {
      log: {
        entries: [
          { _resourceType: 'script', request: { method: 'GET', url: 'https://app.test/main.js', headers: [] }, response: { status: 200, content: {} } },
          {
            _resourceType: 'fetch',
            request: { method: 'DELETE', url: 'https://api.test/items/7', headers: [{ name: 'Cookie', value: 'a=1' }] },
            response: { status: 204, content: {} },
          },
        ],
      },
    };
    const out = await preview(page, 'HAR', JSON.stringify(har));
    await expect(out).toContainText('DELETE /items/7');
    await expect(out).toContainText('204');
    await expect(out).not.toContainText('main.js');
    await expect(page.locator('.import-result-head')).toContainText('cookieHeadersDropped');
  });

  test('a non-curl paste reports why nothing was imported', async ({ page }) => {
    await page.goto('/app/#/import');
    await page.locator('.seg-btn', { hasText: /^cURL$/ }).click();
    await page.locator('textarea.import-source').fill('ls -la');
    await page.getByRole('button', { name: 'Preview import' }).click();
    await expect(page.locator('.import-err')).toContainText('nothing to import');
  });
});
