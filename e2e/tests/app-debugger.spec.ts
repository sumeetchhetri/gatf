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
import { fileNode, HAS_BROWSER } from './helpers';

// G5 — online debugger end-to-end: breakpoint add/REMOVE (removal was broken server-side before delta-075),
// initial pause + current-line highlight, F6 step-over advances, F8 continue runs to the breakpoint /
// completion, Stop tears the session down. Plus a protocol check that the /gatfjdb WebSocket pushes
// frames for a live session. Needs the server-side selenium toolchain → Grid-gated like the T4c specs.
const NEEDS_GRID = !HAS_BROWSER; // Δ163n: or a local chromedriver (GATF_E2E_CHROMEDRIVER)

// Debug needs a RUNNABLE script (one with its own `open`): library files like ui/lib/auth.sel only
// define subtests and fail with "No open command found". sample-project's trace-smoke.sel is standalone.
const DEBUG_SCRIPT = process.env.GATF_E2E_DEBUG_SEL || 'trace-smoke.sel';

async function openSel(page: Page, name: string) {
  await (await fileNode(page, name)).click();
  await expect(page.locator('.cm-content')).toBeVisible();
}

const curLineNo = async (page: Page) => Number(await page.locator('.dbg-cur .dbg-ln').innerText());

test.describe('GATF /app online debugger (Selenium Grid)', () => {
  test.skip(NEEDS_GRID, 'set GATF_SELENIUM_GRID to a Selenium Grid endpoint to run these');

  test('breakpoint toggle, pause, F6 step-over, F8 continue, stop', async ({ page }) => {
    test.setTimeout(240_000);
    await page.goto('/app/');
    await openSel(page, DEBUG_SCRIPT);
    await page.getByRole('button', { name: /Debug/ }).click();

    // initial pause: a highlighted current line
    await expect(page.locator('.dbg-cur')).toBeVisible({ timeout: 180_000 });
    const start = await curLineNo(page);

    // pick a debuggable line after the current one
    const able = page.locator('.dbg-line', { has: page.locator('.dbg-bp-able') });
    let target = -1;
    for (let i = 0, n = await able.count(); i < n; i++) {
      const ln = Number(await able.nth(i).locator('.dbg-ln').innerText());
      if (ln > start + 1) {
        target = ln;
        break;
      }
    }
    test.skip(target < 0, 'script has no debuggable line after the first step');
    const gutter = page.locator('.dbg-line', { has: page.locator(`.dbg-ln:text-is("${target}")`) }).locator('.dbg-bp');

    // add → remove (the fixed bug) → add again
    await gutter.click();
    await expect(gutter).toHaveClass(/dbg-bp-on/);
    await gutter.click();
    await expect(gutter).not.toHaveClass(/dbg-bp-on/);
    await expect(page.locator('.dbg .exec-status')).not.toContainText(/No breakpoint found/);
    await gutter.click();
    await expect(gutter).toHaveClass(/dbg-bp-on/);

    // F6 step-over advances the highlighted line
    await page.keyboard.press('F6');
    await expect.poll(() => curLineNo(page), { timeout: 60_000 }).toBeGreaterThan(start);

    // F8 continue → pauses on the breakpoint, or completes if it ran past it
    await page.keyboard.press('F8');
    await expect
      .poll(
        async () =>
          (await page.locator('.dbg .exec-status').innerText()).includes('completed') ||
          ((await page.locator('.dbg-cur').count()) > 0 && (await curLineNo(page)) === target),
        { timeout: 90_000 },
      )
      .toBe(true);

    await page.getByRole('button', { name: /Stop/ }).click();
    await expect(page.locator('.dbg .exec-status')).toContainText(/Stopped|completed/);
  });

  test('the /gatfjdb WebSocket pushes frames for a live session', async ({ page }) => {
    test.setTimeout(240_000);
    await page.goto('/app/');
    await openSel(page, DEBUG_SCRIPT);
    // capture the sessionId the SPA mints for the debug init PUT
    const initReq = page.waitForRequest((r) => r.url().includes('action=debug') && /[?&]line=0(&|$)/.test(r.url()));
    await page.getByRole('button', { name: /Debug/ }).click();
    const sessionId = new URL((await initReq).url()).searchParams.get('sessionId');
    expect(sessionId).toBeTruthy();
    await expect(page.locator('.dbg-cur')).toBeVisible({ timeout: 180_000 });

    // an independent socket for the same session receives a {s,r,p,...} frame
    const frame = await page.evaluate(
      (sid) =>
        new Promise<string>((resolve, reject) => {
          const ws = new WebSocket((location.protocol === 'https:' ? 'wss:' : 'ws:') + '//' + location.host + '/gatfjdb');
          const t = setTimeout(() => reject(new Error('no frame within 15s')), 15_000);
          ws.onopen = () => ws.send(sid as string);
          ws.onmessage = (e) => {
            clearTimeout(t);
            ws.close();
            resolve(String(e.data));
          };
          ws.onerror = () => reject(new Error('ws error'));
        }),
      sessionId,
    );
    const f = JSON.parse(frame);
    expect(f.s).toBe(true);
    expect(typeof f.p).toBe('number');

    await page.getByRole('button', { name: /Stop/ }).click();
  });
});
