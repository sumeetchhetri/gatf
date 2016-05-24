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

public class Command {
	static int NUMBER = 1;
	static int NUMBER_COND = 1;
	static int NUMBER_SR = 1;
	
	static int NUMBER_SC = 1;
	static Stack<String> stck = new Stack<String>();
	static String sc = null, psc = null;
	
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
			comd = new ValidateCommand();
			((ValidateCommand)comd).cond = time.equals("0")?new FindCommand(cmd):new WaitAndFindCommand(cmd, Long.valueOf(time));
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
		} else if (cmd.toLowerCase().startsWith("capability_set ")) {
            comd = new CapabilitySetPropertyCommand(cmd.substring(15));
        } else if (cmd.toLowerCase().startsWith("goto ")) {
			String url = cmd.substring(5).trim();
			comd = new GotoCommand();
			((GotoCommand)comd).url = unsanitize(url);
		} else if (cmd.toLowerCase().startsWith("back")) {
			comd = new BackCommand();
		} else if (cmd.toLowerCase().startsWith("forward")) {
			comd = new ForwardCommand();
		} else if (cmd.toLowerCase().startsWith("refresh")) {
			comd = new RefreshCommand();
		} else if (cmd.toLowerCase().startsWith("maximize")) {
			comd = new MaximizeCommand();
		} else if (cmd.toLowerCase().startsWith("window_set ")) {
			comd = new WindowSetPropertyCommand(cmd.substring(11));
		} else if (cmd.toLowerCase().startsWith("window ")) {
			comd = new WindowCommand(cmd.substring(7));
		} else if (cmd.toLowerCase().startsWith("type ")) {
			comd = new TypeCommand(cmd.substring(5));
		} else if (cmd.toLowerCase().startsWith("click ") || cmd.toLowerCase().equals("click")) {
			comd = new ClickCommand();
			if(!cmd.toLowerCase().equals("click")) {
				((ClickCommand)comd).cond = new FindCommand(cmd.substring(6));
			}
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
		} else if (cmd.toLowerCase().startsWith("var ")) {
			comd = new VarCommand(cmd.substring(4));
		} else if (cmd.toLowerCase().startsWith("jsvar ")) {
			comd = new JsVarCommand(cmd.substring(6));
		} else if (cmd.toLowerCase().startsWith("exec ")) {
			comd = new ExecCommand(cmd.substring(5));
		} else if (cmd.toLowerCase().startsWith("execjs ")) {
			comd = new ExecJsCommand(cmd.substring(7));
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
		return cmd;
	}
	
	static Command read(File file) throws Exception {
		List<String> commands = FileUtils.readLines(file, "UTF-8");
		Command cmd = Command.getAll(commands);
		return cmd;
	}
	
	String toCmd() {
		StringBuilder b = new StringBuilder();
		for (Command c : children) {
			b.append(c.toCmd());
			b.append("\n");
		}
		return b.toString();
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
		b.append("import org.openqa.selenium.Dimension;\n");
		b.append("import org.openqa.selenium.Point;\n");
		b.append("import org.openqa.selenium.By;\n");
		b.append("import org.openqa.selenium.support.ui.WebDriverWait;\n");
		b.append("import com.google.common.base.Function;\n");
		b.append("import javax.imageio.ImageIO;\n");
		b.append("import java.awt.image.BufferedImage;\n");
		b.append("import org.apache.commons.io.FileUtils;\n");
		b.append("import java.io.File;\n");
		b.append("import org.junit.Assert;\n\n");
		b.append("public class "+className+" implements SeleniumTest, Serializable {\nprivate WebDriver ___d___ = null;\n");
		b.append("public void quit() {\nif(___d___!=null)___d___.quit();\n}\n");
		b.append("public Logs execute(AcceptanceTestContext ___cxt___, LoggingPreferences ___lp___) throws Exception {\n");
		for (Command c : children) {
			if(c instanceof RequireCommand) {
			} else if(c instanceof BrowserCommand) {
				String cc = c.javacode();
				b.append(cc);
				if(!cc.isEmpty()) {
					b.append("\n");
				}
				break;
			} else {
				break;
			}
		}
		b.append("try\n{\n");
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
		b.append("Logs ___logs___ = ___d___.manage().logs();\nreturn ___logs___;\n");
		b.append("}\ncatch(Throwable c)\n{\nthrow new SeleniumException(___d___, c);\n}");
		b.append("}\n}");
		return b.toString();
	}

	static class ExecCommand extends Command {
		String code;
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
			return code + ";";
		}
	}
	
	static class ExecJsCommand extends Command {
		String code;
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
			return "if (___ocw___ instanceof JavascriptExecutor) {\n((JavascriptExecutor)___ocw___).executeScript(\""+esc(code)+"\");\n}";
		}
	}
	
	static class VarCommand extends Command {
		String name;
		FindCommand cond;
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
	}
	
	static class JsVarCommand extends Command {
		String name;
		String script;
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
					name + " = ((JavascriptExecutor)___ocw___).executeScript(\""+esc(script)+"\");\n}";
		}
	}
	
	static class ScreenshotCommand extends Command {
		String fpath;
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
	}
	
	static class EleScreenshotCommand extends Command {
		String fpath;
		FindCommand cond;
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
	}
	
	static class ValueCommand extends Command {
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
	}
	
	static class ValueListCommand extends StartCommand {
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
	}
	
	static class RequireCommand extends Command {
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
	}
	
	static class ImportCommand extends Command {
		String name;
		ImportCommand(String cmd) {
			name = cmd;
		}
		String toCmd() {
			return "import " + name;
		}
		String javacode() {
			return "";
		}
	}
	
	static class ValidateCommand extends Command {
		FindCommand cond;
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
			if(!(cond instanceof WaitAndFindCommand))
			b.append("\nAssert.assertTrue("+cond.condition()+");");
			return b.toString();
		}
	}
	
	static class IfCommand extends Command {
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
		String javacode() {
			StringBuilder b = new StringBuilder();
			StringBuilder ifb = new StringBuilder();
			List<String> ifels = new ArrayList<String>();
			b.append(cond.javacodeonly(children));
			
			ifb.append("\nif("+cond.condition()+")");
			ifb.append("\n{");
			if(!children.isEmpty())
			{
				ifb.append("\n");
				for (Command c : children) {
					ifb.append(c.javacode());
					ifb.append("\n");
				}
				ifb.append("}");
			}
			else
			{
				ifb.append("\n}");
			}
			for (ElseIfCommand elif : elseifs) {
				b.append("\n"+elif.cond.javacodeonly(children));
				ifels.add(elif.javacode());
			}
			b.append(ifb.toString());
			for (String elif : ifels) {
				b.append(elif+"\n");
			}
			if(elsecmd!=null) {
				b.append(elsecmd.javacode()+"\n");
			}
			return b.toString();
		}
	}
	
	static class ElseIfCommand extends Command {
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
			b.append("\nelse if("+cond.condition()+")");
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
	}
	
	static class ElseCommand extends Command {
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
	}
	
	static class LoopCommand extends Command {
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
				pushSc();
				b.append("\nif("+cond.condition()+")");
				b.append("\nfor(final WebElement " + varname() + " : " + cvarname + ") {\n");
				b.append("final SearchContext "+currvarnamesc()+" = "+currvarname()+";");
				String vr = currvarname();
				b.append("\n@SuppressWarnings(\"serial\")\nList<WebElement> "+ varname()+" = new ArrayList<WebElement>(){{add("+vr+");}};");
				for (Command c : children) {
					b.append(c.javacode());
					b.append("\n");
				}
				b.append("}");
				prevvarnamesc();
			}
			return b.toString();
		}
	}
	
	static class BrowserCommand extends Command {
		String name;
		String platform;
		String version;
		BrowserCommand(String val) {
		    String[] parts = val.trim().split("[\t ]+");
            if(parts.length>1) {
                name = unsanitize(parts[0].trim());
                if(parts.length>2) {
                    platform = unsanitize(parts[1].trim());
                }
                if(parts.length>3) {
                    version = unsanitize(parts[2].trim());
                }
            } else {
                //excep
            }
        }
		String name() {
			return name;
		}
		String getPlatform()
        {
            return platform;
        }
        String getVersion()
        {
            return version;
        }

        String toCmd() {
			return "open " + name + (StringUtils.isNotBlank(platform)?(" "+platform):"") + (StringUtils.isNotBlank(version)?(" "+version):"");
		}
		String javacode() {
			pushSc();
			StringBuilder b = new StringBuilder();
			if(name.equalsIgnoreCase("chrome")) {
				b.append("DesiredCapabilities ___dc___ = DesiredCapabilities."+name.toLowerCase()+"();\n");
				b.append("___dc___.setCapability(CapabilityType.LOGGING_PREFS, ___lp___);\n");
				b.append("___d___ = new org.openqa.selenium.chrome.ChromeDriver(___dc___);\n");
			} else if(name.equalsIgnoreCase("firefox")) {
				b.append("DesiredCapabilities ___dc___ = DesiredCapabilities."+name.toLowerCase()+"();\n");
				b.append("___dc___.setCapability(CapabilityType.LOGGING_PREFS, ___lp___);\n");
				b.append("___d___ = new org.openqa.selenium.firefox.FirefoxDriver(___dc___);\n");
			}
			b.append("SearchContext "+currvarnamesc()+" = ___d___;\n");
			b.append("WebDriver ___cw___ = ___d___;\n");
			b.append("WebDriver ___ocw___ = ___cw___;");
			return b.toString();
		}
		int weight() {
	    	return 2;
	    }
	}
	
	static class WindowCommand extends Command {
		String name;
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
	}
	
	static class BackCommand extends Command {
		String toCmd() {
			return "back";
		}
		String javacode() {
			return "___cw___.navigate().back();";
		}
	}
	
	static class ForwardCommand extends Command {
		String toCmd() {
			return "forward";
		}
		String javacode() {
			return "___cw___.navigate().forward();";
		}
	}
	
	static class RefreshCommand extends Command {
		String toCmd() {
			return "refresh";
		}
		String javacode() {
			return "___cw___.navigate().refresh();";
		}
	}
	
	static class MaximizeCommand extends Command {
		String toCmd() {
			return "maximize";
		}
		String javacode() {
			return "___cw___.manage().window().maximize();";
		}
	}
	
	static class GotoCommand extends Command {
		String url;
		String url() {
			return url;
		}
		String toCmd() {
			return "goto " + url;
		}
		String javacode() {
			return "___cw___.navigate().to(\""+esc(url)+"\");";
		}
	}
	
	static class WindowSetPropertyCommand extends Command {
		String type;
		int value;
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
	}
    
    static class CapabilitySetPropertyCommand extends Command {
        String type;
        String value;
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
    }
	
	static String esc(String cmd) {
		return cmd.replace("\"", "\\\"");
	}
	
	static class FindCommand extends Command {
		String by, classifier, subselector, condvar;
		String by() {
			return by;
		}
		String classifier() {
			return classifier;
		}
		String subselector() {
			return subselector;
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
			if(by.equalsIgnoreCase("id")) {
				b.append("List<WebElement>  " + varname() + " = By.id(\""+esc(classifier)+"\").findElements("+currvarnamesc()+");");
			} else if(by.equalsIgnoreCase("name")) {
				b.append("List<WebElement>  " + varname() + " = By.name(\""+esc(classifier)+"\").findElements("+currvarnamesc()+");");
			} else if(by.equalsIgnoreCase("class") || by.equalsIgnoreCase("className")) {
				b.append("List<WebElement>  " + varname() + " = By.className(\""+esc(classifier)+"\").findElements("+currvarnamesc()+");");
			} else if(by.equalsIgnoreCase("tag") || by.equalsIgnoreCase("tagname")) {
				b.append("List<WebElement>  " + varname() + " = By.tagName(\""+esc(classifier)+"\").findElements("+currvarnamesc()+");");
			} else if(by.equalsIgnoreCase("xpath")) {
				b.append("List<WebElement>  " + varname() + " = By.xpath(\""+esc(classifier)+"\").findElements("+currvarnamesc()+");");
			} else if(by.equalsIgnoreCase("cssselector") || by.equalsIgnoreCase("css")) {
				b.append("List<WebElement>  " + varname() + " = By.cssSelector(\""+esc(classifier)+"\").findElements("+currvarnamesc()+");");
			} else if(by.equalsIgnoreCase("text")) {
				b.append("List<WebElement>  " + varname() + " = By.xpath(\"//*[contains(text(), '"+esc(classifier)+"')]\").findElements("+currvarnamesc()+");");
			} else if(by.equalsIgnoreCase("linkText")) {
				b.append("List<WebElement>  " + varname() + " = By.linkText(\""+esc(classifier)+"\").findElements("+currvarnamesc()+");");
			} else if(by.equalsIgnoreCase("partialLinkText")) {
				b.append("List<WebElement>  " + varname() + " = By.partialLinkText(\""+esc(classifier)+"\").findElements("+currvarnamesc()+");");
			} else if(by.equalsIgnoreCase("active")) {
				b.append("\n@SuppressWarnings(\"serial\")");
				b.append("List<WebElement>  " + varname() + " = new ArrayList<WebElement>(){{add(___cw___.activeElement());}};");
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
							b.append("\n" + condvar + " &= \""+esc(value)+"\".equals(___cw___.getTitle());");
							return b.toString();
						} else if(subselector.equalsIgnoreCase("currentUrl")) {
							b.append("\n" + condvar + " &= \""+esc(value)+"\".equals(___cw___.getCurrentUrl());");
							return b.toString();
						} else if(subselector.equalsIgnoreCase("pageSource")) {
							b.append("\n" + condvar + " &= \""+esc(value)+"\".equals(___cw___.getPageSource());");
							return b.toString();
						} else if(subselector.equalsIgnoreCase("width")) {
							b.append("\n" + condvar + " &= \""+esc(value)+"\".equals(String.valueOf(___cw___.manage().window().getSize().getWidth()));");
							return b.toString();
						} else if(subselector.equalsIgnoreCase("height")) {
							b.append("\n" + condvar + " &= \""+esc(value)+"\".equals(String.valueOf(___cw___.manage().window().getSize().getHeight()));");
							return b.toString();
						} else if(subselector.equalsIgnoreCase("xpos")) {
							b.append("\n" + condvar + " &= \""+esc(value)+"\".equals(String.valueOf(___cw___.manage().window().getPosition().getX()));");
							return b.toString();
						} else if(subselector.equalsIgnoreCase("ypos")) {
							b.append("\n" + condvar + " &= \""+esc(value)+"\".equals(String.valueOf(___cw___.manage().window().getPosition().getY()));");
							return b.toString();
						} else if(subselector.equalsIgnoreCase("alerttext")) {
							b.append("\n" + condvar + " &= \""+esc(value)+"\".equals(___cw___.switchTo().alert().getText());");
							return b.toString();
						}
					}
					
					b.append("\nfor(final WebElement " + varname() + " : " + cvarname + ")\n{");
					if(subselector.equalsIgnoreCase("text")) {
						b.append("\n" + condvar + " &= \""+esc(value)+"\".equals(" + currvarname() + ".getText());");
					} else if(subselector.equalsIgnoreCase("tagname")) {
						b.append("\n" + condvar + " &= \""+esc(value)+"\".equalsIgnoreCase(" + currvarname() + ".getTagName());");
					} else if(subselector.toLowerCase().startsWith("attr@")) {
						String atname = subselector.substring(5);
						b.append("\n" + condvar + " &= \""+esc(value)+"\".equals(" + currvarname() + ".getAttribute(\""+esc(atname)+"\"));");
					} else if(subselector.toLowerCase().startsWith("cssvalue@")) {
						String atname = subselector.substring(9);
						b.append("\n" + condvar + " &= \""+esc(value)+"\".equals(" + currvarname() + ".getCssValue(\""+esc(atname)+"\"));");
					} else if(subselector.equalsIgnoreCase("width")) {
						b.append("\n" + condvar + " &= \""+esc(value)+"\".equals(String.valueOf("+currvarname()+".getSize().getWidth()));");
						return b.toString();
					} else if(subselector.equalsIgnoreCase("height")) {
						b.append("\n" + condvar + " &= \""+esc(value)+"\".equals(String.valueOf("+currvarname()+".getSize().getHeight()));");
						return b.toString();
					} else if(subselector.equalsIgnoreCase("xpos")) {
						b.append("\n" + condvar + " &= \""+esc(value)+"\".equals(String.valueOf("+currvarname()+".getPosition().getX()));");
						return b.toString();
					} else if(subselector.equalsIgnoreCase("ypos")) {
						b.append("\n" + condvar + " &= \""+esc(value)+"\".equals(String.valueOf("+currvarname()+".getPosition().getY()));");
						return b.toString();
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
							b.append("\n" + condvar + " &= \""+esc(value)+"\".equals(" + currvarname() + ".getText());");
						} else if(subselector.equalsIgnoreCase("tagname")) {
							b.append("\n" + condvar + " &= \""+esc(value)+"\".equalsIgnoreCase(" + currvarname() + ".getTagName());");
						} else if(subselector.toLowerCase().startsWith("attr@")) {
							String atname = subselector.substring(5);
							b.append("\n" + condvar + " &= \""+esc(value)+"\".equals(" + currvarname() + ".getAttribute(\""+esc(atname)+"\"));");
						} else if(subselector.toLowerCase().startsWith("cssvalue@")) {
							String atname = subselector.substring(9);
							b.append("\n" + condvar + " &= \""+esc(value)+"\".equals(" + currvarname() + ".getCssValue(\""+esc(atname)+"\"));");
						} else if(subselector.equalsIgnoreCase("width")) {
							b.append("\n" + condvar + " &= \""+esc(value)+"\".equals(String.valueOf("+currvarname()+".getSize().getWidth()));");
							return b.toString();
						} else if(subselector.equalsIgnoreCase("height")) {
							b.append("\n" + condvar + " &= \""+esc(value)+"\".equals(String.valueOf("+currvarname()+".getSize().getHeight()));");
							return b.toString();
						} else if(subselector.equalsIgnoreCase("xpos")) {
							b.append("\n" + condvar + " &= \""+esc(value)+"\".equals(String.valueOf("+currvarname()+".getPosition().getX()));");
							return b.toString();
						} else if(subselector.equalsIgnoreCase("ypos")) {
							b.append("\n" + condvar + " &= \""+esc(value)+"\".equals(String.valueOf("+currvarname()+".getPosition().getY()));");
							return b.toString();
						}
					}
					b.append("\n}");
				}
			}
			if(condvar==null) {
				String cvarname = currvarname();
				condvar = condvarname();
				if(subselector!=null && !subselector.isEmpty())
				{
					b.append("\nboolean " + condvar + " = true;");
					b.append("\nfor(final WebElement " + varname() + " : " + cvarname + ")\n{");
					if(subselector.equalsIgnoreCase("selected")) {
						b.append("\n" + condvar + " &= " + currvarname() + ".isSelected();");
					} else if(subselector.equalsIgnoreCase("enabled")) {
						b.append("\n" + condvar + " &= " + currvarname() + ".isEnabled();");
					} else if(subselector.equalsIgnoreCase("visible")) {
						b.append("\n" + condvar + " &= " + currvarname() + ".isDisplayed();");
					}
					b.append("\n}");
				}
				else
				{
					b.append("\nboolean " + condvar + " = "+ cvarname+"!=null && !"+cvarname+".isEmpty();");
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
	
	static class WaitAndFindCommand extends FindCommand {
		long waitfor = 0;
		long waitfor() {
			return waitfor;
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
			String v = "final WebDriver "+currvarnamesc()+" = (WebDriver)"+tsc+";\n(new WebDriverWait(___cw___, "+waitfor+")).until(" +
				"\nnew Function<WebDriver, Boolean>(){"+
					"\npublic Boolean apply(WebDriver input) {\n"+
						super.javacodeonly(children) +
						"\nreturn "+super.condition()+";" +
					"\n}"+
					"\npublic String toString() {"+
					"\nreturn \"\";" +
					"\n}"+
				"\n});";
			prevvarnamesc();
			return v;
		}
	}
	
	static class StartCommand extends Command {
		String type;
		String toCmd() {
			return type;
		}
	}
	
	static class EndCommand extends Command {
		String type;
		String toCmd() {
			return type;
		}
	}
	
	static class TypeCommand extends ValueCommand {
		FindCommand cond;
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
				b.append("\nAssert.assertTrue("+cond.condition()+");");
			}
			b.append("\n"+currvarname()+".get(0).sendKeys(\""+esc(value)+"\");");
			return b.toString();
		}
	}
	
	static class ClickCommand extends Command {
		FindCommand cond;
		String toCmd() {
			return "click" + (cond!=null?cond.toCmd():"");
		}
		String javacode() {
			StringBuilder b = new StringBuilder();
			if(cond!=null) {
				b.append(cond.javacodeonly(children));
				b.append("\nAssert.assertTrue("+cond.condition()+");");
			}
			b.append("\n"+currvarname()+".get(0).click();");
			return b.toString();
		}
	}
	
	static class ClearCommand extends Command {
		FindCommand cond;
		String toCmd() {
			return "clear" + (cond!=null?cond.toCmd():"");
		}
		String javacode() {
			StringBuilder b = new StringBuilder();
			if(cond!=null) {
				b.append(cond.javacodeonly(children));
				b.append("\nAssert.assertTrue("+cond.condition()+");");
			}
			b.append("\n"+currvarname()+".get(0).clear();");
			return b.toString();
		}
	}
	
	static class SubmitCommand extends Command {
		FindCommand cond;
		String toCmd() {
			return "submit" + (cond!=null?cond.toCmd():"");
		}
		String javacode() {
			StringBuilder b = new StringBuilder();
			if(cond!=null) {
				b.append(cond.javacodeonly(children));
				b.append("\nAssert.assertTrue("+cond.condition()+");");
			}
			b.append("\n"+currvarname()+".get(0).submit();");
			return b.toString();
		}
	}
}
