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
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * H3 (Δ153): one browser session's per-test context. {@link #open} creates an empty context, opens a tab in it and
 * switches the driver there; {@link #close} switches back to the window the test started from and disposes the
 * context (the browser closes its tabs). The same WebDriver keeps working throughout: only its current window moves,
 * so generated code holding the driver needs no change.
 *
 * <p>{@link Slot}s carry the per-session state GATF keeps per driver (routes, permission grants, clock preloads):
 * the default context's values are set aside while a context is open and restored after it, and whatever the test
 * created inside the context is torn down before the context goes. Never throws: any failure falls back to the
 * shared default context (logged), and a context that could not be disposed is retried by {@link #closeAll}.
 * Pure (ports only), so lifecycle and leakage are unit-tested with fakes.
 */
public final class ContextScope {

    /** The driver's window handles. */
    public interface Window {
        String current();
        void switchTo(String handle);
        Set<String> handles();
    }

    /** Per-driver state that must not cross a context boundary. */
    public interface Slot {
        /** Remove and return the default context's value (null if none). */
        Object stash();
        /** Undo what the test set up inside the context (called while the context's tab is current). */
        void teardown();
        /** Put back what {@link #stash} returned. */
        void restore(Object stashed);
        /** The context's tab is gone: forget what the test set up there without sending anything. */
        default void drop() { }
    }

    private final List<ContextOps> candidates;
    private final Window window;
    private final List<Slot> slots;
    private final Consumer<String> log;

    private ContextOps ops;                       // the protocol that worked (null until the first open)
    private String context, handle, home;
    private Set<String> before;                   // the browser's windows when the context opened
    private Object[] stashed;
    private final List<String> leaked = new ArrayList<String>();
    private int opened, closed;
    private String unsupported;

    public ContextScope(ContextOps ops, Window window, List<Slot> slots, Consumer<String> log) {
        this(ops == null ? Collections.<ContextOps>emptyList() : Collections.singletonList(ops), window, slots, log);
    }

    /** {@code candidates} in order of preference: one refused at run time is dropped and the next is tried. */
    public ContextScope(List<ContextOps> candidates, Window window, List<Slot> slots, Consumer<String> log) {
        this.candidates = new ArrayList<ContextOps>();
        if (candidates != null) for (ContextOps o : candidates) if (o != null) this.candidates.add(o);
        this.window = window;
        this.slots = slots == null ? Collections.<Slot>emptyList() : slots;
        this.log = log == null ? s -> { } : log;
    }

    /** The open context's id, or null. */
    public String context() { return context; }

    /** The protocol in use (cdp / bidi), or null when none has worked yet. */
    public String protocol() { return ops != null ? ops.protocol() : candidates.isEmpty() ? null : candidates.get(0).protocol(); }

    public int opened() { return opened; }
    public int closed() { return closed; }
    public List<String> leaked() { return Collections.unmodifiableList(leaked); }

    /** This session can never create contexts: logged once, and every later open returns null quietly. */
    public void warnFallback(String why) {
        if (unsupported != null) return;
        unsupported = why;
        candidates.clear();
        log.accept("browserContexts: " + why + " - subtests share the browser's default context (current behaviour)");
    }

    /**
     * Open a fresh context and make its tab current. Returns the context id, or null when contexts are unavailable
     * (the test then runs in the shared default context; a run-time refusal is logged every time). A second open
     * while one is open returns the open one.
     */
    public String open() {
        if (context != null) return context;
        if (unsupported != null) return null;
        if (candidates.isEmpty()) { warnFallback("no CDP or WebDriver BiDi channel"); return null; }
        String last = null;
        String start;
        Set<String> was;
        try {
            start = window.current();
            was = new LinkedHashSet<String>(window.handles());
        } catch (RuntimeException e) {
            log.accept("browserContexts: no current window (" + e.getMessage() + ") - this subtest shares the browser's default context");
            return null;
        }
        while (!candidates.isEmpty()) {
            ContextOps o = candidates.get(0);
            String ctx = null;
            boolean created = false;
            try {
                ctx = o.create();
                created = true;
                String page = o.openPage(ctx);
                String h = resolve(page, window.handles());
                window.switchTo(h);
                Object[] st = new Object[slots.size()];
                for (int i = 0; i < slots.size(); i++) {
                    try {
                        st[i] = slots.get(i).stash();
                    } catch (RuntimeException e) {
                        st[i] = null;
                    }
                }
                ops = o;
                context = ctx;
                handle = h;
                home = start;
                before = was;
                stashed = st;
                opened++;
                log.accept("browserContexts: opened " + o.protocol() + " context " + ctx);
                return ctx;
            } catch (RuntimeException e) {
                if (ctx != null) dispose(o, ctx);
                last = o.protocol() + " context unavailable (" + e.getMessage() + ")";
                if (!created && candidates.size() > 1) {
                    // the protocol refused to create a context (e.g. CDP "Not allowed"): this channel will not work, try the next
                    log.accept("browserContexts: " + last + ", trying " + candidates.get(1).protocol());
                    candidates.remove(0);
                    o.close();
                    continue;
                }
                break;
            }
        }
        log.accept("browserContexts: " + last + " - this subtest shares the browser's default context");
        return null;
    }

    /**
     * The windows a test in the open context may index ({@code tab N}, {@code close others}): the context's own tab
     * first, then tabs opened since, never the default context's. With no context open: every window, unchanged.
     */
    public Set<String> handles() {
        Set<String> all = window.handles();
        if (context == null) return all;
        Set<String> own = new LinkedHashSet<String>();
        if (all.contains(handle)) own.add(handle);
        for (String h : all) if (before == null || !before.contains(h)) own.add(h);
        return own;
    }

    /** Tear down and dispose the open context, back on the window the test started from. No-op if none is open. */
    public void close() {
        String ctx = context;
        if (ctx == null) return;
        boolean onOwnTab = false;
        try {
            if (window.handles().contains(handle)) {
                window.switchTo(handle);   // teardown commands go to the current tab: make it the context's, never the default's
                onOwnTab = true;
            }
        } catch (RuntimeException e) {
            // tab gone: teardown below only drops bookkeeping
        }
        for (Slot s : slots) {
            try {
                if (onOwnTab) s.teardown();
            } catch (RuntimeException e) {
                // the tab may already be gone: nothing left to undo there
            }
            try {
                s.drop();   // after a teardown this is a no-op; with the tab gone it keeps the context's entries out of the default's
            } catch (RuntimeException e) {
                // keep going
            }
        }
        boolean back = false;
        try {
            window.switchTo(home);
            back = true;
        } catch (RuntimeException e) {
            // the starting window was closed by the test: pick a survivor after the context is gone
        }
        dispose(ops, ctx);
        if (!back) {
            try {
                for (String h : window.handles()) {
                    if (!h.equals(handle)) { window.switchTo(h); break; }
                }
            } catch (RuntimeException e) {
                // no window left: the next command reports it
            }
        }
        Object[] st = stashed;
        for (int i = 0; i < slots.size(); i++) {
            try {
                slots.get(i).restore(st == null ? null : st[i]);
            } catch (RuntimeException e) {
                // keep restoring the rest
            }
        }
        context = handle = home = null;
        before = null;
        stashed = null;
        closed++;
    }

    /** Close the open context, retry disposing any that failed before, and release the protocol connection. */
    public void closeAll() {
        close();
        for (String c : new ArrayList<String>(leaked)) {
            try {
                ops.remove(c);
                leaked.remove(c);
            } catch (RuntimeException e) {
                log.accept("browserContexts: context " + c + " could not be disposed (" + e.getMessage() + "); it goes when the browser quits");
            }
        }
        for (ContextOps o : candidates) {
            try { o.close(); } catch (RuntimeException e) { /* connection already gone */ }
        }
        if (ops != null && !candidates.contains(ops)) {
            try { ops.close(); } catch (RuntimeException e) { /* connection already gone */ }
        }
    }

    private void dispose(ContextOps o, String ctx) {
        try {
            o.remove(ctx);
        } catch (RuntimeException e) {
            if (ops == null) ops = o;
            leaked.add(ctx);
            log.accept("browserContexts: could not dispose context " + ctx + " (" + e.getMessage() + "); retried at script end");
        }
    }

    /** The window handle of a page id: equal, or one ending with the other (older drivers prefix handles). */
    static String resolve(String page, Set<String> handles) {
        if (handles != null) {
            for (String h : handles) if (h.equals(page)) return h;
            for (String h : handles) if (h.endsWith(page) || page.endsWith(h)) return h;
        }
        return page;
    }
}
