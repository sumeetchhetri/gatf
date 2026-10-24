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
import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { SEL_MIX, selThemeSpec } from './selTheme';

// Δ163n (bug 6): the caret is drawn by drawSelection() as .cm-cursor; it must use the theme's text colour, 2px wide, and
// cursor / selection / bracket / active line must have usable contrast in BOTH themes (tokens read from styles.css).

type RGB = [number, number, number];
const css = readFileSync(resolve(__dirname, '../styles.css'), 'utf8');
function tokens(theme: 'dark' | 'light'): Record<string, RGB> {
  const m = css.match(new RegExp(`:root\\[data-theme='${theme}'\\]\\s*\\{([^}]*)\\}`));
  if (!m) throw new Error('no ' + theme + ' block');
  const out: Record<string, RGB> = {};
  for (const d of m[1].matchAll(/--([\w-]+):\s*#([0-9a-fA-F]{6})/g)) {
    out[d[1]] = [0, 2, 4].map((i) => parseInt(d[2].slice(i, i + 2), 16)) as RGB;
  }
  return out;
}
const mix = (a: RGB, b: RGB, pct: number): RGB => a.map((v, i) => v * (pct / 100) + b[i] * (1 - pct / 100)) as RGB;
const lum = (c: RGB) => {
  const [r, g, b] = c.map((v) => {
    const s = v / 255;
    return s <= 0.03928 ? s / 12.92 : ((s + 0.055) / 1.055) ** 2.4;
  });
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
};
const contrast = (a: RGB, b: RGB) => {
  const [x, y] = [lum(a), lum(b)].sort((p, q) => q - p);
  return (x + 0.05) / (y + 0.05);
};

describe('seleasy editor theme', () => {
  it('draws the cursor in the text colour, 2px wide (base: no .cm-cursor rule -> near-black on the dark panel)', () => {
    const c = (selThemeSpec as Record<string, Record<string, string>>)['.cm-cursor, .cm-dropCursor'];
    expect(c).toEqual({ borderLeftColor: 'var(--fg)', borderLeftWidth: '2px' });
    const spec = JSON.stringify(selThemeSpec);
    expect(spec).toContain('cm-selectionBackground');
    expect(spec).toContain('cm-matchingBracket');
  });

  for (const theme of ['dark', 'light'] as const) {
    it(`has usable contrast in the ${theme} theme`, () => {
      const t = tokens(theme);
      const { panel, fg, accent } = t;
      expect(panel && fg && accent).toBeTruthy();
      // cursor (non-text UI: WCAG 1.4.11 needs 3:1; we want text-level)
      expect(contrast(fg, panel)).toBeGreaterThanOrEqual(4.5);
      // text on the selection, on a matching bracket and on the active line
      const sel = mix(accent, panel, SEL_MIX.selection);
      const br = mix(accent, panel, SEL_MIX.bracket);
      const al = mix(fg, panel, SEL_MIX.activeLine);
      expect(contrast(fg, sel)).toBeGreaterThanOrEqual(4.5);
      expect(contrast(fg, br)).toBeGreaterThanOrEqual(4.5);
      expect(contrast(fg, al)).toBeGreaterThanOrEqual(4.5);
      // and the selection / active line are visibly different from the plain panel
      expect(contrast(sel, panel)).toBeGreaterThan(1.25);
      expect(contrast(al, panel)).toBeGreaterThan(1.04);
    });
  }
});
