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
import { fileNode, HAS_BROWSER } from './helpers';
import * as fs from 'fs';
import * as os from 'os';
import * as path from 'path';

// Δ163o bug 9: a failed subtest yields a trace.zip linked from ITS report row, with tracing left at the default (no
// traceMode, enableTrace false -> retain-on-failure since Δ163o), for runs from the editor (Run), the Execute view and
// the debugger; traceMode "off" opts out. The CLI path is covered in DELTA-163o.md (same executor code as Execute).

const SCRIPT = (name: string) =>
  ['open chrome', 'goto about:blank', '@call "ok1"', '@call "bad1"', '', 'subtest "ok1"', '{', '    ??5 css@"body"', '}', 'subtest "bad1"', '{', `    ??2 css@"#no-such-element-${name}"`, '}', ''].join('\n');

async function upload(request: APIRequestContext, path: string, body: string) {
  // create + overwrite through /testcases (exists on every build); the body is written even though it would fail to run
  await request.post('/testcasefiles?testcaseFileName=' + path);
  await request.post('/testcases?testcaseFileName=' + path, { headers: { 'Content-Type': 'text/plain' }, data: body });
}

async function setTraceConfig(request: APIRequestContext, traceMode: string | null) {
  const c = (await (await request.get('/configure?configType=executor')).json()) as Record<string, unknown>;
  delete c.traceMode;
  if (traceMode) c.traceMode = traceMode;
  c.enableTrace = false; // the default: neither traceMode nor enableTrace set
  c.traceVideo = false;
  const r = await request.post('/configure?configType=executor', { data: c });
  expect(r.ok()).toBe(true);
}

/** The newest run folder's report row data for subtest `sub` of `script`. */
async function subtestRow(request: APIRequestContext, script: string, sub: string) {
  const paths = (await (await request.get('/reports?action=paths&type=sel')).json()) as { paths: Record<string, unknown>[] };
  const run = Object.keys(paths.paths[0])[0];
  const fin = (await (await request.get(`/reports/${run}/selenium-index.data.json`)).json()) as { indexes: { local: Record<string, string> } };
  const idxHtml = Object.values(fin.indexes.local)[0];
  const idx = (await (await request.get(`/reports/${run}/${idxHtml.replace(/\.html$/, '.data.json')}`)).json()) as {
    testsMap: { local: Record<string, Record<string, string[][]>>[] };
  };
  const rows = Object.values(idx.testsMap.local[0][script])[0];
  const row = rows.find((r) => r[0].startsWith(sub))!;
  expect(row, sub + ' row').toBeTruthy();
  const data = (await (await request.get(`/reports/${run}/${row[1].replace(/\.html$/, '.data.json')}`)).json()) as { tracePath: string | null; status: boolean };
  return { run, row, data };
}

async function expectZip(request: APIRequestContext, url: string) {
  const r = await request.get(url);
  expect(r.status(), url).toBe(200);
  const b = await r.body();
  expect(b.subarray(0, 2).toString('latin1'), url).toBe('PK');
}

