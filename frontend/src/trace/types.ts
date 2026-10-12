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
// Type shapes for the GATF trace.zip bundle. Field names mirror the Java writers in
// com.gatf.selenium.trace exactly (TraceRecorder, *Recorder, NetworkRecorder.buildHar).

export interface Metadata {
  runId: string;
  selFile: string | null;
  browser: string | null;
  protocol: string | null;
  startTs: number;
  endTs: number;
  recNetwork: boolean;
  recConsole: boolean;
  recSnapshots: boolean;
  recMetrics: boolean;
  recCpu: boolean;
  recCoverage: boolean;
  recVideo: boolean;
  truncatedBodies: number;
  bytesCaptured: number;
  /** H2: 2 for traces whose action events carry `a` and whose snapshots may be v2; absent on older traces. */
  format?: number;
  domSnapshots?: 'off' | 'lite' | 'full';
  /** why per-action capture degraded or stopped: slow | budget | count */
  domSnapshotsLimit?: string | null;
  domSnapshotBytes?: number;
}

// events.ndjson — one object per line
export interface TraceEvent {
  ts: number;
  type: string; // lifecycle | navigation | snapshot | action | event
  name: string | null;
  detail: string | null;
  /** H2: the action id (per-action events only) that keys its before/after snapshots */
  a?: number;
}

// console.ndjson — one object per line
export interface ConsoleEntry {
  ts: number;
  level: string;
  text: string | null;
  url: string | null;
  line: number | null;
  source: string | null;
}

// snapshots.ndjson index — html/screenshot are zip-relative paths (may be null)
export interface SnapshotRef {
  n: number;
  ts: number;
  label: string | null;
  html: string | null; // e.g. "snapshots/snapshot-1.html"
  screenshot: string | null; // e.g. "screenshots/shot-1.png"
  /** H2 (format 2): 2 = styled, `dom` is its manifest; 1/absent = lite outerHTML */
  v?: number;
  dom?: string | null;
  action?: number;
  phase?: 'before' | 'after';
  /** the physical capture; "after k" and "before k+1" share one */
  capture?: number;
  /** the action's element at capture time: viewport rect [x, y, w, h]; the HTML marks it data-gatf-target~=action */
  target?: { rect: [number, number, number, number] } | null;
}

/** H2: snapshots/snapshot-<c>.json, the styled snapshot's manifest. */
export interface DomStyle {
  owner: 'link' | 'style' | 'adopted';
  /** stylesheet URL (link) or the document URL; base for relative url()s */
  href: string | null;
  media: string | null;
  /** owner=style: index of the <style> element (document order) whose CSSOM-only rules this is */
  index: number;
  /** resources/<sha1> with the CSS text, or null (cross-origin: use the HAR body for href) */
  ref: string | null;
}
export interface DomManifest {
  v: 2;
  source: 'cdp' | 'script';
  url: string | null;
  title: string | null;
  viewport: { w: number; h: number };
  scroll: { x: number; y: number };
  truncated: boolean;
  styles: DomStyle[];
}

// network.har (HAR 1.2 subset actually emitted by NetworkRecorder.buildHar)
export interface HarHeader {
  name: string;
  value: string;
}
export interface HarContent {
  size: number;
  mimeType: string;
  _file?: string; // zip-relative path into resources/<sha1> when a body was captured
}
export interface HarRequest {
  method: string;
  url: string;
  httpVersion: string;
  headers: HarHeader[];
}
export interface HarResponse {
  status: number;
  statusText: string;
  httpVersion: string;
  headers: HarHeader[];
  content: HarContent;
  bodySize: number;
}
export interface HarEntry {
  startedDateTime: string; // ISO-8601 (Instant.toString of epoch millis)
  time: number; // ms, or -1 when unknown
  request: HarRequest;
  response: HarResponse;
  _failed?: string; // present + error text when the request failed
  // derived at load time:
  startMs: number; // epoch millis parsed from startedDateTime
}
export interface Har {
  log: {
    version: string;
    creator: { name: string; version: string };
    browser?: { name: string; version: string };
    entries: HarEntry[];
  };
}

// performance.json
export interface PerfSample {
  ts: number;
  [metric: string]: number;
}
export interface WebVitals {
  ttfb?: number;
  domContentLoaded?: number;
  load?: number;
  domInteractive?: number;
  fcp?: number;
  fp?: number;
  lcp?: number;
  [k: string]: number | undefined;
}
/** Q9: vitals of one top-level document the script visited (performance.json webVitalsByPage). */
export interface PageVitals {
  url: string | null;
  ts: number;
  lcp?: number;
  cls?: number;
  fcp?: number;
  ttfb?: number;
  domContentLoaded?: number;
  load?: number;
}
export interface Performance {
  samples: PerfSample[];
  /** the page open when the trace stopped */
  webVitals?: WebVitals;
  /** every page, oldest first (newer traces) */
  webVitalsByPage?: PageVitals[];
}

// memory.json
export interface MemSample {
  ts: number;
  jsHeapUsed: number | null;
  jsHeapTotal: number | null;
}
export interface Memory {
  samples: MemSample[];
}

// coverage.json — raw CDP payloads (Profiler.takePreciseCoverage.result + CSS ruleUsage)
export interface JsCoverageRange {
  startOffset: number;
  endOffset: number;
  count: number;
}
export interface JsCoverageFn {
  functionName: string;
  isBlockCoverage: boolean;
  ranges: JsCoverageRange[];
}
export interface JsCoverageScript {
  scriptId: string;
  url: string;
  functions: JsCoverageFn[];
}
export interface CssRuleUsage {
  styleSheetId: string;
  startOffset: number;
  endOffset: number;
  used: boolean;
}
export interface Coverage {
  js?: JsCoverageScript[];
  css?: CssRuleUsage[];
}

// The parsed, in-memory trace the viewer renders.
export interface Trace {
  meta: Metadata;
  events: TraceEvent[];
  console: ConsoleEntry[];
  snapshots: SnapshotRef[];
  har: Har | null;
  perf: Performance | null;
  memory: Memory | null;
  coverage: Coverage | null;
  cpuProfile: unknown | null; // V8 .cpuprofile (rendered as a summary)
  // raw file bytes, keyed by zip-relative path, for lazy body/snapshot/screenshot access
  files: Record<string, Uint8Array>;
  videoUrl: string | null; // object URL for video.mp4 (or null)
  t0: number; // == meta.startTs
  t1: number; // == meta.endTs
}
