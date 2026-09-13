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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Logging posture for CLI runs (-executor / -selenium).
 * <ul>
 *  <li>Detailed log: every log4j logger, plus everything the engine prints to System.out/err (step traces, stack
 *      traces), goes to a per-run rolling file, {@code <gatf.log.dir|logs>/gatf.log}. The previous run's log is
 *      rolled to {@code gatf.1.log.gz} on start, and the last 10 runs are kept.</li>
 *  <li>Console: only the {@link RunConsole} pass/fail reporter plus WARN/ERROR log events.</li>
 * </ul>
 * Must run before log4j initialises, i.e. first thing in {@code main}. See log4j2.properties.
 * <p>Knobs (system properties, e.g. via GATF_JAVA_OPTS):
 * <ul>
 *  <li>{@code -Dgatf.log.dir=<dir>}</li>
 *  <li>{@code -Dgatf.log.level=debug|info|warn}</li>
 *  <li>{@code -Dgatf.console.level=info} (more console output)</li>
 *  <li>{@code -Dgatf.capture.stdout=false} (keep raw engine prints on the console)</li>
 *  <li>{@code -Dgatf.color=always|never|auto}</li>
 * </ul>
 */
public final class GatfLogging {

    private static volatile PrintStream realOut = System.out;
    private static volatile PrintStream realErr = System.err;
    private static volatile boolean captured = false;

    private GatfLogging() {}

    /** Quiet console + detailed file, for a CLI test run. Idempotent. */
    public static synchronized void initForRun() {
        if (System.getProperty("gatf.console.level") == null) {
            System.setProperty("gatf.console.level", "warn");
        }
        // Initialise log4j BEFORE redirecting: its status logger and configuration-time streams then bind to the
        // real terminal, so log4j itself can never write back into the captured streams (no feedback loop).
        LogManager.getLogger("gatf.stdout");
        if (!"false".equalsIgnoreCase(System.getProperty("gatf.capture.stdout")) && !captured) {
            realOut = System.out;
            realErr = System.err;
            Charset cs = System.out.charset();
            System.setOut(new PrintStream(new LineLogger("gatf.stdout", Level.INFO, realOut, cs), true, cs));
            System.setErr(new PrintStream(new LineLogger("gatf.stderr", Level.WARN, realErr, cs), true, cs));
            captured = true;
        }
    }

    /** The terminal's stdout, even after capture. RunConsole writes here. */
    public static PrintStream out() { return realOut; }

    /** The terminal's stderr, even after capture. */
    public static PrintStream err() { return realErr; }

    /** Absolute path of the current detailed log file, for display. */
    public static String logFile() {
        return new File(System.getProperty("gatf.log.dir", "logs"), "gatf.log").getAbsolutePath();
    }

    /** Line-buffers a captured stream into a file-only logger. Falls back to the real stream on re-entry, e.g. when
     *  something inside log4j writes to System.err while handling a line. That is defence in depth only:
 *  initForRun initialises log4j BEFORE the redirect, so log4j's own output (status logger, config errors) is bound
 *  to the real terminal and never re-enters these streams. The same ordering is what rules out a lock-order
 *  deadlock: PrintStream holds its own lock while calling write(), so a log4j appender that wrote to a captured
 *  stream while holding its lock could deadlock regardless of how this class locks. */
    static final class LineLogger extends OutputStream {
        private static final ThreadLocal<Boolean> IN_LOG = ThreadLocal.withInitial(() -> Boolean.FALSE);
        private final String loggerName;
        private final Level level;
        private final PrintStream fallback;
        private final Charset cs;
        private final ByteArrayOutputStream buf = new ByteArrayOutputStream(256);
        private volatile Logger logger;

        LineLogger(String loggerName, Level level, PrintStream fallback, Charset cs) {
            this.loggerName = loggerName;
            this.level = level;
            this.fallback = fallback;
            this.cs = cs;
        }

        @Override
        public synchronized void write(int b) {
            if (b == '\n') {
                emit(take());
            } else {
                buf.write(b);
            }
        }

        @Override
        public synchronized void write(byte[] b, int off, int len) {
            int start = off;
            for (int i = off; i < off + len; i++) {
                if (b[i] == '\n') {
                    buf.write(b, start, i - start);
                    emit(take());
                    start = i + 1;
                }
            }
            buf.write(b, start, off + len - start);
        }

        @Override
        public void flush() {
            // partial lines wait for their newline (print + println pairs stay on one log line)
        }

        private String take() {
            String line = buf.toString(cs);
            buf.reset();
            return !line.isEmpty() && line.charAt(line.length() - 1) == '\r' ? line.substring(0, line.length() - 1) : line;
        }

        private void emit(String line) {
            if (IN_LOG.get()) {
                fallback.println(line);
                return;
            }
            IN_LOG.set(Boolean.TRUE);
            try {
                Logger l = logger;
                if (l == null) {
                    logger = l = LogManager.getLogger(loggerName);
                }
                l.log(level, line);
            } catch (Throwable t) {
                fallback.println(line);
            } finally {
                IN_LOG.set(Boolean.FALSE);
            }
        }
    }
}