test.describe('Δ163o bug 9: trace.zip for a failed subtest (needs a browser)', () => {
  test.skip(!HAS_BROWSER, 'set GATF_SELENIUM_GRID or GATF_E2E_CHROMEDRIVER');
  test.describe.configure({ mode: 'serial' });
  let saved: Record<string, unknown> | null = null;

  test.beforeAll(async ({ playwright, baseURL }) => {
    const rq = await playwright.request.newContext({ baseURL });
    saved = (await (await rq.get('/configure?configType=executor')).json()) as Record<string, unknown>;
    await rq.dispose();
  });
  test.afterAll(async ({ playwright, baseURL }) => {
    if (!saved) return;
    const rq = await playwright.request.newContext({ baseURL });
    await rq.post('/configure?configType=executor', { data: saved });
    await rq.dispose();
  });

  async function runInEditor(page: Page, file: string) {
    await page.goto('/app/');
    await (await fileNode(page, file)).click();
    await expect(page.locator('.cm-content')).toBeVisible();
    const done = page.waitForResponse((r) => r.url().includes('action=playTest'), { timeout: 120_000 });
    await page.getByRole('button', { name: /Run/ }).first().click();
    await done;
  }

  test('editor Run: the failed subtest row links a kept trace.zip; traceMode off keeps none', async ({ page, request }) => {
    test.setTimeout(240_000);
    await setTraceConfig(request, null);
    await upload(request, 'b9-editor.sel', SCRIPT('editor'));
    await runInEditor(page, 'b9-editor.sel');
    const bad = await subtestRow(request, 'b9-editor.sel', 'bad1');
    expect(bad.data.status).toBe(false);
    expect(bad.data.tracePath).toBeTruthy();
    await expectZip(request, `/reports/${bad.run}/${bad.data.tracePath}`);
    const ok = await subtestRow(request, 'b9-editor.sel', 'ok1');
    expect(ok.data.tracePath).toBe(bad.data.tracePath); // one trace per session, linked from every row of that session

    await setTraceConfig(request, 'off');
    await runInEditor(page, 'b9-editor.sel');
    const off = await subtestRow(request, 'b9-editor.sel', 'bad1');
    expect(off.run).not.toBe(bad.run);
    expect(off.data.tracePath).toBeNull();
  });

  test('Execute view run: the failed subtest row links a kept trace.zip', async ({ request }) => {
    test.setTimeout(240_000);
    await setTraceConfig(request, null);
    await upload(request, 'b9-exec.sel', SCRIPT('exec'));
    const start = await request.put('/execute?pluginType=executor-sel', { data: ['b9-exec.sel'] });
    expect(start.ok()).toBe(true);
    await expect
      .poll(async () => JSON.stringify(await (await request.get('/execute?pluginType=executor-sel')).json()), { timeout: 150_000, intervals: [2000] })
      .not.toContain('already in progress');
    const bad = await subtestRow(request, 'b9-exec.sel', 'bad1');
    expect(bad.data.tracePath).toBeTruthy();
    await expectZip(request, `/reports/${bad.run}/${bad.data.tracePath}`);
  });

  test('debugger: a failed run offers its trace.zip when the session ends, and opens it in the viewer', async ({ page, request }) => {
    test.setTimeout(240_000);
    await setTraceConfig(request, null);
    await upload(request, 'b9-debug.sel', SCRIPT('debug'));
    await page.goto('/app/');
    await (await fileNode(page, 'b9-debug.sel')).click();
    await page.getByRole('button', { name: /Debug/ }).click();
    await expect(page.locator('.dbg-cur')).toBeVisible({ timeout: 180_000 });
    await page.keyboard.press('F8');
    await expect(page.locator('.dbg .exec-status')).toContainText(/completed/, { timeout: 150_000 });
    const link = page.getByTestId('dbg-trace');
    await expect(link).toBeVisible();
    await expectZip(request, (await link.getAttribute('href'))!);
    await link.click();
    await expect(page.locator('.view-viewer')).toBeVisible();
  });

  test('debugger: until the debuggee really pauses, the UI says it is starting (no claimed pause)', async ({ page, request }) => {
    // sweep item: the UI showed "Paused." on INIT while the debuggee JVM + browser were still starting. The pushed frames
    // are held back here (WebSocket routed through the test, SSE fallback blocked) so the check is not a timing race.
    test.setTimeout(240_000);
    await upload(request, 'b9-init.sel', SCRIPT('init'));
    let release = false;
    const held: (string | Buffer)[] = [];
    let toPage: ((m: string | Buffer) => void) | null = null;
    await page.routeWebSocket(/\/gatfjdb/, (ws) => {
      const server = ws.connectToServer();
      toPage = (m) => ws.send(m);
      server.onMessage((m) => (release ? ws.send(m) : held.push(m)));
      ws.onMessage((m) => server.send(m));
    });
    await page.route(/action=debugStream/, (r) => (release ? r.continue() : r.abort()));
    await page.goto('/app/');
    await (await fileNode(page, 'b9-init.sel')).click();
    const init = page.waitForResponse((r) => r.url().includes('action=debug&line=0'), { timeout: 120_000 });
    await page.getByRole('button', { name: /Debug/ }).click();
    const d = await (await init).json();
    expect(d.s).toBe(true);
    expect(d.o, 'INIT reports the debuggee has not paused yet').toBe(false);
    const status = page.locator('.dbg .exec-status');
    await expect(status).toContainText('Starting the browser');
    await expect(status.locator('.spinner')).toBeVisible();
    await expect(page.locator('.dbg-cur')).toHaveCount(0);
    await page.waitForTimeout(1500);
    await expect(status).not.toContainText('Paused');
    release = true;
    for (const m of held.splice(0)) toPage!(m);
    await expect(page.locator('.dbg-cur')).toBeVisible({ timeout: 180_000 });
    await expect(status).toContainText('Paused');
    await page.getByRole('button', { name: /Stop/ }).click();
  });
});

