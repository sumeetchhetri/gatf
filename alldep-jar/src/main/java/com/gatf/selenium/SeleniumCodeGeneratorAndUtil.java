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

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.tools.Diagnostic;
import javax.tools.Diagnostic.Kind;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.openqa.selenium.logging.LogType;
import org.openqa.selenium.logging.LoggingPreferences;

import com.gatf.executor.core.AcceptanceTestContext;
import com.gatf.executor.core.GatfExecutorConfig;
import com.google.googlejavaformat.java.Formatter;

import net.lingala.zip4j.ZipFile;

public class SeleniumCodeGeneratorAndUtil {
	
	static URLClassLoader classLoader = null;
	
	public static void clean() throws Exception {
	    File gcdir = new File(FileUtils.getTempDirectory(), "gatf-code");
        if(gcdir.exists()) {
            FileUtils.deleteDirectory(gcdir);
        }
        
        File dir = new File(FileUtils.getTempDirectory(), "gatf-code/com/gatf/selenium/");
        dir.mkdirs();
	}
	
	@SuppressWarnings("unchecked")
	public static SeleniumTest getSeleniumTest(String fileName, ClassLoader loader, AcceptanceTestContext context, Object[] retvals, GatfExecutorConfig config, boolean isLog) throws Exception
	{
	    List<String> commands = new ArrayList<String>();
		Command cmd = Command.read(context.getResourceFile(fileName), commands, context);
		String sourceCode =  cmd.javacode();
		
		if(isLog) {
			String jc = cmd.javacode();
			System.out.println(jc);
	        System.out.println(new Formatter().formatSource(jc));
		}
		
		retvals[0] = fileName;
		retvals[1] = StringUtils.join(commands, '\n');
		retvals[3] = sourceCode;
	    
        List<String> optionList = new ArrayList<String>();
        optionList.add("-classpath");
        
        if(loader instanceof URLClassLoader) {
        	URL[] urls = ((URLClassLoader)loader).getURLs();
        	String cp = "";
        	for (URL url : urls) {
        		cp += url.getPath() + File.separator;
			}
        	optionList.add(cp);
        } else {
        	optionList.add(StringUtils.isNoneBlank(config.getGatfJarPath())?config.getGatfJarPath().trim():"gatf-alldep.jar");
        }
        
        File gcdir = new File(FileUtils.getTempDirectory(), "gatf-code");
        File dir = new File(FileUtils.getTempDirectory(), "gatf-code/com/gatf/selenium/");
        
        File srcfile = new File(dir, cmd.getClassName()+".java");
        retvals[2] = srcfile.getAbsolutePath();
        FileUtils.writeStringToFile(srcfile, sourceCode, "UTF-8");
        
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
        
        if(retvals.length==5) retvals[4] += "";
        
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if(compiler!=null) {
	        StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null);
	        Iterable<? extends JavaFileObject> compilationUnit = fileManager.getJavaFileObjectsFromFiles(Arrays.asList(srcfile));
	        JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnostics, optionList, null, compilationUnit);
	        if (task.call()) {
	        	boolean errCd = false;
	        	for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
	        		if(diagnostic.getKind()==Kind.ERROR) {
	        			errCd = true;
	        			if(retvals.length==5) {
	        				retvals[4] += diagnostic.toString();
	        			}
	        			System.out.format("Error on line %d in %s%n", diagnostic.getLineNumber(), diagnostic.getSource().toUri());
	        		}
	                System.out.println(diagnostic.toString());
	            }
	        	
	        	if(errCd) {
	        		return null;
	        	}
	        	
