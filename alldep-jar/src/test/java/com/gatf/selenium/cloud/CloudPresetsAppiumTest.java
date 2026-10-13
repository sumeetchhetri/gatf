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
package com.gatf.selenium.cloud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.gatf.selenium.cloud.CloudPresets.CloudError;
import com.gatf.selenium.cloud.CloudPresets.Preset;
import com.gatf.selenium.cloud.CloudPresets.Provider;
import com.gatf.selenium.mobile.AppiumCaps;
import com.gatf.selenium.mobile.MobilePlatform;
import com.gatf.selenium.mobile.TouchGestures;

/** Δ158: real-device Appium presets (BrowserStack App Automate, Sauce Real Devices, LambdaTest Real Devices) and the leak test. */
public class CloudPresetsAppiumTest {

    static final String KEY = "sEcReT-aCcEsS-kEy-9f8e7d";
    static final String USER = "alice_cloud_user";

    static Map<String, String> env(String... kv) {
        Map<String, String> m = new HashMap<String, String>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }

    static Map<String, String> cloud(String provider, String... more) {
        Map<String, String> m = env("GATF_CLOUD", provider, "BROWSERSTACK_USERNAME", USER, "BROWSERSTACK_ACCESS_KEY", KEY,
                "SAUCE_USERNAME", USER, "SAUCE_ACCESS_KEY", KEY, "LT_USERNAME", USER, "LT_ACCESS_KEY", KEY,
                "GATF_CLOUD_DEVICE", "Google Pixel 7", "GATF_CLOUD_APP", "bs://f00d");
        m.putAll(env(more));
        return m;
    }

    /** A typical local appium-android config: app path, udid and ports must not reach a cloud. */
    static Map<String, Object> localCaps() {
        Map<String, String> cfg = new LinkedHashMap<String, String>();
        cfg.put("app", "/home/me/demo.apk");
        cfg.put("udid", "emulator-5554");
        cfg.put("deviceName", "Pixel_7_API_34");
        cfg.put("systemPort", "8201");
        cfg.put("appPackage", "com.example.demo");
        cfg.put("autoGrantPermissions", "true");
        return AppiumCaps.local(MobilePlatform.ANDROID, cfg, "14");
    }

    static List<String> outputs(Preset p) {
        List<String> o = new ArrayList<String>();
        o.add(p.toString());
        o.add(p.describe());
        o.add(p.remoteUrl);
        o.add(TouchGestures.json(p.capabilities));
        o.add(String.valueOf(p.capabilities));
        o.addAll(CloudPresets.statusScripts(p.provider, "m.sel", false, "boom"));
        return o;
    }

    static void assertNoSecret(String s) {
        assertFalse(s.contains(KEY), "access key leaked: " + s.replace(KEY, "<KEY>"));
        assertFalse(s.contains(USER), "user name leaked: " + s.replace(USER, "<USER>"));
    }

    @Test
    public void browserStackAppAutomate() {
        Preset p = CloudPresets.buildAppium(MobilePlatform.ANDROID, cloud("bs", "GATF_CLOUD_OS_VERSION", "13.0", "GATF_CLOUD_BUILD", "nightly"),
                env(), localCaps(), null);
        assertEquals(Provider.BROWSERSTACK, p.provider);
        assertEquals("https://hub-cloud.browserstack.com/wd/hub", p.remoteUrl);
        assertEquals("{\"platformName\":\"Android\",\"appium:automationName\":\"UiAutomator2\",\"appium:appPackage\":\"com.example.demo\","
                + "\"appium:autoGrantPermissions\":true,\"appium:app\":\"bs://f00d\",\"bstack:options\":{\"deviceName\":\"Google Pixel 7\","
                + "\"osVersion\":\"13.0\",\"buildName\":\"nightly\"}}", TouchGestures.json(p.capabilities));
        assertTrue(p.realDevice);
        assertEquals("browserstack: Android 13.0 on Google Pixel 7 (real device) via https://hub-cloud.browserstack.com/wd/hub", p.describe());
        assertEquals(USER, p.username());
        assertEquals(KEY, p.accessKey());
        for (String o : outputs(p)) assertNoSecret(o);
    }

    @Test
    public void sauceRealDevices() {
        Preset p = CloudPresets.buildAppium(MobilePlatform.IOS, cloud("sauce", "GATF_CLOUD_DEVICE", "iPhone 1[45].*", "GATF_CLOUD_APP",
                "storage:filename=demo.ipa", "SAUCE_REGION", "eu-central-1"), env(), AppiumCaps.local(MobilePlatform.IOS, null, null), null);
        assertEquals("https://ondemand.eu-central-1.saucelabs.com/wd/hub", p.remoteUrl);
        assertEquals("{\"platformName\":\"iOS\",\"appium:automationName\":\"XCUITest\",\"appium:app\":\"storage:filename=demo.ipa\","
                + "\"appium:deviceName\":\"iPhone 1[45].*\",\"sauce:options\":{\"appiumVersion\":\"latest\"}}", TouchGestures.json(p.capabilities));
        for (String o : outputs(p)) assertNoSecret(o);
    }

