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
 * G8 — seleasy `randomize` value generator (alpha/numeric/range/prefixed/fuzzy... specs), moved verbatim from
 * SeleniumTest.randomize(String) (which now delegates here).
 */
@SuppressWarnings("unused")
final class RandomValues {

	private RandomValues() {}

	static String randomize(String tvalue) {
		String[] parts = tvalue.split("\\s+");
		String v1 = parts[0];
		String v2 = parts.length>1?parts[1]:null;
		String v3 = parts.length>2?parts[2]:null;
		int count = 10, totalcount = 1;
		
		if(v1.toLowerCase().equals("prefixed")) {
			return tvalue+System.currentTimeMillis();
		} else if(v1.toLowerCase().equals("prefixed_")) {
			return tvalue+"_"+System.currentTimeMillis();
		}
		
		if(v1.toLowerCase().equals("range")) {
			long min = 0;
			long max = 99999;
			if(StringUtils.isNotBlank(v2)) {
				try {
					min = Long.parseLong(v2);
				} catch (Exception e) {
				}
			}
			if(StringUtils.isNotBlank(v3)) {
				try {
					max = Long.parseLong(v3);
				} catch (Exception e) {
				}
			}
			long num = (long)(min + (Math.random() * (max - min)));
			return num+"";
		} else if(v1.toLowerCase().equals("fuzzyn")) {
			String sep = v2.charAt(0)+"";
			String[] prts = v2.substring(2).split(":");
			String finalNum = "";
			for (String prt : prts) {
				try {
					finalNum += RandomStringUtils.insecure().nextNumeric(Integer.valueOf(prt)) + sep;
				} catch (Exception e) {
				}
			}
			if(finalNum.endsWith(sep)) {
				finalNum = finalNum.substring(0, finalNum.length()-1);
			}
			return finalNum;
		} else if(v1.toLowerCase().equals("fuzzya")) {
			String sep = v2.charAt(0)+"";
			String[] prts = v2.substring(2).split(":");
			String finalNum = "";
			for (String prt : prts) {
				try {
					finalNum += RandomStringUtils.insecure().nextAlphabetic(Integer.valueOf(prt)) + sep;
				} catch (Exception e) {
				}
			}
			if(finalNum.endsWith(sep)) {
				finalNum = finalNum.substring(0, finalNum.length()-1);
			}
			return finalNum;
		} else if(v1.toLowerCase().equals("fuzzyauc")) {
			String sep = v2.charAt(0)+"";
			String[] prts = v2.substring(2).split(":");
			String finalNum = "";
			for (String prt : prts) {
				try {
					finalNum += RandomStringUtils.insecure().nextAlphabetic(Integer.valueOf(prt)).toUpperCase() + sep;
				} catch (Exception e) {
				}
			}
			if(finalNum.endsWith(sep)) {
				finalNum = finalNum.substring(0, finalNum.length()-1);
			}
			return finalNum;
		} else if(v1.toLowerCase().equals("fuzzyalc")) {
			String sep = v2.charAt(0)+"";
			String[] prts = v2.substring(2).split(":");
			String finalNum = "";
			for (String prt : prts) {
				try {
					finalNum += RandomStringUtils.insecure().nextAlphabetic(Integer.valueOf(prt)).toLowerCase() + sep;
				} catch (Exception e) {
				}
			}
			if(finalNum.endsWith(sep)) {
				finalNum = finalNum.substring(0, finalNum.length()-1);
			}
			return finalNum;
		} else if(v1.toLowerCase().equals("fuzzyan")) {
			String sep = v2.charAt(0)+"";
			String[] prts = v2.substring(2).split(":");
			String finalNum = "";
			for (String prt : prts) {
				try {
					finalNum += RandomStringUtils.insecure().nextAlphanumeric(Integer.valueOf(prt)) + sep;
				} catch (Exception e) {
				}
			}
			if(finalNum.endsWith(sep)) {
				finalNum = finalNum.substring(0, finalNum.length()-1);
			}
			return finalNum;
		} else if(v1.toLowerCase().equals("fuzzyanuc")) {
			String sep = v2.charAt(0)+"";
			String[] prts = v2.substring(2).split(":");
			String finalNum = "";
			for (String prt : prts) {
				try {
					finalNum += RandomStringUtils.insecure().nextAlphanumeric(Integer.valueOf(prt)).toUpperCase() + sep;
				} catch (Exception e) {
				}
			}
			if(finalNum.endsWith(sep)) {
				finalNum = finalNum.substring(0, finalNum.length()-1);
			}
			return finalNum;
		} else if(v1.toLowerCase().equals("fuzzyanlc")) {
			String sep = v2.charAt(0)+"";
			String[] prts = v2.substring(2).split(":");
			String finalNum = "";
			for (String prt : prts) {
				try {
					finalNum += RandomStringUtils.insecure().nextAlphanumeric(Integer.valueOf(prt)).toLowerCase() + sep;
				} catch (Exception e) {
				}
			}
			if(finalNum.endsWith(sep)) {
				finalNum = finalNum.substring(0, finalNum.length()-1);
			}
			return finalNum;
		}
		
		if(StringUtils.isNotBlank(v3)) {
			try {
				totalcount = Integer.parseInt(v3);
			} catch (Exception e) {
			}
		}
		if(v1.toLowerCase().equals("alpha") || v1.toLowerCase().equals("alphanumeric") || v1.toLowerCase().equals("numeric")
				|| v1.toLowerCase().equals("alphauc") || v1.toLowerCase().equals("alphanumericuc")
				|| v1.toLowerCase().equals("alphalc") || v1.toLowerCase().equals("alphanumericlc")) {
			if(StringUtils.isNotBlank(v2)) {
				try {
					count = Integer.parseInt(v2);
				} catch (Exception e) {
				}
			}
		}
		List<String> vals = new ArrayList<>();
		for(int i=0;i<totalcount;i++) {
			if(StringUtils.isBlank(v1) || v1.toLowerCase().equals("alpha")) {
				vals.add(RandomStringUtils.insecure().nextAlphabetic(count));
			} else if(v1.toLowerCase().equals("alphanumeric")) {
				vals.add(RandomStringUtils.insecure().nextAlphanumeric(count));
			} else if(v1.toLowerCase().equals("alphanumericuc")) {
				vals.add(RandomStringUtils.insecure().nextAlphanumeric(count).toUpperCase());
			} else if(v1.toLowerCase().equals("alphanumericlc")) {
				vals.add(RandomStringUtils.insecure().nextAlphanumeric(count).toLowerCase());
			} else if(v1.toLowerCase().equals("alphauc")) {
				vals.add(RandomStringUtils.insecure().nextAlphabetic(count).toUpperCase());
			} else if(v1.toLowerCase().equals("alphalc")) {
				vals.add(RandomStringUtils.insecure().nextAlphabetic(count).toLowerCase());
			} else if(v1.toLowerCase().equals("numeric")) {
				String fv = RandomStringUtils.insecure().nextNumeric(count);
				long v = Long.parseLong(fv);
				if(v==0) {
					fv = "1";
				}
				vals.add(fv);
			} else if(v1.toLowerCase().equals("value") && v2!=null) {
				vals.add(v2);
			}
		}
		return StringUtils.join(vals, " ");
	}

	static void randomizeSelect(WebElement le) {
		try
		{
			Select s = new Select(le);
			if(s.getOptions().size()>0) {
				List<Integer> li = new ArrayList<Integer>();
				for (WebElement o : s.getOptions())
				{
					li.add(Integer.parseInt(o.getDomProperty("index")));
				}
				for (WebElement o : s.getAllSelectedOptions())
				{
					li.remove(Integer.parseInt(o.getDomProperty("index")));
				}
				if(s.isMultiple()) {
					s.deselectAll();
					if(li.size()==0) {
						s.selectByIndex(0);
					} else {
						int ri = (int)(Math.random()*(li.size()-1));
						s.selectByIndex(li.get(ri));
					}
				} else {
					int i = Integer.parseInt(s.getFirstSelectedOption().getDomProperty("index"));
					s.deselectByIndex(i);
					if(li.size()==0) {
						s.selectByIndex(0);
					} else {
						int ri = (int)(Math.random()*(li.size()-1));
						s.selectByIndex(li.get(ri));
					}
				}
			}
		}
		catch (Exception e)
		{
		}
	}
}
