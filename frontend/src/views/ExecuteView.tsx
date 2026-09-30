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
import { useEffect, useMemo, useRef, useState } from 'react';
import { api } from '../api';
import type { TestcaseFileEntry, ExecStatus, LoadStatEntry, LiveGStats } from '../api';
import { fmtMs } from '../util';
import { buildExecRows, visibleRows, dirState, execFilterError, execFilterQuery, describeExecFilter } from './execTree';
import type { ExecFilter } from './execTree';

type Mode = 'api' | 'sel';
type RunState = 'idle' | 'starting' | 'running' | 'paused' | 'done' | 'failed';
type FileStatus = 'pass' | 'fail' | 'running';
interface RunError {
  file: string;
  line: number;
  msg: string;
}

const kindOf = (p: string) => (p.toLowerCase().endsWith('.sel') ? 'sel' : p.toLowerCase().endsWith('.xml') ? 'xml' : 'other');
const pluginOf = (m: Mode) => (m === 'api' ? 'executor-api' : 'executor-sel');
const baseName = (p: string) => {
  const i = p.lastIndexOf('/');
  return i === -1 ? p : p.slice(i + 1);
};

// ---- live load/distributed reporting ----
// One rolling row per completed load-run sample (newest first, capped). Mirrors the classic
// #lol_tblcu table but driven off the same lstats records the classic UI graphed.
interface LiveRow {
  key: string;
  label: string; // prefix#run
  node: string;
  reportUrl: string | null;
  total: number;
  failed: number;
  skipped: number;
  execMs: number;
}
interface LiveState {
  samples: { t: number; ms: number }[]; // exec-time over wall-clock (for the chart)
  rows: LiveRow[]; // recent runs, newest first
  suite: Record<string, { pass: number; fail: number }>; // node|run → tallies (from sstats)
  gstats: LiveGStats | null;
  seq: number; // monotonically increasing run counter for row labels
}
const emptyLive = (): LiveState => ({ samples: [], rows: [], suite: {}, gstats: null, seq: 0 });

function accumulate(prev: LiveState, data: ExecStatus): LiveState {
  const next: LiveState = {
    samples: prev.samples.slice(),
    rows: prev.rows.slice(),
    suite: { ...prev.suite },
    gstats: prev.gstats,
    seq: prev.seq,
  };
  const lstats = (data.lstats || []) as LoadStatEntry[];
  // server sends newest-last; the classic renders reversed → iterate as given, newest ends on top
  for (const e of lstats) {
    const stats = Array.isArray(e[5]) ? e[5] : [0, 0, 0, 0];
    const execMs = Number(stats[3]) || 0;
    const t = Number(e[4]) || Date.now();
    next.seq += 1;
    next.samples.push({ t, ms: execMs });
    next.rows.unshift({
      key: 'r' + next.seq,
      label: (e[0] || 'Run') + '#' + next.seq,
      node: String(e[3] ?? e[7] ?? 'local'),
      reportUrl: e[2] ?? null,
      total: Number(stats[0]) || 0,
      failed: Number(stats[1]) || 0,
      skipped: Number(stats[2]) || 0,
      execMs,
    });
  }
  if (next.samples.length > 600) next.samples = next.samples.slice(-600);
  if (next.rows.length > 100) next.rows = next.rows.slice(0, 100);
  for (const s of data.sstats || []) {
    const p = String(s).split('|'); // node|run|passFlag
    const k = (p[0] ?? '') + '|' + (p[1] ?? '');
    const cell = next.suite[k] || { pass: 0, fail: 0 };
    if (p[2] === '1' || p[2] === '1.0') cell.pass += 1;
    else cell.fail += 1;
    next.suite[k] = cell;
  }
  if (data.gstats) next.gstats = data.gstats;
  return next;
}

function pctl(sorted: number[], p: number): number {
  if (sorted.length === 0) return 0;
  const idx = Math.min(sorted.length - 1, Math.max(0, Math.ceil((p / 100) * sorted.length) - 1));
  return sorted[idx];
}

