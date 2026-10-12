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
// Δ148: the snapshot picker's pure parts — strictness against the snapshot DOM, the server-down fallback, and the
// debounced, cached ranking.
import { describe, it, expect, vi, afterEach } from 'vitest';
import { PickRanker, collectPick, deepElementFromPoint, strictLine, strictLineFromReply, type RankReply, type PickRaw } from './picker';

const doc = () => new DOMParser().parseFromString(
  '<html><body><button data-testid="save">Save</button><button>Dup</button><button>Dup</button><p>t</p></body></html>', 'text/html');

const reply = (over: Partial<RankReply>): RankReply => ({
  locator: 'testid@save', line: 'click testid@save', strict: true, source: 0,
  candidates: [{ kind: 'testid', locator: 'testid@save', n: 1 }], fallback: null, ...over,
});

afterEach(() => { vi.useRealTimers(); });

describe('collectPick + strictLine', () => {
  it('counts in the snapshot document and offers a line only for a source with exactly one match there', () => {
    const d = doc();
    const raw = collectPick(d, d.querySelector('button')!);
    expect(raw.tag).toBe('<button>');
    expect(raw.cand[0]).toEqual({ s: 'testid', v: 'save', n: 1 });
    expect(strictLine(raw, reply({}))).toBe('click testid@save');
    // the server's choice must also hold here: a source whose local count is not 1 gives no line
    const dup = collectPick(d, d.querySelectorAll('button')[1]);
    const roleIdx = dup.cand.findIndex((c) => c.s === 'role');
    expect(dup.cand[roleIdx].n).toBe(2);
    expect(strictLine(dup, reply({ source: roleIdx, locator: 'role@button[name=Dup]', line: 'click role@button[name=Dup]' }))).toBeNull();
    expect(dup.sel).toEqual({ s: 'css', v: 'button:nth-of-type(2)', n: 1 });
    expect(strictLine(dup, reply({ source: -1, locator: 'css@"button:nth-of-type(2)"', line: 'click css@"button:nth-of-type(2)"' })))
      .toBe('click css@"button:nth-of-type(2)"');
    expect(strictLine(dup, reply({ source: null, locator: null, line: null }))).toBeNull();
    expect(strictLine(dup, null)).toBeNull();
    expect(strictLine(dup, reply({ source: 7 }))).toBeNull();                 // out of range: no line
  });

  it('never throws on hostile input', () => {
    const d = doc();
    expect(collectPick(d, null as unknown as Element)).toEqual({ cand: [], sel: null, tag: '' });
    const broken = { defaultView: null } as unknown as Document;
    expect(() => collectPick(broken, d.body)).not.toThrow();
    expect(deepElementFromPoint({} as Document, 1, 1)).toBeNull();
  });

  it('descends into open shadow roots when hit-testing', () => {
    const d = doc();
    const host = d.createElement('div');
    d.body.appendChild(host);
    const sr = host.attachShadow({ mode: 'open' });
    sr.innerHTML = '<span>in</span>';
    const inner = sr.querySelector('span')!;
    (d as unknown as { elementFromPoint: unknown }).elementFromPoint = () => host;
    (sr as unknown as { elementFromPoint: unknown }).elementFromPoint = () => inner;
    expect(deepElementFromPoint(d, 3, 4)).toBe(inner);
  });

  it('strictLineFromReply uses the counts that came with a live pick', () => {
    expect(strictLineFromReply(reply({}))).toBe('click testid@save');
    expect(strictLineFromReply(reply({ candidates: [{ kind: 'testid', locator: 'testid@save', n: 3 }] }))).toBeNull();
    expect(strictLineFromReply(reply({ source: -1, fallback: { locator: 'id@x', n: 1 }, locator: 'id@x', line: 'click id@x' }))).toBe('click id@x');
    expect(strictLineFromReply(null)).toBeNull();
  });
});

describe('PickRanker', () => {
  const raw: PickRaw = { cand: [{ s: 'testid', v: 'save', n: 1 }], sel: null, tag: '<button>' };

  it('debounces hover: only the element the pointer rests on is ranked, once', async () => {
    vi.useFakeTimers();
    const rank = vi.fn(async () => reply({}));
    const r = new PickRanker(rank, 150);
    const d = doc();
    const [a, b, c] = Array.from(d.querySelectorAll('button'));
    const seen: string[] = [];
    const collect = vi.fn(() => raw);
    r.request(a, collect, () => seen.push('a'));
    vi.advanceTimersByTime(100);
    r.request(b, collect, () => seen.push('b'));
    vi.advanceTimersByTime(100);
    r.request(c, collect, () => seen.push('c'));
    expect(rank).not.toHaveBeenCalled();
    expect(collect).not.toHaveBeenCalled();                   // the document walk waits for the pointer to rest too
    await vi.advanceTimersByTimeAsync(150);
    expect(rank).toHaveBeenCalledTimes(1);
    expect(collect).toHaveBeenCalledTimes(1);
    expect(seen).toEqual(['c']);
    // cached: the same element again answers at once, without another call
    r.request(c, collect, () => seen.push('c2'));
    expect(seen).toEqual(['c', 'c2']);
    expect(rank).toHaveBeenCalledTimes(1);
    expect(r.cached(c)!.line).toBe('click testid@save');
  });

  it('server down: candidates stay, no ranked pick, no line; the failure is not cached', async () => {
    const rank = vi.fn(async (): Promise<RankReply> => { throw new TypeError('Failed to fetch'); });
    const r = new PickRanker(rank, 0);
    const el = doc().body;
    const got = await new Promise<Parameters<Parameters<PickRanker['request']>[2]>[0]>((res) => r.request(el, raw, res, true));
    expect(got.ranked).toBeNull();
    expect(got.line).toBeNull();
    expect(got.raw.cand).toHaveLength(1);
    expect(got.error).toMatch(/ranking unavailable/);
    expect(r.cached(el)).toBeUndefined();
    // a 4xx from the server is treated the same way (paste disabled)
    const r2 = new PickRanker(async () => ({ error: 'HTTP 413' }) as RankReply, 0);
    const got2 = await new Promise<Parameters<Parameters<PickRanker['request']>[2]>[0]>((res) => r2.request(el, raw, res, true));
    expect(got2).toMatchObject({ ranked: null, line: null, error: 'HTTP 413' });
    // and back up: the next request is ranked
    rank.mockImplementation(async () => reply({}));
    const got3 = await new Promise<Parameters<Parameters<PickRanker['request']>[2]>[0]>((res) => r.request(el, raw, res, true));
    expect(got3.line).toBe('click testid@save');
  });

  it('a superseded request does not call back', async () => {
    let release!: (v: RankReply) => void;
    const r = new PickRanker(() => new Promise<RankReply>((res) => { release = res; }), 0);
    const d = doc();
    const cb = vi.fn();
    r.request(d.body, raw, cb, true);
    r.cancel();
    release(reply({}));
    await Promise.resolve(); await Promise.resolve();
    expect(cb).not.toHaveBeenCalled();
  });
});
