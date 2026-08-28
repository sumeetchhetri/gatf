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
 package com.gatf.ui;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import javax.ws.rs.core.MediaType;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.glassfish.grizzly.http.Method;
import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.NetworkListener;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.http.util.HttpStatus;

import com.gatf.GatfPlugin;
import com.gatf.executor.core.AcceptanceTestContext;
import com.gatf.executor.core.GatfExecutorConfig;
import com.gatf.executor.core.GatfTestCaseExecutorUtil;
import com.gatf.executor.core.TestCase;
import com.gatf.executor.core.WorkflowContextHandler;
import com.gatf.executor.report.ReportHandler;
import com.gatf.generator.core.GatfConfiguration;
import com.gatf.generator.core.GatfTestGeneratorUtil;
import com.gatf.selenium.Command.GatfSelCodeParseError;
import com.gatf.selenium.SeleniumDriverConfig;
import com.gatf.selenium.SeleniumTest.GatfRunTimeError;
import com.gatf.selenium.SeleniumTest.GatfRunTimeErrors;


/**
 * @author Sumeet Chhetri
 *
 */
public class GatfConfigToolUtil implements GatfConfigToolMojoInt {

	protected String rootDir;
	
	private String ipAddress;
	
	private int port;
	
	private AcceptanceTestContext context = null;
	
	private TestCase authTestCase = null;
	
	protected static Properties authSrc = new Properties();
	
	private int sessionTimeoutMs = 30 * 60 * 1000;

	private static Map<String, Long> authTokens = new ConcurrentHashMap<>();
	
	public GatfConfigToolUtil() {
	}
	
	public boolean isAuthenticated(Request request) {
		if(authSrc!=null) {
			String token = request.getParameter("token");
			if(StringUtils.isNotBlank(token) && authTokens.containsKey(token)) {
				authTokens.put(token, System.currentTimeMillis());
				return true;
			}
			return false;
		}
		return true;
	}
	
	public static String authenticate(Request request, Response response) {
		if(authSrc!=null) {
			response.setStatus(HttpStatus.UNAUTHORIZED_401);
			if(request.getMethod().equals(Method.POST) ) {
				String un = request.getParameter("username");
				String pwd = request.getParameter("password");
				if(StringUtils.isNotBlank(un) && StringUtils.isNotBlank(pwd)) {
					if(authSrc.containsKey(un) && authSrc.get(un).toString().equals(pwd)) {
						try {
							String token = Base64.encodeBase64String(UUID.randomUUID().toString().getBytes("UTF-8"));
							String tkjson = "{\"token\": \""+token+"\"}";
							authTokens.put(token, System.currentTimeMillis());
							response.setContentType(MediaType.APPLICATION_JSON + "; charset=utf-8");
				            response.setContentLength(tkjson.getBytes("UTF-8").length);
				            response.getWriter().write(tkjson);
				            response.setStatus(HttpStatus.OK_200);
						} catch (Exception e) {
						}
					}
				}
			}
		}
		return null;
	}

