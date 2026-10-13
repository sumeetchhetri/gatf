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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import com.gatf.selenium.emulation.Devices;
import com.gatf.selenium.mobile.MobilePlatform;

/**
 * H5 (Δ157): device-cloud capability presets for BrowserStack, Sauce Labs and LambdaTest. Pure: no Selenium types.
 * <p>
 * Selection: {@code GATF_CLOUD} (env) or {@code -Dgatf.cloud}. Nothing set → {@link #selected} is null and nothing
 * changes. Credentials come only from the environment ({@code BROWSERSTACK_USERNAME}/{@code BROWSERSTACK_ACCESS_KEY},
 * {@code SAUCE_USERNAME}/{@code SAUCE_ACCESS_KEY}, {@code LT_USERNAME}/{@code LT_ACCESS_KEY}). They are never put into
 * the remote URL or the capabilities: the driver sends them as HTTP Basic auth, and every printable form of a
 * {@link Preset} omits them. Any problem is a {@link CloudError} raised before a session is requested.
 */
public final class CloudPresets {

    private CloudPresets() {}

    /** A configuration error raised before any session starts. Its message never carries a credential. */
    public static final class CloudError extends IllegalStateException {
        private static final long serialVersionUID = 1L;
        public CloudError(String m) { super(m); }
    }

    public enum Provider {
        BROWSERSTACK("browserstack", "bstack:options", "BROWSERSTACK_USERNAME", "BROWSERSTACK_ACCESS_KEY"),
        SAUCE("saucelabs", "sauce:options", "SAUCE_USERNAME", "SAUCE_ACCESS_KEY"),
        LAMBDATEST("lambdatest", "LT:Options", "LT_USERNAME", "LT_ACCESS_KEY");

        public final String id, optionsKey, userEnv, keyEnv;

        Provider(String id, String optionsKey, String userEnv, String keyEnv) {
            this.id = id;
            this.optionsKey = optionsKey;
            this.userEnv = userEnv;
            this.keyEnv = keyEnv;
        }

        /** browserstack|bs, saucelabs|sauce, lambdatest|lt (case-insensitive). */
        public static Provider parse(String s) {
            String v = s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
            switch (v) {
                case "browserstack": case "bs": return BROWSERSTACK;
                case "saucelabs": case "sauce": return SAUCE;
                case "lambdatest": case "lt": return LAMBDATEST;
                default:
                    throw new CloudError("GATF_CLOUD: unknown device cloud '" + clip(s, 40)
                            + "'. Known: browserstack (bs), saucelabs (sauce), lambdatest (lt)");
            }
        }
    }

    public static final String ENV_CLOUD = "GATF_CLOUD", PROP_CLOUD = "gatf.cloud";
    static final String DEFAULT_PLATFORM = "Windows 11";
    private static final Pattern SAFE_TOKEN = Pattern.compile("[A-Za-z0-9._ -]{1,64}");
    private static final Pattern SAUCE_REGION = Pattern.compile("[a-z]{2}-[a-z]+-[0-9]{1,2}");
    /** Option keys that would carry a credential; refused in GATF_CLOUD_OPTIONS at any depth. */
    static final List<String> CREDENTIAL_KEYS = Arrays.asList("user", "username", "accesskey", "key", "password",
            "apikey", "token", "authorization", "secret");

    /** env first, then the system property; blank = unset. */
    static String val(Map<String, String> env, String envKey, Map<String, String> props, String propKey) {
        String v = env == null ? null : env.get(envKey);
        if ((v == null || v.trim().isEmpty()) && props != null && propKey != null) v = props.get(propKey);
        return v == null || v.trim().isEmpty() ? null : v.trim();
    }

    /** The raw GATF_CLOUD value, or null when no device cloud is selected (behaviour unchanged). */
    public static String selected(Map<String, String> env, Map<String, String> props) {
        return val(env, ENV_CLOUD, props, PROP_CLOUD);
    }

