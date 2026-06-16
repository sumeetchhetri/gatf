import { useEffect, useMemo, useRef, type RefObject } from 'react';
import type { Trace, SnapshotRef } from '../trace/types';

export type StageMode = 'video' | 'dom' | 'screenshot';

const dec = new TextDecoder('utf-8');

export function Stage({
  trace,
  activeSnap,
  mode,
  setMode,
  videoRef,
}: {
  trace: Trace;
  activeSnap: SnapshotRef | null;
  mode: StageMode;
  setMode: (m: StageMode) => void;
  videoRef: RefObject<HTMLVideoElement>;
}) {
  const hasVideo = !!trace.videoUrl;
  const hasSnaps = trace.snapshots.length > 0;

  // available modes based on what the bundle actually carries
  const modes: Array<[StageMode, string, boolean]> = [
    ['video', 'Video', hasVideo],
    ['dom', 'DOM', hasSnaps],
    ['screenshot', 'Screenshot', hasSnaps],
  ];

  return (
    <div className="stage">
      <div className="stage-toolbar">
        {modes.map(([m, label, enabled]) => (
          <button
            key={m}
            className={'seg' + (mode === m ? ' on' : '')}
            disabled={!enabled}
            onClick={() => setMode(m)}
          >
            {label}
          </button>
        ))}
        {activeSnap && (mode === 'dom' || mode === 'screenshot') && (
          <span className="muted small stage-label">
            snapshot #{activeSnap.n}
            {activeSnap.label ? ` — ${activeSnap.label}` : ''}
          </span>
        )}
      </div>
      <div className="stage-body">
        {mode === 'video' && hasVideo && (
          <video ref={videoRef} className="stage-video" src={trace.videoUrl!} muted playsInline preload="auto" />
        )}
        {mode === 'screenshot' && <ScreenshotView trace={trace} snap={activeSnap} />}
        {mode === 'dom' && <DomView trace={trace} snap={activeSnap} />}
        {mode === 'video' && !hasVideo && <div className="stage-empty">No video in this trace.</div>}
      </div>
    </div>
  );
}

function ScreenshotView({ trace, snap }: { trace: Trace; snap: SnapshotRef | null }) {
  const url = useMemo(() => {
    if (!snap?.screenshot) return null;
    const b = trace.files[snap.screenshot];
    if (!b) return null;
    return URL.createObjectURL(new Blob([b.slice()], { type: 'image/png' }));
  }, [trace, snap]);
  useEffect(() => () => { if (url) URL.revokeObjectURL(url); }, [url]);
  if (!url) return <div className="stage-empty">No screenshot at this point.</div>;
  return <img className="stage-shot" src={url} alt="screenshot" />;
}

function DomView({ trace, snap }: { trace: Trace; snap: SnapshotRef | null }) {
  const iframeRef = useRef<HTMLIFrameElement | null>(null);
  const html = useMemo(() => {
    if (!snap?.html) return null;
    const b = trace.files[snap.html];
    return b ? dec.decode(b) : null;
  }, [trace, snap]);

  if (!html) return <div className="stage-empty">No DOM snapshot at this point.</div>;
  return (
    <div className="dom-wrap">
      <iframe
        ref={iframeRef}
        className="dom-frame"
        sandbox="allow-same-origin"
        srcDoc={html}
        title="dom-snapshot"
      />
      <div className="dom-note muted small">
        Lite snapshot (outerHTML). External CSS/images aren't captured in Phase-3 bundles, so styling
        may differ from the live page — use the Screenshot view for pixel-accurate state.
      </div>
    </div>
  );
}
