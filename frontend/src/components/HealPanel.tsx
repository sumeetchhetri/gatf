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
// H1 (Δ150): the healer in the editor. "Heal" asks the server for proposals for the newest failing traced run of this
// script; a heal started in the Trace Viewer arrives through healStore. Each proposal shows as a diff with its rank,
// source (deterministic / ai) and strict match count, and Accept / Reject. Nothing is written without Accept, and
// Accept writes only what the server validated; the editor buffer then takes the server's new content.
import { useState } from 'react';
import { api } from '../api';
import { applyMessage, diffRows, healStore, healableTraceUrl, strictText, useHeal, type HealResult } from '../editor/heal';

export function HealBar({
  path,
  dirty,
  onApplied,
}: {
  path: string;
  dirty: boolean;
  /** the file's new content after an accepted fix (the server wrote exactly this), and the healed line */
  onApplied: (content: string, line: number) => void;
}) {
  const heal = useHeal();
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);
  const [rejected, setRejected] = useState<Set<number>>(new Set());
  const mine = heal && heal.file === path ? heal : null;

  const start = () => {
    setBusy(true);
    setMsg(null);
    api
      .heal({ file: path })
      .then((r) => {
        if (r.error) setMsg('Heal: ' + r.error);
        else {
          setRejected(new Set());
          healStore.set(r);
        }
      })
      .catch((e: Error) => setMsg('Heal failed: ' + e.message))
      .finally(() => setBusy(false));
  };

  const accept = (h: HealResult, rank: number) => {
    if (!h.healId) return;
    setBusy(true);
    setMsg(null);
    api
      .healApply(h.healId, rank)
      .then((r) => {
        if (r.status === 'applied' && typeof r.content === 'string') {
          healStore.set(null);
          setRejected(new Set());
          setMsg('Applied proposal #' + rank + ' to line ' + (r.line ?? h.line) + ' (validated).');
          onApplied(r.content, r.line ?? h.line);
        } else {
          setMsg(applyMessage(r));
        }
      })
      .catch((e: Error) => setMsg('Accept failed: ' + e.message))
      .finally(() => setBusy(false));
  };

  const shown = mine ? mine.proposals.filter((p) => !rejected.has(p.rank)) : [];
  return (
    <div className="heal-bar small" data-testid="heal-bar">
      <div className="heal-head">
        <button className="btn ghost small" disabled={busy} onClick={start} title="Propose fixes for the failed step of the newest traced run of this script">
          🩹 Heal last failure
        </button>
        {msg && <span className="muted heal-msg">{msg}</span>}
        {mine && (
          <>
            <span className="muted">
              line {mine.line}: <code>{mine.step}</code>
            </span>
            <span className="muted heal-ai" title="AI status">AI: {mine.aiStatus}</span>
            <button className="btn ghost small" onClick={() => { healStore.set(null); setRejected(new Set()); }} title="Close (writes nothing)">
              ×
            </button>
          </>
        )}
      </div>
      {mine && mine.status !== 'proposed' && <div className="muted">No proposal ({mine.status}){mine.reason ? ': ' + mine.reason : ''}</div>}
      {mine && mine.status === 'proposed' && shown.length === 0 && <div className="muted">All proposals rejected. Nothing was written.</div>}
      {mine && dirty && shown.length > 0 && <div className="muted">Save or discard your edits before accepting a fix.</div>}
      {mine && shown.map((p) => (
        <div key={p.rank} className="heal-prop" data-testid={'heal-prop-' + p.rank}>
          <div className="heal-prop-head">
            <b>#{p.rank}</b> <span className={'badge ' + (p.source === 'ai' ? 'warn' : 'ok')}>{p.source}</span> <span>{p.kind}</span>{' '}
            {strictText(p) && <span className="muted">{strictText(p)}</span>} <span className="muted">· {p.why}</span>
            <button className="btn small primary" disabled={busy || dirty || !mine.healId} onClick={() => accept(mine, p.rank)}
              title={!mine.healId ? 'This heal has no script to apply to' : dirty ? 'Save or discard your edits first' : 'Write this fix (after it validates)'}>
              Accept
            </button>
            <button className="btn ghost small" disabled={busy} onClick={() => setRejected((s) => new Set(s).add(p.rank))}>
              Reject
            </button>
          </div>
          <pre className="heal-diff">
            {diffRows(p.diff).map((r, i) => (
              <div key={i} className={'heal-row heal-row-' + (r.t === '+' ? 'add' : r.t === '-' ? 'del' : r.t === '@' ? 'hunk' : 'ctx')}>
                {(r.t === '@' ? '' : r.t + ' ') + r.text}
              </div>
            ))}
          </pre>
        </div>
      ))}
    </div>
  );
}

/** The Trace Viewer's Heal: for a failed trace served from the reports directory. */
export function TraceHealButton({ traceUrl, onHeal }: { traceUrl: string | null | undefined; onHeal: (r: HealResult) => void }) {
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);
  const url = healableTraceUrl(traceUrl);
  if (!url) return null;
  const go = () => {
    setBusy(true);
    setMsg(null);
    api
      .heal({ trace: url })
      .then((r) => {
        if (r.error) setMsg(r.error);
        else onHeal(r);
      })
      .catch((e: Error) => setMsg(e.message))
      .finally(() => setBusy(false));
  };
  return (
    <div className="heal-trace small">
      <button className="btn small" disabled={busy} onClick={go} title="Propose fixes for the failed step and open them in the editor">
        🩹 Heal failed step
      </button>
      {msg && <span className="muted">Heal: {msg}</span>}
    </div>
  );
}
