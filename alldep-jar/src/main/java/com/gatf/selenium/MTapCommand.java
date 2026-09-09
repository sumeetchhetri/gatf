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

/** G8 — promoted verbatim from the nested Command.MTapCommand (inherits the same members/nested types). */
@SuppressWarnings("unused")
public class MTapCommand extends FindCommandImpl {
        int x = Integer.MAX_VALUE , y = Integer.MAX_VALUE, d = 500;
        String toCmd() {
            return "";
        }
        String javacode() {
            return "\ndoTap(___cw___, null, new org.openqa.selenium.Point("+x+", "+y+"), "+d+");";
        }
        String selcode(String varnm) {
            if(varnm==null) {
                varnm = state.currvarname();
            }
            return "\ndoTap(___cw___, "+varnm+".get(0), null, "+d+")";
            //return "\nnew io.appium.java_client.TouchAction((io.appium.java_client.MobileDriver) get___d___()).tap("+varnm+".get(0)).perform();";
        }
        public MTapCommand(String info, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            String[] t = info.trim().split("[\t ]+");
            if(t.length==1)
            {
                cond = new FindCommand(t[0].trim(), fileLineDetails, state);
            }
            else if(t.length==2)
            {
                try
                {
                    x = Integer.parseInt(t[0].trim());
                    y = Integer.parseInt(t[1].trim());
                }
                catch (Exception e)
                {
                    cond = new FindCommand(t[0].trim(), fileLineDetails, state);
                    try
                    {
                        d = Integer.parseInt(t[1].trim());
                    }
                    catch (Exception e1)
                    {
                        throwParseError(null, new RuntimeException("tap command needs valid {find-expr}/x-co-ordinate/y-co-ordinate/duration values"));
                    }
                }
            }
            else if(t.length==3)
            {
                try
                {
                    x = Integer.parseInt(t[0].trim());
                    y = Integer.parseInt(t[1].trim());
                    d = Integer.parseInt(t[2].trim());
                }
                catch (Exception e)
                {
                    cond = new FindCommand(t[0].trim(), fileLineDetails, state);
                    try
                    {
                        x = Integer.parseInt(t[1].trim());
                        y = Integer.parseInt(t[2].trim());
                    }
                    catch (Exception e1)
                    {
                        throwParseError(null, new RuntimeException("tap command needs valid {find-expr}/x-co-ordinate/y-co-ordinate/duration values"));
                    }
                }
            }
            else if(t.length==4)
            {
                cond = new FindCommand(t[0].trim(), fileLineDetails, state);
                try
                {
                    x = Integer.parseInt(t[1].trim());
                    y = Integer.parseInt(t[2].trim());
                    d = Integer.parseInt(t[3].trim());
                }
                catch (Exception e1)
                {
                    throwParseError(null, new RuntimeException("tap command needs valid {find-expr}/x-co-ordinate/y-co-ordinate/duration values"));
                }
            }
            else
            {
                throwParseError(null, new RuntimeException("tap command needs valid x-co-ordinate/y-co-ordinate/duration values"));
            }
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
        		"Mobile Tap",
        		"\ttap ({find-expr}|{find-expr} {duration}|{x-co-ordinate} {y-co-ordinate}|{x-co-ordinate} {y-co-ordinate} {duration}|{find-expr} {x-co-ordinate} {y-co-ordinate}|{find-expr} {x-co-ordinate} {y-co-ordinate} {duration})",
        		"Examples :-",
        		"\ttap id@'ele'",
        		"\ttap id@'ele' 2000",
        		"\ttap id@'ele' 123 234",
        		"\ttap id@'ele' 123 234 2000",
        		"\ttap 123 234",
        		"\ttap 123 234 2000",
            };
        }
    }
