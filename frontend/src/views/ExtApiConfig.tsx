import { useCallback, useEffect, useState } from 'react';
import { api, type ApiTestCase } from '../api';
import TestCaseEditor from '../components/TestCaseEditor';

// Issue-Tracker / Server-Logs integration APIs: each is a fixed .xml of TestCase definitions edited with a
// configType, via the same /testcases CRUD as regular API cases (matches the classic getExtIntData).
const MODES = {
  issuetrackerapi: { label: 'Issue Tracking', file: 'gatf-issuetracking-api-int.xml' },
  loggingapi: { label: 'Server Logs', file: 'gatf-logging-api-int.xml' },
} as const;
type ModeKey = keyof typeof MODES;

export default function ExtApiConfig() {
  const [mode, setMode] = useState<ModeKey>('issuetrackerapi');
  const [cases, setCases] = useState<ApiTestCase[] | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const m = MODES[mode];

  const load = useCallback(() => {
    setLoading(true);
    setErr(null);
    setCases(null);
    api
      .readXmlCases(m.file, mode)
      .then(setCases)
      .catch((e) => {
        if (/no testcases/i.test(e.message)) setCases([]); // empty/new integration file
        else setErr(e.message);
      })
      .finally(() => setLoading(false));
  }, [m.file, mode]);

  useEffect(() => {
    load();
  }, [load]);

  return (
    <div className="view">
      <div className="view-head">
        <h2>Integrations</h2>
        <div className="view-head-right">
          {(Object.keys(MODES) as ModeKey[]).map((k) => (
            <button
              key={k}
              className={'btn small' + (mode === k ? ' primary' : ' ghost')}
              onClick={() => setMode(k)}
            >
              {MODES[k].label}
            </button>
          ))}
        </div>
      </div>
      <div className="view-note muted small">
        {mode === 'issuetrackerapi'
          ? 'APIs GATF calls to raise defects in your issue tracker'
          : 'APIs GATF reads to attach server logs during API tests'}{' '}
        — file <code>{m.file}</code>.
      </div>
      {loading && <div className="view-note">Loading…</div>}
      {err && <div className="view-error">Could not load ({err}).</div>}
      {cases && !loading && (
        <TestCaseEditor key={mode} filePath={m.file} cases={cases} onReload={load} configType={mode} />
      )}
    </div>
  );
}
