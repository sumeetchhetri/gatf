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
package com.gatf.selenium.context;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * H3 (Δ154): one run's worker browsers for {@code browserContexts: worker}. Each worker (a pool thread) keeps one
 * browser; every script it runs gets a fresh context in it, disposed when the script ends however it ends. Pure: the
 * browser is a type parameter and everything it does goes through {@link Port}, so scheduling, fallback, crash
 * recovery, leak counting and shutdown are unit-tested without one.
 *
 * <p>Protocol: the script asks {@link #acquire}; null means "launch a browser yourself", after which it hands the new
 * browser to {@link #adopt}. At script end {@link #release} keeps the browser for the worker's next script (true) or
 * leaves it to the caller to quit, as today (false). {@link #shutdown} quits everything at run end.
 *
 * <p>Fallback: a browser kind that cannot create contexts (see {@link ContextMatrix}) is logged once and is never
 * pooled again in this run: each script launches and quits its own browser, i.e. the current behaviour. Slow
 * browser I/O (open, probe, quit) runs outside the lock.
 */
public final class WorkerPool<B> {

    /** What the pool does to a browser. Implementations must not throw from {@link #quit}. */
    public interface Port<B> {
        /** The browser still answers (a crashed or closed browser does not). */
        boolean alive(B b);
        /** Ids of the non-default contexts alive in {@code b} now (leak count); may throw. */
        List<String> contexts(B b);
        /** Dispose a context left behind; may throw. */
        void dispose(B b, String context);
        void quit(B b);
    }

    /** Opens the script's fresh context in {@code b} and makes its tab current: its id, or null when refused. */
    public interface Opener<B> {
        String open(B b);
    }

    /** {@link #trace}: what a run with tracing on does under pooled contexts. */
    public enum Trace {
        /** Tracing off, or already over BiDi: nothing changes. */
        KEEP,
        /** Tracing would be CDP (bound to the default context, so it would record nothing): this run traces over BiDi. */
        BIDI,
        /** Tracing would be CDP and the session has no BiDi: this run keeps a browser per script. */
        FALLBACK
    }

    /** All browser kinds ({@link #fallbackRun}). */
    static final String ALL = "*";
    /** {@code -Dgatf.contextRecycle} default: a fresh browser every 50 scripts (0 = never). */
    public static final int DEFAULT_RECYCLE = 50;

    private static final class Slot<B> {
        final Object worker;
        final String key;
        final B browser;
        Object owner;     // the script holding it, null when idle
        int uses;
        Slot(Object worker, String key, B browser) { this.worker = worker; this.key = key; this.browser = browser; }
    }

    private final Port<B> port;
    private final int recycleAfter;
    private final Consumer<String> log;
    private final Map<Object, Slot<B>> byWorker = new LinkedHashMap<Object, Slot<B>>();
    private final Map<String, String> unsupported = new LinkedHashMap<String, String>();
    private Trace trace;
    private boolean closed;
    private int launches, reuses, retired, leaked, unpooled;

    /** @param recycleAfter quit and relaunch a worker's browser after this many scripts; 0 or less = never */
    public WorkerPool(Port<B> port, int recycleAfter, Consumer<String> log) {
        this.port = port;
        this.recycleAfter = recycleAfter;
        this.log = log == null ? s -> { } : log;
    }

    /** {@code -Dgatf.contextRecycle}: a number &gt;= 0, else {@link #DEFAULT_RECYCLE}. */
    public static int recycleAfter(String value) {
        if (value == null || value.trim().isEmpty()) return DEFAULT_RECYCLE;
        try {
            int n = Integer.parseInt(value.trim());
            return n < 0 ? DEFAULT_RECYCLE : n;
        } catch (NumberFormatException e) {
            return DEFAULT_RECYCLE;
        }
    }

    /**
     * The pooling key of a browser: its kind plus a canonical form of its capabilities, so a script that asks for a
     * different browser or different options gets a fresh one. Values that are not plain data count by class only, and a
     * {@code profile} entry (Firefox serialises its profile afresh per script) is left out.
     */
    public static String fingerprint(String browser, Object caps) {
        StringBuilder b = new StringBuilder(browser == null ? "" : browser).append('|');
        canon(caps, b);
        return b.toString();
    }

    private static void canon(Object o, StringBuilder b) {
        if (o == null || o instanceof String || o instanceof Number || o instanceof Boolean || o instanceof Enum) {
            b.append(o);
        } else if (o instanceof Map) {
            Map<String, Object> sorted = new TreeMap<String, Object>();
            for (Map.Entry<?, ?> e : ((Map<?, ?>) o).entrySet()) {
                // a Firefox profile is serialised afresh for every script (same config, different bytes): not a key
                if (!"profile".equals(String.valueOf(e.getKey()))) sorted.put(String.valueOf(e.getKey()), e.getValue());
            }
            b.append('{');
            for (Map.Entry<String, Object> e : sorted.entrySet()) {
                b.append(e.getKey()).append('=');
                canon(e.getValue(), b);
                b.append(';');
            }
            b.append('}');
        } else if (o instanceof Collection) {
            b.append('[');
            for (Object x : (Collection<?>) o) { canon(x, b); b.append(','); }
            b.append(']');
        } else {
            b.append('<').append(o.getClass().getName()).append('>');
        }
    }

    /**
     * The run's tracing decision, made once (later calls return it): tracing over CDP is bound to the default
     * context, so a pooled context would record nothing. Logged once. FALLBACK also stops pooling for the run.
     */
    public Trace trace(boolean tracing, String protocol, boolean hasDevTools, boolean hasBidi) {
        Trace t;
        synchronized (this) {
            if (trace != null) return trace;
            t = plan(tracing, protocol, hasDevTools, hasBidi);
            trace = t;
        }
        if (t == Trace.BIDI) {
            log.accept("browserContexts: worker: traceProtocol " + protocol + " records only the default context, so this run traces over WebDriver BiDi"
                    + ("hybrid".equals(protocol) ? " (CPU / coverage / video need CDP: not recorded)" : ""));
        } else if (t == Trace.FALLBACK) {
            fallbackRun("tracing is on over CDP, which records nothing in a pooled context, and this session has no WebDriver BiDi");
        }
        return t;
    }

    /** {@link #trace} without the side effects. */
    public static Trace plan(boolean tracing, String protocol, boolean hasDevTools, boolean hasBidi) {
        if (!tracing) return Trace.KEEP;
        // Transports.create: bidi -> BiDi; hybrid -> BiDi + CDP domains; anything else -> CDP on a DevTools driver
        boolean viaCdp = hasDevTools && !"bidi".equals(protocol);
        if (!viaCdp) return Trace.KEEP;
        return hasBidi ? Trace.BIDI : Trace.FALLBACK;
    }

    /**
     * This worker's browser, idle and alive, with the script's fresh context open and current; or null, and the
     * caller launches one and calls {@link #adopt}. A second browser in the same script is never pooled.
     */
    public B acquire(Object worker, Object owner, String key, Opener<B> opener) {
        Slot<B> s;
        Slot<B> drop = null;
        synchronized (this) {
            if (closed || isUnsupported(key)) return null;
            s = byWorker.get(worker);
            if (s == null || s.owner == owner) return null;
            String why = s.owner != null ? "the previous script on this worker never released it"
                    : !s.key.equals(key) ? "this script asks for another browser or other options"
                    : recycleAfter > 0 && s.uses >= recycleAfter ? "recycled after " + recycleAfter + " script(s)" : null;
            if (why != null) {
                drop = remove(s, why);
            } else {
                s.owner = owner;
            }
        }
        if (drop != null) { finish(drop); return null; }
        String ctx = null;
        boolean ok = false;
        try {
            ok = port.alive(s.browser);
            if (ok) ctx = opener.open(s.browser);
        } catch (RuntimeException e) {
            ctx = null;
        }
        synchronized (this) {
            if (ctx == null || closed || byWorker.get(worker) != s) {
                if (byWorker.get(worker) == s) drop = remove(s, ok ? "a context could not be opened in it" : "it no longer answers");
                else drop = null;   // shutdown took it
            } else {
                s.uses++;
                reuses++;
                return s.browser;
            }
        }
        if (drop != null) finish(drop);
        return null;
    }

    /**
     * A browser the script launched after {@link #acquire} returned null. {@code refusal} non-null: this browser
     * kind cannot create contexts (logged once, never pooled again in this run). Returns true when the browser is now
     * this worker's, with the script's context open and current; false = not pooled, the script quits it as today.
     */
    public boolean adopt(Object worker, Object owner, String key, B b, String refusal, Opener<B> opener) {
        Slot<B> stale = null;
        synchronized (this) {
            launches++;
            if (closed || isUnsupported(key)) { unpooled++; return false; }
            Slot<B> s = byWorker.get(worker);
            if (s != null && s.owner == owner) { unpooled++; return false; }   // a second browser in the same script
            if (s != null) stale = remove(s, "replaced by a new browser");
        }
        if (stale != null) finish(stale);
        if (refusal != null) {
            unsupported(key, refusal);
            synchronized (this) { unpooled++; }
            return false;
        }
        String ctx;
        try {
            ctx = opener.open(b);
        } catch (RuntimeException e) {
            ctx = null;
        }
        if (ctx == null) {
            unsupported(key, "a context could not be opened in a new browser");
            synchronized (this) { unpooled++; }
            return false;
        }
        synchronized (this) {
            if (!closed) {
                Slot<B> s = new Slot<B>(worker, key, b);
                s.owner = owner;
                s.uses = 1;
                byWorker.put(worker, s);
                return true;
            }
        }
        // shut down meanwhile: not pooled (the context is disposed with the browser when the script quits it)
        synchronized (this) { unpooled++; }
        return false;
    }

    /**
     * Script end (pass, fail, timeout or abort), after the script disposed its context; any thread. True: the pool
     * owns {@code b} (kept for the next script, or quit here when it crashed or leaked a context the pool could not
     * dispose), so the caller must not quit it. False: not a pooled browser, quit it as today.
     */
    public boolean release(Object owner, B b) {
        Slot<B> s = null;
        synchronized (this) {
            for (Slot<B> x : byWorker.values()) if (x.browser == b) { s = x; break; }
            if (s == null) return false;
            if (s.owner != owner) return true;   // not this script's lease: leave it alone, never quit another's browser
        }
        String why = null;
        try {
            if (!port.alive(b)) {
                why = "it crashed or was closed";
            } else {
                List<String> left = port.contexts(b);
                if (!left.isEmpty()) {
                    for (String c : left) {
                        try { port.dispose(b, c); } catch (RuntimeException e) { /* recounted below */ }
                    }
                    synchronized (this) { leaked += left.size(); }
                    log.accept("browserContexts: worker: disposed " + left.size() + " context(s) a script left behind");
                    if (!port.contexts(b).isEmpty()) why = "contexts it could not dispose";
                }
            }
        } catch (RuntimeException e) {
            why = "it no longer answers (" + e.getMessage() + ")";
        }
        Slot<B> drop = null;
        synchronized (this) {
            if (byWorker.get(s.worker) != s) return true;   // shutdown took it (and quits it)
            if (why != null) drop = remove(s, why);
            else s.owner = null;
        }
        if (drop != null) finish(drop);
        return true;
    }

    /** Run end: quit every pooled browser (any still leased too) and pool nothing more. Idempotent. */
    public void shutdown() {
        List<Slot<B>> all;
        synchronized (this) {
            if (closed) return;
            closed = true;
            all = new ArrayList<Slot<B>>(byWorker.values());
            byWorker.clear();
        }
        for (Slot<B> s : all) {
            if (s.owner != null) log.accept("browserContexts: worker: a browser was still in use at run end; quitting it");
            finish(s);
        }
        log.accept("browserContexts: worker: " + summary());
    }

    /** Stop pooling for every browser kind in this run (logged once). */
    public void fallbackRun(String why) {
        unsupported(ALL, why);
    }

    /** E.g. {@code 4 launch(es), 20 reuse(s), 0 retired, 0 leaked context(s)}. */
    public synchronized String summary() {
        return launches + " browser launch(es), " + reuses + " script(s) on a reused browser, " + retired + " browser(s) retired early, "
                + leaked + " leaked context(s) disposed" + (unpooled > 0 ? ", " + unpooled + " browser(s) not pooled" : "");
    }

    public synchronized int launches() { return launches; }
    public synchronized int reuses() { return reuses; }
    public synchronized int retired() { return retired; }
    public synchronized int leaked() { return leaked; }
    /** Browsers the pool holds now. */
    public synchronized int pooled() { return byWorker.size(); }
    public synchronized boolean pooling(String key) { return !closed && !isUnsupported(key); }
    public synchronized List<String> unsupportedReasons() { return Collections.unmodifiableList(new ArrayList<String>(unsupported.values())); }

    private void unsupported(String key, String why) {
        boolean first;
        synchronized (this) {
            first = unsupported.isEmpty();
            if (!unsupported.containsKey(key)) unsupported.put(key, why);
        }
        // once per run: every later script quietly launches its own browser
        if (first) log.accept("browserContexts: worker: " + why + " - scripts launch their own browser (current behaviour)");
    }

    private boolean isUnsupported(String key) {
        if (unsupported.containsKey(ALL)) return true;
        for (String k : unsupported.keySet()) if (key != null && key.startsWith(browserOf(k))) return true;
        return false;
    }

    /** Support is a property of the browser kind (the key's prefix), not of its options. */
    private static String browserOf(String key) {
        int i = key.indexOf('|');
        return i < 0 ? key + "|" : key.substring(0, i + 1);
    }

    private Slot<B> remove(Slot<B> s, String why) {
        byWorker.remove(s.worker);
        retired++;
        log.accept("browserContexts: worker: replacing a worker browser: " + why);
        return s;
    }

    private void finish(Slot<B> s) {
        try { port.quit(s.browser); } catch (RuntimeException e) { /* already gone */ }
    }
}
