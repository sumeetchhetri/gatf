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
// Δ148: the editor's live-browser picker — opt-in, only with an active recording session, one pick, and always
// disarmed afterwards (pick, cancel, Escape in the page, the recording ending, or the editor unmounting).
import { describe, it, expect, vi, afterEach } from 'vitest';
import { LivePicker, type LiveApi, type LiveState } from './livePicker';
import type { RecordPick, RecordPicked } from '../api';

afterEach(() => { vi.useRealTimers(); });

const pick = (seq: number, over: Partial<RecordPick> = {}): RecordPick => ({
  seq, cancelled: false, locator: 'role@button[name=Save]', line: 'click role@button[name=Save]', strict: true, source: 0,
  candidates: [{ kind: 'role', locator: 'role@button[name=Save]', n: 1 }], fallback: null, ...over,
});

function fakeApi(sessions = [{ sessionId: 's1', browser: 'chrome' }]) {
  let picked: RecordPicked = { running: true, armed: false, pick: pick(4) };   // an older pick must not be pasted
  const arms: boolean[] = [];
  const api: LiveApi = {
    recordList: vi.fn(async () => ({ sessions })),
    recordPick: vi.fn(async (_s: string, on: boolean) => { arms.push(on); picked = { ...picked, armed: on }; return { armed: on }; }),
    recordPicked: vi.fn(async () => picked),
  };
  return { api, arms, set: (p: Partial<RecordPicked>) => { picked = { ...picked, ...p }; } };
}

describe('LivePicker', () => {
  it('without a recording session it says so and arms nothing', async () => {
    const f = fakeApi([]);
    const states: LiveState[] = [];
    const lp = new LivePicker(f.api, vi.fn(), (s) => states.push(s));
    await lp.start();
    expect(states.at(-1)).toMatchObject({ phase: 'idle', message: expect.stringMatching(/No recording session/) });
    expect(f.arms).toEqual([]);
  });

  it('arms, pastes the server line of a new strict pick once, then disarms', async () => {
    vi.useFakeTimers();
    const f = fakeApi();
    const onLine = vi.fn();
    const lp = new LivePicker(f.api, onLine, () => {}, 100);
    await lp.start();
    expect(lp.state).toMatchObject({ phase: 'armed', sessionId: 's1' });
    await vi.advanceTimersByTimeAsync(250);
    expect(onLine).not.toHaveBeenCalled();                       // seq 4 was there before arming
    f.set({ pick: pick(5), armed: false });
    await vi.advanceTimersByTimeAsync(100);
    expect(onLine).toHaveBeenCalledTimes(1);
    expect(onLine).toHaveBeenCalledWith('click role@button[name=Save]');
    expect(lp.state.phase).toBe('idle');
    expect(f.arms).toEqual([true, false]);
    await vi.advanceTimersByTimeAsync(500);
    expect(onLine).toHaveBeenCalledTimes(1);                     // polling stopped
  });

  it('a pick that is not strict pastes nothing and keeps the candidates for display', async () => {
    vi.useFakeTimers();
    const f = fakeApi();
    const onLine = vi.fn();
    const lp = new LivePicker(f.api, onLine, () => {}, 100);
    await lp.start();
    f.set({ pick: pick(5, { candidates: [{ kind: 'role', locator: 'role@button[name=Save]', n: 2 }] }) });
    await vi.advanceTimersByTimeAsync(100);
    expect(onLine).not.toHaveBeenCalled();
    expect(lp.state).toMatchObject({ phase: 'idle', message: expect.stringMatching(/strictly/), pick: { seq: 5 } });
  });

  it('Escape in the page, the recording ending, cancel and unmount all disarm', async () => {
    vi.useFakeTimers();
    let f = fakeApi();
    let lp = new LivePicker(f.api, vi.fn(), () => {}, 100);
    await lp.start();
    f.set({ pick: pick(5, { cancelled: true, locator: null, line: null, source: null }) });
    await vi.advanceTimersByTimeAsync(100);
    expect(lp.state).toMatchObject({ phase: 'idle', message: 'Pick cancelled.' });
    expect(f.arms.at(-1)).toBe(false);

    f = fakeApi();
    lp = new LivePicker(f.api, vi.fn(), () => {}, 100);
    await lp.start();
    f.set({ running: false });
    await vi.advanceTimersByTimeAsync(100);
    expect(lp.state).toMatchObject({ phase: 'idle', message: 'The recording ended.' });

    f = fakeApi();
    lp = new LivePicker(f.api, vi.fn(), () => {}, 100);
    await lp.start();
    lp.cancel();
    expect(f.arms).toEqual([true, false]);

    f = fakeApi();
    const onState = vi.fn();
    lp = new LivePicker(f.api, vi.fn(), onState, 100);
    await lp.start();
    const n = onState.mock.calls.length;
    lp.dispose();
    await vi.advanceTimersByTimeAsync(500);
    expect(f.arms).toEqual([true, false]);
    expect(onState.mock.calls.length).toBe(n);
    expect(f.api.recordPicked).toHaveBeenCalledTimes(1);       // only the baseline read: no polling after dispose
  });

  it('a failing server never throws out of start()', async () => {
    const api: LiveApi = { recordList: async () => { throw new Error('down'); }, recordPick: vi.fn(), recordPicked: vi.fn() };
    const lp = new LivePicker(api, vi.fn(), () => {});
    await expect(lp.start()).resolves.toBeUndefined();
    expect(lp.state).toMatchObject({ phase: 'idle', message: expect.stringMatching(/unavailable/) });
  });
});
