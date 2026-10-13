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
import java.util.List;

import org.openqa.selenium.WebDriver;

import com.gatf.executor.core.GatfExecutorConfig;
import com.gatf.selenium.trace.BidiRaw;
import com.gatf.selenium.trace.Transports;

/**
 * H3 (Δ154): the WebDriver side of {@link WorkerPool} for {@code browserContexts: worker}: one pool per run, started
 * and ended by the executor around its run loop, and the {@link WorkerPool.Port} over a real driver. The per-script
 * context itself is the script's own {@link ContextScope} (SeleniumTest), so routes, emulation, clock, tabs and
 * storageState are scoped exactly as for Δ153's per-subtest contexts.
 */
public final class WorkerDrivers {

    private WorkerDrivers() {}

    /** Run start: pool browsers when the config says {@code browserContexts: worker}; otherwise nothing changes. */
    public static void begin(Object run, final GatfExecutorConfig cfg) {
        if (run == null || cfg == null || !ContextMode.perScript(cfg.getBrowserContexts())) return;
        final String traceProtocol = cfg.getTraceProtocol();
        int recycle = WorkerPool.recycleAfter(System.getProperty("gatf.contextRecycle"));
        WorkerPool<WebDriver> pool = new WorkerPool<WebDriver>(port(), recycle, m -> System.out.println(m));
        System.out.println("browserContexts: worker: one browser per worker, a fresh context per script"
                + (recycle > 0 ? " (relaunched every " + recycle + " scripts)" : ""));
        // a run that had to trace over BiDi (WorkerPool.trace) gets its configured protocol back at the end
        WorkerRuns.begin(run, pool, () -> cfg.setTraceProtocol(traceProtocol));
    }

    /** Run end: quit the run's worker browsers (no-op without a pool). */
    public static void end(Object run) {
        WorkerRuns.end(run);
    }

    /** {@code run}'s pool, or null. */
    @SuppressWarnings("unchecked")
    public static WorkerPool<WebDriver> pool(Object run) {
        return (WorkerPool<WebDriver>) WorkerRuns.pool(run);
    }

    /** Local Chromium and Firefox get a WebDriver BiDi socket in worker mode (contexts, scoped routes, BiDi traces). */
    public static boolean wantsBidi(String browserKey) {
        return "chrome".equals(browserKey) || "edge".equals(browserKey) || "opera".equals(browserKey) || "firefox".equals(browserKey);
    }

    /** Why {@code d} cannot host per-script contexts, or null when it can. */
    public static String refusal(WebDriver d) {
        ContextMatrix.Decision dec = ContextMatrix.decide(System.getProperty("gatf.contextProtocol"), Transports.hasCdp(d), BidiRaw.available(d));
        return dec.supported() ? null : dec.reason;
    }

    static WorkerPool.Port<WebDriver> port() {
        return new WorkerPool.Port<WebDriver>() {
            public boolean alive(WebDriver d) {
                try {
                    return d.getWindowHandles() != null;
                } catch (RuntimeException e) {
                    return false;
                }
            }
            public List<String> contexts(WebDriver d) {
                ContextOps o = ops(d);
                if (o == null) return new ArrayList<String>();
                try {
                    return o.list();
                } finally {
                    o.close();
                }
            }
            public void dispose(WebDriver d, String context) {
                ContextOps o = ops(d);
                if (o == null) return;
                try {
                    o.remove(context);
                } finally {
                    o.close();
                }
            }
            public void quit(WebDriver d) {
                try { d.quit(); } catch (RuntimeException e) { /* already gone */ }
            }
        };
    }

    private static ContextOps ops(WebDriver d) {
        ContextMatrix.Decision dec = ContextMatrix.decide(System.getProperty("gatf.contextProtocol"), Transports.hasCdp(d), BidiRaw.available(d));
        for (String p : new String[] { dec.protocol, dec.alternate }) {
            if (p == null) continue;
            try {
                ContextOps o = Transports.contextOps(d, p);
                if (o != null) return o;
            } catch (RuntimeException e) { /* that channel is not usable */ }
        }
        return null;
    }
}
