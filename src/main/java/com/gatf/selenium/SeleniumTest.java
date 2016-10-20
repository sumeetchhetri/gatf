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

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.codehaus.jackson.map.ObjectMapper;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.logging.LogEntries;
import org.openqa.selenium.logging.LogEntry;
import org.openqa.selenium.logging.LogType;
import org.openqa.selenium.logging.LoggingPreferences;
import org.openqa.selenium.logging.Logs;

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
	
	private transient WebDriver ___d___ = null;
	private transient Map<String, SeleniumResult> __result__ = new LinkedHashMap<String, SeleniumResult>();
	
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
	
	private transient String __provname__ = null;
	private transient String __subtestname__ = null;
    private transient long __subtestexecutiontime__ = 0L;
    private transient long __teststarttime__ = 0L;
	private transient int __provpos__ = -1;
	
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
	        if(__provname__!=null && __provpos__>=0) {
	            List<Map<String, String>> _t = ___cxt___.getProviderTestDataMap().get(__provname__);
	            tmpl = ___cxt___.getWorkflowContextHandler().templatize(_t.get(__provpos__), tmpl);
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
	
    public WebDriver get___d___()
    {
        return ___d___;
    }

    public void set___d___(WebDriver ___d___)
    {
        this.___d___ = ___d___;
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

    public void ___cxt___print_provider__(String name)
    {
        System.out.println(___cxt___.getProviderTestDataMap().get(name));
    }

    public void ___cxt___print_provider__json(String name)
    {
        try
        {
            System.out.println(new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(___cxt___.getProviderTestDataMap().get(name)));
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
        this.__provname__ = __provname__;
    }

    public void set__provpos__(int __provpos__)
    {
        this.__provpos__ = __provpos__;
    }

    public void set__subtestname__(String __subtestname__)
    {
        if(__subtestname__!=null) {
            __subtestexecutiontime__ = System.nanoTime();
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
        return ___d___;
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
	
	public SeleniumTest(String name, AcceptanceTestContext ___cxt___) {
	    this.name = name;
	    this.___cxt___ = ___cxt___;
	}
	
	public static interface Functor<I, O> {
	    O f(I i, I j);
	}
	
	protected final Map<String, Object[]> internalTestRs = new HashMap<String,Object[]>();
	
	protected String name;
	
	protected String browserName;
	
	public abstract void quit();
	
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
}
