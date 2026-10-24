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
package com.gatf.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Δ163n bugs 4 + 8: rename inside a folder, '/' vs '\', traversal guards, zip-slip safe project upload. */
class TestFilePathsTest {

    @TempDir
    Path tmp;

    @Test
    void normalizeAcceptsBothSeparatorsAndRefusesEscapes() {
        assertThat(TestFilePaths.normalize("ui/lib/a.sel")).isEqualTo("ui/lib/a.sel");
        assertThat(TestFilePaths.normalize("ui\\lib\\a.sel")).isEqualTo("ui/lib/a.sel");
        assertThat(TestFilePaths.normalize("./ui//a.sel")).isEqualTo("ui/a.sel");
        for (String bad : new String[] { "../a.sel", "ui/../../a.sel", "ui\\..\\..\\a.sel", "/etc/a.sel", "\\\\host\\s\\a.sel", "C:\\x\\a.sel", "c:/a.sel", " ", "", "/" }) {
            assertThatThrownBy(() -> TestFilePaths.normalize(bad)).as(bad).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> TestFilePaths.normalize(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void renameComparesDirectoriesNotNames() {
        // the bug: 'ui/setup.sel' -> 'ui/setup2.sel' failed ("Source and Target filenames should be valid")
        assertThat(TestFilePaths.checkRename("ui/setup.sel", "ui/setup2.sel")).containsExactly("ui/setup.sel", "ui/setup2.sel");
        assertThat(TestFilePaths.checkRename("ui\\lib\\a.sel", "ui/lib/b.sel")).containsExactly("ui/lib/a.sel", "ui/lib/b.sel");
        assertThat(TestFilePaths.checkRename("a.xml", "b.xml")).containsExactly("a.xml", "b.xml");
        assertThat(TestFilePaths.checkRename("x/app.props", "x/app2.props")).containsExactly("x/app.props", "x/app2.props");
        assertThatThrownBy(() -> TestFilePaths.checkRename("ui/a.sel", "api/a.sel")).hasMessageContaining("keeps the file in its folder");
        assertThatThrownBy(() -> TestFilePaths.checkRename("a.sel", "ui/a.sel")).hasMessageContaining("keeps the file in its folder");
        assertThatThrownBy(() -> TestFilePaths.checkRename("ui/a.sel", "ui/../a.sel")).hasMessageContaining("'..'");
        assertThatThrownBy(() -> TestFilePaths.checkRename("ui/a.sel", "/tmp/a.sel")).hasMessageContaining("Absolute");
        assertThatThrownBy(() -> TestFilePaths.checkRename("ui/a.sel", "ui/a.xml")).hasMessageContaining("extension");
        assertThatThrownBy(() -> TestFilePaths.checkRename("ui/a.txt", "ui/b.txt")).hasMessageContaining("extension");
    }

    @Test
    void resolveStaysUnderTheBaseEvenThroughSymlinks() throws Exception {
        File base = Files.createDirectories(tmp.resolve("tests")).toFile();
        assertThat(TestFilePaths.resolve(base, "ui/a.sel")).isEqualTo(new File(new File(base, "ui"), "a.sel"));
        Path outside = Files.createDirectories(tmp.resolve("outside"));
        try {
            Files.createSymbolicLink(base.toPath().resolve("link"), outside);
        } catch (UnsupportedOperationException | java.io.IOException noSymlinks) {
            return;   // e.g. Windows without the privilege
        }
        assertThatThrownBy(() -> TestFilePaths.resolve(base, "link/a.sel")).hasMessageContaining("outside");
    }

    @Test
    void toClientUsesForwardSlashes() {
        assertThat(TestFilePaths.toClient("ui" + File.separator + "a.sel")).isEqualTo("ui/a.sel");
        assertThat(TestFilePaths.toClient("ui\\lib\\a.sel")).isEqualTo("ui/lib/a.sel");
        assertThat(TestFilePaths.toClient(null)).isNull();
    }

    private static byte[] zip(String... namesAndBodies) throws Exception {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(b)) {
            for (int i = 0; i < namesAndBodies.length; i += 2) {
                z.putNextEntry(new ZipEntry(namesAndBodies[i]));
                z.write(namesAndBodies[i + 1].getBytes(StandardCharsets.UTF_8));
                z.closeEntry();
            }
        }
        return b.toByteArray();
    }

    @Test
    void extractZipIsZipSlipSafeAndTypeFiltered() throws Exception {
        File base = Files.createDirectories(tmp.resolve("tests")).toFile();
        byte[] z = zip("proj/a.sel", "open chrome", "proj/api/b.xml", "<TestCases/>", "../evil.sel", "x", "/abs.sel", "y",
                "proj/run.sh", "rm -rf /", "proj\\win\\c.props", "k=v");
        TestFilePaths.Extracted r = TestFilePaths.extractZip(new ByteArrayInputStream(z), base, "up", false, 100, 1 << 20);
        assertThat(r.written).containsExactly("up/proj/a.sel", "up/proj/api/b.xml", "up/proj/win/c.props");
        assertThat(r.skipped).hasSize(3).anyMatch(s -> s.contains("evil")).anyMatch(s -> s.contains("run.sh"));
        assertThat(new File(base, "up/proj/a.sel")).hasContent("open chrome");
        assertThat(tmp.resolve("evil.sel")).doesNotExist();
        // an existing file is kept unless overwrite
        TestFilePaths.Extracted again = TestFilePaths.extractZip(new ByteArrayInputStream(zip("a.sel", "new")), base, "up/proj", false, 100, 1 << 20);
        assertThat(again.written).isEmpty();
        assertThat(new File(base, "up/proj/a.sel")).hasContent("open chrome");
        TestFilePaths.extractZip(new ByteArrayInputStream(zip("a.sel", "new")), base, "up/proj", true, 100, 1 << 20);
        assertThat(new File(base, "up/proj/a.sel")).hasContent("new");
    }

    @Test
    void extractZipIsBounded() throws Exception {
        File base = Files.createDirectories(tmp.resolve("tests")).toFile();
        assertThatThrownBy(() -> TestFilePaths.extractZip(new ByteArrayInputStream(zip("a.sel", "1", "b.sel", "2")), base, "", false, 1, 1 << 20))
                .hasMessageContaining("more than 1 files");
        assertThatThrownBy(() -> TestFilePaths.extractZip(new ByteArrayInputStream(zip("big.txt", "0123456789")), base, "", false, 10, 5))
                .hasMessageContaining("more than 5 bytes");
        assertThat(new File(base, "big.txt")).doesNotExist();   // no half-written file
    }

    @Test
    void aFailedOverwriteKeepsTheExistingFileAndAFailedZipLeavesNoNewFiles() throws Exception {
        // verifier round: a cap failure with overwrite deleted the user's file, and a capped zip left its first files
        File base = Files.createDirectories(tmp.resolve("tests")).toFile();
        File keep = new File(base, "keep.sel");
        Files.write(keep.toPath(), "original".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> TestFilePaths.copyCapped(new ByteArrayInputStream(new byte[100]), keep, 10, "too big"))
                .isInstanceOf(TestFilePaths.LimitException.class);
        assertThat(keep).hasContent("original");
        assertThat(base.list()).containsExactly("keep.sel");   // no temp file left
        assertThatThrownBy(() -> TestFilePaths.extractZip(new ByteArrayInputStream(zip("n1.sel", "1", "n2.sel", "2", "keep.sel", "x")), base, "", true, 2, 1 << 20))
                .isInstanceOf(TestFilePaths.LimitException.class);
        assertThat(new File(base, "n1.sel")).doesNotExist();
        assertThat(new File(base, "n2.sel")).doesNotExist();
        assertThat(keep).hasContent("original");
    }

    @Test
    void uploadableTypes() {
        for (String ok : new String[] { "a.sel", "a.XML", "a.props", "d.csv", "d.json", "n.txt" }) assertThat(TestFilePaths.uploadable(ok)).as(ok).isTrue();
        for (String no : new String[] { "a.sh", "a.jar", "a", "a.sel.exe", "dir.sel/x" }) assertThat(TestFilePaths.uploadable(no)).as(no).isFalse();
    }
}