    @Test
    public void lambdaTestRealDevices() {
        Preset p = CloudPresets.buildAppium(MobilePlatform.ANDROID, cloud("lt", "GATF_CLOUD_APP", "lt://APP1016", "GATF_CLOUD_OS_VERSION", "14"),
                env(), localCaps(), null);
        assertEquals("https://mobile-hub.lambdatest.com/wd/hub", p.remoteUrl);
        assertEquals("{\"platformName\":\"Android\",\"appium:automationName\":\"UiAutomator2\",\"appium:appPackage\":\"com.example.demo\","
                + "\"appium:autoGrantPermissions\":true,\"lt:options\":{\"platformName\":\"Android\",\"deviceName\":\"Google Pixel 7\","
                + "\"platformVersion\":\"14\",\"isRealMobile\":true,\"app\":\"lt://APP1016\",\"w3c\":true}}", TouchGestures.json(p.capabilities));
        for (String o : outputs(p)) assertNoSecret(o);
    }

    @Test
    public void mobileWebNeedsNoApp() {
        Map<String, String> cfg = new HashMap<String, String>();
        cfg.put("browserName", "Chrome");
        Map<String, String> e = cloud("bs");
        e.remove("GATF_CLOUD_APP");
        Preset p = CloudPresets.buildAppium(MobilePlatform.ANDROID, e, env(), AppiumCaps.local(MobilePlatform.ANDROID, cfg, null), null);
        assertEquals("Chrome", p.capabilities.get("browserName"));
        assertNull(p.capabilities.get("appium:app"));
        assertTrue(p.describe().startsWith("browserstack: Android chrome on Google Pixel 7"), p.describe());
    }

    static String err(Map<String, String> e, Map<String, Object> caps, Map<String, Object> opts) {
        CloudError x = assertThrows(CloudError.class, () -> CloudPresets.buildAppium(MobilePlatform.ANDROID, e, env(), caps, opts));
        assertNoSecret(x.getMessage());
        return x.getMessage();
    }

    @Test
    public void configurationErrorsAreClearAndNeverEchoACredential() {
        Map<String, String> e = cloud("bs");
        e.remove("GATF_CLOUD_DEVICE");
        assertTrue(err(e, localCaps(), null).contains("needs GATF_CLOUD_DEVICE"));
        e = cloud("bs");
        e.remove("GATF_CLOUD_APP");
        assertTrue(err(e, localCaps(), null).contains("needs GATF_CLOUD_APP"));
        e = cloud("bs");
        e.remove("BROWSERSTACK_ACCESS_KEY");
        assertTrue(err(e, localCaps(), null).contains("missing: BROWSERSTACK_ACCESS_KEY"));
        assertTrue(err(cloud("bs", "GATF_CLOUD_DEVICE", "Pixel; rm -rf"), localCaps(), null).contains("unsupported characters"));
        // a credential in the app URL, the device name, the config, or the vendor options: refused, and never echoed
        assertTrue(err(cloud("bs", "GATF_CLOUD_APP", "https://" + USER + ":" + KEY + "@apps.example/a.apk"), localCaps(), null).contains("must not carry credentials"));
        assertTrue(err(cloud("bs", "GATF_CLOUD_APP", "bs://" + KEY), localCaps(), null).contains("contains a browserstack credential"));
        assertTrue(err(cloud("bs", "GATF_CLOUD_DEVICE", "Pixel " + KEY), localCaps(), null).contains("credential"));
        Map<String, Object> caps = localCaps();
        caps.put("appium:accessKey", "whatever");
        assertTrue(err(cloud("bs"), caps, null).contains("must not carry credentials (capability 'appium:accessKey')"));
        caps = localCaps();
        caps.put("appium:appPackage", "com." + KEY);
        assertTrue(err(cloud("bs"), caps, null).contains("contains a browserstack credential"));
        Map<String, Object> opts = new HashMap<String, Object>();
        opts.put("userName", USER);
        assertTrue(err(cloud("bs"), localCaps(), opts).contains("GATF_CLOUD_OPTIONS must not carry credentials"));
        assertTrue(err(cloud("nope"), localCaps(), null).contains("unknown device cloud"));
    }

    @Test
    public void vendorOptionsMerge() {
        Map<String, Object> opts = new HashMap<String, Object>();
        opts.put("appiumVersion", "2.11.0");
        Preset p = CloudPresets.buildAppium(MobilePlatform.ANDROID, cloud("sauce"), env(), localCaps(), opts);
        @SuppressWarnings("unchecked")
        Map<String, Object> so = (Map<String, Object>) p.capabilities.get("sauce:options");
        assertEquals("2.11.0", so.get("appiumVersion"));
    }
}
