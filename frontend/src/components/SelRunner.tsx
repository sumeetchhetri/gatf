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
import DebugCodeView from './DebugCodeView';
import LivePane from './LivePane';

type Status = 'starting' | 'running' | 'paused' | 'done' | 'failed';

// Runs a single .sel test via /reports?action=playTest and tracks the live line + pause state by polling
// pausedLineNo. Pause-on-element (pauseElSearch/unPauseElSearch), Restart Subtest and Stop mirror the
// classic console's run controls.
export default function SelRunner({
  filePath,
  code,
  onExit,
  onFinished,
  embedded = false,
}: {
  filePath: string;
  code: string;
  onExit: () => void;
  /** M15: called once when the run ends (passed, failed or stopped) — the editor's watch mode queues re-runs on it */
  onFinished?: (ok: boolean) => void;
  /** M15: shown beside the editor (watch mode): no "Back to editor" */
  embedded?: boolean;
}) {
  const [status, setStatus] = useState<Status>('starting');
  const [msg, setMsg] = useState('Starting run…');
  const [currentLine, setCurrentLine] = useState<number | null>(null);
  const [pauseLabel, setPauseLabel] = useState<'Pause' | 'Continue' | 'Pausing…' | 'Resuming…'>('Pause');
  const [errLine, setErrLine] = useState<{ line: number; file: string; msg: string } | null>(null);

  const pollRef = useRef<number | null>(null);
  const doneRef = useRef(false);

  const stopPolling = () => {
    if (pollRef.current != null) {
      clearInterval(pollRef.current);
      pollRef.current = null;
    }
  };
  const finish = (s: Status, m: string) => {
    if (doneRef.current) return;
    doneRef.current = true;
    stopPolling();
    setStatus(s);
    setMsg(m);
    setCurrentLine(null);
    onFinished?.(s === 'done');
  };

  useEffect(() => {
    // fire the run (resolves on completion) …
    api
      .playTest(filePath, '')
      .then((d) => {
        if (doneRef.current) return;
        if (Array.isArray(d.error)) {
          const e = d.error as unknown[];
          setErrLine({ line: Number(e[1]), file: String(e[2] ?? filePath), msg: String(e[3] ?? 'Error') });
          finish('failed', 'Run failed — ' + String(e[3] ?? 'error'));
        } else if (d.error && typeof d.error === 'string') {
          finish('failed', d.error);
        } else {
          finish('done', 'Run complete — see the Reports tab.');
        }
      })
      .catch((e) => !doneRef.current && finish('failed', e.message));

    // … and poll the paused line / state alongside it
    pollRef.current = window.setInterval(() => {
      api
        .pausedLine()
        .then((d) => {
          if (doneRef.current) return;
          if (d.running === false) {
            // completion is reported by the playTest promise; just stop tracking the line
            setCurrentLine(null);
            return;
          }
          const line = typeof d.line === 'number' ? d.line : -1;
          if (d.is_paused === true && line > 0) {
            setCurrentLine(line);
            setPauseLabel('Continue');
            setStatus('paused');
            setMsg('Paused at line ' + line + '.');
          } else if (d.is_paused === true && line === -1) {
            setPauseLabel('Pausing…');
            setStatus('running');
          } else if (d.is_paused === false && line > 0) {
            setPauseLabel('Resuming…');
            setStatus('running');
          } else {
            setCurrentLine(null);
            setPauseLabel('Pause');
            setStatus('running');
            setMsg('Running…');
          }
        })
        .catch(() => {
          /* transient — keep polling */
        });
    }, 1000);

    return () => {
      doneRef.current = true;
      stopPolling();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const togglePause = () => {
    if (pauseLabel === 'Pause') {
      setPauseLabel('Pausing…');
      api.pauseEl(true).catch(() => {});
    } else if (pauseLabel === 'Continue') {
      setPauseLabel('Resuming…');
      api.pauseEl(false).catch(() => {});
    }
  };
  const restart = () => api.restartSubtest().catch((e) => setMsg('Restart failed: ' + e.message));
  const stop = () => {
    api.stopRun().catch(() => {});
    finish('done', 'Stopped.');
  };

  const running = status === 'running' || status === 'paused';

  return (
    <div className="dbg">
      <div className="dbg-bar">
        <button className="btn small" title="Pause / continue on the next element search" disabled={!running} onClick={togglePause}>
          {pauseLabel === 'Continue' ? '▶ ' + pauseLabel : '⏸ ' + pauseLabel}
        </button>
        <button className="btn small" title="Restart the current subtest" disabled={!running} onClick={restart}>
          ⟳ Restart subtest
        </button>
        <button className="btn small" title="Stop the run" disabled={!running} onClick={stop}>
          ⏹ Stop
        </button>
        <button className="btn ghost small" onClick={onExit}>
          Close
        </button>
        <span className={'exec-status exec-' + (status === 'done' ? 'done' : status === 'failed' ? 'failed' : running ? 'running' : 'starting')}>
          {status === 'starting' && <span className="spinner" />}
          {msg}
        </span>
      </div>
      {errLine && (
        <div className="tf-problems">
          <div className="tf-problem" onClick={onExit}>
            <span className="tf-problem-ln">L{errLine.line}</span>
            <span className="tf-problem-msg">
              {errLine.file}: {errLine.msg}
            </span>
          </div>
        </div>
      )}
      {(status === 'done' || status === 'failed') && (
        <div className="dbg-file">
          <button className="btn ghost small" onClick={() => (window.location.hash = '#/reports')}>
            Open Reports →
          </button>
          {!embedded && (
            <button className="btn ghost small" style={{ marginLeft: 8 }} onClick={onExit}>
              Back to editor
            </button>
          )}
        </div>
      )}
      <div className="dbg-split">
        <DebugCodeView
          code={code}
          currentLine={currentLine}
          breakpoints={new Set()}
          debuggableLines={null}
          onToggleBreakpoint={() => {}}
          showBreakpoints={false}
        />
        <LivePane file={filePath} active={status === 'starting' || running} />
      </div>
      <div className="view-note muted small">
        Runs the whole script; the current line highlights while it executes. <b>Pause</b> stops at the next
        element lookup so you can inspect the browser, then <b>Continue</b>. The full report lands in the{' '}
        <b>Reports</b> tab.
      </div>
    </div>
  );
}
