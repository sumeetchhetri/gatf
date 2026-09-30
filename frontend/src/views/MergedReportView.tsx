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
import type { MergedIndexData, RunSummary } from '../api';
import { fmtMs } from '../util';

export function SummaryTiles({ s }: { s: RunSummary }) {
  const tiles: [string, number | string, string][] = [
    ['Total', s.total, ''],
    ['Passed', s.passed, 'st-pass'],
    ['Failed', s.failed, 'st-fail'],
    ['Flaky', s.flaky, 'st-warn'],
    ['Skipped', s.skipped, ''],
    ['Duration', fmtMs(s.durationMs), ''],
  ];
  return (
    <div className="mr-tiles">
      {tiles.map(([label, v, cls]) => (
        <div key={label} className={'mr-tile ' + cls}>
          <div className="mr-tile-v mono">{v}</div>
          <div className="muted small">{label}</div>
        </div>
      ))}
    </div>
  );
}

/** M7: the index written by `gatf merge-reports`: overall totals, every shard with its own reports, flaky + failures. */
export default function MergedReportView({
  data,
  baseDir,
  onOpenReport,
}: {
  data: MergedIndexData;
  baseDir: string;
  onOpenReport: (dataUrl: string) => void;
}) {
  return (
    <div className="rv-merged">
      <SummaryTiles s={data.summary} />
      <div className="muted small">
        {data.shards.length} shard(s) merged{data.generated ? ' \u00b7 ' + new Date(data.generated).toLocaleString() : ''}
      </div>

      <h3>Shards</h3>
      <table className="grid-table">
        <thead>
          <tr>
            <th>Shard</th>
            <th className="num">Passed</th>
            <th className="num">Failed</th>
            <th className="num">Flaky</th>
            <th className="num">Time</th>
            <th>Reports</th>
          </tr>
        </thead>
        <tbody>
          {data.shards.map((sh) => (
            <tr key={sh.name} className={sh.summary && sh.summary.failed > 0 ? 'st-fail' : ''}>
              <td className="mono" title={sh.source}>
                {sh.name}
              </td>
              <td className="num mono">{sh.summary?.passed ?? '\u2014'}</td>
              <td className="num mono">{sh.summary?.failed ?? '\u2014'}</td>
              <td className="num mono">{sh.summary?.flaky ?? '\u2014'}</td>
              <td className="num mono">{sh.summary ? fmtMs(sh.summary.durationMs) : '\u2014'}</td>
              <td className="row-actions">
                {sh.reports.map((r) => (
                  <button key={r.dataUrl} className="btn small" title={r.label} onClick={() => onOpenReport(baseDir + r.dataUrl)}>
                    {r.reportType ?? 'report'}
                  </button>
                ))}
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      {data.failures.length > 0 && (
        <>
          <h3>Failures ({data.failures.length})</h3>
          <ul className="mr-list">
            {data.failures.map((f, i) => (
              <li key={i} className="st-fail">
                <span className="mono">{f.title}</span> <span className="muted small">({f.shard})</span>
                {f.error && <div className="small muted">{f.error.split('\n')[0]}</div>}
              </li>
            ))}
          </ul>
        </>
      )}
      {data.flaky.length > 0 && (
        <>
          <h3>Flaky ({data.flaky.length})</h3>
          <ul className="mr-list">
            {data.flaky.map((t) => (
              <li key={t} className="st-warn mono">
                {t}
              </li>
            ))}
          </ul>
        </>
      )}
    </div>
  );
}
