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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/**
 * H3 (Δ154), R1/R2: the worker pool's scheduling and lifecycle over fake browsers (FakeBrowser from Δ153 speaks the
 * BiDi context commands), driven exactly as SeleniumTest drives it: acquire, else launch + adopt; the script's own
 * ContextScope opens and closes its context; release at quit; shutdown at run end.
 */
class WorkerPoolTest {

    /** One browser process: a FakeBrowser plus life and use bookkeeping. */
    static final class Br {
        final FakeBrowser fb = new FakeBrowser();
        final int id;
        volatile boolean dead, quit;
        final AtomicInteger inUse = new AtomicInteger();
        Br(int id) { this.id = id; }
        synchronized Map<String, Object> send(String m, Map<String, Object> p) {
            if (dead) throw new IllegalStateException("browser crashed");
            return fb.send(m, p);
        }
        int contexts() { return fb.contexts.size() - 1; }
        @Override public String toString() { return "B" + id; }
    }

    static final class Port implements WorkerPool.Port<Br> {
        public boolean alive(Br b) { return !b.dead && !b.quit; }
        public List<String> contexts(Br b) { return ContextCalls.bidi(b::send).list(); }
        public void dispose(Br b, String c) { ContextCalls.bidi(b::send).remove(c); }
        public void quit(Br b) { b.quit = true; }
    }

    /** A run: the pool plus everything it launched and logged. */
    static final class Run {
        final List<String> log = Collections.synchronizedList(new ArrayList<String>());
        final WorkerPool<Br> pool;
        final List<Br> launched = Collections.synchronizedList(new ArrayList<Br>());
        final AtomicInteger seq = new AtomicInteger();
        String refusal;
        /** Simulated storageState (loginOnce): applied to each new context, as initBrowser does on the current tab. */
        Map<String, String> state;
        final AtomicInteger stateLoads = new AtomicInteger();
        Run(int recycle) { pool = new WorkerPool<Br>(new Port(), recycle, log::add); }
        Run() { this(WorkerPool.DEFAULT_RECYCLE); }
        Br launch() { Br b = new Br(seq.incrementAndGet()); launched.add(b); return b; }
        int quitCount() { int n = 0; synchronized (launched) { for (Br b : launched) if (b.quit) n++; } return n; }
        int liveContexts() { int n = 0; synchronized (launched) { for (Br b : launched) if (!b.quit) n += b.contexts(); } return n; }
    }

    /** One script, as SeleniumTest runs it (open <browser> ... quit). */
    static final class Script {
        final Run run;
        final Object worker;
        final String key;
        Br browser;
        boolean pooled;
        ContextScope scope;
        Script(Run run, Object worker, String key) { this.run = run; this.worker = worker; this.key = key; }

        String open(Br b) {
            scope = new ContextScope(ContextCalls.bidi(b::send), b.fb, null, null);
            String ctx = scope.open();
            if (ctx != null && run.state != null) {
                synchronized (b) { b.fb.here().cookies.putAll(run.state); }
                run.stateLoads.incrementAndGet();
            }
            return ctx;
        }

        Script start() {
            browser = run.pool.acquire(worker, this, key, this::open);
            pooled = browser != null;
            if (browser == null) {
                browser = run.launch();
                pooled = run.pool.adopt(worker, this, key, browser, run.refusal, this::open);
            }
            assertEquals(1, browser.inUse.incrementAndGet(), "a browser is used by one script at a time");
            return this;
        }

        /** Script end however it ended: its scope is closed (quit's __ctxCloseAll__), then the pool keeps or it quits. */
        void end(boolean closeScope) {
            browser.inUse.decrementAndGet();
            if (closeScope && scope != null) {
                try { scope.closeAll(); } catch (RuntimeException e) { /* never throws */ }
            }
            if (!run.pool.release(this, browser)) browser.quit = true;
        }
        void end() { end(true); }
        FakeBrowser.Ctx here() { return browser.fb.here(); }
    }

