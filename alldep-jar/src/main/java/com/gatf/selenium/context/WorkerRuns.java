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
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * H3 (Δ154): the worker pools of the runs in progress, keyed by the run (its AcceptanceTestContext, by identity), so
 * two runs in one server never share browsers and code outside a run (loginOnce, globalSetup / globalTeardown, a
 * UI-server single script, a distributed node) finds no pool and launches its own browser as today. A JVM shutdown
 * hook quits whatever a run left open (e.g. an exception escaped the run loop, or Ctrl-C).
 */
public final class WorkerRuns {

    private static final Map<Object, Object[]> RUNS = new IdentityHashMap<Object, Object[]>();   // run -> {pool, onEnd}
    private static final AtomicBoolean HOOK = new AtomicBoolean();

    private WorkerRuns() {}

    /** Start {@code run}'s pool; a pool the same run left open is ended first. {@code onEnd} runs after its shutdown. */
    public static void begin(Object run, WorkerPool<?> pool, Runnable onEnd) {
        Object[] prior;
        synchronized (RUNS) {
            prior = RUNS.put(run, new Object[] { pool, onEnd });
        }
        if (prior != null) close(prior);
        if (HOOK.compareAndSet(false, true)) {
            try {
                Runtime.getRuntime().addShutdownHook(new Thread(WorkerRuns::endAll, "gatf-worker-browsers"));
            } catch (RuntimeException e) { /* shutting down already */ }
        }
    }

    /** {@code run}'s pool, or null when the run does not pool browsers. */
    public static WorkerPool<?> pool(Object run) {
        if (run == null) return null;
        synchronized (RUNS) {
            Object[] e = RUNS.get(run);
            return e == null ? null : (WorkerPool<?>) e[0];
        }
    }

    /** Run end: quit its browsers. No-op when the run has no pool. */
    public static void end(Object run) {
        Object[] e;
        synchronized (RUNS) {
            e = RUNS.remove(run);
        }
        if (e != null) close(e);
    }

    static void endAll() {
        List<Object[]> all;
        synchronized (RUNS) {
            all = new ArrayList<Object[]>(RUNS.values());
            RUNS.clear();
        }
        for (Object[] e : all) close(e);
    }

    static int active() {
        synchronized (RUNS) { return RUNS.size(); }
    }

    private static void close(Object[] e) {
        try {
            ((WorkerPool<?>) e[0]).shutdown();
        } finally {
            if (e[1] != null) ((Runnable) e[1]).run();
        }
    }
}
