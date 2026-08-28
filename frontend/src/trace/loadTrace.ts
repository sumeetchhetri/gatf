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
import { unzipSync } from 'fflate';
import type {
  Trace,
  Metadata,
  TraceEvent,
  ConsoleEntry,
  SnapshotRef,
  Har,
  HarEntry,
  Performance,
  Memory,
  Coverage,
} from './types';

const dec = new TextDecoder('utf-8');

function text(files: Record<string, Uint8Array>, path: string): string | null {
  const b = files[path];
  return b ? dec.decode(b) : null;
}

function json<T>(files: Record<string, Uint8Array>, path: string): T | null {
  const t = text(files, path);
  if (t == null) return null;
  try {
    return JSON.parse(t) as T;
  } catch (e) {
    console.warn(`trace: ${path} is not valid JSON`, e);
    return null;
  }
}

function ndjson<T>(files: Record<string, Uint8Array>, path: string): T[] {
  const t = text(files, path);
  if (t == null) return [];
  const out: T[] = [];
  for (const line of t.split('\n')) {
    const s = line.trim();
    if (!s) continue;
    try {
      out.push(JSON.parse(s) as T);
    } catch {
      /* skip a corrupt line rather than fail the whole load */
    }
  }
  return out;
}

const MIME: Record<string, string> = {
  mp4: 'video/mp4',
  webm: 'video/webm',
  png: 'image/png',
  jpg: 'image/jpeg',
  jpeg: 'image/jpeg',
  gif: 'image/gif',
  webp: 'image/webp',
  svg: 'image/svg+xml',
};

export function fileUrl(files: Record<string, Uint8Array>, path: string): string | null {
  const b = files[path];
  if (!b) return null;
  const ext = path.split('.').pop()?.toLowerCase() ?? '';
  const type = MIME[ext] ?? 'application/octet-stream';
  // copy into a fresh ArrayBuffer so the Blob owns its bytes
  return URL.createObjectURL(new Blob([b.slice()], { type }));
}

/** Parse an already-unzipped file map into a Trace. */
export function parseFiles(files: Record<string, Uint8Array>): Trace {
  const meta =
    json<Metadata>(files, 'metadata.json') ?? synthMeta(files);

  const har = json<Har>(files, 'network.har');
  if (har?.log?.entries) {
    for (const e of har.log.entries as HarEntry[]) {
      const ms = Date.parse(e.startedDateTime);
      e.startMs = Number.isFinite(ms) ? ms : meta.startTs;
    }
    har.log.entries.sort((a, b) => a.startMs - b.startMs);
  }

  const videoUrl = files['video.mp4']
    ? fileUrl(files, 'video.mp4')
    : null;

  const events = ndjson<TraceEvent>(files, 'events.ndjson').sort((a, b) => a.ts - b.ts);
  const consoleEntries = ndjson<ConsoleEntry>(files, 'console.ndjson').sort((a, b) => a.ts - b.ts);
  const snapshots = ndjson<SnapshotRef>(files, 'snapshots.ndjson').sort((a, b) => a.ts - b.ts);

  // metadata.endTs can include browser teardown after recording stopped, which would stretch the
  // timeline well past the captured video (making the video look like it "ends early"). Prefer the
  // real data extent — the last event / console / snapshot / network timestamp — and fall back to
  // metadata.endTs only when there is no timeline data at all.
  let hi = -Infinity;
  for (const e of events) if (e.ts > hi) hi = e.ts;
  for (const c of consoleEntries) if (c.ts > hi) hi = c.ts;
  for (const s of snapshots) if (s.ts > hi) hi = s.ts;
  if (har?.log?.entries) {
    for (const e of har.log.entries as HarEntry[]) if (e.startMs > hi) hi = e.startMs;
  }
  const t1 = hi > meta.startTs ? hi : meta.endTs;

  return {
    meta,
    events,
    console: consoleEntries,
    snapshots,
    har: har ?? null,
    perf: json<Performance>(files, 'performance.json'),
    memory: json<Memory>(files, 'memory.json'),
    coverage: json<Coverage>(files, 'coverage.json'),
    cpuProfile: json<unknown>(files, 'cpu.cpuprofile'),
    files,
    videoUrl,
    t0: meta.startTs,
    t1,
  };
}

/** Unzip raw bytes and parse. */
export function loadTraceZip(bytes: Uint8Array): Trace {
  const files = unzipSync(bytes);
  return parseFiles(files);
}

// Fallback when metadata.json is missing/corrupt: derive a time window from whatever
// timelines exist so the viewer still works.
function synthMeta(files: Record<string, Uint8Array>): Metadata {
  const evs = ndjson<TraceEvent>(files, 'events.ndjson');
  let lo = Infinity;
  let hi = -Infinity;
  for (const e of evs) {
    if (e.ts < lo) lo = e.ts;
    if (e.ts > hi) hi = e.ts;
  }
  if (!Number.isFinite(lo)) {
    lo = Date.now();
    hi = lo;
  }
  return {
    runId: '(unknown)',
    selFile: null,
    browser: null,
    protocol: null,
    startTs: lo,
    endTs: hi,
    recNetwork: !!files['network.har'],
    recConsole: !!files['console.ndjson'],
    recSnapshots: !!files['snapshots.ndjson'],
    recMetrics: !!files['performance.json'],
    recCpu: !!files['cpu.cpuprofile'],
    recCoverage: !!files['coverage.json'],
    recVideo: !!files['video.mp4'],
    truncatedBodies: 0,
    bytesCaptured: 0,
  };
}
