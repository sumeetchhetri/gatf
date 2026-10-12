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

/**
 * G8 — seleasy element-finder command codegen, promoted verbatim from the nested Command.FindCommand
 * (still extends Command, so it inherits the same members and nested types it used before).
 */
@SuppressWarnings("unused")
public class FindCommand extends Command {
        // the `timeout ... <granularity>` in effect where this find is written: a later `timeout` line must not
        // retroactively change this wait (counter was computed with this value)
        long sleepGranularity = state.timeoutSleepGranularity;
        String by, classifier, relative = "", by1 = "", classifier1 = "", subselector, condvar = "true", topele, rtl, precond = "", postcond = "", cfiltvar = null, oper = null, operval = null, eval = null, browserScope = "", sessionScope = "";
        boolean suppressErr = false, byselsame = false, classifierPinvXp = false, classifier1PinvXp = false;
        String[] classifierPinvXps = null, classifier1PinvXps = null;
        String by() {
            return by;
        }
        String classifier() {
            return classifier;
        }
        String subselector() {
            return subselector;
        }
        String topele() {
            return topele;
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
        		"Find Expression",
        		"\t{eval|browser-scope|session-scope|relative}? {id|name|class|xpath|tag|cssselector|css|text|partialLinkText|linkText|jq|$|jquery|role|label|placeholder|testid|alt|title|active|this|current}(@selector) (title|currentUrl|pageSource|width|height|xpos|ypos|alerttext) {matching-value|matching-value-in-list}",
        		"\tsemantic: role@button[name=Save][level=N][checked] label@Email placeholder@Search testid@id alt@Logo title@Close text@=exact text@/regex/i",
        		"\tvalue: plain = whole text, case-insensitive | ~x = contains | =x = exact | /re/flags = regex",
        		"Examples :-",
        		"\tclick role@button[name=Save]",
        		"\ttype 'me@example.com' label@'Email address'",
        		"\t?? text@'=Order placed'",
            };
        }
        FindCommand(String val, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            String[] parts = val.trim().split("[\t ]+");
            if(parts[0].equalsIgnoreCase("eval")) {
            	eval = val.trim().substring(5).trim();
            	by = "";
            	classifier = "";
            } else if(parts[0].equalsIgnoreCase("browser-scope")) {
            	browserScope = val.trim().substring(14).trim();
            	by = "";
            	classifier = "";
            } else if(parts[0].equalsIgnoreCase("session-scope")) {
            	sessionScope = val.trim().substring(14).trim();
            	by = "";
            	classifier = "";
            } else if(parts[0].equalsIgnoreCase("relative")) {
            	if(parts.length<4) {
            		throwError(cmdDetails, new RuntimeException("Invalid relative selector expression, relative selector should confirm to `relative xpath@expr {above|below|leftof|rightof|near} xpath@expr1`"));
            	}
            	if(parts[1].indexOf("@")!=-1) {
                    by1 = parts[1].substring(0, parts[1].indexOf("@")).trim();
                    by1 = state.unsanitize(by1);
                    classifier1 = parts[1].substring(parts[1].indexOf("@")+1).trim();
                    classifier1 = state.unsanitize(classifier1);
                    if(classifier1.charAt(0)==classifier1.charAt(classifier1.length()-1)) {
                        if(classifier1.charAt(0)=='"' || classifier1.charAt(0)=='\'') {
                        	classifier1 = classifier1.substring(1, classifier1.length()-1);
                        }
                    }
                	if(by1.equalsIgnoreCase("xpath")) {
                    	try {
    						state.factory.newXPath().compile(classifier1);
    					} catch (XPathExpressionException e) {
    						classifier1PinvXp = true;
    						classifier1PinvXps = new String[] {cmdDetails[0].toString(), cmdDetails[1].toString(), ((File)cmdDetails[2]).getAbsolutePath()};
    						System.out.println("Warning: Possible invalid xpath expression " + classifier1 + ", check if your xpath expression contains spaces and use single quotes(') instead of double quotes(\") for your xpath expressions");
    						//throwError(cmdDetails, new RuntimeException("Invalid xpath expression " + classifier));
    					}
                    }
            	}
            	if(!by1.toLowerCase().matches("tagname|tag") || classifier1.isEmpty()) {
            		throwError(cmdDetails, new RuntimeException("Invalid relative selector command, relative selector should confirm to `relative xpath@expr {above|below|leftof|rightof|near} tag@expr1`"));
            	}
            	if(!parts[2].equalsIgnoreCase("leftof") && !parts[2].equalsIgnoreCase("rightof") && !parts[2].equalsIgnoreCase("above")
            			&& !parts[2].equalsIgnoreCase("below") && !parts[2].equalsIgnoreCase("near")) {
            		throwError(cmdDetails, new RuntimeException("Invalid relative selector command, relative selector should confirm to `relative xpath@expr {above|below|leftof|rightof|near} tag@expr1`"));
            	}
            	if(parts[3].indexOf("@")!=-1) {
                    by = parts[3].substring(0, parts[3].indexOf("@")).trim();
                    by = state.unsanitize(by);
                    classifier = parts[3].substring(parts[3].indexOf("@")+1).trim();
                    classifier = state.unsanitize(classifier);
                    if(classifier.charAt(0)==classifier.charAt(classifier.length()-1)) {
                        if(classifier.charAt(0)=='"' || classifier.charAt(0)=='\'') {
                            classifier = classifier.substring(1, classifier.length()-1);
                        }
                    }
            	}
            	if(by.equalsIgnoreCase("xpath")) {
                	try {
						state.factory.newXPath().compile(classifier);
					} catch (XPathExpressionException e) {
						classifierPinvXp = true;
						classifierPinvXps = new String[] {cmdDetails[0].toString(), cmdDetails[1].toString(), ((File)cmdDetails[2]).getAbsolutePath()};
						System.out.println("Warning: Possible invalid xpath expression " + classifier + ", check if your xpath expression contains spaces and use single quotes(') instead of double quotes(\") for your xpath expressions");
						//throwError(cmdDetails, new RuntimeException("Invalid xpath expression " + classifier));
					}
                }
            	if(!by.toLowerCase().matches("id|name|class|classname|tag|tagname|xpath|text|partiallinktext|linktext|jquery|jq|\\$|css|cssselector|role|label|placeholder|testid|alt|title") || classifier.isEmpty()) {
            		throwError(cmdDetails, new RuntimeException("Invalid selector, should confirm to `{id|name|class|classname|tag|tagname|xpath|text||partiallinktext|linktext|jquery|jq|$|css|cssselector|role|label|placeholder|testid|alt|title}@expr`"));
            	}
            	checkSemantic(cmdDetails);   // M3: a semantic anchor is validated like a plain find
            	relative = parts[2].trim();
            } else {
	            if(parts.length>=1) {
	                parts[0] = parts[0].trim();
	                int nxindx = 2;
	                if(parts[0].indexOf("@")!=-1) {
	                    by = parts[0].substring(0, parts[0].indexOf("@")).trim();
	                    by = state.unsanitize(by);
	                    classifier = parts[0].substring(parts[0].indexOf("@")+1).trim();
	                    classifier = state.unsanitize(classifier);
	                    if(classifier.charAt(0)==classifier.charAt(classifier.length()-1)) {
	                        if(classifier.charAt(0)=='"' || classifier.charAt(0)=='\'') {
	                            classifier = classifier.substring(1, classifier.length()-1);
	                        }
	                    }
		                
	                    if(parts.length>1) {
	                        subselector = parts[1].trim();
	                        subselector = state.unsanitize(subselector);
	                    }
	                    
	                    if(by.equalsIgnoreCase("xpath")) {
	                    	try {
								state.factory.newXPath().compile(classifier);
							} catch (XPathExpressionException e) {
								classifierPinvXp = true;
								classifierPinvXps = new String[] {cmdDetails[0].toString(), cmdDetails[1].toString(), ((File)cmdDetails[2]).getAbsolutePath()};
								System.out.println("Warning: Possible invalid xpath expression " + classifier + ", check if your xpath expression contains spaces and use single quotes(') instead of double quotes(\") for your xpath expressions");
								//throwError(cmdDetails, new RuntimeException("Invalid xpath expression " + classifier));
							}
	                    }
	                	if(!by.toLowerCase().matches("id|name|class|classname|tag|tagname|xpath|text|partiallinktext|linktext|jquery|jq|\\$|css|cssselector|role|label|placeholder|testid|alt|title") || classifier.isEmpty()) {
	                		throwError(cmdDetails, new RuntimeException("Invalid selector, should confirm to `{id|name|class|classname|tag|tagname|xpath|text||partiallinktext|linktext|jquery|jq|$|css|cssselector|role|label|placeholder|testid|alt|title}@expr`"));
	                	}
	                	checkSemantic(cmdDetails);
	                } else {
	                	by = parts[0];
	                	by = state.unsanitize(by);
	                    classifier = by.equalsIgnoreCase("this")?"":null;
	                    classifier = by.equalsIgnoreCase("current")?"":null;
	                    classifier = by.equalsIgnoreCase("active")?"":null;
	                    subselector = (!by.equalsIgnoreCase("this") && !by.equalsIgnoreCase("current") && !by.equalsIgnoreCase("active"))?state.unsanitize(by):null;
	                    byselsame = true;
	                    nxindx = 1;
	                }
	                
                    if(parts.length>nxindx) {
                        oper = state.unsanitize(parts[nxindx].trim()) + (parts.length>nxindx+1?state.unsanitize(parts[nxindx+1].trim()):"");
                        //oper = state.unsanitize(oper);
                        String cval = oper;
                        if(oper.startsWith("<=")) {
                            cval = oper.substring(2);
                            precond = "compareTo";
                            postcond = "<=0";
                            oper = "<=";
                        } else if(oper.startsWith(">=")) {
                            cval = oper.substring(2);
                            precond = "compareTo";
                            postcond = ">=0";
                            oper = ">=";
                        } else if(oper.startsWith("=") || oper.startsWith("==")) {
                            cval = oper.substring(1);
                            precond = "compareTo";
                            postcond = "==0";
                            oper = "=";
                        } else if(oper.startsWith("<")) {
                            cval = oper.substring(1);
                            precond = "compareTo";
                            postcond = "<0";
                            oper = "<";
                        } else if(oper.startsWith(">")) {
                            cval = oper.substring(1);
                            precond = "compareTo";
                            postcond = ">0";
                            oper = ">";
                        } else if(oper.startsWith("!=")) {
                            cval = oper.substring(2);
                            precond = "compareTo";
                            postcond = "!=0";
                            oper = "!=";
                        } else if(oper.startsWith("%") && oper.endsWith("%")) {
                            cval = oper.substring(1, oper.length()-1);
                            precond = "contains";
                        } else if(oper.startsWith("%")) {
                            cval = oper.substring(1);
                            precond = "startsWith";
                        } else if(oper.endsWith("%")) {
                            cval = oper.substring(0, oper.length()-1);
                            precond = "endsWith";
                        } else {
                        	oper = null;
                        }
                        if(oper!=null && !cval.trim().isEmpty()) {
	                        ValueCommand vc = new ValueCommand(cmdDetails, state);
	                        vc.value = cval.trim();
	                        if(!cval.isEmpty() && (vc.value.charAt(0)=='"' || vc.value.charAt(0)=='\'')) {
	                            vc.value = vc.value.substring(1, vc.value.length()-1);
	                        }
	                        operval = vc.value;
                        }
                        //children.add(vc);
                    }
	            } else {
	                //excep
	            }
            }
        }
        /** M3: a semantic locator is checked when the script is compiled (unless it holds a runtime variable). */
        private void checkSemantic(Object[] cmdDetails) {
        	if(isSemanticFind() && !classifier.contains("${") && !classifier.contains("$v{")) {
        		try {
        			SemanticLocators.parse(by, classifier);
        		} catch (IllegalArgumentException e) {
        			throwError(cmdDetails, new RuntimeException(e.getMessage()));
        		}
        	}
        }
        /** M3: true when this find uses a semantic locator (loops then allow many matches under strictLocators). */
        boolean isSemanticFind() {
        	return by != null && classifier != null && SemanticLocators.isSemantic(by, classifier);
        }
        String toCmd() {
        	String logbc = esc(by) + (classifier!=null?("@'" + esc(classifier)):"");
            return " " + logbc + (StringUtils.isEmpty(subselector)?"":" \"" + subselector + "\"");
        }
        String getErr() {
        	String logbc = esc(by) + (classifier!=null?("@'" + esc(classifier) + "'"):"");
            return "\"Element not found by selector " + logbc + " at line number "+fileLineDetails[1]+" \"";
        }
        String javacodetrprovonly(String varname) {
            //if(!varonly) javacodeonlyint(children);
            String sc = state.currvarnamesc();
            String value = null, values = "new String[]{}", action = null, tvalue = null, ssubselector = subselector, sclassifier = classifier;
            /*if(children!=null && children.size()>0) {
                Command c = children.get(0);
                if(c instanceof ValueCommand) {
                   value = "evaluate(\"" + esc(((ValueCommand)c).value) + "\")";
                } else if(c instanceof ValueListCommand) {
                } else if(c instanceof ClickCommand || c instanceof HoverCommand || c instanceof HoverAndClickCommand
                        || c instanceof ClearCommand || c instanceof SubmitCommand || c instanceof TypeCommand
                        || c instanceof SelectCommand || c instanceof DoubleClickCommand || c instanceof UploadCommand) {
                    if(FindCommandImpl.class.isAssignableFrom(c.getClass()) && ((FindCommandImpl)c).cond==null) {
                        action = "\"" + c.getClass().getSimpleName().toLowerCase().replace("command", "") + "\"";
                        if(c instanceof TypeCommand) {
                        	TypeCommand typ = (TypeCommand)c;
                            if(StringUtils.isNotBlank(typ.qualifier)) {
                            	action = "\"" + typ.type  + typ.qualifier + "\"";
                            }
                            if(typ.type.equalsIgnoreCase("type") || typ.type.equalsIgnoreCase("sendkeys")) {
                            	tvalue = "evaluate(\"" + typ.value + "\")";
                            } else if(typ.type.equalsIgnoreCase("chord")) {
                            	tvalue = "Keys.chord(\"" + typ.value + "\")";
                            } else {
                            	tvalue = "evaluate(\"" + esc(typ.v1) + "\")" + "+ \" \"";
                            	if(typ.v2!=null) tvalue += " + evaluate(\"" + esc(typ.v2) + "\")" + " + \" \"";
                            	if(typ.v3!=null) tvalue += " + evaluate(\"" + esc(typ.v3) + "\")";
                            }
                        } else if(c instanceof UploadCommand) {
                            tvalue = "evaluate(\"" + ((UploadCommand)c).value + "\")";
                        } else if(c instanceof SelectCommand) {
                        	SelectCommand scc = (SelectCommand)c;
                        	ssubselector = scc.by;
                        	value = scc.value!=null?"evaluate(\"" + esc(scc.value) + "\")":null;   // first|last carry no value
                        } else if(c instanceof ClickCommand) {
                        	ClickCommand typ = (ClickCommand)c;
                            if(StringUtils.isNotBlank(typ.name)) {
                            	action = "\"click"+typ.name+"\"";
                            }
                        }
                    }
                }
            }*/
            if(ssubselector!=null) {
                ssubselector = "evaluate(\""+esc(subselector)+"\")";
            }
            if(sclassifier!=null) {
                sclassifier =  "new String[] {evaluate(\""+esc(sclassifier)+"\"), evaluate(\""+esc(classifier1)+"\")}";
            } else {
            	sclassifier =  "new String[] {\"\", \"\"}";
            }
            rtl = varname;
            String wel = by.equals("this")?state.currthisat():"___ce___";
            wel = by.equals("active")?"___cw___.switchTo().activeElement()":wel;
            String logbc = esc(by) + (classifier!=null?("@'" + esc(classifier) + "'"):"");
            if(classifierPinvXp) {
            	this.state.invXpathErrs += "addPossInvXpath(\""+(esc(classifier))+"\", \""+esc(classifierPinvXps[0])+"\", \""+classifierPinvXps[1]+"\", \""+esc(classifierPinvXps[2])+"\");";
            }
            if(classifier1PinvXp) {
            	this.state.invXpathErrs += "addPossInvXpath(\""+(esc(classifier1))+"\", \""+esc(classifierPinvXps[0])+"\", \""+classifier1PinvXps[1]+"\", \""+esc(classifierPinvXps[2])+"\");";
            }
            return varname + " = transientProviderDataWL(___cw___, "+sc+", "+wel+", "+state.timeoutNum+", \""+relative+"\", "+sclassifier+", new String[]{\""+esc(by)+"\", \""+esc(by1)+"\"}, "
                    + ssubselector + ", "+byselsame+", "+value+", "+values+", "
                    + action + ", null, "+tvalue+", \"Element not found by selector " 
                    + logbc + " at line number "+fileLineDetails[1]+" \", false, "+sleepGranularity+", false, "+state.getLayers()+");\n";
        }
        /*String javacodevaronly() {
            String sc = state.currvarnamesc();
            String value = null, values = "new String[]{}", action = null, tvalue = null, ssubselector = subselector, soper = oper, sclassifier = classifier;
            if(children!=null && children.size()>0) {
                Command c = children.get(0);
                if(c instanceof ValueCommand) {
                   value = "evaluate(\"" + esc(((ValueCommand)c).value) + "\")";
                } else if(c instanceof ValueListCommand) {
                } else if(c instanceof ClickCommand || c instanceof HoverCommand || c instanceof HoverAndClickCommand
                        || c instanceof ClearCommand || c instanceof SubmitCommand || c instanceof TypeCommand
                        || c instanceof SelectCommand || c instanceof DoubleClickCommand || c instanceof UploadCommand) {
                    if(FindCommandImpl.class.isAssignableFrom(c.getClass()) && ((FindCommandImpl)c).cond==null) {
                        action = "\"" + c.getClass().getSimpleName().toLowerCase().replace("command", "") + "\"";
                        if(c instanceof TypeCommand) {
                        	TypeCommand typ = (TypeCommand)c;
                            if(StringUtils.isNotBlank(typ.qualifier)) {
                            	action = "\"" + typ.type  + typ.qualifier + "\"";
                            }
                            if(typ.type.equalsIgnoreCase("type") || typ.type.equalsIgnoreCase("sendkeys")) {
                            	tvalue = "evaluate(\"" + typ.value + "\")";
                            } else if(typ.type.equalsIgnoreCase("chord")) {
                            	tvalue = "Keys.chord(\"" + typ.value + "\")";
                            } else {
                            	tvalue = "evaluate(\"" + esc(typ.v1) + "\")" + "+ \" \"";
                            	if(typ.v2!=null) tvalue += " + evaluate(\"" + esc(typ.v2) + "\")" + " + \" \"";
                            	if(typ.v3!=null) tvalue += " + evaluate(\"" + esc(typ.v3) + "\")";
                            }
                        } else if(c instanceof UploadCommand) {
                            tvalue = "evaluate(\"" + ((UploadCommand)c).value + "\")";
                        } else if(c instanceof SelectCommand) {
                        	SelectCommand scc = (SelectCommand)c;
                        	ssubselector = scc.by;
                        	value = scc.value!=null?"evaluate(\"" + esc(scc.value) + "\")":null;   // first|last carry no value
                        } else if(c instanceof ClickCommand) {
                        	ClickCommand typ = (ClickCommand)c;
                            if(StringUtils.isNotBlank(typ.name)) {
                            	action = "\"click"+typ.name+"\"";
                            }
                        }
                    }
                }
            }
            if(ssubselector!=null) {
                ssubselector = "evaluate(\""+esc(subselector)+"\")";
            }
            if(soper!=null) {
                soper = "\""+soper+"\"";
            }
            if(sclassifier!=null) {
            	sclassifier = "new String[] {evaluate(\""+esc(sclassifier)+"\"), evaluate(\""+esc(classifier1)+"\")}";
            }
            String var = state.varname();
            rtl = var;
            String wel = by.equals("this")?state.currthisat():"___ce___";
            return "List<String[]> " + var + " = transientProviderData(___cw___, "+sc+", "+wel+", 0L, \""+relative+"\", "+sclassifier+", new String[]{\""+by+"\", \""+by1+"\"}, "
                    + ssubselector + ", "+byselsame+", "+value+", "+values+", "
                    + action + ", "+soper+", "+tvalue+", \"Element not found by selector " 
                    + by + "@'" + esc(classifier) + "' at line number "+fileLineDetails[1]+" \", false, "+state.getLayers()+");\n";
        }*/
        String javacodeonly() {
        	String logbc = esc(by) + (classifier!=null?("@'" + esc(classifier) + "'"):"");
            return javacodeonlyNoAssert(null, false, state.timeoutNum, false)
                    + "\nGatfAssert.assertTrue(\"Element not found by selector " + logbc
                    + " at line number "+fileLineDetails[1]+" \", ___ce___!=null && !___ce___.isEmpty());";
        }
        String javacodeifonly(List<Command> children, String vrd) {
        	if(StringUtils.isNotBlank(eval)) {
        		return vrd + " = doEvalIf(\""+esc1(state.unsanitize(eval))+"\");\nGatfAssert.assertTrue(\"Evaluation condition is invalid at line number "+fileLineDetails[1]+" \", "+vrd+");";
        	} else if(StringUtils.isNotBlank(browserScope)) {
        		return vrd + " = isBrowserName(\""+esc(unSantizedUnQuoted(browserScope, state))+"\");\nGatfAssert.assertTrue(\"Evaluation condition is invalid at line number "+fileLineDetails[1]+" \", "+vrd+");";
        	} else if(StringUtils.isNotBlank(sessionScope)) {
        		return vrd + " = isSessionName(\""+esc(unSantizedUnQuoted(sessionScope, state))+"\");\nGatfAssert.assertTrue(\"Evaluation condition is invalid at line number "+fileLineDetails[1]+" \", "+vrd+");";
        	}
        	String logbc = esc(by) + (classifier!=null?("@'" + esc(classifier) + "'"):"");
            return javacodeonlyNoAssert(children, true, state.ifTimeoutNum, false)
                    + vrd + " = ___ce___!=null && !___ce___.isEmpty();\nGatfAssert.assertTrue(\"Element not found by selector " + logbc 
                    + " at line number "+fileLineDetails[1]+" \", "+vrd+");";
        }
        String javacodeonlyNoAssert(List<Command> children, boolean noexcep, int counter, boolean isVisible) {
            //javacodeonlyint(children);
            String sc = state.currvarnamesc();
            String value = (operval!=null?"evaluate(\""+esc(operval)+"\")":null), values = "new String[]{}", action = null, tvalue = null, ssubselector = subselector, soper = oper, sclassifier = classifier;
            if(children!=null && children.size()>0) {
                Command c = children.get(0);
                if(c instanceof ValueCommand) {
                   value = "evaluate(\"" + esc(((ValueCommand)c).value) + "\")";
                } else if(c instanceof ValueListCommand) {
                	values = "new String[]{";
                	for (Command vc : c.children) {
						if(vc instanceof ValueCommand) {
							ValueCommand v = (ValueCommand)vc;
							values += "evaluate(\""+esc(v.value)+"\"),";
						}
					}
                	if(values.charAt(values.length()-1)==',') {
                		values = values.substring(0, values.length()-1);
                	}
                	values += "}";
                } else if(c instanceof ClickCommand || c instanceof HoverCommand || c instanceof HoverAndClickCommand
                        || c instanceof ClearCommand || c instanceof SubmitCommand || c instanceof TypeCommand
                        || c instanceof SelectCommand || c instanceof DoubleClickCommand || c instanceof UploadCommand) {
                    if(FindCommandImpl.class.isAssignableFrom(c.getClass()) && ((FindCommandImpl)c).cond==null) {
                        action = "\"" + c.getClass().getSimpleName().toLowerCase().replace("command", "") + "\"";
                        if(c instanceof TypeCommand) {
                        	TypeCommand typ = (TypeCommand)c;
                            action = "\"" + typ.type  + typ.qualifier + "\"";
                            if(typ.type.equalsIgnoreCase("type") || typ.type.equalsIgnoreCase("sendkeys")) {
                            	tvalue = "evaluate(\"" + typ.value + "\")";
                            } else if(typ.type.equalsIgnoreCase("chord")) {
                            	tvalue = "Keys.chord(\"" + typ.value + "\")";
                            } else {
                            	tvalue = "evaluate(\"" + esc(typ.v1) + "\")" + "+ \" \"";
                            	if(typ.v2!=null) tvalue += " + evaluate(\"" + esc(typ.v2) + "\")" + " + \" \"";
                            	if(typ.v3!=null) tvalue += " + evaluate(\"" + esc(typ.v3) + "\")";
                            }
                        } else if(c instanceof UploadCommand) {
                            tvalue = "evaluate(\"" + ((UploadCommand)c).value + "\")";
                        } else if(c instanceof SelectCommand) {
                        	SelectCommand scc = (SelectCommand)c;
                        	ssubselector = scc.by;
                        	value = scc.value!=null?"evaluate(\"" + esc(scc.value) + "\")":null;   // first|last carry no value
                        } else if(c instanceof ClickCommand) {
                        	ClickCommand typ = (ClickCommand)c;
                            if(StringUtils.isNotBlank(typ.name)) {
                            	action = "\"click"+typ.name+"\"";
                            }
                        }
                    }
                } else if(c instanceof RobotCommand) {
                	return c.javacode();
                }
            }
            if(ssubselector!=null) {
                ssubselector = "evaluate(\""+esc(ssubselector)+"\")";
            }
            if(soper!=null) {
                soper = "\""+soper+"\"";
            }
            if(sclassifier!=null) {
            	sclassifier = "new String[] {evaluate(\""+esc(sclassifier)+"\"), evaluate(\""+esc(classifier1)+"\")}";
            } else {
            	sclassifier =  "new String[] {\"\", \"\"}";
            }
            String logbc = esc(by) + (classifier!=null?("@'" + esc(classifier) + "'"):"");
            String wel = by.equals("this")?state.currthisat():"___ce___";
            String excmsg = noexcep?null:"\"Element not found by selector " + logbc + " at line number "+fileLineDetails[1]+" \"";
            wel = by.equals("active")?"___cw___.switchTo().activeElement()":wel;
            if(classifierPinvXp) {
            	this.state.invXpathErrs += "addPossInvXpath(\""+(esc(classifier))+"\", \""+esc(classifierPinvXps[0])+"\", \""+classifierPinvXps[1]+"\", \""+esc(classifierPinvXps[2])+"\");";
            }
            if(classifier1PinvXp) {
            	this.state.invXpathErrs += "addPossInvXpath(\""+(esc(classifier1))+"\", \""+esc(classifierPinvXps[0])+"\", \""+classifier1PinvXps[1]+"\", \""+esc(classifierPinvXps[2])+"\");";
            }
            String b = "___ce___ = handleWaitFuncWL(___cw___, "+sc+", "+wel+", "+counter+", \""+relative+"\", "+sclassifier+", new String[]{\""+esc(by)+"\", \""+esc(by1)+"\"}, "
                    + ssubselector + ", "+byselsame+", "+value+", "+values+", "
                    + action + ", "+soper+", "+tvalue+", "+excmsg+", "+noexcep+", "+sleepGranularity+", "+isVisible+", \""+esc(fileLineDetails[2].toString())+"\", "+fileLineDetails[1]+" ,"+state.getLayers()+");\n";
            return b;
        }

        
        protected List<Object> paramsAfterPause(List<Command> children, boolean noexcep, int counter, boolean isVisible) {
            List<Object> params = new ArrayList<>();
            List<String> values = new ArrayList<>();
            String value = (operval!=null?operval:null), action = null, tvalue = null, ssubselector = subselector, soper = oper, sclassifier = classifier;
            if(children!=null && children.size()>0) {
                Command c = children.get(0);
                if(c instanceof ValueCommand) {
                   value =((ValueCommand)c).value;
                } else if(c instanceof ValueListCommand) {
                	for (Command vc : c.children) {
						if(vc instanceof ValueCommand) {
							ValueCommand v = (ValueCommand)vc;
                            values.add(v.value);
						}
					}
                } else if(c instanceof ClickCommand || c instanceof HoverCommand || c instanceof HoverAndClickCommand
                        || c instanceof ClearCommand || c instanceof SubmitCommand || c instanceof TypeCommand
                        || c instanceof SelectCommand || c instanceof DoubleClickCommand || c instanceof UploadCommand) {
                    if(FindCommandImpl.class.isAssignableFrom(c.getClass()) && ((FindCommandImpl)c).cond==null) {
                        action = c.getClass().getSimpleName().toLowerCase().replace("command", "");
                        if(c instanceof TypeCommand) {
                        	TypeCommand typ = (TypeCommand)c;
                            action = typ.type + typ.qualifier;
                            if(typ.type.equalsIgnoreCase("type") || typ.type.equalsIgnoreCase("sendkeys")) {
                            	tvalue = typ.value;
                            } else if(typ.type.equalsIgnoreCase("chord")) {
                            	tvalue = Keys.chord(typ.value);
                            } else {
                            	tvalue = typ.v1 + " ";
                            	if(typ.v2!=null) tvalue += typ.v2 + " ";
                            	if(typ.v3!=null) tvalue += typ.v3 + " ";
                            }
                        } else if(c instanceof UploadCommand) {
                            tvalue = ((UploadCommand)c).value;
                        } else if(c instanceof SelectCommand) {
                        	SelectCommand scc = (SelectCommand)c;
                        	ssubselector = scc.by;
                        	value = scc.value;
                        } else if(c instanceof ClickCommand) {
                        	ClickCommand typ = (ClickCommand)c;
                            if(StringUtils.isNotBlank(typ.name)) {
                            	action = "click"+typ.name;
                            }
                        }
                    }
                } else if(c instanceof RobotCommand) {
                	//return c.javacode();
                }
            }
            String[] classfs = new String[] {"", ""};
            if(sclassifier!=null) {
                classfs = new String[] {sclassifier, classifier1};
            }
            String logbc = by + (classifier!=null?("@'" + classifier + "'"):"");
            String excmsg = noexcep?null:"Element not found by selector " + logbc + " at line number "+fileLineDetails[1]+" ";
            params.add(relative);params.add(classfs);params.add(new String[]{by, by1});
            params.add(ssubselector);params.add(byselsame);params.add(value);params.add(values);params.add(action);
            params.add(soper);params.add(tvalue);params.add(excmsg);
            return params;
        }
        /*String javacodeonly(List<Command> children, long waitTime) {
            javacodeonlyint(children);
            String sc = state.currvarnamesc();
            String value = null, values = "new String[]{}", action = null, tvalue = null, ssubselector = subselector, soper = oper, sclassifier = classifier;
            if(children!=null && children.size()>0) {
                Command c = children.get(0);
                if(c instanceof ValueCommand) {
                   value = "evaluate(\"" + esc(((ValueCommand)c).value) + "\")";
                } else if(c instanceof ValueListCommand) {
                } else if(c instanceof ClickCommand || c instanceof HoverCommand || c instanceof HoverAndClickCommand
                        || c instanceof ClearCommand || c instanceof SubmitCommand || c instanceof TypeCommand
                        || c instanceof SelectCommand || c instanceof DoubleClickCommand || c instanceof UploadCommand) {
                    if(FindCommandImpl.class.isAssignableFrom(c.getClass()) && ((FindCommandImpl)c).cond==null) {
                        action = "\"" + c.getClass().getSimpleName().toLowerCase().replace("command", "") + "\"";
                        if(c instanceof TypeCommand) {
                        	TypeCommand typ = (TypeCommand)c;
                            if(StringUtils.isNotBlank(typ.qualifier)) {
                            	action = "\"" + typ.type  + typ.qualifier + "\"";
                            }
                            if(typ.type.equalsIgnoreCase("type") || typ.type.equalsIgnoreCase("sendkeys")) {
                            	tvalue = "evaluate(\"" + typ.value + "\")";
                            } else if(typ.type.equalsIgnoreCase("chord")) {
                            	tvalue = "Keys.chord(\"" + typ.value + "\")";
                            } else {
                            	tvalue = "evaluate(\"" + esc(typ.v1) + "\")" + "+ \" \"";
                            	if(typ.v2!=null) tvalue += " + evaluate(\"" + esc(typ.v2) + "\")" + " + \" \"";
                            	if(typ.v3!=null) tvalue += " + evaluate(\"" + esc(typ.v3) + "\")";
                            }
                        } else if(c instanceof UploadCommand) {
                            tvalue = "evaluate(\"" + ((UploadCommand)c).value + "\")";
                        } else if(c instanceof SelectCommand) {
                        	SelectCommand scc = (SelectCommand)c;
                        	ssubselector = scc.by;
                        	value = scc.value!=null?"evaluate(\"" + esc(scc.value) + "\")":null;   // first|last carry no value
                        } else if(c instanceof ClickCommand) {
                        	ClickCommand typ = (ClickCommand)c;
                            if(StringUtils.isNotBlank(typ.name)) {
                            	action = "\"click"+typ.name+"\"";
                            }
                        }
                    }
                }
            }
            if(ssubselector!=null) {
                ssubselector = "evaluate(\""+esc(ssubselector)+"\")";
            }
            if(soper!=null) {
                soper = "\""+soper+"\"";
            }
            if(sclassifier!=null) {
            	sclassifier = "new String[] {evaluate(\""+esc(sclassifier)+"\"), evaluate(\""+esc(classifier1)+"\")}";
            }
            String wel = by.equals("this")?state.currthisat():"___ce___";
            String b = "___ce___ = handleWaitFunc(___cw___, "+sc+", "+wel+", (long)"+waitTime+", \""+relative+"\", "+sclassifier+", new String[]{\""+by+"\", \""+by1+"\"}, "
                    + ssubselector + ", "+byselsame+", "+value+", "+values+", "
                    + action + ", "+soper+", "+tvalue+", \"Element not found by selector " 
                    + by + "@'" + esc(classifier) + "' at line number "+fileLineDetails[1]+" \", false, "+state.getLayers()+");\n"
                    + "\nGatfAssert.assertTrue(\"Element not found by selector " + by + "@'" + esc(classifier) 
                    + "' at line number "+fileLineDetails[1]+" \", ___ce___!=null && !___ce___.isEmpty());";
            if(children!=null && children.size()>0) {
                Command c = children.get(0);
                if(c instanceof ActionsCommand || c instanceof RobotCommand) {
                    if(c instanceof ActionsCommand) {
                        b += ((ActionsCommand)c).javacode();
                    }
                }
            }
            return b;
        }
        String javacodeonlyint(List<Command> children) {
            try {
                StringBuilder b = new StringBuilder();
                topele = state.varname();
                String wel = by.equals("this")?state.currthisat():"___ce___";
                if(by.equalsIgnoreCase("id")) {
                    b.append("List<WebElement>  " + topele + " = By.id(evaluate(\""+esc(classifier)+"\")).findElements("+state.currvarnamesc()+");");
                } else if(by.equalsIgnoreCase("this")) {
                    b.append("List<WebElement>  " + topele + " = "+wel+";");
                } else if(by.equalsIgnoreCase("name")) {
                    b.append("List<WebElement>  " + topele + " = By.name(evaluate(\""+esc(classifier)+"\")).findElements("+state.currvarnamesc()+");");
                } else if(by.equalsIgnoreCase("class") || by.equalsIgnoreCase("className")) {
                    b.append("List<WebElement>  " + topele + " = By.className(evaluate(\""+esc(classifier)+"\")).findElements("+state.currvarnamesc()+");");
                } else if(by.equalsIgnoreCase("tag") || by.equalsIgnoreCase("tagname")) {
                    b.append("List<WebElement>  " + topele + " = By.tagName(evaluate(\""+esc(classifier)+"\")).findElements("+state.currvarnamesc()+");");
                } else if(by.equalsIgnoreCase("xpath")) {
                    String ec = esc(classifier);
                    String sc = state.currvarnamesc();
                    if(ec.startsWith(".")) {
                        if(state.loopCounter==0) {
                            throwParseError(null, new RuntimeException("Relative xpath allowed only within a loop block"));
                        }
                        sc = state.currvarnameparc();
                    }
                    b.append("List<WebElement>  " + topele + " = By.xpath(evaluate(\""+ec+"\")).findElements("+sc+");");
                } else if(by.equalsIgnoreCase("cssselector") || by.equalsIgnoreCase("css")) {
                    b.append("List<WebElement>  " + topele + " = By.cssSelector(evaluate(\""+esc(classifier)+"\")).findElements("+state.currvarnamesc()+");");
                } else if(by.equalsIgnoreCase("text")) {
                    b.append("List<WebElement>  " + topele + " = By.xpath(\"//*[contains(text(), '"+esc(classifier)+"')]\").findElements("+state.currvarnamesc()+");");
                } else if(by.equalsIgnoreCase("linkText")) {
                    b.append("List<WebElement>  " + topele + " = By.linkText(evaluate(\""+esc(classifier)+"\")).findElements("+state.currvarnamesc()+");");
                } else if(by.equalsIgnoreCase("partialLinkText")) {
                    b.append("List<WebElement>  " + topele + " = By.partialLinkText(evaluate(\""+esc(classifier)+"\")).findElements("+state.currvarnamesc()+");");
                } else if(by.equalsIgnoreCase("active")) {
                    b.append("\n@SuppressWarnings(\"serial\")");
                    b.append("List<WebElement>  " + topele + " = new ArrayList<WebElement>(){{add(___cw___.activeElement());}};");
                } else if(by.equalsIgnoreCase("jq") || by.equalsIgnoreCase("jquery") || by.equalsIgnoreCase("$")) {
                	b.append("List<WebElement>  " + topele + " = ByJquerySelector(___cw___, evaluate(\""+esc(classifier)+"\"));");
        		} else if(!byselsame) {
                    throwParseError(null);
                } else {
                    condvar = state.condvarname();
                    b.append("\nboolean " + condvar + " = true;");
                    b.append("\n" + condvar + " = evaluate(\"#set($__v__R1_O_P2__ = "+esc(by)+")\\n${__v__R1_O_P2__}\").equalsIgnoreCase(\"true\");");
                    return b.toString();
                }

                b.append("\nGatfAssert.assertTrue(\"Element not found by selector " + by + "@'" + esc(classifier) 
                    + "' at line number "+fileLineDetails[1]+" \", "+topele+"!=null && !"+topele+".isEmpty());");
                b.append("___ce___ = "+topele+";");

                if(this.children!=null && this.children.size()>0) {
                    children = this.children;
                }
                if(children!=null && children.size()>0 && subselector!=null && !subselector.isEmpty()) {
                    Command c = children.get(0);
                    if(c instanceof ValueCommand) {
                        String value = ((ValueCommand)c).value;
                        String cvarname = state.currvarname();
                        cfiltvar = cvarname;
                        condvar = state.condvarname();
                        b.append("\nboolean " + condvar + " = true;");
                        if(byselsame)
                        {
                            if(subselector.equalsIgnoreCase("title")) {
                                b.append("\n" + condvar + " &= evaluate(\""+esc(value)+"\")."+precond+"(___cw___.getTitle())"+postcond+";");
                                return b.toString();
                            } else if(subselector.equalsIgnoreCase("currentUrl")) {
                                b.append("\n" + condvar + " &= evaluate(\""+esc(value)+"\")."+precond+"(___cw___.getCurrentUrl())"+postcond+";");
                                return b.toString();
                            } else if(subselector.equalsIgnoreCase("pageSource")) {
                                b.append("\n" + condvar + " &= evaluate(\""+esc(value)+"\")."+precond+"(___cw___.getPageSource())"+postcond+";");
                                return b.toString();
                            } else if(subselector.equalsIgnoreCase("width")) {
                                b.append("\n" + condvar + " &= evaluate(\""+esc(value)+"\")."+precond+"(String.valueOf(___cw___.manage().window().getSize().getWidth()))"+postcond+";");
                                return b.toString();
                            } else if(subselector.equalsIgnoreCase("height")) {
                                b.append("\n" + condvar + " &= evaluate(\""+esc(value)+"\")."+precond+"(String.valueOf(___cw___.manage().window().getSize().getHeight()))"+postcond+";");
                                return b.toString();
                            } else if(subselector.equalsIgnoreCase("xpos")) {
                                b.append("\n" + condvar + " &= evaluate(\""+esc(value)+"\")."+precond+"(String.valueOf(___cw___.manage().window().getPosition().getX()))"+postcond+";");
                                return b.toString();
                            } else if(subselector.equalsIgnoreCase("ypos")) {
                                b.append("\n" + condvar + " &= evaluate(\""+esc(value)+"\")."+precond+"(String.valueOf(___cw___.manage().window().getPosition().getY()))"+postcond+";");
                                return b.toString();
                            } else if(subselector.equalsIgnoreCase("alerttext")) {
                                b.append("\n" + condvar + " &= evaluate(\""+esc(value)+"\")."+precond+"(___cw___.switchTo().alert().getText())"+postcond+";");
                                return b.toString();
                            } else {
                                throwParseError(null);
                            }
                        }

                        b.append("\nfor(final WebElement " + state.varname() + " : " + cvarname + ")\n{");
                        if(subselector.equalsIgnoreCase("text")) {
                            b.append("\n" + condvar + " &= evaluate(\""+esc(value)+"\")."+precond+"(" + state.currvarname() + ".getText())"+postcond+";");
                        } else if(subselector.equalsIgnoreCase("tagname")) {
                            b.append("\n" + condvar + " &= evaluate(\""+esc(value)+"\")."+precond+"(" + state.currvarname() + ".getTagName())"+postcond+";");
                        } else if(subselector.toLowerCase().startsWith("attr@")) {
                            String atname = subselector.substring(5);
                            if(atname.charAt(0)=='"' || atname.charAt(0)=='\'') {
                                atname = atname.substring(1, atname.length()-1);
                            }
                            b.append("\n" + condvar + " &= evaluate(\""+esc(value)+"\")."+precond+"(" + state.currvarname() + ".getAttribute(\""+esc(atname)+"\"))"+postcond+";");
                        } else if(subselector.toLowerCase().startsWith("cssvalue@")) {
                            String atname = subselector.substring(9);
                            if(atname.charAt(0)=='"' || atname.charAt(0)=='\'') {
                                atname = atname.substring(1, atname.length()-1);
                            }
                            b.append("\n" + condvar + " &= evaluate(\""+esc(value)+"\")."+precond+"(" + state.currvarname() + ".getCssValue(\""+esc(atname)+"\"))"+postcond+";");
                        } else if(subselector.equalsIgnoreCase("width")) {
                            b.append("\n" + condvar + " &= evaluate(\""+esc(value)+"\")."+precond+"(String.valueOf("+state.currvarname()+".getSize().getWidth()))"+postcond+";");
                        } else if(subselector.equalsIgnoreCase("height")) {
                            b.append("\n" + condvar + " &= evaluate(\""+esc(value)+"\")."+precond+"(String.valueOf("+state.currvarname()+".getSize().getHeight()))"+postcond+";");
                        } else if(subselector.equalsIgnoreCase("xpos")) {
                            b.append("\n" + condvar + " &= evaluate(\""+esc(value)+"\")."+precond+"(String.valueOf("+state.currvarname()+".getPosition().getX()))"+postcond+";");
                        } else if(subselector.equalsIgnoreCase("ypos")) {
                            b.append("\n" + condvar + " &= evaluate(\""+esc(value)+"\")."+precond+"(String.valueOf("+state.currvarname()+".getPosition().getY()))"+postcond+";");
                        }
                        b.append("\n}");
                    } else if(c instanceof ValueListCommand) {
                        List<String> values = ((ValueListCommand)c).getValues();
                        String cvarname = state.currvarname();
                        cfiltvar = cvarname;
                        condvar = state.condvarname();
                        b.append("\nboolean " + condvar + " = "+cvarname+"!=null && "+cvarname+".size()>0 && "+String.valueOf(values!=null && values.size()>0)+";");
                        b.append("\nif(!("+condvar+" && "+values.size()+"=="+cvarname+".size())) "+condvar+"=false;\nelse\n{");
                        for (int i=0;i<values.size();i++) {
                            String value = values.get(i);
                            b.append("\nfinal WebElement " + state.varname() + " = "+cvarname+".get("+i+");");
                            if(subselector.equalsIgnoreCase("text")) {
                                b.append("\n" + condvar + " &= evaluate(\""+esc(value)+"\").equals(" + state.currvarname() + ".getText());");
                            } else if(subselector.equalsIgnoreCase("tagname")) {
                                b.append("\n" + condvar + " &= evaluate(\""+esc(value)+"\").equalsIgnoreCase(" + state.currvarname() + ".getTagName());");
                            } else if(subselector.toLowerCase().startsWith("attr@")) {
                                String atname = subselector.substring(5);
                                if(atname.charAt(0)=='"' || atname.charAt(0)=='\'') {
                                    atname = atname.substring(1, atname.length()-1);
                                }
                                b.append("\n" + condvar + " &= evaluate(\""+esc(value)+"\").equals(" + state.currvarname() + ".getAttribute(\""+esc(atname)+"\"));");
                            } else if(subselector.toLowerCase().startsWith("cssvalue@")) {
                                String atname = subselector.substring(9);
                                if(atname.charAt(0)=='"' || atname.charAt(0)=='\'') {
                                    atname = atname.substring(1, atname.length()-1);
                                }
                                b.append("\n" + condvar + " &= evaluate(\""+esc(value)+"\").equals(" + state.currvarname() + ".getCssValue(\""+esc(atname)+"\"));");
                            } else if(subselector.equalsIgnoreCase("width")) {
                                b.append("\n" + condvar + " &= evaluate(\""+esc(value)+"\").equals(String.valueOf("+state.currvarname()+".getSize().getWidth()));");
                            } else if(subselector.equalsIgnoreCase("height")) {
                                b.append("\n" + condvar + " &= evaluate(\""+esc(value)+"\").equals(String.valueOf("+state.currvarname()+".getSize().getHeight()));");
                            } else if(subselector.equalsIgnoreCase("xpos")) {
                                b.append("\n" + condvar + " &= evaluate(\""+esc(value)+"\").equals(String.valueOf("+state.currvarname()+".getPosition().getX()));");
                            } else if(subselector.equalsIgnoreCase("ypos")) {
                                b.append("\n" + condvar + " &= evaluate(\""+esc(value)+"\").equals(String.valueOf("+state.currvarname()+".getPosition().getY()));");
                            } else {
                                throwParseError(null);
                            }
                        }
                        b.append("\n}");
                    }
                } else if(children!=null && children.size()>0) {
                    Command c = children.get(0);
                    if(c instanceof ClickCommand || c instanceof HoverCommand || c instanceof HoverAndClickCommand
                            || c instanceof ClearCommand || c instanceof SubmitCommand || c instanceof TypeCommand
                            || c instanceof SelectCommand || c instanceof DoubleClickCommand || c instanceof UploadCommand) {
                        if(FindCommandImpl.class.isAssignableFrom(c.getClass()) && ((FindCommandImpl)c).cond==null) {
                            b.append(c.selcode(topele));
                        }
                    } else if(c instanceof ActionsCommand || c instanceof RobotCommand) {
                        if(FindCommandImpl.class.isAssignableFrom(c.getClass())) {
                            b.append(c.javacode());
                        }
                    }
                } else if("selected".equalsIgnoreCase(subselector) || "enabled".equalsIgnoreCase(subselector) || "visible".equalsIgnoreCase(subselector)) {
                    String cvarname = topele;
                    cfiltvar = cvarname;
                    condvar = state.condvarname();
                    b.append("\nboolean " + condvar + " = true;");
                    b.append("\nfor(final WebElement " + state.varname() + " : " + cvarname + ")\n{");
                    if(subselector.equalsIgnoreCase("selected")) {
                        b.append("\n" + condvar + " &= " + state.currvarname() + ".isSelected();");
                    } else if(subselector.equalsIgnoreCase("enabled")) {
                        b.append("\n" + condvar + " &= " + state.currvarname() + ".isEnabled();");
                    } else if(subselector.equalsIgnoreCase("visible")) {
                        b.append("\n" + condvar + " &= " + state.currvarname() + ".isDisplayed();");
                    } else {
                        throwParseError(null);
                    }
                    b.append("\n}");
                } else if(subselector!=null) {
                    List<String> ssl = Arrays.asList(subselector.split("[\t ]*,[\t ]*"));
                    rtl = state.varname();
                    b.append("\nList<String[]> "+rtl+" = new java.util.ArrayList<String[]>();");
                    b.append("\nfor(final WebElement " + state.varname() + " : " + topele + ")\n{");
                    b.append("\nString[] __t = new String["+ssl.size()+"];");
                    for (int i=0;i<ssl.size();i++)
                    {
                        if(ssl.get(i).equalsIgnoreCase("text")) {
                            b.append("\n__t["+i+"] = " + state.currvarname() + ".getText();");
                        } else if(ssl.get(i).equalsIgnoreCase("tagname")) {
                            b.append("\n__t["+i+"] = " + state.currvarname() + ".getTagName();");
                        } else if(ssl.get(i).toLowerCase().startsWith("attr@")) {
                            String atname = ssl.get(i).substring(5);
                            if(atname.charAt(0)=='"' || atname.charAt(0)=='\'') {
                                atname = atname.substring(1, atname.length()-1);
                            }
                            b.append("\n__t["+i+"] = " + state.currvarname() + ".getAttribute(\""+esc(atname)+"\");");
                        } else if(ssl.get(i).toLowerCase().startsWith("cssvalue@")) {
                            String atname = ssl.get(i).substring(9);
                            if(atname.charAt(0)=='"' || atname.charAt(0)=='\'') {
                                atname = atname.substring(1, atname.length()-1);
                            }
                            b.append("\n__t["+i+"] = " + state.currvarname() + ".getCssValue(\""+esc(atname)+"\");");
                        } else if(ssl.get(i).equalsIgnoreCase("width")) {
                            b.append("\n__t["+i+"] = String.valueOf("+state.currvarname()+".getSize().getWidth());");
                        } else if(ssl.get(i).equalsIgnoreCase("height")) {
                            b.append("\n__t["+i+"] = String.valueOf("+state.currvarname()+".getSize().getHeight());");
                        } else if(ssl.get(i).equalsIgnoreCase("xpos")) {
                            b.append("\n__t["+i+"] = String.valueOf("+state.currvarname()+".getPosition().getX());");
                        } else if(ssl.get(i).equalsIgnoreCase("ypos")) {
                            b.append("\n__t["+i+"] = String.valueOf("+state.currvarname()+".getPosition().getY());");
                        } else {
                            throwParseError(null);
                        }
                    }
                    b.append("\n" + rtl + ".add(__t);");
                    b.append("\n}");
                }

                return b.toString();
            } catch (GatfSelCodeParseError e) {
                throw e;
            } catch (Throwable e) {
                throwParseError(null, e);
            }
            return null;
        }*/
        String getActionable(String action, String post, String actionsVar) {
            /*StringBuilder b = new StringBuilder();
            if(cfiltvar!=null) {
                b.append("\nfor(final WebElement " + state.varname() + " : " + cfiltvar + ")\n{");
                b.append("\n" + state.currvarname() + "." + action + "(" + (post!=null?post:"") + ");");
                b.append("\n}");
            } else if(actionsVar!=null) {
                b.append("\nfor(final WebElement " + state.varname() + " : " + topele + ")\n{");
                b.append("\n"+actionsVar+".moveToElement("+state.currvarname()+")."+action+"(" + (post!=null?post:"") + ").perform();");
                b.append("\n}");
            } else {
                b.append("\n" + state.currvarname() + ".get(0)." + action + "(" + (post!=null?post:"") + ");");
            }
            return b.toString();*/
        	if(action.equalsIgnoreCase("upload")) {
        		return "\nuploadFile(___ocw___, ___ce___, "+post+", 1);\n";
        	}
            if(actionsVar!=null) {
                return "\n"+actionsVar+".moveToElement(___ce___.get(0))."+action+"(" + (post!=null?post:"") + ").perform();";
            } else {
                return "\n___ce___.get(0)." + action + "(" + (post!=null?post:"") + ");";
            }
        }
        String getActionableVar() {
            return "___ce___";
        }
        String condition() {
            return condvar;
        }
        String javacode() {
            return "";
        }
    }
