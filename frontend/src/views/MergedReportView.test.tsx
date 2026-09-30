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
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import MergedReportView from './MergedReportView';
import type { MergedIndexData } from '../api';

const data: MergedIndexData = {
  type: 'merged-index',
  generated: 1700000000000,
  results: 'gatf-results.json',
  summary: { total: 6, passed: 2, failed: 2, flaky: 1, skipped: 1, durationMs: 4000, shards: 4 },
  shards: [1, 2, 3, 4].map((i) => ({
    name: 'shard-' + i + '-of-4',
    source: '/ci/' + i,
    summary: { total: 2, passed: 1, failed: i === 1 ? 1 : 0, flaky: 0, skipped: 0, durationMs: 1000 * i },
    reports: [{ label: 'shard-' + i + '-of-4/17', dataUrl: 'shard-' + i + '-of-4/17/selenium-index.data.json', reportType: 'selenium-final' }],
  })),
  flaky: ['c.sel › [chrome]'],
  failures: [{ title: 'b.sel › [chrome]', error: 'boom\nstack', shard: 'shard-1-of-4' }],
};

describe('MergedReportView', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response('not found', { status: 404 })));
  });
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('renders run insights from the merged gatf-results.json', async () => {
    const results = {
      summary: { total: 1, passed: 0, failed: 0, flaky: 1, skipped: 0, durationMs: 10 },
      tests: [{ suite: 'c.sel', name: '[chrome]', title: 'c.sel › [chrome]', status: 'flaky', attempts: 2, durationMs: 10, shard: 'shard-2-of-4' }],
    };
    const f = vi.fn(async () => new Response(JSON.stringify(results), { status: 200 }));
    vi.stubGlobal('fetch', f);
    render(<MergedReportView data={data} baseDir="/reports/merged/" onOpenReport={() => {}} />);
    expect(await screen.findByText('Run insights')).toBeInTheDocument();
    expect(String((f.mock.calls[0] as unknown[])[0])).toContain('/reports/merged/gatf-results.json');
  });

  it('shows totals, all four shards, failures and flaky', () => {
    render(<MergedReportView data={data} baseDir="/reports/merged/" onOpenReport={() => {}} />);
    expect(screen.getByText('4 shard(s) merged', { exact: false })).toBeInTheDocument();
    expect(screen.getAllByText(/^shard-\d-of-4$/)).toHaveLength(4);
    expect(screen.getByText('Failures (1)')).toBeInTheDocument();
    expect(screen.getByText('boom')).toBeInTheDocument();
    expect(screen.getByText('Flaky (1)')).toBeInTheDocument();
  });

  it('opens a shard report relative to the merged folder', () => {
    const open = vi.fn();
    render(<MergedReportView data={data} baseDir="/reports/merged/" onOpenReport={open} />);
    fireEvent.click(screen.getAllByText('selenium-final')[2]);
    expect(open).toHaveBeenCalledWith('/reports/merged/shard-3-of-4/17/selenium-index.data.json');
  });
});