	public void execute() throws Exception {
		HttpServer server = new HttpServer();

		
		try {
			authSrc.load(new FileInputStream("auth.props"));
			if(authSrc.containsKey("session.timeout")) {
				try {
					sessionTimeoutMs = Integer.parseInt(authSrc.get("session.timeout").toString());
				} catch (Exception e) {
				}
				authSrc.remove("session.timeout");
			}
			if(authSrc.size()==0) {
				authSrc = null;
				authTokens = null;
			}
		} catch (IOException e) {
			authSrc = null;
			authTokens = null;
		}
		
		if(authSrc!=null) {
			System.out.println("===============Authentication source file available, Running in authenticated mode===============");
		} else {
			System.out.println("===============Running in anonymous mode===============");
		}
        
        final GatfConfigToolUtil mojo = this;
        
        createConfigFileIfNotExists(mojo, true, null);
        
        createConfigFileIfNotExists(mojo, false, null);
        
        createServerApiAndIssueTrackingApiFilesIfNotExists(mojo);
        
        server.addListener(new NetworkListener("ConfigServer", ipAddress, port));
        ReportHandler.MY_URL = "http://localhost:" + port;
        
        handleRootContext(server, mojo);

        // Phase 3: embedded React shell + Trace Viewer, served at /app (the legacy config-tool at
        // "/" now 302-redirects here). The Vite build is packaged under gatf-viewer/ and
        // extracted here; a plain static handler serves it. The config-tool is retired.
        final String viewerDir = rootDir + File.separator + "app";
        try {
            WorkflowContextHandler.copyResourcesToDirectory("gatf-viewer", viewerDir);
        } catch (Exception e) {
        }
        if(new File(viewerDir, "index.html").exists()) {
            server.getServerConfiguration().addHttpHandler(new CacheLessStaticHttpHandler(false, viewerDir), "/app");
        }
        
        Function<String, GatfPlugin> f = new Function<String, GatfPlugin>() {
			@Override
			public GatfPlugin apply(String type) {
				GatfPlugin gp = null;
				if(type.startsWith("executor"))
				{
					gp = new GatfTestCaseExecutorUtil();
				}
				else
				{
					gp = new GatfTestGeneratorUtil();
				}
				return gp;
			}
		};
		
        server.getServerConfiguration().addHttpHandler(new GatfConfigurationHandler(mojo, f), "/configure");
        
        GatfReportsHandler repHandler = new GatfReportsHandler(mojo, f);
        server.getServerConfiguration().addHttpHandler(repHandler, "/reports");
        
        server.getServerConfiguration().addHttpHandler(new GatfMiscHandler(mojo), "/misc");
        
        server.getServerConfiguration().addHttpHandler(new GatfTestCaseFilesHandler(mojo), "/testcasefiles");
        
        server.getServerConfiguration().addHttpHandler(new GatfTestCaseHandler(mojo), "/testcases");
		
        server.getServerConfiguration().addHttpHandler(new GatfPluginExecutionHandler(mojo, f), "/execute");
        
        server.getServerConfiguration().addHttpHandler(new GatfProfileHandler(mojo, f), "/profile");

        // Phase 3.x: read-only JSON API backing the React shell at /app (reports/config/test-files).
        // Additive — no existing handler is touched, and it performs no writes.
        server.getServerConfiguration().addHttpHandler(new GatfApiHandler(mojo), "/api");

        // Phase 11 — Playwright/JMeter test importer (preview + save into the test-case dir)
        server.getServerConfiguration().addHttpHandler(new GatfImportHandler(mojo), "/import");

        // Phase 11 — record mode: infer a test case from live user actions in a visible local Chrome
        server.getServerConfiguration().addHttpHandler(new GatfRecordHandler(mojo), "/record");

        // WebSocket push channel for the debugger (delta-041): mount /gatfjdb + attach the WS add-on to
        // every listener before start(). The SSE stream (/reports?action=debugStream) stays as the client
        // fallback, so a WS init failure degrades gracefully.
        try {
            for (NetworkListener nl : server.getListeners()) {
                nl.registerAddOn(new org.glassfish.grizzly.websockets.WebSocketAddOn());
            }
            org.glassfish.grizzly.websockets.WebSocketEngine.getEngine().register("", "/gatfjdb", new GatfDebugWebSocket());
        } catch (Throwable wsErr) {
            System.out.println("WebSocket debug channel unavailable (SSE fallback active): " + wsErr);
        }
		
		try {
		    server.start();
		    new File("gatf.ctrl").createNewFile();
		    while(new File("gatf.ctrl").exists()) {
				Thread.sleep(10000);
				repHandler.clearInactiveDebugSessions();
				
				if(authTokens!=null) {
					Set<String> rkeys = new HashSet<>();
					authTokens.forEach((k, v) -> {
						if(System.currentTimeMillis()-v>=sessionTimeoutMs) {
							rkeys.add(k);
						}
					});
					for (String k : rkeys) {
						authTokens.remove(k);
					}
				}
		    }
		} catch (Exception e) {
		    System.err.println(e);
		}
	}

    protected static void createServerApiAndIssueTrackingApiFilesIfNotExists(GatfConfigToolMojoInt mojo) {
		String[] configFiles = {AcceptanceTestContext.GATF_SERVER_LOGS_API_FILE_NM, "gatf-issuetracking-api-int.xml"};
		for (String fileNm : configFiles) {
			if(!new File(mojo.getRootDir(), fileNm).exists())
	        {
	        	try {
	        		File loggingApiFile = new File(mojo.getRootDir(), fileNm);
					loggingApiFile.createNewFile();
		        	BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(loggingApiFile));
					bos.write("<TestCases></TestCases>".getBytes());
					bos.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
	        }
		}
	}

