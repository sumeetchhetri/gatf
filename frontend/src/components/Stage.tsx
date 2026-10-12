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

/**
 * Δ147a: bundle path -> object URL, created on first use and revoked together. Owned by an effect (created there,
 * revoked in its cleanup), never by a memo, so StrictMode's mount/unmount/mount cannot revoke URLs still in use.
 */
export class UrlCache {
  private readonly urls = new Map<string, string>();
  private revoked = false;
  get = (path: string, mime: string, bytes: Uint8Array): string => {
    let u = this.urls.get(path);
    if (!u) {
      u = URL.createObjectURL(new Blob([bytes.slice()], { type: mime }));
      if (this.revoked) URL.revokeObjectURL(u);   // a late render after cleanup must not leak
      else this.urls.set(path, u);
    }
    return u;
  };
  revokeAll(): void {
    this.revoked = true;
    for (const u of this.urls.values()) URL.revokeObjectURL(u);
    this.urls.clear();
  }
}

export function DomView({ trace, snap }: { trace: Trace; snap: SnapshotRef | null }) {
  const iframeRef = useRef<HTMLIFrameElement | null>(null);
  // Δ147a: the phase is derived — the snapshot's own phase unless the user toggled within this same action — so a
  // step change renders once, with no effect-driven second build
  // Δ147b: the toggle belongs to the step the user toggled on (snap.n), not to the action: reaching the same action
  // again from another step (its other phase on the timeline) shows that step's own phase
  const [override, setOverride] = useState<{ n: number; phase: 'before' | 'after' } | null>(null);
  const phase: 'before' | 'after' = override && snap && override.n === snap.n ? override.phase : (snap?.phase ?? 'before');
  const setPhase = (p: 'before' | 'after') => { if (snap) setOverride({ n: snap.n, phase: p }); };
  const pair = snap?.action != null ? actionSnapshots(trace.snapshots, snap.action) : null;
  const shown = phaseSnapshot(trace, snap, phase);
  // one URL cache per trace, owned by an effect: revoked when the trace changes or the view unmounts
  const [cache, setCache] = useState<UrlCache | null>(null);
  useEffect(() => {
    const c = new UrlCache();
    setCache(c);
    return () => c.revokeAll();
  }, [trace]);
  const built = useMemo(
    () => (shown && cache ? rebuildSnapshot(trace, shown, { highlight: snap?.action ?? null, toUrl: cache.get }) : null),
    [trace, shown, snap?.action, cache],
  );

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

  if (shown && !cache) return <div className="stage-empty">Loading snapshot…</div>;
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
            {st.missingImages > 0 && `${st.missingImages} image${st.missingImages === 1 ? '' : 's'} or other resource${st.missingImages === 1 ? '' : 's'}`}
            {st.missingImages > 0 && st.missingSheets > 0 && ' and '}
            {st.missingSheets > 0 && `${st.missingSheets} stylesheet${st.missingSheets === 1 ? '' : 's'}`} not in the trace
            (turn on traceNetwork with response bodies to capture them).
          </>
        )}
      </div>
    </div>
  );
}
