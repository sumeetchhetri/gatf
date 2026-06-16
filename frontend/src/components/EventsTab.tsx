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
  if (trace.events.length === 0) return <div className="empty-tab">No events in this trace.</div>;
  return (
    <div className="events-list">
      {trace.events.map((e, i) => (
        <div
          key={i}
          className={'event-row ' + typeClass(e.type) + (e.ts > currentTime ? ' future' : '')}
          onClick={() => onSeek(e.ts)}
          title="Seek to this event"
        >
          <span className="e-time mono">+{fmtOffset(e.ts - trace.t0)}</span>
          <span className={'e-type ' + typeClass(e.type)}>{e.type}</span>
          <span className="e-name">{e.name}</span>
          {e.detail && <span className="e-detail mono ellipsis">{e.detail}</span>}
        </div>
      ))}
    </div>
  );
}
