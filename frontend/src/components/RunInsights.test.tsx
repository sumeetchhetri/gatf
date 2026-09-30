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
import { render, screen, fireEvent, within } from '@testing-library/react';
import RunInsights from './RunInsights';
import type { RunResults } from '../report/results';

const data: RunResults = {
  summary: { total: 4, passed: 2, failed: 1, flaky: 1, skipped: 0, durationMs: 5000, workers: 2 },
  tests: [
    { suite: 'a.sel', name: '[chrome]', title: 'a.sel › [chrome] @smoke', status: 'passed', attempts: 1, durationMs: 300, startMs: 0, worker: 0 },
    { suite: 'b.sel', name: '[chrome]', title: 'b.sel › [chrome]', status: 'failed', attempts: 1, durationMs: 4800, startMs: 0, worker: 1, error: 'boom' },
    { suite: 'c.sel', name: '[chrome]', title: 'c.sel › [chrome] @smoke', status: 'flaky', attempts: 2, durationMs: 1500, startMs: 300, worker: 0 },
    { suite: 'd.sel', name: '[chrome]', title: 'd.sel › [chrome]', status: 'passed', attempts: 1, durationMs: 100, startMs: 1800, worker: 0 },
  ],
};

describe('RunInsights', () => {
  it('shows the slowest test first, the flaky list and one timeline bar per test', () => {
    render(<RunInsights data={data} />);
    const slow = screen.getByRole('table', { name: 'Slowest tests' });
    expect(within(slow).getAllByRole('row')[0]).toHaveTextContent('b.sel › [chrome]');
    const flk = screen.getByRole('table', { name: 'Flaky tests' });
    expect(flk).toHaveTextContent('c.sel › [chrome] @smoke');
    expect(flk).toHaveTextContent('passed on attempt 2');
    expect(screen.getAllByTestId('tl-bar')).toHaveLength(4);
    expect(screen.getByText(/2 lane\(s\)/)).toBeInTheDocument();
  });

  it('filters by tag and status', () => {
    render(<RunInsights data={data} />);
    fireEvent.change(screen.getByLabelText('Tag'), { target: { value: '@smoke' } });
    expect(screen.getByText('2 of 4 test(s)')).toBeInTheDocument();
    expect(screen.getAllByTestId('tl-bar')).toHaveLength(2);
    fireEvent.click(screen.getByLabelText('flaky'));
    expect(screen.getByText('1 of 4 test(s)')).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('Tag'), { target: { value: '' } });
    fireEvent.click(screen.getByLabelText('flaky'));
    fireEvent.change(screen.getByLabelText('Search tests'), { target: { value: 'd.sel' } });
    expect(screen.getByText('1 of 4 test(s)')).toBeInTheDocument();
    expect(screen.getByText('No flaky tests in this selection.')).toBeInTheDocument();
  });
});
