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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * M8: device descriptors for {@code emulate device "<name>"}: the bundled {@code gatf-devices/devices.json}, plus an
 * optional {@code devices.json} in the test-case dir that adds or overrides devices (same shape). Names match
 * case-insensitively. Pure (Jackson only).
 */
public final class Devices {

    /** One device: viewport in CSS pixels. */
    public static final class Device {
        public final String name, userAgent, platform;
        public final int width, height;
        public final double scale;
        public final boolean mobile, touch;

        public Device(String name, int width, int height, double scale, boolean mobile, boolean touch, String userAgent, String platform) {
            if (width <= 0 || height <= 0) throw new IllegalArgumentException("device '" + name + "': viewport must be positive");
            if (!(scale > 0)) throw new IllegalArgumentException("device '" + name + "': deviceScaleFactor must be positive");
            this.name = name;
            this.width = width;
            this.height = height;
            this.scale = scale;
            this.mobile = mobile;
            this.touch = touch;
            this.userAgent = userAgent;
            this.platform = platform;
        }

        /** The same device turned sideways. */
        public Device landscape() {
            return new Device(name + " landscape", height, width, scale, mobile, touch, userAgent, platform);
        }

        @Override
        public String toString() {
            return name + " " + width + "x" + height + "@" + scale + "x" + (mobile ? " mobile" : "") + (touch ? " touch" : "");
        }
    }

    private static final ObjectMapper OM = new ObjectMapper();
    private static volatile Map<String, Device> bundled;

    private Devices() {}

    /** The bundled descriptors, keyed by lower-case name (insertion order = file order). */
    @SuppressWarnings("unchecked")
    public static Map<String, Device> bundled() {
        Map<String, Device> b = bundled;
        if (b == null) {
            try (InputStream in = Devices.class.getResourceAsStream("/gatf-devices/devices.json")) {
                if (in == null) throw new IllegalStateException("gatf-devices/devices.json is missing from the classpath");
                bundled = b = parse(OM.readValue(in, Map.class));
            } catch (IOException e) {
                throw new IllegalStateException("cannot read gatf-devices/devices.json: " + e.getMessage(), e);
            }
        }
        return b;
    }

    /** Parses {@code {"devices": {name: {viewport:{width,height}, deviceScaleFactor, isMobile, hasTouch, userAgent, platform}}}}. */
    @SuppressWarnings("unchecked")
    static Map<String, Device> parse(Map<String, Object> doc) {
        Map<String, Device> out = new LinkedHashMap<String, Device>();
        Object devs = doc == null ? null : doc.get("devices");
        if (!(devs instanceof Map)) throw new IllegalArgumentException("devices.json: expected {\"devices\": {name: {...}}}");
        for (Map.Entry<String, Object> e : ((Map<String, Object>) devs).entrySet()) {
            if (!(e.getValue() instanceof Map)) throw new IllegalArgumentException("devices.json: '" + e.getKey() + "' is not an object");
            Map<String, Object> d = (Map<String, Object>) e.getValue();
            Map<String, Object> vp = d.get("viewport") instanceof Map ? (Map<String, Object>) d.get("viewport") : new LinkedHashMap<String, Object>();
            out.put(e.getKey().toLowerCase(Locale.ROOT), new Device(e.getKey(), num(vp.get("width")), num(vp.get("height")),
                    d.get("deviceScaleFactor") instanceof Number ? ((Number) d.get("deviceScaleFactor")).doubleValue() : 1,
                    Boolean.TRUE.equals(d.get("isMobile")), Boolean.TRUE.equals(d.get("hasTouch")),
                    d.get("userAgent") == null ? null : String.valueOf(d.get("userAgent")),
                    d.get("platform") == null ? null : String.valueOf(d.get("platform"))));
        }
        return out;
    }

    private static int num(Object o) { return o instanceof Number ? ((Number) o).intValue() : -1; }

    /** Bundled + {@code custom} (a devices.json; null or missing = none). */
    @SuppressWarnings("unchecked")
    public static Map<String, Device> all(File custom) {
        Map<String, Device> m = new LinkedHashMap<String, Device>(bundled());
        if (custom != null && custom.isFile()) {
            try {
                m.putAll(parse(OM.readValue(custom, Map.class)));
            } catch (IOException e) {
                throw new IllegalArgumentException(custom + ": " + e.getMessage(), e);
            }
        }
        return m;
    }

    /** The named device (case-insensitive); an unknown name lists the closest known ones. */
    public static Device find(String name, File custom) {
        Map<String, Device> m = all(custom);
        String key = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        Device d = m.get(key);
        if (d != null) return d;
        List<String> close = new ArrayList<String>();
        for (Device x : m.values()) {
            String n = x.name.toLowerCase(Locale.ROOT);
            if (!key.isEmpty() && (n.contains(key) || key.contains(n.split(" ")[0]))) close.add(x.name);
        }
        List<String> names = new ArrayList<String>();
        for (Device x : m.values()) names.add(x.name);
        throw new IllegalArgumentException("unknown device '" + name + "'" + (close.isEmpty() ? "" : ", did you mean " + close + "?")
                + " Known: " + names + (custom != null ? " (add your own in " + custom.getName() + ")" : ""));
    }
}
