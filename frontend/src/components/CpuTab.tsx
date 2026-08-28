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
import { fileUrl } from '../trace/loadTrace';
import { fmtMs } from '../util';

interface CallFrame {
  functionName: string;
  url: string;
  lineNumber?: number;
}
interface CpuNode {
  id: number;
  callFrame: CallFrame;
  hitCount?: number;
}
interface CpuProfile {
  nodes?: CpuNode[];
  samples?: number[];
  timeDeltas?: number[];
  startTime?: number;
  endTime?: number;
}

function label(cf: CallFrame): string {
  const fn = cf.functionName && cf.functionName.length ? cf.functionName : '(anonymous)';
  let where = '';
  try {
    if (cf.url) {
      const u = new URL(cf.url);
      where = (u.pathname.split('/').pop() || u.host) + (cf.lineNumber != null ? ':' + (cf.lineNumber + 1) : '');
    }
  } catch {
    where = cf.url || '';
  }
  return where ? `${fn}  ·  ${where}` : fn;
}

export function CpuTab({ trace }: { trace: Trace }) {
  const prof = trace.cpuProfile as CpuProfile | null;

  const summary = useMemo(() => {
    if (!prof || !prof.nodes) return null;
    const byId = new Map<number, CpuNode>();
    for (const n of prof.nodes) byId.set(n.id, n);
    const rows = prof.nodes
      .map((n) => ({ label: label(n.callFrame), hits: n.hitCount ?? 0 }))
      .filter((r) => r.hits > 0)
      .sort((a, b) => b.hits - a.hits)
      .slice(0, 40);
    const totalHits = rows.reduce((s, r) => s + r.hits, 0) + prof.nodes.reduce((s, n) => s + (n.hitCount ?? 0), 0) - rows.reduce((s, r) => s + r.hits, 0);
    const durUs = prof.endTime != null && prof.startTime != null ? prof.endTime - prof.startTime : 0;
    return { rows, sampleCount: prof.samples?.length ?? 0, durMs: durUs / 1000, totalHits };
  }, [prof]);

  if (!prof) return <div className="empty-tab">No CPU profile in this trace.</div>;
  const dlUrl = trace.files['cpu.cpuprofile'] ? fileUrl(trace.files, 'cpu.cpuprofile') : null;

  return (
    <div className="cpu">
      <div className="cpu-head">
        <div className="kv inline">
          <b>Duration</b>
          <span>{summary ? fmtMs(summary.durMs) : '–'}</span>
          <b>Samples</b>
          <span>{summary?.sampleCount ?? 0}</span>
        </div>
        {dlUrl && (
          <a className="btn" href={dlUrl} download="cpu.cpuprofile">
            Download .cpuprofile
          </a>
        )}
      </div>
      <div className="muted small">
        Load the downloaded file into Chrome DevTools → Performance → "Load profile" for a full flame chart.
      </div>
      <div className="hdr-title">Top functions by self-time</div>
      {summary && summary.rows.length > 0 ? (
        <table className="grid-table">
          <thead>
            <tr>
              <th>Function</th>
              <th>Self samples</th>
              <th>Share</th>
            </tr>
          </thead>
          <tbody>
            {summary.rows.map((r, i) => {
              const share = summary.sampleCount > 0 ? (r.hits / summary.sampleCount) * 100 : 0;
              return (
                <tr key={i}>
                  <td className="mono ellipsis" title={r.label}>
                    {r.label}
                  </td>
                  <td className="mono num">{r.hits}</td>
                  <td className="mono num">{share.toFixed(1)}%</td>
                </tr>
              );
            })}
          </tbody>
        </table>
      ) : (
        <div className="muted small">Profile has no attributed samples.</div>
      )}
    </div>
  );
}
