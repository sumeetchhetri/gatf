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

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.tools.Diagnostic;
import javax.tools.Diagnostic.Kind;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.logging.LogType;
import org.openqa.selenium.logging.LoggingPreferences;

import com.gatf.executor.core.AcceptanceTestContext;
import com.gatf.executor.core.GatfExecutorConfig;
import com.google.googlejavaformat.java.Formatter;

import net.lingala.zip4j.ZipFile;

public class SeleniumCodeGeneratorAndUtil {
	
	// --- delta-049: compile once, then cache by source hash and reuse the loaded class; each distinct
	// source gets its own in-memory classloader. Replaces the single static URLClassLoader, which served
	// stale bytecode when a same-named script was recompiled in a long-lived JVM, leaked class metadata,
	// and was racy across concurrent compiles. ---
	private static final int COMPILE_CACHE_MAX = 256;
	private static final Object COMPILE_LOCK = new Object();

	static final class CompiledUnit {
		final Class<?> cls;
		CompiledUnit(Class<?> cls) { this.cls = cls; }
	}

	private static final class CompileCache extends java.util.LinkedHashMap<String, CompiledUnit> {
		private static final long serialVersionUID = 1L;
		CompileCache() { super(64, 0.75f, true); }
		protected boolean removeEldestEntry(java.util.Map.Entry<String, CompiledUnit> eldest) {
			return size() > COMPILE_CACHE_MAX;
		}
	}

	private static final Map<String, CompiledUnit> COMPILE_CACHE = java.util.Collections.synchronizedMap(new CompileCache());

	// Loads classes from an in-memory {name -> bytecode} map (the generated test + its inner classes);
	// everything else delegates to the parent. Holds no OS handles, so it is GC-eligible once dropped.
	static final class InMemoryClassLoader extends ClassLoader {
		private final java.util.Map<String, byte[]> code;
		InMemoryClassLoader(java.util.Map<String, byte[]> code, ClassLoader parent) { super(parent); this.code = code; }
		protected Class<?> findClass(String name) throws ClassNotFoundException {
			byte[] b = code.get(name);
			if(b == null) return super.findClass(name);
			return defineClass(name, b, 0, b.length);
		}
	}

	private static String sha256(String s) {
		try {
			java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
			byte[] h = md.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder(h.length * 2);
			for (byte x : h) { sb.append(Character.forDigit((x >> 4) & 0xf, 16)).append(Character.forDigit(x & 0xf, 16)); }
			return sb.toString();
		} catch (Exception e) {
			return Integer.toHexString(s.hashCode());
		}
	}
	
	// Builds the -classpath for compiling generated tests, covering both run modes:
	//  - standalone (java -jar gatf-alldep.jar): java.class.path is the fat jar (all deps shaded);
	//  - project / Maven mode: java.class.path (and any URLClassLoader realm in the loader chain) carry
	//    the project's own classes and dependencies.
	// The gatf jar is always included. Works on Java 9+, where the app loader is not a URLClassLoader
	// (the old code fell back to the gatf jar alone there and dropped the project classpath).
	private static String buildCompileClasspath(ClassLoader loader, String gatfJarPath) {
		java.util.LinkedHashSet<String> cp = new java.util.LinkedHashSet<String>();
		String jcp = System.getProperty("java.class.path");
		if(jcp != null && !jcp.isEmpty()) {
			for (String p : jcp.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
				if(!p.isEmpty()) cp.add(p);
			}
		}
		ClassLoader l = loader;
		while(l != null) {
			if(l instanceof URLClassLoader) {
				for (URL url : ((URLClassLoader)l).getURLs()) {
					try { cp.add(new File(url.toURI()).getAbsolutePath()); }
					catch (Exception e) { cp.add(url.getPath()); }
				}
			}
			l = l.getParent();
		}
		if(gatfJarPath != null && !gatfJarPath.trim().isEmpty()) cp.add(gatfJarPath.trim());
		StringBuilder sb = new StringBuilder();
		for (String p : cp) { if(sb.length() > 0) sb.append(File.pathSeparator); sb.append(p); }
		return sb.toString();
	}

	public static void clean() throws Exception {
	    File gcdir = new File(FileUtils.getTempDirectory(), "gatf-code");
        if(gcdir.exists()) {
            FileUtils.deleteDirectory(gcdir);
        }
        
        File dir = new File(FileUtils.getTempDirectory(), "gatf-code/com/gatf/selenium/");
        dir.mkdirs();
	}
	
