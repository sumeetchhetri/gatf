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
    
    /** Streaming trace recorder for this session (functional UI runs only); zipped + linked in the report. */
    public transient com.gatf.selenium.trace.TraceRecorder __trace__;
    
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