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
package com.gatf.selenium.emulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** M8: device descriptors (R1) and the emulate statement's protocol calls over a fake CDP / BiDi channel (R2). */
class EmulationTest {

    @TempDir
    File tmp;

    /** Records every call; fails the methods listed in {@code unknown} like a BiDi server that lacks them. */
    static class Fake implements Emulation.Channel {
        final boolean cdp;
        final List<String> sent = new ArrayList<String>();
        final List<Map<String, Object>> params = new ArrayList<Map<String, Object>>();
        final List<String> unknown = new ArrayList<String>();
        Fake(boolean cdp) { this.cdp = cdp; }
        public boolean cdp() { return cdp; }
        public Map<String, Object> send(String method, Map<String, Object> p) {
            if (unknown.contains(method)) throw new RuntimeException("unknown command: " + method);
            sent.add(method);
            params.add(p);
            return Map.of();
        }
        public String context() { return cdp ? null : "ctx-1"; }
        public String origin() { return cdp ? null : "https://shop.test"; }
    }

    @Test
    void bundledDescriptorsLoad() {
        Map<String, Devices.Device> all = Devices.bundled();
        assertThat(all.size()).isGreaterThanOrEqualTo(15);
        Devices.Device d = Devices.find("iphone 13", null);
        assertThat(d.name).isEqualTo("iPhone 13");
        assertThat(d.width).isEqualTo(390);
        assertThat(d.height).isEqualTo(844);
        assertThat(d.scale).isEqualTo(3.0);
        assertThat(d.mobile).isTrue();
        assertThat(d.touch).isTrue();
        assertThat(d.userAgent).contains("iPhone");
        for (Devices.Device x : all.values()) {
            assertThat(x.userAgent).as(x.name).isNotNull();
            assertThat(x.width).as(x.name).isGreaterThan(0);
        }
        Devices.Device land = d.landscape();
        assertThat(land.width).isEqualTo(844);
        assertThat(land.height).isEqualTo(390);
    }

    @Test
    void projectDevicesAddAndOverride() throws Exception {
        File f = new File(tmp, "devices.json");
        Files.writeString(f.toPath(), "{\"devices\": {\"Kiosk\": {\"viewport\": {\"width\": 1080, \"height\": 1920}, \"deviceScaleFactor\": 1,"
                + " \"isMobile\": false, \"hasTouch\": true, \"userAgent\": \"kiosk\"},"
                + " \"iPhone 13\": {\"viewport\": {\"width\": 1, \"height\": 2}, \"deviceScaleFactor\": 1, \"userAgent\": \"x\"}}}");
        assertThat(Devices.find("kiosk", f).height).isEqualTo(1920);
        assertThat(Devices.find("iPhone 13", f).width).isEqualTo(1);
        assertThatThrownBy(() -> Devices.find("iPhone 99", null)).hasMessageContaining("unknown device 'iPhone 99'").hasMessageContaining("did you mean");
    }

    @Test
    void mobileViewportOverCdp() {
        Emulation e = Emulation.parse("device \"Pixel 7\"");
        Fake f = new Fake(true);
        String msg = e.apply(Devices.find(e.deviceName(), null), f);
        assertThat(f.sent).containsExactly("Emulation.setDeviceMetricsOverride", "Emulation.setTouchEmulationEnabled", "Emulation.setUserAgentOverride");
        assertThat(f.params.get(0)).containsEntry("width", 412).containsEntry("height", 915).containsEntry("deviceScaleFactor", 2.625).containsEntry("mobile", true);
        assertThat(f.params.get(1)).containsEntry("enabled", true).containsEntry("maxTouchPoints", 5);
        assertThat((String) f.params.get(2).get("userAgent")).contains("Pixel 7");
        assertThat(msg).contains("Pixel 7 412x915").contains("(CDP)");
        Fake l = new Fake(true);
        Emulation.parse("device 'iPhone 13' landscape").apply(Devices.find("iPhone 13", null), l);
        assertThat(l.params.get(0)).containsEntry("width", 844).containsEntry("height", 390);
    }

    @Test
    void mobileViewportOverBidiWarnsAboutTouch() {
        Fake f = new Fake(false);
        String msg = Emulation.parse("device \"iPhone 13\"").apply(Devices.find("iPhone 13", null), f);
        assertThat(f.sent).containsExactly("browsingContext.setViewport", "emulation.setUserAgentOverride");
        assertThat(f.params.get(0)).containsEntry("context", "ctx-1").containsEntry("devicePixelRatio", 3.0);
        assertThat(msg).contains("(BiDi)").contains("touch / mobile-layout emulation needs CDP");
    }

