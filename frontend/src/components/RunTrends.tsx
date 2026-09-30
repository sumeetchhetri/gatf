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
import type { HistoryRun } from '../report/history';
import { trend, unstable } from '../report/history';
import { fmtMs } from '../util';

const SERIES: { key: 'passed' | 'flaky' | 'failed' | 'skipped'; color: string }[] = [
  { key: 'passed', color: 'var(--green)' },
  { key: 'flaky', color: 'var(--amber, #d97706)' },
  { key: 'failed', color: 'var(--red)' },
  { key: 'skipped', color: 'var(--muted)' },
];

/** M12: stacked pass/flaky/fail/skip bars per run (oldest → newest) with the run duration as a line. */
export function TrendChart({ runs }: { runs: HistoryRun[] }) {
  const pts = useMemo(() => trend(runs), [runs]);
  if (pts.length === 0) return <div className="view-note">No run history yet.</div>;
  const w = 640,
    h = 160,
    padL = 34,
    padB = 18,
    padT = 6;
  const maxN = Math.max(1, ...pts.map((p) => p.passed + p.flaky + p.failed + p.skipped));
  const maxD = Math.max(1, ...pts.map((p) => p.durationMs));
  const slot = (w - padL) / pts.length;
  const bw = Math.max(4, Math.min(36, slot * 0.6));
  const y = (n: number) => padT + (h - padT - padB) * (1 - n / maxN);
  const yd = (ms: number) => padT + (h - padT - padB) * (1 - ms / maxD);
  const cx = (i: number) => padL + slot * i + slot / 2;
  return (
    <svg role="img" aria-label="Run trend" viewBox={`0 0 ${w} ${h}`} width="100%" style={{ maxWidth: w }}>
      <text x={0} y={padT + 8} fontSize="10" fill="var(--muted)">
        {maxN}
      </text>
      <text x={0} y={h - padB} fontSize="10" fill="var(--muted)">
        0
      </text>
      {pts.map((p, i) => {
        let acc = 0;
        return (
          <g key={p.id} data-testid="trend-run">
            {SERIES.map((s) => {
              const v = p[s.key];
              if (v <= 0) return null;
              const top = y(acc + v),
                bottom = y(acc);
              acc += v;
              return <rect key={s.key} x={cx(i) - bw / 2} y={top} width={bw} height={Math.max(0.5, bottom - top)} fill={s.color} />;
            })}
            <title>
              {new Date(p.time).toLocaleString() +
                ` — ${p.passed} passed, ${p.failed} failed, ${p.flaky} flaky, ${p.skipped} skipped, ${fmtMs(p.durationMs)}`}
            </title>
            <text x={cx(i)} y={h - 4} fontSize="9" fill="var(--muted)" textAnchor="middle">
              {i + 1}
            </text>
          </g>
        );
      })}
      {pts.length === 1 && <circle cx={cx(0)} cy={yd(pts[0].durationMs)} r={2.5} fill="var(--fg)" />}
      <polyline
        data-testid="trend-duration"
        fill="none"
        stroke="var(--fg)"
        strokeDasharray="3 2"
        points={pts.map((p, i) => `${cx(i)},${yd(p.durationMs)}`).join(' ')}
      />
    </svg>
  );
}

/** Trend chart + the least stable tests over the same runs. */
export default function RunTrends({ runs }: { runs: HistoryRun[] }) {
  const worst = useMemo(() => unstable(runs).slice(0, 15), [runs]);
  return (
    <div className="ri-trends">
      <div className="muted small">
        Last {runs.length} run(s): bars = passed / flaky / failed / skipped, dashed line = duration
      </div>
      <TrendChart runs={runs} />
      <h3>Least stable tests</h3>
      {worst.length === 0 ? (
        <div className="view-note">Every test passed first time in these runs.</div>
      ) : (
        <table className="grid-table" aria-label="Least stable tests">
          <thead>
            <tr>
              <th>Test</th>
              <th className="num">Failed</th>
              <th className="num">Flaky</th>
              <th className="num">Runs</th>
              <th className="num">Unstable</th>
              <th>Last</th>
            </tr>
          </thead>
          <tbody>
            {worst.map((u) => (
              <tr key={u.title}>
                <td className="mono">{u.title}</td>
                <td className="num mono">{u.failed}</td>
                <td className="num mono">{u.flaky}</td>
                <td className="num mono">{u.runs}</td>
                <td className="num mono">{Math.round(u.rate * 100)}%</td>
                <td>{u.last}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
