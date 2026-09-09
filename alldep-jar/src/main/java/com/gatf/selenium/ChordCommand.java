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

/** G8 — promoted verbatim from the nested Command.ChordCommand (inherits the same members/nested types). */
@SuppressWarnings("unused")
public class ChordCommand extends FindCommandImpl {
        List<String> values = new ArrayList<String>();
        String value;
        boolean noblur = false;
        public ChordCommand(Object[] cmdDetails, CommandState state, boolean noblur) {
            super(cmdDetails, state);
            this.noblur = noblur;
        }
        ChordCommand(String val, Object[] cmdDetails, CommandState state, boolean noblur) {
            super(cmdDetails, state);
            String[] parts = val.trim().split("[\t ]+");
            this.noblur = noblur;
            if(parts.length>0) {
                value = unSantizedUnQuoted(parts[0], state);
                String temp = StringEscapeUtils.unescapeJava(value);
                try {
                    if(parts.length>1) {
                        cond = new FindCommand(parts[1], fileLineDetails, state);
                    }
                    for (char c : temp.toCharArray()) {
                        Keys kys = Keys.getKeyFromUnicode(c);
                        if(kys!=null) {
                            values.add("Keys." + kys.name());
                        } else {
                            if(c=='"') {
                                values.add("\"\\"+c+"\"");
                            } else {
                                values.add("\""+c+"\""); 
                            }
                        }
                    }
                } catch (Exception e) {
                }
            } else {
                //excep
            }
        }
        String toCmd() {
            return "chord"+(!noblur?"":"nb")+" \"" + value + "\"" + (cond!=null?cond.toCmd():"");
        }
        String javacode() {
            StringBuilder b = new StringBuilder();
            if(cond!=null) {
                b.append(cond.javacodeonly());
                //b.append("\nAssert.assertTrue("+cond.condition()+");");
            }
            if(values!=null && !values.isEmpty()) {
                String chs = "";
                for (int i=0;i<values.size();i++) {
                    chs += values.get(i);
                    chs += (i!=values.size()-1)?", ":"";
                }
                if(cond!=null) {
                    b.append(cond.getActionable("sendKeys", "Keys.chord("+chs+")", null));
                } else {
                    b.append("\n"+state.currvarname()+".get(0).sendKeys(Keys.chord("+chs+"));");
                    if(!noblur) {
                    	b.append("\nblur("+state.currvarname()+".get(0));\n");
                    }
                }
            }
            return b.toString();
        }
        String selcode(String varnm) {
            if(varnm==null) {
                varnm = state.currvarname();
            }
            if(values!=null && !values.isEmpty()) {
                String chs = "";
                for (int i=0;i<values.size();i++) {
                    chs += values.get(i);
                    chs += (i!=values.size()-1)?", ":"";
                }
                return "\n"+varnm+".get(0).sendKeys(Keys.chord("+chs+"));" + (!noblur?"":"\nblur("+varnm+".get(0));\n");
            }
            return "";
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
        		"Type UTF-8 or normal ASCII characters in input/textarea elements",
        		"\tchord(bl|ch|bk|cl|fo) {utf-8 character1}{utf-8 character2}...{utf-8 characterN} {find-expr}",
        		"Examples :-",
        		"\tchord \\u0048\\u0065\\u006c\\u006c\\u006f\\u0020\\u0057\\u006f\\u0072\\u006c\\u0064 id@'abc'",
            };
        }
    }
