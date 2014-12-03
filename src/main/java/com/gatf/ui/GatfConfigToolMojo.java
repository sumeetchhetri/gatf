 package com.gatf.ui;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.ws.rs.core.MediaType;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.SystemUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.InstantiationStrategy;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.NetworkListener;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.http.util.HttpStatus;

import com.gatf.executor.core.AcceptanceTestContext;
import com.gatf.executor.core.GatfExecutorConfig;
import com.gatf.executor.core.GatfTestCaseExecutorMojo;
import com.gatf.executor.core.TestCase;
import com.gatf.executor.report.ReportHandler;
import com.gatf.generator.core.GatfConfiguration;
import com.gatf.generator.core.GatfTestGeneratorMojo;


/**
 * @author sumeetc
 *
 */
@Mojo(
		name = "gatf-config", 
		aggregator = false, 
		executionStrategy = "always", 
		inheritByDefault = true, 
		instantiationStrategy = InstantiationStrategy.PER_LOOKUP, 
		requiresDependencyResolution = ResolutionScope.TEST, 
		requiresDirectInvocation = true, 
		requiresOnline = false, 
		requiresProject = true, 
		threadSafe = true)
public class GatfConfigToolMojo extends AbstractMojo {

	@Component
    private MavenProject project;
	
	@Parameter(alias = "rootDir", defaultValue = "${project.build.testOutputDirectory}")
	protected String rootDir;
	
	@Parameter(alias = "ipAddress", defaultValue = "localhost")
	private String ipAddress;
	
	@Parameter(alias = "port", defaultValue = "9080")
	private int port;

	private AcceptanceTestContext context = null;
	
	private TestCase authTestCase = null;
	
	public GatfConfigToolMojo() {
	}

	public void execute() throws MojoExecutionException, MojoFailureException {
		HttpServer server = new HttpServer();

		final String mainDir = rootDir + SystemUtils.FILE_SEPARATOR + "gatf-config-tool";
		InputStream resourcesIS = GatfConfigToolMojo.class.getResourceAsStream("/gatf-config-tool.zip");
        if (resourcesIS != null)
        {
        	ReportHandler.unzipZipFile(resourcesIS, rootDir);
        }
        
        final GatfConfigToolMojo mojo = this;
        
        createConfigFileIfNotExists(mojo, true, null);
        
        createConfigFileIfNotExists(mojo, false, null);
        
        createServerApiAndIssueTrackingApiFilesIfNotExists();
        
        server.addListener(new NetworkListener("ConfigServer", ipAddress, port));
        
        handleRootContext(server, mainDir, mojo);
        
        server.getServerConfiguration().addHttpHandler(new GatfConfigurationHandler(mojo, project), "/configure");
        
        server.getServerConfiguration().addHttpHandler(new GatfReportsHandler(mojo), "/reports");
        
        server.getServerConfiguration().addHttpHandler(new GatfMiscHandler(mojo), "/misc");
        
        server.getServerConfiguration().addHttpHandler(new GatfTestCaseFilesHandler(mojo), "/testcasefiles");
        
        server.getServerConfiguration().addHttpHandler(new GatfTestCaseHandler(mojo), "/testcases");
		
        server.getServerConfiguration().addHttpHandler(new GatfPluginExecutionHandler(mojo, project), "/execute");
		
		try {
		    server.start();
		    System.out.println("Press any key to stop the server...");
		    System.in.read();
		} catch (Exception e) {
		    System.err.println(e);
		}
	}
	
