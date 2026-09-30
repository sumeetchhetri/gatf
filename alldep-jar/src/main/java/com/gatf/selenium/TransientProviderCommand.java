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

/** G8 — promoted verbatim from the nested Command.TransientProviderCommand (inherits the same members/nested types). */
@SuppressWarnings("unused")
public class TransientProviderCommand extends Command {
        FindCommand cond;
        String varname, value;
        boolean isLazy;
        TransientProviderCommand(String val, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            String[] parts = val.trim().split("[\t ]+");
            if(parts.length>=4) {
                parts[0] = parts[0].trim();
                value = unSantizedUnQuoted(parts[0], state);
                varname = unSantizedUnQuoted(parts[1], state);
                cond = new FindCommand(parts[2].trim() + " " + parts[3].trim(), fileLineDetails, state);
                if(parts.length>4 && parts[4].trim().equalsIgnoreCase("lazy")) {
                	isLazy = true;
                }
            } else {
                //excep
            }
        }
        String toCmd() {
            StringBuilder b = new StringBuilder();
            b.append("#transient-provider ");
            b.append(name);
            b.append(cond.toCmd());
            b.append(isLazy?" lazy":"");
            return b.toString();
        }
        String javacode() {
            StringBuilder b = new StringBuilder();
            b.append("newProvider(\""+value+"\");\n");
            //int counter = state.timeoutNum;
            //String cntvar = state.varnamerandom();
            String provname = state.varname();
            //b.append("int "+cntvar+" = 0;\n");
            b.append("List<String[]> " + provname + " = null;\n");
            //b.append("\nwhile(true) {\n");
            boolean multi = cond.isSemanticFind();   // M3: a provider collects every match, even under strictLocators
            if(multi) b.append("semanticMulti(true);\ntry {\n");
            b.append(cond.javacodetrprovonly(provname));
            if(multi) b.append("} finally {\nsemanticMulti(false);\n}\n");
            //b.append("if("+provname+"!=null && "+provname+".size()>0) break;\n");
            //b.append("sleep("+state.timeoutSleepGranularity+");\n");
            //b.append("if("+cntvar+"++=="+counter+")throw new RuntimeException("+cond.getErr()+");\n");
            //b.append("}\n");
            String loopname = state.varname();
            List<String> ssl = Arrays.asList(varname.split("[\t ]*,[\t ]*"));
            b.append("\nfor(int " + loopname + "=0;"+loopname+"<" + provname + ".size();"+loopname+"++) {");
            for (int i=0;i<ssl.size();i++)
            {
            	b.append("\naddToProviderTestDataMap(\""+value+"\", \""+ssl.get(i)+"\", " + provname + ".get(" + loopname + ")["+i+"]);\n"); 
            }
            b.append("}");
            ProviderLoopCommand plc = new ProviderLoopCommand(value, fileLineDetails, state, false, ProviderType.Provided);
            plc.children = children;
            b.append(plc.javacode());
            b.append("");
            return b.toString();
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
        		"Transient Provider definition",
        		"\t#transient-provider {provider-name} {variableName1,...,variableNameN} {find-expr} {sub-selector1,...,sub-selectorN} {lazy}?",
				"Examples :-",
				"\t#transient-provider prov1 var1,var2 id@'abc' text,attr@abc",
				"\t#tp prov1 var1,var2 id@'abc' text,attr@abc",
            };
        }
    }
