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

/** G8 — promoted verbatim from the nested Command.RandomizeCommand (inherits the same members/nested types). */
@SuppressWarnings("unused")
public class RandomizeCommand extends FindCommandImpl {
        String v1;
        String v2, v3;
        boolean noblur = false;
        RandomizeCommand(String val, Object[] cmdDetails, CommandState state, FindCommand cond, boolean noblur) {
            super(cmdDetails, state);
            String[] parts = val.trim().split("[\t ]+");
            this.noblur = noblur;
            if(parts.length>0) {
                int c = 1, t = 1;
                if(cond!=null) {
                    c = 0;
                    t = 0;
                } else {
                    this.cond = new FindCommand(parts[0].trim(), fileLineDetails, state);
                }
                if(parts.length>t) {
                    v1 = parts[c];
                    v1 = unSantizedUnQuoted(v1, state);
                    if(!v1.toLowerCase().matches("alphanumeric|alpha|alphanumericlc|alphalc|alphanumericuc|alphauc|numeric|value|range|prefixed|prefixed_|fuzzyn|fuzzya|fuzzyauc|fuzzyalc|fuzzyan|fuzzyanuc|fuzzyanlc")) {
                    	throwParseError(null, new RuntimeException("Randomize type can only be one of alphanumeric|alpha|alphanumericlc|alphalc|alphanumericuc|alphauc|numeric|value|range|prefixed|prefixed_|fuzzyn|fuzzya|fuzzyauc|fuzzyalc|fuzzyan|fuzzyanuc|fuzzyanlc"));
                    }
                }
                if(parts.length>t+1) {
                    v2 = parts[c+1];
                    v2 = unSantizedUnQuoted(v2, state);
                }
                if(parts.length>t+2) {
                    v3 = parts[c+2];
                    v3 = unSantizedUnQuoted(v3, state);
                }
                
                try {
    				Long.parseLong(v2);
    			} catch (Exception e) {
    				if(v2!=null && !v1.equalsIgnoreCase("value")) {
    					throwParseError(null, new RuntimeException("Randomize command needs first argument to be a number for alphanumeric|alpha|alphanumericlc|alphalc|alphanumericuc|alphauc"));
    				}
    			}
                
                try {
                	Long.parseLong(v3);
    			} catch (Exception e) {
    				if(v3!=null) {
    					throwParseError(null, new RuntimeException("Randomize command needs second argument to be a number for alphanumeric|alpha|alphanumericlc|alphalc|alphanumericuc|alphauc"));
    				}
    			}
            } else {
                //excep
            }
        }
        String toCmd() {
            return "randomize" + (!noblur?"":"nb") + " \"" + cond.toCmd() + (v1!=null?(" "+v1):"") + (v2!=null?(" "+v2):"") + (v3!=null?(" "+v3):"");
        }
        String javacode() {
            StringBuilder b = new StringBuilder();
            if(cond!=null) {
                b.append(cond.javacodeonly());
                b.append("\nrandomize("+cond.getActionableVar()+", \""+(v1!=null?esc(v1):"")+"\", \""+(v2!=null?esc(v2):"")+"\", \""+(v3!=null?esc(v3):"")+"\");\n");
                if(!noblur) {
                	b.append("\nblur("+cond.getActionableVar()+".get(0));\n");
                }
            } else {
                b.append("\nrandomize(___ce___, \""+(v1!=null?esc(v1):"")+"\", \""+(v2!=null?esc(v2):"")+"\", \""+(v3!=null?esc(v3):"")+"\");\n");
                b.append("\nblur(___ce___.get(0));\n");
            }
            return b.toString();
        }
        String javacodeonly(String condVarnm) {
            StringBuilder b = new StringBuilder();
            b.append("\nrandomize("+condVarnm+", \""+(v1!=null?esc(v1):"")+"\", \""+(v2!=null?esc(v2):"")+"\", \""+(v3!=null?esc(v3):"")+"\");\n");
            if(!noblur) {
            	b.append("\nblur("+condVarnm+".get(0));\n");
            }
            return b.toString();
        }
        String ifTextEl() {
            return "\n("+state.currvarname()+".get(0).getTagName().toLowerCase().matches(\"input\") "
                    + "&& "+state.currvarname()+".get(0).getAttribute(\"type\").toLowerCase().matches(\"text|url|email|hidden\"))"
                    + "|| ("+state.currvarname()+".get(0).getTagName().toLowerCase().matches(\"textarea\"))";
        }
        String ifNumTextEl() {
            return "\n"+state.currvarname()+".get(0).getTagName().toLowerCase().matches(\"input\") "
                    + "&& "+state.currvarname()+".get(0).getAttribute(\"type\").toLowerCase().matches(\"number\")";
        }
        String ifSelectEl() {
            return "\n"+state.currvarname()+".get(0).getTagName().toLowerCase().matches(\"select\")";
        }
        String ifRadioEl() {
            return "\n"+state.currvarname()+".get(0).getTagName().toLowerCase().matches(\"input\")"
                    + "&& "+state.currvarname()+".get(0).getAttribute(\"type\").toLowerCase().matches(\"radio\")";
        }
        String ifCheckboxEl() {
            return "\n"+state.currvarname()+".get(0).getTagName().toLowerCase().matches(\"input\")"
                    + "&& "+state.currvarname()+".get(0).getAttribute(\"type\").toLowerCase().matches(\"checkbox\")";
        }
        String selcode(String varnm) {
            if(varnm==null) {
                varnm = state.currvarname();
            }
            return "\n"+varnm+".get(0).sendKeys(evaluate(\""+esc(v1)+"\"));" + (!noblur?"":"blur("+varnm+".get(0));\n");
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
        		"Type random values in input/textarea elements",
        		"\trandomize(bl|ch|bk|cl|fo) {find-expr} alphanumeric|alpha|alphanumericlc|alphalc|alphanumericuc|alphauc|numeric|value|range|prefixed|prefixed_|fuzzyn|fuzzya|fuzzyauc|fuzzyalc|fuzzyan|fuzzyanuc|fuzzyanlc {optional character count|range start} {count of space separated random words(for eg, name of person)|range end}",
        		"Examples :-",
        		"\trandomize id@'ele1' alphanumeric 12",
        		"\trandomize id@'ele1' alpha 8 3 (first-name middle-name last-name)",
        		"\trandomize id@'ele1' numeric 5",
        		"\trandomize id@'ele1' range 9999 99999",
        		"\trandomize id@'ele1' value 'abcd'",
        		"\trandomize id@'ele1' fuzzyn _@3:4:5:6",
        		"\trandomize id@'ele1' fuzzya _@3:4:5:6",
        		"\trandomize id@'ele1' fuzzyan _@3:4:5:6",
            };
        }
    }