    /** A built preset. {@link #toString()} and {@link #describe()} never include the credentials. */
    public static final class Preset {
        public final Provider provider;
        /** The hub URL. It never carries user info. */
        public final String remoteUrl;
        public final String browser;
        /** Top-level capabilities to set, including the vendor options map under {@link Provider#optionsKey}. */
        public final Map<String, Object> capabilities;
        /** chromedriver {@code mobileEmulation} for GATF_CLOUD_DEVICE (Chrome/Edge), or null. */
        public final Map<String, Object> mobileEmulation;
        public final String platform, browserVersion, device;
        /** Δ158: an Appium session on a real device ({@link #buildAppium}); browser is then the platform label. */
        public final boolean realDevice;
        private final String user, key;

        Preset(Provider p, String url, String browser, Map<String, Object> caps, Map<String, Object> mobile,
               String platform, String version, String device, String user, String key) {
            this(p, url, browser, caps, mobile, platform, version, device, user, key, false);
        }

        Preset(Provider p, String url, String browser, Map<String, Object> caps, Map<String, Object> mobile,
               String platform, String version, String device, String user, String key, boolean realDevice) {
            this.realDevice = realDevice;
            this.provider = p;
            this.remoteUrl = url;
            this.browser = browser;
            this.capabilities = Collections.unmodifiableMap(caps);
            this.mobileEmulation = mobile == null ? null : Collections.unmodifiableMap(mobile);
            this.platform = platform;
            this.browserVersion = version;
            this.device = device;
            this.user = user;
            this.key = key;
        }

        /** For the HTTP client's Basic auth only. */
        public String username() { return user; }
        /** For the HTTP client's Basic auth only. */
        public String accessKey() { return key; }
        /** Values to scrub from any text that leaves the process. */
        public List<String> secrets() { return Arrays.asList(key, user); }

        /** One log line: provider, browser, version, platform, device and hub, with no credentials. */
        public String describe() {
            if (realDevice) {
                return provider.id + ": " + browser + (browserVersion != null ? " " + browserVersion : "") + " on " + device
                        + " (real device) via " + remoteUrl;
            }
            return provider.id + ": " + browser + " " + browserVersion + " on " + platform
                    + (device != null ? " (device emulation: " + device + ")" : "") + " via " + remoteUrl;
        }

        @Override
        public String toString() { return "CloudPreset[" + describe() + "]"; }
    }

    /**
     * Builds the preset for one session.
     * @param browserKey     GATF browser key without suffix (chrome, firefox, edge, safari, ie)
     * @param device         the Devices entry for GATF_CLOUD_DEVICE (resolved by the caller), or null
     * @param extraOptions   GATF_CLOUD_OPTIONS parsed as a JSON object, or null
     * @param webSocketUrl   whether the session asks for a BiDi socket (trace / route / contexts)
     */
    public static Preset build(String browserKey, Map<String, String> env, Map<String, String> props,
                               Devices.Device device, Map<String, Object> extraOptions, boolean webSocketUrl) {
        try {
            return build0(browserKey, env, props, device, extraOptions, webSocketUrl);
        } catch (CloudError e) {
            // an echoed user value (a typo'd provider, region, option key) must never carry any provider's credential
            String m = redact(e.getMessage(), allSecrets(env));
            if (m.equals(e.getMessage())) throw e;
            throw new CloudError(m);
        }
    }

    /** Every provider's credential values present in {@code env} (for redaction). */
    public static List<String> allSecrets(Map<String, String> env) {
        List<String> out = new ArrayList<String>();
        if (env == null) return out;
        for (Provider p : Provider.values()) {
            for (String k : new String[] {p.keyEnv, p.userEnv}) {
                String v = env.get(k);
                if (v != null && !v.trim().isEmpty()) out.add(v.trim());
            }
        }
        return out;
    }

