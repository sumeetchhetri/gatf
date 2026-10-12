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
package com.gatf.coverage;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * H6 (Δ156): parses {@code git diff --no-renames -U0 --src-prefix=a/ --dst-prefix=b/} output into changed files and
 * their hunks, in the OLD file's line numbers (the build the coverage was taken from). Pure.
 *
 * <p>Anything it can't read line by line (binary, mode-only, submodule, an unparsable hunk header) becomes a
 * whole-file change, never a dropped one.
 *
 * @author Sumeet Chhetri
 */
public final class DiffParser {

    private DiffParser() {}

    public enum Status { ADDED, MODIFIED, DELETED }

    /** One contiguous change: old lines {@code [oldStart, oldStart+oldCount)}; oldCount 0 = an insertion after oldStart. */
    public static final class Hunk {
        public final int oldStart, oldCount, newStart, newCount;
        public final String removed, added;   // the hunk's '-' and '+' lines, joined by \n

        Hunk(int oldStart, int oldCount, int newStart, int newCount, String removed, String added) {
            this.oldStart = oldStart;
            this.oldCount = oldCount;
            this.newStart = newStart;
            this.newCount = newCount;
            this.removed = removed;
            this.added = added;
        }

        /** "12-14" / "12" / "after 12" for messages (old line numbers). */
        public String lines() {
            if (oldCount == 0) return "after line " + oldStart;
            return oldCount == 1 ? "line " + oldStart : "lines " + oldStart + "-" + (oldStart + oldCount - 1);
        }
    }

    public static final class FileChange {
        public final String path;            // repo-relative, '/' separated: the old path, or the new one for an added file
        public final Status status;
        public final boolean wholeFile;      // binary, mode-only, submodule or unreadable: no line information
        public final String why;             // why wholeFile, else null
        public final List<Hunk> hunks;

        public FileChange(String path, Status status, boolean wholeFile, String why, List<Hunk> hunks) {
            this.path = path;
            this.status = status;
            this.wholeFile = wholeFile;
            this.why = why;
            this.hunks = hunks == null ? Collections.<Hunk>emptyList() : Collections.unmodifiableList(hunks);
        }

        @Override public String toString() { return status + " " + path; }
    }

    private static final Pattern HUNK = Pattern.compile("^@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@.*$");

    /** Parses a whole {@code git diff} patch. Never throws for odd input: what it can't read is a whole-file change. */
    public static List<FileChange> parse(String patch) {
        List<FileChange> out = new ArrayList<>();
        if (patch == null || patch.isEmpty()) return out;
        String[] lines = patch.split("\n", -1);
        int i = 0;
        while (i < lines.length) {
            String l = strip(lines[i]);
            if (!l.startsWith("diff --git ")) { i++; continue; }
            String header = l;
            i++;
            Status status = Status.MODIFIED;
            String oldPath = null, newPath = null;
            boolean binary = false, submodule = false, badHunk = false;
            List<Hunk> hunks = new ArrayList<>();
            while (i < lines.length && !strip(lines[i]).startsWith("diff --git ")) {
                String x = strip(lines[i]);
                if (x.startsWith("new file mode")) status = Status.ADDED;
                else if (x.startsWith("deleted file mode")) status = Status.DELETED;
                else if (x.startsWith("Binary files ") || x.equals("GIT binary patch")) binary = true;
                else if (x.startsWith("--- ")) oldPath = path(x.substring(4), "a/");
                else if (x.startsWith("+++ ")) newPath = path(x.substring(4), "b/");
                else if (x.startsWith("@@")) {
                    Matcher m = HUNK.matcher(x);
                    if (!m.matches()) { badHunk = true; i++; continue; }
                    int os = Integer.parseInt(m.group(1)), oc = m.group(2) == null ? 1 : Integer.parseInt(m.group(2));
                    int ns = Integer.parseInt(m.group(3)), nc = m.group(4) == null ? 1 : Integer.parseInt(m.group(4));
                    StringBuilder rem = new StringBuilder(), add = new StringBuilder();
                    int j = i + 1, nr = 0, na = 0;
                    // count-bounded: a removed line "-- x" shows as "--- x" and must not be read as a header
                    for (; j < lines.length; j++) {
                        String y = lines[j];
                        if (y.startsWith("\\")) continue;                // "\ No newline at end of file"
                        if (nr < oc && y.startsWith("-")) { append(rem, strip(y.substring(1))); nr++; }
                        else if (na < nc && y.startsWith("+")) { append(add, strip(y.substring(1))); na++; }
                        else if (y.startsWith(" ") && (nr < oc || na < nc)) continue;   // context (not with -U0)
                        else break;
                    }
                    if (nr != oc || na != nc) badHunk = true;
                    if (y(lines, i + 1, j).contains("Subproject commit")) submodule = true;
                    hunks.add(new Hunk(os, oc, ns, nc, rem.toString(), add.toString()));
                    i = j;
                    continue;
                }
                i++;
            }
            String p = status == Status.ADDED ? newPath : oldPath;
            if (p == null) p = status == Status.ADDED ? oldPath : newPath;
            if (p == null) p = headerPath(header);
            if (p == null) continue;   // nothing usable at all: the caller compares with --name-only and fails safe
            String why = binary ? "binary file" : submodule ? "submodule" : badHunk ? "unreadable hunk header"
                    : hunks.isEmpty() ? "no line changes (mode or empty file)" : null;
            out.add(new FileChange(p, status, why != null, why, why != null ? null : hunks));
        }
        return out;
    }

