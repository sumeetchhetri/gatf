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
import { useEffect, useRef, useState } from 'react';
import { api } from '../api';

type Phase = 'idle' | 'starting' | 'recording' | 'stopped' | 'saved';

export default function RecordView({ onOpenInEditor }: { onOpenInEditor: (path: string) => void }) {
  const [phase, setPhase] = useState<Phase>('idle');
  const [url, setUrl] = useState('https://');
  const [sid, setSid] = useState<string | null>(null);
  const [lines, setLines] = useState<string[]>([]);
  const [content, setContent] = useState('');
  const [fileName, setFileName] = useState('recorded.sel');
  const [saved, setSaved] = useState<string | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const pollRef = useRef<number | null>(null);

  const stopPoll = () => {
    if (pollRef.current != null) {
      window.clearInterval(pollRef.current);
      pollRef.current = null;
    }
  };
  useEffect(() => () => stopPoll(), []);

  const start = async () => {
    setErr(null);
    setLines([]);
    setSaved(null);
    setContent('');
    setPhase('starting');
    try {
      const r = await api.recordStart(url);
      if (r.error || !r.sessionId) {
        setErr(r.error || 'could not start recording');
        setPhase('idle');
        return;
      }
      setSid(r.sessionId);
      setPhase('recording');
      const id = r.sessionId;
      pollRef.current = window.setInterval(async () => {
        try {
          const s = await api.recordSteps(id);
          setLines(s.lines || []);
          if (!s.running) {
            stopPoll();
            setPhase('stopped');
          }
        } catch {
          /* transient poll error — keep trying */
        }
      }, 800);
    } catch (e) {
      setErr(String(e));
      setPhase('idle');
    }
  };

  const stop = async () => {
    if (!sid) return;
    stopPoll();
    setBusy(true);
    try {
      const r = await api.recordStop(sid);
      setContent(r.content || '');
      setLines(r.lines || lines);
      setPhase('stopped');
    } catch (e) {
      setErr(String(e));
    } finally {
      setBusy(false);
    }
  };

  const save = async () => {
    if (!sid) return;
    setBusy(true);
    setErr(null);
    try {
      const r = await api.recordSave(sid, fileName.trim());
      if (r.error) setErr(r.error);
      else {
        setSaved(r.savedPath || fileName);
        setPhase('saved');
      }
    } catch (e) {
      setErr(String(e));
    } finally {
      setBusy(false);
    }
  };

  const saveAndEdit = async () => {
    if (!sid) return;
    setBusy(true);
    setErr(null);
    try {
      const r = await api.recordSave(sid, fileName.trim());
      if (r.error) setErr(r.error);
      else onOpenInEditor(r.savedPath || fileName.trim());
    } catch (e) {
      setErr(String(e));
    } finally {
      setBusy(false);
    }
  };

  const reset = () => {
    stopPoll();
    setPhase('idle');
    setSid(null);
    setLines([]);
    setContent('');
    setSaved(null);
    setErr(null);
  };

  return (
    <div className="view record-view">
      <div className="view-head">
        <h2>Record a test</h2>
        <span className="muted small">
          Opens a local Chrome; your clicks, inputs and navigations are captured into a seleasy test.
        </span>
      </div>

      {phase === 'idle' && (
        <div className="import-controls">
          <input
            className="inp"
            placeholder="start URL (https://…)"
            value={url}
            onChange={(e) => setUrl(e.target.value)}
            style={{ minWidth: 360 }}
            onKeyDown={(e) => e.key === 'Enter' && start()}
          />
          <button className="btn primary" disabled={!url.trim() || url.trim() === 'https://'} onClick={start}>
            ● Start recording
          </button>
        </div>
      )}

      {phase === 'starting' && <div className="view-note muted">Launching a local Chrome window…</div>}

      {phase === 'recording' && (
        <div className="record-live">
          <div className="record-banner">
            <span className="record-dot" /> Recording — a Chrome window is open. Interact with it; steps appear
            below. Click <strong>Stop</strong> when done.
            <button className="btn small" style={{ marginLeft: 'auto' }} disabled={busy} onClick={stop}>
              ■ Stop
            </button>
          </div>
          <StepList lines={lines} live />
        </div>
      )}

      {err && <div className="import-err">{err}</div>}

      {phase === 'stopped' && (
        <div className="import-result">
          <div className="import-controls">
            <input
              className="inp"
              value={fileName}
              onChange={(e) => setFileName(e.target.value)}
              style={{ minWidth: 260 }}
            />
            <button className="btn primary" disabled={busy || !fileName.trim()} onClick={saveAndEdit}>
              {busy ? 'Saving…' : 'Save & open in editor'}
            </button>
            <button className="btn small" disabled={busy || !fileName.trim()} onClick={save}>
              Save only
            </button>
            <button className="btn ghost small" disabled={busy} onClick={reset}>
              Record again
            </button>
          </div>
          <div className="import-preview-title muted small">Generated seleasy ({lines.length} step(s))</div>
          <pre className="import-preview">{content}</pre>
        </div>
      )}

      {phase === 'saved' && (
        <div className="import-saved">
          Saved <code>{saved}</code> to the test-case directory.{' '}
          <button className="btn ghost small" onClick={() => onOpenInEditor(saved!)}>Open in editor →</button>
          <button className="btn ghost small" onClick={reset}>Record another</button>
        </div>
      )}
    </div>
  );
}

function StepList({ lines, live }: { lines: string[]; live?: boolean }) {
  if (lines.length === 0) {
    return <div className="view-note muted small">{live ? 'Waiting for your first action…' : 'No steps recorded.'}</div>;
  }
  return (
    <ol className="record-steps">
      {lines.map((l, i) => (
        <li key={i} className="mono">{l}</li>
      ))}
    </ol>
  );
}
