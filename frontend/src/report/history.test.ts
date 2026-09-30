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
import type { HistoryRun } from './history';
import { trend, unstable } from './history';

const run = (i: number, statuses: Record<string, 'passed' | 'failed' | 'flaky' | 'skipped'>): HistoryRun => {
  const tests = Object.entries(statuses).map(([title, status]) => ({ title, status, durationMs: 10 }));
  const c = (s: string) => tests.filter((t) => t.status === s).length;
  return {
    id: 'gatf-results-' + i + '.json',
    time: 1000 * i,
    summary: { total: tests.length, passed: c('passed'), failed: c('failed'), flaky: c('flaky'), skipped: c('skipped'), durationMs: 100 * i },
    tests,
  };
};

// newest first, as /api/history returns them: 10 runs
const runs: HistoryRun[] = Array.from({ length: 10 }, (_, k) => {
  const i = 10 - k;
  return run(i, { a: 'passed', b: i % 2 ? 'failed' : 'passed', c: i === 10 ? 'flaky' : 'passed', d: 'skipped' });
});

describe('run history', () => {
  it('orders the trend oldest to newest with the counts of each run', () => {
    const t = trend(runs);
    expect(t).toHaveLength(10);
    expect(t.map((p) => p.time)).toEqual([1, 2, 3, 4, 5, 6, 7, 8, 9, 10].map((i) => i * 1000));
    expect(t[9]).toMatchObject({ passed: 2, failed: 0, flaky: 1, skipped: 1, durationMs: 1000 });
    expect(t[0]).toMatchObject({ passed: 2, failed: 1, flaky: 0 });
  });

  it('ranks the least stable tests and ignores skips and always-green tests', () => {
    const u = unstable(runs);
    expect(u.map((x) => x.title)).toEqual(['b', 'c']);
    expect(u[0]).toMatchObject({ runs: 10, failed: 5, rate: 0.5, last: 'passed' });
    expect(u[1]).toMatchObject({ runs: 10, flaky: 1, rate: 0.1, last: 'flaky' });
  });

  it('skips runs without a summary', () => {
    expect(trend([{ id: 'x', time: 1, summary: null, tests: [] }])).toEqual([]);
  });
});
