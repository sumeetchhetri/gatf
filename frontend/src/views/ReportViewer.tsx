import { useEffect, useState } from 'react';
import { api } from '../api';
import { reportSidecarUrl } from '../util';
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
  onOpenTrace,
}: {
  data: SeleniumTestData;
  onOpenTrace: (url: string) => void;
}) {
  const logs =
    typeof data.logs === 'string' ? data.logs : data.logs == null ? '' : JSON.stringify(data.logs, null, 2);
  const traceUrl = data.tracePath
    ? data.tracePath.startsWith('/')
      ? data.tracePath
      : '/reports/' + data.tracePath
    : null;
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
          {data.type === 'selenium-test' && <TestView data={data as SeleniumTestData} onOpenTrace={onOpenTrace} />}
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
