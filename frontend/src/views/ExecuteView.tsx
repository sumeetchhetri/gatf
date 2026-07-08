import { useEffect, useMemo, useRef, useState } from 'react';
import { api } from '../api';
import type { TestcaseFileEntry, ExecStatus } from '../api';

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

export default function ExecuteView({ onOpenReports }: { onOpenReports: () => void }) {
  const [files, setFiles] = useState<TestcaseFileEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [mode, setMode] = useState<Mode>('sel');
  const [selected, setSelected] = useState<Set<string>>(new Set());

  const [runState, setRunState] = useState<RunState>('idle');
  const [statusMsg, setStatusMsg] = useState('');
  const [fileStatus, setFileStatus] = useState<Record<string, FileStatus>>({});
  const [errored, setErrored] = useState<RunError[]>([]);

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
    if (sel.length === 0) return;
    setErrored([]);
    setFileStatus({});
    markFiles(sel, 'running');
    setRunState('starting');
    setStatusMsg('Starting…');
    api
      .execStart(pluginOf(mode), sel)
      .then(({ data }) => {
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
            <button className="btn primary" disabled={busy || selCount === 0} onClick={run}>
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
                  {visible.map((f) => {
                    const st = fileStatus[f.path];
                    return (
                      <tr key={f.path}>
                        <td className="exec-cb">
                          <input
                            type="checkbox"
                            checked={selected.has(f.path)}
                            onChange={() => toggle(f.path)}
                            disabled={busy}
                          />
                        </td>
                        <td className="mono ellipsis" style={{ maxWidth: 420 }} title={f.path}>
                          {f.path}
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
            Runs go through the same engine as the classic UI; when a run finishes, its full report (stats,
            per-test results, traces) is in the <b>Reports</b> tab. A paused Selenium run is resumed from the
            debugger (coming in D2).
          </div>
        </>
      )}
    </div>
  );
}
