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
// Q9 per-navigation web vitals: runs the injected scripts (WebVitals.PRELOAD_JS / DRAIN_JS / ASYNC_JS, read straight
// out of the Java string concatenations) against a mocked window + Performance API. No browser, no dependencies.
// Run: node tools/smoke/test-web-vitals.mjs
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const javaSrc = readFileSync(resolve(here, '../../alldep-jar/src/main/java/com/gatf/selenium/trace/WebVitals.java'), 'utf8');

/** The value of `NAME = "..." + "..." ;` (Java escapes used here are JSON-compatible). */
function javaString(name) {
  const m = javaSrc.match(new RegExp('\\b' + name + '\\s*=\\s*((?:\\s*\\+?\\s*"(?:[^"\\\\]|\\\\.)*")+)\\s*;'));
  if (!m) throw new Error('constant ' + name + ' not found');
  return [...m[1].matchAll(/"((?:[^"\\]|\\.)*)"/g)].map((x) => JSON.parse('"' + x[1] + '"')).join('');
}
const PRELOAD = javaString('PRELOAD_JS');
const DRAIN = javaString('DRAIN_JS');
const ASYNC = javaString('ASYNC_JS');

let pass = 0, fail = 0;
function ok(name, cond) {
  if (cond) { pass++; console.log(`  ok   ${name}`); } else { fail++; console.log(`  FAIL ${name}`); }
}

// ---- mock browser: one "tab" (shared sessionStorage) that loads documents -------------------------------
function tab() {
  const store = {};
  const sessionStorage = {
    getItem: (k) => (k in store ? store[k] : null),
    setItem: (k, v) => { store[k] = String(v); },
    removeItem: (k) => { delete store[k]; },
  };
  return { store, sessionStorage };
}
function doc(t, url, { lcp = [], shifts = [], ttfb = 50, fcp = 300, sink } = {}) {
  const listeners = {};
  const observers = [];
  const w = {
    location: { href: url },
    document: { visibilityState: 'visible' },
    sessionStorage: t.sessionStorage,
    addEventListener: (ev, fn) => { (listeners[ev] = listeners[ev] || []).push(fn); },
    performance: {
      getEntriesByType: (ty) => ty === 'navigation' ? [{ responseStart: ttfb, domContentLoadedEventEnd: 400, loadEventEnd: 600 }]
        : ty === 'paint' ? [{ name: 'first-contentful-paint', startTime: fcp }] : [],
    },
  };
  function PerformanceObserver(cb) { this.cb = cb; observers.push(this); }
  PerformanceObserver.prototype.observe = function (o) { this.type = o.type; };
  PerformanceObserver.supportedEntryTypes = ['largest-contentful-paint', 'layout-shift'];
  w.PerformanceObserver = PerformanceObserver;
  if (sink) w.__gatfVitalsSink = sink;
  const deliver = () => {
    for (const o of observers) {
      if (o.type === 'largest-contentful-paint' && lcp.length) o.cb({ getEntries: () => lcp.map((s) => ({ startTime: s })) });
      if (o.type === 'layout-shift' && shifts.length) o.cb({ getEntries: () => shifts });
    }
  };
  const run = (code, args = []) =>
    new Function('window', 'location', 'document', 'performance', 'PerformanceObserver', 'sessionStorage', 'addEventListener',
      'setTimeout', 'arguments_', code.replace(/arguments/g, 'arguments_'))(
      w, w.location, w.document, w.performance, PerformanceObserver, t.sessionStorage, w.addEventListener,
      (f) => f(), args);
  const fire = (ev) => (listeners[ev] || []).forEach((f) => f());
  return { w, run, deliver, fire, observers };
}

// ---- same-tab navigation, sessionStorage buffer (BiDi path) ---------------------------------------------
{
  const t = tab();
  const a = doc(t, 'https://app/a', { lcp: [100, 900], shifts: [{ value: 0.1 }, { value: 0.5, hadRecentInput: true }, { value: 0.05 }] });
  a.run(PRELOAD);
  a.run(PRELOAD);                                  // idempotent: current-doc injection + preload
  ok('one observer pair per document', a.observers.length === 2);
  a.deliver();
  a.w.document.visibilityState = 'hidden';
  a.fire('visibilitychange');
  a.fire('pagehide');                              // second flush of the same document replaces the first
  const b = doc(t, 'https://app/b', { lcp: [2400], ttfb: 90 });
  b.run(PRELOAD);
  b.deliver();
  b.fire('pagehide');
  const buf = JSON.parse(t.store.__gatf_vitals);
  ok('two pages buffered (deduped by document id)', buf.length === 2);
  ok('page a: url + lcp (last entry) + cls (excludes recent input)',
    buf[0].url === 'https://app/a' && buf[0].lcp === 900 && Math.abs(buf[0].cls - 0.15) < 1e-9);
  ok('page a: nav + paint timings', buf[0].ttfb === 50 && buf[0].fcp === 300 && buf[0].domContentLoaded === 400 && buf[0].load === 600);
  ok('page b: lcp, no shifts -> cls 0', buf[1].url === 'https://app/b' && buf[1].lcp === 2400 && buf[1].cls === 0 && buf[1].ttfb === 90);
  ok('entries carry id + ts', typeof buf[0].id === 'string' && buf[0].id !== buf[1].id && typeof buf[1].ts === 'number');

  const c = doc(t, 'https://app/c', { lcp: [700] });
  c.run(PRELOAD);
  c.deliver();
  const drained = c.run(DRAIN);
  ok('drain returns the buffer and clears it', JSON.parse(drained).length === 2 && t.store.__gatf_vitals === undefined);
  ok('drain of an empty buffer is null', c.run(DRAIN) === null);

  let out = null;
  c.run(ASYNC, [(s) => { out = JSON.parse(s); }]);
  ok('final read carries url + the preloaded document id', out && out.url === 'https://app/c' && out.id === c.w.__gatfVitalsId);
}

// ---- CDP binding sink: nothing touches sessionStorage -----------------------------------------------------
{
  const t = tab();
  const got = [];
  const a = doc(t, 'https://x/login', { lcp: [1500], sink: (s) => got.push(JSON.parse(s)) });
  a.run(PRELOAD);
  a.deliver();
  a.fire('pagehide');
  ok('binding receives the entry', got.length === 1 && got[0].url === 'https://x/login' && got[0].lcp === 1500);
  ok('binding path leaves sessionStorage untouched', Object.keys(t.store).length === 0);
}

// ---- no PerformanceObserver support: timings still reported, no throw -------------------------------------
{
  const t = tab();
  const a = doc(t, 'https://old/');
  a.w.PerformanceObserver.supportedEntryTypes = [];
  a.run(PRELOAD);
  a.fire('pagehide');
  const e = JSON.parse(t.store.__gatf_vitals)[0];
  ok('unsupported observers: fcp/ttfb only', e.fcp === 300 && e.lcp === undefined && e.cls === undefined);
}

console.log(`\n${pass} passed, ${fail} failed`);
process.exit(fail ? 1 : 0);
