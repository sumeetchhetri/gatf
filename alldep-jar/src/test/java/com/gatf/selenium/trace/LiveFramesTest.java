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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** M15: the live-frame hub behind the console's screencast. */
class LiveFramesTest {

    private static byte[] b(int n) { return new byte[] { (byte) n }; }

    @Test
    void newestFrameBySuffixAndSequence() {
        LiveFrames.clear();
        assertNull(LiveFrames.latest("login.sel", 0));
        LiveFrames.publish("/proj/tests/ui/login.sel", b(1), 10);
        LiveFrames.publish("/proj/tests/ui/login.sel", b(2), 20);
        LiveFrames.Frame f = LiveFrames.latest("tests/ui/login.sel", 0);
        assertNotNull(f);
        assertEquals(2, f.jpeg[0]);
        assertNull(LiveFrames.latest("tests/ui/login.sel", f.seq));   // nothing newer
        assertNull(LiveFrames.latest("relogin.sel", 0));             // suffix is per path segment
        assertNotNull(LiveFrames.latest(".\\tests\\ui\\login.sel", 0));
        LiveFrames.publish("C:\\p\\cart.sel", b(3), 30);
        assertEquals(3, LiveFrames.latest("cart.sel", 0).jpeg[0]);
    }

    @Test
    void endKeepsTheLastFrameMarkedEnded() {
        LiveFrames.clear();
        LiveFrames.publish("a.sel", b(7), 1);
        long s = LiveFrames.latest("a.sel", 0).seq;
        LiveFrames.end("a.sel");
        LiveFrames.Frame f = LiveFrames.latest("a.sel", s);
        assertNotNull(f);                                            // the end is news to a poller
        assertTrue(f.ended);
        assertEquals(7, f.jpeg[0]);
        LiveFrames.end("never-ran.sel");                             // harmless
        LiveFrames.begin("a.sel");                                   // the next run of the script
        assertNull(LiveFrames.latest("a.sel", 0));                   // does not show the previous run's last frame
        assertNull(LiveFrames.latest("never-ran.sel", 0));
    }

    @Test
    void boundedToTheMostRecentChannels() {
        LiveFrames.clear();
        for (int i = 0; i < LiveFrames.MAX_CHANNELS + 5; i++) LiveFrames.publish("s" + i + ".sel", b(i), i);
        assertNull(LiveFrames.latest("s0.sel", 0));
        assertNotNull(LiveFrames.latest("s" + (LiveFrames.MAX_CHANNELS + 4) + ".sel", 0));
        LiveFrames.publish(null, b(1), 1);
        LiveFrames.publish("x.sel", new byte[0], 1);
        assertNull(LiveFrames.latest("x.sel", 0));
    }

    @Test
    void videoRecorderPublishesWhatItWrites(@TempDir Path tmp) throws Exception {
        LiveFrames.clear();
        VideoRecorder v = new VideoRecorder(tmp);
        v.onFrame(b(1), 5);                                          // no live key: disk only
        assertNull(LiveFrames.latest("t.sel", 0));
        v.setLiveKey("/w/t.sel");
        v.onFrame(b(2), 6);
        assertEquals(2, LiveFrames.latest("t.sel", 0).jpeg[0]);
        assertTrue(Files.exists(tmp.resolve("frames/frame-2.jpg")));
        v.stop();
        assertTrue(LiveFrames.latest("t.sel", 0).ended);
    }
}