    static final String CHROME = WorkerPool.fingerprint("chrome", Collections.singletonMap("goog:chromeOptions", Collections.singletonMap("args", Arrays.asList("--headless"))));

    // ------------------------------------------------------------------ assignment

    @Test
    void eachWorkerLaunchesOnceAndRunsEveryScriptInAFreshContext() {
        Run r = new Run();
        Object w1 = "w1", w2 = "w2";
        List<String> ctxs = new ArrayList<String>();
        for (int i = 0; i < 5; i++) {
            for (Object w : new Object[] { w1, w2 }) {
                Script s = new Script(r, w, CHROME).start();
                assertTrue(s.pooled);
                assertNotNull(s.scope.context());
                ctxs.add(s.browser + "/" + s.scope.context());
                assertEquals(1, s.browser.contexts(), "exactly the script's context is open");
                s.end();
                assertEquals(0, s.browser.contexts(), "disposed at script end");
                assertFalse(s.browser.quit);
            }
        }
        assertEquals(2, r.pool.launches(), "one browser per worker, not per script");
        assertEquals(8, r.pool.reuses());
        assertEquals(10, ctxs.size());
        assertEquals(10, new java.util.HashSet<String>(ctxs).size(), "never the same context twice");
        assertEquals(2, r.pool.pooled());
        r.pool.shutdown();
        assertEquals(2, r.quitCount());
        assertEquals(0, r.pool.pooled());
    }

    @Test
    void workersNeverShareABrowser() {
        Run r = new Run();
        Script a = new Script(r, "w1", CHROME).start();
        Script b = new Script(r, "w2", CHROME).start();   // w1's browser is busy, w2 gets its own
        assertTrue(a.browser != b.browser);
        a.end();
        Script c = new Script(r, "w2", CHROME);
        b.end();
        c.start();
        assertTrue(c.browser == b.browser, "a worker reuses its own browser, never another worker's");
        c.end();
        r.pool.shutdown();
    }

    @Test
    void aSecondBrowserInTheSameScriptIsNotPooled() {
        Run r = new Run();
        Script s = new Script(r, "w", CHROME).start();
        // `open chrome` again / newWindow inside the same script: acquire says launch, adopt says not pooled
        assertNull(r.pool.acquire("w", s, CHROME, b -> "x"));
        Br second = r.launch();
        assertFalse(r.pool.adopt("w", s, CHROME, second, null, b -> "x"));
        assertTrue(r.pool.release(s, s.browser));
        assertFalse(r.pool.release(s, second), "the second browser is the script's own: it quits it");
        r.pool.shutdown();
    }

    @Test
    void anotherBrowserOrOtherOptionsGetAFreshBrowser() {
        Run r = new Run();
        Script a = new Script(r, "w", CHROME).start();
        a.end();
        String ff = WorkerPool.fingerprint("firefox", null);
        Script b = new Script(r, "w", ff).start();
        assertTrue(a.browser.quit, "the worker's chrome is replaced");
        assertTrue(b.browser != a.browser);
        b.end();
        String chrome2 = WorkerPool.fingerprint("chrome", Collections.singletonMap("goog:chromeOptions", Collections.singletonMap("args", Arrays.asList("--lang=de"))));
        Script c = new Script(r, "w", chrome2).start();
        assertTrue(b.browser.quit);
        c.end();
        r.pool.shutdown();
        assertEquals(3, r.pool.launches());
    }

