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
import type { DistributedLoadData } from '../api';
import { fmtMs, reportSidecarUrl } from '../util';

export default function DistributedLoadView({
  data,
  baseDir,
  onOpenReport,
}: {
  data: DistributedLoadData;
  baseDir: string;
  onOpenReport: (dataUrl: string) => void;
}) {
  const nodes = data.nodes ?? [];
  return (
    <div className="ar">
      <div className="view-note muted small">Distributed load test — per-node summary.</div>
      <section className="view-section">
        <table className="grid-table">
          <thead>
            <tr>
              <th>Node</th>
              <th className="num">Total</th>
              <th className="num">Failed</th>
              <th className="num">Runs</th>
              <th className="num">Failed runs</th>
              <th className="num">Suite runs</th>
              <th className="num">Time</th>
            </tr>
          </thead>
          <tbody>
            {nodes.map((node, i) => {
              const s = data.suiteStats?.[i];
              return (
                <tr key={i} className={s && s.failedTestCount ? 'st-fail' : ''}>
                  <td className="mono">{node}</td>
                  <td className="num mono">{s?.totalTestCount ?? ''}</td>
                  <td className="num mono">{s?.failedTestCount ?? ''}</td>
                  <td className="num mono">{s?.totalRuns ?? ''}</td>
                  <td className="num mono">{s?.failedRuns ?? ''}</td>
                  <td className="num mono">{s?.totalSuiteRuns ?? ''}</td>
                  <td className="num mono">{s ? fmtMs(s.executionTime) : ''}</td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </section>

      {(data.loadTestResources ?? []).map((samples, ni) =>
        samples && samples.length > 0 ? (
          <section className="view-section" key={ni}>
            <h3>
              {nodes[ni] || `Node ${ni + 1}`} <span className="muted small">samples</span>
            </h3>
            <ul className="rv-final-list">
              {samples.map((r, i) => (
                <li key={i}>
                  <button className="linkish mono" onClick={() => onOpenReport(reportSidecarUrl(r.url, baseDir))}>
                    {r.title}
                  </button>
                </li>
              ))}
            </ul>
          </section>
        ) : null,
      )}
    </div>
  );
}
