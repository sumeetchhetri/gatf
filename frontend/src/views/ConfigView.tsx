import { useEffect, useState } from 'react';
import { api } from '../api';
import type { ConfigResponse } from '../api';

// Fields worth surfacing prominently; everything else is in the raw JSON below.
const HIGHLIGHT: { key: string; label: string }[] = [
  { key: 'testCasesBasePath', label: 'Test cases base path' },
  { key: 'testCaseDir', label: 'Test case dir' },
  { key: 'outFilesBasePath', label: 'Output base path' },
  { key: 'outFilesDir', label: 'Output dir' },
  { key: 'baseUrl', label: 'Base URL' },
  { key: 'seleniumExecutor', label: 'Selenium executor' },
  { key: 'seleniumScript', label: 'Selenium script' },
  { key: 'numConcurrentExecutions', label: 'Concurrent executions' },
  { key: 'concurrentUserSimulationNum', label: 'Concurrent users' },
  { key: 'loadTestingEnabled', label: 'Load testing' },
  { key: 'authEnabled', label: 'Auth enabled' },
];

function show(v: unknown): string {
  if (v === null || v === undefined || v === '') return '—';
  if (typeof v === 'boolean') return v ? 'yes' : 'no';
  if (Array.isArray(v)) return v.length ? v.join(', ') : '—';
  if (typeof v === 'object') return JSON.stringify(v);
  return String(v);
}

export default function ConfigView() {
  const [data, setData] = useState<ConfigResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [rawOpen, setRawOpen] = useState(false);

  useEffect(() => {
    setLoading(true);
    api
      .config()
      .then(setData)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  }, []);

  const cfg = data?.config ?? {};

  return (
    <div className="view">
      <div className="view-head">
        <h2>Configuration</h2>
        <span className="muted small">read-only</span>
      </div>

      <div className="view-note muted small">
        This is the effective GATF executor config. Editing and saving is still done in the{' '}
        <a href="/">classic UI</a> — an in-shell editor lands with Monaco in Phase 4.
      </div>

      {loading && <div className="view-note">Loading…</div>}
      {error && (
        <div className="view-error">
          Could not reach the config API ({error}). Rebuild the JAR from this delta so the{' '}
          <code>/api</code> handler is present.
        </div>
      )}

      {data && !loading && (
        <>
          <section className="view-section">
            <div className="kv-grid">
              {HIGHLIGHT.map((h) => (
                <div className="kv-row" key={h.key}>
                  <div className="kv-k">{h.label}</div>
                  <div className="kv-v mono">{show((cfg as Record<string, unknown>)[h.key])}</div>
                </div>
              ))}
            </div>
          </section>

          <section className="view-section">
            <button className="btn ghost small" onClick={() => setRawOpen((o) => !o)}>
              {rawOpen ? 'Hide' : 'Show'} raw config JSON
            </button>
            {rawOpen && <pre className="body-pre" style={{ maxHeight: 480 }}>{JSON.stringify(cfg, null, 2)}</pre>}
          </section>
        </>
      )}
    </div>
  );
}
