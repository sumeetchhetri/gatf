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
// M11 clock control: runs FakeClock.JS (the text block, read out of the Java source) against a minimal window
// (vm context, real Date / timers underneath), as the page would. No browser, no dependencies.
// Run: node tools/smoke/test-clock.mjs
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import vm from 'node:vm';

const here = dirname(fileURLToPath(import.meta.url));
const javaSrc = readFileSync(resolve(here, '../../alldep-jar/src/main/java/com/gatf/selenium/emulation/FakeClock.java'), 'utf8');

function textBlock(src, name) {
  const m = src.match(new RegExp(name + '\\s*=\\s*"""\\r?\\n([\\s\\S]*?)"""'));
  if (!m) throw new Error('text block ' + name + ' not found');
  const lines = m[1].replace(/\r/g, '').split('\n');
  const closing = lines[lines.length - 1];
  const ind = Math.min(closing.length, ...lines.filter((l) => l.trim()).map((l) => l.match(/^ */)[0].length));
  return lines.map((l) => l.slice(ind)).join('\n').replace(/\\\\/g, '\\');
}
const CLOCK = textBlock(javaSrc, 'JS');

let pass = 0, fail = 0;
const ok = (name, cond) => { if (cond) { pass++; console.log('  ok   ' + name); } else { fail++; console.log('  FAIL ' + name); } };

/** A fresh "document": its own window object, sharing one sessionStorage (the tab). */
function page(storage) {
  const w = { Date, setTimeout, clearTimeout, setInterval, clearInterval, performance: { now: () => 1000 }, Promise,
    requestAnimationFrame: (cb) => setTimeout(() => cb(0), 16), cancelAnimationFrame: clearTimeout,
    sessionStorage: storage, Array, Function, Object, Math, Number, String, Error, isNaN };
  w.window = w;
  w.top = w;
  w.eval = (s) => vm.runInContext(s, ctx);
  const ctx = vm.createContext(w);
  return { w, run: (src) => vm.runInContext(src, ctx) };
}
function store() {
  const m = {};
  return { getItem: (k) => (k in m ? m[k] : null), setItem: (k, v) => { m[k] = String(v); }, removeItem: (k) => { delete m[k]; } };
}

const T0 = Date.UTC(2026, 0, 1, 9, 0, 0);
function javaMethodPreload() {   // FakeClock.preload(epoch, gen), rebuilt from the Java string pieces
  return (epoch, gen) => CLOCK + "(function () { var s = null, v = " + epoch + "; try { s = window.sessionStorage.getItem('__gatfClockNow'); } catch (e) {}"
    + " if (s) { var i = s.lastIndexOf('|'); if (i > 0 && s.substring(0, i) === '" + gen + "' && !isNaN(Number(s.substring(i + 1)))) v = Number(s.substring(i + 1)); }"
    + " window.__gatfClock.install(v, '" + gen + "'); })();";
}
const preload = javaMethodPreload();
const pages = [];
const tab = store();
const p = page(tab);
pages.push(p);
p.run(CLOCK);
p.run('window.__gatfClock.install(' + T0 + ', "g1")');
ok('Date is frozen at the install time', p.run('Date.now()') === T0 && p.run('new Date().getTime()') === T0);
ok('new Date(args) still works', p.run('new Date(2020, 1, 2).getFullYear()') === 2020);
ok('instanceof Date holds', p.run('new Date() instanceof Date') === true);
ok('Date() as a function gives a string', typeof p.run('Date()') === 'string');

p.run('window.log = []; setTimeout(function (a) { log.push("t500:" + a); }, 500, "x"); setTimeout(function () { log.push("t100"); }, 100);'
  + 'var iv = setInterval(function () { log.push("iv@" + (Date.now() - ' + T0 + ')); }, 300);'
  + 'var c = setTimeout(function () { log.push("cleared"); }, 200); clearTimeout(c);'
  + 'requestAnimationFrame(function (ts) { log.push("raf"); });');
ok('nothing runs until time moves', p.run('log.length') === 0);
const ran = p.run('window.__gatfClock.tick(650)');
ok('fastforward runs due timers in time order', JSON.stringify(p.run('log')) === JSON.stringify(['raf', 't100', 'iv@300', 't500:x', 'iv@600']));
ok('fastforward reports how many ran', ran === 5);
ok('time advanced by the duration', p.run('Date.now()') === T0 + 650);
ok('performance.now advances with it', p.run('performance.now()') === 1000 + 650);
p.run('setTimeout(function () { setTimeout(function () { log.push("nested"); }, 10); }, 10);');
p.run('window.__gatfClock.tick(25)');
ok('a timer scheduled inside a timer runs when it falls due', p.run('log').includes('nested'));
p.run('clearInterval(iv)');
p.run('window.__gatfClock.setSystemTime(' + (T0 + 86400000) + ')');
ok('set jumps the wall clock', p.run('Date.now()') === T0 + 86400000);
const before = p.run('log.length');
p.run('window.__gatfClock.tick(10000)');
ok('set fires nothing; a cleared interval stays cleared', p.run('log.length') === before);
p.run('setTimeout(function () { throw new Error("boom"); }, 1); setTimeout(function () { log.push("after"); }, 2);');
p.run('window.__gatfClock.tick(5)');
ok('a throwing timer does not stop the others', p.run('log').includes('after') && p.run('window.__gatfClock.errors()')[0] === 'boom');
let runaway = false;
try { p.run('(function f() { setTimeout(f, 0); })(); window.__gatfClock.tick(10)'); } catch (e) { runaway = /runaway/.test(String(e)); }
ok('a runaway 0 ms timer is stopped with a message', runaway);

