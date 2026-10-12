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
import { useEffect, useState } from 'react';
import { api } from '../api';
import type { ReportsIndex, ReportArtifact } from '../api';
import type { RunResults } from '../report/results';
import type { HistoryRun } from '../report/history';
import RunTrends from '../components/RunTrends';
import CoverageReportView from './CoverageReportView';
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
  Merged: '#7c3aed',
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
  // M12: the last CLI run's gatf-results.json in the out dir root (absent for console-only runs); Refresh reloads it
  const [latest, setLatest] = useState<RunResults | null>(null);
  const loadLatest = () => {
    api
      .runResults('/reports/gatf-results.json')
      .then(setLatest)
      .catch(() => setLatest(null));
  };
  useEffect(() => {
    load();
    loadLatest();
  }, []);
  // M12: trend over the last 10 archived CLI runs
  const [runs, setRuns] = useState<HistoryRun[]>([]);
  useEffect(() => {
    api
      .history(10)
      .then((h) => setRuns(h.runs))
      .catch(() => setRuns([]));
  }, []);
  const [docsMsg, setDocsMsg] = useState<string | null>(null);
  const downloadDocs = () => {
    setDocsMsg('Generating…');
    api
      .downloadTestDocs('xlsx')
      .then(() => setDocsMsg(null))
      .catch((e) => setDocsMsg('Test cases: ' + e.message));
  };

  // H6 (Δ155): a run's untested routes/scripts, shown in place of the list
  const [coverageOf, setCoverageOf] = useState<ReportArtifact | null>(null);
  if (coverageOf) return <CoverageReportView report={coverageOf} onBack={() => setCoverageOf(null)} />;

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
          <button
            className="btn ghost small"
            onClick={downloadDocs}
            title="Test-case workbook (Test Cases, Traceability, Coverage sheets), as gatf docs writes it"
          >
            {'\u2193'} Test cases (xlsx)
          </button>
          {docsMsg && <span className="muted small">{docsMsg}</span>}
          <button
            className="btn ghost small"
            onClick={() => {
              load();
              loadLatest();
            }}
          >
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

      {latest && (
        <section className="view-section">
          <h3>Latest CLI run</h3>
          <div className="row-actions">
            <span className="small">
              {latest.summary.total} tests: {latest.summary.passed} passed, {latest.summary.failed} failed,{' '}
              {latest.summary.flaky} flaky, {latest.summary.skipped} skipped
            </span>
            <button className="btn small" onClick={() => onOpenReport('/reports/gatf-results.json')}>
              Insights
            </button>
          </div>
        </section>
      )}

      {runs.length > 0 && (
        <section className="view-section">
          <h3>Trends</h3>
          <RunTrends runs={runs} />
        </section>
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
                        {traces.length > 0 && (
                          <button
                            className="btn ghost small"
                            onClick={() => setCoverageOf(r)}
                            title="Untested routes and scripts for this run (traces with coverage + a saved crawl)"
                          >
                            Coverage
                          </button>
                        )}
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