	@SuppressWarnings("unchecked")
	public static SeleniumTest getSeleniumTest(String fileName, ClassLoader loader, AcceptanceTestContext context, Object[] retvals, GatfExecutorConfig config, boolean isLog, Map<String, List<List<Integer[]>>> selToJavaLineMap, Command[] outCmd) throws Exception
	{
	    List<String> commands = new ArrayList<String>();
		Command cmd = Command.read(context.getResourceFile(fileName), commands, context);
		if(outCmd!=null) outCmd[0] = cmd;
		/*if(cmd.children.size()>0 && !(cmd.children.get(0) instanceof Command.BrowserCommand)) {
			throw new RuntimeException("open should be the first execution command in a test script");
		}*/
		String sourceCode =  cmd.javacode();
		
		String gatfJarPath = "gatf-alldep-jar.jar";
        try {
        	gatfJarPath = SeleniumCodeGeneratorAndUtil.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
		} catch (Throwable e) {
		}
        
        if(StringUtils.isNotBlank(config.getGatfJarPath())) {
        	gatfJarPath = config.getGatfJarPath().trim();
        }
		//System.out.println(new Formatter().formatSource(sourceCode));
		if(isLog) {
			try {
				//System.out.println(sourceCode);
				System.out.println(new Formatter().formatSource(sourceCode));
			} catch (Exception e) {
				System.out.println(sourceCode);
			}
		}
		
		retvals[0] = fileName;
		retvals[1] = StringUtils.join(commands, '\n');
		retvals[3] = sourceCode;
		
		if(selToJavaLineMap!=null) {
			File tcdir = context.getTestCasesDir();
			final AtomicInteger cl = new AtomicInteger(1);
			String cLine = "/*GATF_LINE@";
			String opLine = "/*GATF_OP_LINE@";
			sourceCode.lines().forEach(line -> {
				if(line.trim().startsWith(cLine)) {
					int indx = line.indexOf("*/");
					String fld = line.substring(line.indexOf(cLine)+cLine.length(), indx);
					String sflnm = fld.substring(0, fld.lastIndexOf(":"));
					int sln = Integer.parseInt(fld.substring(fld.lastIndexOf(":")+1));
					sflnm = sflnm.replace(tcdir.getAbsolutePath(), "");
					if(sflnm.startsWith(File.separator)) {
						sflnm = sflnm.substring(1);
					}
					if(!selToJavaLineMap.containsKey(sflnm)) {
						selToJavaLineMap.put(sflnm, new ArrayList<>());
						selToJavaLineMap.get(sflnm).add(new ArrayList<>());
					}
					selToJavaLineMap.get(sflnm).get(0).add(new Integer[] {sln, cl.get(), 1});
				} else if(line.trim().startsWith(opLine)) {
					int indx = line.indexOf("*/");
					String fld = line.substring(line.indexOf(opLine)+opLine.length(), indx);
					String sflnm = fld.substring(0, fld.lastIndexOf(":"));
					int sln = Integer.parseInt(fld.substring(fld.lastIndexOf(":")+1));
					sflnm = sflnm.replace(tcdir.getAbsolutePath(), "");
					if(sflnm.startsWith(File.separator)) {
						sflnm = sflnm.substring(1);
					}
					if(!selToJavaLineMap.containsKey(sflnm)) {
						selToJavaLineMap.put(sflnm, new ArrayList<>());
						selToJavaLineMap.get(sflnm).add(new ArrayList<>());
					}
					selToJavaLineMap.get(sflnm).get(0).add(new Integer[] {sln, cl.get(), 2});
				} else if(line.trim().startsWith("/*GATF_ST_START_*/")) {
					//selToJavaLineMap.put(0, new Object[] {null, cl.get(), false});
				}
				cl.incrementAndGet();
			});
			final AtomicInteger cl1 = new AtomicInteger(1);
			String stLine = "/*GATF_ST_LINE@";
			String stEndLine = "/*GATF_ST_LINE_END@";
			List<String> lines = sourceCode.lines().collect(Collectors.toList());
			List<Integer[]> stlines = new ArrayList<>();
			for(int i=0;i<lines.size();i++) {
				String line = lines.get(i);
				if(line.trim().startsWith(stLine)) {
					int indx = line.indexOf("*/");
					String fld = line.substring(line.indexOf(stLine)+stLine.length(), indx);
					String sflnm = fld.substring(0, fld.lastIndexOf(":"));
					int sln = Integer.parseInt(fld.substring(fld.lastIndexOf(":")+1));
					stlines.add(new Integer[] {sln, cl1.get(), 3});
					sflnm = sflnm.replace(tcdir.getAbsolutePath(), "");
					if(sflnm.startsWith(File.separator)) {
						sflnm = sflnm.substring(1);
					}
					if(!selToJavaLineMap.containsKey(sflnm)) {
						selToJavaLineMap.put(sflnm, new ArrayList<>());
					}
				} else if(line.trim().startsWith(stEndLine)) {
					int indx = line.indexOf("*/");
					String sflnm = line.substring(line.indexOf(stEndLine)+stEndLine.length(), indx);
					sflnm = sflnm.replace(tcdir.getAbsolutePath(), "");
					if(sflnm.startsWith(File.separator)) {
						sflnm = sflnm.substring(1);
					}
					selToJavaLineMap.get(sflnm).add(stlines);
					stlines = new ArrayList<>();
					//selToJavaLineMap.put(-cl1.get(), new Object[] {sflnm, cl1.get(), 4});
				}
				cl1.incrementAndGet();
			}
		}
	    
        File dir = new File(FileUtils.getTempDirectory(), "gatf-code/com/gatf/selenium/");
        
        File srcfile = new File(dir, cmd.getClassName()+".java");
        retvals[2] = srcfile.getAbsolutePath();
        
        if(retvals.length==5) retvals[4] += "";
        
        String fqcn = "com.gatf.selenium." + cmd.getClassName();
        String srcHash = sha256(sourceCode);

        CompiledUnit unit = COMPILE_CACHE.get(srcHash);
        if(unit == null) {
            synchronized (COMPILE_LOCK) {
                unit = COMPILE_CACHE.get(srcHash);
                if(unit == null) {
                    // write source under the compile lock so concurrent compiles of the same class
                    // name cannot interleave writes to the same .java (javac's input)
                    FileUtils.writeStringToFile(srcfile, sourceCode, "UTF-8");

                    // Compile with the bundled pure-Java compiler (ECJ) by default, so behaviour is the
                    // same on a JDK or a JRE and no external javac is needed; fall back to the platform
                    // compiler only if ECJ isn't on the classpath.
                    JavaCompiler compiler = null;
                    for (JavaCompiler jc : java.util.ServiceLoader.load(JavaCompiler.class)) { compiler = jc; break; }
                    if(compiler == null) {
                        compiler = ToolProvider.getSystemJavaCompiler();
                    }
                    if(compiler == null) {
                        throw new RuntimeException("No Java compiler available. Add the Eclipse ECJ compiler (org.eclipse.jdt:ecj) to the classpath, or run GATF on a JDK.");
                    }

                    String cp = buildCompileClasspath(loader, gatfJarPath);

                    List<String> optionList = Arrays.asList("-g", "-classpath", cp);
                    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
                    StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null);
                    Iterable<? extends JavaFileObject> compilationUnit = fileManager.getJavaFileObjectsFromFiles(Arrays.asList(srcfile));
                    boolean ok = compiler.getTask(null, fileManager, diagnostics, optionList, null, compilationUnit).call();
                    boolean errCd = false;
                    for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                        if(diagnostic.getKind() == Kind.ERROR) {
                            errCd = true;
                            if(retvals.length == 5) { retvals[4] += diagnostic.toString(); }
                            System.out.format("Error on line %d in %s%n", diagnostic.getLineNumber(), diagnostic.getSource()!=null?diagnostic.getSource().toUri():"<source>");
                        }
                        System.out.println(diagnostic.toString());
                    }
                    try { fileManager.close(); } catch (Exception ce) {}
                    if(!ok || errCd) {
                        return null;
                    }

                    // javac wrote the .class files to disk (still consumed by zipSeleniumTests and the
                    // distributed node loader); additionally read this class + its inner classes into an
                    // in-memory loader for LOCAL execution, so a re-edited same-named script never runs
                    // stale bytecode off disk and the loader stays GC-eligible.
                    final String clsPrefix = cmd.getClassName();
                    java.util.Map<String, byte[]> code = new java.util.HashMap<String, byte[]>();
                    File[] classFiles = dir.listFiles(new java.io.FilenameFilter() {
                        public boolean accept(File d, String n) {
                            return n.endsWith(".class") && (n.equals(clsPrefix + ".class") || n.startsWith(clsPrefix + "$"));
                        }
                    });
                    if(classFiles != null) {
                        for (File cf : classFiles) {
                            String cn = "com.gatf.selenium." + cf.getName().substring(0, cf.getName().length() - 6);
                            code.put(cn, FileUtils.readFileToByteArray(cf));
                        }
                    }
                    ClassLoader parent = (loader != null ? loader : SeleniumCodeGeneratorAndUtil.class.getClassLoader());
                    Class<?> loadedClass = new InMemoryClassLoader(code, parent).loadClass(fqcn);
                    unit = new CompiledUnit(loadedClass);
                    COMPILE_CACHE.put(srcHash, unit);
                }
            }
        }
        return (SeleniumTest) unit.cls.getConstructor(new Class[]{AcceptanceTestContext.class, int.class}).newInstance(new Object[]{context, 1});
	}
	
	public static File zipSeleniumTests() {
		File gcdir = new File(FileUtils.getTempDirectory(), "gatf-code");
		String fileName = UUID.randomUUID().toString()+".zip";
		File zipFile = new File(System.getProperty("java.io.tmpdir"), fileName);
		try {
			ZipFile zf = new ZipFile(zipFile);
        	zf.addFolder(gcdir);
        	zf.close();
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
				if(SeleniumTest.LOG_LEVEL_BY_NAME_MAP.containsKey(m.group(1)) && m.group(1).equalsIgnoreCase("on")
				        && SeleniumTest.LOG_LEVEL_BY_NAME_MAP.get(m.group(1))!=null) {
					lp.enable(LogType.BROWSER, SeleniumTest.LOG_LEVEL_BY_NAME_MAP.get(m.group(1)));
				}
			}
			if(slp.matches(".*client\\(([a-zA-Z]+)\\).*")) {
				Matcher m = Pattern.compile(".*client\\(([a-zA-Z]+)\\).*").matcher(slp);
				m.find();
				if(SeleniumTest.LOG_LEVEL_BY_NAME_MAP.containsKey(m.group(1)) && m.group(1).equalsIgnoreCase("on")
                        && SeleniumTest.LOG_LEVEL_BY_NAME_MAP.get(m.group(1))!=null) {
					//lp.enable(LogType.CLIENT, SeleniumTest.LOG_LEVEL_BY_NAME_MAP.get(m.group(1)));
				}
			}
			if(slp.matches(".*driver\\(([a-zA-Z]+)\\).*")) {
				Matcher m = Pattern.compile(".*driver\\(([a-zA-Z]+)\\).*").matcher(slp);
				m.find();
				if(SeleniumTest.LOG_LEVEL_BY_NAME_MAP.containsKey(m.group(1)) && m.group(1).equalsIgnoreCase("on")
                        && SeleniumTest.LOG_LEVEL_BY_NAME_MAP.get(m.group(1))!=null) {
					lp.enable(LogType.DRIVER, SeleniumTest.LOG_LEVEL_BY_NAME_MAP.get(m.group(1)));
				}
			}
			if(slp.matches(".*performance\\(([a-zA-Z]+)\\).*")) {
				Matcher m = Pattern.compile(".*performance\\(([a-zA-Z]+)\\).*").matcher(slp);
				m.find();
				if(SeleniumTest.LOG_LEVEL_BY_NAME_MAP.containsKey(m.group(1)) && m.group(1).equalsIgnoreCase("on")
                        && SeleniumTest.LOG_LEVEL_BY_NAME_MAP.get(m.group(1))!=null) {
					lp.enable(LogType.PERFORMANCE, SeleniumTest.LOG_LEVEL_BY_NAME_MAP.get(m.group(1)));
				}
			}
			if(slp.matches(".*profiler\\(([a-zA-Z]+)\\).*")) {
				Matcher m = Pattern.compile(".*profiler\\(([a-zA-Z]+)\\).*").matcher(slp);
				m.find();
				if(SeleniumTest.LOG_LEVEL_BY_NAME_MAP.containsKey(m.group(1)) && m.group(1).equalsIgnoreCase("on")
                        && SeleniumTest.LOG_LEVEL_BY_NAME_MAP.get(m.group(1))!=null) {
					//lp.enable(LogType.PROFILER, SeleniumTest.LOG_LEVEL_BY_NAME_MAP.get(m.group(1)));
				}
			}
			if(slp.matches(".*server\\(([a-zA-Z]+)\\).*")) {
				Matcher m = Pattern.compile(".*server\\(([a-zA-Z]+)\\).*").matcher(slp);
				m.find();
				if(SeleniumTest.LOG_LEVEL_BY_NAME_MAP.containsKey(m.group(1)) && m.group(1).equalsIgnoreCase("on")
                        && SeleniumTest.LOG_LEVEL_BY_NAME_MAP.get(m.group(1))!=null) {
					//lp.enable(LogType.SERVER, SeleniumTest.LOG_LEVEL_BY_NAME_MAP.get(m.group(1)));
				}
			}
		}
		return lp;
	}
}
