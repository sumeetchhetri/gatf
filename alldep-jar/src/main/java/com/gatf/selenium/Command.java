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

//https://www.autoitscript.com/site/autoit/
//http://sikulix.com/

public class Command {
	
    protected static class CommandState {
    	XPathFactory factory = XPathFactory.newInstance();
        boolean commentStart = false, codeStart = false, started = false;
        String origBasePath = "";
        String basePath = "";
        boolean isImportedFile = false;
        String testcaseDir = "";
        int concExec = 0;
        int starts = 0;
        int ends = 0;
       
        int ifTimeoutNum = 1;
        int timeoutNum = 60;
        long timeoutSleepGranularity = 1000;
       
        int modeExecType = 0;
        boolean isSTGenPhase = false;
        boolean modeEnableGlobalTimeouts = true;
        boolean modeEnableConcurrentWebDriver = false;
        boolean hasNetapixCommand = false;
        // E13: this file is the configured loginOnce script: a plain `close` first saves the storage state
        boolean isLoginOnceScript = false;
        // M4: the script uses route/unroute: non-Chromium sessions then ask for a BiDi socket (webSocketUrl)
        boolean hasRouteCommand = false;
        /** M8/M11: emulate / clock need CDP or the BiDi socket (webSocketUrl asked for like route's). */
        boolean hasEmulateCommand = false;
        // M1: the project's config has autoWait on; codegen then also emits probes for raw Actions drags
        boolean autoWait = false;

        int NUMBER = 1;
        int NUMBER_COND = 1;
        int NUMBER_EXC = 1;
        int NUMBER_ST = 1;
        int NUMBER_AL = 1;
        int NUMBER_RD = 1;
        int NUMBER_TH = 1;
        int loopCounter = 0;
        String invXpathErrs = "";

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
        Set<String> systemReservedKeys = new HashSet<String>() {{
        	add("loop over");
        	add("begin code in");
        	add("end code");
        	add("if");
        	add("ifnot");
        	add("else if");
        	add("else ifnot");
        	add("else");
        	add("wait till visible");
        	add("wait for");
        }};
		Set<String> subtestDups = new HashSet<String>();
        List<Command> allSubTests = new ArrayList<Command>();
        Set<String> layers = new HashSet<String>();
        
        String layerStr = null;
        
        Properties configProps = null;
        Map<String, String> dynProps = new HashMap<String, String>();
        Map<String, String> functions = new HashMap<>();
        
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
        
        /** M6: the file's hook block of this kind, or null. */
        SubTestCommand hook(String kind) {
            for (Command c : allSubTests) {
                if(c instanceof SubTestCommand && kind.equals(((SubTestCommand)c).hook)) return (SubTestCommand)c;
            }
            return null;
        }

        /** M6: true when a subtest of this script (not an imported one) is marked {@code only}. */
        boolean hasOnly() {
            return firstOnly() != null;
        }

        /** E10: the first subtest of this script (not an imported one) marked {@code only}, or null. */
        SubTestCommand firstOnly() {
            for (Command c : allSubTests) {
                if(c instanceof SubTestCommand && "only".equals(((SubTestCommand)c).marker) && !((SubTestCommand)c).imported) return (SubTestCommand)c;
            }
            return null;
        }

        void addSubtest(SubTestCommand st) {
            if(st.hook!=null) {
                if(isImportedFile) return;   // M6: hooks belong to their own script; an imported file lends subtests/funcs only
                if(hook(st.hook)!=null) {
                    throwError(st.fileLineDetails, new RuntimeException("Duplicate " + st.hook + " hook defined"));
                }
                allSubTests.add(st);
                return;
            }
            st.imported = isImportedFile;
            if(subtestDups.contains(st.name)) {
                throwError(st.fileLineDetails, new RuntimeException("Duplicate "+(st.isAFunc?"func":"subtest")+" defined"));
            }
            for (String rkey : systemReservedKeys) {
				if(rkey.startsWith(st.name)) {
					throwError(st.fileLineDetails, new RuntimeException((st.isAFunc?"func":"subtest") + " name cannot begin with a reserved keyword ["+rkey.trim()+"]"));
				}
			}
            subtestDups.add(st.name);
            allSubTests.add(st);
            //subtestDetails.add(new Object[]{st.name, st.sessionName, st.sessionId+"", st.fileLineDetails, st.fName, true, st.isAFunc});
        }
        
