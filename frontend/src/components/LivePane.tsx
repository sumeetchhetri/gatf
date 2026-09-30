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

// M15: the live screencast of a running script, beside the step list. Frames are the trace video recorder's own
// (GET /api/live), so they appear only when the config records video (traceVideo). Polls for the newest frame;
// after the run ends the last frame stays up.
export const LIVE_POLL_MS = 250;
export const LIVE_HINT_AFTER_MS = 6000;

export default function LivePane({ file, active }: { file: string; active: boolean }) {
  const [src, setSrc] = useState<string | null>(null);
  const [ended, setEnded] = useState(false);
  const [waitedLong, setWaitedLong] = useState(false);
  const seqRef = useRef(0);
  const urlRef = useRef<string | null>(null);
  // a frame marked ended before this pane mounted is the previous run's: never show it as this run's
  const mountedAt = useRef(Date.now());

  useEffect(() => {
    if (!active) return;
    let stop = false;
    let busy = false;
    setEnded(false);
    setWaitedLong(false);
    const started = Date.now();
    const tick = () => {
      if (stop || busy) return;
      busy = true;
      api
        .liveFrame(file, seqRef.current)
        .then((f) => {
          if (stop) return;
          if (f && f.ended && f.ts < mountedAt.current) {
            seqRef.current = f.seq;
          } else if (f) {
            seqRef.current = f.seq;
            const u = URL.createObjectURL(f.blob);
            if (urlRef.current) URL.revokeObjectURL(urlRef.current);
            urlRef.current = u;
            setSrc(u);
            setEnded(f.ended);
          } else if (!urlRef.current && Date.now() - started > LIVE_HINT_AFTER_MS) {
            setWaitedLong(true);
          }
        })
        .catch(() => {
          /* transient — keep polling */
        })
        .finally(() => {
          busy = false;
        });
    };
    tick();
    const id = window.setInterval(tick, LIVE_POLL_MS);
    return () => {
      stop = true;
      clearInterval(id);
    };
  }, [file, active]);

  useEffect(
    () => () => {
      if (urlRef.current) URL.revokeObjectURL(urlRef.current);
    },
    [],
  );

  return (
    <div className="live-pane">
      <div className="live-head small">
        <span className={'live-dot' + (src && active && !ended ? ' on' : '')} /> Live{src && (ended || !active) ? ' — run ended' : ''}
      </div>
      {src ? (
        <img className="live-img" src={src} alt="live browser screencast" />
      ) : (
        <div className="live-empty muted small">
          {waitedLong
            ? 'No live frames. Turn on video in the config (Trace › traceVideo) to watch the browser here.'
            : 'Waiting for the browser…'}
        </div>
      )}
    </div>
  );
}
