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

/** G8 — promoted verbatim from the nested Command.ExecCommand (inherits the same members/nested types). */
@SuppressWarnings("unused")
public class ExecCommand extends Command {
        String code;
        static Pattern p = Pattern.compile("\\$([v|V]*)\\{([a-zA-Z0-9_]+)\\}");
        ExecCommand(String code, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            this.code = code;
            this.code = state.unsanitize(code);
        }
        String toCmd() {
            return "exec " + code;
        }
        String javacode() {
            code = code.replace("@driver", "___cw___");
            code = code.replace("@window", "___ocw___");
            code = code.replace("@element", "___ce___.get(0)");
            code = code.replace("@sc", "get__loopcontext__var(\"s\")");
            code = code.replace("@printProvJson", "___cxt___print_provider__json");
            code = code.replace("@printProv", "___cxt___print_provider__");
            code = code.replace("@print", "System.out.println");
            code = code.replace("@index", "get__loopcontext__indx()");
            code = code.replace("@line", "get__loopcontext__var(\"a1\")");
            code = code.replaceAll("@cntxtParam\\(([a-zA-Z0-9_]+)\\)", "___cxt___add_param__(\"$1\", $1)");
            code = code.replaceAll("@cntxtVar\\(([a-zA-Z0-9_]+)\\)", "___add_var__(\"$1\", $1)");
            Matcher m = p.matcher(code);
            String fcode = "";
            int start = 0;
            while(m.find()) {
                int s = m.start();
                fcode += code.substring(start, s) + "getProviderDataValue(\""+esc(m.group(2))+"\", "+(m.group(1).toLowerCase().trim().equals("v")?"true":"false")+")";
                start = m.end();
            }
            fcode += code.substring(start);
            return fcode + ";";
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
				"Execute java code",
				"\texec {java statement}",
				"Available variables in context -",
				"\t1. @driver - WebDriver instance",
				"\t2. @window - WebDriver instance",
				"\t3. @element - Currently selected WebElement instance",
				"\t4. @sc - Currently selected SearchContext instance",
				"\t5. @printProvJson - Print Provider data as json",
				"\t6. @printProv - Print Provider data",
				"\t7. @print - System.out.println",
				"\t8. @index - Current provider index under iteration",
				"Examples :-",
	    		"\texec @driver.refresh()",
	    		"\texec @window.back()",
	    		"\texec @element.click()",
	    		"\texec @sc.findElement(org.openqa.selenium.By.id(\"id\")",
	    		"\texec @printProvJson(\"provider-name\")",
	    		"\texec @printProv(\"provider-name\")",
	    		"\texec @print(\"something-to-console\")",
	    		"\texec @print(@index)",
	    		"\texec System.out.println(\"Hello World!\")"
        	};
        }
    }
