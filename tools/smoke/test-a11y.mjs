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
// M10 a11y check: runs A11yReport.RUN_JS / CONFIGURE_JS (read out of the Java source) with the bundled axe-core
// (alldep-jar/src/main/resources/gatf-axe/axe.min.js) inside jsdom, as executeAsyncScript would in the browser.
// Run: node tools/smoke/test-a11y.mjs   (needs the frontend dev deps for jsdom)
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';

const here = dirname(fileURLToPath(import.meta.url));
const root = resolve(here, '../..');
const require = createRequire(resolve(root, 'frontend/package.json'));
const { JSDOM, VirtualConsole } = require('jsdom');
const javaSrc = readFileSync(resolve(root, 'alldep-jar/src/main/java/com/gatf/selenium/checks/A11yReport.java'), 'utf8');
const axe = readFileSync(resolve(root, 'alldep-jar/src/main/resources/gatf-axe/axe.min.js'), 'utf8');

/** The string value of `NAME = ...;` in the Java source: one literal, or String.join(sep, literals...). */
function javaConst(name) {
  const m = javaSrc.match(new RegExp('\\b' + name + '\\s*=\\s*([\\s\\S]*?);\\s*\\n'));
  if (!m) throw new Error('constant ' + name + ' not found');
  const lits = [...m[1].matchAll(/"((?:[^"\\]|\\.)*)"/g)].map((x) => JSON.parse('"' + x[1] + '"'));
  return /^String\.join\(/.test(m[1].trim()) ? lits.slice(1).join(lits[0]) : lits.join('');
}
const RUN = javaConst('RUN_JS');
const CONFIGURE = javaConst('CONFIGURE_JS');

let pass = 0, fail = 0;
const ok = (name, cond) => { if (cond) { pass++; console.log('  ok   ' + name); } else { fail++; console.log('  FAIL ' + name); } };
const quiet = new VirtualConsole();   // jsdom has no canvas: axe's colour-contrast probe logs noise

async function run(html, inc, exc, withAxe = true) {
  const dom = new JSDOM(html, { runScripts: 'outside-only', url: 'https://shop.test/', virtualConsole: quiet });
  if (withAxe) { dom.window.eval(axe); dom.window.eval(CONFIGURE); }
  const out = await new Promise((done) => new dom.window.Function(RUN)(inc, exc, done));
  return JSON.parse(out);
}

const page = '<!doctype html><html lang="en"><head><title>Shop</title></head><body><main><h1>Shop</h1>'
  + '<img src="logo.png"><button id="buy"></button><div class="ad"><img src="ad.png"></div></main></body></html>';

const all = await run(page, [], []);
const ids = all.violations.map((v) => v.id);
ok('violations found with selectors', ids.includes('image-alt') && ids.includes('button-name')
  && all.violations.find((v) => v.id === 'button-name').nodes[0].target[0] === '#buy');
ok('url and counts come back', all.url === 'https://shop.test/' && typeof all.passes === 'number');
const noAd = await run(page, [], ['.ad']);
ok('exclude scopes the run', noAd.violations.find((v) => v.id === 'image-alt').nodes.length === 1);
const onlyAd = await run(page, ['.ad'], []);
ok('include scopes the run', onlyAd.violations.every((v) => v.nodes.every((n) => String(n.target).includes('ad'))));
const none = await run(page, [], [], false);
ok('missing axe is an error, not a hang', none.error === 'axe-core is not loaded');
const bad = await run(page, ['#nothing-here'], []);
ok('an include matching nothing reports an error', typeof bad.error === 'string');

console.log(pass + ' passed, ' + fail + ' failed');
process.exit(fail ? 1 : 0);
