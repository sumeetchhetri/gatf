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
// H3 (Δ154) supplement: the raw launch-cost saving that browserContexts=worker relies on, measured on a real
// Chromium over CDP with no GATF/Selenium in the loop. Compares N browser launches (launch, open a tab, quit) with one
// launch plus N contexts (Target.createBrowserContext + createTarget + disposeBrowserContext), and checks that no
// context is left behind. Not a GATF run: Sumeet's 20-script / 4-worker timing (DELTA-154.md) is the real measurement.
//   node tools/smoke/worker-contexts-timing.mjs [chromePath] [N]
import { spawn } from 'node:child_process';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

const CHROME = process.argv[2] || process.env.CHROME || '/opt/google/chrome/chrome';
const N = Number(process.argv[3] || 20);

function launch() {
  const dir = mkdtempSync(join(tmpdir(), 'gatf154-'));
  const p = spawn(CHROME, ['--headless=new', '--no-sandbox', '--disable-gpu', '--remote-debugging-port=0', `--user-data-dir=${dir}`, 'about:blank'],
    { stdio: ['ignore', 'ignore', 'pipe'] });
  return new Promise((ok, fail) => {
    let err = '';
    p.stderr.on('data', d => {
      err += d;
      const m = /DevTools listening on (ws:\S+)/.exec(err);
      if (m) ok({ p, dir, ws: m[1] });
    });
    p.on('exit', c => fail(new Error('chrome exited ' + c + ': ' + err.slice(0, 300))));
  });
}

function cdp(url) {
  const ws = new WebSocket(url);
  let id = 0;
  const waits = new Map();
  ws.onmessage = e => {
    const m = JSON.parse(e.data);
    if (m.id && waits.has(m.id)) { const w = waits.get(m.id); waits.delete(m.id); m.error ? w[1](new Error(m.error.message)) : w[0](m.result); }
  };
  const open = new Promise(ok => { ws.onopen = ok; });
  return {
    open, close: () => ws.close(),
    send: (method, params = {}) => new Promise((ok, fail) => { const i = ++id; waits.set(i, [ok, fail]); ws.send(JSON.stringify({ id: i, method, params })); }),
  };
}

async function quit(b, c) {
  try { await c.send('Browser.close'); } catch { /* closing */ }
  c.close();
  await new Promise(ok => { if (b.p.exitCode !== null) ok(); else b.p.on('exit', ok); setTimeout(ok, 5000); });
  rmSync(b.dir, { recursive: true, force: true });
}

const ms = t => Number(process.hrtime.bigint() - t) / 1e6;

let t = process.hrtime.bigint();
for (let i = 0; i < N; i++) {
  const b = await launch();
  const c = cdp(b.ws); await c.open;
  await c.send('Target.createTarget', { url: 'about:blank' });
  await quit(b, c);
}
const perBrowser = ms(t);

t = process.hrtime.bigint();
const b = await launch();
const c = cdp(b.ws); await c.open;
for (let i = 0; i < N; i++) {
  const { browserContextId } = await c.send('Target.createBrowserContext');
  await c.send('Target.createTarget', { url: 'about:blank', browserContextId });
  await c.send('Target.disposeBrowserContext', { browserContextId });
}
const left = (await c.send('Target.getBrowserContexts')).browserContextIds.length;
const perContext = ms(t);
await quit(b, c);

console.log(JSON.stringify({ chrome: CHROME, n: N, browserPerTestMs: Math.round(perBrowser), contextPerTestMs: Math.round(perContext),
  launches: { browserPerTest: N, contexts: 1 }, speedup: +(perBrowser / perContext).toFixed(1), contextsLeft: left }));
if (left !== 0) process.exit(1);