    protected static void handleRootContext(HttpServer server, final GatfConfigToolMojoInt mojo) {
		server.getServerConfiguration().addHttpHandler(
		    new HttpHandler() {
		        public void service(Request request, Response response) throws Exception {
		        	response.setStatus(302); response.setHeader("Location", "/app/");
		        }
		    }, "/");
		
		server.getServerConfiguration().addHttpHandler(new GatfProjectZipHandler(mojo), "/projectZip");
		server.getServerConfiguration().addHttpHandler(new HttpHandler() {
			@Override
			public void service(Request request, Response response) throws Exception {
				authenticate(request, response);
			}
		}, "/login");
	}

    protected static void sanitizeAndSaveGatfConfig(GatfExecutorConfig gatfConfig, GatfConfigToolMojoInt mojo, boolean isChanged) throws IOException
	{
		sanitizeAndSaveGatfConfig(gatfConfig, mojo, isChanged, new File(mojo.getRootDir(), "gatf-config.json"));
	}

    protected static void sanitizeAndSaveGatfConfig(GatfExecutorConfig gatfConfig, GatfConfigToolMojoInt mojo, boolean isChanged, File targetFile) throws IOException
	{
		if(gatfConfig.getTestCasesBasePath()==null) {
			isChanged = true;
			gatfConfig.setTestCasesBasePath(mojo.getRootDir());
		} else {
			if(gatfConfig.getTestCasesBasePath().trim().equals(".") || !new File(gatfConfig.getTestCasesBasePath()).exists()) {
				throw new RuntimeException("Invalid Testcases base path");
			}
			gatfConfig.setTestCasesBasePath(gatfConfig.getTestCasesBasePath().trim());
		}
		if(gatfConfig.getOutFilesBasePath()==null) {
			isChanged = true;
			gatfConfig.setOutFilesBasePath(mojo.getRootDir());
		} else {
			if(gatfConfig.getOutFilesBasePath().trim().equals(".") || !new File(gatfConfig.getOutFilesBasePath()).exists()) {
				throw new RuntimeException("Invalid Out files base path");
			}
			gatfConfig.setOutFilesBasePath(gatfConfig.getOutFilesBasePath().trim());
		}
		if(StringUtils.isBlank(gatfConfig.getTestCaseDir()) || gatfConfig.getTestCaseDir().trim().equals(".")) {
			isChanged = true;
			gatfConfig.setTestCaseDir(null);
		} else {
			if(!new File(gatfConfig.getTestCasesBasePath(), gatfConfig.getTestCaseDir().trim()).exists()) {
				throw new RuntimeException("Invalid Testcasess directory");
			}
			gatfConfig.setTestCaseDir(gatfConfig.getTestCaseDir().trim());
		}
		if(gatfConfig.getOutFilesDir()==null) {
			isChanged = true;
			gatfConfig.setOutFilesDir("out");
		} else {
			if(gatfConfig.getOutFilesDir().trim().equals(".") || !new File(gatfConfig.getOutFilesBasePath(), gatfConfig.getOutFilesDir().trim()).exists()) {
				//throw new RuntimeException("Invalid Out files directory");
				new File(gatfConfig.getOutFilesBasePath(), gatfConfig.getOutFilesDir().trim()).mkdir();
			}
			gatfConfig.setOutFilesDir(gatfConfig.getOutFilesDir().trim());
		}
		if(gatfConfig.isEnabled()==null) {
			isChanged = true;
			gatfConfig.setEnabled(true);
		}
		if(gatfConfig.getConcurrentUserSimulationNum()==null) {
			isChanged = true;
			gatfConfig.setConcurrentUserSimulationNum(0);
		}
		if(gatfConfig.getHttpConnectionTimeout()==null) {
			isChanged = true;
			gatfConfig.setHttpConnectionTimeout(10000);
		}
		if(gatfConfig.getHttpRequestTimeout()==null) {
			isChanged = true;
			gatfConfig.setHttpRequestTimeout(10000);
		}
		if(gatfConfig.getLoadTestingReportSamples()==null) {
			isChanged = true;
			gatfConfig.setLoadTestingReportSamples(3);
		}
		if(gatfConfig.getConcurrentUserRampUpTime()==null) {
			isChanged = true;
			gatfConfig.setConcurrentUserRampUpTime(0L);
		}
		if(gatfConfig.getNumConcurrentExecutions()==null) {
			isChanged = true;
			gatfConfig.setNumConcurrentExecutions(1);
		}
		if(gatfConfig.getRepeatSuiteExecutionNum()==null) {
			isChanged = true;
			gatfConfig.setRepeatSuiteExecutionNum(0);
		}
        if (gatfConfig.getSeleniumScriptRetryCount() == null) {
        	isChanged = true;
        	gatfConfig.setSeleniumScriptRetryCount(0);
        }
		
		String tmp = System.getProperty("D_JAVA_HOME")!=null?System.getProperty("D_JAVA_HOME"):System.getenv("D_JAVA_HOME");
		if(tmp!=null) {
			isChanged = true;
			gatfConfig.setJavaHome(tmp);
		}
		
		boolean isDocker = false;
		tmp = System.getProperty("D_DOCKER")!=null?System.getProperty("D_DOCKER"):System.getenv("D_DOCKER");
		if("true".equalsIgnoreCase(tmp)) {
			isChanged = true;
			isDocker = true;
			gatfConfig.setTestCasesBasePath("/workdir");
			gatfConfig.setOutFilesBasePath("/workdir");
		}
		
		tmp = System.getProperty("D_CHROME_DRIVER")!=null?System.getProperty("D_CHROME_DRIVER"):System.getenv("D_CHROME_DRIVER");
		if(tmp!=null && gatfConfig.getSeleniumDriverConfigs()!=null) {
			if(gatfConfig.getSeleniumDriverConfigs().length>0) {
				for (SeleniumDriverConfig sc : gatfConfig.getSeleniumDriverConfigs()) {
					if(sc.getName().equalsIgnoreCase("chrome")) {
						isChanged = true;
						sc.setPath(tmp);
						if(isDocker) {
							String args = "--no-sandbox";
							if(StringUtils.isNotBlank(sc.getArguments())) {
								String cargs = sc.getArguments().trim();
								cargs = cargs.replace("--no-sandbox", "");
								args += " " + cargs;
							}
							args = args.replaceAll("\\s+", " ");
							sc.setArguments(args);
						}
					}
				}
			}
		}
		
		tmp = System.getProperty("D_FF_DRIVER")!=null?System.getProperty("D_FF_DRIVER"):System.getenv("D_FF_DRIVER");
		if(tmp!=null && gatfConfig.getSeleniumDriverConfigs()!=null) {
			if(gatfConfig.getSeleniumDriverConfigs().length>0) {
				for (SeleniumDriverConfig sc : gatfConfig.getSeleniumDriverConfigs()) {
					if(sc.getName().equalsIgnoreCase("firefox")) {
						isChanged = true;
						sc.setPath(tmp);
					}
				}
			}
		}
		
		tmp = System.getProperty("D_GATF_JAR")!=null?System.getProperty("D_GATF_JAR"):System.getenv("D_GATF_JAR");
		if(tmp!=null) {
			isChanged = true;
			gatfConfig.setGatfJarPath(tmp);
		}
		
		if(isChanged)
		{
			synchronized (mojo) {
				FileUtils.writeStringToFile(targetFile, targetFile.getName().toLowerCase().endsWith(".xml") ? GatfTestCaseExecutorUtil.getConfigStrXml(gatfConfig) : GatfTestCaseExecutorUtil.getConfigStrJson(gatfConfig), "UTF-8");
			}
		}
	}

