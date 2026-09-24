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
import type { SecurityFinding, TraceChecks } from '../api';

const SEV_ORDER: Record<string, number> = { high: 0, medium: 1, low: 2 };

/** Findings grouped by page, pages in first-seen order, each page's findings most severe first. */
export function groupFindings(findings: SecurityFinding[]): [string, SecurityFinding[]][] {
  const by = new Map<string, SecurityFinding[]>();
  for (const f of findings) {
    const list = by.get(f.page) ?? [];
    list.push(f);
    by.set(f.page, list);
  }
  return [...by.entries()].map(([page, list]) => [
    page,
    [...list].sort((a, b) => (SEV_ORDER[a.severity] ?? 3) - (SEV_ORDER[b.severity] ?? 3)),
  ]);
}

const sevClass = (s: string) => (s === 'high' ? 'st-fail' : s === 'medium' ? 'st-warn' : '');
const outcomeClass = (o: string) => (o === 'pass' ? 'st-pass' : o === 'breach' ? 'st-fail' : 'st-warn');

// Q9/Q10: budgets table + security findings per page, shown in the UI test report.
export default function TraceChecksPanel({ checks }: { checks: TraceChecks }) {
  const groups = checks.security ? groupFindings(checks.security) : [];
  return (
    <div className="rv-checks">
      {checks.budgets.length > 0 && (
        <>
          <h3 className="rv-code-title">Performance budgets</h3>
          <table className="tbl">
            <thead>
              <tr>
                <th>Metric</th>
                <th>Budget</th>
                <th>Actual</th>
                <th>Result</th>
              </tr>
            </thead>
            <tbody>
              {checks.budgets.map((b) => (
                <tr key={b.metric}>
                  <td className="mono">{b.metric}</td>
                  <td className="mono">{b.budget}</td>
                  <td className="mono num">{b.display ?? '—'}</td>
                  <td>
                    <span className={'st-badge ' + outcomeClass(b.outcome)}>{b.outcome.replace('_', ' ')}</span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      )}
      {checks.security && (
        <>
          <h3 className="rv-code-title">Security findings ({checks.security.length})</h3>
          {groups.length === 0 && <div className="muted small">No findings on the pages this script visited.</div>}
          {groups.map(([page, list]) => (
            <div key={page} className="rv-sec-page">
              <div className="mono small ellipsis" title={page}>
                {page}
              </div>
              <table className="tbl">
                <tbody>
                  {list.map((f, i) => (
                    <tr key={i}>
                      <td style={{ width: 80 }}>
                        <span className={'st-badge ' + sevClass(f.severity)}>{f.severity}</span>
                      </td>
                      <td className="mono small" style={{ width: 220 }}>
                        {f.rule}
                      </td>
                      <td className="small">
                        {f.message}
                        {f.url && f.url !== page && <div className="mono muted small ellipsis">{f.url}</div>}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ))}
        </>
      )}
    </div>
  );
}
