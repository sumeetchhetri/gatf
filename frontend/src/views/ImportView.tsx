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
import { useRef, useState } from 'react';
import { api, type ImportResultDto } from '../api';

type Kind = 'playwright' | 'jmeter';

const META: Record<Kind, { label: string; accept: string; ext: string; hint: string }> = {
  playwright: {
    label: 'Playwright',
    accept: '.ts,.js,.spec.ts,.spec.js,text/plain',
    ext: '.sel',
    hint: 'A Playwright spec (.spec.ts / .spec.js). Each test() becomes an auto-running subtest.',
  },
  jmeter: {
    label: 'JMeter',
    accept: '.jmx,application/xml,text/xml',
    ext: '.xml',
    hint: 'A JMeter test plan (.jmx). Each HTTP sampler becomes a GATF API test case.',
  },
};

export default function ImportView({ onOpenTestFiles }: { onOpenTestFiles: () => void }) {
  const [kind, setKind] = useState<Kind>('playwright');
  const [source, setSource] = useState('');
  const [fileName, setFileName] = useState('');
  const [busy, setBusy] = useState<'' | 'preview' | 'save'>('');
  const [result, setResult] = useState<ImportResultDto | null>(null);
  const [saved, setSaved] = useState<string | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const fileRef = useRef<HTMLInputElement>(null);

  const meta = META[kind];

  const pickKind = (k: Kind) => {
    setKind(k);
    setResult(null);
    setSaved(null);
    setErr(null);
  };

  const onFile = async (f: File | undefined) => {
    if (!f) return;
    const text = await f.text();
    setSource(text);
    if (!fileName) setFileName(f.name.replace(/\.(jmx|spec\.ts|spec\.js|ts|js)$/i, '') + meta.ext);
    setResult(null);
    setSaved(null);
    setErr(null);
  };

  const run = async (save: boolean) => {
    if (!source.trim()) {
      setErr('Paste or upload a source file first.');
      return;
    }
    setBusy(save ? 'save' : 'preview');
    setErr(null);
    setSaved(null);
    try {
      const r = await api.importSource(kind, fileName.trim(), save, source);
      if (r.error) {
        setErr(r.error);
        setResult(null);
      } else {
        setResult(r);
        if (save && r.saved) setSaved(r.savedPath || r.fileName);
      }
    } catch (e) {
      setErr(String(e));
    } finally {
      setBusy('');
    }
  };

  return (
    <div className="view import-view">
      <div className="view-head">
        <h2>Import tests</h2>
        <span className="muted small">Convert Playwright or JMeter tests into GATF test cases (best-effort).</span>
      </div>

      <div className="import-controls">
        <div className="seg">
          {(Object.keys(META) as Kind[]).map((k) => (
            <button key={k} className={'seg-btn' + (kind === k ? ' on' : '')} onClick={() => pickKind(k)}>
              {META[k].label}
            </button>
          ))}
        </div>
        <input
          className="inp"
          placeholder={'output file name (optional, ' + meta.ext + ')'}
          value={fileName}
          onChange={(e) => setFileName(e.target.value)}
          style={{ minWidth: 260 }}
        />
        <input
          ref={fileRef}
          type="file"
          accept={meta.accept}
          style={{ display: 'none' }}
          onChange={(e) => onFile(e.target.files?.[0])}
        />
        <button className="btn ghost small" onClick={() => fileRef.current?.click()}>
          Upload file…
        </button>
      </div>
      <div className="muted small" style={{ marginBottom: 8 }}>{meta.hint}</div>

      <textarea
        className="import-source"
        placeholder={'Paste ' + meta.label + ' source here, or use Upload file…'}
        value={source}
        onChange={(e) => setSource(e.target.value)}
        spellCheck={false}
      />

      <div className="import-actions">
        <button className="btn" disabled={!!busy} onClick={() => run(false)}>
          {busy === 'preview' ? 'Converting…' : 'Preview import'}
        </button>
        <button className="btn primary" disabled={!!busy} onClick={() => run(true)}>
          {busy === 'save' ? 'Saving…' : 'Save to test files'}
        </button>
        {source && (
          <button className="btn ghost small" disabled={!!busy} onClick={() => { setSource(''); setResult(null); setSaved(null); setErr(null); }}>
            Clear
          </button>
        )}
      </div>

      {err && <div className="import-err">{err}</div>}

      {saved && (
        <div className="import-saved">
          Saved <code>{saved}</code> to the test-case directory.{' '}
          <button className="btn ghost small" onClick={onOpenTestFiles}>Open in Test Files →</button>
        </div>
      )}

      {result && !result.error && (
        <div className="import-result">
          <div className="import-result-head">
            <strong>{result.fileName}</strong>
            <span className="muted small">
              {Object.entries(result.stats).map(([k, v]) => `${k}: ${v}`).join('  ·  ')}
            </span>
          </div>

          {result.validation && (
            <div className="muted small" style={{ marginBottom: 6 }}>parse-check: {result.validation}</div>
          )}

          {result.loadConfig && (
            <div className="muted small" style={{ marginBottom: 6 }}>
              load settings for gatf-config: <code>{JSON.stringify(result.loadConfig)}</code>
            </div>
          )}

          {result.unsupported && result.unsupported.length > 0 && (
            <div className="import-unsupported">
              <div className="import-unsupported-title">
                {result.unsupported.length} construct(s) need review
              </div>
              <table className="tbl">
                <thead>
                  <tr><th style={{ width: 60 }}>Line</th><th>Construct</th><th>Reason</th></tr>
                </thead>
                <tbody>
                  {result.unsupported.map((u, i) => (
                    <tr key={i}>
                      <td>{u.line || '—'}</td>
                      <td><code>{u.construct}</code></td>
                      <td className="muted">{u.reason}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          <div className="import-preview-title muted small">Generated {result.kind === 'sel' ? 'seleasy' : 'XML'}</div>
          <pre className="import-preview">{result.content}</pre>
        </div>
      )}
    </div>
  );
}
