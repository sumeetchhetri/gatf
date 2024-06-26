package com.gatf.selenium;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import org.openqa.selenium.WebDriver;

import com.gatf.selenium.SeleniumTest.SeleniumTestResult;

/**
 * @author Sumeet Chhetri<br/>
 *
 */
public class SeleniumTestSession implements Serializable {
    private static final long serialVersionUID = 1L;
    
    protected transient List<WebDriver> ___d___ = new ArrayList<WebDriver>();
    protected transient List<Boolean> ___dqs___ = new ArrayList<Boolean>();
    protected transient int __wpos__ = 0;
    protected final Map<String, SeleniumResult> __result__ = new HashMap<String, SeleniumResult>();
    
    protected transient String __funcname__ = null;
    protected transient String __subtestname__ = null;
    protected transient Map<String, Object> __loopcontext__ = new HashMap<>();
    protected transient long __subtestexecutiontime__ = 0L;
    protected transient long __teststarttime__ = 0L;
    protected transient final Map<String, Integer> __provdetails__ = new LinkedHashMap<String, Integer>();
    protected transient final Map<String, Stack<Object>> __vars__ = new LinkedHashMap<String, Stack<Object>>();
	
    //protected transient ServerSocket ccServer = null;
    //protected transient Thread ccThrd = null;
    //protected transient volatile boolean ccStatus = true;
    
    protected final Map<String, Object[]> internalTestRs = new HashMap<String,Object[]>();
    
    protected transient final Map<String, List<Map<String, String>>> providerTestDataMap = new HashMap<String, List<Map<String,String>>>();

    protected String browserName;
    
    protected String sessionName;
    
    public String getSessionName() {
        return sessionName;
    }

    public static class SeleniumResult implements Serializable {
        private static final long serialVersionUID = 1L;

        String browserName;

        SeleniumTestResult result;

        Map<String, SeleniumTestResult>  __cresult__ = new LinkedHashMap<String, SeleniumTestResult>();
        Map<String, SeleniumTestResult>  __cresult_or__ = new LinkedHashMap<String, SeleniumTestResult>();

        public SeleniumTestResult getResult()
        {
            return result;
        }

        public Map<String, SeleniumTestResult> getSubTestResults()
        {
        	__cresult__.putAll(__cresult_or__);
            return __cresult__;
        }

        public String getBrowserName()
        {
            return browserName;
        }
    }
    
    public Map<String, SeleniumResult> getResult() {
        return __result__;
    }
}