    @Test
    @SuppressWarnings("unchecked")
    void everySettingOverCdp() {
        String[][] cases = {
            { "geolocation 51.5072 -0.1276", "Browser.grantPermissions,Emulation.setGeolocationOverride" },
            { "locale de_DE", "Emulation.setLocaleOverride" },
            { "timezone Europe/Berlin", "Emulation.setTimezoneOverride" },
            { "permissions camera,notifications", "Browser.grantPermissions" },
            { "permissions clear", "Browser.resetPermissions" },
            { "colorscheme dark", "Emulation.setEmulatedMedia" },
            { "offline", "Network.enable,Network.emulateNetworkConditions" },
            { "network slow3g", "Network.enable,Network.emulateNetworkConditions" },
            { "viewport 1024 768 2", "Emulation.setDeviceMetricsOverride" },
        };
        for (String[] c : cases) {
            Fake f = new Fake(true);
            Emulation.parse(c[0]).apply(null, f);
            assertThat(String.join(",", f.sent)).as(c[0]).isEqualTo(c[1]);
        }
        Fake g = new Fake(true);
        Emulation.parse("permissions camera,microphone").apply(null, g);
        assertThat((List<Object>) g.params.get(0).get("permissions")).containsExactly("videoCapture", "audioCapture");
        Fake n = new Fake(true);
        Emulation.parse("network 1600 750 150").apply(null, n);
        assertThat(n.params.get(1)).containsEntry("offline", false).containsEntry("latency", 150.0).containsEntry("downloadThroughput", 200000.0);
        Fake loc = new Fake(true);
        Emulation.parse("locale de_DE").apply(null, loc);
        assertThat(loc.params.get(0)).containsEntry("locale", "de-DE");
        Fake r = new Fake(true);
        Emulation.parse("reset").apply(null, r);
        assertThat(r.sent).contains("Emulation.clearDeviceMetricsOverride", "Emulation.clearGeolocationOverride", "Browser.resetPermissions",
                "Emulation.setUserAgentOverride");
        assertThat(r.params.get(r.sent.indexOf("Emulation.setUserAgentOverride"))).containsEntry("userAgent", "");
    }

    @Test
    @SuppressWarnings("unchecked")
    void cdpGrantsAccumulateAcrossStatements() {
        java.util.Set<String> granted = new java.util.LinkedHashSet<String>();
        Fake a = new Fake(true), b = new Fake(true), c = new Fake(true);
        Emulation.parse("permissions notifications").apply(null, a, granted);
        Emulation.parse("geolocation 1 2").apply(null, b, granted);   // must not revoke notifications
        assertThat((List<Object>) b.params.get(0).get("permissions")).containsExactly("notifications", "geolocation");
        Emulation.parse("permissions clear").apply(null, c, granted);
        assertThat(granted).isEmpty();
        Fake n = new Fake(true);
        Emulation.parse("network fast4g").apply(null, n);
        assertThat(n.params.get(1)).containsEntry("latency", 165.0);
        Fake m = new Fake(true);
        Emulation.parse("device \"Pixel 7\"").apply(Devices.find("Pixel 7", null), m);
        assertThat((Map<String, Object>) m.params.get(2).get("userAgentMetadata")).containsEntry("mobile", true).containsEntry("platform", "Android");
        assertThat(m.params.get(2)).containsEntry("platform", "Linux armv8l");
    }

    @Test
    void bidiCapabilityMatrix() {
        Fake geo = new Fake(false);
        Emulation.parse("geolocation 48.85 2.35 5").apply(null, geo);
        assertThat(geo.sent).containsExactly("permissions.setPermission", "emulation.setGeolocationOverride");
        assertThat(geo.params.get(0)).containsEntry("origin", "https://shop.test");
        Fake off = new Fake(false);
        Emulation.parse("offline").apply(null, off);
        assertThat(off.sent).containsExactly("emulation.setNetworkConditions");
        assertThatThrownBy(() -> Emulation.parse("colorscheme dark").apply(null, new Fake(false))).isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("needs CDP");
        assertThatThrownBy(() -> Emulation.parse("network fast3g").apply(null, new Fake(false))).hasMessageContaining("throttling needs CDP");
        assertThatThrownBy(() -> Emulation.parse("permissions clear").apply(null, new Fake(false))).hasMessageContaining("needs CDP");
        assertThatThrownBy(() -> Emulation.parse("permissions midi-sysex").apply(null, new Fake(false))).hasMessageContaining("no WebDriver BiDi permission name");
        Fake reset = new Fake(false);
        reset.unknown.add("emulation.setUserAgentOverride");
        String rm = Emulation.parse("reset").apply(null, reset);   // an unimplemented reset call is skipped, not fatal
        assertThat(rm).contains("emulation.setUserAgentOverride is not implemented").contains("permission grants stay");
        assertThat(reset.sent).contains("emulation.setLocaleOverride");
        Fake early = new Fake(false) {
            @Override public String origin() { return null; }
        };
        assertThat(Emulation.parse("geolocation 1 2").apply(null, early)).contains("permission was not granted");
        Fake old = new Fake(false);
        old.unknown.add("emulation.setLocaleOverride");
        assertThatThrownBy(() -> Emulation.parse("locale fr-FR").apply(null, old)).isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("does not implement emulation.setLocaleOverride yet");
    }

    @Test
    void parseValidation() {
        assertThatThrownBy(() -> Emulation.parse("")).hasMessageContaining("needs a setting");
        assertThatThrownBy(() -> Emulation.parse("gravity 9.8")).hasMessageContaining("unknown setting 'gravity'");
        assertThatThrownBy(() -> Emulation.parse("geolocation 95 0")).hasMessageContaining("latitude");
        assertThatThrownBy(() -> Emulation.parse("geolocation x 0")).hasMessageContaining("must be a number");
        assertThatThrownBy(() -> Emulation.parse("timezone 'Europe Berlin'")).hasMessageContaining("IANA");
        assertThatThrownBy(() -> Emulation.parse("permissions teleport")).hasMessageContaining("unknown permission 'teleport'");
        assertThatThrownBy(() -> Emulation.parse("colorscheme purple")).hasMessageContaining("dark|light|no-preference");
        assertThatThrownBy(() -> Emulation.parse("network 5g")).hasMessageContaining("slow3g");
        assertThatThrownBy(() -> Emulation.parse("viewport 0 10")).hasMessageContaining("positive integer");
        assertThatThrownBy(() -> Emulation.parse("device")).hasMessageContaining("emulate device");
        assertThat(Emulation.parse("colour-scheme light").op).isEqualTo("colorscheme");
        assertThat(Emulation.parse("device \"Galaxy S9+\" landscape").landscape()).isTrue();
    }
}
