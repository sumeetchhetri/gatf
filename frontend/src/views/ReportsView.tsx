import { useEffect, useState } from 'react';
import { api } from '../api';
import type { ReportsIndex, ReportArtifact } from '../api';
import { fmtBytes } from '../util';

function fmtDate(ms: number): string {
  if (!ms) return '';
  const d = new Date(ms);
  return d.toLocaleString();
}

const TYPE_COLOR: Record<string, string> = {
  API: '#2563eb',
  Selenium: '#16a34a',
  Load: '#d97706',
};
function typeBadge(type?: string) {
  const label = type ?? '\u2014';
  return (
    <span
      style={{
        display: 'inline-block',
        padding: '1px 8px',
        borderRadius: 10,
        fontSize: 11,
        fontWeight: 600,
        letterSpacing: 0.2,
        color: '#fff',
        background: TYPE_COLOR[type ?? ''] ?? '#6b7280',
      }}
    >
      {label}
    </span>
  );
}

export default function ReportsView({
  onOpenTrace,
  onOpenReport,
  onOpenViewer,
}: {
  onOpenTrace: (url: string) => void;
  onOpenReport: (dataUrl: string) => void;
  onOpenViewer: () => void;
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
          <button className="btn ghost small" onClick={onOpenViewer} title="Open a trace bundle (.zip) to inspect">
            {'\u25EB'} Trace Viewer
          </button>
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
        <section className="view-section">
          <h3>
            Suite runs <span className="muted small">({data.reports.length})</span>
          </h3>
          {data.reports.length === 0 ? (
            <div className="view-note">No reports generated yet. Run a suite to produce one.</div>
          ) : (
            <table className="grid-table">
              <thead>
                <tr>
                  <th>Type</th>
                  <th>Suite run</th>
                  <th className="num">Size</th>
                  <th>Modified</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {data.reports.map((r: ReportArtifact) => {
                  const traces = r.traces ?? [];
                  return (
                    <tr key={r.url}>
                      <td>{typeBadge(r.type)}</td>
                      <td className="mono ellipsis" style={{ maxWidth: 460 }} title={r.name}>
                        {r.label ?? r.name}
                      </td>
                      <td className="num mono">{fmtBytes(r.size)}</td>
                      <td className="muted small">{fmtDate(r.modified)}</td>
                      <td className="row-actions">
                        {r.dataUrl && (
                          <button
                            className="btn small"
                            onClick={() => onOpenReport(r.dataUrl!)}
                            title="Open in report viewer"
                          >
                            View
                          </button>
                        )}
                        <a
                          className="btn ghost small"
                          href={r.url}
                          target="_blank"
                          rel="noopener"
                          title="Open raw HTML report in a new tab"
                        >
                          {'\u2197'}
                        </a>
                        {traces.map((t, i) => (
                          <button
                            key={t.url}
                            className="btn ghost small"
                            onClick={() => onOpenTrace(t.url)}
                            title={'Open trace bundle in viewer \u2014 ' + t.name + ' (' + fmtBytes(t.size) + ')'}
                          >
                            {'\u25EB'} Trace{traces.length > 1 ? ' ' + (i + 1) : ''}
                          </button>
                        ))}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          )}
        </section>
      )}
    </div>
  );
}
