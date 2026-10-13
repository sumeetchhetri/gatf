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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.gatf.selenium.emulation.Emulation;
import com.gatf.selenium.route.BidiRoutes;

/**
 * H3 (Δ153), R2: the isolation contract over a fake CDP / BiDi browser. Each feature is set up in context A and
 * checked for leakage into context B and into the default context; every test counts contexts before and after.
 * The harness keeps routes / grants / clock preloads per session exactly as SeleniumTest's slots do.
 */
class ContextScopeTest {

    /** SeleniumTest's per-driver state for one driver, and its three slots. */
    static final class Harness {
        final FakeBrowser b = new FakeBrowser();
        final boolean cdp;
        final ContextScope scope;
        final List<String> log = new ArrayList<String>();
        String route;                      // intercept id ("__routes__")
        Set<String> granted;               // CDP grant set ("__granted__")
        String clock;                      // preload id ("__clockPreload__")

        Harness(boolean cdp) {
            this(cdp, null);
        }

        /** {@code ops}: the candidates in order (null: one browser-level channel of the given protocol). */
        Harness(boolean cdp, java.util.function.Function<FakeBrowser, List<ContextOps>> ops) {
            this.cdp = cdp;
            List<ContextScope.Slot> slots = new ArrayList<ContextScope.Slot>();
            slots.add(new ContextScope.Slot() {
                public Object stash() { String r = route; route = null; return r; }
                public void teardown() { if (route != null) { b.send("network.removeIntercept", m("intercept", route)); route = null; } }
                public void restore(Object st) { if (st != null) route = (String) st; }
                public void drop() { if (route != null) { b.send("network.removeIntercept", m("intercept", route)); route = null; } }
            });
            slots.add(new ContextScope.Slot() {
                public Object stash() { Set<String> g = granted; granted = null; return g; }
                public void teardown() { granted = null; }
                @SuppressWarnings("unchecked") public void restore(Object st) { if (st != null) granted = (Set<String>) st; }
                public void drop() { granted = null; }
            });
            slots.add(new ContextScope.Slot() {
                public Object stash() { String c = clock; clock = null; return c; }
                public void teardown() { if (clock != null) { b.send("Page.removeScriptToEvaluateOnNewDocument", m("identifier", clock)); clock = null; } }
                public void restore(Object st) { if (st != null) clock = (String) st; }
                public void drop() { clock = null; }
            });
            scope = ops != null ? new ContextScope(ops.apply(b), b, slots, log::add)
                    : new ContextScope(cdp ? ContextCalls.cdp(b) : ContextCalls.bidi(b), b, slots, log::add);
        }

        /** state load over BiDi, as BrowserState does with USER_CONTEXT set to the open context. */
        void bidiStateLoad(String name) {
            Map<String, Object> c = m("cookie", m("name", name, "value", "1"));
            b.send("storage.setCookie", ContextCalls.inUserContext("storage.setCookie", c, scope.context()));
            b.send("script.addPreloadScript", ContextCalls.inUserContext("script.addPreloadScript", m("functionDeclaration", "() => {}"), scope.context()));
        }

        /** route …: BiDi intercept scoped to the current tab inside a context (as SeleniumTest.__route__). */
        void route() {
            String sc = scope.context() != null ? b.current() : null;
            route = (String) b.send("network.addIntercept", BidiRoutes.interceptParams(sc)).get("intercept");
        }

        /** emulate permissions …: the real Emulation plan through a channel that names the open context. */
        void grant(String perm) {
            if (granted == null) granted = new LinkedHashSet<String>();
            Emulation.Channel ch = new Emulation.Channel() {
                public boolean cdp() { return cdp; }
                public Map<String, Object> send(String method, Map<String, Object> params) { return b.send(method, params); }
                public String context() { return b.current(); }
                public String origin() { return "https://shop.test"; }
                public String userContext() { return scope.context(); }
            };
            Emulation.parse("permissions " + perm).apply(null, ch, granted);
        }

        void geolocation() {
            if (cdp) b.send("Emulation.setGeolocationOverride", m("latitude", 1.0));
            else b.send("emulation.setGeolocationOverride", m("contexts", Arrays.asList(b.current())));
        }

        void clockInstall() {
            clock = (String) b.send("Page.addScriptToEvaluateOnNewDocument", m("source", "clock")).get("identifier");
        }

        int contexts() { return (cdp ? ContextCalls.cdp(b) : ContextCalls.bidi(b)).list().size(); }
    }

    static Map<String, Object> m(Object... kv) {
        Map<String, Object> r = new LinkedHashMap<String, Object>();
        for (int i = 0; i < kv.length; i += 2) r.put((String) kv[i], kv[i + 1]);
        return r;
    }

