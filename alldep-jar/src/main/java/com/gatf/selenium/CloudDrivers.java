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

import java.io.File;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.UsernameAndPassword;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.json.Json;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.remote.http.ClientConfig;

import com.gatf.executor.core.AcceptanceTestContext;
import com.gatf.selenium.SeleniumTest.PassSubTestException;
import com.gatf.selenium.SeleniumTest.SeleniumTestResult;
import com.gatf.selenium.SeleniumTest.WarnSubTestException;
import com.gatf.selenium.SeleniumTestSession.SeleniumResult;
import com.gatf.selenium.cloud.CloudPresets;
import com.gatf.selenium.emulation.Devices;

/**
 * H5 (Δ157): the Selenium side of {@link CloudPresets}. Three entry points, each a one-statement hook:
 * <ul>
 * <li>{@link #routesPlainKeys()} (codegen): with a device cloud selected, plain {@code open chrome|firefox|safari|ie}
 *     goes through {@code getDockerDriver} like the {@code -dkr} forms, so the same .sel reaches the cloud;</li>
 * <li>{@link #createIfSelected} ({@link BrowserDrivers#create}): the cloud RemoteWebDriver, credentials as HTTP Basic
 *     auth through Selenium's ClientConfig (never in the URL or the capabilities);</li>
 * <li>{@link #onResult} ({@code SeleniumTest.pushResult}): name + passed/failed to the vendor dashboard via its
 *     documented executeScript hook. Best-effort: a failure is logged at debug and changes nothing.</li>
 * </ul>
 * With neither {@code GATF_CLOUD} nor {@code -Dgatf.cloud} set, every entry point is a no-op.
 */
final class CloudDrivers {

	private static final Logger LOG = LogManager.getLogger(CloudDrivers.class);

	private CloudDrivers() {}

	/** One cloud session GATF created: where to send the status, and what to scrub from any text. */
	static final class Session {
		final CloudPresets.Provider provider;
		final List<String> secrets;
		volatile String lastSent;
		Session(CloudPresets.Provider provider, List<String> secrets) {
			this.provider = provider;
			this.secrets = secrets;
		}
	}