// dependency-free exec-time line chart (same SVG-only ethos as the trace viewer)
function LoadChart({ samples }: { samples: { t: number; ms: number }[] }) {
  const W = 720,
    H = 200,
    padL = 48,
    padB = 22,
    padT = 10,
    padR = 10;
  if (samples.length < 2) return <div className="view-note muted small">Collecting samples…</div>;
  const ys = samples.map((s) => s.ms);
  const maxY = Math.max(...ys, 1);
  const n = samples.length;
  const x = (i: number) => padL + (i / (n - 1)) * (W - padL - padR);
  const y = (v: number) => padT + (1 - v / maxY) * (H - padT - padB);
  const d = samples.map((s, i) => (i === 0 ? 'M' : 'L') + x(i).toFixed(1) + ' ' + y(s.ms).toFixed(1)).join(' ');
  const ticks = [0, 0.25, 0.5, 0.75, 1].map((f) => Math.round(maxY * f));
  return (
    <svg className="load-chart" viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="none" role="img" aria-label="Execution time">
      {ticks.map((tv, i) => {
        const yy = y(tv);
        return (
          <g key={i}>
            <line x1={padL} y1={yy} x2={W - padR} y2={yy} className="lc-grid" />
            <text x={padL - 6} y={yy + 3} className="lc-axis" textAnchor="end">
              {tv}
            </text>
          </g>
        );
      })}
      <path d={d} className="lc-line" fill="none" />
      <text x={(W + padL) / 2} y={H - 4} className="lc-axis" textAnchor="middle">
        samples (oldest → newest) · y = exec ms
      </text>
    </svg>
  );
}

