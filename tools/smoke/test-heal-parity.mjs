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
// H1 (Δ149): parity between the page resolver (frontend/src/trace/semanticResolver.generated.js, generated verbatim from
// SemanticLocators' text blocks) on jsdom and the healer's Java port (com.gatf.ai.heal.SnapshotResolver), over the
// fixtures in alldep-jar/src/test/resources/heal/parity/cases.json. Writes/compares parity-expected.json, which
// SnapshotResolverParityTest (JUnit) asserts the Java side against.
//   node tools/smoke/test-heal-parity.mjs           compare (exit 1 on drift)
//   node tools/smoke/test-heal-parity.mjs --write   regenerate parity-expected.json
// Needs the frontend's dev dependencies (jsdom): `cd frontend && npm ci`.
import { readFileSync, writeFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const DIR = resolve(here, '../../alldep-jar/src/test/resources/heal/parity');
let JSDOM;
try {
  ({ JSDOM } = createRequire(resolve(here, '../../frontend/package.json'))('jsdom'));
} catch (e) {
  console.log('SKIP test-heal-parity: jsdom not installed (cd frontend && npm ci)');
  process.exit(0);
}
const { makeSemanticResolver } = await import(pathToFileURL(resolve(here, '../../frontend/src/trace/semanticResolver.generated.js')).href);

/** A browser parses <template shadowrootmode> into a shadow root; jsdom does not, so attach them the same way. */
function declarativeShadow(doc) {
  for (;;) {
    const t = doc.querySelector('template[shadowrootmode]');
    if (!t) return;
    const host = t.parentElement;
    t.remove();
    if (host && !host.shadowRoot) host.attachShadow({ mode: 'open' }).appendChild(t.content);
  }
}

const cases = JSON.parse(readFileSync(resolve(DIR, 'cases.json'), 'utf8'));
const out = {};
for (const c of cases) {
  const dom = new JSDOM(c.html);
  const { document, window } = dom.window;
  declarativeShadow(document);
  const r = makeSemanticResolver(document, window);
  const res = { specs: [], picks: {}, css: {} };
  for (const spec of c.specs) res.specs.push(r.semantic(null, spec, 'data-testid').count);
  for (const p of c.picks) {
    let el = document.querySelector('[data-pick="' + p + '"]');
    if (!el) for (const h of document.querySelectorAll('*')) if (h.shadowRoot && (el = h.shadowRoot.querySelector('[data-pick="' + p + '"]'))) break;
    if (!el) throw new Error(c.name + ': pick ' + p + ' not found');
    const info = r.semantic(null, { kind: 'info' }, 'data-testid', el);
    res.picks[p] = { info, candidates: r.candidates(el, 'data-testid'), fallback: r.fallback(el) };
  }
  for (const q of c.css) res.css[q] = document.querySelectorAll(q).length;
  out[c.name] = res;
}
const text = JSON.stringify(out, null, 1) + '\n';
const file = resolve(DIR, 'parity-expected.json');
if (process.argv.includes('--write')) {
  writeFileSync(file, text);
  console.log('wrote ' + file);
} else {
  const prev = readFileSync(file, 'utf8');
  if (prev !== text) {
    console.log('FAIL test-heal-parity: the page resolver no longer matches parity-expected.json (re-run with --write, then fix SnapshotResolver until SnapshotResolverParityTest passes)');
    process.exit(1);
  }
  console.log('ok test-heal-parity: ' + cases.length + ' fixtures');
}
