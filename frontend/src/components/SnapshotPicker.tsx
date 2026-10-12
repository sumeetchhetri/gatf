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
// H2 (Δ148): the Trace Viewer's pick locator. Listeners go on the snapshot frame's document from this (parent) realm;
// the frame stays sandbox="allow-same-origin" with no allow-scripts, and nothing is inserted into it: the outline is a
// parent element laid over the frame. Hover shows the ranked locator; click locks it; Escape or "Pick another" unlocks.
import { useEffect, useMemo, useRef, useState, type RefObject } from 'react';
import { api } from '../api';
import {
  PickRanker, candidateText, collectPick, deepElementFromPoint, isViewerNode, matchText, outlineBox, type PickResult,
} from '../trace/picker';
import { pickStore } from '../editor/pickStore';

let tidPromise: Promise<string> | null = null;
function testIdAttribute(): Promise<string> {
  if (!tidPromise) {
    tidPromise = api.recordTestId().then((r) => r?.testIdAttribute || 'data-testid', () => 'data-testid');
  }
  return tidPromise;
}

export function SnapshotPicker({
  iframeRef,
  loadSeq,
  lite,
  origin,
}: {
  iframeRef: RefObject<HTMLIFrameElement | null>;
  /** bumps on every frame load: listeners are re-attached to the new document */
  loadSeq: number;
  lite: boolean;
  origin: string;
}) {
  const [box, setBox] = useState<{ left: number; top: number; width: number; height: number } | null>(null);
  const [result, setResult] = useState<PickResult | null>(null);
  const [locked, setLocked] = useState(false);
  const [copied, setCopied] = useState<string | null>(null);
  const lockedRef = useRef(false);
  const tidRef = useRef('data-testid');
  const ranker = useMemo(() => new PickRanker((raw) => api.rankLocator(raw)), []);

  useEffect(() => {
    void testIdAttribute().then((t) => { tidRef.current = t; });
  }, []);

  useEffect(() => {
    const f = iframeRef.current;
    let doc: Document | null = null;
    try { doc = f?.contentDocument ?? null; } catch { doc = null; }
    if (!f || !doc) return;
    const d = doc;
    let last: Element | null = null;
    const show = (el: Element | null) => setBox(el ? outlineBox(f, el) : null);
    const at = (ev: MouseEvent): Element | null => {
      const el = deepElementFromPoint(d, ev.clientX, ev.clientY);
      return el && !isViewerNode(el) && el !== d.documentElement ? el : null;
    };
    const rank = (el: Element, immediate: boolean) =>
      ranker.request(el, () => collectPick(d, el, tidRef.current), (r) => { if (immediate || !lockedRef.current) setResult(r); }, immediate);
    const onMove = (ev: MouseEvent) => {
      try {
        if (lockedRef.current) return;
        const el = at(ev);
        if (el === last) return;
        last = el;
        show(el);
        if (el) rank(el, false);
        else { ranker.cancel(); setResult(null); }
      } catch { /* never take the viewer down */ }
    };
    const onClick = (ev: MouseEvent) => {
      try {
        ev.preventDefault();
        ev.stopPropagation();
        const el = at(ev);
        if (!el) return;
        lockedRef.current = true;
        setLocked(true);
        last = el;
        show(el);
        rank(el, true);
      } catch { /* ignore */ }
    };
    const onScroll = () => { try { if (last) show(last); } catch { /* ignore */ } };
    const onLeave = () => { if (!lockedRef.current) { last = null; setBox(null); ranker.cancel(); } };
    const onKey = (ev: KeyboardEvent) => { if (ev.key === 'Escape') { lockedRef.current = false; setLocked(false); } };
    d.addEventListener('mousemove', onMove, true);
    d.addEventListener('click', onClick, true);
    d.addEventListener('scroll', onScroll, true);
    d.addEventListener('mouseleave', onLeave, true);
    d.addEventListener('keydown', onKey, true);
    return () => {
      d.removeEventListener('mousemove', onMove, true);
      d.removeEventListener('click', onClick, true);
      d.removeEventListener('scroll', onScroll, true);
      d.removeEventListener('mouseleave', onLeave, true);
      d.removeEventListener('keydown', onKey, true);
      ranker.cancel();
      lockedRef.current = false;
      setLocked(false);
      setBox(null);
      setResult(null);
    };
  }, [iframeRef, loadSeq, ranker]);

  const unlock = () => { lockedRef.current = false; setLocked(false); };
  const copy = async (line: string) => {
    try { await navigator.clipboard.writeText(line); setCopied(line); } catch { setCopied(null); }
  };

  return (
    <>
      {box && <div className="pick-outline" data-testid="pick-outline" style={{ left: box.left, top: box.top, width: box.width, height: box.height }} />}
      <div className="pick-panel small" data-testid="pick-panel">
        {lite && <div className="muted">Lite snapshot: picking on the sanitized outerHTML; visibility uses only the captured styles.</div>}
        {!result && <div className="muted">Hover an element to get its locator; click to keep it.</div>}
        {result && (
          <>
            <div className="pick-head">
              <code>{result.raw.tag}</code>
              {locked && <button className="btn ghost small" onClick={unlock}>Pick another</button>}
            </div>
            {result.line ? (
              <div className="pick-line">
                <code data-testid="pick-line">{result.line}</code>
                <button className="btn small" onClick={() => copy(result.line!)}>{copied === result.line ? 'Copied' : 'Copy'}</button>
                <button className="btn small primary" onClick={() => pickStore.set({ line: result.line!, origin })}>Send to editor</button>
              </div>
            ) : result.ranked ? (
              <div className="muted">No locator resolves strictly here (exactly one match); nothing to paste.</div>
            ) : (
              <div className="muted">Ranking unavailable ({result.error}); paste is disabled.</div>
            )}
            {!result.line && (
              <ul className="pick-cands" data-testid="pick-cands">
                {result.ranked
                  ? [...result.ranked.candidates.map((c, i) => <li key={i}><code>{c.locator ?? c.kind + ' (not expressible)'}</code> {matchText(c.n)}</li>),
                    ...(result.ranked.fallback ? [<li key="fb"><code>{result.ranked.fallback.locator}</code> {matchText(result.ranked.fallback.n)}</li>] : [])]
                  : [...result.raw.cand.map((c, i) => <li key={i}>{candidateText(c)}: {matchText(c.n)}</li>),
                    ...(result.raw.sel ? [<li key="fb">{candidateText(result.raw.sel)}: {matchText(result.raw.sel.n)}</li>] : [])]}
                {result.raw.cand.length === 0 && !result.raw.sel && <li className="muted">no candidate (element inside a shadow root with no semantic name?)</li>}
              </ul>
            )}
          </>
        )}
      </div>
    </>
  );
}
