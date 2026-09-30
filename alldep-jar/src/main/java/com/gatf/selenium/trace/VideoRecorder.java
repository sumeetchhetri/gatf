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
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * Streams screencast frames to disk as they arrive (each frame written immediately to
 * {@code frames/frame-N.jpg} with an entry in {@code frames.ndjson}), so a long recording never
 * buffers frames in memory. At stop it muxes the on-disk frames to {@code video.mp4} via a
 * reflectively-loaded {@link Muxer} (jcodec); on success the raw frames are dropped and the mp4
 * kept, on failure the frames remain as the fallback artifact.
 *
 * Frames arrive on the CDP event thread — a single writer for the frame files and the index, so
 * no cross-thread contention.
 *
 * @author Sumeet Chhetri
 */
public final class VideoRecorder implements VideoListener {
    private final Path traceDir;
    private final Path framesDir;
    private final NdjsonWriter index;
    private final AtomicInteger seq = new AtomicInteger(0);
    private final long startTs;
    private volatile long lastTs;
    private volatile String liveKey;   // M15: publish frames to the console's live view under this script path

    public VideoRecorder(Path traceDir) throws IOException {
        this.traceDir = traceDir;
        this.framesDir = traceDir.resolve("frames");
        Files.createDirectories(framesDir);
        this.index = new NdjsonWriter(traceDir.resolve("frames.ndjson"));
        this.startTs = System.currentTimeMillis();
        this.lastTs = startTs;
    }

    /** M15: also publish each frame to {@link LiveFrames} under {@code key} (the script path); null = off. */
    public void setLiveKey(String key) {
        this.liveKey = key;
        if (key != null) LiveFrames.begin(key);
    }

    @Override
    public void onFrame(byte[] jpeg, long tsMillis) {
        if (jpeg == null || jpeg.length == 0) return;
        if (liveKey != null) LiveFrames.publish(liveKey, jpeg, tsMillis);
        int n = seq.incrementAndGet();
        try {
            Files.write(framesDir.resolve("frame-" + n + ".jpg"), jpeg);
            lastTs = tsMillis;
            index.writeLine(new Json.Obj().put("n", n).put("ts", tsMillis).end());
        } catch (Exception e) { if (n == 1) System.err.println("[gatf-trace] video frame write failed: " + e); }
    }

    /** Finalize the frame index and mux to mp4 (best-effort). */
    public void stop() {
        if (liveKey != null) LiveFrames.end(liveKey);
        index.flush();
        index.close();
        int frames = seq.get();
        System.err.println("[gatf-trace] video: " + frames + " screencast frame(s) captured");
        if (frames < 2) return;                        // not enough for a video
        double durSec = (lastTs - startTs) / 1000.0;
        int fps = durSec > 0 ? (int) Math.round(frames / durSec) : 10;
        if (fps < 1) fps = 1;
        if (fps > 30) fps = 30;

        boolean ok = false;
        try {
            Muxer muxer = (Muxer) Class.forName("com.gatf.selenium.trace.VideoMuxer")
                    .getDeclaredConstructor().newInstance();
            ok = muxer.mux(framesDir, traceDir.resolve("frames.ndjson"), traceDir.resolve("video.mp4"), fps);
        } catch (Throwable t) {
            ok = false;                                // jcodec absent or mux failed
        }
        if (ok) dropFrames();                          // keep only video.mp4
    }

    private void dropFrames() {
        try { Files.deleteIfExists(traceDir.resolve("frames.ndjson")); } catch (IOException ignored) {}
        if (!Files.exists(framesDir)) return;
        try (Stream<Path> w = Files.walk(framesDir)) {
            w.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignored) {}
            });
        } catch (IOException ignored) {}
    }
}
