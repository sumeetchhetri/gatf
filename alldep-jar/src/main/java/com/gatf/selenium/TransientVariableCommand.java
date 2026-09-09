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

/** G8 — promoted verbatim from the nested Command.TransientVariableCommand (inherits the same members/nested types). */
@SuppressWarnings("unused")
public class TransientVariableCommand extends Command {
        FindCommand cond;
        String varname;
        boolean isSuiteLevel;
        TransientVariableCommand(String val, Object[] cmdDetails, CommandState state, boolean isSuiteLevel) {
            super(cmdDetails, state);
            String[] parts = val.trim().split("[\t ]+");
            if(parts.length>=4) {
                parts[0] = parts[0].trim();
                varname = parts[0];
                varname = unSantizedUnQuoted(varname, state);
                cond = new FindCommand(parts[1].trim() + " " + parts[2].trim(), fileLineDetails, state);
            } else {
                //excep
            }
        }
        String toCmd() {
            StringBuilder b = new StringBuilder();
            b.append(isSuiteLevel?"#transient-suite-variable ":"#transient-variable ");
            b.append(name);
            b.append(cond.toCmd());
            return b.toString();
        }
        String javacode() {
            StringBuilder b = new StringBuilder();
            b.append("newTopLevelProvider();\n");
            //int counter = state.timeoutNum;
            //String cntvar = state.varnamerandom();
            String provname = state.varname();
            //b.append("int "+cntvar+" = 0;\n");
            b.append("List<String[]> " + provname + " = null;\n");
            //b.append("\nwhile(true) {\n");
            b.append(cond.javacodetrprovonly(provname));
            //b.append("if("+provname+"!=null && "+provname+".size()>0) break;\n");
            //b.append("sleep("+state.timeoutSleepGranularity+");\n");
            //b.append("if("+cntvar+"++=="+counter+")throw new RuntimeException("+cond.getErr()+");\n");
            //b.append("}\n");
            String loopname = state.varname();
            List<String> ssl = Arrays.asList(varname.split("[\t ]*,[\t ]*"));
            b.append("\nfor(int " + loopname + "=0;"+loopname+"<" + provname + ".size();"+loopname+"++) {");
            for (int i=0;i<ssl.size();i++)
            {
                b.append("\naddToTopLevelProviderTestDataMap(\""+ssl.get(i)+"\", " + provname + ".get(" + loopname + ")["+i+"]);\n"); 
            }
            b.append("break;\n}");
            return b.toString();
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
        		"Transient Variable definition",
        		"\t#transient-variable {variable-name} {find-expr} {sub-selector}",
				"Examples :-",
				"\t#transient-variable var1 id@'abc' text",
				"\t#transient-variable var1 id@'abc' html",
				"\t#transient-variable var1 id@'abc' attr@data-prop",
				"\t#tv var1 id@'abc' text",
				"\t#tv var1 id@'abc' html",
				"\t#tv var1 id@'abc' attr@data-prop",
            };
        }
    }