    private static Preset build0(String browserKey, Map<String, String> env, Map<String, String> props,
                                 Devices.Device device, Map<String, Object> extraOptions, boolean webSocketUrl) {
        String sel = selected(env, props);
        if (sel == null) throw new CloudError("GATF_CLOUD is not set");
        Provider p = Provider.parse(sel);

        String user = env == null ? null : env.get(p.userEnv), key = env == null ? null : env.get(p.keyEnv);
        List<String> missing = new ArrayList<String>();
        if (user == null || user.trim().isEmpty()) missing.add(p.userEnv);
        if (key == null || key.trim().isEmpty()) missing.add(p.keyEnv);
        if (!missing.isEmpty()) {
            throw new CloudError("GATF_CLOUD=" + p.id + " needs " + p.userEnv + " and " + p.keyEnv
                    + " in the environment (missing: " + String.join(", ", missing) + "). Credentials are read from the environment only.");
        }
        user = user.trim();
        key = key.trim();

        String b = browserKey == null ? "" : browserKey.trim().toLowerCase(Locale.ROOT);
        boolean chromium = b.equals("chrome") || b.equals("edge");
        switch (b) {
            case "chrome": case "firefox": case "edge": case "safari": case "ie": break;
            default: throw new CloudError("GATF_CLOUD=" + p.id + ": browser '" + clip(b, 20) + "' is not offered; use chrome, firefox, edge, safari or ie");
        }

        String version = val(env, "GATF_CLOUD_BROWSER_VERSION", props, "gatf.cloud.browserVersion");
        if (version == null) version = "latest";
        safe(version, "GATF_CLOUD_BROWSER_VERSION");
        String platform = val(env, "GATF_CLOUD_PLATFORM", props, "gatf.cloud.platform");
        if (platform == null) {
            if (b.equals("safari")) {
                throw new CloudError("GATF_CLOUD=" + p.id + ": safari needs GATF_CLOUD_PLATFORM, a macOS platform name as " + p.id + " spells it");
            }
            platform = DEFAULT_PLATFORM;
        }
        safe(platform, "GATF_CLOUD_PLATFORM");
        String build = val(env, "GATF_CLOUD_BUILD", props, "gatf.cloud.build");
        if (build != null) build = clip(build.replaceAll("\\p{Cntrl}", " "), 255);

        List<String> creds = Arrays.asList(key, user);
        for (String[] v : new String[][] {{version, "GATF_CLOUD_BROWSER_VERSION"}, {platform, "GATF_CLOUD_PLATFORM"},
                {build, "GATF_CLOUD_BUILD"}, {device == null ? null : device.name, "GATF_CLOUD_DEVICE"}}) {
            if (v[0] != null && containsSecret(v[0], creds)) {
                throw new CloudError(v[1] + " contains a " + p.id + " credential; credentials are read from the environment only");
            }
        }

        Map<String, Object> mobile = null;
        if (device != null) {
            if (!chromium) {
                throw new CloudError("GATF_CLOUD_DEVICE '" + clip(device.name, 60) + "' uses chromedriver mobile emulation, which needs chrome or edge (this script opens "
                        + b + "); real devices come with Appium (Δ158)");
            }
            mobile = mobileEmulation(device);
        }

        Map<String, Object> vendor = new LinkedHashMap<String, Object>();
        String url;
        switch (p) {
            case BROWSERSTACK: {
                url = "https://hub-cloud.browserstack.com/wd/hub";
                String[] os = browserStackOs(platform);
                vendor.put("os", os[0]);
                if (os[1] != null) vendor.put("osVersion", os[1]);
                if (build != null) vendor.put("buildName", build);
                if (chromium) vendor.put("seleniumCdp", Boolean.TRUE);      // documented: enables Selenium CDP on Chrome/Edge
                if (webSocketUrl) vendor.put("seleniumBidi", Boolean.TRUE); // documented: enables Selenium BiDi (webSocketUrl)
                break;
            }
            case SAUCE: {
                String region = val(env, "SAUCE_REGION", props, "gatf.cloud.sauceRegion");
                if (region == null) region = "us-west-1";
                if (!SAUCE_REGION.matcher(region).matches()) {
                    throw new CloudError("SAUCE_REGION '" + clip(region, 30) + "' is not a region name like us-west-1 or eu-central-1");
                }
                url = "https://ondemand." + region + ".saucelabs.com/wd/hub";
                if (build != null) vendor.put("build", build);
                break;
            }
            default: {
                url = "https://hub.lambdatest.com/wd/hub";
                vendor.put("platformName", platform);
                vendor.put("w3c", Boolean.TRUE);
                if (build != null) vendor.put("build", build);
                break;
            }
        }

        if (extraOptions != null) {
            checkNoCredentials(extraOptions, "", Arrays.asList(user, key), p);
            vendor.putAll(extraOptions);
        }
        if (p == Provider.SAUCE && webSocketUrl && vendor.containsKey("extendedDebugging")
                && !Boolean.FALSE.equals(vendor.get("extendedDebugging")) && !"false".equals(String.valueOf(vendor.get("extendedDebugging")))) {
            throw new CloudError("GATF_CLOUD=saucelabs: extendedDebugging cannot be combined with webSocketUrl (BiDi/CDP), which this run needs");
        }

        Map<String, Object> caps = new LinkedHashMap<String, Object>();
        caps.put("browserVersion", version);
        if (p == Provider.SAUCE) caps.put("platformName", platform);
        caps.put(p.optionsKey, vendor);
        return new Preset(p, url, b, caps, mobile, platform, version, device == null ? null : device.name, user, key);
    }

