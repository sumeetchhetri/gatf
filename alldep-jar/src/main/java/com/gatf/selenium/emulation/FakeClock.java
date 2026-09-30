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
package com.gatf.selenium.emulation;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * M11: {@code clock install [time] | clock set <time> | clock fastforward <duration> | clock uninstall}: a fake clock
 * injected into the page, so time-dependent UI is tested deterministically.
 * <ul>
 *  <li>{@code install}: {@code Date}, {@code performance.now}, {@code setTimeout}/{@code setInterval} (+ clear) and
 *      {@code requestAnimationFrame} (+ cancel) are replaced. Time stands still until the script moves it; the start is
 *      the given time, else the real current time.</li>
 *  <li>{@code set}: jumps the wall clock ({@code Date}) without firing timers.</li>
 *  <li>{@code fastforward}: advances time, running every timer that falls due, in order (intervals repeat, frames
 *      fire every 16 ms; promise reactions run between timers), and returns how many ran.</li>
 *  <li>{@code pause} / {@code resume}: frozen time (the default after install; timers already due still run) or time
 *      that follows the real clock.</li>
 *  <li>The clock is re-installed on every new document (CDP {@code Page.addScriptToEvaluateOnNewDocument} / BiDi
 *      {@code script.addPreloadScript}) and carries its time across same-origin navigations (sessionStorage).</li>
 * </ul>
 * Pure: {@link #JS} runs in the page; the parsers are plain Java.
 */
public final class FakeClock {

    /**
     * Defines {@code window.__gatfClock} (once per document): install(epochMs, gen), setSystemTime(ms), tick(ms),
     * tickAsync(ms, done), pause(), resume(), now(), uninstall(), and {@code real} (the page's own timer functions, for
     * GATF's injected scripts: call them with {@code .call(window, …)}).
     */
    public static final String JS = """
            (function () {
              if (window.__gatfClock) return;
              var W = window, KEY = '__gatfClockNow';
              var P = W.performance, perfOwn = !!P && Object.prototype.hasOwnProperty.call(P, 'now');
              var R = { Date: W.Date, setTimeout: W.setTimeout, clearTimeout: W.clearTimeout, setInterval: W.setInterval,
                        clearInterval: W.clearInterval, requestAnimationFrame: W.requestAnimationFrame,
                        cancelAnimationFrame: W.cancelAnimationFrame, perfNow: P && P.now ? P.now : null };
              var t = 0, offset = 0, perf0 = 0, seq = 1000000000, timers = {}, installed = false, errors = [], gen = '';
              var flowing = false, lastReal = 0, pump = null, busy = false, carry = 0, aborted = false;
              function realNow() { return R.Date.now(); }
              var isTop = true;
              try { isTop = W.top === W; } catch (e) { isTop = false; }
              // only the top document carries the time across navigations (frames have their own, unmoved clocks)
              function save() { if (!isTop) return; try { W.sessionStorage.setItem(KEY, gen + '|' + (offset + t)); } catch (e) {} }
              function FakeDate() {
                if (!new.target) return new R.Date(offset + t).toString();
                var a = arguments.length ? Array.prototype.slice.call(arguments) : [offset + t];
                return Reflect.construct(R.Date, a, new.target);
              }
              FakeDate.prototype = R.Date.prototype;
              FakeDate.now = function () { return offset + t; };
              FakeDate.parse = R.Date.parse;
              FakeDate.UTC = R.Date.UTC;
              try { Object.defineProperty(FakeDate, 'name', { value: 'Date' }); Object.defineProperty(FakeDate, 'length', { value: 7 }); } catch (e) {}
              function add(fn, delay, args, repeat, frame) {
                var d = Math.max(0, Number(delay) || 0), id = ++seq;
                timers[id] = { id: id, fn: fn, args: args, at: t + d, every: repeat ? Math.max(1, d) : 0, frame: !!frame };
                return id;
              }
              function clear(id) {
                if (Object.prototype.hasOwnProperty.call(timers, id)) delete timers[id];
                else if (id != null) { try { R.clearTimeout.call(W, id); } catch (e) {} }   // a timer made before install
              }
              var fake = {
                setTimeout: function (fn, delay) { return add(fn, delay, Array.prototype.slice.call(arguments, 2), false); },
                setInterval: function (fn, delay) { return add(fn, delay, Array.prototype.slice.call(arguments, 2), true); },
                clearTimeout: clear, clearInterval: clear,
                requestAnimationFrame: function (cb) {
                  var next = (Math.floor(t / 16) + 1) * 16;
                  return add(function () { cb(perf0 + t); }, next - t, [], false, true);
                },
                cancelAnimationFrame: clear
              };
              // earliest due first; at the same time timers before animation frames, then creation order
              function due(limit) {
                var best = null;
                for (var k in timers) {
                  var x = timers[k];
                  if (x.at > limit) continue;
                  if (best === null || x.at < best.at || (x.at === best.at && (x.frame !== best.frame ? !x.frame : x.id < best.id))) best = x;
                }
                return best;
              }
              function runOne(x) {
                t = x.at;
                if (x.every) x.at += x.every; else delete timers[x.id];
                try {
                  if (typeof x.fn === 'function') x.fn.apply(W, x.args); else (0, W.eval)(String(x.fn));
                } catch (e) { errors.push(String(e && e.message || e)); }
              }
              // runaway = many runs without time moving (a 0 ms timer that re-arms itself); long ranges are fine
              var RUNAWAY = 10000;
              function tick(ms, persist, budget) {
                var target = t + Math.max(0, Number(ms) || 0), ran = 0, still = 0, last = t, x;
                try {
                  while ((x = due(target)) !== null) {
                    if (budget && ran >= budget) return ran;
                    still = x.at === last ? still + 1 : 0;
                    last = x.at;
                    if (still > RUNAWAY) throw new Error('clock fastforward: a timer re-armed itself more than ' + RUNAWAY + ' times without time moving (a runaway 0 ms timer?)');
                    ran++;
                    runOne(x);
                  }
                  t = target;
                } finally {
                  if (persist) save();
                }
                return ran;
              }
              // frozen: timers already due (0 ms deferrals, e.g. ready handlers) still run; flowing: time follows the real clock
              function onPump() {
                if (!installed) return;
                var r = realNow(), d = flowing ? Math.max(0, r - lastReal) + carry : 0;
                lastReal = r;
                if (busy) return;
                // at most 1000 runs per pump, so a self-re-arming 0 ms timer cannot peg the page; state is saved by explicit steps.
                // Time the budget did not reach is carried into the next pump, so flowing time never falls behind.
                var before = t;
                try { tick(d, flowing, 1000); } catch (e) { errors.push(String(e && e.message || e)); }
                carry = flowing ? Math.max(0, before + d - t) : 0;
              }
              function yieldThen(fn) {
                if (typeof W.MessageChannel === 'function') {
                  var ch = new W.MessageChannel();
                  ch.port1.onmessage = function () { ch.port1.close(); fn(); };
                  ch.port2.postMessage(0);
                } else {
                  R.setTimeout.call(W, fn, 0);
                }
              }
              W.__gatfClock = {
                real: R,
                install: function (epochMs, g) {
                  if (!installed) {
                    perf0 = R.perfNow ? R.perfNow.call(P) : 0;
                    W.Date = FakeDate;
                    try { R.Date.prototype.constructor = FakeDate; } catch (e) {}
                    W.setTimeout = fake.setTimeout; W.setInterval = fake.setInterval;
                    W.clearTimeout = fake.clearTimeout; W.clearInterval = fake.clearInterval;
                    if (R.requestAnimationFrame) { W.requestAnimationFrame = fake.requestAnimationFrame; W.cancelAnimationFrame = fake.cancelAnimationFrame; }
                    if (R.perfNow) P.now = function () { return perf0 + t; };
                    lastReal = realNow();
                    pump = R.setInterval.call(W, onPump, 16);
                    installed = true;
                  }
                  gen = g == null ? String(realNow()) : String(g);
                  offset = Number(epochMs) - t;
                  flowing = false;
                  save();
                  return offset + t;
                },
                setSystemTime: function (ms) { offset = Number(ms) - t; save(); return offset + t; },
                tick: function (ms) { busy = true; try { return tick(ms, true); } finally { busy = false; } },
                // as tick, but lets promise reactions (await, then) run between timers, as the event loop would
                tickAsync: function (ms, done) {
                  var target = t + Math.max(0, Number(ms) || 0), ran = 0, still = 0, last = t;
                  busy = true;
                  aborted = false;
                  function finish(v) { busy = false; save(); done(v); }
                  function step() {
                    var x;
                    if (aborted) { finish({ error: 'clock fastforward aborted' }); return; }
                    try {
                      x = due(target);
                      if (x === null) { t = target; save(); yieldThen(function () { finish(ran); }); return; }
                      still = x.at === last ? still + 1 : 0;
                      last = x.at;
                      if (still > RUNAWAY) throw new Error('clock fastforward: a timer re-armed itself more than ' + RUNAWAY + ' times without time moving (a runaway 0 ms timer?)');
                      ran++;
                      runOne(x);
                      save();   // a timer may navigate away: the next document boots at this time
                    } catch (e) { finish({ error: String(e && e.message || e) }); return; }
                    yieldThen(step);
                  }
                  step();
                },
                abort: function () { aborted = true; },
                pause: function () { onPump(); flowing = false; carry = 0; },
                resume: function () { lastReal = realNow(); flowing = true; },
                now: function () { return offset + t; },
                pending: function () { return Object.keys(timers).length; },
                errors: function () { var e = errors; errors = []; return e; },
                installed: function () { return installed; },
                uninstall: function () {
                  if (installed) {
                    R.clearInterval.call(W, pump);
                    W.Date = R.Date;
                    try { R.Date.prototype.constructor = R.Date; } catch (e) {}
                    W.setTimeout = R.setTimeout; W.setInterval = R.setInterval;
                    W.clearTimeout = R.clearTimeout; W.clearInterval = R.clearInterval;
                    if (R.requestAnimationFrame) { W.requestAnimationFrame = R.requestAnimationFrame; W.cancelAnimationFrame = R.cancelAnimationFrame; }
                    if (R.perfNow) { if (perfOwn) P.now = R.perfNow; else delete P.now; }
                  }
                  try { W.sessionStorage.removeItem(KEY); } catch (e) {}
                  timers = {}; installed = false;
                  delete W.__gatfClock;
                }
              };
            })();
            """;

    /**
     * The per-document boot script: the clock at the time this installation left in sessionStorage (same {@code gen}),
     * else at {@code epochMs}; a value from an earlier installation or another origin's stale key is ignored.
     */
    public static String preload(long epochMs, String gen) {
        String g = gen.replaceAll("[^A-Za-z0-9_-]", "");
        return JS + "(function () { var s = null, v = " + epochMs + "; try { s = window.sessionStorage.getItem('__gatfClockNow'); } catch (e) {}"
                + " if (s) { var i = s.lastIndexOf('|'); if (i > 0 && s.substring(0, i) === '" + g + "' && !isNaN(Number(s.substring(i + 1)))) v = Number(s.substring(i + 1)); }"
                + " window.__gatfClock.install(v, '" + g + "'); })();";
    }

    /** Registers {@link #preload} for every new document of the tab (CDP / BiDi). */
    public static Emulation.Call registerPreload(boolean cdp, long epochMs, String gen, String context) {
        java.util.Map<String, Object> p = new java.util.LinkedHashMap<String, Object>();
        if (cdp) {
            p.put("source", preload(epochMs, gen));
            return new Emulation.Call("Page.addScriptToEvaluateOnNewDocument", p);
        }
        p.put("functionDeclaration", "function () { " + preload(epochMs, gen) + " }");
        if (context != null) p.put("contexts", java.util.Collections.singletonList(context));
        return new Emulation.Call("script.addPreloadScript", p);
    }

    /** The id a registration returned ({@code identifier} on CDP, {@code script} on BiDi); null if none. */
    public static String preloadId(boolean cdp, java.util.Map<String, Object> reply) {
        Object id = reply == null ? null : reply.get(cdp ? "identifier" : "script");
        return id == null ? null : String.valueOf(id);
    }

    /** Removes a registration made by {@link #registerPreload}. */
    public static Emulation.Call unregisterPreload(boolean cdp, String id) {
        java.util.Map<String, Object> p = new java.util.LinkedHashMap<String, Object>();
        p.put(cdp ? "identifier" : "script", id);
        return new Emulation.Call(cdp ? "Page.removeScriptToEvaluateOnNewDocument" : "script.removePreloadScript", p);
    }

    public final String op;
    /** install / set: epoch ms (null = now for install); fastforward: duration ms. */
    public final Long value;

    private FakeClock(String op, Long value) {
        this.op = op;
        this.value = value;
    }

    /** Parses the text after {@code clock}. */
    public static FakeClock parse(String spec) {
        String s = spec == null ? "" : spec.trim();
        String[] p = s.split("\\s+", 2);
        String op = p[0].toLowerCase(Locale.ROOT);
        String arg = p.length > 1 ? unquote(p[1].trim()) : "";
        switch (op) {
            case "install":
                return new FakeClock(op, arg.isEmpty() ? null : time(arg));
            case "set":
                if (arg.isEmpty()) throw new IllegalArgumentException("clock set <time> (ISO date-time, date, or epoch ms)");
                return new FakeClock(op, time(arg));
            case "fastforward": case "ff": case "runfor":
                if (arg.isEmpty()) throw new IllegalArgumentException("clock fastforward <duration> (e.g. 1500, 30s, 5m, 1h30m, 01:30)");
                return new FakeClock("fastforward", duration(arg));
            case "uninstall": case "pause": case "resume":
                if (!arg.isEmpty()) throw new IllegalArgumentException("clock " + op + " takes no value");
                return new FakeClock(op, null);
            default:
                throw new IllegalArgumentException("clock install [time] | clock set <time> | clock fastforward <duration> | clock pause | clock resume | clock uninstall");
        }
    }

    private static String unquote(String s) {
        if (s.length() >= 2 && (s.charAt(0) == '"' || s.charAt(0) == '\'') && s.charAt(s.length() - 1) == s.charAt(0)) return s.substring(1, s.length() - 1);
        return s;
    }

    private static final Pattern UNITS = Pattern.compile("(\\d+(?:\\.\\d+)?)(ms|h|m|s|d)");

    /** {@code 1500} (ms), {@code 30s}, {@code 5m}, {@code 1h30m}, {@code 250ms}, {@code 2d}, or {@code mm:ss} / {@code hh:mm:ss}. */
    public static long duration(String v) {
        String s = v.trim().toLowerCase(Locale.ROOT).replace(" ", "");
        if (s.matches("\\d{1,15}")) return Long.parseLong(s);
        if (s.matches("\\d{1,2}(:\\d{2}){1,2}")) {
            long total = 0;
            for (String part : s.split(":")) total = total * 60 + Long.parseLong(part);
            return total * 1000;
        }
        Matcher m = UNITS.matcher(s);
        long ms = 0;
        int end = 0;
        while (m.find()) {
            if (m.start() != end) break;
            double n = Double.parseDouble(m.group(1));
            switch (m.group(2)) {
                case "ms": ms += Math.round(n); break;
                case "s": ms += Math.round(n * 1000); break;
                case "m": ms += Math.round(n * 60_000); break;
                case "h": ms += Math.round(n * 3_600_000); break;
                default: ms += Math.round(n * 86_400_000); break;
            }
            end = m.end();
        }
        if (end == 0 || end != s.length() || ms < 0) throw new IllegalArgumentException("clock: bad duration '" + v + "' (e.g. 1500, 30s, 5m, 1h30m, 01:30)");
        return ms;
    }

    /** ISO instant ({@code 2026-01-01T09:00:00Z}), local date-time / date (as UTC), or epoch ms. */
    public static long time(String v) {
        String s = v.trim();
        if (s.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:.*")) s = s.replaceFirst(" ", "T");   // 2026-01-01 09:00:00
        if (s.matches("-?\\d{9,18}")) return Long.parseLong(s);
        try {
            return Instant.parse(s).toEpochMilli();
        } catch (DateTimeParseException e) {
            // not an instant with a zone
        }
        try {
            return java.time.OffsetDateTime.parse(s).toInstant().toEpochMilli();
        } catch (DateTimeParseException e) {
            // no offset
        }
        try {
            return LocalDateTime.parse(s).toInstant(ZoneOffset.UTC).toEpochMilli();
        } catch (DateTimeParseException e) {
            // not a local date-time
        }
        try {
            return LocalDate.parse(s).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("clock: bad time '" + v + "' (ISO 2026-01-01T09:00:00Z, 2026-01-01, or epoch ms)");
        }
    }
}
