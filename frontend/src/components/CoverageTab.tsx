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
import { useMemo } from 'react';
import type { Trace } from '../trace/types';
import { summarizeCoverage, type CovRow } from '../trace/coverage';
import { fmtBytes } from '../util';

function Bar({ pct }: { pct: number }) {
  const p = Math.max(0, Math.min(100, pct));
  return (
    <div className="cov-bar">
      <div className="cov-bar-fill" style={{ width: p + '%' }} />
      <span className="cov-bar-txt mono">{p.toFixed(0)}%</span>
    </div>
  );
}

function Table({ rows }: { rows: CovRow[] }) {
  if (rows.length === 0) return <div className="muted small">none</div>;
  return (
    <table className="grid-table cov-table">
      <thead>
        <tr>
          <th>File</th>
          <th>Coverage</th>
          <th>Used</th>
          <th>Total</th>
        </tr>
      </thead>
      <tbody>
        {rows.map((r, i) => (
          <tr key={i}>
            <td className="url" title={r.url}>
              {r.url}
            </td>
            <td className="cov-cell">
              <Bar pct={r.pct} />
            </td>
            <td className="mono num">{fmtBytes(r.usedBytes)}</td>
            <td className="mono num">{fmtBytes(r.totalBytes)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

export function CoverageTab({ trace }: { trace: Trace }) {
  const summary = useMemo(() => summarizeCoverage(trace.coverage), [trace.coverage]);
  if (!summary) return <div className="empty-tab">No coverage data in this trace.</div>;

  return (
    <div className="coverage">
      <div className="cov-section">
        <div className="hdr-title">
          JavaScript — {summary.jsTotals.pct.toFixed(0)}% of{' '}
          {fmtBytes(summary.jsTotals.total)} used
        </div>
        <Table rows={summary.js} />
      </div>
      <div className="cov-section">
        <div className="hdr-title">
          CSS rules — {summary.cssTotals.pct.toFixed(0)}% of tracked rule bytes used
        </div>
        <Table rows={summary.css} />
      </div>
      <div className="muted small cov-note">
        JS from Profiler.takePreciseCoverage (used = merged offset ranges with count&gt;0). CSS from
        CSS ruleUsage (used vs tracked rule spans).
      </div>
    </div>
  );
}