    /** "Windows 11" → Windows/11; "macOS Sonoma" or "OS X Sonoma" → OS X/Sonoma; "Windows" → Windows/null. */
    static String[] browserStackOs(String platform) {
        String t = platform.trim();
        String lower = t.toLowerCase(Locale.ROOT);
        for (String mac : new String[] {"os x ", "osx ", "macos ", "mac "}) {
            if (lower.startsWith(mac)) return new String[] {"OS X", t.substring(mac.length()).trim()};
        }
        int sp = t.indexOf(' ');
        return sp < 0 ? new String[] {t, null} : new String[] {t.substring(0, sp), t.substring(sp + 1).trim()};
    }

    /** chromedriver mobileEmulation from a Devices descriptor (viewport, pixel ratio, mobile/touch flags, user agent). */
    public static Map<String, Object> mobileEmulation(Devices.Device d) {
        Map<String, Object> metrics = new LinkedHashMap<String, Object>();
        metrics.put("width", d.width);
        metrics.put("height", d.height);
        metrics.put("pixelRatio", d.scale);
        metrics.put("mobile", d.mobile);
        metrics.put("touch", d.touch);
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("deviceMetrics", metrics);
        if (d.userAgent != null) m.put("userAgent", d.userAgent);
        return m;
    }

    @SuppressWarnings("unchecked")
    private static void checkNoCredentials(Map<String, Object> m, String path, List<String> secrets, Provider p) {
        for (Map.Entry<String, Object> e : m.entrySet()) {
            String k = String.valueOf(e.getKey());
            if (isCredentialKey(k)) {
                throw new CloudError("GATF_CLOUD_OPTIONS must not carry credentials (key '" + clip(path + k, 60) + "'); set "
                        + p.userEnv + " and " + p.keyEnv + " in the environment instead");
            }
            Object v = e.getValue();
            if (v instanceof Map) checkNoCredentials((Map<String, Object>) v, path + k + ".", secrets, p);
            else if (v instanceof Collection) {
                for (Object o : (Collection<Object>) v) {
                    if (o instanceof Map) checkNoCredentials((Map<String, Object>) o, path + k + "[].", secrets, p);
                    else if (o != null && containsSecret(String.valueOf(o), secrets)) refuseValue(path + k, p);
                }
            } else if (v != null && containsSecret(String.valueOf(v), secrets)) refuseValue(path + k, p);
        }
    }

    /** user, userName, access_key, "Access-Key ", api.key, …password, …token, …secret (separators and case ignored). */
    static boolean isCredentialKey(String k) {
        String n = k.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (CREDENTIAL_KEYS.contains(n)) return true;
        for (String suffix : new String[] {"accesskey", "username", "password", "apikey", "token", "secret"}) {
            if (n.endsWith(suffix)) return true;
        }
        return false;
    }

    private static boolean containsSecret(String s, List<String> secrets) {
        for (String x : secrets) if (x != null && x.length() >= 4 && s.contains(x)) return true;
        return false;
    }

