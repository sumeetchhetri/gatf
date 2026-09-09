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

/** G8 — promoted verbatim from the nested Command.ReadFileCommand (inherits the same members/nested types). */
@SuppressWarnings("unused")
public class ReadFileCommand extends Command {
        String name;
        boolean isBinary = false;
        ReadFileCommand(String val, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            String[] parts = val.trim().split("[\t ]+");
            name = unSantizedUnQuoted(parts[0].trim(), state);
            if(parts.length>1) {
            	isBinary = "binary".equalsIgnoreCase(parts[1].trim());
            }
        }
		String toCmd() {
            StringBuilder b = new StringBuilder();
            b.append("#readfile ");
            b.append(name);
            b.append(isBinary?" binary":"");
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
        public String javacodev() {
            StringBuilder b = new StringBuilder();
            if(isBinary) {
            	b.append(" readFileToBytes(\""+esc(name)+"\");\n");
            } else {
            	b.append(" readFileToStr(\""+esc(name)+"\");\n");
            }
            return b.toString();
        }
        String javacode() {
            StringBuilder b = new StringBuilder();
            String brl = state.varname();
            state.pushItr();
            String loopname = state.currvarnameitr();
            String vrn = state.varname();
            String pstn = state.varnamerandom();
            if(!isBinary) b.append("\njava.io.BufferedReader "+brl+" = null;");
            b.append("Map<String, Object> "+pstn+" = get__loopcontext__();\n");
            b.append("\nset__loopcontext__(\""+esc(name)+"\");\nadd__loopcontext__arg(\"ft\", \""+(isBinary?"b":"t")+"\");\n");
            b.append("\ntry {\n");
            if(isBinary) {
            	b.append("\nbyte[] "+state.varname()+" = null;");
            	b.append("\nint "+loopname+"=0;\nadd__loopcontext__arg(\"i\", "+loopname+");\n");
            	b.append("\nbyte[] "+vrn+"=org.apache.commons.io.FileUtils.readFileToByteArray(new java.io.File(\""+esc(name)+"\"));add__loopcontext__arg(\"a1\", "+vrn+");\n");
                for (Command c : children) {
                	b.append(genDebugInfo(c));
                    b.append(c.javacode());
                    b.append("\n");
                }
            } else {
            	b.append(brl+" = new java.io.BufferedReader(new java.io.InputStreamReader(new java.io.FileInputStream(\""+esc(name)+"\")));");
            	b.append("\nString "+vrn+" = null;");
            	b.append("\nint "+loopname+"=0;");
	            b.append("\nwhile (("+vrn+" = "+brl+".readLine()) != null) {\nadd__loopcontext__arg(\"i\","+loopname+");\nadd__loopcontext__arg(\"a1\", "+vrn+");\n" );
                for (Command c : children) {
                	b.append(genDebugInfo(c));
                    b.append(c.javacode());
                    b.append("\n");
                }
	            b.append("\n"+loopname+"++;");
	            b.append("\n}");
            }
            b.append("\n} catch(Exception e) {throw new RuntimeException(\"Unable to read file\", e);}");
            if(!isBinary) b.append("finally {if("+brl+"!=null) {try{"+brl+".close();}catch(Exception e){}}}");
            b.append("\nset__loopcontext__("+pstn+");");
            return b.toString();
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
        		"Read File line by line",
        		"\t#readfile {file-path} {binary}?\n\t{\n\t\tcode\n\t}",
				"Examples :-",
				"\t#readfile \"/path/to/file.txt\"\n\t{\n\t\texec @print(@index)\n\t\texec @print(@line)\n\t\tclick xpath@'$provider-variable-1'\n\t}",
				"\t#readfile \"/path/to/file.bin\" \"binary\"\n\t{\n\t\texec @print(@index)\n\t\texec @print(@line)\n\t\tclick xpath@'$provider-variable-1'\n\t}",
            };
        }
    }
