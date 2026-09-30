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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatf.log.RunConsole.Status;

/** M7: RunConsole stays line-atomic with parallel workers; results carry the worker lane + start offset. */
class RunConsoleWorkersTest {

    @AfterEach
    void reset() {
        RunConsole.sink = null;
        RunConsole.setShard(null);
    }

    @Test
    void parallelWorkersNeverInterleaveABlock() throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(buf, true, "UTF-8");
        RunConsole.sink = ps;
        RunConsole.activate();
        RunConsole.suite("UI (seleasy)", null, 8, true);
        int threads = 8, per = 150;
        CountDownLatch go = new CountDownLatch(1);
        List<Thread> ts = new ArrayList<Thread>();
        for (int t = 0; t < threads; t++) {
            final int w = t;
            Thread th = new Thread(() -> {
                try { go.await(); } catch (InterruptedException e) { return; }
                for (int i = 0; i < per; i++) {
                    String title = "w" + w + ".sel › t" + i;
                    // a failure prints 2 extra lines (error + attachment) that must stay under their result line
                    boolean fail = i % 5 == 0;
                    RunConsole.result("w" + w + ".sel", "t" + i, title, fail ? Status.FAILED : Status.PASSED, 3,
                            fail ? "boom " + w + "/" + i : null, null, fail ? List.of("/tmp/w" + w + "-" + i + ".png") : null);
                }
            }, "worker-" + t);
            ts.add(th);
            th.start();
        }
        // another writer on the same stream at the same time (e.g. a println from a step)
        Thread noise = new Thread(() -> { try { go.await(); } catch (InterruptedException e) { return; } for (int i = 0; i < 300; i++) ps.println("NOISE " + i); });
        noise.start();
        go.countDown();
        for (Thread th : ts) th.join();
        noise.join();

        String[] lines = buf.toString(StandardCharsets.UTF_8.name()).split("\\R");
        int results = 0;
        for (int i = 0; i < lines.length; i++) {
            String l = lines[i];
            if (l.matches("\\s+[✓✘x]|ok\\s.*") || l.contains(" › t") || l.contains(" > t")) {
                results++;
                assertThat(l).containsPattern("w\\d+\\.sel (›|>) t\\d+ \\(3ms\\)$");
                if (l.contains("✘") || l.trim().startsWith("x ")) {
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("w(\\d+)\\.sel (?:›|>) t(\\d+)").matcher(l);
                    assertThat(m.find()).isTrue();
                    String id = m.group(1) + "/" + m.group(2);
                    assertThat(lines[i + 1]).contains("boom " + id);                                   // error right below
                    assertThat(lines[i + 2]).contains("screenshot: /tmp/w" + m.group(1) + "-" + m.group(2) + ".png");
                }
            }
        }
        assertThat(results).isEqualTo(threads * per);

        // each worker thread got its own lane; offsets are non-negative
        Set<Integer> lanes = new HashSet<Integer>();
        String json = CiReports.json(new ArrayList<RunConsole.Rec>(recsSnapshot()), 1000);
        @SuppressWarnings("unchecked")
        Map<String, Object> root = new ObjectMapper().readValue(json, Map.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tests = (List<Map<String, Object>>) root.get("tests");
        for (Map<String, Object> t : tests) {
            lanes.add(((Number) t.get("worker")).intValue());
            assertThat(((Number) t.get("startMs")).longValue()).isGreaterThanOrEqualTo(0);
        }
        assertThat(lanes).hasSize(threads);
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) root.get("summary");
        assertThat(((Number) summary.get("workers")).intValue()).isEqualTo(8);
        RunConsole.setWorkers(3);   // the pool actually used wins over the configured value
        assertThat(RunConsole.workers()).isEqualTo(3);
        RunConsole.finish();
    }

    @Test
    void shardIsRecordedInTheResults() throws Exception {
        RunConsole.sink = new PrintStream(new ByteArrayOutputStream(), true, "UTF-8");
        RunConsole.activate();
        RunConsole.setShard("2/4");
        RunConsole.result("a.sel", "t", "a.sel › t", Status.PASSED, 5, null, null);
        @SuppressWarnings("unchecked")
        Map<String, Object> root = new ObjectMapper().readValue(CiReports.json(new ArrayList<RunConsole.Rec>(recsSnapshot()), 5), Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) root.get("summary");
        assertThat(summary.get("shard")).isEqualTo("2/4");
        RunConsole.finish();
    }

    private static java.util.Collection<RunConsole.Rec> recsSnapshot() throws Exception {
        java.lang.reflect.Field f = RunConsole.class.getDeclaredField("recs");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, RunConsole.Rec> m = (Map<String, RunConsole.Rec>) f.get(null);
        synchronized (RunConsole.class) { return new ArrayList<RunConsole.Rec>(m.values()); }
    }
}
