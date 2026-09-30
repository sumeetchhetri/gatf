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
// M13: plugin completions from GET /api/plugins (src/plugins.ts, bundled here with esbuild). Run: npm run test:plugins
import { build } from 'esbuild';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, dirname } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const out = mkdtempSync(join(tmpdir(), 'seleasy-plugins-'));
await build({ entryPoints: [join(here, '../src/plugins.ts')], outfile: join(out, 'plugins.mjs'), format: 'esm', platform: 'node', bundle: true, logLevel: 'silent' });
const { pluginItems, pluginsUrl, fetchPlugins } = await import(pathToFileURL(join(out, 'plugins.mjs')).href);

let pass = 0, fail = 0;
const ok = (name, cond) => { if (cond) { pass++; console.log('  ok   ' + name); } else { fail++; console.log('  FAIL ' + name); } };

const list = [
  { name: 'greet', kind: 'sdk', summary: 'Says hello', args: [{ name: 'who', required: true }, { name: 'times' }], examples: ['greet World'], source: 'acme.jar' },
  { name: 'curl', kind: 'builtin', summary: 'cURL' },
  { name: 'Bad Name', kind: 'sdk' },
  null,
];
const items = pluginItems(list, new Set(['curl']));
ok('only new, valid plugin names become items', items.length === 1 && items[0].label === 'greet');
ok('the item carries kind, insert text and docs', items[0].detail === 'plugin (sdk)' && items[0].insertText === 'greet '
  && items[0].documentation.includes('Says hello') && items[0].documentation.includes('args: who [times]')
  && items[0].documentation.includes('e.g. `greet World`') && items[0].documentation.includes('(sdk: acme.jar)'));
ok('a non-array reply gives no items', pluginItems({ error: 'x' }, new Set()).length === 0);
ok('url with and without token', pluginsUrl('http://h:9080/', 't k') === 'http://h:9080/api/plugins?token=t%20k' && pluginsUrl('', undefined) === 'http://localhost:9080/api/plugins');
ok('a server that is down gives []', (await fetchPlugins('http://x', undefined, () => Promise.reject(new Error('ECONNREFUSED')))).length === 0);
ok('a slow server times out to []', (await fetchPlugins('http://x', undefined, () => new Promise(() => {}), 50)).length === 0);
ok('a good reply is returned', (await fetchPlugins('http://x', undefined, async () => ({ ok: true, status: 200, json: async () => list }))).length === 4);

rmSync(out, { recursive: true, force: true });
console.log(pass + ' passed, ' + fail + ' failed');
process.exit(fail ? 1 : 0);
