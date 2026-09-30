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
import { api, type ImportBundle, type ImportResultDto, type ImportType } from '../api';

type Kind = ImportType;

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
  curl: {
    label: 'cURL',
    accept: '.sh,.txt,.curl,text/plain',
    ext: '.xml',
    hint: 'One or more curl commands (bash, cmd or PowerShell "Copy as cURL", or the cURL line of a GATF report). Each becomes an API test case.',
  },
  postman: {
    label: 'Postman',
    accept: '.json,application/json',
    ext: '.xml',
    hint: 'A Postman collection (v2.0/v2.1, or v1). Folders, auth, bodies and status assertions carry over; {{var}} becomes ${var}.',
  },
  har: {
    label: 'HAR',
    accept: '.har,.json,application/json',
    ext: '.xml',
    hint: 'A HAR recording (browser dev tools "Save all as HAR"). XHR/fetch calls become API test cases with the recorded status.',
  },
  openapi: {
    label: 'OpenAPI',
    accept: '.json,.yaml,.yml,application/json,application/yaml,text/yaml',
    ext: '.xml',
    hint: 'A Swagger 2.0 or OpenAPI 3.0/3.1 spec (JSON or YAML). Each operation becomes an API test case; tags become @tags. Set baseUrl and the auth placeholders in gatf-config. For relative-file $refs select the spec together with the files it references (multi-select), or use gatf import openapi <file>.',
  },
  flow: {
    label: 'Flow → load',
    accept: '.har,.json,application/json',
    ext: '.xml',
    hint: "A UI run's network.har (inside its trace zip). Each API/document request becomes a load-test case (vegeta, 10 users, 30 s; change them in the file or with gatf import flow --users/--duration), and <name>.targets.jsonl holds the whole flow for vegeta. Faster: Load test this flow in the Trace Viewer.",
  },
};

// stats values may be nested counts (HAR skipped / origins)
function fmtStat(v: unknown): string {
  if (v && typeof v === 'object') return Object.entries(v as Record<string, unknown>).map(([k, n]) => `${k} ${n}`).join(', ');
  return String(v);
}

