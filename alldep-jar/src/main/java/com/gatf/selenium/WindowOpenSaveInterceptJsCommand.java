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

/** G8 — promoted verbatim from the nested Command.WindowOpenSaveInterceptJsCommand (inherits the same members/nested types). */
@SuppressWarnings("unused")
public class WindowOpenSaveInterceptJsCommand extends Command {
    	boolean start = true;
    	boolean extractText = false;
    	int optionalOpenNums = 1;
    	int openPos = 0;
    	String colsep = "|";
    	WindowOpenSaveInterceptJsCommand(String cmd, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            String[] parts = cmd.trim().split("[\t ]+");
            String mode = unSantizedUnQuoted(parts[0].trim(), state);
            if(StringUtils.isBlank(mode) || !mode.toLowerCase().matches("on|off")) {
            	throwParseError(null, new RuntimeException("window.open save intercept command needs to be enabled/disabled, valid values on|off"));
            }
            start = !mode.equalsIgnoreCase("off");
            if(mode.equalsIgnoreCase("off") && parts.length<2) {
            	throwParseError(null, new RuntimeException("window.open save disable command needs a file name to store the result into"));
            }
            if(mode.equalsIgnoreCase("off")) {
            	name = unSantizedUnQuoted(parts[1].trim(), state);
            }
            if(!start && parts.length>2) {
            	parts[2] = unSantizedUnQuoted(parts[2].trim(), state);
            	this.extractText = parts[2].trim().equalsIgnoreCase("text")?true:false;
            }
            if(!start && parts.length>3) {
            	parts[3] = unSantizedUnQuoted(parts[3].trim(), state);
            	try {
            		openPos = Integer.valueOf(parts[3].trim());
            		if(openPos<0) {
            			openPos = 0;
            			System.out.println("The window open position called in this context should be greater than 0");
            		} else if(openPos>0) {
            			openPos = openPos-1;
            		}
				} catch (Exception e) {
		            //colsep = parts[3].trim();
					throwParseError(null, new RuntimeException("Please provide a valid/positive window.open position"));
				}
            }
            if(!start && parts.length>4) {
            	colsep = StringUtils.isNotBlank(parts[4])?parts[4].trim():"|";
            }
            if(start && parts.length>1) {
            	try {
            		optionalOpenNums = Integer.valueOf(parts[1].trim());
            		if(optionalOpenNums<=0) {
            			optionalOpenNums = 1;
            			System.out.println("The total number of window opens called in this context should be greater than 0");
            		}
				} catch (Exception e) {
					throwParseError(null, new RuntimeException("Please provide a valid/positive total window.open number"));
				}
            }
        }
        String toCmd() {
            return "wopensave " + (start?"on":"off") + (start?"":" \"" +name+ "\"") + (!start && extractText?" text":"");
        }
        String javacode() {
        	if(start) return "\nwindowOpenSaveJsPre(___cw___, "+optionalOpenNums+");\n";
        	else return "\nwindowOpenSaveJsPost(___cw___, evaluate(\""+esc(name)+"\"), "+extractText+", "+openPos+", \""+esc(colsep)+"\");\n";
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
        		"Save URL passed to window.open",
        		"\twopensave {on|off} {num-window-opens|filepath}? {text}? {openPos}?",
        		"Examples :-",
        		"\twopensave on",
        		"\twopensave on 2",
        		"\twopensave off '/path/to/file.txt' text",
        		"\twopensave off '/path/to/file.txt' text 2",
        		"\twopensave off '/path/to/file.txt' text 2 |",
            };
        }
    }