    @Test
    void fingerprintIsCanonical() {
        Map<String, Object> m1 = new LinkedHashMap<String, Object>();
        m1.put("b", 1); m1.put("a", Arrays.asList("x", true));
        Map<String, Object> m2 = new LinkedHashMap<String, Object>();
        m2.put("a", Arrays.asList("x", true)); m2.put("b", 1);
        assertEquals(WorkerPool.fingerprint("chrome", m1), WorkerPool.fingerprint("chrome", m2), "key order does not matter");
        m2.put("logging", new Object());   // non-data values (LoggingPreferences) count by class, not identity
        m1.put("logging", new Object());
        assertEquals(WorkerPool.fingerprint("chrome", m1), WorkerPool.fingerprint("chrome", m2));
        assertFalse(WorkerPool.fingerprint("chrome", m1).equals(WorkerPool.fingerprint("edge", m1)));
        Map<String, Object> ff1 = new LinkedHashMap<String, Object>(), ff2 = new LinkedHashMap<String, Object>();
        ff1.put("moz:firefoxOptions", Collections.singletonMap("profile", "UEsDBBQ...a"));
        ff2.put("moz:firefoxOptions", Collections.singletonMap("profile", "UEsDBBQ...b"));
        assertEquals(WorkerPool.fingerprint("firefox", ff1), WorkerPool.fingerprint("firefox", ff2), "a re-serialised Firefox profile is the same browser");
    }

    // ------------------------------------------------------------------ limits

    @Test
    void recycleAfterNScripts() {
        Run r = new Run(3);
        Br first = null;
        for (int i = 0; i < 7; i++) {
            Script s = new Script(r, "w", CHROME).start();
            if (i == 0) first = s.browser;
            if (i < 3) assertTrue(s.browser == first);
            s.end();
        }
        assertTrue(first.quit);
        assertEquals(3, r.pool.launches(), "7 scripts, a fresh browser every 3");
        r.pool.shutdown();
        assertEquals(3, r.quitCount());
    }

    @Test
    void recycleSettingDefaultsSafely() {
        assertEquals(WorkerPool.DEFAULT_RECYCLE, WorkerPool.recycleAfter(null));
        assertEquals(WorkerPool.DEFAULT_RECYCLE, WorkerPool.recycleAfter(" "));
        assertEquals(WorkerPool.DEFAULT_RECYCLE, WorkerPool.recycleAfter("abc"));
        assertEquals(WorkerPool.DEFAULT_RECYCLE, WorkerPool.recycleAfter("-4"));
        assertEquals(0, WorkerPool.recycleAfter("0"));
        assertEquals(10, WorkerPool.recycleAfter(" 10 "));
        Run r = new Run(0);   // 0 = never recycle
        for (int i = 0; i < 120; i++) new Script(r, "w", CHROME).start().end();
        assertEquals(1, r.pool.launches());
        r.pool.shutdown();
    }

    // ------------------------------------------------------------------ fallback

    @Test
    void unsupportedBrowsersFallBackToABrowserPerScriptLoggedOnce() {
        Run r = new Run();
        r.refusal = "needs CDP (local Chromium) or a WebDriver BiDi session (webSocketUrl); e.g. Safari";
        String safari = WorkerPool.fingerprint("safari", null);
        for (int i = 0; i < 4; i++) {
            for (Object w : new Object[] { "w1", "w2" }) {
                Script s = new Script(r, w, safari).start();
                assertFalse(s.pooled);
                assertNull(s.scope, "no context is attempted");
                s.end();
                assertTrue(s.browser.quit, "the script quits its own browser, as today");
            }
        }
        assertEquals(8, r.launched.size());
        assertEquals(1, count(r.log, "scripts launch their own browser"), "said once: " + r.log);
        assertEquals(0, r.pool.pooled());
        r.pool.shutdown();
    }

    @Test
    void aContextRefusedAtRunTimeFallsBackToo() {
        Run r = new Run();
        Script s = new Script(r, "w", CHROME);
        s.browser = null;
        Br b = r.launch();
        b.fb.refuse.add("browser.createUserContext");
        s.browser = b;
        assertFalse(r.pool.adopt("w", s, CHROME, b, null, s::open));
        assertFalse(r.pool.pooling(CHROME));
        assertNull(r.pool.acquire("w2", new Object(), CHROME, x -> "c"), "never pooled again in this run");
        r.pool.shutdown();
    }

    @Test
    void aReusedBrowserThatRefusesAContextIsReplacedNeverSharedUnisolated() {
        Run r = new Run();
        Script a = new Script(r, "w", CHROME).start();
        a.end();
        a.browser.fb.refuse.add("browser.createUserContext");
        Script b = new Script(r, "w", CHROME).start();
        assertTrue(a.browser.quit, "the browser that cannot isolate is quit");
        assertTrue(b.browser != a.browser);
        b.end();
        r.pool.shutdown();
    }

