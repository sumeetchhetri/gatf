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
import org.openqa.selenium.logging.Logs;

import com.gatf.selenium.SeleniumTest.SeleniumTestResult;

public class SeleniumException extends RuntimeException {
	
    private static final long serialVersionUID = 1L;
    
    private SeleniumTestResult res;
	
	public SeleniumTestResult getResult() {
		return res;
	}
	public SeleniumException(){}
	public SeleniumException(WebDriver d, Throwable cause, SeleniumTest test) {
		super(cause);
		Map<String, SerializableLogEntries> lg = new HashMap<String, SerializableLogEntries>();
        Logs logs = d.manage().logs();
        for (String s : d.manage().logs().getAvailableLogTypes()) {
            LogEntries logEntries = logs.get(s);
            if(!logEntries.getAll().isEmpty()) {
                lg.put(s, new SerializableLogEntries(logEntries.getAll())); 
            }
        }
        List<LogEntry> entries = new ArrayList<LogEntry>();
        entries.add(new LogEntry(Level.ALL, new Date().getTime(), cause.getMessage()));
        entries.add(new LogEntry(Level.ALL, new Date().getTime(), ExceptionUtils.getStackTrace(cause)));
        lg.put("gatf", new SerializableLogEntries(entries));
	}
}
