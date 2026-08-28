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

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import org.jcodec.api.awt.AWTSequenceEncoder;

/**
 * jcodec-backed {@link Muxer}: reads the streamed {@code frame-N.jpg} files in numeric order and
 * encodes them to an mp4 at a fixed fps. Loaded reflectively by {@link VideoRecorder} so the
 * recorder core carries no compile-time dependency on jcodec; a missing jar or an encode error
 * simply returns false and the raw frames are kept instead.
 *
 * Compiled in CI (references jcodec, a new pure-Java dependency added to the POM).
 *
 * @author Sumeet Chhetri
 */
public final class VideoMuxer implements Muxer {

    @Override
    public boolean mux(Path framesDir, Path frameIndex, Path outMp4, int fps) {
        try {
            List<Path> frames = new ArrayList<>();
            try (Stream<Path> s = Files.list(framesDir)) {
                s.filter(p -> p.getFileName().toString().endsWith(".jpg")).forEach(frames::add);
            }
            if (frames.size() < 2) return false;
            frames.sort(Comparator.comparingInt(VideoMuxer::frameIndexOf));

            AWTSequenceEncoder enc = AWTSequenceEncoder.createSequenceEncoder(outMp4.toFile(), Math.max(1, fps));
            int encoded = 0;
            for (Path f : frames) {
                BufferedImage img = ImageIO.read(f.toFile());
                if (img != null) { enc.encodeImage(img); encoded++; }
            }
            enc.finish();
            return encoded >= 2 && Files.exists(outMp4) && Files.size(outMp4) > 0;
        } catch (Throwable t) {
            return false;
        }
    }

    private static int frameIndexOf(Path p) {
        String name = p.getFileName().toString();     // frame-<n>.jpg
        int a = name.indexOf('-'), b = name.lastIndexOf('.');
        try { return (a >= 0 && b > a) ? Integer.parseInt(name.substring(a + 1, b)) : 0; }
        catch (NumberFormatException e) { return 0; }
    }
}
