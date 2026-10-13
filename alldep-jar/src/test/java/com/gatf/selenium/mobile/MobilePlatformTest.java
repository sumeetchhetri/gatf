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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** Δ158: platform detection, the mobile: gesture table, step errors, and local Appium 2 capabilities (R1). */
public class MobilePlatformTest {

    @Test
    public void onlyAndroidAndIosSessionsAreAppium() {
        assertEquals(MobilePlatform.ANDROID, MobilePlatform.of("Android"));
        assertEquals(MobilePlatform.ANDROID, MobilePlatform.of("ANDROID"));
        assertEquals(MobilePlatform.IOS, MobilePlatform.of("iOS"));
        for (String desktop : new String[] {"linux", "windows", "mac", "WIN11", "", "any"}) assertNull(MobilePlatform.of(desktop), desktop);
        assertNull(MobilePlatform.of(null));
        assertEquals(MobilePlatform.ANDROID, MobilePlatform.ofKey("appium-android"));
        assertEquals(MobilePlatform.IOS, MobilePlatform.ofKey("Appium-iOS"));
        assertNull(MobilePlatform.ofKey("chrome"));
        assertEquals("UiAutomator2", MobilePlatform.ANDROID.automationName);
        assertEquals("XCUITest", MobilePlatform.IOS.automationName);
    }

    @Test
    public void platformGesturesUseTheDocumentedMobileMethods() {
        assertEquals("mobile: hideKeyboard", MobilePlatform.ANDROID.hideKeypad().script);
        assertEquals("mobile: hideKeyboard", MobilePlatform.IOS.hideKeypad().script);
        assertEquals("mobile: shake", MobilePlatform.IOS.shake().script);
        UnsupportedOperationException e = assertThrows(UnsupportedOperationException.class, () -> MobilePlatform.ANDROID.shake());
        assertTrue(e.getMessage().contains("not available on Android"), e.getMessage());
        assertEquals("LANDSCAPE", MobilePlatform.nextOrientation("PORTRAIT"));
        assertEquals("PORTRAIT", MobilePlatform.nextOrientation("LANDSCAPE"));
        assertEquals("PORTRAIT", MobilePlatform.nextOrientation("landscapeRight"));
        assertEquals("LANDSCAPE", MobilePlatform.nextOrientation(null));
    }

    @Test
    public void aStepErrorNamesTheStepAndTheLine() {
        RuntimeException e = MobilePlatform.stepError("tap", "tests/m.sel:7", "tap needs an Appium session");
        assertEquals("tap failed at tests/m.sel:7: tap needs an Appium session", e.getMessage());
        assertTrue(e instanceof MobilePlatform.MobileStepError);
        assertEquals("shake failed: x", MobilePlatform.stepError("shake", "", "x").getMessage());
    }

    @Test
    public void localCapabilitiesAreAppium2Shaped() {
        Map<String, String> cfg = new LinkedHashMap<String, String>();
        cfg.put("deviceName", "emulator-5554");
        cfg.put("app", "/apps/demo.apk");
        cfg.put("udid", "0123456789");
        cfg.put("autoGrantPermissions", "true");
        cfg.put("newCommandTimeout", "300");
        cfg.put("appium:noReset", "false");
        cfg.put("platformName", "iOS");     // the key decides the platform
        cfg.put("browserName", "Chrome");   // W3C name: stays bare
        Map<String, Object> c = AppiumCaps.local(MobilePlatform.ANDROID, cfg, "14");
        assertEquals("Android", c.get("platformName"));
        assertEquals("UiAutomator2", c.get("appium:automationName"));
        assertEquals("14", c.get("appium:platformVersion"));
        assertEquals("emulator-5554", c.get("appium:deviceName"));
        assertEquals("/apps/demo.apk", c.get("appium:app"));
        assertEquals("0123456789", c.get("appium:udid"));      // not a number key: stays a string
        assertEquals(Boolean.TRUE, c.get("appium:autoGrantPermissions"));
        assertEquals(Long.valueOf(300), c.get("appium:newCommandTimeout"));
        assertEquals(Boolean.FALSE, c.get("appium:noReset"));
        assertEquals("Chrome", c.get("browserName"));
        assertTrue(!c.containsKey("deviceName") && !c.containsKey("app"));
        Map<String, String> auto = new HashMap<String, String>();
        auto.put("automationName", "Espresso");
        assertEquals("Espresso", AppiumCaps.local(MobilePlatform.ANDROID, auto, null).get("appium:automationName"));
        assertEquals("XCUITest", AppiumCaps.local(MobilePlatform.IOS, null, "null").get("appium:automationName"));
        assertTrue(!AppiumCaps.local(MobilePlatform.IOS, null, "null").containsKey("appium:platformVersion"));
    }

    @Test
    public void serverUrlPrecedence() {
        Map<String, String> env = new HashMap<String, String>(), props = new HashMap<String, String>();
        assertEquals("http://127.0.0.1:4723", AppiumCaps.url(null, env, props));
        assertEquals("http://h:4723/wd/hub", AppiumCaps.url(" http://h:4723/wd/hub ", env, props));
        props.put(AppiumCaps.PROP_URL, "http://p:1");
        assertEquals("http://p:1", AppiumCaps.url("http://cfg", env, props));
        env.put(AppiumCaps.ENV_URL, "http://e:2");
        assertEquals("http://e:2", AppiumCaps.url("http://cfg", env, props));
        assertTrue(AppiumCaps.explicitUrl(env, props));
        assertTrue(!AppiumCaps.explicitUrl(new HashMap<String, String>(), new HashMap<String, String>()));
    }
}