    private static void refuseValue(String key, Provider p) {
        throw new CloudError("GATF_CLOUD_OPTIONS value of '" + clip(key, 60) + "' contains a " + p.id
                + " credential; credentials are read from the environment only");
    }

    private static void safe(String v, String what) {
        if (!SAFE_TOKEN.matcher(v).matches()) throw new CloudError(what + " has unsupported characters (letters, digits, space, . _ - only)");
    }

    // ---- Δ158: Appium 2 on real devices ------------------------------------------------------------------------------

    public static final String ENV_APP = "GATF_CLOUD_APP", PROP_APP = "gatf.cloud.app";
    public static final String ENV_OS_VERSION = "GATF_CLOUD_OS_VERSION", PROP_OS_VERSION = "gatf.cloud.osVersion";
    /** Sauce allows a device-name regex ("Google Pixel.*"). */
    private static final Pattern DEVICE_TOKEN = Pattern.compile("[A-Za-z0-9._ ()\\[\\]*+^$|-]{1,80}");
    /** Capabilities of a local Appium session that name local things (an apk path, a udid, ports): never sent to a cloud. */
    static final List<String> LOCAL_ONLY = Arrays.asList("appium:app", "appium:udid", "appium:avd", "appium:deviceName",
            "appium:platformVersion", "appium:chromedriverExecutable", "appium:chromedriverExecutableDir", "appium:xcodeOrgId",
            "appium:xcodeSigningId", "appium:wdaLocalPort", "appium:systemPort", "appium:mjpegServerPort",
            "appium:chromedriverPort", "appium:derivedDataPath", "appium:keystorePath", "appium:keystorePassword",
            "appium:keyPassword");

    /**
     * The real-device preset for {@code open appium-android|appium-ios} with GATF_CLOUD set: BrowserStack App Automate,
     * Sauce Labs Real Devices or LambdaTest Real Devices, on the provider's documented W3C/Appium 2 capabilities.
     * <ul>
     * <li>{@code GATF_CLOUD_DEVICE}: the device name as the provider spells it (required);</li>
     * <li>{@code GATF_CLOUD_OS_VERSION}: the OS version (optional);</li>
     * <li>{@code GATF_CLOUD_APP}: the uploaded app's id or URL ({@code bs://…}, {@code storage:filename=…},
     *     {@code lt://…}); required unless the session is mobile web ({@code browserName} in the driver config);</li>
     * <li>{@code GATF_CLOUD_BUILD} and {@code GATF_CLOUD_OPTIONS} as for browsers.</li>
     * </ul>
     * The credentials are the same environment variables as for browsers. The driver config's capabilities are kept
     * except the local-only ones ({@link #LOCAL_ONLY}), and none of them, nor the app value, may carry a credential.
     */
    public static Preset buildAppium(MobilePlatform mp, Map<String, String> env, Map<String, String> props,
                                     Map<String, Object> localCaps, Map<String, Object> extraOptions) {
        try {
            return buildAppium0(mp, env, props, localCaps, extraOptions);
        } catch (CloudError e) {
            String m = redact(e.getMessage(), allSecrets(env));
            if (m.equals(e.getMessage())) throw e;
            throw new CloudError(m);
        }
    }

