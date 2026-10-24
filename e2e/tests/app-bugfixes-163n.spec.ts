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
import { test, expect, type APIRequestContext, type Page } from '@playwright/test';
import { fileNode, HAS_BROWSER, storedZip } from './helpers';

// Δ163n: one spec per bug Sumeet reported on 2026-10-24 (editor, files, debugger), on the real server + SPA. Each one
// fails on the Δ163m build (see DELTA-163n.md for the base-vs-fixed runs). Every spec writes its own fixture files
// through the API, so they do not depend on each other or on sample-project's files staying put.

/** Create (or overwrite) a test file. /testcases POST writes the body even when it does not parse. */
async function putFile(request: APIRequestContext, path: string, body: string) {
  await request.post('/testcasefiles?testcaseFileName=' + path);
  await request.post('/testcases?testcaseFileName=' + path, { headers: { 'Content-Type': 'text/plain' }, data: body });
}

async function openInTree(page: Page, name: string) {
  await (await fileNode(page, name)).click();
  await expect(page.locator('.tf-tab.on')).toContainText(name);
}

const fileRow = (page: Page, name: string) => page.locator('.tf-node.tf-file', { has: page.locator('.tf-item-name', { hasText: new RegExp('^' + name.replace('.', '\\.') + '$') }) });
const dirRow = (page: Page, name: string) => page.locator('.tf-node.tf-dir', { has: page.locator('.tf-dir-name', { hasText: new RegExp('^' + name + '$') }) });

