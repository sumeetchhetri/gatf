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
import type { A11yResult } from '../api';

const IMPACT_CLASS: Record<string, string> = { critical: 'st-fail', serious: 'st-fail', moderate: 'st-warn', minor: '' };

/** M10: one `a11y check` result: counts, then every violation with its selectors, HTML and fix summary. */
export default function A11yPanel({ result }: { result: A11yResult }) {
  const counts = Object.entries(result.counts || {}).filter(([, n]) => n > 0);
  return (
    <div className="a11y">
      <div className="small">
        {result.url && (
          <span className="mono ellipsis" title={result.url}>
            {result.url}
          </span>
        )}{' '}
        <span className={'st-badge ' + (result.failed ? 'st-fail' : 'st-pass')}>
          {result.violations.length === 0 ? 'no violations' : result.violations.length + ' violation(s)'}
        </span>{' '}
        {counts.map(([k, n]) => (
          <span key={k} className={'st-badge ' + (IMPACT_CLASS[k] ?? '')} style={{ marginRight: 4 }}>
            {n} {k}
          </span>
        ))}
        <span className="muted small">
          {' '}
          impact ≥ {result.minImpact} · {result.passes} rule(s) passed · {result.incomplete} need review
        </span>
      </div>
      {result.violations.length > 0 && (
        <table className="tbl" aria-label="Accessibility violations">
          <tbody>
            {result.violations.map((v) => (
              <tr key={v.id}>
                <td style={{ width: 80, verticalAlign: 'top' }}>
                  <span className={'st-badge ' + (IMPACT_CLASS[v.impact] ?? '')}>{v.impact}</span>
                </td>
                <td className="small">
                  <div>
                    {v.helpUrl ? (
                      <a href={v.helpUrl} target="_blank" rel="noreferrer" className="mono">
                        {v.id}
                      </a>
                    ) : (
                      <span className="mono">{v.id}</span>
                    )}{' '}
                    {v.help}
                  </div>
                  <ul className="a11y-nodes">
                    {v.nodes.map((n, i) => (
                      <li key={i}>
                        <code className="a11y-sel">{n.target}</code>
                        {n.html && <div className="mono muted small ellipsis" title={n.html}>{n.html}</div>}
                        {n.summary && (
                          <details>
                            <summary className="muted small">How to fix</summary>
                            <pre className="small">{n.summary}</pre>
                          </details>
                        )}
                      </li>
                    ))}
                  </ul>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
