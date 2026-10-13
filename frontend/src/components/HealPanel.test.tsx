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
import { describe, it, expect, vi, afterEach, beforeEach } from 'vitest';
import { render, screen, fireEvent, act } from '@testing-library/react';
import { HealBar, TraceHealButton } from './HealPanel';
import SelCodeMirror from './SelCodeMirror';
import { healStore, diffRows, healableTraceUrl, strictText, applyMessage, type HealResult } from '../editor/heal';

const json = (v: unknown, status = 200) => new Response(JSON.stringify(v), { status, headers: { 'Content-Type': 'application/json' } });

const DIFF = '--- a/checkout.sel\n+++ b/checkout.sel\n@@ -2,5 +2,5 @@\n goto https://shop.example.test/cart\n click id@checkout\n'
  + " type 'pune' id@city\n-click id@place-order\n+click testid@place-order-btn\n ?? css@\"h1\" text = 'Thank you'\n";

function result(over: Partial<HealResult> = {}): HealResult {
  return {
    status: 'proposed',
    ai: 'not-configured',
    aiStatus: 'AI not configured: no <ai> block in the config',
    step: 'click id@place-order',
    locator: { token: 'id@place-order', count: 0 },
    proposals: [
      { rank: 1, source: 'deterministic', kind: 'locator', locator: 'testid@place-order-btn', count: 1, strict: true,
        lines: ['click testid@place-order-btn'], why: 'similar-element', score: 20, diff: DIFF },
      { rank: 2, source: 'ai', kind: 'locator', locator: 'label@Place order', count: 1, strict: true,
        lines: ['click label@Place order'], why: 'model-suggested', score: 0, diff: DIFF.replace('testid@place-order-btn', 'label@Place order') },
    ],
    file: 'checkout.sel',
    line: 5,
    healId: '0123456789abcdef0123456789abcdef',
    ...over,
  };
}

beforeEach(() => healStore.set(null));
afterEach(() => {
  vi.unstubAllGlobals();
  healStore.set(null);
});