	        	URL[] urls = new URL[1];
	            urls[0] = gcdir.toURI().toURL();
	            if(classLoader==null) {
	            	classLoader = new URLClassLoader(urls, loader);
	            }
	            Class<SeleniumTest> loadedClass = (Class<SeleniumTest>)classLoader.loadClass("com.gatf.selenium." + cmd.getClassName());
	            return loadedClass.getConstructor(new Class[]{AcceptanceTestContext.class, int.class}).newInstance(new Object[]{context, 1});
	        }
        }
        
        boolean isWindows = SystemUtils.IS_OS_WINDOWS;
        ProcessBuilder pb = new ProcessBuilder((isWindows?"\"":"") + config.getJavaHome() + "/bin/javac" + (isWindows?"\"":""), "-classpath", 
                (StringUtils.isNoneBlank(config.getGatfJarPath())?config.getGatfJarPath().trim():"gatf-alldep-jar.jar"), 
                (isWindows?"\"":"") + retvals[2].toString() + (isWindows?"\"":""));
        pb.redirectErrorStream(true);
        Process process = pb.start();
        BufferedReader inStreamReader = new BufferedReader(new InputStreamReader(process.getInputStream())); 

        boolean errd = false;
        String err = null;
        while((err = inStreamReader.readLine()) != null) {
            errd |= err.indexOf("error: ")!=-1;
            if(retvals.length==5 && err.indexOf("error: ")!=-1) {
				retvals[4] += err;
			}
            System.out.println(err);
        }
        
        if(errd) {
            return null;
        } else {
            URL[] urls = new URL[1];
            urls[0] = gcdir.toURI().toURL();
            if(classLoader==null) {
                classLoader = new URLClassLoader(urls, loader);
            }
            Class<SeleniumTest> loadedClass = (Class<SeleniumTest>)Class.forName("com.gatf.selenium." + cmd.getClassName(), true, classLoader);
            return loadedClass.getConstructor(new Class[]{AcceptanceTestContext.class, int.class}).newInstance(new Object[]{context, 1});
        }
	}
	
	public static File zipSeleniumTests() {
		File gcdir = new File(FileUtils.getTempDirectory(), "gatf-code");
		String fileName = UUID.randomUUID().toString()+".zip";
		File zipFile = new File(System.getProperty("java.io.tmpdir"), fileName);
		try {
			new ZipFile(zipFile).addFolder(gcdir);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		return zipFile;
	}
	
	public static LoggingPreferences getLp(GatfExecutorConfig configuration) {
		LoggingPreferences lp = new LoggingPreferences();
		if(StringUtils.isNotEmpty(configuration.getSeleniumLoggerPreferences()))
		{
			String slp = configuration.getSeleniumLoggerPreferences().toLowerCase();
			if(slp.matches(".*browser\\(([a-zA-Z]+)\\).*")) {
				Matcher m = Pattern.compile(".*browser\\(([a-zA-Z]+)\\).*").matcher(slp);
				m.find();
				if(SeleniumTest.LOG_LEVEL_BY_NAME_MAP.containsKey(m.group(1)) && m.group(1).equalsIgnoreCase("off")
				        && SeleniumTest.LOG_LEVEL_BY_NAME_MAP.get(m.group(1))!=null) {
					lp.enable(LogType.BROWSER, SeleniumTest.LOG_LEVEL_BY_NAME_MAP.get(m.group(1)));
				}
			}
			if(slp.matches(".*client\\(([a-zA-Z]+)\\).*")) {
				Matcher m = Pattern.compile(".*client\\(([a-zA-Z]+)\\).*").matcher(slp);
				m.find();
				if(SeleniumTest.LOG_LEVEL_BY_NAME_MAP.containsKey(m.group(1)) && m.group(1).equalsIgnoreCase("off")
                        && SeleniumTest.LOG_LEVEL_BY_NAME_MAP.get(m.group(1))!=null) {
					lp.enable(LogType.CLIENT, SeleniumTest.LOG_LEVEL_BY_NAME_MAP.get(m.group(1)));
				}
			}
			if(slp.matches(".*driver\\(([a-zA-Z]+)\\).*")) {
				Matcher m = Pattern.compile(".*driver\\(([a-zA-Z]+)\\).*").matcher(slp);
				m.find();
				if(SeleniumTest.LOG_LEVEL_BY_NAME_MAP.containsKey(m.group(1)) && m.group(1).equalsIgnoreCase("off")
                        && SeleniumTest.LOG_LEVEL_BY_NAME_MAP.get(m.group(1))!=null) {
					lp.enable(LogType.DRIVER, SeleniumTest.LOG_LEVEL_BY_NAME_MAP.get(m.group(1)));
				}
			}
			if(slp.matches(".*performance\\(([a-zA-Z]+)\\).*")) {
				Matcher m = Pattern.compile(".*performance\\(([a-zA-Z]+)\\).*").matcher(slp);
				m.find();
				if(SeleniumTest.LOG_LEVEL_BY_NAME_MAP.containsKey(m.group(1)) && m.group(1).equalsIgnoreCase("off")
                        && SeleniumTest.LOG_LEVEL_BY_NAME_MAP.get(m.group(1))!=null) {
					lp.enable(LogType.PERFORMANCE, SeleniumTest.LOG_LEVEL_BY_NAME_MAP.get(m.group(1)));
				}
			}
			if(slp.matches(".*profiler\\(([a-zA-Z]+)\\).*")) {
				Matcher m = Pattern.compile(".*profiler\\(([a-zA-Z]+)\\).*").matcher(slp);
				m.find();
				if(SeleniumTest.LOG_LEVEL_BY_NAME_MAP.containsKey(m.group(1)) && m.group(1).equalsIgnoreCase("off")
                        && SeleniumTest.LOG_LEVEL_BY_NAME_MAP.get(m.group(1))!=null) {
					lp.enable(LogType.PROFILER, SeleniumTest.LOG_LEVEL_BY_NAME_MAP.get(m.group(1)));
				}
			}
			if(slp.matches(".*server\\(([a-zA-Z]+)\\).*")) {
				Matcher m = Pattern.compile(".*server\\(([a-zA-Z]+)\\).*").matcher(slp);
				m.find();
				if(SeleniumTest.LOG_LEVEL_BY_NAME_MAP.containsKey(m.group(1)) && m.group(1).equalsIgnoreCase("off")
                        && SeleniumTest.LOG_LEVEL_BY_NAME_MAP.get(m.group(1))!=null) {
					lp.enable(LogType.SERVER, SeleniumTest.LOG_LEVEL_BY_NAME_MAP.get(m.group(1)));
				}
			}
		}
		return lp;
	}
}
