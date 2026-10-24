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
package com.gatf.log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import org.junit.jupiter.api.Test;

import com.gatf.log.RunConsole.Status;
import com.gatf.selenium.context.WorkerPool;

/**
 * H3 (Δ154): RunConsole stays line-atomic while four workers report results and the worker pool logs its context and
 * fallback lines on the same stream. Shim-runnable (Assertions only).
 */
class RunConsoleWorkerContextsTest {

    @Test
    void resultLinesAndPoolLinesNeverInterleave() throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        final PrintStream ps = new PrintStream(buf, true, "UTF-8");
        RunConsole.sink = ps;
        System.setProperty("gatf.color", "never");   // Δ163d: GITHUB_ACTIONS turns colour on; this test reads plain lines
        try {
            RunConsole.activate();
            RunConsole.suite("UI (seleasy)", "target/runconsole-test", 4, true);
            final WorkerPool<Object> pool = new WorkerPool<Object>(new WorkerPool.Port<Object>() {
                public boolean alive(Object b) { return true; }
                public List<String> contexts(Object b) { return new ArrayList<String>(); }
                public void dispose(Object b, String c) { }
                public void quit(Object b) { }
            }, 50, ps::println);
            final int threads = 4, per = 200;
            final CountDownLatch go = new CountDownLatch(1);
            List<Thread> ts = new ArrayList<Thread>();
            for (int t = 0; t < threads; t++) {
                final int w = t;
                Thread th = new Thread(() -> {
                    try { go.await(); } catch (InterruptedException e) { return; }
                    for (int i = 0; i < per; i++) {
                        if (i % 10 == 0) ps.println("browserContexts: opened bidi context ctx-" + w + "-" + i);
                        if (i == 7) pool.fallbackRun("worker " + w + " says so");
                        boolean fail = i % 6 == 0;
                        RunConsole.result("w" + w + ".sel", "t" + i, "w" + w + ".sel › t" + i, fail ? Status.FAILED : Status.PASSED, 3,
                                fail ? "boom " + w + "/" + i : null, null);
                    }
                }, "worker-" + t);
                ts.add(th);
                th.start();
            }
            go.countDown();
            for (Thread th : ts) th.join();
            String[] lines = buf.toString(StandardCharsets.UTF_8.name()).split("\\R");
            int results = 0, ctx = 0, fallback = 0;
            for (int i = 0; i < lines.length; i++) {
                String l = lines[i];
                if (l.contains("browserContexts:")) {
                    assertTrue(l.startsWith("browserContexts: "), "a pool line is whole: " + l);
                    if (l.contains("opened bidi context")) { assertTrue(l.matches("browserContexts: opened bidi context ctx-\\d+-\\d+"), l); ctx++; }
                    if (l.contains("launch their own browser")) fallback++;
                    continue;
                }
                if (l.contains(" › t") || l.contains(" > t")) {
                    results++;
                    assertTrue(l.matches(".*w\\d+\\.sel (›|>) t\\d+ \\(3ms\\)$"), "a result line is whole: " + l);
                    if (l.contains("✘") || l.trim().startsWith("x ")) assertTrue(lines[i + 1].contains("boom "), "its error right below: " + lines[i + 1]);
                }
            }
            assertEquals(threads * per, results);
            assertEquals(threads * per / 10, ctx);
            assertEquals(1, fallback, "the fallback is said once");
            RunConsole.finish();
        } finally {
            RunConsole.sink = null;
            System.clearProperty("gatf.color");
        }
    }
}