describe('heal diff UI (Δ150)', () => {
  it('shows each proposal as a diff with rank, source and strict count; Accept writes via the server and updates the buffer', async () => {
    const calls: { url: string; body: unknown }[] = [];
    const content = 'open chrome\n...\nclick testid@place-order-btn\n';
    vi.stubGlobal('fetch', vi.fn(async (url: string, init?: RequestInit) => {
      calls.push({ url, body: JSON.parse(String(init?.body)) });
      return json({ status: 'applied', file: 'checkout.sel', line: 5, rank: 1, content, lines: ['click testid@place-order-btn'] });
    }));
    const onApplied = vi.fn();
    act(() => healStore.set(result()));
    render(<HealBar path="checkout.sel" dirty={false} onApplied={onApplied} />);
    const p1 = screen.getByTestId('heal-prop-1');
    expect(p1.textContent).toContain('#1');
    expect(p1.textContent).toContain('deterministic');
    expect(p1.textContent).toContain('strict · 1 match');
    expect(p1.querySelector('.heal-row-del')!.textContent).toBe('- click id@place-order');
    expect(p1.querySelector('.heal-row-add')!.textContent).toBe('+ click testid@place-order-btn');
    expect(screen.getByTestId('heal-prop-2').textContent).toContain('ai');
    expect(calls).toHaveLength(0);

    fireEvent.click(p1.querySelector('button.primary')!);
    await screen.findByText(/Applied proposal #1 to line 5/);
    expect(calls).toEqual([{ url: '/record?action=healApply', body: { healId: '0123456789abcdef0123456789abcdef', rank: 1 } }]);
    expect(onApplied).toHaveBeenCalledWith(content, 5);
    expect(healStore.get()).toBeNull();
    expect(screen.queryByTestId('heal-prop-1')).toBeNull();
  });

  it('Reject removes a proposal and writes nothing', () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    act(() => healStore.set(result()));
    render(<HealBar path="checkout.sel" dirty={false} onApplied={vi.fn()} />);
    const rejectBtn = Array.from(screen.getByTestId('heal-prop-1').querySelectorAll('button')).find((b) => b.textContent === 'Reject')!;
    fireEvent.click(rejectBtn);
    expect(screen.queryByTestId('heal-prop-1')).toBeNull();
    expect(screen.getByTestId('heal-prop-2')).toBeTruthy();
    fireEvent.click(Array.from(screen.getByTestId('heal-prop-2').querySelectorAll('button')).find((b) => b.textContent === 'Reject')!);
    expect(screen.getByText('All proposals rejected. Nothing was written.')).toBeTruthy();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('a conflict is shown and nothing changes in the editor', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => json({ status: 'conflict', reason: 'line 5 changed since the heal', expected: 'click id@place-order', actual: 'click id@other', line: 5 }, 409)));
    const onApplied = vi.fn();
    act(() => healStore.set(result()));
    render(<HealBar path="checkout.sel" dirty={false} onApplied={onApplied} />);
    fireEvent.click(screen.getByTestId('heal-prop-1').querySelector('button.primary')!);
    expect(await screen.findByText(/Conflict: line 5 changed since the heal \(now: click id@other\)\. Nothing was written; heal again\./)).toBeTruthy();
    expect(onApplied).not.toHaveBeenCalled();
    expect(healStore.get()).not.toBeNull();
  });

  it('a fix that fails validation is reported, not applied', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => json({ status: 'invalid', validation: { status: 'FAILURE', error: 'Invalid command at line 5' } }, 422)));
    const onApplied = vi.fn();
    act(() => healStore.set(result()));
    render(<HealBar path="checkout.sel" dirty={false} onApplied={onApplied} />);
    fireEvent.click(screen.getByTestId('heal-prop-1').querySelector('button.primary')!);
    expect(await screen.findByText(/did not validate: Invalid command at line 5\. Nothing was written\./)).toBeTruthy();
    expect(onApplied).not.toHaveBeenCalled();
  });

  it('shows the AI status (not configured) and the deterministic proposals still work', () => {
    act(() => healStore.set(result({ proposals: [result().proposals[0]] })));
    render(<HealBar path="checkout.sel" dirty={false} onApplied={vi.fn()} />);
    expect(screen.getByText('AI: AI not configured: no <ai> block in the config')).toBeTruthy();
    expect((screen.getByTestId('heal-prop-1').querySelector('button.primary') as HTMLButtonElement).disabled).toBe(false);
  });

  it('unsaved edits block Accept; another file or no healId shows nothing acceptable', () => {
    act(() => healStore.set(result()));
    const { rerender } = render(<HealBar path="checkout.sel" dirty={true} onApplied={vi.fn()} />);
    expect(screen.getByText('Save or discard your edits before accepting a fix.')).toBeTruthy();
    expect((screen.getByTestId('heal-prop-1').querySelector('button.primary') as HTMLButtonElement).disabled).toBe(true);
    rerender(<HealBar path="other.sel" dirty={false} onApplied={vi.fn()} />);
    expect(screen.queryByTestId('heal-prop-1')).toBeNull();
    act(() => healStore.set(result({ healId: undefined })));
    rerender(<HealBar path="checkout.sel" dirty={false} onApplied={vi.fn()} />);
    expect((screen.getByTestId('heal-prop-1').querySelector('button.primary') as HTMLButtonElement).disabled).toBe(true);
  });

  it('"Heal last failure" asks the server for this file and shows the result; a server refusal is shown', async () => {
    const bodies: unknown[] = [];
    const replies = [json({ error: 'no failing traced run of checkout.sel under the reports directory' }, 404), json(result())];
    vi.stubGlobal('fetch', vi.fn(async (url: string, init?: RequestInit) => {
      expect(url).toBe('/record?action=heal');
      bodies.push(JSON.parse(String(init?.body)));
      return replies.shift()!;
    }));
    render(<HealBar path="checkout.sel" dirty={false} onApplied={vi.fn()} />);
    fireEvent.click(screen.getByText('🩹 Heal last failure'));
    expect(await screen.findByText(/Heal: no failing traced run of checkout.sel/)).toBeTruthy();
    fireEvent.click(screen.getByText('🩹 Heal last failure'));
    expect(await screen.findByTestId('heal-prop-1')).toBeTruthy();
    expect(bodies).toEqual([{ file: 'checkout.sel' }, { file: 'checkout.sel' }]);
  });

  it('the Trace Viewer button heals a served trace and hands the result over; dropped-in traces get no button', async () => {
    const bodies: unknown[] = [];
    vi.stubGlobal('fetch', vi.fn(async (_url: string, init?: RequestInit) => {
      bodies.push(JSON.parse(String(init?.body)));
      return json(result());
    }));
    const onHeal = vi.fn();
    const { container } = render(<TraceHealButton traceUrl={'/reports/run%201/checkout-trace.zip'} onHeal={onHeal} />);
    fireEvent.click(screen.getByText('🩹 Heal failed step'));
    await vi.waitFor(() => expect(onHeal).toHaveBeenCalled());
    expect(bodies).toEqual([{ trace: '/reports/run 1/checkout-trace.zip' }]);
    expect(onHeal.mock.calls[0][0].file).toBe('checkout.sel');
    container.remove();
    const { container: c2 } = render(<TraceHealButton traceUrl={undefined} onHeal={onHeal} />);
    expect(c2.textContent).toBe('');
  });

  it('helpers: diff rows, strict text, served trace urls, apply messages', () => {
    expect(diffRows(DIFF).map((r) => r.t).join('')).toBe('@   -+ ');
    expect(strictText(result().proposals[0])).toBe('strict · 1 match');
    expect(strictText({ ...result().proposals[0], kind: 'goto', locator: undefined })).toBe('navigation');
    expect(healableTraceUrl('http://localhost:9080/reports/a-trace.zip?x=1')).toBe('/reports/a-trace.zip');
    expect(healableTraceUrl('blob:abc')).toBeNull();
    expect(healableTraceUrl('/other/a-trace.zip')).toBeNull();
    expect(applyMessage({ status: 'applied' })).toBeNull();
    expect(applyMessage({ error: 'this heal expired or is unknown: heal again', httpStatus: 410 })).toBe('Not applied: this heal expired or is unknown: heal again');
    healStore.set({ file: 5 } as unknown as HealResult);
    expect(healStore.get()).toBeNull();
  });

  it('the editor marks the line a heal targets, and clears it', () => {
    const { container, rerender } = render(<SelCodeMirror value={'open chrome\nclick id@x\n'} filePath="a.sel" markLine={2} />);
    const marked = container.querySelectorAll('.cm-heal-line');
    expect(marked).toHaveLength(1);
    expect(marked[0].textContent).toBe('click id@x');
    rerender(<SelCodeMirror value={'open chrome\nclick id@x\n'} filePath="a.sel" markLine={null} />);
    expect(container.querySelectorAll('.cm-heal-line')).toHaveLength(0);
    rerender(<SelCodeMirror value={'open chrome\n'} filePath="a.sel" markLine={9} />);
    expect(container.querySelectorAll('.cm-heal-line')).toHaveLength(0);
  });
});
