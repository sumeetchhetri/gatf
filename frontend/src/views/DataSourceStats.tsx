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

// GET /profile?dsnames=all → { dataSourceName: [ [header cols…], [row…], … ] }. One table per source.
export default function DataSourceStats() {
  const [data, setData] = useState<Record<string, string[][]> | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api
      .dsStats()
      .then(setData)
      .catch((e) => setErr(e.message))
      .finally(() => setLoading(false));
  }, []);

  const names = data ? Object.keys(data) : [];

  return (
    <div className="view">
      <div className="view-head">
        <h2>Data Source Statistics</h2>
      </div>
      {loading && <div className="view-note">Loading…</div>}
      {err && <div className="view-error">Could not load statistics ({err}).</div>}
      {data && names.length === 0 && !loading && (
        <div className="view-note muted">No data sources are configured.</div>
      )}
      {names.map((name) => {
        const rows = data![name] || [];
        const headers = rows[0] || [];
        const body = rows.slice(1);
        return (
          <section className="view-section" key={name}>
            <h3 className="small">{name}</h3>
            <div className="tbl-scroll">
              <table className="grid-table">
                <thead>
                  <tr>
                    {headers.map((h, i) => (
                      <th key={i}>{h}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {body.map((r, ri) => (
                    <tr key={ri}>
                      {r.map((c, ci) => (
                        <td key={ci}>{c}</td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            {body.length === 0 && <div className="muted small">No rows.</div>}
          </section>
        );
      })}
    </div>
  );
}
