package com.gatf.ui;

import java.io.File;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.SystemUtils;
import org.apache.maven.project.MavenProject;
import org.apache.velocity.VelocityContext;
import org.glassfish.grizzly.http.Method;
import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.http.util.HttpStatus;

import com.gatf.executor.core.AcceptanceTestContext;
import com.gatf.executor.core.GatfExecutorConfig;
import com.gatf.executor.core.GatfTestCaseExecutorMojo;
import com.gatf.executor.core.TestCase;
import com.gatf.executor.executor.TestCaseExecutorUtil;
import com.gatf.executor.finder.XMLTestCaseFinder;
import com.gatf.executor.report.ReportHandler;
import com.gatf.executor.report.TestCaseReport;
import com.gatf.executor.report.TestCaseReport.TestStatus;

public class GatfReportsHandler extends HttpHandler {

	private GatfConfigToolMojo mojo;
	
	private MavenProject project;
	
	private AcceptanceTestContext context = null;
	
	private TestCase authTestCase = null;
	
	private ReentrantLock lock = new ReentrantLock();
	
	protected GatfReportsHandler(GatfConfigToolMojo mojo, MavenProject project) {
		super();
		this.mojo = mojo;
		this.project = project;
	}

	@Override
	public void service(Request request, Response response) throws Exception {
    	try {
    		final GatfExecutorConfig gatfConfig = GatfConfigToolMojo.getGatfExecutorConfig(mojo, null);
			String basepath = gatfConfig.getOutFilesBasePath()==null?mojo.rootDir:gatfConfig.getOutFilesBasePath();
			String dirPath = basepath + SystemUtils.FILE_SEPARATOR + gatfConfig.getOutFilesDir();
			if(!new File(dirPath).exists()) {
				new File(dirPath).mkdir();
			}
			if(request.getMethod().equals(Method.GET) ) {
        		new CacheLessStaticHttpHandler(dirPath).service(request, response);
        	} else if(request.getMethod().equals(Method.PUT) ) {
        		basepath = gatfConfig.getTestCasesBasePath()==null?mojo.rootDir:gatfConfig.getTestCasesBasePath();
        		String action = request.getParameter("action");
        		String testcaseFileName = request.getParameter("testcaseFileName");
        		String testCaseName = request.getParameter("testCaseName");
        		boolean isServerLogsApi = request.getParameter("isServerLogsApi")!=null;
        		boolean isExternalLogsApi = request.getParameter("isExternalLogsApi")!=null;
        		if(action.equals("replayTest") || action.equals("playTest") || action.equals("createIssue") || action.equals("getContent"))
        		{
        			boolean isReplay = action.equals("replayTest");
        			List<TestCaseReport> reports = null;
        			
        			if(isReplay)
        			{
        				TestCase origfound = null;
	        			String filePath = basepath + SystemUtils.FILE_SEPARATOR + gatfConfig.getTestCaseDir() + SystemUtils.FILE_SEPARATOR
	        					+ testcaseFileName;
	        			if(!new File(filePath).exists()) {
	        				throw new RuntimeException("Test case file does not exist");
	        			}
	        			
	        			List<TestCase> tcs = new XMLTestCaseFinder().resolveTestCases(new File(filePath));
	        			for (TestCase tc : tcs) {
							if(tc.getName().equals(testCaseName)) {
								origfound = tc;
								break;
							}
						}
	        			if(origfound==null) {
	        				throw new RuntimeException("Testcase does not exist");
	        			}
	        			
	        			GatfTestCaseExecutorMojo executorMojo = new GatfTestCaseExecutorMojo();

	        			lock.lock();
	        			
	        			if(mojo.getContext()!=null) {
	        				context = mojo.getContext();
	        			} else if(context==null) {
	        				try {
        						executorMojo.setProject(project);
        						executorMojo.initilaizeContext(gatfConfig, true);
        						context = executorMojo.getContext();
							} catch (Exception e) {
								e.printStackTrace();
								throw new RuntimeException("Please Execute the GATF Suite first..");
							}
	        			}
	        			
	        			context.setGatfExecutorConfig(gatfConfig);
	        			executorMojo.setContext(context);
	        			executorMojo.getAllTestCases(context, null, null);
						authTestCase = executorMojo.getAuthTestCase();
	        			
	        			TestCaseExecutorUtil testCaseExecutorUtil = TestCaseExecutorUtil.getSingleConnection(context);
	        			
	        			boolean isAuthExec = false;
	        			
	        			TestCase found = new TestCase(origfound);
	        			found.setSimulationNumber(0);
	        			found.setSourcefileName(testcaseFileName);
						found.setBaseUrl(context.getGatfExecutorConfig().getBaseUrl());
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
						
						TestCaseReport tcReport = new org.codehaus.jackson.map.ObjectMapper().readValue(request.getInputStream(), 
	        					TestCaseReport.class);
	        			if(tcReport == null) {
	        				throw new RuntimeException("Invalid testcase report details provided");
	        			}
						
	        			found.setAurl(tcReport.getActualUrl());
						found.setAcontent(tcReport.getRequestContent());
						found.setAexpectedNodes(tcReport.getAexpectedNodes());
						
						if(found.getAurl().startsWith(found.getBaseUrl().trim())) {
							String turl = found.getAurl().replace(found.getBaseUrl().trim(), "");
							found.setAurl(turl);
						}
						
						if(StringUtils.isNotBlank(tcReport.getRequestHeaders()))
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
						isAuthExec = found.isSecure() && gatfConfig.isAuthEnabled() && authTestCase!=null;
						context.getWorkflowContextHandler().initializeSuiteContextWithnum(0);
	        			
						TestCase authTestCaseT = authTestCase;
						
						boolean complete = false;
						
						if(isAuthExec)
						{
							reports = context.getSingleTestCaseExecutor().execute(authTestCaseT, 
									testCaseExecutorUtil);
							if(reports.size()>0 && !reports.get(0).getStatus().equals(TestStatus.Success.status))
							{
								context.getWorkflowContextHandler().initializeSuiteContextWithnum(-1);
								executorMojo.invokeServerLogApi(false, reports.get(0), testCaseExecutorUtil,
										gatfConfig.isServerLogsApiAuthEnabled());
								complete = true;
							}
						}
						
						if(reports!=null && authTestCaseT!=null && found.getName().equals(authTestCaseT.getName()))
						{
							complete = true;
						}
						
						if(!complete)
						{
							if(found.isSecure() && authTestCaseT==null)
							{
								throw new RuntimeException("No Authentication testcase found, please add one" +
										"or change the testcase to unsecure mode");
							}
							reports = context.getSingleTestCaseExecutor().executeDirectTestCase(found, 
									testCaseExecutorUtil);
							if(reports.size()>0 && !reports.get(0).getStatus().equals(TestStatus.Success.status))
							{
								context.getWorkflowContextHandler().initializeSuiteContextWithnum(-1);
								executorMojo.invokeServerLogApi(false, reports.get(0), testCaseExecutorUtil,
										gatfConfig.isServerLogsApiAuthEnabled());
							}
						}
						
						lock.unlock();
						
	        			testCaseExecutorUtil.shutdown();
        			}
        			else if(isServerLogsApi)
        			{
        				AcceptanceTestContext context = null;
        				try {
        					GatfTestCaseExecutorMojo executorMojo = new GatfTestCaseExecutorMojo();
    						executorMojo.setProject(project);
    						executorMojo.initilaizeContext(gatfConfig, false);
    						context = executorMojo.getContext();
						} catch (Exception e) {
							e.printStackTrace();
							throw new RuntimeException("Please Execute the GATF Suite first..");
						}
	        			
	        			TestCaseExecutorUtil testCaseExecutorUtil = TestCaseExecutorUtil.getSingleConnection(context);
	        			
	        			boolean isAuthExec = false;
						TestCase authTestCaseT = context.getServerLogApi(true);
						isAuthExec = gatfConfig.isServerLogsApiAuthEnabled() && authTestCaseT!=null;
						context.getWorkflowContextHandler().initializeSuiteContextWithnum(-1);
						
						boolean complete = false;
						
						if(isAuthExec)
						{
							reports = context.getSingleTestCaseExecutor().execute(authTestCaseT, 
									testCaseExecutorUtil);
							if(reports.size()>0 && !reports.get(0).getStatus().equals(TestStatus.Success.status))
							{
								complete = true;
							}
						}
						
						if(!complete)
						{
							TestCase found = context.getServerLogApi(false);
							if(gatfConfig.isServerLogsApiAuthEnabled() && authTestCaseT==null)
							{
								throw new RuntimeException("No Authentication testcase found, please add one" +
										"or change the testcase to unsecure mode");
							}
							if(testCaseName.equals(found.getName()))
							{
								reports = context.getSingleTestCaseExecutor().execute(found, 
										testCaseExecutorUtil);
							}
						}
						
	        			testCaseExecutorUtil.shutdown();
        			}
        			else if(isExternalLogsApi)
        			{
        				TestCase origfound = null;
	        			String filePath = basepath + SystemUtils.FILE_SEPARATOR + testcaseFileName;
	        			if(!new File(filePath).exists()) {
	        				throw new RuntimeException("External API Test case file does not exist");
	        			}
	        			
	        			TestCase authTestCaseT = null;
	        			List<TestCase> tcs = new XMLTestCaseFinder().resolveTestCases(new File(filePath));
	        			for (TestCase tc : tcs) {
							if(tc.getName().equals(testCaseName)) {
								origfound = tc;
							} else if(tc.getName().equals("authapi")) {
								authTestCaseT = tc;
							}
						}
	        			if(origfound==null) {
	        				throw new RuntimeException("External API Testcase does not exist");
	        			}
        				
	        			AcceptanceTestContext context = null;
        				try {
        					GatfTestCaseExecutorMojo executorMojo = new GatfTestCaseExecutorMojo();
    						executorMojo.setProject(project);
    						executorMojo.initilaizeContext(gatfConfig, false);
    						context = executorMojo.getContext();
						} catch (Exception e) {
							e.printStackTrace();
							throw new RuntimeException("Please Execute the GATF Suite first..");
						}
	        			
	        			TestCaseExecutorUtil testCaseExecutorUtil = TestCaseExecutorUtil.getSingleConnection(context);
	        			
	        			boolean isAuthExec = false;
	        			
	        			TestCase found = new TestCase(origfound);
	        			found.setSimulationNumber(0);
	        			found.setSourcefileName(testcaseFileName);
						found.setExternalApi(true);
						
						if(found.getHeaders()==null)
						{
							found.setHeaders(new HashMap<String, String>());
						}
						
						TestCaseReport tcReport = null;
						if(!action.equals("playTest"))
						{
							tcReport = new org.codehaus.jackson.map.ObjectMapper().readValue(request.getInputStream(), 
	        					TestCaseReport.class);
							tcReport.setTestCaseExternalApi(found);
						}
						
						if(action.equals("getContent"))
						{
		        			if(tcReport == null) {
		        				throw new RuntimeException("Invalid testcase report details provided");
		        			}
		        			
		        			if(!StringUtils.isBlank(found.getContent())) 
		        			{
		        				VelocityContext vcontext = new VelocityContext();
		        				vcontext.put("report", tcReport);
		        				
		        				StringWriter writer = new StringWriter();
		        				context.getWorkflowContextHandler().getEngine()
		        					.evaluate(vcontext, writer, "ERROR", found.getContent());
		        				String content = writer.toString();
		        				
		        				writer = new StringWriter();
		        				context.getWorkflowContextHandler().getEngine()
		        					.evaluate(vcontext, writer, "ERROR", found.getUrl());
		        				String url = writer.toString();
		        				
		        				TestCaseReport nrep = new TestCaseReport();
		        				nrep.setUrl(url);
		        				nrep.setRequestContent(content);
		        				
	        					String configJson = new org.codehaus.jackson.map.ObjectMapper().writeValueAsString(nrep);
	        					response.setContentLength(configJson.length());
	        		            response.getWriter().write(configJson);
	        					response.setStatus(HttpStatus.OK_200);
	        					return;
		        			}
						}
						else if(action.equals("createIssue"))
						{
							if(tcReport == null) {
		        				throw new RuntimeException("Invalid Issue details provided");
		        			}
							
							found.setUrl(tcReport.getUrl());
							found.setContent(tcReport.getRequestContent());
						}
						
						isAuthExec = gatfConfig.isServerLogsApiAuthEnabled() && authTestCaseT!=null;
						context.getWorkflowContextHandler().initializeSuiteContextWithnum(-2);
						
						boolean complete = false;
						
						if(isAuthExec)
						{
							authTestCaseT = new TestCase(authTestCaseT);
							authTestCaseT.setSimulationNumber(0);
							authTestCaseT.setSourcefileName(testcaseFileName);
							authTestCaseT.setExternalApi(true);
							
							if(authTestCaseT.getHeaders()==null)
							{
								authTestCaseT.setHeaders(new HashMap<String, String>());
							}
							authTestCaseT.setExternalApi(true);
							authTestCaseT.validate(context.getHttpHeaders(), null);
							reports = context.getSingleTestCaseExecutor().execute(authTestCaseT, 
									testCaseExecutorUtil);
							if(reports.size()>0 && !reports.get(0).getStatus().equals(TestStatus.Success.status))
							{
								complete = true;
							}
						}

						if(!complete)
						{
							if(found.isSecure() && authTestCaseT==null)
							{
								throw new RuntimeException("No Authentication testcase found, please add one" +
										"or change the testcase to unsecure mode");
							}
							found.validate(context.getHttpHeaders(), null);
							reports = context.getSingleTestCaseExecutor().execute(found, testCaseExecutorUtil);
						}
						
	        			testCaseExecutorUtil.shutdown();
        			}
        			else
        			{
        				TestCase origfound = null;
	        			String filePath = basepath + SystemUtils.FILE_SEPARATOR + gatfConfig.getTestCaseDir() + SystemUtils.FILE_SEPARATOR
	        					+ testcaseFileName;
	        			if(!new File(filePath).exists()) {
	        				throw new RuntimeException("Test case file does not exist");
	        			}
	        			
	        			List<TestCase> tcs = new XMLTestCaseFinder().resolveTestCases(new File(filePath));
	        			for (TestCase tc : tcs) {
							if(tc.getName().equals(testCaseName)) {
								origfound = tc;
								break;
							}
						}
	        			if(origfound==null) {
	        				throw new RuntimeException("Testcase does not exist");
	        			}

	        			GatfTestCaseExecutorMojo executorMojo = new GatfTestCaseExecutorMojo();
	        			
	        			lock.lock();
	        			
	        			if(mojo.getContext()!=null) {
	        				context = mojo.getContext();
	        			} else if(context==null) {
	        				try {
        						executorMojo.setProject(project);
        						executorMojo.initilaizeContext(gatfConfig, true);
        						context = executorMojo.getContext();
							} catch (Exception e) {
								e.printStackTrace();
								throw new RuntimeException("Please Execute the GATF Suite first..");
							}
	        			}
	        			
	        			context.setGatfExecutorConfig(gatfConfig);
	        			executorMojo.setContext(context);
	        			executorMojo.getAllTestCases(context, null, null);
						authTestCase = executorMojo.getAuthTestCase();
	        			
	        			TestCaseExecutorUtil testCaseExecutorUtil = TestCaseExecutorUtil.getSingleConnection(context);
	        			
	        			boolean isAuthExec = false;
	        			TestCase found = new TestCase(origfound);
	        			found.setSimulationNumber(0);
	        			found.setSourcefileName(testcaseFileName);
						found.setBaseUrl(context.getGatfExecutorConfig().getBaseUrl());
						found.setLogicalValidations(null);
						found.setExecuteOnCondition(null);
						
						if(found.getHeaders()==null)
						{
							found.setHeaders(new HashMap<String, String>());
						}
						
						isAuthExec = found.isSecure() && gatfConfig.isAuthEnabled() && authTestCase!=null;
						context.getWorkflowContextHandler().initializeSuiteContextWithnum(0);
	        			
						TestCase authTestCaseT = authTestCase;
						
						boolean complete = false;
						
						if(isAuthExec)
						{
							reports = context.getSingleTestCaseExecutor().execute(authTestCaseT, testCaseExecutorUtil);
							if(reports.size()>0 && !reports.get(0).getStatus().equals(TestStatus.Success.status))
							{
								context.getWorkflowContextHandler().initializeSuiteContextWithnum(-1);
								executorMojo.invokeServerLogApi(false, reports.get(0), testCaseExecutorUtil,
										gatfConfig.isServerLogsApiAuthEnabled());
								complete = true;
							}
						}
						
						if(reports!=null && authTestCaseT!=null && found.getName().equals(authTestCaseT.getName()))
						{
							complete = true;
						}
						
						if(!complete)
						{
							if(found.isSecure() && authTestCaseT==null)
							{
								throw new RuntimeException("No Authentication testcase found, please add one" +
										"or change the testcase to unsecure mode");
							}
							reports = context.getSingleTestCaseExecutor().execute(found, testCaseExecutorUtil);
							if(reports.size()>0 && !reports.get(0).getStatus().equals(TestStatus.Success.status))
							{
								context.getWorkflowContextHandler().initializeSuiteContextWithnum(-1);
								executorMojo.invokeServerLogApi(false, reports.get(0), testCaseExecutorUtil,
										gatfConfig.isServerLogsApiAuthEnabled());
							}
						}
						
						lock.unlock();
						
	        			testCaseExecutorUtil.shutdown();
        			}
        			
        			if(reports==null || reports.size()==0)
        			{
        				throw new RuntimeException("Could not execute Testcase");
        			}
        			
        			TestCaseReport report = reports.get(0);
        			ReportHandler.populateRequestResponseHeaders(report);
        			String configJson = new org.codehaus.jackson.map.ObjectMapper().writeValueAsString(report);
					response.setContentLength(configJson.length());
		            response.getWriter().write(configJson);
					response.setStatus(HttpStatus.OK_200);
        		}
        	}
    	} catch (Exception e) {
    		GatfConfigToolMojo.handleError(e, response, null);
		}
    }

}