// (the runaway interval above is still registered: uninstall drops it)
p.run('window.__gatfClock.uninstall()');
ok('uninstall restores the real clock', p.run('Date') === Date && p.run('typeof window.__gatfClock') === 'undefined' && tab.getItem('__gatfClockNow') === null);

// ---- navigation: the preload boots the next document with this installation's time, not a stale one ----
const tab2 = store();
const a = page(tab2);
pages.push(a);
a.run(preload(T0, 'g2'));
a.run('window.__gatfClock.tick(5000)');
const carried = a.run('Date.now()');
const b = page(tab2);
pages.push(b);
b.run(preload(T0, 'g2'));
ok('the time carries across a same-tab navigation', b.run('Date.now()') === carried && carried === T0 + 5000);
const c2 = page(tab2);
pages.push(c2);
c2.run(preload(T0 + 99, 'g3'));   // a later installation ignores the earlier one's carried time
ok('a stale carried time from another installation is ignored', c2.run('Date.now()') === T0 + 99);

// ---- Date identity ----
ok('subclasses of Date keep their prototype', b.run('class X extends Date { tag() { return 1; } } var x = new X(); x instanceof X && x.tag() === 1 && x.getTime() === Date.now()'));
// (each page here shares node's Date, so Date.prototype.constructor is checked on a page of its own)
const e = page(store());
e.run(CLOCK + 'window.__gatfClock.install(' + T0 + ', "g9")');
ok('Date looks like Date', e.run('Date.name') === 'Date' && e.run('Date.length') === 7 && e.run('new (new Date().constructor)().getTime() === Date.now()'));
e.run('window.__gatfClock.uninstall()');
ok('the page\'s real timers stay reachable for injected scripts', typeof b.run('window.__gatfClock.real.setTimeout') === 'function');

// ---- async fastforward: promise reactions run between timers ----
b.run('window.polls = 0; (async function () { for (var i = 0; i < 10; i++) { await new Promise(function (r) { setTimeout(r, 1000); }); polls++; } })();');
b.run('window.__gatfClock.tickAsync(5000, function (n) { window.__done = n; })');
await new Promise((r) => setTimeout(r, 300));
const ranAsync = b.run('window.__done');
ok('async fastforward lets await-based loops advance per timer', b.run('polls') === 5 && ranAsync === 5);

// ---- paused clock still runs 0 ms deferrals; resume lets time flow ----
b.run('window.ready = false; setTimeout(function () { ready = true; }, 0); window.later = false; setTimeout(function () { later = true; }, 60000);');
await new Promise((r) => setTimeout(r, 80));
ok('a paused clock still runs due 0 ms timers (ready handlers)', b.run('ready') === true && b.run('later') === false);
const before2 = b.run('Date.now()');
b.run('window.__gatfClock.resume()');
await new Promise((r) => setTimeout(r, 120));
b.run('window.__gatfClock.pause()');
const flowed = b.run('Date.now()') - before2;
ok('resume lets time follow the real clock; pause freezes it again', flowed >= 60 && flowed < 2000);
const frozen = b.run('Date.now()');
await new Promise((r) => setTimeout(r, 60));
ok('paused again', b.run('Date.now()') === frozen);

// ---- clearTimeout of a timer made before install ----
const d = page(store());
pages.push(d);
d.run('window.hit = false; window.pre = setTimeout(function () { hit = true; }, 30);');
d.run(CLOCK + 'window.__gatfClock.install(' + T0 + ', "g4"); clearTimeout(window.pre);');
await new Promise((r) => setTimeout(r, 60));
ok('clearTimeout of a pre-install timer cancels the real timer', d.run('hit') === false);

// ---- long ranges are fine (an animation loop over 30 min), frames never write the carried time ----
const f = page(store());
pages.push(f);
f.run(CLOCK + 'window.__gatfClock.install(' + T0 + ', "g5"); (function loop() { requestAnimationFrame(loop); })();');
ok('fastforward 30m with an animation loop is not a runaway', f.run('window.__gatfClock.tick(1800000)') > 100000);
const tabF = store();
const top = page(tabF);
pages.push(top);
top.run(CLOCK + 'window.__gatfClock.install(' + T0 + ', "g6"); window.__gatfClock.tick(600000);');
const child = page(tabF);   // a same-origin iframe of that tab: its own clock, never writes the carried time
pages.push(child);
child.w.top = {};
child.run(CLOCK + 'window.__gatfClock.install(' + T0 + ', "g6");');
await new Promise((r) => setTimeout(r, 60));
ok('an iframe clock never overwrites the carried time', tabF.getItem('__gatfClockNow') === 'g6|' + (T0 + 600000));

for (const pg of pages) { try { pg.run('window.__gatfClock && window.__gatfClock.uninstall()'); } catch (e) {} }
console.log(pass + ' passed, ' + fail + ' failed');
process.exit(fail ? 1 : 0);
