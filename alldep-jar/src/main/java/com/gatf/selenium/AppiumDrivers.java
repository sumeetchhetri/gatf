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

import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.openqa.selenium.Capabilities;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.remote.http.ClientConfig;

import com.gatf.executor.core.AcceptanceTestContext;
import com.gatf.selenium.cloud.CloudPresets;
import com.gatf.selenium.mobile.AppiumCaps;
import com.gatf.selenium.mobile.MobilePlatform;

/**
 * H5 (Δ158): the Appium 2 session for {@code open appium-android | appium-ios}.
 * <ul>
 * <li>Local: an Appium server at {@code GATF_APPIUM_URL}, the driver config's url, or {@code http://127.0.0.1:4723},
 *     with the {@link AppiumCaps} capabilities.</li>
 * <li>Device cloud: with {@code GATF_CLOUD} set (and no {@code GATF_APPIUM_URL}), the {@link CloudPresets#buildAppium}
 *     real-device preset. Credentials go as HTTP Basic auth through ClientConfig (never in the URL or capabilities), the
 *     session joins {@link CloudDrivers#SESSIONS} so the Δ157 status hook names it and marks it passed/failed, and
 *     every message is redacted.</li>
 * </ul>
 * The session is an {@code AndroidDriver} / {@code IOSDriver} (java-client 10), so the existing {@code instanceof AppiumDriver}
 * paths (screenshots) apply. It is never handed to the Δ154 worker pool: codegen calls this directly, without
 * {@code __workerDriver__} / {@code __workerAdopt__}.
 */
final class AppiumDrivers {

	private AppiumDrivers() {}

	/** How a session is constructed; the default builds the java-client driver. Tests swap it for a recording fake. */
	interface Factory {
		WebDriver create(MobilePlatform p, ClientConfig cc, Capabilities caps);
	}

	static final Factory DEFAULT = (p, cc, caps) -> p == MobilePlatform.IOS
			? new io.appium.java_client.ios.IOSDriver(cc, caps)
			: new io.appium.java_client.android.AndroidDriver(cc, caps);

	/** Tests only. */
	static volatile Factory FACTORY = DEFAULT;

	static WebDriver create(String key, String configUrl, Map<String, String> configCaps, String version, AcceptanceTestContext cxt) {
		return create(key, configUrl, configCaps, version, System.getenv(), CloudDrivers.props());
	}

	static WebDriver create(String key, String configUrl, Map<String, String> configCaps, String version,
			Map<String, String> env, Map<String, String> props) {
		MobilePlatform p = MobilePlatform.ofKey(key);
		if (p == null) throw new IllegalArgumentException("not an Appium browser key: " + key);
		Map<String, Object> local = AppiumCaps.local(p, configCaps, version);
		boolean cloud = CloudPresets.selected(env, props) != null;
		if (cloud && AppiumCaps.explicitUrl(env, props)) {
			System.out.println("[gatf] " + AppiumCaps.ENV_URL + " is set and wins over GATF_CLOUD; the real-device preset is not used");
			cloud = false;
		}
		if (cloud) {
			CloudPresets.Preset pr = CloudPresets.buildAppium(p, env, props, local, CloudDrivers.parseOptions(env, props));
			ClientConfig cc = config(pr.remoteUrl, CloudDrivers.HUB_FOR_TESTS)
					.authenticateAs(new CloudDrivers.Credentials(pr.username(), pr.accessKey()));
			WebDriver d = start(p, cc, new MutableCapabilities(pr.capabilities), pr.describe(), pr.secrets());
			String sid = sessionId(d);
			if (sid != null) CloudDrivers.SESSIONS.put(sid, new CloudDrivers.Session(pr.provider, pr.secrets()));
			System.out.println("[gatf] device cloud " + pr.describe() + " (session " + sid + ")");
			return d;
		}
		String url = AppiumCaps.url(configUrl, env, props);
		String shown = CloudPresets.redact(url, Collections.<String>emptyList());
		WebDriver d = start(p, config(url, null), new MutableCapabilities(local), "appium " + AppiumCaps.label(p, local) + " via " + shown,
				CloudPresets.allSecrets(env));
		System.out.println("[gatf] appium " + AppiumCaps.label(p, local) + " via " + shown + " (session " + sessionId(d) + ")");
		return d;
	}

	/** Device sessions start slowly (app install): a 5-minute read timeout instead of Selenium's 3. */
	private static ClientConfig config(String url, String override) {
		try {
			return ClientConfig.defaultConfig().baseUri(URI.create(override != null ? override : url)).readTimeout(Duration.ofMinutes(5));
		} catch (IllegalArgumentException e) {
			throw new IllegalStateException("Appium server URL is not a URL: " + CloudPresets.redact(url, Collections.<String>emptyList()));
		}
	}

	private static WebDriver start(MobilePlatform p, ClientConfig cc, Capabilities caps, String what, List<String> secrets) {
		try {
			return FACTORY.create(p, cc, caps);
		} catch (RuntimeException e) {
			String m = String.valueOf(e.getMessage()).split("\n")[0];
			throw new IllegalStateException("Could not start an " + what + " session: " + CloudPresets.redact(m, secrets), e);
		}
	}

	private static String sessionId(WebDriver d) {
		return d instanceof RemoteWebDriver && ((RemoteWebDriver) d).getSessionId() != null ? String.valueOf(((RemoteWebDriver) d).getSessionId()) : null;
	}
}
