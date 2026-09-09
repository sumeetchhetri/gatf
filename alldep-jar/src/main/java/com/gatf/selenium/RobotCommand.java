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
import com.gatf.selenium.Command.FindCommandImpl;   // nested supertype: resolved in file scope, not via inheritance

/** G8 — promoted verbatim from the nested Command.RobotCommand (inherits the same members/nested types). */
@SuppressWarnings("unused")
public class RobotCommand extends FindCommandImpl {
        String action;
        String expr1;

        public RobotCommand(Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
        }
        RobotCommand(String val, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            if(val.equals("scrollup")) {
                val = "keypress " + KeyEvent.VK_UP;
            } else if(val.equals("scrollpageup")) {
                val = "keypress " + KeyEvent.VK_PAGE_UP;
            } else if(val.equals("scrolldown")) {
                val = "keypress " + KeyEvent.VK_DOWN;
            } else if(val.equals("scrollpagedown")) {
                val = "keypress " + KeyEvent.VK_PAGE_DOWN;
            }
            String[] t = val.trim().split("[\t ]+");
            int tl = t.length, counter = 0;
            RobotCommand cmd = this;
            while(counter<tl) {
                if(t[counter].toLowerCase().matches("keydown|keyup|keypress|keyRelease|key")) {
                    cmd.action = t[counter].toLowerCase();
                    boolean flag = true;
                    if(cmd.action.equalsIgnoreCase("keydown") || cmd.action.equalsIgnoreCase("keypress")) {
                        cmd.action = "keyPress";
                    } else if(cmd.action.equalsIgnoreCase("keyup") || cmd.action.equalsIgnoreCase("keyRelease")) {
                    	//flag = false;
                        cmd.action = "keyRelease";
                    } else if(cmd.action.equals("key")) {
                        cmd.action = "key";
                    }
                    if(flag && tl>counter+1) {
                        cmd.expr1 = unSantizedUnQuoted(t[++counter], state);
                    } else if(flag) {
                        throwParseError(null, new RuntimeException("Expression expected after actions (keydown|keyup)"));
                    }
                } else {
                    throwParseError(null, new RuntimeException("Invalid action specified, should be one of (keydown|keyup)"));
                }
                ++counter;
                cmd.children.add(new RobotCommand(cmdDetails, state));
                cmd = (RobotCommand)cmd.children.get(0);
            }
            cmd = this;
            while(!cmd.children.isEmpty()) {
                RobotCommand tcmd = (RobotCommand)cmd.children.get(0);
                if(tcmd.action==null) {
                    cmd.children.clear();
                }
                cmd = tcmd;
            }
        }
        String toCmd() {
            return "robot keydown|keyup|keypress ";
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
        		"Send keys using Robot",
        		"\trobot keydown|keyup|keypress {key-code1} ... keydown|keyup|keypress|keyrelease|key {key-codeN}",
        		"\tscrollup",
        		"\tscrolldown",
        		"\tscrollpageup",
        		"\tscrollpagedown",
        		"Examples :-",
        		"\trobot keydown 1",
        		"\trobot keyup 1",
        		"\trobot keypress 1",
        		"\trobot keypress 1 keydown 2 keyup ",
        		"\tscrollup",
        		"\tscrolldown",
        		"\tscrollpageup",
        		"\tscrollpagedown",
            };
        }
        String _javacode(String acnm) {
            StringBuilder b = new StringBuilder();
            if(action.toLowerCase().matches("keypress|keyrelease|key")) {
                String ck = state.unsanitize(expr1);
                try
                {
                    ck = Integer.decode(ck.toLowerCase()) + "";
                }
                catch (Exception e)
                {
                    throwParseError(null, new RuntimeException("Invalid key code specified"));
                }
                if(action.toLowerCase().equals("key")) {
                    b.append("\n"+acnm+".keyPress("+ck+");");
                    b.append("\n"+acnm+".keyRelease("+ck+");");
                } else {
                    b.append("\n"+acnm+"."+action+"("+ck+");");
                }
            }
            if(!children.isEmpty()) {
                RobotCommand tcmd = (RobotCommand)children.get(0);
                b.append(tcmd._javacode(acnm));
            }
            return b.toString();
        }
        String javacode() {
            StringBuilder b = new StringBuilder();
            String acnm = state.varname();
            b.append("\ntry{\njava.awt.Robot "+acnm+" = new java.awt.Robot();");
            b.append(_javacode(acnm));
            b.append("\nsleep(100);\n} catch(Exception e){\nthrow new RuntimeException(e);}\n");
            return b.toString();
        }
    }
