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
import type { Trace } from '../trace/types';
import { Sparkline } from './Sparkline';
import { fmtBytes, fmtMs } from '../util';

const VITAL_LABELS: Array<[string, string]> = [
  ['fcp', 'First Contentful Paint'],
  ['lcp', 'Largest Contentful Paint'],
  ['fp', 'First Paint'],
  ['ttfb', 'Time to First Byte'],
  ['domInteractive', 'DOM Interactive'],
  ['domContentLoaded', 'DOMContentLoaded'],
  ['load', 'Load'],
];

export function PerfTab({ trace, currentTime }: { trace: Trace; currentTime: number }) {
  const perf = trace.perf;
  const mem = trace.memory;

  const metricKeys = useMemo(() => {
    const keys = new Set<string>();
    for (const s of perf?.samples ?? []) {
      for (const k of Object.keys(s)) if (k !== 'ts') keys.add(k);
    }
    return [...keys].sort();
  }, [perf]);

  const [metric, setMetric] = useState<string>('');
  const activeMetric = metric || metricKeys[0] || '';

  if (!perf && !mem) return <div className="empty-tab">No performance data in this trace.</div>;

  const vitals = perf?.webVitals ?? {};
  const hasVitals = Object.keys(vitals).length > 0;

  const metricPoints =
    perf?.samples
      ?.filter((s) => typeof s[activeMetric] === 'number')
      .map((s) => ({ x: s.ts, y: s[activeMetric] as number })) ?? [];

  const usedPoints =
    mem?.samples?.filter((s) => s.jsHeapUsed != null).map((s) => ({ x: s.ts, y: s.jsHeapUsed as number })) ?? [];
  const totalPoints =
    mem?.samples?.filter((s) => s.jsHeapTotal != null).map((s) => ({ x: s.ts, y: s.jsHeapTotal as number })) ?? [];
  const lastUsed = usedPoints.length ? usedPoints[usedPoints.length - 1].y : null;
  const peakUsed = usedPoints.length ? Math.max(...usedPoints.map((p) => p.y)) : null;

  return (
    <div className="perf">
      {hasVitals && (
        <>
          <div className="hdr-title">Web Vitals</div>
          <div className="vitals">
            {VITAL_LABELS.filter(([k]) => vitals[k] != null).map(([k, label]) => (
              <div key={k} className="vital-card">
                <div className="vital-val">{fmtMs(vitals[k] as number)}</div>
                <div className="vital-label">{label}</div>
                <div className="vital-key mono">{k}</div>
              </div>
            ))}
          </div>
        </>
      )}

      {metricKeys.length > 0 && (
        <div className="perf-chart">
          <div className="chart-head">
            <span className="hdr-title">Metric series</span>
            <select className="inp" value={activeMetric} onChange={(e) => setMetric(e.target.value)}>
              {metricKeys.map((k) => (
                <option key={k} value={k}>
                  {k}
                </option>
              ))}
            </select>
          </div>
          <Sparkline
            series={[{ points: metricPoints, color: '#4ea1ff' }]}
            xMin={trace.t0}
            xMax={trace.t1}
            marker={currentTime}
            width={760}
          />
        </div>
      )}

      {(usedPoints.length > 0 || totalPoints.length > 0) && (
        <div className="perf-chart">
          <div className="chart-head">
            <span className="hdr-title">JS heap</span>
            <span className="muted small">
              current {fmtBytes(lastUsed)} · peak {fmtBytes(peakUsed)}
            </span>
          </div>
          <Sparkline
            series={[
              { points: totalPoints, color: '#3a4d66', label: 'total' },
              { points: usedPoints, color: '#4ade80', label: 'used' },
            ]}
            xMin={trace.t0}
            xMax={trace.t1}
            marker={currentTime}
            width={760}
          />
          <div className="legend">
            <span className="lg"><i style={{ background: '#4ade80' }} /> used</span>
            <span className="lg"><i style={{ background: '#3a4d66' }} /> total</span>
          </div>
        </div>
      )}
    </div>
  );
}