	/** By WebDriver session id (an augmented driver keeps the id); bounded, oldest dropped. */
	static final Map<String, Session> SESSIONS = Collections.synchronizedMap(new LinkedHashMap<String, Session>(16, 0.75f, true) {
		private static final long serialVersionUID = 1L;
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Session> e) { return size() > 512; }
	});

	/** Tests only (a fake hub); there is deliberately no user-facing knob for an arbitrary hub URL. */
	static volatile String HUB_FOR_TESTS;

	static Map<String, String> props() {
		Map<String, String> m = new HashMap<String, String>();
		for (String n : System.getProperties().stringPropertyNames()) {
			if (n.startsWith("gatf.")) m.put(n, System.getProperty(n));
		}
		return m;
	}

	/** SELENIUM_REMOTE_URL / -Dgatf.selenium.remoteUrl: an explicit Grid always wins over a cloud preset. */
	static boolean explicitRemoteUrl(Map<String, String> env, Map<String, String> props) {
		String u = env.get("SELENIUM_REMOTE_URL");
		if (u == null || u.trim().isEmpty()) u = props.get("gatf.selenium.remoteUrl");
		return u != null && !u.trim().isEmpty();
	}

	/** Codegen: route plain local browser keys through getDockerDriver (a cloud is selected, no explicit Grid URL). */
	static boolean routesPlainKeys() {
		Map<String, String> env = System.getenv(), props = props();
		return CloudPresets.selected(env, props) != null && !explicitRemoteUrl(env, props);
	}

	/** The cloud session for this browser, or null when no cloud is selected or SELENIUM_REMOTE_URL wins. */
	static WebDriver createIfSelected(String browserName, Capabilities caps, AcceptanceTestContext cxt) {
		return createIfSelected(browserName, caps, cxt, System.getenv(), props());
	}

	static WebDriver createIfSelected(String browserName, Capabilities caps, AcceptanceTestContext cxt,
			Map<String, String> env, Map<String, String> props) {
		String sel = CloudPresets.selected(env, props);
		if (sel == null) return null;
		if (explicitRemoteUrl(env, props)) {
			System.out.println("[gatf] SELENIUM_REMOTE_URL is set and wins over GATF_CLOUD; the device-cloud preset is not used");
			return null;
		}
		String withGrid = env.get("GATF_WITH_GRID");
		if ((withGrid != null && !withGrid.trim().isEmpty()) || props.get("gatf.withGrid") != null) {
			System.out.println("[gatf] GATF_CLOUD wins over GATF_WITH_GRID; no local Grid is started");
		}
		Devices.Device device = null;
		String devName = env.get("GATF_CLOUD_DEVICE");
		if (devName == null || devName.trim().isEmpty()) devName = props.get("gatf.cloud.device");
		if (devName != null && !devName.trim().isEmpty()) {
			File custom = cxt != null ? cxt.getResourceFile("devices.json") : null;
			try {
				device = Devices.find(devName.trim(), custom);
			} catch (IllegalArgumentException e) {
				throw new CloudPresets.CloudError("GATF_CLOUD_DEVICE: " + CloudPresets.redact(e.getMessage(), CloudPresets.allSecrets(env)));
			}
		}
		Map<String, Object> extra = parseOptions(env, props);
		boolean ws = Boolean.TRUE.equals(caps.getCapability("webSocketUrl"));
		CloudPresets.Preset p = CloudPresets.build(browserName, env, props, device, extra, ws);

		MutableCapabilities mc = caps instanceof MutableCapabilities ? (MutableCapabilities) caps : new MutableCapabilities(caps);
		for (Map.Entry<String, Object> e : p.capabilities.entrySet()) {
			mc.setCapability(e.getKey(), e.getValue());
		}
		if (p.mobileEmulation != null) {
			if (!(mc instanceof org.openqa.selenium.chromium.ChromiumOptions)) {
				throw new CloudPresets.CloudError("GATF_CLOUD_DEVICE needs Chrome/Edge options for mobile emulation");
			}
			((org.openqa.selenium.chromium.ChromiumOptions<?>) mc).setExperimentalOption("mobileEmulation", p.mobileEmulation);
		}
		RemoteWebDriver d;
		try {
			URL url = java.net.URI.create(HUB_FOR_TESTS != null ? HUB_FOR_TESTS : p.remoteUrl).toURL();
			ClientConfig cc = ClientConfig.defaultConfig().baseUrl(url).authenticateAs(new Credentials(p.username(), p.accessKey()));
			d = new RemoteWebDriver(url, mc, cc);
		} catch (java.net.MalformedURLException e) {
			throw new CloudPresets.CloudError("internal: bad hub URL for " + p.provider.id);
		} catch (RuntimeException e) {
			String m = String.valueOf(e.getMessage()).split("\n")[0];
			throw new IllegalStateException("Could not start a " + p.describe() + " session: " + CloudPresets.redact(m, p.secrets()), e);
		}
		String sid = String.valueOf(d.getSessionId());
		SESSIONS.put(sid, new Session(p.provider, p.secrets()));
		System.out.println("[gatf] device cloud " + p.describe() + " (session " + sid + ")");
		return d;
	}

	/** GATF_CLOUD_OPTIONS / -Dgatf.cloud.options: a JSON object merged into the vendor options. */
	static Map<String, Object> parseOptions(Map<String, String> env, Map<String, String> props) {
		String raw = env.get("GATF_CLOUD_OPTIONS");
		if (raw == null || raw.trim().isEmpty()) raw = props.get("gatf.cloud.options");
		if (raw == null || raw.trim().isEmpty()) return null;
		Object o;
		try {
			o = new Json().toType(raw.trim(), Json.OBJECT_TYPE);
		} catch (RuntimeException e) {
			o = null;   // the parser's message can quote the input: never surface it
		}
		if (!(o instanceof Map)) throw new CloudPresets.CloudError("GATF_CLOUD_OPTIONS is not a JSON object");
		@SuppressWarnings("unchecked")
		Map<String, Object> m = (Map<String, Object>) o;
		return new LinkedHashMap<String, Object>(m);
	}

	/**
	 * After a result is stored: send the session name and the aggregate status (failed if any result of the session
	 * failed) to each cloud driver of the session. Sent again only when the status changes. Never throws.
	 */
	static void onResult(SeleniumTestSession sess, String scriptPath) {
		if (SESSIONS.isEmpty() || sess == null) return;
		try {
			boolean passed = true;
			String reason = "";
			for (SeleniumResult r : sess.__result__.values()) {
				if (r == null) continue;
				String f = failure(r.result);
				for (SeleniumTestResult c : r.__cresult__.values()) {
					if (f == null) f = failure(c);
				}
				if (f != null && passed) {
					passed = false;
					reason = f;
				}
			}
			String name = scriptPath == null ? "gatf" : new File(scriptPath).getName();
			if (sess.sessionName != null && !sess.sessionName.trim().isEmpty()) name += " [" + sess.sessionName.trim() + "]";
			for (int i = 0; i < sess.___d___.size(); i++) {
				try {
					if (i < sess.___dqs___.size() && Boolean.TRUE.equals(sess.___dqs___.get(i))) continue;
					WebDriver d = sess.___d___.get(i);
					if (!(d instanceof RemoteWebDriver) || !(d instanceof JavascriptExecutor)) continue;
					Session s = SESSIONS.get(String.valueOf(((RemoteWebDriver) d).getSessionId()));
					if (s == null) continue;
					String key = (passed ? "passed" : "failed") + "|" + name;
					if (key.equals(s.lastSent)) continue;
					s.lastSent = key;
					for (String script : CloudPresets.statusScripts(s.provider, CloudPresets.redact(name, s.secrets), passed,
							CloudPresets.redact(reason, s.secrets))) {
						try {
							((JavascriptExecutor) d).executeScript(script);
						} catch (Throwable t) {
							LOG.debug("device-cloud status callback ({}) not delivered: {}", s.provider.id, CloudPresets.redact(String.valueOf(t.getMessage()).split("\n")[0], s.secrets));
						}
					}
				} catch (Throwable t) {
					LOG.debug("device-cloud status callback skipped: {}", t.getClass().getSimpleName());
				}
			}
		} catch (Throwable t) {
			LOG.debug("device-cloud status callback skipped: {}", t.getClass().getSimpleName());
		}
	}

	/** First line of the failure, or null when the result passed (pass/warn subtest markers count as passed). */
	static String failure(SeleniumTestResult t) {
		return t == null ? null : failure(t.isStatus(), t.getCause());
	}

	static String failure(boolean status, Throwable c) {
		if (status) return null;
		if (c instanceof PassSubTestException || c instanceof WarnSubTestException) return null;
		if (c == null) return "failed";
		String m = c.getMessage();
		return m == null || m.trim().isEmpty() ? c.getClass().getSimpleName() : m.trim().split("\n")[0];
	}

	/** Selenium's UsernameAndPassword with a toString that cannot print the key. */
	static final class Credentials extends UsernameAndPassword {
		Credentials(String user, String key) { super(user, key); }
		@Override
		public String toString() { return "Credentials[redacted]"; }
	}
}
