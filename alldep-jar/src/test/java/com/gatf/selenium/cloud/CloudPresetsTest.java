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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.gatf.selenium.cloud.CloudPresets.CloudError;
import com.gatf.selenium.cloud.CloudPresets.Preset;
import com.gatf.selenium.cloud.CloudPresets.Provider;
import com.gatf.selenium.emulation.Devices;

/** Δ157: CloudPresets — provider presets, env-only credentials, redaction, status scripts. */
public class CloudPresetsTest {

    static final String KEY = "sEcReT-aCcEsS-kEy-9f8e7d";
    static final String USER = "alice_cloud_user";

    static Map<String, String> env(String... kv) {
        Map<String, String> m = new HashMap<String, String>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }

    static Map<String, String> bs(String... more) {
        Map<String, String> m = env("GATF_CLOUD", "browserstack", "BROWSERSTACK_USERNAME", USER, "BROWSERSTACK_ACCESS_KEY", KEY);
        m.putAll(env(more));
        return m;
    }

    static final Devices.Device PHONE = new Devices.Device("iPhone 14", 390, 844, 3, true, true, "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0)", "iPhone");

    /** Every printable form of a preset, plus anything else a caller could log. */
    static List<String> outputs(Preset p) {
        List<String> o = new ArrayList<String>();
        o.add(p.toString());
        o.add(p.describe());
        o.add(p.remoteUrl);
        o.add(String.valueOf(p.capabilities));
        o.add(String.valueOf(p.mobileEmulation));
        o.addAll(CloudPresets.statusScripts(p.provider, "a.sel", false, "boom"));
        return o;
    }

    static void assertNoSecret(String s) {
        assertFalse(s.contains(KEY), "access key leaked: " + s.replace(KEY, "<KEY>"));
        assertFalse(s.contains(USER), "user name leaked: " + s.replace(USER, "<USER>"));
    }

    @Test
    public void nothingSelectedMeansUnchanged() {
        assertNull(CloudPresets.selected(env(), env()));
        assertNull(CloudPresets.selected(env("GATF_CLOUD", "  "), env()));
        assertNull(CloudPresets.selected(null, null));
        assertEquals("lt", CloudPresets.selected(env(), env("gatf.cloud", "lt")));
        assertEquals("sauce", CloudPresets.selected(env("GATF_CLOUD", "sauce"), env("gatf.cloud", "lt")), "env wins over -D");
    }

    @Test
    public void browserStackChromeDesktop() {
        Preset p = CloudPresets.build("chrome", bs(), env(), null, null, false);
        assertEquals(Provider.BROWSERSTACK, p.provider);
        assertEquals("https://hub-cloud.browserstack.com/wd/hub", p.remoteUrl);
        assertEquals("latest", p.capabilities.get("browserVersion"));
        @SuppressWarnings("unchecked")
        Map<String, Object> o = (Map<String, Object>) p.capabilities.get("bstack:options");
        assertEquals("Windows", o.get("os"));
        assertEquals("11", o.get("osVersion"));
        assertEquals(Boolean.TRUE, o.get("seleniumCdp"));
        assertNull(o.get("seleniumBidi"));
        assertNull(p.mobileEmulation);
        for (String s : outputs(p)) assertNoSecret(s);
        assertEquals(KEY, p.accessKey());
        assertEquals(USER, p.username());
    }

    @Test
    public void browserStackBidiAndMacPlatformAndBuild() {
        Preset p = CloudPresets.build("firefox", bs("GATF_CLOUD_PLATFORM", "macOS Sonoma", "GATF_CLOUD_BROWSER_VERSION", "130",
                "GATF_CLOUD_BUILD", "nightly 42"), env(), null, null, true);
        @SuppressWarnings("unchecked")
        Map<String, Object> o = (Map<String, Object>) p.capabilities.get("bstack:options");
        assertEquals("OS X", o.get("os"));
        assertEquals("Sonoma", o.get("osVersion"));
        assertEquals(Boolean.TRUE, o.get("seleniumBidi"));
        assertNull(o.get("seleniumCdp"), "CDP is Chromium only");
        assertEquals("nightly 42", o.get("buildName"));
        assertEquals("130", p.capabilities.get("browserVersion"));
    }

