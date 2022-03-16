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
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.Charset;
import java.security.Security;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.Point;
import org.openqa.selenium.Rectangle;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriver.TargetLocator;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.logging.LogEntry;
import org.openqa.selenium.logging.LogType;
import org.openqa.selenium.logging.LoggingPreferences;
import org.openqa.selenium.remote.Augmenter;
import org.openqa.selenium.remote.RemoteWebElement;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.gatf.executor.core.AcceptanceTestContext;
import com.gatf.executor.core.GatfExecutorConfig;
import com.gatf.executor.core.WorkflowContextHandler;
import com.gatf.executor.report.RuntimeReportUtil;
import com.gatf.selenium.Command.GatfSelCodeParseError;
import com.gatf.selenium.SeleniumTestSession.SeleniumResult;
import com.google.common.io.Resources;

import io.appium.java_client.AppiumDriver;
import io.appium.java_client.MobileDriver;
import io.appium.java_client.MultiTouchAction;
import io.appium.java_client.TouchAction;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.ios.IOSDriver;
import io.selendroid.client.SelendroidDriver;
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

	private static final String TOP_LEVEL_PROV_NAME = UUID.randomUUID().toString();

	private transient AcceptanceTestContext ___cxt___ = null;

	List<SeleniumTestSession> sessions = new ArrayList<SeleniumTestSession>();

	protected String name;

	protected transient int sessionNum = -1;

	protected int index;

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
		s.sessionName = sessionName;
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
		} else {
			getSession().__result__.get(getSession().browserName).__cresult__.put(getSession().__subtestname__, result);
			RuntimeReportUtil.addEntry(index, result.status);
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

	protected void addToTopLevelProviderTestDataMap(String name, String value) {
		getSession().providerTestDataMap.get(TOP_LEVEL_PROV_NAME).get(0).put(name, value);
	}

	protected List<Map<String, String>> getProviderTestDataMap(String name) {
		return getAllProviderData(getPn(name));
	}

	protected String getFileProviderHash(String name) {
		return getFileProviderHash(name);
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
					tmpl = ___cxt___.getWorkflowContextHandler().templatize(_mt, tmpl);
				}
			} else {
				Map<String, Object> _mt = getFinalDataMap(null, null);
				if(tmpl.indexOf("$v{")!=-1) {
					tmpl = tmpl.replace("$v", "$");
					_mt.putAll(getSession().__vars__);
				}
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
				tmpl = ___cxt___.getWorkflowContextHandler().templatize(_mt, tmpln);
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		return tmpl;
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
		if(getSession().__vars__.containsKey(name)) {
			throw new RuntimeException("Variable " + name + " redefined");
		}
		getSession().__vars__.put(name, val);
	}

	protected Object ___get_var__(String name) {
		if(!getSession().__vars__.containsKey(name)) {
			throw new RuntimeException("Variable " + name + " not defined");
		}
		return getSession().__vars__.get(name);
	}

	protected void addSubTest(String browserName, String stname) {
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
		___d___.manage().timeouts().pageLoadTimeout(100, TimeUnit.SECONDS);
		getSession().___d___.add(___d___);
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

	protected WebDriver getWebDriver() {
		return getSession().___d___.get(getSession().__wpos__);
	}

	@SuppressWarnings("rawtypes")
	protected AndroidDriver getAndroidDriver() {
		if(getSession().___d___ instanceof AndroidDriver) {
			return (AndroidDriver)getSession().___d___;
		}
		return null;
	}

	@SuppressWarnings("rawtypes")
	protected IOSDriver getIOSDriver() {
		if(getSession().___d___ instanceof IOSDriver) {
			return (IOSDriver)getSession().___d___;
		}
		return null;
	}

	protected SelendroidDriver getSelendroidDriver() {
		if(getSession().___d___ instanceof SelendroidDriver) {
			return (SelendroidDriver)getSession().___d___;
		}
		return null;
	}

	public SeleniumTest(String name, AcceptanceTestContext ___cxt___, int index) {
		this.name = name;
		this.___cxt___ = ___cxt___;
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

	public void quit() {
		if(getSession().___d___.size()>0) {
			for (WebDriver d : getSession().___d___)
			{
				d.quit();
			}
		}
	}

	public void quitAll() {
		for (SeleniumTestSession s : sessions) {
			if(s.___d___.size()>0) {
				for (WebDriver d : s.___d___)
				{
					d.quit();
				}
			}
		}
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

		private Map<String, SerializableLogEntries> logs = new HashMap<String, SerializableLogEntries>();;

		private boolean status;

		long executionTime;

		private Map<String, Object[]> internalTestRes = new HashMap<String, Object[]>();

		public Map<String,SerializableLogEntries> getLogs()
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
		public Map<String,Object[]> getInternalTestRes()
		{
			return internalTestRes;
		}
		public SeleniumTestResult(WebDriver d, SeleniumTest test, LoggingPreferences ___lp___)
		{
			this.status = true;
			this.internalTestRes = test.getSession().internalTestRs;
			/*Logs logs = d.manage().logs();
            for (String s : LOG_TYPES_SET) {
                if(!logs.getAvailableLogTypes().contains(s))continue;
                LogEntries logEntries = logs.get(s);
                if(logEntries!=null && !logEntries.getAll().isEmpty()) {
                    this.logs.put(s, new SerializableLogEntries(logEntries.getAll())); 
                }
            }*/
		}
		public SeleniumTestResult(WebDriver d, SeleniumTest test, Throwable cause, LoggingPreferences ___lp___) {
			this.status = false;
			this.internalTestRes = test.getSession().internalTestRs;
			/*Logs logs = d.manage().logs();
            for (String s : LOG_TYPES_SET) {
                if(!logs.getAvailableLogTypes().contains(s))continue;
                LogEntries logEntries = logs.get(s);
                if(logEntries!=null && !logEntries.getAll().isEmpty()) {
                    this.logs.put(s, new SerializableLogEntries(logEntries.getAll())); 
                }
            }*/
			List<LogEntry> entries = new ArrayList<LogEntry>();
			entries.add(new LogEntry(Level.ALL, new Date().getTime(), cause.getMessage()));
			entries.add(new LogEntry(Level.ALL, new Date().getTime(), ExceptionUtils.getStackTrace(cause)));
			this.logs.put("gatf", new SerializableLogEntries(entries));
		}
		public SeleniumTestResult(SeleniumTest test, Throwable cause) {
			this.status = false;
			this.internalTestRes = test.getSession().internalTestRs;
			List<LogEntry> entries = new ArrayList<LogEntry>();
			entries.add(new LogEntry(Level.ALL, new Date().getTime(), cause.getMessage()));
			entries.add(new LogEntry(Level.ALL, new Date().getTime(), ExceptionUtils.getStackTrace(cause)));
			this.logs.put("gatf", new SerializableLogEntries(entries));
		}
	}

	protected static void randomize(List<WebElement> le, String v1, String v2, String v3) {
		if ((le.get(0).getTagName().toLowerCase().matches("input") && le.get(0).getAttribute("type").toLowerCase().matches("text|url|email|hidden"))
				|| (le.get(0).getTagName().toLowerCase().matches("textarea"))) {
			int count = 10, totalcount = 1;
			if(StringUtils.isNotBlank(v3)) {
				try {
					totalcount = Integer.parseInt(v3);
				} catch (Exception e) {
				}
			}
			if(v1.toLowerCase().equals("alpha") || v1.toLowerCase().equals("alphanumeric") || v1.toLowerCase().equals("numeric")) {
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
			le.get(0).sendKeys(StringUtils.join(vals, " "));
		} else if (le.get(0).getTagName().toLowerCase().matches("input") && le.get(0).getAttribute("type").toLowerCase().matches("number")) {
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
		} else if (le.get(0).getTagName().toLowerCase().matches("select")) {
			randomizeSelect(le);
		} else if (le.get(0).getTagName().toLowerCase().matches("input") && le.get(0).getAttribute("type").toLowerCase().matches("checkbox")) {
			le.get(0).click();
		} else if (le.get(0).getTagName().toLowerCase().matches("input") && le.get(0).getAttribute("type").toLowerCase().matches("radio")) {
			le.get(0).click();
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

	@SuppressWarnings("rawtypes")
	protected void mzoompinch(Object ele, int x, int y, boolean isZoom) {
		try
		{
			if(ele==null) {
				int leftX = x;
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
				TouchAction action1 = new TouchAction((MobileDriver)get___d___());

				if(isZoom) {
					action0.press(bX, bY).waitAction(java.time.Duration.ofMillis(1000)).moveTo(aX, aY).release();
					action1.press(dX, dY).waitAction(java.time.Duration.ofMillis(1000)).moveTo(cX, cY).release();
				} else {
					action0.press(aX, aY).waitAction(java.time.Duration.ofMillis(1000)).moveTo(bX, bY).release();
					action1.press(cX, cY).waitAction(java.time.Duration.ofMillis(1000)).moveTo(dX, dY).release();
				}

				MultiTouchAction mAction = new MultiTouchAction((MobileDriver)get___d___());
				mAction.add(action0).add(action1).perform();
			} else if(ele instanceof WebElement) {
				int leftX = ((WebElement)ele).getLocation().getX();
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
				TouchAction action1 = new TouchAction((MobileDriver)get___d___());

				if(isZoom) {
					action0.press(bX, bY).waitAction(java.time.Duration.ofMillis(1000)).moveTo(aX, aY).release();
					action1.press(dX, dY).waitAction(java.time.Duration.ofMillis(1000)).moveTo(cX, cY).release();
				} else {
					action0.press(aX, aY).waitAction(java.time.Duration.ofMillis(1000)).moveTo(bX, bY).release();
					action1.press(cX, cY).waitAction(java.time.Duration.ofMillis(1000)).moveTo(dX, dY).release();
				}

				MultiTouchAction mAction = new MultiTouchAction((MobileDriver)get___d___());
				mAction.add(action0).add(action1).perform();
			}
		}
		catch (Exception e)
		{
		}
	}

	protected static void randomizeSelect(List<WebElement> le) {
		try
		{
			Select s = new Select(le.get(0));
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

	@SuppressWarnings("serial")
	private static List<WebElement> getElements(WebDriver d, SearchContext sc, String finder) {
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
		} else if(by.equalsIgnoreCase("cssselector") || by.equalsIgnoreCase("css")) {
			el = By.cssSelector(classifier).findElements(sc);
		} else if(by.equalsIgnoreCase("text")) {
			el = By.xpath("//*[contains(text(), '" + classifier+"']").findElements(sc);
		} else if(by.equalsIgnoreCase("linkText")) {
			el = By.linkText(classifier).findElements(sc);
		} else if(by.equalsIgnoreCase("partialLinkText")) {
			el = By.partialLinkText(classifier).findElements(sc);
		} else if(by.equalsIgnoreCase("active")) {
			el = new ArrayList<WebElement>(){{add(((TargetLocator)d).activeElement());}};
		}
		return el;
	}

	protected void uploadFile(List<WebElement> ret, String filePath) {
		ret.get(0).click();
		java.awt.datatransfer.StringSelection ss = new java.awt.datatransfer.StringSelection(filePath);
		java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(ss, null);
		try {
			java.awt.Robot robot21 = new java.awt.Robot();
			robot21.keyPress(java.awt.event.KeyEvent.VK_CONTROL);
			robot21.keyPress(java.awt.event.KeyEvent.VK_V);
			robot21.keyRelease(java.awt.event.KeyEvent.VK_V);
			robot21.keyRelease(java.awt.event.KeyEvent.VK_CONTROL);
			Thread.sleep(1000);
			robot21.keyPress(java.awt.event.KeyEvent.VK_ENTER);
			robot21.keyRelease(java.awt.event.KeyEvent.VK_ENTER);
		} catch (AWTException e) {
		} catch (InterruptedException e) {
		}
	}

	private void elementAction(List<WebElement> ret, String action, String tvalue) {
		if(action.equalsIgnoreCase("click")) {
			for(final WebElement we: ret) {
				we.click();
				break;
			}
		} else if(action.equalsIgnoreCase("hover")) {
			for(final WebElement we: ret) {
				Actions ac = new Actions(get___d___());
				ac.moveToElement(we).perform();
				break;
			}
		} else if(action.equalsIgnoreCase("hoverandclick")) {
			for(final WebElement we: ret) {
				Actions ac = new Actions(get___d___());
				ac.moveToElement(we).click().perform();
				break;
			}
		} else if(action.equalsIgnoreCase("clear")) {
			for(final WebElement we: ret) {
				we.clear();
				change(we);
				break;
			}
		} else if(action.equalsIgnoreCase("submit")) {
			for(final WebElement we: ret) {
				we.submit();
				break;
			}
		} else if(action.equalsIgnoreCase("type") || action.equalsIgnoreCase("sendkeys")) {
			for(final WebElement we: ret) {
				System.out.println("Type => " + tvalue);
				we.sendKeys(tvalue);
				change(we);
				break;
			}
		} else if(action.equalsIgnoreCase("typenb") || action.equalsIgnoreCase("sendkeysnb")) {
			for(final WebElement we: ret) {
				System.out.println("Type => " + tvalue);
				we.sendKeys(tvalue);
				//change(we);
				break;
			}
		} else if(action.equalsIgnoreCase("upload")) {
			uploadFile(ret, tvalue);
		} else if(action.equalsIgnoreCase("select")) {

		} else if(action.equalsIgnoreCase("chord")) {
			for(final WebElement we: ret) {
				we.sendKeys(Keys.chord(tvalue));
				change(we);
				break;
			}
		} else if(action.equalsIgnoreCase("chordnb")) {
			for(final WebElement we: ret) {
				we.sendKeys(Keys.chord(tvalue));
				//change(we);
				break;
			}
		} else if(action.equalsIgnoreCase("dblclick") || action.equalsIgnoreCase("doubleclick")) {
			for(final WebElement we: ret) {
				Actions ac = new Actions(get___d___());
				ac.moveToElement(we).doubleClick().perform();
				break;
			}
		}
	}
	
	protected void change(WebElement we) {
		//get___d___().findElement(By.tagName("body"))
		((JavascriptExecutor)get___d___()).executeScript("arguments[0].dispatchEvent(new Event('change'));", we);
	}
	
	protected void blur(WebElement we) {
		//get___d___().findElement(By.tagName("body"))
		((JavascriptExecutor)get___d___()).executeScript("arguments[0].blur();", we);
	}

	@SuppressWarnings("unchecked")
	protected List<WebElement> handleWaitFunc(final SearchContext sc, final List<WebElement> ce, final long timeOutInSeconds, final String classifier, final String by, String subselector, 
			boolean byselsame, String value, String[] values, String action, String oper, String tvalue, String exmsg, boolean noExcep, String ... layers) {
		return (List<WebElement>)handleWaitOrTransientProv(sc, ce, timeOutInSeconds, classifier, by, subselector, byselsame, value, values, action, oper, tvalue, exmsg, noExcep, layers);
	}

	@SuppressWarnings("unchecked")
	protected List<String[]> transientProviderData(final SearchContext sc, final List<WebElement> ce, final long timeOutInSeconds, final String classifier, final String by, String subselector, 
			boolean byselsame, String value, String[] values, String action, String oper, String tvalue, String exmsg, boolean noExcep, String ... layers) {
		return (List<String[]>)handleWaitOrTransientProv(sc, ce, timeOutInSeconds, classifier, by, subselector, byselsame, value, values, action, oper, tvalue, exmsg, noExcep, layers);
	}

	@SuppressWarnings("unchecked")
	private Object handleWaitOrTransientProv(final SearchContext sc, final List<WebElement> ce, final long timeOutInSeconds, final String classifier, final String by, String subselector, 
			boolean byselsame, String value, String[] values, final String action, String oper, String tvalue, String exmsg, boolean noExcep, String ... layers) {
		final WebDriver wsc = (WebDriver) sc;
		final Object[] o = new Object[2];
		final SeleniumTest test = this;
		long timeoutRemaining = 0;
		System.out.println("Searching element => " + by+"@"+classifier);
		if(timeOutInSeconds<=0) {
			List<WebElement> el = getElements(get___d___(), wsc, by+"@"+classifier);
			if (el == null || el.isEmpty())  {
			} else {
				boolean enabledCheck = false;
				if(action!=null && (action.equalsIgnoreCase("click") || action.equalsIgnoreCase("type") 
						|| action.equalsIgnoreCase("sendkeys") || action.equalsIgnoreCase("chord") 
						|| action.equalsIgnoreCase("typenb") || action.equalsIgnoreCase("sendkeysnb") 
						|| action.equalsIgnoreCase("chordnb"))) {
					enabledCheck = true;
				}

				WebElement elo = elementInteractable(test, el.get(0), enabledCheck, layers).apply(get___d___());
				if(elo!=null) {
					o[0] = el;
					o[1] = ce;
				}
			}
		} else {
			long start = System.currentTimeMillis();
			try {
				(new WebDriverWait(wsc, timeOutInSeconds)).until(new Function<WebDriver, Boolean>() {
					public Boolean apply(WebDriver input) {
						List<WebElement> ___ce___ = ce;
						try {
							List<WebElement> el = getElements(get___d___(), wsc, by+"@"+classifier);
							if (el == null || el.isEmpty()) return false;

							boolean enabledCheck = false;
							if(action!=null && (action.equalsIgnoreCase("click") || action.equalsIgnoreCase("type") 
									|| action.equalsIgnoreCase("sendkeys") || action.equalsIgnoreCase("chord") 
									|| action.equalsIgnoreCase("typenb") || action.equalsIgnoreCase("sendkeysnb") 
									|| action.equalsIgnoreCase("chordnb"))) {
								enabledCheck = true;
							}

							WebElement elo = elementInteractable(test, el.get(0), enabledCheck, layers).apply(get___d___());
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
			timeoutRemaining = timeOutInSeconds - (System.currentTimeMillis() - start)/1000;
			//System.out.println("Timeout remaining = " + timeoutRemaining);
		}
		List<WebElement> ret = (List<WebElement>)o[0];
		Object resp = ret;
		boolean flag = true;
		if(ret!=null) {
			if((StringUtils.isNotBlank(value) || (values!=null && values.length>0)) && subselector!=null && !subselector.isEmpty()) {
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
							rhs = String.valueOf(get___d___().manage().window().getSize().getWidth());
						} else if(subselector.equalsIgnoreCase("height")) {
							rhs = String.valueOf(get___d___().manage().window().getSize().getHeight());
						} else if(subselector.equalsIgnoreCase("xpos")) {
							rhs = String.valueOf(get___d___().manage().window().getPosition().getX());
						} else if(subselector.equalsIgnoreCase("ypos")) {
							rhs = String.valueOf(get___d___().manage().window().getPosition().getY());
						} else if(subselector.equalsIgnoreCase("alerttext")) {
							rhs = String.valueOf(get___d___().switchTo().alert().getText());
						}

						if(rhs!=null) {
							if(oper.startsWith("<=")) {
								flag &= value.compareTo(rhs)<=0;
							} else if(oper.startsWith(">=")) {
								flag &= value.compareTo(rhs)>=0;
							} else if(oper.startsWith("=")) {
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
					}

					for(final WebElement we: ret) {
						String rhs = null;
						if(subselector.equalsIgnoreCase("text")) {
							rhs = we.getText();
						} else if(subselector.equalsIgnoreCase("tagname")) {
							rhs = we.getTagName();
						} else if(subselector.toLowerCase().startsWith("attr@")) {
							rhs = we.getAttribute(value);
						} else if(subselector.toLowerCase().startsWith("cssvalue@")) {
							rhs = we.getCssValue(value);
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
							} else if(oper.startsWith("=")) {
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
				} else if(values!=null && values.length>0) {
					for(final WebElement we: ret) {
						String rhs = null;
						if(subselector.equalsIgnoreCase("text")) {
							rhs = we.getText();
						} else if(subselector.equalsIgnoreCase("tagname")) {
							rhs = we.getTagName();
						} else if(subselector.toLowerCase().startsWith("attr@")) {
							rhs = we.getAttribute(value);
						} else if(subselector.toLowerCase().startsWith("cssvalue@")) {
							rhs = we.getCssValue(value);
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
			} else if(action!=null) {
				try {
					elementAction(ret, action, tvalue);
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
								Thread.sleep(1000);
								System.out.println("WDE-Retrying operation.....Timeout remaining = " + (timeoutRemaining-1) + " secs");
								elementAction(ret, action, tvalue);
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
								Thread.sleep(1000);
								System.out.println("E-Retrying operation.....Timeout remaining = " + (timeoutRemaining-1) + " secs");
								elementAction(ret, action, tvalue);
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

	private static Rectangle getRect(WebElement e) {
		return new Rectangle(e.getLocation().getX(), e.getLocation().getY(), e.getSize().getWidth(), e.getSize().getHeight());
	}

	public static ExpectedCondition<WebElement> elementInteractable(SeleniumTest test, WebElement element, boolean enabledCheck, String ... layers) {
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
						for (String layer : layers) {
							try {
								List<WebElement> el = getElements(test.get___d___(), test.get___d___(), layer);
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
		return Boolean.TRUE.equals(((JavascriptExecutor)driver)
				.executeScript("return arguments[0].offsetParent!==undefined && arguments[0].offsetParent!==null", element));
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

	protected void screenshotAsFile(WebDriver webDriver, String filepath) throws IOException {
		if(webDriver instanceof AppiumDriver) {
			File sc = ((TakesScreenshot)new Augmenter().augment(webDriver)).getScreenshotAs(OutputType.FILE);
			FileUtils.copyFile(sc, new File(filepath));
		} else {
			Screenshot fpScreenshot = new AShot().shootingStrategy(ShootingStrategies.viewportPasting(1000)).takeScreenshot(webDriver);
			BufferedImage originalImage = fpScreenshot.getImage();
			ImageIO.write(originalImage, "png", new FileOutputStream(filepath));
		}
	}

	protected void elementScreenshotAsFile(WebDriver webDriver, WebElement element, String filepath) throws IOException {
		if(webDriver instanceof AppiumDriver && element instanceof RemoteWebElement) {
			File sc = ((TakesScreenshot)new Augmenter().augment((RemoteWebElement)element)).getScreenshotAs(OutputType.FILE);
			FileUtils.copyFile(sc, new File(filepath));
		} else {
			Screenshot fpScreenshot = new AShot().takeScreenshot(webDriver, element);
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
            if(selConf.getDriverName()!=null) {
                System.setProperty(selConf.getDriverName(), selConf.getPath());
            }
        }
        
        System.setProperty("jdk.tls.client.protocols", "TLSv1,TLSv1.1,TLSv1.2");
        Security.setProperty("crypto.policy", "unlimited");
        
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
}
