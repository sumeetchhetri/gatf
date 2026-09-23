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
import { useEffect, useState } from 'react';
import { api } from '../api';
import { reportHtmlUrl, reportSidecarUrl } from '../util';
import type {
  ReportData,
  SeleniumSummaryData,
  SeleniumTestData,
  SeleniumFinalData,
  SeleniumSubtest,
  ApiReportData,
  ApiIndexData,
  LoadReportData,
  DistributedLoadData,
  TAReportData,
} from '../api';
import ApiReportView from './ApiReportView';
import LoadReportView from './LoadReportView';
import DistributedLoadView from './DistributedLoadView';
import TAReportView from './TAReportView';

function statusClass(s: string): string {
  const u = (s || '').toUpperCase();
  if (u === 'SUCCESS' || u === 'PASS' || u === 'PASSED') return 'st-pass';
  if (u === 'FAILED' || u === 'FAIL' || u === 'ERROR') return 'st-fail';
  return 'st-warn';
}

function SummaryView({
  data,
  baseDir,
  onOpenReport,
}: {
  data: SeleniumSummaryData;
  baseDir: string;
  onOpenReport: (dataUrl: string) => void;
}) {
  const nodes = Object.entries(data.testsMap || {});
  return (
    <div className="rv-summary">
      {nodes.map(([node, runs]) => (
        <div className="rv-node" key={node}>
          <div className="rv-node-head">Node: {node}</div>
          {runs.map((run, ri) => {
            const tests = Object.entries(run || {});
            return (
              <div className="rv-run" key={ri}>
                <div className="rv-run-head">Run {ri + 1}</div>
                {tests.map(([test, browsers]) => (
                  <div className="rv-test" key={test}>
                    <div className="rv-test-head">{test}</div>
                    {Object.entries(browsers || {}).map(([browser, subtests]) => (
                      <div className="rv-browser" key={browser}>
                        <div className="rv-browser-head">{browser}</div>
                        <table className="grid-table">
                          <thead>
                            <tr>
                              <th>Subtest</th>
                              <th>Status</th>
                              <th className="num">Time</th>
                              <th>Error</th>
                            </tr>
                          </thead>
                          <tbody>
                            {(subtests as SeleniumSubtest[]).map((tc, i) => {
                              const name = String(tc[0] ?? '');
                              const href = String(tc[1] ?? '');
                              const status = String(tc[2] ?? '');
                              const error = tc[3] == null ? '' : String(tc[3]);
                              const time = tc[4] == null ? '' : String(tc[4]);
                              return (
                                <tr key={i} className={statusClass(status)}>
                                  <td>
                                    {href ? (
                                      <button
                                        className="linkish mono"
                                        onClick={() => onOpenReport(reportSidecarUrl(href, baseDir))}
                                      >
                                        {name}
                                      </button>
                                    ) : (
                                      <span className="mono">{name}</span>
                                    )}
                                  </td>
                                  <td>
                                    <span className={'st-badge ' + statusClass(status)}>{status}</span>
                                  </td>
                                  <td className="num mono">{time}</td>
                                  <td className="small muted" style={{ whiteSpace: 'pre-wrap' }}>
                                    {error}
                                  </td>
                                </tr>
                              );
                            })}
                          </tbody>
                        </table>
                      </div>
                    ))}
                  </div>
                ))}
              </div>
            );
          })}
        </div>
      ))}
      {nodes.length === 0 && <div className="view-note">This run produced no selenium results.</div>}
    </div>
  );
}

function TestView({
  data,
  baseDir,
  onOpenTrace,
}: {
  data: SeleniumTestData;
  baseDir: string;
  onOpenTrace: (url: string) => void;
}) {
  const logs =
    typeof data.logs === 'string' ? data.logs : data.logs == null ? '' : JSON.stringify(data.logs, null, 2);
  // tracePath / screenshot are report-relative names (the report lives in the run's session folder, so they must
  // resolve against the report's own folder; '/reports/' + name missed that folder and 404'd).
  const traceUrl = data.tracePath ? reportHtmlUrl(data.tracePath, baseDir) : null;
  const shotUrl = data.screenshot ? reportHtmlUrl(data.screenshot, baseDir) : null;
  return (
    <div className="rv-test-detail">
      <div className="rv-test-status">
        <span className={'st-badge ' + (data.status ? 'st-pass' : 'st-fail')}>
          {data.status ? 'SUCCESS' : 'FAILED'}
        </span>
        {traceUrl && (
          <button className="btn small" onClick={() => onOpenTrace(traceUrl)}>
            Open in Trace Viewer
          </button>
        )}
      </div>

      {shotUrl && (
        <figure className="rv-shot">
          <figcaption className="rv-code-title">Failure screenshot</figcaption>
          <a href={shotUrl} target="_blank" rel="noreferrer">
            <img src={shotUrl} alt="Screenshot taken when the step failed" />
          </a>
        </figure>
      )}

      <h3 className="rv-code-title mono">{data.selFileName}</h3>
      <pre className="body-pre">{data.selCode}</pre>

      {data.javaCode && (
        <details className="rv-details">
          <summary className="mono">{data.javaFileName || 'Generated Java'}</summary>
          <pre className="body-pre">{data.javaCode}</pre>
        </details>
      )}

      {logs && (
        <>
          <h3 className="rv-code-title">Logs</h3>
          <pre className="body-pre">{logs}</pre>
        </>
      )}
    </div>
  );
}

