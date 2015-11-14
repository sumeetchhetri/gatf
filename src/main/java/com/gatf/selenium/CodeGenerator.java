package com.gatf.selenium;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import org.apache.commons.io.FileUtils;
import com.gatf.executor.core.AcceptanceTestContext;

public class CodeGenerator {
	
	@SuppressWarnings("unchecked")
	public static SeleniumTest getSeleniumTest(String fileName, ClassLoader loader, AcceptanceTestContext context) throws Exception
	{
		Command cmd = Command.read(context.getResourceFile(fileName));
		String sourceCode =  cmd.javacode();
	    
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null);

        List<String> optionList = new ArrayList<String>();
        optionList.add("-classpath");
        
        if(loader instanceof URLClassLoader) {
        	URL[] urls = ((URLClassLoader)loader).getURLs();
        	String cp = "";
        	for (URL url : urls) {
        		cp += url.getPath() + ";";
			}
        	optionList.add(cp);
        }
        
        File gcdir = new File(FileUtils.getTempDirectory(), "gatf-code");
        if(gcdir.exists()) {
        	FileUtils.deleteDirectory(gcdir);
        }
        
        File dir = new File(FileUtils.getTempDirectory(), "gatf-code/com/gatf/selenium/");
        dir.mkdirs();
        
        File srcfile = new File(dir, cmd.getClassName()+".java");
        FileUtils.writeStringToFile(srcfile, sourceCode);
        Iterable<? extends JavaFileObject> compilationUnit = fileManager.getJavaFileObjectsFromFiles(Arrays.asList(srcfile));
        JavaCompiler.CompilationTask task = compiler.getTask(
            null, 
            fileManager, 
            diagnostics, 
            optionList, 
            null, 
            compilationUnit);
        /********************************************************************************************* Compilation Requirements **/
        if (task.call()) {
        	URL[] urls = new URL[1];
            urls[0] = gcdir.toURI().toURL();
            URLClassLoader classLoader = new URLClassLoader(urls, loader);
            Class<SeleniumTest> loadedClass = (Class<SeleniumTest>)classLoader.loadClass("com.gatf.selenium." + cmd.getClassName());
            classLoader.close();
            return loadedClass.newInstance();
        } else {
            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                System.out.format("Error on line %d in %s%n",
                        diagnostic.getLineNumber(),
                        diagnostic.getSource().toUri());
                System.out.println(diagnostic.toString());
            }
            return null;
        }
	}
}
