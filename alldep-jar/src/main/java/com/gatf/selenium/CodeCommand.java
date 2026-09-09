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

/** G8 — promoted verbatim from the nested Command.CodeCommand (inherits the same members/nested types). */
@SuppressWarnings("unused")
public class CodeCommand extends Command {
        static Pattern p = Pattern.compile("\\$([v|V]*)\\{([a-zA-Z0-9_]+)\\}");
        static Pattern p1 = Pattern.compile("@cntxtParam\\([a-zA-Z0-9_]+\\)");
        static Pattern p2 = Pattern.compile("@cntxtVar\\([a-zA-Z0-9_]+\\)");
        //For the code command there are only value childrens which are directly appended to the builder
        StringBuilder b = new StringBuilder();
        String lang = "java";
        String[] arglist = new String[]{};
        CodeCommand(String lang, String argnames, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            if(lang!=null && !lang.trim().isEmpty()) {
                if(lang.equalsIgnoreCase("java") || lang.equalsIgnoreCase("groovy") || lang.equalsIgnoreCase("js") || lang.equalsIgnoreCase("ruby") || lang.equalsIgnoreCase("python")) {
                    this.lang = lang.toLowerCase();
                } else {
                    throwParseError(null, new RuntimeException("Invalid code language specified"));
                }
            }
            if(argnames!=null && !argnames.isEmpty()) {
                arglist = argnames.split("[\t ]*,[\t ]*");
            }
            //b.append("\n");
        }
        String toCmd() {
            return "<<< \n" + b.toString() + "\n>>>\n";
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
				"Execute embedded code in java/js/ruby/groovy/python",
				"\t<<<(java|js|ruby|groovy|python) a,b,c\n\t{code}\n\t>>>",
				"Available variables in context -",
				"\t1. @driver - WebDriver instance (Java only)",
				"\t2. @window - WebDriver instance (Java only)",
				"\t3. @element - Currently selected WebElement instance (Java only)",
				"\t4. @sc - Currently selected SearchContext instance (Java only)",
				"\t5. @printProvJson - Print Provider data as json (Java only)",
				"\t6. @printProv - Print Provider data (Java only)",
				"\t7. @print - System.out.println (Java only)",
				"\t8. @index - Current provider index under iteration (Java only)",
				"\t9. @cntxtParam - Add string variable to current context",
				"\t9. @cntxtVar - Add variable to current context",
				"Examples :-",
				"\t<<<(java) a,b,c\n\tSystem.out.println(a);\n\t>>>",
				"\t<<<(js) a,b,c\n\tconsole.log(a);\n\t>>>",
				"\t<<<(groovy) a,b,c\n\tprintln a\n\t>>>",
				"\t<<<(ruby) a,b,c\n\tputs a\n\t>>>",
				"\t<<<(python) a,b,c\n\tprint(a)\n\t>>>",
        	};
        }
        String javacode() {
            String code = b.toString();
            code = code.replace("@index", "get__loopcontext__indx()");
            code = code.replace("@line", "get__loopcontext__var(\"a1\")");
            if(lang.equals("java")) {
                code = code.replace("@driver", "___cw___");
                code = code.replace("@window", "___ocw___");
                code = code.replace("@element", "___ce___.get(0)");
                code = code.replace("@sc", "get__loopcontext__var(\"s\")");
                code = code.replace("@printProvJson", "___cxt___print_provider__json");
                code = code.replace("@printProv", "___cxt___print_provider__");
                code = code.replace("@print", "System.out.println");
                code = code.replaceAll("@cntxtParam\\(([a-zA-Z0-9_]+)\\)", "___cxt___add_param__(\"$1\", $1)");
                code = code.replaceAll("@cntxtVar\\(([a-zA-Z0-9_]+)\\)", "___add_var__(\"$1\", $1)");
                String args = "";
                for (String arg : arglist)
                {
                    String vn = arg;
                    if(arg.indexOf("=")!=-1) {
                        vn = arg.substring(0, arg.indexOf("="));
                        arg = arg.substring(arg.indexOf("=")+1);
                    }
                    //if(vn.trim().equals(arg.trim()))continue;
                    Matcher m = p.matcher(arg);
                    if(m.find()) {
                        if(vn.trim().isEmpty()) {
                            vn = m.group(2);
                        }
                        args += "Object "+vn+" = getProviderDataValueO(\""+esc(m.group(2))+"\", "+(m.group(1).toLowerCase().trim().equals("v")?"true":"false")+");\n";
                    } else {
                    	args += "Object "+vn+" = " + state.unsanitize(arg) + ";\n";
                    }
                }
                code = args + code;
                return state.unsanitize(code);
            } else if(lang.equals("groovy")) {
                String gcode = "";
                Matcher m = p1.matcher(code);
                List<String> ms = new ArrayList<String>();
                while(m.find()) {
                    ms.add(m.group());
                }
                for (String s : ms)
                {
                    code = code.replace(s, "");
                }
                m = p2.matcher(code);
                List<String> ms1 = new ArrayList<String>();
                while(m.find()) {
                	ms1.add(m.group());
                }
                for (String s : ms1)
                {
                    code = code.replace(s, "");
                }
                gcode += "Binding __b = new Binding();\n";
                for (String arg : arglist)
                {
                	String vn = arg;
                    if(arg.indexOf("=")!=-1) {
                        vn = arg.substring(0, arg.indexOf("="));
                        arg = arg.substring(arg.indexOf("=")+1);
                    }
                    //if(vn.trim().equals(arg.trim()))continue;
                    m = p.matcher(arg);
                    if(m.find()) {
                        if(vn.trim().isEmpty()) {
                            vn = m.group(2);
                        }
                        gcode += "__b.setVariable(\""+vn+"\", getProviderDataValueO(\""+esc(m.group(2))+"\", "+(m.group(1).toLowerCase().trim().equals("v")?"true":"false")+"));\n";
                    } else {
                        gcode += "__b.setVariable(\""+vn+"\", "+state.unsanitize(arg)+");\n";
                    }
                }
                gcode += "GroovyShell __gs = new GroovyShell(__b);\n";
                gcode += "__gs.evaluate(\""+esc(state.unsanitize(code)).replaceAll("\n", "\\\\n")+"\");\n";
                for (String s : ms)
                {
                    gcode += s.replaceAll("@cntxtParam\\(([a-zA-Z0-9_]+)\\)", "___cxt___add_param__(\"$1\", __gs.getVariable(\"$1\").toString());\n");
                }
                for (String s : ms1)
                {
                    gcode += s.replaceAll("@cntxtVar\\(([a-zA-Z0-9_]+)\\)", "___add_var__(\"$1\", __gs.getVariable(\"$1\"));\n");
                }
                return gcode;
            } else if(lang.equals("js")) {
                String jscode = "";
                String args = "";
                for (String arg : arglist)
                {
                	String vn = arg;
                    if(arg.indexOf("=")!=-1) {
                        vn = arg.substring(0, arg.indexOf("="));
                        arg = arg.substring(arg.indexOf("=")+1);
                    }
                    //if(vn.trim().equals(arg.trim()))continue;
                    Matcher m = p.matcher(arg);
                    if(m.find()) {
                        if(vn.trim().isEmpty()) {
                            vn = m.group(2);
                        }
                        args += "var " + vn + " = \\\"\"+getProviderDataValue(\""+esc(m.group(2))+"\", "+(m.group(1).toLowerCase().trim().equals("v")?"true":"false")+")+\"\\\";";
                    } else {
                    	arg = arg.replace("@index", "get__loopcontext__indx()");
                    	args += "var " + vn + " = " + esc(state.unsanitize(arg)) + ";";
                    }
                }
                jscode += "if (___ocw___ instanceof JavascriptExecutor) {\n";
                jscode += "((JavascriptExecutor)___ocw___).executeScript(\""+args+esc(state.unsanitize(code)).replaceAll("\n", "\\\\n")+"\");\n}\n";
                return jscode;
            } else if(lang.equals("ruby")) {
                String rcode = "";
                Matcher m = p1.matcher(code);
                List<String> ms = new ArrayList<String>();
                while(m.find()) {
                    ms.add(m.group());
                }
                for (String s : ms)
                {
                    code = code.replace(s, "");
                }
                m = p2.matcher(code);
                List<String> ms1 = new ArrayList<String>();
                while(m.find()) {
                	ms1.add(m.group());
                }
                for (String s : ms1)
                {
                    code = code.replace(s, "");
                }
                rcode += "ScriptingContainer __rs = new ScriptingContainer(LocalVariableBehavior.PERSISTENT);\n";
                for (String arg : arglist)
                {
                	String vn = arg;
                    if(arg.indexOf("=")!=-1) {
                        vn = arg.substring(0, arg.indexOf("="));
                        arg = arg.substring(arg.indexOf("=")+1);
                    }
                    //if(vn.trim().equals(arg.trim()))continue;
                    m = p.matcher(arg);
                    if(m.find()) {
                        if(vn.trim().isEmpty()) {
                            vn = m.group(2);
                        }
                        rcode += "__rs.put(\""+vn+"\", getProviderDataValueO(\""+esc(m.group(2))+"\", "+(m.group(1).toLowerCase().trim().equals("v")?"true":"false")+"));\n";
                    } else {
                        rcode += "__rs.put(\""+vn+"\", "+state.unsanitize(arg)+");\n";
                    }
                }
                rcode += "__rs.runScriptlet(\""+esc(state.unsanitize(code)).replaceAll("\n", "\\\\n")+"\");\n";
                for (String s : ms)
                {
                    rcode += s.replaceAll("@cntxtParam\\(([a-zA-Z0-9_]+)\\)", "___cxt___add_param__(\"$1\", __rs.get(\"$1\").toString());\n");
                }
                for (String s : ms1)
                {
                	rcode += s.replaceAll("@cntxtVar\\(([a-zA-Z0-9_]+)\\)", "___add_var__(\"$1\", __rs.get(\"$1\"));\n");
                }
                return rcode;
            } else if(lang.equals("python")) {
                String pcode = "";
                Matcher m = p1.matcher(code);
                List<String> ms = new ArrayList<String>();
                while(m.find()) {
                    ms.add(m.group());
                }
                for (String s : ms)
                {
                    code = code.replace(s, "");
                }
                m = p2.matcher(code);
                List<String> ms1 = new ArrayList<String>();
                while(m.find()) {
                	ms1.add(m.group());
                }
                for (String s : ms1)
                {
                    code = code.replace(s, "");
                }
                pcode += "PythonInterpreter pi = new PythonInterpreter();\n";
                for (String arg : arglist)
                {
                	String vn = arg;
                    if(arg.indexOf("=")!=-1) {
                        vn = arg.substring(0, arg.indexOf("="));
                        arg = arg.substring(arg.indexOf("=")+1);
                    }
                    //if(vn.trim().equals(arg.trim()))continue;
                    m = p.matcher(arg);
                    if(m.find()) {
                        if(vn.trim().isEmpty()) {
                            vn = m.group(2);
                        }
                        pcode += "pi.set(\""+vn+"\", getProviderDataValueO(\""+esc(m.group(2))+"\", "+(m.group(1).toLowerCase().trim().equals("v")?"true":"false")+"));\n";
                    } else {
                        pcode += "pi.set(\""+vn+"\", "+state.unsanitize(arg)+");\n";
                    }
                }
                pcode += "pi.exec(\""+esc(state.unsanitize(code)).replaceAll("\n", "\\\\n")+"\");\n";
                pcode += "pi.close();\n";
                for (String s : ms)
                {
                    pcode += s.replaceAll("@cntxtParam\\(([a-zA-Z0-9_]+)\\)", "___cxt___add_param__(\"$1\", __rs.get(\"$1\").toString());\n");
                }
                for (String s : ms1)
                {
                	pcode += s.replaceAll("@cntxtVar\\(([a-zA-Z0-9_]+)\\)", "___add_var__(\"$1\", __rs.get(\"$1\"));\n");
                }
                return pcode;
            }
            return "";
        }
    }
