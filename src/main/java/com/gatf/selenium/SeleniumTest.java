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
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.logging.LogEntries;
import org.openqa.selenium.logging.LogEntry;
import org.openqa.selenium.logging.LoggingPreferences;
import org.openqa.selenium.logging.Logs;

import com.gatf.executor.core.AcceptanceTestContext;

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
	
	public SeleniumTest(String name) {
	    this.name = name;
	}
	
	protected final Map<String, Object[]> internalTestRs = new HashMap<String,Object[]>();
	
	protected String name;
	
	public abstract void quit();
	
	public abstract SeleniumTestResult execute(AcceptanceTestContext ___c___, LoggingPreferences ___lp___) throws Exception;
	
	public static class SeleniumTestResult implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private Map<String, SerializableLogEntries> logs = new HashMap<String, SerializableLogEntries>();;
        
        private String name;
        
        private boolean status;

        private Map<String, Object[]> internalTestRes = new HashMap<String, Object[]>();
        
        public Map<String,SerializableLogEntries> getLogs()
        {
            return logs;
        }
        public String getName()
        {
            return name;
        }
        public boolean isStatus()
        {
            return status;
        }
        public Map<String,Object[]> getInternalTestRes()
        {
            return internalTestRes;
        }
        public SeleniumTestResult(WebDriver d, SeleniumTest test)
        {
            this.name = test.name;
            this.status = true;
            this.internalTestRes = test.internalTestRs;
            Logs logs = d.manage().logs();
            for (String s : d.manage().logs().getAvailableLogTypes()) {
                LogEntries logEntries = logs.get(s);
                if(!logEntries.getAll().isEmpty()) {
                    this.logs.put(s, new SerializableLogEntries(logEntries.getAll())); 
                }
            }
        }
        public SeleniumTestResult(WebDriver d, SeleniumTest test, Throwable cause) {
            this.name = test.name;
            this.status = false;
            this.internalTestRes = test.internalTestRs;
            Logs logs = d.manage().logs();
            for (String s : d.manage().logs().getAvailableLogTypes()) {
                LogEntries logEntries = logs.get(s);
                if(!logEntries.getAll().isEmpty()) {
                    this.logs.put(s, new SerializableLogEntries(logEntries.getAll())); 
                }
            }
            List<LogEntry> entries = new ArrayList<LogEntry>();
            entries.add(new LogEntry(Level.ALL, new Date().getTime(), cause.getMessage()));
            entries.add(new LogEntry(Level.ALL, new Date().getTime(), ExceptionUtils.getStackTrace(cause)));
            this.logs.put("gatf", new SerializableLogEntries(entries));
        }
        public SeleniumTestResult(SeleniumTest test, Throwable cause) {
            this.name = test.name;
            this.status = false;
            this.internalTestRes = test.internalTestRs;
            List<LogEntry> entries = new ArrayList<LogEntry>();
            entries.add(new LogEntry(Level.ALL, new Date().getTime(), cause.getMessage()));
            entries.add(new LogEntry(Level.ALL, new Date().getTime(), ExceptionUtils.getStackTrace(cause)));
            this.logs.put("gatf", new SerializableLogEntries(entries));
        }
	}
}
