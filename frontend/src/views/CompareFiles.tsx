import { useEffect, useMemo, useState } from 'react';
import { api } from '../api';

type Row = { t: 'same' | 'del' | 'add'; a?: string; b?: string; an?: number; bn?: number };

// LCS line diff → aligned rows (same / deleted-from-left / added-on-right).
function lineDiff(a: string[], b: string[]): Row[] {
  const n = a.length,
    m = b.length;
  const dp: number[][] = Array.from({ length: n + 1 }, () => new Array(m + 1).fill(0));
  for (let i = n - 1; i >= 0; i--)
    for (let j = m - 1; j >= 0; j--)
      dp[i][j] = a[i] === b[j] ? dp[i + 1][j + 1] + 1 : Math.max(dp[i + 1][j], dp[i][j + 1]);
  const out: Row[] = [];
  let i = 0,
    j = 0;
  while (i < n && j < m) {
    if (a[i] === b[j]) {
      out.push({ t: 'same', a: a[i], b: b[j], an: i + 1, bn: j + 1 });
      i++;
      j++;
    } else if (dp[i + 1][j] >= dp[i][j + 1]) {
      out.push({ t: 'del', a: a[i], an: i + 1 });
      i++;
    } else {
      out.push({ t: 'add', b: b[j], bn: j + 1 });
      j++;
    }
  }
  while (i < n) out.push({ t: 'del', a: a[i], an: ++i });
  while (j < m) out.push({ t: 'add', b: b[j], bn: ++j });
  return out;
}

export default function CompareFiles() {
  const [paths, setPaths] = useState<string[]>([]);
  const [f1, setF1] = useState('');
  const [f2, setF2] = useState('');
  const [rows, setRows] = useState<Row[] | null>(null);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  useEffect(() => {
    api
      .testcaseFiles()
      .then((fs) => setPaths(fs.map((f) => f.path).filter((p) => p.endsWith('.sel') || p.endsWith('.props'))))
      .catch((e) => setErr(e.message));
  }, []);

  const compare = () => {
    if (!f1 || !f2) return;
    setBusy(true);
    setErr(null);
    setRows(null);
    Promise.all([api.readSel(f1), api.readSel(f2)])
      .then(([a, b]) => setRows(lineDiff(a.split('\n'), b.split('\n'))))
      .catch((e) => setErr(e.message))
      .finally(() => setBusy(false));
  };

  const changed = useMemo(() => (rows ? rows.filter((r) => r.t !== 'same').length : 0), [rows]);

  return (
    <div className="view">
      <div className="view-head">
        <h2>Compare Files</h2>
      </div>
      <div className="cmp-bar">
        <select value={f1} onChange={(e) => setF1(e.target.value)}>
          <option value="">Left file…</option>
          {paths.map((p) => (
            <option key={p} value={p}>
              {p}
            </option>
          ))}
        </select>
        <span className="muted">vs</span>
        <select value={f2} onChange={(e) => setF2(e.target.value)}>
          <option value="">Right file…</option>
          {paths.map((p) => (
            <option key={p} value={p}>
              {p}
            </option>
          ))}
        </select>
        <button className="btn primary small" disabled={!f1 || !f2 || busy} onClick={compare}>
          {busy ? 'Comparing…' : 'Compare'}
        </button>
        {rows && <span className="muted small">{changed === 0 ? 'Identical' : changed + ' differing line(s)'}</span>}
      </div>
      {err && <div className="view-error">{err}</div>}
      {rows && (
        <div className="cmp-grid mono">
          {rows.map((r, i) => (
            <div className={'cmp-row cmp-' + r.t} key={i}>
              <span className="cmp-ln">{r.an ?? ''}</span>
              <span className="cmp-side cmp-l">{r.t === 'add' ? '\u00A0' : r.a || '\u00A0'}</span>
              <span className="cmp-ln">{r.bn ?? ''}</span>
              <span className="cmp-side cmp-r">{r.t === 'del' ? '\u00A0' : r.b || '\u00A0'}</span>
            </div>
          ))}
        </div>
      )}
      <div className="view-note muted small">Compares <code>.sel</code> / <code>.props</code> files line by line.</div>
    </div>
  );
}
