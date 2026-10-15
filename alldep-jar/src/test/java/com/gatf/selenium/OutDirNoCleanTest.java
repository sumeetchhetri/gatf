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
package com.gatf.selenium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.gatf.executor.core.AcceptanceTestContext;
import com.gatf.executor.core.GatfExecutorConfig;
import com.gatf.selenium.SeleniumTest.FailureException;
import com.gatf.selenium.SeleniumTest.SeleniumTestResult;

/**
 * Δ163h: a second JVM (the debugger's debuggee, -validate-sel, the generate gate) must not clean the out dir that a
 * live run is writing to; and a sub-test failure prints its stack trace once, not three times.
 */
class OutDirNoCleanTest {

    @TempDir
    Path tmp;

    private AcceptanceTestContext context(Path work) throws Exception {
        GatfExecutorConfig config = Command.getConfig(work.resolve("gatf-config.xml").toString(), work.toString());
        config.setTestCasesBasePath(work.toString());
        config.setOutFilesBasePath(work.toString());
        AcceptanceTestContext ctx = new AcceptanceTestContext();
        ctx.setGatfExecutorConfig(config);
        return ctx;
    }

    private Path dogfood() throws IOException {
        Path src = Paths.get("../dogfood").toAbsolutePath().normalize();
        Path work = tmp.resolve("dogfood");
        try (Stream<Path> w = Files.walk(src)) {
            for (Path p : (Iterable<Path>) w::iterator) {
                Path rel = src.relativize(p);
                if (rel.getNameCount() > 0 && rel.getName(0).toString().equals("out")) continue;   // a local run's out/ is not part of the project
                Path t = work.resolve(rel.toString());
                if (Files.isDirectory(p)) Files.createDirectories(t);
                else Files.copy(p, t);
            }
        }
        return work;
    }

    @Test
    void aValidateThatMustNotCleanKeepsALiveRunsReports() throws Exception {
        Path work = dogfood();
        Path live = work.resolve("out").resolve("749021159845833");   // a run id dir, as GatfTestCaseExecutorUtil makes it
        Files.createDirectories(live);
        Path report = live.resolve("LRun-1-1-1-1-chrome-0.html");
        Files.write(report, "<html/>".getBytes(StandardCharsets.UTF_8));
        Path legacy = work.resolve("out").resolve("0123456789abcdef0123456789abcdef0123");   // 36 chars: the old cleanup's target
        Files.createDirectories(legacy);

        context(work).validateAndInit(true, false);

        assertTrue(Files.exists(report), "the live run's report was deleted");
        assertTrue(Files.isDirectory(legacy), "cleanOut=false deletes nothing");
    }

    @Test
    void aValidateThatMustNotCleanStillCreatesAMissingOutDir() throws Exception {
        Path work = dogfood();
        assertFalse(Files.exists(work.resolve("out")));
        context(work).validateAndInit(true, false);
        assertTrue(Files.isDirectory(work.resolve("out")));
    }

    @Test
    void aSubTestFailurePrintsItsStackOnceThenALine() {
        RuntimeException raw = new RuntimeException("unknown error: net::ERR_NAME_NOT_RESOLVED\n  (Session info: chrome=153)\nBuild info: x");
        assertTrue(SeleniumTestResult.stackAlreadyPrinted(new FailureException(raw)));
        // a GatfRunTimeError cause, a checked one and a bare cause were not printed before: they keep their stack trace
        assertFalse(SeleniumTestResult.stackAlreadyPrinted(new FailureException(new FailureException(raw))));
        assertFalse(SeleniumTestResult.stackAlreadyPrinted(new FailureException(new IOException("x"))));
        assertFalse(SeleniumTestResult.stackAlreadyPrinted(raw));
        assertEquals("unknown error: net::ERR_NAME_NOT_RESOLVED", SeleniumTestResult.firstLine(raw.getMessage()));
        assertEquals("one line", SeleniumTestResult.firstLine("one line"));
        assertEquals("", SeleniumTestResult.firstLine(null));
    }
}
