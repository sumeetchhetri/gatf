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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/** H6 (Δ156): git diff -U0 parsing, in old line numbers; whatever can't be read line by line is a whole-file change. */
class DiffParserTest {

    @Test
    void hunksInOldLineNumbers() {
        String p = "diff --git a/src/a.js b/src/a.js\nindex 1..2 100644\n--- a/src/a.js\n+++ b/src/a.js\n"
                + "@@ -3 +3 @@ function a() {\n-  x = 1;\n+  x = 2;\n"
                + "@@ -10,0 +11,2 @@\n+  y();\n+  z();\n"
                + "@@ -20,2 +22,0 @@\n--- a removed line that looks like a header\n-++ and another\n"
                + "\\ No newline at end of file\n";
        List<DiffParser.FileChange> c = DiffParser.parse(p);
        assertEquals(1, c.size());
        DiffParser.FileChange f = c.get(0);
        assertEquals("src/a.js", f.path);
        assertEquals(DiffParser.Status.MODIFIED, f.status);
        assertFalse(f.wholeFile, f.why);
        assertEquals(3, f.hunks.size());
        assertEquals("line 3", f.hunks.get(0).lines());
        assertEquals("  x = 1;", f.hunks.get(0).removed);
        assertEquals("  x = 2;", f.hunks.get(0).added);
        assertEquals(0, f.hunks.get(1).oldCount);
        assertEquals("after line 10", f.hunks.get(1).lines());
        assertEquals("  y();\n  z();", f.hunks.get(1).added);
        assertEquals("lines 20-21", f.hunks.get(2).lines());
        assertEquals("-- a removed line that looks like a header\n++ and another", f.hunks.get(2).removed);
    }

    @Test
    void addedDeletedBinaryModeAndQuotedPaths() {
        String p = "diff --git a/new.js b/new.js\nnew file mode 100644\nindex 0..1\n--- /dev/null\n+++ b/new.js\n@@ -0,0 +1 @@\n+x\n"
                + "diff --git a/old.js b/old.js\ndeleted file mode 100644\n--- a/old.js\n+++ /dev/null\n@@ -1 +0,0 @@\n-x\n"
                + "diff --git a/logo.png b/logo.png\nindex 1..2 100644\nBinary files a/logo.png and b/logo.png differ\n"
                + "diff --git a/run.sh b/run.sh\nold mode 100644\nnew mode 100755\n"
                + "diff --git \"a/sp ace\\tab.js\" \"b/sp ace\\tab.js\"\n--- \"a/sp ace\\tab.js\"\n+++ \"b/sp ace\\tab.js\"\n@@ -1 +1 @@\n-a\n+b\n"
                + "diff --git a/with space.js b/with space.js\n--- a/with space.js\t\n+++ b/with space.js\t\n@@ -2 +2 @@\n-a\n+b\n"
                + "diff --git a/café.js b/café.js\n--- \"a/caf\\303\\251.js\"\n+++ \"b/caf\\303\\251.js\"\n@@ -1 +1 @@\n-a\n+b\n";
        List<DiffParser.FileChange> c = DiffParser.parse(p);
        assertEquals(7, c.size(), c.toString());
        assertEquals(DiffParser.Status.ADDED, c.get(0).status);
        assertEquals("new.js", c.get(0).path);
        assertEquals(DiffParser.Status.DELETED, c.get(1).status);
        assertEquals("old.js", c.get(1).path);
        assertTrue(c.get(2).wholeFile);
        assertEquals("binary file", c.get(2).why);
        assertEquals("logo.png", c.get(2).path);
        assertTrue(c.get(3).wholeFile);
        assertEquals("run.sh", c.get(3).path);
        assertEquals("sp ace\tab.js", c.get(4).path);   // \t in git's quoting is a TAB
        assertEquals("with space.js", c.get(5).path);
        assertEquals("café.js", c.get(6).path);
    }

    @Test
    void oddInputIsWholeFileNeverDropped() {
        List<DiffParser.FileChange> c = DiffParser.parse("diff --git a/x.js b/x.js\n--- a/x.js\n+++ b/x.js\n@@ garbage @@\n-a\n");
        assertEquals(1, c.size());
        assertTrue(c.get(0).wholeFile);
        // a hunk shorter than its header says
        c = DiffParser.parse("diff --git a/x.js b/x.js\n--- a/x.js\n+++ b/x.js\n@@ -1,3 +1,3 @@\n-a\n+b\n");
        assertTrue(c.get(0).wholeFile, c.get(0).why);
        c = DiffParser.parse("diff --git a/m b/m\nindex 1..2 160000\n--- a/m\n+++ b/m\n@@ -1 +1 @@\n-Subproject commit 1\n+Subproject commit 2\n");
        assertEquals("submodule", c.get(0).why);
        assertTrue(DiffParser.parse("").isEmpty());
        assertTrue(DiffParser.parse(null).isEmpty());
        assertNull(DiffParser.unquote("\"bad\\q\""));
        assertEquals("a b", DiffParser.headerPath("diff --git a/a b b/a b"));
    }
}
