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
import type { ReportArtifact } from '../api';
import { Bar } from '../components/CoverageTab';
import { asCoverageIndex, scriptRows, untestedRoutes, type CoverageIndexDto } from '../report/coverageIndex';
import { fmtBytes } from '../util';

// H6 (Δ155): untested routes and scripts for one run, from GET /api/coverage (the run's traces merged with a crawl map).
export default function CoverageReportView({ report, onBack }: { report: ReportArtifact; onBack: () => void }) {
  const [idx, setIdx] = useState<CoverageIndexDto | null>(null);
  const [crawls, setCrawls] = useState<string[]>([]);
  const [crawl, setCrawl] = useState<string>('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [unsupported, setUnsupported] = useState(false);

  const load = (c: string) => {
    setLoading(true);
    setError(null);
    api
      .coverageIndex(report.name, c || undefined)
      .then((raw) => {
        const i = asCoverageIndex(raw);
        setUnsupported(i === null);
        setIdx(i);
        if (i?.crawls) setCrawls(i.crawls);
      })
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false));
  };
  useEffect(() => load(crawl), [report.name, crawl]);

  const label = report.label ?? report.name;
  return (
    <div className="view">
      <div className="view-head">
        <h2>
          Coverage <span className="muted small mono">{label}</span>
        </h2>
        <div className="view-head-right">
          {crawls.length > 0 && (
            <select aria-label="Crawl map" value={crawl} onChange={(e) => setCrawl(e.target.value)}>
              <option value="">newest crawl map ({crawls[0]})</option>
              {crawls.map((c) => (
                <option key={c} value={c}>
                  {c}
                </option>
              ))}
            </select>
          )}
          <button className="btn ghost small" onClick={() => load(crawl)}>
            Refresh
          </button>
          <button className="btn ghost small" onClick={onBack}>
            {'\u2039'} Reports
          </button>
        </div>
      </div>
      {loading && <div className="view-note">Loading…</div>}
      {error && <div className="view-error">Could not load coverage ({error}).</div>}
      {!loading && !error && unsupported && (
        <div className="empty-tab">No coverage data: the server did not return a coverage index this page understands.</div>
      )}
      {!loading && !error && idx && <CoverageBody idx={idx} />}
    </div>
  );
}

function Notes({ idx }: { idx: CoverageIndexDto }) {
  return (
    <>
      {idx.notes.length > 0 && (
        <ul className="muted small cov-note">
          {idx.notes.map((n) => (
            <li key={n}>{n}</li>
          ))}
        </ul>
      )}
      {idx.skipped.length > 0 && (
        <details className="cov-section">
          <summary className="small">Skipped ({idx.skipped.length})</summary>
          <table className="grid-table">
            <tbody>
              {idx.skipped.map((s, i) => (
                <tr key={i}>
                  <td className="mono small">{s.file}</td>
                  <td className="small">{s.reason}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </details>
      )}
    </>
  );
}

function CoverageBody({ idx }: { idx: CoverageIndexDto }) {
  if (idx.status === 'no-data') {
    return (
      <>
        <div className="empty-tab">No coverage data for this run.</div>
        <Notes idx={idx} />
      </>
    );
  }
  const routes = untestedRoutes(idx);
  const scripts = scriptRows(idx);
  const tests = Object.entries(idx.tests);
  const css = idx.css;
  const cssPct = css.totalBytes > 0 ? (css.usedBytes / css.totalBytes) * 100 : 0;
  return (
    <div className="coverage">
      {idx.status === 'partial' && (
        <div className="view-note">
          Partial data: {idx.counts.tracesCoverageRead ?? idx.counts.tracesWithCoverage} of {idx.counts.traces} trace(s) gave
          coverage, {idx.skipped.length} item(s) skipped. The lists below cover what was read.
        </div>
      )}
      <div className="cov-section">
        <div className="hdr-title">
          Untested routes
          {routes && idx.crawl && (
            <span className="muted small">
              {' '}
              — {routes.length} of {idx.crawl.routes} crawled route(s) no test visited ({idx.crawl.source})
            </span>
          )}
        </div>
        {routes === null ? (
          <div className="muted small">No crawl map to compare with. Run a crawl (Crawl view) and save it beside the tests.</div>
        ) : routes.length === 0 ? (
          <div className="muted small">Every crawled route was visited by at least one test.</div>
        ) : (
          <table className="grid-table cov-table">
            <thead>
              <tr>
                <th>Route</th>
                <th>Crawl priority</th>
              </tr>
            </thead>
            <tbody>
              {routes.map((r) => (
                <tr key={r.route}>
                  <td className="url mono" title={r.route}>
                    {r.route}
                  </td>
                  <td className="small">{r.priority ?? '\u2014'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
      <div className="cov-section">
        <div className="hdr-title">Scripts with no function run by any test</div>
        {idx.untested.scripts.length === 0 ? (
          <div className="muted small">none</div>
        ) : (
          <ul className="mono small">
            {idx.untested.scripts.map((u) => (
              <li key={u}>{u}</li>
            ))}
          </ul>
        )}
      </div>
      <div className="cov-section">
        <div className="hdr-title">JavaScript — merged over {tests.length} test(s)</div>
        {scripts.length === 0 ? (
          <div className="muted small">none</div>
        ) : (
          <table className="grid-table cov-table">
            <thead>
              <tr>
                <th>File</th>
                <th>Coverage</th>
                <th>Used</th>
                <th>Total</th>
                <th>Functions run</th>
                <th>Tests</th>
              </tr>
            </thead>
            <tbody>
              {scripts.map((r) => (
                <tr key={r.url}>
                  <td className="url" title={r.url}>
                    {r.url}
                  </td>
                  <td className="cov-cell">
                    <Bar pct={r.pct} />
                  </td>
                  <td className="mono num">{fmtBytes(r.usedBytes)}</td>
                  <td className="mono num">{fmtBytes(r.totalBytes)}</td>
                  <td className="mono num">{r.functions}</td>
                  <td className="mono num">{r.tests}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
      <div className="cov-section">
        <div className="hdr-title">CSS rules — unattributed</div>
        {css.rules === 0 ? (
          <div className="muted small">none</div>
        ) : (
          <div className="row-actions">
            <Bar pct={cssPct} />
            <span className="small">
              {fmtBytes(css.usedBytes)} of {fmtBytes(css.totalBytes)} tracked rule bytes used, summed over {css.traces} trace(s)
            </span>
          </div>
        )}
        <div className="muted small cov-note">Rule usage carries no stylesheet URL yet, so CSS is one bucket, not per file.</div>
      </div>
      <div className="cov-section">
        <div className="hdr-title">Tests</div>
        <table className="grid-table">
          <thead>
            <tr>
              <th>Test</th>
              <th className="num">Traces</th>
              <th className="num">Routes</th>
              <th className="num">Scripts</th>
            </tr>
          </thead>
          <tbody>
            {tests.map(([id, t]) => (
              <tr key={id}>
                <td className="mono">{id}</td>
                <td className="mono num">{(t.traces ?? []).length}</td>
                <td className="mono num">{(t.routes ?? []).length}</td>
                <td className="mono num">{Object.keys(t.scripts ?? {}).length}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <Notes idx={idx} />
    </div>
  );
}
