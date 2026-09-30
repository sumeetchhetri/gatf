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
package com.gatf.mcp;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.gatf.executor.core.TestFilter;

/**
 * M14 {@code list_tests}: the project's test files — seleasy scripts ({@code ui}, with header tags and subtests) and
 * API test-case XML ({@code api}, sniffed by root element) — relative to the project root. Pure: NIO + the pure
 * {@link TestFilter} tag reader.
 */
public final class TestLister {

    private static final Set<String> SKIP = new HashSet<String>(Arrays.asList("out", "reports", "logs"));
    private static final Pattern SUBTEST = Pattern.compile("^\\s*subtest\\s+\"([^\"]+)\"");
    private static final Pattern API_ROOT = Pattern.compile("<\\s*(TestCases|testcases|TestCase|testcase)\\b");

    private TestLister() {}

    /**
     * @param kind  "ui", "api" or null for both
     * @param grep  case-insensitive substring of the relative path or a tag; null = all
     */
    public static Map<String, Object> list(final Path root, final String kind, String grep, final int limit) throws IOException {
        if (root == null || !Files.isDirectory(root)) throw new McpTool.ToolError("not a directory: " + root);
        final String g = grep == null ? null : grep.toLowerCase(Locale.ROOT);
        final List<Map<String, Object>> tests = new ArrayList<Map<String, Object>>();
        final int[] matched = { 0 };
        Files.walkFileTree(root, java.util.EnumSet.noneOf(java.nio.file.FileVisitOption.class), 12, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes a) {
                if (d.equals(root)) return FileVisitResult.CONTINUE;
                String n = String.valueOf(d.getFileName());
                return RunResults.SKIP_DIRS.contains(n) || SKIP.contains(n) || n.startsWith(".") ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult visitFile(Path f, BasicFileAttributes a) throws IOException {
                String n = String.valueOf(f.getFileName());
                String rel = root.relativize(f).toString().replace('\\', '/');
                Map<String, Object> m = null;
                if (n.endsWith(".sel") && (kind == null || kind.equals("ui"))) {
                    List<String> lines = readLines(f);
                    List<String> subs = new ArrayList<String>();
                    for (String l : lines) { Matcher mm = SUBTEST.matcher(l); if (mm.find()) subs.add(mm.group(1)); }
                    m = new LinkedHashMap<String, Object>();
                    m.put("path", rel);
                    m.put("kind", "ui");
                    List<String> tags = TestFilter.selHeaderTags(lines);
                    if (!tags.isEmpty()) m.put("tags", tags);
                    if (!subs.isEmpty()) m.put("subtests", subs);
                } else if (n.endsWith(".xml") && (kind == null || kind.equals("api")) && !n.startsWith("gatf-") && !n.equals("pom.xml")
                        && isApiXml(f)) {
                    m = new LinkedHashMap<String, Object>();
                    m.put("path", rel);
                    m.put("kind", "api");
                }
                if (m == null) return FileVisitResult.CONTINUE;
                if (g != null && !(rel.toLowerCase(Locale.ROOT).contains(g) || String.valueOf(m.get("tags")).toLowerCase(Locale.ROOT).contains(g))) {
                    return FileVisitResult.CONTINUE;
                }
                matched[0]++;
                tests.add(m);
                return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult visitFileFailed(Path f, IOException e) { return FileVisitResult.CONTINUE; }
        });
        tests.sort((x, y) -> String.valueOf(x.get("path")).compareTo(String.valueOf(y.get("path"))));
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("root", root.toString());
        out.put("matched", matched[0]);
        out.put("tests", tests.size() > limit ? new ArrayList<Map<String, Object>>(tests.subList(0, limit)) : tests);
        if (matched[0] > limit) out.put("truncated", matched[0] - limit);
        return out;
    }

    /** UTF-8, falling back to ISO-8859-1 so one oddly encoded script cannot fail the whole listing. */
    static List<String> readLines(Path f) throws IOException {
        byte[] b = Files.readAllBytes(f);
        try {
            return Arrays.asList(StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(b)).toString().split("\\r?\\n", -1));
        } catch (java.nio.charset.CharacterCodingException e) {
            return Arrays.asList(new String(b, StandardCharsets.ISO_8859_1).split("\\r?\\n", -1));
        }
    }

    static boolean isApiXml(Path f) throws IOException {
        byte[] b = new byte[2048];
        int n;
        try (InputStream in = Files.newInputStream(f)) { n = in.read(b); }
        if (n <= 0) return false;
        String head = new String(b, 0, n, StandardCharsets.UTF_8);
        // skip the prolog and comments: the first real element decides
        head = head.replaceAll("(?s)<\\?.*?\\?>", "").replaceAll("(?s)<!--.*?-->", "").trim();
        Matcher m = API_ROOT.matcher(head);
        return m.find() && m.start() == 0;
    }
}
