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
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';

const coverageIndex = vi.fn();
const reports = vi.fn();
vi.mock('../api', async (orig) => {
  const real = await orig<typeof import('../api')>();
  return {
    ...real,
    api: {
      ...real.api,
      coverageIndex: (...a: unknown[]) => coverageIndex(...a),
      reports: () => reports(),
      runResults: () => Promise.reject(new Error('none')),
      history: () => Promise.resolve({ dir: '', runs: [] }),
    },
  };
});

import CoverageReportView from './CoverageReportView';
import ReportsView from './ReportsView';
import type { ReportArtifact } from '../api';

const row: ReportArtifact = {
  name: 'run-1/index.html',
  url: '/reports/run-1/index.html',
  size: 10,
  modified: 1,
  label: 'run-1',
  traces: [{ name: 'run-1/a-trace.zip', url: '/reports/run-1/a-trace.zip', size: 5, modified: 1 }],
} as ReportArtifact;

const base = (over: Record<string, unknown> = {}) => ({
  format: 'gatf-coverage-index',
  version: 2,
  detail: 'summary',
  run: 'run-1',
  status: 'ok',
  counts: { traces: 2, tracesWithCoverage: 2, tracesCoverageRead: 2, tests: 2, scripts: 2, routesVisited: 2, crawlRoutes: 4,
    untestedRoutes: 2, untestedScripts: 1, skipped: 0 },
  tests: {
    'orders.sel': { coverage: 'ok', traces: ['a-trace.zip'], routes: ['https://app.test/#/orders'], scripts: { 'https://app.test/main.js': { len: 100, usedBytes: 40 } },
      css: { rules: 3, totalBytes: 40, usedBytes: 10 } },
    'login.sel': { coverage: 'missing', traces: ['b-trace.zip'], routes: ['https://app.test/login'], scripts: {}, css: { rules: 0, totalBytes: 0, usedBytes: 0 } },
  },
  routes: { 'https://app.test/#/orders': ['orders.sel'], 'https://app.test/login': ['login.sel'] },
  scripts: {
    'https://app.test/main.js': { len: 100, usedBytes: 40, functionsHit: 1, functionsTotal: 3, tests: ['orders.sel'], bodies: [] },
    'https://app.test/admin.js': { len: 400, usedBytes: 10, functionsHit: 0, functionsTotal: 5, tests: ['orders.sel'], bodies: [] },
  },
  css: { unattributed: true, traces: 1, sheets: 1, rules: 3, totalBytes: 40, usedBytes: 10 },
  crawl: { source: 'app-coverage.json', routes: 4 },
  untested: {
    routes: [
      { route: 'https://app.test/settings', priority: 'low' },
      { route: 'https://app.test/#/admin', priority: 'high' },
    ],
    scripts: ['https://app.test/admin.js'],
  },
  skipped: [],
  notes: ['CSS is one unattributed bucket: rule usage carries no stylesheet URL in this capture'],
  rules: {},
  crawls: ['app-coverage.json', 'old-coverage.json'],
  ...over,
});

