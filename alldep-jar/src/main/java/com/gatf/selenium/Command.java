/*
    Copyright 2013-2019, Sumeet Chhetri

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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
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

//https://www.autoitscript.com/site/autoit/
//http://sikulix.com/

public class Command {
	
    protected static class CommandState {
    	XPathFactory factory = XPathFactory.newInstance();
        boolean commentStart = false, codeStart = false, started = false;
        String basePath = "";
        String testcaseDir = "";
        int concExec = 0;
        int starts = 0;
        int ends = 0;
       
        int ifTimeoutNum = 1;
        int timeoutNum = 60;
        long timeoutSleepGranularity = 1000;
       
        int modeExecType = 0;
        boolean modeEnableGlobalTimeouts = true;
        boolean modeEnableConcurrentWebDriver = false;

        int NUMBER = 1;
        int NUMBER_COND = 1;
        int NUMBER_EXC = 1;
        int NUMBER_ST = 1;
        int NUMBER_AL = 1;
        int NUMBER_RD = 1;
        int NUMBER_TH = 1;
        int loopCounter = 0;

        int NUMBER_SC = 1;
        Stack<String> stck = new Stack<String>();
        String sc = null, psc = null;

        int NUMBER_PARC = 1;
        Stack<String> stckPar = new Stack<String>();
        String parc = null, pparc = null;

        int NUMBER_ITER = 1;
        Stack<String> stckIter = new Stack<String>();
        String itr = null, pitr = null;

        int NUMBER_ICNTXT = 1;
        Stack<String> stckifcnt = new Stack<String>();
        String ifcnt = null, pifcnt = null;

        List<Object[]> subtestDetails = new ArrayList<Object[]>();
        Set<String> subtestDups = new HashSet<String>();
        List<Command> allSubTests = new ArrayList<Command>();
        Set<String> layers = new HashSet<String>();
        
        String layerStr = null;
        
        Properties configProps = null;
        Map<String, String> dynProps = new HashMap<String, String>();
        Map<String, String> aliases = new HashMap<>();
        
        String getLayers() {
            if(layerStr==null) {
                StringBuilder b = new StringBuilder();
                for (String s : layers) {
                    b.append("evaluate(\"" + esc(s) + "\")");
                    b.append(",");
                }
                if(b.length()>0 && b.charAt(b.length()-1)==',') {
                   b.deleteCharAt(b.length()-1);
                } else {
                    b.append("\"\"");
                }
                layerStr = b.toString();
            }
            return layerStr;
        }
        
        void addSubtest(SubTestCommand st) {
            if(subtestDups.contains(st.name)) {
                throwError(st.fileLineDetails, new RuntimeException("Duplicate "+(st.isAnAlias?"alias":"subtest")+" defined"));
            }
            subtestDups.add(st.name);
            allSubTests.add(st);
            subtestDetails.add(new Object[]{st.name, st.sessionName, st.sessionId+"", st.fileLineDetails, st.fName, true, st.isAnAlias});
        }
        
        void addSubtest(ExecSubTestCommand st) {
            subtestDetails.add(new Object[]{st.subt.name, st.sessionName, st.sessionId+"", st.fileLineDetails, st.subt.fName, false, st.subt.isAnAlias});
        }
        Map<String, String> qss = new HashMap<String, String>();
        Set<String> visitedFiles = new HashSet<String>();

        String varnamerandom() {
            return "___vd___" + NUMBER_RD++;
        }

        String alvarname() {
            return "___a___" + NUMBER_AL++;
        }

        String varname() {
            return "___w___" + NUMBER++;
        }

        String currvarname() {
            return "___w___" + (NUMBER-1);
        }

        String evarname() {
            return "___e___" + NUMBER_EXC++;
        }

        String currvarnamesc() {
            return sc;
        }

        String thisat() {
            return "___th___" + NUMBER_TH++;
        }

        String currthisat() {
            return "___th___" + (NUMBER_TH-1);
        }

        String prevvarnamesc() {
            stck.pop();
            sc = stck.size()==1?stck.peek():psc;
            return psc;
        }

        void pushSc() {
            psc = sc;
            sc = "___sc___" + NUMBER_SC++;
            stck.push(sc);
        }

        String currvarnameparc() {
            return parc;
        }

        String prevvarnameparc() {
            stckPar.pop();
            parc = stckPar.size()==1?stckPar.peek():pparc;
            return pparc;
        }

        void pushParc() {
            pparc = parc;
            parc = "___pc___" + NUMBER_PARC++;
            stckPar.push(parc);
        }

        String currvarnameifcnt() {
            return ifcnt;
        }

        String prevvarnameifcnt() {
            stckifcnt.pop();
            ifcnt = stckifcnt.size()==1?stckifcnt.peek():pifcnt;
            return pifcnt;
        }

        void pushifcnt() {
            pifcnt = ifcnt;
            ifcnt = "___ifcnt___" + NUMBER_ICNTXT++;
            stckifcnt.push(ifcnt);
        }

        String condvarname() {
            return "___c___" + NUMBER_COND++;
        }

        String currcondvarname() {
            return "___c___" + (NUMBER_COND-1);
        }

        String currvarnameitr() {
            return itr;
        }

        String prevvarnameitr() {
            stckIter.pop();
            itr = stckIter.size()==1?stckIter.peek():pitr;
            return pitr;
        }

        void pushItr() {
            pitr = itr;
            itr = "___itr___" + NUMBER_ITER++;
            stckIter.push(itr);
        }

        String unsanitize(String val) {
            if(val.trim().isEmpty())return val;
            for (String qs : qss.keySet())
            {
                val = val.replace(qss.get(qs), qs);
            }
            for (String qs : qss.keySet())
            {
                val = val.replace(qss.get(qs), qs);
            }
            return val;
        }

        String sanitize(String cmd) {
            Pattern p = Pattern.compile("\"([^\"]*)\"");
            Matcher m = p.matcher(cmd);
            while (m.find()) {
                if(!qss.containsKey(m.group())) {
                	String repl = "######" + UUID.randomUUID().toString() + "######";
                    qss.put(m.group(), repl);
                    cmd = cmd.replace(m.group(), repl);
                } else {
                	cmd = cmd.replace(m.group(), qss.get(m.group()));
                }
            }
            /*for (String qs : qss.keySet())
            {
                cmd = cmd.replace(qs, qss.get(qs));
            }*/
            p = Pattern.compile("'([^']*)'");
            m = p.matcher(cmd);
            while (m.find()) {
                //if(!qss.containsKey(m.group())) {
                //    qss.put(m.group(), "######" + UUID.randomUUID().toString() + "######");
                //}
                if(!qss.containsKey(m.group())) {
                	String repl = "######" + UUID.randomUUID().toString() + "######";
                    qss.put(m.group(), repl);
                    cmd = cmd.replace(m.group(), repl);
                } else {
                	cmd = cmd.replace(m.group(), qss.get(m.group()));
                }
            }
            /*for (String qs : qss.keySet())
            {
                cmd = cmd.replace(qs, qss.get(qs));
            }*/
            return cmd;
        }
    }

    protected boolean isTop = false;
    protected static Map<String, String> plugins = new HashMap<String, String>();
    protected CommandState state;
    protected String name;
    protected Object[] fileLineDetails;
    protected Map<String, SeleniumDriverConfig> mp;
    protected String className = "STC_" + System.nanoTime() + "";
    protected List<Command> children = new ArrayList<Command>();

    static Pattern p = Pattern.compile("\"([^\"]*)\"");
    static Pattern WAIT = Pattern.compile("^\\?\\?([0-9]*)");
    static Pattern WAIT_IF = Pattern.compile("^\\?\\?(\\+|\\-)([0-9]*)");
    static Pattern CONCNUM = Pattern.compile("^\\^[\t ]*([0-9]+)");

    Command(Object[] fileLineDetails, CommandState state) {
        this.fileLineDetails = fileLineDetails;
        this.state = state;
    }

    public String getClassName() {
        return className;
    }

    @SuppressWarnings("serial")
    public static class GatfSelCodeParseError extends GatfRunTimeError {
    	Object[] details;
        public GatfSelCodeParseError(String message, Object[] o) {
            super(message);
            details = new Object[] {o[0], o[1], o[5]};
        }
        public GatfSelCodeParseError(String message, Object[] o, Throwable e) {
            super(message, e);
            details = new Object[] {o[0], o[1], o[5]};
        }
        public Object[] getDetails() {
        	return details;
        }
    }

    void throwParseError(Object[] o) {
        throwParseError(o, null);
    }

    void throwParseError(Object[] o, Throwable e) {
        if(e!=null) {
            if(o!=null) {
                throw new GatfSelCodeParseError("Error parsing command at line "+o[1]+" in file "+o[2]+" ("+o[0]+")", o, e);
            }
            throw new GatfSelCodeParseError("Error parsing command at line "+fileLineDetails[1]+" in file "+fileLineDetails[2]+" ("+fileLineDetails[0]+")", o, e);
        }
        if(o!=null) {
            throw new GatfSelCodeParseError("Error parsing command at line "+o[1]+" in file "+o[2]+" ("+o[0]+")", o);
        }
        throw new GatfSelCodeParseError("Error parsing command at line "+fileLineDetails[1]+" in file "+fileLineDetails[2]+" ("+fileLineDetails[0]+")", o);
    }

    static void throwParseErrorS(Object[] o, Throwable e) {
        if(e!=null) {
            throw new GatfSelCodeParseError("Error parsing command at line "+o[1]+" in file "+o[2]+" ("+o[0]+")", o, e);
        }
        throw new GatfSelCodeParseError("Error parsing command at line "+o[1]+" in file "+o[2]+" ("+o[0]+")", o);
    }

    static void throwError(Object[] o, Throwable e) {
        if(e!=null) {
            throw new GatfSelCodeParseError("Error at line "+o[1]+" in file "+o[2]+" ("+o[0]+")", o, e);
        }
        throw new GatfSelCodeParseError("Error at line "+o[1]+" in file "+o[2]+" ("+o[0]+")", o);
    }

    int weight() {
        return 100;
    }

    static Command parse(Object[] cmdDetails, CommandState state, Command parent) {
        String cmd = cmdDetails[0].toString().trim();
        Command comd = null;
        if(cmd.startsWith("??1000")) {
        	System.out.println();
        }
        cmd = state.sanitize(cmd);
        if(parent instanceof ValueListCommand && !cmd.trim().equals("]")) {
    		comd = new ValueCommand(cmdDetails, state);
            if(cmd.charAt(0)==cmd.charAt(cmd.length()-1)) {
                if(cmd.charAt(0)=='"' || cmd.charAt(0)=='\'') {
                    cmd = cmd.substring(1, cmd.length()-1);
                }
            }
            ((ValueCommand)comd).value = state.unsanitize(cmd);
    	} else if(state.commentStart && !cmd.contains("*/")) {
            comd = new ValueCommand(cmdDetails, state);
            ((ValueCommand)comd).value = cmd;
        } else if(state.codeStart && !cmd.equals(">>>")) {
            comd = new ValueCommand(cmdDetails, state);
            ((ValueCommand)comd).value = cmd;
        } else if(cmd.startsWith("??+")) {
        	String time = state.timeoutNum+"";
            Matcher m = WAIT_IF.matcher(cmd);
            int start = 3;
            if(m.find()) {
                time = m.group(2);
                start = m.end(2) + 1;
            }
            cmd = cmd.substring(start).trim();
            comd = new WaitTillElementVisibleOrInvisibleCommand(time, cmd, cmdDetails, state, true, false);
        } else if(cmd.startsWith("??-")) {
        	String time = state.timeoutNum+"";
        	Matcher m = WAIT_IF.matcher(cmd);
        	int start = 3;
        	if(m.find()) {
        		time = m.group(2);
        		start = m.end(2) + 1;
        	}
        	cmd = cmd.substring(start).trim();
        	comd = new WaitTillElementVisibleOrInvisibleCommand(time, cmd, cmdDetails, state, false, false);
        } else if(cmd.startsWith("??")) {
        	String time = state.timeoutNum+"";
        	Matcher m = WAIT.matcher(cmd);
        	int start = 2;
        	if(m.find()) {
        		time = m.group(1);
        		start = m.end(1) + 1;
        	}
        	cmd = cmd.substring(start).trim();
        	comd = new WaitTillElementVisibleOrInvisibleCommand(time, cmd, cmdDetails, state, false, false);
        } else if (cmd.startsWith("//")) {
            cmd = cmd.substring(2);
            comd = new CommentCommand(cmd, cmdDetails, state);
        } else if (cmd.startsWith("/*")) {
            cmd = cmd.substring(2);
            comd = new MultiCommentCommand(cmdDetails, state);
            ((MultiCommentCommand)comd).b.append(state.unsanitize(cmd)+"\n");
            state.commentStart = true;
        } else if (cmd.contains("*/")) {
            state.commentStart = false;
            if(!cmd.endsWith("*/")) {
                //exception
            }
            cmd = cmd.substring(0, cmd.length()-2);
            comd = new EndMultiCommentCommand(cmd, cmdDetails, state);
        } else if (cmd.equals("<<<") || cmd.startsWith("<<<(")) {
            String lang = "java";
            String argnames = "";
            if(cmd.startsWith("<<<(") && cmd.indexOf(")", cmd.indexOf("<<<("))!=-1) {
                lang = cmd.substring(4, cmd.indexOf(")"));
                if(cmd.length()>cmd.indexOf(")")+1) {
                    argnames = cmd.substring(cmd.indexOf(")")+1).trim();
                }
            }
            comd = new CodeCommand(lang, argnames, cmdDetails, state);
            state.codeStart = true;
        } else if (cmd.equals(">>>")) {
            state.codeStart = false;
            comd = new EndCodeCommand(cmdDetails, state);
        }/* else if (cmd.startsWith("^")) {
            Matcher m = CONCNUM.matcher(cmd);
            concExec = Runtime.getRuntime().availableProcessors();
            if(m.find()) {
                try
                {
                    concExec = Integer.parseInt(m.group(1));
                }
                catch (Exception e)
                {
                }
            }
        }*/ else if (cmd.startsWith("?") || cmd.startsWith("?!")) {
            boolean isIfNot = cmd.startsWith("?!");
            cmd = cmd.substring((isIfNot?2:1)).trim();
            comd = new IfCommand(cmd, isIfNot, cmdDetails, state);
            //((IfCommand)comd).cond = new FindCommand(cmd, cmdDetails, state);
            //((IfCommand)comd).cond.suppressErr = true;
        } else if (cmd.startsWith(":?") || cmd.startsWith(":?!")) {
            boolean isIfNot = cmd.startsWith(":?!");
            cmd = cmd.substring((isIfNot?3:2)).trim();
            comd = new ElseIfCommand(cmd, isIfNot, cmdDetails, state);
            //((ElseIfCommand)comd).cond = new FindCommand(cmd, cmdDetails, state);
            //((ElseIfCommand)comd).cond.suppressErr = true;
        } else if (cmd.startsWith(":")) {
            cmd = cmd.substring(1).trim();
            comd = new ElseCommand(cmdDetails, state);
        } else if (cmd.startsWith("#j")) {
            cmd = cmd.substring(2).trim();
            if(!cmd.matches("(if|try|catch|finally|else|else if|while|for|continue|break|\\{|\\}|synchronized).*")) {
            	throwParseErrorS(cmdDetails, new RuntimeException("Only following java control statements allowed - if|try|catch|finally|else|else if|while|for|continue|break|\\{|\\}|synchronized"));
            }
            comd = new JavaControlCommand(cmd, cmdDetails, state);
        } else if (cmd.startsWith("#sql ")) {
            cmd = cmd.substring(5).trim();
            if(cmd.isEmpty()) {
            	throwParseErrorS(cmdDetails, new RuntimeException("Provider details required"));
            }
            comd = new ProviderLoopCommand(cmd.trim(), cmdDetails, state, false, ProviderType.InlineSQL);
        } else if (cmd.startsWith("#mongo ")) {
            cmd = cmd.substring(7).trim();
            if(cmd.isEmpty()) {
            	throwParseErrorS(cmdDetails, new RuntimeException("Provider details required"));
            }
            comd = new ProviderLoopCommand(cmd.trim(), cmdDetails, state, false, ProviderType.InlineMongo);
        } else if (cmd.startsWith("#file ")) {
            cmd = cmd.substring(6).trim();
            if(cmd.isEmpty()) {
            	throwParseErrorS(cmdDetails, new RuntimeException("Provider details required"));
            }
            comd = new ProviderLoopCommand(cmd.trim(), cmdDetails, state, false, ProviderType.InlineFile);
        } else if (cmd.startsWith("#provider ")) {
            cmd = cmd.substring(10).trim();
            if(cmd.isEmpty()) {
            	throwParseErrorS(cmdDetails, new RuntimeException("Provider details required"));
            }
            comd = new ProviderLoopCommand(cmd.trim(), cmdDetails, state, false, ProviderType.Provided);
        } else if (cmd.startsWith("#p ")) {
            cmd = cmd.substring(3).trim();
            if(cmd.isEmpty()) {
            	throwParseErrorS(cmdDetails, new RuntimeException("Provider details required"));
            }
            comd = new ProviderLoopCommand(cmd.trim(), cmdDetails, state, false, ProviderType.Provided);
        } else if (cmd.startsWith("#provider-sf ")) {
            cmd = cmd.substring(13).trim();
            if(cmd.isEmpty()) {
            	throwParseErrorS(cmdDetails, new RuntimeException("Provider details required"));
            }
            comd = new ProviderLoopCommand(cmd.trim(), cmdDetails, state, true, ProviderType.Provided);
        } else if (cmd.startsWith("#p-sf ")) {
            cmd = cmd.substring(6).trim();
            if(cmd.isEmpty()) {
            	throwParseErrorS(cmdDetails, new RuntimeException("Provider details required"));
            }
            comd = new ProviderLoopCommand(cmd.trim(), cmdDetails, state, true, ProviderType.Provided);
        } else if (cmd.startsWith("#counter ")) {
            cmd = cmd.substring(9).trim();
            if(cmd.isEmpty()) {
            	throwParseErrorS(cmdDetails, new RuntimeException("Counter details required"));
            }
            comd = new ProviderLoopCommand(cmd.trim(), cmdDetails, state, false, ProviderType.Counter);
        } else if (cmd.startsWith("#c ")) {
            cmd = cmd.substring(3).trim();
            if(cmd.isEmpty()) {
            	throwParseErrorS(cmdDetails, new RuntimeException("Counter details required"));
            }
            comd = new ProviderLoopCommand(cmd.trim(), cmdDetails, state, false, ProviderType.Counter);
        } else if (cmd.startsWith("#transient-provider ")) {
            cmd = cmd.substring(19).trim();
            if(cmd.isEmpty()) {
            	throwParseErrorS(cmdDetails, new RuntimeException("Provider details required"));
            }
            comd = new TransientProviderCommand(cmd.trim(), cmdDetails, state);
        } else if (cmd.startsWith("#tp ")) {
            cmd = cmd.substring(4).trim();
            if(cmd.isEmpty()) {
                //exception
            }
            comd = new TransientProviderCommand(cmd.trim(), cmdDetails, state);
        } /*else if (cmd.startsWith("#transient-suite-provider ")) {
            cmd = cmd.substring(25).trim();
            if(cmd.isEmpty()) {
            	throwParseErrorS(cmdDetails, new RuntimeException("Provider details required"));
            }
            comd = new TransientProviderCommand(cmd.trim(), cmdDetails, state, true);
        } else if (cmd.startsWith("#tsp ")) {
            cmd = cmd.substring(4).trim();
            if(cmd.isEmpty()) {
            	throwParseErrorS(cmdDetails, new RuntimeException("Provider details required"));
            }
            comd = new TransientProviderCommand(cmd.trim(), cmdDetails, state, true);
        }*/ else if (cmd.startsWith("## ")) {
            cmd = cmd.substring(3).trim();
            FindCommand cond = new FindCommand(cmd, cmdDetails, state);
            comd = new ScopedLoopCommand(cmdDetails, state, cond);
        } else if (cmd.startsWith("# ")) {
            cmd = cmd.substring(2).trim();
            FindCommand cond = new FindCommand(cmd, cmdDetails, state);
            comd = new ScopedLoopCommand(cmdDetails, state, cond);
        } else if (cmd.startsWith("readfile ")) {
            comd = new ReadFileCommand(cmd.substring(9).trim(), cmdDetails, state);
        } else if (cmd.startsWith("filevar ")) {
            comd = new FileVarCommand(cmd.substring(8).trim(), cmdDetails, state);
        } else if (cmd.startsWith("dsq ")) {
            cmd = cmd.substring(4).trim();
            comd = new DSQueryCommand(cmd, cmdDetails, state);
        } else if (cmd.startsWith("[")) {
            comd = new ValueListCommand(cmdDetails, state);
            //((ValueListCommand)comd).type = "[";
        } else if (cmd.startsWith("]")) {
            comd = new EndValueListCommand(cmdDetails, state);
            ((EndValueListCommand)comd).type = "]";
        } else if (cmd.startsWith("{")) {
        	if(!cmd.trim().equals("{")) {
        		throwError(cmdDetails, new RuntimeException("Start command should not have any following statements"));
        	}
            comd = new StartCommand(cmdDetails, state);
            ((StartCommand)comd).type = "{";
        } else if (cmd.startsWith("}")) {
        	if(!cmd.trim().equals("}")) {
        		throwError(cmdDetails, new RuntimeException("End command should not have any following statements"));
        	}
            comd = new EndCommand(cmdDetails, state);
            ((EndCommand)comd).type = "}";
        } else if (cmd.startsWith("pass ") || cmd.equalsIgnoreCase("pass")) {
            comd = new PassCommand(cmd.equalsIgnoreCase("pass")?"":cmd.substring(5).trim(), cmdDetails, state);
        } else if (cmd.startsWith("fail ") || cmd.equalsIgnoreCase("fail")) {
            comd = new FailCommand(cmd.equalsIgnoreCase("fail")?"":cmd.substring(5).trim(), cmdDetails, state);
        } else if (cmd.startsWith("warn ") || cmd.equalsIgnoreCase("warn")) {
            comd = new WarnCommand(cmd.equalsIgnoreCase("warn")?"":cmd.substring(5).trim(), cmdDetails, state);
        } else if (cmd.toLowerCase().startsWith("open ")) {
            String name = cmd.substring(5).trim();
            comd = new BrowserCommand(name, cmdDetails, state);
        } else if (cmd.toLowerCase().startsWith("mode ")) {
            String name = cmd.substring(5).trim();
            comd = new ModeCommand(name, cmdDetails, state);
        } /*else if (cmd.toLowerCase().startsWith("capability_set ")) {
            comd = new CapabilitySetPropertyCommand(cmd.substring(15));
        }*/ else if (cmd.toLowerCase().startsWith("goto ")) {
            String url = cmd.substring(5).trim();
            url = state.unsanitize(url);
            if(url.charAt(0)==url.charAt(url.length()-1)) {
                if(url.charAt(0)=='"' || url.charAt(0)=='\'') {
                    url = url.substring(1, url.length()-1);
                }
            }
            comd = new GotoCommand(cmdDetails, state);
            ((GotoCommand)comd).url = url;
        } else if (cmd.toLowerCase().startsWith("timeout ")) {
        	comd = new TimeoutCommand(cmd.substring(8), cmdDetails, state);
        	state.timeoutNum = ((TimeoutCommand)comd).counter;
        	state.ifTimeoutNum = ((TimeoutCommand)comd).ifCounter;
        	state.timeoutSleepGranularity = ((TimeoutCommand)comd).sleepGranularity;
        } else if (cmd.toLowerCase().startsWith("layer ")) {
            comd = new LayerCommand(cmd.substring(6).trim(), cmdDetails, state);
        } else if (cmd.toLowerCase().equals("break")) {
            comd = new BreakCommand(cmdDetails, state);
        } else if (cmd.toLowerCase().equals("continue")) {
            comd = new ContinueCommand(cmdDetails, state);
        } else if (cmd.toLowerCase().equals("back")) {
            comd = new BackCommand(cmdDetails, state);
        } else if (cmd.toLowerCase().equals("forward")) {
            comd = new ForwardCommand(cmdDetails, state);
        } else if (cmd.toLowerCase().equals("refresh")) {
            comd = new RefreshCommand(cmdDetails, state);
        } else if (cmd.toLowerCase().startsWith("close")) {
            comd = new CloseCommand(cmd.substring(5), cmdDetails, state);
        } else if (cmd.toLowerCase().startsWith("scroll ")) {
            comd = new ScrollCommand(cmd.substring(7).trim(), cmdDetails, state);
        } else if (cmd.toLowerCase().equals("waitready")) {
            comd = new WaitForReady(cmdDetails, state);
        } else if (cmd.toLowerCase().equals("maximize")) {
            comd = new MaximizeCommand(cmdDetails, state);
        } else if (cmd.toLowerCase().startsWith("setting ")) {
            comd = new SettingCommand(cmd.substring(8).trim(), cmdDetails, state);
        } else if (cmd.toLowerCase().startsWith("frame ")) {
            comd = new FrameCommand(cmd.substring(6).trim(), cmdDetails, state);
        } else if (cmd.toLowerCase().startsWith("tab ")) {
            comd = new TabCommand(cmd.substring(4).trim(), cmdDetails, state);
        } else if (cmd.toLowerCase().startsWith("window ")) {
            comd = new WindowCommand(cmd.substring(7).trim(), cmdDetails, state);
        } else if (cmd.toLowerCase().startsWith("frame ")) {
            comd = new FrameCommand(cmd.substring(6).trim(), cmdDetails, state);
        } else if (cmd.toLowerCase().startsWith("printpdf ")) {
            comd = new PrintPDFCommand(cmd.substring(9).trim(), cmdDetails, state);
        } else if (cmd.toLowerCase().startsWith("wopensave ")) {
            comd = new WindowOpenSaveInterceptJsCommand(cmd.substring(10).trim(), cmdDetails, state);
        } else if (cmd.toLowerCase().startsWith("netapix ")) {
            comd = new NetworkAPIInterceptCommand(cmd.substring(8).trim(), cmdDetails, state);
        } else if (cmd.toLowerCase().startsWith("sleep ")) {
            comd = new SleepCommand(cmd.substring(6).trim(), cmdDetails, state);
        } else if (cmd.toLowerCase().matches(typeSwStr) || cmd.toLowerCase().matches(typeExStr) ||cmd.toLowerCase().startsWith("select ") 
                || cmd.toLowerCase().matches(clickSwStr) || cmd.toLowerCase().matches(clickExStr)
                || cmd.toLowerCase().startsWith("doubleclick ") || cmd.toLowerCase().equals("doubleclick")
                || cmd.toLowerCase().startsWith("dblclick ") || cmd.toLowerCase().equals("dblclick")
                || cmd.toLowerCase().startsWith("hover ") || cmd.toLowerCase().equals("hover")
                || cmd.toLowerCase().startsWith("actions ") || cmd.toLowerCase().startsWith("hoverclick ") 
                || cmd.toLowerCase().startsWith("clear ") || cmd.toLowerCase().equals("clear")
                || cmd.toLowerCase().startsWith("submit ") || cmd.toLowerCase().equals("submit")
                || cmd.toLowerCase().startsWith("upload ")) {
        	if(state.modeEnableGlobalTimeouts) {
        		comd = new WaitTillElementVisibleOrInvisibleCommand(state.timeoutNum+"", cmd, cmdDetails, state, false, true);
        	} else {
        		comd = handleActions(cmd, null, cmdDetails, state);
        	}
        } else if(cmd.toLowerCase().startsWith("robot ") ||  cmd.toLowerCase().equals("scrollup") 
                || cmd.toLowerCase().equals("scrolldown") || cmd.toLowerCase().equals("scrollpageup") 
                || cmd.toLowerCase().equals("scrollpagedown")) {
        	comd = new RobotCommand(cmd.substring(cmd.toLowerCase().startsWith("robot ")?6:0), cmdDetails, state);
        } else if (cmd.toLowerCase().startsWith("var ")) {
            comd = new VarCommand(cmd.substring(4).trim(), cmdDetails, state);
        } else if (cmd.toLowerCase().startsWith("jsvar ")) {
            comd = new JsVarCommand(cmd.substring(6).trim(), cmdDetails, state);
        } else if (cmd.toLowerCase().startsWith("exec ")) {
            comd = new ExecCommand(cmd.substring(5).trim(), cmdDetails, state);
        } else if (cmd.toLowerCase().startsWith("execjs ")) {
            comd = new ExecJsCommand(cmd.substring(7).trim(), cmdDetails, state);
        } else if (cmd.toLowerCase().startsWith("execjsfile ")) {
            comd = new ExecJsFileCommand(cmd.substring(11).trim(), cmdDetails, state);
        } else if (cmd.toLowerCase().startsWith("canvas ")) {
            comd = new CanvasCommand(cmd.substring(7).trim(), cmdDetails, state);
        } else if (cmd.toLowerCase().startsWith("subtest ")) {
            comd = new SubTestCommand(cmd.substring(8).trim(), cmdDetails, state, false);
        } else if (cmd.toLowerCase().startsWith("alias ")) {
            comd = new SubTestCommand(cmd.substring(6).trim(), cmdDetails, state, true);
        } else if (cmd.toLowerCase().startsWith("@call ")) {
            comd = new ExecSubTestCommand(cmd.substring(6).trim(), cmdDetails, state);
        } /*else if (cmd.toLowerCase().startsWith("@")) {
            comd = new ExecSubTestCommand(cmd.substring(1).trim(), cmdDetails, state, true);
        }*/ else if (cmd.toLowerCase().startsWith("require ")) {
            comd = new RequireCommand(cmd.substring(8).trim(), cmdDetails, state);
        } else if (cmd.toLowerCase().startsWith("import ")) {
            comd = new ImportCommand(cmd.substring(7).trim(), cmdDetails, state);
        } else if (cmd.toLowerCase().startsWith("include ")) {
            comd = new IncludeCommand(cmd.substring(8).trim(), cmdDetails, state);
        } else if (cmd.toLowerCase().startsWith("config ")) {
            comd = new ConfigPropsCommand(cmd.substring(7).trim(), cmdDetails, state);
        } else if (cmd.toLowerCase().startsWith("dynprops ")) {
            comd = new DynPropsCommand(cmd.substring(9).trim(), cmdDetails, state);
        } else if (cmd.toLowerCase().startsWith("screenshot")) {
            comd = new ScreenshotCommand(cmd.substring(10).trim(), cmdDetails, state, false);
        } else if (cmd.toLowerCase().startsWith("ele-screenshot ")) {
            comd = new EleScreenshotCommand(cmd.substring(15).trim(), cmdDetails, state, false);
        } else if(cmd.toLowerCase().startsWith("alert ")) {
            comd = new AlertCommand(cmd.substring(6).trim(), cmdDetails, state);
        } else if(cmd.toLowerCase().startsWith("alert")) {
            comd = new AlertCommand("", cmdDetails, state);
        } else if(cmd.toLowerCase().startsWith("confirm ")) {
            comd = new ConfirmCommand(cmd.substring(8).trim(), cmdDetails, state);
        } else if(cmd.toLowerCase().startsWith("confirm")) {
            comd = new ConfirmCommand("", cmdDetails, state);
        } else if (cmd.toLowerCase().startsWith("zoom ") || cmd.toLowerCase().startsWith("pinch ") 
                || cmd.toLowerCase().startsWith("tap ") || cmd.toLowerCase().equals("rotate")
                || cmd.toLowerCase().equals("hidekeypad") || cmd.toLowerCase().startsWith("touch ")
                || cmd.toLowerCase().startsWith("swipe ") || cmd.toLowerCase().equals("shake")) {
            if(cmd.toLowerCase().startsWith("zoom ")) {
                comd = new MZoomCommand(cmd.substring(5).trim(), cmdDetails, state);
            } else if(cmd.toLowerCase().startsWith("pinch ")) {
                comd = new MPinchCommand(cmd.substring(6).trim(), cmdDetails, state);
            } else if(cmd.toLowerCase().startsWith("tap ")) {
                comd = new MTapCommand(cmd.substring(4).trim(), cmdDetails, state);
            } else if(cmd.toLowerCase().trim().equals("rotate")) {
                comd = new MRotateCommand(cmdDetails, state);
            } else if(cmd.toLowerCase().trim().equals("hidekeypad")) {
                comd = new MHideKeyPadCommand(cmdDetails, state);
            } else if(cmd.toLowerCase().startsWith("touch ")) {
                comd = new MTouchActionCommand(cmd.substring(6).trim(), cmdDetails, state);
            } else if(cmd.toLowerCase().startsWith("swipe ")) {
                comd = new MSwipeCommand(cmd.substring(6), cmdDetails, state);
            } else if(cmd.toLowerCase().trim().equals("shake")) {
                comd = new MShakeCommand(cmdDetails, state);
            }
        } else if (cmd.trim().isEmpty()) {
            comd = new NoopCommand(cmdDetails, state);
        } else {
            try {
                comd = new PluginCommand(cmd, cmdDetails, state);
            } catch (GatfSelCodeParseError e) {
            	if(parent instanceof PluginCommand) {
	                comd = new ValueCommand(cmdDetails, state);
	                if(cmd.charAt(0)==cmd.charAt(cmd.length()-1)) {
	                    if(cmd.charAt(0)=='"' || cmd.charAt(0)=='\'') {
	                        cmd = cmd.substring(1, cmd.length()-1);
	                    }
	                }
	                ((ValueCommand)comd).value = state.unsanitize(cmd);
            	} else if(parent instanceof VarCommand && ((VarCommand)parent).pcomd!=null) {
	                comd = new ValueCommand(cmdDetails, state);
	                if(cmd.charAt(0)==cmd.charAt(cmd.length()-1)) {
	                    if(cmd.charAt(0)=='"' || cmd.charAt(0)=='\'') {
	                        cmd = cmd.substring(1, cmd.length()-1);
	                    }
	                }
	                ((ValueCommand)comd).value = state.unsanitize(cmd);
            	} else {
            		throwError(cmdDetails, new RuntimeException("Invalid command found ["+state.unsanitize(cmd)+"]"));
            	}
            }
        }
        return comd;
    }

    protected static String typeExStr = "^(type|sendkeys|chord|randomize)(bl|ch|bk|cl|fo)?";
    protected static String typeSwStr = "^(type|sendkeys|chord|randomize)(bl|ch|bk|cl|fo)?\\s+(.*)";
    protected static Pattern typeEx = Pattern.compile(typeExStr);
    protected static Pattern typeSw = Pattern.compile(typeSwStr);
    
    protected static String clickExStr = "^click(fo|nf)?";
    protected static String clickSwStr = "^click(fo|nf)?\\s+(.*)";
    protected static Pattern clickEx = Pattern.compile(clickExStr);
    protected static Pattern clickSw = Pattern.compile(clickSwStr);
    
    static Command handleActions(String cmd, FindCommand fcmd, Object[] cmdDetails, CommandState state) {
        Command comd = null;
        /*if (cmd.toLowerCase().startsWith("type ")) {
            comd = new TypeCommand(cmd.substring(5), cmdDetails, state, false);
        } else*/ if (cmd.toLowerCase().matches(typeSwStr)) {
        	Matcher m = typeSw.matcher(cmd.toLowerCase());
        	m.matches();
        	String type = m.group(1);
        	String qualifier = m.group(2);
        	cmd = m.group(3);
        	//cmd = state.unsanitize(cmd);
            comd = new TypeCommand(cmd, cmdDetails, state, fcmd, type, qualifier);
        } /*else if (cmd.toLowerCase().startsWith("sendkeys ")) {
            comd = new TypeCommand(cmd.substring(9), cmdDetails, state, false);
        } else if (cmd.toLowerCase().startsWith("sendkeysnb ")) {
            comd = new TypeCommand(cmd.substring(11), cmdDetails, state, true);
        }*/ else if (cmd.toLowerCase().startsWith("upload ")) {
            comd = new UploadCommand(cmd.substring(7), cmdDetails, state);
        }/* else if (cmd.toLowerCase().startsWith("randomize ")) {
            comd = new RandomizeCommand(cmd.substring(10), cmdDetails, state, fcmd, false);
        } else if (cmd.toLowerCase().startsWith("randomizenb ")) {
            comd = new RandomizeCommand(cmd.substring(12), cmdDetails, state, fcmd, true);
        } else if (cmd.toLowerCase().startsWith("chord ")) {
            comd = new ChordCommand(cmd.substring(6), cmdDetails, state, false);
        } else if (cmd.toLowerCase().startsWith("chordnb ")) {
            comd = new ChordCommand(cmd.substring(8), cmdDetails, state, false);
        }*/ else if (cmd.toLowerCase().startsWith("select ")) {
            comd = new SelectCommand(cmd.substring(7), cmdDetails, state);
        } else if (cmd.toLowerCase().matches(clickSwStr)) {
        	Matcher m = clickSw.matcher(cmd.toLowerCase());
        	m.matches();
        	String qualifier = m.group(1);
        	cmd = m.group(2);
            comd = new ClickCommand(cmdDetails, state, qualifier);
            if(!cmd.trim().isEmpty()) {
                ((ClickCommand)comd).cond = new FindCommand(cmd, cmdDetails, state);
            }
        } else if (cmd.toLowerCase().matches(clickExStr)) {
        	Matcher m = clickEx.matcher(cmd.toLowerCase());
        	m.matches();
        	String qualifier = m.group(1);
            comd = new ClickCommand(cmdDetails, state, qualifier);
        } else if (cmd.toLowerCase().startsWith("doubleclick ") || cmd.toLowerCase().equals("doubleclick")) {
            comd = new DoubleClickCommand(cmdDetails, state);
            if(!cmd.toLowerCase().equals("doubleclick")) {
                ((DoubleClickCommand)comd).cond = new FindCommand(cmd.substring(12), cmdDetails, state);
            }
        } else if (cmd.toLowerCase().startsWith("dblclick ") || cmd.toLowerCase().equals("dblclick")) {
            comd = new DoubleClickCommand(cmdDetails, state);
            if(!cmd.toLowerCase().equals("dblclick")) {
                ((DoubleClickCommand)comd).cond = new FindCommand(cmd.substring(9), cmdDetails, state);
            }
        } else if (cmd.toLowerCase().startsWith("hover ") || cmd.toLowerCase().equals("hover")) {
            comd = new HoverCommand(cmdDetails, state);
            if(!cmd.toLowerCase().equals("hover")) {
                ((HoverCommand)comd).cond = new FindCommand(cmd.substring(6), cmdDetails, state);
            }
        } else if (cmd.toLowerCase().startsWith("hoverclick ")) {
            comd = new HoverAndClickCommand(cmd.substring(11), cmdDetails, state);
        } else if (cmd.toLowerCase().startsWith("clear ") || cmd.toLowerCase().equals("clear")) {
            comd = new ClearCommand(cmdDetails, state);
            if(!cmd.toLowerCase().equals("clear")) {
                ((ClearCommand)comd).cond = new FindCommand(cmd.substring(6), cmdDetails, state);
            }
        } else if (cmd.toLowerCase().startsWith("submit ") || cmd.toLowerCase().equals("submit")) {
            comd = new SubmitCommand(cmdDetails, state);
            if(!cmd.toLowerCase().equals("submit")) {
                ((SubmitCommand)comd).cond = new FindCommand(cmd.substring(7), cmdDetails, state);
            }
        } else if (cmd.toLowerCase().startsWith("actions ")) {
            comd = new ActionsCommand(cmd.substring(7), fcmd, cmdDetails, state);
        } else if (cmd.toLowerCase().startsWith("robot ") ||  cmd.toLowerCase().equals("scrollup") 
                || cmd.toLowerCase().equals("scrolldown") || cmd.toLowerCase().equals("scrollpageup") 
                || cmd.toLowerCase().equals("scrollpagedown")) {
            comd = new RobotCommand(cmd.substring(cmd.toLowerCase().startsWith("robot ")?6:0), cmdDetails, state);
        }
        return comd;
    }
    
    static ImmutablePair<Command, Object[]> nextCommand(Command parent, ListIterator<Object[]> iter, CommandState state) {
    	Command tmp = null;
        Object[] o = iter.next();
        
    	for (String dkey : state.dynProps.keySet()) {
			o[0] = o[0].toString().replace("!"+dkey+"!", state.dynProps.get(dkey));
		}

        try
        {
            tmp = parse(o, state, parent);
            /*if(o[0].toString().trim().startsWith("alias \"pre1\"")) {
            	System.out.println();
            }*/
        }
        catch (GatfSelCodeParseError e)
        {
            throw e;
        }
        catch (Throwable e)
        {
            throwParseErrorS(o, e);
        }
        return new ImmutablePair<Command, Object[]>(tmp, o);
    }

    static void get(Command parent, ListIterator<Object[]> iter, CommandState state) throws Exception {
        //Command prev = null;
        while(iter.hasNext()) {
        	ImmutablePair<Command, Object[]> ip = nextCommand(parent, iter, state);
            Command tmp = ip.left;
            Object[] o = ip.right;
            
        	/*for (String dkey : state.dynProps.keySet()) {
				o[0] = o[0].toString().replace("!"+dkey+"!", state.dynProps.get(dkey));
			}

            try
            {
                tmp = parse(o, state);
                if(o[0].toString().trim().startsWith("var @firstname")) {
                	System.out.println();
                }
            }
            catch (GatfSelCodeParseError e)
            {
                throw e;
            }
            catch (Throwable e)
            {
                throwParseErrorS(o, e);
            }*/
            
            if(parent instanceof SubTestCommand && tmp instanceof SubTestCommand) {
            	throwParseErrorS(o, new RuntimeException("Nested subtests/aliases not allowed"));
            }
            if(tmp instanceof SubTestCommand) {
            	if(!parent.isTop) {
                	throwParseErrorS(o, new RuntimeException("subtests/aliases are allowed only at the top level"));
                }
            }
            if(parent instanceof FrameCommand && tmp instanceof FrameCommand) {
            	//throwParseErrorS(o, new RuntimeException("Nested frame scopes not allowed"));
            }
            if(parent instanceof TabCommand && tmp instanceof TabCommand) {
            	//throwParseErrorS(o, new RuntimeException("Nested tab scopes not allowed"));
            }
            if(parent instanceof WindowCommand && tmp instanceof WindowCommand) {
            	//throwParseErrorS(o, new RuntimeException("Nested window scopes not allowed"));
            }
            if(parent instanceof ValueListCommand) {
            	if(!(tmp instanceof ValueCommand) && !(tmp instanceof EndValueListCommand)) {
            		throwParseErrorS(o, new RuntimeException("ValueList command can only contain values"));
            	}
            }

            if(tmp instanceof IfCommand || tmp instanceof ElseCommand || tmp instanceof ElseIfCommand 
                    || tmp instanceof ProviderLoopCommand || tmp instanceof SubTestCommand || tmp instanceof ScopedLoopCommand
                    || tmp instanceof ReadFileCommand || tmp instanceof TransientProviderCommand || tmp instanceof PluginCommand
                    || tmp instanceof VarCommand || tmp instanceof FrameCommand|| tmp instanceof TabCommand|| tmp instanceof WindowCommand) {
            	ImmutablePair<Command, Object[]> ipn = nextCommand(tmp, iter, state);
                Command tmpn = ipn.left;
                Object[] on = ipn.right;
            	if(!(tmpn instanceof StartCommand) && !(tmp instanceof VarCommand) && !(tmp instanceof FrameCommand) && !(tmp instanceof PluginCommand) 
            			&& !(tmp instanceof TabCommand) && !(tmp instanceof WindowCommand) && (!(tmp instanceof TransientProviderCommand) 
            			|| (tmp instanceof TransientProviderCommand && !((TransientProviderCommand)tmp).isLazy))) {
            		boolean err = true;
                	if(tmp instanceof IfCommand || tmp instanceof ElseCommand) {
                		if(tmpn instanceof ValueListCommand) {
                			get(tmpn, iter, state);
                			if(tmpn.children.size()==0 || !(tmpn.children.get(tmpn.children.size()-1) instanceof EndValueListCommand)) {
                            	throwParseErrorS(o, new RuntimeException("ValueList command should close with a EndCommand `]`"));
                            }
                			ipn = nextCommand(tmp, iter, state);
                			Command tmpn1 = ipn.left;
                            err = !(tmpn1 instanceof StartCommand);
                            if(tmpn1 instanceof StartCommand) {
                            	tmp.children.add(tmpn);
                            	tmpn = tmpn1;
                            }
                		}
                	}
                	if(err) {
                		throwError(on, new RuntimeException(tmp.getClass().getName().replace("com.gatf.selenium.Command$", "") + " should have a proceeding block"));
                	}
                	
                	parent.children.add(tmp);
                	if(tmpn instanceof StartCommand) {
    	        		get(tmp, iter, state);
    	        		tmp.children.remove(tmp.children.size()-1);
    	        		state.ends--;
                	} else {
                		iter.previous();
    	        		get(parent, iter, state);
                	}
                	
                	if(tmp instanceof PluginCommand) {
                        ((PluginCommand)tmp).reconcile();
                    } else if(tmp instanceof VarCommand) {
                    	((VarCommand)tmp).reconcile();
                    }
        		} else if(!(tmpn instanceof StartCommand) && (tmp instanceof TransientProviderCommand || tmp instanceof PluginCommand
                    || tmp instanceof VarCommand || tmp instanceof FrameCommand|| tmp instanceof TabCommand|| tmp instanceof WindowCommand)) {
        			iter.previous();
                	parent.children.add(tmp);
                	
                	if(tmp instanceof PluginCommand) {
                        ((PluginCommand)tmp).reconcile();
                    } else if(tmp instanceof VarCommand) {
                    	((VarCommand)tmp).reconcile();
                    }
        		} else {
                	parent.children.add(tmp);
                	if(tmpn instanceof StartCommand) {
    	        		get(tmp, iter, state);
    	        		tmp.children.remove(tmp.children.size()-1);
    	        		state.ends--;
                	} else {
                		iter.previous();
    	        		get(parent, iter, state);
                	}
        		}
            } else if(tmp instanceof ValueListCommand) {
            	parent.children.add(tmp);
                get(tmp, iter, state);
                if(tmp.children.size()==0 || !(tmp.children.get(tmp.children.size()-1) instanceof EndValueListCommand)) {
                	throwParseErrorS(o, new RuntimeException("ValueListCommand command should close with a EndCommand `]`"));
                }
            } else if(tmp instanceof StartCommand) {
            	state.starts++;
            	int stindx = parent.children.size();
                parent.children.add(tmp);
                get(parent, iter, state);
                boolean flag = false;
                for (int i = stindx+1; i < parent.children.size(); i++) {
                	if(parent.children.get(i) instanceof EndCommand) {
                    	flag = true;
                    }
				}
                if(!flag) {
                	throwParseErrorS(o, new RuntimeException("StartCommand command should close with a EndCommand `}`"));
                }
            } else if(tmp instanceof MultiCommentCommand) {
                parent.children.add(tmp);
                get(tmp, iter, state);
                if(tmp.children.size()==0 || !(tmp.children.get(tmp.children.size()-1) instanceof EndMultiCommentCommand)) {
                	throwParseErrorS(o, new RuntimeException("MultiCommentCommand command should close with a EndMultiCommentCommand `*/`"));
                }
            }  else if(tmp instanceof CodeCommand) {
                parent.children.add(tmp);
                get(tmp, iter, state);
                if(tmp.children.size()==0 || !(tmp.children.get(tmp.children.size()-1) instanceof EndCodeCommand)) {
                	throwParseErrorS(o, new RuntimeException("CodeCommand command should close with a EndCodeCommand `*/`"));
                }
            } /*else if((tmp instanceof CommentCommand && state.commentStart) || tmp instanceof CodeCommand) {
                if(tmp instanceof CommentCommand) {
                    ((CommentCommand)tmp).b.append(((ValueCommand)tmp.children.get(0)).value+"\n");
                }
                get(tmp, iter, state);
                parent.children.add(tmp);
            }*/ else if(tmp instanceof EndMultiCommentCommand) {
            	if(parent instanceof MultiCommentCommand) {
            		 ((MultiCommentCommand)parent).b.append(state.unsanitize(((EndMultiCommentCommand)tmp).value)+"\n");
            	}
            	parent.children.add(tmp);
                if(!parent.isTop) {
                	return;
                }
            } else if(tmp instanceof EndCommand) {
                parent.children.add(tmp);
                state.ends++;
                if(!parent.isTop) {
                	return;
                }
            } else if(tmp instanceof EndCodeCommand) {
                parent.children.add(tmp);
                if(!parent.isTop) {
                	return;
                }
            } else if(tmp instanceof EndValueListCommand) {
                parent.children.add(tmp);
                if(!parent.isTop) {
                	return;
                }
            } else if(tmp instanceof ImportCommand) {
            	String parentPath = state.basePath;
                if(StringUtils.isNotBlank(o[3].toString())) {
                    parentPath = o[3].toString();
                }
                File f = new File(parentPath + File.separator + ((ImportCommand)tmp).name);
                if(!f.exists()) {
                    f = new File(state.basePath + File.separator + ((ImportCommand)tmp).name);
                    if(!f.exists() && StringUtils.isNotBlank(state.testcaseDir)) {
                        f = new File(state.basePath + File.separator + state.testcaseDir.trim() + File.separator + ((ImportCommand)tmp).name);
                    }
                }
                if(!f.exists()) {
                    throwParseErrorS(o, new RuntimeException("Include script not found in any of the search paths"));
                }
                if(state.visitedFiles.contains(f.getAbsolutePath())) {
                    throwParseErrorS(o, new RuntimeException("Possible include script recursion observed"));
                }
                String currBasePath = state.basePath;
                state.basePath = f.getParent();
                Command imported = Command.read(f.getAbsolutePath(), state);
                for (Command cmd : imported.children) {
					if(cmd instanceof SubTestCommand) {
						parent.children.add(cmd);
						//SubTestCommand st = (SubTestCommand)cmd;
						//st.state = state;
						//st.fName = "__st__" + state.NUMBER_ST++;
						//if(!st.hasName) {
					    //    st.name = "Subtest " + (state.NUMBER_ST - 1);
						//}
						//state.addSubtest((SubTestCommand)cmd);
					}
				}
                state.basePath = currBasePath;
            } else if(tmp instanceof IncludeCommand) {
                String parentPath = state.basePath;
                if(StringUtils.isNotBlank(o[3].toString())) {
                    parentPath = o[3].toString();
                }
                File f = new File(parentPath + File.separator + ((IncludeCommand)tmp).name);
                if(!f.exists()) {
                    f = new File(state.basePath + File.separator + ((IncludeCommand)tmp).name);
                    if(!f.exists() && StringUtils.isNotBlank(state.testcaseDir)) {
                        f = new File(state.basePath + File.separator + state.testcaseDir.trim() + File.separator + ((IncludeCommand)tmp).name);
                    }
                }
                if(!f.exists()) {
                    throwParseErrorS(o, new RuntimeException("Include script not found in any of the search paths"));
                }
                if(state.visitedFiles.contains(f.getAbsolutePath())) {
                    throwParseErrorS(o, new RuntimeException("Possible include script recursion observed"));
                }
                List<String> commands = FileUtils.readLines(f, "UTF-8");
                int cnt = 1;
                String fnm = ((IncludeCommand)tmp).name;
                if(fnm.lastIndexOf("\\")!=-1) {
                    fnm = fnm.substring(0, fnm.lastIndexOf("\\")+1);
                } else if(fnm.lastIndexOf("/")!=-1) {
                    fnm = fnm.substring(0, fnm.lastIndexOf("/")+1);
                }
                for (String c : commands) {
                    iter.add(new Object[]{c, cnt++, f.getParentFile().getAbsolutePath(), fnm, state});
                }
                for (@SuppressWarnings("unused") String c : commands) {
                    iter.previous();
                }
            } else if(tmp instanceof ConfigPropsCommand) {
                String parentPath = state.basePath;
                if(StringUtils.isNotBlank(o[3].toString())) {
                    parentPath = o[3].toString();
                }
                File f = new File(parentPath + File.separator + ((ConfigPropsCommand)tmp).name);
                if(!f.exists()) {
                    f = new File(state.basePath + File.separator + ((ConfigPropsCommand)tmp).name);
                    if(!f.exists() && StringUtils.isNotBlank(state.testcaseDir)) {
                        f = new File(state.basePath + File.separator + state.testcaseDir.trim() + File.separator + ((ConfigPropsCommand)tmp).name);
                    }
                }
                if(!f.exists()) {
                    throwParseErrorS(o, new RuntimeException("Config properties file not found in any of the search paths"));
                }
                Properties tprops = new Properties();
                try {
					tprops.load(new FileInputStream(f));
				} catch (Exception e) {
					throwError(o, new RuntimeException("Config properties file not a valid properties file"));
				}
                state.configProps = tprops;
                if(!parent.isTop) {
                	throwParseErrorS(o, new RuntimeException("mode command allowed only at the top level"));
                }
                parent.children.add(tmp);
            } else if(tmp instanceof DynPropsCommand) {
                String parentPath = state.basePath;
                if(StringUtils.isNotBlank(o[3].toString())) {
                    parentPath = o[3].toString();
                }
                File f = new File(parentPath + File.separator + ((DynPropsCommand)tmp).name);
                if(!f.exists()) {
                    f = new File(state.basePath + File.separator + ((DynPropsCommand)tmp).name);
                    if(!f.exists() && StringUtils.isNotBlank(state.testcaseDir)) {
                        f = new File(state.basePath + File.separator + state.testcaseDir.trim() + File.separator + ((DynPropsCommand)tmp).name);
                    }
                }
                if(!f.exists()) {
                    throwParseErrorS(o, new RuntimeException("Dynamic properties file not found in any of the search paths"));
                }
                Properties tprops = new Properties();
                try {
					tprops.load(new FileInputStream(f));
					if(tprops.size()>0) {
						@SuppressWarnings("unchecked")
		                Enumeration<String> enums = (Enumeration<String>) tprops.propertyNames();
		                while (enums.hasMoreElements()) {
		                	String key = enums.nextElement();
		                    String value = tprops.getProperty(key);
		                	state.dynProps.put(key, value);
		                }
					}
				} catch (Exception e) {
					throwError(o, new RuntimeException("Dynamic properties file not a valid properties file"));
				}
            } else {
                if(parent instanceof CodeCommand) {
                    ((CodeCommand)parent).b.append(((ValueCommand)tmp).value+"\n");
                } else if(parent instanceof MultiCommentCommand) {
                    ((MultiCommentCommand)parent).b.append(state.unsanitize(((ValueCommand)tmp).value)+"\n");
                } else if(tmp instanceof BrowserCommand) {
                    state.started = true;
                    if(state.modeExecType==0) {
                        state.modeExecType = 1;
                    }
                    if(!parent.isTop) {
                    	throwParseErrorS(o, new RuntimeException("open command allowed only at the top level"));
                    }
                    parent.children.add(tmp);
                } else if(tmp instanceof ModeCommand) {
                    if(!((ModeCommand)tmp).name.toLowerCase().matches("normal|integration")) {
                        throwParseErrorS(o, new RuntimeException("mode can have only 2 options normal or integration"));
                    }
                    state.modeExecType = ((ModeCommand)tmp).name.toLowerCase().matches("integration")?2:0;
                    state.modeExecType = ((ModeCommand)tmp).name.toLowerCase().matches("normal")?1:state.modeExecType;
                    
                    state.modeEnableGlobalTimeouts = ((ModeCommand)tmp).enableGlobalTimeouts;
                    if(!parent.isTop) {
                    	throwParseErrorS(o, new RuntimeException("mode command allowed only at the top level"));
                    }
                    parent.children.add(tmp);
                } else if(tmp instanceof SleepCommand) {
                    parent.children.add(tmp);
                } else if(tmp instanceof CommentCommand) {
                    parent.children.add(tmp);
                } else {
                    parent.children.add(tmp);
                }
            }
        }
    }

    static void mergeIfElses(Command cmd) {
        if(cmd==null || cmd.children==null || cmd.children.isEmpty())return;
        IfCommand ifc = null;
        List<Command> ncl = new ArrayList<Command>();
        for (Command c : cmd.children) {
            if(c instanceof IfCommand) {
                ifc = (IfCommand)c;
                ncl.add(c);
            } else if(c instanceof ElseIfCommand) {
                if(ifc!=null) {
                    ifc.elseifs.add((ElseIfCommand)c);
                } else {
                	throwParseErrorS(c.fileLineDetails, new RuntimeException("else if found without a preceeding if"));
                }
            } else if(c instanceof ElseCommand) {
                if(ifc!=null) {
                    ifc.elsecmd = (ElseCommand)c;
                } else {
                	throwParseErrorS(c.fileLineDetails, new RuntimeException("else found without a preceeding if"));
                }
            } else {
                ifc = null;
                ncl.add(c);
            }
            mergeIfElses(c);
        }
        cmd.children = ncl;
    }

    static Command getAll(List<String> scmds, File fn, CommandState state) throws Exception {
        state.commentStart = false;
        Command tcmd = new Command(null, state);
        tcmd.name = fn.getAbsolutePath();

        String relfn = fn.getName();
        if(StringUtils.isNotBlank(state.basePath)) {
        	String path = state.basePath.trim();
        	if(StringUtils.isNotBlank(state.testcaseDir) && !state.testcaseDir.trim().equals(".")) {
        		path += File.separator + state.testcaseDir.trim();
        	}
        	relfn = fn.getAbsolutePath().replace(path, "");
        	if(relfn.startsWith(File.separator)) {
        		relfn = relfn.substring(1);
        	}
        }
        
        List<Object[]> lio = new ArrayList<Object[]>();
        int cnt = 1;
        for (String s : scmds)
        {
            lio.add(new Object[]{s, cnt++, fn, fn.getParentFile()!=null?fn.getParentFile().getAbsolutePath():"", state, relfn});
        }

        tcmd.isTop = true;
        get(tcmd, lio.listIterator(), state);
        mergeIfElses(tcmd);
        if(state.starts!=state.ends) {
        	throwParseErrorS(lio.get(0), new RuntimeException("Blocks not balanced..."));
        }

        Collections.sort(tcmd.children, new Comparator<Command>() {
            public int compare(Command o1, Command o2) {
                return Integer.valueOf(o1.weight()).compareTo(o2.weight());
            }
        });

        return tcmd;
    }

    static Command read(String filename, CommandState state) throws Exception {
        List<String> commands = FileUtils.readLines(new File(filename), "UTF-8");
        state.basePath = new File(filename).getParent();
        state.visitedFiles.add(new File(filename).getAbsolutePath());
        Command cmd = Command.getAll(commands, new File(filename), state);
        return cmd;
    }

    static Command read(String filename) throws Exception {
        List<String> commands = FileUtils.readLines(new File(filename), "UTF-8");
        CommandState state = new CommandState();
        state.basePath = new File(filename).getParent();
        state.visitedFiles.add(new File(filename).getAbsolutePath());
        Command cmd = Command.getAll(commands, new File(filename), state);
        return cmd;
    }

    static Command read(File file, List<String> commands, AcceptanceTestContext context) throws Exception {
        if(commands==null) {
            commands = new ArrayList<String>();
        } else {
            commands.clear();
        }
        SeleniumTest.CONC_WD = context.getGatfExecutorConfig().isSelConcWebdriver();
        commands.addAll(FileUtils.readLines(file, "UTF-8"));
        CommandState state = new CommandState();
        state.basePath = context.getGatfExecutorConfig().getTestCasesBasePath();
        if(context.getGatfExecutorConfig().getTestCaseDir()!=null) {
            state.testcaseDir += context.getGatfExecutorConfig().getTestCaseDir();
        }
        loadPlugins(state, context);
        state.visitedFiles.add(file.getAbsolutePath());
        Command cmd = Command.getAll(commands, file, state);
        cmd.mp = context.getGatfExecutorConfig().getSelDriverConfigMap();
        return cmd;
    }

    static void loadPlugins(CommandState state, AcceptanceTestContext cntxt) throws Exception {
    	List<String> lines = new ArrayList<>();
    	lines.add("jsonread=com.gatf.selenium.plugins.JsonPlugin@read");
    	lines.add("jsonwrite=com.gatf.selenium.plugins.JsonPlugin@write");
    	lines.add("jsonpath=com.gatf.selenium.plugins.JsonPlugin@path");
    	lines.add("xmlread=com.gatf.selenium.plugins.XmlPlugin@read");
    	lines.add("xmlwrite=com.gatf.selenium.plugins.XmlPlugin@write");
    	lines.add("xmlpath=com.gatf.selenium.plugins.XmlPlugin@path");
    	lines.add("api=com.gatf.selenium.plugins.ApiPlugin@api");
    	lines.add("curl=com.gatf.selenium.plugins.CurlPlugin@execute");
    	//lines.add("mail=com.gatf.selenium.plugins.MailPlugin@execute");
        if(cntxt.getResourceFile("plugins.txt").exists()) {
        	lines.addAll(FileUtils.readLines(cntxt.getResourceFile("plugins.txt"), "UTF-8"));
        }
        for (String plg : lines) {
            if(plg.indexOf("=")!=-1) {
                String name = plg.substring(0, plg.indexOf("="));
                String signature = plg.substring(plg.indexOf("=")+1);
                if(!name.trim().isEmpty() && !plugins.containsKey(name.toLowerCase())) {
                    String clsname = signature;
                    String method = "execute";
                    if(clsname.indexOf("@")!=-1) {
                        String[] parts = clsname.split("@");
                        clsname = parts[0].trim();
                        method = parts[1].trim();
                    }

                    try {
                        Class.forName(clsname);
                    } catch (Exception e) {
                        System.out.println("Invalid Plugin specified, Plugin class not found - " + clsname);
                        continue;
                    }

                    try {
                        Class<?> cls = Class.forName(clsname);
                        Method meth = cls.getMethod(method, new Class[]{Object[].class});
                        if(meth.getReturnType().equals(Void.class)) {
                            System.out.println("Invalid Plugin specified, Plugin class method should have either of the following signatures - "
                                    + "public static Object execute(Object[] args) | public static Object execute(String name, Object[] args) - " + plg);
                            continue;
                        }
                        plugins.put(name.toLowerCase().trim(), clsname+"@"+method);
                    } catch (Exception e) {
                        try {
                            Class<?> cls = Class.forName(clsname);
                            Method meth = cls.getMethod(method, new Class[]{String.class, Object[].class});
                            if(meth.getReturnType().equals(Void.class)) {
                                System.out.println("Invalid Plugin specified, Plugin class method should have either of the following signatures - "
                                        + "public static Object execute(Object[] args) | public static Object execute(String name, Object[] args) - " + plg);
                                continue;
                            }
                            plugins.put(name.toLowerCase().trim(), clsname+"@"+method);
                        } catch (Exception e1) {
                            System.out.println("Invalid Plugin specified, Plugin class method should have either of the following signatures - "
                                    + "public static Object execute(Object[] args) | public static Object execute(String name, Object[] args) - " + plg);
                            continue;
                        }
                    }
                } else {
                    if(name.trim().isEmpty()) {
                        System.out.println("Invalid Plugin name specified");
                    } else {
                        System.out.println("Plugin already defined " + name); 
                    }
                }
            }
        }
        for (String name : plugins.keySet()) {
        	String signature = plugins.get(name);
	        try {
	            String[] parts = signature.split("@");
	            String clsname = parts[0].trim();

	            Class<?> cls = Class.forName(clsname);
	            Method meth = cls.getMethod("init", new Class[]{AcceptanceTestContext.class});
	            
	            meth.invoke(null, new Object[] {cntxt});
	        } catch (NoSuchMethodException e) {
	        } catch (Exception e) {
	        	System.out.println("Exception during initilization of plugin \n" + ExceptionUtils.getStackTrace(e));
	        }
		}
    }

    public static String[] toSampleSelCmd() {
    	return null;
    }

    String toCmd() {
        StringBuilder b = new StringBuilder();
        for (Command c : children) {
            b.append(c.toCmd());
            b.append("\n");
        }
        return b.toString();
    }

    String selcode(String varnm) {
        return "";
    }

    String javacode() {
        StringBuilder b = new StringBuilder();
        b.append("package com.gatf.selenium;\n");
        for (Command c : children) {
            if(c instanceof RequireCommand) {
                String cc = c.javacode();
                b.append(cc);
                if(!cc.isEmpty()) {
                    b.append("\n");
                }
            }
        }
        b.append("import java.io.Serializable;\n");
        b.append("import com.gatf.selenium.SeleniumException;\n");
        b.append("import org.openqa.selenium.remote.DesiredCapabilities;\n");
        b.append("import org.openqa.selenium.logging.LoggingPreferences;\n");
        b.append("import org.openqa.selenium.remote.CapabilityType;\n");
        b.append("import org.openqa.selenium.logging.Logs;\n");
        b.append("import java.util.List;\n");
        b.append("import org.openqa.selenium.SearchContext;\n");
        b.append("import org.openqa.selenium.WebDriver;\n");
        b.append("import org.openqa.selenium.WebElement;\n");
        b.append("import org.openqa.selenium.JavascriptExecutor;\n");
        b.append("import org.openqa.selenium.TakesScreenshot;\n");
        b.append("import org.openqa.selenium.OutputType;\n");
        b.append("import com.gatf.executor.core.AcceptanceTestContext;\n");
        b.append("import com.gatf.selenium.SeleniumTest;\n");
        b.append("import com.gatf.selenium.SeleniumTestSession;\n");
        b.append("import org.openqa.selenium.Dimension;\n");
        b.append("import org.openqa.selenium.Point;\n");
        b.append("import org.openqa.selenium.By;\n");
        b.append("import org.openqa.selenium.support.ui.WebDriverWait;\n");
        b.append("import org.openqa.selenium.support.ui.Select;\n");
        b.append("import org.openqa.selenium.interactions.Actions;\n");
        b.append("import java.util.function.Function;\n");
        b.append("import javax.imageio.ImageIO;\n");
        b.append("import java.awt.image.BufferedImage;\n");
        b.append("import org.apache.commons.io.FileUtils;\n");
        b.append("import java.io.File;\n");
        b.append("import groovy.lang.Binding;\n");
        b.append("import groovy.lang.GroovyShell;\n");
        b.append("import org.jruby.embed.LocalVariableBehavior;\n");
        b.append("import org.jruby.embed.ScriptingContainer;\n");
        b.append("import org.python.util.PythonInterpreter;\n");
        b.append("import org.python.core.*;\n");
        b.append("import java.util.Map;\n");
        b.append("import org.junit.Assert;\n");
        b.append("import org.openqa.selenium.Keys;\n");
        b.append("import ru.yandex.qatools.ashot.AShot;\n");
        b.append("import ru.yandex.qatools.ashot.shooting.ShootingStrategies;\n\n");
        b.append("/*GATF_ST_CLASS_START_*/public class "+className+" extends SeleniumTest implements Serializable {\n");
        b.append("public "+className+"(AcceptanceTestContext ___cxt___, int index) {\n/*GATF_ST_CLASS_INIT_*/super(\""+esc(name)+"\", ___cxt___, index);\n}\n");
        b.append("public void close() {\n/*GATF_ST_CLASS_CLOSE_*/if(get___d___()!=null)get___d___().close();\n}\n");
        b.append("public SeleniumTest copy(AcceptanceTestContext ctx, int index) {\nreturn new "+className+"(ctx, index);}\n");
        List<ConfigPropsCommand> cmds = new ArrayList<>();
        if(mp==null || mp.size()==0) {
        	for (Command c : children) {
                if(c instanceof BrowserCommand) {
                	SeleniumDriverConfig dummy = new SeleniumDriverConfig();
                	dummy.setArguments("");
                	dummy.setVersion("");
                	dummy.setName(c.name);
                	mp.put(c.name, dummy);
                }
            }
        }
        for (SeleniumDriverConfig driverConfig : mp.values())
        {
            b.append("public void setupDriver"+driverConfig.getName().toLowerCase().replaceAll("[^0-9A-Za-z]+", "")+"(LoggingPreferences ___lp___) throws Exception {\n");
            DriverCommand cmd = new DriverCommand(driverConfig, new Object[]{}, state);
            String cc = cmd.javacode();
            b.append(cc);
            if(!cc.isEmpty()) {
                b.append("\n");
            }
            for (Command c : children) {
            	if(c instanceof ConfigPropsCommand) {
            		cmds.add((ConfigPropsCommand)c);
            		b.append(c.javacode());
            	}
            }
            b.append("}\n"); 
        }
        List<String[]> bn = new ArrayList<String[]>();
        Set<String> sessDups = new HashSet<String>();
        int lastSessionId = 0;
        for (Command c : children) {
            if(c instanceof BrowserCommand) {
                if(state.modeExecType==2 && ((BrowserCommand)c).sessionName!=null && sessDups.contains(((BrowserCommand)c).sessionName)) {
                    throwError(c.fileLineDetails, new RuntimeException("In integration mode duplicate session names are not allowed " + c.name + "/" + ((BrowserCommand)c).sessionName));
                }
                if(((BrowserCommand)c).sessionName!=null) {
                    sessDups.add(((BrowserCommand)c).sessionName);
                }
                ((BrowserCommand)c).sessionId = lastSessionId;
                if(mp.containsKey(c.name)) {
                    bn.add(new String[]{c.name.toLowerCase(), ((BrowserCommand)c).sessionName, ((BrowserCommand)c).sessionId+""});
                } else {
                	b.append("public void setupDriver"+c.name.toLowerCase().replaceAll("[^0-9A-Za-z]+", "")+"(LoggingPreferences ___lp___) throws Exception {\n");
                	SeleniumDriverConfig sd = new SeleniumDriverConfig();
                	sd.setName(c.name);
                	sd.setArguments("");
                    DriverCommand cmd = new DriverCommand(sd, new Object[]{}, state);
                    String cc = cmd.javacode();
                    b.append(cc);
                    if(!cc.isEmpty()) {
                        b.append("\n");
                    }
                    for (ConfigPropsCommand cfp : cmds) {
                    	b.append(cfp.javacode());
                    }
                    b.append("}\n");
                    bn.add(new String[]{c.name.toLowerCase(), ((BrowserCommand)c).sessionName, ((BrowserCommand)c).sessionId+""});
                    //throwError(c.fileLineDetails, new RuntimeException("Driver configuration not found for " + c.name));
                }
                lastSessionId++;
            } else if(c instanceof SubTestCommand) {
            }
        }
        if(bn.size()==0) {
        	throw new RuntimeException("No open command found...");
        }
        for (Object[] st : state.subtestDetails)
        {
        	String stsessionName = StringUtils.isNotBlank((String)st[1])?(String)st[1]:null;
            String stsessionId = null;
            try {
                stsessionId = Integer.parseInt((String)st[2])+"";
            } catch (Exception e) {
            }
            
            if(stsessionName!=null || stsessionId!=null) {
	            boolean flag = false;
	        	for (String[] brn : bn)
	            {
	                String sessionName = brn[1];
	                String sessionId = brn[2];
	                
	                flag |= (sessionName!=null && sessionName.equalsIgnoreCase(stsessionName)) || sessionId.equalsIgnoreCase(stsessionId);
	            }
	        	if(!flag) {
	        		throwError((Object[])st[3], new RuntimeException("Session Details not found for \"" + (stsessionName!=null?stsessionName:stsessionId+1) + "\""));
	        	} else {
	        		
	        	}
            }
        }
        b.append("public List<SeleniumTestSession> execute(LoggingPreferences ___lp___) throws Exception {\n");
        b.append("/*GATF_ST_START_*/");
        for (String[] brn : bn)
        {
        	String bsessionName = brn[1];
            String bsessionId = brn[2];
            //b.append("java.util.Set<String> "+state.varnameat()+" = addTest("+(StringUtils.isNotBlank(brn[1])?("\""+esc(brn[1])+"\""):"null")+", \""+esc(brn[0])+"\");\n");
            b.append("addTest("+(StringUtils.isNotBlank(brn[1])?("\""+esc(brn[1])+"\""):"null")+", \""+esc(brn[0])+"\");\n");
            for (Object[] st : state.subtestDetails)
            {
                String sessionName = StringUtils.isNotBlank((String)st[1])?(String)st[1]:null;
                String sessionId = null;
                try {
                	sessionId = Integer.parseInt((String)st[2])+"";
                } catch (Exception e) {
                }
                //b.append("if(checkifSessionIdExistsInSet("+state.currvarnameat()+", "
                //            +(sessionName!=null?"\""+esc(sessionName)+"\"":"null")+", "
                //            +(sessionId!=null?"\""+sessionId+"\"":"null") + ")) {");
                if(((bsessionName!=null && bsessionName.equalsIgnoreCase(sessionName)) || bsessionId.equalsIgnoreCase(sessionId)) || (sessionName==null && sessionId==null)) {
                	if((Boolean)st[5]) {
		                b.append("setSession("+(bsessionName!=null?"\""+esc(bsessionName)+"\"":"null")+", "
		                            +(bsessionId!=null?bsessionId:"-1")+", false);\n");
		                b.append("addSubTest(\""+esc(brn[0])+"\", \""+esc((String)st[0])+"\", "+(Boolean)st[6]+");\n\n");
                	}
                }
                //b.append("}\n");
            }
        }
        b.append("\n\n");
        
        if(state.modeExecType==2) {
            if(bn.size()>1 && state.subtestDetails.size()==0) {
                throw new RuntimeException("In integration mode subtests are required per browser session");
            }
            for (int y=bn.size()-1;y>=0;y--)
            {
                String[] brn = bn.get(y);
                b.append("quit();\n");
                b.append("setupDriver"+brn[0].replaceAll("[^0-9A-Za-z]+", "")+"(___lp___);\n");
            }
            b.append("_execute(___lp___);\n");
        } else {
            for (String[] brn : bn)
            {
                b.append("setSession(null, " + (brn[2]!=null?brn[2]:"-1")+", true);\n");
                //b.append("startTest();\n");
                b.append("quit();\n");
                b.append("setupDriver"+brn[0].replaceAll("[^0-9A-Za-z]+", "")+"(___lp___);\n");
                b.append("_execute(___lp___);\n");
            }
        }
        
        b.append("return get__sessions__();\n}\n");
        b.append("public int concurrentExecutionNum() {\n");
        b.append("return " + state.concExec+";\n");
        b.append("}\n");
        b.append("public void _execute(LoggingPreferences ___lp___) throws Exception {\n");
        b.append("WebDriver ___ocw___ = null;\n");
        b.append("try {\n");
        state.pushSc();
        b.append("SearchContext "+state.currvarnamesc()+" = get___d___();\n");
        b.append("WebDriver ___cw___ = get___d___();\n");
        b.append("___ocw___ = ___cw___;\n");
        b.append("List<WebElement> ___ce___ = null;\n");
        int subtestcount = 0;
        for (Command c : children) {
            if((c instanceof RequireCommand) || (c instanceof BrowserCommand) || (c instanceof ModeCommand) || (c instanceof ConfigPropsCommand) || (c instanceof SubTestCommand)) {
                continue;
            }
            String cc = null;
            /*if(c instanceof SubTestCommand) {
                cc = ((SubTestCommand)c).javacodesubtest(subtestcount>0);
                subtestcount++;
            } else*/ if(c instanceof ExecSubTestCommand) {
                cc = ((ExecSubTestCommand)c).javacodesubtest(subtestcount>0);
                subtestcount++;
            } else {
            	cc = genDebugInfo(c);
                cc += c.javacode();
            }
            b.append(cc);
            if(!cc.isEmpty()) {
                b.append("\n");
            }
        }
        b.append("pushResult(new SeleniumTestResult(get___d___(), this, ___lp___));\n");
        String ex = state.evarname();
        b.append("}\ncatch(Throwable "+ex+")\n{");
        //b.append("\ntry{");
        //b.append(ex+".printStackTrace();\n");
        //b.append("java.lang.System.out.println(\"_main_exec.png\");");
        //ScreenshotCommand tm = new ScreenshotCommand("_main_exec.png", new Object[] {}, state, true);
        //b.append(tm.javacode());
        //b.append("}catch(java.io.IOException _ioe){}");
        b.append("if("+ex+" instanceof FailSubTestException) "+ex+" = new RuntimeException("+ex+");\n");
    	b.append("else if("+ex+" instanceof SubTestException) "+ex+" = new RuntimeException("+ex+");\n");
        b.append("pushResult(new SeleniumTestResult(get___d___(), this, "+ex+", getOutDir() + java.io.File.separator + \""+UUID.randomUUID().toString()+".png\", ___lp___));\n}");
        b.append("}\n");
        for (Command c : state.allSubTests) {
            if(c instanceof SubTestCommand) {
                SubTestCommand st = (SubTestCommand)c;
                b.append(st.javacode());
            }
        }
        b.append("}\n");
        return b.toString();
    }
    
    public static String genDebugInfo(Command c) {
    	if(c.fileLineDetails!=null && c.fileLineDetails.length>0) {
    		if(c instanceof GotoCommand) {
    			return "/*GATF_ST_LINE@" + c.fileLineDetails[2].toString().trim()+":"+c.fileLineDetails[1] + "_*/__set__cln__("+c.fileLineDetails[1]+");";
    		}
    		return "/*GATF_ST_LINE@" + c.fileLineDetails[2].toString().trim()+":"+c.fileLineDetails[1] + "*/__set__cln__("+c.fileLineDetails[1]+");";
    	}
    	return "";
    }

    public static class ExecCommand extends Command {
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
	    		"\texec @print(@index)"
        	};
        }
    }

    public static class CommentCommand extends Command {
        String val;
        String javacode() {
            return "//" + val;
        }
        CommentCommand(String val, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            this.val = state.unsanitize(val);
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
				"Single line comment",
				"\t//..."
        	};
        }
    }

    public static class MultiCommentCommand extends Command {
        StringBuilder b = new StringBuilder();
        String javacode() {
            return b.toString() + "*/";
        }
        MultiCommentCommand(Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            b.append("/*");
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
				"Block level comment",
				"\t/*...*/"
        	};
        }
    }

    public static class EndMultiCommentCommand extends Command {
        String type;
        String value;
        EndMultiCommentCommand(String value, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            this.value = value;
        }
        String toCmd() {
            return type;
        }
        String javacode() {
            return "";
        }
		public static String[] toSampleSelCmd() {
			return null;
		}
    }

    public static class CodeCommand extends Command {
        static Pattern p = Pattern.compile("\\$([v|V]*)\\{([a-zA-Z0-9_]+)\\}");
        static Pattern p1 = Pattern.compile("@cntxtParam\\([a-zA-Z0-9_]+\\)");
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
				"\t<<<(java|js|ruby|groovy|python) a,b,c\n\tcode\n\t>>>",
				"Available variables in context -",
				"\t1. @driver - WebDriver instance (Java only)",
				"\t2. @window - WebDriver instance (Java only)",
				"\t3. @element - Currently selected WebElement instance (Java only)",
				"\t4. @sc - Currently selected SearchContext instance (Java only)",
				"\t5. @printProvJson - Print Provider data as json (Java only)",
				"\t6. @printProv - Print Provider data (Java only)",
				"\t7. @print - System.out.println (Java only)",
				"\t8. @index - Current provider index under iteration (Java only)",
				"\t9. @cntxtParam - Add variable to current context",
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
                String args = "";
                for (String arg : arglist)
                {
                    String vn = "";
                    if(arg.indexOf("=")!=-1) {
                        vn = arg.substring(0, arg.indexOf("="));
                        arg = arg.substring(arg.indexOf("=")+1);
                    }
                    if(vn.trim().equals(arg.trim()))continue;
                    Matcher m = p.matcher(arg);
                    if(m.find()) {
                        if(vn.trim().isEmpty()) {
                            vn = m.group(2);
                        }
                        args += "Object "+vn+" = getProviderDataValueO(\""+esc(m.group(2))+"\", "+(m.group(1).toLowerCase().trim().equals("v")?"true":"false")+");\n";
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
                gcode += "Binding __b = new Binding();\n";
                for (String arg : arglist)
                {
                    String vn = "";
                    if(arg.indexOf("=")!=-1) {
                        vn = arg.substring(0, arg.indexOf("="));
                        arg = arg.substring(arg.indexOf("=")+1);
                    }
                    if(vn.trim().equals(arg.trim()))continue;
                    m = p.matcher(arg);
                    if(m.find()) {
                        if(vn.trim().isEmpty()) {
                            vn = m.group(2);
                        }
                        gcode += "__b.setVariable(\""+vn+"\", getProviderDataValueO(\""+esc(m.group(2))+"\", "+(m.group(1).toLowerCase().trim().equals("v")?"true":"false")+"));\n";
                    } else {
                        gcode += "__b.setVariable(\""+vn+"\", "+arg+");\n";
                    }
                }
                gcode += "GroovyShell __gs = new GroovyShell(__b);\n";
                gcode += "__gs.evaluate(\""+esc(state.unsanitize(code)).replaceAll("\n", "\\\\n")+"\");\n";
                for (String s : ms)
                {
                    gcode += s.replaceAll("@cntxtParam\\(([a-zA-Z0-9_]+)\\)", "___cxt___add_param__(\"$1\", __gs.getVariable(\"$1\").toString());\n");
                }
                return gcode;
            } else if(lang.equals("js")) {
                String jscode = "";
                String args = "";
                for (String arg : arglist)
                {
                	String vn = "";
                    if(arg.indexOf("=")!=-1) {
                        vn = arg.substring(0, arg.indexOf("="));
                        arg = arg.substring(arg.indexOf("=")+1);
                    }
                    if(vn.trim().equals(arg.trim()))continue;
                    Matcher m = p.matcher(arg);
                    if(m.find()) {
                        if(vn.trim().isEmpty()) {
                            vn = m.group(2);
                        }
                        args += "var " + vn + " = \\\"\"+getProviderDataValue(\""+esc(m.group(2))+"\", "+(m.group(1).toLowerCase().trim().equals("v")?"true":"false")+")+\"\\\";";
                    } else {
                    	arg = arg.replace("@index", "get__loopcontext__indx()");
                    	args += "var " + vn + " = " + arg + ";";
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
                rcode += "ScriptingContainer __rs = new ScriptingContainer(LocalVariableBehavior.PERSISTENT);\n";
                for (String arg : arglist)
                {
                    String vn = "";
                    if(arg.indexOf("=")!=-1) {
                        vn = arg.substring(0, arg.indexOf("="));
                        arg = arg.substring(arg.indexOf("=")+1);
                    }
                    if(vn.trim().equals(arg.trim()))continue;
                    m = p.matcher(arg);
                    if(m.find()) {
                        if(vn.trim().isEmpty()) {
                            vn = m.group(2);
                        }
                        rcode += "__rs.put(\""+vn+"\", getProviderDataValueO(\""+esc(m.group(2))+"\", "+(m.group(1).toLowerCase().trim().equals("v")?"true":"false")+"));\n";
                    } else {
                        rcode += "__rs.put(\""+vn+"\", "+arg+");\n";
                    }
                }
                rcode += "__rs.runScriptlet(\""+esc(state.unsanitize(code)).replaceAll("\n", "\\\\n")+"\");\n";
                for (String s : ms)
                {
                    rcode += s.replaceAll("@cntxtParam\\(([a-zA-Z0-9_]+)\\)", "___cxt___add_param__(\"$1\", __rs.get(\"$1\").toString());\n");
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
                pcode += "PythonInterpreter pi = new PythonInterpreter();\n";
                for (String arg : arglist)
                {
                    String vn = "";
                    if(arg.indexOf("=")!=-1) {
                        vn = arg.substring(0, arg.indexOf("="));
                        arg = arg.substring(arg.indexOf("=")+1);
                    }
                    if(vn.trim().equals(arg.trim()))continue;
                    m = p.matcher(arg);
                    if(m.find()) {
                        if(vn.trim().isEmpty()) {
                            vn = m.group(2);
                        }
                        pcode += "pi.set(\""+vn+"\", getProviderDataValueO(\""+esc(m.group(2))+"\", "+(m.group(1).toLowerCase().trim().equals("v")?"true":"false")+"));\n";
                    } else {
                        pcode += "pi.set(\""+vn+"\", "+arg+");\n";
                    }
                }
                pcode += "pi.exec(\""+esc(state.unsanitize(code)).replaceAll("\n", "\\\\n")+"\");\n";
                pcode += "pi.close();\n";
                for (String s : ms)
                {
                    pcode += s.replaceAll("@cntxtParam\\(([a-zA-Z0-9_]+)\\)", "___cxt___add_param__(\"$1\", __rs.get(\"$1\").toString());\n");
                }
                return pcode;
            }
            return "";
        }
    }

    public static class JavaControlCommand extends Command {
        String code;
        JavaControlCommand(String code, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            this.code = state.unsanitize(code);
        }
        String toCmd() {
            return "#j" + code;
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
        	Matcher m = CodeCommand.p.matcher(code);
            while(m.find()) {
            	code = code.replaceFirst("\\$"+m.group(1)+"\\{"+m.group(2)+"\\}", "getProviderDataValueO(\""+esc(m.group(2))+"\", "+(m.group(1).toLowerCase().trim().equals("v")?"true":"false")+")");
            }
            //System.out.println(code);
            return code;
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
				"Execute java code",
				"\t#j{if|try|catch|finally|else|else if|while|for|continue|break|\\{|\\}|synchronized} {java statement}",
				"Examples :-",
	    		"\t#jif(1==1) {} else {}",
	    		"\t#jfor(int i=0;i<10;i++){}"
            };
        }
    }

    public static class ExecJsCommand extends Command {
        String code;
        ExecJsCommand(String code, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            code = state.unsanitize(code);
            if(code.charAt(0)==code.charAt(code.length()-1)) {
                if(code.charAt(0)=='"' || code.charAt(0)=='\'') {
                    code = code.substring(1, code.length()-1);
                }
            }
            this.code = code;
        }
        String toCmd() {
            return "execjs \"" + code + "\"";
        }
        String javacode() {
            code = code.replace("@index", "get__loopcontext__indx()");
            return "if (___ocw___ instanceof JavascriptExecutor) {\n((JavascriptExecutor)___ocw___).executeScript(evaluate(\""+esc(code)+"\"));\n}";
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
				"Execute javascript code in the browser",
				"\texecjs {javascript statement}",
				"Examples :-",
	    		"\texecjs 'console.log(\"Hello\");'",
	    		"\texecjs '$(\"#elid\").click();'"
            };
        }
    }

    public static class ExecJsFileCommand extends Command {
        String code;
        ExecJsFileCommand(String code, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            code = state.unsanitize(code);
            if(code.charAt(0)==code.charAt(code.length()-1)) {
                if(code.charAt(0)=='"' || code.charAt(0)=='\'') {
                    code = code.substring(1, code.length()-1);
                }
            }
            this.code = code;
        }
        String toCmd() {
            return "execjsfile \"" + code + "\"";
        }
        String javacode() {
            return "if (___ocw___ instanceof JavascriptExecutor) {\n((JavascriptExecutor)___ocw___).executeScript(org.apache.commons.io.FileUtils.readFileToString(new java.io.File(\""+esc(code)+"\"), \"UTF-8\"));\n}";
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
				"Execute javascript code from file in the browser",
				"\texecjsfile {javascript file path}",
				"Examples :-",
	    		"\texecjsfile 'file.js'"
            };
        }
    }

    public static class CanvasCommand extends FindCommandImpl {
        CanvasCommand(String cmd, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            try {
                cond = new FindCommand(cmd, cmdDetails, state);
			} catch (Exception e) {
				throwParseErrorS(cmdDetails, e);
			}
        }
        String toCmd() {
            return "canvas " + cond.toCmd();
        }
        String javacode() {
        	StringBuilder b = new StringBuilder();
            b.append(cond.javacodeonly());
            b.append("if (___ocw___ instanceof JavascriptExecutor) {((JavascriptExecutor)___ocw___).executeScript(\"var c = arguments[0];var ctx = c.getContext(\\\"2d\\\");" + 
                		"ctx.beginPath();ctx.arc(30, 30, 10, 0, 2 * Math.PI);ctx.stroke();\", ___ce___.get(0));}");
            return b.toString();
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
				"Draw a circle in a canvas element",
				"\tcanvas {find-expr}",
				"Examples :-",
	    		"\tcanvas xpath@\"asds\"",
            };
        }
    }
    
    public static class ExecSubTestCommand extends Command {
    	SubTestCommand subt = null;
    	String sessionName;
        Integer sessionId = null;
        Map<String, String> params = new HashMap<String, String>();
    	ExecSubTestCommand(String val, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            String[] parts = val.trim().split("[\t ]+");
            if(parts.length==0) {
            	throwError(fileLineDetails, new RuntimeException("Please specify a subtest name to execute"));
            }
            name = unSantizedUnQuoted(parts[0].trim(), state);
            for (Command c : state.allSubTests) {
            	SubTestCommand st = (SubTestCommand)c;
				if(st.name.equalsIgnoreCase(name)) {
					subt = st;
					if(StringUtils.isBlank(sessionName)) {
						sessionName = subt.sessionName;
					}
					if(sessionId==null) {
						sessionId = subt.sessionId;
					}
					break;
				}
            }
            if(subt==null) {
            	throwError(fileLineDetails, new RuntimeException("No alias/subtest with that name found - " + name));
            }
            String pstr = "";
            if(parts.length>1) {
            	if(parts[1].startsWith("(")) {
            		pstr += parts[1].substring(1) + " ";
            		boolean pvalid = false;
            		int counter = 2;
            		for (;counter < parts.length;counter++) {
            			if(parts[counter].endsWith(")")) {
            				pstr += parts[counter].substring(0, parts[counter].length()-1) + " ";
            				pvalid = true;
            				break;
            			} else {
            				pstr += parts[counter].substring(1) + " ";
            			}
            		}
            		if(!pvalid) {
            			throwError(fileLineDetails, new RuntimeException("Invalid "+(subt.isAnAlias?"alias":"subtest")+" parameter(s), please use format `(\"p1:value1\" \"p2:value2\" ...)`"));
            		} else {
            			String[] nparts = pstr.trim().split("[\t ]+");
            			for (int i = 0; i < nparts.length; i++) {
    						String param = unSantizedUnQuoted(nparts[i].trim(), state);
    						if(StringUtils.isNotBlank(param)) {
    							String[] pdet = param.split(":");
    							if(pdet.length!=2) {
    								throwError(fileLineDetails, new RuntimeException("Invalid parameter syntax, please use param-name:param-value syntax"));
    							}
    							if(pdet[0].trim().matches("[a-zA-Z]+[a-zA-Z0-9_]*")) {
    								params.put(pdet[0].trim(), pdet[1]);
    							} else {
    								throwError(fileLineDetails, new RuntimeException("Invalid "+(subt.isAnAlias?"alias":"subtest")+" parameter name"));
    							}
    						}
    					}
            		}
            		counter++;
            		if(counter<parts.length && !subt.isAnAlias) {
            			if(parts[counter].trim().matches("@[0-9]+")) {
                            sessionId = Integer.parseInt(parts[counter].trim().substring(1)) - 1;
                            if(sessionId<0) {
                            	throwError(fileLineDetails, new RuntimeException("Session id should be greater than 0"));
                            }
                        } else {
                            sessionName = unSantizedUnQuoted(parts[counter].trim(), state);
                        }
            		}
            	} else if(!subt.isAnAlias) {
            		if(parts[1].trim().matches("@[0-9]+")) {
                        sessionId = Integer.parseInt(parts[1].trim().substring(1)) - 1;
                        if(sessionId<0) {
                        	throwError(fileLineDetails, new RuntimeException("Session id should be greater than 0"));
                        }
                    } else {
                        sessionName = unSantizedUnQuoted(parts[1].trim(), state);
                    }
            	}
            }
            if(!subt.isAnAlias) {
	            /*for (Command c : state.allSubTests) {
	            	SubTestCommand st = (SubTestCommand)c;
					if(st.name.equals(name) && !st.isAnAlias) {
						Map<String, String> nparams = new HashMap<String, String>(st.params);
						nparams.putAll(params);
						subt = st;
						if(StringUtils.isBlank(sessionName)) {
							sessionName = subt.sessionName;
						}
						if(sessionId==null) {
							sessionId = subt.sessionId;
						}
						break;
					}
				}
	            if(subt==null) {
	            	throwError(fileLineDetails, new RuntimeException("Subtest with name \""+name+"\" not found"));
	            }*/
	            state.addSubtest(this);
            }
        }
        String javacode() {
            return javacodesubtest(false);
        }
        String javacodesubtest(boolean initvars) {
            StringBuilder b = new StringBuilder();
            if(!subt.isAnAlias) {
	            if(state.modeExecType==2) {
	                if(sessionName!=null) {
	                    b.append("setSession(\""+esc(sessionName)+"\", -1, true);\n"); 
	                } else if(sessionId>0) {
	                    b.append("setSession(null, "+(sessionId-1)+", true);\n"); 
	                }
	            } else {
	                b.append("if(matchesSessionId("+(sessionName!=null?"\""+esc(sessionName)+"\"":"null")+", "
	                        +((sessionId!=null&&sessionId>0)?(sessionId+""):"-1") + ")) {");
	            }
	            if(initvars) {
	                b.append("___sc___1 = get___d___();\n");
	                b.append("___cw___ = get___d___();\n");
	                b.append("___ocw___ = ___cw___;\n");
	            }
            }
            for (String pn : params.keySet()) {
				b.append("___add_var__(\""+pn+"\", \""+esc(params.get(pn))+"\");\n");
			}
            b.append(genDebugInfo(this));
            b.append("___ce___ = " + subt.fName+"(___cw___, ___ocw___, "+state.currvarnamesc()+", ___lp___);\n");
            if(!subt.isAnAlias && state.modeExecType!=2) {
                b.append("}\n");
            }
            return b.toString();
        }
    }

    public static class SubTestCommand extends Command {
        String sessionName;
        Integer sessionId = null;
        boolean isAnAlias = false;
        boolean hasName = true;
        Map<String, String> params = new HashMap<String, String>();
        String fName = "__st__" + state.NUMBER_ST++;
        SubTestCommand(String val, Object[] cmdDetails, CommandState state, boolean isAnAlias) {
            super(cmdDetails, state);
            this.isAnAlias = isAnAlias;
            String[] parts = val.trim().split("[\t ]+");
            if(parts.length>=1) {
                name = unSantizedUnQuoted(parts[0].trim(), state);
                if(name.length()>0) {
                } else {
                	hasName = false;
                    name = "Subtest " + (state.NUMBER_ST - 1);
                }
                String pstr = "";
                if(parts.length>1) {
                	if(parts[1].startsWith("(")) {
                		pstr += parts[1].substring(1) + " ";
                		boolean pvalid = false;
                		int counter = 2;
                		for (;counter < parts.length;counter++) {
                			if(parts[counter].endsWith(")")) {
                				pstr += parts[counter].substring(0, parts[counter].length()-1) + " ";
                				pvalid = true;
                				break;
                			} else {
                				pstr += parts[counter].substring(1) + " ";
                			}
                		}
                		if(!pvalid) {
                			throwError(fileLineDetails, new RuntimeException("Invalid subtest parameter(s), please use format `(\"p1:value1\" \"p2:value2\" ...)`"));
                		} else {
                			String[] nparts = pstr.trim().split("[\t ]+");
                			for (int i = 0; i < nparts.length; i++) {
        						String param = unSantizedUnQuoted(nparts[i].trim(), state);
        						if(StringUtils.isNotBlank(param)) {
        							String[] pdet = param.split(":");
        							if(pdet.length!=2) {
        								throwError(fileLineDetails, new RuntimeException("Invalid parameter syntax, please use param-name:param-value syntax"));
        							}
        							if(pdet[0].trim().matches("[a-zA-Z]+[a-zA-Z0-9_]*")) {
        								params.put(pdet[0].trim(), pdet[1]);
        							} else {
        								throwError(fileLineDetails, new RuntimeException("Invalid subtest parameter name"));
        							}
        						}
        					}
                		}
                		counter++;
                		if(counter<parts.length) {
                			if(parts[counter].trim().matches("@[0-9]+")) {
                                sessionId = Integer.parseInt(parts[counter].trim().substring(1)) - 1;
                                if(sessionId<0) {
                                	throwError(fileLineDetails, new RuntimeException("Session id should be greater than 0"));
                                }
                            } else {
                                sessionName = unSantizedUnQuoted(parts[counter].trim(), state);
                            }
                		}
                	} else {
                		if(parts[1].trim().matches("@[0-9]+")) {
                            sessionId = Integer.parseInt(parts[1].trim().substring(1)) - 1;
                            if(sessionId<0) {
                            	throwError(fileLineDetails, new RuntimeException("Session id should be greater than 0"));
                            }
                        } else {
                            sessionName = unSantizedUnQuoted(parts[1].trim(), state);
                        }
                	}
                }
            } else {
            	hasName = false;
                name = "Subtest " + (state.NUMBER_ST - 1);
            }
            state.addSubtest(this);
        }
        String toCmd() {
            StringBuilder b = new StringBuilder();
            b.append(isAnAlias?"alias":"subtest");
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
            return javacodeint();
        }
        /*String javacodesubtest(boolean initvars) {
            StringBuilder b = new StringBuilder();
            if(!isAnAlias) {
	            if(state.modeExecType==2) {
	                if(sessionName!=null) {
	                    b.append("setSession(\""+esc(sessionName)+"\", -1, true);\n"); 
	                } else if(sessionId>0) {
	                    b.append("setSession(null, "+(sessionId-1)+", true);\n"); 
	                }
	            } else {
	                b.append("if(matchesSessionId("+(sessionName!=null?"\""+esc(sessionName)+"\"":"null")+", "
	                        +((sessionId!=null&&sessionId>0)?(sessionId+""):"-1") + ")) {");
	            }
	            if(initvars) {
	                b.append("___sc___1 = get___d___();\n");
	                b.append("___cw___ = get___d___();\n");
	                b.append("___ocw___ = ___cw___;\n");
	            }
            }
            for (String pn : params.keySet()) {
				b.append("___add_var__(\""+pn+"\", \""+esc(params.get(pn))+"\");\n");
			}
            b.append(genDebugInfo(this));
            b.append("___ce___ = " + fName+"(___cw___, ___ocw___, "+state.currvarnamesc()+", ___lp___);\n");
            if(!isAnAlias && state.modeExecType!=2) {
                b.append("}\n");
            }
            return b.toString();
        }*/
        String javacodeint() {
            StringBuilder b = new StringBuilder();
            b.append("List<WebElement> " + fName + "(WebDriver ___cw___, WebDriver ___ocw___, SearchContext "+state.currvarnamesc()+", LoggingPreferences ___lp___) {\n");
            b.append("List<WebElement> ___ce___ = null;\n");
            if(!children.isEmpty())
            {
            	b.append(genDebugInfo(this));
            	String pstn = state.varnamerandom();
                b.append("String "+pstn+" = get__"+(!isAnAlias?"subtest":"alias")+"name__();\n");
            	b.append("set__"+(!isAnAlias?"subtest":"alias")+"name__(\""+esc(name)+"\");\n");
                b.append("\ntry {\n");
                for (Command c : children) {
                	b.append(genDebugInfo(c));
                    try {
                    	b.append(c.javacode());
					} catch (Exception e) {
						e.printStackTrace();
						throwParseError(c.fileLineDetails);
					}
                    b.append("\n");
                }
                if(!isAnAlias) {
                	b.append("\npushResult(new SeleniumTestResult(get___d___(), this, ___lp___));");
                }
                String ex = state.evarname();
                b.append("\n}\ncatch(Throwable "+ex+")\n{");
                if(!isAnAlias) {
                	String oex = state.evarname();
	                /*b.append("\ntry{");
	                b.append(ex+".printStackTrace();\n");*/
	                String img = "getOutDir() + java.io.File.separator + \""+UUID.randomUUID().toString()+".png\"";
	                /*b.append("java.lang.System.out.println("+img+");");
	                ScreenshotCommand tm = new ScreenshotCommand(img, new Object[] {}, state, true);
	                b.append(tm.javacode());
	                b.append("}catch(java.io.IOException _ioe){}");*/
	                b.append("Throwable "+oex+" = "+ex+";if(!("+ex+" instanceof ValidSubTestException)) "+ex+" = new SubTestException(\""+name+"\", "+ex+");\n");
                	b.append("pushResult(new SeleniumTestResult(get___d___(), this, "+ex+", "+img+", ___lp___));");
                	b.append("if("+oex+" instanceof FailSubTestException) throw (FailSubTestException)"+oex+";\n");
                	b.append("else if(!("+oex+" instanceof ValidSubTestException)) throw (SubTestException)"+ex+";\n");
                } else {
                	b.append("throw "+ex+";\n");
                }
                b.append("\n}\nfinally {\n");
                //b.append("set__"+(!isAnAlias?"subtest":"alias")+"name__(null);\n");
                b.append("set__"+(!isAnAlias?"subtest":"alias")+"name__("+pstn+");\n");
                b.append("\n}");
            }
            b.append("return ___ce___;\n}\n");
            return b.toString();
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
				"Subtest definition",
				"\tsubtest \"name\" session-name|@session-id (args)\n\t{\n\t\tcode\n\t}",
				"where",
				"\tsession-name - the browser session name for which to run this sub test",
				"\tsession-id - the browser session id prefixed with @ for which to run this sub test",
				"\targs - the arguments passed to the subtest",
				"Examples :-",
				"\tsubtest \"sb1\" \"bs1\"\n\t{\n\t\tselect index@4 id@\"Location\"\n\t}",
				"\tsubtest \"sb1\" @1\n\t{\n\t\tselect index@4 id@\"Location\"\n\t}",
				"Alias definition",
				"\talias \"name\" (args)\n\t{\n\t\tcode\n\t}",
				"where",
				"\targs - the arguments passed to the alias",
				"Examples :-",
				"\talias \"cmd1\"\n\t{\n\t\tselect index@4 id@\"Location\"\n\t}"
        	};
        }
    }

    public static class VarCommand extends FindCommandImpl {
        String name;
        boolean isCntxtVar = false;
        PluginCommand pcomd;
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

    public static class JsVarCommand extends Command {
        String name;
        String script;
        JsVarCommand(String val, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            if(val.indexOf(" ")!=-1) {
                name = val.substring(0, val.indexOf(" ")).trim();
                script = val.substring(val.indexOf(" ")+1).trim();
                script = state.unsanitize(script);
                if(script.charAt(0)==script.charAt(script.length()-1)) {
                    if(script.charAt(0)=='"' || script.charAt(0)=='\'') {
                        script = script.substring(1, script.length()-1);
                    }
                }
            } else {
                //excep
            }
        }
        String toCmd() {
            return "jsvar " + name + " \"" + script + "\"";
        }
        String javacode() {
            return "Object " + name + " = null;\nif (___ocw___ instanceof JavascriptExecutor) {\n" + 
                    name + " = ((JavascriptExecutor)___ocw___).executeScript(evaluate(\""+esc(script)+"\"));\n}";
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
				"Js Variable definition",
				"\tjsvar {javascript statement returning value}",
				"Examples :-",
	    		"\tjsvar var1 'return \"123\"'",
	    		"\tjsvar var1 'return $(\"#elid\").val()'"
            };
        }
    }

    public static class ScreenshotCommand extends Command {
        String fpath;
        boolean isTmp = false;
        ScreenshotCommand(String code, Object[] cmdDetails, CommandState state, boolean isTmp) {
            super(cmdDetails, state);
            code = state.unsanitize(code);
            if(StringUtils.isNotBlank(code)) {
	            if(code.charAt(0)==code.charAt(code.length()-1)) {
	                if(code.charAt(0)=='"' || code.charAt(0)=='\'') {
	                    code = code.substring(1, code.length()-1);
	                }
	            }
            }
            this.fpath = code.trim().isEmpty()?System.nanoTime()+".png":code;
            this.isTmp = isTmp;
        }
        String toCmd() {
            return "screenshot \"" + fpath + "\"";
        }
        String javacode() {
            String filepath = "evaluate(\""+esc(fpath)+"\")";
            if(isTmp) {
            	filepath = "getOutDir() + java.io.File.separator + evaluate(\"" + esc(fpath) + "\")";
            }
            return "screenshotAsFile(get___d___(), "+filepath+");\n";
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
				"Take screenshot",
				"\tscreenshot {image-file-path-to-save-screenshot-to}",
				"Examples :-",
	    		"\tscreenshot",
	    		"\tscreenshot \"/path/to/image/file/file.png\""
            };
        }
    }

    public static class EleScreenshotCommand extends FindCommandImpl {
        String fpath;
        boolean isTmp = false;
        EleScreenshotCommand(String val, Object[] cmdDetails, CommandState state, boolean isTmp) {
            super(cmdDetails, state);
            String[] parts = val.split("[\t ]+");
            if(parts.length==1) {
                fpath = System.nanoTime()+".png";
                cond = new FindCommand(parts[0].trim(), fileLineDetails, state);
            } else {
                cond = new FindCommand(parts[1].trim(), fileLineDetails, state);
                fpath = state.unsanitize(parts[0].trim());
                if(fpath.charAt(0)==fpath.charAt(fpath.length()-1)) {
                    if(fpath.charAt(0)=='"' || fpath.charAt(0)=='\'') {
                        fpath = fpath.substring(1, fpath.length()-1);
                    }
                }
            }
            this.isTmp = isTmp;
        }
        String toCmd() {
            return "ele-screenshot \"" + fpath + "\"";
        }
        String javacode() {
            String filepath = "evaluate(\""+esc(fpath)+"\")";
            if(isTmp) {
            	filepath = "getOutDir() + java.io.File.separator + evaluate(\"" + esc(fpath) + "\")";
            }
            StringBuilder b = new StringBuilder();
            b.append(cond.javacodeonly());
            b.append("elementScreenshotAsFile(get___d___(), ___ce___.get(0), "+filepath+");\n");
            return b.toString();
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
				"Take element screenshot",
				"\tele-screenshot {element-selector} {optional image-file-path-to-save-screenshot-to}",
				"Examples :-",
	    		"\tele-screenshot id@'eleid'",
	    		"\tele-screenshot id@'eleid' '/path/to/image/file/file.png'"
            };
        }
    }

    public static class ValueCommand extends Command {
        String value;
        String value() {
            return value;
        }
        String toCmd() {
            return value;
        }
        String javacode() {
            return value;
        }
        ValueCommand(Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
				"Value",
				"\t{primtive-value}",
				"Examples :-",
	    		"\t'abc'",
	    		"\t123",
	    		"\ttrue"
            };
        }
    }

    public static class ValueListCommand extends Command {
        ValueListCommand(Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
        }
        String toCmd() {
            StringBuilder b = new StringBuilder();
            b.append("[\n");
            for (Command c : children) {
                b.append(c.toCmd());
                b.append("\n");
            }
            b.append("]");
            return b.toString();
        }
        String javacode() {
            return "";
        }
        List<String> getValues() {
            List<String> s = new ArrayList<String>();
            for (Command c : children) {
            	if(c instanceof ValueCommand) {
            		s.add(((ValueCommand)c).value);
            	}
            }
            return s;
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
				"Value List",
				"\t[{primtive-value},...,{primtive-value}]",
				"Examples :-",
	    		"\t['abc', 'sss']",
	    		"\t[123, 234]",
	    		"\t[true, false]"
            };
        }
    }

    public static class RequireCommand extends Command {
        String value;
        String toCmd() {
            return "require " + value;
        }
        String javacode() {
            StringBuilder b = new StringBuilder();
            String[] parts = value.split("[\t ]*,[\t ]*");
            if(parts.length>0) {
                for (String c : parts) {
                    String name = state.unsanitize(c);
                    if(name.charAt(0)==name.charAt(name.length()-1)) {
                        if(name.charAt(0)=='"' || name.charAt(0)=='\'') {
                            name = name.substring(1, name.length()-1);
                        }
                    }
                    b.append("import " + name + ";\n");
                }
                return b.toString();
            }
            return "";
        }
        RequireCommand(String value, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            this.value = value;
        }
        int weight() {
            return 1;
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
				"Specify Java Imports",
				"\trequire [{classname1},..{classnameN}]",
				"Examples :-",
	    		"\trequire java.util.Date",
	    		"\trequire [java.util.List, java.math.BigDecimal]"
            };
        }
    }

    public static class IncludeCommand extends Command {
        String name;
        IncludeCommand(String cmd, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            name = state.unsanitize(cmd);
            if(name.charAt(0)==name.charAt(name.length()-1)) {
                if(name.charAt(0)=='"' || name.charAt(0)=='\'') {
                    name = name.substring(1, name.length()-1);
                }
            }
        }
        String toCmd() {
            return "include " + name;
        }
        String javacode() {
            return "";
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
				"Include other seleasy scripts completely",
				"\tinclude {script-path}",
				"Examples :-",
	    		"\tinclude a/b/c/t1.sel",
	    		"\tinclude t2.sel"
            };
        }
    }

    public static class ImportCommand extends Command {
        String name;
        ImportCommand(String cmd, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            name = state.unsanitize(cmd);
            if(name.charAt(0)==name.charAt(name.length()-1)) {
                if(name.charAt(0)=='"' || name.charAt(0)=='\'') {
                    name = name.substring(1, name.length()-1);
                }
            }
        }
        String toCmd() {
            return "import " + name;
        }
        String javacode() {
            return "";
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
				"Import only subtests/aliases from other seleasy scripts",
				"\timport {script-path}",
				"Examples :-",
	    		"\timport a/b/c/t1.sel",
	    		"\timport t2.sel"
            };
        }
    }

    public static class ConfigPropsCommand extends Command {
        String name;
        ConfigPropsCommand(String cmd, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            name = state.unsanitize(cmd);
            if(name.charAt(0)==name.charAt(name.length()-1)) {
                if(name.charAt(0)=='"' || name.charAt(0)=='\'') {
                    name = name.substring(1, name.length()-1);
                }
            }
        }
        String toCmd() {
            return "config " + name;
        }
        String javacode() {
        	StringBuilder b = new StringBuilder();
            b.append("newTopLevelProvider();\n");
            @SuppressWarnings("unchecked")
            Enumeration<String> enums = (Enumeration<String>) state.configProps.propertyNames();
            while (enums.hasMoreElements()) {
            	String key = enums.nextElement();
                String value = state.configProps.getProperty(key);
                b.append("addToTopLevelProviderTestDataMap(\""+esc(key)+"\", \""+esc(value)+"\");\n"); 
            }
            return b.toString();
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
				"Import config properties file",
				"\tconfig {file-path}",
				"Examples :-",
	    		"\tconfig a/b/c/t1.props",
	    		"\tconfig t2.props"
            };
        }
    }

    public static class DynPropsCommand extends Command {
        String name;
        DynPropsCommand(String cmd, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            name = state.unsanitize(cmd);
            if(name.charAt(0)==name.charAt(name.length()-1)) {
                if(name.charAt(0)=='"' || name.charAt(0)=='\'') {
                    name = name.substring(1, name.length()-1);
                }
            }
        }
        String toCmd() {
            return "dynprops " + name;
        }
        String javacode() {
        	return "";
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
				"Import dynamic (code vars) properties file",
				"\tdynprops {file-path}",
				"Examples :-",
	    		"\tdynprops a/b/c/t1.props",
	    		"\tdynprops t2.props"
            };
        }
    }

    public static class WaitTillElementVisibleOrInvisibleCommand extends FindCommandImpl {
        boolean isVisible;
        int counter = state.timeoutNum;
        WaitTillElementVisibleOrInvisibleCommand(String time, String val, Object[] cmdDetails, CommandState state, boolean isVisible, boolean forced) {
            super(cmdDetails, state);
            this.isVisible = isVisible;
            try {
				if(Integer.valueOf(time)>0) {
					counter = Integer.valueOf(time);
					counter = (int)(counter*1000/state.timeoutSleepGranularity);
				}
			} catch (Exception e) {
			}
            String[] parts = val.trim().split("[\t ]+");
            if(forced) {
            	String[] parts1 = Arrays.asList(parts).toArray(new String[parts.length]);
            	if(parts1.length>2) {
            		if(!parts[0].toLowerCase().equals("actions")) {
            			if((parts[0].toLowerCase().matches(typeExStr) && !parts[0].toLowerCase().startsWith("randomize")) 
            					|| parts[0].toLowerCase().equals("select") || parts[0].toLowerCase().equals("upload")) {
	    	            	parts[0] = parts1[2];
	    	            	parts[1] = parts1[0];
	    	            	parts[2] = parts1[1];
            			} else {
            				parts[0] = parts1[1];
        	            	parts[1] = parts1[0];
            			}
            		}
            	} else if(parts1.length>1 && !parts[0].toLowerCase().equals("actions")) {
	            	parts[0] = parts1[1];
	            	parts[1] = parts1[0];
            	}
            }
            String cmd = parts[0];
            if(parts.length>1) {
                if (parts[1].toLowerCase().matches(typeExStr) || parts[1].toLowerCase().equals("select") 
                        || parts[1].toLowerCase().matches(clickExStr) || parts[1].toLowerCase().equals("hover")
                        || parts[1].toLowerCase().equals("hoverclick") || parts[1].toLowerCase().equals("clear")
                        || parts[1].toLowerCase().equals("submit") || parts[1].toLowerCase().equals("upload")) {
                    cmd = "";
                    for (int i = 1; i < parts.length; i++)
                    {
                        cmd += parts[i] + " ";
                    }
                    String cstr = parts[0];
                    if((parts[1].toLowerCase().matches(typeExStr) && !parts[1].toLowerCase().startsWith("randomize")) 
        					|| parts[1].toLowerCase().equals("select") || parts[1].toLowerCase().equals("upload")) {
                    	cstr = Stream.<String>of(parts).skip(3).collect(Collectors.joining(" "));
                    	cstr = parts[0] + " " +cstr;
                    	cmd = Stream.<String>of(parts).skip(1).limit(2).collect(Collectors.joining(" "));
                    } else if(!parts[1].toLowerCase().startsWith("randomize")) {
                    	cstr = Stream.<String>of(parts).skip(2).collect(Collectors.joining(" "));
                    	cstr = parts[0] + " " + cstr;
                    	cmd = Stream.<String>of(parts).skip(1).limit(1).collect(Collectors.joining(" "));
                    }
                    
                    if(!parts[0].equals("this") && !parts[0].equals("current") && !parts[0].equals("active") && parts[0].indexOf("@")==-1) {
                    	throwParseError(null, new RuntimeException("No selector condition specified"));
                    }
                    cond = new FindCommand(cstr, cmdDetails, state);
                    cmd = cmd.trim();
                    if(!cmd.isEmpty()) {
                        if (cmd.toLowerCase().toLowerCase().matches(typeSwStr) || cmd.toLowerCase().startsWith("select ") 
                        		|| cmd.toLowerCase().matches(clickExStr) || cmd.toLowerCase().equals("hover")
                                || cmd.toLowerCase().startsWith("hoverclick") || cmd.toLowerCase().equals("clear")
                                || cmd.toLowerCase().equals("submit") || cmd.toLowerCase().startsWith("upload ")) {
                            //cmd = unsanitize(cmd);
                            Command comd = handleActions(cmd, cond, cmdDetails, state);
                            children.add(comd);
                        }
                    }
                } else if(cmd.equalsIgnoreCase("actions")) {
                	Command comd = handleActions(val.trim(), null, cmdDetails, state);
                    children.add(comd);
                } else if(parts[1].toLowerCase().equalsIgnoreCase("actions")) {
                	if(!parts[0].equals("this") && !parts[0].equals("active") && !parts[0].equals("current") && parts[0].indexOf("@")==-1) {
                    	throwParseError(null, new RuntimeException("No selector condition specified"));
                    }
                	cmd = "actions moveto " + parts[0] + " ";
                    for (int i = 2; i < parts.length; i++)
                    {
                        cmd += parts[i] + " ";
                    }
                    //cond = new FindCommand(parts[0], cmdDetails, state);
                	Command comd = handleActions(cmd.trim(), null, cmdDetails, state);
                    children.add(comd);
                } else {
                    cmd = val;
                    cond = new FindCommand(cmd, cmdDetails, state);
                }
            } else if(cmd.equalsIgnoreCase("actions")) {
            	Command comd = handleActions(val.trim(), null, cmdDetails, state);
                children.add(comd);
            } else {
                cmd = val;
                cond = new FindCommand(parts[0], cmdDetails, state);
            }
        }
        String toCmd() {
            StringBuilder b = new StringBuilder();
            b.append("??"+(isVisible?"+":"-"));
            b.append(cond.toCmd());
            return b.toString();
        }
        String javacode() {
            StringBuilder b = new StringBuilder();
            if(cond!=null) {
            	b.append(cond.javacodeonlyNoAssert(children, false, counter, isVisible));
	            //String cntvar = state.varnamerandom();
	            //if(!isVisible) {
		            //b.append("int "+cntvar+" = 0;\n");
		            //b.append("\nwhile(true) {\n");
		            //b.append(cond.javacodeonlyNoAssert(children, false, counter, isVisible));
		            //b.append("if("+cond.getActionableVar()+"!=null)break;\n");
		            //b.append("sleep("+state.timeoutSleepGranularity+");\n");
		            //b.append("if("+cntvar+"++=="+counter+")throw new RuntimeException("+cond.getErr()+");\n");
		            //b.append("}\n");
	            //} else {
	            	//b.append("int "+cntvar+" = 0;\n");
		            //b.append("\nwhile(true) {\n");
		            //b.append("\nsleep(1000);\n");
		            //b.append(cond.javacodeonlyNoAssert(children, false, counter, isVisible));
		            //b.append("if("+cond.getActionableVar()+"==null)break;\n");
		            //b.append("sleep("+state.timeoutSleepGranularity+");\n");
		            //b.append("if("+cntvar+"++=="+counter+")throw new RuntimeException("+cond.getErr()+");\n");
		            //b.append("}\n");
	            //}
            }
            if(children!=null && children.size()>0) {
                Command c = children.get(0);
                if(c instanceof ActionsCommand || c instanceof RobotCommand) {
                    b.append(c.javacode());
                }
            }
            /*for (Command command : children) {
                if(command instanceof RandomizeCommand) {
                    b.append(((RandomizeCommand)command).javacodeonly(cond.getActionableVar()));
                } else if(command instanceof SelectCommand) {
                	b.append(((SelectCommand)command).javacodeonly(cond.getActionableVar()));
                } else if(command instanceof TypeCommand) {
                	b.append(((TypeCommand)command).javacodeonly(cond.getActionableVar()));
                } else if(command instanceof ClickCommand) {
                	b.append(((ClickCommand)command).javacodeonly(cond.getActionableVar()));
                } else if(command instanceof SubmitCommand) {
                	b.append(((SubmitCommand)command).javacodeonly(cond.getActionableVar()));
                } else if(command instanceof ClearCommand) {
                	b.append(((ClearCommand)command).javacodeonly(cond.getActionableVar()));
                }
            }
            b.append("\n");*/
            return b.toString();
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
				"Wait for element to be visible/invisible",
				"\t??(+|-) {find-expr}",
				"\t'+' - wait till element is visible",
				"\t'-' - wait till element is not visible",
				"Examples :-",
	    		"\t??+ id@'eleid'",
	    		"\t??- id@'eleid'",
            };
        }
    }
    
    /*public static class ValidateCommand extends FindCommandImpl {
        ValidateCommand(String time, String val, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            String[] parts = val.trim().split("[\t ]+");
            String cmd = parts[0];
            if(parts.length>1) {
                if (parts[1].toLowerCase().matches(typeExStr) || parts[1].toLowerCase().equals("select") 
                        || parts[1].toLowerCase().matches(clickExStr) || parts[1].toLowerCase().equals("hover")
                        || parts[1].toLowerCase().equals("hoverclick") || parts[1].toLowerCase().equals("clear")
                        || parts[1].toLowerCase().equals("submit") || parts[1].toLowerCase().equals("actions")) {
                    cmd = "";
                    for (int i = 1; i < parts.length; i++)
                    {
                        cmd += parts[i] + " ";
                    }
                    cond = time.equals("0")?new FindCommand(parts[0], cmdDetails, state):new WaitAndFindCommand(parts[0], Long.valueOf(time), cmdDetails, state);
                    cmd = cmd.trim();
                    if(!cmd.isEmpty()) {
                        if (cmd.toLowerCase().matches(typeSwStr) || cmd.toLowerCase().startsWith("select ") 
                        		|| cmd.toLowerCase().matches(clickSwStr) || cmd.toLowerCase().equals("hover")
                                || cmd.toLowerCase().startsWith("hoverclick") || cmd.toLowerCase().equals("clear")
                                || cmd.toLowerCase().equals("submit") || cmd.toLowerCase().startsWith("actions")) {
                            //cmd = unsanitize(cmd);
                            Command comd = handleActions(cmd, cond, cmdDetails, state);
                            children.add(comd);
                        }
                    }
                } else {
                    cmd = val;
                    cond = time.equals("0")?new FindCommand(cmd, cmdDetails, state):new WaitAndFindCommand(cmd, Long.valueOf(time), cmdDetails, state);
                }
            } else {
                cmd = val;
                cond = time.equals("0")?new FindCommand(cmd, cmdDetails, state):new WaitAndFindCommand(cmd, Long.valueOf(time), cmdDetails, state);
            }
        }
        String toCmd() {
            StringBuilder b = new StringBuilder();
            b.append("??");
            b.append(cond.toCmd());
            if(!children.isEmpty())
            {
                b.append("\n");
                for (Command c : children) {
                    b.append(c.toCmd());
                }
            }
            return b.toString();
        }
        String javacode() {
            StringBuilder b = new StringBuilder();
            b.append(cond.javacodeonly(children));
            //if(!(cond instanceof WaitAndFindCommand))
            //b.append("\nAssert.assertTrue("+cond.condition()+");");
            return b.toString();
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
				"Wait till an element is found and optionally execute actions on it",
				"\t??[:wait-time-in-secs] {find-expr} {optional action type|hover|hoverclick|click|clear|submit}",
				"Examples :-",
	    		"\t??10 id@'eleid'",
	    		"\t??20 id@'eleid' click",
	    		"\t??20 class@'eleid' type 'abc'",
            };
        }
    }*/

    public static class FindCommandImpl extends Command {
        FindCommand cond;
        FindCommandImpl(Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
        }
		public static String[] toSampleSelCmd() {
			return null;
		}
    }

    public static class IfCommand extends Command {
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
        static String getFp(List<FindCommand> conds, List<Command> children, boolean negation, CommandState state, String vrd) {
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
            b.append(getFp(conds, children, negation, state, vrd));
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

    public static class ElseIfCommand extends Command {
    	List<FindCommand> conds = new ArrayList<>();
        boolean negation;
        ElseIfCommand(String cmd, boolean negation, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            this.negation = negation;
            String[] parts = cmd.trim().split("[\t ]+");
            for (String part : parts) {
            	if(part!=null && !part.trim().isEmpty() && part.charAt(0)==part.charAt(part.length()-1)) {
                    if(part.charAt(0)=='"' || part.charAt(0)=='\'') {
                    	part = part.substring(1, part.length()-1);
                    }
                }
            	if(part==null || part.trim().isEmpty()) {
            		throwParseErrorS(cmdDetails, new RuntimeException("Else if command needs a condition"));
            	}
            	conds.add(new FindCommand(part, cmdDetails, state));
			}
        }
        String toCmd() {
            StringBuilder b = new StringBuilder();
            b.append(":?");
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
        String javacode() {
            StringBuilder b = new StringBuilder();
            String vrd = state.currvarnameifcnt();
            b.append(IfCommand.getFp(conds, children, negation, state, vrd));
            return b.toString();
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
        		"Else-If block, needs to be superseded by an If block",
        		"\t:? {find-expr}\n\t{\n\t\tcode\n\t}",
				"Examples :-",
				"\t:? xpath@\"ddd\"\n\t{\n\t\texec @print(\"else-if\")\n\t}"
            };
        }
    }

    public static class ElseCommand extends Command {
        ElseCommand(Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
        }
        String toCmd() {
            StringBuilder b = new StringBuilder();
            b.append(":");
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
                b.append("\n");
                for (Command c : children) {
                	b.append(genDebugInfo(c));
                    b.append(c.javacode());
                    b.append("\n");
                }
            }
            return b.toString();
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
        		"Else block, needs to be superseded by an If or Else-If block",
        		"\t:\n\t{\n\t\tcode\n\t}",
				"Examples :-",
				"\t:\n\t{\n\t\texec @print(\"else\")\n\t}"
            };
        }
    }

    /*public static class LoopCommand extends Command {
		FindCommand cond;
		LoopCommand() {}
		LoopCommand(Object[] cmdDetails) {
        }
		String toCmd() {
			StringBuilder b = new StringBuilder();
			b.append("#");
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
				b.append(cond.javacodeonly(null));
				String cvarname = state.currvarname();
				//pushSc();
				b.append("\nif("+cond.condition()+") {\n");
				b.append("\nfor(final WebElement " + varname() + " : " + cvarname + ") {\nint index = 0;\n");
				//b.append("final SearchContext "+state.currvarnamesc()+" = "+state.currvarname()+";");
				String vr = state.currvarname();
				b.append("\n@SuppressWarnings(\"serial\")\nList<WebElement> "+ varname()+" = new java.util.ArrayList<WebElement>(){{add("+vr+");}};");
				for (Command c : children) {
					b.append(c.javacode());
					b.append("\n");
				}
				b.append("index++;\n}\n}");
				//prevvarnamesc();
			}
			return b.toString();
		}
        public String toSampleSelCmd() {
            return "# {find-expr}\n\t{\n\t}";
        }
	}*/

    public static class ScopedLoopCommand extends Command {
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
                b.append("\n@SuppressWarnings(\"serial\")\nList<WebElement> "+ state.thisat()+" = new java.util.ArrayList<WebElement>(){{add("+vr+");}};");
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

    public static class ProviderLoopCommand extends Command {
    	enum ProviderType {
    		Counter, Provided, InlineSQL, InlineMongo, InlineFile
    	}
    	
        String name, dsn, query, filePath, collName;
        String[] vars, props;
        int index = Integer.MIN_VALUE;
        int end = Integer.MIN_VALUE;
        boolean isStateFul = false;
        ProviderType type = ProviderType.Provided;
        ProviderLoopCommand(String val, Object[] cmdDetails, CommandState state, boolean isStateFul, ProviderType type) {
            super(cmdDetails, state);
            this.isStateFul = isStateFul;
            this.type = type;
            String[] parts = val.trim().split("[\t ]+");
            if(type==ProviderType.Counter) {
            	if(parts.length==1 && !parts[0].trim().isEmpty()) {
            		try
                    {
            			index = 0;
                        end = Integer.valueOf(parts[0].trim());
                    }
                    catch (Exception e)
                    {
                        throwError(fileLineDetails, new RuntimeException("Counter end should be a number"));
                    }
            	} else if(parts.length==2) {
	                if(!parts[0].trim().isEmpty()) {
	                    try
	                    {
	                        index = Integer.valueOf(parts[0].trim());
	                    }
	                    catch (Exception e)
	                    {
	                        throwError(fileLineDetails, new RuntimeException("Counter start should be a number"));
	                    }
	                }
	                if(!parts[1].trim().isEmpty()) {
	                    try
	                    {
	                        end = Integer.valueOf(parts[1].trim());
	                    }
	                    catch (Exception e)
	                    {
	                        throwError(fileLineDetails, new RuntimeException("Counter end should be a number"));
	                    }
	                }
            	}
                if(index!=Integer.MIN_VALUE && end!=Integer.MIN_VALUE && index<end) {
                } else {
                    throwError(fileLineDetails, new RuntimeException("Counter needs both start and end values and start should be less than end"));
                }
            } else if(type==ProviderType.InlineSQL) {
                if(parts.length==3) {
                	String t = state.unsanitize(parts[0].trim());
                	if(t.charAt(0)==t.charAt(t.length()-1)) {
                        if(t.charAt(0)=='"' || t.charAt(0)=='\'') {
                        	t = t.substring(1, t.length()-1);
                        }
                    }
                	dsn = t.trim();
                	t = state.unsanitize(parts[1].trim());
                	if(t.charAt(0)==t.charAt(t.length()-1)) {
                        if(t.charAt(0)=='"' || t.charAt(0)=='\'') {
                        	t = t.substring(1, t.length()-1);
                        }
                    }
                	query = t.trim();
                	if(!query.toLowerCase().matches("^(select|with|call)[\t ]+.*") && type==ProviderType.InlineSQL) {
                		throwParseError(cmdDetails, new RuntimeException("SQL provider only allows select|with|call queries"));
                	}
                	t = state.unsanitize(parts[2].trim());
                	if(t.charAt(0)==t.charAt(t.length()-1)) {
                        if(t.charAt(0)=='"' || t.charAt(0)=='\'') {
                        	t = t.substring(1, t.length()-1);
                        }
                    }
                	vars= t.split(",");
                } else {
                    throwParseError(cmdDetails, new RuntimeException("SQL provider command should have the syntax `#sql {dsn} {query} {var1,var2,..}`"));
                }
            } else if(type==ProviderType.InlineMongo) {
                if(parts.length==3) {
                	String t = state.unsanitize(parts[0].trim());
                	if(t.charAt(0)==t.charAt(t.length()-1)) {
                        if(t.charAt(0)=='"' || t.charAt(0)=='\'') {
                        	t = t.substring(1, t.length()-1);
                        }
                    }
                	dsn = t.trim();
                	t = state.unsanitize(parts[1].trim());
                	if(t.charAt(0)==t.charAt(t.length()-1)) {
                        if(t.charAt(0)=='"' || t.charAt(0)=='\'') {
                        	t = t.substring(1, t.length()-1);
                        }
                    }
                	collName = t.trim();
                	t = state.unsanitize(parts[2].trim());
                	if(t.charAt(0)==t.charAt(t.length()-1)) {
                        if(t.charAt(0)=='"' || t.charAt(0)=='\'') {
                        	t = t.substring(1, t.length()-1);
                        }
                    }
                	query = t.trim();
            		try {
						WorkflowContextHandler.OM.readTree(query);
					} catch (Exception e) {
						throwParseError(cmdDetails, new RuntimeException("Mongo provider needs valid json queries"));
					}
                	t = state.unsanitize(parts[3].trim());
                	if(t.charAt(0)==t.charAt(t.length()-1)) {
                        if(t.charAt(0)=='"' || t.charAt(0)=='\'') {
                        	t = t.substring(1, t.length()-1);
                        }
                    }
                	vars= t.split(",");
                	t = state.unsanitize(parts[4].trim());
                	if(t.charAt(0)==t.charAt(t.length()-1)) {
                        if(t.charAt(0)=='"' || t.charAt(0)=='\'') {
                        	t = t.substring(1, t.length()-1);
                        }
                    }
                	props= t.split(",");
                	if(vars.length!=props.length) {
                		throwParseError(cmdDetails, new RuntimeException("Mongo provider needs same number of source/target property names"));
                	}
                } else {
                	if(type==ProviderType.InlineMongo)
                		throwParseError(cmdDetails, new RuntimeException("Mongo provider command should have the syntax `#mongo {dsn} {query} {var1,var2,..}`"));
                    throwParseError(cmdDetails, new RuntimeException("SQL provider command should have the syntax `#sql {dsn} {query} {var1,var2,..}`"));
                }
            } else if(type==ProviderType.InlineFile) {
                if(parts.length==2) {
                	String t = state.unsanitize(parts[0].trim());
                	if(t.charAt(0)==t.charAt(t.length()-1)) {
                        if(t.charAt(0)=='"' || t.charAt(0)=='\'') {
                        	t = t.substring(1, t.length()-1);
                        }
                    }
                	filePath = t.trim();
                	t = state.unsanitize(parts[1].trim());
                	if(t.charAt(0)==t.charAt(t.length()-1)) {
                        if(t.charAt(0)=='"' || t.charAt(0)=='\'') {
                        	t = t.substring(1, t.length()-1);
                        }
                    }
                	vars= t.split(",");
                } else {
                    throwParseError(cmdDetails, new RuntimeException("File provider command should have the syntax `#file {filepath} {var1,var2,..}`"));
                }
            } else {
                name = state.unsanitize(parts[0].trim());
                if(name.charAt(0)==name.charAt(name.length()-1)) {
                    if(name.charAt(0)=='"' || name.charAt(0)=='\'') {
                        name = name.substring(1, name.length()-1);
                    }
                }
                if(parts.length>1 && !parts[1].trim().isEmpty()) {
                	if(isStateFul) {
                		throwError(fileLineDetails, new RuntimeException("Stateful Providers cannot specify index/start"));
                	}
                    try
                    {
                        index = Integer.valueOf(parts[1].trim());
                    }
                    catch (Exception e)
                    {
                        throwError(fileLineDetails, new RuntimeException("Provider index/start should be a number"));
                    }
                }
                if(parts.length>2 && !parts[2].trim().isEmpty()) {
                	if(isStateFul) {
                		throwError(fileLineDetails, new RuntimeException("Stateful Providers cannot specify end"));
                	}
                    try
                    {
                        end = Integer.valueOf(parts[2].trim());
                    }
                    catch (Exception e)
                    {
                        throwError(fileLineDetails, new RuntimeException("Provider end should be a number"));
                    }
                }
            }
        }
        String toCmd() {
            StringBuilder b = new StringBuilder();
            if(type==ProviderType.InlineSQL) {
                b.append("#sql ");
                b.append(dsn);
                b.append(" ");
                b.append(query);
                b.append(" ");
                b.append(StringUtils.join(vars, ","));
            } else if(type==ProviderType.InlineMongo) {
                b.append("#mongo ");
                b.append(dsn);
                b.append(" ");
                b.append(collName);
                b.append(" ");
                b.append(query);
                b.append(" ");
                b.append(StringUtils.join(props, ","));
                b.append(" ");
                b.append(StringUtils.join(vars, ","));
            } else if(type==ProviderType.InlineFile) {
                b.append("#sql ");
                b.append(dsn);
                b.append(" ");
                b.append(query);
                b.append(" ");
                b.append(StringUtils.join(vars, ","));
            } else {
            	if(type==ProviderType.Counter) {
            		b.append("#counter ");
            		b.append(index);
            		b.append(" ");
            		b.append(end);
            	} else {
	            	if(isStateFul) {
		            	b.append("#provider-sf ");
		            	b.append(name);
		            } else {
		            	b.append("#provider ");
		            	b.append(name);
	            		b.append(" ");
	            		b.append(index);
	            		b.append(" ");
	            		b.append(end);
		            }
            	}
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
        String javacode() {
            StringBuilder b = new StringBuilder();
            String vrn1 = state.varname();
            b.append("Map<String, Object> "+vrn1+" = get__loopcontext__();\n");
            b.append("\nset__loopcontext__(\"\");\n");
            if(type==ProviderType.Counter)
            {
                state.loopCounter++;
                state.pushItr();
                String loopname = state.currvarnameitr();
                b.append("\nfor(int " + loopname + "="+index+";"+loopname+"<" + end + ";"+loopname+"++) {\n");
                b.append("\nadd__loopcontext__arg(\"a1\","+loopname+");\n");
                for (Command c : children) {
                	b.append(genDebugInfo(c));
                    b.append(c.javacode());
                    b.append("\n");
                }
                b.append("}");
                state.prevvarnameitr();
                state.loopCounter--;
            }
            else if(index>=0)
            {
                b.append("set__provname__(\"" + esc(name) + "\");\n");
                b.append("set__provpos__(\"" + esc(name) + "\", " + index + ");\n{\n");
                state.pushItr();
                String loopname = state.currvarnameitr();
                b.append("int "+loopname + " = " + index +";\n");
                b.append("\nadd__loopcontext__arg(\"a1\","+loopname+");\n");
                for (Command c : children) {
                	b.append(genDebugInfo(c));
                    b.append(c.javacode());
                    b.append("\n");
                }
                b.append("}");
                state.prevvarnameitr();
                b.append("rem__provname__(\"" + esc(name) + "\");\n");
            }
            else if(!children.isEmpty())
            {
                state.loopCounter++;
                if(type==ProviderType.InlineSQL) {
                	name = dsn+query;
                	b.append("int "+state.varname()+" = getSQLProviderTestDataMap(\""+esc(dsn)+"\", evaluate(\""+esc(query)+"\"), \""+esc(StringUtils.join(vars, ","))+"\").size();\n");
                } else if(type==ProviderType.InlineMongo) {
                	name = dsn+collName+query;
                	b.append("int "+state.varname()+" = getMongoProviderTestDataMap(\""+esc(dsn)+"\", evaluate(\""+esc(query)+"\"), \""+esc(collName)+"\", \""+esc(StringUtils.join(props, ","))+"\", \""+esc(StringUtils.join(vars, ","))+"\").size();\n");
                } else if(type==ProviderType.InlineFile) {
                	name = "file://" + filePath;
                	b.append("int "+state.varname()+" = getFileProviderTestDataMap(\""+esc(filePath)+"\", \""+esc(StringUtils.join(vars, ","))+"\").size();\n");
                } else {
                	b.append("int "+state.varname()+" = getProviderTestDataMap(\""+esc(name)+"\").size();\n");
                }
                b.append("set__provname__(\"" + esc(name) + "\");\n");
                String provLength = state.currvarname();
                state.pushItr();
                String loopname = state.currvarnameitr();
                if(isStateFul) {
                	b.append("initStateFulProvider(\""+esc(name)+"\");");
                }
                String indx = index>=0?index+"":"0";
                String endx = "((" + end + ">" + index + " && " + end + "<" + provLength + ")?"+end+":"+provLength+")";
                if(index==Integer.MIN_VALUE && end==Integer.MIN_VALUE) {
                	indx = "0";
                	endx = provLength;
                } else if(end==Integer.MIN_VALUE) {
                	endx = provLength;
                }
                if(isStateFul) {
                	b.append("\nfor(int " + loopname + "=preStateFulProvider(\""+esc(name)+"\", 0);"+loopname+"<" + provLength + ";"+loopname+"++) {\n");
                } else {
                	b.append("\nfor(int " + loopname + "="+indx+";"+loopname+"<" + endx + ";"+loopname+"++) {\n");
                }
                b.append("\nadd__loopcontext__arg(\"a1\","+loopname+");\n");
                b.append("set__provpos__(\"" + esc(name) + "\", " + loopname + ");\n");
                for (Command c : children) {
                	b.append(genDebugInfo(c));
                    b.append(c.javacode());
                    b.append("\n");
                }
                if(isStateFul) {
                	b.append("postStateFulProvider(\""+esc(name)+"\", "+loopname+");\n");
                }
                b.append("}");
                state.prevvarnameitr();
                b.append("rem__provname__(\"" + esc(name) + "\");\n");
                if(type==ProviderType.InlineSQL || type==ProviderType.InlineMongo || type==ProviderType.InlineFile) {
                	b.append("delProvider(\""+esc(name)+"\");\n");
                }
                state.loopCounter--;
            }
            b.append("\nset__loopcontext__("+vrn1+");\n");
            return b.toString();
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
        		"Provider Loop block",
        		"\t#provider {find-expr}\n\t{\n\t\tcode\n\t}",
        		"\t#p {find-expr}\n\t{\n\t\tcode\n\t}",
        		"\t#counter {start-index} {end-index}\n\t{\n\t\tcode\n\t}",
        		"\t#counter {end-index}\n\t{\n\t\tcode\n\t}",
        		"\t#c {start-index} {end-index}\n\t{\n\t\tcode\n\t}",
        		"\t#c {end-index}\n\t{\n\t\tcode\n\t}",
        		"\t#sql {dsn} {query} {var1,var2,..}\n\t{\n\t\tcode\n\t}",
        		"\t#mongo {dsn} {collection-name} {query} {col1,col2,..} {var1,var2,..}\n\t{\n\t\tcode\n\t}",
        		"\t#file {filepath} {var1,var2,..}\n\t{\n\t\tcode\n\t}",
				"Examples :-",
				"\t#provider \"provider-name\"\n\t{\n\t\texec @print(@index)\n\t\tclick xpath@'$provider-variable-1'\n\t}",
				"\t#provider \"provider-name\" 0 3\n\t{\n\t\texec @print(@index)\n\t\tclick xpath@'$provider-variable-1'\n\t}",
				"\t#provider \"provider-name\" 2\n\t{\n\t\texec @print(@index)\n\t\tclick xpath@'$provider-variable-1'\n\t}",
				"\t#p \"provider-name\"\n\t{\n\t\texec @print(@index)\n\t\tclick xpath@'$provider-variable-1'\n\t}",
				"\t#p \"provider-name\" 0 3\n\t{\n\t\texec @print(@index)\n\t\tclick xpath@'$provider-variable-1'\n\t}",
				"\t#p \"provider-name\" 2\n\t{\n\t\texec @print(@index)\n\t\tclick xpath@'$provider-variable-1'\n\t}",
				"\t#counter 0 5\n\t{\n\t\texec @print(@index)\n\t}",
				"\t#counter 5\n\t{\n\t\texec @print(@index)\n\t}",
				"\t#c 0 5\n\t{\n\t\texec @print(@index)\n\t}",
				"\t#c 5\n\t{\n\t\texec @print(@index)\n\t}",
				"\t#sql sqldsn \"select col1,col2 from some_table\" \"var1,var2\"\n\t{\n\t\texec @print(@index)\n\t\texec @print(@var1)\n\t\tclick xpath@'$var2'\n\t}",
				"\t#file \"/tmp/path/file.txt\" \"var1,var2\"\n\t{\n\t\texec @print(@index)\n\t\texec @print(@var1)\n\t\tclick xpath@'$var2'\n\t}"
            };
        }
    }

    public static class ReadFileCommand extends Command {
        String name;
        boolean isBinary = false;
        ReadFileCommand(String val, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            String[] parts = val.trim().split("[\t ]+");
            name = state.unsanitize(parts[0].trim());
            if(name.charAt(0)==name.charAt(name.length()-1)) {
                if(name.charAt(0)=='"' || name.charAt(0)=='\'') {
                    name = name.substring(1, name.length()-1);
                }
            }
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
            b.append("\n} catch(Exception e) {throw new RuntimeException(\"Unable to read file\");}");
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

    public static class FileVarCommand extends Command {
    	String regex;
    	String[] vars, positions;
        FileVarCommand(String val, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            String[] parts = val.trim().split("[\t ]+");
            if(parts.length==3) {
            	String t = state.unsanitize(parts[0].trim());
            	if(t.charAt(0)==t.charAt(t.length()-1)) {
                    if(t.charAt(0)=='"' || t.charAt(0)=='\'') {
                    	t = t.substring(1, t.length()-1);
                    }
                }
            	vars= t.split(",");
            	regex = state.unsanitize(parts[1].trim());
            	if(regex.charAt(0)==regex.charAt(regex.length()-1)) {
                    if(regex.charAt(0)=='"' || regex.charAt(0)=='\'') {
                    	regex = regex.substring(1, regex.length()-1);
                    }
                }
            	try {
					Pattern.compile(regex);
				} catch (Exception e) {
					throwParseError(cmdDetails, new RuntimeException("Invalid regex pattern specified " + regex));
				}
            	t = state.unsanitize(parts[2].trim());
            	if(t.charAt(0)==t.charAt(t.length()-1)) {
                    if(t.charAt(0)=='"' || t.charAt(0)=='\'') {
                    	t = t.substring(1, t.length()-1);
                    }
                }
            	positions= t.split(",");
            	if(vars.length!=positions.length) {
            		throwParseError(cmdDetails, new RuntimeException("File variable command needs length of vars and regex positions to be the same"));
            	}
            	/*for (String pos : positions) {
					if(!pos.matches("[0-9]+")) {
						throwParseError(cmdDetails, new RuntimeException("File variable command needs the regex group positions to be numeric"));
					}
				}*/
            } else {
                throwParseError(cmdDetails, new RuntimeException("File variable command should have the syntax `filevar {var1,var2,..} {regex} {var1-pattern,var2-pattern..}`"));
            }
        }
        String toCmd() {
            return "filevar " + StringUtils.join(vars, ",") + " regex " + StringUtils.join(positions, ",");
        }
        String javacode() {
        	String vrs = "new String[]{", pos = "new String[]{";
        	for (int i=0;i<vars.length;i++) {
        		vrs += "evaluate(\""+esc(vars[i])+"\"),";
        		pos += "\"" + esc(positions[i]) + "\",";
			}
        	vrs = vrs.substring(0, vrs.length()-1) + "}";
        	pos = pos.substring(0, pos.length()-1) + "}";
            return "if(get__loopcontext__var(\"ft\").equals(\"t\") && !get__loopcontext__var(\"n\").equals(\"n\")) setupFileVar(\""+esc(regex)+"\", "+vrs+", "+pos+");\n";
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
				"File Variable definition",
				"\tfilevar {var1,var2,..} {regex} {var1-pattern,var2-pattern..}",
				"Examples :-",
	    		"\tfilevar v1,@v2,@v3 \"(.*)|(.*),(.*)\" \"$1,ABC_$2,BCD$3EFG\"",
	    		"\tfilevar v1 \"([a-z]+)\" \"$1\""
            };
        }
    }

    public static class DSQueryCommand extends Command {
        String dsn, query;
        DSQueryCommand(String val, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            String[] parts = val.trim().split("[\t ]+");
            if(parts.length==2) {
                String t = state.unsanitize(parts[0].trim());
                if(t.charAt(0)==t.charAt(t.length()-1)) {
                    if(t.charAt(0)=='"' || t.charAt(0)=='\'') {
                    	t = t.substring(1, t.length()-1);
                    }
                }
                dsn = t;
                t = state.unsanitize(parts[1].trim());
                if(t.charAt(0)==t.charAt(t.length()-1)) {
                    if(t.charAt(0)=='"' || t.charAt(0)=='\'') {
                    	t = t.substring(1, t.length()-1);
                    }
                }
                query = t;
            } else {
            	throwParseError(cmdDetails, new RuntimeException("DS query command needs dsn name and query to execute"));
            }
        }
        String toCmd() {
            StringBuilder b = new StringBuilder();
            b.append("dsq ");
            b.append(dsn);
            b.append(" ");
            b.append(query);
            return b.toString();
        }
        String javacode() {
            return "executeQuery(\""+esc(dsn)+"\", evaluate(\""+esc(query)+"\"));\n";
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
        		"Data Source Query definition",
        		"\t#dsq {dsn} {query}",
				"Examples :-",
				"\t#dsq sqldb \"update some_table set cola = 'value' where colb = 1\""
            };
        }
    }

    public static class TransientVariableCommand extends Command {
        FindCommand cond;
        String varname;
        boolean isSuiteLevel;
        TransientVariableCommand(String val, Object[] cmdDetails, CommandState state, boolean isSuiteLevel) {
            super(cmdDetails, state);
            String[] parts = val.trim().split("[\t ]+");
            if(parts.length>=4) {
                parts[0] = parts[0].trim();
                varname = parts[0];
                varname = state.unsanitize(varname);
                if(varname.charAt(0)==varname.charAt(varname.length()-1)) {
                    if(varname.charAt(0)=='"' || varname.charAt(0)=='\'') {
                    	varname = varname.substring(1, varname.length()-1);
                    }
                }
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

    public static class TransientProviderCommand extends Command {
        FindCommand cond;
        String varname, value;
        boolean isLazy;
        TransientProviderCommand(String val, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            String[] parts = val.trim().split("[\t ]+");
            if(parts.length>=4) {
                parts[0] = parts[0].trim();
                value = parts[0];
                value = state.unsanitize(value);
                if(value.charAt(0)==value.charAt(value.length()-1)) {
                    if(value.charAt(0)=='"' || value.charAt(0)=='\'') {
                        value = value.substring(1, value.length()-1);
                    }
                }
                varname = parts[1];
                varname = state.unsanitize(varname);
                if(varname.charAt(0)==varname.charAt(varname.length()-1)) {
                    if(varname.charAt(0)=='"' || varname.charAt(0)=='\'') {
                        varname = varname.substring(1, varname.length()-1);
                    }
                }
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
            b.append(cond.javacodetrprovonly(provname));
            //b.append("if("+provname+"!=null && "+provname+".size()>0) break;\n");
            //b.append("sleep("+state.timeoutSleepGranularity+");\n");
            //b.append("if("+cntvar+"++=="+counter+")throw new RuntimeException("+cond.getErr()+");\n");
            //b.append("}\n");
            String loopname = state.varname();
            List<String> ssl = Arrays.asList(varname.split("[\t ]*,[\t ]*"));
            b.append("\nfor(int " + loopname + "=0;"+loopname+"<" + provname + ".size();"+loopname+"++) {");
            b.append("\nMap<String, String> __mp = new java.util.HashMap<String, String>();");
            for (int i=0;i<ssl.size();i++)
            {
                b.append("\n__mp.put(\""+ssl.get(i)+"\", " + provname + ".get(" + loopname + ")["+i+"]);\n"); 
            }
            b.append("getProviderTestDataMap(\""+value+"\").add(__mp);\n");
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

    public static class DriverCommand extends Command {
        SeleniumDriverConfig config;
        DriverCommand(SeleniumDriverConfig config, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            this.config = config;
        }
        String name() {
            return name;
        }
        String toCmd() {
            return "";
        }
		public static String[] toSampleSelCmd() {
			return null;
		}
        String javacode() {
            StringBuilder b = new StringBuilder();
            String rUrl = config.getUrl();
            if(StringUtils.isBlank(rUrl)) {
            	rUrl = "http://127.0.0.1:4723/wd/hub";
            }
            
            b.append("boolean logconsole = false, logdebug = false, lognw = false;\n");
            List<String> rprops = new ArrayList<>();
            for (Map.Entry<String, String> e : config.getCapabilities().entrySet())
            {
            	if(e.getKey().equalsIgnoreCase("logconsole") && e.getValue().toLowerCase().trim().matches("1|on|true")) {
            		b.append("\nlogconsole = true;\n");
            		rprops.add("logconsole");
            	} else if(e.getKey().equalsIgnoreCase("logdebug") && e.getValue().toLowerCase().trim().matches("1|on|true")) {
            		b.append("\nlogdebug = true;\n");
            		rprops.add("logdebug");
                } else if(e.getKey().equalsIgnoreCase("lognw") && e.getValue().toLowerCase().trim().matches("1|on|true")) {
            		b.append("\nlognw = true;\n");
            		rprops.add("lognw");
                }
            }
            for (String rp : rprops) {
            	config.getCapabilities().remove(rp);
			}
            b.append("/*GATF_ST_DRIVER_INIT_"+(config.getName().toUpperCase())+"*/");
            if(config.getName().startsWith("chrome")) {
                b.append("org.openqa.selenium.chrome.ChromeOptions ___dc___ = new org.openqa.selenium.chrome.ChromeOptions();\n");
                b.append("___dc___.setCapability(org.openqa.selenium.chrome.ChromeOptions.LOGGING_PREFS, ___lp___);\n");
                if(config.getCapabilities()!=null)
                {
                    for (Map.Entry<String, String> e : config.getCapabilities().entrySet())
                    {
                        b.append("___dc___.setCapability(\""+esc(e.getKey())+"\", \""+esc(e.getValue())+"\");\n");
                    }
                }
                b.append("___dc___.setCapability(CapabilityType.ACCEPT_INSECURE_CERTS, true);\n");
                
                if(StringUtils.isBlank(config.getArguments())) {
                	config.setArguments("");
                } else {
					String cargs = config.getArguments().trim();
					cargs = cargs.replace("--ignore-certificate-errors", "");
					cargs = cargs.replaceAll("\\s+", " ");
					config.setArguments(cargs);
                }
                config.setArguments(config.getArguments()+ " --ignore-certificate-errors");
                //if(System.getProperty("webdriver.http.factory")==null) {
                //	config.setArguments(config.getArguments()+ " --remote-allow-origins=*");
                //}
                if(!config.getName().equals("chrome")) {
                	String cargs = config.getArguments();
					cargs = cargs.replace("--no-sandbox", "").replace("--disable-dev-shm-usage", "");
					cargs += " --disable-dev-shm-usage --no-sandbox";
					cargs = cargs.replaceAll("\\s+", " ");
					config.setArguments(cargs);
                }
                b.append("___dc___.addArguments(\""+esc(config.getArguments())+"\".trim());\n");
                
                
                if(config.getProperties()!=null)
                {
                	b.append("Map<String, Object> __prefs = new java.util.HashMap<String, Object>();\n");
                	for (Map.Entry<String, String> e : config.getProperties().entrySet())
                    {
                        b.append("__prefs.put(\""+esc(e.getKey())+"\", \""+esc(e.getValue())+"\");\n");
                    }
                	b.append("___dc___.setExperimentalOption(\"prefs\", __prefs);\n");
                }
                b.append("String __bkey__ = addWdm(\""+config.getName().toLowerCase().trim()+"\", ___dc___);\n");
            	if(!config.getName().equals("chrome")) {
            		b.append("set___d___(getDockerDriver(__bkey__));\n");
            	} else {
            		b.append("set___d___(new org.openqa.selenium.chrome.ChromeDriver(___dc___));\n");
            	}
            } else if(config.getName().startsWith("firefox")) {
            	b.append("org.openqa.selenium.firefox.FirefoxProfile ___dcprf___ = new org.openqa.selenium.firefox.FirefoxProfile();\n");
                b.append("org.openqa.selenium.firefox.FirefoxOptions ___dc___ = new org.openqa.selenium.firefox.FirefoxOptions();\n");
                //b.append("___dc___.setCapability(CapabilityType.LOGGING_PREFS, ___lp___);\n");
                if(config.getCapabilities()!=null)
                {
                    for (Map.Entry<String, String> e : config.getCapabilities().entrySet())
                    {
                        b.append("___dc___.setCapability(\""+esc(e.getKey())+"\", \""+esc(e.getValue())+"\");\n");
                    }
                }
                b.append("___dc___.getProfile().setAcceptUntrustedCertificates(true);\n");
                b.append("___dc___.getProfile().setAssumeUntrustedCertificateIssuer(true);\n");
                b.append("___dc___.setCapability(CapabilityType.ACCEPT_INSECURE_CERTS, true);\n");
                if(!config.getName().equals("firefox")) {
                	config.setArguments(config.getArguments()+ " --disable-dev-shm-usage");
                }
                if(StringUtils.isNotBlank(config.getArguments()))
                {
                	b.append("___dc___.addArguments(\""+esc(config.getArguments())+"\".trim());\n");
                }
                if(config.getProperties()!=null)
                {
                	for (Map.Entry<String, String> e : config.getProperties().entrySet())
                    {
                        b.append("___dcprf___.setPreference(\""+esc(e.getKey())+"\", \""+esc(e.getValue())+"\");\n");
                    }
                }
                b.append("___dc___.setProfile(___dcprf___);\n");
                b.append("String __bkey__ = addWdm(\""+config.getName().toLowerCase().trim()+"\", ___dc___);\n");
            	if(!config.getName().equals("firefox")) {
            		b.append("set___d___(getDockerDriver(__bkey__));\n");
            	} else {
            		b.append("set___d___(new org.openqa.selenium.firefox.FirefoxDriver(___dc___));\n");
            	}
            } else if(config.getName().equalsIgnoreCase("ie")) {
                b.append("org.openqa.selenium.ie.InternetExplorerOptions ___dc___ = new org.openqa.selenium.ie.InternetExplorerOptions();\n");
                //b.append("___dc___.setCapability(CapabilityType.LOGGING_PREFS, ___lp___);\n");
                if(config.getCapabilities()!=null)
                {
                    for (Map.Entry<String, String> e : config.getCapabilities().entrySet())
                    {
                        b.append("___dc___.setCapability(\""+esc(e.getKey())+"\", \""+esc(e.getValue())+"\");\n");
                    }
                }
                if(StringUtils.isNotBlank(config.getArguments()))
                {
                	b.append("___dc___.addCommandSwitches(\""+esc(config.getArguments())+"\".split(\"\\\\s+\"));\n");
                }
                b.append("___dc___.setCapability(CapabilityType.ACCEPT_INSECURE_CERTS, true);\n");
                b.append("String __bkey__ = addWdm(\""+config.getName().toLowerCase().trim()+"\", ___dc___);\n");
            	if(!config.getName().equals("ie")) {
            		b.append("set___d___(getDockerDriver(__bkey__));\n");
            	} else {
            		b.append("set___d___(new org.openqa.selenium.ie.InternetExplorerDriver(___dc___));\n");
            	}
            } else if(config.getName().equalsIgnoreCase("safari")) {
                b.append("org.openqa.selenium.safari.SafariOptions ___dc___ = new org.openqa.selenium.safari.SafariOptions();\n");
                //b.append("___dc___.setCapability(CapabilityType.LOGGING_PREFS, ___lp___);\n");
                if(config.getCapabilities()!=null)
                {
                    for (Map.Entry<String, String> e : config.getCapabilities().entrySet())
                    {
                        b.append("___dc___.setCapability(\""+esc(e.getKey())+"\", \""+esc(e.getValue())+"\");\n");
                    }
                }
                b.append("___dc___.setCapability(CapabilityType.ACCEPT_INSECURE_CERTS, true);\n");
                b.append("String __bkey__ = addWdm(\""+config.getName().toLowerCase().trim()+"\", ___dc___);\n");
            	if(!config.getName().equals("safari")) {
            		b.append("set___d___(getDockerDriver(__bkey__));\n");
            	} else {
            		b.append("set___d___(new org.openqa.selenium.safari.SafariDriver(___dc___));\n");
            	}
            } else if(config.getName().equalsIgnoreCase("opera")) {
                b.append("org.openqa.selenium.opera.OperaOptions ___dc___ = new org.openqa.selenium.opera.OperaOptions();\n");
                //b.append("___dc___.setCapability(CapabilityType.LOGGING_PREFS, ___lp___);\n");
                if(config.getCapabilities()!=null)
                {
                    for (Map.Entry<String, String> e : config.getCapabilities().entrySet())
                    {
                        b.append("___dc___.setCapability(\""+esc(e.getKey())+"\", \""+esc(e.getValue())+"\");\n");
                    }
                }
                if(StringUtils.isNotBlank(config.getArguments()))
                {
                	b.append("___dc___.addCommandSwitches(\""+esc(config.getArguments())+"\".split(\"\\\\s+\"));\n");
                }
                b.append("___dc___.setCapability(CapabilityType.ACCEPT_INSECURE_CERTS, true);\n");
                b.append("String __bkey__ = addWdm(\""+config.getName().toLowerCase().trim()+"\", ___dc___);\n");
            	if(!config.getName().equals("opera")) {
            		b.append("set___d___(getDockerDriver(__bkey__));\n");
            	} else {
            		b.append("set___d___(new org.openqa.selenium.opera.OperaDriver(___dc___));\n");
            	}
            } else if(config.getName().equalsIgnoreCase("edge")) {
            	b.append("String __bkey__ = addWdm(\""+config.getName().toLowerCase().trim()+"\");\n");
                b.append("org.openqa.selenium.edge.EdgeOptions ___dc___ = new org.openqa.selenium.edge.EdgeOptions();\n");
                b.append("___dc___.setCapability(org.openqa.selenium.edge.EdgeOptions.LOGGING_PREFS, ___lp___);\n");
                if(config.getCapabilities()!=null)
                {
                    for (Map.Entry<String, String> e : config.getCapabilities().entrySet())
                    {
                        b.append("___dc___.setCapability(\""+esc(e.getKey())+"\", \""+esc(e.getValue())+"\");\n");
                    }
                }
                b.append("___dc___.setCapability(CapabilityType.ACCEPT_INSECURE_CERTS, true);\n");
                b.append("String __bkey__ = addWdm(\""+config.getName().toLowerCase().trim()+"\", ___dc___);\n");
            	if(!config.getName().equals("edge")) {
            		b.append("set___d___(getDockerDriver(__bkey__));\n");
            	} else {
            		b.append("set___d___(new org.openqa.selenium.edge.EdgeDriver(___dc___));\n");
            	}
            } else if(config.getName().equalsIgnoreCase("remote-chrome")) {
                b.append("DesiredCapabilities ___dc___ = DesiredCapabilities.chrome();\n");
                b.append("___dc___.setCapability(org.openqa.selenium.chrome.ChromeOptions.LOGGING_PREFS, ___lp___);\n");
                if(config.getCapabilities()!=null)
                {
                    for (Map.Entry<String, String> e : config.getCapabilities().entrySet())
                    {
                        b.append("___dc___.setCapability(\""+esc(e.getKey())+"\", \""+esc(e.getValue())+"\");\n");
                    }
                }
                b.append("set___d___(new RemoteWebDriver(new java.net.URL(\""+rUrl+"\"), ___dc___));\n");
            }  else if(config.getName().equalsIgnoreCase("remote-firefox")) {
                b.append("DesiredCapabilities ___dc___ = DesiredCapabilities.firefox();\n");
                //b.append("___dc___.setCapability(CapabilityType.LOGGING_PREFS, ___lp___);\n");
                if(config.getCapabilities()!=null)
                {
                    for (Map.Entry<String, String> e : config.getCapabilities().entrySet())
                    {
                        b.append("___dc___.setCapability(\""+esc(e.getKey())+"\", \""+esc(e.getValue())+"\");\n");
                    }
                }
                b.append("set___d___(new RemoteWebDriver(new java.net.URL(\""+rUrl+"\"), ___dc___));\n");
            } else if(config.getName().equalsIgnoreCase("remote-ie")) {
                b.append("DesiredCapabilities ___dc___ = DesiredCapabilities.internetExplorer();\n");
                //b.append("___dc___.setCapability(CapabilityType.LOGGING_PREFS, ___lp___);\n");
                if(config.getCapabilities()!=null)
                {
                    for (Map.Entry<String, String> e : config.getCapabilities().entrySet())
                    {
                        b.append("___dc___.setCapability(\""+esc(e.getKey())+"\", \""+esc(e.getValue())+"\");\n");
                    }
                }
                b.append("set___d___(new RemoteWebDriver(new java.net.URL(\""+rUrl+"\"), ___dc___));\n");
            } else if(config.getName().equalsIgnoreCase("remote-edge")) {
                b.append("DesiredCapabilities ___dc___ = DesiredCapabilities.edge();\n");
                b.append("___dc___.setCapability(org.openqa.selenium.edge.EdgeOptions.LOGGING_PREFS, ___lp___);\n");
                if(config.getCapabilities()!=null)
                {
                    for (Map.Entry<String, String> e : config.getCapabilities().entrySet())
                    {
                        b.append("___dc___.setCapability(\""+esc(e.getKey())+"\", \""+esc(e.getValue())+"\");\n");
                    }
                }
                b.append("set___d___(new RemoteWebDriver(new java.net.URL(\""+rUrl+"\"), ___dc___));\n");
            } else if(config.getName().equalsIgnoreCase("remote-opera")) {
                b.append("DesiredCapabilities ___dc___ = DesiredCapabilities.operaBlink();\n");
                //b.append("___dc___.setCapability(CapabilityType.LOGGING_PREFS, ___lp___);\n");
                if(config.getCapabilities()!=null)
                {
                    for (Map.Entry<String, String> e : config.getCapabilities().entrySet())
                    {
                        b.append("___dc___.setCapability(\""+esc(e.getKey())+"\", \""+esc(e.getValue())+"\");\n");
                    }
                }
                b.append("set___d___(new RemoteWebDriver(new java.net.URL(\""+rUrl+"\"), ___dc___));\n");
            }  else if(config.getName().equalsIgnoreCase("remote-safari")) {
                b.append("DesiredCapabilities ___dc___ = DesiredCapabilities.safari();\n");
                //b.append("___dc___.setCapability(CapabilityType.LOGGING_PREFS, ___lp___);\n");
                if(config.getCapabilities()!=null)
                {
                    for (Map.Entry<String, String> e : config.getCapabilities().entrySet())
                    {
                        b.append("___dc___.setCapability(\""+esc(e.getKey())+"\", \""+esc(e.getValue())+"\");\n");
                    }
                }
                b.append("set___d___(new RemoteWebDriver(new java.net.URL(\""+rUrl+"\"), ___dc___));\n");
            } else if(config.getName().equalsIgnoreCase("appium-android")) {
                b.append("DesiredCapabilities ___dc___ = new DesiredCapabilities(org.openqa.selenium.remote.BrowserType.ANDROID, \""+config.getVersion()+"\", org.openqa.selenium.Platform.ANDROID);\n");
                //b.append("___dc___.setCapability(CapabilityType.LOGGING_PREFS, ___lp___);\n");
                if(config.getCapabilities()!=null)
                {
                    for (Map.Entry<String, String> e : config.getCapabilities().entrySet())
                    {
                        b.append("___dc___.setCapability(\""+esc(e.getKey())+"\", \""+esc(e.getValue())+"\");\n");
                    }
                }
                b.append("set___d___(new io.appium.java_client.android.AndroidDriver(new java.net.URL(\""+rUrl+"\"), ___dc___));\n");
            } else if(config.getName().equalsIgnoreCase("appium-ios")) {
                b.append("DesiredCapabilities ___dc___ = new DesiredCapabilities(\"ios\", \""+config.getVersion()+"\", org.openqa.selenium.Platform.MAC);\n");
                //b.append("___dc___.setCapability(CapabilityType.LOGGING_PREFS, ___lp___);\n");
                if(config.getCapabilities()!=null)
                {
                    for (Map.Entry<String, String> e : config.getCapabilities().entrySet())
                    {
                        b.append("___dc___.setCapability(\""+esc(e.getKey())+"\", \""+esc(e.getValue())+"\");\n");
                    }
                }
                b.append("set___d___(new io.appium.java_client.ios.IOSDriver(new java.net.URL(\""+rUrl+"\"), ___dc___));\n");
            } /*else if(config.getName().equalsIgnoreCase("selendroid")) {
                b.append("SelendroidCapabilities  ___dc___ = new SelendroidCapabilities(\"\");\n");
                b.append("___dc___.setCapability(CapabilityType.LOGGING_PREFS, ___lp___);\n");
                if(config.getCapabilities()!=null)
                {
                    for (Map.Entry<String, String> e : config.getCapabilities().entrySet())
                    {
                        b.append("___dc___.setCapability(\""+esc(e.getKey())+"\", \""+esc(e.getValue())+"\");\n");
                    }
                }
                b.append("set___d___(new io.selendroid.client.SelendroidDriver(___dc___));\n");
            }*/ //else if(config.getName().equalsIgnoreCase("ios-driver")) {
                //b.append("DesiredCapabilities ___dc___ = new DesiredCapabilities(org.openqa.selenium.remote.BrowserType.ANDROID, \""+version+"\", org.openqa.selenium.Platform.ANDROID);\n");
                //b.append("___dc___.setCapability(CapabilityType.LOGGING_PREFS, ___lp___);\n");
                //if(config.getCapabilities()!=null)
                //{
                //    for (Map.Entry<String, String> e : config.getCapabilities().entrySet())
                //    {
                //        b.append("___dc___.setCapability(\""+esc(e.getKey())+"\", \""+esc(e.getValue())+"\");\n");
                //    }
                //}
                //b.append("get___d___() = new org.openqa.selenium.remote.RemoteWebDriver(\""+rUrl+"\", ___dc___);\n");
            //}
            else {
                throwError(fileLineDetails, new RuntimeException("Invalid driver configuration specified, no browser found with name " + config.getName()));
            }
            b.append("initBrowser(get___d___(), logconsole, logdebug, lognw);\n");
            
            boolean isDocker = "true".equalsIgnoreCase(System.getProperty("D_DOCKER")!=null?System.getProperty("D_DOCKER"):System.getenv("D_DOCKER"));
            if(isDocker) {
            	String tmp = System.getProperty("SCREEN_WIDTH")!=null?System.getProperty("SCREEN_WIDTH"):System.getenv("SCREEN_WIDTH");
            	String tmp1 = System.getProperty("SCREEN_HEIGHT")!=null?System.getProperty("SCREEN_HEIGHT"):System.getenv("SCREEN_HEIGHT");
            	if(StringUtils.isNotBlank(tmp) && StringUtils.isNotBlank(tmp1)) {
            		try {
						SettingCommand ws = new SettingCommand("width "+Integer.valueOf(tmp.trim()), new Object[] {}, state);
						SettingCommand wh = new SettingCommand("height "+Integer.valueOf(tmp1.trim()), new Object[] {}, state);
						b.append("WebDriver ___cw___ = get___d___();");
						b.append(ws.javacode());
						b.append(wh.javacode());
					} catch (Exception e) {
					}
            	}
            }
            return b.toString();
        }
        int weight() {
            return 2;
        }
    }

    public static class BrowserCommand extends Command {
        String sessionName;
        int sessionId = -1;
        BrowserCommand(String val, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            String[] parts = val.trim().split("[\t ]+");
            if(parts.length>=1) {
                name = unSantizedUnQuoted(parts[0].trim(), state);
                if(parts.length>1) {
                    sessionName = unSantizedUnQuoted(parts[1].trim(), state);
                }
            } else {
                //excep
            }
        }
        String name() {
            return name;
        }

        String toCmd() {
            return "open " + name;
        }
        int weight() {
            return 2;
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
        		"Open Browser",
        		"\topen {chrome|chrome-dkr|chrome-rec|chrome-hdl|firefox|firefox-dkr|firefox-rec|firefox-hdl|opera|opera-dkr|opera-rec|opera-hdl|ie|edge|safari|appium-android|appium-ios..} {optional session-name}",
				"\t\t*-hdl -- Headless Docker based WebDriver",
				"\t\t*-dkr -- VNC Docker based WebDriver",
				"\t\t*-rec -- VNC Docker based WebDriver With Recording",
        		"Examples :-",
				"\topen chrome",
				"\topen firefox \"my-ff-sess\"",
            };
        }
    }

    public static class LayerCommand extends Command {
        LayerCommand(String val, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            this.name = val;
            if(!val.trim().isEmpty()) {
                state.layers.add(unSantizedUnQuoted(val, state));
            }
        }
        String toCmd() {
            return "layer " + name;
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
        		"Define Screen No-activity layers",
        		"\tlayer {find-expr}",
				"Examples :-",
				"\tlayer id@\"loader-icon\"",
				"\tlayer id@\"overlay-div\"",
            };
        }
        public String javacode() {
            return "";
        }
    }

    public static class TimeoutCommand extends Command {
    	int ifCounter = 1;
    	int counter = 60;
    	long sleepGranularity = 1000;
    	TimeoutCommand(String val, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            String[] parts = val.trim().split("[\t ]+");
            if(parts.length==0) {
            	throwParseError(null, new RuntimeException("Please specify timeout in seconds"));
            }
            if(parts.length>0) {
            	if(StringUtils.isBlank(parts[0])) {
            		throwParseError(null, new RuntimeException("Please specify timeout in seconds"));
            	}
            	try {
            		counter = Integer.valueOf(parts[0]);
				} catch (Exception e) {
					throwParseError(null, new RuntimeException("Please specify timeout in seconds"));
				}
            }
            if(counter<2) {
            	throwParseError(null, new RuntimeException("Timeout should be more than or equal to 2"));
            }
            if(parts.length>1 && StringUtils.isNotBlank(parts[1])) {
            	try {
            		ifCounter = Integer.valueOf(parts[1]);
				} catch (Exception e) {
					throwParseError(null, new RuntimeException("Please specify timeout in seconds"));
				}
            }
            if(ifCounter<1) {
            	throwParseError(null, new RuntimeException("Timeout should be more than or equal to 1"));
            }
            if(parts.length>2 && StringUtils.isNotBlank(parts[2])) {
            	try {
            		sleepGranularity = Long.valueOf(parts[2]);
				} catch (Exception e) {
					throwParseError(null, new RuntimeException("Please specify proper sleep granularity (should be in milliseconds)"));
				}
            }
            
            if(sleepGranularity!=1000) {
            	counter = (int)(counter*1000/sleepGranularity);
            	ifCounter = (int)(ifCounter*1000/sleepGranularity);
            }
        }
        String toCmd() {
            return "timeout " + counter + " " + ifCounter + " " + sleepGranularity;
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
        		"Define Global timeout",
        		"\ttimeout {timeout-secs} {if-timeout-secs}} {sleep-granularity-millis}",
				"Examples :-",
				"\ttimeout 60 1 1000",
            };
        }
        public String javacode() {
            return "";
        }
    }

    public static class ModeCommand extends Command {
    	boolean enableGlobalTimeouts = true;
    	long sleepGranularity = 1000;
        ModeCommand(String val, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            String[] parts = val.trim().split("[\t ]+");
            if(parts.length>=1) {
                name = state.unsanitize(parts[0].trim());
                if(parts.length>1 && !parts[1].toLowerCase().matches("true|1|yes")) {
                	enableGlobalTimeouts = false;
                }
            } else {
                //excep
            }
        }
        String name() {
            return name;
        }
        boolean getEnableGlobalTimeouts() {
            return enableGlobalTimeouts;
        }

        String toCmd() {
            return "mode " + name + (enableGlobalTimeouts?"true":"false");
        }
        int weight() {
            return 2;
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
        		"Define test mode",
        		"\tmode {normal|integration} {true|false}",
				"Examples :-",
				"\tmode normal",
				"\tmode integration",
            };
        }
    }

    public static class FrameCommand extends Command {
        FrameCommand(String cmd, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            name = state.unsanitize(cmd);
            if(name.charAt(0)==name.charAt(name.length()-1)) {
                if(name.charAt(0)=='"' || name.charAt(0)=='\'') {
                    name = name.substring(1, name.length()-1);
                }
            }
        }
        String name() {
            return name;
        }
        String toCmd() {
            return "frame " + name;
        }
        String javacode() {
        	if(children.size()>0) {
        		StringBuilder b = new StringBuilder();
        		String cntvar = state.varnamerandom();
        		b.append("int "+cntvar+" = 0;\n");
                b.append("\nwhile(true) {\n");
        		b.append("\ntry {\n");
        		b.append("switchToFrame(___ocw___, \""+esc(name)+"\");\n");
        		for (Command c : children) {
                	b.append(genDebugInfo(c));
                    b.append(c.javacode());
                    b.append("\n");
                }
        		b.append("\nbreak;\n} catch(Exception e){}\n");
                b.append("sleep("+state.timeoutSleepGranularity+");\n");
                b.append("if("+cntvar+"++=="+state.timeoutNum+")throw new RuntimeException(\"Unable to move to frame ("+name+")\");\n");
                b.append("}\n");
        		b.append("switchToFrame(___ocw___, \"\");\n");//Switch to main frame
        		return b.toString();
        	}
            return "switchToFrame(___ocw___, \""+esc(name)+"\");\n";
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
        		"Select frame",
        		"\tframe main|parent|1..N|{some-name}",
				"Examples :-",
				"\tframe main",
				"\tframe parent",
				"\tframe 2",
				"\tframe \"my-frame\"",
            };
        }
    }

    public static class WindowCommand extends Command {
        WindowCommand(String cmd, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            name = cmd;
            name = state.unsanitize(name);
            if(StringUtils.isNotBlank(name) && name.charAt(0)==name.charAt(name.length()-1)) {
                if(name.charAt(0)=='"' || name.charAt(0)=='\'') {
                    name = name.substring(1, name.length()-1);
                }
            }
            name = name.trim();
        }
        String name() {
            return name;
        }
        String toCmd() {
            return "window " + name;
        }
        String javacode() {
        	if(name.equalsIgnoreCase("main") || name.equalsIgnoreCase("0")) {
        		StringBuilder b = new StringBuilder();
        		b.append("window(0);___cw___ = get___d___();\n___sc___1 = ___cw___;");
	            for (Command c : children) {
                	b.append(genDebugInfo(c));
                    b.append(c.javacode());
                    b.append("\n");
                }
	            return b.toString();
            } else {
            	StringBuilder b = new StringBuilder();
        		b.append("\ntry {\n");
	            if(name.equals("")) {
	        		b.append("newWindow(___lp___);\n");
	        		
	            } else {
	                try {
	                    int index = Integer.parseInt(name);
	                    b.append("window("+index+");___cw___ = get___d___();\n___sc___1 = ___cw___;\n");
	                } catch (Exception e) {
	                    throwParseError(null, new RuntimeException("Invalid window number specified"));
	                    return null;
	                }
	            }
	            for (Command c : children) {
                	b.append(genDebugInfo(c));
                    b.append(c.javacode());
                    b.append("\n");
                }
        		b.append("\n\n} finally {\n");
        		if(children.size()>0) {
        			b.append("window(0);___cw___ = get___d___();\n___sc___1 = ___cw___;\n");
        		}
        		b.append("}\n");
        		return b.toString();
            }
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
        		"Select/Open Window",
        		"\twindow {optional main|0}",
				"Examples :-",
				"\twindow",
				"\twindow 0",
				"\twindow main",
            };
        }
    }

    public static class TabCommand extends Command {
        TabCommand(String cmd, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            name = cmd;
            name = state.unsanitize(name);
            if(StringUtils.isNotBlank(name) && name.charAt(0)==name.charAt(name.length()-1)) {
                if(name.charAt(0)=='"' || name.charAt(0)=='\'') {
                    name = name.substring(1, name.length()-1);
                }
            }
            if("main".equalsIgnoreCase(name)) {
            	name = "0";
            }
        }
        String name() {
            return name;
        }
        String toCmd() {
            return "tab " + name;
        }
        String javacode() {
            if(name.trim().isEmpty() || name.equalsIgnoreCase("0")) {
            	StringBuilder b = new StringBuilder();
            	b.append("___ocw___.switchTo().window(___ocw___.getWindowHandles().iterator().next());\n___cw___ = ___ocw___;\n___sc___1 = ___cw___;\n");
            	if(children.size()>0) {
            		for (Command c : children) {
                    	b.append(genDebugInfo(c));
                        b.append(c.javacode());
                        b.append("\n");
                    }
            	}
            	return b.toString();
            } else {
            	if(children.size()==0) {
            		StringBuilder b = new StringBuilder();
                    String cntvar = state.varnamerandom();
                    b.append("int "+cntvar+" = 0;\n");
                    b.append("\nwhile(true) {\n");
                    b.append("\ntry {\n");
                    try {
                        int index = Integer.parseInt(name);
                        String acvn = state.varname();
                        String whl = "List<String> "+acvn+" = new java.util.ArrayList<String> (___ocw___.getWindowHandles());\n"
                                + "if("+state.currvarname()+"!=null && "+index+">=0 && "+state.currvarname()+".size()>"+index+")\n{\n";
                        b.append(whl + "___cw___ = ___ocw___.switchTo().window("+acvn+".get("+index+"));\n___sc___1 = ___cw___;\nbreak;}");
                    } catch (Exception e) {
                        b.append("___cw___ = ___ocw___.switchTo().window(\""+esc(name)+"\");\n___sc___1 = ___cw___;\nbreak;\n");
                    }
                    b.append("\n} catch(Exception e){}\n");
                    b.append("sleep("+state.timeoutSleepGranularity+");\n");
                    b.append("if("+cntvar+"++=="+state.timeoutNum+")throw new RuntimeException(\"Unable to move to tab ("+name+")\");\n");
                    b.append("}\n");
                    return b.toString();
            	}
            	StringBuilder b = new StringBuilder();
                String cntvar = state.varnamerandom();
                b.append("int "+cntvar+" = 0;\n");
                b.append("\nwhile(true) {\n");
                b.append("\ntry {\n");
                try {
                    int index = Integer.parseInt(name);
                    String acvn = state.varname();
                    String whl = "List<String> "+acvn+" = new java.util.ArrayList<String> (___ocw___.getWindowHandles());\n"
                            + "if("+state.currvarname()+"!=null && "+index+">=0 && "+state.currvarname()+".size()>"+index+")\n{\n";
                    b.append(whl + "___cw___ = ___ocw___.switchTo().window("+acvn+".get("+index+"));\n___sc___1 = ___cw___;}");
                } catch (Exception e) {
                    b.append("___cw___ = ___ocw___.switchTo().window(\""+esc(name)+"\");\n___sc___1 = ___cw___;\n");
                }

            	if(children.size()>0) {
            		for (Command c : children) {
                    	b.append(genDebugInfo(c));
                        b.append(c.javacode());
                        b.append("\n");
                    }
            	}
                b.append("\nbreak;\n} catch(Exception e){}\n");
                b.append("sleep("+state.timeoutSleepGranularity+");\n");
                b.append("if("+cntvar+"++=="+state.timeoutNum+")throw new RuntimeException(\"Unable to move to tab ("+name+")\");\n");
                b.append("}\n");
                b.append("___ocw___.switchTo().window(___ocw___.getWindowHandles().iterator().next());\n___cw___ = ___ocw___;\n___sc___1 = ___cw___;\n");
                return b.toString();
            }
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
        		"Select frame",
        		"\ttab main|0..N|{some-name}",
				"Examples :-",
				"\ttab main",
				"\ttab 0",
				"\ttab 2",
				"\ttab \"my-frame\"",
            };
        }
    }

    public static class BackCommand extends Command {
        String toCmd() {
            return "back";
        }
        String javacode() {
            return "___cw___.navigate().back();";
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
        		"Navigate Back/Previous",
        		"\tback",
            };
        }
        BackCommand(Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
        }
    }

    public static class ForwardCommand extends Command {
        String toCmd() {
            return "forward";
        }
        String javacode() {
            return "___cw___.navigate().forward();";
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
        		"Navigate Forward/Next",
        		"\tforward",
            };
        }
        ForwardCommand(Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
        }
    }

    public static class RefreshCommand extends Command {
        String toCmd() {
            return "refresh";
        }
        String javacode() {
            return "refresh(___cw___);\n";
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
        		"Refresh window",
        		"\trefresh",
            };
        }
        RefreshCommand(Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
        }
    }

    public static class CloseCommand extends Command {
        String toCmd() {
            return "close" + (StringUtils.isNotBlank(name)?(" "+name):"");
        }
        String javacode() {
        	if(StringUtils.isNotBlank(name)) {
        		StringBuilder b = new StringBuilder();
        		String acvn = state.varname();
        		String whl = "List<String> "+acvn+" = new java.util.ArrayList<String> (___ocw___.getWindowHandles());\n";
        		if(name.equals("others")) {
        			String cntvar = state.varnamerandom();
	        		whl += "for(int "+cntvar+"=1;"+cntvar+"<"+state.currvarname()+".size();"+cntvar+"++)\n{\n";
	                b.append(whl + "___ocw___.switchTo().window("+acvn+".get("+cntvar+")).close();\n}");
        		} else {
	        		whl += "if("+state.currvarname()+"!=null && "+name+">=0 && "+state.currvarname()+".size()>"+name+")\n{\n";
	                b.append(whl + "___ocw___.switchTo().window("+acvn+".get("+name+")).close();\n}");
        		}
        		return b.toString();
        	}
            return "___cw___.close();";
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
        		"Close window",
        		"\tclose {tab-index|others}?",
        		"Examples :-",
        		"\tclose",
        		"\tclose 1",
        		"\tclose others",
            };
        }
        CloseCommand(String cmd, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            name = state.unsanitize(cmd.trim());
            if(StringUtils.isNotBlank(name)) {
	            if(name.charAt(0)==name.charAt(name.length()-1)) {
	                if(name.charAt(0)=='"' || name.charAt(0)=='\'') {
	                    name = name.substring(1, name.length()-1);
	                }
	            }
	            if(name.equalsIgnoreCase("others")) {
	            	name = "others";
	            } else {
		            try {
		        		Integer.parseInt(name);
					} catch (Exception e) {
						throwParseError(cmdDetails, new RuntimeException("close tab index should be an integer"));
					}
	            }
            }
        }
    }

    public static class WaitForReady extends Command {
        String toCmd() {
            return "waitready";
        }
        String javacode() {
            return "waitForReady(___cw___);\n";
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
        		"Wait Till Browser/Dowument is ready, called after application level logouts or reloads",
        		"\twaitready",
            };
        }
        WaitForReady(Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
        }
    }

    public static class MaximizeCommand extends Command {
        String toCmd() {
            return "maximize";
        }
        String javacode() {
            return "___cw___.manage().window().maximize();";
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
        		"Maximize window",
        		"\tmaximize",
            };
        }
        MaximizeCommand(Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
        }
    }

    public static class GotoCommand extends Command {
        String url;
        String url() {
            return url;
        }
        String toCmd() {
            return "goto " + url;
        }
        String javacode() {
            //String exnm = state.evarname();
            return "navigateTo(___cw___, evaluate(\""+state.sanitize(url).replace("\\", "\\\\")+"\"));\n";
        }
        GotoCommand(Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
        		"Open URL in window",
        		"\tgoto {url}",
        		"Examples :-",
        		"\tgoto http://abc.com/testpage.html"
            };
        }
    }

    public static class FailCommand extends ValueCommand {
        String toCmd() {
            return "fail \"" + value + "\"";
        }
        String javacode() {
            return "if(true)\n{\nthrow new FailSubTestException(\""+esc(value)+"\");\n}";
        }
        FailCommand(String cmd, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            if(cmd.equals("")) {
            	value = "Test/Subtest Failed";
            } else {
	            value = state.unsanitize(cmd);
	            if(value.charAt(0)==value.charAt(value.length()-1)) {
	                if(value.charAt(0)=='"' || value.charAt(0)=='\'') {
	                    value = value.substring(1, value.length()-1);
	                }
	            }
            }
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
        		"Fail test/sub-test",
        		"\tfail {error string}",
        		"Examples :-",
        		"\tfail \"Test failed\"",
        		"\tfail \"Sub-Test failed\"",
            };
        }
    }

    public static class WarnCommand extends ValueCommand {
        String toCmd() {
            return "warn \"" + value + "\"";
        }
        String javacode() {
            return "if(true)\n{\nthrow new WarnSubTestException(\""+esc(value)+"\");\n}";
        }
        WarnCommand(String cmd, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            if(cmd.equals("")) {
            	value = "Test/Subtest Failed";
            } else {
	            value = state.unsanitize(cmd);
	            if(value.charAt(0)==value.charAt(value.length()-1)) {
	                if(value.charAt(0)=='"' || value.charAt(0)=='\'') {
	                    value = value.substring(1, value.length()-1);
	                }
	            }
            }
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
        		"Fail test/sub-test and continue",
        		"\twarn {error string}",
        		"Examples :-",
        		"\twarn \"Test failed\"",
        		"\twarn \"Sub-Test failed\"",
            };
        }
    }

    public static class PassCommand extends ValueCommand {
        String toCmd() {
            return "pass \"" + value + "\"";
        }
        String javacode() {
            return "if(true)\n{\nthrow new PassSubTestException(\""+esc(value)+"\");\n}";
        }
        PassCommand(String cmd, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            value = state.unsanitize(cmd);
            if(!value.trim().isEmpty()) {
	            if(value.charAt(0)==value.charAt(value.length()-1)) {
	                if(value.charAt(0)=='"' || value.charAt(0)=='\'') {
	                    value = value.substring(1, value.length()-1);
	                }
	            }
            }
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
        		"Pass test/sub-test",
        		"\tpass {some string}?",
        		"Examples :-",
        		"\tpass",
        		"\tpass \"Test passed\"",
        		"\tpass \"Sub-Test passed\"",
            };
        }
    }

    public static class SettingCommand extends Command {
        String type;
        String value;
        SettingCommand(String val, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            String[] parts = val.trim().split("[\t ]+");
            if(parts.length>1) {
                parts[0] = parts[0].trim();
                type = parts[0];
                value = state.unsanitize(parts[1].trim());
                if(type.equalsIgnoreCase("width") || type.equalsIgnoreCase("height") || type.equalsIgnoreCase("posx") || type.equalsIgnoreCase("posy")) {
                	try {
                		Integer.parseInt(value);
					} catch (Exception e) {
						throwParseError(cmdDetails, new RuntimeException("Value needs to be an integer for (width|height|posx|posy) settings"));
					}
                }
            } else {
                //excep
            }
        }
        String value() {
            return value;
        }
        String toCmd() {
            return "setting " + type + " " + value;
        }
        String javacode() {
            String cvn = state.varname();
            if(type.equalsIgnoreCase("width")) {
                return "Dimension "+cvn+" = ___cw___.manage().window().getSize();\n___cw___.manage().window().setSize(new Dimension("+value+", "+cvn+".getHeight()));";
            } else if(type.equalsIgnoreCase("height")) {
                return "Dimension "+cvn+" = ___cw___.manage().window().getSize();\n___cw___.manage().window().setSize(new Dimension("+cvn+".getWidth(), "+value+"));";
            } else if(type.equalsIgnoreCase("posx")) {
                return "Point "+cvn+" = ___cw___.manage().window().getPosition();\n___cw___.manage().window().setPosition(new Point("+value+", "+cvn+".getY()));";
            } else if(type.equalsIgnoreCase("posy")) {
                return "Point "+cvn+" = ___cw___.manage().window().getPosition();\n___cw___.manage().window().setPosition(new Point("+cvn+".getX(), "+value+"));";
            } else if(type.equalsIgnoreCase("clk_focus")) {
                return "addSettingVal(___cw___, \""+esc(type)+"\", \""+esc(value)+"\");";
            }
            return "";
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
        		"Add Settings",
        		"\tsetting {width|height|posx|posy|clk_focus} {value} {value}?",
        		"Examples :-",
        		"\tsetting width 100",
        		"\tsetting height 100",
        		"\tsetting posx 100",
        		"\tsetting posy 100",
        		"\tsetting clk_focus true",
            };
        }
    }

    public static class CapabilitySetPropertyCommand extends Command {
        String type;
        String value;
        CapabilitySetPropertyCommand(String val, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            String[] parts = val.trim().split("[\t ]+");
            if(parts.length>1) {
                parts[0] = parts[0].trim();
                type = parts[0];
                value = state.unsanitize(parts[1].trim());
            } else {
                //excep
            }
        }
        String value() {
            return value;
        }
        String toCmd() {
            return "capability_set "+type+" " + value;
        }
        String javacode() {
            if(StringUtils.isNotBlank(type)) {
                return "___dc___.setCapability(\""+esc(type)+"\", \""+esc(value)+"\");\n";
            }
            return "";
        }
		public static String[] toSampleSelCmd() {
			return null;
		}
    }

    static String esc(String cmd) {
        String blid = "######" + UUID.randomUUID().toString() + "######";
        String nlid = "######" + UUID.randomUUID().toString() + "######";
        String tbid = "######" + UUID.randomUUID().toString() + "######";
        String crid = "######" + UUID.randomUUID().toString() + "######";
        String ffid = "######" + UUID.randomUUID().toString() + "######";
        String dqid = "######" + UUID.randomUUID().toString() + "######";
        String tmp = cmd.replace("\\\\b", blid).replace("\\\\n", nlid).replace("\\\\t", tbid)
                .replace("\\\\r", crid).replace("\\\\f", ffid).replace("\\\"", dqid);
        tmp = tmp.replace("\\", "\\\\").replace("\"", "\\\"");
        tmp = tmp.replace(blid, "\\\\b").replace(nlid, "\\\\n").replace(tbid, "\\\\t")
                .replace(crid, "\\\\r").replace(ffid, "\\\\f").replace(dqid, "\\\"");
        return tmp;
    }

    public static class FindCommand extends Command {
        String by, classifier, relative = "", by1 = "", classifier1 = "", subselector, condvar = "true", topele, rtl, precond = "", postcond = "", cfiltvar = null, oper = null, operval = null, eval = null, browserScope = "", sessionScope = "";
        boolean suppressErr = false, byselsame = false;
        String by() {
            return by;
        }
        String classifier() {
            return classifier;
        }
        String subselector() {
            return subselector;
        }
        String topele() {
            return topele;
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
        		"Find Expression",
        		"\t{eval|browser-scope|session-scope|relative}? {id|name|class|xpath|tag|cssselector|css|text|partialLinkText|linkText|active|jq|$|jquery|this|current}(@selector) (title|currentUrl|pageSource|width|height|xpos|ypos|alerttext) {matching-value|matching-value-in-list}",
            };
        }
        FindCommand(String val, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            String[] parts = val.trim().split("[\t ]+");
            if(parts[0].equalsIgnoreCase("eval")) {
            	eval = val.trim().substring(5).trim();
            	by = "";
            	classifier = "";
            } else if(parts[0].equalsIgnoreCase("browser-scope")) {
            	browserScope = val.trim().substring(14).trim();
            	by = "";
            	classifier = "";
            } else if(parts[0].equalsIgnoreCase("session-scope")) {
            	sessionScope = val.trim().substring(14).trim();
            	by = "";
            	classifier = "";
            } else if(parts[0].equalsIgnoreCase("relative")) {
            	if(parts.length<4) {
            		throwError(cmdDetails, new RuntimeException("Invalid relative selector expression, relative selector should confirm to `relative xpath@expr {above|below|leftof|rightof|near} xpath@expr1`"));
            	}
            	if(parts[1].indexOf("@")!=-1) {
                    by1 = parts[1].substring(0, parts[1].indexOf("@")).trim();
                    classifier1 = parts[1].substring(parts[1].indexOf("@")+1).trim();
                    classifier1 = state.unsanitize(classifier1);
                    if(classifier1.charAt(0)==classifier1.charAt(classifier1.length()-1)) {
                        if(classifier1.charAt(0)=='"' || classifier1.charAt(0)=='\'') {
                        	classifier1 = classifier1.substring(1, classifier1.length()-1);
                        }
                    }
            	}
            	if(by1.isEmpty() || classifier1.isEmpty()) {
            		throwError(cmdDetails, new RuntimeException("Invalid relative selector command, relative selector should confirm to `relative xpath@expr {above|below|leftof|rightof|near} xpath@expr1`"));
            	}
            	if(!parts[2].equalsIgnoreCase("leftof") && !parts[2].equalsIgnoreCase("rightof") && !parts[2].equalsIgnoreCase("above")
            			&& !parts[2].equalsIgnoreCase("below") && !parts[2].equalsIgnoreCase("near")) {
            		throwError(cmdDetails, new RuntimeException("Invalid relative selector command, relative selector should confirm to `relative xpath@expr {above|below|leftof|rightof|near} xpath@expr1`"));
            	}
            	if(parts[3].indexOf("@")!=-1) {
                    by = parts[3].substring(0, parts[3].indexOf("@")).trim();
                    classifier = parts[3].substring(parts[3].indexOf("@")+1).trim();
                    classifier = state.unsanitize(classifier);
                    if(classifier.charAt(0)==classifier.charAt(classifier.length()-1)) {
                        if(classifier.charAt(0)=='"' || classifier.charAt(0)=='\'') {
                            classifier = classifier.substring(1, classifier.length()-1);
                        }
                    }
            	}
            	if(by.isEmpty() || classifier.isEmpty()) {
            		throwError(cmdDetails, new RuntimeException("Invalid relative selector command, relative selector should confirm to `relative xpath@expr {above|below|leftof|rightof|near} xpath@expr1`"));
            	}
            	relative = parts[2].trim();
            } else {
	            if(parts.length>=1) {
	                parts[0] = parts[0].trim();
	                int nxindx = 2;
	                if(parts[0].indexOf("@")!=-1) {
	                    by = parts[0].substring(0, parts[0].indexOf("@")).trim();
	                    classifier = parts[0].substring(parts[0].indexOf("@")+1).trim();
	                    classifier = state.unsanitize(classifier);
	                    if(classifier.charAt(0)==classifier.charAt(classifier.length()-1)) {
	                        if(classifier.charAt(0)=='"' || classifier.charAt(0)=='\'') {
	                            classifier = classifier.substring(1, classifier.length()-1);
	                        }
	                    }
		                
	                    if(parts.length>1) {
	                        subselector = parts[1].trim();
	                        subselector = state.unsanitize(subselector);
	                    }
	                    
	                    if(by.equalsIgnoreCase("xpath")) {
	                    	try {
								state.factory.newXPath().compile(classifier);
							} catch (XPathExpressionException e) {
								throwError(cmdDetails, new RuntimeException("Invalid xpath expression " + classifier));
							}
	                    }
	                } else {
	                	by = parts[0];
	                    classifier = by.equalsIgnoreCase("this")?"":null;
	                    classifier = by.equalsIgnoreCase("current")?"":null;
	                    classifier = by.equalsIgnoreCase("active")?"":null;
	                    subselector = (!by.equalsIgnoreCase("this") && !by.equalsIgnoreCase("current") && !by.equalsIgnoreCase("active"))?state.unsanitize(by):null;
	                    byselsame = true;
	                    nxindx = 1;
	                }
	                
	                by = state.unsanitize(by);
	                
                    if(parts.length>nxindx) {
                        oper = state.unsanitize(parts[nxindx].trim()) + (parts.length>nxindx+1?state.unsanitize(parts[nxindx+1].trim()):"");
                        //oper = state.unsanitize(oper);
                        String cval = oper;
                        if(oper.startsWith("<=")) {
                            cval = oper.substring(2);
                            precond = "compareTo";
                            postcond = "<=0";
                            oper = "<=";
                        } else if(oper.startsWith(">=")) {
                            cval = oper.substring(2);
                            precond = "compareTo";
                            postcond = ">=0";
                            oper = ">=";
                        } else if(oper.startsWith("=") || oper.startsWith("==")) {
                            cval = oper.substring(1);
                            precond = "compareTo";
                            postcond = "==0";
                            oper = "=";
                        } else if(oper.startsWith("<")) {
                            cval = oper.substring(1);
                            precond = "compareTo";
                            postcond = "<0";
                            oper = "<";
                        } else if(oper.startsWith(">")) {
                            cval = oper.substring(1);
                            precond = "compareTo";
                            postcond = ">0";
                            oper = ">";
                        } else if(oper.startsWith("!=")) {
                            cval = oper.substring(2);
                            precond = "compareTo";
                            postcond = "!=0";
                            oper = "!=";
                        } else if(oper.startsWith("%") && oper.endsWith("%")) {
                            cval = oper.substring(1, oper.length()-1);
                            precond = "contains";
                        } else if(oper.startsWith("%")) {
                            cval = oper.substring(1);
                            precond = "startsWith";
                        } else if(oper.endsWith("%")) {
                            cval = oper.substring(0, oper.length()-1);
                            precond = "endsWith";
                        }
                        if(!cval.trim().isEmpty()) {
	                        ValueCommand vc = new ValueCommand(cmdDetails, state);
	                        vc.value = cval.trim();
	                        if(!cval.isEmpty() && (vc.value.charAt(0)=='"' || vc.value.charAt(0)=='\'')) {
	                            vc.value = vc.value.substring(1, vc.value.length()-1);
	                        }
	                        operval = vc.value;
                        }
                        //children.add(vc);
                    }
	            } else {
	                //excep
	            }
            }
        }
        String toCmd() {
        	String logbc = esc(by) + (classifier!=null?("@'" + esc(classifier)):"");
            return " " + logbc + (StringUtils.isEmpty(subselector)?"":" \"" + subselector + "\"");
        }
        String getErr() {
        	String logbc = esc(by) + (classifier!=null?("@'" + esc(classifier)):"");
            return "\"Element not found by selector " + logbc + " at line number "+fileLineDetails[1]+" \"";
        }
        String javacodetrprovonly(String varname) {
            //if(!varonly) javacodeonlyint(children);
            String sc = state.currvarnamesc();
            String value = null, values = "new String[]{}", action = null, tvalue = null, ssubselector = subselector, sclassifier = classifier;
            /*if(children!=null && children.size()>0) {
                Command c = children.get(0);
                if(c instanceof ValueCommand) {
                   value = "evaluate(\"" + esc(((ValueCommand)c).value) + "\")";
                } else if(c instanceof ValueListCommand) {
                } else if(c instanceof ClickCommand || c instanceof HoverCommand || c instanceof HoverAndClickCommand
                        || c instanceof ClearCommand || c instanceof SubmitCommand || c instanceof TypeCommand
                        || c instanceof SelectCommand || c instanceof DoubleClickCommand || c instanceof UploadCommand) {
                    if(FindCommandImpl.class.isAssignableFrom(c.getClass()) && ((FindCommandImpl)c).cond==null) {
                        action = "\"" + c.getClass().getSimpleName().toLowerCase().replace("command", "") + "\"";
                        if(c instanceof TypeCommand) {
                        	TypeCommand typ = (TypeCommand)c;
                            if(StringUtils.isNotBlank(typ.qualifier)) {
                            	action = "\"" + typ.type  + typ.qualifier + "\"";
                            }
                            if(typ.type.equalsIgnoreCase("type") || typ.type.equalsIgnoreCase("sendkeys")) {
                            	tvalue = "evaluate(\"" + typ.value + "\")";
                            } else if(typ.type.equalsIgnoreCase("chord")) {
                            	tvalue = "Keys.chord(\"" + typ.value + "\")";
                            } else {
                            	tvalue = "evaluate(\"" + esc(typ.v1) + "\")" + "+ \" \"";
                            	if(typ.v2!=null) tvalue += " + evaluate(\"" + esc(typ.v2) + "\")" + " + \" \"";
                            	if(typ.v3!=null) tvalue += " + evaluate(\"" + esc(typ.v3) + "\")";
                            }
                        } else if(c instanceof UploadCommand) {
                            tvalue = "evaluate(\"" + ((UploadCommand)c).value + "\")";
                        } else if(c instanceof SelectCommand) {
                        	SelectCommand scc = (SelectCommand)c;
                        	ssubselector = scc.by;
                        	value = "evaluate(\"" + esc(scc.value) + "\")";
                        } else if(c instanceof ClickCommand) {
                        	ClickCommand typ = (ClickCommand)c;
                            if(StringUtils.isNotBlank(typ.name)) {
                            	action = "\"click"+typ.name+"\"";
                            }
                        }
                    }
                }
            }*/
            if(ssubselector!=null) {
                ssubselector = "evaluate(\""+esc(subselector)+"\")";
            }
            if(sclassifier!=null) {
                sclassifier =  "new String[] {evaluate(\""+esc(sclassifier)+"\"), evaluate(\""+esc(classifier1)+"\")}";
            } else {
            	sclassifier =  "new String[] {\"\", \"\"}";
            }
            rtl = varname;
            String wel = by.equals("this")?state.currthisat():"___ce___";
            wel = by.equals("active")?"___cw___.switchTo().activeElement()":wel;
            String logbc = esc(by) + (classifier!=null?("@'" + esc(classifier)):"");
            return varname + " = transientProviderDataWL(___cw___, "+sc+", "+wel+", "+state.timeoutNum+", \""+relative+"\", "+sclassifier+", new String[]{\""+esc(by)+"\", \""+esc(by1)+"\"}, "
                    + ssubselector + ", "+byselsame+", "+value+", "+values+", "
                    + action + ", null, "+tvalue+", \"Element not found by selector " 
                    + logbc + " at line number "+fileLineDetails[1]+" \", false, "+state.timeoutSleepGranularity+", false, "+state.getLayers()+");\n";
        }
        /*String javacodevaronly() {
            String sc = state.currvarnamesc();
            String value = null, values = "new String[]{}", action = null, tvalue = null, ssubselector = subselector, soper = oper, sclassifier = classifier;
            if(children!=null && children.size()>0) {
                Command c = children.get(0);
                if(c instanceof ValueCommand) {
                   value = "evaluate(\"" + esc(((ValueCommand)c).value) + "\")";
                } else if(c instanceof ValueListCommand) {
                } else if(c instanceof ClickCommand || c instanceof HoverCommand || c instanceof HoverAndClickCommand
                        || c instanceof ClearCommand || c instanceof SubmitCommand || c instanceof TypeCommand
                        || c instanceof SelectCommand || c instanceof DoubleClickCommand || c instanceof UploadCommand) {
                    if(FindCommandImpl.class.isAssignableFrom(c.getClass()) && ((FindCommandImpl)c).cond==null) {
                        action = "\"" + c.getClass().getSimpleName().toLowerCase().replace("command", "") + "\"";
                        if(c instanceof TypeCommand) {
                        	TypeCommand typ = (TypeCommand)c;
                            if(StringUtils.isNotBlank(typ.qualifier)) {
                            	action = "\"" + typ.type  + typ.qualifier + "\"";
                            }
                            if(typ.type.equalsIgnoreCase("type") || typ.type.equalsIgnoreCase("sendkeys")) {
                            	tvalue = "evaluate(\"" + typ.value + "\")";
                            } else if(typ.type.equalsIgnoreCase("chord")) {
                            	tvalue = "Keys.chord(\"" + typ.value + "\")";
                            } else {
                            	tvalue = "evaluate(\"" + esc(typ.v1) + "\")" + "+ \" \"";
                            	if(typ.v2!=null) tvalue += " + evaluate(\"" + esc(typ.v2) + "\")" + " + \" \"";
                            	if(typ.v3!=null) tvalue += " + evaluate(\"" + esc(typ.v3) + "\")";
                            }
                        } else if(c instanceof UploadCommand) {
                            tvalue = "evaluate(\"" + ((UploadCommand)c).value + "\")";
                        } else if(c instanceof SelectCommand) {
                        	SelectCommand scc = (SelectCommand)c;
                        	ssubselector = scc.by;
                        	value = "evaluate(\"" + esc(scc.value) + "\")";
                        } else if(c instanceof ClickCommand) {
                        	ClickCommand typ = (ClickCommand)c;
                            if(StringUtils.isNotBlank(typ.name)) {
                            	action = "\"click"+typ.name+"\"";
                            }
                        }
                    }
                }
            }
            if(ssubselector!=null) {
                ssubselector = "evaluate(\""+esc(subselector)+"\")";
            }
            if(soper!=null) {
                soper = "\""+soper+"\"";
            }
            if(sclassifier!=null) {
            	sclassifier = "new String[] {evaluate(\""+esc(sclassifier)+"\"), evaluate(\""+esc(classifier1)+"\")}";
            }
            String var = state.varname();
            rtl = var;
            String wel = by.equals("this")?state.currthisat():"___ce___";
            return "List<String[]> " + var + " = transientProviderData(___cw___, "+sc+", "+wel+", 0L, \""+relative+"\", "+sclassifier+", new String[]{\""+by+"\", \""+by1+"\"}, "
                    + ssubselector + ", "+byselsame+", "+value+", "+values+", "
                    + action + ", "+soper+", "+tvalue+", \"Element not found by selector " 
                    + by + "@'" + esc(classifier) + "' at line number "+fileLineDetails[1]+" \", false, "+state.getLayers()+");\n";
        }*/
        String javacodeonly() {
        	String logbc = esc(by) + (classifier!=null?("@'" + esc(classifier)):"");
            return javacodeonlyNoAssert(null, false, state.timeoutNum, false)
                    + "\nAssert.assertTrue(\"Element not found by selector " + logbc
                    + " at line number "+fileLineDetails[1]+" \", ___ce___!=null && !___ce___.isEmpty());";
        }
        String javacodeifonly(List<Command> children, String vrd) {
        	if(StringUtils.isNotBlank(eval)) {
        		return vrd + " = doEvalIf(\""+esc(state.unsanitize(eval))+"\");\nAssert.assertTrue(\"Evaluation condition is invalid at line number "+fileLineDetails[1]+" \", "+vrd+");";
        	} else if(StringUtils.isNotBlank(browserScope)) {
        		return vrd + " = isBrowserName(\""+esc(state.unsanitize(browserScope))+"\");\nAssert.assertTrue(\"Evaluation condition is invalid at line number "+fileLineDetails[1]+" \", "+vrd+");";
        	} else if(StringUtils.isNotBlank(sessionScope)) {
        		return vrd + " = isSessionName(\""+esc(state.unsanitize(sessionScope))+"\");\nAssert.assertTrue(\"Evaluation condition is invalid at line number "+fileLineDetails[1]+" \", "+vrd+");";
        	}
        	String logbc = esc(by) + (classifier!=null?("@'" + esc(classifier)):"");
            return javacodeonlyNoAssert(children, true, state.ifTimeoutNum, false)
                    + vrd + " = ___ce___!=null && !___ce___.isEmpty();\nAssert.assertTrue(\"Element not found by selector " + logbc 
                    + " at line number "+fileLineDetails[1]+" \", "+vrd+");";
        }
        String javacodeonlyNoAssert(List<Command> children, boolean noexcep, int counter, boolean isVisible) {
            //javacodeonlyint(children);
            String sc = state.currvarnamesc();
            String value = (operval!=null?"evaluate(\""+esc(operval)+"\")":null), values = "new String[]{}", action = null, tvalue = null, ssubselector = subselector, soper = oper, sclassifier = classifier;
            if(children!=null && children.size()>0) {
                Command c = children.get(0);
                if(c instanceof ValueCommand) {
                   value = "evaluate(\"" + esc(((ValueCommand)c).value) + "\")";
                } else if(c instanceof ValueListCommand) {
                	values = "new String[]{";
                	for (Command vc : c.children) {
						if(vc instanceof ValueCommand) {
							ValueCommand v = (ValueCommand)vc;
							values += "evaluate(\""+esc(v.value)+"\"),";
						}
					}
                	if(values.charAt(values.length()-1)==',') {
                		values = values.substring(0, values.length()-1);
                	}
                	values += "}";
                } else if(c instanceof ClickCommand || c instanceof HoverCommand || c instanceof HoverAndClickCommand
                        || c instanceof ClearCommand || c instanceof SubmitCommand || c instanceof TypeCommand
                        || c instanceof SelectCommand || c instanceof DoubleClickCommand || c instanceof UploadCommand) {
                    if(FindCommandImpl.class.isAssignableFrom(c.getClass()) && ((FindCommandImpl)c).cond==null) {
                        action = "\"" + c.getClass().getSimpleName().toLowerCase().replace("command", "") + "\"";
                        if(c instanceof TypeCommand) {
                        	TypeCommand typ = (TypeCommand)c;
                            action = "\"" + typ.type  + typ.qualifier + "\"";
                            if(typ.type.equalsIgnoreCase("type") || typ.type.equalsIgnoreCase("sendkeys")) {
                            	tvalue = "evaluate(\"" + typ.value + "\")";
                            } else if(typ.type.equalsIgnoreCase("chord")) {
                            	tvalue = "Keys.chord(\"" + typ.value + "\")";
                            } else {
                            	tvalue = "evaluate(\"" + esc(typ.v1) + "\")" + "+ \" \"";
                            	if(typ.v2!=null) tvalue += " + evaluate(\"" + esc(typ.v2) + "\")" + " + \" \"";
                            	if(typ.v3!=null) tvalue += " + evaluate(\"" + esc(typ.v3) + "\")";
                            }
                        } else if(c instanceof UploadCommand) {
                            tvalue = "evaluate(\"" + ((UploadCommand)c).value + "\")";
                        } else if(c instanceof SelectCommand) {
                        	SelectCommand scc = (SelectCommand)c;
                        	ssubselector = scc.by;
                        	value = "evaluate(\"" + esc(scc.value) + "\")";
                        } else if(c instanceof ClickCommand) {
                        	ClickCommand typ = (ClickCommand)c;
                            if(StringUtils.isNotBlank(typ.name)) {
                            	action = "\"click"+typ.name+"\"";
                            }
                        }
                    }
                } else if(c instanceof RobotCommand) {
                	return c.javacode();
                }
            }
            if(ssubselector!=null) {
                ssubselector = "evaluate(\""+esc(ssubselector)+"\")";
            }
            if(soper!=null) {
                soper = "\""+soper+"\"";
            }
            if(sclassifier!=null) {
            	sclassifier = "new String[] {evaluate(\""+esc(sclassifier)+"\"), evaluate(\""+esc(classifier1)+"\")}";
            } else {
            	sclassifier =  "new String[] {\"\", \"\"}";
            }
            String logbc = esc(by) + (classifier!=null?("@'" + esc(classifier)):"");
            String wel = by.equals("this")?state.currthisat():"___ce___";
            String excmsg = noexcep?null:"\"Element not found by selector " + logbc + " at line number "+fileLineDetails[1]+" \"";
            wel = by.equals("active")?"___cw___.switchTo().activeElement()":wel;
            String b = "___ce___ = handleWaitFuncWL(___cw___, "+sc+", "+wel+", "+counter+", \""+relative+"\", "+sclassifier+", new String[]{\""+esc(by)+"\", \""+esc(by1)+"\"}, "
                    + ssubselector + ", "+byselsame+", "+value+", "+values+", "
                    + action + ", "+soper+", "+tvalue+", "+excmsg+", "+noexcep+", "+state.timeoutSleepGranularity+", "+isVisible+", "+state.getLayers()+");\n";
            return b;
        }
        /*String javacodeonly(List<Command> children, long waitTime) {
            javacodeonlyint(children);
            String sc = state.currvarnamesc();
            String value = null, values = "new String[]{}", action = null, tvalue = null, ssubselector = subselector, soper = oper, sclassifier = classifier;
            if(children!=null && children.size()>0) {
                Command c = children.get(0);
                if(c instanceof ValueCommand) {
                   value = "evaluate(\"" + esc(((ValueCommand)c).value) + "\")";
                } else if(c instanceof ValueListCommand) {
                } else if(c instanceof ClickCommand || c instanceof HoverCommand || c instanceof HoverAndClickCommand
                        || c instanceof ClearCommand || c instanceof SubmitCommand || c instanceof TypeCommand
                        || c instanceof SelectCommand || c instanceof DoubleClickCommand || c instanceof UploadCommand) {
                    if(FindCommandImpl.class.isAssignableFrom(c.getClass()) && ((FindCommandImpl)c).cond==null) {
                        action = "\"" + c.getClass().getSimpleName().toLowerCase().replace("command", "") + "\"";
                        if(c instanceof TypeCommand) {
                        	TypeCommand typ = (TypeCommand)c;
                            if(StringUtils.isNotBlank(typ.qualifier)) {
                            	action = "\"" + typ.type  + typ.qualifier + "\"";
                            }
                            if(typ.type.equalsIgnoreCase("type") || typ.type.equalsIgnoreCase("sendkeys")) {
                            	tvalue = "evaluate(\"" + typ.value + "\")";
                            } else if(typ.type.equalsIgnoreCase("chord")) {
                            	tvalue = "Keys.chord(\"" + typ.value + "\")";
                            } else {
                            	tvalue = "evaluate(\"" + esc(typ.v1) + "\")" + "+ \" \"";
                            	if(typ.v2!=null) tvalue += " + evaluate(\"" + esc(typ.v2) + "\")" + " + \" \"";
                            	if(typ.v3!=null) tvalue += " + evaluate(\"" + esc(typ.v3) + "\")";
                            }
                        } else if(c instanceof UploadCommand) {
                            tvalue = "evaluate(\"" + ((UploadCommand)c).value + "\")";
                        } else if(c instanceof SelectCommand) {
                        	SelectCommand scc = (SelectCommand)c;
                        	ssubselector = scc.by;
                        	value = "evaluate(\"" + esc(scc.value) + "\")";
                        } else if(c instanceof ClickCommand) {
                        	ClickCommand typ = (ClickCommand)c;
                            if(StringUtils.isNotBlank(typ.name)) {
                            	action = "\"click"+typ.name+"\"";
                            }
                        }
                    }
                }
            }
            if(ssubselector!=null) {
                ssubselector = "evaluate(\""+esc(ssubselector)+"\")";
            }
            if(soper!=null) {
                soper = "\""+soper+"\"";
            }
            if(sclassifier!=null) {
            	sclassifier = "new String[] {evaluate(\""+esc(sclassifier)+"\"), evaluate(\""+esc(classifier1)+"\")}";
            }
            String wel = by.equals("this")?state.currthisat():"___ce___";
            String b = "___ce___ = handleWaitFunc(___cw___, "+sc+", "+wel+", (long)"+waitTime+", \""+relative+"\", "+sclassifier+", new String[]{\""+by+"\", \""+by1+"\"}, "
                    + ssubselector + ", "+byselsame+", "+value+", "+values+", "
                    + action + ", "+soper+", "+tvalue+", \"Element not found by selector " 
                    + by + "@'" + esc(classifier) + "' at line number "+fileLineDetails[1]+" \", false, "+state.getLayers()+");\n"
                    + "\nAssert.assertTrue(\"Element not found by selector " + by + "@'" + esc(classifier) 
                    + "' at line number "+fileLineDetails[1]+" \", ___ce___!=null && !___ce___.isEmpty());";
            if(children!=null && children.size()>0) {
                Command c = children.get(0);
                if(c instanceof ActionsCommand || c instanceof RobotCommand) {
                    if(c instanceof ActionsCommand) {
                        b += ((ActionsCommand)c).javacode();
                    }
                }
            }
            return b;
        }
        String javacodeonlyint(List<Command> children) {
            try {
                StringBuilder b = new StringBuilder();
                topele = state.varname();
                String wel = by.equals("this")?state.currthisat():"___ce___";
                if(by.equalsIgnoreCase("id")) {
                    b.append("List<WebElement>  " + topele + " = By.id(evaluate(\""+esc(classifier)+"\")).findElements("+state.currvarnamesc()+");");
                } else if(by.equalsIgnoreCase("this")) {
                    b.append("List<WebElement>  " + topele + " = "+wel+";");
                } else if(by.equalsIgnoreCase("name")) {
                    b.append("List<WebElement>  " + topele + " = By.name(evaluate(\""+esc(classifier)+"\")).findElements("+state.currvarnamesc()+");");
                } else if(by.equalsIgnoreCase("class") || by.equalsIgnoreCase("className")) {
                    b.append("List<WebElement>  " + topele + " = By.className(evaluate(\""+esc(classifier)+"\")).findElements("+state.currvarnamesc()+");");
                } else if(by.equalsIgnoreCase("tag") || by.equalsIgnoreCase("tagname")) {
                    b.append("List<WebElement>  " + topele + " = By.tagName(evaluate(\""+esc(classifier)+"\")).findElements("+state.currvarnamesc()+");");
                } else if(by.equalsIgnoreCase("xpath")) {
                    String ec = esc(classifier);
                    String sc = state.currvarnamesc();
                    if(ec.startsWith(".")) {
                        if(state.loopCounter==0) {
                            throwParseError(null, new RuntimeException("Relative xpath allowed only within a loop block"));
                        }
                        sc = state.currvarnameparc();
                    }
                    b.append("List<WebElement>  " + topele + " = By.xpath(evaluate(\""+ec+"\")).findElements("+sc+");");
                } else if(by.equalsIgnoreCase("cssselector") || by.equalsIgnoreCase("css")) {
                    b.append("List<WebElement>  " + topele + " = By.cssSelector(evaluate(\""+esc(classifier)+"\")).findElements("+state.currvarnamesc()+");");
                } else if(by.equalsIgnoreCase("text")) {
                    b.append("List<WebElement>  " + topele + " = By.xpath(\"//*[contains(text(), '"+esc(classifier)+"')]\").findElements("+state.currvarnamesc()+");");
                } else if(by.equalsIgnoreCase("linkText")) {
                    b.append("List<WebElement>  " + topele + " = By.linkText(evaluate(\""+esc(classifier)+"\")).findElements("+state.currvarnamesc()+");");
                } else if(by.equalsIgnoreCase("partialLinkText")) {
                    b.append("List<WebElement>  " + topele + " = By.partialLinkText(evaluate(\""+esc(classifier)+"\")).findElements("+state.currvarnamesc()+");");
                } else if(by.equalsIgnoreCase("active")) {
                    b.append("\n@SuppressWarnings(\"serial\")");
                    b.append("List<WebElement>  " + topele + " = new ArrayList<WebElement>(){{add(___cw___.activeElement());}};");
                } else if(by.equalsIgnoreCase("jq") || by.equalsIgnoreCase("jquery") || by.equalsIgnoreCase("$")) {
                	b.append("List<WebElement>  " + topele + " = ByJquerySelector(___cw___, evaluate(\""+esc(classifier)+"\"));");
        		} else if(!byselsame) {
                    throwParseError(null);
                } else {
                    condvar = state.condvarname();
                    b.append("\nboolean " + condvar + " = true;");
                    b.append("\n" + condvar + " = evaluate(\"#set($__v__R1_O_P2__ = "+esc(by)+")\\n${__v__R1_O_P2__}\").equalsIgnoreCase(\"true\");");
                    return b.toString();
                }

                b.append("\nAssert.assertTrue(\"Element not found by selector " + by + "@'" + esc(classifier) 
                    + "' at line number "+fileLineDetails[1]+" \", "+topele+"!=null && !"+topele+".isEmpty());");
                b.append("___ce___ = "+topele+";");

                if(this.children!=null && this.children.size()>0) {
                    children = this.children;
                }
                if(children!=null && children.size()>0 && subselector!=null && !subselector.isEmpty()) {
                    Command c = children.get(0);
                    if(c instanceof ValueCommand) {
                        String value = ((ValueCommand)c).value;
                        String cvarname = state.currvarname();
                        cfiltvar = cvarname;
                        condvar = state.condvarname();
                        b.append("\nboolean " + condvar + " = true;");
                        if(byselsame)
                        {
                            if(subselector.equalsIgnoreCase("title")) {
                                b.append("\n" + condvar + " &= evaluate(\""+esc(value)+"\")."+precond+"(___cw___.getTitle())"+postcond+";");
                                return b.toString();
                            } else if(subselector.equalsIgnoreCase("currentUrl")) {
                                b.append("\n" + condvar + " &= evaluate(\""+esc(value)+"\")."+precond+"(___cw___.getCurrentUrl())"+postcond+";");
                                return b.toString();
                            } else if(subselector.equalsIgnoreCase("pageSource")) {
                                b.append("\n" + condvar + " &= evaluate(\""+esc(value)+"\")."+precond+"(___cw___.getPageSource())"+postcond+";");
                                return b.toString();
                            } else if(subselector.equalsIgnoreCase("width")) {
                                b.append("\n" + condvar + " &= evaluate(\""+esc(value)+"\")."+precond+"(String.valueOf(___cw___.manage().window().getSize().getWidth()))"+postcond+";");
                                return b.toString();
                            } else if(subselector.equalsIgnoreCase("height")) {
                                b.append("\n" + condvar + " &= evaluate(\""+esc(value)+"\")."+precond+"(String.valueOf(___cw___.manage().window().getSize().getHeight()))"+postcond+";");
                                return b.toString();
                            } else if(subselector.equalsIgnoreCase("xpos")) {
                                b.append("\n" + condvar + " &= evaluate(\""+esc(value)+"\")."+precond+"(String.valueOf(___cw___.manage().window().getPosition().getX()))"+postcond+";");
                                return b.toString();
                            } else if(subselector.equalsIgnoreCase("ypos")) {
                                b.append("\n" + condvar + " &= evaluate(\""+esc(value)+"\")."+precond+"(String.valueOf(___cw___.manage().window().getPosition().getY()))"+postcond+";");
                                return b.toString();
                            } else if(subselector.equalsIgnoreCase("alerttext")) {
                                b.append("\n" + condvar + " &= evaluate(\""+esc(value)+"\")."+precond+"(___cw___.switchTo().alert().getText())"+postcond+";");
                                return b.toString();
                            } else {
                                throwParseError(null);
                            }
                        }

                        b.append("\nfor(final WebElement " + state.varname() + " : " + cvarname + ")\n{");
                        if(subselector.equalsIgnoreCase("text")) {
                            b.append("\n" + condvar + " &= evaluate(\""+esc(value)+"\")."+precond+"(" + state.currvarname() + ".getText())"+postcond+";");
                        } else if(subselector.equalsIgnoreCase("tagname")) {
                            b.append("\n" + condvar + " &= evaluate(\""+esc(value)+"\")."+precond+"(" + state.currvarname() + ".getTagName())"+postcond+";");
                        } else if(subselector.toLowerCase().startsWith("attr@")) {
                            String atname = subselector.substring(5);
                            if(atname.charAt(0)=='"' || atname.charAt(0)=='\'') {
                                atname = atname.substring(1, atname.length()-1);
                            }
                            b.append("\n" + condvar + " &= evaluate(\""+esc(value)+"\")."+precond+"(" + state.currvarname() + ".getAttribute(\""+esc(atname)+"\"))"+postcond+";");
                        } else if(subselector.toLowerCase().startsWith("cssvalue@")) {
                            String atname = subselector.substring(9);
                            if(atname.charAt(0)=='"' || atname.charAt(0)=='\'') {
                                atname = atname.substring(1, atname.length()-1);
                            }
                            b.append("\n" + condvar + " &= evaluate(\""+esc(value)+"\")."+precond+"(" + state.currvarname() + ".getCssValue(\""+esc(atname)+"\"))"+postcond+";");
                        } else if(subselector.equalsIgnoreCase("width")) {
                            b.append("\n" + condvar + " &= evaluate(\""+esc(value)+"\")."+precond+"(String.valueOf("+state.currvarname()+".getSize().getWidth()))"+postcond+";");
                        } else if(subselector.equalsIgnoreCase("height")) {
                            b.append("\n" + condvar + " &= evaluate(\""+esc(value)+"\")."+precond+"(String.valueOf("+state.currvarname()+".getSize().getHeight()))"+postcond+";");
                        } else if(subselector.equalsIgnoreCase("xpos")) {
                            b.append("\n" + condvar + " &= evaluate(\""+esc(value)+"\")."+precond+"(String.valueOf("+state.currvarname()+".getPosition().getX()))"+postcond+";");
                        } else if(subselector.equalsIgnoreCase("ypos")) {
                            b.append("\n" + condvar + " &= evaluate(\""+esc(value)+"\")."+precond+"(String.valueOf("+state.currvarname()+".getPosition().getY()))"+postcond+";");
                        }
                        b.append("\n}");
                    } else if(c instanceof ValueListCommand) {
                        List<String> values = ((ValueListCommand)c).getValues();
                        String cvarname = state.currvarname();
                        cfiltvar = cvarname;
                        condvar = state.condvarname();
                        b.append("\nboolean " + condvar + " = "+cvarname+"!=null && "+cvarname+".size()>0 && "+String.valueOf(values!=null && values.size()>0)+";");
                        b.append("\nif(!("+condvar+" && "+values.size()+"=="+cvarname+".size())) "+condvar+"=false;\nelse\n{");
                        for (int i=0;i<values.size();i++) {
                            String value = values.get(i);
                            b.append("\nfinal WebElement " + state.varname() + " = "+cvarname+".get("+i+");");
                            if(subselector.equalsIgnoreCase("text")) {
                                b.append("\n" + condvar + " &= evaluate(\""+esc(value)+"\").equals(" + state.currvarname() + ".getText());");
                            } else if(subselector.equalsIgnoreCase("tagname")) {
                                b.append("\n" + condvar + " &= evaluate(\""+esc(value)+"\").equalsIgnoreCase(" + state.currvarname() + ".getTagName());");
                            } else if(subselector.toLowerCase().startsWith("attr@")) {
                                String atname = subselector.substring(5);
                                if(atname.charAt(0)=='"' || atname.charAt(0)=='\'') {
                                    atname = atname.substring(1, atname.length()-1);
                                }
                                b.append("\n" + condvar + " &= evaluate(\""+esc(value)+"\").equals(" + state.currvarname() + ".getAttribute(\""+esc(atname)+"\"));");
                            } else if(subselector.toLowerCase().startsWith("cssvalue@")) {
                                String atname = subselector.substring(9);
                                if(atname.charAt(0)=='"' || atname.charAt(0)=='\'') {
                                    atname = atname.substring(1, atname.length()-1);
                                }
                                b.append("\n" + condvar + " &= evaluate(\""+esc(value)+"\").equals(" + state.currvarname() + ".getCssValue(\""+esc(atname)+"\"));");
                            } else if(subselector.equalsIgnoreCase("width")) {
                                b.append("\n" + condvar + " &= evaluate(\""+esc(value)+"\").equals(String.valueOf("+state.currvarname()+".getSize().getWidth()));");
                            } else if(subselector.equalsIgnoreCase("height")) {
                                b.append("\n" + condvar + " &= evaluate(\""+esc(value)+"\").equals(String.valueOf("+state.currvarname()+".getSize().getHeight()));");
                            } else if(subselector.equalsIgnoreCase("xpos")) {
                                b.append("\n" + condvar + " &= evaluate(\""+esc(value)+"\").equals(String.valueOf("+state.currvarname()+".getPosition().getX()));");
                            } else if(subselector.equalsIgnoreCase("ypos")) {
                                b.append("\n" + condvar + " &= evaluate(\""+esc(value)+"\").equals(String.valueOf("+state.currvarname()+".getPosition().getY()));");
                            } else {
                                throwParseError(null);
                            }
                        }
                        b.append("\n}");
                    }
                } else if(children!=null && children.size()>0) {
                    Command c = children.get(0);
                    if(c instanceof ClickCommand || c instanceof HoverCommand || c instanceof HoverAndClickCommand
                            || c instanceof ClearCommand || c instanceof SubmitCommand || c instanceof TypeCommand
                            || c instanceof SelectCommand || c instanceof DoubleClickCommand || c instanceof UploadCommand) {
                        if(FindCommandImpl.class.isAssignableFrom(c.getClass()) && ((FindCommandImpl)c).cond==null) {
                            b.append(c.selcode(topele));
                        }
                    } else if(c instanceof ActionsCommand || c instanceof RobotCommand) {
                        if(FindCommandImpl.class.isAssignableFrom(c.getClass())) {
                            b.append(c.javacode());
                        }
                    }
                } else if("selected".equalsIgnoreCase(subselector) || "enabled".equalsIgnoreCase(subselector) || "visible".equalsIgnoreCase(subselector)) {
                    String cvarname = topele;
                    cfiltvar = cvarname;
                    condvar = state.condvarname();
                    b.append("\nboolean " + condvar + " = true;");
                    b.append("\nfor(final WebElement " + state.varname() + " : " + cvarname + ")\n{");
                    if(subselector.equalsIgnoreCase("selected")) {
                        b.append("\n" + condvar + " &= " + state.currvarname() + ".isSelected();");
                    } else if(subselector.equalsIgnoreCase("enabled")) {
                        b.append("\n" + condvar + " &= " + state.currvarname() + ".isEnabled();");
                    } else if(subselector.equalsIgnoreCase("visible")) {
                        b.append("\n" + condvar + " &= " + state.currvarname() + ".isDisplayed();");
                    } else {
                        throwParseError(null);
                    }
                    b.append("\n}");
                } else if(subselector!=null) {
                    List<String> ssl = Arrays.asList(subselector.split("[\t ]*,[\t ]*"));
                    rtl = state.varname();
                    b.append("\nList<String[]> "+rtl+" = new java.util.ArrayList<String[]>();");
                    b.append("\nfor(final WebElement " + state.varname() + " : " + topele + ")\n{");
                    b.append("\nString[] __t = new String["+ssl.size()+"];");
                    for (int i=0;i<ssl.size();i++)
                    {
                        if(ssl.get(i).equalsIgnoreCase("text")) {
                            b.append("\n__t["+i+"] = " + state.currvarname() + ".getText();");
                        } else if(ssl.get(i).equalsIgnoreCase("tagname")) {
                            b.append("\n__t["+i+"] = " + state.currvarname() + ".getTagName();");
                        } else if(ssl.get(i).toLowerCase().startsWith("attr@")) {
                            String atname = ssl.get(i).substring(5);
                            if(atname.charAt(0)=='"' || atname.charAt(0)=='\'') {
                                atname = atname.substring(1, atname.length()-1);
                            }
                            b.append("\n__t["+i+"] = " + state.currvarname() + ".getAttribute(\""+esc(atname)+"\");");
                        } else if(ssl.get(i).toLowerCase().startsWith("cssvalue@")) {
                            String atname = ssl.get(i).substring(9);
                            if(atname.charAt(0)=='"' || atname.charAt(0)=='\'') {
                                atname = atname.substring(1, atname.length()-1);
                            }
                            b.append("\n__t["+i+"] = " + state.currvarname() + ".getCssValue(\""+esc(atname)+"\");");
                        } else if(ssl.get(i).equalsIgnoreCase("width")) {
                            b.append("\n__t["+i+"] = String.valueOf("+state.currvarname()+".getSize().getWidth());");
                        } else if(ssl.get(i).equalsIgnoreCase("height")) {
                            b.append("\n__t["+i+"] = String.valueOf("+state.currvarname()+".getSize().getHeight());");
                        } else if(ssl.get(i).equalsIgnoreCase("xpos")) {
                            b.append("\n__t["+i+"] = String.valueOf("+state.currvarname()+".getPosition().getX());");
                        } else if(ssl.get(i).equalsIgnoreCase("ypos")) {
                            b.append("\n__t["+i+"] = String.valueOf("+state.currvarname()+".getPosition().getY());");
                        } else {
                            throwParseError(null);
                        }
                    }
                    b.append("\n" + rtl + ".add(__t);");
                    b.append("\n}");
                }

                return b.toString();
            } catch (GatfSelCodeParseError e) {
                throw e;
            } catch (Throwable e) {
                throwParseError(null, e);
            }
            return null;
        }*/
        String getActionable(String action, String post, String actionsVar) {
            /*StringBuilder b = new StringBuilder();
            if(cfiltvar!=null) {
                b.append("\nfor(final WebElement " + state.varname() + " : " + cfiltvar + ")\n{");
                b.append("\n" + state.currvarname() + "." + action + "(" + (post!=null?post:"") + ");");
                b.append("\n}");
            } else if(actionsVar!=null) {
                b.append("\nfor(final WebElement " + state.varname() + " : " + topele + ")\n{");
                b.append("\n"+actionsVar+".moveToElement("+state.currvarname()+")."+action+"(" + (post!=null?post:"") + ").perform();");
                b.append("\n}");
            } else {
                b.append("\n" + state.currvarname() + ".get(0)." + action + "(" + (post!=null?post:"") + ");");
            }
            return b.toString();*/
        	if(action.equalsIgnoreCase("upload")) {
        		return "\nuploadFile(___ocw___, ___ce___, "+post+", 1);\n";
        	}
            if(actionsVar!=null) {
                return "\n"+actionsVar+".moveToElement(___ce___.get(0))."+action+"(" + (post!=null?post:"") + ").perform();";
            } else {
                return "\n___ce___.get(0)." + action + "(" + (post!=null?post:"") + ");";
            }
        }
        String getActionableVar() {
            return "___ce___";
        }
        String condition() {
            return condvar;
        }
        String javacode() {
            return "";
        }
    }

    /*public static class WaitAndFindCommand extends FindCommand {
        long waitfor = 0;
        long waitfor() {
            return waitfor;
        }
        WaitAndFindCommand(String val, long wf, Object[] cmdDetails, CommandState state) {
            super(val, cmdDetails, state);
            waitfor = wf;
        }
        String toCmd() {
            return waitfor + " " + by + "@\"" + classifier + "\"" + (StringUtils.isEmpty(subselector)?"":" \"" + subselector + "\"");
        }
        String javacode() {
            return "";
        }
        String javacodeonly(List<Command> children) {
            return super.javacodeonly(children, waitfor);
        }
    }*/

    public static class StartCommand extends Command {
        String type;
        StartCommand(Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
        }
        String toCmd() {
            return type;
        }
        String javacode() {
            return "\n{\n";
        }
		public static String[] toSampleSelCmd() {
			return null;
		}
    }

    public static class EndCommand extends Command {
        String type;
        EndCommand(Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
        }
        String toCmd() {
            return type;
        }
        String javacode() {
            return "\n}\n";
        }
		public static String[] toSampleSelCmd() {
			return null;
		}
    }

    public static class EndValueListCommand extends Command {
        String type;
        EndValueListCommand(Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
        }
        String toCmd() {
            return type;
        }
        String javacode() {
            return "";
        }
		public static String[] toSampleSelCmd() {
			return null;
		}
    }

    public static class EndCodeCommand extends Command {
        String type;
        EndCodeCommand(Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
        }
        String toCmd() {
            return type;
        }
        String javacode() {
            return "";
        }
		public static String[] toSampleSelCmd() {
			return null;
		}
    }

    public static class NoopCommand extends Command {
        String type;
        NoopCommand(Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
        }
        String toCmd() {
            return type;
        }
        String javacode() {
            return "";
        }
		public static String[] toSampleSelCmd() {
			return null;
		}
    }

    public static class TypeCommand extends FindCommandImpl {
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
                    parts[0] = state.unsanitize(parts[0].trim());
                    value = parts[0];
                    if(value.charAt(0)==value.charAt(value.length()-1)) {
                        if(value.charAt(0)=='"' || value.charAt(0)=='\'') {
                            value = value.substring(1, value.length()-1);
                        }
                    }
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
                    value = parts[0];
                    value = state.unsanitize(value);
                    if(value.charAt(0)==value.charAt(value.length()-1)) {
                        if(value.charAt(0)=='"' || value.charAt(0)=='\'') {
                            value = value.substring(1, value.length()-1);
                        }
                    }
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
                        v1 = state.unsanitize(v1);
                        if(v1.charAt(0)==v1.charAt(v1.length()-1)) {
                            if(v1.charAt(0)=='"' || v1.charAt(0)=='\'') {
                                v1 = v1.substring(1, v1.length()-1);
                            }
                        }
                        if(!v1.toLowerCase().matches("alphanumeric|alpha|alphanumericlc|alphalc|alphanumericuc|alphauc|numeric|value|range|prefixed|prefixed_|fuzzyn|fuzzya|fuzzyauc|fuzzyalc|fuzzyan|fuzzyanuc|fuzzyanlc")) {
                        	throwParseError(null, new RuntimeException("Randomize type can only be one of alphanumeric|alpha|alphanumericlc|alphalc|alphanumericuc|alphauc|numeric|value|range|prefixed|prefixed_|fuzzyn|fuzzya|fuzzyauc|fuzzyalc|fuzzyan|fuzzyanuc|fuzzyanlc"));
                        }
                    }
                    if(parts.length>t+1) {
                        v2 = parts[c+1];
                        v2 = state.unsanitize(v2);
                        if(v2.charAt(0)==v2.charAt(v2.length()-1)) {
                            if(v2.charAt(0)=='"' || v2.charAt(0)=='\'') {
                                v2 = v2.substring(1, v2.length()-1);
                            }
                        }
                    }
                    if(parts.length>t+2) {
                        v3 = parts[c+2];
                        v3 = state.unsanitize(v3);
                        if(v3.charAt(0)==v3.charAt(v3.length()-1)) {
                            if(v3.charAt(0)=='"' || v3.charAt(0)=='\'') {
                                v3 = v3.substring(1, v3.length()-1);
                            }
                        }
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
    
    public static class UploadCommand extends FindCommandImpl {
        String value;
        int count = 1;
        UploadCommand(String val, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            String[] parts = val.trim().split("[\t ]+");
            if(parts.length>0) {
                parts[0] = parts[0].trim();
                value = parts[0];
                value = state.unsanitize(value);
                if(value.charAt(0)==value.charAt(value.length()-1)) {
                    if(value.charAt(0)=='"' || value.charAt(0)=='\'') {
                        value = value.substring(1, value.length()-1);
                    }
                }
                if(value.matches(".*@[0-9]+")) {
                	try {
                		count = Integer.parseInt(value.substring(value.lastIndexOf("@")+1));
					} catch (Exception e) {
						throwParseError(null, new RuntimeException("Upload multi command expects the number of uploads to be specified as a numeric value eg:- [upload ]"));
					}
                }
                if(parts.length>1) {
                	String cstr = Stream.<String>of(parts).skip(1).collect(Collectors.joining(" "));
                    cond = new FindCommand(cstr.trim(), fileLineDetails, state);
                }
            } else {
                //excep
            }
        }
        String toCmd() {
            return "upload \"" + value + "\"" + (cond!=null?cond.toCmd():"");
        }
        String javacode() {
            StringBuilder b = new StringBuilder();
            if(cond!=null) {
                b.append(cond.javacodeonly());
                b.append(cond.getActionable("upload", "evaluate(\""+esc(value)+"\")", null));
            } else {
                b.append("\nuploadFile(___ocw___, "+state.currvarname()+", evaluate(\""+esc(value)+"\"), "+count+");");
            }
            return b.toString();
        }
        String selcode(String varnm) {
            if(varnm==null) {
                varnm = state.currvarname();
            }
            return "\nuploadFile(___ocw___, "+varnm+", evaluate(\""+esc(value)+"\"), "+count+");";
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
        		"Upload file",
        		"\tupload {filepath} {find-expr}",
        		"Examples :-",
        		"\tupload '/path/to/file.txt' id@'ele1'",
            };
        }
    }
    
    public static class PrintPDFCommand extends Command {
        boolean extractText = false;
        PrintPDFCommand(String cmd, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            String[] parts = cmd.trim().split("[\t ]+");
            this.extractText = parts.length>1 && parts[1].trim().equalsIgnoreCase("text")?true:false;
            name = unSantizedUnQuoted(parts[0].trim(), state);
            if(StringUtils.isBlank(name)) {
            	throwParseError(null, new RuntimeException("Print pdf command needs a file path argument"));
            }
        }
        String toCmd() {
            return "printpdf \"" + name + "\"" + (extractText?" text":"");
        }
        String javacode() {
            return "\nprintToPdf(get___d___(), evaluate(\""+esc(name)+"\"), "+extractText+");\n";
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
        		"Print pdf of the page to file",
        		"\tprintpdf {filepath} {text?}",
        		"Examples :-",
        		"\tprintpdf '/path/to/file.txt'",
        		"\tprintpdf '/path/to/file.txt' text",
            };
        }
    }
    
    public static class WindowOpenSaveInterceptJsCommand extends Command {
    	boolean start = true;
    	boolean extractText = false;
    	WindowOpenSaveInterceptJsCommand(String cmd, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            String[] parts = cmd.trim().split("[\t ]+");
            String mode = unSantizedUnQuoted(parts[0].trim(), state);
            if(StringUtils.isBlank(mode) || !mode.toLowerCase().matches("on|off")) {
            	throwParseError(null, new RuntimeException("window.open save intercept command needs to be enabled/disabled, valid values on|off"));
            }
            start = !mode.equalsIgnoreCase("off");
            if(mode.equalsIgnoreCase("off") && parts.length<2) {
            	throwParseError(null, new RuntimeException("window.open save disable command needs a file name to store the result into"));
            }
            if(mode.equalsIgnoreCase("off")) {
            	name = unSantizedUnQuoted(parts[1].trim(), state);
            }
            if(!start && parts.length>2) {
            	this.extractText = parts.length>2 && parts[2].trim().equalsIgnoreCase("text")?true:false;
            }
        }
        String toCmd() {
            return "wopensave " + (start?"on":"off") + (start?"":" \"" +name+ "\"") + (!start && extractText?" text":"");
        }
        String javacode() {
        	if(start) return "\nwindowOpenSaveJsPre(___cw___);\n";
        	else return "\nwindowOpenSaveJsPost(___cw___, evaluate(\""+esc(name)+"\"), "+extractText+");\n";
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
        		"Save URL passed to window.open",
        		"\twopensave {on|off} {filepath}? {text}?",
        		"Examples :-",
        		"\twopensave on",
        		"\twopensave off '/path/to/file.txt' text",
            };
        }
    }
    
    public static class NetworkAPIInterceptCommand extends Command {
    	boolean start = true;
    	String apiMethod = "GET";
    	String apiUrl = null;
    	String resSel = null;
    	String resSubSel = null;
    	NetworkAPIInterceptCommand(String cmd, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            String[] parts = cmd.trim().split("[\t ]+");
            String mode = unSantizedUnQuoted(parts[0].trim(), state);
            if(StringUtils.isBlank(mode) || !mode.toLowerCase().matches("on|off")) {
            	throwParseError(null, new RuntimeException("window.open save intercept command needs to be enabled/disabled, valid values on|off"));
            }
            start = !mode.equalsIgnoreCase("off");
            if(mode.equalsIgnoreCase("on") && parts.length<2) {
            	throwParseError(null, new RuntimeException("netapix start command needs the api methdod and url parameters"));
            }
            if(mode.equalsIgnoreCase("on")) {
            	apiMethod = unSantizedUnQuoted(parts[1].trim(), state);
            	apiUrl = unSantizedUnQuoted(parts[2].trim(), state);
            }
            if(mode.equalsIgnoreCase("off") && parts.length<2) {
            	throwParseError(null, new RuntimeException("netapix stop command needs the response selector (status,header,json)and response sub selector (header-name, json path for body)"));
            }
            if(mode.equalsIgnoreCase("off")) {
            	resSel = unSantizedUnQuoted(parts[1].trim(), state);
            	resSubSel = unSantizedUnQuoted(parts[2].trim(), state);
            }
        }
        String toCmd() {
            return "netapix " + (start?"on":"off") + (start?"":" \"" +apiMethod+ "\"") + (start?"":" \"" +apiUrl+ "\"") + (!start?"":" \"" +resSel+ "\"") + (!start?"":" \"" +resSubSel+ "\"");
        }
        String javacode() {
        	if(start) return "\nnetworkApiInspectPre(___cw___, evaluate(\""+esc(apiMethod)+"\"), evaluate(\""+esc(apiUrl)+"\"));\n";
        	else return "\nnetworkApiInspectPost(___cw___, evaluate(\""+esc(resSel)+"\"), evaluate(\""+esc(resSubSel)+"\"));\n";
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
        		"Save URL passed to window.open",
        		"\tnetapix on {api-method} {api-url}",
        		"\tnetapix off {status|header|json} {header-name|json-path}?",
        		"Examples :-",
        		"\tnetapix on GET http://abc.com/api/person",
        		"\tnetapix off status --> creates context parameter by name ${apiStatus}=200",
        		"\tnetapix off header token --> creates context parameter by name ${apiHeader}=some-token",
        		"\tnetapix off json $.store.book[0].title --> creates context parameter by name ${apiJson}=some-value",
            };
        }
    }

    public static class RandomizeCommand extends FindCommandImpl {
        String v1;
        String v2, v3;
        boolean noblur = false;
        RandomizeCommand(String val, Object[] cmdDetails, CommandState state, FindCommand cond, boolean noblur) {
            super(cmdDetails, state);
            String[] parts = val.trim().split("[\t ]+");
            this.noblur = noblur;
            if(parts.length>0) {
                int c = 1, t = 1;
                if(cond!=null) {
                    c = 0;
                    t = 0;
                } else {
                    this.cond = new FindCommand(parts[0].trim(), fileLineDetails, state);
                }
                if(parts.length>t) {
                    v1 = parts[c];
                    v1 = state.unsanitize(v1);
                    if(v1.charAt(0)==v1.charAt(v1.length()-1)) {
                        if(v1.charAt(0)=='"' || v1.charAt(0)=='\'') {
                            v1 = v1.substring(1, v1.length()-1);
                        }
                    }
                    if(!v1.toLowerCase().matches("alphanumeric|alpha|alphanumericlc|alphalc|alphanumericuc|alphauc|numeric|value|range|prefixed|prefixed_|fuzzyn|fuzzya|fuzzyauc|fuzzyalc|fuzzyan|fuzzyanuc|fuzzyanlc")) {
                    	throwParseError(null, new RuntimeException("Randomize type can only be one of alphanumeric|alpha|alphanumericlc|alphalc|alphanumericuc|alphauc|numeric|value|range|prefixed|prefixed_|fuzzyn|fuzzya|fuzzyauc|fuzzyalc|fuzzyan|fuzzyanuc|fuzzyanlc"));
                    }
                }
                if(parts.length>t+1) {
                    v2 = parts[c+1];
                    v2 = state.unsanitize(v2);
                    if(v2.charAt(0)==v2.charAt(v2.length()-1)) {
                        if(v2.charAt(0)=='"' || v2.charAt(0)=='\'') {
                            v2 = v2.substring(1, v2.length()-1);
                        }
                    }
                }
                if(parts.length>t+2) {
                    v3 = parts[c+2];
                    v3 = state.unsanitize(v3);
                    if(v3.charAt(0)==v3.charAt(v3.length()-1)) {
                        if(v3.charAt(0)=='"' || v3.charAt(0)=='\'') {
                            v3 = v3.substring(1, v3.length()-1);
                        }
                    }
                }
                
                try {
    				Long.parseLong(v2);
    			} catch (Exception e) {
    				if(v2!=null && !v1.equalsIgnoreCase("value")) {
    					throwParseError(null, new RuntimeException("Randomize command needs first argument to be a number for alphanumeric|alpha|alphanumericlc|alphalc|alphanumericuc|alphauc"));
    				}
    			}
                
                try {
                	Long.parseLong(v3);
    			} catch (Exception e) {
    				if(v3!=null) {
    					throwParseError(null, new RuntimeException("Randomize command needs second argument to be a number for alphanumeric|alpha|alphanumericlc|alphalc|alphanumericuc|alphauc"));
    				}
    			}
            } else {
                //excep
            }
        }
        String toCmd() {
            return "randomize" + (!noblur?"":"nb") + " \"" + cond.toCmd() + (v1!=null?(" "+v1):"") + (v2!=null?(" "+v2):"") + (v3!=null?(" "+v3):"");
        }
        String javacode() {
            StringBuilder b = new StringBuilder();
            if(cond!=null) {
                b.append(cond.javacodeonly());
                b.append("\nrandomize("+cond.getActionableVar()+", \""+(v1!=null?esc(v1):"")+"\", \""+(v2!=null?esc(v2):"")+"\", \""+(v3!=null?esc(v3):"")+"\");\n");
                if(!noblur) {
                	b.append("\nblur("+cond.getActionableVar()+".get(0));\n");
                }
            } else {
                b.append("\nrandomize(___ce___, \""+(v1!=null?esc(v1):"")+"\", \""+(v2!=null?esc(v2):"")+"\", \""+(v3!=null?esc(v3):"")+"\");\n");
                b.append("\nblur(___ce___.get(0));\n");
            }
            return b.toString();
        }
        String javacodeonly(String condVarnm) {
            StringBuilder b = new StringBuilder();
            b.append("\nrandomize("+condVarnm+", \""+(v1!=null?esc(v1):"")+"\", \""+(v2!=null?esc(v2):"")+"\", \""+(v3!=null?esc(v3):"")+"\");\n");
            if(!noblur) {
            	b.append("\nblur("+condVarnm+".get(0));\n");
            }
            return b.toString();
        }
        String ifTextEl() {
            return "\n("+state.currvarname()+".get(0).getTagName().toLowerCase().matches(\"input\") "
                    + "&& "+state.currvarname()+".get(0).getAttribute(\"type\").toLowerCase().matches(\"text|url|email|hidden\"))"
                    + "|| ("+state.currvarname()+".get(0).getTagName().toLowerCase().matches(\"textarea\"))";
        }
        String ifNumTextEl() {
            return "\n"+state.currvarname()+".get(0).getTagName().toLowerCase().matches(\"input\") "
                    + "&& "+state.currvarname()+".get(0).getAttribute(\"type\").toLowerCase().matches(\"number\")";
        }
        String ifSelectEl() {
            return "\n"+state.currvarname()+".get(0).getTagName().toLowerCase().matches(\"select\")";
        }
        String ifRadioEl() {
            return "\n"+state.currvarname()+".get(0).getTagName().toLowerCase().matches(\"input\")"
                    + "&& "+state.currvarname()+".get(0).getAttribute(\"type\").toLowerCase().matches(\"radio\")";
        }
        String ifCheckboxEl() {
            return "\n"+state.currvarname()+".get(0).getTagName().toLowerCase().matches(\"input\")"
                    + "&& "+state.currvarname()+".get(0).getAttribute(\"type\").toLowerCase().matches(\"checkbox\")";
        }
        String selcode(String varnm) {
            if(varnm==null) {
                varnm = state.currvarname();
            }
            return "\n"+varnm+".get(0).sendKeys(evaluate(\""+esc(v1)+"\"));" + (!noblur?"":"blur("+varnm+".get(0));\n");
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
        		"Type random values in input/textarea elements",
        		"\trandomize(bl|ch|bk|cl|fo) {find-expr} alphanumeric|alpha|alphanumericlc|alphalc|alphanumericuc|alphauc|numeric|value|range|prefixed|prefixed_|fuzzyn|fuzzya|fuzzyauc|fuzzyalc|fuzzyan|fuzzyanuc|fuzzyanlc {optional character count|range start} {count of space separated random words(for eg, name of person)|range end}",
        		"Examples :-",
        		"\trandomize id@'ele1' alphanumeric 12",
        		"\trandomize id@'ele1' alpha 8 3 (first-name middle-name last-name)",
        		"\trandomize id@'ele1' numeric 5",
        		"\trandomize id@'ele1' range 9999 99999",
        		"\trandomize id@'ele1' value 'abcd'",
        		"\trandomize id@'ele1' fuzzyn _@3:4:5:6",
        		"\trandomize id@'ele1' fuzzya _@3:4:5:6",
        		"\trandomize id@'ele1' fuzzyan _@3:4:5:6",
            };
        }
    }

    public static class ChordCommand extends FindCommandImpl {
        List<String> values = new ArrayList<String>();
        String value;
        boolean noblur = false;
        public ChordCommand(Object[] cmdDetails, CommandState state, boolean noblur) {
            super(cmdDetails, state);
            this.noblur = noblur;
        }
        ChordCommand(String val, Object[] cmdDetails, CommandState state, boolean noblur) {
            super(cmdDetails, state);
            String[] parts = val.trim().split("[\t ]+");
            this.noblur = noblur;
            if(parts.length>0) {
                parts[0] = state.unsanitize(parts[0].trim());
                value = parts[0];
                if(value.charAt(0)==value.charAt(value.length()-1)) {
                    if(value.charAt(0)=='"' || value.charAt(0)=='\'') {
                        value = value.substring(1, value.length()-1);
                    }
                }
                String temp = StringEscapeUtils.unescapeJava(value);
                try {
                    if(parts.length>1) {
                        cond = new FindCommand(parts[1], fileLineDetails, state);
                    }
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
                } catch (Exception e) {
                }
            } else {
                //excep
            }
        }
        String toCmd() {
            return "chord"+(!noblur?"":"nb")+" \"" + value + "\"" + (cond!=null?cond.toCmd():"");
        }
        String javacode() {
            StringBuilder b = new StringBuilder();
            if(cond!=null) {
                b.append(cond.javacodeonly());
                //b.append("\nAssert.assertTrue("+cond.condition()+");");
            }
            if(values!=null && !values.isEmpty()) {
                String chs = "";
                for (int i=0;i<values.size();i++) {
                    chs += values.get(i);
                    chs += (i!=values.size()-1)?", ":"";
                }
                if(cond!=null) {
                    b.append(cond.getActionable("sendKeys", "Keys.chord("+chs+")", null));
                } else {
                    b.append("\n"+state.currvarname()+".get(0).sendKeys(Keys.chord("+chs+"));");
                    if(!noblur) {
                    	b.append("\nblur("+state.currvarname()+".get(0));\n");
                    }
                }
            }
            return b.toString();
        }
        String selcode(String varnm) {
            if(varnm==null) {
                varnm = state.currvarname();
            }
            if(values!=null && !values.isEmpty()) {
                String chs = "";
                for (int i=0;i<values.size();i++) {
                    chs += values.get(i);
                    chs += (i!=values.size()-1)?", ":"";
                }
                return "\n"+varnm+".get(0).sendKeys(Keys.chord("+chs+"));" + (!noblur?"":"\nblur("+varnm+".get(0));\n");
            }
            return "";
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
        		"Type UTF-8 or normal ASCII characters in input/textarea elements",
        		"\tchord(bl|ch|bk|cl|fo) {utf-8 character1}{utf-8 character2}...{utf-8 characterN} {find-expr}",
        		"Examples :-",
        		"\tchord \\u0048\\u0065\\u006c\\u006c\\u006f\\u0020\\u0057\\u006f\\u0072\\u006c\\u0064 id@'abc'",
            };
        }
    }

    public static class SelectCommand extends FindCommandImpl {
        String by, value;
        public SelectCommand(Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
        }
        SelectCommand(String val, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            String[] parts = val.trim().split("[\t ]+");
            if(parts.length>0) {
                parts[0] = parts[0].trim();
                by = parts[0];
                if(by.indexOf("@")!=-1) {
                    value = by.split("@")[1];
                    by = by.split("@")[0];
                    value = state.unsanitize(value);
                    if(value.charAt(0)==value.charAt(value.length()-1)) {
                        if(value.charAt(0)=='"' || value.charAt(0)=='\'') {
                            value = value.substring(1, value.length()-1);
                        }
                    }
                }
                if(parts.length>1) {
                	String cstr = Stream.<String>of(parts).skip(1).collect(Collectors.joining(" "));
                    cond = new FindCommand(cstr.trim(), fileLineDetails, state);
                }
            } else {
                //excep
            }
        }
        String toCmd() {
            return "select \"" + by + "\"" + (cond!=null?cond.toCmd():"");
        }
        String javacodeonly(String cvarnm) {
        	StringBuilder b = new StringBuilder();
        	String selvrnm = state.varname();
            b.append("\nSelect "+selvrnm+" = new Select("+cvarnm+".get(0));");
            if(by.equalsIgnoreCase("text")) {
                b.append("\n"+selvrnm+".selectByVisibleText(evaluate(\""+value+"\"));");
            } else if(by.equalsIgnoreCase("index")) {
                b.append("\n"+selvrnm+".selectByIndex(Integer.parseInt(evaluate(\""+value+"\")));"); 
            } else if(by.equalsIgnoreCase("value")) {
                b.append("\n"+selvrnm+".selectByValue(evaluate(\""+value+"\"));"); 
            } else if(by.equalsIgnoreCase("first")) {
                b.append("\n"+selvrnm+".selectByIndex(0);"); 
            } else if(by.equalsIgnoreCase("last")) {
                b.append("\n"+selvrnm+".selectByIndex("+selvrnm+".getOptions().size()-1);"); 
            }
            return b.toString();
        }
        String javacode() {
            StringBuilder b = new StringBuilder();
            if(cond!=null) {
                b.append(cond.javacodeonly());
                //b.append("\nAssert.assertTrue("+cond.condition()+");");
            }
            String cvarnm = null;;
            if(cond!=null) {
                cvarnm = cond.getActionableVar();
            }
            if(cond==null || cond.getActionableVar()==null) {
                cvarnm = state.currvarname();
            }
            String selvrnm = state.varname();
            b.append("\nSelect "+selvrnm+" = new Select("+cvarnm+".get(0));");
            if(by.equalsIgnoreCase("text")) {
                b.append("\n"+selvrnm+".selectByVisibleText(evaluate(\""+value+"\"));");
            } else if(by.equalsIgnoreCase("index")) {
                b.append("\n"+selvrnm+".selectByIndex(Integer.parseInt(evaluate(\""+value+"\")));"); 
            } else if(by.equalsIgnoreCase("value")) {
                b.append("\n"+selvrnm+".selectByValue(evaluate(\""+value+"\"));"); 
            } else if(by.equalsIgnoreCase("first")) {
                b.append("\n"+selvrnm+".selectByIndex(0);"); 
            } else if(by.equalsIgnoreCase("last")) {
                b.append("\n"+selvrnm+".selectByIndex("+selvrnm+".getOptions().size()-1);"); 
            }
            return b.toString();
        }
        String selcode(String varnm) {
            if(varnm==null) {
                varnm = state.currvarname();
            }
            if(by.equalsIgnoreCase("text")) {
                return ("\n"+varnm+".selectByVisibleText(evaluate(\""+value+"\"));");
            } else if(by.equalsIgnoreCase("index")) {
                return ("\n"+varnm+".selectByIndex("+value+");"); 
            } else if(by.equalsIgnoreCase("value")) {
                return ("\n"+varnm+".selectByValue(evaluate(\""+value+"\"));"); 
            } else if(by.equalsIgnoreCase("first")) {
                return ("\n"+varnm+".selectByIndex(0);"); 
            } else if(by.equalsIgnoreCase("last")) {
                return ("\n"+varnm+".selectByIndex("+varnm+".getOptions().size()-1);"); 
            }
            return "";
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
        		"Select value from dropdown element",
        		"\tselect {text|index|value|first|last}@{value} {find-expr}",
        		"Examples :-",
        		"\tselect text@'first' id@'abc'",
        		"\tselect index@2 id@'abc'",
        		"\tselect value@'second' id@'abc'",
        		"\tselect first id@'abc'",
        		"\tselect last id@'abc'",
            };
        }
    }

    public static class HoverCommand extends FindCommandImpl {
        String toCmd() {
            return "hover" + (cond!=null?cond.toCmd():"");
        }
        String javacode() {
            StringBuilder b = new StringBuilder();
            if(cond!=null) {
                b.append(cond.javacodeonly());
                //b.append("\nAssert.assertTrue("+cond.condition()+");");o
            }
            String cvarnm = null;
            if(cond!=null) {
                cvarnm = cond.getActionableVar();
            }
            if(cond.getActionableVar()==null) {
                cvarnm = state.currvarname();
            }
            String hvvrnm = state.varname();
            b.append("\norg.openqa.selenium.interactions.Actions "+hvvrnm+" = new org.openqa.selenium.interactions.Actions(___cw___);");
            b.append("\n"+hvvrnm+".moveToElement("+cvarnm+".get(0)).perform();");
            return b.toString();
        }
        String selcode(String varnm) {
            if(varnm==null) {
                varnm = state.currvarname();
            }
            String hvvrnm = state.varname();
            StringBuilder b = new StringBuilder();
            b.append("\norg.openqa.selenium.interactions.Actions "+hvvrnm+" = new org.openqa.selenium.interactions.Actions(___cw___);");
            b.append("\n"+hvvrnm+".moveToElement("+varnm+".get(0)).perform();");
            return b.toString();
        }
        public HoverCommand(Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
        		"Hover over an element",
        		"\thover {find-expr}",
        		"Examples :-",
        		"\thover id@'abc'",
            };
        }
    }

    public static class HoverAndClickCommand extends FindCommandImpl {
        FindCommand cond;//hover element
        FindCommand condCe;
        public HoverAndClickCommand(Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
        }
        HoverAndClickCommand(String val, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            String[] t = val.trim().split("[\t ]+");
            if(t.length>0) {
                cond = new FindCommand(t[0], fileLineDetails, state);
            }
            if(t.length>1) {
                condCe = new FindCommand(t[1], fileLineDetails, state);
            }
        }
        String toCmd() {
            return "hoverclick " + (cond!=null?cond.toCmd():"") +  (condCe!=null?(" "+condCe.toCmd()):"");
        }
        String javacode() {
            StringBuilder b = new StringBuilder();
            if(cond!=null) {
                b.append(cond.javacodeonly());
                //b.append("\nAssert.assertTrue("+condHe.condition()+");");
            }
            String hevarnm = state.currvarname();
            if(condCe!=null) {
                b.append(condCe.javacodeonly());
            }
            String cevarnm = condCe.topele();
            String hvvrnm = state.varname();
            b.append("\norg.openqa.selenium.interactions.Actions "+hvvrnm+" = new org.openqa.selenium.interactions.Actions(___cw___);");
            b.append("\n"+hvvrnm+".moveToElement("+hevarnm+".get(0)).click(((List<WebElement>)"+cevarnm+"[0]).get(0)).perform();");
            return b.toString();
        }
        String selcode(String varnm) {
            if(varnm==null) {
                varnm = state.currvarname();
            }
            String hvvrnm = state.varname();
            StringBuilder b = new StringBuilder();
            b.append("\norg.openqa.selenium.interactions.Actions "+hvvrnm+" = new org.openqa.selenium.interactions.Actions(___cw___);");
            b.append("\n"+hvvrnm+".moveToElement("+varnm+".get(0)).click().perform();");
            return b.toString();
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
        		"Hover over an element and click some other element",
        		"\thoverclick {find-expr} {find-expr}",
        		"Examples :-",
        		"\thoverclick id@'hoverele' id@'clickele'",
            };
        }
    }

    public static class ActionsCommand extends FindCommandImpl {
        String action;
        String expr1;
        String expr2;
        String expr3;
        static final String ALLCMDS = "click|clickandhold|clickhold|release|dblclick|doubleclick|contextclick|rightclick|movetoelement|moveto|keydown|keyup"
                + "|sendkeys|type|chord|randomize|draganddrop|dragdrop|draganddrop1|dragdrop1|movebyoffset|moveby|";
        public ActionsCommand(Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
        		"Multiple Chained Actions",
        		"\tactions movetoelement|moveto {find-expr} ({click|clickandhold|release|dblclick|doubleclick|contextclick|clickhold|rightclick} {find-expr}?|"
        		+ "{keydown|keyup|sendkeys|type {value}}|{movetoelement|moveto {find-expr}}|{draganddrop|dragdrop|draganddrop1|dragdrop1 {find-expr} {find-expr}}|"
        		+ "randomize {alphanumeric|alpha|alphanumericlc|alphalc|alphanumericuc|alphauc|numeric|value|range|prefixed|prefixed_|fuzzyn|fuzzya|fuzzyauc|fuzzyalc|fuzzyan|fuzzyanuc|fuzzyanlc} {arg1} {arg2} {arg3}?}|"
        		+ "{movebyoffset|moveby {x-offset} {y-offset}}) ... movetoelement|moveto {find-expr} ... ({click|clickan...",
        		"Examples :-",
        		"\tactions movetoelement id@'ele' click moveto id@'ele2' clickandhold moveto id@'ele3' release type '123'",
        		"\tactions movetoelement id@'ele' sendkeys 'abc'",
            };
        }
        ActionsCommand(String val, FindCommand fcmd, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            String[] t = val.trim().split("[\t ]+");
            int tl = t.length, counter = 0;
            ActionsCommand cmd = this;
            this.cond = fcmd;
            boolean moveDone = false;
            while(counter<tl) {
                if(t[counter].toLowerCase().matches("click|clickandhold|release|dblclick|doubleclick|contextclick|clickhold|rightclick")) {
                    cmd.action = t[counter].toLowerCase();
                    if(cmd.action.equalsIgnoreCase("clickhold") || cmd.action.equalsIgnoreCase("clickandhold")) {
                        cmd.action = "clickAndHold";
                    } else if(cmd.action.equalsIgnoreCase("rightclick") || cmd.action.equalsIgnoreCase("contextclick")) {
                        cmd.action = "contextClick";
                    } else if(cmd.action.equalsIgnoreCase("doubleclick") || cmd.action.equalsIgnoreCase("dblclick")) {
                        cmd.action = "doubleClick";
                    }
                    if(tl>counter+1 && !t[counter+1].toLowerCase().matches(ALLCMDS)) {
                        cmd.expr1 = state.unsanitize(t[++counter]);
                        if(cmd.expr1.charAt(0)==cmd.expr1.charAt(cmd.expr1.length()-1)) {
                            if(cmd.expr1.charAt(0)=='"' || cmd.expr1.charAt(0)=='\'') {
                                cmd.expr1 = cmd.expr1.substring(1, cmd.expr1.length()-1);
                            }
                        }
                    }
                } else if(t[counter].toLowerCase().matches("movetoelement|moveto")) {
                    cmd.action = t[counter].toLowerCase();
                    if(cmd.action.equalsIgnoreCase("moveto") || cmd.action.equalsIgnoreCase("movetoelement")) {
                        cmd.action = "moveToElement";
                    }
                    if(!moveDone && cond!=null && ((tl>counter+1 && t[counter+1].toLowerCase().matches(ALLCMDS)) || tl==counter+1)) {
                        moveDone = true;
                    } else if(tl>counter+1) {
                        cmd.expr1 = state.unsanitize(t[++counter]);
                        if(cmd.expr1.charAt(0)==cmd.expr1.charAt(cmd.expr1.length()-1)) {
                            if(cmd.expr1.charAt(0)=='"' || cmd.expr1.charAt(0)=='\'') {
                                cmd.expr1 = cmd.expr1.substring(1, cmd.expr1.length()-1);
                            }
                        }
                    } else {
                        throwParseError(null, new RuntimeException("Expression expected after actions (movetoelement|moveto)"));
                    }
                } else if(t[counter].toLowerCase().matches("keydown|keyup|sendkeys|type|chord")) {
                    cmd.action = t[counter].toLowerCase();
                    if(cmd.action.equalsIgnoreCase("type") || cmd.action.equalsIgnoreCase("sendkeys") || cmd.action.equalsIgnoreCase("chord")) {
                        cmd.action = "sendKeys";
                    } else if(cmd.action.equalsIgnoreCase("keydown")) {
                        cmd.action = "keyDown";
                    } else if(cmd.action.equalsIgnoreCase("keyup")) {
                        cmd.action = "keyUp";
                    }
                    if(tl>counter+1) {
                        cmd.expr1 = state.unsanitize(t[++counter]);
                        if(cmd.expr1.charAt(0)==cmd.expr1.charAt(cmd.expr1.length()-1)) {
                            if(cmd.expr1.charAt(0)=='"' || cmd.expr1.charAt(0)=='\'') {
                                cmd.expr1 = cmd.expr1.substring(1, cmd.expr1.length()-1);
                            }
                        }
                    } else {
                        throwParseError(null, new RuntimeException("Expression expected after actions (keydown|keyup|sendkeys|type|chord)"));
                    }
                } else if(t[counter].toLowerCase().matches("draganddrop|dragdrop|draganddrop1|dragdrop1|movebyoffset|moveby")) {
                    cmd.action = t[counter].toLowerCase();
                    if(cmd.action.equalsIgnoreCase("moveby") || cmd.action.equalsIgnoreCase("movebyoffset")) {
                        cmd.action = "moveByOffset";
                    } else if(cmd.action.equalsIgnoreCase("dragdrop") || cmd.action.equalsIgnoreCase("draganddrop")) {
                        cmd.action = "dragAndDrop";
                    } else if(cmd.action.equalsIgnoreCase("dragdrop1") || cmd.action.equalsIgnoreCase("draganddrop1")) {
                        cmd.action = "dragAndDrop1";
                    }
                    int fc = 0;
                    for (int i = 1; i < 3; i++)
                    {
                        if(tl<=counter+i)break;
                        if(t[counter+i].toLowerCase().matches(ALLCMDS)) {
                            break;
                        }
                        fc++;
                    }
                    if(fc==2) {
                        cmd.expr1 = state.unsanitize(t[++counter]);
                        if(cmd.expr1.charAt(0)==cmd.expr1.charAt(cmd.expr1.length()-1)) {
                            if(cmd.expr1.charAt(0)=='"' || cmd.expr1.charAt(0)=='\'') {
                                cmd.expr1 = cmd.expr1.substring(1, cmd.expr1.length()-1);
                            }
                        }
                        cmd.expr2 = state.unsanitize(t[++counter]);
                        if(cmd.expr2.charAt(0)==cmd.expr2.charAt(cmd.expr2.length()-1)) {
                            if(cmd.expr2.charAt(0)=='"' || cmd.expr2.charAt(0)=='\'') {
                                cmd.expr2 = cmd.expr2.substring(1, cmd.expr2.length()-1);
                            }
                        }
                    } else {
                        throwParseError(null, new RuntimeException("Expressions(2) expected after actions (dragAndDrop|moveByOffset)"));
                    }
                } else if(t[counter].toLowerCase().equals("randomize")) {
                    cmd.action = t[counter].toLowerCase();
                    if(t.length<counter+1 || !t[counter+1].toLowerCase().toLowerCase().matches("alphanumeric|alpha|alphanumericlc|alphalc|alphanumericuc|alphauc|numeric|value|range|prefixed|prefixed_|fuzzyn|fuzzya|fuzzyauc|fuzzyalc|fuzzyan|fuzzyanuc|fuzzyanlc")) {
                    	throwParseError(null, new RuntimeException("Randomize command needs to define a type that can only be one of alphanumeric|alpha|alphanumericlc|alphalc|alphanumericuc|alphauc|numeric|value|range|prefixed|prefixed_|fuzzyn|fuzzya|fuzzyauc|fuzzyalc|fuzzyan|fuzzyanuc|fuzzyanlc"));
                    }
                    
                    cmd.expr1 = state.unsanitize(t[++counter]);
                    if(cmd.expr1.charAt(0)==cmd.expr1.charAt(cmd.expr1.length()-1)) {
                        if(cmd.expr1.charAt(0)=='"' || cmd.expr1.charAt(0)=='\'') {
                            cmd.expr1 = cmd.expr1.substring(1, cmd.expr1.length()-1);
                        }
                    }
                    
                    try {
                    	cmd.expr2 = state.unsanitize(t[counter+1]);
                        if(cmd.expr2.charAt(0)==cmd.expr2.charAt(cmd.expr2.length()-1)) {
                            if(cmd.expr2.charAt(0)=='"' || cmd.expr2.charAt(0)=='\'') {
                                cmd.expr2 = cmd.expr2.substring(1, cmd.expr2.length()-1);
                            }
                        }
                        ++counter;
                        Long.parseLong(cmd.expr2);
        			} catch (Exception e) {
        				if(cmd.expr2!=null && !cmd.expr1.equalsIgnoreCase("value")) {
        					if(cmd.expr1.startsWith("fuzzy")) {
        						if(cmd.expr2.length()>4 && cmd.expr2.charAt(1)=='@' && cmd.expr2.contains(":")) {
        						} else {
        							throwParseError(null, new RuntimeException("Randomize command with fuzzy logic needs the syntax fuzzy <sep-char>@<seq-len>:<seq-len>:<seq-len> --> _@3:4:5 --> 333_4444_55555"));
        						}
        					} else {
        						throwParseError(null, new RuntimeException("Randomize command needs first argument to be a number for alphanumeric|alpha|alphanumericlc|alphalc|alphanumericuc|alphauc|numeric|range"));
        					}
        					throwParseError(null, new RuntimeException("Randomize command needs first argument to be a number for alphanumeric|alpha|alphanumericlc|alphalc|alphanumericuc|alphauc|numeric|range"));
        				}
        			}
                    
                    try {
                    	cmd.expr3 = state.unsanitize(t[counter+1]);
                        if(cmd.expr3.charAt(0)==cmd.expr3.charAt(cmd.expr3.length()-1)) {
                            if(cmd.expr3.charAt(0)=='"' || cmd.expr3.charAt(0)=='\'') {
                                cmd.expr3 = cmd.expr3.substring(1, cmd.expr3.length()-1);
                            }
                        }
                        Long.parseLong(cmd.expr3);
        				++counter;
        			} catch (Exception e) {
        				if(cmd.expr3!=null && !cmd.expr3.matches(ALLCMDS)) {
        					throwParseError(null, new RuntimeException("Randomize command needs second argument to be a number for alphanumeric|alpha|alphanumericlc|alphalc|alphanumericuc|alphauc|numeric|range"));
        				}
        				cmd.expr3 = null;
        			}
                } else {
                    throwParseError(null, new RuntimeException("Invalid action ["+t[counter]+"] specified, should be one of (click|clickhold|clickAndHold|release|doubleClick|dblclick|contextClick|rightclick|keyDown|keyUp|sendKeys|type|randomize|moveToElement|dragAndDrop|dragdrop|moveByOffset|moveby)"));
                }
                ++counter;
                cmd.children.add(new ActionsCommand(cmdDetails, state));
                cmd = (ActionsCommand)cmd.children.get(0);
            }
            cmd = this;
            while(!cmd.children.isEmpty()) {
                ActionsCommand tcmd = (ActionsCommand)cmd.children.get(0);
                if(tcmd.action==null) {
                    cmd.children.clear();
                }
                cmd = tcmd;
            }
        }
        String toCmd() {
            return "action ";
        }
        String _javacode(String acnm) {
            StringBuilder b = new StringBuilder();
            if(action.toLowerCase().matches("click|clickandhold|release|doubleclick|contextclick")) {
            	if(cond!=null && expr1==null) {
                    b.append("\n"+acnm+"."+action+"("+cond.getActionableVar()+".get(0));");
                } else if(expr1==null) {
                    b.append("\n"+acnm+"."+action+"();");
                } else {
                	if(expr1.equals("this")) {
                		b.append("\n"+acnm+"."+action+"("+state.currthisat()+".get(0));");
                	} else if(expr1.equals("current")) {
                		b.append("\n"+acnm+"."+action+"(___ce___.get(0));");
                	} else {
	                	String ck = state.unsanitize(expr1);
	                    FindCommand fc = new FindCommand(ck, fileLineDetails, state);
	                    //int counter = state.timeoutNum;
	                    //String cntvar = state.varnamerandom();
	                    //b.append("int "+cntvar+" = 0;\n");
	                    //b.append("\nwhile(true) {\n");
	                    b.append(fc.javacodeonly());
	                    //b.append("if("+fc.getActionableVar()+"!=null)break;\n");
	                    //b.append("sleep("+state.timeoutSleepGranularity+");\n");
	                    //b.append("if("+cntvar+"++=="+counter+")throw new RuntimeException("+fc.getErr()+");\n");
	                    //b.append("}\n");
	                    b.append("\n"+acnm+"."+action+"("+fc.getActionableVar()+".get(0));");
                	}
                }
            } else if(action.toLowerCase().matches("keydown|keyup")) {
                String ck = state.unsanitize(expr1);
                b.append("\n"+acnm+"."+(action.equalsIgnoreCase("keydown")?"keyDown":"keyUp")+"(Keys.getKeyFromUnicode('"+ck+"'));");
            } else if(action.toLowerCase().matches("sendkeys") || action.toLowerCase().matches("type") || action.toLowerCase().matches("chord")) {
                String ck = state.unsanitize(expr1);
                b.append("\n"+acnm+".sendKeys(evaluate(\""+ck+"\"));");
            } else if(action.toLowerCase().matches("randomize")) {
                String v1 = state.unsanitize(expr1);
                String v2 = expr2!= null?state.unsanitize(expr2):"";
                String v3 = expr3!= null?state.unsanitize(expr3):"";
                v1 = esc(v1) + " " + esc(v2) + " " + esc(v3);
                b.append("\n"+acnm+".sendKeys(randomize(\""+v1.trim()+"\"));");
            } else if(action.toLowerCase().matches("movetoelement")) {
                if(cond!=null && expr1==null) {
                    b.append("\n"+acnm+"."+action+"("+cond.getActionableVar()+".get(0));");
                } else if(expr1==null) {
                    b.append("\n"+acnm+"."+action+"(___ce___.get(0));");
                } else {
                	if(expr1.equals("this")) {
                		b.append("\n"+acnm+"."+action+"("+state.currthisat()+".get(0));");
                	} else if(expr1.equals("current")) {
                		b.append("\n"+acnm+"."+action+"(___ce___.get(0));");
                	} else {
	                    String ck = state.unsanitize(expr1);
	                    FindCommand fc = new FindCommand(ck, fileLineDetails, state);
	                    //int counter = state.timeoutNum;
	                    //String cntvar = state.varnamerandom();
	                    //b.append("int "+cntvar+" = 0;\n");
	                    //b.append("\nwhile(true) {\n");
	                    b.append(fc.javacodeonly());
	                    //b.append("if("+fc.getActionableVar()+"!=null)break;\n");
	                    //b.append("sleep("+state.timeoutSleepGranularity+");\n");
	                    //b.append("if("+cntvar+"++=="+counter+")throw new RuntimeException("+fc.getErr()+");\n");
	                    //b.append("}\n");
	                    //b.append("\n"+fc.javacodeonly(null));
	                    b.append("\n"+acnm+"."+action+"("+fc.getActionableVar()+".get(0));");
                	}
                }
            } else if(action.toLowerCase().matches("draganddrop1")) {
                String ck = state.unsanitize(expr1);
                FindCommand fc = new FindCommand(ck, fileLineDetails, state);
                //int counter = state.timeoutNum;
                //String cntvar = state.varnamerandom();
                //b.append("int "+cntvar+" = 0;\n");
                //b.append("\nwhile(true) {\n");
                b.append(fc.javacodeonly());
                //b.append("if("+fc.getActionableVar()+"!=null)break;\n");
                //b.append("sleep("+state.timeoutSleepGranularity+");\n");
                //b.append("if("+cntvar+"++=="+counter+")throw new RuntimeException("+fc.getErr()+");\n");
                //b.append("}\n");
                b.append("List<WebElement> " + state.varname() + " = ___ce___;\n");
                String cvarnm = state.currvarname();
                
                String ck1 = state.unsanitize(expr2);
                FindCommand fc1 = new FindCommand(ck1, fileLineDetails, state);
                //counter = state.timeoutNum;
                //cntvar = state.varnamerandom();
                //b.append("int "+cntvar+" = 0;\n");
                //b.append("\nwhile(true) {\n");
                b.append(fc1.javacodeonly());
                //b.append("if("+fc1.getActionableVar()+"!=null)break;\n");
                //b.append("sleep("+state.timeoutSleepGranularity+");\n");
                //b.append("if("+cntvar+"++=="+counter+")throw new RuntimeException("+fc1.getErr()+");\n");
                //b.append("}\n");
                b.append("List<WebElement> " + state.varname() + " = ___ce___;\n");
                String cvarnm1 = state.currvarname();
                
                b.append("\n"+acnm+".dragAndDrop("+cvarnm+".get(0), "+cvarnm1+".get(0));");
            } else if(action.toLowerCase().matches("draganddrop")) {
                String ck = state.unsanitize(expr1);
                FindCommand fc = new FindCommand(ck, fileLineDetails, state);
                //int counter = state.timeoutNum;
                //String cntvar = state.varnamerandom();
                //b.append("int "+cntvar+" = 0;\n");
                //b.append("\nwhile(true) {\n");
                b.append(fc.javacodeonly());
                //b.append("if("+fc.getActionableVar()+"!=null)break;\n");
                //b.append("sleep("+state.timeoutSleepGranularity+");\n");
                //b.append("if("+cntvar+"++=="+counter+")throw new RuntimeException("+fc.getErr()+");\n");
                //b.append("}\n");
                b.append("List<WebElement> " + state.varname() + " = ___ce___;\n");
                String cvarnm = state.currvarname();
                
                String ck1 = state.unsanitize(expr2);
                FindCommand fc1 = new FindCommand(ck1, fileLineDetails, state);
                //counter = state.timeoutNum;
                //cntvar = state.varnamerandom();
                //b.append("int "+cntvar+" = 0;\n");
                //b.append("\nwhile(true) {\n");
                b.append(fc1.javacodeonly());
                //b.append("if("+fc1.getActionableVar()+"!=null)break;\n");
                //b.append("sleep("+state.timeoutSleepGranularity+");\n");
                //b.append("if("+cntvar+"++=="+counter+")throw new RuntimeException("+fc1.getErr()+");\n");
                //b.append("}\n");
                b.append("List<WebElement> " + state.varname() + " = ___ce___;\n");
                String cvarnm1 = state.currvarname();
                
                b.append("\n"+acnm+".clickAndHold("+cvarnm+".get(0)).moveToElement("+cvarnm1+".get(0)).release("+cvarnm1+".get(0));");
            } else if(action.toLowerCase().matches("movebyoffset")) {
                try {
                    int ck = Integer.parseInt(state.unsanitize(expr1));
                    int ck1 = Integer.parseInt(state.unsanitize(expr2));
                    b.append("\n"+acnm+"."+action+"("+ck+", "+ck1+");");
                } catch (Exception e) {
                    throwParseError(null, new RuntimeException("xOffset and yOffset need to be integer values for moveByOffset"));
                }
            }
            if(!children.isEmpty()) {
                ActionsCommand tcmd = (ActionsCommand)children.get(0);
                b.append(tcmd._javacode(acnm));
            }
            return b.toString();
        }
        String javacode() {
            StringBuilder b = new StringBuilder();
            String acnm = state.varname();
            b.append("\norg.openqa.selenium.interactions.Actions "+acnm+" = new org.openqa.selenium.interactions.Actions(___cw___);");
            if(cond!=null) {
                b.append("\n"+acnm+".moveToElement("+cond.getActionableVar()+".get(0));");
            }
            b.append(_javacode(acnm));
            b.append("\n"+acnm+".perform();");
            return b.toString();
        }
        String javacode(String elnm, boolean a) {
            StringBuilder b = new StringBuilder();
            String acnm = state.varname();
            b.append("\norg.openqa.selenium.interactions.Actions "+acnm+" = new org.openqa.selenium.interactions.Actions(___cw___);");
            if(cond!=null) {
                b.append("\n"+acnm+".moveToElement("+elnm+".get(0));");
            }
            b.append(_javacode(acnm));
            b.append("\n"+acnm+".perform();");
            return b.toString();
        }
    }

    public static class RobotCommand extends FindCommandImpl {
        String action;
        String expr1;

        public RobotCommand(Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
        }
        RobotCommand(String val, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            if(val.equals("scrollup")) {
                val = "keypress " + KeyEvent.VK_UP;
            } else if(val.equals("scrollpageup")) {
                val = "keypress " + KeyEvent.VK_PAGE_UP;
            } else if(val.equals("scrolldown")) {
                val = "keypress " + KeyEvent.VK_DOWN;
            } else if(val.equals("scrollpagedown")) {
                val = "keypress " + KeyEvent.VK_PAGE_DOWN;
            }
            String[] t = val.trim().split("[\t ]+");
            int tl = t.length, counter = 0;
            RobotCommand cmd = this;
            while(counter<tl) {
                if(t[counter].toLowerCase().matches("keydown|keyup|keypress|keyRelease|key")) {
                    cmd.action = t[counter].toLowerCase();
                    boolean flag = true;
                    if(cmd.action.equalsIgnoreCase("keydown") || cmd.action.equalsIgnoreCase("keypress")) {
                        cmd.action = "keyPress";
                    } else if(cmd.action.equalsIgnoreCase("keyup") || cmd.action.equalsIgnoreCase("keyRelease")) {
                    	//flag = false;
                        cmd.action = "keyRelease";
                    } else if(cmd.action.equals("key")) {
                        cmd.action = "key";
                    }
                    if(flag && tl>counter+1) {
                        cmd.expr1 = state.unsanitize(t[++counter]);
                        if(cmd.expr1.charAt(0)==cmd.expr1.charAt(cmd.expr1.length()-1)) {
                            if(cmd.expr1.charAt(0)=='"' || cmd.expr1.charAt(0)=='\'') {
                                cmd.expr1 = cmd.expr1.substring(1, cmd.expr1.length()-1);
                            }
                        }
                    } else if(flag) {
                        throwParseError(null, new RuntimeException("Expression expected after actions (keydown|keyup)"));
                    }
                } else {
                    throwParseError(null, new RuntimeException("Invalid action specified, should be one of (keydown|keyup)"));
                }
                ++counter;
                cmd.children.add(new RobotCommand(cmdDetails, state));
                cmd = (RobotCommand)cmd.children.get(0);
            }
            cmd = this;
            while(!cmd.children.isEmpty()) {
                RobotCommand tcmd = (RobotCommand)cmd.children.get(0);
                if(tcmd.action==null) {
                    cmd.children.clear();
                }
                cmd = tcmd;
            }
        }
        String toCmd() {
            return "robot keydown|keyup|keypress ";
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
        		"Send keys using Robot",
        		"\trobot keydown|keyup|keypress {key-code1} ... keydown|keyup|keypress|keyrelease|key {key-codeN}",
        		"\tscrollup",
        		"\tscrolldown",
        		"\tscrollpageup",
        		"\tscrollpagedown",
        		"Examples :-",
        		"\trobot keydown 1",
        		"\trobot keyup 1",
        		"\trobot keypress 1",
        		"\trobot keypress 1 keydown 2 keyup ",
        		"\tscrollup",
        		"\tscrolldown",
        		"\tscrollpageup",
        		"\tscrollpagedown",
            };
        }
        String _javacode(String acnm) {
            StringBuilder b = new StringBuilder();
            if(action.toLowerCase().matches("keypress|keyrelease|key")) {
                String ck = state.unsanitize(expr1);
                try
                {
                    ck = Integer.decode(ck.toLowerCase()) + "";
                }
                catch (Exception e)
                {
                    throwParseError(null, new RuntimeException("Invalid key code specified"));
                }
                if(action.toLowerCase().equals("key")) {
                    b.append("\n"+acnm+".keyPress("+ck+");");
                    b.append("\n"+acnm+".keyRelease("+ck+");");
                } else {
                    b.append("\n"+acnm+"."+action+"("+ck+");");
                }
            }
            if(!children.isEmpty()) {
                RobotCommand tcmd = (RobotCommand)children.get(0);
                b.append(tcmd._javacode(acnm));
            }
            return b.toString();
        }
        String javacode() {
            StringBuilder b = new StringBuilder();
            String acnm = state.varname();
            b.append("\njava.awt.Robot "+acnm+" = new java.awt.Robot();");
            b.append(_javacode(acnm));
            b.append("\nsleep(100);");
            return b.toString();
        }
    }

    public static class SleepCommand extends Command {
        long ms;
        SleepCommand(String val, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            try
            {
                ms = Long.valueOf(val);
            }
            catch (Exception e)
            {
            }
        }
        String toCmd() {
            return "sleep " + ms;
        }
        String javacode() {
            return "sleep("+ms+");\n";
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
        		"Sleep for milliseconds",
        		"\tsleep {time-in-ms}",
        		"Examples :-",
        		"\tsleep 10000",
            };
        }
    }

    public static class BreakCommand extends Command {
        BreakCommand(Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
        }
        String toCmd() {
            return "break";
        }
        String javacode() {
            if(state.loopCounter==0) {
                throwParseError(null, new RuntimeException("Break is allowed only within a loop block"));
            }
            return "\nbreak;";
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
        		"Break from loop",
        		"\tbreak",
            };
        }
    }

    public static class ContinueCommand extends Command {
        ContinueCommand(Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
        }
        String toCmd() {
            return "continue";
        }
        String javacode() {
            if(state.loopCounter==0) {
                throwParseError(null, new RuntimeException("Break is allowed only within a loop block"));
            }
            return "\ncontinue;";
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
        		"Continue in loop",
        		"\tcontinue",
            };
        }
    }

    public static class ClickCommand extends FindCommandImpl {
        String toCmd() {
            return "click" + (name!=null?name:"") + (cond!=null?cond.toCmd():"");
        }
        ClickCommand(Object[] cmdDetails, CommandState state, String qualifier) {
            super(cmdDetails, state);
            name = qualifier;
        }
        String javacode() {
            StringBuilder b = new StringBuilder();
            if(cond!=null) {
                b.append(cond.javacodeonly());
                b.append("\nclickAction(___ocw___, ___ce___, "+(name!=null?("\""+name+"\""):null)+");\n");
                //b.append(cond.getActionable("click", null, null));
            } else {
                b.append("\nclickAction(___ocw___, "+state.currvarname()+", \""+name+"\");\n");
            }
            return b.toString();
        }
        String javacodeonly(String condvarnm) {
            StringBuilder b = new StringBuilder();
            b.append("\nclickAction(___ocw___, "+condvarnm+", \""+name+"\");\n");
            return b.toString();
        }
        String selcode(String varnm) {
            if(varnm==null) {
                varnm = state.currvarname();
            }
            return "\nclickAction(___ocw___, "+varnm+", \""+name+"\");\n";
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
        		"Click element",
        		"\tclick(fo|nf) {find-expr}",
        		"Examples :-",
        		"\tclick id@'ele1'",
        		"\tclickfo id@'ele1'",
        		"\tclicknf id@'ele1'",
            };
        }
    }

    public static class DoubleClickCommand extends FindCommandImpl {
        String toCmd() {
            return "doubleclick" + (cond!=null?cond.toCmd():"");
        }
        String javacode() {
            StringBuilder b = new StringBuilder();
            if(cond!=null) {
                b.append(cond.javacodeonly());
                //b.append("\nAssert.assertTrue("+cond.condition()+");");
            }
            String hvvrnm = state.varname();
            b.append("\norg.openqa.selenium.interactions.Actions "+hvvrnm+" = new org.openqa.selenium.interactions.Actions(___cw___);");
            b.append(cond.getActionable("doubleClick", null, hvvrnm));
            return b.toString();
        }
        String selcode(String varnm) {
            if(varnm==null) {
                varnm = state.currvarname();
            }
            String hvvrnm = state.varname();
            StringBuilder b = new StringBuilder();
            b.append("\norg.openqa.selenium.interactions.Actions "+hvvrnm+" = new org.openqa.selenium.interactions.Actions(___cw___);");
            b.append(cond.getActionable("doubleClick", null, hvvrnm));
            return b.toString();
        }
        public DoubleClickCommand(Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
        		"Double Click element",
        		"\tdoubleclick {find-expr}",
        		"\tdblclick {find-expr}",
        		"Examples :-",
        		"\tdoubleclick id@'ele1'",
            };
        }
    }

    public static class ClearCommand extends FindCommandImpl {
        String toCmd() {
            return "clear" + (cond!=null?cond.toCmd():"");
        }
        String javacode() {
            StringBuilder b = new StringBuilder();
            if(cond!=null) {
                b.append(cond.javacodeonly());
                b.append(cond.getActionable("clear", null, null));
            } else {
                b.append("\n"+state.currvarname()+".get(0).clear();");
            }
            return b.toString();
        }
        String javacodeonly(String condvarnm) {
            StringBuilder b = new StringBuilder();
            b.append("\n"+condvarnm+".get(0).clear();");
            return b.toString();
        }
        String selcode(String varnm) {
            if(varnm==null) {
                varnm = state.currvarname();
            }
            return "\n"+varnm+".get(0).clear();";
        }
        ClearCommand(Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
        		"Clear element value",
        		"\tclear {find-expr}",
        		"Examples :-",
        		"\tclear id@'ele1'",
            };
        }
    }

    public static class ScrollCommand extends FindCommandImpl {
        String toCmd() {
            return "scroll " + name;
        }
        String javacode() {
            switch(name) {
            	case "up":
            		return "if (___ocw___ instanceof JavascriptExecutor) ((JavascriptExecutor)___ocw___).executeScript(\"window.scrollBy(0,-20)\");\n";
            	case "down":
            		return "if (___ocw___ instanceof JavascriptExecutor) ((JavascriptExecutor)___ocw___).executeScript(\"window.scrollBy(0,20)\");\n";
            	case "pageup":
            		return "if (___ocw___ instanceof JavascriptExecutor) ((JavascriptExecutor)___ocw___).executeScript(\"window.scrollBy(0,-200)\");\n";
            	case "pagedown":
            		return "if (___ocw___ instanceof JavascriptExecutor) ((JavascriptExecutor)___ocw___).executeScript(\"window.scrollBy(0,200)\");\n";
            	case "top":
            		return "if (___ocw___ instanceof JavascriptExecutor) ((JavascriptExecutor)___ocw___).executeScript(\"window.scrollTo(0,0)\");\n";
            	case "bottom":
            		return "if (___ocw___ instanceof JavascriptExecutor) ((JavascriptExecutor)___ocw___).executeScript(\"window.scrollTo(0,document.body.scrollHeight)\");\n";
            	default: {
            		StringBuilder b = new StringBuilder();
            		b.append(cond.javacodeonly());
            		b.append("if (___ocw___ instanceof JavascriptExecutor) ((JavascriptExecutor)___ocw___).executeScript(\"arguments[0].scrollIntoView(true)\", ___ce___.get(0));\nsleep(1000);\n");
            		return b.toString();
            	}
            }
        }
        ScrollCommand(String type, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            name = type;
            if(!name.toLowerCase().trim().matches("up|down|pageup|pagedown|top|bottom")) {
            	try {
            		cond = new FindCommand(name, cmdDetails, state);
				} catch (Exception e) {
				}
            	if(cond==null || cond.by==null || cond.classifier==null) {
            		throwParseError(null, new RuntimeException("scroll with one of (up|down|pageup|pagedown|top|bottom|find-expr) types allowed"));
            	}
            }
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
        		"Scroll [Javascript based]",
        		"\tscroll {up|down|pageup|pagedown|top|bottom|left|right|find-expr}",
        		"Examples :-",
        		"\tscroll up",
        		"\tscroll id@dsdsd",
            };
        }
    }

    public static class SubmitCommand extends FindCommandImpl {
        String toCmd() {
            return "submit" + (cond!=null?cond.toCmd():"");
        }
        String javacode() {
            StringBuilder b = new StringBuilder();
            if(cond!=null) {
                b.append(cond.javacodeonly());
                b.append(cond.getActionable("submit", null, null));
            } else {
                b.append("\n"+state.currvarname()+".get(0).submit();");
            }
            return b.toString();
        }
        String javacodeonly(String condvarnm) {
            StringBuilder b = new StringBuilder();
            b.append("\n"+condvarnm+".get(0).submit();");
            return b.toString();
        }
        String selcode(String varnm) {
            if(varnm==null) {
                varnm = state.currvarname();
            }
            return "\n"+varnm+".get(0).submit();";
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
        		"Submit element",
        		"\tsubmit {find-expr}",
        		"Examples :-",
        		"\tsubmit id@'ele1'",
            };
        }
        SubmitCommand(Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
        }
    }

    public static class AlertCommand extends Command {
        String value;
        String toCmd() {
            return "";
        }
        String javacode() {
            if(value!=null && !value.isEmpty()) {
            	return "\nAssert.assertTrue(\"\", handleAlertConfirm(___cw___, true, false, \""+esc(value)+"\"));\n";
                /*String avn = state.alvarname();
                String c = "Alert "+avn+" = ___cw___.switchTo().alert();\n";
                c += "\nAssert.assertEquals(\""+esc(value)+"\", "+avn+".getText());\n"+avn+".accept();\n";
                return c;*/
            } else {
            	return "\nAssert.assertTrue(\"\", handleAlertConfirm(___cw___, true, false, null));\n";
                //return "___cw___.switchTo().alert().accept();";
            }
        }
        public AlertCommand(String info, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            value = state.unsanitize(info);
            if(value.length()>0 && value.charAt(0)==value.charAt(value.length()-1)) {
                if(value.charAt(0)=='"' || value.charAt(0)=='\'') {
                    value = value.substring(1, value.length()-1);
                }
            }
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
        		"Show alert with message",
        		"\talert {value}",
        		"Examples :-",
        		"\talert",
        		"\talert 'Hello'",
            };
        }
    }

    public static class ConfirmCommand extends Command {
        String value;
        boolean isOk = true;
        String toCmd() {
            return "";
        }
        String javacode() {
            if(value!=null && !value.isEmpty()) {
            	return "\nAssert.assertTrue(\"\", handleAlertConfirm(___cw___, true, "+isOk+", \""+esc(value)+"\"));\n";
                /*String avn = state.alvarname();
                String c = "Alert "+avn+" = ___cw___.switchTo().alert();\n";
                c += "\nAssert.assertEquals(\""+esc(value)+"\", "+avn+".getText());\n"+avn+"."+(isOk?"accept":"dismiss")+"();\n";
                return c;*/
            } else {
            	return "\nAssert.assertTrue(\"\", handleAlertConfirm(___cw___, true, "+isOk+", null));\n";
                //return "___cw___.switchTo().alert()."+(isOk?"accept":"dismiss")+"();";
            }
        }
        public ConfirmCommand(String info, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            String[] t = info.equals("")?new String[] {"ok"}:info.trim().split("[\t ]+");
            if(t[0].trim().isEmpty()) {
                t[0] = "ok";
            }
            if(!t[0].toLowerCase().trim().matches("ok|cancel|yes|no|true|false|1|0")) {
                throwParseError(null, new RuntimeException("confirm dialog can be accepted or dismissed, one of (ok|cancel|yes|no) allowed"));
            }
            if(t[0].toLowerCase().trim().matches("cancel|no|false|0")) {
                isOk = false;
            }
            if(t.length>1)
            {
                value = state.unsanitize(t[1].trim());
                if(value.length()>0 && value.charAt(0)==value.charAt(value.length()-1)) {
                    if(value.charAt(0)=='"' || value.charAt(0)=='\'') {
                        value = value.substring(1, value.length()-1);
                    }
                }
            }
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
        		"Handle Confirm Dialog",
        		"\tconfirm ok|cancel|yes|no {optional button-text-to-check}",
        		"Examples :-",
        		"\tconfirm ok",
        		"\tconfirm yes",
        		"\tconfirm cancel",
        		"\tconfirm no",
        		"\tconfirm yes 'Confirm'",
            };
        }
    }

    public static class MZoomCommand extends FindCommandImpl {
        int x, y;
        String toCmd() {
            return "";
        }
        String javacode() {
            StringBuilder b = new StringBuilder();
            if(cond!=null) {
                b.append(cond.javacodeonly());
                b.append("\nmzoompinch(___cw___, ___ce___.get(0), -1, -1, true);");
            } else {
                b.append("\nmzoompinch(___cw___, null, "+x+", "+y+", true);");
            }
            return b.toString();
        }
        String selcode(String varnm) {
            if(varnm==null) {
                varnm = state.currvarname();
            }
            return "\nmzoompinch(___cw___, ___ce___.get(0), -1, -1, true);";
        }
        public MZoomCommand(String info, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            String[] t = info.trim().split("[\t ]+");
            if(t.length==1)
            {
                cond = new FindCommand(t[0].trim(), fileLineDetails, state);
            }
            else if(t.length==2)
            {
                try
                {
                    x = Integer.parseInt(t[0].trim());
                    y = Integer.parseInt(t[1].trim());
                }
                catch (Exception e)
                {
                    throwParseError(null, new RuntimeException("zoom command needs valid x/y co-ordinates"));
                }
            }
            else
            {
                throwParseError(null, new RuntimeException("zoom command needs valid x/y co-ordinates"));
            }
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
        		"Mobile Zoom",
        		"\tzoom ({x-co-ordinate} {y-co-ordinate}|{find-expr})",
        		"Examples :-",
        		"\tzoom 123 234",
        		"\tzoom id@'ele'",
            };
        }
    }

    public static class MPinchCommand extends FindCommandImpl {
        int x, y;
        String toCmd() {
            return "";
        }
        String javacode() {
            StringBuilder b = new StringBuilder();
            if(cond!=null) {
                b.append(cond.javacodeonly());
                b.append("\nmzoompinch(___cw___, ___ce___.get(0), -1, -1, false);");
            } else {
                b.append("\nmzoompinch(___cw___, null, "+x+", "+y+", false);");
            }
            return b.toString();
        }
        String selcode(String varnm) {
            if(varnm==null) {
                varnm = state.currvarname();
            }
            return "\nmzoompinch(___cw___, ___ce___.get(0), -1, -1, true);";
        }
        public MPinchCommand(String info, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            String[] t = info.trim().split("[\t ]+");
            if(t.length==1)
            {
                cond = new FindCommand(t[0].trim(), fileLineDetails, state);
            }
            else if(t.length==2)
            {
                try
                {
                    x = Integer.parseInt(t[0].trim());
                    y = Integer.parseInt(t[1].trim());
                }
                catch (Exception e)
                {
                    throwParseError(null, new RuntimeException("pinch command needs valid x/y co-ordinates"));
                }
            }
            else
            {
                throwParseError(null, new RuntimeException("pinch command needs valid x/y co-ordinates"));
            }
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
        		"Mobile Pinch",
        		"\tpinch ({x-co-ordinate} {y-co-ordinate}|{find-expr})",
        		"Examples :-",
        		"\tpinch 123 234",
        		"\tpinch id@'ele'",
            };
        }
    }

    public static class MSwipeCommand extends FindCommandImpl {
        int sx, sy, ex, ey, d = 1000;
        String toCmd() {
            return "";
        }
        String javacode() {
            return "\ndoSwipe(___cw___, new org.openqa.selenium.Point("+sx+", "+sy+"), new org.openqa.selenium.Point("+ex+", "+ey+"), "+d+");";
        }
        public MSwipeCommand(String info, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            String[] t = info.trim().split("[\t ]+");
            if(t.length<4)
            {
                throwParseError(null, new RuntimeException("swipe command needs valid start(x/y) and end(x/y) co-ordinates"));
            }
            else
            {
                try
                {
                    sx = Integer.parseInt(t[0].trim());
                    sy = Integer.parseInt(t[1].trim());
                    ex = Integer.parseInt(t[2].trim());
                    ey = Integer.parseInt(t[3].trim());
                    if(t.length>4) {
                        d = Integer.parseInt(t[4].trim());
                    }
                }
                catch (Exception e)
                {
                    throwParseError(null, new RuntimeException("swipe command needs valid start(x/y) and end(x/y) co-ordinates and/or duration(milliseconds)"));
                }
            }
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
        		"Mobile Swipe",
        		"\tswipe {start-x-co-ordinate} {start-y-co-ordinate} {end-x-co-ordinate} {end-y-co-ordinate}",
        		"Examples :-",
        		"\tswipe 123 234 200 300",
            };
        }
    }

    public static class MTapCommand extends FindCommandImpl {
        int x = Integer.MAX_VALUE , y = Integer.MAX_VALUE, d = 500;
        String toCmd() {
            return "";
        }
        String javacode() {
            return "\ndoTap(___cw___, null, new org.openqa.selenium.Point("+x+", "+y+"), "+d+");";
        }
        String selcode(String varnm) {
            if(varnm==null) {
                varnm = state.currvarname();
            }
            return "\ndoTap(___cw___, "+varnm+".get(0), null, "+d+")";
            //return "\nnew io.appium.java_client.TouchAction((io.appium.java_client.MobileDriver) get___d___()).tap("+varnm+".get(0)).perform();";
        }
        public MTapCommand(String info, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            String[] t = info.trim().split("[\t ]+");
            if(t.length==1)
            {
                cond = new FindCommand(t[0].trim(), fileLineDetails, state);
            }
            else if(t.length==2)
            {
                try
                {
                    x = Integer.parseInt(t[0].trim());
                    y = Integer.parseInt(t[1].trim());
                }
                catch (Exception e)
                {
                    cond = new FindCommand(t[0].trim(), fileLineDetails, state);
                    try
                    {
                        d = Integer.parseInt(t[1].trim());
                    }
                    catch (Exception e1)
                    {
                        throwParseError(null, new RuntimeException("tap command needs valid {find-expr}/x-co-ordinate/y-co-ordinate/duration values"));
                    }
                }
            }
            else if(t.length==3)
            {
                try
                {
                    x = Integer.parseInt(t[0].trim());
                    y = Integer.parseInt(t[1].trim());
                    d = Integer.parseInt(t[2].trim());
                }
                catch (Exception e)
                {
                    cond = new FindCommand(t[0].trim(), fileLineDetails, state);
                    try
                    {
                        x = Integer.parseInt(t[1].trim());
                        y = Integer.parseInt(t[2].trim());
                    }
                    catch (Exception e1)
                    {
                        throwParseError(null, new RuntimeException("tap command needs valid {find-expr}/x-co-ordinate/y-co-ordinate/duration values"));
                    }
                }
            }
            else if(t.length==4)
            {
                cond = new FindCommand(t[0].trim(), fileLineDetails, state);
                try
                {
                    x = Integer.parseInt(t[1].trim());
                    y = Integer.parseInt(t[2].trim());
                    d = Integer.parseInt(t[3].trim());
                }
                catch (Exception e1)
                {
                    throwParseError(null, new RuntimeException("tap command needs valid {find-expr}/x-co-ordinate/y-co-ordinate/duration values"));
                }
            }
            else
            {
                throwParseError(null, new RuntimeException("tap command needs valid x-co-ordinate/y-co-ordinate/duration values"));
            }
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
        		"Mobile Tap",
        		"\ttap ({find-expr}|{find-expr} {duration}|{x-co-ordinate} {y-co-ordinate}|{x-co-ordinate} {y-co-ordinate} {duration}|{find-expr} {x-co-ordinate} {y-co-ordinate}|{find-expr} {x-co-ordinate} {y-co-ordinate} {duration})",
        		"Examples :-",
        		"\ttap id@'ele'",
        		"\ttap id@'ele' 2000",
        		"\ttap id@'ele' 123 234",
        		"\ttap id@'ele' 123 234 2000",
        		"\ttap 123 234",
        		"\ttap 123 234 2000",
            };
        }
    }

    public static class MRotateCommand extends Command {
        String toCmd() {
            return "";
        }
        String javacode() {
            StringBuilder b = new StringBuilder();
            String vn = state.varname();
            b.append("org.openqa.selenium.ScreenOrientation "+vn+" = ((io.appium.java_client.remote.SupportsRotation)___cw___).getOrientation();\n");
            b.append("if("+vn+".value().equals(org.openqa.selenium.ScreenOrientation.LANDSCAPE.value())){((io.appium.java_client.remote.SupportsRotation)___cw___).rotate(org.openqa.selenium.ScreenOrientation.PORTRAIT);}");
            b.append("else{((io.appium.java_client.remote.SupportsRotation)___cw___).rotate(org.openqa.selenium.ScreenOrientation.LANDSCAPE);}");
            return b.toString();
        }
        public MRotateCommand(Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
        		"Mobile rotate",
        		"\trotate",
            };
        }
    }

    public static class MHideKeyPadCommand extends Command {
        String toCmd() {
            return "";
        }
        String javacode() {
            StringBuilder b = new StringBuilder();
            b.append("try{Thread.sleep(200);if(___cw___ instanceof io.appium.java_client.HidesKeyboard) ((io.appium.java_client.HidesKeyboard) ___cw___).hideKeyboard();}catch(Exception e){}");
            return b.toString();
        }
        public MHideKeyPadCommand(Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
        		"Mobile hide keypad",
        		"\thidekeypad",
            };
        }
    }

    public static class MShakeCommand extends Command {
        String toCmd() {
            return "";
        }
        String javacode() {
            StringBuilder b = new StringBuilder();
            b.append("if(___cw___ instanceof io.appium.java_client.ios.IOSDriver){((io.appium.java_client.ios.IOSDriver) ___cw___).shake();}");
            return b.toString();
        }
        public MShakeCommand(Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
        		"Mobile shake",
        		"\tshake",
            };
        }
    }

    public static class MTouchActionCommand extends Command {
        String action, expr;
        Integer x, y, d;

        public MTouchActionCommand(Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
        		"Mobile Touch",
        		"\ttouch ({press|moveto|tap {find-expr}|{find-expr} {x-co-ordinate} {y-co-ordinate}|{x-co-ordinate} {y-co-ordinate}}|"
        		+ "longpress|{longpress {find-expr} {x-co-ordinate} {y-co-ordinate} {duration}|{find-expr} {x-co-ordinate} {y-co-ordinate}|"
        		+ "{find-expr} {duration}|{x-co-ordinate} {y-co-ordinate} {duration}|{x-co-ordinate} {y-co-ordinate}}|"
        		+ "{wait {duration}}|release) ... ({press|moveto|tap {find-ex...",
        		"Examples :-",
        		"\ttouch moveto id@'ele' longpress moveto id@'ele2' wait 1000 release",
        		"\ttouch moveto id@'ele' longpress id@'ele1' moveto id@'ele2' wait 1000 release",
            };
        }
        MTouchActionCommand(String val, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            String[] t = val.trim().split("[\t ]+");
            int tl = t.length, counter = 0;
            MTouchActionCommand cmd = this;
            while(counter<tl) {
                if(t[counter].toLowerCase().matches("press|moveto|tap")) {
                    cmd.action = t[counter];
                    if(cmd.action.toLowerCase().equals("moveto")) {
                        cmd.action = "moveTo";
                    }
                    int fc = 0;
                    for (int i = 1; i < 4; i++)
                    {
                        if(tl<=counter+i)break;
                        if(t[counter+i].matches("press|moveto|tap|longpress|wait|release")) {
                            break;
                        }
                        fc++;
                    }
                    if(fc==3) {
                        cmd.expr = t[++counter];
                        try
                        {
                            cmd.x = Integer.parseInt(t[++counter]);
                            cmd.y = Integer.parseInt(t[++counter]);
                        }
                        catch (Exception e)
                        {
                            throwParseError(null, new RuntimeException("Invalid x/y co-ordinates specified for actions (press|moveto|tap)"));
                        }
                    } else if(fc==2) {
                        try
                        {
                            cmd.x = Integer.parseInt(t[++counter]);
                            cmd.y = Integer.parseInt(t[++counter]);
                        }
                        catch (Exception e)
                        {
                            throwParseError(null, new RuntimeException("Invalid x/y co-ordinates specified for actions (press|moveto|tap)"));
                        }
                    } else if(fc==1) {
                        cmd.expr = t[++counter];
                    } else {
                        throwParseError(null, new RuntimeException("Expressions(>=1 <=3) expected after actions (press|moveto|tap)"));
                    }
                } else if(t[counter].toLowerCase().matches("longpress")) {
                    cmd.action = "longPress";
                    int fc = 0;
                    for (int i = 1; i < 5; i++)
                    {
                        if(tl<=counter+i)break;
                        if(t[counter+i].matches("press|moveto|tap|longpress|wait|release")) {
                            break;
                        }
                        fc++;
                    }
                    if(fc==4) {
                        cmd.expr = t[++counter];
                        try
                        {
                            cmd.x = Integer.parseInt(t[++counter]);
                            cmd.y = Integer.parseInt(t[++counter]);
                            cmd.d = Integer.parseInt(t[++counter]);
                        }
                        catch (Exception e)
                        {
                            throwParseError(null, new RuntimeException("Invalid x/y/d values specified for actions (longpress)"));
                        }
                    } else if(fc==3) {
                        try
                        {
                            Integer.parseInt(t[counter+1]);
                            cmd.x = Integer.parseInt(t[++counter]);
                            cmd.y = Integer.parseInt(t[++counter]);
                            cmd.d = Integer.parseInt(t[++counter]);
                        }
                        catch (Exception e)
                        {
                            cmd.expr = t[++counter];
                            try
                            {
                                cmd.x = Integer.parseInt(t[++counter]);
                                cmd.y = Integer.parseInt(t[++counter]);
                            }
                            catch (Exception e1)
                            {
                                throwParseError(null, new RuntimeException("Invalid x/y co-ordinates specified for actions (longpress)"));
                            }
                        }
                    } else if(fc==2) {
                        try
                        {
                            Integer.parseInt(t[counter+1]);
                            cmd.x = Integer.parseInt(t[++counter]);
                            cmd.y = Integer.parseInt(t[++counter]);
                        }
                        catch (Exception e)
                        {
                            cmd.expr = t[++counter];
                            try
                            {
                                cmd.d = Integer.parseInt(t[++counter]);
                            }
                            catch (Exception e1)
                            {
                                throwParseError(null, new RuntimeException("Invalid d value specified for actions (longpress)"));
                            }
                        }
                    } else if(fc==1) {
                        cmd.expr = t[++counter];
                    } else {
                        //throwParseError(null, new RuntimeException("Expressions(>=1 <=3) expected after actions (longpress)"));
                    }
                } else if(t[counter].toLowerCase().matches("wait")) {
                    cmd.action = "waitAction";
                    if(!t[counter+1].toLowerCase().matches("press|moveto|tap|longpress|wait|release")) {
                        try
                        {
                            cmd.d = Integer.parseInt(t[++counter]);
                        }
                        catch (Exception e)
                        {
                            throwParseError(null, new RuntimeException("Invalid duration specified for actions (wait)"));
                        }
                    }
                } else if(t[counter].toLowerCase().matches("release")) {
                    cmd.action = t[counter];
                } else {
                    throwParseError(null, new RuntimeException("Invalid action specified, should be one of (press|moveto|tap|longpress|wait|release)"));
                }
                ++counter;
                cmd.children.add(new MTouchActionCommand(cmdDetails, state));
                cmd = (MTouchActionCommand)cmd.children.get(0);
            }
            cmd = this;
            while(!cmd.children.isEmpty()) {
                MTouchActionCommand tcmd = (MTouchActionCommand)cmd.children.get(0);
                if(tcmd.action==null) {
                    cmd.children.clear();
                }
                cmd = tcmd;
            }
        }
        String toCmd() {
            return "touch ";
        }
        String _javacode(String acnm) {
            StringBuilder b = new StringBuilder();
            if(action.toLowerCase().matches("press|tap|moveto")) {
                if(expr!=null) {
                    FindCommand cond = new FindCommand(expr, fileLineDetails, state);
                    b.append("\n"+cond.javacodeonly());
                    if(x==null) {
                        b.append("\n"+acnm+"."+action+"(___ce___.get(0));");
                    } else {
                        b.append("\n"+acnm+"."+action+"(___ce___.get(0), "+x+", "+y+");");
                    }
                } else {
                    b.append("\n"+acnm+"."+action+"("+x+", "+y+");");
                }
            } else if(action.toLowerCase().matches("longpress")) {
                if(expr!=null) {
                    FindCommand cond = new FindCommand(expr, fileLineDetails, state);
                    b.append("\n"+cond.javacodeonly());
                    if(d==null) {
                        if(x==null) {
                            b.append("\n"+acnm+"."+action+"(___ce___.get(0));");
                        } else {
                            b.append("\n"+acnm+"."+action+"(___ce___.get(0), "+x+", "+y+");");
                        }
                    } else {
                        if(x==null) {
                            b.append("\n"+acnm+"."+action+"(___ce___.get(0), java.time.Duration.ofMillis("+d+"));");
                        } else {
                            b.append("\n"+acnm+"."+action+"(___ce___.get(0), "+x+", "+y+", java.time.Duration.ofMillis("+d+"));");
                        }
                    }
                } else {
                	if(x!=null && y!=null) {
	                    if(d==null) {
	                        b.append("\n"+acnm+"."+action+"("+x+", "+y+");");
	                    } else {
	                        b.append("\n"+acnm+"."+action+"("+x+", "+y+", java.time.Duration.ofMillis("+d+"));");
	                    }
                	} else {
                		String cvarnm = state.currvarname();
                		if(d==null) {
                            if(x==null) {
                                b.append("\n"+acnm+"."+action+"(___ce___.get(0));");
                            } else {
                                b.append("\n"+acnm+"."+action+"("+cvarnm+".get(0), "+x+", "+y+");");
                            }
                        } else {
                            if(x==null) {
                                b.append("\n"+acnm+"."+action+"(___ce___.get(0), java.time.Duration.ofMillis("+d+"));");
                            } else {
                                b.append("\n"+acnm+"."+action+"(___ce___.get(0), "+x+", "+y+", java.time.Duration.ofMillis("+d+"));");
                            }
                        }
                	}
                }
            } else if(action.toLowerCase().matches("wait")) {
                if(d!=null) {
                    b.append("\n"+acnm+"."+action+"(java.time.Duration.ofMillis("+d+"));");
                } else {
                    b.append("\n"+acnm+"."+action+"();");
                }
            } else if(action.toLowerCase().matches("release")) {
                b.append("\n"+acnm+"."+action+"();");
            }
            if(!children.isEmpty()) {
                MTouchActionCommand tcmd = (MTouchActionCommand)children.get(0);
                b.append(tcmd._javacode(acnm));
            }
            return b.toString();
        }
        String javacode() {
            StringBuilder b = new StringBuilder();
            String acnm = state.varname();
            b.append("\nio.appium.java_client.TouchAction "+acnm+" = new io.appium.java_client.TouchAction((io.appium.java_client.PerformsTouchActions)___cw___);");
            b.append(_javacode(acnm));
            b.append("\n"+acnm+".perform();");
            return b.toString();
        }
    }

    public static String unSantizedUnQuoted(String value, CommandState state) {
        value = state.unsanitize(value.trim());
        if(value.charAt(0)==value.charAt(value.length()-1)) {
            if(value.charAt(0)=='"' || value.charAt(0)=='\'') {
                value = value.substring(1, value.length()-1);
            }
        }
        return value;
    }

    public static class PluginCommand extends Command {
        String name;
        List<String> in = new ArrayList<String>();
        List<String[]> in1 = new ArrayList<String[]>();
        String outtype;
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
        public void reconcile() {
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
    
    public static String validateSel(String[] args, String className, boolean testExecution) throws Exception {
        GatfExecutorConfig config = getConfig(args.length>2?args[2].trim():"gatf-config.xml", args.length>3?args[3].trim():"/workdir");
        config.setSeleniumLoggerPreferences("browser(OFF),client(OFF),driver(OFF),performance(OFF),profiler(OFF),server(OFF)");
        for (SeleniumDriverConfig selConf : config.getSeleniumDriverConfigs())
        {
            if(selConf.getDriverName()!=null && selConf.getPath()!=null && new File(selConf.getPath()).exists()) {
                System.setProperty(selConf.getDriverName(), selConf.getPath());
            }
        }
        
        AcceptanceTestContext c = new AcceptanceTestContext(); 
        c.setGatfExecutorConfig(config);
        c.validateAndInit(true);
        c.getWorkflowContextHandler().initializeSuiteContext(1);
        
        if(args[1].indexOf(File.separator)==-1) {
        	args[1] = "/workdir/"+args[1];
        }
    	
        Object[] retvals = new Object[5];
        try {
        	Map<Integer, Object[]> selToJavaLineMap = new HashMap<Integer, Object[]>();
        	SeleniumTest dyn = null;
        	if(className!=null) {
        		dyn = (SeleniumTest)Class.forName(className).getConstructor(new Class[] {AcceptanceTestContext.class, int.class}).newInstance(new Object[] {c, 0});
        	} else {
        		dyn = SeleniumCodeGeneratorAndUtil.getSeleniumTest(args[1], Command.class.getClassLoader(), c, retvals, config, args.length>4?args[4].trim().equalsIgnoreCase("true"):false, selToJavaLineMap);
        	}
            System.out.println(dyn!=null?"SUCCESS":"FAILURE");
            if(dyn!=null) {
            	if(testExecution) {
            		try {
            			final LoggingPreferences lp = SeleniumCodeGeneratorAndUtil.getLp(config);
						dyn.execute(lp);
					} catch (Exception e) {
						e.printStackTrace();
					} finally {
						dyn.quitAll();
					}
            	}
            	return "{\"status\": \"SUCCESS\"}";
            }
        	Map<String, String> mpe = new HashMap<String, String>();
        	mpe.put("status", "FAILURE");
        	mpe.put("error", retvals[4].toString());
            return WorkflowContextHandler.OM.writeValueAsString(mpe);
        } catch (GatfSelCodeParseError e) {
        	e.printStackTrace();
        	Map<String, String> mpe = new HashMap<String, String>();
        	mpe.put("status", "FAILURE");
        	mpe.put("error", e.getMessage());
            return WorkflowContextHandler.OM.writeValueAsString(mpe);
        } catch (Throwable e) {
        	e.printStackTrace();
        	Map<String, String> mpe = new HashMap<String, String>();
        	mpe.put("status", "FAILURE");
        	mpe.put("error", "Unable to compile seleasy script " + args[1]);
            return WorkflowContextHandler.OM.writeValueAsString(mpe);
        }
    }
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
	public static void main(String[] args) throws Exception {
    	System.out.println(Command.toSampleSelCmd());
    	Reflections r = new Reflections("com.gatf.selenium");
    	Set<Class<? extends Command>> classes = r.getSubTypesOf(Command.class);
    	Set<Class<?>> pclasses = new HashSet<Class<?>>();
    	pclasses.add(ApiPlugin.class);
    	pclasses.add(CurlPlugin.class);
    	pclasses.add(JsonPlugin.class);
    	pclasses.add(XmlPlugin.class);
    	for (Class c : classes) {
    		Method m = c.getMethod("toSampleSelCmd", new Class[]{});
    		String[] cmds = (String[])m.invoke(null, new Object[] {});
    		//System.out.println(c.getName());
    		if(cmds==null) {
    			continue;
    		}
    		for (String s : cmds) {
				System.out.println(s);
			}
    		System.out.println("\n");
		}
    	for (Class c : pclasses) {
    		Method m = c.getMethod("toSampleSelCmd", new Class[]{});
    		String[] cmds = (String[])m.invoke(null, new Object[] {});
    		//System.out.println(c.getName());
    		if(cmds==null) {
    			continue;
    		}
    		for (String s : cmds) {
				System.out.println(s);
			}
    		System.out.println("\n");
		}

    	validateSel(new String[] {"-validate-sel", "data/test.sel",
    			"/path/to/project/gatf-config.xml",
        		"/path/to/project/", "true"}, null, false);
    	/*validateSel(new String[] {"-validate-sel", "data/ui-auto.sel",
        		"/path/to/project/gatf-config.xml",
        		"/path/to/project/", "true"}, null, false);
    	validateSel(new String[] {"-validate-sel", "main/clinical.sel",
        		"/path/to/project/gatf-config.xml",
        		"/path/to/project/", "true"}, null, false);*/

        /*List<String> ___a___1 = new ArrayList<String>();
        ___a___1.add("{\"a\": 1}");
        Object o = t.pluginize("jsonread", "com.gatf.selenium.plugins.JsonPlugin@read", ___a___1, null);
        System.out.println(o);
        t.___add_var__("o", o);
        ___a___1.clear();
        ___a___1.add("$v{o}");
        System.out.println(t.pluginize("jsonwrite", "com.gatf.selenium.plugins.JsonPlugin@write", ___a___1, null));
        ___a___1.clear();
        ___a___1.add("a");
        ___a___1.add("$v{o}");
        System.out.println(t.pluginize("jsonpath", "com.gatf.selenium.plugins.JsonPlugin@path", ___a___1, null));

        ___a___1 = new ArrayList<String>();
        ___a___1.add("<as><a><b>1</b></a></as>");
        o = t.pluginize("xmlread", "com.gatf.selenium.plugins.XmlPlugin@read", ___a___1, null);
        System.out.println(o);
        t.___add_var__("xo", o);
        ___a___1.clear();
        ___a___1.add("$v{xo}");
        System.out.println(t.pluginize("xmlwrite", "com.gatf.selenium.plugins.XmlPlugin@write", ___a___1, null));
        ___a___1.clear();
        ___a___1.add("as/a/b");
        ___a___1.add("$v{xo}");
        System.out.println(t.pluginize("xmlpath", "com.gatf.selenium.plugins.XmlPlugin@path", ___a___1, null));
        ___a___1.clear();
        ___a___1.add("get");
        ___a___1.add("http://google.co.in");
         o = t.pluginize("curl", "com.gatf.selenium.plugins.CurlPlugin@execute@true", ___a___1, null);
         System.out.println(o);

         t.___add_var__("xxo", o);
         ___a___1.clear();
         ___a___1.add("headers.X-Frame-Options");
         ___a___1.add("$v{xxo}");
         System.out.println(t.pluginize("jsonpath", "com.gatf.selenium.plugins.JsonPlugin@path", ___a___1, null));*/

        //testSelScript(c);
    }
    
    public static GatfExecutorConfig getConfig(String configFile, String testCasesBasePath) {
        GatfExecutorConfig configuration = null;
        if(configFile!=null) {
            try {
                File resource = new File(configFile);
                if(!resource.exists())
                {
                	resource = new File(new File(testCasesBasePath), configFile);
                }
                if(resource.exists()) {
                	if(configFile.trim().endsWith(".xml")) {
	                    configuration = WorkflowContextHandler.XOM.readValue(resource, GatfExecutorConfig.class);
                	} else if(configFile.trim().endsWith(".json")) {
                		configuration = WorkflowContextHandler.OM.readValue(resource, GatfExecutorConfig.class);
                	} else {
                		throw new RuntimeException("Invalid Config file, please provide either an xml or a json config file");
                	}
                    
                    if(configuration.getTestCasesBasePath()==null)
                        configuration.setTestCasesBasePath(testCasesBasePath);
                    
                    if(configuration.getOutFilesBasePath()==null)
                        configuration.setOutFilesBasePath(testCasesBasePath);
                    
                    if(configuration.getTestCaseDir()==null)
                        configuration.setTestCaseDir("");
                    
                    if(configuration.getNumConcurrentExecutions()==null)
                        configuration.setNumConcurrentExecutions(1);
                    
                    if(configuration.getHttpConnectionTimeout()==null)
                        configuration.setHttpConnectionTimeout(10000);
                    
                    if(configuration.getHttpRequestTimeout()==null)
                        configuration.setHttpRequestTimeout(10000);
                    
                    if(!configuration.isHttpCompressionEnabled())
                        configuration.setHttpCompressionEnabled(true);
                    
                    if(configuration.getConcurrentUserSimulationNum()==null)
                        configuration.setConcurrentUserSimulationNum(1);
                    
                    if(configuration.getLoadTestingReportSamples()==null)
                        configuration.setLoadTestingReportSamples(1);
                    
                    if(configuration.getConcurrentUserRampUpTime()==null)
                        configuration.setConcurrentUserRampUpTime(10000L);
                    
                    if(configuration.isEnabled()==null)
                        configuration.setEnabled(true);
                    
                    if(configuration.getRepeatSuiteExecutionNum()==null)
                        configuration.setRepeatSuiteExecutionNum(0);
                    
                    configuration.setJavaVersion(System.getProperty("java.version"));
                }
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }
        return configuration;
    }

    public static void testSelScript(AcceptanceTestContext c) throws Exception {
        Map<String, Object> _mt = new HashMap<String, Object>();
        _mt.put("a", "test");
        c.getWorkflowContextHandler().templatize(_mt, "dsadasd ${a} $v{f}");
    }
}
