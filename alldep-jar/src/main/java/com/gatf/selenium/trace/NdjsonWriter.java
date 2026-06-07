package com.gatf.selenium.trace;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Append-only NDJSON writer: one JSON object per line, flushed to disk as events arrive so a
 * long-running trace never accumulates in memory (and survives a crash up to the last line).
 *
 * Thread-safe via a {@link ReentrantLock} rather than {@code synchronized} — deliberately, so
 * the recorder core stays free of pinning points when the runtime moves to virtual threads.
 *
 * @author Sumeet Chhetri
 */
public final class NdjsonWriter implements AutoCloseable {
    private final ReentrantLock lock = new ReentrantLock();
    private final Writer out;
    private volatile boolean closed = false;

    public NdjsonWriter(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        this.out = new BufferedWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8));
    }

    /** Append one already-rendered JSON object as a line. Best-effort; never throws upward. */
    public void writeLine(String jsonObject) {
        if (closed || jsonObject == null) return;
        lock.lock();
        try {
            out.write(jsonObject);
            out.write('\n');
        } catch (IOException e) {
            // best-effort: a dropped trace line must not break the test run
        } finally {
            lock.unlock();
        }
    }

    public void flush() {
        lock.lock();
        try { out.flush(); } catch (IOException ignored) {} finally { lock.unlock(); }
    }

    @Override
    public void close() {
        lock.lock();
        try { closed = true; out.flush(); out.close(); }
        catch (IOException ignored) {}
        finally { lock.unlock(); }
    }
}
