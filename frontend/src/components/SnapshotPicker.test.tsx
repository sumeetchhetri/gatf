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
// Δ148: the Trace Viewer's snapshot picker in DomView — parent-realm listeners and outline, the sandbox untouched,
// the server line offered only when it resolves strictly, the server-down fallback, and clean removal.
import { describe, it, expect, vi, afterEach } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { render, screen, fireEvent, act, waitFor } from '@testing-library/react';
import { strToU8 } from 'fflate';
import { loadTraceZip, parseFiles } from '../trace/loadTrace';
import { DomView } from './Stage';
import { pickStore } from '../editor/pickStore';

const zip = readFileSync(resolve(process.cwd(), 'src/trace/fixtures/dom-v2.trace.zip'));
const realFetch = globalThis.fetch;
afterEach(() => { globalThis.fetch = realFetch; pickStore.set(null); vi.restoreAllMocks(); });

async function mountPicker(trace = loadTraceZip(new Uint8Array(zip)), at = 1) {
  URL.createObjectURL = (() => 'blob:v') as typeof URL.createObjectURL;
  URL.revokeObjectURL = (() => {}) as typeof URL.revokeObjectURL;
  const snap = trace.snapshots.find((s) => s.action === at) ?? trace.snapshots[0];
  const view = render(<DomView trace={trace} snap={snap} />);
  const frame = screen.getByTitle('dom-snapshot') as HTMLIFrameElement;
  await act(() => new Promise((r) => setTimeout(r, 30)));   // jsdom's own about:blank load event comes first
  // jsdom does not load srcdoc: put the rebuilt document into the frame the way the browser would, then fire load
  const doc = frame.contentDocument!;
  doc.open(); doc.write(frame.getAttribute('srcdoc')!); doc.close();
  fireEvent.load(frame);
  await act(() => new Promise((r) => setTimeout(r, 30)));   // and the load jsdom fires for document.close()
  fireEvent.click(screen.getByText(/Pick locator/));
  return { view, frame, doc };
}

function hover(doc: Document, el: Element) {
  (el as HTMLElement).getBoundingClientRect = () => ({ left: 10, top: 20, right: 70, bottom: 40, width: 60, height: 20, x: 10, y: 20, toJSON() {} }) as DOMRect;
  (doc as unknown as { elementFromPoint: unknown }).elementFromPoint = () => el;
  act(() => { doc.dispatchEvent(new MouseEvent('mousemove', { clientX: 3, clientY: 3, bubbles: true })); });
}

const rankOk = (body: unknown) => vi.fn(async (url: string) => {
  if (String(url).includes('action=tid')) return new Response(JSON.stringify({ testIdAttribute: 'data-testid' }));
  return new Response(JSON.stringify(body), { status: 200 });
}) as unknown as typeof fetch;