    @Test
    public void sauceRegionsAndPlatform() {
        Map<String, String> e = env("GATF_CLOUD", "saucelabs", "SAUCE_USERNAME", USER, "SAUCE_ACCESS_KEY", KEY, "GATF_CLOUD_BUILD", "b1");
        Preset p = CloudPresets.build("firefox", e, env(), null, null, false);
        assertEquals("https://ondemand.us-west-1.saucelabs.com/wd/hub", p.remoteUrl);
        assertEquals("Windows 11", p.capabilities.get("platformName"));
        @SuppressWarnings("unchecked")
        Map<String, Object> o = (Map<String, Object>) p.capabilities.get("sauce:options");
        assertEquals("b1", o.get("build"));
        assertFalse(o.containsKey("username"));
        assertFalse(o.containsKey("accessKey"));
        e.put("SAUCE_REGION", "eu-central-1");
        assertEquals("https://ondemand.eu-central-1.saucelabs.com/wd/hub", CloudPresets.build("chrome", e, env(), null, null, false).remoteUrl);
        for (String bad : new String[] {"evil.com/x#", "us-west-1.attacker.net", "US-WEST-1", "a@b"}) {
            e.put("SAUCE_REGION", bad);
            assertThrows(CloudError.class, () -> CloudPresets.build("chrome", e, env(), null, null, false));
        }
    }

    @Test
    public void lambdaTestEdge() {
        Map<String, String> e = env("GATF_CLOUD", "LT", "LT_USERNAME", USER, "LT_ACCESS_KEY", KEY);
        Preset p = CloudPresets.build("edge", e, env(), null, null, false);
        assertEquals("https://hub.lambdatest.com/wd/hub", p.remoteUrl);
        @SuppressWarnings("unchecked")
        Map<String, Object> o = (Map<String, Object>) p.capabilities.get("LT:Options");
        assertEquals("Windows 11", o.get("platformName"));
        assertEquals(Boolean.TRUE, o.get("w3c"));
        assertFalse(o.containsKey("user"));
        assertFalse(o.containsKey("accessKey"));
        for (String s : outputs(p)) assertNoSecret(s);
    }

    @Test
    public void aliasesAndUnknownProvider() {
        assertEquals(Provider.BROWSERSTACK, Provider.parse(" BS "));
        assertEquals(Provider.SAUCE, Provider.parse("Sauce"));
        assertEquals(Provider.LAMBDATEST, Provider.parse("lambdatest"));
        CloudError x = assertThrows(CloudError.class, () -> CloudPresets.build("chrome", env("GATF_CLOUD", "browserstak"), env(), null, null, false));
        assertTrue(x.getMessage().contains("browserstak") && x.getMessage().contains("lambdatest"), x.getMessage());
    }

    @Test
    public void typoedProviderHoldingAKeyIsRedacted() {
        // a credential pasted into GATF_CLOUD by mistake must not be echoed back
        Map<String, String> e = env("GATF_CLOUD", KEY, "LT_ACCESS_KEY", KEY, "LT_USERNAME", USER);
        CloudError x = assertThrows(CloudError.class, () -> CloudPresets.build("chrome", e, env(), null, null, false));
        assertNoSecret(x.getMessage());
    }

