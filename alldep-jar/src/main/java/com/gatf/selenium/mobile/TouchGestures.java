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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * H5 (Δ158): the seleasy touch commands as W3C pointer actions with {@code pointerType: touch}, the form Appium 2
 * (UiAutomator2 / XCUITest) executes. Pure: no Selenium types, so the exact gesture of every command is testable without
 * a device; {@code AppiumTouch} turns each {@link Finger} into a Selenium {@code Sequence} one step for one step.
 * <p>
 * Coordinates are viewport pixels. An element target is its rectangle: no offset → its centre, an offset → that many
 * pixels from its top-left corner (the old TouchAction {@code ElementOption} rule).
 */
public final class TouchGestures {

    private TouchGestures() {}

    /** A tap without a duration holds for this long (a 500 ms hold reads as a long-press on Android). */
    public static final int DEFAULT_TAP_HOLD_MS = 100;
    /** {@code touch … longpress} without a duration. */
    public static final int DEFAULT_LONG_PRESS_MS = 1000;
    /** {@code touch … moveto}: the move from the current point to the target. */
    public static final int MOVE_MS = 300;
    /** zoom / pinch: how long the two fingers travel. */
    public static final int ZOOM_MS = 500;
    /** zoom / pinch: each finger's distance from the centre at the near and far end of its path. */
    public static final int ZOOM_NEAR = 50, ZOOM_FAR = 250;

    /** A rectangle in viewport pixels (an element's rect, or the window's size at 0,0). */
    public static final class Box {
        public final int x, y, width, height;
        public Box(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
        public int[] center() { return new int[] {x + width / 2, y + height / 2}; }
    }

    /** One W3C pointer action. */
    public static final class Step {
        public final String type;   // pointerMove | pointerDown | pointerUp | pause
        public final int x, y, durationMs;
        Step(String type, int x, int y, int durationMs) {
            this.type = type;
            this.x = x;
            this.y = y;
            this.durationMs = durationMs;
        }
        static Step move(int ms, int[] p) { return new Step("pointerMove", p[0], p[1], Math.max(0, ms)); }
        static Step down() { return new Step("pointerDown", 0, 0, 0); }
        static Step up() { return new Step("pointerUp", 0, 0, 0); }
        static Step pause(int ms) { return new Step("pause", 0, 0, Math.max(0, ms)); }

        /** The W3C action object, as it goes on the wire. */
        public Map<String, Object> toW3c() {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("type", type);
            switch (type) {
                case "pointerMove":
                    m.put("duration", durationMs);
                    m.put("origin", "viewport");
                    m.put("x", x);
                    m.put("y", y);
                    break;
                case "pause":
                    m.put("duration", durationMs);
                    break;
                default:
                    m.put("button", 0);
            }
            return m;
        }
    }

    /** One touch input source ("finger1", "finger2") and its actions. */
    public static final class Finger {
        public final String id;
        public final List<Step> steps;
        Finger(String id, List<Step> steps) {
            this.id = id;
            this.steps = Collections.unmodifiableList(steps);
        }
        public Map<String, Object> toW3c() {
            Map<String, Object> params = new LinkedHashMap<String, Object>();
            params.put("pointerType", "touch");
            List<Object> acts = new ArrayList<Object>();
            for (Step s : steps) acts.add(s.toW3c());
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("type", "pointer");
            m.put("id", id);
            m.put("parameters", params);
            m.put("actions", acts);
            return m;
        }
    }

    /** The {@code actions} array of a W3C Perform Actions request. */
    public static List<Object> toW3c(List<Finger> fingers) {
        List<Object> out = new ArrayList<Object>();
        for (Finger f : fingers) out.add(f.toW3c());
        return out;
    }

    /** Compact JSON of maps, lists, strings, numbers and booleans (for logs and tests; keys keep their order). */
    public static String json(Object o) {
        StringBuilder b = new StringBuilder();
        json(o, b);
        return b.toString();
    }

    @SuppressWarnings("unchecked")
    private static void json(Object o, StringBuilder b) {
        if (o == null) b.append("null");
        else if (o instanceof Map) {
            b.append('{');
            boolean first = true;
            for (Map.Entry<Object, Object> e : ((Map<Object, Object>) o).entrySet()) {
                if (!first) b.append(',');
                first = false;
                json(String.valueOf(e.getKey()), b);
                b.append(':');
                json(e.getValue(), b);
            }
            b.append('}');
        } else if (o instanceof Iterable) {
            b.append('[');
            boolean first = true;
            for (Object x : (Iterable<Object>) o) {
                if (!first) b.append(',');
                first = false;
                json(x, b);
            }
            b.append(']');
        } else if (o instanceof Number || o instanceof Boolean) b.append(o);
        else {
            b.append('"');
            for (char c : String.valueOf(o).toCharArray()) {
                if (c == '"' || c == '\\') b.append('\\').append(c);
                else if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                else b.append(c);
            }
            b.append('"');
        }
    }

    /** The hold for {@code tap}: the given duration exactly, or {@link #DEFAULT_TAP_HOLD_MS} when none was given (null or negative). */
    public static int tapHold(Integer givenMs) {
        return givenMs == null || givenMs < 0 ? DEFAULT_TAP_HOLD_MS : givenMs;
    }

    /** The point for an element (centre, or top-left + offset) or for plain coordinates. */
    public static int[] point(Box element, Integer x, Integer y) {
        if (element != null) {
            if (x != null && y != null) return new int[] {element.x + x, element.y + y};
            return element.center();
        }
        if (x == null || y == null) return null;
        return new int[] {x, y};
    }

