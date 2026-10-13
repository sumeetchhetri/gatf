#!/usr/bin/env node
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
// trace-capabilities.mjs -- CI glue (Δ159): print which trace capabilities the browser served.
//
// GATF's recorders skip a capability the transport does not support WITHOUT a log line; the only
// record is the "capabilities" list in each trace.zip's metadata.json (G10). This makes that explicit
// in the job log: one "served"/"MISSING" line per capability, with what degrades. Report-only: exit 0
// unless no trace.zip was found (exit 1). Dependency-free (node:fs + node:zlib).
//
// Usage: node trace-capabilities.mjs <outDir> [--self-test]
import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';
import { pathToFileURL } from 'node:url';

// Mirrors com.gatf.selenium.trace.Capability (order + names).
export const CAPABILITIES = {
  NETWORK: 'no network.har / request list in the Trace Viewer',
  CONSOLE: 'no console/log entries in the trace',
  DOM_SNAPSHOT: 'no per-action DOM snapshot',
  SCREENSHOT: 'no per-action screenshots',
  RESPONSE_BODY: 'HAR has no response bodies',
  SCREENCAST: 'no video / live screencast',
  METRICS: 'no performance/memory samples',
  CPU_PROFILE: 'no .cpuprofile (CDP Profiler only)',
  COVERAGE: 'no JS/CSS coverage (CDP Profiler/CSS only)',
  PRINT_PDF: '`printpdf` fails: "printpdf: this browser session cannot print to PDF (...)"',
  ROUTE: '`route` fails: "route: this browser session cannot intercept requests (needs CDP on Chromium, or a BiDi session: webSocketUrl)"',
  DOM_SNAPSHOT_RICH: 'no styled snapshot (before/after time-travel falls back to the plain DOM)',
};

export function report(meta) {
  const served = new Set(String(meta.capabilities || '').split(',').map((s) => s.trim()).filter(Boolean));
  const lines = [`browser=${meta.browser || '?'} served=${served.size}/${Object.keys(CAPABILITIES).length}`];
  for (const [c, why] of Object.entries(CAPABILITIES)) {
    lines.push(served.has(c) ? `  served   ${c}` : `  MISSING  ${c} -> ${why}`);
  }
  return lines;
}

// Minimal central-directory zip reader (stored + deflate), as in tools/smoke/validate-trace.mjs.
function readEntry(file, wanted) {
  const b = fs.readFileSync(file);
  let eocd = -1;
  for (let i = b.length - 22; i >= Math.max(0, b.length - 22 - 65535); i--) {
    if (b.readUInt32LE(i) === 0x06054b50) { eocd = i; break; }
  }
  if (eocd < 0) throw new Error('not a zip');
  const count = b.readUInt16LE(eocd + 10);
  let off = b.readUInt32LE(eocd + 16);
  for (let n = 0; n < count; n++) {
    const method = b.readUInt16LE(off + 10), csize = b.readUInt32LE(off + 20);
    const nlen = b.readUInt16LE(off + 28), xlen = b.readUInt16LE(off + 30), clen = b.readUInt16LE(off + 32);
    const lho = b.readUInt32LE(off + 42);
    const name = b.toString('utf8', off + 46, off + 46 + nlen);
    if (name === wanted) {
      const start = lho + 30 + b.readUInt16LE(lho + 26) + b.readUInt16LE(lho + 28);
      const raw = b.subarray(start, start + csize);
      return (method === 0 ? raw : zlib.inflateRawSync(raw)).toString('utf8');
    }
    off += 46 + nlen + xlen + clen;
  }
  return null;
}

function findTraces(dir, acc = []) {
  if (!fs.existsSync(dir)) return acc;
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) findTraces(p, acc);
    else if (e.name.endsWith('-trace.zip')) acc.push(p);
  }
  return acc;
}

function selfTest() {
  const l = report({ browser: 'safari', capabilities: 'DOM_SNAPSHOT,SCREENSHOT,METRICS,DOM_SNAPSHOT_RICH' });
  const ok = (c, m) => { if (!c) { console.log('FAIL ' + m); process.exitCode = 1; } else console.log('ok   ' + m); };
  ok(l[0] === 'browser=safari served=4/12', 'header counts served/total');
  ok(l.some((s) => s.startsWith('  MISSING  ROUTE -> `route` fails')), 'ROUTE missing names the run-log error');
  ok(l.some((s) => s === '  served   SCREENSHOT'), 'served capability listed');
  ok(report({}).filter((s) => s.includes('MISSING')).length === 12, 'empty metadata -> all missing');
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) {
  if (process.argv.includes('--self-test')) selfTest();
  else {
    const dir = process.argv[2] || '.';
    const traces = findTraces(dir);
    if (!traces.length) {
      console.log(`capability probe: no *-trace.zip under ${dir}`);
      console.log('  GATF records a trace only with CDP or a live WebDriver BiDi socket: on this browser enableTrace');
      console.log('  is a silent no-op (or the session did not start: see the probe log).');
      process.exit(1);
    }
    for (const t of traces) {
      let meta = {};
      try { meta = JSON.parse(readEntry(t, 'metadata.json') || '{}'); } catch (e) { console.log(`${t}: metadata.json unreadable (${e.message})`); }
      console.log(`capability probe: ${path.basename(t)}`);
      for (const s of report(meta)) console.log(s);
    }
  }
}