    // ------------------------------------------------------------------ crash, abort, leaks

    @Test
    void aBrowserCrashMidTestIsCleanedUpAndTheWorkerCarriesOn() {
        Run r = new Run();
        Script a = new Script(r, "w", CHROME).start();
        Br crashed = a.browser;
        crashed.dead = true;   // the browser dies while the test runs: the test fails, its quit still runs
        a.end();
        assertTrue(crashed.quit, "the crashed browser (and every context in it) is quit");
        assertEquals(0, r.pool.pooled());
        // the remaining tests on that worker run as before: a fresh browser
        for (int i = 0; i < 3; i++) {
            Script s = new Script(r, "w", CHROME).start();
            assertTrue(s.pooled && s.browser != crashed);
            s.end();
        }
        assertEquals(2, r.pool.launches());
        assertEquals(1, r.pool.retired());
        r.pool.shutdown();
        assertEquals(0, r.liveContexts());
    }

    @Test
    void aCrashBeforeTheNextScriptIsDetectedAtAcquire() {
        Run r = new Run();
        Script a = new Script(r, "w", CHROME).start();
        a.end();
        a.browser.dead = true;   // dies while idle
        Script b = new Script(r, "w", CHROME).start();
        assertTrue(a.browser.quit);
        assertTrue(b.pooled && b.browser != a.browser);
        b.end();
        r.pool.shutdown();
    }

    @Test
    void aWorkerThatDiedWithoutQuittingLeavesNothingBehind() {
        Run r = new Run();
        Script a = new Script(r, "w", CHROME).start();
        a.browser.inUse.decrementAndGet();   // the worker task died: no quit, no release
        Script b = new Script(r, "w", CHROME).start();
        assertTrue(a.browser.quit, "a lease never released is retired: its browser and context go");
        assertTrue(b.browser != a.browser);
        b.end();
        r.pool.shutdown();
        assertEquals(0, r.liveContexts());
        assertEquals(r.launched.size(), r.quitCount());
    }

    @Test
    void contextsAScriptLeftBehindAreDisposedAndCounted() {
        Run r = new Run();
        Script a = new Script(r, "w", CHROME).start();
        int before = a.browser.contexts();
        a.end(false);   // the script's own scope never closed (e.g. killed between steps)
        assertEquals(0, a.browser.contexts(), "release disposed it");
        assertEquals(1, before);
        assertEquals(1, r.pool.leaked());
        // its tab was the current window and is gone with the context: the next script gets a working, isolated
        // context either way (the same browser, or a replacement when no window can be switched to)
        Script b = new Script(r, "w", CHROME).start();
        assertTrue(b.pooled);
        assertEquals(1, b.browser.contexts());
        assertTrue(b.here().cookies.isEmpty());
        b.end();
        r.pool.shutdown();
    }

    @Test
    void aContextThatCannotBeDisposedRetiresTheBrowser() {
        Run r = new Run();
        Script a = new Script(r, "w", CHROME).start();
        a.browser.fb.refuse.add("browser.removeUserContext");
        a.end();   // the scope's dispose fails too
        assertTrue(a.browser.quit, "never reuse a browser with a context still in it");
        r.pool.shutdown();
    }

    @Test
    void abortReleasedFromAnotherThreadWorks() throws Exception {
        Run r = new Run();
        Script a = new Script(r, "w", CHROME).start();
        Thread t = new Thread(a::end);   // a stop from the UI thread runs quit
        t.start();
        t.join();
        assertEquals(0, a.browser.contexts());
        Script b = new Script(r, "w", CHROME).start();
        assertTrue(b.browser == a.browser);
        b.end();
        assertTrue(r.pool.release(new Object(), b.browser) , "a stranger's release never quits a pooled browser");
        assertFalse(b.browser.quit);
        r.pool.shutdown();
    }