    /** {@code tap}: down at the point, hold, up. */
    public static List<Finger> tap(int[] p, int holdMs) {
        List<Step> s = new ArrayList<Step>();
        s.add(Step.move(0, p));
        s.add(Step.down());
        s.add(Step.pause(holdMs));
        s.add(Step.up());
        return Collections.singletonList(new Finger("finger1", s));
    }

    /** {@code swipe}: down at the start, move to the end over {@code durationMs}, up. */
    public static List<Finger> swipe(int[] from, int[] to, int durationMs) {
        List<Step> s = new ArrayList<Step>();
        s.add(Step.move(0, from));
        s.add(Step.down());
        s.add(Step.move(durationMs, to));
        s.add(Step.up());
        return Collections.singletonList(new Finger("finger1", s));
    }

    /**
     * {@code zoom} (fingers move apart) or {@code pinch} (fingers move together) around {@code center}, on the 45° diagonal.
     * Every point is clamped into {@code window} when it is known, so a target near an edge never sends a point off screen.
     */
    public static List<Finger> zoom(int[] center, boolean zoomIn, Box window) {
        int from = zoomIn ? ZOOM_NEAR : ZOOM_FAR, to = zoomIn ? ZOOM_FAR : ZOOM_NEAR;
        List<Finger> out = new ArrayList<Finger>();
        for (int f = 0; f < 2; f++) {
            int sign = f == 0 ? 1 : -1;   // finger1 up-right, finger2 down-left
            List<Step> s = new ArrayList<Step>();
            s.add(Step.move(0, clamp(diag(center, from, sign), window)));
            s.add(Step.down());
            s.add(Step.move(ZOOM_MS, clamp(diag(center, to, sign), window)));
            s.add(Step.up());
            out.add(new Finger("finger" + (f + 1), s));
        }
        return out;
    }

    private static int[] diag(int[] c, int r, int sign) {
        int d = (int) Math.round(r * Math.sqrt(0.5));
        return new int[] {c[0] + sign * d, c[1] - sign * d};
    }

    static int[] clamp(int[] p, Box w) {
        if (w == null || w.width <= 0 || w.height <= 0) return p;
        return new int[] {Math.max(w.x, Math.min(w.x + w.width - 1, p[0])), Math.max(w.y, Math.min(w.y + w.height - 1, p[1]))};
    }

    /** One {@code touch} chain step: press | moveTo | tap | longPress | waitAction | release, with its resolved target. */
    public static final class ChainStep {
        public final String action;
        public final Box element;
        public final Integer x, y, durationMs;
        public ChainStep(String action, Box element, Integer x, Integer y, Integer durationMs) {
            this.action = action;
            this.element = element;
            this.x = x;
            this.y = y;
            this.durationMs = durationMs;
        }
    }

    /**
     * {@code touch …}: the old TouchAction chain as one W3C touch source. press = down at the target; moveto = move
     * ({@link #MOVE_MS}); tap = down, {@link #DEFAULT_TAP_HOLD_MS}, up; longpress = down and hold (given ms, else
     * {@link #DEFAULT_LONG_PRESS_MS}); wait = pause; release = up. A finger still down at the end is released. A step
     * that cannot be performed (press while down, release while up, a move with no target) is an
     * {@link IllegalArgumentException} naming the step; nothing is sent.
     */
    public static List<Finger> chain(List<ChainStep> chain) {
        if (chain == null || chain.isEmpty()) throw new IllegalArgumentException("touch needs at least one action");
        List<Step> s = new ArrayList<Step>();
        boolean down = false;
        int n = 0;
        for (ChainStep c : chain) {
            n++;
            String a = c.action == null ? "" : c.action.toLowerCase(Locale.ROOT);
            int[] p = point(c.element, c.x, c.y);
            String which = "action " + n + " (" + c.action + ")";
            switch (a) {
                case "press":
                    if (down) throw new IllegalArgumentException(which + ": the finger is already down (release it first)");
                    if (p == null) throw new IllegalArgumentException(which + ": press needs an element or x y");
                    s.add(Step.move(0, p));
                    s.add(Step.down());
                    down = true;
                    break;
                case "moveto":
                    if (p == null) throw new IllegalArgumentException(which + ": moveto needs an element or x y");
                    s.add(Step.move(s.isEmpty() ? 0 : MOVE_MS, p));
                    break;
                case "tap":
                    if (down) throw new IllegalArgumentException(which + ": the finger is already down (release it first)");
                    if (p != null) s.add(Step.move(0, p));
                    else if (s.isEmpty()) throw new IllegalArgumentException(which + ": tap needs an element or x y");
                    s.add(Step.down());
                    s.add(Step.pause(DEFAULT_TAP_HOLD_MS));
                    s.add(Step.up());
                    break;
                case "longpress":
                    if (down) throw new IllegalArgumentException(which + ": the finger is already down (release it first)");
                    if (p != null) s.add(Step.move(0, p));
                    else if (s.isEmpty()) throw new IllegalArgumentException(which + ": longpress needs an element or x y");
                    s.add(Step.down());
                    s.add(Step.pause(c.durationMs == null ? DEFAULT_LONG_PRESS_MS : c.durationMs));
                    down = true;
                    break;
                case "waitaction": case "wait":
                    s.add(Step.pause(c.durationMs == null ? 0 : c.durationMs));
                    break;
                case "release":
                    if (!down) throw new IllegalArgumentException(which + ": no finger is down");
                    s.add(Step.up());
                    down = false;
                    break;
                default:
                    throw new IllegalArgumentException(which + ": unknown touch action");
            }
        }
        if (down) s.add(Step.up());
        return Collections.singletonList(new Finger("finger1", s));
    }
}
