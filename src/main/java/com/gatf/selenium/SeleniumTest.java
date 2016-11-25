/*
    Copyright 2013-2016, Sumeet Chhetri
    
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

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;

import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.codehaus.jackson.map.ObjectMapper;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.logging.LogEntries;
import org.openqa.selenium.logging.LogEntry;
import org.openqa.selenium.logging.LogType;
import org.openqa.selenium.logging.LoggingPreferences;
import org.openqa.selenium.logging.Logs;
import org.openqa.selenium.support.ui.Select;

import com.gatf.executor.core.AcceptanceTestContext;

import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.ios.IOSDriver;
import io.selendroid.client.SelendroidDriver;

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
	
	private transient List<WebDriver> ___d___ = new ArrayList<WebDriver>();
	private transient int __wpos__ = 0;
	private transient Map<String, SeleniumResult> __result__ = new LinkedHashMap<String, SeleniumResult>();
	private transient Map<String, String> properties = null;
	
	public void addTest(String name) {
        if(!__result__.containsKey(name)) {
            SeleniumResult s = new SeleniumResult();
            s.name = name;
            __result__.put(name, s);
        } else {
            throw new RuntimeException("Duplicate browser defined");
        }
    }
	
	public void addSubTest(String name, String stname) {
	    if(__result__.containsKey(name)) {
	        if(!__result__.get(name).__cresult__.containsKey(stname)) {
	            __result__.get(name).__cresult__.put(stname, null);
	        } else {
	            throw new RuntimeException("Duplicate subtest defined");
	        }
	    } else {
	        throw new RuntimeException("Invalid browser specified");
	    }
	}
	
	public void pushResult(SeleniumTestResult result)
	{
	    if(__subtestname__==null) {
	        result.executionTime = System.nanoTime() - __teststarttime__;
	        __result__.get(browserName).result = result;
	    } else {
	        __result__.get(browserName).__cresult__.put(__subtestname__, result);
	    }
	}
	
	public void startTest() {
	    __teststarttime__ = System.nanoTime();
	}
	
	public void newProvider(String name) {
	    ___cxt___.newProvider(getPn(name));
	}
    
    public List<Map<String, String>> getProviderTestDataMap(String name) {
        return ___cxt___.getProviderTestDataMap().get(getPn(name));
    }
	
	//private transient String __provname__ = null;
	private transient String __subtestname__ = null;
    private transient long __subtestexecutiontime__ = 0L;
    private transient long __teststarttime__ = 0L;
	private transient Map<String, Integer> __provdetails__ = new LinkedHashMap<String, Integer>();
	
	private transient AcceptanceTestContext ___cxt___ = null;
    
	public static class SeleniumResult implements Serializable {
        private static final long serialVersionUID = 1L;
	    
        private String name;
        
        private SeleniumTestResult result;
        
        private Map<String, SeleniumTestResult>  __cresult__ = new LinkedHashMap<String, SeleniumTestResult>();

        public SeleniumTestResult getResult()
        {
            return result;
        }

        public Map<String,SeleniumTestResult> getSubTestResults()
        {
            return __cresult__;
        }

        public String getName()
        {
            return name;
        }
	}
	
	public String evaluate(String tmpl) {
	    if(tmpl.indexOf("$")==-1)return tmpl;
	    try
        {
	        if(__provdetails__.size()>0) {
	            ArrayList<String> keys = new ArrayList<String>(__provdetails__.keySet());
	            for (int i=keys.size()-1;i>=0;i--)
                {
	                String pn = keys.get(i);
	                Integer pp = __provdetails__.get(pn);
	                List<Map<String, String>> _t = ___cxt___.getProviderTestDataMap().get(pn);
	                tmpl = ___cxt___.getWorkflowContextHandler().templatize(_t.get(pp), tmpl);
                }
	        } else {
	            tmpl = ___cxt___.getWorkflowContextHandler().templatize(tmpl);
	        }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
	    return tmpl;
    }
	
	public String getProviderDataValue(String key) {
	    if(__provdetails__.size()>0) {
            ArrayList<String> keys = new ArrayList<String>(__provdetails__.keySet());
            for (int i=keys.size()-1;i>=0;i--)
            {
                String pn = keys.get(i);
                Integer pp = __provdetails__.get(pn);
                List<Map<String, String>> _t = ___cxt___.getProviderTestDataMap().get(pn);
                if(_t!=null && _t.get(pp)!=null && _t.get(pp).containsKey(key)) {
                    return _t.get(pp).get(key);
                }
            }
        }
	    return null;
	}
	
    public WebDriver get___d___()
    {
        if(___d___.size()==0)return null;
        return ___d___.get(__wpos__);
    }

    public void set___d___(WebDriver ___d___)
    {
        this.___d___.add(___d___);
    }
    
    public class PrettyPrintingMap<K, V> {
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

    public void ___cxt___print_provider__(String name)
    {
        System.out.println(___cxt___.getProviderTestDataMap().get(getPn(name)));
    }

    public void ___cxt___print_provider__json(String name)
    {
        try
        {
            System.out.println(new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(___cxt___.getProviderTestDataMap().get(getPn(name))));
        }
        catch (Exception e)
        {
        }
    }

    public AcceptanceTestContext get___cxt___()
    {
        return ___cxt___;
    }

    public void set___cxt___(AcceptanceTestContext ___cxt___)
    {
        this.___cxt___ = ___cxt___;
    }

    public Map<String, SeleniumResult> get__result__()
    {
        return __result__;
    }

    public void set__provname__(String __provname__)
    {
        this.__provdetails__.put(getPn(__provname__), -1);
    }

    public void set__provpos__(String __provname__, int __provpos__)
    {
        this.__provdetails__.put(getPn(__provname__), __provpos__);
    }

    public void rem__provname__(String __provname__)
    {
        this.__provdetails__.remove(getPn(__provname__));
    }

    public void set__subtestname__(String __subtestname__)
    {
        if(__subtestname__!=null) {
            __subtestexecutiontime__ = System.nanoTime();
            if(__provdetails__.size()>0) {
                String fstn = "";
                ArrayList<String> keys = new ArrayList<String>(__provdetails__.keySet());
                for (int i=keys.size()-1;i>=0;i--)
                {
                    String pn = keys.get(i);
                    Integer pp = __provdetails__.get(pn);
                    fstn += pn + "(" + pp + ") ";
                }
                __subtestname__ = fstn + __subtestname__;
            }
        } else {
            __subtestexecutiontime__ = System.nanoTime() - __subtestexecutiontime__;
            if(this.__subtestname__!=null) {
                __result__.get(browserName).__cresult__.get(this.__subtestname__).executionTime = __subtestexecutiontime__;
            }
        }
        this.__subtestname__ = __subtestname__;
    }

    public void setBrowserName(String browserName)
    {
        this.browserName = browserName;
    }

    protected WebDriver getWebDriver() {
        return ___d___.get(__wpos__);
    }
	
	protected AndroidDriver getAndroidDriver() {
	    if(___d___ instanceof AndroidDriver) {
	        return (AndroidDriver)___d___;
	    }
	    return null;
	}
    
    protected IOSDriver getIOSDriver() {
        if(___d___ instanceof IOSDriver) {
            return (IOSDriver)___d___;
        }
        return null;
    }
    
    protected SelendroidDriver getSelendroidDriver() {
        if(___d___ instanceof SelendroidDriver) {
            return (SelendroidDriver)___d___;
        }
        return null;
    }
	
	public SeleniumTest(String name, AcceptanceTestContext ___cxt___, int index) {
	    this.name = name;
	    this.___cxt___ = ___cxt___;
	    this.index = index;
	    //this.properties = ___cxt___.getGatfExecutorConfig().getSelDriverConfigMap().get(name).getProperties();
	}
	
	public static interface Functor<I, O> {
	    O f(I i, I j);
	}
	
	protected final Map<String, Object[]> internalTestRs = new HashMap<String,Object[]>();
	
	protected String name;
	
	protected int index;
	
	protected String browserName;
	
	public void quit() {
	    if(___d___.size()>0) {
	        for (WebDriver d : ___d___)
            {
                d.quit();
            }
	    }
	}
    
    public abstract void close();
    
    public abstract SeleniumTest copy(AcceptanceTestContext ctx, int index);
	
	public abstract Map<String, SeleniumResult> execute(LoggingPreferences ___lp___) throws Exception;
	
	public static class SeleniumTestResult implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private Map<String, SerializableLogEntries> logs = new HashMap<String, SerializableLogEntries>();;
        
        private boolean status;
        
        private long executionTime;

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
            this.internalTestRes = test.internalTestRs;
            Logs logs = d.manage().logs();
            for (String s : LOG_TYPES_SET) {
                if(!logs.getAvailableLogTypes().contains(s))continue;
                LogEntries logEntries = logs.get(s);
                if(logEntries!=null && !logEntries.getAll().isEmpty()) {
                    this.logs.put(s, new SerializableLogEntries(logEntries.getAll())); 
                }
            }
        }
        public SeleniumTestResult(WebDriver d, SeleniumTest test, Throwable cause, LoggingPreferences ___lp___) {
            this.status = false;
            this.internalTestRes = test.internalTestRs;
            Logs logs = d.manage().logs();
            for (String s : LOG_TYPES_SET) {
                if(!logs.getAvailableLogTypes().contains(s))continue;
                LogEntries logEntries = logs.get(s);
                if(logEntries!=null && !logEntries.getAll().isEmpty()) {
                    this.logs.put(s, new SerializableLogEntries(logEntries.getAll())); 
                }
            }
            List<LogEntry> entries = new ArrayList<LogEntry>();
            entries.add(new LogEntry(Level.ALL, new Date().getTime(), cause.getMessage()));
            entries.add(new LogEntry(Level.ALL, new Date().getTime(), ExceptionUtils.getStackTrace(cause)));
            this.logs.put("gatf", new SerializableLogEntries(entries));
        }
        public SeleniumTestResult(SeleniumTest test, Throwable cause) {
            this.status = false;
            this.internalTestRes = test.internalTestRs;
            List<LogEntry> entries = new ArrayList<LogEntry>();
            entries.add(new LogEntry(Level.ALL, new Date().getTime(), cause.getMessage()));
            entries.add(new LogEntry(Level.ALL, new Date().getTime(), ExceptionUtils.getStackTrace(cause)));
            this.logs.put("gatf", new SerializableLogEntries(entries));
        }
	}
	
	public static void randomize(List<WebElement> le, String v1, String v2, String v3) {
	    if ((le.get(0).getTagName().toLowerCase().matches("input") && le.get(0).getAttribute("type").toLowerCase().matches("text|url|email|hidden"))
	            || (le.get(0).getTagName().toLowerCase().matches("textarea"))) {
	        int count = 10;
	        if(v3!=null) {
	            try {
	                count = Integer.parseInt(v3);
	            } catch (Exception e) {
	            }
	        }
	        if(v1==null || v1.toLowerCase().equals("alpha")) {
	            le.get(0).sendKeys(RandomStringUtils.randomAlphabetic(count));
	        } else if(v1.toLowerCase().equals("alphanumeric")) {
	            le.get(0).sendKeys(RandomStringUtils.randomAlphanumeric(count));
	        } else if(v1.toLowerCase().equals("numeric")) {
	            le.get(0).sendKeys(RandomStringUtils.randomNumeric(count));
	        } else if(v1.toLowerCase().equals("value") && v2!=null) {
	            le.get(0).sendKeys(v2);
	        }
	    } else if (le.get(0).getTagName().toLowerCase().matches("input") && le.get(0).getAttribute("type").toLowerCase().matches("number")) {
	        int min = 1;
	        int max = 99999;
	        if(v1!=null) {
	            try {
	                min = Integer.parseInt(v1);
	            } catch (Exception e) {
	            }
	        }
	        if(v2!=null) {
	            try {
	                max = Integer.parseInt(v2);
	            } catch (Exception e) {
	            }
	        }
	        int num = (int)(min + (Math.random() * (max - min)));
	        le.get(0).sendKeys(num+"");
	    } else if (le.get(0).getTagName().toLowerCase().matches("select")) {
	        randomizeSelect(le);
	    } else if (le.get(0).getTagName().toLowerCase().matches("input") && le.get(0).getAttribute("type").toLowerCase().matches("checkbox")) {
	        le.get(0).click();
	    } else if (le.get(0).getTagName().toLowerCase().matches("input") && le.get(0).getAttribute("type").toLowerCase().matches("radio")) {
	        le.get(0).click();
	    }
	}
	
	public void window(int pos) {
	    if(pos>=0 && pos<___d___.size()) {
	        __wpos__ = pos;
	    } else {
	        throw new RuntimeException("Invalid window number specified");
	    }
	}
	
	public void newWindow(LoggingPreferences lp) {
	    try
        {
            Method m = getClass().getMethod("setupDriver"+browserName, new Class[]{LoggingPreferences.class});
            if(m!=null) {
                m.invoke(this, new Object[]{lp});
            }
            __wpos__++;
        }
        catch (Exception e)
        {
            throw new RuntimeException("Invalid browser name specified");
        }
	}
	
	public static void randomizeSelect(List<WebElement> le) {
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
}
