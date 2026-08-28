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
import type { LoadReportData } from '../api';
import { fmtMs, reportSidecarUrl, reportHtmlUrl } from '../util';

// mirror index-load.vm: the sample url encodes an epoch-ms timestamp
function sampleDate(url: string): string {
  const dot = url.indexOf('.');
  if (dot === -1) return '';
  const dash = url.lastIndexOf('-', dot);
  const timems = dash === -1 ? url.substring(0, dot) : url.substring(dash + 1, dot);
  const n = parseInt(timems, 10);
  if (isNaN(n)) return '';
  return new Date(n).toLocaleString();
}

export default function LoadReportView({
  data,
  baseDir,
  onOpenReport,
}: {
  data: LoadReportData;
  baseDir: string;
  onOpenReport: (dataUrl: string) => void;
}) {
  const s = data.suiteStats;
  const passed = s.totalTestCount - s.failedTestCount - s.skippedTestCount;
  const tps = s.executionTime ? Math.floor(((s.totalTestCount - s.skippedTestCount) * 1000) / s.executionTime) : 0;
  const cards: [string, string, string?][] = [
    ['Suite runs', String(s.totalSuiteRuns)],
    ['Concurrent users', String(s.totalUserSuiteRuns)],
    ['Total tests', String(s.totalTestCount)],
    ['Passed', String(passed), 'st-pass'],
    ['Failed', String(s.failedTestCount), s.failedTestCount ? 'st-fail' : undefined],
    ['Transactions/sec', String(tps)],
    ['Total time', fmtMs(s.executionTime)],
    ['Multi-run (fail)', `${s.totalRuns} (${s.failedRuns})`],
  ];

  const samples = data.loadTestResources ?? [];
  const nodes = data.nodes ?? [];

  return (
    <div className="ar">
      <section className="view-section">
        <div className="vitals">
          {cards.map(([label, val, cls]) => (
            <div className="vital-card" key={label}>
              <div className={'vital-val ' + (cls || '')}>{val}</div>
              <div className="vital-label">{label}</div>
            </div>
          ))}
        </div>
      </section>

      {nodes.length > 0 ? (
        <section className="view-section">
          <h3>Distributed nodes</h3>
          <ul className="rv-final-list">
            {nodes.map((node, i) => (
              <li key={i}>
                <button className="linkish mono" onClick={() => onOpenReport(reportSidecarUrl((data.nodeurls ?? [])[i] || '', baseDir))}>
                  Node — {node}
                </button>
              </li>
            ))}
          </ul>
        </section>
      ) : (
        <section className="view-section">
          <h3>
            Sample runs <span className="muted small">({samples.length})</span>
          </h3>
          {samples.length === 0 ? (
            <div className="view-note">No load-test samples recorded.</div>
          ) : (
            <table className="grid-table">
              <thead>
                <tr>
                  <th>Sample</th>
                  <th>When</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {samples.map((r, i) => (
                  <tr key={i}>
                    <td className="mono">{r.title}</td>
                    <td className="muted small">{sampleDate(r.url)}</td>
                    <td className="row-actions">
                      <button className="btn small" onClick={() => onOpenReport(reportSidecarUrl(r.url, baseDir))}>
                        Open in viewer
                      </button>
                      <a className="btn ghost small" href={reportHtmlUrl(r.url, baseDir)} target="_blank" rel="noopener">
                        HTML &#8599;
                      </a>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </section>
      )}
    </div>
  );
}