	protected static void createConfigFileIfNotExists(GatfConfigToolMojoInt mojo, boolean isExecutor, Object config)
	{
		if(isExecutor && !new File(mojo.getRootDir(), "gatf-config.xml").exists() && !new File(mojo.getRootDir(), "gatf-config.json").exists()) {
			try {
				synchronized (mojo) {
					new File(mojo.getRootDir(), "gatf-config.json").createNewFile();
				}
				GatfExecutorConfig gatfConfig = config==null?new GatfExecutorConfig():(GatfExecutorConfig)config;
				sanitizeAndSaveGatfConfig(gatfConfig, mojo, false);
			} catch (IOException e) {
			}
        } else if(!isExecutor && !new File(mojo.getRootDir(), "gatf-generator.xml").exists() && !new File(mojo.getRootDir(), "gatf-generator.json").exists()) {
			try {
				synchronized (mojo) {
					new File(mojo.getRootDir(), "gatf-generator.xml").createNewFile();
				}
				GatfConfiguration gatfConfig = config==null?new GatfConfiguration():(GatfConfiguration)config;
				synchronized (mojo) {
					FileUtils.writeStringToFile(new File(mojo.getRootDir(), "gatf-generator.xml"), GatfTestGeneratorUtil.getConfigStr(gatfConfig), "UTF-8");
				}
			} catch (IOException e) {
			}
        }
	}
	
