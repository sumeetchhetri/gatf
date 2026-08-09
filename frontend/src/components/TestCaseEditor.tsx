import { useEffect, useMemo, useState } from 'react';
import { api } from '../api';
import type { ApiTestCase, TestCaseReport } from '../api';
import { testcaseSchema } from '../config/testcaseSchema';
import SchemaForm, { type EnumMap } from './SchemaForm';
import ApiResult from './ApiResult';

function asStrArr(v: unknown): string[] {
  if (!Array.isArray(v)) return [];
  return v.map((x) => (typeof x === 'string' ? x : String(x))).filter((s) => s !== '');
}

export default function TestCaseEditor({
  filePath,
  cases,
  onReload,
  configType,
}: {
  filePath: string;
  cases: ApiTestCase[];
  onReload: () => void;
  configType?: string;
}) {
  const [misc, setMisc] = useState<Record<string, unknown>>({});
  const [view, setView] = useState<'list' | 'form'>('list');
  const [mode, setMode] = useState<'new' | 'edit'>('new');
  const [editingName, setEditingName] = useState<string | null>(null);
  const [tc, setTc] = useState<Record<string, unknown>>({});
  const [ver, setVer] = useState(0);
  const [saving, setSaving] = useState(false);
  const [saveErr, setSaveErr] = useState<string | null>(null);
  const [delName, setDelName] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  // ▶ Play — run an API test case as defined (regular API only; not the external integrations)
  const canPlay = !configType;
  const [playBusy, setPlayBusy] = useState<string | null>(null);
  const [playResult, setPlayResult] = useState<TestCaseReport | null>(null);
  const [playErr, setPlayErr] = useState<string | null>(null);

  const playOne = (name: string) => {
    if (!name) return;
    setPlayBusy(name);
    setPlayErr(null);
    setPlayResult(null);
    api
      .playApiTest(filePath, name)
      .then((r) => setPlayResult(r))
      .catch((e) => setPlayErr(e.message || String(e)))
      .finally(() => setPlayBusy(null));
  };

  useEffect(() => {
    api
      .misc()
      .then(setMisc)
      .catch(() => setMisc({}));
  }, []);

  const enums: EnumMap = useMemo(
    () => ({
      'miscMap.hooks': asStrArr(misc.hooks),
      'miscMap.providers': asStrArr(misc.providers),
      currtestcases: cases.map((c) => c.name ?? '').filter((n) => n !== ''),
    }),
    [misc, cases],
  );

  const openNew = () => {
    setTc({});
    setMode('new');
    setEditingName(null);
    setSaveErr(null);
    setVer((v) => v + 1);
    setView('form');
  };
  const openEdit = (c: ApiTestCase) => {
    setTc({ ...(c as Record<string, unknown>) });
    setMode('edit');
    setEditingName(c.name ?? null);
    setSaveErr(null);
    setVer((v) => v + 1);
    setView('form');
  };

  const save = () => {
    setSaving(true);
    setSaveErr(null);
    const op =
      mode === 'new'
        ? api.addCase(filePath, tc as ApiTestCase, configType)
        : api.updateCase(filePath, editingName || '', tc as ApiTestCase, configType);
    op.then(() => {
      setView('list');
      onReload();
    })
      .catch((e) => setSaveErr(e.message))
      .finally(() => setSaving(false));
  };

  const del = (name: string) => {
    setBusy(true);
    api
      .deleteCase(filePath, name, configType)
      .then(() => {
        setDelName(null);
        onReload();
      })
      .catch((e) => setSaveErr(e.message))
      .finally(() => setBusy(false));
  };

  const playPanel =
    playErr || playResult ? (
      <div className="tce-play">
        <div className="tce-play-bar">
          <strong className="small">Play result</strong>
          <button className="btn ghost small" onClick={() => { setPlayResult(null); setPlayErr(null); }}>
            Close
          </button>
        </div>
        {playErr ? <div className="view-error">{playErr}</div> : <ApiResult r={playResult!} showStatus />}
      </div>
    ) : null;

  if (view === 'form') {
    return (
      <div className="tce">
        <div className="tce-bar">
          <button className="btn ghost small" onClick={() => setView('list')}>
            ← Back to cases
          </button>
          <span className="tce-title small">{mode === 'new' ? 'New test case' : `Edit: ${editingName}`}</span>
          {canPlay && mode === 'edit' && editingName && (
            <button
              className="btn small"
              disabled={playBusy === editingName}
              onClick={() => playOne(editingName)}
              title="Run this test as defined (the last saved version)"
            >
              {playBusy === editingName ? 'Playing…' : '\u25b6 Play'}
            </button>
          )}
          <button className="btn primary small" disabled={saving} onClick={save}>
            {saving ? 'Saving…' : 'Save case'}
          </button>
        </div>
        {saveErr && <div className="view-error">Save failed: {saveErr}</div>}
        <div className="view-note muted small">
          Saved to the XML file via the test-case API, which validates the request (name, URL, method…).
        </div>
        {playPanel}
        <SchemaForm key={ver} schema={testcaseSchema} value={tc} onChange={setTc} enums={enums} />
      </div>
    );
  }

  return (
    <div className="tce">
      <div className="tce-bar">
        <span className="small muted">{cases.length} API test case(s)</span>
        <button className="btn primary small" onClick={openNew}>
          + Add case
        </button>
      </div>
      {saveErr && <div className="view-error">{saveErr}</div>}
      {playPanel}
      {cases.length === 0 ? (
        <div className="view-note muted">No cases yet. Add one with “+ Add case”.</div>
      ) : (
        <table className="grid-table">
          <thead>
            <tr>
              <th>Name</th>
              <th>Method</th>
              <th>URL</th>
              <th className="tce-actions-h"></th>
            </tr>
          </thead>
          <tbody>
            {cases.map((c, i) => (
              <tr key={i}>
                <td className="mono">{c.name ?? '—'}</td>
                <td>{c.method ?? '—'}</td>
                <td className="mono ellipsis" style={{ maxWidth: 300 }} title={c.url}>
                  {c.url ?? '—'}
                </td>
                <td className="tce-actions">
                  {canPlay && (
                    <button
                      className="btn small"
                      disabled={playBusy === c.name}
                      onClick={() => playOne(c.name ?? '')}
                      title="Run this test as defined"
                    >
                      {playBusy === c.name ? 'Playing…' : '\u25b6 Play'}
                    </button>
                  )}
                  <button className="btn ghost small" onClick={() => openEdit(c)}>
                    Edit
                  </button>
                  {delName === c.name ? (
                    <>
                      <button
                        className="btn small"
                        style={{ color: '#e2557b' }}
                        disabled={busy}
                        onClick={() => del(c.name!)}
                      >
                        Confirm
                      </button>
                      <button className="btn ghost small" onClick={() => setDelName(null)}>
                        Cancel
                      </button>
                    </>
                  ) : (
                    <button className="btn ghost small" onClick={() => setDelName(c.name ?? null)}>
                      Delete
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
