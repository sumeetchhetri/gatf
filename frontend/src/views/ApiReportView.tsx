import { Fragment, useState } from 'react';
import type { ApiReportData, ApiIndexData, TestCaseReport, TestCaseStat, TestSuiteStats } from '../api';
import { fmtMs } from '../util';

function stClass(s: string): string {
  const u = (s || '').toLowerCase();
  if (u === 'success' || u === 'pass' || u === 'passed') return 'st-pass';
  if (u === 'skipped' || u === 'skip') return 'st-warn';
  return 'st-fail';
}

function SummaryCards({ s }: { s: TestSuiteStats }) {
  const passed = s.totalTestCount - s.failedTestCount - s.skippedTestCount;
  const rate = s.totalTestCount ? Math.round((passed / s.totalTestCount) * 100) : 0;
  const cards: [string, string, string?][] = [
    ['Total', String(s.totalTestCount)],
    ['Passed', String(passed), 'st-pass'],
    ['Failed', String(s.failedTestCount), s.failedTestCount ? 'st-fail' : undefined],
    ['Skipped', String(s.skippedTestCount), s.skippedTestCount ? 'st-warn' : undefined],
    ['Pass rate', rate + '%'],
    ['Runs', `${s.totalRuns || s.totalSuiteRuns} (${s.failedRuns} failed)`],
    ['Time', fmtMs(s.executionTime)],
  ];
  return (
    <div className="vitals">
      {cards.map(([label, val, cls]) => (
        <div className="vital-card" key={label}>
          <div className={'vital-val ' + (cls || '')}>{val}</div>
          <div className="vital-label">{label}</div>
        </div>
      ))}
    </div>
  );
}

function Detail({ r }: { r: TestCaseReport }) {
  const reqHdr = r.requestHeaders;
  const resHdr = r.responseHeaders || r.resHeaders;
  return (
    <div className="ar-detail">
      {(r.error || r.errorText || r.failureReason) && (
        <div className="view-error" style={{ margin: '0 0 10px' }}>
          {r.failureReason || r.error || r.errorText}
        </div>
      )}
      {r.curlCmd && (
        <>
          <div className="hdr-title">cURL</div>
          <pre className="body-pre">{r.curlCmd}</pre>
        </>
      )}
      {reqHdr && (
        <>
          <div className="hdr-title">Request headers</div>
          <pre className="body-pre">{reqHdr}</pre>
        </>
      )}
      {r.requestContent && (
        <>
          <div className="hdr-title">Request body {r.requestContentType ? `(${r.requestContentType})` : ''}</div>
          <pre className="body-pre">{r.requestContent}</pre>
        </>
      )}
      {resHdr && (
        <>
          <div className="hdr-title">Response headers</div>
          <pre className="body-pre">{resHdr}</pre>
        </>
      )}
      {r.responseContent && (
        <>
          <div className="hdr-title">Response body {r.responseContentType ? `(${r.responseContentType})` : ''}</div>
          <pre className="body-pre">{r.responseContent}</pre>
        </>
      )}
      {r.serverLogs && (
        <>
          <div className="hdr-title">Server logs</div>
          <pre className="body-pre">{r.serverLogs}</pre>
        </>
      )}
    </div>
  );
}

function ResultsTable({ rows }: { rows: TestCaseReport[] }) {
  const [open, setOpen] = useState<number | null>(null);
  return (
    <table className="grid-table">
      <thead>
        <tr>
          <th></th>
          <th>Test</th>
          <th>Method</th>
          <th>URL</th>
          <th>Status</th>
          <th className="num">Code</th>
          <th className="num">Time</th>
        </tr>
      </thead>
      <tbody>
        {rows.map((r, i) => {
          const name = r.workflowName || r.testIdentifier || `#${i + 1}`;
          const time = r.averageExecutionTime ?? r.executionTime;
          const isOpen = open === i;
          return (
            <Fragment key={i}>
              <tr className={stClass(r.status)} onClick={() => setOpen(isOpen ? null : i)} style={{ cursor: 'pointer' }}>
                <td className="muted">{isOpen ? '\u25be' : '\u25b8'}</td>
                <td className="mono">{name}</td>
                <td className="mono">{r.method || ''}</td>
                <td className="mono url" style={{ maxWidth: 320 }}>{r.actualUrl || r.url || ''}</td>
                <td><span className={'st-badge ' + stClass(r.status)}>{r.status}</span></td>
                <td className="num mono">{r.responseStatusCode ?? ''}</td>
                <td className="num mono">{time != null ? fmtMs(time) : ''}</td>
              </tr>
              {isOpen && (
                <tr className="ar-detail-row">
                  <td></td>
                  <td colSpan={6}>
                    <Detail r={r} />
                  </td>
                </tr>
              )}
            </Fragment>
          );
        })}
      </tbody>
    </table>
  );
}

function StatsTable({ rows }: { rows: TestCaseStat[] }) {
  return (
    <table className="grid-table">
      <thead>
        <tr>
          <th>Run</th>
          <th>Source</th>
          <th>Workflow</th>
          <th>Test case</th>
          <th>Status</th>
          <th className="num">Time</th>
        </tr>
      </thead>
      <tbody>
        {rows.map((s, i) => (
          <tr key={i} className={stClass(s.status)}>
            <td className="mono">{s.identifier}</td>
            <td className="mono">{s.sourceFileName}</td>
            <td className="mono">{s.workflowName}</td>
            <td className="mono">{s.testCaseName}</td>
            <td><span className={'st-badge ' + stClass(s.status)}>{s.status}</span></td>
            <td className="num mono">{s.executionTime != null ? fmtMs(s.executionTime) : ''}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

export default function ApiReportView({ data }: { data: ApiReportData | ApiIndexData }) {
  const groups = data.suiteStats?.groupStats ?? [];
  const isFull = data.type === 'api-report';
  const runs = isFull ? Object.entries((data as ApiReportData).testcaseReports || {}) : [];
  const stats = data.testcaseStats ?? [];
  return (
    <div className="ar">
      <section className="view-section">
        <SummaryCards s={data.suiteStats} />
      </section>

      {groups.length > 0 && (
        <section className="view-section">
          <h3>Groups</h3>
          <table className="grid-table">
            <thead>
              <tr>
                <th>Source</th>
                <th className="num">Total</th>
                <th className="num">Failed</th>
                <th className="num">Skipped</th>
                <th className="num">Runs</th>
                <th className="num">Time</th>
              </tr>
            </thead>
            <tbody>
              {groups.map((g, i) => (
                <tr key={i} className={g.failedTestCount ? 'st-fail' : ''}>
                  <td className="mono">{g.sourceFile}</td>
                  <td className="num mono">{g.totalTestCount}</td>
                  <td className="num mono">{g.failedTestCount}</td>
                  <td className="num mono">{g.skippedTestCount}</td>
                  <td className="num mono">{g.totalRuns}</td>
                  <td className="num mono">{fmtMs(g.executionTime)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      )}

      {isFull ? (
        runs.map(([identifier, rows]) => (
          <section className="view-section" key={identifier}>
            <h3>
              {identifier} <span className="muted small">({rows.length} tests)</span>
            </h3>
            <ResultsTable rows={rows} />
          </section>
        ))
      ) : (
        <section className="view-section">
          <h3>Test cases</h3>
          {stats.length === 0 ? <div className="view-note">No per-test stats.</div> : <StatsTable rows={stats} />}
        </section>
      )}
      {isFull && runs.length === 0 && <div className="view-note">No test results in this report.</div>}
    </div>
  );
}
