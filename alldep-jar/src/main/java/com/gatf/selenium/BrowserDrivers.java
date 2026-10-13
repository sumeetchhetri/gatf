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

import java.awt.AWTException;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.security.Security;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.Stack;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.openqa.selenium.Alert;
import org.openqa.selenium.By;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.NoSuchWindowException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.Pdf;
import org.openqa.selenium.Point;
import org.openqa.selenium.PrintsPage;
import org.openqa.selenium.Rectangle;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.devtools.DevTools;
import org.openqa.selenium.devtools.HasDevTools;
import org.openqa.selenium.devtools.v150.dom.DOM;
import org.openqa.selenium.devtools.v150.dom.model.Node;
import org.openqa.selenium.devtools.v150.dom.model.NodeId;
import org.openqa.selenium.devtools.v150.fetch.Fetch;
import org.openqa.selenium.devtools.v150.fetch.model.HeaderEntry;
import org.openqa.selenium.devtools.v150.fetch.model.RequestPattern;
import org.openqa.selenium.devtools.v150.fetch.model.RequestStage;
import org.openqa.selenium.devtools.v150.log.Log;
import org.openqa.selenium.devtools.v150.network.Network;
import org.openqa.selenium.devtools.v150.page.Page.PrintToPDFResponse;
import org.openqa.selenium.devtools.v150.target.Target;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.interactions.Pause;
import org.openqa.selenium.interactions.PointerInput;
import org.openqa.selenium.interactions.Sequence;
import org.openqa.selenium.logging.LogEntry;
import org.openqa.selenium.logging.LogType;
import org.openqa.selenium.logging.LoggingPreferences;
import org.openqa.selenium.print.PrintOptions;
import org.openqa.selenium.remote.Augmenter;
import org.openqa.selenium.remote.LocalFileDetector;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.locators.RelativeLocator;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import com.gatf.executor.core.AcceptanceTestContext;
import com.gatf.executor.core.GatfExecutorConfig;
import com.gatf.executor.core.WorkflowContextHandler;
import com.gatf.executor.dataprovider.FileTestDataProvider;
import com.gatf.executor.dataprovider.MongoDBTestDataSource;
import com.gatf.executor.dataprovider.SQLDatabaseTestDataSource;
import com.gatf.executor.dataprovider.TestDataSource;
import com.gatf.executor.executor.TestCaseExecutorUtil;
import com.gatf.selenium.Command.FindCommandImpl;
import com.gatf.selenium.Command.GatfSelCodeParseError;
import com.gatf.selenium.SeleniumTestSession.SeleniumResult;
import com.google.common.io.Resources;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.jayway.jsonpath.JsonPath;
import io.appium.java_client.AppiumDriver;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.ios.IOSDriver;
import jakarta.xml.bind.DatatypeConverter;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract1;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import ru.yandex.qatools.ashot.AShot;
import ru.yandex.qatools.ashot.Screenshot;
import ru.yandex.qatools.ashot.shooting.ShootingStrategies;
import technology.tabula.ObjectExtractor;
import technology.tabula.Page;
import technology.tabula.PageIterator;
import technology.tabula.RectangularTextContainer;
import technology.tabula.Table;
import technology.tabula.extractors.SpreadsheetExtractionAlgorithm;

/**
 * G8 — WebDriver construction for a browser key: Selenium Grid (SELENIUM_REMOTE_URL), local drivers via
 * Selenium Manager, Chrome for Testing headless-shell (076), BiDi socket negotiation for tracing (081).
 * Moved verbatim from SeleniumTest.getDockerDriver (which now delegates here).
 */
@SuppressWarnings("unused")
final class BrowserDrivers {

	private BrowserDrivers() {}