    @Test
    void aDoubleReleaseNeverQuitsOrCorrupts() {
        Run r = new Run();
        Script a = new Script(r, "w", CHROME).start();
        a.end();
        assertTrue(r.pool.release(a, a.browser), "a second quit of the same script is a no-op");
        assertFalse(a.browser.quit);
        Script b = new Script(r, "w", CHROME).start();
        assertTrue(b.browser == a.browser);
        b.end();
        r.pool.shutdown();
    }

    @Test
    void aBrowserThatDiesWhileTheContextOpensIsReplaced() {
        Run r = new Run();
        Script a = new Script(r, "w", CHROME).start();
        a.end();
        Object owner = new Object();
        assertNull(r.pool.acquire("w", owner, CHROME, b -> { b.dead = true; throw new IllegalStateException("browser crashed"); }));
        assertTrue(a.browser.quit);
        assertEquals(0, r.pool.pooled());
        assertTrue(r.pool.pooling(CHROME), "a crash is not a capability problem: the next launch is pooled");
        r.pool.shutdown();
    }

    // ------------------------------------------------------------------ shutdown

    @Test
    void shutdownQuitsEverythingAndPoolsNothingAfter() {
        Run r = new Run();
        Script busy = new Script(r, "w1", CHROME).start();
        new Script(r, "w2", CHROME).start().end();
        r.pool.shutdown();
        assertEquals(2, r.quitCount(), "idle and in-use browsers alike");
        assertEquals(1, count(r.log, "still in use at run end"));
        busy.end();   // its quit after shutdown: not pooled any more, so it quits (again) itself
        assertNull(r.pool.acquire("w2", new Object(), CHROME, b -> "x"));
        Br late = r.launch();
        assertFalse(r.pool.adopt("w2", new Object(), CHROME, late, null, b -> "x"));
        r.pool.shutdown();   // idempotent
        assertEquals(1, count(r.log, "browser launch(es)"), "summary logged once: " + r.log);
    }

    @Test
    void runsAreKeptApartAndEndQuitsTheirBrowsers() {
        Object runA = new Object(), runB = new Object();
        Run a = new Run(), b = new Run();
        final int[] ended = new int[1];
        WorkerRuns.begin(runA, a.pool, () -> ended[0]++);
        WorkerRuns.begin(runB, b.pool, null);
        assertTrue(WorkerRuns.pool(runA) == a.pool && WorkerRuns.pool(runB) == b.pool);
        assertNull(WorkerRuns.pool(new Object()), "code outside a run finds no pool");
        assertNull(WorkerRuns.pool(null));
        new Script(a, "w", CHROME).start().end();
        WorkerRuns.end(runA);
        assertEquals(1, ended[0]);
        assertEquals(1, a.quitCount());
        assertNull(WorkerRuns.pool(runA));
        // a run that begins again without having ended (an exception escaped its loop) ends the old pool first
        Run b2 = new Run();
        new Script(b, "w", CHROME).start().end();
        WorkerRuns.begin(runB, b2.pool, null);
        assertEquals(1, b.quitCount());
        WorkerRuns.end(runB);
        WorkerRuns.end(runB);   // no-op
        assertEquals(0, WorkerRuns.active());
    }

    // ------------------------------------------------------------------ isolation (cookies, storage, routes, emulation, clock)

