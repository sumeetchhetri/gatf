#!/usr/bin/env node
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
// Regenerates the documentation screenshots in docs/guide/img/ (Δ191).
//
// 1. Do the getting-started steps in a scratch folder (docs/guide/getting-started): gatf-config.json,
//    tests/health.xml, ui/console.sel, then `gatf ui` there and `gatf test` + `gatf test gatf-config-ui.json`.
// 2. From the repository root:
//      GATF_URL=http://127.0.0.1:9080 GATF_TRACE=/path/to/hello-gatf/out-ui/<run>/<...>-trace.zip \
//        node tools/gen-docs/screenshots.mjs
//    Playwright is resolved from the current directory, e2e/node_modules or the global modules
//    (`npm i -D playwright` anywhere on that path). GATF_DOCS_IMG overrides the output folder.
import { createRequire } from 'node:module';
import { existsSync, mkdirSync } from 'node:fs';
import { join, resolve } from 'node:path';

const require = createRequire(import.meta.url);
function loadPlaywright() {
  const paths = [process.cwd(), resolve('e2e'), resolve('e2e/node_modules')];
  for (const name of ['playwright', '@playwright/test']) {
    try { return require(require.resolve(name, { paths })); } catch { /* next */ }
    try { return require(name); } catch { /* next */ }
  }
  throw new Error('playwright not found: run `npm i -D playwright` (or `npm ci` in e2e/)');
}

const { chromium } = loadPlaywright();
const base = (process.env.GATF_URL || 'http://127.0.0.1:9080').replace(/\/$/, '');
const out = resolve(process.env.GATF_DOCS_IMG || 'docs/guide/img');
const trace = process.env.GATF_TRACE;
mkdirSync(out, { recursive: true });

const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1280, height: 760 }, colorScheme: 'light' });
const shot = async (name) => {
  await page.waitForTimeout(800);   // let charts and lists settle
  await page.screenshot({ path: join(out, name) });
  console.log('wrote', join(out, name));
};

// Reports: the console's start page, with the API run from the getting-started guide
await page.goto(base + '/app/');
await page.getByRole('button', { name: 'Reports' }).first().waitFor();
await shot('console-reports.png');

// Test Files: the project tree
await page.getByRole('button', { name: 'Test Files' }).first().click();
await page.getByText('health.xml', { exact: true }).first().waitFor();
await shot('console-test-files.png');

// Trace Viewer with the UI run's trace
if (trace) {
  if (!existsSync(trace)) throw new Error('GATF_TRACE does not exist: ' + trace);
  await page.goto(base + '/app/');   // the Trace Viewer button is on the Reports view
  await page.getByRole('button', { name: /Trace Viewer/ }).first().click();
  await page.locator('input[type=file]').first().setInputFiles(trace);
  await page.waitForTimeout(2500);
  await shot('trace-viewer.png');
} else {
  console.log('GATF_TRACE not set: skipped trace-viewer.png');
}

await browser.close();
