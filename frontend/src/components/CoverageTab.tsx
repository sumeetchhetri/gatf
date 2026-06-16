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
