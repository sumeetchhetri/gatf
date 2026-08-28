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
// Verifies the generated TextMate grammar actually compiles under Oniguruma (the engine VS Code
// uses) and highlights key seleasy constructs correctly — including the Phase 7 rule that a `//`
// after a URL scheme is NOT a comment. Uses vscode-textmate + vscode-oniguruma (devDeps).
//   Run: npm run test:grammar   (or node test/grammar.test.mjs)
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import { createRequire } from 'node:module';

const here = dirname(fileURLToPath(import.meta.url));
const ext = join(here, '..');
const require = createRequire(import.meta.url);
const vsctm = require('vscode-textmate');
const oniguruma = require('vscode-oniguruma');

const wasmPath = join(dirname(require.resolve('vscode-oniguruma/package.json')), 'release/onig.wasm');
const wasmBin = readFileSync(wasmPath);
const vscodeOnigurumaLib = oniguruma.loadWASM(wasmBin.buffer).then(() => ({
  createOnigScanner: (patterns) => new oniguruma.OnigScanner(patterns),
  createOnigString: (s) => new oniguruma.OnigString(s),
}));

const grammarPath = join(ext, 'syntaxes/seleasy.tmLanguage.json');
const registry = new vsctm.Registry({
  onigLib: vscodeOnigurumaLib,
  loadGrammar: async (scope) =>
    scope === 'source.seleasy' ? vsctm.parseRawGrammar(readFileSync(grammarPath, 'utf8'), grammarPath) : null,
});

const grammar = await registry.loadGrammar('source.seleasy');
if (!grammar) {
  console.error('FAIL: grammar failed to load/compile under Oniguruma');
  process.exit(1);
}

let pass = 0,
  fail = 0;
// tokenize `line`, return scopes covering the first char at/after `col` of substring `needle`
function scopesFor(line, needle) {
  const r = grammar.tokenizeLine(line, vsctm.INITIAL);
  const col = line.indexOf(needle);
  const tok = r.tokens.find((t) => col >= t.startIndex && col < t.endIndex);
  return tok ? tok.scopes : [];
}
function has(name, line, needle, scopeFrag) {
  const scopes = scopesFor(line, needle);
  const ok = scopes.some((s) => s.includes(scopeFrag));
  if (ok) pass++;
  else fail++;
  console.log(`  ${ok ? 'ok  ' : 'FAIL'} ${name}  [${needle}] -> ${scopes.join(' ') || '(none)'}`);
}
function not(name, line, needle, scopeFrag) {
  const scopes = scopesFor(line, needle);
  const bad = scopes.some((s) => s.includes(scopeFrag));
  if (!bad) pass++;
  else fail++;
  console.log(`  ${!bad ? 'ok  ' : 'FAIL'} ${name}  [${needle}] should NOT be ${scopeFrag} -> ${scopes.join(' ')}`);
}

has('keyword open', 'open chrome', 'open', 'keyword');
has('atom chrome (browser target)', 'open chrome', 'chrome', 'constant.language');
has('string dq', 'subtest "doLogin" {', '"doLogin"', 'string');
has('variable ${}', 'goto ${baseUrl}', '${baseUrl}', 'variable');
has('selector id@', 'click id@save', 'id@', 'selector');
has('@call directive', '@call "doLogin"', '@call', 'function');
has('full-line comment', '// a whole line', '// a whole line', 'comment');
has('trailing comment', 'config app.props //notes', '//notes', 'comment');
has('number', 'randomize alphauc 8', '8', 'numeric');
has('multiword wait till visible', 'wait till visible id@x', 'wait till visible', 'keyword');
has('conditional ??', '?? id@el', '??', 'keyword');
// the Phase 7 guarantee: the // in a URL is NOT a comment
not('url // not a comment', 'goto https://example.com', '://example.com', 'comment');
not('url // (the slashes)', 'goto https://example.com', '//example', 'comment');

console.log(`\ngrammar: ${pass} passed, ${fail} failed`);
process.exit(fail ? 1 : 0);
