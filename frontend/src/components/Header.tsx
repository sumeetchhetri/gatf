import type { Trace } from '../trace/types';
import { fmtBytes, fmtOffset } from '../util';

export function Header({ trace, onClose }: { trace: Trace; onClose: () => void }) {
  const m = trace.meta;
  const dur = trace.t1 - trace.t0;
  const recs: Array<[string, boolean]> = [
    ['network', m.recNetwork],
    ['console', m.recConsole],
    ['snapshots', m.recSnapshots],
    ['metrics', m.recMetrics],
    ['cpu', m.recCpu],
    ['coverage', m.recCoverage],
    ['video', m.recVideo],
  ];
  return (
    <div className="hdr">
      <div className="hdr-main">
        <div className="hdr-title-lg">
          {m.selFile ? m.selFile.split('/').pop() : m.runId}
          <span className="hdr-sub mono">{m.runId}</span>
        </div>
        <div className="hdr-facts mono">
          <span>{m.browser || 'browser?'}</span>
          <span className="dot">·</span>
          <span title="transport protocol">{m.protocol || 'cdp'}</span>
          <span className="dot">·</span>
          <span title="trace duration">{fmtOffset(dur)}</span>
          {m.truncatedBodies > 0 && (
            <>
              <span className="dot">·</span>
              <span className="warn-badge" title="response bodies dropped over the size budget">
                {m.truncatedBodies} bodies truncated
              </span>
            </>
          )}
          {m.bytesCaptured > 0 && (
            <>
              <span className="dot">·</span>
              <span title="captured response-body bytes">{fmtBytes(m.bytesCaptured)} captured</span>
            </>
          )}
        </div>
      </div>
      <div className="hdr-right">
        <div className="rec-badges">
          {recs.map(([name, on]) => (
            <span key={name} className={'rec' + (on ? ' on' : '')}>
              {name}
            </span>
          ))}
        </div>
        <button className="btn ghost" onClick={onClose} title="Load a different trace">
          Close
        </button>
      </div>
    </div>
  );
}
