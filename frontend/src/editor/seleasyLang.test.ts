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
import { describe, it, expect } from 'vitest';
import { StringStream } from '@codemirror/language';
import { SELEASY, seleasyStreamParser, type SelState } from './seleasyLang';

describe('SELEASY keyword map', () => {
  it('includes core seleasy commands and locators', () => {
    for (const kw of ['open', 'chrome', 'click', 'goto', 'subtest', '@call', 'include', 'config', 'type', 'screenshot', 'execjs', 'css', 'xpath']) {
      expect(kw in SELEASY, `missing keyword: ${kw}`).toBe(true);
    }
  });

  it('classifies commands (1) vs locators/values (3) vs operators (4)', () => {
    expect(SELEASY['open']).toBe(1);
    expect(SELEASY['subtest']).toBe(1);
    expect(SELEASY['@call']).toBe(1);
    expect(SELEASY['css']).toBe(3);
    expect(SELEASY['xpath']).toBe(3);
    for (const p of ['role', 'label', 'placeholder', 'testid', 'alt', 'title']) expect(SELEASY[p]).toBe(3);   // M3 semantic locators
    expect(SELEASY['chrome']).toBe(3);
    expect(SELEASY['==']).toBe(4);
    expect(SELEASY['??']).toBe(1);          // conditional wait
    expect(SELEASY['if']).toEqual([1, 1]);  // block keyword -> tuple
  });

  it('has the full command vocabulary', () => {
    expect(Object.keys(SELEASY).length).toBeGreaterThan(150);
  });
});

/** Style of the token covering `needle` on each line, carrying the tokenizer state across lines as the editor does. */
function styleAt(lines: string[], idx: number, needle: string): string | null {
  const st = seleasyStreamParser.startState!(2) as SelState;
  for (let i = 0; i < lines.length; i++) {
    const s = new StringStream(lines[i], 4, 2);
    const col = lines[i].indexOf(needle);
    let guard = 0;
    while (!s.eol() && guard++ < 10000) {
      s.start = s.pos;
      const style = seleasyStreamParser.token(s, st);
      if (s.pos === s.start) s.pos++;
      if (i === idx && col >= s.start && col < s.pos) return style;
    }
  }
  return null;
}

/** Every token moves the stream forward (the base tokenizer reset the position to 1 on a mid-line '/*'). */
function advancesEveryToken(line: string): boolean {
  const st = seleasyStreamParser.startState!(2) as SelState;
  const s = new StringStream(line, 4, 2);
  let guard = 0;
  while (!s.eol() && guard++ < 1000) {
    s.start = s.pos;
    seleasyStreamParser.token(s, st);
    if (s.pos <= s.start) return false;
  }
  return s.eol();
}

describe('block comments (Δ145c: as Command.parse since Δ145b)', () => {
  it('a mid-line /* never moves the tokenizer backwards', () => {
    for (const l of ['click xpath@//div/*/a', 'route **/api/* fulfill 200 users.json', 'type "a /* b" id@x', '/* c */']) {
      expect(advancesEveryToken(l), l).toBe(true);
    }
  });

  it("'/*' inside a statement is not a comment opener", () => {
    const xp = ['click xpath@//div/*/a', 'open chrome'];
    expect(styleAt(xp, 0, '/*/a')).not.toBe('comment');
    expect(styleAt(xp, 1, 'open')).toBe('keyword');
    const rt = ['route **/api/* fulfill 200 users.json', 'click id@x'];
    expect(styleAt(rt, 0, '/* fulfill')).not.toBe('comment');
    expect(styleAt(rt, 1, 'click')).not.toBe('comment');
  });

  it("'/*' at the start of a statement opens one; a quoted '*/' does not close it; the closing line's rest is comment", () => {
    const blk = ['  /* block', 'click id@x', '"a */ b" still comment', 'end */ trailing', 'open chrome'];
    expect(styleAt(blk, 0, '/*')).toBe('comment');
    expect(styleAt(blk, 1, 'click')).toBe('comment');
    expect(styleAt(blk, 2, 'still')).toBe('comment');
    expect(styleAt(blk, 3, 'trailing')).toBe('comment');
    expect(styleAt(blk, 4, 'open')).toBe('keyword');
    expect(styleAt(['/* one */', 'open chrome'], 1, 'open')).toBe('keyword');
    expect(styleAt(["/* it's open */", 'open chrome'], 1, 'open')).toBe('keyword');   // an unclosed quote masks nothing
  });
});