	public static void main(String[] args) throws Exception {
		if(args.length>3 && args[0].equals("-configtool") && !args[1].trim().isEmpty() 
				&& !args[2].trim().isEmpty() && !args[3].trim().isEmpty()) {
			GatfConfigToolUtil mojo = new GatfConfigToolUtil();
			mojo.port = 9080;
			try {
				mojo.port = Integer.parseInt(args[1].trim());
			} catch (Exception e) {
			}
			mojo.ipAddress = args[2].trim();
			String cwd = System.getProperty("user.dir");
			if(!new File(cwd).isAbsolute()) {
				throw new RuntimeException("Invalid root dir, current Working directory should be an absolute path");
			}
			if(args[3].trim().equals(".")) {
				mojo.setRootDir(cwd);
			} else if(new File(args[3].trim()).exists()) {
				mojo.setRootDir(new File(args[3].trim()).getAbsolutePath());
			} else {
				if(new File(cwd + File.separatorChar + args[3].trim()).exists()) {
					mojo.setRootDir(new File(cwd + File.separatorChar + args[3].trim()).getAbsolutePath());
				} else {
					throw new RuntimeException("Invalid root dir");
				}
			}
			if(mojo.getRootDir().charAt(mojo.getRootDir().length()-1)=='/') {
				mojo.setRootDir(mojo.getRootDir().substring(0, mojo.getRootDir().length()-1));
			}
			mojo.execute();
		}
	}
	
	protected static void handleError(Throwable e, Response response, HttpStatus status) throws IOException
	{
		String configJson = e.getMessage()==null?ExceptionUtils.getStackTrace(e):e.getMessage();
		if(e instanceof GatfRunTimeError) {
			Map<String, Object> h = new HashMap<>();
			ReportHandler.updatePaths("sel", h);
			h.put("error", ((GatfRunTimeError)e).getDetails());
			configJson = WorkflowContextHandler.OM.writeValueAsString(h);
			response.setContentType(MediaType.APPLICATION_JSON + "; charset=utf-8");
		} else if(e instanceof GatfRunTimeErrors) {
			GatfRunTimeErrors errs = (GatfRunTimeErrors)e;
			Set<String> uniq = new HashSet<>();
			List<Object[]> alldets = new ArrayList<>();
			for (int i=0;i<errs.getAll().size();i++) {
				if(errs.getAll().get(i) instanceof GatfSelCodeParseError && ((GatfSelCodeParseError)errs.getAll().get(i)).getAllErrors().size()>0) {
					for(GatfSelCodeParseError ge : ((GatfSelCodeParseError)errs.getAll().get(i)).getAllErrors()) {
						String key = "";
						if(ge.getDetails()!=null) {
							for (Object o : ge.getDetails()) {
								key += o!=null?o.toString():"";
							}
							if(!uniq.contains(key)) {
								alldets.add(ge.getDetails());
								uniq.add(key);
							}
						}
					}
				} else {
					String key = "";
					if(errs.getAll().get(i).getDetails()!=null) {
						for (Object o : errs.getAll().get(i).getDetails()) {
							key += o!=null?o.toString():"";
						}
						if(!uniq.contains(key)) {
							alldets.add(errs.getAll().get(i).getDetails());
							uniq.add(key);
						}
					}
				}
			}
			Map<String, Object> h = new HashMap<>();
			List<Object[]> others = new ArrayList<>();

			if(alldets.size()>0) {
			h.put("error", alldets.get(0));
				for (int i=1;i<alldets.size();i++) {
					others.add(alldets.get(i));
				}
			}
			
			h.put("others", others);
			ReportHandler.updatePaths("sel", h);
			configJson = WorkflowContextHandler.OM.writeValueAsString(h);
			response.setContentType(MediaType.APPLICATION_JSON + "; charset=utf-8");
		}
		response.setContentLength(configJson.getBytes("UTF-8").length);
        response.getWriter().write(configJson);
		response.setStatus(status==null?HttpStatus.INTERNAL_SERVER_ERROR_500:status);
		if(status==null)e.printStackTrace();
	}
	
