import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { Trace, SnapshotRef } from './trace/types';
import { loadTraceZip } from './trace/loadTrace';
import { clamp } from './util';
import { Dropzone } from './components/Dropzone';
import { Header } from './components/Header';
import { Timeline } from './components/Timeline';
import { Stage, type StageMode } from './components/Stage';
import { NetworkTab } from './components/NetworkTab';
import { ConsoleTab } from './components/ConsoleTab';
import { PerfTab } from './components/PerfTab';
import { CoverageTab } from './components/CoverageTab';
import { CpuTab } from './components/CpuTab';
import { EventsTab } from './components/EventsTab';

type TabId = 'events' | 'network' | 'console' | 'perf' | 'coverage' | 'cpu';

function activeSnapshot(snaps: SnapshotRef[], t: number): SnapshotRef | null {
  let found: SnapshotRef | null = null;
  for (const s of snaps) {
    if (s.ts <= t) found = s;
    else break;
  }
  return found ?? (snaps.length ? snaps[0] : null);
}

export default function App() {
  const [trace, setTrace] = useState<Trace | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loadingUrl, setLoadingUrl] = useState<string | null>(null);

  const [currentTime, setCurrentTimeState] = useState(0);
  const [playing, setPlaying] = useState(false);
  const [stageMode, setStageMode] = useState<StageMode>('video');
  const [tab, setTab] = useState<TabId>('events');

  const videoRef = useRef<HTMLVideoElement>(null);
  const currentTimeRef = useRef(0);
  const lastWall = useRef(0);

  const setCurrentTime = useCallback(
    (t: number, fromUser: boolean) => {
      const c = trace ? clamp(t, trace.t0, trace.t1) : t;
      currentTimeRef.current = c;
      setCurrentTimeState(c);
      if (fromUser && trace) {
        const v = videoRef.current;
        if (v && stageMode === 'video' && trace.videoUrl) {
          const target = clamp((c - trace.t0) / 1000, 0, v.duration || 0);
          if (Number.isFinite(target)) v.currentTime = target;
        }
      }
    },
    [trace, stageMode],
  );

  const ingest = useCallback((bytes: Uint8Array) => {
    try {
      const t = loadTraceZip(bytes);
      setError(null);
      setTrace(t);
      currentTimeRef.current = t.t0;
      setCurrentTimeState(t.t0);
      setPlaying(false);
      setStageMode(t.videoUrl ? 'video' : t.snapshots.length ? 'screenshot' : 'video');
      setTab(t.events.length ? 'events' : t.har ? 'network' : 'console');
    } catch (e) {
      console.error(e);
      setError('Could not read this file as a GATF trace.zip. ' + (e instanceof Error ? e.message : ''));
    }
  }, []);

  // optional auto-load via ?trace=<url> (same-origin bundle served by the report)
  useEffect(() => {
    const url = new URLSearchParams(window.location.search).get('trace');
    if (!url) return;
    setLoadingUrl(url);
    fetch(url)
      .then((r) => {
        if (!r.ok) throw new Error(`HTTP ${r.status}`);
        return r.arrayBuffer();
      })
      .then((buf) => ingest(new Uint8Array(buf)))
      .catch((e) => setError(`Could not fetch trace at ${url} — drag the file in instead. (${e.message})`))
      .finally(() => setLoadingUrl(null));
  }, [ingest]);

  // playback loop
  useEffect(() => {
    if (!playing || !trace) return;
    const useVideoClock = stageMode === 'video' && !!videoRef.current && !!trace.videoUrl;
    const v = videoRef.current;
    if (useVideoClock && v) void v.play().catch(() => {});
    lastWall.current = performance.now();
    let raf = 0;
    const tick = () => {
      let t: number;
      if (useVideoClock && v) {
        t = trace.t0 + v.currentTime * 1000;
      } else {
        const now = performance.now();
        t = currentTimeRef.current + (now - lastWall.current);
        lastWall.current = now;
      }
      if (t >= trace.t1) {
        currentTimeRef.current = trace.t1;
        setCurrentTimeState(trace.t1);
        setPlaying(false);
        return;
      }
      currentTimeRef.current = t;
      setCurrentTimeState(t);
      raf = requestAnimationFrame(tick);
    };
    raf = requestAnimationFrame(tick);
    return () => {
      cancelAnimationFrame(raf);
      if (v) v.pause();
    };
  }, [playing, trace, stageMode]);

  // when switching into video mode while paused, align the video frame to the clock
  useEffect(() => {
    if (stageMode !== 'video' || playing || !trace) return;
    const v = videoRef.current;
    if (!v || !trace.videoUrl) return;
    const seek = () => {
      const target = clamp((currentTimeRef.current - trace.t0) / 1000, 0, v.duration || 0);
      if (Number.isFinite(target) && Math.abs(v.currentTime - target) > 0.2) v.currentTime = target;
    };
    if (v.readyState >= 1) seek();
    else v.addEventListener('loadedmetadata', seek, { once: true });
  }, [stageMode, playing, trace]);

  const togglePlay = useCallback(() => {
    if (!trace) return;
    setPlaying((p) => {
      const next = !p;
      // restart from the beginning if we're parked at the end
      if (next && currentTimeRef.current >= trace.t1 - 1) {
        currentTimeRef.current = trace.t0;
        setCurrentTimeState(trace.t0);
      }
      return next;
    });
  }, [trace]);

  const activeSnap = useMemo(
    () => (trace ? activeSnapshot(trace.snapshots, currentTime) : null),
    [trace, currentTime],
  );

  if (!trace) {
    return (
      <div className="app center">
        <Dropzone onBytes={ingest} error={loadingUrl ? null : error} />
        {loadingUrl && <div className="loading">Loading trace…</div>}
      </div>
    );
  }

  const tabs: Array<[TabId, string, boolean, number | null]> = [
    ['events', 'Events', trace.events.length > 0, trace.events.length],
    ['network', 'Network', !!trace.har, trace.har?.log.entries.length ?? 0],
    ['console', 'Console', trace.console.length > 0, trace.console.length],
    ['perf', 'Performance', !!(trace.perf || trace.memory), null],
    ['coverage', 'Coverage', !!trace.coverage, null],
    ['cpu', 'CPU', !!trace.cpuProfile, null],
  ];

  return (
    <div className="app">
      <Header trace={trace} onClose={() => { setTrace(null); setError(null); }} />
      <div className="main">
        <div className="left">
          <Stage
            trace={trace}
            activeSnap={activeSnap}
            mode={stageMode}
            setMode={setStageMode}
            videoRef={videoRef}
          />
          <Timeline
            trace={trace}
            currentTime={currentTime}
            setCurrentTime={setCurrentTime}
            playing={playing}
            togglePlay={togglePlay}
          />
        </div>
        <div className="right">
          <div className="tabbar">
            {tabs.map(([id, label, enabled, count]) => (
              <button
                key={id}
                className={'tab' + (tab === id ? ' on' : '')}
                disabled={!enabled}
                onClick={() => setTab(id)}
              >
                {label}
                {enabled && count != null && <span className="tab-count">{count}</span>}
              </button>
            ))}
          </div>
          <div className="tabpanel">
            {tab === 'events' && (
              <EventsTab trace={trace} currentTime={currentTime} onSeek={(ts) => setCurrentTime(ts, true)} />
            )}
            {tab === 'network' && <NetworkTab trace={trace} currentTime={currentTime} />}
            {tab === 'console' && <ConsoleTab trace={trace} currentTime={currentTime} />}
            {tab === 'perf' && <PerfTab trace={trace} currentTime={currentTime} />}
            {tab === 'coverage' && <CoverageTab trace={trace} />}
            {tab === 'cpu' && <CpuTab trace={trace} />}
          </div>
        </div>
      </div>
    </div>
  );
}