describe('<CoverageReportView>', () => {
  beforeEach(() => {
    coverageIndex.mockReset();
    reports.mockReset();
  });

  it('lists untested routes (high priority first), scripts with no function run, JS rows and the CSS bucket', async () => {
    coverageIndex.mockResolvedValue(base());
    const { container } = render(<CoverageReportView report={row} onBack={() => {}} />);
    await screen.findByText(/2 of 4 crawled route\(s\) no test visited/);
    expect(coverageIndex).toHaveBeenCalledWith('run-1/index.html', undefined);
    const routeCells = [...container.querySelectorAll('td.url.mono')].map((td) => td.textContent);
    expect(routeCells).toEqual(['https://app.test/#/admin', 'https://app.test/settings']);
    expect(screen.getAllByText('https://app.test/admin.js').length).toBe(2); // untested list + JS table
    expect(screen.getByText('0/5')).toBeTruthy();
    expect(screen.getByText(/CSS rules — unattributed/)).toBeTruthy();
    expect(screen.getByText(/summed over 1 trace/)).toBeTruthy();
    expect(screen.getByText('orders.sel')).toBeTruthy();
    const missing = screen.getByText('missing'); // Δ155a: the per-test state is shown, with why it matters
    expect(missing.getAttribute('title')).toMatch(/always selected/);
    expect(screen.queryByText(/Partial data/)).toBeNull();
  });

  it('shows the empty state for a run with no data, with the reason', async () => {
    coverageIndex.mockResolvedValue(
      base({
        status: 'no-data',
        tests: {},
        routes: {},
        scripts: {},
        untested: { routes: null, scripts: [] },
        notes: ['no trace in this run has coverage.json: turn on trace coverage (traceCoverage) for a Chromium run'],
      }),
    );
    render(<CoverageReportView report={row} onBack={() => {}} />);
    await screen.findByText('No coverage data for this run.');
    expect(screen.getByText(/turn on trace coverage/)).toBeTruthy();
    expect(screen.queryByText(/Untested routes/)).toBeNull();
  });

  it('flags partial data, lists what was skipped and says when there is no crawl map', async () => {
    coverageIndex.mockResolvedValue(
      base({
        status: 'partial',
        counts: { ...base().counts, tracesCoverageRead: 1, skipped: 1 },
        crawl: null,
        untested: { routes: null, scripts: [] },
        skipped: [{ file: 'b-trace.zip', test: 'login.sel', reason: 'coverage.json has an unknown or old format (expected {js:[…], css:[…]})' }],
      }),
    );
    render(<CoverageReportView report={row} onBack={() => {}} />);
    await screen.findByText(/Partial data: 1 of 2 trace\(s\) gave coverage, 1 item\(s\) skipped/);
    expect(screen.getByText(/No crawl map to compare with/)).toBeTruthy();
    expect(screen.getByText('Skipped (1)')).toBeTruthy();
    expect(screen.getByText(/unknown or old format/)).toBeTruthy();
    expect(screen.getAllByText('login.sel').length).toBeGreaterThan(1); // the skip names its test
  });

  it('treats an old or foreign payload as no data, and a failed request as an error', async () => {
    coverageIndex.mockResolvedValueOnce({ format: 'gatf-coverage-index', version: 3, status: 'ok', counts: {} });
    const { unmount } = render(<CoverageReportView report={row} onBack={() => {}} />);
    await screen.findByText(/did not return a coverage index this page understands/);
    unmount();
    coverageIndex.mockRejectedValueOnce(new Error('HTTP 400 for /api/coverage'));
    render(<CoverageReportView report={row} onBack={() => {}} />);
    await screen.findByText(/Could not load coverage \(HTTP 400/);
  });

  it('reloads with the chosen crawl map', async () => {
    coverageIndex.mockResolvedValue(base());
    render(<CoverageReportView report={row} onBack={() => {}} />);
    const sel = await screen.findByLabelText('Crawl map');
    fireEvent.change(sel, { target: { value: 'old-coverage.json' } });
    await waitFor(() => expect(coverageIndex).toHaveBeenLastCalledWith('run-1/index.html', 'old-coverage.json'));
  });

  it('opens from a Reports row that has traces, and goes back', async () => {
    reports.mockResolvedValue({
      dir: '/out',
      reports: [row, { ...row, name: 'run-2/index.html', url: '/reports/run-2/index.html', label: 'run-2', traces: [] }],
    });
    coverageIndex.mockResolvedValue(base());
    render(<ReportsView onOpenTrace={() => {}} onOpenReport={() => {}} onOpenViewer={() => {}} />);
    const buttons = await screen.findAllByRole('button', { name: 'Coverage' });
    expect(buttons).toHaveLength(1); // run-2 has no traces
    fireEvent.click(buttons[0]);
    await screen.findByText(/2 of 4 crawled route/);
    fireEvent.click(screen.getByRole('button', { name: /Reports/ }));
    await screen.findByText(/Suite runs/);
  });
});
