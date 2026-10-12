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
import { useMemo } from 'react';
import type { Trace } from '../trace/types';
import { fmtOffset } from '../util';

function typeClass(t: string): string {
  switch (t) {
    case 'action':
      return 'ev-action';
    case 'navigation':
      return 'ev-nav';
    case 'snapshot':
      return 'ev-snap';
    case 'lifecycle':
      return 'ev-life';
    default:
      return 'ev-other';
  }
}

export function EventsTab({
  trace,
  currentTime,
  onSeek,
}: {
  trace: Trace;
  currentTime: number;
  onSeek: (ts: number) => void;
}) {
  // H2 (Δ147): an action with a before-snapshot seeks to it, so the DOM view shows that action's state
  const beforeTs = useMemo(() => {
    const m = new Map<number, number>();
    for (const s of trace.snapshots) if (s.action != null && s.phase === 'before' && !m.has(s.action)) m.set(s.action, s.ts);
    return m;
  }, [trace.snapshots]);
  if (trace.events.length === 0) return <div className="empty-tab">No events in this trace.</div>;
  return (
    <div className="events-list">
      {trace.events.map((e, i) => (
        <div
          key={i}
          className={'event-row ' + typeClass(e.type) + (e.ts > currentTime ? ' future' : '')}
          onClick={() => onSeek(e.a != null && beforeTs.has(e.a) ? Math.max(e.ts, beforeTs.get(e.a)!) : e.ts)}
          title={e.a != null && beforeTs.has(e.a) ? 'Seek to this action (DOM before/after)' : 'Seek to this event'}
        >
          <span className="e-time mono">+{fmtOffset(e.ts - trace.t0)}</span>
          <span className={'e-type ' + typeClass(e.type)}>{e.type}</span>
          <span className="e-name">{e.name}</span>
          {e.a != null && beforeTs.has(e.a) && <span className="e-snap" aria-label="DOM snapshot">◫</span>}
          {e.detail && <span className="e-detail mono ellipsis">{e.detail}</span>}
        </div>
      ))}
    </div>
  );
}