	static WebDriver create(String browserName, org.openqa.selenium.Capabilities caps, AcceptanceTestContext ___cxt___) {
		// G9: tracing over WebDriver BiDi needs the session to negotiate a BiDi socket ('webSocketUrl').
		// Non-Chromium browsers (Firefox, Safari) have no CDP, so they always trace over BiDi; Chrome/Edge
		// only when traceProtocol is bidi|hybrid (default cdp keeps today's behaviour). Local + Grid alike.
		try {
			GatfExecutorConfig __tcfg = ___cxt___!=null ? ___cxt___.getGatfExecutorConfig() : null;
			if (__tcfg!=null && __tcfg.captureTraceThisRun() && caps instanceof org.openqa.selenium.MutableCapabilities) {
				String __tp = __tcfg.getTraceProtocol();
				boolean __chromium = "chrome".equals(browserName) || "edge".equals(browserName);
				if (!__chromium || "bidi".equals(__tp) || "hybrid".equals(__tp)) {
					((org.openqa.selenium.MutableCapabilities) caps).setCapability("webSocketUrl", true);
				}
			}
		} catch (Exception __ignore) { /* never block driver creation */ }
		// Δ157 (H5): device-cloud preset (GATF_CLOUD / -Dgatf.cloud); null when unset or when SELENIUM_REMOTE_URL wins
		WebDriver __cloud = CloudDrivers.createIfSelected(browserName, caps, ___cxt___);
		if (__cloud != null) return __cloud;
		// G11: SELENIUM_REMOTE_URL / -Dgatf.selenium.remoteUrl, or an on-demand official Grid (GATF_WITH_GRID)
		// Δ145d: `open safari` comes here now; no official Grid image runs Safari, so GATF_WITH_GRID never applies to it
		String __gridUrl = "safari".equals(browserName) ? SeleniumGrid.explicitRemoteUrl() : SeleniumGrid.remoteUrl(browserName);
		if (__gridUrl != null && !__gridUrl.trim().isEmpty()) {
			try {
				return new RemoteWebDriver(new URL(__gridUrl.trim()), caps);
			} catch (java.net.MalformedURLException e) {
				throw new RuntimeException("Invalid SELENIUM_REMOTE_URL: " + __gridUrl, e);
			}
		}
		// Local driver: Selenium Manager (built into Selenium 4.6+) auto-resolves the driver and
		// downloads the browser (Chrome for Testing) when absent -- no docker, no manual setup.
		try {
			switch (browserName) {
				case "chrome": {
					org.openqa.selenium.chrome.ChromeOptions __co = (org.openqa.selenium.chrome.ChromeOptions) caps;
					// Optional local browser-binary override: a path, or GATF_CHROME_BINARY=headless-shell to
					// auto-provision Chrome for Testing's chrome-headless-shell + its matching chromedriver
					// (delta-076, G4). Selenium Manager still handles every other local chrome.
					String __bin = System.getenv("GATF_CHROME_BINARY");
					if (__bin == null || __bin.trim().isEmpty()) __bin = System.getProperty("gatf.chrome.binary");
					if (ChromeForTesting.isHeadlessShellRequest(__bin)) {
						try {
							ChromeForTesting.Install __cft = ChromeForTesting.headlessShell();
							__co.setBinary(__cft.browser.getAbsolutePath());
							return new org.openqa.selenium.chrome.ChromeDriver(new org.openqa.selenium.chrome.ChromeDriverService.Builder()
									.usingDriverExecutable(__cft.driver).build(), __co);
						} catch (RuntimeException e) {
							throw e;
						} catch (Exception e) {
							throw new RuntimeException("chrome-headless-shell provisioning failed: " + e.getMessage(), e);
						}
					}
					if (__bin != null && !__bin.trim().isEmpty()) __co.setBinary(__bin.trim());
					return new org.openqa.selenium.chrome.ChromeDriver(__co);
				}
				case "firefox":
					return new org.openqa.selenium.firefox.FirefoxDriver((org.openqa.selenium.firefox.FirefoxOptions) caps);
				case "edge":
					return new org.openqa.selenium.edge.EdgeDriver((org.openqa.selenium.edge.EdgeOptions) caps);
				case "ie":
					return new org.openqa.selenium.ie.InternetExplorerDriver((org.openqa.selenium.ie.InternetExplorerOptions) caps);
				case "safari":
					return new org.openqa.selenium.safari.SafariDriver((org.openqa.selenium.safari.SafariOptions) caps);
				default:
					throw new RuntimeException("Unsupported local browser: " + browserName);
			}
		} catch (org.openqa.selenium.WebDriverException __local) {
			// G11: explain the options (gatf grid up / GATF_WITH_GRID / docker run) instead of a bare driver error
			throw SeleniumGrid.withGuidance(browserName, __local);
		}
	}
}
