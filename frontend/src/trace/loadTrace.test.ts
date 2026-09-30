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
import { describe, it, expect } from 'vitest';
import { zipSync } from 'fflate';
import { parseFiles, loadTraceZip, pageVitals } from './loadTrace';

const enc = new TextEncoder();
const j = (o: unknown) => enc.encode(JSON.stringify(o));
const nd = (...os: unknown[]) => enc.encode(os.map((o) => JSON.stringify(o)).join('\n') + '\n');

function fullMeta(over: Record<string, unknown> = {}) {
  return {
    runId: 'R1', selFile: 'login.sel', browser: 'chrome', protocol: 'cdp',
    startTs: 1000, endTs: 5000,
    recNetwork: true, recConsole: true, recSnapshots: false, recMetrics: false,
    recCpu: false, recCoverage: true, recVideo: false, truncatedBodies: 0, bytesCaptured: 123,
    ...over,
  };
}
const harEntry = (startMsIso: number, url: string) => ({
  startedDateTime: new Date(startMsIso).toISOString(), time: 5,
  request: { method: 'GET', url, httpVersion: 'HTTP/1.1', headers: [] },
  response: { status: 200, statusText: 'OK', httpVersion: 'HTTP/1.1', headers: [], content: { size: 0, mimeType: 'text/html' }, bodySize: 0 },
});

describe('parseFiles', () => {
  it('parses metadata, sorts events + HAR by time, derives t0/t1', () => {
    const files: Record<string, Uint8Array> = {
      'metadata.json': j(fullMeta()),
      'events.ndjson': nd({ ts: 3000, type: 'action', name: 'click', detail: null }, { ts: 1500, type: 'navigation', name: 'nav', detail: null }),
      'console.ndjson': nd({ ts: 2000, level: 'log', text: 'hi', url: null, line: null, source: null }),
      'network.har': j({ log: { version: '1.2', creator: { name: 'gatf', version: '1' }, entries: [harEntry(4000, 'http://x'), harEntry(2500, 'http://y')] } }),
      'coverage.json': j({ js: [], css: [] }),
    };
    const t = parseFiles(files);
    expect(t.meta.runId).toBe('R1');
    expect(t.meta.browser).toBe('chrome');
    expect(t.events.map((e) => e.ts)).toEqual([1500, 3000]);                 // sorted
    expect(t.har!.log.entries.map((e) => e.startMs)).toEqual([2500, 4000]);  // startMs derived + sorted
    expect(t.t0).toBe(1000);
    expect(t.t1).toBe(4000);            // max data ts (HAR 4000 > events 3000 > console 2000)
    expect(t.coverage).not.toBeNull();
    expect(t.videoUrl).toBeNull();      // no video.mp4 -> no createObjectURL
  });

  it('synthesizes metadata from events when metadata.json is absent', () => {
    const files: Record<string, Uint8Array> = {
      'events.ndjson': nd({ ts: 200, type: 'action', name: 'a', detail: null }, { ts: 100, type: 'nav', name: 'n', detail: null }),
    };
    const t = parseFiles(files);
    expect(t.meta.runId).toBe('(unknown)');
    expect(t.meta.startTs).toBe(100);
    expect(t.meta.endTs).toBe(200);
    expect(t.meta.recNetwork).toBe(false);
    expect(t.t0).toBe(100);
    expect(t.events.map((e) => e.ts)).toEqual([100, 200]);
  });
});

describe('loadTraceZip', () => {
  it('unzips raw bytes and parses (fflate round-trip)', () => {
    const zipped = zipSync({
      'metadata.json': j(fullMeta({ runId: 'Z', startTs: 1, endTs: 2 })),
      'events.ndjson': nd({ ts: 1, type: 'x', name: null, detail: null }),
    });
    const t = loadTraceZip(zipped);
    expect(t.meta.runId).toBe('Z');
    expect(t.events).toHaveLength(1);
  });
});

describe('pageVitals', () => {
  it('returns well-formed per-page rows oldest first, [] for older traces', () => {
    expect(pageVitals(null)).toEqual([]);
    expect(pageVitals({ samples: [], webVitals: { lcp: 5 } })).toEqual([]);
    const rows = pageVitals({
      samples: [],
      webVitalsByPage: [
        { url: 'https://a/2', ts: 20, lcp: 900 },
        null as never,
        { url: 'https://a/1', ts: 10, lcp: 2600, cls: 0.2 },
        { url: 'bad' } as never,
      ],
    });
    expect(rows.map((r) => r.url)).toEqual(['https://a/1', 'https://a/2']);
  });
});
