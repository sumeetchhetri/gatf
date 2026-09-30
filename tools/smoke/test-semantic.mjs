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
// M3 semantic locators: runs the injected resolver (SemanticLocators.RESOLVER_JS, read straight out of the Java text
// block) against a jsdom DOM, covering every prefix, value mode, strictness data and open shadow roots.
// Needs the frontend's dev dependencies (jsdom): `cd frontend && npm ci`. Run: node tools/smoke/test-semantic.mjs
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const javaSrc = readFileSync(resolve(here, '../../alldep-jar/src/main/java/com/gatf/selenium/SemanticLocators.java'), 'utf8');
let JSDOM;
try {
  ({ JSDOM } = createRequire(resolve(here, '../../frontend/package.json'))('jsdom'));
} catch (e) {
  console.log('SKIP test-semantic: jsdom not installed (cd frontend && npm ci)');
  process.exit(0);
}

/** Java text-block semantics for this file: strip the common indentation, then unescape \\ -> \. */
function textBlock(src, name) {
  const m = src.match(new RegExp(name + '\\s*=\\s*"""\\r?\\n([\\s\\S]*?)"""'));
  if (!m) throw new Error('text block ' + name + ' not found');
  const lines = m[1].replace(/\r/g, '').split('\n');
  const closing = lines[lines.length - 1];
  const ind = Math.min(closing.length, ...lines.filter((l) => l.trim()).map((l) => l.match(/^ */)[0].length));
  return lines.map((l) => l.slice(ind)).join('\n').replace(/\\\\/g, '\\');
}
const resolver = new Function(textBlock(javaSrc, 'RESOLVER_JS'));

const dom = new JSDOM(`<!doctype html><html><body>
  <header><nav aria-label="Main"><a href="/home">Home</a><a>not a link</a></nav></header>
  <main>
    <h1>Welcome back</h1>
    <h2 id="sub">Orders <small>(3)</small></h2>
    <form>
      <label for="email">Email address</label><input id="email" type="email" placeholder="you@example.com">
      <label>Password <input id="pw" type="password"></label>
      <span id="lbl-q">Search</span><input id="q" type="search" aria-labelledby="lbl-q">
      <input id="age" type="number" aria-label="Age">
      <input type="checkbox" id="tos" checked><label for="tos">Accept terms</label>
      <select id="country" title="Country"><option>India</option><option selected>Nepal</option></select>
      <button type="submit" data-testid="save-btn" class="btn primary">Save</button>
      <button type="button" data-qa="cancel">Cancel</button>
      <button type="button" hidden>Save</button>
      <button type="button" aria-pressed="true">Bold</button>
      <input type="submit" value="Save draft">
    </form>
    <img src="a.png" alt="Company logo"><img src="b.png" alt="">
    <p>Total: <b>42</b> items</p>
    <p>Saved   at noon</p>
    <div role="dialog" aria-labelledby="dlg-t"><h3 id="dlg-t">Confirm</h3><button>OK</button></div>
    <div style="display:none"><button>Ghost</button></div>
    <span title="Close panel">x</span>
    <x-card id="card"></x-card>
  </main></body></html>`);
const { document, window } = dom.window;
const card = document.getElementById('card');
const sr = card.attachShadow({ mode: 'open' });
sr.innerHTML = '<label for="inner">Coupon</label><input id="inner"><button>Apply</button><p>In shadow text</p>';
globalThis.window = window;
globalThis.document = document;

let pass = 0, fail = 0;
function q(spec, root = null, tid = 'data-testid') {
  return resolver(root, spec, tid);
}
function eq(name, got, want) {
  const ok = JSON.stringify(got) === JSON.stringify(want);
  if (ok) pass++; else fail++;
  console.log((ok ? '  ok   ' : '  FAIL ') + name + (ok ? '' : '\n        got  ' + JSON.stringify(got) + '\n        want ' + JSON.stringify(want)));
}
const ids = (r) => r.els.map((e) => e.id || e.localName + ':' + (e.textContent || e.getAttribute('alt') || e.value || '').trim());

