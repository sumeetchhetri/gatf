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
// Δ148: the live picker's page script (PickLocator.PICKER_FN, run as the recorder session injects it) in jsdom: the
// pick is swallowed before the recorder's document listeners, and nothing of the picker stays on the page afterwards.
import { describe, it, expect, vi, afterEach } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
// @ts-ignore -- plain .mjs build tool, no types
import { textBlock } from '../../../tools/gen-semantic-resolver.mjs';

const pickJava = readFileSync(resolve(process.cwd(), '../alldep-jar/src/main/java/com/gatf/record/PickLocator.java'), 'utf8');
const semJava = readFileSync(resolve(process.cwd(), '../alldep-jar/src/main/java/com/gatf/selenium/SemanticLocators.java'), 'utf8');
// PickLocator.armJs(tid): (function(){if(window.__gatfPicker)return; RESOLVER_FN CANDIDATES_FN FALLBACK_FN PICKER_FN install(tid);})();
const ARM = '(function(){if(window.__gatfPicker)return;' + textBlock(semJava, 'RESOLVER_FN') + textBlock(semJava, 'CANDIDATES_FN') +
  textBlock(semJava, 'FALLBACK_FN') + textBlock(pickJava, 'PICKER_FN') + '__gatfPickerInstall("data-testid");})();';
/** A Java `static final String NAME = "…" + "…";` constant: its literals joined, Java escapes undone. */
function javaConst(src: string, name: string): string {
  const m = new RegExp('\\b' + name + '\\s*=\\s*([\\s\\S]*?");\\s*\\n').exec(src);
  if (!m) throw new Error(name + ' not found');
  let out = '';
  for (const lit of m[1].match(/"(?:[^"\\]|\\.)*"/g) ?? []) out += lit.slice(1, -1).replace(/\\(.)/g, '$1');
  return out;
}
const OFF = javaConst(pickJava, 'OFF_JS');
const DRAIN = javaConst(pickJava, 'DRAIN_JS');
const exec = (src: string) => new Function(src)();
type W = Window & { __gatfPicker?: unknown; __gatfPicks?: unknown[] };

afterEach(() => { exec(OFF); document.body.innerHTML = ''; });

