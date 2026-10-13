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
package com.gatf.selenium.mobile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.gatf.selenium.mobile.TouchGestures.Box;
import com.gatf.selenium.mobile.TouchGestures.ChainStep;
import com.gatf.selenium.mobile.TouchGestures.Finger;

/** Δ158: the exact W3C touch actions of every touch command (R1, no device). */
public class TouchGesturesTest {

    static final String TOUCH = "\"type\":\"pointer\",\"id\":\"finger1\",\"parameters\":{\"pointerType\":\"touch\"}";

    static String mv(int ms, int x, int y) {
        return "{\"type\":\"pointerMove\",\"duration\":" + ms + ",\"origin\":\"viewport\",\"x\":" + x + ",\"y\":" + y + "}";
    }
    static final String DOWN = "{\"type\":\"pointerDown\",\"button\":0}", UP = "{\"type\":\"pointerUp\",\"button\":0}";
    static String pause(int ms) { return "{\"type\":\"pause\",\"duration\":" + ms + "}"; }
    static String one(String... acts) { return "[{" + TOUCH + ",\"actions\":[" + String.join(",", acts) + "]}]"; }
    static String w3c(List<Finger> f) { return TouchGestures.json(TouchGestures.toW3c(f)); }

    @Test
    public void tapHoldDefaultsTo100AndAGivenDurationIsExact() {
        assertEquals(100, TouchGestures.tapHold(null));
        assertEquals(100, TouchGestures.tapHold(-1));
        assertEquals(0, TouchGestures.tapHold(0));
        assertEquals(500, TouchGestures.tapHold(500));
        assertEquals(2000, TouchGestures.tapHold(2000));
    }

    @Test
    public void tapIsMoveDownPauseUp() {
        assertEquals(one(mv(0, 123, 234), DOWN, pause(100), UP), w3c(TouchGestures.tap(new int[] {123, 234}, TouchGestures.tapHold(-1))));
        assertEquals(one(mv(0, 5, 6), DOWN, pause(2000), UP), w3c(TouchGestures.tap(new int[] {5, 6}, 2000)));
    }

    @Test
    public void elementTargetsAreTheCentreOrAnOffsetFromTopLeft() {
        Box b = new Box(100, 200, 50, 40);
        assertEquals(Arrays.toString(new int[] {125, 220}), Arrays.toString(TouchGestures.point(b, null, null)));
        assertEquals(Arrays.toString(new int[] {110, 205}), Arrays.toString(TouchGestures.point(b, 10, 5)));
        assertEquals(Arrays.toString(new int[] {7, 8}), Arrays.toString(TouchGestures.point(null, 7, 8)));
        assertEquals(null, TouchGestures.point(null, 7, null));
    }

    @Test
    public void swipeMovesOverTheDuration() {
        assertEquals(one(mv(0, 123, 234), DOWN, mv(1000, 200, 300), UP),
                w3c(TouchGestures.swipe(new int[] {123, 234}, new int[] {200, 300}, 1000)));
    }

    @Test
    public void zoomAndPinchAreTwoTouchFingersOnTheDiagonalClampedToTheWindow() {
        // 50 * sqrt(.5) = 35, 250 * sqrt(.5) = 177
        String in = w3c(TouchGestures.zoom(new int[] {500, 800}, true, new Box(0, 0, 1080, 2400)));
        assertEquals("[{" + TOUCH + ",\"actions\":[" + mv(0, 535, 765) + "," + DOWN + "," + mv(500, 677, 623) + "," + UP + "]},"
                + "{" + TOUCH.replace("finger1", "finger2") + ",\"actions\":[" + mv(0, 465, 835) + "," + DOWN + "," + mv(500, 323, 977) + "," + UP + "]}]", in);
        String out = w3c(TouchGestures.zoom(new int[] {500, 800}, false, null));
        assertTrue(out.contains(mv(0, 677, 623)) && out.contains(mv(500, 535, 765)), out);
        // near the top-left corner: no negative or off-screen point
        String edge = w3c(TouchGestures.zoom(new int[] {20, 20}, true, new Box(0, 0, 400, 300)));
        assertTrue(edge.contains(mv(500, 197, 0)) && edge.contains(mv(500, 0, 197)), edge);
        assertTrue(!edge.contains("-"), edge);
    }

    static ChainStep st(String a, Box b, Integer x, Integer y, Integer d) { return new ChainStep(a, b, x, y, d); }

    @Test
    public void touchChainMapsTheOldTouchActionStepsToOneTouchSource() {
        Box ele = new Box(0, 0, 100, 100), ele2 = new Box(300, 300, 100, 100);
        // REFERENCE example: touch moveto id@'ele' longpress moveto id@'ele2' wait 1000 release
        List<Finger> f = TouchGestures.chain(Arrays.asList(st("moveTo", ele, null, null, null), st("longPress", null, null, null, null),
                st("moveTo", ele2, null, null, null), st("waitAction", null, null, null, 1000), st("release", null, null, null, null)));
        assertEquals(one(mv(0, 50, 50), DOWN, pause(1000), mv(300, 350, 350), pause(1000), UP), w3c(f));
        // press x y, moveto x y, release; then a tap with a target; longpress with duration and an implicit release
        assertEquals(one(mv(0, 10, 20), DOWN, mv(300, 30, 40), UP, mv(0, 1, 2), DOWN, pause(100), UP),
                w3c(TouchGestures.chain(Arrays.asList(st("press", null, 10, 20, null), st("moveTo", null, 30, 40, null),
                        st("release", null, null, null, null), st("tap", null, 1, 2, null)))));
        assertEquals(one(mv(0, 10, 5), DOWN, pause(1500), UP),
                w3c(TouchGestures.chain(Arrays.asList(st("longPress", ele, 10, 5, 1500)))));
    }

    @Test
    public void impossibleChainsAreRefusedBeforeAnythingIsSent() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> TouchGestures.chain(Arrays.asList(
                st("press", null, 1, 1, null), st("press", null, 2, 2, null))));
        assertTrue(e.getMessage().contains("action 2 (press)") && e.getMessage().contains("already down"), e.getMessage());
        e = assertThrows(IllegalArgumentException.class, () -> TouchGestures.chain(Arrays.asList(st("release", null, null, null, null))));
        assertTrue(e.getMessage().contains("no finger is down"), e.getMessage());
        e = assertThrows(IllegalArgumentException.class, () -> TouchGestures.chain(Arrays.asList(st("moveTo", null, null, null, null))));
        assertTrue(e.getMessage().contains("needs an element or x y"), e.getMessage());
        assertThrows(IllegalArgumentException.class, () -> TouchGestures.chain(Arrays.<ChainStep>asList()));
        assertThrows(IllegalArgumentException.class, () -> TouchGestures.chain(Arrays.asList(st("fling", null, 1, 1, null))));
    }

    @Test
    public void jsonEscapes() {
        assertEquals("{\"a\\\"b\":[1,true,null,\"x\\\\y\\u000a\"]}",
                TouchGestures.json(java.util.Collections.singletonMap("a\"b", Arrays.asList(1, true, null, "x\\y\n"))));
    }
}