// Δ163o verifier round: valued state assertions asserted nothing (`enabled ="false"` passed on an enabled button), and
// hoverclick under autoWait looked for a hover-revealed item before hovering. One script per case, so each case's
// pass / fail is its own subtest row.
const STATE_PAGE = `<html><head><style>#menu #ia{display:none} #menu:hover #ia{display:block}</style></head><body>
<button id="b" disabled>x</button><button id="e">y</button>
<div id="menu" style="padding:20px;background:#eee">menu A<a id="ia" href="#" onclick="var p=document.createElement('p');p.id='done-a';p.textContent='a';document.body.appendChild(p);return false">item A</a></div>
<div id="menu2" style="padding:20px;background:#ddd" onmouseover="if(!document.getElementById('ib')){var a=document.createElement('a');a.id='ib';a.href='#';a.textContent='item B';a.onclick=function(){var p=document.createElement('p');p.id='done-b';p.textContent='b';document.body.appendChild(p);return false};this.appendChild(a)}">menu B</div>
</body></html>`;

const CASES: [name: string, step: string, passes: boolean][] = [
  ['st-disabled-false', '?? id@b enabled ="false"', true],
  ['st-enabled-false', '?? id@e enabled ="false"', false],
  ['st-disabled-enabled', '?? id@b enabled', false],
  ['st-disabled-no', '?? id@b enabled ="no"', false],
  ['st-selected-true', '?? id@e selected ="true"', false],
  ['hc-css-hover', 'hoverclick id@menu id@ia\n    ?? id@done-a', true],
  ['hc-inserted', 'hoverclick id@menu2 id@ib\n    ?? id@done-b', true],
];

test.describe('Δ163o: valued state assertions and hoverclick under autoWait (needs a browser)', () => {
  test.skip(!HAS_BROWSER, 'set GATF_SELENIUM_GRID or GATF_E2E_CHROMEDRIVER');
  let saved: Record<string, unknown> | null = null;

  test.beforeAll(async ({ playwright, baseURL }) => {
    const rq = await playwright.request.newContext({ baseURL });
    saved = (await (await rq.get('/configure?configType=executor')).json()) as Record<string, unknown>;
    await rq.dispose();
  });
  test.afterAll(async ({ playwright, baseURL }) => {
    if (!saved) return;
    const rq = await playwright.request.newContext({ baseURL });
    await rq.post('/configure?configType=executor', { data: saved });
    await rq.dispose();
  });

  test('each case passes or fails as it should', async ({ request }) => {
    test.setTimeout(600_000);
    const page = path.join(fs.mkdtempSync(path.join(os.tmpdir(), 'gatf-163o-')), 'state.html');
    fs.writeFileSync(page, STATE_PAGE);
    fs.chmodSync(path.dirname(page), 0o755);
    fs.chmodSync(page, 0o644);   // the server's browser may run as another user (base runs)
    const c = (await (await request.get('/configure?configType=executor')).json()) as Record<string, unknown>;
    Object.assign(c, { traceMode: 'off', autoWait: true, actionTimeout: 5000, webFirstAssertions: true, assertTimeout: 3000 });
    expect((await request.post('/configure?configType=executor', { data: c })).ok()).toBe(true);
    for (const [name, step] of CASES) {
      await upload(request, `${name}.sel`, ['open chrome', `goto file://${page}`, '@call "x"', '', 'subtest "x"', '{', `    ${step}`, '}', ''].join('\n'));
    }
    const start = await request.put('/execute?pluginType=executor-sel', { data: CASES.map(([n]) => `${n}.sel`) });
    expect(start.ok()).toBe(true);
    await expect
      .poll(async () => JSON.stringify(await (await request.get('/execute?pluginType=executor-sel')).json()), { timeout: 540_000, intervals: [3000] })
      .not.toContain('already in progress');
    const got: string[] = [], want: string[] = [];
    for (const [name, step, passes] of CASES) {
      const { data } = await subtestRow(request, `${name}.sel`, 'x');
      got.push(`${name} (${step}): ${data.status ? 'passed' : 'failed'}`);
      want.push(`${name} (${step}): ${passes ? 'passed' : 'failed'}`);
    }
    expect(got).toEqual(want);
  });
});