    @Test
    void cookiesStorageAndCacheDoNotLeak() {
        for (boolean cdp : new boolean[] { true, false }) {
            Harness h = new Harness(cdp);
            h.b.here().cookies.put("home", "1");
            int before = h.contexts();
            assertNotNull(h.scope.open());
            h.b.here().cookies.put("sid", "A");
            h.b.here().storage.put("token", "A");
            h.b.here().cache.add("/app.js");
            h.scope.close();
            assertEquals("T0", h.b.current());
            assertEquals(Set.of("home"), h.b.here().cookies.keySet(), "A's cookie in the default context");
            assertTrue(h.b.here().storage.isEmpty() && h.b.here().cache.isEmpty());
            assertNotNull(h.scope.open());
            assertTrue(h.b.here().cookies.isEmpty(), "B sees A's or the default's cookies (" + (cdp ? "cdp" : "bidi") + ")");
            assertTrue(h.b.here().storage.isEmpty() && h.b.here().cache.isEmpty());
            h.scope.close();
            assertEquals(before, h.contexts());
        }
    }

    @Test
    void authStateDoesNotLeak() {
        for (boolean cdp : new boolean[] { true, false }) {
            Harness h = new Harness(cdp);
            h.scope.open();
            h.b.here().cookies.put("session", "admin");
            h.b.here().storage.put("jwt", "x");
            Map<String, String> saved = new LinkedHashMap<String, String>(h.b.here().cookies);   // state save
            h.scope.close();
            assertTrue(h.b.here().cookies.isEmpty(), "A's login reached the default context");
            h.scope.open();
            assertTrue(h.b.here().cookies.isEmpty() && h.b.here().storage.isEmpty(), "B starts logged in");
            h.b.here().cookies.putAll(saved);                                                   // state load (storageState)
            String bTab = h.b.current();
            h.scope.close();
            assertTrue(h.b.here().cookies.isEmpty(), "B's loaded state reached the default context");
            assertFalse(h.b.tabs.containsKey(bTab));
            assertEquals(0, h.contexts());
        }
    }

    @Test
    void routesDoNotLeak() {
        for (boolean cdp : new boolean[] { true, false }) {
            Harness h = new Harness(cdp);
            h.route();                                       // default context's route (unscoped here)
            String home = h.route;
            h.scope.open();
            assertNull(h.route, "the default route is visible to the context's route table");
            h.route();
            assertTrue(h.b.intercepted());
            assertEquals(List.of(h.b.current()), h.b.intercepts.get(h.route), "A's intercept is not scoped to A's tab");
            h.scope.close();
            assertEquals(home, h.route, "the default route was not restored");
            assertEquals(Set.of(home), h.b.intercepts.keySet(), "A's intercept outlived A");
            h.b.intercepts.remove(home);                      // only A-style scoped intercepts from here on
            h.route = null;
            h.scope.open();
            h.route();
            String bTab = h.b.current();
            h.scope.close();
            h.scope.open();
            assertFalse(h.b.intercepted(), "B's route reaches the next context");
            h.scope.close();
            assertFalse(h.b.intercepted(), "a context's route reaches the default tab");
            assertFalse(h.b.tabs.containsKey(bTab));
            assertEquals(0, h.contexts());
        }
    }

    @Test
    void emulationDoesNotLeak() {
        for (boolean cdp : new boolean[] { true, false }) {
            Harness h = new Harness(cdp);
            h.grant("notifications");                         // default context's grant
            Set<String> homeGranted = h.granted;
            h.scope.open();
            String a = h.scope.context();
            String aTab = h.b.current();
            assertNull(h.granted, "the default grant set is shared with the context");
            h.grant("geolocation");
            h.geolocation();
            assertFalse(h.b.contexts.get(a).permissions.isEmpty(), "the grant did not reach A");
            h.scope.close();
            assertFalse(h.b.contexts.get(FakeBrowser.DEFAULT).permissions.contains("geolocation"), "A's grant went to the default context");
            assertEquals(homeGranted, h.granted);
            assertFalse(h.b.overrides.containsKey(aTab));
            h.scope.open();
            String b = h.scope.context();
            assertTrue(h.b.contexts.get(b).permissions.isEmpty(), "B inherits A's grants");
            assertFalse(h.b.overrides.containsKey(h.b.current()), "B inherits A's emulation");
            h.scope.close();
            assertFalse(h.b.overrides.containsKey("T0"));
            assertEquals(0, h.contexts());
        }
    }

