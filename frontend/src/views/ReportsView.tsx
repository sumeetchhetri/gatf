import { useEffect, useState } from 'react';
import { api } from '../api';
import type { ReportsIndex, ReportArtifact } from '../api';
import { fmtBytes } from '../util';

function fmtDate(ms: number): string {
  if (!ms) return '';
  const d = new Date(ms);
  return d.toLocaleString();
}

export default function ReportsView({
  onOpenTrace,
  onOpenReport,
}: {
  onOpenTrace: (url: string) => void;
  onOpenReport: (dataUrl: string) => void;
}) {
  const [data, setData] = useState<ReportsIndex | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const load = () => {
    setLoading(true);
    setError(null);
    api
      .reports()
      .then(setData)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  };
  useEffect(load, []);

  return (
    <div className="view">
      <div className="view-head">
        <h2>Reports</h2>
        <div className="view-head-right">
          {data && (
            <span className="muted small mono ellipsis" title={data.dir}>
              {data.dir}
            </span>
          )}
          <button className="btn ghost small" onClick={load}>
            Refresh
          </button>
        </div>
      </div>

      {loading && <div className="view-note">Loading…</div>}
      {error && (
        <div className="view-error">
          Could not reach the reports API ({error}). This shell needs the Phase-3.x <code>/api</code>{' '}
          handler — rebuild the JAR from this delta.
        </div>
      )}

      {data && !loading && (
        <>
          <section className="view-section">
            <h3>
              HTML reports <span className="muted small">({data.reports.length})</span>
            </h3>
            {data.reports.length === 0 ? (
              <div className="view-note">No reports generated yet. Run a suite to produce one.</div>
            ) : (
              <table className="grid-table">
                <thead>
                  <tr>
                    <th>Report</th>
                    <th className="num">Size</th>
                    <th>Modified</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  {data.reports.map((r: ReportArtifact) => (
                    <tr key={r.url}>
                      <td className="mono ellipsis" style={{ maxWidth: 420 }}>
                        {r.name}
                      </td>
                      <td className="num mono">{fmtBytes(r.size)}</td>
                      <td className="muted small">{fmtDate(r.modified)}</td>
                      <td className="row-actions">
                        {r.dataUrl && (
                          <button className="btn small" onClick={() => onOpenReport(r.dataUrl!)}>
                            Open in viewer
                          </button>
                        )}
                        <a className={'btn small' + (r.dataUrl ? ' ghost' : '')} href={r.url} target="_blank" rel="noopener">
                          {r.dataUrl ? 'HTML \u2197' : 'Open \u2197'}
                        </a>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </section>

          <section className="view-section">
            <h3>
              Trace bundles <span className="muted small">({data.traces.length})</span>
            </h3>
            {data.traces.length === 0 ? (
              <div className="view-note">
                No trace bundles found. Enable trace recording on a selenium run to capture one.
              </div>
            ) : (
              <table className="grid-table">
                <thead>
                  <tr>
                    <th>Bundle</th>
                    <th className="num">Size</th>
                    <th>Modified</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  {data.traces.map((r: ReportArtifact) => (
                    <tr key={r.url}>
                      <td className="mono ellipsis" style={{ maxWidth: 420 }}>
                        {r.name}
                      </td>
                      <td className="num mono">{fmtBytes(r.size)}</td>
                      <td className="muted small">{fmtDate(r.modified)}</td>
                      <td className="row-actions">
                        <button className="btn small" onClick={() => onOpenTrace(r.url)}>
                          Open in Viewer
                        </button>
                        <a className="btn ghost small" href={r.url} download={r.name}>
                          Download
                        </a>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </section>
        </>
      )}
    </div>
  );
}
