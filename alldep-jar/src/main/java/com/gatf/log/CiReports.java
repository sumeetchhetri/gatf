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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.gatf.log.RunConsole.Rec;
import com.gatf.log.RunConsole.Status;

/**
 * Q6: CI reporters for CLI runs, written at the end of the run from RunConsole's per-test records.
 * <ul>
 *  <li>{@code gatf-junit.xml}: JUnit XML, one testsuite per test file. Flaky tests are reported as passed, with a
 *      Surefire-style {@code <flakyFailure>} (understood by Jenkins, GitLab, and the GitHub JUnit report actions).</li>
 *  <li>{@code gatf-results.json}: summary counts, plus every test with its status, attempts, time and error.</li>
 *  <li>GitHub Actions ({@code GITHUB_ACTIONS=true}): one {@code ::error} annotation per failed test and one
 *      {@code ::warning} per flaky test, written to stdout so they appear on the run's summary page.</li>
 * </ul>
 * Written to the run's out dir. {@code -Dgatf.reporters=junit,json,github} selects them (default: all; "none" disables).
 */
final class CiReports {

    private CiReports() {}

    /** Writes the enabled reports; returns display lines ("JUnit:  /path/…") for the summary. Never throws. */
    static List<String> write(List<Rec> recs, String dir, long totalMs) {
        List<String> shown = new ArrayList<String>();
        if (recs.isEmpty()) return shown;   // load / multi-user runs: the HTML report covers them
        String sel = System.getProperty("gatf.reporters", "junit,json,github").toLowerCase(Locale.ROOT);
        if (sel.contains("none")) return shown;
        File out = new File(dir != null ? dir : ".");
        try {
            out.mkdirs();
            if (sel.contains("junit")) {
                File f = new File(out, "gatf-junit.xml");
                Files.write(f.toPath(), junitXml(recs, totalMs).getBytes(StandardCharsets.UTF_8));
                shown.add("JUnit:  " + f.getAbsolutePath());
            }
            if (sel.contains("json")) {
                File f = new File(out, "gatf-results.json");
                Files.write(f.toPath(), json(recs, totalMs).getBytes(StandardCharsets.UTF_8));
                shown.add("JSON:   " + f.getAbsolutePath());
            }
        } catch (IOException e) {
            shown.add("CI reports not written: " + e.getMessage());
        }
        if (sel.contains("github") && "true".equalsIgnoreCase(System.getenv("GITHUB_ACTIONS"))) {
            for (String a : githubAnnotations(recs)) GatfLogging.out().println(a);
        }
        return shown;
    }

    // ---- JUnit XML -------------------------------------------------------------------------------------------

    static String junitXml(List<Rec> recs, long totalMs) {
        Map<String, List<Rec>> bySuite = new LinkedHashMap<String, List<Rec>>();
        for (Rec r : recs) bySuite.computeIfAbsent(r.suite, k -> new ArrayList<Rec>()).add(r);
        int[] all = counts(recs);
        StringBuilder b = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        b.append("<testsuites name=\"gatf\" tests=\"").append(recs.size()).append("\" failures=\"").append(all[0])
                .append("\" skipped=\"").append(all[1]).append("\" time=\"").append(sec(totalMs)).append("\">\n");
        for (Map.Entry<String, List<Rec>> e : bySuite.entrySet()) {
            int[] c = counts(e.getValue());
            long ms = 0;
            for (Rec r : e.getValue()) ms += r.millis;
            b.append("  <testsuite name=\"").append(xml(e.getKey())).append("\" tests=\"").append(e.getValue().size())
                    .append("\" failures=\"").append(c[0]).append("\" skipped=\"").append(c[1])
                    .append("\" time=\"").append(sec(ms)).append("\">\n");
            for (Rec r : e.getValue()) {
                b.append("    <testcase name=\"").append(xml(r.name)).append("\" classname=\"").append(xml(r.suite))
                        .append("\" time=\"").append(sec(r.millis)).append("\"");
                Status st = r.last();
                if (st == Status.PASSED && !r.flaky()) {
                    b.append("/>\n");
                    continue;
                }
                b.append(">\n");
                if (st == Status.SKIPPED) {
                    b.append("      <skipped/>\n");
                } else if (st == Status.FAILED) {
                    if (r.soft > 0) {   // M2: soft-failure count as a testcase property (Jenkins/GitLab show properties)
                        b.append("      <properties><property name=\"softFailures\" value=\"").append(r.soft).append("\"/></properties>\n");
                    }
                    b.append("      <failure message=\"").append(xml(first(r.error))).append("\"")
                            .append(r.soft > 0 ? " type=\"SoftAssertionError\"" : "").append(">")
                            .append(xml(r.detail != null ? r.detail : nz(r.error))).append("</failure>\n");
                } else {   // flaky: passed on retry
                    b.append("      <flakyFailure message=\"failed ").append(r.attempts.size() - 1)
                            .append(" time(s) before passing\"/>\n");
                }
                if (!r.attachments.isEmpty()) {
                    // Q3: the Jenkins/GitLab JUnit attachment convention: [[ATTACHMENT|/abs/path]] in system-out
                    b.append("      <system-out>");
                    for (String a : r.attachments) b.append("[[ATTACHMENT|").append(xml(a)).append("]]\n");
                    b.append("</system-out>\n");
                }
                b.append("    </testcase>\n");
            }
            b.append("  </testsuite>\n");
        }
        return b.append("</testsuites>\n").toString();
    }

