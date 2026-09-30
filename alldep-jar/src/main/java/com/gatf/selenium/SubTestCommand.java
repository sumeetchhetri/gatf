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

/** G8 — promoted verbatim from the nested Command.SubTestCommand (inherits the same members/nested types). */
@SuppressWarnings("unused")
public class SubTestCommand extends Command {
        String sessionName;
        Integer sessionId = null;
        Map<String, String> tags = new HashMap<String, String>();
        Set<String> params = new HashSet<String>();
        Long calls = 0L;
        boolean isAFunc = false;
        boolean hasName = true;
        /** M6: beforeAll | beforeEach | afterEach | afterAll for a hook block (run like a func), else null. */
        String hook;
        /** M6: only | skip | fixme marker on a subtest, else null. */
        String marker;
        /** M6: defined in an imported file (its {@code only} does not narrow the importing script). */
        boolean imported;
        String fName = "__st__" + state.NUMBER_ST++;
        SubTestCommand(String val, Object[] cmdDetails, CommandState state, boolean isAFunc) {
            this(val, cmdDetails, state, isAFunc, null, null);
        }
        /** M6: {@code hook} names a hook block; {@code marker} is a subtest's only/skip/fixme prefix. */
        SubTestCommand(String val, Object[] cmdDetails, CommandState state, boolean isAFunc, String hook, String marker) {
            super(cmdDetails, state);
            this.isAFunc = isAFunc;
            this.hook = hook;
            this.marker = marker;
            String[] parts = val.trim().split("[\t ]+");
            if(parts.length>=1) {
                name = unSantizedUnQuoted(parts[0].trim(), state);
                if(name.length()>0) {
                	if(name.contains("[")) {
                		Matcher m = tagRgx.matcher(name);
                		while(m.find()) {
                			tags.putIfAbsent(m.group(1).trim().toLowerCase(), m.group(1).trim());
                		}
                		m = tagRgx.matcher(name);
                		name = m.replaceAll("").trim();
                	}
                } else {
                	hasName = false;
                    name = "Subtest " + (state.NUMBER_ST - 1);
                }
                if(parts.length>1) {
                	String pstr = "";
                	parts[1] = parts[1].trim();
                	if(parts[1].startsWith("(")) {
                		if(!parts[1].equals("(")) {
                			pstr += parts[1].substring(1);
                		}
                		boolean pvalid = false;
                		if(parts[1].endsWith(")")) {
                			pstr = pstr.substring(0, pstr.length()-1) + " ";
                			pvalid = true;
        				} else {
        					pstr += " ";
        				}
                		int counter = 2;
                		if(!pvalid) {
	                		for (;counter < parts.length;counter++) {
	                			if(parts[counter].endsWith(")")) {
	                				if(!parts[counter].equals(")")) {
	                        			pstr += parts[counter].substring(0, parts[counter].length()-1) + " ";
	                				}
	                				pvalid = true;
	                				break;
	                			} else {
	                				pstr += parts[counter] + " ";
	                			}
	                		}
                		}
                		if(!pvalid) {
                			throwError(fileLineDetails, new RuntimeException("Invalid subtest parameter(s), please use format `(arg1 arg2 arg3 arg4....)`"));
                		} else {
                			String[] nparts = pstr.trim().split("[\t ]+");
                			for (int i = 0; i < nparts.length; i++) {
        						String param = unSantizedUnQuoted(nparts[i].trim(), state);
    							if(!param.trim().matches("[a-zA-Z]+[a-zA-Z0-9_]*")) {
    								throwError(fileLineDetails, new RuntimeException("Invalid subtest parameter name `"+param+"`"));
    							} else {
    								if(params.contains(param.trim())) {
    									throwError(fileLineDetails, new RuntimeException("Duplicate parameter name `"+param.trim()+"`"));
    								}
    								params.add(param.trim());
    							}
        					}
                			
                			counter++;
                    		if(counter<parts.length && !isAFunc) {
                    			if(parts[counter].trim().matches("@[0-9]+")) {
                                    sessionId = Integer.parseInt(parts[counter].trim().substring(1)) - 1;
                                    if(sessionId<0) {
                                    	throwError(fileLineDetails, new RuntimeException("Session id should be greater than 0"));
                                    }
                                } else {
                                    sessionName = unSantizedUnQuoted(parts[counter].trim(), state);
                                }
                    		}
                		}
                	} else if(parts.length==2) {
                		if(StringUtils.isNotBlank(parts[1].trim())) {
	                		if(parts[1].trim().matches("@[0-9]+")) {
	    	                    sessionId = Integer.parseInt(parts[1].trim().substring(1)) - 1;
	    	                    if(sessionId<0) {
	    	                    	throwError(fileLineDetails, new RuntimeException("Session id should be greater than 0"));
	    	                    }
	    	                } else {
	    	                    sessionName = unSantizedUnQuoted(parts[1].trim(), state);
	    	                }
                		}
                	} else {
                		throwError(fileLineDetails, new RuntimeException("Subtest/Function definition arguments should follow within `(` and `)`"));
                	}
                }
            } else {
            	hasName = false;
                name = "Subtest " + (state.NUMBER_ST - 1);
            }
            state.addSubtest(this);
        }
        /** M6: the statement that runs this hook block from generated code ({@code sc} = the caller's search context). */
        String hookCall(String sc) {
            return "___ce___ = ___invoke_sub_test_dyn___(getClass(), \"" + fName + "\", \"" + hook + "\", ___cw___, ___ocw___, " + sc + ", ___lp___);\n";
        }
        String toCmd() {
            StringBuilder b = new StringBuilder();
            b.append(hook!=null?hook:((marker!=null?marker+" ":"")+(isAFunc?"func":"subtest")));
            if(!children.isEmpty())
            {
                b.append("\n{\n");
                for (Command c : children) {
                    b.append(c.toCmd());
                    b.append("\n");
                }
                b.append("}");
            }
            return b.toString();
        }
        String javacode() {
            return javacodeint();
        }
        /*String javacodesubtest(boolean initvars) {
            StringBuilder b = new StringBuilder();
            if(!isAFunc) {
	            if(state.modeExecType==2) {
	                if(sessionName!=null) {
	                    b.append("setSession(\""+esc(sessionName)+"\", -1, true);\n"); 
	                } else if(sessionId>0) {
	                    b.append("setSession(null, "+(sessionId-1)+", true);\n"); 
	                }
	            } else {
	                b.append("if(matchesSessionId("+(sessionName!=null?"\""+esc(sessionName)+"\"":"null")+", "
	                        +((sessionId!=null&&sessionId>0)?(sessionId+""):"-1") + ")) {");
	            }
	            if(initvars) {
	                b.append("___sc___1 = get___d___();\n");
	                b.append("___cw___ = get___d___();\n");
	                b.append("___ocw___ = ___cw___;\n");
	            }
            }
            for (String pn : params.keySet()) {
				b.append("___add_var__(\""+pn+"\", \""+esc(params.get(pn))+"\");\n");
			}
            b.append(genDebugInfo(this));
            b.append("___ce___ = " + fName+"(___cw___, ___ocw___, "+state.currvarnamesc()+", ___lp___);\n");
            if(!isAFunc && state.modeExecType!=2) {
                b.append("}\n");
            }
            return b.toString();
        }*/
        String javacodeint() {
        	if(!state.isSTGenPhase) return "";
            StringBuilder b = new StringBuilder();
            b.append("List<WebElement> " + fName + "(String __sfname__, WebDriver ___cw___, WebDriver ___ocw___, SearchContext "+state.currvarnamesc()+", LoggingPreferences ___lp___) throws Throwable {\n");
            b.append("List<WebElement> ___ce___ = null;\n");
            // E11: an empty subtest still gets the file's each-hooks (so it is reported like the others)
            if(!children.isEmpty() || (!isAFunc && hook==null && (state.hook("beforeEach")!=null || state.hook("afterEach")!=null)))
            {
            	isSubtestScope = true;
            	b.append(genDebugInfo(this));
            	String pstn = state.varnamerandom();
                b.append("String "+pstn+" = get__"+(!isAFunc?"subtest":"func")+"name__();\n");
            	b.append("set__"+(!isAFunc?"subtest":"func")+"name__(\""+name+"\", __sfname__);\n");
            	// M6: beforeEach / afterEach wrap every subtest (not funcs or hooks); only emitted when the file has them
            	SubTestCommand beh = isAFunc ? null : state.hook("beforeEach"), aeh = isAFunc ? null : state.hook("afterEach");
            	String sc = state.currvarnamesc(), aef = null;
            	if(aeh!=null) {
            		aef = state.varnamerandom();
            		b.append("boolean "+aef+" = false;\n");
            	}
                b.append("\ntry {\n");
                if(beh!=null) {
                	b.append(beh.hookCall(sc));
                }
                for (Command c : children) {
                	b.append(genDebugInfo(c));
                    try {
                    	b.append(c.javacode());
					} catch (Exception e) {
						e.printStackTrace();
						throwParseError(c.fileLineDetails);
					}
                    b.append("\n");
                }
                if(aeh!=null) {
                	b.append(aef+" = true;\n");
                	b.append(aeh.hookCall(sc));
                }
                if(!isAFunc) {
                	b.append("\npushResult(new SeleniumTestResult(get___d___(), this, ___lp___));");
                }
                String ex = state.evarname();
                b.append("\n}\ncatch(Throwable "+ex+")\n{");
                String res = null;
                if(aeh!=null) {
                	// the subtest (or beforeEach) failed: its result (line, screenshot) is taken first, then afterEach
                	// still runs; afterEach's own failure is logged and the first one reported
                	res = state.varnamerandom();
                	String img = "getOutDir() + java.io.File.separator + \""+UUID.randomUUID().toString()+".png\"";
                	b.append("SeleniumTestResult "+res+" = new SeleniumTestResult(get___d___(), this, "+ex+", "+img+", ___lp___, \""+name+"\");\n");
                	b.append("if(!"+aef+") {\n"+aef+" = true;\ntry {\n"+aeh.hookCall(sc)+"} catch(Throwable ___hk___) {\n__hook_failed__(\"afterEach\", ___hk___);\n}\n__hook_soft__(\"afterEach\");\n}\n");
                }
                if(res!=null) {
                	b.append("pushResult("+res+");");
                	b.append("if(!("+ex+" instanceof WarnSubTestException) && !("+ex+" instanceof PassSubTestException)) throw "+ex+";\n");
                } else if(!isAFunc) {
                	//String oex = state.evarname();
	                /*b.append("\ntry{");
	                b.append(ex+".printStackTrace();\n");*/
	                String img = "getOutDir() + java.io.File.separator + \""+UUID.randomUUID().toString()+".png\"";
	                /*b.append("java.lang.System.out.println("+img+");");
	                ScreenshotCommand tm = new ScreenshotCommand(img, new Object[] {}, state, true);
	                b.append(tm.javacode());
	                b.append("}catch(java.io.IOException _ioe){}");*/
	                //b.append("Throwable "+oex+" = "+ex+";if(!("+ex+" instanceof ValidSubTestException)) "+ex+" = new SubTestException(\""+name+"\", "+ex+");\n");
                	b.append("pushResult(new SeleniumTestResult(get___d___(), this, "+ex+", "+img+", ___lp___, \""+name+"\"));");
                	b.append("if(!("+ex+" instanceof WarnSubTestException) && !("+ex+" instanceof PassSubTestException)) throw "+ex+";\n");
                	//b.append("else if(!("+oex+" instanceof ValidSubTestException)) throw (SubTestException)"+ex+";\n");
                } else {
                	b.append("throw "+ex+";\n");
                }
                b.append("\n}\nfinally {\n");
                //b.append("set__"+(!isAFunc?"subtest":"func")+"name__(null);\n");
                b.append("reset__"+(!isAFunc?"subtest":"func")+"name__("+pstn+");\n");
                b.append("\n}\n");
                isSubtestScope = false;
            }
            b.append("/*GATF_ST_LINE_END@"+esc(fileLineDetails[2].toString().trim())+"*/return ___ce___;\n}\n");
            return b.toString();
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
				"Subtest definition",
				"\tsubtest \"name\" session-name|@session-id (args)\n\t{\n\t\tcode\n\t}",
				"where",
				"\tsession-name - the browser session name for which to run this sub test",
				"\tsession-id - the browser session id prefixed with @ for which to run this sub test",
				"\targs - the arguments passed to the subtest, space separated names for args",
				"Examples :-",
				"\tsubtest \"sb1\" \"bs1\"\n\t{\n\t\tselect index@4 id@\"Location\"\n\t}",
				"\tsubtest \"sb1\" @1 (arg1 arg2)\n\t{\n\t\tselect index@$v{arg1} id@\"$v{arg2}\"\n\t}\n\n",
				"Function definition",
				"\tfunc \"name\" (args)\n\t{\n\t\tcode\n\t}",
				"where",
				"\targs - the arguments passed to the func",
				"Examples :-",
				"\tfunc \"cmd1\"\n\t{\n\t\tselect index@4 id@\"Location\"\n\t}",
				"\tfunc \"cmd2\" @1 (arg1 arg2)\n\t{\n\t\tselect index@$v{arg1} id@\"$v{arg2}\"\n\t}\n\n",
				"Hooks",
				"\tbeforeAll|beforeEach|afterEach|afterAll\n\t{\n\t\tcode\n\t}",
				"where",
				"\tbeforeAll - runs once before the script's first statement, afterAll once at its end (also when it failed)",
				"\tbeforeEach / afterEach - run around every subtest; afterEach also runs when the subtest failed",
				"Examples :-",
				"\tbeforeEach\n\t{\n\t\tgoto ${baseUrl}/login\n\t}",
				"\tafterAll\n\t{\n\t\tstate save .auth/after.json\n\t}\n\n",
				"Subtest markers",
				"\tonly|skip|fixme subtest \"name\" ...",
				"where",
				"\tskip / fixme - the subtest is not run and is reported as skipped",
				"\tonly - when any subtest in the file is marked only, the others are reported as skipped",
				"Examples :-",
				"\tskip subtest \"export\"\n\t{\n\t\tclick id@export\n\t}",
				"\tfixme subtest \"checkout\"\n\t{\n\t\tclick testid@pay\n\t}\n\n",
        	};
        }
    }