    @Test
    public void missingCredentialsNameTheVariablesNotTheValues() {
        CloudError x = assertThrows(CloudError.class, () -> CloudPresets.build("chrome",
                env("GATF_CLOUD", "browserstack", "BROWSERSTACK_USERNAME", USER), env(), null, null, false));
        assertTrue(x.getMessage().contains("BROWSERSTACK_ACCESS_KEY"), x.getMessage());
        assertNoSecret(x.getMessage());
        // -D credentials are ignored: env only
        CloudError y = assertThrows(CloudError.class, () -> CloudPresets.build("chrome", env("GATF_CLOUD", "lt"),
                env("LT_USERNAME", USER, "LT_ACCESS_KEY", KEY, "gatf.cloud.username", USER), null, null, false));
        assertTrue(y.getMessage().contains("LT_USERNAME") && y.getMessage().contains("LT_ACCESS_KEY"), y.getMessage());
        assertNoSecret(y.getMessage());
    }

    @Test
    public void optionsMergeButNeverCarryCredentials() {
        Map<String, Object> ok = new LinkedHashMap<String, Object>();
        ok.put("local", Boolean.TRUE);
        ok.put("seleniumCdp", Boolean.FALSE);
        Preset p = CloudPresets.build("chrome", bs(), env(), null, ok, false);
        @SuppressWarnings("unchecked")
        Map<String, Object> o = (Map<String, Object>) p.capabilities.get("bstack:options");
        assertEquals(Boolean.TRUE, o.get("local"));
        assertEquals(Boolean.FALSE, o.get("seleniumCdp"), "user options override the defaults");

        for (String k : new String[] {"accessKey", "userName", "user", "ACCESS_KEY", "access-key", "password", "token"}) {
            Map<String, Object> bad = new LinkedHashMap<String, Object>();
            bad.put(k, KEY);
            CloudError x = assertThrows(CloudError.class, () -> CloudPresets.build("chrome", bs(), env(), null, bad, false));
            assertNoSecret(x.getMessage());
        }
        Map<String, Object> nested = new LinkedHashMap<String, Object>();
        nested.put("local", Boolean.TRUE);
        nested.put("tunnel", new LinkedHashMap<String, Object>(Map.of("accessKey", "x")));
        assertThrows(CloudError.class, () -> CloudPresets.build("chrome", bs(), env(), null, nested, false));
        Map<String, Object> hidden = new LinkedHashMap<String, Object>();
        hidden.put("localIdentifier", "id-" + KEY);
        CloudError x = assertThrows(CloudError.class, () -> CloudPresets.build("chrome", bs(), env(), null, hidden, false));
        assertNoSecret(x.getMessage());
        Map<String, Object> inList = new LinkedHashMap<String, Object>();
        inList.put("tags", Arrays.asList("a", KEY));
        assertNoSecret(assertThrows(CloudError.class, () -> CloudPresets.build("chrome", bs(), env(), null, inList, false)).getMessage());
    }

    @Test
    public void credentialsSmuggledIntoOtherSettingsAreRefused() {
        // a key pasted into the build name / platform / version would be sent as a capability: refuse, never echo
        for (String var : new String[] {"GATF_CLOUD_BUILD", "GATF_CLOUD_PLATFORM", "GATF_CLOUD_BROWSER_VERSION"}) {
            CloudError x = assertThrows(CloudError.class, () -> CloudPresets.build("chrome", bs(var, KEY), env(), null, null, false));
            assertTrue(x.getMessage().contains(var), x.getMessage());
            assertNoSecret(x.getMessage());
        }
        for (String k : new String[] {"Access-Key ", "api.key", "browserstack.accessKey", "proxyPassword", "authToken", "clientSecret", "KEY"}) {
            assertTrue(CloudPresets.isCredentialKey(k), k);
        }
        for (String k : new String[] {"local", "localIdentifier", "seleniumCdp", "buildName", "keyboard", "tunnelName"}) {
            assertFalse(CloudPresets.isCredentialKey(k), k);
        }
    }

    @Test
    public void sauceExtendedDebuggingConflictsWithBidi() {
        Map<String, String> e = env("GATF_CLOUD", "sauce", "SAUCE_USERNAME", USER, "SAUCE_ACCESS_KEY", KEY);
        Map<String, Object> o = new LinkedHashMap<String, Object>(Map.of("extendedDebugging", Boolean.TRUE));
        assertThrows(CloudError.class, () -> CloudPresets.build("chrome", e, env(), null, o, true));
        assertNotNull(CloudPresets.build("chrome", e, env(), null, o, false));
    }

