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

import java.awt.event.KeyEvent;
import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Method;
import java.security.Security;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Stack;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.text.StringEscapeUtils;
import org.openqa.selenium.Keys;
import org.openqa.selenium.logging.LoggingPreferences;
import org.reflections.Reflections;
import com.gatf.executor.core.AcceptanceTestContext;
import com.gatf.executor.core.GatfExecutorConfig;
import com.gatf.executor.core.WorkflowContextHandler;
import com.gatf.selenium.Command.ProviderLoopCommand.ProviderType;
import com.gatf.selenium.SeleniumTest.GatfRunTimeError;
import com.gatf.selenium.plugins.ApiPlugin;
import com.gatf.selenium.plugins.CurlPlugin;
import com.gatf.selenium.plugins.JsonPlugin;
import com.gatf.selenium.plugins.XmlPlugin;

/** G8 — promoted verbatim from the nested Command.TimeoutCommand (inherits the same members/nested types). */
@SuppressWarnings("unused")
public class TimeoutCommand extends Command {
    	int ifCounter = 1;
    	int counter = 60;
    	long sleepGranularity = 1000;
    	TimeoutCommand(String val, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            String[] parts = val.trim().split("[\t ]+");
            if(parts.length==0) {
            	throwParseError(null, new RuntimeException("Please specify timeout in seconds"));
            }
            if(parts.length>0) {
            	if(StringUtils.isBlank(parts[0])) {
            		throwParseError(null, new RuntimeException("Please specify timeout in seconds"));
            	}
            	try {
            		counter = Integer.valueOf(parts[0]);
				} catch (Exception e) {
					throwParseError(null, new RuntimeException("Please specify timeout in seconds"));
				}
            }
            if(counter<2) {
            	throwParseError(null, new RuntimeException("Timeout should be more than or equal to 2"));
            }
            if(parts.length>1 && StringUtils.isNotBlank(parts[1])) {
            	try {
            		ifCounter = Integer.valueOf(parts[1]);
				} catch (Exception e) {
					throwParseError(null, new RuntimeException("Please specify timeout in seconds"));
				}
            }
            if(ifCounter<1) {
            	throwParseError(null, new RuntimeException("Timeout should be more than or equal to 1"));
            }
            if(parts.length>2 && StringUtils.isNotBlank(parts[2])) {
            	try {
            		sleepGranularity = Long.valueOf(parts[2]);
				} catch (Exception e) {
					throwParseError(null, new RuntimeException("Please specify proper sleep granularity (should be in milliseconds)"));
				}
            }
            
            if(sleepGranularity!=1000) {
            	counter = (int)(counter*1000/sleepGranularity);
            	ifCounter = (int)(ifCounter*1000/sleepGranularity);
            }
        }
        String toCmd() {
            return "timeout " + counter + " " + ifCounter + " " + sleepGranularity;
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
        		"Define Global timeout",
        		"\ttimeout {timeout-secs} {if-timeout-secs}} {sleep-granularity-millis}",
				"Examples :-",
				"\ttimeout 60 1 1000",
            };
        }
        public String javacode() {
            return "";
        }
    }
