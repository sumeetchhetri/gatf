import { describe, it, expect } from 'vitest';
import { zipSync } from 'fflate';
import { parseFiles, loadTraceZip } from './loadTrace';

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