	private void createServerApiAndIssueTrackingApiFilesIfNotExists() {
		String[] configFiles = {AcceptanceTestContext.GATF_SERVER_LOGS_API_FILE_NM, "gatf-issuetracking-api-int.xml"};
		for (String fileNm : configFiles) {
			if(!new File(rootDir, fileNm).exists())
	        {
	        	try {
	        		File loggingApiFile = new File(rootDir, fileNm);
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

	private void handleRootContext(HttpServer server, final String mainDir, final GatfConfigToolMojo mojo) {
		server.getServerConfiguration().addHttpHandler(
		    new HttpHandler() {
		        public void service(Request request, Response response) throws Exception {
		        	new CacheLessStaticHttpHandler(mainDir).service(request, response);
		        }
		    }, "/");
		
		server.getServerConfiguration().addHttpHandler(new GatfProjectZipHandler(mojo), "/projectZip");
	}

	private static void sanitizeAndSaveGatfConfig(GatfExecutorConfig gatfConfig, GatfConfigToolMojo mojo, boolean isChanged) throws IOException
	{
		if(gatfConfig.getTestCasesBasePath()==null) {
			isChanged = true;
			gatfConfig.setTestCasesBasePath(mojo.rootDir);
		}
		if(gatfConfig.getOutFilesBasePath()==null) {
			isChanged = true;
			gatfConfig.setOutFilesBasePath(mojo.rootDir);
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
		
		if(isChanged)
		{
			FileUtils.writeStringToFile(new File(mojo.rootDir, "gatf-config.xml"), 
					GatfTestCaseExecutorMojo.getConfigStr(gatfConfig));
		}
	}

	protected static void createConfigFileIfNotExists(GatfConfigToolMojo mojo, boolean isExecutor, Object config)
	{
		if(isExecutor && !new File(mojo.rootDir, "gatf-config.xml").exists()) {
			try {
				new File(mojo.rootDir, "gatf-config.xml").createNewFile();
				GatfExecutorConfig gatfConfig = config==null?new GatfExecutorConfig():(GatfExecutorConfig)config;
				sanitizeAndSaveGatfConfig(gatfConfig, mojo, false);
			} catch (IOException e) {
			}
        }
		else if(!isExecutor && !new File(mojo.rootDir, "gatf-generator.xml").exists()) {
			try {
				new File(mojo.rootDir, "gatf-generator.xml").createNewFile();
				GatfConfiguration gatfConfig = config==null?new GatfConfiguration():(GatfConfiguration)config;

				FileUtils.writeStringToFile(new File(mojo.rootDir, "gatf-generator.xml"), 
						GatfTestGeneratorMojo.getConfigStr(gatfConfig));
			
			} catch (IOException e) {
			}
        }
	}
	
	public static void main(String[] args) throws MojoExecutionException, MojoFailureException {
		
		if(args.length>3 && args[0].equals("-configtool") && !args[1].trim().isEmpty() 
				&& !args[2].trim().isEmpty() && !args[3].trim().isEmpty()) {
			GatfConfigToolMojo mojo = new GatfConfigToolMojo();
			mojo.port = 9080;
			try {
				mojo.port = Integer.parseInt(args[1].trim());
			} catch (Exception e) {
			}
			mojo.ipAddress = args[2].trim();
			mojo.rootDir = args[3].trim();
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
	
	protected static GatfExecutorConfig getGatfExecutorConfig(GatfConfigToolMojo mojo, GatfExecutorConfig config) throws Exception
	{
		createConfigFileIfNotExists(mojo, true, config);
		GatfExecutorConfig gatfConfig = config;
		if(gatfConfig == null)
		{
			gatfConfig = GatfTestCaseExecutorMojo.getConfig(new FileInputStream(new File(mojo.rootDir, "gatf-config.xml")));
		}
		if(gatfConfig == null) {
			throw new RuntimeException("Invalid configuration present");
		}
		sanitizeAndSaveGatfConfig(gatfConfig, mojo, config!=null);
		return gatfConfig;
	}
	
	protected static GatfConfiguration getGatfConfiguration(GatfConfigToolMojo mojo, GatfConfiguration config) throws Exception
	{
		createConfigFileIfNotExists(mojo, false, config);
		GatfConfiguration gatfConfig = config;
		if(gatfConfig == null)
		{
			gatfConfig = GatfTestGeneratorMojo.getConfig(new FileInputStream(new File(mojo.rootDir, "gatf-generator.xml")));
		}
		if(gatfConfig == null) {
			throw new RuntimeException("Invalid configuration present");
		}
		if(gatfConfig.getResourcepath()==null)
		{
			gatfConfig.setResourcepath(mojo.rootDir + "/generated");
		}
		FileUtils.writeStringToFile(new File(mojo.rootDir, "gatf-generator.xml"), 
				GatfTestGeneratorMojo.getConfigStr(gatfConfig));
		return gatfConfig;
	}
	
	protected AcceptanceTestContext getContext() {
		return context;
	}

	protected void setContext(AcceptanceTestContext context) {
		this.context = context;
	}

	protected TestCase getAuthTestCase() {
		return authTestCase;
	}

	protected void setAuthTestCase(TestCase authTestCase) {
		this.authTestCase = authTestCase;
	}
}
