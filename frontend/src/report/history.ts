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
// M12: trends over the archived runs served by GET /api/history (newest first).
import type { RunResults, TestStatus } from './results';

export interface HistoryRun {
  id: string;
  time: number;
  summary: RunResults['summary'] | null;
  tests: { title: string; status: TestStatus; durationMs: number }[];
}
export interface HistoryIndex {
  dir: string;
  runs: HistoryRun[];
}

export interface TrendPoint {
  id: string;
  time: number;
  passed: number;
  failed: number;
  flaky: number;
  skipped: number;
  durationMs: number;
}

/** Oldest → newest points for the trend chart (runs without a summary are skipped). */
export function trend(runs: HistoryRun[]): TrendPoint[] {
  return runs
    .filter((r) => r.summary)
    .map((r) => ({
      id: r.id,
      time: r.time,
      passed: r.summary!.passed ?? 0,
      failed: r.summary!.failed ?? 0,
      flaky: r.summary!.flaky ?? 0,
      skipped: r.summary!.skipped ?? 0,
      durationMs: r.summary!.durationMs ?? 0,
    }))
    .sort((a, b) => a.time - b.time);
}

export interface Unstable {
  title: string;
  runs: number;
  failed: number;
  flaky: number;
  /** (failed + flaky) / runs */
  rate: number;
  last: TestStatus;
}

/** Tests that failed or were flaky in any of the runs, least stable first. */
export function unstable(runs: HistoryRun[]): Unstable[] {
  const m = new Map<string, Unstable>();
  const newestFirst = [...runs].sort((a, b) => b.time - a.time);
  for (const r of newestFirst) {
    for (const t of r.tests) {
      if (t.status === 'skipped') continue;
      const u = m.get(t.title) ?? { title: t.title, runs: 0, failed: 0, flaky: 0, rate: 0, last: t.status };
      u.runs++;
      if (t.status === 'failed') u.failed++;
      if (t.status === 'flaky') u.flaky++;
      m.set(t.title, u);
    }
  }
  return [...m.values()]
    .map((u) => ({ ...u, rate: (u.failed + u.flaky) / u.runs }))
    .filter((u) => u.rate > 0)
    .sort((a, b) => b.rate - a.rate || b.runs - a.runs || a.title.localeCompare(b.title));
}
