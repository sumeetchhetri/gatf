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
// M1 auto-wait: runs the injected actionability probe (Actionability.PROBE_JS, read straight out of the Java
// text block) against a mocked DOM. No browser, no dependencies. Run: node tools/smoke/test-actionability.mjs
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const javaSrc = readFileSync(resolve(here, '../../alldep-jar/src/main/java/com/gatf/selenium/Actionability.java'), 'utf8');

/** Java text-block semantics for this file: strip the common indentation, then unescape \\ -> \. */
function textBlock(src, name) {
  const m = src.match(new RegExp(name + '\\s*=\\s*"""\\r?\\n([\\s\\S]*?)"""'));
  if (!m) throw new Error('text block ' + name + ' not found');
  const lines = m[1].replace(/\r/g, '').split('\n');
  const closing = lines[lines.length - 1];               // indentation before the closing """
  const ind = Math.min(closing.length, ...lines.filter((l) => l.trim()).map((l) => l.match(/^ */)[0].length));
  return lines.map((l) => l.slice(ind)).join('\n').replace(/\\\\/g, '\\');
}
const PROBE = textBlock(javaSrc, 'PROBE_JS');
const probeFn = new Function(PROBE);

// ---- mock DOM --------------------------------------------------------------------------------------------
function el(tag, o = {}) {
  const e = {
    tagName: tag.toUpperCase(), id: o.id || '', className: o.cls || '', isConnected: o.connected !== false,
    parentNode: o.parent || null, disabled: !!o.disabled, readOnly: !!o.readOnly, type: o.type,
    isContentEditable: !!o.editable, control: o.control, shadowRoot: o.shadowRoot || null, host: o.host,
    style: { display: o.display || 'block', visibility: o.visibility || 'visible' },
    attrs: o.attrs || {}, rect: o.rect || { left: 10, top: 10, width: 100, height: 30 }, scrolled: 0,
    getAttribute(n) { return n in this.attrs ? this.attrs[n] : null; },
    getBoundingClientRect() { return typeof this.rect === 'function' ? this.rect() : this.rect; },
    matches(sel) { return sel === ':disabled' ? !!(this.disabled || o.fieldsetDisabled) : false; },
    scrollIntoView() { this.scrolled++; if (o.onScroll) o.onScroll(this); },
  };
  return e;
}
let hitAt = () => null;
globalThis.window = {
  innerWidth: 1000, innerHeight: 800,
  getComputedStyle: (n) => n.style,
  requestAnimationFrame: (cb) => setTimeout(cb, 1),
};
globalThis.document = { elementFromPoint: (x, y) => hitAt(x, y) };

function probe(e, mode) {
  return new Promise((res, rej) => {
    const t = setTimeout(() => rej(new Error('probe never called back')), 2000);
    probeFn(e, mode, (v) => { clearTimeout(t); res(v); });
  });
}

// ---- cases -----------------------------------------------------------------------------------------------
const cases = [];
const test = (name, fn) => cases.push([name, fn]);
const eq = (a, b) => { if (a !== b) throw new Error(`expected ${JSON.stringify(b)} got ${JSON.stringify(a)}`); };

