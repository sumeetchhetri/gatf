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

/** G8 — promoted verbatim from the nested Command.IfCommand (inherits the same members/nested types). */
@SuppressWarnings("unused")
public class IfCommand extends Command {
    	List<FindCommand> conds = new ArrayList<>();
        List<ElseIfCommand> elseifs = new ArrayList<ElseIfCommand>();
        ElseCommand elsecmd;
        boolean negation;
        IfCommand(String cmd, boolean negation, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            this.negation = negation;
            String[] parts = cmd.trim().split("&");
            for (String part : parts) {
            	if(part.charAt(0)==part.charAt(part.length()-1)) {
                    if(part.charAt(0)=='"' || part.charAt(0)=='\'') {
                    	part = part.substring(1, part.length()-1);
                    }
                }
            	conds.add(new FindCommand(part, cmdDetails, state));
			}
        }
        String toCmd() {
            StringBuilder b = new StringBuilder();
            b.append("?");
            for (FindCommand fcmd : conds) {
            	b.append(fcmd.toCmd());
            	b.append(" ");
			}
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
        static String getFp(Command ifcmd, List<FindCommand> conds, List<Command> children, boolean negation, CommandState state, String vrd) {
            StringBuilder b = new StringBuilder();
            String ex = state.evarname();
            b.append("try{\n");
            if(conds!=null) {
            	for (FindCommand fcmd : conds) {
            		b.append(fcmd.javacodeifonly(children, vrd));
            	}
            }
            //b.append("\n"+vrd+"=true;\n");
            if(!negation)
            {
                if(!children.isEmpty())
                {
                    b.append("\n");
                    for (Command c : children) {
                    	b.append(genDebugInfo(c));
                        b.append(c.javacode());
                        b.append("\n");
                    }
                }
            }
            b.append("\n}\ncatch(AssertionError "+state.evarname()+"){");
            if(negation)
            {
                if(!children.isEmpty())
                {
                    b.append("\n");
                    for (Command c : children) {
                    	b.append(genDebugInfo(c));
                        b.append(c.javacode());
                        b.append("\n");
                    }
                }
                //b.append("\n"+vrd+"=true;\n");
            }
            else
            {
                //b.append("\n"+vrd+"=false;\n");
            }
            b.append("}\ncatch(Exception "+ex+"){\nSystem.out.println("+ex+".getMessage());throw "+ex+";}\n");
            return b.toString();
        }
        String javacode() {
            StringBuilder b = new StringBuilder();
            state.pushifcnt();
            String vrd = state.currvarnameifcnt();
            b.append("boolean "+vrd+" = false;");
            b.append(getFp(this, conds, children, negation, state, vrd));
            if(elseifs.size()>0)
            {
                for (int i=0;i<elseifs.size();i++) {
                    b.append("if(!"+vrd+"){");
                    b.append(genDebugInfo(elseifs.get(i)));
                    b.append(elseifs.get(i).javacode());
                    b.append("}");
                }
            }
            if(elsecmd!=null) {
                b.append("if(!"+vrd+"){");
                b.append(genDebugInfo(elsecmd));
                b.append(elsecmd.javacode()+"\n");
                b.append("}");
            }
            state.prevvarnameifcnt();
            return b.toString();
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
        		"If block",
        		"\t? {find-expr} & {find-expr}?\n\t{\n\t\tcode\n\t}",
        		"\t? eval {template-expr} & {template-expr}?\n\t{\n\t\tcode\n\t}",
        		"\t? browser-scope {browser-name}?\n\t{\n\t\tcode\n\t}",
        		"\t? session-scope {browser-name}?\n\t{\n\t\tcode\n\t}",
				"Examples :-",
				"\t? xpath@\"ddd\"\n\t{\n\t\texec @print(\"if\")\n\t}",
				"\t? eval \"a\"==\"a\" & eval ${bvar}!=\"b\"\n\t{\n\t\texec @print(\"if\")\n\t}",
				"\t? browser-scope \"chrome\"\n\t{\n\t\texec @print(\"if\")\n\t}",
				"\t? s-scope \"one\"\n\t{\n\t\texec @print(\"if\")\n\t}"
            };
        }
    }
