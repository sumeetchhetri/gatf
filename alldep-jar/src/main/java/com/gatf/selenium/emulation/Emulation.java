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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * M8: {@code emulate …} — parsing, plus the protocol calls each form needs on each transport (capability matrix):
 * <pre>
 *   emulate device "iPhone 13" [landscape]    viewport + scale + mobile/touch + user agent
 *   emulate viewport 1024 768 [scale]
 *   emulate geolocation 51.5072 -0.1276 [accuracy]   (also grants the geolocation permission)
 *   emulate locale de-DE
 *   emulate timezone Europe/Berlin
 *   emulate permissions geolocation,notifications | emulate permissions clear
 *   emulate colorscheme dark|light|no-preference
 *   emulate offline | emulate online
 *   emulate network slow3g|slow4g|fast4g|off | emulate network {down kbit/s} {up kbit/s} {latency ms}
 *   emulate reset
 * </pre>
 * CDP (Chromium) supports every form. WebDriver BiDi (Firefox, BiDi sessions) gets viewport, user agent, geolocation,
 * locale, timezone, permission grants and offline; colour scheme, throttling and clearing permissions need CDP and fail
 * with a clear message. Touch / mobile flags have no BiDi equivalent and are reported as a warning. Pure: the calls are
 * data, sent by a {@link Channel}.
 */
public final class Emulation {

    /** Where the calls go: CDP ({@code executeCdpCommand}) or BiDi (raw command on the session's socket). */
    public interface Channel {
        boolean cdp();
        Map<String, Object> send(String method, Map<String, Object> params);
        /** BiDi browsing-context id of the current tab (null on CDP). */
        String context();
        /** Origin of the current page (permission grants on BiDi), e.g. https://shop.test; null if unknown. */
        String origin();
        /** H3 (Δ153): the per-test browser context the current tab belongs to (null: the default context). */
        default String userContext() { return null; }
    }

    /** One protocol call. */
    public static final class Call {
        public final String method;
        public final Map<String, Object> params;

        Call(String method, Map<String, Object> params) {
            this.method = method;
            this.params = params;
        }

        @Override
        public String toString() { return method + " " + params; }
    }

    /** The calls for one statement on one transport, plus warnings for what that transport cannot emulate. */
    public static final class Plan {
        public final List<Call> calls = new ArrayList<Call>();
        public final List<String> warnings = new ArrayList<String>();
    }

    public final String op;
    public final List<String> args;

    static final List<String> OPS = Arrays.asList("device", "viewport", "geolocation", "locale", "timezone", "permissions",
            "colorscheme", "offline", "online", "network", "reset");

    /** Chrome DevTools' presets (names as of Chrome 125; fast3g = the old name of slow4g): {download B/s, upload B/s, latency ms}. */
    static final Map<String, double[]> NETWORK_PRESETS = new LinkedHashMap<String, double[]>();
    static {
        NETWORK_PRESETS.put("slow3g", new double[] { 500 * 1000 / 8.0 * .8, 500 * 1000 / 8.0 * .8, 400 * 5 });
        NETWORK_PRESETS.put("slow4g", new double[] { 1.6 * 1000 * 1000 / 8 * .9, 750 * 1000 / 8.0 * .9, 150 * 3.75 });
        NETWORK_PRESETS.put("fast3g", NETWORK_PRESETS.get("slow4g"));
        NETWORK_PRESETS.put("fast4g", new double[] { 9 * 1000 * 1000 / 8.0 * .9, 1.5 * 1000 * 1000 / 8 * .9, 60 * 2.75 });
    }

    /** CDP-only permission names (no single web permission name for WebDriver BiDi). */
    static final List<String> CDP_ONLY_PERMISSIONS = Arrays.asList("midi-sysex", "sensors");

