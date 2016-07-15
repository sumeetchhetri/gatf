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
	static int NUMBER = 1;
	static int NUMBER_COND = 1;
	static int NUMBER_SR = 1;
	
	static int NUMBER_SC = 1;
	static Stack<String> stck = new Stack<String>();
	static String sc = null, psc = null;
	
	String name;
	private Map<String, SeleniumDriverConfig> mp;
	String className = "STC_" + System.nanoTime() + "";
	List<Command> children = new ArrayList<Command>();
	static Map<String, String> qss = new HashMap<String, String>();
    static Pattern p = Pattern.compile("\"([^\"]*)\"");
    static Pattern WAIT = Pattern.compile("\\?\\?[\t ]*([0-9]+)");
    
    public String getClassName() {
    	return className;
    }
    
    int weight() {
    	return 100;
    }
    
    static String varname() {
    	return "___w___" + NUMBER++;
    }
    
    static String currvarname() {
    	return "___w___" + (NUMBER-1);
    }
    
    static String varnamesr() {
    	return "___sr___" + NUMBER_SR++;
    }
    
    static String currvarnamesr() {
    	return "___sr___" + (NUMBER_SR-1);
    }
    
    static String currvarnamesc() {
    	return sc;
    }
    
    static String prevvarnamesc() {
    	stck.pop();
    	sc = stck.size()==1?stck.peek():psc;
    	return psc;
    }
    
    static void pushSc() {
    	psc = sc;
    	sc = "___sc___" + NUMBER_SC++;
    	stck.push(sc);
    }
    
    static String condvarname() {
    	return "___c___" + NUMBER_COND++;
    }
    
    static String currcondvarname() {
    	return "___c___" + (NUMBER_COND-1);
    }
    
    static String unsanitize(String val) {
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
    
    static String sanitize(String cmd) {
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
    
	static Command parse(String cmd) {
		Command comd = null;
		cmd = sanitize(cmd);
		if(cmd.startsWith("??")) {
			String time = "0";
			Matcher m = WAIT.matcher(cmd);
			int start = 2;
			if(m.find()) {
				time = m.group(1);
				start = m.end(1) + 1;
			}
			cmd = cmd.substring(start).trim();
			comd = new ValidateCommand(time, cmd);
		} else if (cmd.startsWith("?")) {
			cmd = cmd.substring(1);
			comd = new IfCommand();
			((IfCommand)comd).cond = new FindCommand(cmd);
		} else if (cmd.startsWith(":?")) {
			cmd = cmd.substring(2);
			comd = new ElseIfCommand();
			((ElseIfCommand)comd).cond = new FindCommand(cmd);
		} else if (cmd.startsWith(":")) {
			cmd = cmd.substring(1);
			comd = new ElseCommand();
		} else if (cmd.startsWith("#provider")) {
            cmd = cmd.substring(9).trim();
            if(cmd.isEmpty()) {
                //exception
            }
            comd = new ProviderLoopCommand(cmd.trim());
        } else if (cmd.startsWith("#transient-provider")) {
            cmd = cmd.substring(19).trim();
            if(cmd.isEmpty()) {
                //exception
            }
            comd = new TransientProviderLoopCommand(cmd.trim());
        } else if (cmd.startsWith("#")) {
			cmd = cmd.substring(1);
			comd = new LoopCommand();
			((LoopCommand)comd).cond = new FindCommand(cmd);
		} else if (cmd.startsWith("[")) {
			comd = new ValueListCommand();
			((ValueListCommand)comd).type = "[";
		} else if (cmd.startsWith("]")) {
			comd = new EndCommand();
			((EndCommand)comd).type = "]";
		} else if (cmd.startsWith("{")) {
			comd = new StartCommand();
			((StartCommand)comd).type = "{";
		} else if (cmd.startsWith("}")) {
			comd = new EndCommand();
			((EndCommand)comd).type = "}";
		} else if (cmd.toLowerCase().startsWith("open ")) {
			String name = cmd.substring(5).trim();
			comd = new BrowserCommand(name);
		} /*else if (cmd.toLowerCase().startsWith("capability_set ")) {
            comd = new CapabilitySetPropertyCommand(cmd.substring(15));
        }*/ else if (cmd.toLowerCase().startsWith("goto ")) {
			String url = cmd.substring(5).trim();
			comd = new GotoCommand();
			((GotoCommand)comd).url = unsanitize(url);
		} else if (cmd.toLowerCase().startsWith("back")) {
			comd = new BackCommand();
		} else if (cmd.toLowerCase().startsWith("forward")) {
			comd = new ForwardCommand();
		} else if (cmd.toLowerCase().startsWith("refresh")) {
			comd = new RefreshCommand();
		} else if (cmd.toLowerCase().startsWith("close")) {
            comd = new CloseCommand();
        } else if (cmd.toLowerCase().startsWith("maximize")) {
			comd = new MaximizeCommand();
		} else if (cmd.toLowerCase().startsWith("window_set ")) {
			comd = new WindowSetPropertyCommand(cmd.substring(11));
		} else if (cmd.toLowerCase().startsWith("frame ")) {
            comd = new FrameCommand(cmd.substring(6));
        } else if (cmd.toLowerCase().startsWith("window ")) {
			comd = new WindowCommand(cmd.substring(7));
		} else if (cmd.toLowerCase().startsWith("sleep ")) {
			comd = new SleepCommand(cmd.substring(6));
		} else if (cmd.toLowerCase().startsWith("type ") || cmd.toLowerCase().startsWith("select ") 
                || cmd.toLowerCase().startsWith("click ") || cmd.toLowerCase().equals("click")
                || cmd.toLowerCase().startsWith("hover ") || cmd.toLowerCase().equals("hover")
                || cmd.toLowerCase().startsWith("chord ") || cmd.toLowerCase().startsWith("hoverclick ") 
                || cmd.toLowerCase().startsWith("clear ") || cmd.toLowerCase().equals("clear")
                || cmd.toLowerCase().startsWith("submit ") || cmd.toLowerCase().equals("submit")) {
			comd = handleActions(cmd, null);
		} else if (cmd.toLowerCase().startsWith("var ")) {
			comd = new VarCommand(cmd.substring(4));
		} else if (cmd.toLowerCase().startsWith("jsvar ")) {
			comd = new JsVarCommand(cmd.substring(6));
		} else if (cmd.toLowerCase().startsWith("exec ")) {
			comd = new ExecCommand(cmd.substring(5));
		} else if (cmd.toLowerCase().startsWith("execjs ")) {
			comd = new ExecJsCommand(cmd.substring(7));
		} else if (cmd.toLowerCase().startsWith("subtest ")) {
            comd = new SubTestCommand(cmd.substring(8));
        } else if (cmd.toLowerCase().startsWith("require")) {
			comd = new RequireCommand();
		} else if (cmd.toLowerCase().startsWith("import ")) {
			comd = new ImportCommand(cmd.substring(7));
		} else if (cmd.toLowerCase().startsWith("screenshot ")) {
			comd = new ScreenshotCommand(cmd.substring(11));
		} else if (cmd.toLowerCase().startsWith("ele-screenshot ")) {
			comd = new EleScreenshotCommand(cmd.substring(15));
		} else {
			comd = new ValueCommand();
			if(cmd.charAt(0)==cmd.charAt(cmd.length()-1)) {
        		if(cmd.charAt(0)=='"' || cmd.charAt(0)=='\'') {
        			cmd = cmd.substring(1, cmd.length()-1);
        		}
        	}
			((ValueCommand)comd).value = unsanitize(cmd);
		}
		return comd;
	}
	
	static Command handleActions(String cmd, FindCommand fcmd) {
	    Command comd = null;
	    if (cmd.toLowerCase().startsWith("type ")) {
            comd = new TypeCommand(cmd.substring(5));
        } else if (cmd.toLowerCase().startsWith("chord ")) {
            comd = new ChordCommand(cmd.substring(6));
        } else if (cmd.toLowerCase().startsWith("select ")) {
            comd = new SelectCommand(cmd.substring(7));
        } else if (cmd.toLowerCase().startsWith("click ") || cmd.toLowerCase().equals("click")) {
            comd = new ClickCommand();
            if(!cmd.toLowerCase().equals("click")) {
                ((ClickCommand)comd).cond = new FindCommand(cmd.substring(6));
            }
        } else if (cmd.toLowerCase().startsWith("hover ") || cmd.toLowerCase().equals("hover")) {
            comd = new HoverCommand();
            if(!cmd.toLowerCase().equals("hover")) {
                ((HoverCommand)comd).cond = new FindCommand(cmd.substring(6));
            }
        } else if (cmd.toLowerCase().startsWith("hoverclick ")) {
            comd = new HoverAndClickCommand(cmd.substring(11));
        } else if (cmd.toLowerCase().startsWith("clear ") || cmd.toLowerCase().equals("clear")) {
            comd = new ClearCommand();
            if(!cmd.toLowerCase().equals("clear")) {
                ((ClearCommand)comd).cond = new FindCommand(cmd.substring(6));
            }
        } else if (cmd.toLowerCase().startsWith("submit ") || cmd.toLowerCase().equals("submit")) {
            comd = new SubmitCommand();
            if(!cmd.toLowerCase().equals("submit")) {
                ((SubmitCommand)comd).cond = new FindCommand(cmd.substring(7));
            }
        } else if (cmd.toLowerCase().startsWith("actions ")) {
            comd = new ActionsCommand(cmd.substring(7), fcmd);
        }
	    return comd;
	}
	
	static void get(Command parent, ListIterator<String> iter) throws Exception {
		Command prev = null;
		while(iter.hasNext()) {
			Command tmp = parse(iter.next().trim());
			
			if(tmp instanceof ValueListCommand) {
				get(tmp, iter);
				prev.children.add(tmp);
			} else if(tmp instanceof StartCommand) {
				get(prev, iter);
			} else if(tmp instanceof EndCommand) {
				return;
			} else if(tmp instanceof ImportCommand) {
				List<String> commands = FileUtils.readLines(new File(((ImportCommand)tmp).name), "UTF-8");
				for (String c : commands) {
					iter.add(c);
				}
				for (@SuppressWarnings("unused") String c : commands) {
					iter.previous();
				}
			} else {
				parent.children.add(tmp);
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
	
	static Command getAll(List<String> scmds) throws Exception {
		Command tcmd = new Command();
		
		get(tcmd, scmds.listIterator());
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
		Command cmd = Command.getAll(commands);
		cmd.name = filename;
		return cmd;
	}
	
	static Command read(File file, List<String> commands, Map<String, SeleniumDriverConfig> mp) throws Exception {
	    if(commands==null) {
	        commands = new ArrayList<String>();
	    } else {
	        commands.clear();
	    }
		commands.addAll(FileUtils.readLines(file, "UTF-8"));
		Command cmd = Command.getAll(commands);
		cmd.name = file.getName();
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
	
	String fjavacode() throws Exception {
	    return new Formatter().formatSource(javacode());
	}
	
	String javacode() {
		StringBuilder b = new StringBuilder();
		for (Command c : children) {
			if(c instanceof RequireCommand) {
				String cc = c.javacode();
				b.append(cc);
				if(!cc.isEmpty()) {
					b.append("\n");
				}
			} else {
				break;
			}
		}
		b.append("package com.gatf.selenium;\n");
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
		b.append("import java.util.Map;\n");
		b.append("import org.junit.Assert;\n");
		b.append("import org.openqa.selenium.Keys;\n\n");
		b.append("public class "+className+" extends SeleniumTest implements Serializable {\n");
		b.append("public "+className+"(AcceptanceTestContext ___cxt___) {\nsuper(\""+esc(name)+"\", ___cxt___);\n}\n");
		b.append("public void quit() {\nif(get___d___()!=null)get___d___().quit();\n}\n");
		for (SeleniumDriverConfig driverConfig : mp.values())
        {
            b.append("public void setupDriver"+driverConfig.getName().toLowerCase()+"(LoggingPreferences ___lp___) throws Exception {\n");
            DriverCommand cmd = new DriverCommand(driverConfig);
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
		for (String brn : bn)
		{
		    b.append("quit();\n");
		    b.append("setupDriver"+brn+"(___lp___);\n");
		    b.append("_execute(___lp___);\n");
        }
		b.append("return get__result__();\n}\n");
		b.append("@SuppressWarnings(\"unchecked\")\npublic void _execute(LoggingPreferences ___lp___) throws Exception {\n");
		b.append("try {\n");
		b.append("SearchContext "+currvarnamesc()+" = get___d___();\n");
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
		b.append("}\ncatch(Throwable c)\n{\npushResult(new SeleniumTestResult(get___d___(), this, c, ___lp___));\n}");
		b.append("}\n}");
		return b.toString();
	}

	public static class ExecCommand extends Command {
		String code;
        public ExecCommand() {
        }
		ExecCommand(String code) {
			this.code = code;
			this.code = unsanitize(code);
		}
		String toCmd() {
			return "exec " + code;
		}
		String javacode() {
			code = code.replace("@driver", "___cw___");
			code = code.replace("@window", "___ocw___");
			code = code.replace("@element", currvarname());
			code = code.replace("@sc", currvarnamesc());
			code = code.replace("@index", "index");
			code = code.replace("@print", "System.out.println");
			return code + ";";
		}
		public String toSampleSelCmd() {
		    return "exec {statement}";
		}
	}
	
	public static class ExecJsCommand extends Command {
		String code;
        public ExecJsCommand() {
        }
		ExecJsCommand(String code) {
			code = unsanitize(code);
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
        public SubTestCommand() {
        }
        SubTestCommand(String name) {
            name = unsanitize(name);
            if(name.charAt(0)==name.charAt(name.length()-1)) {
                if(name.charAt(0)=='"' || name.charAt(0)=='\'') {
                    name = name.substring(1, name.length()-1);
                }
            }
            this.name = name;
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
            StringBuilder b = new StringBuilder();
            if(!children.isEmpty())
            {
                b.append("\nset__subtestname__(\""+esc(name)+"\");");
                b.append("\ntry {\n");
                for (Command c : children) {
                    b.append(c.javacode());
                    b.append("\n");
                }
                b.append("\npushResult(new SeleniumTestResult(get___d___(), this, ___lp___));");
                b.append("\n}\ncatch(Throwable c)\n{\npushResult(new SeleniumTestResult(get___d___(), this, c, ___lp___));");
                b.append("\n}\nfinally {\nset__subtestname__(null);\n}");
            }
            return b.toString();
        }
        public String toSampleSelCmd() {
            return "";
        }
    }
	
	public static class VarCommand extends Command {
		String name;
		FindCommand cond;
        public VarCommand() {
        }
		VarCommand(String val) {
			if(val.indexOf(" ")!=-1) {
				name = val.substring(0, val.indexOf(" ")).trim();
				cond = new FindCommand(val.substring(val.indexOf(" ")+1).trim());
			} else {
				//excep
			}
		}
		String toCmd() {
			return "var " + name + " " + cond.toCmd();
		}
		String javacode() {
			return cond.javacodeonly(null) + "\nList<WebElement> " + name + " = " + currvarname() + ";";
		}
        public String toSampleSelCmd() {
            return "var {name} {vale-expr}";
        }
	}
	
	public static class JsVarCommand extends Command {
		String name;
		String script;
        public JsVarCommand() {
        }
		JsVarCommand(String val) {
			if(val.indexOf(" ")!=-1) {
				name = val.substring(0, val.indexOf(" ")).trim();
				script = val.substring(val.indexOf(" ")+1).trim();
				script = unsanitize(script);
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
        public ScreenshotCommand() {
        }
		ScreenshotCommand(String code) {
			code = unsanitize(code);
			if(code.charAt(0)==code.charAt(code.length()-1)) {
	    		if(code.charAt(0)=='"' || code.charAt(0)=='\'') {
	    			code = code.substring(1, code.length()-1);
	    		}
	    	}
			this.fpath = code;
		}
		String toCmd() {
			return "screenshot \"" + fpath + "\"";
		}
		String javacode() {
			String sc = varnamesr();
			return "File "+sc+" = ((TakesScreenshot)___ocw___).getScreenshotAs(OutputType.FILE);\nFileUtils.copyFile("+sc+", new File(\""+esc(fpath)+"\"));";
		}
        public String toSampleSelCmd() {
            return "screenshot {target-path}";
        }
	}
	
	public static class EleScreenshotCommand extends Command {
		String fpath;
		FindCommand cond;
        public EleScreenshotCommand() {
        }
		EleScreenshotCommand(String val) {
			val = unsanitize(val);
			fpath = val.substring(0, val.indexOf(" ")).trim();
			if(fpath.charAt(0)==fpath.charAt(fpath.length()-1)) {
	    		if(fpath.charAt(0)=='"' || fpath.charAt(0)=='\'') {
	    			fpath = fpath.substring(1, fpath.length()-1);
	    		}
	    	}
			cond = new FindCommand(val.substring(val.indexOf(" ")+1).trim());
		}
		String toCmd() {
			return "ele-screenshot \"" + fpath + "\"";
		}
		String javacode() {
			StringBuilder b = new StringBuilder();
			b.append(cond.javacodeonly(children));
			b.append("\nif("+cond.condition()+")");
			b.append("\n{");
			b.append("\nWebElement ele = " +currvarname() + ".get(0);");
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
	}
	
	public static class ValueListCommand extends StartCommand {
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
			StringBuilder b = new StringBuilder();
			if(children!=null && children.size()>0 && children.get(0) instanceof ValueListCommand) {
				for (Command c : ((ValueListCommand)children.get(0)).children) {
					b.append("import " + ((ValueCommand)c).value);
					b.append(";\n");
				}
				return b.toString();
			}
			return "";
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
        public ImportCommand() {
        }
		ImportCommand(String cmd) {
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
	
	public static class ValidateCommand extends Command {
		FindCommand cond;
        public ValidateCommand() {
        }
		ValidateCommand(String time, String val) {
		    String[] parts = val.trim().split("[\t ]+");
		    cond = time.equals("0")?new FindCommand(parts[0]):new WaitAndFindCommand(parts[0], Long.valueOf(time));
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
    		            Command comd = handleActions(cmd, cond);
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
	
	public static class IfCommand extends Command {
		FindCommand cond;
		List<ElseIfCommand> elseifs = new ArrayList<ElseIfCommand>();
		ElseCommand elsecmd;
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
		static String getFp(FindCommand cond, List<Command> children) {
		    StringBuilder b = new StringBuilder();
		    b.append("new Functor<SearchContext, Boolean>() {@Override\n");
		    b.append("public Boolean f(SearchContext "+currvarnamesc()+")\n{\ntry{\n");
            b.append(cond.javacodeonly(children));
            if(!children.isEmpty())
            {
                b.append("\n");
                for (Command c : children) {
                    b.append(c.javacode());
                    b.append("\n");
                }
            }
            b.append("\nreturn "+cond.condition()+";\n}\ncatch(AssertionError e){\nSystem.out.println(e.getMessage());}\ncatch(Exception e){\nSystem.out.println(e.getMessage());}\n");
		    b.append("\nreturn false;}\n}.f("+currvarnamesc()+")");
		    return b.toString();
		}
		String javacode() {
			StringBuilder b = new StringBuilder();
			b.append("\nif("+getFp(cond, children)+"){}");
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
			b.append("\nelse if("+IfCommand.getFp(cond, children)+"){}");
			return b.toString();
		}
        public String toSampleSelCmd() {
            return ":? {find-expr}\n\t{\n\t}";
        }
	}
	
	public static class ElseCommand extends Command {
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
	
	public static class LoopCommand extends Command {
		FindCommand cond;
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
				String cvarname = currvarname();
				//pushSc();
				b.append("\nif("+cond.condition()+") {\n");
				b.append("\nfor(final WebElement " + varname() + " : " + cvarname + ") {\nint index = 0;\n");
				//b.append("final SearchContext "+currvarnamesc()+" = "+currvarname()+";");
				String vr = currvarname();
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
	}
    
    public static class ProviderLoopCommand extends Command {
        String name;
        int index = -1;
        public ProviderLoopCommand() {}
        ProviderLoopCommand(String val)
        {
            String[] parts = val.trim().split("[\t ]+");
            name = unsanitize(parts[0].trim());
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
                b.append("set__provpos__(" + index + ");\n");
                for (Command c : children) {
                    b.append(c.javacode());
                    b.append("\n");
                }
            }
            else if(!children.isEmpty())
            {
                b.append("int "+varname()+" = get___cxt___().getProviderTestDataMap().get(\""+name+"\").size();\n");
                b.append("set__provname__(\"" + name + "\");\n");
                String provname = currvarname();
                String loopname = varname();
                b.append("\nfor(int " + loopname + "=0;"+loopname+"<" + provname + ";"+loopname+"++) {\n");
                b.append("set__provpos__(" + loopname + ");\n");
                for (Command c : children) {
                    b.append(c.javacode());
                    b.append("\n");
                }
                b.append("}");
            }
            return b.toString();
        }
        public String toSampleSelCmd() {
            return "#provider {find-expr}\n\t{\n\t}";
        }
    }
	
    public static class TransientProviderLoopCommand extends ValueCommand {
        FindCommand cond;
        String varname;
        public TransientProviderLoopCommand() {}
        TransientProviderLoopCommand(String val)
        {
            String[] parts = val.trim().split("[\t ]+");
            if(parts.length>=4) {
                parts[0] = parts[0].trim();
                value = parts[0];
                value = unsanitize(value);
                if(value.charAt(0)==value.charAt(value.length()-1)) {
                    if(value.charAt(0)=='"' || value.charAt(0)=='\'') {
                        value = value.substring(1, value.length()-1);
                    }
                }
                varname = parts[1];
                varname = unsanitize(varname);
                if(varname.charAt(0)==varname.charAt(varname.length()-1)) {
                    if(varname.charAt(0)=='"' || varname.charAt(0)=='\'') {
                        varname = varname.substring(1, varname.length()-1);
                    }
                }
                cond = new FindCommand(parts[2].trim() + " " + parts[3].trim());
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
            b.append("get___cxt___().newProvider(\""+value+"\");\n");
            b.append(cond.javacodeonly(children));
            String provname = cond.rtl;
            String loopname = varname();
            List<String> ssl = Arrays.asList(varname.split(","));
            b.append("\nfor(int " + loopname + "=0;"+loopname+"<" + provname + ".size();"+loopname+"++) {\n");
            for (int i=0;i<ssl.size();i++)
            {
                b.append("get___cxt___().getProviderTestDataMap().get(\""+value+"\").put(\""+ssl.get(i)+"\", " + provname + ".get(" + loopname + ")["+i+"]);\n");
            }
            b.append("}");
            return b.toString();
        }
        public String toSampleSelCmd() {
            return "#transient-provider {find-expr} {sub-selector}";
        }
    }
    
	public static class DriverCommand extends Command {
		SeleniumDriverConfig config;
        public DriverCommand() {
        }
        DriverCommand(SeleniumDriverConfig config) {
		    this.config = config;
        }
		String name() {
			return name;
		}

        String toCmd() {
			return "";
		}
		String javacode() {
			pushSc();
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
                b.append("DesiredCapabilities ___dc___ = DesiredCapabilities."+config.getName().toLowerCase()+"();\n");
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
                b.append("DesiredCapabilities ___dc___ = DesiredCapabilities.internetExplorer();\n");
                b.append("___dc___.setCapability(CapabilityType.LOGGING_PREFS, ___lp___);\n");
                if(config.getCapabilities()!=null)
                {
                    for (Map.Entry<String, String> e : config.getCapabilities().entrySet())
                    {
                        b.append("___dc___.setCapability(\""+esc(e.getKey())+"\", \""+esc(e.getValue())+"\");\n");
                    }
                }
                b.append("set___d___(new org.openqa.selenium.safari.SafariDriver(___dc___));\n");
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
                b.append("DesiredCapabilities ___dc___ = new DesiredCapabilities(BrowserType.ANDROID, \""+config.getVersion()+"\", Platform.ANDROID);\n");
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
                //b.append("DesiredCapabilities ___dc___ = new DesiredCapabilities(BrowserType.ANDROID, \""+version+"\", Platform.ANDROID);\n");
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
        public BrowserCommand() {
        }
        BrowserCommand(String val) {
            String[] parts = val.trim().split("[\t ]+");
            if(parts.length>=1) {
                name = unsanitize(parts[0].trim());
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
        public FrameCommand() {
        }
        FrameCommand(String cmd) {
			name = cmd;
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
					name = unsanitize(name);
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
        public WindowCommand() {
        }
        WindowCommand(String cmd) {
            name = cmd;
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
                    String whl = "ArrayList<String> "+varname()+" = new ArrayList<String> (___ocw___.getWindowHandles());\n"
                            + "if("+varname()+"!=null && "+index+">=0 && "+varname()+".size()>"+index+")\n{\n";
                    return whl + "___cw___ = ___ocw___.switchTo().window(\""+esc(name)+"\");\n}\n___sc___1 = ___cw___;";
                } catch (Exception e) {
                    name = unsanitize(name);
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
        public String toSampleSelCmd() {
            return "goto {url}";
        }
	}
	
	public static class WindowSetPropertyCommand extends Command {
		String type;
		int value;
        public WindowSetPropertyCommand() {
        }
		WindowSetPropertyCommand(String val) {
			String[] parts = val.trim().split("[\t ]+");
			if(parts.length>1) {
				parts[0] = parts[0].trim();
				type = parts[0];
				value = Integer.valueOf(unsanitize(parts[1].trim()));
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
			String cvn = varname();
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
        public CapabilitySetPropertyCommand() {
        }
        CapabilitySetPropertyCommand(String val) {
            String[] parts = val.trim().split("[\t ]+");
            if(parts.length>1) {
                parts[0] = parts[0].trim();
                type = parts[0];
                value = unsanitize(parts[1].trim());
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
        public FindCommand() {
            
        }
		FindCommand(String val) {
			String[] parts = val.trim().split("[\t ]+");
			if(parts.length>=1) {
				parts[0] = parts[0].trim();
				if(parts[0].indexOf("@")!=-1) {
					by = parts[0].substring(0, parts[0].indexOf("@"));
					classifier = parts[0].substring(parts[0].indexOf("@")+1);
		        	classifier = unsanitize(classifier);
		        	if(classifier.charAt(0)==classifier.charAt(classifier.length()-1)) {
		        		if(classifier.charAt(0)=='"' || classifier.charAt(0)=='\'') {
		        			classifier = classifier.substring(1, classifier.length()-1);
		        		}
		        	}
		        	
		        	if(parts.length>1) {
		        		subselector = parts[1].trim();
		        		subselector = unsanitize(subselector);
		        	}
		        	if(parts.length>2) {
		        		String rhs = parts[2].trim();
		        		rhs = unsanitize(rhs);
		        		if(rhs.startsWith("=")) {
		        			ValueCommand vc = new ValueCommand();
		        			vc.value = rhs.substring(1);
		        			if(vc.value.charAt(0)=='"' || vc.value.charAt(0)=='\'') {
		        				vc.value = vc.value.substring(1, vc.value.length()-1);
			        		}
		        			children.add(vc);
		        		}
		        	}
				} else {
					by = parts[0];
					subselector = unsanitize(by);
					if(parts.length>1) {
						String rhs = parts[1].trim();
		        		rhs = unsanitize(rhs);
		        		if(rhs.startsWith("=")) {
		        			ValueCommand vc = new ValueCommand();
		        			vc.value = rhs.substring(1);
		        			if(vc.value.charAt(0)=='"' || vc.value.charAt(0)=='\'') {
		        				vc.value = vc.value.substring(1, vc.value.length()-1);
			        		}
		        			children.add(vc);
		        		}
					}
				}
				by = unsanitize(by);
			} else {
				//excep
			}
		}
		String toCmd() {
			return " " + by + "@\"" + classifier + "\"" + (StringUtils.isEmpty(subselector)?"":" \"" + subselector + "\"");
		}
		String javacodeonly(List<Command> children) {
			StringBuilder b = new StringBuilder();
			topele = varname();
			if(by.equalsIgnoreCase("id")) {
				b.append("List<WebElement>  " + topele + " = By.id(evaluate(\""+esc(classifier)+"\")).findElements("+currvarnamesc()+");");
			} else if(by.equalsIgnoreCase("name")) {
				b.append("List<WebElement>  " + topele + " = By.name(evaluate(\""+esc(classifier)+"\")).findElements("+currvarnamesc()+");");
			} else if(by.equalsIgnoreCase("class") || by.equalsIgnoreCase("className")) {
				b.append("List<WebElement>  " + topele + " = By.className(evaluate(\""+esc(classifier)+"\")).findElements("+currvarnamesc()+");");
			} else if(by.equalsIgnoreCase("tag") || by.equalsIgnoreCase("tagname")) {
				b.append("List<WebElement>  " + topele + " = By.tagName(evaluate(\""+esc(classifier)+"\")).findElements("+currvarnamesc()+");");
			} else if(by.equalsIgnoreCase("xpath")) {
				b.append("List<WebElement>  " + topele + " = By.xpath(evaluate(\""+esc(classifier)+"\")).findElements("+currvarnamesc()+");");
			} else if(by.equalsIgnoreCase("cssselector") || by.equalsIgnoreCase("css")) {
				b.append("List<WebElement>  " + topele + " = By.cssSelector(evaluate(\""+esc(classifier)+"\")).findElements("+currvarnamesc()+");");
			} else if(by.equalsIgnoreCase("text")) {
				b.append("List<WebElement>  " + topele + " = By.xpath(\"//*[contains(text(), '"+esc(classifier)+"')]\").findElements("+currvarnamesc()+");");
			} else if(by.equalsIgnoreCase("linkText")) {
				b.append("List<WebElement>  " + topele + " = By.linkText(evaluate(\""+esc(classifier)+"\")).findElements("+currvarnamesc()+");");
			} else if(by.equalsIgnoreCase("partialLinkText")) {
				b.append("List<WebElement>  " + topele + " = By.partialLinkText(evaluate(\""+esc(classifier)+"\")).findElements("+currvarnamesc()+");");
			} else if(by.equalsIgnoreCase("active")) {
				b.append("\n@SuppressWarnings(\"serial\")");
				b.append("List<WebElement>  " + topele + " = new ArrayList<WebElement>(){{add(___cw___.activeElement());}};");
			}
			if(this instanceof WaitAndFindCommand) {
			    b.append("\nif("+topele+"==null || "+topele+".isEmpty())return false;");
			} else {
			    b.append("\nAssert.assertTrue(\"Element not found\", "+topele+"!=null && !"+topele+".isEmpty());");
			}
			
			if(this.children!=null && this.children.size()>0) {
				children = this.children;
			}
			if(children!=null && children.size()>0 && subselector!=null && !subselector.isEmpty()) {
				Command c = children.get(0);
				if(c instanceof ValueCommand) {
					String value = ((ValueCommand)c).value;
					String cvarname = currvarname();
					condvar = condvarname();
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
						}
					}
					
					b.append("\nfor(final WebElement " + varname() + " : " + cvarname + ")\n{");
					if(subselector.equalsIgnoreCase("text")) {
						b.append("\n" + condvar + " &= evaluate(\""+esc(value)+"\").equals(" + currvarname() + ".getText());");
					} else if(subselector.equalsIgnoreCase("tagname")) {
						b.append("\n" + condvar + " &= evaluate(\""+esc(value)+"\").equalsIgnoreCase(" + currvarname() + ".getTagName());");
					} else if(subselector.toLowerCase().startsWith("attr@")) {
						String atname = subselector.substring(5);
						if(atname.charAt(0)=='"' || atname.charAt(0)=='\'') {
						    atname = atname.substring(1, atname.length()-1);
                        }
						b.append("\n" + condvar + " &= evaluate(\""+esc(value)+"\").equals(" + currvarname() + ".getAttribute(\""+esc(atname)+"\"));");
					} else if(subselector.toLowerCase().startsWith("cssvalue@")) {
						String atname = subselector.substring(9);
						if(atname.charAt(0)=='"' || atname.charAt(0)=='\'') {
                            atname = atname.substring(1, atname.length()-1);
                        }
						b.append("\n" + condvar + " &= evaluate(\""+esc(value)+"\").equals(" + currvarname() + ".getCssValue(\""+esc(atname)+"\"));");
					} else if(subselector.equalsIgnoreCase("width")) {
						b.append("\n" + condvar + " &= evaluate(\""+esc(value)+"\").equals(String.valueOf("+currvarname()+".getSize().getWidth()));");
					} else if(subselector.equalsIgnoreCase("height")) {
						b.append("\n" + condvar + " &= evaluate(\""+esc(value)+"\").equals(String.valueOf("+currvarname()+".getSize().getHeight()));");
					} else if(subselector.equalsIgnoreCase("xpos")) {
						b.append("\n" + condvar + " &= evaluate(\""+esc(value)+"\").equals(String.valueOf("+currvarname()+".getPosition().getX()));");
					} else if(subselector.equalsIgnoreCase("ypos")) {
						b.append("\n" + condvar + " &= evaluate(\""+esc(value)+"\").equals(String.valueOf("+currvarname()+".getPosition().getY()));");
					}
					b.append("\n}");
				} else if(c instanceof ValueListCommand) {
					List<String> values = ((ValueListCommand)c).getValues();
					String cvarname = currvarname();
					condvar = condvarname();
					b.append("\nboolean " + condvar + " = "+cvarname+"!=null && "+cvarname+".size()>0 && "+String.valueOf(values!=null && values.size()>0)+";");
					b.append("\nif(!("+condvar+" && "+values.size()+"=="+cvarname+".size())) "+condvar+"=false;\nelse\n{");
					for (int i=0;i<values.size();i++) {
						String value = values.get(i);
						b.append("\nfinal WebElement " + varname() + " = "+cvarname+".get("+i+");");
						if(subselector.equalsIgnoreCase("text")) {
							b.append("\n" + condvar + " &= evaluate(\""+esc(value)+"\").equals(" + currvarname() + ".getText());");
						} else if(subselector.equalsIgnoreCase("tagname")) {
							b.append("\n" + condvar + " &= evaluate(\""+esc(value)+"\").equalsIgnoreCase(" + currvarname() + ".getTagName());");
						} else if(subselector.toLowerCase().startsWith("attr@")) {
							String atname = subselector.substring(5);
							if(atname.charAt(0)=='"' || atname.charAt(0)=='\'') {
	                            atname = atname.substring(1, atname.length()-1);
	                        }
							b.append("\n" + condvar + " &= evaluate(\""+esc(value)+"\").equals(" + currvarname() + ".getAttribute(\""+esc(atname)+"\"));");
						} else if(subselector.toLowerCase().startsWith("cssvalue@")) {
							String atname = subselector.substring(9);
							if(atname.charAt(0)=='"' || atname.charAt(0)=='\'') {
	                            atname = atname.substring(1, atname.length()-1);
	                        }
							b.append("\n" + condvar + " &= evaluate(\""+esc(value)+"\").equals(" + currvarname() + ".getCssValue(\""+esc(atname)+"\"));");
						} else if(subselector.equalsIgnoreCase("width")) {
							b.append("\n" + condvar + " &= evaluate(\""+esc(value)+"\").equals(String.valueOf("+currvarname()+".getSize().getWidth()));");
						} else if(subselector.equalsIgnoreCase("height")) {
							b.append("\n" + condvar + " &= evaluate(\""+esc(value)+"\").equals(String.valueOf("+currvarname()+".getSize().getHeight()));");
						} else if(subselector.equalsIgnoreCase("xpos")) {
							b.append("\n" + condvar + " &= evaluate(\""+esc(value)+"\").equals(String.valueOf("+currvarname()+".getPosition().getX()));");
						} else if(subselector.equalsIgnoreCase("ypos")) {
							b.append("\n" + condvar + " &= evaluate(\""+esc(value)+"\").equals(String.valueOf("+currvarname()+".getPosition().getY()));");
						}
					}
					b.append("\n}");
				}
			} else if(children!=null && children.size()>0) {
			    Command c = children.get(0);
			    if(c instanceof ClickCommand || c instanceof HoverCommand || c instanceof HoverAndClickCommand
                        || c instanceof ClearCommand || c instanceof SubmitCommand || c instanceof TypeCommand
                        || c instanceof SelectCommand || c instanceof ChordCommand) {
                    b.append(c.selcode(topele));
                } else if(c instanceof ActionsCommand) {
                    b.append(c.javacode());
                }
			} else if(subselector!=null) {
                @SuppressWarnings("unchecked")
                List<String> ssl = Arrays.asList(subselector.split(","));
                rtl = varname();
                b.append("\nList<String[]> "+rtl+" = new java.util.ArrayList<String[]>();");
                b.append("\nfor(final WebElement " + varname() + " : " + topele + ")\n{");
                b.append("\nString[] __t = new String["+ssl.size()+"];");
                for (int i=0;i<ssl.size();i++)
                {
                    if(ssl.get(i).equalsIgnoreCase("text")) {
                        b.append("\n__t["+i+"] = " + currvarname() + ".getText();");
                    } else if(ssl.get(i).equalsIgnoreCase("tagname")) {
                        b.append("\n__t["+i+"] = " + currvarname() + ".getTagName();");
                    } else if(ssl.get(i).toLowerCase().startsWith("attr@")) {
                        String atname = ssl.get(i).substring(5);
                        if(atname.charAt(0)=='"' || atname.charAt(0)=='\'') {
                            atname = atname.substring(1, atname.length()-1);
                        }
                        b.append("\n__t["+i+"] = " + currvarname() + ".getAttribute(\""+esc(atname)+"\");");
                    } else if(ssl.get(i).toLowerCase().startsWith("cssvalue@")) {
                        String atname = ssl.get(i).substring(9);
                        if(atname.charAt(0)=='"' || atname.charAt(0)=='\'') {
                            atname = atname.substring(1, atname.length()-1);
                        }
                        b.append("\n__t["+i+"] = " + currvarname() + ".getCssValue(\""+esc(atname)+"\");");
                    } else if(ssl.get(i).equalsIgnoreCase("width")) {
                        b.append("\n__t["+i+"] = String.valueOf("+currvarname()+".getSize().getWidth());");
                    } else if(ssl.get(i).equalsIgnoreCase("height")) {
                        b.append("\n__t["+i+"] = String.valueOf("+currvarname()+".getSize().getHeight());");
                    } else if(ssl.get(i).equalsIgnoreCase("xpos")) {
                        b.append("\n__t["+i+"] = String.valueOf("+currvarname()+".getPosition().getX());");
                    } else if(ssl.get(i).equalsIgnoreCase("ypos")) {
                        b.append("\n__t["+i+"] = String.valueOf("+currvarname()+".getPosition().getY());");
                    }
                }
                b.append("\n" + rtl + ".add(__t);");
                b.append("\n}");
            }
			
			if(condvar==null) {
				String cvarname = topele;
				condvar = condvarname();
				if(subselector!=null && !subselector.isEmpty())
				{
					b.append("\nboolean " + condvar + " = true;");
					b.append("\nfor(final WebElement " + varname() + " : " + cvarname + ")\n{");
					if(subselector.equalsIgnoreCase("selected")) {
						b.append("\n" + condvar + " &= " + cvarname + ".isSelected();");
					} else if(subselector.equalsIgnoreCase("enabled")) {
						b.append("\n" + condvar + " &= " + cvarname + ".isEnabled();");
					} else if(subselector.equalsIgnoreCase("visible")) {
						b.append("\n" + condvar + " &= " + cvarname + ".isDisplayed();");
					}
					b.append("\n}");
				}
				else
				{
					//b.append("\nboolean " + condvar + " = "+ cvarname+"!=null && !"+cvarname+".isEmpty();");
				}
			}
			return b.toString();
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
        public WaitAndFindCommand() {
            
        }
		WaitAndFindCommand(String val, long wf) {
			super(val);
			waitfor = wf;
		}
		String toCmd() {
			return waitfor + " " + by + "@\"" + classifier + "\"" + (StringUtils.isEmpty(subselector)?"":" \"" + subselector + "\"");
		}
		String javacode() {
			return "";
		}
		String javacodeonly(List<Command> children) {
			String tsc = currvarnamesc();
			pushSc();
			String wfte = varname();
			String v = "final Object[]  " + wfte + " = new Object[1];\n"
			        + "final WebDriver "+currvarnamesc()+" = (WebDriver)"+tsc+";\n(new WebDriverWait(___cw___, "+waitfor+")).until(" +
				"\nnew Function<WebDriver, Boolean>(){"+
					"\npublic Boolean apply(WebDriver input) {\n"+
						super.javacodeonly(children) +
						"\n" + wfte + "[0] = " + topele() + ";\n" +
						"\nreturn "+super.condition()+";" +
					"\n}"+
					"\npublic String toString() {"+
					"\nreturn \"\";" +
					"\n}"+
				"\n});";
			prevvarnamesc();
			topele = wfte;
			return v;
		}
	}
	
	public static class StartCommand extends Command {
		String type;
		String toCmd() {
			return type;
		}
	}
	
	public static class EndCommand extends Command {
		String type;
		String toCmd() {
			return type;
		}
	}
	
	public static class TypeCommand extends ValueCommand {
		FindCommand cond;
        public TypeCommand() {
        }
		TypeCommand(String val) {
			String[] parts = val.trim().split("[\t ]+");
			if(parts.length>0) {
				parts[0] = parts[0].trim();
				value = parts[0];
				value = unsanitize(value);
				if(value.charAt(0)==value.charAt(value.length()-1)) {
	        		if(value.charAt(0)=='"' || value.charAt(0)=='\'') {
	        			value = value.substring(1, value.length()-1);
	        		}
	        	}
				if(parts.length>1)cond = new FindCommand(parts[1].trim());
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
			b.append("\n"+currvarname()+".get(0).sendKeys(evaluate(\""+esc(value)+"\"));");
			return b.toString();
		}
		String selcode(String varnm) {
		    if(varnm==null) {
		        varnm = currvarname();
		    }
		    return "\n"+varnm+".get(0).sendKeys(evaluate(\""+esc(value)+"\"));";
		}
        public String toSampleSelCmd() {
            return "type {text}";
        }
	}
    
    public static class ChordCommand extends ValueCommand {
        FindCommand cond;
        List<String> values = new ArrayList<String>();
        public ChordCommand() {
        }
        ChordCommand(String val) {
            String[] parts = val.trim().split("[\t ]+");
            if(parts.length>0) {
                parts[0] = parts[0].trim();
                value = parts[0];
                String temp = org.apache.commons.lang.StringEscapeUtils.unescapeJava(parts[0]);
                try
                {
                    if(parts.length>1)cond = new FindCommand(parts[1].trim());
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
                b.append("\n"+currvarname()+".get(0).sendKeys(Keys.chord("+chs+"));");
            }
            return b.toString();
        }
        String selcode(String varnm) {
            if(varnm==null) {
                varnm = currvarname();
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
    
    public static class SelectCommand extends ValueCommand {
        FindCommand cond;
        String by;
        public SelectCommand() {
        }
        SelectCommand(String val) {
            String[] parts = val.trim().split("[\t ]+");
            if(parts.length>0) {
                parts[0] = parts[0].trim();
                by = parts[0];
                if(by.indexOf("@")!=-1) {
                    value = by.split("@")[1];
                    by = by.split("@")[0];
                    value = unsanitize(value);
                }
                if(parts.length>1)cond = new FindCommand(parts[1].trim());
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
            String cvarnm = currvarname();
            String selvrnm = varname();
            b.append("\nSelect "+selvrnm+" = new Select("+cvarnm+".get(0));");
            if(by.equalsIgnoreCase("text")) {
                b.append("\n"+selvrnm+".selectByVisibleText(evaluate(\""+value+"\"));");
            } else if(by.equalsIgnoreCase("index")) {
                b.append("\n"+selvrnm+".selectByIndex("+value+");"); 
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
                varnm = currvarname();
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
    
    public static class HoverCommand extends Command {
        FindCommand cond;
        String toCmd() {
            return "hover" + (cond!=null?cond.toCmd():"");
        }
        String javacode() {
            StringBuilder b = new StringBuilder();
            if(cond!=null) {
                b.append(cond.javacodeonly(children));
                //b.append("\nAssert.assertTrue("+cond.condition()+");");
            }
            String cvarnm = currvarname();
            String hvvrnm = varname();
            b.append("\nActions "+hvvrnm+" = new Actions(get___d___());");
            b.append("\n"+hvvrnm+".moveToElement("+cvarnm+".get(0)).perform();");
            return b.toString();
        }
        String selcode(String varnm) {
            if(varnm==null) {
                varnm = currvarname();
            }
            String hvvrnm = varname();
            StringBuilder b = new StringBuilder();
            b.append("\nActions "+hvvrnm+" = new Actions(get___d___());");
            b.append("\n"+hvvrnm+".moveToElement("+varnm+".get(0)).perform();");
            return b.toString();
        }
        public String toSampleSelCmd() {
            return "hover {find-expr}";
        }
    }
    
    public static class HoverAndClickCommand extends Command {
        FindCommand condHe;
        WaitAndFindCommand condCe;
        public HoverAndClickCommand() {
        }
        HoverAndClickCommand(String val) {
            String[] t = val.trim().split("[\t ]+");
            if(t.length>0) {
                condHe = new FindCommand(t[0]);
            }
            if(t.length>1) {
                condCe = new WaitAndFindCommand(t[1], 10000);
            }
        }
        String toCmd() {
            return "hoverclick " + (condHe!=null?condHe.toCmd():"") +  (condCe!=null?(" "+condCe.toCmd()):"");
        }
        String javacode() {
            StringBuilder b = new StringBuilder();
            if(condHe!=null) {
                b.append(condHe.javacodeonly(children));
                //b.append("\nAssert.assertTrue("+condHe.condition()+");");
            }
            String hevarnm = currvarname();
            if(condCe!=null) {
                b.append(condCe.javacodeonly(children));
            }
            String cevarnm = condCe.topele();
            String hvvrnm = varname();
            b.append("\nActions "+hvvrnm+" = new Actions(get___d___());");
            b.append("\n"+hvvrnm+".moveToElement("+hevarnm+".get(0)).click(((List<WebElement>)"+cevarnm+"[0]).get(0)).perform();");
            return b.toString();
        }
        String selcode(String varnm) {
            if(varnm==null) {
                varnm = currvarname();
            }
            String hvvrnm = varname();
            StringBuilder b = new StringBuilder();
            b.append("\nActions "+hvvrnm+" = new Actions(get___d___());");
            b.append("\n"+hvvrnm+".moveToElement("+varnm+".get(0)).click().perform();");
            return b.toString();
        }
        public String toSampleSelCmd() {
            return "hoverclick {find-expr}";
        }
    }
    
    public static class ActionsCommand extends Command {
        String action;
        String expr1;
        String expr2;
        FindCommand fcmd;
        
        public ActionsCommand() {
        }
        ActionsCommand(String val, FindCommand fcmd) {
            String[] t = val.trim().split("[\t ]+");
            int tl = t.length, counter = 0;
            ActionsCommand cmd = this;
            this.fcmd = fcmd;
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
                        cmd.expr1 = unsanitize(t[++counter]);
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
                        cmd.expr1 = unsanitize(t[++counter]);
                        if(cmd.expr1.charAt(0)==cmd.expr1.charAt(cmd.expr1.length()-1)) {
                            if(cmd.expr1.charAt(0)=='"' || cmd.expr1.charAt(0)=='\'') {
                                cmd.expr1 = cmd.expr1.substring(1, cmd.expr1.length()-1);
                            }
                        }
                        cmd.expr2 = unsanitize(t[++counter]);
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
                cmd.children.add(new ActionsCommand());
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
                String ck = unsanitize(expr1);
                char key = ck.charAt(0);
                b.append("\n"+acnm+"."+action+"(Keys.getKeyFromUnicode('"+key+"'));");
            } else if(action.matches("sendKeys")) {
                String ck = unsanitize(expr1);
                b.append("\n"+acnm+"."+action+"(evaluate(\""+ck+"\"));");
            } else if(action.matches("moveToElement")) {
                String ck = unsanitize(expr1);
                FindCommand fc = new FindCommand(ck);
                b.append("\n"+fc.javacodeonly(null));
                //b.append("\nAssert.assertTrue("+fc.condition()+");");
                String cvarnm = currvarname();
                b.append("\n"+acnm+"."+action+"("+cvarnm+".get(0));");
            } else if(action.matches("dragAndDrop")) {
                String ck = unsanitize(expr1);
                FindCommand fc = new FindCommand(ck);
                b.append("\n"+fc.javacodeonly(null));
                //b.append("\nAssert.assertTrue("+fc.condition()+");");
                String cvarnm = currvarname();
                String ck1 = unsanitize(expr1);
                FindCommand fc1 = new FindCommand(ck1);
                b.append("\n"+fc1.javacodeonly(null));
                //b.append("\nAssert.assertTrue("+fc1.condition()+");");
                String cvarnm1 = currvarname();
                b.append("\n"+acnm+"."+action+"("+cvarnm+".get(0), "+cvarnm1+".get(0));");
            } else if(action.matches("moveByOffset")) {
                try {
                    int ck = Integer.parseInt(unsanitize(expr1));
                    int ck1 = Integer.parseInt(unsanitize(expr2));
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
            String acnm = varname();
            b.append("\nActions "+acnm+" = new Actions(get___d___());\n"+acnm+".moveToElement("+fcmd.topele+".get(0));");
            b.append(_javacode(acnm));
            b.append("\n"+acnm+".build().perform();");
            return b.toString();
        }
    }
    
    public static class SleepCommand extends Command {
        long ms;
        public SleepCommand() {
        }
        SleepCommand(String val) {
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
            return "\nThread.sleep("+ms+");";
        }
        public String toSampleSelCmd() {
            return "sleep {time-in-ms}";
        }
    }
	
	public static class ClickCommand extends Command {
		FindCommand cond;
		String toCmd() {
			return "click" + (cond!=null?cond.toCmd():"");
		}
		String javacode() {
			StringBuilder b = new StringBuilder();
			if(cond!=null) {
				b.append(cond.javacodeonly(children));
				//b.append("\nAssert.assertTrue("+cond.condition()+");");
			}
			b.append("\n"+currvarname()+".get(0).click();");
			return b.toString();
		}
        String selcode(String varnm) {
            if(varnm==null) {
                varnm = currvarname();
            }
            return "\n"+varnm+".get(0).click();";
        }
        public String toSampleSelCmd() {
            return "click {find-expr}";
        }
	}
	
	public static class ClearCommand extends Command {
		FindCommand cond;
		String toCmd() {
			return "clear" + (cond!=null?cond.toCmd():"");
		}
		String javacode() {
			StringBuilder b = new StringBuilder();
			if(cond!=null) {
				b.append(cond.javacodeonly(children));
				//b.append("\nAssert.assertTrue("+cond.condition()+");");
			}
			b.append("\n"+currvarname()+".get(0).clear();");
			return b.toString();
		}
        String selcode(String varnm) {
            if(varnm==null) {
                varnm = currvarname();
            }
            return "\n"+varnm+".get(0).clear();";
        }
        public String toSampleSelCmd() {
            return "clear {find-expr}";
        }
	}
	
	public static class SubmitCommand extends Command {
		FindCommand cond;
		String toCmd() {
			return "submit" + (cond!=null?cond.toCmd():"");
		}
		String javacode() {
			StringBuilder b = new StringBuilder();
			if(cond!=null) {
				b.append(cond.javacodeonly(children));
				//b.append("\nAssert.assertTrue("+cond.condition()+");");
			}
			b.append("\n"+currvarname()+".get(0).submit();");
			return b.toString();
		}
        String selcode(String varnm) {
            if(varnm==null) {
                varnm = currvarname();
            }
            return "\n"+varnm+".get(0).submit();";
        }
        public String toSampleSelCmd() {
            return "submit {find-expr}";
        }
	}
}
