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

/** G8 — promoted verbatim from the nested Command.ActionsCommand (inherits the same members/nested types). */
@SuppressWarnings("unused")
public class ActionsCommand extends FindCommandImpl {
        String action;
        String expr1;
        String expr2;
        String expr3;
        static final String ALLCMDS = "click|clickandhold|clickhold|release|dblclick|doubleclick|contextclick|rightclick|movetoelement|moveto|keydown|keyup"
                + "|sendkeys|type|chord|randomize|draganddrop|dragdrop|draganddrop1|dragdrop1|movebyoffset|moveby|sleep|";
        public ActionsCommand(Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
        		"Multiple Chained Actions",
        		"\tactions movetoelement|moveto {find-expr} ({click|clickandhold|release|dblclick|doubleclick|contextclick|clickhold|rightclick} {find-expr}?|"
        		+ "{keydown|keyup|sendkeys|type {value}}|{movetoelement|moveto {find-expr}}|{draganddrop|dragdrop|draganddrop1|dragdrop1 {find-expr} {find-expr}}|"
        		+ "randomize {alphanumeric|alpha|alphanumericlc|alphalc|alphanumericuc|alphauc|numeric|value|range|prefixed|prefixed_|fuzzyn|fuzzya|fuzzyauc|fuzzyalc|fuzzyan|fuzzyanuc|fuzzyanlc} {arg1} {arg2} {arg3}?}|"
        		+ "{movebyoffset|moveby {x-offset} {y-offset}}) ... movetoelement|moveto {find-expr} ... ({click|clickan...",
        		"Examples :-",
        		"\tactions movetoelement id@'ele' click moveto id@'ele2' clickandhold moveto id@'ele3' release type '123'",
        		"\tactions movetoelement id@'ele' sendkeys 'abc'",
            };
        }
        ActionsCommand(String val, FindCommand fcmd, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            String[] t = val.trim().split("[\t ]+");
            int tl = t.length, counter = 0;
            ActionsCommand cmd = this;
            this.cond = fcmd;
            boolean moveDone = false;
            while(counter<tl) {
                if(t[counter].toLowerCase().matches("click|clickandhold|release|dblclick|doubleclick|contextclick|clickhold|rightclick")) {
                    cmd.action = t[counter].toLowerCase();
                    if(cmd.action.equalsIgnoreCase("clickhold") || cmd.action.equalsIgnoreCase("clickandhold")) {
                        cmd.action = "clickAndHold";
                    } else if(cmd.action.equalsIgnoreCase("rightclick") || cmd.action.equalsIgnoreCase("contextclick")) {
                        cmd.action = "contextClick";
                    } else if(cmd.action.equalsIgnoreCase("doubleclick") || cmd.action.equalsIgnoreCase("dblclick")) {
                        cmd.action = "doubleClick";
                    }
                    if(tl>counter+1 && !t[counter+1].toLowerCase().matches(ALLCMDS)) {
                        cmd.expr1 = state.unsanitize(t[++counter]);
                        if(cmd.expr1.charAt(0)==cmd.expr1.charAt(cmd.expr1.length()-1)) {
                            if(cmd.expr1.charAt(0)=='"' || cmd.expr1.charAt(0)=='\'') {
                                cmd.expr1 = cmd.expr1.substring(1, cmd.expr1.length()-1);
                            }
                        }
                    }
                } else if(t[counter].toLowerCase().matches("movetoelement|moveto")) {
                    cmd.action = t[counter].toLowerCase();
                    if(cmd.action.equalsIgnoreCase("moveto") || cmd.action.equalsIgnoreCase("movetoelement")) {
                        cmd.action = "moveToElement";
                    }
                    if(!moveDone && cond!=null && ((tl>counter+1 && t[counter+1].toLowerCase().matches(ALLCMDS)) || tl==counter+1)) {
                        moveDone = true;
                    } else if(tl>counter+1) {
                        cmd.expr1 = state.unsanitize(t[++counter]);
                        if(cmd.expr1.charAt(0)==cmd.expr1.charAt(cmd.expr1.length()-1)) {
                            if(cmd.expr1.charAt(0)=='"' || cmd.expr1.charAt(0)=='\'') {
                                cmd.expr1 = cmd.expr1.substring(1, cmd.expr1.length()-1);
                            }
                        }
                    } else {
                        throwParseError(null, new RuntimeException("Expression expected after actions (movetoelement|moveto)"));
                    }
                } else if(t[counter].toLowerCase().matches("keydown|keyup|sendkeys|type|chord")) {
                    cmd.action = t[counter].toLowerCase();
                    if(cmd.action.equalsIgnoreCase("type") || cmd.action.equalsIgnoreCase("sendkeys") || cmd.action.equalsIgnoreCase("chord")) {
                        cmd.action = "sendKeys";
                    } else if(cmd.action.equalsIgnoreCase("keydown")) {
                        cmd.action = "keyDown";
                    } else if(cmd.action.equalsIgnoreCase("keyup")) {
                        cmd.action = "keyUp";
                    }
                    if(tl>counter+1) {
                        cmd.expr1 = state.unsanitize(t[++counter]);
                        if(cmd.expr1.charAt(0)==cmd.expr1.charAt(cmd.expr1.length()-1)) {
                            if(cmd.expr1.charAt(0)=='"' || cmd.expr1.charAt(0)=='\'') {
                                cmd.expr1 = cmd.expr1.substring(1, cmd.expr1.length()-1);
                            }
                        }
                    } else {
                        throwParseError(null, new RuntimeException("Expression expected after actions (keydown|keyup|sendkeys|type|chord)"));
                    }
                } else if(t[counter].toLowerCase().matches("draganddrop|dragdrop|draganddrop1|dragdrop1|movebyoffset|moveby")) {
                    cmd.action = t[counter].toLowerCase();
                    if(cmd.action.equalsIgnoreCase("moveby") || cmd.action.equalsIgnoreCase("movebyoffset")) {
                        cmd.action = "moveByOffset";
                    } else if(cmd.action.equalsIgnoreCase("dragdrop") || cmd.action.equalsIgnoreCase("draganddrop")) {
                        cmd.action = "dragAndDrop";
                    } else if(cmd.action.equalsIgnoreCase("dragdrop1") || cmd.action.equalsIgnoreCase("draganddrop1")) {
                        cmd.action = "dragAndDrop1";
                    }
                    int fc = 0;
                    for (int i = 1; i < 3; i++)
                    {
                        if(tl<=counter+i)break;
                        if(t[counter+i].toLowerCase().matches(ALLCMDS)) {
                            break;
                        }
                        fc++;
                    }
                    if(fc==2) {
                        cmd.expr1 = unSantizedUnQuoted(t[++counter], state);
                        cmd.expr2 = unSantizedUnQuoted(t[++counter], state);
                    } else {
                        throwParseError(null, new RuntimeException("Expressions(2) expected after actions (dragAndDrop|moveByOffset)"));
                    }
                } else if(t[counter].toLowerCase().equals("randomize")) {
                    cmd.action = t[counter].toLowerCase();
                    if(t.length<counter+1 || !t[counter+1].toLowerCase().toLowerCase().matches("alphanumeric|alpha|alphanumericlc|alphalc|alphanumericuc|alphauc|numeric|value|range|prefixed|prefixed_|fuzzyn|fuzzya|fuzzyauc|fuzzyalc|fuzzyan|fuzzyanuc|fuzzyanlc")) {
                    	throwParseError(null, new RuntimeException("Randomize command needs to define a type that can only be one of alphanumeric|alpha|alphanumericlc|alphalc|alphanumericuc|alphauc|numeric|value|range|prefixed|prefixed_|fuzzyn|fuzzya|fuzzyauc|fuzzyalc|fuzzyan|fuzzyanuc|fuzzyanlc"));
                    }
                    
                    cmd.expr1 = unSantizedUnQuoted(t[++counter], state);
                    try {
                    	cmd.expr2 = unSantizedUnQuoted(t[counter+1], state);
                        ++counter;
                        Long.parseLong(cmd.expr2);
        			} catch (Exception e) {
        				if(cmd.expr2!=null && !cmd.expr1.equalsIgnoreCase("value")) {
        					if(cmd.expr1.startsWith("fuzzy")) {
        						if(cmd.expr2.length()>4 && cmd.expr2.charAt(1)=='@' && cmd.expr2.contains(":")) {
        						} else {
        							throwParseError(null, new RuntimeException("Randomize command with fuzzy logic needs the syntax fuzzy <sep-char>@<seq-len>:<seq-len>:<seq-len> --> _@3:4:5 --> 333_4444_55555"));
        						}
        					} else {
        						throwParseError(null, new RuntimeException("Randomize command needs first argument to be a number for alphanumeric|alpha|alphanumericlc|alphalc|alphanumericuc|alphauc|numeric|range"));
        					}
        					throwParseError(null, new RuntimeException("Randomize command needs first argument to be a number for alphanumeric|alpha|alphanumericlc|alphalc|alphanumericuc|alphauc|numeric|range"));
        				}
        			}
                    
                    try {
                    	cmd.expr3 = unSantizedUnQuoted(t[counter], state);
                        Long.parseLong(cmd.expr3);
        				++counter;
        			} catch (Exception e) {
        				if(cmd.expr3!=null && !cmd.expr3.matches(ALLCMDS)) {
        					throwParseError(null, new RuntimeException("Randomize command needs second argument to be a number for alphanumeric|alpha|alphanumericlc|alphalc|alphanumericuc|alphauc|numeric|range"));
        				}
        				cmd.expr3 = null;
        			}
                } else if(t[counter].toLowerCase().equals("sleep")) { 
                	cmd.action = "sleep";
                	try {
						Long.valueOf(unSantizedUnQuoted(t[counter+1], state));
						cmd.expr1 = unSantizedUnQuoted(t[++counter], state);
					} catch (Exception e) {
						throwParseError(null, new RuntimeException("Invalid sleep duration"));
					}
                } else {
                    throwParseError(null, new RuntimeException("Invalid action ["+t[counter]+"] specified, should be one of (click|clickhold|clickAndHold|release|doubleClick|dblclick|contextClick|rightclick|keyDown|keyUp|sendKeys|type|randomize|moveToElement|dragAndDrop|dragdrop|moveByOffset|moveby)"));
                }
                ++counter;
                cmd.children.add(new ActionsCommand(cmdDetails, state));
                cmd = (ActionsCommand)cmd.children.get(0);
            }
            cmd = this;
            while(!cmd.children.isEmpty()) {
                ActionsCommand tcmd = (ActionsCommand)cmd.children.get(0);
                if(tcmd.action==null) {
                    cmd.children.clear();
                }
                cmd = tcmd;
            }
        }
        String toCmd() {
            return "action ";
        }
        // M1: auto-wait probe (hover mode) before an element step; emitted only for autoWait projects
        private String probe(String el, boolean on) {
            return on && state.autoWait ? "\nawaitActionable(___cw___, "+el+", \"hover\");" : "";
        }
        String _javacode(String acnm) {
            StringBuilder b = new StringBuilder();
            if(action.toLowerCase().matches("click|clickandhold|release|doubleclick|contextclick")) {
            	boolean hold = action.equals("clickAndHold");
            	if(cond!=null && expr1==null) {
                    b.append(probe(cond.getActionableVar()+".get(0)", hold));
                    b.append("\n"+acnm+"."+action+"("+cond.getActionableVar()+".get(0));");
                } else if(expr1==null) {
                    b.append("\n"+acnm+"."+action+"();");
                } else {
                	if(expr1.equals("this")) {
                		b.append(probe(state.currthisat()+".get(0)", hold));
                		b.append("\n"+acnm+"."+action+"("+state.currthisat()+".get(0));");
                	} else if(expr1.equals("current")) {
                		b.append(probe("___ce___.get(0)", hold));
                		b.append("\n"+acnm+"."+action+"(___ce___.get(0));");
                	} else {
	                	String ck = state.unsanitize(expr1);
	                    FindCommand fc = new FindCommand(ck, fileLineDetails, state);
	                    //int counter = state.timeoutNum;
	                    //String cntvar = state.varnamerandom();
	                    //b.append("int "+cntvar+" = 0;\n");
	                    //b.append("\nwhile(true) {\n");
	                    b.append(fc.javacodeonly());
	                    //b.append("if("+fc.getActionableVar()+"!=null)break;\n");
	                    //b.append("sleep("+state.timeoutSleepGranularity+");\n");
	                    //b.append("if("+cntvar+"++=="+counter+")throw new RuntimeException("+fc.getErr()+");\n");
	                    //b.append("}\n");
	                    b.append(probe(fc.getActionableVar()+".get(0)", hold));
	                    b.append("\n"+acnm+"."+action+"("+fc.getActionableVar()+".get(0));");
                	}
                }
            } else if(action.toLowerCase().matches("keydown|keyup")) {
                String ck = state.unsanitize(expr1);
                b.append("\n"+acnm+"."+(action.equalsIgnoreCase("keydown")?"keyDown":"keyUp")+"(Keys.getKeyFromUnicode('"+ck+"'));");
            } else if(action.toLowerCase().matches("sendkeys") || action.toLowerCase().matches("type") || action.toLowerCase().matches("chord")) {
                String ck = state.unsanitize(expr1);
                b.append("\n"+acnm+".sendKeys(evaluate(\""+ck+"\"));");
            } else if(action.toLowerCase().matches("randomize")) {
                String v1 = state.unsanitize(expr1);
                String v2 = expr2!= null?state.unsanitize(expr2):"";
                String v3 = expr3!= null?state.unsanitize(expr3):"";
                v1 = esc(v1) + " " + esc(v2) + " " + esc(v3);
                b.append("\n"+acnm+".sendKeys(randomize(\""+v1.trim()+"\"));");
            } else if(action.toLowerCase().matches("movetoelement")) {
                if(cond!=null && expr1==null) {
                    b.append(probe(cond.getActionableVar()+".get(0)", true));
                    b.append("\n"+acnm+"."+action+"("+cond.getActionableVar()+".get(0));");
                } else if(expr1==null) {
                    b.append(probe("___ce___.get(0)", true));
                    b.append("\n"+acnm+"."+action+"(___ce___.get(0));");
                } else {
                	if(expr1.equals("this")) {
                		b.append(probe(state.currthisat()+".get(0)", true));
                		b.append("\n"+acnm+"."+action+"("+state.currthisat()+".get(0));");
                	} else if(expr1.equals("current")) {
                		b.append(probe("___ce___.get(0)", true));
                		b.append("\n"+acnm+"."+action+"(___ce___.get(0));");
                	} else {
	                    String ck = state.unsanitize(expr1);
	                    FindCommand fc = new FindCommand(ck, fileLineDetails, state);
	                    //int counter = state.timeoutNum;
	                    //String cntvar = state.varnamerandom();
	                    //b.append("int "+cntvar+" = 0;\n");
	                    //b.append("\nwhile(true) {\n");
	                    b.append(fc.javacodeonly());
	                    //b.append("if("+fc.getActionableVar()+"!=null)break;\n");
	                    //b.append("sleep("+state.timeoutSleepGranularity+");\n");
	                    //b.append("if("+cntvar+"++=="+counter+")throw new RuntimeException("+fc.getErr()+");\n");
	                    //b.append("}\n");
	                    //b.append("\n"+fc.javacodeonly(null));
	                    b.append(probe(fc.getActionableVar()+".get(0)", true));
	                    b.append("\n"+acnm+"."+action+"("+fc.getActionableVar()+".get(0));");
                	}
                }
            } else if(action.toLowerCase().matches("draganddrop1")) {
                String ck = state.unsanitize(expr1);
                FindCommand fc = new FindCommand(ck, fileLineDetails, state);
                //int counter = state.timeoutNum;
                //String cntvar = state.varnamerandom();
                //b.append("int "+cntvar+" = 0;\n");
                //b.append("\nwhile(true) {\n");
                b.append(fc.javacodeonly());
                //b.append("if("+fc.getActionableVar()+"!=null)break;\n");
                //b.append("sleep("+state.timeoutSleepGranularity+");\n");
                //b.append("if("+cntvar+"++=="+counter+")throw new RuntimeException("+fc.getErr()+");\n");
                //b.append("}\n");
                b.append("List<WebElement> " + state.varname() + " = ___ce___;\n");
                String cvarnm = state.currvarname();
                
                String ck1 = state.unsanitize(expr2);
                FindCommand fc1 = new FindCommand(ck1, fileLineDetails, state);
                //counter = state.timeoutNum;
                //cntvar = state.varnamerandom();
                //b.append("int "+cntvar+" = 0;\n");
                //b.append("\nwhile(true) {\n");
                b.append(fc1.javacodeonly());
                //b.append("if("+fc1.getActionableVar()+"!=null)break;\n");
                //b.append("sleep("+state.timeoutSleepGranularity+");\n");
                //b.append("if("+cntvar+"++=="+counter+")throw new RuntimeException("+fc1.getErr()+");\n");
                //b.append("}\n");
                b.append("List<WebElement> " + state.varname() + " = ___ce___;\n");
                String cvarnm1 = state.currvarname();
                
                if(state.autoWait) b.append("\nawaitActionable(___cw___, "+cvarnm+".get(0), \"drag\");");
                b.append("\n"+acnm+".dragAndDrop("+cvarnm+".get(0), "+cvarnm1+".get(0));");
            } else if(action.toLowerCase().matches("draganddrop")) {
                String ck = state.unsanitize(expr1);
                FindCommand fc = new FindCommand(ck, fileLineDetails, state);
                //int counter = state.timeoutNum;
                //String cntvar = state.varnamerandom();
                //b.append("int "+cntvar+" = 0;\n");
                //b.append("\nwhile(true) {\n");
                b.append(fc.javacodeonly());
                //b.append("if("+fc.getActionableVar()+"!=null)break;\n");
                //b.append("sleep("+state.timeoutSleepGranularity+");\n");
                //b.append("if("+cntvar+"++=="+counter+")throw new RuntimeException("+fc.getErr()+");\n");
                //b.append("}\n");
                b.append("List<WebElement> " + state.varname() + " = ___ce___;\n");
                String cvarnm = state.currvarname();
                
                String ck1 = state.unsanitize(expr2);
                FindCommand fc1 = new FindCommand(ck1, fileLineDetails, state);
                //counter = state.timeoutNum;
                //cntvar = state.varnamerandom();
                //b.append("int "+cntvar+" = 0;\n");
                //b.append("\nwhile(true) {\n");
                b.append(fc1.javacodeonly());
                //b.append("if("+fc1.getActionableVar()+"!=null)break;\n");
                //b.append("sleep("+state.timeoutSleepGranularity+");\n");
                //b.append("if("+cntvar+"++=="+counter+")throw new RuntimeException("+fc1.getErr()+");\n");
                //b.append("}\n");
                b.append("List<WebElement> " + state.varname() + " = ___ce___;\n");
                String cvarnm1 = state.currvarname();
                
                if(state.autoWait) b.append("\nawaitActionable(___cw___, "+cvarnm+".get(0), \"drag\");");
                b.append("\n"+acnm+".clickAndHold("+cvarnm+".get(0)).moveToElement("+cvarnm1+".get(0)).release("+cvarnm1+".get(0));");
            } else if(action.toLowerCase().matches("movebyoffset")) {
                try {
                    int ck = Integer.parseInt(state.unsanitize(expr1));
                    int ck1 = Integer.parseInt(state.unsanitize(expr2));
                    b.append("\n"+acnm+"."+action+"("+ck+", "+ck1+");");
                } catch (Exception e) {
                    throwParseError(null, new RuntimeException("xOffset and yOffset need to be integer values for moveByOffset"));
                }
            } else if(action.toLowerCase().matches("sleep")) {
                try {
                    b.append("\nsleep("+expr1+");");
                } catch (Exception e) {
                }
            }
            if(!children.isEmpty()) {
                ActionsCommand tcmd = (ActionsCommand)children.get(0);
                b.append(tcmd._javacode(acnm));
            }
            return b.toString();
        }
        String javacode() {
            StringBuilder b = new StringBuilder();
            String acnm = state.varname();
            b.append("\norg.openqa.selenium.interactions.Actions "+acnm+" = new org.openqa.selenium.interactions.Actions(___cw___);");
            if(cond!=null) {
                b.append(probe(cond.getActionableVar()+".get(0)", true));
                b.append("\n"+acnm+".moveToElement("+cond.getActionableVar()+".get(0));");
            }
            b.append(_javacode(acnm));
            b.append("\n"+acnm+".perform();");
            return b.toString();
        }
        String javacode(String elnm, boolean a) {
            StringBuilder b = new StringBuilder();
            String acnm = state.varname();
            b.append("\norg.openqa.selenium.interactions.Actions "+acnm+" = new org.openqa.selenium.interactions.Actions(___cw___);");
            if(cond!=null) {
                b.append(probe(elnm+".get(0)", true));
                b.append("\n"+acnm+".moveToElement("+elnm+".get(0));");
            }
            b.append(_javacode(acnm));
            b.append("\n"+acnm+".perform();");
            return b.toString();
        }
    }
