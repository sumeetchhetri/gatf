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

/** G8 — promoted verbatim from the nested Command.TabCommand (inherits the same members/nested types). */
@SuppressWarnings("unused")
public class TabCommand extends Command {
        // Δ145c: the `timeout ... <granularity>` in effect where this tab is written (as FindCommand): a later
        // `timeout` line must not change this statement's retry sleep
        long sleepGranularity = state.timeoutSleepGranularity;
        // Δ145d: and the retry count (`timeout` seconds) in effect here, for the same reason
        int timeoutNum = state.timeoutNum;
        TabCommand(String cmd, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            name = unSantizedUnQuoted(cmd, state);
            if("main".equalsIgnoreCase(name)) {
            	name = "0";
            }
        }
        String name() {
            return name;
        }
        String toCmd() {
            return "tab " + name;
        }
        String javacode() {
            if(name.trim().isEmpty() || name.equalsIgnoreCase("0")) {
            	StringBuilder b = new StringBuilder();
            	b.append("___ocw___.switchTo().window(__handles__(___ocw___).iterator().next());\n___cw___ = ___ocw___;\n___sc___1 = ___cw___;\n");
            	if(children.size()>0) {
            		for (Command c : children) {
                    	b.append(genDebugInfo(c));
                        b.append(c.javacode());
                        b.append("\n");
                    }
            	}
            	return b.toString();
            } else {
            	if(children.size()==0) {
            		StringBuilder b = new StringBuilder();
                    String cntvar = state.varnamerandom();
                    b.append("int "+cntvar+" = 0;\n");
                    b.append("\nwhile(true) {\n");
                    b.append("\ntry {\n");
                    try {
                        int index = Integer.parseInt(name);
                        String acvn = state.varname();
                        String whl = "List<String> "+acvn+" = new java.util.ArrayList<String> (__handles__(___ocw___));\n"
                                + "if("+state.currvarname()+"!=null && "+index+">=0 && "+state.currvarname()+".size()>"+index+")\n{\n";
                        b.append(whl + "___cw___ = ___ocw___.switchTo().window("+acvn+".get("+index+"));\n___sc___1 = ___cw___;\nbreak;}");
                    } catch (Exception e) {
                        b.append("___cw___ = ___ocw___.switchTo().window(\""+esc(name)+"\");\n___sc___1 = ___cw___;\nbreak;\n");
                    }
                    b.append("\n} catch(Exception e){}\n");
                    b.append("sleep("+sleepGranularity+");\n");
                    b.append("if("+cntvar+"++=="+timeoutNum+")throw new RuntimeException(\"Unable to move to tab ("+name+")\");\n");
                    b.append("}\n");
                    return b.toString();
            	}
            	StringBuilder b = new StringBuilder();
                String cntvar = state.varnamerandom();
                b.append("int "+cntvar+" = 0;\n");
                b.append("\nwhile(true) {\n");
                b.append("\ntry {\n");
                try {
                    int index = Integer.parseInt(name);
                    String acvn = state.varname();
                    String whl = "List<String> "+acvn+" = new java.util.ArrayList<String> (__handles__(___ocw___));\n"
                            + "if("+state.currvarname()+"!=null && "+index+">=0 && "+state.currvarname()+".size()>"+index+")\n{\n";
                    b.append(whl + "___cw___ = ___ocw___.switchTo().window("+acvn+".get("+index+"));\n___sc___1 = ___cw___;}");
                } catch (Exception e) {
                    b.append("___cw___ = ___ocw___.switchTo().window(\""+esc(name)+"\");\n___sc___1 = ___cw___;\n");
                }

            	if(children.size()>0) {
            		for (Command c : children) {
                    	b.append(genDebugInfo(c));
                        b.append(c.javacode());
                        b.append("\n");
                    }
            	}
                b.append("\nbreak;\n} catch(Exception e){}\n");
                b.append("sleep("+sleepGranularity+");\n");
                b.append("if("+cntvar+"++=="+timeoutNum+")throw new RuntimeException(\"Unable to move to tab ("+name+")\");\n");
                b.append("}\n");
                b.append("___ocw___.switchTo().window(__handles__(___ocw___).iterator().next());\n___cw___ = ___ocw___;\n___sc___1 = ___cw___;\n");
                return b.toString();
            }
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
        		"Select a tab or use a scoped tab block",
        		"\ttab main|0..N|{some-name}",
				"Examples :-",
				"\ttab main",
				"\ttab 0",
				"\ttab 2",
				"\ttab \"my-frame\"",
				"\ttab\n\t{...\n\t}"
            };
        }
    }
