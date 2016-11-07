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

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Stack;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.openqa.selenium.Keys;

import com.google.googlejavaformat.java.Formatter;

import edu.emory.mathcs.backport.java.util.Arrays;

public class Command {
	
    protected static class CommandState {
        boolean commentStart = false, codeStart = false;
        int concExec = 0;
        
        int NUMBER = 1;
        int NUMBER_COND = 1;
        int NUMBER_SR = 1;
        int NUMBER_DR = 1;
        int NUMBER_LIV = 1;
        int NUMBER_EXC = 1;
        int NUMBER_ST = 1;
        int NUMBER_IF = 1;
        
        int NUMBER_SC = 1;
        Stack<String> stck = new Stack<String>();
        String sc = null, psc = null;
        
        int NUMBER_PARC = 1;
        Stack<String> stckPar = new Stack<String>();
        String parc = null, pparc = null;
        
        int NUMBER_ITER = 1;
        Stack<String> stckIter = new Stack<String>();
        String itr = null, pitr = null;
        
        List<Command> allSubTests = new ArrayList<Command>();
        Map<String, String> qss = new HashMap<String, String>();
        
        String ifvarname() {
            return "___i___" + NUMBER_IF++;
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
        
        String condvarname() {
            return "___c___" + NUMBER_COND++;
        }
        
        String currcondvarname() {
            return "___c___" + (NUMBER_COND-1);
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
            comd = new CommentCommand(cmdDetails, state);
            ValueCommand vc = new ValueCommand(cmdDetails, state);
            vc.value = cmd;
            comd.children.add(vc);
        } else if (cmd.startsWith("/*")) {
            cmd = cmd.substring(2);
            comd = new CommentCommand(cmdDetails, state);
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
            if(cmd.startsWith("<<<(") && cmd.endsWith(")")) {
                lang = cmd.substring(4, cmd.length()-1);
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
            comd = new TransientProviderLoopCommand(cmd.trim(), cmdDetails, state);
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
        } else if (cmd.toLowerCase().startsWith("window ")) {
			comd = new WindowCommand(cmd.substring(7), cmdDetails, state);
		} else if (cmd.toLowerCase().startsWith("sleep ")) {
			comd = new SleepCommand(cmd.substring(6), cmdDetails, state);
		} else if (cmd.toLowerCase().startsWith("type ") || cmd.toLowerCase().startsWith("select ") 
                || cmd.toLowerCase().startsWith("click ") || cmd.toLowerCase().equals("click")
                || cmd.toLowerCase().startsWith("hover ") || cmd.toLowerCase().equals("hover")
                || cmd.toLowerCase().startsWith("chord ") || cmd.toLowerCase().startsWith("hoverclick ") 
                || cmd.toLowerCase().startsWith("clear ") || cmd.toLowerCase().equals("clear")
                || cmd.toLowerCase().startsWith("submit ") || cmd.toLowerCase().equals("submit")
                || cmd.toLowerCase().startsWith("randomize ")) {
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
		} else if (cmd.trim().isEmpty()) {
		    comd = new NoopCommand(cmdDetails, state);
		} else {
			comd = new ValueCommand(cmdDetails, state);
			if(cmd.charAt(0)==cmd.charAt(cmd.length()-1)) {
        		if(cmd.charAt(0)=='"' || cmd.charAt(0)=='\'') {
        			cmd = cmd.substring(1, cmd.length()-1);
        		}
        	}
			((ValueCommand)comd).value = state.unsanitize(cmd);
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
			
			if(tmp instanceof ValueListCommand) {
				get(tmp, iter, state);
				if(prev!=null)prev.children.add(tmp);
			} else if(tmp instanceof StartCommand) {
				get(prev, iter, state);
			} else if((tmp instanceof CommentCommand && state.commentStart) || tmp instanceof CodeCommand) {
                get(tmp, iter, state);
                parent.children.add(tmp);
            } else if(tmp instanceof EndCommentCommand) {
			    ValueCommand vc = new ValueCommand(o, state);
			    vc.value = ((EndCommentCommand)tmp).value;
			    parent.children.add(vc);
				return;
			} else if(tmp instanceof EndCommand) {
                return;
            } else if(tmp instanceof ImportCommand) {
				List<String> commands = FileUtils.readLines(new File(o[3].toString()+((ImportCommand)tmp).name), "UTF-8");
				int cnt = 1;
				String fnm = ((ImportCommand)tmp).name;
				if(fnm.lastIndexOf("\\")!=-1) {
				    fnm = fnm.substring(0, fnm.lastIndexOf("\\")+1);
				} else if(fnm.lastIndexOf("/")!=-1) {
                    fnm = fnm.substring(0, fnm.lastIndexOf("/")+1);
                }
				for (String c : commands) {
					iter.add(new Object[]{c, cnt++, ((ImportCommand)tmp).name, fnm, state});
				}
				for (@SuppressWarnings("unused") String c : commands) {
					iter.previous();
				}
			} else {
			    if(parent instanceof CodeCommand) {
			        ((CodeCommand)parent).b.append(((ValueCommand)tmp).value+"\n");
			    } else {
			        parent.children.add(tmp);
			    }
			}
			prev = tmp;
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
		Command cmd = Command.getAll(commands, filename, state);
		return cmd;
	}
	
	static Command read(File file, List<String> commands, Map<String, SeleniumDriverConfig> mp) throws Exception {
	    if(commands==null) {
	        commands = new ArrayList<String>();
	    } else {
	        commands.clear();
	    }
		commands.addAll(FileUtils.readLines(file, "UTF-8"));
		CommandState state = new CommandState();
		Command cmd = Command.getAll(commands, file.getName(), state);
		cmd.mp = mp;
		return cmd;
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
		b.append("import com.google.common.base.Function;\n");
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
		b.append("public void quit() {\nif(get___d___()!=null)get___d___().quit();\n}\n");
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
		b.append("SearchContext "+state.currvarnamesc()+" = get___d___();\n");
        b.append("WebDriver ___cw___ = get___d___();\n");
        b.append("WebDriver ___ocw___ = ___cw___;");
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
			code = code.replace("@element", state.currvarname());
			code = code.replace("@sc", state.currvarnamesc());
			code = code.replace("@index", state.currvarnameitr()!=null?state.currvarnameitr():"@index");
			code = code.replace("@printProvJson", "___cxt___print_provider__json");
			code = code.replace("@printProv", "___cxt___print_provider__");
			code = code.replace("@print", "System.out.println");
			return code + ";";
		}
		public String toSampleSelCmd() {
		    return "exec {statement}";
		}
	}
	
	public static class CommentCommand extends Command {
	    String javacode() {
	        return "";
	    }
	    CommentCommand(Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
        }
	}
    
    public static class CodeCommand extends Command {
        StringBuilder b = new StringBuilder();
        String lang = "java";
        String[] arglist = new String[]{};
        CodeCommand(String lang, String argnames, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
            if(lang!=null && !lang.trim().isEmpty()) {
                if(lang.equalsIgnoreCase("java") || lang.equalsIgnoreCase("groovy") || lang.equalsIgnoreCase("js") || lang.equalsIgnoreCase("ruby") || lang.equalsIgnoreCase("python")) {
                    this.lang = lang.toLowerCase();
                } else {
                    throw new RuntimeException("Invalid code language specified");
                }
            }
            if(argnames!=null && !argnames.isEmpty()) {
                arglist = argnames.split("[\t ]*,[\t ]*");
            }
            b.append("\n");
        }
        String toCmd() {
            return "<<< \n" + b.toString() + "\n>>>\n";
        }
        String javacode() {
            String code = b.toString();
            if(lang.equals("java")) {
                code = code.replace("@driver", "___cw___");
                code = code.replace("@window", "___ocw___");
                code = code.replace("@element", state.currvarname());
                code = code.replace("@sc", state.currvarnamesc());
                code = code.replace("@index", state.currvarnameitr()!=null?state.currvarnameitr():"@index");
                code = code.replace("@printProvJson", "___cxt___print_provider__json");
                code = code.replace("@printProv", "___cxt___print_provider__");
                code = code.replace("@print", "System.out.println");
                return state.unsanitize(code);
            } else if(lang.equals("groovy")) {
                String gcode = "";
                gcode += "Binding __b = new Binding();\n";
                for (String arg : arglist)
                {
                    gcode += "__b.setVariable(\""+arg+"\", "+arg+");\n";
                }
                gcode += "GroovyShell __gs = new GroovyShell(__b);\n";
                gcode += "__gs.evaluate(\""+esc(state.unsanitize(code.replaceAll("\n", "\\\\n")))+"\");\n";
                return gcode;
            } else if(lang.equals("js")) {
                String jscode = "";
                String args = "";
                for (String arg : arglist)
                {
                    arg = arg.replace("@element", state.currvarname());
                    arg = arg.replace("@index", state.currvarnameitr()!=null?state.currvarnameitr():"@index");
                    if(!arg.trim().isEmpty()) {
                        args += arg +",";
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
                rcode += "ScriptingContainer __rs = new ScriptingContainer(LocalVariableBehavior.PERSISTENT);\n";
                for (String arg : arglist)
                {
                    rcode += "__rs.put(\""+arg+"\", "+arg+");\n";
                }
                rcode += "__rs.runScriptlet(\""+esc(state.unsanitize(code.replaceAll("\n", "\\\\n")))+"\");\n";
                return rcode;
            } else if(lang.equals("python")) {
                String pcode = "";
                pcode += "PythonInterpreter pi = new PythonInterpreter();\n";
                for (String arg : arglist)
                {
                    pcode += "__rs.set(\""+arg+"\", "+arg+");\n";
                }
                pcode += "__rs.exec(\""+esc(state.unsanitize(code.replaceAll("\n", "\\\\n")))+"\");\n";
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
            return fName+"("+state.currvarnamesc()+", ___lp___);\n";
        }
        String javacodeint() {
            StringBuilder b = new StringBuilder();
            b.append("void " + fName + "(SearchContext "+state.currvarnamesc()+", LoggingPreferences ___lp___) {\n");
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
            b.append("}\n");
            return b.toString();
        }
        public String toSampleSelCmd() {
            return "";
        }
    }
	
	public static class VarCommand extends FindCommandImpl {
		String name;
		VarCommand(String val, Object[] cmdDetails, CommandState state) {
            super(cmdDetails, state);
			if(val.indexOf(" ")!=-1) {
				name = val.substring(0, val.indexOf(" ")).trim();
				cond = new FindCommand(val.substring(val.indexOf(" ")+1).trim(), fileLineDetails, state);
			} else {
				//excep
			}
		}
		String toCmd() {
			return "var " + name + " " + cond.toCmd();
		}
		String javacode() {
			return cond.javacodeonly(null) + "\nList<WebElement> " + name + " = " + state.currvarname() + ";";
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
			return "File "+sc+" = ((TakesScreenshot)___ocw___).getScreenshotAs(OutputType.FILE);\nFileUtils.copyFile("+sc+", new File(\""+esc(fpath)+"\"));";
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
					b.append("import " + c + ";\n");
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
			name = cmd;
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
		    cond = time.equals("0")?new FindCommand(parts[0], lineNumber, state):new WaitAndFindCommand(parts[0], Long.valueOf(time), lineNumber, state);
		    if(parts.length>1) {
		        String cmd = "";
		        for (int i = 1; i < parts.length; i++)
                {
                    cmd += parts[i] + " ";
                }
		        cmd = cmd.trim();
		        if(!cmd.isEmpty()) {
    		        if (cmd.toLowerCase().startsWith("type ") || cmd.toLowerCase().startsWith("select ") 
    		                || cmd.toLowerCase().equals("click") || cmd.toLowerCase().equals("hover")
    		                || cmd.toLowerCase().startsWith("hoverclick") || cmd.toLowerCase().equals("clear")
    		                || cmd.toLowerCase().equals("submit") || cmd.toLowerCase().startsWith("actions ")
    		                || cmd.toLowerCase().equals("chord ")) {
    		            //cmd = unsanitize(cmd);
    		            Command comd = handleActions(cmd, cond, lineNumber, state);
    		            children.add(comd);
    		        }
		        }
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
		static String getFp(FindCommand cond, List<Command> children, boolean negation, CommandState state) {
		    StringBuilder b = new StringBuilder();
		    b.append("new Functor<SearchContext, Integer>() {@Override\n");
		    String cparc = state.currvarnameparc()!=null?state.currvarnameparc():"__1__";
		    b.append("public Integer f(SearchContext "+state.currvarnamesc()+", SearchContext "+cparc+")\n{\ntry{\n");
            b.append(cond.javacodeonly(children));
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
                String ex = state.evarname();
                b.append("\nreturn "+cond.condition()+"?1:2;\n}\ncatch(AssertionError "+state.evarname()+"){}\ncatch(Exception "+ex+"){\nSystem.out.println("+ex+".getMessage());}\n");
            }
            else
            {
                b.append("\nreturn "+cond.condition()+"?1:2;\n}\ncatch(AssertionError "+state.evarname()+"){");
                if(!children.isEmpty())
                {
                    b.append("\n");
                    for (Command c : children) {
                        b.append(c.javacode());
                        b.append("\n");
                    }
                }
                String ex = state.evarname();
                b.append("}\ncatch(Exception "+ex+"){\nSystem.out.println("+ex+".getMessage());}\n");
            }
		    b.append("\nreturn 2;}\n}.f("+state.currvarnamesc()+", "+state.currvarnameparc()+")");
		    return b.toString();
		}
		String javacode() {
			StringBuilder b = new StringBuilder();
			String ifvn = state.ifvarname();
			b.append("\nInteger "+ifvn+" = "+getFp(cond, children, negation, state)+";\nif("+ifvn+"!=2){if("+ifvn+"==3)break;if("+ifvn+"==4)continue;}");
			for (ElseIfCommand elif : elseifs) {
				b.append(elif.javacode());
			}
			if(elsecmd!=null) {
				b.append(elsecmd.javacode()+"\n");
			}
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
			b.append("\nelse if("+IfCommand.getFp(cond, children, negation, state)+"){}");
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
			b.append("else");
			b.append("\n{");
			if(!children.isEmpty())
			{
				b.append("\n");
				for (Command c : children) {
					b.append(c.javacode());
					b.append("\n");
				}
				b.append("}");
			}
			else
			{
				b.append("\n}");
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
        TransientProviderLoopCommand(String val, Object[] cmdDetails, CommandState state) {
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
            b.append("#transient-provider ");
            b.append(name);
            b.append(cond.toCmd());
            return b.toString();
        }
        @SuppressWarnings("unchecked")
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
		    state.pushSc();
			StringBuilder b = new StringBuilder();
			if(config.getName().equalsIgnoreCase("chrome")) {
				b.append("DesiredCapabilities ___dc___ = DesiredCapabilities."+config.getName().toLowerCase()+"();\n");
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
                b.append("DesiredCapabilities ___dc___ = new DesiredCapabilities(\"ios\", \""+config.getVersion()+"\", \"MAC\");\n");
                b.append("___dc___.setCapability(CapabilityType.LOGGING_PREFS, ___lp___);\n");
                if(config.getCapabilities()!=null)
                {
                    for (Map.Entry<String, String> e : config.getCapabilities().entrySet())
                    {
                        b.append("___dc___.setCapability(\""+esc(e.getKey())+"\", \""+esc(e.getValue())+"\");\n");
                    }
                }
                b.append("set___d___(new io.appium.java_client.android.AndroidDriver(new java.net.URL(\"http://{0}:{1}@127.0.0.1:4444/wd/hub\"), ___dc___));\n");
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
            return "window " + name;
        }
        String javacode() {
            if(name.equals("") || name.equalsIgnoreCase("main")) {
                return "___cw___ = ___ocw___;\n___sc___1 = ___cw___;";
            } else {
                try {
                    int index = Integer.parseInt(name);
                    String whl = "List<String> "+state.varname()+" = new java.util.ArrayList<String> (___ocw___.getWindowHandles());\n"
                            + "if("+state.currvarname()+"!=null && "+index+">=0 && "+state.currvarname()+".size()>"+index+")\n{\n";
                    return whl + "___cw___ = ___ocw___.switchTo().window(\""+esc(name)+"\");\n}\n___sc___1 = ___cw___;";
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
            return "window {name(main|1..N)}";
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
			return "___cw___.navigate().to(evaluate(\""+esc(url)+"\"));";
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
		return cmd.replace("\"", "\\\"");
	}
	
	public static class FindCommand extends Command {
		String by, classifier, subselector, condvar = "true", topele, rtl;
		boolean suppressErr = false;
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
		        		String rhs = parts[2].trim();
		        		rhs = state.unsanitize(rhs);
		        		if(rhs.startsWith("=")) {
		        			ValueCommand vc = new ValueCommand(cmdDetails, state);
		        			vc.value = rhs.substring(1);
		        			if(vc.value.charAt(0)=='"' || vc.value.charAt(0)=='\'') {
		        				vc.value = vc.value.substring(1, vc.value.length()-1);
			        		}
		        			children.add(vc);
		        		}
		        	}
				} else {
					by = parts[0];
					subselector = state.unsanitize(by);
					if(parts.length>1) {
						String rhs = parts[1].trim();
		        		rhs = state.unsanitize(rhs);
		        		if(rhs.startsWith("=")) {
		        			ValueCommand vc = new ValueCommand(cmdDetails, state);
		        			vc.value = rhs.substring(1).trim();
		        			if(vc.value.charAt(0)=='"' || vc.value.charAt(0)=='\'') {
		        				vc.value = vc.value.substring(1, vc.value.length()-1);
			        		}
		        			children.add(vc);
		        		}
					}
				}
				by = state.unsanitize(by);
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
    			        if(state.currvarnameparc()==null) {
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
    			} else if(!by.equalsIgnoreCase(subselector)) {
    			    throwParseError(null);
    			}
    			
    			if(this instanceof WaitAndFindCommand) {
    			    b.append("\nif("+topele+"==null || "+topele+".isEmpty())return false;");
    			} else {
    			    b.append("\nAssert.assertTrue(\"Element not found by selector " + by + "@'" + esc(classifier) 
    			            + "' at line number "+fileLineDetails[1]+" \", "+topele+"!=null && !"+topele+".isEmpty());");
    			}
    			
    			if(this.children!=null && this.children.size()>0) {
    				children = this.children;
    			}
    			if(children!=null && children.size()>0 && subselector!=null && !subselector.isEmpty()) {
    				Command c = children.get(0);
    				if(c instanceof ValueCommand) {
    					String value = ((ValueCommand)c).value;
    					String cvarname = state.currvarname();
    					condvar = state.condvarname();
    					b.append("\nboolean " + condvar + " = true;");
    					if(by.equalsIgnoreCase(subselector))
    					{
    						if(subselector.equalsIgnoreCase("title")) {
    							b.append("\n" + condvar + " &= evaluate(\""+esc(value)+"\").equals(___cw___.getTitle());");
    							return b.toString();
    						} else if(subselector.equalsIgnoreCase("currentUrl")) {
    							b.append("\n" + condvar + " &= evaluate(\""+esc(value)+"\").equals(___cw___.getCurrentUrl());");
    							return b.toString();
    						} else if(subselector.equalsIgnoreCase("pageSource")) {
    							b.append("\n" + condvar + " &= evaluate(\""+esc(value)+"\").equals(___cw___.getPageSource());");
    							return b.toString();
    						} else if(subselector.equalsIgnoreCase("width")) {
    							b.append("\n" + condvar + " &= evaluate(\""+esc(value)+"\").equals(String.valueOf(___cw___.manage().window().getSize().getWidth()));");
    							return b.toString();
    						} else if(subselector.equalsIgnoreCase("height")) {
    							b.append("\n" + condvar + " &= evaluate(\""+esc(value)+"\").equals(String.valueOf(___cw___.manage().window().getSize().getHeight()));");
    							return b.toString();
    						} else if(subselector.equalsIgnoreCase("xpos")) {
    							b.append("\n" + condvar + " &= evaluate(\""+esc(value)+"\").equals(String.valueOf(___cw___.manage().window().getPosition().getX()));");
    							return b.toString();
    						} else if(subselector.equalsIgnoreCase("ypos")) {
    							b.append("\n" + condvar + " &= evaluate(\""+esc(value)+"\").equals(String.valueOf(___cw___.manage().window().getPosition().getY()));");
    							return b.toString();
    						} else if(subselector.equalsIgnoreCase("alerttext")) {
    							b.append("\n" + condvar + " &= evaluate(\""+esc(value)+"\").equals(___cw___.switchTo().alert().getText());");
    							return b.toString();
    						} else {
    						    throwParseError(null);
    						}
    					}
    					
    					b.append("\nfor(final WebElement " + state.varname() + " : " + cvarname + ")\n{");
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
    					}
    					b.append("\n}");
    				} else if(c instanceof ValueListCommand) {
    					List<String> values = ((ValueListCommand)c).getValues();
    					String cvarname = state.currvarname();
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
                            || c instanceof SelectCommand || c instanceof ChordCommand) {
                        if(FindCommandImpl.class.isAssignableFrom(c.getClass()) && ((FindCommandImpl)c).cond==null) {
                            b.append(c.selcode(topele));
                        }
                    } else if(c instanceof ActionsCommand) {
                        if(FindCommandImpl.class.isAssignableFrom(c.getClass())) {
                            b.append(c.javacode());
                        }
                    }
    			} else if(subselector!=null) {
                    @SuppressWarnings("unchecked")
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
    			
    			if(condvar==null) {
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
    			}
    			return b.toString();
		    } catch (GatfSelCodeParseError e) {
                throw e;
            } catch (Throwable e) {
		        throwParseError(null, e);
            }
		    return null;
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
			String v = "final Object[]  " + wfte + " = new Object[1];\n"
			        + "final WebDriver "+state.currvarnamesc()+" = (WebDriver)"+tsc+";\ntry{\n(new WebDriverWait("+state.currvarnamesc()+", "+waitfor+")).until(" +
				"\nnew Function<WebDriver, Boolean>(){"+
					"\npublic Boolean apply(WebDriver input) {\n"+
						super.javacodeonly(children) +
						"\n" + wfte + "[0] = " + topele() + ";\n" +
						"\nreturn "+super.condition()+";" +
					"\n}"+
					"\npublic String toString() {"+
					"\nreturn \"\";" +
					"\n}"+
				"\n});\n} catch(org.openqa.selenium.TimeoutException "+ex+") {\nthrow new RuntimeException("+getErr()+", "+ex+");\n}";
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
				//b.append("\nAssert.assertTrue("+cond.condition()+");");
			}
			b.append("\n"+state.currvarname()+".get(0).sendKeys(evaluate(\""+esc(value)+"\"));");
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
                //b.append("\nAssert.assertTrue("+cond.condition()+");");
            }
            b.append("\nrandomize("+state.currvarname()+", \""+(v1!=null?esc(v1):"")+"\", \""+(v2!=null?esc(v2):"")+"\", \""+(v3!=null?esc(v3):"")+"\");");
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
                parts[0] = parts[0].trim();
                value = parts[0];
                String temp = org.apache.commons.lang.StringEscapeUtils.unescapeJava(parts[0]);
                try
                {
                    if(parts.length>1){
                        cond = new FindCommand(parts[1].trim(), fileLineDetails, state);
                    }
                    for (char c : temp.toCharArray())
                    {
                        Keys kys = Keys.getKeyFromUnicode(c);
                        if(kys!=null) {
                            values.add("Keys." + kys.name());
                        } else {
                            values.add("\""+c+"\"");
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
                b.append("\n"+state.currvarname()+".get(0).sendKeys(Keys.chord("+chs+"));");
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
            String cvarnm = state.currvarname();
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
                //b.append("\nAssert.assertTrue("+cond.condition()+");");
            }
            String cvarnm = state.currvarname();
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
                if(t[counter].matches("click|clickAndHold|release|doubleClick|contextClick|clickhold|rightclick")) {
                    cmd.action = t[counter];
                    if(cmd.action.equals("clickhold")) {
                        cmd.action = "clickAndHold";
                    } else if(cmd.action.equals("rightclick")) {
                        cmd.action = "contextClick";
                    }
                } else if(t[counter].matches("keyDown|keyUp|sendKeys|type|moveToElement|moveto")) {
                    cmd.action = t[counter];
                    if(cmd.action.equals("type")) {
                        cmd.action = "sendKeys";
                    } else if(cmd.action.equals("moveto")) {
                        cmd.action = "moveToElement";
                    }
                    if(tl>counter+1) {
                        cmd.expr1 = state.unsanitize(t[++counter]);
                        if(cmd.expr1.charAt(0)==cmd.expr1.charAt(cmd.expr1.length()-1)) {
                            if(cmd.expr1.charAt(0)=='"' || cmd.expr1.charAt(0)=='\'') {
                                cmd.expr1 = cmd.expr1.substring(1, cmd.expr1.length()-1);
                            }
                        }
                    } else {
                        throw new RuntimeException("Expression expected after actions (keyDown|keyUp|sendKeys|moveToElement)");
                    }
                } else if(t[counter].matches("dragAndDrop|moveByOffset|moveby|dragdrop")) {
                    cmd.action = t[counter];
                    if(cmd.action.equals("moveby")) {
                        cmd.action = "moveByOffset";
                    } else if(cmd.action.equals("dragdrop")) {
                        cmd.action = "dragAndDrop";
                    }
                    if(tl>counter+2) {
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
                        throw new RuntimeException("Expressions(2) expected after actions (dragAndDrop|moveByOffset)");
                    }
                } else {
                    throw new RuntimeException("Invalid action specified, should be one of (click|clickAndHold|release|doubleClick|contextClick|keyDown|keyUp|sendKeys|moveToElement|dragAndDrop|moveByOffset)");
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
            if(action.matches("click|clickAndHold|release|doubleClick|contextClick")) {
                b.append("\n"+acnm+"."+action+"();");
            } else if(action.matches("keyDown|keyUp")) {
                String ck = state.unsanitize(expr1);
                char key = ck.charAt(0);
                b.append("\n"+acnm+"."+action+"(Keys.getKeyFromUnicode('"+key+"'));");
            } else if(action.matches("sendKeys") || action.matches("type")) {
                String ck = state.unsanitize(expr1);
                b.append("\n"+acnm+".sendKeys(evaluate(\""+ck+"\"));");
            } else if(action.matches("moveToElement")) {
                String ck = state.unsanitize(expr1);
                FindCommand fc = new FindCommand(ck, fileLineDetails, state);
                b.append("\n"+fc.javacodeonly(null));
                //b.append("\nAssert.assertTrue("+fc.condition()+");");
                String cvarnm = state.currvarname();
                b.append("\n"+acnm+"."+action+"("+cvarnm+".get(0));");
            } else if(action.matches("dragAndDrop")) {
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
            } else if(action.matches("moveByOffset")) {
                try {
                    int ck = Integer.parseInt(state.unsanitize(expr1));
                    int ck1 = Integer.parseInt(state.unsanitize(expr2));
                    b.append("\n"+acnm+"."+action+"("+ck+", "+ck1+");");
                } catch (Exception e) {
                    throw new RuntimeException("xOffset and yOffset need to be integer values for moveByOffset");
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
            b.append("\nActions "+acnm+" = new Actions(get___d___());\n"+acnm+".moveToElement("+cond.topele+".get(0));");
            b.append(_javacode(acnm));
            b.append("\n"+acnm+".build().perform();");
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
            if(state.currvarnameparc()==null) {
                throwParseError(null, new RuntimeException("Break is allowed only within a loop block"));
            }
            return "\nif(true)return 3;";
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
            if(state.currvarnameparc()==null) {
                throwParseError(null, new RuntimeException("Break is allowed only within a loop block"));
            }
            return "\nif(true)return 4;";
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
				//b.append("\nAssert.assertTrue("+cond.condition()+");");
			}
			b.append("\n"+state.currvarname()+".get(0).click();");
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
	
	public static class ClearCommand extends FindCommandImpl {
		String toCmd() {
			return "clear" + (cond!=null?cond.toCmd():"");
		}
		String javacode() {
			StringBuilder b = new StringBuilder();
			if(cond!=null) {
				b.append(cond.javacodeonly(children));
				//b.append("\nAssert.assertTrue("+cond.condition()+");");
			}
			b.append("\n"+state.currvarname()+".get(0).clear();");
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
				//b.append("\nAssert.assertTrue("+cond.condition()+");");
			}
			b.append("\n"+state.currvarname()+".get(0).submit();");
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
        mp.put("chrome", dc);
        List<String> commands = new ArrayList<String>();
        Command cmd = Command.read(new File("F:\\Laptop_Backup\\sumeetc\\workspace\\sampleApp\\src\\test\\resources\\test.sel"), commands, mp);
        String jc = cmd.javacode();
        System.out.println(jc);
        System.out.println(new Formatter().formatSource(jc));
    }
}