	protected static void handleErrorJson(Throwable e, Response response, HttpStatus status) throws IOException
	{
		String configJson = e.getMessage()==null?ExceptionUtils.getStackTrace(e):e.getMessage();
		response.setContentType(MediaType.APPLICATION_XML + "; charset=utf-8");
		response.setContentLength(configJson.getBytes("UTF-8").length);
        response.getWriter().write(configJson);
		response.setStatus(status==null?HttpStatus.INTERNAL_SERVER_ERROR_500:status);
		if(status==null)e.printStackTrace();
	}
	
	public static GatfExecutorConfig getGatfExecutorConfig(GatfConfigToolMojoInt mojo, GatfExecutorConfig config) throws Exception
	{
		return getGatfExecutorConfig(mojo, config, null);
	}

	// Filename-aware config load/save. When configFileName names a valid selectable config (root-dir
	// gatf-config*.{json,xml} or configs/<file>, path-guarded, .json/.xml), that file is the one loaded (when
	// config==null) AND the one the sanitize save-back writes to (in its own format). When it is blank/invalid,
	// behaviour is exactly the legacy default: load gatf-config.json (else .xml), save-back to gatf-config.json.
	public static GatfExecutorConfig getGatfExecutorConfig(GatfConfigToolMojoInt mojo, GatfExecutorConfig config, String configFileName) throws Exception
	{
		createConfigFileIfNotExists(mojo, true, config);
		File selected = selectedExecutorConfigFile(mojo, configFileName);
		GatfExecutorConfig gatfConfig = config;
		if(gatfConfig == null)
		{
			File loadFile = selected != null ? selected
					: (new File(mojo.getRootDir(), "gatf-config.json").exists()
						? new File(mojo.getRootDir(), "gatf-config.json")
						: new File(mojo.getRootDir(), "gatf-config.xml"));
			synchronized (mojo) {
				gatfConfig = GatfTestCaseExecutorUtil.getConfig(new FileInputStream(loadFile), loadFile.getName().toLowerCase().endsWith(".xml"));
			}
		}
		if(gatfConfig == null) {
			throw new RuntimeException("Invalid configuration present");
		}
		File saveFile = selected != null ? selected : new File(mojo.getRootDir(), "gatf-config.json");
		sanitizeAndSaveGatfConfig(gatfConfig, mojo, config!=null, saveFile);
		return gatfConfig;
	}

	// Resolve a selectable executor config file (root-dir gatf-config*.{json,xml} or configs/<file>),
	// path-traversal guarded, .json/.xml only. Returns null when blank/unresolvable (caller uses the default).
	public static File selectedExecutorConfigFile(GatfConfigToolMojoInt mojo, String configRelPath) throws IOException
	{
		if(StringUtils.isBlank(configRelPath)) return null;
		File root = new File(mojo.getRootDir()).getCanonicalFile();
		File target = new File(root, configRelPath).getCanonicalFile();
		if(!target.getPath().equals(root.getPath()) && !target.getPath().startsWith(root.getPath() + File.separator)) return null;
		if(!target.isFile()) return null;
		String ln = target.getName().toLowerCase();
		if(!ln.endsWith(".json") && !ln.endsWith(".xml")) return null;
		return target;
	}

	
	protected static GatfConfiguration getGatfConfiguration(GatfConfigToolMojoInt mojo, GatfConfiguration config) throws Exception
	{
		createConfigFileIfNotExists(mojo, false, config);
		GatfConfiguration gatfConfig = config;
		if(gatfConfig == null)
		{
			gatfConfig = GatfTestGeneratorUtil.getConfig(new FileInputStream(new File(mojo.getRootDir(), "gatf-generator.xml")));
		}
		if(gatfConfig == null) {
			throw new RuntimeException("Invalid configuration present");
		}
		if(gatfConfig.getResourcepath()==null)
		{
			gatfConfig.setResourcepath(mojo.getRootDir() + "/generated");
		}
		synchronized (mojo) {
			FileUtils.writeStringToFile(new File(mojo.getRootDir(), "gatf-generator.xml"),  GatfTestGeneratorUtil.getConfigStr(gatfConfig), "UTF-8");
		}
		return gatfConfig;
	}
	
	public AcceptanceTestContext getContext() {
		return context;
	}

	public void setContext(AcceptanceTestContext context) {
		this.context = context;
	}

	public TestCase getAuthTestCase() {
		return authTestCase;
	}

	public void setAuthTestCase(TestCase authTestCase) {
		this.authTestCase = authTestCase;
	}
    
	public String getRootDir() {
	    return rootDir;
	}

	public void setRootDir(String rootDir) {
	    this.rootDir = rootDir;
	}
}
