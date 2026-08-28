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