function LiveLoadPanel({ live }: { live: LiveState }) {
  const sorted = useMemo(() => live.samples.map((s) => s.ms).sort((a, b) => a - b), [live.samples]);
  const g = live.gstats;
  const rate = g && g.totalTestCount ? Math.round(((g.totalTestCount - (g.failedTestCount || 0)) / g.totalTestCount) * 100) : null;
  const cards: [string, string][] = [
    ['Suite runs', String(g?.totalSuiteRuns ?? live.seq)],
    ['Tests', String(g?.totalTestCount ?? '—')],
    ['Failed', String(g?.failedTestCount ?? 0)],
    ['Skipped', String(g?.skippedTestCount ?? 0)],
    ['Pass rate', rate != null ? rate + '%' : '—'],
    ['p50', fmtMs(pctl(sorted, 50))],
    ['p90', fmtMs(pctl(sorted, 90))],
    ['p99', fmtMs(pctl(sorted, 99))],
  ];
  const nodes = Object.keys(live.suite).length;
  return (
    <section className="view-section load-live">
      <h3>
        Live results{' '}
        <span className="muted small">
          {live.seq} sample{live.seq === 1 ? '' : 's'}
          {nodes > 0 ? ` · ${nodes} suite run(s)` : ''}
        </span>
      </h3>
      <div className="vitals">
        {cards.map(([label, val]) => (
          <div className="vital-card" key={label}>
            <div className="vital-val">{val}</div>
            <div className="vital-label">{label}</div>
          </div>
        ))}
      </div>
      <LoadChart samples={live.samples} />
      {live.rows.length > 0 && (
        <div className="tbl-scroll" style={{ maxHeight: 260, marginTop: 10 }}>
          <table className="grid-table">
            <thead>
              <tr>
                <th>Run</th>
                <th>Node</th>
                <th className="num">Total</th>
                <th className="num">Failed</th>
                <th className="num">Skipped</th>
                <th className="num">Exec</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {live.rows.map((r) => (
                <tr key={r.key} className={r.failed > 0 ? 'st-fail' : ''}>
                  <td className="mono">{r.label}</td>
                  <td className="mono">{r.node}</td>
                  <td className="num mono">{r.total}</td>
                  <td className="num mono">{r.failed}</td>
                  <td className="num mono">{r.skipped}</td>
                  <td className="num mono">{fmtMs(r.execMs)}</td>
                  <td>
                    {r.reportUrl && (
                      <a className="btn ghost small" href={'/reports/' + r.reportUrl} target="_blank" rel="noopener">
                        {'\u2197'}
                      </a>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

export default function ExecuteView({
  onOpenReports,
  autoRun,
  onConsumeAutoRun,
}: {
  onOpenReports: () => void;
  /** M17: a test file to select and start at once (the Trace Viewer's "Load test this flow") */
  autoRun?: string;
  onConsumeAutoRun?: () => void;
}) {
  const [files, setFiles] = useState<TestcaseFileEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [mode, setMode] = useState<Mode>('sel');
  const [selected, setSelected] = useState<Set<string>>(new Set());

  const [runState, setRunState] = useState<RunState>('idle');
  const [statusMsg, setStatusMsg] = useState('');
  const [fileStatus, setFileStatus] = useState<Record<string, FileStatus>>({});
  const [errored, setErrored] = useState<RunError[]>([]);
  const [liveEnabled, setLiveEnabled] = useState(false);
  const [live, setLive] = useState<LiveState>(emptyLive);
  // grep / tag selection inside the chosen files (the CLI's --grep / --grep-invert / --tag)
  const [filter, setFilter] = useState<ExecFilter>({ grep: '', grepInvert: '', tags: '' });
  const filterError = execFilterError(filter);
  const filterDesc = describeExecFilter(filter);

  const pollRef = useRef<number | null>(null);
  const selectedRef = useRef(selected);
  selectedRef.current = selected;

  useEffect(() => {
    api
      .testcaseFiles()
      .then(setFiles)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
    return () => stopPolling();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // M17: auto-run — select once the file list is in, then start on the next render (run() reads mode/selection)
  const [autoPending, setAutoPending] = useState<string | null>(null);
  useEffect(() => {
    if (!autoRun || loading) return;
    onConsumeAutoRun?.();
    const hit = files.find((f) => f.path === autoRun) ?? files.find((f) => f.path.endsWith('/' + autoRun));
    if (!hit) {
      setRunState('failed');
      setStatusMsg('Load test ' + autoRun + ' was saved but is not in the test files list — refresh and run it from here.');
      return;
    }
    setMode(kindOf(hit.path) === 'xml' ? 'api' : 'sel');
    setSelected(new Set([hit.path]));
    setAutoPending(hit.path);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [autoRun, loading, files]);
  useEffect(() => {
    if (!autoPending || !selected.has(autoPending) || mode !== (kindOf(autoPending) === 'xml' ? 'api' : 'sel')) return;
    setAutoPending(null);
    run();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [autoPending, selected, mode]);

  const visible = useMemo(
    () => files.filter((f) => kindOf(f.path) === (mode === 'api' ? 'xml' : 'sel')).sort((a, b) => a.path.localeCompare(b.path)),
    [files, mode],
  );

  const stopPolling = () => {
    if (pollRef.current != null) {
      clearInterval(pollRef.current);
      pollRef.current = null;
    }
  };

  const markFiles = (paths: string[], s: FileStatus) =>
    setFileStatus((prev) => {
      const next = { ...prev };
      for (const p of paths) next[p] = s;
      return next;
    });

  // interpret a status payload (from start or a poll) and drive the state machine
  const interpret = (data: ExecStatus) => {
    // live load/distributed samples ride on the in-progress poll — fold them in regardless of branch
    if ((data.lstats && data.lstats.length) || (data.sstats && data.sstats.length) || data.gstats) {
      setLive((prev) => accumulate(prev, data));
    }
    const err = data.error;
    if (Array.isArray(err)) {
      const rows: RunError[] = [err, ...(data.others || [])].map((e) => ({
        file: String(e[2] ?? ''),
        line: Number(e[1]),
        msg: String(e[3] ?? e[0] ?? 'Error'),
      }));
      setErrored(rows);
      markFiles(
        rows.map((r) => r.file).filter(Boolean),
        'fail',
      );
      setRunState('failed');
      setStatusMsg('Execution failed — see the errors below.');
      stopPolling();
      return;
    }
    const s = String(err ?? '');
    if (/completed/i.test(s)) {
      const bad = new Set(errored.map((r) => r.file));
      markFiles(
        [...selectedRef.current].filter((p) => !bad.has(p)),
        'pass',
      );
      setRunState('done');
      setStatusMsg('Execution completed — open the Reports tab for the full results.');
      stopPolling();
    } else if (/failed with Error/i.test(s) || /Unknown Error/i.test(s)) {
      setRunState('failed');
      setStatusMsg(s);
      stopPolling();
    } else if (/Please Start/i.test(s)) {
      setRunState('idle');
      stopPolling();
    } else {
      // "in progress", "Execution Started", live-status payloads, or anything unrecognized → keep polling
      setRunState(data.is_paused ? 'paused' : 'running');
      setStatusMsg(data.is_paused ? 'Paused (waiting on an element). Resume from the debugger.' : 'Running…');
    }
  };

  const startPolling = () => {
    stopPolling();
    pollRef.current = window.setInterval(() => {
      api
        .execPoll(pluginOf(mode))
        .then(({ data }) => interpret(data))
        .catch(() => {
          /* transient — keep polling */
        });
    }, 2000);
  };

  const run = () => {
    const sel = [...selected].filter((p) => kindOf(p) === (mode === 'api' ? 'xml' : 'sel'));
    if (sel.length === 0 || filterError) return;
    setErrored([]);
    setFileStatus({});
    setLive(emptyLive());
    setLiveEnabled(false);
    markFiles(sel, 'running');
    setRunState('starting');
    setStatusMsg('Starting…');
    api
      .execStart(pluginOf(mode), sel, execFilterQuery(filter))
      .then(({ data }) => {
        if (data.loadTestingEnabled) setLiveEnabled(true);
        interpret(data);
        const errStr = Array.isArray(data.error) ? '' : String(data.error ?? '');
        const terminal = Array.isArray(data.error) || /completed/i.test(errStr) || /failed with Error|Unknown Error/i.test(errStr);
        if (!terminal) startPolling();
      })
      .catch((e) => {
        setRunState('failed');
        setStatusMsg(e.message);
      });
  };

  const stop = () => {
    api.execStop(pluginOf(mode)).finally(() => {
      stopPolling();
      setRunState('idle');
      setStatusMsg('Stopped.');
      setFileStatus((prev) => {
        const next: Record<string, FileStatus> = {};
        for (const [k, v] of Object.entries(prev)) if (v !== 'running') next[k] = v;
        return next;
      });
    });
  };

  const busy = runState === 'starting' || runState === 'running' || runState === 'paused';
  const toggle = (p: string) =>
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(p)) next.delete(p);
      else next.add(p);
      return next;
    });
  const allVisibleSelected = visible.length > 0 && visible.every((f) => selected.has(f.path));
  const toggleAll = () =>
    setSelected((prev) => {
      const next = new Set(prev);
      if (allVisibleSelected) visible.forEach((f) => next.delete(f.path));
      else visible.forEach((f) => next.add(f.path));
      return next;
    });

  const selCount = visible.filter((f) => selected.has(f.path)).length;

  // G6: folder rows — a folder checkbox (tri-state) selects/deselects every descendant file
  const [collapsed, setCollapsed] = useState<Set<string>>(new Set());
  const rows = useMemo(() => buildExecRows(visible.map((f) => f.path)), [visible]);
  const shownRows = useMemo(() => visibleRows(rows, collapsed), [rows, collapsed]);
  const toggleDir = (files: string[]) =>
    setSelected((prev) => {
      const next = new Set(prev);
      if (dirState(files, prev) === 'all') files.forEach((f) => next.delete(f));
      else files.forEach((f) => next.add(f));
      return next;
    });
  const toggleCollapse = (dir: string) =>
    setCollapsed((prev) => {
      const next = new Set(prev);
      if (next.has(dir)) next.delete(dir);
      else next.add(dir);
      return next;
    });
  const dirStatus = (files: string[]): FileStatus | undefined => {
    const st = files.map((f) => fileStatus[f]).filter(Boolean);
    if (st.length === 0) return undefined;
    return st.includes('fail') ? 'fail' : st.includes('running') ? 'running' : st.length === files.length ? 'pass' : undefined;
  };

  return (
    <div className="view">
      <div className="view-head">
        <h2>Execute</h2>
        <div className="view-head-right">
          <div className="seg">
            <button className={'seg-btn' + (mode === 'sel' ? ' on' : '')} disabled={busy} onClick={() => setMode('sel')}>
              Selenium
            </button>
            <button className={'seg-btn' + (mode === 'api' ? ' on' : '')} disabled={busy} onClick={() => setMode('api')}>
              API
            </button>
          </div>
        </div>
      </div>

      {loading && <div className="view-note">Loading…</div>}
      {error && <div className="view-error">{error}</div>}

      {!loading && !error && (
        <>
          <div className="cfg-bar">
            <button className="btn primary" disabled={busy || selCount === 0 || !!filterError} onClick={run}>
              ▶ Run {selCount > 0 ? `(${selCount})` : ''}
            </button>
            <button className="btn" disabled={!busy} onClick={stop}>
              ■ Stop
            </button>
            {runState === 'done' && (
              <button className="btn ghost" onClick={onOpenReports}>
                Open Reports →
              </button>
            )}
            <span className={'exec-status exec-' + runState}>
              {busy && <span className="spinner" />}
              {statusMsg || 'Idle'}
            </span>
          </div>

          <div className="cfg-bar exec-filter">
            <input
              className="inp"
              placeholder="grep (regex on title)"
              aria-label="grep"
              value={filter.grep}
              disabled={busy}
              onChange={(e) => setFilter({ ...filter, grep: e.target.value })}
            />
            <input
              className="inp"
              placeholder="tags, e.g. @smoke"
              aria-label="tags"
              value={filter.tags}
              disabled={busy}
              onChange={(e) => setFilter({ ...filter, tags: e.target.value })}
            />
            <input
              className="inp"
              placeholder="grep-invert (exclude)"
              aria-label="grep-invert"
              value={filter.grepInvert}
              disabled={busy}
              onChange={(e) => setFilter({ ...filter, grepInvert: e.target.value })}
            />
            {filterError ? (
              <span className="view-error small">{filterError}</span>
            ) : filterDesc ? (
              <span className="muted small mono" title={mode === 'sel' ? 'UI: script path + the @tags in its leading // comments' : 'API: file, test name and description'}>
                {filterDesc}
              </span>
            ) : null}
          </div>

          {errored.length > 0 && (
            <div className="tf-problems">
              <div className="tf-problems-h small">{errored.length} error(s)</div>
              {errored.map((e, i) => (
                <div className="tf-problem" key={i}>
                  <span className="tf-problem-ln mono">
                    {baseName(e.file)}
                    {Number.isFinite(e.line) && e.line > 0 ? `:${e.line}` : ''}
                  </span>
                  <span className="tf-problem-msg">{e.msg}</span>
                </div>
              ))}
            </div>
          )}

          {(liveEnabled || live.seq > 0) && <LiveLoadPanel live={live} />}

          <section className="view-section">
            {visible.length === 0 ? (
              <div className="view-note muted">No {mode === 'api' ? '.xml' : '.sel'} test files.</div>
            ) : (
              <table className="grid-table">
                <thead>
                  <tr>
                    <th className="exec-cb">
                      <input type="checkbox" checked={allVisibleSelected} onChange={toggleAll} disabled={busy} />
                    </th>
                    <th>File</th>
                    <th className="exec-st">Status</th>
                  </tr>
                </thead>
                <tbody>
                  {shownRows.map((r) => {
                    if (r.kind === 'dir') {
                      const ds = dirState(r.files, selected);
                      const dst = dirStatus(r.files);
                      const open = !collapsed.has(r.path);
                      return (
                        <tr key={'d:' + r.path} className="exec-dir">
                          <td className="exec-cb">
                            <input
                              type="checkbox"
                              checked={ds === 'all'}
                              ref={(el) => {
                                if (el) el.indeterminate = ds === 'some';
                              }}
                              onChange={() => toggleDir(r.files)}
                              disabled={busy}
                              title={`Select all ${r.files.length} file(s) in ${r.path}`}
                            />
                          </td>
                          <td className="mono ellipsis" style={{ maxWidth: 420, paddingLeft: 8 + r.depth * 16 }} title={r.path}>
                            <button className="exec-twisty" onClick={() => toggleCollapse(r.path)} aria-expanded={open}>
                              {open ? '\u25BE' : '\u25B8'}
                            </button>
                            {r.name}/ <span className="muted small">({r.files.length})</span>
                          </td>
                          <td className="exec-st">{dst && <span className={'rag rag-' + dst}>{dst}</span>}</td>
                        </tr>
                      );
                    }
                    const st = fileStatus[r.path];
                    return (
                      <tr key={r.path}>
                        <td className="exec-cb">
                          <input
                            type="checkbox"
                            checked={selected.has(r.path)}
                            onChange={() => toggle(r.path)}
                            disabled={busy}
                          />
                        </td>
                        <td className="mono ellipsis" style={{ maxWidth: 420, paddingLeft: 8 + r.depth * 16 }} title={r.path}>
                          {r.name}
                        </td>
                        <td className="exec-st">
                          {st && <span className={'rag rag-' + st}>{st === 'running' ? 'running' : st}</span>}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            )}
          </section>

          <div className="view-note muted small">
            Runs go through the same engine as the classic UI. Load / distributed runs stream live samples
            above while executing; when a run finishes, its full report (stats, per-test results, traces) is
            in the <b>Reports</b> tab. Pause / resume a Selenium run from the editor's debugger.
          </div>
        </>
      )}
    </div>
  );
}
