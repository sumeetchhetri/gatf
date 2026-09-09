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

/** G8 — promoted verbatim from the nested Command.MTouchActionCommand (inherits the same members/nested types). */
@SuppressWarnings("unused")
public class MTouchActionCommand extends Command {
        String action, expr;
        Integer x, y, d;

        public MTouchActionCommand(Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
        		"Mobile Touch",
        		"\ttouch ({press|moveto|tap {find-expr}|{find-expr} {x-co-ordinate} {y-co-ordinate}|{x-co-ordinate} {y-co-ordinate}}|"
        		+ "longpress|{longpress {find-expr} {x-co-ordinate} {y-co-ordinate} {duration}|{find-expr} {x-co-ordinate} {y-co-ordinate}|"
        		+ "{find-expr} {duration}|{x-co-ordinate} {y-co-ordinate} {duration}|{x-co-ordinate} {y-co-ordinate}}|"
        		+ "{wait {duration}}|release) ... ({press|moveto|tap {find-ex...",
        		"Examples :-",
        		"\ttouch moveto id@'ele' longpress moveto id@'ele2' wait 1000 release",
        		"\ttouch moveto id@'ele' longpress id@'ele1' moveto id@'ele2' wait 1000 release",
            };
        }
        MTouchActionCommand(String val, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            String[] t = val.trim().split("[\t ]+");
            int tl = t.length, counter = 0;
            MTouchActionCommand cmd = this;
            while(counter<tl) {
                if(t[counter].toLowerCase().matches("press|moveto|tap")) {
                    cmd.action = t[counter];
                    if(cmd.action.toLowerCase().equals("moveto")) {
                        cmd.action = "moveTo";
                    }
                    int fc = 0;
                    for (int i = 1; i < 4; i++)
                    {
                        if(tl<=counter+i)break;
                        if(t[counter+i].matches("press|moveto|tap|longpress|wait|release")) {
                            break;
                        }
                        fc++;
                    }
                    if(fc==3) {
                        cmd.expr = t[++counter];
                        try
                        {
                            cmd.x = Integer.parseInt(t[++counter]);
                            cmd.y = Integer.parseInt(t[++counter]);
                        }
                        catch (Exception e)
                        {
                            throwParseError(null, new RuntimeException("Invalid x/y co-ordinates specified for actions (press|moveto|tap)"));
                        }
                    } else if(fc==2) {
                        try
                        {
                            cmd.x = Integer.parseInt(t[++counter]);
                            cmd.y = Integer.parseInt(t[++counter]);
                        }
                        catch (Exception e)
                        {
                            throwParseError(null, new RuntimeException("Invalid x/y co-ordinates specified for actions (press|moveto|tap)"));
                        }
                    } else if(fc==1) {
                        cmd.expr = t[++counter];
                    } else {
                        throwParseError(null, new RuntimeException("Expressions(>=1 <=3) expected after actions (press|moveto|tap)"));
                    }
                } else if(t[counter].toLowerCase().matches("longpress")) {
                    cmd.action = "longPress";
                    int fc = 0;
                    for (int i = 1; i < 5; i++)
                    {
                        if(tl<=counter+i)break;
                        if(t[counter+i].matches("press|moveto|tap|longpress|wait|release")) {
                            break;
                        }
                        fc++;
                    }
                    if(fc==4) {
                        cmd.expr = t[++counter];
                        try
                        {
                            cmd.x = Integer.parseInt(t[++counter]);
                            cmd.y = Integer.parseInt(t[++counter]);
                            cmd.d = Integer.parseInt(t[++counter]);
                        }
                        catch (Exception e)
                        {
                            throwParseError(null, new RuntimeException("Invalid x/y/d values specified for actions (longpress)"));
                        }
                    } else if(fc==3) {
                        try
                        {
                            Integer.parseInt(t[counter+1]);
                            cmd.x = Integer.parseInt(t[++counter]);
                            cmd.y = Integer.parseInt(t[++counter]);
                            cmd.d = Integer.parseInt(t[++counter]);
                        }
                        catch (Exception e)
                        {
                            cmd.expr = t[++counter];
                            try
                            {
                                cmd.x = Integer.parseInt(t[++counter]);
                                cmd.y = Integer.parseInt(t[++counter]);
                            }
                            catch (Exception e1)
                            {
                                throwParseError(null, new RuntimeException("Invalid x/y co-ordinates specified for actions (longpress)"));
                            }
                        }
                    } else if(fc==2) {
                        try
                        {
                            Integer.parseInt(t[counter+1]);
                            cmd.x = Integer.parseInt(t[++counter]);
                            cmd.y = Integer.parseInt(t[++counter]);
                        }
                        catch (Exception e)
                        {
                            cmd.expr = t[++counter];
                            try
                            {
                                cmd.d = Integer.parseInt(t[++counter]);
                            }
                            catch (Exception e1)
                            {
                                throwParseError(null, new RuntimeException("Invalid d value specified for actions (longpress)"));
                            }
                        }
                    } else if(fc==1) {
                        cmd.expr = t[++counter];
                    } else {
                        //throwParseError(null, new RuntimeException("Expressions(>=1 <=3) expected after actions (longpress)"));
                    }
                } else if(t[counter].toLowerCase().matches("wait")) {
                    cmd.action = "waitAction";
                    if(!t[counter+1].toLowerCase().matches("press|moveto|tap|longpress|wait|release")) {
                        try
                        {
                            cmd.d = Integer.parseInt(t[++counter]);
                        }
                        catch (Exception e)
                        {
                            throwParseError(null, new RuntimeException("Invalid duration specified for actions (wait)"));
                        }
                    }
                } else if(t[counter].toLowerCase().matches("release")) {
                    cmd.action = t[counter];
                } else {
                    throwParseError(null, new RuntimeException("Invalid action specified, should be one of (press|moveto|tap|longpress|wait|release)"));
                }
                ++counter;
                cmd.children.add(new MTouchActionCommand(cmdDetails, state));
                cmd = (MTouchActionCommand)cmd.children.get(0);
            }
            cmd = this;
            while(!cmd.children.isEmpty()) {
                MTouchActionCommand tcmd = (MTouchActionCommand)cmd.children.get(0);
                if(tcmd.action==null) {
                    cmd.children.clear();
                }
                cmd = tcmd;
            }
        }
        String toCmd() {
            return "touch ";
        }
        String _javacode(String acnm) {
            StringBuilder b = new StringBuilder();
            if(action.toLowerCase().matches("press|tap|moveto")) {
                if(expr!=null) {
                    FindCommand cond = new FindCommand(expr, fileLineDetails, state);
                    b.append("\n"+cond.javacodeonly());
                    if(x==null) {
                        b.append("\n"+acnm+"."+action+"(___ce___.get(0));");
                    } else {
                        b.append("\n"+acnm+"."+action+"(___ce___.get(0), "+x+", "+y+");");
                    }
                } else {
                    b.append("\n"+acnm+"."+action+"("+x+", "+y+");");
                }
            } else if(action.toLowerCase().matches("longpress")) {
                if(expr!=null) {
                    FindCommand cond = new FindCommand(expr, fileLineDetails, state);
                    b.append("\n"+cond.javacodeonly());
                    if(d==null) {
                        if(x==null) {
                            b.append("\n"+acnm+"."+action+"(___ce___.get(0));");
                        } else {
                            b.append("\n"+acnm+"."+action+"(___ce___.get(0), "+x+", "+y+");");
                        }
                    } else {
                        if(x==null) {
                            b.append("\n"+acnm+"."+action+"(___ce___.get(0), java.time.Duration.ofMillis("+d+"));");
                        } else {
                            b.append("\n"+acnm+"."+action+"(___ce___.get(0), "+x+", "+y+", java.time.Duration.ofMillis("+d+"));");
                        }
                    }
                } else {
                	if(x!=null && y!=null) {
	                    if(d==null) {
	                        b.append("\n"+acnm+"."+action+"("+x+", "+y+");");
	                    } else {
	                        b.append("\n"+acnm+"."+action+"("+x+", "+y+", java.time.Duration.ofMillis("+d+"));");
	                    }
                	} else {
                		String cvarnm = state.currvarname();
                		if(d==null) {
                            if(x==null) {
                                b.append("\n"+acnm+"."+action+"(___ce___.get(0));");
                            } else {
                                b.append("\n"+acnm+"."+action+"("+cvarnm+".get(0), "+x+", "+y+");");
                            }
                        } else {
                            if(x==null) {
                                b.append("\n"+acnm+"."+action+"(___ce___.get(0), java.time.Duration.ofMillis("+d+"));");
                            } else {
                                b.append("\n"+acnm+"."+action+"(___ce___.get(0), "+x+", "+y+", java.time.Duration.ofMillis("+d+"));");
                            }
                        }
                	}
                }
            } else if(action.toLowerCase().matches("wait")) {
                if(d!=null) {
                    b.append("\n"+acnm+"."+action+"(java.time.Duration.ofMillis("+d+"));");
                } else {
                    b.append("\n"+acnm+"."+action+"();");
                }
            } else if(action.toLowerCase().matches("release")) {
                b.append("\n"+acnm+"."+action+"();");
            }
            if(!children.isEmpty()) {
                MTouchActionCommand tcmd = (MTouchActionCommand)children.get(0);
                b.append(tcmd._javacode(acnm));
            }
            return b.toString();
        }
        String javacode() {
            StringBuilder b = new StringBuilder();
            String acnm = state.varname();
            b.append("\nio.appium.java_client.TouchAction "+acnm+" = new io.appium.java_client.TouchAction((io.appium.java_client.PerformsTouchActions)___cw___);");
            b.append(_javacode(acnm));
            b.append("\n"+acnm+".perform();");
            return b.toString();
        }
    }
