import type { DistributedLoadData } from '../api';
import { fmtMs } from '../util';

function dataUrlFromHref(href: string): string {
  const base = href.startsWith('/') ? href : '/reports/' + href;
  return base.replace(/\.html$/, '.data.json');
}

export default function DistributedLoadView({
  data,
  onOpenReport,
}: {
  data: DistributedLoadData;
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
                  <button className="linkish mono" onClick={() => onOpenReport(dataUrlFromHref(r.url))}>
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