    @Test
    void clockDoesNotLeak() {
        for (boolean cdp : new boolean[] { true, false }) {
            Harness h = new Harness(cdp);
            h.clockInstall();                                 // default tab's fake clock
            String home = h.clock;
            h.scope.open();
            assertNull(h.clock);
            h.clockInstall();
            String aTab = h.b.current();
            assertEquals(1, h.b.preloads.get(aTab).size());
            h.scope.close();
            assertFalse(h.b.preloads.containsKey(aTab));
            assertEquals(home, h.clock, "the default clock registration was lost");
            assertEquals(List.of(home), h.b.preloads.get("T0"), "the default tab's clock was removed or doubled");
            h.scope.open();
            assertFalse(h.b.preloads.containsKey(h.b.current()), "B inherits A's clock");
            h.scope.close();
            assertEquals(0, h.contexts());
        }
    }

    @Test
    void teardownOnPassFailTimeoutAndAbort() {
        List<Runnable> outcomes = Arrays.asList(
                () -> { },                                                                      // pass
                () -> { throw new AssertionError("expected 2 rows"); },                         // fail
                () -> { throw new RuntimeException("Timed out after 60 seconds waiting for id@'x'"); },   // timeout
                () -> { throw new RuntimeException("Stopped Execution"); },                    // abort (stop)
                () -> { Thread.currentThread().interrupt(); throw new RuntimeException(new InterruptedException()); });
        for (boolean cdp : new boolean[] { true, false }) {
            Harness h = new Harness(cdp);
            int before = h.contexts();
            for (Runnable body : outcomes) {
                try {
                    h.scope.open();
                    h.route();
                    h.clockInstall();
                    body.run();
                } catch (RuntimeException | AssertionError expected) {
                    // the subtest's failure
                } finally {
                    h.scope.close();
                }
                Thread.interrupted();
                assertEquals(before, h.contexts(), "context leaked");
                assertEquals("T0", h.b.current());
                assertTrue(h.b.intercepts.isEmpty() && h.b.preloads.isEmpty());
            }
            assertEquals(outcomes.size(), h.scope.opened());
            assertEquals(outcomes.size(), h.scope.closed());
            assertEquals(List.of("T0"), new ArrayList<String>(h.b.handles()));
        }
    }

    @Test
    void testThatClosesWindows() {
        Harness h = new Harness(true);
        h.scope.open();
        h.b.closeTab();                                         // `close` inside the subtest
        h.scope.close();
        assertEquals("T0", h.b.current());
        assertEquals(0, h.contexts());
        h.b.tabs.put("T50", FakeBrowser.DEFAULT);               // a popup in the default context
        h.scope.open();
        h.b.switchTo("T0");
        h.b.closeTab();                                         // the test closed the window it started from
        h.b.switchTo(h.b.handles().stream().filter(x -> !x.equals("T50")).findFirst().get());
        h.scope.close();
        assertEquals("T50", h.b.current(), "not moved to a surviving window");
        assertEquals(0, h.contexts());
    }

    @Test
    void fallbacksNeverThrowNorLeak() {
        Harness none = new Harness(true);
        ContextScope noOps = new ContextScope((ContextOps) null, none.b, null, none.log::add);
        assertNull(noOps.open());
        assertNull(noOps.open());
        noOps.close();
        assertEquals(1, none.log.size(), "the fallback is logged once per session");
        assertTrue(none.log.get(0).contains("current behaviour"));

        Harness refused = new Harness(false);
        refused.b.refuse.add("browser.createUserContext");
        assertNull(refused.scope.open());
        assertEquals("T0", refused.b.current());

        Harness hiddenTab = new Harness(true);                  // created, but the driver cannot switch to it
        hiddenTab.b.hidden.add("T2");
        assertNull(hiddenTab.scope.open());
        assertEquals(0, hiddenTab.contexts(), "a context that could not be used was not disposed");
        assertEquals("T0", hiddenTab.b.current());

        Harness stuck = new Harness(true);                      // dispose refused: retried at script end
        stuck.scope.open();
        stuck.b.refuse.add("Target.disposeBrowserContext");
        stuck.scope.close();
        assertEquals("T0", stuck.b.current());
        assertEquals(1, stuck.scope.leaked().size());
        stuck.b.refuse.clear();
        stuck.scope.closeAll();
        assertTrue(stuck.scope.leaked().isEmpty());
        assertEquals(0, stuck.contexts());
    }

    @Test
    void oneContextAtATimeAndResolve() {
        Harness h = new Harness(false);
        String a = h.scope.open();
        assertEquals(a, h.scope.open(), "a nested open made a second context");
        assertEquals(1, h.contexts());
        h.scope.closeAll();
        assertEquals(0, h.contexts());
        assertNull(h.scope.context());
        assertEquals("CDwindow-AB12", ContextScope.resolve("AB12", Set.of("CDwindow-AB12", "X")));
        assertEquals("AB12", ContextScope.resolve("AB12", Set.of("AB12")));
        assertEquals("Q", ContextScope.resolve("Q", null));
    }

