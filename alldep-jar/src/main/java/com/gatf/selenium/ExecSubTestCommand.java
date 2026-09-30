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

/** G8 — promoted verbatim from the nested Command.ExecSubTestCommand (inherits the same members/nested types). */
@SuppressWarnings("unused")
public class ExecSubTestCommand extends Command {
    	SubTestCommand subt = null;
    	String sessionName;
        Integer sessionId = null;
        String args = "";
        Map<String, String> params = new HashMap<String, String>();
        String[] lazyState = null;
        ExecSubTestCommand(Object[] cmdDetails, CommandState state) {
        	super(cmdDetails, state);
        }
    	ExecSubTestCommand(String val, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            String[] parts = val.trim().split("[\t ]+");
            if(parts.length==0) {
            	throwError(fileLineDetails, new RuntimeException("Please specify a subtest name to execute"));
            }
            name = unSantizedUnQuoted(parts[0].trim(), state);
            for (Command c : state.allSubTests) {
            	SubTestCommand st = (SubTestCommand)c;
				if(st.hook==null && st.name.equalsIgnoreCase(name)) {
					subt = st;
					if(StringUtils.isBlank(sessionName)) {
						sessionName = subt.sessionName;
					}
					if(sessionId==null) {
						sessionId = subt.sessionId;
					}
					break;
				}
            }
            if(subt==null) {
            	lazyState = parts;
            	return;
            	//throwError(fileLineDetails, new RuntimeException("No func/subtest with that name found - " + name));
            }
            postProcess(parts);
        }
    	private void postProcess(String[] parts) {
    		if(parts.length>1) {
            	String pstr = "";
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
	            				if(!parts[counter].equals("(")) {
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
    						if(param.indexOf(":")==-1) {
    							if(subt.params.size()==1 && nparts.length==1) {}
    							else {
    								throwError(fileLineDetails, new RuntimeException("Invalid parameter syntax, please use param-name:param-value syntax"));
    							}
							}
    						args += param + " ";
    						String[] pdet = new String[] {param.substring(0, param.indexOf(":")), param.substring(param.indexOf(":")+1)};
    						if(subt.params.size()==1 && nparts.length==1) {
    							params.put(subt.params.iterator().next(), pdet[1].trim().isEmpty()?param:pdet[1]);
    						} else if(subt.params.contains(pdet[0].trim())) {
								if(params.containsKey(pdet[0].trim())) {
									throwError(fileLineDetails, new RuntimeException("Duplicate parameter name `"+pdet[0].trim()+"`"));
								}
								if(pdet[1]!=null && pdet[1].length()>=2 && pdet[1].charAt(0)==pdet[1].charAt(pdet[1].length()-1)) {
						            if(pdet[1].charAt(0)=='"' || pdet[1].charAt(0)=='\'') {
						            	pdet[1] = pdet[1].substring(1, pdet[1].length()-1);
						            }
						        }
								params.put(pdet[0].trim(), pdet[1]);
							} else {
								if(subt.params.size()==0) {
									throwError(fileLineDetails, new RuntimeException((subt.isAFunc?"func":"subtest")+" does not specify any parameters"));
								} else {
									throwError(fileLineDetails, new RuntimeException("Invalid "+(subt.isAFunc?"func":"subtest")+" parameter name, allowed parameters are " + subt.params.toString()));
								}
							}
    					}
            			
            			counter++;
                		if(counter<parts.length && !subt.isAFunc) {
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
            	} else if(!subt.isAFunc) {
            		if(parts.length==2) {
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
            	} else {
            		throwError(fileLineDetails, new RuntimeException("Subtest/Function definition arguments should follow within `(` and `)`"));
            	}
            }
            
            if(subt.params.size()!=params.size()) {
            	throwError(fileLineDetails, new RuntimeException("Please provide all the arguments for execution"));
            }

            if(!subt.isAFunc) {
	            /*for (Command c : state.allSubTests) {
	            	SubTestCommand st = (SubTestCommand)c;
					if(st.name.equals(name) && !st.isAFunc) {
						Map<String, String> nparams = new HashMap<String, String>(st.params);
						nparams.putAll(params);
						subt = st;
						if(StringUtils.isBlank(sessionName)) {
							sessionName = subt.sessionName;
						}
						if(sessionId==null) {
							sessionId = subt.sessionId;
						}
						break;
					}
				}
	            if(subt==null) {
	            	throwError(fileLineDetails, new RuntimeException("Subtest with name \""+name+"\" not found"));
	            }*/
	            state.addSubtest(this);
            }
    	}
        String javacode() {
            return javacodesubtest(false);
        }
        String javacodesubtest(boolean initvars) {
        	if(lazyState!=null) {
        		for (Command c : state.allSubTests) {
                	SubTestCommand st = (SubTestCommand)c;
    				if(st.hook==null && st.name.equalsIgnoreCase(name)) {
    					subt = st;
    					if(StringUtils.isBlank(sessionName)) {
    						sessionName = subt.sessionName;
    					}
    					if(sessionId==null) {
    						sessionId = subt.sessionId;
    					}
    					break;
    				}
                }
                if(subt==null) {
                	throwError(fileLineDetails, new RuntimeException("No func/subtest with that name found - " + name));
                }
                postProcess(lazyState);
        	}
            StringBuilder b = new StringBuilder();
            if(!subt.isAFunc) {
	            if(state.modeExecType==2) {
	                if(sessionName!=null) {
	                    b.append("setSession(\""+esc(sessionName)+"\", -1, true);\n"); 
	                } else if(sessionId>0) {
	                    b.append("setSession(null, "+(sessionId-1)+", true);\n"); 
	                }
	            } else {
	                b.append("if(matchesSessionId("+(sessionName!=null?"\""+esc(sessionName)+"\"":"null")+", "
	                        +((sessionId!=null&&sessionId>0)?(sessionId+""):"-1") + ")) {\n");
	            }
	            if(initvars) {
	                b.append("___sc___1 = get___d___();\n");
	                b.append("___cw___ = get___d___();\n");
	                b.append("___ocw___ = ___cw___;\n");
	            }
            }
            for (String pn : params.keySet()) {
				b.append("___add_var__(\""+pn+"\", evaluate(\""+esc(params.get(pn))+"\"));\n");
			}
            b.append(genDebugInfo(this));
            subt.calls += 1;
            String name_ = subt.name + " (" + subt.calls + ")" + (args.trim().isEmpty()?"":(" (" + args.trim() + ")"));
            String skip = subt.isAFunc ? null : skipReason(subt, state.hasOnly());
            if(skip!=null) {
            	// M6: skip / fixme / not-only: the subtest is reported as skipped without running (hooks included)
            	b.append("__skip_subtest__(\""+esc(subt.name)+"\", \""+esc(name_)+"\", \""+skip+"\");\n");
            } else {
            	b.append("___ce___ = ___invoke_sub_test_dyn___(getClass(), \"" + subt.fName+"\", \""+esc(name_)+"\", ___cw___, ___ocw___, "+state.currvarnamesc()+", ___lp___);\n");
            }
            for (String pn : params.keySet()) {
				b.append("___del_var__(\""+pn+"\");\n");
			}
            if(!subt.isAFunc && state.modeExecType!=2) {
                b.append("}\n");
            }
            return b.toString();
        }
        /** M6: why a subtest call is skipped — its skip/fixme marker, "only" when another subtest is marked only — or null. */
        static String skipReason(SubTestCommand st, boolean fileHasOnly) {
        	if("skip".equals(st.marker) || "fixme".equals(st.marker)) return st.marker;
        	if(fileHasOnly && !"only".equals(st.marker)) return "only";
        	return null;
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
				"Subtest/Function execution",
				"\t@call \"name\" session-name|@session-id (args)",
				"where",
				"\tsession-name - the browser session name for which to run this sub test",
				"\tsession-id - the browser session id prefixed with @ for which to run this sub test",
				"\targs - the arguments passed to the subtest, space separated pairs of colon separated name/value",
				"Examples :-",
				"\t@call \"func1\" @1 (arg1:1 arg2:abc)\n\t{\n\t\tselect index@$v{arg1} id@\"$v{arg2}\"\n\t}\n\n"
        	};
        }
    }
