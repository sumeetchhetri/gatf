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
import { useMemo, useState } from 'react';
import type { RunResults, ResultTest, TestStatus } from '../report/results';
import { allTags, filterTests, flaky, slowest, slowestFiles, timeline, utilisation } from '../report/results';
import { SummaryTiles } from '../views/MergedReportView';
import AttachmentsGallery from './AttachmentsGallery';
import RaiseDefect from './RaiseDefect';
import { fmtMs } from '../util';

const STATUSES: TestStatus[] = ['passed', 'failed', 'flaky', 'skipped'];
const BAR_COLOR: Record<TestStatus, string> = {
  passed: 'var(--green)',
  failed: 'var(--red)',
  flaky: 'var(--amber, #d97706)',
  skipped: 'var(--muted)',
};

function statusCls(s: TestStatus): string {
  return s === 'passed' ? 'st-pass' : s === 'failed' ? 'st-fail' : s === 'flaky' ? 'st-warn' : '';
}

/** Speedboard-style duration timeline: one row per worker lane, one bar per test. */
export function RunTimeline({ tests }: { tests: ResultTest[] }) {
  const m = useMemo(() => timeline(tests), [tests]);
  if (m.lanes.length === 0 || m.totalMs <= 0) return <div className="view-note">No timed tests to chart.</div>;
  const rowH = 18;
  const labelW = 150;
  const w = 900;
  const plotW = w - labelW - 8;
  const x = (ms: number) => labelW + (ms / m.totalMs) * plotW;
  const ticks = 5;
  return (
    <div className="ri-timeline">
      <div className="muted small">
        {m.lanes.length} lane(s) · wall {fmtMs(m.totalMs)} · workers busy {Math.round(utilisation(m) * 100)}%
        {!m.exact && ' · estimated (results without start times: tests laid end to end)'}
      </div>
      <svg
        role="img"
        aria-label="Test duration timeline"
        viewBox={`0 0 ${w} ${m.lanes.length * rowH + 18}`}
        width="100%"
        style={{ maxWidth: w }}
      >
        {Array.from({ length: ticks + 1 }, (_, i) => {
          const ms = (m.totalMs * i) / ticks;
          return (
            <g key={i}>
              <line x1={x(ms)} x2={x(ms)} y1={0} y2={m.lanes.length * rowH} stroke="var(--border)" />
              <text x={x(ms)} y={m.lanes.length * rowH + 13} fontSize="10" fill="var(--muted)" textAnchor="middle">
                {fmtMs(ms)}
              </text>
            </g>
          );
        })}
        {m.lanes.map((l, li) => (
          <g key={l.key}>
            <text x={0} y={li * rowH + 13} fontSize="11" fill="var(--fg)">
              {l.label}
            </text>
            {l.bars.map((b, bi) => (
              <rect
                key={bi}
                data-testid="tl-bar"
                x={x(b.start)}
                y={li * rowH + 3}
                width={Math.max(1, x(b.end) - x(b.start))}
                height={rowH - 6}
                rx={2}
                fill={BAR_COLOR[b.test.status]}
              >
                <title>{b.test.title + ' — ' + fmtMs(b.test.durationMs) + ' (' + b.test.status + ')'}</title>
              </rect>
            ))}
          </g>
        ))}
      </svg>
    </div>
  );
}

/**
 * M12: what is slow and what is flaky in a run: summary, timeline, slowest tests and files, flaky list, and a
 * filterable test list (status / tag / text).
 */
