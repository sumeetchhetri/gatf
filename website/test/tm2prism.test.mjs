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
import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { tmToPrism, toRegExp, lineLocal, prismType, emitModule } from '../scripts/tm2prism.mjs';

const TM = JSON.parse(fs.readFileSync(new URL('../../vscode-extension/syntaxes/seleasy.tmLanguage.json', import.meta.url), 'utf8'));

// Minimal Prism-style tokenizer for tests: first token (in grammar order) that matches at the earliest position wins.
function firstToken(grammar, text) {
  let best = null;
  for (const [name, def] of Object.entries(grammar)) {
    const m = new RegExp(def.pattern.source, def.pattern.flags).exec(text);
    if (m && (!best || m.index < best.index)) best = { name, alias: def.alias, index: m.index, text: m[0] };
  }
  return best;
}

test('every seleasy rule converts and compiles, in the grammar order', () => {
  const g = tmToPrism(TM);
  const keys = Object.keys(g);
  assert.ok(keys.length >= 15, 'tokens: ' + keys.length);
  assert.equal(keys[0], 'comments-0');
  const order = TM.patterns.map((p) => p.include.slice(1));
  const seen = [...new Set(keys.map((k) => k.replace(/-\d+$/, '')))];
  assert.deepEqual(seen, order.filter((o) => seen.includes(o)));
  for (const def of Object.values(g)) assert.ok(def.pattern instanceof RegExp && def.pattern.flags.includes('m'));
});

test('seleasy tokens get the colours the editor gives them', () => {
  const g = tmToPrism(TM);
  const t = (s) => firstToken(g, s);
  assert.equal(t('// note').alias, 'comment');
  assert.equal(t('click id@save').alias, 'keyword');
  assert.deepEqual([t('id@save').alias, t('id@save').text], ['tag', 'id@']);
  assert.equal(t('"a \\" b"').text, '"a \\" b"', 'escaped quote does not end the string');
  assert.equal(t('${user}').alias, 'variable');
  assert.equal(t('open chrome').alias, 'keyword');
  assert.equal(t('42').alias, 'number');
});

test('a block comment or code block spans lines; a string never runs past a newline inside a comment', () => {
  const g = tmToPrism(TM);
  const block = '/* one\n "two" \n*/ x';
  const m = g['comments-1'].pattern.exec(block);
  assert.equal(m[0], '/* one\n "two" \n*/ x');
  const apostrophe = "/* don't\n*/\nclick id@a 'b'";
  assert.equal(g['comments-1'].pattern.exec(apostrophe)[0], "/* don't\n*/");
  const code = '<<<(java)\nint a = 1;\n>>>\nclick id@x';
  assert.equal(g['codeblocks-0'].pattern.exec(code)[0], '<<<(java)\nint a = 1;\n>>>');
});

test('lineLocal keeps negated classes on one line, is idempotent and skips escaped brackets', () => {
  assert.equal(lineLocal('[^"]'), '[^\\n"]');
  assert.equal(lineLocal(lineLocal('[^"]')), '[^\\n"]');
  assert.equal(lineLocal('\\[^x]'), '\\[^x]');
  assert.equal(lineLocal('\\\\[^x]'), '\\\\[^\\nx]');
});

test('Oniguruma-only syntax and bad grammars fail loudly', () => {
  assert.throws(() => toRegExp('\\Afoo', 'r'), /unsupported/);
  assert.throws(() => toRegExp('(?>a)', 'r'), /unsupported/);
  assert.throws(() => toRegExp('a++', 'r'), /unsupported/);
  assert.throws(() => toRegExp('(', 'r'), /tm2prism: r/);
  assert.throws(() => tmToPrism({ patterns: [{ include: '#nope' }], repository: {} }), /missing repository/);
  assert.throws(() => tmToPrism({ patterns: [{ match: 'x' }] }), /#includes/);
});

test('scope mapping and emitted module round-trip', async () => {
  assert.equal(prismType('keyword.operator.seleasy'), 'operator');
  assert.equal(prismType('keyword.control.seleasy'), 'keyword');
  assert.equal(prismType('meta.unknown'), null);
  const g = tmToPrism(TM);
  const src = emitModule(g, 'x.json', '/* h */\n');
  const mod = await import('data:text/javascript,' + encodeURIComponent(src));
  assert.deepEqual(Object.keys(mod.default), Object.keys(g));
  for (const k of Object.keys(g)) assert.equal(mod.default[k].pattern.source, g[k].pattern.source);
});
