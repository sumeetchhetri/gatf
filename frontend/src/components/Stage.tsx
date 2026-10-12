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
import { useEffect, useMemo, useRef, useState, type RefObject } from 'react';
import type { Trace, SnapshotRef } from '../trace/types';
import { actionSnapshots } from '../trace/loadTrace';
import { rebuildSnapshot } from '../trace/rebuild';

export type StageMode = 'video' | 'dom' | 'screenshot';

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

/** H2 (Δ147): the snapshot an action shows for a phase: its before/after pair when the trace has one (format 2). */
export function phaseSnapshot(trace: Trace, snap: SnapshotRef | null, phase: 'before' | 'after'): SnapshotRef | null {
  if (!snap || snap.action == null) return snap;
  const pair = actionSnapshots(trace.snapshots, snap.action);
  return (phase === 'after' ? pair.after : pair.before) ?? snap;
}

export function DomView({ trace, snap }: { trace: Trace; snap: SnapshotRef | null }) {
  const iframeRef = useRef<HTMLIFrameElement | null>(null);
  const [phase, setPhase] = useState<'before' | 'after'>(snap?.phase ?? 'before');
  // a newly active snapshot shows its own phase; the toggle then flips within the same action
  useEffect(() => setPhase(snap?.phase ?? 'before'), [snap?.action, snap?.phase, snap?.n]);
  const pair = snap?.action != null ? actionSnapshots(trace.snapshots, snap.action) : null;
  const shown = phaseSnapshot(trace, snap, phase);
  const built = useMemo(() => (shown ? rebuildSnapshot(trace, shown, { highlight: snap?.action ?? null }) : null), [trace, shown, snap?.action]);
  useEffect(() => () => built?.urls.forEach((u) => URL.revokeObjectURL(u)), [built]);

  // the frame runs no script; the viewer (same origin via allow-same-origin) restores scroll and reveals the target
  const onLoad = () => {
    try {
      const w = iframeRef.current?.contentWindow;
      const d = iframeRef.current?.contentDocument;
      if (!w || !d || !built) return;
      const sc = built.manifest?.scroll;
      if (sc && (sc.x || sc.y)) w.scrollTo(sc.x, sc.y);
      if (snap?.action != null) {
        const hit = (d.querySelector(`[data-gatf-target~="${snap.action}"]`) as HTMLElement | null) ?? (d.querySelector('[data-gatf-rect]') as HTMLElement | null);
        const r = hit?.getBoundingClientRect();
        if (hit && r && (r.bottom < 0 || r.top > w.innerHeight || r.right < 0 || r.left > w.innerWidth)) {
          hit.scrollIntoView({ block: 'center', inline: 'center' });
        }
      }
    } catch {
      /* best-effort */
    }
  };

  if (!built) return <div className="stage-empty">No DOM snapshot at this point.</div>;
  const st = built.stats;
  return (
    <div className="dom-wrap">
      {pair && (
        <div className="dom-phase">
          <span className="seg">
            <button className={'seg-btn' + (phase === 'before' ? ' on' : '')} disabled={!pair.before} onClick={() => setPhase('before')}>
              Before
            </button>
            <button className={'seg-btn' + (phase === 'after' ? ' on' : '')} disabled={!pair.after} onClick={() => setPhase('after')}>
              After
            </button>
          </span>
          <span className="muted small">action #{snap!.action}{shown?.label ? ` — ${shown.label}` : ''}</span>
        </div>
      )}
      <iframe
        ref={iframeRef}
        className="dom-frame"
        sandbox="allow-same-origin"
        srcDoc={built.html}
        title="dom-snapshot"
        onLoad={onLoad}
      />
      <div className="dom-note muted small">
        {st.styled ? (
          <>
            Styled snapshot ({built.manifest?.source === 'cdp' ? 'DOMSnapshot' : 'serialized DOM'}), {st.sheets} stylesheet
            {st.sheets === 1 ? '' : 's'} from the trace{built.manifest?.truncated ? '; truncated at the size cap' : ''}.
          </>
        ) : (
          <>Lite snapshot (outerHTML){st.sheets ? `, ${st.sheets} stylesheet${st.sheets === 1 ? '' : 's'} from captured network bodies` : ''}. Use the Screenshot view for pixel-accurate state.</>
        )}
        {(st.missingImages > 0 || st.missingSheets > 0) && (
          <>
            {' '}
            {st.missingImages > 0 && `${st.missingImages} image${st.missingImages === 1 ? '' : 's'}`}
            {st.missingImages > 0 && st.missingSheets > 0 && ' and '}
            {st.missingSheets > 0 && `${st.missingSheets} stylesheet${st.missingSheets === 1 ? '' : 's'}`} not in the trace
            (turn on traceNetwork with response bodies to capture them).
          </>
        )}
      </div>
    </div>
  );
}