export default function RunInsights({
  data,
  showSummary = true,
  baseDir = '/reports/',
  onOpenTrace,
}: {
  data: RunResults;
  /** false when the host view already shows the totals (merged report) */
  showSummary?: boolean;
  /** folder the results file was loaded from (resolves attachments) */
  baseDir?: string;
  onOpenTrace?: (url: string) => void;
}) {
  const [statuses, setStatuses] = useState<Set<TestStatus>>(new Set());
  const [tag, setTag] = useState('');
  const [text, setText] = useState('');
  const [topN, setTopN] = useState(10);
  const tags = useMemo(() => allTags(data.tests), [data]);
  const shown = useMemo(() => filterTests(data.tests, { statuses, tag, text }), [data, statuses, tag, text]);
  const slow = useMemo(() => slowest(shown, topN), [shown, topN]);
  const files = useMemo(() => slowestFiles(shown, topN), [shown, topN]);
  const flk = useMemo(() => flaky(shown), [shown]);
  const toggle = (s: TestStatus) => {
    const n = new Set(statuses);
    if (n.has(s)) n.delete(s);
    else n.add(s);
    setStatuses(n);
  };

  return (
    <div className="ri">
      {showSummary && <SummaryTiles s={data.summary} />}
      {(data.summary.workers ?? 1) > 1 || data.summary.shard ? (
        <div className="muted small">
          {data.summary.workers ?? 1} worker(s){data.summary.shard ? ' · shard ' + data.summary.shard : ''}
        </div>
      ) : null}

      <div className="ri-filters">
        {STATUSES.map((s) => (
          <label key={s} className="small">
            <input type="checkbox" checked={statuses.has(s)} onChange={() => toggle(s)} /> {s}
          </label>
        ))}
        <select aria-label="Tag" value={tag} onChange={(e) => setTag(e.target.value)}>
          <option value="">any tag</option>
          {tags.map((t) => (
            <option key={t} value={t}>
              {t}
            </option>
          ))}
        </select>
        <input
          aria-label="Search tests"
          placeholder="search title…"
          value={text}
          onChange={(e) => setText(e.target.value)}
        />
        <select aria-label="Top N" value={topN} onChange={(e) => setTopN(Number(e.target.value))}>
          {[10, 25, 50].map((n) => (
            <option key={n} value={n}>
              top {n}
            </option>
          ))}
        </select>
        <span className="muted small">
          {shown.length} of {data.tests.length} test(s)
        </span>
      </div>

      <h3>Timeline</h3>
      <RunTimeline tests={shown} />

      <div className="ri-cols">
        <section>
          <h3>Slowest tests</h3>
          <table className="grid-table" aria-label="Slowest tests">
            <tbody>
              {slow.map((t, i) => (
                <tr key={i} className={statusCls(t.status)}>
                  <td className="mono ellipsis" title={t.title}>
                    {t.title}
                  </td>
                  <td className="num mono">{fmtMs(t.durationMs)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
        <section>
          <h3>Slowest files</h3>
          <table className="grid-table" aria-label="Slowest files">
            <tbody>
              {files.map((f) => (
                <tr key={f.suite} className={f.failed ? 'st-fail' : f.flaky ? 'st-warn' : ''}>
                  <td className="mono ellipsis" title={f.suite}>
                    {f.suite}
                  </td>
                  <td className="num muted small">{f.tests} test(s)</td>
                  <td className="num mono">{fmtMs(f.totalMs)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      </div>

      <h3>Flaky ({flk.length})</h3>
      {flk.length === 0 ? (
        <div className="view-note">No flaky tests{shown.length !== data.tests.length ? ' in this selection' : ''}.</div>
      ) : (
        <table className="grid-table" aria-label="Flaky tests">
          <tbody>
            {flk.map((t, i) => (
              <tr key={i} className="st-warn">
                <td className="mono">{t.title}</td>
                <td className="num small">passed on attempt {t.attempts}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      <h3>Attachments</h3>
      <AttachmentsGallery tests={shown} runDir={data.dir} baseDir={baseDir} onOpenTrace={onOpenTrace} />

      <details className="rv-details" open={statuses.size === 1 && statuses.has('failed')}>
        <summary>All tests in the selection ({shown.length})</summary>
        <table className="grid-table">
          <thead>
            <tr>
              <th>Test</th>
              <th>Status</th>
              <th className="num">Time</th>
              <th>Error</th>
            </tr>
          </thead>
          <tbody>
            {shown.map((t, i) => (
              <tr key={i} className={statusCls(t.status)}>
                <td className="mono">{t.title}</td>
                <td>
                  <span className={'st-badge ' + statusCls(t.status)}>{t.status}</span>
                </td>
                <td className="num mono">{fmtMs(t.durationMs)}</td>
                <td className="small muted">
                  {t.error ? t.error.split('\n')[0] : ''}
                  {t.status === 'failed' && (
                    <RaiseDefect
                      report={{
                        status: 'Failed',
                        workflowName: t.suite,
                        testIdentifier: t.title,
                        error: t.error ? t.error.split('\n')[0] : 'Failed: ' + t.title,
                        errorText: t.error ?? '',
                      }}
                    />
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </details>
    </div>
  );
}
