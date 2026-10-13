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

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * H5 (Δ158): the capabilities and server URL of a local Appium 2 session ({@code open appium-android|appium-ios}). Pure.
 * <ul>
 * <li>URL: {@code GATF_APPIUM_URL} / {@code -Dgatf.appium.url}, else the driver config's {@code url}, else
 *     {@code http://127.0.0.1:4723} (Appium 2 serves at {@code /}; Appium 1's {@code /wd/hub} is used only when configured).</li>
 * <li>Appium 2 accepts only W3C capability names or vendor-prefixed ones, so every other config key gets
 *     {@code appium:} ({@code deviceName} → {@code appium:deviceName}).</li>
 * <li>{@code platformName} comes from the key; {@code appium:automationName} defaults to UiAutomator2 / XCUITest; the
 *     config {@code version} becomes {@code appium:platformVersion}. Config values win over these defaults.</li>
 * <li>{@code "true"}/{@code "false"} become booleans, and the known numeric Appium keys become numbers, because the
 *     config map holds strings only.</li>
 * </ul>
 */
public final class AppiumCaps {

    private AppiumCaps() {}

    public static final String DEFAULT_URL = "http://127.0.0.1:4723";
    public static final String ENV_URL = "GATF_APPIUM_URL", PROP_URL = "gatf.appium.url";

    static final Set<String> W3C = new HashSet<String>(Arrays.asList("browserName", "browserVersion", "platformName",
            "acceptInsecureCerts", "pageLoadStrategy", "proxy", "setWindowRect", "timeouts", "strictFileInteractability",
            "unhandledPromptBehavior", "webSocketUrl", "userAgent"));
    static final Set<String> NUMERIC = new HashSet<String>(Arrays.asList("newCommandTimeout", "systemPort", "wdaLocalPort",
            "mjpegServerPort", "chromedriverPort", "adbExecTimeout", "androidInstallTimeout", "appWaitDuration",
            "uiautomator2ServerLaunchTimeout", "uiautomator2ServerInstallTimeout", "uiautomator2ServerReadTimeout",
            "wdaLaunchTimeout", "wdaConnectionTimeout", "wdaStartupRetries", "wdaStartupRetryInterval",
            "autoWebviewTimeout", "webviewConnectTimeout", "commandTimeouts", "simulatorStartupTimeout"));

    /** True when GATF_APPIUM_URL / -Dgatf.appium.url names a server explicitly (it then wins over a device cloud). */
    public static boolean explicitUrl(Map<String, String> env, Map<String, String> props) {
        return pick(env == null ? null : env.get(ENV_URL), props == null ? null : props.get(PROP_URL)) != null;
    }

    public static String url(String configUrl, Map<String, String> env, Map<String, String> props) {
        String u = pick(env == null ? null : env.get(ENV_URL), props == null ? null : props.get(PROP_URL));
        if (u == null) u = pick(configUrl, null);
        return u == null ? DEFAULT_URL : u;
    }

    private static String pick(String a, String b) {
        if (a != null && !a.trim().isEmpty()) return a.trim();
        if (b != null && !b.trim().isEmpty()) return b.trim();
        return null;
    }

    /** The W3C capabilities for a local Appium 2 session. */
    public static Map<String, Object> local(MobilePlatform p, Map<String, String> configCaps, String version) {
        Map<String, Object> caps = new LinkedHashMap<String, Object>();
        caps.put("platformName", p.platformName);
        caps.put("appium:automationName", p.automationName);
        if (version != null && !version.trim().isEmpty() && !version.trim().equals("null")) {
            caps.put("appium:platformVersion", version.trim());
        }
        if (configCaps != null) {
            for (Map.Entry<String, String> e : configCaps.entrySet()) {
                if (e.getKey() == null || e.getKey().trim().isEmpty() || e.getValue() == null) continue;
                String k = key(e.getKey().trim());
                if (k.equals("platformName")) continue;   // the key decides the platform
                caps.put(k, value(k, e.getValue()));
            }
        }
        return caps;
    }

    /** W3C names and names with a vendor prefix stay; every other name gets {@code appium:}. */
    public static String key(String k) {
        return W3C.contains(k) || k.indexOf(':') > 0 ? k : "appium:" + k;
    }

    static Object value(String key, String v) {
        String t = v.trim();
        if (t.equalsIgnoreCase("true")) return Boolean.TRUE;
        if (t.equalsIgnoreCase("false")) return Boolean.FALSE;
        String bare = key.startsWith("appium:") ? key.substring(7) : key;
        if (NUMERIC.contains(bare) && t.matches("-?\\d{1,15}")) return Long.valueOf(t);
        return v;
    }

    /** The platform name for logs: "Android" / "iOS" with the browser for mobile web ("Android chrome"). */
    public static String label(MobilePlatform p, Map<String, Object> caps) {
        Object b = caps == null ? null : caps.get("browserName");
        return p.platformName + (b == null || String.valueOf(b).trim().isEmpty() ? "" : " " + String.valueOf(b).trim().toLowerCase(Locale.ROOT));
    }
}
