package com.gatf.selenium.trace;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** T1 backend-unit: content-addressed body store — sha1 ref, dedup, per-body cap, total budget. */
class ResourceStoreTest {

    // sha1("hello") — well-known, lets us assert the exact ref + on-disk filename.
    private static final String HELLO_SHA1 = "aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d";

    private static byte[] b(String s) { return s.getBytes(StandardCharsets.UTF_8); }

    @Test
    void storesBodyUnderShaRefAndWritesFile(@TempDir Path tmp) {
        ResourceStore rs = new ResourceStore(tmp, 1 << 20, 1 << 20);
        String ref = rs.store(b("hello"));
        assertThat(ref).isEqualTo("resources/" + HELLO_SHA1);
        assertThat(Files.exists(tmp.resolve(HELLO_SHA1))).isTrue();
        assertThat(rs.usedBytes()).isEqualTo(5);
        assertThat(rs.truncatedCount()).isZero();
    }

    @Test
    void nullOrEmptyBodyReturnsNull(@TempDir Path tmp) {
        ResourceStore rs = new ResourceStore(tmp, 1 << 20, 1 << 20);
        assertThat(rs.store(null)).isNull();
        assertThat(rs.store(new byte[0])).isNull();
        assertThat(rs.usedBytes()).isZero();
    }

    @Test
    void identicalBodiesAreDedupedAndCountedOnce(@TempDir Path tmp) {
        ResourceStore rs = new ResourceStore(tmp, 1 << 20, 1 << 20);
        String r1 = rs.store(b("hello"));
        String r2 = rs.store(b("hello"));
        assertThat(r2).isEqualTo(r1);
        assertThat(rs.usedBytes()).isEqualTo(5);   // budget charged once
    }

    @Test
    void perBodyCapDropsAndCountsTruncated(@TempDir Path tmp) {
        ResourceStore rs = new ResourceStore(tmp, 4, 1 << 20);   // 4-byte per-body cap
        assertThat(rs.store(b("hello"))).isNull();               // 5 > 4 -> dropped
        assertThat(rs.truncatedCount()).isEqualTo(1);
        assertThat(rs.usedBytes()).isZero();
    }

    @Test
    void totalBudgetDropsOverflowKeepingWhatFit(@TempDir Path tmp) {
        ResourceStore rs = new ResourceStore(tmp, 1 << 20, 6);   // 6-byte total budget
        assertThat(rs.store(b("hello"))).isNotNull();            // 5 <= 6
        assertThat(rs.store(b("world!"))).isNull();              // 5 + 6 > 6 -> dropped
        assertThat(rs.truncatedCount()).isEqualTo(1);
        assertThat(rs.usedBytes()).isEqualTo(5);
    }
}
