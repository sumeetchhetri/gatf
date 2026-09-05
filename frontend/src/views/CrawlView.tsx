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
import type { CrawlStatus, CoverageItem } from '../api';

// G3 — browser-driven crawl of a (JS-rendered) app → starter seleasy suite + prioritized coverage plan.
type Phase = 'idle' | 'running' | 'done' | 'saved';

export default function CrawlView({ onOpenInEditor }: { onOpenInEditor: (path: string) => void }) {
  const [seed, setSeed] = useState('https://');
  const [maxPages, setMaxPages] = useState(25);
  const [maxDepth, setMaxDepth] = useState(3);
  const [creds, setCreds] = useState(false);
  const [loginUrl, setLoginUrl] = useState('');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [userSel, setUserSel] = useState('');
  const [passSel, setPassSel] = useState('');
  const [submitSel, setSubmitSel] = useState('');
  const [submitForms, setSubmitForms] = useState(false);
  const [headless, setHeadless] = useState(true);
  const [browser, setBrowser] = useState<'chrome' | 'firefox' | 'edge'>('chrome');
  const [exclude, setExclude] = useState('');

  const [phase, setPhase] = useState<Phase>('idle');
  const [sid, setSid] = useState<string | null>(null);
  const [status, setStatus] = useState<CrawlStatus | null>(null);
  const [content, setContent] = useState('');
  const [coverage, setCoverage] = useState<CoverageItem[]>([]);
  const [fileName, setFileName] = useState('crawl-suite');
  const [saved, setSaved] = useState<string | null>(null);
  const [validation, setValidation] = useState<string | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const poll = useRef<number | null>(null);

  const stopPoll = () => {
    if (poll.current != null) window.clearInterval(poll.current);
    poll.current = null;
  };
  useEffect(() => stopPoll, []);

  const start = async () => {
    setErr(null);
    setBusy(true);
    try {
      const r = await api.crawlStart({
        seed: seed.trim(),
        maxPages,
        maxDepth,
        submitForms,
        headless,
        browser,
        exclude: exclude.trim() || undefined,
        suiteName: fileName.trim() || undefined,
        ...(creds
          ? { loginUrl: loginUrl.trim(), username, password, userSel: userSel.trim() || undefined, passSel: passSel.trim() || undefined, submitSel: submitSel.trim() || undefined }
          : {}),
      });
      if (!r.sessionId) {
        setErr(r.error || 'could not start the crawl');
        return;
      }
      setSid(r.sessionId);
      setPhase('running');
      setPassword(''); // not kept in the page once handed to the server
      const id = r.sessionId;
      poll.current = window.setInterval(async () => {
        const s = await api.crawlStatus(id).catch(() => null);
        if (!s) return;
        setStatus(s);
        if (!s.running) {
          stopPoll();
          if (s.done) {
            const res = await api.crawlResult(id);
            setContent(res.content || '');
            setCoverage(res.coverage || []);
            setPhase('done');
          } else {
            setErr(s.error || 'crawl ended without a result');
            setPhase('idle');
          }
        }
      }, 1000);
    } finally {
      setBusy(false);
    }
  };

  const stop = async () => {
    if (sid) await api.crawlStop(sid);
  };

  const save = async (open: boolean) => {
    if (!sid) return;
    setBusy(true);
    try {
      const r = await api.crawlSave(sid, fileName.trim());
      if (r.error) {
        setErr(r.error);
        return;
      }
      setSaved(r.savedPath || null);
      setValidation(r.validation || null);
      setPhase('saved');
      if (open && r.savedPath) onOpenInEditor(r.savedPath);
    } finally {
      setBusy(false);
    }
  };

  const reset = () => {
    stopPoll();
    setPhase('idle');
    setSid(null);
    setStatus(null);
    setContent('');
    setCoverage([]);
    setSaved(null);
    setValidation(null);
    setErr(null);
  };

  const validSeed = /^https?:\/\/.+/i.test(seed.trim());
  const pct = status ? Math.min(100, Math.round((status.visited / Math.max(1, status.maxPages)) * 100)) : 0;

  return (
    <div className="view crawl-view">
      <div className="view-head">
        <h2>Crawl an app</h2>
        <span className="muted small">
          Walks the rendered pages of your app in a real browser (works for React/Angular/Vue) and generates a starter
          seleasy suite plus a coverage plan.
        </span>
      </div>

      {phase === 'idle' && (
        <div className="crawl-form">
          <div className="import-controls">
            <input className="inp" style={{ minWidth: 380 }} placeholder="start URL (https://…)" value={seed} onChange={(e) => setSeed(e.target.value)} />
            <label className="small">
              pages <input className="inp num" type="number" min={1} max={500} value={maxPages} onChange={(e) => setMaxPages(+e.target.value)} />
            </label>
            <label className="small">
              depth <input className="inp num" type="number" min={0} max={10} value={maxDepth} onChange={(e) => setMaxDepth(+e.target.value)} />
            </label>
          </div>
          <div className="import-controls small">
            <label><input type="checkbox" checked={creds} onChange={(e) => setCreds(e.target.checked)} /> log in first</label>
            <select className="inp" value={browser} onChange={(e) => setBrowser(e.target.value as 'chrome' | 'firefox' | 'edge')} title="browser">
              <option value="chrome">Chrome</option>
              <option value="firefox">Firefox</option>
              <option value="edge">Edge</option>
            </select>
            <label><input type="checkbox" checked={headless} onChange={(e) => setHeadless(e.target.checked)} /> headless</label>
            <label title="POST forms are only filled, not submitted, unless enabled">
              <input type="checkbox" checked={submitForms} onChange={(e) => setSubmitForms(e.target.checked)} /> submit POST forms
            </label>
            <input className="inp" style={{ minWidth: 260 }} placeholder="exclude regex (default: logout/delete links)" value={exclude} onChange={(e) => setExclude(e.target.value)} />
          </div>
          {creds && (
            <div className="crawl-creds">
              <input className="inp" style={{ minWidth: 300 }} placeholder="login URL" value={loginUrl} onChange={(e) => setLoginUrl(e.target.value)} />
              <input className="inp" placeholder="username" value={username} onChange={(e) => setUsername(e.target.value)} autoComplete="off" />
              <input className="inp" type="password" placeholder="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="new-password" />
              <details className="small">
                <summary>selectors (optional — auto-detected)</summary>
                <input className="inp" placeholder="username selector e.g. id@email" value={userSel} onChange={(e) => setUserSel(e.target.value)} />
                <input className="inp" placeholder="password selector" value={passSel} onChange={(e) => setPassSel(e.target.value)} />
                <input className="inp" placeholder="submit selector" value={submitSel} onChange={(e) => setSubmitSel(e.target.value)} />
              </details>
              <div className="muted small">
                Credentials are used only for this crawl; the generated suite contains <code>${'{username}'}</code> /{' '}
                <code>${'{password}'}</code> placeholders, never the values.
              </div>
            </div>
          )}
          <div className="import-controls">
            <button className="btn primary" disabled={busy || !validSeed || (creds && (!loginUrl.trim() || !username || !password))} onClick={start}>
              ▶ Start crawl
            </button>
          </div>
        </div>
      )}

      {err && <div className="import-err">{err}</div>}

      {phase === 'running' && status && (
        <div className="crawl-live">
          <div className="record-banner">
            <span className="spinner" /> Crawling — {status.visited}/{status.maxPages} pages, {status.queued} queued
            {status.current ? <span className="muted small ellipsis" style={{ maxWidth: 420 }}> · {status.current}</span> : null}
            <button className="btn small" style={{ marginLeft: 'auto' }} onClick={stop}>■ Stop</button>
          </div>
          <div className="crawl-bar"><div style={{ width: pct + '%' }} /></div>
          {status.loginWarning && <div className="import-err">{status.loginWarning}</div>}
          <pre className="crawl-log mono small">{status.log.slice(-40).join('\n')}</pre>
        </div>
      )}

      {(phase === 'done' || phase === 'saved') && (
        <div className="import-result">
          <div className="import-result-head">
            <strong>{coverage.length} page(s) crawled</strong>
            <span className="muted small">
              {coverage.filter((c) => c.priority === 'high').length} high · {coverage.filter((c) => c.priority === 'medium').length} medium ·{' '}
              {coverage.filter((c) => c.priority === 'low').length} low priority
            </span>
          </div>
          {status?.loginWarning && <div className="import-err">{status.loginWarning}</div>}
          <div className="tbl-scroll" style={{ maxHeight: 320 }}>
            <table className="grid-table">
              <thead>
                <tr>
                  <th>Priority</th>
                  <th>Page</th>
                  <th className="num">Forms</th>
                  <th className="num">Fields</th>
                  <th className="num">Actions</th>
                  <th>Why</th>
                </tr>
              </thead>
              <tbody>
                {coverage.map((c) => (
                  <tr key={c.url}>
                    <td><span className={'rag rag-' + (c.priority === 'high' ? 'fail' : c.priority === 'medium' ? 'running' : c.priority === 'error' ? 'fail' : 'pass')}>{c.priority}</span></td>
                    <td className="mono ellipsis" style={{ maxWidth: 380 }} title={c.url}>{c.title || c.url}</td>
                    <td className="num mono">{c.forms}</td>
                    <td className="num mono">{c.fields}</td>
                    <td className="num mono">{c.buttons}</td>
                    <td className="muted small">{c.reasons}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="import-preview-title muted small">Generated seleasy (starter walkthrough — add assertions)</div>
          <pre className="import-preview mono">{content}</pre>
          {phase === 'done' && (
            <div className="import-controls">
              <input className="inp" value={fileName} onChange={(e) => setFileName(e.target.value)} placeholder="file name" />
              <button className="btn primary" disabled={busy || !fileName.trim()} onClick={() => save(true)}>Save &amp; open in editor</button>
              <button className="btn small" disabled={busy || !fileName.trim()} onClick={() => save(false)}>Save</button>
              <button className="btn ghost small" onClick={reset}>New crawl</button>
            </div>
          )}
          {phase === 'saved' && (
            <div className="import-saved">
              Saved <code>{saved}</code> (+ coverage JSON) to the test-case directory.
              {validation && <span className="muted small"> parse-check: {validation}</span>}{' '}
              <button className="btn ghost small" onClick={() => saved && onOpenInEditor(saved)}>Open in editor →</button>
              <button className="btn ghost small" onClick={reset}>New crawl</button>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
