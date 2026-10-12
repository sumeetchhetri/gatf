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
// Δ148: the generated resolver module is the Java scripts verbatim (one source), and it works from the viewer's realm
// against another document — the snapshot frame's — without touching the viewer's own document.
import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
// @ts-ignore -- plain .mjs build tool, no types
import { render, JAVA, OUT } from '../../../tools/gen-semantic-resolver.mjs';
import { makeSemanticResolver } from './semanticResolver.generated';

describe('semanticResolver.generated.js', () => {
  it('equals what the generator makes from SemanticLocators.java now (else: node tools/gen-semantic-resolver.mjs)', () => {
    expect(resolve(OUT)).toBe(resolve(process.cwd(), 'src/trace/semanticResolver.generated.js'));
    expect(readFileSync(OUT, 'utf8')).toBe(render(readFileSync(JAVA, 'utf8')));
  });

  it('runs in strict mode, in this realm, against a foreign document', () => {
    document.body.innerHTML = '<button data-testid="go">Go</button><button data-testid="go">Go</button>';   // the viewer's own DOM
    const doc = new DOMParser().parseFromString(
      '<html><body><main><h1>Orders</h1><button data-testid="go">Go</button><button>Cancel</button><button>Cancel</button>' +
        '<label for="e">Email</label><input id="e" name="email"><p class="note">a</p><p class="note">b</p>' +
        '<div id="host"></div></main></body></html>', 'text/html');
    const r = makeSemanticResolver(doc, window);
    const go = doc.querySelector('[data-testid="go"]')!;
    const cand = r.candidates(go, 'data-testid');
    expect(cand.find((c) => c.s === 'testid')).toEqual({ s: 'testid', v: 'go', n: 1 });   // counted in doc, not document
    expect(cand.find((c) => c.s === 'role')).toMatchObject({ role: 'button', name: 'Go', n: 1 });
    const cancel = doc.querySelectorAll('button')[1];
    expect(r.candidates(cancel).find((c) => c.s === 'role')!.n).toBe(2);
    expect(r.candidates(doc.getElementById('e')!).find((c) => c.s === 'label')).toEqual({ s: 'label', v: 'Email', n: 1 });
    // fallback: unique id, unique name, else the shortest unique css path
    expect(r.fallback(doc.getElementById('e')!)).toEqual({ s: 'id', v: 'e', n: 1 });
    const second = doc.querySelectorAll('p.note')[1];
    const fb = r.fallback(second)!;
    expect(fb.s).toBe('css');
    expect(fb.n).toBe(1);
    expect(doc.querySelectorAll(fb.v).length).toBe(1);
    expect(doc.querySelector(fb.v)).toBe(second);
    expect(r.fallback(cancel)!.v).toBe('button:nth-of-type(2)');
    // inside a shadow root: no id@ / name@ / css@ can reach it
    const sr = doc.getElementById('host')!.attachShadow({ mode: 'open' });
    sr.innerHTML = '<button id="in">In</button>';
    expect(r.fallback(sr.getElementById('in')!)).toBeNull();
    expect(r.candidates(sr.getElementById('in')!).find((c) => c.s === 'role')).toMatchObject({ role: 'button', name: 'In', n: 1 });
    expect(r.fallback(null as unknown as Element)).toBeNull();
  });

  it('fallback escapes ids and classes that need it', () => {
    const doc = new DOMParser().parseFromString('<p id="1 a:b">x</p><span class="a:b 2">y</span><span class="a:b">z</span>', 'text/html');
    const r = makeSemanticResolver(doc, window);
    const p = doc.querySelector('p')!;
    const fp = r.fallback(p)!;
    expect(fp.s).toBe('id');
    expect(doc.querySelectorAll('#' + CSS.escape('1 a:b')).length).toBe(1);
    const sp = doc.querySelector('span')!;
    const fs = r.fallback(sp)!;
    expect(doc.querySelector(fs.v)).toBe(sp);
    expect(doc.querySelectorAll(fs.v).length).toBe(1);
  });
});
