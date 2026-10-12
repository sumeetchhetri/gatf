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
// Δ148: pick a locator in the live browser of an active recording session, from the editor. Opt-in and one pick at a
// time: arm → the page shows an outline and swallows one click (or Escape) → the server ranks it (LocatorRanking) and
// builds the line → it is pasted. The page script removes its own listeners and outline after the pick; disarming
// (cancel, unmount, or the recording ending) also tells the server to clear it.
import type { RecordPicked, RecordPick } from '../api';
import { strictLineFromReply } from '../trace/picker';

export interface LiveApi {
  recordList(): Promise<{ sessions: Array<{ sessionId: string; browser: string }> }>;
  recordPick(sessionId: string, on: boolean): Promise<{ armed?: boolean; error?: string }>;
  recordPicked(sessionId: string): Promise<RecordPicked>;
}

export type LiveState =
  | { phase: 'idle'; message?: string; pick?: RecordPick }
  | { phase: 'arming' }
  | { phase: 'armed'; sessionId: string; browser: string };

export class LivePicker {
  private timer: ReturnType<typeof setInterval> | null = null;
  private sid: string | null = null;
  private baseline = 0;
  private busy = false;
  private disposed = false;
  state: LiveState = { phase: 'idle' };

  constructor(
    private readonly api: LiveApi,
    private readonly onLine: (line: string) => void,
    private readonly onState: (s: LiveState) => void,
    private readonly pollMs = 600,
  ) {}

  private set(s: LiveState): void {
    this.state = s;
    if (!this.disposed) this.onState(s);
  }

  async start(): Promise<void> {
    if (this.state.phase !== 'idle') return;
    this.set({ phase: 'arming' });
    try {
      const { sessions } = await this.api.recordList();
      const s = sessions?.[0];
      if (!s) { this.set({ phase: 'idle', message: 'No recording session is active: start one in Record, then pick.' }); return; }
      const before = await this.api.recordPicked(s.sessionId);
      this.baseline = before?.pick?.seq ?? 0;
      const r = await this.api.recordPick(s.sessionId, true);
      if (r.error || !r.armed) { this.set({ phase: 'idle', message: r.error || 'the picker could not be armed' }); return; }
      if (this.disposed) { void this.api.recordPick(s.sessionId, false).catch(() => {}); return; }
      this.sid = s.sessionId;
      this.set({ phase: 'armed', sessionId: s.sessionId, browser: s.browser });
      this.timer = setInterval(() => void this.poll(), this.pollMs);
    } catch (e) {
      this.set({ phase: 'idle', message: 'live picker unavailable: ' + String((e as Error)?.message ?? e) });
    }
  }

  private async poll(): Promise<void> {
    if (this.busy || !this.sid) return;
    this.busy = true;
    try {
      const p = await this.api.recordPicked(this.sid);
      if (!p.running) { this.finish('The recording ended.'); return; }
      const pick = p.pick;
      if (pick && pick.seq > this.baseline) {
        if (pick.cancelled) { this.finish('Pick cancelled.'); return; }
        const line = strictLineFromReply(pick);
        if (line) { this.onLine(line); this.finish(undefined, pick); }
        else this.finish('No locator matches that element strictly; candidates are listed below.', pick);
      } else if (!p.armed) {
        this.finish('The picker was disarmed.');
      }
    } catch {
      /* transient: keep polling */
    } finally {
      this.busy = false;
    }
  }

  private finish(message?: string, pick?: RecordPick): void {
    const sid = this.sid;
    this.stopTimer();
    if (sid) void this.api.recordPick(sid, false).catch(() => {});
    this.set({ phase: 'idle', message, pick });
  }

  cancel(): void {
    if (this.state.phase === 'idle') return;
    this.finish('Pick cancelled.');
  }

  /** On unmount: disarm, stop polling, report nothing more. */
  dispose(): void {
    const sid = this.sid;
    this.stopTimer();
    if (sid) void this.api.recordPick(sid, false).catch(() => {});
    this.disposed = true;
  }

  private stopTimer(): void {
    if (this.timer != null) clearInterval(this.timer);
    this.timer = null;
    this.sid = null;
  }
}
