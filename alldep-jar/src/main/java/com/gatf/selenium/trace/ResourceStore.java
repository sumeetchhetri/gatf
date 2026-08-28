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
package com.gatf.selenium.trace;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Content-addressed store for captured response bodies. Bodies are written to
 * {@code resources/<sha1>} and deduped, so repeated identical responses cost one file. A
 * per-body cap and a total-trace byte budget bound disk/memory (Playwright-style): once the
 * budget is exhausted, or a body exceeds the per-body cap, the body is dropped and the caller
 * records a {@code truncated} marker rather than an empty body.
 *
 * Shared across the network callback thread and the test thread, so it uses a concurrent map
 * and an atomic counter — no locks, virtual-thread friendly.
 *
 * @author Sumeet Chhetri
 */
public final class ResourceStore {
    private final Path dir;
    private final long maxBodyBytes;
    private final long maxTotalBytes;
    private final AtomicLong used = new AtomicLong(0);
    private final AtomicLong truncatedCount = new AtomicLong(0);
    private final ConcurrentHashMap<String, String> seen = new ConcurrentHashMap<>();

    public ResourceStore(Path dir, long maxBodyBytes, long maxTotalBytes) {
        this.dir = dir;
        this.maxBodyBytes = maxBodyBytes;
        this.maxTotalBytes = maxTotalBytes;
    }

    /**
     * Store body bytes; return the resource ref ("resources/&lt;sha1&gt;") or null when the body
     * was dropped (too large, over budget, or unavailable).
     */
    public String store(byte[] body) {
        if (body == null || body.length == 0) return null;
        if (body.length > maxBodyBytes) { truncatedCount.incrementAndGet(); return null; }
        if (used.get() + body.length > maxTotalBytes) { truncatedCount.incrementAndGet(); return null; }

        String sha = sha1(body);
        if (sha == null) return null;
        String existing = seen.get(sha);
        if (existing != null) return existing;

        // reserve budget before writing; if the reservation blows the budget, drop
        long after = used.addAndGet(body.length);
        if (after > maxTotalBytes) { used.addAndGet(-body.length); truncatedCount.incrementAndGet(); return null; }

        String ref = "resources/" + sha;
        try {
            Files.createDirectories(dir);
            Path f = dir.resolve(sha);
            if (!Files.exists(f)) Files.write(f, body);
            seen.put(sha, ref);
            return ref;
        } catch (IOException e) {
            used.addAndGet(-body.length);
            return null;
        }
    }

    public long truncatedCount() { return truncatedCount.get(); }
    public long usedBytes() { return used.get(); }

    private static String sha1(byte[] b) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] d = md.digest(b);
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte x : d) sb.append(Character.forDigit((x >> 4) & 0xf, 16)).append(Character.forDigit(x & 0xf, 16));
            return sb.toString();
        } catch (Exception e) { return null; }
    }
}
