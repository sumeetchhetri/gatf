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
// Validate a GATF trace.zip produced by an end-to-end run (port of the retired validate-trace.py; same checks,
// same output). Finds the newest *-trace.zip under <output-dir> and asserts the bundle is well-formed: metadata,
// network HAR 1.2, console + event streams (incl. >= 1 per-action event), snapshots, and — when metadata says they
// ran — performance/memory, cpu, coverage and video. Dependency-free (node:fs + node:zlib).
//
// Usage:  node tools/smoke/validate-trace.mjs <output-dir>     # defaults to '.'
// Exit non-zero on any failed check or if no trace bundle is found.
import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';

function findTrace(dir) {
  let best = null;
  const walk = (d) => {
    let ents;
    try { ents = fs.readdirSync(d, { withFileTypes: true }); } catch { return; }
    for (const e of ents) {
      const p = path.join(d, e.name);
      if (e.isDirectory()) walk(p);
      else if (e.name.endsWith('-trace.zip')) {
        const m = fs.statSync(p).mtimeMs;
        if (!best || m > best.m) best = { p, m };
      }
    }
  };
  walk(dir);
  return best ? best.p : null;
}

// Minimal central-directory zip reader (stored + deflate; what java.util.zip writes).
function readZip(file) {
  const b = fs.readFileSync(file);
  let eocd = -1;
  for (let i = b.length - 22; i >= Math.max(0, b.length - 22 - 65535); i--) {
    if (b.readUInt32LE(i) === 0x06054b50) { eocd = i; break; }
  }
  if (eocd < 0) throw new Error('not a zip (no end-of-central-directory record)');
  const count = b.readUInt16LE(eocd + 10);
  let off = b.readUInt32LE(eocd + 16);
  const entries = new Map();
  for (let n = 0; n < count; n++) {
    if (b.readUInt32LE(off) !== 0x02014b50) throw new Error('corrupt central directory');
    const method = b.readUInt16LE(off + 10);
    const csize = b.readUInt32LE(off + 20);
    const nlen = b.readUInt16LE(off + 28), xlen = b.readUInt16LE(off + 30), clen = b.readUInt16LE(off + 32);
    const lho = b.readUInt32LE(off + 42);
    const name = b.toString('utf8', off + 46, off + 46 + nlen);
    entries.set(name, { method, csize, lho });
    off += 46 + nlen + xlen + clen;
  }
  const read = (name) => {
    const e = entries.get(name);
    if (!e) return null;
    const start = e.lho + 30 + b.readUInt16LE(e.lho + 26) + b.readUInt16LE(e.lho + 28);
    const raw = b.subarray(start, start + e.csize);
    const data = e.method === 0 ? raw : e.method === 8 ? zlib.inflateRawSync(raw) : null;
    if (data === null) throw new Error(`unsupported compression method ${e.method} for ${name}`);
    return data.toString('utf8');
  };
  return { names: new Set(entries.keys()), read };
}

function main() {
  const outdir = process.argv[2] || '.';
  const tz = findTrace(outdir);
  if (!tz) {
    console.log(`FAIL: no *-trace.zip found under ${outdir}`);
    console.log('  - was enableTrace=true, on a functional run (not load / multi-user)?');
    console.log('  - did the browser session actually start against the Grid?');
    return 1;
  }
  console.log(`trace bundle: ${tz} (${fs.statSync(tz).size} bytes)`);
  let fails = 0;
  const check = (cond, msg) => { console.log((cond ? '  ok  ' : ' FAIL ') + msg); if (!cond) fails++; };

  const { names, read } = readZip(tz);

  check(names.has('metadata.json'), 'metadata.json present');
  let meta = {};
  if (names.has('metadata.json')) {
    try { meta = JSON.parse(read('metadata.json')); check(Boolean(meta.browser), 'metadata names the browser'); }
    catch (e) { check(false, `metadata.json parses (${e.message})`); }
  }

  check(names.has('network.har'), 'network.har present');
  if (names.has('network.har')) {
    try {
      const log = JSON.parse(read('network.har')).log || {};
      check(log.version === '1.2', 'network.har is HAR 1.2');
      const entries = log.entries || [];
      check(entries.length >= 1, `HAR has >= 1 request (${entries.length})`);
    } catch (e) { check(false, `network.har parses (${e.message})`); }
  }

  check(names.has('console.ndjson'), 'console.ndjson present');
  check(names.has('events.ndjson'), 'events.ndjson present');
  const ev = read('events.ndjson') || '';
  check(ev.includes('"type":"lifecycle"'), 'events.ndjson has lifecycle markers');
  check(ev.includes('"type":"action"'), 'events.ndjson has >= 1 per-action event (click/hover/... hook)');

  check([...names].some((n) => n.startsWith('snapshots/')) || names.has('snapshots.ndjson'), 'snapshots present');

  if (meta.recMetrics) check(names.has('performance.json') && names.has('memory.json'), 'performance.json + memory.json present (metrics)');
  if (meta.recCpu) check(names.has('cpu.cpuprofile'), 'cpu.cpuprofile present');
  if (meta.recCoverage) check(names.has('coverage.json'), 'coverage.json present');
  if (meta.recVideo) check(names.has('video.mp4') || [...names].some((n) => n.startsWith('frames/')), 'video.mp4 (or frames/ fallback) present');

  check('truncatedBodies' in meta, 'metadata carries body-cap counters');

  console.log();
  console.log(`bundle entries: ${[...names].sort().join(', ')}`);
  console.log();
  if (fails === 0) {
    console.log('TRACE E2E VALIDATION PASSED - the recorder pipeline works against a real browser.');
    return 0;
  }
  console.log(`${fails} CHECK(S) FAILED`);
  return 1;
}

try { process.exitCode = main(); } catch (e) { console.log(`FAIL: ${e.message}`); process.exitCode = 1; }