    // ---- JSON ------------------------------------------------------------------------------------------------

    static String json(List<Rec> recs, long totalMs) {
        int passed = 0, failed = 0, skipped = 0, flaky = 0, soft = 0;
        for (Rec r : recs) {
            soft += r.soft;
            if (r.flaky()) flaky++;
            else if (r.last() == Status.PASSED) passed++;
            else if (r.last() == Status.FAILED) failed++;
            else skipped++;
        }
        StringBuilder b = new StringBuilder("{\n  \"summary\": {");
        b.append("\"total\": ").append(recs.size()).append(", \"passed\": ").append(passed).append(", \"failed\": ").append(failed)
                .append(", \"flaky\": ").append(flaky).append(", \"skipped\": ").append(skipped)
                .append(", \"softFailures\": ").append(soft)
                .append(", \"durationMs\": ").append(totalMs).append("},\n  \"tests\": [");
        for (int i = 0; i < recs.size(); i++) {
            Rec r = recs.get(i);
            String st = r.flaky() ? "flaky" : r.last().name().toLowerCase(Locale.ROOT);
            b.append(i == 0 ? "\n" : ",\n").append("    {\"suite\": ").append(js(r.suite)).append(", \"name\": ").append(js(r.name))
                    .append(", \"title\": ").append(js(r.title)).append(", \"status\": ").append(js(st))
                    .append(", \"attempts\": ").append(r.attempts.size()).append(", \"durationMs\": ").append(r.millis)
                    .append(", \"error\": ").append(r.last() == Status.FAILED ? js(r.error) : "null")
                    .append(", \"softFailures\": ").append(r.soft)
                    .append(", \"attachments\": ").append(jsArr(r.attachments)).append("}");
        }
        return b.append(recs.isEmpty() ? "]\n}\n" : "\n  ]\n}\n").toString();
    }

    // ---- GitHub Actions annotations ----------------------------------------------------------------------------

    static List<String> githubAnnotations(List<Rec> recs) {
        List<String> out = new ArrayList<String>();
        for (Rec r : recs) {
            if (r.last() == Status.FAILED) {
                out.add("::error title=" + ghProp("GATF failed: " + r.title) + "::" + ghMsg(nz(r.error).isEmpty() ? "failed" : r.error));
            } else if (r.flaky()) {
                out.add("::warning title=" + ghProp("GATF flaky: " + r.title) + "::" + ghMsg("passed after " + (r.attempts.size() - 1) + " failed attempt(s)"));
            }
        }
        return out;
    }

    // ---- helpers -----------------------------------------------------------------------------------------------

    /** [final failures, skipped] (flaky counts as passed). */
    private static int[] counts(List<Rec> recs) {
        int f = 0, s = 0;
        for (Rec r : recs) {
            if (r.last() == Status.FAILED) f++;
            else if (r.last() == Status.SKIPPED) s++;
        }
        return new int[] { f, s };
    }

    private static String sec(long ms) { return String.format(Locale.ROOT, "%.3f", ms / 1000.0); }
    private static String nz(String s) { return s == null ? "" : s; }
    private static String first(String s) { return s == null ? "" : RunConsole.firstLine(s); }

    static String xml(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&': b.append("&amp;"); break;
                case '<': b.append("&lt;"); break;
                case '>': b.append("&gt;"); break;
                case '"': b.append("&quot;"); break;
                case '\'': b.append("&apos;"); break;
                default:
                    // XML 1.0 forbids most control chars (ANSI escapes in messages would break parsers)
                    if (c < 0x20 && c != '\n' && c != '\r' && c != '\t') b.append('?');
                    else b.append(c);
            }
        }
        return b.toString();
    }

    static String js(String s) {
        if (s == null) return "null";
        StringBuilder b = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                default:
                    if (c < 0x20) b.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    else b.append(c);
            }
        }
        return b.append('"').toString();
    }

    static String jsArr(List<String> l) {
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < l.size(); i++) b.append(i == 0 ? "" : ", ").append(js(l.get(i)));
        return b.append(']').toString();
    }

    /** GitHub workflow-command escaping (message part). */
    static String ghMsg(String s) { return s.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A"); }

    /** GitHub workflow-command escaping (property part, e.g. title=). */
    static String ghProp(String s) { return ghMsg(s).replace(":", "%3A").replace(",", "%2C"); }
}
