 package com.gatf.ui;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.ws.rs.HttpMethod;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
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
import org.codehaus.jackson.map.DeserializationConfig;
import org.codehaus.jackson.map.ObjectMapper;
import org.glassfish.grizzly.http.Method;
import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.NetworkListener;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.http.util.HttpStatus;

import com.gatf.GatfPlugin;
import com.gatf.GatfPluginConfig;
import com.gatf.executor.core.AcceptanceTestContext;
import com.gatf.executor.core.GatfExecutorConfig;
import com.gatf.executor.core.GatfTestCaseExecutorMojo;
import com.gatf.executor.core.TestCase;
import com.gatf.executor.dataprovider.DatabaseTestDataSource;
import com.gatf.executor.dataprovider.FileTestDataProvider;
import com.gatf.executor.dataprovider.GatfTestDataProvider;
import com.gatf.executor.dataprovider.GatfTestDataSource;
import com.gatf.executor.dataprovider.GatfTestDataSourceHook;
import com.gatf.executor.dataprovider.InlineValueTestDataProvider;
import com.gatf.executor.dataprovider.MongoDBTestDataSource;
import com.gatf.executor.dataprovider.RandomValueTestDataProvider;
import com.gatf.executor.executor.TestCaseExecutorUtil;
import com.gatf.executor.finder.TestCaseFinder;
import com.gatf.executor.finder.XMLTestCaseFinder;
import com.gatf.executor.report.ReportHandler;
import com.gatf.executor.report.TestCaseReport;
import com.gatf.generator.core.GatfConfiguration;
import com.gatf.generator.core.GatfTestGeneratorMojo;
import com.gatf.xstream.GatfPrettyPrintWriter;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.io.xml.XppDriver;


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
	private String rootDir;
	
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
        
        if(!new File(mojo.rootDir, AcceptanceTestContext.GATF_SERVER_LOGS_API_FILE_NM).exists())
        {
        	try {
        		File loggingApiFile = new File(mojo.rootDir, AcceptanceTestContext.GATF_SERVER_LOGS_API_FILE_NM);
				loggingApiFile.createNewFile();
	        	BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(loggingApiFile));
				bos.write("<TestCases></TestCases>".getBytes());
				bos.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
        }
        
        if(!new File(mojo.rootDir, "gatf-issuetracking-api-int.xml").exists())
        {
        	try {
        		File issueTrackingApi = new File(mojo.rootDir, "gatf-issuetracking-api-int.xml");
				issueTrackingApi.createNewFile();
	        	BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(issueTrackingApi));
				bos.write("<TestCases></TestCases>".getBytes());
				bos.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
        }
        
        server.addListener(new NetworkListener("ConfigServer", ipAddress, port));
        
        server.getServerConfiguration().addHttpHandler(
			    new HttpHandler() {
			        public void service(Request request, Response response) throws Exception {
			        	new CacheLessStaticHttpHandler(mainDir).service(request, response);
			        }
			    }, "/");
		
		server.getServerConfiguration().addHttpHandler(
			    new HttpHandler() {
			        public void service(Request request, Response response) throws Exception {
			        	try {
			        		GatfExecutorConfig gatfConfig = getGatfExecutorConfig(mojo, null);
		        			String basepath = gatfConfig.getOutFilesBasePath()==null?rootDir:gatfConfig.getOutFilesBasePath();
		        			String dirPath = basepath + SystemUtils.FILE_SEPARATOR + gatfConfig.getOutFilesDir();
	        				if(!new File(dirPath).exists()) {
	        					new File(dirPath).mkdir();
	        				}
	        				if(request.getMethod().equals(Method.GET) ) {
				        		new CacheLessStaticHttpHandler(dirPath).service(request, response);
				        	} else if(request.getMethod().equals(Method.PUT) ) {
				        		String action = request.getParameter("action");
				        		String testcaseFileName = request.getParameter("testcaseFileName");
				        		String testCaseName = request.getParameter("testCaseName");
				        		if(action.equals("replayTest"))
				        		{
				        			TestCaseReport tcReport = new ObjectMapper().readValue(request.getInputStream(), 
				        					TestCaseReport.class);
				        			if(tcReport == null) {
				        				throw new RuntimeException("Invalid testcase report details provided");
				        			}
				        			
				        			String filePath = basepath + SystemUtils.FILE_SEPARATOR + gatfConfig.getTestCaseDir() + SystemUtils.FILE_SEPARATOR
				        					+ testcaseFileName;
				        			if(!new File(filePath).exists()) {
				        				throw new RuntimeException("Test case file does not exist");
				        			}
				        			
				        			List<TestCase> tcs = new XMLTestCaseFinder().resolveTestCases(new File(filePath));
				        			TestCase found = null;
				        			for (TestCase tc : tcs) {
										if(tc.getName().equals(testCaseName)) {
											found = tc;
											break;
										}
									}
				        			if(found==null) {
				        				throw new RuntimeException("Testcase does not exist");
				        			}
				        			
				        			if(context==null) {
				        				throw new RuntimeException("Please Execute the GATF Suite first..");
				        			}
				        			
				        			TestCaseExecutorUtil testCaseExecutorUtil = TestCaseExecutorUtil.getSingleConnection(context);
				        			List<TestCaseReport> reports = null;
				        			synchronized(context)
				        			{
					        			context.clearTestResults();
					        			found = new TestCase(found);
					        			found.setSimulationNumber(0);
					        			found.setSourcefileName(testcaseFileName);
										found.setBaseUrl(context.getGatfExecutorConfig().getBaseUrl());
										found.setAurl(tcReport.getActualUrl());
										found.setAcontent(tcReport.getRequestContent());
										found.setAexpectedNodes(tcReport.getAexpectedNodes());
										found.setLogicalValidations(null);
										found.setExecuteOnCondition(null);
										found.setPreWaitMs(0L);
										found.setPostWaitMs(0L);
										found.setPreExecutionDataSourceHookName(null);
										found.setPostExecutionDataSourceHookName(null);
										if(found.getHeaders()!=null)
										{
											found.getHeaders().clear();
										}
										else
										{
											found.setHeaders(new HashMap<String, String>());
										}
										if(tcReport.getRequestHeaders()!=null)
										{
											String[] headers = tcReport.getRequestHeaders().split("\n");
											for(String header: headers)
											{
												if(header.indexOf(": ")!=-1)
												{
													found.getHeaders().put(header.substring(0, header.indexOf(": ")),
															header.substring(header.indexOf(": ")+2));
												}
											}
										}
										if(found.isSecure() && gatfConfig.isAuthEnabled() && authTestCase!=null)
										{
											reports = context.getSingleTestCaseExecutor().execute(authTestCase, 
													testCaseExecutorUtil);
										}
										context.getWorkflowContextHandler().initializeSuiteContextWithnum(0);
					        			reports = context.getSingleTestCaseExecutor().executeDirectTestCase(found, 
					        					testCaseExecutorUtil);
				        			}
				        			testCaseExecutorUtil.shutdown();
				        			
				        			if(reports==null || reports.size()==0)
				        			{
				        				throw new RuntimeException("Could not execute Testcase");
				        			}
				        			
				        			TestCaseReport report = reports.get(0);
				        			ReportHandler.populateRequestResponseHeaders(report);
				        			String configJson = new ObjectMapper().writeValueAsString(report);
									response.setContentLength(configJson.length());
						            response.getWriter().write(configJson);
									response.setStatus(HttpStatus.OK_200);
				        		}
				        	}
			        	} catch (Exception e) {
			        		handleError(e, response, null);
						}
			        }
			    }, "/reports");
		
		server.getServerConfiguration().addHttpHandler(
		    new HttpHandler() {
		        public void service(Request request, Response response) throws Exception {
		        	String configType = request.getParameter("configType");
		        	if(request.getMethod().equals(Method.GET) ) {
		        		try {
		        			String configJson = null;
		        			if(configType.equalsIgnoreCase("executor")) {
		        				createConfigFileIfNotExists(mojo, true, null);
		        				GatfExecutorConfig gatfConfig = getGatfExecutorConfig(mojo, null);
			        			configJson = new ObjectMapper().writeValueAsString(gatfConfig);
		        			} else if(configType.equalsIgnoreCase("generator")) {
		        				createConfigFileIfNotExists(mojo, false, null);
		        				GatfConfiguration gatfConfig = getGatfConfiguration(mojo, null);
			        			configJson = new ObjectMapper().writeValueAsString(gatfConfig);
		        			}
		        			response.setContentType(MediaType.APPLICATION_JSON);
				            response.setContentLength(configJson.length());
				            response.getWriter().write(configJson);
				            response.setStatus(HttpStatus.OK_200);
						} catch (Exception e) {
							handleError(e, response, null);
						}
		        	} else if(request.getMethod().equals(Method.POST) ) {
		        		try {
		        			if(configType.equalsIgnoreCase("executor")) {
		        				GatfExecutorConfig gatfConfig = new ObjectMapper().readValue(request.getInputStream(), 
			        					GatfExecutorConfig.class);
			        			getGatfExecutorConfig(mojo, gatfConfig);
		        			} else if(configType.equalsIgnoreCase("generator")) {
		        				GatfConfiguration gatfConfig = new ObjectMapper().readValue(request.getInputStream(), 
			        					GatfConfiguration.class);
			        			getGatfConfiguration(mojo, gatfConfig);
		        			}
		        			response.setStatus(HttpStatus.OK_200);
						} catch (Exception e) {
							handleError(e, response, HttpStatus.BAD_REQUEST_400);
						}
		        	}
		        }
		    },
		    "/configure");
		
		server.getServerConfiguration().addHttpHandler(
			    new HttpHandler() {
			        public void service(Request request, Response response) throws Exception {
			        	if(request.getMethod().equals(Method.GET) ) {
			        		try {
			        			GatfExecutorConfig gatfConfig = getGatfExecutorConfig(mojo, null);
			        			
			        			Map<String, List<String>> miscMap = new HashMap<String, List<String>>();
		        				if(gatfConfig.getGatfTestDataConfig()!=null && 
		        						gatfConfig.getGatfTestDataConfig().getDataSourceList()!=null) {
		        					List<String> dataLst = new ArrayList<String>();
		        					dataLst.add("");
		        					for (GatfTestDataSource ds : gatfConfig.getGatfTestDataConfig().getDataSourceList()) {
		        						dataLst.add(ds.getDataSourceName());
									}
		        					miscMap.put("datasources", dataLst);
			        			}
		        				if(gatfConfig.getGatfTestDataConfig()!=null && 
		        						gatfConfig.getGatfTestDataConfig().getProviderTestDataList()!=null) {
		        					List<String> dataLst = new ArrayList<String>();
		        					dataLst.add("");
		        					for (GatfTestDataProvider pv : gatfConfig.getGatfTestDataConfig().getProviderTestDataList()) {
		        						dataLst.add(pv.getProviderName());
									}
		        					miscMap.put("providers", dataLst);
		        				}
		        				if(gatfConfig.getGatfTestDataConfig()!=null && 
		        						gatfConfig.getGatfTestDataConfig().getDataSourceHooks()!=null) {
		        					List<String> dataLst = new ArrayList<String>();
		        					dataLst.add("");
		        					for (GatfTestDataSourceHook hk : gatfConfig.getGatfTestDataConfig().getDataSourceHooks()) {
		        						dataLst.add(hk.getHookName());
									}
		        					miscMap.put("hooks", dataLst);
		        				}
		        				List<String> dataLst = new ArrayList<String>();
		        				dataLst.add("");
		        				dataLst.add(MongoDBTestDataSource.class.getName());
		        				dataLst.add(DatabaseTestDataSource.class.getName());
		        				miscMap.put("datasourcecls", dataLst);
			        			
		        				dataLst = new ArrayList<String>();
		        				dataLst.add("");
		        				dataLst.add(FileTestDataProvider.class.getName());
		        				dataLst.add(InlineValueTestDataProvider.class.getName());
		        				dataLst.add(RandomValueTestDataProvider.class.getName());
		        				miscMap.put("providercls", dataLst);
			        			
			        			String configJson = new ObjectMapper().writeValueAsString(miscMap);
			        			response.setContentType(MediaType.APPLICATION_JSON);
					            response.setContentLength(configJson.length());
					            response.getWriter().write(configJson);
					            response.setStatus(HttpStatus.OK_200);
							} catch (Exception e) {
								handleError(e, response, null);
							}
			        	}
			        }
			    },
			    "/misc");
		
		server.getServerConfiguration().addHttpHandler(
			    new HttpHandler() {
			        public void service(Request request, Response response) throws Exception {
			        	if(request.getMethod().equals(Method.POST)) {
			        		try {
			        			String testcaseFileName = request.getParameter("testcaseFileName");
			        			if(StringUtils.isNotBlank(testcaseFileName)) {
			        				GatfExecutorConfig gatfConfig = getGatfExecutorConfig(mojo, null);
				        			if(!testcaseFileName.endsWith(".xml"))
				        			{
				        				throw new RuntimeException("Testcase File should be an xml file, extension should be (.xml)");
				        			}
				        			String basepath = gatfConfig.getTestCasesBasePath()==null?rootDir:gatfConfig.getTestCasesBasePath();
				        			String dirPath = basepath + SystemUtils.FILE_SEPARATOR + gatfConfig.getTestCaseDir();
			        				if(!new File(dirPath).exists()) {
			        					new File(dirPath).mkdir();
			        				}
				        			String filePath = basepath + SystemUtils.FILE_SEPARATOR + gatfConfig.getTestCaseDir() + SystemUtils.FILE_SEPARATOR + testcaseFileName;
				        			if(new File(filePath).exists()) {
				        				throw new RuntimeException("Testcase file already exists");
				        			}
				        			new File(filePath).createNewFile();
				        			BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(filePath));
				        			bos.write("<TestCases></TestCases>".getBytes());
				        			bos.close();
				        			response.setStatus(HttpStatus.OK_200);
			        			} else {
			        				throw new RuntimeException("No testcaseFileName query parameter specified");
			        			}
							} catch (Exception e) {
								handleError(e, response, null);
							}
			        	} else if(request.getMethod().equals(Method.DELETE)) {
			        		try {
			        			String testcaseFileName = request.getParameter("testcaseFileName");
			        			if(StringUtils.isNotBlank(testcaseFileName)) {
			        				GatfExecutorConfig gatfConfig = getGatfExecutorConfig(mojo, null);
				        			if(!testcaseFileName.endsWith(".xml"))
				        			{
				        				throw new RuntimeException("Testcase File should be an xml file, extension should be (.xml)");
				        			}
				        			String basepath = gatfConfig.getTestCasesBasePath()==null?rootDir:gatfConfig.getTestCasesBasePath();
				        			String dirPath = basepath + SystemUtils.FILE_SEPARATOR + gatfConfig.getTestCaseDir();
			        				if(!new File(dirPath).exists()) {
			        					new File(dirPath).mkdir();
			        				}
				        			String filePath = basepath + SystemUtils.FILE_SEPARATOR + gatfConfig.getTestCaseDir() + SystemUtils.FILE_SEPARATOR + testcaseFileName;
				        			if(new File(filePath).exists()) {
				        				new File(filePath).delete();
				        			} else {
				        				throw new RuntimeException("Testcase file does not exist");
				        			}
				        			response.setStatus(HttpStatus.OK_200);
			        			} else {
			        				throw new RuntimeException("No testcaseFileName query parameter specified");
			        			}
							} catch (Exception e) {
								handleError(e, response, null);
							}
			        	} else if(request.getMethod().equals(Method.GET) ) {
			        		try {
			        			GatfExecutorConfig gatfConfig = getGatfExecutorConfig(mojo, null);
			        			String basepath = gatfConfig.getTestCasesBasePath()==null?rootDir:gatfConfig.getTestCasesBasePath();
			        			String dirPath = basepath + SystemUtils.FILE_SEPARATOR + gatfConfig.getTestCaseDir();
		        				if(!new File(dirPath).exists()) {
		        					new File(dirPath).mkdir();
		        				}
			        			/*File[] xmlFiles = new File(dirPath).listFiles(new FilenameFilter() {
			        				public boolean accept(File folder, String name) {
			        					return name.toLowerCase().endsWith(".xml");
			        				}
			        			});*/
			        			
			        			FilenameFilter filter = new FilenameFilter() {
			        				public boolean accept(File folder, String name) {
			        					return name.toLowerCase().endsWith(".xml");
			        				}
			        			};
			        			
			        			File dirFPath = new File(dirPath);
			        			List<File> fileLst = new ArrayList<File>();
			        			TestCaseFinder.getFiles(dirFPath, filter, fileLst);
			        			
			        			List<String> fileNames = new ArrayList<String>();
			        			for (File file : fileLst) {
			        				fileNames.add(getRelativePath(file, dirFPath));
								}
			        			
			        			String json = new ObjectMapper().writeValueAsString(fileNames);
			        			response.setContentType(MediaType.APPLICATION_JSON);
					            response.setContentLength(json.length());
					            response.getWriter().write(json);
			        			response.setStatus(HttpStatus.OK_200);
							} catch (Exception e) {
								handleError(e, response, HttpStatus.BAD_REQUEST_400);
							}
			        	}
			        }
			    },
			    "/testcasefiles");
		
		server.getServerConfiguration().addHttpHandler(
			    new HttpHandler() {
			        public void service(Request request, Response response) throws Exception {
			        	String configType = request.getParameter("configType");
			        	String testcaseFileName = request.getParameter("testcaseFileName");
			        	boolean isApiIntType = configType!=null && (configType.equals("loggingapi") || configType.equals("issuetrackerapi"));
	        			if(StringUtils.isBlank(testcaseFileName)) {
	        				response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR_500);
	        				return;
	        			}
	        			GatfExecutorConfig gatfConfig = null;
	        			String filePath = null;
	        			if(!isApiIntType)
	        			{
		        			try {
		        				gatfConfig = getGatfExecutorConfig(mojo, null);
			        			String basepath = gatfConfig.getTestCasesBasePath()==null?rootDir:gatfConfig.getTestCasesBasePath();
			        			String dirPath = basepath + SystemUtils.FILE_SEPARATOR + gatfConfig.getTestCaseDir();
		        				if(!new File(dirPath).exists()) {
		        					new File(dirPath).mkdir();
		        				}
			        			filePath = basepath + SystemUtils.FILE_SEPARATOR + gatfConfig.getTestCaseDir() + SystemUtils.FILE_SEPARATOR
			        					+ testcaseFileName;
			        			if(!new File(filePath).exists()) {
			        				throw new RuntimeException("Test case file does not exist");
			        			}
		        			} catch (Exception e) {
		        				handleError(e, response, null);
								return;
							}
	        			}
	        			else
	        			{
	        				gatfConfig = getGatfExecutorConfig(mojo, null);
	        				String basepath = gatfConfig.getTestCasesBasePath()==null?rootDir:gatfConfig.getTestCasesBasePath();
	        				filePath = basepath + SystemUtils.FILE_SEPARATOR + testcaseFileName;
	        			}
	        			boolean isUpdate = request.getMethod().equals(Method.PUT);
	        			if(!isApiIntType && request.getMethod().equals(Method.DELETE)) {
			        		try {
			        			String testCaseName = request.getHeader("testcasename");
			        			List<TestCase> tcs = new XMLTestCaseFinder().resolveTestCases(new File(filePath));
			        			TestCase found = null;
			        			for (TestCase tc : tcs) {
									if(tc.getName().equals(testCaseName)) {
										found = tc;
										break;
									}
								}
			        			if(found==null) {
			        				throw new RuntimeException("Testcase does not exist");
			        			}
			        			tcs.remove(found);
			        			
			        			XStream xstream = new XStream(
			                			new XppDriver() {
			                				public HierarchicalStreamWriter createWriter(Writer out) {
			                					return new GatfPrettyPrintWriter(out, TestCase.CDATA_NODES);
			                				}
			                			}
			                		);
			                		xstream.processAnnotations(new Class[]{TestCase.class});
			                		xstream.alias("TestCases", List.class);
			                		xstream.toXML(tcs, new FileOutputStream(filePath));
				        			
				        			response.setStatus(HttpStatus.OK_200);
			        		} catch (Exception e) {
			        			handleError(e, response, HttpStatus.BAD_REQUEST_400);
							}
	        			} else if(request.getMethod().equals(Method.POST) || isUpdate) {
			        		try {
			        			TestCase testCase = new ObjectMapper().readValue(request.getInputStream(), 
			        					TestCase.class);
			        			if(testCase.getName()==null) {
			        				throw new RuntimeException("Testcase does not specify name");
			        			}
			        			
			        			if(isApiIntType && !testCase.getName().equals("authapi") && !testCase.getName().equals("targetapi"))
			        			{
			        				throw new RuntimeException("Only authapi or targetapi allowed for name");
			        			}
			        			
			        			testCase.validate(AcceptanceTestContext.getHttpHeadersMap());
			        			List<TestCase> tcs = new XMLTestCaseFinder().resolveTestCases(new File(filePath));
			        			if(!isUpdate)
			        			{
				        			for (TestCase tc : tcs) {
										if(tc.getName().equals(testCase.getName())) {
											throw new RuntimeException("Testcase with same name already exists");
										}
									}
				        			tcs.add(testCase);
			        			}
			        			else
			        			{
			        				boolean found = false;
			        				for (TestCase tc : tcs) {
										if(tc.getName().equals(testCase.getName())) {
											found = true;
											break;
										}
									}
			        				
			        				if(!found) {
			        					throw new RuntimeException("Testcase with name does not exist");
			        				}
			        				
			        				List<TestCase> ttcs = new ArrayList<TestCase>();
			        				for (TestCase tc : tcs) {
										if(tc.getName().equals(testCase.getName())) {
											ttcs.add(testCase);
										} else {
											ttcs.add(tc);
										}
									}
			        				tcs = ttcs;
			        			}
			        			
			        			XStream xstream = new XStream(
		                			new XppDriver() {
		                				public HierarchicalStreamWriter createWriter(Writer out) {
		                					return new GatfPrettyPrintWriter(out, TestCase.CDATA_NODES);
		                				}
		                			}
		                		);
		                		xstream.processAnnotations(new Class[]{TestCase.class});
		                		xstream.alias("TestCases", List.class);
		                		xstream.toXML(tcs, new FileOutputStream(filePath));
			        			
			        			response.setStatus(HttpStatus.OK_200);
							} catch (Throwable e) {
								handleError(e, response, null);
							}
			        	} else if(request.getMethod().equals(Method.GET) ) {
			        		try {
			        			List<TestCase> tcs = new XMLTestCaseFinder().resolveTestCases(new File(filePath));
			        			List<TestCase> tcsn = new ArrayList<TestCase>();
			        			boolean isAuthApi = false;
			        			boolean isTargetApi = false;
			        			if(tcs==null)
			        				throw new RuntimeException("No Testcases found...");
			        			for (TestCase testCase : tcs) {
			        				if(!isApiIntType || (isApiIntType && (testCase.getName().equals("authapi") 
			        						|| testCase.getName().equals("targetapi"))))
				        			{
			        					if(!isAuthApi)isAuthApi = testCase.getName().equals("authapi");
			        					if(!isTargetApi)isTargetApi = testCase.getName().equals("targetapi");
			        					tcsn.add(testCase);
				        			}
								}
			        			if(isApiIntType)
			        			{
			        				boolean changed = false;
			        				if(!isAuthApi)
			        				{
			        					TestCase tc = new TestCase();
			        					tc.setMethod(HttpMethod.POST);
			        					tc.setUrl("auth");
			        					tc.setName("authapi");
			        					tc.getHeaders().put(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN);
			        					tc.setRepeatScenarioProviderName("");
			        					tc.setPreExecutionDataSourceHookName("");
			        					tc.setPostExecutionDataSourceHookName("");
			        					tc.setServerApiAuth(true);
			        					tcsn.add(tc);
			        					changed = true;
			        				}
			        				if(!isTargetApi)
			        				{
			        					TestCase tc = new TestCase();
			        					tc.setMethod(HttpMethod.POST);
			        					tc.setUrl("target");
			        					tc.setName("targetapi");
			        					tc.setSecure(isAuthApi);
			        					tc.getHeaders().put(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN);
			        					tc.setRepeatScenarioProviderName("");
			        					tc.setPreExecutionDataSourceHookName("");
			        					tc.setPostExecutionDataSourceHookName("");
			        					tc.setServerApiTarget(true);
			        					tcsn.add(tc);
			        					changed = true;
			        				}
			        				if(changed)
			        				{
			        					XStream xstream = new XStream(
				                			new XppDriver() {
				                				public HierarchicalStreamWriter createWriter(Writer out) {
				                					return new GatfPrettyPrintWriter(out, TestCase.CDATA_NODES);
				                				}
				                			}
				                		);
				                		xstream.processAnnotations(new Class[]{TestCase.class});
				                		xstream.alias("TestCases", List.class);
				                		xstream.toXML(tcsn, new FileOutputStream(filePath));
			        				}
			        			}
			        			String json = new ObjectMapper().writeValueAsString(tcsn);
			        			response.setContentType(MediaType.APPLICATION_JSON);
					            response.setContentLength(json.length());
					            response.getWriter().write(json);
			        			response.setStatus(HttpStatus.OK_200);
							} catch (Exception e) {
								handleError(e, response, HttpStatus.BAD_REQUEST_400);
							}
			        	}
			        }
			    },
			    "/testcases");
		
		server.getServerConfiguration().addHttpHandler(
			    new HttpHandler() {
			    	AtomicBoolean isStarted = new AtomicBoolean(false);
			    	AtomicBoolean isDone = new AtomicBoolean(false);
			    	Thread _executorThread = null;
			    	volatile String status = "";
			        public void service(Request request, Response response) throws Exception {
			        	GatfPluginConfig gatfConfig = null;
			        	final String pluginType = request.getParameter("pluginType");
	        			try {
	        				gatfConfig = getGatfPluginConfig(pluginType, mojo);
	        				if(request.getMethod().equals(Method.GET) ) {
	        					if(isStarted.get()) {
	        						throw new RuntimeException("Execution already in progress..");
	        					} else if(isDone.get()) {
	        						isDone.set(false);
	        						isStarted.set(false);
	        						
	        						String temp = status;
	        						status = "";
	        						if(temp!=null)
	        							throw new RuntimeException("Execution failed with Error - " + temp);
	        						
	        						String text = "Execution completed, check Reports Section";
				        			response.setContentType(MediaType.TEXT_PLAIN);
						            response.setContentLength(text.length());
						            response.getWriter().write(text);
				        			response.setStatus(HttpStatus.OK_200);
	        					} else if(!isStarted.get()) {
	        						throw new RuntimeException("Please Start the Execution....");
	        					} else {
	        						throw new RuntimeException("Unknown Error...");
	        					}
	        				}
	        				else if(request.getMethod().equals(Method.PUT) ) {
	        					if(!isStarted.get() && !isDone.get()) {
	        						isStarted.set(true);
	        						final GatfPluginConfig config = gatfConfig;
	        						_executorThread = new Thread(new Runnable() {
										public void run() {
											GatfPlugin executorMojo = getGatfPlugin(pluginType);
			        						executorMojo.setProject(project);
			        						try {
												executorMojo.doExecute(config);
												initializeMojoProps(executorMojo, mojo);
											} catch (Throwable e) {
												e.printStackTrace();
												if(e.getMessage()!=null)
													status = e.getMessage();
												else
													status = ExceptionUtils.getStackTrace(e);
											} finally { 
												executorMojo.shutdown();
											}
			        						isDone.set(true);
											isStarted.set(false);
										}

										private void initializeMojoProps(GatfPlugin executorMojo, GatfConfigToolMojo mojo) {
											if(executorMojo instanceof GatfTestCaseExecutorMojo) {
												mojo.context = ((GatfTestCaseExecutorMojo)executorMojo).getContext();
												mojo.authTestCase = ((GatfTestCaseExecutorMojo)executorMojo).getAuthTestCase();
											}
										}
									});
	        						_executorThread.start();
	        						String text = "Execution Started";
				        			response.setContentType(MediaType.TEXT_PLAIN);
						            response.setContentLength(text.length());
						            response.getWriter().write(text);
				        			response.setStatus(HttpStatus.OK_200);
	        					} else if(isDone.get()) {
	        						isDone.set(false);
	        						isStarted.set(false);
	        						
	        						String temp = status;
	        						status = "";
	        						if(temp!=null)
	        							throw new RuntimeException("Execution failed with Error - " + temp);
	        						
	        						String text = "Execution completed, check Reports Section";
				        			response.setContentType(MediaType.TEXT_PLAIN);
						            response.setContentLength(text.length());
						            response.getWriter().write(text);
				        			response.setStatus(HttpStatus.OK_200);
	        					} else if(isStarted.get()) {
	        						throw new RuntimeException("Execution already in progress..");
	        					} else {
	        						throw new RuntimeException("Unknown Error...");
	        					}
				        	} else if(request.getMethod().equals(Method.DELETE) ) {
				        		if(isStarted.get() && _executorThread!=null) {
				        			_executorThread.interrupt();
				        			_executorThread = null;
	        					} else if(!isStarted.get()) {
	        						throw new RuntimeException("Testcase execution is not in progress...");
	        					} else if(isDone.get()) {
	        						isDone.set(false);
	        						isStarted.set(false);
	        						
	        						String temp = status;
	        						status = "";
	        						if(temp!=null)
	        							throw new RuntimeException("Execution failed with Error - " + temp);
	        						
	        						String text = "Execution completed, check Reports Section";
				        			response.setContentType(MediaType.TEXT_PLAIN);
						            response.setContentLength(text.length());
						            response.getWriter().write(text);
				        			response.setStatus(HttpStatus.OK_200);
	        					} else {
	        						throw new RuntimeException("Unknown Error...");
	        					}
				        	}
	        			} catch (Exception e) {
	        				handleError(e, response, HttpStatus.BAD_REQUEST_400);
							return;
						}
			        }
			    },
			    "/execute");
		
		try {
		    server.start();
		    System.out.println("Press any key to stop the server...");
		    System.in.read();
		} catch (Exception e) {
		    System.err.println(e);
		}
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

	private static void createConfigFileIfNotExists(GatfConfigToolMojo mojo, boolean isExecutor, Object config)
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
	
	private static void handleError(Throwable e, Response response, HttpStatus status) throws IOException
	{
		String configJson = e.getMessage()==null?ExceptionUtils.getStackTrace(e):e.getMessage();
		response.setContentLength(configJson.length());
        response.getWriter().write(configJson);
		response.setStatus(status==null?HttpStatus.INTERNAL_SERVER_ERROR_500:status);
		if(status==null)e.printStackTrace();
	}
	
	private static GatfExecutorConfig getGatfExecutorConfig(GatfConfigToolMojo mojo, GatfExecutorConfig config) throws Exception
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
	
	private static GatfConfiguration getGatfConfiguration(GatfConfigToolMojo mojo, GatfConfiguration config) throws Exception
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
	
	private static GatfPlugin getGatfPlugin(String type)
	{
		if(type.equals("executor"))
		{
			return new GatfTestCaseExecutorMojo();
		}
		else
		{
			return new GatfTestGeneratorMojo();
		}
	}
	
	private static GatfPluginConfig getGatfPluginConfig(String type, GatfConfigToolMojo mojo) throws Exception
	{
		if(type.equals("executor"))
		{
			GatfExecutorConfig gatfConfig = getGatfExecutorConfig(mojo, null);
			return gatfConfig;
		}
		else
		{
			GatfConfiguration gatfConfig = getGatfConfiguration(mojo, null);
			return gatfConfig;
		}
	}
	
	// returns null if file isn't relative to folder
	public static String getRelativePath(File file, File folder) {
	    String filePath = file.getAbsolutePath();
	    String folderPath = folder.getAbsolutePath();
	    if (filePath.startsWith(folderPath)) {
	        return filePath.substring(folderPath.length() + 1);
	    } else {
	        return null;
	    }
	}
	
	public static void main2(String[] args) throws Exception
	{
		ObjectMapper jsonMapper = new ObjectMapper();
    	jsonMapper.configure(DeserializationConfig.Feature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    	jsonMapper.configure(DeserializationConfig.Feature.AUTO_DETECT_FIELDS, true);
    	String schemaJson = jsonMapper.generateJsonSchema(GatfConfiguration.class).toString();
    	schemaJson = schemaJson.replaceAll("'", "\'");
    	System.out.println(schemaJson);
	}
}