function FinalView({
  data,
  baseDir,
  onOpenReport,
}: {
  data: SeleniumFinalData;
  baseDir: string;
  onOpenReport: (dataUrl: string) => void;
}) {
  const nodes = Object.entries(data.indexes || {});
  return (
    <div className="rv-final">
      <div className="muted small">{data.dt}</div>
      {nodes.map(([node, runs]) => (
        <div className="rv-node" key={node}>
          <div className="rv-node-head">Node: {node}</div>
          <ul className="rv-final-list">
            {Object.entries(runs || {}).map(([run, htmlName]) => (
              <li key={run}>
                <button className="linkish mono" onClick={() => onOpenReport(reportSidecarUrl(String(htmlName), baseDir))}>
                  Run {run} — {String(htmlName)}
                </button>
              </li>
            ))}
          </ul>
        </div>
      ))}
      {nodes.length === 0 && <div className="view-note">No runs recorded.</div>}
    </div>
  );
}

export default function ReportViewer({
  dataUrl,
  onBack,
  onOpenTrace,
  onOpenReport,
}: {
  dataUrl: string;
  onBack: () => void;
  onOpenTrace: (url: string) => void;
  onOpenReport: (dataUrl: string) => void;
}) {
  const [data, setData] = useState<ReportData | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  // directory of the current report, used to resolve its intra-report links
  const baseDir = dataUrl.replace(/[^/]*$/, '');

  useEffect(() => {
    setLoading(true);
    setError(null);
    setData(null);
    api
      .reportData(dataUrl)
      .then(setData)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  }, [dataUrl]);

  const title =
    data?.type === 'selenium-summary'
      ? 'Selenium run summary'
      : data?.type === 'selenium-test'
        ? 'Selenium test'
        : data?.type === 'selenium-final'
          ? 'Selenium index'
          : data?.type === 'api-report'
            ? 'API test report'
            : data?.type === 'api-index'
              ? 'API test index'
              : data?.type === 'load-report'
                ? 'Load test report'
                : data?.type === 'load-distributed'
                  ? 'Distributed load report'
                  : data?.type === 'ta-report'
                    ? 'Test analysis'
                    : 'Report';

  return (
    <div className="view">
      <div className="view-head">
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <button className="btn ghost small" onClick={onBack}>
            &#8592; Reports
          </button>
          <h2>{title}</h2>
        </div>
        <a className="btn ghost small" href={dataUrl.replace(/\.data\.json$/, '.html')} target="_blank" rel="noopener">
          Legacy HTML &#8599;
        </a>
      </div>

      {loading && <div className="view-note">Loading…</div>}
      {error && (
        <div className="view-error">
          Could not load report data ({error}). Reports generated before this build have no{' '}
          <code>.data.json</code> sidecar — open the legacy HTML instead.
        </div>
      )}

      {data && !loading && (
        <>
          {data.type === 'selenium-summary' && (
            <SummaryView data={data as SeleniumSummaryData} baseDir={baseDir} onOpenReport={onOpenReport} />
          )}
          {data.type === 'selenium-test' && (
            <TestView data={data as SeleniumTestData} baseDir={baseDir} onOpenTrace={onOpenTrace} />
          )}
          {data.type === 'selenium-final' && (
            <FinalView data={data as SeleniumFinalData} baseDir={baseDir} onOpenReport={onOpenReport} />
          )}
          {data.type === 'api-report' && <ApiReportView data={data as ApiReportData} />}
          {data.type === 'api-index' && <ApiReportView data={data as ApiIndexData} />}
          {data.type === 'load-report' && (
            <LoadReportView data={data as LoadReportData} baseDir={baseDir} onOpenReport={onOpenReport} />
          )}
          {data.type === 'load-distributed' && (
            <DistributedLoadView data={data as DistributedLoadData} baseDir={baseDir} onOpenReport={onOpenReport} />
          )}
          {data.type === 'ta-report' && <TAReportView data={data as TAReportData} />}
          {![
            'selenium-summary',
            'selenium-test',
            'selenium-final',
            'api-report',
            'api-index',
            'load-report',
            'load-distributed',
            'ta-report',
          ].includes(data.type) && (
            <>
              <div className="view-note muted small">
                No React viewer for “{data.type}” reports yet — raw data below.
              </div>
              <pre className="body-pre">{JSON.stringify(data, null, 2)}</pre>
            </>
          )}
        </>
      )}
    </div>
  );
}
