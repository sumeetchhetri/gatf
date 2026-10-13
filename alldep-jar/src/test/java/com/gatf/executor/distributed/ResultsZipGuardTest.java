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
package com.gatf.executor.distributed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Δ145d: a node's results zip is extracted through an allow-list; hostile zips write nothing outside it. */
class ResultsZipGuardTest {

    @TempDir
    Path tmp;

    /** name → content; a name ending in '/' is a directory entry. */
    private static byte[] zip(String... nameContent) throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(bo)) {
            for (int i = 0; i < nameContent.length; i += 2) {
                z.putNextEntry(new ZipEntry(nameContent[i]));
                if (!nameContent[i].endsWith("/")) z.write(nameContent[i + 1].getBytes(StandardCharsets.UTF_8));
                z.closeEntry();
            }
        }
        return bo.toByteArray();
    }

    /** Mark the central-directory entry {@code name} as a Unix symlink (made by Unix, mode 0120777). */
    private static byte[] asSymlink(byte[] z, String name) {
        byte[] n = name.getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i + 46 < z.length; i++) {
            if (z[i] == 0x50 && z[i + 1] == 0x4b && z[i + 2] == 0x01 && z[i + 3] == 0x02) {
                int nl = (z[i + 28] & 0xFF) | (z[i + 29] & 0xFF) << 8;
                if (nl == n.length && Arrays.equals(Arrays.copyOfRange(z, i + 46, i + 46 + nl), n)) {
                    z[i + 5] = 3;
                    long ext = 0120777L << 16;
                    for (int k = 0; k < 4; k++) z[i + 38 + k] = (byte) (ext >>> (8 * k));
                    return z;
                }
            }
        }
        throw new AssertionError("no central entry " + name);
    }

    private static byte[] replaceAll(byte[] z, String from, String to) {
        byte[] f = from.getBytes(StandardCharsets.UTF_8), t = to.getBytes(StandardCharsets.UTF_8);
        assertEquals(f.length, t.length);
        for (int i = 0; i + f.length <= z.length; i++) {
            if (Arrays.equals(Arrays.copyOfRange(z, i, i + f.length), f)) System.arraycopy(t, 0, z, i, t.length);
        }
        return z;
    }

    private File write(String name, byte[] b) throws Exception {
        File f = tmp.resolve(name).toFile();
        Files.write(f.toPath(), b);
        return f;
    }

    private static List<String> listAll(Path p) throws Exception {
        try (Stream<Path> s = Files.walk(p)) {
            return s.map(x -> p.relativize(x).toString()).filter(x -> !x.isEmpty()).sorted().collect(Collectors.toList());
        }
    }

    @Test
    void namePolicy() {
        ResultsZipGuard.Policy api = ResultsZipGuard.apiReports(), sel = ResultsZipGuard.seleniumRun(1);
        assertNull(ResultsZipGuard.reject(api, "index.html"));
        assertNull(ResultsZipGuard.reject(api, "DRun-1-1-summary.csv"));
        assertNull(ResultsZipGuard.reject(sel, "DRun-1-1-selenium-index.html"));
        assertNull(ResultsZipGuard.reject(sel, "DRun-1"));
        for (String bad : new String[] { "../x.html", "..\\x.html", "/etc/x.html", "C:\\x.html", "C:x.html", "a/b.html", ".html",
                "..", ".x.html", "x.html.", "x.html ", "x\u0000.html", "x\n.html", "a|b.html", "x.html/", "", "gatf-config.xml",
                "run.sh", "x.HTML" }) {
            assertTrue(ResultsZipGuard.reject(api, bad) != null, bad);
        }
        for (String bad : new String[] { "DRun-10-1.html", "DRun-2-1.html", "index.html", "x/DRun-1.html", "DRun-1/../../x" }) {
            assertTrue(ResultsZipGuard.reject(sel, bad) != null, bad);
        }
        assertTrue(ResultsZipGuard.reject(api, new String(new char[256]).replace('\0', 'a') + ".html") != null);
    }

    @Test
    void ordinaryResultsExtractAsBefore() throws Exception {
        Path out = Files.createDirectories(tmp.resolve("out"));
        File z = write("r.zip", zip("index.html", "<h1/>", "DRun-1-summary.csv", "a,b"));
        ResultsZipGuard.Result r = ResultsZipGuard.extract(z, out.toFile(), ResultsZipGuard.apiReports(), 1 << 20, 100);
        assertEquals(Arrays.asList("index.html", "DRun-1-summary.csv"), r.extracted);
        assertTrue(r.rejected.isEmpty());
        assertFalse(r.stopped);
        assertEquals("<h1/>", new String(Files.readAllBytes(out.resolve("index.html")), StandardCharsets.UTF_8));
        assertEquals(Arrays.asList("DRun-1-summary.csv", "index.html"), listAll(out));   // no .part leftovers
    }

    @Test
    void hostileZipWritesNothingOutsideTheAllowList() throws Exception {
        Path base = Files.createDirectories(tmp.resolve("base"));
        Path out = Files.createDirectories(base.resolve("out"));
        byte[] b = zip("../escape.html", "x", "../../escape2.html", "x", "/tmp/abs-145d.html", "x", "sub/", "", "sub/in.html", "x",
                "gatf-config.xml", "<x/>", "evil.sh", "rm -rf /", "link.html", "/etc/passwd", "good.html", "ok",
                "dupe1.html", "first", "dupe2.html", "second");
        b = asSymlink(b, "link.html");
        b = replaceAll(b, "dupe2.html", "dupe1.html");
        File z = write("hostile.zip", b);
        ResultsZipGuard.Result r = ResultsZipGuard.extract(z, out.toFile(), ResultsZipGuard.apiReports(), 1 << 20, 100);
        assertEquals(Arrays.asList("good.html", "dupe1.html"), r.extracted, r.rejected.toString());
        assertEquals("first", new String(Files.readAllBytes(out.resolve("dupe1.html")), StandardCharsets.UTF_8));
        assertEquals(Arrays.asList("dupe1.html", "good.html"), listAll(out));
        assertEquals(Arrays.asList("hostile.zip"), listAll(tmp).stream().filter(x -> !x.startsWith("base")).collect(Collectors.toList()));
        assertFalse(new File("/tmp/abs-145d.html").exists());
        assertEquals(9, r.rejected.size(), r.rejected.toString());
        String all = String.join("\n", r.rejected);
        assertTrue(all.contains("'link.html': a symlink entry"), all);
        assertTrue(all.contains("'sub/': a directory entry"), all);
        assertTrue(all.contains("'dupe1.html': a duplicate entry"), all);
        assertTrue(all.contains("'../escape.html': not a top-level file"), all);
        assertTrue(all.contains("'evil.sh': not an expected API report file"), all);
    }

    @Test
    void seleniumZipKeepsOnlyThisNodesRun() throws Exception {
        Path out = Files.createDirectories(tmp.resolve("out"));
        File z = write("s.zip", zip("DRun-1-1-selenium-index.html", "a", "DRun-10-1-selenium-index.html", "b", "index.html", "c"));
        ResultsZipGuard.Result r = ResultsZipGuard.extract(z, out.toFile(), ResultsZipGuard.seleniumRun(1), 1 << 20, 100);
        assertEquals(Collections.singletonList("DRun-1-1-selenium-index.html"), r.extracted);
        assertEquals(2, r.rejected.size());
    }

    @Test
    void anExistingSymlinkAtTheTargetIsReplacedNotFollowed() throws Exception {
        Path out = Files.createDirectories(tmp.resolve("out"));
        Path outside = Files.write(tmp.resolve("outside.txt"), "keep".getBytes(StandardCharsets.UTF_8));
        try {
            Files.createSymbolicLink(out.resolve("index.html"), outside);
        } catch (UnsupportedOperationException | java.io.IOException e) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "symlinks unavailable here: " + e);
        }
        Files.createDirectories(out.resolve("dir.html"));
        File z = write("r.zip", zip("index.html", "new", "dir.html", "x"));
        ResultsZipGuard.Result r = ResultsZipGuard.extract(z, out.toFile(), ResultsZipGuard.apiReports(), 1 << 20, 100);
        assertEquals("keep", new String(Files.readAllBytes(outside), StandardCharsets.UTF_8));
        assertFalse(Files.isSymbolicLink(out.resolve("index.html")));
        assertEquals("new", new String(Files.readAllBytes(out.resolve("index.html")), StandardCharsets.UTF_8));
        assertTrue(String.join("", r.rejected).contains("would replace a directory"));
    }

    @Test
    void capsStopTheExtraction() throws Exception {
        Path out = Files.createDirectories(tmp.resolve("out"));
        char[] big = new char[5000];
        Arrays.fill(big, 'x');
        File z = write("big.zip", zip("a.html", "small", "b.html", new String(big), "c.html", "after"));
        ResultsZipGuard.Result r = ResultsZipGuard.extract(z, out.toFile(), ResultsZipGuard.apiReports(), 1000, 100);
        assertTrue(r.stopped);
        assertEquals(Collections.singletonList("a.html"), r.extracted);
        assertEquals(Collections.singletonList("a.html"), listAll(out));   // no partial b.html, no .part file

        Path out2 = Files.createDirectories(tmp.resolve("out2"));
        File z2 = write("many.zip", zip("a.html", "1", "b.html", "2", "c.html", "3"));
        ResultsZipGuard.Result r2 = ResultsZipGuard.extract(z2, out2.toFile(), ResultsZipGuard.apiReports(), 1 << 20, 2);
        assertTrue(r2.stopped);
        assertEquals(Arrays.asList("a.html", "b.html"), r2.extracted);
    }

    @Test
    void transferIsCappedAndGarbageIsRefused() throws Exception {
        File to = tmp.resolve("t.zip").toFile();
        assertTrue(ResultsZipGuard.copyCapped(new ByteArrayInputStream(new byte[100]), to, 100));
        assertEquals(100, to.length());
        assertFalse(ResultsZipGuard.copyCapped(new ByteArrayInputStream(new byte[101]), to, 100));
        assertFalse(to.exists());

        Path out = Files.createDirectories(tmp.resolve("out"));
        File junk = write("junk.zip", "not a zip at all".getBytes(StandardCharsets.UTF_8));
        ResultsZipGuard.Result r = ResultsZipGuard.extract(junk, out.toFile(), ResultsZipGuard.apiReports(), 1 << 20, 100);
        assertTrue(r.stopped);
        assertTrue(r.extracted.isEmpty());
        assertTrue(listAll(out).isEmpty());
    }

    @Test
    void receiveLogsNamesOnlyClippedAndBounded() throws Exception {
        Path out = Files.createDirectories(tmp.resolve("out"));
        List<String> args = new ArrayList<String>();
        for (int i = 0; i < 30; i++) {
            args.add("../e" + i + ".html");
            args.add("x");
        }
        args.add("x\u001b[31mred" + new String(new char[300]).replace('\0', 'y') + ".sh");
        args.add("x");
        byte[] b = zip(args.toArray(new String[0]));
        List<String> log = new ArrayList<String>();
        ResultsZipGuard.Result r = ResultsZipGuard.receive(new ByteArrayInputStream(b), tmp.resolve("in.zip").toFile(), out.toFile(),
                ResultsZipGuard.apiReports(), "node-1", log::add);
        assertEquals(31, r.rejected.size());
        assertEquals(ResultsZipGuard.MAX_LOGGED + 1, log.size(), log.toString());
        assertTrue(log.get(log.size() - 1).contains("0 file(s) extracted, 31 entr(y/ies) rejected"), log.toString());
        for (String l : log) {
            assertFalse(l.contains("\u001b"), l);
            assertTrue(l.length() < 400, l);
        }
        assertTrue(listAll(out).isEmpty());
    }

    @Test
    void maxBytesComesFromPropertyThenEnv() {
        Map<String, String> env = new HashMap<String, String>();
        Properties p = new Properties();
        assertEquals(ResultsZipGuard.DEFAULT_MAX_BYTES, ResultsZipGuard.maxBytes(env, p));
        env.put("GATF_DISTRIBUTED_RESULTS_MAX_BYTES", "2048");
        assertEquals(2048, ResultsZipGuard.maxBytes(env, p));
        p.setProperty("gatf.distributed.resultsMaxBytes", "4096");
        assertEquals(4096, ResultsZipGuard.maxBytes(env, p));
        p.setProperty("gatf.distributed.resultsMaxBytes", "-1");
        assertEquals(2048, ResultsZipGuard.maxBytes(env, p));
        env.put("GATF_DISTRIBUTED_RESULTS_MAX_BYTES", "lots");
        assertEquals(ResultsZipGuard.DEFAULT_MAX_BYTES, ResultsZipGuard.maxBytes(env, p));
    }
}
