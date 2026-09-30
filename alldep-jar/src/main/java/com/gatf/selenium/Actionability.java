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
package com.gatf.selenium;

import java.util.Locale;
import java.util.function.Function;

/**
 * M1 auto-wait: decides whether an element is ready for an action, and polls until it is.
 * <p>
 * {@link #PROBE_JS} runs in the page through {@code executeAsyncScript(PROBE_JS, element, mode)} and calls back
 * with {@code null} when the element is actionable, or with the first unmet condition, for example
 * {@code "covered by <div.modal>"}. {@link #await} is the polling loop. It has no Selenium dependency: the caller
 * passes the probe as a function, so the loop can be tested with a fake probe and a fake clock.
 * <p>
 * Checks per mode (in this order: attached, visible, enabled, editable, stable, receives events):
 * <pre>
 *   click, dblclick, hover, drag  attached visible [enabled: not hover/drag] stable hit
 *   type                          attached visible enabled editable (a readonly text input/textarea fails;
 *                                 other elements pass, so keys can go to widgets, checkboxes, range inputs)
 *   clear                         attached visible enabled editable (strict: text input, textarea, select,
 *                                 contenteditable)
 *   input (randomize, chord)      attached visible enabled
 *   select                        attached visible enabled
 *   upload, submit                attached enabled
 * </pre>
 * {@code <option>} targets only need to be attached and enabled, because browsers report no box for them.
 */
public final class Actionability {

    private Actionability() {}

    /** Probe result meaning the element left the DOM. The caller should search for it again. */
    public static final String DETACHED = "not attached to the DOM";

    public static final long DEFAULT_TIMEOUT_MS = 5000L;

    /** Sleep before each re-probe: fast at first, then 500 ms. */
    static final long[] BACKOFF_MS = { 0, 20, 50, 100, 100, 250, 500 };

    /** arguments: [0] element, [1] mode, [last] async callback. Callback value: null = actionable, else the reason. */
    public static final String PROBE_JS = """
        var el = arguments[0], mode = String(arguments[1] || 'click'), done = arguments[arguments.length - 1];
        var HIT = /^(click|dblclick|hover|drag)$/.test(mode);
        var VIS = !/^(upload|submit)$/.test(mode);
        var ENA = !/^(hover|drag)$/.test(mode);
        var EDIT = mode === 'type' || mode === 'clear', STRICT = mode === 'clear';
        function desc(n) {
          if (!n || !n.tagName) return String(n);
          var s = '<' + String(n.tagName).toLowerCase();
          if (n.id) s += '#' + n.id;
          var c = typeof n.className === 'string' ? n.className.trim().split(/\\s+/).filter(Boolean).slice(0, 2) : [];
          if (c.length) s += '.' + c.join('.');
          return s + '>';
        }
        function attached(n) {
          if (!n) return false;
          if (typeof n.isConnected === 'boolean') return n.isConnected;
          return !!(n.ownerDocument && n.ownerDocument.documentElement && n.ownerDocument.documentElement.contains(n));
        }
        function visible(n) {
          var cs = window.getComputedStyle(n);
          if (!cs || cs.display === 'none' || cs.visibility === 'hidden' || cs.visibility === 'collapse') return false;
          var r = n.getBoundingClientRect();
          return r.width > 0 && r.height > 0;
        }
        function disabled(n) {
          if (n.getAttribute && n.getAttribute('aria-disabled') === 'true') return true;
          if (/^(BUTTON|INPUT|SELECT|TEXTAREA|OPTION|OPTGROUP|FIELDSET)$/.test(String(n.tagName).toUpperCase())) {
            try { if (n.matches && n.matches(':disabled')) return true; } catch (e) {}
            return n.disabled === true;
          }
          return false;
        }
        function editable(n) {
          if (n.isContentEditable) return true;
          var t = String(n.tagName).toUpperCase();
          if (t === 'SELECT') return true;
          if (t === 'TEXTAREA') return !n.readOnly;
          if (t === 'INPUT') {
            var ty = String(n.type || 'text').toLowerCase();
            if (/^(button|submit|reset|image|checkbox|radio|file|hidden|range|color)$/.test(ty)) return !STRICT;
            return !n.readOnly;
          }
          return !STRICT;
        }
        function box(n) { var r = n.getBoundingClientRect(); return [r.left, r.top, r.width, r.height]; }
        function frame(cb) {
          // M11: the page's real timers even under `clock install` (a paused fake clock would never fire them)
          var C = window.__gatfClock && window.__gatfClock.real;
          var ST = C ? C.setTimeout : setTimeout, CT = C ? C.clearTimeout : clearTimeout, RAF = C ? C.requestAnimationFrame : window.requestAnimationFrame;
          var fired = false;
          var t = ST.call(window, function () { if (!fired) { fired = true; cb(); } }, 100);
          if (RAF) RAF.call(window, function () { if (!fired) { fired = true; CT.call(window, t); cb(); } });
        }
        function isTarget(h) {
          for (var n = h; n; n = n.parentNode || n.host) {
            if (n === el) return true;
            if (String(n.tagName).toUpperCase() === 'LABEL' && n.control === el) return true;
          }
          return false;
        }
        function closedHost(h) {
          // h is a shadow host above el (a closed shadow root hides el from elementFromPoint)
          for (var n = el, crossed = false; n; ) {
            if (n === h) return crossed;
            if (n.parentNode) n = n.parentNode; else { n = n.host; crossed = true; }
          }
          return false;
        }
        function hit(b) {
          var x = b[0] + b[2] / 2, y = b[1] + b[3] / 2;
          if (x < 0 || y < 0 || x >= window.innerWidth || y >= window.innerHeight) return 'outside of the viewport';
          var h = document.elementFromPoint(x, y);
          while (h && h.shadowRoot && h.shadowRoot.elementFromPoint) {
            var d = h.shadowRoot.elementFromPoint(x, y);
            if (!d || d === h) break;
            h = d;
          }
          if (!h) return 'not hit at its center point';
          if (isTarget(h) || closedHost(h)) return null;
          return 'covered by ' + desc(h);
        }
        try {
          if (!attached(el)) return done('not attached to the DOM');
          var opt = /^(OPTION|OPTGROUP)$/.test(String(el.tagName).toUpperCase());
          if (VIS && !opt && !visible(el)) return done('not visible');
          if (ENA && disabled(el)) return done('disabled');
          if (EDIT && !editable(el)) return done('not editable');
          if (!HIT || opt) return done(null);
          var b0 = box(el);
          if (hit(b0) !== null) {   // off-screen, or clipped by a scrolling ancestor: scroll it to the centre
            try { el.scrollIntoView({ block: 'center', inline: 'center' }); } catch (e) { el.scrollIntoView(); }
            b0 = box(el);
          }
          frame(function () { frame(function () {
            try {
              if (!attached(el)) return done('not attached to the DOM');
              var b1 = box(el);
              for (var i = 0; i < 4; i++) if (Math.abs(b1[i] - b0[i]) > 0.5) return done('not stable (moving or animating)');
              done(hit(b1));
            } catch (e) { done(null); }
          }); });
        } catch (e) { done(null); }
        """;