    private static Preset buildAppium0(MobilePlatform mp, Map<String, String> env, Map<String, String> props,
                                       Map<String, Object> localCaps, Map<String, Object> extraOptions) {
        String sel = selected(env, props);
        if (sel == null) throw new CloudError("GATF_CLOUD is not set");
        Provider p = Provider.parse(sel);
        String user = env == null ? null : env.get(p.userEnv), key = env == null ? null : env.get(p.keyEnv);
        List<String> missing = new ArrayList<String>();
        if (user == null || user.trim().isEmpty()) missing.add(p.userEnv);
        if (key == null || key.trim().isEmpty()) missing.add(p.keyEnv);
        if (!missing.isEmpty()) {
            throw new CloudError("GATF_CLOUD=" + p.id + " needs " + p.userEnv + " and " + p.keyEnv
                    + " in the environment (missing: " + String.join(", ", missing) + "). Credentials are read from the environment only.");
        }
        user = user.trim();
        key = key.trim();
        List<String> creds = Arrays.asList(key, user);

        String device = val(env, "GATF_CLOUD_DEVICE", props, "gatf.cloud.device");
        if (device == null) {
            throw new CloudError("GATF_CLOUD=" + p.id + ": open appium-" + mp.name().toLowerCase(Locale.ROOT)
                    + " needs GATF_CLOUD_DEVICE, a real-device name as " + p.id + " spells it");
        }
        if (!DEVICE_TOKEN.matcher(device).matches()) {
            throw new CloudError("GATF_CLOUD_DEVICE has unsupported characters (letters, digits, space, . _ - ( ) [ ] * + ^ $ | only)");
        }
        String osVersion = val(env, ENV_OS_VERSION, props, PROP_OS_VERSION);
        if (osVersion != null) safe(osVersion, ENV_OS_VERSION);
        String build = val(env, "GATF_CLOUD_BUILD", props, "gatf.cloud.build");
        if (build != null) build = clip(build.replaceAll("\\p{Cntrl}", " "), 255);
        String app = val(env, ENV_APP, props, PROP_APP);

        Map<String, Object> kept = new LinkedHashMap<String, Object>();
        if (localCaps != null) {
            for (Map.Entry<String, Object> e : localCaps.entrySet()) {
                if (!LOCAL_ONLY.contains(e.getKey())) kept.put(e.getKey(), e.getValue());
            }
        }
        Object browserName = kept.get("browserName");
        boolean web = browserName != null && !String.valueOf(browserName).trim().isEmpty();
        if (app == null && !web) {
            throw new CloudError("GATF_CLOUD=" + p.id + ": a native app session needs GATF_CLOUD_APP, the app id or URL from your "
                    + p.id + " upload (" + appHint(p) + "); for mobile web set browserName in the appium driver config");
        }
        if (app != null) {
            if (app.length() > 512 || app.matches(".*\\s.*")) throw new CloudError(ENV_APP + " is not an app id or URL");
            if (URL_USERINFO.matcher(app).find()) {
                throw new CloudError(ENV_APP + " must not carry credentials in the URL; credentials are read from the environment only");
            }
        }
        for (String[] v : new String[][] {{osVersion, ENV_OS_VERSION}, {build, "GATF_CLOUD_BUILD"}, {device, "GATF_CLOUD_DEVICE"}, {app, ENV_APP}}) {
            if (v[0] != null && containsSecret(v[0], creds)) {
                throw new CloudError(v[1] + " contains a " + p.id + " credential; credentials are read from the environment only");
            }
        }
        for (Map.Entry<String, Object> e : kept.entrySet()) {
            String k = e.getKey().contains(":") ? e.getKey().substring(e.getKey().indexOf(':') + 1) : e.getKey();
            if (isCredentialKey(k)) {
                throw new CloudError("the appium driver config must not carry credentials (capability '" + clip(e.getKey(), 60) + "'); set "
                        + p.userEnv + " and " + p.keyEnv + " in the environment instead");
            }
            if (e.getValue() != null && containsSecret(String.valueOf(e.getValue()), creds)) {
                throw new CloudError("the appium driver config capability '" + clip(e.getKey(), 60) + "' contains a " + p.id
                        + " credential; credentials are read from the environment only");
            }
        }

        Map<String, Object> caps = new LinkedHashMap<String, Object>(kept);
        caps.put("platformName", mp.platformName);
        Map<String, Object> vendor = new LinkedHashMap<String, Object>();
        String url, optionsKey;
        switch (p) {
            case BROWSERSTACK:
                url = "https://hub-cloud.browserstack.com/wd/hub";
                optionsKey = p.optionsKey;
                if (app != null) caps.put("appium:app", app);
                vendor.put("deviceName", device);
                if (osVersion != null) vendor.put("osVersion", osVersion);
                if (build != null) vendor.put("buildName", build);
                break;
            case SAUCE: {
                String region = val(env, "SAUCE_REGION", props, "gatf.cloud.sauceRegion");
                if (region == null) region = "us-west-1";
                if (!SAUCE_REGION.matcher(region).matches()) {
                    throw new CloudError("SAUCE_REGION '" + clip(region, 30) + "' is not a region name like us-west-1 or eu-central-1");
                }
                url = "https://ondemand." + region + ".saucelabs.com/wd/hub";
                optionsKey = p.optionsKey;
                if (app != null) caps.put("appium:app", app);
                caps.put("appium:deviceName", device);
                if (osVersion != null) caps.put("appium:platformVersion", osVersion);
                vendor.put("appiumVersion", "latest");
                if (build != null) vendor.put("build", build);
                break;
            }
            default:
                url = "https://mobile-hub.lambdatest.com/wd/hub";
                optionsKey = "lt:options";   // LambdaTest's app-automation options key (LT:Options is its browser key)
                vendor.put("platformName", mp.platformName);
                vendor.put("deviceName", device);
                if (osVersion != null) vendor.put("platformVersion", osVersion);
                vendor.put("isRealMobile", Boolean.TRUE);
                if (app != null) vendor.put("app", app);
                vendor.put("w3c", Boolean.TRUE);
                if (build != null) vendor.put("build", build);
                break;
        }
        if (extraOptions != null) {
            checkNoCredentials(extraOptions, "", creds, p);
            vendor.putAll(extraOptions);
        }
        caps.put(optionsKey, vendor);
        String label = mp.platformName + (web ? " " + String.valueOf(browserName).trim().toLowerCase(Locale.ROOT) : "");
        return new Preset(p, url, label, caps, null, mp.platformName, osVersion, device, user, key, true);
    }

