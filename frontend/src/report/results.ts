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
// M12: analysis of a run's gatf-results.json (written by CLI runs, merged by gatf merge-reports):
// duration timeline by worker lane, slowest tests / files, flaky list, tag + status filters. Pure functions.

export type TestStatus = 'passed' | 'failed' | 'flaky' | 'skipped';

export interface ResultTest {
  suite: string;
  name: string;
  title: string;
  status: TestStatus;
  attempts: number;
  durationMs: number;
  startMs?: number; // M7: ms since the run started (absent in results written before Δ131)
  worker?: number; // M7: worker lane
  shard?: string; // merged runs
  error?: string | null;
  softFailures?: number;
  attachments?: string[];
  tags?: string[]; // M12: file-header + own @tags (results written from this build on)
}

export interface RunResults {
  summary: {
    total: number;
    passed: number;
    failed: number;
    flaky: number;
    skipped: number;
    softFailures?: number;
    durationMs: number;
    workers?: number;
    shard?: string | null;
    shards?: number;
  };
  tests: ResultTest[];
}

/** True when `d` looks like a gatf-results.json document (it has no `type` field). */
export function isRunResults(d: unknown): d is RunResults {
  const o = d as Record<string, unknown> | null;
  return !!o && typeof o === 'object' && !('type' in o) && Array.isArray(o.tests) && typeof o.summary === 'object';
}

// same shape as the executor's TestFilter.TAG
const TAG = /(^|\s)@([A-Za-z0-9_][A-Za-z0-9_.:-]*)/g;

/** A test's @tags: the recorded `tags` (a .sel file's header tags, an API case's name/description tags) plus any in its title. */
export function tagsOf(t: ResultTest): string[] {
  const out = new Set<string>(t.tags ?? []);
  for (const src of [t.title, t.name]) {
    for (const m of (src || '').matchAll(TAG)) out.add('@' + m[2]);
  }
  return [...out];
}

export function allTags(tests: ResultTest[]): string[] {
  const s = new Set<string>();
  tests.forEach((t) => tagsOf(t).forEach((x) => s.add(x)));
  return [...s].sort();
}

export interface Filter {
  statuses: Set<TestStatus>; // empty = all
  tag?: string; // '' / undefined = any
  text?: string; // case-insensitive substring of the title
}

export function filterTests(tests: ResultTest[], f: Filter): ResultTest[] {
  const text = (f.text || '').trim().toLowerCase();
  return tests.filter(
    (t) =>
      (f.statuses.size === 0 || f.statuses.has(t.status)) &&
      (!f.tag || tagsOf(t).includes(f.tag)) &&
      (!text || t.title.toLowerCase().includes(text)),
  );
}

/** The n slowest tests (ties keep input order). Skipped tests have no meaningful duration. */
export function slowest(tests: ResultTest[], n: number): ResultTest[] {
  return tests
    .map((t, i) => ({ t, i }))
    .filter((x) => x.t.status !== 'skipped')
    .sort((a, b) => b.t.durationMs - a.t.durationMs || a.i - b.i)
    .slice(0, n)
    .map((x) => x.t);
}

/**
 * Drops records that are part of another record in the list: a UI script reports one result for the whole script
 * and one per subtest (`[chrome] › x.sel` and `[chrome] › x.sel › sub`), and adding both would count the time twice.
 */
export function topLevel(tests: ResultTest[]): ResultTest[] {
  const titles = new Set(tests.map((t) => t.suite + '\u0000' + t.title));
  return tests.filter((t) => {
    for (let i = t.title.lastIndexOf(' \u203a '); i > 0; i = t.title.lastIndexOf(' \u203a ', i - 1)) {
      if (titles.has(t.suite + '\u0000' + t.title.slice(0, i))) return false;
    }
    return true;
  });
}

export interface FileStat {
  suite: string;
  tests: number;
  totalMs: number;
  failed: number;
  flaky: number;
}

/** Per test file: count, summed duration, failures, flaky; slowest first. */
export function slowestFiles(tests: ResultTest[], n: number): FileStat[] {
  const m = new Map<string, FileStat>();
  const all = new Set(tests);
  for (const t of topLevel(tests)) {
    const k = (t.shard ? t.shard + ' › ' : '') + t.suite;
    const s = m.get(k) ?? { suite: k, tests: 0, totalMs: 0, failed: 0, flaky: 0 };
    s.totalMs += t.status === 'skipped' ? 0 : t.durationMs;
    m.set(k, s);
  }
  // counts cover every record of the file (subtests included)
  for (const t of all) {
    const s = m.get((t.shard ? t.shard + ' \u203a ' : '') + t.suite);
    if (!s) continue;
    s.tests++;
    if (t.status === 'failed') s.failed++;
    if (t.status === 'flaky') s.flaky++;
  }
  return [...m.values()].sort((a, b) => b.totalMs - a.totalMs).slice(0, n);
}

/** Flaky tests (passed on a retry), most retries first. */
export function flaky(tests: ResultTest[]): ResultTest[] {
  return tests.filter((t) => t.status === 'flaky').sort((a, b) => b.attempts - a.attempts);
}

export interface Bar {
  test: ResultTest;
  start: number;
  end: number;
}
export interface Lane {
  key: string;
  label: string;
  bars: Bar[];
}
export interface TimelineModel {
  lanes: Lane[];
  totalMs: number;
  /** false when the results predate startMs/worker: bars are then laid end to end per lane (an estimate). */
  exact: boolean;
}

/**
 * Speedboard-style timeline: one lane per worker (per shard + worker for merged runs), bars at startMs..+durationMs.
 * A UI script's subtests are inside its bar (see topLevel).
 * Results without start offsets fall back to back-to-back bars in one lane per shard.
 */
export function timeline(tests: ResultTest[]): TimelineModel {
  const exact = tests.length > 0 && tests.every((t) => typeof t.startMs === 'number');
  const lanes = new Map<string, Lane>();
  const cursor = new Map<string, number>();
  let totalMs = 0;
  for (const t of topLevel(tests)) {
    if (t.status === 'skipped') continue;
    const w = exact ? (t.worker ?? 0) : 0;
    const key = (t.shard ?? '') + '#' + w;
    let lane = lanes.get(key);
    if (!lane) {
      lane = { key, label: (t.shard ? t.shard + ' · ' : '') + 'worker ' + (w + 1), bars: [] };
      lanes.set(key, lane);
    }
    const start = exact ? (t.startMs as number) : (cursor.get(key) ?? 0);
    const end = start + Math.max(0, t.durationMs);
    cursor.set(key, end);
    lane.bars.push({ test: t, start, end });
    totalMs = Math.max(totalMs, end);
  }
  const sorted = [...lanes.values()].sort((a, b) => a.key.localeCompare(b.key, undefined, { numeric: true }));
  sorted.forEach((l) => l.bars.sort((a, b) => a.start - b.start));
  return { lanes: sorted, totalMs, exact };
}

/** Share of the run's wall time the workers were busy (0..1): how well the parallel pool is used. */
export function utilisation(m: TimelineModel): number {
  if (m.totalMs <= 0 || m.lanes.length === 0) return 0;
  let busy = 0;
  for (const l of m.lanes) for (const b of l.bars) busy += b.end - b.start;
  return Math.min(1, busy / (m.totalMs * m.lanes.length));
}
