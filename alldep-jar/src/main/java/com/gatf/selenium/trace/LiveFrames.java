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

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * M15: the latest screencast frame of each running script, for the console's live view. The video recorder publishes
 * every frame it already writes to disk; only the newest frame per script is kept, so memory is bounded by
 * {@link #MAX_CHANNELS} frames. Scripts are keyed by their path; a lookup matches the full key or a path suffix, so the
 * console's test-dir-relative path finds a run keyed by an absolute one. Pure and thread-safe.
 */
public final class LiveFrames {

    public static final int MAX_CHANNELS = 16;
    /** A channel whose run ended is dropped after this long. */
    public static final long ENDED_TTL_MS = 10 * 60 * 1000L;

    public static final class Frame {
        public final String key;
        public final long seq;
        public final long ts;
        public final byte[] jpeg;
        public final boolean ended;
        Frame(String key, long seq, long ts, byte[] jpeg, boolean ended) {
            this.key = key; this.seq = seq; this.ts = ts; this.jpeg = jpeg; this.ended = ended;
        }
    }

    private static final Map<String, Frame> CHANNELS = new LinkedHashMap<String, Frame>(16, 0.75f, true);
    private static long nextSeq = 0;

    private LiveFrames() {}

    static String norm(String key) {
        if (key == null) return null;
        String k = key.trim().replace('\\', '/');
        while (k.startsWith("./")) k = k.substring(2);
        return k.isEmpty() ? null : k;
    }

    public static synchronized void publish(String key, byte[] jpeg, long ts) {
        String k = norm(key);
        if (k == null || jpeg == null || jpeg.length == 0) return;
        CHANNELS.put(k, new Frame(k, ++nextSeq, ts, jpeg, false));
        evict(System.currentTimeMillis());
    }

    /** A new run of {@code key} starts: forget the previous run's last frame. */
    public static synchronized void begin(String key) {
        String k = norm(key);
        if (k != null) CHANNELS.remove(k);
    }

    /** The run finished: the last frame stays visible (marked ended) until it expires. */
    public static synchronized void end(String key) {
        String k = norm(key);
        Frame f = k == null ? null : CHANNELS.get(k);
        if (f != null) CHANNELS.put(k, new Frame(k, ++nextSeq, System.currentTimeMillis(), f.jpeg, true));
    }

    /**
     * The newest frame for {@code key} with a sequence number above {@code afterSeq}, or null when there is none (no
     * run, or nothing new). Several matching channels (the same file run twice): the most recently updated wins.
     */
    public static synchronized Frame latest(String key, long afterSeq) {
        String k = norm(key);
        if (k == null) return null;
        evict(System.currentTimeMillis());
        Frame best = null;
        for (Frame f : CHANNELS.values()) {
            if (matches(f.key, k) && (best == null || f.seq > best.seq)) best = f;
        }
        return best != null && best.seq > afterSeq ? best : null;
    }

    static boolean matches(String channel, String wanted) {
        return channel.equals(wanted) || channel.endsWith("/" + wanted) || wanted.endsWith("/" + channel);
    }

    private static void evict(long now) {
        for (Iterator<Frame> it = CHANNELS.values().iterator(); it.hasNext();) {
            Frame f = it.next();
            if (f.ended && now - f.ts > ENDED_TTL_MS) it.remove();
        }
        // access order: the first entries are the least recently published
        for (Iterator<String> it = CHANNELS.keySet().iterator(); CHANNELS.size() > MAX_CHANNELS && it.hasNext();) {
            it.next();
            it.remove();
        }
    }

    static synchronized void clear() {
        CHANNELS.clear();
    }
}
