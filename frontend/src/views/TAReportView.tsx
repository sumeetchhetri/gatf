import type { TAReportData } from '../api';

function PctTable({ label, rows }: { label: string; rows: [string, number[]][] }) {
  return (
    <section className="view-section">
      <h3>{label}</h3>
      {rows.length === 0 ? (
        <div className="view-note">No data.</div>
      ) : (
        <table className="grid-table">
          <thead>
            <tr>
              <th>Name</th>
              <th className="num">90th (ms)</th>
              <th className="num">50th (ms)</th>
            </tr>
          </thead>
          <tbody>
            {rows.map(([name, vals]) => (
              <tr key={name} className={name === 'All' ? 'ta-all' : ''}>
                <td className="mono">{name}</td>
                <td className="num mono">{vals[0] ?? ''}</td>
                <td className="num mono">{vals[1] ?? ''}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}

export default function TAReportView({ data }: { data: TAReportData }) {
  const tests = Object.entries(data.testcaseTAReports || {});
  const runs = Object.entries(data.runTAReports || {});
  return (
    <div className="ar">
      <div className="view-note muted small">
        Response-time percentiles (90th and 50th) per test case and per run.
      </div>
      <PctTable label="Per test case" rows={tests} />
      <PctTable label="Per run" rows={runs} />
    </div>
  );
}
