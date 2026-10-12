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
const OFF = 'try{if(window.__gatfPicker&&window.__gatfPicker.off)window.__gatfPicker.off();}catch(e){}try{delete window.__gatfPicker;}catch(e){}try{delete window.__gatfPicks;}catch(e){}';
const DRAIN = 'var p=window.__gatfPicks||[];try{delete window.__gatfPicks;}catch(e){window.__gatfPicks=undefined;}return p;';
const exec = (src: string) => new Function(src)();
type W = Window & { __gatfPicker?: unknown; __gatfPicks?: unknown[] };

afterEach(() => { exec(OFF); document.body.innerHTML = ''; });

describe('live picker page script', () => {
  it('the Java constants the test rebuilds are the shipped ones', () => {
    expect(pickJava).toContain('"(function(){if(window.__gatfPicker)return;" + SemanticLocators.RESOLVER_FN + SemanticLocators.CANDIDATES_FN');
    expect(pickJava).toContain('+ SemanticLocators.FALLBACK_FN + PICKER_FN + "__gatfPickerInstall(" + tidAttrLiteral + ");})();"');
    expect(pickJava.replace(/"\s*\+\s*"/g, '')).toContain(OFF);
    expect(pickJava).toContain(JSON.stringify(DRAIN).slice(1, -1).replace(/\\"/g, '"'));
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
    expect(picks[0].sel).toEqual({ s: 'id', v: 'b', n: 1 });
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
    expect(exec(DRAIN)).toEqual([{ type: 'cancel' }]);
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
