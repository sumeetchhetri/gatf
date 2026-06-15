package com.gatf.selenium.trace;

import java.nio.file.Path;

/**
 * Turns a directory of streamed JPEG frames into an mp4. Kept as a pure-Java interface so the
 * recorder core never references the codec library directly — the concrete {@code VideoMuxer}
 * (jcodec) is loaded reflectively at stop, so a missing/failed codec degrades to "frames kept,
 * mp4 skipped" instead of breaking the trace or the build's verifiable core.
 *
 * @author Sumeet Chhetri
 */
public interface Muxer {
    /** Encode the indexed frames to {@code outMp4} at {@code fps}; return true on success. */
    boolean mux(Path framesDir, Path frameIndex, Path outMp4, int fps);
}
