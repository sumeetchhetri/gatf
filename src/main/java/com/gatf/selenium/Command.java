/*
    Copyright 2013-2016, Sumeet Chhetri

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
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.SystemUtils;
import org.openqa.selenium.Keys;

import com.gatf.executor.core.AcceptanceTestContext;
import com.gatf.executor.core.GatfExecutorConfig;
import com.gatf.executor.core.TestCase;
import com.google.googlejavaformat.java.Formatter;

public class Command {

    protected static class CommandState {
        boolean commentStart = false, codeStart = false, started = false, pluginstart = false;
        String basePath = "";
        int concExec = 0;

        int NUMBER = 1;
        int NUMBER_COND = 1;
        int NUMBER_SR = 1;
        int NUMBER_DR = 1;
        int NUMBER_LIV = 1;
        int NUMBER_EXC = 1;
        int NUMBER_ST = 1;
        int NUMBER_IF = 1;
        int NUMBER_AL = 1;
        int NUMBER_RD = 1;
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

        List<Command> allSubTests = new ArrayList<Command>();
        Map<String, String> qss = new HashMap<String, String>();
        Map<String, String> plugins = new HashMap<String, String>();
        Set<String> visitedFiles = new HashSet<String>();

        String varnamerandom() {
            return "___vd___" + NUMBER_RD++;
        }

        String ifvarname() {
            return "___i___" + NUMBER_IF++;
        }

        String alvarname() {
            return "___a___" + NUMBER_AL++;
        }

        String lvarname() {
            return "___w___" + NUMBER++;
        }

        String lcurrvarname() {
            return "___w___" + (NUMBER-1);
        }

        String varname() {
            return "___w___" + NUMBER++;
        }

        String currvarname() {
            return "___w___" + (NUMBER-1);
        }

        String varnamesr() {
            return "___sr___" + NUMBER_SR++;
        }

        String currvarnamesr() {
            return "___sr___" + (NUMBER_SR-1);
        }

        String dvarname() {
            return "___d___" + NUMBER_DR++;
        }

        String currdvarname() {
            return "___d___" + (NUMBER_DR-1);
        }

        String evarname() {
            return "___e___" + NUMBER_EXC++;
        }

        String currvarnamesc() {
            return sc;
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
                    qss.put(m.group(), "######" + UUID.randomUUID().toString() + "######");
                }
            }
            for (String qs : qss.keySet())
            {
                cmd = cmd.replace(qs, qss.get(qs));
            }
            p = Pattern.compile("'([^']*)'");
            m = p.matcher(cmd);
            while (m.find()) {
                if(!qss.containsKey(m.group())) {
                    qss.put(m.group(), "######" + UUID.randomUUID().toString() + "######");
                }
            }
            for (String qs : qss.keySet())
            {
                cmd = cmd.replace(qs, qss.get(qs));
            }
            return cmd;
        }
    }

    protected CommandState state;
    protected String name;
    protected Object[] fileLineDetails;
    protected Map<String, SeleniumDriverConfig> mp;
    protected String className = "STC_" + System.nanoTime() + "";
    protected List<Command> children = new ArrayList<Command>();

    static Pattern p = Pattern.compile("\"([^\"]*)\"");
    static Pattern WAIT = Pattern.compile("^\\?\\?[\t ]*([0-9]+)");
    static Pattern CONCNUM = Pattern.compile("^\\^[\t ]*([0-9]+)");

    Command(Object[] fileLineDetails, CommandState state) {
        this.fileLineDetails = fileLineDetails;
        this.state = state;
    }

    public String getClassName() {
        return className;
    }

    @SuppressWarnings("serial")
    public static class GatfSelCodeParseError extends RuntimeException {
        public GatfSelCodeParseError(String message) {
            super(message);
        }
        public GatfSelCodeParseError(String message, Throwable e) {
            super(message, e);
        }
    }

    void throwParseError(Object[] o) {
        throwParseError(o, null);
    }

    void throwParseError(Object[] o, Throwable e) {
        if(e!=null) {
            if(o!=null) {
                throw new GatfSelCodeParseError("Error parsing command at line "+o[1]+" in file "+o[2]+" ("+o[0]+")", e);
            }
            throw new GatfSelCodeParseError("Error parsing command at line "+fileLineDetails[1]+" in file "+fileLineDetails[2]+" ("+fileLineDetails[0]+")", e);
        }
        if(o!=null) {
            throw new GatfSelCodeParseError("Error parsing command at line "+o[1]+" in file "+o[2]+" ("+o[0]+")");
        }
        throw new GatfSelCodeParseError("Error parsing command at line "+fileLineDetails[1]+" in file "+fileLineDetails[2]+" ("+fileLineDetails[0]+")");
    }

    static void throwParseErrorS(Object[] o, Throwable e) {
        if(e!=null) {
            throw new GatfSelCodeParseError("Error parsing command at line "+o[1]+" in file "+o[2]+" ("+o[0]+")", e);
        }
        throw new GatfSelCodeParseError("Error parsing command at line "+o[1]+" in file "+o[2]+" ("+o[0]+")");
    }

    static void throwError(Object[] o, Throwable e) {
        if(e!=null) {
            throw new GatfSelCodeParseError("Error at line "+o[1]+" in file "+o[2]+" ("+o[0]+")", e);
        }
        throw new GatfSelCodeParseError("Error at line "+o[1]+" in file "+o[2]+" ("+o[0]+")");
    }

    int weight() {
        return 100;
    }

    static Command parse(Object[] cmdDetails, CommandState state) {
        String cmd = cmdDetails[0].toString().trim();
        Command comd = null;
        cmd = state.sanitize(cmd);
        if(state.commentStart && !cmd.contains("*/")) {
            comd = new ValueCommand(cmdDetails, state);
            ((ValueCommand)comd).value = cmd;
        } else if(state.codeStart && !cmd.equals(">>>")) {
            comd = new ValueCommand(cmdDetails, state);
            ((ValueCommand)comd).value = cmd;
        } else if(cmd.startsWith("??")) {
            String time = "0";
            Matcher m = WAIT.matcher(cmd);
            int start = 2;
            if(m.find()) {
                time = m.group(1);
                start = m.end(1) + 1;
            }
            cmd = cmd.substring(start).trim();
            comd = new ValidateCommand(time, cmd, cmdDetails, state);
        } else if (cmd.startsWith("//")) {
            cmd = cmd.substring(2);
            comd = new CommentCommand(false, cmdDetails, state);
            ValueCommand vc = new ValueCommand(cmdDetails, state);
            vc.value = cmd;
            comd.children.add(vc);
        } else if (cmd.startsWith("/*")) {
            cmd = cmd.substring(2);
            comd = new CommentCommand(true, cmdDetails, state);
            ValueCommand vc = new ValueCommand(cmdDetails, state);
            vc.value = cmd;
            comd.children.add(vc);
            state.commentStart = true;
        } else if (cmd.contains("*/")) {
            state.commentStart = false;
            if(!cmd.endsWith("*/")) {
                //exception
            }
            cmd = cmd.substring(0, cmd.length()-2);
            comd = new EndCommentCommand(cmd, cmdDetails, state);
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
            comd = new EndCommand(cmdDetails, state);
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
            cmd = cmd.substring((isIfNot?2:1));
            comd = new IfCommand(isIfNot, cmdDetails, state);
            ((IfCommand)comd).cond = new FindCommand(cmd, cmdDetails, state);
            ((IfCommand)comd).cond.suppressErr = true;
        } else if (cmd.startsWith(":?") || cmd.startsWith(":?!")) {
            boolean isIfNot = cmd.startsWith(":?!");
            cmd = cmd.substring((isIfNot?3:2));
            comd = new ElseIfCommand(isIfNot, cmdDetails, state);
            ((ElseIfCommand)comd).cond = new FindCommand(cmd, cmdDetails, state);
            ((ElseIfCommand)comd).cond.suppressErr = true;
        } else if (cmd.startsWith(":")) {
            cmd = cmd.substring(1);
            comd = new ElseCommand(cmdDetails, state);
        } else if (cmd.startsWith("#provider")) {
            cmd = cmd.substring(9).trim();
            if(cmd.isEmpty()) {
                //exception
            }
            comd = new ProviderLoopCommand(cmd.trim(), cmdDetails, state);
        } else if (cmd.startsWith("#transient-provider")) {
            cmd = cmd.substring(19).trim();
            if(cmd.isEmpty()) {
                //exception
            }
            comd = new TransientProviderLoopCommand(cmd.trim(), cmdDetails, state, false);
        } else if (cmd.startsWith("#transient-suite-provider")) {
            cmd = cmd.substring(19).trim();
            if(cmd.isEmpty()) {
                //exception
            }
            comd = new TransientProviderLoopCommand(cmd.trim(), cmdDetails, state, true);
        } else if (cmd.startsWith("##")) {
            cmd = cmd.substring(2);
            comd = new ScopedLoopCommand(cmdDetails, state);
            ((ScopedLoopCommand)comd).cond = new FindCommand(cmd, cmdDetails, state);
        } else if (cmd.startsWith("#")) {
            cmd = cmd.substring(1);
            comd = new ScopedLoopCommand(cmdDetails, state);
            ((ScopedLoopCommand)comd).cond = new FindCommand(cmd, cmdDetails, state);
        } else if (cmd.startsWith("[")) {
            comd = new ValueListCommand(cmdDetails, state);
            ((ValueListCommand)comd).type = "[";
        } else if (cmd.startsWith("]")) {
            comd = new EndCommand(cmdDetails, state);
            ((EndCommand)comd).type = "]";
        } else if (cmd.startsWith("{")) {
            comd = new StartCommand(cmdDetails, state);
            ((StartCommand)comd).type = "{";
        } else if (cmd.startsWith("}")) {
            comd = new EndCommand(cmdDetails, state);
            ((EndCommand)comd).type = "}";
        } else if (cmd.startsWith("fail ")) {
            comd = new FailCommand(cmd.substring(5).trim(), cmdDetails, state);
        } else if (cmd.toLowerCase().startsWith("open ")) {
            String name = cmd.substring(5).trim();
            comd = new BrowserCommand(name, cmdDetails, state);
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
        } else if (cmd.toLowerCase().startsWith("break")) {
            comd = new BreakCommand(cmdDetails, state);
        } else if (cmd.toLowerCase().startsWith("continue")) {
            comd = new ContinueCommand(cmdDetails, state);
        } else if (cmd.toLowerCase().startsWith("back")) {
            comd = new BackCommand(cmdDetails, state);
        } else if (cmd.toLowerCase().startsWith("forward")) {
            comd = new ForwardCommand(cmdDetails, state);
        } else if (cmd.toLowerCase().startsWith("refresh")) {
            comd = new RefreshCommand(cmdDetails, state);
        } else if (cmd.toLowerCase().startsWith("close")) {
            comd = new CloseCommand(cmdDetails, state);
        } else if (cmd.toLowerCase().startsWith("maximize")) {
            comd = new MaximizeCommand(cmdDetails, state);
        } else if (cmd.toLowerCase().startsWith("window_set ")) {
            comd = new WindowSetPropertyCommand(cmd.substring(11), cmdDetails, state);
        } else if (cmd.toLowerCase().startsWith("frame ")) {
            comd = new FrameCommand(cmd.substring(6), cmdDetails, state);
        } else if (cmd.toLowerCase().startsWith("tab ") || cmd.toLowerCase().equals("tab")) {
            comd = new TabCommand(cmd.substring(cmd.toLowerCase().equals("tab")?3:4), cmdDetails, state);
        } else if (cmd.toLowerCase().startsWith("window ") || cmd.toLowerCase().equals("window")) {
            comd = new WindowCommand(cmd.substring(cmd.toLowerCase().equals("window")?6:7), cmdDetails, state);
        } else if (cmd.toLowerCase().startsWith("sleep ")) {
            comd = new SleepCommand(cmd.substring(6), cmdDetails, state);
        } else if (cmd.toLowerCase().startsWith("type ") || cmd.toLowerCase().startsWith("select ") 
                || cmd.toLowerCase().startsWith("click ") || cmd.toLowerCase().equals("click")
                || cmd.toLowerCase().startsWith("doubleclick ") || cmd.toLowerCase().equals("doubleclick")
                || cmd.toLowerCase().startsWith("dblclick ") || cmd.toLowerCase().equals("dblclick")
                || cmd.toLowerCase().startsWith("hover ") || cmd.toLowerCase().equals("hover")
                || cmd.toLowerCase().startsWith("chord ") || cmd.toLowerCase().startsWith("hoverclick ") 
                || cmd.toLowerCase().startsWith("clear ") || cmd.toLowerCase().equals("clear")
                || cmd.toLowerCase().startsWith("submit ") || cmd.toLowerCase().equals("submit")
                || cmd.toLowerCase().startsWith("randomize ") || cmd.toLowerCase().startsWith("actions ")
                || cmd.toLowerCase().startsWith("robot ") ||  cmd.toLowerCase().equals("scrollup") 
                || cmd.toLowerCase().equals("scrolldown") || cmd.toLowerCase().equals("scrollpageup") 
                || cmd.toLowerCase().equals("scrollpagedown")) {
            comd = handleActions(cmd, null, cmdDetails, state);
        } else if (cmd.toLowerCase().startsWith("var ")) {
            comd = new VarCommand(cmd.substring(4), cmdDetails, state);
        } else if (cmd.toLowerCase().startsWith("jsvar ")) {
            comd = new JsVarCommand(cmd.substring(6), cmdDetails, state);
        } else if (cmd.toLowerCase().startsWith("exec ")) {
            comd = new ExecCommand(cmd.substring(5), cmdDetails, state);
        } else if (cmd.toLowerCase().startsWith("execjs ")) {
            comd = new ExecJsCommand(cmd.substring(7), cmdDetails, state);
        } else if (cmd.toLowerCase().startsWith("subtest ") || cmd.toLowerCase().equals("subtest")) {
            comd = new SubTestCommand(cmd.substring((cmd.toLowerCase().equals("subtest")?7:8)), cmdDetails, state);
        } else if (cmd.toLowerCase().startsWith("require ")) {
            comd = new RequireCommand(cmd.substring(8), cmdDetails, state);
        } else if (cmd.toLowerCase().startsWith("import ")) {
            comd = new ImportCommand(cmd.substring(7), cmdDetails, state);
        } else if (cmd.toLowerCase().startsWith("screenshot ")) {
            comd = new ScreenshotCommand(cmd.substring(11), cmdDetails, state);
        } else if (cmd.toLowerCase().startsWith("ele-screenshot ")) {
            comd = new EleScreenshotCommand(cmd.substring(15), cmdDetails, state);
        } else if(cmd.toLowerCase().equals("alert") || cmd.toLowerCase().startsWith("alert ")) {
            comd = new AlertCommand(cmd.substring((cmd.toLowerCase().equals("alert")?5:6)), cmdDetails, state);
        } else if(cmd.toLowerCase().equals("confirm") || cmd.toLowerCase().startsWith("confirm ")) {
            comd = new ConfirmCommand(cmd.substring((cmd.toLowerCase().equals("confirm")?7:8)), cmdDetails, state);
        } else if (cmd.toLowerCase().startsWith("zoom ") || cmd.toLowerCase().startsWith("pinch ") 
                || cmd.toLowerCase().startsWith("tap ") || cmd.toLowerCase().equals("rotate")
                || cmd.toLowerCase().equals("hidekeypad") || cmd.toLowerCase().startsWith("touch ")
                || cmd.toLowerCase().startsWith("swipe ") || cmd.toLowerCase().equals("shake")) {
            if(cmd.toLowerCase().startsWith("zoom ")) {
                comd = new MZoomCommand(cmd.substring(5), cmdDetails, state);
            } else if(cmd.toLowerCase().startsWith("pinch ")) {
                comd = new MPinchCommand(cmd.substring(6), cmdDetails, state);
            } else if(cmd.toLowerCase().startsWith("tap ")) {
                comd = new MTapCommand(cmd.substring(4), cmdDetails, state);
            } else if(cmd.toLowerCase().equals("rotate")) {
                comd = new MRotateCommand(cmdDetails, state);
            } else if(cmd.toLowerCase().equals("hidekeypad")) {
                comd = new MHideKeyPadCommand(cmdDetails, state);
            } else if(cmd.toLowerCase().startsWith("touch ")) {
                comd = new MTouchActionCommand(cmd.substring(6), cmdDetails, state);
            } else if(cmd.toLowerCase().startsWith("swipe ")) {
                comd = new MSwipeCommand(cmd.substring(6), cmdDetails, state);
            } else if(cmd.toLowerCase().equals("shake")) {
                comd = new MShakeCommand(cmdDetails, state);
            }
        } else if (cmd.trim().isEmpty()) {
            comd = new NoopCommand(cmdDetails, state);
        } else {
            try {
                comd = new PluginCommand(cmd, cmdDetails, state);
            } catch (GatfSelCodeParseError e) {
                comd = new ValueCommand(cmdDetails, state);
                if(cmd.charAt(0)==cmd.charAt(cmd.length()-1)) {
                    if(cmd.charAt(0)=='"' || cmd.charAt(0)=='\'') {
                        cmd = cmd.substring(1, cmd.length()-1);
                    }
                }
                ((ValueCommand)comd).value = state.unsanitize(cmd);
            }
        }
        return comd;
    }

    static Command handleActions(String cmd, FindCommand fcmd, Object[] cmdDetails, CommandState state) {
        Command comd = null;
        if (cmd.toLowerCase().startsWith("type ")) {
            comd = new TypeCommand(cmd.substring(5), cmdDetails, state);
        } else if (cmd.toLowerCase().startsWith("randomize ")) {
            comd = new RandomizeCommand(cmd.substring(10), cmdDetails, state);
        } else if (cmd.toLowerCase().startsWith("chord ")) {
            comd = new ChordCommand(cmd.substring(6), cmdDetails, state);
        } else if (cmd.toLowerCase().startsWith("select ")) {
            comd = new SelectCommand(cmd.substring(7), cmdDetails, state);
        } else if (cmd.toLowerCase().startsWith("click ") || cmd.toLowerCase().equals("click")) {
            comd = new ClickCommand(cmdDetails, state);
            if(!cmd.toLowerCase().equals("click")) {
                ((ClickCommand)comd).cond = new FindCommand(cmd.substring(6), cmdDetails, state);
            }
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
            comd = new RobotCommand(cmd.substring(cmd.toLowerCase().startsWith("robot ")?6:0), fcmd, cmdDetails, state);
        }
        return comd;
    }

    static void get(Command parent, ListIterator<Object[]> iter, CommandState state) throws Exception {
        Command prev = null;
        while(iter.hasNext()) {
            Command tmp = null;
            Object[] o = iter.next();

            try
            {
                tmp = parse(o, state);
            }
            catch (Throwable e)
            {
                throwParseErrorS(o, e);
            }

            boolean isValid = state.started;
            boolean isPlugin = prev instanceof PluginCommand || (prev instanceof VarCommand && ((VarCommand)prev).pcomd!=null&& ((VarCommand)prev).pcomd instanceof PluginCommand);
            if(isPlugin && state.pluginstart && (!(tmp instanceof StartCommand) && !(tmp instanceof EndCommand) 
                    && !(tmp instanceof ValueCommand) && !(tmp instanceof ValueListCommand) && !(tmp instanceof CommentCommand) 
                    && !(tmp instanceof EndCommentCommand))) {
                throwParseErrorS(o, new RuntimeException("Plugins can have only values, valuelists and comments along-with blocks"));
            } else if(tmp instanceof IfCommand || tmp instanceof ElseCommand || tmp instanceof ElseIfCommand 
                    || tmp instanceof ProviderLoopCommand || tmp instanceof SubTestCommand || tmp instanceof ScopedLoopCommand) {
                get(tmp, iter, state);
                isValid = true;
                parent.children.add(tmp);
            } else if(tmp instanceof ValueListCommand) {
                get(tmp, iter, state);
                if(!isValid)isValid = false;
                if(prev instanceof IfCommand || prev instanceof ElseIfCommand || prev instanceof ValidateCommand) {
                    prev.children.add(tmp);
                } else {
                    parent.children.add(tmp);
                }
            } else if(tmp instanceof StartCommand) {
                if(isPlugin) {
                    state.pluginstart = true;
                    if(prev instanceof VarCommand) {
                        prev = ((VarCommand)prev).pcomd;
                        get(prev, iter, state);
                    }
                    get(parent, iter, state);
                } else {
                    get(parent, iter, state);
                }
                if(!isValid)isValid = false;
                return;
            } else if((tmp instanceof CommentCommand && state.commentStart) || tmp instanceof CodeCommand) {
                if(tmp instanceof CommentCommand) {
                    ((CommentCommand)tmp).b.append(((ValueCommand)tmp.children.get(0)).value+"\n");
                }
                get(tmp, iter, state);
                parent.children.add(tmp);
                isValid = true;
            } else if(tmp instanceof EndCommentCommand) {
                if(parent instanceof CommentCommand) {
                    ((CommentCommand)parent).b.append(((EndCommentCommand)tmp).value);
                }
                return;
            } else if(tmp instanceof EndCommand) {
                if(parent instanceof PluginCommand) {
                    ((PluginCommand)parent).reconcile();
                    state.pluginstart = false;
                }
                return;
            } else if(tmp instanceof ImportCommand) {
                File f = new File(state.basePath + SystemUtils.FILE_SEPARATOR + o[3].toString()+((ImportCommand)tmp).name);
                if(state.visitedFiles.contains(f.getAbsolutePath())) {
                    throwParseErrorS(o, new RuntimeException("Possible import script recursion observed"));
                }
                List<String> commands = FileUtils.readLines(f, "UTF-8");
                int cnt = 1;
                String fnm = ((ImportCommand)tmp).name;
                if(fnm.lastIndexOf("\\")!=-1) {
                    fnm = fnm.substring(0, fnm.lastIndexOf("\\")+1);
                } else if(fnm.lastIndexOf("/")!=-1) {
                    fnm = fnm.substring(0, fnm.lastIndexOf("/")+1);
                } else {
                    fnm = ""; 
                }
                for (String c : commands) {
                    iter.add(new Object[]{c, cnt++, ((ImportCommand)tmp).name, fnm, state});
                }
                for (@SuppressWarnings("unused") String c : commands) {
                    iter.previous();
                }
                isValid = true;
            } else {
                if(parent instanceof CodeCommand) {
                    isValid = true;
                    ((CodeCommand)parent).b.append(((ValueCommand)tmp).value+"\n");
                } else if(parent instanceof CommentCommand) {
                    isValid = true;
                    ((CommentCommand)parent).b.append(((ValueCommand)tmp).value+"\n");
                } else if(tmp instanceof CommentCommand) {
                    isValid = true;
                    ((CommentCommand)tmp).b.append(((ValueCommand)tmp.children.get(0)).value);
                    parent.children.add(tmp);
                } else if(tmp instanceof BrowserCommand) {
                    state.started = true;
                    isValid = true;
                    parent.children.add(tmp);
                } else if(tmp instanceof SleepCommand) {
                    isValid = true;
                    parent.children.add(tmp);
                } else if(tmp instanceof CommentCommand) {
                    isValid = true;
                    parent.children.add(tmp);
                } else {
                    if(!isValid)isValid = false;
                    parent.children.add(tmp);
                }
            }
            prev = tmp;
            if(!isValid) {
                throwParseErrorS(o, new RuntimeException("open should be the first execution command in a test script"));
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
                }
            } else if(c instanceof ElseCommand) {
                if(ifc!=null) {
                    ifc.elsecmd = (ElseCommand)c;
                }
            } else {
                ifc = null;
                ncl.add(c);
            }
            mergeIfElses(c);
        }
        cmd.children = ncl;
    }

    static Command getAll(List<String> scmds, String fn, CommandState state) throws Exception {
        state.commentStart = false;
        Command tcmd = new Command(null, state);
        tcmd.name = fn;

        List<Object[]> lio = new ArrayList<Object[]>();
        int cnt = 1;
        for (String s : scmds)
        {
            lio.add(new Object[]{s, cnt++, fn, "", state});
        }

        get(tcmd, lio.listIterator(), state);
        mergeIfElses(tcmd);

        Collections.sort(tcmd.children, new Comparator<Command>() {
            public int compare(Command o1, Command o2) {
                return Integer.valueOf(o1.weight()).compareTo(o2.weight());
            }
        });

        return tcmd;
    }

    static Command read(String filename) throws Exception {
        List<String> commands = FileUtils.readLines(new File(filename), "UTF-8");
        CommandState state = new CommandState();
        state.basePath = new File(filename).getParent();
        state.visitedFiles.add(new File(filename).getAbsolutePath());
        Command cmd = Command.getAll(commands, filename, state);
        return cmd;
    }

    static Command read(File file, List<String> commands, AcceptanceTestContext context) throws Exception {
        if(commands==null) {
            commands = new ArrayList<String>();
        } else {
            commands.clear();
        }
        commands.addAll(FileUtils.readLines(file, "UTF-8"));
        CommandState state = new CommandState();
        state.basePath = context.getGatfExecutorConfig().getTestCasesBasePath();
        if(context.getGatfExecutorConfig().getTestCaseDir()!=null) {
            state.basePath += SystemUtils.FILE_SEPARATOR + context.getGatfExecutorConfig().getTestCaseDir();
        }
        loadPlugins(state, context);
        state.visitedFiles.add(file.getAbsolutePath());
        Command cmd = Command.getAll(commands, file.getName(), state);
        cmd.mp = context.getGatfExecutorConfig().getSelDriverConfigMap();
        return cmd;
    }

    static void loadPlugins(CommandState state, AcceptanceTestContext cntxt) throws Exception {
        if(cntxt.getResourceFile("plugins.txt").exists()) {
            List<String> lines = FileUtils.readLines(cntxt.getResourceFile("plugins.txt"), "UTF-8");
            for (String plg : lines) {
                if(plg.indexOf("=")!=-1) {
                    String name = plg.substring(0, plg.indexOf("="));
                    String signature = plg.substring(plg.indexOf("=")+1);
                    if(!name.trim().isEmpty() && !state.plugins.containsKey(name.toLowerCase())) {
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
                            state.plugins.put(name.toLowerCase().trim(), clsname+"@"+method);
                        } catch (Exception e) {
                            try {
                                Class<?> cls = Class.forName(clsname);
                                Method meth = cls.getMethod(method, new Class[]{String.class, Object[].class});
                                if(meth.getReturnType().equals(Void.class)) {
                                    System.out.println("Invalid Plugin specified, Plugin class method should have either of the following signatures - "
                                            + "public static Object execute(Object[] args) | public static Object execute(String name, Object[] args) - " + plg);
                                    continue;
                                }
                                state.plugins.put(name.toLowerCase().trim(), clsname+"@"+method);
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
                            System.out.println("Plugin already defined"); 
                        }
                    }
                }
            }
        }
    }

    public String toSampleSelCmd() {
        return "";
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
        b.append("import com.gatf.selenium.SeleniumTest.SeleniumTestResult;\n");
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
        b.append("import org.openqa.selenium.Keys;\n\n");
        b.append("public class "+className+" extends SeleniumTest implements Serializable {\n");
        b.append("public "+className+"(AcceptanceTestContext ___cxt___, int index) {\nsuper(\""+esc(name)+"\", ___cxt___, index);\n}\n");
        b.append("public void close() {\nif(get___d___()!=null)get___d___().close();\n}\n");
        b.append("public SeleniumTest copy(AcceptanceTestContext ctx, int index) {\nreturn new "+className+"(ctx, index);}\n");
        for (SeleniumDriverConfig driverConfig : mp.values())
        {
            b.append("public void setupDriver"+driverConfig.getName().toLowerCase().replaceAll("[^0-9A-Za-z]+", "")+"(LoggingPreferences ___lp___) throws Exception {\n");
            DriverCommand cmd = new DriverCommand(driverConfig, new Object[]{}, state);
            String cc = cmd.javacode();
            b.append(cc);
            if(!cc.isEmpty()) {
                b.append("\n");
            }
            b.append("}\n"); 
        }
        List<String> bn = new ArrayList<String>();
        List<String> stn = new ArrayList<String>();
        for (Command c : children) {
            if(c instanceof RequireCommand) {
                //TODO
            } else if(c instanceof BrowserCommand) {
                if(mp.containsKey(c.name)) {
                    bn.add(c.name.toLowerCase());
                } else {
                    throw new RuntimeException("Driver configuration not found for " + c.name);
                }
            } else if(c instanceof SubTestCommand) {
                stn.add(c.name);
            }
        }
        b.append("@SuppressWarnings(\"unchecked\")\npublic Map<String, SeleniumResult> execute(LoggingPreferences ___lp___) throws Exception {\n");
        for (String brn : bn)
        {
            b.append("addTest(\""+esc(brn)+"\");\n");
            for (String st : stn)
            {
                b.append("addSubTest(\""+esc(brn)+"\", \""+esc(st)+"\");\n");
            }
        }
        b.append("startTest();\n");
        for (String brn : bn)
        {
            b.append("quit();\n");
            b.append("setupDriver"+brn.replaceAll("[^0-9A-Za-z]+", "")+"(___lp___);\n");
            b.append("_execute(___lp___);\n");
        }
        b.append("return get__result__();\n}\n");
        b.append("public int concurrentExecutionNum() {\n");
        b.append("return " + state.concExec+";\n");
        b.append("}\n");
        b.append("@SuppressWarnings(\"unchecked\")\npublic void _execute(LoggingPreferences ___lp___) throws Exception {\n");
        b.append("try {\n");
        state.pushSc();
        b.append("SearchContext "+state.currvarnamesc()+" = get___d___();\n");
        b.append("WebDriver ___cw___ = get___d___();\n");
        b.append("WebDriver ___ocw___ = ___cw___;\n");
        b.append("List<WebElement> ___ce___ = null;\n");
        for (Command c : children) {
            if((c instanceof RequireCommand) || (c instanceof BrowserCommand)) {
                continue;
            }
            String cc = c.javacode();
            b.append(cc);
            if(!cc.isEmpty()) {
                b.append("\n");
            }
        }
        b.append("pushResult(new SeleniumTestResult(get___d___(), this, ___lp___));\n");
        String ex = state.evarname();
        b.append("}\ncatch(Throwable "+ex+")\n{\npushResult(new SeleniumTestResult(get___d___(), this, "+ex+", ___lp___));\n}");
        b.append("}\n");
        for (Command c : state.allSubTests) {
            if(c instanceof SubTestCommand) {
                SubTestCommand st = (SubTestCommand)c;
                b.append(st.javacodeint());
            }
        }
        b.append("}\n");
        return b.toString();
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
            code = code.replace("@sc", state.currvarnamesc());
            code = code.replace("@index", state.currvarnameitr()!=null?state.currvarnameitr():"@index");
            code = code.replace("@printProvJson", "___cxt___print_provider__json");
            code = code.replace("@printProv", "___cxt___print_provider__");
            code = code.replace("@print", "System.out.println");
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
        public String toSampleSelCmd() {
            return "exec {statement}";
        }
    }

    public static class CommentCommand extends Command {
        StringBuilder b = new StringBuilder();
        boolean isML = false;
        String javacode() {
            return b.toString() + (isML?"*/":"");
        }
        CommentCommand(boolean isML, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            this.isML = isML;
            if(!isML) {
                b.append("//");
            } else {
                b.append("/*");
            }
        }
    }

    public static class CodeCommand extends Command {
        static Pattern p = Pattern.compile("\\$([v|V]*)\\{([a-zA-Z0-9_]+)\\}");
        static Pattern p1 = Pattern.compile("@cntxtParam\\([a-zA-Z0-9_]+\\)");
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
        String javacode() {
            String code = b.toString();
            if(lang.equals("java")) {
                code = code.replace("@driver", "___cw___");
                code = code.replace("@window", "___ocw___");
                code = code.replace("@element", "___ce___.get(0)");
                code = code.replace("@sc", state.currvarnamesc());
                code = code.replace("@index", state.currvarnameitr()!=null?state.currvarnameitr():"@index");
                code = code.replace("@printProvJson", "___cxt___print_provider__json");
                code = code.replace("@printProv", "___cxt___print_provider__");
                code = code.replace("@print", "System.out.println");
                code = code.replaceAll("@cntxtParam\\(([a-zA-Z0-9_]+)\\)", "___cxt___add_param__(\"$1\", $1);");
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
                gcode += "__gs.evaluate(\""+esc(state.unsanitize(code.replaceAll("\n", "\\\\n")))+"\");\n";
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
                    Matcher m = p.matcher(arg);
                    if(m.find()) {
                        args += "getProviderDataValue(\""+esc(m.group(2))+"\", "+(m.group(1).toLowerCase().trim().equals("v")?"true":"false")+"),";
                    } else {
                        arg = arg.replace("@element", "___ce___.get(0)");
                        arg = arg.replace("@index", state.currvarnameitr()!=null?state.currvarnameitr():"@index");
                        if(!arg.trim().isEmpty()) {
                            args += arg +",";
                        }
                    }
                }
                jscode += "if (___ocw___ instanceof JavascriptExecutor) {\n";
                jscode += "((JavascriptExecutor)___ocw___).executeScript(\""+esc(state.unsanitize(code.replaceAll("\n", "\\\\n")))+"\"";
                if(!args.isEmpty()) {
                    args = args.substring(0, args.length()-1);
                    jscode += ", "+args;
                }
                jscode += ");\n}\n";
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
                rcode += "__rs.runScriptlet(\""+esc(state.unsanitize(code.replaceAll("\n", "\\\\n")))+"\");\n";
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
                        pcode += "pi.set(\""+vn+"\", getProviderDataValueO(\""+esc(m.group(1))+"\", "+(m.group(2).toLowerCase().trim().equals("v")?"true":"false")+"));\n";
                    } else {
                        pcode += "pi.set(\""+vn+"\", "+arg+");\n";
                    }
                }
                pcode += "pi.exec(\""+esc(state.unsanitize(code.replaceAll("\n", "\\\\n")))+"\");\n";
                for (String s : ms)
                {
                    pcode += s.replaceAll("@cntxtParam\\(([a-zA-Z0-9_]+)\\)", "___cxt___add_param__(\"$1\", __rs.get(\"$1\").toString());\n");
                }
                return pcode;
            }
            return "";
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
            return "if (___ocw___ instanceof JavascriptExecutor) {\n((JavascriptExecutor)___ocw___).executeScript(evaluate(\""+esc(code)+"\"));\n}";
        }
        public String toSampleSelCmd() {
            return "execjs {jsstatement}";
        }
    }

    public static class SubTestCommand extends Command {
        String fName = "__st__" + state.NUMBER_ST++;
        SubTestCommand(String name, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            name = state.unsanitize(name);
            if(name.length()>0 && name.charAt(0)==name.charAt(name.length()-1)) {
                if(name.charAt(0)=='"' || name.charAt(0)=='\'') {
                    name = name.substring(1, name.length()-1);
                }
            } else {
                name = "Subtest " + (state.NUMBER_ST - 1);
            }
            this.name = name;
            state.allSubTests.add(this);
        }
        String toCmd() {
            StringBuilder b = new StringBuilder();
            b.append("subtest");
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
            return "___ce___ = " + fName+"(___cw___, ___ocw___, "+state.currvarnamesc()+", ___lp___);\n";
        }
        String javacodeint() {
            StringBuilder b = new StringBuilder();
            b.append("List<WebElement> " + fName + "(WebDriver ___cw___, WebDriver ___ocw___, SearchContext "+state.currvarnamesc()+", LoggingPreferences ___lp___) {\n");
            b.append("List<WebElement> ___ce___ = null;\n");
            if(!children.isEmpty())
            {
                b.append("\nset__subtestname__(\""+esc(name)+"\");");
                b.append("\ntry {\n");
                for (Command c : children) {
                    b.append(c.javacode());
                    b.append("\n");
                }
                b.append("\npushResult(new SeleniumTestResult(get___d___(), this, ___lp___));");
                String ex = state.evarname();
                b.append("\n}\ncatch(Throwable "+ex+")\n{\npushResult(new SeleniumTestResult(get___d___(), this, "+ex+", ___lp___));");
                b.append("\n}\nfinally {\nset__subtestname__(null);\n}");
            }
            b.append("return ___ce___;\n}\n");
            return b.toString();
        }
        public String toSampleSelCmd() {
            return "";
        }
    }

    public static class VarCommand extends FindCommandImpl {
        String name;
        boolean isCntxtVar = false;
        PluginCommand pcomd;
        String val = "";
        VarCommand(String val, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            if(val.indexOf(" ")!=-1) {
                name = val.substring(0, val.indexOf(" ")).trim();
                if(name.startsWith("@")) {
                    isCntxtVar = true;
                    name = name.substring(1);
                }
                val = val.substring(val.indexOf(" ")+1).trim();
                if(val.indexOf("@")!=-1) {
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
                return cond.javacodeonly(null) + "\nList<WebElement> " + name + " = " + state.currvarname() + ";" + (isCntxtVar?"\n___cxt___add_param__(\""+name+"\", "+name+");":"");
            } else if(pcomd!=null) {
                return "\nObject " + name + " = null;\n"+pcomd.javacodev(name)+";" + (isCntxtVar?"\n___cxt___add_param__(\""+name+"\", "+name+");":"");
            } else {
                return "\nString " + name + " = "+val+";\n" + (isCntxtVar?"\n___cxt___add_param__(\""+name+"\", "+name+");":"");
            }
        }
        public String toSampleSelCmd() {
            return "var {name} {vale-expr}";
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
        public String toSampleSelCmd() {
            return "jsvar {name} {script-expr}";
        }
    }

    public static class ScreenshotCommand extends Command {
        String fpath;
        ScreenshotCommand(String code, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            code = state.unsanitize(code);
            if(code.charAt(0)==code.charAt(code.length()-1)) {
                if(code.charAt(0)=='"' || code.charAt(0)=='\'') {
                    code = code.substring(1, code.length()-1);
                }
            }
            this.fpath = code.trim().isEmpty()?System.nanoTime()+".jpg":code;
        }
        String toCmd() {
            return "screenshot \"" + fpath + "\"";
        }
        String javacode() {
            String sc = state.varnamesr();
            return "if(get___d___() instanceof io.appium.java_client.AppiumDriver){"
            + "File "+sc+" = ((TakesScreenshot)new org.openqa.selenium.remote.Augmenter().augment(get___d___())).getScreenshotAs(OutputType.FILE);"
            + "FileUtils.copyFile("+sc+", new File(\""+esc(fpath)+"\"));}\n"
            + "else{File "+sc+" = ((TakesScreenshot)___ocw___).getScreenshotAs(OutputType.FILE);"
            + "\nFileUtils.copyFile("+sc+", new File(\""+esc(fpath)+"\"));}";
        }
        public String toSampleSelCmd() {
            return "screenshot {target-path}";
        }
    }

    public static class EleScreenshotCommand extends FindCommandImpl {
        String fpath;
        EleScreenshotCommand(String val, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            String[] parts = val.split("[\t ]+");
            if(parts.length==1) {
                fpath = System.nanoTime()+".jpg";
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
        }
        String toCmd() {
            return "ele-screenshot \"" + fpath + "\"";
        }
        String javacode() {
            StringBuilder b = new StringBuilder();
            b.append(cond.javacodeonly(children));
            b.append("\nif("+cond.condition()+")");
            b.append("\n{");
            b.append("\nWebElement ele = " +state.currvarname() + ".get(0);");
            b.append("\nFile sc = ((TakesScreenshot)___ocw___).getScreenshotAs(OutputType.FILE);");
            b.append("\nBufferedImage fi = ImageIO.read(sc);");
            b.append("\nPoint point = ele.getLocation();");
            b.append("\nint ew = ele.getSize().getWidth();");
            b.append("\nint eh = ele.getSize().getHeight();");
            b.append("\nBufferedImage esc = fi.getSubimage(point.getX(), point.getY(), ew, eh);");
            b.append("\nImageIO.write(esc, \"png\", sc);");
            b.append("\nFileUtils.copyFile(sc, new File(\""+esc(fpath)+"\"));");
            b.append("\n}");
            return b.toString();
        }
        public String toSampleSelCmd() {
            return "ele-screenshot {target-path}";
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
        public String toSampleSelCmd() {
            return "{value}";
        }
        ValueCommand(Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
        }
    }

    public static class ValueListCommand extends StartCommand {
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
                s.add(((ValueCommand)c).value);
            }
            return s;
        }
        public String toSampleSelCmd() {
            return "[{value1},...,{valueN}]";
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
        public String toSampleSelCmd() {
            return "require [{classname1},..{classnameN}]";
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
        public String toSampleSelCmd() {
            return "import {classname}";
        }
    }

    public static class ValidateCommand extends FindCommandImpl {
        ValidateCommand(String time, String val, Object[] lineNumber, CommandState state) {
            super(lineNumber, state);
            String[] parts = val.trim().split("[\t ]+");
            String cmd = parts[0];
            if(parts.length>1) {
                if (parts[1].toLowerCase().startsWith("type") || (parts[1].toLowerCase().startsWith("select") && !parts[1].toLowerCase().startsWith("selected")) 
                        || parts[1].toLowerCase().equals("click") || parts[1].toLowerCase().equals("hover")
                        || parts[1].toLowerCase().startsWith("hoverclick") || parts[1].toLowerCase().equals("clear")
                        || parts[1].toLowerCase().equals("submit") || parts[1].toLowerCase().equals("actions")
                        || parts[1].toLowerCase().equals("chord ")) {
                    cmd = "";
                    for (int i = 1; i < parts.length; i++)
                    {
                        cmd += parts[i] + " ";
                    }
                    cond = time.equals("0")?new FindCommand(parts[0], lineNumber, state):new WaitAndFindCommand(parts[0], Long.valueOf(time), lineNumber, state);
                    cmd = cmd.trim();
                    if(!cmd.isEmpty()) {
                        if (cmd.toLowerCase().startsWith("type ") || cmd.toLowerCase().startsWith("select ") 
                                || cmd.toLowerCase().equals("click") || cmd.toLowerCase().equals("hover")
                                || cmd.toLowerCase().startsWith("hoverclick") || cmd.toLowerCase().equals("clear")
                                || cmd.toLowerCase().equals("submit") || cmd.toLowerCase().startsWith("actions")
                                || cmd.toLowerCase().equals("chord ")) {
                            //cmd = unsanitize(cmd);
                            Command comd = handleActions(cmd, cond, lineNumber, state);
                            children.add(comd);
                        }
                    }
                } else {
                    cmd = val;
                    cond = time.equals("0")?new FindCommand(cmd, lineNumber, state):new WaitAndFindCommand(cmd, Long.valueOf(time), lineNumber, state);
                }
            } else {
                cmd = val;
                cond = time.equals("0")?new FindCommand(cmd, lineNumber, state):new WaitAndFindCommand(cmd, Long.valueOf(time), lineNumber, state);
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
        public String toSampleSelCmd() {
            return "??[:time-in-sec] {find-expr} {optional action(type|hover|hoverclick|click|clear|submit)}";
        }
    }

    public static class FindCommandImpl extends Command {
        FindCommand cond;
        FindCommandImpl(Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
        }
    }

    public static class IfCommand extends FindCommandImpl {
        List<ElseIfCommand> elseifs = new ArrayList<ElseIfCommand>();
        ElseCommand elsecmd;
        boolean negation;
        IfCommand(boolean negation, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            this.negation = negation;
        }
        String toCmd() {
            StringBuilder b = new StringBuilder();
            b.append("?");
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
        static String getFp(FindCommand cond, List<Command> children, boolean negation, CommandState state, String vrd) {
            StringBuilder b = new StringBuilder();
            String ex = state.evarname();
            b.append("try{\n");
            if(cond!=null) {
                b.append(cond.javacodeonly(children));
            }
            if(!negation)
            {
                if(!children.isEmpty())
                {
                    b.append("\n");
                    for (Command c : children) {
                        b.append(c.javacode());
                        b.append("\n");
                    }
                }
            }
            b.append("\n"+vrd+"=true;\n");
            b.append("\n}\ncatch(AssertionError "+state.evarname()+"){");
            if(negation)
            {
                if(!children.isEmpty())
                {
                    b.append("\n");
                    for (Command c : children) {
                        b.append(c.javacode());
                        b.append("\n");
                    }
                }
                b.append("\n"+vrd+"=true;\n");
            }
            else
            {
                b.append("\n"+vrd+"=false;\n");
            }
            b.append("}\ncatch(Exception "+ex+"){\nSystem.out.println("+ex+".getMessage());}\n");
            return b.toString();
        }
        String javacode() {
            StringBuilder b = new StringBuilder();
            state.pushifcnt();
            String vrd = state.currvarnameifcnt();
            b.append("boolean "+vrd+" = false;");
            b.append(getFp(cond, children, negation, state, vrd));
            if(elseifs.size()>0)
            {
                for (int i=0;i<elseifs.size();i++) {
                    b.append("if(!"+vrd+"){");
                    b.append(elseifs.get(i).javacode());
                    b.append("}");
                }
            }
            if(elsecmd!=null) {
                b.append("if(!"+vrd+"){");
                b.append(elsecmd.javacode()+"\n");
                b.append("}");
            }
            state.prevvarnameifcnt();
            return b.toString();
        }
        public String toSampleSelCmd() {
            return "? {find-expr}\n\t{\n\t}";
        }
    }

    public static class ElseIfCommand extends Command {
        FindCommand cond;
        boolean negation;
        ElseIfCommand(boolean negation, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            this.negation = negation;
        }
        String toCmd() {
            StringBuilder b = new StringBuilder();
            b.append(":?");
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
            String vrd = state.currvarnameifcnt();
            b.append(IfCommand.getFp(cond, children, negation, state, vrd));
            return b.toString();
        }
        public String toSampleSelCmd() {
            return ":? {find-expr}\n\t{\n\t}";
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
                    b.append(c.javacode());
                    b.append("\n");
                }
            }
            return b.toString();
        }
        public String toSampleSelCmd() {
            return ": {find-expr}\n\t{\n\t}";
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
        ScopedLoopCommand(Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
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
                b.append(cond.javacodeonly(null));
                String cvarname = state.currvarname();
                //pushSc();
                state.pushParc();
                state.pushItr();
                state.loopCounter++;
                b.append("\nif("+cond.condition()+") {\n");
                b.append("\nint "+state.currvarnameitr() + " = 0;");
                b.append("\nfor(final WebElement " + state.varname() + " : " + cvarname + ") {\n");
                b.append("final SearchContext "+state.currvarnameparc()+" = "+state.currvarname()+";");
                String vr = state.currvarname();
                b.append("\n@SuppressWarnings(\"serial\")\nList<WebElement> "+ state.varname()+" = new java.util.ArrayList<WebElement>(){{add("+vr+");}};");
                for (Command c : children) {
                    b.append(c.javacode());
                    b.append("\n");
                }
                b.append(state.currvarnameitr()+"++;\n}\n}");
                //prevvarnamesc();
                state.prevvarnameparc();
                state.prevvarnameitr();
                state.loopCounter--;
            }
            return b.toString();
        }
        public String toSampleSelCmd() {
            return "## {find-expr}\n\t{\n\t}";
        }
    }

    public static class ProviderLoopCommand extends Command {
        String name;
        int index = -1;
        ProviderLoopCommand(String val, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            String[] parts = val.trim().split("[\t ]+");
            name = state.unsanitize(parts[0].trim());
            if(name.charAt(0)==name.charAt(name.length()-1)) {
                if(name.charAt(0)=='"' || name.charAt(0)=='\'') {
                    name = name.substring(1, name.length()-1);
                }
            }
            if(parts.length>1 && !parts[1].trim().isEmpty()) {
                try
                {
                    index = Integer.valueOf(parts[1].trim());
                }
                catch (Exception e)
                {
                    throw new AssertionError("Provider index should be a number");
                }
            }
        }
        String toCmd() {
            StringBuilder b = new StringBuilder();
            b.append("#provider ");
            b.append(name);
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
            if(index>=0)
            {
                b.append("set__provname__(\"" + name + "\");\n");
                b.append("set__provpos__(\"" + name + "\", " + index + ");\n{\n");
                for (Command c : children) {
                    b.append(c.javacode());
                    b.append("\n");
                }
                b.append("}");
                b.append("rem__provname__(\"" + name + "\");\n");
            }
            else if(!children.isEmpty())
            {
                state.loopCounter++;
                b.append("int "+state.varname()+" = getProviderTestDataMap(\""+name+"\").size();\n");
                b.append("set__provname__(\"" + name + "\");\n");
                String provname = state.currvarname();
                String loopname = state.varname();
                b.append("\nfor(int " + loopname + "=0;"+loopname+"<" + provname + ";"+loopname+"++) {\n");
                b.append("set__provpos__(\"" + name + "\", " + loopname + ");\n");
                for (Command c : children) {
                    b.append(c.javacode());
                    b.append("\n");
                }
                b.append("}");
                b.append("rem__provname__(\"" + name + "\");\n");
                state.loopCounter--;
            }
            return b.toString();
        }
        public String toSampleSelCmd() {
            return "#provider {find-expr}\n\t{\n\t}";
        }
    }

    public static class TransientProviderLoopCommand extends Command {
        FindCommand cond;
        String varname, value;
        boolean isSuiteLevel;
        TransientProviderLoopCommand(String val, Object[] cmdDetails, CommandState state, boolean isSuiteLevel) {
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
            } else {
                //excep
            }
        }
        String toCmd() {
            StringBuilder b = new StringBuilder();
            b.append(isSuiteLevel?"#transient-suite-provider ":"#transient-provider ");
            b.append(name);
            b.append(cond.toCmd());
            return b.toString();
        }
        String javacode() {
            StringBuilder b = new StringBuilder();
            b.append("newProvider(\""+value+"\");\n");
            b.append(cond.javacodeonly(children));
            String provname = cond.rtl;
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
            return b.toString();
        }
        public String toSampleSelCmd() {
            return "#transient-provider {find-expr} {sub-selector}";
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
        String javacode() {
            StringBuilder b = new StringBuilder();
            if(config.getName().equalsIgnoreCase("chrome")) {
                b.append("org.openqa.selenium.chrome.ChromeOptions ___dc___ = new org.openqa.selenium.chrome.ChromeOptions();\n");
                b.append("___dc___.setCapability(CapabilityType.LOGGING_PREFS, ___lp___);\n");
                if(config.getCapabilities()!=null)
                {
                    for (Map.Entry<String, String> e : config.getCapabilities().entrySet())
                    {
                        b.append("___dc___.setCapability(\""+esc(e.getKey())+"\", \""+esc(e.getValue())+"\");\n");
                    }
                }
                b.append("set___d___(new org.openqa.selenium.chrome.ChromeDriver(___dc___));\n");
            } else if(config.getName().equalsIgnoreCase("firefox")) {
                b.append("DesiredCapabilities ___dc___ = DesiredCapabilities."+config.getName().toLowerCase()+"();\n");
                b.append("___dc___.setCapability(CapabilityType.LOGGING_PREFS, ___lp___);\n");
                if(config.getCapabilities()!=null)
                {
                    for (Map.Entry<String, String> e : config.getCapabilities().entrySet())
                    {
                        b.append("___dc___.setCapability(\""+esc(e.getKey())+"\", \""+esc(e.getValue())+"\");\n");
                    }
                }
                if(config.getVersion()!=null) {
                    try
                    {
                        double ver = Double.valueOf(config.getVersion());
                        if(ver<=46) {
                            b.append("set___d___(new org.openqa.selenium.firefox.FirefoxDriver(___dc___));\n");
                        } else {
                            b.append("set___d___(new org.openqa.selenium.firefox.MarionetteDriver(___dc___));\n");
                        }
                    }
                    catch (Exception e)
                    {
                        b.append("set___d___(new org.openqa.selenium.firefox.FirefoxDriver(___dc___));\n");
                    }
                } else {
                    b.append("set___d___(new org.openqa.selenium.firefox.FirefoxDriver(___dc___));\n");
                }
            } else if(config.getName().equalsIgnoreCase("ie")) {
                b.append("DesiredCapabilities ___dc___ = DesiredCapabilities.internetExplorer();\n");
                b.append("___dc___.setCapability(CapabilityType.LOGGING_PREFS, ___lp___);\n");
                if(config.getCapabilities()!=null)
                {
                    for (Map.Entry<String, String> e : config.getCapabilities().entrySet())
                    {
                        b.append("___dc___.setCapability(\""+esc(e.getKey())+"\", \""+esc(e.getValue())+"\");\n");
                    }
                }
                b.append("set___d___(new org.openqa.selenium.ie.InternetExplorerDriver(___dc___));\n");
            } else if(config.getName().equalsIgnoreCase("safari")) {
                b.append("DesiredCapabilities ___dc___ = DesiredCapabilities.safari();\n");
                b.append("___dc___.setCapability(CapabilityType.LOGGING_PREFS, ___lp___);\n");
                if(config.getCapabilities()!=null)
                {
                    for (Map.Entry<String, String> e : config.getCapabilities().entrySet())
                    {
                        b.append("___dc___.setCapability(\""+esc(e.getKey())+"\", \""+esc(e.getValue())+"\");\n");
                    }
                }
                b.append("set___d___(new org.openqa.selenium.safari.SafariDriver(___dc___));\n");
            } else if(config.getName().equalsIgnoreCase("opera")) {
                b.append("DesiredCapabilities ___dc___ = DesiredCapabilities.operaBlink();\n");
                b.append("___dc___.setCapability(CapabilityType.LOGGING_PREFS, ___lp___);\n");
                if(config.getCapabilities()!=null)
                {
                    for (Map.Entry<String, String> e : config.getCapabilities().entrySet())
                    {
                        b.append("___dc___.setCapability(\""+esc(e.getKey())+"\", \""+esc(e.getValue())+"\");\n");
                    }
                }
                b.append("set___d___(new org.openqa.selenium.opera.OperaDriver(___dc___));\n");
            } else if(config.getName().equalsIgnoreCase("edge")) {
                b.append("DesiredCapabilities ___dc___ = DesiredCapabilities."+config.getName().toLowerCase()+"();\n");
                b.append("___dc___.setCapability(CapabilityType.LOGGING_PREFS, ___lp___);\n");
                if(config.getCapabilities()!=null)
                {
                    for (Map.Entry<String, String> e : config.getCapabilities().entrySet())
                    {
                        b.append("___dc___.setCapability(\""+esc(e.getKey())+"\", \""+esc(e.getValue())+"\");\n");
                    }
                }
                b.append("set___d___(new org.openqa.selenium.edge.EdgeDriver(___dc___));\n");
            } else if(config.getName().equalsIgnoreCase("appium-android")) {
                b.append("DesiredCapabilities ___dc___ = new DesiredCapabilities(org.openqa.selenium.remote.BrowserType.ANDROID, \""+config.getVersion()+"\", org.openqa.selenium.Platform.ANDROID);\n");
                b.append("___dc___.setCapability(CapabilityType.LOGGING_PREFS, ___lp___);\n");
                if(config.getCapabilities()!=null)
                {
                    for (Map.Entry<String, String> e : config.getCapabilities().entrySet())
                    {
                        b.append("___dc___.setCapability(\""+esc(e.getKey())+"\", \""+esc(e.getValue())+"\");\n");
                    }
                }
                b.append("set___d___(new io.appium.java_client.android.AndroidDriver(new java.net.URL(\"http://127.0.0.1:4723/wd/hub\"), ___dc___));\n");
            } else if(config.getName().equalsIgnoreCase("appium-ios")) {
                b.append("DesiredCapabilities ___dc___ = new DesiredCapabilities(\"ios\", \""+config.getVersion()+"\", org.openqa.selenium.Platform.MAC);\n");
                b.append("___dc___.setCapability(CapabilityType.LOGGING_PREFS, ___lp___);\n");
                if(config.getCapabilities()!=null)
                {
                    for (Map.Entry<String, String> e : config.getCapabilities().entrySet())
                    {
                        b.append("___dc___.setCapability(\""+esc(e.getKey())+"\", \""+esc(e.getValue())+"\");\n");
                    }
                }
                b.append("set___d___(new io.appium.java_client.ios.IOSDriver(new java.net.URL(\"http://127.0.0.1:4723/wd/hub\"), ___dc___));\n");
            } else if(config.getName().equalsIgnoreCase("selendroid")) {
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
            } else if(config.getName().equalsIgnoreCase("ios-driver")) {
                //b.append("DesiredCapabilities ___dc___ = new DesiredCapabilities(org.openqa.selenium.remote.BrowserType.ANDROID, \""+version+"\", org.openqa.selenium.Platform.ANDROID);\n");
                //b.append("___dc___.setCapability(CapabilityType.LOGGING_PREFS, ___lp___);\n");
                //if(config.getCapabilities()!=null)
                //{
                //    for (Map.Entry<String, String> e : config.getCapabilities().entrySet())
                //    {
                //        b.append("___dc___.setCapability(\""+esc(e.getKey())+"\", \""+esc(e.getValue())+"\");\n");
                //    }
                //}
                //b.append("get___d___() = new org.openqa.selenium.remote.RemoteWebDriver(\"http://127.0.0.1:4723/wd/hub\", ___dc___);\n");
            }
            else {
                throw new RuntimeException("Invalid driver configuration specified, no browser found with name " + config.getName());
            }
            b.append("setBrowserName(\""+config.getName()+"\");\n");
            return b.toString();
        }
        int weight() {
            return 2;
        }
        public String toSampleSelCmd() {
            return "";
        }
    }

    public static class BrowserCommand extends Command {
        BrowserCommand(String val, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            String[] parts = val.trim().split("[\t ]+");
            if(parts.length>=1) {
                name = state.unsanitize(parts[0].trim());
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
        public String toSampleSelCmd() {
            return "open {chrome|firefox|ie|opera|edge|safari|appium-android|appium-ios|selendroid|ios-driver..}";
        }
    }

    public static class FrameCommand extends Command {
        String name;
        FrameCommand(String cmd, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            name = cmd;
            name = state.unsanitize(name);
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
            if(name.equals("") || name.equalsIgnoreCase("main")) {
                return "___cw___ = ___ocw___;\n___sc___1 = ___cw___;";
            } else if(name.equalsIgnoreCase("parent")) {
                return "___cw___ = ___cw___.parentFrame();\n___sc___1 = ___cw___;";
            } else {
                try {
                    int index = Integer.parseInt(name);
                    return "___cw___ = ___ocw___.switchTo().frame("+index+")!=null?___ocw___.switchTo().frame("+index+"):___ocw___.switchTo().frame(\""+esc(name)+"\");\n___sc___1 = ___cw___;";
                } catch (Exception e) {
                    name = state.unsanitize(name);
                    if(name.charAt(0)==name.charAt(name.length()-1)) {
                        if(name.charAt(0)=='"' || name.charAt(0)=='\'') {
                            name = name.substring(1, name.length()-1);
                        }
                    }
                    return "___cw___ = ___ocw___.switchTo().frame(\""+esc(name)+"\");\n___sc___1 = ___cw___;";
                }
            }
        }
        public String toSampleSelCmd() {
            return "frame {name(main|1..N)}";
        }
    }

    public static class WindowCommand extends Command {
        String name;
        WindowCommand(String cmd, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            name = cmd;
            name = state.unsanitize(name);
            if(StringUtils.isNotBlank(name) && name.charAt(0)==name.charAt(name.length()-1)) {
                if(name.charAt(0)=='"' || name.charAt(0)=='\'') {
                    name = name.substring(1, name.length()-1);
                }
            }
        }
        String name() {
            return name;
        }
        String toCmd() {
            return "window " + name;
        }
        String javacode() {
            if(name.equals("")) {
                return "newWindow(___lp___);"; 
            } else if(name.equalsIgnoreCase("main") || name.equalsIgnoreCase("0")) {
                return "window(0);___cw___ = get___d___();\n___sc___1 = ___cw___;";
            } else {
                try {
                    int index = Integer.parseInt(name);
                    return "window("+index+");___cw___ = get___d___();\n___sc___1 = ___cw___;";
                } catch (Exception e) {
                    throwParseError(null, new RuntimeException("Invalid window number specified"));
                    return null;
                }
            }
        }
        public String toSampleSelCmd() {
            return "window {name(main|1..N)}";
        }
    }

    public static class TabCommand extends Command {
        String name;
        TabCommand(String cmd, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            name = cmd;
            name = state.unsanitize(name);
            if(StringUtils.isNotBlank(name) && name.charAt(0)==name.charAt(name.length()-1)) {
                if(name.charAt(0)=='"' || name.charAt(0)=='\'') {
                    name = name.substring(1, name.length()-1);
                }
            }
        }
        String name() {
            return name;
        }
        String toCmd() {
            return "tab " + name;
        }
        String javacode() {
            if(name.trim().isEmpty() || name.equalsIgnoreCase("main") || name.equalsIgnoreCase("0")) {
                return "___ocw___.switchTo().window(___ocw___.getWindowHandles().iterator().next());\n___cw___ = ___ocw___;\n___sc___1 = ___cw___;";
            } else {
                try {
                    int index = Integer.parseInt(name);
                    String acvn = state.varname();
                    String whl = "List<String> "+acvn+" = new java.util.ArrayList<String> (___ocw___.getWindowHandles());\n"
                            + "if("+state.currvarname()+"!=null && "+index+">=0 && "+state.currvarname()+".size()>"+index+")\n{\n";
                    return whl + "___cw___ = ___ocw___.switchTo().window("+acvn+".get("+index+"));\n}\n___sc___1 = ___cw___;";
                } catch (Exception e) {
                    name = state.unsanitize(name);
                    if(name.charAt(0)==name.charAt(name.length()-1)) {
                        if(name.charAt(0)=='"' || name.charAt(0)=='\'') {
                            name = name.substring(1, name.length()-1);
                        }
                    }
                    return "___cw___ = ___ocw___.switchTo().window(\""+esc(name)+"\");\n___sc___1 = ___cw___;";
                }
            }
        }
        public String toSampleSelCmd() {
            return "tab {name(main|1..N)}";
        }
    }

    public static class BackCommand extends Command {
        String toCmd() {
            return "back";
        }
        String javacode() {
            return "___cw___.navigate().back();";
        }
        public String toSampleSelCmd() {
            return "back";
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
        public String toSampleSelCmd() {
            return "forward";
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
            return "___cw___.navigate().refresh();";
        }
        public String toSampleSelCmd() {
            return "refresh";
        }
        RefreshCommand(Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
        }
    }

    public static class CloseCommand extends Command {
        String toCmd() {
            return "close";
        }
        String javacode() {
            return "___cw___.close();";
        }
        public String toSampleSelCmd() {
            return "close";
        }
        CloseCommand(Object[] cmdDetails, CommandState state) {
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
        public String toSampleSelCmd() {
            return "maximize";
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
            String exnm = state.evarname();
            return "try{\n___cw___.navigate().to(evaluate(\""+state.sanitize(url).replace("\\", "\\\\")+"\"));\n} catch (org.openqa.selenium.TimeoutException "+exnm+") {\n___cw___.navigate().refresh();\n}";
        }
        GotoCommand(Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
        }
        public String toSampleSelCmd() {
            return "goto {url}";
        }
    }

    public static class FailCommand extends ValueCommand {
        String toCmd() {
            return "fail \"" + value + "\"";
        }
        String javacode() {
            return "if(true)\n{\nthrow new RuntimeException(\""+esc(value)+"\");\n}";
        }
        FailCommand(String cmd, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            value = state.unsanitize(cmd);
            if(value.charAt(0)==value.charAt(value.length()-1)) {
                if(value.charAt(0)=='"' || value.charAt(0)=='\'') {
                    value = value.substring(1, value.length()-1);
                }
            }
        }
        public String toSampleSelCmd() {
            return "fail {error string}";
        }
    }

    public static class PassCommand extends ValueCommand {
        String toCmd() {
            return "pass \"" + value + "\"";
        }
        String javacode() {
            return "if(true)\n{\nthrow new RuntimeException(\""+esc(value)+"\");\n}";
        }
        PassCommand(String cmd, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            value = state.unsanitize(cmd);
            if(value.charAt(0)==value.charAt(value.length()-1)) {
                if(value.charAt(0)=='"' || value.charAt(0)=='\'') {
                    value = value.substring(1, value.length()-1);
                }
            }
        }
        public String toSampleSelCmd() {
            return "pass {msg string}";
        }
    }

    public static class WindowSetPropertyCommand extends Command {
        String type;
        int value;
        WindowSetPropertyCommand(String val, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            String[] parts = val.trim().split("[\t ]+");
            if(parts.length>1) {
                parts[0] = parts[0].trim();
                type = parts[0];
                value = Integer.valueOf(state.unsanitize(parts[1].trim()));
            } else {
                //excep
            }
        }
        String value() {
            return String.valueOf(value);
        }
        String toCmd() {
            return "window_set "+type+" " + value;
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
            }
            return "";
        }
        public String toSampleSelCmd() {
            return "window_set {width|height|posx|posy} {value}";
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
        public String toSampleSelCmd() {
            return "capability_set {name} {value}";
        }
    }

    static String esc(String cmd) {
        String blid = "######" + UUID.randomUUID().toString() + "######";
        String nlid = "######" + UUID.randomUUID().toString() + "######";
        String tbid = "######" + UUID.randomUUID().toString() + "######";
        String crid = "######" + UUID.randomUUID().toString() + "######";
        String ffid = "######" + UUID.randomUUID().toString() + "######";
        String dqid = "######" + UUID.randomUUID().toString() + "######";
        String tmp = cmd.replace("\\b", blid).replace("\\n", nlid).replace("\\t", tbid)
                .replace("\\r", crid).replace("\\f", ffid).replace("\\\"", dqid);
        tmp = tmp.replace("\\", "\\\\").replace("\"", "\\\"");
        tmp = tmp.replace(blid, "\\b").replace(nlid, "\\n").replace(tbid, "\\t")
                .replace(crid, "\\r").replace(ffid, "\\f").replace(dqid, "\\\"");
        return tmp;
    }

    public static class FindCommand extends Command {
        String by, classifier, subselector, condvar = "true", topele, rtl, precond = "", postcond = "", cfiltvar = null;
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
        public String toSampleSelCmd() {
            return "{id|name|class|xpath|tag|cssselector|css|text|partialLinkText|linkText|active}(@selector) (title|currentUrl|pageSource|width|height|xpos|ypos|alerttext) {value|value-list}";
        }
        FindCommand(String val, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            String[] parts = val.trim().split("[\t ]+");
            if(parts.length>=1) {
                parts[0] = parts[0].trim();
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
                    if(parts.length>2) {
                        String oper = parts[2].trim();
                        oper = state.unsanitize(oper);
                        String cval = oper;
                        if(oper.startsWith("<=")) {
                            cval = oper.substring(2);
                            precond = "compareTo";
                            postcond = "<=0";
                        } else if(oper.startsWith(">=")) {
                            cval = oper.substring(2);
                            precond = "compareTo";
                            postcond = ">=0";
                        } else if(oper.startsWith("=")) {
                            cval = oper.substring(1);
                            precond = "compareTo";
                            postcond = "==0";
                        } else if(oper.startsWith("<")) {
                            cval = oper.substring(1);
                            precond = "compareTo";
                            postcond = "<0";
                        } else if(oper.startsWith(">")) {
                            cval = oper.substring(1);
                            precond = "compareTo";
                            postcond = ">0";
                        } else if(oper.startsWith("!=")) {
                            cval = oper.substring(2);
                            precond = "compareTo";
                            postcond = "!=0";
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
                        ValueCommand vc = new ValueCommand(cmdDetails, state);
                        vc.value = cval;
                        if(vc.value.charAt(0)=='"' || vc.value.charAt(0)=='\'') {
                            vc.value = vc.value.substring(1, vc.value.length()-1);
                        }
                        children.add(vc);
                    }
                    by = state.unsanitize(by);
                } else {
                    by = parts[0];
                    subselector = state.unsanitize(by);
                    by = subselector;
                    byselsame = true;
                    /*if(parts.length>1) {
					    String oper = parts[2].trim();
                        oper = state.unsanitize(oper);
                        String cval = oper;
                        if(oper.startsWith("<=")) {
                            cval = oper.substring(2);
                            precond = "compareTo";
                            postcond = "<=0";
                        } else if(oper.startsWith(">=")) {
                            cval = oper.substring(2);
                            precond = "compareTo";
                            postcond = ">=0";
                        } else if(oper.startsWith("=")) {
                            cval = oper.substring(1);
                            precond = "compareTo";
                            postcond = "==0";
                        } else if(oper.startsWith("<")) {
                            cval = oper.substring(1);
                            precond = "compareTo";
                            postcond = "<0";
                        } else if(oper.startsWith(">")) {
                            cval = oper.substring(1);
                            precond = "compareTo";
                            postcond = ">0";
                        } else if(oper.startsWith("!=")) {
                            cval = oper.substring(2);
                            precond = "compareTo";
                            postcond = "!=0";
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
                        ValueCommand vc = new ValueCommand(cmdDetails, state);
                        vc.value = cval;
                        if(vc.value.charAt(0)=='"' || vc.value.charAt(0)=='\'') {
                            vc.value = vc.value.substring(1, vc.value.length()-1);
                        }
		        		children.add(vc);
					}*/
                }
            } else {
                //excep
            }
        }
        String toCmd() {
            return " " + by + "@\"" + classifier + "\"" + (StringUtils.isEmpty(subselector)?"":" \"" + subselector + "\"");
        }
        String getErr() {
            return "\"Element not found by selector " + by + "@'" + esc(classifier) + "' at line number "+fileLineDetails[1]+" \"";
        }
        String javacodeonly(List<Command> children) {
            try {
                StringBuilder b = new StringBuilder();
                topele = state.varname();
                if(by.equalsIgnoreCase("id")) {
                    b.append("List<WebElement>  " + topele + " = By.id(evaluate(\""+esc(classifier)+"\")).findElements("+state.currvarnamesc()+");");
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
                } else if(!byselsame) {
                    throwParseError(null);
                } else {
                    condvar = state.condvarname();
                    b.append("\nboolean " + condvar + " = true;");
                    b.append("\n" + condvar + " = evaluate(\"#set($__v__R1_O_P2__ = "+esc(by)+")\\n${__v__R1_O_P2__}\").equalsIgnoreCase(\"true\");");
                    return b.toString();
                }

                if(this instanceof WaitAndFindCommand) {
                    b.append("\nif("+topele+"==null || "+topele+".isEmpty())return false;");
                } else {
                    b.append("\nAssert.assertTrue(\"Element not found by selector " + by + "@'" + esc(classifier) 
                    + "' at line number "+fileLineDetails[1]+" \", "+topele+"!=null && !"+topele+".isEmpty());");
                    b.append("___ce___ = "+topele+";");
                }

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
                            || c instanceof SelectCommand || c instanceof ChordCommand || c instanceof DoubleClickCommand) {
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

                /*if(condvar==null) {
    				String cvarname = topele;
    				condvar = state.condvarname();
    				if(subselector!=null && !subselector.isEmpty())
    				{
    					b.append("\nboolean " + condvar + " = true;");
    					b.append("\nfor(final WebElement " + state.varname() + " : " + cvarname + ")\n{");
    					if(subselector.equalsIgnoreCase("selected")) {
    						b.append("\n" + condvar + " &= " + cvarname + ".isSelected();");
    					} else if(subselector.equalsIgnoreCase("enabled")) {
    						b.append("\n" + condvar + " &= " + cvarname + ".isEnabled();");
    					} else if(subselector.equalsIgnoreCase("visible")) {
    						b.append("\n" + condvar + " &= " + cvarname + ".isDisplayed();");
    					} else {
    					    throwParseError(null);
                        }
    					b.append("\n}");
    				}
    				else
    				{
    					//b.append("\nboolean " + condvar + " = "+ cvarname+"!=null && !"+cvarname+".isEmpty();");
    				}
    			}*/
                return b.toString();
            } catch (GatfSelCodeParseError e) {
                throw e;
            } catch (Throwable e) {
                throwParseError(null, e);
            }
            return null;
        }
        String getActionable(String action, String post, String actionsVar) {
            StringBuilder b = new StringBuilder();
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
            return b.toString();
        }
        String getActionableVar() {
            return cfiltvar;
        }
        String condition() {
            return condvar;
        }
        String javacode() {
            return "";
        }
    }

    public static class WaitAndFindCommand extends FindCommand {
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
            String tsc = state.currvarnamesc();
            state.pushSc();
            String wfte = state.varname();
            String ex = state.evarname();
            String v = "final Object[]  " + wfte + " = new Object[2];\n"
                    + "final WebDriver "+state.currvarnamesc()+" = (WebDriver)"+tsc+";\ntry{\n(new WebDriverWait("+state.currvarnamesc()+", "+waitfor+")).until(" +
                    "\nnew Function<WebDriver, Boolean>(){"+
                    "\npublic Boolean apply(WebDriver input) {\n"+
                    "\nList<WebElement> ___ce___ = null;\n" +
                    super.javacodeonly(children) +
                    "\n" + wfte + "[0] = " + topele() + ";\n" +
                    "\n" + wfte + "[1] = ___ce___;\n" +
                    "\nreturn "+super.condition()+";" +
                    "\n}"+
                    "\npublic String toString() {"+
                    "\nreturn \"\";" +
                    "\n}"+
                    "\n});\n} catch(org.openqa.selenium.TimeoutException "+ex+") {\nthrow new RuntimeException("+getErr()+", "+ex+");\n}" +
                    "\n___ce___ = (List<WebElement>)" + wfte + "[1];\n";
            state.prevvarnamesc();
            topele = wfte;
            return v;
        }
    }

    public static class StartCommand extends Command {
        String type;
        StartCommand(Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
        }
        String toCmd() {
            return type;
        }
    }

    public static class EndCommentCommand extends Command {
        String type;
        String value;
        EndCommentCommand(String value, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            this.value = value;
        }
        String toCmd() {
            return type;
        }
        String javacode() {
            return "";
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
    }

    public static class TypeCommand extends FindCommandImpl {
        String value;
        TypeCommand(String val, Object[] cmdDetails, CommandState state) {
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
                if(parts.length>1){
                    cond = new FindCommand(parts[1].trim(), fileLineDetails, state);
                }
            } else {
                //excep
            }
        }
        String toCmd() {
            return "type \"" + value + "\"" + (cond!=null?cond.toCmd():"");
        }
        String javacode() {
            StringBuilder b = new StringBuilder();
            if(cond!=null) {
                b.append(cond.javacodeonly(children));
                b.append(cond.getActionable("sendKeys", "evaluate(\""+esc(value)+"\")", null));
            } else {
                b.append("\n"+state.currvarname()+".get(0).sendKeys(evaluate(\""+esc(value)+"\"));");
            }
            return b.toString();
        }
        String selcode(String varnm) {
            if(varnm==null) {
                varnm = state.currvarname();
            }
            return "\n"+varnm+".get(0).sendKeys(evaluate(\""+esc(value)+"\"));";
        }
        public String toSampleSelCmd() {
            return "type {text}";
        }
    }

    public static class RandomizeCommand extends FindCommandImpl {
        String v1;
        String v2, v3;
        RandomizeCommand(String val, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            String[] parts = val.trim().split("[\t ]+");
            if(parts.length>0) {
                cond = new FindCommand(parts[0].trim(), fileLineDetails, state);
                parts[0] = parts[0].trim();
                if(parts.length>1){
                    v1 = parts[1];
                    v1 = state.unsanitize(v1);
                    if(v1.charAt(0)==v1.charAt(v1.length()-1)) {
                        if(v1.charAt(0)=='"' || v1.charAt(0)=='\'') {
                            v1 = v1.substring(1, v1.length()-1);
                        }
                    }
                }
                if(parts.length>2){
                    v2 = parts[2];
                    v2 = state.unsanitize(v2);
                    if(v2.charAt(0)==v2.charAt(v2.length()-1)) {
                        if(v2.charAt(0)=='"' || v2.charAt(0)=='\'') {
                            v2 = v2.substring(1, v2.length()-1);
                        }
                    }
                }
                if(parts.length>3){
                    v3 = parts[3];
                    v3 = state.unsanitize(v3);
                    if(v3.charAt(0)==v3.charAt(v3.length()-1)) {
                        if(v3.charAt(0)=='"' || v3.charAt(0)=='\'') {
                            v3 = v3.substring(1, v3.length()-1);
                        }
                    }
                }
            } else {
                //excep
            }
        }
        String toCmd() {
            return "randomize \"" + cond.toCmd() + (v1!=null?(" "+v1):"") + (v2!=null?(" "+v2):"") + (v3!=null?(" "+v3):"");
        }
        String javacode() {
            StringBuilder b = new StringBuilder();
            if(cond!=null) {
                b.append(cond.javacodeonly(children));
                b.append("\nrandomize("+cond.getActionableVar()+", \""+(v1!=null?esc(v1):"")+"\", \""+(v2!=null?esc(v2):"")+"\", \""+(v3!=null?esc(v3):"")+"\");");
            } else {
                b.append("\nrandomize("+state.currvarname()+", \""+(v1!=null?esc(v1):"")+"\", \""+(v2!=null?esc(v2):"")+"\", \""+(v3!=null?esc(v3):"")+"\");");
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
            return "\n"+varnm+".get(0).sendKeys(evaluate(\""+esc(v1)+"\"));";
        }
        public String toSampleSelCmd() {
            return "type {text}";
        }
    }

    public static class ChordCommand extends FindCommandImpl {
        List<String> values = new ArrayList<String>();
        String value;
        public ChordCommand(Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
        }
        ChordCommand(String val, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            String[] parts = val.trim().split("[\t ]+");
            if(parts.length>0) {
                parts[0] = state.unsanitize(parts[0].trim());
                value = parts[0];
                if(value.charAt(0)==value.charAt(value.length()-1)) {
                    if(value.charAt(0)=='"' || value.charAt(0)=='\'') {
                        value = value.substring(1, value.length()-1);
                    }
                }
                String temp = org.apache.commons.lang.StringEscapeUtils.unescapeJava(value);
                try
                {
                    if(parts.length>1) {
                        cond = new FindCommand(parts[1], fileLineDetails, state);
                    }
                    for (char c : temp.toCharArray())
                    {
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
                }
                catch (Exception e)
                {
                    // TODO: handle exception
                }
            } else {
                //excep
            }
        }
        String toCmd() {
            return "chord \"" + value + "\"" + (cond!=null?cond.toCmd():"");
        }
        String javacode() {
            StringBuilder b = new StringBuilder();
            if(cond!=null) {
                b.append(cond.javacodeonly(children));
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
                return "\n"+varnm+".get(0).sendKeys(Keys.chord("+chs+"));";
            }
            return "";
        }
        public String toSampleSelCmd() {
            return "chord {utf-8 character1}{utf-8 character2}...{utf-8 characterN}";
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
                if(parts.length>1){
                    cond = new FindCommand(parts[1].trim(), fileLineDetails, state);
                }
            } else {
                //excep
            }
        }
        String toCmd() {
            return "select \"" + by + "\"" + (cond!=null?cond.toCmd():"");
        }
        String javacode() {
            StringBuilder b = new StringBuilder();
            if(cond!=null) {
                b.append(cond.javacodeonly(children));
                //b.append("\nAssert.assertTrue("+cond.condition()+");");
            }
            String cvarnm = cond!=null?cond.getActionableVar():state.currvarname();
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
        public String toSampleSelCmd() {
            return "select {find-expr} {text|index|value|first|last}@{value}";
        }
    }

    public static class HoverCommand extends FindCommandImpl {
        String toCmd() {
            return "hover" + (cond!=null?cond.toCmd():"");
        }
        String javacode() {
            StringBuilder b = new StringBuilder();
            if(cond!=null) {
                b.append(cond.javacodeonly(children));
                //b.append("\nAssert.assertTrue("+cond.condition()+");");o
            }
            String cvarnm = cond!=null?cond.getActionableVar():state.currvarname();
            String hvvrnm = state.varname();
            b.append("\nActions "+hvvrnm+" = new Actions(get___d___());");
            b.append("\n"+hvvrnm+".moveToElement("+cvarnm+".get(0)).perform();");
            return b.toString();
        }
        String selcode(String varnm) {
            if(varnm==null) {
                varnm = state.currvarname();
            }
            String hvvrnm = state.varname();
            StringBuilder b = new StringBuilder();
            b.append("\nActions "+hvvrnm+" = new Actions(get___d___());");
            b.append("\n"+hvvrnm+".moveToElement("+varnm+".get(0)).perform();");
            return b.toString();
        }
        public HoverCommand(Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
        }
        public String toSampleSelCmd() {
            return "hover {find-expr}";
        }
    }

    public static class HoverAndClickCommand extends FindCommandImpl {
        FindCommand cond;//hover element
        WaitAndFindCommand condCe;
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
                condCe = new WaitAndFindCommand(t[1], 10000, fileLineDetails, state);
            }
        }
        String toCmd() {
            return "hoverclick " + (cond!=null?cond.toCmd():"") +  (condCe!=null?(" "+condCe.toCmd()):"");
        }
        String javacode() {
            StringBuilder b = new StringBuilder();
            if(cond!=null) {
                b.append(cond.javacodeonly(children));
                //b.append("\nAssert.assertTrue("+condHe.condition()+");");
            }
            String hevarnm = state.currvarname();
            if(condCe!=null) {
                b.append(condCe.javacodeonly(children));
            }
            String cevarnm = condCe.topele();
            String hvvrnm = state.varname();
            b.append("\nActions "+hvvrnm+" = new Actions(get___d___());");
            b.append("\n"+hvvrnm+".moveToElement("+hevarnm+".get(0)).click(((List<WebElement>)"+cevarnm+"[0]).get(0)).perform();");
            return b.toString();
        }
        String selcode(String varnm) {
            if(varnm==null) {
                varnm = state.currvarname();
            }
            String hvvrnm = state.varname();
            StringBuilder b = new StringBuilder();
            b.append("\nActions "+hvvrnm+" = new Actions(get___d___());");
            b.append("\n"+hvvrnm+".moveToElement("+varnm+".get(0)).click().perform();");
            return b.toString();
        }
        public String toSampleSelCmd() {
            return "hoverclick {find-expr}";
        }
    }

    public static class ActionsCommand extends FindCommandImpl {
        String action;
        String expr1;
        String expr2;

        public ActionsCommand(Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
        }
        ActionsCommand(String val, FindCommand fcmd, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            String[] t = val.trim().split("[\t ]+");
            int tl = t.length, counter = 0;
            ActionsCommand cmd = this;
            this.cond = fcmd;
            while(counter<tl) {
                if(t[counter].toLowerCase().matches("click|clickandhold|release|doubleclick|contextclick|clickhold|rightclick")) {
                    cmd.action = t[counter].toLowerCase();
                    if(cmd.action.equals("clickhold") || cmd.action.equals("clickandhold")) {
                        cmd.action = "clickAndHold";
                    } else if(cmd.action.equals("rightclick") || cmd.action.equals("contextclick")) {
                        cmd.action = "contextClick";
                    } else if(cmd.action.equals("doubleclick") || cmd.action.equals("dblclick")) {
                        cmd.action = "doubleClick";
                    }
                } else if(t[counter].toLowerCase().matches("keydown|keyup|sendkeys|type|movetoelement|moveto")) {
                    cmd.action = t[counter].toLowerCase();
                    if(cmd.action.equals("type") || cmd.action.equals("sendkeys")) {
                        cmd.action = "sendKeys";
                    } else if(cmd.action.equals("moveto") || cmd.action.equals("movetoelement")) {
                        cmd.action = "moveToElement";
                    } else if(cmd.action.equals("keydown")) {
                        cmd.action = "keyDown";
                    } else if(cmd.action.equals("keyup")) {
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
                        throwParseError(null, new RuntimeException("Expression expected after actions (keyDown|keyUp|sendKeys|moveToElement)"));
                    }
                } else if(t[counter].toLowerCase().matches("draganddrop|movebyoffset|moveby|dragdrop")) {
                    cmd.action = t[counter].toLowerCase();
                    if(cmd.action.equals("moveby") || cmd.action.equals("movebyoffset")) {
                        cmd.action = "moveByOffset";
                    } else if(cmd.action.equals("dragdrop") || cmd.action.equals("draganddrop")) {
                        cmd.action = "dragAndDrop";
                    }
                    int fc = 0;
                    for (int i = 1; i < 3; i++)
                    {
                        if(tl<=counter+i)break;
                        if(t[counter+i].toLowerCase().matches("click|clickandhold|release|doubleclick|contextclick|keydown|keyup|sendkeys|movetoelement|draganddrop|movebyoffset")) {
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
                } else {
                    throwParseError(null, new RuntimeException("Invalid action specified, should be one of (click|clickhold|clickAndHold|release|doubleClick|dblclick|contextClick|rightclick|keyDown|keyUp|sendKeys|moveToElement|dragAndDrop|dragdrop|moveByOffset|moveby)"));
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
                b.append("\n"+acnm+"."+action+"();");
            } else if(action.toLowerCase().matches("keydown|keyup")) {
                String ck = state.unsanitize(expr1);
                b.append("\n"+acnm+"."+(action.equalsIgnoreCase("keydown")?"keyDown":"keyUp")+"(Keys.getKeyFromUnicode('"+ck+"'));");
            } else if(action.toLowerCase().matches("sendkeys") || action.toLowerCase().matches("type")) {
                String ck = state.unsanitize(expr1);
                b.append("\n"+acnm+".sendKeys(evaluate(\""+ck+"\"));");
            } else if(action.toLowerCase().matches("movetoelement")) {
                String ck = state.unsanitize(expr1);
                FindCommand fc = new FindCommand(ck, fileLineDetails, state);
                b.append("\n"+fc.javacodeonly(null));
                //b.append("\nAssert.assertTrue("+fc.condition()+");");
                String cvarnm = state.currvarname();
                b.append("\n"+acnm+"."+action+"("+cvarnm+".get(0));");
            } else if(action.toLowerCase().matches("draganddrop")) {
                String ck = state.unsanitize(expr1);
                FindCommand fc = new FindCommand(ck, fileLineDetails, state);
                b.append("\n"+fc.javacodeonly(null));
                //b.append("\nAssert.assertTrue("+fc.condition()+");");
                String cvarnm = state.currvarname();
                String ck1 = state.unsanitize(expr1);
                FindCommand fc1 = new FindCommand(ck1, fileLineDetails, state);
                b.append("\n"+fc1.javacodeonly(null));
                //b.append("\nAssert.assertTrue("+fc1.condition()+");");
                String cvarnm1 = state.currvarname();
                b.append("\n"+acnm+"."+action+"("+cvarnm+".get(0), "+cvarnm1+".get(0));");
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
            b.append("\nActions "+acnm+" = new Actions(get___d___());");
            if(cond!=null) {
                b.append("\n"+acnm+".moveToElement("+cond.topele+".get(0));");
            }
            b.append(_javacode(acnm));
            b.append("\n"+acnm+".build().perform();");
            return b.toString();
        }
    }

    public static class RobotCommand extends FindCommandImpl {
        String action;
        String expr1;

        public RobotCommand(Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
        }
        RobotCommand(String val, FindCommand fcmd, Object[] cmdDetails, CommandState state) {
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
            this.cond = fcmd;
            while(counter<tl) {
                if(t[counter].toLowerCase().matches("keydown|keyup|keypress")) {
                    cmd.action = t[counter].toLowerCase();
                    if(cmd.action.equals("keydown")) {
                        cmd.action = "keyPress";
                    } else if(cmd.action.equals("keyup")) {
                        cmd.action = "keyRelease";
                    } else if(cmd.action.equals("keypress")) {
                        cmd.action = "key";
                    }
                    if(tl>counter+1) {
                        cmd.expr1 = state.unsanitize(t[++counter]);
                        if(cmd.expr1.charAt(0)==cmd.expr1.charAt(cmd.expr1.length()-1)) {
                            if(cmd.expr1.charAt(0)=='"' || cmd.expr1.charAt(0)=='\'') {
                                cmd.expr1 = cmd.expr1.substring(1, cmd.expr1.length()-1);
                            }
                        }
                    } else {
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
            return "robot ";
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
            b.append("\ntry{Thread.sleep(100);}catch(Exception "+state.evarname()+"){}");
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
            return "\ntry{Thread.sleep("+ms+");}catch(Exception "+state.evarname()+"){}";
        }
        public String toSampleSelCmd() {
            return "sleep {time-in-ms}";
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
        public String toSampleSelCmd() {
            return "break";
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
        public String toSampleSelCmd() {
            return "continue";
        }
    }

    public static class ClickCommand extends FindCommandImpl {
        String toCmd() {
            return "click" + (cond!=null?cond.toCmd():"");
        }
        ClickCommand(Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
        }
        String javacode() {
            StringBuilder b = new StringBuilder();
            if(cond!=null) {
                b.append(cond.javacodeonly(children));
                b.append(cond.getActionable("click", null, null));
            } else {
                b.append("\n"+state.currvarname()+".get(0).click();");
            }
            return b.toString();
        }
        String selcode(String varnm) {
            if(varnm==null) {
                varnm = state.currvarname();
            }
            return "\n"+varnm+".get(0).click();";
        }
        public String toSampleSelCmd() {
            return "click {find-expr}";
        }
    }

    public static class DoubleClickCommand extends FindCommandImpl {
        String toCmd() {
            return "doubleclick" + (cond!=null?cond.toCmd():"");
        }
        String javacode() {
            StringBuilder b = new StringBuilder();
            if(cond!=null) {
                b.append(cond.javacodeonly(children));
                //b.append("\nAssert.assertTrue("+cond.condition()+");");
            }
            String hvvrnm = state.varname();
            b.append("\nActions "+hvvrnm+" = new Actions(get___d___());");
            b.append(cond.getActionable("doubleClick", null, hvvrnm));
            return b.toString();
        }
        String selcode(String varnm) {
            if(varnm==null) {
                varnm = state.currvarname();
            }
            String hvvrnm = state.varname();
            StringBuilder b = new StringBuilder();
            b.append("\nActions "+hvvrnm+" = new Actions(get___d___());");
            b.append(cond.getActionable("doubleClick", null, hvvrnm));
            return b.toString();
        }
        public DoubleClickCommand(Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
        }
        public String toSampleSelCmd() {
            return "doubleclick {find-expr}";
        }
    }

    public static class ClearCommand extends FindCommandImpl {
        String toCmd() {
            return "clear" + (cond!=null?cond.toCmd():"");
        }
        String javacode() {
            StringBuilder b = new StringBuilder();
            if(cond!=null) {
                b.append(cond.javacodeonly(children));
                b.append(cond.getActionable("clear", null, null));
            } else {
                b.append("\n"+state.currvarname()+".get(0).clear();");
            }
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
        public String toSampleSelCmd() {
            return "clear {find-expr}";
        }
    }

    public static class SubmitCommand extends FindCommandImpl {
        String toCmd() {
            return "submit" + (cond!=null?cond.toCmd():"");
        }
        String javacode() {
            StringBuilder b = new StringBuilder();
            if(cond!=null) {
                b.append(cond.javacodeonly(children));
                b.append(cond.getActionable("submit", null, null));
            } else {
                b.append("\n"+state.currvarname()+".get(0).submit();");
            }
            return b.toString();
        }
        String selcode(String varnm) {
            if(varnm==null) {
                varnm = state.currvarname();
            }
            return "\n"+varnm+".get(0).submit();";
        }
        public String toSampleSelCmd() {
            return "submit {find-expr}";
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
                String avn = state.alvarname();
                String c = "Alert "+avn+" = get___d___().switchTo().alert();\n";
                c += "\nAssert.assertEquals(\""+esc(value)+"\", "+avn+".getText());\n"+avn+".accept();\n";
                return c;
            } else {
                return "get___d___().switchTo().alert().accept();";
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
        public String toSampleSelCmd() {
            return "alert {?value}";
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
                String avn = state.alvarname();
                String c = "Alert "+avn+" = get___d___().switchTo().alert();\n";
                c += "\nAssert.assertEquals(\""+esc(value)+"\", "+avn+".getText());\n"+avn+"."+(isOk?"accept":"dismiss")+"();\n";
                return c;
            } else {
                return "get___d___().switchTo().alert()."+(isOk?"accept":"dismiss")+"();";
            }
        }
        public ConfirmCommand(String info, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            String[] t = info.trim().split("[\t ]+");
            if(t[0].trim().isEmpty()) {
                t[0] = "ok";
            }
            if(!t[0].toLowerCase().trim().matches("ok|cancel|yes|no")) {
                throwParseError(null, new RuntimeException("confirm dialog can be accepted or dismissed, one of (ok|cancel|yes|no) allowed"));
            }
            if(t[0].toLowerCase().trim().matches("cancel|no")) {
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
        public String toSampleSelCmd() {
            return "confirm [ok|cancel] {?value}";
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
                b.append(cond.javacodeonly(children));
                b.append("\nmzoompinch("+state.currvarname()+".get(0), -1, -1, true);");
            } else {
                b.append("\nmzoompinch(null, "+x+", "+y+", true);");
            }
            return b.toString();
        }
        String selcode(String varnm) {
            if(varnm==null) {
                varnm = state.currvarname();
            }
            return "\nmzoompinch("+varnm+".get(0), -1, -1, true);";
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
        public String toSampleSelCmd() {
            return "zoom ({x} {y}|{find-expr})";
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
                b.append(cond.javacodeonly(children));
                b.append("\nmzoompinch("+state.currvarname()+".get(0), -1, -1, false);");
            } else {
                b.append("\nmzoompinch(null, "+x+", "+y+", false);");
            }
            return b.toString();
        }
        String selcode(String varnm) {
            if(varnm==null) {
                varnm = state.currvarname();
            }
            return "\nmzoompinch("+varnm+".get(0), -1, -1, true);";
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
        public String toSampleSelCmd() {
            return "pinch ({x} {y}|{find-expr})";
        }
    }

    public static class MSwipeCommand extends FindCommandImpl {
        int sx, sy, ex, ey, d;
        String toCmd() {
            return "";
        }
        String javacode() {
            return "\nnew io.appium.java_client.TouchAction((io.appium.java_client.MobileDriver) get___d___()).press("+sx+", "+sy+").waitAction(java.time.Duration.ofMillis("+d+")).moveTo("+ex+", "+ey+").release().perform();";
        }
        public MSwipeCommand(String info, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            String[] t = info.trim().split("[\t ]+");
            if(t.length<5)
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
        public String toSampleSelCmd() {
            return "swipe {sx} {sy} {ex} {ey}";
        }
    }

    public static class MTapCommand extends FindCommandImpl {
        int x = Integer.MAX_VALUE , y = Integer.MAX_VALUE, d = Integer.MAX_VALUE;
        String toCmd() {
            return "";
        }
        String javacode() {
            StringBuilder b = new StringBuilder();
            if(cond!=null) {
                b.append(cond.javacodeonly(children));
                if(d!=Integer.MAX_VALUE) {
                    if(x!=Integer.MAX_VALUE && y!=Integer.MAX_VALUE) {
                        b.append("\nnew io.appium.java_client.TouchAction((io.appium.java_client.MobileDriver) get___d___()).waitAction(java.time.Duration.ofMillis("+d+")).tap("+state.currvarname()+".get(0), "+x+", "+y+").perform();");
                    } else {
                        b.append("\nnew io.appium.java_client.TouchAction((io.appium.java_client.MobileDriver) get___d___()).waitAction(java.time.Duration.ofMillis("+d+")).tap("+state.currvarname()+".get(0)).perform();");
                    }
                } else if(x!=Integer.MAX_VALUE && y!=Integer.MAX_VALUE) {
                    b.append("\nnew io.appium.java_client.TouchAction((io.appium.java_client.MobileDriver) get___d___()).tap("+state.currvarname()+".get(0), "+x+", "+y+").perform();");
                } else {
                    b.append("\nnew io.appium.java_client.TouchAction((io.appium.java_client.MobileDriver) get___d___()).tap("+state.currvarname()+".get(0)).perform();");
                }
            } else if(x!=Integer.MAX_VALUE && y!=Integer.MAX_VALUE) {
                if(d!=Integer.MAX_VALUE) {
                    b.append("\nnew io.appium.java_client.TouchAction((io.appium.java_client.MobileDriver) get___d___()).waitAction(java.time.Duration.ofMillis("+d+")).tap("+x+", "+y+").perform();");
                } else {
                    b.append("\nnew io.appium.java_client.TouchAction((io.appium.java_client.MobileDriver) get___d___()).tap("+x+", "+y+").perform();");
                }
            }
            return b.toString();
        }
        String selcode(String varnm) {
            if(varnm==null) {
                varnm = state.currvarname();
            }
            return "\nnew io.appium.java_client.TouchAction((io.appium.java_client.MobileDriver) get___d___()).tap("+varnm+".get(0)).perform();";
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
                        throwParseError(null, new RuntimeException("tap command needs valid {find-expr}/x/y/d values"));
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
                        throwParseError(null, new RuntimeException("tap command needs valid {find-expr}/x/y/d values"));
                    }
                }
            }
            else
            {
                throwParseError(null, new RuntimeException("tap command needs valid x/y/d values"));
            }
        }
        public String toSampleSelCmd() {
            return "tap ({f} {find-expr} {d}|{f} {x} {y} {d})";
        }
    }

    public static class MRotateCommand extends Command {
        String toCmd() {
            return "";
        }
        String javacode() {
            StringBuilder b = new StringBuilder();
            String vn = state.varname();
            b.append("org.openqa.selenium.ScreenOrientation "+vn+" = ((org.openqa.selenium.Rotatable)get___d___()).getOrientation();\n");
            b.append("if("+vn+".value().equals(org.openqa.selenium.ScreenOrientation.LANDSCAPE.value())){((org.openqa.selenium.Rotatable)get___d___()).rotate(org.openqa.selenium.ScreenOrientation.PORTRAIT);}");
            b.append("else{((org.openqa.selenium.Rotatable)get___d___()).rotate(org.openqa.selenium.ScreenOrientation.LANDSCAPE);}");
            return b.toString();
        }
        public MRotateCommand(Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
        }
        public String toSampleSelCmd() {
            return "rotate";
        }
    }

    public static class MHideKeyPadCommand extends Command {
        String toCmd() {
            return "";
        }
        String javacode() {
            StringBuilder b = new StringBuilder();
            b.append("try{Thread.sleep(200);((io.appium.java_client.MobileDriver) get___d___()).hideKeyboard();}catch(Exception e){}");
            return b.toString();
        }
        public MHideKeyPadCommand(Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
        }
        public String toSampleSelCmd() {
            return "hidekeypad";
        }
    }

    public static class MShakeCommand extends Command {
        String toCmd() {
            return "";
        }
        String javacode() {
            StringBuilder b = new StringBuilder();
            b.append("if(get___d___() instanceof io.appium.java_client.ios.IOSDriver){((io.appium.java_client.ios.IOSDriver)get___d___()).shake();}");
            return b.toString();
        }
        public MShakeCommand(Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
        }
        public String toSampleSelCmd() {
            return "shake";
        }
    }

    public static class MTouchActionCommand extends Command {
        String action, expr;
        Integer x, y, d;

        public MTouchActionCommand(Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
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
                        throwParseError(null, new RuntimeException("Expressions(>=1 <=3) expected after actions (longpress)"));
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
                    b.append("\n"+cond.javacodeonly(null));
                    String cvarnm = state.currvarname();
                    if(x==null) {
                        b.append("\n"+acnm+"."+action+"("+cvarnm+".get(0));");
                    } else {
                        b.append("\n"+acnm+"."+action+"("+cvarnm+".get(0), "+x+", "+y+");");
                    }
                } else {
                    b.append("\n"+acnm+"."+action+"("+x+", "+y+");");
                }
            } else if(action.toLowerCase().matches("longpress")) {
                if(expr!=null) {
                    FindCommand cond = new FindCommand(expr, fileLineDetails, state);
                    b.append("\n"+cond.javacodeonly(null));
                    String cvarnm = state.currvarname();
                    if(d==null) {
                        if(x==null) {
                            b.append("\n"+acnm+"."+action+"("+cvarnm+".get(0));");
                        } else {
                            b.append("\n"+acnm+"."+action+"("+cvarnm+".get(0), "+x+", "+y+");");
                        }
                    } else {
                        if(x==null) {
                            b.append("\n"+acnm+"."+action+"("+cvarnm+".get(0), java.time.Duration.ofMillis("+d+"));");
                        } else {
                            b.append("\n"+acnm+"."+action+"("+cvarnm+".get(0), "+x+", "+y+", java.time.Duration.ofMillis("+d+"));");
                        }
                    }
                } else {
                    if(d==null) {
                        b.append("\n"+acnm+"."+action+"("+x+", "+y+");");
                    } else {
                        b.append("\n"+acnm+"."+action+"("+x+", "+y+", java.time.Duration.ofMillis("+d+"));");
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
            b.append("\nio.appium.java_client.TouchAction "+acnm+" = new io.appium.java_client.TouchAction((io.appium.java_client.MobileDriver)get___d___());");
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
            c += vn + " = pluginize(\""+name+"\", \""+state.plugins.get(name)+"\", "+avn+", "+avn1+")";
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
            c += "pluginize(\""+name+"\", \""+state.plugins.get(name)+"\", "+avn+", "+avn1+");\n";
            return c;
        }
        public PluginCommand(String info, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            String[] parts = info.trim().split("[\t ]+");
            if(parts.length>0) {
                name = unSantizedUnQuoted(parts[0], state);
                if(!state.plugins.containsKey(name)) {
                    throwError(cmdDetails, new RuntimeException("Plugin not found with name " + name));
                }
                for (int i = 1; i < parts.length; i++) {
                    in.add(unSantizedUnQuoted(parts[i], state));
                }
                for (int i = 0; i < children.size(); i++) {
                    if(children.get(i) instanceof ValueListCommand) {
                        ValueListCommand vlc = (ValueListCommand)children.get(i);
                        String[] tm = new String[vlc.children.size()];
                        int c = 0;
                        for (Command vc : vlc.children) {
                            tm[c++] = unSantizedUnQuoted(((ValueCommand)vc).value, state); 
                        }
                    } else {
                        in1.add(new String[]{unSantizedUnQuoted(((ValueCommand)children.get(i)).value, state)});
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
                    String[] tm = new String[vlc.children.size()];
                    int c = 0;
                    for (Command vc : vlc.children) {
                        tm[c++] = unSantizedUnQuoted(((ValueCommand)vc).value, state); 
                    }
                    in1.add(tm);
                } else {
                    in1.add(new String[]{unSantizedUnQuoted(((ValueCommand)children.get(i)).value, state)});
                }
            }
        }
        public String toSampleSelCmd() {
            return "plugin $abc \"123\" \"file://abc.txt\" 'http://abc.com/a.html' 'https://abc.com/a.html' (json|xml|csv|urlencoded|text)";
        }
    }

    public static void main(String[] args) throws Exception {
        /*Reflections reflections = new Reflections("com.gatf.selenium");
        Set<Class<? extends Command>> cmds = reflections.getSubTypesOf(Command.class);
        for (Class<? extends Command> cls : cmds)
        {
            Object o = cls.newInstance();
            Method m = cls.getMethod("toSampleSelCmd", new Class[]{});
            m.setAccessible(true);
            System.out.println(cls.getSimpleName().substring(0, cls.getSimpleName().length()-7)+ ":\n\t" + m.invoke(o, new Object[]{}));
        }*/
        Map<String, SeleniumDriverConfig> mp = new HashMap<String, SeleniumDriverConfig>();
        SeleniumDriverConfig dc = new SeleniumDriverConfig();
        dc.setName("chrome");
        dc.setDriverName("webdriver.chrome.driver");
        dc.setPath("F:\\Laptop_Backup\\Development\\selenium-drivers\\chromedriver.exe");
        mp.put("chrome", dc);dc = new SeleniumDriverConfig();
        dc.setName("appium-ios");
        dc.setDriverName("webdriver.chrome.driver");
        dc.setPath("F:\\Laptop_Backup\\Development\\selenium-drivers\\chromedriver.exe");
        mp.put("appium-ios", dc);
        List<String> commands = new ArrayList<String>();
        GatfExecutorConfig config = new GatfExecutorConfig();
        config.setTestCasesBasePath("F:\\Laptop_Backup\\sumeetc\\workspace\\sampleApp\\src\\test\\resources\\");
        config.setTestCaseDir("data");
        config.setSeleniumDriverConfigs(mp.values().toArray(new SeleniumDriverConfig[mp.values().size()]));
        AcceptanceTestContext c = new AcceptanceTestContext();
        c.setGatfExecutorConfig(config);
        Command cmd = Command.read(new File("F:\\Laptop_Backup\\sumeetc\\workspace\\sampleApp\\src\\test\\resources\\data\\test.sel"), commands, c);
        String jc = cmd.javacode();
        System.out.println(jc);
        System.out.println(new Formatter().formatSource(jc));

        /*SeleniumTest t = new SeleniumTest(jc, c, 0) {

            @Override
            public Map<String, SeleniumResult> execute(LoggingPreferences ___lp___) throws Exception {
                return null;
            }

            @Override
            public SeleniumTest copy(AcceptanceTestContext ctx, int index) {
                return null;
            }

            @Override
            public void close() {

            }
        };
        List<String> ___a___1 = new ArrayList<String>();
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

        testSelScript();
    }

    public static void testSelScript() throws Exception {
        Map<String, SeleniumDriverConfig> mp = new HashMap<String, SeleniumDriverConfig>();
        SeleniumDriverConfig dc = new SeleniumDriverConfig();
        dc.setName("chrome");
        dc.setDriverName("webdriver.chrome.driver");
        dc.setPath("F:\\Laptop_Backup\\Development\\selenium-drivers\\chromedriver.exe");
        mp.put("chrome", dc);dc = new SeleniumDriverConfig();
        GatfExecutorConfig config = new GatfExecutorConfig();
        config.setSeleniumDriverConfigs(mp.values().toArray(new SeleniumDriverConfig[mp.values().size()]));
        AcceptanceTestContext c = new AcceptanceTestContext();
        c.setGatfExecutorConfig(config);

        config.setSeleniumLoggerPreferences("browser(OFF),client(OFF),driver(OFF),performance(OFF),profiler(OFF),server(OFF)");
        for (SeleniumDriverConfig selConf : config.getSeleniumDriverConfigs())
        {
            if(selConf.getDriverName()!=null) {
                System.setProperty(selConf.getDriverName(), selConf.getPath());
            }
        }

        Map<String, Object> _mt = new HashMap<String, Object>();
        _mt.put("a", "test");
        c.getWorkflowContextHandler().templatize(_mt, "dsadasd ${a} $v{f}");

        Test t1 = new Test(c, 0);
        t1.execute(SeleniumCodeGeneratorAndUtil.getLp(config));
        t1.quit();
        TestCase tc = new TestCase();
        tc.setSimulationNumber(0);
        System.out.println(t1.get___cxt___().getWorkflowContextHandler().getSuiteWorkflowContext(tc));

    }
}
