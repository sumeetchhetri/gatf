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

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

import org.openqa.selenium.logging.LoggingPreferences;
import org.openqa.selenium.logging.Logs;

import com.gatf.executor.core.AcceptanceTestContext;

public interface SeleniumTest {
	@SuppressWarnings("serial")
	Map<String, Level> LOG_LEVEL_BY_NAME_MAP = new HashMap<String, Level>(){{
		put("ALL".toLowerCase(), Level.ALL);
		put("CONFIG".toLowerCase(), Level.CONFIG);
		put("FINE".toLowerCase(), Level.FINE);
		put("FINER".toLowerCase(), Level.FINER);
		put("FINEST".toLowerCase(), Level.FINEST);
		put("INFO".toLowerCase(), Level.INFO);
		put("OFF".toLowerCase(), Level.OFF);
		put("SEVERE".toLowerCase(), Level.SEVERE);
		put("WARNING".toLowerCase(), Level.WARNING);
	}};
	
	void quit();
	Logs execute(AcceptanceTestContext ___c___, LoggingPreferences ___lp___) throws Exception;
}