    /** Web permission names → CDP {@code Browser.grantPermissions} names. */
    static final Map<String, String> CDP_PERMISSIONS = new LinkedHashMap<String, String>();
    static {
        String[][] m = { { "geolocation", "geolocation" }, { "notifications", "notifications" }, { "camera", "videoCapture" },
                { "microphone", "audioCapture" }, { "clipboard-read", "clipboardReadWrite" }, { "clipboard-write", "clipboardSanitizedWrite" },
                { "midi", "midi" }, { "midi-sysex", "midiSysex" }, { "background-sync", "backgroundSync" }, { "persistent-storage", "durableStorage" },
                { "sensors", "sensors" }, { "idle-detection", "idleDetection" }, { "storage-access", "storageAccess" } };
        for (String[] p : m) CDP_PERMISSIONS.put(p[0], p[1]);
    }

    private Emulation(String op, List<String> args) {
        this.op = op;
        this.args = Collections.unmodifiableList(args);
    }

    /** Parses the text after {@code emulate}; throws IllegalArgumentException with the expected form. */
    public static Emulation parse(String spec) {
        List<String> t = tokens(spec == null ? "" : spec.trim());
        if (t.isEmpty()) throw new IllegalArgumentException("emulate needs a setting: " + String.join(", ", OPS));
        String op = t.get(0).toLowerCase(Locale.ROOT).replace("-", "");
        if (op.equals("colourscheme")) op = "colorscheme";
        List<String> a = new ArrayList<String>(t.subList(1, t.size()));
        switch (op) {
            case "device":
                if (a.isEmpty() || a.size() > 2 || (a.size() == 2 && !a.get(1).equalsIgnoreCase("landscape")))
                    throw new IllegalArgumentException("emulate device \"<name>\" [landscape]");
                break;
            case "viewport":
                if (a.size() < 2 || a.size() > 3) throw new IllegalArgumentException("emulate viewport <width> <height> [scale]");
                positiveInt(a.get(0), "width");
                positiveInt(a.get(1), "height");
                if (a.size() == 3 && !(number(a.get(2), "scale") > 0)) throw new IllegalArgumentException("emulate viewport: scale must be positive");
                break;
            case "geolocation":
                if (a.size() < 2 || a.size() > 3) throw new IllegalArgumentException("emulate geolocation <latitude> <longitude> [accuracy]");
                double lat = number(a.get(0), "latitude"), lon = number(a.get(1), "longitude");
                if (lat < -90 || lat > 90) throw new IllegalArgumentException("emulate geolocation: latitude must be between -90 and 90");
                if (lon < -180 || lon > 180) throw new IllegalArgumentException("emulate geolocation: longitude must be between -180 and 180");
                if (a.size() == 3 && number(a.get(2), "accuracy") < 0) throw new IllegalArgumentException("emulate geolocation: accuracy must be >= 0");
                break;
            case "locale":
                if (a.size() != 1 || !a.get(0).matches("[A-Za-z]{2,3}([-_][A-Za-z0-9]{2,8})*"))
                    throw new IllegalArgumentException("emulate locale <BCP 47 tag, e.g. de-DE>");
                break;
            case "timezone":
                if (a.size() != 1 || !a.get(0).matches("[A-Za-z][A-Za-z0-9_+\\-]*(/[A-Za-z0-9_+\\-]+)*"))
                    throw new IllegalArgumentException("emulate timezone <IANA id, e.g. Europe/Berlin>");
                break;
            case "permissions":
                if (a.size() != 1) throw new IllegalArgumentException("emulate permissions <name,name,...> | emulate permissions clear");
                if (!a.get(0).equalsIgnoreCase("clear")) {
                    for (String p : a.get(0).split(",")) {
                        if (!CDP_PERMISSIONS.containsKey(p.trim().toLowerCase(Locale.ROOT)))
                            throw new IllegalArgumentException("emulate permissions: unknown permission '" + p.trim() + "' (known: " + CDP_PERMISSIONS.keySet() + ")");
                    }
                }
                break;
            case "colorscheme":
                if (a.size() != 1 || !Arrays.asList("dark", "light", "no-preference").contains(a.get(0).toLowerCase(Locale.ROOT)))
                    throw new IllegalArgumentException("emulate colorscheme dark|light|no-preference");
                break;
            case "offline": case "online": case "reset":
                if (!a.isEmpty()) throw new IllegalArgumentException("emulate " + op + " takes no value");
                break;
            case "network":
                if (a.size() == 1) {
                    String p = a.get(0).toLowerCase(Locale.ROOT);
                    if (!p.equals("off") && !NETWORK_PRESETS.containsKey(p))
                        throw new IllegalArgumentException("emulate network " + String.join("|", NETWORK_PRESETS.keySet()) + "|off, or <down kbit/s> <up kbit/s> <latency ms>");
                } else if (a.size() == 3) {
                    for (int i = 0; i < 3; i++) if (number(a.get(i), "network value") < 0) throw new IllegalArgumentException("emulate network: values must be >= 0");
                } else {
                    throw new IllegalArgumentException("emulate network " + String.join("|", NETWORK_PRESETS.keySet()) + "|off, or <down kbit/s> <up kbit/s> <latency ms>");
                }
                break;
            default:
                throw new IllegalArgumentException("emulate: unknown setting '" + t.get(0) + "' (expected " + String.join(", ", OPS) + ")");
        }
        return new Emulation(op, a);
    }

