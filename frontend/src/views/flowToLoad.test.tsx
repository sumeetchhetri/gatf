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
import { describe, it, expect, vi, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { api, flowLoadFileName } from '../api';
import { Header } from '../components/Header';
import ExecuteView from './ExecuteView';
import type { Trace } from '../trace/types';

afterEach(() => vi.unstubAllGlobals());

const json = (v: unknown, status = 200) => new Response(JSON.stringify(v), { status, headers: { 'Content-Type': 'application/json' } });

const trace = {
  meta: { runId: 'LRun-1', selFile: 'tests/ui/checkout.sel', browser: 'chrome', protocol: 'cdp', startTs: 0, endTs: 10,
    recNetwork: true, recConsole: false, recSnapshots: false, recMetrics: false, recCpu: false, recCoverage: false, recVideo: false,
    truncatedBodies: 0, bytesCaptured: 0 },
  t0: 0,
  t1: 10,
} as unknown as Trace;

describe('flow → load (M17)', () => {
  it('names the load test after the script', () => {
    expect(flowLoadFileName('tests/ui/checkout.sel', 'r')).toBe('checkout-load.xml');
    expect(flowLoadFileName('C:\\p\\my flow.sel', 'r')).toBe('my_flow-load.xml');
    expect(flowLoadFileName(null, 'LRun-1-2')).toBe('LRun-1-2-load.xml');
    expect(flowLoadFileName('a-load.sel', 'r')).toBe('a-load.xml');
  });

  it('posts the HAR as a saved flow import with the load options', async () => {
    const calls: { url: string; body: string }[] = [];
    vi.stubGlobal('fetch', vi.fn(async (url: string, init?: RequestInit) => {
      calls.push({ url, body: String(init?.body) });
      return json({ type: 'flow', fileName: 'checkout-load.xml', kind: 'xml', content: '', unsupported: [], stats: {}, saved: true, savedPath: 'checkout-load.xml', sideFiles: ['checkout-load.targets.jsonl'] });
    }));
    const r = await api.flowToLoad('{"log":{}}', 'checkout-load.xml', { users: 5, durationSeconds: 20 });
    expect(r.savedPath).toBe('checkout-load.xml');
    expect(calls[0].url).toBe('/import?type=flow&save=true&fileName=checkout-load.xml&users=5&duration=20');
    expect(calls[0].body).toBe('{"log":{}}');
  });

  it('surfaces a server refusal', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => json({ error: 'nothing to load-test: the HAR has no API/document request' }, 400)));
    await expect(api.flowToLoad('{}', 'x-load.xml')).rejects.toThrow('nothing to load-test');
  });

  it('the Trace Viewer header offers the button only when there is a flow', () => {
    const go = vi.fn();
    const { rerender } = render(<Header trace={trace} onClose={() => {}} onLoadTest={go} />);
    fireEvent.click(screen.getByRole('button', { name: '⚡ Load test this flow' }));
    expect(go).toHaveBeenCalledTimes(1);
    rerender(<Header trace={trace} onClose={() => {}} onLoadTest={go} loadBusy />);
    expect(screen.getByRole('button', { name: 'Creating load test…' })).toBeDisabled();
    rerender(<Header trace={trace} onClose={() => {}} />);
    expect(screen.queryByRole('button', { name: /Load test this flow/ })).toBeNull();
  });

  it('Execute selects the saved load test and starts it as an API run', async () => {
    const calls: { url: string; method: string; body?: string }[] = [];
    vi.stubGlobal('fetch', vi.fn(async (url: string, init?: RequestInit) => {
      calls.push({ url, method: init?.method || 'GET', body: init?.body as string | undefined });
      if (url.startsWith('/testcasefiles')) return json([['tests/ui/checkout.sel', null], ['checkout-load.xml', null]]);
      return json({ error: 'Execution completed' });
    }));
    const consumed = vi.fn();
    render(<ExecuteView onOpenReports={() => {}} autoRun="checkout-load.xml" onConsumeAutoRun={consumed} />);
    await waitFor(() => expect(calls.some((c) => c.method === 'PUT')).toBe(true));
    const put = calls.find((c) => c.method === 'PUT')!;
    expect(put.url).toContain('/execute?pluginType=executor-api');
    expect(JSON.parse(put.body!)).toEqual(['checkout-load.xml']);
    expect(consumed).toHaveBeenCalled();
    expect(await screen.findByText(/Execution completed/)).toBeInTheDocument();
  });
});