    @Test
    void bidiAuthStateGoesToTheContextNotTheDefault() {
        Harness h = new Harness(false);
        h.scope.open();
        String a = h.scope.context();
        h.bidiStateLoad("session");
        assertEquals(Set.of("session"), h.b.contexts.get(a).cookies.keySet(), "storageState did not reach the context");
        assertTrue(h.b.contexts.get(FakeBrowser.DEFAULT).cookies.isEmpty(), "storageState went to the default user context");
        String preload = h.b.bidiPreloads.keySet().iterator().next();
        assertEquals(List.of(a), h.b.bidiPreloads.get(preload), "the storage preload is not scoped to the context");
        h.scope.close();
        h.scope.open();
        String b = h.scope.context();
        for (List<String> ucs : h.b.bidiPreloads.values()) assertFalse(ucs == null || ucs.contains(b), "A's storage preload reaches B");
        h.scope.close();
        assertEquals(0, h.contexts());
    }

    @Test
    void clockTeardownWhenTheTestEndedOnTheDefaultTab() {
        Harness h = new Harness(true);
        h.clockInstall();                                     // default tab: id "1"
        h.scope.open();
        h.clockInstall();                                     // context tab: also id "1" (per-target counter)
        h.b.switchTo("T0");                                   // `tab main` / the end of a `tab N { }` block
        h.scope.close();
        assertEquals(List.of("1"), h.b.preloads.get("T0"), "teardown removed the default tab's clock");
        assertEquals("1", h.clock);
    }

    @Test
    void cdpRefusedOnThePageFallsThroughToBidi() {
        Harness h = new Harness(false, b -> Arrays.asList(ContextCalls.cdp(b.pageCdp()), ContextCalls.bidi(b)));
        assertNotNull(h.scope.open(), "no fall-through to BiDi");
        assertEquals("bidi", h.scope.protocol());
        assertTrue(h.log.get(0).contains("Not allowed") && h.log.get(0).contains("trying bidi"), h.log.toString());
        h.scope.close();
        int calls = h.b.calls.size();
        h.scope.open();
        assertFalse(h.b.calls.subList(calls, h.b.calls.size()).contains("Target.createBrowserContext"), "CDP retried every subtest");
        h.scope.close();
        assertEquals(0, h.contexts());
        Harness only = new Harness(true, b -> Arrays.asList(ContextCalls.cdp(b.pageCdp())));
        assertNull(only.scope.open());
        assertNull(only.scope.open());
        assertEquals(2, only.log.size(), "each subtest without isolation is logged");
    }

    @Test
    void tabCommandsSeeOnlyTheContextsTabs() {
        Harness h = new Harness(false);
        h.b.tabs.put("T9", FakeBrowser.DEFAULT);              // the script's popup, opened before the subtest
        assertEquals(Set.of("T0", "T9"), h.scope.handles());
        h.scope.open();
        String own = h.b.current();
        h.b.tabs.put("T50", h.scope.context());               // a popup inside the context
        assertEquals(List.of(own, "T50"), new ArrayList<String>(h.scope.handles()), "the context's own tab first, no default tabs");
        h.scope.close();
        assertEquals(Set.of("T0", "T9"), h.scope.handles());
    }

    @Test
    void stateIsDroppedWhenTheTestClosedItsTab() {
        Harness h = new Harness(false);
        h.scope.open();
        h.route();
        h.grant("geolocation");
        h.clockInstall();
        h.b.closeTab();
        h.b.switchTo("T0");
        h.scope.close();
        assertNull(h.route, "the context's route entry leaked into the default context");
        assertNull(h.granted, "the context's grants leaked into the default context");
        assertNull(h.clock, "the context's clock entry leaked into the default context");
        assertTrue(h.b.intercepts.isEmpty(), "the context's intercept outlived it");
        assertEquals(0, h.contexts());
    }

    @Test
    void aWindowErrorDoesNotDemoteTheProtocol() {
        Harness h = new Harness(false, b -> Arrays.asList(ContextCalls.bidi(b), ContextCalls.cdp(b.pageCdp())));
        String t0 = h.b.current;
        h.b.current = "gone";                                   // no current window this once
        assertNull(h.scope.open());
        h.b.current = t0;
        assertNotNull(h.scope.open(), "one window error dropped BiDi for the rest of the script");
        assertEquals("bidi", h.scope.protocol());
        h.scope.close();
        assertEquals(0, h.contexts());
    }
}
