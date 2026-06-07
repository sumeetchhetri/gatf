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
