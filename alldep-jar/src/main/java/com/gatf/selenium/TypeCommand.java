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

/** G8 — promoted verbatim from the nested Command.TypeCommand (inherits the same members/nested types). */
@SuppressWarnings("unused")
public class TypeCommand extends FindCommandImpl {
    	String type = "";
        String value;
        String qualifier = "";
        String chs = "";
        String v1, v2, v3;
        TypeCommand(String val, Object[] cmdDetails, CommandState state, FindCommand fcmd, String type, String qualifier) {
            super(cmdDetails, state);
            //this.cond = fcmd;
            this.type = type;
            if(qualifier!=null) {
            	this.qualifier = qualifier;
            }
            String[] parts = val.trim().split("[\t ]+");
            if(parts.length>0) {
            	if(type.equalsIgnoreCase("chord")) {
                    value = unSantizedUnQuoted(parts[0], state);
                    String temp = StringEscapeUtils.unescapeJava(value);
                    try {
                        if(fcmd==null) {
                        	String cstr = Stream.<String>of(parts).skip(1).collect(Collectors.joining(" "));
                            cond = new FindCommand(cstr, fileLineDetails, state);
                        }
                        List<String> values = new ArrayList<String>();
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
                        if(values!=null && !values.isEmpty()) {
        	                for (int i=0;i<values.size();i++) {
        	                    chs += values.get(i);
        	                    chs += (i!=values.size()-1)?", ":"";
        	                }
                        }
                    } catch (Exception e) {
                    }
                } else if(type.equalsIgnoreCase("type") || type.equalsIgnoreCase("sendkeys")) {
                	parts[0] = parts[0].trim();
                    value = unSantizedUnQuoted(parts[0], state);
                    if(fcmd==null) {
                    	String cstr = Stream.<String>of(parts).skip(1).collect(Collectors.joining(" "));
                        cond = new FindCommand(cstr.trim(), fileLineDetails, state);
                    }
                } else {
                	int c = 1, t = 1;
                    if(fcmd!=null) {
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
        					if(v1.startsWith("fuzzy")) {
        						if(v2.length()>4 && v2.charAt(1)=='@' && v2.contains(":")) {
        							
        						} else {
        							throwParseError(null, new RuntimeException("Randomize command with fuzzy logic needs the syntax fuzzy <sep-char>@<seq-len>:<seq-len>:<seq-len> --> _@3:4:5 --> 333_4444_55555"));
        						}
        					} else {
        						throwParseError(null, new RuntimeException("Randomize command needs first argument to be a number for alphanumeric|alpha|alphanumericlc|alphalc|alphanumericuc|alphauc|numeric|range"));
        					}
        				}
        			}
                    
                    try {
                    	Long.parseLong(v3);
        			} catch (Exception e) {
        				if(v3!=null) {
        					throwParseError(null, new RuntimeException("Randomize command needs second argument to be a number for alphanumeric|alpha|alphanumericlc|alphalc|alphanumericuc|alphauc|numeric|range"));
        				}
        			}
                }
            } else {
                //excep
            }
        }
        String toCmd() {
            return type + (qualifier!=null?qualifier:"") + " \"" + value + "\"" + (cond!=null?cond.toCmd():"");
        }
        String javacode() {
            StringBuilder b = new StringBuilder();
            
            String wename = state.currvarname();
            if(cond!=null) {
                b.append(cond.javacodeonly());
                wename = "___ce___";
            }
            
            if(type.equalsIgnoreCase("chord")) {
                //if(cond!=null) {
                //    b.append(cond.getActionable("sendKeys", "Keys.chord("+chs+")", null));
                //} else {
                	b.append("\nsendKeys(___cw___, "+wename+".get(0), \""+type+"\", \""+qualifier+"\", Keys.chord((\""+chs+"\"));\n");
                //}
            } else if(type.equalsIgnoreCase("type") || type.equalsIgnoreCase("sendkeys")) {
            	//if(cond!=null) {
                //    b.append(cond.getActionable("sendKeys", "evaluate(\""+esc(value)+"\")", null));
                //} else {
                    //b.append("\n"+state.currvarname()+".get(0).sendKeys(evaluate(\""+esc(value)+"\"));");
                    b.append("\nsendKeys(___cw___, "+wename+".get(0), \""+type+"\", \""+qualifier+"\", evaluate(\""+esc(value)+"\"));\n");
                //}
            } else {
            	//if(cond!=null) {
            	//	b.append(cond.getActionable("randomize", "\""+(v1!=null?esc(v1):"")+"\", \""+(v2!=null?esc(v2):"")+"\", \""+(v3!=null?esc(v3):"")+"\"", null));
            	//} else {
            		b.append("\nsendKeys(___cw___, "+wename+".get(0), \""+type+"\", \""+qualifier+"\", evaluate(\""+(v1!=null?esc(v1):"")+"\" + \" "+(v2!=null?esc(v2):"")+"\" + \" "+(v3!=null?esc(v3):"")+"\"));\n");
            	//}
            }
            return b.toString();
        }
        String javacodeonly(String cvarnm) {
        	StringBuilder b = new StringBuilder();
        	if(type.equalsIgnoreCase("chord")) {
	            b.append("\nsendKeys(___cw___, "+cvarnm+".get(0), \""+type+"\", \""+qualifier+"\", Keys.chord((\""+chs+"\"));\n");
            } else if(type.equalsIgnoreCase("type") || type.equalsIgnoreCase("sendkeys")) {
                b.append("\nsendKeys(___cw___, "+cvarnm+".get(0), \""+type+"\", \""+qualifier+"\", evaluate(\""+esc(value)+"\"));\n");
            } else {
            	b.append("\nsendKeys(___cw___, "+cvarnm+".get(0), \""+type+"\", \""+qualifier+"\", evaluate(\""+(v1!=null?esc(v1):"")+"\" + \" "+(v2!=null?esc(v2):"")+"\" + \" "+(v3!=null?esc(v3):"")+"\"));\n");
            }
            return b.toString();
        }
        String selcode(String varnm) {
            if(varnm==null) {
                varnm = state.currvarname();
            }
        	if(type.equalsIgnoreCase("chord")) {
	            return ("\nsendKeys(___cw___, "+varnm+".get(0), \""+type+"\", \""+qualifier+"\", Keys.chord((\""+chs+"\"));\n");
            } else if(type.equalsIgnoreCase("type") || type.equalsIgnoreCase("sendkeys")) {
            	return ("\nsendKeys(___cw___, "+varnm+".get(0), \""+type+"\", \""+qualifier+"\", evaluate(\""+esc(value)+"\"));\n");
            } else {
            	return ("\nsendKeys(___cw___, "+varnm+".get(0), \""+type+"\", \""+qualifier+"\", evaluate(\""+(v1!=null?esc(v1):"")+"\" + \" "+(v2!=null?esc(v2):"")+"\" + \" "+(v3!=null?esc(v3):"")+"\"));\n");
            }
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
        		"Type Value in input/textarea elements",
        		"\t(type|sendkeys|chord|randomize)(bl|ch|bk|cl|fo) {text} {find-expr}",
        		"\twhere \n\t\tbl->Trigger Blur\n\t\tch->Trigger Change\n\t\tbk->Trigger Backspace\n\t\tcl->Trigger Click\n\t\tfo->Trigger Focus",
        		"Examples :-",
        		"\ttype 'abc' id@'ele1'",
        		"\tsendkeys 'abc' id@'ele1'",
        		"\ttypebl 'abc' id@'ele1'",
        		"\tsendkeyscl 'abc' id@'ele1'",
            };
        }
    }