describe('live picker page script', () => {
  it('Δ148a: a pick the page queued before arming is cleared on install', () => {
    document.body.innerHTML = '<p>x</p>';
    (window as W).__gatfPicks = [{ type: 'pick', cand: [{ s: 'testid', v: 'forged', n: 1 }], sel: null }];
    exec(ARM);
    expect(exec(DRAIN)).toEqual([]);
  });

  it('Δ148a: DRAIN returns only the last pick, rebuilt and bounded, and survives hostile shapes', () => {
    const big = 'x'.repeat(5000);
    (window as W).__gatfPicks = [
      { type: 'pick', cand: [{ s: 'testid', v: 'first', n: 1 }], sel: null },
      { type: 'pick', cand: Array.from({ length: 1000 }, (_, i) => ({ s: 'testid', v: 't' + i + big, n: 1, extra: big })), sel: { s: 'css', v: big, n: 1 }, junk: big },
    ];
    const out = exec(DRAIN) as Array<{ type: string; cand: Array<Record<string, unknown>>; sel: Record<string, unknown> }>;
    expect(out).toHaveLength(1);
    expect(out[0].cand).toHaveLength(16);
    expect(out[0].cand[0]).toEqual({ s: 'testid', v: ('t0' + big).slice(0, 61), role: null, name: null, n: 1 });
    expect((out[0].sel.v as string).length).toBe(1025);
    expect(Object.keys(out[0])).toEqual(['type', 'cand', 'sel']);
    expect('__gatfPicks' in (window as W)).toBe(false);
    for (const hostile of [42, 'str', { length: 1e9 }, [null], [7], { length: 1, 0: { cand: { length: 3 } } }]) {
      (window as W).__gatfPicks = hostile as unknown as unknown[];
      expect(() => exec(DRAIN)).not.toThrow();
    }
  });

  it('the Java constants the test rebuilds are the shipped ones', () => {
    expect(pickJava).toContain('"(function(){if(window.__gatfPicker)return;" + SemanticLocators.RESOLVER_FN + SemanticLocators.CANDIDATES_FN');
    expect(pickJava).toContain('+ SemanticLocators.FALLBACK_FN + PICKER_FN + "__gatfPickerInstall(" + tidAttrLiteral + ");})();"');
    expect(OFF).toContain('window.__gatfPicker.off()');
    expect(DRAIN).toMatch(/^var p=window.__gatfPicks;/);
  });

  it('a click is swallowed (the recorder never sees it), ranked candidates are queued, and the picker removes itself', () => {
    document.body.innerHTML = '<button data-testid="save" id="b">Save</button><button>Other</button>';
    const recorder = vi.fn();
    document.addEventListener('click', recorder, true);           // the recorder's capture listener
    const pageClick = vi.fn();
    document.getElementById('b')!.addEventListener('click', pageClick);
    exec(ARM);
    exec(ARM);                                                     // idempotent: the poller re-arms every tick
    const btn = document.getElementById('b')!;
    (document as unknown as { elementFromPoint: unknown }).elementFromPoint = () => btn;
    window.dispatchEvent(new MouseEvent('mousemove', { clientX: 5, clientY: 5 }));
    expect(document.querySelectorAll('gatf-pick-outline')).toHaveLength(1);
    const ev = new MouseEvent('click', { bubbles: true, cancelable: true, clientX: 5, clientY: 5 });
    btn.dispatchEvent(ev);
    expect(ev.defaultPrevented).toBe(true);
    expect(recorder).not.toHaveBeenCalled();
    expect(pageClick).not.toHaveBeenCalled();
    const picks = exec(DRAIN) as Array<{ type: string; cand: Array<{ s: string; n: number }>; sel: { s: string; v: string } }>;
    expect(picks).toHaveLength(1);
    expect(picks[0].type).toBe('pick');
    expect(picks[0].cand.find((c) => c.s === 'testid')).toMatchObject({ v: 'save', n: 1 });
    expect(picks[0].sel).toMatchObject({ s: 'id', v: 'b', n: 1 });
    // nothing left: no outline, no globals, no listeners (the next click reaches the page and the recorder again)
    expect(document.querySelector('gatf-pick-outline')).toBeNull();
    expect('__gatfPicker' in (window as W)).toBe(false);
    expect('__gatfPicks' in (window as W)).toBe(false);
    btn.click();
    expect(recorder).toHaveBeenCalledTimes(1);
    expect(pageClick).toHaveBeenCalledTimes(1);
    document.removeEventListener('click', recorder, true);
  });

  it('Escape cancels, and OFF clears an armed picker with its outline', () => {
    document.body.innerHTML = '<p id="p">x</p>';
    exec(ARM);
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', cancelable: true }));
    expect(exec(DRAIN)).toEqual([{ type: 'cancel', cand: [], sel: null }]);
    expect('__gatfPicker' in (window as W)).toBe(false);
    exec(ARM);
    const p = document.getElementById('p')!;
    (document as unknown as { elementFromPoint: unknown }).elementFromPoint = () => p;
    window.dispatchEvent(new MouseEvent('mousemove', { clientX: 1, clientY: 1 }));
    expect(document.querySelector('gatf-pick-outline')).not.toBeNull();
    exec(OFF);
    expect(document.querySelector('gatf-pick-outline')).toBeNull();
    expect('__gatfPicker' in (window as W)).toBe(false);
    const down = new MouseEvent('mousedown', { bubbles: true, cancelable: true });
    p.dispatchEvent(down);
    expect(down.defaultPrevented).toBe(false);                    // the swallow listeners are gone too
  });
});
