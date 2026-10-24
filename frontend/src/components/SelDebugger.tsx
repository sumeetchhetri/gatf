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
import { useEffect, useRef, useState } from 'react';
import { api } from '../api';
import { debugLinesFor, DBG } from '../api';
import type { DbgResponse } from '../api';
import DebugCodeView from './DebugCodeView';
import LivePane from './LivePane';

type Status = 'initializing' | 'paused' | 'running' | 'done' | 'terminated' | 'error';
// The sessionId is the only capability guarding the push channels (WS/SSE) — use a CSPRNG id.
const uid = () =>
  typeof crypto !== 'undefined' && 'randomUUID' in crypto
    ? crypto.randomUUID()
    : Date.now().toString(36) + Math.random().toString(36).slice(2) + Math.random().toString(36).slice(2);

export default function SelDebugger({
  filePath,
  onExit,
  onOpenTrace,
}: {
  filePath: string;
  onExit: () => void;
  onOpenTrace?: (url: string) => void;
}) {
  const tc = ''; // whole-file .sel debug uses an empty test-case name
  const sessionRef = useRef<string>(uid());
  const [status, setStatus] = useState<Status>('initializing');
  const [msg, setMsg] = useState<string>('Starting debugger…');
  const [traceUrl, setTraceUrl] = useState<string | null>(null); // Δ163o (bug 9): the run's kept trace.zip
  const [code, setCode] = useState('');
  const [currentFile, setCurrentFile] = useState(filePath);
  const [currentLine, setCurrentLine] = useState<number | null>(null);
  const [breakpoints, setBreakpoints] = useState<Set<number>>(new Set());
  const [debuggable, setDebuggable] = useState<Set<number> | null>(null);
  const [busy, setBusy] = useState(false);

  const lFullRef = useRef<unknown>(null);
  const codeRef = useRef('');
  const currentFileRef = useRef(filePath);
  const currentLineRef = useRef<number | null>(null);
  const esRef = useRef<EventSource | null>(null);
  const wsRef = useRef<WebSocket | null>(null);
  const closedRef = useRef(false); // intentional teardown — suppresses WS→SSE fallback
  const doneRef = useRef(false);
  codeRef.current = code;
  currentFileRef.current = currentFile;
  currentLineRef.current = currentLine;

  const stopStream = () => {
    closedRef.current = true;
    if (wsRef.current != null) {
      try {
        wsRef.current.close();
      } catch {
        /* ignore */
      }
      wsRef.current = null;
    }
    if (esRef.current != null) {
      esRef.current.close();
      esRef.current = null;
    }
  };

  // init on mount; terminate on unmount (only one server session per instance)
  useEffect(() => {
    const sid = sessionRef.current;
    api
      .dbg(DBG.INIT, filePath, tc, sid)
      .then((d) => {
        if (d.s) {
          setCode(String(d.c ?? ''));
          setCurrentFile(filePath);
          lFullRef.current = d.l;
          setDebuggable(debugLinesFor(d.l, filePath));
          if (d.o === false) {
            // Δ163o (sweep): the debuggee's JVM + browser are still starting; it has not paused anywhere yet, so don't
            // claim a pause (steps sent now used to wait server-side, the UI looked stuck on "Paused")
            setMsg('Starting the browser… (breakpoints can be set now)');
          } else {
            setCurrentLine(typeof d.i === 'number' ? d.i : null);
            setStatus('paused');
            setMsg('Paused.');
          }
          startStream();
        } else {
          setStatus('error');
          setMsg(d.m || 'Could not start the debugger.');
        }
      })
      .catch((e) => {
        setStatus('error');
        setMsg(e.message);
      });
    return () => {
      stopStream();
      if (!doneRef.current) api.dbg(DBG.TERMINATE, filePath, tc, sessionRef.current).catch(() => {});
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // One place to apply a pushed {s,r,p,n,v,t} frame — used by both the WebSocket and the SSE fallback.
  const handleFrame = (raw: string) => {
    let d: DbgResponse;
    try {
      d = JSON.parse(raw) as DbgResponse;
    } catch {
      return;
    }
    if (d.s === false) {
      doneRef.current = true;
      setStatus('terminated');
      setMsg(d.m || 'Debug session ended.');
      stopStream();
      return;
    }
    if (d.r === false) {
      doneRef.current = true;
      if (typeof d.z === 'string' && d.z) setTraceUrl(d.z);
      setStatus('done');
      setMsg('Debug session completed — see the Reports tab.');
      setCurrentLine(null);
      stopStream();
      return;
    }
    if (d.o === false) return; // Δ163o: not really paused yet (still initializing)
    const line = typeof d.p === 'number' ? d.p : currentLineRef.current;
    // stepped into another file → load its source
    if (d.t && d.t !== currentFileRef.current) {
      const nf = d.t;
      api
        .readSel(nf)
        .then((src) => {
          setCode(src);
          setCurrentFile(nf);
          setDebuggable(debugLinesFor(lFullRef.current, nf));
          setCurrentLine(typeof line === 'number' ? line : null);
        })
        .catch(() => setCurrentLine(typeof line === 'number' ? line : null));
    } else {
      setCurrentLine(typeof line === 'number' ? line : null);
    }
    setStatus(typeof line === 'number' && line > 0 ? 'paused' : 'running');
    if (d.o === true) setMsg((m) => (m.startsWith('Starting the browser') ? 'Paused.' : m));
  };

  // SSE fallback (delta-040): server pushes on change; EventSource auto-reconnects on a transient drop.
  const startSse = () => {
    if (closedRef.current) return;
    const es = new EventSource(api.debugStreamUrl(sessionRef.current));
    esRef.current = es;
    es.onmessage = (ev: MessageEvent) => handleFrame(ev.data as string);
    es.onerror = () => {
      /* transient — EventSource reconnects; a finished session was already closed in handleFrame */
    };
  };

  // Prefer the WebSocket (delta-041); if it can't connect (or drops mid-session), fall back to SSE.
  const startStream = () => {
    stopStream();
    closedRef.current = false;
    let ws: WebSocket;
    try {
      ws = new WebSocket(api.debugWsUrl());
    } catch {
      startSse();
      return;
    }
    wsRef.current = ws;
    ws.onopen = () => {
      try {
        ws.send(sessionRef.current); // first message = the debug sessionId
      } catch {
        /* ignore */
      }
    };
    ws.onmessage = (ev: MessageEvent) => handleFrame(ev.data as string);
    ws.onerror = () => {
      /* handled by onclose */
    };
    ws.onclose = () => {
      wsRef.current = null;
      // WS unavailable or dropped before the session finished → switch to the SSE fallback
      if (!closedRef.current && !doneRef.current) startSse();
    };
  };

  const cmd = (lineCode: string, sline?: string) => {
    setBusy(true);
    api
      .dbg(lineCode, filePath, tc, sessionRef.current, sline)
      .then((d) => {
        if (d.s === false) setMsg(d.m || 'Command rejected.');
        // position updates arrive via the heartbeat
      })
      .catch((e) => setMsg(e.message))
      .finally(() => setBusy(false));
  };

  const stepInto = () => {
    const ln = currentLineRef.current;
    const sline = ln ? (codeRef.current.split('\n')[ln - 1] ?? '') : '';
    cmd(DBG.STEP_INTO, sline);
  };
  const stepOut = () => cmd(DBG.STEP_OUT);
  const stepOver = () => cmd(DBG.STEP_OVER);
  const cont = () => cmd(DBG.CONTINUE);
  const suspend = () => cmd(DBG.SUSPEND);
  // Δ163n (bug 5): re-run the subtest the debuggee is paused in (also from inside a func it calls); it pauses again on
  // the subtest's first line
  const restartSubtest = () => {
    setBusy(true);
    api
      .dbg(DBG.RESTART_SUBTEST, filePath, tc, sessionRef.current)
      .then((d) => {
        // status comes from the pushed frames (the replay's pause can arrive before this response)
        setMsg(d.s === false ? d.m || 'Restart rejected.' : 'Subtest restarted.');
      })
      .catch((e) => setMsg(e.message))
      .finally(() => setBusy(false));
  };
  const terminate = () => {
    doneRef.current = true;
    stopStream();
    api.dbg(DBG.TERMINATE, filePath, tc, sessionRef.current).catch(() => {});
    setStatus('terminated');
    setMsg('Stopped.');
  };

  const toggleBreakpoint = (ln: number) => {
    const has = breakpoints.has(ln);
    api
      .dbg(has ? DBG.removeBreakpoint(ln) : DBG.addBreakpoint(ln), filePath, tc, sessionRef.current)
      .then((d) => {
        if (d.s === false) {
          setMsg(d.m || 'Breakpoint change rejected.');
          return;
        }
        setBreakpoints((prev) => {
          const next = new Set(prev);
          if (has) next.delete(ln);
          else next.add(ln);
          return next;
        });
      })
      .catch((e) => setMsg(e.message));
  };

  const active = status === 'paused' || status === 'running';
  const canStep = status === 'paused' && !busy;

  // F5 step-into · F6 step-over · F7 step-out · F8 continue (matches the classic console)
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (!canStep) return;
      const map: Record<string, () => void> = { F5: stepInto, F6: stepOver, F7: stepOut, F8: cont };
      const fn = map[e.key];
      if (fn) {
        e.preventDefault();
        fn();
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [canStep]);

  return (
    <div className="dbg">
      <div className="dbg-bar">
        <button className="btn small" title="Continue — run to next breakpoint (F8)" disabled={!canStep} onClick={cont}>
          ▶ Continue
        </button>
        <button className="btn small" title="Step over (F6)" disabled={!canStep} onClick={stepOver}>
          ⤼ Over
        </button>
        <button className="btn small" title="Step into (F5)" disabled={!canStep} onClick={stepInto}>
          ↳ Into
        </button>
        <button className="btn small" title="Step out (F7)" disabled={!canStep} onClick={stepOut}>
          ↰ Out
        </button>
        <button className="btn small" title="Restart the current subtest from its first line" disabled={!canStep} onClick={restartSubtest}>
          ⟲ Restart subtest
        </button>
        <button className="btn small" title="Suspend" disabled={!active || busy} onClick={suspend}>
          ⏸ Suspend
        </button>
        <button className="btn small" title="Stop the debug session" onClick={terminate}>
          ⏹ Stop
        </button>
        <button className="btn ghost small" onClick={onExit}>
          Close
        </button>
        <span className={'exec-status exec-' + (status === 'done' ? 'done' : status === 'error' ? 'failed' : active ? 'running' : 'starting')}>
          {status === 'initializing' && <span className="spinner" />}
          {msg}
        </span>
      </div>
      <div className="dbg-file mono small muted">
        {currentFile}
        {status === 'done' && (
          <button className="btn ghost small" style={{ marginLeft: 10 }} onClick={onExit}>
            Back to editor
          </button>
        )}
        {traceUrl && (
          <a
            className="btn small"
            style={{ marginLeft: 10 }}
            href={traceUrl}
            data-testid="dbg-trace"
            onClick={(e) => {
              if (!onOpenTrace) return; // no viewer hook: plain download
              e.preventDefault();
              onOpenTrace(traceUrl);
            }}
          >
            Open trace
          </a>
        )}
      </div>
      <div className="dbg-split">
        <DebugCodeView
          code={code}
          currentLine={currentLine}
          breakpoints={breakpoints}
          debuggableLines={debuggable}
          onToggleBreakpoint={toggleBreakpoint}
        />
        <LivePane file={filePath} active={active} />
      </div>
      <div className="view-note muted small">
        Click a line's gutter to toggle a breakpoint. Stepping into an <code>@call</code>/include switches
        to that file. The full run report is in the <b>Reports</b> tab.
      </div>
    </div>
  );
}
