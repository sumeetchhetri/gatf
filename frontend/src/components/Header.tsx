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
import type { Trace } from '../trace/types';
import { fmtBytes, fmtOffset } from '../util';

export function Header({
  trace,
  onClose,
  onLoadTest,
  loadBusy = false,
  loadMsg = null,
}: {
  trace: Trace;
  onClose: () => void;
  /** M17: turn this run's network.har into a load test and run it (absent when the trace has no network) */
  onLoadTest?: () => void;
  loadBusy?: boolean;
  loadMsg?: string | null;
}) {
  const m = trace.meta;
  const dur = trace.t1 - trace.t0;
  const recs: Array<[string, boolean]> = [
    ['network', m.recNetwork],
    ['console', m.recConsole],
    ['snapshots', m.recSnapshots],
    ['metrics', m.recMetrics],
    ['cpu', m.recCpu],
    ['coverage', m.recCoverage],
    ['video', m.recVideo],
  ];
  return (
    <div className="hdr">
      <div className="hdr-main">
        <div className="hdr-title-lg">
          {m.selFile ? m.selFile.split('/').pop() : m.runId}
          <span className="hdr-sub mono">{m.runId}</span>
        </div>
        <div className="hdr-facts mono">
          <span>{m.browser || 'browser?'}</span>
          <span className="dot">·</span>
          <span title="transport protocol">{m.protocol || 'cdp'}</span>
          <span className="dot">·</span>
          <span title="trace duration">{fmtOffset(dur)}</span>
          {m.truncatedBodies > 0 && (
            <>
              <span className="dot">·</span>
              <span className="warn-badge" title="response bodies dropped over the size budget">
                {m.truncatedBodies} bodies truncated
              </span>
            </>
          )}
          {m.bytesCaptured > 0 && (
            <>
              <span className="dot">·</span>
              <span title="captured response-body bytes">{fmtBytes(m.bytesCaptured)} captured</span>
            </>
          )}
        </div>
      </div>
      <div className="hdr-right">
        <div className="rec-badges">
          {recs.map(([name, on]) => (
            <span key={name} className={'rec' + (on ? ' on' : '')}>
              {name}
            </span>
          ))}
        </div>
        {onLoadTest && (
          <button
            className="btn"
            disabled={loadBusy}
            onClick={onLoadTest}
            title="Save this run's API/document requests as a load test (one case per step, vegeta, 10 users × 30 s) and run it"
          >
            {loadBusy ? 'Creating load test…' : '⚡ Load test this flow'}
          </button>
        )}
        {loadMsg && <span className="small muted">{loadMsg}</span>}
        <button className="btn ghost" onClick={onClose} title="Load a different trace">
          Close
        </button>
      </div>
    </div>
  );
}
