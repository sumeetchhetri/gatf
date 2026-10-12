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
// Δ148: what the pick locator pastes. The shared escape vectors (alldep-jar/src/test/resources/pick-locator/, pinned
// by PickLocatorTest to exactly what the server builds, and validated by PickLocatorValidationTest through
// validateSel / FindCommand) are inserted verbatim and must tokenize cleanly with the editor's seleasy tokenizer.
import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { Text } from '@codemirror/state';
import { StringStream } from '@codemirror/language';
import { seleasyStreamParser, type SelState } from './seleasyLang';
import { lineInsertion } from './pastePick';

interface Vector { id: string; locator: string | null; line: string | null }
const vectors = JSON.parse(readFileSync(resolve(process.cwd(), '../alldep-jar/src/test/resources/pick-locator/escape-vectors.json'), 'utf8')) as Vector[];

/** Tokens of each line, carrying the tokenizer state across lines as the editor does. */
function tokenize(lines: string[]): Array<Array<{ text: string; style: string | null }>> {
  const st = seleasyStreamParser.startState!(2) as SelState;
  return lines.map((line) => {
    const out: Array<{ text: string; style: string | null }> = [];
    const s = new StringStream(line, 4, 2);
    let guard = 0;
    while (!s.eol() && guard++ < 10000) {
      s.start = s.pos;
      const style = seleasyStreamParser.token(s, st);
      if (s.pos === s.start) s.pos++;
      out.push({ text: line.slice(s.start, s.pos), style });
    }
    return out;
  });
}

const settled = (toks: Array<{ text: string; style: string | null }>) => toks.map((t) => [t.text, t.style]);

describe('pasted pick lines', () => {
  it('the shared vectors are present', () => {
    expect(vectors.length).toBeGreaterThanOrEqual(20);
    expect(vectors.filter((v) => v.line).length).toBeGreaterThanOrEqual(15);
    for (const id of ['role-double-quote', 'role-single-quote', 'role-backslash', 'role-non-ascii', 'role-long-61-none', 'role-newline-falls-to-id',
      'role-bracket-falls-to-id', 'css-both-quotes']) expect(vectors.some((v) => v.id === id), id).toBe(true);
  });

  for (const v of vectors.filter((x) => x.line)) {
    it(`${v.id}: inserted verbatim as its own line, and it tokenizes cleanly`, () => {
      const line = v.line!;
      expect(line).toBe('click ' + v.locator);
      expect(line).not.toMatch(/[\r\n]/);
      const doc = Text.of(['open chrome', '  goto http://abc.com/x.html', 'sleep 100']);
      const ins = lineInsertion(doc, doc.line(2).from + 4, line)!;
      const after = doc.replace(ins.from, ins.to, Text.of(ins.insert.split('\n')));
      expect(after.line(3).text).toBe('  ' + line);                     // own line, current indent, text untouched
      expect(after.line(4).text).toBe('sleep 100');
      const toks = tokenize(after.toJSON());
      // the line round-trips through the tokens, its command is recognised, and every quoted part is one closed string
      expect(toks[2].map((t) => t.text).join('')).toBe('  ' + line);
      expect(toks[2].find((t) => t.text.trim() === 'click')?.style).toBe('atom');
      for (const t of toks[2]) {
        const q = t.text[0];
        if (t.style === 'string' && (q === '"' || q === "'") && t.text.length > 0) {
          expect(t.text.length, `${v.id}: ${t.text}`).toBeGreaterThan(1);
          expect(t.text.endsWith(q), `${v.id}: unterminated ${t.text}`).toBe(true);
        }
      }
      if (/[@=]['"]/.test(line)) expect(toks[2].some((t) => t.style === 'string' && /^['"]/.test(t.text)), `${v.id}: quoted value`).toBe(true);
      // nothing leaks into the next line: it tokenizes exactly as it does alone
      expect(settled(toks[3])).toEqual(settled(tokenize(['sleep 100'])[0]));
    });
  }

  it('a blank cursor line is replaced; a line with a newline is refused', () => {
    const doc = Text.of(['open chrome', '    ', 'x']);
    const ins = lineInsertion(doc, doc.line(2).from + 2, 'click id@a')!;
    expect(ins).toMatchObject({ from: doc.line(2).from, to: doc.line(2).to, insert: '    click id@a' });
    expect(lineInsertion(doc, 0, 'click id@a\ngoto x')).toBeNull();
    expect(lineInsertion(doc, 0, '')).toBeNull();
    expect(lineInsertion(doc, 9999, 'click id@a')!.from).toBe(doc.length);   // clamped
  });
});
