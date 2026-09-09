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

/** G8 — promoted verbatim from the nested Command.ScopedLoopCommand (inherits the same members/nested types). */
@SuppressWarnings("unused")
public class ScopedLoopCommand extends Command {
        FindCommand cond;
        ScopedLoopCommand(Object[] cmdDetails, CommandState state, FindCommand cond) {
            super(cmdDetails, state);
            this.cond = cond;
        }
        String toCmd() {
            StringBuilder b = new StringBuilder();
            b.append("##");
            b.append(cond.toCmd());
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
            StringBuilder b = new StringBuilder();
            if(!children.isEmpty())
            {
                b.append(cond.javacodeonly());
                String cvarname = cond.getActionableVar();
                //pushSc();
                state.pushParc();
                state.pushItr();
                state.loopCounter++;
                String varitr = state.currvarnameitr();
                String vrn = state.varname();
                b.append("Map<String, Object> "+vrn+" = get__loopcontext__();\n");
                b.append("\nset__loopcontext__(\"\");\n");
                b.append("\nif("+cond.condition()+") {\n");
                b.append("\nint "+varitr + " = 0;");
                b.append("\nfor(final WebElement " + state.varname() + " : " + cvarname + ") {\n");
                b.append("\nadd__loopcontext__arg(\"i\","+varitr+");\n");
                b.append("final SearchContext "+state.currvarnameparc()+" = "+state.currvarname()+";");
                String vr = state.currvarname();
                b.append("\nList<WebElement> "+ state.thisat()+" = new java.util.ArrayList<WebElement>(){{add("+vr+");}};");
                for (Command c : children) {
                	b.append(genDebugInfo(c));
                    b.append(c.javacode());
                    b.append("\n");
                }
                b.append(varitr+"++;\n}\n}\nset__loopcontext__("+vrn+");\n");
                //prevvarnamesc();
                state.prevvarnameparc();
                state.prevvarnameitr();
                state.loopCounter--;
            }
            return b.toString();
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
        		"Loop block",
        		"\t## {find-expr}\n\t{\n\t\tcode\n\t}",
				"Examples :-",
				"\t## class@\"ddd\"\n\t{\n\t\texec @print(@index)\n\t\tclick xpath@\"ddd-@index\"\n\t}"
            };
        }
    }
