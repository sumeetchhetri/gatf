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
import { render, screen, within } from '@testing-library/react';
import RunTrends from './RunTrends';
import type { HistoryRun } from '../report/history';

const runs: HistoryRun[] = Array.from({ length: 10 }, (_, k) => ({
  id: 'r' + k,
  time: 10_000 - k * 1000,
  summary: { total: 2, passed: k % 3 ? 2 : 1, failed: k % 3 ? 0 : 1, flaky: 0, skipped: 0, durationMs: 1000 + k },
  tests: [
    { title: 'a.sel › [chrome]', status: 'passed', durationMs: 1 },
    { title: 'b.sel › [chrome]', status: k % 3 ? 'passed' : 'failed', durationMs: 1 },
  ],
}));

describe('RunTrends', () => {
  it('shows the trend over the last 10 runs and the least stable test', () => {
    render(<RunTrends runs={runs} />);
    expect(screen.getByText(/Last 10 run\(s\)/)).toBeInTheDocument();
    expect(screen.getAllByTestId('trend-run')).toHaveLength(10);
    expect(screen.getByTestId('trend-duration').getAttribute('points')!.split(' ')).toHaveLength(10);
    const t = screen.getByRole('table', { name: 'Least stable tests' });
    const rows = within(t).getAllByRole('row');
    expect(rows).toHaveLength(2); // header + b.sel
    expect(rows[1]).toHaveTextContent('b.sel › [chrome]');
    expect(rows[1]).toHaveTextContent('40%');
  });

  it('says so when there is no history', () => {
    render(<RunTrends runs={[]} />);
    expect(screen.getByText('No run history yet.')).toBeInTheDocument();
  });
});