    @Test
    void twoScriptsOnOneWorkerNeverSeeEachOthersState() {
        Run r = new Run();
        Script a = new Script(r, "w", CHROME).start();
        FakeBrowser fb = a.browser.fb;
        String tabA = fb.current();
        a.here().cookies.put("sid", "A");
        a.here().storage.put("cart", "3");
        a.here().cache.add("/app.js");
        a.browser.send("browser.createUserContext", null);   // noise: something else the script opened...
        // route: BiDi intercept scoped to the context's tab; emulation + clock: tab-level overrides / preloads
        Map<String, Object> ic = new LinkedHashMap<String, Object>();
        ic.put("contexts", Arrays.asList(tabA));
        a.browser.send("network.addIntercept", ic);
        Map<String, Object> geo = new LinkedHashMap<String, Object>();
        geo.put("contexts", Arrays.asList(tabA));
        a.browser.send("emulation.setGeolocationOverride", geo);
        Map<String, Object> pre = new LinkedHashMap<String, Object>();
        pre.put("userContexts", Arrays.asList(a.scope.context()));
        a.browser.send("script.addPreloadScript", pre);   // clock preload, scoped like ContextCalls.inUserContext does
        Map<String, Object> perm = new LinkedHashMap<String, Object>();
        perm.put("userContext", a.scope.context());
        perm.put("descriptor", Collections.singletonMap("name", "geolocation"));
        a.browser.send("permissions.setPermission", perm);
        assertTrue(fb.intercepted());
        a.end();

        Script b = new Script(r, "w", CHROME).start();
        assertTrue(b.browser == a.browser, "same browser...");
        assertFalse(b.scope.context().equals(a.scope == null ? null : "x"));
        FakeBrowser.Ctx here = b.here();
        assertTrue(here.cookies.isEmpty(), "...no cookies from the previous script");
        assertTrue(here.storage.isEmpty(), "no storage");
        assertTrue(here.cache.isEmpty(), "no cache");
        assertTrue(here.permissions.isEmpty(), "no permission grants");
        assertFalse(fb.intercepted(), "no routes");
        assertNull(fb.overrides.get(fb.current()), "no emulation");
        for (List<String> ucs : fb.bidiPreloads.values()) assertTrue(ucs == null || ucs.isEmpty(), "no clock preload for the new context");
        b.end();
        assertTrue(fb.contexts.get(FakeBrowser.DEFAULT).cookies.isEmpty(), "the default context stays clean");
        assertEquals(1, r.pool.leaked(), "the extra context the first script opened was disposed at its release");
        r.pool.shutdown();
    }

    // ------------------------------------------------------------------ loginOnce

    @Test
    void loginOnceLogsInOnceThenEveryContextStartsWithItsState() {
        Run r = new Run();
        // the loginOnce script runs before the pool exists (WorkerRuns has no pool for it): its own browser
        int logins = 0;
        Br loginBrowser = new Br(0);
        loginBrowser.fb.here().cookies.put("session", "s3cr3t");
        logins++;
        r.state = new LinkedHashMap<String, String>(loginBrowser.fb.here().cookies);   // state save
        loginBrowser.quit = true;
        List<Script> done = new ArrayList<Script>();
        for (int i = 0; i < 6; i++) {
            Script s = new Script(r, i % 2 == 0 ? "w1" : "w2", CHROME).start();
            assertEquals("s3cr3t", s.here().cookies.get("session"), "each context starts logged in");
            s.here().cookies.put("visited", "page" + i);
            done.add(s);
            s.end();
        }
        assertEquals(1, logins);
        assertEquals(6, r.stateLoads.get(), "the state is loaded into each context");
        assertEquals(2, r.pool.launches());
        for (Br b : r.launched) assertTrue(b.fb.contexts.get(FakeBrowser.DEFAULT).cookies.isEmpty(), "never into the default context");
        r.pool.shutdown();
    }

    // ------------------------------------------------------------------ tracing

