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

/** G8 — promoted verbatim from the nested Command.PluginCommand (inherits the same members/nested types). */
@SuppressWarnings("unused")
public class PluginCommand extends Command {
        String name;
        List<String> in = new ArrayList<String>();
        List<String[]> in1 = new ArrayList<String[]>();
        String outtype;
        boolean rawCurl;
        String toCmd() {
            return "";
        }
        String javacodev(String vn) {
            String avn = state.alvarname();
            String c = "List<String> "+avn+" = new java.util.ArrayList<String>();\n";
            for (String i : in) {
                c += avn + ".add(\""+esc(i)+"\");\n";
            }
            String avn1 = state.alvarname();
            c += "List<List<String>> "+avn1+" = new java.util.ArrayList<List<String>>();\n";
            for (String[] i : in1) {
                String avn2 = state.alvarname();
                c += "List<String> "+avn2+" = new java.util.ArrayList<String>();\n";
                for (String s : i) {
                    c += avn2 + ".add(\""+esc(s)+"\");\n";
                }
                c += avn1 + ".add("+avn2+");\n";
            }
            c += vn + " = pluginize(\""+name+"\", \""+plugins.get(name)+"\", "+avn+", "+avn1+")";
            return c;
        }
        String javacode() {
            String avn = state.alvarname();
            String c = "List<String> "+avn+" = new java.util.ArrayList<String>();\n";
            for (String i : in) {
                c += avn + ".add(\""+esc(i)+"\");\n";
            }
            String avn1 = state.alvarname();
            c += "List<List<String>> "+avn1+" = new java.util.ArrayList<List<String>>();\n";
            for (String[] i : in1) {
                String avn2 = state.alvarname();
                c += "List<String> "+avn2+" = new java.util.ArrayList<String>();\n";
                for (String s : i) {
                    c += avn2 + ".add(\""+esc(s)+"\");\n";
                }
                c += avn1 + ".add("+avn2+");\n";
            }
            c += "pluginize(\""+name+"\", \""+plugins.get(name)+"\", "+avn+", "+avn1+");\n";
            return c;
        }
        public PluginCommand(String info, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            String[] parts = info.trim().split("[\t ]+");
            if(parts.length>0) {
                name = unSantizedUnQuoted(parts[0], state);
                if(!plugins.containsKey(name)) {
                    throwError(cmdDetails, new RuntimeException("Plugin not found with name " + name));
                }
                if("curl".equals(name) && parts.length > 1 && isRawCurl(unSantizedUnQuoted(parts[1], state))) {
                    rawCurl(state.unsanitize(info.trim()), cmdDetails);
                    return;
                }
                for (int i = 1; i < parts.length; i++) {
                    in.add(unSantizedUnQuoted(parts[i], state));
                }
                for (int i = 0; i < children.size(); i++) {
                    if(children.get(i) instanceof ValueListCommand) {
                        ValueListCommand vlc = (ValueListCommand)children.get(i);
                        String[] tm = new String[vlc.children.size()-1];
                        int c = 0;
                        for (String vc : vlc.getValues()) {
                        	tm[c++] = unSantizedUnQuoted(vc, state);
                        }
                        in1.add(tm);
                    } else {
                    	if(children.get(i) instanceof ValueCommand) {
                    		in1.add(new String[]{unSantizedUnQuoted(((ValueCommand)children.get(i)).value, state)});
                    	}
                    }
                }
            } else {
                //excep
            }
        }
        private static final java.util.Set<String> CURL_METHODS = java.util.Set.of("get", "post", "put", "delete", "patch", "head", "options");

        /** Q2: {@code curl <method> <url> {…}} is the plugin form; anything else after "curl" is a raw cURL command line. */
        static boolean isRawCurl(String firstArg) {
            return !CURL_METHODS.contains(firstArg.toLowerCase(java.util.Locale.ROOT));
        }

        /**
         * Q2: a raw cURL command line (as copied from browser dev tools or a GATF report) on one seleasy line, e.g.
         * {@code curl 'https://x/api' -H 'Accept: application/json' --data-raw '{"a":1}'}. It is converted here, at
         * transpile time, into the plugin's method/url/headers/body arguments, so it runs exactly like the block form.
         * Anything the plugin cannot send (multipart, body files, options GATF does not reproduce) fails the parse.
         */
        private void rawCurl(String line, Object[] cmdDetails) {
            rawCurl = true;
            com.gatf.imports.CurlCommand.Parsed p = null;
            try {
                p = com.gatf.imports.CurlCommand.parseOne(line);
            } catch (IllegalArgumentException e) {
                throwError(cmdDetails, new RuntimeException("curl: " + e.getMessage()));
            }
            if (!p.issues.isEmpty()) {
                throwError(cmdDetails, new RuntimeException("curl: " + p.issues.get(0).construct + " — " + p.issues.get(0).reason));
            }
            com.gatf.imports.ApiRequest r = p.request;
            if (!r.formParts.isEmpty() || r.contentFile != null) {
                throwError(cmdDetails, new RuntimeException("curl: multipart (-F) and file bodies (@file) are not supported by the curl plugin — use an API test case"));
            }
            in.add(r.upperMethod().toLowerCase(java.util.Locale.ROOT));
            in.add(r.fullUrl);
            List<String> hdrs = new ArrayList<String>();
            for (Map.Entry<String, String> h : r.headers.entrySet()) {
                hdrs.add(h.getKey() + ": " + h.getValue());
            }
            in1.add(hdrs.toArray(new String[0]));
            if (r.body != null && !r.body.isEmpty()) {
                in1.add(new String[] { r.body });
            }
        }

        public void reconcile() {
            if (rawCurl) {
                if (!children.isEmpty()) {
                    throwError(fileLineDetails, new RuntimeException("curl: a raw cURL command line cannot have a { } block"));
                }
                return;
            }
            for (int i = 0; i < children.size(); i++) {
                if(children.get(i) instanceof ValueListCommand) {
                    ValueListCommand vlc = (ValueListCommand)children.get(i);
                    String[] tm = new String[vlc.children.size()-1];
                    int c = 0;
                    for (String vc : vlc.getValues()) {
                    	tm[c++] = unSantizedUnQuoted(vc, state);
                    }
                    in1.add(tm);
                } else {
                	if(children.get(i) instanceof ValueCommand) {
                		in1.add(new String[]{unSantizedUnQuoted(((ValueCommand)children.get(i)).value, state)});
                	}
                }
            }
        }
        public static String[] toSampleSelCmd() {
        	List<String> cmdl = new ArrayList<String>();
        	for (String name : plugins.keySet()) {
				String signature = plugins.get(name);
		        try
		        {
		            String[] parts = signature.split("@");
		            String clsname = parts[0].trim();

		            Class<?> cls = Class.forName(clsname);
		            Method meth = cls.getMethod("toSampleSelCmd", new Class[]{});
		            
		            String[] cmds = (String[])meth.invoke(null, new Object[] {});
		            cmdl.addAll(Arrays.asList(cmds));
		        }
		        catch (Exception e)
		        {
		        }
			}
            return cmdl.toArray(new String[cmdl.size()]);
        }
    }
