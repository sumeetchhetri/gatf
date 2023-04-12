/*
    Copyright 2013-2019, Sumeet Chhetri

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
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.Charset;
import java.security.Security;
import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.joda.time.DateTime;
import org.openqa.selenium.Alert;
import org.openqa.selenium.By;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
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
import org.openqa.selenium.devtools.v108.dom.DOM;
import org.openqa.selenium.devtools.v108.dom.model.Node;
import org.openqa.selenium.devtools.v108.dom.model.NodeId;
import org.openqa.selenium.devtools.v108.fetch.Fetch;
import org.openqa.selenium.devtools.v108.fetch.model.HeaderEntry;
import org.openqa.selenium.devtools.v108.fetch.model.RequestPattern;
import org.openqa.selenium.devtools.v108.fetch.model.RequestStage;
import org.openqa.selenium.devtools.v108.log.Log;
import org.openqa.selenium.devtools.v108.network.Network;
import org.openqa.selenium.devtools.v108.network.model.ResourceType;
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
import com.gatf.selenium.Command.GatfSelCodeParseError;
import com.gatf.selenium.SeleniumTestSession.SeleniumResult;
import com.github.dockerjava.api.DockerClient;
import com.google.common.io.Resources;
import com.jayway.jsonpath.JsonPath;

import io.appium.java_client.AppiumDriver;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.ios.IOSDriver;
import io.github.bonigarcia.wdm.WebDriverManager;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import ru.yandex.qatools.ashot.AShot;
import ru.yandex.qatools.ashot.Screenshot;
import ru.yandex.qatools.ashot.shooting.ShootingStrategies;

public abstract class SeleniumTest {
	@SuppressWarnings("serial")
	protected final static Map<String, Level> LOG_LEVEL_BY_NAME_MAP = new HashMap<String, Level>(){{
		put(Level.ALL.getName().toLowerCase(), Level.ALL);
		put(Level.CONFIG.getName().toLowerCase(), Level.CONFIG);
		put(Level.FINE.getName().toLowerCase(), Level.FINE);
		put(Level.FINER.getName().toLowerCase(), Level.FINER);
		put(Level.FINEST.getName().toLowerCase(), Level.FINEST);
		put(Level.INFO.getName().toLowerCase(), Level.INFO);
		put(Level.OFF.getName().toLowerCase(), Level.OFF);
		put(Level.SEVERE.getName().toLowerCase(), Level.SEVERE);
		put(Level.WARNING.getName().toLowerCase(), Level.WARNING);
	}};
	@SuppressWarnings("serial")
	protected final static HashSet<String> LOG_TYPES_SET = new HashSet<String>() {{
		add(LogType.BROWSER);
		add(LogType.CLIENT);
		add(LogType.DRIVER);
		add(LogType.PERFORMANCE);
		add(LogType.PROFILER);
		add(LogType.SERVER);
	}};
	
	private long timeoutSleepGranularity = 1000;

	private static final String TOP_LEVEL_PROV_NAME = UUID.randomUUID().toString();

	private transient AcceptanceTestContext ___cxt___ = null;
	
    protected transient Map<String, WebDriverManager> wdmMgs = new ConcurrentHashMap<>();
    protected transient Map<String, Boolean> wdmSessions = new ConcurrentHashMap<>();

	List<SeleniumTestSession> sessions = new ArrayList<SeleniumTestSession>();
	
	protected int line;

	protected String name;

	protected transient int sessionNum = -1;

	protected int index;
	
	public static boolean CONC_WD = false;
	
	public static Lock createLock() {
		if(CONC_WD) {
			return new ReentrantLock();
		} else {
			return new Lock() {
				@Override
				public void unlock() {
				}
				@Override
				public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
					return false;
				}
				@Override
				public boolean tryLock() {
					return false;
				}
				@Override
				public Condition newCondition() {
					return null;
				}
				@Override
				public void lockInterruptibly() throws InterruptedException {
				}
				@Override
				public void lock() {
				}
			};
		}
	}
	
	public static Map<String, Map<String, String>> DRV_FEATURES = new ConcurrentHashMap<String, Map<String,String>>();
	
	public static ThreadLocal<ImmutableTriple<Boolean, String, String[]>> IN_DOCKER = new ThreadLocal<>();
	public static ThreadLocal<Boolean> IS_GATF = new ThreadLocal<>();
	
	//public static ThreadLocal<>
	
	/*public void setUpCC(SeleniumTestSession sess) {
		sess.ccStatus = true;
		try {
			sess.ccServer = new ServerSocket();
			System.out.println("Command Server Listening on port " + sess.ccServer.getLocalPort());
			sess.ccThrd = new Thread(new Runnable() {
				@Override
				public void run() {
					while(sess.ccStatus) {
						try {
							Socket c = sess.ccServer.accept();
							System.out.println("Agent Connected from " + c.getRemoteSocketAddress().toString());
							BufferedReader bio = new BufferedReader(new InputStreamReader(c.getInputStream()));
							String command = bio.readLine();
							switch (command) {
								case "screenshot":
									screenshotAsFile(get___d___(), command);
									break;
								default:
									break;
							}
						} catch (Exception e) {
						}
					}
					try {
						sess.ccServer.close();
					} catch (Exception e) {
					}
				}
			});
			sess.ccThrd.start();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void cleanUpCC(SeleniumTestSession sess) {
		sess.ccStatus = false;
	}*/
	
	protected String addWdm(String browserName, Capabilities capabilities) {
		IS_GATF.set(true);
		boolean isDocker = false;
		boolean isHeadless = false;
		boolean isRecording = false;
		if(browserName.endsWith("-rec")) {
			isDocker = true;
			isRecording = true;
			browserName= browserName.replaceFirst("-rec", "");
		} else if(browserName.endsWith("-dkr")) {
			isDocker = true;
			browserName= browserName.replaceFirst("-dkr", "");
		} else if(browserName.endsWith("-hdl")) {
			isDocker = true;
			isHeadless = true;
			browserName= browserName.replaceFirst("-hdl", "");
		}
		
		if(isDocker) {
			if(!WebDriverManager.isDockerAvailable()) {
				if(isRecording) {
					throw new RuntimeException("Docker not available, recording cannot proceed, will not execute test");
				} else {
					System.out.println("Docker not available, will proceed with local browser/driver...");
					IN_DOCKER.set(new ImmutableTriple<Boolean, String, String[]>(false, null, null));
					isDocker = false;
				}
			} else {
				IN_DOCKER.set(new ImmutableTriple<Boolean, String, String[]>(true, null, null));
			}
		} else {
			IN_DOCKER.set(new ImmutableTriple<Boolean, String, String[]>(false, null, null));
		}
		
		WebDriverManager wdm = null;
		switch(browserName) {
			case "chrome": {
				wdm = WebDriverManager.chromedriver();
				break;
			}
			case "firefox": {
				wdm = WebDriverManager.firefoxdriver();
				break;
			}
			case "ie": {
				wdm = WebDriverManager.iedriver();
				break;
			}
			case "edge": {
				wdm = WebDriverManager.edgedriver();
				break;
			}
			case "safari": {
				wdm = WebDriverManager.safaridriver();
				break;
			}
			case "opera": {
				wdm = WebDriverManager.operadriver();
				break;
			}
			default: {
				throw new RuntimeException("Invalid browser name specified for browser");
			}
		}
		
		if(isDocker) {
			wdm.config().setDockerBrowserSelenoidImageFormat("sumeetchhetri/vnc:%s_%s");
			wdm.config().setDockerTimezone(ZoneId.systemDefault().toString());
			//wdm.config().setDockerShmSize("2g");
			wdm.capabilities(capabilities).browserInDocker();
			if(isRecording) {
				wdm.enableRecording().enableVnc();
			} else if(!isHeadless) {
				wdm.enableVnc();
			}
			browserName = UUID.randomUUID().toString()+"-"+(isRecording?"rec":"dkr");
			IN_DOCKER.set(new ImmutableTriple<Boolean, String, String[]>(true, null, new String[] {browserName, null}));
			wdmMgs.put(browserName, wdm);
		} else {
			//wdm.setup();
			wdmMgs.put(browserName, wdm);
			IN_DOCKER.set(new ImmutableTriple<Boolean, String, String[]>(false, null, null));
		}
		return browserName;
	}
	
	protected WebDriver getDockerDriver(String browserName) {
		boolean isDocker = false;
		boolean isHeadless = false;
		boolean isRecording = false;
		if(browserName.endsWith("-rec")) {
			isDocker = true;
			isRecording = true;
		} else if(browserName.endsWith("-dkr")) {
			isDocker = true;
		} else if(browserName.endsWith("-hdl")) {
			isDocker = true;
			isHeadless = true;
		}
		
		if(!WebDriverManager.isDockerAvailable()) {
			if(isRecording) {
				throw new RuntimeException("Docker not available, recording cannot proceed, will not execute test");
			} else {
				System.out.println("Docker not available, will proceed with local browser/driver...");
				isDocker = false;
				return wdmMgs.get(browserName).create();
			}
		}
		
		if(wdmMgs.containsKey(browserName) && isDocker) {
			WebDriver wd = wdmMgs.get(browserName).create();
			wdmSessions.put(getSessionId(wd), true);
			if(isRecording) {
				System.out.println(String.format("VNC URL for Docker Browser Session is [%s], Recording Path is [%s]", wdmMgs.get(browserName).getDockerNoVncUrl(), wdmMgs.get(browserName).getDockerRecordingPath()));
			} else if(!isHeadless) {
				System.out.println(String.format("VNC URL for Docker Browser Session is [%s]", wdmMgs.get(browserName).getDockerNoVncUrl()));
			}
			WebDriver augmented = null;
			try {
				Thread.sleep(2000);//Sleep for some time for cdp proxy to be up and running
			} catch (Exception e) {
			}
			int counter = 10;
			while(counter-->0) {
				try {
					Thread.sleep(1000);//Sleep for some time for cdp proxy to be up and running
				} catch (Exception e) {
				}
				try {
					augmented = new Augmenter().augment(wd);
					if(augmented instanceof HasDevTools) {
						System.out.println("Got augmented docker driver....");
						break;
					}
				} catch (Exception e) {
					System.out.println(SeleniumTest.IN_DOCKER.get());
					System.out.println(Arrays.asList(SeleniumTest.IN_DOCKER.get()));
					System.out.println("Getting augmented docker driver.... Attempt " + (10-counter));
				}
			}
			return augmented;
		}
		throw new RuntimeException("No driver found for remote docker");
	}
	
	protected void __set__cln__(int line) {
		this.line = line;
	}
	
	protected void setSleepGranularity(long timeoutSleepGranularity) {
		this.timeoutSleepGranularity = timeoutSleepGranularity; 
	}
	
	private static final Map<String, Boolean> BROWSER_FEATURES = new ConcurrentHashMap<String, Boolean>();

	protected void nextSession() {
		if(sessions.size()>sessionNum+1) {
			sessionNum++;
		}
	}

	protected void setSession(String sns, int sn, boolean startFlag) {
		if(sn>=0 && sessions.size()>sn) {
			sessionNum = sn;
		} else if(sns!=null) {
			sn = 0;
			for (SeleniumTestSession s : sessions) {
				if(s.sessionName.equalsIgnoreCase(sns)) {
					sessionNum = sn; 
				}
				sn++;
			}
		} else if(sn==-1 && sns==null) {
			sessionNum = 0;
		}
		if(startFlag) {
			startTest();
		}
	}

	@SuppressWarnings("serial")
	protected Set<String> addTest(String sessionName, String browserName) {
		final SeleniumTestSession s = new SeleniumTestSession();
		s.sessionName = sessionName==null?Integer.toHexString(sessionNum+1):sessionName;
		s.browserName = browserName;
		sessions.add(s);
		if(!s.__result__.containsKey(browserName)) {
			SeleniumResult r = new SeleniumResult();
			r.browserName = browserName;
			s.__result__.put(browserName, r);
		} else {
			//throw new RuntimeException("Duplicate browser defined");
		}
		return new HashSet<String>(){{add(s.sessionName); add((sessions.size()-1)+"");}};
	}

	protected void startTest() {
		if(getSession().__teststarttime__==0) {
			getSession().__teststarttime__ = System.nanoTime();
		}
	}

	public void pushResult(SeleniumTestResult result)
	{
		if(getSession().__subtestname__==null) {
			result.executionTime = System.nanoTime() - getSession().__teststarttime__;
			getSession().__result__.get(getSession().browserName).result = result;
			if(result!=null && !result.isStatus() && !result.isContinue) {
				//quit();
				FailureException fe = new FailureException(result.getCause());
				fe.details = new Object[] {"", line, name};
				throw fe;
			}
		} else {
			getSession().__result__.get(getSession().browserName).__cresult__.put(getSession().__subtestname__, result);
			//RuntimeReportUtil.addEntry(index, result.status);
		}
	}

	protected void newTopLevelProvider() {
		if(!getSession().providerTestDataMap.containsKey(TOP_LEVEL_PROV_NAME)) {
			getSession().providerTestDataMap.put(TOP_LEVEL_PROV_NAME, new ArrayList<Map<String,String>>());
			getSession().providerTestDataMap.get(TOP_LEVEL_PROV_NAME).add(new HashMap<String, String>());
		}
	}

	protected void newProvider(String name) {
		if(!getSession().providerTestDataMap.containsKey(name)) {
			getSession().providerTestDataMap.put(name, new ArrayList<Map<String,String>>());
		}
	}

	protected void delProvider(String name) {
		if(getSession().providerTestDataMap.containsKey(name)) {
			getSession().providerTestDataMap.remove(name);
		}
	}

	protected void addToTopLevelProviderTestDataMap(String name, String value) {
		getSession().providerTestDataMap.get(TOP_LEVEL_PROV_NAME).get(0).put(name, value);
	}

	protected List<Map<String, String>> getProviderTestDataMap(String name) {
		return getAllProviderData(getPn(name));
	}

	protected List<Map<String, String>> getSQLProviderTestDataMap(String dsn, String query, String vars) {
		SQLDatabaseTestDataSource sdt = ___cxt___.getSQLDSN(dsn);
		if(sdt!=null) {
			getSession().providerTestDataMap.put(dsn+query, sdt.provide(query, vars, ___cxt___));
			return getSession().providerTestDataMap.get(dsn+query);
		}
		throw new RuntimeException("No SQL DSN found with the name " + dsn);
	}

	protected List<Map<String, String>> getMongoProviderTestDataMap(String dsn, String query, String collName, String properties, String vars) {
		MongoDBTestDataSource sdt = ___cxt___.getMongoDSN(dsn);
		if(sdt!=null) {
			getSession().providerTestDataMap.put(dsn+query, sdt.provide(query, collName, properties, vars, ___cxt___));
			return getSession().providerTestDataMap.get(dsn+query);
		}
		throw new RuntimeException("No Mongo DSN found with the name " + dsn);
	}
	
	protected boolean executeQuery(String dsn, String query) {
		TestDataSource tds = ___cxt___.getDSN(dsn);
		if(tds!=null) {
			return tds.execute(query);
		}
		return false;
	}

	protected List<Map<String, String>> getFileProviderTestDataMap(String filePath, String vars) {
		return FileTestDataProvider.provide(filePath, vars, ___cxt___);
	}

	protected String getFileProviderHash(String name) {
		return ___cxt___.getFileProviderHash(name);
	}

	protected String getBrowserName() {
		return getSession().browserName;
	}

	protected String getSessionName() {
		return getSession().sessionName;
	}

	protected boolean isBrowserName(String bn) {
		if(StringUtils.isNotBlank(bn)) {
			bn = bn.trim();
			if(bn.charAt(0)==bn.charAt(bn.length()-1)) {
                if(bn.charAt(0)=='"' || bn.charAt(0)=='\'') {
                	bn = bn.substring(1, bn.length()-1);
                }
            }
		}
		return getSession().browserName.equals(bn);
	}

	protected boolean isSessionName(String bn) {
		return getSession().browserName.equals(bn);
	}

	private SeleniumTestSession getSession() {
		return sessions.get(sessionNum);
	}

	protected boolean matchesSessionId(String sname, int sessionId) {
		if(sname==null && sessionId==-1)return true;
		if(StringUtils.isBlank(sname)) {
			return sessionNum==sessionId;
		} else {
			int sn = 0;
			for (SeleniumTestSession s : sessions) {
				if(s.sessionName.equalsIgnoreCase(sname)) {
					return sessionNum==sn; 
				}
				sn++;
			}
		}
		return false;
	}

	private List<Map<String, String>> getAllProviderData(String pn) {
		List<Map<String, String>> ep = pn!=null?___cxt___.getAnyProviderData(pn, null):null;
		List<Map<String, String>> tp = pn!=null?getSession().providerTestDataMap.get(pn):null;
		List<Map<String, String>> fp = null;
		if((ep!=null && tp!=null) || tp!=null) {
			fp = tp;
		} else if(ep!=null) {
			fp = ep;
		}
		return fp;
	}

	private Map<String, Object> getFinalDataMap(String pn, Integer pp) {
		Map<String, Object> _mt = new HashMap<String, Object>();
		List<Map<String, String>> fp = getAllProviderData(pn);
		_mt.putAll(___cxt___.getWorkflowContextHandler().getGlobalSuiteAndTestLevelParameters(null, CollectionUtils.isEmpty(fp)?null:fp.get(pp), index));
		if(getSession().providerTestDataMap.containsKey(TOP_LEVEL_PROV_NAME) && getSession().providerTestDataMap.get(TOP_LEVEL_PROV_NAME).get(0)!=null) {
			_mt.putAll(getSession().providerTestDataMap.get(TOP_LEVEL_PROV_NAME).get(0));
		}
		return _mt;
	}

	static Pattern p = Pattern.compile("\\$(p|P)\\{([\"'a-zA-Z0-9_\\.]+)\\.([0-9]+)\\.([a-zA-Z0-9_\\.]+)\\}");
	
	private void initTmplMap(Map<String, Object> _mt) {
		_mt.put("M", Math.class);
		_mt.put("U", Util.class);
		_mt.put("today", Util.today);
		_mt.put("yesterday", Util.yesterday);
		_mt.put("tomorrow", Util.tomorrow);
		_mt.put("day", Util.day);
		_mt.put("lastWeek", Util.lastWeek);
		_mt.put("nextWeek", Util.nextWeek);
		_mt.put("week", Util.week);
		_mt.put("lastMonth", Util.lastMonth);
		_mt.put("nextMonth", Util.nextMonth);
		_mt.put("month", Util.month);
		_mt.put("lastYear", Util.lastYear);
		_mt.put("nextYear", Util.nextYear);
		_mt.put("year", Util.year);
		_mt.put("now", Util.now);
		_mt.put("lastSecond", Util.lastSecond);
		_mt.put("nextSecond", Util.nextSecond);
		_mt.put("second", Util.second);
		_mt.put("lastMinute", Util.lastMinute);
		_mt.put("nextMinute", Util.nextMinute);
		_mt.put("minute", Util.minute);
		_mt.put("lastHour", Util.lastHour);
		_mt.put("nextHour", Util.nextHour);
		_mt.put("hour", Util.hour);
		_mt.put("S", StringUtils.class);
		_mt.put("D", DateUtils.class);
	}

	/*
	 * Three types of template variables can be evaluated
	 * 1. ${var-name} - a value inside a provider slot or a top-level variable
	 * 2. $v{var-name} - a current context java variable
	 * 3. $p{prov-name.position.var-name} - a direct reference to a variable in provider at a positional map of values
	 */
	protected String evaluate(String tmpl) {
		if(tmpl.indexOf("$")==-1)return tmpl;
		try
		{
			if(getSession().__provdetails__.size()>0) {
				ArrayList<String> keys = new ArrayList<String>(getSession().__provdetails__.keySet());
				for (int i=keys.size()-1;i>=0;i--)
				{
					String pn = keys.get(i);
					Integer pp = getSession().__provdetails__.get(pn);
					Map<String, Object> _mt = getFinalDataMap(pn, pp);
					if(tmpl.indexOf("$v{")!=-1) {
						tmpl = tmpl.replace("$v", "$");
						_mt.putAll(getSession().__vars__);
					}
					initTmplMap(_mt);
					tmpl = ___cxt___.getWorkflowContextHandler().templatize(_mt, tmpl);
				}
			} else {
				Map<String, Object> _mt = getFinalDataMap(null, null);
				if(tmpl.indexOf("$v{")!=-1) {
					tmpl = tmpl.replace("$v", "$");
					_mt.putAll(getSession().__vars__);
				}
				initTmplMap(_mt);
				tmpl = ___cxt___.getWorkflowContextHandler().templatize(_mt, tmpl);
			}
			String tmpln = tmpl;
			Matcher m = p.matcher(tmpl);
			Map<String, Object> _mt = new HashMap<String, Object>();
			boolean foundProvRef = false;
			while(m.find()) {
				String[] parts = m.group(4).split("\\.");
				if(parts.length>1) {
					String ivp = parts[0];
					for (int i = 1; i < parts.length; i++) {
						ivp += "." + parts[i];
					}
					String provName = m.group(2);
					if((provName.startsWith("\"") && provName.endsWith("\"")) || (provName.startsWith("'") && provName.endsWith("'"))) {
						provName = provName.substring(1, provName.length()-1);
					}
					if(getSession().providerTestDataMap.containsKey(provName)) {
						foundProvRef = true;
						tmpln = tmpln.replace(tmpl.substring(m.start(), m.end()), "${"+ivp+"}");
						_mt.putAll(getSession().providerTestDataMap.get(provName).get(Integer.valueOf(m.group(3))));
					}
				}
			}
			if(foundProvRef) {
				initTmplMap(_mt);
				tmpl = ___cxt___.getWorkflowContextHandler().templatize(_mt, tmpln);
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		return tmpl;
	}
	
	protected boolean doEvalIf(String eval) {
		Map<String, Object> _mt = new HashMap<String, Object>();
		try {
			initTmplMap(_mt);
			String ret = ___cxt___.getWorkflowContextHandler().templatize(_mt, "#if("+evaluate(eval) + ") true #else false #end");
			return ret.toLowerCase().trim().equals("true");
		} catch (Exception e) {
		}
		return false;
	}

	protected String getProviderDataValue(String key, boolean isVar) {
		if(isVar) {
			Object o = ___get_var__(key);
			if(o!=null && o instanceof String) {
				return o.toString();
			}
			return null;
		}
		if(getSession().providerTestDataMap.containsKey(TOP_LEVEL_PROV_NAME) && getSession().providerTestDataMap.get(TOP_LEVEL_PROV_NAME).get(0).containsKey(key)) {
			return getSession().providerTestDataMap.get(TOP_LEVEL_PROV_NAME).get(0).get(key);
		}
		if(getSession().__provdetails__.size()>0) {
			ArrayList<String> keys = new ArrayList<String>(getSession().__provdetails__.keySet());
			for (int i=keys.size()-1;i>=0;i--)
			{
				String pn = keys.get(i);
				Integer pp = getSession().__provdetails__.get(pn);
				List<Map<String, String>> _t = getAllProviderData(pn);
				if(_t!=null && _t.get(pp)!=null) {
					Map<String, String> _mt = ___cxt___.getWorkflowContextHandler().getGlobalSuiteAndTestLevelParameters(null, _t.get(pp), index);
					if(_mt.containsKey(key)) {
						return _mt.get(key);
					}
				}
			}
			return null;
		} else {
			Map<String, String> _mt = ___cxt___.getWorkflowContextHandler().getGlobalSuiteAndTestLevelParameters(null, null, index);
			return _mt.get(key);
		}
	}

	protected Object getProviderDataValueO(String key, boolean isVar) {
		if(isVar) {
			return ___get_var__(key);
		}
		if(getSession().providerTestDataMap.containsKey(TOP_LEVEL_PROV_NAME) && getSession().providerTestDataMap.get(TOP_LEVEL_PROV_NAME).get(0).containsKey(key)) {
			return getSession().providerTestDataMap.get(TOP_LEVEL_PROV_NAME).get(0).get(key);
		}
		if(getSession().__provdetails__.size()>0) {
			ArrayList<String> keys = new ArrayList<String>(getSession().__provdetails__.keySet());
			for (int i=keys.size()-1;i>=0;i--)
			{
				String pn = keys.get(i);
				Integer pp = getSession().__provdetails__.get(pn);
				List<Map<String, String>> _t = getAllProviderData(pn);
				if(_t!=null && _t.get(pp)!=null) {
					Map<String, String> _mt = ___cxt___.getWorkflowContextHandler().getGlobalSuiteAndTestLevelParameters(null, _t.get(pp), index);
					if(_mt.containsKey(key)) {
						return _mt.get(key);
					}
				}
			}
			return null;
		} else {
			Map<String, String> _mt = ___cxt___.getWorkflowContextHandler().getGlobalSuiteAndTestLevelParameters(null, null, index);
			return _mt.get(key);
		}
	}

	protected void ___add_var__(String name, Object val) {
		getSession().__vars__.put(name, val);
	}

	protected Object ___get_var__(String name) {
		if(!getSession().__vars__.containsKey(name)) {
			throw new RuntimeException("Variable " + name + " not defined");
		}
		return getSession().__vars__.get(name);
	}

	protected Object ___get_var_nex__(String name) {
		return getSession().__vars__.get(name);
	}

	protected void ___del_var__(String name) {
		if(getSession().__vars__.containsKey(name)) {
			getSession().__vars__.remove(name);
		}
	}

	protected void addSubTest(String browserName, String stname, boolean isAnAlias) {
		if(isAnAlias) return;
		if(getSession().__result__.containsKey(browserName)) {
			if(!getSession().__result__.get(getSession().browserName).__cresult__.containsKey(stname)) {
				getSession().__result__.get(browserName).__cresult__.put(stname, null);
			} else {
				throw new RuntimeException("Duplicate subtest defined");
			}
		} else {
			throw new RuntimeException("Invalid browser specified");
		}
	}

	protected WebDriver get___d___()
	{
		if(getSession().___d___.size()==0)return null;
		return getSession().___d___.get(getSession().__wpos__);
	}

	
	protected void set___d___(WebDriver ___d___)
	{
		if(___d___ instanceof RemoteWebDriver) {
			try {
				((RemoteWebDriver)___d___).setFileDetector(new LocalFileDetector());
			} catch(Exception e) {
			}
		}
		___d___.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(100));
		getSession().___d___.add(___d___);
		getSession().___dqs___.add(false);
	}

	protected class PrettyPrintingMap<K, V> {
		private Map<K, V> map;

		public PrettyPrintingMap(Map<K, V> map) {
			this.map = map;
		}

		public String toString() {
			StringBuilder sb = new StringBuilder();
			Iterator<Entry<K, V>> iter = map.entrySet().iterator();
			while (iter.hasNext()) {
				Entry<K, V> entry = iter.next();
				sb.append(entry.getKey());
				sb.append('=').append('"');
				sb.append(entry.getValue());
				sb.append('"');
				if (iter.hasNext()) {
					sb.append(',').append(' ');
				}
			}
			return sb.toString();

		}
	}

	private String getPn(String name) {
		String pn = name;
		if(index>0) {
			pn += index;
		}
		return pn;
	}

	protected void ___cxt___add_param__(String name, Object value)
	{
		___cxt___.getWorkflowContextHandler().addSuiteLevelParameter(index, name, value==null?null:value.toString());
	}

	protected void ___cxt___print_provider__(String name)
	{
		System.out.println(getAllProviderData(getPn(name)));
	}

	protected void ___cxt___print_provider__json(String name)
	{
		try
		{
			System.out.println(WorkflowContextHandler.OM.writerWithDefaultPrettyPrinter().writeValueAsString(getAllProviderData(getPn(name))));
		}
		catch (Exception e)
		{
		}
	}

	protected AcceptanceTestContext get___cxt___()
	{
		return ___cxt___;
	}

	protected void set___cxt___(AcceptanceTestContext ___cxt___)
	{
		this.___cxt___ = ___cxt___;
	}

	public List<SeleniumTestSession> get__sessions__()
	{
		return sessions;
	}

	protected void set__provname__(String __provname__)
	{
		getSession().__provdetails__.put(getPn(__provname__), -1);
	}

	protected void set__provpos__(String __provname__, int __provpos__)
	{
		getSession().__provdetails__.put(getPn(__provname__), __provpos__);
	}

	protected void rem__provname__(String __provname__)
	{
		getSession().__provdetails__.remove(getPn(__provname__));
	}

	protected void set__subtestname__(String __subtestname__)
	{
		if(__subtestname__!=null) {
			getSession().__subtestexecutiontime__ = System.nanoTime();
			if(getSession().__provdetails__.size()>0) {
				String fstn = "";
				ArrayList<String> keys = new ArrayList<String>(getSession().__provdetails__.keySet());
				for (int i=keys.size()-1;i>=0;i--)
				{
					String pn = keys.get(i);
					Integer pp = getSession().__provdetails__.get(pn);
					fstn += pn + "(" + pp + ") ";
				}
				__subtestname__ = fstn + __subtestname__;
			}
		} else {
			getSession().__subtestexecutiontime__ = System.nanoTime() - getSession().__subtestexecutiontime__;
			if(getSession().__subtestname__!=null) {
				getSession().__result__.get(getSession().browserName).__cresult__.get(getSession().__subtestname__).executionTime = getSession().__subtestexecutiontime__;
			}
		}
		getSession().__subtestname__ = __subtestname__;
	}
	
	protected String get__subtestname__() {
		return getSession().__subtestname__;
	}
	
	protected void set__aliasname__(String __aliasname__)
	{
		getSession().__aliasname__ = __aliasname__;
	}
	
	protected String get__aliasname__() {
		return getSession().__aliasname__;
	}
	
	protected void set__loopcontext__(Map<String, Object> __loopcontext__)
	{
		getSession().__loopcontext__ = __loopcontext__;
		if(!get__loopcontext__var("n").equals("n") && get__loopcontext__var("ft").equals("t")) {
			rem__provname__(get__loopcontext__var("n")+"_rf");
			delProvider(get__loopcontext__var("n")+"_rf");
		}
	}
	
	protected String get__loopcontext__var(String n) {
		if(!getSession().__loopcontext__.containsKey(n)) {
			return n;
			//throw new RuntimeException("No Loop Context available");
		}
		return (String)getSession().__loopcontext__.get(n);
	}
	
	protected int get__loopcontext__indx() {
		if(!getSession().__loopcontext__.containsKey("i")) {
			return -1;
		}
		return Integer.valueOf((String)getSession().__loopcontext__.get("i"));
	}
	
	protected void set__loopcontext__(String name)
	{
		getSession().__loopcontext__ = new HashMap<>();
		getSession().__loopcontext__.put("n", name);
	}
	
	protected void add__loopcontext__arg(String n, Object v)
	{
		getSession().__loopcontext__.put(n, v);
	}
	
	protected Map<String, Object> get__loopcontext__() {
		return getSession().__loopcontext__;
	}

	protected WebDriver getWebDriver() {
		return getSession().___d___.get(getSession().__wpos__);
	}

	protected AndroidDriver getAndroidDriver() {
		if(getSession().___d___ instanceof AndroidDriver) {
			return (AndroidDriver)getSession().___d___;
		}
		return null;
	}

	protected IOSDriver getIOSDriver() {
		if(getSession().___d___ instanceof IOSDriver) {
			return (IOSDriver)getSession().___d___;
		}
		return null;
	}

	/*protected SelendroidDriver getSelendroidDriver() {
		if(getSession().___d___ instanceof SelendroidDriver) {
			return (SelendroidDriver)getSession().___d___;
		}
		return null;
	}*/

	public SeleniumTest(String name, AcceptanceTestContext ___cxt___, int index) {
		this.___cxt___ = ___cxt___;
		try {
			File basePath = new File(___cxt___.getGatfExecutorConfig().getTestCasesBasePath());
		    File testPath = new File(basePath, ___cxt___.getGatfExecutorConfig().getTestCaseDir());
		    this.name = name.replace(testPath.getAbsolutePath(), "").substring(1);
		} catch (Exception e) {
			this.name = name;
		}
		this.index = index;
		//this.properties = ___cxt___.getGatfExecutorConfig().getSelDriverConfigMap().get(name).getProperties();
	}

	public String getName() {
		return name;
	}

	public static interface Functor<I, O> {
		O f(I i, I j);
	}

	public static interface CondFunc {
		Integer f(Object[] args);
	}
	
	public void quit(SeleniumTestSession sess) {
		if(sess.___d___.size()>0) {
			int indx = 0;
			for (WebDriver d : sess.___d___)
			{
				if(sess.___dqs___.get(indx)) continue;
				String sessionId = null;
				try {
					sessionId = ((RemoteWebDriver)d).getSessionId().toString();
					DRV_FEATURES.remove(sessionId);
				} catch (Exception e) {
				}
				try {
					if((sessionId!=null && !wdmSessions.containsKey(sessionId)) || sessionId==null) d.quit();
				} catch (Exception e) {
					if(e.getCause()!=null) {
						/*if(e.getCause() instanceof TimeoutException) {
							try {
								d.quit();
							} catch (Exception e2) {
								e2.getCause().printStackTrace();
							}
						} else {*/
							e.getCause().printStackTrace();
						//}
					}
				}
				sess.___dqs___.set(indx, true);
			}
		}
	}

	public void quit() {
		quit(getSession());
	}

	public void quitAll() {
		for (SeleniumTestSession s : sessions) {
			quit(s);
		}
		for(WebDriverManager wdm: wdmMgs.values()) {
			wdm.quit();
		}
		IN_DOCKER.remove();
	}

	public abstract void close();

	public abstract SeleniumTest copy(AcceptanceTestContext ctx, int index);

	public abstract List<SeleniumTestSession> execute(LoggingPreferences ___lp___) throws Exception;

	/*protected boolean checkifSessionIdExistsInSet(Set<String> st, String sname, String sid) {
        if(sname==null && sid==null)return true;
        if(sname!=null && st.contains(sname))return true;
        if(sid!=null && st.contains(sid))return true;
        return false;
    }*/

	public static class SeleniumTestResult implements Serializable {
		private static final long serialVersionUID = 1L;

		private Map<String, SerializableLogEntries> logs = new HashMap<String, SerializableLogEntries>();

		private boolean status;

		private boolean isContinue;

		long executionTime;

		String stName;
		
		String stImg;
		
		int line;
		
		transient Throwable cause;

		private Map<String, Object[]> internalTestRes = new HashMap<String, Object[]>();

		public Throwable getCause() {
			return cause;
		}
		public Map<String, SerializableLogEntries> getLogs()
		{
			return logs;
		}
		public boolean isStatus()
		{
			return status;
		}
		public long getExecutionTime()
		{
			return executionTime;
		}
		public String getSubtestName()
		{
			return stName;
		}
		public String getSubtestImg()
		{
			return stImg;
		}
		public Map<String,Object[]> getInternalTestRes()
		{
			return internalTestRes;
		}
		public SeleniumTestResult(WebDriver d, SeleniumTest test, LoggingPreferences ___lp___)
		{
			this.stName = test.get__subtestname__();
			this.status = true;
			this.internalTestRes = test.getSession().internalTestRs;
			this.line = test.line;
		}
		public SeleniumTestResult(WebDriver d, SeleniumTest test, Throwable cause, String img, LoggingPreferences ___lp___) {
			this.cause = cause;
			this.status = false;
			this.isContinue = false;
			this.line = test.line;
			this.internalTestRes = test.getSession().internalTestRs;
			/*Logs logs = d.manage().logs();
            for (String s : LOG_TYPES_SET) {
                if(!logs.getAvailableLogTypes().contains(s))continue;
                LogEntries logEntries = logs.get(s);
                if(logEntries!=null && !logEntries.getAll().isEmpty()) {
                    this.logs.put(s, new SerializableLogEntries(logEntries.getAll())); 
                }
            }*/
			if(cause instanceof GatfRunTimeError) {
				((GatfRunTimeError)cause).details = new Object[] {"", test.line, test.name};
			}
			if(cause instanceof PassSubTestException) {
				this.status = true;
				this.isContinue = true;
				this.stName = test.get__subtestname__();
				List<LogEntry> entries = new ArrayList<LogEntry>();
				entries.add(new LogEntry(Level.ALL, new Date().getTime(), cause.getMessage()));
				this.logs.put("gatf", new SerializableLogEntries(entries));
				System.out.println(cause.getMessage());
			} else if(cause instanceof FailSubTestException || cause instanceof WarnSubTestException) {
				this.isContinue = cause instanceof WarnSubTestException;
				this.stName = test.get__subtestname__();
				List<LogEntry> entries = new ArrayList<LogEntry>();
				entries.add(new LogEntry(Level.ALL, new Date().getTime(), cause.getMessage()));
				entries.add(new LogEntry(Level.ALL, new Date().getTime(), ExceptionUtils.getStackTrace(cause)));
				this.logs.put("gatf", new SerializableLogEntries(entries));
				if(cause instanceof FailSubTestException) {
					try {
						((FailSubTestException)cause).stName = this.stName;
						stImg = img;
						((FailSubTestException)cause).img = img;
						System.out.println("Error occurred on line no " + line);
						cause.printStackTrace();
						java.lang.System.out.println(img);
						screenshotAsFile(d, img);
					} catch (Exception e) {
					}
				} else {
					System.out.println(cause.getMessage());
				}
			} else if(cause instanceof SubTestException) {
				this.stName = ((SubTestException)cause).stName;
				this.isContinue = false;
				List<LogEntry> entries = new ArrayList<LogEntry>();
				entries.add(new LogEntry(Level.ALL, new Date().getTime(), ((SubTestException)cause).cause.getMessage()));
				entries.add(new LogEntry(Level.ALL, new Date().getTime(), ExceptionUtils.getStackTrace(((SubTestException)cause).cause)));
				this.logs.put("gatf", new SerializableLogEntries(entries));
				try {
					stImg = img;
					((SubTestException)cause).img = img;
					System.out.println("Error occurred on line no " + line);
					cause.printStackTrace();
					java.lang.System.out.println(img);
					screenshotAsFile(d, img);
				} catch (Exception e) {
				}
			} else {
				this.stName = test.get__subtestname__();
				List<LogEntry> entries = new ArrayList<LogEntry>();
				if(cause instanceof RuntimeException) {
					if(cause.getCause()!=null) {
						if(cause.getCause() instanceof FailSubTestException) {
							stImg = ((FailSubTestException)cause.getCause()).img;
							entries.add(new LogEntry(Level.ALL, new Date().getTime(), "Subtest " + ((FailSubTestException)cause.getCause()).stName + " Failed"));
						} else if(cause.getCause() instanceof SubTestException) {
							stImg = ((SubTestException)cause.getCause()).img;
							entries.add(new LogEntry(Level.ALL, new Date().getTime(), "Subtest " + ((SubTestException)cause.getCause()).stName + " Failed"));
						} else {
							entries.add(new LogEntry(Level.ALL, new Date().getTime(), cause.getMessage()));
							entries.add(new LogEntry(Level.ALL, new Date().getTime(), ExceptionUtils.getStackTrace(cause)));
						}
					} else {
						entries.add(new LogEntry(Level.ALL, new Date().getTime(), cause.getMessage()));
						entries.add(new LogEntry(Level.ALL, new Date().getTime(), ExceptionUtils.getStackTrace(cause)));
					}
				} else {
					entries.add(new LogEntry(Level.ALL, new Date().getTime(), cause.getMessage()));
					entries.add(new LogEntry(Level.ALL, new Date().getTime(), ExceptionUtils.getStackTrace(cause)));
				}
				if(stImg==null) {
					try {
						stImg = img;
						System.out.println("Error occurred on line no " + line);
						cause.printStackTrace();
						java.lang.System.out.println(img);
						screenshotAsFile(d, img);
					} catch (Exception e) {
					}
				}
				this.logs.put("gatf", new SerializableLogEntries(entries));
			}
		}
		public SeleniumTestResult(SeleniumTest test, Throwable cause) {
			this.cause = cause;
			this.status = false;
			this.stName = test.get__subtestname__();
			this.line = test.line;
			this.internalTestRes = test.getSession().internalTestRs;
			if(cause instanceof GatfRunTimeError) {
				((GatfRunTimeError)cause).details = new Object[] {"", test.line, test.name};
			}
			System.out.println("Error occurred on line no " + line);
			List<LogEntry> entries = new ArrayList<LogEntry>();
			entries.add(new LogEntry(Level.ALL, new Date().getTime(), cause.getMessage()));
			entries.add(new LogEntry(Level.ALL, new Date().getTime(), ExceptionUtils.getStackTrace(cause)));
			this.logs.put("gatf", new SerializableLogEntries(entries));
		}
	}
	
	protected void sendKeys(WebDriver driver, WebElement le, String type, String qualifier, String tvalue) {
		sendKeys(type, qualifier, driver, le, tvalue, ___cxt___);
	}
	
	protected static String randomize(String tvalue) {
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
					finalNum += RandomStringUtils.randomNumeric(Integer.valueOf(prt)) + sep;
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
					finalNum += RandomStringUtils.randomAlphabetic(Integer.valueOf(prt)) + sep;
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
					finalNum += RandomStringUtils.randomAlphabetic(Integer.valueOf(prt)).toUpperCase() + sep;
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
					finalNum += RandomStringUtils.randomAlphabetic(Integer.valueOf(prt)).toLowerCase() + sep;
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
					finalNum += RandomStringUtils.randomAlphanumeric(Integer.valueOf(prt)) + sep;
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
					finalNum += RandomStringUtils.randomAlphanumeric(Integer.valueOf(prt)).toUpperCase() + sep;
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
					finalNum += RandomStringUtils.randomAlphanumeric(Integer.valueOf(prt)).toLowerCase() + sep;
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
				vals.add(RandomStringUtils.randomAlphabetic(count));
			} else if(v1.toLowerCase().equals("alphanumeric")) {
				vals.add(RandomStringUtils.randomAlphanumeric(count));
			} else if(v1.toLowerCase().equals("alphanumericuc")) {
				vals.add(RandomStringUtils.randomAlphanumeric(count).toUpperCase());
			} else if(v1.toLowerCase().equals("alphanumericlc")) {
				vals.add(RandomStringUtils.randomAlphanumeric(count).toLowerCase());
			} else if(v1.toLowerCase().equals("alphauc")) {
				vals.add(RandomStringUtils.randomAlphabetic(count).toUpperCase());
			} else if(v1.toLowerCase().equals("alphalc")) {
				vals.add(RandomStringUtils.randomAlphabetic(count).toLowerCase());
			} else if(v1.toLowerCase().equals("numeric")) {
				String fv = RandomStringUtils.randomNumeric(count);
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

	protected static void sendKeys(String type, String qualifier, WebDriver wd, WebElement le, String tvalue, AcceptanceTestContext ___cxt___) {
		if(type.equalsIgnoreCase("randomize")) {
			if ((le.getTagName().toLowerCase().matches("input") /*&& le.get(0).getAttribute("type").toLowerCase().matches("text|url|email|hidden")*/)
					|| (le.getTagName().toLowerCase().matches("textarea"))) {
				String randomVal = randomize(tvalue);
				le.sendKeys(randomVal);
				jsEvent(wd, le, qualifier);
			} /*else if (le.get(0).getTagName().toLowerCase().matches("input") && le.get(0).getAttribute("type").toLowerCase().matches("number")) {
				long min = 0;
				long max = 99999;
				if(StringUtils.isNotBlank(v1)) {
					try {
						min = Long.parseLong(v1);
					} catch (Exception e) {
					}
				}
				if(StringUtils.isNotBlank(v2)) {
					try {
						max = Long.parseLong(v2);
					} catch (Exception e) {
					}
				}
				long num = (long)(min + (Math.random() * (max - min)));
				le.get(0).sendKeys(num+"");
			}*/ else if (le.getTagName().toLowerCase().matches("select")) {
				randomizeSelect(le);
			} else if (le.getTagName().toLowerCase().matches("input") && le.getAttribute("type").toLowerCase().matches("checkbox")) {
				le.click();
			} else if (le.getTagName().toLowerCase().matches("input") && le.getAttribute("type").toLowerCase().matches("radio")) {
				le.click();
			}
		} else {
			if(tvalue.startsWith("file://")) {
				tvalue = resolveFile(tvalue.substring(7), ___cxt___);
			}
			le.sendKeys(tvalue);
			jsEvent(wd, le, qualifier);
		}
	}

	protected void window(int pos) {
		if(pos>=0 && pos<getSession().___d___.size()) {
			getSession().__wpos__ = pos;
		} else {
			throw new RuntimeException("Invalid window number specified");
		}
	}

	protected void newWindow(LoggingPreferences lp) {
		try
		{
			Method m = getClass().getMethod("setupDriver"+getSession().browserName, new Class[]{LoggingPreferences.class});
			if(m!=null) {
				m.invoke(this, new Object[]{lp});
			}
			getSession().__wpos__++;
		}
		catch (Exception e)
		{
			throw new RuntimeException("Invalid browser name specified");
		}
	}
	
	
	//THE BELOW Code excerpt has been copy pasted shamelessly from https://www.headspin.io/blog/generating-touch-gestures-to-zoom-in-and-out-on-google-maps
	//START ZOOM IN/OUT Code
	/*
    locus: the center of the touch gesture, the point that fingers are pinching away from or towards. They won't actually touch this point though
    startRadius: distance from center that fingers begin at
    endRadius: distance from center that fingers end at
    pinchAngle: at what angle the fingers pinch around the locus, in degrees. 0 for vertical pinch, 90 for horizontal pinch
    duration: the total amount of time the pinch gesture will take
	*/
	private Collection<Sequence> zoom(Point locus, int startRadius, int endRadius, int pinchAngle, Duration duration) {
	    // convert degree angle into radians. 0/360 is top (12 O'clock).
	    double angle = Math.PI / 2 - (2 * Math.PI / 360 * pinchAngle);
	
	    // create the gesture for one finger
	    Sequence fingerAPath = zoomSinglefinger("fingerA", locus, startRadius, endRadius, angle, duration);
	
	    // flip the angle around to the other side of the locus and get the gesture for the second finger
	    angle = angle + Math.PI;
	    Sequence fingerBPath = zoomSinglefinger("fingerB", locus, startRadius, endRadius, angle, duration);
	
	    return Arrays.asList(fingerAPath, fingerBPath);
	}
	
	/*
	    Used by the `zoom` method, for creating one half of a zooming pinch gesture.
	    This will return the tough gesture for a single finger, to be put together with
	    another finger action to complete the gesture.
	    fingerName: name of this input finger for the gesture. Used by automation system to tell inputs apart
	    locus: the center of the touch gesture, the point that fingers are pinching away from or towards. They won't actually touch this point though
	    startRadius: distance from center that fingers begin at
	    endRadius: distance from center that fingers end at
	    angle: at what angle the fingers pinch around the locus, in radians.
	    duration: the total amount of time the pinch gesture will take
	 */
	private Sequence zoomSinglefinger(String fingerName, Point locus, int startRadius, int endRadius, double angle, Duration duration) {
	    PointerInput finger = new PointerInput(PointerInput.Kind.TOUCH, fingerName);
	    Sequence fingerPath = new Sequence(finger, 0);
	
	    double midpointRadius = startRadius + (endRadius > startRadius ? 1 : -1) * 20;
	
	    // find coordinates for starting point of action (converting from polar coordinates to cartesian)
	    int fingerStartx = (int)Math.floor(locus.x + startRadius * Math.cos(angle));
	    int fingerStarty = (int)Math.floor(locus.y - startRadius * Math.sin(angle));
	
	    // find coordinates for first point that pingers move quickly to
	    int fingerMidx = (int)Math.floor(locus.x + (midpointRadius * Math.cos(angle)));
	    int fingerMidy = (int)Math.floor(locus.y - (midpointRadius * Math.sin(angle)));
	
	    // find coordinates for ending point of action (converting from polar coordinates to cartesian)
	    int fingerEndx = (int)Math.floor(locus.x + endRadius * Math.cos(angle));
	    int fingerEndy = (int)Math.floor(locus.y - endRadius * Math.sin(angle));
	
	    // move finger into start position
	    fingerPath.addAction(finger.createPointerMove(Duration.ZERO, PointerInput.Origin.viewport(), fingerStartx, fingerStarty));
	    // finger comes down into contact with screen
	    fingerPath.addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()));
	    // finger moves a small amount very quickly
	    fingerPath.addAction(finger.createPointerMove(Duration.ofMillis(1), PointerInput.Origin.viewport(), fingerMidx, fingerMidy));
	    // pause for a little bit
	    fingerPath.addAction(new Pause(finger, Duration.ofMillis(100)));
	    // finger moves to end position
	    fingerPath.addAction(finger.createPointerMove(duration, PointerInput.Origin.viewport(), fingerEndx, fingerEndy));
	    // finger lets up, off the screen
	    fingerPath.addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()));
	
	    return fingerPath;
	}
	
	/*
	Simplified method for zooming in.
	Defaults to a 45 degree angle for the pinch gesture.
	Defaults to a duration of half a second
	Fingers start 50px from locus
	
	locus: the center of the pinch action, fingers move away from here
	distance: how far fingers move outwards, starting 100px from the locus
	 */
	private Collection<Sequence> zoomIn(Point locus, int distance) {
	    return zoom(locus, 200, 200 + distance, 45, Duration.ofMillis(25));
	}
	
	/*
	Simplified method for zooming out.
	Defaults to a 45 degree angle for the pinch gesture.
	Defaults to a duration of half a second
	Fingers finish 50px from locus
	
	locus: the center of the pinch action, fingers move towards here
	distance: how far fingers move inwards, they will end 100px from the locus
	 */
	private Collection<Sequence> zoomOut(Point locus, int distance) {
	    return zoom(locus, 200 + distance, 200, 45, Duration.ofMillis(25));
	}
	//END ZOOM IN/OUT Code

	protected void mzoompinch(WebDriver driver, Object ele, int x, int y, boolean isZoom) {
		try
		{
			if(ele==null) {
				/*int leftX = x;
				int rightX = get___d___().manage().window().getSize().getWidth() + leftX;
				int midX = (leftX + rightX) / 2;

				int upperY = y;
				int lowerY = get___d___().manage().window().getSize().getHeight() + upperY;
				int midY = (upperY + lowerY) / 2;

				int aX = (int) (midX * 0.3);
				int aY = (int) (midY * 1.7);
				int bX = (int) (midX * 0.8);
				int bY = (int) (midY * 1.2);

				int cX = (int) (midX * 1.7);
				int cY = (int) (midY * 0.3);
				int dX = (int) (midX * 1.2);
				int dY = (int) (midY * 0.8);

				TouchAction action0 = new TouchAction((MobileDriver)get___d___());
				TouchAction action1 = new TouchAction((MobileDriver)get___d___());*/

				if(isZoom) {
			        ((AppiumDriver)driver).perform(zoomIn(new Point(x, y), 450));
					//action0.press(bX, bY).waitAction(java.time.Duration.ofMillis(1000)).moveTo(aX, aY).release();
					//action1.press(dX, dY).waitAction(java.time.Duration.ofMillis(1000)).moveTo(cX, cY).release();
				} else {
					((AppiumDriver)driver).perform(zoomOut(new Point(x, y), 450));
					//action0.press(aX, aY).waitAction(java.time.Duration.ofMillis(1000)).moveTo(bX, bY).release();
					//action1.press(cX, cY).waitAction(java.time.Duration.ofMillis(1000)).moveTo(dX, dY).release();
				}

				//MultiTouchAction mAction = new MultiTouchAction((MobileDriver)get___d___());
				//mAction.add(action0).add(action1).perform();
			} else if(ele instanceof WebElement) {
				/*int leftX = ((WebElement)ele).getLocation().getX();
				int rightX = ((WebElement)ele).getSize().getWidth() + leftX;
				int midX = (leftX + rightX) / 2;

				int upperY = ((WebElement)ele).getLocation().getY();
				int lowerY = ((WebElement)ele).getSize().getHeight() + upperY;
				int midY = (upperY + lowerY) / 2;

				int aX = (int) (midX * 0.3);
				int aY = (int) (midY * 1.7);
				int bX = (int) (midX * 0.8);
				int bY = (int) (midY * 1.2);

				int cX = (int) (midX * 1.7);
				int cY = (int) (midY * 0.3);
				int dX = (int) (midX * 1.2);
				int dY = (int) (midY * 0.8);

				TouchAction action0 = new TouchAction((MobileDriver)get___d___());
				TouchAction action1 = new TouchAction((MobileDriver)get___d___());*/
				
				Rectangle rect = ((WebElement)ele).getRect();
				Point from = new Point(rect.x + rect.getWidth() / 2, rect.y + rect.getHeight() / 2);

				if(isZoom) {
					((AppiumDriver)driver).perform(zoomIn(from, 450));
					//action0.press(bX, bY).waitAction(java.time.Duration.ofMillis(1000)).moveTo(aX, aY).release();
					//action1.press(dX, dY).waitAction(java.time.Duration.ofMillis(1000)).moveTo(cX, cY).release();
				} else {
					((AppiumDriver)driver).perform(zoomOut(from, 450));
					//action0.press(aX, aY).waitAction(java.time.Duration.ofMillis(1000)).moveTo(bX, bY).release();
					//action1.press(cX, cY).waitAction(java.time.Duration.ofMillis(1000)).moveTo(dX, dY).release();
				}

				//MultiTouchAction mAction = new MultiTouchAction((MobileDriver)get___d___());
				//mAction.add(action0).add(action1).perform();
			}
		}
		catch (Exception e)
		{
		}
	}
	
	protected void doSwipe(WebDriver driver, Point start, Point end, int duration) {
		PointerInput finger = new PointerInput(PointerInput.Kind.TOUCH, "fingerA");
        Sequence swipe = new Sequence(finger, 1)
                .addAction(finger.createPointerMove(Duration.ZERO, PointerInput.Origin.viewport(), start.getX(), start.getY()))
                .addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()))
                .addAction(finger.createPointerMove(Duration.ofMillis(duration), PointerInput.Origin.viewport(), end.getX(), end.getY()))
                .addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()));
        ((AppiumDriver)driver).perform(Arrays.asList(swipe));
    }

	protected void doTap(WebDriver driver, WebElement e, Point point, int duration) {
		if(e!=null) {
			point = getCenter(e);
		}
		PointerInput finger = new PointerInput(PointerInput.Kind.TOUCH, "fingerA");
        Sequence tap = new Sequence(finger, 1)
                .addAction(finger.createPointerMove(Duration.ZERO, PointerInput.Origin.viewport(), point.getX(), point.getY()))
                .addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()))
                .addAction(new Pause(finger, Duration.ofMillis(duration)))
                .addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()));
        ((AppiumDriver)driver).perform(Arrays.asList(tap));
    }

	protected static void randomizeSelect(WebElement le) {
		try
		{
			Select s = new Select(le);
			if(s.getOptions().size()>0) {
				List<Integer> li = new ArrayList<Integer>();
				for (WebElement o : s.getOptions())
				{
					li.add(Integer.parseInt(o.getAttribute("index")));
				}
				for (WebElement o : s.getAllSelectedOptions())
				{
					li.remove(Integer.parseInt(o.getAttribute("index")));
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
					int i = Integer.parseInt(s.getFirstSelectedOption().getAttribute("index"));
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

	protected Object pluginize(String name, String signature, List<String> args, List<List<String>> args1) {
		try
		{
			String[] parts = signature.split("@");
			String clsname = parts[0].trim();
			String method = parts[1].trim();

			Class<?> cls = Class.forName(clsname);
			Method meth = null, meth1 = null;
			boolean twoargs = true;

			try {
				meth = cls.getMethod(method, new Class[]{String.class, Object[].class});
			} catch (Exception e) {
			}

			if(meth==null) {
				meth = cls.getMethod(method, new Class[]{Object[].class});
				twoargs = false;
			}

			try {
				Method meth2 = cls.getMethod("isValidParams", new Class[]{List.class, List.class});
				Boolean isValid = (Boolean)meth2.invoke(null, new Object[]{args, args1});
				if(isValid!=null && !isValid) {
					throw new RuntimeException("Problem executing plugin, invalid parameters provided " + name);
				}
			} catch (Exception e) {
			}

			Object[] nargs =  new Object[args.size()+3];
			int c = 0;
			int indx = -1;
			for (String arg : args) {
				indx++;

				boolean parameterize = true;
				try {
					meth1 = cls.getMethod("isParameterizeFirstSetParam", new Class[]{String.class, int.class});
					Boolean temp = (Boolean)meth1.invoke(null, new Object[]{arg, indx});
					if(temp!=null && !temp) {
						parameterize = temp;
					}
				} catch (Exception e) {
				}

				if(!parameterize) {
					nargs[c++] = arg.trim();
					continue;
				}

				if(arg.trim().startsWith("${") && arg.trim().endsWith("}")) {
					String vn = arg.trim().substring(2, arg.length()-1);
					nargs[c++] = evaluate(vn);
				} else if(arg.trim().startsWith("$v{") && arg.trim().endsWith("}")) {
					String vn = arg.trim().substring(3, arg.length()-1);
					nargs[c++] = ___get_var__(vn);
				} else if((arg.toLowerCase().trim().startsWith("file://") || arg.toLowerCase().trim().startsWith("http://") 
						|| arg.toLowerCase().trim().startsWith("https://"))) {
					if(arg.toLowerCase().trim().startsWith("file://")) {
						File _f = ___cxt___.getResourceFile(arg.trim().substring(7));
						arg = _f.toURI().toURL().toString();
					}
					nargs[c++] = Resources.toString(new URL(arg), Charset.forName("UTF-8"));
				} else if(arg.toLowerCase().trim().startsWith("$file://") || arg.toLowerCase().trim().startsWith("$http://") 
						|| arg.toLowerCase().trim().startsWith("$https://")) {
					if(arg.toLowerCase().trim().startsWith("file://")) {
						File _f = ___cxt___.getResourceFile(arg.trim().substring(7));
						arg = _f.toURI().toURL().toString();
					}
					String tmpl = Resources.toString(new URL(arg), Charset.forName("UTF-8"));
					tmpl = evaluate(tmpl);
					nargs[c++] = tmpl;
				} else {
					nargs[c++] = arg;
				}
			}

			List<List<Object>> nargs1 = new ArrayList<List<Object>>();
			if(args1!=null) {
				int indx1 = 0;
				int indx2 = -1;
				for (List<String> narg : args1) {
					List<Object> nargs2 = new ArrayList<Object>();
					for (String arg : narg) {
						indx2++;

						boolean parameterize = true;
						try {
							meth1 = cls.getMethod("isParameterizeSecondSetParam", new Class[]{String.class, int.class, int.class});
							Boolean temp = (Boolean)meth1.invoke(null, new Object[]{arg, indx1, indx2});
							if(temp!=null && !temp) {
								parameterize = temp;
							}
						} catch (Exception e) {
						}

						if(!parameterize) {
							nargs2.add(arg.trim());
							continue;
						}

						if(arg.trim().startsWith("${") && arg.trim().endsWith("}")) {
							String vn = arg.trim().substring(2, arg.length()-1);
							nargs2.add(evaluate(vn));
						} else if(arg.trim().startsWith("$v{") && arg.trim().endsWith("}")) {
							String vn = arg.trim().substring(3, arg.length()-1);
							nargs2.add(___get_var__(vn));
						} else if(arg.toLowerCase().trim().startsWith("file://") || arg.toLowerCase().trim().startsWith("http://") 
								|| arg.toLowerCase().trim().startsWith("https://")) {
							if(arg.toLowerCase().trim().startsWith("file://")) {
								File _f = ___cxt___.getResourceFile(arg.trim().substring(7));
								arg = _f.toURI().toURL().toString();
							}
							nargs2.add(Resources.toString(new URL(arg), Charset.forName("UTF-8")));
						} else if(arg.toLowerCase().trim().startsWith("$file://") || arg.toLowerCase().trim().startsWith("$http://") 
								|| arg.toLowerCase().trim().startsWith("$https://")) {
							if(arg.toLowerCase().trim().startsWith("file://")) {
								File _f = ___cxt___.getResourceFile(arg.trim().substring(7));
								arg = _f.toURI().toURL().toString();
							}
							String tmpl = Resources.toString(new URL(arg), Charset.forName("UTF-8"));
							tmpl = evaluate(tmpl);
							nargs2.add(tmpl);
						} else {
							nargs2.add(arg);
						}

						indx1++;
					}
					nargs1.add(nargs2);
				}

				indx1++;
			}

			nargs[c++] = nargs1;
			nargs[c++] = index==-1?0:index;
			nargs[c++] = ___cxt___;

			return meth.invoke(null, twoargs?new Object[]{name, nargs}:new Object[]{nargs});
		}
		catch (Exception e)
		{
			throw new RuntimeException("Problem executing plugin " + name, e);
		}
	}

	public class SelFunc<T, R> implements Function<T, R> {
		Object[] ____wi = new Object[2];

		@Override
		public R apply(T t) {
			return null;
		}
	}
	
	@SuppressWarnings("unchecked")
	protected static List<WebElement> ByJquerySelector(WebDriver d, String finder) {
		if (d instanceof JavascriptExecutor) {
		    return (List<WebElement>)((JavascriptExecutor)d).executeScript("return $(arguments[0]).get()", finder);
		} else {
		    throw new IllegalStateException("This driver cannot run JavaScript.");
		}
	}
	
	protected static List<WebElement> getElements(WebDriver d, SearchContext sc, String finder, List<WebElement> ce) {
		return getElements(d, sc, finder, null, null, ce);
	}

	@SuppressWarnings({ "serial", "unchecked" })
	protected static List<WebElement> getElements(WebDriver d, SearchContext sc, String finder, String finder1, String relative, List<WebElement> ce) {
		finder = finder.trim();
		String by = finder.indexOf("@")!=-1?finder.substring(0, finder.indexOf("@")).trim():finder;
		if(by.charAt(0)==by.charAt(by.length()-1)) {
			if(by.charAt(0)=='"' || by.charAt(0)=='\'') {
				by = by.substring(1, by.length()-1);
			}
		}
		
		String classifier = finder.indexOf("@")!=-1?finder.substring(finder.indexOf("@")+1).trim():"";
		if(!classifier.isEmpty() && classifier.charAt(0)==classifier.charAt(classifier.length()-1)) {
			if(classifier.charAt(0)=='"' || classifier.charAt(0)=='\'') {
				classifier = classifier.substring(1, classifier.length()-1);
			}
		}
		
		if(by.equalsIgnoreCase("this") || by.equalsIgnoreCase("current")) {
			return ce;
		} else if(by.equalsIgnoreCase("active")) {
			return new ArrayList<WebElement>(){{add(d.switchTo().activeElement());}};
		} else if(classifier.trim().isEmpty()) {
			throw new RuntimeException("Invalid element selector specified " + finder);
		}
		
		List< WebElement> el = null;
		if(by.equalsIgnoreCase("id")) {
			el = By.id(classifier).findElements(sc);
		} else if(by.equalsIgnoreCase("name")) {
			el = By.name(classifier).findElements(sc);
		} else if(by.equalsIgnoreCase("class") || by.equalsIgnoreCase("className")) {
			el = By.className(classifier).findElements(sc);
		} else if(by.equalsIgnoreCase("tag") || by.equalsIgnoreCase("tagname")) {
			el = By.tagName(classifier).findElements(sc);
		} else if(by.equalsIgnoreCase("xpath")) {
			el = By.xpath(classifier).findElements(sc);
			if(el==null || el.size()==0) {
				Object ctxt = null;
				if(sc instanceof WebElement) {
					ctxt = sc;
				}
				if (d instanceof JavascriptExecutor) {
					el = (List<WebElement>)((JavascriptExecutor)d).executeScript("return window.GatfUtil.findByXpath(arguments[0], arguments[1])", classifier, ctxt);
				}
			}
		} else if(by.equalsIgnoreCase("cssselector") || by.equalsIgnoreCase("css")) {
			el = By.cssSelector(classifier).findElements(sc);
		} else if(by.equalsIgnoreCase("text")) {
			el = By.xpath("//*[contains(text(), '" + classifier+"')]").findElements(sc);
		} else if(by.equalsIgnoreCase("linkText")) {
			el = By.linkText(classifier).findElements(sc);
		} else if(by.equalsIgnoreCase("partialLinkText")) {
			el = By.partialLinkText(classifier).findElements(sc);
		} else if(by.equalsIgnoreCase("jq") || by.equalsIgnoreCase("jquery") || by.equalsIgnoreCase("$")) {
			el = ByJquerySelector(d, classifier);
		}
		
		if(el!=null && el.size()>0 && relative!=null && finder1!=null) {
			switch (relative) {
				case "leftof":
					el = RelativeLocator.with(getBy(d, finder1)).toLeftOf(el.get(0)).findElements(sc);
					break;
				case "rightof":
					el = RelativeLocator.with(getBy(d, finder1)).toRightOf(el.get(0)).findElements(sc);
					break;
				case "above":
					el = RelativeLocator.with(getBy(d, finder1)).above(el.get(0)).findElements(sc);
					break;
				case "below":
					el = RelativeLocator.with(getBy(d, finder1)).below(el.get(0)).findElements(sc);
					break;
				case "near":
					el = RelativeLocator.with(getBy(d, finder1)).near(el.get(0)).findElements(sc);
					break;
				default:
					break;
			}
		}
		return el;
	}
	
	private static By getBy(WebDriver d, String finder) {
		finder = finder.trim();
		String by = finder.substring(0, finder.indexOf("@")).trim();
		if(by.charAt(0)==by.charAt(by.length()-1)) {
			if(by.charAt(0)=='"' || by.charAt(0)=='\'') {
				by = by.substring(1, by.length()-1);
			}
		}
		String classifier = finder.substring(finder.indexOf("@")+1).trim();
		if(classifier.charAt(0)==classifier.charAt(classifier.length()-1)) {
			if(classifier.charAt(0)=='"' || classifier.charAt(0)=='\'') {
				classifier = classifier.substring(1, classifier.length()-1);
			}
		}
		By el = null;
		if(by.equalsIgnoreCase("id")) {
			el = By.id(classifier);
		} else if(by.equalsIgnoreCase("name")) {
			el = By.name(classifier);
		} else if(by.equalsIgnoreCase("class") || by.equalsIgnoreCase("className")) {
			el = By.className(classifier);
		} else if(by.equalsIgnoreCase("tag") || by.equalsIgnoreCase("tagname")) {
			el = By.tagName(classifier);
		} else if(by.equalsIgnoreCase("xpath")) {
			el = By.xpath(classifier);
		} else if(by.equalsIgnoreCase("cssselector") || by.equalsIgnoreCase("css")) {
			el = By.cssSelector(classifier);
		} else if(by.equalsIgnoreCase("text")) {
			el = By.xpath("//*[contains(text(), '" + classifier+"')]");
		} else if(by.equalsIgnoreCase("linkText")) {
			el = By.linkText(classifier);
		} else if(by.equalsIgnoreCase("partialLinkText")) {
			el = By.partialLinkText(classifier);
		}
		return el;
	}
	
	protected void printToPdf(WebDriver pdr, String filePath, boolean extractText) {
		if(pdr instanceof PrintsPage) {
			try {
				PrintOptions po = new PrintOptions();
				Pdf pdf = ((PrintsPage)pdr).print(po);
				IOUtils.write(Base64.getDecoder().decode(pdf.getContent()), new FileOutputStream(filePath));
				if(extractText && new File(filePath).exists()) {
					extractTextFromPdf(filePath);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	private void extractTextFromPdf(String filePath) {
		try {
			PDDocument doc = PDDocument.load(new File(filePath));
			System.out.println(doc.getNumberOfPages());
			System.out.println(doc.isEncrypted());
			PDFTextStripper stripper = new PDFTextStripper();
			stripper.setStartPage(1);
			//stripper.setEndPage(1);
			Writer wr = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filePath+".txt")));
			stripper.writeText(doc, wr);
			if (doc != null) {
				doc.close();
			}
			wr.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	protected void windowOpenSaveJsPre(WebDriver driver) {
		if (!(driver instanceof JavascriptExecutor)) {
		    throw new IllegalStateException("This driver cannot run JavaScript.");
		}
		try {
			((JavascriptExecutor)driver).executeScript("window.__wosjp__=[];window.__owo__=window.open;window.open=function(a,b,c){window.__wosjp__=[a,b,c];window.__owo__(a,b,c);window.open=__owo__;console.log(a);}");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	protected void windowOpenSaveJsPost(WebDriver driver, String filePath, boolean extractText) {
		if (!(driver instanceof JavascriptExecutor)) {
		    throw new IllegalStateException("This driver cannot run JavaScript.");
		}
		OkHttpClient client = null;
		try {
			String url = (String)((JavascriptExecutor)driver).executeScript("return window.__wosjp__[0]");
			int counter = 0;
			while(StringUtils.isBlank(url) && counter++<60) {
				System.out.println("Waiting for download URL... attempt " + counter);
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
				}
			}
			if(StringUtils.isBlank(url)) {
				throw new RuntimeException("Invalid window.open url found");
			}
			client = TestCaseExecutorUtil.getClient();
			Call call = client.newCall(new Request.Builder().url(url).build());
			Response res = call.execute();
			IOUtils.copy(res.body().byteStream(), new FileOutputStream(filePath));
			res.close();
			if(extractText && new File(filePath).exists()) {
				extractTextFromPdf(filePath);
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if(client!=null) {
				client.connectionPool().evictAll();
			}
		}
	}
	
	private static String resolveFile(String filePath, AcceptanceTestContext ___cxt___) {
		if(!new File(filePath).exists()) {
			File upfl = ___cxt___.getResourceFile(filePath);
			if(!upfl.exists()) {
				upfl = new File(System.getProperty("user.dir"), filePath);
				if(!upfl.exists()) {
					throw new RuntimeException("File not found at the given path, please provide a valid file [" + filePath + "]");
				} else {
					filePath = upfl.getAbsolutePath();
				}
			} else {
				filePath = upfl.getAbsolutePath();
			}
		}
		return filePath;
	}

	protected void uploadFile(WebDriver wd, List<WebElement> ret, String filePath, int count) {
		initJs(wd);
		jsFocus(wd, ret.get(0));
		filePath = resolveFile(filePath, ___cxt___);
		/*if(!new File(filePath).exists()) {
			File upfl = ___cxt___.getResourceFile(filePath);
			if(!upfl.exists()) {
				upfl = new File(System.getProperty("user.dir"), filePath);
				if(!upfl.exists()) {
					throw new RuntimeException("File not found at the given path, please provide a valid file [" + filePath + "]");
				} else {
					filePath = upfl.getAbsolutePath();
				}
			} else {
				filePath = upfl.getAbsolutePath();
			}
		}*/
		
		if(SeleniumTest.IN_DOCKER.get().getLeft() && wdmMgs.containsKey(SeleniumTest.IN_DOCKER.get().getRight()[0])) {
			try {
				DockerClient dc = wdmMgs.get(SeleniumTest.IN_DOCKER.get().getRight()[0]).getDockerService().getDockerClient();
				String containerId = SeleniumTest.IN_DOCKER.get().getRight()[1];
				File to = new File("/home/selenium");
				dc.copyArchiveToContainerCmd(containerId).withHostResource(filePath).withRemotePath(to.getAbsolutePath()).exec();
				filePath = new File("/home/selenium", new File(filePath).getName()).getAbsolutePath();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		if(wd instanceof HasDevTools && wd instanceof JavascriptExecutor) {
			DevTools devTools = ((HasDevTools)wd).getDevTools();
			try {
				String cssSelctor = (String)((JavascriptExecutor)wd).executeScript("return window.GatfUtil.getCssSelector($(arguments[0]))", ret.get(0));
				devTools.createSession();
				Node node = devTools.send(DOM.getDocument(Optional.empty(), Optional.empty()));
				NodeId nodeId = devTools.send(DOM.querySelector(node.getNodeId(), cssSelctor));
				List<String> files = new ArrayList<String>();
				files.add(filePath);
				for(int i=0;i<count-1;i++) {
					files.add(filePath);
				}
				devTools.send(DOM.setFileInputFiles(files, Optional.of(nodeId), Optional.empty(), Optional.empty()));
				/*Map<String, Object> dom = ((org.openqa.selenium.chrome.ChromeDriver)get___d___()).executeCdpCommand("DOM.getDocument", new HashMap<>());
				Map<String, Object> domqaArgs = new HashMap<>();
				domqaArgs.put("nodeId", ((Map<String, Object>)dom.get("root")).get("nodeId"));
				domqaArgs.put("selector", cssSelctor);
				Map<String, Object> domqs = ((HasDevTools)get___d___()).executeCdpCommand("DOM.querySelector", domqaArgs);
				Map<String, Object> setIfArgs = new HashMap<>();
				setIfArgs.put("nodeId", domqs.get("nodeId"));
				List<String> files = new ArrayList<String>();
				files.add(filePath);
				for(int i=0;i<count-1;i++) {
					files.add(filePath);
				}
				setIfArgs.put("files", files);
				((org.openqa.selenium.chrome.ChromeDriver)get___d___()).executeCdpCommand("DOM.setFileInputFiles", setIfArgs);*/
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				devTools.disconnectSession();
			}
		} else {
			if(count>1) {
				throw new RuntimeException("Multi File uploads only supported with devtools supported browsers for now");
			}
			ret.get(0).click();
			java.awt.datatransfer.StringSelection ss = new java.awt.datatransfer.StringSelection(filePath);
			java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(ss, null);
			try {
				java.awt.Robot robot21 = new java.awt.Robot();
				//robot21.keyPress(java.awt.event.KeyEvent.VK_ENTER);
				//robot21.keyRelease(java.awt.event.KeyEvent.VK_ENTER);
				robot21.keyPress(java.awt.event.KeyEvent.VK_CONTROL);
				robot21.keyPress(java.awt.event.KeyEvent.VK_V);
				robot21.keyRelease(java.awt.event.KeyEvent.VK_V);
				robot21.keyRelease(java.awt.event.KeyEvent.VK_CONTROL);
				robot21.delay(1000);
				robot21.keyPress(java.awt.event.KeyEvent.VK_ENTER);
				robot21.delay(200);
				robot21.keyRelease(java.awt.event.KeyEvent.VK_ENTER);
			} catch (AWTException e) {
			}
		}
		sleep(1000);
	}
	
	private static String getSettingVal(String sessionId, String name) {
		if(DRV_FEATURES.containsKey(sessionId)) {
			if(DRV_FEATURES.get(sessionId).containsKey(name) && DRV_FEATURES.get(sessionId).get(name)!=null) {
				return DRV_FEATURES.get(sessionId).get(name).trim();
			}
		}
		return null;
	}
	
	protected static void addSettingVal(WebDriver wd, String key, String val) {
		String sessionId = getSessionId(wd);
		if(DRV_FEATURES.containsKey(sessionId)) {
			DRV_FEATURES.get(sessionId).put(key, val);
		}
	}
	
	private static boolean isSettingEnabled(String sessionId, String name) {
		return "true".equalsIgnoreCase(getSettingVal(sessionId, name));
	}
	
	private static String getSessionId(WebDriver wd) {
		String sessionId = "";
		if(wd instanceof RemoteWebDriver) {
			sessionId = ((RemoteWebDriver)wd).getSessionId().toString();
		}
		return sessionId;
	}
	
	protected void clickAction(WebDriver wd, List<WebElement> ret, String qualifier) {
		for(final WebElement we: ret) {
			String sessionId = getSessionId(wd);
			if(isSettingEnabled(sessionId, "clk_focus") || "fo".equalsIgnoreCase(qualifier)) {
				jsFocus(wd, we);
			}
			we.click();
			break;
		}
	}

	private void elementAction(WebDriver wd, List<WebElement> ret, String action, String tvalue, String selValue, String selSubsel) {
		if(action.toLowerCase().matches(Command.clickExStr)) {
			Matcher m = Command.clickEx.matcher(action.toLowerCase());
        	m.matches();
        	String qualifier = m.group(1);
			clickAction(wd, ret, qualifier);
		} else if(action.equalsIgnoreCase("hover")) {
			for(final WebElement we: ret) {
				Actions ac = new Actions(wd);
				ac.moveToElement(we).perform();
				break;
			}
		} else if(action.equalsIgnoreCase("hoverandclick")) {
			for(final WebElement we: ret) {
				Actions ac = new Actions(wd);
				ac.moveToElement(we).click().perform();
				break;
			}
		} else if(action.equalsIgnoreCase("clear")) {
			for(final WebElement we: ret) {
				jsEvent(wd, we, "fo");
				we.clear();
				jsChange(wd, we);
				break;
			}
		} else if(action.equalsIgnoreCase("submit")) {
			for(final WebElement we: ret) {
				jsEvent(wd, we, "fo");
				we.submit();
				break;
			}
		} else if(action.toLowerCase().matches(Command.typeExStr)) {
			Matcher m = Command.typeEx.matcher(action.toLowerCase());
        	m.matches();
        	String type = m.group(1);
        	String qualifier = m.group(2);
			for(final WebElement we: ret) {
				jsEvent(wd, we, "fo");
				System.out.println(type + " => " + tvalue);
				sendKeys(type, qualifier, wd, we, tvalue, ___cxt___);
				//we.sendKeys(tvalue);
				//jsEvent(wd, we, qualifier);
				break;
			}
		} /*else if(action.equalsIgnoreCase("typenb") || action.equalsIgnoreCase("sendkeysnb")) {
			for(final WebElement we: ret) {
				System.out.println("Type => " + tvalue);
				we.sendKeys(tvalue);
				//jsChange(wd, we);
				break;
			}
		} else if(action.equalsIgnoreCase("randomize") || action.equalsIgnoreCase("randomizenb")) {
			String[] parts = tvalue.split("\\s+");
			sendKeys("randomize", qualifier, wd, ret, parts[0], parts.length>1?parts[1]:null, parts.length>2?parts[2]:null);
		}*/ else if(action.equalsIgnoreCase("upload")) {
			uploadFile(wd, ret, tvalue, 1);
		} else if(action.equalsIgnoreCase("select")) {
			//jsEvent(wd, ret.get(0), "fo");
			Select s = new Select(ret.get(0));
			if(selSubsel.equalsIgnoreCase("text")) {
				s.selectByVisibleText(selValue);
            } else if(selSubsel.equalsIgnoreCase("index")) {
            	s.selectByIndex(Integer.parseInt(selValue));
            } else if(selSubsel.equalsIgnoreCase("value")) {
            	s.selectByValue(selValue);
            } else if(selSubsel.equalsIgnoreCase("first")) {
            	s.selectByIndex(0);
            } else if(selSubsel.equalsIgnoreCase("last")) {
            	s.selectByIndex(s.getOptions().size()-1);
            }
			jsEvent(wd, ret.get(0), "fo");
		} /*else if(action.equalsIgnoreCase("chord")) {
			for(final WebElement we: ret) {
				we.sendKeys(Keys.chord(tvalue));
				jsChange(wd, we);
				break;
			}
		} else if(action.equalsIgnoreCase("chordnb")) {
			for(final WebElement we: ret) {
				we.sendKeys(Keys.chord(tvalue));
				//jsChange(wd, we);
				break;
			}
		}*/ else if(action.equalsIgnoreCase("dblclick") || action.equalsIgnoreCase("doubleclick")) {
			for(final WebElement we: ret) {
				//jsEvent(wd, we, "fo");
				Actions ac = new Actions(wd);
				ac.moveToElement(we).doubleClick().perform();
				break;
			}
		}
	}
	
	protected static void jsEvent(WebDriver wd, WebElement we, String qualifier) {
		if(StringUtils.isBlank(qualifier)) return;
		switch (qualifier) {
			case "bl":
				jsBlur(wd, we);
				break;
			case "ch":
				jsChange(wd, we);
				break;
			case "fo":
				we.click();
				jsFocus(wd, we);
				break;
			case "bk":
				we.sendKeys(Keys.chord("\ue003"));
				we.click();
				/*try {
					java.awt.Robot rbt = new java.awt.Robot();
					rbt.keyPress(java.awt.event.KeyEvent.VK_BACK_SPACE);
					rbt.keyRelease(java.awt.event.KeyEvent.VK_BACK_SPACE);
				} catch (AWTException e) {
				}*/
				break;
			case "cl":
				we.click();
				break;
			default:
				break;
		}
	}
	
	protected static void jsChange(WebDriver wd, WebElement we) {
		if (wd instanceof JavascriptExecutor) {
			((JavascriptExecutor)wd).executeScript("arguments[0].dispatchEvent(new Event('change', {bubbles: true}));", we);
		}
	}
	
	protected static void jsBlur(WebDriver wd, WebElement we) {
		if (wd instanceof JavascriptExecutor) {
			((JavascriptExecutor)wd).executeScript("arguments[0].blur();", we);
		}
	}
	
	protected static void jsFocus(WebDriver wd, WebElement we) {
		if (wd instanceof JavascriptExecutor) {
			((JavascriptExecutor)wd).executeScript("arguments[0].focus();", we);
		}
	}
	
	protected static void jsKeyEvent(WebDriver wd, WebElement we, String type, char key) {
		jsFocus(wd, we);
		if (wd instanceof JavascriptExecutor) {
			((JavascriptExecutor)wd).executeScript("arguments[0].dispatchEvent(new KeyboardEvent('"+type+"', {'key': '"+key+"'}));", we);
		}
	}
	
	protected static void jsClick(WebDriver wd, WebElement we) {
		if (wd instanceof JavascriptExecutor) {
			((JavascriptExecutor)wd).executeScript("arguments[0].dispatchEvent(new MouseEvent('click', {bubbles: true}));", we);
		}
	}
	
	@SuppressWarnings("unchecked")
	protected List<WebElement> handleWaitFuncWL(WebDriver driver, final SearchContext sc, final List<WebElement> ce, final int timeOutCounter, String relative, final String[] classifier, final String[] by, String subselector, 
			boolean byselsame, String value, String[] values, String action, String oper, String tvalue, String exmsg, boolean noExcep, int timeoutGranularity, boolean isVisible, String ... layers) {
		int counter = 0;
		while(true) {
			List<WebElement> el = (List<WebElement>)handleWaitOrTransientProv(driver, sc, ce, 0L, relative, classifier, by, subselector, byselsame, value, values, action, oper, tvalue, exmsg, noExcep, timeoutGranularity, isVisible, layers);
			if(!isVisible) {
				if (el != null) return el;
			} else {
				if (el == null) return el;
			}
			sleep(timeoutGranularity);
			if (counter++ == timeOutCounter)
				break;
		}
		if(!noExcep) throw new RuntimeException(exmsg);
		return null;
	}
	
	@SuppressWarnings("unchecked")
	protected List<String[]> transientProviderDataWL(WebDriver driver, final SearchContext sc, final List<WebElement> ce, final long timeOutCounter, String relative, final String[] classifier, final String[] by, String subselector, 
			boolean byselsame, String value, String[] values, String action, String oper, String tvalue, String exmsg, boolean noExcep, int timeoutGranularity, boolean isVisible, String ... layers) {
		int counter = 0;
		while(true) {
			List<String[]> el = (List<String[]>)handleWaitOrTransientProv(driver, sc, ce, 0L, relative, classifier, by, subselector, byselsame, value, values, action, oper, tvalue, exmsg, noExcep, timeoutGranularity, isVisible, layers);
			if(!isVisible) {
				if (el != null) return el;
			} else {
				if (el == null) return el;
			}
			sleep(timeoutGranularity);
			if (counter++ == timeOutCounter)
				break;
		}
		if(!noExcep) throw new RuntimeException(exmsg);
		return null;
	}

	@SuppressWarnings("unchecked")
	protected List<WebElement> handleWaitFunc(WebDriver driver, final SearchContext sc, final List<WebElement> ce, final long timeOutInSeconds, String relative, final String[] classifier, final String[] by, String subselector, 
			boolean byselsame, String value, String[] values, String action, String oper, String tvalue, String exmsg, boolean noExcep, int timeoutGranularity, boolean isVisible, String ... layers) {
		return (List<WebElement>)handleWaitOrTransientProv(driver, sc, ce, timeOutInSeconds, relative, classifier, by, subselector, byselsame, value, values, action, oper, tvalue, exmsg, noExcep, timeoutGranularity, isVisible, layers);
	}
	
	@SuppressWarnings("unchecked")
	protected List<String[]> transientProviderData(WebDriver driver, final SearchContext sc, final List<WebElement> ce, final long timeOutInSeconds, String relative, final String[] classifier, final String[] by, String subselector, 
			boolean byselsame, String value, String[] values, String action, String oper, String tvalue, String exmsg, boolean noExcep, int timeoutGranularity, boolean isVisible, String ... layers) {
		return (List<String[]>)handleWaitOrTransientProv(driver, sc, ce, timeOutInSeconds, relative, classifier, by, subselector, byselsame, value, values, action, oper, tvalue, exmsg, noExcep, timeoutGranularity, isVisible, layers);
	}

	@SuppressWarnings("unchecked")
	private Object handleWaitOrTransientProv(WebDriver driver, final SearchContext sc, final List<WebElement> ce, final long timeOutInSeconds, String relative, final String[] classifier, final String[] by, String subselector, 
			boolean byselsame, String value, String[] values, final String action, String oper, String tvalue, String exmsg, boolean noExcep, int timeoutGranularity, boolean isVisible, String ... layers) {
		final WebDriver wsc = (WebDriver) sc;
		final Object[] o = new Object[2];
		long timeoutRemaining = 0;
		initJs(driver);
		System.out.println("Searching element => " + by[0]+"@"+classifier[0]);
		if(timeOutInSeconds<=0) {
			List<WebElement> el = getElements(driver, wsc, by[0]+"@"+classifier[0], by[1]+"@"+classifier[1], relative, ce);
			if (el == null || el.isEmpty())  {
			} else {
				boolean enabledCheck = false;
				if(action!=null && (action.toLowerCase().matches(Command.clickExStr) || action.toLowerCase().matches(Command.typeExStr))) {
					enabledCheck = true;
				}

				WebElement elo = elementInteractable(el.get(0), enabledCheck, layers).apply(driver);
				if(elo!=null) {
					o[0] = el;
					o[1] = ce;
				}
			}
		} else {
			long start = System.currentTimeMillis();
			try {
				(new WebDriverWait(wsc, Duration.ofSeconds(timeOutInSeconds))).until(new Function<WebDriver, Boolean>() {
					public Boolean apply(WebDriver input) {
						List<WebElement> ___ce___ = ce;
						try {
							List<WebElement> el = getElements(driver, wsc, by[0]+"@"+classifier[0], by[1]+"@"+classifier[1], relative, ce);
							if (el == null || el.isEmpty()) return false;

							boolean enabledCheck = false;
							if(action!=null && (action.toLowerCase().matches(Command.clickExStr) || action.toLowerCase().matches(Command.typeExStr))) {
								enabledCheck = true;
							}

							WebElement elo = elementInteractable(el.get(0), enabledCheck, layers).apply(driver);
							if(elo!=null) {
								o[0] = el;
								o[1] = ___ce___;
								return true;
							}
							return false;
						} catch(Exception e) {
							return false;
						}
					}
				});
			} catch (org.openqa.selenium.TimeoutException e) {
				throw new RuntimeException(exmsg, e);
			}
			timeoutRemaining = timeOutInSeconds - (System.currentTimeMillis() - start)/timeoutSleepGranularity;
			//System.out.println("Timeout remaining = " + timeoutRemaining);
		}
		List<WebElement> ret = (List<WebElement>)o[0];
		Object resp = ret;
		boolean flag = true;
		if(ret!=null) {
			if(action==null && (StringUtils.isNotBlank(value) || (values!=null && values.length>0)) && subselector!=null && !subselector.isEmpty()) {
				if(value!=null)
				{
					if(byselsame)
					{
						String rhs = null;
						if(subselector.equalsIgnoreCase("title")) {
							rhs = ((WebDriver)sc).getTitle();
						} else if(subselector.equalsIgnoreCase("currentUrl")) {
							rhs = ((WebDriver)sc).getCurrentUrl();
						} else if(subselector.equalsIgnoreCase("pageSource")) {
							rhs = ((WebDriver)sc).getPageSource();
						} else if(subselector.equalsIgnoreCase("width")) {
							rhs = String.valueOf(driver.manage().window().getSize().getWidth());
						} else if(subselector.equalsIgnoreCase("height")) {
							rhs = String.valueOf(driver.manage().window().getSize().getHeight());
						} else if(subselector.equalsIgnoreCase("xpos")) {
							rhs = String.valueOf(driver.manage().window().getPosition().getX());
						} else if(subselector.equalsIgnoreCase("ypos")) {
							rhs = String.valueOf(driver.manage().window().getPosition().getY());
						} else if(subselector.equalsIgnoreCase("alerttext")) {
							rhs = String.valueOf(driver.switchTo().alert().getText());
						}

						if(rhs!=null) {
							if(oper.startsWith("<=")) {
								flag &= value.compareTo(rhs)<=0;
							} else if(oper.startsWith(">=")) {
								flag &= value.compareTo(rhs)>=0;
							} else if(oper.startsWith("=") || oper.startsWith("==")) {
								flag &= value.compareTo(rhs)==0;
							} else if(oper.startsWith("<")) {
								flag &= value.compareTo(rhs)<0;
							} else if(oper.startsWith(">")) {
								flag &= value.compareTo(rhs)>0;
							} else if(oper.startsWith("!=")) {
								flag &= value.compareTo(rhs)!=0;
							} else if(oper.startsWith("%") && oper.endsWith("%")) {
								flag &= value.contains(rhs);
							} else if(oper.startsWith("%")) {
								flag &= value.startsWith(rhs);
							} else if(oper.endsWith("%")) {
								flag &= value.endsWith(rhs);
							}
						}
					} else {
						for(final WebElement we: ret) {
							String rhs = null;
							if(subselector.equalsIgnoreCase("text")) {
								rhs = we.getText();
							} else if(subselector.equalsIgnoreCase("tagname")) {
								rhs = we.getTagName();
							} else if(subselector.toLowerCase().startsWith("attr@")) {
								String atname = subselector.substring(5);
								if(atname.charAt(0)=='"' || atname.charAt(0)=='\'') {
									atname = atname.substring(1, atname.length()-1);
								}
								rhs = we.getAttribute(atname);
							} else if(subselector.toLowerCase().startsWith("cssvalue@")) {
								String atname = subselector.substring(9);
								if(atname.charAt(0)=='"' || atname.charAt(0)=='\'') {
									atname = atname.substring(1, atname.length()-1);
								}
								rhs = we.getCssValue(atname);
							} else if(subselector.equalsIgnoreCase("width")) {
								rhs = String.valueOf(we.getSize().getWidth());
							} else if(subselector.equalsIgnoreCase("height")) {
								rhs = String.valueOf(we.getSize().getHeight());
							} else if(subselector.equalsIgnoreCase("xpos")) {
								rhs = String.valueOf(we.getLocation().getX());
							} else if(subselector.equalsIgnoreCase("ypos")) {
								rhs = String.valueOf(we.getLocation().getY());
							}
	
							if(rhs!=null) {
								if(oper.startsWith("<=")) {
									flag &= value.compareTo(rhs)<=0;
								} else if(oper.startsWith(">=")) {
									flag &= value.compareTo(rhs)>=0;
								} else if(oper.startsWith("=") || oper.startsWith("==")) {
									flag &= value.compareTo(rhs)==0;
								} else if(oper.startsWith("<")) {
									flag &= value.compareTo(rhs)<0;
								} else if(oper.startsWith(">")) {
									flag &= value.compareTo(rhs)>0;
								} else if(oper.startsWith("!=")) {
									flag &= value.compareTo(rhs)!=0;
								} else if(oper.startsWith("%") && oper.endsWith("%")) {
									flag &= value.contains(rhs);
								} else if(oper.startsWith("%")) {
									flag &= value.startsWith(rhs);
								} else if(oper.endsWith("%")) {
									flag &= value.endsWith(rhs);
								}
							}
	
							break;
						}
					}
				} else if(values!=null && values.length>0) {
					if(byselsame)
					{
						String rhs = null;
						if(subselector.equalsIgnoreCase("title")) {
							rhs = ((WebDriver)sc).getTitle();
						} else if(subselector.equalsIgnoreCase("currentUrl")) {
							rhs = ((WebDriver)sc).getCurrentUrl();
						} else if(subselector.equalsIgnoreCase("pageSource")) {
							rhs = ((WebDriver)sc).getPageSource();
						} else if(subselector.equalsIgnoreCase("width")) {
							rhs = String.valueOf(driver.manage().window().getSize().getWidth());
						} else if(subselector.equalsIgnoreCase("height")) {
							rhs = String.valueOf(driver.manage().window().getSize().getHeight());
						} else if(subselector.equalsIgnoreCase("xpos")) {
							rhs = String.valueOf(driver.manage().window().getPosition().getX());
						} else if(subselector.equalsIgnoreCase("ypos")) {
							rhs = String.valueOf(driver.manage().window().getPosition().getY());
						} else if(subselector.equalsIgnoreCase("alerttext")) {
							rhs = String.valueOf(driver.switchTo().alert().getText());
						}

						if(rhs!=null) {
							if(oper.startsWith("<=")) {
								boolean tflag = true;
								for (int i=0;i<values.length;i++) {
									String nvalue = values[i];
									tflag |= nvalue.compareTo(rhs)<=0;
								}
								flag &= tflag;
							} else if(oper.startsWith(">=")) {
								boolean tflag = true;
								for (int i=0;i<values.length;i++) {
									String nvalue = values[i];
									tflag &= nvalue.compareTo(rhs)>=0;
								}
								flag &= tflag;
							} else if(oper.startsWith("=") || oper.startsWith("==")) {
								boolean tflag = true;
								for (int i=0;i<values.length;i++) {
									String nvalue = values[i];
									tflag &= nvalue.compareTo(rhs)==0;
								}
								flag &= tflag;
							} else if(oper.startsWith("<")) {
								boolean tflag = true;
								for (int i=0;i<values.length;i++) {
									String nvalue = values[i];
									tflag &= nvalue.compareTo(rhs)<0;
								}
								flag &= tflag;
							} else if(oper.startsWith(">")) {
								boolean tflag = true;
								for (int i=0;i<values.length;i++) {
									String nvalue = values[i];
									tflag &= nvalue.compareTo(rhs)>0;
								}
								flag &= tflag;
							} else if(oper.startsWith("!=")) {
								boolean tflag = true;
								for (int i=0;i<values.length;i++) {
									String nvalue = values[i];
									tflag &= nvalue.compareTo(rhs)!=0;
								}
								flag &= tflag;
							} else if(oper.startsWith("%") && oper.endsWith("%")) {
								boolean tflag = true;
								for (int i=0;i<values.length;i++) {
									String nvalue = values[i];
									tflag &= nvalue.contains(rhs);
								}
								flag &= tflag;
							} else if(oper.startsWith("%")) {
								boolean tflag = true;
								for (int i=0;i<values.length;i++) {
									String nvalue = values[i];
									tflag &= nvalue.startsWith(rhs);
								}
								flag &= tflag;
							} else if(oper.endsWith("%")) {
								boolean tflag = true;
								for (int i=0;i<values.length;i++) {
									String nvalue = values[i];
									tflag &= nvalue.endsWith(rhs);
								}
								flag &= tflag;
							}
						}
					} else {
						for(final WebElement we: ret) {
							String rhs = null;
							if(subselector.equalsIgnoreCase("text")) {
								rhs = we.getText();
							} else if(subselector.equalsIgnoreCase("tagname")) {
								rhs = we.getTagName();
							} else if(subselector.toLowerCase().startsWith("attr@")) {
								String atname = subselector.substring(5);
								if(atname.charAt(0)=='"' || atname.charAt(0)=='\'') {
									atname = atname.substring(1, atname.length()-1);
								}
								rhs = we.getAttribute(atname);
							} else if(subselector.toLowerCase().startsWith("cssvalue@")) {
								String atname = subselector.substring(9);
								if(atname.charAt(0)=='"' || atname.charAt(0)=='\'') {
									atname = atname.substring(1, atname.length()-1);
								}
								rhs = we.getCssValue(atname);
							} else if(subselector.equalsIgnoreCase("width")) {
								rhs = String.valueOf(we.getSize().getWidth());
							} else if(subselector.equalsIgnoreCase("height")) {
								rhs = String.valueOf(we.getSize().getHeight());
							} else if(subselector.equalsIgnoreCase("xpos")) {
								rhs = String.valueOf(we.getLocation().getX());
							} else if(subselector.equalsIgnoreCase("ypos")) {
								rhs = String.valueOf(we.getLocation().getY());
							}
	
							if(rhs!=null) {
								if(oper.startsWith("<=")) {
									boolean tflag = true;
									for (int i=0;i<values.length;i++) {
										String nvalue = values[i];
										tflag |= nvalue.compareTo(rhs)<=0;
									}
									flag &= tflag;
								} else if(oper.startsWith(">=")) {
									boolean tflag = true;
									for (int i=0;i<values.length;i++) {
										String nvalue = values[i];
										tflag &= nvalue.compareTo(rhs)>=0;
									}
									flag &= tflag;
								} else if(oper.startsWith("=")) {
									boolean tflag = true;
									for (int i=0;i<values.length;i++) {
										String nvalue = values[i];
										tflag &= nvalue.compareTo(rhs)==0;
									}
									flag &= tflag;
								} else if(oper.startsWith("<")) {
									boolean tflag = true;
									for (int i=0;i<values.length;i++) {
										String nvalue = values[i];
										tflag &= nvalue.compareTo(rhs)<0;
									}
									flag &= tflag;
								} else if(oper.startsWith(">")) {
									boolean tflag = true;
									for (int i=0;i<values.length;i++) {
										String nvalue = values[i];
										tflag &= nvalue.compareTo(rhs)>0;
									}
									flag &= tflag;
								} else if(oper.startsWith("!=")) {
									boolean tflag = true;
									for (int i=0;i<values.length;i++) {
										String nvalue = values[i];
										tflag &= nvalue.compareTo(rhs)!=0;
									}
									flag &= tflag;
								} else if(oper.startsWith("%") && oper.endsWith("%")) {
									boolean tflag = true;
									for (int i=0;i<values.length;i++) {
										String nvalue = values[i];
										tflag &= nvalue.contains(rhs);
									}
									flag &= tflag;
								} else if(oper.startsWith("%")) {
									boolean tflag = true;
									for (int i=0;i<values.length;i++) {
										String nvalue = values[i];
										tflag &= nvalue.startsWith(rhs);
									}
									flag &= tflag;
								} else if(oper.endsWith("%")) {
									boolean tflag = true;
									for (int i=0;i<values.length;i++) {
										String nvalue = values[i];
										tflag &= nvalue.endsWith(rhs);
									}
									flag &= tflag;
								}
							}
	
							break;
						}
					}
				}
			} else if(action!=null) {
				try {
					elementAction(driver, ret, action, tvalue, value, subselector);
				} catch (WebDriverException e) {
					String exMsg = ExceptionUtils.getStackTrace(e);
					boolean isInvClk = exMsg.contains("org.openqa.selenium.ElementClickInterceptedException") 
							&& exMsg.contains("is not clickable at point (")
							&& exMsg.contains("Other element would receive the click");
					boolean isNotSel = exMsg.contains("org.openqa.selenium.ElementNotSelectableException");
					boolean isNotInt = exMsg.contains("org.openqa.selenium.ElementNotInteractableException");
					boolean isNotVis = exMsg.contains("org.openqa.selenium.ElementNotVisibleException");
					if(isInvClk || isNotSel || isNotInt || isNotVis) {
						resp = null;
					}
					if(timeoutRemaining>0 && (isInvClk || isNotSel || isNotInt || isNotVis)) {
						Exception lastException = null;
						while(timeoutRemaining>0) {
							try {
								Thread.sleep(timeoutSleepGranularity);
								System.out.println("WDE-Retrying operation.....Timeout remaining = " + (timeoutRemaining-1) + " secs");
								elementAction(driver, ret, action, tvalue, value, subselector);
								resp = ret;
								lastException = null;
								break;
							} catch (Exception e1) {
								lastException = e1;
								exMsg = ExceptionUtils.getStackTrace(e1);
								isInvClk = exMsg.contains("org.openqa.selenium.ElementClickInterceptedException") 
										&& exMsg.contains("is not clickable at point (")
										&& exMsg.contains("Other element would receive the click");
								isNotSel = exMsg.contains("org.openqa.selenium.ElementNotSelectableException");
								isNotInt = exMsg.contains("org.openqa.selenium.ElementNotInteractableException");
								isNotVis = exMsg.contains("org.openqa.selenium.ElementNotVisibleException");
								if(!(isInvClk || isNotSel || isNotInt || isNotVis)) {
									break;
								}
							}
							timeoutRemaining--;
						}
						if(lastException!=null) {
							lastException.printStackTrace();
						}
					} else {
						//This means this has come from WaitTillElementVisibleOrInvisibleCommand
						if(timeoutRemaining==0 && (isInvClk || isNotSel || isNotInt || isNotVis)) {
							return null;
						}
						e.printStackTrace();
					}
				} catch (Exception e) {
					String exMsg = ExceptionUtils.getStackTrace(e);
					boolean isInvClk = exMsg.contains("org.openqa.selenium.ElementClickInterceptedException") 
							&& exMsg.contains("is not clickable at point (")
							&& exMsg.contains("Other element would receive the click");
					boolean isNotSel = exMsg.contains("org.openqa.selenium.ElementNotSelectableException");
					boolean isNotInt = exMsg.contains("org.openqa.selenium.ElementNotInteractableException");
					boolean isNotVis = exMsg.contains("org.openqa.selenium.ElementNotVisibleException");
					if(isInvClk || isNotSel || isNotInt || isNotVis) {
						resp = null;
					}
					if(timeoutRemaining>0 && (isInvClk || isNotSel || isNotInt || isNotVis)) {
						Exception lastException = null;
						while(timeoutRemaining>0) {
							try {
								Thread.sleep(timeoutSleepGranularity);
								System.out.println("E-Retrying operation.....Timeout remaining = " + (timeoutRemaining-1) + " secs");
								elementAction(driver, ret, action, tvalue, value, subselector);
								resp = ret;
								lastException = null;
								break;
							} catch (Exception e1) {
								lastException = e1;
								exMsg = ExceptionUtils.getStackTrace(e1);
								isInvClk = exMsg.contains("org.openqa.selenium.ElementClickInterceptedException") 
										&& exMsg.contains("is not clickable at point (")
										&& exMsg.contains("Other element would receive the click");
								isNotSel = exMsg.contains("org.openqa.selenium.ElementNotSelectableException");
								isNotInt = exMsg.contains("org.openqa.selenium.ElementNotInteractableException");
								isNotVis = exMsg.contains("org.openqa.selenium.ElementNotVisibleException");
								if(!(isInvClk || isNotSel || isNotInt || isNotVis)) {
									break;
								}
							}
							timeoutRemaining--;
						}
						if(lastException!=null) {
							lastException.printStackTrace();
						}
					} else {
						//This means this has come from WaitTillElementVisibleOrInvisibleCommand
						if(timeoutRemaining==0 && (isInvClk || isNotSel || isNotInt || isNotVis)) {
							return null;
						}
						e.printStackTrace();
					}
				}
			} else if("selected".equalsIgnoreCase(subselector) || "enabled".equalsIgnoreCase(subselector) || "visible".equalsIgnoreCase(subselector)) {
				for(final WebElement we: ret) {
					if(subselector.equalsIgnoreCase("selected")) {
						flag &= we.isSelected();
					} else if(subselector.equalsIgnoreCase("enabled")) {
						flag &= we.isEnabled();
					} else if(subselector.equalsIgnoreCase("visible")) {
						flag &= we.isDisplayed();
					}
					break;
				}
			} else if(subselector!=null) {
				List<String> ssl = Arrays.asList(subselector.split("[\t ]*,[\t ]*"));
				List<String[]> rtl = new java.util.ArrayList<String[]>();
				for(final WebElement we: ret) {
					String[] t = new String[ssl.size()];
					for (int i=0;i<ssl.size();i++)
					{
						if(ssl.get(i).equalsIgnoreCase("text")) {
							t[i] = we.getText();
						} else if(ssl.get(i).equalsIgnoreCase("tagname")) {
							t[i] = we.getTagName();
						} else if(ssl.get(i).toLowerCase().startsWith("attr@")) {
							String atname = ssl.get(i).substring(5);
							if(atname.charAt(0)=='"' || atname.charAt(0)=='\'') {
								atname = atname.substring(1, atname.length()-1);
							}
							t[i] = we.getAttribute(atname);
						} else if(ssl.get(i).toLowerCase().startsWith("cssvalue@")) {
							String atname = ssl.get(i).substring(9);
							if(atname.charAt(0)=='"' || atname.charAt(0)=='\'') {
								atname = atname.substring(1, atname.length()-1);
							}
							t[i] = we.getCssValue(atname);
						} else if(ssl.get(i).equalsIgnoreCase("width")) {
							t[i] = String.valueOf(we.getSize().getWidth());
						} else if(ssl.get(i).equalsIgnoreCase("height")) {
							t[i] = String.valueOf(we.getSize().getHeight());
						} else if(ssl.get(i).equalsIgnoreCase("xpos")) {
							t[i] = String.valueOf(we.getLocation().getX());
						} else if(ssl.get(i).equalsIgnoreCase("ypos")) {
							t[i] = String.valueOf(we.getLocation().getY());
						}
					}
					rtl.add(t);
				}
				resp = rtl;
			}
		}
		if(!noExcep && !flag) {
			throw new RuntimeException(exmsg);
		} else if(noExcep) {
			//resp = null;
		}
		return resp;
	}

	protected void sleep(long wait) {
		try {
			Thread.sleep(wait);
		} catch (Exception ___e___15) {
		}
	}

	private static int getZIndex(WebElement el) {
		String zindex = el.getCssValue("z-index");
		if(zindex!=null) {
			try {
				return Integer.parseInt(el.getCssValue("z-index"));
			} catch (Exception e) {
			}
		}
		return 0;
	}

	private static boolean checkRectangleOnTop(Rectangle trec, Rectangle orec) {
		Point tmid = new Point(trec.getX() + trec.getWidth()/2, trec.getY() + trec.getHeight()/2);
		return tmid.getX()>orec.getX() && tmid.getX()<(orec.getX()+orec.getWidth()) && tmid.getY()>orec.getY() && tmid.getY()<(orec.getY()+orec.getHeight());
	}

	protected static Rectangle getRect(WebElement e) {
		return new Rectangle(e.getLocation().getX(), e.getLocation().getY(), e.getSize().getWidth(), e.getSize().getHeight());
	}
	
	protected static Point getCenter(WebElement e) {
		Rectangle rect = ((WebElement)e).getRect();
		Point from = new Point(rect.x + rect.getWidth() / 2, rect.y + rect.getHeight() / 2);
		return from;
	}

	private static ExpectedCondition<WebElement> elementInteractable(WebElement element, boolean enabledCheck, String ... layers) {
		return new ExpectedCondition<WebElement>() {
			@Override
			public WebElement apply(WebDriver driver) {
				try {
					WebElement telement = ExpectedConditions.elementToBeClickable(element).apply(driver);
					if(telement==null && isJsVisible(driver, element)) {
						return element;
					}
					//element.isDisplayed() && (!enabledCheck || (enabledCheck && element.isEnabled()));
					if(telement!=null && layers!=null && layers.length>0) {
						int tzl = getZIndex(telement);
						Rectangle trec = getRect(telement);
						List<WebElement> ce = new ArrayList<WebElement>();
						ce.add(element);
						for (String layer : layers) {
							try {
								List<WebElement> el = getElements(driver, driver, layer, null, null, ce);
								if(el!=null && el.size()>0 && /*el.get(0).isEnabled() &&*/ el.get(0).isDisplayed()) {
									int ozl = getZIndex(el.get(0));
									Rectangle orec = getRect(el.get(0));
									boolean temp = ozl>tzl && checkRectangleOnTop(trec, orec);
									if(!temp) {
										telement = null;
										break;
									}
								}
							} catch (Exception e) {
							}
						}
					}
					return telement;
				} catch (Exception e) {
					return null;
				}
			}
		};
	}
	
	private static boolean isJsVisible(WebDriver driver, WebElement element) {
		if (driver instanceof JavascriptExecutor) {
			return Boolean.TRUE.equals(((JavascriptExecutor)driver)
					.executeScript("return arguments[0].offsetParent!==undefined && arguments[0].offsetParent!==null", element));
		}
		return true;
	}

	protected void initStateFulProvider(String name) {
		String hash = getFileProviderHash(name);
		if(hash==null) {
			throw new RuntimeException("Only File providers can have stateful loops, provder " + name + " is not a file provider");
		}
		File stateFile = new File(name+"_s_state");
		if(!stateFile.exists()) {
			try {
				stateFile.createNewFile();
				FileUtils.write(stateFile, hash+";0", "UTF-8");
			} catch (Exception e) {
			}
		} else {
			try {
				String contents = FileUtils.readFileToString(stateFile, "UTF-8");
				if(!contents.startsWith(hash+";")) {
					throw new RuntimeException("File provider " + name + " has changed, cannot continue with stateful provider loop\n"
							+ "Delete the " + name+"_s_state" + " file to continue from the beginning of the provider loop");
				}
			} catch (Exception e) {
			}
		}
	}

	protected int preStateFulProvider(String name, int index) {
		File stateFile = new File(name+"_s_state");
		if(stateFile.exists()) {
			try {
				String contents = FileUtils.readFileToString(stateFile, "UTF-8");
				return Integer.valueOf(contents.substring(contents.indexOf(";")+1));
			} catch (Exception e) {
			}
		} else {
			System.out.println("File provider " + name + " has been deleted on the filesystem, but we will continue as far as we can");
		}
		return index;
	}

	protected void postStateFulProvider(String name, int index) {
		File stateFile = new File(name+"_s_state");
		if(stateFile.exists()) {
			try {
				String contents = FileUtils.readFileToString(stateFile, "UTF-8");
				contents = contents.substring(0, contents.indexOf(";")+1) + (index+1);
				FileUtils.write(stateFile, contents, "UTF-8");
			} catch (Exception e) {
			}
		} else {
			System.out.println("File provider " + name + " has been deleted on the filesystem, but we will continue as far as we can");
		}
	}

	protected byte[] screenshotAsBytes(WebDriver webDriver) throws IOException {
		Screenshot fpScreenshot = new AShot().shootingStrategy(ShootingStrategies.viewportPasting(1000)).takeScreenshot(webDriver);
		BufferedImage originalImage = fpScreenshot.getImage();
		try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
			ImageIO.write(originalImage, "png", baos);
			baos.flush();
			return baos.toByteArray();
		}
	}

	protected static void screenshotAsFile(WebDriver webDriver, String filepath) throws IOException {
		if(webDriver instanceof AppiumDriver) {
			File sc = ((TakesScreenshot)new Augmenter().augment(webDriver)).getScreenshotAs(OutputType.FILE);
			FileUtils.copyFile(sc, new File(filepath));
		} else {
			float windowDPR = 1.75f;
			if (webDriver instanceof JavascriptExecutor) {
				Object output = ((JavascriptExecutor) webDriver).executeScript("return window.devicePixelRatio");
				String value = String.valueOf(output);
				windowDPR = Float.parseFloat(value);
			}
			Screenshot fpScreenshot = new AShot().shootingStrategy(ShootingStrategies.viewportPasting(ShootingStrategies.scaling(windowDPR), 100)).takeScreenshot(webDriver);
			BufferedImage originalImage = fpScreenshot.getImage();
			ImageIO.write(originalImage, "png", new FileOutputStream(filepath));
		}
	}

	protected void elementScreenshotAsFile(WebDriver webDriver, WebElement element, String filepath) throws IOException {
		if(webDriver instanceof AppiumDriver) {
			File sc = element.getScreenshotAs(OutputType.FILE);
			//File sc = ((TakesScreenshot)new Augmenter().augment(element)).getScreenshotAs(OutputType.FILE);
			FileUtils.copyFile(sc, new File(filepath));
		} else {
			float windowDPR = 1.75f;
			if (webDriver instanceof JavascriptExecutor) {
				Object output = ((JavascriptExecutor) webDriver).executeScript("return window.devicePixelRatio");
				String value = String.valueOf(output);
				windowDPR = Float.parseFloat(value);
			}
			Screenshot fpScreenshot = new AShot().shootingStrategy(ShootingStrategies.viewportPasting(ShootingStrategies.scaling(windowDPR), 100)).takeScreenshot(webDriver, element);
			BufferedImage originalImage = fpScreenshot.getImage();
			ImageIO.write(originalImage, "png", new FileOutputStream(filepath));
		}
	}
	
	protected String getOutDir() {
		return ___cxt___.getOutDirPath();
	}
	
	public static void main(String[] args) throws Exception {
        GatfExecutorConfig config = Command.getConfig(args[1].trim(), args.length>2?args[2].trim():null);
        config.setSeleniumLoggerPreferences("browser(OFF),client(OFF),driver(OFF),performance(OFF),profiler(OFF),server(OFF)");
        for (SeleniumDriverConfig selConf : config.getSeleniumDriverConfigs())
        {
            if(selConf.getDriverName() != null && selConf.getPath()!=null && new File(selConf.getPath()).exists()) {
                System.setProperty(selConf.getDriverName(), selConf.getPath());
            }
        }
        
        System.setProperty("jdk.tls.client.protocols", "TLSv1,TLSv1.1,TLSv1.2");
        Security.setProperty("crypto.policy", "unlimited");
        System.setProperty("webdriver.http.factory", "jdk-http-client");
        
        AcceptanceTestContext c = new AcceptanceTestContext();
        c.setGatfExecutorConfig(config);
        c.validateAndInit(true);
        c.getWorkflowContextHandler().initializeSuiteContext(1);
    	
        try {
        	final LoggingPreferences lp = SeleniumCodeGeneratorAndUtil.getLp(config);
        	SeleniumTest dyn = (SeleniumTest)Class.forName(args[0]).getDeclaredConstructor(new Class[] {AcceptanceTestContext.class, int.class}).newInstance(new Object[] {c, 0});
        	System.out.println(dyn!=null?"SUCCESS":"FAILURE");
            if(dyn!=null) {
            	try {
                    dyn.execute(lp);
                } catch (Throwable e) {
                }
                dyn.quitAll();
            }
        } catch (GatfSelCodeParseError e) {
        	e.printStackTrace();
        } catch (Throwable e) {
        	e.printStackTrace();
        }
    }
	
	@SuppressWarnings("serial")
	public static class GatfRunTimeErrors extends RuntimeException {
		private List<GatfRunTimeError> errors;
		public GatfRunTimeErrors(List<GatfRunTimeError> errors) {
			this.errors = errors;
		}
		public List<GatfRunTimeError> getAll() {
			return errors;
		}
	}
	
	@SuppressWarnings("serial")
	public static abstract class GatfRunTimeError extends RuntimeException {
		Object[] details;
		public abstract Object[] getDetails();
		public GatfRunTimeError(String msg) {
			super(msg);
		}
		public GatfRunTimeError(String msg, Throwable e) {
			super(msg, e);
		}
	}
	
	@SuppressWarnings("serial")
	public static abstract class ValidSubTestException extends GatfRunTimeError {
		public ValidSubTestException(String msg) {
			super(msg);
		}
		public Object[] getDetails() {
        	return details;
        }
	}
	
	@SuppressWarnings("serial")
	public static class PassSubTestException extends ValidSubTestException {
		public PassSubTestException(String msg) {
			super(msg);
		}
	}
	
	@SuppressWarnings("serial")
	public static class WarnSubTestException extends ValidSubTestException {
		public WarnSubTestException(String msg) {
			super(msg);
		}
	}
	
	@SuppressWarnings("serial")
	public static class FailSubTestException extends GatfRunTimeError {
		public FailSubTestException(String msg) {
			super(msg);
		}
		String img, stName;
		public String getStName() {
			return stName;
		}
		public String getStImg() {
			return img;
		}
		public Object[] getDetails() {
        	return details;
        }
	}
	
	@SuppressWarnings("serial")
	public static class SubTestException extends GatfRunTimeError {
		String img, stName;
		Throwable cause;
		public SubTestException(String stName, Throwable cause) {
			super(cause.getMessage());
			this.stName = stName;
			this.cause = cause;
		}
		public String getStName() {
			return stName;
		}
		public String getStImg() {
			return img;
		}
		public Object[] getDetails() {
        	return details;
        }
	}
	
	@SuppressWarnings("serial")
	public static class FailureException extends GatfRunTimeError {
		Throwable cause;
		public FailureException(Throwable cause) {
			super(cause.getMessage());
			this.cause = cause;
		}
		public Object[] getDetails() {
        	return details;
        }
	}
	
	protected void navigateTo(WebDriver d, String url) {
		d.navigate().to(url);
		waitForReady(d);
	}
	
	protected void refresh(WebDriver d) {
		d.navigate().refresh();
		waitForReady(d);
	}
	
	protected void initBrowser(WebDriver driver, boolean logconsole, boolean logdebug, boolean lognw) {
		String sessionId = ((RemoteWebDriver)driver).getSessionId().toString();
		if(driver instanceof HasDevTools) {
			DevTools devTools = ((HasDevTools)driver).getDevTools();
			BROWSER_FEATURES.put(sessionId+".SECURITY", true);
			//BROWSER_FEATURES.put(sessionId+".INTERCEPTNW", interceptApiCall);
			BROWSER_FEATURES.put(sessionId+".LOGCONSOLE", logconsole);
			BROWSER_FEATURES.put(sessionId+".NWCONSOLE", lognw);
			devTools.createSession();
			devTools.send(Network.setCacheDisabled(true));
			devTools.send(org.openqa.selenium.devtools.v108.security.Security.setIgnoreCertificateErrors(true));
			if(logconsole) {
				devTools.send(Log.enable());
				devTools.addListener(Log.entryAdded(), logEntry -> {
					System.out.println("Browser Console Log ["+logEntry.getLevel()+"]: "+logEntry.getText());
				});
			}
			devTools.disconnectSession();
		}
		
		if(logdebug) {
			java.util.logging.Logger.getGlobal().setLevel(Level.ALL);
		}
		
		DRV_FEATURES.put(sessionId, new ConcurrentHashMap<String, String>());
	}
	
	protected void waitForReady(WebDriver driver) {
		if (driver instanceof JavascriptExecutor) {
			int counter = 0;
			while(counter++<60) {
				String status = (String)((JavascriptExecutor)driver).executeScript("return document.readyState");
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
				}
				if(status.equalsIgnoreCase("complete")) break;
			}
		}
	}
	
	/*protected void initCdp(WebDriver driver) {
		if(driver instanceof HasDevTools) {
			DevTools devTools = ((HasDevTools)driver).getDevTools();
			String sessionId = ((RemoteWebDriver)driver).getSessionId().toString();

			waitForReady(driver);
			
			devTools.createSession();
			
			if(BROWSER_FEATURES.get(sessionId+".LOGCONSOLE")) {
				devTools.send(Log.enable());
				devTools.addListener(Log.entryAdded(), logEntry -> {
					System.out.println("Browser Console Log ["+logEntry.getLevel()+"]: "+logEntry.getText());
				});
			}
			
			devTools.disconnectSession();
		}
	}*/
	
	protected void initJs(WebDriver driver) {
		try {
			if(driver instanceof JavascriptExecutor) {
				if("Success".equalsIgnoreCase((String)((JavascriptExecutor)driver).executeScript("return window.GatfUtil.check()"))) {
					return;
				}
			}
		} catch (Exception e) {
			try {
				((JavascriptExecutor)driver).executeScript(IOUtils.toString(getClass().getResourceAsStream("/gatf-js-util.js"), "UTF-8"));
			} catch (IOException e1) {
			}
		}
	}

	private static final Map<String, Object[]> NETWORK_INSPECTION = new ConcurrentHashMap<>();
	
	protected void networkApiInspectPre(WebDriver driver, String method, String url) {
		String sessionId = ((RemoteWebDriver)driver).getSessionId().toString();
		if(!NETWORK_INSPECTION.containsKey(sessionId)) {
			NETWORK_INSPECTION.put(sessionId, new Object[] {method, url, null, null});
		} else {
			throw new RuntimeException("Only one inspection allowed at a time, no nested or simultaneous interceptions allowed.");
		}
		
		final String murl = url.indexOf("?")!=-1?url.substring(0, url.indexOf("?")):url;
		if(driver instanceof HasDevTools) {
			DevTools devTools = ((HasDevTools)driver).getDevTools();
			
			devTools.createSession();
		
			RequestPattern rp = new RequestPattern(Optional.of("*"), Optional.of(ResourceType.XHR), Optional.of(RequestStage.RESPONSE));
			List<RequestPattern> lrp = new ArrayList<>();
			lrp.add(rp);
			devTools.send(Fetch.enable(Optional.of(lrp), Optional.empty()));
			
			System.out.println("Starting network inspection on CDP session " + devTools.getCdpSession().toString());
			devTools.addListener(Fetch.requestPaused(), requestPaused -> {
				System.out.println(String.format("Captured Network Response for [%s] -> %d", requestPaused.getRequestId().toString(), 
						requestPaused.getResponseStatusCode().get()));
				//requestPaused.getRequestId()
				String rurl = requestPaused.getRequest().getUrl();
				rurl = rurl.indexOf("?")!=-1?rurl.substring(0, rurl.indexOf("?")):rurl;
				if(requestPaused.getRequest().getMethod().equalsIgnoreCase(method) && rurl.equalsIgnoreCase(murl)) {
					System.out.println("Matched inspection request [" + method + "@" + murl + "] on CDP session " + devTools.getCdpSession().toString());
					List<HeaderEntry> headers = requestPaused.getResponseHeaders().get();
					String mimeType = null;
					Map<String, Object> headerMap = new HashMap<>();
					for(HeaderEntry he: headers) {
						if(he.getName().equalsIgnoreCase("content-type")) {
							mimeType = he.getValue();
							if(mimeType.indexOf(";")!=-1) {
								mimeType = mimeType.substring(0, mimeType.indexOf(";"));
							}
						}
						headerMap.put(he.getName(), he.getValue());
					}
					org.openqa.selenium.devtools.v108.fetch.Fetch.GetResponseBodyResponse firsb = devTools.send(Fetch.getResponseBody(requestPaused.getRequestId()));
					String body = firsb.getBody();
					if(firsb.getBase64Encoded()) {
						try {
							body = new String(org.apache.commons.codec.binary.Base64.decodeBase64(body), "UTF-8");
						} catch (UnsupportedEncodingException e) {
						}
					}
					NETWORK_INSPECTION.get(sessionId)[2] = requestPaused.getRequestId().toString();
					NETWORK_INSPECTION.get(sessionId)[3] = new Object[] {requestPaused.getResponseStatusCode().get(), headerMap, mimeType, body};
				}
				devTools.send(Fetch.continueResponse(requestPaused.getRequestId(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));
			});
		}
	}
	
	@SuppressWarnings("unchecked")
	protected void networkApiInspectPost(WebDriver driver, String v1, String v2) {
		String sessionId = ((RemoteWebDriver)driver).getSessionId().toString();
		if(/*BROWSER_FEATURES.containsKey(sessionId+".NETWORK_RES") && */NETWORK_INSPECTION.containsKey(sessionId)) {
			int counter = 0;
			while(NETWORK_INSPECTION.get(sessionId)[3]==null && counter++<60) {
				System.out.println("Waiting for network API response for ["+NETWORK_INSPECTION.get(sessionId)[0].toString() 
						+  "->" + NETWORK_INSPECTION.get(sessionId)[1].toString() + "]... attempt " + counter);
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
				}
			}
			if(NETWORK_INSPECTION.get(sessionId)[3]==null) {
				throw new RuntimeException("No valid API response detected for ["+NETWORK_INSPECTION.get(sessionId)[0].toString() 
						+  "->" + NETWORK_INSPECTION.get(sessionId)[1].toString() + "]");
			}
			if(NETWORK_INSPECTION.get(sessionId)[3]!=null) {
				Object[] res = (Object[])NETWORK_INSPECTION.get(sessionId)[3];
				int status = (Integer)res[0];
				Map<String, Object> headers = (Map<String, Object>)res[1];
				String mimeType = (String)res[2];
				String body = (String)res[3];
				switch (v1) {
					case "status":
						___cxt___add_param__("apiStatus", status);
						break;
					case "header":
						if(v2!=null && headers.containsKey(v2)) {
							___cxt___add_param__("apiHeader", headers.get(v2).toString());
						}
						break;
					case "json":
						if(v2!=null && mimeType!=null && mimeType.toLowerCase().startsWith("application/json")) {
							String[] params = v2.split(",");
							int cindex = 1;
							for (String param : params) {
								String[] det = param.split("@");
								String vname = null;
								String path = null;
								if(det.length==1) {
									vname = "apiJson" + cindex++;
									path = det[0].trim();
								} else {
									vname = det[0].trim();
									path = det[1].trim();
								}
								if(BROWSER_FEATURES.containsKey(sessionId+".NWCONSOLE") && BROWSER_FEATURES.get(sessionId+".NWCONSOLE")) {
									System.out.println("Fetching " + path + " from [" + body + "]");
								}
								___cxt___add_param__(vname, JsonPath.read(body, path));
							}
						}
						break;
					default:
						break;
				}
			}
			
			if(driver instanceof HasDevTools) {
				DevTools devTools = ((HasDevTools)driver).getDevTools();
				System.out.println("Closing network inspection on CDP session " + devTools.getCdpSession().toString());
				devTools.send(Fetch.disable());
				devTools.clearListeners();
				devTools.disconnectSession();
			}
			
			NETWORK_INSPECTION.remove(sessionId);
		}
	}
	
	protected void switchToFrame(WebDriver d, String selector) {
		if(StringUtils.isBlank(selector) || selector.trim().equalsIgnoreCase("main")) {
			d.switchTo().defaultContent();
			return;
		}
		
		if(selector.trim().equalsIgnoreCase("parent")) {
			d.switchTo().parentFrame();
			return;
		}
		
		if(selector.indexOf("@")!=-1) {
			List<WebElement> els = getElements(d, d, selector, null, null, null);
			if(els.size()>0) {
				d.switchTo().frame(els.get(0));
			}
		}
		
		try {
			d.switchTo().frame(Integer.parseInt(selector));
		} catch (NumberFormatException e) {
			d.switchTo().frame(selector);
		}
	}
	
	protected boolean handleAlertConfirm(WebDriver d, boolean isAlert, boolean confirmIsOk, String value) {
		int counter = 1;
		while(counter++<11) {
			try {
				Alert alert = d.switchTo().alert();
				if(isAlert) {
					alert.accept();
				} else {
					if(confirmIsOk) 
						alert.accept();
					else 
						alert.dismiss();
				}
				return value!=null?true:alert.getText().equals(value);
			} catch (Exception e) {
			}
			sleep(1000);
		}
		return false;
	}
	
	protected void setupFileVar(String regex, String[] vars, String[] pos) {
		Pattern pr = Pattern.compile(regex);
		Matcher m = pr.matcher(get__loopcontext__var("a1"));
		if(m.matches()) {
			for (int i=0;i<vars.length;i++) {
				String varv = pos[i];
				for (int j = 0; j < m.groupCount(); j++) {
					varv = varv.replace("${"+(j+1)+"}", m.group(j+1));
					varv = varv.replace("$"+(j+1), m.group(j+1));
				}
				if(vars[i].startsWith("@")) {
					___cxt___add_param__(vars[i], varv);
				} else {
					newProvider(get__loopcontext__var("n")+"_rf");
					set__provname__(get__loopcontext__var("n")+"_rf");
					set__provpos__(get__loopcontext__var("n")+"_rf", get__loopcontext__indx());
				}
			}
		}
	}
	
	public static interface UtilDateF {
		public String f();
	}
	public static interface UtilDateFS {
		public String f(int days, String fmt);
	}
	
	public static class Util {
		public static String random(String typstr) {
			return randomize(typstr);
		}
		
		private static String dateArith(String what, int howMuch, String fmt) {
			if(fmt==null) fmt = "dd/MM/yyyy HH:mm:ss";
			DateTime dt = new DateTime();
			if(what!=null) {
				switch(what) {
					case "h":
						dt = dt.plusHours(howMuch);
						break;
					case "mi":
						dt = dt.plusMinutes(howMuch);
						break;
					case "s":
						dt = dt.plusSeconds(howMuch);
						break;
					case "ms":
						dt = dt.plusMillis(howMuch);
						break;
					case "d":
						dt = dt.plusDays(howMuch);
						break;
					case "m":
						dt = dt.plusMonths(howMuch);
						break;
					case "w":
						dt = dt.plusWeeks(howMuch);
						break;
					case "y":
						dt = dt.plusYears(howMuch);
						break;
					default: break;
				}
			}
			return dt.toString(fmt);
		}
		
		public static String today() {
			return dateArith(null, 0, "dd/MM/yyyy");
		}
		public static String yesterday() {
			return dateArith("d", -1, "dd/MM/yyyy");
		}
		public static String tomorrow() {
			return dateArith("d", 1, "dd/MM/yyyy");
		}
		public static String someDay(int days, String fmt) {
			return dateArith("d", days, StringUtils.isNotBlank(fmt)?fmt:"dd/MM/yyyy");
		}

		public static String lastWeek() {
			return dateArith("w", -1, "dd/MM/yyyy");
		}
		public static String nextWeek() {
			return dateArith("w", 1, "dd/MM/yyyy");
		}
		public static String someWeek(int weeks, String fmt) {
			return dateArith("w", weeks, StringUtils.isNotBlank(fmt)?fmt:"dd/MM/yyyy");
		}
		
		public static String lastMonth() {
			return dateArith("m", -1, "dd/MM/yyyy");
		}
		public static String nextMonth() {
			return dateArith("m", 1, "dd/MM/yyyy");
		}
		public static String someMonth(int months, String fmt) {
			return dateArith("m", months, StringUtils.isNotBlank(fmt)?fmt:"dd/MM/yyyy");
		}
		
		public static String lastYear() {
			return dateArith("y", -1, "dd/MM/yyyy");
		}
		public static String nextYear() {
			return dateArith("y", 1, "dd/MM/yyyy");
		}
		public static String someYear(int years, String fmt) {
			return dateArith("y", years, StringUtils.isNotBlank(fmt)?fmt:"dd/MM/yyyy");
		}
		
		public static String now() {
			return dateArith(null, 0, "HH:mm:ss");
		}
		
		public static String lastSecond() {
			return dateArith("s", -1, "HH:mm:ss");
		}
		public static String nextSecond() {
			return dateArith("s", 1, "HH:mm:ss");
		}
		public static String someSecond(int secs, String fmt) {
			return dateArith("s", secs, StringUtils.isNotBlank(fmt)?fmt:"HH:mm:ss");
		}
		
		public static String lastMinute() {
			return dateArith("mi", -1, "HH:mm:ss");
		}
		public static String nextMinute() {
			return dateArith("mi", 1, "HH:mm:ss");
		}
		public static String someMinute(int mins, String fmt) {
			return dateArith("mi", mins, StringUtils.isNotBlank(fmt)?fmt:"HH:mm:ss");
		}
		
		public static String lastHour() {
			return dateArith("h", -1, "HH:mm:ss");
		}
		public static String nextHour() {
			return dateArith("h", 1, "HH:mm:ss");
		}
		public static String someHour(int hours, String fmt) {
			return dateArith("h", hours, StringUtils.isNotBlank(fmt)?fmt:"HH:mm:ss");
		}
		
		public static UtilDateF today = () -> {
			return dateArith(null, 0, "dd/MM/yyyy");
		};
		public static UtilDateF yesterday = () -> {
			return dateArith("d", -1, "dd/MM/yyyy");
		};
		public static UtilDateF tomorrow = () -> {
			return dateArith("d", 1, "dd/MM/yyyy");
		};
		public static UtilDateFS day = (int days, String fmt) -> {
			return dateArith("d", days, StringUtils.isNotBlank(fmt)?fmt:"dd/MM/yyyy");
		};

		public static UtilDateF lastWeek = () -> {
			return dateArith("w", -1, "dd/MM/yyyy");
		};
		public static UtilDateF nextWeek = () -> {
			return dateArith("w", 1, "dd/MM/yyyy");
		};
		public static UtilDateFS week = (int weeks, String fmt) -> {
			return dateArith("w", weeks, StringUtils.isNotBlank(fmt)?fmt:"dd/MM/yyyy");
		};
		
		public static UtilDateF lastMonth = () -> {
			return dateArith("m", -1, "dd/MM/yyyy");
		};
		public static UtilDateF nextMonth = () -> {
			return dateArith("m", 1, "dd/MM/yyyy");
		};
		public static UtilDateFS month = (int months, String fmt) -> {
			return dateArith("m", months, StringUtils.isNotBlank(fmt)?fmt:"dd/MM/yyyy");
		};
		
		public static UtilDateF lastYear = () -> {
			return dateArith("y", -1, "dd/MM/yyyy");
		};
		public static UtilDateF nextYear = () -> {
			return dateArith("y", 1, "dd/MM/yyyy");
		};
		public static UtilDateFS year = (int years, String fmt) -> {
			return dateArith("y", years, StringUtils.isNotBlank(fmt)?fmt:"dd/MM/yyyy");
		};
		
		public static UtilDateF now = () -> {
			return dateArith(null, 0, "HH:mm:ss");
		};
		
		public static UtilDateF lastSecond = () -> {
			return dateArith("s", -1, "HH:mm:ss");
		};
		public static UtilDateF nextSecond = () -> {
			return dateArith("s", 1, "HH:mm:ss");
		};
		public static UtilDateFS second = (int secs, String fmt) -> {
			return dateArith("s", secs, StringUtils.isNotBlank(fmt)?fmt:"HH:mm:ss");
		};
		
		public static UtilDateF lastMinute = () -> {
			return dateArith("mi", -1, "HH:mm:ss");
		};
		public static UtilDateF nextMinute = () -> {
			return dateArith("mi", 1, "HH:mm:ss");
		};
		public static UtilDateFS minute = (int mins, String fmt) -> {
			return dateArith("mi", mins, StringUtils.isNotBlank(fmt)?fmt:"HH:mm:ss");
		};
		
		public static UtilDateF lastHour = () -> {
			return dateArith("h", -1, "HH:mm:ss");
		};
		public static UtilDateF nextHour = () -> {
			return dateArith("h", 1, "HH:mm:ss");
		};
		public static UtilDateFS hour = (int hours, String fmt) -> {
			return dateArith("h", hours, StringUtils.isNotBlank(fmt)?fmt:"HH:mm:ss");
		};
		
		public static String g(UtilDateF f1, UtilDateF f2) {
			return g(f1, f2, " ");
		}
		public static String g(UtilDateF f1, UtilDateF f2, String del) {
			return StringUtils.joinWith(del, f1.f(), f2.f());
		}
		public static String g(UtilDateFS f1, int p1, String fmt1, UtilDateFS f2, int p2, String fmt2) {
			return g(f1, p1, fmt1, f2, p2, fmt2, " ");
		}
		public static String g(UtilDateFS f1, int p1, String fmt1, UtilDateFS f2, int p2, String fmt2, String del) {
			return StringUtils.joinWith(del, f1.f(p1, fmt1), f2.f(p2, fmt2));
		}
	}
}
