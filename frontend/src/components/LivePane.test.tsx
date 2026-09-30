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
import { render, screen } from '@testing-library/react';
import LivePane from './LivePane';

afterEach(() => {
  vi.unstubAllGlobals();
  vi.useRealTimers();
});

describe('<LivePane> (M15)', () => {
  it('shows the newest frame and asks only for newer ones', async () => {
    const urls: string[] = [];
    let n = 0;
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string) => {
        urls.push(url);
        n++;
        if (n === 1)
          return new Response(new Blob([new Uint8Array([1, 2])]), {
            status: 200,
            headers: { 'Content-Type': 'image/jpeg', 'X-Frame-Seq': '7', 'X-Frame-Ts': '5', 'X-Frame-Ended': 'false' },
          });
        return new Response(null, { status: 204 });
      }),
    );
    vi.stubGlobal('URL', Object.assign(URL, { createObjectURL: () => 'blob:frame-7', revokeObjectURL: () => {} }));
    render(<LivePane file="tests/ui/login.sel" active={true} />);
    const img = await screen.findByAltText('live browser screencast');
    expect(img.getAttribute('src')).toBe('blob:frame-7');
    expect(urls[0]).toBe('/api/live?file=tests%2Fui%2Flogin.sel&after=0');
    await vi.waitFor(() => expect(urls.length).toBeGreaterThan(1));
    expect(urls[1]).toContain('after=7');
  });

  it('explains how to get frames when none arrive', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    vi.stubGlobal('fetch', vi.fn(async () => new Response(null, { status: 204 })));
    render(<LivePane file="a.sel" active={true} />);
    expect(screen.getByText('Waiting for the browser…')).toBeInTheDocument();
    await vi.advanceTimersByTimeAsync(7000);
    expect(await screen.findByText(/traceVideo/)).toBeInTheDocument();
  });

  it("skips the previous run's ended frame", async () => {
    const urls: string[] = [];
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string) => {
        urls.push(url);
        return urls.length === 1
          ? new Response(new Blob([new Uint8Array([9])]), {
              status: 200,
              headers: { 'X-Frame-Seq': '4', 'X-Frame-Ts': '1', 'X-Frame-Ended': 'true' },
            })
          : new Response(null, { status: 204 });
      }),
    );
    render(<LivePane file="a.sel" active={true} />);
    await vi.waitFor(() => expect(urls.length).toBeGreaterThan(1));
    expect(urls[1]).toContain('after=4');
    expect(screen.queryByAltText('live browser screencast')).toBeNull();
    expect(screen.getByText('Waiting for the browser…')).toBeInTheDocument();
  });

  it('does not poll while inactive', () => {
    const f = vi.fn();
    vi.stubGlobal('fetch', f);
    render(<LivePane file="a.sel" active={false} />);
    expect(f).not.toHaveBeenCalled();
  });
});
