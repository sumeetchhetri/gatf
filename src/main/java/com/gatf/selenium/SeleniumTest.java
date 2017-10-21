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

import java.io.File;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
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
import com.google.common.io.Resources;

import io.appium.java_client.MobileDriver;
import io.appium.java_client.MultiTouchAction;
import io.appium.java_client.TouchAction;
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
        return ___cxt___.getAnyProviderData(getPn(name), null);
    }

    //private transient String __provname__ = null;
    private transient String __subtestname__ = null;
    private transient long __subtestexecutiontime__ = 0L;
    private transient long __teststarttime__ = 0L;
    private transient Map<String, Integer> __provdetails__ = new LinkedHashMap<String, Integer>();

    private transient AcceptanceTestContext ___cxt___ = null;
    private transient Map<String, Object> __vars__ = new LinkedHashMap<String, Object>();

    public void ___add_var__(String name, Object val) {
        if(__vars__.containsKey(name)) {
            throw new RuntimeException("Variable " + name + " redefined");
        }
        __vars__.put(name, val);
    }

    protected Object ___get_var__(String name) {
        if(!__vars__.containsKey(name)) {
            throw new RuntimeException("Variable " + name + " not defined");
        }
        return __vars__.get(name);
    }

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
                    List<Map<String, String>> _t = ___cxt___.getAnyProviderData(pn, null);
                    Map<String, Object> _mt = new HashMap<String, Object>();
                    _mt.putAll(___cxt___.getWorkflowContextHandler().getGlobalSuiteAndTestLevelParameters(null, _t.get(pp), index));
                    if(tmpl.indexOf("$v{")!=-1) {
                        tmpl = tmpl.replace("$v", "$");
                        _mt.putAll(__vars__);
                    }
                    tmpl = ___cxt___.getWorkflowContextHandler().templatize(_mt, tmpl);
                }
            } else {
                Map<String, Object> _mt = new HashMap<String, Object>();
                _mt.putAll(___cxt___.getWorkflowContextHandler().getGlobalSuiteAndTestLevelParameters(null, null, index));
                if(tmpl.indexOf("$v{")!=-1) {
                    tmpl = tmpl.replace("$v", "$");
                }
                tmpl = ___cxt___.getWorkflowContextHandler().templatize(_mt, tmpl);
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        return tmpl;
    }

    public String getProviderDataValue(String key, boolean isVar) {
        if(isVar) {
            Object o = ___get_var__(key);
            if(o!=null && o instanceof String) {
                return o.toString();
            }
            return null;
        }
        if(__provdetails__.size()>0) {
            ArrayList<String> keys = new ArrayList<String>(__provdetails__.keySet());
            for (int i=keys.size()-1;i>=0;i--)
            {
                String pn = keys.get(i);
                Integer pp = __provdetails__.get(pn);
                List<Map<String, String>> _t = ___cxt___.getAnyProviderData(pn, null);
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

    public Object getProviderDataValueO(String key, boolean isVar) {
        if(isVar) {
            return ___get_var__(key);
        }
        if(__provdetails__.size()>0) {
            ArrayList<String> keys = new ArrayList<String>(__provdetails__.keySet());
            for (int i=keys.size()-1;i>=0;i--)
            {
                String pn = keys.get(i);
                Integer pp = __provdetails__.get(pn);
                List<Map<String, String>> _t = ___cxt___.getAnyProviderData(pn, null);
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

    public WebDriver get___d___()
    {
        if(___d___.size()==0)return null;
        return ___d___.get(__wpos__);
    }

    public void set___d___(WebDriver ___d___)
    {
        ___d___.manage().timeouts().pageLoadTimeout(100, TimeUnit.SECONDS);
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

    public void ___cxt___add_param__(String name, Object value)
    {
        ___cxt___.getWorkflowContextHandler().addSuiteLevelParameter(index, name, value==null?null:value.toString());
    }

    public void ___cxt___print_provider__(String name)
    {
        System.out.println(___cxt___.getAnyProviderData(getPn(name), null));
    }

    public void ___cxt___print_provider__json(String name)
    {
        try
        {
            System.out.println(new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(___cxt___.getAnyProviderData(getPn(name), null)));
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

    @SuppressWarnings("rawtypes")
    protected AndroidDriver getAndroidDriver() {
        if(___d___ instanceof AndroidDriver) {
            return (AndroidDriver)___d___;
        }
        return null;
    }

    @SuppressWarnings("rawtypes")
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

    public static interface CondFunc {
        Integer f(Object[] args);
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

    public void mzoompinch(Object ele, int x, int y, boolean isZoom) {
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

    public Object pluginize(String name, String signature, List<String> args, List<List<String>> args1) {
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
}
