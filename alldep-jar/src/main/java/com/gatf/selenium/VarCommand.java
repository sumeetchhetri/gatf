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

/** G8 — promoted verbatim from the nested Command.VarCommand (inherits the same members/nested types). */
@SuppressWarnings("unused")
public class VarCommand extends FindCommandImpl {
        String name;
        boolean isCntxtVar = false;
        PluginCommand pcomd;
        ReadFileCommand rcmd;
        String val = "";
        String type = "";
        VarCommand(String val, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            if(val.indexOf(" ")!=-1) {
                name = val.substring(0, val.indexOf(" ")).trim();
                if(name.startsWith("@")) {
                    isCntxtVar = true;
                    name = name.substring(1);
                } else if(name.indexOf("@")!=-1) {
                	type = name.substring(name.indexOf("@")+1);
                	name = name.substring(0, name.indexOf("@"));
                }
                val = val.substring(val.indexOf(" ")+1).trim();
                if(!val.startsWith("@") && val.indexOf("@")!=-1) {
                    cond = new FindCommand(val, fileLineDetails, state);
                } else if(val.startsWith("plugin ")) {
                    pcomd = new PluginCommand(val.substring(7), cmdDetails, state);
                } else {
                    this.val = state.unsanitize(val);
                }
                if(pcomd==null) {
                	if(this.val.startsWith("readfile ")) {
                    	rcmd = new ReadFileCommand(this.val.substring(9), cmdDetails, state);
                    	this.val = null;
                    } else if(this.val.startsWith("readimgtext ")) {
                    	rcmd = new ReadFileCommand(this.val.substring(12), cmdDetails, state);
                    	this.val = null;
                    }
                }
            } else {
                //excep
            }
        }
        public void reconcile() {
        	if(pcomd!=null && children.size()>0) {
        		pcomd.children.addAll(children);
        		pcomd.reconcile();
        	}
        }
        String toCmd() {
            if(cond!=null) {
                return "var " + name + " " + cond.toCmd();
            } else if(pcomd!=null) {
                return "var " + name + " plugin " + pcomd.toCmd();
            } else {
            	if(rcmd!=null) {
            		return "var " + name + " " + rcmd.toCmd();
            	}
                return "var " + name + " " + val; 
            }
        }
        String javacode() {
            if(cond!=null) {
            	StringBuilder b = new StringBuilder();
                //int counter = state.timeoutNum;
                //String cntvar = state.varnamerandom();
                String varname = state.varname();
                //b.append("int "+cntvar+" = 0;\n");
                b.append("List<String[]> " + varname + " = null;\n");
                //b.append("\nwhile(true) {\n");
                b.append(cond.javacodetrprovonly(varname));
                //b.append("if("+varname+"!=null && "+varname+".size()>0) break;\n");
                //b.append("sleep("+state.timeoutSleepGranularity+");\n");
                //b.append("if("+cntvar+"++=="+counter+")throw new RuntimeException("+cond.getErr()+");\n");
                //b.append("}\n");
                b.append(isCntxtVar?"\n___cxt___add_param__(\""+name+"\", "+varname+".get(0)[0]);":"\n___add_var__(\""+name+"\", "+varname+".get(0)[0]);\n");
                return b.toString();
            } else if(pcomd!=null) {
                return "\nObject " + name + " = null;\n"+pcomd.javacodev(name)+";" + (isCntxtVar?"\n___cxt___add_param__(\""+name+"\", "+name+");":"\n___add_var__(\""+name+"\", "+name+");");
            } else {
            	if(rcmd!=null) {
            		return "\nObject " + name + " = "+rcmd.javacodev() + (isCntxtVar?"\n___cxt___add_param__(\""+name+"\", "+name+");":"\n___add_var__(\""+name+"\", "+name+");");
            	}
                return "\n"+(val.startsWith("@")?"Object ":"String ") + name + " = "+(val.startsWith("@")?val.substring(1):val)+";\n" + (isCntxtVar?"\n___cxt___add_param__(\""+name+"\", "+name+");":"\n___add_var__(\""+name+"\", "+name+");");
            }
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
				"Variable definition",
				"\tvar name @{another-variable-name}|plugin ...|{primitive-value}",
				"Examples :-",
				"\tvar var0 \"Some text\"",
				"\tvar var1 @var0",
				"\tvar var1 plugin jsonpath $v{myvar} out.x.y.z",
				"\tvar var1 123455",
				"\tvar var1 123.455",
				"\tvar var1 true",
				"\tvar var1 new java.util.Date()",
        	};
        }
    }