// OpenAPI multi-select: the spec (a top-level openapi:/swagger: key) is the main file; the others go by path relative to it.
async function readBundle(files: File[]): Promise<{ main: File; text: string; bundle: ImportBundle | null }> {
  const texts = await Promise.all(files.map((f) => f.text()));
  let mi = texts.findIndex((t) => /^\s*[{]?\s*["']?(openapi|swagger)["']?\s*:/m.test(t));
  if (mi < 0) mi = 0;
  if (files.length < 2) return { main: files[mi], text: texts[mi], bundle: null };
  const rel = (f: File) => ((f as File & { webkitRelativePath?: string }).webkitRelativePath || f.name).replace(/\\/g, '/');
  const mainRel = rel(files[mi]);
  const dir = mainRel.includes('/') ? mainRel.slice(0, mainRel.lastIndexOf('/') + 1) : '';
  const out: Record<string, string> = {};
  files.forEach((f, i) => {
    if (i === mi) return;
    const r = rel(f);
    out[dir && r.startsWith(dir) ? r.slice(dir.length) : r] = texts[i];
  });
  return { main: files[mi], text: texts[mi], bundle: { mainName: mainRel.slice(dir.length), files: out } };
}

export default function ImportView({ onOpenTestFiles }: { onOpenTestFiles: () => void }) {
  const [kind, setKind] = useState<Kind>('playwright');
  const [source, setSource] = useState('');
  const [fileName, setFileName] = useState('');
  const [filter, setFilter] = useState('');
  const [negative, setNegative] = useState(false);
  const [bundle, setBundle] = useState<ImportBundle | null>(null);
  const [busy, setBusy] = useState<'' | 'preview' | 'save'>('');
  const [result, setResult] = useState<ImportResultDto | null>(null);
  const [saved, setSaved] = useState<string | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const fileRef = useRef<HTMLInputElement>(null);

  const meta = META[kind];

  const pickKind = (k: Kind) => {
    setKind(k);
    setBundle(null);
    setResult(null);
    setSaved(null);
    setErr(null);
  };

  const onFile = async (list: FileList | File[] | null | undefined) => {
    const all = list ? Array.from(list) : [];
    if (!all.length) return;
    const { main: f, text, bundle: b } = kind === 'openapi' ? await readBundle(all) : { main: all[0], text: await all[0].text(), bundle: null };
    setSource(text);
    setBundle(b);
    let base = f.name.replace(/\.(jmx|spec\.ts|spec\.js|ts|js|har|json|yaml|yml|sh|txt|curl)$/i, '');
    if (kind === 'openapi') base = base.replace(/\.(openapi|swagger)$/i, ''); // petstore.openapi.json -> petstore.xml (+ petstore.openapi.json spec)
    if (!fileName) setFileName(base + meta.ext);
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
      const r = await api.importSource(kind, fileName.trim(), save, source, kind === 'har' || kind === 'flow' ? filter.trim() : undefined,
        kind === 'openapi' && negative, kind === 'openapi' && bundle ? bundle : undefined);
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
        <span className="muted small">Convert Playwright, JMeter, cURL, Postman, HAR or OpenAPI into GATF test cases (best-effort).</span>
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
        {(kind === 'har' || kind === 'flow') && (
          <input
            className="inp"
            placeholder="URL filter regex (optional, e.g. api\.example\.com)"
            value={filter}
            onChange={(e) => setFilter(e.target.value)}
            style={{ minWidth: 240 }}
          />
        )}
        {kind === 'openapi' && (
          <label className="small" title="Also generate cases that leave out a required parameter or send a wrong-typed body property, expecting the spec's 4xx">
            <input type="checkbox" checked={negative} onChange={(e) => setNegative(e.target.checked)} /> negative cases
          </label>
        )}
        <input
          ref={fileRef}
          type="file"
          data-testid="import-file"
          accept={meta.accept}
          multiple={kind === 'openapi'}
          style={{ display: 'none' }}
          onChange={(e) => onFile(e.target.files)}
        />
        <button className="btn ghost small" onClick={() => fileRef.current?.click()}>
          Upload file…
        </button>
      </div>
      <div className="muted small" style={{ marginBottom: 8 }}>{meta.hint}</div>
      {bundle && (
        <div className="muted small" style={{ marginBottom: 8 }}>
          bundle: <code>{bundle.mainName}</code> + {Object.keys(bundle.files).length} referenced file(s):{' '}
          <code>{Object.keys(bundle.files).join(', ')}</code>
        </div>
      )}

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
          <button className="btn ghost small" disabled={!!busy} onClick={() => { setSource(''); setBundle(null); setResult(null); setSaved(null); setErr(null); }}>
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
              {Object.entries(result.stats).map(([k, v]) => `${k}: ${fmtStat(v)}`).join('  ·  ')}
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

          {result.variables && (
            <div className="muted small" style={{ marginBottom: 6 }}>
              variables for gatf-config globalVariables: <code>{JSON.stringify(result.variables)}</code>
            </div>
          )}

          {result.spec && (
            <div className="muted small" style={{ marginBottom: 6 }}>
              responses are contract-checked against <code>{result.spec}</code> (saved beside the test file)
            </div>
          )}

          {result.drift && (
            <div className="import-drift small" style={{ marginBottom: 6 }}>
              {result.drift.added.length + result.drift.removed.length + result.drift.changed.length === 0 ? (
                <span className="muted">no drift from the existing {result.fileName}</span>
              ) : (
                <>
                  <div className="import-unsupported-title">drift from the existing {result.fileName}</div>
                  {result.drift.added.map((o) => <div key={'a' + o}><code>+ {o}</code> added</div>)}
                  {result.drift.removed.map((o) => <div key={'r' + o}><code>- {o}</code> removed</div>)}
                  {result.drift.changed.map((o) => <div key={'c' + o}><code>~ {o}</code> changed</div>)}
                </>
              )}
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