test('click on a visible, stable, uncovered button', async () => {
  const b = el('button'); hitAt = () => b; eq(await probe(b, 'click'), null);
});
test('click on a covered element names the blocker', async () => {
  const b = el('button', { id: 'save' }); const modal = el('div', { cls: 'modal  open fade' });
  hitAt = () => modal; eq(await probe(b, 'click'), 'covered by <div.modal.open>');
});
test('blocker description uses #id', async () => {
  const b = el('button'); const ov = el('div', { id: 'overlay' }); hitAt = () => ov;
  eq(await probe(b, 'click'), 'covered by <div#overlay>');
});
test('hit on a child of the element is fine', async () => {
  const b = el('button'); const span = el('span', { parent: b }); hitAt = () => span; eq(await probe(b, 'click'), null);
});
test('hit on a <label> for the input is fine', async () => {
  const cb = el('input', { type: 'checkbox' }); const lab = el('label', { control: cb }); hitAt = () => lab;
  eq(await probe(cb, 'click'), null);
});
test('shadow DOM is pierced', async () => {
  const inner = el('button'); const root = { elementFromPoint: () => inner };
  const host = el('my-widget', { shadowRoot: root }); hitAt = () => host; eq(await probe(inner, 'click'), null);
});
test('element inside a shadow root walks up through host', async () => {
  const host = el('my-widget'); const target = el('div', { parent: host });
  const deep = el('span', { host: target }); hitAt = () => deep; eq(await probe(target, 'click'), null);
});
test('display:none is not visible', async () => {
  eq(await probe(el('button', { display: 'none' }), 'click'), 'not visible');
});
test('visibility:hidden is not visible', async () => {
  eq(await probe(el('button', { visibility: 'hidden' }), 'type'), 'not visible');
});
test('zero-size box is not visible', async () => {
  eq(await probe(el('button', { rect: { left: 0, top: 0, width: 0, height: 0 } }), 'click'), 'not visible');
});
test('disabled button', async () => { eq(await probe(el('button', { disabled: true }), 'click'), 'disabled'); });
test('input in a disabled fieldset', async () => {
  eq(await probe(el('input', { fieldsetDisabled: true }), 'type'), 'disabled');
});
test('aria-disabled div', async () => {
  eq(await probe(el('div', { attrs: { 'aria-disabled': 'true' } }), 'click'), 'disabled');
});
test('hover ignores disabled', async () => {
  const b = el('button', { disabled: true }); hitAt = () => b; eq(await probe(b, 'hover'), null);
});
test('type into a text input', async () => { eq(await probe(el('input', { type: 'text' }), 'type'), null); });
test('type into a readonly input', async () => {
  eq(await probe(el('input', { type: 'text', readOnly: true }), 'type'), 'not editable');
});
test('type (keys) into a checkbox / range input is fine; clear needs a text control', async () => {
  eq(await probe(el('input', { type: 'checkbox' }), 'type'), null);
  eq(await probe(el('input', { type: 'range' }), 'type'), null);
  eq(await probe(el('input', { type: 'checkbox' }), 'clear'), 'not editable');
});
test('type into a readonly textarea is not editable; clear too', async () => {
  eq(await probe(el('textarea', { readOnly: true }), 'type'), 'not editable');
  eq(await probe(el('input', { type: 'email', readOnly: true }), 'clear'), 'not editable');
});
test('clear on text input, textarea, contenteditable', async () => {
  eq(await probe(el('input', { type: 'text' }), 'clear'), null);
  eq(await probe(el('textarea'), 'clear'), null);
  eq(await probe(el('div', { editable: true }), 'clear'), null);
});
test('type into textarea and contenteditable', async () => {
  eq(await probe(el('textarea'), 'type'), null);
  eq(await probe(el('div', { editable: true }), 'type'), null);
});
test('type into a <select> (Selenium picks the option)', async () => { eq(await probe(el('select'), 'type'), null); });
test('closed shadow root: hit on the host counts', async () => {
  const host = el('my-comp'); const root = { host, parentNode: null }; const inner = el('button', { parent: root });
  hitAt = () => host; eq(await probe(inner, 'click'), null);
});
test('a light-DOM ancestor on top is still a blocker', async () => {
  const wrap = el('div', { cls: 'wrap' }); const b = el('button', { parent: wrap }); hitAt = () => wrap;
  eq(await probe(b, 'click'), 'covered by <div.wrap>');
});
test('SVG className object does not crash the description', async () => {
  const b = el('button'); const p = el('path'); p.className = { baseVal: 'x' }; hitAt = () => p;
  eq(await probe(b, 'click'), 'covered by <path>');
});
test('type into a div[tabindex] widget is fine; clear on it is not editable', async () => {
  const w = el('div', { attrs: { tabindex: '0' } });
  eq(await probe(w, 'type'), null); eq(await probe(w, 'clear'), 'not editable');
});
test('clear on a disabled input', async () => { eq(await probe(el('input', { disabled: true }), 'clear'), 'disabled'); });
test('type does not hit-test (covered input still types)', async () => {
  const i = el('input'); hitAt = () => el('div', { cls: 'mask' }); eq(await probe(i, 'type'), null);
});
test('randomize/chord (input mode) does not need editable', async () => {
  eq(await probe(el('select'), 'input'), null);
});
test('detached element', async () => {
  eq(await probe(el('button', { connected: false }), 'click'), 'not attached to the DOM');
});
test('moving element is not stable', async () => {
  let x = 10; const b = el('button', { rect: () => ({ left: (x += 7), top: 10, width: 100, height: 30 }) });
  hitAt = () => b; eq(await probe(b, 'click'), 'not stable (moving or animating)');
});
test('off-screen element is scrolled into view first', async () => {
  const b = el('button', { rect: { left: 10, top: 2000, width: 100, height: 30 },
    onScroll: (e) => { e.rect = { left: 10, top: 300, width: 100, height: 30 }; } });
  hitAt = () => b; eq(await probe(b, 'click'), null); eq(b.scrolled, 1);
});
test('element that cannot be scrolled into view', async () => {
  const b = el('button', { rect: { left: 10, top: 2000, width: 100, height: 30 } }); hitAt = () => b;
  eq(await probe(b, 'click'), 'outside of the viewport');
});
test('element clipped by a scrolling ancestor is scrolled into view, then passes', async () => {
  const list = el('div', { cls: 'list' });
  const b = el('li', { parent: list, rect: { left: 10, top: 300, width: 100, height: 30 },
    onScroll: (e) => { e.rect = { left: 10, top: 120, width: 100, height: 30 }; } });
  hitAt = () => (b.scrolled ? b : list);
  eq(await probe(b, 'click'), null); eq(b.scrolled, 1);
});
test('genuinely covered element: one scroll attempt, still covered', async () => {
  const b = el('button'); const modal = el('div', { cls: 'modal' }); hitAt = () => modal;
  eq(await probe(b, 'click'), 'covered by <div.modal>'); eq(b.scrolled, 1);
});
test('uncovered in-view element is not scrolled', async () => {
  const b = el('button'); hitAt = () => b; eq(await probe(b, 'click'), null); eq(b.scrolled, 0);
});
test('upload into a hidden file input is fine', async () => {
  eq(await probe(el('input', { type: 'file', display: 'none' }), 'upload'), null);
});
test('option elements skip box checks', async () => {
  eq(await probe(el('option', { rect: { left: 0, top: 0, width: 0, height: 0 } }), 'click'), null);
});
test('no requestAnimationFrame (background tab) still calls back', async () => {
  const raf = window.requestAnimationFrame; delete window.requestAnimationFrame;
  try { const b = el('button'); hitAt = () => b; eq(await probe(b, 'click'), null); }
  finally { window.requestAnimationFrame = raf; }
});
test('nothing at the point', async () => { const b = el('button'); hitAt = () => null; eq(await probe(b, 'click'), 'not hit at its center point'); });
test('a throwing DOM fails open (legacy behaviour)', async () => {
  const b = el('button'); b.getBoundingClientRect = () => { throw new Error('boom'); };
  eq(await probe(b, 'click'), null);
});

let failed = 0;
for (const [name, fn] of cases) {
  try { await fn(); console.log('PASS', name); } catch (e) { failed++; console.log('FAIL', name, '-', e.message); }
}
console.log(`${cases.length - failed}/${cases.length} actionability probe cases passed`);
process.exit(failed ? 1 : 0);