        void addSubtest(ExecSubTestCommand st) {
            if(!st.state.isImportedFile) {
            	subtestDetails.add(new Object[]{st.subt.name, st.sessionName, st.sessionId+"", st.fileLineDetails, st.subt.fName, false, st.subt.isAFunc});
            }
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
            Pattern p = Pattern.compile("\"(?:\\\\\\\"|[^\\\"])*\"");
            Matcher m = p.matcher(cmd);
            while (m.find()) {
            	String g = m.toMatchResult().group();
                if(!qss.containsKey(g)) {
                	String repl = "######" + UUID.randomUUID().toString() + "######";
                    qss.put(g, repl);
                    cmd = cmd.replace(g, repl);
                } else {
                	cmd = cmd.replace(g, qss.get(g));
                }
            }
            /*for (String qs : qss.keySet())
            {
                cmd = cmd.replace(qs, qss.get(qs));
            }*/
            p = Pattern.compile("'(?:\\\\'|[^'])*'");
            m = p.matcher(cmd);
            while (m.find()) {
            	String g = m.toMatchResult().group();
                if(!qss.containsKey(g)) {
                	String repl = "######" + UUID.randomUUID().toString() + "######";
                    qss.put(g, repl);
                    cmd = cmd.replace(g, repl);
                } else {
                	cmd = cmd.replace(g, qss.get(g));
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
    // Q1: concurrent - loaded and read by parallel sessions/servers (was a plain HashMap)
    protected static Map<String, String> plugins = new java.util.concurrent.ConcurrentHashMap<String, String>();
    protected CommandState state;
    protected String name;
    protected Object[] fileLineDetails;
    protected Map<String, SeleniumDriverConfig> mp;
    protected String className = "STC_" + System.nanoTime() + "";
    protected List<Command> children = new ArrayList<Command>();
    protected static boolean isSubtestScope = false;
    // M1: statement written as `nowait <action> ...` or `<action>! ...`: its actions skip the auto-wait
    protected boolean noWait = false;

    static Pattern p = Pattern.compile("\"([^\"]*)\"");
    static Pattern WAIT = Pattern.compile("^\\?\\?([0-9]*)");
    static Pattern WAIT_IF = Pattern.compile("^\\?\\?(\\+|\\-)([0-9]*)");
    // Δ145b: `else if <cond>` / `else ifnot <cond>`
    static final Pattern ELSE_IF = Pattern.compile("^else[ \\t]+(ifnot|if)[ \\t]+");
    static Pattern WAIT_SOFT = Pattern.compile("^\\?\\?~([0-9]*)");
    // M6: hook blocks and subtest markers
    static Pattern HOOK_BLOCK = Pattern.compile("(?i)^(beforeall|beforeeach|aftereach|afterall)$");
    static Pattern SUBTEST_MARKER = Pattern.compile("(?i)^(only|skip|fixme)\\s+subtest\\s+");
    /** E10: the transpile error for an {@code only} marker under forbidOnly. */
    static final String FORBID_ONLY = "'only' is not allowed (forbidOnly)";
    /** M6: canonical hook name (beforeAll, beforeEach, afterEach, afterAll) of a HOOK_BLOCK line, any case. */
    static String hookName(String line) {
        for (String h : new String[] {"beforeAll", "beforeEach", "afterEach", "afterAll"}) {
            if (h.equalsIgnoreCase(line)) return h;
        }
        return null;
    }
    static Pattern CONCNUM = Pattern.compile("^\\^[\t ]*([0-9]+)");
    // M1: auto-wait opt-out forms for element actions: `nowait click id@x`, `nowait ??10 id@x click`, `click! id@x`
    static final String NOWAIT_VERBS = "(?:click(?:fo|nf)?|(?:type|sendkeys|chord|randomize)(?:bl|ch|bk|cl|fo)?|select|hover|hoverclick"
            + "|clear|submit|upload|doubleclick|dblclick)";
    static Pattern NOWAIT_PREFIX = Pattern.compile("^nowait[\t ]+(?=(?:" + NOWAIT_VERBS + "|actions)(?:[\t ]|$)|\\?\\?)", Pattern.CASE_INSENSITIVE);
    static Pattern NOWAIT_BANG = Pattern.compile("^(" + NOWAIT_VERBS + ")!(?=[\t ]|$)", Pattern.CASE_INSENSITIVE);

    /** M1: strips an auto-wait opt-out marker; returns null when the statement has none. */
    static String stripNoWait(String cmd) {
        Matcher m = NOWAIT_PREFIX.matcher(cmd);
        if(m.find()) return cmd.substring(m.end());
        m = NOWAIT_BANG.matcher(cmd);
        if(m.find()) return m.group(1) + cmd.substring(m.end());
        return null;
    }

    Command(Object[] fileLineDetails, CommandState state) {
        this.fileLineDetails = fileLineDetails;
        this.state = state;
    }
    
    public static Object[] getSubtestFromCall(String line, String testcaseFileName, AcceptanceTestContext context, Command allcmds) {
    	List<String> commands = new ArrayList<String>();
    	try {
    		allcmds = allcmds!=null?allcmds:Command.read(context.getResourceFile(testcaseFileName), commands, context);
    		CommandState state = new CommandState();
    		state.allSubTests = allcmds.state.allSubTests;
    		Object[] cmdDetails = new Object[]{line, 1, null, "", state, ""};
			if(line.trim().startsWith("@call")) {
				String cmd = utf8Trim(line.substring(6).trim().trim());
		        cmd = state.sanitize(cmd);
				ExecSubTestCommand ecmd = new ExecSubTestCommand(cmd, cmdDetails, state);
				for (Command c : allcmds.children) {
					if(c instanceof SubTestCommand) {
						if(((SubTestCommand)c).name.equals(ecmd.name)) {
							return c.fileLineDetails;
						}
					}
				}
			} else {
				NlpExecSubTestCommand ecmd = new NlpExecSubTestCommand(line.trim(), cmdDetails, state);
				for (Command c : allcmds.children) {
					if(c instanceof SubTestCommand) {
						if(((SubTestCommand)c).name.equals(ecmd.name)) {
							return c.fileLineDetails;
						}
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
    	return new Object[] {};
    }
    
    public static List<String> getSubtestsForFile(String testcaseFileName, AcceptanceTestContext context, boolean withException) throws Exception {
    	List<String> subtests = new ArrayList<String>();
    	try {
    		List<String> commands = new ArrayList<String>();
    		Command allcmds = Command.read(context.getResourceFile(testcaseFileName), commands, context);
    		CommandState state = new CommandState();
    		state.allSubTests = allcmds.state.allSubTests;
    		for(Command cmd: allcmds.state.allSubTests) {
    			if(cmd instanceof SubTestCommand && ((SubTestCommand)cmd).hook==null) {
    				SubTestCommand scmd = (SubTestCommand)cmd;
    				subtests.add("@call \"" + scmd.name.replace('"', '\"') + "\"");
    			}
    		}
		} catch (Exception e) {
			if(withException) throw e;
			else e.printStackTrace();
		}
    	return subtests;
    }

    public String getClassName() {
        return className;
    }

    public static class GatfSelCodeParseError extends GatfRunTimeError {
    	Object[] details;
    	List<GatfSelCodeParseError> multiple = new ArrayList<GatfSelCodeParseError>();
        public GatfSelCodeParseError(String message, Object[] o) {
            super(message);
            details = new Object[] {o[0], o[1], o[5], ""};
        }
        public GatfSelCodeParseError(String message, Object[] o, Throwable e) {
            super(message, e);
            details = new Object[] {o[0], o[1], o[5], e.getMessage()};
        }
        public GatfSelCodeParseError(List<GatfSelCodeParseError> errs) {
            super("");
            multiple = errs;
        }
        public Object[] getDetails() {
        	return details;
        }
        public List<GatfSelCodeParseError> getAllErrors() {
        	return multiple;
        }
    }

    void throwParseError(Object[] o) {
        throwParseError(o, null);
    }

    void throwParseError(Object[] o, Throwable e) {
        if(e!=null) {
            if(o!=null) {
                throw new GatfSelCodeParseError("Error parsing command at line "+o[1]+" in file "+o[2]+" ("+o[0].toString().trim()+")", o, e);
            }
            throw new GatfSelCodeParseError("Error parsing command at line "+fileLineDetails[1]+" in file "+fileLineDetails[2]+" ("+fileLineDetails[0].toString().trim()+")", o, e);
        }
        if(o!=null) {
            throw new GatfSelCodeParseError("Error parsing command at line "+o[1]+" in file "+o[2]+" ("+o[0].toString().trim()+")", o);
        }
        throw new GatfSelCodeParseError("Error parsing command at line "+fileLineDetails[1]+" in file "+fileLineDetails[2]+" ("+fileLineDetails[0].toString().trim()+")", o);
    }

    static void throwParseErrorS(Object[] o, Throwable e) throws GatfSelCodeParseError {
        if(e!=null) {
            throw new GatfSelCodeParseError("Error parsing command at line "+o[1]+" in file "+o[2]+" ("+o[0].toString().trim()+")", o, e);
        }
        throw new GatfSelCodeParseError("Error parsing command at line "+o[1]+" in file "+o[2]+" ("+o[0].toString().trim()+")", o);
    }

    static void throwError(Object[] o, Throwable e) {
        if(e!=null) {
            throw new GatfSelCodeParseError("Error at line "+o[1]+" in file "+o[2]+" ("+o[0].toString().trim()+")", o, e);
        }
        throw new GatfSelCodeParseError("Error at line "+o[1]+" in file "+o[2]+" ("+o[0].toString().trim()+")", o);
    }

    int weight() {
        return 100;
    }

    static String utf8Trim(String str) {
    	if(str!=null && str.length()>0 && !StringUtils.isAsciiPrintable(str.substring(0, 1))) {
        	for (String utf8 : WHT_SPC_CHARS) {
        		if(str.startsWith(utf8)) {
        			str = str.substring(1);
        		}
			}
        	/*if(str.toCharArray()[0]==(char)-96) {
        		str = str.substring(1);
        	}*/
        	return utf8Trim(str);
        }
    	return str;
    }
    
    //Refer to the utf8 whitespace characters list -- https://unicode-explorer.com/articles/space-characters
    static final String[] WHT_SPC_CHARS = {"\u0000", "\u0020", "\u00A0", "\u2000", "\u2001", "\u2002", "\u2003", "\u2004", "\u2005", "\u2006", "\u2007", "\u2008", "\u2009", "\u200A", "\u202F", "\u205F", "\u3000", "\u200B", "\u2060", "\uFEFF", "\u180E", "\u2800", "\u3164", "\uFFA0"};
    // Phase 7: strip a trailing end-of-line // comment from a (sanitized) command line.
    // The '//' must be whitespace-preceded, so URL schemes (http://, https://, ws://,
    // file:///) are never mistaken for a comment; a full-line // (index 0) is left intact so
    // it still becomes a CommentCommand. Quoted text is already placeholder-masked by
    // sanitize(), so a // inside a string is invisible here. Returns the line up to the
    // comment marker, right-trimmed.
    static String stripTrailingLineComment(String cmd) {
        if(cmd==null || cmd.length()<3) return cmd;
        for(int i=1; i<cmd.length()-1; i++) {
            if(cmd.charAt(i)=='/' && cmd.charAt(i+1)=='/') {
                char prev = cmd.charAt(i-1);
                if(prev==' ' || prev=='\t') {
                    return cmd.substring(0, i).replaceAll("[ \\t]+$", "");
                }
            }
        }
        return cmd;
    }

    static Command parse(Object[] cmdDetails, CommandState state, Command parent) {
        String cmd = utf8Trim(cmdDetails[0].toString().trim());
        Command comd = null;
        cmd = state.sanitize(cmd);
        // Phase 7: drop a trailing end-of-line // comment before dispatch (values inside
        // quotes are already placeholder-masked by sanitize; :// URL schemes are preserved).
        // Δ145b: not on a '/*' line, so `/* a // b */` keeps its '*/' and stays a one-line comment
        if(!state.commentStart && !state.codeStart && !(parent instanceof ValueListCommand) && !cmd.startsWith("/*")) {
            cmd = stripTrailingLineComment(cmd);
        }
        if(!state.commentStart && cmd.trim().startsWith("^")) {
        	String skipKey = null;
        	for (String dkey : state.dynProps.keySet()) {
    			if(cmd.trim().startsWith("^"+dkey+" ") && state.dynProps.get(dkey).toLowerCase().matches("yes|true|1|enabled|on")) {
    				skipKey = dkey;
    				break;
    			}
    		}
        	if(skipKey!=null) {
        		cmd = cmd.replaceFirst("\\^"+skipKey+" ", "").trim();
        	} else {
        		return new NoopCommand(cmdDetails, state);
        	}
        }
        boolean noWait = false;
        if(!state.commentStart && !state.codeStart && !(parent instanceof ValueListCommand)) {
            String nw = stripNoWait(cmd);
            if(nw!=null) {
                cmd = nw;
                noWait = true;
            }
        }
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
        } else if(state.commentStart) {
            // Δ145b: '*/' ends a block comment only while one is open (sanitize() has masked quoted text, so a
            // quoted '*/' never counts); text after the first '*/' on the closing line is ignored, as before
            state.commentStart = false;
            comd = new EndMultiCommentCommand(cmd.substring(0, cmd.indexOf("*/")), cmdDetails, state);
        } else if(state.codeStart && (!cmd.equals(">>>") && !cmd.equals("end code"))) {
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
        } else if(cmd.startsWith("wait till visible ")) {
        	String time = state.timeoutNum+"";
            /*Matcher m = WAIT_IF.matcher(cmd);
            int start = 3;
            if(m.find()) {
                time = m.group(2);
                start = m.end(2) + 1;
            }*/
            cmd = cmd.substring(18).trim();
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
        } else if(cmd.startsWith("wait for ")) {
        	String time = state.timeoutNum+"";
            /*Matcher m = WAIT_IF.matcher(cmd);
            int start = 3;
            if(m.find()) {
                time = m.group(2);
                start = m.end(2) + 1;
            }*/
            cmd = cmd.substring(9).trim();
            comd = new WaitTillElementVisibleOrInvisibleCommand(time, cmd, cmdDetails, state, false, false);
        } else if(cmd.startsWith("??~")) {
        	// M2: soft assertion — record the failure and continue; the script fails at the end listing them all
        	String time = state.timeoutNum+"";
        	Matcher m = WAIT_SOFT.matcher(cmd);
        	int start = 3;
        	if(m.find() && !m.group(1).isEmpty()) {
        		time = m.group(1);
        		start = m.end(1);
        	}
        	cmd = cmd.substring(start).trim();
        	comd = new WaitTillElementVisibleOrInvisibleCommand(time, cmd, cmdDetails, state, false, false);
        	((WaitTillElementVisibleOrInvisibleCommand)comd).soft = true;
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
            if(!cmd.contains("*/")) {
                comd = new MultiCommentCommand(cmdDetails, state);
                ((MultiCommentCommand)comd).b.append(state.unsanitize(cmd)+"\n");
                state.commentStart = true;
            } else {
                // Δ145b: /* … */ on one line is a whole comment and opens no block (as sel-lint and SelDocs read it)
                comd = new CommentCommand(cmd.substring(0, cmd.indexOf("*/")), cmdDetails, state);
            }
        } else if (cmd.indexOf("*/")==0) {
            // Δ145b: a '*/' with no open block comment is an error, no longer a silent no-op; a '*/' elsewhere in a
            // line (route **/api/x, xpath@//div/*/a) is ordinary text and the statement runs
            throwParseErrorS(cmdDetails, new RuntimeException("'*/' without an open '/*' block comment"));
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
        } else if (cmd.startsWith("begin code in ")) {
            String lang = cmd.substring(14);
            String argnames = null;
            if(lang.indexOf(" ")!=-1) {
            	argnames = lang.substring(lang.indexOf(" ")+1);
            	lang = lang.substring(0, lang.indexOf(" "));
            }
            comd = new CodeCommand(lang, argnames, cmdDetails, state);
            state.codeStart = true;
        } else if (cmd.equals("end code")) {
            state.codeStart = false;
            comd = new EndCodeCommand(cmdDetails, state);
        } else if (cmd.startsWith("?") || cmd.startsWith("?!")) {
            boolean isIfNot = cmd.startsWith("?!");
            cmd = cmd.substring((isIfNot?2:1)).trim();
            comd = new IfCommand(cmd, isIfNot, cmdDetails, state);
            //((IfCommand)comd).cond = new FindCommand(cmd, cmdDetails, state);
            //((IfCommand)comd).cond.suppressErr = true;
        } else if (cmd.startsWith("if ") || cmd.startsWith("ifnot ")) {
            boolean isIfNot = cmd.startsWith("ifnot ");
            cmd = cmd.substring((isIfNot?6:3)).trim();
            comd = new IfCommand(cmd, isIfNot, cmdDetails, state);
            //((IfCommand)comd).cond = new FindCommand(cmd, cmdDetails, state);
            //((IfCommand)comd).cond.suppressErr = true;
        } else if (cmd.startsWith(":?") || cmd.startsWith(":?!")) {
            boolean isIfNot = cmd.startsWith(":?!");
            cmd = cmd.substring((isIfNot?3:2)).trim();
            comd = new ElseIfCommand(cmd, isIfNot, cmdDetails, state);
            //((ElseIfCommand)comd).cond = new FindCommand(cmd, cmdDetails, state);
            //((ElseIfCommand)comd).cond.suppressErr = true;
        } else if (cmd.startsWith("else if ") || cmd.startsWith("else ifnot ") || ELSE_IF.matcher(cmd).lookingAt()) {
            // Δ145b: the condition starts after the matched keyword (was startsWith("else ifnot  ") with two spaces,
            // so `else ifnot x` ran as `else if` on "ot x"); any run of spaces/tabs between the words is accepted.
            // The startsWith literals are subsumed by ELSE_IF and stay for tools/gen-commands.
            Matcher em = ELSE_IF.matcher(cmd);
            em.lookingAt();
            boolean isIfNot = "ifnot".equals(em.group(1));
            cmd = cmd.substring(em.end()).trim();
            comd = new ElseIfCommand(cmd, isIfNot, cmdDetails, state);
            //((ElseIfCommand)comd).cond = new FindCommand(cmd, cmdDetails, state);
            //((ElseIfCommand)comd).cond.suppressErr = true;
        } else if (cmd.startsWith(":")) {
            cmd = cmd.substring(1).trim();
            comd = new ElseCommand(cmdDetails, state);
        } else if (cmd.equals("else")) {
            cmd = cmd.substring(4).trim();
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
        } else if (cmd.startsWith("#optional ")) {
            cmd = cmd.substring(10).trim();
            if(cmd.isEmpty()) {
            	throwParseErrorS(cmdDetails, new RuntimeException("Optional condition/dynprops variable for code enablement required"));
            }
            comd = new ProviderLoopCommand(cmd.trim(), cmdDetails, state, false, ProviderType.Optional);
        } else if (cmd.startsWith("#o ")) {
            cmd = cmd.substring(3).trim();
            if(cmd.isEmpty()) {
            	throwParseErrorS(cmdDetails, new RuntimeException("Optional condition/dynprops variable for code enablement required"));
            }
            comd = new ProviderLoopCommand(cmd.trim(), cmdDetails, state, false, ProviderType.Optional);
        } else if (cmd.startsWith("#transient-provider ")) {
            cmd = cmd.substring(19).trim();
            if(cmd.isEmpty()) {
            	throwParseErrorS(cmdDetails, new RuntimeException("Provider details required"));
            }
            comd = new TransientProviderCommand(cmd.trim(), cmdDetails, state);
        } else if (cmd.startsWith("#transient-variable ")) {
            comd = new TransientVariableCommand(cmd.substring(20).trim(), cmdDetails, state, false);
        } else if (cmd.startsWith("#tv ")) {
            comd = new TransientVariableCommand(cmd.substring(4).trim(), cmdDetails, state, false);
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
        } else if (cmd.startsWith("loop over ")) {
            cmd = cmd.substring(10).trim();
            FindCommand cond = new FindCommand(cmd, cmdDetails, state);
            comd = new ScopedLoopCommand(cmdDetails, state, cond);
        } else if (cmd.startsWith("readfile ")) {
            comd = new ReadFileCommand(cmd.substring(9).trim(), cmdDetails, state);
        } else if (cmd.startsWith("filevar ")) {
            comd = new FileVarCommand(cmd.substring(8).trim(), cmdDetails, state);
        } else if (cmd.startsWith("dsq ")) {
            cmd = cmd.substring(4).trim();
            comd = new DSQueryCommand(cmd, cmdDetails, state);
        } else if (cmd.startsWith("ds query ")) {
            cmd = cmd.substring(9).trim();
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
            url = unSantizedUnQuoted(url, state);
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
        } else if (cmd.toLowerCase().startsWith("window ") || cmd.equalsIgnoreCase("window")) {
            comd = new WindowCommand(cmd.length()>6?cmd.substring(7).trim():"", cmdDetails, state);
        } else if (cmd.toLowerCase().startsWith("frame ")) {
            comd = new FrameCommand(cmd.substring(6).trim(), cmdDetails, state);
        } else if (cmd.toLowerCase().startsWith("printpdf ")) {
            comd = new PrintPDFCommand(cmd.substring(9).trim(), cmdDetails, state);   // re-enabled (transport-agnostic print)
        } else if (cmd.toLowerCase().startsWith("mail ")) {
            comd = new MailCommand(cmd.substring(5).trim(), cmdDetails, state);
        } else if (cmd.toLowerCase().startsWith("http")) {
            comd = new HttpCommand(cmd.substring(5).trim(), cmdDetails, state);
        } else if (cmd.toLowerCase().startsWith("wopensave ")) {
            comd = new WindowOpenSaveInterceptJsCommand(cmd.substring(10).trim(), cmdDetails, state);
        } else if (cmd.toLowerCase().startsWith("netapix ")) {
            comd = new NetworkAPIInterceptCommand(cmd.substring(8).trim(), cmdDetails, state);
            state.hasNetapixCommand = true;
        } else if (cmd.toLowerCase().startsWith("a11y ")) {
            comd = new A11yCommand(state.unsanitize(cmd.substring(5).trim()), cmdDetails, state);   // M10 accessibility
        } else if (cmd.toLowerCase().startsWith("clock ")) {
            comd = new ClockCommand(state.unsanitize(cmd.substring(6).trim()), cmdDetails, state);   // M11 clock control
            state.hasEmulateCommand = true;   // the per-document clock needs CDP or the BiDi socket
        } else if (cmd.toLowerCase().startsWith("emulate ")) {
            comd = new EmulateCommand(state.unsanitize(cmd.substring(8).trim()), cmdDetails, state);   // M8 emulation
            state.hasEmulateCommand = true;
        } else if (cmd.toLowerCase().startsWith("state ")) {
            comd = new StateCommand(cmd.substring(6).trim(), cmdDetails, state);   // M5 auth state
        } else if (cmd.toLowerCase().startsWith("route ") || cmd.toLowerCase().equals("unroute") || cmd.toLowerCase().startsWith("unroute ")) {
            comd = new RouteCommand(state.unsanitize(cmd), cmdDetails, state);   // M4 network mocking
            state.hasRouteCommand = true;
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
        	// Δ145c: hoverclick is never force-wrapped: HoverAndClickCommand waits for both of its elements itself
        	if(state.modeEnableGlobalTimeouts && !cmd.toLowerCase().startsWith("hoverclick ")) {
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
        } else if (HOOK_BLOCK.matcher(cmd.trim()).matches()) {
            // M6: beforeAll / beforeEach / afterEach / afterAll block, generated like a func and called around subtests
            String hk = hookName(cmd.trim());
            comd = new SubTestCommand("\"" + hk + "\"", cmdDetails, state, true, hk, null);
        } else if (SUBTEST_MARKER.matcher(cmd).find()) {
            // M6: only | skip | fixme subtest ...
            Matcher sm = SUBTEST_MARKER.matcher(cmd);
            sm.find();
            comd = new SubTestCommand(cmd.substring(sm.end()).trim(), cmdDetails, state, false, null, sm.group(1).toLowerCase());
        } else if (cmd.toLowerCase().startsWith("subtest ")) {
            comd = new SubTestCommand(cmd.substring(8).trim(), cmdDetails, state, false);
        } else if (cmd.toLowerCase().startsWith("func ")) {
            comd = new SubTestCommand(cmd.substring(5).trim(), cmdDetails, state, true);
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
        } else if (cmd.toLowerCase().startsWith("screenshot compare ") || cmd.toLowerCase().matches("screenshot\\s+compare(\\s.*)?")) {
            // M9 visual regression (the bare / tab-separated form reaches the command's own "needs a name" error, not `screenshot`)
            comd = new ScreenshotCompareCommand(cmd.replaceFirst("(?i)^screenshot\\s+compare", "").trim(), cmdDetails, state);
        } else if (cmd.toLowerCase().startsWith("screenshot")) {
            comd = new ScreenshotCommand(cmd.substring(10).trim(), cmdDetails, state, false);
        } else if (cmd.toLowerCase().startsWith("ele-screenshot ")) {
            comd = new EleScreenshotCommand(cmd.substring(15).trim(), cmdDetails, state, false);
        } else if (cmd.toLowerCase().startsWith("imgsave ")) {
            comd = new SaveImageCommand(cmd.substring(8).trim(), cmdDetails, state, false);
        } else if (cmd.toLowerCase().startsWith("readimgtext ")) {
            comd = new ReadImageTextCommand(cmd.substring(12).trim(), cmdDetails, state);
        } else if (cmd.toLowerCase().startsWith("ocr ")) {
            comd = new ReadImageTextCommand(cmd.substring(4).trim(), cmdDetails, state);
        } else if (cmd.toLowerCase().startsWith("readqrbar ")) {
            comd = new ReadQRCodeBarCodeCommand(cmd.substring(10).trim(), cmdDetails, state);
        } else if (cmd.toLowerCase().startsWith("qrcode ")) {
            comd = new ReadQRCodeBarCodeCommand(cmd.substring(7).trim(), cmdDetails, state);
        } else if (cmd.toLowerCase().startsWith("barcode ")) {
            comd = new ReadQRCodeBarCodeCommand(cmd.substring(8).trim(), cmdDetails, state);
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
            	if(e.getCause()!=null && String.valueOf(e.getCause().getMessage()).startsWith("curl: ")) {
            		throw e;   // Q2: a raw cURL line that failed to convert — keep the specific reason
            	}
            	try {
            		 comd = new NlpExecSubTestCommand(cmd, cmdDetails, state);
				} catch (Exception e2) {
	            	if(parent instanceof PluginCommand) {
		                comd = new ValueCommand(cmdDetails, state);
		                ((ValueCommand)comd).value = unSantizedUnQuoted(cmd, state);
	            	} else if(parent instanceof VarCommand && ((VarCommand)parent).pcomd!=null) {
		                comd = new ValueCommand(cmdDetails, state);
		                ((ValueCommand)comd).value = unSantizedUnQuoted(cmd, state);
	            	} else {
	            		if(e2 instanceof GatfSelCodeParseError && !((GatfSelCodeParseError)e2).getMessage().startsWith("No func/subtest with that name found - ")) {
	            			throw e2;
	            		}
	            		throwError(cmdDetails, new RuntimeException("Invalid command found ["+state.unsanitize(cmd)+"]"));
	            	}
				}
            }
        } /* else if (cmd.startsWith("^")) {
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
	    }*/
        if(noWait && comd!=null) {
            comd.noWait = true;
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
        if (cmd.toLowerCase().matches(typeSwStr)) {
        	Matcher m = typeSw.matcher(cmd.toLowerCase());
        	m.matches();
        	String type = m.group(1);
        	String qualifier = m.group(2);
        	cmd = m.group(3);
            comd = new TypeCommand(cmd, cmdDetails, state, fcmd, type, qualifier);
        } else if (cmd.toLowerCase().startsWith("upload ")) {
            comd = new UploadCommand(cmd.substring(7), cmdDetails, state);
        } else if (cmd.toLowerCase().startsWith("select ")) {
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
    
    static ImmutablePair<Command, Object[]> nextCommand(Command parent, ListIterator<Object[]> iter, CommandState state) throws GatfSelCodeParseError {
    	Command tmp = null;
        Object[] o = iter.next();
        
    	for (String dkey : state.dynProps.keySet()) {
			o[0] = o[0].toString().replace("!"+dkey+"!", state.dynProps.get(dkey));
		}

        try
        {
            tmp = parse(o, state, parent);
            /*if(o[0].toString().trim().startsWith("func \"pre1\"")) {
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

    static void get(Command parent, ListIterator<Object[]> iter, CommandState state, List<GatfSelCodeParseError> allSynErrs) throws Exception {
        while(iter.hasNext()) {
        	ImmutablePair<Command, Object[]> ip = null;
        	try {
				ip = nextCommand(parent, iter, state);
			} catch (GatfSelCodeParseError e) {
				allSynErrs.add(e);
				continue;
			}
        	
            Command tmp = ip.left;
            Object[] o = ip.right;
            
            try {
                if(parent instanceof SubTestCommand && tmp instanceof SubTestCommand) {
                	throwParseErrorS(o, new RuntimeException("Nested subtests/functions not allowed"));
                }
                if(tmp instanceof SubTestCommand) {
                	if(!parent.isTop) {
                    	throwParseErrorS(o, new RuntimeException("subtests/functions are allowed only at the top level"));
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
                	if(!iter.hasNext()) {   // last statement of the file: no block can follow it
                		if(tmp instanceof VarCommand || tmp instanceof FrameCommand || tmp instanceof PluginCommand || tmp instanceof TabCommand
                				|| tmp instanceof WindowCommand || tmp instanceof TransientProviderCommand) {
                			parent.children.add(tmp);
                			if(tmp instanceof PluginCommand) {
                				((PluginCommand)tmp).reconcile();
                			} else if(tmp instanceof VarCommand) {
                				((VarCommand)tmp).reconcile();
                			}
                			continue;
                		}
                		throwParseErrorS(o, new RuntimeException(tmp.getClass().getSimpleName() + " should have a proceeding block"));
                	}
                	ImmutablePair<Command, Object[]> ipn = nextCommand(tmp, iter, state);
                    Command tmpn = ipn.left;
                    Object[] on = ipn.right;
                	if(!(tmpn instanceof StartCommand) && !(tmp instanceof VarCommand) && !(tmp instanceof FrameCommand) && !(tmp instanceof PluginCommand) 
                			&& !(tmp instanceof TabCommand) && !(tmp instanceof WindowCommand) && !(tmp instanceof TransientProviderCommand)) {
                		boolean err = true;
                    	if(tmp instanceof IfCommand || tmp instanceof ElseCommand) {
                    		if(tmpn instanceof ValueListCommand) {
                    			get(tmpn, iter, state, allSynErrs);
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
                    		throwError(on, new RuntimeException(tmp.getClass().getSimpleName() + " should have a proceeding block"));
                    	}
                    	
                    	parent.children.add(tmp);
                    	if(tmpn instanceof StartCommand) {
        	        		get(tmp, iter, state, allSynErrs);
        	        		tmp.children.remove(tmp.children.size()-1);
        	        		state.ends--;
                    	} else {
                    		iter.previous();
        	        		get(parent, iter, state, allSynErrs);
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
        	        		get(tmp, iter, state, allSynErrs);
        	        		tmp.children.remove(tmp.children.size()-1);
        	        		state.ends--;
                    	} else {
                    		iter.previous();
        	        		get(parent, iter, state, allSynErrs);
                    	}
            		}
                } else if(tmp instanceof ValueListCommand) {
                	parent.children.add(tmp);
                    get(tmp, iter, state, allSynErrs);
                    if(tmp.children.size()==0 || !(tmp.children.get(tmp.children.size()-1) instanceof EndValueListCommand)) {
                    	throwParseErrorS(o, new RuntimeException("ValueListCommand command should close with a EndCommand `]`"));
                    }
                } else if(tmp instanceof StartCommand) {
                	state.starts++;
                	int stindx = parent.children.size();
                    parent.children.add(tmp);
                    get(parent, iter, state, allSynErrs);
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
                    get(tmp, iter, state, allSynErrs);
                    if(tmp.children.size()==0 || !(tmp.children.get(tmp.children.size()-1) instanceof EndMultiCommentCommand)) {
                    	throwParseErrorS(o, new RuntimeException("MultiCommentCommand command should close with a EndMultiCommentCommand `*/`"));
                    }
                }  else if(tmp instanceof CodeCommand) {
                    parent.children.add(tmp);
                    get(tmp, iter, state, allSynErrs);
                    if(tmp.children.size()==0 || !(tmp.children.get(tmp.children.size()-1) instanceof EndCodeCommand)) {
                    	throwParseErrorS(o, new RuntimeException("CodeCommand command should close with a EndCodeCommand `>>>|end code`"));
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
                        	if(state.testcaseDir.trim().equals(".")) {
                        		f = new File(state.origBasePath + File.separator + ((ImportCommand)tmp).name);
                        	} else {
                        		f = new File(state.origBasePath + File.separator + state.testcaseDir.trim() + File.separator + ((ImportCommand)tmp).name);
                        	}
                        }
                    }
                    if(!f.exists()) {
                        f = new File(new File(parentPath).getParent() + File.separator + ((ImportCommand)tmp).name);
                        parentPath = new File(parentPath).getParent();
                        if(!f.exists() && new File(parentPath).exists()) {
                            f = new File(new File(parentPath).getParent() + File.separator + ((ImportCommand)tmp).name);
                        }
                    }
                    if(!f.exists()) {
                    	f = new File(state.origBasePath + File.separator + ((ImportCommand)tmp).name);
                    }
                    if(!f.exists()) {
                        throwParseErrorS(o, new RuntimeException("Include script not found in any of the search paths"));
                    }
                    if(state.visitedFiles.contains(f.getAbsolutePath())) {
                        throwParseErrorS(o, new RuntimeException("Possible include script recursion observed"));
                    }
                    String currBasePath = state.basePath;
                    state.basePath = f.getParent();
                    state.isImportedFile = true;
                    Command imported = Command.read(f.getAbsolutePath(), state);
                    for (Command cmd : imported.children) {
    					if(cmd instanceof SubTestCommand && ((SubTestCommand)cmd).hook==null) {
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
                    state.isImportedFile = false;
                } else if(tmp instanceof IncludeCommand) {
                    String parentPath = state.basePath;
                    if(StringUtils.isNotBlank(o[3].toString())) {
                        parentPath = o[3].toString();
                    }
                    File f = new File(parentPath + File.separator + ((IncludeCommand)tmp).name);
                    if(!f.exists()) {
                        f = new File(state.basePath + File.separator + ((IncludeCommand)tmp).name);
                        if(!f.exists() && StringUtils.isNotBlank(state.testcaseDir)) {
                        	if(state.testcaseDir.trim().equals(".")) {
                        		f = new File(state.origBasePath + File.separator + ((IncludeCommand)tmp).name);
                        	} else {
                        		f = new File(state.origBasePath + File.separator + state.testcaseDir.trim() + File.separator + ((IncludeCommand)tmp).name);
                        	}
                        }
                    }
                    if(!f.exists()) {
                    	f = new File(state.origBasePath + File.separator + ((IncludeCommand)tmp).name);
                    }
                    if(!f.exists()) {
                        throwParseErrorS(o, new RuntimeException("Include script not found in any of the search paths"));
                    }
                    if(state.visitedFiles.contains(f.getAbsolutePath())) {
                        throwParseErrorS(o, new RuntimeException("Possible include script recursion observed"));
                    }
                    state.visitedFiles.add(f.getAbsolutePath());
                    List<String> commands = FileUtils.readLines(f, "UTF-8");
                    int cnt = 1;
                    String fnm = ((IncludeCommand)tmp).name;
                    if(fnm.lastIndexOf("\\")!=-1) {
                        fnm = fnm.substring(0, fnm.lastIndexOf("\\")+1);
                    } else if(fnm.lastIndexOf("/")!=-1) {
                        fnm = fnm.substring(0, fnm.lastIndexOf("/")+1);
                    }
                    
                    String relfn = f.getName();
                    if(StringUtils.isNotBlank(state.origBasePath)) {
                    	String path = state.origBasePath.trim();
                    	if(StringUtils.isNotBlank(state.testcaseDir) && !state.testcaseDir.trim().equals(".")) {
                    		path += File.separator + state.testcaseDir.trim();
                    	}
                    	relfn = f.getAbsolutePath().replace(path, "");
                    	if(relfn.startsWith(File.separator)) {
                    		relfn = relfn.substring(1);
                    	}
                    }
                    
                    for (String c : commands) {
                        iter.add(new Object[]{c, cnt++, f, f.getParentFile().getAbsolutePath(), state, relfn});
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
                        	if(state.testcaseDir.trim().equals(".")) {
                        		f = new File(state.origBasePath + File.separator + ((ConfigPropsCommand)tmp).name);
                        	} else {
                        		f = new File(state.origBasePath + File.separator + state.testcaseDir.trim() + File.separator + ((ConfigPropsCommand)tmp).name);
                        	}
                        }
                    }
                    if(!f.exists()) {
                    	f = new File(state.origBasePath + File.separator + ((ConfigPropsCommand)tmp).name);
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
                        	if(state.testcaseDir.trim().equals(".")) {
                        		f = new File(state.origBasePath + File.separator + ((DynPropsCommand)tmp).name);
                        	} else {
                        		f = new File(state.origBasePath + File.separator + state.testcaseDir.trim() + File.separator + ((DynPropsCommand)tmp).name);
                        	}
                        }
                    }
                    if(!f.exists()) {
                        f = new File(new File(parentPath).getParent() + File.separator + ((DynPropsCommand)tmp).name);
                        parentPath = new File(parentPath).getParent();
                        if(!f.exists() && new File(parentPath).exists()) {
                            f = new File(new File(parentPath).getParent() + File.separator + ((DynPropsCommand)tmp).name);
                        }
                    }
                    if(!f.exists()) {
                    	f = new File(state.origBasePath + File.separator + ((DynPropsCommand)tmp).name);
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
			} catch (GatfSelCodeParseError e) {
                if(e.multiple.size()>0) {
                    allSynErrs.addAll(e.multiple);
                } else {
				    allSynErrs.add(e);
                }
			}
        }
    }

    public static Command findCommandByLineNum(Command parent, String testFile, int lineNum) {
        if(parent.fileLineDetails!=null && ((File)parent.fileLineDetails[2]).getAbsolutePath().equals(testFile) && parent.fileLineDetails[1].equals(lineNum)) return parent;
        for (Command c : parent.children) {
            if(((File)c.fileLineDetails[2]).getAbsolutePath().equals(testFile) && c.fileLineDetails[1].equals(lineNum)) {
                return c;
            }
            if(c.children!=null) {
                Command ct = findCommandByLineNum(c, testFile, lineNum);
                if(ct!=null) {
                    return ct;
                }
            }
        }
        return null;
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
        if(StringUtils.isNotBlank(state.origBasePath)) {
        	String path = state.origBasePath.trim();
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
        List<GatfSelCodeParseError> allSynErrs = new ArrayList<>();
        get(tcmd, lio.listIterator(), state, allSynErrs);
        try {
        	mergeIfElses(tcmd);
		} catch (GatfSelCodeParseError e) {
			allSynErrs.add(e);
		}
        if(state.starts!=state.ends) {
        	throwParseErrorS(lio.get(0), new RuntimeException("Blocks not balanced..."));
        } else if(allSynErrs.size()>0) {
        	//allSynErrs.get(0).getCause().printStackTrace();
        	throw new GatfSelCodeParseError(allSynErrs);
        }

        if(allSynErrs.size()>0) {
            Collections.sort(tcmd.children, new Comparator<Command>() {
                public int compare(Command o1, Command o2) {
                    return Integer.valueOf(o1.weight()).compareTo(o2.weight());
                }
            });
        }

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
        state.origBasePath = context.getGatfExecutorConfig().getTestCasesBasePath();
        state.basePath = state.origBasePath;
		if(context.getGatfExecutorConfig().getGatfTestDataConfig()!=null && context.getGatfExecutorConfig().getGatfTestDataConfig().getGlobalVariables()!=null) {
			state.dynProps.putAll(context.getGatfExecutorConfig().getGatfTestDataConfig().getGlobalVariables());
		}
        if(context.getGatfExecutorConfig().getTestCaseDir()!=null) {
            state.testcaseDir = context.getGatfExecutorConfig().getTestCaseDir();
        }
        state.autoWait = context.getGatfExecutorConfig().autoWaitEnabled();
        String lo = context.getGatfExecutorConfig().getLoginOnce();
        state.isLoginOnceScript = lo != null && !lo.trim().isEmpty() && com.gatf.executor.core.SetupScripts.sameFile(file.getPath(), lo.trim(),
                n -> n.equals(file.getPath()) ? file : context.getResourceFile(n));
        loadPlugins(state, context);
        state.visitedFiles.add(file.getAbsolutePath());
        Command cmd = Command.getAll(commands, file, state);
        SubTestCommand only = state.firstOnly();
        if(only!=null && Boolean.TRUE.equals(context.getGatfExecutorConfig().getForbidOnly())) {
            throwError(only.fileLineDetails, new RuntimeException(FORBID_ONLY));
        }
        cmd.mp = context.getGatfExecutorConfig().getSelDriverConfigMap();
        return cmd;
    }

    static void loadPlugins(CommandState state, AcceptanceTestContext cntxt) throws Exception {
    	// M13: plugins/*.jar (SDK v2 via ServiceLoader, and plugins.txt classes shipped in jars) - re-scanned on every load
    	com.gatf.selenium.plugins.sdk.PluginRegistry.loadAndInit(cntxt.getResourceFile("plugins"), Command.class.getClassLoader(), cntxt);
    	for (String w : com.gatf.selenium.plugins.sdk.PluginRegistry.warnings()) {
    		org.apache.logging.log4j.LogManager.getLogger(Command.class).warn("plugins/: {}", w);
    	}
    	List<String> lines = new ArrayList<>(PluginCatalog.BUILTIN);
    	//lines.add("sendmail=com.gatf.selenium.plugins.MailPlugin@execute");
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

                    String err = pluginSignatureError(clsname, method);
                    if(err != null) {
                        org.apache.logging.log4j.LogManager.getLogger(Command.class).warn("Invalid plugin '{}' ({}): {}", name.trim(), plg, err);
                        continue;
                    }
                    plugins.put(name.toLowerCase().trim(), clsname+"@"+method);
                } else {
                    if(name.trim().isEmpty()) {
                        org.apache.logging.log4j.LogManager.getLogger(Command.class).warn("Invalid plugin entry (empty name): {}", plg);
                    } else {
                        org.apache.logging.log4j.LogManager.getLogger(Command.class).debug("Plugin already defined, keeping the first definition: {}", name);
                    }
                }
            }
        }
        // M13: SDK v2 plugins after the built-ins and plugins.txt (the first definition of a name wins); a jar removed
        // since the last load drops its names, so the script fails to compile instead of at run time
        Map<String, com.gatf.selenium.plugins.sdk.GatfStepPlugin> v2s = com.gatf.selenium.plugins.sdk.PluginRegistry.all();
        plugins.entrySet().removeIf(e -> e.getValue().startsWith(V2_PLUGIN) && !v2s.containsKey(e.getKey()));
        for (Map.Entry<String, com.gatf.selenium.plugins.sdk.GatfStepPlugin> e : v2s.entrySet()) {
            String prev = plugins.get(e.getKey());
            if (prev == null || prev.startsWith(V2_PLUGIN)) {
                plugins.put(e.getKey(), V2_PLUGIN + e.getKey());
            } else {
                org.apache.logging.log4j.LogManager.getLogger(Command.class).warn("plugins/: '{}' is already defined ({}); keeping the first definition", e.getKey(), prev);
            }
        }
        for (String name : plugins.keySet()) {
        	String signature = plugins.get(name);
        	if (signature.startsWith(V2_PLUGIN)) continue;
	        try {
	            String[] parts = signature.split("@");
	            String clsname = parts[0].trim();

	            Class<?> cls = Class.forName(clsname, true, com.gatf.selenium.plugins.sdk.PluginRegistry.classLoader());
	            Method meth = cls.getMethod("init", new Class[]{AcceptanceTestContext.class});
	            
	            meth.invoke(null, new Object[] {cntxt});
	        } catch (NoSuchMethodException e) {
	        } catch (Exception e) {
	        	org.apache.logging.log4j.LogManager.getLogger(Command.class).warn("Plugin '{}' init(AcceptanceTestContext) failed", name, e);
	        }
		}
    }

    /**
     * Q1: validates a plugin entry. Returns null when {@code clsname} has a {@code public static} method named
     * {@code method} taking {@code (Object[])} or {@code (String, Object[])} and returning a value; otherwise returns
     * the reason. The old check compared against {@code Void.class}, which never matches the primitive
     * {@code void.class}, so void methods were accepted. It also didn't check {@code static}, so an instance method
     * failed later with an NPE at {@code invoke(null, ...)}.
     */
    /** M13: the {@code plugins} signature of an SDK v2 plugin ({@code v2:<name>}); resolved via PluginRegistry at run time. */
    static final String V2_PLUGIN = "v2:";

    static String pluginSignatureError(String clsname, String method) {
        Class<?> cls;
        try {
            cls = Class.forName(clsname, true, com.gatf.selenium.plugins.sdk.PluginRegistry.classLoader());   // M13: plugins/*.jar too
        } catch (Throwable e) {
            return "class not found: " + clsname;
        }
        Method meth = null;
        for (Class<?>[] sig : new Class<?>[][] { {Object[].class}, {String.class, Object[].class} }) {
            try {
                meth = cls.getMethod(method, sig);
                break;
            } catch (NoSuchMethodException e) {
                // try the next signature
            }
        }
        if(meth == null) {
            return "no public method " + method + "(Object[]) or " + method + "(String, Object[]) in " + clsname;
        }
        if(!java.lang.reflect.Modifier.isStatic(meth.getModifiers())) {
            return "method " + method + " must be static";
        }
        if(meth.getReturnType() == void.class || meth.getReturnType() == Void.class) {
            return "method " + method + " must return a value (Object), not void";
        }
        return null;
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

    void commonImports(StringBuilder b) {
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
        b.append("import com.gatf.selenium.SeleniumTest.Util;\n");
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
        b.append("import com.gatf.GatfAssert;\nimport com.gatf.GatfAssert.Assert;\n");   // M16: no junit at runtime; Assert = alias for inline #j code
        b.append("import org.openqa.selenium.Keys;\n");
        b.append("import ru.yandex.qatools.ashot.AShot;\n");
        b.append("import ru.yandex.qatools.ashot.shooting.ShootingStrategies;\n\n@SuppressWarnings({\"unused\", \"unchecked\", \"serial\"})\n");
    }
    
    String javacode() {
        StringBuilder b = new StringBuilder();
        commonImports(b);
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
        List<Object[]> bn = new ArrayList<Object[]>();
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
                    bn.add(new Object[]{c.name.toLowerCase(), ((BrowserCommand)c).sessionName, ((BrowserCommand)c).sessionId+"", c});
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
                    bn.add(new Object[]{c.name.toLowerCase(), ((BrowserCommand)c).sessionName, ((BrowserCommand)c).sessionId+"", c});
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
	        	for (Object[] brn : bn)
	            {
	                String sessionName = (String)brn[1];
	                String sessionId =  (String)brn[2];
	                
	                flag |= (sessionName!=null && sessionName.equalsIgnoreCase(stsessionName)) || sessionId.equalsIgnoreCase(stsessionId);
	            }
	        	if(!flag) {
	        		throwError((Object[])st[3], new RuntimeException("Session Details not found for \"" + (stsessionName!=null?stsessionName:stsessionId+1) + "\""));
	        	} else {
	        		
	        	}
            }
        }
        b.append("public List<SeleniumTestSession> execute(LoggingPreferences ___lp___, String outPath) throws Exception {\nthis.setOutPath(outPath);\n");
        b.append("/*GATF_ST_START_*/");
        for (Object[] brn : bn)
        {
        	String bsessionName =  (String)brn[1];
            b.append("addTest("+(StringUtils.isNotBlank(bsessionName)?("\""+esc(bsessionName)+"\""):"null")+", \""+esc(brn[0].toString())+"\");\n");
        }
        for (Object[] st : state.subtestDetails)
        {
        	if(!(Boolean)st[6]) {
        		b.append("addSubTest(\""+esc((String)st[0])+"\");\n\n");
        	}
        }
        b.append("\n\n");
        
        if(state.modeExecType==2) {
            if(bn.size()>1 && state.subtestDetails.size()==0) {
                throw new RuntimeException("In integration mode subtests are required per browser session");
            }
            for (int y=bn.size()-1;y>=0;y--)
            {
                Object[] brn = bn.get(y);
                b.append("quit();\n");
                b.append(genDebugInfo(((Command)brn[3])));
                b.append("setupDriver"+brn[0].toString().replaceAll("[^0-9A-Za-z]+", "")+"(___lp___);\n");
            }
            b.append("_execute(___lp___);\n");
        } else {
            for (Object[] brn : bn)
            {
                b.append("setSession(null, " + (brn[2]!=null?brn[2]:"-1")+", true);\n");
                //b.append("startTest();\n");
                b.append("quit();\n");
                b.append(genDebugInfo(((Command)brn[3])));
                b.append("setupDriver"+brn[0].toString().replaceAll("[^0-9A-Za-z]+", "")+"(___lp___);\n");
                b.append("_execute(___lp___);\n");
            }
        }
        
        b.append("return get__sessions__();\n}\n");
        b.append("public int concurrentExecutionNum() {\n");
        b.append("return " + state.concExec+";\n");
        b.append("}\n");
        b.append("public void _execute(LoggingPreferences ___lp___) throws Exception {\n");
        b.append("WebDriver ___ocw___ = null;\n");
        // M6: beforeAll runs before the first top-level statement, afterAll after the last (and on failure)
        SubTestCommand bah = state.hook("beforeAll"), aah = state.hook("afterAll");
        if(aah!=null) {
        	b.append("boolean ___aa___ = false;\n");
        }
        b.append("try {\n<<<<---->>>>");
        state.pushSc();
        b.append("SearchContext "+state.currvarnamesc()+" = get___d___();\n");
        b.append("WebDriver ___cw___ = get___d___();\n");
        b.append("___ocw___ = ___cw___;\n");
        b.append("List<WebElement> ___ce___ = null;\n");
        if(bah!=null) {
        	b.append(bah.hookCall(state.currvarnamesc()));
        }
        int subtestcount = 0;
        state.isSTGenPhase = false;
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
        if(aah!=null) {
        	b.append("___aa___ = true;\n");
        	b.append(aah.hookCall(state.currvarnamesc()));
        }
        b.append("pushResult(new SeleniumTestResult(get___d___(), this, ___lp___));\n");
        String ex = state.evarname();
        b.append("}\ncatch(Throwable "+ex+")\n{");
        String aares = "new SeleniumTestResult(get___d___(), this, "+ex+", getOutDir() + java.io.File.separator + \""+UUID.randomUUID().toString()+".png\", ___lp___, null)";
        if(aah!=null) {
        	// the script's failure result (line, screenshot) is taken before afterAll changes the page
        	b.append("SeleniumTestResult ___aar___ = " + aares + ";\n");
        	aares = "___aar___";
        	b.append("if(!___aa___) {\n___aa___ = true;\ntry {\n___invoke_sub_test_dyn___(getClass(), \"" + aah.fName + "\", \"afterAll\", get___d___(), get___d___(), get___d___(), ___lp___);\n"
        			+ "} catch(Throwable ___hk___) {\n__hook_failed__(\"afterAll\", ___hk___);\n}\n__hook_soft__(\"afterAll\");\n}\n");
        }
        state.isSTGenPhase = true;
        //b.append("\ntry{");
        //b.append(ex+".printStackTrace();\n");
        //b.append("java.lang.System.out.println(\"_main_exec.png\");");
        //ScreenshotCommand tm = new ScreenshotCommand("_main_exec.png", new Object[] {}, state, true);
        //b.append(tm.javacode());
        //b.append("}catch(java.io.IOException _ioe){}");
        //b.append("if("+ex+" instanceof XPathException) {}\n");
        //b.append("else if("+ex+" instanceof FailSubTestException) "+ex+" = new RuntimeException("+ex+");\n");
    	//b.append("else if("+ex+" instanceof SubTestException) "+ex+" = new RuntimeException("+ex+");\n");
        b.append("pushResult(" + aares + ");\n}");
        b.append("}\n");
        for (Command c : state.allSubTests) {
            if(c instanceof SubTestCommand) {
                SubTestCommand st = (SubTestCommand)c;
                b.append(st.javacode());
            }
        }
        b.append("}\n");
        String bs = b.toString();
        bs = bs.replace("<<<<---->>>>", state.invXpathErrs);
        return bs;
    }
    
    public static String genDebugInfo(Command c) {
    	String dbg = genDebugInfo_(c);
    	return c.noWait && !dbg.isEmpty() ? dbg + "__nowait__();" : dbg;
    }

    private static String genDebugInfo_(Command c) {
    	if(c.fileLineDetails!=null && c.fileLineDetails.length>0) {
    		if(c instanceof CommentCommand || c instanceof MultiCommentCommand || c instanceof NoopCommand) return "";
    		if(isSubtestScope || c instanceof SubTestCommand) {
    			return "/*GATF_ST_LINE@" + esc(c.fileLineDetails[2].toString().trim())+":"+c.fileLineDetails[1] + "*/__set__cln__(\""+(esc(c.fileLineDetails[2].toString().trim())+":"+c.fileLineDetails[1])+"\");";
    		} else if(c instanceof BrowserCommand) {
    			return "/*GATF_OP_LINE@" + esc(c.fileLineDetails[2].toString().trim())+":"+c.fileLineDetails[1] + "*/__set__cln__(\""+(esc(c.fileLineDetails[2].toString().trim())+":"+c.fileLineDetails[1])+"\");";
    		}
    		return "/*GATF_LINE@" + esc(c.fileLineDetails[2].toString().trim())+":"+c.fileLineDetails[1] + "*/__set__cln__(\""+(esc(c.fileLineDetails[2].toString().trim())+":"+c.fileLineDetails[1])+"\");";
    	}
    	return "";
    }

    // G8: ExecCommand promoted to its own file (ExecCommand.java)

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
            // Δ145b: a quoted '*/' in the comment text must not end the generated Java comment early
            return "/*" + b.substring(2).replace("*/", "* /") + "*/";
        }
        MultiCommentCommand(Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            b.append("/*");
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
				"Block level comment",
				"\t/*\n\t Some multi line comment\n\t Some multi line comment 2\n\t*/"
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

    // G8: CodeCommand promoted to its own file (CodeCommand.java)

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
				"Java control command, block statements",
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
            code = unSantizedUnQuoted(code, state);
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
            code = unSantizedUnQuoted(code, state);
            this.code = code;
        }
        String toCmd() {
            return "execjsfile \"" + code + "\"";
        }
        String javacode() {
            return "if (___ocw___ instanceof JavascriptExecutor) {\n((JavascriptExecutor)___ocw___).executeScript(readFileToStr(\""+esc(code)+"\"));\n}";
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
				"Draw a circle on a canvas element",
				"\tcanvas {find-expr}",
				"Examples :-",
	    		"\tcanvas xpath@\"asds\"",
            };
        }
    }
    
    public static class NlpExecSubTestCommand extends ExecSubTestCommand {
		NlpExecSubTestCommand(String val, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            String[] parts = val.trim().split("[\t ]+");
            if(parts.length==0) {
            	throwError(fileLineDetails, new RuntimeException("Please specify a subtest name to execute"));
            }
            
            int iters = parts.length;
            if(parts.length>0) {
	            String trialname = unSantizedUnQuoted(val, state);
	            outer: while(true) {
	            	for (Command c : state.allSubTests) {
	                	SubTestCommand st = (SubTestCommand)c;
	    				if(st.hook==null && st.name.equalsIgnoreCase(trialname)) {
	    					subt = st;
	    					break outer;
	    				}
	            	}
	            	if(trialname.lastIndexOf(" ")==-1) break;
	            	iters--;
	            	trialname = trialname.substring(0, trialname.lastIndexOf(" "));
	            }
	            if(subt!=null) name = trialname;
            }
            
            int counter = name==null?1:iters;
            name = name==null?unSantizedUnQuoted(parts[0].trim(), state):name;
            for (Command c : state.allSubTests) {
            	SubTestCommand st = (SubTestCommand)c;
				if(st.hook==null && st.name.equalsIgnoreCase(name)) {
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
            	throwError(fileLineDetails, new RuntimeException("No func/subtest with that name found - " + name));
            }
            
            for (; counter < parts.length; counter++) {
				String param = unSantizedUnQuoted(parts[counter].trim(), state);
				String[] pdet = param.split(":");
				if(pdet.length!=2) {
					throwError(fileLineDetails, new RuntimeException("Invalid parameter syntax, please use param-name:param-value syntax"));
				}
				if(subt.params.contains(pdet[0].trim())) {
					if(params.containsKey(pdet[0].trim())) {
						throwError(fileLineDetails, new RuntimeException("Duplicate parameter name `"+pdet[0].trim()+"`"));
					}
					if(pdet[1]!=null && pdet[1].length()>=2 && pdet[1].charAt(0)==pdet[1].charAt(pdet[1].length()-1)) {
			            if(pdet[1].charAt(0)=='"' || pdet[1].charAt(0)=='\'') {
			            	pdet[1] = pdet[1].substring(1, pdet[1].length()-1);
			            }
			        }
					params.put(pdet[0].trim(), pdet[1]);
				} else {
					if(subt.params.size()==0) {
						throwError(fileLineDetails, new RuntimeException((subt.isAFunc?"func":"subtest")+" does not specify any parameters"));
					} else {
						throwError(fileLineDetails, new RuntimeException("Invalid "+(subt.isAFunc?"func":"subtest")+" parameter name, allowed parameters are " + subt.params.toString()));
					}
				}
			}
            
    		/*if(counter<parts.length && !subt.isAFunc) {
    			if(parts[counter].trim().matches("@[0-9]+")) {
                    sessionId = Integer.parseInt(parts[counter].trim().substring(1)) - 1;
                    if(sessionId<0) {
                    	throwError(fileLineDetails, new RuntimeException("Session id should be greater than 0"));
                    }
                } else {
                    sessionName = unSantizedUnQuoted(parts[counter].trim(), state);
                }
    		}*/
            
            if(subt.params.size()!=params.size()) {
            	throwError(fileLineDetails, new RuntimeException("Please provide all the arguments for execution"));
            }

            if(!subt.isAFunc) {
	            state.addSubtest(this);
            }
        }
    }
    
    // G8: ExecSubTestCommand promoted to its own file (ExecSubTestCommand.java)

    static Pattern tagRgx = Pattern.compile("(\\[[a-zA-Z0-9_\\-]+\\])");
    // G8: SubTestCommand promoted to its own file (SubTestCommand.java)

    // G8: VarCommand promoted to its own file (VarCommand.java)

    public static class JsVarCommand extends Command {
        String name;
        String script;
        JsVarCommand(String val, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            if(val.indexOf(" ")!=-1) {
                name = val.substring(0, val.indexOf(" ")).trim();
                script = val.substring(val.indexOf(" ")+1).trim();
                script = unSantizedUnQuoted(script, state);
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
            code = unSantizedUnQuoted(code, state);
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
            	cond = new FindCommand(parts[0].trim(), fileLineDetails, state);
                fpath = unSantizedUnQuoted(parts[1].trim(), state);
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

    public static class SaveImageCommand extends FindCommandImpl {
        String fpath;
        boolean isTmp = false;
        SaveImageCommand(String val, Object[] cmdDetails, CommandState state, boolean isTmp) {
            super(cmdDetails, state);
            String[] parts = val.split("[\t ]+");
            if(parts.length==1) {
                fpath = System.nanoTime()+".png";
                cond = new FindCommand(parts[0].trim(), fileLineDetails, state);
            } else {
            	cond = new FindCommand(parts[0].trim(), fileLineDetails, state);
                fpath = unSantizedUnQuoted(parts[1].trim(), state);
            }
            this.isTmp = isTmp;
        }
        String toCmd() {
            return "imgsave \"" + fpath + "\"";
        }
        String javacode() {
            String filepath = "evaluate(\""+esc(fpath)+"\")";
            if(isTmp) {
            	filepath = "getOutDir() + java.io.File.separator + evaluate(\"" + esc(fpath) + "\")";
            }
            StringBuilder b = new StringBuilder();
            b.append(cond.javacodeonly());
            b.append("saveImageToFile(get___d___(), ___ce___.get(0), "+filepath+");\n");
            return b.toString();
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
				"Save element image to file",
				"\timgsave {element-selector} {optional image-file-path-to-save-screenshot-to}",
				"Examples :-",
	    		"\timgsave id@'eleid'",
	    		"\timgsave id@'eleid' '/path/to/image/file/file.png'"
            };
        }
    }

    public static class ReadImageTextCommand extends FindCommandImpl {
        String varname, fpath;
        ReadImageTextCommand(String val, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            String[] parts = val.split("[\t ]+");
            cond = new FindCommand(parts[0].trim(), fileLineDetails, state);
            if(parts.length<2)
            	throwParseError(cmdDetails, new RuntimeException("variable name should be provided.."));
            
            varname = unSantizedUnQuoted(parts[1].trim(), state);
            fpath = System.nanoTime()+".png";
            if(parts.length>2) {
                fpath = unSantizedUnQuoted(parts[2].trim(), state);
            }
        }
        String toCmd() {
            return "readimgtext " + varname + " \"" + fpath + "\"";
        }
        String javacode() {
            String filepath = "evaluate(\""+esc(fpath)+"\")";
            StringBuilder b = new StringBuilder();
            b.append(cond.javacodeonly());
            String cd = varname.startsWith("@")?"\n___cxt___add_param__(\""+varname.substring(1)+"\", readImageText(get___d___(), ___ce___.get(0), "+filepath+"));":"\n___add_var__(\""+varname+"\", readImageText(get___d___(), ___ce___.get(0), "+filepath+"));\n";
            b.append(cd);
            return b.toString();
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
				"Read text from an element image using OCR (Captcha)",
				"\treadimgtext {element-selector} {varname} {optional image-file-path-to-save-screenshot-to}",
				"\tocr {element-selector} {varname} {optional image-file-path-to-save-screenshot-to}",
				"Examples :-",
	    		"\treadimgtext id@'eleid' somevar",
	    		"\tocr id@'eleid' somevar",
	    		"\treadimgtext id@'eleid' captchatext '/path/to/image/file/file.png'"
            };
        }
    }

    public static class ReadQRCodeBarCodeCommand extends FindCommandImpl {
        String varname, fpath;
        ReadQRCodeBarCodeCommand(String val, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            String[] parts = val.split("[\t ]+");
            cond = new FindCommand(parts[0].trim(), fileLineDetails, state);
            if(parts.length<2)
            	throwParseError(cmdDetails, new RuntimeException("variable name should be provided.."));
            
            varname = unSantizedUnQuoted(parts[1].trim(), state);
            fpath = System.nanoTime()+".png";
            if(parts.length>2) {
                fpath = unSantizedUnQuoted(parts[2].trim(), state);
            }
        }
        String toCmd() {
            return "readqrbar " + varname + " \"" + fpath + "\"";
        }
        String javacode() {
            String filepath = "evaluate(\""+esc(fpath)+"\")";
            StringBuilder b = new StringBuilder();
            b.append(cond.javacodeonly());
            String cd = varname.startsWith("@")?"\n___cxt___add_param__(\""+varname.substring(1)+"\", readQrBarCode(get___d___(), ___ce___.get(0), "+filepath+"));":"\n___add_var__(\""+varname+"\", readQrBarCode(get___d___(), ___ce___.get(0), "+filepath+"));\n";
            b.append(cd);
            return b.toString();
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
				"Read text from a QR code or a Bar code",
				"\treadqrbar {element-selector} {varname} {optional image-file-path-to-save-screenshot-to}",
				"\tqrcode {element-selector} {varname}  {optional image-file-path-to-save-screenshot-to}",
				"\tbarcode {element-selector} {varname} {optional image-file-path-to-save-screenshot-to}",
				"Examples :-",
	    		"\treadqrbar id@'eleid' somevar",
	    		"\tqrcode id@'eleid' somevar",
	    		"\tbarcode id@'eleid' somevar",
	    		"\treadqrbar id@'eleid' captchatext '/path/to/image/file/file.png'"
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
                    String name = unSantizedUnQuoted(c, state);
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
				"\trequire fqcn|[{fqcn1},..{fqcnN}]",
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
            name = unSantizedUnQuoted(cmd, state);
        }
        String toCmd() {
            return "include " + name;
        }
        String javacode() {
            return "";
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
				"Include other seleasy scripts inline",
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
            name = unSantizedUnQuoted(cmd, state);
        }
        String toCmd() {
            return "import " + name;
        }
        String javacode() {
            return "";
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
				"Import only subtests/functions from other seleasy scripts",
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
            name = unSantizedUnQuoted(cmd, state);
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
            name = unSantizedUnQuoted(cmd, state);
        }
        String toCmd() {
            return "dynprops " + name;
        }
        String javacode() {
        	return "";
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
				"Import dynamic (code vars) properties file, dynamic variables provide support for configurable code",
				"\tdynprops {file-path}",
				"Examples :-",
	    		"\tdynprops a/b/c/t1.props",
	    		"\tdynprops t2.props"
            };
        }
    }

    // G8: WaitTillElementVisibleOrInvisibleCommand promoted to its own file (WaitTillElementVisibleOrInvisibleCommand.java)
    
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
        protected FindCommand cond;
        FindCommandImpl(Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
        }
		public static String[] toSampleSelCmd() {
			return null;
		}
    }

    // G8: IfCommand promoted to its own file (IfCommand.java)

    public static class ElseIfCommand extends Command {
    	List<FindCommand> conds = new ArrayList<>();
        boolean negation;
        ElseIfCommand(String cmd, boolean negation, Object[] cmdDetails, CommandState state) {
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
            b.append(negation?":?!":":?");   // Δ145c: was ":?" — the round-trip text lost the negation
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
            b.append(IfCommand.getFp(this, conds, children, negation, state, vrd));
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

    // G8: ScopedLoopCommand promoted to its own file (ScopedLoopCommand.java)

    public static class ProviderLoopCommand extends Command {
    	enum ProviderType {
    		Counter, Provided, InlineSQL, InlineMongo, InlineFile, Optional
    	}
    	
        String name, dsn, query, filePath, collName;
        String[] vars, props;
        int index = Integer.MIN_VALUE;
        int end = Integer.MIN_VALUE;
        boolean isStateFul = false;
        boolean isValid = true;
        ProviderType type = ProviderType.Provided;
        ProviderLoopCommand(String val, Object[] cmdDetails, CommandState state, boolean isStateFul, ProviderType type) {
            super(cmdDetails, state);
            this.isStateFul = isStateFul;
            this.type = type;
            String[] parts = val.trim().split("[\t ]+");
            if(type==ProviderType.Optional) {
            	isValid = false;
    			if(state.dynProps.containsKey(parts[0].trim()) && state.dynProps.get(parts[0].trim()).toLowerCase().matches("yes|true|1|enabled|on")) {
    				isValid = true;
    			}
            } else if(type==ProviderType.Counter) {
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
                	String t = unSantizedUnQuoted(parts[0].trim(), state);
                	dsn = t.trim();
                	t = unSantizedUnQuoted(parts[1].trim(), state);
                	query = t.trim();
                	if(!query.toLowerCase().matches("^(select|with|call)[\t ]+.*") && type==ProviderType.InlineSQL) {
                		throwParseError(cmdDetails, new RuntimeException("SQL provider only allows select|with|call queries"));
                	}
                	t = unSantizedUnQuoted(parts[2].trim(), state);
                	vars= t.split(",");
                } else {
                    throwParseError(cmdDetails, new RuntimeException("SQL provider command should have the syntax `#sql {dsn} {query} {var1,var2,..}`"));
                }
            } else if(type==ProviderType.InlineMongo) {
                if(parts.length==3) {
                	String t = unSantizedUnQuoted(parts[0].trim(), state);
                	dsn = t.trim();
                	t = unSantizedUnQuoted(parts[1].trim(), state);
                	collName = t.trim();
                	t = unSantizedUnQuoted(parts[2].trim(), state);
                	query = t.trim();
            		try {
						WorkflowContextHandler.OM.readTree(query);
					} catch (Exception e) {
						throwParseError(cmdDetails, new RuntimeException("Mongo provider needs valid json queries"));
					}
                	t = unSantizedUnQuoted(parts[3].trim(), state);
                	vars= t.split(",");
                	t = unSantizedUnQuoted(parts[4].trim(), state);
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
                	String t = unSantizedUnQuoted(parts[0].trim(), state);
                	filePath = t.trim();
                	t = unSantizedUnQuoted(parts[1].trim(), state);
                	vars= t.split(",");
                } else {
                    throwParseError(cmdDetails, new RuntimeException("File provider command should have the syntax `#file {filepath} {var1,var2,..}`"));
                }
            } else {
                name = unSantizedUnQuoted(parts[0].trim(), state);
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
        	if(type==ProviderType.Optional)
            {
        		if(isValid) {
	        		for (Command c : children) {
	                	b.append(genDebugInfo(c));
	                    b.append(c.javacode());
	                    b.append("\n");
	                }
        		}
        		return b.toString();
            }
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
                	name = UUID.randomUUID().toString();
                	b.append("int "+state.varname()+" = getSQLProviderTestDataMap(\""+esc(name)+"\", \""+esc(dsn)+"\", evaluate(\""+esc(query)+"\"), \""+esc(StringUtils.join(vars, ","))+"\").size();\n");
                } else if(type==ProviderType.InlineMongo) {
                	name = UUID.randomUUID().toString();
                	b.append("int "+state.varname()+" = getMongoProviderTestDataMap(\""+esc(name)+"\", \""+esc(dsn)+"\", evaluate(\""+esc(query)+"\"), \""+esc(collName)+"\", \""+esc(StringUtils.join(props, ","))+"\", \""+esc(StringUtils.join(vars, ","))+"\").size();\n");
                } else if(type==ProviderType.InlineFile) {
                	name = UUID.randomUUID().toString();
                	b.append("int "+state.varname()+" = getFileProviderTestDataMap(\""+esc(name)+"\", \""+esc(filePath)+"\", \""+esc(StringUtils.join(vars, ","))+"\").size();\n");
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

    // G8: ReadFileCommand promoted to its own file (ReadFileCommand.java)

    public static class FileVarCommand extends Command {
    	String regex;
    	String[] vars, positions;
        FileVarCommand(String val, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            String[] parts = val.trim().split("[\t ]+");
            if(parts.length==3) {
            	String t = unSantizedUnQuoted(parts[0].trim(), state);
            	vars= t.split(",");
            	regex = unSantizedUnQuoted(parts[1].trim(), state);
            	try {
					Pattern.compile(regex);
				} catch (Exception e) {
					throwParseError(cmdDetails, new RuntimeException("Invalid regex pattern specified " + regex));
				}
            	t = unSantizedUnQuoted(parts[2].trim(), state);
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
                String t = unSantizedUnQuoted(parts[0].trim(), state);
                dsn = t;
                t = unSantizedUnQuoted(parts[1].trim(), state);
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

    // G8: TransientVariableCommand promoted to its own file (TransientVariableCommand.java)

    // G8: TransientProviderCommand promoted to its own file (TransientProviderCommand.java)

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
            // strip serialization noise ("class"/"property") that legacy XML configs baked into
            // these maps via <class>linked-hash-map</class>/<property>..</property> elements; they
            // are not valid Selenium capabilities/prefs and make newer Selenium reject newSession
            config.getCapabilities().remove("class");
            config.getCapabilities().remove("property");
            if(config.getProperties()!=null) {
                config.getProperties().remove("class");
                config.getProperties().remove("property");
            }
            if(state.hasRouteCommand || state.hasEmulateCommand) b.append("__route_bidi__();\n");   // M4/M8: before the driver is created
            b.append("/*GATF_ST_DRIVER_INIT_"+(config.getName().toUpperCase())+"*/");
            DriverInitCodegen.append(b, config, rUrl, fileLineDetails);   // G8: per-browser driver-options codegen
            b.append("initBrowser(get___d___(), logconsole, logdebug, lognw, "+state.hasNetapixCommand+");\n");
            
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

    // G8: TimeoutCommand promoted to its own file (TimeoutCommand.java)

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
        // Δ145c: the `timeout ... <granularity>` in effect where this frame is written (as FindCommand): a later
        // `timeout` line must not change this statement's retry sleep
        long sleepGranularity = state.timeoutSleepGranularity;
        // Δ145d: and the retry count (`timeout` seconds) in effect here, for the same reason
        int timeoutNum = state.timeoutNum;
        FrameCommand(String cmd, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            name = unSantizedUnQuoted(cmd, state);
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
                b.append("sleep("+sleepGranularity+");\n");
                b.append("if("+cntvar+"++=="+timeoutNum+")throw new RuntimeException(\"Unable to move to frame ("+name+")\");\n");
                b.append("}\n");
        		b.append("switchToFrame(___ocw___, \"\");\n");//Switch to main frame
        		return b.toString();
        	}
            return "switchToFrame(___ocw___, \""+esc(name)+"\");\n";
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
        		"Select frame or use a scoped frame block",
        		"\tframe main|parent|1..N|{some-name}",
				"Examples :-",
				"\tframe main",
				"\tframe parent",
				"\tframe 2",
				"\tframe \"my-frame\"",
				"\tframe\n\t{...\n\t}"
            };
        }
    }

    // G8: WindowCommand promoted to its own file (WindowCommand.java)

    // G8: TabCommand promoted to its own file (TabCommand.java)

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
        		String whl = "List<String> "+acvn+" = new java.util.ArrayList<String> (__handles__(___ocw___));\n";
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
            // E13: in the loginOnce script, save the login while the last window is still open (nothing is left at quit)
            return (state.isLoginOnceScript ? "__loginOnceSave__(___cw___);\n" : "") + "___cw___.close();";
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
            name = unSantizedUnQuoted(cmd, state);
            if(StringUtils.isNotBlank(name)) {
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
	            value = unSantizedUnQuoted(cmd, state);
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
            return "pushResult(new SeleniumTestResult(get___d___(), this, new WarnSubTestException(\""+esc(value)+"\"), getOutDir() + java.io.File.separator + \""+UUID.randomUUID().toString()+".png\", ___lp___, null));\n";
            //return "if(true)\n{\nthrow new WarnSubTestException(\""+esc(value)+"\");\n}";
        }
        WarnCommand(String cmd, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
	        value = unSantizedUnQuoted(cmd, state);
            if(StringUtils.isBlank(value)) {
                throwParseError(cmdDetails, new RuntimeException("Warn needs a mandatory message"));
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
            return "pushResult(new SeleniumTestResult(get___d___(), this, new PassSubTestException(\""+esc(value)+"\"), getOutDir() + java.io.File.separator + \""+UUID.randomUUID().toString()+".png\", ___lp___, null));\n";
            //return "if(true)\n{\nthrow new PassSubTestException(\""+esc(value)+"\");\n}";
        }
        PassCommand(String cmd, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            value = unSantizedUnQuoted(cmd, state);
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
    
    static String esc1(String cmd) {
        String blid = "######" + UUID.randomUUID().toString() + "######";
        String nlid = "######" + UUID.randomUUID().toString() + "######";
        String tbid = "######" + UUID.randomUUID().toString() + "######";
        String crid = "######" + UUID.randomUUID().toString() + "######";
        String ffid = "######" + UUID.randomUUID().toString() + "######";
        String tmp = cmd.replace("\\\\b", blid).replace("\\\\n", nlid).replace("\\\\t", tbid)
                .replace("\\\\r", crid).replace("\\\\f", ffid);
        tmp = tmp.replace(blid, "\\\\b").replace(nlid, "\\\\n").replace(tbid, "\\\\t")
                .replace(crid, "\\\\r").replace(ffid, "\\\\f");
        return tmp.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // G8: FindCommand promoted to its own file (FindCommand.java)

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

    // G8: TypeCommand promoted to its own file (TypeCommand.java)
    
    public static class UploadCommand extends FindCommandImpl {
        String value;
        int count = 1;
        UploadCommand(String val, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            String[] parts = val.trim().split("[\t ]+");
            if(parts.length>0) {
                parts[0] = parts[0].trim();
                value = unSantizedUnQuoted(parts[0], state);
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
        String colsep = "|";
        PrintPDFCommand(String cmd, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            String[] parts = cmd.trim().split("[\t ]+");
            this.extractText = parts.length>1 && parts[1].trim().equalsIgnoreCase("text")?true:false;
            // the separator is the 3rd token (`printpdf <file> text <sep>`); this used to read parts[1], i.e. the word "text"
            colsep = (parts.length>2 && StringUtils.isNotBlank(parts[2]))?unSantizedUnQuoted(parts[2].trim(), state):"|";
            name = unSantizedUnQuoted(parts[0].trim(), state);
            if(StringUtils.isBlank(name)) {
            	throwParseError(null, new RuntimeException("Print pdf command needs a file path argument"));
            }
        }
        String toCmd() {
            return "printpdf \"" + name + "\"" + (extractText?" text" + ("|".equals(colsep)?"":" " + colsep):"");
        }
        String javacode() {
            return "\nprintToPdf(___cw___, evaluate(\""+esc(name)+"\"), "+extractText+", \""+esc(colsep)+"\");\n";
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
        		"Print the page to a PDF file (optionally extract its text/tables to <file>.txt)",
        		"\tprintpdf {pdf-filepath} {text?} {col-separator}?",
        		"Examples :-",
        		"\tprintpdf '/path/to/page.pdf'",
        		"\tprintpdf '/path/to/page.pdf' text",
        		"\tprintpdf '/path/to/page.pdf' text ;",
            };
        }
    }
    
    public static class MailCommand extends Command {
    	String from, to, subject, content;
    	long timeout = 20000;
        MailCommand(String cmd, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            String[] parts = cmd.trim().split("[\t ]+");
            if(StringUtils.isBlank(parts[0])) {
            	throwParseError(null, new RuntimeException("Mail Simulator Message Checker command needs test name argument"));
            }
            name = unSantizedUnQuoted(parts[0].trim(), state);
            if(StringUtils.isBlank(parts[1])) {
            	throwParseError(null, new RuntimeException("Mail Simulator Message Checker command needs from address argument"));
            }
            from = unSantizedUnQuoted(parts[1].trim(), state);
            if(StringUtils.isBlank(parts[2])) {
            	throwParseError(null, new RuntimeException("Mail Simulator Message Checker command needs to address argument"));
            }
            to = unSantizedUnQuoted(parts[2].trim(), state);
            if(StringUtils.isBlank(parts[3])) {
            	throwParseError(null, new RuntimeException("Mail Simulator Message Checker command needs subject argument"));
            }
            subject = unSantizedUnQuoted(parts[3].trim(), state);
            if(StringUtils.isNotBlank(parts[4])) {
	            try {
					timeout = Long.parseLong(parts[4].trim());
				} catch (Exception e) {
					content = unSantizedUnQuoted(parts[4].trim(), state);
				}
            }
            if(StringUtils.isNotBlank(parts[5])) {
	            try {
					timeout = Long.parseLong(parts[5].trim());
				} catch (Exception e) {
					throwParseError(null, new RuntimeException("Mail Simulator Message Checker command timeout argument should be a number"));
				}
            }
            if(content==null) content = "";
        }
        String toCmd() {
            return "mail \"" + name + "\" \""+from+"\" \""+to+"\" \""+subject+"\" \""+content+"\" " + timeout;
        }
        String javacode() {
            return "checkMail(\""+esc(name)+"\", \""+esc(from)+"\", \""+esc(to)+"\", \""+esc(subject)+"\", \""+esc(content)+"\", "+timeout+");\n";
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
        		"Check whether email was received from a given sender, for a given receiver, with a given subject and matching the given content with timeout period",
        		"\tmail {name}  {from} {to} {subject} {content}? {timeout}?",
        		"Examples :-",
        		"\tmail 'mail for appointment' 'abc@a.com' 'abc@b.com' 'Hello There'",
        		"\tmail 'mail for appointment' 'abc@a.com' 'abc@b.com' 'Hello There' 'How are you doing today'",
        		"\tmail 'mail for appointment' 'abc@a.com' 'abc@b.com' 'Hello There' 'How are you doing today (.*)'",
        		"\tmail 'mail for appointment' 'abc@a.com' 'abc@b.com' 'Hello There' 'How are you doing today (.*)' 50000",
            };
        }
    }
    
    public static class HttpCommand extends Command {
    	String from, to, content;
    	long timeout = 20000;
    	HttpCommand(String cmd, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            String[] parts = cmd.trim().split("[\t ]+");
            if(StringUtils.isBlank(parts[0])) {
            	throwParseError(null, new RuntimeException("Http Simulator Request Checker command needs test name argument"));
            }
            name = unSantizedUnQuoted(parts[0].trim(), state);
            if(StringUtils.isBlank(parts[1])) {
            	throwParseError(null, new RuntimeException("Http Simulator Request Checker command needs from address argument"));
            }
            from = unSantizedUnQuoted(parts[1].trim(), state);
            if(StringUtils.isBlank(parts[2])) {
            	throwParseError(null, new RuntimeException("Http Simulator Request Checker command needs to address argument"));
            }
            to = unSantizedUnQuoted(parts[2].trim(), state);
            if(StringUtils.isBlank(parts[3])) {
            	throwParseError(null, new RuntimeException("Http Simulator Request Checker command needs content argument"));
            }
            content = unSantizedUnQuoted(parts[3].trim(), state);
            if(StringUtils.isNotBlank(parts[4])) {
	            try {
					timeout = Long.parseLong(parts[4].trim());
				} catch (Exception e) {
					throwParseError(null, new RuntimeException("Http Simulator Request Checker command timeout argument should be a number"));
				}
            }
            if(content==null) content = "";
        }
        String toCmd() {
            return "http \"" + name + "\" \""+from+"\" \""+to+"\" \""+content+"\" " + timeout;
        }
        String javacode() {
            return "checkHttpRequest(\""+esc(name)+"\", \""+esc(from)+"\", \""+esc(to)+"\", \""+esc(content)+"\", "+timeout+");\n";
        }
        public static String[] toSampleSelCmd() {
        	return new String[] {
    			"Check whether an http request was received from a given sender, for a given receiver and matching the given content with timeout period",
    			"\thttp {name}  {from} {to} {content} {timeout}?",
    			"Examples :-",
    			"\thttp 'http request message for sms' 'from' 'to' 'How are you doing today'",
    			"\thttp 'http request message for sms' 'from' 'to' 'How are you doing today (.*)'",
    			"\thttp 'http request message for sms' 'from' 'to' 'How are you doing today (.*)' 50000",
        	};
        }
    }
    
    // G8: WindowOpenSaveInterceptJsCommand promoted to its own file (WindowOpenSaveInterceptJsCommand.java)
    
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
            	throwParseError(null, new RuntimeException("netapix command needs to be enabled/disabled, valid values on|off"));
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

    // G8: RandomizeCommand promoted to its own file (RandomizeCommand.java)

    // G8: ChordCommand promoted to its own file (ChordCommand.java)

    // G8: SelectCommand promoted to its own file (SelectCommand.java)

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
            if(state.autoWait) b.append("\nawaitActionable(___cw___, "+cvarnm+".get(0), \"hover\");");
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
            // Δ145c: both finds now leave their match in ___ce___ (handleWaitFuncWL); keep the hover match before the
            // second find overwrites it (was currvarname()/topele(), which no longer name the found elements → null[0])
            String hevarnm = state.varname();
            b.append("\nList<WebElement> "+hevarnm+" = ___ce___;\n");
            if(condCe!=null) {
                b.append(condCe.javacodeonly());
            }
            String hvvrnm = state.varname();
            b.append("\norg.openqa.selenium.interactions.Actions "+hvvrnm+" = new org.openqa.selenium.interactions.Actions(___cw___);");
            if(state.autoWait) b.append("\nawaitActionable(___cw___, "+hevarnm+".get(0), \"hover\");");
            b.append("\n"+hvvrnm+".moveToElement("+hevarnm+".get(0)).click("+(condCe!=null?"___ce___":hevarnm)+".get(0)).perform();");
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

    // G8: ActionsCommand promoted to its own file (ActionsCommand.java)

    // G8: RobotCommand promoted to its own file (RobotCommand.java)

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
                b.append("\nclickAction(___ocw___, ___ce___, "+(name!=null?("\""+name+"\""):null)+", \""+(cond.by!=null?esc(cond.by):"")+(cond.classifier!=null?"@"+esc(cond.classifier):"")+"\");\n");
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
            if(state.autoWait) b.append("\nawaitActionable(___cw___, ___ce___.get(0), \"dblclick\");");
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
                if(state.autoWait) b.append("\nawaitActionable(___cw___, ___ce___.get(0), \"clear\");");
                b.append(cond.getActionable("clear", null, null));
            } else {
                if(state.autoWait) b.append("\nawaitActionable(___cw___, "+state.currvarname()+".get(0), \"clear\");");
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
                if(state.autoWait) b.append("\nawaitActionable(___cw___, ___ce___.get(0), \"submit\");");
                b.append(cond.getActionable("submit", null, null));
            } else {
                if(state.autoWait) b.append("\nawaitActionable(___cw___, "+state.currvarname()+".get(0), \"submit\");");
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
            	return "\nGatfAssert.assertTrue(\"\", handleAlertConfirm(___cw___, true, false, \""+esc(value)+"\"));\n";
                /*String avn = state.alvarname();
                String c = "Alert "+avn+" = ___cw___.switchTo().alert();\n";
                c += "\nAssert.assertEquals(\""+esc(value)+"\", "+avn+".getText());\n"+avn+".accept();\n";
                return c;*/
            } else {
            	return "\nGatfAssert.assertTrue(\"\", handleAlertConfirm(___cw___, true, false, null));\n";
                //return "___cw___.switchTo().alert().accept();";
            }
        }
        public AlertCommand(String info, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            value = unSantizedUnQuoted(info, state);
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
            	return "\nGatfAssert.assertTrue(\"\", handleAlertConfirm(___cw___, true, "+isOk+", \""+esc(value)+"\"));\n";
                /*String avn = state.alvarname();
                String c = "Alert "+avn+" = ___cw___.switchTo().alert();\n";
                c += "\nAssert.assertEquals(\""+esc(value)+"\", "+avn+".getText());\n"+avn+"."+(isOk?"accept":"dismiss")+"();\n";
                return c;*/
            } else {
            	return "\nGatfAssert.assertTrue(\"\", handleAlertConfirm(___cw___, true, "+isOk+", null));\n";
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
                value = unSantizedUnQuoted(t[1], state);
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

    // G8: MTapCommand promoted to its own file (MTapCommand.java)

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

    // G8: MTouchActionCommand promoted to its own file (MTouchActionCommand.java)

    public static String unSantizedUnQuoted(String value, CommandState state) {
        value = state.unsanitize(value.trim());
        if(value!=null && value.length()>=2 && value.charAt(0)==value.charAt(value.length()-1)) {
            if(value.charAt(0)=='"' || value.charAt(0)=='\'') {
                value = value.substring(1, value.length()-1);
            }
        }
        return value;
    }

    // G8: PluginCommand promoted to its own file (PluginCommand.java)
    
    /**
     * User arguments with each managed flag removed (token-exact) and then appended once, space-separated
     * with a leading space, e.g. " --ignore-certificate-errors --remote-allow-origins=*" for blank input.
     * Pure: never mutates the driver config (generation must be repeatable).
     */
    static String withManagedArgs(String userArgs, String... managed) {
        StringBuilder b = new StringBuilder();
        if (userArgs != null) {
            java.util.Set<String> m = new java.util.HashSet<String>(java.util.Arrays.asList(managed));
            for (String t : userArgs.trim().split("\\s+")) {
                if (!t.isEmpty() && !m.contains(t)) b.append(b.length() == 0 ? "" : " ").append(t);
            }
        }
        for (String f : managed) b.append(' ').append(f);
        return b.toString();
    }

    public static String validateSel(String[] args, String className, boolean testExecution) throws Exception {
        GatfExecutorConfig config = getConfig(args.length>2?args[2].trim():(new File(args.length>3?args[3].trim():"/workdir", "gatf-config.json").exists()?"gatf-config.json":"gatf-config.xml"), args.length>3?args[3].trim():"/workdir");
        config.setSeleniumLoggerPreferences("browser(OFF),client(OFF),driver(OFF),performance(OFF),profiler(OFF),server(OFF)");
        for (SeleniumDriverConfig selConf : config.getSeleniumDriverConfigs())
        {
            if(selConf.getDriverName()!=null && selConf.getPath()!=null && new File(selConf.getPath()).exists()) {
                System.setProperty(selConf.getDriverName(), selConf.getPath());
            }
        }
        
        //System.setProperty("java.home", configuration.getJavaHome());
        System.setProperty("jdk.tls.client.protocols", "TLSv1,TLSv1.1,TLSv1.2");
        Security.setProperty("crypto.policy", "unlimited");
        System.setProperty("webdriver.http.factory", "jdk-http-client");
        if(!SystemUtils.IS_OS_WINDOWS) {
        	System.setProperty("jna.library.path", "/usr/local/lib:/usr/lib");
        }
        
        if(config.getExtraProperties()!=null) {
        	if(config.getExtraProperties().containsKey("jdk.tls.client.protocols")) {
        		System.setProperty("jdk.tls.client.protocols", config.getExtraProperties().get("jdk.tls.client.protocols"));
        	} else if(config.getExtraProperties().containsKey("crypto.policy")) {
        		System.setProperty("crypto.policy", config.getExtraProperties().get("crypto.policy"));
        	} else if(config.getExtraProperties().containsKey("webdriver.http.factory")) {
        		System.setProperty("webdriver.http.factory", config.getExtraProperties().get("webdriver.http.factory"));
        	} else if(config.getExtraProperties().containsKey("jna.library.path")) {
        		System.setProperty("jna.library.path", config.getExtraProperties().get("jna.library.path"));
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
        	Command[] out = new Command[1];
        	Map<String, List<List<Integer[]>>> selToJavaLineMap = new LinkedHashMap<>();
        	SeleniumTest dyn = null;
        	if(className!=null) {
        		dyn = (SeleniumTest)Class.forName(className).getConstructor(new Class[] {AcceptanceTestContext.class, int.class}).newInstance(new Object[] {c, 0});
        	} else {
        		dyn = SeleniumCodeGeneratorAndUtil.getSeleniumTest(args[1], Command.class.getClassLoader(), c, retvals, config, args.length>4?args[4].trim().equalsIgnoreCase("true"):false, selToJavaLineMap, out);
        	}
            System.out.println(dyn!=null?"SUCCESS":"FAILURE");
            if(dyn!=null) {
            	if(testExecution) {
            		try {
            			final LoggingPreferences lp = SeleniumCodeGeneratorAndUtil.getLp(config);
						dyn.execute(lp, "");
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
        	if(e.multiple!=null && e.multiple.size()>0) {
        		for (GatfSelCodeParseError ec : e.multiple) {
					ec.printStackTrace();
				}
        	}
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
    	Pattern p = Pattern.compile("'(?:\\\\'|[^'])*'");
        Matcher m1 = p.matcher(".. '\\\\\"test me'..'Say \\'Hello\\'!'..'\\\\\\\\ \\\\'' .. ");
        while (m1.find()) {
            System.out.println(m1.toMatchResult().group());
        }
        
        p = Pattern.compile("\"(?:\\\\\\\"|[^\\\"])*\"");
        m1 = p.matcher("? eval ${a}==\"a\" & eval ${a}==\"a\"");
        while (m1.find()) {
            System.out.println(m1.toMatchResult().group());
        }
    	
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
                    
                    //if(configuration.getTestCaseDir()==null)
                        //configuration.setTestCaseDir("");
                    
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