    /** Mode for a type-family step: a {@code file://} value goes to a file input, so it is probed like upload. */
    public static String modeFor(String action, String value) {
        return value != null && value.startsWith("file://") ? "upload" : modeFor(action);
    }

    /** Maps a seleasy/elementAction verb to a probe mode. Unknown verbs map to {@code click}. */
    public static String modeFor(String action) {
        String a = action == null ? "" : action.trim().toLowerCase(Locale.ROOT);
        if (a.matches("^(type|sendkeys)(bl|ch|bk|cl|fo)?$")) return "type";
        if (a.equals("clear")) return "clear";
        if (a.matches("^(chord|randomize)(bl|ch|bk|cl|fo)?$")) return "input";
        if (a.equals("doubleclick") || a.equals("dblclick")) return "dblclick";
        if (a.equals("hover")) return "hover";
        if (a.equals("hoverandclick") || a.equals("hoverclick")) return "click";
        if (a.equals("drag") || a.startsWith("draganddrop")) return "drag";
        if (a.equals("select")) return "select";
        if (a.equals("upload")) return "upload";
        if (a.equals("submit")) return "submit";
        return "click";
    }

    /** Time source and sleeper, swappable in tests. */
    public interface Clock {
        long now();
        void sleep(long ms) throws InterruptedException;
    }

    public static final Clock SYSTEM_CLOCK = new Clock() {
        public long now() { return System.currentTimeMillis(); }
        public void sleep(long ms) throws InterruptedException { if (ms > 0) Thread.sleep(ms); }
    };

    /** Thrown when an element never became actionable, or left the DOM ({@link #isDetached()}). */
    public static final class NotActionableException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final String mode, reason;
        private final long waitedMs;
        public NotActionableException(String mode, String reason, long waitedMs, String locator) {
            super("Element" + (locator == null || locator.isEmpty() ? "" : " " + locator) + " not actionable for "
                    + mode + " after " + waitedMs + " ms: " + reason);
            this.mode = mode;
            this.reason = reason;
            this.waitedMs = waitedMs;
        }
        public String getMode() { return mode; }
        public String getReason() { return reason; }
        public long getWaitedMs() { return waitedMs; }
        public boolean isDetached() { return DETACHED.equals(reason); }
        /** Same failure with the element's locator named. */
        public NotActionableException withLocator(String locator) {
            NotActionableException e = new NotActionableException(mode, reason, waitedMs, locator);
            e.setStackTrace(getStackTrace());
            return e;
        }
    }

    /**
     * Probes until the element is actionable. Returns the ms spent waiting (0 when the first probe passed).
     * A detached element fails at once so the caller can search again. A probe that returns null counts as
     * actionable, so a driver that cannot run the probe falls back to the legacy behaviour.
     *
     * The budget bounds the polling, not a single probe call: on a page whose script thread is hung, one probe
     * can block until the driver's script timeout.
     *
     * @param probe mode to reason (null = actionable)
     * @throws NotActionableException with the last unmet condition once {@code timeoutMs} has passed
     */
    public static long await(String mode, long timeoutMs, Function<String, String> probe, Clock clock) {
        long timeout = timeoutMs > 0 ? timeoutMs : DEFAULT_TIMEOUT_MS;
        long start = clock.now();
        for (int attempt = 0; ; attempt++) {
            String reason = probe.apply(mode);
            long waited = clock.now() - start;
            if (reason == null || reason.isEmpty()) return attempt == 0 ? 0 : waited;
            if (DETACHED.equals(reason) || waited >= timeout) {
                throw new NotActionableException(mode, reason, waited, null);
            }
            long pause = BACKOFF_MS[Math.min(attempt, BACKOFF_MS.length - 1)];
            pause = Math.min(pause, Math.max(0, timeout - waited));
            try {
                clock.sleep(pause);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new NotActionableException(mode, reason + " (interrupted)", clock.now() - start, null);
            }
        }
    }
}