    @Test
    public void deviceEmulationOnChromiumOnly() {
        Preset p = CloudPresets.build("chrome", bs(), env(), PHONE, null, false);
        @SuppressWarnings("unchecked")
        Map<String, Object> metrics = (Map<String, Object>) p.mobileEmulation.get("deviceMetrics");
        assertEquals(390, metrics.get("width"));
        assertEquals(844, metrics.get("height"));
        assertEquals(3.0, metrics.get("pixelRatio"));
        assertEquals(Boolean.TRUE, metrics.get("mobile"));
        assertEquals(Boolean.TRUE, metrics.get("touch"));
        assertEquals(PHONE.userAgent, p.mobileEmulation.get("userAgent"));
        assertTrue(p.describe().contains("iPhone 14"));
        assertNotNull(CloudPresets.build("edge", bs(), env(), PHONE, null, false).mobileEmulation);
        CloudError x = assertThrows(CloudError.class, () -> CloudPresets.build("firefox", bs(), env(), PHONE, null, false));
        assertTrue(x.getMessage().contains("chrome or edge"), x.getMessage());
    }

    @Test
    public void safariNeedsAPlatformAndBadInputsFailEarly() {
        CloudError x = assertThrows(CloudError.class, () -> CloudPresets.build("safari", bs(), env(), null, null, false));
        assertTrue(x.getMessage().contains("GATF_CLOUD_PLATFORM"), x.getMessage());
        assertNotNull(CloudPresets.build("safari", bs("GATF_CLOUD_PLATFORM", "OS X Ventura"), env(), null, null, false));
        assertThrows(CloudError.class, () -> CloudPresets.build("opera", bs(), env(), null, null, false));
        assertThrows(CloudError.class, () -> CloudPresets.build("chrome", bs("GATF_CLOUD_PLATFORM", "Windows\"11"), env(), null, null, false));
        assertThrows(CloudError.class, () -> CloudPresets.build("chrome", bs("GATF_CLOUD_BROWSER_VERSION", "latest}}"), env(), null, null, false));
    }

    @Test
    public void statusScriptsPerVendor() {
        List<String> b = CloudPresets.statusScripts(Provider.BROWSERSTACK, "login \"x\".sel\n", false, "expected \"a\"\\b");
        assertEquals("browserstack_executor: {\"action\": \"setSessionName\", \"arguments\": {\"name\": \"login \\\"x\\\".sel\"}}", b.get(0));
        assertEquals("browserstack_executor: {\"action\": \"setSessionStatus\", \"arguments\": {\"status\": \"failed\", \"reason\": \"expected \\\"a\\\"\\\\b\"}}", b.get(1));
        assertEquals(Arrays.asList("sauce:job-name=t.sel", "sauce:job-result=passed"), CloudPresets.statusScripts(Provider.SAUCE, "t.sel", true, null));
        assertEquals(Arrays.asList("lambda-name=t.sel x", "lambda-status=failed"), CloudPresets.statusScripts(Provider.LAMBDATEST, "t.sel\rx", false, "r"));
        assertEquals("\"\\u0001\"", CloudPresets.json("\u0001"));
    }

    @Test
    public void redaction() {
        assertEquals("see https://***@hub.example/wd/hub", CloudPresets.redact("see https://u:" + KEY + "@hub.example/wd/hub", null));
        assertEquals("k=***", CloudPresets.redact("k=" + KEY, Arrays.asList(KEY)));
        assertEquals("abc", CloudPresets.redact("abc", Arrays.asList("ab", null)), "very short values are not treated as secrets");
        assertNull(CloudPresets.redact(null, null));
        assertEquals(Arrays.asList(KEY, USER), CloudPresets.allSecrets(bs()));
    }
}
