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

import java.util.Collections;
import java.util.Locale;
import java.util.Map;

/**
 * H5 (Δ158): which Appium 2 platform a session is, and the documented platform gestures. Pure.
 * <ul>
 * <li>A session is an Appium session when its {@code platformName} capability is Android or iOS (desktop browsers
 *     report linux / windows / mac); everything else keeps the desktop path, where touch commands are a step error.</li>
 * <li>{@code hidekeypad} → {@code mobile: hideKeyboard} (UiAutomator2 and XCUITest).</li>
 * <li>{@code shake} → {@code mobile: shake} (XCUITest; the driver documents it for simulators). UiAutomator2 has none.</li>
 * <li>{@code rotate}: neither driver documents a {@code mobile:} command for device orientation; both implement
 *     Appium's orientation endpoint ({@code GET/POST /session/:id/orientation}), which java-client sends through
 *     {@code SupportsRotation}. {@link #nextOrientation} picks the target.</li>
 * </ul>
 */
public enum MobilePlatform {
    ANDROID("Android", "UiAutomator2"),
    IOS("iOS", "XCUITest");

    public final String platformName, automationName;

    MobilePlatform(String platformName, String automationName) {
        this.platformName = platformName;
        this.automationName = automationName;
    }

    /** android / ios (any case), else null. */
    public static MobilePlatform of(Object platformName) {
        String p = platformName == null ? "" : String.valueOf(platformName).trim().toLowerCase(Locale.ROOT);
        if (p.equals("android")) return ANDROID;
        if (p.equals("ios")) return IOS;
        return null;
    }

    /** The platform of a GATF browser key: appium-android / appium-ios, else null. */
    public static MobilePlatform ofKey(String key) {
        String k = key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
        if (k.equals("appium-android")) return ANDROID;
        if (k.equals("appium-ios")) return IOS;
        return null;
    }

    /** A {@code mobile:} execute method and its single argument object. */
    public static final class MobileCall {
        public final String script;
        public final Map<String, Object> args;
        MobileCall(String script, Map<String, Object> args) {
            this.script = script;
            this.args = args;
        }
    }

    public MobileCall hideKeypad() {
        return new MobileCall("mobile: hideKeyboard", Collections.<String, Object>emptyMap());
    }

    /** iOS only; on Android an {@link UnsupportedOperationException} with the reason. */
    public MobileCall shake() {
        if (this != IOS) {
            throw new UnsupportedOperationException("shake is not available on Android: UiAutomator2 has no shake gesture "
                    + "(XCUITest's mobile: shake runs on iOS simulators)");
        }
        return new MobileCall("mobile: shake", Collections.<String, Object>emptyMap());
    }

    /** {@code rotate} toggles: any LANDSCAPE* → PORTRAIT, anything else → LANDSCAPE. */
    public static String nextOrientation(Object current) {
        String c = current == null ? "" : String.valueOf(current).trim().toUpperCase(Locale.ROOT);
        return c.startsWith("LANDSCAPE") ? "PORTRAIT" : "LANDSCAPE";
    }

    /**
     * A failed mobile step: {@code "<step> failed at <file:line>: <reason>"}. It is a plain step failure, so a soft
     * statement ({@code ??~}) records it and continues, and the script's other steps and sessions are unaffected.
     */
    public static RuntimeException stepError(String step, String where, String reason) {
        return new MobileStepError(step + " failed" + (where == null || where.isEmpty() ? "" : " at " + where) + ": " + reason);
    }

    /** See {@link MobilePlatform#stepError}. */
    public static final class MobileStepError extends RuntimeException {
        private static final long serialVersionUID = 1L;
        MobileStepError(String m) { super(m); }
    }
}
