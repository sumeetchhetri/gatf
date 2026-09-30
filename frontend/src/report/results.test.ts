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
import type { ResultTest } from './results';
import { allTags, filterTests, flaky, isRunResults, slowest, slowestFiles, tagsOf, timeline, topLevel, utilisation } from './results';

const t = (suite: string, name: string, status: ResultTest['status'], durationMs: number, extra: Partial<ResultTest> = {}): ResultTest => ({
  suite,
  name,
  title: suite + ' › ' + name,
  status,
  attempts: status === 'flaky' ? 2 : 1,
  durationMs,
  ...extra,
});

const tests: ResultTest[] = [
  t('api/users.xml', 'List users @smoke', 'passed', 120, { startMs: 0, worker: 0 }),
  t('api/users.xml', 'Create user @smoke @crud', 'failed', 900, { startMs: 120, worker: 0, error: 'Expected 201\nstack' }),
  t('ui/login.sel', '[chrome]', 'flaky', 4000, { startMs: 0, worker: 1, attempts: 3 }),
  t('ui/cart.sel', '[chrome]', 'passed', 2500, { startMs: 1020, worker: 0 }),
  t('ui/cart.sel', '[chrome] refund', 'skipped', 0, { startMs: 3520, worker: 0 }),
  t('ui/pay.sel', '[chrome]', 'flaky', 300, { startMs: 4000, worker: 1 }),
];

describe('run results analysis', () => {
  it('recognises gatf-results.json (no type field)', () => {
    expect(isRunResults({ summary: {}, tests: [] })).toBe(true);
    expect(isRunResults({ type: 'merged-index', summary: {}, tests: [] })).toBe(false);
    expect(isRunResults(null)).toBe(false);
  });

  it('answers what is slow', () => {
    expect(slowest(tests, 2).map((x) => x.suite)).toEqual(['ui/login.sel', 'ui/cart.sel']);
    expect(slowest(tests, 10).some((x) => x.status === 'skipped')).toBe(false);
    const files = slowestFiles(tests, 10);
    expect(files[0]).toMatchObject({ suite: 'ui/login.sel', totalMs: 4000, flaky: 1 });
    expect(files.find((f) => f.suite === 'api/users.xml')).toMatchObject({ tests: 2, totalMs: 1020, failed: 1 });
  });

  it('answers what is flaky, most retries first', () => {
    expect(flaky(tests).map((x) => x.suite)).toEqual(['ui/login.sel', 'ui/pay.sel']);
  });

  it('filters by status, tag and text', () => {
    expect(tagsOf(tests[1])).toEqual(['@smoke', '@crud']);
    expect(allTags(tests)).toEqual(['@crud', '@smoke']);
    expect(filterTests(tests, { statuses: new Set(['failed', 'flaky']) })).toHaveLength(3);
    expect(filterTests(tests, { statuses: new Set(), tag: '@crud' })).toHaveLength(1);
    expect(filterTests(tests, { statuses: new Set(), text: 'CART' })).toHaveLength(2);
    expect(filterTests(tests, { statuses: new Set(['passed']), tag: '@smoke', text: 'list' })).toHaveLength(1);
  });

  it('uses recorded tags (.sel header tags) and the executor tag shape', () => {
    const x = t('ui/login.sel', '[chrome]', 'passed', 1, { tags: ['@smoke', '@auth'] });
    expect(tagsOf(x)).toEqual(['@smoke', '@auth']);
    expect(tagsOf(t('a', 'x @-. y @ok', 'passed', 1))).toEqual(['@ok']);
  });

  it('counts a UI script once, not once for the script and again for its subtests', () => {
    const script = t('ui/x.sel', '[chrome]', 'passed', 1000, { title: '[chrome] › ui/x.sel', startMs: 0, worker: 0 });
    const s1 = t('ui/x.sel', '[chrome] a', 'passed', 400, { title: '[chrome] › ui/x.sel › a', startMs: 600, worker: 0 });
    const s2 = t('ui/x.sel', '[chrome] b', 'failed', 500, { title: '[chrome] › ui/x.sel › b', startMs: 500, worker: 0 });
    expect(topLevel([script, s1, s2])).toEqual([script]);
    expect(slowestFiles([script, s1, s2], 5)[0]).toMatchObject({ totalMs: 1000, tests: 3, failed: 1 });
    const m = timeline([script, s1, s2]);
    expect(m.lanes[0].bars).toHaveLength(1);
    expect(utilisation(m)).toBe(1);
    // a selection with only the failed subtest still charts it
    expect(timeline([s2]).lanes[0].bars).toHaveLength(1);
  });

  it('lays tests out per worker lane at their real start', () => {
    const m = timeline(tests);
    expect(m.exact).toBe(true);
    expect(m.lanes.map((l) => l.label)).toEqual(['worker 1', 'worker 2']);
    expect(m.totalMs).toBe(4300);
    expect(m.lanes[1].bars.map((b) => [b.start, b.end])).toEqual([
      [0, 4000],
      [4000, 4300],
    ]);
    // busy 3520 + 4300 over 2 lanes x 4300 ms
    expect(utilisation(m)).toBeCloseTo((3520 + 4300) / (2 * 4300), 5);
  });

  it('falls back to end-to-end bars for results without start times, one lane per shard', () => {
    const old = tests.map(({ startMs: _s, worker: _w, ...rest }) => ({ ...rest, shard: rest.suite.startsWith('ui') ? 'shard-2-of-2' : 'shard-1-of-2' }));
    const m = timeline(old);
    expect(m.exact).toBe(false);
    expect(m.lanes).toHaveLength(2);
    expect(m.lanes[0].label).toBe('shard-1-of-2 · worker 1');
    expect(m.lanes[1].bars.map((b) => b.start)).toEqual([0, 4000, 6500]);
  });
});
