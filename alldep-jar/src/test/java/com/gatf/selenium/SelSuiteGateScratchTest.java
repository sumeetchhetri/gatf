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

import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** H1 (Δ152): the generator gate's scratch dir: private, removed completely, symlinks never followed. */
public class SelSuiteGateScratchTest {

    @Test
    void scratchIsPrivateAndDeletedWithoutFollowingLinks(@TempDir Path dir) throws Exception {
        Path s = SelSuiteGate.scratch();
        assertTrue(s.getFileName().toString().startsWith("gatf-gen-"));
        if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(s)));
        }
        Path keep = Files.createDirectories(dir.resolve("keep"));
        Files.write(keep.resolve("precious.txt"), "x".getBytes(StandardCharsets.UTF_8));
        Files.createDirectories(s.resolve("sel/a/b"));
        Files.write(s.resolve("sel/a/b/x.sel"), "open chrome".getBytes(StandardCharsets.UTF_8));
        try {
            Files.createSymbolicLink(s.resolve("sel/link"), keep);
            Files.createSymbolicLink(s.resolve("filelink"), keep.resolve("precious.txt"));
        } catch (UnsupportedOperationException | java.nio.file.FileSystemException e) {
            // no symlinks here: the tree is still deleted
        }
        SelSuiteGate.delete(s);
        assertFalse(Files.exists(s, LinkOption.NOFOLLOW_LINKS));
        assertTrue(Files.isRegularFile(keep.resolve("precious.txt")), "a linked dir's contents are never deleted");
        SelSuiteGate.delete(s);          // already gone: no error
        SelSuiteGate.delete(null);
    }
}