    private static int positiveInt(String s, String what) {
        try {
            int v = Integer.parseInt(s.trim());
            if (v <= 0) throw new NumberFormatException();
            return v;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("emulate: " + what + " must be a positive integer, got '" + s + "'");
        }
    }

    private static double number(String s, String what) {
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("emulate: " + what + " must be a number, got '" + s + "'");
        }
    }

    /** Whitespace tokens; single / double quotes group and are removed. */
    static List<String> tokens(String s) {
        List<String> out = new ArrayList<String>();
        StringBuilder b = new StringBuilder();
        char q = 0;
        boolean any = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (q != 0) {
                if (c == q) q = 0; else b.append(c);
            } else if (c == '"' || c == '\'') {
                q = c;
                any = true;
            } else if (Character.isWhitespace(c)) {
                if (b.length() > 0 || any) { out.add(b.toString()); b.setLength(0); any = false; }
            } else {
                b.append(c);
            }
        }
        if (b.length() > 0 || any) out.add(b.toString());
        return out;
    }

    /** Needs a device descriptor (resolved by the caller: bundled + the project's devices.json). */
    public boolean needsDevice() { return op.equals("device"); }

    public String deviceName() { return needsDevice() ? args.get(0) : null; }

    public boolean landscape() { return needsDevice() && args.size() == 2; }

    private static Map<String, Object> m(Object... kv) {
        Map<String, Object> p = new LinkedHashMap<String, Object>();
        for (int i = 0; i < kv.length; i += 2) p.put((String) kv[i], kv[i + 1]);
        return p;
    }

    private static double[] throttle(List<String> a) {
        if (a.size() == 1) return NETWORK_PRESETS.get(a.get(0).toLowerCase(Locale.ROOT));
        return new double[] { Double.parseDouble(a.get(0)) * 1000 / 8, Double.parseDouble(a.get(1)) * 1000 / 8, Double.parseDouble(a.get(2)) };
    }

    // ---- CDP ------------------------------------------------------------------------------------------------------

    /** Chromium (CDP): every form. */
    public Plan cdp(Devices.Device device) {
        return cdp(device, new java.util.LinkedHashSet<String>());
    }

    /**
     * As {@link #cdp(Devices.Device)}; {@code granted} holds the tab's granted CDP permissions across statements, since
     * {@code Browser.grantPermissions} replaces the whole set (a later grant would otherwise revoke an earlier one).
     */
    public Plan cdp(Devices.Device device, java.util.Set<String> granted) {
        Plan p = new Plan();
        switch (op) {
            case "device": {
                Devices.Device d = landscape() ? device.landscape() : device;
                p.calls.add(new Call("Emulation.setDeviceMetricsOverride", m("width", d.width, "height", d.height,
                        "deviceScaleFactor", d.scale, "mobile", d.mobile, "screenWidth", d.width, "screenHeight", d.height)));
                p.calls.add(new Call("Emulation.setTouchEmulationEnabled", d.touch ? m("enabled", true, "maxTouchPoints", 5) : m("enabled", false)));
                if (d.userAgent != null) {
                    Map<String, Object> ua = m("userAgent", d.userAgent);
                    if (d.platform != null) ua.put("platform", d.platform);
                    if (d.mobile) {   // UA client hints (Sec-CH-UA-Mobile / -Platform) must agree with the user agent
                        boolean ios = d.userAgent.contains("iPhone") || d.userAgent.contains("iPad");
                        ua.put("userAgentMetadata", m("platform", ios ? "iOS" : "Android", "platformVersion", "", "architecture", "",
                                "model", ios ? "" : d.name, "mobile", true));
                    }
                    p.calls.add(new Call("Emulation.setUserAgentOverride", ua));
                }
                break;
            }
            case "viewport":
                p.calls.add(new Call("Emulation.setDeviceMetricsOverride", m("width", Integer.parseInt(args.get(0)), "height", Integer.parseInt(args.get(1)),
                        "deviceScaleFactor", args.size() == 3 ? Double.parseDouble(args.get(2)) : 1.0, "mobile", false)));
                break;
            case "geolocation":
                granted.add("geolocation");
                p.calls.add(new Call("Browser.grantPermissions", m("permissions", new ArrayList<String>(granted))));
                p.calls.add(new Call("Emulation.setGeolocationOverride", m("latitude", Double.parseDouble(args.get(0)),
                        "longitude", Double.parseDouble(args.get(1)), "accuracy", args.size() == 3 ? Double.parseDouble(args.get(2)) : 10.0)));
                break;
            case "locale":
                p.calls.add(new Call("Emulation.setLocaleOverride", m("locale", args.get(0).replace('_', '-'))));
                break;
            case "timezone":
                p.calls.add(new Call("Emulation.setTimezoneOverride", m("timezoneId", args.get(0))));
                break;
            case "permissions":
                if (args.get(0).equalsIgnoreCase("clear")) {
                    granted.clear();
                    p.calls.add(new Call("Browser.resetPermissions", m()));
                } else {
                    for (String s : args.get(0).split(",")) granted.add(CDP_PERMISSIONS.get(s.trim().toLowerCase(Locale.ROOT)));
                    p.calls.add(new Call("Browser.grantPermissions", m("permissions", new ArrayList<String>(granted))));
                }
                break;
            case "colorscheme":
                p.calls.add(new Call("Emulation.setEmulatedMedia", m("features",
                        Collections.singletonList(m("name", "prefers-color-scheme", "value", args.get(0).toLowerCase(Locale.ROOT))))));
                break;
            case "offline": case "online":
                p.calls.add(new Call("Network.enable", m()));
                p.calls.add(new Call("Network.emulateNetworkConditions", m("offline", op.equals("offline"), "latency", 0, "downloadThroughput", -1, "uploadThroughput", -1)));
                break;
            case "network":
                p.calls.add(new Call("Network.enable", m()));
                if (args.size() == 1 && args.get(0).equalsIgnoreCase("off")) {
                    p.calls.add(new Call("Network.emulateNetworkConditions", m("offline", false, "latency", 0, "downloadThroughput", -1, "uploadThroughput", -1)));
                } else {
                    double[] t = throttle(args);
                    p.calls.add(new Call("Network.emulateNetworkConditions", m("offline", false, "latency", t[2], "downloadThroughput", t[0], "uploadThroughput", t[1])));
                }
                break;
            case "reset":
                p.calls.add(new Call("Emulation.clearDeviceMetricsOverride", m()));
                p.calls.add(new Call("Emulation.setTouchEmulationEnabled", m("enabled", false)));
                p.calls.add(new Call("Emulation.clearGeolocationOverride", m()));
                p.calls.add(new Call("Emulation.setLocaleOverride", m()));
                p.calls.add(new Call("Emulation.setTimezoneOverride", m("timezoneId", "")));
                p.calls.add(new Call("Emulation.setEmulatedMedia", m("features", new ArrayList<Object>())));
                p.calls.add(new Call("Emulation.setUserAgentOverride", m("userAgent", "")));   // "" clears the override
                p.calls.add(new Call("Browser.resetPermissions", m()));
                granted.clear();
                p.calls.add(new Call("Network.emulateNetworkConditions", m("offline", false, "latency", 0, "downloadThroughput", -1, "uploadThroughput", -1)));
                break;
            default:
                throw new IllegalStateException(op);
        }
        return p;
    }

    // ---- WebDriver BiDi ---------------------------------------------------------------------------------------------

    /** WebDriver BiDi: throws UnsupportedOperationException for forms that need CDP. */
    public Plan bidi(Devices.Device device, String context, String origin) {
        Plan p = new Plan();
        List<String> ctx = Collections.singletonList(context);
        switch (op) {
            case "device": {
                Devices.Device d = landscape() ? device.landscape() : device;
                p.calls.add(new Call("browsingContext.setViewport", m("context", context, "viewport", m("width", d.width, "height", d.height),
                        "devicePixelRatio", d.scale)));
                if (d.userAgent != null) p.calls.add(new Call("emulation.setUserAgentOverride", m("userAgent", d.userAgent, "contexts", ctx)));
                if (d.mobile || d.touch) p.warnings.add("touch / mobile-layout emulation needs CDP (Chromium); viewport, scale and user agent applied");
                break;
            }
            case "viewport":
                p.calls.add(new Call("browsingContext.setViewport", m("context", context, "viewport",
                        m("width", Integer.parseInt(args.get(0)), "height", Integer.parseInt(args.get(1))),
                        "devicePixelRatio", args.size() == 3 ? Double.parseDouble(args.get(2)) : 1.0)));
                break;
            case "geolocation":
                if (origin != null) p.calls.add(new Call("permissions.setPermission", m("descriptor", m("name", "geolocation"), "state", "granted", "origin", origin)));
                else p.warnings.add("no page is open yet, so the geolocation permission was not granted (BiDi grants are per origin): add `emulate permissions geolocation` after goto");
                p.calls.add(new Call("emulation.setGeolocationOverride", m("coordinates", m("latitude", Double.parseDouble(args.get(0)),
                        "longitude", Double.parseDouble(args.get(1)), "accuracy", args.size() == 3 ? Double.parseDouble(args.get(2)) : 10.0), "contexts", ctx)));
                break;
            case "locale":
                p.calls.add(new Call("emulation.setLocaleOverride", m("locale", args.get(0).replace('_', '-'), "contexts", ctx)));
                break;
            case "timezone":
                p.calls.add(new Call("emulation.setTimezoneOverride", m("timezone", args.get(0), "contexts", ctx)));
                break;
            case "permissions":
                if (args.get(0).equalsIgnoreCase("clear")) throw new UnsupportedOperationException("emulate permissions clear needs CDP (Chromium)");
                if (origin == null) throw new UnsupportedOperationException("emulate permissions: open a page first (BiDi grants are per origin)");
                for (String s : args.get(0).split(",")) {
                    if (CDP_ONLY_PERMISSIONS.contains(s.trim().toLowerCase(Locale.ROOT)))
                        throw new UnsupportedOperationException("emulate permissions " + s.trim() + " needs CDP (Chromium): it has no WebDriver BiDi permission name");
                    p.calls.add(new Call("permissions.setPermission", m("descriptor", m("name", s.trim().toLowerCase(Locale.ROOT)), "state", "granted", "origin", origin)));
                }
                break;
            case "colorscheme":
                throw new UnsupportedOperationException("emulate colorscheme needs CDP (Chromium): WebDriver BiDi has no prefers-color-scheme override");
            case "offline": case "online":
                p.calls.add(new Call("emulation.setNetworkConditions", m("networkConditions", op.equals("offline") ? m("type", "offline") : null, "contexts", ctx)));
                break;
            case "network":
                if (args.size() == 1 && args.get(0).equalsIgnoreCase("off")) {
                    p.calls.add(new Call("emulation.setNetworkConditions", m("networkConditions", null, "contexts", ctx)));
                    break;
                }
                throw new UnsupportedOperationException("emulate network throttling needs CDP (Chromium); WebDriver BiDi only emulates offline");
            case "reset":
                p.calls.add(new Call("browsingContext.setViewport", m("context", context, "viewport", null, "devicePixelRatio", null)));
                p.calls.add(new Call("emulation.setUserAgentOverride", m("userAgent", null, "contexts", ctx)));
                p.calls.add(new Call("emulation.setGeolocationOverride", m("coordinates", null, "contexts", ctx)));
                p.calls.add(new Call("emulation.setLocaleOverride", m("locale", null, "contexts", ctx)));
                p.calls.add(new Call("emulation.setTimezoneOverride", m("timezone", null, "contexts", ctx)));
                p.calls.add(new Call("emulation.setNetworkConditions", m("networkConditions", null, "contexts", ctx)));
                p.warnings.add("permission grants stay until the session ends (WebDriver BiDi has no reset)");
                break;
            default:
                throw new IllegalStateException(op);
        }
        return p;
    }

    /** As {@link #apply(Devices.Device, Channel, java.util.Set)}, with no permission state carried between statements. */
    public String apply(Devices.Device device, Channel ch) {
        return apply(device, ch, new java.util.LinkedHashSet<String>());
    }

    /**
     * H3 (Δ153): permission grants are browser-level, so inside a per-test context they name it: CDP
     * {@code Browser.grantPermissions / resetPermissions} take {@code browserContextId}, BiDi
     * {@code permissions.setPermission} takes {@code userContext}. Other calls are tab-level already.
     */
    static Call scoped(Call c, String userContext) {
        String key = c.method.startsWith("Browser.") && c.method.endsWith("Permissions") ? "browserContextId"
                : "permissions.setPermission".equals(c.method) ? "userContext" : null;
        if (key == null || userContext == null) return c;
        Map<String, Object> p = c.params == null ? new java.util.LinkedHashMap<String, Object>() : new java.util.LinkedHashMap<String, Object>(c.params);
        p.put(key, userContext);
        return new Call(c.method, p);
    }

    /**
     * Sends this statement's calls over {@code ch}; returns a one-line summary (warnings included). A BiDi command the
     * browser does not implement yet fails with the method named.
     */
    public String apply(Devices.Device device, Channel ch, java.util.Set<String> granted) {
        Plan p = ch.cdp() ? cdp(device, granted) : bidi(device, ch.context(), ch.origin());
        String uc = ch.userContext();
        if (uc != null) {
            for (int i = 0; i < p.calls.size(); i++) p.calls.set(i, scoped(p.calls.get(i), uc));
        }
        for (Call c : p.calls) {
            try {
                ch.send(c.method, c.params);
            } catch (RuntimeException e) {
                String msg = String.valueOf(e.getMessage());
                boolean unknown = msg.toLowerCase(Locale.ROOT).contains("unknown command");
                if (!ch.cdp() && unknown && op.equals("reset")) {
                    p.warnings.add(c.method + " is not implemented by this browser (nothing to reset)");   // reset what exists
                    continue;
                }
                if (!ch.cdp() && unknown) {
                    throw new UnsupportedOperationException("emulate " + op + ": this browser's WebDriver BiDi does not implement " + c.method + " yet", e);
                }
                throw new RuntimeException("emulate " + op + ": " + c.method + " failed: " + msg, e);
            }
        }
        StringBuilder b = new StringBuilder("emulate ").append(op);
        if (device != null) b.append(' ').append(landscape() ? device.landscape() : device);
        else if (!args.isEmpty()) b.append(' ').append(String.join(" ", args));
        b.append(ch.cdp() ? " (CDP)" : " (BiDi)");
        for (String w : p.warnings) b.append("; note: ").append(w);
        return b.toString();
    }
}
