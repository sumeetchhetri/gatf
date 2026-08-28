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
package com.gatf.selenium.trace;

/**
 * Lightweight periodic sampler: runs a task on a single daemon thread at a fixed interval
 * until stopped. Used by metric recorders to poll the browser on a cadence without pulling in
 * a scheduler. No {@code synchronized} — a dedicated thread plus a volatile flag, so it stays
 * clear of virtual-thread pinning. Task exceptions are swallowed so a bad sample never kills
 * the loop or the test run.
 *
 * @author Sumeet Chhetri
 */
public final class Sampler {
    private final long intervalMs;
    private final Runnable task;
    private volatile boolean running = false;
    private Thread thread;

    public Sampler(long intervalMs, Runnable task) {
        this.intervalMs = Math.max(100L, intervalMs);
        this.task = task;
    }

    public void start() {
        if (running) return;
        running = true;
        thread = new Thread(() -> {
            while (running) {
                try { Thread.sleep(intervalMs); } catch (InterruptedException e) { break; }
                if (!running) break;
                try { task.run(); } catch (Exception ignored) {}
            }
        }, "gatf-trace-sampler");
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        running = false;
        if (thread != null) thread.interrupt();
    }
}
