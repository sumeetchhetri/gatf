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
import { Fragment, useState } from 'react';
import { api } from '../api';
import type { ApiReportData, ApiIndexData, TestCaseReport, TestCaseStat, TestSuiteStats } from '../api';
import { fmtMs } from '../util';
import ApiResult from '../components/ApiResult';

function stClass(s: string): string {
  const u = (s || '').toLowerCase();
  if (u === 'success' || u === 'pass' || u === 'passed') return 'st-pass';
  if (u === 'skipped' || u === 'skip') return 'st-warn';
  return 'st-fail';
}

// The server's replayTest resolves a test by (testcaseFileName, testCaseName). TestCaseReport.testIdentifier
// is built as "identifier\n[sourcefile\n]name" (sourcefile is included only when identifier != sourcefile),
// so the last segment is the test name and — when present — the middle segment is the source file. Fall back
// to the group identifier (the testcaseReports map key = testCase.getIdentifier(), the source file for a
// top-level suite) when the report carries no explicit source file.
function replayTarget(r: TestCaseReport, groupId: string): { file: string; name: string } {
  const parts = String(r.testIdentifier ?? '')
    .split('\n')
    .map((s) => s.trim())
    .filter(Boolean);
  const name = parts.length ? parts[parts.length - 1] : String(r.workflowName ?? '');
  const file = parts.length >= 3 ? parts[parts.length - 2] : parts.length === 1 ? parts[0] : groupId;
  return { file: file || groupId, name };
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

function Detail({
  r,
  onReplay,
  busy,
  err,
}: {
  r: TestCaseReport;
  onReplay: () => void;
  busy: '' | 'replay';
  err?: string;
}) {
  return (
    <div className="ar-detail">
      <div className="ar-detail-bar">
        <button
          className="btn small primary"
          disabled={!!busy}
          onClick={onReplay}
          title="Re-run the captured request (URL / body / headers exactly as sent)"
        >
          {busy === 'replay' ? 'Replaying…' : '\u21bb Replay'}
        </button>
        <span className="muted small">
          Replay re-sends the captured request. To run a test as defined, use ▶ Play in Test Files.
        </span>
        {err && <span className="st-badge st-fail" style={{ marginLeft: 8 }}>{err}</span>}
      </div>
      <ApiResult r={r} />
    </div>
  );
}

function ResultsTable({ rows, groupId }: { rows: TestCaseReport[]; groupId: string }) {
  const [open, setOpen] = useState<number | null>(null);
  const [over, setOver] = useState<Record<number, TestCaseReport>>({});
  const [busy, setBusy] = useState<Record<number, '' | 'replay'>>({});
  const [errs, setErrs] = useState<Record<number, string>>({});
  const [replayed, setReplayed] = useState<Record<number, boolean>>({});
  const view = (i: number) => over[i] ?? rows[i];

  const replay = (i: number) => {
    const r = view(i);
    const { file, name } = replayTarget(r, groupId);
    if (!file || !name) {
      setErrs((e) => ({ ...e, [i]: 'Cannot resolve test file / name' }));
      return;
    }
    setBusy((b) => ({ ...b, [i]: 'replay' }));
    setErrs((e) => ({ ...e, [i]: '' }));
    api
      .replayApiTest(file, name, r)
      .then((nr) => {
        setOver((o) => ({ ...o, [i]: nr }));
        setReplayed((s) => ({ ...s, [i]: true }));
      })
      .catch((err) => setErrs((e) => ({ ...e, [i]: err.message })))
      .finally(() => setBusy((b) => ({ ...b, [i]: '' })));
  };

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
        {rows.map((_row, i) => {
          const r = view(i);
          const name = r.workflowName || r.testIdentifier || `#${i + 1}`;
          const time = r.averageExecutionTime ?? r.executionTime;
          const isOpen = open === i;
          return (
            <Fragment key={i}>
              <tr className={stClass(r.status)} onClick={() => setOpen(isOpen ? null : i)} style={{ cursor: 'pointer' }}>
                <td className="muted">{isOpen ? '\u25be' : '\u25b8'}</td>
                <td className="mono">
                  {String(name).split('\n').pop()}
                  {replayed[i] && (
                    <span className="muted small" title="Replayed">
                      {' '}
                      {'\u21bb'}
                    </span>
                  )}
                </td>
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
                    <Detail r={r} onReplay={() => replay(i)} busy={busy[i] || ''} err={errs[i]} />
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
            <ResultsTable rows={rows} groupId={identifier} />
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
