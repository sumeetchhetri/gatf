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
package com.gatf.selenium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.ImmutableCapabilities;
import org.openqa.selenium.Point;
import org.openqa.selenium.ScreenOrientation;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.json.Json;
import org.openqa.selenium.remote.Command;
import org.openqa.selenium.remote.CommandExecutor;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.remote.RemoteWebElement;
import org.openqa.selenium.remote.Response;

import com.gatf.selenium.mobile.MobilePlatform;

/**
 * Δ158 R2: the touch commands through a real Selenium RemoteWebDriver whose CommandExecutor is a
 * {@link java.lang.reflect.Proxy}. The proxy records every command and its parameters serialised with Selenium's own
 * {@link Json}, i.e. exactly the W3C JSON the driver would put on the wire, and the test asserts it per command and per
 * platform (UiAutomator2 / XCUITest), plus the desktop step error.
 */
public class AppiumTouchR2Test {

    /** A recording W3C endpoint: newSession answers with the given platform; element/window rects are fixed. */
    static final class Wire {
        final List<String> sent = new ArrayList<String>();
        final List<Object[]> raw = new ArrayList<Object[]>();
        final Map<String, Object> caps;
        volatile String failOn;
        Wire(Map<String, Object> caps) { this.caps = caps; }

        CommandExecutor executor() {
            return (CommandExecutor) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {CommandExecutor.class}, (p, m, a) -> {
                if (!m.getName().equals("execute")) return m.getDefaultValue();
                Command c = (Command) a[0];
                Response r = new Response();
                r.setState("success");
                if (c.getName().equals("newSession")) {
                    r.setSessionId("sid-158");
                    r.setValue(caps);
                    return r;
                }
                r.setSessionId("sid-158");
                String js = new Json().toJson(c.getParameters());
                sent.add(c.getName() + " " + js.replaceAll("\\s+", ""));
                raw.add(new Object[] {c.getName(), new Json().toType(js, Json.MAP_TYPE)});
                if (c.getName().equals(failOn)) throw new WebDriverException("An unknown server-side error occurred\nstack…");
                switch (c.getName()) {
                    case "getElementRect": r.setValue(rect(100, 200, 50, 40)); break;
                    case "getWindowRect": r.setValue(rect(0, 0, 1080, 2400)); break;
                    case "getScreenOrientation": r.setValue("PORTRAIT"); break;
                    default: r.setValue(null);
                }
                return r;
            });
        }
        static Map<String, Object> rect(int x, int y, int w, int h) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("x", x); m.put("y", y); m.put("width", w); m.put("height", h);
            return m;
        }
        /** The parsed parameters of each command with this name (order-insensitive, whitespace kept). */
        List<Object> params(String name) {
            List<Object> out = new ArrayList<Object>();
            for (Object[] r : raw) if (r[0].equals(name)) out.add(r[1]);
            return out;
        }
        List<String> named(String name) {
            List<String> out = new ArrayList<String>();
            for (String s : sent) if (s.startsWith(name + " ")) out.add(s.substring(name.length() + 1));
            return out;
        }
    }

    /** An Appium-like driver: RemoteWebDriver + SupportsRotation (java-client's rotate/getOrientation go through execute()). */
    static final class Device extends RemoteWebDriver implements io.appium.java_client.remote.SupportsRotation {
        Device(CommandExecutor ex, Map<String, Object> caps) { super(ex, new ImmutableCapabilities(caps)); }
        // java-client's ExecutesMethod declares both public (RemoteWebDriver's are protected); AppiumDriver widens them the same way
        @Override public org.openqa.selenium.remote.Response execute(String command, Map<String, ?> parameters) { return super.execute(command, parameters); }
        @Override public org.openqa.selenium.remote.Response execute(String command) { return super.execute(command); }
        @Override public void rotate(ScreenOrientation o) {
            execute("setScreenOrientation", Collections.singletonMap("orientation", o.value().toUpperCase()));
        }
        @Override public ScreenOrientation getOrientation() {
            return ScreenOrientation.valueOf(String.valueOf(execute("getScreenOrientation").getValue()).toUpperCase());
        }
    }

    static Map<String, Object> caps(String platform, String browser) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("platformName", platform);
        if (browser != null) m.put("browserName", browser);
        return m;
    }

    static RemoteWebElement element(RemoteWebDriver d) {
        RemoteWebElement e = new RemoteWebElement();
        e.setParent(d);
        e.setId("el-1");
        return e;
    }

    static String finger(String id, String acts) {
        return "{\"id\":\"" + id + "\",\"type\":\"pointer\",\"parameters\":{\"pointerType\":\"touch\"},\"actions\":[" + acts + "]}";
    }
    static String mv(int ms, int x, int y) { return "{\"duration\":" + ms + ",\"x\":" + x + ",\"y\":" + y + ",\"type\":\"pointerMove\",\"origin\":\"viewport\"}"; }

    static final String WHERE = "tests/m.sel:7";

    @Test
    public void touchCommandsSendW3cTouchActionsOnBothPlatforms() {
        for (String platform : new String[] {"Android", "iOS"}) {
            Wire w = new Wire(caps(platform, null));
            Device d = new Device(w.executor(), w.caps);
            assertEquals(MobilePlatform.of(platform), AppiumTouch.platform(d));

            AppiumTouch.tap(d, null, new Point(123, 234), -1, WHERE);          // tap 123 234
            AppiumTouch.tap(d, null, new Point(123, 234), 2000, WHERE);        // tap 123 234 2000
            AppiumTouch.tap(d, element(d), null, -1, WHERE);                   // tap id@'ele'  (centre of 100,200 50x40)
            AppiumTouch.tap(d, element(d), new Point(10, 5), -1, WHERE);       // tap id@'ele' 10 5
            AppiumTouch.swipe(d, new Point(123, 234), new Point(200, 300), 1000, WHERE);
            AppiumTouch.zoom(d, null, 500, 800, true, WHERE);
            AppiumTouch.zoom(d, element(d), -1, -1, false, WHERE);
            AppiumTouch.chain(d, Arrays.asList(new Object[] {"press", null, 10, 20, null}, new Object[] {"moveTo", element(d), null, null, null},
                    new Object[] {"waitAction", null, null, null, 250}, new Object[] {"release", null, null, null, null}), WHERE);

            List<String> acts = w.named("actions");
            assertEquals(8, acts.size(), platform + ": " + w.sent);
            String down = "{\"button\":0,\"type\":\"pointerDown\"}", up = "{\"button\":0,\"type\":\"pointerUp\"}";
            assertEquals("{\"actions\":[" + finger("finger1", mv(0, 123, 234) + "," + down + ",{\"duration\":100,\"type\":\"pause\"}," + up) + "]}", acts.get(0));
            assertTrue(acts.get(1).contains("{\"duration\":2000,\"type\":\"pause\"}"), acts.get(1));
            assertTrue(acts.get(2).startsWith("{\"actions\":[" + finger("finger1", mv(0, 125, 220))
                    .substring(0, finger("finger1", mv(0, 125, 220)).length() - 2)), acts.get(2));
            assertTrue(acts.get(3).contains(mv(0, 110, 205)), acts.get(3));
            assertEquals("{\"actions\":[" + finger("finger1", mv(0, 123, 234) + "," + down + "," + mv(1000, 200, 300) + "," + up) + "]}", acts.get(4));
            assertTrue(acts.get(5).contains("\"id\":\"finger1\"") && acts.get(5).contains("\"id\":\"finger2\"")
                    && acts.get(5).contains(mv(500, 677, 623)) && acts.get(5).contains(mv(500, 323, 977)), acts.get(5));
            assertTrue(acts.get(6).contains(mv(0, 302, 43)) && acts.get(6).contains(mv(500, 160, 185)), acts.get(6));   // pinch on the element centre 125,220
            assertEquals("{\"actions\":[" + finger("finger1", mv(0, 10, 20) + "," + down + "," + mv(300, 125, 220)
                    + ",{\"duration\":250,\"type\":\"pause\"}," + up) + "]}", acts.get(7));
            for (String a : acts) {
                assertFalse(a.contains("\"mouse\"") || a.contains("\"pen\""), a);   // touch, never mouse
            }
        }
    }

    /** The W3C Execute Script body of {@code executeScript(name, {})}: one empty argument object. */
    static Object script(String name) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("script", name);
        m.put("args", Collections.singletonList(Collections.emptyMap()));
        return m;
    }

    @Test
    public void platformGesturesUseMobileMethodsOrTheOrientationEndpoint() {
        Wire wa = new Wire(caps("Android", null));
        Device a = new Device(wa.executor(), wa.caps);
        AppiumTouch.hideKeypad(a, WHERE);
        AppiumTouch.rotate(a, WHERE);
        assertEquals(Arrays.asList(script("mobile: hideKeyboard")), wa.params("executeScript"));
        assertEquals(Arrays.asList("{\"orientation\":\"LANDSCAPE\"}"), wa.named("setScreenOrientation"));
        RuntimeException e = assertThrows(RuntimeException.class, () -> AppiumTouch.shake(a, WHERE));
        assertEquals("shake failed at tests/m.sel:7: shake is not available on Android: UiAutomator2 has no shake gesture "
                + "(XCUITest's mobile: shake runs on iOS simulators)", e.getMessage());
        assertEquals(1, wa.named("executeScript").size(), "nothing sent for the unsupported shake");

        Wire wi = new Wire(caps("iOS", null));
        Device i = new Device(wi.executor(), wi.caps);
        AppiumTouch.shake(i, WHERE);
        AppiumTouch.hideKeypad(i, WHERE);
        assertEquals(Arrays.asList(script("mobile: shake"), script("mobile: hideKeyboard")), wi.params("executeScript"));
    }

    @Test
    public void onADesktopSessionEachTouchCommandIsAStepErrorAndNothingIsSent() {
        Wire w = new Wire(caps("linux", "chrome"));
        RemoteWebDriver d = new RemoteWebDriver(w.executor(), new ImmutableCapabilities(w.caps));
        List<Runnable> steps = Arrays.<Runnable>asList(
                () -> AppiumTouch.tap(d, null, new Point(1, 2), -1, WHERE),
                () -> AppiumTouch.swipe(d, new Point(1, 2), new Point(3, 4), 100, WHERE),
                () -> AppiumTouch.zoom(d, null, 1, 2, true, WHERE),
                () -> AppiumTouch.zoom(d, null, 1, 2, false, WHERE),
                () -> AppiumTouch.chain(d, Collections.singletonList(new Object[] {"tap", null, 1, 2, null}), WHERE),
                () -> AppiumTouch.hideKeypad(d, WHERE),
                () -> AppiumTouch.shake(d, WHERE),
                () -> AppiumTouch.rotate(d, WHERE));
        String[] names = {"tap", "swipe", "zoom", "pinch", "touch", "hidekeypad", "shake", "rotate"};
        for (int k = 0; k < steps.size(); k++) {
            RuntimeException e = assertThrows(RuntimeException.class, steps.get(k)::run);
            assertTrue(e instanceof MobilePlatform.MobileStepError, String.valueOf(e));
            assertEquals(names[k] + " failed at tests/m.sel:7: " + names[k] + " needs an Appium session (open appium-android | appium-ios); "
                    + "this session is chrome on linux", e.getMessage());
        }
        assertTrue(w.sent.isEmpty(), "a desktop step error sends nothing: " + w.sent);
        // only that step failed: the same session keeps working for the next (non-touch) step
        d.executeScript("return 1");
        assertEquals(1, w.named("executeScript").size());
        assertThrows(RuntimeException.class, () -> AppiumTouch.tap(null, null, new Point(1, 2), -1, WHERE));
    }

    @Test
    public void deviceErrorsAndBadChainsAreStepErrorsWithTheLine() {
        Wire w = new Wire(caps("Android", null));
        Device d = new Device(w.executor(), w.caps);
        w.failOn = "actions";
        RuntimeException e = assertThrows(RuntimeException.class, () -> AppiumTouch.swipe(d, new Point(1, 2), new Point(3, 4), 10, WHERE));
        assertEquals("swipe failed at tests/m.sel:7: An unknown server-side error occurred", e.getMessage());
        assertTrue(e.getCause() instanceof WebDriverException);
        w.failOn = null;
        int before = w.sent.size();
        e = assertThrows(RuntimeException.class, () -> AppiumTouch.chain(d, Collections.singletonList(new Object[] {"release", null, null, null, null}), WHERE));
        assertEquals("touch failed at tests/m.sel:7: action 1 (release): no finger is down", e.getMessage());
        assertEquals(before, w.sent.size(), "a bad chain sends nothing");
    }

    /** The driver hands Selenium a W3C-valid structure: Sequence encodes to TouchGestures' own JSON. */
    @Test
    public void seleniumSequencesEncodeToTheSameActionsAsTheModel() {
        List<com.gatf.selenium.mobile.TouchGestures.Finger> f = com.gatf.selenium.mobile.TouchGestures.zoom(new int[] {500, 800}, true, null);
        String viaSelenium = new Json().toJson(Collections.singletonMap("actions", AppiumTouch.sequences(f))).replaceAll("\\s+", "");
        Object a = new Json().toType(viaSelenium, Json.MAP_TYPE);
        Object b = new Json().toType(new Json().toJson(Collections.singletonMap("actions", com.gatf.selenium.mobile.TouchGestures.toW3c(f))), Json.MAP_TYPE);
        assertEquals(b, a);
    }

}
