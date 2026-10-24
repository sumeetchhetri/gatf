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
package com.gatf.executor.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Δ163o sweep (AUDIT F12): the distributed worker's unzipZipFile must not write outside its target (zip slip). */
class UnzipSlipTest {

    @TempDir
    Path tmp;

    @Test
    void entriesThatEscapeTheTargetAreNotWritten() throws Exception {
        File zip = tmp.resolve("in.zip").toFile();
        try (ZipOutputStream z = new ZipOutputStream(new FileOutputStream(zip))) {
            for (String n : new String[] { "ok/a.txt", "../evil.txt", "ok/../../evil2.txt" }) {
                z.putNextEntry(new ZipEntry(n));
                z.write("x".getBytes(StandardCharsets.UTF_8));
                z.closeEntry();
            }
        }
        Path target = Files.createDirectories(tmp.resolve("t/gcdir"));
        ReportHandler.unzipZipFile(zip, target.toString());   // zip4j 2.11.5 refuses the whole archive (logged, no throw)
        assertThat(tmp.resolve("t/evil.txt")).doesNotExist();
        assertThat(tmp.resolve("evil2.txt")).doesNotExist();
        assertThat(tmp.resolve("t/evil2.txt")).doesNotExist();
    }
}