    private static String y(String[] lines, int from, int to) {
        StringBuilder b = new StringBuilder();
        for (int k = from; k < to && k < lines.length; k++) b.append(lines[k]).append('\n');
        return b.toString();
    }

    private static void append(StringBuilder b, String s) {
        if (b.length() > 0) b.append('\n');
        b.append(s);
    }

    private static String strip(String s) {
        return s.endsWith("\r") ? s.substring(0, s.length() - 1) : s;
    }

    // "--- a/x" / "+++ b/x" / "/dev/null"; git appends a TAB when the name has a space, and quotes unusual names
    static String path(String raw, String prefix) {
        String s = raw;
        if (s.endsWith("\t")) s = s.substring(0, s.length() - 1);
        if (s.equals("/dev/null")) return null;
        if (s.startsWith("\"")) s = unquote(s);
        if (s == null) return null;
        return s.startsWith(prefix) ? s.substring(prefix.length()) : s;
    }

    // "diff --git a/X b/X" (no renames: both sides are the same path)
    static String headerPath(String header) {
        String rest = header.substring("diff --git ".length());
        if (rest.startsWith("\"")) {
            int end = closingQuote(rest);
            if (end < 0) return null;
            String a = unquote(rest.substring(0, end + 1));
            return a != null && a.startsWith("a/") ? a.substring(2) : a;
        }
        if (!rest.startsWith("a/")) return null;
        int n = rest.length() - "a/".length() - " b/".length();
        if (n <= 0 || n % 2 != 0) return null;
        String a = rest.substring(2, 2 + n / 2);
        return rest.substring(2 + n / 2).equals(" b/" + a) ? a : null;
    }

    private static int closingQuote(String s) {
        for (int k = 1; k < s.length(); k++) {
            char c = s.charAt(k);
            if (c == '\\') { k++; continue; }
            if (c == '"') return k;
        }
        return -1;
    }

    /** git's C-style quoting: \t \n \" \\ and \ooo octal bytes (UTF-8). Null when malformed. */
    static String unquote(String q) {
        if (q.length() < 2 || !q.startsWith("\"") || !q.endsWith("\"")) return null;
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        for (int k = 1; k < q.length() - 1; k++) {
            char c = q.charAt(k);
            if (c != '\\') {
                int cp = q.codePointAt(k);
                byte[] u = new String(Character.toChars(cp)).getBytes(StandardCharsets.UTF_8);
                b.write(u, 0, u.length);
                if (Character.charCount(cp) == 2) k++;
                continue;
            }
            if (++k >= q.length() - 1) return null;
            char e = q.charAt(k);
            switch (e) {
                case 'n': b.write('\n'); break;
                case 't': b.write('\t'); break;
                case 'r': b.write('\r'); break;
                case 'a': b.write(7); break;
                case 'b': b.write('\b'); break;
                case 'f': b.write('\f'); break;
                case 'v': b.write(11); break;
                case '"': b.write('"'); break;
                case '\\': b.write('\\'); break;
                default:
                    if (e >= '0' && e <= '3' && k + 2 < q.length() - 1 && octal(q.charAt(k + 1)) && octal(q.charAt(k + 2))) {
                        int v = Integer.parseInt(q.substring(k, k + 3), 8);
                        b.write(v);
                        k += 2;
                    } else {
                        return null;
                    }
            }
        }
        return new String(b.toByteArray(), StandardCharsets.UTF_8);
    }

    private static boolean octal(char c) { return c >= '0' && c <= '7'; }
}