    private static String appHint(Provider p) {
        switch (p) {
            case BROWSERSTACK: return "bs://…";
            case SAUCE: return "storage:filename=… or storage:<id>";
            default: return "lt://…";
        }
    }

    // ---- status callback ---------------------------------------------------------------------------------------------

    /**
     * The vendor's documented {@code executeScript} hooks that name the session and mark it passed/failed, in the
     * order to send them. {@code reason} is used by BrowserStack only; pass it already redacted.
     */
    public static List<String> statusScripts(Provider p, String name, boolean passed, String reason) {
        String n = name == null ? "gatf" : clip(name.replaceAll("\\p{Cntrl}", " ").trim(), 255);
        String st = passed ? "passed" : "failed";
        List<String> out = new ArrayList<String>();
        switch (p) {
            case BROWSERSTACK:
                out.add("browserstack_executor: {\"action\": \"setSessionName\", \"arguments\": {\"name\": " + json(n) + "}}");
                out.add("browserstack_executor: {\"action\": \"setSessionStatus\", \"arguments\": {\"status\": \"" + st
                        + "\", \"reason\": " + json(reason == null ? "" : clip(reason.replaceAll("\\p{Cntrl}", " ").trim(), 255)) + "}}");
                break;
            case SAUCE:
                out.add("sauce:job-name=" + n);
                out.add("sauce:job-result=" + st);
                break;
            default:
                out.add("lambda-name=" + n);
                out.add("lambda-status=" + st);
                break;
        }
        return out;
    }

    /** A JSON string literal. */
    static String json(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                default:
                    if (c < 0x20 || c == 0x2028 || c == 0x2029) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
            }
        }
        return b.append('"').toString();
    }

    // ---- redaction ---------------------------------------------------------------------------------------------------

    private static final Pattern URL_USERINFO = Pattern.compile("(?i)([a-z][a-z0-9+.-]*://)[^/@\\s]+@");

    /** Removes URL user info ({@code https://u:k@host} → {@code https://***@host}) and every given secret (≥ 4 chars). */
    public static String redact(String text, Collection<String> secrets) {
        if (text == null) return null;
        String out = URL_USERINFO.matcher(text).replaceAll("$1***@");
        if (secrets != null) {
            for (String s : secrets) {
                if (s != null && s.length() >= 4) out = out.replace(s, "***");
            }
        }
        return out;
    }

    static String clip(String s, int n) {
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(0, n) + "…";
    }
}