    @Test
    void tracingOverCdpSwitchesTheRunToBidiOrFallsBack() {
        assertEquals(WorkerPool.Trace.KEEP, WorkerPool.plan(false, "cdp", true, true), "tracing off");
        assertEquals(WorkerPool.Trace.KEEP, WorkerPool.plan(true, "bidi", true, true));
        assertEquals(WorkerPool.Trace.KEEP, WorkerPool.plan(true, "cdp", false, true), "Firefox: always BiDi");
        assertEquals(WorkerPool.Trace.BIDI, WorkerPool.plan(true, "cdp", true, true));
        assertEquals(WorkerPool.Trace.BIDI, WorkerPool.plan(true, null, true, true), "unset = CDP");
        assertEquals(WorkerPool.Trace.BIDI, WorkerPool.plan(true, "hybrid", true, true));
        assertEquals(WorkerPool.Trace.FALLBACK, WorkerPool.plan(true, "cdp", true, false));

        Run r = new Run();
        assertEquals(WorkerPool.Trace.BIDI, r.pool.trace(true, "cdp", true, true));
        assertEquals(WorkerPool.Trace.BIDI, r.pool.trace(true, "cdp", true, false), "decided once per run");
        assertEquals(1, count(r.log, "traces over WebDriver BiDi"));
        assertTrue(r.pool.pooling(CHROME));

        Run f = new Run();
        assertEquals(WorkerPool.Trace.FALLBACK, f.pool.trace(true, "cdp", true, false));
        f.pool.trace(true, "cdp", true, false);
        assertFalse(f.pool.pooling(CHROME), "no BiDi: this run keeps a browser per script");
        assertEquals(1, count(f.log, "records nothing in a pooled context"), "said once: " + f.log);
        Script s = new Script(f, "w", CHROME).start();
        assertFalse(s.pooled);
        s.end();
        assertTrue(s.browser.quit);
        r.pool.shutdown();
        f.pool.shutdown();
    }

    // ------------------------------------------------------------------ worker + subtest

    @Test
    void underWorkerSubtestsRunInsideTheScriptsContext() {
        assertFalse(ContextMode.perSubtest("worker"), "user contexts cannot nest: no context per subtest");
        Run r = new Run();
        Script s = new Script(r, "w", CHROME).start();
        String scriptCtx = s.scope.context();
        // a top-level subtest asking the same scope for a context gets the script's (Δ153: open while open)
        assertEquals(scriptCtx, s.scope.open());
        assertEquals(1, s.browser.contexts());
        s.end();
        r.pool.shutdown();
    }

    // ------------------------------------------------------------------ concurrency

    @Test
    void fourWorkersFortyScriptsWithCrashesAndLeaks() throws Exception {
        final Run r = new Run(7);
        final ConcurrentLinkedQueue<Integer> queue = new ConcurrentLinkedQueue<Integer>();
        for (int i = 0; i < 40; i++) queue.add(i);
        final AtomicInteger crashes = new AtomicInteger(), errors = new AtomicInteger();
        final CountDownLatch go = new CountDownLatch(1);
        List<Thread> ts = new ArrayList<Thread>();
        for (int t = 0; t < 4; t++) {
            Thread th = new Thread(() -> {
                try {
                    go.await();
                    Integer i;
                    while ((i = queue.poll()) != null) {
                        Script s = new Script(r, Thread.currentThread(), CHROME).start();
                        synchronized (s.browser) { s.here().cookies.put("t", "" + i); }
                        if (i % 9 == 4) { s.browser.dead = true; crashes.incrementAndGet(); }
                        s.end(i % 11 != 3);   // some scripts leave their context open
                    }
                } catch (Throwable e) {
                    errors.incrementAndGet();
                    e.printStackTrace();
                }
            });
            ts.add(th);
            th.start();
        }
        go.countDown();
        for (Thread th : ts) th.join();
        assertEquals(0, errors.get());
        assertTrue(r.pool.launches() <= 4 + crashes.get() + 4 * 6, "bounded by workers + crashes + recycles: " + r.pool.summary());
        assertTrue(r.pool.launches() < 40, "fewer launches than scripts: " + r.pool.summary());
        assertEquals(0, r.liveContexts(), "no context left in any live browser");
        r.pool.shutdown();
        assertEquals(r.launched.size(), r.quitCount(), "every browser quit");
        Map<Br, Boolean> distinct = new IdentityHashMap<Br, Boolean>();
        for (Br b : r.launched) distinct.put(b, Boolean.TRUE);
        assertEquals(r.launched.size(), distinct.size());
    }

    static int count(List<String> log, String s) {
        int n = 0;
        synchronized (log) { for (String l : log) if (l.contains(s)) n++; }
        return n;
    }
}
