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

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Δ163o (AUDIT F11): dist-traces keep the newest runs only. */
class DistributedTracesPruneTest {

    @TempDir
    Path tmp;

    private Path run(Path store, String name, long mtime) throws Exception {
        Path d = Files.createDirectories(store.resolve(name).resolve("node-1"));
        Files.write(d.resolve("a-trace.zip"), new byte[] { 1 });
        Files.setLastModifiedTime(store.resolve(name), FileTime.fromMillis(mtime));
        return store.resolve(name);
    }

    @Test
    void oldestRunsBeyondKeepAreDeletedNeverTheCurrentOne() throws Exception {
        Path store = Files.createDirectories(tmp.resolve("dist-traces"));
        run(store, "r1", 1000);
        run(store, "r2", 2000);
        run(store, "r3", 3000);
        Path cur = run(store, "cur", 500);   // oldest mtime, but it is the current run
        assertThat(DistributedTraces.prune(store, "cur", 2)).isEqualTo(2);
        assertThat(store.resolve("r1")).doesNotExist();
        assertThat(store.resolve("r2")).doesNotExist();
        assertThat(store.resolve("r3")).exists();
        assertThat(cur).exists();
        assertThat(DistributedTraces.prune(store, "cur", 50)).isZero();
    }

    @Test
    void symlinkedRunsAreNotFollowed() throws Exception {
        Path store = Files.createDirectories(tmp.resolve("dist-traces"));
        Path outside = Files.createDirectories(tmp.resolve("keep-me"));
        Files.write(outside.resolve("x"), new byte[] { 1 });
        try {
            Files.createSymbolicLink(store.resolve("old"), outside);
        } catch (UnsupportedOperationException | java.io.IOException noSymlinks) {
            return;
        }
        run(store, "cur", 1);
        DistributedTraces.prune(store, "cur", 1);
        assertThat(outside.resolve("x")).exists();
    }

    @Test
    void storeDirPrunesWhenANewRunStarts() throws Exception {
        System.setProperty("gatf.distTraces.keep", "1");
        try {
            DistributedTraces.storeDir(tmp.toFile(), "run-a", "n1");
            DistributedTraces.storeDir(tmp.toFile(), "run-b", "n1");
            assertThat(tmp.resolve("dist-traces").toFile().list()).hasSize(1);
        } finally {
            System.clearProperty("gatf.distTraces.keep");
        }
    }

    @Test
    void aSymlinkedStoreIsNeverPruned() throws Exception {
        // verifier round: out/dist-traces -> victim/ was pruned (55 -> 50 dirs) before the escape check refused it
        Path victim = Files.createDirectories(tmp.resolve("victim"));
        for (int i = 0; i < 55; i++) Files.createDirectories(victim.resolve("d" + i));
        Path out = Files.createDirectories(tmp.resolve("out"));
        try {
            Files.createSymbolicLink(out.resolve("dist-traces"), victim);
        } catch (UnsupportedOperationException | java.io.IOException noSymlinks) {
            return;
        }
        System.setProperty("gatf.distTraces.keep", "1");
        try {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> DistributedTraces.storeDir(out.toFile(), "run-new", "node1"))
                    .hasMessageContaining("escapes");
        } finally {
            System.clearProperty("gatf.distTraces.keep");
        }
        assertThat(victim.toFile().list()).hasSize(56);   // 55 + the run-new dir the refused call created; nothing deleted
    }
}

