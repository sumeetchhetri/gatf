import { useCallback, useRef } from 'react';
import type { Trace } from '../trace/types';
import { fmtOffset, fmtClock, clamp } from '../util';

function markerClass(type: string): string {
  switch (type) {
    case 'action':
      return 'mk-action';
    case 'navigation':
      return 'mk-nav';
    case 'lifecycle':
      return 'mk-life';
    default:
      return 'mk-other';
  }
}

export function Timeline({
  trace,
  currentTime,
  setCurrentTime,
  playing,
  togglePlay,
}: {
  trace: Trace;
  currentTime: number;
  setCurrentTime: (t: number, fromUser: boolean) => void;
  playing: boolean;
  togglePlay: () => void;
}) {
  const trackRef = useRef<HTMLDivElement | null>(null);
  const dragging = useRef(false);
  const span = Math.max(1, trace.t1 - trace.t0);

  const pctOf = (ts: number) => (clamp(ts, trace.t0, trace.t1) - trace.t0) / span;

  const seekFromClientX = useCallback(
    (clientX: number) => {
      const el = trackRef.current;
      if (!el) return;
      const r = el.getBoundingClientRect();
      const frac = clamp((clientX - r.left) / r.width, 0, 1);
      setCurrentTime(trace.t0 + frac * span, true);
    },
    [setCurrentTime, span, trace.t0],
  );

  const onDown = (e: React.PointerEvent) => {
    dragging.current = true;
    (e.target as Element).setPointerCapture?.(e.pointerId);
    seekFromClientX(e.clientX);
  };
  const onMove = (e: React.PointerEvent) => {
    if (dragging.current) seekFromClientX(e.clientX);
  };
  const onUp = (e: React.PointerEvent) => {
    dragging.current = false;
    (e.target as Element).releasePointerCapture?.(e.pointerId);
  };

  const playhead = pctOf(currentTime) * 100;

  return (
    <div className="timeline">
      <div className="tl-controls">
        <button className="play-btn" onClick={togglePlay} title={playing ? 'Pause' : 'Play'}>
          {playing ? '❚❚' : '▶'}
        </button>
        <span className="tl-time mono">+{fmtOffset(currentTime - trace.t0)}</span>
        <span className="tl-sep">/</span>
        <span className="tl-time mono muted">{fmtOffset(span)}</span>
        <span className="tl-clock mono muted" title="wall-clock time">
          {fmtClock(currentTime)}
        </span>
      </div>
      <div
        className="tl-track"
        ref={trackRef}
        onPointerDown={onDown}
        onPointerMove={onMove}
        onPointerUp={onUp}
      >
        {/* event markers */}
        {trace.events.map((e, i) => (
          <div
            key={'e' + i}
            className={'tl-mark ' + markerClass(e.type)}
            style={{ left: pctOf(e.ts) * 100 + '%' }}
            title={`${e.type}: ${e.name ?? ''}`}
          />
        ))}
        {/* snapshot ticks on a lower band */}
        {trace.snapshots.map((s, i) => (
          <div
            key={'s' + i}
            className="tl-snap"
            style={{ left: pctOf(s.ts) * 100 + '%' }}
            title={`snapshot #${s.n}${s.label ? ' — ' + s.label : ''}`}
          />
        ))}
        <div className="tl-progress" style={{ width: playhead + '%' }} />
        <div className="tl-playhead" style={{ left: playhead + '%' }} />
      </div>
      <div className="tl-legend muted small">
        <span className="lg"><i className="mk-action" /> action</span>
        <span className="lg"><i className="mk-nav" /> navigation</span>
        <span className="lg"><i className="mk-life" /> lifecycle</span>
        <span className="lg"><i className="tl-snap-legend" /> snapshot</span>
      </div>
    </div>
  );
}
