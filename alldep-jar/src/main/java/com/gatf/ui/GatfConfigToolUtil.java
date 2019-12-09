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
import java.io.InputStream;
import java.util.function.Function;

import javax.ws.rs.core.MediaType;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.SystemUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
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
import com.gatf.executor.report.ReportHandler;
import com.gatf.generator.core.GatfConfiguration;
import com.gatf.generator.core.GatfTestGeneratorUtil;


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

		final String mainDir = rootDir + SystemUtils.FILE_SEPARATOR + "gatf-config-tool";
		InputStream resourcesIS = GatfConfigToolUtil.class.getResourceAsStream("/gatf-config-tool.zip");
        if (resourcesIS != null)
        {
        	ReportHandler.unzipZipFile(resourcesIS, rootDir);
        }
        
        final GatfConfigToolUtil mojo = this;
        
        createConfigFileIfNotExists(mojo, true, null);
        
        createConfigFileIfNotExists(mojo, false, null);
        
        createServerApiAndIssueTrackingApiFilesIfNotExists(mojo);
        
        server.addListener(new NetworkListener("ConfigServer", ipAddress, port));
        
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
        
        server.getServerConfiguration().addHttpHandler(new GatfReportsHandler(mojo, f), "/reports");
        
        server.getServerConfiguration().addHttpHandler(new GatfMiscHandler(mojo), "/misc");
        
        server.getServerConfiguration().addHttpHandler(new GatfTestCaseFilesHandler(mojo), "/testcasefiles");
        
        server.getServerConfiguration().addHttpHandler(new GatfTestCaseHandler(mojo), "/testcases");
		
        server.getServerConfiguration().addHttpHandler(new GatfPluginExecutionHandler(mojo, f), "/execute");
        
        server.getServerConfiguration().addHttpHandler(new GatfProfileHandler(mojo, f), "/profile");
		
		try {
		    server.start();
		    System.out.println("Press any key to stop the server...");
		    System.in.read();
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
		}
		if(gatfConfig.getOutFilesBasePath()==null) {
			isChanged = true;
			gatfConfig.setOutFilesBasePath(mojo.getRootDir());
		}
		if(gatfConfig.getTestCaseDir()==null) {
			isChanged = true;
			gatfConfig.setTestCaseDir("data");
		}
		if(gatfConfig.getOutFilesDir()==null) {
			isChanged = true;
			gatfConfig.setOutFilesDir("out");
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
		
		if(isChanged)
		{
			FileUtils.writeStringToFile(new File(mojo.getRootDir(), "gatf-config.xml"), 
					GatfTestCaseExecutorUtil.getConfigStr(gatfConfig), "UTF-8");
		}
	}

	protected static void createConfigFileIfNotExists(GatfConfigToolMojoInt mojo, boolean isExecutor, Object config)
	{
		if(isExecutor && !new File(mojo.getRootDir(), "gatf-config.xml").exists()) {
			try {
				new File(mojo.getRootDir(), "gatf-config.xml").createNewFile();
				GatfExecutorConfig gatfConfig = config==null?new GatfExecutorConfig():(GatfExecutorConfig)config;
				sanitizeAndSaveGatfConfig(gatfConfig, mojo, false);
			} catch (IOException e) {
			}
        }
		else if(!isExecutor && !new File(mojo.getRootDir(), "gatf-generator.xml").exists()) {
			try {
				new File(mojo.getRootDir(), "gatf-generator.xml").createNewFile();
				GatfConfiguration gatfConfig = config==null?new GatfConfiguration():(GatfConfiguration)config;

				FileUtils.writeStringToFile(new File(mojo.getRootDir(), "gatf-generator.xml"), 
						GatfTestGeneratorUtil.getConfigStr(gatfConfig), "UTF-8");
			
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
			mojo.setRootDir(args[3].trim());
			mojo.execute();
		}
	}
	
	protected static void handleError(Throwable e, Response response, HttpStatus status) throws IOException
	{
		String configJson = e.getMessage()==null?ExceptionUtils.getStackTrace(e):e.getMessage();
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
			gatfConfig = GatfTestCaseExecutorUtil.getConfig(new FileInputStream(new File(mojo.getRootDir(), "gatf-config.xml")));
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
		FileUtils.writeStringToFile(new File(mojo.getRootDir(), "gatf-generator.xml"), 
				GatfTestGeneratorUtil.getConfigStr(gatfConfig), "UTF-8");
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