describe('snapshot pick locator', () => {
  it('stays sandboxed without scripts; hover shows the server line for a strict pick, click keeps it, send to editor', async () => {
    const { frame, doc } = await mountPicker();
    expect(frame.getAttribute('sandbox')).toBe('allow-same-origin');
    const go = doc.getElementById('go')!;
    let posted: { cand: Array<{ s: string; n: number }>; sel: unknown } | null = null;
    globalThis.fetch = vi.fn(async (url: string, init?: RequestInit) => {
      if (String(url).includes('action=tid')) return new Response(JSON.stringify({ testIdAttribute: 'data-testid' }));
      posted = JSON.parse(String(init!.body));
      const idx = posted!.cand.findIndex((c) => c.s === 'role');
      return new Response(JSON.stringify({ locator: 'role@button[name=Go]', line: 'click role@button[name=Go]', strict: true, source: idx,
        candidates: posted!.cand.map((c) => ({ kind: c.s, locator: c.s === 'role' ? 'role@button[name=Go]' : null, n: c.n })), fallback: null }));
    }) as unknown as typeof fetch;
    hover(doc, go);
    expect(screen.getByTestId('pick-outline')).toBeTruthy();
    expect(doc.querySelector('[data-testid="pick-outline"], .pick-outline')).toBeNull();          // nothing drawn inside the frame
    await waitFor(() => expect(screen.getByTestId('pick-line').textContent).toBe('click role@button[name=Go]'));
    expect(posted!.cand.find((c) => c.s === 'role')!.n).toBe(1);                                 // counted in the snapshot DOM
    const click = new MouseEvent('click', { bubbles: true, cancelable: true, clientX: 3, clientY: 3 });
    act(() => { go.dispatchEvent(click); });
    expect(click.defaultPrevented).toBe(true);
    expect(screen.getByText('Pick another')).toBeTruthy();
    fireEvent.click(screen.getByText('Send to editor'));
    expect(pickStore.get()).toEqual({ line: 'click role@button[name=Go]', origin: expect.stringMatching(/^trace snapshot #/) });
  });

  it('a server choice that is not unique in this snapshot is not offered for paste', async () => {
    const { doc } = await mountPicker();
    globalThis.fetch = rankOk({ locator: 'role@button[name=Go]', line: 'click role@button[name=Go]', strict: true, source: 5,
      candidates: [], fallback: null });
    hover(doc, doc.getElementById('go')!);
    await waitFor(() => expect(screen.getByText(/No locator resolves strictly here/)).toBeTruthy());
    expect(screen.queryByText('Send to editor')).toBeNull();
  });

  it('server down: candidates with counts, paste disabled, nothing thrown', async () => {
    const { doc } = await mountPicker();
    globalThis.fetch = vi.fn(async () => { throw new TypeError('Failed to fetch'); }) as unknown as typeof fetch;
    hover(doc, doc.getElementById('go')!);
    await waitFor(() => expect(screen.getByText(/Ranking unavailable/)).toBeTruthy());
    expect(screen.queryByText('Send to editor')).toBeNull();
    expect(screen.queryByTestId('pick-line')).toBeNull();
    expect(screen.getByTestId('pick-cands').textContent).toMatch(/1 match/);
  });

  it('lite snapshot: the picker works on the sanitized DOM and says so', async () => {
    const files: Record<string, Uint8Array> = {
      'snapshots/snapshot-1.html': strToU8('<html><body><button id="old">Old</button><script>x()</script></body></html>'),
      'snapshots.ndjson': strToU8(JSON.stringify({ n: 1, ts: 1, label: 'old', html: 'snapshots/snapshot-1.html', screenshot: null }) + '\n'),
    };
    const { doc } = await mountPicker(parseFiles(files));
    expect(screen.getByText(/Lite snapshot: picking on the sanitized outerHTML/)).toBeTruthy();
    globalThis.fetch = rankOk({ locator: 'role@button[name=Old]', line: 'click role@button[name=Old]', strict: true, source: 0,
      candidates: [{ kind: 'role', locator: 'role@button[name=Old]', n: 1 }], fallback: { locator: 'id@old', n: 1 } });
    hover(doc, doc.getElementById('old')!);
    await waitFor(() => expect(screen.getByTestId('pick-line').textContent).toBe('click role@button[name=Old]'));
  });

  it('toggling off removes every listener from the frame document and the outline', async () => {
    const { doc } = await mountPicker();
    const removed = vi.spyOn(doc, 'removeEventListener');
    globalThis.fetch = rankOk({ locator: null, line: null, strict: false, source: null, candidates: [], fallback: null });
    hover(doc, doc.getElementById('go')!);
    fireEvent.click(screen.getByText(/Pick locator/));
    expect(screen.queryByTestId('pick-outline')).toBeNull();
    expect(screen.queryByTestId('pick-panel')).toBeNull();
    expect(removed.mock.calls.map((c) => c[0]).sort()).toEqual(['click', 'keydown', 'mouseleave', 'mousemove', 'scroll']);
    const click = new MouseEvent('click', { bubbles: true, cancelable: true });
    doc.getElementById('go')!.dispatchEvent(click);
    expect(click.defaultPrevented).toBe(false);
  });
});
