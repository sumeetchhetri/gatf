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
package com.gatf.executor.core;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * M7: deterministic file-level sharding for {@code gatf test --shard i/n} (1-based, as in Playwright).
 * <p>
 * The distinct test-file paths are ordered by a stable 64-bit FNV-1a hash of the normalised path (ties by the path),
 * then dealt round-robin over the n shards. So the same file set always produces the same split on every machine and
 * JVM, and shard sizes differ by at most one file. Adding or removing a file can move other files between shards;
 * every shard of one CI run sees the same checkout, so that never splits a run inconsistently.
 * <p>
 * Paths must be relative (to the test-case dir) for the split to match across machines; {@code \} and a leading
 * {@code ./} or {@code /} are normalised away. Pure, no side effects.
 */
public final class ShardSplitter {

    /** Thrown when this shard received no files (more shards than files); the CLI treats it as an empty, green run. */
    public static final class EmptyShardException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        public EmptyShardException(String msg) { super(msg); }
    }

    /** 1-based shard index. */
    public final int index;
    public final int total;

    public ShardSplitter(int index, int total) {
        if (total < 1) throw new IllegalArgumentException("shard total must be >= 1, got " + total);
        if (index < 1 || index > total) throw new IllegalArgumentException("shard index must be in 1.." + total + ", got " + index);
        this.index = index;
        this.total = total;
    }

    /** Parses {@code "i/n"}; null or blank = null (no sharding). Throws IllegalArgumentException otherwise. */
    public static ShardSplitter parse(String spec) {
        if (spec == null || spec.trim().isEmpty()) return null;
        String s = spec.trim();
        int slash = s.indexOf('/');
        if (slash <= 0 || slash == s.length() - 1) throw new IllegalArgumentException("--shard expects i/n (e.g. 2/4), got '" + spec + "'");
        try {
            return new ShardSplitter(Integer.parseInt(s.substring(0, slash).trim()), Integer.parseInt(s.substring(slash + 1).trim()));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("--shard expects i/n (e.g. 2/4), got '" + spec + "'");
        }
    }

    public String describe() { return "shard " + index + "/" + total; }

    @Override
    public String toString() { return index + "/" + total; }

    static String normalize(String path) {
        String p = path == null ? "" : path.trim().replace('\\', '/');
        while (p.startsWith("./")) p = p.substring(2);
        while (p.startsWith("/")) p = p.substring(1);
        return p;
    }

    /**
     * The shard key of a test file: {@code path} relative to {@code baseDir} when it lies under it (folder / single-file
     * {@code seleniumScript} settings expand to absolute paths, and a checkout folder that differs per machine must
     * not change the split), else the path as given.
     */
    public static String relativeTo(String path, String baseDir) {
        if (path == null || baseDir == null || baseDir.trim().isEmpty()) return path;
        String p = path.replace('\\', '/');
        String b = baseDir.replace('\\', '/');
        while (b.endsWith("/")) b = b.substring(0, b.length() - 1);
        if (!b.isEmpty() && p.startsWith(b + "/")) return p.substring(b.length() + 1);
        return path;
    }

    /** 64-bit FNV-1a over the UTF-8 bytes of the normalised path. */
    static long hash(String path) {
        long h = 0xcbf29ce484222325L;
        for (byte b : normalize(path).getBytes(StandardCharsets.UTF_8)) {
            h ^= (b & 0xff);
            h *= 0x100000001b3L;
        }
        return h;
    }

    /** Shard (1-based) of every distinct normalised path in {@code paths}. */
    public static Map<String, Integer> assign(List<String> paths, int total) {
        Set<String> distinct = new LinkedHashSet<String>();
        for (String p : paths) distinct.add(normalize(p));
        List<String> order = new ArrayList<String>(distinct);
        Collections.sort(order, (a, b) -> {
            int c = Long.compareUnsigned(hash(a), hash(b));
            return c != 0 ? c : a.compareTo(b);
        });
        Map<String, Integer> out = new HashMap<String, Integer>();
        for (int i = 0; i < order.size(); i++) out.put(order.get(i), (i % total) + 1);
        return out;
    }

    /** The items whose file belongs to this shard, order preserved. */
    public <T> List<T> select(List<T> items, Function<T, String> path) {
        List<String> paths = new ArrayList<String>();
        for (T it : items) paths.add(path.apply(it));
        Map<String, Integer> shard = assign(paths, total);
        List<T> out = new ArrayList<T>();
        for (T it : items) {
            if (shard.get(normalize(path.apply(it))) == index) out.add(it);
        }
        return out;
    }

    /**
     * As {@link #select}, then adds back every transitive dependency of a kept item (API {@code relatedTestName}),
     * even when it lives in another shard's file, so a shard never runs a test without its prerequisite.
     */
    public <T> List<T> selectWithDependencies(List<T> items, Function<T, String> path, Function<T, String> name, Function<T, String> dependsOn) {
        Set<T> keep = Collections.newSetFromMap(new IdentityHashMap<T, Boolean>());
        keep.addAll(select(items, path));
        boolean grew = true;
        while (grew) {
            grew = false;
            for (T it : new ArrayList<T>(keep)) {
                String dep = dependsOn == null ? null : dependsOn.apply(it);
                if (dep == null) continue;
                for (T cand : items) {
                    if (dep.equals(name.apply(cand)) && keep.add(cand)) grew = true;
                }
            }
        }
        List<T> out = new ArrayList<T>();
        for (T it : items) if (keep.contains(it)) out.add(it);
        return out;
    }

    /**
     * M7: resolves a {@code --workers} value: {@code N} or {@code N%} of the available processors (at least 1).
     * Throws IllegalArgumentException for anything else.
     */
    public static int resolveWorkers(String v, int processors) {
        String s = v == null ? "" : v.trim();
        try {
            if (s.endsWith("%")) {
                int pct = Integer.parseInt(s.substring(0, s.length() - 1).trim());
                if (pct < 1) throw new IllegalArgumentException("--workers percentage must be >= 1%, got '" + v + "'");
                return Math.max(1, (int) Math.floor(processors * pct / 100.0));
            }
            int n = Integer.parseInt(s);
            if (n < 1) throw new IllegalArgumentException("--workers must be >= 1");
            return n;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("--workers expects N or N%, got '" + v + "'");
        }
    }

    /** M7: the functional worker-pool size: the configured workers (no fixed cap), never more than the file count. */
    public static int poolSize(int configured, int files) {
        return Math.max(1, Math.min(configured, files));
    }
}