test.describe('Δ163n reported bugs', () => {
  test('bug 1: the file list is a narrow column, names ellipsised with the full path, hide/show remembered', async ({ page, request }) => {
    await putFile(request, 'ui/b1-a-very-long-file-name-that-will-not-fit-in-the-column.sel', 'open chrome\n');
    await page.setViewportSize({ width: 1400, height: 900 });
    await page.goto('/app/');
    await openInTree(page, 'b1-a-very-long-file-name');
    const list = (await page.locator('.tf-list').boundingBox())!;
    const content = (await page.locator('.tf-content').boundingBox())!;
    expect(list.width).toBeLessThanOrEqual(320);
    expect(content.width).toBeGreaterThan(900);
    const row = page.locator('.tf-node.tf-file', { hasText: 'b1-a-very-long-file-name' });
    await expect(row).toHaveAttribute('title', 'ui/b1-a-very-long-file-name-that-will-not-fit-in-the-column.sel');
    await expect(row.locator('.tf-item-name')).toHaveCSS('text-overflow', 'ellipsis');
    const nm = await row.locator('.tf-item-name').evaluate((e) => [e.scrollWidth, e.clientWidth]);
    expect(nm[0]).toBeGreaterThan(nm[1]); // really truncated
    // hide the list -> the editor takes the width; remembered across a reload
    await page.getByRole('button', { name: 'Hide the file list' }).click();
    await expect(page.locator('.tf-tree')).toHaveCount(0);
    expect((await page.locator('.tf-content').boundingBox())!.width).toBeGreaterThan(1200);
    await page.reload();
    await page.getByRole('button', { name: 'Test Files' }).click();
    await expect(page.getByRole('button', { name: 'Show the file list' })).toBeVisible();
    await page.getByRole('button', { name: 'Show the file list' }).click();
    await expect(page.locator('.tf-tree')).toBeVisible();
  });

  test('bug 2: a collapsible tree (also from Windows paths), expand/collapse all, remembered, auto-expand to the file', async ({ page }) => {
    // the server lists Windows paths as ui\login-flow.sel: replay that, the tree must still have folders
    await page.route('**/testcasefiles*', async (route) => {
      const u = new URL(route.request().url());
      if (route.request().method() !== 'GET' || u.searchParams.get('testcaseFileName')) return route.continue();
      const r = await route.fetch();
      const j = (await r.json()) as [string, string][];
      await route.fulfill({ response: r, json: j.map(([p, a]) => [p.replace(/\//g, '\\'), a]) });
    });
    await page.goto('/app/');
    await page.getByRole('button', { name: 'Test Files' }).click();
    await expect(dirRow(page, 'ui')).toBeVisible();
    await expect(dirRow(page, 'lib')).toBeVisible();
    // collapse one folder: its files go; remembered across a reload
    await dirRow(page, 'ui').click();
    await expect(fileRow(page, 'setup.sel')).toHaveCount(0);
    await page.reload();
    await page.getByRole('button', { name: 'Test Files' }).click();
    await expect(dirRow(page, 'api')).toBeVisible();
    await expect(dirRow(page, 'ui').locator('.tf-caret')).toHaveText('▸');
    await expect(fileRow(page, 'setup.sel')).toHaveCount(0);
    // expand all / collapse all
    await page.getByTitle('Expand all folders').click();
    await expect(fileRow(page, 'auth.sel')).toBeVisible();
    await page.getByTitle('Collapse all folders').click();
    await expect(page.locator('.tf-node.tf-dir .tf-caret', { hasText: '▾' })).toHaveCount(0);
    await expect(page.locator('.tf-node.tf-file')).toHaveCount(1); // only the top-level trace-smoke.sel
    // opening a file inside collapsed folders expands to it
    await page.goto('/app/#/testfiles?path=ui/lib/auth.sel');
    await expect(page.locator('.tf-node.tf-file.sel')).toContainText('auth.sel');
    await expect(dirRow(page, 'lib').locator('.tf-caret')).toHaveText('▾');
  });

  test('bug 3: parse, transpile and Java compile errors show the X and the reason on their .sel line', async ({ page, request }) => {
    const cases: [string, string, RegExp][] = [
      // file, content, the reason expected on line 3
      ['ui/b3-xpath.sel', 'open chrome\ngoto about:blank\n??5 xpath@"//div[" click\n', /Invalid xpath expression \/\/div\[/],
      ['ui/b3-npe.sel', 'open chrome\ngoto about:blank\nscroll sideways\n', /scroll with one of/],
      ['ui/b3-transpile.sel', 'open chrome\ngoto about:blank\nbreak\n', /Break is allowed only within a loop/],
      ['ui/b3-compile.sel', 'open chrome\ngoto about:blank\nexec int x = "a";\n', /Java compile error: incompatible types/],
    ];
    await page.goto('/app/');
    for (const [path, body, reason] of cases) {
      await putFile(request, path, body);
      await page.goto('/app/');
      await openInTree(page, path.split('/')[1]);
      await page.locator('.cm-content').click();
      await page.keyboard.press('Control+End');
      await page.keyboard.type(' ');
      await page.getByRole('button', { name: 'Save' }).click();
      const problem = page.locator('.tf-problem').first();
      await expect(problem, path).toBeVisible({ timeout: 60_000 });
      await expect(problem).toContainText('L3');
      await expect(problem).toContainText(reason);
      // the X in the gutter and the squiggle sit on line 3
      await expect(page.locator('.cm-gutter-lint .cm-lint-marker-error')).toHaveCount(1);
      await expect(page.locator('.cm-line').nth(2).locator('.cm-lintRange-error')).toHaveCount(1);
    }
    // verifier round: an import-only library (subtests, no `open`) and an xpath with escaped quotes still save clean
    await putFile(request, 'ui/b3-lib.sel', 'subtest "lib1"\n{\n    click xpath@"//a[text()=\\"Log in\\"]"\n}\n');
    await page.goto('/app/');
    await openInTree(page, 'b3-lib.sel');
    await page.locator('.cm-content').click();
    await page.keyboard.press('Control+End');
    await page.keyboard.type(' ');
    await page.getByRole('button', { name: 'Save' }).click();
    await expect(page.locator('.cfg-ok')).toContainText('saved', { timeout: 60_000 });
    await expect(page.locator('.tf-problem')).toHaveCount(0);
  });

  test('bug 4: rename inside a folder works, keeps the file open; ../ and absolute targets are refused', async ({ page, request }) => {
    await putFile(request, 'ui/b4-one.sel', 'open chrome\n// rename me\n');
    await page.goto('/app/');
    await openInTree(page, 'b4-one.sel');
    await page.getByRole('button', { name: 'Rename' }).click();
    await page.locator('.tf-rename input').fill('ui/b4-two.sel');
    await page.locator('.tf-rename input').press('Enter');
    await expect(fileRow(page, 'b4-two.sel')).toBeVisible();
    await expect(fileRow(page, 'b4-one.sel')).toHaveCount(0);
    await expect(page.locator('.view-error')).toHaveCount(0);
    await expect(page.locator('.tf-tab.on')).toContainText('two.sel');
    await expect(page.locator('.tf-node.tf-file.sel')).toContainText('two.sel');
    await expect(page.locator('.cm-content')).toContainText('rename me');
    for (const [to, why] of [
      ['ui/../../evil.sel', "'..'"],
      ['/tmp/evil.sel', 'Absolute'],
      ['C:\\evil.sel', 'Absolute'],
      ['api/b4-two.sel', 'keeps the file in its folder'],
    ]) {
      const r = await request.put('/testcasefiles?testcaseFileName=ui/b4-two.sel&testcaseFileNameTo=' + encodeURIComponent(to));
      expect(r.status(), to).toBe(400);
      expect(await r.text()).toContain(why);
    }
    // verifier round: DELETE refuses ../ too, and a stale target .attr is a 409 BEFORE anything moves
    const del = await request.delete('/testcasefiles?testcaseFileName=ui/../../outside.sel');
    expect(del.status()).toBe(400);
    await putFile(request, 'ui/b4-three.sel', 'open chrome\n');
    await request.put('/testcasefiles?testcaseFileName=ui/b4-three.sel&extras=' + encodeURIComponent(Buffer.from('{"a":1}').toString('base64')));
    await request.delete('/testcasefiles?testcaseFileName=ui/b4-three.sel'); // leaves ui/b4-three.sel.attr behind
    const stale = await request.put('/testcasefiles?testcaseFileName=ui/b4-two.sel&testcaseFileNameTo=ui/b4-three.sel');
    expect(stale.status()).toBe(409);
    await expect(fileRow(page, 'b4-two.sel')).toBeVisible();
  });

  test('bug 6: the editor cursor uses the theme text colour, 2px, in dark and light', async ({ page }) => {
    await page.goto('/app/');
    await openInTree(page, 'setup.sel');
    for (const theme of ['dark', 'light']) {
      if ((await page.evaluate(() => document.documentElement.getAttribute('data-theme'))) !== theme)
        await page.getByRole('button', { name: 'Toggle theme' }).click();
      await expect.poll(() => page.evaluate(() => document.documentElement.getAttribute('data-theme'))).toBe(theme);
      await page.locator('.cm-content').click();
      await expect(page.locator('.cm-cursor').first()).toBeAttached();
      const [border, width, fg] = await page.evaluate(() => {
        const c = getComputedStyle(document.querySelector('.cm-cursor') as HTMLElement);
        return [c.borderLeftColor, c.borderLeftWidth, getComputedStyle(document.querySelector('.cm-editor') as HTMLElement).color];
      });
      expect(border, theme).toBe(fg);
      expect(width, theme).toBe('2px');
    }
  });

  test('bug 7: the selected file, open folders and scroll survive Reports; deep link; back/forward', async ({ page, request }) => {
    await putFile(request, 'ui/b7-long.sel', 'open chrome\n' + Array.from({ length: 300 }, (_, i) => `// line ${i + 2}`).join('\n') + '\n');
    await page.goto('/app/');
    await openInTree(page, 'b7-long.sel');
    await expect(page).toHaveURL(/#\/testfiles\?path=ui\/b7-long\.sel$/);
    await dirRow(page, 'api').click(); // a collapsed folder to come back to
    await page.locator('.cm-scroller').evaluate((e) => (e.scrollTop = 1500));
    await page.waitForTimeout(200);
    await page.getByRole('button', { name: 'Reports' }).click();
    await expect(page.locator('.tf-tree')).toHaveCount(0);
    await page.getByRole('button', { name: 'Test Files' }).click();
    await expect(page.locator('.tf-tab.on')).toContainText('b7-long.sel');
    await expect(dirRow(page, 'api').locator('.tf-caret')).toHaveText('▸');
    await expect.poll(() => page.locator('.cm-scroller').evaluate((e) => e.scrollTop)).toBeGreaterThan(1400);
    // the nav round trip added ONE history entry: Back goes straight to Reports (verifier round: it was a no-op)
    await page.goBack();
    await expect(page).toHaveURL(/#\/reports$/);
    await page.goForward();
    await expect(page.locator('.tf-tab.on')).toContainText('b7-long.sel');
    // browser back from Reports returns to the file
    await page.getByRole('button', { name: 'Reports' }).click();
    await page.goBack();
    await expect(page.locator('.tf-tab.on')).toContainText('b7-long.sel');
    // back / forward between files
    await openInTree(page, 'setup.sel');
    await expect(page).toHaveURL(/path=ui\/setup\.sel$/);
    await page.goBack();
    await expect(page.locator('.tf-tab.on')).toContainText('b7-long.sel');
    await page.goForward();
    await expect(page.locator('.tf-tab.on')).toContainText('setup.sel');
    // deep link in a fresh page
    const p2 = await page.context().newPage();
    await p2.goto('/app/#/testfiles?path=api/users.xml');
    await expect(p2.locator('.tf-tab.on')).toContainText('users.xml');
  });

  test('bug 8: upload test files and a project zip (zip-slip refused); a trace.zip feeds the flow importer', async ({ page }) => {
    await page.goto('/app/');
    await page.getByRole('button', { name: 'Test Files' }).click();
    await page.getByRole('button', { name: /Upload/ }).click();
    await page.getByPlaceholder('into folder (blank = top level)').fill('b8');
    await page.getByTestId('tf-upload').setInputFiles({ name: 'uploaded.sel', mimeType: 'text/plain', buffer: Buffer.from('open chrome\n// uploaded\n') });
    await expect(page.locator('.tf-upmsg')).toContainText('Uploaded 1 file(s)');
    await expect(fileRow(page, 'uploaded.sel')).toBeVisible();
    await expect(page.locator('.tf-tab.on')).toContainText('uploaded.sel');
    await expect(page.locator('.cm-content')).toContainText('// uploaded');
    // a project zip: unpacked into the folder, a ../ entry and a script are skipped
    const zip = storedZip({ 'proj/main.sel': 'open chrome\n', 'proj/api/x.xml': '<TestCases></TestCases>', '../evil.sel': 'x', 'proj/run.sh': 'echo' });
    await page.getByTestId('tf-upload').setInputFiles({ name: 'proj.zip', mimeType: 'application/zip', buffer: zip });
    await expect(page.locator('.tf-upmsg')).toContainText('Uploaded 2 file(s); skipped 2');
    await expect(fileRow(page, 'main.sel')).toBeVisible();
    await expect(fileRow(page, 'x.xml')).toBeVisible();
    await expect(fileRow(page, 'evil.sel')).toHaveCount(0);
    // a form-encoded body would arrive empty: refused (415) instead of writing a 0-byte file (verifier round)
    const form = await page.request.post('/testcasefiles?testcaseFileName=b8/uploaded.sel&upload=true&overwrite=true', {
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      data: 'open chrome',
    });
    expect(form.status()).toBe(415);
    expect((await page.request.get('/testcases?testcaseFileName=b8/uploaded.sel').then((r) => r.text()))).toContain('// uploaded');
    // the flow importer takes the trace.zip its hint names
    await page.goto('/app/#/import');
    await page.getByRole('button', { name: 'Flow → load' }).click();
    const har = JSON.stringify({ log: { version: '1.2', entries: [] } });
    await page.getByTestId('import-file').setInputFiles({ name: 'run-trace.zip', mimeType: 'application/zip', buffer: storedZip({ 'trace.json': '{}', 'network.har': har }) });
    await expect(page.locator('textarea').first()).toHaveValue(har);
  });
});

test.describe('Δ163n bug 5: restart the current subtest in the debugger (needs a browser)', () => {
  test.skip(!HAS_BROWSER, 'set GATF_SELENIUM_GRID or GATF_E2E_CHROMEDRIVER');

  const curLine = async (page: Page) => Number(await page.locator('.dbg-cur .dbg-ln').innerText());
  const bp = (page: Page, ln: number) => page.locator('.dbg-line', { has: page.locator(`.dbg-ln:text-is("${ln}")`) }).locator('.dbg-bp');

  test('restart from inside the subtest and from inside a func it calls; the replay pauses on its first statement', async ({ page, request, baseURL }) => {
    test.setTimeout(300_000);
    const sel = [
      'open chrome', //                       1
      `goto ${baseURL}/app/`, //              2
      '@call "st1"', //                       3
      '?? css@"body"', //                     4
      '', //                                  5
      'func "f1"', //                         6
      '{', //                                 7
      '    ?? css@"body"', //                 8
      '}', //                                 9
      'subtest "st1"', //                    10
      '{', //                                11
      '    ?? css@"body"', //                12
      '    @call "f1"', //                   13
      '    ?? css@"#root"', //               14
      '}', //                                15
    ].join('\n');
    await putFile(request, 'b5-restart.sel', sel + '\n');
    await page.goto('/app/');
    await openInTree(page, 'b5-restart.sel');
    await page.getByRole('button', { name: /Debug/ }).click();
    await expect(page.locator('.dbg-cur')).toBeVisible({ timeout: 180_000 });
    // into the subtest: breakpoint on its last line, continue
    await bp(page, 14).click();
    await expect(bp(page, 14)).toHaveClass(/dbg-bp-on/);
    await page.keyboard.press('F8');
    await expect.poll(() => curLine(page), { timeout: 90_000 }).toBe(14);
    await page.getByRole('button', { name: /Restart subtest/ }).click();
    await expect.poll(() => curLine(page), { timeout: 90_000 }).toBe(12);
    // inside the func the subtest calls: the restart still replays the SUBTEST
    await bp(page, 8).click();
    await expect(bp(page, 8)).toHaveClass(/dbg-bp-on/);
    await page.keyboard.press('F8');
    await expect.poll(() => curLine(page), { timeout: 90_000 }).toBe(8);
    await page.getByRole('button', { name: /Restart subtest/ }).click();
    await expect.poll(() => curLine(page), { timeout: 90_000 }).toBe(12);
    // and it runs to the end
    await bp(page, 8).click();
    await bp(page, 14).click();
    await page.keyboard.press('F8');
    await expect(page.locator('.dbg .exec-status')).toContainText(/completed/, { timeout: 120_000 });
  });
});
