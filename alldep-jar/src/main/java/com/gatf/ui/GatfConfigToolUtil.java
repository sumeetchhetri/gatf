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
 package com.gatf.ui;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import javax.ws.rs.core.MediaType;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
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
	
	public GatfConfigToolUtil() {
	}

	public void execute() throws Exception {
		HttpServer server = new HttpServer();

		final String mainDir = rootDir + File.separator + "gatf-config-tool";
		WorkflowContextHandler.copyResourcesToDirectory("gatf-config-tool", mainDir);
        
        final GatfConfigToolUtil mojo = this;
        
        createConfigFileIfNotExists(mojo, true, null);
        
        createConfigFileIfNotExists(mojo, false, null);
        
        createServerApiAndIssueTrackingApiFilesIfNotExists(mojo);
        
        server.addListener(new NetworkListener("ConfigServer", ipAddress, port));
        ReportHandler.MY_URL = "http://localhost:" + port;
        
        handleRootContext(server, mainDir, mojo);
        
        Function<String, GatfPlugin> f = new Function<String, GatfPlugin>() {
			@Override
			public GatfPlugin apply(String type) {
				GatfPlugin gp = null;
				if(type.equals("executor"))
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
		
		try {
		    server.start();
		    new File("gatf.ctrl").createNewFile();
		    while(new File("gatf.ctrl").exists()) {
				Thread.sleep(10000);
				repHandler.clearInactiveDebugSessions();
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

    protected static void handleRootContext(HttpServer server, final String mainDir, final GatfConfigToolMojoInt mojo) {
		server.getServerConfiguration().addHttpHandler(
		    new HttpHandler() {
		        public void service(Request request, Response response) throws Exception {
		        	new CacheLessStaticHttpHandler(mainDir).service(request, response);
		        }
		    }, "/");
		
		server.getServerConfiguration().addHttpHandler(new GatfProjectZipHandler(mojo), "/projectZip");
	}

    protected static void sanitizeAndSaveGatfConfig(GatfExecutorConfig gatfConfig, GatfConfigToolMojoInt mojo, boolean isChanged) throws IOException
	{
		if(gatfConfig.getTestCasesBasePath()==null) {
			isChanged = true;
			gatfConfig.setTestCasesBasePath(mojo.getRootDir());
		} else {
			gatfConfig.setTestCasesBasePath(gatfConfig.getTestCasesBasePath().trim());
		}
		if(gatfConfig.getOutFilesBasePath()==null) {
			isChanged = true;
			gatfConfig.setOutFilesBasePath(mojo.getRootDir());
		} else {
			gatfConfig.setOutFilesBasePath(gatfConfig.getOutFilesBasePath().trim());
		}
		if(gatfConfig.getTestCaseDir()==null) {
			isChanged = true;
			gatfConfig.setTestCaseDir("data");
		} else {
			gatfConfig.setTestCaseDir(gatfConfig.getTestCaseDir().trim());
		}
		if(gatfConfig.getOutFilesDir()==null) {
			isChanged = true;
			gatfConfig.setOutFilesDir("out");
		} else {
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
				FileUtils.writeStringToFile(new File(mojo.getRootDir(), "gatf-config.xml"), GatfTestCaseExecutorUtil.getConfigStrXml(gatfConfig), "UTF-8");
			}
		}
	}

	protected static void createConfigFileIfNotExists(GatfConfigToolMojoInt mojo, boolean isExecutor, Object config)
	{
		if(isExecutor && !new File(mojo.getRootDir(), "gatf-config.xml").exists() && !new File(mojo.getRootDir(), "gatf-config.json").exists()) {
			try {
				synchronized (mojo) {
					new File(mojo.getRootDir(), "gatf-config.xml").createNewFile();
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
		if(e instanceof GatfSelCodeParseError) {
			Map<String, Object> h = new HashMap<>();
			h.put("error", ((GatfSelCodeParseError)e).getDetails());
			configJson = WorkflowContextHandler.OM.writeValueAsString(h);
			response.setContentType(MediaType.APPLICATION_JSON);
		}
		response.setContentLength(configJson.length());
        response.getWriter().write(configJson);
		response.setStatus(status==null?HttpStatus.INTERNAL_SERVER_ERROR_500:status);
		if(status==null)e.printStackTrace();
	}
	
	protected static void handleErrorJson(Throwable e, Response response, HttpStatus status) throws IOException
	{
		String configJson = e.getMessage()==null?ExceptionUtils.getStackTrace(e):e.getMessage();
		response.setContentType(MediaType.APPLICATION_XML);
		response.setContentLength(configJson.length());
        response.getWriter().write(configJson);
		response.setStatus(status==null?HttpStatus.INTERNAL_SERVER_ERROR_500:status);
		if(status==null)e.printStackTrace();
	}
	
	public static GatfExecutorConfig getGatfExecutorConfig(GatfConfigToolMojoInt mojo, GatfExecutorConfig config) throws Exception
	{
		createConfigFileIfNotExists(mojo, true, config);
		GatfExecutorConfig gatfConfig = config;
		if(gatfConfig == null)
		{
			synchronized (mojo) {
				if(new File(mojo.getRootDir(), "gatf-config.xml").exists()) {
					gatfConfig = GatfTestCaseExecutorUtil.getConfig(new FileInputStream(new File(mojo.getRootDir(), "gatf-config.xml")), true);
				} else {
					gatfConfig = GatfTestCaseExecutorUtil.getConfig(new FileInputStream(new File(mojo.getRootDir(), "gatf-config.json")), false);
				}
			}
		}
		if(gatfConfig == null) {
			throw new RuntimeException("Invalid configuration present");
		}
		sanitizeAndSaveGatfConfig(gatfConfig, mojo, config!=null);
		return gatfConfig;
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