// role
eq('role button full name (case-insensitive)', ids(q({ kind: 'role', role: 'button', mode: 'full', value: 'save' })), ['button:Save']);
eq('hidden button excluded; include-hidden finds it', q({ kind: 'role', role: 'button', mode: 'full', value: 'Save', includeHidden: true }).count, 2);
eq('display:none ancestor hides', q({ kind: 'role', role: 'button', mode: 'full', value: 'Ghost' }).count, 0);
eq('input[type=submit] name from value', ids(q({ kind: 'role', role: 'button', mode: 'contains', value: 'draft' })), ['input:Save draft']);
eq('role regex name', q({ kind: 'role', role: 'button', mode: 'regex', value: '^(ok|apply)$', flags: 'i' }).count, 2);
eq('heading level', ids(q({ kind: 'role', role: 'heading', level: 2 })), ['sub']);
eq('heading name ignores nothing visible', ids(q({ kind: 'role', role: 'heading', mode: 'full', value: 'Orders (3)' })), ['sub']);
eq('link needs href', q({ kind: 'role', role: 'link' }).count, 1);
eq('textbox by label[for]', ids(q({ kind: 'role', role: 'textbox', mode: 'full', value: 'Email address' })), ['email']);
eq('searchbox by aria-labelledby', ids(q({ kind: 'role', role: 'searchbox', mode: 'full', value: 'Search' })), ['q']);
eq('spinbutton by aria-label', ids(q({ kind: 'role', role: 'spinbutton', mode: 'exact', value: 'Age' })), ['age']);
eq('checkbox checked state', ids(q({ kind: 'role', role: 'checkbox', checked: true })), ['tos']);
eq('checkbox unchecked filter', q({ kind: 'role', role: 'checkbox', checked: false }).count, 0);
eq('pressed state', q({ kind: 'role', role: 'button', pressed: true }).count, 1);
eq('combobox by title', ids(q({ kind: 'role', role: 'combobox', mode: 'full', value: 'Country' })), ['country']);
eq('img alt="" is presentation', q({ kind: 'role', role: 'img' }).count, 1);
eq('navigation by aria-label', q({ kind: 'role', role: 'navigation', mode: 'full', value: 'main' }).count, 1);
eq('dialog named by labelledby', q({ kind: 'role', role: 'dialog', mode: 'full', value: 'Confirm' }).count, 1);
eq('banner = top-level header', q({ kind: 'role', role: 'banner' }).count, 1);
// other prefixes
eq('label@ full', ids(q({ kind: 'label', mode: 'full', value: 'email address' })), ['email']);
eq('label@ wrapping label', ids(q({ kind: 'label', mode: 'contains', value: 'pass' })), ['pw']);
eq('label@ aria-label', ids(q({ kind: 'label', mode: 'full', value: 'Age' })), ['age']);
eq('placeholder@', ids(q({ kind: 'placeholder', mode: 'full', value: 'you@example.com' })), ['email']);
eq('testid@ default attribute', q({ kind: 'testid', mode: 'exact', value: 'save-btn' }).count, 1);
eq('testid@ custom attribute', q({ kind: 'testid', mode: 'exact', value: 'cancel' }, null, 'data-qa').count, 1);
eq('testid@ is exact', q({ kind: 'testid', mode: 'exact', value: 'SAVE-BTN' }).count, 0);
eq('alt@', q({ kind: 'alt', mode: 'contains', value: 'logo' }).count, 1);
eq('title@', ids(q({ kind: 'title', mode: 'full', value: 'Close panel' })), ['span:x']);
// text exact / regex: innermost element, whitespace collapsed
eq('text exact collapses whitespace', ids(q({ kind: 'text', mode: 'exact', value: 'Saved at noon' })), ['p:Saved   at noon']);
eq('text exact is case-sensitive', q({ kind: 'text', mode: 'exact', value: 'saved at noon' }).count, 0);
eq('text regex innermost', ids(q({ kind: 'text', mode: 'regex', value: '^42$' })), ['b:42']);
eq('text exact on submit input value', q({ kind: 'text', mode: 'exact', value: 'Save draft' }).count, 1);
// shadow DOM piercing
eq('shadow: role button', ids(q({ kind: 'role', role: 'button', mode: 'full', value: 'Apply' })), ['button:Apply']);
eq('shadow: label[for] inside shadow root', ids(q({ kind: 'label', mode: 'full', value: 'Coupon' })), ['inner']);
eq('shadow: text', q({ kind: 'text', mode: 'regex', value: 'in shadow', flags: 'i' }).count, 1);
// scoping + strict data
eq('scoped to a root element', q({ kind: 'role', role: 'button' }, document.querySelector('[role=dialog]')).count, 1);
const many = q({ kind: 'role', role: 'button' });
eq('candidates described for strict mode', many.desc[0], '<button.btn.primary> "Save"');
eq('count reports every visible match', many.count, 6);

// ---- label edge cases (a fresh page): wrapping labels label their control only; a label's text excludes controls ----
{
  const d2 = new JSDOM(`<!doctype html><html><body>
    <label><span>Email</span><input id="w-email"></label>
    <label><input type="checkbox" id="acc"> I accept the <a href="/terms">terms</a></label>
    <label><span>Config</span><select id="cfg"><option>Default</option><option>prod</option></select></label>
    <button>Plain</button><button aria-expanded="false">Menu</button>
  </body></html>`, { runScripts: 'outside-only' });
  globalThis.window = d2.window;
  globalThis.document = d2.window.document;
  eq('wrapping label: only the control is labelled', ids(q({ kind: 'label', mode: 'full', value: 'Email' })), ['w-email']);
  eq('link inside a label keeps its own name', ids(q({ kind: 'role', role: 'link', mode: 'full', value: 'terms' })), ['link:terms'.replace('link', 'a')]);
  eq('checkbox named by its wrapping label', ids(q({ kind: 'role', role: 'checkbox', mode: 'contains', value: 'I accept' })), ['acc']);
  eq('label text excludes the wrapped select', ids(q({ kind: 'label', mode: 'full', value: 'Config' })), ['cfg']);
  eq('combobox named by label without its options', ids(q({ kind: 'role', role: 'combobox', mode: 'full', value: 'Config' })), ['cfg']);
  eq('[expanded=false] needs aria-expanded', ids(q({ kind: 'role', role: 'button', expanded: false })), ['button:Menu']);
  globalThis.window = window;
  globalThis.document = document;
}

console.log(`${pass} passed, ${fail} failed`);
process.exit(fail ? 1 : 0);
