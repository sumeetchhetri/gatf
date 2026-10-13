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

/** G8 — promoted verbatim from the nested Command.WaitTillElementVisibleOrInvisibleCommand (inherits the same members/nested types). */
@SuppressWarnings("unused")
public class WaitTillElementVisibleOrInvisibleCommand extends FindCommandImpl {
        boolean isVisible;
        /** M2: {@code ??~} — a failure is recorded (SeleniumTest.__soft_fail__) and the script continues. */
        boolean soft;
        int counter = state.timeoutNum;
        WaitTillElementVisibleOrInvisibleCommand(String time, String val, Object[] cmdDetails, CommandState state, boolean isVisible, boolean forced) {
            super(cmdDetails, state);
            this.isVisible = isVisible;
            try {
				if(Integer.valueOf(time)>0) {
					counter = Integer.valueOf(time);
					counter = (int)(counter*1000/state.timeoutSleepGranularity);
				}
			} catch (Exception e) {
			}
            String[] parts = val.trim().split("[\t ]+");
            if(forced) {
            	String[] parts1 = Arrays.asList(parts).toArray(new String[parts.length]);
            	if(parts1.length>2) {
            		if(!parts[0].toLowerCase().equals("actions")) {
            			if((parts[0].toLowerCase().matches(typeExStr) && !parts[0].toLowerCase().startsWith("randomize")) 
            					|| parts[0].toLowerCase().equals("select") || parts[0].toLowerCase().equals("upload")) {
	    	            	parts[0] = parts1[2];
	    	            	parts[1] = parts1[0];
	    	            	parts[2] = parts1[1];
            			} else {
            				parts[0] = parts1[1];
        	            	parts[1] = parts1[0];
            			}
            		}
            	} else if(parts1.length>1 && !parts[0].toLowerCase().equals("actions")) {
	            	parts[0] = parts1[1];
	            	parts[1] = parts1[0];
            	}
            }
            String cmd = parts[0];
            if(parts.length>1) {
                if (parts[1].toLowerCase().matches(typeExStr) || parts[1].toLowerCase().equals("select") 
                        || parts[1].toLowerCase().matches(clickExStr) || parts[1].toLowerCase().equals("hover")
                        || parts[1].toLowerCase().equals("hoverclick") || parts[1].toLowerCase().equals("clear")
                        || parts[1].toLowerCase().equals("submit") || parts[1].toLowerCase().equals("upload")
                        || parts[1].toLowerCase().equals("doubleclick") || parts[1].toLowerCase().equals("dblclick")) {
                    cmd = "";
                    for (int i = 1; i < parts.length; i++)
                    {
                        cmd += parts[i] + " ";
                    }
                    if(parts[1].equalsIgnoreCase("hoverclick")) {
                    	// Δ145c: `?? <loc> hoverclick` built no action (a silent no-op); hoverclick waits for both of its elements itself
                    	throwParseError(null, new RuntimeException("hoverclick cannot follow a ?? wait; write `hoverclick <hover-locator> <click-locator>` (it waits for both elements)"));
                    }
                    String cstr = parts[0];
                    if((parts[1].toLowerCase().matches(typeExStr) && !parts[1].toLowerCase().startsWith("randomize")) 
        					|| parts[1].toLowerCase().equals("select") || parts[1].toLowerCase().equals("upload")) {
                    	cstr = Stream.<String>of(parts).skip(3).collect(Collectors.joining(" "));
                    	cstr = parts[0] + " " +cstr;
                    	cmd = Stream.<String>of(parts).skip(1).limit(2).collect(Collectors.joining(" "));
                    } else if(!parts[1].toLowerCase().startsWith("randomize")) {
                    	cstr = Stream.<String>of(parts).skip(2).collect(Collectors.joining(" "));
                    	cstr = parts[0] + " " + cstr;
                    	cmd = Stream.<String>of(parts).skip(1).limit(1).collect(Collectors.joining(" "));
                    }
                    
                    if(!parts[0].equals("this") && !parts[0].equals("current") && !parts[0].equals("active") && parts[0].indexOf("@")==-1) {
                    	throwParseError(null, new RuntimeException("No selector condition specified"));
                    }
                    cond = new FindCommand(cstr, cmdDetails, state);
                    cmd = cmd.trim();
                    if(!cmd.isEmpty()) {
                        if (cmd.toLowerCase().toLowerCase().matches(typeSwStr) || cmd.toLowerCase().startsWith("select ") 
                        		|| cmd.toLowerCase().matches(clickExStr) || cmd.toLowerCase().equals("hover")
                                || cmd.toLowerCase().equals("doubleclick") || cmd.toLowerCase().equals("dblclick")   // Δ145c: was dropped → wait, no double-click
                                || cmd.toLowerCase().equals("clear")
                                || cmd.toLowerCase().equals("submit") || cmd.toLowerCase().startsWith("upload ")) {
                            //cmd = unsanitize(cmd);
                            Command comd = handleActions(cmd, cond, cmdDetails, state);
                            if(comd!=null) children.add(comd);
                        }
                    }
                } else if(cmd.equalsIgnoreCase("actions")) {
                	Command comd = handleActions(val.trim(), null, cmdDetails, state);
                    children.add(comd);
                } else if(parts[1].toLowerCase().equalsIgnoreCase("actions")) {
                	if(!parts[0].equals("this") && !parts[0].equals("active") && !parts[0].equals("current") && parts[0].indexOf("@")==-1) {
                    	throwParseError(null, new RuntimeException("No selector condition specified"));
                    }
                	cmd = "actions moveto " + parts[0] + " ";
                    for (int i = 2; i < parts.length; i++)
                    {
                        cmd += parts[i] + " ";
                    }
                    //cond = new FindCommand(parts[0], cmdDetails, state);
                	Command comd = handleActions(cmd.trim(), null, cmdDetails, state);
                    children.add(comd);
                } else {
                    cmd = val;
                    cond = new FindCommand(cmd, cmdDetails, state);
                }
            } else if(cmd.equalsIgnoreCase("actions")) {
            	Command comd = handleActions(val.trim(), null, cmdDetails, state);
                children.add(comd);
            } else {
                cmd = val;
                cond = new FindCommand(parts[0], cmdDetails, state);
            }
        }
        String toCmd() {
            StringBuilder b = new StringBuilder();
            b.append(soft?"??~":"??"+(isVisible?"+":"-"));
            b.append(cond.toCmd());
            return b.toString();
        }
        String javacode() {
            StringBuilder b = new StringBuilder();
            if(cond!=null) {
            	b.append(cond.javacodeonlyNoAssert(children, false, counter, isVisible));
	            //String cntvar = state.varnamerandom();
	            //if(!isVisible) {
		            //b.append("int "+cntvar+" = 0;\n");
		            //b.append("\nwhile(true) {\n");
		            //b.append(cond.javacodeonlyNoAssert(children, false, counter, isVisible));
		            //b.append("if("+cond.getActionableVar()+"!=null)break;\n");
		            //b.append("sleep("+state.timeoutSleepGranularity+");\n");
		            //b.append("if("+cntvar+"++=="+counter+")throw new RuntimeException("+cond.getErr()+");\n");
		            //b.append("}\n");
	            //} else {
	            	//b.append("int "+cntvar+" = 0;\n");
		            //b.append("\nwhile(true) {\n");
		            //b.append("\nsleep(1000);\n");
		            //b.append(cond.javacodeonlyNoAssert(children, false, counter, isVisible));
		            //b.append("if("+cond.getActionableVar()+"==null)break;\n");
		            //b.append("sleep("+state.timeoutSleepGranularity+");\n");
		            //b.append("if("+cntvar+"++=="+counter+")throw new RuntimeException("+cond.getErr()+");\n");
		            //b.append("}\n");
	            //}
            }
            if(children!=null && children.size()>0) {
                Command c = children.get(0);
                if(c instanceof ActionsCommand || c instanceof RobotCommand) {
                    b.append(c.javacode());
                }
            }
            if(soft) {
            	return "try {\n" + b + "} catch (RuntimeException ___sf___) {\n__soft_fail__(___sf___);\n}\n";
            }
            /*for (Command command : children) {
                if(command instanceof RandomizeCommand) {
                    b.append(((RandomizeCommand)command).javacodeonly(cond.getActionableVar()));
                } else if(command instanceof SelectCommand) {
                	b.append(((SelectCommand)command).javacodeonly(cond.getActionableVar()));
                } else if(command instanceof TypeCommand) {
                	b.append(((TypeCommand)command).javacodeonly(cond.getActionableVar()));
                } else if(command instanceof ClickCommand) {
                	b.append(((ClickCommand)command).javacodeonly(cond.getActionableVar()));
                } else if(command instanceof SubmitCommand) {
                	b.append(((SubmitCommand)command).javacodeonly(cond.getActionableVar()));
                } else if(command instanceof ClearCommand) {
                	b.append(((ClearCommand)command).javacodeonly(cond.getActionableVar()));
                }
            }
            b.append("\n");*/
            return b.toString();
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
				"Wait for element to be found / no longer found",
				"\t??(+|-) {find-expr}",
				"\t'+' - wait till the element is no longer found (same as: wait till visible {find-expr})",
				"\t'-' - wait till the element is found, like ?? (same as: wait for {find-expr}); neither checks visibility",
				"\t??~ {find-expr} {assertion} - soft assertion: a failure is recorded, the script continues and fails at the end",
				"Examples :-",
	    		"\t??+ id@'eleid'",
	    		"\t??- id@'eleid'",
	    		"\t??~ css@'h1' text ='Welcome'",
            };
        }
    }
